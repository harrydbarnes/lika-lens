package com.scope.camera;

import org.junit.Test;
import static org.junit.Assert.*;

public class LookRecipeControlTest {

    @Test
    public void testGetControlMin() {
        assertEquals(-20, LookRecipeControl.getControlMin(LookRecipeControl.TYPE_EXPOSURE));
        assertEquals(50, LookRecipeControl.getControlMin(LookRecipeControl.TYPE_CONTRAST));
        assertEquals(50, LookRecipeControl.getControlMin(LookRecipeControl.TYPE_SATURATION));
        assertEquals(0, LookRecipeControl.getControlMin(LookRecipeControl.TYPE_WHITE_BALANCE));
        assertEquals(0, LookRecipeControl.getControlMin(LookRecipeControl.TYPE_GRAIN));
    }

    @Test
    public void testGetDefaultValue() {
        assertEquals(0, LookRecipeControl.getDefaultValue(LookRecipeControl.TYPE_EXPOSURE));
        assertEquals(100, LookRecipeControl.getDefaultValue(LookRecipeControl.TYPE_CONTRAST));
        assertEquals(100, LookRecipeControl.getDefaultValue(LookRecipeControl.TYPE_SATURATION));
        assertEquals(50, LookRecipeControl.getDefaultValue(LookRecipeControl.TYPE_HIGHLIGHTS));
        assertEquals(50, LookRecipeControl.getDefaultValue(LookRecipeControl.TYPE_WARMTH));
        assertEquals(100, LookRecipeControl.getDefaultValue(LookRecipeControl.TYPE_LUT_INTENSITY));
    }

    @Test
    public void testIsLandmarkValue() {
        assertTrue(LookRecipeControl.isLandmarkValue(LookRecipeControl.TYPE_EXPOSURE, 0));
        assertFalse(LookRecipeControl.isLandmarkValue(LookRecipeControl.TYPE_EXPOSURE, 5));

        assertTrue(LookRecipeControl.isLandmarkValue(LookRecipeControl.TYPE_CONTRAST, 100));
        assertFalse(LookRecipeControl.isLandmarkValue(LookRecipeControl.TYPE_CONTRAST, 110));

        assertTrue(LookRecipeControl.isLandmarkValue(LookRecipeControl.TYPE_WARMTH, 50));
        assertFalse(LookRecipeControl.isLandmarkValue(LookRecipeControl.TYPE_WARMTH, 40));
    }

    @Test
    public void testFormatValue() {
        assertEquals("+0.0 EV", LookRecipeControl.formatValue(LookRecipeControl.TYPE_EXPOSURE, 0));
        assertEquals("+1.5 EV", LookRecipeControl.formatValue(LookRecipeControl.TYPE_EXPOSURE, 15));
        assertEquals("-1.0 EV", LookRecipeControl.formatValue(LookRecipeControl.TYPE_EXPOSURE, -10));

        assertEquals("AUTO", LookRecipeControl.formatValue(LookRecipeControl.TYPE_WHITE_BALANCE, 0));
        assertEquals("3200 K", LookRecipeControl.formatValue(LookRecipeControl.TYPE_WHITE_BALANCE, 1));
        assertEquals("5600 K", LookRecipeControl.formatValue(LookRecipeControl.TYPE_WHITE_BALANCE, 25));

        assertEquals("1.00", LookRecipeControl.formatValue(LookRecipeControl.TYPE_CONTRAST, 100));
        assertEquals("0.85", LookRecipeControl.formatValue(LookRecipeControl.TYPE_SATURATION, 85));

        assertEquals("0", LookRecipeControl.formatValue(LookRecipeControl.TYPE_WARMTH, 50));
        assertEquals("WARM +10", LookRecipeControl.formatValue(LookRecipeControl.TYPE_WARMTH, 60));
        assertEquals("COOL 10", LookRecipeControl.formatValue(LookRecipeControl.TYPE_WARMTH, 40));

        assertEquals("25%", LookRecipeControl.formatValue(LookRecipeControl.TYPE_GRAIN, 25));
    }

    @Test
    public void testApplyToLook() {
        RawDeveloper.Look look = new RawDeveloper.Look();

        LookRecipeControl.applyToLook(look, LookRecipeControl.TYPE_EXPOSURE, 15);
        assertEquals(1.5f, look.exposureEv, 0.001f);

        LookRecipeControl.applyToLook(look, LookRecipeControl.TYPE_CONTRAST, 120);
        assertEquals(1.20f, look.contrast, 0.001f);

        LookRecipeControl.applyToLook(look, LookRecipeControl.TYPE_SATURATION, 80);
        assertEquals(0.80f, look.saturation, 0.001f);

        LookRecipeControl.applyToLook(look, LookRecipeControl.TYPE_WARMTH, 60);
        assertEquals(0.20f, look.warmth, 0.001f);

        LookRecipeControl.applyToLook(look, LookRecipeControl.TYPE_LUT_INTENSITY, 75);
        assertEquals(0.75f, look.lutIntensity, 0.001f);
    }
}
