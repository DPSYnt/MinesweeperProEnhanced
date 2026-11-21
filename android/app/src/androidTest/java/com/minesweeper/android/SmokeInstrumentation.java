package com.minesweeper.android;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import com.minesweeper.model.Cell;
import com.minesweeper.model.GameSnapshot;
import com.minesweeper.model.MineType;
import com.minesweeper.model.Position;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayDeque;
import java.util.Arrays;

/** Separate emulator-only test APK; no test entry point is included in the delivered release APK. */
public final class SmokeInstrumentation extends Instrumentation {
    private int checks;
    private MainActivity activity;
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Intent launch = new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity = (MainActivity) startActivitySync(launch);
            waitForIdleSync();
            screenshot("android-menu.png");
            for (int choice : new int[]{MainActivity.NORMAL_ID, MainActivity.HARD_ID, MainActivity.HELL_ID, MainActivity.NIGHTMARE_ID}) {
                runOnMainSync(() -> {
                    activity.findViewById(choice).performClick();
                    activity.findViewById(MainActivity.START_ID).performClick();
                });
                waitForIdleSync();
                awaitBoard();
                GameSnapshot state = snapshot();
                int expected = choice == MainActivity.NORMAL_ID ? 18 : choice == MainActivity.HARD_ID ? 25 : choice == MainActivity.HELL_ID ? 30 : 35;
                check(state.size() == expected && state.remainingMineCount() == state.difficulty().totalInitialMines(), "difficulty and real mine total");
                check(state.informerZones().get(0).size() == (choice == MainActivity.NORMAL_ID ? 4 : choice == MainActivity.HARD_ID ? 6 : choice == MainActivity.HELL_ID ? 8 : 10), "informer size");
                runOnMainSync(() -> check(activity.findViewById(MainActivity.RESTART_ID).isShown()
                        && activity.findViewById(MainActivity.RESTART_ID).isEnabled(), "dedicated restart exists on each board"));
                screenshot("android-game-" + expected + ".png");
                if (choice == MainActivity.NORMAL_ID) {
                    Cell frontier = state.cells().stream().filter(c -> !c.revealed() && !c.hasMine() && canAct(state, c.position())).findFirst().orElseThrow();
                    tapCell(frontier.position());
                    check(snapshot().cellAt(frontier.position().row(), frontier.position().column()).revealed(), "real touch explores a safe frontier cell");
                    GameSnapshot beforeMenu = snapshot();
                    runOnMainSync(() -> activity.findViewById(MainActivity.MENU_ID).performClick());
                    runOnMainSync(() -> activity.findViewById(MainActivity.RESUME_ID).performClick());
                    awaitBoard();
                    check(snapshot().cells().equals(beforeMenu.cells()), "menu and resume preserve board");
                }
                if (choice == MainActivity.HARD_ID) {
                    Position core = state.informerZones().get(0).core();
                    runOnMainSync(() -> approach(core));
                    GameSnapshot before = snapshot();
                    runOnMainSync(() -> activity.engineForTest().flag(core.row(), core.column()));
                    GameSnapshot after = snapshot();
                    check(after.revealedCount() == before.revealedCount(), "core removal never expands territory");
                    check(!after.informerZones().get(0).active() && after.informerZones().get(0).remainingMines() > 0, "removing core stops interference with mines remaining");
                    for (Cell cell : before.cells()) {
                        if (cell.position().equals(core)) continue;
                        Cell remaining = after.cellAt(cell.position().row(), cell.position().column());
                        check(cell.mineType() == remaining.mineType() && cell.revealed() == remaining.revealed(), "remaining cells unchanged");
                    }
                }
                runOnMainSync(() -> activity.findViewById(MainActivity.MENU_ID).performClick());
            }
            result.putString("stream", "PASS Android native menu, 4 difficulty boards, actual touch, resume and core invariants: " + checks + " assertions\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable exception) {
            result.putString("stream", "FAIL: " + android.util.Log.getStackTraceString(exception));
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private GameSnapshot snapshot() {
        GameSnapshot[] state = new GameSnapshot[1];
        runOnMainSync(() -> state[0] = activity.engineForTest().snapshot());
        return state[0];
    }
    private void tapCell(Position target) {
        awaitBoard();
        View board = activity.findViewById(MainActivity.BOARD_ID);
        int[] location = new int[2];
        runOnMainSync(() -> board.getLocationOnScreen(location));
        GameSnapshot state = snapshot();
        float density = activity.getResources().getDisplayMetrics().density;
        float gutter = 24 * density;
        float cellSize = Math.max(14 * density, Math.min(42 * density,
                Math.min(board.getWidth() - gutter, board.getHeight() - gutter) / state.size()));
        float x = location[0] + gutter + Math.max(0, (board.getWidth() - gutter - state.size() * cellSize) / 2)
                + (target.column() + .5f) * cellSize;
        float y = location[1] + gutter + Math.max(0, (board.getHeight() - gutter - state.size() * cellSize) / 2)
                + (target.row() + .5f) * cellSize;
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(now, now + 70, MotionEvent.ACTION_UP, x, y, 0);
        sendPointerSync(down); sendPointerSync(up); down.recycle(); up.recycle();
        SystemClock.sleep(500); waitForIdleSync();
    }
    private void awaitBoard() {
        for (int attempt = 0; attempt < 30; attempt++) {
            boolean[] ready = new boolean[1];
            runOnMainSync(() -> {
                View board = activity.findViewById(MainActivity.BOARD_ID);
                ready[0] = board != null && board.getWidth() > 0 && board.getHeight() > 0 && board.hasWindowFocus();
            });
            if (ready[0]) { SystemClock.sleep(150); return; }
            SystemClock.sleep(200);
            waitForIdleSync();
        }
        throw new AssertionError("Board did not finish layout or gain window focus");
    }
    private void approach(Position target) {
        GameSnapshot state = activity.engineForTest().snapshot();
        int size = state.size();
        int[] parent = new int[size * size];
        Arrays.fill(parent, -2);
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (Cell cell : state.cells()) if (cell.revealed()) {
            int i = cell.position().row() * size + cell.position().column(); parent[i] = -1; queue.add(i);
        }
        int destination = target.row() * size + target.column();
        while (!queue.isEmpty() && parent[destination] == -2) {
            int current = queue.removeFirst();
            int row = current / size, column = current % size;
            for (int r = Math.max(0, row - 1); r <= Math.min(size - 1, row + 1); r++) {
                for (int c = Math.max(0, column - 1); c <= Math.min(size - 1, column + 1); c++) {
                    int next = r * size + c;
                    Cell cell = state.cellAt(r, c);
                    if (parent[next] != -2 || next != destination && (cell.mineType() == MineType.KING || cell.mineType() == MineType.INFORMER_CORE)) continue;
                    parent[next] = current; queue.add(next);
                }
            }
        }
        check(parent[destination] != -2, "core reachable");
        ArrayDeque<Integer> path = new ArrayDeque<>();
        for (int step = parent[destination]; step >= 0 && parent[step] != -1; step = parent[step]) path.addFirst(step);
        for (int step : path) {
            Cell cell = activity.engineForTest().snapshot().cellAt(step / size, step % size);
            if (cell.revealed()) continue;
            check((cell.hasMine() ? activity.engineForTest().flag(step / size, step % size)
                    : activity.engineForTest().reveal(step / size, step % size)).accepted(), "legal core approach");
            if (!activity.engineForTest().snapshot().cellAt(step / size, step % size).revealed()) {
                check(activity.engineForTest().reveal(step / size, step % size).accepted(), "explore cleared informer on core route");
            }
        }
    }
    private static boolean canAct(GameSnapshot state, Position p) {
        for (int r = Math.max(0, p.row() - 1); r <= Math.min(state.size() - 1, p.row() + 1); r++) {
            for (int c = Math.max(0, p.column() - 1); c <= Math.min(state.size() - 1, p.column() + 1); c++) if (state.cellAt(r, c).revealed()) return true;
        }
        return false;
    }
    private void screenshot(String name) throws Exception {
        waitForIdleSync();
        Bitmap image = getUiAutomation().takeScreenshot();
        if (image == null) throw new AssertionError("Screenshot unavailable");
        try (FileOutputStream output = new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null), name))) {
            image.compress(Bitmap.CompressFormat.PNG, 100, output);
        }
        image.recycle();
    }
    private void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
