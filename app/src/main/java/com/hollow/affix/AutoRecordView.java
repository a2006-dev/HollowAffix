package com.hollow.affix;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.List;

@SuppressLint("ViewConstructor")
public class AutoRecordView {

    public interface Listener {
        void onPointAdded(int count);
        void onSwipeChanged(int x1, int y1, int x2, int y2);
        void onPointFinished(AutoPoints data);
        void onSwipeFinished(AutoPoints data);
        void onCancelled();

        default void onMaskShown() {}
    }

    private final Context ctx;
    private final WindowManager wm;
    private final Listener listener;
    private final AutoPoints data;
    private final boolean pointMode;

    private MaskView mask;
    private SwipeBarView bar;
    private WindowManager.LayoutParams lp;

    public AutoRecordView(Context ctx, boolean pointMode, AutoPoints data, Listener l) {
        this.ctx = ctx;
        this.listener = l;
        this.pointMode = pointMode;
        this.data = data != null ? data : new AutoPoints();
        this.wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
    }

    public void start() {
        if (pointMode) showMask();
        else showBar();
    }

    private void showMask() {
        if (mask != null) return;
        mask = new MaskView(ctx);

        float inv = 1f / rawToPhysicalScale();
        for (AutoPoints.Point p : data.cells) {
            mask.addMark(Math.round(p.x * inv), Math.round(p.y * inv));
        }

        mask.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {

                if (data.cells.size() >= AutoPoints.CELLS_PER_PAGE) {
                    return true;
                }

                int rx = (int) e.getRawX();
                int ry = (int) e.getRawY();
                int x = toPhysicalX(rx);
                int y = toPhysicalY(ry);
                Logger.appendTo("points.txt", "录制 raw=(" + rx + "," + ry
                        + ") 存=(" + x + "," + y + ")");
                data.cells.add(new AutoPoints.Point(x, y));

                int vx = (int) e.getX();
                int vy = (int) e.getY();
                mask.addMark(vx, vy);
                if (listener != null) listener.onPointAdded(data.cells.size());

                if (data.cells.size() >= AutoPoints.CELLS_PER_PAGE) {
                    final MaskView m = mask;
                    if (m != null) m.postDelayed(this::finishPoints, 60);
                }
            }
            return true;
        });

        lp = new WindowManager.LayoutParams();
        lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        lp.format = PixelFormat.TRANSLUCENT;
        lp.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        lp.gravity = Gravity.TOP | Gravity.START;

        lp.x = 0;
        lp.y = 0;
        lp.width = WindowManager.LayoutParams.MATCH_PARENT;
        lp.height = WindowManager.LayoutParams.MATCH_PARENT;
        wm.addView(mask, lp);

        if (listener != null) listener.onMaskShown();
    }

    public void undo() {
        if (!pointMode) return;
        if (data.cells.isEmpty()) return;
        data.cells.remove(data.cells.size() - 1);
        if (mask != null) mask.removeLastMark();
        if (listener != null) listener.onPointAdded(data.cells.size());
    }

    public void clearPoints() {
        data.cells.clear();
        if (mask != null) mask.clearMarks();
        if (listener != null) listener.onPointAdded(0);
    }

    public void finishPoints() {
        Logger.appendTo("trace.txt", "finishPoints 被调用, 点位数=" + data.cells.size());
        if (data.cells.isEmpty()) return;
        data.save();
        stop();
        if (listener != null) listener.onPointFinished(data);
    }

    public boolean isFullPage() {
        return data.cells.size() == AutoPoints.CELLS_PER_PAGE;
    }

    public int pointCount() {
        return data.cells.size();
    }

    private void showBar() {
        if (bar != null) return;
        bar = new SwipeBarView(ctx);
        bar.setListener((x1, y1, x2, y2) -> {
            data.swipeX1 = x1;
            data.swipeY1 = y1;
            data.swipeX2 = x2;
            data.swipeY2 = y2;
            data.hasSwipe = true;
            if (listener != null) listener.onSwipeChanged(x1, y1, x2, y2);
        });

        bar.setWindowDragListener((nx, ny) -> {
            if (lp == null || bar == null) return;
            lp.x = nx;
            lp.y = ny;
            try {
                wm.updateViewLayout(bar, lp);
            } catch (Throwable ignored) {}
            syncCoords();
        });

        bar.setHeightChangeListener((topOff, botOff) -> syncCoords());

        lp = new WindowManager.LayoutParams();
        lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        lp.format = PixelFormat.TRANSLUCENT;
        lp.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        lp.gravity = Gravity.TOP | Gravity.START;

        int[] sz = screenSize();
        int ww = SwipeBarView.LINE_W + SwipeBarView.PAD * 2;
        int hh = sz[1];
        lp.width = ww;
        lp.height = hh;
        lp.x = (sz[0] - ww) / 2;
        lp.y = 0;

        bar.setWindowPos(lp.x, lp.y, lp.width, lp.height);
        wm.addView(bar, lp);
        bar.post(this::syncCoords);
    }

    private void syncCoords() {
        if (bar == null || lp == null || data == null) return;
        int[] c = bar.absoluteCoords(lp.x, lp.y, lp.width, lp.height);
        data.swipeX1 = c[0];
        data.swipeY1 = c[1];
        data.swipeX2 = c[0];
        data.swipeY2 = c[2];
        if (listener != null) listener.onSwipeChanged(c[0], c[1], c[0], c[2]);
    }

    private int[] screenSize() {
        return screenSize(ctx);
    }

    static int[] screenSize(Context c) {
        int w = 0, h = 0;

        try {
            android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
            WindowManager wm2 = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
            if (wm2 != null && wm2.getDefaultDisplay() != null) {
                wm2.getDefaultDisplay().getRealMetrics(dm);
                w = dm.widthPixels;
                h = dm.heightPixels;
            }
        } catch (Throwable ignored) {}

        if (w <= 0 || h <= 0) {
            android.util.DisplayMetrics dm = c.getResources().getDisplayMetrics();
            w = Math.max(dm.widthPixels, dm.heightPixels);
            h = Math.min(dm.widthPixels, dm.heightPixels);
        }

        return new int[]{Math.max(w, h), Math.min(w, h)};
    }

    private float rawToPhysicalScale() {
        try {
            android.util.DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
            int appLong = Math.max(dm.widthPixels, dm.heightPixels);
            int appShort = Math.min(dm.widthPixels, dm.heightPixels);
            int[] sz = screenSize();
            int physLong = Math.max(sz[0], sz[1]);
            int physShort = Math.min(sz[0], sz[1]);
            if (appLong > 0 && physLong > 0) {
                float s = (float) physLong / appLong;
                if (s > 1.001f || s < 0.999f) return s;
            }
            if (appShort > 0 && physShort > 0) {
                float s = (float) physShort / appShort;
                if (s > 1.001f || s < 0.999f) return s;
            }
        } catch (Throwable ignored) {}
        return 1f;
    }

    private int toPhysicalX(float rawX) {
        return Math.round(rawX * rawToPhysicalScale());
    }

    private int toPhysicalY(float rawY) {
        return Math.round(rawY * rawToPhysicalScale());
    }

    public void finishSwipe() {
        if (!data.hasSwipe) return;
        data.save();
        stop();
        if (listener != null) listener.onSwipeFinished(data);
    }

    public void resetBar() {
        if (bar != null) bar.reset();
    }

    public void cancel() {
        stop();
        if (listener != null) listener.onCancelled();
    }

    public void stop() {
        try {
            if (mask != null) wm.removeViewImmediate(mask);
        } catch (Throwable t) {
            Logger.appendTo("trace.txt", "移除蒙版失败: " + t);
        }
        try {
            if (bar != null) wm.removeViewImmediate(bar);
        } catch (Throwable t) {
            Logger.appendTo("trace.txt", "移除滑条失败: " + t);
        }
        mask = null;
        bar = null;
    }

    public AutoPoints data() {
        return data;
    }

    private static class MaskView extends View {
        private final Paint dim = new Paint();
        private final Paint dot = new Paint();
        private final Paint ring = new Paint();
        private final Paint textPaint = new Paint();
        private final List<int[]> marks = new ArrayList<>();

        MaskView(Context c) {
            super(c);
            dim.setColor(0x40000000);
            dot.setColor(0xFF4ADE80);
            dot.setAntiAlias(true);
            ring.setColor(0xFF4ADE80);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(3f);
            ring.setAntiAlias(true);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(28f);
            textPaint.setAntiAlias(true);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setShadowLayer(4f, 0f, 0f, Color.BLACK);
        }

        void addMark(int x, int y) {
            marks.add(new int[]{x, y});
            invalidate();
        }

        void removeLastMark() {
            if (!marks.isEmpty()) marks.remove(marks.size() - 1);
            invalidate();
        }

        void clearMarks() {
            marks.clear();
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            c.drawRect(0, 0, getWidth(), getHeight(), dim);

            for (int i = 0; i < marks.size(); i++) {
                int[] p = marks.get(i);
                c.drawCircle(p[0], p[1], 22f, dot);
                c.drawCircle(p[0], p[1], 30f, ring);
                c.drawText(String.valueOf(i + 1), p[0], p[1] + 10f, textPaint);
            }
        }
    }

    private static class SwipeBarView extends View {

        interface Listener {
            void onChange(int x1, int y1, int x2, int y2);
        }

        interface WindowDragListener {
            void onDrag(int newX, int newY);
        }

        interface HeightChangeListener {
            void onHeight(int topOffset, int botOffset);
        }

        static final int LINE_W = 300;
        static final int HANDLE_R = 26;
        static final int PAD = 12;

        private final Paint lineTop = new Paint();
        private final Paint lineBottom = new Paint();
        private final Paint handle = new Paint();
        private final Paint vline = new Paint();
        private final Paint label = new Paint();

        private int winX, winY, winW, winH;

        private int topOff = 0;

        private int botOff = 0;

        private int dragMode = 0;
        private float downRawX, downRawY;
        private int origWinX, origWinY;
        private int origTopOff, origBotOff;

        private Listener listener;
        private WindowDragListener dragListener;
        private HeightChangeListener heightListener;

        SwipeBarView(Context c) {
            super(c);

            lineTop.setColor(0xFFFFB300);
            lineTop.setStrokeWidth(9f);
            lineTop.setAntiAlias(true);

            lineBottom.setColor(0xFF4ADE80);
            lineBottom.setStrokeWidth(9f);
            lineBottom.setAntiAlias(true);

            vline.setColor(0xAA4ADE80);
            vline.setStrokeWidth(5f);
            vline.setAntiAlias(true);

            handle.setColor(0xFFFFFFFF);
            handle.setAntiAlias(true);

            label.setColor(Color.WHITE);
            label.setTextSize(24f);
            label.setAntiAlias(true);
            label.setTextAlign(Paint.Align.CENTER);
            label.setShadowLayer(5f, 0f, 0f, Color.BLACK);
        }

        void setListener(Listener l) { this.listener = l; }
        void setWindowDragListener(WindowDragListener l) { this.dragListener = l; }
        void setHeightChangeListener(HeightChangeListener l) { this.heightListener = l; }

        void setWindowPos(int x, int y, int w, int h) {
            winX = x;
            winY = y;
            winW = w;
            winH = h;

            topOff = Math.max(HANDLE_R + PAD, (int) (h * 0.15f));
            botOff = Math.min(h - HANDLE_R - PAD, (int) (h * 0.85f));
            invalidate();
        }

        int[] absoluteCoords(int wx, int wy, int ww, int wh) {
            int centerX = wx + ww / 2;
            int topAbs = wy + topOff;
            int botAbs = wy + botOff;

            int[] sz = screenSizeOf(getContext());
            int maxY = sz[1] - 1;
            if (topAbs < 0) topAbs = 0;
            if (topAbs > maxY) topAbs = maxY;
            if (botAbs < 0) botAbs = 0;
            if (botAbs > maxY) botAbs = maxY;
            if (centerX < 0) centerX = 0;
            if (centerX > sz[0] - 1) centerX = sz[0] - 1;
            return new int[]{centerX, topAbs, botAbs};
        }

        private int[] screenSizeOf(android.content.Context c) {
            return AutoRecordView.screenSize(c);
        }

        void reset() {
            topOff = Math.max(HANDLE_R + PAD, (int) (winH * 0.15f));
            botOff = Math.min(winH - HANDLE_R - PAD, (int) (winH * 0.85f));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            float lx = getWidth() / 2f;

            c.drawLine(lx, topOff, lx, botOff, vline);

            c.drawLine(lx - LINE_W / 2f, topOff, lx + LINE_W / 2f, topOff, lineTop);

            c.drawLine(lx - LINE_W / 2f, botOff, lx + LINE_W / 2f, botOff, lineBottom);

            c.drawCircle(lx - LINE_W / 2f, topOff, HANDLE_R * 0.5f, handle);
            c.drawCircle(lx + LINE_W / 2f, topOff, HANDLE_R * 0.5f, handle);
            c.drawCircle(lx - LINE_W / 2f, botOff, HANDLE_R * 0.5f, handle);
            c.drawCircle(lx + LINE_W / 2f, botOff, HANDLE_R * 0.5f, handle);

            c.drawCircle(lx, topOff, HANDLE_R, lineTop);
            c.drawCircle(lx, botOff, HANDLE_R, lineBottom);

            label.setTextAlign(Paint.Align.LEFT);
            c.drawText("起点", lx + LINE_W / 2f + 14, topOff + 9, label);
            c.drawText("终点", lx + LINE_W / 2f + 14, botOff + 9, label);

            int dist = botOff - topOff;
            label.setTextAlign(Paint.Align.CENTER);
            c.drawText(dist + " px", lx, (topOff + botOff) / 2f - 26, label);

            float ratio = winH > 0 ? dist / (float) winH : 0f;
            c.drawText(Math.round(ratio * 100) + "% 屏高", lx, (topOff + botOff) / 2f + 34, label);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            float rx = e.getRawX(), ry = e.getRawY();
            float lx = getWidth() / 2f;

            float localX = e.getX();
            float localY = e.getY();

            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    if (Math.abs(localY - topOff) < HANDLE_R + 30) {
                        dragMode = 2;
                    } else if (Math.abs(localY - botOff) < HANDLE_R + 30) {
                        dragMode = 3;
                    } else if (Math.abs(localX - lx) < 70) {
                        dragMode = 1;
                    } else {
                        return false;
                    }
                    downRawX = rx;
                    downRawY = ry;
                    origWinX = winX;
                    origWinY = winY;
                    origTopOff = topOff;
                    origBotOff = botOff;
                    return true;
                }
                case MotionEvent.ACTION_MOVE: {
                    if (dragMode == 0) return false;
                    float dx = rx - downRawX;
                    float dy = ry - downRawY;

                    if (dragMode == 1) {

                        winX = origWinX + (int) dx;
                        winY = origWinY + (int) dy;
                        if (dragListener != null) dragListener.onDrag(winX, winY);
                    } else if (dragMode == 2) {

                        int nt = origTopOff + (int) dy;
                        topOff = Math.max(HANDLE_R, Math.min(nt, botOff - HANDLE_R * 3));
                        if (heightListener != null) heightListener.onHeight(topOff, botOff);
                        invalidate();
                    } else {

                        int nb = origBotOff + (int) dy;
                        botOff = Math.min(winH - HANDLE_R,
                                Math.max(nb, topOff + HANDLE_R * 3));
                        if (heightListener != null) heightListener.onHeight(topOff, botOff);
                        invalidate();
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    dragMode = 0;
                    return true;
            }
            return false;
        }
    }
}
