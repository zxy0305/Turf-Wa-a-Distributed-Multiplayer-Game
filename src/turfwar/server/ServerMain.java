package turfwar.server;

import java.net.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Enumeration;

/**
 * Entry point of the server jar. This is a skeleton. Replace the body of main with your server.
 *
 * Usage: java -jar TurfWarServer.jar <port>
 *
 * Your server holds the state of the match, decides the order in which player actions take effect,
 * keeps the round clock, and sends every change to every client.
 */
public final class ServerMain {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar TurfWarServer.jar <port>");
            System.exit(1);
        }
        int port;
        try {
            port = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.err.println("The port must be a number from 1 to 65535.");
            System.exit(1);
            return;
        }
        if (port < 1 || port > 65535) {
            System.err.println("The port must be a number from 1 to 65535.");
            System.exit(1);
        }
        // TODO (your work): start your server on this port.
        try {
            System.setProperty("java.rmi.server.hostname", findLanAddress());
            // create the remote server object;
            GameServerImpl server = new GameServerImpl();

            // start an RMI registry inside this JVM on the given port,
            // so that clients can look up to find the server
            Registry registry = LocateRegistry.createRegistry(port);

            // register the server under a name; clients call registry.lookup("TurfWarServer")
            // rebind (not bind): creates registry if it doesn't exist, or replaces the existing binding
            registry.rebind("TurfWarServer", server);
            System.out.println("Turf War server running on port " + port);

            // Don't let the main server thread finish; 
            // keep the server process alive so clients can continue connecting.
            Thread.currentThread().join();
        } catch (Exception e) {
            System.err.println("Server failed: " + e.getMessage());
            System.exit(1);
        }

        // System.out.println("Turf War server skeleton. Nothing is implemented yet. Port " + port + ".");
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
