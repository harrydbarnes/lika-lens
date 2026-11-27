package com.scope.camera;

import android.graphics.Bitmap;

/**
 * Off-UI-thread analyzer for viewfinder exposure aids: luminance & RGB histograms,
 * highlight zebra cells, focus-peaking edge cells, dirty-lens haze detection,
 * open-palm self-timer gesture detection, and 180-degree video shutter luma metering.
 */
final class ExposureAidsAnalyzer {
    static final int HIST_BINS = 32;
    static final int GRID_SIZE = 24;
    static final int DIRTY_LENS_CLEAN_LIMIT = 8;

    interface Callback {
        void onDirtyLensDetected();
        void onPalmDetected();
        void onVideo180MeanLuma(double meanLuma);
    }

    private final Object lock = new Object();
    private final int[] liveHistogramBins = new int[HIST_BINS];
    private final int[] liveHistR = new int[HIST_BINS];
    private final int[] liveHistG = new int[HIST_BINS];
    private final int[] liveHistB = new int[HIST_BINS];
    private boolean liveHighlightClipped = false;
    private boolean liveShadowClipped = false;
    private final boolean[] liveZebraCells = new boolean[GRID_SIZE * GRID_SIZE];
    private final boolean[] livePeakingCells = new boolean[GRID_SIZE * GRID_SIZE];
    private int[] samplePixels = new int[64 * 48];
    private int[] sampleLuma = new int[64 * 48];

    private final InteractionState.Detection dirtyLens = new InteractionState.Detection(6, 90000);
    private final InteractionState.Detection palm = new InteractionState.Detection(4, 8000);
    private volatile String dirtyLensCheckedCameraId = null;
    private volatile int dirtyLensCleanSamples = 0;

    boolean shouldCheckDirtyLens(String activeCameraId, boolean prefDirtyLensWarning, boolean isFrontCamera) {
        if (activeCameraId != null && !activeCameraId.equals(dirtyLensCheckedCameraId)) {
            dirtyLensCheckedCameraId = activeCameraId;
            dirtyLensCleanSamples = 0;
            dirtyLens.resetTracking();
        }
        return prefDirtyLensWarning && !isFrontCamera && dirtyLensCleanSamples < DIRTY_LENS_CLEAN_LIMIT;
    }

    void resetDetectors() {
        palm.resetTracking();
        dirtyLens.resetTracking();
        dirtyLensCleanSamples = 0;
        dirtyLensCheckedCameraId = null;
    }

    void analyzeBitmap(Bitmap bmp, int w, int h,
                       boolean wantHist, boolean wantZebra, boolean wantPeak,
                       boolean wantDirty, boolean wantPalm, boolean want180Luma,
                       boolean palmAllowed, long nowMs, Callback callback) {
        int total = w * h;
        if (samplePixels.length < total) {
            samplePixels = new int[total];
            sampleLuma = new int[total];
        }
        bmp.getPixels(samplePixels, 0, w, 0, 0, w, h);
        analyzePixels(samplePixels, w, h, wantHist, wantZebra, wantPeak,
                wantDirty, wantPalm, want180Luma, palmAllowed, nowMs, callback);
    }

