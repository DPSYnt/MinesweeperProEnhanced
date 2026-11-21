package com.minesweeper.ui;

import com.minesweeper.controller.GameController;
import com.minesweeper.controller.GameView;
import com.minesweeper.model.*;
import com.minesweeper.service.GameEngine;
import java.awt.Dimension;
import java.awt.CardLayout;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.Timer;

/** 窗口管理与动画生命周期，终局通过结果对话框展示。 */
@SuppressWarnings("serial")
public final class MinesweeperFrame extends JFrame implements GameView {
    private final GameController controller;
    private final GamePanel gamePanel;
    private final StartMenuPanel startMenu;
    private final GameDialogs dialogs;
    private final CardLayout pageLayout = new CardLayout();
    private final JPanel pages = new JPanel(pageLayout);
    private boolean gameStarted;
    private boolean gameVisible;
    private final Timer clockTimer;
    private Timer explosionTimer;
    private int explosionFrame;

    public MinesweeperFrame() {
        super("MinesweeperEX · 动态扫雷");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        controller = new GameController(new GameEngine(Difficulty.NORMAL), this);
        dialogs = new GameDialogs(this, controller::snapshot, controller::history);
        gamePanel = new GamePanel(new BoardPanel.Actions() {
            public void reveal(int row, int column) { controller.reveal(row, column); }
            public void flag(int row, int column) { controller.flag(row, column); }
            public void chord(int row, int column) { controller.chord(row, column); }
        }, controller::endTurn, this::restartCurrentGame, this::chooseNewGame, dialogs::showRules, this::showRecords);
        startMenu = new StartMenuPanel(this::startGame, dialogs::showRules, this::dispose, this::resumeGame, this::showRecords);
        pages.add(startMenu, "menu");
        pages.add(gamePanel, "game");
        setContentPane(pages);
        shortcut("control ENTER", "endTurn", () -> { if (gameVisible) controller.endTurn(); });
        shortcut("control N", "newGame", this::chooseNewGame);
        shortcut("F1", "help", dialogs::showRules);
        shortcut("control H", "records", this::showRecords);
        addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowLostFocus(WindowEvent event) { gamePanel.cancelGesture(); }
        });
        clockTimer = new Timer(1000, event -> gamePanel.updateTime(controller.elapsedSeconds()));
        pack();
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setSize(Math.min(getWidth(), screen.width), Math.min(getHeight(), screen.height));
        setMinimumSize(new Dimension(Math.min(900, screen.width), Math.min(700, screen.height)));
        setLocationRelativeTo(null);
        pageLayout.show(pages, "menu");
    }

    public MinesweeperFrame(Difficulty difficulty) {
        this();
        startGame(difficulty);
    }

    public static void launch() {
        UiTheme.install();
        new MinesweeperFrame().setVisible(true);
    }

    @Override
    public void render(GameSnapshot state) {
        gamePanel.render(state);
        dialogs.refreshVisibleRecords();
    }

    @Override
    public void animateExplosions(List<Position> positions, Runnable onFinished) {
        cancelAnimation();
        gamePanel.setInputEnabled(false);
        explosionFrame = 0;
        gamePanel.showExplosion(positions, explosionFrame++);
        explosionTimer = new Timer(24, event -> {
            if (explosionFrame < IconFactory.EXPLOSION_FRAMES) {
                gamePanel.showExplosion(positions, explosionFrame++);
            } else {
                cancelAnimation();
                onFinished.run();
            }
        });
        explosionTimer.start();
    }

    @Override
    public void cancelAnimation() {
        if (explosionTimer != null) {
            explosionTimer.stop();
            explosionTimer = null;
        }
    }

    @Override
    public void showResult(ActionResult result, GameSnapshot state) {
        if (!result.accepted()) return;
        if (state.status() == GameStatus.PLAYING) return;
        Object[] options = {"重新开始", "重新选择难度", "查看终局棋盘"};
        int choice = JOptionPane.showOptionDialog(this,
                state.outcome() + "\n\n" + GameDialogs.resultSummary(state),
                state.status() == GameStatus.WON ? "恭喜胜利" : "游戏结束",
                JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, options, options[0]);
        switch (choice) {
            case 0 -> startGame(state.difficulty());
            case 1 -> chooseNewGame();
            default -> { }
        }
    }

    private void showRecords() {
        if (gameStarted) dialogs.showRecords();
    }

    private void restartCurrentGame() {
        if (!gameStarted) return;
        gamePanel.cancelGesture();
        controller.cancelPendingPresentation();
        render(controller.snapshot());
        if (controller.snapshot().status() == GameStatus.PLAYING
                && JOptionPane.showConfirmDialog(this, "将按当前难度生成新棋盘，本局进度会被替换。",
                "重新开始？", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE) != JOptionPane.OK_OPTION) return;
        startGame(controller.snapshot().difficulty());
    }

    private void chooseNewGame() {
        gamePanel.cancelGesture();
        controller.cancelPendingPresentation();
        clockTimer.stop();
        gameVisible = false;
        startMenu.setSession(gameStarted ? controller.snapshot() : null);
        if (gameStarted) startMenu.selectDifficulty(controller.snapshot().difficulty());
        pageLayout.show(pages, "menu");
    }

    private void startGame(Difficulty difficulty) {
        gameStarted = true;
        gameVisible = true;
        controller.restart(difficulty);
        pageLayout.show(pages, "game");
        clockTimer.start();
    }

    private void resumeGame() {
        if (!gameStarted) return;
        gameVisible = true;
        render(controller.snapshot());
        pageLayout.show(pages, "game");
        clockTimer.start();
    }

    private void shortcut(String key, String name, Runnable action) {
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key), name);
        getRootPane().getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) { action.run(); }
        });
    }

    @Override
    public void dispose() {
        cancelAnimation();
        if (clockTimer != null) clockTimer.stop();
        for (java.awt.Window window : getOwnedWindows()) window.dispose();
        super.dispose();
    }
}
