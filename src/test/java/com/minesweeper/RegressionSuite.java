package com.minesweeper;

import com.minesweeper.config.GameRules;
import com.minesweeper.controller.GameController;
import com.minesweeper.controller.GameView;
import com.minesweeper.model.*;
import com.minesweeper.service.GameEngine;
import com.minesweeper.ui.RuleBook;
import com.minesweeper.ui.UiInteractionChecks;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

/** 通过公开动作测试规则，无反射修改棋盘，无第三方依赖。 */
public final class RegressionSuite {
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-10-29T00:00:00Z"), ZoneOffset.UTC);
    private static int checks;
    private static int groups;
    private RegressionSuite() { }

    public static void main(String[] args) throws Exception {
        run("difficulty sizes, configured mine counts and rule text", RegressionSuite::configuration);
        run("800 initial boards across all difficulty sizes", RegressionSuite::initialization);
        run("deterministic and immutable snapshots", RegressionSuite::immutableSnapshots);
        run("snapshot cache, time refresh and action invalidation", RegressionSuite::snapshotLifecycle);
        run("120 seeded action traces match the current rules baseline", () -> check(
                EnginePerformanceProbe.fingerprint().equals("5f453c4a0fa45fc060a0f91688c1f47f2822b01705cfae95bc4c6efbd34fefa5"),
                "engine preserves complete snapshots, results and history under the current rules"));
        run("invalid actions and distant king area are rejected", RegressionSuite::invalidActions);
        run("king flags lose immediately without environment changes", RegressionSuite::kingFlag);
        run("direct king explosions lose immediately regardless of remaining HP", RegressionSuite::kingExplosion);
        run("collateral king explosions lose even when all other mines are cleared", RegressionSuite::blastHitsKing);
        run("unique explosion counts and difficulty damage", RegressionSuite::normalExplosion);
        run("right-clicking safe cells is a silent immutable no-op", RegressionSuite::wrongFlags);
        run("ordinary actions never move mines or close revealed cells", RegressionSuite::stablePlayerLayout);
        run("HP heals up to 100 and recovers normally after damage", RegressionSuite::healthCap);
        run("expanded achievements track legal progress, session totals and clean wins", RegressionSuite::achievementProgress);
        run("chord opens neighbors once after remaining mines reach zero", RegressionSuite::chord);
        run("informer flags and core flags remove only the target mine", RegressionSuite::partialInformerFlag);
        run("informer hints clamp random plus/minus one to 0-8 and false zero never expands", RegressionSuite::informerNoise);
        run("informer core clears interference while preserving other mines", RegressionSuite::informerCapture);
        run("informer explosions stay within 3x3 and settle once", RegressionSuite::informerExplosion);
        run("direct informer explosions regenerate exactly one core and preserve interference", RegressionSuite::informerRegeneration);
        run("informer regeneration stays inside when no other interior cell is available", RegressionSuite::informerInteriorFallback);
        run("same-turn informer achievement", RegressionSuite::sameTurnCapture);
        run("simultaneous boundary shrink and capacity-limited growth", RegressionSuite::environment);
        run("king movement stays hidden and cleared king never respawns", RegressionSuite::movement);
        run("only the surviving king remains after disarming the last mine", RegressionSuite::victory);
        run("revealing all safe cells cannot win while an informer core remains", RegressionSuite::allSafeNotVictory);
        run("exploding the last ordinary mine wins before environment changes", RegressionSuite::lastMineExplosion);
        run("elapsed time freezes after finish", RegressionSuite::timing);
        run("controller animation and stale presentation lifecycle", RegressionSuite::controller);
        run("controller delivers animation completion once and rejects stale results", RegressionSuite::controllerCompletions);
        run("menu, rule panels, mouse gestures and large-board layouts", UiInteractionChecks::run);
        System.out.println("PASS: " + groups + " test groups, " + checks + " rule assertions");
    }

    private static void configuration() {
        int[] sizes = {18, 25, 30, 35};
        int[] ordinary = {67, 157, 267, 397};
        int[] informer = {7, 13, 19, 26};
        int[] zoneSizes = {4, 6, 8, 10};
        int[] totals = {70, 160, 270, 400};
        int[] damage = {1, 2, 3, 4};
        int[] health = {5, 4, 3, 2};
        double[] rates = {.12, .15, .18, .20};
        double density = 0;
        for (Difficulty difficulty : Difficulty.values()) {
            int i = difficulty.ordinal();
            check(difficulty.boardSize() == sizes[i], "requested board size");
            check(difficulty.initialMines() == ordinary[i] && difficulty.zoneMineCount() == informer[i], "ordinary and per-zone mine counts");
            check(difficulty.totalInitialMines() == totals[i], "initial total includes core once and king once");
            check(difficulty.informerZoneSize() == zoneSizes[i], "requested informer dimensions");
            check(difficulty.damageMultiplier() == damage[i] && difficulty.initialHp() == health[i], "difficulty damage and health");
            check(Math.abs(difficulty.growthRate() - rates[i]) < 0.000001, "growth rates are halved");
            double currentDensity = totals[i] / (double) (sizes[i] * sizes[i]);
            check(currentDensity > density, "total mine density increases with difficulty");
            density = currentDensity;
            String summary = RuleBook.difficultySummary(difficulty);
            check(summary.contains(sizes[i] + "×" + sizes[i]) && summary.contains("共" + totals[i] + "颗"), "menu summary uses configuration");
            check(RuleBook.difficultyHtml().contains("<b>" + totals[i] + "</b>"), "rules total uses configuration");
        }
        check(RuleBook.specialHtml().contains("国王雷必须保留至胜利")
                && RuleBook.specialHtml().contains("被周围爆炸波及或排除国王雷，均立即失败"),
                "rules require the king to survive both disarming and explosions");
        check(RuleBook.specialHtml().contains("行号和列号均从 1 开始"), "rules use one-based display coordinates");
    }

    private static void initialization() {
        for (Difficulty difficulty : Difficulty.values()) {
            Set<Position> coreOffsets = new HashSet<>();
            for (long seed = 0; seed < 200; seed++) {
                GameEngine candidate = engine(difficulty, seed);
                GameSnapshot state = candidate.snapshot();
                check(candidate.history().stream().noneMatch(line -> line.contains("已调整") || line.contains("推理保障")),
                        "random openings are never adjusted to force a deduction");
                check(state.size() == difficulty.boardSize() && state.cells().size() == state.size() * state.size(), "dynamic board allocation");
                check(state.hp() == difficulty.initialHp() && state.normalMineCount() == difficulty.initialMines(), "initial HP and ordinary count");
                check(state.remainingMineCount() == difficulty.totalInitialMines(), "actual initial count matches descriptions");
                check(state.kingAlive() && state.revealedCount() >= 25, "king and initial safe area");
                for (InformerZone zone : state.informerZones()) {
                    check(zone.size() == difficulty.informerZoneSize() && zone.remainingMines() == difficulty.zoneMineCount(), "zone size and scaled mines");
                    check(zone.contains(zone.core()), "random core remains in its own zone");
                    coreOffsets.add(new Position(zone.core().row() - zone.origin().row(), zone.core().column() - zone.origin().column()));
                    check(state.cells().stream().filter(c -> c.informerId() == zone.id()
                            && c.mineType() == MineType.INFORMER_CORE).count() == 1, "exactly one core per zone");
                    check(zone.origin().row() >= 10 || zone.origin().column() >= 10, "zone avoids start buffer");
                    long members = state.cells().stream().filter(c -> c.informerId() == zone.id()).count();
                    check(members == zone.size() * zone.size(), "whole zone membership");
                }
                InformerZone a = state.informerZones().get(0);
                InformerZone b = state.informerZones().get(1);
                check(Math.abs(a.origin().row() - b.origin().row()) > a.size()
                        || Math.abs(a.origin().column() - b.origin().column()) > a.size(), "zones are separated");
                for (int row = 0; row < 5; row++) for (int column = 0; column < 5; column++) {
                    check(state.cellAt(row, column).revealed() && !state.cellAt(row, column).hasMine(), "initial safe area");
                }
                invariants(state);
            }
            int interiorSize = difficulty.informerZoneSize() - 2;
            check(coreOffsets.size() > 1 && coreOffsets.size() <= interiorSize * interiorSize,
                    "seeded boards distribute cores across the interior instead of a fixed center");
        }
    }

