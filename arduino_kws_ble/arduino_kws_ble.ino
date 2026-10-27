/* Edge Impulse ingestion SDK
 * Proyecto: Casa Segura - KWS + BLE + Actuadores LED
 * Versión Oficial de Alto Rendimiento para Presentación en Vivo
 */

#define EIDSP_QUANTIZE_FILTERBANK   0

/* Includes ---------------------------------------------------------------- */
#include <PDM.h>
#include <ArduinoBLE.h>
#include <casa-inteligente-kws_inferencing.h>

// Definición de pines para los LEDs de la tarjeta Nano 33 BLE
#define LED_ROJO    LEDR
#define LED_VERDE   LEDG
#define LED_AZUL    LEDB
#define LED_PLACA   LED_BUILTIN

// Servicio y Característica Bluetooth BLE
BLEService kwsService("19B10000-E8F2-537E-4F6C-D104768A1214");
BLEStringCharacteristic comandoChar("19B10001-E8F2-537E-4F6C-D104768A1214", BLERead | BLENotify, 20);

typedef struct {
    int16_t *buffer;
    uint8_t buf_ready;
    uint32_t buf_count;
    uint32_t n_samples;
} inference_t;

static inference_t inference;
static signed short sampleBuffer[2048];
static bool debug_nn = false; 

static bool microphone_inference_start(uint32_t n_samples);
static bool microphone_inference_record(void);
static void microphone_inference_end(void);
static int microphone_audio_signal_get_data(size_t offset, size_t length, float *out_ptr);

// Apagar todos los LEDs (Lógica invertida de la tarjeta Nano 33 BLE)
void apagar_leds() {
    digitalWrite(LED_ROJO, HIGH);  // HIGH = Apagado
    digitalWrite(LED_VERDE, HIGH); // HIGH = Apagado
    digitalWrite(LED_AZUL, HIGH);  // HIGH = Apagado
    digitalWrite(LED_PLACA, LOW);  // LOW  = Apagado
}

void setup()
{
    Serial.begin(115200);

    pinMode(LED_ROJO, OUTPUT);
    pinMode(LED_VERDE, OUTPUT);
    pinMode(LED_AZUL, OUTPUT);
    pinMode(LED_PLACA, OUTPUT);
    apagar_leds();

    Serial.println("\n==========================================");
    Serial.println("  CASA SEGURA - SISTEMA KWS + BLE + LEDS  ");
    Serial.println("==========================================\n");

    // Inicializar servidor Bluetooth BLE
    if (!BLE.begin()) {
        Serial.println("ERR: Falló la inicialización del Bluetooth!");
    } else {
        BLE.setLocalName("CasaSegura_KWS");
        BLE.setAdvertisedService(kwsService);
        kwsService.addCharacteristic(comandoChar);
        BLE.addService(kwsService);
        comandoChar.writeValue("OK");
        BLE.advertise();
        Serial.println("📡 BLE listo y transmitiendo como 'CasaSegura_KWS'");
    }

    if (microphone_inference_start(EI_CLASSIFIER_RAW_SAMPLE_COUNT) == false) {
        ei_printf("ERR: Could not allocate audio buffer\r\n");
        return;
    }
}

