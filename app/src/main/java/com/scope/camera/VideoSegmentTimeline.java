package com.scope.camera;

/** Timestamp arithmetic shared by the audio and video tracks of a segment. */
final class VideoSegmentTimeline {
    private VideoSegmentTimeline() { }

    static long origin(long firstVideoUs, long firstAudioUs) {
        return firstAudioUs < 0 ? firstVideoUs : Math.min(firstVideoUs, firstAudioUs);
    }

    static long duration(long lastVideoUs, long videoStepUs, long audioEndUs) {
        return Math.max(lastVideoUs + videoStepUs, audioEndUs);
    }
}
