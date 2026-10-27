package com.casasegura;

import javax.swing.SwingUtilities;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.HashSet;
import java.util.Set;

/**
 * Captura global de teclado para la ventana del juego mediante un {@link KeyEventDispatcher}.
 * Recibe las teclas emuladas por el puente BLE ({@code bridge_ble.py}):
 * <pre>
 * P → Puerta   G → Garaje   V → Ventanas   T → Televisor   A → Alarma   X → Anomalía
 * </pre>
 * A diferencia de los Key Bindings, el dispatcher recibe los eventos antes que cualquier
 * componente y aunque ningún componente tenga el foco, por lo que funciona igual en cualquier
 * pestaña (Centro de Control o Telemetría). Ignora la auto-repetición: cada pulsación cuenta
 * una sola vez hasta soltar la tecla.
 */
public class KeyController implements KeyEventDispatcher {

    /** Receptor de las entradas del operador. */
    public interface Handler {
        void onSectorKey(Sector sector);

        void onAnomalyKey();
    }

    private static final int ANOMALY_KEY = KeyEvent.VK_X;

    private final Handler handler;
    private final Set<Integer> heldKeys = new HashSet<>();
    private Window window;
    private boolean enabled = true;

    public KeyController(Handler handler) {
        this.handler = handler;
    }

    /** Empieza a escuchar las teclas dirigidas a la ventana indicada. */
    public void install(Window window) {
        this.window = window;
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(this);
    }

    public void uninstall() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(this);
        reset();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (!enabled || !belongsToWindow(e) || e.isControlDown() || e.isAltDown() || e.isMetaDown()) {
            return false;
        }
        int code = e.getKeyCode();
        Sector sector = sectorFor(code);
        boolean mapped = sector != null || code == ANOMALY_KEY;

        switch (e.getID()) {
            case KeyEvent.KEY_PRESSED -> {
                if (!mapped) {
                    return false;
                }
                if (heldKeys.add(code)) {
                    if (sector != null) {
                        handler.onSectorKey(sector);
                    } else {
                        handler.onAnomalyKey();
                    }
                }
                return true; // Consumido: no llega a otros componentes
            }
            case KeyEvent.KEY_RELEASED -> {
                heldKeys.remove(code);
                return mapped;
            }
            case KeyEvent.KEY_TYPED -> {
                // KEY_TYPED no trae keyCode: se filtra por carácter.
                char c = Character.toUpperCase(e.getKeyChar());
                return sectorFor(c) != null || c == ANOMALY_KEY;
            }
            default -> {
                return false;
            }
        }
    }

    /** VK_A..VK_Z coinciden con los caracteres 'A'..'Z'. */
    private static Sector sectorFor(int keyCode) {
        for (Sector sector : Sector.values()) {
            if (sector.getShortcutKey() == keyCode) {
                return sector;
            }
        }
        return null;
    }

    private boolean belongsToWindow(KeyEvent e) {
        if (window == null || e.getComponent() == null) {
            return false;
        }
        return e.getComponent() == window || SwingUtilities.getWindowAncestor(e.getComponent()) == window;
    }

    /** Olvida las teclas presionadas (p. ej. al perder el foco la ventana y no recibir el "soltar"). */
    public void reset() {
        heldKeys.clear();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        reset();
    }
}
