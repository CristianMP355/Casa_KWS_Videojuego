import asyncio
from bleak import BleakScanner, BleakClient
from pynput.keyboard import Controller

keyboard = Controller()

DEVICE_NAME = "CasaSegura_KWS"
CHARACTERISTIC_UUID = "19B10001-E8F2-537E-4F6C-D104768A1214"

TECLAS_MAP = {
    "PUERTA": 'p',
    "GARAJE": 'g',
    "VENTANAS": 'v',
    "TELEVISOR": 't',
    "ALARMA": 'a'
}

def notification_handler(sender, data: bytearray):
    comando = data.decode('utf-8').strip().upper()
    print(f"📡 [BLE RECIBIDO]: {comando}")

    if comando in TECLAS_MAP:
        tecla = TECLAS_MAP[comando]
        keyboard.press(tecla)
        keyboard.release(tecla)
        print(f"⌨️  [TECLA EMULADA]: '{tecla.upper()}' presionada")
    elif comando == "ANOMALIA":
        keyboard.press('x')
        keyboard.release('x')
        print("⚠️ [ANOMALÍA EMULADA]: 'X' presionada")

async def main():
    print("🔍 Buscando dispositivo Arduino Nano 33 BLE ('CasaSegura_KWS')...")
    device = await BleakScanner.find_device_by_name(DEVICE_NAME, timeout=12.0)

    if not device:
        print("❌ No se encontró el Arduino. Asegúrate de que esté encendido y emitiendo BLE.")
        return

    print(f"✅ Arduino encontrado en [{device.address}]. Vinculando servicios...")

    # Conexión por dirección MAC directa con mayor timeout para Windows
    async with BleakClient(device.address, timeout=20.0) as client:
        print("🔗 ¡Conectado con éxito por Bluetooth Low Energy!")
        await client.start_notify(CHARACTERISTIC_UUID, notification_handler)
        print("🎤 Escuchando comandos de voz en tiempo real... (Presiona Ctrl+C para salir)")

        while True:
            await asyncio.sleep(1)

if __name__ == "__main__":
    try:
        asyncio.run(main())
    except Exception as e:
        print(f"\n⚠️ Error de conexión: {e}")
        print("💡 Sugerencia: Presiona el botón RESET en la placa Arduino y vuelve a ejecutar el comando.")
