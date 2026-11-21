package com.minesweeper.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JPanel;

/** 规则示意图：只展示教学样例，不包含真实局面的隐藏信息。 */
@SuppressWarnings("serial")
final class RuleDiagram extends JPanel {
    RuleDiagram() { setOpaque(false); setPreferredSize(new Dimension(800, 150)); }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        UiTheme.smooth(g);
        int width = getWidth() / 4;
        for (int diagram = 0; diagram < 4; diagram++) {
            int x = width * diagram + (width - 84) / 2;
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    boolean center = row == 1 && col == 1;
                    g.setColor(diagram == 2 && !center ? new Color(222, 231, 227) : UiTheme.ACCENT_LIGHT);
                    g.fillRoundRect(x + col * 29, 12 + row * 29, 26, 26, 6, 6);
                    if (diagram == 3 && !center) {
                        g.setColor(UiTheme.ACCENT);
                        g.drawRoundRect(x + col * 29, 12 + row * 29, 26, 26, 6, 6);
                    }
                    if (diagram == 0 && (row == 0 && col == 0 || row == 0 && col == 2 || row == 2 && col == 1)) {
                        IconFactory.mine(com.minesweeper.model.MineType.NORMAL).paintIcon(this, g,
                                x + col * 29 + 1, 12 + row * 29 + 1);
                    }
                }
            }
            g.setFont(UiTheme.font(20, true));
            g.setColor(UiTheme.ACCENT);
            g.drawString(switch (diagram) { case 0 -> "3"; case 1 -> "+"; case 2 -> "1"; default -> "0"; }, x + 36, 61);
            g.setFont(UiTheme.font(11, true));
            String text = switch (diagram) {
                case 0 -> "数字 = 周围八格的雷数";
                case 1 -> "绿色边缘表示可操作范围";
                case 2 -> "环境变化时边缘收缩一层";
                default -> "数字归零，左右键辅助展开";
            };
            g.drawString(text, width * diagram + (width - g.getFontMetrics().stringWidth(text)) / 2, 123);
        }
        g.dispose();
    }
}
