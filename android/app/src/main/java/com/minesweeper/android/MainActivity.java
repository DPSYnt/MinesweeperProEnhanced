package com.minesweeper.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import com.minesweeper.model.ActionResult;
import com.minesweeper.model.Achievement;
import com.minesweeper.model.Difficulty;
import com.minesweeper.model.GameSnapshot;
import com.minesweeper.model.GameStatus;
import com.minesweeper.model.InformerZone;
import com.minesweeper.config.GameRules;
import com.minesweeper.service.GameEngine;
import com.minesweeper.service.RuleGuide;
import java.util.Locale;

/** Android presentation only; all mine placement, damage and win rules use the shared engine. */
public final class MainActivity extends Activity {
    static final int NORMAL_ID = 1002, HARD_ID = 1003, START_ID = 1004, HELL_ID = 1005, NIGHTMARE_ID = 1006;
    static final int BOARD_ID = 1010, MENU_ID = 1011, FLAG_ID = 1012, TURN_ID = 1013, RESUME_ID = 1014, RESTART_ID = 1015;
    private static final int GREEN = 0xff167762, TEXT = 0xff1d3531, MUTED = 0xff728480, BACKGROUND = 0xffeef4f1;
    private FrameLayout shell;
    private GameEngine engine;
    private Difficulty selected = Difficulty.NORMAL;
    private boolean inGame;
    private boolean flagMode;
    private TextView statistics;
    private TextView intelligence;
    private ProgressBar controlProgress;
    private TextView time;
    private BoardView board;
    private Button mode;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (inGame && engine != null && time != null) time.setText(formatTime(engine.elapsedSeconds()));
            handler.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        shell = new FrameLayout(this);
        shell.setBackgroundColor(BACKGROUND);
        shell.setPadding(dp(16), dp(16), dp(16), dp(12));
        shell.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(dp(16) + insets.getSystemWindowInsetLeft(), dp(12) + insets.getSystemWindowInsetTop(),
                    dp(16) + insets.getSystemWindowInsetRight(), dp(10) + insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(shell);
        showMenu();
    }

