package com.minesweeper.model;

/** 行、列明确命名，避免 java.awt.Point 的 x/y 与棋盘坐标混用。 */
public record Position(int row, int column) { }
