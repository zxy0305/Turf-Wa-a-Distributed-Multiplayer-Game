package turfwar.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.function.Consumer;

/**
 * Shown to the host when a player asks to join. Non-modal, so the host can keep playing.
 * Closing the dialog without choosing counts as Deny.
 */
final class JoinRequestDialog extends JDialog {

    JoinRequestDialog(JFrame owner, String name, Consumer<Boolean> answer) {
        super(owner, "Join request", false);
        JLabel text = new JLabel("<html><b>" + name + "</b> wants to join your match.</html>");
        text.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JButton approve = new JButton("Approve");
        JButton deny = new JButton("Deny");
        final boolean[] answered = {false};
        Consumer<Boolean> reply = ok -> {
            if (answered[0]) return;
            answered[0] = true;
            answer.accept(ok);
            dispose();
        };
        approve.addActionListener(e -> reply.accept(true));
        deny.addActionListener(e -> reply.accept(false));
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) { reply.accept(false); }
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(deny);
        buttons.add(approve);
        setLayout(new BorderLayout());
        add(text, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(approve);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        pack();
        setLocationRelativeTo(owner);
        setAlwaysOnTop(true);
    }
}
