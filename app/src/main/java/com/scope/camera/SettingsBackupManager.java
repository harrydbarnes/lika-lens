package com.scope.camera;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;

/**
 * Serializes, exports, shares, validates, and restores user settings,
 * custom film recipes, and custom imported 3D .CUBE LUTs.
 */
final class SettingsBackupManager {
    private static final String TAG = "SettingsBackupManager";
    static final String FORMAT_ID = "lika-lens-backup";
    static final int FORMAT_VERSION = 2;
    static final String FILE_EXTENSION = ".likalens.json";
    private static final int MAX_BACKUP_BYTES = 8 * 1024 * 1024;

    private SettingsBackupManager() { }

    static final class BackupData {
        final String name;
        final String createdAt;
        final String appVersion;
        final int version;
        final Map<String, Object> preferences;
        final Map<String, String> customLuts;

        BackupData(String name, String createdAt, String appVersion, int version,
                   Map<String, Object> preferences, Map<String, String> customLuts) {
            this.name = name;
            this.createdAt = createdAt;
            this.appVersion = appVersion;
            this.version = version;
            this.preferences = preferences;
            this.customLuts = customLuts;
        }

        int getPreferenceCount() {
            return preferences.size();
        }

        int getCustomLutCount() {
            return customLuts.size();
        }

        int getRecipeCount() {
            int count = 0;
            String[] nameKeys = {AppSettings.RECIPE_1_NAME, AppSettings.RECIPE_2_NAME, AppSettings.RECIPE_3_NAME};
            for (String key : nameKeys) {
                Object val = preferences.get(key);
                if (val instanceof String && !((String) val).trim().isEmpty()) {
                    count++;
                }
            }
            return count;
        }

        String formatSummary() {
            int settingsCount = getPreferenceCount();
            int recipesCount = getRecipeCount();
            int lutsCount = getCustomLutCount();
            return String.format(Locale.US, "%d %s · %d %s · %d custom %s",
                    settingsCount, settingsCount == 1 ? "setting" : "settings",
                    recipesCount, recipesCount == 1 ? "recipe" : "recipes",
                    lutsCount, lutsCount == 1 ? "LUT" : "LUTs");
        }
    }

    static final class RestoreResult {
        final String backupName;
        final int restoredPreferences;
        final int restoredRecipes;
        final int restoredLuts;
        final int failedLuts;

        RestoreResult(String backupName, int restoredPreferences, int restoredRecipes, int restoredLuts, int failedLuts) {
            this.backupName = backupName;
            this.restoredPreferences = restoredPreferences;
            this.restoredRecipes = restoredRecipes;
            this.restoredLuts = restoredLuts;
            this.failedLuts = failedLuts;
        }
    }

    static boolean isExcludedPreferenceKey(String key) {
        if (key == null || key.trim().isEmpty()) return true;
        return AppSettings.RAW_SUPPORTED.equals(key)
                || AppSettings.MANUAL_SUPPORTED.equals(key)
                || AppSettings.MANUAL_FOCUS_SUPPORTED.equals(key)
                || AppSettings.VIDEO_HDR_SUPPORTED.equals(key)
                || AppSettings.AUDIO_ZOOM_SUPPORTED.equals(key)
                || AppSettings.LAST_CAMERA_ID.equals(key)
                || AppSettings.LAST_UPDATE_CHECK_TIME.equals(key);
    }

    static String defaultBackupName() {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH-mm", Locale.US);
        return "Lika Lens " + fmt.format(new Date());
    }

    static String sanitizeBackupName(String rawName) {
        if (rawName == null) return "Lika Lens Backup";
        String cleaned = rawName.replaceAll("[\\r\\n\\t]+", " ").trim();
        if (cleaned.isEmpty()) return "Lika Lens Backup";
        if (cleaned.length() > 60) {
            cleaned = cleaned.substring(0, 60).trim();
        }
        return cleaned.isEmpty() ? "Lika Lens Backup" : cleaned;
    }

    static String buildBackupFileName(String rawName) {
        String base = sanitizeBackupName(rawName);
        String lower = base.toLowerCase(Locale.US);
        if (lower.endsWith(FILE_EXTENSION)) {
            base = base.substring(0, base.length() - FILE_EXTENSION.length());
        } else if (lower.endsWith(".json")) {
            base = base.substring(0, base.length() - 5);
        }
        String safe = base.replaceAll("[^A-Za-z0-9._-]+", "_");
        safe = safe.replaceAll("^[_.-]+|[_.-]+$", "");
        if (safe.isEmpty()) safe = "Lika_Lens_Backup";
        return safe + FILE_EXTENSION;
    }

