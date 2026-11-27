package com.scope.camera;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * Multi-frame night/astro stacking for both 16-bit Bayer RAW_SENSOR mosaics and JPEG fallback RGB
 * frames. Performs fast integer translational alignment (even-stepped for Bayer frames so CFA
 * phases never invert) followed by noise-adaptive robust temporal averaging to suppress high-ISO
 * read/shot noise, hot pixels, and moving-subject ghosting.
 */
final class NightStacker {
    private static final int SHIFT_RADIUS = 16;
    private static final int LOCAL_RADIUS = 8;

    private NightStacker() {}

    static short[] extractRawShorts(ByteBuffer raw, int width, int height, int rowStride, int pixelStride) {
        int total = width * height;
        short[] shorts = new short[total];
        if (raw == null) return shorts;
        ByteBuffer buf = raw.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        buf.position(0);
        int totalBytes = total * 2;
        if (pixelStride == 2 && rowStride == width * 2 && buf.limit() >= totalBytes) {
            buf.asShortBuffer().get(shorts);
        } else if (pixelStride == 2 && buf.limit() >= (height - 1) * rowStride + width * 2) {
            for (int y = 0; y < height; y++) {
                buf.position(y * rowStride);
                ByteBuffer slice = buf.slice().order(ByteOrder.LITTLE_ENDIAN);
                slice.limit(width * 2);
                slice.asShortBuffer().get(shorts, y * width, width);
            }
        } else {
            int limit = buf.limit();
            for (int y = 0; y < height; y++) {
                int rowStart = y * rowStride;
                int dstRow = y * width;
                for (int x = 0; x < width; x++) {
                    int off = rowStart + x * pixelStride;
                    shorts[dstRow + x] = (off >= 0 && off + 1 < limit) ? buf.getShort(off) : 0;
                }
            }
        }
        return shorts;
    }

