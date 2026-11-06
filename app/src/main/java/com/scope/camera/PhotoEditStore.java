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
        return look;
    }

    static void save(Context context, String key, RawDeveloper.Look look) {
        String p = prefix(key);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
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
                .apply();
    }

    static void reset(Context context, String key) {
        String p = prefix(key);
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().remove(p + "brain").remove(p + "grain_structure")
                .remove(p + "exposure").remove(p + "warmth").remove(p + "pivot").remove(p + "contrast").remove(p + "saturation")
                .remove(p + "color_rolloff")
                .remove(p + "highlights").remove(p + "shadows").remove(p + "grain")
                .remove(p + "halation").remove(p + "sharpening").remove(p + "chroma_nr")
                .remove(p + "vignette").remove(p + "white_balance").apply();
    }

    private static String prefix(String key) {
        String safe = key == null ? "" : key.replaceAll("[^A-Za-z0-9_.-]", "_");
        return safe + ".";
    }
}
