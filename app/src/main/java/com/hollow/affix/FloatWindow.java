package com.hollow.affix;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * 悬浮窗：顶部显示当前规则名，中间显示判定结果与副属性。
 */
public class FloatWindow {

    private static final String TAG = "FLOAT";

    // 配色
    private static final String C_BG = "#E6101419";      // 92% 透明深色
    private static final String C_ACCENT = "#7C4DFF";
    private static final String C_TEXT = "#E8EAED";
    private static final String C_SUB = "#8B95A5";

    private final RuleSet ruleSet;

    private Context ctx;
    private WindowManager wm;
    private LinearLayout root;
    private WindowManager.LayoutParams lp;
    private CollectOverlay overlay;
    private WindowManager.LayoutParams overlayLp;

    private TextView tvRuleName;     // 顶部规则名
    private TextView tvStatus;
    private TextView btnStart, btnPause, btnSelect, btnFold;
    private TextView tvGrade, tvScore, tvReason;
    private TextView[] tvAffix = new TextView[4];
    private LinearLayout bodyBox;

    private boolean folded = false;
    private boolean running = false;
    private Thread worker;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final OcrEngine ocr = new OcrEngine();
    private boolean debugOnce = true;

    public FloatWindow(RuleSet rs) {
        this.ruleSet = rs != null ? rs : RuleSet.defaultSet();
    }

    public boolean isRunning() { return running; }

    @SuppressLint("ClickableViewAccessibility")
    public void show(Context context) {
        this.ctx = context;
        wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        if (!Settings.canDrawOverlays(ctx)) {
            Logger.e(TAG, "无悬浮窗权限");
            return;
        }
        if (root != null) return;

        root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(9), dp(6), dp(9), dp(7));
        root.setBackground(bg(C_BG, 13, "#2A7C4DFF"));

