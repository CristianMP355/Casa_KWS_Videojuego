package com.casasegura;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;

/**
 * Tabla "HISTORIAL DE TURNOS" de la Consola de Telemetría: fecha, puntaje final,
 * nivel alcanzado y modo de juego de cada turno guardado en el JSON.
 */
public class HistoryPanel extends JPanel implements GameEngine.Listener {

    private final GameEngine engine;
    private final JLabel summary = new JLabel();
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"Fecha", "Puntaje Final", "Nivel Alcanzado", "Modo de Juego"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    public HistoryPanel(GameEngine engine) {
        super(new BorderLayout(0, 8));
        this.engine = engine;
        setBorder(BorderFactory.createEmptyBorder(10, 10, 6, 10));

        JLabel title = new JLabel("HISTORIAL DE TURNOS");
        title.setForeground(new Color(0x00E5FF));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
        summary.setForeground(new Color(0x7F8FA6));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(title, BorderLayout.WEST);
        header.add(summary, BorderLayout.EAST);

        JTable table = new JTable(model);
        table.setFocusable(false);
        table.setRowSelectionAllowed(false);
        table.setRowHeight(24);
        table.getTableHeader().setReorderingAllowed(false);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(0x24324A)));

        add(header, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);

        engine.addListener(this);
        refresh();
    }

    @Override
    public void onGameOver(int finalScore, boolean newRecord) {
        refresh();
    }

    private void refresh() {
        model.setRowCount(0);
        HighScoreRepository.Record record = engine.getHighScoreRecord();
        for (HighScoreRepository.GameEntry entry : record.getHistory()) {
            model.addRow(new Object[]{entry.getDate(), entry.getScore(), "Nivel " + entry.getLevel(),
                    entry.getDifficulty()});
        }
        summary.setText("High Score: " + record.getHighScore() + " pts"
                + (record.getAchievedAt().isEmpty() ? "" : " (" + record.getAchievedAt() + ")")
                + "  ·  Turnos registrados: " + record.getHistory().size());
    }
}
