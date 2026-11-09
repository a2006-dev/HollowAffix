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

public class CassetteEditorActivity extends Activity {

    private static final String C_BG = "#0A0E14";
    private static final String C_CARD = "#151A22";
    private static final String C_CARD2 = "#1C2330";
    private static final String C_ACCENT = "#7C4DFF";
    private static final String C_TEXT = "#E8EAED";
    private static final String C_SUB = "#8B95A5";
    private static final String C_OK = "#4ADE80";
    private static final String C_ERR = "#F87171";

    private static final String[] PAGES = {"基本", "等级", "权重档", "套装", "主属性", "副属性"};

    private CassetteRule cr;
    private File srcFile;
    private int page = 0;
    private String editingProfile = "";

    private LinearLayout bodyBox;
    private TextView[] pageTabs = new TextView[PAGES.length];

    public static void open(Context ctx, File file, int type) {
        Intent i = new Intent(ctx, CassetteEditorActivity.class);
        i.putExtra("path", file == null ? "" : file.getAbsolutePath());
        ctx.startActivity(i);
    }

    @Override
    protected void onCreate(android.os.Bundle b) {
        super.onCreate(b);

        String path = getIntent().getStringExtra("path");
        srcFile = (path == null || path.isEmpty()) ? null : new File(path);

        if (srcFile != null && srcFile.exists()) {
            try {
                cr = CassetteRule.parse(RuleStore.readJsonFile(srcFile)).copy();
            } catch (Throwable e) {
                cr = CassetteRule.defaultRule().copy();
                toast("读取失败，已载入默认规则");
            }
        } else {
            cr = CassetteRule.defaultRule().copy();
            cr.name = "";
            cr.author = "";
        }

        if (!cr.profiles.isEmpty()) {
            editingProfile = cr.profiles.keySet().iterator().next();
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

        TextView title = tv(srcFile == null ? "新建空幕规则" : "编辑空幕规则", 20, C_TEXT, true);
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
        LinearLayout pageBar = new LinearLayout(this);
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
            case 1: renderGrades(); break;
            case 2: renderProfiles(); break;
            case 3: renderSets(); break;
            case 4: renderMainWeights(); break;
            case 5: renderSubWeights(); break;
        }
    }