    private static void immutableSnapshots() {
        GameEngine first = engine(Difficulty.NORMAL, 32);
        GameSnapshot frozen = first.snapshot();
        check(frozen.equals(engine(Difficulty.NORMAL, 32).snapshot()), "reproducible board");
        expectImmutable(() -> frozen.cells().clear());
        expectImmutable(() -> frozen.informerZones().clear());
        expectImmutable(() -> frozen.achievements().add(Achievement.FIRST_WIN));
        expectImmutable(() -> first.history().clear());
        Cell target = accessible(first.snapshot(), c -> true);
        perform(first, target);
        check(first.history().stream().anyMatch(line -> line.contains("第" + (target.position().row() + 1)
                + "行，第" + (target.position().column() + 1) + "列")), "action history uses one-based coordinates for the selected cell");
        check(frozen.statistics().totalMoves() == 0 && frozen.status() == GameStatus.PLAYING, "saved snapshot remains unchanged");
    }

    private static void invalidActions() {
        GameEngine engine = engine(Difficulty.NORMAL, 2);
        GameSnapshot before = engine.snapshot();
        check(!engine.reveal(-1, 0).accepted() && !engine.flag(before.size(), 0).accepted(), "bounds rejected");
        check(!engine.reveal(0, 0).accepted(), "revealed cells ignored");
        Position king = before.kingPosition();
        check(!canAct(before, king) && !engine.flag(king.row(), king.column()).accepted(), "king area has no privileged click");
        check(before.equals(engine.snapshot()), "invalid actions do not count or mutate state");
    }

    private static void snapshotLifecycle() {
        for (Difficulty difficulty : Difficulty.values()) {
            MutableClock clock = new MutableClock();
            GameEngine engine = new GameEngine(difficulty, new Random(17), clock);
            GameSnapshot initial = engine.snapshot();
            check(engine.snapshot() == initial, "unchanged state and time reuse the immutable snapshot");
            engine.flag(-1, 0);
            engine.reveal(0, 0);
            engine.chord(-1, 0);
            check(engine.snapshot() == initial, "rejected actions do not discard unchanged snapshots");
            clock.seconds = 30;
            GameSnapshot timed = engine.snapshot();
            check(engine.elapsedSeconds() == 30 && timed.statistics().elapsedSeconds() == 30
                    && initial.statistics().elapsedSeconds() == 0, "time-only refresh never mutates saved statistics");
            check(timed.cells() == initial.cells() && timed.informerZones() == initial.informerZones(),
                    "time-only refresh shares immutable board and zones");
            check(engine.snapshot() == timed, "snapshot reused until the next second or accepted action");
            Cell safe = accessible(timed, c -> !c.hasMine());
            check(safe != null && perform(engine, safe).accepted(), "cache test explores a legal safe cell");
            GameSnapshot revealed = engine.snapshot();
            check(revealed.revealedCount() > timed.revealedCount() && timed.cells() != revealed.cells(),
                    "accepted reveal replaces board snapshot and keeps saved state intact");
            Cell mine = accessible(revealed, c -> c.mineType() == MineType.NORMAL);
            check(mine != null && perform(engine, mine).accepted(), "cache test clears an accessible normal mine");
            GameSnapshot flagged = engine.snapshot();
            check(flagged.hp() == Math.min(GameRules.MAX_HP, revealed.hp() + 1) && flagged.remainingMineCount() == revealed.remainingMineCount() - 1,
                    "flag invalidates cached counts and HP");
            invariants(flagged);
            expandTo(engine, 80);
            GameSnapshot beforeTurn = engine.snapshot();
            check(engine.endTurn().accepted(), "cache test enters the environment legally");
            GameSnapshot nextTurn = engine.snapshot();
            check(nextTurn != beforeTurn && nextTurn.cells() != beforeTurn.cells(), "environment replaces cached board");
            check(nextTurn.turn() == beforeTurn.turn() + (nextTurn.status() == GameStatus.PLAYING ? 1 : 0),
                    "environment updates turn or exposes the final state without stale cache");
            invariants(nextTurn);
            engine.restart(difficulty);
            GameSnapshot restarted = engine.snapshot();
            check(restarted != nextTurn && restarted.statistics().totalMoves() == 0
                    && restarted.remainingMineCount() == difficulty.totalInitialMines(), "same-size restart invalidates the whole cache");
            invariants(restarted);
        }
    }

    private static void kingFlag() {
        for (Difficulty difficulty : Difficulty.values()) {
            GameEngine engine = engine(difficulty, 17);
            Position king = engine.snapshot().kingPosition();
            approach(engine, king);
            GameSnapshot before = engine.snapshot();
            ActionResult result = engine.flag(king.row(), king.column());
            GameSnapshot after = engine.snapshot();
            check(result.accepted() && result.explosions().isEmpty(), "correct king flag needs no explosion animation");
            check(after.hp() == Math.min(GameRules.MAX_HP, before.hp() + 1) && after.statistics().minesCleared() == before.statistics().minesCleared() + 1, "king flag HP and stats");
            check(after.normalMineCount() == before.normalMineCount() && !after.kingAlive(), "king cleared independently");
            check(after.status() == GameStatus.LOST && after.phase() == GamePhase.FINISHED
                    && after.outcome().contains("国王雷被排除"), "king flag loses immediately");
            check(after.turn() == before.turn() && after.statistics().correctFlags() == before.statistics().correctFlags() + 1,
                    "king loss counts the action and skips environment");
            check(after.remainingMineCount() > 1 && after.hp() > 0, "king removal loses despite remaining mines and positive HP");
            for (Cell cell : before.cells()) {
                if (!cell.position().equals(king)) {
                    Cell remaining = after.cellAt(cell.position().row(), cell.position().column());
                    check(remaining.mineType() == cell.mineType() && remaining.revealed() == cell.revealed(),
                            "king flag does not redistribute mines or shrink territory");
                }
            }
            check(!after.achievements().contains(Achievement.KING_GUARDIAN), "destroying the king never grants its protection achievement");
            check(!after.achievements().contains(Achievement.FIRST_WIN), "king loss grants no victory achievements");
            check(!engine.endTurn().accepted() && !engine.reveal(0, 0).accepted()
                    && !engine.flag(king.row(), king.column()).accepted() && engine.snapshot().equals(after),
                    "king loss freezes state and prevents later actions");
            invariants(engine.snapshot());
        }
    }

    private static void kingExplosion() {
        for (Difficulty difficulty : Difficulty.values()) {
            GameEngine engine = engine(difficulty, 29);
            Position king = engine.snapshot().kingPosition();
            approach(engine, king);
            GameSnapshot before = engine.snapshot();
            Set<Position> expected = expectedExplosion(before, king);
            ActionResult result = engine.reveal(king.row(), king.column());
            GameSnapshot after = engine.snapshot();
            check(result.accepted() && new HashSet<>(result.explosions()).equals(expected), "king triggers normal 3x3 explosion");
            check(after.hp() == before.hp() - expected.size() * difficulty.damageMultiplier(), "king deals ordinary damage");
            check(after.statistics().minesCleared() == before.statistics().minesCleared() + expected.size(), "king counted in clear stats");
            check(!after.kingAlive() && after.status() == GameStatus.LOST && after.phase() == GamePhase.FINISHED
                    && after.outcome().contains("国王雷被引爆"), "king explosion always loses with the king-specific reason");
            check(after.turn() == before.turn() && after.revealedCount() == before.revealedCount(),
                    "king explosion cannot shrink territory or advance the turn");
            for (Cell cell : before.cells()) {
                if (!expected.contains(cell.position())) {
                    Cell remaining = after.cellAt(cell.position().row(), cell.position().column());
                    check(remaining.mineType() == cell.mineType() && remaining.revealed() == cell.revealed(),
                            "king explosion stops before mine redistribution");
                }
            }
            check(!engine.endTurn().accepted() && engine.snapshot().equals(after), "king explosion freezes the finished board");
            invariants(after);
        }
    }

    private static void blastHitsKing() {
        for (Difficulty difficulty : Difficulty.values()) {
            boolean found = false;
            for (long seed = 0; seed < 100 && !found; seed++) {
                GameEngine engine = engine(difficulty, seed);
                GameSnapshot state = engine.snapshot();
                Cell ordinary = around(state, state.kingPosition()).stream().map(p -> state.cellAt(p.row(), p.column()))
                        .filter(c -> c.mineType() == MineType.NORMAL).findFirst().orElse(null);
                if (ordinary == null) continue;
                clearExcept(engine, ordinary.position());
                approach(engine, ordinary.position());
                GameSnapshot before = engine.snapshot();
                Position king = before.kingPosition();
                check(before.remainingMineCount() == 2 && before.hp() > 2 * difficulty.damageMultiplier(),
                        "collateral scenario has only king and adjacent ordinary mine with sufficient HP");
                ActionResult result = engine.reveal(ordinary.position().row(), ordinary.position().column());
                GameSnapshot after = engine.snapshot();
                check(result.explosions().contains(king) && !after.kingAlive(), "king is not immune to nearby blast");
                check(after.status() == GameStatus.LOST && after.remainingMineCount() == 0 && after.hp() > 0
                        && after.outcome().contains("国王雷被引爆"), "removing all mines including king is a loss despite positive HP");
                check(after.turn() == before.turn() && after.revealedCount() == before.revealedCount(), "collateral king loss skips environment");
                check(!engine.endTurn().accepted() && engine.snapshot().equals(after), "collateral king loss freezes the board");
                invariants(after);
                found = true;
            }
            check(found, "king-neighbor blast scenario exists for every difficulty");
        }
    }

