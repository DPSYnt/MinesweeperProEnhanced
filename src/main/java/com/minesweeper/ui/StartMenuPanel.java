package com.minesweeper.ui;

import com.minesweeper.config.GameRules;
import com.minesweeper.model.Difficulty;
import com.minesweeper.model.GameSnapshot;
import com.minesweeper.model.GameStatus;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.JLabel;

/** 开始菜单的所有数字来自Difficulty，选择难度本身不会创建或修改对局。 */
@SuppressWarnings("serial")
public final class StartMenuPanel extends JPanel {
    private Difficulty selected = Difficulty.NORMAL;
    private final Map<Difficulty, JToggleButton> options = new EnumMap<>(Difficulty.class);
    private final UiTheme.Card choices = new UiTheme.Card();
    private final UiTheme.Button primary = new UiTheme.Button("开始游戏   →", true);
    private final UiTheme.Button newGame = new UiTheme.Button("开始新游戏", false);
    private final UiTheme.Button records = new UiTheme.Button("对局记录", false);
    private final JLabel sessionSummary = UiTheme.label("准备开始新的挑战", 11, UiTheme.MUTED, false);
    private final java.awt.Component newGameGap = Box.createVerticalStrut(8);
    private boolean canResume;
    private boolean compactLayout;

    public StartMenuPanel(Consumer<Difficulty> onStart, Runnable onRules, Runnable onExit,
                          Runnable onResume, Runnable onRecords) {
        super(new BorderLayout(30, 20));
        setBackground(UiTheme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(36, 36, 28, 36));
        setPreferredSize(new Dimension(1100, 850));
        add(UiTheme.label("动态扫雷 · 回合策略", 11, UiTheme.ACCENT, true), BorderLayout.NORTH);
        JPanel body = new JPanel(new GridLayout(1, 2, 30, 0));
        body.setOpaque(false);
        JPanel story = new JPanel();
        story.setLayout(new BoxLayout(story, BoxLayout.Y_AXIS));
        story.setOpaque(false);
        story.add(Box.createVerticalGlue());
        story.add(UiTheme.label(GameRules.GAME_NAME, 38, UiTheme.TEXT, true));
        story.add(Box.createVerticalStrut(15));
        story.add(UiTheme.label("每一次扩张，都是新的机会。", 19, UiTheme.ACCENT, true));
        story.add(Box.createVerticalStrut(20));
        story.add(UiTheme.label("<html>探索未知区域，排除地雷。<br>在不断变化的棋盘中，守住安全区。<br><br>"
                + "清除其他全部地雷，保留国王雷获胜。<br>提前引爆或排除国王雷，立即失败。</html>", 14, UiTheme.MUTED, false));
        story.add(Box.createVerticalStrut(30));
        UiTheme.Button rules = new UiTheme.Button("规则与操作", false);
        rules.addActionListener(event -> onRules.run());
        story.add(rules);
        story.add(Box.createVerticalStrut(10));
        records.addActionListener(event -> onRecords.run());
        records.setVisible(false);
        story.add(records);
        story.add(Box.createVerticalStrut(20));
        story.add(UiTheme.label("左键探索 / 右键排雷 / 左右键辅助", 11, UiTheme.MUTED, false));
        story.add(Box.createVerticalGlue());
        body.add(story);
        choices.setLayout(new BoxLayout(choices, BoxLayout.Y_AXIS));
        choices.add(UiTheme.label("新游戏难度", 23, UiTheme.TEXT, true));
        choices.add(Box.createVerticalStrut(7));
        choices.add(UiTheme.label("棋盘越大，地雷越多，容错越低。", 12, UiTheme.MUTED, false));
        choices.add(Box.createVerticalStrut(16));
        ButtonGroup group = new ButtonGroup();
        for (Difficulty difficulty : Difficulty.values()) {
            DifficultyOption option = new DifficultyOption(difficulty);
            option.setAlignmentX(LEFT_ALIGNMENT);
            option.addActionListener(event -> selected = difficulty);
            group.add(option);
            options.put(difficulty, option);
            choices.add(option);
            choices.add(Box.createVerticalStrut(8));
        }
        options.get(selected).setSelected(true);
        choices.add(Box.createVerticalGlue());
        sessionSummary.setAlignmentX(LEFT_ALIGNMENT);
        choices.add(sessionSummary);
        choices.add(Box.createVerticalStrut(8));
        primary.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        primary.setAlignmentX(LEFT_ALIGNMENT);
        primary.addActionListener(event -> {
            if (canResume) onResume.run();
            else onStart.accept(selected);
        });
        choices.add(primary);
        choices.add(newGameGap);
        newGame.setAlignmentX(LEFT_ALIGNMENT);
        newGame.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        newGame.addActionListener(event -> onStart.accept(selected));
        newGame.setVisible(false);
        newGameGap.setVisible(false);
        choices.add(newGame);
        choices.add(Box.createVerticalStrut(10));
        UiTheme.Button exit = new UiTheme.Button("退出游戏", false);
        exit.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        exit.setAlignmentX(LEFT_ALIGNMENT);
        exit.addActionListener(event -> onExit.run());
        choices.add(exit);
        body.add(choices);
        add(body, BorderLayout.CENTER);
        add(UiTheme.label("成就保留至本次运行结束；退出后不保存对局。", 11, UiTheme.MUTED, false),
                BorderLayout.SOUTH);
    }

