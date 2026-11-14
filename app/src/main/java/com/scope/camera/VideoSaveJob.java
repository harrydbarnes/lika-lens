package com.scope.camera;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Owns segment files until the complete recording has been published successfully. */
final class VideoSaveJob {
    enum Phase { CAPTURED, STITCHING, PUBLISHING, SAVED, RECOVERABLE_FAILURE }
    final List<File> segments;
    private Phase phase = Phase.CAPTURED;
    VideoSaveJob(List<File> segments) { this.segments = Collections.unmodifiableList(new ArrayList<>(segments)); }
    synchronized Phase phase() { return phase; }
    synchronized void stitching() { phase = Phase.STITCHING; }
    synchronized void publishing() { phase = Phase.PUBLISHING; }
    synchronized void saved() { phase = Phase.SAVED; }
    synchronized void failed() { phase = Phase.RECOVERABLE_FAILURE; }
    synchronized boolean mayDeleteSegments() { return phase == Phase.SAVED; }

    static File recoveryDirectory(File filesDir) throws IOException {
        File directory = new File(filesDir, "video-recovery");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Could not create video recovery folder");
        return directory;
    }
}
