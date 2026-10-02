package turfwar.api;

import turfwar.model.Tool;

import java.io.File;

/**
 * What the user interface asks your code to do. YOU IMPLEMENT THIS INTERFACE.
 *
 * Every method is called on the Swing event thread in response to user input. Return quickly:
 * send the request to your server (or apply it to your shared state) and return. Do not block
 * waiting for a reply. When the outcome is known, update the UI through GameView.java.
 *
 * The UI never assumes an action succeeded. It only shows what your code passes to GameView.
 */
public interface GameController {

    /** The local player clicked or dragged over a square with the Paint tool. */
    void paint(int col, int row);

    /** The local player used a power-up (LINE, BLOCK or WEDGE) anchored at the square. */
    void usePowerUp(Tool tool, int col, int row);

    /** The local player dropped the Bomb (a fixed 3 x 3 area) centred on the square. */
    void useBomb(int col, int row);

    /** The local player pressed a taunt button or Ctrl+1 to Ctrl+8. Index into Config.TAUNTS. */
    void sendTaunt(int tauntIndex);

    /** Host only: Game > Start Round. */
    void startRound();

    /** Host only: Game > Pause / Resume (the UI shows one item that toggles). */
    void pauseOrResume();

    /** Host only: the host selected a player on the scoreboard and chose Game > Kick. */
    void kick(String playerId);

    /** Host only: the host answered a join request shown by {@link GameView#showJoinRequest}. */
    void respondToJoin(String requestId, boolean approve);

    /** Game > Leave, or the window was closed. Disconnect cleanly and exit. */
    void leave();

    /** Host only: Replay > Replay Last Round. Play back the last finished round on this client. */
    void replayLastRound();

    /** Host only: Replay > Save Replay. Write the last finished round's log to the file. */
    void saveReplay(File file);

    /** Host only: Replay > Open Replay. Load a saved log from the file and play it back. */
    void openReplay(File file);
}
