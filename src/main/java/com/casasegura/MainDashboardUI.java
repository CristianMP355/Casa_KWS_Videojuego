package com.casasegura;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Ventana principal de "Casa Segura - Smart Home KWS Game Engine".
 * <ul>
 *   <li>Header: indicador de estado del sistema KWS y selector de dificultad manual.</li>
 *   <li>Pestaña 1 "Plano 2D de la Casa // Centro de Control": todo el bucle de juego en vivo
 *       (HUD de estadísticas, plano 2D arrastrable, emergencias en tiempo real y estado de dispositivos).</li>
 *   <li>Pestaña 2 "Consola de Telemetría e Historial": historial de turnos y terminal de eventos.</li>
 * </ul>
 * Los comandos vocales llegan como teclas emuladas por el puente BLE {@code bridge_ble.py};
 * {@link KeyController} las captura siempre en segundo plano, en cualquier pestaña.
 */
public class MainDashboardUI extends JFrame {

    private static final String TITLE = "Casa Segura - Smart Home KWS Game Engine";
    private static final Color CYAN = new Color(0x00E5FF);
    private static final Color GREEN = new Color(0x00E676);
    private static final Color ORANGE = new Color(0xFFAB40);
    private static final Color TEXT_DIM = new Color(0x7F8FA6);
    private static final Color HEADER_BG = new Color(0x0D121B);

    /** Opciones del selector de dificultad; {@code null} = progresiva. */
    private static final GameEngine.Level[] DIFFICULTY_LEVELS = {
            null, GameEngine.Level.LEVEL_1, GameEngine.Level.LEVEL_2, GameEngine.Level.LEVEL_3};
    private static final String[] DIFFICULTY_LABELS = {
            "Progresiva (automática)", "Manual · Nivel 1 (10.0 s)", "Manual · Nivel 2 (8.0 s)",
            "Manual · Nivel 3 (6.0 s)"};

    private final GameEngine engine = new GameEngine(new HighScoreRepository());
    private final EventLogPanel logPanel = new EventLogPanel();
    private final StatsHudPanel hudPanel = new StatsHudPanel(engine);
    private final HousePlanPanel housePlan = new HousePlanPanel(engine);
    private final EmergencyPanel emergencyPanel = new EmergencyPanel(engine);
    private final DeviceStatusPanel deviceStatus = new DeviceStatusPanel(engine);
    private final HistoryPanel historyPanel = new HistoryPanel(engine);
    private final LedIndicator systemLed = new LedIndicator();
    private final KeyController keyController;

    public MainDashboardUI() {
        super(TITLE);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(1150, 760));
        setSize(1400, 900);
        setLocationRelativeTo(null);

        // Captura siempre habilitada: recibe los comandos que emula el puente BLE.
        keyController = new KeyController(new KeyController.Handler() {
            @Override
            public void onSectorKey(Sector sector) {
                handleVoiceCommand(sector);
            }

            @Override
            public void onAnomalyKey() {
                handleAnomaly();
            }
        });