    void analyzePixels(int[] px, int w, int h,
                       boolean wantHist, boolean wantZebra, boolean wantPeak,
                       boolean wantDirty, boolean wantPalm, boolean want180Luma,
                       boolean palmAllowed, long nowMs, Callback callback) {
        int total = w * h;
        if (total <= 0 || px == null || px.length < total) return;
        if (sampleLuma.length < total) {
            sampleLuma = new int[total];
        }
        int[] luma = sampleLuma;

        int[] localHist = wantHist ? new int[HIST_BINS] : null;
        int[] localR = wantHist ? new int[HIST_BINS] : null;
        int[] localG = wantHist ? new int[HIST_BINS] : null;
        int[] localB = wantHist ? new int[HIST_BINS] : null;
        int clipHigh = 0;
        int clipLow = 0;
        long sumLuma = 0L;
        long sumLumaWeight = 0L;
        final int cx0 = w >> 2;
        final int cx1 = w - cx0;
        final int cy0 = h >> 2;
        final int cy1 = h - cy0;
        for (int i = 0; i < total; i++) {
            int c = px[i];
            int r = (c >> 16) & 0xff;
            int g = (c >> 8) & 0xff;
            int b = c & 0xff;
            int y = (r * 77 + g * 150 + b * 29) >> 8;
            luma[i] = y;
            if (want180Luma) {
                int pxY = i / w;
                int pxX = i - pxY * w;
                int weight = (pxX >= cx0 && pxX < cx1 && pxY >= cy0 && pxY < cy1) ? 3 : 1;
                sumLuma += (long) y * weight;
                sumLumaWeight += weight;
            }
            if (wantHist) {
                int bin = Math.min(HIST_BINS - 1, y >> 3);
                localHist[bin]++;
                localR[Math.min(HIST_BINS - 1, r >> 3)]++;
                localG[Math.min(HIST_BINS - 1, g >> 3)]++;
                localB[Math.min(HIST_BINS - 1, b >> 3)]++;
                if (y >= 250) clipHigh++;
                if (y <= 4) clipLow++;
            }
        }
        synchronized (lock) {
            if (wantHist) {
                System.arraycopy(localHist, 0, liveHistogramBins, 0, HIST_BINS);
                System.arraycopy(localR, 0, liveHistR, 0, HIST_BINS);
                System.arraycopy(localG, 0, liveHistG, 0, HIST_BINS);
                System.arraycopy(localB, 0, liveHistB, 0, HIST_BINS);
                liveHighlightClipped = clipHigh > (total * 0.012f);
                liveShadowClipped = clipLow > (total * 0.020f);
            }
            if (wantZebra || wantPeak) {
                for (int cy = 0; cy < GRID_SIZE; cy++) {
                    int sy = Math.min(h - 1, (cy * h + (h / 2)) / GRID_SIZE);
                    int rowOff = sy * w;
                    int gridRow = cy * GRID_SIZE;
                    for (int cx = 0; cx < GRID_SIZE; cx++) {
                        int idx = gridRow + cx;
                        int sx = Math.min(w - 1, (cx * w + (w / 2)) / GRID_SIZE);
                        int pIdx = rowOff + sx;
                        int yVal = luma[pIdx];
                        liveZebraCells[idx] = wantZebra && (yVal >= 242);
                        if (wantPeak && sx > 0 && sx < w - 1 && sy > 0 && sy < h - 1) {
                            int gx = Math.abs(luma[pIdx + 1] - luma[pIdx - 1]);
                            int gy = Math.abs(luma[pIdx + w] - luma[pIdx - w]);
                            livePeakingCells[idx] = (gx + gy) > 52;
                        } else {
                            livePeakingCells[idx] = false;
                        }
                    }
                }
            }
        }
        if (wantDirty) {
            checkDirtyLens(luma, w, h, nowMs, callback);
        }
        if (wantPalm) {
            checkPalmGesture(px, w, h, palmAllowed, nowMs, callback);
        }
        if (want180Luma && callback != null) {
            callback.onVideo180MeanLuma(sumLumaWeight > 0L ? (sumLuma / (double) sumLumaWeight) : 0.0);
        }
    }

    /** Copies active overlay buffers into caller-owned draw arrays; returns bitmask (1=shadowClip, 2=highlightClip). */
    int copyForDraw(boolean copyZebra, boolean[] outZebra,
                    boolean copyPeaking, boolean[] outPeaking,
                    boolean copyHist, int[] outHist, int[] outR, int[] outG, int[] outB) {
        synchronized (lock) {
            int flags = (liveShadowClipped ? 1 : 0) | (liveHighlightClipped ? 2 : 0);
            if (copyZebra && outZebra != null) {
                System.arraycopy(liveZebraCells, 0, outZebra, 0, Math.min(liveZebraCells.length, outZebra.length));
            }
            if (copyPeaking && outPeaking != null) {
                System.arraycopy(livePeakingCells, 0, outPeaking, 0, Math.min(livePeakingCells.length, outPeaking.length));
            }
            if (copyHist && outHist != null && outR != null && outG != null && outB != null) {
                System.arraycopy(liveHistogramBins, 0, outHist, 0, HIST_BINS);
                System.arraycopy(liveHistR, 0, outR, 0, HIST_BINS);
                System.arraycopy(liveHistG, 0, outG, 0, HIST_BINS);
                System.arraycopy(liveHistB, 0, outB, 0, HIST_BINS);
            }
            return flags;
        }
    }

