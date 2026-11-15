package com.scope.camera;

import android.util.Size;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertArrayEquals;

public class VideoRecordingCoordinatorTest {

    @Test public void correctsOppositeSensorMountsInBothSwitchDirections() {
        assertEquals(180, VideoRecordingCoordinator.rotationCorrection(270, 90));
        assertEquals(180, VideoRecordingCoordinator.rotationCorrection(90, 270));
        assertEquals(0, VideoRecordingCoordinator.rotationCorrection(90, 90));
        assertEquals(0, VideoRecordingCoordinator.rotationCorrection(0, 0));
        assertEquals(270, VideoRecordingCoordinator.rotationCorrection(0, 90));
        assertEquals(90, VideoRecordingCoordinator.rotationCorrection(90, 0));
    }

    @Test public void removesFrontCameraPreviewTransformBeforeRecordingRotation() {
        float[] frontPreview = {0,-1,0,0, 1,0,0,0, 0,0,1,0, 0,1,0,1};
        float[] recording = new float[16];
        VideoRecordingCoordinator.recordingTextureMatrix(frontPreview, recording);
        assertArrayEquals(new float[]{1,0,0,0, 0,-1,0,0, 0,0,1,0, 0,1,0,1}, recording, .0001f);
    }

    @Test public void preservesCropWhenRemovingProducerRotation() {
        float[] rotatedCrop = {0,-.6f,0,0, .8f,0,0,0, 0,0,1,0, .1f,.8f,0,1};
        float[] recording = new float[16];
        VideoRecordingCoordinator.recordingTextureMatrix(rotatedCrop, recording);
        assertArrayEquals(new float[]{.8f,0,0,0, 0,-.6f,0,0, 0,0,1,0, .1f,.8f,0,1}, recording, .0001f);
    }

    @Test public void halfTurnRotatesPixelsWithoutMirroringOrChangingFrameSize() {
        assertArrayEquals(new float[]{1, 1, -1, 1, 1, -1, -1, -1},
                VideoRecordingCoordinator.recordingVertices(180, 1920, 1080), .0001f);
        assertArrayEquals(new float[]{-1, -1, 1, -1, -1, 1, 1, 1},
                VideoRecordingCoordinator.recordingVertices(0, 1920, 1080), .0001f);
    }

    @Test public void quarterTurnFitsTheRotatedAspectWithoutStretching() {
        float x = (1080f / 1920) * (1080f / 1920);
        assertArrayEquals(new float[]{-x, 1, -x, -1, x, 1, x, -1},
                VideoRecordingCoordinator.recordingVertices(90, 1920, 1080), .0001f);
    }

    @Test
    public void testComputeTargetFps() {
        assertEquals(60, VideoRecordingCoordinator.computeTargetFps("60 fps (Smooth)"));
        assertEquals(30, VideoRecordingCoordinator.computeTargetFps("30 fps (Standard)"));
        assertEquals(24, VideoRecordingCoordinator.computeTargetFps("24 fps (Cinematic)"));
        assertEquals(24, VideoRecordingCoordinator.computeTargetFps(null));
        assertEquals(24, VideoRecordingCoordinator.computeTargetFps("Unknown"));
    }

    @Test
    public void testComputeBitrate() {
        assertEquals(35_000_000, VideoRecordingCoordinator.computeBitrate("Ultra · 35 Mbps"));
        assertEquals(14_000_000, VideoRecordingCoordinator.computeBitrate("Standard · 14 Mbps"));
        assertEquals(24_000_000, VideoRecordingCoordinator.computeBitrate("High · 24 Mbps"));
        assertEquals(24_000_000, VideoRecordingCoordinator.computeBitrate(null));
    }

    @Test
    public void testFormatProfileLabel() {
        assertEquals("1080P / 24 FPS CINEMATIC",
                VideoRecordingCoordinator.formatProfileLabel(null, "24 fps (Cinematic)", "16:9"));

        assertEquals("1080P / 60 FPS",
                VideoRecordingCoordinator.formatProfileLabel(null, "60 fps", "16:9"));

        assertEquals("1080P / 24 FPS CINEMATIC · 2.39:1 SCOPE",
                VideoRecordingCoordinator.formatProfileLabel(null, "24 fps", "2.39:1 Widescreen"));

        assertEquals("1080P / 25 FPS PAL",
                VideoRecordingCoordinator.formatProfileLabel(null, "25 fps (PAL)", "16:9"));

        assertEquals("1080P / 30 FPS",
                VideoRecordingCoordinator.formatProfileLabel(null, "30 fps", "16:9"));
    }

