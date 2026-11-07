package com.hollow.affix;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 规则校验：保存 / 导入前执行，收集所有问题。
 *
 * 校验分两类：
 *   错误(error) —— 必须修复才能保存
 *   警告(warn)  —— 可以保存，但可能不是本意
 */
public class RuleValidator {

    /** 单条问题 */
    public static class Issue {
        public final String field;
        public final String message;
        public final boolean error;

        public Issue(String field, String message, boolean error) {
            this.field = field;
            this.message = message;
            this.error = error;
        }

        @Override
        public String toString() {
            return (error ? "[错误] " : "[警告] ") + field + "：" + message;
        }
    }

    /** 校验结果 */
    public static class Report {
        public final List<Issue> issues = new ArrayList<>();

        public void err(String field, String msg) {
            issues.add(new Issue(field, msg, true));
        }

        public void warn(String field, String msg) {
            issues.add(new Issue(field, msg, false));
        }

        public boolean ok() {
            for (Issue i : issues) if (i.error) return false;
            return true;
        }

        public boolean hasError() {
            return !ok();
        }

        public String summary() {
            if (issues.isEmpty()) return "校验通过";
            int e = 0, w = 0;
            for (Issue i : issues) if (i.error) e++; else w++;
            StringBuilder sb = new StringBuilder();
            sb.append("发现 ").append(e).append(" 个错误");
            if (w > 0) sb.append("、").append(w).append(" 个警告");
            sb.append("：\n");
            for (Issue i : issues) sb.append(i).append('\n');
            return sb.toString();
        }

        public String firstError() {
            for (Issue i : issues) if (i.error) return i.toString();
            return null;
        }
    }

    /** 判断颜色是否合法 #RRGGBB */
    private static boolean validColor(String c) {
        if (c == null) return false;
        return c.matches("^#[0-9A-Fa-f]{6}$");
    }

    // ==================== 驱动块 ====================

    /**
     * 校验驱动块规则。
     * @param rs 规则对象
     * @param knownAffixes 合法词条名（固定 11 项）
     */
    public static Report validateDrive(RuleSet rs, List<String> knownAffixes) {
        Report r = new Report();

        if (rs.name == null || rs.name.trim().isEmpty()) {
            r.err("规则名", "不能为空");
        }
        if (rs.version < 1) {
            r.err("版本", "必须 >= 1");
        }

        if (rs.rules.isEmpty()) {
            r.err("判定规则", "至少需要 1 条规则");
        }

        // 权重
        Set<String> affixSet = new HashSet<>(knownAffixes);
        for (Map.Entry<String, Integer> e : rs.weights.entrySet()) {
            String k = e.getKey();
            Integer v = e.getValue();
            if (v == null) {
                r.err("权重/" + k, "必须填写数字");
            } else if (v < 0) {
                r.err("权重/" + k, "不能为负数");
            }
            if (!affixSet.contains(k)) {
                r.warn("权重/" + k, "不是已知词条，可能永远不会命中");
            }
        }
        if (rs.weights.isEmpty()) {
            r.err("权重", "至少需要 1 个词条权重");
        }
        for (String a : knownAffixes) {
            if (!rs.weights.containsKey(a)) {
                r.warn("权重/" + a, "未设置，按 0 处理");
            }
        }

        // 组
        for (Map.Entry<String, List<String>> e : rs.groups.entrySet()) {
            String g = e.getKey();
            List<String> mem = e.getValue();
            if (g == null || g.trim().isEmpty()) {
                r.err("组", "组名不能为空");
                continue;
            }
            if (mem == null || mem.isEmpty()) {
                r.err("组/" + g, "至少需要 1 个成员");
                continue;
            }
            for (String m : mem) {
                if (!affixSet.contains(m)) {
                    r.err("组/" + g, "成员「" + m + "」不是合法词条名");
                }
            }
        }

        // 判定规则
        Set<String> gradeNames = new HashSet<>();
        for (int i = 0; i < rs.rules.size(); i++) {
            RuleSet.Rule rule = rs.rules.get(i);
            String tag = "规则" + (i + 1);
            if (rule.grade == null || rule.grade.trim().isEmpty()) {
                r.err(tag + "/等级", "不能为空");
            } else {
                gradeNames.add(rule.grade);
            }
            if (rule.require.isEmpty() && rule.requireAny.isEmpty()
                    && rule.minCount <= 0) {
                r.err(tag + "/条件", "至少要设置一个条件");
            }
            for (String req : rule.require) {
                if (!affixSet.contains(req) && !rs.groups.containsKey(req)) {
                    r.err(tag + "/条件", "「" + req + "」既不是词条也不是组");
                }
            }
            for (String req : rule.requireAny) {
                if (!affixSet.contains(req) && !rs.groups.containsKey(req)) {
                    r.err(tag + "/条件", "「" + req + "」既不是词条也不是组");
                }
            }
            if (rule.minCount < 0) {
                r.err(tag + "/最少词条数", "不能为负数");
            } else if (rule.minCount > 4) {
                r.warn(tag + "/最少词条数", "超过 4，实际不可能满足");
            }
        }

        // 兜底等级
        if (rs.fallback == null || rs.fallback.trim().isEmpty()) {
            r.err("兜底等级", "不能为空");
        } else if (!gradeNames.contains(rs.fallback)) {
            r.warn("兜底等级", "「" + rs.fallback + "」不在判定规则的等级中");
        }

        // 颜色
        for (Map.Entry<String, String> e : rs.colors.entrySet()) {
            if (!validColor(e.getValue())) {
                r.err("颜色/" + e.getKey(), "格式必须是 #RRGGBB");
            }
        }
        for (String g : gradeNames) {
            if (!rs.colors.containsKey(g)) {
                r.warn("颜色/" + g, "未设置颜色，将用默认灰色");
            }
        }

        if (rs.doubleCritBonus < 0) {
            r.err("双爆加成", "不能为负数");
        }

        return r;
    }

