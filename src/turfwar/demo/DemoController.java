package turfwar.demo;

import turfwar.api.GameController;
import turfwar.api.GameView;
import turfwar.model.Config;
import turfwar.model.Geometry;
import turfwar.model.Phase;
import turfwar.model.PlayerInfo;
import turfwar.model.Square;
import turfwar.model.Tool;

import javax.swing.Timer;
import java.awt.Point;
import java.io.File;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A single-player, no-network controller so the window can be run and explored immediately.
 * It applies each click straight to the view and enforces only the use limits and the timer.
 * It is NOT a reference implementation of the rules: replace it with your own controller that
 * talks to your server.
 */
public final class DemoController implements GameController {

    private final GameView view;
    private final PlayerInfo me;
    private final Square[][] arena = new Square[Config.COLS][Config.ROWS];
    private final Map<Tool, Integer> left = new EnumMap<>(Tool.class);
    private final Timer ticker;
    private int remaining;
    private boolean paused;

    public DemoController(GameView view, String name, int roundSeconds) {
        this.view = view;
        this.me = new PlayerInfo("demo", name, 0, true, 0);   // the host always receives colour 0
        this.remaining = roundSeconds;
        for (int c = 0; c < Config.COLS; c++)
            for (int r = 0; r < Config.ROWS; r++) arena[c][r] = Square.NEUTRAL;
        ticker = new Timer(1000, e -> tick());
        view.setLocalPlayer(me);
        view.setArena(arena);
        view.setPhase(Phase.LOBBY);
        view.setRemainingSeconds(remaining);
        left.put(Tool.BOMB, Config.BOMB_USES);   // set once for the whole match, never reset by a round
        resetPowerUps();
        view.showInfo("Local demo: Game > Start Round to begin");
    }

    private void tick() {
        if (paused) return;
        remaining--;
        view.setRemainingSeconds(remaining);
        if (remaining <= 0) {
            ticker.stop();
            List<PlayerInfo> standings = Collections.singletonList(me.withSquares(count()));
            view.showRoundOver(standings, standings);
        }
    }

    private int count() {
        int n = 0;
        for (Square[] col : arena) for (Square s : col) if (s.isOwnedBy(me.id)) n++;
        return n;
    }

    /** Resets Line/Block/Wedge to a full round's worth of uses. The Bomb is untouched: it is set
     *  once for the whole match, per the rules, not reset every round. */
    private void resetPowerUps() {
        for (Tool t : Tool.values())
            if (t.isPowerUp()) left.put(t, Config.POWERUP_USES);
        view.setAllowances(left);
    }

    private void set(Point p, Square s) {
        arena[p.x][p.y] = s;
        view.setSquare(p.x, p.y, s);
    }

    private void refreshScore() {
        view.setPlayers(Collections.singletonList(me.withSquares(count())));
    }

    @Override public void paint(int col, int row) {
        Square s = arena[col][row];
        if (s.isScorched() || s.isOwnedBy(me.id)) return;
        set(new Point(col, row), Square.ownedBy(me.id));
        refreshScore();
    }

    @Override public void usePowerUp(Tool tool, int col, int row) {
        if (left.get(tool) <= 0) { view.showError("No " + tool.label + " uses left"); return; }
        for (Point p : Geometry.squares(tool, col, row))
            if (!arena[p.x][p.y].isScorched()) set(p, Square.ownedBy(me.id));
        left.put(tool, left.get(tool) - 1);
        view.setAllowances(left);
        refreshScore();
    }

    @Override public void useBomb(int col, int row) {
        if (left.get(Tool.BOMB) <= 0) { view.showError("No Bomb uses left"); return; }
        for (Point p : Geometry.squares(Tool.BOMB, col, row)) set(p, Square.SCORCHED);
        left.put(Tool.BOMB, left.get(Tool.BOMB) - 1);
        view.setAllowances(left);
        refreshScore();
    }

    @Override public void sendTaunt(int tauntIndex) { view.showTaunt(me, Config.TAUNTS[tauntIndex]); }

    @Override public void startRound() {
        for (int c = 0; c < Config.COLS; c++)
            for (int r = 0; r < Config.ROWS; r++) arena[c][r] = Square.NEUTRAL;
        view.setArena(arena);
        resetPowerUps();          // the Bomb count is deliberately left as it was: see resetPowerUps()
        refreshScore();
        remaining = Config.DEFAULT_ROUND_SECONDS;
        view.setRemainingSeconds(remaining);
        paused = false;
        view.setPhase(Phase.RUNNING);
        ticker.start();
    }

    @Override public void pauseOrResume() {
        paused = !paused;
        view.setPhase(paused ? Phase.PAUSED : Phase.RUNNING);
    }

    @Override public void kick(String playerId) { view.showError("No other players in the local demo"); }
    @Override public void respondToJoin(String requestId, boolean approve) { }
    @Override public void leave() { System.exit(0); }
    @Override public void replayLastRound() { view.showInfo("Replay is not part of the demo"); }
    @Override public void saveReplay(File file) { view.showInfo("Replay is not part of the demo"); }
    @Override public void openReplay(File file) { view.showInfo("Replay is not part of the demo"); }
}