    @Test
    public void test25FpsSupportAndRangeResolution() {
        assertEquals(25, VideoRecordingCoordinator.computeTargetFps("25 fps (PAL)"));

        java.util.List<int[]> ranges = Arrays.asList(
                new int[]{15, 30},
                new int[]{24, 24},
                new int[]{25, 25},
                new int[]{30, 30}
        );
        VideoRecordingCoordinator.FpsRangeDecision d25 =
                VideoRecordingCoordinator.resolveFpsRange(25, ranges, 0L);
        assertEquals(25, d25.lower);
        assertEquals(25, d25.upper);
        assertEquals(25, d25.effectiveFps);
        org.junit.Assert.assertTrue(d25.exactMatch);

        // Stream min frame duration = 33.33ms caps requested 60fps down to 30fps
        VideoRecordingCoordinator.FpsRangeDecision d60Capped =
                VideoRecordingCoordinator.resolveFpsRange(60, ranges, 33_333_333L);
        assertEquals(30, d60Capped.effectiveFps);
        assertFalse(d60Capped.exactMatch);
        assertFalse(VideoRecordingCoordinator.isTargetFpsSupported(60, ranges, 33_333_333L));
    }

    @Test
    public void test180ShutterEvaluationAndDaylightFallback() {
        assertEquals(20_833_333L, VideoRecordingCoordinator.target180ShutterNs(24));
        assertEquals(20_000_000L, VideoRecordingCoordinator.target180ShutterNs(25));
        assertEquals("1/48 (180°)", VideoRecordingCoordinator.format180ShutterLabel(24));
        assertEquals("1/50 (180°)", VideoRecordingCoordinator.format180ShutterLabel(25));

        // Moderate indoor scene: metered 1/30s @ ISO 400 -> 1/48s requires ~ISO 640
        VideoRecordingCoordinator.Shutter180Plan indoor = VideoRecordingCoordinator.evaluate180Shutter(
                true, true, 24, 100_000L, 500_000_000L, 50, 3200,
                33_333_333L, 400, 0f);
        org.junit.Assert.assertTrue(indoor.engaged);
        assertEquals(20_833_333L, indoor.shutterNs);
        org.junit.Assert.assertTrue(indoor.initialIso >= 600 && indoor.initialIso <= 660);

        // Bright daylight: metered 1/2000s @ ISO 50 cannot fit 1/48s without blowing highlights
        VideoRecordingCoordinator.Shutter180Plan daylight = VideoRecordingCoordinator.evaluate180Shutter(
                true, true, 24, 100_000L, 500_000_000L, 50, 3200,
                500_000L, 50, 0f);
        assertFalse(daylight.engaged);

        // Missing manual sensor support falls back cleanly
        VideoRecordingCoordinator.Shutter180Plan legacy = VideoRecordingCoordinator.evaluate180Shutter(
                true, false, 24, 100_000L, 500_000_000L, 50, 3200,
                33_333_333L, 400, 0f);
        assertFalse(legacy.engaged);
    }

    @Test
    public void test180AutoIsoDampingAndBlowoutRelease() {
        // Inside target deadband holds current ISO
        assertEquals(400, VideoRecordingCoordinator.step180AutoIso(400, 112.0, 0f, 50, 3200));

        // Dark scene bumps ISO smoothly upward without overshooting
        int raised = VideoRecordingCoordinator.step180AutoIso(400, 56.0, 0f, 50, 3200);
        org.junit.Assert.assertTrue(raised > 400 && raised <= 580);

        // Overexposed scene at minimum ISO disengages 180° shutter (-1)
        assertEquals(-1, VideoRecordingCoordinator.step180AutoIso(50, 220.0, 0f, 50, 3200));

        // AE lock must prevent auto-engaging 180° shutter and overwriting locked exposure
        org.junit.Assert.assertTrue(VideoRecordingCoordinator.canAutoEngage180Shutter(true, false, false, true, true));
        assertFalse(VideoRecordingCoordinator.canAutoEngage180Shutter(true, false, true, true, true));
        assertFalse(VideoRecordingCoordinator.canAutoEngage180Shutter(true, true, false, true, true));
        assertFalse(VideoRecordingCoordinator.canAutoEngage180Shutter(false, false, false, true, true));
        assertFalse(VideoRecordingCoordinator.canAutoEngage180Shutter(true, false, false, false, true));
        assertFalse(VideoRecordingCoordinator.canAutoEngage180Shutter(true, false, false, true, false));
    }

