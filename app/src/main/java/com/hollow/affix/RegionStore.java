package com.hollow.affix;

import android.content.SharedPreferences;

public class RegionStore {

    public static final int TYPE_SCAN = 0;
    public static final int TYPE_LOCK = 1;
    public static final int TYPE_DISCARD = 2;

    public static final int COUNT = 3;

    private static final String PREF = "region2";
    private static final String[] PREFIX = {"scan_", "lock_", "discard_"};

    public static final int DEF_X = 2022, DEF_Y = 244, DEF_W = 783, DEF_H = 1140;

    private static volatile int[][] cache = new int[COUNT][];

    public static int[] get(int type) {
        if (type < 0 || type >= COUNT) return null;
        if (cache[type] != null) return cache[type].clone();

        int[] r = null;
        try {
            SharedPreferences sp = ContextHolder.get().getSharedPreferences(PREF, 0);
            int x = sp.getInt(PREFIX[type] + "x", -1);
            if (x >= 0) {
                r = new int[]{
                        x,
                        sp.getInt(PREFIX[type] + "y", 0),
                        sp.getInt(PREFIX[type] + "w", 0),
                        sp.getInt(PREFIX[type] + "h", 0)
                };
            }
        } catch (Throwable ignored) {}

        if (r != null && type == TYPE_SCAN && r[3] < 900) {
            int[] old = r;
            r = new int[]{DEF_X, DEF_Y, DEF_W, DEF_H};
            try {
                SharedPreferences sp = ContextHolder.get().getSharedPreferences(PREF, 0);
                sp.edit()
                        .putInt(PREFIX[type] + "x", r[0])
                        .putInt(PREFIX[type] + "y", r[1])
                        .putInt(PREFIX[type] + "w", r[2])
                        .putInt(PREFIX[type] + "h", r[3])
                        .apply();
            } catch (Throwable ignored) {}
            Logger.appendTo("region.txt", "识别区已从旧区域 "
                    + old[2] + "x" + old[3] + " 升级为 " + r[2] + "x" + r[3]);
        }

        if (r == null && type == TYPE_SCAN) {
            r = new int[]{DEF_X, DEF_Y, DEF_W, DEF_H};
        }
        cache[type] = r;
        return r == null ? null : r.clone();
    }

    public static int[] scan() {
        int[] r = get(TYPE_SCAN);
        return r != null ? r : new int[]{DEF_X, DEF_Y, DEF_W, DEF_H};
    }

    public static int[] center(int type) {
        int[] r = get(type);
        if (r == null) return null;
        return new int[]{r[0] + r[2] / 2, r[1] + r[3] / 2};
    }

    public static void set(int type, int x, int y, int w, int h) {
        if (type < 0 || type >= COUNT) return;
        if (w < 20 || h < 20) {
            Logger.appendTo("region.txt", "拒绝(太小)\ttype=" + type
                    + "\t" + x + "\t" + y + "\t" + w + "\t" + h);
            return;
        }
        cache[type] = new int[]{x, y, w, h};
        try {
            ContextHolder.get().getSharedPreferences(PREF, 0).edit()
                    .putInt(PREFIX[type] + "x", x)
                    .putInt(PREFIX[type] + "y", y)
                    .putInt(PREFIX[type] + "w", w)
                    .putInt(PREFIX[type] + "h", h)
                    .apply();
        } catch (Throwable ignored) {}

        String name = nameOf(type);
        Logger.i("REGION", name + " 更新 " + x + "," + y + " " + w + "x" + h);
        Logger.appendTo("region.txt", name + "\t" + x + "\t" + y + "\t" + w + "\t" + h);
    }

    public static String nameOf(int type) {
        switch (type) {
            case TYPE_SCAN: return "识别区";
            case TYPE_LOCK: return "锁定键";
            case TYPE_DISCARD: return "弃置键";
            default: return "区域";
        }
    }

    public static void clear(int type) {
        if (type < 0 || type >= COUNT) return;
        cache[type] = null;
        try {
            ContextHolder.get().getSharedPreferences(PREF, 0).edit()
                    .remove(PREFIX[type] + "x")
                    .remove(PREFIX[type] + "y")
                    .remove(PREFIX[type] + "w")
                    .remove(PREFIX[type] + "h")
                    .apply();
        } catch (Throwable ignored) {}
    }

    public static void resetAll() {
        cache = new int[COUNT][];
        try {
            ContextHolder.get().getSharedPreferences(PREF, 0).edit().clear().apply();
        } catch (Throwable ignored) {}
        Logger.i("REGION", "全部区域已重置");
    }

    public static boolean has(int type) {
        return get(type) != null;
    }

    public static String describe(int type) {
        int[] r = get(type);
        String tag = nameOf(type);
        if (r == null) return tag + " 未设置";
        if (type == TYPE_SCAN) {
            return tag + " " + r[2] + "×" + r[3];
        }
        return tag + " (" + (r[0] + r[2] / 2) + "," + (r[1] + r[3] / 2) + ")";
    }
}
