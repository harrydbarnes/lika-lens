package com.scope.camera;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for SettingsBackupManager naming, typed serialization, parsing, and validation.
 */
public class SettingsBackupManagerTest {

    @Test
    public void testSanitizeAndBuildBackupFileName() {
        assertEquals("Lika Lens Backup", SettingsBackupManager.sanitizeBackupName(null));
        assertEquals("Lika Lens Backup", SettingsBackupManager.sanitizeBackupName("   \n  "));
        assertEquals("Street Portra 400", SettingsBackupManager.sanitizeBackupName("  Street Portra 400 \t "));

        assertEquals("Street_Portra_400.likalens.json",
                SettingsBackupManager.buildBackupFileName("Street Portra 400"));
        assertEquals("My_Setup.likalens.json",
                SettingsBackupManager.buildBackupFileName("My Setup.json"));
        assertEquals("My_Setup.likalens.json",
                SettingsBackupManager.buildBackupFileName("My Setup.likalens.json"));
        assertEquals("Lika_Lens_Backup.likalens.json",
                SettingsBackupManager.buildBackupFileName("///..///"));
    }

    @Test
    public void testSerializeAndParsePreservesTypesRecipesAndCustomLuts() throws Exception {
        Map<String, Object> prefs = new LinkedHashMap<>();
        prefs.put(AppSettings.BRAIN, AppSettings.BRAIN_KODACHROME);
        prefs.put(AppSettings.CONTRAST, 118);
        prefs.put(AppSettings.GRAIN, 34);
        prefs.put(AppSettings.EASY_MODE, false);
        prefs.put(AppSettings.LAST_UPDATE_CHECK_TIME, 1731654321000L); // excluded transient key
        prefs.put(AppSettings.UPDATE_CACHE_ETAG_BETA, "\"etag\""); // excluded transient key
        prefs.put(AppSettings.UPDATE_CACHE_JSON_BETA, "[{}]"); // excluded transient key
        prefs.put(AppSettings.UPDATE_CACHE_ETAG_STABLE, "\"etag2\""); // excluded transient key
        prefs.put(AppSettings.UPDATE_CACHE_JSON_STABLE, "{}"); // excluded transient key
        prefs.put(AppSettings.UPDATE_CACHED_AVAILABLE_TAG, "v0.1.109"); // excluded transient key
        prefs.put(AppSettings.UPDATE_CACHED_AVAILABLE_CHANNEL, AppSettings.UPDATE_CHANNEL_BETA); // excluded transient key
        prefs.put(AppSettings.RAW_SUPPORTED, true); // excluded hardware flag
        prefs.put(AppSettings.MANUAL_SUPPORTED, true); // excluded hardware flag
        prefs.put(AppSettings.LAST_CAMERA_ID, "0"); // excluded hardware key
        prefs.put(AppSettings.RECIPE_1_NAME, "Neon Night");
        prefs.put(AppSettings.RECIPE_1_DATA, "{\"brain\":\"LIKA / FILM\"}");
        prefs.put("custom_float", 1.25f);
        prefs.put("custom_long", 9876543210L);
        prefs.put("custom_tags", new HashSet<>(Arrays.asList("35mm", "night")));

        Map<String, String> luts = new HashMap<>();
        luts.put("Cinestill 800T", "TITLE \"Cinestill\"\nLUT_3D_SIZE 2\n0 0 0\n1 0 0\n0 1 0\n1 1 0\n0 0 1\n1 0 1\n0 1 1\n1 1 1\n");

        String json = SettingsBackupManager.serializeBackup(
                "Tokyo Night Walk",
                "0.1.42",
                "2026-11-15T07:15:00Z",
                prefs,
                luts
        );

        SettingsBackupManager.BackupData parsed = SettingsBackupManager.parseBackup(json);
        assertEquals("Tokyo Night Walk", parsed.name);
        assertEquals("2026-11-15T07:15:00Z", parsed.createdAt);
        assertEquals("0.1.42", parsed.appVersion);

        // Hardware/transient keys must not leak into backup
        assertFalse(parsed.preferences.containsKey(AppSettings.RAW_SUPPORTED));
        assertFalse(parsed.preferences.containsKey(AppSettings.MANUAL_SUPPORTED));
        assertFalse(parsed.preferences.containsKey(AppSettings.LAST_CAMERA_ID));
        assertFalse(parsed.preferences.containsKey(AppSettings.LAST_UPDATE_CHECK_TIME));
        assertFalse(parsed.preferences.containsKey(AppSettings.UPDATE_CACHE_ETAG_BETA));
        assertFalse(parsed.preferences.containsKey(AppSettings.UPDATE_CACHE_JSON_BETA));
        assertFalse(parsed.preferences.containsKey(AppSettings.UPDATE_CACHE_ETAG_STABLE));
        assertFalse(parsed.preferences.containsKey(AppSettings.UPDATE_CACHE_JSON_STABLE));
        assertFalse(parsed.preferences.containsKey(AppSettings.UPDATE_CACHED_AVAILABLE_TAG));
        assertFalse(parsed.preferences.containsKey(AppSettings.UPDATE_CACHED_AVAILABLE_CHANNEL));

        // Types must round-trip faithfully for SharedPreferences
        assertEquals(AppSettings.BRAIN_KODACHROME, parsed.preferences.get(AppSettings.BRAIN));
        assertTrue(parsed.preferences.get(AppSettings.CONTRAST) instanceof Integer);
        assertEquals(118, parsed.preferences.get(AppSettings.CONTRAST));
        assertTrue(parsed.preferences.get(AppSettings.EASY_MODE) instanceof Boolean);
        assertEquals(Boolean.FALSE, parsed.preferences.get(AppSettings.EASY_MODE));
        assertTrue(parsed.preferences.get("custom_float") instanceof Float);
        assertEquals(1.25f, (Float) parsed.preferences.get("custom_float"), 0.0001f);
        assertTrue(parsed.preferences.get("custom_long") instanceof Long);
        assertEquals(9876543210L, ((Long) parsed.preferences.get("custom_long")).longValue());
        assertTrue(parsed.preferences.get("custom_tags") instanceof Set<?>);
        assertTrue(((Set<?>) parsed.preferences.get("custom_tags")).contains("35mm"));

        assertEquals(1, parsed.getRecipeCount());
        assertEquals(1, parsed.getCustomLutCount());
        assertTrue(parsed.customLuts.containsKey("Cinestill 800T"));
        assertTrue(parsed.formatSummary().contains("1 recipe"));
        assertTrue(parsed.formatSummary().contains("1 custom LUT"));
    }

    @Test
    public void testParseBackupRejectsInvalidFormatOrEmptyPayload() {
        assertThrows(IllegalArgumentException.class, () -> SettingsBackupManager.parseBackup(""));
        assertThrows(IllegalArgumentException.class, () -> SettingsBackupManager.parseBackup("{not valid json"));
        assertThrows(IllegalArgumentException.class, () ->
                SettingsBackupManager.parseBackup("{\"format\":\"other-app\",\"version\":1}"));
        assertThrows(IllegalArgumentException.class, () ->
                SettingsBackupManager.parseBackup("{\"format\":\"lika-lens-backup\",\"version\":99}"));
    }
}
