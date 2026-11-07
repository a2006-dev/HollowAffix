package com.hollow.affix;

import android.content.SharedPreferences;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 自动操作规则：按等级配置是否自动锁定 / 自动弃置。
 * 等级名从 RuleSet 动态获取，换规则自动适配。
 */
public class AutoRule {

    private static final String PREF = "autorule";
    private static final String K_LOCK = "lock_";
    private static final String K_DISCARD = "discard_";
    private static final String K_ENABLED = "enabled";

    /** 锁定规则：等级名 → 是否启用 */
    private static final Map<String, Boolean> lockMap = new LinkedHashMap<>();
    /** 弃置规则 */
    private static final Map<String, Boolean> discardMap = new LinkedHashMap<>();
    private static boolean loaded = false;

    /** 从规则集同步等级列表（新增等级补默认值，已删除的等级清掉） */
    public static synchronized void sync(RuleSet rs) {
        Set<String> grades = collectGrades(rs);
        SharedPreferences sp = null;
        try {
            sp = ContextHolder.get().getSharedPreferences(PREF, 0);
        } catch (Throwable ignored) {}

        lockMap.clear();
        discardMap.clear();
        for (String g : grades) {
            boolean dl = sp != null && sp.getBoolean(K_LOCK + g, false);
            boolean dd = sp != null && sp.getBoolean(K_DISCARD + g, false);
            lockMap.put(g, dl);
            discardMap.put(g, dd);
        }
        loaded = true;
    }

    /** 从规则集提取所有等级（保持规则顺序，去重） */
    public static Set<String> collectGrades(RuleSet rs) {
        Set<String> set = new LinkedHashSet<>();
        if (rs != null) {
            for (RuleSet.Rule r : rs.rules) {
                if (r.grade != null && !r.grade.isEmpty()) set.add(r.grade);
            }
            if (rs.fallback != null && !rs.fallback.isEmpty()) set.add(rs.fallback);
        }
        return set;
    }

    public static Map<String, Boolean> getLock() {
        ensure();
        return lockMap;
    }

    public static Map<String, Boolean> getDiscard() {
        ensure();
        return discardMap;
    }

    private static void ensure() {
        if (!loaded) {
            // 未初始化时用默认规则同步一次
            sync(RuleSet.defaultSet());
        }
    }

    public static void setLock(String grade, boolean on) {
        ensure();
        lockMap.put(grade, on);
        put(K_LOCK + grade, on);
    }

    public static void setDiscard(String grade, boolean on) {
        ensure();
        discardMap.put(grade, on);
        put(K_DISCARD + grade, on);
    }

    private static void put(String key, boolean v) {
        try {
            ContextHolder.get().getSharedPreferences(PREF, 0).edit()
                    .putBoolean(key, v).apply();
        } catch (Throwable ignored) {}
    }

    /** 该评级是否需要自动锁定 */
    public static boolean shouldLock(String grade) {
        ensure();
        Boolean b = lockMap.get(grade);
        return b != null && b;
    }

    /** 该评级是否需要自动弃置 */
    public static boolean shouldDiscard(String grade) {
        ensure();
        Boolean b = discardMap.get(grade);
        return b != null && b;
    }

    /** 是否有任何自动操作启用 */
    public static boolean anyEnabled() {
        if (!isEnabled()) return false;
        ensure();
        for (Boolean b : lockMap.values()) if (b != null && b) return true;
        for (Boolean b : discardMap.values()) if (b != null && b) return true;
        return false;
    }

    /** 总开关是否开启 */
    public static boolean isEnabled() {
        try {
            return ContextHolder.get().getSharedPreferences(PREF, 0)
                    .getBoolean(K_ENABLED, false);
        } catch (Throwable e) {
            return false;
        }
    }

    /** 设置总开关 */
    public static void setEnabled(boolean on) {
        try {
            ContextHolder.get().getSharedPreferences(PREF, 0).edit()
                    .putBoolean(K_ENABLED, on).apply();
        } catch (Throwable ignored) {}
    }

    public static void reset() {
        lockMap.clear();
        discardMap.clear();
        loaded = false;
        try {
            ContextHolder.get().getSharedPreferences(PREF, 0).edit().clear().apply();
        } catch (Throwable ignored) {}
    }
}