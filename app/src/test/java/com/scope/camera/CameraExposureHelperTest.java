package com.scope.camera;

import android.graphics.Rect;
import android.util.Range;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for CameraExposureHelper verifying exposure conversions,
 * ISO parsing, zoom interpolation, and digital crop mathematics.
 */
public class CameraExposureHelperTest {

    @Test
    public void testShutterNanosecondsConversion() {
        assertEquals(33_333_333L, CameraExposureHelper.shutterNanoseconds("1/30 sec"));
        assertEquals(16_666_667L, CameraExposureHelper.shutterNanoseconds("1/60 sec"));
        assertEquals(8_333_333L, CameraExposureHelper.shutterNanoseconds("1/120 sec"));
        assertEquals(4_166_667L, CameraExposureHelper.shutterNanoseconds("1/240 sec"));
        assertEquals(2_000_000L, CameraExposureHelper.shutterNanoseconds("1/500 sec"));
        assertEquals(1_000_000L, CameraExposureHelper.shutterNanoseconds("1/1000 sec"));
    }

    @Test
    public void testFormatShutter() {
        assertEquals("AUTO", CameraExposureHelper.formatShutter(0));
        assertEquals("1/120", CameraExposureHelper.formatShutter(8_333_333L));
        assertEquals("1/60", CameraExposureHelper.formatShutter(16_666_667L));
        assertEquals("1/1000", CameraExposureHelper.formatShutter(1_000_000L));
        assertEquals("1.0s", CameraExposureHelper.formatShutter(1_000_000_000L));
    }

    @Test
    public void testParseIso() {
        assertEquals(100, CameraExposureHelper.parseIso("ISO 100"));
        assertEquals(400, CameraExposureHelper.parseIso("ISO 400"));
        assertEquals(6400, CameraExposureHelper.parseIso("ISO 6400"));
        assertEquals(800, CameraExposureHelper.parseIso("800"));
        assertEquals(100, CameraExposureHelper.parseIso(null));
        assertEquals(100, CameraExposureHelper.parseIso("Invalid"));
    }

    @Test
    public void testClampExposureAndSensitivity() {
        assertEquals(500L, CameraExposureHelper.clampExposureTime(500L, null));
        assertEquals(200_000_000L, CameraExposureHelper.clampExposureTime(200_000_000L, null));
        assertEquals(50, CameraExposureHelper.clampSensitivity(50, null));
        assertEquals(800, CameraExposureHelper.clampSensitivity(800, null));
    }

    @Test
    public void testZoomProgressMapping() {
        int pMin = CameraExposureHelper.zoomProgressFor(CameraExposureHelper.MIN_ZOOM);
        assertEquals(0, pMin);

        int pMax = CameraExposureHelper.zoomProgressFor(CameraExposureHelper.MAX_ZOOM);
        assertEquals(CameraExposureHelper.ZOOM_SEEKBAR_MAX, pMax);

        float z1 = CameraExposureHelper.zoomForProgress(CameraExposureHelper.zoomProgressFor(1.0f));
        assertTrue("Zoom 1.0x round trip: " + z1, Math.abs(z1 - 1.0f) < 0.05f);

        float z2 = CameraExposureHelper.zoomForProgress(CameraExposureHelper.zoomProgressFor(2.0f));
        assertTrue("Zoom 2.0x round trip: " + z2, Math.abs(z2 - 2.0f) < 0.05f);
    }

    @Test
    public void testComputeCropRect() {
        assertNull(CameraExposureHelper.computeCropRect(null, 1.0f));
        assertNull(CameraExposureHelper.computeCropRect(null, 2.0f));
    }
}
