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

public class CassetteRule {

    public String name = "空幕规则";
    public String author = "";
    public int version = 1;
    public String description = "";

    public String target = RuleType.CASSETTE;

    public List<String> mainAffixes = new ArrayList<>();

    public List<String> subAffixes = new ArrayList<>();

    public Map<String, Profile> profiles = new LinkedHashMap<>();

    public static class Profile {
        public String name = "";
        public String label = "";

        public int maxTotal = 100;
        public Map<String, Integer> mainWeights = new LinkedHashMap<>();
        public Map<String, Integer> subWeights = new LinkedHashMap<>();
    }

    public int doubleCritBonus = 3;

    public List<String> sets = new ArrayList<>();

    public Map<String, SetDetail> setDetails = new LinkedHashMap<>();

    public static class SetDetail {
        public String name = "";
        public List<String> aliases = new ArrayList<>();
        public String element = "";
        public String type = "";

        public String profile = "elemDPS";

        public String matchElement = "";
        public String two = "";
        public String four = "";
        public String note = "";
    }

    public List<Grade> grades = new ArrayList<>();
    public Map<String, String> colors = new LinkedHashMap<>();

    public static class Grade {
        public String name = "";

        public double ratio = 0;
        public String color = "#AAAAAA";
    }

    public static class Result {
        public String mainAffix = null;
        public int mainScore = 0;
        public List<String> subAffixes = new ArrayList<>();
        public int subScore = 0;
        public String set = null;
        public int total = 0;

        public String profileName = "";

        public int maxTotal = 100;

        public double ratio = 0;

        public boolean elementMatch = false;
        public String grade = "垃圾";
        public String color = "#787878";
        public String detail = "";
    }

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

    public static CassetteRule load(android.content.Context ctx) {
        String json = RuleStore.loadJson(ctx, RuleStore.TYPE_CASSETTE);
        if (json != null && !json.trim().isEmpty()) {
            return parse(json);
        }
        return loadBuiltin(ctx);
    }

    public static CassetteRule parse(String json) {
        CassetteRule cr = new CassetteRule();
        try {
            JSONObject o = new JSONObject(json);
            cr.name = o.optString("name", "空幕规则");
            cr.author = o.optString("author", "");
            cr.version = o.optInt("version", 1);
            cr.description = o.optString("description", "");
            cr.target = RuleType.normalize(o.optString("target", RuleType.CASSETTE));
            cr.doubleCritBonus = o.optInt("doubleCritBonus", 3);

            readArray(o.optJSONArray("mainAffixes"), cr.mainAffixes);
            readArray(o.optJSONArray("subAffixes"), cr.subAffixes);
            readArray(o.optJSONArray("sets"), cr.sets);

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

                    if (!cr.sets.contains(d.name)) cr.sets.add(d.name);
                }
            }

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

    public String matchMain(String raw) {
        if (raw == null) return null;
        String t = normalize(raw);
        if (t.isEmpty()) return null;

        String best = null;
        int bestLen = 0;
        for (String m : mainAffixes) {
            if (m != null && !m.isEmpty() && t.contains(m) && m.length() > bestLen) {
                best = m;
                bestLen = m.length();
            }
        }
        for (String m : subAffixes) {
            if (m != null && !m.isEmpty() && t.contains(m) && m.length() > bestLen) {
                best = m;
                bestLen = m.length();
            }
        }
        if (best != null) return best;

        if (t.contains("攻击力")) return "攻击力";
        if (t.contains("防御力")) return "防御力";
        if (t.contains("生命值")) return "生命值";

        if (t.contains("异能伤害") || t.contains("属性异能")) {
            for (String e : new String[]{"光", "灵", "咒", "暗", "魂", "相"}) {
                if (t.contains(e)) return e + "属性异能伤害增强";
            }
        }
        if (t.contains("心灵")) return "心灵伤害增强";
        if (t.contains("治疗")) return "治疗加成";
        return null;
    }

