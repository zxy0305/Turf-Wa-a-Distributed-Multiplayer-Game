package turfwar.server;

import turfwar.model.*;
import turfwar.rmi.*;

import java.awt.Point;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.*;

public class GameServerImpl extends UnicastRemoteObject implements IGameServer {
    
    private final Square[][] arena = new Square[Config.COLS][Config.ROWS];
    private final LinkedHashMap<String, ServerPlayer> players = new LinkedHashMap<>();
    private Phase phase = Phase.LOBBY;

    private int remainingSeconds;
    private int roundLengthSeconds = Config.DEFAULT_ROUND_SECONDS;

    private int scorchCount = 0;
    private int nextPlayerId = 0;
    private int nextRequestId = 0;
    // creates the waiting list for join requests
    private final Map<String, PendingJoin> pendingJoins = new HashMap<>();

    // inner class for pending joins
    static class PendingJoin {
        final IClientCallback callback;
        final String name;
        PendingJoin(IClientCallback cb, String n) { callback = cb; name = n; }
    }

    // creates a scheduler that can run some code later or repeatedly at scheduled times
    // this scheduler has one worker thread (to avoid blocking server's main networking code)
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
    // stores the reference to the scheduled task that I created
    private ScheduledFuture<?> timerTask;


    // Initialize
    public GameServerImpl() throws RemoteException {
        super(0); // export on any available port
        for (int c = 0; c < Config.COLS; c++)
            for (int r = 0; r < Config.ROWS; r++)
                arena[c][r] = Square.NEUTRAL;
        remainingSeconds = roundLengthSeconds;

        // heartbeat checker every 2 seconds
        timer.scheduleAtFixedRate(this::checkHeartbeats, 2, 2, TimeUnit.SECONDS);
    }


    // === Join Workflow ===
    
    // joinRequest is synchronized
    // Validation Check and if the player is host, auto approved; otherwise, ask host to approve
    @Override
    public synchronized void joinRequest(String name, int roundSeconds,
                                          IClientCallback callback) throws RemoteException {
        // validate name
        if (!Config.validName(name)) {
            callback.onJoinDecision(false, "Invalid username", null, -1, false);
            return;
        }
        // check full
        if (players.size() >= Config.COLOURS.length) {
            callback.onJoinDecision(false, "Match is full", null, -1, false);
            return;
        }
        // check name duplicate
        for (ServerPlayer p : players.values()) {
            if (p.name.equalsIgnoreCase(name)) {
                callback.onJoinDecision(false,
                    "Username '" + name + "' is existed", null, -1, false);
                return;
            }
        }
        // check pending name duplicate
        for (PendingJoin pj : pendingJoins.values()) {
            if (pj.name.equalsIgnoreCase(name)) {
                callback.onJoinDecision(false,
                    "Username '" + name + "' is already pending", null, -1, false);
                return;
            }
        }

        // first player is host, auto approve
        if (players.isEmpty()) {
            if (roundSeconds >= Config.MIN_ROUND_SECONDS
                    && roundSeconds <= Config.MAX_ROUND_SECONDS) {
                roundLengthSeconds = roundSeconds;
                remainingSeconds = roundSeconds;
            }
            admitPlayer(name, true, callback);
        } else {
            
            String reqId = String.valueOf(nextRequestId++);
            pendingJoins.put(reqId, new PendingJoin(callback, name));
            ServerPlayer host = findHost();
            // ask host for approval
            if (host != null) {
                safeCallback(() -> host.callback.onJoinApprovalRequest(reqId, name));
            }
            safeCallback(() -> callback.onInfo("Waiting for host approval..."));
        }
    }


