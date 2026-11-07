package com.hollow.affix;

import android.content.SharedPreferences;

/**
 * 区域管理：支持三类框选目标
 *   1. 识别区域（属性面板 OCR 范围）
 *   2. 锁定按钮（点击位置）
 *   3. 弃置按钮（点击位置）
 */
public class RegionStore {

    public static final int TYPE_SCAN = 0;    // 识别区域
    public static final int TYPE_LOCK = 1;    // 锁定按钮
    public static final int TYPE_DISCARD = 2; // 弃置按钮
    /** 空幕详情面板（卡带/驱动块的主属性+副属性+套装名） */
    public static final int TYPE_CASSETTE = 3;

    public static final int COUNT = 4;

    private static final String PREF = "region2";
    private static final String[] PREFIX = {"scan_", "lock_", "discard_", "cassette_"};

    /** 识别区域默认值（3168x1440 实测） */
    public static final int DEF_X = 2141, DEF_Y = 621, DEF_W = 762, DEF_H = 742;

    /**
     * 空幕详情面板默认值。基准 3168x1440。
     *
     * 实测（用户手动框选）：右下角 (2805,1384)，尺寸 783x1140
     *   => 左上角 (2022, 244)
     * 已用截图像素分析交叉验证：右边框 x≈2926、下边框 y≈1383，吻合。
     */
    public static final int CAS_X = 2022, CAS_Y = 244, CAS_W = 783, CAS_H = 1140;

    /** 缓存 [类型][x,y,w,h]，null 表示未设置 */
    private static volatile int[][] cache = new int[COUNT][];

    /** 读取指定类型的区域，未设置返回 null */
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

        // 识别区和空幕面板有默认值，按钮没有
        if (r == null && type == TYPE_SCAN) {
            r = new int[]{DEF_X, DEF_Y, DEF_W, DEF_H};
        } else if (r == null && type == TYPE_CASSETTE) {
            r = new int[]{CAS_X, CAS_Y, CAS_W, CAS_H};
        }
        cache[type] = r;
        return r == null ? null : r.clone();
    }

    /** 读取识别区域，永不为 null（有默认值） */
    public static int[] scan() {
        int[] r = get(TYPE_SCAN);
        return r != null ? r : new int[]{DEF_X, DEF_Y, DEF_W, DEF_H};
    }

    /** 取区域中心点，未设置返回 null */
    public static int[] center(int type) {
        int[] r = get(type);
        if (r == null) return null;
        return new int[]{r[0] + r[2] / 2, r[1] + r[3] / 2};
    }

    /** 保存区域 */
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

    /** 类型显示名 */
    public static String nameOf(int type) {
        switch (type) {
            case TYPE_SCAN: return "识别区";
            case TYPE_LOCK: return "锁定键";
            case TYPE_DISCARD: return "弃置键";
            case TYPE_CASSETTE: return "空幕面板";
            default: return "区域";
        }
    }

    /** 清除指定类型 */
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

    /** 全部重置 */
    public static void resetAll() {
        cache = new int[COUNT][];
        try {
            ContextHolder.get().getSharedPreferences(PREF, 0).edit().clear().apply();
        } catch (Throwable ignored) {}
        Logger.i("REGION", "全部区域已重置");
    }

    /** 是否已设置 */
    public static boolean has(int type) {
        return get(type) != null;
    }

    public static String describe(int type) {
        int[] r = get(type);
        String tag = nameOf(type);
        if (r == null) return tag + " 未设置";
        if (type == TYPE_SCAN || type == TYPE_CASSETTE) {
            return tag + " " + r[2] + "×" + r[3];
        }
        return tag + " (" + (r[0] + r[2] / 2) + "," + (r[1] + r[3] / 2) + ")";
    }
}