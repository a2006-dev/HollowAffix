package com.hollow.affix;

import android.content.SharedPreferences;

/**
 * 识别区域管理：框选结果持久化，立即生效。
 */
public class RegionStore {

    private static final String PREF = "region";
    private static final String K_X = "x", K_Y = "y", K_W = "w", K_H = "h";

    /** 默认区域（实机实测 3168x1440 基准） */
    public static final int DEF_X = 2141, DEF_Y = 621, DEF_W = 762, DEF_H = 742;

    /** 当前区域缓存 */
    private static volatile int[] cached = null;

    /** 读取当前识别区域 {x,y,w,h} */
    public static int[] get() {
        if (cached != null) return cached.clone();
        int[] def = {DEF_X, DEF_Y, DEF_W, DEF_H};
        try {
            SharedPreferences sp = ContextHolder.get().getSharedPreferences(PREF, 0);
            int x = sp.getInt(K_X, -1);
            if (x < 0) {
                cached = def;
                return def.clone();
            }
            cached = new int[]{
                    x, sp.getInt(K_Y, DEF_Y),
                    sp.getInt(K_W, DEF_W), sp.getInt(K_H, DEF_H)
            };
        } catch (Throwable e) {
            cached = def;
        }
        return cached.clone();
    }

    /** 保存新区域，立即生效（带合理性校验） */
    public static void set(int x, int y, int w, int h) {
        // 合理性检查：属性面板必须位于屏幕右侧、尺寸接近实测值
        boolean ok = w >= 450 && w <= 1200
                && h >= 400 && h <= 1200
                && x >= 1700 && y >= 200;
        if (!ok) {
            Logger.appendTo("region.txt",
                    "拒绝\t" + x + "\t" + y + "\t" + w + "\t" + h
                            + "\t尺寸或位置不合理，保持原值");
            // 提示用户
            android.widget.Toast.makeText(ContextHolder.get(),
                    "框选区域不合理，保持原区域", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        cached = new int[]{x, y, w, h};
        try {
            ContextHolder.get().getSharedPreferences(PREF, 0).edit()
                    .putInt(K_X, x).putInt(K_Y, y)
                    .putInt(K_W, w).putInt(K_H, h).apply();
        } catch (Throwable ignored) {}
        Logger.i("REGION", "识别区域更新 x=" + x + " y=" + y
                + " w=" + w + " h=" + h);
        Logger.appendTo("region.txt", "更新\t" + x + "\t" + y + "\t" + w + "\t" + h);
    }

    /** 恢复默认 */
    public static void reset() {
        cached = null;
        try {
            ContextHolder.get().getSharedPreferences(PREF, 0).edit().clear().apply();
        } catch (Throwable ignored) {}
        Logger.i("REGION", "识别区域已恢复默认");
    }

    /** 是否已自定义 */
    public static boolean isCustom() {
        int[] r = get();
        return !(r[0] == DEF_X && r[1] == DEF_Y && r[2] == DEF_W && r[3] == DEF_H);
    }

    public static String describe() {
        int[] r = get();
        return r[2] + "×" + r[3];
    }
}