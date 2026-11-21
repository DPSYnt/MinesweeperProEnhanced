package com.minesweeper.service;

import com.minesweeper.config.GameRules;
import com.minesweeper.model.*;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/**
 * 纯 Java 游戏引擎，不依赖 AWT/Swing。一次操作同步完成全部规则结算，
 * 调用方串行使用；Swing 控制器在 EDT 上调用，测试可直接在无窗口环境运行。
 */
public final class GameEngine {
    private int size;
    private int cellCount;
    private int[][] neighbors;
    private int[] revealQueue;
    private GameSnapshot cachedSnapshot;

    private final Random random;
    private final Clock clock;
    private final AchievementService achievements = new AchievementService();
    private CellState[] board;
    private final ZoneState[] zones = new ZoneState[GameRules.INFORMER_COUNT];
    private final List<String> history = new ArrayList<>();
    private Difficulty difficulty;
    private GameStatus status;
    private GamePhase phase;
    private int hp;
    private int turn;
    private int kingIndex;
    private int totalMoves;
    private int correctFlags;
    private int minesCleared;
    private Instant startedAt;
    private Instant endedAt;
    private String outcome;

    public GameEngine(Difficulty difficulty) {
        this(difficulty, new Random(), Clock.systemUTC());
    }

    /** 可注入随机数和时钟，便于复现棋盘、统计时间与规则回归。 */
    public GameEngine(Difficulty difficulty, Random random, Clock clock) {
        this.random = Objects.requireNonNull(random, "random");
        this.clock = Objects.requireNonNull(clock, "clock");
        restart(difficulty);
    }

    public void restart(Difficulty difficulty) {
        this.difficulty = Objects.requireNonNull(difficulty, "difficulty");
        cachedSnapshot = null;
        size = difficulty.boardSize();
        cellCount = size * size;
        neighbors = createNeighbors();
        revealQueue = new int[cellCount];
        board = new CellState[cellCount];
        status = GameStatus.PLAYING;
        phase = GamePhase.PLAYER;
        hp = difficulty.initialHp();
        turn = 1;
        kingIndex = -1;
        totalMoves = correctFlags = minesCleared = 0;
        startedAt = clock.instant();
        endedAt = null;
        outcome = "";
        history.clear();
        for (int i = 0; i < cellCount; i++) board[i] = new CellState();
        placeInformerZones();
        moveKing(true);
        redistributeNormalMines(difficulty.initialOutsideMines());
        recomputeNumbers(false);
        revealInitialArea();
        log("新游戏开始，难度: " + difficulty.displayName());
    }

    private void revealInitialArea() {
        for (CellState cell : board) cell.revealed = false;
        for (int row = 0; row < GameRules.INITIAL_OPEN_SIZE; row++) {
            for (int column = 0; column < GameRules.INITIAL_OPEN_SIZE; column++) {
                revealSafeArea(index(row, column));
            }
        }
    }

    public ActionResult reveal(int row, int column) {
        ActionResult rejection = validateAction(row, column);
        if (rejection != null) return rejection;
        int index = index(row, column);
        CellState cell = board[index];
        Set<Achievement> before = achievements.unlocked();
        cachedSnapshot = null;
        totalMoves++;
        log("左键揭开 " + displayPosition(index));
        if (cell.mine != MineType.NONE) {
            List<Position> explosions = explode(index);
            if (kingIndex < 0) lose("国王雷被引爆，游戏失败。");
            else if (!checkHpLoss() && !checkOnlyKingRemains()) runEnvironment();
            return result(before, explosions);
        }
        revealSafeArea(index);
        return result(before, List.of());
    }