    @Test
    public void testHevcMain10ProfileSelection() {
        int[] sdrOnly = {android.media.MediaCodecInfo.CodecProfileLevel.HEVCProfileMain};
        int[] sdrLevels = {android.media.MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel41};
        assertEquals(-1, VideoRecordingCoordinator.selectHevcMain10Level(sdrOnly, sdrLevels));

        int[] mixed = {
                android.media.MediaCodecInfo.CodecProfileLevel.HEVCProfileMain,
                android.media.MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10
        };
        int[] mixedLevels = {
                android.media.MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel5,
                android.media.MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel41
        };
        assertEquals(android.media.MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel41,
                VideoRecordingCoordinator.selectHevcMain10Level(mixed, mixedLevels));
    }

    @Test
    public void testAudioConfigAndZoomCapabilities() {
        VideoRecordingCoordinator.AudioConfig stereo =
                VideoRecordingCoordinator.resolveAudioConfig("Camcorder · Stereo", true, false, true);
        org.junit.Assert.assertTrue(stereo.enabled);
        assertEquals(2, stereo.channels);
        org.junit.Assert.assertTrue(stereo.allowAudioZoom);
        assertFalse(stereo.unprocessedFallback);

        VideoRecordingCoordinator.AudioConfig voice =
                VideoRecordingCoordinator.resolveAudioConfig("Voice / Speech Focus", true, false, true);
        org.junit.Assert.assertTrue(voice.enabled);
        assertEquals(1, voice.channels);
        assertFalse(voice.allowAudioZoom);

        VideoRecordingCoordinator.AudioConfig unprocessedFallback =
                VideoRecordingCoordinator.resolveAudioConfig("Unprocessed · Raw Mic", true, false, true);
        org.junit.Assert.assertTrue(unprocessedFallback.enabled);
        org.junit.Assert.assertTrue(unprocessedFallback.unprocessedFallback);

        VideoRecordingCoordinator.AudioConfig muted =
                VideoRecordingCoordinator.resolveAudioConfig("Muted · Video Only", true, true, true);
        assertFalse(muted.enabled);

        assertFalse(VideoRecordingCoordinator.supportsAudioZoomField(
                28, java.util.Collections.singletonList(android.media.MicrophoneInfo.DIRECTIONALITY_CARDIOID), 2));
        org.junit.Assert.assertTrue(VideoRecordingCoordinator.supportsAudioZoomField(
                29, java.util.Collections.singletonList(android.media.MicrophoneInfo.DIRECTIONALITY_OMNI), 2));
        assertFalse(VideoRecordingCoordinator.supportsAudioZoomField(
                29, java.util.Collections.singletonList(android.media.MicrophoneInfo.DIRECTIONALITY_OMNI), 1));
    }

    @Test
    public void testMidTakeSwitchCompatibility() {
        org.junit.Assert.assertTrue(VideoRecordingCoordinator.checkMidTakeSwitch(
                true, true, true, 90, 90).allowed);
        assertFalse(VideoRecordingCoordinator.checkMidTakeSwitch(
                false, false, false, 90, 90).allowed);
        assertFalse(VideoRecordingCoordinator.checkMidTakeSwitch(
                true, true, false, 90, 90).allowed);
        assertFalse(VideoRecordingCoordinator.checkMidTakeSwitch(
                true, true, true, 90, 270).allowed);
    }

    @Test
    public void testChooseVideoSize() {
        assertNull(VideoRecordingCoordinator.chooseVideoSize(null));
        assertNull(VideoRecordingCoordinator.chooseVideoSize(new Size[0]));
    }

    @Test
    public void testStitchVideoSegmentsEmpty() {
        assertFalse(VideoRecordingCoordinator.stitchVideoSegments(null, new File("/tmp/out.mp4")));
        assertFalse(VideoRecordingCoordinator.stitchVideoSegments(new ArrayList<>(), new File("/tmp/out.mp4")));
    }
}
