package com.scope.camera;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ModeTransitionMotionTest {

    @Test
    public void lerpColorInterpolatesEndpointsAndMidpoint() {
        int white = 0xfffaf9f5;
        int crimson = 0xffd93632;
        assertEquals(white, ModeTransitionMotion.lerpColor(white, crimson, 0f));
        assertEquals(crimson, ModeTransitionMotion.lerpColor(white, crimson, 1f));
        assertEquals(white, ModeTransitionMotion.lerpColor(white, crimson, -0.5f));
        assertEquals(crimson, ModeTransitionMotion.lerpColor(white, crimson, 1.5f));

        int mid = ModeTransitionMotion.lerpColor(0xff000000, 0xffffffff, 0.5f);
        assertEquals(0xff808080, mid);
    }

    @Test
    public void pillStretchAndSquashReturnToIdentityAtEndpoints() {
        assertEquals(1.0f, ModeTransitionMotion.pillStretchScaleX(0f), 1e-5f);
        assertEquals(1.0f, ModeTransitionMotion.pillStretchScaleX(1f), 1e-5f);
        assertEquals(1.0f, ModeTransitionMotion.pillSquashScaleY(0f), 1e-5f);
        assertEquals(1.0f, ModeTransitionMotion.pillSquashScaleY(1f), 1e-5f);

        assertTrue(ModeTransitionMotion.pillStretchScaleX(0.5f) > 1.0f);
        assertTrue(ModeTransitionMotion.pillSquashScaleY(0.5f) < 1.0f);
    }

    @Test
    public void shutterTargetsDistinguishPhotoVideoIdleAndRecording() {
        float photoScale = ModeTransitionMotion.shutterRestingScale(false, false);
        float videoIdleScale = ModeTransitionMotion.shutterRestingScale(true, false);
        float recordingScale = ModeTransitionMotion.shutterRestingScale(true, true);

        assertTrue(photoScale > videoIdleScale);
        assertTrue(videoIdleScale > recordingScale);

        assertEquals(1.0f, ModeTransitionMotion.shutterCornerFraction(false, false), 1e-5f);
        assertEquals(1.0f, ModeTransitionMotion.shutterCornerFraction(true, false), 1e-5f);
        assertTrue(ModeTransitionMotion.shutterCornerFraction(true, true) < 0.5f);
    }

    @Test
    public void lerpBoundsInterpolatesRectCoordinates() {
        float[] out = new float[4];
        ModeTransitionMotion.lerpBounds(0f, 100f, 1000f, 900f, 100f, 200f, 900f, 800f, 0.5f, out);
        assertEquals(50f, out[0], 1e-4f);
        assertEquals(150f, out[1], 1e-4f);
        assertEquals(950f, out[2], 1e-4f);
        assertEquals(850f, out[3], 1e-4f);
    }
}
