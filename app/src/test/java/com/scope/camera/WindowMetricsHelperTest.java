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

    @Test
    public void testHeaderCutoutZonesForCenteredAndEdgeCutouts() {
        // Pixel 11 Pro portrait (1080px wide, centered punch-hole [498..582], top privacy dot rightPad=148px)
        WindowMetricsHelper.HeaderCutoutZones pixelPortrait = WindowMetricsHelper.computeHeaderCutoutZones(
                1080, 32, 148, 22, 84,
                true, 498, 20, 582, 108,
                16, 130
        );
        assertTrue(pixelPortrait.splitCenteredCutout);
        assertEquals(450, pixelPortrait.leftZoneWidth);   // 498 - 16 - 32
        assertEquals(116, pixelPortrait.spacerWidth);     // (582 - 498) + 32
        assertEquals(334, pixelPortrait.rightZoneWidth);  // (1080 - 148) - (582 + 16)
        assertEquals(1080, 32 + pixelPortrait.leftZoneWidth + pixelPortrait.spacerWidth + pixelPortrait.rightZoneWidth + 148);

        // Centered notch entirely above the header row does not force split
        WindowMetricsHelper.HeaderCutoutZones belowNotch = WindowMetricsHelper.computeHeaderCutoutZones(
                1080, 32, 32, 120, 84,
                true, 360, 0, 720, 100,
                16, 130
        );
        assertFalse(belowNotch.splitCenteredCutout);

        // Landscape corner punch-hole does not force centered split
        WindowMetricsHelper.HeaderCutoutZones edgeHole = WindowMetricsHelper.computeHeaderCutoutZones(
                2410, 110, 32, 22, 84,
                true, 20, 20, 100, 104,
                16, 130
        );
        assertFalse(edgeHole.splitCenteredCutout);
    }
}
