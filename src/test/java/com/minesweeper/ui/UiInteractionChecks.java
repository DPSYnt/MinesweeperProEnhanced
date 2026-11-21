package com.minesweeper.ui;

import com.minesweeper.model.GameSnapshot;
import com.minesweeper.model.Difficulty;
import com.minesweeper.model.Cell;
import com.minesweeper.model.MineType;
import com.minesweeper.model.Position;
import com.minesweeper.service.GameEngine;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Random;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.AbstractButton;
import javax.swing.JTabbedPane;
import javax.swing.text.JTextComponent;
import javax.swing.SwingUtilities;

/** 验证真实Swing手势与布局；也可导出相同面板的PNG供视觉检查。 */
public final class UiInteractionChecks {
    private static int checks;
    private UiInteractionChecks() { }

    public static void run() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            UiTheme.install();
            gestures();
            chordGestures();
            layouts();
            viewport();
            menu();
            records();
            informerPresentation();
            interferedNumberRendering();
            rapidMouseInput();
        });
        System.out.println("PASS UI interaction assertions: " + checks);
    }

    private static void gestures() {
        int[] reveals = {0};
        int[] flags = {0};
        int[] hints = {0};
        BoardPanel panel = new BoardPanel(new BoardPanel.Actions() {
            public void reveal(int row, int column) { reveals[0]++; }
            public void flag(int row, int column) { flags[0]++; }
            public void hint(String message) { hints[0]++; }
        });
        GameSnapshot snapshot = new GameEngine(Difficulty.NORMAL, new Random(8), Clock.systemUTC()).snapshot();
        panel.render(snapshot, false);
        panel.setSize(638, 638);
        panel.doLayout();
        JButton button = (JButton) java.util.Arrays.stream(panel.getComponents())
                .filter(c -> c.getCursor().getType() == Cursor.HAND_CURSOR).findFirst().orElseThrow();
        press(button, MouseEvent.BUTTON1, 1);
        mouse(button, MouseEvent.MOUSE_DRAGGED, MouseEvent.NOBUTTON, 14, 12, 1);
        release(button, MouseEvent.BUTTON1, 14, 12, 1);
        check(reveals[0] == 1, "minor drag within same cell is accepted");
        mouse(button, MouseEvent.MOUSE_CLICKED, MouseEvent.BUTTON1, 14, 12, 1);
        check(reveals[0] == 1, "native clicked event cannot duplicate a release");
        press(button, MouseEvent.BUTTON3, 1);
        release(button, MouseEvent.BUTTON3, 10, 10, 1);
        check(flags[0] == 1 && reveals[0] == 1, "right click dispatches once and never reveals");
        press(button, MouseEvent.BUTTON1, 1);
        release(button, MouseEvent.BUTTON1, button.getWidth() + 2, 10, 1);
        check(reveals[0] == 1, "release outside source cell cancels");
        press(button, MouseEvent.BUTTON1, 1);
        release((JButton) panel.getComponent(0), MouseEvent.BUTTON1, 10, 10, 1);
        check(reveals[0] == 1, "release on neighbor cannot activate either cell");
        press(button, MouseEvent.BUTTON1, 1);
        press(button, MouseEvent.BUTTON3, 1);
        release(button, MouseEvent.BUTTON1, 10, 10, 1);
        release(button, MouseEvent.BUTTON3, 10, 10, 1);
        check(reveals[0] == 1 && flags[0] == 1, "combined buttons cancel the gesture");
        press(button, MouseEvent.BUTTON1, 2);
        release(button, MouseEvent.BUTTON1, 10, 10, 2);
        check(reveals[0] == 1, "second click of a double click is ignored");
        release(button, MouseEvent.BUTTON1, 10, 10, 1);
        check(reveals[0] == 1, "orphaned release ignored");
        press(button, MouseEvent.BUTTON1, 1);
        panel.render(snapshot, false);
        release(button, MouseEvent.BUTTON1, 10, 10, 1);
        check(reveals[0] == 2, "presenting the identical board preserves a pending press");
        reveals[0] = 1;
        press(button, MouseEvent.BUTTON1, 1);
        panel.render(new GameEngine(Difficulty.NORMAL, new Random(19), Clock.systemUTC()).snapshot(), false);
        release(button, MouseEvent.BUTTON1, 10, 10, 1);
        check(reveals[0] == 1, "a changed board invalidates the pending press");
        panel.render(snapshot, false);
        press(button, MouseEvent.BUTTON1, 1);
        panel.setInputEnabled(false);
        release(button, MouseEvent.BUTTON1, 10, 10, 1);
        check(reveals[0] == 1, "animation lock cancels pending press");
        panel.render(snapshot, false);
        press(button, MouseEvent.BUTTON1, 1);
        panel.cancelGesture();
        release(button, MouseEvent.BUTTON1, 10, 10, 1);
        check(reveals[0] == 1, "window focus cancellation prevents stale release");
        Cell blocked = snapshot.cells().stream().filter(c -> !c.revealed()
                && panel.getComponent(c.position().row() * snapshot.size() + c.position().column()).getCursor().getType()
                == Cursor.DEFAULT_CURSOR).findFirst().orElseThrow();
        JButton locked = (JButton) panel.getComponent(blocked.position().row() * snapshot.size() + blocked.position().column());
        press(locked, MouseEvent.BUTTON1, 1);
        release(locked, MouseEvent.BUTTON1, 10, 10, 1);
        check(reveals[0] == 1 && hints[0] == 1, "unreachable cells reject the gesture without a domain operation");
        button.doClick(0);
        check(reveals[0] == 2, "button keyboard action still reveals once");
        button.getActionMap().get("F").actionPerformed(new java.awt.event.ActionEvent(button, 0, "F"));
        check(flags[0] == 2, "F key flags once");
        panel.render(snapshot, true);
        press(button, MouseEvent.BUTTON1, 1);
        release(button, MouseEvent.BUTTON1, 10, 10, 1);
        check(reveals[0] == 2, "game-over map ignores presses");
    }

    private static void chordGestures() {
        int[] chords = {0};
        int[] singles = {0};
        GameSnapshot state = new GameEngine(Difficulty.NORMAL, new Random(17), Clock.systemUTC()).snapshot();
        BoardPanel board = new BoardPanel(new BoardPanel.Actions() {
            public void reveal(int row, int column) { singles[0]++; }
            public void flag(int row, int column) { singles[0]++; }
            public void chord(int row, int column) { chords[0]++; }
        });
        board.render(state, false);
        board.setSize(638, 638);
        board.doLayout();
        Cell origin = chordOrigin(state);
        JButton button = (JButton) board.getComponent(origin.position().row() * state.size() + origin.position().column());
        BufferedImage normal = paint(board);
        for (int firstPress : new int[]{MouseEvent.BUTTON1, MouseEvent.BUTTON3}) {
            for (int firstRelease : new int[]{MouseEvent.BUTTON1, MouseEvent.BUTTON3}) {
                int before = chords[0];
                press(button, firstPress, 1);
                press(button, firstPress == MouseEvent.BUTTON1 ? MouseEvent.BUTTON3 : MouseEvent.BUTTON1, 1);
                check(!samePixels(normal, paint(board)), "dual press previews neighboring unknown cells");
                release(button, firstRelease, 10, 10, 1);
                release(button, firstRelease == MouseEvent.BUTTON1 ? MouseEvent.BUTTON3 : MouseEvent.BUTTON1, 10, 10, 1);
                mouse(button, MouseEvent.MOUSE_CLICKED, MouseEvent.BUTTON1, 10, 10, 1);
                check(chords[0] == before + 1 && singles[0] == 0, "both press and release orders dispatch exactly one chord");
                check(samePixels(normal, paint(board)), "release clears all neighbor previews");
            }
        }
        int before = chords[0];
        press(button, MouseEvent.BUTTON1, 1);
        release(button, MouseEvent.BUTTON1, 10, 10, 1);
        press(button, MouseEvent.BUTTON1, 2);
        release(button, MouseEvent.BUTTON1, 10, 10, 2);
        check(chords[0] == before + 1 && singles[0] == 0, "left double click on revealed cell also chords once");
        for (int cancellation = 0; cancellation < 5; cancellation++) {
            before = chords[0];
            press(button, MouseEvent.BUTTON1, 1);
            press(button, MouseEvent.BUTTON3, 1);
            switch (cancellation) {
                case 0 -> mouse(button, MouseEvent.MOUSE_DRAGGED, MouseEvent.NOBUTTON, -2, 10, 1);
                case 1 -> board.render(new GameEngine(Difficulty.NORMAL, new Random(19), Clock.systemUTC()).snapshot(), false);
                case 2 -> board.cancelGesture();
                case 3 -> board.setInputEnabled(false);
                default -> press(button, MouseEvent.BUTTON1, 1);
            }
            release(button, MouseEvent.BUTTON1, cancellation == 0 ? -2 : 10, 10, 1);
            release(button, MouseEvent.BUTTON3, 10, 10, 1);
            check(chords[0] == before && singles[0] == 0, "drag, refresh, focus, animation and repeated presses cancel chords");
            board.render(state, false);
            check(samePixels(normal, paint(board)), "cancel clears all neighbor previews");
        }
        JButton other = (JButton) board.getComponent(0);
        before = chords[0];
        press(button, MouseEvent.BUTTON1, 1);
        press(other, MouseEvent.BUTTON3, 1);
        release(button, MouseEvent.BUTTON1, 10, 10, 1);
        release(other, MouseEvent.BUTTON3, 10, 10, 1);
        check(chords[0] == before && singles[0] == 0, "two different origins cannot form a chord");
    }

    private static boolean samePixels(BufferedImage first, BufferedImage second) {
        return java.util.Arrays.equals(first.getRGB(0, 0, first.getWidth(), first.getHeight(), null, 0, first.getWidth()),
                second.getRGB(0, 0, second.getWidth(), second.getHeight(), null, 0, second.getWidth()));
    }

    private static Cell chordOrigin(GameSnapshot state) {
        Cell best = null;
        int bestCount = 0;
        for (Cell cell : state.cells()) {
            if (!cell.revealed() || cell.displayedNumber() == 0) continue;
            int count = 0;
            for (int dr = -1; dr <= 1; dr++) for (int dc = -1; dc <= 1; dc++) {
                int row = cell.position().row() + dr;
                int column = cell.position().column() + dc;
                if (row >= 0 && row < state.size() && column >= 0 && column < state.size()
                        && !state.cellAt(row, column).revealed()) count++;
            }
            if (count > bestCount) {
                best = cell;
                bestCount = count;
            }
        }
        if (best == null) throw new AssertionError("missing numbered frontier for chord preview");
        return best;
    }

    private static void layouts() {
        GamePanel panel = previewPanel();
        for (Difficulty difficulty : Difficulty.values()) {
        panel.render(new GameEngine(difficulty, new Random(17), Clock.systemUTC()).snapshot());
        for (int[] size : new int[][]{{1100, 850}, {900, 650}, {1280, 900}}) {
            panel.setSize(size[0], size[1]);
            layoutTree(panel);
            layoutTree(panel);
            BoardPanel board = findBoard(panel);
            check(board.getComponentCount() == difficulty.boardSize() * difficulty.boardSize(), "board buttons rebuild for selected difficulty");
            check(board.getWidth() == board.getHeight(), "board remains square on resize");
            check(board.getX() >= 0 && board.getY() >= 0
                    && board.getX() + board.getWidth() <= board.getParent().getWidth()
                    && board.getY() + board.getHeight() <= board.getParent().getHeight(), "board fits container");
            for (Component cell : board.getComponents()) {
                check(cell.getWidth() == cell.getHeight(), "each cell remains square");
                check(cell.getWidth() >= 18, "cell targets remain usable at minimum window size");
            }
            javax.swing.JButton endTurn = findButton(panel, "结束回合   →");
            java.awt.Rectangle endBounds = SwingUtilities.convertRectangle(endTurn.getParent(), endTurn.getBounds(), panel);
            check(endBounds.y >= 0 && endBounds.y + endBounds.height <= panel.getHeight(),
                    "end turn remains visible in compact layout");
            JButton restart = findButton(panel, "重新开始");
            java.awt.Rectangle restartBounds = SwingUtilities.convertRectangle(restart.getParent(), restart.getBounds(), panel);
            check(restartBounds.height >= 30 && restartBounds.y >= endBounds.y + endBounds.height
                    && restartBounds.y + restartBounds.height <= panel.getHeight(), "dedicated restart remains visible below end turn");
            javax.swing.JLabel timer = (javax.swing.JLabel) findNamed(panel, "gameTimer");
            java.awt.Rectangle timerBounds = SwingUtilities.convertRectangle(timer.getParent(), timer.getBounds(), panel);
            check(timer.getFont().getSize() >= 24 && timerBounds.x > board.getX()
                    && timerBounds.y < endBounds.y && timerBounds.y >= 0 && timerBounds.y + timerBounds.height <= panel.getHeight(),
                    "large timer stays visible above the fixed action buttons");
            GameSnapshot unchanged = panel.snapshot();
            panel.updateTime(3661);
            check(timer.getText().equals("61:01") && panel.snapshot() == unchanged, "timer refresh changes only its visible text");
            javax.swing.JProgressBar progress = (javax.swing.JProgressBar) findNamed(panel, "territoryProgress");
            check(progress.getMaximum() == unchanged.cells().size() && progress.getValue() == unchanged.revealedCount()
                    && progress.getWidth() >= 160 && progress.getHeight() >= 8, "territory progress bar matches real controlled area and stays readable");
            java.awt.Rectangle progressBounds = SwingUtilities.convertRectangle(progress.getParent(), progress.getBounds(), panel);
            check(progressBounds.y > 0 && progressBounds.y + progressBounds.height < timerBounds.y,
                    "control progress stays visible above the fixed operation panel");
            BufferedImage meter = paint(progress);
            boolean hasFill = false;
            boolean hasTrack = false;
            for (int x = 0; x < meter.getWidth(); x++) {
                int pixel = meter.getRGB(x, meter.getHeight() / 2);
                hasFill |= pixel == progress.getForeground().getRGB();
                hasTrack |= pixel == progress.getBackground().getRGB();
            }
            check(hasFill && hasTrack, "progress bar paints both its controlled fraction and empty track");
            check(((java.awt.BorderLayout) panel.getLayout()).getLayoutComponent(java.awt.BorderLayout.SOUTH) == null,
                    "removed bottom-left live status has no remaining footer");
            noHoverTooltips(panel);
            BufferedImage image = paint(panel);
            check(image.getWidth() == size[0] && image.getHeight() == size[1], "full dashboard paints headlessly");
        }
        }
    }

    private static Component findNamed(Container root, String name) {
        for (Component component : root.getComponents()) {
            if (name.equals(component.getName())) return component;
            if (component instanceof Container child) {
                Component found = findNamed(child, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void noHoverTooltips(Container root) {
        for (Component component : root.getComponents()) {
            if (component instanceof javax.swing.JComponent child) check(child.getToolTipText() == null, "hover tooltips are removed from game controls");
            if (component instanceof Container child) noHoverTooltips(child);
        }
    }

    private static void menu() {
        Difficulty[] started = {null};
        int[] calls = new int[4];
        StartMenuPanel menu = new StartMenuPanel(d -> started[0] = d, () -> calls[0]++, () -> calls[1]++,
                () -> calls[2]++, () -> calls[3]++);
        check(!findButton(menu, "对局记录").isVisible() && !findButton(menu, "开始新游戏").isVisible(),
                "initial menu hides actions without a session");
        for (Difficulty difficulty : Difficulty.values()) {
            menu.selectDifficulty(difficulty);
            check(menu.selectedDifficulty() == difficulty, "difficulty selection updates menu");
            findButton(menu, "开始游戏   →").doClick(0);
            check(started[0] == difficulty, "start uses selected difficulty");
        }
        findButton(menu, "规则与操作").doClick(0);
        findButton(menu, "退出游戏").doClick(0);
        GameEngine active = new GameEngine(Difficulty.NORMAL, new Random(17), Clock.systemUTC());
        menu.setSession(active.snapshot());
        started[0] = null;
        findButton(menu, "继续当前对局   →").doClick(0);
        check(started[0] == null, "primary continuation does not restart with selected difficulty");
        findButton(menu, "开始新游戏").doClick(0);
        check(started[0] == Difficulty.NIGHTMARE, "explicit new game uses selected difficulty");
        findButton(menu, "对局记录").doClick(0);
        check(calls[0] == 1 && calls[1] == 1 && calls[2] == 1 && calls[3] == 1, "rules exit resume record callbacks");
        GameEngine finished = new GameEngine(Difficulty.NORMAL, new Random(17), Clock.systemUTC());
        for (int turn = 0; turn < 25 && finished.snapshot().status() == com.minesweeper.model.GameStatus.PLAYING; turn++) finished.endTurn();
        check(finished.snapshot().status() != com.minesweeper.model.GameStatus.PLAYING, "finished menu scenario");
        menu.setSession(finished.snapshot());
        check(findButton(menu, "开始游戏   →").isVisible() && !findButton(menu, "开始新游戏").isVisible()
                && findButton(menu, "对局记录").isVisible(), "finished game has records and a single start action");
        for (GameSnapshot session : new GameSnapshot[]{null, active.snapshot(), finished.snapshot()}) {
            menu.setSession(session);
            for (int[] size : new int[][]{{1100, 850}, {900, 650}}) {
                menu.setSize(size[0], size[1]);
                layoutTree(menu);
                layoutTree(menu);
                visibleButtons(menu, menu);
                paint(menu);
            }
        }
        RulesPanel rules = new RulesPanel();
        rules.setSize(900, 750);
        layoutTree(rules);
        paint(rules);
        for (Difficulty difficulty : Difficulty.values()) {
            check(RuleBook.difficultyHtml().contains("<b>" + difficulty.totalInitialMines() + "</b>"),
                    "rule table matches actual difficulty total");
        }
        int[] gameCalls = new int[5];
        GamePanel game = new GamePanel(new BoardPanel.Actions() {
            public void reveal(int row, int column) { }
            public void flag(int row, int column) { }
        }, () -> gameCalls[0]++, () -> gameCalls[4]++, () -> gameCalls[1]++, () -> gameCalls[2]++, () -> gameCalls[3]++);
        game.render(active.snapshot());
        findButton(game, "结束回合   →").doClick(0);
        findButton(game, "返回菜单").doClick(0);
        findButton(game, "规则与操作").doClick(0);
        findButton(game, "对局记录").doClick(0);
        check(java.util.Arrays.equals(gameCalls, new int[]{1, 1, 1, 1, 0}), "game navigation callbacks stay distinct");
        check(findButton(game, "玩法指南") == null && findButton(game, "开始菜单  ↗") == null,
                "retired duplicate labels have no entries");
        GameEngine winner = new GameEngine(Difficulty.NORMAL, new Random(17), Clock.systemUTC());
        for (int step = 0; step < winner.snapshot().cells().size() * 2
                && winner.snapshot().status() == com.minesweeper.model.GameStatus.PLAYING; step++) {
            GameSnapshot state = winner.snapshot();
            Cell next = state.cells().stream().filter(c -> !c.revealed() && c.mineType() != MineType.KING
                    && hasRevealedNeighbor(state, c.position()))
                    .min(java.util.Comparator.comparingInt(c -> c.hasMine() ? 0 : 1)).orElseThrow();
            check((next.hasMine() ? winner.flag(next.position().row(), next.position().column())
                    : winner.reveal(next.position().row(), next.position().column())).accepted(), "complete winning UI fixture through legal actions");
        }
        check(winner.snapshot().status() == com.minesweeper.model.GameStatus.WON
                && winner.snapshot().kingAlive() && winner.snapshot().remainingMineCount() == 1, "real winning board preserves the sole king for restart checks");
        for (GameSnapshot state : new GameSnapshot[]{active.snapshot(), finished.snapshot(), winner.snapshot()}) {
            game.render(state);
            JButton retry = findButton(game, "重新开始");
            JButton turn = findButton(game, "结束回合   →");
            check(retry != null && retry.isEnabled(), "restart available while playing, after loss and after victory");
            check(turn.isEnabled() == (state.status() == com.minesweeper.model.GameStatus.PLAYING),
                    "only playing board can end a turn");
            game.setInputEnabled(false);
            check(retry.isEnabled() && !turn.isEnabled(), "restart remains available during presentation lock");
            retry.doClick(0);
            game.setInputEnabled(true);
            check(turn.isEnabled() == (state.status() == com.minesweeper.model.GameStatus.PLAYING),
                    "unlock cannot enable end turn after game over");
        }
        check(gameCalls[4] == 3 && gameCalls[0] == 1, "restart callback is independent of ending a turn");
    }

    private static void records() {
        GameEngine engine = new GameEngine(Difficulty.NORMAL, new Random(17), Clock.systemUTC());
        int[] refreshed = {0};
        RecordsPanel[] holder = new RecordsPanel[1];
        RecordsPanel records = new RecordsPanel(() -> {
            refreshed[0]++;
            holder[0].render(engine.snapshot(), engine.history());
        });
        holder[0] = records;
        records.render(engine.snapshot(), engine.history());
        JTabbedPane tabs = null;
        for (Component component : records.getComponents()) if (component instanceof JTabbedPane pane) tabs = pane;
        check(tabs != null && tabs.getTabCount() == 3 && tabs.getTitleAt(0).equals("本局统计")
                && tabs.getTitleAt(1).equals("行动记录") && tabs.getTitleAt(2).equals("会话成就"),
                "records consolidate the three views in one panel");
        String first = documentText(records);
        check(first.contains("70颗") && first.contains("国王守护者") && first.contains("新游戏开始"),
                "records render actual totals, achievements and event history");
        engine.restart(Difficulty.HARD);
        tabs.setSelectedIndex(1);
        findButton(records, "刷新记录").doClick(0);
        check(refreshed[0] == 1 && tabs.getSelectedIndex() == 1, "refresh retains selected tab");
        String next = documentText(records);
        check(next.contains("160颗") && next.contains("625") && next.contains("难度: 困难"),
                "refresh replaces old session statistics and history");
        for (int[] size : new int[][]{{860, 720}, {700, 560}}) {
            records.setSize(size[0], size[1]);
            for (int tab = 0; tab < tabs.getTabCount(); tab++) {
                tabs.setSelectedIndex(tab);
                layoutTree(records);
                layoutTree(records);
                paint(records);
            }
            visibleButtons(records, records);
        }
    }

    private static void rapidMouseInput() {
        int[] reveals = {0}, flags = {0};
        GameSnapshot snapshot = new GameEngine(Difficulty.NIGHTMARE, new Random(17), Clock.systemUTC()).snapshot();
        BoardPanel board = new BoardPanel(new BoardPanel.Actions() {
            public void reveal(int row, int column) { reveals[0]++; }
            public void flag(int row, int column) { flags[0]++; }
        });
        board.render(snapshot, false);
        board.setSize(900, 900);
        board.doLayout();
        JButton target = (JButton) java.util.Arrays.stream(board.getComponents())
                .filter(c -> c.getCursor().getType() == Cursor.HAND_CURSOR).findFirst().orElseThrow();
        for (int count = 1; count <= 100; count++) {
            press(target, MouseEvent.BUTTON3, count);
            board.render(snapshot, false);
            release(target, MouseEvent.BUTTON3, 10, 10, count);
        }
        check(flags[0] == 100 && reveals[0] == 0, "rapid right clicks never become chords or lose second/third clicks");
        var statistics = snapshot.statistics();
        GameSnapshot timed = new GameSnapshot(snapshot.size(), snapshot.difficulty(), snapshot.status(), snapshot.phase(), snapshot.hp(),
                snapshot.turn(), snapshot.normalMineCount(), snapshot.revealedCount(), snapshot.kingPosition(), snapshot.cells(),
                snapshot.informerZones(), new com.minesweeper.model.GameStatistics(statistics.totalMoves(), statistics.correctFlags(),
                statistics.wrongFlags(), statistics.minesCleared(), statistics.lifetimeMinesCleared(), statistics.elapsedSeconds() + 1),
                snapshot.achievements(), snapshot.outcome());
        int[] propertyUpdates = {0};
        target.addPropertyChangeListener(event -> propertyUpdates[0]++);
        press(target, MouseEvent.BUTTON3, 1);
        board.render(timed, false);
        release(target, MouseEvent.BUTTON3, 10, 10, 1);
        check(flags[0] == 101 && propertyUpdates[0] == 0, "time-only snapshots preserve a press without rewriting tile properties");
        press(target, MouseEvent.BUTTON3, 1); // Simulate a lost mouseReleased.
        press(target, MouseEvent.BUTTON3, 1);
        release(target, MouseEvent.BUTTON3, 10, 10, 1);
        check(flags[0] == 102, "next right press recovers immediately after a lost release");
        press(target, MouseEvent.BUTTON1, 1);
        press(target, MouseEvent.BUTTON1, 1);
        release(target, MouseEvent.BUTTON1, 10, 10, 1);
        check(reveals[0] == 1, "next left press also recovers from a lost release");
        press(target, MouseEvent.BUTTON3, 1);
        board.setInputEnabled(true);
        release(target, MouseEvent.BUTTON3, 10, 10, 1);
        check(flags[0] == 103, "redundant enable calls cannot interrupt a gesture");
        JButton other = (JButton) board.getComponent(0);
        check(java.util.Arrays.stream(target.getMouseListeners()).anyMatch(listener ->
                        java.util.Arrays.asList(other.getMouseListeners()).contains(listener)),
                "large boards share one mouse listener instead of allocating listeners per tile");
    }

    private static String documentText(Container root) {
        StringBuilder result = new StringBuilder();
        for (Component component : root.getComponents()) {
            if (component instanceof JTextComponent text) {
                check(!text.isEditable(), "record documents are read-only");
                try { result.append(text.getDocument().getText(0, text.getDocument().getLength())); }
                catch (javax.swing.text.BadLocationException exception) { throw new AssertionError(exception); }
            }
            if (component instanceof Container child) result.append(documentText(child));
        }
        return result.toString();
    }

    private static void informerPresentation() {
        for (Difficulty difficulty : Difficulty.values()) {
            GameEngine engine = informerScenario(difficulty);
            GameSnapshot before = engine.snapshot();
            Cell sample = before.cells().stream().filter(c -> c.informerId() == 0 && c.revealed()).findFirst().orElseThrow();
            Position core = before.informerZones().get(0).core();
            GamePanel panel = previewPanel();
            panel.render(before);
            panel.setSize(1100, 850);
            layoutTree(panel);
            layoutTree(panel);
            BoardPanel board = findBoard(panel);
            JButton sampleButton = (JButton) board.getComponent(sample.position().row() * before.size() + sample.position().column());
            JButton ordinaryButton = (JButton) board.getComponent(0);
            check(sampleButton.getToolTipText() == null && sampleButton.getAccessibleContext().getAccessibleDescription().contains("干扰"),
                    "interference remains accessible without a hover tooltip");
            check(paint(sampleButton).getRGB(4, 4) != paint(ordinaryButton).getRGB(4, 4),
                    "active informer cell has its own visible interference tint");
            check(engine.flag(core.row(), core.column()).accepted(), "presentation scenario removes core legally");
            GameSnapshot after = engine.snapshot();
            panel.render(after);
            check(sampleButton.getToolTipText() == null && !sampleButton.getAccessibleContext().getAccessibleDescription().contains("干扰")
                    && sampleButton.getAccessibleContext().getAccessibleDescription().contains("辅助展开"),
                    "disabled region offers accessible chord guidance without a tooltip");
            check(paint(sampleButton).getRGB(4, 4) == paint(ordinaryButton).getRGB(4, 4),
                    "disabled region restores ordinary revealed-cell tint");
            for (int[] size : new int[][]{{1100, 850}, {900, 650}, {1280, 900}}) {
                panel.setSize(size[0], size[1]);
                layoutTree(panel);
                layoutTree(panel);
                checkClueLabelsFit(panel);
            }
            int remaining = after.informerZones().get(0).remainingMines();
            check(remaining > 0, "presentation scenario retains informer mines");
            InfoPanel info = new InfoPanel(() -> { }, () -> { });
            info.render(after);
            check(labelText(info).contains("第 " + (after.kingPosition().column() + 1) + " 列 · 区内余 " + remaining + " 雷"),
                    "sidebar unlocks moving coordinate while keeping remaining mine count");
            RecordsPanel records = new RecordsPanel(() -> { });
            records.render(after, engine.history());
            check(documentText(records).contains("干扰已解除 · 第" + after.turn() + "回合 · 剩余" + remaining + "颗"),
                    "records distinguish disabled interference from cleared zone");
        }
    }

    private static void interferedNumberRendering() {
        GameSnapshot initial = new GameEngine(Difficulty.NORMAL, new Random(17), Clock.systemUTC()).snapshot();
        Cell sample = initial.cells().stream().filter(c -> c.informerId() >= 0 && !c.hasMine()).findFirst().orElseThrow();
        BoardPanel board = new BoardPanel(new BoardPanel.Actions() {
            public void reveal(int row, int column) { }
            public void flag(int row, int column) { }
        });
        for (int number : new int[]{0, 8}) {
            java.util.List<Cell> cells = new java.util.ArrayList<>(initial.cells());
            int index = sample.position().row() * initial.size() + sample.position().column();
            cells.set(index, new Cell(sample.position(), sample.mineType(), sample.informerId(), true, false,
                    sample.adjacentMines(), number));
            board.render(new GameSnapshot(initial.size(), initial.difficulty(), initial.status(), initial.phase(), initial.hp(),
                    initial.turn(), initial.normalMineCount(), initial.revealedCount() + 1, initial.kingPosition(), cells,
                    initial.informerZones(), initial.statistics(), initial.achievements(), initial.outcome()), false);
            board.setSize(638, 638);
            board.doLayout();
            BufferedImage tile = paint((JButton) board.getComponent(index));
            boolean hasNumber = false;
            for (int x = tile.getWidth() / 4; x < tile.getWidth() * 3 / 4; x++) {
                for (int y = tile.getHeight() / 4; y < tile.getHeight() * 3 / 4; y++) {
                    java.awt.Color pixel = new java.awt.Color(tile.getRGB(x, y));
                    if (pixel.getRed() + pixel.getGreen() + pixel.getBlue() < 580) hasNumber = true;
                }
            }
            check(hasNumber, "interfered boundary number " + number + " is visible rather than a blank tile");
        }
    }

    private static String labelText(Container root) {
        StringBuilder result = new StringBuilder();
        for (Component component : root.getComponents()) {
            if (component instanceof javax.swing.JLabel label) result.append(label.getText());
            if (component instanceof Container child) result.append(labelText(child));
        }
        return result.toString();
    }

    private static void checkClueLabelsFit(Container root) {
        for (Component component : root.getComponents()) {
            if (component instanceof javax.swing.JLabel label
                    && (label.getText().contains("内鬼雷未排除") || label.getText().contains("区内余"))) {
                check(label.getWidth() >= label.getFontMetrics(label.getFont()).stringWidth(label.getText()),
                        "coordinate and remaining mine count fit without ellipsis: " + label.getText());
            }
            if (component instanceof Container child) checkClueLabelsFit(child);
        }
    }

    /** Build a real legal position with a visible noisy cell and an accessible core. */
    private static GameEngine informerScenario(Difficulty difficulty) {
        GameEngine engine = new GameEngine(difficulty, new Random(17), Clock.systemUTC());
        Position safe = engine.snapshot().cells().stream().filter(c -> c.informerId() == 0 && !c.hasMine())
                .findFirst().orElseThrow().position();
        approach(engine, safe);
        if (!engine.snapshot().cellAt(safe.row(), safe.column()).revealed()) {
            check(engine.reveal(safe.row(), safe.column()).accepted(), "reveal noisy sample cell legally");
        }
        approach(engine, engine.snapshot().informerZones().get(0).core());
        return engine;
    }

    private static void approach(GameEngine engine, Position target) {
        for (int step = 0; step < engine.snapshot().cells().size(); step++) {
            GameSnapshot state = engine.snapshot();
            if (state.cellAt(target.row(), target.column()).revealed() || hasRevealedNeighbor(state, target)) return;
            Cell next = state.cells().stream().filter(c -> !c.revealed() && c.mineType() != MineType.KING
                    && c.mineType() != MineType.INFORMER_CORE && hasRevealedNeighbor(state, c.position()))
                    .min(java.util.Comparator.comparingInt(c -> Math.max(Math.abs(c.position().row() - target.row()),
                            Math.abs(c.position().column() - target.column())))).orElseThrow();
            check((next.hasMine() ? engine.flag(next.position().row(), next.position().column())
                    : engine.reveal(next.position().row(), next.position().column())).accepted(), "presentation approach uses legal actions");
        }
        throw new AssertionError("presentation target is unreachable");
    }

    private static boolean hasRevealedNeighbor(GameSnapshot state, Position target) {
        return state.cells().stream().anyMatch(c -> c.revealed()
                && Math.max(Math.abs(c.position().row() - target.row()), Math.abs(c.position().column() - target.column())) == 1);
    }

    private static void viewport() {
        GamePanel panel = previewPanel();
        panel.render(new GameEngine(Difficulty.HARD, new Random(17), Clock.systemUTC()).snapshot());
        panel.setSize(900, 650);
        layoutTree(panel);
        layoutTree(panel);
        BoardPanel board = findBoard(panel);
        BoardViewport viewport = (BoardViewport) SwingUtilities.getAncestorOfClass(BoardViewport.class, board);
        axisHighlights(panel, board, viewport);
        int automaticSize = board.getComponent(0).getWidth();
        check(automaticSize >= 18, "automatic fit keeps usable cell size");
        findButton(panel, "+").doClick(0);
        layoutTree(panel);
        layoutTree(panel);
        check(board.getComponent(0).getWidth() == Math.min(42, automaticSize + 2), "zoom button increases usable cell size");
        for (int i = 0; i < 20; i++) viewport.zoom(2);
        layoutTree(panel);
        layoutTree(panel);
        check(board.getComponent(0).getWidth() == 42, "zoom has a sensible upper bound");
        check(viewport.getHorizontalScrollBar().isVisible() && viewport.getVerticalScrollBar().isVisible(),
                "zoomed board provides both scroll directions");
        viewport.dispatchEvent(new MouseWheelEvent(viewport, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(),
                MouseEvent.CTRL_DOWN_MASK, 10, 10, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, 1));
        layoutTree(panel);
        layoutTree(panel);
        check(board.getComponent(0).getWidth() == 40, "control wheel zooms the board");
        viewport.getHorizontalScrollBar().setValue(0);
        viewport.dispatchEvent(new MouseWheelEvent(viewport, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(),
                MouseEvent.SHIFT_DOWN_MASK, 10, 10, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, 1));
        check(viewport.getHorizontalScrollBar().getValue() > 0, "shift wheel scrolls horizontally");
        findButton(panel, "适应").doClick(0);
        layoutTree(panel);
        layoutTree(panel);
        check(board.getComponent(0).getWidth() == automaticSize, "fit restores automatic size for selected board");
        findButton(panel, "−").doClick(0);
        for (int i = 0; i < 20; i++) viewport.zoom(-2);
        layoutTree(panel);
        layoutTree(panel);
        check(board.getComponent(0).getWidth() == 18, "zoom never shrinks targets below 18 pixels");
    }

    private static void axisHighlights(GamePanel panel, BoardPanel board, BoardViewport viewport) {
        java.util.List<javax.swing.JLabel> labels = new java.util.ArrayList<>();
        for (Component component : ((Container) viewport.getViewport().getView()).getComponents()) {
            if (component instanceof Container group && !(component instanceof BoardPanel)) {
                for (Component child : group.getComponents()) {
                    if (child instanceof javax.swing.JLabel label) labels.add(label);
                }
            }
        }
        check(labels.size() == panel.snapshot().size() * 2, "axis labels exist for each row and column");
        for (int i = 0; i < labels.size(); i++) {
            check(labels.get(i).getText().equals(Integer.toString(i % panel.snapshot().size() + 1)),
                    "row and column labels count from one through board size");
        }
        JButton first = (JButton) board.getComponent(0);
        mouse(first, MouseEvent.MOUSE_ENTERED, MouseEvent.NOBUTTON, 10, 10, 0);
        check(!labelText(panel).contains("行 01   列 01") && first.getToolTipText() == null, "hover has no floating tooltip or live coordinate readout");
        check(first.getAccessibleContext().getAccessibleName().startsWith("第1行，第1列，"),
                "accessible first cell coordinates agree with axes");
        JButton last = (JButton) board.getComponent(board.getComponentCount() - 1);
        check(last.getAccessibleContext().getAccessibleName().startsWith("第" + panel.snapshot().size()
                + "行，第" + panel.snapshot().size() + "列，"), "accessible final cell uses board size for row and column");
        check(labels.stream().filter(l -> l.getForeground().equals(UiTheme.ACCENT) && l.getFont().isBold()).count() == 2,
                "hover highlights exactly one row and column");
        BufferedImage highlighted = paint(panel);
        mouse(first, MouseEvent.MOUSE_ENTERED, MouseEvent.NOBUTTON, 10, 10, 0);
        check(samePixels(highlighted, paint(panel)), "repeat hover leaves axis appearance stable");
        mouse(first, MouseEvent.MOUSE_EXITED, MouseEvent.NOBUTTON, 10, 10, 0);
        check(labels.stream().noneMatch(l -> l.getForeground().equals(UiTheme.ACCENT) || l.getFont().isBold()),
                "leaving the board clears old axis highlight");
        JButton second = (JButton) board.getComponent(panel.snapshot().size() + 2);
        mouse(second, MouseEvent.MOUSE_ENTERED, MouseEvent.NOBUTTON, 10, 10, 0);
        check(labels.stream().filter(l -> l.getForeground().equals(UiTheme.ACCENT) && l.getFont().isBold()).count() == 2,
                "moving hover replaces the two highlighted axis labels");
        panel.render(new GameEngine(Difficulty.HARD, new Random(18), Clock.systemUTC()).snapshot());
        check(labels.stream().noneMatch(l -> l.getForeground().equals(UiTheme.ACCENT) || l.getFont().isBold()),
                "same-size new game clears previous session axis highlight");
        mouse(second, MouseEvent.MOUSE_EXITED, MouseEvent.NOBUTTON, 10, 10, 0);
    }

    private static void visibleButtons(Container container, Container root) {
        for (Component component : container.getComponents()) {
            if (!component.isVisible()) continue;
            if (component instanceof AbstractButton button
                    && (button instanceof UiTheme.Button || button instanceof javax.swing.JToggleButton)) {
                java.awt.Rectangle bounds = SwingUtilities.convertRectangle(button.getParent(), button.getBounds(), root);
                check(bounds.width > 0 && bounds.height >= 30 && root.contains(bounds.x, bounds.y)
                        && root.contains(bounds.x + bounds.width - 1, bounds.y + bounds.height - 1),
                        "menu button remains visible: " + button.getText());
                for (Container ancestor = button.getParent(); ancestor != root; ancestor = ancestor.getParent()) {
                    java.awt.Rectangle local = SwingUtilities.convertRectangle(button.getParent(), button.getBounds(), ancestor);
                    check(ancestor.contains(local.x, local.y)
                            && ancestor.contains(local.x + local.width - 1, local.y + local.height - 1),
                            "menu button fits each ancestor: " + button.getText() + " " + local + " / "
                                    + ancestor.getSize() + " at " + root.getSize());
                }
            }
            if (component instanceof Container child) visibleButtons(child, root);
        }
    }

    /** 运行方式：java -cp target/classes;target/test-classes ...UiInteractionChecks target/ui-preview.png */
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args.length == 0 ? "target/ui-preview.png" : args[0]);
        Files.createDirectories(output.toAbsolutePath().getParent());
        SwingUtilities.invokeAndWait(() -> {
            UiTheme.install();
            GamePanel panel = previewPanel();
            try {
                panel.setSize(1100, 850);
                layoutTree(panel);
                layoutTree(panel);
                ImageIO.write(paint(panel), "png", output.toFile());
                panel.setSize(900, 650);
                layoutTree(panel);
                ImageIO.write(paint(panel), "png", output.resolveSibling("ui-preview-compact.png").toFile());
                panel.setSize(1100, 850);
                layoutTree(panel);
                BoardPanel board = findBoard(panel);
                JButton button = (JButton) java.util.Arrays.stream(board.getComponents())
                        .filter(c -> c.getCursor().getType() == Cursor.HAND_CURSOR).findFirst().orElseThrow();
                mouse(button, MouseEvent.MOUSE_ENTERED, MouseEvent.NOBUTTON, 10, 10, 0);
                press(button, MouseEvent.BUTTON3, 1);
                ImageIO.write(paint(panel), "png", output.resolveSibling("ui-preview-pressed.png").toFile());
                for (Difficulty difficulty : Difficulty.values()) {
                    panel.render(new GameEngine(difficulty, new Random(17), Clock.systemUTC()).snapshot());
                    layoutTree(panel);
                    layoutTree(panel);
                    ImageIO.write(paint(panel), "png", output.resolveSibling("game-" + difficulty.name().toLowerCase(java.util.Locale.ROOT) + ".png").toFile());
                }
                panel.render(new GameEngine(Difficulty.NORMAL, new Random(17), Clock.systemUTC()).snapshot());
                layoutTree(panel);
                layoutTree(panel);
                board = findBoard(panel);
                Cell chord = chordOrigin(panel.snapshot());
                JButton chordButton = (JButton) board.getComponent(chord.position().row() * panel.snapshot().size()
                        + chord.position().column());
                press(chordButton, MouseEvent.BUTTON1, 1);
                press(chordButton, MouseEvent.BUTTON3, 1);
                ImageIO.write(paint(panel), "png", output.resolveSibling("chord-preview.png").toFile());
                board.cancelGesture();
                GameEngine coreGame = informerScenario(Difficulty.NORMAL);
                panel.render(coreGame.snapshot());
                layoutTree(panel);
                layoutTree(panel);
                ImageIO.write(paint(panel), "png", output.resolveSibling("informer-core-before.png").toFile());
                Position core = coreGame.snapshot().informerZones().get(0).core();
                coreGame.flag(core.row(), core.column());
                panel.render(coreGame.snapshot());
                layoutTree(panel);
                layoutTree(panel);
                ImageIO.write(paint(panel), "png", output.resolveSibling("informer-core-after.png").toFile());
                RecordsPanel coreRecords = new RecordsPanel(() -> { });
                coreRecords.render(coreGame.snapshot(), coreGame.history());
                coreRecords.setSize(860, 720);
                layoutTree(coreRecords);
                layoutTree(coreRecords);
                ImageIO.write(paint(coreRecords), "png", output.resolveSibling("informer-core-records.png").toFile());
                StartMenuPanel menu = new StartMenuPanel(d -> { }, () -> { }, () -> { }, () -> { }, () -> { });
                menu.setSize(1100, 850);
                layoutTree(menu);
                ImageIO.write(paint(menu), "png", output.resolveSibling("start-menu.png").toFile());
                menu.setSession(panel.snapshot());
                menu.setSize(900, 650);
                layoutTree(menu);
                layoutTree(menu);
                ImageIO.write(paint(menu), "png", output.resolveSibling("start-menu-compact.png").toFile());
                RecordsPanel records = new RecordsPanel(() -> { });
                GameEngine recordGame = new GameEngine(Difficulty.NORMAL, new Random(17), Clock.systemUTC());
                records.render(recordGame.snapshot(), recordGame.history());
                records.setSize(860, 720);
                for (Component component : records.getComponents()) {
                    if (component instanceof JTabbedPane tabs) {
                        for (int i = 0; i < tabs.getTabCount(); i++) {
                            tabs.setSelectedIndex(i);
                            layoutTree(records);
                            layoutTree(records);
                            ImageIO.write(paint(records), "png", output.resolveSibling("records-tab-" + i + ".png").toFile());
                        }
                    }
                }
                RulesPanel rules = new RulesPanel();
                rules.setSize(900, 750);
                layoutTree(rules);
                ImageIO.write(paint(rules), "png", output.resolveSibling("rules-preview.png").toFile());
                for (Component component : rules.getComponents()) {
                    if (component instanceof JTabbedPane tabs) {
                        for (int i = 1; i < tabs.getTabCount(); i++) {
                            tabs.setSelectedIndex(i);
                            layoutTree(rules);
                            layoutTree(rules);
                            ImageIO.write(paint(rules), "png", output.resolveSibling("rules-tab-" + i + ".png").toFile());
                        }
                    }
                }
            } catch (IOException exception) {
                throw new java.io.UncheckedIOException(exception);
            }
        });
        System.out.println("Rendered UI previews: " + output.toAbsolutePath());
    }

    private static GamePanel previewPanel() {
        GamePanel panel = new GamePanel(new BoardPanel.Actions() {
            public void reveal(int row, int column) { }
            public void flag(int row, int column) { }
        }, () -> { }, () -> { }, () -> { }, () -> { }, () -> { });
        panel.render(new GameEngine(Difficulty.NORMAL, new Random(17), Clock.systemUTC()).snapshot());
        return panel;
    }

    private static BufferedImage paint(javax.swing.JComponent panel) {
        BufferedImage image = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        panel.printAll(graphics);
        graphics.dispose();
        return image;
    }

    private static void layoutTree(Container container) {
        container.doLayout();
        for (Component component : container.getComponents()) {
            if (component instanceof Container child) layoutTree(child);
        }
    }

    private static BoardPanel findBoard(Container container) {
        for (Component component : container.getComponents()) {
            if (component instanceof BoardPanel board) return board;
            if (component instanceof Container child) {
                BoardPanel found = findBoard(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static JButton findButton(Container container, String text) {
        for (Component component : container.getComponents()) {
            if (component instanceof JButton button && text.equals(button.getText())) return button;
            if (component instanceof Container child) {
                JButton result = findButton(child, text);
                if (result != null) return result;
            }
        }
        return null;
    }

    private static void press(JButton button, int mouseButton, int clickCount) {
        mouse(button, MouseEvent.MOUSE_PRESSED, mouseButton, 10, 10, clickCount);
    }
    private static void release(JButton button, int mouseButton, int x, int y, int clickCount) {
        mouse(button, MouseEvent.MOUSE_RELEASED, mouseButton, x, y, clickCount);
    }
    private static void mouse(JButton button, int type, int mouseButton, int x, int y, int clickCount) {
        int mask = type == MouseEvent.MOUSE_PRESSED ? mouseButton == MouseEvent.BUTTON1
                ? MouseEvent.BUTTON1_DOWN_MASK : MouseEvent.BUTTON3_DOWN_MASK : 0;
        button.dispatchEvent(new MouseEvent(button, type, System.currentTimeMillis(), mask,
                x, y, clickCount, false, mouseButton));
    }
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
