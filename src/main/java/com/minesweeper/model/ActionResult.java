package com.minesweeper.model;

import java.util.List;
import java.util.Set;

/** 规则先完整结算，再交由界面播放动画；动画不负责扣血或改变棋盘。 */
public record ActionResult(boolean accepted, String message, List<Position> explosions,
                            Set<Achievement> newAchievements) {
    public ActionResult {
        explosions = List.copyOf(explosions);
        newAchievements = Set.copyOf(newAchievements);
    }

    public static ActionResult rejected(String message) {
        return new ActionResult(false, message, List.of(), Set.of());
    }
}
