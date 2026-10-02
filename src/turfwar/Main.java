package turfwar;

import turfwar.demo.DemoController;
import turfwar.ui.MainWindow;
import turfwar.ui.StartDialog;

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

            switch (opt.mode) {
                case LOCAL_DEMO:
                    window.setController(new DemoController(window, opt.username, opt.roundSeconds));
                    break;
                case HOST:
                case JOIN:
                    // TODO (your work): create your own GameController here. It should connect to
                    // (or start) your server using opt.serverAddress, opt.port, opt.username
                    // and, for the host, opt.roundSeconds, then drive the window
                    // through the GameView interface. Until then the program explains and exits.
                    JOptionPane.showMessageDialog(null,
                        "Networking is not implemented yet.\nSee the TODO in turfwar.Main.",
                        "Turf War", JOptionPane.INFORMATION_MESSAGE);
                    System.exit(0);
                    return;
                default:
                    throw new IllegalStateException();
            }
            window.setVisible(true);
        });
    }

    private static int parsePort(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return -1; }
    }
}
