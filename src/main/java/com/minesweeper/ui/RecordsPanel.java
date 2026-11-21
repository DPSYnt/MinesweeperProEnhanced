package com.minesweeper.ui;

import com.minesweeper.model.Achievement;
import com.minesweeper.model.GameSnapshot;
import com.minesweeper.model.GameStatus;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;

/** 同一窗口集中展示最近一局统计、行动记录和本次运行的成就。 */
@SuppressWarnings("serial")
public final class RecordsPanel extends JPanel {
    private final JLabel summary = UiTheme.label("", 12, UiTheme.MUTED, false);
    private final JEditorPane statistics = document();
    private final JEditorPane achievements = document();
    private final JTextArea history = new JTextArea();
    private String lastStatistics = "";
    private String lastAchievements = "";

    public RecordsPanel(Runnable onRefresh) {
        super(new BorderLayout(0, 16));
        setBackground(UiTheme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(18, 20, 14, 20));
        setPreferredSize(new Dimension(820, 680));
        JPanel heading = new JPanel(new BorderLayout(12, 0));
        heading.setOpaque(false);
        JPanel title = new JPanel(new BorderLayout(0, 5));
        title.setOpaque(false);
        title.add(UiTheme.label("对局记录", 24, UiTheme.TEXT, true), BorderLayout.NORTH);
        title.add(summary, BorderLayout.SOUTH);
        heading.add(title, BorderLayout.CENTER);
        UiTheme.Button refresh = new UiTheme.Button("刷新记录", false);
        refresh.addActionListener(event -> onRefresh.run());
        heading.add(refresh, BorderLayout.EAST);
        add(heading, BorderLayout.NORTH);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("本局统计", scroll(statistics));
        history.setEditable(false);
        history.setLineWrap(true);
        history.setWrapStyleWord(true);
        history.setFont(UiTheme.font(13, false));
        history.setForeground(UiTheme.TEXT);
        history.setBackground(UiTheme.SURFACE);
        history.setMargin(new java.awt.Insets(16, 18, 16, 18));
        tabs.addTab("行动记录", scroll(history));
        tabs.addTab("会话成就", scroll(achievements));
        add(tabs, BorderLayout.CENTER);
        add(UiTheme.label("行动后更新；刷新可更新用时。新游戏替换本局记录，成就保留至退出程序。",
                11, UiTheme.MUTED, false), BorderLayout.SOUTH);
    }

    public void render(GameSnapshot state, List<String> events) {
        String status = state.status() == GameStatus.PLAYING ? "进行中" : state.status() == GameStatus.WON ? "已获胜" : "已结束";
        summary.setText(state.difficulty().displayName() + " · " + state.size() + "×" + state.size()
                + " · 第 " + state.turn() + " 回合 · " + status);
        String html = statisticsHtml(state);
        if (!html.equals(lastStatistics)) {
            lastStatistics = html;
            statistics.setText(html);
            statistics.setCaretPosition(0);
        }
        StringBuilder content = new StringBuilder("<h2>会话成就</h2><p>已解锁 <b>")
                .append(state.achievements().size()).append(" / ").append(Achievement.values().length)
                .append("</b>。成就与累计清雷数在本次程序运行期间保留。</p><table width='100%' cellspacing='3'>");
        for (Achievement achievement : Achievement.values()) {
            content.append("<tr><td width='90'>")
                    .append(state.achievements().contains(achievement) ? "<b>已解锁</b>" : "未解锁")
                    .append("</td><td><b>").append(escape(achievement.displayName())).append("</b><br>")
                    .append(escape(achievement.description())).append("</td></tr>");
        }
        String achievementHtml = RuleBook.page(content.append("</table>").toString());
        if (!achievementHtml.equals(lastAchievements)) {
            lastAchievements = achievementHtml;
            achievements.setText(achievementHtml);
            achievements.setCaretPosition(0);
        }
        String log = events.isEmpty() ? "本局尚无行动记录。" : String.join("\n\n", events);
        if (!log.equals(history.getText())) {
            history.setText(log);
            history.setCaretPosition(0);
        }
    }

    private static String statisticsHtml(GameSnapshot state) {
        var stats = state.statistics();
        int informer = (int) state.cells().stream().filter(cell -> cell.mineType() == com.minesweeper.model.MineType.INFORMER_CORE).count();
        StringBuilder html = new StringBuilder("<h2>本局概览</h2>");
        if (!state.outcome().isEmpty()) html.append("<p><b>").append(escape(state.outcome())).append("</b></p>");
        html.append("<table width='100%' cellspacing='3'>")
                .append(row("用时 / 回合", "%02d:%02d / 第 %d 回合".formatted(stats.elapsedSeconds() / 60, stats.elapsedSeconds() % 60, state.turn())))
                .append(row("生命 / 控制区", state.hp() + " HP / " + state.revealedCount() + " 格，共 " + state.cells().size() + " 格"))
                .append(row("初始雷数", "普通%d + 内鬼%d + 国王1 = %d颗".formatted(state.difficulty().initialMines(),
                        state.informerZones().size(), state.difficulty().totalInitialMines())))
                .append(row("当前剩余雷", "普通%d + 内鬼%d + 国王%d = %d颗".formatted(state.normalMineCount(), informer,
                        state.kingAlive() ? 1 : 0, state.remainingMineCount())))
                .append(row("排雷成绩", "清除%d颗 · 正确排雷%d次".formatted(stats.minesCleared(), stats.correctFlags())))
                .append(row("有效行动", stats.totalMoves() + "次"))
                .append(row("会话累计清雷", stats.lifetimeMinesCleared() + "颗"))
                .append(row("增殖 / 伤害", "每回合%.0f%% / 每颗引爆地雷扣%d HP".formatted(
                        state.difficulty().growthRate() * 100, state.difficulty().damageMultiplier())));
        for (var zone : state.informerZones()) {
            html.append(row("内鬼区 " + (zone.id() + 1), zone.active() ? "内鬼雷未排除 · 干扰中 · 剩余" + zone.remainingMines() + "颗"
                    : "干扰已解除 · 第" + zone.capturedTurn() + "回合 · 剩余" + zone.remainingMines() + "颗"));
        }
        html.append(row("国王雷", state.kingAlive() ? "存活 · 须保留至胜利" : "已清除 · 游戏失败"));
        return RuleBook.page(html.append("</table>").toString());
    }

    private static String row(String label, String value) {
        return "<tr><th width='145' align='left'>" + escape(label) + "</th><td>" + escape(value) + "</td></tr>";
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static JEditorPane document() {
        JEditorPane editor = new JEditorPane("text/html", "");
        editor.setEditable(false);
        editor.setBackground(UiTheme.SURFACE);
        return editor;
    }

    private static JScrollPane scroll(javax.swing.JComponent component) {
        JScrollPane scroll = new JScrollPane(component);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        return scroll;
    }
}