    public ActionResult flag(int row, int column) {
        if (inBounds(row, column) && board[index(row, column)].mine == MineType.NONE) return ActionResult.rejected("");
        ActionResult rejection = validateAction(row, column);
        if (rejection != null) return rejection;
        int index = index(row, column);
        CellState cell = board[index];
        if (cell.flagged || cell.mine == MineType.NONE) return ActionResult.rejected("");
        Set<Achievement> before = achievements.unlocked();
        cachedSnapshot = null;
        totalMoves++;
        log("右键标记 " + displayPosition(index));
        correctFlags++;
        int healing = hp < GameRules.MAX_HP ? 1 : 0;
        hp = Math.min(GameRules.MAX_HP, hp + 1);
        cell.flagged = true;
        // Only the core clears interference without granting additional territory.
        cell.revealed = cell.mine != MineType.INFORMER_CORE;
        int informerId = cell.informerId;
        boolean foundCore = cell.mine == MineType.INFORMER_CORE;
        boolean foundKing = cell.mine == MineType.KING;
        removeKingIfPresent(index);
        cell.mine = MineType.NONE;
        addClearedMines(1);
        if (foundCore) deactivateInformer(informerId);
        updateNumbersAfterRemoval(index);
        log("标记正确，HP +" + healing + (healing == 0 ? "（已达上限）" : ""));
        if (foundKing) lose("国王雷被排除，游戏失败。");
        else checkOnlyKingRemains();
        return result(before, List.of());
    }

    /** 直接清雷会同步降低数字；剩余数字为零时，等价于经典扫雷已标完周围地雷。 */
    public ActionResult chord(int row, int column) {
        if (!isPlaying()) return ActionResult.rejected("");
        if (!inBounds(row, column)) return ActionResult.rejected("格子超出棋盘范围。");
        int origin = index(row, column);
        CellState center = board[origin];
        if (!center.revealed) return ActionResult.rejected("请在已揭开的格子上使用辅助展开。");
        if (center.informerId >= 0 && zones[center.informerId].active) {
            return ActionResult.rejected("这个格子仍受内鬼干扰，排除该区核心后才能辅助展开。");
        }
        if (center.displayedNumber != 0) {
            return ActionResult.rejected("周围还剩 " + center.displayedNumber + " 颗雷；先右键排雷，数字归零后才能辅助展开。");
        }
        boolean hasUnknown = false;
        for (int next : neighbors[origin]) if (!board[next].revealed) hasUnknown = true;
        if (!hasUnknown) return ActionResult.rejected("周围没有待展开的格子。");
        Set<Achievement> before = achievements.unlocked();
        cachedSnapshot = null;
        totalMoves++;
        int previousCount = countRevealed();
        for (int next : neighbors[origin]) revealSafeArea(next);
        log("辅助展开 " + displayPosition(origin) + "，揭开 " + (countRevealed() - previousCount) + " 格");
        return result(before, List.of());
    }

    public ActionResult endTurn() {
        if (!isPlaying()) return ActionResult.rejected("");
        Set<Achievement> before = achievements.unlocked();
        cachedSnapshot = null;
        runEnvironment();
        return result(before, List.of());
    }

    public GameSnapshot snapshot() {
        long seconds = elapsedSeconds();
        if (cachedSnapshot != null) {
            if (cachedSnapshot.statistics().elapsedSeconds() == seconds) return cachedSnapshot;
            cachedSnapshot = createSnapshot(cachedSnapshot.cells(), cachedSnapshot.informerZones(),
                    cachedSnapshot.normalMineCount(), cachedSnapshot.revealedCount(), seconds);
            return cachedSnapshot;
        }
        List<Cell> cells = new ArrayList<>(cellCount);
        int normalCount = 0;
        int revealedCount = 0;
        int[] informerCounts = new int[zones.length];
        for (int i = 0; i < cellCount; i++) {
            CellState cell = board[i];
            cells.add(new Cell(position(i), cell.mine, cell.informerId,
                    cell.revealed, cell.flagged, cell.adjacentMines, cell.displayedNumber));
            if (cell.revealed) revealedCount++;
            if (cell.mine == MineType.NORMAL) normalCount++;
            if (cell.informerId >= 0 && cell.mine != MineType.NONE) informerCounts[cell.informerId]++;
        }
        List<InformerZone> informerZones = new ArrayList<>(zones.length);
        for (int id = 0; id < zones.length; id++) {
            ZoneState zone = zones[id];
            informerZones.add(new InformerZone(id, zone.origin, position(zone.coreIndex), difficulty.informerZoneSize(),
                    zone.active, zone.capturedTurn, informerCounts[id]));
        }
        cachedSnapshot = createSnapshot(cells, informerZones, normalCount, revealedCount, seconds);
        return cachedSnapshot;
    }

