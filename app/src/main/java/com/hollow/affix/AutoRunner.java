package com.hollow.affix;

import android.os.Handler;
import android.os.Looper;

public class AutoRunner {

    public static final int MODE_TEST = 0;
    public static final int MODE_FULL = 1;
    public static final int MODE_RANGE = 2;

    public static final int MODE_ONE_PAGE = 3;

    public interface Callback {

        void onStep(int page, int pointIndex, int pointTotal);

        void onPageTurn(int newPage);

        void onStop(String reason, int totalPages);
    }

    public interface ScreenHandler {
        String handle(int round);
    }

    private static final long STEP_SETTLE_MS = 500;
    private static final long STEP_GAP_MS = 150;
    private static final long PAGE_GAP_MS = 900;
    private static final int  EMPTY_PAGE_LIMIT = 2;
    private static final int  TURN_RETRY_LIMIT = 2;
    private static final float TURN_RETRY_SCALE = 1.3f;
    private static final int  TURN_RETRY_MAX = 3;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final AutoPoints points;
    private final Callback cb;
    private final ScreenHandler screen;
    private final int mode;
    private final int startPage;
    private final int endPage;

    private volatile boolean running = false;
    private volatile boolean paused = false;
    private volatile Thread thread;

    private volatile int page = 1;
    private volatile int stepIndex = 0;

    public AutoRunner(AutoPoints points, ScreenHandler screen,
                      int mode, int startPage, int endPage, Callback cb) {
        this.points = points != null ? points : new AutoPoints();
        this.screen = screen;
        this.mode = mode;
        this.startPage = Math.max(1, startPage);
        this.endPage = Math.max(1, endPage);
        this.cb = cb;
    }

    public boolean isRunning() { return running; }
    public boolean isPaused() { return paused; }

    public void start() {

        if (running) {
            if (paused) {
                paused = false;
                Logger.appendTo("trace.txt", "自动执行 继续");
            }
            return;
        }

        if (!points.hasCells()) {
            stop("请先录制点位", 1);
            return;
        }

        running = true;
        paused = false;
        page = 1;
        stepIndex = 0;

        String modeName = mode == MODE_TEST ? "测试"
                : mode == MODE_FULL ? "全部执行"
                : mode == MODE_ONE_PAGE ? "单页执行" : "部分执行";
        Logger.appendTo("trace.txt", "=== " + modeName + " 开始 === 点位 "
                + points.count() + " 个（手动翻页模式）");

        Thread t = new Thread(this::loop, "affix-auto");
        thread = t;
        t.start();
    }

    public void togglePause() {
        if (!running) return;
        paused = !paused;
        Logger.appendTo("trace.txt", "自动执行 " + (paused ? "暂停" : "继续"));
    }

    public void stop(String reason, int pages) {
        if (!running) return;
        running = false;
        paused = false;
        Thread t = thread;
        thread = null;
        if (t != null) t.interrupt();
        Logger.appendTo("trace.txt", "=== 自动执行结束 === " + reason + " 共 " + pages + " 页");
        if (cb != null) cb.onStop(reason, pages);
    }

    public void stop(String reason) {
        stop(reason, page);
    }

