package com.casasegura;

/**
 * Sectores de la casa inteligente. Cada uno corresponde a una palabra clave
 * que el modelo KWS del Arduino reconoce y a la tecla que emula el puente BLE.
 */
public enum Sector {

    ALARMA("Alarma", 'A'),
    GARAJE("Garaje", 'G'),
    PUERTA("Puerta", 'P'),
    TELEVISOR("Televisor", 'T'),
    VENTANAS("Ventanas", 'V');

    private final String displayName;
    private final char shortcutKey;

    Sector(String displayName, char shortcutKey) {
        this.displayName = displayName;
        this.shortcutKey = shortcutKey;
    }

    /** Nombre legible para la interfaz. */
    public String getDisplayName() {
        return displayName;
    }

    /** Tecla asociada (en mayúscula; coincide con el código {@code KeyEvent.VK_*}). */
    public char getShortcutKey() {
        return shortcutKey;
    }
}
