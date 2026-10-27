package com.casasegura;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Plano arquitectónico (estilo blueprint) de la casa con cuatro áreas:
 * <ul>
 *   <li>HABITACIÓN: zona Ventanas.</li>
 *   <li>COCINA / COMEDOR: zona Alarma y Televisor.</li>
 *   <li>SALA: zona Puerta principal.</li>
 *   <li>GARAJE: zona Portón vehicular.</li>
 * </ul>
 * Un área con una intrusión activa (o durante una anomalía) parpadea en rojo neón con el texto
 * {@code ¡INTRUSIÓN EN CURSO!}; a salvo se muestra en cyan/verde con {@code SEGURO / OK}.
 * Al hacer clic sobre un dispositivo (o sobre un área de un solo sector) se envía su respuesta.
 */
public class HousePlanPanel extends JPanel {

    /** Área del plano y los sectores (dispositivos) que contiene. */
    private record Room(String name, String zone, Rectangle2D bounds, List<Sector> sectors) {
    }

    /** Espacio de coordenadas virtual del plano; se escala al tamaño del panel. */
    private static final double VIRTUAL_W = 1000;
    private static final double VIRTUAL_H = 620;
    private static final double ROOM_INSET = 10;
    private static final double DEVICE_SIZE = 64;
    private static final int FLASH_MS = 450;

    private static final Color BLUEPRINT_BG = new Color(0x0A1A2F);
    private static final Color BLUEPRINT_GRID = new Color(120, 180, 255, 22);
    private static final Color BLUEPRINT_GRID_MAJOR = new Color(120, 180, 255, 40);
    private static final Color WALL = new Color(0xCFE8FF);
    private static final Color CYAN = new Color(0x00E5FF);
    private static final Color SAFE = new Color(0x00E676);
    private static final Color NEON_RED = new Color(0xFF1744);
    private static final Color ANOMALY = new Color(0xFF4081);
    private static final Color TEXT = new Color(0xE3F2FD);
    private static final Color TEXT_DIM = new Color(0x8FA9C7);

    private final GameEngine engine;
    private final List<Room> rooms = List.of(
            new Room("HABITACIÓN", "Zona Ventanas",
                    new Rectangle2D.Double(0, 0, 420, 300), List.of(Sector.VENTANAS)),
            new Room("COCINA / COMEDOR", "Zona Alarma / TV",
                    new Rectangle2D.Double(420, 0, 580, 300), List.of(Sector.ALARMA, Sector.TELEVISOR)),
            new Room("SALA", "Zona Puerta Principal",
                    new Rectangle2D.Double(0, 300, 600, 320), List.of(Sector.PUERTA)),
            new Room("GARAJE", "Zona Portón Vehicular",
                    new Rectangle2D.Double(600, 300, 400, 320), List.of(Sector.GARAJE)));
    /** Centro de cada dispositivo dentro del plano. */
    private final Map<Sector, Point2D> devices = new EnumMap<>(Sector.class);
    private final Map<Sector, Long> flashUntil = new EnumMap<>(Sector.class);
    private final Map<Sector, Color> flashColor = new EnumMap<>(Sector.class);
    private final Timer animationTimer = new Timer(50, e -> repaint());
    private long anomalyFlashUntil;
    private Sector hovered;
    private Consumer<Sector> sectorClickHandler;
    private AffineTransform viewTransform = new AffineTransform();

    // Desplazamiento del plano (píxeles de pantalla) y estado del arrastre
    private double translateX;
    private double translateY;
    private double dragOriginX;
    private double dragOriginY;
    private Point dragStart;
    private Rectangle centerButton = new Rectangle();
    private boolean hoverCenterButton;
    /** Animación suave de regreso al centro. */
    private final Timer recenterTimer = new Timer(15, e -> recenterStep());

