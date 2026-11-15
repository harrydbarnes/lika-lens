package com.scope.camera;

import android.graphics.Rect;
import android.media.AudioManager;
import android.util.Range;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
    @Test public void automaticNightIntegrationRequiresDimLightAndPreservesMeteredBrightness() {
        assertFalse(CameraExposureHelper.shouldUseNightIntegration("Auto",true,false,10_000_000L,100));
        assertFalse(CameraExposureHelper.shouldUseNightIntegration("Auto",false,false,33_000_000L,1600));
        assertFalse(CameraExposureHelper.shouldUseNightIntegration("Auto",true,true,33_000_000L,1600));
        assertTrue(CameraExposureHelper.shouldUseNightIntegration("Auto",true,false,33_000_000L,1600));
        assertTrue(CameraExposureHelper.shouldUseNightIntegration("Always",false,false,10_000_000L,100));
        assertFalse(CameraExposureHelper.shouldUseNightIntegration("Off",true,false,33_000_000L,1600));
        assertFalse(CameraExposureHelper.shouldUseNightIntegration("Auto",true,false,33_000_000L,1600,"Auto"));
        assertFalse(CameraExposureHelper.shouldUseNightIntegration("Auto",true,false,33_000_000L,1600,"Torch"));
        assertFalse(CameraExposureHelper.shouldUseNightIntegration("Always",true,false,33_000_000L,1600,"Torch"));
        assertTrue(CameraExposureHelper.shouldUseNightIntegration("Auto",true,false,33_000_000L,1600,"Off"));
        assertEquals(132_000_000L,CameraExposureHelper.matchedNightExposure(33_000_000L,800,200,2_000_000_000L));
        assertEquals(2_000_000_000L,CameraExposureHelper.matchedNightExposure(500_000_000L,3200,200,2_000_000_000L));
    }

    @Test public void computeNightStackParametersCapsShutterAndScalesIso() {
        long[] modDim = CameraExposureHelper.computeNightStackParameters(
                "Auto", 33_000_000L, 800, 100_000L, 4_000_000_000L, 50, 6400);
        assertEquals(132_000_000L, modDim[0]);
        assertEquals(200L, modDim[1]);
        assertEquals(CameraExposureHelper.NIGHT_STACK_FRAME_COUNT, modDim[2]);

        long[] veryDark = CameraExposureHelper.computeNightStackParameters(
                "Auto", 500_000_000L, 1600, 100_000L, 4_000_000_000L, 50, 6400);
        assertEquals(CameraExposureHelper.MAX_NIGHT_STACK_FRAME_NS, veryDark[0]);
        assertEquals(800L, veryDark[1]);
        assertEquals(CameraExposureHelper.NIGHT_STACK_FRAME_COUNT, veryDark[2]);

        long[] uncalibrated = CameraExposureHelper.computeNightStackParameters(
                "Always", 0L, 0, 100_000L, 2_000_000_000L, 100, 3200);
        assertEquals(CameraExposureHelper.MAX_NIGHT_STACK_FRAME_NS, uncalibrated[0]);
        assertEquals(200L, uncalibrated[1]);
        assertEquals(CameraExposureHelper.NIGHT_STACK_FRAME_COUNT, uncalibrated[2]);
    }

    @Test
    public void wideZoomDropsLogicalRawAndRestoresAboveThreshold() {
        assertTrue(CameraExposureHelper.shouldUseJpegForWideZoom(true, 0.5f));
        assertTrue(CameraExposureHelper.shouldUseJpegForWideZoom(true, 0.7f));
        assertFalse(CameraExposureHelper.shouldUseJpegForWideZoom(true, 0.99f));
        assertFalse(CameraExposureHelper.shouldUseJpegForWideZoom(true, 1.0f));
        assertFalse(CameraExposureHelper.shouldUseJpegForWideZoom(true, 5.0f));
        assertFalse(CameraExposureHelper.shouldUseJpegForWideZoom(false, 0.5f));
    }

    @Test
    public void seekBarRoundingNearOneNormalizesForRawSession() {
        float sliderOne = CameraExposureHelper.zoomForProgress(CameraExposureHelper.zoomProgressFor(1.0f));
        assertEquals(1.0f, CameraExposureHelper.resolveZoomForSessionMode(sliderOne, false), 0.0001f);
        assertEquals(0.5f, CameraExposureHelper.resolveZoomForSessionMode(0.5f, true), 0.0001f);
        assertEquals(2.0f, CameraExposureHelper.resolveZoomForSessionMode(2.0f, false), 0.0001f);
    }

    @Test
    public void sameCameraStreamModeChangeTriggersSessionReconfig() {
        assertTrue(CameraExposureHelper.needsPreviewSessionReconfig("0", "0", false, true));
        assertTrue(CameraExposureHelper.needsPreviewSessionReconfig("0", "0", true, false));
        assertFalse(CameraExposureHelper.needsPreviewSessionReconfig("0", "0", false, false));
        assertFalse(CameraExposureHelper.needsPreviewSessionReconfig("0", "0", true, true));
        assertFalse(CameraExposureHelper.needsPreviewSessionReconfig("0", "1", false, true));
        assertFalse(CameraExposureHelper.needsPreviewSessionReconfig(null, "0", false, true));
    }

    @Test
    public void wideZoomRangeAndCapabilityResolution() {
        assertEquals(0.5f, CameraExposureHelper.resolveWideZoomRatio(0.5f, 30.0f, 0.5f), 0.0001f);
        assertEquals(0.58f, CameraExposureHelper.resolveWideZoomRatio(0.58f, 30.0f, 0.5f), 0.0001f);
        assertEquals(-1f, CameraExposureHelper.resolveWideZoomRatio(1.0f, 10.0f, 0.5f), 0.0001f);
        assertEquals(-1f, CameraExposureHelper.resolveWideZoomRatio(null, null, 0.5f), 0.0001f);

        assertTrue(CameraExposureHelper.canZoomOnCamera(true, 0.5f, 30.0f, true, 0.5f));
        assertTrue(CameraExposureHelper.canZoomOnCamera(true, 0.58f, 30.0f, false, 0.5f));
        assertFalse(CameraExposureHelper.canZoomOnCamera(true, 1.0f, 10.0f, true, 0.5f));
        assertTrue(CameraExposureHelper.canZoomOnCamera(true, 1.0f, 10.0f, true, 5.0f));
        assertFalse(CameraExposureHelper.canZoomOnCamera(true, null, null, true, 0.5f));
        assertTrue(CameraExposureHelper.canZoomOnCamera(true, null, null, true, 1.0f));
        assertFalse(CameraExposureHelper.canZoomOnCamera(false, 0.5f, 30.0f, true, 0.5f));
    }

    @Test
    public void preReconfigAnimationTargetBridgesStreamTransitions() {
        assertEquals(1.0f, CameraExposureHelper.preReconfigAnimationTarget(true, false, 0.5f, 1.0f), 0.0001f);
        assertEquals(2.0f, CameraExposureHelper.preReconfigAnimationTarget(true, false, 0.5f, 2.0f), 0.0001f);
        assertEquals(1.0f, CameraExposureHelper.preReconfigAnimationTarget(false, true, 5.0f, 0.5f), 0.0001f);
        assertEquals(-1f, CameraExposureHelper.preReconfigAnimationTarget(false, true, 1.0f, 0.5f), 0.0001f);
        assertEquals(-1f, CameraExposureHelper.preReconfigAnimationTarget(false, false, 1.0f, 2.0f), 0.0001f);
        assertEquals(-1f, CameraExposureHelper.preReconfigAnimationTarget(true, true, 0.5f, 0.7f), 0.0001f);
    }

    @Test
    public void shutterSoundRespectsRingerModeUnlessPlatformMandatory() {
        assertTrue(CameraExposureHelper.shouldPlayShutterSound(true, AudioManager.RINGER_MODE_NORMAL, false));
        assertFalse(CameraExposureHelper.shouldPlayShutterSound(false, AudioManager.RINGER_MODE_NORMAL, false));
        assertFalse(CameraExposureHelper.shouldPlayShutterSound(true, AudioManager.RINGER_MODE_VIBRATE, false));
        assertFalse(CameraExposureHelper.shouldPlayShutterSound(true, AudioManager.RINGER_MODE_SILENT, false));
        assertTrue(CameraExposureHelper.shouldPlayShutterSound(false, AudioManager.RINGER_MODE_SILENT, true));
    }

    @Test
    public void shutterAndIsoSupportChecksRespectHardwareRanges() {
        assertTrue(CameraExposureHelper.isShutterNanosecondsSupported(1_000_000L, 100_000L, 1_000_000_000L));
        assertFalse(CameraExposureHelper.isShutterNanosecondsSupported(250_000L, 1_000_000L, 1_000_000_000L));
        assertTrue(CameraExposureHelper.isIsoSupported(100, 100, 3200));
        assertFalse(CameraExposureHelper.isIsoSupported(50, 100, 3200));
        assertFalse(CameraExposureHelper.isIsoSupported(6400, 100, 3200));
    }

    @Test
    public void defaultLensAndNonRawTeleResolutionPreserveMainCameraId() {
        List<CameraExposureHelper.LensCandidate> cameras = Arrays.asList(
                new CameraExposureHelper.LensCandidate("0", true, true, 12_000_000L, 5.0f, 0.5f, 10f),
                new CameraExposureHelper.LensCandidate("1", false, false, 12_000_000L, 3.0f, 1.0f, 2.0f),
                new CameraExposureHelper.LensCandidate("2", true, false, 12_000_000L, 2.5f, null, null),
                new CameraExposureHelper.LensCandidate("3", true, false, 10_000_000L, 15.0f, null, null)
        );

        CameraExposureHelper.StartupLensChoice wideAuto =
                CameraExposureHelper.resolveStartupLens(cameras, "Auto / Wide 1×", "1");
        assertEquals("0", wideAuto.mainCameraId);
        assertEquals("0", wideAuto.activeCameraId);
        assertEquals(1.0f, wideAuto.initialZoom, 0.001f);

        CameraExposureHelper.StartupLensChoice lastFront =
                CameraExposureHelper.resolveStartupLens(cameras, "Last used", "1");
        assertEquals("0", lastFront.mainCameraId);
        assertEquals(5.0f, lastFront.primaryFocalLength, 0.001f);
        assertEquals("1", lastFront.activeCameraId);
        assertEquals(1.0f, lastFront.initialZoom, 0.001f);

        CameraExposureHelper.StartupLensChoice ultraWide =
                CameraExposureHelper.resolveStartupLens(cameras, "Ultra-wide .5×", "0");
        assertEquals("0", ultraWide.mainCameraId);
        assertEquals("0", ultraWide.activeCameraId);
        assertEquals(0.5f, ultraWide.initialZoom, 0.001f);
        assertTrue(ultraWide.forceJpegForWide);

        CameraExposureHelper.StartupLensChoice tele =
                CameraExposureHelper.resolveStartupLens(cameras, "Telephoto 2×/5×", "0");
        assertEquals("0", tele.mainCameraId);
        assertEquals("3", tele.activeCameraId);
        assertEquals(3.0f, tele.physicalFocalFactor, 0.001f);

        // Non-RAW physical tele must still be selectable during continuous zoom
        CameraExposureHelper.LensCandidate primary = cameras.get(0);
        CameraExposureHelper.LensCandidate picked =
                CameraExposureHelper.selectContinuousPhysicalLens(cameras, primary, 3.5f);
        assertEquals("3", picked.id);
    }

    @Test
    public void wideHandoffStartsSessionAtOneThenGlidesToTarget() {
        assertEquals(1.0f, CameraExposureHelper.sessionInitialZoomForReconfig(true, 1.0f, 0.5f, false), 0.0001f);
        assertEquals(0.5f, CameraExposureHelper.postReconfigZoomTarget(true, 1.0f, 0.5f, false), 0.0001f);

        assertEquals(0.96f, CameraExposureHelper.sessionInitialZoomForReconfig(true, 1.0f, 0.96f, true), 0.0001f);
        assertEquals(-1f, CameraExposureHelper.postReconfigZoomTarget(true, 0.96f, 0.96f, true), 0.0001f);

        assertEquals(1.0f, CameraExposureHelper.sessionInitialZoomForReconfig(false, 1.0f, 1.0f, false), 0.0001f);
        assertEquals(-1f, CameraExposureHelper.postReconfigZoomTarget(false, 1.0f, 1.0f, false), 0.0001f);
    }

}
