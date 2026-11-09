package com.hollow.affix;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class RuleEditorActivity extends Activity {

    private static final String C_BG = "#0A0E14";
    private static final String C_CARD = "#151A22";
    private static final String C_CARD2 = "#1C2330";
    private static final String C_ACCENT = "#7C4DFF";
    private static final String C_TEXT = "#E8EAED";
    private static final String C_SUB = "#8B95A5";
    private static final String C_OK = "#4ADE80";
    private static final String C_ERR = "#F87171";

    private static final String[] PAGES = {"基本", "权重", "判定", "组", "等级"};

    private RuleSet rs;

    private File srcFile;
    private int page = 0;

    private LinearLayout pageBar;
    private LinearLayout bodyBox;
    private TextView[] pageTabs = new TextView[PAGES.length];

    private static File pendingFile;
    private static int pendingType;
    private static Runnable pendingDone;

    public static void open(Context ctx, File file, int type) {
        Intent i = new Intent(ctx, RuleEditorActivity.class);
        i.putExtra("type", type);
        i.putExtra("path", file == null ? "" : file.getAbsolutePath());
        ctx.startActivity(i);
    }

    public static void pickImportFile(Context ctx, int type, Runnable done) {
        pendingType = type;
        pendingDone = done;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/plain", "*/*"});
        if (ctx instanceof Activity) {
            ((Activity) ctx).startActivityForResult(i, 9001);
        } else {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        }
    }

    @Override
    protected void onCreate(android.os.Bundle b) {
        super.onCreate(b);

        int type = getIntent().getIntExtra("type", RuleStore.TYPE_DRIVE);
        String path = getIntent().getStringExtra("path");
        srcFile = (path == null || path.isEmpty()) ? null : new File(path);

        if (type == RuleStore.TYPE_CASSETTE) {

            android.widget.Toast.makeText(this, "空幕编辑器开发中", android.widget.Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (srcFile != null && srcFile.exists()) {
            try {
                String json = RuleStore.readJsonFile(srcFile);
                rs = RuleSet.parse(json).copy();
            } catch (Throwable e) {
                rs = RuleSet.defaultSet().copy();
                toast("读取失败，已载入默认规则");
            }
        } else {
            rs = RuleSet.defaultSet().copy();
            rs.name = "";
            rs.author = "";
        }

        buildUI();
    }

    private void buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor(C_BG));
        root.setPadding(dp(16), dp(26), dp(16), dp(12));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = tv(srcFile == null ? "新建规则" : "编辑规则", 20, C_TEXT, true);
        head.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView save = tv("保存", 14, C_OK, true);
        save.setPadding(dp(12), dp(6), dp(4), dp(6));
        save.setOnClickListener(v -> onSave());
        head.addView(save);

        TextView cancel = tv("取消", 14, C_SUB, false);
        cancel.setPadding(dp(12), dp(6), dp(4), dp(6));
        cancel.setOnClickListener(v -> finish());
        head.addView(cancel);

        root.addView(head);

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        pageBar = new LinearLayout(this);
        pageBar.setOrientation(LinearLayout.HORIZONTAL);
        pageBar.setPadding(0, dp(12), 0, dp(10));
        for (int i = 0; i < PAGES.length; i++) {
            final int idx = i;
            TextView t = tv(PAGES[i], 13, C_SUB, true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(14), dp(7), dp(14), dp(7));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(8);
            pageBar.addView(t, lp);
            t.setOnClickListener(v -> {
                page = idx;
                renderPage();
            });
            pageTabs[i] = t;
        }
        hs.addView(pageBar);
        root.addView(hs);

        ScrollView sv = new ScrollView(this);
        bodyBox = new LinearLayout(this);
        bodyBox.setOrientation(LinearLayout.VERTICAL);
        bodyBox.setPadding(0, 0, 0, dp(20));
        sv.addView(bodyBox);
        root.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        renderPage();
    }

    private void renderPage() {
        for (int i = 0; i < pageTabs.length; i++) {
            boolean on = i == page;
            pageTabs[i].setTextColor(Color.parseColor(on ? "#FFFFFF" : C_SUB));
            pageTabs[i].setBackground(on
                    ? bg(C_ACCENT, 10, null)
                    : bg("#1AFFFFFF", 10, "#30FFFFFF"));
        }
        bodyBox.removeAllViews();
        switch (page) {
            case 0: renderBasic(); break;
            case 1: renderWeights(); break;
            case 2: renderRules(); break;
            case 3: renderGroups(); break;
            case 4: renderGrades(); break;
        }
    }

    private void renderBasic() {
        bodyBox.addView(labeledInput("规则名 *", rs.name, s -> rs.name = s));
        bodyBox.addView(labeledInput("作者", rs.author, s -> rs.author = s));
        bodyBox.addView(labeledInput("描述", rs.description, s -> rs.description = s));
        bodyBox.addView(numberInput("版本", String.valueOf(rs.version), v -> {
            if (v >= 1) rs.version = v;
        }));

        bodyBox.addView(sectionTitle("适用目标"));
        bodyBox.addView(pickRow("识别对象", RuleType.label(rs.target), () -> {
            String[] opts = {RuleType.DRIVE, RuleType.CASSETTE, RuleType.BOTH};
            String[] names = {"驱动块", "空幕", "两者都识别"};
            int cur = 0;
            for (int i = 0; i < opts.length; i++) if (opts[i].equals(rs.target)) cur = i;
            new AlertDialog.Builder(this)
                    .setTitle("识别对象")
                    .setSingleChoiceItems(names, cur, (d, which) -> {
                        rs.target = opts[which];
                        d.dismiss();
                        renderPage();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        }));
        bodyBox.addView(hintText("选「驱动块」时，遇到空幕面板会自动跳过不识别"));

        bodyBox.addView(numberInput("双爆加成", String.valueOf(rs.doubleCritBonus), v -> {
            if (v >= 0) rs.doubleCritBonus = v;
        }));

        bodyBox.addView(sectionTitle("兜底等级"));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        row.setBackground(bg(C_CARD, 10, "#1FFFFFFF"));

        TextView label = tv("全都不匹配时", 13, C_TEXT, false);
        row.addView(label, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView val = tv(rs.fallback == null || rs.fallback.isEmpty() ? "未设置" : rs.fallback,
                13, C_ACCENT, true);
        val.setPadding(dp(10), dp(5), dp(10), dp(5));
        val.setBackground(bg("#207C4DFF", 6, C_ACCENT));
        val.setOnClickListener(v -> pickFallback());
        row.addView(val);

        bodyBox.addView(row, wrapLp(6));
    }

    private void pickFallback() {
        List<String> grades = collectGradeNames();
        if (grades.isEmpty()) {
            toast("请先在「判定」页添加规则");
            return;
        }
        showPicker("兜底等级", grades, rs.fallback, s -> {
            rs.fallback = s;
            renderPage();
        });
    }

    private List<String> collectGradeNames() {
        List<String> out = new ArrayList<>();
        for (RuleSet.Rule r : rs.rules) {
            if (r.grade != null && !r.grade.isEmpty() && !out.contains(r.grade)) {
                out.add(r.grade);
            }
        }
        for (String g : rs.colors.keySet()) {
            if (!out.contains(g)) out.add(g);
        }
        return out;
    }

    private void renderWeights() {
        bodyBox.addView(hintText("词条名固定 11 项，填权重分值（≥ 0）。权重高于最高值一半的词条会显示金色。"));

        for (final String affix : RuleSet.affixList()) {
            Integer cur = rs.weights.get(affix);
            bodyBox.addView(numberInput(affix, cur == null ? "0" : String.valueOf(cur), v -> {
                rs.weights.put(affix, Math.max(0, v));
            }));
        }
    }

    private void renderRules() {
        bodyBox.addView(hintText("按顺序从上到下匹配，命中即停。拖动不方便，用 ↑↓ 调顺序。"));

        for (int i = 0; i < rs.rules.size(); i++) {
            final int idx = i;
            RuleSet.Rule rule = rs.rules.get(i);

            LinearLayout card = card();

            LinearLayout h = new LinearLayout(this);
            h.setOrientation(LinearLayout.HORIZONTAL);
            h.setGravity(Gravity.CENTER_VERTICAL);

            TextView no = tv(String.valueOf(i + 1), 12, C_SUB, true);
            no.setGravity(Gravity.CENTER);
            no.setBackground(bg("#1AFFFFFF", 4, null));
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(dp(20), dp(20));
            nlp.rightMargin = dp(8);
            h.addView(no, nlp);

            TextView gv = tv(rule.grade == null || rule.grade.isEmpty() ? "选等级" : rule.grade,
                    13, C_ACCENT, true);
            gv.setPadding(dp(10), dp(4), dp(10), dp(4));
            gv.setBackground(bg("#207C4DFF", 6, C_ACCENT));
            gv.setOnClickListener(v -> pickGrade(idx));
            h.addView(gv, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView up = miniBtn("↑", () -> moveRule(idx, -1));
            h.addView(up);
            TextView down = miniBtn("↓", () -> moveRule(idx, 1));
            h.addView(down);
            TextView del = miniBtn("×", () -> {
                rs.rules.remove(idx);
                renderPage();
            });
            del.setTextColor(Color.parseColor(C_ERR));
            h.addView(del);

            card.addView(h);

            TextView cond = tv(describeRule(rule), 11, C_SUB, false);
            cond.setPadding(0, dp(6), 0, dp(4));
            cond.setLineSpacing(dp(3), 1f);
            card.addView(cond);

            LinearLayout ops = new LinearLayout(this);
            ops.setOrientation(LinearLayout.HORIZONTAL);
            ops.setPadding(0, dp(6), 0, 0);

            TextView editCond = smallBtn("编辑条件", C_ACCENT, () -> editCondition(idx));
            ops.addView(editCond);

            card.addView(ops);

            bodyBox.addView(card, wrapLp(8));
        }

        TextView add = bigBtn("+ 添加规则", C_CARD2);
        add.setOnClickListener(v -> {
            RuleSet.Rule r = new RuleSet.Rule();
            r.grade = collectGradeNames().isEmpty() ? "" : collectGradeNames().get(0);
            rs.rules.add(r);
            page = 2;
            renderPage();
        });
        bodyBox.addView(add, wrapLp(12, 0));
    }

    private String describeRule(RuleSet.Rule rule) {
        StringBuilder sb = new StringBuilder();
        if (!rule.require.isEmpty()) {
            sb.append("必须同时满足：").append(join(rule.require)).append('\n');
        }
        if (!rule.requireAny.isEmpty()) {
            sb.append("至少满足其一：").append(join(rule.requireAny)).append('\n');
        }
        if (rule.minCount > 0) {
            sb.append("词条数不少于 ").append(rule.minCount).append('\n');
        }
        if (sb.length() == 0) return "尚未设置条件";
        return sb.toString().trim();
    }

    private String join(List<String> l) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size(); i++) {
            if (i > 0) sb.append(" + ");
            sb.append(l.get(i));
        }
        return sb.toString();
    }

    private void moveRule(int idx, int delta) {
        int t = idx + delta;
        if (t < 0 || t >= rs.rules.size()) return;
        RuleSet.Rule tmp = rs.rules.get(idx);
        rs.rules.set(idx, rs.rules.get(t));
        rs.rules.set(t, tmp);
        renderPage();
    }

    private void pickGrade(final int ruleIdx) {
        List<String> grades = collectGradeNames();
        if (grades.isEmpty()) {

            askText("等级名", "", s -> {
                if (!s.isEmpty()) {
                    rs.rules.get(ruleIdx).grade = s;
                    if (!rs.colors.containsKey(s)) rs.colors.put(s, "#AAAAAA");
                    renderPage();
                }
            });
            return;
        }
        grades.add("+ 输入新等级");
        showPicker("选择等级", grades, rs.rules.get(ruleIdx).grade, s -> {
            if (s.startsWith("+ ")) {
                askText("等级名", "", n -> {
                    if (!n.isEmpty()) {
                        rs.rules.get(ruleIdx).grade = n;
                        if (!rs.colors.containsKey(n)) rs.colors.put(n, "#AAAAAA");
                        renderPage();
                    }
                });
            } else {
                rs.rules.get(ruleIdx).grade = s;
                renderPage();
            }
        });
    }

    private void editCondition(final int idx) {
        final RuleSet.Rule rule = rs.rules.get(idx);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(10), dp(16), dp(10));
        box.setBackgroundColor(Color.parseColor(C_BG));

        TextView t1 = sectionTitle("必须同时满足");
        box.addView(t1);

        final List<String> pool = new ArrayList<>();
        pool.addAll(rs.groups.keySet());
        pool.addAll(RuleSet.affixList());

        for (String item : pool) {
            final String it = item;
            final TextView c = tv(rule.require.contains(it) ? "☑ " + it : "☐ " + it, 13,
                    rule.require.contains(it) ? C_ACCENT : C_SUB,
                    rule.require.contains(it));
            c.setPadding(dp(4), dp(8), dp(4), dp(8));
            c.setOnClickListener(v -> {
                if (rule.require.contains(it)) {
                    rule.require.remove(it);
                    c.setText("☐ " + it);
                    c.setTextColor(Color.parseColor(C_SUB));
                } else {
                    rule.require.add(it);
                    c.setText("☑ " + it);
                    c.setTextColor(Color.parseColor(C_ACCENT));
                }
            });
            box.addView(c);
        }

        box.addView(buildRequireAnySection(rule, pool));
        box.addView(buildMinCountSection(rule));

        ScrollView sv = new ScrollView(this);
        sv.addView(box);

        new AlertDialog.Builder(this)
                .setTitle("编辑条件")
                .setView(sv)
                .setPositiveButton("完成", (d, w) -> renderPage())
                .show();
    }

    private LinearLayout buildRequireAnySection(final RuleSet.Rule rule, List<String> pool) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(sectionTitle("至少满足其一"));
        for (final String it : pool) {
            final boolean checked = rule.requireAny.contains(it);
            TextView c = tv((checked ? "☑ " : "☐ ") + it, 13,
                    checked ? C_ACCENT : C_SUB, checked);
            c.setPadding(dp(4), dp(8), dp(4), dp(8));
            c.setOnClickListener(v -> {
                if (rule.requireAny.contains(it)) {
                    rule.requireAny.remove(it);
                    c.setText("☐ " + it);
                    c.setTextColor(Color.parseColor(C_SUB));
                } else {
                    rule.requireAny.add(it);
                    c.setText("☑ " + it);
                    c.setTextColor(Color.parseColor(C_ACCENT));
                }
            });
            box.addView(c);
        }
        return box;
    }

    private LinearLayout buildMinCountSection(final RuleSet.Rule rule) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(sectionTitle("词条数下限"));
        box.addView(numberInput("不少于 N 条（0 = 不限）",
                String.valueOf(rule.minCount), v -> rule.minCount = Math.max(0, v)));
        return box;
    }

    private void renderGroups() {
        bodyBox.addView(hintText("组是词条的集合。判定条件里写组名，等于要求组内成员。\n"
                + "特殊行为：「双爆」要求组内全部命中，「单爆」要求恰好 1 个命中，其他组任一命中即可。"));

        List<String> keys = new ArrayList<>(rs.groups.keySet());
        for (int i = 0; i < keys.size(); i++) {
            final String g = keys.get(i);
            final List<String> mem = rs.groups.get(g);

            LinearLayout card = card();

            LinearLayout h = new LinearLayout(this);
            h.setOrientation(LinearLayout.HORIZONTAL);
            h.setGravity(Gravity.CENTER_VERTICAL);

            TextView name = tv(g, 14, C_ACCENT, true);
            h.addView(name, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView edit = miniBtn("改", () -> askText("组名", g, n -> {
                if (n.isEmpty() || n.equals(g)) return;
                List<String> old = rs.groups.remove(g);
                rs.groups.put(n, old);
                renderPage();
            }));
            h.addView(edit);

            TextView del = miniBtn("×", () -> {
                rs.groups.remove(g);
                renderPage();
            });
            del.setTextColor(Color.parseColor(C_ERR));
            h.addView(del);

            card.addView(h);

            TextView ms = tv(mem.isEmpty() ? "（空）" : join(mem), 11, C_SUB, false);
            ms.setPadding(0, dp(5), 0, dp(6));
            card.addView(ms);

            TextView editMem = smallBtn("编辑成员", C_ACCENT, () -> editGroupMembers(g));
            card.addView(editMem);

            bodyBox.addView(card, wrapLp(8));
        }

        TextView add = bigBtn("+ 新建组", C_CARD2);
        add.setOnClickListener(v -> askText("组名", "", n -> {
            if (n.isEmpty()) return;
            if (rs.groups.containsKey(n)) {
                toast("组名已存在");
                return;
            }
            rs.groups.put(n, new ArrayList<>());
            page = 3;
            renderPage();
        }));
        bodyBox.addView(add, wrapLp(12, 0));
    }

    private void editGroupMembers(final String g) {
        final List<String> mem = rs.groups.get(g);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(10), dp(16), dp(10));

        box.addView(sectionTitle("选择成员"));
        for (final String it : RuleSet.affixList()) {
            final boolean checked = mem.contains(it);
            final TextView c = tv((checked ? "☑ " : "☐ ") + it, 13,
                    checked ? C_ACCENT : C_SUB, checked);
            c.setPadding(dp(4), dp(8), dp(4), dp(8));
            c.setOnClickListener(v -> {
                if (mem.contains(it)) {
                    mem.remove(it);
                    c.setText("☐ " + it);
                    c.setTextColor(Color.parseColor(C_SUB));
                } else {
                    mem.add(it);
                    c.setText("☑ " + it);
                    c.setTextColor(Color.parseColor(C_ACCENT));
                }
            });
            box.addView(c);
        }

        ScrollView sv = new ScrollView(this);
        sv.addView(box);

        new AlertDialog.Builder(this)
                .setTitle("组：" + g)
                .setView(sv)
                .setPositiveButton("完成", (d, w) -> renderPage())
                .show();
    }

    private void renderGrades() {
        bodyBox.addView(hintText("等级名和颜色可改。驱动块不按分数分档，"
                + "等级取决于命中的判定规则；这里只改颜色。"));

        List<String> names = new ArrayList<>();
        for (RuleSet.Rule r : rs.rules) {
            if (r.grade != null && !r.grade.isEmpty() && !names.contains(r.grade)) {
                names.add(r.grade);
            }
        }
        for (String g : rs.colors.keySet()) {
            if (!names.contains(g)) names.add(g);
        }

        for (final String g : names) {
            String c = rs.colors.get(g);

            LinearLayout card = card();
            LinearLayout h = new LinearLayout(this);
            h.setOrientation(LinearLayout.HORIZONTAL);
            h.setGravity(Gravity.CENTER_VERTICAL);

            TextView dot = tv("●", 16, c == null ? C_SUB : c, true);
            dot.setPadding(0, 0, dp(8), 0);
            h.addView(dot);

            TextView name = tv(g, 14, C_TEXT, true);
            h.addView(name, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView rename = miniBtn("改名", () -> askText("等级名", g, n -> {
                if (n.isEmpty() || n.equals(g)) return;
                renameGrade(g, n);
            }));
            h.addView(rename);

            TextView color = miniBtn("颜色", () -> askText("颜色（#RRGGBB）",
                    c == null ? "#AAAAAA" : c, n -> {
                        if (n.matches("^#[0-9A-Fa-f]{6}$")) {
                            rs.colors.put(g, n);
                            renderPage();
                        } else {
                            toast("格式必须是 #RRGGBB");
                        }
                    }));
            h.addView(color);

            card.addView(h);
            bodyBox.addView(card, wrapLp(8));
        }

        if (names.isEmpty()) {
            bodyBox.addView(hintText("还没有等级。在「判定」页添加规则时会创建等级。"));
        }
    }

    private void renameGrade(String from, String to) {
        for (RuleSet.Rule r : rs.rules) {
            if (from.equals(r.grade)) r.grade = to;
        }
        if (from.equals(rs.fallback)) rs.fallback = to;
        String c = rs.colors.remove(from);
        rs.colors.put(to, c == null ? "#AAAAAA" : c);
        renderPage();
    }

    private void onSave() {
        RuleValidator.Report rep = RuleValidator.validateDrive(rs, RuleSet.affixList());
        if (rep.hasError()) {
            new AlertDialog.Builder(this)
                    .setTitle("无法保存")
                    .setMessage(rep.summary())
                    .setPositiveButton("去修改", null)
                    .show();
            return;
        }

        try {
            String json = rs.toJson();
            String fn = RuleStore.save(this, RuleStore.TYPE_DRIVE, rs.name, json);
            RuleStore.setCurrent(RuleStore.TYPE_DRIVE, fn);
            toast("已保存：" + fn);
            finish();
        } catch (Throwable e) {
            toast("保存失败: " + e.getMessage());
        }
    }

    private interface TextCb { void onText(String s); }
    private interface NumCb { void onNum(int v); }

    private View labeledInput(String label, String value, final TextCb cb) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(14), dp(10), dp(14), dp(10));
        row.setBackground(bg(C_CARD, 10, "#1FFFFFFF"));

        TextView l = tv(label, 11, C_SUB, false);
        row.addView(l);

        EditText et = new EditText(this);
        et.setText(value == null ? "" : value);
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        et.setTextColor(Color.parseColor(C_TEXT));
        et.setBackground(null);
        et.setPadding(0, dp(4), 0, 0);
        et.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(android.text.Editable s) {
                cb.onText(s.toString());
            }
        });
        row.addView(et);

        LinearLayout.LayoutParams lp = wrapLp(6);
        row.setLayoutParams(lp);
        return row;
    }

    private View numberInput(String label, String value, final NumCb cb) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(10), dp(14), dp(10));
        row.setBackground(bg(C_CARD, 10, "#1FFFFFFF"));

        TextView l = tv(label, 13, C_TEXT, false);
        row.addView(l, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        EditText et = new EditText(this);
        et.setText(value);
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        et.setTextColor(Color.parseColor(C_ACCENT));
        et.setGravity(Gravity.END);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setBackground(null);
        et.setWidth(dp(80));
        et.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(android.text.Editable s) {
                String t = s.toString().trim();
                if (t.isEmpty()) return;
                try {
                    cb.onNum(Integer.parseInt(t));
                } catch (Throwable ignored) {}
            }
        });
        row.addView(et);

        LinearLayout.LayoutParams lp = wrapLp(6);
        row.setLayoutParams(lp);
        return row;
    }

    private void askText(String title, String def, final TextCb cb) {
        final EditText et = new EditText(this);
        et.setText(def);
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        et.setPadding(dp(16), dp(12), dp(16), dp(12));
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(et)
                .setPositiveButton("确定", (d, w) -> cb.onText(et.getText().toString().trim()))
                .setNegativeButton("取消", null)
                .show();
    }

    private void showPicker(String title, List<String> items, String cur, final TextCb cb) {
        String[] arr = items.toArray(new String[0]);
        int sel = items.indexOf(cur);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setSingleChoiceItems(arr, sel, (d, which) -> {
                    cb.onText(arr[which]);
                    d.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private LinearLayout card() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(dp(14), dp(12), dp(14), dp(12));
        v.setBackground(bg(C_CARD, 12, "#1FFFFFFF"));
        return v;
    }

    private TextView sectionTitle(String s) {
        TextView t = tv(s, 11, C_SUB, true);
        t.setPadding(0, dp(12), 0, dp(6));
        return t;
    }

    private TextView hintText(String s) {
        TextView t = tv(s, 11, C_SUB, false);
        t.setLineSpacing(dp(4), 1f);
        t.setPadding(0, dp(4), 0, dp(10));
        return t;
    }

    private LinearLayout pickRow(String label, String value, Runnable onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        row.setBackground(bg(C_CARD, 10, "#1FFFFFFF"));

        TextView l = tv(label, 13, C_TEXT, false);
        row.addView(l, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView v = tv(value, 13, C_ACCENT, true);
        v.setPadding(dp(10), dp(5), dp(10), dp(5));
        v.setBackground(bg("#207C4DFF", 6, C_ACCENT));
        v.setOnClickListener(x -> onClick.run());
        row.addView(v);
        return row;
    }

    private TextView miniBtn(String s, Runnable r) {
        TextView t = tv(s, 12, C_ACCENT, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(9), dp(5), dp(9), dp(5));
        t.setOnClickListener(v -> r.run());
        return t;
    }

    private TextView smallBtn(String s, String color, Runnable r) {
        TextView t = tv(s, 11, color, false);
        t.setPadding(dp(10), dp(5), dp(10), dp(5));
        t.setBackground(bg("#20" + color.substring(1), 6, color));
        t.setOnClickListener(v -> r.run());
        return t;
    }

    private TextView bigBtn(String s, String fill) {
        TextView t = tv(s, 14, C_TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(14), 0, dp(14));
        t.setBackground(bg(fill, 10, "#20FFFFFF"));
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

    private GradientDrawable bg(String fill, int radiusDp, String stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.parseColor(fill));
        g.setCornerRadius(dp(radiusDp));
        if (stroke != null) g.setStroke(dp(1), Color.parseColor(stroke));
        return g;
    }

    private LinearLayout.LayoutParams wrapLp(int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = dp(bottom);
        return p;
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
