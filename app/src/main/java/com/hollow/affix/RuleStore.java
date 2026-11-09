package com.hollow.affix;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

public class RuleStore {

    public static final int TYPE_DRIVE = 0;
    public static final int TYPE_CASSETTE = 1;

    private static final String PREF = "rulestore";
    private static final String K_CUR_DRIVE = "cur_drive";
    private static final String K_CUR_CASSETTE = "cur_cassette";

    private static final String PREFIX_DRIVE = "general_";
    private static final String PREFIX_CASSETTE = "cassette_";

    public static File dir() {
        File d = new File(Logger.DIR, "rules");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public static class Entry {
        public File file;
        public String name = "";
        public int version = 1;
        public boolean valid = true;
        public String error = "";

        public boolean builtin = false;

        public String target = "";

        public String display() {
            return name + "  v" + version + (builtin ? "  内置" : "");
        }
    }

    public static List<Entry> list(int type) {
        List<Entry> out = new ArrayList<>();
        String prefix = type == TYPE_DRIVE ? PREFIX_DRIVE : PREFIX_CASSETTE;
        File[] files = dir().listFiles();
        if (files == null) return out;

        for (File f : files) {
            String fn = f.getName();
            if (!fn.endsWith(".json")) continue;
            if (!fn.startsWith(prefix)) continue;
            Entry e = new Entry();
            e.file = f;
            try {
                String json = readFile(f);
                if (type == TYPE_DRIVE) {
                    RuleSet rs = RuleSet.parse(json);
                    e.name = rs.name;
                    e.version = rs.version;
                    e.target = rs.target;
                } else {
                    CassetteRule cr = CassetteRule.parse(json);
                    e.name = cr.name;
                    e.version = cr.version;
                    e.target = cr.target;
                }
            } catch (Throwable t) {
                e.valid = false;
                e.error = "解析失败: " + t.getMessage();
                e.name = fn.substring(prefix.length(), fn.length() - 5);
            }
            out.add(e);
        }
        return out;
    }

    public static Entry builtinEntry(int type) {
        Entry e = new Entry();
        e.builtin = true;
        if (type == TYPE_DRIVE) {
            RuleSet rs = RuleSet.defaultSet();
            e.name = rs.name;
            e.version = rs.version;
            e.target = rs.target;
        } else {
            CassetteRule cr = CassetteRule.defaultRule();
            e.name = cr.name;
            e.version = cr.version;
            e.target = cr.target;
        }
        return e;
    }

    public static String currentRule(int type) {
        try {
            SharedPreferences sp = ContextHolder.get().getSharedPreferences(PREF, 0);
            return sp.getString(type == TYPE_DRIVE ? K_CUR_DRIVE : K_CUR_CASSETTE, "");
        } catch (Throwable e) {
            return "";
        }
    }

    public static void setCurrent(int type, String fileName) {
        try {
            SharedPreferences sp = ContextHolder.get().getSharedPreferences(PREF, 0);
            sp.edit().putString(type == TYPE_DRIVE ? K_CUR_DRIVE : K_CUR_CASSETTE,
                    fileName == null ? "" : fileName).apply();
        } catch (Throwable ignored) {}
    }

    public static String loadJson(Context ctx, int type) {
        String cur = currentRule(type);
        if (!cur.isEmpty()) {
            File f = new File(dir(), cur);
            if (f.exists()) {
                try {
                    return readFile(f);
                } catch (Throwable e) {
                    Logger.appendTo("trace.txt", "规则读取失败 " + cur + ": " + e);
                }
            }

            setCurrent(type, "");
        }
        return loadBuiltinJson(ctx, type);
    }

    private static String loadBuiltinJson(Context ctx, int type) {
        try {
            String asset = type == TYPE_DRIVE ? "rules/general.json" : "rules/cassette.json";
            InputStream is = ctx.getAssets().open(asset);
            java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            br.close();
            return sb.toString();
        } catch (Throwable e) {
            Logger.appendTo("trace.txt", "内置规则读取失败: " + e);
            return null;
        }
    }

    public static String save(Context ctx, int type, String name, String json) throws Exception {
        String safe = safeName(name);
        String prefix = type == TYPE_DRIVE ? PREFIX_DRIVE : PREFIX_CASSETTE;
        String fileName = prefix + safe + ".json";
        File f = new File(dir(), fileName);
        writeFile(f, json);
        return fileName;
    }

    public static boolean delete(String fileName) {
        if (fileName == null || fileName.isEmpty()) return false;
        File f = new File(dir(), fileName);
        return f.exists() && f.delete();
    }

    public static boolean exportTo(String json, File dest) {
        try {
            writeFile(dest, json);
            return true;
        } catch (Throwable e) {
            Logger.appendTo("trace.txt", "导出失败: " + e);
            return false;
        }
    }

    public static String readJsonFile(File f) throws Exception {
        return readFile(f);
    }

    private static String readFile(File f) throws Exception {
        FileInputStream fis = new FileInputStream(f);
        byte[] buf = new byte[(int) f.length()];
        int off = 0;
        while (off < buf.length) {
            int n = fis.read(buf, off, buf.length - off);
            if (n < 0) break;
            off += n;
        }
        fis.close();
        return new String(buf, 0, off, Charset.forName("UTF-8"));
    }

    private static void writeFile(File f, String content) throws Exception {
        File parent = f.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        FileOutputStream fos = new FileOutputStream(f);
        fos.write(content.getBytes(Charset.forName("UTF-8")));
        fos.flush();
        fos.close();
    }

    public static String safeName(String name) {
        if (name == null || name.trim().isEmpty()) return "未命名";
        return name.trim().replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
    }
}
