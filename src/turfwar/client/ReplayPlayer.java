package turfwar.client;

import turfwar.api.GameView;
import turfwar.model.*;

import java.util.*;

// Plays a recorded round (format: ReplayLog) on the local window at 4x speed. 
public class ReplayPlayer implements Runnable {
    private static final int SPEED = 4;
    private static final long END_HOLD_MS = 1500;

    // Represents one recorded replay event and the time
    private static final class Event {
        long t; String type; String[] args;
    }

    private final GameView view;
    private final ClientCallbackImpl cb;
    private final long duration;

    private final List<Event> events = new ArrayList<>();
    private final Map<String, PlayerInfo> players = new LinkedHashMap<>();

    private final Square[][] grid = new Square[Config.COLS][Config.ROWS];
    private volatile boolean cancelled;

    private ReplayPlayer(GameView view, ClientCallbackImpl cb, long duration) {
        this.view = view;
        this.cb = cb;
        this.duration = duration;
        for (Square[] col : grid) Arrays.fill(col, Square.NEUTRAL);
    }

    // Parses a replay; throws IllegalArgumentException if it is malformed
    public static ReplayPlayer parse(List<String> lines, GameView view, ClientCallbackImpl cb) {
        try {
            if (lines.isEmpty() || !lines.get(0).startsWith("TURFWAR-REPLAY"))
                throw new IllegalArgumentException("not a Turf War replay");
            long duration = -1;

            // Read the total duration
            for (String l : lines) if (l.startsWith("DURATION ")) 
                duration = Long.parseLong(l.substring(9).trim());
            if (duration < 0) throw new IllegalArgumentException("missing DURATION");

            ReplayPlayer rp = new ReplayPlayer(view, cb, duration);
            boolean ended = false;

            // Parse 1.players and 2.recorded events
            for (String l : lines) {
                if (l.startsWith("PLAYER ")) {
                    String[] f = l.substring(7).split("\\|");
                    rp.players.put(f[0], new PlayerInfo(f[0], f[1], Integer.parseInt(f[2]), Boolean.parseBoolean(f[3]), 0));
                } 
                else if (l.startsWith("EVENT ")) {
                    String[] f = l.substring(6).split("\\|");
                    Event e = new Event();
                    e.t = Long.parseLong(f[0]);
                    e.type = f[1];
                    e.args = Arrays.copyOfRange(f, 2, f.length);
                    rp.events.add(e);
                } else if (l.trim().equals("END")) {
                    ended = true;
                }
            }

            if (!ended) throw new IllegalArgumentException("file has no END");
            return rp;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("corrupt line (" + e.getMessage() + ")");
        }
    }

    public void cancel() { cancelled = true; }

    // Initialise the UI for replay mode
    @Override
    public void run() {
        try {
            cb.whileReplaying(() -> {
                view.setPhase(Phase.REPLAY);
                view.setArena(copyGrid());
                view.setPlayers(playerList());
                view.setRemainingSeconds((int) Math.ceil(duration / 1000.0));
                view.showInfo("Replaying at " + SPEED + "x");
            });

            long start = System.currentTimeMillis();
            int next = 0;
            int lastRem = -1;

            while (!cancelled) {
                // Convert real elapsed time into replay time using the replay speed
                long rt = (System.currentTimeMillis() - start) * SPEED;
                List<int[]> dirty = new ArrayList<>();
                boolean membership = false;

                // Apply all events where t <= rt to the board in order
                while (next < events.size() && events.get(next).t <= rt)
                    membership |= apply(events.get(next++), dirty);

                int rem = (int) Math.ceil(Math.max(0, duration - rt) / 1000.0);

                // If there are changes to the grid, player changes, or changes to the remaining seconds, 
                // refresh the screen
                if (!dirty.isEmpty() || membership || rem != lastRem) {
                    lastRem = rem;
                    final int r = rem;
                    // refresh the screen
                    cb.whileReplaying(() -> {
                        for (int[] d : dirty) view.setSquare(d[0], d[1], grid[d[0]][d[1]]);
                        view.setPlayers(playerList());
                        view.setRemainingSeconds(r);
                    });
                }
                if (rt >= duration && next >= events.size()) break;
                // Thread.sleep(40) pauses the replay thread for 40 ms between loop iterations, reducing unnecessary CPU usage
                Thread.sleep(40);
            }
            // Pause for 1.5 seconds to let the player view the final screen
            if (!cancelled) Thread.sleep(END_HOLD_MS);
        } catch (InterruptedException ignored) {
        } finally {
            cb.endReplay(!cancelled);
        }
    }

    // applies one event to the local copy; returns true if membership changed
    private boolean apply(Event e, List<int[]> dirty) {
        switch (e.type) {
            case "SQ":
                for (String ch : e.args[0].split(",")) {
                    String[] p = ch.split(":");
                    int c = Integer.parseInt(p[0]), r = Integer.parseInt(p[1]);
                    grid[c][r] = "N".equals(p[2]) ? Square.NEUTRAL
                               : "S".equals(p[2]) ? Square.SCORCHED : Square.ownedBy(p[2]);
                    //Mark the changed cells as “dirty” and refresh only those in run()
                    dirty.add(new int[]{c, r});
                }
                return false;
            // Returns true if the player list has changed
            case "JOIN":
                players.put(e.args[0], new PlayerInfo(e.args[0], e.args[1],
                        Integer.parseInt(e.args[2]), Boolean.parseBoolean(e.args[3]), 0));
                return true;
            case "LEAVE":
                players.remove(e.args[0]);
                return true;
            default:
                return false;
        }
    }

    private Square[][] copyGrid() {
        Square[][] g = new Square[Config.COLS][];
        for (int c = 0; c < Config.COLS; c++) g[c] = grid[c].clone();
        return g;
    }

    // scoreboard recomputed from the grid so counts always match the arena
    private List<PlayerInfo> playerList() {
        Map<String, Integer> counts = new HashMap<>();
        for (Square[] col : grid)
            for (Square s : col) if (s.isOwned()) counts.merge(s.ownerId, 1, Integer::sum);
        List<PlayerInfo> list = new ArrayList<>();
        for (PlayerInfo p : players.values()) list.add(p.withSquares(counts.getOrDefault(p.id, 0)));
        return list;
    }
}