    private void checkDirtyLens(int[] luma, int w, int h, long nowMs, Callback callback) {
        int total = w * h;
        long sumLuma = 0;
        int minLuma = 255;
        int maxLuma = 0;
        for (int i = 0; i < total; i++) {
            int y = luma[i];
            sumLuma += y;
            if (y < minLuma) minLuma = y;
            if (y > maxLuma) maxLuma = y;
        }
        int meanLuma = (int) (sumLuma / total);
        if (meanLuma < 55 || meanLuma > 225) {
            dirtyLens.resetTracking();
            return;
        }
        long gradientSum = 0;
        int edgeCount = 0;
        for (int y = 1; y < h - 1; y++) {
            int row = y * w;
            for (int x = 1; x < w - 1; x++) {
                int idx = row + x;
                gradientSum += Math.abs(luma[idx + 1] - luma[idx - 1]) + Math.abs(luma[idx + w] - luma[idx - w]);
                edgeCount += 2;
            }
        }
        float avgGradient = edgeCount > 0 ? ((float) gradientSum / edgeCount) : 0f;
        boolean hasSmudgeBloom = (minLuma > 36 && maxLuma >= 210 && avgGradient < 4.4f);
        if (hasSmudgeBloom) {
            dirtyLensCleanSamples = 0;
            if (dirtyLens.hit(nowMs) && callback != null) {
                callback.onDirtyLensDetected();
            }
        } else {
            dirtyLens.miss();
            if (dirtyLensCleanSamples < DIRTY_LENS_CLEAN_LIMIT) {
                dirtyLensCleanSamples++;
            }
        }
    }

    private void checkPalmGesture(int[] px, int w, int h, boolean palmAllowed, long nowMs, Callback callback) {
        if (!palmAllowed || palm.cooling(nowMs)) {
            palm.resetTracking();
            return;
        }
        int yStart = Math.max(2, (int) (h * 0.08f));
        int yEnd = Math.min(h - 2, (int) (h * 0.78f));
        int xStart = Math.max(2, (int) (w * 0.08f));
        int xEnd = Math.min(w - 2, (int) (w * 0.92f));
        int skinCount = 0;
        int minX = w, maxX = 0, minY = h, maxY = 0;

        for (int y = yStart; y < yEnd; y++) {
            int row = y * w;
            for (int x = xStart; x < xEnd; x++) {
                int c = px[row + x];
                int r = (c >> 16) & 0xff;
                int g = (c >> 8) & 0xff;
                int b = c & 0xff;
                if (isSkinTonePixel(r, g, b)) {
                    skinCount++;
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }

        int frameArea = w * h;
        int boxW = maxX - minX + 1;
        int boxH = maxY - minY + 1;
        if (skinCount < frameArea * 0.035f || skinCount > frameArea * 0.30f || boxW < 8 || boxH < 9) {
            palm.miss();
            return;
        }

        float aspect = boxW / (float) boxH;
        if (aspect < 0.48f || aspect > 1.55f) {
            palm.miss();
            return;
        }

        int fingerZoneTop = minY + Math.max(1, boxH / 10);
        int fingerZoneBottom = minY + Math.max(3, (boxH * 42) / 100);
        int maxRunsInRow = 0;
        int multiFingerRows = 0;
        for (int y = fingerZoneTop; y <= fingerZoneBottom && y < h; y++) {
            int row = y * w;
            int runs = 0;
            boolean inSkin = false;
            for (int x = minX; x <= maxX && x < w; x++) {
                int c = px[row + x];
                boolean skin = isSkinTonePixel((c >> 16) & 0xff, (c >> 8) & 0xff, c & 0xff);
                if (skin && !inSkin) {
                    runs++;
                    inSkin = true;
                } else if (!skin) {
                    inSkin = false;
                }
            }
            if (runs > maxRunsInRow) maxRunsInRow = runs;
            if (runs >= 3) multiFingerRows++;
        }

        int palmMidY = minY + (boxH * 62) / 100;
        int palmSkinPixels = 0;
        if (palmMidY < h) {
            int row = palmMidY * w;
            for (int x = minX; x <= maxX && x < w; x++) {
                int c = px[row + x];
                if (isSkinTonePixel((c >> 16) & 0xff, (c >> 8) & 0xff, c & 0xff)) {
                    palmSkinPixels++;
                }
            }
        }
        float palmSolidity = palmSkinPixels / (float) Math.max(1, boxW);

        if (maxRunsInRow >= 3 && multiFingerRows >= 2 && palmSolidity >= 0.35f) {
            if (palm.hit(nowMs)) {
                palm.resetTracking();
                if (callback != null) {
                    callback.onPalmDetected();
                }
            }
        } else {
            palm.miss();
        }
    }

    static boolean isSkinTonePixel(int r, int g, int b) {
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        return r > 85 && g > 38 && b > 20
                && r > g && g >= (b - 10)
                && (max - min) > 16
                && (r - g) >= 10 && (r - b) >= 18;
    }
}
