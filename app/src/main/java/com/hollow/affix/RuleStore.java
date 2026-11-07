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

/**
 * 规则存储：管理外部规则文件，支持导入导出。
 *
 * 目录结构：
 *   /sdcard/HollowAffix/rules/
 *     general_我的规则.json      驱动块规则
 *     cassette_空幕规则.json     空幕规则
 *
 * 加载优先级：外部文件 > assets 内置。
 */
public class RuleStore {

    public static final int TYPE_DRIVE = 0;    // 驱动块
    public static final int TYPE_CASSETTE = 1; // 空幕

    private static final String PREF = "rulestore";
    private static final String K_CUR_DRIVE = "cur_drive";
    private static final String K_CUR_CASSETTE = "cur_cassette";

    private static final String PREFIX_DRIVE = "general_";
    private static final String PREFIX_CASSETTE = "cassette_";

    /** 规则目录 */
    public static File dir() {
        File d = new File(Logger.DIR, "rules");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /** 一项规则文件 */
    public static class Entry {
        public File file;
        public String name = "";
        public int version = 1;
        public boolean valid = true;
        public String error = "";
        /** 是否内置（assets 里的） */
        public boolean builtin = false;

        public String display() {
            return name + "  v" + version + (builtin ? "  内置" : "");
        }
    }

    // ==================== 扫描 ====================

    /** 列出某类型的所有外部规则文件 */
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
                } else {
                    CassetteRule cr = CassetteRule.parse(json);
                    e.name = cr.name;
                    e.version = cr.version;
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

    /** 内置规则条目（虚拟，不对应文件） */
    public static Entry builtinEntry(int type) {
        Entry e = new Entry();
        e.builtin = true;
        if (type == TYPE_DRIVE) {
            RuleSet rs = RuleSet.defaultSet();
            e.name = rs.name;
            e.version = rs.version;
        } else {
            CassetteRule cr = CassetteRule.defaultRule();
            e.name = cr.name;
            e.version = cr.version;
        }
        return e;
    }

    // ==================== 当前规则 ====================

    /** 当前规则文件名；空字符串表示用内置 */
    public static String currentRule(int type) {
        try {
            SharedPreferences sp = ContextHolder.get().getSharedPreferences(PREF, 0);
            return sp.getString(type == TYPE_DRIVE ? K_CUR_DRIVE : K_CUR_CASSETTE, "");
        } catch (Throwable e) {
            return "";
        }
    }

    /** 设置当前规则；传空字符串表示切换回内置 */
    public static void setCurrent(int type, String fileName) {
        try {
            SharedPreferences sp = ContextHolder.get().getSharedPreferences(PREF, 0);
            sp.edit().putString(type == TYPE_DRIVE ? K_CUR_DRIVE : K_CUR_CASSETTE,
                    fileName == null ? "" : fileName).apply();
        } catch (Throwable ignored) {}
    }

    // ==================== 加载 ====================

    /**
     * 加载当前规则的 JSON 文本。
     *
     * 逻辑只有一条：
     *   当前规则为空 → 用内置
     *   当前规则有值 → 读该文件；文件不存在则回退内置并清空记录
     */
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
            // 文件没了，清空记录回退内置
            setCurrent(type, "");
        }
        return loadBuiltinJson(ctx, type);
    }

    /** 读 assets 里的内置规则 */
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

    // ==================== 保存 / 导入 / 导出 ====================

    /** 保存规则文本到外部文件，返回文件名 */
    public static String save(Context ctx, int type, String name, String json) throws Exception {
        String safe = safeName(name);
        String prefix = type == TYPE_DRIVE ? PREFIX_DRIVE : PREFIX_CASSETTE;
        String fileName = prefix + safe + ".json";
        File f = new File(dir(), fileName);
        writeFile(f, json);
        return fileName;
    }

    /** 删除规则文件 */
    public static boolean delete(String fileName) {
        if (fileName == null || fileName.isEmpty()) return false;
        File f = new File(dir(), fileName);
        return f.exists() && f.delete();
    }

    /** 导出到指定路径 */
    public static boolean exportTo(String json, File dest) {
        try {
            writeFile(dest, json);
            return true;
        } catch (Throwable e) {
            Logger.appendTo("trace.txt", "导出失败: " + e);
            return false;
        }
    }

    /** 从文件读取 JSON 文本 */
    public static String readJsonFile(File f) throws Exception {
        return readFile(f);
    }

    // ==================== 文件工具 ====================

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

    /** 文件名安全化：去掉非法字符 */
    public static String safeName(String name) {
        if (name == null || name.trim().isEmpty()) return "未命名";
        return name.trim().replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
    }
}