    private void renderBasic() {
        bodyBox.addView(labeledInput("规则名 *", cr.name, s -> cr.name = s));
        bodyBox.addView(labeledInput("作者", cr.author, s -> cr.author = s));
        bodyBox.addView(labeledInput("描述", cr.description, s -> cr.description = s));
        bodyBox.addView(numberInput("版本", String.valueOf(cr.version), v -> {
            if (v >= 1) cr.version = v;
        }));

        bodyBox.addView(sectionTitle("适用目标"));
        bodyBox.addView(pickRow("识别对象", RuleType.label(cr.target), () -> {
            String[] opts = {RuleType.CASSETTE, RuleType.DRIVE, RuleType.BOTH};
            String[] names = {"空幕", "驱动块", "两者都识别"};
            int cur = 0;
            for (int i = 0; i < opts.length; i++) if (opts[i].equals(cr.target)) cur = i;
            new AlertDialog.Builder(this)
                    .setTitle("识别对象")
                    .setSingleChoiceItems(names, cur, (d, which) -> {
                        cr.target = opts[which];
                        d.dismiss();
                        renderPage();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        }));
        bodyBox.addView(hintText("选「空幕」时，遇到驱动块面板会自动跳过不识别"));

        bodyBox.addView(numberInput("双爆加成", String.valueOf(cr.doubleCritBonus), v -> {
            if (v >= 0) cr.doubleCritBonus = v;
        }));
        bodyBox.addView(hintText("空幕按分数占比分档，不看等级名匹配。\n"
                + "总分 = 主属性 + 4 条副属性 + 双爆加成，除以权重档满分得到占比。"));
    }

    private void renderGrades() {
        bodyBox.addView(hintText("等级需从高到低排列，占比门槛必须递减。\n"
                + "例如：大毕业 0.84 → 毕业 0.67 → 过渡 0.43 → 垃圾 0"));

        for (int i = 0; i < cr.grades.size(); i++) {
            final int idx = i;
            final CassetteRule.Grade g = cr.grades.get(i);

            LinearLayout card = card();

            LinearLayout h = new LinearLayout(this);
            h.setOrientation(LinearLayout.HORIZONTAL);
            h.setGravity(Gravity.CENTER_VERTICAL);

            TextView dot = tv("●", 16, g.color == null ? C_SUB : g.color, true);
            dot.setPadding(0, 0, dp(8), 0);
            h.addView(dot);

            TextView name = tv(g.name, 14, C_TEXT, true);
            h.addView(name, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            h.addView(miniBtn("↑", () -> moveGrade(idx, -1)));
            h.addView(miniBtn("↓", () -> moveGrade(idx, 1)));
            TextView del = miniBtn("×", () -> {
                cr.grades.remove(idx);
                renderPage();
            });
            del.setTextColor(Color.parseColor(C_ERR));
            h.addView(del);

            card.addView(h);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(8), 0, 0);

            TextView l = tv("占比门槛 ≥", 12, C_SUB, false);
            row.addView(l, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            EditText et = new EditText(this);
            et.setText(String.valueOf(g.ratio));
            et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            et.setTextColor(Color.parseColor(C_ACCENT));
            et.setGravity(Gravity.END);
            et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            et.setBackground(null);
            et.setWidth(dp(80));
            et.addTextChangedListener(new SimpleWatcher(s -> {
                try {
                    g.ratio = Double.parseDouble(s);
                } catch (Throwable ignored) {}
            }));
            row.addView(et);

            card.addView(row);

            LinearLayout ops = new LinearLayout(this);
            ops.setOrientation(LinearLayout.HORIZONTAL);
            ops.setPadding(0, dp(8), 0, 0);

            ops.addView(smallBtn("改名", C_ACCENT, () ->
                    askText("等级名", g.name, n -> {
                        if (!n.isEmpty()) renameGrade(g.name, n);
                    })));

            TextView color = smallBtn("颜色", "#FFB300", () ->
                    askText("颜色（#RRGGBB）", g.color == null ? "#AAAAAA" : g.color, n -> {
                        if (n.matches("^#[0-9A-Fa-f]{6}$")) {
                            g.color = n;
                            cr.colors.put(g.name, n);
                            renderPage();
                        } else {
                            toast("格式必须是 #RRGGBB");
                        }
                    }));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.leftMargin = dp(8);
            ops.addView(color, clp);

            card.addView(ops);
            bodyBox.addView(card, wrapLp(8));
        }

        TextView add = bigBtn("+ 添加等级", C_CARD2);
        add.setOnClickListener(v -> askText("等级名", "", n -> {
            if (n.isEmpty()) return;
            CassetteRule.Grade g = new CassetteRule.Grade();
            g.name = n;
            g.ratio = 0;
            g.color = "#AAAAAA";
            cr.grades.add(g);
            cr.colors.put(n, g.color);
            renderPage();
        }));
        bodyBox.addView(add, wrapLp(12, 0));
    }

    private void moveGrade(int idx, int delta) {
        int t = idx + delta;
        if (t < 0 || t >= cr.grades.size()) return;
        CassetteRule.Grade tmp = cr.grades.get(idx);
        cr.grades.set(idx, cr.grades.get(t));
        cr.grades.set(t, tmp);
        renderPage();
    }

    private void renameGrade(String from, String to) {
        for (CassetteRule.Grade g : cr.grades) {
            if (from.equals(g.name)) g.name = to;
        }
        String c = cr.colors.remove(from);
        cr.colors.put(to, c == null ? "#AAAAAA" : c);
        renderPage();
    }

    private void renderProfiles() {
        bodyBox.addView(hintText("权重档决定某类套装怎么打分。\n"
                + "每档有自己的主属性权重、副属性权重和满分。\n"
                + "点「编辑权重」去主属性/副属性页调整。"));

        List<String> keys = new ArrayList<>(cr.profiles.keySet());
        for (int i = 0; i < keys.size(); i++) {
            final String key = keys.get(i);
            final CassetteRule.Profile p = cr.profiles.get(key);

            LinearLayout card = card();
            if (key.equals(editingProfile)) {
                card.setBackground(bg("#1E2A3A", 12, C_ACCENT));
            }

            LinearLayout h = new LinearLayout(this);
            h.setOrientation(LinearLayout.HORIZONTAL);
            h.setGravity(Gravity.CENTER_VERTICAL);

            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);

            TextView label = tv(p.label == null || p.label.isEmpty() ? key : p.label,
                    14, C_TEXT, true);
            col.addView(label);

            TextView sub = tv("key: " + key + "   满分 " + p.maxTotal, 10, C_SUB, false);
            sub.setPadding(0, dp(3), 0, 0);
            col.addView(sub);

            h.addView(col, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView del = miniBtn("×", () -> {
                if (cr.profiles.size() <= 1) {
                    toast("至少保留一个权重档");
                    return;
                }
                for (CassetteRule.SetDetail d : cr.setDetails.values()) {
                    if (key.equals(d.profile)) {
                        toast("套装「" + d.name + "」正在使用此权重档");
                        return;
                    }
                }
                cr.profiles.remove(key);
                if (key.equals(editingProfile)) {
                    editingProfile = cr.profiles.isEmpty() ? ""
                            : cr.profiles.keySet().iterator().next();
                }
                renderPage();
            });
            del.setTextColor(Color.parseColor(C_ERR));
            h.addView(del);

            card.addView(h);

            LinearLayout ops = new LinearLayout(this);
            ops.setOrientation(LinearLayout.HORIZONTAL);
            ops.setPadding(0, dp(8), 0, 0);

            ops.addView(smallBtn("编辑权重", C_ACCENT, () -> {
                editingProfile = key;
                page = 4;
                renderPage();
            }));

            TextView rename = smallBtn("改名", "#FFB300", () ->
                    askText("显示名", p.label, n -> {
                        if (!n.isEmpty()) {
                            p.label = n;
                            renderPage();
                        }
                    }));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.leftMargin = dp(8);
            ops.addView(rename, rlp);

            card.addView(ops);
            card.addView(numberInput("满分", String.valueOf(p.maxTotal), v -> {
                if (v > 0) p.maxTotal = v;
            }));

            bodyBox.addView(card, wrapLp(8));
        }

        TextView add = bigBtn("+ 新建权重档", C_CARD2);
        add.setOnClickListener(v -> askText("权重档 key（英文）", "", key -> {
            if (key.isEmpty()) return;
            if (cr.profiles.containsKey(key)) {
                toast("key 已存在");
                return;
            }
            askText("显示名", key, label -> {
                CassetteRule.Profile p = new CassetteRule.Profile();
                p.name = key;
                p.label = label.isEmpty() ? key : label;
                p.maxTotal = 100;
                for (String a : CassetteRule.mainAffixList()) p.mainWeights.put(a, 0);
                for (String a : CassetteRule.subAffixList()) p.subWeights.put(a, 0);
                cr.profiles.put(key, p);
                editingProfile = key;
                renderPage();
            });
        }));
        bodyBox.addView(add, wrapLp(12, 0));
    }

    private void renderSets() {
        bodyBox.addView(hintText("每个套装绑定一个权重档，并指定本命主属性。\n"
                + "主属性命中本命属性时得最高分，否则按该档权重表计分。"));

        List<String> keys = new ArrayList<>(cr.setDetails.keySet());
        for (int i = 0; i < keys.size(); i++) {
            final String key = keys.get(i);
            final CassetteRule.SetDetail d = cr.setDetails.get(key);

            LinearLayout card = card();

            LinearLayout h = new LinearLayout(this);
            h.setOrientation(LinearLayout.HORIZONTAL);
            h.setGravity(Gravity.CENTER_VERTICAL);

            TextView name = tv(d.name, 14, C_TEXT, true);
            h.addView(name, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView del = miniBtn("×", () -> {
                cr.setDetails.remove(key);
                cr.sets.remove(d.name);
                renderPage();
            });
            del.setTextColor(Color.parseColor(C_ERR));
            h.addView(del);

            card.addView(h);

            LinearLayout row1 = new LinearLayout(this);
            row1.setOrientation(LinearLayout.HORIZONTAL);
            row1.setPadding(0, dp(8), 0, 0);

            row1.addView(chip("档：" + profileLabel(d.profile), C_ACCENT, () -> pickProfile(d)));

            boolean hasElem = d.matchElement != null && !d.matchElement.isEmpty();
            TextView elemTv = chip(hasElem ? "本命：" + shortName(d.matchElement) : "本命：未设",
                    hasElem ? "#FFB300" : C_SUB, () -> pickMatchElement(d));
            LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            elp.leftMargin = dp(8);
            row1.addView(elemTv, elp);

            card.addView(row1);

            LinearLayout ops = new LinearLayout(this);
            ops.setOrientation(LinearLayout.HORIZONTAL);
            ops.setPadding(0, dp(8), 0, 0);
            ops.addView(smallBtn("编辑详情", C_ACCENT, () -> editSetDetail(d)));
            card.addView(ops);

            bodyBox.addView(card, wrapLp(8));
        }

        TextView add = bigBtn("+ 新建套装", C_CARD2);
        add.setOnClickListener(v -> askText("套装名", "", n -> {
            if (n.isEmpty()) return;
            if (cr.setDetails.containsKey(n)) {
                toast("套装名已存在");
                return;
            }
            CassetteRule.SetDetail d = new CassetteRule.SetDetail();
            d.name = n;
            d.aliases.add(n);
            d.profile = cr.profiles.isEmpty() ? "" : cr.profiles.keySet().iterator().next();
            d.matchElement = "";
            d.element = "";
            d.type = "";
            d.two = "";
            d.four = "";
            d.note = "";
            cr.setDetails.put(n, d);
            cr.sets.add(n);
            renderPage();
        }));
        bodyBox.addView(add, wrapLp(12, 0));
    }

    private String profileLabel(String key) {
        if (key == null) return "未设";
        CassetteRule.Profile p = cr.profiles.get(key);
        if (p == null) return key + "（不存在）";
        return p.label == null || p.label.isEmpty() ? key : p.label;
    }

    private String shortName(String s) {
        if (s == null) return "";
        return s.replace("属性异能伤害增强", "").replace("伤害增强", "");
    }

    private void pickProfile(final CassetteRule.SetDetail d) {
        List<String> keys = new ArrayList<>(cr.profiles.keySet());
        List<String> labels = new ArrayList<>();
        for (String k : keys) labels.add(profileLabel(k));
        showPicker("选择权重档", labels, profileLabel(d.profile), idx -> {
            d.profile = keys.get(idx);
            renderPage();
        });
    }

    private void pickMatchElement(final CassetteRule.SetDetail d) {
        List<String> list = new ArrayList<>();
        list.add("（通用，不设本命）");
        list.addAll(CassetteRule.mainAffixList());
        int cur = 0;
        if (d.matchElement != null && !d.matchElement.isEmpty()) {
            int idx = list.indexOf(d.matchElement);
            if (idx >= 0) cur = idx;
        }
        showPicker("本命主属性", list, list.get(cur), idx -> {
            d.matchElement = idx == 0 ? "" : list.get(idx);
            renderPage();
        });
    }

    private void editSetDetail(final CassetteRule.SetDetail d) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(12), dp(16), dp(12));

        box.addView(labeledInput("套装名 *", d.name, s -> {
            String old = d.name;
            if (s.equals(old)) return;
            d.name = s;
            cr.setDetails.remove(old);
            cr.setDetails.put(s, d);
            cr.sets.remove(old);
            if (!cr.sets.contains(s)) cr.sets.add(s);
        }));
        box.addView(labeledInput("别名（逗号分隔）", joinAliases(d.aliases), s -> {
            d.aliases.clear();
            for (String a : s.split("[,，]")) {
                String t = a.trim();
                if (!t.isEmpty()) d.aliases.add(t);
            }
        }));
        box.addView(labeledInput("属性（如 暗 / 光 / 通用）", d.element, s -> d.element = s));
        box.addView(labeledInput("类型（如 输出 / 辅助 / 防御）", d.type, s -> d.type = s));
        box.addView(labeledInput("2件套效果", d.two, s -> d.two = s));
        box.addView(labeledInput("4件套效果", d.four, s -> d.four = s));
        box.addView(labeledInput("备注", d.note, s -> d.note = s));

        ScrollView sv = new ScrollView(this);
        sv.addView(box);

        new AlertDialog.Builder(this)
                .setTitle("套装：" + d.name)
                .setView(sv)
                .setPositiveButton("完成", (dlg, w) -> renderPage())
                .show();
    }

    private String joinAliases(List<String> a) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(a.get(i));
        }
        return sb.toString();
    }

