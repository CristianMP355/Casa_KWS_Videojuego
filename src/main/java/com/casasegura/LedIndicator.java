package com.casasegura;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;

/**
 * LED circular con halo luminoso. Puede emitir un destello breve ({@link #pulse()})
 * cada vez que llega una línea por el puerto serie.
 */
public class LedIndicator extends JComponent {

    private Color color = new Color(0x5A1A1A);
    private long pulseUntil;
    private final Timer pulseTimer = new Timer(60, e -> {
        repaint();
        if (System.currentTimeMillis() > pulseUntil) {
            ((Timer) e.getSource()).stop();
        }
    });

    public LedIndicator() {
        setPreferredSize(new Dimension(22, 22));
        setMinimumSize(getPreferredSize());
    }

    public void setColor(Color color) {
        this.color = color;
        repaint();
    }

    /** Destello breve de actividad. */
    public void pulse() {
        pulseUntil = System.currentTimeMillis() + 120;
        if (!pulseTimer.isRunning()) {
            pulseTimer.start();
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        boolean pulsing = System.currentTimeMillis() < pulseUntil;
        Color core = pulsing ? color.brighter().brighter() : color;
        float size = Math.min(getWidth(), getHeight());
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;

        // Halo
        float glowRadius = size / 2f;
        g2.setPaint(new RadialGradientPaint(new Point2D.Float(cx, cy), glowRadius,
                new float[]{0f, 1f},
                new Color[]{withAlpha(core, pulsing ? 160 : 110), withAlpha(core, 0)}));
        g2.fill(new Ellipse2D.Float(cx - glowRadius, cy - glowRadius, glowRadius * 2, glowRadius * 2));

        // Núcleo con brillo
        float r = size * 0.28f;
        g2.setPaint(new RadialGradientPaint(new Point2D.Float(cx - r / 3, cy - r / 3), r * 1.4f,
                new float[]{0f, 1f},
                new Color[]{core.brighter(), core.darker()}));
        g2.fill(new Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2));
        g2.dispose();
    }

    private static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }
}