    // respondToJoin is called by the host to answer a pending join request
    // 1. take the joiner out of the waiting list
    // 2. if the host approved: re-check that the match still has room and the name is still free
    // 3. if the host denied: tell the joiner they were rejected
    // the joiner always gets an onJoinDecision with the final result
    @Override
    public synchronized void respondToJoin(String requestId, boolean approve)
            throws RemoteException {
        PendingJoin pj = pendingJoins.remove(requestId);
        if (pj == null) return;

        if (approve) {
            // re-check conditions
            if (players.size() >= Config.COLOURS.length) {
                safeCallback(() -> pj.callback.onJoinDecision(false, "Match is full",
                    null, -1, false));
                return;
            }
            for (ServerPlayer p : players.values()) {
                if (p.name.equalsIgnoreCase(pj.name)) {
                    safeCallback(() -> pj.callback.onJoinDecision(false, "Username existed",
                        null, -1, false));
                    return;
                }
            }
            // approved 
            admitPlayer(pj.name, false, pj.callback);
        } else {
            // host denied the request
            safeCallback(() -> pj.callback.onJoinDecision(false,
                "Host denied your request", null, -1, false));
        }
    }

    private void admitPlayer(String name, boolean isHost, IClientCallback callback) {
        // assign id, color
        String id = String.valueOf(nextPlayerId++);
        int color = nextFreeColor();
        ServerPlayer player = new ServerPlayer(id, name, color, isHost, callback);
        players.put(id, player);

        safeCallback(() -> callback.onJoinDecision(true, null, id, color, isHost));
        // send snapshot to the player who is just got approved to join the game
        sendSnapshot(player);
        // boradcast to all players about the new player
        broadcastPlayers();
        broadcastInfo(name + " joined");
    }

    // returns the lowest colour index not used by any current player
    private int nextFreeColor() {
        boolean[] used = new boolean[Config.COLOURS.length];
        // mark every color that is taken
        for (ServerPlayer p : players.values()) used[p.colorIndex] = true;
        
        for (int i = 0; i < used.length; i++) 
            if (!used[i]) return i;
        return 0;
    }

    // === Arena Actions ===

    // Validation check about the phase, whether the square has been taken, scorched or out of arena
    // change the state of arena square
    // broadcast change and playerData
    @Override
    public synchronized void paint(String playerId, int col, int row) throws RemoteException {
        ServerPlayer player = players.get(playerId);
        if (player == null) return;
        if (phase != Phase.RUNNING) {
            safeCallback(() -> player.callback.onRejected("Round is not running"));
            return;
        }
        if (!Geometry.inArena(col, row)) return;
        Square sq = arena[col][row];
        if (sq.isScorched() || sq.isOwnedBy(playerId)) return; // not an error

        arena[col][row] = Square.ownedBy(playerId);
        broadcastSquareChanges(new String[]{col + ":" + row + ":" + playerId});
        // why broadcast players:
        // painting a square changes the scores, and scores live in PlayerData.squares, not in the arena message
        broadcastPlayers();
    }

    // usePowerUp: a player uses a Line, Block or Wedge power-up anchored at (col, row)
    // 1. Validation check, include player, phase, correct tool 
    // 2. update number of use
    // 3. claim every square in the tool's shape
    // 4. broadcast
    @Override
    public synchronized void usePowerUp(String playerId, String toolName, int col, int row)
            throws RemoteException {
        ServerPlayer player = players.get(playerId);
        if (player == null) return;
        if (phase != Phase.RUNNING) {
            safeCallback(() -> player.callback.onRejected("Round is not running"));
            return;
        }
        Tool tool;
        try { tool = Tool.valueOf(toolName); } 
        catch (Exception e) { return; }

        if (!tool.isPowerUp()) return;

        // check if any chance of usage of this tool is left
        int uses = player.powerUpUses.getOrDefault(tool, 0);
        if (uses <= 0) {
            safeCallback(() -> player.callback.onRejected("No " + tool.label + " uses left"));
            return;
        }
        // or deduct one chance
        player.powerUpUses.put(tool, uses - 1);

        // collect only the squares that actually change, encoded as "col:row:playerIDd"
        List<String> changes = new ArrayList<>();
        for (Point p : Geometry.squares(tool, col, row)) {
            Square sq = arena[p.x][p.y];
            if (!sq.isScorched() && !sq.isOwnedBy(playerId)) {
                arena[p.x][p.y] = Square.ownedBy(playerId);
                changes.add(p.x + ":" + p.y + ":" + playerId);
            }
        }
        if (!changes.isEmpty()) {
            broadcastSquareChanges(changes.toArray(new String[0]));
            broadcastPlayers();
        }
        // send this player remaining uses
        sendAllowances(player);
    }