    private static void normalExplosion() {
        for (Difficulty difficulty : Difficulty.values()) {
            GameEngine engine = engine(difficulty, 43);
            Cell target = engine.snapshot().cells().stream().filter(c -> c.mineType() == MineType.NORMAL).findFirst().orElseThrow();
            approach(engine, target.position());
            GameSnapshot before = engine.snapshot();
            Set<Position> expected = expectedExplosion(before, target.position());
            ActionResult result = engine.reveal(target.position().row(), target.position().column());
            GameSnapshot after = engine.snapshot();
            check(result.explosions().size() == expected.size() && new HashSet<>(result.explosions()).equals(expected), "unique explosion origin and neighbors");
            check(after.hp() == before.hp() - expected.size() * difficulty.damageMultiplier(), "exact explosion damage");
            check(after.statistics().minesCleared() == before.statistics().minesCleared() + expected.size(), "exact explosion stats");
            if (after.hp() <= 0) check(after.turn() == before.turn(), "fatal blast does not advance turn");
            invariants(after);
        }
    }

    private static void wrongFlags() {
        for (Difficulty difficulty : Difficulty.values()) {
            GameEngine engine = engine(difficulty, 7);
            expandTo(engine, 70);
            GameSnapshot before = engine.snapshot();
            List<String> history = engine.history();
            for (Cell safe : before.cells()) {
                if (safe.hasMine() || safe.revealed()) continue;
                for (int attempt = 0; attempt < 3; attempt++) {
                    ActionResult result = engine.flag(safe.position().row(), safe.position().column());
                    check(!result.accepted() && result.message().isEmpty() && result.explosions().isEmpty()
                            && result.newAchievements().isEmpty(), "safe disarming returns a silent rejected result");
                    check(engine.snapshot() == before && engine.history().equals(history),
                            "safe disarming preserves HP, moves, layout, turn, achievements, cache and history");
                }
            }
            FakeView view = new FakeView();
            GameController controller = new GameController(engine, view);
            controller.start();
            Cell safe = accessible(before, c -> !c.hasMine());
            check(safe != null, "safe frontier exists for ignored input");
            controller.flag(safe.position().row(), safe.position().column());
            check(view.renders == 1 && view.completion == null && engine.snapshot() == before,
                    "ignored right clicks never trigger a render or explosion animation");
        }
    }

    private static void healthCap() {
        GameEngine engine = engine(Difficulty.HARD, 99);
        int cappedFlags = 0;
        for (int step = 0; step < engine.snapshot().cells().size() * 2
                && engine.snapshot().statistics().correctFlags() < 110; step++) {
            GameSnapshot before = engine.snapshot();
            Cell next = accessible(before, c -> c.hasMine() && c.mineType() != MineType.KING);
            if (next == null) next = accessible(before, c -> !c.hasMine());
            check(next != null && perform(engine, next).accepted(), "build health through legal exploration and disarming");
            GameSnapshot after = engine.snapshot();
            if (next.hasMine()) {
                check(after.hp() == Math.min(100, before.hp() + 1), "disarming heals exactly one point up to the cap");
                if (before.hp() == 100) cappedFlags++;
            }
        }
        check(engine.snapshot().hp() == 100 && cappedFlags > 0, "continued disarming cannot exceed 100 HP");
        check(engine.snapshot().achievements().contains(Achievement.FULL_HEALTH), "reaching maximum health unlocks its achievement");
        check(engine.history().stream().anyMatch(line -> line.contains("HP +0（已达上限）")), "history reports no healing at the cap");
        expandTo(engine, 150);
        Cell blast = accessible(engine.snapshot(), c -> c.mineType() == MineType.NORMAL
                && around(engine.snapshot(), c.position()).stream().noneMatch(p -> engine.snapshot().cellAt(p.row(), p.column()).mineType() == MineType.KING));
        check(blast != null && engine.reveal(blast.position().row(), blast.position().column()).accepted(), "explode a normal mine at full health");
        check(engine.snapshot().hp() < 100 && engine.snapshot().status() == GameStatus.PLAYING, "explosion damage still applies below the health cap");
        for (int step = 0; step < engine.snapshot().cells().size() * 2 && engine.snapshot().hp() < 100; step++) {
            GameSnapshot state = engine.snapshot();
            Cell next = accessible(state, c -> c.hasMine() && c.mineType() != MineType.KING);
            if (next == null) next = accessible(state, c -> !c.hasMine());
            check(next != null && perform(engine, next).accepted(), "recover health normally after damage");
        }
        check(engine.snapshot().hp() == 100, "healing resumes after damage and stops at the cap again");
        invariants(engine.snapshot());
    }

    private static void stablePlayerLayout() {
        for (Difficulty difficulty : Difficulty.values()) {
            for (int seed = 0; seed < 12; seed++) {
                GameEngine engine = engine(difficulty, seed);
                for (int step = 0; step < 60 && engine.snapshot().status() == GameStatus.PLAYING; step++) {
                    GameSnapshot before = engine.snapshot();
                    Cell target = accessible(before, c -> !c.hasMine());
                    if (target == null || step % 3 == 2) target = accessible(before, c -> c.hasMine() && c.mineType() != MineType.KING);
                    if (target == null) break;
                    check(perform(engine, target).accepted(), "make a legal non-explosive action");
                    GameSnapshot after = engine.snapshot();
                    for (int i = 0; i < before.cells().size(); i++) {
                        Cell old = before.cells().get(i), now = after.cells().get(i);
                        if (!old.position().equals(target.position())) check(old.mineType() == now.mineType(),
                                "safe exploration and disarming never move another mine");
                        check(!old.revealed() || now.revealed(), "ordinary actions never close an already revealed cell");
                    }
                    check(after.turn() == before.turn() && engine.history().stream().noneMatch(line -> line.contains("推理保障")),
                            "ordinary actions cannot invoke hidden board repairs");
                }
            }
        }
    }

    private static void achievementProgress() {
        GameEngine engine = engine(Difficulty.NIGHTMARE, 99);
        Cell first = accessible(engine.snapshot(), c -> c.mineType() == MineType.NORMAL);
        if (first == null) {
            first = engine.snapshot().cells().stream().filter(c -> c.mineType() == MineType.NORMAL).findFirst().orElseThrow();
            approach(engine, first.position());
        }
        ActionResult result = engine.flag(first.position().row(), first.position().column());
        check(result.newAchievements().contains(Achievement.FIRST_DISARM), "first proven mine unlocks the first-disarm achievement");
        check(!engine.flag(first.position().row(), first.position().column()).accepted(), "repeating a removed mine never duplicates progress");
        solve(engine);
        GameSnapshot nightmare = engine.snapshot();
        check(nightmare.achievements().containsAll(Set.of(Achievement.FIRST_DISARM, Achievement.DISARM_EXPERT,
                Achievement.FULL_HEALTH, Achievement.TERRITORY_EXPLORER, Achievement.NIGHTMARE_WIN, Achievement.UNHARMED_WIN)),
                "new achievements reflect actual disarming, health, territory and clean nightmare completion");
        check(nightmare.statistics().lifetimeMinesCleared() == 399
                && !nightmare.achievements().contains(Achievement.VETERAN_SWEEPER), "500-clear achievement cannot unlock early");
        engine.restart(Difficulty.HARD);
        check(engine.snapshot().achievements().contains(Achievement.NIGHTMARE_WIN), "achievements survive restarting within the process");
        solve(engine);
        check(engine.snapshot().statistics().lifetimeMinesCleared() == 558
                && engine.snapshot().achievements().contains(Achievement.VETERAN_SWEEPER), "session clear totals unlock the 500-clear milestone");

        GameEngine patient = engine(Difficulty.NORMAL, 99);
        expandTo(patient, 100);
        check(patient.endTurn().accepted() && patient.snapshot().status() == GameStatus.PLAYING, "a manual environment turn can be survived without damage");
        solve(patient);
        check(patient.snapshot().turn() > 1 && patient.snapshot().achievements().contains(Achievement.UNHARMED_WIN),
                "a clean win may span multiple turns without explosions or wrong flags");
    }

