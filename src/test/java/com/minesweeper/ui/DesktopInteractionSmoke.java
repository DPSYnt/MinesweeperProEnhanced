package com.minesweeper.ui;

import com.minesweeper.model.Cell;
import com.minesweeper.model.Difficulty;
import com.minesweeper.model.GameSnapshot;
import com.minesweeper.model.MineType;
import java.awt.Component;
import java.awt.Container;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.SwingUtilities;

/** 可选桌面冒烟测试：用真实鼠标事件检查焦点与按压流程，完成后关闭测试窗口。 */
public final class DesktopInteractionSmoke {
    private DesktopInteractionSmoke() { }

    public static void main(String[] args) throws Exception {
        AtomicReference<MinesweeperFrame> frameRef = new AtomicReference<>();
        Point previousPointer = MouseInfo.getPointerInfo().getLocation();
        Robot robot = new Robot();
        robot.setAutoDelay(45);
        try {
            SwingUtilities.invokeAndWait(() -> {
                UiTheme.install();
                MinesweeperFrame frame = new MinesweeperFrame(Difficulty.NORMAL);
                frameRef.set(frame);
                frame.setVisible(true);
                frame.toFront();
                frame.requestFocus();
            });
            robot.waitForIdle();
            awaitFocus(robot, frameRef.get());
            GameSnapshot before = snapshot(frameRef.get());
            Cell safe = before.cells().stream().filter(c -> !c.revealed() && !c.hasMine()
                    && actionable(before, c)).findFirst().orElseThrow();
            Point target = center(frameRef.get(), safe);
            System.out.println("Native target: " + target + ", cell " + safe.position());
            requireFocus(frameRef.get());
            robot.mouseMove(target.x, target.y);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseMove(target.x + 5, target.y + 3);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.waitForIdle();
            GameSnapshot afterLeft = snapshot(frameRef.get());
            System.out.println("Native move counts: " + before.statistics().totalMoves() + " -> " + afterLeft.statistics().totalMoves());
            check(afterLeft.statistics().totalMoves() == before.statistics().totalMoves() + 1,
                    "native left press-drag-release must execute exactly once");
            Cell mine = afterLeft.cells().stream().filter(c -> !c.revealed() && c.mineType() == MineType.NORMAL
                    && actionable(afterLeft, c)).findFirst().orElseThrow();
            Point mineTarget = center(frameRef.get(), mine);
            requireFocus(frameRef.get());
            robot.mouseMove(mineTarget.x, mineTarget.y);
            robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
            robot.waitForIdle();
            GameSnapshot afterRight = snapshot(frameRef.get());
            check(afterRight.statistics().correctFlags() == afterLeft.statistics().correctFlags() + 1
                    && afterRight.hp() == afterLeft.hp() + 1, "native right click must flag once and recover HP");

            Path output = Path.of("target/ui-desktop.png");
            Files.createDirectories(output.getParent());
            AtomicReference<BufferedImage> image = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> {
                GamePanel panel = findGamePanel(frameRef.get().getContentPane());
                BufferedImage buffer = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = buffer.createGraphics();
                panel.printAll(graphics);
                graphics.dispose();
                image.set(buffer);
            });
            ImageIO.write(image.get(), "png", output.toFile());
            System.out.println("PASS desktop mouse smoke test: left drag, right flag, native focus");
            System.out.println("Desktop screenshot: " + output.toAbsolutePath());
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frameRef.get() != null) frameRef.get().dispose();
            });
            robot.mouseMove(previousPointer.x, previousPointer.y);
        }
    }

    private static GameSnapshot snapshot(MinesweeperFrame frame) throws Exception {
        AtomicReference<GameSnapshot> state = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> state.set(findGamePanel(frame.getContentPane()).snapshot()));
        return state.get();
    }

    private static void awaitFocus(Robot robot, MinesweeperFrame frame) throws Exception {
        java.util.concurrent.atomic.AtomicBoolean focused = new java.util.concurrent.atomic.AtomicBoolean();
        for (int attempt = 0; attempt < 20; attempt++) {
            SwingUtilities.invokeAndWait(() -> focused.set(frame.isFocused()));
            if (focused.get()) {
                robot.delay(100);
                return;
            }
            robot.delay(100);
        }
        throw new AssertionError("Desktop test window did not acquire foreground focus");
    }

    private static void requireFocus(MinesweeperFrame frame) throws Exception {
        java.util.concurrent.atomic.AtomicBoolean focused = new java.util.concurrent.atomic.AtomicBoolean();
        SwingUtilities.invokeAndWait(() -> focused.set(frame.isFocused()));
        check(focused.get(), "Desktop test lost foreground focus; stop before sending mouse input");
    }

    private static Point center(MinesweeperFrame frame, Cell cell) throws Exception {
        AtomicReference<Point> point = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            BoardPanel board = findBoard(frame.getContentPane());
            JButton button = (JButton) board.getComponent(cell.position().row() * findGamePanel(frame.getContentPane()).snapshot().size() + cell.position().column());
            Point location = button.getLocationOnScreen();
            point.set(new Point(location.x + button.getWidth() / 2, location.y + button.getHeight() / 2));
        });
        return point.get();
    }

    private static BoardPanel findBoard(Container container) {
        for (Component component : container.getComponents()) {
            if (component instanceof BoardPanel board) return board;
            if (component instanceof Container child) {
                BoardPanel result = findBoard(child);
                if (result != null) return result;
            }
        }
        return null;
    }

    private static GamePanel findGamePanel(Container container) {
        if (container instanceof GamePanel panel) return panel;
        for (Component component : container.getComponents()) {
            if (component instanceof Container child) {
                GamePanel panel = findGamePanel(child);
                if (panel != null) return panel;
            }
        }
        return null;
    }

    private static boolean actionable(GameSnapshot state, Cell cell) {
        if (cell.revealed()) return false;
        for (int dr = -1; dr <= 1; dr++) for (int dc = -1; dc <= 1; dc++) {
            int row = cell.position().row() + dr;
            int column = cell.position().column() + dc;
            if ((dr != 0 || dc != 0) && row >= 0 && row < state.size() && column >= 0 && column < state.size()
                    && state.cellAt(row, column).revealed()) return true;
        }
        return false;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
