package com.hollow.affix;

import org.json.JSONArray;
import org.json.JSONObject;

import android.content.Context;
import android.view.WindowManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class AutoPoints {

    public static final int CELLS_PER_PAGE = 21;

    public static class Point {
        public int x, y;
        public Point(int x, int y) { this.x = x; this.y = y; }
    }

    public final List<Point> cells = new ArrayList<>();

    public int swipeX1, swipeY1, swipeX2, swipeY2;
    public int swipeDuration = 800;
    public boolean hasSwipe = false;

    public int swipeSegments = 1;

    public int totalPages = 0;

    private static final String FILE = "autopoints.json";

    public boolean hasCells() {
        return !cells.isEmpty();
    }

    public boolean ready() {
        return hasCells() && hasSwipe;
    }

    public int count() {
        return cells.size();
    }

    public int[] swipeForPage() {
        int h = screenHeight();
        int y1 = swipeY1, y2 = swipeY2;
        if (y1 > y2) { int t = y1; y1 = y2; y2 = t; }
        if (h > 0) {
            y1 = Math.max(0, Math.min(y1, h - 1));
            y2 = Math.max(0, Math.min(y2, h - 1));
        }

        if (y2 - y1 < 50 && h > 0) {
            y1 = (int) (h * 0.15f);
            y2 = (int) (h * 0.85f);
            Logger.appendTo("trace.txt", "滑动距离过小(" + (y2 - y1)
                    + "px)，已重置为 15%~85%");
        }
        return new int[]{swipeX1, y1, swipeX2, y2};
    }

    public int swipeDistance() {
        if (!hasSwipe) return 0;
        return Math.abs(swipeY2 - swipeY1);
    }

    private static int screenHeight() {
        try {
            Context c = ContextHolder.get();
            if (c == null) return 0;
            android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
            WindowManager wm = (WindowManager)
                    c.getSystemService(Context.WINDOW_SERVICE);
            if (wm == null || wm.getDefaultDisplay() == null) return 0;
            wm.getDefaultDisplay().getRealMetrics(dm);
            return Math.min(dm.widthPixels, dm.heightPixels);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    public static File file() {
        return new File(Logger.DIR, FILE);
    }

    public static AutoPoints load() {
        AutoPoints ap = new AutoPoints();
        File f = file();
        if (!f.exists()) return ap;
        try {
            String json = RuleStore.readJsonFile(f);
            JSONObject o = new JSONObject(json);

            JSONArray arr = o.optJSONArray("cells");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONArray p = arr.getJSONArray(i);
                    ap.cells.add(new Point(p.getInt(0), p.getInt(1)));
                }
            }

            JSONObject sw = o.optJSONObject("swipe");
            if (sw != null) {
                ap.swipeX1 = sw.optInt("x1");
                ap.swipeY1 = sw.optInt("y1");
                ap.swipeX2 = sw.optInt("x2");
                ap.swipeY2 = sw.optInt("y2");
                ap.swipeDuration = sw.optInt("duration", 800);
                ap.swipeSegments = Math.max(1, sw.optInt("segments", 1));
                ap.hasSwipe = true;
            }

            ap.totalPages = o.optInt("totalPages", 0);
        } catch (Throwable e) {
            Logger.appendTo("trace.txt", "自动点位读取失败: " + e);
        }
        return ap;
    }

    public boolean save() {
        try {
            JSONObject o = new JSONObject();

            JSONArray arr = new JSONArray();
            for (Point p : cells) {
                JSONArray one = new JSONArray();
                one.put(p.x);
                one.put(p.y);
                arr.put(one);
            }
            o.put("cells", arr);

            if (hasSwipe) {
                JSONObject sw = new JSONObject();
                sw.put("x1", swipeX1);
                sw.put("y1", swipeY1);
                sw.put("x2", swipeX2);
                sw.put("y2", swipeY2);
                sw.put("duration", swipeDuration);
                sw.put("segments", swipeSegments);
                o.put("swipe", sw);
            }

            o.put("totalPages", totalPages);

            return RuleStore.exportTo(o.toString(2), file());
        } catch (Throwable e) {
            Logger.appendTo("trace.txt", "自动点位保存失败: " + e);
            return false;
        }
    }

    public void clear() {
        cells.clear();
        hasSwipe = false;
        try {
            File f = file();
            if (f.exists()) f.delete();
        } catch (Throwable ignored) {}
    }

    public String summary() {
        if (!hasCells() && !hasSwipe) return "未配置";
        StringBuilder sb = new StringBuilder();
        sb.append(hasCells() ? cells.size() + " 个点位" : "未录点位");
        if (hasSwipe) sb.append(" · 滑动 ").append(Math.abs(swipeY2 - swipeY1)).append("px");
        if (totalPages > 0) sb.append(" · 共 ").append(totalPages).append(" 页");
        return sb.toString();
    }
}