    /** 计时刷新无需创建棋盘快照；终局时间与完整快照共用同一计算。 */
    public long elapsedSeconds() {
        return Math.max(0, java.time.Duration.between(startedAt,
                endedAt == null ? clock.instant() : endedAt).getSeconds());
    }

    private GameSnapshot createSnapshot(List<Cell> cells, List<InformerZone> informerZones,
                                        int normalCount, int revealedCount, long seconds) {
        GameStatistics statistics = new GameStatistics(totalMoves, correctFlags, 0,
                minesCleared, achievements.lifetimeMinesCleared(), seconds);
        return new GameSnapshot(size, difficulty, status, phase, hp, turn,
                normalCount, revealedCount, kingIndex < 0 ? null : position(kingIndex), cells,
                informerZones, statistics, achievements.unlocked(), outcome);
    }

    public List<String> history() { return List.copyOf(history); }

    private ActionResult validateAction(int row, int column) {
        if (!isPlaying()) return ActionResult.rejected("");
        if (!inBounds(row, column)) return ActionResult.rejected("格子超出棋盘范围。");
        int index = index(row, column);
        if (board[index].revealed) {
            return ActionResult.rejected("");
        }
        for (int neighbor : neighbors[index]) {
            if (board[neighbor].revealed) return null;
        }
        return ActionResult.rejected("请从已揭开区域的边缘扩张：目标格必须与控制区相邻（包括斜角）。");
    }

    private void placeInformerZones() {
        ZonePair pair = chooseZonePair();
        Position[] origins = {pair.first, pair.second};
        for (int id = 0; id < zones.length; id++) {
            Position origin = origins[id];
            int length = difficulty.informerZoneSize();
            int core = index(origin.row() + 1 + random.nextInt(length - 2),
                    origin.column() + 1 + random.nextInt(length - 2));
            List<Integer> candidates = new ArrayList<>();
            for (int dr = 0; dr < length; dr++) {
                for (int dc = 0; dc < length; dc++) {
                    int i = index(origin.row() + dr, origin.column() + dc);
                    board[i].informerId = id;
                    if (i != core) candidates.add(i);
                }
            }
            Collections.shuffle(candidates, random);
            zones[id] = new ZoneState(origin, core);
            board[core].mine = MineType.INFORMER_CORE;
            for (int n = 0; n < difficulty.zoneMineCount() - 1; n++) {
                board[candidates.get(n)].mine = MineType.NORMAL;
            }
        }
    }

    private void moveKing(boolean initialPlacement) {
        if (!initialPlacement && kingIndex < 0) return;
        if (kingIndex >= 0) board[kingIndex].mine = MineType.NONE;
        List<Integer> candidates = new ArrayList<>();
        int start = size - GameRules.KING_ZONE_SIZE;
        for (int row = start; row < size; row++) {
            for (int column = start; column < size; column++) {
                int i = index(row, column);
                if (board[i].mine == MineType.NONE && !board[i].revealed) candidates.add(i);
            }
        }
        // 存活国王原本就在未知格，旧位置腾空后至少保留一个合法候选。
        kingIndex = candidates.get(random.nextInt(candidates.size()));
        board[kingIndex].mine = MineType.KING;
        board[kingIndex].flagged = false;
    }

