package com.scope.camera;

import android.content.Context;
import android.content.SharedPreferences;

/** Per-frame look values. The sensor archive remains untouched so the render can be rebuilt. */
final class PhotoEditStore {
    private static final String PREFS = "scope_photo_edits";
    private PhotoEditStore() { }

    static RawDeveloper.Look load(Context context, String key) {
        RawDeveloper.Look look = RawDeveloper.Look.fromPreferences(context);
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String prefix = prefix(key);
        look.brain = prefs.getString(prefix + "brain", look.brain);
        look.grainStructure = prefs.getString(prefix + "grain_structure", look.grainStructure);
        look.exposureEv = prefs.getFloat(prefix + "exposure", look.exposureEv);
        look.warmth = prefs.getFloat(prefix + "warmth", look.warmth);
        look.pivot = prefs.getFloat(prefix + "pivot", look.pivot);
        look.contrast = prefs.getFloat(prefix + "contrast", look.contrast);
        look.saturation = prefs.getFloat(prefix + "saturation", look.saturation);
        look.colorRollOff = prefs.getFloat(prefix + "color_rolloff", look.colorRollOff);
        look.highlights = prefs.getFloat(prefix + "highlights", look.highlights);
        look.shadows = prefs.getFloat(prefix + "shadows", look.shadows);
        look.grain = prefs.getFloat(prefix + "grain", look.grain);
        look.halation = prefs.getFloat(prefix + "halation", look.halation);
        look.sharpening = prefs.getFloat(prefix + "sharpening", look.sharpening);
        look.chromaNr = prefs.getFloat(prefix + "chroma_nr", look.chromaNr);
        look.vignette = prefs.getFloat(prefix + "vignette", look.vignette);
        look.whiteBalanceKelvin = prefs.getInt(prefix + "white_balance", look.whiteBalanceKelvin);
        look.aspectRatio = prefs.getString(prefix + "aspect_ratio", null);
        look.flipHorizontal = prefs.getBoolean(prefix + "flip_h", false);
        look.easyMode = prefs.getBoolean(prefix + "easy_mode", look.easyMode);
        look.skinProtection = prefs.getBoolean(prefix + "skin_protection", look.skinProtection);
        look.smartVibrance = prefs.getBoolean(prefix + "smart_vibrance", look.smartVibrance);
        look.opticalCorrection = prefs.getBoolean(prefix + "optical_correction", look.opticalCorrection);
        look.frameBorder = prefs.getString(prefix + "frame_border", look.frameBorder);
        look.lutName = prefs.getString(prefix + "lut_name", look.lutName);
        look.lutIntensity = prefs.getFloat(prefix + "lut_intensity", look.lutIntensity);
        return look;
    }

    static void save(Context context, String key, RawDeveloper.Look look) {
        String p = prefix(key);
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(p + "brain", look.brain)
                .putString(p + "grain_structure", look.grainStructure)
                .putFloat(p + "exposure", look.exposureEv)
                .putFloat(p + "warmth", look.warmth)
                .putFloat(p + "pivot", look.pivot)
                .putFloat(p + "contrast", look.contrast)
                .putFloat(p + "saturation", look.saturation)
                .putFloat(p + "color_rolloff", look.colorRollOff)
                .putFloat(p + "highlights", look.highlights)
                .putFloat(p + "shadows", look.shadows)
                .putFloat(p + "grain", look.grain)
                .putFloat(p + "halation", look.halation)
                .putFloat(p + "sharpening", look.sharpening)
                .putFloat(p + "chroma_nr", look.chromaNr)
                .putFloat(p + "vignette", look.vignette)
                .putInt(p + "white_balance", look.whiteBalanceKelvin)
                .putBoolean(p + "easy_mode", look.easyMode)
                .putBoolean(p + "skin_protection", look.skinProtection)
                .putBoolean(p + "smart_vibrance", look.smartVibrance)
                .putBoolean(p + "optical_correction", look.opticalCorrection)
                .putString(p + "frame_border", look.frameBorder == null ? "None" : look.frameBorder)
                .putString(p + "lut_name", look.lutName == null ? "None" : look.lutName)
                .putFloat(p + "lut_intensity", look.lutIntensity);
        if (look.aspectRatio != null) editor.putString(p + "aspect_ratio", look.aspectRatio);
        else editor.remove(p + "aspect_ratio");
        editor.putBoolean(p + "flip_h", look.flipHorizontal);
        editor.apply();
    }

    static void reset(Context context, String key) {
        String p = prefix(key);
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().remove(p + "brain").remove(p + "grain_structure")
                .remove(p + "exposure").remove(p + "warmth").remove(p + "pivot").remove(p + "contrast").remove(p + "saturation")
                .remove(p + "color_rolloff")
                .remove(p + "highlights").remove(p + "shadows").remove(p + "grain")
                .remove(p + "halation").remove(p + "sharpening").remove(p + "chroma_nr")
                .remove(p + "vignette").remove(p + "white_balance")
                .remove(p + "easy_mode").remove(p + "skin_protection").remove(p + "smart_vibrance")
                .remove(p + "optical_correction").remove(p + "frame_border")
                .remove(p + "lut_name").remove(p + "lut_intensity")
                .remove(p + "aspect_ratio").remove(p + "flip_h").apply();
    }

    static void copyToClipboard(Context context, RawDeveloper.Look look) {
        if (look == null) return;
        save(context, "__clipboard_recipe__", look);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean("__has_clipboard_recipe__", true).apply();
    }

    static boolean hasClipboard(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean("__has_clipboard_recipe__", false);
    }

    static RawDeveloper.Look pasteFromClipboard(Context context) {
        if (!hasClipboard(context)) return null;
        return load(context, "__clipboard_recipe__");
    }

    private static String prefix(String key) {
        String safe = key == null ? "" : key.replaceAll("[^A-Za-z0-9_.-]", "_");
        return safe + ".";
    }
}