    static String currentIsoTimestamp() {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        return fmt.format(new Date());
    }

    static String serializeBackup(String backupName, String appVersion, String createdAtIso,
                                  Map<String, ?> allPrefs, Map<String, String> customLuts) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("format", FORMAT_ID);
        root.put("version", FORMAT_VERSION);
        root.put("name", sanitizeBackupName(backupName));
        root.put("createdAt", createdAtIso != null && !createdAtIso.trim().isEmpty() ? createdAtIso : currentIsoTimestamp());
        if (appVersion != null && !appVersion.trim().isEmpty()) {
            root.put("appVersion", appVersion.trim());
        }

        JSONObject prefsJson = new JSONObject();
        if (allPrefs != null) {
            TreeMap<String, Object> sorted = new TreeMap<>(allPrefs);
            for (Map.Entry<String, Object> entry : sorted.entrySet()) {
                String key = entry.getKey();
                Object val = entry.getValue();
                if (isExcludedPreferenceKey(key) || val == null) continue;
                JSONObject typed = encodeTypedPreference(val);
                if (typed != null) {
                    prefsJson.put(key, typed);
                }
            }
        }
        root.put("preferences", prefsJson);

        JSONArray lutsJson = new JSONArray();
        if (customLuts != null) {
            TreeMap<String, String> sortedLuts = new TreeMap<>(customLuts);
            for (Map.Entry<String, String> entry : sortedLuts.entrySet()) {
                String lutName = entry.getKey();
                String cubeText = entry.getValue();
                if (lutName == null || lutName.trim().isEmpty() || cubeText == null || cubeText.trim().isEmpty()) continue;
                JSONObject lutObj = new JSONObject();
                lutObj.put("name", LutManager.sanitizeLutName(lutName));
                lutObj.put("cubeText", cubeText);
                lutsJson.put(lutObj);
            }
        }
        root.put("customLuts", lutsJson);
        return root.toString(2);
    }

    private static JSONObject encodeTypedPreference(Object val) throws JSONException {
        JSONObject obj = new JSONObject();
        if (val instanceof Boolean) {
            obj.put("type", "boolean");
            obj.put("value", (Boolean) val);
            return obj;
        }
        if (val instanceof Integer) {
            obj.put("type", "int");
            obj.put("value", ((Integer) val).intValue());
            return obj;
        }
        if (val instanceof Long) {
            obj.put("type", "long");
            obj.put("value", ((Long) val).longValue());
            return obj;
        }
        if (val instanceof Float) {
            float f = (Float) val;
            if (!Float.isFinite(f)) return null;
            obj.put("type", "float");
            obj.put("value", (double) f);
            return obj;
        }
        if (val instanceof String) {
            obj.put("type", "string");
            obj.put("value", (String) val);
            return obj;
        }
        if (val instanceof Set<?>) {
            JSONArray arr = new JSONArray();
            for (Object item : (Set<?>) val) {
                if (item != null) arr.put(String.valueOf(item));
            }
            obj.put("type", "string_set");
            obj.put("value", arr);
            return obj;
        }
        return null;
    }

    static BackupData parseBackup(String jsonText) {
        if (jsonText == null || jsonText.trim().isEmpty()) {
            throw new IllegalArgumentException("Backup file is empty.");
        }
        try {
            JSONObject root = new JSONObject(jsonText);
            String format = root.optString("format", "");
            if (!FORMAT_ID.equals(format)) {
                throw new IllegalArgumentException("Not a Lika Lens backup file.");
            }
            int version = root.optInt("version", 0);
            if (version < 1 || version > FORMAT_VERSION) {
                throw new IllegalArgumentException("Unsupported backup version: " + version);
            }
            String name = sanitizeBackupName(root.optString("name", "Lika Lens Backup"));
            String createdAt = root.optString("createdAt", "");
            String appVersion = root.optString("appVersion", "");

            Map<String, Object> prefs = new LinkedHashMap<>();
            JSONObject prefsJson = root.optJSONObject("preferences");
            if (prefsJson != null) {
                Iterator<String> keys = prefsJson.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    if (isExcludedPreferenceKey(key)) continue;
                    Object raw = prefsJson.opt(key);
                    Object decoded = decodePreferenceValue(raw);
                    if (decoded != null) {
                        prefs.put(key, decoded);
                    }
                }
            }
            if (version == 1) {
                Map<String, Object> updates = new LinkedHashMap<>();
                Set<String> removals = new HashSet<>();
                if (AppSettings.computeMigration(prefs, updates, removals)) {
                    for (String rem : removals) {
                        prefs.remove(rem);
                    }
                    prefs.putAll(updates);
                }
            }

            Map<String, String> customLuts = new LinkedHashMap<>();
            JSONArray lutsJson = root.optJSONArray("customLuts");
            if (lutsJson != null) {
                for (int i = 0; i < lutsJson.length(); i++) {
                    JSONObject lutObj = lutsJson.optJSONObject(i);
                    if (lutObj == null) continue;
                    String lutName = LutManager.sanitizeLutName(lutObj.optString("name", ""));
                    String cubeText = lutObj.optString("cubeText", "");
                    if (!lutName.isEmpty() && !cubeText.trim().isEmpty() && !LutManager.isBuiltIn(lutName)) {
                        customLuts.put(lutName, cubeText);
                    }
                }
            }

            return new BackupData(name, createdAt, appVersion, version, prefs, customLuts);
        } catch (JSONException e) {
            throw new IllegalArgumentException("Invalid backup JSON format.", e);
        }
    }

    private static Object decodePreferenceValue(Object raw) {
        if (raw == null || raw == JSONObject.NULL) return null;
        if (raw instanceof JSONObject) {
            JSONObject obj = (JSONObject) raw;
            String type = obj.optString("type", "");
            Object val = obj.opt("value");
            if (val == null || val == JSONObject.NULL) return null;
            switch (type) {
                case "boolean":
                    if (val instanceof Boolean) return val;
                    if (val instanceof String) return Boolean.parseBoolean((String) val);
                    return null;
                case "int":
                    if (val instanceof Number) return ((Number) val).intValue();
                    if (val instanceof String) {
                        try { return Integer.parseInt((String) val); } catch (NumberFormatException ignored) { return null; }
                    }
                    return null;
                case "long":
                    if (val instanceof Number) return ((Number) val).longValue();
                    if (val instanceof String) {
                        try { return Long.parseLong((String) val); } catch (NumberFormatException ignored) { return null; }
                    }
                    return null;
                case "float":
                    if (val instanceof Number) return ((Number) val).floatValue();
                    if (val instanceof String) {
                        try { return Float.parseFloat((String) val); } catch (NumberFormatException ignored) { return null; }
                    }
                    return null;
                case "string":
                    return String.valueOf(val);
                case "string_set":
                    if (val instanceof JSONArray) {
                        JSONArray arr = (JSONArray) val;
                        Set<String> set = new HashSet<>();
                        for (int i = 0; i < arr.length(); i++) {
                            if (!arr.isNull(i)) set.add(String.valueOf(arr.opt(i)));
                        }
                        return set;
                    }
                    return null;
                default:
                    return null;
            }
        }
        // Permissive fallback if raw primitive values are supplied directly
        if (raw instanceof Boolean || raw instanceof Integer || raw instanceof String) return raw;
        if (raw instanceof Long) {
            long l = (Long) raw;
            if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) return (int) l;
            return l;
        }
        if (raw instanceof Double) return ((Double) raw).floatValue();
        return null;
    }

    static Map<String, String> collectCustomLuts(Context context) {
        Map<String, String> map = new LinkedHashMap<>();
        List<String> names = LutManager.getCustomLutNames(context);
        for (String name : names) {
            String text = LutManager.readCustomLutText(context, name);
            if (text != null && !text.trim().isEmpty()) {
                map.put(name, text);
            }
        }
        return map;
    }

    static int countLocalCustomRecipes(Context context) {
        int count = 0;
        for (int i = 1; i <= 3; i++) {
            String name = AppSettings.getCustomRecipeName(context, i);
            if (name != null && !name.trim().isEmpty()) count++;
        }
        return count;
    }

    static String createBackupJson(Context context, String backupName) throws JSONException {
        Map<String, ?> prefs = AppSettings.prefs(context).getAll();
        Map<String, String> luts = collectCustomLuts(context);
        String appVersion = AppUpdateManager.getAppVersionName(context);
        return serializeBackup(backupName, appVersion, currentIsoTimestamp(), prefs, luts);
    }

    @SuppressWarnings("unchecked")
    static RestoreResult restoreBackup(Context context, BackupData data) {
        if (context == null || data == null) {
            throw new IllegalArgumentException("Missing backup data");
        }
        int restoredLuts = 0;
        int failedLuts = 0;
        for (Map.Entry<String, String> entry : data.customLuts.entrySet()) {
            if (LutManager.saveCustomLut(context, entry.getKey(), entry.getValue())) {
                restoredLuts++;
            } else {
                failedLuts++;
            }
        }

        SharedPreferences prefs = AppSettings.prefs(context);
        // Preserve hardware-probed capability keys and update timestamp of this device
        boolean hadRaw = prefs.contains(AppSettings.RAW_SUPPORTED);
        boolean rawSupported = prefs.getBoolean(AppSettings.RAW_SUPPORTED, false);
        boolean hadManual = prefs.contains(AppSettings.MANUAL_SUPPORTED);
        boolean manualSupported = prefs.getBoolean(AppSettings.MANUAL_SUPPORTED, false);
        boolean hadMf = prefs.contains(AppSettings.MANUAL_FOCUS_SUPPORTED);
        boolean mfSupported = prefs.getBoolean(AppSettings.MANUAL_FOCUS_SUPPORTED, false);
        boolean hadVideoHdr = prefs.contains(AppSettings.VIDEO_HDR_SUPPORTED);
        boolean videoHdrSupported = prefs.getBoolean(AppSettings.VIDEO_HDR_SUPPORTED, false);
        boolean hadAudioZoom = prefs.contains(AppSettings.AUDIO_ZOOM_SUPPORTED);
        boolean audioZoomSupported = prefs.getBoolean(AppSettings.AUDIO_ZOOM_SUPPORTED, false);
        String lastCamId = prefs.getString(AppSettings.LAST_CAMERA_ID, null);
        long lastUpdateCheck = prefs.getLong(AppSettings.LAST_UPDATE_CHECK_TIME, 0L);

        SharedPreferences.Editor editor = prefs.edit().clear();
        if (hadRaw) editor.putBoolean(AppSettings.RAW_SUPPORTED, rawSupported);
        if (hadManual) editor.putBoolean(AppSettings.MANUAL_SUPPORTED, manualSupported);
        if (hadMf) editor.putBoolean(AppSettings.MANUAL_FOCUS_SUPPORTED, mfSupported);
        if (hadVideoHdr) editor.putBoolean(AppSettings.VIDEO_HDR_SUPPORTED, videoHdrSupported);
        if (hadAudioZoom) editor.putBoolean(AppSettings.AUDIO_ZOOM_SUPPORTED, audioZoomSupported);
        if (lastCamId != null) editor.putString(AppSettings.LAST_CAMERA_ID, lastCamId);
        if (lastUpdateCheck > 0L) editor.putLong(AppSettings.LAST_UPDATE_CHECK_TIME, lastUpdateCheck);

        for (Map.Entry<String, Object> entry : data.preferences.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();
            if (isExcludedPreferenceKey(key) || val == null) continue;
            if (val instanceof Boolean) editor.putBoolean(key, (Boolean) val);
            else if (val instanceof Integer) editor.putInt(key, (Integer) val);
            else if (val instanceof Long) editor.putLong(key, (Long) val);
            else if (val instanceof Float) editor.putFloat(key, (Float) val);
            else if (val instanceof String) editor.putString(key, (String) val);
            else if (val instanceof Set<?>) editor.putStringSet(key, (Set<String>) val);
        }
        editor.commit();

        AppSettings.migrateIfNeeded(context);
        LiveLookPreview.clearCache();

        return new RestoreResult(
                data.name,
                data.getPreferenceCount(),
                data.getRecipeCount(),
                restoredLuts,
                failedLuts
        );
    }

    static Uri writeBackupForShare(Context context, String backupName, String jsonText) throws IOException {
        File backupDir = BackupFileProvider.getBackupCacheDir(context);
        if (!backupDir.exists() && !backupDir.mkdirs()) {
            throw new IOException("Could not create backup cache directory");
        }
        File[] existing = backupDir.listFiles();
        if (existing != null) {
            for (File old : existing) {
                if (old.isFile() && !old.delete()) {
                    Log.w(TAG, "Failed to clean old cached backup: " + old.getName());
                }
            }
        }
        File target = new File(backupDir, buildBackupFileName(backupName));
        try (FileOutputStream out = new FileOutputStream(target)) {
            out.write(jsonText.getBytes(StandardCharsets.UTF_8));
        }
        return BackupFileProvider.getUriForFile(context, target);
    }

    static void writeTextToUri(Context context, Uri uri, String jsonText) throws IOException {
        try (OutputStream out = context.getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) throw new IOException("Unable to open output stream for URI");
            out.write(jsonText.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
    }

    static String readTextFromUri(Context context, Uri uri) throws IOException {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Unable to open backup file");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int total = 0;
            int len;
            while ((len = in.read(buf)) > 0) {
                total += len;
                if (total > MAX_BACKUP_BYTES) {
                    throw new IOException("Backup file is too large");
                }
                out.write(buf, 0, len);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
}