    private void renderMainWeights() {
        CassetteRule.Profile p = cr.profiles.get(editingProfile);
        if (p == null) {
            bodyBox.addView(hintText("请先在「权重档」页选择一个权重档"));
            return;
        }

        bodyBox.addView(hintText("当前权重档：" + profileLabel(editingProfile)
                + "\n主属性共 15 项，填大于等于 0 的整数。"));

        bodyBox.addView(smallBtn("全部清零", C_ERR, () -> {
            for (String a : CassetteRule.mainAffixList()) p.mainWeights.put(a, 0);
            renderPage();
        }));

        bodyBox.addView(hintText(""));

        for (final String a : CassetteRule.mainAffixList()) {
            Integer cur = p.mainWeights.get(a);
            bodyBox.addView(numberInput(a, cur == null ? "0" : String.valueOf(cur), v -> {
                p.mainWeights.put(a, Math.max(0, v));
            }));
        }
    }

    private void renderSubWeights() {
        CassetteRule.Profile p = cr.profiles.get(editingProfile);
        if (p == null) {
            bodyBox.addView(hintText("请先在「权重档」页选择一个权重档"));
            return;
        }

        bodyBox.addView(hintText("当前权重档：" + profileLabel(editingProfile)
                + "\n副属性共 11 项。权重高于最高值一半的会显示金色。"));

        bodyBox.addView(smallBtn("全部清零", C_ERR, () -> {
            for (String a : CassetteRule.subAffixList()) p.subWeights.put(a, 0);
            renderPage();
        }));

        bodyBox.addView(hintText(""));

        for (final String a : CassetteRule.subAffixList()) {
            Integer cur = p.subWeights.get(a);
            bodyBox.addView(numberInput(a, cur == null ? "0" : String.valueOf(cur), v -> {
                p.subWeights.put(a, Math.max(0, v));
            }));
        }
    }

