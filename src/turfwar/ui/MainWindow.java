package turfwar.ui;

import turfwar.api.GameController;
import turfwar.api.GameView;
import turfwar.model.Config;
import turfwar.model.Phase;
import turfwar.model.PlayerInfo;
import turfwar.model.Square;
import turfwar.model.Tool;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The one game window every submission uses. Layout: header (phase, timer, you), arena in the
 * centre, scoreboard / tools / taunts on the right, status bar at the bottom. Implements
 * GameView.java; forwards user input to the GameController.java you provide.
 */
public final class MainWindow extends JFrame implements GameView {

    private final ArenaPanel arena = new ArenaPanel();
    private final ScoreboardPanel scoreboard = new ScoreboardPanel();
    private final ToolbarPanel toolbar = new ToolbarPanel();
    private final TauntPanel taunts = new TauntPanel();
    private final JLabel phaseLabel = new JLabel("LOBBY");
    private final JLabel timerLabel = new JLabel("--:--");
    private final JLabel meLabel = new JLabel(" ");
    private final JLabel status = new JLabel(" ");
    private final JMenuItem startItem = new JMenuItem("Start Round");
    private final JMenuItem pauseItem = new JMenuItem("Pause");
    private final JMenuItem kickItem = new JMenuItem("Kick Selected Player");
    private final JMenuItem replayItem = new JMenuItem("Replay Last Round");
    private final JMenuItem saveItem = new JMenuItem("Save Replay...");
    private final JMenuItem openItem = new JMenuItem("Open Replay...");

    private GameController controller;
    private PlayerInfo me;
    private Phase phase = Phase.LOBBY;

