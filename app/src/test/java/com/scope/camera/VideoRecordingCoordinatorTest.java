package com.scope.camera;

import android.util.Size;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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

        Size fhd = new Size(1920, 1080);
        assertEquals("1080P / 60 FPS",
                VideoRecordingCoordinator.formatProfileLabel(fhd, "60 fps", "16:9"));

        assertEquals("1080P / 24 FPS CINEMATIC · 2.39:1 SCOPE",
                VideoRecordingCoordinator.formatProfileLabel(fhd, "24 fps", "2.39:1 Widescreen"));

        Size hd = new Size(1280, 720);
        assertEquals("720P / 30 FPS",
                VideoRecordingCoordinator.formatProfileLabel(hd, "30 fps", "16:9"));
    }

    @Test
    public void testChooseVideoSize() {
        assertNull(VideoRecordingCoordinator.chooseVideoSize(null));
        assertNull(VideoRecordingCoordinator.chooseVideoSize(new Size[0]));

        Size[] sizes = new Size[] {
                new Size(3840, 2160),
                new Size(1920, 1080),
                new Size(1280, 720),
                new Size(640, 480)
        };
        Size chosen = VideoRecordingCoordinator.chooseVideoSize(sizes);
        assertNotNull(chosen);
        assertEquals(1920, chosen.getWidth());
        assertEquals(1080, chosen.getHeight());
    }

    @Test
    public void testStitchVideoSegmentsEmpty() {
        assertFalse(VideoRecordingCoordinator.stitchVideoSegments(null, new File("/tmp/out.mp4")));
        assertFalse(VideoRecordingCoordinator.stitchVideoSegments(new ArrayList<>(), new File("/tmp/out.mp4")));
    }
}
