package com.hollow.affix;

/**
 * 界面坐标常量。基准：横屏 3168 x 1440（实机截图 cv2 测量）。
 *
 * 注意：游戏是横屏，AccessibilityService.takeScreenshot() 返回
 * 3168x1440，不做任何旋转。
 */
public class Coord {

    public static final int BASE_W = 3168;
    public static final int BASE_H = 1440;

    // ===== 左侧驱动块网格 =====
    // 实测卡片边界：330~432 / 572~674 / 815~917 / 1058~1160
    //               1300~1402 / 1543~1645 / 1786~1888
    // 列中心 381 + (c-1)*242.7，卡片宽 102
    // 行：291~481 / 577~766 / 862~1052，行中心 386 / 672 / 957
    public static final float X0 = 381f;
    public static final float DX = 242.7f;
    public static final float Y0 = 386f;
    public static final float DY = 285.5f;
    public static final int CARD_W = 102;
    public static final int CARD_H = 190;
    public static final int COLS = 7;
    public static final int ROWS = 3;

    // ===== 底栏按钮（每列两个）=====
    // 实测 y 中心 484；垃圾桶 = 列中心-9（宽7），小锁 = 列中心+14（宽19）
    public static final int BTN_CY = 484;
    public static final int TRASH_DX = -9;
    public static final int LOCK_DX = 14;

    // ===== 选中态检测 =====
    // 卡片宽 102，左右边框在 cx±51
    public static final int SEL_DX_INNER = 47;
    public static final int SEL_DX_OUTER = 57;

    // ===== 底栏小锁存在性检测窗口 =====
    // 以第1列小锁为中心，±16 x ±14
    public static final int LOCK_WIN_W = 34;
    public static final int LOCK_WIN_H = 30;

    // ===== 右侧属性卡片（默认识别区域）=====
    public static final int PANEL_X = 2141;
    public static final int PANEL_Y = 621;
    public static final int PANEL_W = 762;
    public static final int PANEL_H = 742;

    public static int colCenter(int c) {          // 1-based
        return Math.round(X0 + (c - 1) * DX);
    }

    public static int rowCenter(int r) {          // 1-based
        return Math.round(Y0 + (r - 1) * DY);
    }

    public static int[] cellCenter(int r, int c) {
        return new int[]{colCenter(c), rowCenter(r)};
    }

    public static int[] trashPoint(int c) {
        return new int[]{colCenter(c) + TRASH_DX, BTN_CY};
    }

    public static int[] lockPoint(int c) {
        return new int[]{colCenter(c) + LOCK_DX, BTN_CY};
    }

    public static int[] panelRegion() {
        return new int[]{PANEL_X, PANEL_Y, PANEL_W, PANEL_H};
    }
}