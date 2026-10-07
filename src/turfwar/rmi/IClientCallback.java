package turfwar.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;
// IClientCallback is remoteInterface for the client. 
// called by the server (stub on server side, impl on client side)
public interface IClientCallback extends Remote {

    // === Request ===
    // Join result — server tells client whether they can join the game
    void onJoinDecision(boolean approved, String reason,
                    String playerId, int color, boolean isHost) throws RemoteException;
    // Host only: server asks host to approve deny a joiner
    // Host is a client.
    // Specifically, it's the first client that joined, with a flag isHost = true
    void onJoinApprovalRequest(String requestId, String name) throws RemoteException;

    // === State pushes ===

    // onSnapshot is the server sending one client the entire current game state in a single call
    // The time it's called: 
    // 1. right after a join is approved, so a mid-match joiner sees the existing board, players and timer
    // 2. at the start of a round, when the arena is reset
    // 3. as a resync if a client falls out of step
    // Arena is sent as String[300] — null=neutral, "S"=scorched, playerId=owned (flat array, indexed col * 15 + row).
    void onSnapshot(String[] arena, PlayerData[] players, String phase,
                    int remaining, int[] allowances) throws RemoteException;
    // Square changes are String[] where each element is "col:row:state"
    // Square state: NEUTRAL, OWNED, SCORCHED
    void onSquaresChanged(String[] changes) throws RemoteException;
    void onPlayersChanged(PlayerData[] players) throws RemoteException;
    void onTimerUpdate(int remaining, String phase) throws RemoteException;
    void onRoundOver(PlayerData[] winners, PlayerData[] standings) throws RemoteException;

    // === Notifications ===

    // Action rejected
    void onRejected(String reason) throws RemoteException;
    // generic text notification to a client (e.g., "Player X left", "Round starting")
    void onInfo(String message) throws RemoteException;
    void onMatchClosed(String reason) throws RemoteException;
    // Allowances are sent as int[4] = {lineUses, blockUses, wedgeUses, bombUses}
    void onAllowances(int[] allowances) throws RemoteException;
    // a taunt from player fromPlayer, broadcast to everyone
    void onTaunt(PlayerData fromPlayer, String text) throws RemoteException;

    // === Heatbeat ===

    // Heartbeat, Server -> client: every so often the server calls callback.ping() on each connected client.
    void ping() throws RemoteException;
}