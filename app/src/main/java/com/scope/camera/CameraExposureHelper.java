package com.scope.camera;

import android.graphics.Rect;
import android.util.Log;
import android.util.Range;

import java.util.Locale;

/**
 * CameraExposureHelper encapsulates exposure calculations, shutter speed conversions,
 * ISO parsing, zoom interpolation, and sensor crop mathematics.
 *
 * Extracted from MainActivity to modularize camera state management and enable
 * comprehensive unit testing with deterministic inputs.
 */
public final class CameraExposureHelper {
    private static final String TAG = "CameraExposureHelper";

    public static final float MIN_ZOOM = 0.5f;
    public static final float MAX_ZOOM = 5.0f;
    public static final float WIDE_ZOOM_THRESHOLD = 0.98f;
    public static final int ZOOM_SEEKBAR_MAX = 1000;

    private CameraExposureHelper() {}

    /**
     * Converts a shutter speed string (e.g. "1/120 sec", "1/60", "1/1000") to nanoseconds.
     */
    public static long shutterNanoseconds(String value) {
        if (value == null) return 8_333_333L; // default 1/120s
        String clean = value.replace("sec", "").replace("s", "").trim();
        if (clean.startsWith("1/")) {
            try {
                double denominator = Double.parseDouble(clean.substring(2).trim());
                if (denominator > 0) {
                    return Math.round(1_000_000_000.0 / denominator);
                }
            } catch (NumberFormatException e) {
                Log.w(TAG, "Failed to parse shutter speed denominator: " + clean, e);
            }
        }
        if (value.startsWith("1/30")) return 33_333_333L;
        if (value.startsWith("1/60")) return 16_666_667L;
        if (value.startsWith("1/120")) return 8_333_333L;
        if (value.startsWith("1/240")) return 4_166_667L;
        if (value.startsWith("1/500")) return 2_000_000L;
        if (value.startsWith("1/1000")) return 1_000_000L;
        return 8_333_333L;
    }

    /**
     * Formats sensor exposure time in nanoseconds to a clean shutter speed display string.
     */
    public static String formatShutter(long nanoseconds) {
        if (nanoseconds <= 0) return "AUTO";
        double seconds = nanoseconds / 1_000_000_000.0;
        if (seconds >= 0.5) {
            return String.format(Locale.US, "%.1fs", seconds);
        }
        long denominator = Math.round(1.0 / seconds);
        return "1/" + denominator;
    }

    /**
     * Parses an ISO string (e.g. "ISO 100", "ISO 6400", "400") to integer sensitivity.
     */
    public static int parseIso(String value) {
        if (value == null) return 100;
        try {
            return Integer.parseInt(value.replace("ISO", "").trim());
        } catch (NumberFormatException ignored) {
            return 100;
        }
    }

    /**
     * Clamps exposure time within hardware sensor limits.
     */
    public static long clampExposureTime(long requestedNs, Range<Long> hardwareRange) {
        if (hardwareRange == null) return requestedNs;
        return Math.max(hardwareRange.getLower(), Math.min(hardwareRange.getUpper(), requestedNs));
    }

    /**
     * Clamps sensitivity (ISO) within hardware sensor limits.
     */
    public static int clampSensitivity(int requestedIso, Range<Integer> hardwareRange) {
        if (hardwareRange == null) return requestedIso;
        return Math.max(hardwareRange.getLower(), Math.min(hardwareRange.getUpper(), requestedIso));
    }

    /**
     * Calculates AE compensation step index from a target EV offset and step size.
     */
    public static int calculateAeCompensationIndex(float targetEv, float stepSize, Range<Integer> aeRange) {
        if (stepSize <= 0f) return 0;
        int index = Math.round(targetEv / stepSize);
        if (aeRange != null) {
            index = Math.max(aeRange.getLower(), Math.min(aeRange.getUpper(), index));
        }
        return index;
    }

    /**
     * Calculates EV offset from AE compensation index and step size.
     */
    public static float calculateEvFromIndex(int index, float stepSize) {
        return index * stepSize;
    }

