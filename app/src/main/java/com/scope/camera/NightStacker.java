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

            final int[] shift = estimateBayerShift(baseSamples, samples, width, height);
            final int dx = shift[0];
            final int dy = shift[1];
            final short[] base = baseSamples;
            final int[] wSum = weightedSum;
            final short[] wtSum = weightSum;
            final int[] lut = tolLut;
            final long scale = invWhiteScale;
            final int xStart = Math.max(0, -dx);
            final int xEnd = Math.min(width, width - dx);

            IntStream.range(0, height).parallel().forEach(y -> {
                int sy = y + dy;
                if (sy < 0 || sy >= height) return;
                int baseRow = y * width;
                int srcRow = sy * width;
                for (int x = xStart; x < xEnd; x++) {
                    int idx = baseRow + x;
                    int b = base[idx] & 0xffff;
                    int s = samples[srcRow + x + dx] & 0xffff;
                    int diff = Math.abs(s - b);
                    int lutIdx = (int) Math.min(255L, (b * scale) >>> 16);
                    int tol = lut[lutIdx];
                    int w;
                    if (diff <= tol) {
                        w = 256;
                    } else {
                        int maxDiff = tol << 1;
                        w = diff < maxDiff ? ((maxDiff - diff) << 8) / tol : 0;
                    }
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

            final int[] shift = estimateRgbShift(basePixels, pixels, width, height);
            final int dx = shift[0];
            final int dy = shift[1];
            final int[] base = basePixels;
            final int[] sR = sumR;
            final int[] sG = sumG;
            final int[] sB = sumB;
            final short[] wtSum = weightSum;
            final int[] lut = tolLut;
            final int xStart = Math.max(0, -dx);
            final int xEnd = Math.min(width, width - dx);

            IntStream.range(0, height).parallel().forEach(y -> {
                int sy = y + dy;
                if (sy < 0 || sy >= height) return;
                int baseRow = y * width;
                int srcRow = sy * width;
                for (int x = xStart; x < xEnd; x++) {
                    int idx = baseRow + x;
                    int bc = base[idx];
                    int sc = pixels[srcRow + x + dx];
                    int br = (bc >> 16) & 0xff;
                    int bg = (bc >> 8) & 0xff;
                    int bb = bc & 0xff;
                    int sr = (sc >> 16) & 0xff;
                    int sg = (sc >> 8) & 0xff;
                    int sb = sc & 0xff;
                    int diff = Math.max(Math.abs(sr - br), Math.max(Math.abs(sg - bg), Math.abs(sb - bb)));
                    int luma = Math.min(255, (br * 54 + bg * 183 + bb * 19) >> 8);
                    int tol = lut[luma];
                    int w;
                    if (diff <= tol) {
                        w = 256;
                    } else {
                        int maxDiff = tol << 1;
                        w = diff < maxDiff ? ((maxDiff - diff) << 8) / tol : 0;
                    }
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
