package com.minesweeper.ui;

import com.minesweeper.model.GameSnapshot;
import com.minesweeper.model.GameStatus;
import com.minesweeper.model.Achievement;
import com.minesweeper.config.GameRules;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JComponent;
import javax.swing.JProgressBar;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.BasicProgressBarUI;

@SuppressWarnings("serial")
final class InfoPanel extends JPanel {
    private final JLabel hp = UiTheme.label("HP：5/" + GameRules.MAX_HP, 28, UiTheme.ACCENT, true);
    private final JLabel phase = UiTheme.label("玩家行动", 12, UiTheme.ACCENT, true);
    private final JLabel control = UiTheme.label("0 / 400", 12, UiTheme.TEXT, true);
    private final JLabel mines = UiTheme.label("80", 25, UiTheme.TEXT, true);
    private final JLabel cleared = UiTheme.label("0", 25, UiTheme.TEXT, true);
    private final JLabel hpHint = UiTheme.label("右键排雷恢复生命", 11, UiTheme.MUTED, false);
    private final JLabel kingColumn = UiTheme.label("等待排除内鬼核心 01", 13, UiTheme.MUTED, true);
    private final JLabel kingRow = UiTheme.label("等待排除内鬼核心 02", 13, UiTheme.MUTED, true);
    private final JLabel achievements = UiTheme.label("成就  0 / " + Achievement.values().length, 11, UiTheme.MUTED, false);
    private final JLabel timer = UiTheme.label("00:00", 28, UiTheme.ACCENT, true);
    private final JProgressBar territory = new JProgressBar(0, 400);
    private final UiTheme.Button endTurn = new UiTheme.Button("结束回合   →", true);
    private final UiTheme.Button restart = new UiTheme.Button("重新开始", false);
    private boolean actionAvailable = true;

    InfoPanel(Runnable onEndTurn, Runnable onRestart) {
        setOpaque(false);
        setLayout(new BorderLayout(0, 14));
        setPreferredSize(new Dimension(270, 647));
        setMinimumSize(new Dimension(250, 500));
        JPanel overview = new JPanel();
        overview.setOpaque(false);
        overview.setLayout(new BoxLayout(overview, BoxLayout.Y_AXIS));
        overview.add(vitals());
        overview.add(Box.createVerticalStrut(14));
        overview.add(intelligence());
        overview.setPreferredSize(new Dimension(260, 453));
        JScrollPane scroll = new JScrollPane(overview, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(22);
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(7, 0));
        scroll.getVerticalScrollBar().setUI(new BasicScrollBarUI() {
            @Override
            protected void paintTrack(Graphics graphics, JComponent component, Rectangle bounds) { }
            @Override
            protected void paintThumb(Graphics graphics, JComponent component, Rectangle bounds) {
                Graphics2D copy = (Graphics2D) graphics.create();
                UiTheme.smooth(copy);
                copy.setColor(new Color(187, 205, 195));
                copy.fillRoundRect(bounds.x + 1, bounds.y, Math.max(3, bounds.width - 2), bounds.height, 6, 6);
                copy.dispose();
            }
            @Override
            protected javax.swing.JButton createDecreaseButton(int orientation) { return invisibleButton(); }
            @Override
            protected javax.swing.JButton createIncreaseButton(int orientation) { return invisibleButton(); }
            private javax.swing.JButton invisibleButton() {
                javax.swing.JButton button = new javax.swing.JButton();
                button.setPreferredSize(new Dimension(0, 0));
                button.setMinimumSize(new Dimension(0, 0));
                button.setMaximumSize(new Dimension(0, 0));
                return button;
            }
        });
        add(scroll, BorderLayout.CENTER);
        add(actions(onEndTurn, onRestart), BorderLayout.SOUTH);
    }

