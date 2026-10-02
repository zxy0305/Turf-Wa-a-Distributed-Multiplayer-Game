package turfwar.ui;

import turfwar.model.Config;
import turfwar.model.PlayerInfo;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.function.IntConsumer;

/** Eight fixed taunt buttons (Ctrl+1 to Ctrl+8) and the feed of taunts received from all players. */
public final class TauntPanel extends JPanel {

    private static final int MAX_LINES = 60;
    private final JTextArea feed = new JTextArea();
    private final JButton[] buttons = new JButton[Config.TAUNTS.length];
    private IntConsumer onTaunt = i -> {};

    public TauntPanel() {
        setLayout(new BorderLayout(4, 4));
        setBorder(BorderFactory.createTitledBorder("Taunts"));
        JPanel grid = new JPanel(new GridLayout(4, 2, 4, 4));
        for (int i = 0; i < Config.TAUNTS.length; i++) {
            final int idx = i;
            JButton b = new JButton(Config.TAUNTS[i]);
            b.setToolTipText("Ctrl+" + (i + 1));
            b.setFocusable(false);
            b.addActionListener(e -> onTaunt.accept(idx));
            buttons[i] = b;
            grid.add(b);
        }
        add(grid, BorderLayout.NORTH);
        feed.setEditable(false);
        feed.setLineWrap(true);
        feed.setWrapStyleWord(true);
        JScrollPane scroll = new JScrollPane(feed);
        scroll.setPreferredSize(new Dimension(260, 140));
        add(scroll, BorderLayout.CENTER);
    }

    public void onTaunt(IntConsumer listener) { this.onTaunt = listener; }

    public void setSendingEnabled(boolean enabled) { for (JButton b : buttons) b.setEnabled(enabled); }

    /** Called by the window when the local player presses Ctrl+n. */
    public void fire(int index) { if (index >= 0 && index < buttons.length && buttons[index].isEnabled()) onTaunt.accept(index); }

    public void showTaunt(PlayerInfo from, String text) {
        feed.append(from.name + ": " + text + "\n");
        int lines = feed.getLineCount();
        if (lines > MAX_LINES) {
            try { feed.replaceRange("", 0, feed.getLineStartOffset(lines - MAX_LINES)); } catch (Exception ignored) {}
        }
        feed.setCaretPosition(feed.getDocument().getLength());
    }
}
