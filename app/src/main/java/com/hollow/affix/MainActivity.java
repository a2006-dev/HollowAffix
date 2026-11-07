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
        ruleSet = RuleSet.loadBuiltin(this);
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

        LinearLayout ruleHead = new LinearLayout(this);
        ruleHead.setOrientation(LinearLayout.HORIZONTAL);
        ruleHead.setGravity(Gravity.CENTER_VERTICAL);

        TextView ruleLabel = tv("当前规则", 11, C_SUB, false);
        ruleHead.addView(ruleLabel, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView badge = tv("内置", 10, C_ACCENT, false);
        badge.setBackground(bg("#207C4DFF", 6, C_ACCENT));
        badge.setPadding(dp(8), dp(2), dp(8), dp(2));
        ruleHead.addView(badge);

        ruleCard.addView(ruleHead);

        tvRuleName = tv(ruleSet.name, 18, C_ACCENT, true);
        tvRuleName.setPadding(0, dp(6), 0, dp(2));
        ruleCard.addView(tvRuleName);

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
        sb.append("识别区域 ").append(RegionStore.describe());
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