    private JPanel vitals() {
        UiTheme.Card card = card(254);
        JPanel title = row();
        title.add(UiTheme.label("局势概览", 15, UiTheme.TEXT, true), BorderLayout.WEST);
        title.add(phase, BorderLayout.EAST);
        card.add(title);
        card.add(Box.createVerticalStrut(13));
        JPanel health = row();
        health.add(hp, BorderLayout.WEST);
        card.add(health);
        card.add(hpHint);
        card.add(Box.createVerticalStrut(16));
        JPanel territoryTitle = row();
        territoryTitle.add(UiTheme.label("控制区域", 12, UiTheme.MUTED, false), BorderLayout.WEST);
        territoryTitle.add(control, BorderLayout.EAST);
        card.add(territoryTitle);
        card.add(Box.createVerticalStrut(7));
        territory.setForeground(UiTheme.ACCENT);
        territory.setBackground(UiTheme.LINE);
        territory.setBorderPainted(false);
        territory.setName("territoryProgress");
        territory.getAccessibleContext().setAccessibleName("控制区域进度");
        territory.setPreferredSize(new Dimension(220, 10));
        territory.setMinimumSize(new Dimension(40, 10));
        territory.setMaximumSize(new Dimension(Integer.MAX_VALUE, 10));
        territory.setUI(new BasicProgressBarUI() {
            @Override
            protected void paintDeterminate(Graphics graphics, JComponent component) {
                Graphics2D copy = (Graphics2D) graphics.create();
                UiTheme.smooth(copy);
                int width = component.getWidth();
                int height = component.getHeight();
                copy.setColor(territory.getBackground());
                copy.fillRoundRect(0, 0, width, height, height, height);
                int fill = (int) Math.round(width * territory.getPercentComplete());
                if (fill > 0) {
                    copy.setColor(territory.getForeground());
                    copy.fillRoundRect(0, 0, fill, height, height, height);
                }
                copy.dispose();
            }
        });
        card.add(territory);
        card.add(Box.createVerticalStrut(17));
        JPanel metrics = new JPanel(new GridLayout(1, 2, 14, 0));
        metrics.setOpaque(false);
        metrics.add(metric("剩余地雷（全部）", mines));
        metrics.add(metric("本局清雷", cleared));
        card.add(metrics);
        alignLeft(card);
        return card;
    }

    private JPanel intelligence() {
        UiTheme.Card card = card(185);
        for (JLabel clue : new JLabel[]{kingColumn, kingRow}) {
            clue.setMaximumSize(new Dimension(Integer.MAX_VALUE, clue.getPreferredSize().height));
        }
        card.add(UiTheme.label("内鬼情报", 15, UiTheme.TEXT, true));
        card.add(Box.createVerticalStrut(5));
        card.add(UiTheme.label("排除核心，解除干扰并解锁情报", 11, UiTheme.MUTED, false));
        card.add(Box.createVerticalStrut(15));
        card.add(UiTheme.label("01  /  国王列坐标 X", 10, UiTheme.PURPLE, true));
        card.add(Box.createVerticalStrut(4));
        card.add(kingColumn);
        card.add(Box.createVerticalStrut(14));
        card.add(UiTheme.label("02  /  国王行坐标 Y", 10, UiTheme.PURPLE, true));
        card.add(Box.createVerticalStrut(4));
        card.add(kingRow);
        alignLeft(card);
        return card;
    }

    private JPanel actions(Runnable onEndTurn, Runnable onRestart) {
        UiTheme.Card card = card(258);
        JPanel clock = row();
        clock.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        clock.add(UiTheme.label("本局用时", 12, UiTheme.MUTED, true), BorderLayout.WEST);
        timer.setName("gameTimer");
        clock.add(timer, BorderLayout.EAST);
        card.add(clock);
        card.add(Box.createVerticalStrut(8));
        card.add(UiTheme.label("<html>清除其他雷，保留国王雷。<br>控制区需至少 20 格。</html>",
                11, UiTheme.MUTED, false));
        card.add(Box.createVerticalStrut(14));
        endTurn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        endTurn.setAlignmentX(LEFT_ALIGNMENT);
        endTurn.addActionListener(event -> onEndTurn.run());
        card.add(endTurn);
        card.add(Box.createVerticalStrut(6));
        restart.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        restart.addActionListener(event -> onRestart.run());
        card.add(restart);
        card.add(Box.createVerticalStrut(9));
        JPanel footer = row();
        footer.add(UiTheme.label("Ctrl + Enter", 10, UiTheme.MUTED, false), BorderLayout.WEST);
        footer.add(achievements, BorderLayout.EAST);
        card.add(footer);
        alignLeft(card);
        return card;
    }

