package turfwar.client;

import turfwar.api.GameView;
import turfwar.model.*;
import turfwar.rmi.IClientCallback;
import turfwar.rmi.IGameServer;
import turfwar.rmi.PlayerData;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
// receives server pushes, calls GameView
// one ClientCallbackImpl object per client program, each client program is a player
// Each player's callback is their own private "mailbox" that the server delivers to

// During replay, prevent real-time server updates from overwriting the playback screen, 
// while ensuring that the system returns to the latest state once playback ends.
public class ClientCallbackImpl extends UnicastRemoteObject implements IClientCallback {

    private final GameView view;
    private String myId;
    private IGameServer server;

    private String username; // set in constructor
    private ClientController controller; // set after construction

    // Mirror of the live server state. It is always updated, but only pushed to the window
    // while no replay is on screen, so live updates never overwrite a replay.
    private final Object stateLock = new Object();
    private Square[][] liveGrid = neutralGrid();
    private Phase livePhase = Phase.LOBBY;
    private int liveRemaining;
    private List<PlayerInfo> livePlayers = new ArrayList<>();
    private Map<Tool, Integer> liveAllow = new EnumMap<>(Tool.class);
    private ReplayPlayer replay; // non-null while a replay is playing

    public ClientCallbackImpl(GameView view) throws RemoteException {
        super(0);
        this.view = view;
    }
    
    public void setMyId(String id) { this.myId = id; }
    public void setServer(IGameServer server) { this.server = server; }
    public void setUsername(String u) { this.username = u; }
    public void setController(ClientController c) { this.controller = c; }

    // handles the server's decision about whether this client is allowed to join the game
    // onJoinDecision needs to call view.setLocalPlayer() 
    // and feed the playerId back to ClientController
    @Override
    public void onJoinDecision(boolean approved, String reason,
                            String playerId, int color, boolean isHost)
            throws RemoteException {
        if (approved) {
            this.myId = playerId;

            // Pass the server-assigned ID to the controller
            controller.setMyId(playerId);
            controller.setHost(isHost);

            // Update the UI with this client's player information
            view.setLocalPlayer(new PlayerInfo(playerId, username, color, isHost, 0));
        } else {
            view.showMatchClosed("Join denied: " + reason);
        }
    }

    // the server sends this client the entire current game state
    @Override
    public void onSnapshot(String[] arena, PlayerData[] players, String phase,
                           int remaining, int[] allowances) throws RemoteException {
        // decode arena
        Square[][] grid = new Square[Config.COLS][Config.ROWS];
        for (int c = 0; c < Config.COLS; c++)
            for (int r = 0; r < Config.ROWS; r++) {
                String s = arena[c * Config.ROWS + r];
                if (s == null) grid[c][r] = Square.NEUTRAL;
                else if ("S".equals(s)) grid[c][r] = Square.SCORCHED;
                else grid[c][r] = Square.ownedBy(s);
            }

        synchronized (stateLock) {
            // store changed data into live mirror
            liveGrid = grid;
            livePlayers = toPlayerInfoList(players);
            livePhase = Phase.valueOf(phase);
            liveRemaining = remaining;
            liveAllow = toToolMap(allowances);
            if (replay == null) pushLive();
        }
    }

    // the server sends only the squares that just changed, not the whole board
    @Override
    public void onSquaresChanged(String[] changes) throws RemoteException {
        for (String change : changes) {
            String[] parts = change.split(":");
            int c = Integer.parseInt(parts[0]);
            int r = Integer.parseInt(parts[1]);
            String state = parts[2];

            Square sq;
            if ("N".equals(state)) sq = Square.NEUTRAL;
            else if ("S".equals(state)) sq = Square.SCORCHED;
            else sq = Square.ownedBy(state);
            synchronized (stateLock) {
                liveGrid[c][r] = sq;
                if (replay == null) view.setSquare(c, r, sq);
            }
        }
    }
    @Override
    public void onPlayersChanged(PlayerData[] players) throws RemoteException {
        synchronized (stateLock) {
            livePlayers = toPlayerInfoList(players);
            if (replay == null) view.setPlayers(livePlayers);
        }
    }

