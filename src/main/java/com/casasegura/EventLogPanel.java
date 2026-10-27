package com.casasegura;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Terminal de eventos: consola con colores diferenciados por tipo de evento,
 * marca de tiempo y desplazamiento automático. Es segura para llamar desde cualquier hilo.
 */
public class EventLogPanel extends JPanel {

    private static final int MAX_LINES = 1500;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final Color BACKGROUND = new Color(0x0A0D14);
    private static final Color TIMESTAMP_COLOR = new Color(0x4F5B6B);

    private final JTextPane textPane = new JTextPane();
    private final StyledDocument document = textPane.getStyledDocument();

    public EventLogPanel() {
        super(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));

        JLabel title = new JLabel(">_ TERMINAL DE EVENTOS");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 12f));
        title.setForeground(new Color(0x00E5FF));

        JButton clearButton = new JButton("Limpiar");
        clearButton.setFocusable(false);
        clearButton.addActionListener(e -> textPane.setText(""));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(0, 2, 6, 0));
        header.add(title, BorderLayout.WEST);
        header.add(clearButton, BorderLayout.EAST);

        textPane.setEditable(false);
        textPane.setFocusable(false);
        textPane.setBackground(BACKGROUND);
        textPane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        textPane.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        JScrollPane scroll = new JScrollPane(textPane);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(0x1F2A3A)));

        add(header, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
    }

    /** Agrega una línea con el color de su tipo. */
    public void append(String message, LogType type) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> append(message, type));
            return;
        }
        try {
            document.insertString(document.getLength(), LocalTime.now().format(TIME_FORMAT) + "  ",
                    style(TIMESTAMP_COLOR, false));
            document.insertString(document.getLength(), String.format("%-6s", type.getTag()) + " ",
                    style(type.getColor(), true));
            document.insertString(document.getLength(), message + "\n", style(type.getColor(), false));
            trimOldLines();
        } catch (BadLocationException ignored) {
            // No puede ocurrir: siempre insertamos al final del documento.
        }
        textPane.setCaretPosition(document.getLength()); // Auto-scroll
    }

    /** Mantiene acotado el tamaño de la consola para no degradar el rendimiento. */
    private void trimOldLines() throws BadLocationException {
        Element root = document.getDefaultRootElement();
        int excess = root.getElementCount() - MAX_LINES;
        if (excess > 0) {
            int end = root.getElement(excess - 1).getEndOffset();
            document.remove(0, end);
        }
    }

    private static SimpleAttributeSet style(Color color, boolean bold) {
        SimpleAttributeSet attrs = new SimpleAttributeSet();
        StyleConstants.setForeground(attrs, color);
        StyleConstants.setBold(attrs, bold);
        return attrs;
    }
}