    public String matchSub(String raw) {
        if (raw == null) return null;
        String t = normalize(raw);
        if (t.isEmpty()) return null;

        if (t.contains("伤害增加") || t.contains("通用伤害") || t.contains("伤害增强")) {

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

    public String matchSet(String raw) {
        if (raw == null) return null;
        String t = raw.replace("「", "").replace("」", "")
                .replace("『", "").replace("』", "")
                .replace("【", "").replace("】", "")
                .replaceAll("[\\s\\u3000]", "");
        if (t.isEmpty()) return null;

        for (String s : sets) {
            if (t.contains(s)) return s;
        }

        for (SetDetail d : setDetails.values()) {
            for (String a : d.aliases) {
                if (!a.isEmpty() && t.contains(a)) return d.name;
            }
        }

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

    public SetDetail detail(String setName) {
        if (setName == null) return null;
        return setDetails.get(setName);
    }

    private static String normalize(String raw) {
        return raw.replaceAll("[\\s\\u3000]", "")
                .replace("：", "").replace(":", "")
                .replace("_", "").replace("—", "").replace("-", "");
    }

    public Result evaluate(String mainAffix, List<String> subs, String set) {
        Result r = new Result();
        r.mainAffix = mainAffix;
        r.subAffixes.addAll(subs);
        r.set = set;

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

        if (mainAffix != null) {
            Integer w = p.mainWeights.get(mainAffix);
            r.mainScore = w != null ? w : 0;

            if (sd != null && !sd.matchElement.isEmpty()
                    && sd.matchElement.equals(mainAffix)) {
                r.mainScore = 32;
                r.elementMatch = true;
            }
        }

        if (r.mainScore <= 0) {
            r.subScore = 0;
            r.total = 0;
            r.ratio = 0;
            r.grade = "垃圾";
            r.color = colorOf("垃圾");
            r.detail = mainAffix == null ? "主属性未识别" : "主属性无效";
            return r;
        }

        int sum = 0;
        for (String s : subs) {
            Integer w = p.subWeights.get(s);
            if (w != null) sum += w;
        }
        if (subs.contains("暴击率") && subs.contains("暴击伤害")) sum += doubleCritBonus;
        r.subScore = sum;

        r.total = r.mainScore + r.subScore;
        int max = Math.max(1, p.maxTotal);
        double ratio = r.total / (double) max;

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
        return (c == null || c.trim().isEmpty()) ? "#AAAAAA" : c;
    }

    public static final String COLOR_HIGH = "#FFB300";

    public static final String COLOR_NORMAL = "#E8EAED";

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

    public String toJson() {
        try {
            org.json.JSONObject o = new org.json.JSONObject();
            o.put("name", name);
            o.put("author", author);
            o.put("version", version);
            o.put("description", description);
            o.put("target", target);
            o.put("doubleCritBonus", doubleCritBonus);

            org.json.JSONArray marr = new org.json.JSONArray();
            for (String s : mainAffixes) marr.put(s);
            o.put("mainAffixes", marr);

            org.json.JSONArray sarr = new org.json.JSONArray();
            for (String s : subAffixes) sarr.put(s);
            o.put("subAffixes", sarr);

            org.json.JSONArray garr = new org.json.JSONArray();
            for (Grade g : grades) {
                org.json.JSONObject go = new org.json.JSONObject();
                go.put("name", g.name);
                go.put("ratio", g.ratio);
                go.put("color", g.color);
                garr.put(go);
            }
            o.put("grades", garr);

            org.json.JSONObject c = new org.json.JSONObject();
            for (Map.Entry<String, String> e : colors.entrySet()) {
                c.put(e.getKey(), e.getValue());
            }
            o.put("colors", c);

            org.json.JSONObject po = new org.json.JSONObject();
            for (Map.Entry<String, Profile> e : profiles.entrySet()) {
                Profile p = e.getValue();
                org.json.JSONObject pj = new org.json.JSONObject();
                pj.put("label", p.label);
                pj.put("maxTotal", p.maxTotal);

                org.json.JSONObject mw = new org.json.JSONObject();
                for (Map.Entry<String, Integer> w : p.mainWeights.entrySet()) {
                    mw.put(w.getKey(), w.getValue() == null ? 0 : w.getValue());
                }
                pj.put("mainWeights", mw);

                org.json.JSONObject sw = new org.json.JSONObject();
                for (Map.Entry<String, Integer> w : p.subWeights.entrySet()) {
                    sw.put(w.getKey(), w.getValue() == null ? 0 : w.getValue());
                }
                pj.put("subWeights", sw);

                po.put(e.getKey(), pj);
            }
            o.put("profiles", po);

            org.json.JSONArray sdArr = new org.json.JSONArray();
            for (SetDetail d : setDetails.values()) {
                org.json.JSONObject so = new org.json.JSONObject();
                so.put("name", d.name);
                if (!d.aliases.isEmpty()) {
                    org.json.JSONArray a = new org.json.JSONArray();
                    for (String s : d.aliases) a.put(s);
                    so.put("aliases", a);
                }
                so.put("element", d.element);
                so.put("type", d.type);
                so.put("profile", d.profile);
                so.put("matchElement", d.matchElement == null ? "" : d.matchElement);
                so.put("two", d.two);
                so.put("four", d.four);
                so.put("note", d.note);
                sdArr.put(so);
            }
            o.put("setDetails", sdArr);

            return o.toString(2);
        } catch (Throwable e) {
            Logger.appendTo("trace.txt", "空幕规则序列化失败: " + e);
            return "{}";
        }
    }

    public CassetteRule copy() {
        CassetteRule cr = new CassetteRule();
        cr.name = name;
        cr.author = author;
        cr.version = version;
        cr.description = description;
        cr.doubleCritBonus = doubleCritBonus;
        cr.mainAffixes.addAll(mainAffixes);
        cr.subAffixes.addAll(subAffixes);
        for (Map.Entry<String, String> e : colors.entrySet()) {
            cr.colors.put(e.getKey(), e.getValue());
        }
        for (Grade g : grades) {
            Grade n = new Grade();
            n.name = g.name;
            n.ratio = g.ratio;
            n.color = g.color;
            cr.grades.add(n);
        }
        for (Map.Entry<String, Profile> e : profiles.entrySet()) {
            Profile p = e.getValue();
            Profile n = new Profile();
            n.name = p.name;
            n.label = p.label;
            n.maxTotal = p.maxTotal;
            n.mainWeights.putAll(p.mainWeights);
            n.subWeights.putAll(p.subWeights);
            cr.profiles.put(e.getKey(), n);
        }
        for (Map.Entry<String, SetDetail> e : setDetails.entrySet()) {
            SetDetail d = e.getValue();
            SetDetail n = new SetDetail();
            n.name = d.name;
            n.aliases.addAll(d.aliases);
            n.element = d.element;
            n.type = d.type;
            n.profile = d.profile;
            n.matchElement = d.matchElement;
            n.two = d.two;
            n.four = d.four;
            n.note = d.note;
            cr.setDetails.put(e.getKey(), n);
            if (!cr.sets.contains(n.name)) cr.sets.add(n.name);
        }
        return cr;
    }

    public static List<String> mainAffixList() {
        return defaultRule().mainAffixes;
    }

    public static List<String> subAffixList() {
        return defaultRule().subAffixes;
    }
}
