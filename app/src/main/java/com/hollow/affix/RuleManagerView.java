package com.hollow.affix;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.List;

public class RuleManagerView extends LinearLayout {

    private static final String C_BG = "#0A0E14";
    private static final String C_CARD = "#151A22";
    private static final String C_CARD2 = "#1C2330";
    private static final String C_ACCENT = "#7C4DFF";
    private static final String C_TEXT = "#E8EAED";
    private static final String C_SUB = "#8B95A5";
    private static final String C_OK = "#4ADE80";
    private static final String C_ERR = "#F87171";

    private final Context ctx;
    private final Runnable onClose;
    private final Runnable onRuleChanged;

    private int tab = RuleStore.TYPE_DRIVE;

    private LinearLayout listBox;
    private TextView tabDrive, tabCassette;

    public RuleManagerView(Context context, Runnable onClose, Runnable onRuleChanged) {
        super(context);
        this.ctx = context;
        this.onClose = onClose;
        this.onRuleChanged = onRuleChanged;
        build();
    }

    private void build() {
        setOrientation(VERTICAL);
        setBackgroundColor(Color.parseColor(C_BG));
        setPadding(dp(18), dp(30), dp(18), dp(20));

        LinearLayout head = new LinearLayout(ctx);
        head.setOrientation(HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = tv("规则管理", 22, C_TEXT, true);
        head.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView back = tv("完成", 14, C_ACCENT, true);
        back.setPadding(dp(12), dp(6), dp(4), dp(6));
        back.setOnClickListener(v -> {
            if (onClose != null) onClose.run();
        });
        head.addView(back);
        addView(head);

        TextView hint = tv("点规则名切换，长按删除", 11, C_SUB, false);
        hint.setPadding(0, dp(4), 0, dp(14));
        addView(hint);

        LinearLayout tabs = new LinearLayout(ctx);
        tabs.setOrientation(HORIZONTAL);
        tabs.setPadding(0, 0, 0, dp(10));

        tabDrive = tabBtn("驱动块");
        tabDrive.setOnClickListener(v -> {
            tab = RuleStore.TYPE_DRIVE;
            refresh();
        });
        LinearLayout.LayoutParams t1 = new LinearLayout.LayoutParams(0, dp(36), 1f);
        t1.rightMargin = dp(8);
        tabs.addView(tabDrive, t1);

        tabCassette = tabBtn("空幕");
        tabCassette.setOnClickListener(v -> {
            tab = RuleStore.TYPE_CASSETTE;
            refresh();
        });
        tabs.addView(tabCassette, new LinearLayout.LayoutParams(0, dp(36), 1f));

        addView(tabs);

        ScrollView sv = new ScrollView(ctx);
        listBox = new LinearLayout(ctx);
        listBox.setOrientation(VERTICAL);
        sv.addView(listBox);
        addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout btns = new LinearLayout(ctx);
        btns.setOrientation(HORIZONTAL);
        btns.setPadding(0, dp(12), 0, 0);

        TextView bNew = btn("新建", C_ACCENT, () -> onNew());
        btns.addView(bNew, lb(0, dp(8)));

        TextView bImport = btn("导入", C_CARD2, () -> onImport());
        btns.addView(bImport, lb(dp(8), 0));

        addView(btns);

        refresh();
    }

    private void refresh() {

        styleTab(tabDrive, tab == RuleStore.TYPE_DRIVE);
        styleTab(tabCassette, tab == RuleStore.TYPE_CASSETTE);

        listBox.removeAllViews();

        String active = RuleStore.currentRule(tab);

        RuleStore.Entry bi = RuleStore.builtinEntry(tab);
        listBox.addView(ruleRow(bi, active.isEmpty(), true));

        List<RuleStore.Entry> list = RuleStore.list(tab);
        if (list.isEmpty()) {
            TextView empty = tv("暂无自定义规则\n点下方「新建」创建", 12, C_SUB, false);
            empty.setPadding(dp(4), dp(16), 0, dp(8));
            empty.setLineSpacing(dp(4), 1f);
            listBox.addView(empty);
        } else {
            for (RuleStore.Entry e : list) {
                listBox.addView(ruleRow(e, e.file.getName().equals(active), false));
            }
        }
    }

    private View ruleRow(final RuleStore.Entry e, boolean isActive, boolean builtin) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        row.setBackground(bg(isActive ? "#1E2A3A" : C_CARD, 12,
                isActive ? C_ACCENT : "#1FFFFFFF"));

        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(VERTICAL);

        LinearLayout nameRow = new LinearLayout(ctx);
        nameRow.setOrientation(HORIZONTAL);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);

