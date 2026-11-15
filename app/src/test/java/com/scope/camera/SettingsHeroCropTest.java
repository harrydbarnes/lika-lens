package com.scope.camera;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for SettingsActivity hero preview aspect-preserving scaling and upper-middle crop framing.
 */
public class SettingsHeroCropTest {

    @Test
    public void testComputeHeroScaledDimensionsPreservesAspectRatio() {
        // 3:4 portrait stays 3:4 portrait (never squashed to landscape 360x225)
        int[] portrait = SettingsActivity.computeHeroScaledDimensions(3000, 4000, 480);
        assertArrayEquals(new int[]{360, 480}, portrait);

        // 4:3 landscape stays 4:3 landscape
        int[] landscape = SettingsActivity.computeHeroScaledDimensions(4000, 3000, 480);
        assertArrayEquals(new int[]{480, 360}, landscape);

        // Smaller image is not upscaled unnecessarily
        int[] small = SettingsActivity.computeHeroScaledDimensions(240, 320, 480);
        assertArrayEquals(new int[]{240, 320}, small);
    }

    @Test
    public void testPortraitCropTargetsUpperMiddleOneSixthToFourSixthsBand() {
        // Portrait 3:4 bitmap inside a wide 1080x576 (approx 360x192dp) hero frame
        float[] t = SettingsActivity.computeHeroUpperMiddleTransform(360, 480, 1080, 576);
        float scale = t[0];
        float dx = t[1];
        float dy = t[2];

        assertEquals(3.0f, scale, 0.001f);
        assertEquals(0f, dx, 0.01f);

        float scaledH = 480f * scale;
        float topFraction = -dy / scaledH;
        float bottomFraction = (-dy + 576f) / scaledH;

        // Visible window should lie inside roughly the upper-middle 1/6..4/6 band without any blank bars
        assertTrue("Expected top >= 0.15, got " + topFraction, topFraction >= 0.15f);
        assertTrue("Expected bottom <= 0.67, got " + bottomFraction, bottomFraction <= 0.67f);
        assertEquals(5f / 12f, (topFraction + bottomFraction) * 0.5f, 0.01f);
    }

    @Test
    public void testLandscapeAndWidePhotosCoverViewCompletelyWithoutGaps() {
        float[] tLand = SettingsActivity.computeHeroUpperMiddleTransform(480, 360, 1080, 576);
        float scale = tLand[0];
        float dy = tLand[2];
        assertTrue("Scale must cover width", 480f * scale >= 1080f - 0.01f);
        assertTrue("Scale must cover height", 360f * scale >= 576f - 0.01f);
        assertTrue("dy <= 0", dy <= 0f);
        assertTrue("bottom edge covers viewH", dy + 360f * scale >= 576f - 0.01f);
    }
}
