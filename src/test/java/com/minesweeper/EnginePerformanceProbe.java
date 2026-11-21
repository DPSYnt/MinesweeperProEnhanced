package com.minesweeper;

import com.minesweeper.model.ActionResult;
import com.minesweeper.model.Achievement;
import com.minesweeper.model.Cell;
import com.minesweeper.model.Difficulty;
import com.minesweeper.model.GameSnapshot;
import com.minesweeper.model.GameStatus;
import com.minesweeper.model.MineType;
import com.minesweeper.service.GameEngine;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** Optional reproducible behavior fingerprint and local timing probe; no timing assertions. */
public final class EnginePerformanceProbe {
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
    private static volatile Object sink;
    private EnginePerformanceProbe() { }

    public static void main(String[] args) throws NoSuchAlgorithmException {
        System.out.println("Behavior SHA-256: " + fingerprint());
        if (args.length > 0 && args[0].equals("--fingerprint")) return;
        for (Difficulty difficulty : Difficulty.values()) {
            GameEngine engine = new GameEngine(difficulty, new Random(17), FIXED_CLOCK);
            readSnapshots(engine, 10_000);
            long[] samples = new long[5];
            for (int sample = 0; sample < samples.length; sample++) {
                long started = System.nanoTime();
                readSnapshots(engine, 30_000);
                samples[sample] = System.nanoTime() - started;
            }
            Arrays.sort(samples);
            System.out.printf(Locale.ROOT, "%s: 30,000 snapshot reads, median %.3f ms%n",
                    difficulty.name(), samples[samples.length / 2] / 1_000_000.0);
        }
    }

    static String fingerprint() throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (Difficulty difficulty : Difficulty.values()) {
            for (int seed = 0; seed < 30; seed++) {
                GameEngine engine = new GameEngine(difficulty, new Random(seed), FIXED_CLOCK);
                Random choices = new Random(seed + 400L);
                record(digest, engine);
                for (int move = 0; move < 120 && engine.snapshot().status() == GameStatus.PLAYING; move++) {
                    GameSnapshot state = engine.snapshot();
                    List<Cell> candidates = new ArrayList<>();
                    for (Cell cell : state.cells()) {
                        if (!cell.revealed() && adjacentToRevealed(state, cell)) candidates.add(cell);
                    }
                    if (candidates.isEmpty()) break;
                    Cell target = candidates.get(choices.nextInt(candidates.size()));
                    int row = target.position().row();
                    int column = target.position().column();
                    ActionResult result;
                    if (move % 29 == 28) result = engine.endTurn();
                    else if (target.hasMine() && !(move % 37 == 36 && target.mineType() != MineType.KING)) {
                        result = engine.flag(row, column);
                    } else result = engine.reveal(row, column);
                    if (!result.accepted()) throw new AssertionError("Probe selected an illegal action");
                    update(digest, result.accepted() + ":" + result.message() + ":" + result.explosions());
                    for (Achievement achievement : Achievement.values()) {
                        if (result.newAchievements().contains(achievement)) update(digest, achievement.name());
                    }
                    record(digest, engine);
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void record(MessageDigest digest, GameEngine engine) {
        GameSnapshot state = engine.snapshot();
        update(digest, state.size() + ":" + state.difficulty() + ":" + state.status() + ":" + state.phase()
                + ":" + state.hp() + ":" + state.turn() + ":" + state.normalMineCount() + ":" + state.revealedCount()
                + ":" + state.kingPosition() + ":" + state.statistics() + ":" + state.outcome());
        update(digest, state.cells().toString());
        update(digest, state.informerZones().toString());
        for (Achievement achievement : Achievement.values()) {
            if (state.achievements().contains(achievement)) update(digest, achievement.name());
        }
        update(digest, engine.history().toString());
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) '\n');
    }

    private static boolean adjacentToRevealed(GameSnapshot state, Cell cell) {
        for (int row = Math.max(0, cell.position().row() - 1); row <= Math.min(state.size() - 1, cell.position().row() + 1); row++) {
            for (int column = Math.max(0, cell.position().column() - 1); column <= Math.min(state.size() - 1, cell.position().column() + 1); column++) {
                if (state.cellAt(row, column).revealed()) return true;
            }
        }
        return false;
    }

    private static void readSnapshots(GameEngine engine, int count) {
        for (int read = 0; read < count; read++) sink = engine.snapshot();
    }
}
