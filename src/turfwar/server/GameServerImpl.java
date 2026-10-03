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
        for (ServerPlayer p : players.values())
            safeCallback(() -> p.callback.onPlayersChanged(list));
    }

    private void broadcastInfo(String message) {
        for (ServerPlayer p : players.values())
            safeCallback(() -> p.callback.onInfo(message));
    }


    // === Helpers ===

    private PlayerData[] buildPlayerList() {
        PlayerData[] list = new PlayerData[players.size()];
        int i = 0;

        for (ServerPlayer p : players.values())
            list[i++] = new PlayerData(p.id, p.name, p.colorIndex,
                                       p.isHost, countSquares(p.id));
        return list;
    }


    private int countSquares(String playerId) {
        int n = 0;

        for (int c = 0; c < Config.COLS; c++)
            for (int r = 0; r < Config.ROWS; r++)
                if (arena[c][r].isOwnedBy(playerId)) n++;
        return n;
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