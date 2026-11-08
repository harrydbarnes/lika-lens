package com.scope.camera;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 3D LUT (Color Lookup Table) management and trilinear interpolation engine.
 * Supports standard Adobe .CUBE 3D and 1D files, built-in cinematic film stock
 * profiles, and user-imported custom LUTs with intensity blending.
 */
final class LutManager {
    private static final String TAG = "LutManager";
    public static final String LUT_NONE = "None";
    public static final String LUT_PORTRA = "Portra 400 Warm";
    public static final String LUT_FUJI = "Fuji 400H Soft";
    public static final String LUT_TEAL_ORANGE = "Cine Teal & Orange";
    public static final String LUT_ANSEL_BW = "Ansel Silver B&W";
    public static final String LUT_AMBER_70S = "Warm Amber 70s";
    public static final String LUT_BLEACH = "Bleach Bypass";

    private static final List<String> BUILT_IN_LUTS = Arrays.asList(
            LUT_NONE,
            LUT_PORTRA,
            LUT_FUJI,
            LUT_TEAL_ORANGE,
            LUT_ANSEL_BW,
            LUT_AMBER_70S,
            LUT_BLEACH
    );

    private static final Map<String, Lut3D> sCache = new ConcurrentHashMap<>();
    private static File sLutsDir;

    private LutManager() { }

    public static final class Lut3D {
        public final String name;
        public final String title;
        public final int size;
        public final float[] table; // length: size * size * size * 3 (order: r + size * (g + size * b))
        public final boolean isCustom;

        public Lut3D(String name, String title, int size, float[] table, boolean isCustom) {
            this.name = name;
            this.title = title != null ? title : name;
            this.size = size;
            this.table = table;
            this.isCustom = isCustom;
        }

        /**
         * Applies the 3D LUT using hardware-friendly trilinear interpolation.
         *
         * @param r input red in [0.0, 1.0] sRGB
         * @param g input green in [0.0, 1.0] sRGB
         * @param b input blue in [0.0, 1.0] sRGB
         * @param intensity blend opacity in [0.0, 1.0]
         * @return 32-bit ARGB packed integer (0xFFRRGGBB)
         */
        public int applyToRgb(float r, float g, float b, float intensity) {
            if (intensity <= 0.001f) {
                int ir = (int) (Math.max(0f, Math.min(1f, r)) * 255f + 0.5f);
                int ig = (int) (Math.max(0f, Math.min(1f, g)) * 255f + 0.5f);
                int ib = (int) (Math.max(0f, Math.min(1f, b)) * 255f + 0.5f);
                return 0xff000000 | (ir << 16) | (ig << 8) | ib;
            }

            float cr = Math.max(0f, Math.min(1f, r));
            float cg = Math.max(0f, Math.min(1f, g));
            float cb = Math.max(0f, Math.min(1f, b));

            float fr = cr * (size - 1);
            float fg = cg * (size - 1);
            float fb = cb * (size - 1);

            int r0 = (int) fr; int r1 = Math.min(r0 + 1, size - 1);
            int g0 = (int) fg; int g1 = Math.min(g0 + 1, size - 1);
            int b0 = (int) fb; int b1 = Math.min(b0 + 1, size - 1);

            float dr = fr - r0;
            float dg = fg - g0;
            float db = fb - b0;

            int strideG = size * 3;
            int strideB = size * size * 3;

            int b0Off = b0 * strideB;
            int b1Off = b1 * strideB;

            int g0b0 = g0 * strideG + b0Off;
            int g1b0 = g1 * strideG + b0Off;
            int g0b1 = g0 * strideG + b1Off;
            int g1b1 = g1 * strideG + b1Off;

            int i000 = r0 * 3 + g0b0;
            int i100 = r1 * 3 + g0b0;
            int i010 = r0 * 3 + g1b0;
            int i110 = r1 * 3 + g1b0;
            int i001 = r0 * 3 + g0b1;
            int i101 = r1 * 3 + g0b1;
            int i011 = r0 * 3 + g1b1;
            int i111 = r1 * 3 + g1b1;

            // R channel
            float r00 = table[i000]     * (1f - dr) + table[i100]     * dr;
            float r10 = table[i010]     * (1f - dr) + table[i110]     * dr;
            float r01 = table[i001]     * (1f - dr) + table[i101]     * dr;
            float r11 = table[i011]     * (1f - dr) + table[i111]     * dr;
            float r0_ = r00 * (1f - dg) + r10 * dg;
            float r1_ = r01 * (1f - dg) + r11 * dg;
            float lutR = r0_ * (1f - db) + r1_ * db;

            // G channel
            float g00 = table[i000 + 1] * (1f - dr) + table[i100 + 1] * dr;
            float g10 = table[i010 + 1] * (1f - dr) + table[i110 + 1] * dr;
            float g01 = table[i001 + 1] * (1f - dr) + table[i101 + 1] * dr;
            float g11 = table[i011 + 1] * (1f - dr) + table[i111 + 1] * dr;
            float g0_ = g00 * (1f - dg) + g10 * dg;
            float g1_ = g01 * (1f - dg) + g11 * dg;
            float lutG = g0_ * (1f - db) + g1_ * db;

            // B channel
            float b00 = table[i000 + 2] * (1f - dr) + table[i100 + 2] * dr;
            float b10 = table[i010 + 2] * (1f - dr) + table[i110 + 2] * dr;
            float b01 = table[i001 + 2] * (1f - dr) + table[i101 + 2] * dr;
            float b11 = table[i011 + 2] * (1f - dr) + table[i111 + 2] * dr;
            float b0_ = b00 * (1f - dg) + b10 * dg;
            float b1_ = b01 * (1f - dg) + b11 * dg;
            float lutB = b0_ * (1f - db) + b1_ * db;

            float outR = intensity >= 0.999f ? lutR : (r + (lutR - r) * intensity);
            float outG = intensity >= 0.999f ? lutG : (g + (lutG - g) * intensity);
            float outB = intensity >= 0.999f ? lutB : (b + (lutB - b) * intensity);

            int ir = (int) (Math.max(0f, Math.min(1f, outR)) * 255f + 0.5f);
            int ig = (int) (Math.max(0f, Math.min(1f, outG)) * 255f + 0.5f);
            int ib = (int) (Math.max(0f, Math.min(1f, outB)) * 255f + 0.5f);
            return 0xff000000 | (ir << 16) | (ig << 8) | ib;
        }
    }

