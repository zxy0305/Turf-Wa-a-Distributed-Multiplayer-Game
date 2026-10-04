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

    // private void startTimerTask() {
    //     stopTimerTask();
    //     timerTask = timer.scheduleAtFixedRate(() -> {
    //         synchronized (GameServerImpl.this) { timerTick(); }
    //     }, 1, 1, TimeUnit.SECONDS);
    // }

    private void stopTimerTask() {
        if (timerTask != null) { timerTask.cancel(false); timerTask = null; }
    }

    // private void timerTick() {
    //         if (phase != Phase.RUNNING) return;
    //         remainingSeconds--;
    //         if (remainingSeconds < 0) remainingSeconds = 0;
    //         broadcastTimer();
    //         if (remainingSeconds <= 0) endRound();
    //     }


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

    

    // === Helpers ===

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

    // reads whatever is in p.powerUpUses and p.bombRemaining at the moment it's called
    private int[] buildAllowances(ServerPlayer p) {
        return new int[]{
            p.powerUpUses.getOrDefault(Tool.LINE, 0),
            p.powerUpUses.getOrDefault(Tool.BLOCK, 0),
            p.powerUpUses.getOrDefault(Tool.WEDGE, 0),
            p.bombRemaining
        };
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