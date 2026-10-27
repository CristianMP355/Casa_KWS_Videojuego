package com.casasegura;

import java.awt.Color;

/**
 * Categorías de eventos de la terminal. Cada una tiene un color diferenciado
 * para que la consola sea legible de un vistazo.
 */
public enum LogType {

    INFO("INFO", new Color(0xB0BEC5)),
    VOICE("VOZ", new Color(0x00E5FF)),
    KWS("KWS", new Color(0xE040FB)),
    ANOMALY("ANOM", new Color(0xFF4081)),
    GAME("JUEGO", new Color(0xFFD54F)),
    SUCCESS("OK", new Color(0x69F0AE)),
    WARNING("AVISO", new Color(0xFFAB40)),
    ERROR("ERROR", new Color(0xFF5252));

    private final String tag;
    private final Color color;

    LogType(String tag, Color color) {
        this.tag = tag;
        this.color = color;
    }

    public String getTag() {
        return tag;
    }

    public Color getColor() {
        return color;
    }
}