    private void onSave() {
        RuleValidator.Report rep = RuleValidator.validateCassette(cr,
                CassetteRule.mainAffixList(), CassetteRule.subAffixList());
        if (rep.hasError()) {
            new AlertDialog.Builder(this)
                    .setTitle("无法保存")
                    .setMessage(rep.summary())
                    .setPositiveButton("去修改", null)
                    .show();
            return;
        }

        try {
            String json = cr.toJson();
            String fn = RuleStore.save(this, RuleStore.TYPE_CASSETTE, cr.name, json);
            RuleStore.setCurrent(RuleStore.TYPE_CASSETTE, fn);
            toast("已保存：" + fn);
            finish();
        } catch (Throwable e) {
            toast("保存失败: " + e.getMessage());
        }
    }

    private interface TextCb { void onText(String s); }
    private interface NumCb { void onNum(int v); }
    private interface IdxCb { void onIdx(int i); }

    private static class SimpleWatcher implements android.text.TextWatcher {
        private final TextCb cb;
        SimpleWatcher(TextCb cb) { this.cb = cb; }
        public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
        public void onTextChanged(CharSequence s, int a, int b, int c) {}
        public void afterTextChanged(android.text.Editable s) { cb.onText(s.toString()); }
    }

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
        et.addTextChangedListener(new SimpleWatcher(cb));
        row.addView(et);

