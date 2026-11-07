package com.hollow.affix;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 判定引擎：按 RuleSet 中的规则依次匹配。
 */
public class Rating {

    /** 判定结果 */
    public static class Result {
        public List<String> affixes = new ArrayList<>();   // 识别到的词条名
        public int score = 0;
        public String grade = "?";
        public String reason = "";
        public String color = "#AAAAAA";
    }

    /**
     * 按规则集判定。
     * @param affixes 识别到的词条名列表
     * @param rs 规则集
     */
    public static Result evaluate(List<String> affixes, RuleSet rs) {
        Result r = new Result();
        r.affixes.addAll(affixes);

        // ---- 总分 ----
        int sum = 0;
        for (String a : affixes) {
            Integer w = rs.weights.get(a);
            if (w != null) sum += w;
        }
        boolean hasCR = affixes.contains("暴击率");
        boolean hasCD = affixes.contains("暴击伤害");
        if (hasCR && hasCD) sum += rs.doubleCritBonus;
        r.score = sum;

        // ---- 依次匹配规则 ----
        for (RuleSet.Rule rule : rs.rules) {
            if (matchRule(rule, affixes, rs)) {
                r.grade = rule.grade;
                r.reason = rule.desc;
                r.color = rs.colorOf(rule.grade);
                return r;
            }
        }

        // 全部不匹配
        r.grade = rs.fallback;
        r.reason = "无匹配规则";
        r.color = rs.colorOf(rs.fallback);
        return r;
    }

    /** 单条规则匹配 */
    private static boolean matchRule(RuleSet.Rule rule, List<String> affixes, RuleSet rs) {
        // 词条数下限
        if (rule.minCount > 0 && affixes.size() < rule.minCount) return false;

        // require：每项都要满足
        for (String req : rule.require) {
            if (!satisfy(req, affixes, rs)) return false;
        }

        // requireAny：至少满足一项
        if (!rule.requireAny.isEmpty()) {
            boolean any = false;
            for (String req : rule.requireAny) {
                if (satisfy(req, affixes, rs)) { any = true; break; }
            }
            if (!any) return false;
        }
        return true;
    }

    /**
     * 判断某一项是否满足。
     * 项可以是：
     *   - 具体词条名（如「攻击力百分比」）
     *   - 组名（如「大攻击」），组内任一成员命中即可
     *   - 特殊组「双爆」，要求组内全部成员命中
     *   - 特殊组「单爆」，要求组内命中数 == 1
     */
    private static boolean satisfy(String item, List<String> affixes, RuleSet rs) {
        List<String> group = rs.groups.get(item);

        // 「双爆」= 组内全部命中
        if ("双爆".equals(item) && group != null) {
            for (String m : group) {
                if (!affixes.contains(m)) return false;
            }
            return true;
        }

        // 「单爆」= 组内恰好命中 1 个
        if ("单爆".equals(item) && group != null) {
            int cnt = 0;
            for (String m : group) {
                if (affixes.contains(m)) cnt++;
            }
            return cnt == 1;
        }

        // 普通组：任一成员命中
        if (group != null) {
            for (String m : group) {
                if (affixes.contains(m)) return true;
            }
            return false;
        }

        // 具体词条名
        return affixes.contains(item);
    }
}