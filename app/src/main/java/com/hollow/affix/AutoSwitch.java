package com.hollow.affix;

import android.content.Context;
import android.content.SharedPreferences;

public class AutoSwitch {

    private static final String PREF = "autorule";
    private static final String KEY = "enabled";

    public static boolean isEnabled() {
        Context c = safeContext();
        if (c == null) return AutoRule.isEnabled();
        try {
            return c.getSharedPreferences(PREF, 0).getBoolean(KEY, false);
        } catch (Throwable e) {
            return AutoRule.isEnabled();
        }
    }

    public static void setEnabled(Context ctx, boolean on) {
        Context c = ctx != null ? ctx : safeContext();
        if (c != null) {
            try {
                c.getSharedPreferences(PREF, 0).edit().putBoolean(KEY, on).apply();
            } catch (Throwable ignored) {}
        }

        AutoRule.setEnabled(on);
    }

    public static void setEnabled(boolean on) {
        setEnabled(null, on);
    }

    private static Context safeContext() {
        try {
            return ContextHolder.get();
        } catch (Throwable e) {
            return null;
        }
    }
}
