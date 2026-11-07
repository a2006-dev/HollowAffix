package com.hollow.affix;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 空幕规则：卡带 / 驱动块的 主属性 + 副属性 + 套装 判定。
 *
 * 与驱动块（RuleSet）的区别：
 *  - 驱动块只看 4 条副词条，按 require/requireAny 做逻辑匹配
 *  - 卡带是「主属性 + 4 副词条」打分制，主属性权重远高于副词条
 *  - 还要识别套装名
 */
public class CassetteRule {

    public String name = "空幕规则";
    public String author = "";
    public int version = 1;
    public String description = "";

    /** 允许的主属性词条 */
    public List<String> mainAffixes = new ArrayList<>();
    /** 允许的副属性词条 */
    public List<String> subAffixes = new ArrayList<>();

    /** 权重配置档，key = profile 名（elemDPS / genDPS / support / tank / hp / healer） */
    public Map<String, Profile> profiles = new LinkedHashMap<>();

    /** 权重档 */
    public static class Profile {
        public String name = "";
        public String label = "";
        /** 该类套装的主属性满分 + 副属性满配之和 */
        public int maxTotal = 100;
        public Map<String, Integer> mainWeights = new LinkedHashMap<>();
        public Map<String, Integer> subWeights = new LinkedHashMap<>();
    }

    /** 双爆加成 */
    public int doubleCritBonus = 3;

    /** 套装名列表 */
    public List<String> sets = new ArrayList<>();

    /** 套装详细信息，key = 套装名 */
    public Map<String, SetDetail> setDetails = new LinkedHashMap<>();

    /** 套装详情 */
    public static class SetDetail {
        public String name = "";
        public List<String> aliases = new ArrayList<>();
        public String element = "";
        public String type = "";
        /** 对应的权重档名（elemDPS / genDPS / support / tank / hp / healer） */
        public String profile = "elemDPS";
        /** 该套的本命主属性，空表示通用套 */
        public String matchElement = "";
        public String two = "";
        public String four = "";
        public String note = "";
    }

    /** 等级定义，按顺序从高到低 */
    public List<Grade> grades = new ArrayList<>();
    public Map<String, String> colors = new LinkedHashMap<>();

    public static class Grade {
        public String name = "";
        /** 占总满分的比例门槛 */
        public double ratio = 0;
        public String color = "#AAAAAA";
    }

    /** 判定结果 */
    public static class Result {
        public String mainAffix = null;
        public int mainScore = 0;
        public List<String> subAffixes = new ArrayList<>();
        public int subScore = 0;
        public String set = null;
        public int total = 0;
        /** 所属权重档标签，如「属性输出」 */
        public String profileName = "";
        /** 该档满分 */
        public int maxTotal = 100;
        /** 总分占比 */
        public double ratio = 0;
        /** 主属性是否命中套装本命属性 */
        public boolean elementMatch = false;
        public String grade = "垃圾";
        public String color = "#787878";
        public String detail = "";
    }

    // ==================== 加载 ====================

    public static CassetteRule loadBuiltin(android.content.Context ctx) {
        try {
            InputStream is = ctx.getAssets().open("rules/cassette.json");
            BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            br.close();
            return parse(sb.toString());
        } catch (Throwable e) {
            Logger.appendTo("trace.txt", "空幕规则加载失败: " + e);
            return defaultRule();
        }
    }

    public static CassetteRule parse(String json) {
        CassetteRule cr = new CassetteRule();
        try {
            JSONObject o = new JSONObject(json);
            cr.name = o.optString("name", "空幕规则");
            cr.author = o.optString("author", "");
            cr.version = o.optInt("version", 1);
            cr.description = o.optString("description", "");
            cr.doubleCritBonus = o.optInt("doubleCritBonus", 3);

            readArray(o.optJSONArray("mainAffixes"), cr.mainAffixes);
            readArray(o.optJSONArray("subAffixes"), cr.subAffixes);
            readArray(o.optJSONArray("sets"), cr.sets);

            // 套装详情
            JSONArray sd = o.optJSONArray("setDetails");
            if (sd != null) {
                for (int i = 0; i < sd.length(); i++) {
                    JSONObject so = sd.getJSONObject(i);
                    SetDetail d = new SetDetail();
                    d.name = so.optString("name", "");
                    if (d.name.isEmpty()) continue;
                    readArray(so.optJSONArray("aliases"), d.aliases);
                    d.element = so.optString("element", "");
                    d.type = so.optString("type", "");
                    d.profile = so.optString("profile", "elemDPS");
                    d.matchElement = so.optString("matchElement", "");
                    d.two = so.optString("two", "");
                    d.four = so.optString("four", "");
                    d.note = so.optString("note", "");
                    cr.setDetails.put(d.name, d);
                    // 缺省补进 sets 列表
                    if (!cr.sets.contains(d.name)) cr.sets.add(d.name);
                }
            }
            // 权重配置档
            JSONObject po = o.optJSONObject("profiles");
            if (po != null) {
                java.util.Iterator<String> it = po.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    JSONObject pj = po.optJSONObject(k);
                    if (pj == null) continue;
                    Profile p = new Profile();
                    p.name = k;
                    p.label = pj.optString("label", k);
                    p.maxTotal = pj.optInt("maxTotal", 100);
                    readMap(pj.optJSONObject("mainWeights"), p.mainWeights);
                    readMap(pj.optJSONObject("subWeights"), p.subWeights);
                    cr.profiles.put(k, p);
                }
            }

            JSONObject c = o.optJSONObject("colors");
            if (c != null) {
                java.util.Iterator<String> it = c.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    cr.colors.put(k, c.optString(k, "#AAAAAA"));
                }
            }

