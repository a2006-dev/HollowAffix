package com.hollow.affix;

import android.content.SharedPreferences;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class AutoRule {

    private static final String PREF = "autorule";
    private static final String K_LOCK = "lock_";
    private static final String K_DISCARD = "discard_";
    private static final String K_ENABLED = "enabled";

    private static final Map<String, Boolean> lockMap = new LinkedHashMap<>();

    private static final Map<String, Boolean> discardMap = new LinkedHashMap<>();
    private static boolean loaded = false;

    private static volatile boolean enabled = false;

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

        if (sp != null) enabled = sp.getBoolean(K_ENABLED, false);
        loaded = true;
    }

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

    public static boolean shouldLock(String grade) {
        ensure();
        Boolean b = lockMap.get(grade);
        return b != null && b;
    }

    public static boolean shouldDiscard(String grade) {
        ensure();
        Boolean b = discardMap.get(grade);
        return b != null && b;
    }

    public static boolean anyEnabled() {
        if (!isEnabled()) return false;
        ensure();
        for (Boolean b : lockMap.values()) if (b != null && b) return true;
        for (Boolean b : discardMap.values()) if (b != null && b) return true;
        return false;
    }

    public static boolean isEnabled() {
        if (!loaded) ensure();
        return enabled;
    }

    public static void setEnabled(boolean on) {
        enabled = on;
        try {
            ContextHolder.get().getSharedPreferences(PREF, 0).edit()
                    .putBoolean(K_ENABLED, on).apply();
        } catch (Throwable ignored) {}
    }

    public static void reset() {
        lockMap.clear();
        discardMap.clear();
        loaded = false;
        enabled = false;
        try {
            ContextHolder.get().getSharedPreferences(PREF, 0).edit().clear().apply();
        } catch (Throwable ignored) {}
    }
}
