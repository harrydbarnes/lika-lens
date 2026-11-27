package com.scope.camera;

/**
 * Pure-math motion curves and state targets for smooth Photo <-> Video UI transitions.
 * Kept Android-free so every interpolation invariant is deterministically unit-tested.
 */
final class ModeTransitionMotion {

    static final long MODE_SWITCH_DURATION_MS = 320L;
    static final long SHUTTER_MORPH_DURATION_MS = 300L;
    static final long FRAME_MORPH_DURATION_MS = 320L;
    static final long PILL_ENTER_DURATION_MS = 260L;
    static final long TEXT_SWAP_DURATION_MS = 220L;

    static final float PHOTO_SHUTTER_SCALE = 1.0f;
    static final float VIDEO_IDLE_SHUTTER_SCALE = 0.86f;
    static final float VIDEO_RECORDING_SHUTTER_SCALE = 0.64f;

    static final float CIRCLE_CORNER_FRACTION = 1.0f;
    static final float RECORDING_SQUARE_CORNER_FRACTION = 0.24f;

    private ModeTransitionMotion() {}

    static float clamp01(float t) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        return t;
    }

    static float lerp(float start, float end, float t) {
        return start + (end - start) * clamp01(t);
    }

    static int lerpColor(int startArgb, int endArgb, float t) {
        float k = clamp01(t);
        int sA = (startArgb >>> 24) & 0xff;
        int sR = (startArgb >>> 16) & 0xff;
        int sG = (startArgb >>> 8) & 0xff;
        int sB = startArgb & 0xff;

        int eA = (endArgb >>> 24) & 0xff;
        int eR = (endArgb >>> 16) & 0xff;
        int eG = (endArgb >>> 8) & 0xff;
        int eB = endArgb & 0xff;

        int a = Math.round(sA + (eA - sA) * k);
        int r = Math.round(sR + (eR - sR) * k);
        int g = Math.round(sG + (eG - sG) * k);
        int b = Math.round(sB + (eB - sB) * k);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Subtle horizontal stretch mid-flight as the mode selector pill glides between PHOTO and VIDEO.
     */
    static float pillStretchScaleX(float progress) {
        float k = clamp01(progress);
        float arc = (float) Math.sin(Math.PI * k);
        return 1.0f + 0.08f * arc;
    }

    /**
     * Complementary subtle vertical compression mid-flight to preserve visual volume.
     */
    static float pillSquashScaleY(float progress) {
        float k = clamp01(progress);
        float arc = (float) Math.sin(Math.PI * k);
        return 1.0f - 0.04f * arc;
    }

    static float shutterRestingScale(boolean videoActive, boolean recordingActive) {
        if (videoActive && recordingActive) return VIDEO_RECORDING_SHUTTER_SCALE;
        if (videoActive) return VIDEO_IDLE_SHUTTER_SCALE;
        return PHOTO_SHUTTER_SCALE;
    }

    static float shutterCornerFraction(boolean videoActive, boolean recordingActive) {
        return (videoActive && recordingActive)
                ? RECORDING_SQUARE_CORNER_FRACTION
                : CIRCLE_CORNER_FRACTION;
    }

    static void lerpBounds(
            float startLeft, float startTop, float startRight, float startBottom,
            float endLeft, float endTop, float endRight, float endBottom,
            float t,
            float[] out4) {
        float k = clamp01(t);
        out4[0] = startLeft + (endLeft - startLeft) * k;
        out4[1] = startTop + (endTop - startTop) * k;
        out4[2] = startRight + (endRight - startRight) * k;
        out4[3] = startBottom + (endBottom - startBottom) * k;
    }

    /**
     * Vertical translation offset that pins a bottom-anchored overlay (such as the zoom preset pill row)
     * to the currently animated viewfinder bottom edge while its final LayoutParams sit at targetBottom.
     */
    static float frameAnchoredOffsetY(float currentFrameBottom, float targetFrameBottom) {
        return currentFrameBottom - targetFrameBottom;
    }
}