    private static void chord() {
        for (Difficulty difficulty : Difficulty.values()) {
            GameEngine engine = null;
            Position origin = null;
            for (long seed = 0; seed < 100 && origin == null; seed++) {
                GameEngine candidate = engine(difficulty, seed);
                GameSnapshot state = candidate.snapshot();
                for (Cell cell : state.cells()) {
                    if (!cell.revealed() || cell.informerId() >= 0 || cell.displayedNumber() == 0) continue;
                    List<Cell> neighbors = around(state, cell.position()).stream()
                            .map(p -> state.cellAt(p.row(), p.column())).toList();
                    if (neighbors.stream().noneMatch(c -> c.mineType() == MineType.KING)
                            && neighbors.stream().anyMatch(c -> !c.revealed() && !c.hasMine())) {
                        engine = candidate;
                        origin = cell.position();
                        break;
                    }
                }
            }
            check(engine != null && origin != null, "numbered frontier has safe and mined neighbors");
            GameSnapshot initial = engine.snapshot();
            check(!engine.chord(origin.row(), origin.column()).accepted() && engine.snapshot().equals(initial),
                    "positive remaining number refuses chord without changing anything");
            check(!engine.chord(-1, 0).accepted(), "chord bounds check");
            Cell unknown = initial.cells().stream().filter(c -> !c.revealed()).findFirst().orElseThrow();
            check(!engine.chord(unknown.position().row(), unknown.position().column()).accepted(), "unknown origin rejected");
            for (Position next : around(initial, origin)) {
                if (initial.cellAt(next.row(), next.column()).hasMine()) {
                    check(engine.flag(next.row(), next.column()).accepted(), "right flag removes adjacent mine");
                }
            }
            GameSnapshot before = engine.snapshot();
            check(before.cellAt(origin.row(), origin.column()).displayedNumber() == 0, "direct clears reduce number to zero");
            FakeView view = new FakeView();
            GameController controller = new GameController(engine, view);
            controller.chord(origin.row(), origin.column());
            GameSnapshot after = engine.snapshot();
            check(view.completion == null && view.renders == 1, "controller renders chord without explosion animation");
            check(after.statistics().totalMoves() == before.statistics().totalMoves() + 1
                    && after.statistics().correctFlags() == before.statistics().correctFlags(), "batch expansion counts as one action");
            check(after.hp() == before.hp() && after.turn() == before.turn()
                    && after.remainingMineCount() == before.remainingMineCount()
                    && java.util.Objects.equals(after.kingPosition(), before.kingPosition()), "chord does not change HP, turn or mines");
            for (Position next : around(before, origin)) {
                check(after.cellAt(next.row(), next.column()).revealed(), "each adjacent safe cell opens");
            }
            check(after.revealedCount() > before.revealedCount(), "chord expands frontier");
            check(!engine.chord(origin.row(), origin.column()).accepted() && engine.snapshot().equals(after),
                    "repeat chord with no targets is a no-op");
            invariants(after);

            GameEngine interfered = engine(difficulty, 17);
            Cell target = interfered.snapshot().cells().stream().filter(c -> c.informerId() == 0 && !c.hasMine()).findFirst().orElseThrow();
            approach(interfered, target.position());
            interfered.reveal(target.position().row(), target.position().column());
            GameSnapshot noisy = interfered.snapshot();
            ActionResult rejection = interfered.chord(target.position().row(), target.position().column());
            check(!rejection.accepted() && rejection.message().contains("干扰") && interfered.snapshot().equals(noisy),
                    "informer interference blocks chord without revealing hidden mines");

            GameEngine disabled = engine(difficulty, 17);
            capture(disabled, 0);
            Position core = disabled.snapshot().informerZones().get(0).core();
            check(disabled.reveal(core.row(), core.column()).accepted(), "explicit exploration opens the cleared core before chord");
            GameSnapshot coreRemoved = disabled.snapshot();
            for (Position next : around(coreRemoved, core)) {
                if (coreRemoved.cellAt(next.row(), next.column()).hasMine()) {
                    check(disabled.flag(next.row(), next.column()).accepted(), "clear mines adjacent to disabled core");
                }
            }
            GameSnapshot ready = disabled.snapshot();
            check(ready.cellAt(core.row(), core.column()).informerId() == 0
                    && ready.cellAt(core.row(), core.column()).displayedNumber() == 0,
                    "disabled core retains membership with accurate zero hint");
            ActionResult expanded = disabled.chord(core.row(), core.column());
            GameSnapshot opened = disabled.snapshot();
            check(expanded.accepted() && expanded.explosions().isEmpty() && opened.revealedCount() > ready.revealedCount(),
                    "core removal permits chord despite retained zone membership");
            check(opened.hp() == ready.hp() && opened.turn() == ready.turn()
                    && opened.remainingMineCount() == ready.remainingMineCount()
                    && opened.statistics().totalMoves() == ready.statistics().totalMoves() + 1,
                    "chord after core removal preserves HP, turn and mines and counts once");
            invariants(opened);
        }
    }

    private static void partialInformerFlag() {
        for (Difficulty difficulty : Difficulty.values()) {
            for (MineType type : new MineType[]{MineType.NORMAL, MineType.INFORMER_CORE}) {
                GameEngine engine = engine(difficulty, 17);
                Position target = engine.snapshot().cells().stream().filter(c -> c.informerId() == 0
                        && c.mineType() == type).findFirst().orElseThrow().position();
                approach(engine, target);
                GameSnapshot before = engine.snapshot();
                check(before.informerZones().get(0).remainingMines() > 1, "partial capture scenario");
                ActionResult result = engine.flag(target.row(), target.column());
                GameSnapshot after = engine.snapshot();
                check(result.accepted() && result.explosions().isEmpty(), "individual informer flag accepted");
                boolean core = type == MineType.INFORMER_CORE;
                check(after.informerZones().get(0).active() != core && after.informerZones().get(0).capturedTurn()
                        == (core ? before.turn() : -1), "only the core changes interference state");
                check(after.informerZones().get(0).remainingMines() == before.informerZones().get(0).remainingMines() - 1,
                        "zone loses exactly one mine");
                check(after.revealedCount() == before.revealedCount() + (core ? 0 : 1) && after.hp() == Math.min(GameRules.MAX_HP, before.hp() + 1),
                        "non-core informer behaves like an ordinary mine; only the core avoids territory expansion");
                Cell cleared = after.cellAt(target.row(), target.column());
                check(!cleared.hasMine() && cleared.flagged() && cleared.revealed() != core,
                        "non-core removal reveals its tile while the core stays hidden and marked");
                check(after.statistics().minesCleared() == before.statistics().minesCleared() + 1,
                        "one mine credited");
                for (Cell cell : before.cells()) {
                    if (cell.position().equals(target)) continue;
                    Cell remaining = after.cellAt(cell.position().row(), cell.position().column());
                    check(remaining.mineType() == cell.mineType() && remaining.revealed() == cell.revealed(),
                            "other mines and reveal states preserved");
                }
                for (Cell cell : after.cells()) {
                    if (cell.informerId() == 0 && core) check(cell.displayedNumber() == cell.adjacentMines(), "core removal restores true numbers");
                    if (cell.informerId() == 1 && !cell.hasMine()) check(Math.abs(cell.displayedNumber() - cell.adjacentMines()) <= 1,
                            "other zone keeps bounded plus/minus-one interference");
                }
                check(!engine.flag(target.row(), target.column()).accepted() && engine.snapshot().equals(after),
                        "repeated informer removal cannot cost HP or change the board");
                if (core) check(engine.reveal(target.row(), target.column()).accepted()
                            && engine.snapshot().cellAt(target.row(), target.column()).revealed(),
                            "a cleared core cell can be explored explicitly");
                invariants(after);
            }
        }
    }

