package com.scope.camera;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class AppSettings {
    private static final String TAG = "AppSettings";
    static final String PREFS = "scope_camera_preferences";

    static final String SETTINGS_VERSION = "settings_schema_version";
    static final int CURRENT_SETTINGS_VERSION = 2;

    static final String BRAIN = "brain";
    static final String BRAIN_LIKA_FILM = "LIKA / FILM";
    static final String BRAIN_KODACHROME = "KODACHROME / 64";
    static final String BRAIN_FUJI_ETERNA = "FUJI / ETERNA";
    static final String BRAIN_BLEACH_BYPASS = "BLEACH BYPASS";
    static final String BRAIN_MONO_SILVER = "MONO / SILVER";
    static final String BRAIN_NEUTRAL_RAW = "NEUTRAL / RAW";

    static final String[] BRAIN_CHOICES = {
            BRAIN_LIKA_FILM,
            BRAIN_KODACHROME,
            BRAIN_FUJI_ETERNA,
            BRAIN_BLEACH_BYPASS,
            BRAIN_MONO_SILVER,
            BRAIN_NEUTRAL_RAW
    };

    static final String[] BRAIN_DESCRIPTIONS = {
            "LIKA / FILM · Kodak Vision3 500T 35mm Cinema Stock",
            "KODACHROME / 64 · Vintage Color Reversal Film",
            "FUJI / ETERNA · Soft Cinema Negative & Subtle Cyan Tones",
            "BLEACH BYPASS · Skip-Bleach ENR High Contrast Silver",
            "MONO / SILVER · Tri-X 400 Silver Gelatin Monochrome",
            "NEUTRAL / RAW · Linear Unstyled Sensor Baseline"
    };

    // Consolidated Photo Output & Editable Archive Management
    static final String PHOTO_OUTPUT = "photo_output";
    static final String PHOTO_OUTPUT_JPEG_ONLY = "Processed JPEG only";
    static final String PHOTO_OUTPUT_JPEG_DNG = "JPEG + DNG original";
    static final String PHOTO_OUTPUT_DNG_ONLY = "DNG original only";
    static final String[] PHOTO_OUTPUT_CHOICES = {
            PHOTO_OUTPUT_JPEG_ONLY,
            PHOTO_OUTPUT_JPEG_DNG,
            PHOTO_OUTPUT_DNG_ONLY
    };

    static final String EDITABLE_ARCHIVE_LIMIT = "editable_archive_limit";
    static final String EDITABLE_ARCHIVE_256MB = "256 MB";
    static final String EDITABLE_ARCHIVE_512MB = "512 MB (Recommended)";
    static final String EDITABLE_ARCHIVE_1GB = "1 GB";
    static final String EDITABLE_ARCHIVE_2GB = "2 GB";
    static final String EDITABLE_ARCHIVE_UNLIMITED = "Unlimited";
    static final String[] EDITABLE_ARCHIVE_LIMIT_CHOICES = {
            EDITABLE_ARCHIVE_256MB,
            EDITABLE_ARCHIVE_512MB,
            EDITABLE_ARCHIVE_1GB,
            EDITABLE_ARCHIVE_2GB,
            EDITABLE_ARCHIVE_UNLIMITED
    };

    static final String RAW_SIZE = "raw_size";
    static final String TIMER = "timer";
    static final String ASPECT_RATIO = "aspect_ratio";
    static final String FLASH = "flash";
    static final String PIVOT = "pivot";
    static final String EXPOSURE = "exposure_ev_tenths";
    static final String WARMTH = "warmth";
    static final String EXPOSURE_MODE = "exposure_mode";
    static final String MANUAL_SHUTTER = "manual_shutter";
    static final String MANUAL_ISO = "manual_iso";
    static final String WHITE_BALANCE = "white_balance_kelvin";
    static final String WHITE_BALANCE_MODE = "white_balance_mode";
    static final String CONTRAST = "contrast";
    static final String SATURATION = "saturation";
    static final String COLOR_ROLLOFF = "colour_rolloff";
    static final String HIGHLIGHTS = "highlights";
    static final String SHADOWS = "shadows";
    static final String GRAIN = "grain";
    static final String GRAIN_STRUCTURE = "grain_structure";
    static final String[] GRAIN_STRUCTURE_CHOICES = {
            "Fine · 35 mm",
            "Soft · 16 mm",
            "Coarse · 8 mm"
    };
    static final String HALATION = "halation";
    static final String SHARPEN = "sharpen";
    static final String COLOR_NR = "color_nr";
    static final String VIGNETTE = "vignette";
    static final String GRID_STYLE = "grid_style";
    static final String HAPTICS = "haptics";
    static final String HISTOGRAM = "histogram";
    static final String FOCUS_PEAKING = "focus_peaking";
    static final String ZEBRA = "zebra";
    static final String LOCATION = "location";

    static final String COLOR_SPACE = "color_space";
    static final String COLOR_SPACE_SRGB = "sRGB · Standard Display";
    static final String COLOR_SPACE_P3 = "Display P3 · Wide Gamut";
    static final String[] COLOR_SPACE_CHOICES = {
            COLOR_SPACE_SRGB,
            COLOR_SPACE_P3
    };

    static final String FILE_PREFIX = "file_prefix";
    static final String RAW_SUPPORTED = "raw_supported";
    static final String MANUAL_SUPPORTED = "manual_supported";
    static final String MANUAL_FOCUS_SUPPORTED = "manual_focus_supported";
    static final String VIDEO_HDR_SUPPORTED = "video_hdr_supported";
    static final String AUDIO_ZOOM_SUPPORTED = "audio_zoom_supported";
    static final String VISUAL_LEVEL = "visual_level";
    static final String DEFAULT_LENS = "default_lens";
    static final String LAST_CAMERA_ID = "last_camera_id";
    static final String VIDEO_FPS = "video_fps";
    static final String[] VIDEO_FPS_CHOICES = {
            "24 fps (Cinematic)",
            "25 fps (PAL)",
            "30 fps",
            "60 fps"
    };
    static final String VIDEO_ASPECT = "video_aspect";
    static final String VIDEO_BITRATE = "video_bitrate";
    static final String VIDEO_SHUTTER_MODE = "video_shutter_mode";
    static final String VIDEO_SHUTTER_180 = "180° Shutter (Auto ISO)";
    static final String VIDEO_SHUTTER_AUTO = "Auto exposure (AE)";
    static final String[] VIDEO_SHUTTER_MODES = {
            VIDEO_SHUTTER_180,
            VIDEO_SHUTTER_AUTO
    };
    static final String SHUTTER_SOUND = "shutter_sound";

    // Processing & Colour Pipeline Keys
    static final String EASY_MODE = "easy_mode";
    static final String OVERRIDE_AUTO = "Auto (Brain default)";
    static final String OVERRIDE_ON = "On";
    static final String OVERRIDE_OFF = "Off";
    static final String[] OVERRIDE_CHOICES = {
            OVERRIDE_AUTO,
            OVERRIDE_ON,
            OVERRIDE_OFF
    };
    static final String SKIN_PROTECTION_MODE = "skin_protection_mode";
    static final String SMART_VIBRANCE_MODE = "smart_vibrance_mode";

    static final String HIGHLIGHT_RECOVERY = "highlight_recovery";
    static final String HL_REC_AUTO = "Auto (Scene-adaptive)";
    static final String HL_REC_ADAPTIVE = "Adaptive blend";
    static final String HL_REC_SUBTRACTIVE = "Subtractive roll-off";
    static final String HL_REC_HARD = "Hard clip";
    static final String[] HIGHLIGHT_RECOVERY_CHOICES = {
            HL_REC_AUTO,
            HL_REC_ADAPTIVE,
            HL_REC_SUBTRACTIVE,
            HL_REC_HARD
    };

    static final String SHADOW_PROFILE = "shadow_profile";
    static final String SHADOW_PROFILE_AUTO = "Auto (Brain default)";
    static final String SHADOW_PROFILE_DEEP = "Deep Cinema Blacks";
    static final String SHADOW_PROFILE_NEUTRAL = "Neutral / Open Shadows";
    static final String SHADOW_PROFILE_LIFTED = "Lifted Vintage Blacks";
    static final String[] SHADOW_PROFILE_CHOICES = {
            SHADOW_PROFILE_AUTO,
            SHADOW_PROFILE_DEEP,
            SHADOW_PROFILE_NEUTRAL,
            SHADOW_PROFILE_LIFTED
    };

    static final String TONE_CURVE = "tone_curve";
    static final String[] TONE_CURVE_CHOICES = {
            "Filmic S-Curve",
            "Linear Response",
            "Log / Flat"
    };
    static final String GRAIN_RESPONSE = "grain_response";
    static final String ANTI_BANDING = "anti_banding";

    // 3D LUT (Color Lookup Table) Keys
    static final String LUT_NAME = "lut_name";
    static final String LUT_INTENSITY = "lut_intensity";
    static final int DEFAULT_LUT_INTENSITY = 100;

    // Customisable Adjust Controls
    static final String ADJUST_CONTROLS_ORDER = "adjust_controls_order";
    static final List<String> ALL_ADJUST_CONTROLS = Collections.unmodifiableList(Arrays.asList(
            EXPOSURE,
            WARMTH,
            HIGHLIGHTS,
            SHADOWS,
            CONTRAST,
            SATURATION,
            COLOR_ROLLOFF,
            GRAIN,
            GRAIN_STRUCTURE,
            HALATION,
            SHARPEN,
            PIVOT,
            TONE_CURVE,
            SHADOW_PROFILE,
            HIGHLIGHT_RECOVERY,
            COLOR_NR,
            VIGNETTE,
            LUT_INTENSITY
    ));
    static final List<String> DEFAULT_ADJUST_CONTROLS = Collections.unmodifiableList(Arrays.asList(
            EXPOSURE,
            WARMTH,
            HIGHLIGHTS,
            SHADOWS,
            CONTRAST,
            SATURATION,
            GRAIN,
            HALATION,
            SHARPEN
    ));

    // Focus & Loupe Keys
    static final String FOCUS_MODE = "focus_mode";
    static final String FOCUS_CONTINUOUS = "Continuous autofocus";
    static final String FOCUS_SINGLE = "Single autofocus";
    static final String FOCUS_MANUAL = "Manual focus (MF)";
    static final String[] FOCUS_MODE_CHOICES = {
            FOCUS_CONTINUOUS,
            FOCUS_SINGLE,
            FOCUS_MANUAL
    };
    static final String MANUAL_FOCUS_DISTANCE = "manual_focus_distance";
    static final String LOUPE_ON_MF_ADJUST = "loupe_on_mf_adjust";

    // Viewfinder & Camera Shooting Keys
    static final String VIEWFINDER_LONG_PRESS = "viewfinder_long_press";
    static final String VIEWFINDER_DOUBLE_TAP = "viewfinder_double_tap";
    static final String RETICLE_METERING = "reticle_metering";
    static final String HORIZON_SENSITIVITY = "horizon_sensitivity";
    static final String HORIZON_MODE = "horizon_mode";
    static final String VOLUME_ROCKER = "volume_rocker";
    static final String SHUTTER_WINK = "shutter_wink";
    static final String LEFT_HAND_MODE = "left_hand_mode";
    static final String LEFT_HAND_OFF = "Off (Standard)";
    static final String LEFT_HAND_MAIN = "On · Main buttons";
    static final String LEFT_HAND_FURTHER = "On · Full (swap camera & gallery)";
    static final String[] LEFT_HAND_MODE_CHOICES = {
            LEFT_HAND_OFF,
            LEFT_HAND_MAIN,
            LEFT_HAND_FURTHER
    };

    // Pro UX & Viewfinder Feature Keys
    static final String LIVE_FILM_PREVIEW = "live_film_preview";
    static final String OPTICAL_LOUPE = "optical_loupe";
    static final String HISTOGRAM_MODE = "histogram_mode";
    static final String SHOW_TELEMETRY = "show_telemetry";
    static final String TIMER_LIGHT = "timer_light";
    static final String DIRTY_LENS_WARNING = "dirty_lens_warning";
    static final String LAUNCH_MODE = "launch_mode";
    static final String[] LAUNCH_MODES = {"Photo (default)", "Cinematic Video", "Last used"};
    static final String LAST_USED_MODE = "last_used_mode";
    static final String FRAME_COUNTER = "frame_counter";
    static final String PALM_TIMER = "palm_timer";
    static final String FILM_PUSH_PULL = "film_push_pull";
    static final String[] FILM_PUSH_PULL_CHOICES = {
            "Box Speed · EI 400 (Standard)",
            "+1 Stop Push · EI 800 (Steep Contrast & Grain)",
            "+2 Stop Push · EI 1600 (Gritty Night Stock)",
            "-1 Stop Pull · EI 200 (Soft Pastel Latitude)"
    };
    static final String ULTRA_HDR = "ultra_hdr";
    static final String ASTROPHOTOGRAPHY = "astrophotography";
    static final String[] ASTRO_CHOICES = {"Off", "Auto (when stationary at night)", "Always on (Night Stacking)"};

    // Custom Film Recipes (User Presets)
    static final String RECIPE_1_NAME = "recipe_1_name";
    static final String RECIPE_1_DATA = "recipe_1_data";
    static final String RECIPE_2_NAME = "recipe_2_name";
    static final String RECIPE_2_DATA = "recipe_2_data";
    static final String RECIPE_3_NAME = "recipe_3_name";
    static final String RECIPE_3_DATA = "recipe_3_data";

    // File Management & Output Keys
    static final String FOLDER_ORG = "folder_organization";
    static final String FILE_NAMING = "file_naming";
    static final String JPEG_QUALITY = "jpeg_quality";
    static final String EXIF_PARAMS = "exif_params";
    static final String CUSTOM_PREFIX_VALUE = "custom_prefix_value";
    static final String FRAME_BORDER = "frame_border";
    static final String[] FRAME_BORDER_CHOICES = {
            "None",
            "35mm Film Rebate",
            "Medium Format 6×6 Rebate",
            "Cinemascope 2.39:1 Matte",
            "Museum Matte"
    };

    // Optical, Hardware & Video Keys
    static final String OPTICAL_CORRECTION = "optical_correction";
    static final String MIRROR_FRONT = "mirror_front";
    static final String STABILIZATION = "stabilization";
    static final String[] STABILIZATION_CHOICES = {
            "Cinema OIS + EIS",
            "Locked (Tripod)",
            "Active (Action run)",
            "Optical only (OIS)",
            "Off (Gimbal / Manual)"
    };
    static final String VIDEO_CODEC = "video_codec";
    static final String VIDEO_AUDIO = "video_audio";
    static final String[] VIDEO_AUDIO_CHOICES = {
            "Camcorder · Stereo",
            "Speech enhanced · Voice",
            "Unprocessed · Raw Mic",
            "Muted · No Audio"
    };
    static final String VIDEO_HDR = "video_hdr";
    static final String AUDIO_ZOOM = "audio_zoom";

    // Animated GIF Keys
    static final String GIF_FPS = "gif_fps";
    static final String GIF_RESOLUTION = "gif_resolution";
    static final String GIF_BOOMERANG = "gif_boomerang";

    // In-App Update Keys
    static final String UPDATE_CHANNEL = "update_channel";
    static final String UPDATE_CHANNEL_BETA = "Beta (Every Commit / Action)";
    static final String UPDATE_CHANNEL_STABLE = "Stable (Recommended)";
    static final String[] UPDATE_CHANNEL_CHOICES = {
            UPDATE_CHANNEL_STABLE,
            UPDATE_CHANNEL_BETA
    };
    static final String AUTO_CHECK_UPDATES = "auto_check_updates";
    static final String LAST_UPDATE_CHECK_TIME = "last_update_check_time";
    static final String UPDATE_CACHE_ETAG_BETA = "update_cache_etag_beta";
    static final String UPDATE_CACHE_JSON_BETA = "update_cache_json_beta";
    static final String UPDATE_CACHE_ETAG_STABLE = "update_cache_etag_stable";
    static final String UPDATE_CACHE_JSON_STABLE = "update_cache_json_stable";
    static final String UPDATE_CACHED_AVAILABLE_TAG = "update_cached_available_tag";
    static final String UPDATE_CACHED_AVAILABLE_CHANNEL = "update_cached_available_channel";

    // Obsolete v1 keys retained privately for migration & cleanup only
    private static final String LEGACY_CAPTURE_FORMAT = "capture_format";
    private static final String LEGACY_KEEP_RAW = "keep_raw";
    private static final String LEGACY_DNG_POLICY = "dng_policy";
    private static final String LEGACY_AUTO_EXPORT = "auto_export";
    private static final String LEGACY_STRIP_GEO = "strip_geo";
    private static final String LEGACY_SPEECH_ENHANCEMENT = "speech_enhancement";
    private static final String LEGACY_RANGEFINDER_LINES = "rangefinder_frame_lines";
    private static final String LEGACY_GRID = "grid";
    private static final String LEGACY_SKIN_PROTECTION = "skin_protection";
    private static final String LEGACY_SMART_VIBRANCE = "smart_vibrance";

    static final String[] ASPECT_RATIOS = {
            "2.39:1",
            "1.85:1",
            "1.66:1",
            "16:9",
            "3:2",
            "4:3",
            "1:1"
    };

    static final String[] ASPECT_RATIO_LABELS = {
            "2.39:1 · Cinemascope",
            "1.85:1 · Flat Widescreen",
            "1.66:1 · European Scope",
            "16:9 · 16:9 Widescreen",
            "3:2 · 35mm Classic",
            "4:3 · Academy Sensor",
            "1:1 · Square Format"
    };

    static float parseAspectRatio(String aspect) {
        if (aspect == null || aspect.isEmpty()) return 4f / 3f;
        int dot = aspect.indexOf('·');
        String clean = (dot >= 0 ? aspect.substring(0, dot) : aspect).trim();
        int colon = clean.indexOf(':');
        if (colon > 0 && colon + 1 < clean.length() && clean.indexOf(':', colon + 1) < 0) {
            try {
                float w = Float.parseFloat(clean.substring(0, colon).trim());
                float h = Float.parseFloat(clean.substring(colon + 1).trim());
                if (w > 0f && h > 0f) return w / h;
            } catch (NumberFormatException e) {
                Log.w(TAG, "Failed to parse aspect ratio: " + aspect, e);
            }
        }
        return 4f / 3f;
    }

    static final int DEFAULT_EXPOSURE = 0;
    static final int DEFAULT_WARMTH = 50;
    static final int DEFAULT_CONTRAST = 112;
    static final int DEFAULT_PIVOT = 20;
    static final int DEFAULT_SATURATION = 98;
    static final int DEFAULT_COLOR_ROLLOFF = 72;
    static final int DEFAULT_HIGHLIGHTS = 68;
    static final int DEFAULT_SHADOWS = 36;
    static final int DEFAULT_GRAIN = 22;
    static final int DEFAULT_HALATION = 16;
    static final int DEFAULT_SHARPEN = 6;
    static final int DEFAULT_COLOR_NR = 15;
    static final int DEFAULT_VIGNETTE = 10;

    private AppSettings() {}

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String get(Context context, String key, String fallback) {
        return prefs(context).getString(key, fallback);
    }

    static int getInt(Context context, String key, int fallback) {
        return prefs(context).getInt(key, fallback);
    }

    static boolean getBoolean(Context context, String key, boolean fallback) {
        return prefs(context).getBoolean(key, fallback);
    }

    static boolean shouldSaveDng(String photoOutput) {
        return PHOTO_OUTPUT_JPEG_DNG.equals(photoOutput) || PHOTO_OUTPUT_DNG_ONLY.equals(photoOutput);
    }

    static boolean isDngOnly(String photoOutput) {
        return PHOTO_OUTPUT_DNG_ONLY.equals(photoOutput);
    }

    static long parseEditableArchiveLimitBytes(String choice) {
        if (choice == null) return 512L * 1024L * 1024L;
        if (choice.startsWith("256")) return 256L * 1024L * 1024L;
        if (choice.startsWith("1 GB")) return 1024L * 1024L * 1024L;
        if (choice.startsWith("2 GB")) return 2048L * 1024L * 1024L;
        if (choice.startsWith("Unlimited")) return Long.MAX_VALUE;
        return 512L * 1024L * 1024L;
    }

    static List<String> parseAdjustControls(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return new ArrayList<>(DEFAULT_ADJUST_CONTROLS);
        }
        Set<String> validSet = new HashSet<>(ALL_ADJUST_CONTROLS);
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String token : raw.split(",")) {
            String clean = token.trim();
            if (validSet.contains(clean)) {
                result.add(clean);
            }
        }
        if (result.isEmpty()) {
            return new ArrayList<>(DEFAULT_ADJUST_CONTROLS);
        }
        return new ArrayList<>(result);
    }

    static String serializeAdjustControls(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return String.join(",", DEFAULT_ADJUST_CONTROLS);
        }
        Set<String> validSet = new HashSet<>(ALL_ADJUST_CONTROLS);
        LinkedHashSet<String> clean = new LinkedHashSet<>();
        for (String k : keys) {
            if (k != null && validSet.contains(k.trim())) {
                clean.add(k.trim());
            }
        }
        if (clean.isEmpty()) return String.join(",", DEFAULT_ADJUST_CONTROLS);
        return String.join(",", clean);
    }

    static List<String> getAdjustControls(Context context) {
        return parseAdjustControls(get(context, ADJUST_CONTROLS_ORDER, null));
    }

    static void setAdjustControls(Context context, List<String> keys) {
        prefs(context).edit().putString(ADJUST_CONTROLS_ORDER, serializeAdjustControls(keys)).apply();
    }

    static void resetAdjustControls(Context context) {
        prefs(context).edit().remove(ADJUST_CONTROLS_ORDER).apply();
    }

    static String adjustControlLabel(String key) {
        if (EXPOSURE.equals(key)) return "Exposure trim";
        if (WARMTH.equals(key)) return "Warmth shift";
        if (HIGHLIGHTS.equals(key)) return "Highlights";
        if (SHADOWS.equals(key)) return "Shadows";
        if (CONTRAST.equals(key)) return "Contrast";
        if (SATURATION.equals(key)) return "Colour intensity";
        if (COLOR_ROLLOFF.equals(key)) return "Colour roll-off";
        if (GRAIN.equals(key)) return "Film grain";
        if (GRAIN_STRUCTURE.equals(key)) return "Grain structure";
        if (HALATION.equals(key)) return "Halation";
        if (SHARPEN.equals(key)) return "Sharpening";
        if (PIVOT.equals(key)) return "Contrast pivot";
        if (TONE_CURVE.equals(key)) return "Tone curve";
        if (SHADOW_PROFILE.equals(key)) return "Shadow profile";
        if (HIGHLIGHT_RECOVERY.equals(key)) return "Highlight recovery";
        if (COLOR_NR.equals(key)) return "Colour noise reduction";
        if (VIGNETTE.equals(key)) return "Vignette";
        if (LUT_INTENSITY.equals(key)) return "LUT intensity";
        return key;
    }

    static boolean isLeftHandMode(String mode) {
        return LEFT_HAND_MAIN.equals(mode) || LEFT_HAND_FURTHER.equals(mode);
    }

    static boolean isLeftHandSwapShutterRow(String mode) {
        return LEFT_HAND_FURTHER.equals(mode);
    }

    static boolean isLeftHandMode(Context context) {
        return isLeftHandMode(get(context, LEFT_HAND_MODE, LEFT_HAND_OFF));
    }

    static boolean isLeftHandSwapShutterRow(Context context) {
        return isLeftHandSwapShutterRow(get(context, LEFT_HAND_MODE, LEFT_HAND_OFF));
    }

    static String brain(Context context) {
        String brain = get(context, BRAIN, BRAIN_LIKA_FILM);
        if ("SCOPE / CORE".equals(brain)) return BRAIN_LIKA_FILM;
        if (brain != null && brain.startsWith("[RECIPE ") && brain.length() > 8) {
            int slot = Character.getNumericValue(brain.charAt(8));
            if (slot >= 1 && slot <= 3) {
                String rData = getCustomRecipeData(context, slot);
                if (rData != null) {
                    RawDeveloper.Look look = RawDeveloper.Look.fromJson(rData);
                    if (look != null && look.brain != null && !look.brain.startsWith("[RECIPE ")) {
                        return look.brain;
                    }
                }
            }
            return BRAIN_LIKA_FILM;
        }
        return brain;
    }

    static void migrateBranding(Context context) {
        migrateIfNeeded(context);
    }

    static void migrateIfNeeded(Context context) {
        SharedPreferences prefs = prefs(context);
        Map<String, Object> existing = new HashMap<>(prefs.getAll());
        Map<String, Object> updates = new HashMap<>();
        Set<String> removals = new HashSet<>();
        if (!computeMigration(existing, updates, removals)) {
            return;
        }
        SharedPreferences.Editor editor = prefs.edit();
        for (String key : removals) {
            editor.remove(key);
        }
        for (Map.Entry<String, Object> entry : updates.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();
            if (val instanceof Boolean) editor.putBoolean(key, (Boolean) val);
            else if (val instanceof Integer) editor.putInt(key, (Integer) val);
            else if (val instanceof Long) editor.putLong(key, (Long) val);
            else if (val instanceof Float) editor.putFloat(key, (Float) val);
            else if (val instanceof String) editor.putString(key, (String) val);
        }
        editor.apply();
    }

    /**
     * Pure-Java idempotent settings migration from v1 to v2.
     * Preserves custom recipes, LUT selections, valid brain/look choices, naming & folder org,
     * while resetting obsolete or contradictory v1 settings to the new v2 defaults.
     */
    static boolean computeMigration(Map<String, Object> existing,
                                    Map<String, Object> outUpdates,
                                    Set<String> outRemovals) {
        if (existing == null || outUpdates == null || outRemovals == null) return false;
        Object rawVer = existing.get(SETTINGS_VERSION);
        int currentVersion = rawVer instanceof Number ? ((Number) rawVer).intValue() : 0;
        boolean changed = false;

        // Branding normalization (idempotent across versions)
        if ("SCOPE / CORE".equals(existing.get(BRAIN))) {
            outUpdates.put(BRAIN, BRAIN_LIKA_FILM);
            changed = true;
        }
        if ("SCOPE".equals(existing.get(FILE_PREFIX))) {
            outUpdates.put(FILE_PREFIX, "LIKA");
            changed = true;
        }

        // Normalize invalid choices even on already-v2 installs / imported backups
        Object curColorSpace = existing.get(COLOR_SPACE);
        if (curColorSpace instanceof String && ((String) curColorSpace).contains("709")) {
            outUpdates.put(COLOR_SPACE, COLOR_SPACE_SRGB);
            changed = true;
        }
        Object curGrainStruct = existing.get(GRAIN_STRUCTURE);
        if ("Monochrome".equals(curGrainStruct)) {
            outUpdates.put(GRAIN_STRUCTURE, GRAIN_STRUCTURE_CHOICES[0]);
            changed = true;
        }
        Object curAudio = existing.get(VIDEO_AUDIO);
        if (curAudio instanceof String && ((String) curAudio).contains("Wind filter")) {
            outUpdates.put(VIDEO_AUDIO, VIDEO_AUDIO_CHOICES[1]);
            changed = true;
        }
        Object curFocus = existing.get(FOCUS_MODE);
        if ("Continuous picture autofocus".equals(curFocus)) {
            outUpdates.put(FOCUS_MODE, FOCUS_CONTINUOUS);
            changed = true;
        }

        if (currentVersion >= CURRENT_SETTINGS_VERSION) {
            return changed;
        }

        // Remove dead/obsolete v1 keys
        String[] obsoleteKeys = {
                LEGACY_CAPTURE_FORMAT,
                LEGACY_KEEP_RAW,
                LEGACY_DNG_POLICY,
                LEGACY_AUTO_EXPORT,
                LEGACY_STRIP_GEO,
                LEGACY_SPEECH_ENHANCEMENT,
                LEGACY_RANGEFINDER_LINES,
                LEGACY_GRID,
                LEGACY_SKIN_PROTECTION,
                LEGACY_SMART_VIBRANCE
        };
        for (String obsolete : obsoleteKeys) {
            if (existing.containsKey(obsolete)) {
                outRemovals.add(obsolete);
                changed = true;
            }
        }

        // Reset consolidated/obsolete settings to new v2 defaults (§33)
        if (!existing.containsKey(PHOTO_OUTPUT)) {
            outUpdates.put(PHOTO_OUTPUT, PHOTO_OUTPUT_JPEG_ONLY);
            changed = true;
        }
        if (!existing.containsKey(LOCATION) || Boolean.FALSE.equals(existing.get(LOCATION))) {
            // Migrate legacy false default or strip_geo conflict to v2 GPS-enabled default
            outUpdates.put(LOCATION, Boolean.TRUE);
            changed = true;
        }
        if (!existing.containsKey(UPDATE_CHANNEL) || UPDATE_CHANNEL_BETA.equals(existing.get(UPDATE_CHANNEL))) {
            // Old v1 defaulted everyone to Beta; reset to Stable (Beta is opt-in in v2)
            outUpdates.put(UPDATE_CHANNEL, UPDATE_CHANNEL_STABLE);
            changed = true;
        }
        if (!existing.containsKey(GRID_STYLE)) {
            outUpdates.put(GRID_STYLE, "None");
            changed = true;
        }
        if (!existing.containsKey(HIGHLIGHT_RECOVERY)) {
            outUpdates.put(HIGHLIGHT_RECOVERY, HL_REC_AUTO);
            changed = true;
        }
        if (!existing.containsKey(SHADOW_PROFILE)) {
            outUpdates.put(SHADOW_PROFILE, SHADOW_PROFILE_AUTO);
            changed = true;
        }

        // Migrate explicit Skin Protection & Smart Vibrance booleans to explicit 3-way override modes
        if (!existing.containsKey(SKIN_PROTECTION_MODE)) {
            Object oldSkin = existing.get(LEGACY_SKIN_PROTECTION);
            String mode = Boolean.FALSE.equals(oldSkin) ? OVERRIDE_OFF : OVERRIDE_AUTO;
            outUpdates.put(SKIN_PROTECTION_MODE, mode);
            changed = true;
        }
        if (!existing.containsKey(SMART_VIBRANCE_MODE)) {
            Object oldVib = existing.get(LEGACY_SMART_VIBRANCE);
            String mode = Boolean.FALSE.equals(oldVib) ? OVERRIDE_OFF : OVERRIDE_AUTO;
            outUpdates.put(SMART_VIBRANCE_MODE, mode);
            changed = true;
        }

        outUpdates.put(SETTINGS_VERSION, CURRENT_SETTINGS_VERSION);
        return true;
    }

    static String getCustomRecipeName(Context context, int slot) {
        String key = slot == 1 ? RECIPE_1_NAME : slot == 2 ? RECIPE_2_NAME : RECIPE_3_NAME;
        return get(context, key, null);
    }

    static String getCustomRecipeData(Context context, int slot) {
        String key = slot == 1 ? RECIPE_1_DATA : slot == 2 ? RECIPE_2_DATA : RECIPE_3_DATA;
        return get(context, key, null);
    }

    static void saveCustomRecipe(Context context, int slot, String name, String data) {
        String nameKey = slot == 1 ? RECIPE_1_NAME : slot == 2 ? RECIPE_2_NAME : RECIPE_3_NAME;
        String dataKey = slot == 1 ? RECIPE_1_DATA : slot == 2 ? RECIPE_2_DATA : RECIPE_3_DATA;
        prefs(context).edit().putString(nameKey, name).putString(dataKey, data).apply();
    }

    static void deleteCustomRecipe(Context context, int slot) {
        String nameKey = slot == 1 ? RECIPE_1_NAME : slot == 2 ? RECIPE_2_NAME : RECIPE_3_NAME;
        String dataKey = slot == 1 ? RECIPE_1_DATA : slot == 2 ? RECIPE_2_DATA : RECIPE_3_DATA;
        prefs(context).edit().remove(nameKey).remove(dataKey).apply();
    }

    static int resolveWhiteBalanceKelvin(Context context) {
        SharedPreferences p = prefs(context);
        if (p.contains(WHITE_BALANCE)) {
            int k = p.getInt(WHITE_BALANCE, 0);
            if (k > 0) return k;
        }
        String mode = p.getString(WHITE_BALANCE_MODE, "Auto · as shot");
        return parseWhiteBalanceModeKelvin(mode);
    }

    static int parseWhiteBalanceModeKelvin(String mode) {
        if (mode == null || mode.startsWith("Auto")) return 0;
        if (mode.startsWith("2800")) return 2800;
        if (mode.startsWith("3200")) return 3200;
        if (mode.startsWith("4000")) return 4000;
        if (mode.startsWith("5000")) return 5000;
        if (mode.startsWith("5600")) return 5600;
        if (mode.startsWith("6500")) return 6500;
        if (mode.startsWith("7500")) return 7500;
        return 0;
    }

    static String formatWhiteBalanceMode(int kelvin) {
        if (kelvin <= 0) return "Auto · as shot";
        if (kelvin <= 3000) return "2800 K · incandescent";
        if (kelvin <= 3600) return "3200 K · tungsten";
        if (kelvin <= 4500) return "4000 K · warm LED";
        if (kelvin <= 5300) return "5000 K · daylight";
        if (kelvin <= 6000) return "5600 K · daylight";
        if (kelvin <= 7000) return "6500 K · overcast";
        return "7500 K · shade";
    }

    static void setWhiteBalanceKelvin(Context context, int kelvin) {
        int clamped = kelvin <= 0 ? 0 : Math.max(2000, Math.min(10000, kelvin));
        prefs(context).edit()
                .putInt(WHITE_BALANCE, clamped)
                .putString(WHITE_BALANCE_MODE, formatWhiteBalanceMode(clamped))
                .apply();
    }

    private static StatFs storageStats(Context context) {
        try {
            File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
            if (!path.exists()) path = context.getFilesDir();
            return new StatFs(path.getPath());
        } catch (Throwable t) {
            return null;
        }
    }

    static long getAvailableStorageBytes(Context context) {
        StatFs stat = storageStats(context);
        return stat != null ? stat.getAvailableBytes() : 0L;
    }

    static long getEstimatedRemainingFrames(Context context) {
        long bytes = getAvailableStorageBytes(context);
        String output = get(context, PHOTO_OUTPUT, PHOTO_OUTPUT_JPEG_ONLY);
        long bytesPerFrame = shouldSaveDng(output) ? (28L * 1024L * 1024L) : (12L * 1024L * 1024L);
        return Math.max(0L, bytes / bytesPerFrame);
    }

    static long getEstimatedRemainingVideoMinutes(Context context) {
        long bytes = getAvailableStorageBytes(context);
        return Math.max(0L, bytes / (180L * 1024L * 1024L)); // ~180MB/min at 24Mbps
    }

    static long getTotalStorageBytes(Context context) {
        StatFs stat = storageStats(context);
        return stat != null ? stat.getTotalBytes() : 0L;
    }

    static String formatDetailedStorageBreakdown(Context context) {
        StatFs stat = storageStats(context);
        long avail = stat != null ? stat.getAvailableBytes() : 0L;
        long total = stat != null ? stat.getTotalBytes() : 0L;
        double availGb = avail / (1024.0 * 1024.0 * 1024.0);
        double totalGb = total / (1024.0 * 1024.0 * 1024.0);
        long jpegFrames = Math.max(0L, avail / (5L * 1024L * 1024L));
        long dngFrames = Math.max(0L, avail / (28L * 1024L * 1024L));
        long mins = Math.max(0L, avail / (180L * 1024L * 1024L));
        long h = mins / 60;
        long m = mins % 60;
        String videoStr = h > 0 ? (h + "h " + m + "m") : (m + "m");
        return String.format(Locale.US,
                "%.1f GB free of %.1f GB\n• ~%d Processed JPEGs (or ~%d with DNG)\n• ~%s 24fps Cinematic Video",
                availGb, totalGb, jpegFrames, dngFrames, videoStr);
    }

    static String formatStorageInfo(Context context) {
        long bytes = getAvailableStorageBytes(context);
        double gb = bytes / (1024.0 * 1024.0 * 1024.0);
        long frames = getEstimatedRemainingFrames(context);
        long mins = Math.max(0L, bytes / (180L * 1024L * 1024L));
        long h = mins / 60;
        long m = mins % 60;
        String videoStr = h > 0 ? (h + "h " + m + "m") : (m + "m");
        return String.format(Locale.US, "%.1f GB available · ~%d frames (or ~%s video)", gb, frames, videoStr);
    }
}
