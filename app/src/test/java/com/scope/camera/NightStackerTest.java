package com.scope.camera;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class NightStackerTest {

    @Test
    public void burstCapturePairsByTimestampAndGracefullyHandlesPartialFailure() {
        InFlightBurstCapture<String, String> burst = new InFlightBurstCapture<>(1L, 4, true, null);
        List<String> disposed = new ArrayList<>();

        // Out-of-order result arrives before image for frame 0, then image 1 arrives before result 1.
        assertNull(burst.attachResult("R0", 100L, disposed::add));
        assertNull(burst.attachImage("I1", 200L, disposed::add));

        InFlightBurstCapture.BurstStep<String, String> step0 = burst.attachImage("I0", 100L, disposed::add);
        assertNotNull(step0);
        assertEquals("I0", step0.image);
        assertEquals("R0", step0.result);
        assertEquals(0, step0.frameIndex);
        assertEquals(1, step0.completedCount);
        assertFalse(step0.isLast);
        assertFalse(step0.failed);

        InFlightBurstCapture.BurstStep<String, String> step1 = burst.attachResult("R1", 200L, disposed::add);
        assertNotNull(step1);
        assertEquals("I1", step1.image);
        assertEquals("R1", step1.result);
        assertEquals(1, step1.frameIndex);
        assertEquals(2, step1.completedCount);
        assertFalse(step1.isLast);

        // One frame drops out, then frame 2 succeeds -> finishes cleanly with 3 paired frames.
        assertNull(burst.markFrameFailed(disposed::add));
        assertNull(burst.attachImage("I2", 300L, disposed::add));
        InFlightBurstCapture.BurstStep<String, String> step2 = burst.attachResult("R2", 300L, disposed::add);
        assertNotNull(step2);
        assertEquals("I2", step2.image);
        assertEquals("R2", step2.result);
        assertEquals(3, step2.completedCount);
        assertTrue(step2.isLast);
        assertFalse(step2.failed);
        assertFalse(burst.isActive());

        // Late image arriving after completion is disposed immediately.
        assertNull(burst.attachImage("LATE", 400L, disposed::add));
        assertEquals(1, disposed.size());
        assertEquals("LATE", disposed.get(0));
    }

    @Test
    public void burstCaptureCancelDisposesPendingImagesAndAllFailedReportsFailure() {
        InFlightBurstCapture<String, String> cancelledBurst = new InFlightBurstCapture<>(2L, 4, true, null);
        List<String> disposed = new ArrayList<>();
        assertNull(cancelledBurst.attachImage("PENDING_IMG", 100L, disposed::add));
        cancelledBurst.cancel(disposed::add);
        assertFalse(cancelledBurst.isActive());
        assertEquals(1, disposed.size());
        assertEquals("PENDING_IMG", disposed.get(0));

        InFlightBurstCapture<String, String> failedBurst = new InFlightBurstCapture<>(3L, 2, true, null);
        assertNull(failedBurst.markFrameFailed(disposed::add));
        InFlightBurstCapture.BurstStep<String, String> finalFail = failedBurst.markFrameFailed(disposed::add);
        assertNotNull(finalFail);
        assertTrue(finalFail.isLast);
        assertTrue(finalFail.failed);
        assertEquals(0, finalFail.completedCount);
    }

    @Test
    public void rawAccumulatorReducesNoisePreservesCfaAndRejectsHotPixelOutliers() {
        int w = 128;
        int h = 128;
        int white = 4095;
        short[] clean = new short[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                // Checkerboard Bayer pattern + smooth gradient feature so alignment locks accurately.
                int baseVal = 600 + (x * 4) + (y * 3);
                int cfaOffset = ((x & 1) == 0 && (y & 1) == 0) ? 200 : ((x & 1) == 1 && (y & 1) == 1) ? -120 : 40;
                clean[y * w + x] = (short) (baseVal + cfaOffset);
            }
        }

        NightStacker.RawAccumulator acc = new NightStacker.RawAccumulator(w, h, white);
        Random rng = new Random(42L);
        double singleFrameSqErr = 0.0;
        int interiorCount = 0;

        for (int f = 0; f < 4; f++) {
            int dx = (f == 1) ? 4 : (f == 2) ? -2 : 0; // Even Bayer shifts preserve CFA phase
            int dy = (f == 1) ? -2 : (f == 2) ? 4 : 0;
            short[] frame = new short[w * h];
            for (int y = 0; y < h; y++) {
                int srcY = Math.max(0, Math.min(h - 1, y - dy));
                for (int x = 0; x < w; x++) {
                    int srcX = Math.max(0, Math.min(w - 1, x - dx));
                    int val = (clean[srcY * w + srcX] & 0xffff) + Math.round((float) rng.nextGaussian() * 45f);
                    val = Math.max(0, Math.min(white, val));
                    frame[y * w + x] = (short) val;
                }
            }
            // Inject transient hot-pixel burst outlier into frame 1 only at (64, 64).
            if (f == 1) {
                int shiftedX = 64 + dx;
                int shiftedY = 64 + dy;
                frame[shiftedY * w + shiftedX] = (short) white;
            }
            if (f == 0) {
                for (int y = 20; y < h - 20; y++) {
                    for (int x = 20; x < w - 20; x++) {
                        int err = (frame[y * w + x] & 0xffff) - (clean[y * w + x] & 0xffff);
                        singleFrameSqErr += err * (double) err;
                        interiorCount++;
                    }
                }
            }
            acc.addFrame(frame);
        }

        assertEquals(4, acc.frameCount());
        short[] stacked = acc.finishShorts();
        assertEquals(w * h, stacked.length);

        double stackedSqErr = 0.0;
        for (int y = 20; y < h - 20; y++) {
            for (int x = 20; x < w - 20; x++) {
                int err = (stacked[y * w + x] & 0xffff) - (clean[y * w + x] & 0xffff);
                stackedSqErr += err * (double) err;
            }
        }
        double singleRmse = Math.sqrt(singleFrameSqErr / interiorCount);
        double stackedRmse = Math.sqrt(stackedSqErr / interiorCount);
        assertTrue("Expected stacked RMSE (" + stackedRmse + ") < 65% of single-frame RMSE (" + singleRmse + ")",
                stackedRmse < singleRmse * 0.65);

        // Outlier at (64, 64) should stay close to clean signal rather than spiking toward white level.
        int cleanCenter = clean[64 * w + 64] & 0xffff;
        int stackedCenter = stacked[64 * w + 64] & 0xffff;
        assertTrue("Hot pixel outlier should be rejected at (64,64): got " + stackedCenter + " vs clean " + cleanCenter,
                Math.abs(stackedCenter - cleanCenter) < 120);
    }

    @Test
    public void bayerShiftRespectsEvenCfaParityAndRoundTripsBuffer() {
        int w = 128;
        int h = 128;
        short[] base = new short[w * h];
        short[] shifted = new short[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double dx = (x - 58.0) / 18.0;
                double dy = (y - 66.0) / 14.0;
                int spot = (int) Math.round(1400.0 * Math.exp(-(dx * dx + dy * dy)));
                int ramp = x * 5 + y * 3;
                base[y * w + x] = (short) (200 + ramp + spot);
            }
        }
        int dxShift = 6;
        int dyShift = -4;
        for (int y = 0; y < h; y++) {
            int by = Math.max(0, Math.min(h - 1, y - dyShift));
            for (int x = 0; x < w; x++) {
                int bx = Math.max(0, Math.min(w - 1, x - dxShift));
                shifted[y * w + x] = base[by * w + bx];
            }
        }
        int[] detected = NightStacker.estimateBayerShift(base, shifted, w, h);
        assertEquals(0, detected[0] & 1);
        assertEquals(0, detected[1] & 1);
        assertEquals(dxShift, detected[0]);
        assertEquals(dyShift, detected[1]);

        ByteBuffer packed = ByteBuffer.allocate(w * 2 * h).order(ByteOrder.LITTLE_ENDIAN);
        for (short s : base) packed.putShort(s);
        packed.position(0);
        short[] unpacked = NightStacker.extractRawShorts(packed, w, h, w * 2, 2);
        assertArrayEquals(base, unpacked);
    }

    @Test
    public void rgbAccumulatorAlignsAndSuppressesNoise() {
        int w = 120;
        int h = 120;
        int[] clean = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int r = Math.min(255, 40 + (x * 160) / w + (((x / 10) ^ (y / 10)) & 1) * 30);
                int g = Math.min(255, 50 + (y * 150) / h);
                int b = 90;
                clean[y * w + x] = 0xff000000 | (r << 16) | (g << 8) | b;
            }
        }
        NightStacker.RgbAccumulator acc = new NightStacker.RgbAccumulator(w, h);
        Random rng = new Random(7L);
        double singleSqErr = 0.0;
        int count = 0;
        for (int f = 0; f < 4; f++) {
            int dx = f == 2 ? 3 : 0;
            int dy = f == 2 ? -2 : 0;
            int[] frame = new int[w * h];
            for (int y = 0; y < h; y++) {
                int sy = Math.max(0, Math.min(h - 1, y - dy));
                for (int x = 0; x < w; x++) {
                    int sx = Math.max(0, Math.min(w - 1, x - dx));
                    int c = clean[sy * w + sx];
                    int r = clampByte(((c >> 16) & 0xff) + Math.round((float) rng.nextGaussian() * 14f));
                    int g = clampByte(((c >> 8) & 0xff) + Math.round((float) rng.nextGaussian() * 14f));
                    int b = clampByte((c & 0xff) + Math.round((float) rng.nextGaussian() * 14f));
                    frame[y * w + x] = 0xff000000 | (r << 16) | (g << 8) | b;
                }
            }
            if (f == 0) {
                for (int y = 20; y < h - 20; y++) {
                    for (int x = 20; x < w - 20; x++) {
                        int d = ((frame[y * w + x] >> 16) & 0xff) - ((clean[y * w + x] >> 16) & 0xff);
                        singleSqErr += d * (double) d;
                        count++;
                    }
                }
            }
            acc.addFrame(frame);
        }
        int[] stacked = acc.finishPixels();
        double stackedSqErr = 0.0;
        for (int y = 20; y < h - 20; y++) {
            for (int x = 20; x < w - 20; x++) {
                int d = ((stacked[y * w + x] >> 16) & 0xff) - ((clean[y * w + x] >> 16) & 0xff);
                stackedSqErr += d * (double) d;
            }
        }
        assertTrue(Math.sqrt(stackedSqErr / count) < Math.sqrt(singleSqErr / count) * 0.65);
    }

    private static int clampByte(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
