package com.minesweeper.model;

/** 对外只读的格子快照；真实邻雷数与受内鬼干扰的显示数字分开保存。 */
public record Cell(Position position, MineType mineType, int informerId,
                   boolean revealed, boolean flagged,
                   int adjacentMines, int displayedNumber) {
    public boolean hasMine() { return mineType != MineType.NONE; }
}
