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

    private static Rect makeRect(int left, int top, int right, int bottom) {
        Rect r = new Rect();
        r.left = left;
        r.top = top;
        r.right = right;
        r.bottom = bottom;
        return r;
    }

    @Test
    public void testComputeCropRect() {
        assertNotNull(CameraExposureHelper.computeCropRect(null, 1.0f));
        assertNotNull(CameraExposureHelper.computeCropRect(null, 2.0f));
        Rect active = makeRect(0, 0, 4000, 3000);
        Rect crop = CameraExposureHelper.computeCropRect(active, 2.0f);
        assertEquals(1000, crop.left);
        assertEquals(750, crop.top);
        assertEquals(3000, crop.right);
        assertEquals(2250, crop.bottom);
    }

    @Test
    public void testComputeMeteringRectBoundsAndCentering() {
        Rect cropRegion = makeRect(1000, 750, 3000, 2250); // 2x digital crop on 4000x3000 sensor
        // Center tap (0.5, 0.5)
        Rect centerRect = CameraExposureHelper.computeMeteringRect(cropRegion, 0.5f, 0.5f, false, 0.10f);
        assertNotNull(centerRect);
        assertTrue("Left must be inside crop region", centerRect.left >= cropRegion.left);
        assertTrue("Top must be inside crop region", centerRect.top >= cropRegion.top);
        assertTrue("Right must be inside crop region", centerRect.right <= cropRegion.right);
        assertTrue("Bottom must be inside crop region", centerRect.bottom <= cropRegion.bottom);
        int cropCenterX = (cropRegion.left + cropRegion.right) / 2;
        int cropCenterY = (cropRegion.top + cropRegion.bottom) / 2;
        int centerRectX = (centerRect.left + centerRect.right) / 2;
        int centerRectY = (centerRect.top + centerRect.bottom) / 2;
        assertEquals(cropCenterX, centerRectX);
        assertEquals(cropCenterY, centerRectY);

        // Corner tap (0.0, 0.0) - must clamp cleanly within crop region
        Rect cornerRect = CameraExposureHelper.computeMeteringRect(cropRegion, 0.0f, 0.0f, false, 0.10f);
        assertTrue("Corner tap left >= crop left", cornerRect.left >= cropRegion.left);
        assertTrue("Corner tap top >= crop top", cornerRect.top >= cropRegion.top);
        assertTrue("Corner tap right <= crop right", cornerRect.right <= cropRegion.right);
        assertTrue("Corner tap bottom <= crop bottom", cornerRect.bottom <= cropRegion.bottom);
    }

    @Test
    public void testComputeMeteringRectFrontCameraMirroring() {
        Rect cropRegion = makeRect(0, 0, 4000, 3000);
        // Tapping right side of screen (nx = 0.8f) on rear vs front camera
        Rect rearRect = CameraExposureHelper.computeMeteringRect(cropRegion, 0.8f, 0.5f, false, 0.10f);
        Rect frontRect = CameraExposureHelper.computeMeteringRect(cropRegion, 0.8f, 0.5f, true, 0.10f);

        // Rear camera maps nx=0.8 to (1 - 0.8)*3000 = 600
        // Front camera (mirrored) maps nx=0.8 to 0.8*3000 = 2400
        int rearCenterY = (rearRect.top + rearRect.bottom) / 2;
        int frontCenterY = (frontRect.top + frontRect.bottom) / 2;
        assertTrue("Rear sensorY should be in lower third", rearCenterY < 1000);
        assertTrue("Front sensorY should be in upper third", frontCenterY > 2000);
    }
}
