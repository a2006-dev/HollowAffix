package com.hollow.affix;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.view.accessibility.AccessibilityEvent;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 核心无障碍服务：截屏 + 点击。
 * - 截屏走 takeScreenshot()（API 30+，无授权弹窗，不产生录像）
 * - 点击走 dispatchGesture()（无需 root / Shizuku）
 */
public class ProbeService extends AccessibilityService {

    private static volatile ProbeService instance;

    public static ProbeService get() { return instance; }
    public static boolean isReady() { return instance != null; }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Logger.i("SERVICE", "无障碍服务已连接");
        Logger.appendTo("device.txt",
                "连接时间=" + new java.util.Date()
                        + "\n无障碍=OK"
                        + "\nSDK=" + Build.VERSION.SDK_INT);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 不处理事件，仅保留能力
    }

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        instance = null;
        Logger.i("SERVICE", "无障碍服务已断开");
        super.onDestroy();
    }

    // ==================== 截屏 ====================

    /**
     * 同步截屏，最多等 3 秒。失败返回 null。
     */
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
                                // 复制到软件位图，避免硬件缓冲被回收
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

        // 游戏为横屏 3168x1440，takeScreenshot 返回的正是这个方向，
        // 不需要旋转。此处保留日志便于核对。
        Logger.i("SHOT", "截屏完成 " + b.getWidth() + "x" + b.getHeight()
                + " 耗时 " + (System.currentTimeMillis() - t0) + "ms");
        return b;
    }

    // ==================== 点击 ====================

    public interface ClickCallback {
        void onDone(boolean ok);
    }

    /** 单击 */
    public void tap(int x, int y) {
        tap(x, y, 60, null);
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

    /** 长按 */
    public void longPress(int x, int y, long ms) {
        tap(x, y, ms, null);
    }

    /** 滑动 */
    public void swipe(int x1, int y1, int x2, int y2, long durationMs) {
        Path p = new Path();
        p.moveTo(x1, y1);
        p.lineTo(x2, y2);
        GestureDescription.Builder b = new GestureDescription.Builder();
        b.addStroke(new GestureDescription.StrokeDescription(p, 0, Math.max(2, durationMs)));
        dispatchGesture(b.build(), null, null);
        Logger.i("SWIPE", "滑动 (" + x1 + "," + y1 + ")→(" + x2 + "," + y2 + ")");
    }

    /** 获取屏幕尺寸 */
    public int[] screenSize() {
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        return new int[]{dm.widthPixels, dm.heightPixels};
    }
}