package com.minesweeper.controller;

import com.minesweeper.model.ActionResult;
import com.minesweeper.model.GameSnapshot;
import com.minesweeper.model.Position;
import java.util.List;

/** 控制器只依赖视图协议，可用无窗口视图测试动画期间的交互。 */
public interface GameView {
    void render(GameSnapshot snapshot);
    void animateExplosions(List<Position> positions, Runnable onFinished);
    void cancelAnimation();
    void showResult(ActionResult result, GameSnapshot snapshot);
}