    public static void init(Context context) {
        if (sLutsDir == null && context != null) {
            sLutsDir = new File(context.getFilesDir(), "luts");
            if (!sLutsDir.exists()) sLutsDir.mkdirs();
        }
    }

    public static File getLutsDir(Context context) {
        if (sLutsDir == null && context != null) {
            init(context);
        }
        return sLutsDir;
    }

    public static List<String> getAvailableLutNames(Context context) {
        List<String> list = new ArrayList<>(BUILT_IN_LUTS);
        File dir = getLutsDir(context);
        if (dir != null && dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles((d, name) -> name.toLowerCase(Locale.US).endsWith(".cube"));
            if (files != null) {
                Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                for (File f : files) {
                    String name = f.getName();
                    if (name.toLowerCase(Locale.US).endsWith(".cube")) {
                        name = name.substring(0, name.length() - 5);
                    }
                    if (!list.contains(name)) list.add(name);
                }
            }
        }
        return list;
    }

    public static Lut3D get(String name) {
        return get(null, name);
    }

    public static Lut3D get(Context context, String name) {
        if (name == null || name.trim().isEmpty() || LUT_NONE.equalsIgnoreCase(name)) {
            return null;
        }
        Lut3D cached = sCache.get(name);
        if (cached != null) return cached;

        // Check built-in LUTs
        if (isBuiltIn(name)) {
            Lut3D builtIn = generateBuiltIn(name, 33);
            if (builtIn != null) {
                sCache.put(name, builtIn);
                return builtIn;
            }
        }

        // Check custom LUT directory
        File dir = getLutsDir(context);
        if (dir != null && dir.exists()) {
            File file = new File(dir, name.endsWith(".cube") ? name : (name + ".cube"));
            if (file.exists() && file.isFile()) {
                try (InputStream in = new FileInputStream(file)) {
                    Lut3D custom = parseCube(in, name);
                    if (custom != null) {
                        sCache.put(name, custom);
                        return custom;
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to load custom LUT file: " + file, e);
                }
            }
        }

        return null;
    }

    public static boolean isBuiltIn(String name) {
        if (name == null) return false;
        for (String b : BUILT_IN_LUTS) {
            if (b.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    public static String importLut(Context context, Uri uri) {
        if (context == null || uri == null) return null;
        init(context);
        String displayName = "custom_look";
        try (Cursor cursor = context.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    String val = cursor.getString(idx);
                    if (val != null && !val.trim().isEmpty()) displayName = val;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to query display name for LUT URI: " + uri, e);
        }

        String baseName = displayName;
        if (baseName.toLowerCase(Locale.US).endsWith(".cube")) {
            baseName = baseName.substring(0, baseName.length() - 5);
        }
        baseName = baseName.replaceAll("[^A-Za-z0-9_ -]", "_").trim();
        if (baseName.isEmpty()) baseName = "imported_lut";

        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            Lut3D parsed = parseCube(in, baseName);
            if (parsed == null) return null;

            File dest = new File(getLutsDir(context), baseName + ".cube");
            try (InputStream copyIn = context.getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(dest)) {
                if (copyIn != null) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = copyIn.read(buf)) > 0) {
                        out.write(buf, 0, len);
                    }
                }
            }
            sCache.put(baseName, parsed);
            return baseName;
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean deleteCustomLut(Context context, String name) {
        if (name == null || isBuiltIn(name)) return false;
        sCache.remove(name);
        File dir = getLutsDir(context);
        if (dir != null) {
            File f = new File(dir, name.endsWith(".cube") ? name : (name + ".cube"));
            if (f.exists()) return f.delete();
        }
        return false;
    }

    public static Lut3D parseCube(InputStream is, String name) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        String line;
        int size3d = 0;
        int size1d = 0;
        String title = name;

        List<float[]> rawData = new ArrayList<>();
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("TITLE")) {
                int q1 = line.indexOf('"');
                int q2 = line.lastIndexOf('"');
                if (q1 >= 0 && q2 > q1) title = line.substring(q1 + 1, q2);
                else {
                    String[] parts = line.split("\\s+", 2);
                    if (parts.length > 1) title = parts[1].trim();
                }
            } else if (line.startsWith("LUT_3D_SIZE")) {
                String[] parts = line.split("\\s+");
                if (parts.length > 1) size3d = Integer.parseInt(parts[1].trim());
            } else if (line.startsWith("LUT_1D_SIZE")) {
                String[] parts = line.split("\\s+");
                if (parts.length > 1) size1d = Integer.parseInt(parts[1].trim());
            } else if (line.startsWith("DOMAIN_MIN") || line.startsWith("DOMAIN_MAX") || line.startsWith("LUT_3D_INPUT_RANGE")) {
                // Supported header lines ignored for normalized sRGB domain [0, 1]
            } else {
                String[] parts = line.split("\\s+");
                if (parts.length >= 3) {
                    try {
                        float r = Float.parseFloat(parts[0]);
                        float g = Float.parseFloat(parts[1]);
                        float b = Float.parseFloat(parts[2]);
                        rawData.add(new float[]{r, g, b});
                    } catch (NumberFormatException e) {
                        Log.w(TAG, "Malformed RGB triplet in CUBE: " + line, e);
                    }
                }
            }
        }

