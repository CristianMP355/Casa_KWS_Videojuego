package com.casasegura;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;

/**
 * HUD superior del Centro de Control: PUNTAJE, NIVEL, VIDAS y HIGH SCORE, la barra de
 * RIESGO DE SEGURIDAD (0-100 %) y los botones INICIAR TURNO / TERMINAR TURNO.
 */
public class StatsHudPanel extends JPanel implements GameEngine.Listener {

    private static final Color CARD_BG = new Color(0x161E2D);
    private static final Color CARD_BORDER = new Color(0x24324A);
    private static final Color GREEN = new Color(0x00E676);
    private static final Color AMBER = new Color(0xFFC400);
    private static final Color RED = new Color(0xFF1744);
    private static final Color TEXT_DIM = new Color(0x7F8FA6);

    private final JLabel scoreValue = statValue();
    private final JLabel levelValue = statValue();
    private final JLabel highScoreValue = statValue();
    private final HeartsComponent hearts = new HeartsComponent();
    private final JProgressBar riskBar = new JProgressBar(0, 100);
    private final JLabel levelInfo = new JLabel();
    private final JButton startButton = new JButton("INICIAR TURNO");
    private final JButton stopButton = new JButton("TERMINAR TURNO");

    public StatsHudPanel(GameEngine engine) {
        super(new BorderLayout(0, 10));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0x1F2A3A)),
                BorderFactory.createEmptyBorder(10, 14, 10, 14)));

        JPanel stats = new JPanel(new GridLayout(1, 4, 12, 0));
        stats.setOpaque(false);
        stats.add(statCard("PUNTAJE", scoreValue));
        stats.add(statCard("NIVEL", levelValue));
        stats.add(statCard("VIDAS", hearts));
        stats.add(statCard("HIGH SCORE", highScoreValue));

        riskBar.setStringPainted(true);
        riskBar.setPreferredSize(new Dimension(100, 22));
        riskBar.setFont(riskBar.getFont().deriveFont(Font.BOLD, 12f));
        JLabel riskTitle = new JLabel("RIESGO DE SEGURIDAD");
        riskTitle.setForeground(TEXT_DIM);
        riskTitle.setFont(riskTitle.getFont().deriveFont(Font.BOLD, 11f));
        riskTitle.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 10));
        levelInfo.setForeground(TEXT_DIM);
        levelInfo.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 0));

        JPanel risk = new JPanel(new BorderLayout());
        risk.setOpaque(false);
        risk.add(riskTitle, BorderLayout.WEST);
        risk.add(riskBar, BorderLayout.CENTER);
        risk.add(levelInfo, BorderLayout.EAST);

        startButton.setFocusable(false);
        stopButton.setFocusable(false);
        startButton.setBackground(new Color(0x00838F));
        startButton.setForeground(Color.WHITE);
        startButton.setFont(startButton.getFont().deriveFont(Font.BOLD));
        startButton.addActionListener(e -> engine.start());
        stopButton.addActionListener(e -> engine.stop());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(startButton);
        buttons.add(stopButton);

        JPanel bottom = new JPanel(new BorderLayout(14, 0));
        bottom.setOpaque(false);
        bottom.add(risk, BorderLayout.CENTER);
        bottom.add(buttons, BorderLayout.EAST);

        add(stats, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        engine.addListener(this);
        onTick(engine);
    }

    private static JLabel statValue() {
        JLabel label = new JLabel("0");
        label.setFont(label.getFont().deriveFont(Font.BOLD, 22f));
        label.setForeground(Color.WHITE);
        return label;
    }

    private static JPanel statCard(String title, JComponent value) {
        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(TEXT_DIM);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 11f));
        JPanel card = new JPanel(new BorderLayout(12, 0));
        card.setBackground(CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CARD_BORDER),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)));
        card.add(titleLabel, BorderLayout.WEST);
        card.add(value, BorderLayout.EAST);
        return card;
    }

    @Override
    public void onTick(GameEngine e) {
        scoreValue.setText(String.valueOf(e.getScore()));
        levelValue.setText(String.valueOf(e.getLevel().getNumber()));
        highScoreValue.setText(String.valueOf(e.getHighScore()));
        hearts.setLives(e.getLives());
        levelInfo.setText(e.describeDifficulty() + "  ·  " + GameEngine.describeLevel(e.getLevel()));

        int risk = (int) Math.round(e.getRiskPercent());
        riskBar.setValue(risk);
        riskBar.setString(risk + " %");
        riskBar.setForeground(risk >= 80 ? RED : risk >= 50 ? AMBER : GREEN);

        startButton.setText(e.isRunning() ? "REINICIAR TURNO" : "INICIAR TURNO");
        stopButton.setEnabled(e.isRunning());
    }

    /** Corazones de vida dibujados vectorialmente. */
    private static final class HeartsComponent extends JComponent {
        private int lives = GameEngine.MAX_LIVES;

        HeartsComponent() {
            setPreferredSize(new Dimension(3 * 30, 28));
        }

        void setLives(int lives) {
            if (this.lives != lives) {
                this.lives = lives;
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double size = Math.min(getHeight() - 4, 22);
            double startX = getWidth() - GameEngine.MAX_LIVES * (size + 8) + 8;
            for (int i = 0; i < GameEngine.MAX_LIVES; i++) {
                double x = startX + i * (size + 8);
                double y = (getHeight() - size) / 2;
                Path2D heart = new Path2D.Double();
                heart.moveTo(x + size / 2, y + size * 0.95);
                heart.curveTo(x - size * 0.15, y + size * 0.45, x + size * 0.1, y - size * 0.1, x + size / 2, y + size * 0.28);
                heart.curveTo(x + size * 0.9, y - size * 0.1, x + size * 1.15, y + size * 0.45, x + size / 2, y + size * 0.95);
                heart.closePath();
                g2.setColor(i < lives ? RED : new Color(0x3A4150));
                g2.fill(heart);
            }
            g2.dispose();
        }
    }
}
