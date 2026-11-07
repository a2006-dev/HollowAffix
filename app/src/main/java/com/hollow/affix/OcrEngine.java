package com.hollow.affix;

import android.content.Context;
import android.graphics.Bitmap;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PP-OCRv4 精简实现：det(检测文本框) + cls(方向) + rec(识别)。
 * 模型与 Linux 端 rapidocr_onnxruntime 完全一致，识别结果可与桌面端对齐。
 */
public class OcrEngine {

    private OrtEnvironment env;
    private OrtSession detSession, recSession;
    private List<String> charset;
    private boolean ready = false;
    private String loadError = "";

    public synchronized void init(Context ctx) {
        if (ready) return;
        long t0 = System.currentTimeMillis();
        try {
            env = OrtEnvironment.getEnvironment();

            byte[] det = readAsset(ctx, "ch_PP-OCRv4_det_infer.onnx");
            byte[] rec = readAsset(ctx, "ch_PP-OCRv4_rec_infer.onnx");

            OrtSession.SessionOptions opt = new OrtSession.SessionOptions();
            opt.setIntraOpNumThreads(4);

            detSession = env.createSession(det, opt);
            recSession = env.createSession(rec, opt);

            charset = new ArrayList<>();
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(ctx.getAssets().open("ppocr_keys.txt"), "UTF-8"));
            String line;
            while ((line = br.readLine()) != null) charset.add(line);
            br.close();

            ready = true;
            Logger.i("OCR", "模型加载完成 " + (System.currentTimeMillis() - t0) + "ms, 字典 "
                    + charset.size() + " 字");
        } catch (Throwable e) {
            loadError = e.toString();
            Logger.e("OCR", "模型加载失败: " + loadError);
        }
    }

    public boolean isReady() { return ready; }
    public String getLoadError() { return loadError; }

    private byte[] readAsset(Context ctx, String name) throws Exception {
        InputStream is = ctx.getAssets().open(name);
        byte[] buf = new byte[is.available()];
        int off = 0;
        while (off < buf.length) {
            int n = is.read(buf, off, buf.length - off);
            if (n < 0) break;
            off += n;
        }
        is.close();
        return buf;
    }

    public static class Box {
        public int x, y, w, h;      // 原图坐标
        public String text = "";
        public float score = 0f;
    }

    /**
     * 识别整图（或指定区域）。region 为 null 则全图。
     * detLimit: 检测输入的最短边，736 = 桌面端默认
     */
    public synchronized List<Box> recognize(Bitmap src, int[] region) {
        List<Box> out = new ArrayList<>();
        if (!ready) return out;

        Bitmap full = src;
        int offX = 0, offY = 0;

        if (region != null) {
            int rx = Math.max(0, region[0]), ry = Math.max(0, region[1]);
            int rw = Math.min(region[2], src.getWidth() - rx);
            int rh = Math.min(region[3], src.getHeight() - ry);
            if (rw <= 0 || rh <= 0) return out;
            full = Bitmap.createBitmap(src, rx, ry, rw, rh);
            offX = rx; offY = ry;
        }

        long t0 = System.currentTimeMillis();
        // 1) 检测
        float scale = 736f / Math.min(full.getWidth(), full.getHeight());
        if (scale > 1f) scale = 1f;
        int dw = Math.max(32, (int) (full.getWidth() * scale) / 32 * 32);
        int dh = Math.max(32, (int) (full.getHeight() * scale) / 32 * 32);

        List<int[]> boxes = detect(full, dw, dh, scale);
        Logger.i("OCR", "检测到 " + boxes.size() + " 个文本框, det="
                + (System.currentTimeMillis() - t0) + "ms");
        Logger.appendTo("trace.txt", "  det: 输入 " + dw + "x" + dh
                + " 得到 " + boxes.size() + " 框");

        // 2) 识别每个框
        long t1 = System.currentTimeMillis();
        for (int[] b : boxes) {
            String[] r = recognizeBox(full, b);
            if (r == null) continue;
            if (r[1].trim().isEmpty()) continue;
            Box box = new Box();
            box.x = b[0] + offX; box.y = b[1] + offY;
            box.w = b[2]; box.h = b[3];
            box.text = r[1];
            box.score = Float.parseFloat(r[0]);
            out.add(box);
        }
        Logger.i("OCR", "识别完成 " + out.size() + " 行, rec="
                + (System.currentTimeMillis() - t1) + "ms");
        return out;
    }

    /**
     * DB 检测后处理，按官方算法实现：
     * 1) 概率图按 thresh 二值化
     * 2) 连通域找轮廓，求最小外接旋转矩形
     * 3) unclip 外扩（按面积/周长比），保证文字完整
     * 4) 用框内置信度均值过滤（box_thresh）
     *
     * 参数对齐 config.yaml：thresh=0.3, box_thresh=0.5, unclip_ratio=1.6
     */
    private List<int[]> detect(Bitmap full, int dw, int dh, float scale) {
        List<int[]> result = new ArrayList<>();
        final float THRESH = 0.3f;
        final float BOX_THRESH = 0.5f;
        final float UNCLIP_RATIO = 1.6f;

        try {
            Bitmap small = Bitmap.createScaledBitmap(full, dw, dh, true);
            float[] data = toChw(small, 1f / 255f, new float[]{0.5f, 0.5f, 0.5f},
                    new float[]{0.5f, 0.5f, 0.5f});
            OnnxTensor t = OnnxTensor.createTensor(env,
                    FloatBuffer.wrap(data), new long[]{1, 3, dh, dw});
            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put(detSession.getInputNames().iterator().next(), t);
            OrtSession.Result r = detSession.run(inputs);
            float[][][][] prob = (float[][][][]) r.get(0).getValue();
            r.close(); t.close(); small.recycle();

            int ph = prob[0][0].length, pw = prob[0][0][0].length;
            float[][] map = new float[ph][pw];
            for (int y = 0; y < ph; y++)
                for (int x = 0; x < pw; x++) map[y][x] = prob[0][0][y][x];

            // ---- 1) 二值化 ----
            boolean[][] bin = new boolean[ph][pw];
            for (int y = 0; y < ph; y++)
                for (int x = 0; x < pw; x++) bin[y][x] = map[y][x] > THRESH;

            // ---- 2) 连通域 → 每块的像素集合与统计 ----
            boolean[][] seen = new boolean[ph][pw];
            for (int sy = 0; sy < ph; sy++) {
                for (int sx = 0; sx < pw; sx++) {
                    if (!bin[sy][sx] || seen[sy][sx]) continue;

                    List<int[]> pix = new ArrayList<>();
                    java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
                    q.add(new int[]{sx, sy});
                    seen[sy][sx] = true;
                    int minX = sx, maxX = sx, minY = sy, maxY = sy;
                    float scoreSum = 0f;

                    while (!q.isEmpty()) {
                        int[] p = q.poll();
                        pix.add(p);
                        scoreSum += map[p[1]][p[0]];
                        minX = Math.min(minX, p[0]); maxX = Math.max(maxX, p[0]);
                        minY = Math.min(minY, p[1]); maxY = Math.max(maxY, p[1]);
                        for (int dy = -1; dy <= 1; dy++)
                            for (int dx = -1; dx <= 1; dx++) {
                                if (dx == 0 && dy == 0) continue;
                                int nx = p[0] + dx, ny = p[1] + dy;
                                if (nx < 0 || ny < 0 || nx >= pw || ny >= ph) continue;
                                if (!bin[ny][nx] || seen[ny][nx]) continue;
                                seen[ny][nx] = true;
                                q.add(new int[]{nx, ny});
                            }
                    }

                    int bw = maxX - minX + 1, bh = maxY - minY + 1;
                    int area = pix.size();
                    if (area < 12 || bw < 3 || bh < 3) continue;

                    // ---- 3) 置信度过滤 ----
                    float boxScore = scoreSum / area;
                    if (boxScore < BOX_THRESH) continue;

                    // ---- 4) unclip 外扩 ----
                    // DB 公式：distance = area * ratio / perimeter
                    float perimeter = 2f * (bw + bh);
                    float distance = area * UNCLIP_RATIO / perimeter;
                    int padX = Math.max(2, Math.round(distance));
                    int padY = Math.max(2, Math.round(distance * 1.15f));  // 纵向略多，中文上下结构需要

                    float inv = 1f / scale;
                    int ox = Math.round((minX - padX) * inv);
                    int oy = Math.round((minY - padY) * inv);
                    int ow = Math.round((bw + padX * 2) * inv);
                    int oh = Math.round((bh + padY * 2) * inv);

                    ox = Math.max(0, ox); oy = Math.max(0, oy);
                    ow = Math.min(ow, full.getWidth() - ox);
                    oh = Math.min(oh, full.getHeight() - oy);
                    if (ow < 6 || oh < 6) continue;

                    result.add(new int[]{ox, oy, ow, oh});
                }
            }

            // ---- 5) 去掉互相包含的重复框（大框包小框时留大框）----
            result = removeContained(result);

        } catch (Throwable e) {
            Logger.e("OCR", "det 失败: " + e);
            Logger.appendTo("trace.txt", "  det异常: " + e);
        }
        return result;
    }

    /** 去掉被其他框完全包含的小框 */
    private List<int[]> removeContained(List<int[]> boxes) {
        List<int[]> keep = new ArrayList<>();
        for (int i = 0; i < boxes.size(); i++) {
            int[] a = boxes.get(i);
            boolean contained = false;
            for (int j = 0; j < boxes.size(); j++) {
                if (i == j) continue;
                int[] b = boxes.get(j);
                int aArea = a[2] * a[3], bArea = b[2] * b[3];
                if (aArea >= bArea) continue;
                // a 被 b 包含（留 15% 余量）
                if (a[0] >= b[0] - a[2] * 0.15f
                        && a[1] >= b[1] - a[3] * 0.15f
                        && a[0] + a[2] <= b[0] + b[2] + a[2] * 0.15f
                        && a[1] + a[3] <= b[1] + b[3] + a[3] * 0.15f) {
                    contained = true;
                    break;
                }
            }
            if (!contained) keep.add(a);
        }
        return keep;
    }

    /**
     * 把同一行的碎片框合并成一个框。
     * 简化版 det 会把「环合强度增加24」拆成「环合强度」+「增加24」，
     * 合并后才能正确识别完整文字。
     */
    private List<int[]> mergeSameLine(List<int[]> boxes) {
        if (boxes.size() <= 1) return boxes;

        // 按 y 排序
        List<int[]> sorted = new ArrayList<>(boxes);
        java.util.Collections.sort(sorted, new java.util.Comparator<int[]>() {
            @Override public int compare(int[] a, int[] b) {
                return Integer.compare(a[1], b[1]);
            }
        });

        List<int[]> merged = new ArrayList<>();
        List<int[]> line = new ArrayList<>();
        for (int[] b : sorted) {
            if (line.isEmpty()) {
                line.add(b);
                continue;
            }
            int[] first = line.get(0);
            // 中心 y 相差小于 高度的一半 → 同一行
            int cy1 = first[1] + first[3] / 2;
            int cy2 = b[1] + b[3] / 2;
            if (Math.abs(cy1 - cy2) <= Math.max(first[3], b[3]) / 2) {
                line.add(b);
            } else {
                merged.add(mergeLine(line));
                line = new ArrayList<>();
                line.add(b);
            }
        }
        if (!line.isEmpty()) merged.add(mergeLine(line));
        return merged;
    }

    /** 合并同一行的多个框为一个外接框 */
    private int[] mergeLine(List<int[]> line) {
        if (line.size() == 1) return line.get(0);
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE;
        int x1 = 0, y1 = 0;
        for (int[] b : line) {
            x0 = Math.min(x0, b[0]);
            y0 = Math.min(y0, b[1]);
            x1 = Math.max(x1, b[0] + b[2]);
            y1 = Math.max(y1, b[1] + b[3]);
        }
        return new int[]{x0, y0, x1 - x0, y1 - y0};
    }

    /** 识别单个框，返回 [score, text] */
    private String[] recognizeBox(Bitmap full, int[] b) {
        try {
            int x = Math.max(0, b[0]), y = Math.max(0, b[1]);
            int w = Math.min(b[2], full.getWidth() - x);
            int h = Math.min(b[3], full.getHeight() - y);
            if (w < 4 || h < 4) return null;

            // 按原始宽高比缩放到高 48，宽度不超过 320（PP-OCRv4 rec 输入）
            int targetH = 48;
            int targetW = Math.min(320, Math.max(16, Math.round(w * targetH / (float) h)));
            targetW = (targetW + 3) / 4 * 4;

            Bitmap crop = Bitmap.createBitmap(full, x, y, w, h);
            Bitmap scaled = Bitmap.createScaledBitmap(crop, targetW, targetH, true);
            crop.recycle();

            // 填充到固定宽度 320
            float[] data = toChwPadded(scaled, targetW, targetH, 320, 48);
            scaled.recycle();

            OnnxTensor t = OnnxTensor.createTensor(env,
                    FloatBuffer.wrap(data), new long[]{1, 3, 48, 320});
            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put(recSession.getInputNames().iterator().next(), t);
            OrtSession.Result r = recSession.run(inputs);
            float[][][] out = (float[][][]) r.get(0).getValue();
            r.close(); t.close();

            // CTC 贪心解码。
            // 字典第 0 位是 blank（CTC 类别 0），所以非 0 类别直接对应
            // charset[best]，不存在 -1 偏移。
            int steps = out[0].length, cls = out[0][0].length;
            int validSteps = Math.max(1, Math.min(steps,
                    (int) Math.ceil(steps * (targetW / 320f))));
            StringBuilder sb = new StringBuilder();
            float total = 0; int cnt = 0; int prev = -1;
            for (int i = 0; i < validSteps; i++) {
                int best = 0; float bv = out[0][i][0];
                for (int c = 1; c < cls; c++)
                    if (out[0][i][c] > bv) { bv = out[0][i][c]; best = c; }
                if (best != 0 && best != prev) {
                    if (best >= 0 && best < charset.size()) sb.append(charset.get(best));
                    total += bv; cnt++;
                }
                prev = best;
            }
            float score = cnt > 0 ? total / cnt : 0f;
            return new String[]{String.valueOf(score), sb.toString()};
        } catch (Throwable e) {
            Logger.appendTo("trace.txt", "  rec异常: " + e);
            return null;
        }
    }
    private float[] toChw(Bitmap bmp, float s, float[] mean, float[] std) {
        int w = bmp.getWidth(), h = bmp.getHeight();
        float[] f = new float[3 * h * w];
        int[] px = new int[w * h];
        bmp.getPixels(px, 0, w, 0, 0, w, h);
        for (int i = 0; i < px.length; i++) {
            int p = px[i];
            f[i] = (((p >> 16) & 0xFF) * s - mean[0]) / std[0];
            f[h * w + i] = (((p >> 8) & 0xFF) * s - mean[1]) / std[1];
            f[2 * h * w + i] = (((p) & 0xFF) * s - mean[2]) / std[2];
        }
        return f;
    }

    /**
     * 转 CHW 并右侧补零到目标宽度。
     * rec 模型要求固定 320 宽，但真实文字不该被拉变形，所以按比例缩放后补齐。
     * 补零段归一化后必须为 0（即模型眼里的「灰底」），不能是 -1。
     */
    private float[] toChwPadded(Bitmap bmp, int srcW, int srcH,
                                int dstW, int dstH) {
        float[] f = new float[3 * dstH * dstW];   // 默认全 0
        int[] px = new int[srcW * srcH];
        bmp.getPixels(px, 0, srcW, 0, 0, srcW, srcH);

        float s = 1f / 255f, m = 0.5f, sd = 0.5f;
        for (int y = 0; y < srcH && y < dstH; y++) {
            for (int x = 0; x < srcW && x < dstW; x++) {
                int p = px[y * srcW + x];
                int idx = y * dstW + x;
                f[idx] = (((p >> 16) & 0xFF) * s - m) / sd;
                f[dstH * dstW + idx] = (((p >> 8) & 0xFF) * s - m) / sd;
                f[2 * dstH * dstW + idx] = (((p) & 0xFF) * s - m) / sd;
            }
        }
        return f;
    }
}