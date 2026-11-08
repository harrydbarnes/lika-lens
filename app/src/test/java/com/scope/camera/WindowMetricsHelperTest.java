package com.scope.camera;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for WindowMetricsHelper verifying Google's Adaptive Window Size Classes
 * and adaptive layout recommendations.
 */
public class WindowMetricsHelperTest {

    @Test
    public void testWidthSizeClasses() {
        // Compact (< 600dp)
        WindowMetricsHelper.WindowSizeInfo compactInfo = new WindowMetricsHelper.WindowSizeInfo(
                WindowMetricsHelper.WidthSizeClass.COMPACT,
                WindowMetricsHelper.HeightSizeClass.MEDIUM,
                380, 800, false
        );
        assertEquals(WindowMetricsHelper.WidthSizeClass.COMPACT, compactInfo.widthClass);
        assertFalse(compactInfo.isTwoPaneRecommended);

        // Medium (600dp - 839dp)
        WindowMetricsHelper.WindowSizeInfo mediumPortrait = new WindowMetricsHelper.WindowSizeInfo(
                WindowMetricsHelper.WidthSizeClass.MEDIUM,
                WindowMetricsHelper.HeightSizeClass.EXPANDED,
                700, 1000, false
        );
        assertEquals(WindowMetricsHelper.WidthSizeClass.MEDIUM, mediumPortrait.widthClass);

        WindowMetricsHelper.WindowSizeInfo mediumLandscape = new WindowMetricsHelper.WindowSizeInfo(
                WindowMetricsHelper.WidthSizeClass.MEDIUM,
                WindowMetricsHelper.HeightSizeClass.COMPACT,
                700, 400, true
        );
        assertTrue(mediumLandscape.isTwoPaneRecommended);

        // Expanded (>= 840dp)
        WindowMetricsHelper.WindowSizeInfo expandedInfo = new WindowMetricsHelper.WindowSizeInfo(
                WindowMetricsHelper.WidthSizeClass.EXPANDED,
                WindowMetricsHelper.HeightSizeClass.MEDIUM,
                1024, 768, true
        );
        assertEquals(WindowMetricsHelper.WidthSizeClass.EXPANDED, expandedInfo.widthClass);
        assertTrue(expandedInfo.isTwoPaneRecommended);
    }

    @Test
    public void testCalculateGalleryColumns() {
        // Narrow compact phone
        assertEquals(2, WindowMetricsHelper.calculateGalleryColumns(300));

        // Standard phone portrait (360dp - 412dp)
        assertEquals(2, WindowMetricsHelper.calculateGalleryColumns(360));
        assertEquals(3, WindowMetricsHelper.calculateGalleryColumns(412));

        // Foldable unfolded / medium tablet (600dp - 720dp)
        assertEquals(4, WindowMetricsHelper.calculateGalleryColumns(600));
        assertEquals(5, WindowMetricsHelper.calculateGalleryColumns(720));

        // Large tablet / expanded landscape (840dp+)
        assertEquals(6, WindowMetricsHelper.calculateGalleryColumns(840));
        assertEquals(6, WindowMetricsHelper.calculateGalleryColumns(1200)); // clamped to max 6
    }
}