        // ===== 标题栏：规则名 + 折叠/关闭 =====
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        // 左侧色条 + 规则名
        View bar0 = new View(ctx);
        bar0.setBackground(bg(C_ACCENT, 2, null));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(3), dp(13));
        bp.rightMargin = dp(6);
        bar.addView(bar0, bp);

        tvRuleName = tv(ruleSet.name, 10, C_ACCENT, true);
        tvRuleName.setSingleLine(true);
        tvRuleName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        bar.addView(tvRuleName, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        btnFold = miniBtn("─");
        btnFold.setOnClickListener(v -> toggleFold());
        bar.addView(btnFold);

        TextView btnClose = miniBtn("×");
        btnClose.setOnClickListener(v -> hide());
        bar.addView(btnClose);

        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(20)));

        // ===== 状态 =====
        tvStatus = tv("就绪", 8, C_SUB, false);
        tvStatus.setSingleLine(true);
        root.addView(tvStatus);

        // ===== 内容区 =====
        bodyBox = new LinearLayout(ctx);
        bodyBox.setOrientation(LinearLayout.VERTICAL);
        bodyBox.setPadding(0, dp(3), 0, 0);

        // 判定结果行：等级 + 分数
        LinearLayout gradeRow = new LinearLayout(ctx);
        gradeRow.setOrientation(LinearLayout.HORIZONTAL);
        gradeRow.setGravity(Gravity.CENTER_VERTICAL);

        tvGrade = tv("—", 17, C_TEXT, true);
        gradeRow.addView(tvGrade, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        tvScore = tv("", 11, C_SUB, false);
        gradeRow.addView(tvScore);

        bodyBox.addView(gradeRow);

        // 判定依据
        tvReason = tv("", 8, C_SUB, false);
        tvReason.setSingleLine(true);
        tvReason.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tvReason.setPadding(0, 0, 0, dp(3));
        bodyBox.addView(tvReason);

        // 4 条副属性
        for (int i = 0; i < 4; i++) {
            tvAffix[i] = tv("· —", 9, C_TEXT, false);
            tvAffix[i].setSingleLine(true);
            tvAffix[i].setEllipsize(android.text.TextUtils.TruncateAt.END);
            tvAffix[i].setIncludeFontPadding(false);
            tvAffix[i].setPadding(0, dp(1), 0, dp(1));
            bodyBox.addView(tvAffix[i]);
        }

        root.addView(bodyBox);

        // ===== 按钮行 =====
        LinearLayout btns = new LinearLayout(ctx);
        btns.setOrientation(LinearLayout.HORIZONTAL);

        btnStart = chip("开始", C_ACCENT);
        btnStart.setOnClickListener(v -> start());
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, dp(24), 1f);
        p1.setMargins(0, dp(4), dp(4), 0);
        btns.addView(btnStart, p1);

        btnPause = chip("暂停", "#25FFFFFF");
        btnPause.setOnClickListener(v -> pause());
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, dp(24), 1f);
        p2.setMargins(0, dp(4), dp(4), 0);
        btns.addView(btnPause, p2);

        btnSelect = chip("框选", "#25FFFFFF");
        btnSelect.setOnClickListener(v -> toggleCollect());
        LinearLayout.LayoutParams p3 = new LinearLayout.LayoutParams(0, dp(24), 1f);
        p3.setMargins(0, dp(4), 0, 0);
        btns.addView(btnSelect, p3);

        root.addView(btns);

        // ===== 窗口参数 =====
        lp = new WindowManager.LayoutParams();
        lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        lp.format = PixelFormat.TRANSLUCENT;
        lp.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.width = dp(152);
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
        lp.x = 14;
        lp.y = 70;

        wm.addView(root, lp);

        // 拖动
        bar.setOnTouchListener(new View.OnTouchListener() {
            float dx, dy;
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        dx = e.getRawX() - lp.x;
                        dy = e.getRawY() - lp.y;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        lp.x = (int) (e.getRawX() - dx);
                        lp.y = (int) (e.getRawY() - dy);
                        wm.updateViewLayout(root, lp);
                        return true;
                }
                return false;
            }
        });

        // 保活服务
        try {
            Intent ka = new Intent(ctx, KeepAliveService.class);
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                ctx.startForegroundService(ka);
            } else {
                ctx.startService(ka);
            }
        } catch (Throwable ignored) {}

        Logger.i(TAG, "悬浮窗已显示，规则=" + ruleSet.name);

        // 加载 OCR
        new Thread(() -> {
            ocr.init(ctx);
            ui.post(() -> {
                if (ocr.isReady()) setStatus("就绪");
                else setStatus("OCR失败");
            });
        }, "ocr-init").start();
    }

    public void hide() {
        pause();
        stopCollect();
        try { if (root != null) wm.removeView(root); } catch (Exception ignored) {}
        root = null;
        Logger.i(TAG, "悬浮窗已关闭");
    }

    // ==================== 框选 ====================

    private void toggleCollect() {
        if (overlay != null) {
            stopCollect();
            btnSelect.setText("框选");
            btnSelect.setBackground(chipBg("#25FFFFFF"));
        } else {
            startCollect();
            btnSelect.setText("取消");
            btnSelect.setBackground(chipBg("#CC7C4DFF"));
        }
    }

    private void startCollect() {
        if (!Settings.canDrawOverlays(ctx)) return;
        if (overlay == null) {
            overlay = new CollectOverlay(ctx);
            overlay.setListener(new CollectOverlay.Listener() {
                @Override public void onTap(int x, int y) {
                    setStatus("(" + x + "," + y + ")");
                }
                @Override public void onRegion(Rect r) {
                    RegionStore.set(r.left, r.top, r.width(), r.height());
                    setStatus("区域 " + r.width() + "×" + r.height());
                    toast("识别区域已更新");
                    ui.post(() -> {
                        stopCollect();
                        btnSelect.setText("框选");
                        btnSelect.setBackground(chipBg("#25FFFFFF"));
                    });
                }
            });
        }
        overlayLp = new WindowManager.LayoutParams();
        overlayLp.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        overlayLp.format = PixelFormat.TRANSLUCENT;
        overlayLp.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        overlayLp.gravity = Gravity.TOP | Gravity.START;
        overlayLp.width = ViewGroup.LayoutParams.MATCH_PARENT;
        overlayLp.height = ViewGroup.LayoutParams.MATCH_PARENT;
        wm.addView(overlay, overlayLp);
        if (root != null) {
            wm.removeView(root);
            wm.addView(root, lp);
        }
        toast("框住属性面板，松手生效");
    }

    private void stopCollect() {
        if (overlay != null) {
            try { wm.removeView(overlay); } catch (Throwable ignored) {}
            overlay = null;
        }
    }

    // ==================== 运行 ====================

    private void start() {
        if (running) return;
        if (!ProbeService.isReady()) {
            setStatus("无障碍未开启");
            toast("请先开启无障碍服务");
            return;
        }
        if (!ocr.isReady()) {
            setStatus("OCR未就绪");
            return;
        }
        running = true;
        setStatus("运行中");
        btnStart.setBackground(chipBg("#CC2E7D32"));
        btnPause.setBackground(chipBg("#CC7C4DFF"));

        worker = new Thread(() -> {
            int round = 0;
            while (running) {
                round++;
                long t0 = System.currentTimeMillis();
                try {
                    tick(round);
                } catch (Throwable e) {
                    Logger.appendTo("trace.txt", "r" + round + " 异常: " + e);
                }
                long cost = System.currentTimeMillis() - t0;
                try { Thread.sleep(Math.max(200, 900 - cost)); }
                catch (InterruptedException e) { break; }
            }
        }, "affix-worker");
        worker.start();
    }

    private void pause() {
        if (!running) return;
        running = false;
        if (worker != null) { worker.interrupt(); worker = null; }
        setStatus("已暂停");
        ui.post(() -> {
            btnStart.setBackground(chipBg("#CC7C4DFF"));
            btnPause.setBackground(chipBg("#25FFFFFF"));
        });
    }

    /** 单轮识别 */
    private void tick(int round) {
        ProbeService svc = ProbeService.get();
        if (svc == null) return;

        Bitmap bmp = svc.screenshot();
        if (bmp == null) return;

        // 必须是横屏
        if (bmp.getWidth() < bmp.getHeight()) {
            setStatus("请切到游戏");
            bmp.recycle();
            return;
        }

        long t0 = System.currentTimeMillis();

        int[] region = RegionStore.get();
        int affixY = region[1] + (int) (region[3] * 0.34f);
        int[] affixRegion = {region[0], affixY,
                region[2], region[3] - (affixY - region[1])};

        if (com.hollow.affix.BuildConfig.DEBUG_SHOT && debugOnce) {
            Logger.saveDebug(bmp, affixRegion, "r" + round);
            debugOnce = false;
        }

        List<OcrEngine.Box> boxes = ocr.recognize(bmp, affixRegion);

        // 解析副属性
        List<String> names = new ArrayList<>();   // 标准词条名
        List<String> pretty = new ArrayList<>();  // 显示用
        int rx = region[0], rw = region[2];
        for (OcrEngine.Box b : boxes) {
            if (b.x > rx + rw * 0.73f) continue;
            String raw = b.text;
            if (raw == null) continue;
            if (!raw.contains("增加") && !raw.contains("提升")
                    && !raw.contains("增强") && !raw.contains("提高")) continue;

            String clean = AffixMatcher.cleanText(raw);
            if (clean.length() < 3) continue;
            String std = AffixMatcher.match(clean);
            if (std == null) {
                Logger.appendTo("trace.txt", "未匹配 raw=[" + raw + "]");
                continue;
            }
            if (names.contains(std)) continue;
            names.add(std);
            pretty.add(AffixMatcher.pretty(clean));
        }

        // 判定
        Rating.Result rs = Rating.evaluate(names, ruleSet);

        final String grade = rs.grade;
        final String color = rs.color;
        final int score = rs.score;
        final String reason = rs.reason;
        final String[] affixStr = new String[4];
        for (int i = 0; i < 4; i++) {
            affixStr[i] = i < pretty.size() ? "· " + pretty.get(i) : "· —";
        }
        final int roundNo = round;
        final long cost = System.currentTimeMillis() - t0;

        ui.post(() -> {
            tvGrade.setText(grade);
            tvGrade.setTextColor(Color.parseColor(color));
            tvScore.setText(score + "分");
            tvReason.setText(reason);
            for (int i = 0; i < 4; i++) tvAffix[i].setText(affixStr[i]);
            tvStatus.setText(roundNo + "轮 " + cost + "ms");
        });

        // 落盘
        StringBuilder sb = new StringBuilder();
        sb.append("轮次\t").append(round).append('\n');
        sb.append("规则\t").append(ruleSet.name).append('\n');
        for (String a : pretty) sb.append("词条\t").append(a).append('\n');
        sb.append("判定\t").append(grade).append('\t').append(score).append('\n');
        sb.append("耗时\t").append(cost).append("ms\n");
        Logger.appendTo("ocr_cards.txt", sb.toString());

        bmp.recycle();
    }

    // ==================== UI 辅助 ====================

    private void setStatus(String s) {
        ui.post(() -> {
            if (tvStatus != null) tvStatus.setText(s);
        });
    }

    private void toggleFold() {
        folded = !folded;
        bodyBox.setVisibility(folded ? View.GONE : View.VISIBLE);
        tvStatus.setVisibility(folded ? View.GONE : View.VISIBLE);
        btnFold.setText(folded ? "□" : "─");
    }

    private void toast(String s) {
        ui.post(() -> android.widget.Toast
                .makeText(ctx, s, android.widget.Toast.LENGTH_SHORT).show());
    }

    private TextView tv(String s, float sp, String color, boolean bold) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(Color.parseColor(color));
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    private TextView miniBtn(String s) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        t.setTextColor(Color.parseColor("#99FFFFFF"));
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(6), 0, dp(6), 0);
        t.setBackgroundColor(Color.TRANSPARENT);
        return t;
    }

    private TextView chip(String s, String fill) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
        t.setTextColor("#FFFFFF".equals(fill) ? Color.BLACK : Color.WHITE);
        t.setGravity(Gravity.CENTER);
        t.setBackground(chipBg(fill));
        return t;
    }

    private GradientDrawable chipBg(String fill) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.parseColor(fill));
        g.setCornerRadius(dp(6));
        return g;
    }

    private GradientDrawable bg(String fill, int radiusDp, String stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.parseColor(fill));
        g.setCornerRadius(dp(radiusDp));
        if (stroke != null) g.setStroke(dp(1), Color.parseColor(stroke));
        return g;
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, ctx.getResources().getDisplayMetrics()));
    }
}