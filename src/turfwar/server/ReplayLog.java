package turfwar.server;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Server-side recorder for one round. All methods are called when holding the GameServerImpl lock,
 * so events are stored in exactly the order the server processed them.
 * 
 * It only records the players at the start of the game and every subsequent change to the board (with a timestamp)
 *
 * File / wire format (UTF-8 text, one record per line):
 *   TURFWAR-REPLAY v1
 *   DURATION <ms>                                   round length on the round's own clock
 *   PLAYER <id>|<name>|<colorIndex>|<host>         players at round start (arena starts all neutral)
 *   EVENT <ms>|SQ|<c>:<r>:<state>,<c>:<r>:<state>   accepted paint/power-up/bomb/leave changes; state = playerId | S | N
 *   EVENT <ms>|JOIN|<id>|<name>|<colorIndex>|<host>
 *   EVENT <ms>|LEAVE|<id>
 *   END
 * <ms> is the offset on the round clock (paused time is excluded).
 */
class ReplayLog {
    private final List<String> initialPlayers = new ArrayList<>();
    private final List<String> events = new ArrayList<>();
    private boolean recording;
    private long lastEventMs;
    private String[] finished;

    // beginning of the game
    void begin(Collection<ServerPlayer> players) {
        initialPlayers.clear();
        events.clear();
        lastEventMs = 0;
        recording = true;
        // storing info PLAYER for each player
        for (ServerPlayer p : players) initialPlayers.add("PLAYER " + playerFields(p));
    }

    // Tools/Allowance used or painted, record changed grids here
    void squares(long t, String[] changes) {
        if (!recording || changes.length == 0) return;
        add(t, "SQ|" + String.join(",", changes));
    }

    // record joiner
    void join(long t, ServerPlayer p) {
        if (!recording) return;
        add(t, "JOIN|" + playerFields(p));
    }

    // record player who leaves
    void leave(long t, String playerId) {
        if (!recording) return;
        add(t, "LEAVE|" + playerId);
    }

    // Stop recording, and concatenate the final lines of text to form “finished”
    // finish() is to add the head and tail of the file, and the whole body of the file
    void finish(long durationMs) {
        if (!recording) return;
        //Stop recording
        recording = false;

        List<String> out = new ArrayList<>();

        // add head, duration and initail players
        out.add("TURFWAR-REPLAY v1");
        // the lasteventMs usually happens before the round actually ends, that's why we use MAX
        out.add("DURATION " + Math.max(durationMs, lastEventMs));
        out.addAll(initialPlayers);

        // add body: events
        out.addAll(events);

        // add tail
        out.add("END");
        finished = out.toArray(new String[0]);
    }

    String[] last() { return finished; }

    // add() is espaecially used for recording events
    private void add(long t, String body) {
        lastEventMs = Math.max(lastEventMs, t);
        events.add("EVENT " + t + "|" + body);
    }

    private static String playerFields(ServerPlayer p) {
        return p.id + "|" + p.name + "|" + p.colorIndex + "|" + p.isHost;
    }
}
