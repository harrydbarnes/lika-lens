package com.scope.camera;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

final class AppSettings {
    private static final String TAG = "AppSettings";
    static final String PREFS = "scope_camera_preferences";
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

    static final String CAPTURE_FORMAT = "capture_format";
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
    static final String HALATION = "halation";
    static final String SHARPEN = "sharpen";
    static final String COLOR_NR = "color_nr";
    static final String VIGNETTE = "vignette";
    static final String GRID = "grid";
    static final String GRID_STYLE = "grid_style";
    static final String HAPTICS = "haptics";
    static final String HISTOGRAM = "histogram";
    static final String FOCUS_PEAKING = "focus_peaking";
    static final String ZEBRA = "zebra";
    static final String KEEP_RAW = "keep_raw";
    static final String AUTO_EXPORT = "auto_export";
    static final String LOCATION = "location";
    static final String COLOR_SPACE = "color_space";
    static final String FILE_PREFIX = "file_prefix";
    static final String RAW_SUPPORTED = "raw_supported";
    static final String MANUAL_SUPPORTED = "manual_supported";
    static final String VISUAL_LEVEL = "visual_level";
    static final String DEFAULT_LENS = "default_lens";
    static final String LAST_CAMERA_ID = "last_camera_id";
    static final String VIDEO_FPS = "video_fps";
    static final String VIDEO_ASPECT = "video_aspect";
    static final String VIDEO_BITRATE = "video_bitrate";
    static final String SHUTTER_SOUND = "shutter_sound";

    // Processing & Colour Pipeline Keys
    static final String EASY_MODE = "easy_mode";
    static final String SKIN_PROTECTION = "skin_protection";
    static final String SMART_VIBRANCE = "smart_vibrance";
    static final String HIGHLIGHT_RECOVERY = "highlight_recovery";
    static final String SHADOW_PROFILE = "shadow_profile";
    static final String TONE_CURVE = "tone_curve";
    static final String GRAIN_RESPONSE = "grain_response";
    static final String ANTI_BANDING = "anti_banding";

    // Viewfinder & Camera Shooting Keys
    static final String VIEWFINDER_LONG_PRESS = "viewfinder_long_press";
    static final String VIEWFINDER_DOUBLE_TAP = "viewfinder_double_tap";
    static final String RETICLE_METERING = "reticle_metering";
    static final String HORIZON_SENSITIVITY = "horizon_sensitivity";
    static final String HORIZON_MODE = "horizon_mode";
    static final String VOLUME_ROCKER = "volume_rocker";
    static final String SHUTTER_WINK = "shutter_wink";

    // Pro UX & Viewfinder Feature Keys
    static final String LIVE_FILM_PREVIEW = "live_film_preview";
    static final String RANGEFINDER_LINES = "rangefinder_frame_lines";
    static final String OPTICAL_LOUPE = "optical_loupe";
    static final String HISTOGRAM_MODE = "histogram_mode";

    // Custom Film Recipes (User Presets)
    static final String RECIPE_1_NAME = "recipe_1_name";
    static final String RECIPE_1_DATA = "recipe_1_data";
    static final String RECIPE_2_NAME = "recipe_2_name";
    static final String RECIPE_2_DATA = "recipe_2_data";
    static final String RECIPE_3_NAME = "recipe_3_name";
    static final String RECIPE_3_DATA = "recipe_3_data";

    // 3D LUT (Color Lookup Table) Keys
    static final String LUT_NAME = "lut_name";
    static final String LUT_INTENSITY = "lut_intensity";
    static final int DEFAULT_LUT_INTENSITY = 100;

    // File Management & Output Keys
    static final String DNG_POLICY = "dng_policy";
    static final String FOLDER_ORG = "folder_organization";
    static final String FILE_NAMING = "file_naming";
    static final String JPEG_QUALITY = "jpeg_quality";
    static final String EXIF_PARAMS = "exif_params";
    static final String STRIP_GEO = "strip_geo";
    static final String CUSTOM_PREFIX_VALUE = "custom_prefix_value";
    static final String FRAME_BORDER = "frame_border";

    // Optical, Hardware & Video Keys
    static final String OPTICAL_CORRECTION = "optical_correction";
    static final String MIRROR_FRONT = "mirror_front";
    static final String STABILIZATION = "stabilization";
    static final String VIDEO_CODEC = "video_codec";
    static final String VIDEO_AUDIO = "video_audio";

    // Animated GIF Keys
    static final String GIF_FPS = "gif_fps";
    static final String GIF_RESOLUTION = "gif_resolution";
    static final String GIF_BOOMERANG = "gif_boomerang";

    // In-App Update Keys
    static final String UPDATE_CHANNEL = "update_channel";
    static final String UPDATE_CHANNEL_BETA = "Beta (Every Commit / Action)";
    static final String UPDATE_CHANNEL_STABLE = "Stable (Recommended)";
    static final String[] UPDATE_CHANNEL_CHOICES = {
            UPDATE_CHANNEL_BETA,
            UPDATE_CHANNEL_STABLE
    };
    static final String AUTO_CHECK_UPDATES = "auto_check_updates";
    static final String LAST_UPDATE_CHECK_TIME = "last_update_check_time";

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
        String clean = aspect.split("·")[0].trim();
        String[] parts = clean.split(":");
        if (parts.length == 2) {
            try {
                float w = Float.parseFloat(parts[0].trim());
                float h = Float.parseFloat(parts[1].trim());
                if (w > 0f && h > 0f) return w / h;
            } catch (NumberFormatException e) {
                Log.w(TAG, "Failed to parse aspect ratio: " + aspect, e);
            }
        }
        return 4f / 3f;
    }

    static final int DEFAULT_EXPOSURE = -2;
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

    static String brain(Context context) {
        String brain = get(context, BRAIN, "LIKA / FILM");
        return "SCOPE / CORE".equals(brain) ? "LIKA / FILM" : brain;
    }

    static void migrateBranding(Context context) {
        SharedPreferences prefs = prefs(context);
        String oldBrain = prefs.getString(BRAIN, null);
        String oldPrefix = prefs.getString(FILE_PREFIX, null);
        SharedPreferences.Editor editor = prefs.edit();
        boolean changed = false;
        if ("SCOPE / CORE".equals(oldBrain)) { editor.putString(BRAIN, "LIKA / FILM"); changed = true; }
        if ("SCOPE".equals(oldPrefix)) { editor.putString(FILE_PREFIX, "LIKA"); changed = true; }
        if (!prefs.contains(GRID_STYLE) && prefs.getBoolean(GRID, false)) {
            editor.putString(GRID_STYLE, "3×3");
            changed = true;
        }
        if (changed) editor.apply();
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
}
