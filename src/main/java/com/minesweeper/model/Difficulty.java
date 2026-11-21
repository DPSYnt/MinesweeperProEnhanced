package com.minesweeper.model;

import com.minesweeper.config.GameRules;

public enum Difficulty {
    NORMAL("普通", 18, 70, 5, 1.2, 1, 4),
    HARD("困难", 25, 160, 4, 1.5, 2, 6),
    HELL("地狱", 30, 270, 3, 1.8, 3, 8),
    NIGHTMARE("噩梦", 35, 400, 2, 2.0, 4, 10);

    private final String displayName;
    private final int totalInitialMines;
    private final int initialHp;
    private final double growthMultiplier;
    private final int damageMultiplier;
    private final int boardSize;
    private final int informerZoneSize;

    Difficulty(String displayName, int boardSize, int totalInitialMines, int initialHp,
               double growthMultiplier, int damageMultiplier, int informerZoneSize) {
        this.displayName = displayName;
        this.boardSize = boardSize;
        this.totalInitialMines = totalInitialMines;
        this.initialHp = initialHp;
        this.growthMultiplier = growthMultiplier;
        this.damageMultiplier = damageMultiplier;
        this.informerZoneSize = informerZoneSize;
    }

    public String displayName() { return displayName; }
    public int initialMines() { return totalInitialMines - GameRules.INFORMER_COUNT - 1; }
    public int initialOutsideMines() { return totalInitialMines - GameRules.INFORMER_COUNT * zoneMineCount() - 1; }
    public int initialHp() { return initialHp; }
    public double growthMultiplier() { return growthMultiplier; }
    public int damageMultiplier() { return damageMultiplier; }
    public int boardSize() { return boardSize; }
    public int informerZoneSize() { return informerZoneSize; }
    public int zoneMineCount() { return scaleMineCount(GameRules.BASE_INFORMER_MINES, boardSize); }
    public int totalInitialMines() { return totalInitialMines; }
    public double growthRate() { return GameRules.NORMAL_GROWTH_RATE * growthMultiplier; }

    private static int scaleMineCount(int count, int boardSize) {
        return (int) Math.round(count * (double) boardSize * boardSize * GameRules.MINE_DENSITY_FACTOR
                / (GameRules.BASE_BOARD_SIZE * GameRules.BASE_BOARD_SIZE));
    }

    @Override
    public String toString() { return displayName; }
}
