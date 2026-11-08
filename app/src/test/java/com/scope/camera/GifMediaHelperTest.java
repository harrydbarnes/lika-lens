package com.scope.camera;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Unit tests for GifMediaHelper dimension scaling and null safety.
 */
public class GifMediaHelperTest {

    @Test
    public void testCalculateTargetDimensionsWithinBounds() {
        int[] result = GifMediaHelper.calculateTargetDimensions(320, 240, 480);
        assertNotNull(result);
        assertEquals(320, result[0]);
        assertEquals(240, result[1]);
    }

    @Test
    public void testCalculateTargetDimensionsScalingDownLandscape() {
        int[] result = GifMediaHelper.calculateTargetDimensions(1920, 1080, 480);
        assertNotNull(result);
        assertEquals(480, result[0]);
        assertEquals(270, result[1]);
    }

    @Test
    public void testCalculateTargetDimensionsScalingDownPortrait() {
        int[] result = GifMediaHelper.calculateTargetDimensions(1080, 1920, 480);
        assertNotNull(result);
        assertEquals(270, result[0]);
        assertEquals(480, result[1]);
    }

    @Test
    public void testCalculateTargetDimensionsSquare() {
        int[] result = GifMediaHelper.calculateTargetDimensions(1000, 1000, 500);
        assertNotNull(result);
        assertEquals(500, result[0]);
        assertEquals(500, result[1]);
    }

    @Test
    public void testNullBitmapHandling() {
        assertNull(GifMediaHelper.scaleDown(null, 480));
        assertNull(GifMediaHelper.rotateBitmap(null, 90));
    }
}