    private static void informerNoise() {
        boolean sawClampedZero = false;
        boolean sawFalseZero = false;
        for (Difficulty difficulty : Difficulty.values()) {
            for (boolean increase : new boolean[]{false, true}) {
                GameEngine engine = new GameEngine(difficulty, new FixedNoiseRandom(17, increase), FIXED_CLOCK);
                for (int frame = 0; frame < 2; frame++) {
                    GameSnapshot state = engine.snapshot();
                    for (Cell cell : state.cells()) {
                        if (cell.informerId() < 0 || cell.hasMine()) continue;
                        check(cell.displayedNumber() == Math.max(0, Math.min(8, cell.adjacentMines() + (increase ? 1 : -1))),
                                "initial and environment hints apply the chosen sign clamped to 0-8");
                        sawClampedZero |= !increase && cell.adjacentMines() == 0 && cell.displayedNumber() == 0;
                        sawFalseZero |= cell.displayedNumber() == 0 && cell.adjacentMines() == 1;
                    }
                    if (frame == 0) engine.endTurn();
                }
            }
        }
        check(sawClampedZero && sawFalseZero, "boundary coverage includes clipped zero and misleading zero");
        boolean testedEight = false;
        for (int seed = 0; seed < 2000 && !testedEight; seed++) {
            GameSnapshot positive = new GameEngine(Difficulty.HARD, new FixedNoiseRandom(seed, true), FIXED_CLOCK).snapshot();
            Cell boundary = positive.cells().stream().filter(c -> c.informerId() >= 0 && !c.hasMine() && c.adjacentMines() == 8)
                    .findFirst().orElse(null);
            if (boundary == null) continue;
            Cell negative = new GameEngine(Difficulty.HARD, new FixedNoiseRandom(seed, false), FIXED_CLOCK).snapshot()
                    .cellAt(boundary.position().row(), boundary.position().column());
            check(boundary.displayedNumber() == 8 && negative.displayedNumber() == 7, "eight plus one stays eight; eight minus one becomes seven");
            testedEight = true;
        }
        check(testedEight, "exercise an actual noisy safe tile surrounded by eight mines");
        boolean testedZero = false;
        for (int seed = 0; seed < 100 && !testedZero; seed++) {
            GameEngine engine = new GameEngine(Difficulty.NORMAL, new FixedNoiseRandom(seed, false), FIXED_CLOCK);
            Cell candidate = engine.snapshot().cells().stream().filter(c -> c.informerId() >= 0 && !c.hasMine()
                    && c.displayedNumber() == 0).findFirst().orElse(null);
            if (candidate == null) continue;
            Position target = candidate.position();
            approach(engine, target);
            GameSnapshot before = engine.snapshot();
            Cell ready = before.cellAt(target.row(), target.column());
            if (ready.revealed() || ready.displayedNumber() != 0) continue;
            check(engine.reveal(target.row(), target.column()).accepted(), "a misleading zero can be explored normally");
            GameSnapshot after = engine.snapshot();
            check(after.revealedCount() == before.revealedCount() + 1,
                    "a misleading zero opens only the selected tile, never its neighbors");
            check(!engine.chord(target.row(), target.column()).accepted() && engine.snapshot().equals(after),
                    "a misleading zero also rejects chord without changing state");
            invariants(after);
            testedZero = true;
        }
        check(testedZero, "exercise a real reachable misleading-zero tile");
    }

    private static void informerCapture() {
        for (Difficulty difficulty : Difficulty.values()) {
            GameEngine complete = engine(difficulty, 17);
            capture(complete, 0);
            capture(complete, 1);
            check(complete.snapshot().informerZones().stream().allMatch(z -> !z.active()),
                    "both cores can be removed at every zone size");
            invariants(complete.snapshot());
            expandTo(complete, 100);
            GameSnapshot beforeTurn = complete.snapshot();
            Set<Position> remaining = beforeTurn.cells().stream().filter(c -> c.informerId() >= 0 && c.hasMine())
                    .map(Cell::position).collect(java.util.stream.Collectors.toSet());
            check(!remaining.isEmpty(), "core removal leaves zone mines for future actions");
            complete.endTurn();
            GameSnapshot next = complete.snapshot();
            check(next.informerZones().stream().allMatch(z -> !z.active()), "interference never returns after core removal");
            check(next.cells().stream().filter(c -> c.informerId() >= 0 && c.hasMine()).map(Cell::position)
                    .collect(java.util.stream.Collectors.toSet()).equals(remaining), "remaining zone mines are not redistributed next turn");
            for (Position position : remaining) {
                check(next.cellAt(position.row(), position.column()).mineType()
                        == beforeTurn.cellAt(position.row(), position.column()).mineType(), "remaining mine types stay unchanged");
            }
            invariants(next);
        }
        GameEngine engine = engine(Difficulty.NORMAL, 17);
        capture(engine, 0);
        GameSnapshot state = engine.snapshot();
        check(!state.informerZones().get(0).active() && state.informerZones().get(0).remainingMines() > 0,
                "core disables interference while other mines remain");
        int lifetime = state.statistics().lifetimeMinesCleared();
        for (Difficulty difficulty : Difficulty.values()) {
            engine.restart(difficulty);
            GameSnapshot fresh = engine.snapshot();
            check(fresh.statistics().totalMoves() == 0 && fresh.statistics().minesCleared() == 0, "single-game stats reset");
            check(fresh.statistics().lifetimeMinesCleared() == lifetime, "session progress retained");
            check(fresh.size() == difficulty.boardSize() && fresh.kingAlive(), "restart reallocates and restores king");
            check(fresh.informerZones().stream().allMatch(z -> z.active() && z.capturedTurn() == -1
                    && z.remainingMines() == difficulty.zoneMineCount()), "informer state fully reset");
            invariants(fresh);
        }
    }

    private static void informerExplosion() {
        for (Difficulty difficulty : Difficulty.values()) {
            for (MineType type : new MineType[]{MineType.INFORMER_CORE, MineType.NORMAL}) {
                GameEngine engine = null;
                Position target = null;
                for (int seed = 0; seed < 200 && target == null; seed++) {
                    GameEngine candidate = engine(difficulty, seed);
                    GameSnapshot initial = candidate.snapshot();
                    Cell cell = initial.cells().stream().filter(c -> c.mineType() == type
                            && (c.informerId() == 0 || type == MineType.NORMAL && around(initial, c.position()).stream()
                            .anyMatch(p -> initial.cellAt(p.row(), p.column()).informerId() == 0
                                    && initial.cellAt(p.row(), p.column()).mineType() == MineType.INFORMER_CORE)))
                            .findFirst().orElse(null);
                    if (cell != null) { engine = candidate; target = cell.position(); }
                }
                check(engine != null && target != null, "find direct and neighboring-core blast scenarios");
                approach(engine, target);
                GameSnapshot before = engine.snapshot();
                Set<Position> expected = expectedExplosion(before, target);
                long zoneHits = expected.stream().filter(p -> before.cellAt(p.row(), p.column()).informerId() == 0).count();
                boolean directCore = type == MineType.INFORMER_CORE;
                ActionResult result = engine.reveal(target.row(), target.column());
                GameSnapshot after = engine.snapshot();
                check(new HashSet<>(result.explosions()).equals(expected), "all mine types share bounded explosion");
                check(after.hp() == before.hp() - expected.size() * difficulty.damageMultiplier(), "informer HP deducted once");
                check(after.statistics().minesCleared() == before.statistics().minesCleared() + expected.size(), "informer stats incremented once");
                check(after.informerZones().get(0).active() && after.informerZones().get(0).remainingMines()
                        == before.informerZones().get(0).remainingMines() - zoneHits + (directCore ? 1 : 0), "only directly exploded core regenerates and interference persists");
                if (!directCore) check(!result.explosions().contains(before.informerZones().get(0).core())
                        && after.informerZones().get(0).core().equals(before.informerZones().get(0).core()), "normal blast cannot damage or relocate an informer core");
                for (Cell cell : before.cells()) {
                    if (cell.informerId() != 0 || expected.contains(cell.position())) continue;
                    if (cell.position().equals(after.informerZones().get(0).core())) continue;
                    Cell remaining = after.cellAt(cell.position().row(), cell.position().column());
                    check(remaining.mineType() == cell.mineType() && (!remaining.revealed() || cell.revealed()),
                            "remote informer cells are never automatically revealed or cleared");
                }
                invariants(after);
            }
        }
    }

