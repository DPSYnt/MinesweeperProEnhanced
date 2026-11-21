package com.minesweeper.ui;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.MouseWheelListener;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import com.minesweeper.model.Position;

/** 大棋盘保留可点击的最小格子尺寸，支持滚动、Shift横向滚动和Ctrl滚轮缩放。 */
@SuppressWarnings("serial")
final class BoardViewport extends JScrollPane {
    private final AxisSurface surface;
    BoardViewport(BoardPanel board) {
        surface = new AxisSurface(board);
        setViewportView(surface);
        setBorder(null);
        setOpaque(false);
        getViewport().setOpaque(false);
        getVerticalScrollBar().setUnitIncrement(26);
        getHorizontalScrollBar().setUnitIncrement(26);
        MouseWheelListener[] defaults = getMouseWheelListeners();
        for (MouseWheelListener listener : defaults) removeMouseWheelListener(listener);
        addMouseWheelListener(event -> {
            if (event.isControlDown()) {
                zoom(-event.getWheelRotation() * 2);
                event.consume();
            } else if (event.isShiftDown()) {
                var bar = getHorizontalScrollBar();
                bar.setValue(bar.getValue() + event.getWheelRotation() * 52);
                event.consume();
            } else {
                for (MouseWheelListener listener : defaults) listener.mouseWheelMoved(event);
            }
        });
    }

    void renderSize(int size) { surface.renderSize(size); }
    void highlight(Position position) { surface.highlight(position); }
    void fit() { surface.automatic = true; surface.revalidate(); }
    void zoom(int delta) {
        int before = surface.currentCellSize();
        surface.automatic = false;
        surface.cellSize = Math.max(18, Math.min(42, before + delta));
        surface.revalidate();
    }

    private static final class AxisSurface extends JPanel implements Scrollable {
        private static final Font AXIS_FONT = UiTheme.font(10, false);
        private static final Font ACTIVE_AXIS_FONT = UiTheme.font(10, true);
        private final BoardPanel board;
        private final JPanel columns = new JPanel();
        private final JPanel rows = new JPanel();
        private JLabel[] columnLabels = new JLabel[0];
        private JLabel[] rowLabels = new JLabel[0];
        private int size;
        private int cellSize = 26;
        private boolean automatic = true;
        private Position highlighted;

        AxisSurface(BoardPanel board) {
            this.board = board;
            setOpaque(false);
            setLayout(null);
            columns.setOpaque(false);
            rows.setOpaque(false);
            add(columns);
            add(rows);
            add(board);
        }

        void renderSize(int size) {
            if (this.size == size) return;
            this.size = size;
            highlighted = null;
            automatic = true;
            columns.removeAll();
            rows.removeAll();
            columns.setLayout(new GridLayout(1, size, 2, 0));
            rows.setLayout(new GridLayout(size, 1, 0, 2));
            columnLabels = new JLabel[size];
            rowLabels = new JLabel[size];
            for (int i = 0; i < size; i++) {
                columnLabels[i] = axisLabel(i);
                rowLabels[i] = axisLabel(i);
                columns.add(columnLabels[i]);
                rows.add(rowLabels[i]);
            }
            revalidate();
        }

        int currentCellSize() {
            if (!automatic || !(getParent() instanceof JViewport viewport)) return cellSize;
            Dimension extent = viewport.getExtentSize();
            if (extent.width <= 0 || extent.height <= 0 || size == 0) return 26;
            return Math.max(18, Math.min(32, (Math.min(extent.width, extent.height) - 23 - (size - 1) * 2) / size));
        }

        @Override
        public Dimension getPreferredSize() {
            int side = size * currentCellSize() + Math.max(0, size - 1) * 2 + 23;
            return new Dimension(side, side);
        }

        @Override
        public void doLayout() {
            int side = size * currentCellSize() + Math.max(0, size - 1) * 2;
            int x = Math.max(23, (getWidth() - side + 23) / 2);
            int y = Math.max(23, (getHeight() - side + 23) / 2);
            board.setBounds(x, y, side, side);
            columns.setBounds(x, y - 22, side, 18);
            rows.setBounds(x - 23, y, 18, side);
        }

        void highlight(Position position) {
            if (java.util.Objects.equals(highlighted, position)) return;
            if (highlighted != null) {
                styleAxis(columnLabels[highlighted.column()], false);
                styleAxis(rowLabels[highlighted.row()], false);
            }
            highlighted = position;
            if (position != null) {
                styleAxis(columnLabels[position.column()], true);
                styleAxis(rowLabels[position.row()], true);
            }
        }

        private static void styleAxis(JLabel label, boolean active) {
            label.setForeground(active ? UiTheme.ACCENT : UiTheme.MUTED);
            label.setFont(active ? ACTIVE_AXIS_FONT : AXIS_FONT);
        }

        private static JLabel axisLabel(int value) {
            JLabel label = UiTheme.label(Integer.toString(value + 1), 10, UiTheme.MUTED, false);
            label.setFont(AXIS_FONT);
            label.setHorizontalAlignment(SwingConstants.CENTER);
            return label;
        }

        public Dimension getPreferredScrollableViewportSize() { return new Dimension(620, 620); }
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return currentCellSize() + 2; }
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return Math.max(26, (orientation == SwingConstants.VERTICAL ? visible.height : visible.width) - 26);
        }
        public boolean getScrollableTracksViewportWidth() {
            return getParent() instanceof JViewport viewport && getPreferredSize().width <= viewport.getWidth();
        }
        public boolean getScrollableTracksViewportHeight() {
            return getParent() instanceof JViewport viewport && getPreferredSize().height <= viewport.getHeight();
        }
    }
}