        if (size3d > 0 && !rawData.isEmpty()) {
            int expected = size3d * size3d * size3d;
            float[] table = new float[expected * 3];
            int count = Math.min(rawData.size(), expected);
            for (int i = 0; i < count; i++) {
                float[] rgb = rawData.get(i);
                table[i * 3] = rgb[0];
                table[i * 3 + 1] = rgb[1];
                table[i * 3 + 2] = rgb[2];
            }
            return new Lut3D(name, title, size3d, table, true);
        } else if (size1d > 0 && !rawData.isEmpty()) {
            int size = 17;
            float[] table = new float[size * size * size * 3];
            for (int b = 0; b < size; b++) {
                float inB = b / (size - 1f);
                float outB = sample1D(rawData, inB, 2);
                for (int g = 0; g < size; g++) {
                    float inG = g / (size - 1f);
                    float outG = sample1D(rawData, inG, 1);
                    for (int r = 0; r < size; r++) {
                        float inR = r / (size - 1f);
                        float outR = sample1D(rawData, inR, 0);
                        int idx = (r + size * (g + size * b)) * 3;
                        table[idx] = outR;
                        table[idx + 1] = outG;
                        table[idx + 2] = outB;
                    }
                }
            }
            return new Lut3D(name, title, size, table, true);
        }
        throw new IOException("Invalid or unsupported CUBE format");
    }

    private static float sample1D(List<float[]> rawData, float inVal, int channel) {
        if (rawData == null || rawData.isEmpty()) return inVal;
        int n = rawData.size();
        float f = Math.max(0f, Math.min(1f, inVal)) * (n - 1);
        int i0 = (int) f;
        int i1 = Math.min(i0 + 1, n - 1);
        float d = f - i0;
        int ch = Math.min(channel, rawData.get(i0).length - 1);
        return rawData.get(i0)[ch] * (1f - d) + rawData.get(i1)[ch] * d;
    }

    static Lut3D generateBuiltIn(String name, int size) {
        float[] table = new float[size * size * size * 3];
        for (int b = 0; b < size; b++) {
            float inB = b / (size - 1f);
            for (int g = 0; g < size; g++) {
                float inG = g / (size - 1f);
                for (int r = 0; r < size; r++) {
                    float inR = r / (size - 1f);
                    int idx = (r + size * (g + size * b)) * 3;
                    float outR = inR, outG = inG, outB = inB;

                    float luma = 0.2126f * inR + 0.7152f * inG + 0.0722f * inB;

                    if (LUT_PORTRA.equalsIgnoreCase(name)) {
                        // Kodak Portra 400: Golden creamy highlights, pastel cyan shadow lift, peach skin tones
                        outR = (float) Math.pow(inR, 0.94) * 1.05f + 0.02f;
                        outG = (float) Math.pow(inG, 0.97) * 1.01f + 0.01f;
                        outB = (float) Math.pow(inB, 1.03) * 0.95f;
                        float shadowLift = 0.035f * (1f - luma);
                        outR += shadowLift * 0.8f;
                        outG += shadowLift * 1.0f;
                        outB += shadowLift * 1.25f;
                    } else if (LUT_FUJI.equalsIgnoreCase(name)) {
                        // Fuji Pro 400H: Luminous minty greens, soft lavender shadow tint, airy highlights
                        outR = (float) Math.pow(inR, 0.98) * 0.97f + 0.02f;
                        outG = (float) Math.pow(inG, 0.95) * 1.03f + 0.03f;
                        outB = (float) Math.pow(inB, 0.96) * 1.05f + 0.04f;
                        outG += 0.04f * (float) Math.sin(luma * Math.PI);
                    } else if (LUT_TEAL_ORANGE.equalsIgnoreCase(name)) {
                        // Cine Teal & Orange: Cool cyan shadows & blue skies, warm amber highlights & skin
                        float shadowW = 1f - smoothstep(0.12f, 0.65f, luma);
                        float hlW = smoothstep(0.40f, 0.90f, luma);
                        outR = inR * (1f - 0.22f * shadowW) + 0.16f * hlW;
                        outG = inG * (1f + 0.04f * shadowW) + 0.06f * hlW;
                        outB = inB * (1f + 0.35f * shadowW) - 0.14f * hlW;
                        // S-curve contrast
                        float c = (float) (1.0 / (1.0 + Math.exp(-5.5 * (luma - 0.48))));
                        float scale = c / Math.max(0.001f, luma);
                        outR *= scale; outG *= scale; outB *= scale;
                    } else if (LUT_ANSEL_BW.equalsIgnoreCase(name)) {
                        // Ansel Adams Silver B&W: Zone system contrast with luminous silver highlights
                        float mono = 0.299f * inR + 0.587f * inG + 0.114f * inB;
                        float curved = (float) (Math.pow(mono, 1.38) / (Math.pow(mono, 1.38) + Math.pow(1.0 - mono, 1.38)));
                        outR = outG = outB = curved;
                    } else if (LUT_AMBER_70S.equalsIgnoreCase(name)) {
                        // Vintage 1970s warm amber slide film: honey highlights, faded warm blacks
                        outR = inR * 1.18f + 0.04f;
                        outG = inG * 1.03f + 0.02f;
                        outB = inB * 0.76f - 0.01f;
                    } else if (LUT_BLEACH.equalsIgnoreCase(name)) {
                        // Bleach Bypass: high contrast silver retention, crushed darks, metallic sheen
                        float mono = 0.2126f * inR + 0.7152f * inG + 0.0722f * inB;
                        float desatR = inR * 0.36f + mono * 0.64f;
                        float desatG = inG * 0.36f + mono * 0.64f;
                        float desatB = inB * 0.36f + mono * 0.64f;
                        outR = desatR < 0.5f ? (2f * desatR * desatR) : (1f - 2f * (1f - desatR) * (1f - desatR));
                        outG = desatG < 0.5f ? (2f * desatG * desatG) : (1f - 2f * (1f - desatG) * (1f - desatG));
                        outB = desatB < 0.5f ? (2f * desatB * desatB) : (1f - 2f * (1f - desatB) * (1f - desatB));
                    }

                    table[idx]     = Math.max(0f, Math.min(1f, outR));
                    table[idx + 1] = Math.max(0f, Math.min(1f, outG));
                    table[idx + 2] = Math.max(0f, Math.min(1f, outB));
                }
            }
        }
        return new Lut3D(name, name, size, table, false);
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Math.max(0f, Math.min(1f, (x - edge0) / (edge1 - edge0)));
        return t * t * (3f - 2f * t);
    }
}
