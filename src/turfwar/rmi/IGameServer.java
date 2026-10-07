package turfwar.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;

// IGameServer is remoteInterface for the server. 
// called by the client (stub on client side, skeleton/impl on server side)
public interface IGameServer extends Remote {
    void joinRequest(String name, int roundSeconds, IClientCallback callback)
            throws RemoteException;
    void respondToJoin(String requestId, boolean approve) throws RemoteException;

    void paint(String playerId, int col, int row) throws RemoteException;
    void usePowerUp(String playerId, String tool, int col, int row) throws RemoteException;
    void useBomb(String playerId, int col, int row) throws RemoteException;

    void startRound(String playerId) throws RemoteException;
    void leave(String playerId) throws RemoteException;

    // Client -> server: the client answers by calling server.pong(playerId), which tells the server it's still alive.
    void pong(String playerId) throws RemoteException;

    // === Advanced features ===
    // A1: taunt index(Config.TAUNTS)
    void sendTaunt(String playerId, int index) throws RemoteException;
    // A2: host only
    void pauseOrResume(String playerId) throws RemoteException;
    // A3: host only, removes another player
    void kick(String hostId, String targetId) throws RemoteException;
}