package turfwar.client;

import turfwar.api.GameController;
import turfwar.api.GameView;
import turfwar.model.Phase;
import turfwar.model.PlayerInfo;
import turfwar.model.Tool;
import turfwar.rmi.IGameServer;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.function.Consumer;
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
    // called when the server rejects the username, so the user can try another one
    private final Consumer<String> onNameRejected;

    public ClientController(String host, int port, String username,
                            int roundSeconds, GameView view) throws Exception {
        this(host, port, username, roundSeconds, view, null);
    }

    public ClientController(String host, int port, String username,
                            int roundSeconds, GameView view,
                            Consumer<String> onNameRejected) throws Exception {
        this.view = view;
        this.onNameRejected = onNameRejected;

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

    // called by the callback when the server rejects the username
    void nameRejected(String message) {
        dispose();
        if (onNameRejected != null) onNameRejected.accept(message);
        else view.showMatchClosed("Join denied: " + message);
    }

    // stops receiving callbacks
    private void dispose() {
        try { UnicastRemoteObject.unexportObject(callback, true); } 
        catch (Exception ignored) {}
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
        if (callback.isReplaying()) { view.showError("Wait for the replay to finish"); return; }
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

    @Override
    public void sendTaunt(int tauntIndex) {
        async(() -> { try { server.sendTaunt(myId, tauntIndex); } catch (RemoteException e) { throw new RuntimeException(e); } });
    }

    @Override
    public void pauseOrResume() {
        async(() -> { try { server.pauseOrResume(myId); } catch (RemoteException e) { throw new RuntimeException(e); } });
    }

    @Override
    public void kick(String playerId) {
        async(() -> { try { server.kick(myId, playerId); } catch (RemoteException e) { throw new RuntimeException(e); } });
    }

    // === A4: Replay (host only). The log lives on the server; replay is local to the host's window ===

    @Override
    public void replayLastRound() {
        if (!canUseReplay()) return;
        async(() -> {
            String[] log = fetchReplay();
            if (log != null) play(Arrays.asList(log));
        });
    }

    @Override
    public void saveReplay(File file) {
        if (!isHost) { view.showError("Only the host can use replays"); return; }
        async(() -> {
            String[] log = fetchReplay();
            if (log == null) return;
            try {
                // Save the replay log as a UTF-8 text file
                Files.write(file.toPath(), Arrays.asList(log), StandardCharsets.UTF_8);
                view.showInfo("Replay saved to " + file.getName());
            } catch (IOException e) {
                view.showError("Could not save replay: " + e.getMessage());
            }
        });
    }

    @Override
    public void openReplay(File file) {
        if (!canUseReplay()) return;
        async(() -> {
            try {
                play(Files.readAllLines(file.toPath(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                view.showError("Could not open replay: " + e.getMessage());
            }
        });
    }

    private boolean canUseReplay() {
        // Only the host can use replay
        if (!isHost) { view.showError("Only the host can use replays"); return false; }
        // Prevent starting another replay while one is already playing
        if (callback.isReplaying()) { view.showError("A replay is already playing"); return false; }
        // Replays are only allowed in LOBBY or OVER phase
        Phase ph = callback.livePhase();
        if (ph != Phase.LOBBY && ph != Phase.OVER) {
            view.showError("Replays are only available in the lobby or after a round");
            return false;
        }
        return true;
    }

    private String[] fetchReplay() {
        try {
            // Request the replay log from the server using RMI
            String[] log = server.getReplay(myId);
            if (log == null) view.showError("No finished round to replay yet");
            return log;
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    private void play(List<String> lines) {
        try {
            ReplayPlayer rp = ReplayPlayer.parse(lines, view, callback);
            // Tell the client callback that a replay has started
            callback.beginReplay(rp);

            // ReplayPlayer implements Runnable, so run it on a separate thread.
            // This keeps replay independent from the Swing event thread
            new Thread(rp, "replay").start();
        } catch (IllegalArgumentException e) {
            view.showError("Invalid replay file: " + e.getMessage());
        }
    }
}
