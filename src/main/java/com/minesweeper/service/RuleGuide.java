package com.minesweeper.service;

import com.minesweeper.model.Difficulty;
import java.util.List;
import java.util.Locale;

/** 桌面端与手机端共用的规则正文；各界面仅补充对应的操作方式。 */
public final class RuleGuide {
    public record Section(String title, String text) { }

    public static final List<Section> BASIC_RULES = List.of(
            new Section("目标与胜负", "保留国王雷，清除其他全部地雷，场上仅剩国王雷时获胜，无须揭开全部安全格。"
                    + "提前引爆或排除国王雷，立即失败。"
                    + "生命值（HP）耗尽立即失败；环境变化后，控制区少于 20 格也判定失败。"),
            new Section("探索与排雷", "开局自动揭开左上 5×5 安全区，地雷随机分布，不保证每一步均可由数字推断。"
                    + "已揭开格组成控制区，仅可操作与控制区八方向相邻的未知格，范围以绿色边缘标示。"
                    + "数字表示周围八格的地雷总数，无干扰的零值格自动展开。\n"
                    + "正确排雷清除目标并恢复 1 HP，HP 上限为 100；除核心雷外，同时揭开该格。对未揭开的安全格执行排雷无响应，不扣血、不计行动、不触发环境变化。"),
            new Section("爆炸与回合", "探索地雷将引爆以该格为中心的 3×3 范围内地雷，不连锁扩散；按实际引爆雷数扣血。\n"
                    + "引爆地雷或选择“结束回合”后，若对局尚未结束，依次执行："
                    + "控制区边缘收缩一层、国王雷移动、普通雷增殖并重新分布、数字更新。"),
            new Section("辅助展开", "已揭开格的数字为 0 且无干扰时，可使用辅助展开，一次揭开相邻未知格。"));

    public static final List<Section> SPECIAL_RULES = List.of(
            new Section("国王雷", "每次环境变化时，国王雷在右下 5×5 区域内未揭开的无雷格中随机移动。"
                    + "国王雷必须保留至胜利；直接引爆、被周围爆炸波及或排除国王雷，均立即失败。"),
            new Section("内鬼区", "每局设两个内鬼区，每区在内部随机生成一颗内鬼雷（即核心），最外层一圈不生成内鬼雷；其余均为普通雷，可分布于全区。干扰未解除时，区内安全数字在真实邻雷数上随机加 1 或减 1，结果限制在 0～8（0−1 为 0，8+1 为 8）；"
                    + "开局和环境变化时重新随机。干扰中的 0 不触发自动或辅助展开。\n"
                    + "排除核心解除本区干扰、恢复真实数字并获得国王坐标；核心格保持未揭开，可另行探索，其余格子的地雷与揭开状态不变。"
                    + "内鬼雷不受周围爆炸波及；直接点中时按规则爆炸扣血，随后在本区内部的未知无雷格重新生成，仍避开最外层，干扰保留。区内普通雷正常参与爆炸和排除，但不随环境变化增殖、重排。"),
            new Section("坐标情报", "内鬼区 1 提供国王列号，内鬼区 2 提供行号，情报随国王移动更新。行号和列号均从 1 开始。"));

    public static final Section SESSION = new Section("对局管理", "重新开始沿用当前难度，进行中的对局须确认；终局也可重新选择难度。"
            + "返回菜单不暂停计时。成就与累计清雷仅在本次运行中保留，退出后不保存对局。");

    public static final String DIFFICULTY_NOTE = "总雷数包含全部普通雷、两颗内鬼雷和一颗国王雷；每区雷数含该区的一颗内鬼雷。"
            + "扣血值适用于每颗引爆地雷。区外普通雷按现存数量的比例增殖，新增量向下取整，并受可用位置限制。";

    private RuleGuide() { }

    public static String difficultySummary(Difficulty difficulty) {
        return String.format(Locale.ROOT, "%d×%d · 共%d颗雷 · %d HP · 扣血%d HP · 普通雷增殖%.0f%% · 内鬼区%d×%d，每区%d颗",
                difficulty.boardSize(), difficulty.boardSize(), difficulty.totalInitialMines(), difficulty.initialHp(),
                difficulty.damageMultiplier(), difficulty.growthRate() * 100,
                difficulty.informerZoneSize(), difficulty.informerZoneSize(), difficulty.zoneMineCount());
    }

}