        setLayout(new BorderLayout());
        add(buildHeader(), BorderLayout.NORTH);
        add(buildTabs(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        keyController.install(this);
        housePlan.setSectorClickHandler(this::handleVoiceCommand);
        wireEngine();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                housePlan.requestFocusInWindow();
            }

            @Override
            public void windowDeactivated(WindowEvent e) {
                keyController.reset();
            }

            @Override
            public void windowClosed(WindowEvent e) {
                keyController.uninstall();
                engine.stop(); // Guarda el turno en curso en el historial
            }
        });

        logPanel.append("Sistema Casa Segura listo. High Score actual: " + engine.getHighScore()
                + " pts. Récords en " + new HighScoreRepository().getFile().toAbsolutePath(), LogType.INFO);
        logPanel.append("Micrófono KWS en escucha. Esperando comandos vocales...", LogType.KWS);
    }

    // ------------------------------------------------------------------ Construcción

    private JPanel buildHeader() {
        JLabel title = new JLabel("CASA SEGURA  //  KWS GAME ENGINE");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        title.setForeground(CYAN);
        JLabel subtitle = new JLabel("Operador de seguridad nocturno · Arduino Nano 33 BLE Sense");
        subtitle.setForeground(TEXT_DIM);

        JPanel titles = new JPanel(new BorderLayout());
        titles.setOpaque(false);
        titles.add(title, BorderLayout.NORTH);
        titles.add(subtitle, BorderLayout.SOUTH);

        JComboBox<String> difficulty = new JComboBox<>(DIFFICULTY_LABELS);
        difficulty.setFocusable(false); // Evita que las letras seleccionen opciones del combo
        difficulty.addActionListener(e -> engine.setManualLevel(DIFFICULTY_LEVELS[difficulty.getSelectedIndex()]));

        // Indicador de estado del sistema KWS (destella al procesar cada comando).
        systemLed.setColor(GREEN);
        JLabel systemStatus = new JLabel("Micrófono KWS  //  Estado del Sistema: ESCUCHANDO");
        systemStatus.setForeground(GREEN);
        systemStatus.setFont(systemStatus.getFont().deriveFont(Font.BOLD));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        controls.setOpaque(false);
        controls.add(new JLabel("Dificultad:"));
        controls.add(difficulty);
        controls.add(separator());
        controls.add(systemLed);
        controls.add(systemStatus);

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(HEADER_BG);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0x1F2A3A)),
                BorderFactory.createEmptyBorder(10, 16, 10, 16)));
        header.add(titles, BorderLayout.WEST);
        header.add(controls, BorderLayout.EAST);
        return header;
    }

    private static JLabel separator() {
        JLabel sep = new JLabel("|");
        sep.setForeground(new Color(0x2E3A4E));
        return sep;
    }

    private JTabbedPane buildTabs() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFocusable(false);
        tabs.addTab("Plano 2D de la Casa // Centro de Control", buildControlCenterTab());
        tabs.addTab("Consola de Telemetría e Historial", buildTelemetryTab());
        return tabs;
    }

    /** Pestaña 1: HUD arriba, plano + emergencias al centro, estado de dispositivos a la derecha. */
    private JPanel buildControlCenterTab() {
        JPanel planAndEmergencies = new JPanel(new BorderLayout());
        planAndEmergencies.add(housePlan, BorderLayout.CENTER);
        planAndEmergencies.add(emergencyPanel, BorderLayout.SOUTH);

        JPanel tab = new JPanel(new BorderLayout());
        tab.add(hudPanel, BorderLayout.NORTH);
        tab.add(planAndEmergencies, BorderLayout.CENTER);
        tab.add(deviceStatus, BorderLayout.EAST);
        return tab;
    }

    /** Pestaña 2: historial de turnos arriba y terminal de eventos abajo. */
    private JSplitPane buildTelemetryTab() {
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, historyPanel, logPanel);
        split.setResizeWeight(0.4);
        split.setDividerSize(6);
        split.setBorder(null);
        split.setFocusable(false);
        return split;
    }

    private JPanel buildFooter() {
        JLabel commands = new JLabel(
                "Comandos vocales:   PUERTA   ·   GARAJE   ·   VENTANAS   ·   TELEVISOR   ·   ALARMA");
        commands.setForeground(TEXT_DIM);
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 6));
        footer.setBackground(HEADER_BG);
        footer.add(commands);
        return footer;
    }

    // ------------------------------------------------------------------ Lógica

    private void wireEngine() {
        engine.addListener(new GameEngine.Listener() {
            @Override
            public void onMessage(String message, LogType type) {
                logPanel.append(message, type);
            }
        });
    }

    /** Comando vocal recibido para un sector. */
    private void handleVoiceCommand(Sector sector) {
        systemLed.pulse();
        logPanel.append("Comando \"" + sector.name() + "\" procesado", LogType.VOICE);
        deviceStatus.registerCommand(sector);
        if (!engine.isRunning()) {
            housePlan.flash(sector, CYAN);
            logPanel.append("No hay turno en curso: inicia uno en el Centro de Control.", LogType.INFO);
            return;
        }
        switch (engine.handleCommand(sector)) {
            case NEUTRALIZED -> housePlan.flash(sector, GREEN);
            case FALSE_ALARM -> housePlan.flash(sector, ORANGE);
            default -> {
                // Bloqueada o ignorada: el motor ya registró el motivo.
            }
        }
    }

    /** Evento de anomalía detectado por el modelo KWS. */
    private void handleAnomaly() {
        systemLed.pulse();
        logPanel.append("[ANOMALÍA DETECTADA] -> Descongelando monitores", LogType.KWS);
        housePlan.flashAnomaly();
        if (!engine.isRunning()) {
            logPanel.append("Anomalía registrada fuera de turno: sin efecto en el juego.", LogType.INFO);
            return;
        }
        engine.handleAnomaly();
    }
}
