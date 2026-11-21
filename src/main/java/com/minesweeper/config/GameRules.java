package com.minesweeper.config;

/** 与界面无关的游戏名称、难度换算和回合规则参数。 */
public final class GameRules {
    public static final String GAME_NAME = "MinesweeperEX";
    public static final int BASE_BOARD_SIZE = 20;
    public static final int INITIAL_OPEN_SIZE = 5;
    public static final int BASE_INFORMER_MINES = 10;
    public static final int INFORMER_COUNT = 2;
    public static final int KING_ZONE_SIZE = 5;
    public static final int MIN_CONTROLLED_CELLS = 20;
    public static final int MAX_HP = 100;
    public static final double MINE_DENSITY_FACTOR = 0.85;
    public static final double NORMAL_GROWTH_RATE = 0.10;

    private GameRules() { }
}
