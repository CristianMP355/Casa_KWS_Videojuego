# Casa Segura - Smart Home KWS & Game Engine 🛡️🎙️

Juego de operador de seguridad nocturno controlado por voz. Un modelo TinyML de *Keyword
Spotting* (KWS) corre en un Arduino Nano 33 BLE Sense, reconoce comandos vocales y los envía por
Bluetooth Low Energy a un puente en Python, que los convierte en pulsaciones de teclado para el
dashboard en Java Swing donde se juega.

---

## Arquitectura del Sistema

```
 ┌──────────────────────────┐   BLE (notify)   ┌──────────────────────┐   teclas   ┌──────────────────────────┐
 │ Arduino Nano 33 BLE Sense│ ───────────────► │  bridge_ble.py       │ ─────────► │ Dashboard Java Swing     │
 │ Micrófono + TinyML (KWS) │  "PUERTA", ...   │  bleak + pynput      │  P,G,V,T,A │ FlatLaf · Main.java      │
 └──────────────────────────┘                  └──────────────────────┘     X      └──────────────────────────┘
```

1. **Arduino Nano 33 BLE Sense con TinyML**
   - El micrófono PDM integrado captura audio continuamente.
   - Un modelo de KWS entrenado (TensorFlow Lite for Microcontrollers / Edge Impulse) clasifica
     las palabras `PUERTA`, `GARAJE`, `VENTANAS`, `TELEVISOR`, `ALARMA` y la clase `ANOMALIA`.
   - Publica la palabra detectada como periférico BLE con el nombre `CasaSegura_KWS`, en la
     característica `19B10001-E8F2-537E-4F6C-D104768A1214`.

2. **Puente Python `bridge_ble.py`**
   - Busca el dispositivo con `bleak` y se suscribe a las notificaciones de la característica.
   - Traduce cada palabra a una tecla con `pynput` (`PUERTA → p`, `GARAJE → g`, `VENTANAS → v`,
     `TELEVISOR → t`, `ALARMA → a`, `ANOMALIA → x`).

3. **Dashboard Java Swing con FlatLaf** (`src/main/java/com/casasegura`)
   - **Plano 2D de la Casa // Centro de Control:**
     - HUD con puntaje, nivel, vidas, high score, barra de riesgo y botones de turno.
     - Plano blueprint que se puede arrastrar (para volver: botón *Centrar Vista* o doble clic).
     - Tarjetas de *Emergencias en tiempo real* y columna *Estado de dispositivos*.
   - **Consola de Telemetría e Historial:** historial de turnos y terminal de eventos.
   - `GameEngine` gestiona amenazas, niveles, combos y vidas. `HighScoreRepository` guarda el
     récord en JSON con Gson.

---

## Requisitos Previos

| Componente | Versión |
|------------|---------|
| JDK        | 17 o superior (IntelliJ IDEA incluye uno) |
| Maven      | 3.8+ (incluido en IntelliJ IDEA) |
| Python     | 3.10 o superior, con `bleak` y `pynput` |
| Arduino IDE| 2.x, con el core **Arduino Mbed OS Nano Boards** y la librería **ArduinoBLE** |
| Hardware   | Arduino Nano 33 BLE Sense y un PC con Bluetooth 4.0+ |

---

## Guía de Ejecución

### 1. Subir el sketch al Arduino

1. Abre el sketch de KWS en Arduino IDE 2.x. Si el modelo viene de Edge Impulse, primero importa
   su librería `.zip` en `Sketch > Include Library > Add .ZIP Library...`.
2. En `Tools > Board` selecciona **Arduino Nano 33 BLE** y elige el puerto COM correspondiente.
3. Pulsa **Upload**. Al terminar, la placa debe anunciarse por BLE como `CasaSegura_KWS`.

### 2. Correr el puente BLE

```bash
pip install bleak pynput
python bridge_ble.py
```

