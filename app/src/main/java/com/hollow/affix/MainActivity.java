package com.hollow.affix;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;

/**
 * 主界面：深色卡片风。
 */
public class MainActivity extends AppCompatActivity {

    // 配色
    private static final String C_BG = "#0A0E14";
    private static final String C_CARD = "#151A22";
    private static final String C_CARD2 = "#1C2330";
    private static final String C_ACCENT = "#7C4DFF";
    private static final String C_TEXT = "#E8EAED";
    private static final String C_SUB = "#8B95A5";
    private static final String C_OK = "#4ADE80";
    private static final String C_ERR = "#F87171";

    private LinearLayout container;
    private TextView tvAcc, tvOverlay, tvFile;
    private TextView tvRuleName, tvRuleInfo;
    private FloatWindow fw;
    private RuleSet ruleSet;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ruleSet = RuleSet.load(this);
        buildUI();
    }

    private void buildUI() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.parseColor(C_BG));
        sv.setFillViewport(true);

        container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(18), dp(36), dp(18), dp(36));
        sv.addView(container);

        // ===== 标题 =====
        TextView title = tv("异环OCR助手", 26, C_TEXT, true);
        container.addView(title);

        TextView sub = tv("实时识别 · 自动评级", 12, C_SUB, false);
        sub.setPadding(0, dp(4), 0, dp(20));
        container.addView(sub);

        // ===== 当前规则卡片 =====
        LinearLayout ruleCard = card(C_CARD, 16);
        ruleCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        ruleCard.setOnClickListener(v -> openRuleManager());

        LinearLayout ruleHead = new LinearLayout(this);
        ruleHead.setOrientation(LinearLayout.HORIZONTAL);
        ruleHead.setGravity(Gravity.CENTER_VERTICAL);

        TextView ruleLabel = tv("当前规则", 11, C_SUB, false);
        ruleHead.addView(ruleLabel, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView badge = tv("内置", 10, C_ACCENT, false);
        badge.setBackground(bg("#207C4DFF", 6, C_ACCENT));
        badge.setPadding(dp(8), dp(2), dp(8), dp(2));
        badge.setTag("badge");
        ruleHead.addView(badge);

        TextView arrow = tv("  规则 ›", 11, C_ACCENT, false);
        ruleHead.addView(arrow);

        ruleCard.addView(ruleHead);

        tvRuleName = tv(ruleSet.name, 18, C_ACCENT, true);
        tvRuleName.setPadding(0, dp(6), 0, dp(2));
        ruleCard.addView(tvRuleName);

        // 徽章：外部规则显示「自定义」，否则「内置」
        View badgeV = ruleHead.findViewWithTag("badge");
        if (badgeV instanceof TextView) {
            boolean custom = !RuleStore.currentRule(RuleStore.TYPE_DRIVE).isEmpty();
            ((TextView) badgeV).setText(custom ? "自定义" : "内置");
            ((TextView) badgeV).setTextColor(Color.parseColor(custom ? C_OK : C_ACCENT));
            badgeV.setBackground(bg(custom ? "#204ADE80" : "#207C4DFF", 6,
                    custom ? C_OK : C_ACCENT));
        }

        StringBuilder info = new StringBuilder();
        if (!ruleSet.author.isEmpty()) info.append("作者 ").append(ruleSet.author).append("  ·  ");
        info.append("v").append(ruleSet.version)
            .append("  ·  ").append(ruleSet.rules.size()).append(" 条判定");
        tvRuleInfo = tv(info.toString(), 11, C_SUB, false);
        ruleCard.addView(tvRuleInfo);

        // 等级预览
        LinearLayout gradeRow = new LinearLayout(this);
        gradeRow.setOrientation(LinearLayout.HORIZONTAL);
        gradeRow.setPadding(0, dp(10), 0, 0);
        for (String[] g : new String[][]{
                {"大毕业", "#FF4D4D"}, {"毕业", "#FFB300"},
                {"过渡", "#64B5F6"}, {"垃圾", "#787878"}}) {
            TextView chip = tv(g[0], 10, g[1], true);
            chip.setBackground(bg("#18" + g[1].substring(1), 6, g[1]));
            chip.setPadding(dp(9), dp(3), dp(9), dp(3));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(6);
            gradeRow.addView(chip, lp);
        }
        ruleCard.addView(gradeRow);

        container.addView(ruleCard, wrapLp(0, 12));

        // ===== 权限 =====
        section("权限");

        tvAcc = permCard("无障碍服务", "截屏识别必需", v -> {
            try {
                Intent i = new Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS");
                i.putExtra("android.intent.extra.COMPONENT_NAME",
                        new android.content.ComponentName(this, ProbeService.class));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            } catch (Exception e) {
                try {
                    startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                } catch (Exception ignored) {}
            }
        });

        tvOverlay = permCard("悬浮窗", "显示识别面板", v -> {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        });

        tvFile = permCard("所有文件访问", "保存识别日志", v -> {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
        });

        // ===== 自动操作 =====
        section("自动操作");
        buildAutoCard();

        // ===== 操作 =====
        section("操作");

        TextView btnFloat = bigBtn("打开悬浮窗", C_ACCENT);
        btnFloat.setOnClickListener(v -> toggleFloat());
        container.addView(btnFloat, wrapLp(0, 10));

        TextView btnTest = midBtn("测试识别（用相册图）", C_CARD2);
        btnTest.setOnClickListener(v -> toast("功能开发中"));
        container.addView(btnTest, wrapLp(0, 8));

        // ===== 信息 =====
        section("设备");
        TextView tvDev = tv("", 11, C_SUB, false);
        tvDev.setLineSpacing(dp(4), 1f);
        tvDev.setTag("dev");
        container.addView(tvDev);

        setContentView(sv);
        refresh();
    }

    private void refresh() {
        boolean acc = isAccOn();
        boolean ov = Settings.canDrawOverlays(this);
        boolean fl = isFileOn();

        // 无障碍服务
        if (tvAcc != null) {
            tvAcc.setText((acc ? "✓ " : "✗ ") + "无障碍服务");
            tvAcc.setTextColor(Color.parseColor(acc ? C_OK : C_ERR));
        }
        if (tvOverlay != null) {
            tvOverlay.setText((ov ? "✓ " : "✗ ") + "悬浮窗");
            tvOverlay.setTextColor(Color.parseColor(ov ? C_OK : C_ERR));
        }
        if (tvFile != null) {
            tvFile.setText((fl ? "✓ " : "✗ ") + "所有文件访问");
            tvFile.setTextColor(Color.parseColor(fl ? C_OK : C_ERR));
        }

        int[] sz = screenSize();
        StringBuilder sb = new StringBuilder();
        sb.append(android.os.Build.MODEL).append("  ·  Android ")
          .append(android.os.Build.VERSION.RELEASE).append('\n');
        sb.append("屏幕 ").append(sz[0]).append(" × ").append(sz[1]);
        sb.append(sz[0] == 3168 && sz[1] == 1440 ? "  ✓ 适配" : "  ⚠ 需调整坐标");
        sb.append('\n');
        sb.append("识别区 ").append(RegionStore.describe(RegionStore.TYPE_SCAN));
        sb.append("  ·  锁定键 ").append(RegionStore.describe(RegionStore.TYPE_LOCK));
        sb.append("  ·  弃置键 ").append(RegionStore.describe(RegionStore.TYPE_DISCARD));
        sb.append('\n');
        sb.append("数据目录 ").append(Logger.DIR.getAbsolutePath());

        View dv = container.findViewWithTag("dev");
        if (dv instanceof TextView) ((TextView) dv).setText(sb.toString());
    }

    private boolean isAccOn() {
        if (ProbeService.isReady()) return true;
        String pkg = getPackageName();
        String flat = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(flat)) return false;
        return flat.contains(pkg + "/" + pkg + ".ProbeService")
                || flat.contains(pkg + "/.ProbeService");
    }

    private boolean isFileOn() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            return Environment.isExternalStorageManager();
        }
        return true;
    }

    private int[] screenSize() {
        android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(dm);
        return new int[]{dm.widthPixels, dm.heightPixels};
    }

    private void toggleFloat() {
        if (!Settings.canDrawOverlays(this)) {
            toast("请先授予悬浮窗权限");
            return;
        }
        if (fw == null) {
            fw = new FloatWindow(ruleSet);
            fw.show(this);
            toast("悬浮窗已打开");
        } else {
            fw.hide();
            fw = null;
            toast("悬浮窗已关闭");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    // ==================== 规则管理 ====================

    /** 打开规则管理页 */
    private void openRuleManager() {
        setContentView(new RuleManagerView(this,
                () -> {
                    // 关闭：回到主界面
                    buildUI();
                },
                () -> {
                    // 规则变更：重载当前规则，悬浮窗若开着也立即生效
                    ruleSet = RuleSet.load(this);
                    if (fw != null) fw.reloadRules();
                }));
    }

    // ==================== 自动操作卡片 ====================

    private LinearLayout autoCard;

    private void buildAutoCard() {
        AutoRule.sync(ruleSet);

        autoCard = card(C_CARD, 14);
        autoCard.setPadding(dp(14), dp(12), dp(14), dp(12));

        rebuildAutoRows();
        container.addView(autoCard, wrapLp(0, 0));
    }

    private void rebuildAutoRows() {
        autoCard.removeAllViews();

        // ===== 总开关 =====
        LinearLayout switchRow = new LinearLayout(this);
        switchRow.setOrientation(LinearLayout.HORIZONTAL);
        switchRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout switchCol = new LinearLayout(this);
        switchCol.setOrientation(LinearLayout.VERTICAL);

        TextView swName = tv("自动化", 14, C_TEXT, true);
        switchCol.addView(swName);

        TextView swDesc = tv("关闭后悬浮窗不执行任何自动操作", 10, C_SUB, false);
        swDesc.setPadding(0, dp(2), 0, 0);
        switchCol.addView(swDesc);

        switchRow.addView(switchCol, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        boolean on = AutoRule.isEnabled();
        final TextView sw = tv(on ? "已开启" : "已关闭", 11,
                on ? "#FFFFFF" : C_SUB, true);
        sw.setGravity(Gravity.CENTER);
        sw.setPadding(dp(14), dp(7), dp(14), dp(7));
        sw.setBackground(on
                ? bg("#CC2E7D32", 8, null)
                : bg("#1AFFFFFF", 8, "#40FFFFFF"));
        sw.setOnClickListener(v -> {
            AutoRule.setEnabled(!AutoRule.isEnabled());
            rebuildAutoRows();
        });
        switchRow.addView(sw);

        autoCard.addView(switchRow);

        // 分隔
        View sp0 = new View(this);
        sp0.setBackgroundColor(Color.parseColor("#1AFFFFFF"));
        LinearLayout.LayoutParams sp0l = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        sp0l.topMargin = dp(12);
        sp0l.bottomMargin = dp(10);
        autoCard.addView(sp0, sp0l);

        // ===== 等级勾选（关闭时变灰不可点）=====
        boolean enabled = AutoRule.isEnabled();

        java.util.Set<String> grades = AutoRule.collectGrades(ruleSet);
        java.util.Map<String, String> colorMap = new java.util.LinkedHashMap<>();
        for (String g : grades) colorMap.put(g, ruleSet.colorOf(g));

        // 自动锁定
        TextView t1 = tv("自动锁定", 13,
                enabled ? C_TEXT : "#60FFFFFF", true);
        autoCard.addView(t1);
        TextView d1 = tv("勾选的等级自动点锁定", 10,
                enabled ? C_SUB : "#50FFFFFF", false);
        d1.setPadding(0, dp(2), 0, dp(6));
        autoCard.addView(d1);
        autoCard.addView(gradeRow(grades, colorMap, true, enabled));

        // 分隔
        View sp = new View(this);
        sp.setBackgroundColor(Color.parseColor("#1AFFFFFF"));
        LinearLayout.LayoutParams spl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        spl.topMargin = dp(12);
        spl.bottomMargin = dp(10);
        autoCard.addView(sp, spl);

        // 自动弃置
        TextView t2 = tv("自动弃置", 13,
                enabled ? C_TEXT : "#60FFFFFF", true);
        autoCard.addView(t2);
        TextView d2 = tv("勾选的等级自动点弃置", 10,
                enabled ? C_SUB : "#50FFFFFF", false);
        d2.setPadding(0, dp(2), 0, dp(6));
        autoCard.addView(d2);
        autoCard.addView(gradeRow(grades, colorMap, false, enabled));

        // 按钮状态
        StringBuilder st = new StringBuilder();
        st.append("锁定键 ").append(RegionStore.describe(RegionStore.TYPE_LOCK));
        st.append("   ·   弃置键 ").append(RegionStore.describe(RegionStore.TYPE_DISCARD));
        TextView info = tv(st.toString(), 9, C_SUB, false);
        info.setPadding(0, dp(10), 0, 0);
        autoCard.addView(info);
    }

    /** 一行可勾选的等级标签 */
    private LinearLayout gradeRow(java.util.Set<String> grades,
                                  java.util.Map<String, String> colors,
                                  final boolean isLock,
                                  final boolean enabled) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.HORIZONTAL);
        wrap.setPadding(0, 0, 0, 0);

        java.util.Map<String, Boolean> cur = isLock
                ? AutoRule.getLock() : AutoRule.getDiscard();

        for (final String g : grades) {
            boolean on = cur.get(g) != null && cur.get(g);
            String c = colors.get(g);
            if (c == null) c = C_SUB;

            final TextView chip = tv((on ? "✓ " : "") + g, 11,
                    !enabled ? "#40FFFFFF" : (on ? "#FFFFFF" : c), on && enabled);
            chip.setPadding(dp(11), dp(6), dp(11), dp(6));
            chip.setBackground(!enabled
                    ? bg("#0DFFFFFF", 8, "#30FFFFFF")
                    : (on ? bg(c, 8, null) : bg("#1AFFFFFF", 8, c)));
            chip.setAlpha(enabled ? 1f : 0.5f);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(7);
            wrap.addView(chip, lp);

            if (!enabled) continue;   // 关闭时不可点

            chip.setOnClickListener(v -> {
                boolean now = isLock
                        ? (AutoRule.getLock().get(g) != null && AutoRule.getLock().get(g))
                        : (AutoRule.getDiscard().get(g) != null && AutoRule.getDiscard().get(g));
                boolean next = !now;
                if (isLock) AutoRule.setLock(g, next);
                else AutoRule.setDiscard(g, next);
                rebuildAutoRows();
            });
        }
        return wrap;
    }

    // ==================== UI 组件 ====================

    private void section(String s) {
        TextView t = tv(s, 12, C_SUB, true);
        t.setPadding(0, dp(16), 0, dp(8));
        container.addView(t);
    }

    private LinearLayout card(String color, int radius) {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setBackground(bg(color, radius, "#1FFFFFFF"));
        return v;
    }

    private TextView permCard(String name, String desc, View.OnClickListener onClick) {
        LinearLayout row = card(C_CARD, 12);
        row.setPadding(dp(16), dp(13), dp(16), dp(13));
        row.setOnClickListener(onClick);

        TextView title = tv(name, 14, C_TEXT, false);
        row.addView(title);

        TextView d = tv(desc, 11, C_SUB, false);
        d.setPadding(0, dp(3), 0, 0);
        row.addView(d);

        container.addView(row, wrapLp(0, 8));
        return title;
    }

    private TextView bigBtn(String s, String color) {
        TextView t = tv(s, 15, "#FFFFFF", true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(bg(color, 12, null));
        return t;
    }

    private TextView midBtn(String s, String color) {
        TextView t = tv(s, 13, C_TEXT, false);
        t.setGravity(Gravity.CENTER);
        t.setBackground(bg(color, 12, "#20FFFFFF"));
        return t;
    }

    private TextView tv(String s, float sp, String color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(Color.parseColor(color));
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    private GradientDrawable bg(String fill, int radius, String stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.parseColor(fill));
        g.setCornerRadius(dp(radius));
        if (stroke != null) g.setStroke(dp(1), Color.parseColor(stroke));
        return g;
    }

    private LinearLayout.LayoutParams wrapLp(int top, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(top);
        p.bottomMargin = dp(bottom);
        return p;
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    private void toast(String s) {
        android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_SHORT).show();
    }
}