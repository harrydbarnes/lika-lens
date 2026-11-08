package com.scope.camera;

import android.graphics.Rect;
import android.util.Range;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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
        Range<Long> expRange = new Range<>(1_000L, 100_000_000L);
        assertEquals(1_000L, CameraExposureHelper.clampExposureTime(500L, expRange));
        assertEquals(100_000_000L, CameraExposureHelper.clampExposureTime(200_000_000L, expRange));
        assertEquals(5_000_000L, CameraExposureHelper.clampExposureTime(5_000_000L, expRange));

        Range<Integer> isoRange = new Range<>(100, 3200);
        assertEquals(100, CameraExposureHelper.clampSensitivity(50, isoRange));
        assertEquals(3200, CameraExposureHelper.clampSensitivity(6400, isoRange));
        assertEquals(800, CameraExposureHelper.clampSensitivity(800, isoRange));
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
        Rect sensor = new Rect(0, 0, 4000, 3000);
        Rect crop1x = CameraExposureHelper.computeCropRect(sensor, 1.0f);
        assertEquals(sensor, crop1x);

        Rect crop2x = CameraExposureHelper.computeCropRect(sensor, 2.0f);
        assertNotNull(crop2x);
        assertEquals(2000, crop2x.width());
        assertEquals(1500, crop2x.height());
        assertEquals(1000, crop2x.left);
        assertEquals(750, crop2x.top);
    }
}
