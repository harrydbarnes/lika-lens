package com.scope.camera;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.media.MicrophoneInfo;
import android.os.Build;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
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
        if (fpsChoice.startsWith("25")) return 25;
        return 24;
    }

    static final class FpsRangeDecision {
        final int lower;
        final int upper;
        final int requestedFps;
        final int effectiveFps;
        final boolean exactMatch;

        FpsRangeDecision(int lower, int upper, int requestedFps, int effectiveFps, boolean exactMatch) {
            this.lower = lower;
            this.upper = upper;
            this.requestedFps = requestedFps;
            this.effectiveFps = effectiveFps;
            this.exactMatch = exactMatch;
        }

        boolean hasRange() {
            return lower > 0 && upper >= lower;
        }
    }

    /**
     * Resolves a requested video FPS against Camera2's CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES
     * and the MediaRecorder surface's minimum frame duration (in ns, 0 if unknown).
     */
    static FpsRangeDecision resolveFpsRange(int requestedFps, List<int[]> availableRanges, long minFrameDurationNs) {
        int clampedReq = requestedFps > 0 ? requestedFps : 24;
        int maxStreamFps = Integer.MAX_VALUE;
        if (minFrameDurationNs > 0L) {
            // Allow 1 ms timing slack for nominal sensor rounding (e.g. 16.7ms for 60fps).
            maxStreamFps = Math.max(1, (int) Math.floor(1_000_000_000.0 / Math.max(1L, minFrameDurationNs - 1_000_000L)));
        }
        int cappedTarget = Math.min(clampedReq, maxStreamFps);
        if (availableRanges == null || availableRanges.isEmpty()) {
            boolean exact = cappedTarget == clampedReq;
            return new FpsRangeDecision(cappedTarget, cappedTarget, clampedReq, cappedTarget, exact);
        }

        int[] exactFixed = null;
        int[] exactUpper = null;
        int[] bestFallback = null;
        int bestFallbackDiff = Integer.MAX_VALUE;

        for (int[] r : availableRanges) {
            if (r == null || r.length < 2) continue;
            int lo = r[0];
            int hi = r[1];
            if (lo <= 0 || hi < lo) continue;
            if (hi > maxStreamFps && maxStreamFps >= 15) continue;

            if (lo == cappedTarget && hi == cappedTarget) {
                exactFixed = r;
                break;
            }
            if (hi == cappedTarget) {
                if (exactUpper == null || lo > exactUpper[0]) {
                    exactUpper = r;
                }
            }
            int diff = Math.abs(hi - cappedTarget);
            if (bestFallback == null || diff < bestFallbackDiff) {
                bestFallback = r;
                bestFallbackDiff = diff;
            } else if (diff == bestFallbackDiff) {
                boolean candFixed = (lo == hi);
                boolean bestFixed = (bestFallback[0] == bestFallback[1]);
                if ((candFixed && !bestFixed)
                        || (candFixed == bestFixed && hi <= cappedTarget && bestFallback[1] > cappedTarget)
                        || (candFixed == bestFixed && hi == bestFallback[1] && lo > bestFallback[0])) {
                    bestFallback = r;
                }
            }
        }

        // If stream duration filtered out every range, fall back across all valid ranges.
        if (exactFixed == null && exactUpper == null && bestFallback == null) {
            for (int[] r : availableRanges) {
                if (r == null || r.length < 2 || r[0] <= 0 || r[1] < r[0]) continue;
                int diff = Math.abs(r[1] - cappedTarget);
                if (bestFallback == null || diff < bestFallbackDiff) {
                    bestFallback = r;
                    bestFallbackDiff = diff;
                }
            }
        }

        int[] chosen = exactFixed != null ? exactFixed : (exactUpper != null ? exactUpper : bestFallback);
        if (chosen == null) {
            boolean exact = cappedTarget == clampedReq;
            return new FpsRangeDecision(cappedTarget, cappedTarget, clampedReq, cappedTarget, exact);
        }
        int effective = Math.min(chosen[1], maxStreamFps);
        boolean exact = (effective == clampedReq) && (chosen[1] == clampedReq);
        return new FpsRangeDecision(chosen[0], chosen[1], clampedReq, effective, exact);
    }

    static boolean isTargetFpsSupported(int requestedFps, List<int[]> availableRanges, long minFrameDurationNs) {
        return resolveFpsRange(requestedFps, availableRanges, minFrameDurationNs).exactMatch;
    }

    static long target180ShutterNs(int fps) {
        int safeFps = Math.max(1, fps);
        return Math.round(1_000_000_000.0 / (2.0 * safeFps));
    }

    static long frameDurationNs(int fps) {
        int safeFps = Math.max(1, fps);
        return Math.round(1_000_000_000.0 / safeFps);
    }

    static String format180ShutterLabel(int fps) {
        int safeFps = Math.max(1, fps);
        return "1/" + (2 * safeFps) + " (180°)";
    }

    static final class Shutter180Plan {
        final boolean engaged;
        final long shutterNs;
        final long frameDurationNs;
        final int initialIso;
        final String fallbackReason;

        Shutter180Plan(boolean engaged, long shutterNs, long frameDurationNs, int initialIso, String fallbackReason) {
            this.engaged = engaged;
            this.shutterNs = shutterNs;
            this.frameDurationNs = frameDurationNs;
            this.initialIso = initialIso;
            this.fallbackReason = fallbackReason;
        }
    }

    static boolean canAutoEngage180Shutter(boolean videoMode, boolean video180Active,
                                            boolean aeLocked, boolean video180Requested,
                                            boolean manualSupported) {
        return videoMode && !video180Active && !aeLocked && video180Requested && manualSupported;
    }

    /**
     * Evaluates whether a genuine 180-degree shutter (CONTROL_AE_MODE_OFF with Auto-ISO)
     * can run cleanly under the sensor's hardware limits and current metered exposure.
     */
    static Shutter180Plan evaluate180Shutter(boolean want180, boolean manualSensorSupported,
                                            int effectiveFps,
                                            long minExposureNs, long maxExposureNs,
                                            int minIso, int maxIso,
                                            long meteredExposureNs, int meteredIso,
                                            float evOffset) {
        int safeFps = Math.max(1, effectiveFps);
        long targetNs = target180ShutterNs(safeFps);
        long frameNs = frameDurationNs(safeFps);
        if (!want180) {
            return new Shutter180Plan(false, targetNs, frameNs, 0, "Auto exposure selected");
        }
        if (!manualSensorSupported || minExposureNs <= 0L || maxExposureNs < minExposureNs
                || minIso <= 0 || maxIso < minIso) {
            return new Shutter180Plan(false, targetNs, frameNs, 0, "Sensor lacks manual shutter/ISO control");
        }
        if (targetNs < minExposureNs || targetNs > maxExposureNs) {
            return new Shutter180Plan(false, targetNs, frameNs, 0, "180° shutter exceeds sensor exposure range");
        }
        if (meteredExposureNs <= 0L || meteredIso <= 0) {
            return new Shutter180Plan(false, targetNs, frameNs, 0, "Metering initial exposure");
        }
        double evScale = Math.pow(2.0, evOffset);
        double rawIso = meteredIso * ((double) meteredExposureNs / (double) targetNs) * evScale;
        if (rawIso < minIso * 0.85) {
            return new Shutter180Plan(false, targetNs, frameNs, 0,
                    "Bright scene requires faster shutter than 1/" + (2 * safeFps) + " s");
        }
        int clampedIso = (int) Math.max(minIso, Math.min(maxIso, Math.round(rawIso)));
        return new Shutter180Plan(true, targetNs, frameNs, clampedIso, null);
    }

    /**
     * Closed-loop damped Auto-ISO step while 180° shutter is active.
     * Returns -1 when bright daylight forces falling back to AE_ON to avoid highlight blowout.
     */
    static int step180AutoIso(int currentIso, double meanLuma, float evOffset, int minIso, int maxIso) {
        if (minIso <= 0 || maxIso < minIso || currentIso <= 0) return -1;
        double targetLuma = Math.max(48.0, Math.min(195.0, 112.0 * Math.pow(2.0, evOffset * 0.45)));
        if (currentIso <= minIso && meanLuma > targetLuma * 1.65) {
            return -1;
        }
        if (meanLuma >= targetLuma * 0.84 && meanLuma <= targetLuma * 1.18) {
            return currentIso;
        }
        double safeLuma = Math.max(8.0, meanLuma);
        double ratio = targetLuma / safeLuma;
        double damped = Math.max(0.70, Math.min(1.45, 1.0 + (ratio - 1.0) * 0.35));
        int next = (int) Math.round(currentIso * damped);
        return Math.max(minIso, Math.min(maxIso, next));
    }

    static int selectHevcMain10Level(int[] profiles, int[] levels) {
        if (profiles == null || levels == null || profiles.length != levels.length) return -1;
        int preferredLevel = -1;
        int fallbackLevel = -1;
        for (int i = 0; i < profiles.length; i++) {
            int p = profiles[i];
            int lvl = levels[i];
            boolean isMain10 = p == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10
                    || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    && (p == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10
                    || p == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus));
            if (!isMain10 || lvl <= 0) continue;
            if (p == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10
                    && lvl == MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel41) {
                return lvl;
            }
            if (p == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10 && lvl > preferredLevel) {
                preferredLevel = lvl;
            } else if (lvl > fallbackLevel) {
                fallbackLevel = lvl;
            }
        }
        return preferredLevel > 0 ? preferredLevel : fallbackLevel;
    }

    /** Queries hardware/regular HEVC encoders for a supported Main10 level, or -1 if unsupported. */
    static int findSupportedHevcMain10Level() {
        try {
            MediaCodecList list = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
            for (MediaCodecInfo info : list.getCodecInfos()) {
                if (!info.isEncoder()) continue;
                boolean supportsHevc = false;
                for (String type : info.getSupportedTypes()) {
                    if (MediaFormat.MIMETYPE_VIDEO_HEVC.equalsIgnoreCase(type)) {
                        supportsHevc = true;
                        break;
                    }
                }
                if (!supportsHevc) continue;
                MediaCodecInfo.CodecCapabilities caps = info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_HEVC);
                if (caps == null || caps.profileLevels == null) continue;
                int[] profiles = new int[caps.profileLevels.length];
                int[] levels = new int[caps.profileLevels.length];
                for (int i = 0; i < caps.profileLevels.length; i++) {
                    profiles[i] = caps.profileLevels[i].profile;
                    levels[i] = caps.profileLevels[i].level;
                }
                int level = selectHevcMain10Level(profiles, levels);
                if (level > 0) return level;
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed to query HEVC Main10 codec capabilities", t);
        }
        return -1;
    }

    static final class AudioConfig {
        final boolean enabled;
        final int audioSource;
        final int channels;
        final boolean allowAudioZoom;
        final boolean unprocessedFallback;

        AudioConfig(boolean enabled, int audioSource, int channels, boolean allowAudioZoom, boolean unprocessedFallback) {
            this.enabled = enabled;
            this.audioSource = audioSource;
            this.channels = channels;
            this.allowAudioZoom = allowAudioZoom;
            this.unprocessedFallback = unprocessedFallback;
        }
    }

    static AudioConfig resolveAudioConfig(String audioPref, boolean hasMicPermission,
                                            boolean unprocessedSupported, boolean audioZoomSupported) {
        if (!hasMicPermission || (audioPref != null && audioPref.startsWith("Muted"))) {
            return new AudioConfig(false, -1, 0, false, false);
        }
        if (audioPref != null && audioPref.startsWith("Unprocessed")) {
            if (unprocessedSupported && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                return new AudioConfig(true, MediaRecorder.AudioSource.UNPROCESSED, 2, false, false);
            }
            return new AudioConfig(true, MediaRecorder.AudioSource.CAMCORDER, 2, audioZoomSupported, true);
        }
        if (audioPref != null && (audioPref.contains("Speech") || audioPref.startsWith("Voice"))) {
            return new AudioConfig(true, MediaRecorder.AudioSource.VOICE_RECOGNITION, 1, false, false);
        }
        return new AudioConfig(true, MediaRecorder.AudioSource.CAMCORDER, 2, audioZoomSupported, false);
    }

    static boolean supportsAudioZoomField(int apiLevel, List<Integer> directionalities, int builtInMicCount) {
        if (apiLevel < 29) return false;
        if (directionalities != null) {
            for (Integer dir : directionalities) {
                if (dir != null
                        && dir != MicrophoneInfo.DIRECTIONALITY_UNKNOWN
                        && dir != MicrophoneInfo.DIRECTIONALITY_OMNI) {
                    return true;
                }
            }
        }
        return builtInMicCount >= 2;
    }

    static boolean probeAudioZoomSupported(Context context) {
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false;
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return false;
            List<MicrophoneInfo> mics = am.getMicrophones();
            if (mics == null || mics.isEmpty()) return false;
            List<Integer> dirs = new ArrayList<>(mics.size());
            int builtInCount = 0;
            for (MicrophoneInfo mic : mics) {
                if (mic == null) continue;
                if (mic.getType() == AudioDeviceInfo.TYPE_BUILTIN_MIC) {
                    builtInCount++;
                    dirs.add(mic.getDirectionality());
                }
            }
            return supportsAudioZoomField(Build.VERSION.SDK_INT, dirs, builtInCount);
        } catch (Throwable t) {
            Log.w(TAG, "Could not inspect microphone array capabilities", t);
            return false;
        }
    }

    static boolean probeUnprocessedAudioSupported(Context context) {
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false;
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return false;
            return "true".equalsIgnoreCase(am.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED));
        } catch (Throwable ignored) {
            return false;
        }
    }

    static final class SwitchCompatibility {
        final boolean allowed;
        final String reason;

        SwitchCompatibility(boolean allowed, String reason) {
            this.allowed = allowed;
            this.reason = reason;
        }
    }

    static SwitchCompatibility checkMidTakeSwitch(boolean targetSupportsVideoSize,
                                                    boolean activeTakeHdr10,
                                                    boolean targetSupportsHdr10,
                                                    int lockedOrientation,
                                                    int targetOrientation) {
        if (activeTakeHdr10) {
            if (!targetSupportsHdr10) {
                return new SwitchCompatibility(false, "Cannot switch mid-take · target lens lacks HLG10 HDR");
            }
            if (lockedOrientation >= 0 && targetOrientation >= 0 && lockedOrientation != targetOrientation) {
                return new SwitchCompatibility(false, "Switch front/back before starting a 10-bit HLG HDR take");
            }
        }
        if (!targetSupportsVideoSize) {
            return new SwitchCompatibility(false, "Cannot switch mid-take · target lens lacks matching video resolution");
        }
        return new SwitchCompatibility(true, null);
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
        String fpsLabel = fps.startsWith("60") ? "60 FPS"
                : fps.startsWith("30") ? "30 FPS"
                : fps.startsWith("25") ? "25 FPS PAL"
                : "24 FPS CINEMATIC";
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
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                retriever.setDataSource(segments.get(0).getAbsolutePath());
                String rot = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
                if (rot != null) {
                    muxer.setOrientationHint(Integer.parseInt(rot));
                }
            } catch (Throwable t) {
                Log.w(TAG, "Failed to read orientation hint from video segment", t);
            } finally {
                try {
                    retriever.release();
                } catch (Throwable ignored) { }
            }

            // Discover tracks from first valid segment
            long fallbackStepUs = 33_333L;
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
                            if (fmt.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                                int fps = fmt.getInteger(MediaFormat.KEY_FRAME_RATE);
                                if (fps > 0) fallbackStepUs = 1_000_000L / fps;
                            }
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
                    long lastVideoDeltaUs = fallbackStepUs;
                    if (segVideoTrack >= 0) {
                        extractor.selectTrack(segVideoTrack);
                        long firstVideoPts = -1L;
                        long prevRelPts = -1L;
                        while (true) {
                            buffer.clear();
                            int sampleSize = extractor.readSampleData(buffer, 0);
                            if (sampleSize < 0) break;
                            long rawPts = extractor.getSampleTime();
                            if (firstVideoPts < 0) firstVideoPts = Math.max(0L, rawPts);
                            long relPts = Math.max(0L, rawPts - firstVideoPts);
                            if (prevRelPts >= 0 && relPts > prevRelPts) {
                                lastVideoDeltaUs = relPts - prevRelPts;
                            }
                            prevRelPts = relPts;
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

                    timelineOffsetUs += maxSegPtsUs + Math.max(1_000L, lastVideoDeltaUs);
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
