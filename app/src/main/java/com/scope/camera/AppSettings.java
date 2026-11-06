package com.scope.camera;

import android.content.Context;
import android.content.SharedPreferences;

final class AppSettings {
    static final String PREFS = "scope_camera_preferences";
    static final String BRAIN = "brain";
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
        if (changed) editor.apply();
    }
}
