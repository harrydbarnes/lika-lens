package com.scope.camera;

import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;

import java.util.Locale;

/**
 * Pure helper functions for Camera2 manual focus capability detection,
 * diopter/slider mapping, and focus distance formatting.
 */
final class ManualFocusHelper {
    static final int SLIDER_MAX = 1000;
    private static final float INFINITY_THRESHOLD_DIOPTERS = 0.05f;
    private static final double SLIDER_CURVE_GAMMA = 1.25;

    private ManualFocusHelper() {}

    static boolean isManualFocusSupported(Float minFocusDistance, int[] availableAfModes) {
        if (minFocusDistance == null || !Float.isFinite(minFocusDistance) || minFocusDistance <= 0f) {
            return false;
        }
        if (availableAfModes == null) return false;
        for (int mode : availableAfModes) {
            if (mode == CaptureRequest.CONTROL_AF_MODE_OFF) {
                return true;
            }
        }
        return false;
    }

    static float clampedDiopters(float requestedDiopters, Float minFocusDistance) {
        if (minFocusDistance == null || !Float.isFinite(minFocusDistance) || minFocusDistance <= 0f) {
            return 0f;
        }
        if (!Float.isFinite(requestedDiopters)) return 0f;
        return Math.max(0f, Math.min(minFocusDistance, requestedDiopters));
    }

    /**
     * Maps SeekBar progress [0 .. SLIDER_MAX] (0 = NEAR/macro, SLIDER_MAX = infinity)
     * to Camera2 LENS_FOCUS_DISTANCE in diopters [minFocusDistance .. 0f].
     */
    static float progressToDiopters(int progress, float minFocusDistance) {
        if (!Float.isFinite(minFocusDistance) || minFocusDistance <= 0f) return 0f;
        int p = Math.max(0, Math.min(SLIDER_MAX, progress));
        double t = 1.0 - (p / (double) SLIDER_MAX); // 1.0 at NEAR, 0.0 at infinity
        float diopters = (float) (minFocusDistance * Math.pow(t, SLIDER_CURVE_GAMMA));
        return clampedDiopters(diopters, minFocusDistance);
    }

    /**
     * Inverse mapping from diopters [0f .. minFocusDistance] to SeekBar progress [SLIDER_MAX .. 0].
     */
    static int dioptersToProgress(float diopters, float minFocusDistance) {
        if (!Float.isFinite(minFocusDistance) || minFocusDistance <= 0f) return SLIDER_MAX;
        float clamped = clampedDiopters(diopters, minFocusDistance);
        double ratio = Math.max(0.0, Math.min(1.0, clamped / (double) minFocusDistance));
        double t = Math.pow(ratio, 1.0 / SLIDER_CURVE_GAMMA);
        return Math.max(0, Math.min(SLIDER_MAX, (int) Math.round((1.0 - t) * SLIDER_MAX)));
    }

    static String formatFocusDistance(float diopters, Float minFocusDistance, Integer calibration) {
        if (minFocusDistance == null || !Float.isFinite(minFocusDistance) || minFocusDistance <= 0f) {
            return "FIXED";
        }
        float d = clampedDiopters(diopters, minFocusDistance);
        if (d < INFINITY_THRESHOLD_DIOPTERS) {
            return "∞";
        }
        boolean uncalibrated = calibration != null
                && calibration == CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION_UNCALIBRATED;
        if (uncalibrated) {
            return String.format(Locale.US, "%.2f D", d);
        }
        float meters = 1f / d;
        if (meters >= 10f) return "∞";
        if (meters >= 1.0f) return String.format(Locale.US, "%.2f m", meters);
        return String.format(Locale.US, "%d cm", Math.max(1, Math.round(meters * 100f)));
    }
}
