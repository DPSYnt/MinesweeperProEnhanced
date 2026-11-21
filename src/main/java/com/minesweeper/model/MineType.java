package com.minesweeper.model;

/** 一个格子只能有一种雷，避免国王雷与普通雷重叠及重复计数。 */
public enum MineType {
    NONE, NORMAL, INFORMER_CORE, KING
}
