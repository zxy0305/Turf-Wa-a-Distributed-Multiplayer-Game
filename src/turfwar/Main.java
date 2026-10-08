package turfwar;

import turfwar.client.ClientController;
import turfwar.demo.DemoController;
import turfwar.server.GameServerImpl;
import turfwar.ui.MainWindow;
import turfwar.ui.StartDialog;

import java.net.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Enumeration;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 * Entry point of the client jar. Usage: java -jar TurfWarClient.jar [serverAddress] [serverPort] [username]
 * Command-line values only pre-fill the start dialog.
 */
public final class Main {
    public static void main(String[] args) {
        final String address = args.length > 0 ? args[0] : null;
        final int port = args.length > 1 ? parsePort(args[1]) : -1;
        final String name = args.length > 2 ? args[2] : null;

        SwingUtilities.invokeLater(() -> {
            StartDialog.Options opt = new StartDialog(address, port, name).showAndGet();
            if (opt == null) System.exit(0);

            MainWindow window = new MainWindow();

            // TODO (your work): create your own GameController here. It should connect to
                    // (or start) your server using opt.serverAddress, opt.port, opt.username
                    // and, for the host, opt.roundSeconds, then drive the window
                    // through the GameView interface. Until then the program explains and exits.
            switch (opt.mode) {
                // Run the local demo without using the distributed server and networking
                case LOCAL_DEMO:
                    window.setController(new DemoController(window, opt.username, opt.roundSeconds));
                    break;

                // Start an RMI server and connect the host as a normal client
                case HOST:
                    {
                    try {
                        System.setProperty("java.rmi.server.hostname", findLanAddress());
                        GameServerImpl server = new GameServerImpl();
                        Registry registry = LocateRegistry.createRegistry(opt.port);
                        registry.rebind("TurfWarServer", server);
                    } catch (Exception e) {
                        JOptionPane.showMessageDialog(null,
                            "Could not start server on port " + opt.port + ":\n" + e.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                        System.exit(1); return;
                    }
                    try {
                        // The host also acts as a normal client.
                        // "localhost" means the host client connects to the server running on the same machine
                        // (host starts the server inside the host's own client program, that's why "localhost" is set here)
                        // for the host, opt.roundSeconds is needed
                        ClientController ctrl = new ClientController(
                            "localhost", opt.port, opt.username, opt.roundSeconds, window);

                        // Connect the GUI window to the client-side controller
                        window.setController(ctrl);
                    } catch (Exception e) {
                        JOptionPane.showMessageDialog(null,
                            "Could not connect:\n" + e.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                        System.exit(1); return;
                    }
                    break;
                }
                // Connect to an existing server as a non-host player
                case JOIN:
                    joinFlow(window, opt.serverAddress, opt.port, opt.username, false);
                    break;
                default: throw new IllegalStateException();
            }
            window.setVisible(true);
        });
    }

    // Connects as a non-host player. If the server rejects the username, the start dialog is shown again
    // (instead of exiting) so the user can try another name
    private static void joinFlow(MainWindow window, String address, int port, String name, boolean retrying) {
        try {
            // the server calls back into this client
            System.setProperty("java.rmi.server.hostname", findLanAddress());
            // retryJoin() if the join is rejected
            ClientController ctrl = new ClientController(address, port, name, 0, window,
                msg -> SwingUtilities.invokeLater(() -> retryJoin(window, address, port, name, msg)));
            window.setController(ctrl);

        } catch (Exception e) {
            String msg = "Could not connect to " + address + ":" + port + ":\n" + e.getMessage();
            if (retrying) { retryJoin(window, address, port, name, msg); return; }
            JOptionPane.showMessageDialog(null, msg, "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    private static void retryJoin(MainWindow window, String address, int port, String name, String message) {
        JOptionPane.showMessageDialog(window, message, "Cannot join", JOptionPane.WARNING_MESSAGE);
        String addr = address, user = name;
        int prt = port;
        while (true) {
            StartDialog.Options opt = new StartDialog(addr, prt, user).showAndGet();
            if (opt == null) System.exit(0);
            if (opt.mode == StartDialog.Mode.JOIN) {
                joinFlow(window, opt.serverAddress, opt.port, opt.username, true);
                return;
            }
            JOptionPane.showMessageDialog(window, "Choose \"Join a match\" to try again.",
                "Cannot join", JOptionPane.INFORMATION_MESSAGE);
            addr = opt.serverAddress; prt = opt.port; user = opt.username;
        }
    }

    private static int parsePort(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return -1; }
    }

    // Automatically determine the current computer's IPv4 address on the local area network (LAN);
    // if it cannot be found, return 127.0.0.1
    private static String findLanAddress() {
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface iface = ifaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                Enumeration<InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress())
                        return addr.getHostAddress();
                }
            }
        } catch (SocketException ignored) {}
        return "127.0.0.1";
    }
}