    // a player drops their Bomb on the 3x3 area centred on (col, row)
    // 1. validate: player exists, round is running, player still has a bomb
    // 2. count how many squares would be scorched, and reject if that would beyond the arena's scorch capability
    // 3. deduct the bomb and scorch every square in the area
    // 4. broadcast
    @Override
    public synchronized void useBomb(String playerId, int col, int row) throws RemoteException {
        ServerPlayer player = players.get(playerId);
        if (player == null) return;
        if (phase != Phase.RUNNING) {
            safeCallback(() -> player.callback.onRejected("Round is not running"));
            return;
        }
        if (player.bombRemaining <= 0) {
            safeCallback(() -> player.callback.onRejected("No Bomb left"));
            return;
        }

        // count how many squares would be scorched
        List<Point> pts = Geometry.squares(Tool.BOMB, col, row);
        int newScorched = 0;
        for (Point p : pts) if (!arena[p.x][p.y].isScorched()) newScorched++;
        // reject if that would beyond the arena's scorch capability
        if (scorchCount + newScorched > Config.scorchCap()) {
            safeCallback(() -> player.callback.onRejected("Arena too damaged so bomb is rejected"));
            return;
        }

        // deduct the bomb
        player.bombRemaining--;
        // scorch every square in the area
        List<String> changes = new ArrayList<>();
        for (Point p : pts) {
            if (!arena[p.x][p.y].isScorched()) {
                arena[p.x][p.y] = Square.SCORCHED;

                scorchCount++;

                changes.add(p.x + ":" + p.y + ":S");
            }
        }

        if (!changes.isEmpty()) {
            broadcastSquareChanges(changes.toArray(new String[0]));
            broadcastPlayers();
        }
        sendAllowances(player);
    }


    // === Match Control ===

    // 1. validate: caller is the host, enough players, no round already in progress
    // 2. reset the round state: clear the arena to neutral, reset the scorch count, refill power-ups
    // 3. switch to RUNNING, reset the clock, send everyone a full snapshot, start the 1 second timer
    @Override
    public synchronized void startRound(String playerId) throws RemoteException {
        // validate: caller is the host
        ServerPlayer sender = players.get(playerId);
        if (sender == null || !sender.isHost) {
            if (sender != null)
                safeCallback(() -> sender.callback.onRejected("Only the host can start"));
            return;
        }

        if (players.size() < Config.MIN_PLAYERS) {
            safeCallback(() -> sender.callback.onRejected(
                "Need at least " + Config.MIN_PLAYERS + " players"));
            return;
        }
        // no round already in progress
        if (phase == Phase.RUNNING || phase == Phase.PAUSED) {
            safeCallback(() -> sender.callback.onRejected("Round already in progress"));
            return;
        }

        // reset arena
        for (int c = 0; c < Config.COLS; c++)
            for (int r = 0; r < Config.ROWS; r++)
                arena[c][r] = Square.NEUTRAL;
        scorchCount = 0;
        // reset powerups
        for (ServerPlayer p : players.values()) p.resetPowerUps();

        phase = Phase.RUNNING;
        remainingSeconds = roundLengthSeconds;
        broadcastSnapshotToAll();
        // start the 1 second timer
        startTimerTask();
    }

    @Override
    public synchronized void leave(String playerId) throws RemoteException {
        // when removePlayer() is called inside leave(), it is also sychronized protected as leave() is holding the lock
        removePlayer(playerId);
    }

    // Updates the player's last response time when a pong is received
    @Override
    public synchronized void pong(String playerId) throws RemoteException {
        ServerPlayer p = players.get(playerId);
        if (p != null) p.lastPongTime = System.currentTimeMillis();
    }


    // === HeartBeats ===