    private void redistributeNormalMines(int requestedCount) {
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < cellCount; i++) {
            CellState cell = board[i];
            if (cell.informerId >= 0) continue;
            if (cell.mine == MineType.NORMAL) cell.mine = MineType.NONE;
            int row = i / size;
            int column = i % size;
            if (cell.mine != MineType.NONE || cell.informerId >= 0 || cell.revealed) continue;
            if (row < GameRules.INITIAL_OPEN_SIZE && column < GameRules.INITIAL_OPEN_SIZE) continue;
            candidates.add(i);
        }
        Collections.shuffle(candidates, random);
        int actualCount = Math.min(requestedCount, candidates.size());
        for (int n = 0; n < actualCount; n++) {
            CellState cell = board[candidates.get(n)];
            cell.mine = MineType.NORMAL;
            cell.flagged = false;
        }
        log("普通雷分布: " + actualCount + " 颗");
    }

    private void recomputeNumbers(boolean refreshNoise) {
        for (int i = 0; i < cellCount; i++) {
            CellState cell = board[i];
            int count = 0;
            for (int neighbor : neighbors[i]) {
                if (board[neighbor].mine != MineType.NONE) count++;
            }
            cell.adjacentMines = count;
            updateDisplayedNumber(cell, refreshNoise);
        }
    }

    /** 单颗排雷只改变八邻格的真实雷数，不重新扫描整张棋盘。 */
    private void updateNumbersAfterRemoval(int removed) {
        updateDisplayedNumber(board[removed], false);
        for (int neighbor : neighbors[removed]) {
            CellState cell = board[neighbor];
            cell.adjacentMines--;
            updateDisplayedNumber(cell, false);
        }
    }

    private void updateDisplayedNumber(CellState cell, boolean refreshNoise) {
        if (cell.informerId >= 0 && zones[cell.informerId].active && cell.mine == MineType.NONE) {
            if (refreshNoise || cell.noise == 0) cell.noise = random.nextBoolean() ? 1 : -1;
            cell.displayedNumber = Math.max(0, Math.min(8, cell.adjacentMines + cell.noise));
        } else {
            cell.noise = 0;
            cell.displayedNumber = cell.adjacentMines;
        }
    }

    /** 入队时即标记，保证每个格子至多入队一次，空间和时间均为 O(格子数)。 */
    private void revealSafeArea(int start) {
        if (board[start].revealed || board[start].mine != MineType.NONE) return;
        int head = 0;
        int tail = 0;
        board[start].revealed = true;
        board[start].flagged = false;
        revealQueue[tail++] = start;
        while (head < tail) {
            int current = revealQueue[head++];
            CellState center = board[current];
            if (center.adjacentMines != 0 || center.informerId >= 0 && zones[center.informerId].active) continue;
            for (int next : neighbors[current]) {
                CellState cell = board[next];
                if (!cell.revealed && cell.mine == MineType.NONE) {
                    cell.revealed = true;
                    cell.flagged = false;
                    revealQueue[tail++] = next;
                }
            }
        }
    }

    private List<Position> explode(int start) {
        int informerId = board[start].mine == MineType.INFORMER_CORE ? board[start].informerId : -1;
        Set<Integer> hit = new LinkedHashSet<>();
        hit.add(start);
        for (int next : neighbors[start]) {
            if (board[next].mine != MineType.NONE && board[next].mine != MineType.INFORMER_CORE) hit.add(next);
        }
        for (int i : hit) {
            removeKingIfPresent(i);
            board[i].mine = MineType.NONE;
            board[i].flagged = false;
        }
        int count = hit.size();
        addClearedMines(count);
        hp -= count * difficulty.damageMultiplier();
        if (count >= 5) achievements.unlock(Achievement.CHAIN_REACTION);
        log("爆炸清除 " + count + " 颗雷，HP -" + count * difficulty.damageMultiplier());
        if (informerId >= 0) respawnInformer(informerId, start);
        // HP 耗尽时不会进入环境阶段，仍需更新邻雷数。
        recomputeNumbers(false);
        return hit.stream().map(this::position).toList();
    }

    private void removeKingIfPresent(int index) {
        if (board[index].mine != MineType.KING) return;
        kingIndex = -1;
        log("国王雷已清除");
    }

    private void respawnInformer(int id, int previous) {
        ZoneState zone = zones[id];
        List<Integer> candidates = new ArrayList<>();
        for (int row = zone.origin.row() + 1; row < zone.origin.row() + difficulty.informerZoneSize() - 1; row++) {
            for (int column = zone.origin.column() + 1; column < zone.origin.column() + difficulty.informerZoneSize() - 1; column++) {
                int i = index(row, column);
                if (i != previous && board[i].mine == MineType.NONE && !board[i].revealed) candidates.add(i);
            }
        }
        // The original cell remains unknown, so even a fully explored zone has a valid fallback.
        int destination = candidates.isEmpty() ? previous : candidates.get(random.nextInt(candidates.size()));
        board[destination].mine = MineType.INFORMER_CORE;
        board[destination].flagged = false;
        zone.coreIndex = destination;
        log("内鬼雷已在本区重新生成，干扰保留");
    }

    private void deactivateInformer(int id) {
        ZoneState zone = zones[id];
        if (!zone.active) return;
        zone.active = false;
        int length = difficulty.informerZoneSize();
        for (int row = zone.origin.row(); row < zone.origin.row() + length; row++) {
            for (int column = zone.origin.column(); column < zone.origin.column() + length; column++) {
                updateDisplayedNumber(board[index(row, column)], false);
            }
        }
        zone.capturedTurn = turn;
        log("内鬼区 " + (id + 1) + " 核心已清除，干扰解除，剩余地雷保留；获得持续更新的国王坐标情报");
        boolean sameTurn = Arrays.stream(zones).allMatch(z -> !z.active && z.capturedTurn == turn);
        if (sameTurn) achievements.unlock(Achievement.INFORMER_MASTER);
    }

    private void runEnvironment() {
        phase = GamePhase.ENVIRONMENT;
        log("环境变化开始");
        // 先收集边缘，后统一收缩，避免遍历顺序导致整片区域连续消失。
        List<Integer> boundary = new ArrayList<>();
        for (int i = 0; i < cellCount; i++) {
            if (!board[i].revealed) continue;
            for (int neighbor : neighbors[i]) {
                if (!board[neighbor].revealed) {
                    boundary.add(i);
                    break;
                }
            }
        }
        for (int i : boundary) {
            board[i].revealed = false;
            board[i].flagged = false;
        }
        moveKing(false);
        int currentCount = countNormalMines();
        int growth = (int) Math.floor(currentCount * GameRules.NORMAL_GROWTH_RATE
                * difficulty.growthMultiplier());
        redistributeNormalMines(currentCount + growth);
        recomputeNumbers(true);
        log("视野收缩 " + boundary.size() + " 格，普通雷计划增殖 " + growth + " 颗");
        if (countRevealed() < GameRules.MIN_CONTROLLED_CELLS) {
            lose("控制区（已揭开格数）小于 " + GameRules.MIN_CONTROLLED_CELLS + "。");
            return;
        }
        if (checkOnlyKingRemains()) return;
        turn++;
        phase = GamePhase.PLAYER;
        log("行动阶段开始");
    }

    private void addClearedMines(int count) {
        minesCleared += count;
        achievements.addClearedMines(count);
    }

    private boolean checkHpLoss() {
        if (hp > 0) return false;
        lose("生命值 HP 已耗尽。");
        return true;
    }

    private boolean checkOnlyKingRemains() {
        if (kingIndex < 0 || status != GameStatus.PLAYING) return false;
        for (CellState cell : board) {
            if (cell.mine != MineType.NONE && cell.mine != MineType.KING) return false;
        }
        achievements.unlock(Achievement.KING_GUARDIAN);
        win("其他地雷已全部清除，场上仅剩国王雷，游戏胜利！");
        return true;
    }

    private void win(String message) {
        achievements.checkWin(turn, hp, difficulty, minesCleared == correctFlags);
        finish(GameStatus.WON, message);
    }

    private void lose(String message) { finish(GameStatus.LOST, message); }

    private void finish(GameStatus finalStatus, String message) {
        status = finalStatus;
        phase = GamePhase.FINISHED;
        endedAt = clock.instant();
        outcome = message;
        log((finalStatus == GameStatus.WON ? "游戏胜利: " : "游戏失败: ") + message);
    }

    private ActionResult result(Set<Achievement> before, List<Position> explosions) {
        if (status != GameStatus.LOST) achievements.checkProgress(correctFlags, hp, countRevealed(), cellCount);
        Set<Achievement> newlyUnlocked = EnumSet.noneOf(Achievement.class);
        newlyUnlocked.addAll(achievements.unlocked());
        newlyUnlocked.removeAll(before);
        return new ActionResult(true, outcome, explosions, newlyUnlocked);
    }

    private boolean isPlaying() {
        return status == GameStatus.PLAYING && phase == GamePhase.PLAYER;
    }

    private int countNormalMines() {
        int count = 0;
        for (CellState cell : board) if (cell.mine == MineType.NORMAL && cell.informerId < 0) count++;
        return count;
    }

    private int countRevealed() {
        int count = 0;
        for (CellState cell : board) if (cell.revealed) count++;
        return count;
    }

    private String displayPosition(int index) { return "第" + (index / size + 1) + "行，第" + (index % size + 1) + "列"; }
    private void log(String message) { history.add("第 " + turn + " 回合 - " + message); }
    private int index(int row, int column) { return row * size + column; }
    private Position position(int index) { return new Position(index / size, index % size); }
    private boolean inBounds(int row, int column) {
        return row >= 0 && row < size && column >= 0 && column < size;
    }
    private int[][] createNeighbors() {
        int[][] neighbors = new int[cellCount][];
        for (int i = 0; i < cellCount; i++) {
            int[] buffer = new int[8];
            int count = 0;
            for (int dr = -1; dr <= 1; dr++) {
                for (int dc = -1; dc <= 1; dc++) {
                    int row = i / size + dr;
                    int column = i % size + dc;
                    if ((dr != 0 || dc != 0) && inBounds(row, column)) {
                        buffer[count++] = index(row, column);
                    }
                }
            }
            neighbors[i] = Arrays.copyOf(buffer, count);
        }
        return neighbors;
    }

    /** 有界洗牌后选取兼容区域，无随机重试，也不构建O(n^4)区域对列表。 */
    private ZonePair chooseZonePair() {
        List<Position> candidates = new ArrayList<>();
        int length = difficulty.informerZoneSize();
        int kingStart = size - GameRules.KING_ZONE_SIZE;
        for (int row = 0; row <= size - length; row++) {
            for (int column = 0; column <= size - length; column++) {
                if (row < 10 && column < 10) continue;
                if (row + length > kingStart && column + length > kingStart) continue;
                candidates.add(new Position(row, column));
            }
        }
        Collections.shuffle(candidates, random);
        for (int i = 0; i < candidates.size(); i++) {
            for (int j = i + 1; j < candidates.size(); j++) {
                Position a = candidates.get(i);
                Position b = candidates.get(j);
                if (a.row() + length < b.row() || b.row() + length < a.row()
                        || a.column() + length < b.column() || b.column() + length < a.column()) {
                    return new ZonePair(a, b);
                }
            }
        }
        throw new IllegalStateException("规则参数无法放置两个内鬼区");
    }

    private record ZonePair(Position first, Position second) { }

    private static final class CellState {
        private MineType mine = MineType.NONE;
        private int informerId = -1;
        private boolean revealed;
        private boolean flagged;
        private int adjacentMines;
        private int displayedNumber;
        private int noise;
    }

    private static final class ZoneState {
        private final Position origin;
        private int coreIndex;
        private boolean active = true;
        private int capturedTurn = -1;

        private ZoneState(Position origin, int coreIndex) {
            this.origin = origin;
            this.coreIndex = coreIndex;
        }
    }
}
