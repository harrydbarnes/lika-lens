package com.scope.camera;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class VideoSegmentTimelineTest {
    @Test public void preservesAudioVideoStartOffsetsInEitherDirection() {
        assertEquals(100_000L, VideoSegmentTimeline.origin(120_000L, 100_000L));
        assertEquals(100_000L, VideoSegmentTimeline.origin(100_000L, 120_000L));
        assertEquals(120_000L, VideoSegmentTimeline.origin(120_000L, -1L));
    }

    @Test public void nextSegmentStartsAfterBothTrackEnds() {
        assertEquals(1_021_333L, VideoSegmentTimeline.duration(966_667L, 33_333L, 1_021_333L));
        assertEquals(1_000_000L, VideoSegmentTimeline.duration(966_667L, 33_333L, 990_000L));
    }
}
