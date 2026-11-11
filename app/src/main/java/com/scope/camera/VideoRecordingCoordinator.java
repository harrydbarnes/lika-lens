package com.scope.camera;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Manages video recording configuration, size negotiation, resource cleanup,
 * and multi-segment MP4 track stitching.
 */
public final class VideoRecordingCoordinator {
    private static final String TAG = "VideoRecordingCoordinator";

    private VideoRecordingCoordinator() {}

    /** Pixel rotation required before encoding under the take's single playback matrix. */
    static int rotationCorrection(int cameraOrientation, int takeOrientation) {
        return Math.floorMod(cameraOrientation - takeOrientation, 360);
    }

    /** Keep the valid crop, but remove Camera2's preview-specific rotation/mirroring.
     * Recording applies sensor rotation explicitly and does not mirror selfie pixels.
     */
    static void recordingTextureMatrix(float[] source, float[] result) {
        float x = source[12], y = source[13];
        float minX = Math.min(Math.min(x, x + source[0]), Math.min(x + source[4], x + source[0] + source[4]));
        float maxX = Math.max(Math.max(x, x + source[0]), Math.max(x + source[4], x + source[0] + source[4]));
        float minY = Math.min(Math.min(y, y + source[1]), Math.min(y + source[5], y + source[1] + source[5]));
        float maxY = Math.max(Math.max(y, y + source[1]), Math.max(y + source[5], y + source[1] + source[5]));
        Arrays.fill(result, 0);
        result[0] = maxX - minX;
        result[5] = -(maxY - minY);
        result[10] = 1;
        result[12] = minX;
        result[13] = maxY;
        result[15] = 1;
    }