    // for the server to check whether each connected client is still alive
    private void checkHeartbeats() {
        synchronized (this) {
            long now = System.currentTimeMillis();
            List<String> dead = new ArrayList<>();
            for (ServerPlayer p : players.values()) {
                // If the player has not responded to a ping for more than 5 seconds, consider the player disconnected
                if (now - p.lastPongTime > 5000) {
                    dead.add(p.id);
                } else {
                    // Player is still alive, so send another ping
                    safeCallback(() -> p.callback.ping());
                }
            }
            for (String id : dead) removePlayer(id);
        }
    }

    // === Player remover ===

    // When a player leaves or disconnects, remove that player from the server and reset the tiles they occupied to “neutral”; 
    // if the player who leaves is the host, simply end the entire match
    private void removePlayer(String playerId) {
    
        ServerPlayer player = players.remove(playerId);
        if (player == null) return;

        // neutralize squares
        List<String> changes = new ArrayList<>();
        for (int c = 0; c < Config.COLS; c++)
            for (int r = 0; r < Config.ROWS; r++)
                if (arena[c][r].isOwnedBy(playerId)) {
                    arena[c][r] = Square.NEUTRAL;
                    changes.add(c + ":" + r + ":N");
                }

        if (player.isHost) {
            stopTimerTask();
            for (ServerPlayer p : players.values())
                safeCallback(() -> p.callback.onMatchClosed("Host left the match"));
            players.clear();
            pendingJoins.clear();
            return;
        }

        if (!changes.isEmpty())
            broadcastSquareChanges(changes.toArray(new String[0]));

        broadcastPlayers();
        
        broadcastInfo(player.name + " left");
    }



    // === Timer ===

    private void startTimerTask() {
        // stop any existing timer before starting a new one
        stopTimerTask();
        // run timerTick() every second to update the server's game clock
        timerTask = timer.scheduleAtFixedRate(() -> {
            // synchronize access to the game server state 
            // reason to synchronize:
            // ensure that only one thread can modify or access the shared game state at a time,
            // preventing race conditions and keeping the server state consistent
            synchronized (GameServerImpl.this) { timerTick(); }
        }, 1, 1, TimeUnit.SECONDS);
    }

    private void stopTimerTask() {
        if (timerTask != null) { timerTask.cancel(false); timerTask = null; }
    }

    // timerTick runs on timer thread
    private void timerTick() {
            if (phase != Phase.RUNNING) return;
            remainingSeconds--;
            if (remainingSeconds < 0) remainingSeconds = 0;
            // send the updated remaining time to all clients
            broadcastTimer();
            // End the round when the timer reaches zero
            if (remainingSeconds <= 0) endRound();
        }

    // Ends the round and broadcasts the final results to all players.
    private void endRound() {
        stopTimerTask();
        // change the phase state to OVER
        phase = Phase.OVER;
        PlayerData[] standings = buildPlayerList();

        // Sort players by the number of squares they own, highest first
        Arrays.sort(standings, (a, b) -> b.squares - a.squares);

        int max = standings.length > 0 ? standings[0].squares : 0;
        List<PlayerData> winners = new ArrayList<>();
        // Find all players with the highest score
        for (PlayerData pd : standings) 
            if (pd.squares == max) winners.add(pd);
        PlayerData[] winnersArr = winners.toArray(new PlayerData[0]);
        // Notify every player the round is over and send them the winners and final standings
        for (ServerPlayer p : players.values())
            safeCallback(() -> p.callback.onRoundOver(winnersArr, standings));
    }


    // === Broadcast ===

    private void broadcastSquareChanges(String[] changes) {
        for (ServerPlayer p : players.values())
            safeCallback(() -> p.callback.onSquaresChanged(changes));
    }

    private void broadcastPlayers() {
        PlayerData[] list = buildPlayerList();
        // broadcast all players info (list of PlayerData) to each player
        for (ServerPlayer p : players.values())
            safeCallback(() -> p.callback.onPlayersChanged(list));
    }