    private static void informerRegeneration() {
        for (Difficulty difficulty : Difficulty.values()) {
            GameEngine engine = engine(difficulty, 37);
            for (int round = 0; round < 3; round++) {
                for (int step = 0; step < 80 && engine.snapshot().hp() < 50; step++) {
                    Cell mine = engine.snapshot().cells().stream().filter(c -> c.mineType() == MineType.NORMAL && c.informerId() < 0).findFirst().orElseThrow();
                    Position target = mine.position();
                    approach(engine, target);
                    check(engine.flag(target.row(), target.column()).accepted(), "fund health through legal normal-mine removal");
                }
                expandTo(engine, 150);
                Position core = engine.snapshot().informerZones().get(0).core();
                approach(engine, core);
                GameSnapshot before = engine.snapshot();
                Set<Position> expected = expectedExplosion(before, core);
                InformerZone beforeZone = before.informerZones().get(0);
                boolean hasAlternative = before.cells().stream().anyMatch(c -> c.informerId() == 0 && !c.revealed()
                        && !c.hasMine() && insideZoneInterior(beforeZone, c.position()));
                ActionResult result = engine.reveal(core.row(), core.column());
                GameSnapshot after = engine.snapshot();
                InformerZone zone = after.informerZones().get(0);
                check(after.status() == GameStatus.PLAYING && zone.active() && zone.capturedTurn() == -1,
                        "direct core explosion regenerates it without granting intelligence");
                check(new HashSet<>(result.explosions()).equals(expected)
                        && after.hp() == before.hp() - expected.size() * difficulty.damageMultiplier(), "direct informer explosion uses ordinary damage once");
                check(zone.contains(zone.core()) && after.cellAt(zone.core().row(), zone.core().column()).mineType() == MineType.INFORMER_CORE
                        && !after.cellAt(zone.core().row(), zone.core().column()).revealed(), "one regenerated core remains in its own unknown zone cells");
                check(after.cells().stream().filter(c -> c.informerId() == 0 && c.mineType() == MineType.INFORMER_CORE).count() == 1,
                        "regeneration never duplicates cores");
                check(!hasAlternative || !zone.core().equals(core), "regeneration chooses another cell whenever space is available");
                check(after.statistics().minesCleared() == before.statistics().minesCleared() + expected.size(), "each actually exploded mine is counted once");
                invariants(after);
            }
            capture(engine, 0);
            engine.endTurn();
            check(!engine.snapshot().informerZones().get(0).active()
                    && engine.snapshot().cells().stream().noneMatch(c -> c.informerId() == 0 && c.mineType() == MineType.INFORMER_CORE),
                    "correctly disarmed core never regenerates and interference stays off");
        }
        GameEngine fatal = engine(Difficulty.NIGHTMARE, 17);
        Position core = fatal.snapshot().informerZones().get(0).core();
        approach(fatal, core);
        // Exhaust HP with repeated core explosions, rebuilding territory only while still alive.
        for (int round = 0; round < 100 && fatal.snapshot().status() == GameStatus.PLAYING; round++) {
            core = fatal.snapshot().informerZones().get(0).core();
            approach(fatal, core);
            fatal.reveal(core.row(), core.column());
            invariants(fatal.snapshot());
        }
        check(fatal.snapshot().status() == GameStatus.LOST && fatal.snapshot().informerZones().get(0).active(),
                "even a fatal core explosion preserves its interference and regenerated mine");
    }

    private static void informerInteriorFallback() {
        for (Difficulty difficulty : Difficulty.values()) {
            GameEngine engine = engine(difficulty, 99);
            Position core = engine.snapshot().informerZones().get(0).core();
            clearExcept(engine, core);
            InformerZone zone = engine.snapshot().informerZones().get(0);
            for (int row = zone.origin().row() + 1; row < zone.origin().row() + zone.size() - 1; row++) {
                for (int column = zone.origin().column() + 1; column < zone.origin().column() + zone.size() - 1; column++) {
                    Position next = new Position(row, column);
                    if (next.equals(core)) continue;
                    approach(engine, next);
                    if (!engine.snapshot().cellAt(row, column).revealed()) {
                        check(engine.reveal(row, column).accepted(), "reveal every other interior cell legally");
                    }
                }
            }
            approach(engine, core);
            GameSnapshot before = engine.snapshot();
            check(before.cells().stream().noneMatch(c -> insideZoneInterior(zone, c.position())
                    && !c.revealed() && !c.hasMine()), "no alternative interior regeneration location remains");
            check(engine.reveal(core.row(), core.column()).accepted(), "explode core without an alternative interior cell");
            GameSnapshot after = engine.snapshot();
            check(after.informerZones().get(0).core().equals(core) && after.informerZones().get(0).active()
                    && after.cellAt(core.row(), core.column()).mineType() == MineType.INFORMER_CORE,
                    "fallback keeps the core at its original interior position without ending interference");
            check(after.remainingMineCount() == 2 && after.status() == GameStatus.PLAYING, "regenerated last core prevents premature victory");
            invariants(after);
        }
    }

    private static void sameTurnCapture() {
        GameEngine engine = engine(Difficulty.NORMAL, 29);
        capture(engine, 0);
        capture(engine, 1);
        check(engine.snapshot().achievements().contains(Achievement.INFORMER_MASTER), "same-turn capture achievement");
        GameEngine other = engine(Difficulty.NORMAL, 29);
        capture(other, 0);
        expandTo(other, 120);
        other.endTurn();
        check(other.snapshot().status() == GameStatus.PLAYING, "second turn remains playable");
        capture(other, 1);
        check(!other.snapshot().achievements().contains(Achievement.INFORMER_MASTER), "different turns do not unlock same-turn achievement");
    }

    private static void environment() {
        for (Difficulty difficulty : Difficulty.values()) {
            for (long seed = 0; seed < 30; seed++) {
                GameEngine engine = engine(difficulty, seed);
                for (int turn = 0; turn < 6 && engine.snapshot().status() == GameStatus.PLAYING; turn++) {
                    expandTo(engine, 70);
                    GameSnapshot before = engine.snapshot();
                    if (before.status() != GameStatus.PLAYING) break;
                    boolean[] expected = new boolean[before.cells().size()];
                    for (int i = 0; i < expected.length; i++) {
                        Cell cell = before.cells().get(i);
                        expected[i] = cell.revealed() && around(before, cell.position()).stream()
                                .allMatch(p -> before.cellAt(p.row(), p.column()).revealed());
                    }
                    engine.endTurn();
                    GameSnapshot after = engine.snapshot();
                    for (int i = 0; i < expected.length; i++) {
                        Cell cell = after.cells().get(i);
                        check(cell.revealed() == expected[i], "environment shrinks exactly one layer without an automatic layout repair");
                    }
                    int outside = (int) before.cells().stream().filter(c -> c.mineType() == MineType.NORMAL && c.informerId() < 0).count();
                    int inside = before.normalMineCount() - outside;
                    int desired = outside + (int) Math.floor(outside * difficulty.growthRate());
                    check(after.normalMineCount() == inside + Math.min(desired, normalCapacity(after, expected)), "outside growth capped by actual capacity; informer mines remain fixed");
                    invariants(after);
                }
            }
        }
    }

    private static void movement() {
        GameEngine engine = engine(Difficulty.NORMAL, 51);
        for (int round = 0; round < 5; round++) {
            expandTo(engine, 100);
            if (engine.snapshot().status() != GameStatus.PLAYING) break;
            engine.endTurn();
            invariants(engine.snapshot());
            check(engine.snapshot().kingAlive(), "surviving king remains unique");
        }
    }

    private static void victory() {
        for (Difficulty difficulty : Difficulty.values()) {
            GameEngine engine = engine(difficulty, 99);
            solve(engine);
            GameSnapshot state = engine.snapshot();
            check(state.status() == GameStatus.WON && state.kingAlive() && state.remainingMineCount() == 1,
                    "victory preserves the king as the sole remaining mine");
            check(state.achievements().contains(Achievement.FIRST_WIN), "win achievement");
            check(state.achievements().contains(Achievement.KING_GUARDIAN), "protecting the king unlocks the updated achievement");
            check(state.achievements().contains(Achievement.UNHARMED_WIN), "clean win unlocks the undamaged achievement");
            check(state.achievements().contains(Achievement.HELL_WIN) == (difficulty == Difficulty.HELL)
                    && state.achievements().contains(Achievement.NIGHTMARE_WIN) == (difficulty == Difficulty.NIGHTMARE),
                    "difficulty-specific win achievements match the completed difficulty");
            check(state.cells().stream().anyMatch(c -> !c.hasMine() && !c.revealed()), "winning no longer requires revealing all safe cells");
            GameSnapshot frozen = engine.snapshot();
            check(!engine.endTurn().accepted() && !engine.reveal(0, 0).accepted() && engine.snapshot().equals(frozen), "finished state ignores inputs");
        }
    }

    private static void allSafeNotVictory() {
        for (Difficulty difficulty : Difficulty.values()) {
            GameEngine engine = engine(difficulty, 99);
            Position core = engine.snapshot().informerZones().get(0).core();
            clearExcept(engine, core);
            for (int step = 0; step < engine.snapshot().cells().size(); step++) {
                Cell safe = accessible(engine.snapshot(), c -> !c.hasMine());
                if (safe == null) break;
                check(engine.reveal(safe.position().row(), safe.position().column()).accepted(), "reveal every remaining safe cell legally");
            }
            GameSnapshot before = engine.snapshot();
            check(before.cells().stream().filter(c -> !c.hasMine()).allMatch(Cell::revealed), "all safe cells are revealed in counterexample");
            check(before.status() == GameStatus.PLAYING && before.remainingMineCount() == 2
                    && before.informerZones().get(0).active(), "all-safe reveal cannot win while a core and king remain");
            check(engine.flag(core.row(), core.column()).accepted(), "disarm final core");
            GameSnapshot after = engine.snapshot();
            check(after.status() == GameStatus.WON && after.kingAlive() && after.remainingMineCount() == 1,
                    "disarming the final core wins immediately");
            check(!after.cellAt(core.row(), core.column()).revealed() && !after.informerZones().get(0).active(),
                    "final core still clears interference without granting territory");
            check(after.turn() == before.turn() && after.hp() == Math.min(GameRules.MAX_HP, before.hp() + 1), "final core skips environment and grants capped healing");
            invariants(after);
        }
    }

