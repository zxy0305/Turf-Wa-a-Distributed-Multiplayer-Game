package turfwar.api;

import turfwar.model.Phase;
import turfwar.model.PlayerInfo;
import turfwar.model.Square;
import turfwar.model.Tool;

import java.util.List;
import java.util.Map;

/**
 * What your code tells the user interface. The provided MainWindow.java implements this.
 *
 * Every method may be called from any thread; the implementation moves the work to the Swing
 * event thread. Calls are applied in the order they are made.
 */
public interface GameView {

    /** Identify the local player. Must be called once before the window is useful. */
    void setLocalPlayer(PlayerInfo me);

    /** Replace the scoreboard with these players (with current square counts). */
    void setPlayers(List<PlayerInfo> players);

    /** Update one square. */
    void setSquare(int col, int row, Square square);

    /** Replace the whole arena, indexed [col][row]. Used after joining and at round start. */
    void setArena(Square[][] squares);

    /** Change the phase shown in the header; also enables or disables arena input. */
    void setPhase(Phase phase);

    /** Seconds left in the round as reported by the server. */
    void setRemainingSeconds(int seconds);

    /** Remaining uses of LINE, BLOCK and WEDGE this round, and of BOMB for the whole match, for the local player. */
    void setAllowances(Map<Tool, Integer> remaining);

    /** Show a taunt in the taunt feed of every client. */
    void showTaunt(PlayerInfo from, String text);

    /** A short status message in the status bar (for example "Alice joined"). */
    void showInfo(String message);

    /** A rejected action or other error, shown in the status bar in red (no modal dialog). */
    void showError(String message);

    /** The round is over: show winners and final standings; phase becomes OVER. */
    void showRoundOver(List<PlayerInfo> winners, List<PlayerInfo> standings);

    /**
     * Host only: someone asks to join. The UI shows a non-blocking dialog with Approve and Deny and
     * answers through {@link GameController#respondToJoin(String, boolean)}. The server assigns the colour.
     */
    void showJoinRequest(String requestId, String name);

    /**
     * The match ended for this client (host left, kicked, denied, or connection lost).
     * The UI shows the reason and, when the user dismisses it, closes the window.
     */
    void showMatchClosed(String reason);
}