    // ==================== 空幕 ====================

    /**
     * 校验空幕规则。
     * @param cr 空幕规则
     * @param knownMain 合法主属性名（15 项）
     * @param knownSub  合法副属性名（11 项）
     */
    public static Report validateCassette(CassetteRule cr,
                                          List<String> knownMain,
                                          List<String> knownSub) {
        Report r = new Report();

        if (cr.name == null || cr.name.trim().isEmpty()) {
            r.err("规则名", "不能为空");
        }
        if (cr.version < 1) {
            r.err("版本", "必须 >= 1");
        }

        // 等级：必须从高到低，ratio 递减
        if (cr.grades.isEmpty()) {
            r.err("等级", "至少需要 1 个等级");
        }
        double lastRatio = 2.0;
        for (int i = 0; i < cr.grades.size(); i++) {
            CassetteRule.Grade g = cr.grades.get(i);
            String tag = "等级" + (i + 1);
            if (g.name == null || g.name.trim().isEmpty()) {
                r.err(tag + "/名称", "不能为空");
            }
            if (g.ratio < 0 || g.ratio > 1) {
                r.err(tag + "/门槛", "必须在 0 ~ 1 之间");
            } else if (g.ratio >= lastRatio) {
                r.err(tag + "/门槛", "必须低于上一档（等级需从高到低排列）");
            } else {
                lastRatio = g.ratio;
            }
            if (!validColor(g.color)) {
                r.err(tag + "/颜色", "格式必须是 #RRGGBB");
            }
        }

        // 权重档
        if (cr.profiles.isEmpty()) {
            r.err("权重档", "至少需要 1 个权重档");
        }
        Set<String> mainSet = new HashSet<>(knownMain);
        Set<String> subSet = new HashSet<>(knownSub);
        for (Map.Entry<String, CassetteRule.Profile> e : cr.profiles.entrySet()) {
            String pn = e.getKey();
            CassetteRule.Profile p = e.getValue();
            String plabel = (p.label == null || p.label.isEmpty()) ? pn : p.label;
            String tag = "权重档/" + plabel;

            if (p.maxTotal <= 0) {
                r.err(tag + "/满分", "必须大于 0");
            }
            if (p.mainWeights.isEmpty()) {
                r.err(tag + "/主属性权重", "至少需要 1 项");
            }
            if (p.subWeights.isEmpty()) {
                r.err(tag + "/副属性权重", "至少需要 1 项");
            }
            for (Map.Entry<String, Integer> w : p.mainWeights.entrySet()) {
                if (!mainSet.contains(w.getKey())) {
                    r.err(tag + "/主属性", "「" + w.getKey() + "」不是合法主属性名");
                }
                if (w.getValue() == null || w.getValue() < 0) {
                    r.err(tag + "/主属性/" + w.getKey(), "必须是非负数字");
                }
            }
            for (Map.Entry<String, Integer> w : p.subWeights.entrySet()) {
                if (!subSet.contains(w.getKey())) {
                    r.err(tag + "/副属性", "「" + w.getKey() + "」不是合法副属性名");
                }
                if (w.getValue() == null || w.getValue() < 0) {
                    r.err(tag + "/副属性/" + w.getKey(), "必须是非负数字");
                }
            }
            int mainMax = 0;
            for (Integer v : p.mainWeights.values()) {
                if (v != null && v > mainMax) mainMax = v;
            }
            if (mainMax == 0) {
                r.warn(tag + "/主属性", "所有主属性权重都是 0，任何主属性都判无效");
            }
        }

        // 套装
        if (cr.setDetails.isEmpty()) {
            r.err("套装", "至少需要 1 个套装");
        }
        Set<String> setNames = new HashSet<>();
        for (Map.Entry<String, CassetteRule.SetDetail> e : cr.setDetails.entrySet()) {
            CassetteRule.SetDetail d = e.getValue();
            String dname = (d.name == null || d.name.isEmpty()) ? e.getKey() : d.name;
            String tag = "套装/" + dname;
            if (d.name == null || d.name.trim().isEmpty()) {
                r.err(tag + "/名称", "不能为空");
            } else if (!setNames.add(d.name)) {
                r.err(tag + "/名称", "重名");
            }
            if (d.profile == null || d.profile.isEmpty()) {
                r.err(tag + "/权重档", "必须指定");
            } else if (!cr.profiles.containsKey(d.profile)) {
                r.err(tag + "/权重档", "「" + d.profile + "」不存在");
            }
            if (d.matchElement != null && !d.matchElement.isEmpty()
                    && !mainSet.contains(d.matchElement)) {
                r.err(tag + "/本命属性", "「" + d.matchElement + "」不是合法主属性名");
            }
        }

        // 配色与等级对应
        Set<String> gradeNames = new HashSet<>();
        for (CassetteRule.Grade g : cr.grades) {
            if (g.name != null) gradeNames.add(g.name);
            if (!cr.colors.containsKey(g.name)) {
                r.warn("颜色/" + g.name, "未设置配色，将用默认色");
            }
        }
        for (String k : cr.colors.keySet()) {
            if (!gradeNames.contains(k)) {
                r.warn("颜色/" + k, "没有对应等级");
            }
        }

        if (cr.doubleCritBonus < 0) {
            r.err("双爆加成", "不能为负数");
        }

        return r;
    }

    // ==================== JSON 结构预检 ====================

    /** 粗略检查 JSON 是否像驱动块规则 */
    public static Report checkDriveJson(String json) {
        Report r = new Report();
        if (json == null || json.trim().isEmpty()) {
            r.err("文件", "内容为空");
            return r;
        }
        try {
            org.json.JSONObject o = new org.json.JSONObject(json);
            if (!o.has("weights") && !o.has("rules")) {
                r.err("文件", "缺少 weights 或 rules，不像驱动块规则");
            }
        } catch (Throwable e) {
            r.err("文件", "JSON 格式错误：" + e.getMessage());
        }
        return r;
    }

    /** 粗略检查 JSON 是否像空幕规则 */
    public static Report checkCassetteJson(String json) {
        Report r = new Report();
        if (json == null || json.trim().isEmpty()) {
            r.err("文件", "内容为空");
            return r;
        }
        try {
            org.json.JSONObject o = new org.json.JSONObject(json);
            if (!o.has("profiles") && !o.has("setDetails")) {
                r.err("文件", "缺少 profiles 或 setDetails，不像空幕规则");
            }
        } catch (Throwable e) {
            r.err("文件", "JSON 格式错误：" + e.getMessage());
        }
        return r;
    }
}