    /**
     * Maps zoom factor (0.5x to 5.0x) to SeekBar integer progress (0 to 1000) using log curve.
     */
    public static int zoomProgressFor(float zoom) {
        float z = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));
        float minLog = (float) Math.log(MIN_ZOOM);
        float maxLog = (float) Math.log(MAX_ZOOM);
        float curLog = (float) Math.log(z);
        float ratio = (curLog - minLog) / (maxLog - minLog);
        return Math.round(ratio * ZOOM_SEEKBAR_MAX);
    }

    /**
     * Maps SeekBar integer progress (0 to 1000) to zoom factor (0.5x to 5.0x) using exponential curve.
     */
    public static float zoomForProgress(int progress) {
        float p = Math.max(0, Math.min(ZOOM_SEEKBAR_MAX, progress));
        float ratio = p / (float) ZOOM_SEEKBAR_MAX;
        float minLog = (float) Math.log(MIN_ZOOM);
        float maxLog = (float) Math.log(MAX_ZOOM);
        float curLog = minLog + ratio * (maxLog - minLog);
        return (float) Math.exp(curLog);
    }

    private static Rect makeRect(int left, int top, int right, int bottom) {
        Rect r = new Rect();
        r.left = left;
        r.top = top;
        r.right = right;
        r.bottom = bottom;
        return r;
    }

    /**
     * Computes the digital crop rectangle for optical/digital zoom centered on active sensor array.
     */
    public static Rect computeCropRect(Rect activeArray, float zoomFactor) {
        if (activeArray == null) return new Rect();
        if (zoomFactor <= 1.0f) return makeRect(activeArray.left, activeArray.top, activeArray.right, activeArray.bottom);

        int activeW = activeArray.right - activeArray.left;
        int activeH = activeArray.bottom - activeArray.top;
        int cropWidth = Math.round(activeW / zoomFactor);
        int cropHeight = Math.round(activeH / zoomFactor);
        int cropLeft = activeArray.left + (activeW - cropWidth) / 2;
        int cropTop = activeArray.top + (activeH - cropHeight) / 2;

        return makeRect(cropLeft, cropTop, cropLeft + cropWidth, cropTop + cropHeight);
    }

    /**
     * Computes a bounded metering rectangle within the current crop region for touch-to-focus/AE.
     * Takes into account sensor rotation, digital zoom crop region, and front camera mirroring.
     *
     * @param cropRegion The active sensor crop region (or sensor active array if uncropped).
     * @param nx Normalized touch X within the framing rectangle [0.0 .. 1.0].
     * @param ny Normalized touch Y within the framing rectangle [0.0 .. 1.0].
     * @param isFrontCamera True if front-facing camera with preview mirror.
     * @param boxFraction Fraction of crop dimension for the metering box (e.g. 0.10f for 10%).
     * @return Bounded Rect inside cropRegion in sensor coordinates.
     */
    public static Rect computeMeteringRect(Rect cropRegion, float nx, float ny, boolean isFrontCamera, float boxFraction) {
        if (cropRegion == null) return new Rect();
        float clampedNx = Math.max(0f, Math.min(1f, nx));
        float clampedNy = Math.max(0f, Math.min(1f, ny));

        int w = cropRegion.right - cropRegion.left;
        int h = cropRegion.bottom - cropRegion.top;
        if (w <= 0 || h <= 0) return makeRect(cropRegion.left, cropRegion.top, cropRegion.right, cropRegion.bottom);

        // Android camera sensor coordinates:
        // In default portrait orientation, camera sensor is oriented 90 degrees clockwise (rear).
        // Tap Y corresponds to sensor X, and tap (1 - X) corresponds to sensor Y.
        // For front-facing cameras (which are horizontally mirrored for the user preview),
        // horizontal touch coordinate is mirrored: tap X corresponds to sensor Y.
        int sensorX = cropRegion.left + Math.round(clampedNy * w);
        int sensorY = cropRegion.top + Math.round((isFrontCamera ? clampedNx : (1.0f - clampedNx)) * h);

        float fraction = boxFraction > 0f ? boxFraction : 0.10f;
        int boxW = Math.max(1, Math.min(w, Math.max(50, Math.round(w * fraction))));
        int boxH = Math.max(1, Math.min(h, Math.max(50, Math.round(h * fraction))));

        int left = Math.max(cropRegion.left, Math.min(cropRegion.right - boxW, sensorX - boxW / 2));
        int top = Math.max(cropRegion.top, Math.min(cropRegion.bottom - boxH, sensorY - boxH / 2));
        int right = Math.min(cropRegion.right, left + boxW);
        int bottom = Math.min(cropRegion.bottom, top + boxH);

        return makeRect(left, top, right, bottom);
    }
    static final int NIGHT_STACK_FRAME_COUNT = 4;
    static final long MAX_NIGHT_STACK_FRAME_NS = 1_000_000_000L;

    /** Automatic long integration is only for stable, genuinely dim scenes, never manual exposure. */
    static boolean shouldUseNightIntegration(String setting, boolean stationary, boolean manual,
                                             long exposureNs, int iso) {
        if (manual || setting == null || setting.startsWith("Off")) return false;
        if (setting.startsWith("Always")) return true;
        return stationary && iso >= 800 && exposureNs >= 25_000_000L;
    }

    /** Flash-aware check shared by capture and viewfinder status indicator. */
    static boolean shouldUseNightIntegration(String setting, boolean stationary, boolean manual,
                                             long exposureNs, int iso, String flashMode) {
        if (flashMode != null && !"Off".equals(flashMode)) return false;
        return shouldUseNightIntegration(setting, stationary, manual, exposureNs, iso);
    }

    /**
     * Computes per-frame exposure (ns), sensitivity (ISO), and burst frame count for multi-frame
     * night stacking, trading ISO upward only when preserving metered brightness would exceed 1s/frame.
     */
    static long[] computeNightStackParameters(String setting, long meteredNs, int meteredIso,
                                              long minExpNs, long maxExpNs, int minIso, int maxIso) {
        int lowIso = Math.min(minIso, maxIso);
        int highIso = Math.max(minIso, maxIso);
        long lowExp = Math.min(minExpNs, maxExpNs);
        long highExp = Math.max(minExpNs, maxExpNs);
        int baseIso = Math.max(lowIso, Math.min(highIso, 200));

        if (setting != null && setting.startsWith("Always") && (meteredNs <= 0L || meteredIso <= 0)) {
            long exp = Math.max(lowExp, Math.min(highExp, MAX_NIGHT_STACK_FRAME_NS));
            return new long[]{exp, baseIso, NIGHT_STACK_FRAME_COUNT};
        }

        if (meteredNs <= 0L || meteredIso <= 0) {
            long exp = Math.max(lowExp, Math.min(highExp, MAX_NIGHT_STACK_FRAME_NS));
            return new long[]{exp, baseIso, NIGHT_STACK_FRAME_COUNT};
        }

        double equivalentNs = meteredNs * (meteredIso / (double) baseIso);
        long frameExpCap = Math.min(highExp, MAX_NIGHT_STACK_FRAME_NS);
        int targetIso = baseIso;
        long targetExp;
        if (equivalentNs > frameExpCap && frameExpCap > 0L) {
            long scaledIso = Math.round(baseIso * (equivalentNs / (double) frameExpCap));
            targetIso = (int) Math.max(lowIso, Math.min(highIso, scaledIso));
            targetExp = Math.max(lowExp, frameExpCap);
        } else {
            targetExp = Math.max(lowExp, Math.min(highExp, Math.round(equivalentNs)));
        }
        return new long[]{targetExp, targetIso, NIGHT_STACK_FRAME_COUNT};
    }

    static long matchedNightExposure(long meteredNs, int meteredIso, int targetIso, long maxNs) {
        if (meteredNs <= 0 || meteredIso <= 0 || targetIso <= 0) return 0L;
        double equivalent = meteredNs * (meteredIso / (double) targetIso);
        return (long) Math.min(Math.min(maxNs, 2_000_000_000L), equivalent);
    }

    /**
     * Returns true when a RAW-capable camera must drop its logical RAW_SENSOR stream and switch
     * to a JPEG capture session so CONTROL_ZOOM_RATIO below 1.0x can switch to a wider sensor.
     */
    static boolean shouldUseJpegForWideZoom(boolean rawSupported, float targetZoom) {
        return rawSupported && targetZoom < WIDE_ZOOM_THRESHOLD;
    }

    /**
     * Normalizes SeekBar rounding just below 1.0x when returning to a RAW session so the HAL
     * does not receive CONTROL_ZOOM_RATIO slightly below 1.0f with a logical RAW stream attached.
     */
    static float resolveZoomForSessionMode(float requestedZoom, boolean jpegWideMode) {
        if (!jpegWideMode && requestedZoom >= WIDE_ZOOM_THRESHOLD && requestedZoom < 1.0f) {
            return 1.0f;
        }
        return requestedZoom;
    }

    /**
     * Returns true when staying on the same camera ID still requires recreating the capture session
     * because the stream mode changed between logical RAW and wide-angle JPEG.
     */
    static boolean needsPreviewSessionReconfig(String currentCameraId, String targetCameraId,
            boolean currentForceJpegWide, boolean targetForceJpegWide) {
        return currentCameraId != null
                && currentCameraId.equals(targetCameraId)
                && currentForceJpegWide != targetForceJpegWide;
    }

    /**
     * Evaluates whether the active camera supports the requested zoom factor natively.
     */
    static boolean canZoomOnCamera(boolean hasCharacteristics, Float minZoomRatio, Float maxZoomRatio,
            boolean isMainCamera, float factor) {
        if (!hasCharacteristics) return false;
        if (minZoomRatio != null && maxZoomRatio != null) {
            if (factor >= 1.0f) {
                return factor <= maxZoomRatio + 0.05f;
            }
            return minZoomRatio < WIDE_ZOOM_THRESHOLD;
        }
        return factor >= 1.0f && isMainCamera;
    }

    /**
     * Resolves a clamped wide-angle zoom ratio when CONTROL_ZOOM_RATIO_RANGE extends below 1.0x,
     * or -1f when wide optical zoom is unsupported.
     */
    static float resolveWideZoomRatio(Float minZoomRatio, Float maxZoomRatio, float factor) {
        if (minZoomRatio == null || maxZoomRatio == null
                || minZoomRatio >= WIDE_ZOOM_THRESHOLD || maxZoomRatio < 1.0f) {
            return -1f;
        }
        return Math.max(factor, minZoomRatio);
    }

    /**
     * Returns a zoom target to animate smoothly in the current session before reconfiguring
     * streams across RAW/wide-JPEG mode boundaries, or -1f to reconfigure immediately.
     */
    static float preReconfigAnimationTarget(boolean currentForceJpegWide, boolean targetForceJpegWide,
            float currentZoom, float targetZoom) {
        if (currentForceJpegWide == targetForceJpegWide) return -1f;
        if (currentForceJpegWide) {
            return targetZoom;
        }
        return currentZoom > 1.05f ? 1.0f : -1f;
    }

}