    @Override
    public void onTimerUpdate(int remaining, String phase) throws RemoteException {
        synchronized (stateLock) {
            liveRemaining = remaining;
            if (phase != null) livePhase = Phase.valueOf(phase);
            if (replay == null) {
                view.setRemainingSeconds(remaining);
                if (phase != null) view.setPhase(livePhase);
            }
        }
    }

    @Override
    public void onRoundOver(PlayerData[] winners, PlayerData[] standings)
            throws RemoteException {
        synchronized (stateLock) {
            livePhase = Phase.OVER;
            if (replay == null) view.showRoundOver(toPlayerInfoList(winners), toPlayerInfoList(standings));
        }
    }

    @Override
    public void onRejected(String reason) throws RemoteException {
        view.showError(reason);
    }

    // HOST ONLY!!
    // The server asks this client (host) to approve or deny a new player
    @Override
    public void onJoinApprovalRequest(String requestId, String name) throws RemoteException {
        view.showJoinRequest(requestId, name);
    }

    @Override
    public void onInfo(String message) throws RemoteException {
        view.showInfo(message);
    }

    @Override
    public void onMatchClosed(String reason) throws RemoteException {
        synchronized (stateLock) {
            if (replay != null) replay.cancel();
        }
        view.showMatchClosed(reason);
    }

    @Override
    public void onAllowances(int[] allowances) throws RemoteException {
        synchronized (stateLock) {
            liveAllow = toToolMap(allowances);
            if (replay == null) view.setAllowances(liveAllow);
        }
    }

    // === Replay support (A4) ===

    public Phase livePhase() { synchronized (stateLock) { return livePhase; } }
    public boolean isReplaying() { synchronized (stateLock) { return replay != null; } }
    // Starting now, real-time push notifications will no longer be displayed on the screen
    // as replay starts 
    public void beginReplay(ReplayPlayer r) { synchronized (stateLock) { replay = r; } }


    // runs r only while a replay is still on screen
    void whileReplaying(Runnable r) {
        synchronized (stateLock) { if (replay != null) r.run(); }
    }

    // replay finished: return to the current server state
    void endReplay(boolean restoreLive) {
        synchronized (stateLock) {
            replay = null;
            // If replay completes normally, call pushLive() to resume
            if (restoreLive) pushLive();
        }
    }

    
    private void pushLive() {
        view.setArena(liveGrid);
        view.setPlayers(livePlayers);
        view.setPhase(livePhase);
        view.setRemainingSeconds(liveRemaining);
        if (!liveAllow.isEmpty()) view.setAllowances(liveAllow);
    }

    private static Square[][] neutralGrid() {
        Square[][] g = new Square[Config.COLS][Config.ROWS];
        for (Square[] col : g) Arrays.fill(col, Square.NEUTRAL);
        return g;
    }

    // show a taunt with the sender's name and color
    @Override
    public void onTaunt(PlayerData from, String text) throws RemoteException {
        view.showTaunt(new PlayerInfo(from.id, from.name, from.colorIndex, from.host, from.squares), text);
    }

    // client-side response to the server's heartbeat ping()
    @Override
    public void ping() throws RemoteException {
        // respond with pong on a background thread (don't block server's call)
        // reason to create a new thread: ping() returns quickly, 
        // while the background thread handles the network call server.pong(myId) asynchronously
        if (server != null && myId != null) {
            new Thread(() -> {
                try { server.pong(myId); } 
                catch (RemoteException ignored) {}
            }).start();
        }
    }


    // === Helpers ===

    // converts the PlayerData DTOs received into the PlayerInfo objects the window uses
    private List<PlayerInfo> toPlayerInfoList(PlayerData[] pds) {
        List<PlayerInfo> list = new ArrayList<>();
        
        for (PlayerData pd : pds)
            list.add(new PlayerInfo(pd.id, pd.name, pd.colorIndex, pd.host, pd.squares));
        return list;
    }

    // converts the allowances int[] from the server into the Map
    // the order must match the server's buildAllowances: {LINE, BLOCK, WEDGE, BOMB}
    private Map<Tool, Integer> toToolMap(int[] a) {
        Map<Tool, Integer> map = new EnumMap<>(Tool.class);

        map.put(Tool.LINE, a[0]);
        map.put(Tool.BLOCK, a[1]);
        map.put(Tool.WEDGE, a[2]);
        map.put(Tool.BOMB, a[3]);

        return map;
    }

}
