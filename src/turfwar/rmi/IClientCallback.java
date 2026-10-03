package turfwar.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;
// IClientCallback is remoteInterface for the client. 
// called by the server (stub on server side, impl on client side)
public interface IClientCallback extends Remote {
    // Join result, goes to the joining player
    void onJoinDecision(boolean approved, String reason,
                        String playerId, int colour, boolean isHost) throws RemoteException;

    // State pushes
    // onSnapshot is the server sending one client the entire current game state in a single call
    // The time it's called: 
    // 1. right after a join is approved, so a mid-match joiner sees the existing board, players and timer
    // 2. at the start of a round, when the arena is reset
    // 3. as a resync if a client falls out of step
    void onSnapshot(String[] arena, PlayerData[] players, String phase,
                    int remaining, int[] allowances) throws RemoteException;
    void onSquaresChanged(String[] changes) throws RemoteException;
    void onPlayersChanged(PlayerData[] players) throws RemoteException;
    void onTimerUpdate(int remaining, String phase) throws RemoteException;
    void onRoundOver(PlayerData[] winners, PlayerData[] standings) throws RemoteException;

    // Notifications
    void onRejected(String reason) throws RemoteException;
    void onJoinApprovalRequest(String requestId, String name) throws RemoteException;
    // generic text notification to a client (e.g., "Player X left", "Round starting")
    void onInfo(String message) throws RemoteException;
    void onMatchClosed(String reason) throws RemoteException;
    // tells the client how many pixels of each color they're allowed to paint
    void onAllowances(int[] allowances) throws RemoteException;

    // Heartbeat, Server -> client: every so often the server calls callback.ping() on each connected client.
    void ping() throws RemoteException;
}