    public MainWindow() {
        super("Turf War");
        buildMenu();
        buildLayout();
        bindKeys();
        wireInput();
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { if (controller != null) controller.leave(); else dispose(); }
        });
        pack();
        setMinimumSize(new Dimension(900, 620));
        setLocationRelativeTo(null);
        applyPhase();
    }

    /** Must be called before the window is shown. */
    public void setController(GameController controller) { this.controller = controller; }

    // ------------------------------------------------------------------ construction

    private void buildMenu() {
        JMenuBar bar = new JMenuBar();
        JMenu game = new JMenu("Game");
        startItem.addActionListener(e -> controller.startRound());
        pauseItem.addActionListener(e -> controller.pauseOrResume());
        kickItem.addActionListener(e -> {
            String id = scoreboard.selectedPlayerId();
            if (id == null) showError("Select a player on the scoreboard first");
            else if (me != null && id.equals(me.id)) showError("You cannot kick yourself");
            else controller.kick(id);
        });
        JMenuItem leave = new JMenuItem("Leave");
        leave.addActionListener(e -> controller.leave());
        game.add(startItem); game.add(pauseItem); game.add(kickItem); game.addSeparator(); game.add(leave);

        JMenu replay = new JMenu("Replay");
        replayItem.addActionListener(e -> controller.replayLastRound());
        saveItem.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setSelectedFile(new File("turfwar-replay.txt"));
            if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) controller.saveReplay(fc.getSelectedFile());
        });
        openItem.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) controller.openReplay(fc.getSelectedFile());
        });
        replay.add(replayItem); replay.add(saveItem); replay.add(openItem);

        JMenu help = new JMenu("Help");
        JMenuItem rules = new JMenuItem("Rules");
        rules.addActionListener(e -> JOptionPane.showMessageDialog(this, RULES, "Turf War rules", JOptionPane.INFORMATION_MESSAGE));
        help.add(rules);

        bar.add(game); bar.add(replay); bar.add(help);
        setJMenuBar(bar);
    }

    private void buildLayout() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        phaseLabel.setFont(phaseLabel.getFont().deriveFont(Font.BOLD, 16f));
        timerLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 26));
        header.add(phaseLabel, BorderLayout.WEST);
        header.add(timerLabel, BorderLayout.CENTER);
        timerLabel.setHorizontalAlignment(JLabel.CENTER);
        header.add(meLabel, BorderLayout.EAST);

        JPanel side = new JPanel();
        side.setLayout(new javax.swing.BoxLayout(side, javax.swing.BoxLayout.Y_AXIS));
        side.add(scoreboard);
        side.add(toolbar);
        side.add(taunts);
        side.setPreferredSize(new Dimension(290, 600));

        status.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));

        JPanel centre = new JPanel(new BorderLayout());
        centre.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 6));
        centre.add(arena, BorderLayout.CENTER);

        setLayout(new BorderLayout());
        add(header, BorderLayout.NORTH);
        add(centre, BorderLayout.CENTER);
        add(side, BorderLayout.EAST);
        add(status, BorderLayout.SOUTH);
    }

    private void bindKeys() {
        JComponent root = getRootPane();
        for (Tool t : Tool.values()) {
            String name = "tool-" + t.name();
            root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(t.key), name);
            root.getActionMap().put(name, new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) { toolbar.selectTool(t); }
            });
        }
        for (int i = 0; i < Config.TAUNTS.length; i++) {
            final int idx = i;
            String name = "taunt-" + i;
            root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_1 + i, InputEvent.CTRL_DOWN_MASK), name);
            root.getActionMap().put(name, new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) { taunts.fire(idx); }
            });
        }
    }

    private void wireInput() {
        toolbar.onToolSelected(arena::setTool);
        taunts.onTaunt(i -> controller.sendTaunt(i));
        arena.setInput(new ArenaPanel.Input() {
            @Override public void paint(int col, int row) { controller.paint(col, row); }
            @Override public void powerUp(Tool tool, int col, int row) {
                controller.usePowerUp(tool, col, row);
                toolbar.selectTool(Tool.PAINT);          // a power-up is used once, then back to Paint
            }
            @Override public void bomb(int col, int row) {
                controller.useBomb(col, row);
                toolbar.selectTool(Tool.PAINT);
            }
        });
    }

    private void applyPhase() {
        phaseLabel.setText(phase.name());
        boolean running = phase == Phase.RUNNING;
        arena.setInputEnabled(running);
        taunts.setSendingEnabled(running || phase == Phase.PAUSED);
        boolean host = me != null && me.host;
        startItem.setEnabled(host && (phase == Phase.LOBBY || phase == Phase.OVER));
        pauseItem.setEnabled(host && (phase == Phase.RUNNING || phase == Phase.PAUSED));
        pauseItem.setText(phase == Phase.PAUSED ? "Resume" : "Pause");
        kickItem.setEnabled(host && phase != Phase.REPLAY);
        replayItem.setEnabled(host && phase == Phase.OVER);
        saveItem.setEnabled(host && phase == Phase.OVER);
        openItem.setEnabled(host && (phase == Phase.LOBBY || phase == Phase.OVER));
        if (phase == Phase.PAUSED) showInfo("Paused by the host");
        if (phase == Phase.REPLAY) showInfo("Replaying");
    }

    private static void onEdt(Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) r.run(); else SwingUtilities.invokeLater(r);
    }

    // ------------------------------------------------------------------ GameView

    @Override public void setLocalPlayer(PlayerInfo me) {
        onEdt(() -> {
            this.me = me;
            meLabel.setText("You: " + me.name + " (" + me.colourName() + ")" + (me.host ? "  HOST" : ""));
            meLabel.setForeground(me.colour().darker());
            scoreboard.setLocalPlayerId(me.id);
            setTitle("Turf War: " + me.name);
            applyPhase();
        });
    }

    @Override public void setPlayers(List<PlayerInfo> players) {
        onEdt(() -> {
            scoreboard.setPlayers(players);
            Map<String, Color> colours = new HashMap<>();
            for (PlayerInfo p : players) colours.put(p.id, p.colour());
            arena.setPlayerColours(colours);
        });
    }

    @Override public void setSquare(int col, int row, Square square) { onEdt(() -> arena.setSquare(col, row, square)); }

    @Override public void setArena(Square[][] squares) { onEdt(() -> arena.setArena(squares)); }

    @Override public void setPhase(Phase phase) { onEdt(() -> { this.phase = phase; applyPhase(); }); }

    @Override public void setRemainingSeconds(int seconds) {
        onEdt(() -> {
            int s = Math.max(0, seconds);
            timerLabel.setText(String.format("%02d:%02d", s / 60, s % 60));
            timerLabel.setForeground(s <= 10 && phase == Phase.RUNNING ? Color.RED : Color.BLACK);
        });
    }

    @Override public void setAllowances(Map<Tool, Integer> remaining) { onEdt(() -> toolbar.setAllowances(remaining)); }

    @Override public void showTaunt(PlayerInfo from, String text) { onEdt(() -> taunts.showTaunt(from, text)); }

    @Override public void showInfo(String message) {
        onEdt(() -> { status.setForeground(Color.DARK_GRAY); status.setText(message); });
    }

    @Override public void showError(String message) {
        onEdt(() -> { status.setForeground(new Color(0xB00020)); status.setText(message); });
    }

    @Override public void showRoundOver(List<PlayerInfo> winners, List<PlayerInfo> standings) {
        onEdt(() -> {
            this.phase = Phase.OVER;
            applyPhase();
            scoreboard.setPlayers(standings);
            StringBuilder sb = new StringBuilder("<html>");
            if (winners.size() == 1) sb.append("<b>").append(winners.get(0).name).append(" wins!</b>");
            else {
                sb.append("<b>Draw between ");
                for (int i = 0; i < winners.size(); i++) sb.append(i > 0 ? ", " : "").append(winners.get(i).name);
                sb.append("</b>");
            }
            sb.append("<br><br>Final standings:<br>");
            for (PlayerInfo p : standings) sb.append(p.name).append(": ").append(p.squares).append(" squares<br>");
            sb.append("</html>");
            showInfo("Round over");
            JOptionPane.showMessageDialog(this, sb.toString(), "Round over", JOptionPane.INFORMATION_MESSAGE);
        });
    }

    @Override public void showJoinRequest(String requestId, String name) {
        onEdt(() -> new JoinRequestDialog(this, name, ok -> controller.respondToJoin(requestId, ok)).setVisible(true));
    }

    @Override public void showMatchClosed(String reason) {
        onEdt(() -> {
            arena.setInputEnabled(false);
            JOptionPane.showMessageDialog(this, reason, "Match closed", JOptionPane.WARNING_MESSAGE);
            dispose();
            System.exit(0);
        });
    }

    private static final String RULES =
        "<html><body style='width:420px'>"
        + "<b>Goal:</b> own the most squares when the timer reaches zero.<br><br>"
        + "<b>Paint (1):</b> click or drag to take squares. Your own squares and scorched squares are unaffected.<br>"
        + "<b>Line (2):</b> the square and the 4 to its right. <b>Block (3):</b> 3x3 around the square.<br>"
        + "<b>Wedge (4):</b> a 6-square triangle down-right.<br>"
        + "Power-ups take squares from anyone. Each can be used " + Config.POWERUP_USES + " times per round.<br>"
        + "<b>Bomb (5):</b> scorches a fixed 3x3 area centred on the clicked square. "
        + "Scorched squares can never be taken again. "
        + Config.BOMB_USES + " per player for the whole match, and at most " + Config.SCORCH_CAP_PERCENT + "% of the arena may be scorched.<br>"
        + "<b>Taunts:</b> Ctrl+1 to Ctrl+8, one every " + (Config.TAUNT_MIN_INTERVAL_MS / 1000) + " seconds.<br>"
        + "<b>Host:</b> starts, pauses and resumes the round, approves joiners, can kick players, and controls replays.<br>"
        + "<b>Colours</b> are assigned by the server when you join."
        + "</body></html>";
}
