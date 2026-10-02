package turfwar.ui;

import turfwar.model.Config;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/**
 * The first window: host a match, join a match, or run the local demo. Collects server address,
 * port, username and (for the host) the round length. The server assigns colours. Validates the input.
 */
public final class StartDialog extends JDialog {

    public enum Mode { HOST, JOIN, LOCAL_DEMO }

    /** The user's choices. Null when the dialog was cancelled. */
    public static final class Options {
        public final Mode mode;
        public final String serverAddress;
        public final int port;
        public final String username;
        public final int roundSeconds;

        Options(Mode mode, String serverAddress, int port, String username, int roundSeconds) {
            this.mode = mode;
            this.serverAddress = serverAddress;
            this.port = port;
            this.username = username;
            this.roundSeconds = roundSeconds;
        }
    }

    private final JRadioButton host = new JRadioButton("Host a match", true);
    private final JRadioButton join = new JRadioButton("Join a match");
    private final JRadioButton local = new JRadioButton("Local demo (no network)");
    private final JTextField address = new JTextField("localhost", 16);
    private final JTextField port = new JTextField("", 6);
    private final JTextField username = new JTextField("", 16);
    private final JSpinner roundLength = new JSpinner(new SpinnerNumberModel(
        Config.DEFAULT_ROUND_SECONDS, Config.MIN_ROUND_SECONDS, Config.MAX_ROUND_SECONDS, 10));
    private Options result;

    /**
     * @param defaultAddress pre-filled server address (from the command line), may be null
     * @param defaultPort    pre-filled port, or a value outside 1..65535 to leave it blank
     * @param defaultName    pre-filled username, may be null
     */
    public StartDialog(String defaultAddress, int defaultPort, String defaultName) {
        super((java.awt.Frame) null, "Turf War", true);
        if (defaultAddress != null) address.setText(defaultAddress);
        if (defaultPort > 0 && defaultPort <= 65535) port.setText(Integer.toString(defaultPort));
        if (defaultName != null) username.setText(defaultName);
        ButtonGroup g = new ButtonGroup();
        g.add(host); g.add(join); g.add(local);
        JPanel modes = new JPanel(new FlowLayout(FlowLayout.LEFT));
        modes.add(host); modes.add(join); modes.add(local);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        int row = 0;
        addRow(form, c, row++, "Server address", address);
        addRow(form, c, row++, "Server port", port);
        addRow(form, c, row++, "Username", username);
        addRow(form, c, row++, "Round length (s)", roundLength);

        JButton ok = new JButton("Start");
        JButton cancel = new JButton("Quit");
        ok.addActionListener(e -> { if (validateAndStore()) dispose(); });
        cancel.addActionListener(e -> { result = null; dispose(); });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancel); buttons.add(ok);

        Runnable sync = () -> {
            boolean net = !local.isSelected();
            address.setEnabled(net); port.setEnabled(net);
            roundLength.setEnabled(host.isSelected() || local.isSelected());
        };
        host.addActionListener(e -> sync.run());
        join.addActionListener(e -> sync.run());
        local.addActionListener(e -> sync.run());
        sync.run();

        setLayout(new BorderLayout());
        add(modes, BorderLayout.NORTH);
        add(form, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(ok);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        pack();
        setLocationRelativeTo(null);
    }

    /** Shows the dialog and returns the options, or null if the user quit. */
    public Options showAndGet() {
        setVisible(true);
        return result;
    }

    private boolean validateAndStore() {
        Mode mode = host.isSelected() ? Mode.HOST : join.isSelected() ? Mode.JOIN : Mode.LOCAL_DEMO;
        String name = username.getText().trim();
        if (!Config.validName(name)) {
            JOptionPane.showMessageDialog(this, "Username: 1 to " + Config.MAX_NAME_LENGTH
                + " letters, digits or underscores.", "Invalid username", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        int portNo = 0;
        String addr = address.getText().trim();
        if (mode != Mode.LOCAL_DEMO) {
            if (addr.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Enter the server address.", "Missing address", JOptionPane.WARNING_MESSAGE);
                return false;
            }
            try { portNo = Integer.parseInt(port.getText().trim()); } catch (NumberFormatException e) { portNo = -1; }
            if (portNo < 1 || portNo > 65535) {
                JOptionPane.showMessageDialog(this, "Port must be a number from 1 to 65535.", "Invalid port", JOptionPane.WARNING_MESSAGE);
                return false;
            }
        }
        result = new Options(mode, addr, portNo, name, (Integer) roundLength.getValue());
        return true;
    }

    private static void addRow(JPanel form, GridBagConstraints c, int row, String label, Component field) {
        c.gridx = 0; c.gridy = row; form.add(new JLabel(label + ":"), c);
        c.gridx = 1; form.add(field, c);
    }
}
