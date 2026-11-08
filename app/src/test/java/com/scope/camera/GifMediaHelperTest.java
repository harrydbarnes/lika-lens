package com.scope.camera;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

/**
 * Unit tests for GifMediaHelper dimension scaling and null safety.
 */
public class GifMediaHelperTest {

    @Test
    public void testCalculateTargetDimensionsWithinBounds() {
        int[] result = GifMediaHelper.calculateTargetDimensions(320, 240, 480);
        assertArrayEquals(new int[]{320, 240}, result);
    }

    @Test
    public void testCalculateTargetDimensionsScalingDownLandscape() {
        int[] result = GifMediaHelper.calculateTargetDimensions(1920, 1080, 480);
        assertArrayEquals(new int[]{480, 270}, result);
    }

    @Test
    public void testCalculateTargetDimensionsScalingDownPortrait() {
        int[] result = GifMediaHelper.calculateTargetDimensions(1080, 1920, 480);
        assertArrayEquals(new int[]{270, 480}, result);
    }

    @Test
    public void testCalculateTargetDimensionsSquare() {
        int[] result = GifMediaHelper.calculateTargetDimensions(1000, 1000, 500);
        assertArrayEquals(new int[]{500, 500}, result);
    }

    @Test
    public void testNullBitmapHandling() {
        assertNull(GifMediaHelper.scaleDown(null, 480));
        assertNull(GifMediaHelper.rotateBitmap(null, 90));
    }
}