            JSONArray ga = o.optJSONArray("grades");
            if (ga != null) {
                for (int i = 0; i < ga.length(); i++) {
                    JSONObject go = ga.getJSONObject(i);
                    Grade g = new Grade();
                    g.name = go.optString("name", "");
                    g.ratio = go.optDouble("ratio", 0);
                    g.color = go.optString("color", cr.colors.containsKey(g.name)
                            ? cr.colors.get(g.name) : "#AAAAAA");
                    if (!g.name.isEmpty()) cr.grades.add(g);
                }
            }
            if (cr.grades.isEmpty() || cr.profiles.isEmpty()) return defaultRule();
        } catch (Throwable e) {
            Logger.appendTo("trace.txt", "空幕规则解析失败: " + e);
            return defaultRule();
        }
        return cr;
    }

    private static void readArray(JSONArray arr, List<String> target) {
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            String s = arr.optString(i, "");
            if (!s.isEmpty()) target.add(s);
        }
    }

    private static void readMap(JSONObject o, Map<String, Integer> target) {
        if (o == null) return;
        java.util.Iterator<String> it = o.keys();
        while (it.hasNext()) {
            String k = it.next();
            target.put(k, o.optInt(k, 0));
        }
    }

    /** 内置兜底规则（assets 读不到时使用） */
    public static CassetteRule defaultRule() {
        CassetteRule cr = new CassetteRule();
        for (String s : new String[]{"生命值百分比", "攻击力百分比", "防御力百分比",
                "暴击率", "暴击伤害", "环合强度", "倾陷强度", "治疗加成",
                "光属性异能伤害增强", "灵属性异能伤害增强", "咒属性异能伤害增强",
                "暗属性异能伤害增强", "魂属性异能伤害增强", "相属性异能伤害增强",
                "心灵伤害增强"}) {
            cr.mainAffixes.add(s);
        }
        for (String s : new String[]{"生命值百分比", "攻击力百分比", "防御力百分比",
                "生命值", "攻击力", "防御力", "暴击率", "暴击伤害",
                "环合强度", "倾陷强度", "通用伤害增强"}) {
            cr.subAffixes.add(s);
        }

        // ---- elemDPS：属性输出 ----
        Profile elem = new Profile();
        elem.name = "elemDPS";
        elem.label = "属性输出";
        elem.maxTotal = 105;
        elem.mainWeights.put("暴击率", 28);
        elem.mainWeights.put("暴击伤害", 28);
        elem.mainWeights.put("攻击力百分比", 22);
        for (String e : new String[]{"光", "灵", "咒", "暗", "魂", "相"}) {
            elem.mainWeights.put(e + "属性异能伤害增强", 10);
        }
        elem.mainWeights.put("心灵伤害增强", 10);
        elem.subWeights.put("暴击率", 20);
        elem.subWeights.put("暴击伤害", 20);
        elem.subWeights.put("攻击力百分比", 15);
        elem.subWeights.put("通用伤害增强", 15);
        elem.subWeights.put("攻击力", 7);
        elem.subWeights.put("倾陷强度", 3);
        cr.profiles.put(elem.name, elem);

        // ---- genDPS：通用输出 ----
        Profile gen = new Profile();
        gen.name = "genDPS";
        gen.label = "通用输出";
        gen.maxTotal = 103;
        gen.mainWeights.put("攻击力百分比", 34);
        gen.mainWeights.put("暴击率", 28);
        gen.mainWeights.put("暴击伤害", 28);
        for (String e : new String[]{"光", "灵", "咒", "暗", "魂", "相"}) {
            gen.mainWeights.put(e + "属性异能伤害增强", 12);
        }
        gen.mainWeights.put("心灵伤害增强", 10);
        gen.subWeights.put("攻击力百分比", 18);
        gen.subWeights.put("攻击力", 12);
        gen.subWeights.put("暴击率", 18);
        gen.subWeights.put("暴击伤害", 18);
        gen.subWeights.put("通用伤害增强", 10);
        gen.subWeights.put("倾陷强度", 3);
        cr.profiles.put(gen.name, gen);

        // ---- support：辅助 ----
        Profile sup = new Profile();
        sup.name = "support";
        sup.label = "辅助";
        sup.maxTotal = 94;
        sup.mainWeights.put("环合强度", 30);
        sup.mainWeights.put("倾陷强度", 30);
        sup.mainWeights.put("生命值百分比", 24);
        sup.mainWeights.put("治疗加成", 16);
        sup.mainWeights.put("攻击力百分比", 12);
        sup.mainWeights.put("防御力百分比", 10);
        sup.mainWeights.put("暴击率", 6);
        sup.mainWeights.put("暴击伤害", 6);
        sup.subWeights.put("环合强度", 18);
        sup.subWeights.put("倾陷强度", 18);
        sup.subWeights.put("生命值百分比", 14);
        sup.subWeights.put("攻击力百分比", 8);
        sup.subWeights.put("通用伤害增强", 6);
        sup.subWeights.put("攻击力", 6);
        sup.subWeights.put("防御力百分比", 5);
        sup.subWeights.put("生命值", 4);
        sup.subWeights.put("暴击率", 3);
        sup.subWeights.put("暴击伤害", 3);
        sup.subWeights.put("防御力", 2);
        cr.profiles.put(sup.name, sup);

        // ---- tank：防御 ----
        Profile tank = new Profile();
        tank.name = "tank";
        tank.label = "防御";
        tank.maxTotal = 100;
        tank.mainWeights.put("防御力百分比", 34);
        tank.mainWeights.put("生命值百分比", 26);
        tank.mainWeights.put("治疗加成", 8);
        tank.mainWeights.put("环合强度", 6);
        tank.mainWeights.put("倾陷强度", 6);
        tank.mainWeights.put("攻击力百分比", 2);
        tank.subWeights.put("防御力百分比", 20);
        tank.subWeights.put("生命值百分比", 20);
        tank.subWeights.put("防御力", 13);
        tank.subWeights.put("生命值", 13);
        tank.subWeights.put("环合强度", 4);
        tank.subWeights.put("倾陷强度", 4);
        tank.subWeights.put("攻击力百分比", 2);
        tank.subWeights.put("攻击力", 1);
        cr.profiles.put(tank.name, tank);

        // ---- hp：生命 ----
        Profile hp = new Profile();
        hp.name = "hp";
        hp.label = "生命";
        hp.maxTotal = 92;
        hp.mainWeights.put("生命值百分比", 34);
        hp.mainWeights.put("治疗加成", 18);
        hp.mainWeights.put("防御力百分比", 14);
        hp.mainWeights.put("环合强度", 8);
        hp.mainWeights.put("倾陷强度", 8);
        hp.mainWeights.put("攻击力百分比", 4);
        hp.subWeights.put("生命值百分比", 22);
        hp.subWeights.put("生命值", 16);
        hp.subWeights.put("防御力百分比", 10);
        hp.subWeights.put("防御力", 10);
        hp.subWeights.put("环合强度", 4);
        hp.subWeights.put("倾陷强度", 4);
        hp.subWeights.put("攻击力百分比", 3);
        hp.subWeights.put("攻击力", 1);
        cr.profiles.put(hp.name, hp);

        // ---- healer：治疗 ----
        Profile healer = new Profile();
        healer.name = "healer";
        healer.label = "治疗";
        healer.maxTotal = 89;
        healer.mainWeights.put("治疗加成", 34);
        healer.mainWeights.put("生命值百分比", 30);
        healer.mainWeights.put("防御力百分比", 8);
        healer.mainWeights.put("环合强度", 8);
        healer.mainWeights.put("倾陷强度", 8);
        healer.mainWeights.put("攻击力百分比", 4);
        healer.subWeights.put("生命值百分比", 22);
        healer.subWeights.put("生命值", 16);
        healer.subWeights.put("环合强度", 7);
        healer.subWeights.put("倾陷强度", 7);
        healer.subWeights.put("防御力百分比", 6);
        healer.subWeights.put("防御力", 4);
        healer.subWeights.put("攻击力百分比", 2);
        healer.subWeights.put("攻击力", 1);
        cr.profiles.put(healer.name, healer);

        // ---- 等级 ----
        String[][] gs = {
                {"大毕业", "0.84", "#FF4D4D"},
                {"毕业", "0.67", "#FFB300"},
                {"过渡", "0.43", "#64B5F6"},
                {"垃圾", "0.0", "#787878"},
        };
        for (String[] g : gs) {
            Grade gg = new Grade();
            gg.name = g[0];
            gg.ratio = Double.parseDouble(g[1]);
            gg.color = g[2];
            cr.grades.add(gg);
            cr.colors.put(g[0], g[2]);
        }
        return cr;
    }

    // ==================== 识别归一 ====================

    /**
     * 把 OCR 文本归一成标准主属性名。
     * 主属性文本形如「倾陷强度」「光属性异能伤害增强」，通常不带数值（数值在右侧）。
     */
    public String matchMain(String raw) {
        if (raw == null) return null;
        String t = normalize(raw);
        if (t.isEmpty()) return null;

        // 先做「最长精确包含」匹配，避免「生命值百分比」被「生命值」抢走
        String best = null;
        int bestLen = 0;
        List<String> pool = mainAffixes.isEmpty() ? subAffixes : mainAffixes;
        for (String m : pool) {
            if (t.contains(m) && m.length() > bestLen) {
                best = m;
                bestLen = m.length();
            }
        }
        if (best != null) return best;

        // 属性伤害：形如「X属性异能伤害增强」，X 可能被 OCR 读错
        if (t.contains("异能伤害") || t.contains("属性异能")) {
            for (String e : new String[]{"光", "灵", "咒", "暗", "魂", "相"}) {
                if (t.contains(e)) return e + "属性异能伤害增强";
            }
        }
        if (t.contains("心灵")) return "心灵伤害增强";
        if (t.contains("治疗")) return "治疗加成";
        return null;
    }

    /**
     * 副属性归一。副属性文本带数值，如「伤害增加10.00%」「防御力增加80」。
     * 复用 AffixMatcher 的词条映射，再用本规则的 subAffixes 校验。
     */
    public String matchSub(String raw) {
        if (raw == null) return null;
        String t = normalize(raw);
        if (t.isEmpty()) return null;

        // 通用伤害增强：OCR 常出现「伤害增加」「通用伤害」
        if (t.contains("伤害增加") || t.contains("通用伤害") || t.contains("伤害增强")) {
            // 「X属性异能伤害增强」出现在副词条的情况不存在，但保险起见排除
            if (!t.contains("异能")) {
                if (t.contains("通用") || t.contains("增加") || t.contains("增强")) {
                    return "通用伤害增强";
                }
            }
        }

        String std = AffixMatcher.match(t);
        if (std != null && (subAffixes.isEmpty() || subAffixes.contains(std))) return std;

        String best = null;
        int bestLen = 0;
        for (String m : subAffixes) {
            if (t.contains(m) && m.length() > bestLen) {
                best = m;
                bestLen = m.length();
            }
        }
        return best;
    }

    /** 套装名归一 */
    public String matchSet(String raw) {
        if (raw == null) return null;
        String t = raw.replace("「", "").replace("」", "")
                .replace("『", "").replace("』", "")
                .replace("【", "").replace("】", "")
                .replaceAll("[\\s\\u3000]", "");
        if (t.isEmpty()) return null;

        // 1) 正式名直接包含
        for (String s : sets) {
            if (t.contains(s)) return s;
        }

        // 2) 别名包含
        for (SetDetail d : setDetails.values()) {
            for (String a : d.aliases) {
                if (!a.isEmpty() && t.contains(a)) return d.name;
            }
        }

        // 3) 去掉分隔符再比（OCR 常把「·」读成其他符号）
        String t2 = sep(t);
        for (String s : sets) {
            if (t2.contains(sep(s))) return s;
        }
        for (SetDetail d : setDetails.values()) {
            for (String a : d.aliases) {
                String aa = sep(a);
                if (!aa.isEmpty() && t2.contains(aa)) return d.name;
            }
        }
        return null;
    }

    private static String sep(String s) {
        return s.replaceAll("[·・••.。\\-—_]", "");
    }

    /** 取套装详情，没有返回 null */
    public SetDetail detail(String setName) {
        if (setName == null) return null;
        return setDetails.get(setName);
    }

    /** 去空格与常见干扰符号 */
    private static String normalize(String raw) {
        return raw.replaceAll("[\\s\\u3000]", "")
                .replace("：", "").replace(":", "")
                .replace("_", "").replace("—", "").replace("-", "");
    }

    // ==================== 判定 ====================

    /**
     * 判定一套卡带 / 驱动块（总分制）。
     *
     * 规则：
     *   1. 按套装找到对应 Profile（elemDPS / genDPS / support / tank / hp / healer）
     *   2. 主属性分 = profile.mainWeights[主属性]
     *      - 属性套若主属性等于该套的 matchElement，直接给该属性的最高权重
     *   3. 副属性分 = Σ profile.subWeights + 双爆加成
     *   4. 总分 = 主 + 副，按 profile.maxTotal 的比例查等级
     *   5. 主属性 0 分 → 强制「垃圾」
     */
    public Result evaluate(String mainAffix, List<String> subs, String set) {
        Result r = new Result();
        r.mainAffix = mainAffix;
        r.subAffixes.addAll(subs);
        r.set = set;

        // ---- 找到权重档 ----
        SetDetail sd = detail(set);
        String pname = sd != null ? sd.profile : "elemDPS";
        Profile p = profiles.get(pname);
        if (p == null) p = profiles.get("elemDPS");
        if (p == null) {
            r.grade = "垃圾";
            r.color = colorOf("垃圾");
            r.detail = "无权重档";
            return r;
        }
        r.profileName = p.label;
        r.maxTotal = p.maxTotal;

        // ---- 主属性分 ----
        if (mainAffix != null) {
            Integer w = p.mainWeights.get(mainAffix);
            r.mainScore = w != null ? w : 0;

            // 属性套 + 本命属性 → 提权到最高（表格里该属性写 10，命中时升到 32）
            if (sd != null && !sd.matchElement.isEmpty()
                    && sd.matchElement.equals(mainAffix)) {
                r.mainScore = 32;
                r.elementMatch = true;
            }
        }

        // ---- 主属性无效：整件作废，副词条不再计算 ----
        if (r.mainScore <= 0) {
            r.subScore = 0;
            r.total = 0;
            r.ratio = 0;
            r.grade = "垃圾";
            r.color = colorOf("垃圾");
            r.detail = mainAffix == null ? "主属性未识别" : "主属性无效";
            return r;
        }

        // ---- 副属性分 ----
        int sum = 0;
        for (String s : subs) {
            Integer w = p.subWeights.get(s);
            if (w != null) sum += w;
        }
        if (subs.contains("暴击率") && subs.contains("暴击伤害")) sum += doubleCritBonus;
        r.subScore = sum;

        // ---- 总分 ----
        r.total = r.mainScore + r.subScore;
        int max = Math.max(1, p.maxTotal);
        double ratio = r.total / (double) max;

        // ---- 查等级 ----
        r.grade = null;
        for (Grade g : grades) {
            if (ratio >= g.ratio) {
                r.grade = g.name;
                r.color = g.color;
                break;
            }
        }
        if (r.grade == null) {
            r.grade = grades.isEmpty() ? "垃圾" : grades.get(grades.size() - 1).name;
            r.color = colorOf(r.grade);
        }

        r.ratio = ratio;
        r.detail = "主" + r.mainScore + "+副" + r.subScore
                + "/" + p.maxTotal
                + (r.elementMatch ? " 本命" : "");
        return r;
    }

    public String colorOf(String grade) {
        String c = colors.get(grade);
        return c != null ? c : "#AAAAAA";
    }

    // ==================== 词条着色 ====================

    /** 金色：权重达该档最高值一半以上 */
    public static final String COLOR_HIGH = "#FFB300";
    /** 白色：其余词条 */
    public static final String COLOR_NORMAL = "#E8EAED";

    /**
     * 按套装对应权重档给副词条着色，只分两档：
     *   权重 >= 该档副属性最高权重 / 2  → 金色
     *   其余                            → 白色
     *
     * 例如 elemDPS 副属性最高 20（双爆），攻%/通用伤害 15 >= 10 也是金色；
     *      tank 副属性最高 20（防%/生命%），防%/生命% 金色，防御/生命 13 >= 10 也金色。
     */
    public String colorForSub(String setName, String affix) {
        if (affix == null) return COLOR_NORMAL;
        SetDetail sd = detail(setName);
        String pname = sd != null ? sd.profile : "elemDPS";
        Profile p = profiles.get(pname);
        if (p == null) p = profiles.get("elemDPS");
        if (p == null) return COLOR_NORMAL;

        Integer w = p.subWeights.get(affix);
        if (w == null || w <= 0) return COLOR_NORMAL;

        int max = 1;
        for (Integer v : p.subWeights.values()) {
            if (v != null && v > max) max = v;
        }
        return w * 2 >= max ? COLOR_HIGH : COLOR_NORMAL;
    }
}