    private void broadcastInfo(String message) {
        for (ServerPlayer p : players.values())
            safeCallback(() -> p.callback.onInfo(message));
    }

    private void broadcastTimer() {
        String ph = phase.name();
        int rem = remainingSeconds;
        for (ServerPlayer p : players.values())
            safeCallback(() -> p.callback.onTimerUpdate(rem, ph));
    }

    // broadcastSnapshotToAll() sends the whole game state to every connected player, 
    // while sendSnapshot(target) sends the whole game state only to one specific player,
    // typically a newly joined player
    private void broadcastSnapshotToAll() {
        String[] enc = encodeArena();
        PlayerData[] pl = buildPlayerList();
        String ph = phase.name();
        int rem = remainingSeconds;

        for (ServerPlayer p : players.values()) {
            // also send allowance
            int[] allow = buildAllowances(p);
            safeCallback(() -> p.callback.onSnapshot(enc, pl, ph, rem, allow));
        }
    }


    // sends the whole game state to one player (used after joining so they start with the correct board)
    private void sendSnapshot(ServerPlayer target) {
        // send Arena, playerlist, phase, remainingseconds and allowance
        // all squares as a flat String[], indexed col * ROWS + row
        String[] enc = encodeArena();                  
        PlayerData[] pl = buildPlayerList();         
        // phase as a String so it can be sent over RMI  
        String ph = phase.name();                      
        int rem = remainingSeconds;
        // this player's own remaining power-up and bomb uses
        int[] allow = buildAllowances(target);         
        // safeCallback catches RemoteException if the client is unreachable
        safeCallback(() -> target.callback.onSnapshot(enc, pl, ph, rem, allow));
    }

    private void sendAllowances(ServerPlayer player) {
        int[] allow = buildAllowances(player);
        safeCallback(() -> player.callback.onAllowances(allow));
    }

    // === Data Built Helpers ===

    private String[] encodeArena() {
        String[] enc = new String[Config.COLS * Config.ROWS];
        for (int c = 0; c < Config.COLS; c++)
            for (int r = 0; r < Config.ROWS; r++) {
                Square sq = arena[c][r];
                if (sq.isScorched()) enc[c * Config.ROWS + r] = "S";
                else if (sq.isOwned()) enc[c * Config.ROWS + r] = sq.ownerId;
                // null for neutral
            }
        return enc;
    }

    // Build Player DTO
    private PlayerData[] buildPlayerList() {
        PlayerData[] list = new PlayerData[players.size()];
        int i = 0;

        for (ServerPlayer p : players.values())
            list[i++] = new PlayerData(p.id, p.name, p.colorIndex,
                                       p.isHost, countSquares(p.id));
        return list;
    }

    // reads whatever is in p.powerUpUses and p.bombRemaining at the moment it's called
    // why int[]:
    // int[] is the simplest and most compact thing to send over RMI
    private int[] buildAllowances(ServerPlayer p) {
        return new int[]{
            p.powerUpUses.getOrDefault(Tool.LINE, 0),
            p.powerUpUses.getOrDefault(Tool.BLOCK, 0),
            p.powerUpUses.getOrDefault(Tool.WEDGE, 0),
            p.bombRemaining
        };
    }



    // === Other Helpers (Utility) ===

    // Count the number of squares belonged to specific playerId
    private int countSquares(String playerId) {
        int n = 0;

        for (int c = 0; c < Config.COLS; c++)
            for (int r = 0; r < Config.ROWS; r++)
                if (arena[c][r].isOwnedBy(playerId)) n++;
        return n;
    }

    private ServerPlayer findHost() {
        for (ServerPlayer p : players.values()) if (p.isHost) return p;
        return null;
    }
    // Wraps remote callback so a dead client doesn't crash the server
    // If the server tries to call a client, but that client has disconnected, 
    // the RemoteException is caught so the server doesn't crash.
    private void safeCallback(RunnableWithException r) {
        try { r.run(); }
        catch (RemoteException e) { }
    }

    @FunctionalInterface
    interface RunnableWithException {
        void run() throws RemoteException;
    }
}