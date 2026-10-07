package turfwar.client;

import turfwar.api.GameController;
import turfwar.api.GameView;
import turfwar.model.PlayerInfo;
import turfwar.model.Tool;
import turfwar.rmi.IGameServer;

import java.io.File;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

// implements GameController, wraps RMI calls in background threads
// each client application has its own GameController instance
public class ClientController implements GameController {
    private final GameView view;
    private final IGameServer server;
    private final ClientCallbackImpl callback;
    private String myId;
    private boolean isHost;

    public ClientController(String host, int port, String username,
                            int roundSeconds, GameView view) throws Exception {
        this.view = view;

        // look up server in RMI registry
        Registry registry = LocateRegistry.getRegistry(host, port);
        this.server = (IGameServer) registry.lookup("TurfWarServer");

        // create callback (exported as remote object)
        this.callback = new ClientCallbackImpl(view);
        callback.setServer(server);
        callback.setUsername(username);
        callback.setController(this);

        // send join request (on background thread to not block EDT(event dispatch thread))
        new Thread(() -> {
            try {
                server.joinRequest(username, roundSeconds, callback);
            } catch (RemoteException e) {
                view.showMatchClosed("Could not connect: " + e.getMessage());
            }
        }).start();
    }

    // called by the callback after join is approved
    public void setMyId(String id) { this.myId = id; }
    public void setHost(boolean h) { this.isHost = h; }
    
    // every GameController method: start an operation in a separate thread and don't wait for its result
    // async() is a convenience helper designed so we don't have to write new Thread() every time 
    // when making a potentially blocking RMI/network call
    private void async(Runnable r) {
        new Thread(() -> {
            try { r.run(); }
            catch (Exception e) {
                view.showMatchClosed("Lost connection to server");
            }
        }).start();
    }

    @Override
    public void paint(int col, int row) {
        async(() -> { try { server.paint(myId, col, row); } catch (RemoteException e) { throw new RuntimeException(e); } });
    }

    @Override
    public void usePowerUp(Tool tool, int col, int row) {
        async(() -> { try { server.usePowerUp(myId, tool.name(), col, row); } catch (RemoteException e) { throw new RuntimeException(e); } });
    }

    @Override
    public void useBomb(int col, int row) {
        async(() -> { try { server.useBomb(myId, col, row); } catch (RemoteException e) { throw new RuntimeException(e); } });
    }

    @Override
    public void startRound() {
        async(() -> { try { server.startRound(myId); } catch (RemoteException e) { throw new RuntimeException(e); } });
    }

    @Override
    public void respondToJoin(String requestId, boolean approve) {
        async(() -> { try { server.respondToJoin(requestId, approve); } catch (RemoteException e) { throw new RuntimeException(e); } });
    }

    // Problem: System.exit(0) runs right after the leave thread starts, 
    // so the JVM may exit before server.leave(myId) is sent.
    // The server would then only notice the player is gone when its next callback to that client fails

    // @Override
    // public void leave() {
    //     async(() -> { try { server.leave(myId); } catch (RemoteException ignored) {} });
    //     System.exit(0);
    // }

    @Override
    public void leave() {
        Thread t = new Thread(() -> {
            try { server.leave(myId); } catch (RemoteException ignored) {}
        });
        t.start();
        try {
            // give the leave call a moment to reach the server before exiting
            // and 1000 timeout avoid t blocking forever if RMI call hangs
            t.join(1000);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        System.exit(0);
    }

    // TODO
    @Override
    public void sendTaunt(int tauntIndex) {
        // stub for now — advanced feature
    }

    @Override
    public void pauseOrResume() {
        // stub — advanced feature
    }

    @Override
    public void kick(String playerId) {
        // stub — advanced feature
    }
    @Override public void replayLastRound() { view.showInfo("Not implemented"); }
    @Override public void saveReplay(File file) { view.showInfo("Not implemented"); }
    @Override public void openReplay(File file) { view.showInfo("Not implemented"); }
}
