package com.casasegura;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Color;
import java.util.Map;

/**
 * Punto de entrada de "Casa Segura - Smart Home KWS Dashboard &amp; Game Engine".
 * Configura el tema FlatLaf Dark con acentos neón y abre el dashboard principal.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            configureLookAndFeel();
            MainDashboardUI dashboard = new MainDashboardUI();
            dashboard.setVisible(true);
        });
    }

    /** Aplica FlatLaf Dark con un acento cian estilo Cyberpunk. */
    private static void configureLookAndFeel() {
        FlatLaf.setGlobalExtraDefaults(Map.of("@accentColor", "#00E5FF"));
        FlatDarkLaf.setup();

        UIManager.put("Component.arc", 10);
        UIManager.put("Button.arc", 10);
        UIManager.put("ProgressBar.arc", 10);
        UIManager.put("TextComponent.arc", 8);
        UIManager.put("Component.focusWidth", 1);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("TabbedPane.showTabSeparators", true);
        UIManager.put("TabbedPane.tabHeight", 34);
        UIManager.put("TabbedPane.underlineColor", new Color(0x00E5FF));
        UIManager.put("Panel.background", new Color(0x12161F));
    }
}
