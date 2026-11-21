package com.minesweeper.ui;

import com.minesweeper.config.GameRules;
import com.minesweeper.model.*;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.ArrayList;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/** 轻量自绘格子、合法操作提示以及不依赖mouseClicked的完整按压手势。 */
@SuppressWarnings("serial")
public final class BoardPanel extends JPanel {
    private int size;
    private static final Color[] NUMBER_COLORS = {
        new Color(45, 113, 176), UiTheme.ACCENT, new Color(201, 87, 67), UiTheme.PURPLE,
        new Color(160, 79, 89), new Color(48, 137, 148), UiTheme.TEXT, UiTheme.MUTED
    };
    private CellButton[][] buttons = new CellButton[0][];
    private final Actions actions;
    private boolean inputEnabled;
    private CellButton pressedCell;
    private int heldButtons;
    private boolean chordGesture;
    private final List<CellButton> pressedFeedback = new ArrayList<>(8);
    private long renderedVersion;
    private long pressedVersion;
    private List<Cell> renderedCells;
    private boolean renderedShowAll;
    private boolean feedbackInside;
    private final MouseAdapter mouseListener = createMouseListener();
    private final FocusAdapter focusListener = new FocusAdapter() {
        @Override
        public void focusLost(FocusEvent event) {
            CellButton button = (CellButton) event.getSource();
            if (pressedCell == button) cancelGesture();
            button.repaint();
        }
        @Override
        public void focusGained(FocusEvent event) { event.getComponent().repaint(); }
    };

    public interface Actions {
        void reveal(int row, int column);
        void flag(int row, int column);
        default void chord(int row, int column) { }
        default void hover(Position position, boolean actionable) { }
        default void hint(String message) { }
    }

    public BoardPanel(Actions actions) {
        super();
        this.actions = actions;
        setOpaque(false);
    }