    public HousePlanPanel(GameEngine engine) {
        this.engine = engine;
        setOpaque(true);
        setFocusable(true);

        devices.put(Sector.VENTANAS, new Point2D.Double(210, 165));
        devices.put(Sector.ALARMA, new Point2D.Double(590, 165));
        devices.put(Sector.TELEVISOR, new Point2D.Double(840, 165));
        devices.put(Sector.PUERTA, new Point2D.Double(300, 470));
        devices.put(Sector.GARAJE, new Point2D.Double(800, 470));

        // Navegación tipo "canvas drag": clic sostenido + arrastre desplaza el plano en X/Y.
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                if (centerButton.contains(e.getPoint())) {
                    centerView();
                    return;
                }
                dragStart = e.getPoint();
                dragOriginX = translateX;
                dragOriginY = translateY;
                recenterTimer.stop();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragStart == null) {
                    return;
                }
                double limitX = getWidth() * 0.6;
                double limitY = getHeight() * 0.6;
                translateX = clamp(dragOriginX + e.getX() - dragStart.x, limitX);
                translateY = clamp(dragOriginY + e.getY() - dragStart.y, limitY);
                setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragStart = null;
                updateHoverCursor(e);
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                updateHoverCursor(e);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hovered = null;
                hoverCenterButton = false;
                repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (centerButton.contains(e.getPoint())) {
                    return; // Ya se atendió en mousePressed
                }
                if (e.getClickCount() == 2) {
                    centerView();
                    return;
                }
                Sector clicked = sectorAt(e.getX(), e.getY());
                if (clicked != null && sectorClickHandler != null) {
                    sectorClickHandler.accept(clicked);
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        animationTimer.start();
    }

    /** Acción al hacer clic sobre un sector ({@code null} lo desactiva). */
    public void setSectorClickHandler(Consumer<Sector> handler) {
        this.sectorClickHandler = handler;
    }

    /** Destello breve en un dispositivo con el color indicado. */
    public void flash(Sector sector, Color color) {
        flashUntil.put(sector, System.currentTimeMillis() + FLASH_MS);
        flashColor.put(sector, color);
        repaint();
    }

    /** Destello magenta general por anomalía. */
    public void flashAnomaly() {
        anomalyFlashUntil = System.currentTimeMillis() + 600;
        repaint();
    }

    /** Regresa el plano a su posición original con una animación suave. */
    public void centerView() {
        dragStart = null;
        recenterTimer.start();
    }

    private void recenterStep() {
        translateX *= 0.78;
        translateY *= 0.78;
        if (Math.abs(translateX) < 0.5 && Math.abs(translateY) < 0.5) {
            translateX = 0;
            translateY = 0;
            recenterTimer.stop();
        }
        repaint();
    }

    private boolean isDisplaced() {
        return Math.abs(translateX) > 1 || Math.abs(translateY) > 1;
    }

    private static double clamp(double value, double limit) {
        return Math.max(-limit, Math.min(limit, value));
    }

