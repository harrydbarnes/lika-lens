package com.scope.camera;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for AppSettings verifying aspect ratio parsing and default choices.
 */
public class AppSettingsTest {

    @Test
    public void testParseAspectRatio() {
        assertEquals(1.5f, AppSettings.parseAspectRatio("3:2 · Classic 35mm"), 0.001f);
        assertEquals(4.0f / 3.0f, AppSettings.parseAspectRatio("4:3 · Default"), 0.001f);
        assertEquals(16.0f / 9.0f, AppSettings.parseAspectRatio("16:9 · Cinematic"), 0.001f);
        assertEquals(1.0f, AppSettings.parseAspectRatio("1:1 · Square Format"), 0.001f);
        assertEquals(4.0f / 3.0f, AppSettings.parseAspectRatio(null), 0.001f);
        assertEquals(4.0f / 3.0f, AppSettings.parseAspectRatio(""), 0.001f);
        assertEquals(4.0f / 3.0f, AppSettings.parseAspectRatio("invalid:string"), 0.001f);
    }

    @Test
    public void testBrainChoicesExist() {
        assertNotNull(AppSettings.BRAIN_CHOICES);
        assertTrue(AppSettings.BRAIN_CHOICES.length >= 6);
        assertEquals(AppSettings.BRAIN_CHOICES.length, AppSettings.BRAIN_DESCRIPTIONS.length);
    }
}
