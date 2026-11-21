package com.minesweeper.ui;

import com.minesweeper.model.MineType;
import java.awt.Color;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;
import javax.swing.ImageIcon;

/** 每种雷与每帧爆炸图标只生成一次，避免逐格逐帧分配图片。 */
final class IconFactory {
    static final int CELL_PIXELS = 30;
    static final int EXPLOSION_FRAMES = 8;
    private static final int ICON_SIZE = CELL_PIXELS - 6;
    private static final Map<MineType, ImageIcon> MINES = new EnumMap<>(MineType.class);
    private static final ImageIcon[] EXPLOSIONS = new ImageIcon[EXPLOSION_FRAMES];

    static {
        MINES.put(MineType.NORMAL, createMine(MineType.NORMAL));
        MINES.put(MineType.INFORMER_CORE, createMine(MineType.INFORMER_CORE));
        MINES.put(MineType.KING, createMine(MineType.KING));
        for (int frame = 0; frame < EXPLOSION_FRAMES; frame++) {
            EXPLOSIONS[frame] = createExplosion(frame);
        }
    }

    private IconFactory() { }
    static ImageIcon mine(MineType type) { return MINES.get(type); }
    static ImageIcon explosion(int frame) { return EXPLOSIONS[frame]; }

    private static ImageIcon createMine(MineType type) {
        BufferedImage image = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            if (type == MineType.KING) {
                graphics.setColor(new Color(191, 139, 42));
                graphics.fillPolygon(new int[]{3, 7, 12, 17, 21, 18, 6},
                        new int[]{7, 11, 4, 11, 7, 18, 18}, 7);
                graphics.drawLine(7, 21, 17, 21);
                graphics.setColor(new Color(255, 242, 192));
                graphics.fillOval(10, 12, 4, 4);
            } else if (type == MineType.INFORMER_CORE) {
                graphics.setColor(UiTheme.PURPLE);
                graphics.fillPolygon(new int[]{12, 22, 12, 2}, new int[]{2, 12, 22, 12}, 4);
                graphics.setColor(Color.WHITE);
                graphics.drawOval(7, 9, 10, 6);
                graphics.fillOval(11, 11, 3, 3);
            } else {
                graphics.setColor(new Color(165, 72, 65));
                for (int angle = 0; angle < 8; angle++) {
                    double radians = Math.PI * angle / 4;
                    graphics.drawLine(12 + (int) (7 * Math.cos(radians)), 12 + (int) (7 * Math.sin(radians)),
                            12 + (int) (10 * Math.cos(radians)), 12 + (int) (10 * Math.sin(radians)));
                }
                graphics.fillOval(5, 5, 14, 14);
                graphics.setColor(new Color(255, 223, 207));
                graphics.fillOval(8, 8, 4, 4);
            }
        } finally {
            graphics.dispose();
        }
        return new ImageIcon(image);
    }

    private static ImageIcon createExplosion(int frame) {
        float progress = (float) frame / EXPLOSION_FRAMES;
        BufferedImage image = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(255, (int) (255 * (1 - progress)), 0));
            int size = (int) (ICON_SIZE * (0.5 + 0.5 * progress));
            graphics.fillOval((ICON_SIZE - size) / 2, (ICON_SIZE - size) / 2, size, size);
            graphics.setColor(Color.WHITE);
            int center = size / 3;
            graphics.fillOval((ICON_SIZE - center) / 2, (ICON_SIZE - center) / 2, center, center);
        } finally {
            graphics.dispose();
        }
        return new ImageIcon(image);
    }
}