    private void updateHoverCursor(MouseEvent e) {
        Sector over = sectorAt(e.getX(), e.getY());
        boolean overButton = centerButton.contains(e.getPoint());
        if (over != hovered || overButton != hoverCenterButton) {
            hovered = over;
            hoverCenterButton = overButton;
            repaint();
        }
        boolean clickable = overButton || (over != null && sectorClickHandler != null);
        setCursor(Cursor.getPredefinedCursor(clickable ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
    }

    /** Primero se busca un dispositivo bajo el cursor; si no, un área con un único sector. */
    private Sector sectorAt(int x, int y) {
        Point2D p;
        try {
            p = viewTransform.inverseTransform(new Point2D.Double(x, y), null);
        } catch (NoninvertibleTransformException e) {
            return null;
        }
        for (Map.Entry<Sector, Point2D> device : devices.entrySet()) {
            if (device.getValue().distance(p) <= DEVICE_SIZE * 0.75) {
                return device.getKey();
            }
        }
        for (Room room : rooms) {
            if (room.bounds().contains(p) && room.sectors().size() == 1) {
                return room.sectors().get(0);
            }
        }
        return null;
    }

    private boolean isRoomInAlert(Room room) {
        if (engine.isFrozen()) {
            return true; // Anomalía activa: toda la casa en alerta
        }
        for (Sector sector : room.sectors()) {
            if (engine.getThreat(sector) != null) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ Dibujo

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        long now = System.currentTimeMillis();

        // Papel blueprint con cuadrícula (se desplaza junto con el plano)
        g2.setColor(BLUEPRINT_BG);
        g2.fillRect(0, 0, w, h);
        int gridX = (int) Math.floorMod(Math.round(translateX), 100L);
        int gridY = (int) Math.floorMod(Math.round(translateY), 100L);
        for (int x = gridX - 100, i = 0; x < w; x += 20, i++) {
            g2.setColor(i % 5 == 0 ? BLUEPRINT_GRID_MAJOR : BLUEPRINT_GRID);
            g2.drawLine(x, 0, x, h);
        }
        for (int y = gridY - 100, i = 0; y < h; y += 20, i++) {
            g2.setColor(i % 5 == 0 ? BLUEPRINT_GRID_MAJOR : BLUEPRINT_GRID);
            g2.drawLine(0, y, w, y);
        }

        double top = 56;
        double margin = 30;
        double scale = Math.min((w - margin * 2) / VIRTUAL_W, (h - top - margin) / VIRTUAL_H);
        if (scale <= 0) {
            g2.dispose();
            return;
        }
        double ox = (w - VIRTUAL_W * scale) / 2 + translateX;
        double oy = top + (h - top - margin - VIRTUAL_H * scale) / 2 + translateY;
        viewTransform = new AffineTransform(scale, 0, 0, scale, ox, oy);

        Graphics2D plan = (Graphics2D) g2.create();
        plan.transform(viewTransform);

        boolean blinkOn = (now / 250) % 2 == 0;
        for (Room room : rooms) {
            drawRoomState(plan, room, blinkOn);
        }
        drawWalls(plan);
        for (Room room : rooms) {
            drawRoomLabels(plan, room, blinkOn);
            for (Sector sector : room.sectors()) {
                drawDevice(plan, sector, now, blinkOn);
            }
        }

        if (now < anomalyFlashUntil) {
            float alpha = (anomalyFlashUntil - now) / 600f;
            plan.setColor(new Color(255, 64, 129, Math.round(80 * alpha)));
            plan.fill(new Rectangle2D.Double(0, 0, VIRTUAL_W, VIRTUAL_H));
        }
        plan.dispose();

        if (engine.isFrozen()) {
            for (int y = 0; y < h; y += 4) {
                g2.setColor(new Color(255, 64, 129, 14));
                g2.drawLine(0, y, w, y);
            }
        }
        drawHeader(g2, w); // Encima del plano para que no lo tape al arrastrar
        drawNavigationControls(g2, w, h);
        g2.dispose();
    }

    /** Botón discreto "CENTRAR VISTA" y ayuda de navegación en la esquina inferior. */
    private void drawNavigationControls(Graphics2D g2, int w, int h) {
        g2.setFont(getFont().deriveFont(Font.BOLD, 12f));
        FontMetrics fm = g2.getFontMetrics();
        String label = "CENTRAR VISTA";
        int bw = fm.stringWidth(label) + 28;
        int bh = 26;
        centerButton = new Rectangle(w - bw - 14, h - bh - 12, bw, bh);

        boolean active = isDisplaced();
        Color border = hoverCenterButton ? CYAN : (active ? new Color(0, 229, 255, 200) : new Color(0, 229, 255, 70));
        g2.setColor(new Color(10, 26, 47, 220));
        g2.fillRoundRect(centerButton.x, centerButton.y, bw, bh, bh, bh);
        g2.setColor(border);
        g2.drawRoundRect(centerButton.x, centerButton.y, bw, bh, bh, bh);
        // Pequeña mira
        int cx = centerButton.x + 12;
        int cy = centerButton.y + bh / 2;
        g2.drawOval(cx - 4, cy - 4, 8, 8);
        g2.drawLine(cx - 7, cy, cx + 7, cy);
        g2.drawLine(cx, cy - 7, cx, cy + 7);
        g2.setColor(active || hoverCenterButton ? TEXT : TEXT_DIM);
        g2.drawString(label, centerButton.x + 22, centerButton.y + (bh + fm.getAscent() - fm.getDescent()) / 2);

        g2.setFont(getFont().deriveFont(Font.PLAIN, 11f));
        g2.setColor(TEXT_DIM);
        String hint = active
                ? String.format(Locale.US, "Vista desplazada (%+.0f, %+.0f)  ·  doble clic para centrar", translateX, translateY)
                : "Arrastra para desplazar el plano";
        g2.drawString(hint, 16, h - 18);
    }

    private void drawHeader(Graphics2D g2, int w) {
        // Franja semitransparente para que el plano desplazado pase por debajo del título
        g2.setColor(new Color(10, 26, 47, 215));
        g2.fillRect(0, 0, w, 46);
        g2.setFont(getFont().deriveFont(Font.BOLD, 15f));
        g2.setColor(CYAN);
        g2.drawString("PLANO 2D DE LA CASA  //  CENTRO DE CONTROL", 20, 30);

        String status;
        Color color;
        int threats = engine.getActiveThreats().size();
        if (engine.isFrozen()) {
            status = String.format(Locale.US, "ANOMALÍA: MONITORES CONGELADOS %.1f s", engine.getFrozenRemaining());
            color = ANOMALY;
        } else if (!engine.isRunning()) {
            status = "SISTEMA EN ESPERA";
            color = TEXT_DIM;
        } else if (threats == 0) {
            status = "TODAS LAS ZONAS SEGURAS";
            color = SAFE;
        } else {
            status = threats + (threats == 1 ? " INTRUSIÓN ACTIVA" : " INTRUSIONES SIMULTÁNEAS");
            color = NEON_RED;
        }
        g2.setFont(getFont().deriveFont(Font.BOLD, 13f));
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(color);
        g2.drawString(status, w - fm.stringWidth(status) - 20, 30);
    }

    /** Fondo y contorno interior del área: rojo neón parpadeante en alerta, cyan a salvo. */
    private void drawRoomState(Graphics2D g, Room room, boolean blinkOn) {
        Rectangle2D b = room.bounds();
        RoundRectangle2D inner = new RoundRectangle2D.Double(b.getX() + ROOM_INSET, b.getY() + ROOM_INSET,
                b.getWidth() - ROOM_INSET * 2, b.getHeight() - ROOM_INSET * 2, 14, 14);

        if (isRoomInAlert(room)) {
            g.setColor(new Color(255, 23, 68, blinkOn ? 95 : 35));
            g.fill(inner);
            g.setStroke(new BasicStroke(12f));
            g.setColor(new Color(255, 23, 68, blinkOn ? 90 : 30));
            g.draw(inner);
            g.setStroke(new BasicStroke(3.5f));
            g.setColor(blinkOn ? NEON_RED : NEON_RED.darker());
            g.draw(inner);
        } else {
            g.setColor(new Color(0, 229, 255, 14));
            g.fill(inner);
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                    new float[]{10f, 6f}, 0f));
            g.setColor(new Color(0, 229, 255, 150));
            g.draw(inner);
        }
    }

    /** Muros, puertas, ventanas y portón del plano arquitectónico. */
    private void drawWalls(Graphics2D g) {
        g.setColor(WALL);

        // Muros exteriores e interiores
        g.setStroke(new BasicStroke(7f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
        g.draw(new Rectangle2D.Double(0, 0, VIRTUAL_W, VIRTUAL_H));
        g.setStroke(new BasicStroke(4.5f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
        g.draw(new Line2D.Double(0, 300, VIRTUAL_W, 300));
        g.draw(new Line2D.Double(420, 0, 420, 300));
        g.draw(new Line2D.Double(600, 300, 600, VIRTUAL_H));

        // Vanos de puertas interiores (se "borran" con el color del papel) y su arco de apertura
        clearOpening(g, new Line2D.Double(300, 300, 375, 300));   // Habitación ↔ Sala
        clearOpening(g, new Line2D.Double(470, 300, 545, 300));   // Cocina ↔ Sala
        clearOpening(g, new Line2D.Double(600, 380, 600, 455));   // Sala ↔ Garaje
        clearOpening(g, new Line2D.Double(420, 120, 420, 195));   // Habitación ↔ Cocina
        g.setColor(WALL);
        g.setStroke(new BasicStroke(1.5f));
        g.draw(new Arc2D.Double(300 - 75, 300 - 75, 150, 150, 270, 90, Arc2D.OPEN));
        g.draw(new Arc2D.Double(470 - 75, 300 - 75, 150, 150, 270, 90, Arc2D.OPEN));
        g.draw(new Arc2D.Double(600 - 75, 380 - 75, 150, 150, 270, 90, Arc2D.OPEN));

        // Puerta principal (Sala, muro inferior)
        clearOpening(g, new Line2D.Double(420, VIRTUAL_H, 510, VIRTUAL_H));
        g.setColor(CYAN);
        g.setStroke(new BasicStroke(3f));
        g.draw(new Line2D.Double(420, VIRTUAL_H, 420, VIRTUAL_H - 90));
        g.setStroke(new BasicStroke(1.5f));
        g.draw(new Arc2D.Double(420 - 90, VIRTUAL_H - 90, 180, 180, 0, 90, Arc2D.OPEN));

        // Ventanas de la habitación (muro superior e izquierdo): triple línea
        drawWindow(g, new Line2D.Double(70, 0, 350, 0));
        drawWindow(g, new Line2D.Double(0, 60, 0, 240));
        // Ventana de la cocina
        drawWindow(g, new Line2D.Double(650, 0, 880, 0));

        // Portón vehicular del garaje (muro inferior, segmentado)
        clearOpening(g, new Line2D.Double(640, VIRTUAL_H, 960, VIRTUAL_H));
        g.setColor(CYAN);
        g.setStroke(new BasicStroke(5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                new float[]{26f, 8f}, 0f));
        g.draw(new Line2D.Double(640, VIRTUAL_H, 960, VIRTUAL_H));
    }

    private static void clearOpening(Graphics2D g, Line2D segment) {
        g.setColor(BLUEPRINT_BG);
        g.setStroke(new BasicStroke(9f));
        g.draw(segment);
    }

    private static void drawWindow(Graphics2D g, Line2D segment) {
        clearOpening(g, segment);
        g.setColor(WALL);
        g.setStroke(new BasicStroke(1.5f));
        boolean horizontal = segment.getY1() == segment.getY2();
        for (int offset = -4; offset <= 4; offset += 4) {
            double dx = horizontal ? 0 : offset;
            double dy = horizontal ? offset : 0;
            g.draw(new Line2D.Double(segment.getX1() + dx, segment.getY1() + dy,
                    segment.getX2() + dx, segment.getY2() + dy));
        }
    }

    private void drawRoomLabels(Graphics2D g, Room room, boolean blinkOn) {
        Rectangle2D b = room.bounds();
        float x = (float) b.getX() + 28;

        g.setFont(getFont().deriveFont(Font.BOLD, 24f));
        g.setColor(TEXT);
        g.drawString(room.name(), x, (float) b.getY() + 48);
        g.setFont(getFont().deriveFont(Font.PLAIN, 15f));
        g.setColor(TEXT_DIM);
        g.drawString(room.zone(), x, (float) b.getY() + 70);

        boolean alert = isRoomInAlert(room);
        g.setFont(getFont().deriveFont(Font.BOLD, 19f));
        if (alert) {
            g.setColor(blinkOn ? NEON_RED : NEON_RED.darker());
            g.drawString("¡INTRUSIÓN EN CURSO!", x, (float) b.getMaxY() - 28);
        } else {
            g.setColor(SAFE);
            g.drawString("SEGURO / OK", x, (float) b.getMaxY() - 28);
        }
    }

    /** Dispositivo del sector: icono, nombre, estado y barra de tiempo si hay intrusión. */
    private void drawDevice(Graphics2D g, Sector sector, long now, boolean blinkOn) {
        Point2D c = devices.get(sector);
        GameEngine.Threat threat = engine.getThreat(sector);
        Long flashEnd = flashUntil.get(sector);
        boolean flashing = flashEnd != null && now < flashEnd;

        double r = DEVICE_SIZE * 0.75;
        Ellipse2D halo = new Ellipse2D.Double(c.getX() - r, c.getY() - r, r * 2, r * 2);
        if (flashing) {
            Color fc = flashColor.getOrDefault(sector, CYAN);
            float alpha = (flashEnd - now) / (float) FLASH_MS;
            g.setColor(new Color(fc.getRed(), fc.getGreen(), fc.getBlue(), Math.round(150 * alpha)));
            g.fill(halo);
        }
        g.setColor(new Color(10, 26, 47, 200));
        g.fill(halo);
        g.setStroke(new BasicStroke(sector == hovered ? 3f : 1.5f));
        g.setColor(threat != null ? NEON_RED : (sector == hovered ? CYAN : new Color(0, 229, 255, 120)));
        g.draw(halo);

        g.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(threat != null ? (blinkOn ? NEON_RED : NEON_RED.darker()) : CYAN);
        drawIcon(g, sector, c.getX(), c.getY(), DEVICE_SIZE * 0.7);

        // Indicador luminoso del dispositivo
        Color led = threat != null ? (blinkOn ? NEON_RED : NEON_RED.darker().darker()) : SAFE;
        g.setColor(led);
        g.fill(new Ellipse2D.Double(c.getX() + r * 0.55, c.getY() - r * 0.95, 14, 14));

        // Nombre del sector y su estado
        g.setFont(getFont().deriveFont(Font.BOLD, 14f));
        String name = sector.name() + "  ·  ";
        String state = threat != null ? "INTRUSIÓN" : "SEGURA";
        FontMetrics fm = g.getFontMetrics();
        float labelX = (float) (c.getX() - fm.stringWidth(name + state) / 2.0);
        float labelY = (float) (c.getY() + r + 20);
        g.setColor(TEXT);
        g.drawString(name, labelX, labelY);
        g.setColor(threat != null ? NEON_RED : SAFE);
        g.drawString(state, labelX + fm.stringWidth(name), labelY);

        if (threat != null) {
            double barW = 150;
            double barX = c.getX() - barW / 2;
            double barY = c.getY() + r + 30;
            g.setColor(new Color(255, 255, 255, 30));
            g.fill(new RoundRectangle2D.Double(barX, barY, barW, 9, 9, 9));
            g.setColor(NEON_RED);
            g.fill(new RoundRectangle2D.Double(barX, barY, barW * threat.getFraction(), 9, 9, 9));
            String seconds = String.format(Locale.US, "%.1f s", threat.getRemainingSeconds());
            g.setFont(getFont().deriveFont(Font.BOLD, 13f));
            g.drawString(seconds, (float) (barX + barW + 8), (float) (barY + 9));
        }
    }

    /** Iconos vectoriales simples para cada dispositivo. */
    private static void drawIcon(Graphics2D g, Sector sector, double cx, double cy, double s) {
        double half = s / 2;
        switch (sector) {
            case ALARMA -> {
                // Sirena con ondas
                g.draw(new Arc2D.Double(cx - half * 0.6, cy - half * 0.6, s * 0.6, s * 0.8, 0, 180, Arc2D.OPEN));
                g.draw(new Line2D.Double(cx - half * 0.75, cy + half * 0.2, cx + half * 0.75, cy + half * 0.2));
                g.draw(new Arc2D.Double(cx - half * 1.1, cy - half * 0.9, s * 1.1, s * 1.1, 120, 50, Arc2D.OPEN));
                g.draw(new Arc2D.Double(cx - half * 1.1, cy - half * 0.9, s * 1.1, s * 1.1, 10, 50, Arc2D.OPEN));
            }
            case GARAJE -> {
                // Carro
                g.draw(new RoundRectangle2D.Double(cx - half, cy - half * 0.2, s, half * 0.7, 14, 14));
                g.draw(new Line2D.Double(cx - half * 0.55, cy - half * 0.2, cx - half * 0.3, cy - half * 0.65));
                g.draw(new Line2D.Double(cx - half * 0.3, cy - half * 0.65, cx + half * 0.35, cy - half * 0.65));
                g.draw(new Line2D.Double(cx + half * 0.35, cy - half * 0.65, cx + half * 0.6, cy - half * 0.2));
                double wheel = half * 0.4;
                g.draw(new Ellipse2D.Double(cx - half * 0.65 - wheel / 2, cy + half * 0.5 - wheel / 2, wheel, wheel));
                g.draw(new Ellipse2D.Double(cx + half * 0.65 - wheel / 2, cy + half * 0.5 - wheel / 2, wheel, wheel));
            }
            case PUERTA -> {
                // Puerta con pomo
                g.draw(new Rectangle2D.Double(cx - half * 0.45, cy - half, half * 0.9, s));
                g.fill(new Ellipse2D.Double(cx + half * 0.18, cy - 3, 6, 6));
            }
            case TELEVISOR -> {
                // Pantalla con base
                g.draw(new RoundRectangle2D.Double(cx - half, cy - half * 0.7, s, half * 1.2, 10, 10));
                g.draw(new Line2D.Double(cx, cy + half * 0.5, cx, cy + half * 0.8));
                g.draw(new Line2D.Double(cx - half * 0.4, cy + half * 0.8, cx + half * 0.4, cy + half * 0.8));
            }
            case VENTANAS -> {
                // Ventana con cuatro paneles
                g.draw(new Rectangle2D.Double(cx - half, cy - half * 0.8, s, half * 1.6));
                g.draw(new Line2D.Double(cx, cy - half * 0.8, cx, cy + half * 0.8));
                g.draw(new Line2D.Double(cx - half, cy, cx + half, cy));
            }
        }
    }
}
