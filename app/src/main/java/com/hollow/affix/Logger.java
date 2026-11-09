package com.hollow.affix;

import android.graphics.Bitmap;
import android.os.Environment;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Logger {

    public static final File DIR = resolveDir();

    private static File resolveDir() {
        File primary = new File(Environment.getExternalStorageDirectory(), "HollowAffix");
        try {
            if (primary.exists() || primary.mkdirs()) {

                File probe = new File(primary, ".w");
                if (probe.createNewFile() || probe.exists()) {
                    probe.delete();
                    return primary;
                }
            }
        } catch (Throwable ignored) {
        }

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

    private static final java.util.Map<String, StringBuilder> pending =
            new java.util.HashMap<>();

    public static synchronized void appendTo(String fileName, String content) {
        android.util.Log.i("PROBE_" + fileName.replace(".txt", ""), content);
        StringBuilder sb = pending.get(fileName);
        if (sb == null) {
            sb = new StringBuilder();
            pending.put(fileName, sb);
        }
        sb.append(content);
        if (!content.endsWith("\n")) sb.append('\n');

        if (sb.length() > 256 * 1024) flush(fileName);
    }

    public static synchronized void flushAll() {
        for (String name : new java.util.ArrayList<>(pending.keySet())) {
            flush(name);
        }
    }

    public static synchronized void flush(String fileName) {
        StringBuilder sb = pending.get(fileName);
        if (sb == null || sb.length() == 0) return;
        String data = sb.toString();
        sb.setLength(0);
        try {
            File f = new File(DIR, fileName);
            boolean isNew = !f.exists();
            FileWriter fw = new FileWriter(f, true);
            if (isNew) fw.write("# 空幕探测数据 " + FULL.format(new Date()) + "\n");
            fw.write(data);
            fw.close();
        } catch (Exception e) {
            android.util.Log.e("PROBE", "写入 " + fileName + " 失败: " + e, e);
        }
    }

    public static boolean saveDebug(Bitmap src, int[] region, String tag) {
        try {
            File dir = new File(DIR, "debug");
            if (!dir.exists()) dir.mkdirs();

            markNoMedia(dir);

            File full = new File(dir, tag + "_full.jpg");
            int fw = src.getWidth() / 2, fh = src.getHeight() / 2;
            Bitmap half = Bitmap.createScaledBitmap(src, fw, fh, true);
            java.io.FileOutputStream fos = new java.io.FileOutputStream(full);
            half.compress(Bitmap.CompressFormat.JPEG, 85, fos);
            fos.close();
            half.recycle();

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

    public static void markNoMedia(File dir) {
        try {
            if (dir == null) return;
            if (!dir.exists()) dir.mkdirs();
            File nm = new File(dir, ".nomedia");
            if (!nm.exists()) {
                nm.createNewFile();
                Logger.appendTo("trace.txt", "已添加 .nomedia 到 " + dir.getName());
            }
        } catch (Throwable ignored) {}
    }

    public static boolean hasNoMedia(File dir) {
        return dir != null && new File(dir, ".nomedia").exists();
    }

    public static void cleanup() {
        try {
            markNoMedia(DIR);
            markNoMedia(new File(DIR, "debug"));

            long now = System.currentTimeMillis();
            final long WEEK = 7L * 24 * 3600 * 1000;
            final long DAY = 24L * 3600 * 1000;
            final long MAX_LOG = 5L * 1024 * 1024;

            File[] files = DIR.listFiles();
            if (files == null) return;

            int deletedSession = 0, truncated = 0, deletedShot = 0;

            for (File f : files) {
                if (f.isDirectory()) {

                    if ("debug".equals(f.getName())) {
                        File[] shots = f.listFiles();
                        if (shots != null) {
                            for (File s : shots) {
                                if (s.getName().startsWith(".")) continue;
                                if (now - s.lastModified() > DAY) {
                                    if (s.delete()) deletedShot++;
                                }
                            }
                        }
                    }
                    continue;
                }

                String name = f.getName();

                if (name.startsWith("session_") && name.endsWith(".txt")) {
                    if (now - f.lastModified() > WEEK) {
                        if (f.delete()) deletedSession++;
                    }
                    continue;
                }

                if (name.endsWith(".txt") && f.length() > MAX_LOG) {
                    if (truncateTail(f, 200 * 1024)) truncated++;
                }
            }

            if (deletedSession > 0 || truncated > 0 || deletedShot > 0) {
                String msg = "日志清理：删除旧 session " + deletedSession
                        + " 个，截断大日志 " + truncated
                        + " 个，删除旧截图 " + deletedShot + " 张";
                Logger.i("CLEANUP", msg);
                Logger.appendTo("trace.txt", msg);
            }
        } catch (Throwable e) {
            android.util.Log.e("PROBE", "清理失败: " + e, e);
        }
    }

    private static boolean truncateTail(File f, int keepBytes) {
        try {
            long len = f.length();
            if (len <= keepBytes) return false;

            java.io.RandomAccessFile raf = new java.io.RandomAccessFile(f, "rw");
            raf.seek(len - keepBytes);
            byte[] buf = new byte[keepBytes];
            int read = raf.read(buf);
            raf.setLength(0);
            raf.seek(0);
            if (read > 0) {

                int start = 0;
                for (int i = 0; i < read - 1; i++) {
                    if (buf[i] == '\n') { start = i + 1; break; }
                }
                raf.write(buf, start, read - start);
            }
            raf.close();
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    public static void purgeGalleryShots(android.content.Context ctx) {
        try {
            File dir = new File(DIR, "debug");
            File[] shots = dir.listFiles();
            if (shots == null || shots.length == 0) return;

            java.util.List<String> paths = new java.util.ArrayList<>();
            for (File s : shots) {
                String n = s.getName().toLowerCase();
                if (n.endsWith(".jpg") || n.endsWith(".png")) {
                    if (s.delete()) paths.add(s.getAbsolutePath());
                }
            }
            if (paths.isEmpty()) return;

            android.media.MediaScannerConnection.scanFile(
                    ctx, paths.toArray(new String[0]), null, null);

            Logger.appendTo("trace.txt", "已清理相册中的调试截图 " + paths.size() + " 张");
        } catch (Throwable ignored) {}
    }

    public static String dirPath() { return DIR.getAbsolutePath(); }
}