    /** Clockwise-rotated quad, fitted without stretching for quarter-turn sensor changes. */
    static float[] recordingVertices(int rotation, int width, int height) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Invalid recording size");
        int degrees = Math.floorMod(rotation, 360);
        if (degrees % 90 != 0) throw new IllegalArgumentException("Rotation must be a quarter turn");
        float[] vertices = {-1, -1, 1, -1, -1, 1, 1, 1};
        float radians = (float) Math.toRadians(-degrees);
        float cos = Math.round(Math.cos(radians));
        float sin = Math.round(Math.sin(radians));
        float scaleX = 1, scaleY = 1;
        if (degrees % 180 != 0) {
            float ratio = (float) height / width;
            if (ratio < 1) scaleX = ratio * ratio;
            else scaleY = 1 / (ratio * ratio);
        }
        for (int i = 0; i < vertices.length; i += 2) {
            float x = vertices[i], y = vertices[i + 1];
            vertices[i] = (x * cos - y * sin) * scaleX;
            vertices[i + 1] = (x * sin + y * cos) * scaleY;
        }
        return vertices;
    }

    /**
     * Chooses an optimal landscape video recording resolution capped at 1080p.
     */
    public static Size chooseVideoSize(Size[] sizes) {
        if (sizes == null || sizes.length == 0) return null;
        return Arrays.stream(sizes)
                .filter(s -> s.getWidth() >= s.getHeight() && s.getWidth() <= 1920 && s.getHeight() <= 1080)
                .max(Comparator.comparingLong(s -> (long) s.getWidth() * s.getHeight()))
                .orElseGet(() -> Arrays.stream(sizes)
                        .filter(s -> s.getWidth() >= s.getHeight())
                        .min(Comparator.comparingLong(s -> Math.abs((long) s.getWidth() * s.getHeight() - 1920L * 1080L)))
                        .orElse(sizes[0]));
    }

    /**
     * Computes the target frame rate based on user preference string.
     */
    public static int computeTargetFps(String fpsChoice) {
        if (fpsChoice == null) return 24;
        if (fpsChoice.startsWith("60")) return 60;
        if (fpsChoice.startsWith("30")) return 30;
        return 24;
    }

    /**
     * Computes the target video encoding bitrate based on user preference string.
     */
    public static int computeBitrate(String bitrateChoice) {
        if (bitrateChoice == null) return 24_000_000;
        if (bitrateChoice.startsWith("Ultra")) return 35_000_000;
        if (bitrateChoice.startsWith("Standard")) return 14_000_000;
        return 24_000_000;
    }

    /**
     * Formats video profile label displayed in the viewfinder HUD.
     */
    public static String formatProfileLabel(Size videoSize, String fpsSetting, String vAspect) {
        String fps = fpsSetting == null ? "24 fps (Cinematic)" : fpsSetting;
        String fpsLabel = fps.startsWith("60") ? "60 FPS" : fps.startsWith("30") ? "30 FPS" : "24 FPS CINEMATIC";
        String aspect = vAspect == null ? "" : vAspect;
        String aspectLabel = aspect.contains("2.39") ? " · 2.39:1 SCOPE" : "";
        if (videoSize == null) return "1080P / " + fpsLabel + aspectLabel;
        int vertical = Math.min(videoSize.getWidth(), videoSize.getHeight());
        return vertical + "P / " + fpsLabel + aspectLabel;
    }

    /**
     * Safely resets and releases a MediaRecorder instance.
     */
    public static void releaseMediaRecorder(MediaRecorder mediaRecorder) {
        if (mediaRecorder == null) return;
        try {
            mediaRecorder.reset();
        } catch (RuntimeException e) {
            Log.w(TAG, "Failed to reset media recorder", e);
        }
        try {
            mediaRecorder.release();
        } catch (RuntimeException e) {
            Log.w(TAG, "Failed to release media recorder", e);
        }
    }

    /**
     * Safely releases the recording preview Surface.
     */
    public static void releaseRecordingPreviewSurface(Surface surface) {
        if (surface == null) return;
        try {
            surface.release();
        } catch (RuntimeException e) {
            Log.w(TAG, "Failed to release recording preview surface", e);
        }
    }

    /**
     * Seamlessly stitches multiple MP4 segments recorded across camera/lens switches into one continuous MP4.
     */
    public static boolean stitchVideoSegments(List<File> segments, File outputFile) {
        if (segments == null || segments.isEmpty() || outputFile == null) return false;
        MediaMuxer muxer = null;
        try {
            muxer = new MediaMuxer(outputFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int outVideoTrack = -1;
            int outAudioTrack = -1;
            boolean muxerStarted = false;

            // Read orientation hint from first segment
            try {
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                retriever.setDataSource(segments.get(0).getAbsolutePath());
                String rot = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
                if (rot != null) {
                    muxer.setOrientationHint(Integer.parseInt(rot));
                }
                retriever.release();
            } catch (Throwable t) {
                Log.w(TAG, "Failed to read orientation hint from video segment", t);
            }

            // Discover tracks from first valid segment
            for (File seg : segments) {
                if (seg == null || !seg.exists() || seg.length() == 0) continue;
                MediaExtractor extractor = new MediaExtractor();
                try {
                    extractor.setDataSource(seg.getAbsolutePath());
                    for (int i = 0; i < extractor.getTrackCount(); i++) {
                        MediaFormat fmt = extractor.getTrackFormat(i);
                        String mime = fmt.getString(MediaFormat.KEY_MIME);
                        if (mime == null) continue;
                        if (mime.startsWith("video/") && outVideoTrack < 0) {
                            outVideoTrack = muxer.addTrack(fmt);
                        } else if (mime.startsWith("audio/") && outAudioTrack < 0) {
                            outAudioTrack = muxer.addTrack(fmt);
                        }
                    }
                    if (outVideoTrack >= 0) break;
                } finally {
                    extractor.release();
                }
            }

            if (outVideoTrack < 0) return false;
            muxer.start();
            muxerStarted = true;

            ByteBuffer buffer = ByteBuffer.allocateDirect(2 * 1024 * 1024);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            long timelineOffsetUs = 0L;

            for (File seg : segments) {
                if (seg == null || !seg.exists() || seg.length() == 0) continue;
                MediaExtractor extractor = new MediaExtractor();
                try {
                    extractor.setDataSource(seg.getAbsolutePath());
                    int segVideoTrack = -1;
                    int segAudioTrack = -1;
                    for (int i = 0; i < extractor.getTrackCount(); i++) {
                        MediaFormat fmt = extractor.getTrackFormat(i);
                        String mime = fmt.getString(MediaFormat.KEY_MIME);
                        if (mime == null) continue;
                        if (mime.startsWith("video/") && segVideoTrack < 0) segVideoTrack = i;
                        else if (mime.startsWith("audio/") && segAudioTrack < 0) segAudioTrack = i;
                    }

                    long maxSegPtsUs = 0L;
                    if (segVideoTrack >= 0) {
                        extractor.selectTrack(segVideoTrack);
                        long firstVideoPts = -1L;
                        while (true) {
                            buffer.clear();
                            int sampleSize = extractor.readSampleData(buffer, 0);
                            if (sampleSize < 0) break;
                            long rawPts = extractor.getSampleTime();
                            if (firstVideoPts < 0) firstVideoPts = Math.max(0L, rawPts);
                            long relPts = Math.max(0L, rawPts - firstVideoPts);
                            info.offset = 0;
                            info.size = sampleSize;
                            info.presentationTimeUs = timelineOffsetUs + relPts;
                            info.flags = extractor.getSampleFlags();
                            muxer.writeSampleData(outVideoTrack, buffer, info);
                            if (relPts > maxSegPtsUs) maxSegPtsUs = relPts;
                            extractor.advance();
                        }
                        extractor.unselectTrack(segVideoTrack);
                    }

                    if (segAudioTrack >= 0 && outAudioTrack >= 0) {
                        extractor.selectTrack(segAudioTrack);
                        long firstAudioPts = -1L;
                        while (true) {
                            buffer.clear();
                            int sampleSize = extractor.readSampleData(buffer, 0);
                            if (sampleSize < 0) break;
                            long rawPts = extractor.getSampleTime();
                            if (firstAudioPts < 0) firstAudioPts = Math.max(0L, rawPts);
                            long relPts = Math.max(0L, rawPts - firstAudioPts);
                            info.offset = 0;
                            info.size = sampleSize;
                            info.presentationTimeUs = timelineOffsetUs + relPts;
                            info.flags = extractor.getSampleFlags();
                            muxer.writeSampleData(outAudioTrack, buffer, info);
                            if (relPts > maxSegPtsUs) maxSegPtsUs = relPts;
                            extractor.advance();
                        }
                        extractor.unselectTrack(segAudioTrack);
                    }

                    timelineOffsetUs += maxSegPtsUs + 33_333L;
                } finally {
                    extractor.release();
                }
            }

            if (muxerStarted) muxer.stop();
            return outputFile.exists() && outputFile.length() > 1024;
        } catch (Exception e) {
            Log.e(TAG, "Stitching video segments failed", e);
            return false;
        } finally {
            if (muxer != null) {
                try {
                    muxer.release();
                } catch (RuntimeException re) {
                    Log.w(TAG, "Failed to release media muxer", re);
                }
            }
        }
    }
}
