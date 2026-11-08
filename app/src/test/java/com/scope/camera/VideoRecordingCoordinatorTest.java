package com.scope.camera;

import android.util.Size;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

public class VideoRecordingCoordinatorTest {

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

        assertEquals("1080P / 30 FPS",
                VideoRecordingCoordinator.formatProfileLabel(null, "30 fps", "16:9"));
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
