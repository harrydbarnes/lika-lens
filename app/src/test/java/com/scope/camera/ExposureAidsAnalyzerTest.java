package com.scope.camera;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Unit tests for ExposureAidsAnalyzer histogram, zebra, peaking, and dirty-lens clean-window gating. */
public class ExposureAidsAnalyzerTest {

    @Test
    public void testHistogramAndClippingFlags() {
        ExposureAidsAnalyzer analyzer = new ExposureAidsAnalyzer();
        int w = 64, h = 48;
        int[] px = new int[w * h];
        // Half pure white (clipped highlight), half pure black (clipped shadow)
        Arrays.fill(px, 0, px.length / 2, 0xffffffff);
        Arrays.fill(px, px.length / 2, px.length, 0xff000000);

        analyzer.analyzePixels(px, w, h, true, true, false, false, false, true, false, 1000L, null);

        int[] hist = new int[ExposureAidsAnalyzer.HIST_BINS];
        int[] r = new int[ExposureAidsAnalyzer.HIST_BINS];
        int[] g = new int[ExposureAidsAnalyzer.HIST_BINS];
        int[] b = new int[ExposureAidsAnalyzer.HIST_BINS];
        boolean[] zebra = new boolean[ExposureAidsAnalyzer.GRID_SIZE * ExposureAidsAnalyzer.GRID_SIZE];

        int flags = analyzer.copyForDraw(true, zebra, false, null, true, hist, r, g, b);
        assertEquals(3, flags); // both shadow (1) and highlight (2) clipped
        assertEquals(px.length / 2, hist[0]);
        assertEquals(px.length / 2, hist[ExposureAidsAnalyzer.HIST_BINS - 1]);
        assertTrue("Top row should trigger zebra", zebra[0]);
        assertFalse("Bottom row should not trigger zebra", zebra[zebra.length - 1]);
    }

    @Test
    public void testDirtyLensStopsPollingAfterCleanWindowAndResetsOnCameraSwitch() {
        ExposureAidsAnalyzer analyzer = new ExposureAidsAnalyzer();
        int w = 64, h = 48;
        int[] sharpPx = new int[w * h];
        // High-contrast checkerboard -> clean lens (high gradient)
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                sharpPx[y * w + x] = ((x + y) & 1) == 0 ? 0xff202020 : 0xffd0d0d0;
            }
        }

        for (int i = 0; i < ExposureAidsAnalyzer.DIRTY_LENS_CLEAN_LIMIT; i++) {
            assertTrue(analyzer.shouldCheckDirtyLens("0", true, false));
            analyzer.analyzePixels(sharpPx, w, h, false, false, false, true, false, false, false, 1000L + i * 2000L, null);
        }
        assertFalse("Should stop polling once clean window completes",
                analyzer.shouldCheckDirtyLens("0", true, false));
        assertTrue("Switching cameraId must reset the clean window",
                analyzer.shouldCheckDirtyLens("2", true, false));
        assertFalse("Front camera should never check dirty lens",
                analyzer.shouldCheckDirtyLens("1", true, true));
    }

    @Test
    public void testSkinTonePixelHeuristic() {
        assertTrue(ExposureAidsAnalyzer.isSkinTonePixel(190, 140, 110));
        assertFalse(ExposureAidsAnalyzer.isSkinTonePixel(40, 120, 200));
        assertFalse(ExposureAidsAnalyzer.isSkinTonePixel(120, 120, 120));
    }
}
