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
        public int x, y, w, h;
        public String text = "";
        public float score = 0f;
    }

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

        float scale = 736f / Math.min(full.getWidth(), full.getHeight());
        if (scale > 1f) scale = 1f;
        int dw = Math.max(32, (int) (full.getWidth() * scale) / 32 * 32);
        int dh = Math.max(32, (int) (full.getHeight() * scale) / 32 * 32);

        List<int[]> boxes = detect(full, dw, dh, scale);
        Logger.i("OCR", "检测到 " + boxes.size() + " 个文本框, det="
                + (System.currentTimeMillis() - t0) + "ms");
        Logger.appendTo("trace.txt", "  det: 输入 " + dw + "x" + dh
                + " 得到 " + boxes.size() + " 框");

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

            boolean[][] bin = new boolean[ph][pw];
            for (int y = 0; y < ph; y++)
                for (int x = 0; x < pw; x++) bin[y][x] = map[y][x] > THRESH;

            boolean[][] seen = new boolean[ph][pw];

            int[] qx = new int[ph * pw];
            int[] qy = new int[ph * pw];
            for (int sy = 0; sy < ph; sy++) {
                for (int sx = 0; sx < pw; sx++) {
                    if (!bin[sy][sx] || seen[sy][sx]) continue;

                    int head = 0, tail = 0;
                    qx[tail] = sx; qy[tail] = sy; tail++;
                    seen[sy][sx] = true;
                    int minX = sx, maxX = sx, minY = sy, maxY = sy;
                    float scoreSum = 0f;
                    int area = 0;

                    while (head < tail) {
                        int px2 = qx[head], py2 = qy[head];
                        head++;
                        area++;
                        scoreSum += map[py2][px2];
                        if (px2 < minX) minX = px2;
                        if (px2 > maxX) maxX = px2;
                        if (py2 < minY) minY = py2;
                        if (py2 > maxY) maxY = py2;
                        for (int dy = -1; dy <= 1; dy++) {
                            int ny = py2 + dy;
                            if (ny < 0 || ny >= ph) continue;
                            for (int dx = -1; dx <= 1; dx++) {
                                if (dx == 0 && dy == 0) continue;
                                int nx = px2 + dx;
                                if (nx < 0 || nx >= pw) continue;
                                if (!bin[ny][nx] || seen[ny][nx]) continue;
                                seen[ny][nx] = true;
                                qx[tail] = nx; qy[tail] = ny; tail++;
                            }
                        }
                    }

                    int bw = maxX - minX + 1, bh = maxY - minY + 1;
                    if (area < 12 || bw < 3 || bh < 3) continue;

                    float boxScore = scoreSum / area;
                    if (boxScore < BOX_THRESH) continue;

                    float perimeter = 2f * (bw + bh);
                    float distance = area * UNCLIP_RATIO / perimeter;
                    int padX = Math.max(2, Math.round(distance));
                    int padY = Math.max(2, Math.round(distance * 1.15f));

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

            result = removeContained(result);

        } catch (Throwable e) {
            Logger.e("OCR", "det 失败: " + e);
            Logger.appendTo("trace.txt", "  det异常: " + e);
        }
        return result;
    }

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

    private String[] recognizeBox(Bitmap full, int[] b) {
        try {
            int x = Math.max(0, b[0]), y = Math.max(0, b[1]);
            int w = Math.min(b[2], full.getWidth() - x);
            int h = Math.min(b[3], full.getHeight() - y);
            if (w < 4 || h < 4) return null;

            final int targetH = 48;
            int targetW = Math.min(320, Math.max(16, Math.round(w * targetH / (float) h)));
            targetW = (targetW + 3) / 4 * 4;

            float[] data = cropToChwPadded(full, x, y, w, h, targetW, targetH, 320, 48);

            OnnxTensor t = OnnxTensor.createTensor(env,
                    FloatBuffer.wrap(data), new long[]{1, 3, 48, 320});
            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put(recSession.getInputNames().iterator().next(), t);
            OrtSession.Result r = recSession.run(inputs);
            float[][][] out = (float[][][]) r.get(0).getValue();
            r.close(); t.close();

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
                    if (best < charset.size()) sb.append(charset.get(best));
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

    private float[] cropToChwPadded(Bitmap full, int x, int y, int w, int h,
                                    int targetW, int targetH, int dstW, int dstH) {

        int[] src = new int[w * h];
        full.getPixels(src, 0, w, x, y, w, h);

        float[] f = new float[3 * dstH * dstW];
        final int plane = dstH * dstW;
        final float s = 1f / 255f, m = 0.5f, sd = 0.5f;
        final float sx = w / (float) targetW;
        final float sy = h / (float) targetH;

        for (int ty = 0; ty < targetH; ty++) {
            int srcY = Math.min(h - 1, (int) (ty * sy));
            int rowBase = ty * dstW;
            int srcRow = srcY * w;
            for (int tx = 0; tx < targetW; tx++) {
                int srcX = Math.min(w - 1, (int) (tx * sx));
                int p = src[srcRow + srcX];
                int idx = rowBase + tx;
                f[idx] = (((p >> 16) & 0xFF) * s - m) / sd;
                f[plane + idx] = (((p >> 8) & 0xFF) * s - m) / sd;
                f[2 * plane + idx] = ((p & 0xFF) * s - m) / sd;
            }
        }
        return f;
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
}