    @Override protected void onResume() { super.onResume(); handler.post(tick); }
    @Override protected void onPause() { handler.removeCallbacks(tick); super.onPause(); }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy(); }
    @Override public void onBackPressed() { if (inGame) showMenu(); else super.onBackPressed(); }

    private void showMenu() {
        inGame = false;
        shell.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = column();
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(label("MinesweeperEX", 32, GREEN, true));
        TextView subtitle = label("清除其他全部地雷，保留国王雷获胜。", 15, MUTED, false);
        subtitle.setPadding(0, dp(8), 0, dp(20));
        content.addView(subtitle);
        content.addView(label("选择难度", 18, TEXT, true));
        for (Difficulty difficulty : Difficulty.values()) {
            String text = (selected == difficulty ? "● " : "○ ") + difficulty.displayName()
                    + "   " + difficulty.boardSize() + "×" + difficulty.boardSize() + "   共" + difficulty.totalInitialMines() + "雷\n"
                    + difficulty.initialHp() + " HP · 增殖" + Math.round(difficulty.growthRate() * 100) + "% · 内鬼区"
                    + difficulty.informerZoneSize() + "×" + difficulty.informerZoneSize();
            Button choice = button(text, selected == difficulty, () -> { selected = difficulty; showMenu(); });
            choice.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            choice.setId(switch (difficulty) {
                case NORMAL -> NORMAL_ID;
                case HARD -> HARD_ID;
                case HELL -> HELL_ID;
                case NIGHTMARE -> NIGHTMARE_ID;
            });
            content.addView(choice, spaced());
        }
        boolean playing = engine != null && engine.snapshot().status() == GameStatus.PLAYING;
        if (playing) {
            Button resume = button("继续当前对局", true, () -> showGame(false));
            resume.setId(RESUME_ID);
            content.addView(resume, spaced());
        }
        Button start = button(playing ? "开始新游戏" : "开始游戏", !playing, this::startSelectedGame);
        start.setId(START_ID);
        content.addView(start, spaced());
        content.addView(button("规则与操作", false, this::showRules), spaced());
        if (engine != null) content.addView(button("对局记录", false, this::showRecords), spaced());
        content.addView(button("退出", false, this::finish), spaced());
        TextView note = label("离线游玩 · 单击探索 / 长按排雷\n双击已揭开格辅助 · 双指缩放 / 拖动查看", 12, MUTED, false);
        note.setPadding(0, dp(12), 0, 0);
        content.addView(note);
        scroll.addView(content);
        shell.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
    }

    private void startSelectedGame() {
        if (engine == null) engine = new GameEngine(selected); else engine.restart(selected);
        flagMode = false;
        showGame(true);
    }

    private void restartCurrentGame() {
        selected = engine.snapshot().difficulty();
        startSelectedGame();
    }

    private void requestRestart() {
        board.cancelGesture();
        if (engine.snapshot().status() != GameStatus.PLAYING) {
            restartCurrentGame();
            return;
        }
        new AlertDialog.Builder(this).setTitle("重新开始？")
                .setMessage("将按当前难度生成新棋盘，本局进度会被替换。")
                .setPositiveButton("重新开始", (dialog, which) -> restartCurrentGame())
                .setNegativeButton("继续对局", null).show();
    }

    private void chooseDifficulty() {
        selected = engine.snapshot().difficulty();
        showMenu();
    }

    private void showGame(boolean newGame) {
        inGame = true;
        shell.removeAllViews();
        LinearLayout content = column();
        LinearLayout heading = row();
        heading.addView(label("MinesweeperEX", 22, GREEN, true), new LinearLayout.LayoutParams(0, -2, 1));
        Button menu = button("菜单", false, this::showMenu);
        menu.setId(MENU_ID);
        heading.addView(menu);
        content.addView(heading);
        statistics = label("", 15, TEXT, true);
        statistics.setPadding(0, dp(8), 0, dp(8));
        content.addView(statistics);
        controlProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        controlProgress.setIndeterminate(false);
        controlProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(GREEN));
        controlProgress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xffdce7e1));
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(-1, dp(6));
        progressParams.bottomMargin = dp(8);
        content.addView(controlProgress, progressParams);
        intelligence = label("", 12, MUTED, false);
        content.addView(intelligence);
        board = new BoardView(this, this::act);
        board.setId(BOARD_ID);
        LinearLayout.LayoutParams boardParams = new LinearLayout.LayoutParams(-1, 0, 1);
        boardParams.topMargin = dp(8);
        boardParams.bottomMargin = dp(8);
        content.addView(board, boardParams);
        LinearLayout tools = row();
        mode = button(flagMode ? "模式：排雷" : "模式：探索", flagMode, () -> {
            flagMode = !flagMode;
            board.setFlagMode(flagMode);
            mode.setText(flagMode ? "模式：排雷" : "模式：探索");
            mode.setTextColor(flagMode ? GREEN : TEXT);
        });
        mode.setId(FLAG_ID);
        tools.addView(mode, new LinearLayout.LayoutParams(0, -2, 1));
        tools.addView(button("−", false, () -> board.zoom(.8f)));
        tools.addView(button("适应", false, board::fit));
        tools.addView(button("+", false, () -> board.zoom(1.25f)));
        content.addView(tools);
        time = label(formatTime(engine.elapsedSeconds()), 26, GREEN, true);
        time.setPadding(0, dp(6), 0, dp(6));
        content.addView(time);
        LinearLayout bottom = row();
        Button turn = button("结束回合", true, () -> apply(engine.endTurn()));
        turn.setId(TURN_ID);
        bottom.addView(turn, new LinearLayout.LayoutParams(0, -2, 1));
        Button restart = button("重新开始", false, this::requestRestart);
        restart.setId(RESTART_ID);
        bottom.addView(restart, new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(bottom);
        LinearLayout links = row();
        links.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        links.addView(button("规则", false, this::showRules));
        links.addView(button("记录", false, this::showRecords));
        content.addView(links);
        shell.addView(content, new FrameLayout.LayoutParams(-1, -1));
        board.setFlagMode(flagMode);
        refresh(newGame);
    }

    private void act(int row, int column, boolean flag, boolean chord) {
        if (!inGame) return;
        GameSnapshot state = engine.snapshot();
        if (state.cellAt(row, column).revealed() && !chord) return;
        ActionResult result = chord ? engine.chord(row, column) : flag ? engine.flag(row, column) : engine.reveal(row, column);
        apply(result);
    }

    private void apply(ActionResult result) {
        if (!result.accepted()) return;
        refresh(false);
        GameSnapshot state = engine.snapshot();
        if (state.status() != GameStatus.PLAYING) {
            boolean lost = state.status() == GameStatus.LOST;
            new AlertDialog.Builder(this).setTitle(lost ? "游戏失败" : "游戏胜利")
                    .setMessage(state.outcome() + "\n清雷 " + state.statistics().minesCleared() + " 颗 · " + formatTime(engine.elapsedSeconds()))
                    .setPositiveButton("重新开始", (dialog, which) -> restartCurrentGame())
                    .setNegativeButton("查看棋盘", null)
                    .setNeutralButton("重新选择难度", (dialog, which) -> chooseDifficulty()).show();
        }
    }

    private void refresh(boolean newGame) {
        GameSnapshot state = engine.snapshot();
        statistics.setText(state.difficulty().displayName() + " · 回合 " + state.turn() + "     HP：" + state.hp() + "/" + GameRules.MAX_HP + "\n"
                + "剩余 " + state.remainingMineCount() + " 雷     控制 " + state.revealedCount() + "/" + state.cells().size()
                + " · " + Math.round(100.0 * state.revealedCount() / state.cells().size()) + "%");
        controlProgress.setMax(state.cells().size());
        controlProgress.setProgress(state.revealedCount());
        controlProgress.setContentDescription("控制区域 " + state.revealedCount() + "/" + state.cells().size());
        StringBuilder clues = new StringBuilder();
        for (InformerZone zone : state.informerZones()) {
            if (clues.length() > 0) clues.append('\n');
            clues.append("内鬼").append(zone.id() + 1).append("：");
            if (zone.active()) clues.append("内鬼雷未排除 · 干扰中");
            else {
                clues.append("干扰解除 · ");
                if (!state.kingAlive()) clues.append("国王已清除");
                else if (zone.id() == 0) clues.append("国王列 X=").append(state.kingPosition().column() + 1);
                else clues.append("国王行 Y=").append(state.kingPosition().row() + 1);
            }
            clues.append(" · 余").append(zone.remainingMines()).append("雷");
        }
        intelligence.setText(clues.toString());
        board.render(state, newGame);
        Button turn = findViewById(TURN_ID);
        if (turn != null) {
            turn.setEnabled(state.status() == GameStatus.PLAYING);
        }
    }

    private void showRules() {
        StringBuilder rules = new StringBuilder();
        for (RuleGuide.Section section : RuleGuide.BASIC_RULES) appendRule(rules, section);
        appendRule(rules, new RuleGuide.Section("手机操作", "单击探索；长按排雷，或切换排雷模式后单击；双击已揭开格辅助展开。"
                + "拖动查看，双指缩放，也可使用 − / 适应 / +。"));
        for (RuleGuide.Section section : RuleGuide.SPECIAL_RULES) appendRule(rules, section);
        rules.append("难度配置\n\n");
        for (Difficulty difficulty : Difficulty.values()) {
            rules.append(difficulty.displayName()).append(' ').append(RuleGuide.difficultySummary(difficulty))
                    .append("\n\n");
        }
        rules.append(RuleGuide.DIFFICULTY_NOTE).append("\n\n");
        appendRule(rules, RuleGuide.SESSION);
        showText("规则与操作", rules.toString());
    }

    private static void appendRule(StringBuilder rules, RuleGuide.Section section) {
        rules.append(section.title()).append("\n\n").append(section.text()).append("\n\n");
    }

    private void showRecords() {
        if (engine == null) return;
        GameSnapshot state = engine.snapshot();
        String outcome = switch (state.status()) {
            case PLAYING -> "进行中";
            case WON -> "胜利";
            case LOST -> "失败";
        };
        StringBuilder text = new StringBuilder(state.difficulty().displayName() + " · " + outcome
                + "\n回合 " + state.turn() + " · " + formatTime(engine.elapsedSeconds())
                + "\n\n本局清雷 " + state.statistics().minesCleared() + " 颗"
                + "\n正确排雷 " + state.statistics().correctFlags()
                + "\n\n已解锁成就 " + state.achievements().size() + "/" + Achievement.values().length + "\n");
        if (state.achievements().isEmpty()) text.append("暂无");
        for (Achievement achievement : Achievement.values()) {
            if (state.achievements().contains(achievement)) text.append("✓ ").append(achievement.displayName()).append('\n');
        }
        showText("对局记录", text.toString());
    }

    private void showText(String title, String text) {
        ScrollView scroll = new ScrollView(this);
        TextView body = label(text, 15, TEXT, false);
        body.setPadding(dp(22), dp(12), dp(22), dp(12));
        body.setTextIsSelectable(true);
        body.setLineSpacing(dp(4), 1);
        scroll.addView(body);
        new AlertDialog.Builder(this).setTitle(title).setView(scroll).setPositiveButton("返回", null).show();
    }

    private LinearLayout column() { LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); return layout; }
    private LinearLayout row() { LinearLayout layout = new LinearLayout(this); layout.setGravity(Gravity.CENTER_VERTICAL); return layout; }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView label(String text, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text); view.setTextSize(size); view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }
    private Button button(String text, boolean primary, Runnable action) {
        Button button = new Button(this);
        button.setText(text); button.setAllCaps(false); button.setTextSize(14);
        button.setTextColor(primary ? Color.WHITE : TEXT);
        button.setMinHeight(dp(44)); button.setMinimumHeight(dp(44));
        button.setMinWidth(dp(40)); button.setMinimumWidth(dp(40));
        button.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable background = new GradientDrawable();
        background.setColor(primary ? GREEN : Color.WHITE);
        background.setCornerRadius(dp(10)); background.setStroke(dp(1), primary ? GREEN : 0xffdce7e1);
        android.content.res.ColorStateList ripple = android.content.res.ColorStateList.valueOf(0x33167762);
        button.setBackground(new android.graphics.drawable.RippleDrawable(ripple, background, null));
        button.setOnClickListener(view -> action.run());
        return button;
    }
    private LinearLayout.LayoutParams spaced() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(9); return params;
    }
    private static String formatTime(long seconds) { return String.format(Locale.ROOT, "用时 %02d:%02d", seconds / 60, seconds % 60); }

    // Package-private hooks used only by the separately built emulator instrumentation APK.
    GameEngine engineForTest() { return engine; }
}
