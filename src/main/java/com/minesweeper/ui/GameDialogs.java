package com.minesweeper.ui;

import com.minesweeper.config.GameRules;
import com.minesweeper.model.GameSnapshot;
import java.awt.GraphicsEnvironment;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;

/** 每类辅助窗口只保留一份，重复点击激活已有窗口。 */
final class GameDialogs {
    private final JFrame owner;
    private final Supplier<GameSnapshot> snapshot;
    private final Supplier<List<String>> history;
    private JDialog rulesDialog;
    private JDialog recordsDialog;
    private RecordsPanel recordsPanel;

    GameDialogs(JFrame owner, Supplier<GameSnapshot> snapshot, Supplier<List<String>> history) {
        this.owner = owner;
        this.snapshot = snapshot;
        this.history = history;
    }

    void showRules() {
        if (rulesDialog == null || !rulesDialog.isDisplayable()) {
            rulesDialog = create("规则与操作", new RulesPanel(), 900, 780);
        }
        activate(rulesDialog);
    }

    void showRecords() {
        if (recordsDialog == null || !recordsDialog.isDisplayable()) {
            recordsPanel = new RecordsPanel(this::refreshRecords);
            recordsDialog = create("对局记录", recordsPanel, 860, 760);
        }
        refreshRecords();
        activate(recordsDialog);
    }

    void refreshVisibleRecords() {
        if (recordsDialog != null && recordsDialog.isVisible()) refreshRecords();
    }

    private void refreshRecords() { recordsPanel.render(snapshot.get(), history.get()); }

    private JDialog create(String title, JComponent content, int width, int height) {
        JDialog dialog = new JDialog(owner, GameRules.GAME_NAME + " · " + title, false);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setContentPane(content);
        var screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        dialog.setSize(Math.min(width, screen.width), Math.min(height, screen.height));
        dialog.setLocationRelativeTo(owner);
        return dialog;
    }

    private static void activate(JDialog dialog) {
        dialog.setVisible(true);
        dialog.toFront();
        dialog.requestFocus();
    }

    static String resultSummary(GameSnapshot state) {
        var stats = state.statistics();
        return "%s · %d×%d\n用时 %02d:%02d · 第 %d 回合 · 清雷 %d 颗".formatted(
                state.difficulty().displayName(), state.size(), state.size(), stats.elapsedSeconds() / 60,
                stats.elapsedSeconds() % 60, state.turn(), stats.minesCleared());
    }
}
