package com.minesweeper.ui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;

/** 统一的轻量主题，绘制使用Swing逻辑像素，兼容系统HiDPI缩放。 */
final class UiTheme {
    static final Color BACKGROUND = new Color(239, 243, 242);
    static final Color SURFACE = Color.WHITE;
    static final Color TEXT = new Color(29, 53, 49);
    static final Color MUTED = new Color(114, 132, 128);
    static final Color LINE = new Color(225, 233, 230);
    static final Color ACCENT = new Color(22, 119, 98);
    static final Color ACCENT_LIGHT = new Color(229, 245, 238);
    static final Color GOLD = new Color(175, 126, 40);
    static final Color DANGER = new Color(190, 71, 66);
    static final Color PURPLE = new Color(130, 96, 170);

    private UiTheme() { }
    static Font font(float size, boolean bold) {
        return new Font("Microsoft YaHei UI", bold ? Font.BOLD : Font.PLAIN, Math.round(size));
    }

    static void install() {
        var keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            if (UIManager.get(key) instanceof FontUIResource) UIManager.put(key, new FontUIResource(font(13, false)));
        }
        UIManager.put("Panel.background", BACKGROUND);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("OptionPane.background", BACKGROUND);
        UIManager.put("TextArea.background", SURFACE);
    }

    static JLabel label(String text, int size, Color color, boolean bold) {
        JLabel label = new JLabel(text);
        label.setFont(font(size, bold));
        label.setForeground(color);
        return label;
    }

    static void smooth(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    @SuppressWarnings("serial")
    static final class Card extends JPanel {
        Card() { setOpaque(false); setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18)); }
        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D copy = (Graphics2D) graphics.create();
            smooth(copy);
            copy.setColor(new Color(25, 55, 45, 8));
            copy.fillRoundRect(1, 3, getWidth() - 2, getHeight() - 4, 20, 20);
            copy.setColor(SURFACE);
            copy.fillRoundRect(1, 1, getWidth() - 2, getHeight() - 4, 20, 20);
            copy.setColor(LINE);
            copy.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 5, 20, 20);
            copy.dispose();
        }
    }

    @SuppressWarnings("serial")
    static final class Button extends JButton {
        private final boolean primary;
        Button(String text, boolean primary) {
            super(text);
            this.primary = primary;
            setFont(font(13, true));
            setForeground(primary ? Color.WHITE : TEXT);
            setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));
            setContentAreaFilled(false);
            setFocusPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setRolloverEnabled(true);
        }
        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D copy = (Graphics2D) graphics.create();
            smooth(copy);
            Color fill = primary ? ACCENT : SURFACE;
            if (!isEnabled()) fill = primary ? new Color(151, 178, 169) : BACKGROUND;
            else if (getModel().isPressed()) fill = primary ? ACCENT.darker() : ACCENT_LIGHT;
            else if (getModel().isRollover()) fill = primary ? new Color(18, 103, 84) : new Color(245, 250, 247);
            copy.setColor(fill);
            copy.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            copy.setColor(primary ? fill : LINE);
            copy.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            if (isFocusOwner()) {
                copy.setColor(primary ? new Color(170, 224, 201) : ACCENT);
                copy.drawRoundRect(3, 3, getWidth() - 7, getHeight() - 7, 9, 9);
            }
            copy.dispose();
            super.paintComponent(graphics);
        }
    }
}