    static ByteBuffer toLittleEndianBuffer(short[] samples) {
        if (samples == null) return null;
        ByteBuffer out = ByteBuffer.allocate(samples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        out.asShortBuffer().put(samples);
        out.position(0);
        return out;
    }

    static int[] estimateBayerShift(short[] base, short[] cur, int width, int height) {
        if (base == null || cur == null || width < 96 || height < 96) return new int[]{0, 0};
        final int margin = SHIFT_RADIUS + 4;
        final int xMin = Math.max(margin, width / 6) & ~1;
        final int xMax = Math.min(width - margin - 2, (width * 5) / 6) & ~1;
        final int yMin = Math.max(margin, height / 6) & ~1;
        final int yMax = Math.min(height - margin - 2, (height * 5) / 6) & ~1;
        if (xMax <= xMin || yMax <= yMin) return new int[]{0, 0};

        final int cols = 20;
        final int rows = 16;
        final int[] sxList = new int[cols];
        final int[] syList = new int[rows];
        for (int i = 0; i < cols; i++) {
            sxList[i] = (xMin + ((xMax - xMin) * i) / (cols - 1)) & ~1;
        }
        for (int j = 0; j < rows; j++) {
            syList[j] = (yMin + ((yMax - yMin) * j) / (rows - 1)) & ~1;
        }

        final long zeroSad = bayerSad(base, cur, width, sxList, syList, 0, 0);
        if (zeroSad == 0L) return new int[]{0, 0};

        int bestDx = 0;
        int bestDy = 0;
        long bestSad = zeroSad;
        for (int dy = -SHIFT_RADIUS; dy <= SHIFT_RADIUS; dy += 4) {
            for (int dx = -SHIFT_RADIUS; dx <= SHIFT_RADIUS; dx += 4) {
                if (dx == 0 && dy == 0) continue;
                long sad = bayerSad(base, cur, width, sxList, syList, dx, dy);
                if (sad < bestSad) {
                    bestSad = sad;
                    bestDx = dx;
                    bestDy = dy;
                }
            }
        }

        int fineBaseDx = bestDx;
        int fineBaseDy = bestDy;
        for (int oy = -2; oy <= 2; oy += 2) {
            for (int ox = -2; ox <= 2; ox += 2) {
                if (ox == 0 && oy == 0) continue;
                int dx = fineBaseDx + ox;
                int dy = fineBaseDy + oy;
                if (Math.abs(dx) > SHIFT_RADIUS || Math.abs(dy) > SHIFT_RADIUS) continue;
                long sad = bayerSad(base, cur, width, sxList, syList, dx, dy);
                if (sad < bestSad) {
                    bestSad = sad;
                    bestDx = dx;
                    bestDy = dy;
                }
            }
        }

        if (bestSad >= (zeroSad * 96L) / 100L) {
            return new int[]{0, 0};
        }
        return new int[]{bestDx & ~1, bestDy & ~1};
    }

    static int[] estimateBayerTileShifts(short[] base, short[] cur, int width, int height,
                                         int gDx, int gDy, int tileCols, int tileRows) {
        int[] shifts = new int[tileCols * tileRows * 2];
        for (int i = 0; i < tileCols * tileRows; i++) {
            shifts[i * 2] = gDx;
            shifts[i * 2 + 1] = gDy;
        }
        if (base == null || cur == null || width < 192 || height < 192) return shifts;

        final int safeMargin = SHIFT_RADIUS + LOCAL_RADIUS + 4;
        final int[] sxTile = new int[8];
        final int[] syTile = new int[8];
        for (int ty = 0; ty < tileRows; ty++) {
            int y0 = Math.max(safeMargin, (ty * height) / tileRows);
            int y1 = Math.min(height - safeMargin - 2, ((ty + 1) * height) / tileRows);
            if (y1 - y0 < 32) continue;
            for (int j = 0; j < 8; j++) {
                syTile[j] = (y0 + ((y1 - y0) * j) / 7) & ~1;
            }
            for (int tx = 0; tx < tileCols; tx++) {
                int x0 = Math.max(safeMargin, (tx * width) / tileCols);
                int x1 = Math.min(width - safeMargin - 2, ((tx + 1) * width) / tileCols);
                if (x1 - x0 < 32) continue;
                for (int i = 0; i < 8; i++) {
                    sxTile[i] = (x0 + ((x1 - x0) * i) / 7) & ~1;
                }
                long baseSad = bayerSad(base, cur, width, sxTile, syTile, gDx, gDy);
                if (baseSad == 0L) continue;
                int bestDx = gDx;
                int bestDy = gDy;
                long bestSad = baseSad;
                for (int ody = -LOCAL_RADIUS; ody <= LOCAL_RADIUS; ody += 4) {
                    for (int odx = -LOCAL_RADIUS; odx <= LOCAL_RADIUS; odx += 4) {
                        if (odx == 0 && ody == 0) continue;
                        int dx = gDx + odx;
                        int dy = gDy + ody;
                        long sad = bayerSad(base, cur, width, sxTile, syTile, dx, dy);
                        if (sad < bestSad) {
                            bestSad = sad;
                            bestDx = dx;
                            bestDy = dy;
                        }
                    }
                }
                int fDx = bestDx;
                int fDy = bestDy;
                for (int oy = -2; oy <= 2; oy += 2) {
                    for (int ox = -2; ox <= 2; ox += 2) {
                        if (ox == 0 && oy == 0) continue;
                        int dx = fDx + ox;
                        int dy = fDy + oy;
                        if (Math.abs(dx - gDx) > LOCAL_RADIUS || Math.abs(dy - gDy) > LOCAL_RADIUS) continue;
                        long sad = bayerSad(base, cur, width, sxTile, syTile, dx, dy);
                        if (sad < bestSad) {
                            bestSad = sad;
                            bestDx = dx;
                            bestDy = dy;
                        }
                    }
                }
                if (bestSad < (baseSad * 88L) / 100L) {
                    int idx = (ty * tileCols + tx) * 2;
                    shifts[idx] = bestDx & ~1;
                    shifts[idx + 1] = bestDy & ~1;
                }
            }
        }
        return shifts;
    }

    private static long bayerSad(short[] base, short[] cur, int width, int[] sxList, int[] syList, int dx, int dy) {
        long sad = 0L;
        for (int sy : syList) {
            int baseRow0 = sy * width;
            int baseRow1 = baseRow0 + width;
            int curRow0 = (sy + dy) * width;
            int curRow1 = curRow0 + width;
            for (int sx : sxList) {
                int bQuad = (base[baseRow0 + sx] & 0xffff) + (base[baseRow0 + sx + 1] & 0xffff)
                        + (base[baseRow1 + sx] & 0xffff) + (base[baseRow1 + sx + 1] & 0xffff);
                int cx = sx + dx;
                int cQuad = (cur[curRow0 + cx] & 0xffff) + (cur[curRow0 + cx + 1] & 0xffff)
                        + (cur[curRow1 + cx] & 0xffff) + (cur[curRow1 + cx + 1] & 0xffff);
                sad += Math.abs((bQuad - cQuad) >> 2);
            }
        }
        return sad;
    }

    static int[] estimateRgbShift(int[] base, int[] cur, int width, int height) {
        if (base == null || cur == null || width < 96 || height < 96) return new int[]{0, 0};
        final int margin = SHIFT_RADIUS + 2;
        final int xMin = Math.max(margin, width / 6);
        final int xMax = Math.min(width - margin - 1, (width * 5) / 6);
        final int yMin = Math.max(margin, height / 6);
        final int yMax = Math.min(height - margin - 1, (height * 5) / 6);
        if (xMax <= xMin || yMax <= yMin) return new int[]{0, 0};

        final int cols = 20;
        final int rows = 16;
        final int[] sxList = new int[cols];
        final int[] syList = new int[rows];
        for (int i = 0; i < cols; i++) sxList[i] = xMin + ((xMax - xMin) * i) / (cols - 1);
        for (int j = 0; j < rows; j++) syList[j] = yMin + ((yMax - yMin) * j) / (rows - 1);

        final long zeroSad = rgbSad(base, cur, width, sxList, syList, 0, 0);
        if (zeroSad == 0L) return new int[]{0, 0};

        int bestDx = 0;
        int bestDy = 0;
        long bestSad = zeroSad;
        for (int dy = -SHIFT_RADIUS; dy <= SHIFT_RADIUS; dy += 4) {
            for (int dx = -SHIFT_RADIUS; dx <= SHIFT_RADIUS; dx += 4) {
                if (dx == 0 && dy == 0) continue;
                long sad = rgbSad(base, cur, width, sxList, syList, dx, dy);
                if (sad < bestSad) {
                    bestSad = sad;
                    bestDx = dx;
                    bestDy = dy;
                }
            }
        }

        int fineBaseDx = bestDx;
        int fineBaseDy = bestDy;
        for (int oy = -2; oy <= 2; oy++) {
            for (int ox = -2; ox <= 2; ox++) {
                if (ox == 0 && oy == 0) continue;
                int dx = fineBaseDx + ox;
                int dy = fineBaseDy + oy;
                if (Math.abs(dx) > SHIFT_RADIUS || Math.abs(dy) > SHIFT_RADIUS) continue;
                long sad = rgbSad(base, cur, width, sxList, syList, dx, dy);
                if (sad < bestSad) {
                    bestSad = sad;
                    bestDx = dx;
                    bestDy = dy;
                }
            }
        }

        if (bestSad >= (zeroSad * 96L) / 100L) {
            return new int[]{0, 0};
        }
        return new int[]{bestDx, bestDy};
    }

    static int[] estimateRgbTileShifts(int[] base, int[] cur, int width, int height,
                                       int gDx, int gDy, int tileCols, int tileRows) {
        int[] shifts = new int[tileCols * tileRows * 2];
        for (int i = 0; i < tileCols * tileRows; i++) {
            shifts[i * 2] = gDx;
            shifts[i * 2 + 1] = gDy;
        }
        if (base == null || cur == null || width < 192 || height < 192) return shifts;

        final int safeMargin = SHIFT_RADIUS + LOCAL_RADIUS + 2;
        final int[] sxTile = new int[8];
        final int[] syTile = new int[8];
        for (int ty = 0; ty < tileRows; ty++) {
            int y0 = Math.max(safeMargin, (ty * height) / tileRows);
            int y1 = Math.min(height - safeMargin - 1, ((ty + 1) * height) / tileRows);
            if (y1 - y0 < 32) continue;
            for (int j = 0; j < 8; j++) syTile[j] = y0 + ((y1 - y0) * j) / 7;
            for (int tx = 0; tx < tileCols; tx++) {
                int x0 = Math.max(safeMargin, (tx * width) / tileCols);
                int x1 = Math.min(width - safeMargin - 1, ((tx + 1) * width) / tileCols);
                if (x1 - x0 < 32) continue;
                for (int i = 0; i < 8; i++) sxTile[i] = x0 + ((x1 - x0) * i) / 7;

                long baseSad = rgbSad(base, cur, width, sxTile, syTile, gDx, gDy);
                if (baseSad == 0L) continue;
                int bestDx = gDx;
                int bestDy = gDy;
                long bestSad = baseSad;
                for (int ody = -LOCAL_RADIUS; ody <= LOCAL_RADIUS; ody += 2) {
                    for (int odx = -LOCAL_RADIUS; odx <= LOCAL_RADIUS; odx += 2) {
                        if (odx == 0 && ody == 0) continue;
                        int dx = gDx + odx;
                        int dy = gDy + ody;
                        long sad = rgbSad(base, cur, width, sxTile, syTile, dx, dy);
                        if (sad < bestSad) {
                            bestSad = sad;
                            bestDx = dx;
                            bestDy = dy;
                        }
                    }
                }
                int fDx = bestDx;
                int fDy = bestDy;
                for (int oy = -1; oy <= 1; oy++) {
                    for (int ox = -1; ox <= 1; ox++) {
                        if (ox == 0 && oy == 0) continue;
                        int dx = fDx + ox;
                        int dy = fDy + oy;
                        if (Math.abs(dx - gDx) > LOCAL_RADIUS || Math.abs(dy - gDy) > LOCAL_RADIUS) continue;
                        long sad = rgbSad(base, cur, width, sxTile, syTile, dx, dy);
                        if (sad < bestSad) {
                            bestSad = sad;
                            bestDx = dx;
                            bestDy = dy;
                        }
                    }
                }
                if (bestSad < (baseSad * 88L) / 100L) {
                    int idx = (ty * tileCols + tx) * 2;
                    shifts[idx] = bestDx;
                    shifts[idx + 1] = bestDy;
                }
            }
        }
        return shifts;
    }

    private static long rgbSad(int[] base, int[] cur, int width, int[] sxList, int[] syList, int dx, int dy) {
        long sad = 0L;
        for (int sy : syList) {
            int baseRow = sy * width;
            int curRow = (sy + dy) * width;
            for (int sx : sxList) {
                int bc = base[baseRow + sx];
                int cc = cur[curRow + sx + dx];
                int bLuma = (((bc >> 16) & 0xff) * 3 + ((bc >> 8) & 0xff) * 6 + (bc & 0xff));
                int cLuma = (((cc >> 16) & 0xff) * 3 + ((cc >> 8) & 0xff) * 6 + (cc & 0xff));
                sad += Math.abs(bLuma - cLuma);
            }
        }
        return sad;
    }

    private static int temporalTukeyWeight(int diff, int tol) {
        if (diff <= tol) return 256;
        int maxDiff = tol << 1;
        if (diff >= maxDiff) return 0;
        float norm = (diff - tol) / (float) Math.max(1, maxDiff - tol);
        float biweight = 1f - norm * norm;
        return Math.max(0, Math.min(256, Math.round(256f * biweight * biweight)));
    }

    /** Accumulates aligned 16-bit Bayer RAW frames into a low-noise stacked frame. */
    static final class RawAccumulator {
        final int width;
        final int height;
        final int whiteLevel;
        private final int[] tolLut = new int[256];
        private final long invWhiteScale;
        private short[] baseSamples;
        private int[] weightedSum;
        private short[] weightSum;
        private int frameCount;

        RawAccumulator(int width, int height, int whiteLevel) {
            if (width <= 0 || height <= 0 || (long) width * height > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Invalid RAW frame dimensions: " + width + "x" + height);
            }
            this.width = width;
            this.height = height;
            this.whiteLevel = whiteLevel > 0 ? whiteLevel : 65535;
            this.invWhiteScale = (255L << 16) / this.whiteLevel;
            int blackMargin = Math.max(32, Math.round(this.whiteLevel * 0.04f));
            for (int i = 0; i < 256; i++) {
                float bNorm = i / 255f;
                tolLut[i] = Math.max(blackMargin, Math.round(this.whiteLevel * (0.06f + 0.14f * (float) Math.sqrt(bNorm))));
            }
            int count = width * height;
            this.baseSamples = new short[count];
            this.weightedSum = new int[count];
            this.weightSum = new short[count];
        }

        int frameCount() {
            return frameCount;
        }

        void addFrame(short[] samples) {
            if (samples == null || samples.length < width * height || weightedSum == null) return;
            if (frameCount == 0) {
                System.arraycopy(samples, 0, baseSamples, 0, width * height);
                final short[] src = samples;
                final int[] wSum = weightedSum;
                final short[] wtSum = weightSum;
                IntStream.range(0, height).parallel().forEach(y -> {
                    int row = y * width;
                    int end = row + width;
                    for (int i = row; i < end; i++) {
                        wSum[i] = (src[i] & 0xffff) << 8;
                        wtSum[i] = 256;
                    }
                });
                frameCount = 1;
                return;
            }

            final int[] global = estimateBayerShift(baseSamples, samples, width, height);
            final int gDx = global[0];
            final int gDy = global[1];
            final int tileCols = Math.max(2, Math.min(8, width / 96));
            final int tileRows = Math.max(2, Math.min(6, height / 96));
            final int[] tileShifts = (width >= 192 && height >= 192)
                    ? estimateBayerTileShifts(baseSamples, samples, width, height, gDx, gDy, tileCols, tileRows)
                    : null;
            final short[] base = baseSamples;
            final int[] wSum = weightedSum;
            final short[] wtSum = weightSum;
            final int[] lut = tolLut;
            final long scale = invWhiteScale;

            IntStream.range(0, height).parallel().forEach(y -> {
                float tyf = Math.max(0f, Math.min(tileRows - 1f, (y + 0.5f) / Math.max(1f, height) * tileRows - 0.5f));
                int ty0 = (int) tyf;
                int ty1 = Math.min(tileRows - 1, ty0 + 1);
                float wy = tyf - ty0;
                int baseRow = y * width;
                for (int x = 0; x < width; x++) {
                    int dx = gDx;
                    int dy = gDy;
                    if (tileShifts != null) {
                        float txf = Math.max(0f, Math.min(tileCols - 1f, (x + 0.5f) / Math.max(1f, width) * tileCols - 0.5f));
                        int tx0 = (int) txf;
                        int tx1 = Math.min(tileCols - 1, tx0 + 1);
                        float wx = txf - tx0;
                        int i00 = (ty0 * tileCols + tx0) * 2;
                        int i10 = (ty0 * tileCols + tx1) * 2;
                        int i01 = (ty1 * tileCols + tx0) * 2;
                        int i11 = (ty1 * tileCols + tx1) * 2;
                        float fdx = (tileShifts[i00] * (1f - wx) + tileShifts[i10] * wx) * (1f - wy)
                                + (tileShifts[i01] * (1f - wx) + tileShifts[i11] * wx) * wy;
                        float fdy = (tileShifts[i00 + 1] * (1f - wx) + tileShifts[i10 + 1] * wx) * (1f - wy)
                                + (tileShifts[i01 + 1] * (1f - wx) + tileShifts[i11 + 1] * wx) * wy;
                        // Snap to even integers so Bayer CFA phase parity never inverts.
                        dx = Math.round(fdx * 0.5f) * 2;
                        dy = Math.round(fdy * 0.5f) * 2;
                    }
                    int sx = x + dx;
                    int sy = y + dy;
                    if (sx < 0 || sx >= width || sy < 0 || sy >= height) continue;
                    int idx = baseRow + x;
                    int b = base[idx] & 0xffff;
                    int s = samples[sy * width + sx] & 0xffff;
                    int diff = Math.abs(s - b);
                    int lutIdx = (int) Math.min(255L, (b * scale) >>> 16);
                    int w = temporalTukeyWeight(diff, lut[lutIdx]);
                    if (w > 0) {
                        wSum[idx] += s * w;
                        wtSum[idx] = (short) ((wtSum[idx] & 0xffff) + w);
                    }
                }
            });
            frameCount++;
        }

        short[] finishShorts() {
            if (weightedSum == null || weightSum == null) return new short[0];
            final int total = width * height;
            final short[] out = new short[total];
            final int[] wSum = weightedSum;
            final short[] wtSum = weightSum;
            final int maxVal = Math.min(65535, whiteLevel);
            IntStream.range(0, height).parallel().forEach(y -> {
                int row = y * width;
                int end = row + width;
                for (int i = row; i < end; i++) {
                    int w = wtSum[i] & 0xffff;
                    int val = w <= 0 ? 0 : (wSum[i] + (w >> 1)) / w;
                    out[i] = (short) (val < 0 ? 0 : Math.min(maxVal, val));
                }
            });
            release();
            return out;
        }

        void release() {
            baseSamples = null;
            weightedSum = null;
            weightSum = null;
        }
    }

    /** Accumulates aligned ARGB_8888 JPEG fallback frames into a low-noise stacked image. */
    static final class RgbAccumulator {
        final int width;
        final int height;
        private final int[] tolLut = new int[256];
        private int[] basePixels;
        private int[] sumR;
        private int[] sumG;
        private int[] sumB;
        private short[] weightSum;
        private int frameCount;

        RgbAccumulator(int width, int height) {
            if (width <= 0 || height <= 0 || (long) width * height > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Invalid RGB frame dimensions: " + width + "x" + height);
            }
            this.width = width;
            this.height = height;
            for (int i = 0; i < 256; i++) {
                float bNorm = i / 255f;
                tolLut[i] = Math.max(12, Math.round(14f + 28f * (float) Math.sqrt(bNorm)));
            }
            int count = width * height;
            this.basePixels = new int[count];
            this.sumR = new int[count];
            this.sumG = new int[count];
            this.sumB = new int[count];
            this.weightSum = new short[count];
        }

        int frameCount() {
            return frameCount;
        }

        void addFrame(int[] pixels) {
            if (pixels == null || pixels.length < width * height || sumR == null) return;
            if (frameCount == 0) {
                System.arraycopy(pixels, 0, basePixels, 0, width * height);
                final int[] sR = sumR;
                final int[] sG = sumG;
                final int[] sB = sumB;
                final short[] wtSum = weightSum;
                IntStream.range(0, height).parallel().forEach(y -> {
                    int row = y * width;
                    int end = row + width;
                    for (int i = row; i < end; i++) {
                        int c = pixels[i];
                        sR[i] = ((c >> 16) & 0xff) << 8;
                        sG[i] = ((c >> 8) & 0xff) << 8;
                        sB[i] = (c & 0xff) << 8;
                        wtSum[i] = 256;
                    }
                });
                frameCount = 1;
                return;
            }

            final int[] global = estimateRgbShift(basePixels, pixels, width, height);
            final int gDx = global[0];
            final int gDy = global[1];
            final int tileCols = Math.max(2, Math.min(8, width / 96));
            final int tileRows = Math.max(2, Math.min(6, height / 96));
            final int[] tileShifts = (width >= 192 && height >= 192)
                    ? estimateRgbTileShifts(basePixels, pixels, width, height, gDx, gDy, tileCols, tileRows)
                    : null;
            final int[] base = basePixels;
            final int[] sR = sumR;
            final int[] sG = sumG;
            final int[] sB = sumB;
            final short[] wtSum = weightSum;
            final int[] lut = tolLut;

            IntStream.range(0, height).parallel().forEach(y -> {
                float tyf = Math.max(0f, Math.min(tileRows - 1f, (y + 0.5f) / Math.max(1f, height) * tileRows - 0.5f));
                int ty0 = (int) tyf;
                int ty1 = Math.min(tileRows - 1, ty0 + 1);
                float wy = tyf - ty0;
                int baseRow = y * width;
                for (int x = 0; x < width; x++) {
                    int dx = gDx;
                    int dy = gDy;
                    if (tileShifts != null) {
                        float txf = Math.max(0f, Math.min(tileCols - 1f, (x + 0.5f) / Math.max(1f, width) * tileCols - 0.5f));
                        int tx0 = (int) txf;
                        int tx1 = Math.min(tileCols - 1, tx0 + 1);
                        float wx = txf - tx0;
                        int i00 = (ty0 * tileCols + tx0) * 2;
                        int i10 = (ty0 * tileCols + tx1) * 2;
                        int i01 = (ty1 * tileCols + tx0) * 2;
                        int i11 = (ty1 * tileCols + tx1) * 2;
                        float fdx = (tileShifts[i00] * (1f - wx) + tileShifts[i10] * wx) * (1f - wy)
                                + (tileShifts[i01] * (1f - wx) + tileShifts[i11] * wx) * wy;
                        float fdy = (tileShifts[i00 + 1] * (1f - wx) + tileShifts[i10 + 1] * wx) * (1f - wy)
                                + (tileShifts[i01 + 1] * (1f - wx) + tileShifts[i11 + 1] * wx) * wy;
                        dx = Math.round(fdx);
                        dy = Math.round(fdy);
                    }
                    int sx = x + dx;
                    int sy = y + dy;
                    if (sx < 0 || sx >= width || sy < 0 || sy >= height) continue;
                    int idx = baseRow + x;
                    int bc = base[idx];
                    int sc = pixels[sy * width + sx];
                    int br = (bc >> 16) & 0xff;
                    int bg = (bc >> 8) & 0xff;
                    int bb = bc & 0xff;
                    int sr = (sc >> 16) & 0xff;
                    int sg = (sc >> 8) & 0xff;
                    int sb = sc & 0xff;
                    int diff = Math.max(Math.abs(sr - br), Math.max(Math.abs(sg - bg), Math.abs(sb - bb)));
                    int luma = Math.min(255, (br * 54 + bg * 183 + bb * 19) >> 8);
                    int w = temporalTukeyWeight(diff, lut[luma]);
                    if (w > 0) {
                        sR[idx] += sr * w;
                        sG[idx] += sg * w;
                        sB[idx] += sb * w;
                        wtSum[idx] = (short) ((wtSum[idx] & 0xffff) + w);
                    }
                }
            });
            frameCount++;
        }

        int[] finishPixels() {
            if (sumR == null || weightSum == null) return new int[0];
            final int total = width * height;
            final int[] out = new int[total];
            final int[] sR = sumR;
            final int[] sG = sumG;
            final int[] sB = sumB;
            final short[] wtSum = weightSum;
            IntStream.range(0, height).parallel().forEach(y -> {
                int row = y * width;
                int end = row + width;
                for (int i = row; i < end; i++) {
                    int w = wtSum[i] & 0xffff;
                    if (w <= 0) {
                        out[i] = 0xff000000;
                    } else {
                        int half = w >> 1;
                        int r = Math.min(255, Math.max(0, (sR[i] + half) / w));
                        int g = Math.min(255, Math.max(0, (sG[i] + half) / w));
                        int b = Math.min(255, Math.max(0, (sB[i] + half) / w));
                        out[i] = 0xff000000 | (r << 16) | (g << 8) | b;
                    }
                }
            });
            release();
            return out;
        }

        void release() {
            basePixels = null;
            sumR = null;
            sumG = null;
            sumB = null;
            weightSum = null;
        }
    }
}
