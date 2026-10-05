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
public class ClientCallbackImpl extends UnicastRemoteObject implements IClientCallback {

    private final GameView view;
    private String myId;
    private IGameServer server;

    private String username; // set in constructor
    private ClientController controller; // set after construction

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

        view.setArena(grid);
        // convert input into the format that GameView accepts
        view.setPlayers(toPlayerInfoList(players));
        view.setPhase(Phase.valueOf(phase));
        view.setRemainingSeconds(remaining);
        // convert input into the format that GameView accepts
        view.setAllowances(toToolMap(allowances));
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
            view.setSquare(c, r, sq);
        }
    }
    @Override
    public void onPlayersChanged(PlayerData[] players) throws RemoteException {
        view.setPlayers(toPlayerInfoList(players));
    }

    @Override
    public void onTimerUpdate(int remaining, String phase) throws RemoteException {
        view.setRemainingSeconds(remaining);
        if (phase != null) view.setPhase(Phase.valueOf(phase));
    }

    @Override
    public void onRoundOver(PlayerData[] winners, PlayerData[] standings)
            throws RemoteException {
        view.showRoundOver(toPlayerInfoList(winners), toPlayerInfoList(standings));
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
        view.showMatchClosed(reason);
    }

    @Override
    public void onAllowances(int[] allowances) throws RemoteException {
        view.setAllowances(toToolMap(allowances));
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
