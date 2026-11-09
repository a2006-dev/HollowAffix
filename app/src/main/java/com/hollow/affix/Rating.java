package com.hollow.affix;

import java.util.ArrayList;
import java.util.List;

public class Rating {

    public static class Result {
        public List<String> affixes = new ArrayList<>();
        public int score = 0;
        public String grade = "?";
        public String reason = "";
        public String color = "#AAAAAA";
    }

    public static Result evaluate(List<String> affixes, RuleSet rs) {
        Result r = new Result();
        r.affixes.addAll(affixes);

        int sum = 0;
        for (String a : affixes) {
            Integer w = rs.weights.get(a);
            if (w != null) sum += w;
        }
        boolean hasCR = affixes.contains("暴击率");
        boolean hasCD = affixes.contains("暴击伤害");
        if (hasCR && hasCD) sum += rs.doubleCritBonus;
        r.score = sum;

        for (RuleSet.Rule rule : rs.rules) {
            if (matchRule(rule, affixes, rs)) {
                r.grade = rule.grade;
                r.reason = rule.desc;
                r.color = rs.colorOf(rule.grade);
                return r;
            }
        }

        r.grade = rs.fallback;
        r.reason = "无匹配规则";
        r.color = rs.colorOf(rs.fallback);
        return r;
    }

    private static boolean matchRule(RuleSet.Rule rule, List<String> affixes, RuleSet rs) {

        if (rule.minCount > 0 && affixes.size() < rule.minCount) return false;

        for (String req : rule.require) {
            if (!satisfy(req, affixes, rs)) return false;
        }

        if (!rule.requireAny.isEmpty()) {
            boolean any = false;
            for (String req : rule.requireAny) {
                if (satisfy(req, affixes, rs)) { any = true; break; }
            }
            if (!any) return false;
        }
        return true;
    }

    private static boolean satisfy(String item, List<String> affixes, RuleSet rs) {
        List<String> group = rs.groups.get(item);

        if ("双爆".equals(item) && group != null) {
            for (String m : group) {
                if (!affixes.contains(m)) return false;
            }
            return true;
        }

        if ("单爆".equals(item) && group != null) {
            int cnt = 0;
            for (String m : group) {
                if (affixes.contains(m)) cnt++;
            }
            return cnt == 1;
        }

        if (group != null) {
            for (String m : group) {
                if (affixes.contains(m)) return true;
            }
            return false;
        }

        return affixes.contains(item);
    }
}
