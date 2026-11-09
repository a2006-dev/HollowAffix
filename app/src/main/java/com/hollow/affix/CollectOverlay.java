package com.hollow.affix;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

@SuppressLint("ViewConstructor")
public class CollectOverlay extends View {

    public interface Listener {
        void onRegion(Rect r);
    }

    private static final int ZOOM = 3;
    private static final int LENS_SRC = 60;
    private static final int LENS_SIZE = LENS_SRC * ZOOM;

    private final Paint crossPaint = new Paint();
    private final Paint textPaint = new Paint();
    private final Paint regionPaint = new Paint();
    private final Paint regionStroke = new Paint();
    private final Paint lensBorder = new Paint();
    private final Paint lensBg = new Paint();

    private Bitmap snapshot;
    private RectF lensRect;

    private float downX, downY, curX, curY;
    private boolean down = false, dragging = false;
    private Listener listener;

    public CollectOverlay(Context ctx) {
        super(ctx);

        crossPaint.setColor(Color.parseColor("#FF3D7EFF"));
        crossPaint.setStrokeWidth(2f);
        crossPaint.setAntiAlias(true);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(30f);
        textPaint.setAntiAlias(true);
        textPaint.setShadowLayer(5f, 0f, 0f, Color.BLACK);

        regionPaint.setColor(Color.parseColor("#2A3D7EFF"));
        regionPaint.setStyle(Paint.Style.FILL);

        regionStroke.setColor(Color.parseColor("#FFFFD54F"));
        regionStroke.setStyle(Paint.Style.STROKE);
        regionStroke.setStrokeWidth(3f);
        regionStroke.setAntiAlias(true);

        lensBorder.setColor(Color.parseColor("#FF7C4DFF"));
        lensBorder.setStyle(Paint.Style.STROKE);
        lensBorder.setStrokeWidth(4f);
        lensBorder.setAntiAlias(true);

        lensBg.setColor(Color.parseColor("#FF0A0E14"));
        lensBg.setStyle(Paint.Style.FILL);
    }

    public void setSnapshot(Bitmap bmp) {

        if (snapshot != null && snapshot != bmp && !snapshot.isRecycled()) {
            snapshot.recycle();
        }
        this.snapshot = bmp;
        invalidate();
    }

    public void release() {
        if (snapshot != null && !snapshot.isRecycled()) {
            snapshot.recycle();
        }
        snapshot = null;
    }

    public void setListener(Listener l) { this.listener = l; }

    private Rect makeRect(float x1, float y1, float x2, float y2) {
        return new Rect(Math.round(Math.min(x1, x2)), Math.round(Math.min(y1, y2)),
                Math.round(Math.max(x1, x2)), Math.round(Math.max(y1, y2)));
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);

        if (down) {
            Rect r = makeRect(downX, downY, curX, curY);
            c.drawRect(r, regionPaint);
            c.drawRect(r, regionStroke);

            String size = r.width() + " x " + r.height();
            c.drawText(size, r.left + 8, r.top - 10, textPaint);

            int cx = r.centerX(), cy = r.centerY();
            String center = "(" + cx + ", " + cy + ")";
            c.drawText(center, r.left + 8, r.bottom + 34, textPaint);

            c.drawLine(0, curY, getWidth(), curY, crossPaint);
            c.drawLine(curX, 0, curX, getHeight(), crossPaint);

            drawLens(c);
        }
    }

    private void drawLens(Canvas c) {

        float lensX = getWidth() / 2f - LENS_SIZE / 2f;
        float lensY = 70f;
        lensRect = new RectF(lensX, lensY, lensX + LENS_SIZE, lensY + LENS_SIZE);

        c.drawRect(lensRect, lensBg);

        if (snapshot != null) {
            int half = LENS_SRC / 2;
            int sx = Math.round(curX) - half;
            int sy = Math.round(curY) - half;
            sx = Math.max(0, Math.min(sx, snapshot.getWidth() - LENS_SRC));
            sy = Math.max(0, Math.min(sy, snapshot.getHeight() - LENS_SRC));

            Rect src = new Rect(sx, sy, sx + LENS_SRC, sy + LENS_SRC);

            c.save();
            c.clipRect(lensRect);
            c.drawBitmap(snapshot, src, lensRect, null);
            c.restore();
        } else {

            Paint tp = new Paint(textPaint);
            tp.setTextSize(24f);
            tp.setTextAlign(Paint.Align.CENTER);
            c.drawText("快照加载中…", lensRect.centerX(),
                    lensRect.centerY() + 8, tp);
        }

        c.drawRect(lensRect, lensBorder);

        float ccx = lensRect.centerX();
        float ccy = lensRect.centerY();
        Paint p = new Paint(crossPaint);
        p.setColor(Color.parseColor("#FFFFD54F"));
        p.setStrokeWidth(3f);
        c.drawLine(ccx - 20, ccy, ccx + 20, ccy, p);
        c.drawLine(ccx, ccy - 20, ccx, ccy + 20, p);

        String coord = "(" + Math.round(curX) + ", " + Math.round(curY) + ")";
        c.drawText(coord, lensRect.left + 10, lensRect.bottom + 32, textPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {

        curX = e.getRawX();
        curY = e.getRawY();

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                down = true;
                dragging = false;
                downX = curX;
                downY = curY;
                invalidate();
                return true;

            case MotionEvent.ACTION_MOVE:
                if (down && Math.hypot(curX - downX, curY - downY) > 30f) {
                    dragging = true;
                }
                invalidate();
                return true;

            case MotionEvent.ACTION_UP:
                down = false;
                if (dragging) {
                    Rect r = makeRect(downX, downY, curX, curY);
                    if (r.width() > 15 && r.height() > 15) {
                        if (listener != null) listener.onRegion(r);
                        Logger.appendTo("region.txt",
                                String.format("框选\t%d\t%d\t%d\t%d\t宽%d\t高%d",
                                        r.left, r.top, r.right, r.bottom,
                                        r.width(), r.height()));
                    }
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