    private void loop() {
        try {
            int perPage = AutoPoints.CELLS_PER_PAGE;
            int round = 0;
            int emptyStreak = 0;

            int cellCount = Math.min(perPage, points.cells.size());
            if (cellCount <= 0) {
                stop("点位为空", 1);
                return;
            }

            for (page = 1; ; page++) {
                if (!alive()) return;

                if (mode == MODE_RANGE && page < startPage) {
                    if (!turnPage(page, null)) {
                        stop("翻页失败，无法到达第 " + startPage + " 页", page);
                        return;
                    }
                    continue;
                }

                boolean gotAny = false;
                String lastSig = null;

                for (stepIndex = 0; stepIndex < cellCount; stepIndex++) {
                    if (!alive()) return;

                    AutoPoints.Point p = points.cells.get(stepIndex);

                    ProbeService svc = ProbeService.get();
                    if (svc == null) {
                        stop("无障碍未连接", page);
                        return;
                    }
                    svc.tapAndWait(p.x, p.y, 1200);

                    round++;
                    if (!sleep(STEP_SETTLE_MS)) return;

                    String sig = screen.handle(round);
                    if (sig != null) {
                        gotAny = true;
                        lastSig = sig;
                    }

                    final int fp = page, fi = stepIndex + 1, ft = cellCount;
                    post(() -> { if (cb != null) cb.onStep(fp, fi, ft); });

                    if (!sleep(STEP_GAP_MS)) return;
                }

                if (!gotAny) {
                    emptyStreak++;
                    if (emptyStreak >= EMPTY_PAGE_LIMIT) {
                        stop("本页没识别到内容", Math.max(1, page - emptyStreak));
                        return;
                    }
                } else {
                    emptyStreak = 0;
                }

                String why = (mode == MODE_ONE_PAGE || mode == MODE_FULL)
                        ? "本页已完成，请手动翻页后重新点开始"
                        : "已执行到第 " + page + " 页";
                stop(why, page);
                return;
            }
        } catch (Throwable t) {
            Logger.appendTo("trace.txt", "自动执行异常: " + t);
            stop("执行异常: " + t.getMessage(), page);
        }
    }

    private boolean turnPage(int fromPage, String beforeSig) {
        ProbeService svc = ProbeService.get();
        if (svc == null) {
            stop("无障碍未连接", fromPage);
            return false;
        }

        int[] s = points.swipeForPage();
        int dist = Math.abs(s[3] - s[1]);
        int segs = Math.max(1, points.swipeSegments);

        int minY = Math.min(s[1], s[3]);
        int maxY = Math.max(s[1], s[3]);

        for (int attempt = 0; attempt < TURN_RETRY_MAX; attempt++) {
            if (!alive()) return false;

            int add = Math.round(dist * (TURN_RETRY_SCALE - 1f) * attempt);
            int y2 = s[3] + (s[3] > s[1] ? add : -add);

            if (s[3] > s[1] && y2 > maxY) y2 = maxY;
            if (s[3] < s[1] && y2 < minY) y2 = minY;

            Logger.appendTo("trace.txt", "翻页 " + fromPage + "→" + (fromPage + 1)
                    + " 第 " + (attempt + 1) + " 次滑动 (" + s[0] + "," + s[1]
                    + ")→(" + s[2] + "," + y2 + ") " + points.swipeDuration
                    + "ms " + segs + "段");

            svc.swipeAndWait(s[0], s[1], s[2], y2, points.swipeDuration, segs);
            sleep(PAGE_GAP_MS);

            if (beforeSig == null) {
                postPageTurn(fromPage + 1);
                return true;
            }

            String nowSig = screen.handle(0);
            if (nowSig == null) {

                Logger.appendTo("trace.txt", "翻页 " + fromPage + " 校验识别失败，按已翻页处理");
                postPageTurn(fromPage + 1);
                return true;
            }
            if (!nowSig.equals(beforeSig)) {
                postPageTurn(fromPage + 1);
                return true;
            }

            Logger.appendTo("trace.txt", "翻页 " + fromPage + " 第 " + (attempt + 1)
                    + " 次滑动未翻动（内容未变），准备补滑");
        }

        Logger.appendTo("trace.txt", "翻页 " + fromPage + " 连续 "
                + TURN_RETRY_MAX + " 次未翻动，判定翻不动");
        return false;
    }

    private void postPageTurn(int newPage) {
        post(() -> { if (cb != null) cb.onPageTurn(newPage); });
    }

    private boolean alive() {
        if (!running) return false;
        if (Thread.currentThread().isInterrupted()) {
            running = false;
            return false;
        }
        return true;
    }

    private boolean sleep(long ms) {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            if (!alive()) return false;

            while (paused) {
                if (!alive()) return false;
                try {
                    Thread.sleep(120);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    running = false;
                    return false;
                }
            }
            try {
                Thread.sleep(60);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running = false;
                return false;
            }
        }
        return true;
    }

    private void post(Runnable r) {
        ui.post(() -> {
            try {
                r.run();
            } catch (Throwable t) {
                Logger.appendTo("trace.txt", "自动执行回调异常: " + t);
            }
        });
    }
}
