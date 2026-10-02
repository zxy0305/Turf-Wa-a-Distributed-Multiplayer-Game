package turfwar.ui;

import turfwar.model.PlayerInfo;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

/** The live scoreboard: colour, name, squares owned, sorted by squares. The host can select a row to kick. */
public final class ScoreboardPanel extends JPanel {

    private final List<PlayerInfo> players = new ArrayList<>();
    private final Model model = new Model();
    private final JTable table = new JTable(model);
    private String localId;

    public ScoreboardPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Scoreboard"));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(24);
        table.setFillsViewportHeight(true);
        table.getColumnModel().getColumn(0).setMaxWidth(36);
        table.getColumnModel().getColumn(0).setCellRenderer(new Swatch());
        table.getColumnModel().getColumn(2).setMaxWidth(70);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(260, 220));
        add(scroll, BorderLayout.CENTER);
    }

    public void setLocalPlayerId(String id) { this.localId = id; model.fireTableDataChanged(); }

    public void setPlayers(List<PlayerInfo> list) {
        String selected = selectedPlayerId();
        players.clear();
        players.addAll(list);
        players.sort((a, b) -> Integer.compare(b.squares, a.squares));
        model.fireTableDataChanged();
        if (selected != null)
            for (int i = 0; i < players.size(); i++)
                if (players.get(i).id.equals(selected)) table.setRowSelectionInterval(i, i);
    }

    /** Id of the selected player, or null. */
    public String selectedPlayerId() {
        int row = table.getSelectedRow();
        return row < 0 || row >= players.size() ? null : players.get(row).id;
    }

    private final class Model extends AbstractTableModel {
        private final String[] cols = {"", "Player", "Squares"};
        @Override public int getRowCount() { return players.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int c) { return cols[c]; }
        @Override public Object getValueAt(int r, int c) {
            PlayerInfo p = players.get(r);
            switch (c) {
                case 0: return p.colour();
                case 1: return p.name + (p.host ? " (host)" : "") + (p.id.equals(localId) ? " (you)" : "");
                default: return p.squares;
            }
        }
    }

    private static final class Swatch extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
            JLabel l = (JLabel) super.getTableCellRendererComponent(t, "", sel, foc, r, c);
            l.setOpaque(true);
            l.setBackground(v instanceof Color ? (Color) v : Color.GRAY);
            return l;
        }
    }
}