    public Difficulty selectedDifficulty() { return selected; }
    public void selectDifficulty(Difficulty difficulty) {
        options.get(difficulty).doClick(0);
    }
    public void setSession(GameSnapshot state) {
        canResume = state != null && state.status() == GameStatus.PLAYING;
        primary.setText(canResume ? "继续当前对局   →" : "开始游戏   →");
        newGame.setVisible(canResume);
        newGameGap.setVisible(canResume);
        records.setVisible(state != null);
        sessionSummary.setText(state == null ? "准备开始新的挑战" : canResume
                ? "当前：" + state.difficulty().displayName() + " · 第 " + state.turn() + " 回合 · " + state.hp() + " HP"
                : "上一局：" + state.difficulty().displayName() + (state.status() == GameStatus.WON ? " · 已获胜" : " · 已结束"));
        choices.invalidate();
        revalidate();
        repaint();
    }

    @Override
    public void doLayout() {
        boolean compact = getHeight() < 760;
        if (compact != compactLayout) {
            compactLayout = compact;
            setBorder(BorderFactory.createEmptyBorder(compact ? 20 : 36, compact ? 24 : 36,
                    compact ? 16 : 28, compact ? 24 : 36));
            ((BorderLayout) getLayout()).setVgap(compact ? 12 : 20);
            for (JToggleButton option : options.values()) {
                option.setPreferredSize(new Dimension(400, compact ? 64 : 90));
                option.setMinimumSize(new Dimension(330, compact ? 60 : 84));
                option.setMaximumSize(new Dimension(Integer.MAX_VALUE, compact ? 64 : 90));
                option.setBorder(BorderFactory.createEmptyBorder(compact ? 4 : 8, 16, compact ? 4 : 8, 34));
            }
            choices.invalidate();
        }
        super.doLayout();
    }

    private static final class DifficultyOption extends JToggleButton {
        DifficultyOption(Difficulty difficulty) {
            String text = "<html><b>" + difficulty.displayName() + "　" + difficulty.boardSize() + " × "
                    + difficulty.boardSize() + "</b><br>普通雷 " + difficulty.initialMines() + " + 内鬼雷 "
                    + GameRules.INFORMER_COUNT + " + 国王雷 1 = <b>共 " + difficulty.totalInitialMines()
                    + " 颗</b><br>" + difficulty.initialHp() + " HP · 每颗扣 " + difficulty.damageMultiplier()
                    + " HP · 普通雷增殖 " + Math.round(difficulty.growthRate() * 100) + "%</html>";
            setText(text);
            setFont(UiTheme.font(12, false));
            setForeground(UiTheme.TEXT);
            setHorizontalAlignment(LEFT);
            setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 34));
            setContentAreaFilled(false);
            setFocusPainted(false);
            setOpaque(false);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
            setPreferredSize(new Dimension(400, 90));
            setMinimumSize(new Dimension(330, 84));
            setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            UiTheme.smooth(g);
            g.setColor(isSelected() ? UiTheme.ACCENT_LIGHT : Color.WHITE);
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            g.setColor(isSelected() || isFocusOwner() ? UiTheme.ACCENT : UiTheme.LINE);
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            g.drawOval(getWidth() - 28, getHeight() / 2 - 7, 14, 14);
            if (isSelected()) g.fillOval(getWidth() - 24, getHeight() / 2 - 3, 6, 6);
            g.dispose();
            super.paintComponent(graphics);
        }
    }
}