void loop()
{
    BLE.poll();

    bool m = microphone_inference_record();
    if (!m) {
        ei_printf("ERR: Failed to record audio...\n");
        return;
    }

    signal_t signal;
    signal.total_length = EI_CLASSIFIER_RAW_SAMPLE_COUNT;
    signal.get_data = &microphone_audio_signal_get_data;
    ei_impulse_result_t result = { 0 };

    EI_IMPULSE_ERROR r = run_classifier(&signal, &result, debug_nn);
    if (r != EI_IMPULSE_OK) {
        ei_printf("ERR: Failed to run classifier (%d)\n", r);
        return;
    }

    float max_score = 0.0;
    int max_index = -1;

    for (size_t ix = 0; ix < EI_CLASSIFIER_LABEL_COUNT; ix++) {
        if (result.classification[ix].value > max_score) {
            max_score = result.classification[ix].value;
            max_index = ix;
        }
    }

    float umbral_anomalia = 1.5;
    float umbral_confianza = 0.60; // 60% para respuesta ágil e instantánea

#if EI_CLASSIFIER_HAS_ANOMALY == 1
    if (result.anomaly > umbral_anomalia) {
        Serial.print("⚠️ [ANOMALÍA DETECTADA]: Score: ");
        Serial.println(result.anomaly, 2);
        
        // Notificar anomalía por BLE
        comandoChar.writeValue("ANOMALIA");

        // Destello rápido en LED Rojo
        apagar_leds();
        digitalWrite(LED_ROJO, LOW);
        delay(150);
        apagar_leds();
    }
    else
#endif
    if (max_score >= umbral_confianza && max_index != -1) {
        String comando = String(result.classification[max_index].label);
        comando.toUpperCase();

        if (comando != "RUIDO") {
            Serial.print("✅ [COMANDO DETECTADO]: ");
            Serial.print(comando);
            Serial.print("  --> Confianza: ");
            Serial.print(max_score * 100.0, 1);
            Serial.println("%");

            // TRANSMISIÓN INALÁMBRICA BLE A PYTHON
            comandoChar.writeValue(comando);

            // REACCIÓN FÍSICA DE LOS LEDS
            apagar_leds();
            if (comando == "ALARMA") {
                digitalWrite(LED_ROJO, LOW);   // LED Rojo
            } else if (comando == "GARAJE") {
                digitalWrite(LED_VERDE, LOW);  // LED Verde
            } else if (comando == "PUERTA") {
                digitalWrite(LED_AZUL, LOW);   // LED Azul
            } else if (comando == "TELEVISOR") {
                digitalWrite(LED_ROJO, LOW);   // LED Amarillo
                digitalWrite(LED_VERDE, LOW);
            } else if (comando == "VENTANAS") {
                digitalWrite(LED_PLACA, HIGH); // LED Integrado
            }
        }
    } 
}

static void pdm_data_ready_inference_callback(void)
{
    int bytesAvailable = PDM.available();
    int bytesRead = PDM.read((char *)&sampleBuffer[0], bytesAvailable);

    if (inference.buf_ready == 0) {
        for(int i = 0; i < bytesRead>>1; i++) {
            inference.buffer[inference.buf_count++] = sampleBuffer[i];

            if(inference.buf_count >= inference.n_samples) {
                inference.buf_count = 0;
                inference.buf_ready = 1;
                break;
            }
        }
    }
}

static bool microphone_inference_start(uint32_t n_samples)
{
    inference.buffer = (int16_t *)malloc(n_samples * sizeof(int16_t));

    if(inference.buffer == NULL) {
        return false;
    }

    inference.buf_count = 0;
    inference.n_samples = n_samples;
    inference.buf_ready = 0;

    PDM.onReceive(&pdm_data_ready_inference_callback);
    PDM.setBufferSize(4096);

    if (!PDM.begin(1, EI_CLASSIFIER_FREQUENCY)) {
        microphone_inference_end();
        return false;
    }

    PDM.setGain(127);
    return true;
}

static bool microphone_inference_record(void)
{
    inference.buf_ready = 0;
    inference.buf_count = 0;

    while(inference.buf_ready == 0) {
        BLE.poll(); // Mantiene la transmisión BLE fluida
        yield();    // Prioridad absoluta al micrófono PDM sin congelar
    }

    return true;
}

static int microphone_audio_signal_get_data(size_t offset, size_t length, float *out_ptr)
{
    numpy::int16_to_float(&inference.buffer[offset], out_ptr, length);
    return 0;
}

static void microphone_inference_end(void)
{
    PDM.end();
    free(inference.buffer);
}

#if !defined(EI_CLASSIFIER_SENSOR) || EI_CLASSIFIER_SENSOR != EI_CLASSIFIER_SENSOR_MICROPHONE
#error "Invalid model for current sensor."
#endif