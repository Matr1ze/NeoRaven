package xyz.daikirai.music;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Frame;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.function.Consumer;

/** A short-lived, explicitly opened IME-enabled search window; all Swing work stays on the EDT. */
final class MusicSearchInput {
    private final String initial;
    private final Consumer<String> completion;
    private JDialog dialog;
    private boolean finished;

    MusicSearchInput(String initial, Consumer<String> completion) {
        this.initial = initial;
        this.completion = completion;
    }

    void open() {
        SwingUtilities.invokeLater(() -> {
            if (finished) return;
            dialog = new JDialog((Frame) null, "NeoRaven 1.2 · 网易云搜索", false);
            dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            dialog.setResizable(false);
            JPanel panel = new JPanel(new BorderLayout(0, 14));
            panel.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
            panel.setBackground(new Color(0x20242C));
            JLabel label = new JLabel("使用中文输入法输入歌名或歌手，点击搜索返回游戏");
            label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            label.setForeground(new Color(0xE6E9EF));
            panel.add(label, BorderLayout.NORTH);
            JTextField input = new JTextField(initial, 28);
            input.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
            input.enableInputMethods(true);
            panel.add(input, BorderLayout.CENTER);
            JPanel actions = new JPanel();
            actions.setOpaque(false);
            JButton cancel = new JButton("取消");
            JButton search = new JButton("搜索");
            cancel.addActionListener(event -> finish(null));
            search.addActionListener(event -> finish(input.getText()));
            input.addActionListener(event -> finish(input.getText()));
            actions.add(cancel);
            actions.add(search);
            panel.add(actions, BorderLayout.SOUTH);
            dialog.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent event) {
                    finish(null);
                }
            });
            dialog.getRootPane().registerKeyboardAction(event -> finish(null),
                    KeyStroke.getKeyStroke("ESCAPE"), javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
            dialog.setContentPane(panel);
            dialog.pack();
            dialog.setLocationRelativeTo(null);
            dialog.setVisible(true);
            input.selectAll();
            input.requestFocusInWindow();
        });
    }

    void close() {
        SwingUtilities.invokeLater(() -> finish(null));
    }

    private void finish(String result) {
        if (finished) return;
        finished = true;
        if (dialog != null) {
            dialog.dispose();
            dialog = null;
        }
        completion.accept(result);
    }
}