    private static void lastMineExplosion() {
        for (Difficulty difficulty : Difficulty.values()) {
            GameEngine engine = engine(difficulty, 99);
            Position king = engine.snapshot().kingPosition();
            Cell last = engine.snapshot().cells().stream().filter(c -> c.mineType() == MineType.NORMAL
                    && Math.max(Math.abs(c.position().row() - king.row()), Math.abs(c.position().column() - king.column())) > 1)
                    .findFirst().orElseThrow();
            clearExcept(engine, last.position());
            approach(engine, last.position());
            GameSnapshot before = engine.snapshot();
            check(before.remainingMineCount() == 2 && before.hp() > difficulty.damageMultiplier(), "final ordinary mine can be exploded safely");
            ActionResult result = engine.reveal(last.position().row(), last.position().column());
            GameSnapshot after = engine.snapshot();
            check(result.explosions().equals(List.of(last.position())) && after.remainingMineCount() == 1 && after.kingAlive()
                    && after.status() == GameStatus.WON, "last non-king explosion wins with exactly one surviving king");
            check(after.turn() == before.turn() && after.revealedCount() == before.revealedCount()
                    && after.kingPosition().equals(before.kingPosition()), "win is settled before contraction, king movement and growth");
            check(after.hp() == before.hp() - difficulty.damageMultiplier()
                    && after.statistics().minesCleared() == before.statistics().minesCleared() + 1, "winning explosion settles damage and clear count once");
            check(!after.achievements().contains(Achievement.UNHARMED_WIN), "winning with an explosion is not an undamaged win");
            invariants(after);
        }
    }

    private static void timing() {
        MutableClock clock = new MutableClock();
        GameEngine engine = new GameEngine(Difficulty.NORMAL, new Random(99), clock);
        clock.seconds = 30;
        check(engine.snapshot().statistics().elapsedSeconds() == 30, "live elapsed time");
        solve(engine);
        clock.seconds = 90;
        check(engine.elapsedSeconds() == 30 && engine.snapshot().statistics().elapsedSeconds() == 30, "finish freezes time");
        engine.restart(Difficulty.HARD);
        check(engine.snapshot().statistics().elapsedSeconds() == 0, "restart resets clock");
    }

    private static void controller() {
        GameEngine engine = engine(Difficulty.NORMAL, 31);
        Cell target = engine.snapshot().cells().stream().filter(c -> c.mineType() == MineType.NORMAL).findFirst().orElseThrow();
        approach(engine, target.position());
        FakeView view = new FakeView();
        GameController controller = new GameController(engine, view);
        controller.start();
        controller.reveal(target.position().row(), target.position().column());
        check(view.completion != null, "explosion requests animation");
        GameSnapshot settled = engine.snapshot();
        controller.endTurn();
        controller.chord(0, 0);
        check(engine.snapshot().equals(settled), "animation protects operations");
        Runnable old = view.completion;
        controller.restart(Difficulty.HARD);
        GameSnapshot restarted = engine.snapshot();
        int renders = view.renders;
        old.run();
        check(view.renders == renders && restarted.equals(engine.snapshot()), "stale animation callback ignored after resize/restart");
        controller.cancelPendingPresentation();
        check(view.cancelled, "menu cancels pending presentation");
    }

    private static void controllerCompletions() {
        GameEngine engine = engine(Difficulty.NORMAL, 31);
        Cell target = engine.snapshot().cells().stream().filter(c -> c.mineType() == MineType.NORMAL).findFirst().orElseThrow();
        approach(engine, target.position());
        FakeView view = new FakeView();
        GameController controller = new GameController(engine, view);
        controller.start();
        int renders = view.renders;
        controller.reveal(-1, 0);
        check(view.renders == renders && view.results == 1, "rejected input reports a result without rebuilding the view");
        controller.reveal(target.position().row(), target.position().column());
        Runnable firstCompletion = view.completion;
        check(firstCompletion != null, "completion test starts a real explosion animation");
        firstCompletion.run();
        int results = view.results;
        renders = view.renders;
        firstCompletion.run();
        check(view.renders == renders && view.results == results, "duplicate completion delivers no second render or result");

        controller.restart(Difficulty.NORMAL);
        target = engine.snapshot().cells().stream().filter(c -> c.mineType() == MineType.NORMAL).findFirst().orElseThrow();
        approach(engine, target.position());
        controller.reveal(target.position().row(), target.position().column());
        Runnable currentCompletion = view.completion;
        GameSnapshot pending = engine.snapshot();
        firstCompletion.run();
        controller.endTurn();
        check(engine.snapshot() == pending, "old completion cannot release a newer animation input lock");
        currentCompletion.run();
        results = view.results;
        renders = view.renders;
        currentCompletion.run();
        check(view.renders == renders && view.results == results, "later animation also completes only once");

        controller.restart(Difficulty.NORMAL);
        target = engine.snapshot().cells().stream().filter(c -> c.mineType() == MineType.NORMAL).findFirst().orElseThrow();
        approach(engine, target.position());
        controller.reveal(target.position().row(), target.position().column());
        view.onRender = () -> {
            view.onRender = null;
            controller.restart(Difficulty.HARD);
        };
        results = view.results;
        view.completion.run();
        check(engine.snapshot().difficulty() == Difficulty.HARD && view.results == results,
                "restart during render prevents the previous session result from being shown");
        check(controller.elapsedSeconds() == engine.elapsedSeconds(), "controller clock reads the lightweight engine timer");
    }

    private static void invariants(GameSnapshot state) {
        check(state.hp() <= GameRules.MAX_HP, "HP never exceeds 100");
        check(state.cells().stream().filter(c -> c.mineType() == MineType.NORMAL).count() == state.normalMineCount(), "ordinary count matches board");
        check(state.cells().stream().filter(Cell::revealed).count() == state.revealedCount(), "revealed count matches board");
        check(state.cells().stream().filter(c -> c.mineType() == MineType.KING).count() == (state.kingAlive() ? 1 : 0), "king count and coordinate agree");
        if (state.status() == GameStatus.WON) {
            check(state.kingAlive() && state.remainingMineCount() == 1 && state.hp() > 0, "every victory has only a surviving king and positive HP");
        }
        if (!state.kingAlive()) check(state.status() == GameStatus.LOST, "a destroyed king always ends the game in defeat");
        if (state.kingAlive()) {
            Position king = state.kingPosition();
            check(king.row() >= state.size() - 5 && king.column() >= state.size() - 5
                    && state.cellAt(king.row(), king.column()).mineType() == MineType.KING, "king remains in moving area");
        }
        for (Cell cell : state.cells()) {
            int trueCount = 0;
            for (Position neighbor : around(state, cell.position())) if (state.cellAt(neighbor.row(), neighbor.column()).hasMine()) trueCount++;
            check(cell.adjacentMines() == trueCount, "all mine types count toward adjacent hint");
            check(cell.displayedNumber() >= 0 && cell.displayedNumber() <= 8, "hint range remains 0-8 under interference");
            if (cell.informerId() >= 0) {
                InformerZone zone = state.informerZones().get(cell.informerId());
                check(zone.contains(cell.position()) && cell.mineType() != MineType.KING, "persistent zone membership");
                if (zone.active() && !cell.hasMine()) {
                    int delta = cell.displayedNumber() - trueCount;
                    check(Math.abs(delta) == 1 || delta == 0 && (trueCount == 0 || trueCount == 8),
                            "active informer hint applies plus/minus one with clipping only at 0 and 8");
                }
                else check(cell.displayedNumber() == trueCount, "inactive informer hints are accurate");
            } else check(cell.displayedNumber() == trueCount, "accurate hints outside informer zones");
            check(!cell.revealed() || !cell.hasMine(), "no mine can spawn on revealed cell");
        }
        for (InformerZone zone : state.informerZones()) {
            check(insideZoneInterior(zone, zone.core()), "initial and regenerated cores never occupy the outermost ring");
            check(zone.active() == (zone.capturedTurn() < 0), "interference persists until deliberate core removal");
            check(state.cells().stream().filter(c -> c.informerId() == zone.id() && c.mineType() == MineType.INFORMER_CORE).count()
                    == (zone.active() ? 1 : 0), "active zone has exactly one core, inactive zone has none");
            check(zone.active() || !state.cellAt(zone.core().row(), zone.core().column()).hasMine(), "inactive zone has no core mine");
            check(zone.remainingMines() == state.cells().stream().filter(c -> c.informerId() == zone.id() && c.hasMine()).count(),
                    "remaining zone count includes retained mines after core removal");
        }
    }