    void render(GameSnapshot snapshot) {
        hp.setText("HP：" + snapshot.hp() + "/" + GameRules.MAX_HP);
        hp.setForeground(snapshot.hp() <= 2 ? UiTheme.DANGER : UiTheme.ACCENT);
        hpHint.setText(snapshot.hp() == GameRules.MAX_HP ? "生命值已达上限" : snapshot.hp() <= 2
                ? "生命值偏低，谨慎排雷" : "右键正确排雷恢复 1 点生命");
        phase.setText(snapshot.status() == GameStatus.PLAYING ? "● 玩家行动"
                : snapshot.status() == GameStatus.WON ? "● 已获胜" : "● 已结束");
        control.setText(snapshot.revealedCount() + " / " + snapshot.cells().size() + " · "
                + Math.round(100.0 * snapshot.revealedCount() / snapshot.cells().size()) + "%");
        territory.setMaximum(snapshot.cells().size());
        territory.setValue(snapshot.revealedCount());
        territory.setForeground(snapshot.revealedCount() < 30 ? UiTheme.DANGER : UiTheme.ACCENT);
        territory.getAccessibleContext().setAccessibleDescription(control.getText());
        mines.setText(Integer.toString(snapshot.remainingMineCount()));
        cleared.setText(Integer.toString(snapshot.statistics().minesCleared()));
        kingColumn.setText(!snapshot.kingAlive() ? "移动雷已清除" : snapshot.informerZones().get(0).active()
                ? "内鬼雷未排除 · 尚余 " + snapshot.informerZones().get(0).remainingMines() + " 雷"
                : "第 " + (snapshot.kingPosition().column() + 1) + " 列 · 区内余 " + snapshot.informerZones().get(0).remainingMines() + " 雷");
        kingRow.setText(!snapshot.kingAlive() ? "移动雷已清除" : snapshot.informerZones().get(1).active()
                ? "内鬼雷未排除 · 尚余 " + snapshot.informerZones().get(1).remainingMines() + " 雷"
                : "第 " + (snapshot.kingPosition().row() + 1) + " 行 · 区内余 " + snapshot.informerZones().get(1).remainingMines() + " 雷");
        kingColumn.setForeground(snapshot.informerZones().get(0).active() ? UiTheme.MUTED : UiTheme.ACCENT);
        kingRow.setForeground(snapshot.informerZones().get(1).active() ? UiTheme.MUTED : UiTheme.ACCENT);
        achievements.setText("成就  " + snapshot.achievements().size() + " / " + Achievement.values().length);
        actionAvailable = snapshot.status() == GameStatus.PLAYING;
        endTurn.setEnabled(actionAvailable);
    }

    void updateTime(long seconds) {
        timer.setText(String.format("%02d:%02d", seconds / 60, seconds % 60));
    }

    void setInputEnabled(boolean enabled) {
        endTurn.setEnabled(enabled && actionAvailable);
        if (!enabled) phase.setText("● 正在结算");
    }


    private static UiTheme.Card card(int height) {
        UiTheme.Card card = new UiTheme.Card();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setAlignmentX(LEFT_ALIGNMENT);
        card.setPreferredSize(new Dimension(270, height));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        card.setMinimumSize(new Dimension(250, height));
        return card;
    }

    private static JPanel metric(String title, JLabel value) {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setOpaque(false);
        panel.add(UiTheme.label(title, 11, UiTheme.MUTED, false), BorderLayout.NORTH);
        panel.add(value, BorderLayout.CENTER);
        return panel;
    }

    private static void alignLeft(JPanel panel) {
        for (Component component : panel.getComponents()) {
            if (component instanceof JComponent child) child.setAlignmentX(LEFT_ALIGNMENT);
        }
    }

    private static JPanel row() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setAlignmentX(LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        return panel;
    }
}