        if (isActive) {
            TextView dot = tv("● ", 12, C_OK, true);
            nameRow.addView(dot);
        }

        TextView name = tv(e.name.isEmpty() ? "未命名" : e.name, 15,
                e.valid ? C_TEXT : C_ERR, true);
        nameRow.addView(name);

        TextView ver = tv("  v" + e.version, 11, C_SUB, false);
        nameRow.addView(ver);

        if (e.target != null && !e.target.isEmpty()) {
            TextView tTag = tv("  " + RuleType.label(e.target), 10,
                    RuleType.BOTH.equals(RuleType.normalize(e.target)) ? C_OK : C_ACCENT, false);
            nameRow.addView(tTag);
        }

        if (builtin) {
            TextView tag = tv("  内置", 10, C_ACCENT, false);
            nameRow.addView(tag);
        }
        col.addView(nameRow);

        if (!e.valid && !e.error.isEmpty()) {
            TextView errTv = tv(e.error, 10, C_ERR, false);
            errTv.setPadding(0, dp(3), 0, 0);
            col.addView(errTv);
        } else if (!builtin && e.file != null) {
            TextView fn = tv(e.file.getName(), 10, C_SUB, false);
            fn.setPadding(0, dp(3), 0, 0);
            col.addView(fn);
        }

        row.addView(col, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout opsCol = new LinearLayout(ctx);
        opsCol.setOrientation(LinearLayout.VERTICAL);
        opsCol.setGravity(Gravity.CENTER_VERTICAL);

        if (!builtin) {
            TextView edit = miniChip("编辑", C_ACCENT, () -> onEdit(e));
            opsCol.addView(edit);

            TextView exp = miniChip("导出", "#4ADE80", () -> onExport(e));
            LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            elp.topMargin = dp(5);
            opsCol.addView(exp, elp);
        } else {
            TextView copy = miniChip("复制", "#FFB300", () -> onCopyBuiltin());
            opsCol.addView(copy);

            TextView exp = miniChip("导出", "#4ADE80", () -> onExportBuiltin());
            LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            elp.topMargin = dp(5);
            opsCol.addView(exp, elp);
        }

        LinearLayout.LayoutParams oclp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        oclp.leftMargin = dp(8);
        row.addView(opsCol, oclp);

        row.setOnClickListener(v -> {
            if (!e.valid) {
                toast("规则损坏，无法启用");
                return;
            }
            if (builtin) {
                RuleStore.setCurrent(tab, "");
            } else {
                RuleStore.setCurrent(tab, e.file.getName());
            }
            refresh();
            if (onRuleChanged != null) onRuleChanged.run();
            toast("已切换到 " + e.name);
        });

        if (!builtin) {
            row.setOnLongClickListener(v -> {
                confirmDelete(e);
                return true;
            });
        }

        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rl.bottomMargin = dp(8);
        row.setLayoutParams(rl);
        return row;
    }

    private void onNew() {
        if (tab == RuleStore.TYPE_DRIVE) {
            RuleEditorActivity.open(ctx, null, RuleStore.TYPE_DRIVE);
        } else {
            CassetteEditorActivity.open(ctx, null, RuleStore.TYPE_CASSETTE);
        }
    }

    private void onEdit(RuleStore.Entry e) {
        if (tab == RuleStore.TYPE_DRIVE) {
            RuleEditorActivity.open(ctx, e.file, tab);
        } else {
            CassetteEditorActivity.open(ctx, e.file, tab);
        }
    }

    private void onCopyBuiltin() {
        try {
            String json = RuleStore.loadJson(ctx, tab);
            if (json == null) {
                toast("读取内置规则失败");
                return;
            }
            String baseName;
            if (tab == RuleStore.TYPE_DRIVE) {
                baseName = RuleSet.parse(json).name + " 副本";
            } else {
                baseName = CassetteRule.parse(json).name + " 副本";
            }
            askName(baseName, name -> {
                try {
                    String fn = RuleStore.save(ctx, tab, name, json);
                    RuleStore.setCurrent(tab, fn);
                    refresh();
                    if (onRuleChanged != null) onRuleChanged.run();
                    toast("已创建并启用");
                } catch (Throwable t) {
                    toast("创建失败: " + t.getMessage());
                }
            });
        } catch (Throwable t) {
            toast("复制失败: " + t.getMessage());
        }
    }

    private void onImport() {
        RuleEditorActivity.pickImportFile(ctx, tab, () -> {
            refresh();
            if (onRuleChanged != null) onRuleChanged.run();
        });
    }

