package com.minesweeper.model;

import java.util.List;
import java.util.Set;

/** 完整的不可变快照，界面无需也不能直接修改引擎中的状态。 */
public record GameSnapshot(int size, Difficulty difficulty, GameStatus status, GamePhase phase,
                           int hp, int turn, int normalMineCount, int revealedCount,
                           Position kingPosition, List<Cell> cells, List<InformerZone> informerZones,
                           GameStatistics statistics, Set<Achievement> achievements, String outcome) {
    public GameSnapshot {
        cells = List.copyOf(cells);
        informerZones = List.copyOf(informerZones);
        achievements = Set.copyOf(achievements);
    }

    public Cell cellAt(int row, int column) {
        if (row < 0 || row >= size || column < 0 || column >= size) {
            throw new IndexOutOfBoundsException("格子超出棋盘范围: " + row + "," + column);
        }
        return cells.get(row * size + column);
    }

    public int remainingMineCount() {
        return (int) cells.stream().filter(Cell::hasMine).count();
    }

    public boolean kingAlive() { return kingPosition != null; }

}
