package com.hollow.affix;

import android.graphics.Bitmap;

import java.util.List;

public class CassetteReader {

    public static class Parse {
        public String set = null;
        public String mainAffix = null;
        public String mainValue = "";
        public java.util.List<String> subs = new java.util.ArrayList<>();
        public java.util.List<String> subTexts = new java.util.ArrayList<>();

        public boolean located = false;
        public String debug = "";
    }

    private final CassetteRule rule;

    public CassetteReader(CassetteRule rule) {
        this.rule = rule != null ? rule : CassetteRule.defaultRule();
    }

    public Parse parse(List<OcrEngine.Box> boxes, int[] region) {
        Parse p = new Parse();
        if (boxes == null || boxes.isEmpty()) {
            p.debug = "无文本";
            return p;
        }

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

        int topLimit = mainTitleY >= 0 ? mainTitleY : (region != null ? region[1] + region[3] / 3 : Integer.MAX_VALUE);
        for (OcrEngine.Box b : list) {
            if (b.y >= topLimit) break;
            String hit = rule.matchSet(b.text);
            if (hit != null) {
                p.set = hit;
                break;
            }
        }

        if (p.set == null) {
            for (OcrEngine.Box b : list) {
                String hit = rule.matchSet(b.text);
                if (hit != null) { p.set = hit; break; }
            }
        }

        if (mainTitleY >= 0) {
            int mainEnd = subTitleY >= 0 ? subTitleY : Integer.MAX_VALUE;

            int lineHeight = Math.max(24, mainTitleY / 10);

            OcrEngine.Box bestBox = null;
            for (OcrEngine.Box b : list) {
                if (b.y <= mainTitleY + lineHeight) continue;
                if (b.y >= mainEnd) continue;
                String t = strip(b.text);
                if (t.isEmpty()) continue;
                if (isMainTitle(t) || isSubTitle(t)) continue;
                if (rule.matchMain(b.text) == null) continue;

                if (bestBox == null || b.y < bestBox.y) bestBox = b;
            }
            if (bestBox != null) {
                p.mainAffix = rule.matchMain(bestBox.text);
                p.mainValue = extractNumber(bestBox.text);
            }
        }

        if (p.mainAffix == null && subTitleY >= 0) {
            OcrEngine.Box bestBox = null;
            for (OcrEngine.Box b : list) {
                if (b.y >= subTitleY) continue;
                String t = strip(b.text);
                if (t.isEmpty() || isMainTitle(t) || isSubTitle(t)) continue;
                if (rule.matchSet(b.text) != null) continue;
                if (rule.matchMain(b.text) == null) continue;
                if (bestBox == null || b.y > bestBox.y) bestBox = b;
            }
            if (bestBox != null) {
                p.mainAffix = rule.matchMain(bestBox.text);
                p.mainValue = extractNumber(bestBox.text);
            }
        }

        int subStart = subTitleY >= 0 ? subTitleY : (mainTitleY >= 0 ? mainTitleY + 120 : 0);
        for (OcrEngine.Box b : list) {
            if (b.y <= subStart) continue;
            String t = strip(b.text);
            if (t.isEmpty()) continue;
            if (isSubTitle(t) || isMainTitle(t)) continue;

            if (t.matches("^\\+\\d+$")) continue;

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

    private static boolean isMainTitle(String t) {
        return t.equals("主属性") || t.equals("主词条");
    }

    private static boolean isSubTitle(String t) {
        return t.equals("副属性") || t.equals("副词条");
    }

    private static String strip(String s) {
        if (s == null) return "";
        return s.replaceAll("[\\s\\u3000]", "")
                .replace("：", "").replace(":", "");
    }

    private static String extractNumber(String s) {
        if (s == null) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\d+(?:\\.\\d+)?%?)").matcher(s);
        return m.find() ? m.group(1) : "";
    }

    public Parse read(OcrEngine ocr, Bitmap full, int[] region) {
        if (ocr == null || !ocr.isReady() || full == null) {
            Parse p = new Parse();
            p.debug = "OCR未就绪";
            return p;
        }
        int[] r = region != null ? region : RegionStore.scan();
        List<OcrEngine.Box> boxes = ocr.recognize(full, r);
        return parse(boxes, r);
    }

    public Parse read(List<OcrEngine.Box> boxes, int[] region) {
        return parse(boxes, region != null ? region : RegionStore.scan());
    }
}
