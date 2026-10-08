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
                    {
                    try {
                        // the server calls back into this client, so advertise our LAN address, not whatever the OS hostname resolves to
                        System.setProperty("java.rmi.server.hostname", findLanAddress());
                        // Connect to the remote server using its address and port
                        ClientController ctrl = new ClientController(
                            opt.serverAddress, opt.port, opt.username, 0, window);

                        // Connect the GUI window to the client-side controller
                        window.setController(ctrl);
                    } catch (Exception e) {
                        JOptionPane.showMessageDialog(null,
                            "Could not connect to " + opt.serverAddress + ":" + opt.port
                                + ":\n" + e.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                        System.exit(1); return;
                    }
                    break;
                }
                default: throw new IllegalStateException();
            }
            window.setVisible(true);
        });
    }

    private static int parsePort(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return -1; }
    }

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
