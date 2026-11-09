package com.hollow.affix;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class ProbeService extends AccessibilityService {

    private static volatile ProbeService instance;

    public static ProbeService get() { return instance; }
    public static boolean isReady() { return instance != null; }

    private View keepAliveView;
    private WindowManager keepAliveWm;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;

        attachKeepAliveOverlay();

        Logger.i("SERVICE", "无障碍服务已连接");
        Logger.appendTo("device.txt",
                "连接时间=" + new java.util.Date()
                        + "\n无障碍=OK"
                        + "\n保活窗口=" + (keepAliveView != null ? "已挂载" : "失败")
                        + "\nSDK=" + Build.VERSION.SDK_INT);
    }

    private void attachKeepAliveOverlay() {
        if (keepAliveView != null) return;
        new Thread(() -> {
            final long[] retryDelays = {250, 750, 1500};
            for (int attempt = 0; attempt <= retryDelays.length; attempt++) {
                if (attempt > 0) {
                    try {
                        Thread.sleep(retryDelays[attempt - 1]);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
                if (tryAttach()) {
                    Logger.i("SERVICE", "保活窗口已挂载 第" + (attempt + 1) + "次尝试");
                    Logger.appendTo("trace.txt", "保活窗口挂载成功 尝试=" + (attempt + 1));
                    return;
                }
            }
            Logger.e("SERVICE", "保活窗口挂载失败，已重试 " + (retryDelays.length + 1) + " 次");
            Logger.appendTo("trace.txt", "保活窗口挂载失败");
        }, "keepalive").start();
    }

    private boolean tryAttach() {
        try {
            keepAliveWm = (WindowManager) getSystemService(WINDOW_SERVICE);
            if (keepAliveWm == null) return false;

            View v = new View(this);
            v.setBackgroundColor(0x00000000);

            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    1, 1,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSPARENT);
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.x = 0;
            lp.y = 0;

            keepAliveWm.addView(v, lp);
            keepAliveView = v;
            return true;
        } catch (Throwable e) {

            Logger.appendTo("trace.txt", "保活窗口尝试失败: " + e);
            return false;
        }
    }

    private void detachKeepAliveOverlay() {
        if (keepAliveView == null) return;
        try {
            keepAliveWm.removeView(keepAliveView);
        } catch (Throwable ignored) {}
        keepAliveView = null;
        keepAliveWm = null;
    }

    @Override
    public void onInterrupt() {}

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {

    }

    @Override
    public void onDestroy() {
        detachKeepAliveOverlay();
        instance = null;
        Logger.i("SERVICE", "无障碍服务已断开");
        super.onDestroy();
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        detachKeepAliveOverlay();
        return super.onUnbind(intent);
    }

    public interface VolumeKeyListener {
        void onVolumeDown();
    }

    private static volatile VolumeKeyListener volumeListener;

    public static void setVolumeKeyListener(VolumeKeyListener l) {
        volumeListener = l;
    }

    public static volatile boolean captureVolumeKey = false;

    @Override
    protected boolean onKeyEvent(android.view.KeyEvent event) {
        if (captureVolumeKey
                && event.getKeyCode() == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                VolumeKeyListener l = volumeListener;
                if (l != null) {

                    new android.os.Handler(android.os.Looper.getMainLooper())
                            .post(l::onVolumeDown);
                }
            }
            return true;
        }
        return super.onKeyEvent(event);
    }

    public Bitmap screenshot() {
        if (Build.VERSION.SDK_INT < 30) {
            Logger.e("SHOT", "takeScreenshot 需要 Android 11+");
            return null;
        }
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Bitmap> ref = new AtomicReference<>();

        long t0 = System.currentTimeMillis();
        takeScreenshot(android.view.Display.DEFAULT_DISPLAY, getMainExecutor(),
                new TakeScreenshotCallback() {
                    @Override
                    public void onSuccess(ScreenshotResult result) {
                        try {
                            Bitmap bmp = Bitmap.wrapHardwareBuffer(
                                    result.getHardwareBuffer(), result.getColorSpace());
                            if (bmp != null) {

                                ref.set(bmp.copy(Bitmap.Config.ARGB_8888, false));
                                bmp.recycle();
                            }
                            result.getHardwareBuffer().close();
                        } catch (Throwable e) {
                            Logger.e("SHOT", "转换位图失败: " + e);
                        }
                        latch.countDown();
                    }

                    @Override
                    public void onFailure(int errorCode) {
                        Logger.e("SHOT", "截屏失败 code=" + errorCode);
                        latch.countDown();
                    }
                });

        try {
            if (!latch.await(3, TimeUnit.SECONDS)) {
                Logger.e("SHOT", "截屏超时");
                return null;
            }
        } catch (InterruptedException e) {
            return null;
        }
        Bitmap b = ref.get();
        if (b == null) {
            Logger.e("SHOT", "截屏返回 null");
            return null;
        }

        Logger.i("SHOT", "截屏完成 " + b.getWidth() + "x" + b.getHeight()
                + " 耗时 " + (System.currentTimeMillis() - t0) + "ms");
        return b;
    }

    public interface ClickCallback {
        void onDone(boolean ok);
    }

    public void tap(int x, int y) {
        tap(x, y, 60, null);
    }

    public boolean tapAndWait(int x, int y, long timeoutMs) {
        if (Build.VERSION.SDK_INT < 24) return false;
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Boolean> ok = new AtomicReference<>(false);

        Path p = new Path();
        p.moveTo(x, y);
        GestureDescription.Builder b = new GestureDescription.Builder();
        b.addStroke(new GestureDescription.StrokeDescription(p, 0, 40));

        boolean dispatched = dispatchGesture(b.build(), new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription g) {
                ok.set(true);
                latch.countDown();
            }

            @Override
            public void onCancelled(GestureDescription g) {
                ok.set(false);
                latch.countDown();
            }
        }, null);

        if (!dispatched) {
            Logger.w("TAP", "点击下发失败 (" + x + "," + y + ")");
            return false;
        }
        try {
            latch.await(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        Logger.i("TAP", "点击 (" + x + "," + y + ") 完成=" + ok.get());
        return ok.get();
    }

    public boolean swipeAndWait(int x1, int y1, int x2, int y2, long durationMs) {
        return swipeAndWait(x1, y1, x2, y2, durationMs, 1);
    }

    public boolean swipeAndWait(int x1, int y1, int x2, int y2,
                                long durationMs, int segments) {
        int n = Math.max(1, segments);
        if (n == 1) {
            return swipeOnceAndWait(x1, y1, x2, y2, durationMs);
        }

        boolean ok = true;
        for (int i = 0; i < n; i++) {

            int sy = y1 + (y2 - y1) * i / n;
            int ey = y1 + (y2 - y1) * (i + 1) / n;
            ok &= swipeOnceAndWait(x1, sy, x2, ey, Math.max(60, durationMs / n));
            if (i < n - 1) {
                try {
                    Thread.sleep(80);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return ok;
    }

    private boolean swipeOnceAndWait(int x1, int y1, int x2, int y2, long durationMs) {
        final CountDownLatch latch = new CountDownLatch(1);
        Path p = new Path();
        p.moveTo(x1, y1);
        p.lineTo(x2, y2);
        GestureDescription.Builder b = new GestureDescription.Builder();
        b.addStroke(new GestureDescription.StrokeDescription(p, 0, Math.max(2, durationMs)));
        boolean dispatched = dispatchGesture(b.build(), new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription g) { latch.countDown(); }
            @Override public void onCancelled(GestureDescription g) { latch.countDown(); }
        }, null);
        if (!dispatched) return false;
        try {
            latch.await(durationMs + 1500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        Logger.i("SWIPE", "滑动 (" + x1 + "," + y1 + ")→(" + x2 + "," + y2 + ") 完成");
        return true;
    }

    public void tap(int x, int y, long durationMs, final ClickCallback cb) {
        if (Build.VERSION.SDK_INT < 24) return;
        Path p = new Path();
        p.moveTo(x, y);
        GestureDescription.Builder b = new GestureDescription.Builder();
        b.addStroke(new GestureDescription.StrokeDescription(p, 0, Math.max(1, durationMs)));
        boolean ok = dispatchGesture(b.build(), new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription g) {
                Logger.i("TAP", "点击完成");
                if (cb != null) cb.onDone(true);
            }

            @Override
            public void onCancelled(GestureDescription g) {
                Logger.w("TAP", "点击被取消");
                if (cb != null) cb.onDone(false);
            }
        }, null);
        Logger.i("TAP", "点击 (" + x + "," + y + ") dispatch=" + ok);
        if (!ok && cb != null) cb.onDone(false);
    }

    public void longPress(int x, int y, long ms) {
        tap(x, y, ms, null);
    }

    public void swipe(int x1, int y1, int x2, int y2, long durationMs) {
        Path p = new Path();
        p.moveTo(x1, y1);
        p.lineTo(x2, y2);
        GestureDescription.Builder b = new GestureDescription.Builder();
        b.addStroke(new GestureDescription.StrokeDescription(p, 0, Math.max(2, durationMs)));
        dispatchGesture(b.build(), null, null);
        Logger.i("SWIPE", "滑动 (" + x1 + "," + y1 + ")→(" + x2 + "," + y2 + ")");
    }

    public int[] screenSize() {
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        return new int[]{dm.widthPixels, dm.heightPixels};
    }
}