    private void onExport(RuleStore.Entry e) {
        try {
            String json = RuleStore.readJsonFile(e.file);
            doExport(json, e.name);
        } catch (Throwable t) {
            toast("读取失败: " + t.getMessage());
        }
    }

    private void onExportBuiltin() {
        try {
            String json = RuleStore.loadJson(ctx, tab);
            if (json == null) {
                toast("读取内置规则失败");
                return;
            }
            String name;
            if (tab == RuleStore.TYPE_DRIVE) {
                name = RuleSet.parse(json).name;
            } else {
                name = CassetteRule.parse(json).name;
            }
            doExport(json, name);
        } catch (Throwable t) {
            toast("导出失败: " + t.getMessage());
        }
    }

    private void doExport(String json, String ruleName) {
        try {
            File dir = new File(Logger.DIR, "export");
            if (!dir.exists()) dir.mkdirs();

            String base = RuleStore.safeName(ruleName);
            File f = new File(dir, base + ".json");
            int n = 1;
            while (f.exists()) {
                f = new File(dir, base + "_" + n + ".json");
                n++;
            }

            if (!RuleStore.exportTo(json, f)) {
                toast("写入失败");
                return;
            }

            final File outFile = f;
            final String path = outFile.getAbsolutePath();
            new AlertDialog.Builder(ctx)
                    .setTitle("导出成功")
                    .setMessage("文件名：" + outFile.getName() + "\n\n路径：\n" + path)
                    .setPositiveButton("分享", (d, w) -> shareFile(outFile))
                    .setNeutralButton("关闭", null)
                    .show();
        } catch (Throwable t) {
            toast("导出失败: " + t.getMessage());
        }
    }

    private void shareFile(File f) {
        try {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("application/json");
            android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                    ctx, ctx.getPackageName() + ".fileprovider", f);
            i.putExtra(Intent.EXTRA_STREAM, uri);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            ctx.startActivity(Intent.createChooser(i, "分享规则文件"));
        } catch (Throwable t) {
            toast("分享失败：" + t.getMessage() + "\n文件在 " + f.getAbsolutePath());
        }
    }

    private void confirmDelete(final RuleStore.Entry e) {
        new AlertDialog.Builder(ctx)
                .setTitle("删除规则")
                .setMessage("确定删除「" + e.name + "」？\n文件：" + e.file.getName())
                .setPositiveButton("删除", (d, w) -> {
                    if (RuleStore.delete(e.file.getName())) {
                        String active = RuleStore.currentRule(tab);
                        if (e.file.getName().equals(active)) {
                            RuleStore.setCurrent(tab, "");
                        }
                        refresh();
                        if (onRuleChanged != null) onRuleChanged.run();
                        toast("已删除");
                    } else {
                        toast("删除失败");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void askName(String def, final NameCallback cb) {
        final EditText et = new EditText(ctx);
        et.setText(def);
        et.setHint("规则名（必填）");
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        et.setPadding(dp(16), dp(12), dp(16), dp(12));

        new AlertDialog.Builder(ctx)
                .setTitle("规则名")
                .setView(et)
                .setPositiveButton("确定", (d, w) -> {
                    String n = et.getText().toString().trim();
                    if (n.isEmpty()) {
                        toast("规则名不能为空");
                        return;
                    }
                    cb.onName(n);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private interface NameCallback {
        void onName(String name);
    }

    private TextView miniChip(String s, String color, Runnable r) {
        TextView t = tv(s, 11, color, false);
        t.setPadding(dp(10), dp(5), dp(10), dp(5));
        t.setBackground(bg("#20" + color.substring(1), 6, color));
        t.setOnClickListener(v -> r.run());
        return t;
    }

    private TextView tabBtn(String s) {
        TextView t = tv(s, 13, C_SUB, true);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private void styleTab(TextView t, boolean on) {
        t.setTextColor(Color.parseColor(on ? "#FFFFFF" : C_SUB));
        t.setBackground(on ? bg(C_ACCENT, 10, null) : bg("#1AFFFFFF", 10, "#30FFFFFF"));
    }

    private TextView btn(String s, String fill, Runnable r) {
        TextView t = tv(s, 14, "#FFFFFF", true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(bg(fill, 10, null));
        t.setOnClickListener(v -> r.run());
        return t;
    }

    private LinearLayout.LayoutParams lb(int left, int right) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(44), 1f);
        p.leftMargin = left;
        p.rightMargin = right;
        return p;
    }

    private TextView tv(String s, float sp, String color, boolean bold) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(Color.parseColor(color));
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
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

    private void toast(String s) {
        android.widget.Toast.makeText(ctx, s, android.widget.Toast.LENGTH_SHORT).show();
    }
}
