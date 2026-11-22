package com.scope.camera;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for AppSettings verifying aspect ratio parsing, v2 defaults, Adjust controls, and migration.
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
        assertEquals(2.39f, AppSettings.parseAspectRatio("2.39:1 · Cinemascope"), 0.001f);
        assertEquals(1.85f, AppSettings.parseAspectRatio("1.85:1 · Flat Widescreen"), 0.001f);
        assertEquals(16.0f / 9.0f, AppSettings.parseAspectRatio(" 16 : 9 "), 0.001f);
        assertEquals(4.0f / 3.0f, AppSettings.parseAspectRatio("0:9"), 0.001f);
        assertEquals(4.0f / 3.0f, AppSettings.parseAspectRatio("16:0"), 0.001f);
        assertEquals(4.0f / 3.0f, AppSettings.parseAspectRatio("16:9:1"), 0.001f);
        assertEquals(4.0f / 3.0f, AppSettings.parseAspectRatio("16"), 0.001f);
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
            assertFalse("VIDEO_AUDIO_CHOICES must not claim wind filter", a.contains("Wind filter"));
        }
        assertTrue("VIDEO_AUDIO_CHOICES must include Speech enhanced", hasSpeech);
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

        assertFalse(AppSettings.isLeftHandMode(AppSettings.LEFT_HAND_OFF));
        assertFalse(AppSettings.isLeftHandSwapShutterRow(AppSettings.LEFT_HAND_OFF));
        assertFalse(AppSettings.isLeftHandMode((String) null));

        assertTrue(AppSettings.isLeftHandMode(AppSettings.LEFT_HAND_MAIN));
        assertFalse(AppSettings.isLeftHandSwapShutterRow(AppSettings.LEFT_HAND_MAIN));

        assertTrue(AppSettings.isLeftHandMode(AppSettings.LEFT_HAND_FURTHER));
        assertTrue(AppSettings.isLeftHandSwapShutterRow(AppSettings.LEFT_HAND_FURTHER));
    }

    @Test
    public void testPhotoOutputAndEditableArchiveLimits() {
        assertEquals(AppSettings.PHOTO_OUTPUT_JPEG_ONLY, AppSettings.PHOTO_OUTPUT_CHOICES[0]);
        assertFalse(AppSettings.shouldSaveDng(AppSettings.PHOTO_OUTPUT_JPEG_ONLY));
        assertTrue(AppSettings.shouldSaveDng(AppSettings.PHOTO_OUTPUT_JPEG_DNG));
        assertTrue(AppSettings.shouldSaveDng(AppSettings.PHOTO_OUTPUT_DNG_ONLY));
        assertTrue(AppSettings.isDngOnly(AppSettings.PHOTO_OUTPUT_DNG_ONLY));
        assertFalse(AppSettings.isDngOnly(AppSettings.PHOTO_OUTPUT_JPEG_DNG));

        assertEquals(256L * 1024L * 1024L, AppSettings.parseEditableArchiveLimitBytes(AppSettings.EDITABLE_ARCHIVE_256MB));
        assertEquals(512L * 1024L * 1024L, AppSettings.parseEditableArchiveLimitBytes(AppSettings.EDITABLE_ARCHIVE_512MB));
        assertEquals(1024L * 1024L * 1024L, AppSettings.parseEditableArchiveLimitBytes(AppSettings.EDITABLE_ARCHIVE_1GB));
        assertEquals(2048L * 1024L * 1024L, AppSettings.parseEditableArchiveLimitBytes(AppSettings.EDITABLE_ARCHIVE_2GB));
        assertEquals(Long.MAX_VALUE, AppSettings.parseEditableArchiveLimitBytes(AppSettings.EDITABLE_ARCHIVE_UNLIMITED));
    }

    @Test
    public void testAdjustControlsSerializationAndValidation() {
        List<String> defaults = AppSettings.parseAdjustControls(null);
        assertEquals(AppSettings.DEFAULT_ADJUST_CONTROLS, defaults);

        List<String> parsed = AppSettings.parseAdjustControls("pivot, invalid_key, exposure_ev_tenths, pivot, tone_curve");
        assertEquals(Arrays.asList(AppSettings.PIVOT, AppSettings.EXPOSURE, AppSettings.TONE_CURVE), parsed);

        String serialized = AppSettings.serializeAdjustControls(Arrays.asList(
                AppSettings.SHADOW_PROFILE, "nonexistent", AppSettings.GRAIN, AppSettings.SHADOW_PROFILE));
        assertEquals(AppSettings.SHADOW_PROFILE + "," + AppSettings.GRAIN, serialized);
    }

    @Test
    public void testSettingsV1ToV2MigrationIsIdempotentAndPreservesUserWork() {
        Map<String, Object> v1 = new HashMap<>();
        v1.put(AppSettings.BRAIN, "SCOPE / CORE");
        v1.put(AppSettings.FILE_PREFIX, "SCOPE");
        v1.put(AppSettings.RECIPE_1_NAME, "My Neon");
        v1.put(AppSettings.RECIPE_1_DATA, "{\"brain\":\"KODACHROME / 64\"}");
        v1.put(AppSettings.LUT_NAME, "CustomCine");
        v1.put(AppSettings.COLOR_SPACE, "Rec. 709 · Cinema Broadcast");
        v1.put(AppSettings.GRAIN_STRUCTURE, "Monochrome");
        v1.put(AppSettings.VIDEO_AUDIO, "Speech · Wind filter");
        v1.put(AppSettings.FOCUS_MODE, "Continuous picture autofocus");
        v1.put(AppSettings.UPDATE_CHANNEL, AppSettings.UPDATE_CHANNEL_BETA);
        v1.put(AppSettings.LOCATION, Boolean.FALSE);
        v1.put("capture_format", "RAW + rendered JPEG");
        v1.put("keep_raw", Boolean.TRUE);
        v1.put("dng_policy", "Save DNG beside JPEG (Pictures/RAW)");
        v1.put("auto_export", Boolean.TRUE);
        v1.put("strip_geo", Boolean.TRUE);
        v1.put("speech_enhancement", Boolean.TRUE);
        v1.put("rangefinder_frame_lines", Boolean.TRUE);
        v1.put("grid", Boolean.TRUE);
        v1.put("skin_protection", Boolean.FALSE);
        v1.put("smart_vibrance", Boolean.TRUE);

        Map<String, Object> updates = new HashMap<>();
        Set<String> removals = new HashSet<>();
        assertTrue(AppSettings.computeMigration(v1, updates, removals));

        // Branding & obsolete keys
        assertEquals(AppSettings.BRAIN_LIKA_FILM, updates.get(AppSettings.BRAIN));
        assertEquals("LIKA", updates.get(AppSettings.FILE_PREFIX));
        assertEquals(AppSettings.CURRENT_SETTINGS_VERSION, updates.get(AppSettings.SETTINGS_VERSION));
        assertEquals(AppSettings.PHOTO_OUTPUT_JPEG_ONLY, updates.get(AppSettings.PHOTO_OUTPUT));
        assertEquals(Boolean.TRUE, updates.get(AppSettings.LOCATION));
        assertEquals(AppSettings.UPDATE_CHANNEL_STABLE, updates.get(AppSettings.UPDATE_CHANNEL));
        assertEquals(AppSettings.OVERRIDE_OFF, updates.get(AppSettings.SKIN_PROTECTION_MODE));
        assertEquals(AppSettings.OVERRIDE_AUTO, updates.get(AppSettings.SMART_VIBRANCE_MODE));
        assertEquals(AppSettings.COLOR_SPACE_SRGB, updates.get(AppSettings.COLOR_SPACE));
        assertEquals(AppSettings.GRAIN_STRUCTURE_CHOICES[0], updates.get(AppSettings.GRAIN_STRUCTURE));
        assertEquals(AppSettings.FOCUS_CONTINUOUS, updates.get(AppSettings.FOCUS_MODE));

        assertTrue(removals.contains("capture_format"));
        assertTrue(removals.contains("keep_raw"));
        assertTrue(removals.contains("dng_policy"));
        assertTrue(removals.contains("auto_export"));
        assertTrue(removals.contains("strip_geo"));
        assertTrue(removals.contains("speech_enhancement"));
        assertTrue(removals.contains("rangefinder_frame_lines"));
        assertTrue(removals.contains("grid"));
        assertTrue(removals.contains("skin_protection"));
        assertTrue(removals.contains("smart_vibrance"));

        // Apply to state and verify idempotency + preservation of user recipes/LUTs
        for (String r : removals) v1.remove(r);
        v1.putAll(updates);
        assertEquals("My Neon", v1.get(AppSettings.RECIPE_1_NAME));
        assertEquals("CustomCine", v1.get(AppSettings.LUT_NAME));

        Map<String, Object> updates2 = new HashMap<>();
        Set<String> removals2 = new HashSet<>();
        assertFalse(AppSettings.computeMigration(v1, updates2, removals2));
        assertTrue(updates2.isEmpty());
        assertTrue(removals2.isEmpty());
    }

    @Test
    public void testLoupeAndHorizonProjectionHelpers() {
        assertEquals(AppSettings.LOUPE_MODE_OFF, AppSettings.formatLoupeMode(false, false));
        assertEquals(AppSettings.LOUPE_MODE_MF, AppSettings.formatLoupeMode(true, false));
        assertEquals(AppSettings.LOUPE_MODE_TAP, AppSettings.formatLoupeMode(false, true));
        assertEquals(AppSettings.LOUPE_MODE_BOTH, AppSettings.formatLoupeMode(true, true));

        assertEquals(AppSettings.HORIZON_LEVEL_OFF,
                AppSettings.formatHorizonLevel(false, "Always visible", "Precise (±0.5°)"));
        assertEquals(AppSettings.HORIZON_LEVEL_SMART_NORMAL,
                AppSettings.formatHorizonLevel(true, "Smart (near level only)", "Normal (±1.0°)"));
        assertEquals(AppSettings.HORIZON_LEVEL_SMART_PRECISE,
                AppSettings.formatHorizonLevel(true, "Smart (near level only)", "Precise (±0.5°)"));
        assertEquals(AppSettings.HORIZON_LEVEL_ALWAYS_NORMAL,
                AppSettings.formatHorizonLevel(true, "Always visible", "Normal (±1.0°)"));
        assertEquals(AppSettings.HORIZON_LEVEL_ALWAYS_PRECISE,
                AppSettings.formatHorizonLevel(true, "Always visible", "Precise (±0.5°)"));
    }
}
