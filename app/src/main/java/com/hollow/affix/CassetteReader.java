package com.hollow.affix;

import android.graphics.Bitmap;

import java.util.List;

/**
 * 空幕识别：从右侧详情面板的 OCR 结果里还原出
 *   套装名 / 主属性 / 4 条副属性
 *
 * 面板文本布局（实测 3168x1440）：
 *   ┌─────────────────────────┐
 *   │「卡带」            ╳ ⓘ │
 *   │  「迪亚波罗斯」          │  ← 套装名
 *   │         +0              │
 *   │ 主属性                   │  ← 段落标题
 *   │  ◎ 倾陷强度        36    │  ← 主属性名 + 数值
 *   │ 副属性                   │  ← 段落标题
 *   │  ▲ 伤害增加10.00%    +5  │  ← 4 条副属性
 *   │  ◆ 防御力增加80      +10 │
 *   │  ▲ 攻击力提升12.50%  +15 │
 *   │  ◆ 防御力提升17.50%  +20 │
 *   └─────────────────────────┘
 *
 * 识别策略：
 *   1. 用「主属性」「副属性」两个段落标题把面板切成上下两块
 *   2. 主属性块里取最像词条名的那一行
 *   3. 副属性块里连词条名带数值一起匹配
 *   4. 套装名在面板最上方，带书名号「」
 */
public class CassetteReader {

    /** 一次识别的完整结果 */
    public static class Parse {
        public String set = null;
        public String mainAffix = null;
        public String mainValue = "";
        public java.util.List<String> subs = new java.util.ArrayList<>();
        public java.util.List<String> subTexts = new java.util.ArrayList<>();
        /** 是否成功从标题切出主/副属性区 */
        public boolean located = false;
        public String debug = "";
    }

    private final CassetteRule rule;

    public CassetteReader(CassetteRule rule) {
        this.rule = rule != null ? rule : CassetteRule.defaultRule();
    }

    /**
     * 从 OCR 结果解析空幕详情。
     *
     * @param boxes      OCR 框（坐标是整图坐标）
     * @param region     识别区域 [x,y,w,h]，用于判断相对位置
     */
    public Parse parse(List<OcrEngine.Box> boxes, int[] region) {
        Parse p = new Parse();
        if (boxes == null || boxes.isEmpty()) {
            p.debug = "无文本";
            return p;
        }

        // 按 y 从上到下排序
        java.util.List<OcrEngine.Box> list = new java.util.ArrayList<>(boxes);
        java.util.Collections.sort(list, new java.util.Comparator<OcrEngine.Box>() {
            @Override public int compare(OcrEngine.Box a, OcrEngine.Box b) {
                return Integer.compare(a.y, b.y);
            }
        });

        int mainTitleY = -1, subTitleY = -1;
        for (OcrEngine.Box b : list) {
            String t = strip(b.text);
            if (mainTitleY < 0 && isMainTitle(t)) mainTitleY = b.y;
            else if (subTitleY < 0 && isSubTitle(t)) subTitleY = b.y;
        }
        p.located = mainTitleY >= 0;

        // ---- 1) 套装名：取面板顶部、带「」或能命中套装表的那一行 ----
        int topLimit = mainTitleY >= 0 ? mainTitleY : (region != null ? region[1] + region[3] / 3 : Integer.MAX_VALUE);
        for (OcrEngine.Box b : list) {
            if (b.y >= topLimit) break;
            String hit = rule.matchSet(b.text);
            if (hit != null) {
                p.set = hit;
                break;
            }
        }
        // 保险：整面板再找一次（有些面板套装名在词条下方）
        if (p.set == null) {
            for (OcrEngine.Box b : list) {
                String hit = rule.matchSet(b.text);
                if (hit != null) { p.set = hit; break; }
            }
        }

        // ---- 2) 主属性：主属性标题与副属性标题之间 ----
        int mainEnd = subTitleY >= 0 ? subTitleY : (mainTitleY >= 0 ? mainTitleY + 200 : -1);
        if (mainTitleY >= 0) {
            for (OcrEngine.Box b : list) {
                if (b.y <= mainTitleY || b.y >= mainEnd) continue;
                String hit = rule.matchMain(b.text);
                if (hit == null) continue;
                // 排除把「主属性」标题本身误判
                String t = strip(b.text);
                if (isMainTitle(t) || isSubTitle(t)) continue;
                p.mainAffix = hit;
                p.mainValue = extractNumber(b.text);
                break;
            }
        }

        // 主属性没找到（可能标题没识别出来），退化为全文搜索
        if (p.mainAffix == null) {
            for (OcrEngine.Box b : list) {
                String t = strip(b.text);
                if (isMainTitle(t) || isSubTitle(t)) continue;
                String hit = rule.matchMain(b.text);
                if (hit != null) {
                    p.mainAffix = hit;
                    p.mainValue = extractNumber(b.text);
                    break;
                }
            }
        }

        // ---- 3) 副属性：副属性标题以下 ----
        int subStart = subTitleY >= 0 ? subTitleY : (mainTitleY >= 0 ? mainTitleY + 120 : 0);
        for (OcrEngine.Box b : list) {
            if (b.y <= subStart) continue;
            String t = strip(b.text);
            if (t.isEmpty()) continue;
            if (isSubTitle(t) || isMainTitle(t)) continue;
            // 「+5 / +10」这类强化标记不是词条
            if (t.matches("^\\+\\d+$")) continue;
            // 与主属性同名的行跳过（避免重复计入）
            String hit = rule.matchSub(b.text);
            if (hit == null) continue;
            if (p.subs.contains(hit) && p.subs.size() >= 4) break;
            if (p.subs.size() >= 4) break;
            p.subs.add(hit);
            p.subTexts.add(AffixMatcher.pretty(AffixMatcher.cleanText(b.text)));
        }

        p.debug = "套装=" + p.set + " 主=" + p.mainAffix
                + " 副=" + p.subs + " 定位=" + p.located
                + " (mainY=" + mainTitleY + " subY=" + subTitleY + ")";
        return p;
    }

    /** 主属性段落标题 */
    private static boolean isMainTitle(String t) {
        return t.equals("主属性") || t.equals("主词条");
    }

    /** 副属性段落标题 */
    private static boolean isSubTitle(String t) {
        return t.equals("副属性") || t.equals("副词条");
    }

    private static String strip(String s) {
        if (s == null) return "";
        return s.replaceAll("[\\s\\u3000]", "")
                .replace("：", "").replace(":", "");
    }

    /** 抽取文本里的数字部分，如「倾陷强度36」→「36」 */
    private static String extractNumber(String s) {
        if (s == null) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\d+(?:\\.\\d+)?%?)").matcher(s);
        return m.find() ? m.group(1) : "";
    }

    /**
     * 完整流程：截取指定区域做 OCR，再解析。
     * 封装好方便 FloatWindow 直接调用。
     */
    public Parse read(OcrEngine ocr, Bitmap full, int[] region) {
        if (ocr == null || !ocr.isReady() || full == null) {
            Parse p = new Parse();
            p.debug = "OCR未就绪";
            return p;
        }
        int[] r = region != null ? region
                : new int[]{RegionStore.CAS_X, RegionStore.CAS_Y,
                            RegionStore.CAS_W, RegionStore.CAS_H};
        List<OcrEngine.Box> boxes = ocr.recognize(full, r);
        return parse(boxes, r);
    }
}