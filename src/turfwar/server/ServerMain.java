package turfwar.server;

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
        System.out.println("Turf War server skeleton. Nothing is implemented yet. Port " + port + ".");
    }
}
