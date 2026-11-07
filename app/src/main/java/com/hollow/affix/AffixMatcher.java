package com.hollow.affix;

/**
 * 词条匹配：把 OCR 文本转成标准词条名。
 */
public class AffixMatcher {

    public static final String CR = "暴击率";
    public static final String CD = "暴击伤害";
    public static final String ATK_P = "攻击力百分比";
    public static final String DMG = "通用伤害增强";
    public static final String ATK = "攻击力";
    public static final String HP_P = "生命值百分比";
    public static final String DEF_P = "防御力百分比";
    public static final String HP = "生命值";
    public static final String DEF = "防御力";
    public static final String RING = "环合强度";
    public static final String SINK = "倾陷强度";

    /** 别名表：标准名 → 可能出现的写法 */
    private static final String[][] ALIAS = {
            {CR, "暴击率", "暴率", "暴击几率", "会心率"},
            {CD, "暴击伤害", "暴伤", "暴击伤害加成", "会心伤害"},
            {ATK_P, "攻击力百分比", "攻击百分比", "攻击力%", "攻击%"},
            {DMG, "通用伤害增强", "伤害增强", "通用增伤", "伤害加成", "通用伤害加成"},
            {ATK, "攻击力", "小攻击"},
            {HP_P, "生命值百分比", "生命百分比", "生命值%", "生命%"},
            {DEF_P, "防御力百分比", "防御百分比", "防御力%", "防御%"},
            {HP, "生命值", "小生命"},
            {DEF, "防御力", "小防御"},
            {RING, "环合强度", "环合"},
            {SINK, "倾陷强度", "倾陷"},
    };

    /**
     * 从 OCR 文本匹配标准词条名，失败返回 null。
     */
    public static String match(String text) {
        if (text == null) return null;
        String t = text.replaceAll("[\\s\\u3000]", "")
                .replace("：", "").replace(":", "")
                .replace("_", "").replace("—", "").replace("-", "")
                .replace("·", "").replace("•", "");

        if (t.isEmpty()) return null;

        // 百分比形态：% 或小数点
        boolean isPct = t.contains("%") || t.contains("％") || t.contains(".");

        if (isPct) {
            if (t.contains("攻击")) return ATK_P;
            if (t.contains("生命")) return HP_P;
            if (t.contains("防御")) return DEF_P;
        }

        // 最长别名匹配
        String best = null;
        int bestLen = 0;
        for (String[] row : ALIAS) {
            String std = row[0];
            for (int i = 1; i < row.length; i++) {
                String a = row[i];
                if (t.contains(a) && a.length() > bestLen) {
                    best = std;
                    bestLen = a.length();
                }
            }
        }
        if (best != null) return best;

        // 兜底：关键字模糊匹配
        if (t.contains("暴") && t.contains("率")) return CR;
        if (t.contains("暴") && t.contains("伤")) return CD;
        if (t.contains("爆") && t.contains("率")) return CR;
        if (t.contains("爆") && t.contains("伤")) return CD;
        if (t.contains("攻击")) return isPct ? ATK_P : ATK;
        if (t.contains("生命")) return isPct ? HP_P : HP;
        if (t.contains("防御")) return isPct ? DEF_P : DEF;
        if (t.contains("伤害") || t.contains("增伤")) return DMG;
        if (t.contains("环合")) return RING;
        if (t.contains("倾陷")) return SINK;

        return null;
    }

    /** 清理 OCR 误读：% 常被读成 9/g/q */
    public static String cleanText(String raw) {
        if (raw == null) return "";
        String t = raw;
        // 「4.0009」→「4.00%」，只在百分比形态下替换
        t = t.replaceAll("(\\.\\d{1,2})[9gq]$", "$1%");
        t = t.replaceAll("(\\.\\d{1,2})[9gq](?![0-9])", "$1%");

        StringBuilder sb = new StringBuilder();
        for (char c : t.toCharArray()) {
            if (c >= 0x4E00 && c <= 0x9FFF) sb.append(c);       // 中文
            else if (c >= '0' && c <= '9') sb.append(c);        // 数字
            else if (c == '.' || c == '%' || c == '+' || c == '-') sb.append(c);
        }
        return sb.toString();
    }

    /** 「暴击伤害提升8.00%」→「暴击伤害 8%」 */
    public static String pretty(String raw) {
        String t = raw.replace(" ", "");
        t = t.replace("提升", " ").replace("增加", " ")
             .replace("增强", " ").replace("提高", " ");
        t = t.replaceAll("(\\d+)\\.00%", "$1%");
        t = t.replaceAll("(\\d+\\.\\d)0*%", "$1%");
        return t.trim();
    }
}