        row.setLayoutParams(wrapLp(6));
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
        et.addTextChangedListener(new SimpleWatcher(s -> {
            String t = s.trim();
            if (t.isEmpty()) return;
            try {
                cb.onNum(Integer.parseInt(t));
            } catch (Throwable ignored) {}
        }));
        row.addView(et);

        row.setLayoutParams(wrapLp(6));
        return row;
    }

    private TextView chip(String s, String color, Runnable r) {
        TextView t = tv(s, 11, color, false);
        t.setPadding(dp(10), dp(5), dp(10), dp(5));
        t.setBackground(bg("#20" + color.substring(1), 6, color));
        t.setOnClickListener(v -> r.run());
        return t;
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

    private void showPicker(String title, List<String> items, String cur, final IdxCb cb) {
        String[] arr = items.toArray(new String[0]);
        int sel = items.indexOf(cur);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setSingleChoiceItems(arr, sel, (d, which) -> {
                    cb.onIdx(which);
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

    private TextView hintText(String s) {
        TextView t = tv(s, 11, C_SUB, false);
        t.setLineSpacing(dp(4), 1f);
        t.setPadding(0, dp(4), 0, dp(10));
        return t;
    }

    private TextView sectionTitle(String s) {
        TextView t = tv(s, 11, C_SUB, true);
        t.setPadding(0, dp(12), 0, dp(6));
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
