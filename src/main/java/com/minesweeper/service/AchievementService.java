package com.minesweeper.service;

import com.minesweeper.model.Achievement;
import com.minesweeper.model.Difficulty;
import com.minesweeper.config.GameRules;
import java.util.EnumSet;
import java.util.Set;

/** 当前程序会话中的成就和累计清雷数，重开单局时保留。 */
final class AchievementService {
    private final EnumSet<Achievement> unlocked = EnumSet.noneOf(Achievement.class);
    private int lifetimeMinesCleared;

    void addClearedMines(int count) {
        lifetimeMinesCleared += count;
        if (lifetimeMinesCleared >= 100) unlock(Achievement.MASTER_SWEEPER);
        if (lifetimeMinesCleared >= 500) unlock(Achievement.VETERAN_SWEEPER);
    }

    void checkWin(int turn, int hp, Difficulty difficulty, boolean noExplosions) {
        unlock(Achievement.FIRST_WIN);
        unlock(Achievement.PERFECT_WIN);
        if (turn <= 5) unlock(Achievement.QUICK_WIN);
        if (hp == 1) unlock(Achievement.SURVIVOR);
        if (noExplosions) unlock(Achievement.UNHARMED_WIN);
        if (difficulty == Difficulty.HELL) unlock(Achievement.HELL_WIN);
        if (difficulty == Difficulty.NIGHTMARE) unlock(Achievement.NIGHTMARE_WIN);
    }

    void checkProgress(int correctFlags, int hp, int revealed, int cells) {
        if (correctFlags >= 1) unlock(Achievement.FIRST_DISARM);
        if (correctFlags >= 50) unlock(Achievement.DISARM_EXPERT);
        if (hp == GameRules.MAX_HP) unlock(Achievement.FULL_HEALTH);
        if (revealed * 2 >= cells) unlock(Achievement.TERRITORY_EXPLORER);
    }

    void unlock(Achievement achievement) { unlocked.add(achievement); }
    int lifetimeMinesCleared() { return lifetimeMinesCleared; }
    Set<Achievement> unlocked() { return Set.copyOf(unlocked); }
}
