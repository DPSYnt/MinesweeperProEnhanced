package com.minesweeper.model;

/** 每区唯一的内鬼雷即核心；直接引爆后在区内再生，active仅由正确排除解除。 */
public record InformerZone(int id, Position origin, Position core, int size,
                           boolean active, int capturedTurn, int remainingMines) {
    public boolean contains(Position position) {
        return position.row() >= origin.row()
                && position.row() < origin.row() + size
                && position.column() >= origin.column()
                && position.column() < origin.column() + size;
    }
}
