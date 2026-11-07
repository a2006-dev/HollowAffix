package com.hollow.affix;

import android.graphics.Bitmap;

/**
 * 图像分析：选中态检测、底栏小锁存在性检测。
 * 基准：横屏 3168x1440。
 */
public class ImageAnalyzer {

    /** 灰度 */
    public static int gray(Bitmap bmp, int x, int y) {
        if (x < 0 || y < 0 || x >= bmp.getWidth() || y >= bmp.getHeight()) return 0;
        int p = bmp.getPixel(x, y);
        int r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
        return (int) (0.299 * r + 0.587 * g + 0.114 * b);
    }

    /**
     * 卡片左右边框亮度峰值，取左右较小值。
     * 选中卡片有一圈亮描边，左右两侧会同时变亮；
     * 相邻卡片共用描边时只有单侧亮，取 min 可排除。
     */
    public static int borderPeak(Bitmap bmp, int cx, int cy) {
        int y0 = cy - 55, y1 = cy + 55;
        if (y0 < 0) y0 = 0;
        if (y1 > bmp.getHeight()) y1 = bmp.getHeight();
        int li = cx - Coord.SEL_DX_OUTER, lo = cx - Coord.SEL_DX_INNER;
        int ri = cx + Coord.SEL_DX_INNER, ro = cx + Coord.SEL_DX_OUTER;
        if (li < 0 || ro >= bmp.getWidth()) return 0;

        int left = 0;
        for (int x = li; x < lo; x++)
            for (int y = y0; y < y1; y++) {
                int v = gray(bmp, x, y);
                if (v > left) left = v;
            }

        int right = 0;
        for (int x = ri; x <= ro; x++)
            for (int y = y0; y < y1; y++) {
                int v = gray(bmp, x, y);
                if (v > right) right = v;
            }

        return Math.min(left, right);
    }

    /** 全网格扫描选中态 */
    public static SelResult scanSelected(Bitmap bmp) {
        int[][] peaks = new int[Coord.ROWS][Coord.COLS];
        int[] all = new int[Coord.ROWS * Coord.COLS];
        int k = 0;
        for (int r = 1; r <= Coord.ROWS; r++)
            for (int c = 1; c <= Coord.COLS; c++) {
                int[] p = Coord.cellCenter(r, c);
                int v = borderPeak(bmp, p[0], p[1]);
                peaks[r - 1][c - 1] = v;
                all[k++] = v;
            }
        int base = median(all);
        int thr = Math.max((int) (base * 1.18f), base + 18);

        SelResult res = new SelResult();
        res.peaks = peaks;
        res.base = base;
        res.thr = thr;
        res.mask = new boolean[Coord.ROWS][Coord.COLS];
        for (int r = 0; r < Coord.ROWS; r++)
            for (int c = 0; c < Coord.COLS; c++)
                res.mask[r][c] = peaks[r][c] > thr;
        return res;
    }

    /**
     * 底栏小锁图标是否存在。
     * 实测：小锁在 (列中心+14, 484)，宽 19 高 26，纯白色。
     */
    public static boolean lockPresent(Bitmap bmp) {
        int[] p = Coord.lockPoint(1);
        int cx = p[0], cy = p[1];
        int hw = Coord.LOCK_WIN_W / 2, hh = Coord.LOCK_WIN_H / 2;
        int x0 = cx - hw, y0 = cy - hh;
        int x1 = cx + hw, y1 = cy + hh;
        if (x0 < 0 || y0 < 0 || x1 >= bmp.getWidth() || y1 >= bmp.getHeight())
            return false;

        int bright = 0, total = 0;
        for (int y = y0; y < y1; y++)
            for (int x = x0; x < x1; x++) {
                total++;
                if (gray(bmp, x, y) > 190) bright++;
            }
        // 挂锁图标约占窗口 18%~55% 的亮像素
        float ratio = bright / (float) total;
        return ratio > 0.10f && ratio < 0.70f;
    }

    public static int median(int[] arr) {
        int[] a = arr.clone();
        java.util.Arrays.sort(a);
        return a[a.length / 2];
    }

    public static class SelResult {
        public boolean[][] mask;
        public int[][] peaks;
        public int base;
        public int thr;

        public int[] first() {
            for (int r = 0; r < Coord.ROWS; r++)
                for (int c = 0; c < Coord.COLS; c++)
                    if (mask[r][c]) return new int[]{r + 1, c + 1};
            return null;
        }

        public int count() {
            int n = 0;
            for (int r = 0; r < Coord.ROWS; r++)
                for (int c = 0; c < Coord.COLS; c++) if (mask[r][c]) n++;
            return n;
        }
    }
}