package com.minesweeper.ui;

import com.minesweeper.model.Difficulty;
import com.minesweeper.service.RuleGuide;
import java.util.Locale;

/** 将共用规则正文呈现为桌面规则页，难度数值直接读取配置。 */
public final class RuleBook {
    private RuleBook() { }

    public static String difficultySummary(Difficulty difficulty) {
        return RuleGuide.difficultySummary(difficulty);
    }

    static String page(String body) {
        return "<html><head><style>body{font-family:'Microsoft YaHei UI';font-size:12px;color:#1d3531;"
                + "margin:18px;}h2{font-size:20px;color:#167762;}h3{font-size:14px;color:#167762;}"
                + "p,li{margin-top:7px;margin-bottom:8px;}td,th{padding:9px;}"
                + "th{background:#e5f5ee;}td{background:#f4f8f6;}small{color:#728480;}</style></head><body>"
                + body + "</body></html>";
    }

    public static String quickHtml() {
        StringBuilder text = new StringBuilder();
        for (RuleGuide.Section section : RuleGuide.BASIC_RULES) append(text, section);
        text.append("<h3>电脑操作</h3><p>左键探索，右键排雷；在已揭开格上同时按左右键或双击左键辅助展开。拖出原格后松开可取消。</p>"
                + "<p>方向键定位，Enter/空格探索，F 排雷；Ctrl+Enter 结束回合，Ctrl+N 返回菜单，Ctrl+H 查看记录，F1 查看规则。"
                + "滚轮滚动，Ctrl+滚轮缩放，Shift+滚轮横移。</p>");
        append(text, RuleGuide.SESSION);
        return page(text.toString());
    }

    public static String difficultyHtml() {
        StringBuilder text = new StringBuilder("<h2>难度配置</h2>"
                + "<table width='100%' cellspacing='3'><tr><th>难度 / 棋盘</th><th>初始总雷数</th><th>初始HP</th>"
                + "<th>扣血</th><th>普通雷增殖</th><th>内鬼区（每区）</th></tr>");
        for (Difficulty difficulty : Difficulty.values()) {
            text.append(String.format(Locale.ROOT,
                    "<tr><td>%s<br>%d×%d</td><td><b>%d</b></td><td>%d</td><td>%d HP</td><td>%.0f%%</td>"
                            + "<td>%d×%d<br>%d颗</td></tr>",
                    difficulty.displayName(), difficulty.boardSize(), difficulty.boardSize(), difficulty.totalInitialMines(),
                    difficulty.initialHp(), difficulty.damageMultiplier(), difficulty.growthRate() * 100,
                    difficulty.informerZoneSize(), difficulty.informerZoneSize(), difficulty.zoneMineCount()));
        }
        return page(text.append("</table><p>").append(RuleGuide.DIFFICULTY_NOTE).append("</p>").toString());
    }

    public static String specialHtml() {
        StringBuilder text = new StringBuilder();
        for (RuleGuide.Section section : RuleGuide.SPECIAL_RULES) append(text, section);
        return page(text.toString());
    }

    private static void append(StringBuilder text, RuleGuide.Section section) {
        text.append("<h3>").append(section.title()).append("</h3><p>")
                .append(section.text().replace("\n", "<br>")).append("</p>");
    }
}