    private static boolean insideZoneInterior(InformerZone zone, Position position) {
        return position.row() > zone.origin().row() && position.row() < zone.origin().row() + zone.size() - 1
                && position.column() > zone.origin().column() && position.column() < zone.origin().column() + zone.size() - 1;
    }

    private static int normalCapacity(GameSnapshot state, boolean[] revealed) {
        int count = 0;
        for (int i = 0; i < revealed.length; i++) {
            Cell cell = state.cells().get(i);
            if (!revealed[i] && cell.informerId() < 0 && cell.mineType() != MineType.KING
                    && !(cell.position().row() < 5 && cell.position().column() < 5)) count++;
        }
        return count;
    }

    private static Set<Position> expectedExplosion(GameSnapshot state, Position start) {
        Set<Position> hit = new LinkedHashSet<>();
        List<Position> area = new ArrayList<>(around(state, start));
        area.add(start);
        for (Position position : area) {
            Cell candidate = state.cellAt(position.row(), position.column());
            if (!candidate.hasMine() || candidate.mineType() == MineType.INFORMER_CORE && !position.equals(start)) continue;
            hit.add(position);
        }
        return hit;
    }

    private static void capture(GameEngine engine, int id) {
        Position target = engine.snapshot().informerZones().get(id).core();
        approach(engine, target);
        GameSnapshot before = engine.snapshot();
        check(engine.flag(target.row(), target.column()).accepted(), "core flag accepted");
        GameSnapshot after = engine.snapshot();
        check(after.hp() == Math.min(GameRules.MAX_HP, before.hp() + 1) && after.normalMineCount() == before.normalMineCount(), "informer flag HP and ordinary count");
        check(after.statistics().minesCleared() == before.statistics().minesCleared() + 1, "informer flag stats");
        check(after.revealedCount() == before.revealedCount(), "core removal never expands territory or reveals the target");
        check(after.informerZones().get(id).remainingMines() == before.informerZones().get(id).remainingMines() - 1,
                "core flag removes only one mine");
        check(!after.informerZones().get(id).active() && after.informerZones().get(id).capturedTurn() == before.turn(),
                "core flag records interference removal turn");
        GameSnapshot captured = engine.snapshot();
        check(captured.cells().stream().filter(c -> captured.informerZones().get(id).contains(c.position()))
                .allMatch(c -> c.informerId() == id && c.displayedNumber() == c.adjacentMines()),
                "core removal restores clues while retaining region ownership");
    }

    private static void approach(GameEngine engine, Position target) {
        GameSnapshot state = engine.snapshot();
        int size = state.size();
        int[] parent = new int[size * size];
        Arrays.fill(parent, -2);
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (Cell cell : state.cells()) if (cell.revealed()) {
            int index = cell.position().row() * size + cell.position().column();
            parent[index] = -1;
            queue.add(index);
        }
        int destination = target.row() * size + target.column();
        while (!queue.isEmpty() && parent[destination] == -2) {
            int current = queue.removeFirst();
            for (Position next : around(state, new Position(current / size, current % size))) {
                int index = next.row() * size + next.column();
                Cell cell = state.cellAt(next.row(), next.column());
                if (parent[index] != -2 || (cell.mineType() == MineType.KING || cell.mineType() == MineType.INFORMER_CORE)
                        && index != destination) continue;
                parent[index] = current;
                queue.add(index);
            }
        }
        check(parent[destination] != -2, "target reachable without clearing protected special mines");
        ArrayDeque<Integer> path = new ArrayDeque<>();
        for (int step = parent[destination]; step >= 0 && parent[step] != -1; step = parent[step]) path.addFirst(step);
        for (int step : path) {
            Cell next = engine.snapshot().cellAt(step / size, step % size);
            if (next.revealed()) continue;
            check(perform(engine, next).accepted() && engine.snapshot().status() == GameStatus.PLAYING, "legal route expansion");
            if (!engine.snapshot().cellAt(step / size, step % size).revealed()) {
                check(engine.reveal(step / size, step % size).accepted(), "explicitly explore a cleared informer on the route");
            }
        }
    }

    private static void expandTo(GameEngine engine, int desiredOpen) {
        for (int step = 0; step < engine.snapshot().cells().size(); step++) {
            GameSnapshot state = engine.snapshot();
            if (state.status() != GameStatus.PLAYING || state.revealedCount() >= desiredOpen) return;
            Cell next = accessible(state, c -> c.informerId() < 0 && c.mineType() != MineType.KING && !c.hasMine());
            if (next == null) next = accessible(state, c -> c.mineType() == MineType.NORMAL);
            if (next == null) return;
            check(perform(engine, next).accepted(), "expansion accepted");
        }
    }

    private static void clearExcept(GameEngine engine, Position keep) {
        int bound = engine.snapshot().cells().size() * 2;
        for (int step = 0; step < bound && engine.snapshot().remainingMineCount() > 2; step++) {
            GameSnapshot state = engine.snapshot();
            Cell next = accessible(state, c -> c.hasMine() && c.mineType() != MineType.KING && !c.position().equals(keep));
            if (next == null) next = accessible(state, c -> !c.hasMine());
            check(next != null && perform(engine, next).accepted() && engine.snapshot().status() == GameStatus.PLAYING,
                    "prepare king and one reserved mine through legal actions");
        }
        check(engine.snapshot().remainingMineCount() == 2
                && engine.snapshot().cellAt(keep.row(), keep.column()).hasMine(), "only the king and reserved mine remain");
    }

    private static void solve(GameEngine engine) {
        int bound = engine.snapshot().cells().size() * 2;
        for (int step = 0; step < bound && engine.snapshot().status() == GameStatus.PLAYING; step++) {
            GameSnapshot state = engine.snapshot();
            Cell next = accessible(state, c -> c.hasMine() && c.mineType() != MineType.KING);
            if (next == null) next = accessible(state, c -> c.mineType() != MineType.KING);
            check(next != null && perform(engine, next).accepted(), "oracle strategy proceeds via legal actions");
        }
        check(engine.snapshot().status() == GameStatus.WON, "oracle completes board");
    }

    private static ActionResult perform(GameEngine engine, Cell cell) {
        return cell.hasMine() ? engine.flag(cell.position().row(), cell.position().column())
                : engine.reveal(cell.position().row(), cell.position().column());
    }

    private static Cell accessible(GameSnapshot state, Predicate<Cell> predicate) {
        return state.cells().stream().filter(c -> canAct(state, c.position()) && predicate.test(c)).findFirst().orElse(null);
    }

    private static boolean canAct(GameSnapshot state, Position position) {
        return !state.cellAt(position.row(), position.column()).revealed() && around(state, position).stream()
                .anyMatch(p -> state.cellAt(p.row(), p.column()).revealed());
    }

    private static List<Position> around(GameSnapshot state, Position position) {
        List<Position> result = new ArrayList<>(8);
        for (int row = Math.max(0, position.row() - 1); row <= Math.min(state.size() - 1, position.row() + 1); row++) {
            for (int column = Math.max(0, position.column() - 1); column <= Math.min(state.size() - 1, position.column() + 1); column++) {
                if (row != position.row() || column != position.column()) result.add(new Position(row, column));
            }
        }
        return result;
    }

    private static GameEngine engine(Difficulty difficulty, long seed) { return new GameEngine(difficulty, new Random(seed), FIXED_CLOCK); }
    private static void expectImmutable(Runnable action) {
        try { action.run(); throw new AssertionError("mutable snapshot"); }
        catch (UnsupportedOperationException expected) { checks++; }
    }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
    private static void run(String name, CheckedTest test) throws Exception { test.run(); groups++; System.out.println("PASS " + name); }
    @FunctionalInterface private interface CheckedTest { void run() throws Exception; }
    private static final class FixedNoiseRandom extends Random {
        private static final long serialVersionUID = 1L;
        private final boolean increase;
        private FixedNoiseRandom(long seed, boolean increase) { super(seed); this.increase = increase; }
        @Override public boolean nextBoolean() { return increase; }
    }
    private static final class MutableClock extends Clock {
        private long seconds;
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return FIXED_CLOCK.instant().plusSeconds(seconds); }
    }
    private static final class FakeView implements GameView {
        private Runnable completion;
        private boolean cancelled;
        private int renders;
        private int results;
        private Runnable onRender;
        public void render(GameSnapshot state) { renders++; if (onRender != null) onRender.run(); }
        public void animateExplosions(List<Position> positions, Runnable onFinished) { completion = onFinished; }
        public void cancelAnimation() { cancelled = true; }
        public void showResult(ActionResult result, GameSnapshot state) { results++; }
    }
}
