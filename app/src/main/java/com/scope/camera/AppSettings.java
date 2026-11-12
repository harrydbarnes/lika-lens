package com.scope.camera;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;
import java.io.File;
import java.util.Locale;

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
    static final String RANGEFINDER_LINES = "rangefinder_frame_lines";
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
            "Speech enhanced · Wind filter",
            "Unprocessed · Raw Mic",
            "Muted · MOS (No Audio)"
    };
    static final String SPEECH_ENHANCEMENT = "speech_enhancement";
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
        String brain = get(context, BRAIN, "LIKA / FILM");
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

    static long getAvailableStorageBytes(Context context) {
        try {
            File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
            if (!path.exists()) path = context.getFilesDir();
            StatFs stat = new StatFs(path.getPath());
            return stat.getAvailableBytes();
        } catch (Throwable t) {
            return 0L;
        }
    }

    static long getEstimatedRemainingFrames(Context context) {
        long bytes = getAvailableStorageBytes(context);
        return Math.max(0L, bytes / (28L * 1024L * 1024L)); // ~28MB per DNG + rendered JPEG
    }

    static long getEstimatedRemainingVideoMinutes(Context context) {
        long bytes = getAvailableStorageBytes(context);
        return Math.max(0L, bytes / (180L * 1024L * 1024L)); // ~180MB/min at 24Mbps
    }

    static long getTotalStorageBytes(Context context) {
        try {
            File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
            if (!path.exists()) path = context.getFilesDir();
            StatFs stat = new StatFs(path.getPath());
            return stat.getTotalBytes();
        } catch (Throwable t) {
            return 0L;
        }
    }

    static String formatDetailedStorageBreakdown(Context context) {
        long avail = getAvailableStorageBytes(context);
        long total = getTotalStorageBytes(context);
        double availGb = avail / (1024.0 * 1024.0 * 1024.0);
        double totalGb = total / (1024.0 * 1024.0 * 1024.0);
        long frames = getEstimatedRemainingFrames(context);
        long mins = getEstimatedRemainingVideoMinutes(context);
        long h = mins / 60;
        long m = mins % 60;
        String videoStr = h > 0 ? (h + "h " + m + "m") : (m + "m");
        return String.format(Locale.US,
                "%.1f GB free of %.1f GB\n• ~%d RAW DNG + JPEG frames\n• ~%s 24fps Cinematic Video",
                availGb, totalGb, frames, videoStr);
    }

    static String formatStorageInfo(Context context) {
        long bytes = getAvailableStorageBytes(context);
        double gb = bytes / (1024.0 * 1024.0 * 1024.0);
        long frames = getEstimatedRemainingFrames(context);
        long mins = getEstimatedRemainingVideoMinutes(context);
        long h = mins / 60;
        long m = mins % 60;
        String videoStr = h > 0 ? (h + "h " + m + "m") : (m + "m");
        return String.format(Locale.US, "%.1f GB available · ~%d film frames (or ~%s video)", gb, frames, videoStr);
    }
}
