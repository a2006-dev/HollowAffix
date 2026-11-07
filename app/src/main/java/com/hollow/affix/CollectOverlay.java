package com.hollow.affix;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * 坐标采集覆盖层：全屏透明 View，记录触摸点坐标。
 * 仿开发者选项的「指针位置」，但会保存到文件。
 * 支持：单击记录点 / 长按开始框选（记录矩形区域）
 */
@SuppressLint("ViewConstructor")
public class CollectOverlay extends View {

    public interface Listener {
        void onTap(int x, int y);
        void onRegion(Rect r);
    }

    private final Paint dotPaint = new Paint();
    private final Paint crossPaint = new Paint();
    private final Paint textPaint = new Paint();
    private final Paint regionPaint = new Paint();
    private final Paint regionStroke = new Paint();

    private final List<int[]> dots = new ArrayList<>();  // {x,y}
    private final List<Rect> regions = new ArrayList<>();

    private float downX, downY, curX, curY;
    private boolean down = false, dragging = false;
    private long downTime = 0;
    private Listener listener;

    private static final long LONG_PRESS_MS = 400;

    public CollectOverlay(Context ctx) {
        super(ctx);
        dotPaint.setColor(Color.parseColor("#FF3D7EFF"));
        dotPaint.setStyle(Paint.Style.STROKE);
        dotPaint.setStrokeWidth(3f);
        dotPaint.setAntiAlias(true);

        crossPaint.setColor(Color.parseColor("#FF3D7EFF"));
        crossPaint.setStrokeWidth(2f);
        crossPaint.setAntiAlias(true);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(34f);
        textPaint.setAntiAlias(true);
        textPaint.setShadowLayer(4f, 0f, 0f, Color.BLACK);

        regionPaint.setColor(Color.parseColor("#333D7EFF"));
        regionPaint.setStyle(Paint.Style.FILL);

        regionStroke.setColor(Color.parseColor("#FFFFD54F"));
        regionStroke.setStyle(Paint.Style.STROKE);
        regionStroke.setStrokeWidth(3f);
    }

    public void setListener(Listener l) { this.listener = l; }

    public void clear() {
        dots.clear();
        regions.clear();
        invalidate();
    }

    public int dotCount() { return dots.size(); }
    public int regionCount() { return regions.size(); }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);

        for (int[] d : dots) {
            c.drawCircle(d[0], d[1], 18f, dotPaint);
            c.drawLine(d[0] - 26, d[1], d[0] + 26, d[1], crossPaint);
            c.drawLine(d[0], d[1] - 26, d[0], d[1] + 26, crossPaint);
            c.drawText("(" + d[0] + "," + d[1] + ")", d[0] + 26, d[1] - 22, textPaint);
        }

        for (Rect r : regions) {
            c.drawRect(r, regionPaint);
            c.drawRect(r, regionStroke);
            c.drawText(r.left + "," + r.top + "  " + r.width() + "x" + r.height(),
                    r.left + 8, r.top - 8, textPaint);
        }

        // 拖动中的实时框
        if (down && dragging) {
            Rect r = makeRect(downX, downY, curX, curY);
            c.drawRect(r, regionPaint);
            c.drawRect(r, regionStroke);
            c.drawText(r.left + "," + r.top + "  " + r.width() + "x" + r.height(),
                    r.left + 8, r.top - 8, textPaint);
        }

        // 十字准星（跟随手指）
        if (down) {
            c.drawLine(0, curY, getWidth(), curY, crossPaint);
            c.drawLine(curX, 0, curX, getHeight(), crossPaint);
            c.drawText("x=" + (int) curX + "  y=" + (int) curY,
                    curX + 24, curY - 24, textPaint);
        }
    }

    private Rect makeRect(float x1, float y1, float x2, float y2) {
        return new Rect(Math.round(Math.min(x1, x2)), Math.round(Math.min(y1, y2)),
                Math.round(Math.max(x1, x2)), Math.round(Math.max(y1, y2)));
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        curX = e.getX();
        curY = e.getY();

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                down = true;
                dragging = false;
                downX = curX;
                downY = curY;
                downTime = System.currentTimeMillis();
                invalidate();
                return true;

            case MotionEvent.ACTION_MOVE:
                if (down && Math.hypot(curX - downX, curY - downY) > 40f) {
                    dragging = true;
                }
                invalidate();
                return true;

            case MotionEvent.ACTION_UP:
                down = false;
                if (dragging) {
                    Rect r = makeRect(downX, downY, curX, curY);
                    if (r.width() > 40 && r.height() > 40) {
                        regions.add(r);
                        if (listener != null) listener.onRegion(r);
                        Logger.appendTo("regions.txt",
                                String.format("区域\t%d\t%d\t%d\t%d\t宽%d\t高%d",
                                        r.left, r.top, r.right, r.bottom,
                                        r.width(), r.height()));
                    }
                } else {
                    int x = Math.round(downX), y = Math.round(downY);
                    dots.add(new int[]{x, y});
                    if (listener != null) listener.onTap(x, y);
                    Logger.appendTo("taps.txt",
                            String.format("点击\t%d\t%d", x, y));
                }
                dragging = false;
                invalidate();
                return true;

            case MotionEvent.ACTION_CANCEL:
                down = false;
                dragging = false;
                invalidate();
                return true;
        }
        return super.onTouchEvent(e);
    }
}