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

    @Test
    public void testStabilizationAndAudioChoices() {
        assertNotNull(AppSettings.STABILIZATION_CHOICES);
        assertTrue(AppSettings.STABILIZATION_CHOICES.length >= 5);
        boolean hasLocked = false;
        boolean hasActive = false;
        for (String s : AppSettings.STABILIZATION_CHOICES) {
            if (s.startsWith("Locked")) hasLocked = true;
            if (s.startsWith("Active")) hasActive = true;
        }
        assertTrue("STABILIZATION_CHOICES must include Locked", hasLocked);
        assertTrue("STABILIZATION_CHOICES must include Active", hasActive);

        assertNotNull(AppSettings.VIDEO_AUDIO_CHOICES);
        boolean hasSpeech = false;
        for (String a : AppSettings.VIDEO_AUDIO_CHOICES) {
            if (a.contains("Speech")) hasSpeech = true;
        }
        assertTrue("VIDEO_AUDIO_CHOICES must include Speech enhanced", hasSpeech);
        assertNotNull(AppSettings.SPEECH_ENHANCEMENT);
    }

    @Test
    public void testFrameBorderChoices() {
        assertNotNull(AppSettings.FRAME_BORDER_CHOICES);
        boolean hasMediumFormat = false;
        boolean hasCinemascope = false;
        for (String b : AppSettings.FRAME_BORDER_CHOICES) {
            if (b.contains("Medium Format")) hasMediumFormat = true;
            if (b.contains("Cinemascope")) hasCinemascope = true;
        }
        assertTrue("FRAME_BORDER_CHOICES must include Medium Format 6x6 Rebate", hasMediumFormat);
        assertTrue("FRAME_BORDER_CHOICES must include Cinemascope 2.39:1 Matte", hasCinemascope);
    }

    @Test
    public void testAstrophotographyAndLaunchModes() {
        assertNotNull(AppSettings.ASTRO_CHOICES);
        assertTrue(AppSettings.ASTRO_CHOICES.length >= 3);
        boolean hasAuto = false;
        for (String c : AppSettings.ASTRO_CHOICES) {
            if (c.contains("Auto")) hasAuto = true;
        }
        assertTrue("ASTRO_CHOICES must contain Auto", hasAuto);

        assertNotNull(AppSettings.LAUNCH_MODES);
        assertTrue(AppSettings.LAUNCH_MODES.length >= 3);
        assertEquals("Photo (default)", AppSettings.LAUNCH_MODES[0]);
    }

    @Test
    public void testLeftHandModeChoicesAndHelpers() {
        assertNotNull(AppSettings.LEFT_HAND_MODE_CHOICES);
        assertEquals(3, AppSettings.LEFT_HAND_MODE_CHOICES.length);
        assertEquals(AppSettings.LEFT_HAND_OFF, AppSettings.LEFT_HAND_MODE_CHOICES[0]);

        assertTrue(!AppSettings.isLeftHandMode(AppSettings.LEFT_HAND_OFF));
        assertTrue(!AppSettings.isLeftHandSwapShutterRow(AppSettings.LEFT_HAND_OFF));
        assertTrue(!AppSettings.isLeftHandMode((String) null));

        assertTrue(AppSettings.isLeftHandMode(AppSettings.LEFT_HAND_MAIN));
        assertTrue(!AppSettings.isLeftHandSwapShutterRow(AppSettings.LEFT_HAND_MAIN));

        assertTrue(AppSettings.isLeftHandMode(AppSettings.LEFT_HAND_FURTHER));
        assertTrue(AppSettings.isLeftHandSwapShutterRow(AppSettings.LEFT_HAND_FURTHER));
    }
}
