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
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class FloatWindow {

    private static final String TAG = "FLOAT";

    private static final String C_BG = "#E6101419";
    private static final String C_ACCENT = "#7C4DFF";
    private static final String C_TEXT = "#E8EAED";
    private static final String C_SUB = "#8B95A5";

    private RuleSet ruleSet;

    private Context ctx;
    private WindowManager wm;
    private LinearLayout root;
    private WindowManager.LayoutParams lp;
    private CollectOverlay overlay;
    private WindowManager.LayoutParams overlayLp;

    private TextView tvRuleName;
    private TextView tvStatus;
    private TextView btnStart, btnPause, btnStop, btnSelect, btnFold;
    private TextView tvGrade, tvScore, tvReason;
    private TextView[] tvAffix = new TextView[4];
    private LinearLayout bodyBox;

    private CassetteRule cassetteRule;
    private CassetteReader cassetteReader;

    private boolean autoSwitch = false;

    private AutoRunner autoRunner;

    private AutoRecordView recorder;

    private boolean swipeThenTest = false;

    private TextView tvHint;

    private boolean folded = false;
    private boolean running = false;

    private volatile boolean paused = false;

    private String lastSig = null;
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

        if (cassetteRule == null) {
            cassetteRule = CassetteRule.load(ctx);
            cassetteReader = new CassetteReader(cassetteRule);
        }

        root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(9), dp(6), dp(9), dp(7));
        root.setBackground(bg(C_BG, 13, "#2A7C4DFF"));

        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);

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

        tvStatus = tv("就绪", 8, C_SUB, false);
        tvStatus.setSingleLine(true);
        root.addView(tvStatus);

        bodyBox = new LinearLayout(ctx);
        bodyBox.setOrientation(LinearLayout.VERTICAL);
        bodyBox.setPadding(0, dp(3), 0, 0);

        LinearLayout gradeRow = new LinearLayout(ctx);
        gradeRow.setOrientation(LinearLayout.HORIZONTAL);
        gradeRow.setGravity(Gravity.CENTER_VERTICAL);

        tvGrade = tv("—", 17, C_TEXT, true);
        gradeRow.addView(tvGrade, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        tvScore = tv("", 11, C_SUB, false);
        gradeRow.addView(tvScore);

        bodyBox.addView(gradeRow);

        tvReason = tv("", 8, C_SUB, false);
        tvReason.setSingleLine(true);
        tvReason.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tvReason.setPadding(0, 0, 0, dp(3));
        bodyBox.addView(tvReason);

        for (int i = 0; i < 4; i++) {
            tvAffix[i] = tv("· —", 9, C_TEXT, false);
            tvAffix[i].setSingleLine(true);
            tvAffix[i].setEllipsize(android.text.TextUtils.TruncateAt.END);
            tvAffix[i].setIncludeFontPadding(false);
            tvAffix[i].setPadding(0, dp(1), 0, dp(1));
            bodyBox.addView(tvAffix[i]);
        }

        root.addView(bodyBox);

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

        btnStop = chip("停止", "#25FFFFFF");
        btnStop.setOnClickListener(v -> stopAll());
        LinearLayout.LayoutParams p2b = new LinearLayout.LayoutParams(0, dp(24), 1f);
        p2b.setMargins(0, dp(4), dp(4), 0);
        btns.addView(btnStop, p2b);

        btnSelect = chip("设置", "#25FFFFFF");
        btnSelect.setOnClickListener(v -> toggleCollect());
        LinearLayout.LayoutParams p3 = new LinearLayout.LayoutParams(0, dp(24), 1f);
        p3.setMargins(0, dp(4), 0, 0);
        btns.addView(btnSelect, p3);

        root.addView(btns);

        tvHint = tv("运行中 · 按音量下键暂停", 8, "#4ADE80", false);
        tvHint.setPadding(0, dp(4), 0, 0);
        tvHint.setVisibility(View.GONE);
        root.addView(tvHint);

        ProbeService.setVolumeKeyListener(this::onVolumeDown);
        loadAutoSwitch();

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

        try {
            Intent ka = new Intent(ctx, KeepAliveService.class);
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                ctx.startForegroundService(ka);
            } else {
                ctx.startService(ka);
            }
        } catch (Throwable ignored) {}

        Logger.i(TAG, "悬浮窗已显示，规则=" + ruleSet.name);

        new Thread(() -> {
            ocr.init(ctx);
            ui.post(() -> {
                if (ocr.isReady()) setStatus("就绪");
                else setStatus("OCR失败");
            });
        }, "ocr-init").start();
    }

    public void hide() {
        stopCollect();

        stopAll();

        if (recorder != null) {
            recorder.stop();
            recorder = null;
        }

        ProbeService.setVolumeKeyListener(null);
        ProbeService.captureVolumeKey = false;
        try { if (root != null) wm.removeView(root); } catch (Exception ignored) {}
        root = null;
        Logger.flushAll();
        Logger.i(TAG, "悬浮窗已关闭");
    }

    private int collectType = RegionStore.TYPE_SCAN;

    private void toggleCollect() {
        if (overlay != null) {
            stopCollect();
            btnSelect.setText("设置");
            btnSelect.setBackground(chipBg("#25FFFFFF"));
        } else {
            showCollectMenu();
        }
    }

    private void showCollectMenu() {
        if (!Settings.canDrawOverlays(ctx)) return;

        final LinearLayout panel = new LinearLayout(ctx);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(10), dp(10), dp(10), dp(10));
        panel.setBackground(bg("#F00D1119", 14, "#507C4DFF"));

        final ScrollView scroller = new ScrollView(ctx);
        scroller.setVerticalScrollBarEnabled(true);
        scroller.setFillViewport(false);
        scroller.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
        final LinearLayout body = new LinearLayout(ctx);
        body.setOrientation(LinearLayout.VERTICAL);

        LinearLayout head = new LinearLayout(ctx);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = tv("设置", 13, C_TEXT, true);
        head.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView close = tv("×", 15, C_SUB, false);
        close.setPadding(dp(8), 0, dp(4), 0);
        close.setOnClickListener(v -> {
            try { wm.removeView(panel); } catch (Throwable ignored) {}
        });
        head.addView(close);

        panel.addView(head, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(24)));

        LinearLayout sec1 = addDrawer(body, "框选区域", false);
        sec1.addView(regionRow("识别区域", "属性面板 / 卡带详情 OCR 范围",
                RegionStore.TYPE_SCAN));

        final LinearLayout secLock = addDrawer(body, "自动上锁 / 弃置", true);

        LinearLayout swRow = new LinearLayout(ctx);
        swRow.setOrientation(LinearLayout.HORIZONTAL);
        swRow.setGravity(Gravity.CENTER_VERTICAL);
        swRow.setPadding(dp(8), dp(6), dp(8), dp(6));
        swRow.setBackground(bg("#14FFFFFF", 7, null));

        TextView swName = tv("自动操作", 11, C_TEXT, true);
        swRow.addView(swName, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final TextView swBtn = tv("", 9, C_SUB, true);
        swBtn.setGravity(Gravity.CENTER);
        swBtn.setPadding(dp(11), dp(5), dp(11), dp(5));
        refreshSwitchBtn(swBtn, AutoRule.isEnabled());

        swBtn.setOnClickListener(v -> {
            AutoRule.setEnabled(!AutoRule.isEnabled());
            refreshSwitchBtn(swBtn, AutoRule.isEnabled());
        });
        swRow.addView(swBtn);

        LinearLayout.LayoutParams swl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        swl.bottomMargin = dp(4);
        secLock.addView(swRow, swl);

        secLock.addView(regionRow("锁定按钮", "自动上锁的点击位置",
                RegionStore.TYPE_LOCK));
        secLock.addView(regionRow("弃置按钮", "自动弃置的点击位置",
                RegionStore.TYPE_DISCARD));

        final LinearLayout sec2 = addDrawer(body, "全自动", true);

        final LinearLayout swRow2 = new LinearLayout(ctx);
        swRow2.setOrientation(LinearLayout.HORIZONTAL);
        swRow2.setGravity(Gravity.CENTER_VERTICAL);
        swRow2.setPadding(dp(8), dp(6), dp(8), dp(6));
        swRow2.setBackground(bg("#14FFFFFF", 7, null));

        LinearLayout swCol2 = new LinearLayout(ctx);
        swCol2.setOrientation(LinearLayout.VERTICAL);

        TextView swName2 = tv("自动切换", 11, C_TEXT, true);
        swCol2.addView(swName2);

        AutoPoints apInfo0 = AutoPoints.load();

        TextView apInfo = tv(apInfo0.summary(), 9,
                apInfo0.hasCells() ? C_SUB : "#F87171", false);
        apInfo.setPadding(0, dp(1), 0, 0);
        swCol2.addView(apInfo);

        swRow2.addView(swCol2, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        boolean sw2On = autoSwitch;
        final TextView swBtn2 = tv("", 9, C_SUB, true);
        swBtn2.setGravity(Gravity.CENTER);
        swBtn2.setPadding(dp(11), dp(5), dp(11), dp(5));
        refreshSwitchBtn(swBtn2, sw2On);
        swBtn2.setOnClickListener(v -> {
            if (!autoSwitch) {
                AutoPoints check = AutoPoints.load();
                if (!check.hasCells()) {
                    toast("请先录制点位");
                    return;
                }
            }
            autoSwitch = !autoSwitch;
            saveAutoSwitch(autoSwitch);
            refreshSwitchBtn(swBtn2, autoSwitch);
        });
        swRow2.addView(swBtn2);

        LinearLayout.LayoutParams swl2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        swl2.topMargin = dp(4);
        swl2.bottomMargin = dp(4);
        sec2.addView(swRow2, swl2);

        sec2.addView(autoEntryRow("录制点位", "列表格子的点击顺序",
                AutoPoints.load().hasCells()
                        ? AutoPoints.load().count() + " 个"
                        : "未录制",
                "录制", () -> {
                    try { wm.removeView(panel); } catch (Throwable ignored) {}
                    startPointRecord();
                }));

        final AutoPoints apOne = AutoPoints.load();
        sec2.addView(autoEntryRow("执行本页", "跑完这一页就停，翻页后手动再点开始",
                apOne.hasCells() ? "就绪" : "请先录制点位",
                "开始", () -> {
                    if (!requireReady()) return;
                    try { wm.removeView(panel); } catch (Throwable ignored) {}
                    runAuto(AutoRunner.MODE_ONE_PAGE, 1, 1);
                }));

        scroller.addView(body, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams scLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        panel.addView(scroller, scLp);

        TextView done = tv("完成", 12, "#FFFFFF", true);
        done.setGravity(Gravity.CENTER);
        done.setBackground(bg(C_ACCENT, 8, null));
        done.setOnClickListener(v -> {
            try { wm.removeView(panel); } catch (Throwable ignored) {}
        });
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(32));
        dl.topMargin = dp(10);
        panel.addView(done, dl);

        WindowManager.LayoutParams plp = new WindowManager.LayoutParams();
        plp.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        plp.format = PixelFormat.TRANSLUCENT;
        plp.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        plp.gravity = Gravity.CENTER;
        plp.width = dp(230);

        int screenH;
        try {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.view.WindowMetrics wmt =
                        ((WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE))
                                .getCurrentWindowMetrics();
                screenH = wmt.getBounds().height();
            } else {
                screenH = ctx.getResources().getDisplayMetrics().heightPixels;
            }
        } catch (Throwable t) {
            screenH = ctx.getResources().getDisplayMetrics().heightPixels;
        }
        plp.height = (int) (screenH * 0.80f);

        try {
            wm.addView(panel, plp);
        } catch (Throwable e) {
            toast("面板打开失败");
        }
    }

    private TextView sectionHeader(String name, boolean foldable) {
        TextView t = tv((foldable ? "▸ " : "") + name, 11,
                foldable ? C_ACCENT : C_SUB, true);
        t.setPadding(0, dp(8), 0, dp(4));
        return t;
    }

    private void refreshSwitchBtn(TextView btn, boolean on) {
        btn.setText(on ? "已开启" : "已关闭");
        btn.setTextColor(on ? Color.WHITE : Color.parseColor(C_SUB));
        btn.setBackground(on
                ? bg("#CC2E7D32", 6, null)
                : bg("#1AFFFFFF", 6, "#40FFFFFF"));
    }

    private LinearLayout addDrawer(LinearLayout parent, String name, boolean expanded) {
        final LinearLayout content = new LinearLayout(ctx);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(2), 0, dp(2));
        content.setVisibility(expanded ? View.VISIBLE : View.GONE);

        final TextView head = sectionHeader(name, true);
        head.setText((expanded ? "▾ " : "▸ ") + name);
        head.setOnClickListener(v -> {
            boolean vis = content.getVisibility() == View.VISIBLE;
            content.setVisibility(vis ? View.GONE : View.VISIBLE);
            head.setText((vis ? "▸ " : "▾ ") + name);
        });

        parent.addView(head);
        parent.addView(content);
        return content;
    }

    private LinearLayout regionRow(final String name, String desc, final int type) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(7), dp(8), dp(7));
        row.setBackground(bg("#14FFFFFF", 7, null));

        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rl.bottomMargin = dp(4);

        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);

        TextView n = tv(name, 11, C_TEXT, true);
        col.addView(n);

        String cur = RegionStore.describe(type);
        TextView d = tv(cur, 9,
                "未设置".equals(cur) ? "#F87171" : C_SUB, false);
        d.setPadding(0, dp(1), 0, 0);
        col.addView(d);

        row.addView(col, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView btn = tv("框选", 9, C_ACCENT, false);
        btn.setPadding(dp(9), dp(4), dp(9), dp(4));
        btn.setBackground(bg("#207C4DFF", 6, C_ACCENT));
        btn.setOnClickListener(v -> {
            try {
                ViewGroup pp = (ViewGroup) row.getParent().getParent();
                wm.removeView(pp);
            } catch (Throwable ignored) {}
            collectType = type;
            startCollect();
        });
        row.addView(btn);

        boolean isBtn = (type == RegionStore.TYPE_LOCK)
                || (type == RegionStore.TYPE_DISCARD);
        if (isBtn) {
            TextView test = tv("测试", 9, "#4ADE80", false);
            test.setPadding(dp(9), dp(4), dp(9), dp(4));
            test.setBackground(bg("#1A4ADE80", 6, "#704ADE80"));
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            tl.leftMargin = dp(5);
            test.setOnClickListener(v -> {
                int[] c = RegionStore.center(type);
                if (c == null) {
                    toast("请先框选");
                    return;
                }
                ProbeService svc = ProbeService.get();
                if (svc == null) {
                    toast("无障碍未连接");
                    return;
                }
                svc.tap(c[0], c[1]);
                String nm = type == RegionStore.TYPE_LOCK ? "锁定键" : "弃置键";
                toast("测试点击 " + nm + " (" + c[0] + "," + c[1] + ")");
                setStatus("▶ " + nm + " (" + c[0] + "," + c[1] + ")");
                Logger.appendTo("trace.txt", "测试点击 " + nm
                        + " (" + c[0] + "," + c[1] + ")");
            });
            row.addView(test, tl);
        }

        if (RegionStore.has(type)) {

            boolean hasDefault = !isBtn;
            TextView op = tv(hasDefault ? "恢复默认" : "清除", 9,
                    hasDefault ? "#FFB300" : "#F87171", false);
            op.setPadding(dp(9), dp(4), dp(9), dp(4));
            op.setBackground(bg(hasDefault ? "#1AFFB300" : "#1AF87171", 6,
                    hasDefault ? "#70FFB300" : "#70F87171"));
            LinearLayout.LayoutParams ol = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            ol.leftMargin = dp(5);
            op.setOnClickListener(v -> {
                RegionStore.clear(type);
                toast(hasDefault ? (RegionStore.nameOf(type) + "已恢复默认") : "已清除");

                try {
                    ViewGroup pp = (ViewGroup) row.getParent().getParent();
                    wm.removeView(pp);
                } catch (Throwable ignored) {}
                showCollectMenu();
            });
            row.addView(op, ol);
        }

        return row;
    }

    private void startCollect() {
        if (!Settings.canDrawOverlays(ctx)) return;
        if (overlay == null) {
            overlay = new CollectOverlay(ctx);
            overlay.setListener(new CollectOverlay.Listener() {
                @Override public void onRegion(Rect r) {
                    int t = collectType;
                    RegionStore.set(t, r.left, r.top, r.width(), r.height());
                    String name = t == RegionStore.TYPE_SCAN ? "识别区"
                            : (t == RegionStore.TYPE_LOCK ? "锁定键" : "弃置键");
                    Logger.appendTo("trace.txt", "框选保存 type=" + t + " (" + name
                            + ") " + r.left + "," + r.top + " " + r.width() + "x" + r.height());
                    setStatus(name + " " + r.width() + "×" + r.height());
                    toast(name + "已保存");
                    ui.post(() -> {
                        stopCollect();
                        btnSelect.setText("设置");
                        btnSelect.setBackground(chipBg("#25FFFFFF"));
                    });
                }
            });
        }

        ProbeService svc = ProbeService.get();
        if (svc != null) {
            new Thread(() -> {
                Bitmap snap = svc.screenshot();
                if (snap != null) {
                    ui.post(() -> {
                        if (overlay != null) {
                            overlay.setSnapshot(snap);
                            Logger.appendTo("trace.txt", "放大镜快照 "
                                    + snap.getWidth() + "x" + snap.getHeight());
                        }
                    });
                } else {
                    Logger.appendTo("trace.txt", "放大镜快照失败");
                }
            }, "lens-snap").start();
        } else {
            Logger.appendTo("trace.txt", "放大镜：服务未连接");
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
        btnSelect.setText("取消");
        btnSelect.setBackground(chipBg("#CC7C4DFF"));

        String tip = collectType == RegionStore.TYPE_SCAN ? "识别区"
                : (collectType == RegionStore.TYPE_LOCK ? "锁定键" : "弃置键");
        setStatus("框选 " + tip + "…");
        toast("框住" + tip + "，松手生效");
    }

    private void stopCollect() {
        if (overlay != null) {
            try { wm.removeView(overlay); } catch (Throwable ignored) {}
            overlay.release();
            overlay = null;
        }
    }

    private void start() {

        if (running) {
            if (paused) resume();
            return;
        }
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
        paused = false;
        setStatus("运行中");
        btnStart.setText("开始");
        btnStart.setBackground(chipBg("#CC2E7D32"));
        btnPause.setText("暂停");
        btnPause.setBackground(chipBg("#CC7C4DFF"));
        btnStop.setBackground(chipBg("#CCB3261E"));
        ProbeService.captureVolumeKey = true;
        if (tvHint != null) {
            tvHint.setText("运行中 · 按音量下键暂停");
            tvHint.setVisibility(View.VISIBLE);
        }

        Logger.appendTo("trace.txt", "=== 开始 === 服务=" + ProbeService.isReady()
                + " OCR=" + ocr.isReady()
                + " 自动=" + AutoRule.isEnabled()
                + " 锁定规则=" + AutoRule.getLock()
                + " 弃置规则=" + AutoRule.getDiscard());

        worker = new Thread(() -> {
            int round = 0;
            while (running) {

                if (paused) {
                    try { Thread.sleep(150); } catch (InterruptedException e) { break; }
                    continue;
                }
                round++;
                long t0 = System.currentTimeMillis();
                try {
                    dispatchTick(round);
                } catch (Throwable e) {
                    Logger.appendTo("trace.txt", "r" + round + " 异常: " + e);
                }

                Logger.flushAll();
                long cost = System.currentTimeMillis() - t0;
                try { Thread.sleep(Math.max(200, 900 - cost)); }
                catch (InterruptedException e) { break; }
            }
            Logger.flushAll();
        }, "affix-worker");
        worker.start();

        if (autoSwitch) {
            runAuto(AutoRunner.MODE_ONE_PAGE, 1, 1);
        }
    }

    private void pause() {

        if (running && paused) {
            stopAll();
            restoreNormalUI();
            return;
        }
        if (!running || paused) return;
        paused = true;

        if (autoRunner != null && autoRunner.isRunning() && !autoRunner.isPaused()) {
            autoRunner.togglePause();
        }
        setTransparent(false);
        if (tvHint != null) {
            tvHint.setText("已暂停 · 点「继续」或按音量下键恢复");
            tvHint.setVisibility(View.VISIBLE);
        }
        setStatus("⏸ 已暂停");
        ui.post(() -> {
            btnStart.setText("继续");
            btnStart.setBackground(chipBg("#CC2E7D32"));
            btnPause.setText("停止");
            btnPause.setBackground(chipBg("#CCB3261E"));
            btnStop.setText("结束");
            btnStop.setBackground(chipBg("#CCB3261E"));
        });
    }

    private void resume() {
        if (!running || !paused) return;
        paused = false;
        if (autoRunner != null && autoRunner.isRunning() && autoRunner.isPaused()) {
            autoRunner.togglePause();
        }
        setTransparent(autoSwitch && autoRunner != null && autoRunner.isRunning());
        if (tvHint != null) {
            tvHint.setText("运行中 · 按音量下键暂停");
            tvHint.setVisibility(View.VISIBLE);
        }
        setStatus("运行中");
        ui.post(() -> {
            btnStart.setText("开始");
            btnStart.setBackground(chipBg("#CC2E7D32"));
            btnPause.setText("暂停");
            btnPause.setBackground(chipBg("#CC7C4DFF"));
            btnStop.setText("停止");
            btnStop.setBackground(chipBg("#CCB3261E"));
        });
        Logger.appendTo("trace.txt", "=== 继续 ===");
    }

    private void stopAll() {
        running = false;
        paused = false;
        if (worker != null) { worker.interrupt(); worker = null; }
        if (autoRunner != null && autoRunner.isRunning()) {
            autoRunner.stop("用户停止");
        }
        setTransparent(false);
        ProbeService.captureVolumeKey = false;
        if (tvHint != null) tvHint.setVisibility(View.GONE);
        setStatus("已停止");

        ui.post(() -> {
            if (btnStart != null) {
                btnStart.setText("开始");
                btnStart.setBackground(chipBg("#CC2E7D32"));
            }
            if (btnPause != null) {
                btnPause.setText("暂停");
                btnPause.setBackground(chipBg("#25FFFFFF"));
            }
            if (btnStop != null) {
                btnStop.setText("停止");
                btnStop.setBackground(chipBg("#25FFFFFF"));
            }
        });
    }

    public String handleOneCard(int round) {
        ProbeService svc = ProbeService.get();
        if (svc == null) return null;

        Bitmap bmp = svc.screenshot();
        if (bmp == null) {
            Logger.appendTo("trace.txt", "r" + round + " ✗ 截屏失败");
            return null;
        }

        try {
            if (bmp.getWidth() < bmp.getHeight()) {
                Logger.appendTo("trace.txt", "r" + round + " ✗ 竖屏");
                return null;
            }
            int[] region = RegionStore.scan();
            List<OcrEngine.Box> boxes = ocr.recognize(bmp, region);
            if (boxes == null) boxes = new ArrayList<>();

            String type = currentTarget();
            boolean cassette;
            if (RuleType.DRIVE.equals(type)) {
                cassette = false;
            } else if (RuleType.CASSETTE.equals(type)) {
                cassette = true;
            } else {
                cassette = looksLikeCassette(boxes);
            }

            if (cassette) return tickCassetteOnce(round, bmp, boxes);
            return tickOnce(round, bmp, boxes);
        } catch (Throwable t) {
            Logger.appendTo("trace.txt", "r" + round + " 识别异常: " + t);
            return null;
        } finally {
            if (!bmp.isRecycled()) bmp.recycle();
        }
    }

    private void dispatchTick(int round) {
        String type = currentTarget();
        ProbeService svc = ProbeService.get();
        if (svc == null) {
            Logger.appendTo("trace.txt", "r" + round + " ✗ 服务为空");
            setStatus("无障碍未连接");
            return;
        }

        Bitmap bmp = svc.screenshot();
        if (bmp == null) {
            Logger.appendTo("trace.txt", "r" + round + " ✗ 截屏失败");
            setStatus("截屏失败");
            return;
        }

        try {
            if (bmp.getWidth() < bmp.getHeight()) {
                Logger.appendTo("trace.txt", "r" + round + " ✗ 竖屏 "
                        + bmp.getWidth() + "x" + bmp.getHeight());
                setStatus("请切到游戏");
                return;
            }

            int[] region = RegionStore.scan();
            List<OcrEngine.Box> boxes = ocr.recognize(bmp, region);
            if (boxes == null) boxes = new ArrayList<>();

            final boolean cassette;
            if (RuleType.DRIVE.equals(type)) {
                cassette = false;
            } else if (RuleType.CASSETTE.equals(type)) {
                cassette = true;
            } else {
                cassette = looksLikeCassette(boxes);
            }

            try {
                if (cassette) {
                    tickCassetteOnce(round, bmp, boxes);
                } else {
                    tickOnce(round, bmp, boxes);
                }
            } catch (Throwable t) {
                Logger.appendTo("trace.txt", "r" + round + (cassette ? " 空幕" : "") + "识别异常: " + t);
            }
        } finally {
            if (!bmp.isRecycled()) bmp.recycle();
        }
    }

    private String currentTarget() {
        String a = ruleSet != null ? RuleType.normalize(ruleSet.target) : RuleType.BOTH;
        String b = cassetteRule != null
                ? RuleType.normalize(cassetteRule.target) : RuleType.BOTH;

        if (RuleType.BOTH.equals(a) || RuleType.BOTH.equals(b)) return RuleType.BOTH;

        if (!a.equals(b)) return RuleType.BOTH;

        return a;
    }

    private boolean looksLikeCassette(List<OcrEngine.Box> boxes) {
        try {
            if (cassetteRule == null) {
                cassetteRule = CassetteRule.load(ctx);
                cassetteReader = new CassetteReader(cassetteRule);
            }
            if (boxes == null) return false;
            for (OcrEngine.Box b : boxes) {
                if (b.text == null) continue;
                if (cassetteRule.matchSet(b.text) != null) {
                    Logger.appendTo("trace.txt", "界面=空幕 (套装: " + b.text + ")");
                    return true;
                }
            }
            Logger.appendTo("trace.txt", "界面=驱动块 (无套装名)");
            return false;
        } catch (Throwable e) {
            Logger.appendTo("trace.txt", "界面判断异常: " + e);
            return false;
        }
    }

    private boolean autoLockOrDiscard(ProbeService svc, int round,
                                      String grade, String reason, String sig) {
        if (!running || !AutoRule.anyEnabled()) {
            if (round % 20 == 1) {
                Logger.appendTo("trace.txt", "r" + round + " 自动未启用");
            }
            return false;
        }

        int target;
        String act;
        if (AutoRule.shouldLock(grade)) {
            target = RegionStore.TYPE_LOCK;
            act = "锁定";
        } else if (AutoRule.shouldDiscard(grade)) {
            target = RegionStore.TYPE_DISCARD;
            act = "弃置";
        } else {
            lastSig = null;
            return false;
        }

        int[] center = RegionStore.center(target);
        if (center == null) {
            String miss = target == RegionStore.TYPE_LOCK ? "未框选锁定按钮" : "未框选弃置按钮";
            ui.post(() -> tvReason.setText(miss));
            Logger.appendTo("trace.txt", "r" + round + " " + miss);
            return false;
        }

        String fullSig = grade + "|" + sig;
        boolean same = fullSig.equals(lastSig);
        Logger.appendTo("trace.txt", "r" + round + " 准备" + act
                + " (" + center[0] + "," + center[1] + ") 评级=" + grade
                + " sig=" + sig + " same=" + same);
        if (same) return false;

        boolean tapped2 = svc.tapAndWait(center[0], center[1], 1200);
        lastSig = fullSig;
        final String fAct = act;
        final int[] fc = center;
        if (!tapped2) {
            Logger.appendTo("trace.txt", "r" + round + " 点击" + act + "未完成，已放行");
            lastSig = null;
        }
        ui.post(() -> {
            tvStatus.setText("▶ 点击" + fAct + " (" + fc[0] + "," + fc[1] + ")");
            tvReason.setText(reason + " → 自动" + fAct);
        });
        try { Thread.sleep(700); } catch (InterruptedException ignored) {}
        return true;
    }

    private String tickOnce(int round, Bitmap bmp, List<OcrEngine.Box> boxes) {
        ProbeService svc = ProbeService.get();
        if (svc == null) {
            Logger.appendTo("trace.txt", "r" + round + " ✗ 服务为空");
            setStatus("无障碍未连接");
            return null;
        }
        if (round == 1) {
            Logger.appendTo("trace.txt", "r1 截图 " + bmp.getWidth()
                    + "x" + bmp.getHeight());
        }

        long t0 = System.currentTimeMillis();

        int[] region = RegionStore.scan();

        if (com.hollow.affix.BuildConfig.DEBUG_SHOT && debugOnce) {
            Logger.saveDebug(bmp, region, "r" + round);
            debugOnce = false;
        }

        List<String> names = new ArrayList<>();
        List<String> pretty = new ArrayList<>();
        int rx = region[0], rw = region[2];
        for (OcrEngine.Box b : boxes) {

            if (b.x > rx + rw * 0.78f) continue;
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

        Rating.Result rs = Rating.evaluate(names, ruleSet);

        boolean didTap = autoLockOrDiscard(svc, round,
                rs.grade, rs.reason, String.join(",", names));

        final String grade = rs.grade;
        final String color = rs.color;
        final int score = rs.score;
        final String reason = rs.reason;
        final String[] affixStr = new String[4];
        final int[] affixColors = new int[4];
        final int cNormal = Color.parseColor(RuleSet.COLOR_NORMAL);
        for (int i = 0; i < 4; i++) {
            affixStr[i] = i < pretty.size() ? "· " + pretty.get(i) : "· —";

            affixColors[i] = i < names.size()
                    ? Color.parseColor(ruleSet.colorForAffix(names.get(i)))
                    : cNormal;
        }
        final int roundNo = round;
        final long cost = System.currentTimeMillis() - t0;

        final boolean tapped = didTap;
        ui.post(() -> {
            try {
                tvGrade.setText(grade);
                tvGrade.setTextColor(parseColorSafe(color));
                tvScore.setText(score + "分");
                tvReason.setText(reason);
                for (int i = 0; i < 4; i++) {
                    tvAffix[i].setText(affixStr[i]);
                    tvAffix[i].setTextColor(affixColors[i]);
                }

                if (!tapped) {
                    tvStatus.setText(roundNo + "轮 " + cost + "ms");
                }
            } catch (Throwable e) {
                Logger.appendTo("trace.txt", "r" + roundNo + " 界面刷新异常: " + e);
            } finally {

                forceRedraw();
            }
        });

        StringBuilder sb = new StringBuilder();
        sb.append("轮次\t").append(round).append('\n');
        sb.append("规则\t").append(ruleSet.name).append('\n');
        for (String a : pretty) sb.append("词条\t").append(a).append('\n');
        sb.append("判定\t").append(grade).append('\t').append(score).append('\n');
        sb.append("耗时\t").append(cost).append("ms\n");
        Logger.appendTo("ocr_cards.txt", sb.toString());

        return rs.grade + "|" + String.join(",", names);
    }

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

    private boolean requireReady() {
        AutoPoints ap = AutoPoints.load();
        if (!ap.hasCells()) { toast("请先录制点位"); return false; }
        return true;
    }

    private LinearLayout autoEntryRowAuto(String name, String desc,
                                          java.util.function.Supplier<String> value,
                                          String btnText,
                                          java.util.function.Consumer<TextView> onClick) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(7), dp(8), dp(7));
        row.setBackground(bg("#14FFFFFF", 7, null));

        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rl.bottomMargin = dp(4);
        row.setLayoutParams(rl);

        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);

        TextView n = tv(name, 11, C_TEXT, true);
        col.addView(n);

        String v = value.get();
        final TextView d = tv(v, 9,
                v.startsWith("未") ? "#F87171" : C_SUB, false);
        d.setPadding(0, dp(1), 0, 0);
        col.addView(d);

        row.addView(col, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView btn = tv(btnText, 9, C_ACCENT, false);
        btn.setPadding(dp(9), dp(4), dp(9), dp(4));
        btn.setBackground(bg("#207C4DFF", 6, C_ACCENT));
        btn.setOnClickListener(v2 -> onClick.accept(d));
        row.addView(btn);

        return row;
    }

    private LinearLayout autoEntryRow(String name, String desc,
                                      String value, String btnText, Runnable onClick) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(7), dp(8), dp(7));
        row.setBackground(bg("#14FFFFFF", 7, null));

        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rl.bottomMargin = dp(4);
        row.setLayoutParams(rl);

        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);

        TextView n = tv(name, 11, C_TEXT, true);
        col.addView(n);

        TextView d = tv(value, 9,
                "未录制".equals(value) || "未设置".equals(value) ? "#F87171" : C_SUB, false);
        d.setPadding(0, dp(1), 0, 0);
        col.addView(d);

        row.addView(col, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView btn = tv(btnText, 9, C_ACCENT, false);
        btn.setPadding(dp(9), dp(4), dp(9), dp(4));
        btn.setBackground(bg("#207C4DFF", 6, C_ACCENT));
        btn.setOnClickListener(v -> onClick.run());
        row.addView(btn);

        return row;
    }

    private void startPointRecord() {
        if (recorder != null) return;
        AutoPoints data = AutoPoints.load();

        data.cells.clear();
        recorder = new AutoRecordView(ctx, true, data, new AutoRecordView.Listener() {
            @Override
            public void onPointAdded(int count) {
                ui.post(() -> {
                    int total = AutoPoints.CELLS_PER_PAGE;
                    tvStatus.setText("已录 " + count + " / " + total);
                    if (count >= total) {
                        tvGrade.setText("录制完成");
                        tvReason.setText("已录满 " + total + " 个，点位已保存");
                    } else {
                        tvGrade.setText("点击第 " + (count + 1) + " 个");
                        tvReason.setText("点位录制中（一页固定 " + total + " 个）");
                    }
                });
            }

            @Override
            public void onSwipeChanged(int x1, int y1, int x2, int y2) {}

            @Override
            public void onPointFinished(AutoPoints d) {
                recorder = null;
                final boolean full = d.count() == AutoPoints.CELLS_PER_PAGE;
                ui.post(() -> {
                    restoreNormalUI();
                    setStatus(full
                            ? "点位已保存 " + d.count() + " 个"
                            : "已保存 " + d.count() + " 个，但一页应为 "
                              + AutoPoints.CELLS_PER_PAGE + " 个");
                });
                toast(full
                        ? "点位已保存：" + d.count() + " 个"
                        : "注意：录了 " + d.count() + " 个，一页应为 "
                          + AutoPoints.CELLS_PER_PAGE + " 个");
            }

            @Override
            public void onMaskShown() {

                ui.postDelayed(FloatWindow.this::bringToFront, 120);
            }

            @Override
            public void onSwipeFinished(AutoPoints d) {}

            @Override
            public void onCancelled() {
                recorder = null;
                ui.post(FloatWindow.this::restoreNormalUI);
                toast("已取消");
            }
        });

        enterRecordUI(true);
        recorder.start();
    }

    private void startSwipeSetup() {
        if (recorder != null) return;
        AutoPoints data = AutoPoints.load();
        recorder = new AutoRecordView(ctx, false, data, new AutoRecordView.Listener() {
            @Override
            public void onPointAdded(int count) {}

            @Override
            public void onSwipeChanged(int x1, int y1, int x2, int y2) {
                ui.post(() -> {
                    int dist = Math.abs(y1 - y2);

                    String hint;
                    if (dist < 300) hint = "距离偏短，可能翻不动";
                    else if (dist > 1800) hint = "距离偏长，可能一次翻两页";
                    else hint = "距离合适";
                    tvGrade.setText(dist + "px");
                    tvReason.setText(hint + " · (" + x1 + "," + y1 + ")→(" + x2 + "," + y2 + ")");
                });
            }

            @Override
            public void onPointFinished(AutoPoints d) {}

            @Override
            public void onSwipeFinished(AutoPoints d) {
                recorder = null;
                final boolean doTest = swipeThenTest;
                swipeThenTest = false;
                ui.post(() -> {
                    restoreNormalUI();
                    setStatus("滑动已保存");
                });
                if (doTest) {
                    ui.postDelayed(FloatWindow.this::testSwipeOnce, 400);
                } else {
                    toast("滑动已保存");
                }
            }

            @Override
            public void onCancelled() {
                recorder = null;
                ui.post(FloatWindow.this::restoreNormalUI);
                toast("已取消");
            }
        });

        enterRecordUI(false);
        recorder.start();
    }

    private void enterRecordUI(final boolean pointMode) {
        ui.post(() -> {
            if (bodyBox == null) return;

            bodyBox.setVisibility(View.GONE);
            tvStatus.setVisibility(View.VISIBLE);
            tvStatus.setText(pointMode ? "点格子录点位" : "拖上下横线定位，对好之后点完成");
            btnStart.setText("完成");
            btnStart.setOnClickListener(v -> {
                if (recorder == null) return;
                if (pointMode) recorder.finishPoints();
                else recorder.finishSwipe();
            });
            if (pointMode) {
                btnPause.setText("撤销");
                btnPause.setOnClickListener(v -> {
                    if (recorder == null) return;
                    recorder.undo();
                });
            } else {
                btnPause.setText("测试");
                btnPause.setOnClickListener(v -> {
                    if (recorder == null) return;

                    swipeThenTest = true;
                    recorder.finishSwipe();
                });
            }

            btnSelect.setText("取消");
            btnSelect.setOnClickListener(v -> {
                if (recorder != null) recorder.cancel();
            });

            tvRuleName.setText(pointMode ? "录制点位" : "设置滑动");

            try {
                lp.width = dp(150);
                lp.x = 14;
                lp.y = 70;
                wm.updateViewLayout(root, lp);
            } catch (Throwable ignored) {}
        });
    }

    private void testSwipeOnce() {
        final AutoPoints ap = AutoPoints.load();
        if (!ap.hasSwipe) { toast("请先设置滑动"); return; }

        ProbeService svc = ProbeService.get();
        if (svc == null) { toast("无障碍未连接"); return; }

        int x1 = ap.swipeX1, y1 = ap.swipeY1;
        int x2 = ap.swipeX2, y2 = ap.swipeY2;
        svc.swipeAndWait(x1, y1, x2, y2, ap.swipeDuration,
                Math.max(1, ap.swipeSegments));
        Logger.appendTo("trace.txt", "测试滑动 (" + x1 + "," + y1 + ")→(" + x2 + "," + y2
                + ") 时长 " + ap.swipeDuration + "ms 分 " + ap.swipeSegments + " 段");

        long wait = ap.swipeDuration + 600L;
        ui.postDelayed(() -> ui.post(() -> {
            tvGrade.setText("滑动测试");
            tvReason.setText("翻到下一页了吗？");

            btnStart.setText("翻到了");
            btnStart.setOnClickListener(v -> {
                restoreNormalUI();
                setStatus("滑动正常 · 距离 " + Math.abs(y1 - y2) + "px");
                toast("滑动可用");
            });

            btnPause.setText("没翻");
            btnPause.setOnClickListener(v -> {
                restoreNormalUI();
                setStatus("滑动无效 · 请调整横线");
                toast("请检查横线是否对齐游戏滚动条");
            });

            btnSelect.setText("再试");
            btnSelect.setOnClickListener(v -> {
                restoreNormalUI();
                testSwipeOnce();
            });
        }), wait);
    }

    private void forceRedraw() {
        if (root == null) return;
        try {
            root.invalidate();
            root.requestLayout();

            if (lp != null) {
                wm.updateViewLayout(root, lp);
            }
        } catch (Throwable ignored) {}
    }

    private void bringToFront() {
        if (root == null) return;
        try { wm.removeView(root); } catch (Throwable ignored) {}
        try { wm.addView(root, lp); } catch (Throwable ignored) {}
    }

    private void restoreNormalUI() {
        if (bodyBox == null) return;
        bodyBox.setVisibility(View.VISIBLE);
        tvRuleName.setText(ruleTitle());
        tvGrade.setText("—");
        tvScore.setText("");
        tvReason.setText("");
        for (int i = 0; i < 4; i++) tvAffix[i].setText("· —");

        btnStart.setText("开始");
        btnStart.setOnClickListener(v -> start());
        btnPause.setText("暂停");
        btnPause.setOnClickListener(v -> pause());
        btnSelect.setText("设置");
        btnSelect.setOnClickListener(v -> toggleCollect());

        setStatus("就绪");
    }

    private void loadAutoSwitch() {
        autoSwitch = AutoSwitch.isEnabled();
    }

    private void saveAutoSwitch(boolean on) {
        AutoSwitch.setEnabled(on);
    }

    public void syncAutoSwitch(boolean on) {
        autoSwitch = on;
        Logger.appendTo("trace.txt", "自动切换同步为 " + on);
    }

    private void runAuto(int mode, int startPage, int endPage) {

        if (!AutoSwitch.isEnabled()) {
            setStatus("自动化已关闭");
            toast("请先在设置里打开「自动化」");
            return;
        }
        AutoPoints ap = AutoPoints.load();

        if (!ap.hasCells()) {
            setStatus("请先录制点位");
            toast("请先录制点位");
            return;
        }

        if (autoRunner != null && autoRunner.isRunning()) {
            autoRunner.togglePause();
            return;
        }

        autoRunner = new AutoRunner(ap, this::handleOneCard, mode, startPage, endPage,
                new AutoRunner.Callback() {
            @Override
            public void onStep(int page, int pointIndex, int pointTotal) {
                ui.post(() -> {
                    tvStatus.setText("第 " + page + " 页 · 点位 " + pointIndex + "/" + pointTotal);
                });
            }

            @Override
            public void onPageTurn(int newPage) {
                ui.post(() -> tvStatus.setText("翻到第 " + newPage + " 页"));
            }

            @Override
            public void onStop(String reason, int totalPages) {
                ui.post(() -> {
                    setTransparent(false);
                    ProbeService.captureVolumeKey = false;
                    if (tvHint != null) tvHint.setVisibility(View.GONE);
                    setStatus(reason + "（共 " + totalPages + " 页）");

                    if (mode == AutoRunner.MODE_TEST && totalPages > 1) {
                        AutoPoints save = AutoPoints.load();
                        save.totalPages = totalPages;
                        save.save();
                        toast("测试完成：共 " + totalPages + " 页");
                    } else {
                        toast(reason);
                    }
                });
            }
        });

        setTransparent(true);
        ProbeService.captureVolumeKey = true;
        if (tvHint != null) {
            tvHint.setText("按音量下键暂停");
            tvHint.setVisibility(View.VISIBLE);
        }
        autoRunner.start();
    }

    private void showRangePicker() {
        final AutoPoints ap = AutoPoints.load();
        int total = ap.totalPages;
        if (total <= 0) {
            toast("请先执行一次「测试」");
            return;
        }

        final int[] range = {1, total};

        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(12), dp(16), dp(12));

        final TextView tvStart = tv("起始页 1", 12, C_TEXT, false);
        box.addView(tvStart);
        android.widget.SeekBar sb1 = new android.widget.SeekBar(ctx);
        sb1.setMax(total - 1);
        sb1.setProgress(0);
        sb1.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(android.widget.SeekBar sb, int p, boolean fromUser) {
                range[0] = p + 1;
                if (range[0] > range[1]) range[1] = range[0];
                tvStart.setText("起始页 " + range[0]);
            }
        });
        box.addView(sb1);

        final TextView tvEnd = tv("结束页 " + total, 12, C_TEXT, false);
        tvEnd.setPadding(0, dp(10), 0, 0);
        box.addView(tvEnd);
        android.widget.SeekBar sb2 = new android.widget.SeekBar(ctx);
        sb2.setMax(total - 1);
        sb2.setProgress(total - 1);
        sb2.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(android.widget.SeekBar sb, int p, boolean fromUser) {
                range[1] = p + 1;
                if (range[1] < range[0]) range[1] = range[0];
                tvEnd.setText("结束页 " + range[1]);
            }
        });
        box.addView(sb2);

        new android.app.AlertDialog.Builder(ctx)
                .setTitle("部分执行（共 " + total + " 页）")
                .setView(box)
                .setPositiveButton("开始", (d, w) -> runAuto(AutoRunner.MODE_RANGE, range[0], range[1]))
                .setNegativeButton("取消", null)
                .show();
    }

    private static class SimpleSeek implements android.widget.SeekBar.OnSeekBarChangeListener {
        @Override
        public void onProgressChanged(android.widget.SeekBar sb, int p, boolean fromUser) {}
        @Override
        public void onStartTrackingTouch(android.widget.SeekBar sb) {}
        @Override
        public void onStopTrackingTouch(android.widget.SeekBar sb) {}
    }

    private void setTransparent(boolean on) {
        if (root == null || lp == null) return;
        try {
            if (on) {
                lp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            } else {
                lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            }
            wm.updateViewLayout(root, lp);
        } catch (Throwable e) {
            Logger.appendTo("trace.txt", "切换穿透失败: " + e);
        }
    }

    private void onVolumeDown() {
        if (!running) return;
        if (paused) resume();
        else pause();
    }

    public void reloadRules() {
        if (ctx == null) return;

        ruleSet = RuleSet.load(ctx);
        cassetteRule = CassetteRule.load(ctx);
        cassetteReader = new CassetteReader(cassetteRule);
        ui.post(() -> {
            tvRuleName.setText(ruleTitle());
            lastSig = null;
        });
        Logger.i(TAG, "规则已重载: 驱动块=" + ruleSet.name
                + "(" + ruleSet.target + ")"
                + " 空幕=" + cassetteRule.name + "(" + cassetteRule.target + ")");
    }

    private String ruleTitle() {
        String type = currentTarget();
        if (RuleType.DRIVE.equals(type)) return ruleSet != null ? ruleSet.name : "驱动块";
        if (RuleType.CASSETTE.equals(type)) return cassetteRule != null ? cassetteRule.name : "空幕";

        String a = ruleSet != null ? ruleSet.name : "驱动块";
        String b = cassetteRule != null ? cassetteRule.name : "空幕";
        String s = a + " / " + b;
        return s.length() > 14 ? "驱动块 + 空幕" : s;
    }

    private String tickCassetteOnce(int round, Bitmap bmp, List<OcrEngine.Box> boxes) {
        ProbeService svc = ProbeService.get();
        if (svc == null) {
            setStatus("无障碍未连接");
            return null;
        }
        if (cassetteReader == null) {
            cassetteRule = CassetteRule.load(ctx);
            cassetteReader = new CassetteReader(cassetteRule);
        }

        long t0 = System.currentTimeMillis();

        int[] region = RegionStore.scan();

        CassetteReader.Parse p;
        try {
            p = cassetteReader.read(boxes, region);
        } catch (Throwable e) {
            Logger.appendTo("trace.txt", "空幕识别异常: " + e);
            setStatus("识别异常");
            return null;
        }

        CassetteRule.Result r = cassetteRule.evaluate(p.mainAffix, p.subs, p.set);

        long cost = System.currentTimeMillis() - t0;

        boolean didTap = autoLockOrDiscard(svc, round,
                r.grade, "", String.join(",", p.subs) + "|" + p.mainAffix);

        final String grade = r.grade;
        final String color = r.color;
        final int total = r.total;
        final String detail = r.detail;
        final String mainTxt = p.mainAffix == null ? "主属性未识别"
                : (p.mainAffix + (p.mainValue.isEmpty() ? "" : " " + p.mainValue));
        final String setTxt = p.set == null ? "套装未识别" : p.set;
        final String profileTxt = r.profileName;
        final boolean elemHit = r.elementMatch;
        final String[] subStr = new String[4];
        final int[] subColors = new int[4];
        final int cNormal = Color.parseColor(CassetteRule.COLOR_NORMAL);

        final boolean mainOk = r.mainScore > 0;
        for (int i = 0; i < 4; i++) {
            subStr[i] = i < p.subTexts.size() ? "· " + p.subTexts.get(i) : "· —";
            subColors[i] = (i < p.subs.size() && mainOk)
                    ? Color.parseColor(cassetteRule.colorForSub(p.set, p.subs.get(i)))
                    : cNormal;
        }
        final int roundNo = round;
        final String dbg = p.debug;

        CassetteRule.SetDetail sd = cassetteRule.detail(p.set);
        final boolean tapped = didTap;
        ui.post(() -> {
            try {
                tvGrade.setText(grade);
                tvGrade.setTextColor(parseColorSafe(color));
                tvScore.setText(total + "分");
                tvReason.setText(setTxt + " · " + profileTxt + " · " + mainTxt
                        + (elemHit ? " ★" : ""));
                for (int i = 0; i < 4; i++) {
                    tvAffix[i].setText(subStr[i]);
                    tvAffix[i].setTextColor(subColors[i]);
                }

                if (!tapped) {
                    tvStatus.setText(roundNo + "轮 " + cost + "ms");
                }

                if (folded && sd != null) {
                    tvStatus.setText(sd.element + " · " + sd.type + " · 4件 " + sd.four);
                }
            } catch (Throwable e) {
                Logger.appendTo("trace.txt", "r" + roundNo + " 空幕界面异常: " + e);
            } finally {

                forceRedraw();
            }
        });

        StringBuilder sb = new StringBuilder();
        sb.append("轮次\t").append(round).append('\n');
        sb.append("模式\t空幕\n");
        sb.append("套装\t").append(p.set).append('\n');
        if (sd != null) {
            sb.append("套装属性\t").append(sd.element).append('\t').append(sd.type).append('\n');
            sb.append("2件套\t").append(sd.two).append('\n');
            sb.append("4件套\t").append(sd.four).append('\n');
            sb.append("备注\t").append(sd.note).append('\n');
        }
        sb.append("主属性\t").append(p.mainAffix)
          .append('\t').append(p.mainValue).append('\n');
        for (String a : p.subTexts) sb.append("副属性\t").append(a).append('\n');
        sb.append("得分\t主").append(r.mainScore).append("\t副").append(r.subScore)
          .append("\t总").append(total).append("/").append(r.maxTotal)
          .append("\t占比").append(String.format("%.0f%%", r.ratio * 100)).append('\n');
        sb.append("权重档\t").append(r.profileName)
          .append("\t本命=").append(r.elementMatch).append('\n');
        sb.append("判定\t").append(grade).append('\t').append(detail).append('\n');
        sb.append("耗时\t").append(cost).append("ms\n");
        sb.append("解析\t").append(dbg).append('\n');
        Logger.appendTo("cassette.txt", sb.toString());

        return r.grade + "|" + p.mainAffix + "|" + String.join(",", p.subs);
    }

    private void toast(String s) {
        ui.post(() -> android.widget.Toast
                .makeText(ctx, s, android.widget.Toast.LENGTH_SHORT).show());
    }

    private TextView tv(String s, float sp, String color, boolean bold) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(parseColorSafe(color));
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    private int parseColorSafe(String color) {
        if (color == null || color.trim().isEmpty()) {
            return Color.parseColor(C_TEXT);
        }
        try {
            return Color.parseColor(color);
        } catch (Throwable e) {
            return Color.parseColor(C_TEXT);
        }
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
