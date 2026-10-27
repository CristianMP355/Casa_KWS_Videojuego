package com.casasegura;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Panel "EMERGENCIAS EN TIEMPO REAL" del Centro de Control: una tarjeta por intrusión activa
 * con el nombre de la alerta, el comando de voz requerido y la barra de tiempo regresivo.
 * Muestra además un aviso cuando los monitores están congelados o termina el turno.
 */
public class EmergencyPanel extends JPanel implements GameEngine.Listener {

    private static final Color CYAN = new Color(0x00E5FF);
    private static final Color GREEN = new Color(0x00E676);
    private static final Color AMBER = new Color(0xFFC400);
    private static final Color RED = new Color(0xFF1744);
    private static final Color MAGENTA = new Color(0xFF4081);
    private static final Color TEXT_DIM = new Color(0x7F8FA6);

    private final GameEngine engine;
    private final JLabel banner = new JLabel(" ", SwingConstants.RIGHT);
    private final JPanel cardsContainer = new JPanel(new GridLayout(1, 3, 12, 0));
    private final Map<Sector, EmergencyCard> cards = new EnumMap<>(Sector.class);

    public EmergencyPanel(GameEngine engine) {
        super(new BorderLayout(0, 8));
        this.engine = engine;
        setBackground(new Color(0x0D1524));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0x1F2A3A)),
                BorderFactory.createEmptyBorder(10, 14, 12, 14)));
        setPreferredSize(new Dimension(100, 150));

        JLabel title = new JLabel("EMERGENCIAS EN TIEMPO REAL");
        title.setForeground(CYAN);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
        banner.setFont(banner.getFont().deriveFont(Font.BOLD, 13f));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(title, BorderLayout.WEST);
        header.add(banner, BorderLayout.EAST);

        cardsContainer.setOpaque(false);
        add(header, BorderLayout.NORTH);
        add(cardsContainer, BorderLayout.CENTER);

        engine.addListener(this);
        onThreatsChanged(engine.getActiveThreats());
        onTick(engine);
    }

    @Override
    public void onThreatsChanged(List<GameEngine.Threat> activeThreats) {
        cardsContainer.removeAll();
        cards.clear();
        if (activeThreats.isEmpty()) {
            String text = engine.isRunning()
                    ? "Sin intrusiones activas. Mantente alerta..."
                    : "Presiona INICIAR TURNO para comenzar la vigilancia nocturna.";
            JLabel placeholder = new JLabel(text, SwingConstants.CENTER);
            placeholder.setForeground(TEXT_DIM);
            placeholder.setFont(placeholder.getFont().deriveFont(Font.ITALIC, 14f));
            cardsContainer.add(placeholder);
        } else {
            for (GameEngine.Threat threat : activeThreats) {
                EmergencyCard card = new EmergencyCard(threat.getSector());
                card.showThreat(threat, engine.isFrozen());
                cards.put(threat.getSector(), card);
                cardsContainer.add(card);
            }
        }
        cardsContainer.revalidate();
        cardsContainer.repaint();
    }

    @Override
    public void onTick(GameEngine e) {
        if (e.isFrozen()) {
            banner.setText(String.format(Locale.US,
                    "MONITORES CONGELADOS POR ANOMALÍA  (%.1f s)", e.getFrozenRemaining()));
            banner.setForeground(MAGENTA);
        } else if (e.isRunning()) {
            banner.setText(" ");
        }
        for (Map.Entry<Sector, EmergencyCard> entry : cards.entrySet()) {
            GameEngine.Threat threat = e.getThreat(entry.getKey());
            if (threat != null) {
                entry.getValue().showThreat(threat, e.isFrozen());
            }
        }
    }

    @Override
    public void onGameOver(int finalScore, boolean newRecord) {
        banner.setText(newRecord
                ? "FIN DEL TURNO  ·  ¡NUEVO HIGH SCORE: " + finalScore + " pts!"
                : "FIN DEL TURNO  ·  Puntaje final: " + finalScore + " pts");
        banner.setForeground(newRecord ? GREEN : AMBER);
    }

    /** Tarjeta de alerta con barra de tiempo regresivo para una intrusión. */
    private static final class EmergencyCard extends JPanel {
        private final JLabel timeLabel = new JLabel();
        private final JProgressBar bar = new JProgressBar(0, 1000);

        EmergencyCard(Sector sector) {
            super(new BorderLayout(0, 6));
            setBackground(new Color(0x24101A));
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(RED, 2),
                    BorderFactory.createEmptyBorder(8, 12, 8, 12)));

            JLabel name = new JLabel("INTRUSIÓN EN " + sector.name());
            name.setForeground(Color.WHITE);
            name.setFont(name.getFont().deriveFont(Font.BOLD, 17f));
            JLabel command = new JLabel("Comando vocal requerido: \"" + sector.name() + "\"");
            command.setForeground(TEXT_DIM);

            JPanel text = new JPanel(new GridLayout(2, 1, 0, 2));
            text.setOpaque(false);
            text.add(name);
            text.add(command);

            timeLabel.setFont(timeLabel.getFont().deriveFont(Font.BOLD, 16f));
            bar.setPreferredSize(new Dimension(100, 12));
            JPanel bottom = new JPanel(new BorderLayout(10, 0));
            bottom.setOpaque(false);
            bottom.add(bar, BorderLayout.CENTER);
            bottom.add(timeLabel, BorderLayout.EAST);

            add(text, BorderLayout.CENTER);
            add(bottom, BorderLayout.SOUTH);
        }

        void showThreat(GameEngine.Threat threat, boolean frozen) {
            if (!frozen) {
                // Con los monitores congelados la tarjeta conserva la última lectura.
                bar.setValue((int) Math.round(threat.getFraction() * 1000));
                timeLabel.setText(String.format(Locale.US, "%.1f s", threat.getRemainingSeconds()));
            }
            double fraction = threat.getFraction();
            Color color = frozen ? MAGENTA : fraction > 0.6 ? GREEN : fraction > 0.3 ? AMBER : RED;
            bar.setForeground(color);
            timeLabel.setForeground(color);
        }
    }
}
