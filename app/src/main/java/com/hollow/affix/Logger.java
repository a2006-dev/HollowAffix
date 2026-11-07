package com.hollow.affix;

import android.graphics.Bitmap;
import android.os.Environment;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 统一日志与数据导出。全部落到 /sdcard/HollowAffix/，纯文本，方便直接读取。
 */
public class Logger {

    // 优先用 /sdcard/HollowAffix（有「所有文件访问」权限时最方便读取）
    // 没有权限时退回 App 私有外部目录，保证一定能写
    public static final File DIR = resolveDir();

    private static File resolveDir() {
        File primary = new File(Environment.getExternalStorageDirectory(), "HollowAffix");
        try {
            if (primary.exists() || primary.mkdirs()) {
                // 验证真的可写
                File probe = new File(primary, ".w");
                if (probe.createNewFile() || probe.exists()) {
                    probe.delete();
                    return primary;
                }
            }
        } catch (Throwable ignored) {
        }
        // 兜底：App 外部私有目录，无需任何权限
        File fallback = null;
        try {
            File ext = ContextHolder.get().getExternalFilesDir(null);
            if (ext != null) fallback = new File(ext, "HollowAffix");
        } catch (Throwable ignored) {
        }
        if (fallback == null) fallback = primary;
        try { fallback.mkdirs(); } catch (Throwable ignored) {}
        return fallback;
    }

    private static final SimpleDateFormat TIME =
            new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);
    private static final SimpleDateFormat FULL =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);

    private static File sessionFile;
    private static final StringBuilder memLog = new StringBuilder();

    static {
        if (!DIR.exists()) DIR.mkdirs();
    }

    public static void initSession() {
        String name = "session_" + new SimpleDateFormat("MMdd_HHmmss", Locale.US)
                .format(new Date()) + ".txt";
        sessionFile = new File(DIR, name);
        header();
    }

    private static void header() {
        StringBuilder sb = new StringBuilder();
        sb.append("# 空幕探测 会话日志\n");
        sb.append("# 开始: ").append(FULL.format(new Date())).append("\n");
        sb.append("# 设备: ").append(android.os.Build.MODEL)
          .append(" / Android ").append(android.os.Build.VERSION.RELEASE)
          .append(" (SDK ").append(android.os.Build.VERSION.SDK_INT).append(")\n");
        sb.append("# ------------------------------------------------------------\n");
        writeRaw(sb.toString());
    }

    public static void i(String tag, String msg) { add("I", tag, msg); }
    public static void w(String tag, String msg) { add("W", tag, msg); }
    public static void e(String tag, String msg) { add("E", tag, msg); }
    public static void d(String tag, String msg) { add("D", tag, msg); }

    private static synchronized void add(String lv, String tag, String msg) {
        String line = TIME.format(new Date()) + " [" + lv + "] " + tag + ": " + msg;
        memLog.append(line).append('\n');
        if (memLog.length() > 200000) {
            memLog.delete(0, 100000);
        }
        writeRaw(line + "\n");
        android.util.Log.println(
                lv.equals("E") ? android.util.Log.ERROR :
                lv.equals("W") ? android.util.Log.WARN : android.util.Log.INFO,
                tag, msg);
    }

    private static void writeRaw(String s) {
        if (sessionFile == null) return;
        try {
            FileWriter fw = new FileWriter(sessionFile, true);
            fw.write(s);
            fw.close();
        } catch (Exception ignored) {}
    }

    public static String getMemLog() { return memLog.toString(); }

    /** 追加写入指定数据文件（同时打 logcat，方便文件写不进去时排查） */
    public static synchronized void appendTo(String fileName, String content) {
        android.util.Log.i("PROBE_" + fileName.replace(".txt", ""), content);
        try {
            File f = new File(DIR, fileName);
            boolean isNew = !f.exists();
            FileWriter fw = new FileWriter(f, true);
            if (isNew) {
                fw.write("# 空幕探测数据 " + FULL.format(new Date()) + "\n");
            }
            fw.write(content);
            if (!content.endsWith("\n")) fw.write("\n");
            fw.close();
        } catch (Exception e) {
            android.util.Log.e("PROBE", "写入 " + fileName + " 失败: " + e, e);
        }
    }

    /** 保存调试截图（区域裁剪预览 + 整图），用于核对坐标。 */
    public static boolean saveDebug(Bitmap src, int[] region, String tag) {
        try {
            File dir = new File(DIR, "debug");
            if (!dir.exists()) dir.mkdirs();

            // 整图（缩放一半，省空间）
            File full = new File(dir, tag + "_full.jpg");
            int fw = src.getWidth() / 2, fh = src.getHeight() / 2;
            Bitmap half = Bitmap.createScaledBitmap(src, fw, fh, true);
            java.io.FileOutputStream fos = new java.io.FileOutputStream(full);
            half.compress(Bitmap.CompressFormat.JPEG, 85, fos);
            fos.close();
            half.recycle();

            // 裁剪区域 + 红框标注
            if (region != null) {
                int rx = Math.max(0, region[0]), ry = Math.max(0, region[1]);
                int rw = Math.min(region[2], src.getWidth() - rx);
                int rh = Math.min(region[3], src.getHeight() - ry);
                if (rw > 0 && rh > 0) {
                    Bitmap crop = Bitmap.createBitmap(src, rx, ry, rw, rh)
                            .copy(Bitmap.Config.ARGB_8888, true);
                    new android.graphics.Canvas(crop).drawRect(
                            1, 1, rw - 2, rh - 2, redStroke());
                    File cf = new File(dir, tag + "_crop.jpg");
                    java.io.FileOutputStream cos = new java.io.FileOutputStream(cf);
                    crop.compress(Bitmap.CompressFormat.JPEG, 90, cos);
                    cos.close();
                    crop.recycle();

                    // 整图 + 红框
                    Bitmap marked = src.copy(Bitmap.Config.ARGB_8888, true);
                    new android.graphics.Canvas(marked).drawRect(
                            rx, ry, rx + rw, ry + rh, redStroke());
                    Bitmap mh = Bitmap.createScaledBitmap(marked,
                            marked.getWidth() / 2, marked.getHeight() / 2, true);
                    File mf = new File(dir, tag + "_region.jpg");
                    java.io.FileOutputStream mos = new java.io.FileOutputStream(mf);
                    mh.compress(Bitmap.CompressFormat.JPEG, 85, mos);
                    mos.close();
                    mh.recycle();
                    marked.recycle();
                }
            }
            Logger.i("DEBUG", "已保存 " + tag + " 到 " + dir.getAbsolutePath());
            return true;
        } catch (Throwable e) {
            Logger.e("DEBUG", "保存失败: " + e);
            return false;
        }
    }

    private static android.graphics.Paint redStroke() {
        android.graphics.Paint p = new android.graphics.Paint();
        p.setColor(android.graphics.Color.RED);
        p.setStyle(android.graphics.Paint.Style.STROKE);
        p.setStrokeWidth(6f);
        p.setAntiAlias(true);
        return p;
    }

    public static void clearAll() {
        File[] fs = DIR.listFiles();
        if (fs != null) for (File f : fs) {
            if (f.getName().startsWith("frame_")) f.delete();
        }
    }

    public static String dirPath() { return DIR.getAbsolutePath(); }
}