Cuando el script se conecta, muestra un mensaje de conexión. Si no encuentra la placa, presiona
**RESET** en el Arduino y vuelve a ejecutarlo.

### 3. Lanzar el juego (`Main.java`)

**IntelliJ IDEA:**
1. `File > Open...` y selecciona esta carpeta. IntelliJ detecta el `pom.xml`.
2. Configura un JDK 17+ en `File > Project Structure > SDK`.
3. Abre `src/main/java/com/casasegura/Main.java` y ejecútalo con **Shift + F10**.

**Terminal:**
```bash
mvn package
java -jar target/casa-segura-kws-game-1.0.0-jar-with-dependencies.jar
```

> La ventana del juego debe tener el foco para recibir las teclas que emula el puente BLE.
> Pulsa **INICIAR TURNO** y responde a las intrusiones diciendo el comando vocal del sector.

---

## Tabla de Comandos Vocales

| Comando vocal | Sector / acción                         | Tecla de respaldo |
|---------------|-----------------------------------------|:-----------------:|
| `"PUERTA"`    | Puerta principal (Sala)                 | `P` |
| `"GARAJE"`    | Garaje                                  | `G` |
| `"VENTANAS"`  | Ventanas (Habitación)                   | `V` |
| `"TELEVISOR"` | Televisor (Cocina / Comedor)            | `T` |
| `"ALARMA"`    | Alarma (Cocina / Comedor)               | `A` |
| *Anomalía*    | Ruido / palabra desconocida → congela monitores | `X` |

Las teclas de respaldo son **sigilosas**: la interfaz no las muestra y presenta cada entrada como
un comando vocal (`VOZ Comando "PUERTA" procesado`). La captura de teclado está siempre activa en
cualquier pestaña, así que puedes jugar sin el Arduino o usarlas si falla la conexión BLE.

---

## Reglas del Juego

- **Niveles:**
  - Nivel 1 (0-500 pts): 1 amenaza a la vez, 10.0 s para responder.
  - Nivel 2 (500-1200 pts): 2 amenazas simultáneas, 8.0 s.
  - Nivel 3 (1200+ pts): 3 amenazas simultáneas, 6.0 s.
  - Los tiempos dejan margen para la inferencia de audio y la latencia BLE. La dificultad también
    puede fijarse a mano en el header.
- **Puntaje:** neutralizar una amenaza da 100-200 pts según la rapidez, multiplicado por el combo
  (hasta x2.0). Un comando en un sector sin amenaza resta 30 pts y rompe el combo.
- **Anomalía (X):** congela los monitores 1.2 s y sube temporalmente el riesgo global. Mientras
  dura, las respuestas se ignoran, pero los tiempos siguen corriendo.
- **Vidas:** hay 3, y cada intrusión no atendida a tiempo cuesta una.
- **Récord:** el récord y los últimos 20 turnos se guardan en `highscore_casa_segura.json`, en el
  directorio de trabajo.

---

## Estructura del Proyecto

```
├── bridge_ble.py                 # Puente BLE → teclado
├── pom.xml                       # Maven (FlatLaf, Gson)
└── src/main/java/com/casasegura/
    ├── Main.java                 # Punto de entrada (tema FlatLaf)
    ├── MainDashboardUI.java      # Ventana principal y pestañas
    ├── GameEngine.java           # Lógica del juego
    ├── KeyController.java        # Captura global de teclado
    ├── HousePlanPanel.java       # Plano 2D arrastrable
    ├── StatsHudPanel.java        # HUD de estadísticas
    ├── EmergencyPanel.java       # Emergencias en tiempo real
    ├── DeviceStatusPanel.java    # Estado de dispositivos
    ├── HistoryPanel.java         # Historial de turnos
    ├── EventLogPanel.java        # Terminal de eventos
    ├── HighScoreRepository.java  # Persistencia JSON
    ├── LedIndicator.java, Sector.java, LogType.java
```
