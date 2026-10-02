package turfwar.ui;

import turfwar.model.Config;
import turfwar.model.Tool;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import java.awt.Component;
import java.awt.Dimension;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Tool selection: Paint, the power-ups, and the Bomb. Shows the remaining uses and greys out a
 * tool with none left. Keys 1 to 5 select tools (bound in MainWindow).
 */
public final class ToolbarPanel extends JPanel {

    private final EnumMap<Tool, JToggleButton> buttons = new EnumMap<>(Tool.class);
    private final EnumMap<Tool, Integer> remaining = new EnumMap<>(Tool.class);
    private Consumer<Tool> onSelect = t -> {};

    public ToolbarPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createTitledBorder("Tools"));
        ButtonGroup group = new ButtonGroup();
        for (Tool t : Tool.values()) {
            JToggleButton b = new JToggleButton();
            b.setAlignmentX(Component.LEFT_ALIGNMENT);
            b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
            b.addActionListener(e -> { if (b.isSelected()) onSelect.accept(t); });
            group.add(b);
            buttons.put(t, b);
            add(b);
        }
        buttons.get(Tool.PAINT).setSelected(true);
        for (Tool t : Tool.values()) {
            if (t.isPowerUp()) remaining.put(t, Config.POWERUP_USES);
            else if (t == Tool.BOMB) remaining.put(t, Config.BOMB_USES);
        }
        refreshLabels();
    }

    public void onToolSelected(Consumer<Tool> listener) { this.onSelect = listener; }

    public Tool getSelectedTool() {
        for (Map.Entry<Tool, JToggleButton> e : buttons.entrySet()) if (e.getValue().isSelected()) return e.getKey();
        return Tool.PAINT;
    }

    /** Selects a tool as if the user clicked it (used for key bindings and the automatic return to Paint). */
    public void selectTool(Tool t) {
        JToggleButton b = buttons.get(t);
        if (!b.isEnabled()) return;
        b.setSelected(true);
        onSelect.accept(t);
    }

    /**
     * Remaining uses for the local player: power-ups for this round, and the Bomb for the whole
     * match. Tools with 0 left are disabled; Paint is always enabled.
     */
    public void setAllowances(Map<Tool, Integer> left) {
        for (Map.Entry<Tool, Integer> e : left.entrySet()) remaining.put(e.getKey(), e.getValue());
        refreshLabels();
        if (!buttons.get(getSelectedTool()).isEnabled()) selectTool(Tool.PAINT);
    }

    private void refreshLabels() {
        for (Tool t : Tool.values()) {
            JToggleButton b = buttons.get(t);
            if (t == Tool.PAINT) {
                b.setText("Paint (" + t.key + ")");
            } else {
                int n = remaining.getOrDefault(t, 0);
                b.setText(t.label + " (" + t.key + ")  " + n + " left");
                b.setEnabled(n > 0);
            }
        }
    }
}
