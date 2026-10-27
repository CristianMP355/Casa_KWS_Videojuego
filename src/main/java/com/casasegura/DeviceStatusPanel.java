package com.casasegura;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Columna lateral "ESTADO DE DISPOSITIVOS": una tarjeta por zona de seguridad con su estado
 * (rojo en intrusión, verde a salvo) y la confianza KWS emulada en tiempo real.
 * <p>
 * La confianza oscila levemente como lo haría la lectura de un sensor; cuando el operador
 * responde un sector se registra una nueva detección con su porcentaje.
 */
public class DeviceStatusPanel extends JPanel implements GameEngine.Listener {

    private static final Color CARD_BG = new Color(0x111C2E);
    private static final Color SAFE = new Color(0x00E676);
    private static final Color ALERT = new Color(0xFF1744);
    private static final Color CYAN = new Color(0x00E5FF);
    private static final Color TEXT_DIM = new Color(0x8FA9C7);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final GameEngine engine;
    private final Map<Sector, DeviceCard> cards = new EnumMap<>(Sector.class);
    private final Random random = new Random();

    public DeviceStatusPanel(GameEngine engine) {
        super(new BorderLayout(0, 10));
        this.engine = engine;
        setBackground(new Color(0x0D1524));
        setPreferredSize(new Dimension(310, 100));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(0x1F2A3A)),
                BorderFactory.createEmptyBorder(14, 14, 14, 14)));

        JLabel title = new JLabel("ESTADO DE DISPOSITIVOS");
        title.setForeground(CYAN);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));

        JPanel list = new ScrollableList(new GridLayout(Sector.values().length, 1, 0, 8));
        for (Sector sector : new Sector[]{Sector.ALARMA, Sector.GARAJE, Sector.PUERTA,
                Sector.TELEVISOR, Sector.VENTANAS}) {
            DeviceCard card = new DeviceCard(sector, 94 + random.nextDouble() * 5);
            cards.put(sector, card);
            list.add(card);
        }

        // Si la ventana es baja, la lista se desplaza en lugar de encimar el texto.
        JScrollPane scroll = new JScrollPane(list, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        add(title, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);

        // Oscilación de la confianza emulada (lectura "en vivo").
        new Timer(500, e -> {
            for (DeviceCard card : cards.values()) {
                card.jitter(random.nextGaussian() * 0.25);
            }
        }).start();

        engine.addListener(this);
        onTick(engine);
    }

    /** Registra una detección del operador sobre el sector con una confianza emulada. */
    public void registerCommand(Sector sector) {
        DeviceCard card = cards.get(sector);
        if (card != null) {
            card.registerDetection(82 + random.nextDouble() * 17.8);
        }
    }

    @Override
    public void onTick(GameEngine e) {
        for (Map.Entry<Sector, DeviceCard> entry : cards.entrySet()) {
            entry.getValue().showThreat(e.getThreat(entry.getKey()));
        }
    }

    /** Texto de estado de cada dispositivo: {en alerta, a salvo}. */
    private static String[] statusTexts(Sector sector) {
        return switch (sector) {
            case ALARMA -> new String[]{"ACTIVADA", "DESACTIVADA"};
            case GARAJE -> new String[]{"ABIERTO", "CERRADO"};
            case PUERTA -> new String[]{"DESBLOQUEADA", "BLOQUEADA"};
            case TELEVISOR -> new String[]{"FALLA CC", "OK"};
            case VENTANAS -> new String[]{"ABIERTAS", "PROTEGIDAS"};
        };
    }

    /** Lista que llena el alto disponible y solo se desplaza cuando no cabe. */
    private static final class ScrollableList extends JPanel implements Scrollable {
        ScrollableList(GridLayout layout) {
            super(layout);
            setOpaque(false);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return visibleRect.height;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return getParent() != null && getParent().getHeight() > getPreferredSize().height;
        }
    }

    /** Tarjeta gráfica de un dispositivo. */
    private static final class DeviceCard extends JPanel {
        private final Sector sector;
        private final LedIndicator led = new LedIndicator();
        private final JLabel statusLabel = new JLabel();
        private final JLabel confidenceLabel = new JLabel();
        private final JProgressBar confidenceBar = new JProgressBar(0, 1000);
        private final JLabel detailLabel = new JLabel();
        private double confidence;
        private String lastDetection = "Sin detecciones";
        private boolean alert;

        DeviceCard(Sector sector, double initialConfidence) {
            super(new BorderLayout(10, 4));
            this.sector = sector;
            this.confidence = initialConfidence;
            setBackground(CARD_BG);

            JLabel name = new JLabel(sector.name());
            name.setForeground(Color.WHITE);
            name.setFont(name.getFont().deriveFont(Font.BOLD, 13f));
            statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 13.5f));
            statusLabel.setHorizontalAlignment(SwingConstants.RIGHT);

            JPanel top = new JPanel(new BorderLayout(8, 0));
            top.setOpaque(false);
            top.add(led, BorderLayout.WEST);
            top.add(name, BorderLayout.CENTER);
            top.add(statusLabel, BorderLayout.EAST);

            led.setPreferredSize(new Dimension(16, 16));
            confidenceLabel.setForeground(TEXT_DIM);
            confidenceLabel.setFont(confidenceLabel.getFont().deriveFont(11.5f));
            confidenceBar.setPreferredSize(new Dimension(60, 6));
            confidenceBar.setForeground(CYAN);
            detailLabel.setForeground(TEXT_DIM);
            detailLabel.setFont(detailLabel.getFont().deriveFont(11f));

            // Fila compacta: texto de confianza + barra centrada verticalmente.
            JPanel confidenceRow = new JPanel(new GridBagLayout());
            confidenceRow.setOpaque(false);
            GridBagConstraints gc = new GridBagConstraints();
            gc.anchor = GridBagConstraints.WEST;
            confidenceRow.add(confidenceLabel, gc);
            gc.gridx = 1;
            gc.weightx = 1;
            gc.fill = GridBagConstraints.HORIZONTAL;
            gc.insets = new Insets(0, 8, 0, 0);
            confidenceRow.add(confidenceBar, gc);

            JPanel bottom = new JPanel(new GridLayout(2, 1, 0, 2));
            bottom.setOpaque(false);
            bottom.add(confidenceRow);
            bottom.add(detailLabel);

            add(top, BorderLayout.NORTH);
            add(bottom, BorderLayout.CENTER);
            showThreat(null);
            refreshConfidence();
        }

        void showThreat(GameEngine.Threat threat) {
            boolean nowAlert = threat != null;
            if (nowAlert != alert || getBorder() == null) {
                // Solo se reconstruye el estilo cuando cambia el estado.
                alert = nowAlert;
                Color color = alert ? ALERT : SAFE;
                statusLabel.setText(alert ? "INTRUSIÓN EN CURSO" : "SEGURA");
                statusLabel.setForeground(color);
                led.setColor(color);
                setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(alert ? ALERT : new Color(0x1F3A3A), alert ? 2 : 1),
                        BorderFactory.createEmptyBorder(6, 10, 6, 10)));
            }
            // Estado propio del dispositivo (p. ej. BLOQUEADA / DESBLOQUEADA) + detalle en vivo.
            String deviceState = statusTexts(sector)[alert ? 0 : 1];
            detailLabel.setText(deviceState + "  ·  " + (alert
                    ? String.format(Locale.US, "responde en %.1f s", threat.getRemainingSeconds())
                    : lastDetection));
            detailLabel.setForeground(alert ? ALERT : TEXT_DIM);
        }

        void jitter(double delta) {
            confidence = Math.max(80, Math.min(99.9, confidence + delta));
            refreshConfidence();
        }

        void registerDetection(double detectedConfidence) {
            confidence = detectedConfidence;
            lastDetection = String.format(Locale.US, "Última detección %s (%.1f %%)",
                    LocalTime.now().format(TIME_FORMAT), detectedConfidence);
            led.pulse();
            refreshConfidence();
            if (!alert) {
                detailLabel.setText(statusTexts(sector)[1] + "  ·  " + lastDetection);
            }
        }

        private void refreshConfidence() {
            confidenceLabel.setText(String.format(Locale.US, "Confianza KWS: %.1f %%", confidence));
            confidenceBar.setValue((int) Math.round(confidence * 10));
        }
    }
}