    private void rebuild(int boardSize) {
        cancelGesture();
        removeAll();
        size = boardSize;
        buttons = new CellButton[size][size];
        setLayout(new GridLayout(size, size, 2, 2));
        int pixels = size * IconFactory.CELL_PIXELS + (size - 1) * 2;
        setPreferredSize(new Dimension(pixels, pixels));
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                CellButton button = new CellButton(row, column);
                installMouse(button);
                installKeyboard(button);
                buttons[row][column] = button;
                add(button);
            }
        }
        revalidate();
    }

    private void installMouse(CellButton button) {
        button.addMouseListener(mouseListener);
        button.addMouseMotionListener(mouseListener);
        button.addFocusListener(focusListener);
    }

    private MouseAdapter createMouseListener() {
        return new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent event) {
                CellButton button = (CellButton) event.getSource();
                button.hovered = true;
                button.repaint();
                actions.hover(new Position(button.row, button.column), inputEnabled && button.actionable);
            }
            @Override
            public void mouseExited(MouseEvent event) {
                CellButton button = (CellButton) event.getSource();
                button.hovered = false;
                button.repaint();
                actions.hover(null, false);
            }
            @Override
            public void mousePressed(MouseEvent event) {
                CellButton button = (CellButton) event.getSource();
                if (!inputEnabled || !isPrimaryButton(event.getButton())) return;
                int mask = buttonMask(event.getButton());
                int otherButton = event.getButton() == MouseEvent.BUTTON1
                        ? InputEvent.BUTTON3_DOWN_MASK : InputEvent.BUTTON1_DOWN_MASK;
                // If a release was lost outside the window, the next physical press
                // of that same button starts a fresh gesture instead of being eaten.
                if ((heldButtons & mask) != 0 && (event.getModifiersEx() & otherButton) == 0) cancelGesture();
                if (heldButtons != 0) {
                    boolean sameOrigin = pressedCell == button && (heldButtons & mask) == 0
                            && button.cell.revealed() && pressedVersion == renderedVersion;
                    heldButtons |= mask;
                    clearPressFeedback();
                    if (sameOrigin) {
                        chordGesture = true;
                        updatePressFeedback(true);
                    } else {
                        pressedCell = null;
                    }
                    return;
                }
                heldButtons = mask;
                boolean doubleLeft = event.getButton() == MouseEvent.BUTTON1
                        && event.getClickCount() > 1 && event.getClickCount() % 2 == 0;
                if (doubleLeft && !button.cell.revealed()) return;
                pressedCell = button;
                pressedVersion = renderedVersion;
                chordGesture = doubleLeft;
                updatePressFeedback(true);
                button.requestFocusInWindow();
            }
            @Override
            public void mouseReleased(MouseEvent event) {
                CellButton button = (CellButton) event.getSource();
                if (!isPrimaryButton(event.getButton())) return;
                CellButton origin = pressedCell;
                boolean valid = inputEnabled && origin != null && (heldButtons & buttonMask(event.getButton())) != 0
                        && pressedVersion == renderedVersion;
                Point release = origin == null ? event.getPoint()
                        : SwingUtilities.convertPoint(button, event.getPoint(), origin);
                valid &= origin != null && origin.contains(release);
                boolean chord = chordGesture;
                cancelGesture();
                if (valid) {
                    if (chord) actions.chord(origin.row, origin.column);
                    else activate(origin, event.getButton() == MouseEvent.BUTTON3);
                }
            }
            @Override
            public void mouseDragged(MouseEvent event) {
                if (pressedCell == null) return;
                CellButton button = (CellButton) event.getSource();
                Point point = SwingUtilities.convertPoint(button, event.getPoint(), pressedCell);
                updatePressFeedback(pressedCell.contains(point));
            }
        };
    }

    private void installKeyboard(CellButton button) {
        button.addActionListener(event -> activate(button, false));
        bind(button, "ENTER", () -> activate(button, false));
        bind(button, "F", () -> activate(button, true));
        bind(button, "LEFT", () -> focusCell(button.row, button.column - 1));
        bind(button, "RIGHT", () -> focusCell(button.row, button.column + 1));
        bind(button, "UP", () -> focusCell(button.row - 1, button.column));
        bind(button, "DOWN", () -> focusCell(button.row + 1, button.column));
    }

    private static void bind(CellButton button, String key, Runnable action) {
        button.getInputMap().put(KeyStroke.getKeyStroke(key), key);
        button.getActionMap().put(key, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) { action.run(); }
        });
    }

    private void focusCell(int row, int column) {
        if (row >= 0 && row < size && column >= 0 && column < size) {
            CellButton button = buttons[row][column];
            button.scrollRectToVisible(new java.awt.Rectangle(0, 0, button.getWidth(), button.getHeight()));
            button.requestFocusInWindow();
        }
    }

    private void activate(CellButton button, boolean rightClick) {
        if (!inputEnabled) return;
        if (!button.actionable) {
            if (!button.visible) actions.hint("先沿绿色边缘扩张：目标格需与已揭开区域相邻，包括斜角。");
            return;
        }
        if (rightClick) actions.flag(button.row, button.column);
        else actions.reveal(button.row, button.column);
    }

    public void cancelGesture() {
        clearPressFeedback();
        pressedCell = null;
        heldButtons = 0;
        chordGesture = false;
    }

    private void clearPressFeedback() {
        for (CellButton cell : pressedFeedback) {
            cell.pressed = false;
            cell.repaint();
        }
        pressedFeedback.clear();
        feedbackInside = false;
    }

    private void updatePressFeedback(boolean inside) {
        if (feedbackInside == inside) return;
        clearPressFeedback();
        if (!inside || pressedCell == null) return;
        feedbackInside = true;
        if (chordGesture) {
            for (int dr = -1; dr <= 1; dr++) {
                for (int dc = -1; dc <= 1; dc++) {
                    int row = pressedCell.row + dr;
                    int column = pressedCell.column + dc;
                    if (row < 0 || row >= size || column < 0 || column >= size || dr == 0 && dc == 0) continue;
                    CellButton neighbor = buttons[row][column];
                    if (!neighbor.visible) pressedFeedback.add(neighbor);
                }
            }
        } else if (pressedCell.actionable) {
            pressedFeedback.add(pressedCell);
        }
        for (CellButton cell : pressedFeedback) {
            cell.pressed = true;
            cell.repaint();
        }
    }

    private static int buttonMask(int button) { return button == MouseEvent.BUTTON1 ? 1 : 2; }

    public void render(GameSnapshot snapshot, boolean showAll) {
        if (snapshot.size() != size) rebuild(snapshot.size());
        boolean enabled = !showAll && snapshot.status() == GameStatus.PLAYING;
        // Time-only snapshots share the exact immutable cells list. Re-presenting
        // that board must not interrupt a press or rebuild 1,225 accessibility labels.
        if (renderedCells == snapshot.cells() && renderedShowAll == showAll && inputEnabled == enabled) return;
        renderedCells = snapshot.cells();
        renderedShowAll = showAll;
        renderedVersion++;
        cancelGesture();
        inputEnabled = enabled;
        for (Cell cell : snapshot.cells()) {
            int row = cell.position().row();
            int column = cell.position().column();
            CellButton button = buttons[row][column];
            boolean kingZone = row >= size - GameRules.KING_ZONE_SIZE && column >= size - GameRules.KING_ZONE_SIZE;
            boolean actionable = !cell.revealed() && hasRevealedNeighbor(snapshot, row, column);
            boolean visible = cell.revealed() || showAll;
            boolean interfered = cell.informerId() >= 0 && snapshot.informerZones().get(cell.informerId()).active();
            boolean changed = button.cell == null || !button.cell.equals(cell) || button.visible != visible
                    || button.actionable != actionable || button.interfered != interfered || button.getIcon() != null;
            button.cell = cell;
            button.kingZone = kingZone;
            button.visible = visible;
            button.interfered = interfered;
            button.actionable = actionable;
            if (button.isEnabled() != inputEnabled) button.setEnabled(inputEnabled);
            int cursor = inputEnabled && actionable ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR;
            if (button.getCursor().getType() != cursor) button.setCursor(Cursor.getPredefinedCursor(cursor));
            if (button.getIcon() != null) {
                button.setIcon(null);
                button.setDisabledIcon(null);
            }
            if (!changed) continue;
            String description = "第" + (row + 1) + "行，第" + (column + 1) + "列，"
                    + (visible ? "已揭开" : actionable ? "可操作" : "未解锁") + (kingZone ? "，国王区" : "");
            button.getAccessibleContext().setAccessibleName(description);
            button.getAccessibleContext().setAccessibleDescription(!cell.revealed() && cell.flagged() ? "地雷已扫除，尚未揭开；左键探索该格后才扩张控制区。" : cell.revealed()
                    ? interfered ? "内鬼数字仍受干扰，排除该区核心后才能辅助展开。"
                    : "同时按住左右键，或双击左键辅助展开；周围剩余雷数归零后可用。"
                    : actionable ? "左键探索，右键排雷。" : "先沿已揭开区域的边缘扩张。");
            if (changed) button.repaint();
        }
    }

    private static boolean hasRevealedNeighbor(GameSnapshot snapshot, int row, int column) {
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                int r = row + dr;
                int c = column + dc;
                if ((dr != 0 || dc != 0) && r >= 0 && r < snapshot.size() && c >= 0 && c < snapshot.size()
                        && snapshot.cellAt(r, c).revealed()) return true;
            }
        }
        return false;
    }

    public void setInputEnabled(boolean enabled) {
        if (inputEnabled == enabled) return;
        cancelGesture();
        inputEnabled = enabled;
        for (CellButton[] row : buttons) {
            for (CellButton button : row) {
                button.setEnabled(enabled);
                button.setCursor(Cursor.getPredefinedCursor(enabled && button.actionable ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            }
        }
    }

    public void showExplosion(List<Position> positions, int frame) {
        for (Position position : positions) {
            CellButton button = buttons[position.row()][position.column()];
            button.setIcon(IconFactory.explosion(frame));
            button.setDisabledIcon(IconFactory.explosion(frame));
            button.repaint();
        }
    }

    private static boolean isPrimaryButton(int button) {
        return button == MouseEvent.BUTTON1 || button == MouseEvent.BUTTON3;
    }

    private static final class CellButton extends JButton {
        private final int row;
        private final int column;
        private Cell cell;
        private boolean visible;
        private boolean kingZone;
        private boolean interfered;
        private boolean actionable;
        private boolean hovered;
        private boolean pressed;
        CellButton(int row, int column) {
            this.row = row;
            this.column = column;
            setOpaque(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setMargin(new Insets(0, 0, 0, 0));
            setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
            // 避免ButtonUI在同一次鼠标松开时再次触发ActionListener。
            for (var listener : getMouseListeners()) removeMouseListener(listener);
            for (var listener : getMouseMotionListeners()) removeMouseMotionListener(listener);
        }
        @Override
        protected void paintComponent(Graphics graphics) {
            if (cell == null) return;
            Graphics2D copy = (Graphics2D) graphics.create();
            UiTheme.smooth(copy);
            int width = getWidth() - 1;
            int height = getHeight() - 1;
            Color fill = visible ? new Color(244, 248, 246) : new Color(222, 231, 227);
            Color stroke = visible ? new Color(229, 236, 232) : new Color(213, 224, 218);
            if (kingZone) {
                fill = visible ? new Color(255, 249, 234) : new Color(247, 237, 210);
                stroke = new Color(224, 205, 159);
            } else if (visible && interfered) {
                fill = new Color(243, 236, 251);
                stroke = new Color(222, 207, 239);
            } else if (!visible && actionable) {
                fill = new Color(214, 238, 225);
                stroke = new Color(136, 186, 160);
            }
            if (hovered && isEnabled()) {
                fill = actionable ? kingZone ? new Color(250, 222, 148) : new Color(180, 222, 199)
                        : visible ? new Color(235, 242, 238) : new Color(211, 223, 216);
                stroke = actionable ? kingZone ? UiTheme.GOLD : UiTheme.ACCENT : new Color(176, 193, 184);
            }
            if (pressed) fill = kingZone ? new Color(235, 202, 120) : new Color(141, 199, 168);
            if (getIcon() != null) fill = new Color(255, 224, 178);
            copy.setColor(fill);
            copy.fillRoundRect(0, pressed ? 1 : 0, width, height, 7, 7);
            copy.setColor(stroke);
            copy.drawRoundRect(0, pressed ? 1 : 0, width, height - (pressed ? 1 : 0), 7, 7);
            if (!visible && actionable && !kingZone && !cell.flagged()) {
                copy.setColor(new Color(84, 153, 116, 110));
                copy.fillOval(getWidth() / 2 - 2, getHeight() / 2 - 2, 3, 3);
            }
            var icon = getIcon() != null ? getIcon() : visible && cell.hasMine() ? IconFactory.mine(cell.mineType()) : null;
            if (icon != null) {
                icon.paintIcon(this, copy, (getWidth() - icon.getIconWidth()) / 2,
                        (getHeight() - icon.getIconHeight()) / 2);
            } else if (!visible && cell.flagged()) {
                copy.setColor(UiTheme.ACCENT);
                copy.setStroke(new java.awt.BasicStroke(Math.max(1.5f, getHeight() * .07f)));
                copy.drawLine(getWidth() * 3 / 10, getHeight() / 2, getWidth() * 9 / 20, getHeight() * 13 / 20);
                copy.drawLine(getWidth() * 9 / 20, getHeight() * 13 / 20, getWidth() * 7 / 10, getHeight() * 7 / 20);
            } else if (visible && (cell.displayedNumber() != 0 || interfered)) {
                copy.setFont(getFont().deriveFont(Math.min(17f, getHeight() * 0.56f)));
                int number = cell.displayedNumber();
                copy.setColor(number == 0 ? UiTheme.PURPLE : NUMBER_COLORS[number - 1]);
                String text = Integer.toString(cell.displayedNumber());
                var metrics = copy.getFontMetrics();
                copy.drawString(text, (getWidth() - metrics.stringWidth(text)) / 2,
                        (getHeight() + metrics.getAscent() - metrics.getDescent()) / 2 + (pressed ? 1 : 0));
            }
            if (isFocusOwner()) {
                copy.setColor(UiTheme.ACCENT);
                copy.drawRoundRect(2, 2, width - 4, height - 4, 5, 5);
            }
            copy.dispose();
        }
    }
}
