package com.minesweeper.model;

public enum Achievement {
    FIRST_WIN("首次胜利", "赢得第一场游戏"),
    PERFECT_WIN("完美胜利", "在不标记错误的情况下获胜"),
    QUICK_WIN("快速胜利", "在5回合内获胜"),
    MASTER_SWEEPER("扫雷大师", "累计清除100颗地雷"),
    KING_GUARDIAN("国王守护者", "保留国王雷并清除其他全部地雷获胜"),
    INFORMER_MASTER("内鬼大师", "在同一回合解除两个内鬼区的干扰"),
    SURVIVOR("幸存者", "以1点HP获胜"),
    CHAIN_REACTION("连锁反应", "一次引爆至少5颗地雷"),
    FIRST_DISARM("排雷初体验", "首次正确排除一颗非国王地雷"),
    DISARM_EXPERT("排雷能手", "单局正确排除50颗地雷"),
    VETERAN_SWEEPER("排雷老兵", "本次运行累计清除500颗地雷"),
    FULL_HEALTH("生命充盈", "生命值达到100 HP"),
    TERRITORY_EXPLORER("开拓者", "控制区达到棋盘面积的一半"),
    HELL_WIN("地狱征服者", "在地狱难度获胜"),
    NIGHTMARE_WIN("噩梦征服者", "在噩梦难度获胜"),
    UNHARMED_WIN("毫发无伤", "在没有引爆地雷的情况下获胜");

    private final String displayName;
    private final String description;

    Achievement(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String displayName() { return displayName; }
    public String description() { return description; }
}
