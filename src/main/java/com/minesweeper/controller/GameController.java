package com.minesweeper.controller;

import com.minesweeper.model.ActionResult;
import com.minesweeper.model.Difficulty;
import com.minesweeper.model.GameSnapshot;
import com.minesweeper.service.GameEngine;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** 串行调度操作，负责动画期间的输入保护以及重开后的旧回调失效。 */
public final class GameController {
    private final GameEngine engine;
    private final GameView view;
    private boolean busy;
    private long revision;

    public GameController(GameEngine engine, GameView view) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.view = Objects.requireNonNull(view, "view");
    }

    public void start() { view.render(engine.snapshot()); }
    public void reveal(int row, int column) { perform(() -> engine.reveal(row, column)); }
    public void flag(int row, int column) { perform(() -> engine.flag(row, column)); }
    public void chord(int row, int column) { perform(() -> engine.chord(row, column)); }
    public void endTurn() { perform(engine::endTurn); }
    public GameSnapshot snapshot() { return engine.snapshot(); }
    public long elapsedSeconds() { return engine.elapsedSeconds(); }
    public List<String> history() { return engine.history(); }

    public void cancelPendingPresentation() {
        revision++;
        view.cancelAnimation();
        busy = false;
    }

    public void restart(Difficulty difficulty) {
        revision++;
        view.cancelAnimation();
        engine.restart(difficulty);
        busy = false;
        view.render(engine.snapshot());
    }

    private void perform(Supplier<ActionResult> operation) {
        if (busy) return;
        busy = true;
        long expectedRevision = ++revision;
        try {
            ActionResult result = operation.get();
            Runnable completed = new Runnable() {
                private boolean delivered;
                @Override
                public void run() {
                    if (delivered || revision != expectedRevision) return;
                    delivered = true;
                    try {
                        var snapshot = engine.snapshot();
                        if (result.accepted()) view.render(snapshot);
                        if (revision == expectedRevision) view.showResult(result, snapshot);
                    } finally {
                        if (revision == expectedRevision) busy = false;
                    }
                }
            };
            if (result.explosions().isEmpty()) completed.run();
            else view.animateExplosions(result.explosions(), completed);
        } catch (RuntimeException exception) {
            if (revision == expectedRevision) busy = false;
            throw exception;
        }
    }
}
