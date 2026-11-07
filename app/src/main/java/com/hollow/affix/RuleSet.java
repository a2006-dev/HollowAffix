package com.hollow.affix;

import android.content.Context;
import android.content.res.AssetManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 规则模板：从 assets/rules/*.json 或外部目录加载。
 * 支持自定义规则名、权重、判定条件。
 */
public class RuleSet {

    public String name = "通用规则";
    public String author = "";
    public int version = 1;
    public String description = "";

    /** 词条名 → 权重 */
    public Map<String, Integer> weights = new LinkedHashMap<>();
    /** 双爆加成 */
    public int doubleCritBonus = 3;

    /** 判定规则，按顺序匹配 */
    public List<Rule> rules = new ArrayList<>();
    /** 全部不匹配时的兜底等级 */
    public String fallback = "垃圾";

    /** 组定义：组名 → 成员词条列表 */
    public Map<String, List<String>> groups = new HashMap<>();
    /** 等级 → 颜色 */
    public Map<String, String> colors = new HashMap<>();

    public static class Rule {
        public String grade;
        public String desc;
        /** 必须同时满足的项（可以是词条名或组名） */
        public List<String> require = new ArrayList<>();
        /** 至少满足其中之一 */
        public List<String> requireAny = new ArrayList<>();
        /** 词条总数下限 */
        public int minCount = 0;
    }

    // ==================== 加载 ====================

    /** 从 assets 加载内置规则 */
    public static RuleSet loadBuiltin(Context ctx) {
        try {
            AssetManager am = ctx.getAssets();
            InputStream is = am.open("rules/general.json");
            String json = readAll(is);
            return parse(json);
        } catch (Throwable e) {
            Logger.appendTo("trace.txt", "内置规则加载失败: " + e);
            return defaultSet();
        }
    }

    private static String readAll(InputStream is) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line).append('\n');
        br.close();
        return sb.toString();
    }

    // ==================== 解析 ====================

    public static RuleSet parse(String json) {
        RuleSet rs = new RuleSet();
        try {
            JSONObject o = new JSONObject(json);
            rs.name = o.optString("name", "未命名规则");
            rs.author = o.optString("author", "");
            rs.version = o.optInt("version", 1);
            rs.description = o.optString("description", "");
            rs.doubleCritBonus = o.optInt("doubleCritBonus", 3);
            rs.fallback = o.optString("fallback", "垃圾");

            // weights
            JSONObject w = o.optJSONObject("weights");
            if (w != null) {
                java.util.Iterator<String> it = w.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    rs.weights.put(k, w.optInt(k, 0));
                }
            }

            // groups
            JSONObject g = o.optJSONObject("groups");
            if (g != null) {
                java.util.Iterator<String> it = g.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    JSONArray arr = g.optJSONArray(k);
                    List<String> mem = new ArrayList<>();
                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) mem.add(arr.getString(i));
                    }
                    rs.groups.put(k, mem);
                }
            }

            // colors
            JSONObject c = o.optJSONObject("colors");
            if (c != null) {
                java.util.Iterator<String> it = c.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    rs.colors.put(k, c.optString(k, "#AAAAAA"));
                }
            }

            // rules
            JSONArray arr = o.optJSONArray("rules");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject ro = arr.getJSONObject(i);
                    Rule r = new Rule();
                    r.grade = ro.optString("grade", "");
                    r.desc = ro.optString("desc", "");
                    r.minCount = ro.optInt("minCount", 0);
                    JSONArray ra = ro.optJSONArray("require");
                    if (ra != null) for (int j = 0; j < ra.length(); j++) r.require.add(ra.getString(j));
                    JSONArray rb = ro.optJSONArray("requireAny");
                    if (rb != null) for (int j = 0; j < rb.length(); j++) r.requireAny.add(rb.getString(j));
                    rs.rules.add(r);
                }
            }
        } catch (Throwable e) {
            Logger.appendTo("trace.txt", "规则解析失败: " + e);
            return defaultSet();
        }
        return rs;
    }

    /** 兜底：解析失败时用硬编码规则 */
    public static RuleSet defaultSet() {
        RuleSet rs = new RuleSet();
        rs.name = "通用规则";
        rs.weights.put("暴击率", 20);
        rs.weights.put("暴击伤害", 20);
        rs.weights.put("攻击力百分比", 14);
        rs.weights.put("通用伤害增强", 14);
        rs.weights.put("攻击力", 8);
        rs.weights.put("生命值百分比", 4);
        rs.weights.put("防御力百分比", 3);
        rs.weights.put("生命值", 2);
        rs.weights.put("防御力", 1);
        rs.weights.put("环合强度", 0);
        rs.weights.put("倾陷强度", 0);
        rs.colors.put("大毕业", "#FF4D4D");
        rs.colors.put("毕业", "#FFB300");
        rs.colors.put("过渡", "#64B5F6");
        rs.colors.put("垃圾", "#787878");

        List<String> crit = new ArrayList<>();
        crit.add("暴击率"); crit.add("暴击伤害");
        rs.groups.put("双爆", crit);
        rs.groups.put("单爆", crit);
        List<String> big = new ArrayList<>();
        big.add("攻击力百分比"); big.add("通用伤害增强");
        rs.groups.put("大攻击", big);

        Rule r1 = new Rule();
        r1.grade = "大毕业"; r1.desc = "双爆+攻%+增伤"; r1.minCount = 4;
        r1.require.add("双爆"); r1.require.add("攻击力百分比"); r1.require.add("通用伤害增强");
        rs.rules.add(r1);

        Rule r2 = new Rule();
        r2.grade = "毕业"; r2.desc = "双爆+大攻击+小攻"; r2.minCount = 4;
        r2.require.add("双爆"); r2.require.add("大攻击"); r2.require.add("攻击力");
        rs.rules.add(r2);

        Rule r3 = new Rule();
        r3.grade = "毕业"; r3.desc = "双爆+特殊+大攻击";
        r3.require.add("双爆"); r3.require.add("大攻击");
        r3.requireAny.add("环合强度"); r3.requireAny.add("倾陷强度");
        rs.rules.add(r3);

        Rule r4 = new Rule();
        r4.grade = "毕业"; r4.desc = "双爆+大攻击";
        r4.require.add("双爆"); r4.require.add("大攻击");
        rs.rules.add(r4);

        Rule r5 = new Rule();
        r5.grade = "过渡"; r5.desc = "双爆无大攻击";
        r5.require.add("双爆");
        rs.rules.add(r5);

        Rule r6 = new Rule();
        r6.grade = "过渡"; r6.desc = "单爆+大攻击";
        r6.require.add("单爆"); r6.require.add("大攻击");
        rs.rules.add(r6);

        return rs;
    }

    /** 取等级颜色 */
    public String colorOf(String grade) {
        String c = colors.get(grade);
        return c != null ? c : "#AAAAAA";
    }
}