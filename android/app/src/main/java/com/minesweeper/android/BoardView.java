package com.minesweeper.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import com.minesweeper.model.Cell;
import com.minesweeper.model.GameSnapshot;
import com.minesweeper.model.GameStatus;
import com.minesweeper.model.MineType;
import com.minesweeper.model.Position;

/** Native zoomable touch board backed by the desktop game's immutable snapshots. */
final class BoardView extends View {
    interface Actions { void act(int row, int column, boolean flag, boolean chord); }
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final GestureDetector gestures;
    private final ScaleGestureDetector scaling;
    private final Actions actions;
    private GameSnapshot state;
    private float cellSize;
    private float offsetX;
    private float offsetY;
    private boolean flagMode;
    private boolean multiTouch;
    private boolean automatic = true;
    private Position pressed;
    private static final int[] NUMBERS = {0xff2874b3, 0xff167762, 0xffc95743, 0xff8c61b4,
            0xffa04f59, 0xff308994, 0xff1d3531, 0xff728480};

    BoardView(Context context, Actions actions) {
        super(context);
        this.actions = actions;
        setContentDescription("扫雷棋盘：行列从1开始；单击探索，长按排雷，双击已揭开格辅助展开，双指缩放，拖动查看");
        setFocusable(true);
        gestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent event) { pressed = at(event); invalidate(); return true; }
            @Override public boolean onSingleTapConfirmed(MotionEvent event) {
                if (!multiTouch) dispatch(event, flagMode, false);
                return true;
            }
            @Override public void onLongPress(MotionEvent event) {
                if (multiTouch || scaling.isInProgress()) return;
                performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                dispatch(event, true, false);
                pressed = null;
                invalidate();
            }
            @Override public boolean onDoubleTap(MotionEvent event) {
                Position position = at(event);
                if (position != null && state.cellAt(position.row(), position.column()).revealed()) {
                    dispatch(event, false, true);
                } else if (!multiTouch) dispatch(event, flagMode, false);
                return true;
            }
            @Override public boolean onScroll(MotionEvent first, MotionEvent second, float dx, float dy) {
                if (!scaling.isInProgress()) { offsetX -= dx; offsetY -= dy; clamp(); invalidate(); }
                pressed = null;
                return true;
            }
        });
        scaling = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector detector) {
                zoom(detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
                return true;
            }
        });
    }

    void render(GameSnapshot snapshot, boolean newGame) {
        state = snapshot;
        pressed = null;
        if (newGame || cellSize == 0) fit();
        invalidate();
    }

    void setFlagMode(boolean enabled) { flagMode = enabled; }
    void fit() { automatic = true; calculateFit(); invalidate(); }
    void zoom(float factor) { zoom(factor, getWidth() / 2f, getHeight() / 2f); }
    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }

    private void calculateFit() {
        if (state == null || getWidth() == 0 || getHeight() == 0) return;
        float gutter = dp(24);
        cellSize = Math.max(dp(14), Math.min(dp(42), Math.min(getWidth() - gutter, getHeight() - gutter) / state.size()));
        offsetX = gutter;
        offsetY = gutter;
        clamp();
    }

    private void zoom(float factor, float focusX, float focusY) {
        if (state == null || cellSize == 0) return;
        automatic = false;
        float previous = cellSize;
        cellSize = Math.max(dp(14), Math.min(dp(64), cellSize * factor));
        offsetX = focusX - (focusX - offsetX) * cellSize / previous;
        offsetY = focusY - (focusY - offsetY) * cellSize / previous;
        clamp();
        invalidate();
    }

    private void clamp() {
        if (state == null) return;
        float side = cellSize * state.size();
        float gutter = dp(24);
        offsetX = side <= getWidth() - gutter ? gutter + (getWidth() - gutter - side) / 2
                : Math.max(getWidth() - side, Math.min(gutter, offsetX));
        offsetY = side <= getHeight() - gutter ? gutter + (getHeight() - gutter - side) / 2
                : Math.max(getHeight() - side, Math.min(gutter, offsetY));
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        if (automatic) calculateFit(); else clamp();
    }

    private Position at(MotionEvent event) {
        if (state == null || cellSize == 0 || event.getX() < dp(24) || event.getY() < dp(24)) return null;
        int row = (int) Math.floor((event.getY() - offsetY) / cellSize);
        int column = (int) Math.floor((event.getX() - offsetX) / cellSize);
        return row < 0 || column < 0 || row >= state.size() || column >= state.size() ? null : new Position(row, column);
    }

    private void dispatch(MotionEvent event, boolean flag, boolean chord) {
        Position position = at(event);
        if (position == null || state.status() != GameStatus.PLAYING) return;
        actions.act(position.row(), position.column(), flag, chord);
        performClick();
    }

    @Override public boolean performClick() { super.performClick(); return true; }
    void cancelGesture() {
        MotionEvent cancelled = MotionEvent.obtain(android.os.SystemClock.uptimeMillis(),
                android.os.SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, 0, 0, 0);
        gestures.onTouchEvent(cancelled);
        scaling.onTouchEvent(cancelled);
        cancelled.recycle();
        pressed = null;
        invalidate();
    }
    @Override protected void onDetachedFromWindow() {
        cancelGesture();
        super.onDetachedFromWindow();
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) multiTouch = false;
        if (event.getPointerCount() > 1) multiTouch = true;
        scaling.onTouchEvent(event);
        gestures.onTouchEvent(event);
        if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            pressed = null;
            invalidate();
        }
        return true;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (state == null) return;
        boolean finished = state.status() != GameStatus.PLAYING;
        canvas.save();
        canvas.clipRect(dp(24), dp(24), getWidth(), getHeight());
        for (Cell cell : state.cells()) {
            Position p = cell.position();
            float x = offsetX + p.column() * cellSize;
            float y = offsetY + p.row() * cellSize;
            if (x + cellSize < 0 || y + cellSize < 0 || x > getWidth() || y > getHeight()) continue;
            boolean visible = cell.revealed() || finished;
            boolean kingZone = p.row() >= state.size() - 5 && p.column() >= state.size() - 5;
            boolean interfered = cell.informerId() >= 0 && state.informerZones().get(cell.informerId()).active();
            boolean frontier = !visible && isFrontier(p);
            int fill = visible ? 0xfff4f8f6 : 0xffdee7e3;
            if (kingZone) fill = visible ? 0xfffff9ea : 0xfff7edd2;
            else if (visible && interfered) fill = 0xfff3ecfb;
            else if (frontier) fill = 0xffd6eee1;
            if (p.equals(pressed)) fill = 0xffa9d6bf;
            rect.set(x + dp(1), y + dp(1), x + cellSize - dp(1), y + cellSize - dp(1));
            paint.setColor(fill);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(rect, dp(4), dp(4), paint);
            if (frontier) {
                paint.setColor(kingZone ? 0xffc4a14d : 0xff88baa0);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(0.7f));
                canvas.drawRoundRect(rect, dp(4), dp(4), paint);
                paint.setStyle(Paint.Style.FILL);
            }
            if (visible && cell.hasMine()) {
                paint.setColor(cell.mineType() == MineType.KING ? 0xffb78923 : cell.mineType() == MineType.INFORMER_CORE ? 0xff8c61b4 : 0xffc95743);
                canvas.drawCircle(x + cellSize / 2, y + cellSize / 2, cellSize * .2f, paint);
                paint.setStrokeWidth(dp(1.5f));
                canvas.drawLine(x + cellSize * .2f, y + cellSize / 2, x + cellSize * .8f, y + cellSize / 2, paint);
                canvas.drawLine(x + cellSize / 2, y + cellSize * .2f, x + cellSize / 2, y + cellSize * .8f, paint);
            } else if (!visible && cell.flagged()) {
                paint.setColor(0xff167762);
                paint.setStrokeWidth(Math.max(dp(1.5f), cellSize * .07f));
                canvas.drawLine(x + cellSize * .3f, y + cellSize * .5f, x + cellSize * .45f, y + cellSize * .65f, paint);
                canvas.drawLine(x + cellSize * .45f, y + cellSize * .65f, x + cellSize * .7f, y + cellSize * .35f, paint);
            } else if (visible && (cell.displayedNumber() != 0 || interfered)) {
                int number = cell.displayedNumber();
                paint.setColor(number == 0 ? 0xff8c61b4 : NUMBERS[number - 1]);
                paint.setTextSize(cellSize * .52f);
                paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(Integer.toString(cell.displayedNumber()), x + cellSize / 2,
                        y + cellSize / 2 - (paint.ascent() + paint.descent()) / 2, paint);
            } else if (frontier) {
                paint.setColor(0xff549974);
                canvas.drawCircle(x + cellSize / 2, y + cellSize / 2, dp(1), paint);
            }
        }
        canvas.restore();
        drawAxes(canvas);
    }

    /** Keep coordinate headers visible while the cells scroll and zoom underneath them. */
    private void drawAxes(Canvas canvas) {
        float gutter = dp(24);
        paint.setColor(0xff728480);
        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(android.graphics.Typeface.DEFAULT);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(Math.min(dp(11), cellSize * .65f));
        float baseline = -(paint.ascent() + paint.descent()) / 2;
        canvas.save();
        canvas.clipRect(gutter, 0, getWidth(), gutter);
        for (int column = 0; column < state.size(); column++) {
            float x = offsetX + (column + .5f) * cellSize;
            if (x >= gutter && x <= getWidth()) canvas.drawText(Integer.toString(column + 1), x, gutter / 2 + baseline, paint);
        }
        canvas.restore();
        canvas.save();
        canvas.clipRect(0, gutter, gutter, getHeight());
        for (int row = 0; row < state.size(); row++) {
            float y = offsetY + (row + .5f) * cellSize;
            if (y >= gutter && y <= getHeight()) canvas.drawText(Integer.toString(row + 1), gutter / 2, y + baseline, paint);
        }
        canvas.restore();
    }

    private boolean isFrontier(Position p) {
        for (int row = Math.max(0, p.row() - 1); row <= Math.min(state.size() - 1, p.row() + 1); row++) {
            for (int column = Math.max(0, p.column() - 1); column <= Math.min(state.size() - 1, p.column() + 1); column++) {
                if (state.cellAt(row, column).revealed()) return true;
            }
        }
        return false;
    }
}
