package com.minesweeper.ui;

import com.minesweeper.config.GameRules;
import com.minesweeper.model.GameSnapshot;
import com.minesweeper.model.GameStatus;
import com.minesweeper.model.Position;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** 可独立渲染的完整游戏面板，窗口和无窗口视觉检查共用同一实现。 */
@SuppressWarnings("serial")
public final class GamePanel extends JPanel {
    private final BoardPanel board;
    private final InfoPanel info;
    private final JLabel gameBadge = UiTheme.label("普通 / 回合 01", 12, UiTheme.ACCENT, true);
    private final BoardViewport coordinateBoard;
    private final JLabel brandTitle = UiTheme.label(GameRules.GAME_NAME, 27, UiTheme.TEXT, true);
    private final JLabel brandSubtitle = UiTheme.label("每一次扩张，都是新的机会。", 12, UiTheme.MUTED, false);
    private final JLabel boardSubtitle = UiTheme.label("清除其他全部地雷，保留国王雷", 11, UiTheme.MUTED, false);
    private final UiTheme.Card boardSurface;
    private boolean compactLayout;
    private GameSnapshot snapshot;

    public GamePanel(BoardPanel.Actions actions, Runnable onEndTurn, Runnable onRestart, Runnable onMenu,
                     Runnable onHelp, Runnable onRecords) {
        setBackground(UiTheme.BACKGROUND);
        setLayout(new BorderLayout(0, 18));
        setBorder(BorderFactory.createEmptyBorder(22, 24, 14, 24));
        setPreferredSize(new Dimension(1100, 850));
        board = new BoardPanel(new BoardPanel.Actions() {
            public void reveal(int row, int column) { actions.reveal(row, column); }
            public void flag(int row, int column) { actions.flag(row, column); }
            public void chord(int row, int column) { actions.chord(row, column); }
            public void hover(Position position, boolean actionable) {
                coordinateBoard.highlight(position);
            }
        });
        coordinateBoard = new BoardViewport(board);
        info = new InfoPanel(onEndTurn, onRestart);
        add(header(onMenu, onHelp, onRecords), BorderLayout.NORTH);
        JPanel workspace = new JPanel(new BorderLayout(18, 0));
        workspace.setOpaque(false);
        boardSurface = boardCard();
        workspace.add(boardSurface, BorderLayout.CENTER);
        workspace.add(info, BorderLayout.EAST);
        add(workspace, BorderLayout.CENTER);
    }

    private JPanel header(Runnable onMenu, Runnable onHelp, Runnable onRecords) {
        JPanel header = new JPanel(new BorderLayout(20, 0));
        header.setOpaque(false);
        JPanel brand = new JPanel(new BorderLayout(0, 5));
        brand.setOpaque(false);
        brand.add(brandTitle, BorderLayout.NORTH);
        brand.add(brandSubtitle, BorderLayout.SOUTH);
        header.add(brand, BorderLayout.WEST);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        buttons.setOpaque(false);
        gameBadge.setBorder(BorderFactory.createEmptyBorder(9, 12, 9, 12));
        gameBadge.setOpaque(true);
        gameBadge.setBackground(UiTheme.ACCENT_LIGHT);
        buttons.add(gameBadge);
        UiTheme.Button records = new UiTheme.Button("对局记录", false);
        records.addActionListener(event -> onRecords.run());
        buttons.add(records);
        UiTheme.Button help = new UiTheme.Button("规则与操作", false);
        help.addActionListener(event -> onHelp.run());
        buttons.add(help);
        UiTheme.Button restart = new UiTheme.Button("返回菜单", false);
        restart.addActionListener(event -> onMenu.run());
        buttons.add(restart);
        header.add(buttons, BorderLayout.EAST);
        return header;
    }

    private UiTheme.Card boardCard() {
        UiTheme.Card card = new UiTheme.Card();
        card.setLayout(new BorderLayout(0, 12));
        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        JPanel titles = new JPanel(new BorderLayout(0, 4));
        titles.setOpaque(false);
        titles.add(UiTheme.label("战术棋盘", 16, UiTheme.TEXT, true), BorderLayout.NORTH);
        titles.add(boardSubtitle, BorderLayout.SOUTH);
        heading.add(titles, BorderLayout.WEST);
        JPanel tools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        tools.setOpaque(false);
        tools.add(zoomButton("−", () -> coordinateBoard.zoom(-2)));
        tools.add(zoomButton("适应", coordinateBoard::fit));
        tools.add(zoomButton("+", () -> coordinateBoard.zoom(2)));
        heading.add(tools, BorderLayout.EAST);
        card.add(heading, BorderLayout.NORTH);
        card.add(coordinateBoard, BorderLayout.CENTER);
        JPanel legend = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 2));
        legend.setOpaque(false);
        legend.add(UiTheme.label("● 可扩张边缘", 11, UiTheme.ACCENT, false));
        legend.add(UiTheme.label("● 已揭开区域", 11, UiTheme.MUTED, false));
        legend.add(UiTheme.label("● 移动雷活动区", 11, UiTheme.GOLD, false));
        legend.add(UiTheme.label("● 内鬼干扰", 11, UiTheme.PURPLE, false));
        card.add(legend, BorderLayout.SOUTH);
        return card;
    }

    public void render(GameSnapshot snapshot) {
        if (this.snapshot == null || this.snapshot.size() != snapshot.size()
                || snapshot.turn() == 1 && snapshot.statistics().totalMoves() == 0) {
            coordinateBoard.highlight(null);
        }
        this.snapshot = snapshot;
        board.render(snapshot, snapshot.status() != GameStatus.PLAYING);
        coordinateBoard.renderSize(snapshot.size());
        info.render(snapshot);
        gameBadge.setText(snapshot.difficulty().displayName() + " / 回合 " + String.format("%02d", snapshot.turn()));
        boardSubtitle.setText(snapshot.size() + " × " + snapshot.size() + " / 清除其他雷，保留国王雷");
        updateTime(snapshot.statistics().elapsedSeconds());
    }

    public void updateTime(long seconds) { info.updateTime(seconds); }
    public void setInputEnabled(boolean enabled) {
        board.setInputEnabled(enabled);
        info.setInputEnabled(enabled);
    }
    public void cancelGesture() { board.cancelGesture(); }
    public void showExplosion(List<Position> positions, int frame) { board.showExplosion(positions, frame); }
    public GameSnapshot snapshot() { return snapshot; }

    private static javax.swing.JButton zoomButton(String text, Runnable action) {
        UiTheme.Button button = new UiTheme.Button(text, false);
        button.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        button.addActionListener(event -> action.run());
        return button;
    }

    @Override
    public void doLayout() {
        boolean compact = getHeight() < 760;
        if (compact != compactLayout) {
            compactLayout = compact;
            setBorder(BorderFactory.createEmptyBorder(compact ? 12 : 22, compact ? 16 : 24,
                    compact ? 10 : 14, compact ? 16 : 24));
            ((BorderLayout) getLayout()).setVgap(compact ? 10 : 18);
            brandTitle.setFont(UiTheme.font(compact ? 24 : 27, true));
            brandSubtitle.setVisible(!compact);
            boardSubtitle.setVisible(!compact);
            boardSurface.setBorder(BorderFactory.createEmptyBorder(compact ? 12 : 18, compact ? 12 : 18,
                    compact ? 12 : 18, compact ? 12 : 18));
            ((BorderLayout) boardSurface.getLayout()).setVgap(compact ? 8 : 12);
        }
        super.doLayout();
    }

}
