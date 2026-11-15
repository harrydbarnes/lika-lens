package com.scope.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.hardware.camera2.CameraCharacteristics;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;
import org.json.JSONException;
import org.json.JSONObject;

/** Scene-linear RAW development followed by the authored Lika Lens display rendering. */
final class RawDeveloper {
    private static final int MAX_RENDER_PIXELS = 50_000_000;
    private static final int[] SRGB_LUT = buildSrgbLut();
    private static final float[] SRGB_FLOAT_LUT = buildSrgbFloatLut();
    private RawDeveloper() { }

    static final class Look {
        String brain = "LIKA / FILM";
        String grainStructure = "Fine · 35 mm";
        int whiteBalanceKelvin;
        float exposureEv;
        float warmth;
        float contrast = 1.12f;
        float pivot = .20f;
        float saturation = .98f;
        float colorRollOff = .72f;
        float highlights = .68f;
        float shadows = .36f;
        float grain = .22f;
        float halation = .16f;
        float sharpening = .06f;
        float chromaNr = .15f;
        float vignette = .10f;
        String aspectRatio;
        boolean flipHorizontal;
        String highlightRecovery = "Adaptive blend";
        String shadowProfile = "Deep Cinema Blacks";
        String toneCurve = "Filmic S-Curve";
        String grainResponse = "Luminance-weighted";
        boolean easyMode = true;
        boolean skinProtection = true;
        boolean smartVibrance = true;
        boolean opticalCorrection = true;
        String frameBorder = "None";
        String lutName = "None";
        float lutIntensity = 1.0f;
        String colorSpace = "sRGB · Standard Display";
        String filmPushPull = "Box Speed · EI 400 (Standard)";
        boolean ultraHdr = true;

        static Look fromPreferences(Context context) {
            Look look = new Look();
            look.brain = AppSettings.brain(context);
            look.whiteBalanceKelvin = AppSettings.resolveWhiteBalanceKelvin(context);
            look.exposureEv = AppSettings.getInt(context, AppSettings.EXPOSURE, AppSettings.DEFAULT_EXPOSURE) / 10f;
            look.warmth = (AppSettings.getInt(context, AppSettings.WARMTH, AppSettings.DEFAULT_WARMTH) - 50) / 50f;
            look.contrast = AppSettings.getInt(context, AppSettings.CONTRAST, AppSettings.DEFAULT_CONTRAST) / 100f;
            look.pivot = AppSettings.getInt(context, AppSettings.PIVOT, AppSettings.DEFAULT_PIVOT) / 100f;
            look.saturation = AppSettings.getInt(context, AppSettings.SATURATION, AppSettings.DEFAULT_SATURATION) / 100f;
            look.colorRollOff = AppSettings.getInt(context, AppSettings.COLOR_ROLLOFF, AppSettings.DEFAULT_COLOR_ROLLOFF) / 100f;
            look.highlights = AppSettings.getInt(context, AppSettings.HIGHLIGHTS, AppSettings.DEFAULT_HIGHLIGHTS) / 100f;
            look.shadows = AppSettings.getInt(context, AppSettings.SHADOWS, AppSettings.DEFAULT_SHADOWS) / 100f;
            look.grain = AppSettings.getInt(context, AppSettings.GRAIN, AppSettings.DEFAULT_GRAIN) / 100f;
            look.halation = AppSettings.getInt(context, AppSettings.HALATION, AppSettings.DEFAULT_HALATION) / 100f;
            look.sharpening = AppSettings.getInt(context, AppSettings.SHARPEN, AppSettings.DEFAULT_SHARPEN) / 100f;
            look.chromaNr = AppSettings.getInt(context, AppSettings.COLOR_NR, AppSettings.DEFAULT_COLOR_NR) / 100f;
            look.vignette = AppSettings.getInt(context, AppSettings.VIGNETTE, AppSettings.DEFAULT_VIGNETTE) / 100f;
            look.grainStructure = AppSettings.get(context, "grain_structure", "Fine · 35 mm");
            look.aspectRatio = AppSettings.get(context, AppSettings.ASPECT_RATIO, "4:3");
            look.highlightRecovery = AppSettings.get(context, AppSettings.HIGHLIGHT_RECOVERY, "Adaptive blend");
            look.shadowProfile = AppSettings.get(context, AppSettings.SHADOW_PROFILE, "Deep Cinema Blacks");
            look.toneCurve = AppSettings.get(context, AppSettings.TONE_CURVE, "Filmic S-Curve");
            look.grainResponse = AppSettings.get(context, AppSettings.GRAIN_RESPONSE, "Luminance-weighted");
            look.easyMode = AppSettings.getBoolean(context, AppSettings.EASY_MODE, true);
            look.skinProtection = AppSettings.getBoolean(context, AppSettings.SKIN_PROTECTION, true);
            look.smartVibrance = AppSettings.getBoolean(context, AppSettings.SMART_VIBRANCE, true);
            look.opticalCorrection = AppSettings.getBoolean(context, AppSettings.OPTICAL_CORRECTION, true);
            look.frameBorder = AppSettings.get(context, AppSettings.FRAME_BORDER, "None");
            look.lutName = AppSettings.get(context, AppSettings.LUT_NAME, "None");
            look.lutIntensity = AppSettings.getInt(context, AppSettings.LUT_INTENSITY, AppSettings.DEFAULT_LUT_INTENSITY) / 100f;
            look.colorSpace = AppSettings.get(context, AppSettings.COLOR_SPACE, "sRGB · Standard Display");
            look.filmPushPull = AppSettings.get(context, AppSettings.FILM_PUSH_PULL, "Box Speed · EI 400 (Standard)");
            look.ultraHdr = AppSettings.getBoolean(context, AppSettings.ULTRA_HDR, true);
            return look;
        }

        Look copy() {
            Look c = new Look();
            c.brain = brain; c.grainStructure = grainStructure;
            c.whiteBalanceKelvin = whiteBalanceKelvin; c.exposureEv = exposureEv; c.warmth = warmth;
            c.contrast = contrast; c.pivot = pivot; c.saturation = saturation; c.colorRollOff = colorRollOff;
            c.highlights = highlights; c.shadows = shadows; c.grain = grain;
            c.halation = halation; c.sharpening = sharpening; c.chromaNr = chromaNr;
            c.vignette = vignette;
            c.aspectRatio = aspectRatio;
            c.flipHorizontal = flipHorizontal;
            c.highlightRecovery = highlightRecovery;
            c.shadowProfile = shadowProfile;
            c.toneCurve = toneCurve;
            c.grainResponse = grainResponse;
            c.easyMode = easyMode;
            c.skinProtection = skinProtection;
            c.smartVibrance = smartVibrance;
            c.opticalCorrection = opticalCorrection;
            c.frameBorder = frameBorder;
            c.lutName = lutName;
            c.lutIntensity = lutIntensity;
            c.colorSpace = colorSpace;
            c.filmPushPull = filmPushPull;
            c.ultraHdr = ultraHdr;
            return c;
        }

        String toJson() {
            try {
                JSONObject o = new JSONObject();
                o.put("brain", brain);
                o.put("grainStructure", grainStructure);
                o.put("whiteBalanceKelvin", whiteBalanceKelvin);
                o.put("exposureEv", (double) exposureEv);
                o.put("warmth", (double) warmth);
                o.put("contrast", (double) contrast);
                o.put("pivot", (double) pivot);
                o.put("saturation", (double) saturation);
                o.put("colorRollOff", (double) colorRollOff);
                o.put("highlights", (double) highlights);
                o.put("shadows", (double) shadows);
                o.put("grain", (double) grain);
                o.put("halation", (double) halation);
                o.put("sharpening", (double) sharpening);
                o.put("chromaNr", (double) chromaNr);
                o.put("vignette", (double) vignette);
                if (aspectRatio != null) o.put("aspectRatio", aspectRatio);
                o.put("flipHorizontal", flipHorizontal);
                o.put("highlightRecovery", highlightRecovery);
                o.put("shadowProfile", shadowProfile);
                o.put("toneCurve", toneCurve);
                o.put("grainResponse", grainResponse);
                o.put("easyMode", easyMode);
                o.put("skinProtection", skinProtection);
                o.put("smartVibrance", smartVibrance);
                o.put("opticalCorrection", opticalCorrection);
                o.put("frameBorder", frameBorder);
                o.put("lutName", lutName != null ? lutName : "None");
                o.put("lutIntensity", (double) lutIntensity);
                if (colorSpace != null) o.put("colorSpace", colorSpace);
                if (filmPushPull != null) o.put("filmPushPull", filmPushPull);
                o.put("ultraHdr", ultraHdr);
                return o.toString();
            } catch (JSONException e) {
                return null;
            }
        }

        static Look fromJson(String jsonStr) {
            if (jsonStr == null || jsonStr.trim().isEmpty()) return null;
            try {
                JSONObject o = new JSONObject(jsonStr);
                Look look = new Look();
                if (o.has("brain")) look.brain = o.getString("brain");
                if (o.has("grainStructure")) look.grainStructure = o.getString("grainStructure");
                if (o.has("whiteBalanceKelvin")) look.whiteBalanceKelvin = o.getInt("whiteBalanceKelvin");
                if (o.has("exposureEv")) look.exposureEv = (float) o.getDouble("exposureEv");
                if (o.has("warmth")) look.warmth = (float) o.getDouble("warmth");
                if (o.has("contrast")) look.contrast = (float) o.getDouble("contrast");
                if (o.has("pivot")) look.pivot = (float) o.getDouble("pivot");
                if (o.has("saturation")) look.saturation = (float) o.getDouble("saturation");
                if (o.has("colorRollOff")) look.colorRollOff = (float) o.getDouble("colorRollOff");
                if (o.has("highlights")) look.highlights = (float) o.getDouble("highlights");
                if (o.has("shadows")) look.shadows = (float) o.getDouble("shadows");
                if (o.has("grain")) look.grain = (float) o.getDouble("grain");
                if (o.has("halation")) look.halation = (float) o.getDouble("halation");
                if (o.has("sharpening")) look.sharpening = (float) o.getDouble("sharpening");
                if (o.has("chromaNr")) look.chromaNr = (float) o.getDouble("chromaNr");
                if (o.has("vignette")) look.vignette = (float) o.getDouble("vignette");
                if (o.has("aspectRatio")) look.aspectRatio = o.getString("aspectRatio");
                if (o.has("flipHorizontal")) look.flipHorizontal = o.getBoolean("flipHorizontal");
                if (o.has("highlightRecovery")) look.highlightRecovery = o.getString("highlightRecovery");
                if (o.has("shadowProfile")) look.shadowProfile = o.getString("shadowProfile");
                if (o.has("toneCurve")) look.toneCurve = o.getString("toneCurve");
                if (o.has("grainResponse")) look.grainResponse = o.getString("grainResponse");
                if (o.has("easyMode")) look.easyMode = o.getBoolean("easyMode");
                if (o.has("skinProtection")) look.skinProtection = o.getBoolean("skinProtection");
                if (o.has("smartVibrance")) look.smartVibrance = o.getBoolean("smartVibrance");
                if (o.has("opticalCorrection")) look.opticalCorrection = o.getBoolean("opticalCorrection");
                if (o.has("frameBorder")) look.frameBorder = o.getString("frameBorder");
                if (o.has("lutName")) look.lutName = o.getString("lutName");
                if (o.has("lutIntensity")) look.lutIntensity = (float) o.getDouble("lutIntensity");
                if (o.has("colorSpace")) look.colorSpace = o.getString("colorSpace");
                if (o.has("filmPushPull")) look.filmPushPull = o.getString("filmPushPull");
                if (o.has("ultraHdr")) look.ultraHdr = o.getBoolean("ultraHdr");
                return look;
            } catch (JSONException e) {
                return null;
            }
        }
    }

    private static final int BRAIN_ID_LIKA_FILM = 0;
    private static final int BRAIN_ID_KODACHROME = 1;
    private static final int BRAIN_ID_FUJI_ETERNA = 2;
    private static final int BRAIN_ID_BLEACH = 3;
    private static final int BRAIN_ID_MONO = 4;
    private static final int BRAIN_ID_NEUTRAL = 5;

    private static final int CURVE_FILMIC = 0;
    private static final int CURVE_LINEAR = 1;
    private static final int CURVE_LOG = 2;

    private static final int HL_ADAPTIVE = 0;
    private static final int HL_SUBTRACTIVE = 1;
    private static final int HL_HARD = 2;

    private static final float INV_LOG1P_7 = 1.0f / (float) Math.log1p(7.0);

    /** Precomputed frame-invariant look constants to eliminate per-pixel String checks and redundant math. */
    static final class PreparedLook {
        final Look look;
        final boolean displayReferred;
        final int brainId;
        final int curveId;
        final int hlRecId;
        final boolean liftedShadows;
        final float safePivot;
        final float safeContrast;
        final float toeContrast;
        final float logPivot;
        final float invSafeLogPivot;
        final float logContrast;
        final float shadowDepth;
        final float knee;
        final float shoulderSpan;
        final float satBase;
        final float rollOff;
        final float rollOffSlope;
        final boolean applySkinProtection;
        final boolean applySmartVibrance;
        final float ambientWarmthFactor;
        final boolean fluorescentSpike;
        final float grainAmount;
        final int grainScale;
        final int coarseDiv;
        final float fineWeight;
        final float coarseWeight;
        final boolean uniformGrain;
        final LutManager.Lut3D lut;
        final float lutIntensity;
        final float exposureScale;
        final float displayWbShift;

        PreparedLook(Look look, RawArchive.Frame frame, LutManager.Lut3D lut) {
            this(look, frame, lut, false);
        }

        PreparedLook(Look look, RawArchive.Frame frame, LutManager.Lut3D lut, boolean displayReferred) {
            this.look = look != null ? look : new Look();
            this.displayReferred = displayReferred;
            this.exposureScale = (float) Math.pow(2.0, this.look.exposureEv);
            this.displayWbShift = this.look.whiteBalanceKelvin <= 0 ? 1f
                    : (float) Math.sqrt(clamp(this.look.whiteBalanceKelvin / 5600f, .55f, 1.45f));
            String brain = this.look.brain == null ? "LIKA / FILM" : this.look.brain;
            if (brain.startsWith("MONO")) {
                this.brainId = BRAIN_ID_MONO;
            } else if (brain.startsWith("KODACHROME")) {
                this.brainId = BRAIN_ID_KODACHROME;
            } else if (brain.startsWith("FUJI") || brain.contains("ETERNA")) {
                this.brainId = BRAIN_ID_FUJI_ETERNA;
            } else if (brain.startsWith("BLEACH")) {
                this.brainId = BRAIN_ID_BLEACH;
            } else if (brain.startsWith("NEUTRAL")) {
                this.brainId = BRAIN_ID_NEUTRAL;
            } else {
                this.brainId = BRAIN_ID_LIKA_FILM;
            }

            String pushPull = this.look.filmPushPull == null ? "Box Speed" : this.look.filmPushPull;
            boolean push1 = pushPull.startsWith("+1");
            boolean push2 = pushPull.startsWith("+2");
            boolean pull1 = pushPull.startsWith("-1");

            float effectiveContrast = this.look.contrast;
            float effectivePivot = this.look.pivot;
            if (this.brainId == BRAIN_ID_KODACHROME) {
                effectiveContrast = this.look.contrast * 1.24f;
                effectivePivot = Math.max(0.12f, this.look.pivot * 0.90f);
            } else if (this.brainId == BRAIN_ID_FUJI_ETERNA) {
                effectiveContrast = this.look.contrast * 0.84f;
                effectivePivot = this.look.pivot * 1.10f;
            } else if (this.brainId == BRAIN_ID_BLEACH) {
                effectiveContrast = this.look.contrast * 1.38f;
                effectivePivot = 0.22f;
            } else if (this.brainId == BRAIN_ID_MONO) {
                effectiveContrast = this.look.contrast * 1.14f;
            } else if (this.brainId == BRAIN_ID_NEUTRAL) {
                effectiveContrast = this.look.contrast * 0.86f;
                effectivePivot = 0.22f;
            } else {
                effectiveContrast = this.look.contrast * 1.06f;
            }
            if (displayReferred) {
                // Camera2 preview and JPEG inputs already include the ISP baseline S-curve (~1.12);
                // normalize relative to 1.12 so NEUTRAL stays display-neutral and film brains don't double-stack.
                if (this.brainId == BRAIN_ID_NEUTRAL) {
                    effectiveContrast = this.look.contrast / 1.12f;
                } else {
                    effectiveContrast = 1.0f + (effectiveContrast - 1.12f) * 0.72f;
                }
                effectivePivot = clamp(effectivePivot * 1.15f, 0.18f, 0.45f);
            }
            if (push1) {
                effectiveContrast *= 1.14f;
                effectivePivot *= 0.92f;
            } else if (push2) {
                effectiveContrast *= 1.28f;
                effectivePivot *= 0.85f;
            } else if (pull1) {
                effectiveContrast *= 0.86f;
                effectivePivot *= 1.12f;
            }
            this.safePivot = clamp(effectivePivot, 0.02f, 0.85f);
            this.safeContrast = clamp(effectiveContrast, 0.35f, 2.50f);
            if (this.look.easyMode && !push1 && !push2) {
                if (displayReferred) {
                    this.toeContrast = this.brainId == BRAIN_ID_NEUTRAL
                            ? this.safeContrast
                            : clamp(this.safeContrast * 0.94f, 0.86f, 1.25f);
                } else if (this.brainId == BRAIN_ID_KODACHROME) {
                    this.toeContrast = clamp(this.safeContrast * 0.94f, 0.95f, 1.38f);
                } else if (this.brainId == BRAIN_ID_BLEACH) {
                    this.toeContrast = clamp(this.safeContrast * 0.96f, 1.05f, 1.45f);
                } else if (this.brainId == BRAIN_ID_FUJI_ETERNA) {
                    this.toeContrast = clamp(this.safeContrast * 0.88f, 0.78f, 0.92f);
                } else if (this.brainId == BRAIN_ID_NEUTRAL) {
                    this.toeContrast = clamp(this.safeContrast * 0.92f, 0.80f, 0.94f);
                } else if (this.brainId == BRAIN_ID_MONO) {
                    this.toeContrast = clamp(this.safeContrast * 0.92f, 0.88f, 1.25f);
                } else {
                    this.toeContrast = clamp(this.safeContrast * 0.88f, 0.88f, 1.05f);
                }
            } else {
                this.toeContrast = this.safeContrast;
            }

            String curve = this.look.toneCurve == null ? "Filmic S-Curve" : this.look.toneCurve;
            if (curve.startsWith("Linear")) {
                this.curveId = CURVE_LINEAR;
            } else if (curve.startsWith("Log")) {
                this.curveId = CURVE_LOG;
            } else {
                this.curveId = CURVE_FILMIC;
            }
            this.logPivot = (float) Math.log1p(this.safePivot * 7.0f) * INV_LOG1P_7;
            this.invSafeLogPivot = 1.0f / Math.max(0.01f, this.logPivot);
            this.logContrast = this.safeContrast * 0.78f;

            String shadowProf = this.look.shadowProfile == null ? "Deep Cinema Blacks" : this.look.shadowProfile;
            float sDepth = clamp(this.look.shadows, 0f, 1f);
            if (this.brainId == BRAIN_ID_NEUTRAL && !push1 && !push2 && !pull1) {
                this.liftedShadows = false;
                sDepth = Math.max(0f, sDepth - 0.36f) * 0.50f;
            } else {
                if (shadowProf.startsWith("Lifted") || this.brainId == BRAIN_ID_FUJI_ETERNA || pull1) {
                    this.liftedShadows = !push1 && !push2;
                    sDepth *= pull1 ? 0.28f : 0.32f;
                } else {
                    this.liftedShadows = false;
                    if (shadowProf.startsWith("Neutral")) {
                        sDepth *= 0.45f;
                    } else if (this.brainId == BRAIN_ID_KODACHROME) {
                        sDepth = Math.min(1.25f, sDepth * 1.15f);
                    } else if (this.brainId == BRAIN_ID_BLEACH) {
                        sDepth = Math.min(1.35f, sDepth * 1.30f);
                    } else {
                        sDepth *= 0.68f;
                    }
                }
                if (displayReferred) {
                    sDepth *= 0.45f;
                }
            }
            if (push1) sDepth = Math.min(1.30f, sDepth * 1.25f + 0.15f);
            else if (push2) sDepth = Math.min(1.45f, sDepth * 1.45f + 0.25f);
            this.shadowDepth = sDepth;

            float shoulder = clamp(this.look.highlights, 0f, 1f);
            if (this.look.easyMode && shoulder < 0.65f) {
                shoulder = 0.65f;
            }
            if (pull1) shoulder = Math.min(1.0f, shoulder + 0.18f);
            this.knee = this.look.easyMode ? (0.78f - 0.30f * shoulder) : (0.86f - 0.24f * shoulder);
            float shoulderCeil = 1.15f - 0.13f * shoulder;
            this.shoulderSpan = Math.max(0.10f, shoulderCeil - this.knee);

            String hlRec = this.look.highlightRecovery == null ? "Adaptive blend" : this.look.highlightRecovery;
            if (hlRec.startsWith("Hard")) {
                this.hlRecId = HL_HARD;
            } else if (hlRec.startsWith("Subtractive") || this.look.easyMode) {
                this.hlRecId = HL_SUBTRACTIVE;
            } else {
                this.hlRecId = HL_ADAPTIVE;
            }

            float sb = this.look.saturation;
            if (this.brainId == BRAIN_ID_BLEACH) {
                sb *= 0.30f;
            } else if (this.brainId == BRAIN_ID_KODACHROME) {
                sb *= 1.18f;
            } else if (this.brainId == BRAIN_ID_FUJI_ETERNA) {
                sb *= 0.85f;
            } else if (this.brainId == BRAIN_ID_LIKA_FILM) {
                sb *= 1.04f;
            } else if (this.brainId == BRAIN_ID_NEUTRAL) {
                sb = Math.abs(sb - 0.98f) < 0.01f ? 1.00f : sb;
            }
            if (push1) sb *= 1.06f;
            else if (push2) sb *= 0.94f;
            else if (pull1) sb *= 0.90f;
            this.satBase = sb;

            this.rollOff = clamp(this.look.colorRollOff, 0f, 1f);
            this.rollOffSlope = 2f + this.rollOff * 5.5f;
            this.applySkinProtection = this.look.skinProtection || this.look.easyMode;
            this.applySmartVibrance = (this.look.smartVibrance || this.look.easyMode)
                    && this.brainId != BRAIN_ID_NEUTRAL && this.brainId != BRAIN_ID_MONO && this.brainId != BRAIN_ID_BLEACH;

            this.fluorescentSpike = isFluorescentSpike(this.look, frame);
            this.ambientWarmthFactor = computeAmbientWarmth(this.look, frame, this.fluorescentSpike);

            float gAmt = clamp(this.look.grain, 0f, 1f);
            if (this.look.easyMode && frame != null && frame.sensitivityIso > 0) {
                if (frame.sensitivityIso < 100) {
                    gAmt *= 0.75f;
                } else if (frame.sensitivityIso > 800) {
                    gAmt *= 0.65f;
                }
            }
            if (push1) gAmt = clamp(gAmt * 1.35f + 0.04f, 0f, 1f);
            else if (push2) gAmt = clamp(gAmt * 1.75f + 0.09f, 0f, 1f);
            else if (pull1) gAmt *= 0.65f;
            this.grainAmount = gAmt;
            String gStruct = this.look.grainStructure == null ? "Fine · 35 mm" : this.look.grainStructure;
            int baseScale = gStruct.startsWith("Coarse") ? 5 : gStruct.startsWith("Soft") ? 2 : 1;
            if (push2 && baseScale < 2) baseScale = 2;
            this.grainScale = baseScale;
            this.coarseDiv = this.grainScale * 3;
            this.fineWeight = this.grainScale == 1 ? .72f : .40f;
            this.coarseWeight = this.grainScale == 1 ? .28f : .60f;
            String gResp = this.look.grainResponse == null ? "Luminance-weighted" : this.look.grainResponse;
            this.uniformGrain = gResp.startsWith("Uniform");
            this.lut = lut;
            this.lutIntensity = this.look.lutIntensity;
        }
    }

    private static boolean isFluorescentSpike(Look look, RawArchive.Frame frame) {
        if (look != null && look.whiteBalanceKelvin > 0 && look.whiteBalanceKelvin < 4000) return false;
        if (frame == null || frame.wbGains == null || frame.wbGains.length < 4) return false;
        float greenGain = (frame.wbGains[1] + frame.wbGains[2]) * .5f;
        return greenGain > 1.35f * frame.wbGains[0] && greenGain > 1.35f * frame.wbGains[3];
    }

    private static float computeAmbientWarmth(Look look, RawArchive.Frame frame, boolean fluo) {
        if (look != null && look.whiteBalanceKelvin > 0 && look.whiteBalanceKelvin < 4000) {
            return clamp(0.5f + 0.5f * (look.whiteBalanceKelvin - 2800f) / 1200f, 0.35f, 1.0f);
        }
        if (frame != null && frame.wbGains != null && frame.wbGains.length >= 4) {
            // Low red / high blue AWB gains indicate warm illumination. Do not add a second
            // full tungsten tint on top of a scene that has already been white balanced.
            float redBlue = frame.wbGains[0] / Math.max(.01f, frame.wbGains[3]);
            float ambWarmth = .35f + .65f * smoothstep(.5f, 1.1f, redBlue);
            if (fluo) {
                ambWarmth = Math.min(ambWarmth, look != null && look.easyMode ? 0.50f : 0.65f);
            }
            return ambWarmth;
        }
        return 1.0f;
    }

    /** Cached scene-linear RGB buffer before user EV & tone/colour grading for fast editor scrubbing. */
    static final class LinearSceneCache {
        final String frameKey;
        final RawArchive.Frame frame;
        final int maxDimension;
        final String aspect;
        final boolean flipHorizontal;
        final int whiteBalanceKelvin;
        final boolean easyMode;
        final boolean opticalCorrection;
        final int outW;
        final int outH;
        final float[] baseRgb;
        final float autoSceneGain;
        final int seed;

        LinearSceneCache(RawArchive.Frame frame, int maxDimension, String aspect,
                         boolean flipHorizontal, int whiteBalanceKelvin, boolean easyMode,
                         boolean opticalCorrection, int outW, int outH,
                         float[] baseRgb, float autoSceneGain, int seed) {
            this.frameKey = frame.key;
            this.frame = frame;
            this.maxDimension = maxDimension;
            this.aspect = aspect;
            this.flipHorizontal = flipHorizontal;
            this.whiteBalanceKelvin = whiteBalanceKelvin;
            this.easyMode = easyMode;
            this.opticalCorrection = opticalCorrection;
            this.outW = outW;
            this.outH = outH;
            this.baseRgb = baseRgb;
            this.autoSceneGain = autoSceneGain;
            this.seed = seed;
        }

        boolean matches(RawArchive.Frame candidate, Look look, int targetMaxDim) {
            if (candidate == null || look == null || candidate != this.frame) return false;
            String candidateAspect = look.aspectRatio != null ? look.aspectRatio : candidate.aspectRatio;
            boolean opt = look.opticalCorrection && candidate.lsmGains != null && candidate.lsmCols > 1 && candidate.lsmRows > 1;
            return this.maxDimension == targetMaxDim
                    && this.flipHorizontal == look.flipHorizontal
                    && this.whiteBalanceKelvin == look.whiteBalanceKelvin
                    && this.easyMode == look.easyMode
                    && this.opticalCorrection == opt
                    && java.util.Objects.equals(this.aspect, candidateAspect)
                    && java.util.Objects.equals(this.frameKey, candidate.key);
        }
    }

    private static short[] extractRawShorts(RawArchive.Frame frame, ByteBuffer raw) {
        int width = frame.width, height = frame.height;
        short[] rawShorts = new short[width * height];
        int totalBytes = width * height * 2;
        if (frame.rawSamples != null && raw.limit() >= totalBytes) {
            raw.position(0);
            raw.asShortBuffer().get(rawShorts);
        } else {
            int rowStride = frame.rowStride;
            int pixelStride = frame.pixelStride;
            if (pixelStride == 2 && rowStride == width * 2 && raw.limit() >= totalBytes) {
                raw.position(0);
                raw.asShortBuffer().get(rawShorts);
            } else if (pixelStride == 2 && raw.limit() >= (height - 1) * rowStride + width * 2) {
                ByteOrder order = raw.order();
                for (int y = 0; y < height; y++) {
                    raw.position(y * rowStride);
                    ByteBuffer slice = raw.slice().order(order);
                    slice.limit(width * 2);
                    slice.asShortBuffer().get(rawShorts, y * width, width);
                }
            } else {
                for (int y = 0; y < height; y++) {
                    int rowStart = y * rowStride;
                    int dstRow = y * width;
                    for (int x = 0; x < width; x++) {
                        int off = rowStart + x * pixelStride;
                        rawShorts[dstRow + x] = (off >= 0 && off + 1 < raw.limit()) ? raw.getShort(off) : 0;
                    }
                }
            }
        }
        return rawShorts;
    }

    static LinearSceneCache prepareLinearScene(RawArchive.Frame frame, Look look, int maxDimension) {
        if (frame == null || (frame.rawSamples == null && frame.captureBuffer == null)) return null;
        if (look == null) look = new Look();
        final int width = frame.width, height = frame.height;
        final int orientation = normalizeOrientation(frame.orientation);
        final int displayWidth = orientation == 90 || orientation == 270 ? height : width;
        final int displayHeight = orientation == 90 || orientation == 270 ? width : height;
        final String aspect = look.aspectRatio != null ? look.aspectRatio : frame.aspectRatio;
        final float[] crop = cropRect(displayWidth, displayHeight, aspect);
        final float cropWidth = crop[2], cropHeight = crop[3];
        long cropPixels = Math.max(1L, (long) (cropWidth * cropHeight));
        final int step = maxDimension > 0
                ? Math.max(1, (int) Math.ceil(Math.max(cropWidth, cropHeight) / maxDimension))
                : Math.max(1, (int) Math.ceil(Math.sqrt(cropPixels / (double) renderPixelBudget(frame))));
        final int outW = Math.max(1, (int) Math.ceil(cropWidth / step));
        final int outH = Math.max(1, (int) Math.ceil(cropHeight / step));

        final short[] rawShorts = extractRawShorts(frame, frame.rawBuffer());
        final float[] cameraToXyz = profileFor(frame, look.whiteBalanceKelvin);
        final float rawWhite = frame.dynamicWhiteLevel > 0 ? frame.dynamicWhiteLevel : frame.staticWhiteLevel;
        final float white = rawWhite <= 0 ? 65535f : rawWhite;
        final float[] gains = whiteBalanceGains(frame, look.whiteBalanceKelvin);
        final boolean applyLsm = look.opticalCorrection && frame.lsmGains != null && frame.lsmCols > 1 && frame.lsmRows > 1;
        final float autoSceneGain = computeSceneExposureGain(rawShorts, frame, white, gains, cameraToXyz,
                crop, orientation, applyLsm, look);
        final boolean hasCct = frame.colorCorrectionTransform != null && frame.colorCorrectionTransform.length >= 9;
        final float[] cct = frame.colorCorrectionTransform;
        final boolean flipH = look.flipHorizontal;
        final boolean useQuad = step >= 2 && width >= 2 && height >= 2;
        final float[] baseRgb = new float[outW * outH * 3];
        final float gainR = gains[0] * autoSceneGain;
        final float gainG = (gains[1] + gains[2]) * 0.5f * autoSceneGain;
        final float gainB = gains[3] * autoSceneGain;

        java.util.stream.IntStream.range(0, outH).parallel().forEach(oy -> {
            float dy = Math.min(crop[1] + cropHeight - .5f, crop[1] + (oy + .5f) * cropHeight / outH);
            int rowBase = oy * outW * 3;
            float[] rgb = new float[3];
            float[] lsm = applyLsm ? new float[4] : null;
            for (int ox = 0; ox < outW; ox++) {
                float effectiveOx = flipH ? (outW - 1 - ox) : ox;
                float dx = Math.min(crop[0] + cropWidth - .5f, crop[0] + (effectiveOx + .5f) * cropWidth / outW);
                int sx, sy;
                switch (orientation) {
                    case 90: sx = Math.min(width - 1, (int) dy); sy = Math.max(0, Math.min(height - 1, height - 1 - (int) dx)); break;
                    case 180: sx = Math.max(0, Math.min(width - 1, width - 1 - (int) dx)); sy = Math.max(0, Math.min(height - 1, height - 1 - (int) dy)); break;
                    case 270: sx = Math.max(0, Math.min(width - 1, width - 1 - (int) dy)); sy = Math.min(height - 1, (int) dx); break;
                    default: sx = Math.min(width - 1, (int) dx); sy = Math.min(height - 1, (int) dy); break;
                }
                if (useQuad) sampleQuad(rawShorts, frame, white, sx, sy, rgb);
                else demosaicPixel(rawShorts, frame, white, sx, sy, rgb);
                float r = rgb[0], g = rgb[1], b = rgb[2];
                if (applyLsm) {
                    sampleLsm(frame, sx, sy, lsm);
                    float sig = Math.max(r, Math.max(g, b));
                    float lsmWeight = smoothstep(0.003f, 0.030f, sig);
                    r *= 1f + (lsm[0] - 1f) * lsmWeight;
                    g *= 1f + ((lsm[1] + lsm[2]) * 0.5f - 1f) * lsmWeight;
                    b *= 1f + (lsm[3] - 1f) * lsmWeight;
                }
                r *= gainR;
                g *= gainG;
                b *= gainB;
                float lr, lg, lb;
                if (hasCct) {
                    lr = cct[0] * r + cct[1] * g + cct[2] * b;
                    lg = cct[3] * r + cct[4] * g + cct[5] * b;
                    lb = cct[6] * r + cct[7] * g + cct[8] * b;
                } else {
                    float x = cameraToXyz[0] * r + cameraToXyz[1] * g + cameraToXyz[2] * b;
                    float y = cameraToXyz[3] * r + cameraToXyz[4] * g + cameraToXyz[5] * b;
                    float z = cameraToXyz[6] * r + cameraToXyz[7] * g + cameraToXyz[8] * b;
                    lr = 3.1339f * x - 1.6169f * y - .4906f * z;
                    lg = -.9785f * x + 1.9160f * y + .0333f * z;
                    lb = .0720f * x - .2290f * y + 1.4057f * z;
                }
                int idx = rowBase + ox * 3;
                baseRgb[idx] = lr;
                baseRgb[idx + 1] = lg;
                baseRgb[idx + 2] = lb;
            }
        });

        int seed = frame.key == null ? width * 31 + height : frame.key.hashCode();
        return new LinearSceneCache(frame, maxDimension, aspect, flipH, look.whiteBalanceKelvin,
                look.easyMode, applyLsm, outW, outH, baseRgb, autoSceneGain, seed);
    }

    static int[] developFromLinearPixels(LinearSceneCache scene, Look look) {
        if (scene == null) return null;
        if (look == null) look = new Look();
        final int outW = scene.outW, outH = scene.outH;
        final float[] baseRgb = scene.baseRgb;
        final float evScale = (float) Math.pow(2.0, look.exposureEv);
        final float exposureScale = evScale * scene.autoSceneGain;
        final LutManager.Lut3D lut = (look.lutName != null && !look.lutName.equals("None") && look.lutIntensity > 0.001f)
                ? LutManager.get(look.lutName) : null;
        final PreparedLook prep = new PreparedLook(look, scene.frame, lut);
        final int lgW = look.easyMode && !scene.frame.manualExposure ? Math.min(48, Math.max(4, outW)) : 0;
        final int lgH = look.easyMode && !scene.frame.manualExposure ? Math.min(36, Math.max(4, outH)) : 0;
        final float[] localGainGrid = lgW > 0
                ? buildLocalToneGainGridFromLinear(baseRgb, outW, outH, evScale, exposureScale, scene.frame, lgW, lgH, prep.brainId)
                : null;
        final int[] allPixels = new int[outW * outH];
        final float vigAmount = look.vignette;

        java.util.stream.IntStream.range(0, outH).parallel().forEach(oy -> {
            int rowOffset = oy * outW;
            int rgbRowBase = rowOffset * 3;
            float ny = outH <= 1 ? 0f : oy / (float) (outH - 1) * 2f - 1f;
            float nySq = ny * ny;
            for (int ox = 0; ox < outW; ox++) {
                int rIdx = rgbRowBase + ox * 3;
                float lr = baseRgb[rIdx] * evScale;
                float lg = baseRgb[rIdx + 1] * evScale;
                float lb = baseRgb[rIdx + 2] * evScale;
                if (localGainGrid != null) {
                    float lgain = sampleLocalGain(localGainGrid, lgW, lgH, ox, oy, outW, outH);
                    if (lgain > 1.0f) {
                        float pixY = Math.max(0f, .2126f * Math.max(0f, lr) + .7152f * Math.max(0f, lg) + .0722f * Math.max(0f, lb));
                        float signalGate = smoothstep(0.0020f, 0.012f, pixY);
                        lgain = 1.0f + (lgain - 1.0f) * signalGate;
                    }
                    lr *= lgain;
                    lg *= lgain;
                    lb *= lgain;
                }
                int color = renderPreparedLook(lr, lg, lb, prep, ox, oy, scene.seed);
                if (vigAmount > 0f) {
                    float nx = outW <= 1 ? 0f : ox / (float) (outW - 1) * 2f - 1f;
                    float falloff = Math.max(.72f, 1f - vigAmount * .18f * (nx * nx + nySq));
                    color = scaleColor(color, falloff);
                }
                allPixels[rowOffset + ox] = color;
            }
        });

        float effectiveChromaNr = look.chromaNr;
        if (look.easyMode && scene.frame.sensitivityIso >= 400 && effectiveChromaNr < 0.25f) {
            effectiveChromaNr = Math.max(effectiveChromaNr,
                    .25f + .15f * (1f - shadowRecoveryConfidence(scene.frame.sensitivityIso)));
        }
        applyPostGradeEffects(allPixels, outW, outH, look, prep, scene.frame, scene.seed, effectiveChromaNr);
        return allPixels;
    }

    static Bitmap developFromLinear(LinearSceneCache scene, Look look) {
        if (scene == null) return null;
        if (look == null) look = new Look();
        int[] pixels = developFromLinearPixels(scene, look);
        return finishBitmap(pixels, scene.outW, scene.outH, look, scene.frame);
    }

    static int[] developPixels(RawArchive.Frame frame, Look look, int maxDimension) {
        return developPixels(frame, look, maxDimension, null);
    }

    static int[] developPixels(RawArchive.Frame frame, Look look, int maxDimension, int[] outSize) {
        if (frame == null || (frame.rawSamples == null && frame.captureBuffer == null)) return null;
        if (look == null) look = new Look();
        final int width = frame.width, height = frame.height;
        final int orientation = normalizeOrientation(frame.orientation);
        final int displayWidth = orientation == 90 || orientation == 270 ? height : width;
        final int displayHeight = orientation == 90 || orientation == 270 ? width : height;
        final String aspect = look.aspectRatio != null ? look.aspectRatio : frame.aspectRatio;
        final float[] crop = cropRect(displayWidth, displayHeight, aspect);
        final float cropWidth = crop[2], cropHeight = crop[3];
        long cropPixels = Math.max(1L, (long) (cropWidth * cropHeight));
        final int step = maxDimension > 0
                ? Math.max(1, (int) Math.ceil(Math.max(cropWidth, cropHeight) / maxDimension))
                : Math.max(1, (int) Math.ceil(Math.sqrt(cropPixels / (double) renderPixelBudget(frame))));
        final int outW = Math.max(1, (int) Math.ceil(cropWidth / step));
        final int outH = Math.max(1, (int) Math.ceil(cropHeight / step));
        if (outSize != null && outSize.length >= 2) {
            outSize[0] = outW;
            outSize[1] = outH;
        }
        final short[] rawShorts = extractRawShorts(frame, frame.rawBuffer());
        final float[] cameraToXyz = profileFor(frame, look.whiteBalanceKelvin);
        final float rawWhite = frame.dynamicWhiteLevel > 0 ? frame.dynamicWhiteLevel : frame.staticWhiteLevel;
        final float white = rawWhite <= 0 ? 65535f : rawWhite;
        final float[] gains = whiteBalanceGains(frame, look.whiteBalanceKelvin);
        final boolean applyLsm = look.opticalCorrection && frame.lsmGains != null && frame.lsmCols > 1 && frame.lsmRows > 1;
        final float autoSceneGain = computeSceneExposureGain(rawShorts, frame, white, gains, cameraToXyz,
                crop, orientation, applyLsm, look);
        final float exposureScale = (float) Math.pow(2.0, look.exposureEv) * autoSceneGain;
        final int[] allPixels = new int[outW * outH];
        final int seed = frame.key == null ? width * 31 + height : frame.key.hashCode();
        final LutManager.Lut3D lut = (look.lutName != null && !look.lutName.equals("None") && look.lutIntensity > 0.001f)
                ? LutManager.get(look.lutName) : null;
        final PreparedLook prep = new PreparedLook(look, frame, lut);
        final boolean hasCct = frame.colorCorrectionTransform != null && frame.colorCorrectionTransform.length >= 9;
        final float[] cct = frame.colorCorrectionTransform;
        final boolean flipH = look.flipHorizontal;
        final float vigAmount = look.vignette;
        final boolean useQuad = step >= 2 && width >= 2 && height >= 2;
        final int lgW = look.easyMode && !frame.manualExposure ? Math.min(48, Math.max(4, outW)) : 0;
        final int lgH = look.easyMode && !frame.manualExposure ? Math.min(36, Math.max(4, outH)) : 0;
        final float[] localGainGrid = lgW > 0
                ? buildLocalToneGainGrid(rawShorts, frame, white, gains, cameraToXyz, hasCct, cct,
                        exposureScale, crop, cropWidth, cropHeight, orientation, applyLsm, flipH,
                        outW, outH, lgW, lgH, prep.brainId)
                : null;
        final float gainR = gains[0] * exposureScale;
        final float gainG = (gains[1] + gains[2]) * 0.5f * exposureScale;
        final float gainB = gains[3] * exposureScale;

        java.util.stream.IntStream.range(0, outH).parallel().forEach(oy -> {
            float dy = Math.min(crop[1] + cropHeight - .5f,
                    crop[1] + (oy + .5f) * cropHeight / outH);
            int rowOffset = oy * outW;
            float ny = outH <= 1 ? 0f : oy / (float) (outH - 1) * 2f - 1f;
            float nySq = ny * ny;
            float[] rgb = new float[3];
            float[] lsm = applyLsm ? new float[4] : null;
            for (int ox = 0; ox < outW; ox++) {
                float effectiveOx = flipH ? (outW - 1 - ox) : ox;
                float dx = Math.min(crop[0] + cropWidth - .5f,
                        crop[0] + (effectiveOx + .5f) * cropWidth / outW);
                int sx, sy;
                switch (orientation) {
                    case 90: sx = Math.min(width - 1, (int) dy); sy = Math.max(0, Math.min(height - 1, height - 1 - (int) dx)); break;
                    case 180: sx = Math.max(0, Math.min(width - 1, width - 1 - (int) dx)); sy = Math.max(0, Math.min(height - 1, height - 1 - (int) dy)); break;
                    case 270: sx = Math.max(0, Math.min(width - 1, width - 1 - (int) dy)); sy = Math.min(height - 1, (int) dx); break;
                    default: sx = Math.min(width - 1, (int) dx); sy = Math.min(height - 1, (int) dy); break;
                }
                if (useQuad) sampleQuad(rawShorts, frame, white, sx, sy, rgb);
                else demosaicPixel(rawShorts, frame, white, sx, sy, rgb);
                float r = rgb[0], g = rgb[1], b = rgb[2];
                if (applyLsm) {
                    sampleLsm(frame, sx, sy, lsm);
                    float sig = Math.max(r, Math.max(g, b));
                    float lsmWeight = smoothstep(0.003f, 0.030f, sig);
                    r *= 1f + (lsm[0] - 1f) * lsmWeight;
                    g *= 1f + ((lsm[1] + lsm[2]) * 0.5f - 1f) * lsmWeight;
                    b *= 1f + (lsm[3] - 1f) * lsmWeight;
                }
                r *= gainR;
                g *= gainG;
                b *= gainB;

                float lr, lg, lb;
                if (hasCct) {
                    lr = cct[0] * r + cct[1] * g + cct[2] * b;
                    lg = cct[3] * r + cct[4] * g + cct[5] * b;
                    lb = cct[6] * r + cct[7] * g + cct[8] * b;
                } else {
                    float x = cameraToXyz[0] * r + cameraToXyz[1] * g + cameraToXyz[2] * b;
                    float y = cameraToXyz[3] * r + cameraToXyz[4] * g + cameraToXyz[5] * b;
                    float z = cameraToXyz[6] * r + cameraToXyz[7] * g + cameraToXyz[8] * b;
                    // XYZ D50 to linear sRGB. The camera profile is kept separate from the authored look.
                    lr = 3.1339f * x - 1.6169f * y - .4906f * z;
                    lg = -.9785f * x + 1.9160f * y + .0333f * z;
                    lb = .0720f * x - .2290f * y + 1.4057f * z;
                }
                if (localGainGrid != null) {
                    float lgain = sampleLocalGain(localGainGrid, lgW, lgH, ox, oy, outW, outH);
                    if (lgain > 1.0f) {
                        float pixY = Math.max(0f, .2126f * Math.max(0f, lr) + .7152f * Math.max(0f, lg) + .0722f * Math.max(0f, lb));
                        float signalGate = smoothstep(0.0020f, 0.012f, pixY);
                        lgain = 1.0f + (lgain - 1.0f) * signalGate;
                    }
                    lr *= lgain;
                    lg *= lgain;
                    lb *= lgain;
                }
                int color = renderPreparedLook(lr, lg, lb, prep, ox, oy, seed);
                if (vigAmount > 0f) {
                    float nx = outW <= 1 ? 0f : ox / (float) (outW - 1) * 2f - 1f;
                    float falloff = Math.max(.72f, 1f - vigAmount * .18f * (nx * nx + nySq));
                    color = scaleColor(color, falloff);
                }
                allPixels[rowOffset + ox] = color;
            }
        });

        float effectiveChromaNr = look.chromaNr;
        if (look.easyMode && frame.sensitivityIso >= 400 && effectiveChromaNr < 0.25f) {
            effectiveChromaNr = Math.max(effectiveChromaNr,
                    .25f + .15f * (1f - shadowRecoveryConfidence(frame.sensitivityIso)));
        }
        applyPostGradeEffects(allPixels, outW, outH, look, prep, frame, seed, effectiveChromaNr);
        return allPixels;
    }

    /** maxDimension=0 renders at the sensor's available resolution, within the safety pixel budget. */
    static Bitmap develop(RawArchive.Frame frame, Look look, int maxDimension) {
        if (frame == null || (frame.rawSamples == null && frame.captureBuffer == null)) return null;
        if (look == null) look = new Look();
        int[] outSize = new int[2];
        int[] allPixels = developPixels(frame, look, maxDimension, outSize);
        if (allPixels == null) return null;
        return finishBitmap(allPixels, outSize[0], outSize[1], look, frame);
    }

    private static void applyPostGradeEffects(int[] pixels, int outW, int outH, Look look,
                                              PreparedLook prep, RawArchive.Frame frame, int seed,
                                              float effectiveChromaNr) {
        if (effectiveChromaNr > 0f) applyChromaDenoise(pixels, outW, outH, effectiveChromaNr);
        if (look.halation > 0f) applyHalation(pixels, outW, outH, look.halation, look.aspectRatio != null ? look.aspectRatio : (frame != null ? frame.aspectRatio : null));
        if (look.sharpening > 0f) applySharpen(pixels, outW, outH, Math.min(.14f, look.sharpening * .14f));
        applyFilmGrain(pixels, outW, outH, prep, seed);
    }

    private static Bitmap finishBitmap(int[] pixels, int outW, int outH, Look look, RawArchive.Frame frame) {
        boolean hasBorder = look.frameBorder != null && !look.frameBorder.startsWith("None");
        Bitmap bitmap = createColorSpaceAwareBitmap(pixels, outW, outH, look, !hasBorder);
        if (hasBorder) {
            int[] pad = borderPadding(outW, outH, look);
            bitmap = applyFrameBorder(bitmap, look, frame);
            if (look.ultraHdr && android.os.Build.VERSION.SDK_INT >= 34 && bitmap != null) {
                attachUltraHdrGainmap(bitmap, pixels, outW, outH, look,
                        pad[0], pad[1], outW + pad[0] * 2, outH + pad[1] + pad[2]);
            }
        }
        return bitmap;
    }

    private static Bitmap finishPixels(int[] pixels, int outW, int outH, Look look,
                                       PreparedLook prep, RawArchive.Frame frame, int seed,
                                       float effectiveChromaNr) {
        applyPostGradeEffects(pixels, outW, outH, look, prep, frame, seed, effectiveChromaNr);
        return finishBitmap(pixels, outW, outH, look, frame);
    }

    private static Bitmap createColorSpaceAwareBitmap(int[] pixels, int w, int h, Look look, boolean attachHdr) {
        String colorSpace = look != null ? look.colorSpace : null;
        Bitmap bmp = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
                && colorSpace != null && colorSpace.contains("Display P3")) {
            try {
                android.graphics.ColorSpace p3 = android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.DISPLAY_P3);
                if (p3 != null) {
                    bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888, false, p3);
                    bmp.setPixels(pixels, 0, w, 0, 0, w, h);
                }
            } catch (Throwable ignored) {
            }
        }
        if (bmp == null) {
            bmp = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888);
        }
        if (attachHdr && look != null && look.ultraHdr && android.os.Build.VERSION.SDK_INT >= 34) {
            attachUltraHdrGainmap(bmp, pixels, w, h, look, 0, 0, w, h);
        }
        return bmp;
    }

    /**
     * Generates and attaches an ISO 21496-1 Ultra HDR Gainmap on Android 14+ (API 34+) so bright
     * film highlights and warm halation blooms radiate with true optical HDR headroom on OLED displays.
     */
    private static void attachUltraHdrGainmap(Bitmap bmp, int[] pixels, int w, int h, Look look,
                                              int padLeft, int padTop, int targetW, int targetH) {
        if (bmp == null || pixels == null || w < 8 || h < 8) return;
        if (android.os.Build.VERSION.SDK_INT < 34) return;
        try {
            int gw = Math.max(4, targetW / 4);
            int gh = Math.max(4, targetH / 4);
            byte[] gainBytes = new byte[gw * gh];
            int photoGw = Math.max(1, (w * gw + targetW / 2) / targetW);
            int photoGh = Math.max(1, (h * gh + targetH / 2) / targetH);
            int gx0 = Math.max(0, (padLeft * gw + targetW / 2) / targetW);
            int gy0 = Math.max(0, (padTop * gh + targetH / 2) / targetH);
            int gxEnd = Math.min(gw, gx0 + photoGw);
            int gyEnd = Math.min(gh, gy0 + photoGh);
            float halBoost = 1.0f + clamp(look.halation, 0f, 1f) * 0.45f;
            for (int gy = gy0; gy < gyEnd; gy++) {
                int sy = Math.min(h - 1, ((gy - gy0) * h) / Math.max(1, photoGh));
                int rowOff = sy * w;
                int gRowOff = gy * gw;
                for (int gx = gx0; gx < gxEnd; gx++) {
                    int sx = Math.min(w - 1, ((gx - gx0) * w) / Math.max(1, photoGw));
                    int c = pixels[rowOff + sx];
                    int r = (c >> 16) & 0xFF;
                    int g = (c >> 8) & 0xFF;
                    int b = c & 0xFF;
                    float luma = (0.26f * r + 0.64f * g + 0.10f * b) / 255f;
                    float peak = Math.max(r, Math.max(g, b)) / 255f;
                    float sig = luma * 0.65f + peak * 0.35f;
                    float gain = smoothstep(0.60f, 0.99f, sig) * halBoost;
                    int v = Math.min(255, Math.max(0, Math.round(clamp(gain, 0f, 1f) * 255f)));
                    gainBytes[gRowOff + gx] = (byte) v;
                }
            }
            Bitmap gainBmp = Bitmap.createBitmap(gw, gh, Bitmap.Config.ALPHA_8);
            gainBmp.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(gainBytes));
            android.graphics.Gainmap gainmap = new android.graphics.Gainmap(gainBmp);
            float maxHdrRatio = 2.35f;
            gainmap.setRatioMin(1.0f, 1.0f, 1.0f);
            gainmap.setRatioMax(maxHdrRatio, maxHdrRatio, maxHdrRatio);
            gainmap.setGamma(1.0f, 1.0f, 1.0f);
            gainmap.setEpsilonSdr(0.01f, 0.01f, 0.01f);
            gainmap.setEpsilonHdr(0.01f, 0.01f, 0.01f);
            gainmap.setMinDisplayRatioForHdrTransition(1.0f);
            gainmap.setDisplayRatioForFullHdr(maxHdrRatio);
            bmp.setGainmap(gainmap);
        } catch (Throwable ignored) {
        }
    }

    /** JPEG fallback keeps the file usable, but cannot undo the phone's baked image processing. */
    public static Bitmap developJpeg(byte[] bytes, Context context) {
        return developJpeg(bytes, context, false);
    }

    public static Bitmap developJpeg(byte[] bytes, Context context, boolean flipHorizontal) {
        Look look = Look.fromPreferences(context);
        look.flipHorizontal = flipHorizontal;
        return developJpeg(bytes, look);
    }

    public static Bitmap developJpeg(byte[] bytes, Look look) {
        return developJpeg(bytes, look, 0);
    }

    static Bitmap decodeOrientedJpegForGrading(byte[] bytes, int fallbackOrientation) {
        if (bytes == null || bytes.length == 0) return null;
        int maxSide = 3000;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
        int sampleSize = 1;
        int rawMax = Math.max(opts.outWidth, opts.outHeight);
        while (rawMax / (sampleSize * 2) >= maxSide) {
            sampleSize *= 2;
        }
        BitmapFactory.Options decodeOpts = new BitmapFactory.Options();
        decodeOpts.inSampleSize = sampleSize;
        Bitmap source = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, decodeOpts);
        if (source == null) return null;
        source = CaptureOrientation.orientJpeg(source, bytes, fallbackOrientation);
        float scale = Math.min(1f, maxSide / (float) Math.max(source.getWidth(), source.getHeight()));
        Bitmap bitmap = scale < 1 ? Bitmap.createScaledBitmap(source,
                Math.max(1, Math.round(source.getWidth() * scale)), Math.max(1, Math.round(source.getHeight() * scale)), true) : source;
        if (bitmap != source) source.recycle();
        return bitmap;
    }

    public static Bitmap developJpeg(byte[] bytes, Look look, int fallbackOrientation) {
        Bitmap bitmap = decodeOrientedJpegForGrading(bytes, fallbackOrientation);
        if (bitmap == null) return null;
        return developBitmap(bitmap, look, true);
    }

    /**
     * Develops an in-memory Bitmap applying the full look pipeline (color science, LUT, tone curve,
     * shadows, halation, sharpening, vignette, and optional frame border).
     */
    public static Bitmap developBitmap(Bitmap bitmap, Look look, boolean recycleSource) {
        if (bitmap == null) return null;
        if (look == null) look = new Look();

        final int w = bitmap.getWidth(), h = bitmap.getHeight();
        final int[] pixels = new int[w * h];
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
        if (recycleSource && !bitmap.isRecycled()) {
            bitmap.recycle();
        }

        final PreparedLook prep = prepareDisplayLook(look);
        final boolean flipH = look.flipHorizontal;
        final float vigAmount = look.vignette;

        java.util.stream.IntStream.range(0, h).parallel().forEach(y -> {
            int rowOffset = y * w;
            if (flipH) {
                for (int x = 0, rx = w - 1; x < rx; x++, rx--) {
                    int tmp = pixels[rowOffset + x];
                    pixels[rowOffset + x] = pixels[rowOffset + rx];
                    pixels[rowOffset + rx] = tmp;
                }
            }
            float ny = h <= 1 ? 0f : y / (float) (h - 1) * 2f - 1f;
            float nySq = ny * ny;
            for (int x = 0; x < w; x++) {
                int c = pixels[rowOffset + x];
                int rendered = renderDisplayRgb(((c >> 16) & 0xff) / 255f,
                        ((c >> 8) & 0xff) / 255f, (c & 0xff) / 255f, prep);
                if (vigAmount > 0f) {
                    float nx = w <= 1 ? 0f : x / (float) (w - 1) * 2f - 1f;
                    float falloff = Math.max(.72f, 1f - vigAmount * .18f * (nx * nx + nySq));
                    rendered = scaleColor(rendered, falloff);
                }
                pixels[rowOffset + x] = rendered;
            }
        });

        float effectiveJpegChromaNr = look.chromaNr;
        if (look.easyMode && effectiveJpegChromaNr < 0.15f) effectiveJpegChromaNr = 0.15f;
        return finishPixels(pixels, w, h, look, prep, null, 0x53434f50, effectiveJpegChromaNr);
    }

    static PreparedLook prepareDisplayLook(Look look) {
        return prepareDisplayLook(look, null);
    }

    static PreparedLook prepareDisplayLook(Look look, RawArchive.Frame frame) {
        Look resolved = look != null ? look : new Look();
        LutManager.Lut3D lut = resolved.lutName != null && !"None".equalsIgnoreCase(resolved.lutName)
                && resolved.lutIntensity > .001f ? LutManager.get(resolved.lutName) : null;
        return new PreparedLook(resolved, frame, lut, true);
    }

    /** Lightweight cache key for display-referred 3D LUT generation without JSON or LUT I/O. */
    static String displayLookKey(Look look, RawArchive.Frame frame) {
        Look l = look != null ? look : new Look();
        boolean fluo = isFluorescentSpike(l, frame);
        float ambWarmth = computeAmbientWarmth(l, frame, fluo);
        return new StringBuilder(160)
                .append(l.brain).append('|')
                .append(l.whiteBalanceKelvin).append('|')
                .append(l.exposureEv).append('|')
                .append(l.warmth).append('|')
                .append(l.contrast).append('|')
                .append(l.pivot).append('|')
                .append(l.saturation).append('|')
                .append(l.colorRollOff).append('|')
                .append(l.highlights).append('|')
                .append(l.shadows).append('|')
                .append(l.highlightRecovery).append('|')
                .append(l.shadowProfile).append('|')
                .append(l.toneCurve).append('|')
                .append(l.easyMode).append('|')
                .append(l.skinProtection).append('|')
                .append(l.smartVibrance).append('|')
                .append(l.lutName).append('|')
                .append(l.lutIntensity).append('|')
                .append(l.filmPushPull).append('|')
                .append(Math.round(ambWarmth * 20f)).append('|')
                .append(fluo)
                .toString();
    }

    static int renderDisplayRgb(float r, float g, float b, PreparedLook prep) {
        float lr = srgbToLinear(r) * prep.exposureScale * prep.displayWbShift;
        float lg = srgbToLinear(g) * prep.exposureScale;
        float lb = srgbToLinear(b) * prep.exposureScale / prep.displayWbShift;
        float y = .2126f * lr + .7152f * lg + .0722f * lb;
        if (y > 0.55f) {
            // Display-referred Camera2/JPEG inputs have already been ISP-compressed below 1.0;
            // expand luminance smoothly above 0.55 so whites and bright highlights feed the shoulder cleanly.
            float yc = Math.min(y, 1.02f);
            float yExpanded = 0.55f + 0.51f * (yc - 0.55f) / Math.max(0.04f, 1.06f - yc);
            if (y > 1.02f) {
                yExpanded += (y - 1.02f) * 3.0f;
            }
            float scale = yExpanded / y;
            lr *= scale;
            lg *= scale;
            lb *= scale;
        }
        return renderPreparedLook(lr, lg, lb, prep, 0, 0, 0);
    }

    static float previewMidTarget(int[] pixels, int w, int h) {
        if (pixels == null || w <= 0 || h <= 0 || pixels.length < w * h) return 0f;
        float[] values = new float[w * h * 3];
        int count = 0;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int c = pixels[y * w + x];
            float luma = .2126f * srgbToLinear(((c >> 16) & 255) / 255f)
                    + .7152f * srgbToLinear(((c >> 8) & 255) / 255f)
                    + .0722f * srgbToLinear((c & 255) / 255f);
            if (luma >= .92f) continue;
            int weight = x >= w / 4 && x < w * 3 / 4 && y >= h / 4 && y < h * 3 / 4 ? 3 : 1;
            for (int i = 0; i < weight; i++) values[count++] = luma;
        }
        if (count < 16) return 0f;
        java.util.Arrays.sort(values, 0, count);
        return .65f * values[(int) ((count - 1) * .50f)] + .35f * values[(int) ((count - 1) * .70f)];
    }

    static float shadowRecoveryConfidence(int iso) {
        if (iso <= 400) return 1f;
        return clamp((float) Math.sqrt(400f / iso), .25f, 1f);
    }

    /** Grain is an output texture, added after sensor-noise cleanup and sharpening. */
    static void applyFilmGrain(int[] pixels, int w, int h, PreparedLook prep, int seed) {
        if (prep.grainAmount <= 0f) return;
        // Anchor grain to a 3000-pixel reference long edge, independent of output resolution.
        final float coordinateScale = 3000f / Math.max(w, h);
        final float invFine = coordinateScale / prep.grainScale;
        final float invCoarse = coordinateScale / prep.coarseDiv;
        final int seedCoarse = seed ^ 0x51ed270b;
        final float grainScaleAmt = prep.grainAmount * 13f;
        final float fineW = prep.fineWeight;
        final float coarseW = prep.coarseWeight;
        final boolean uniform = prep.uniformGrain;

        java.util.stream.IntStream.range(0, h).parallel().forEach(y -> {
            int rowOff = y * w;
            float fyFine = y * invFine;
            float fyCoarse = y * invCoarse;
            for (int x = 0; x < w; x++) {
                int c = pixels[rowOff + x];
                int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
                float lightness = (.2126f * r + .7152f * g + .0722f * b) * 0.003921569f;
                float fine = grainNoise(x * invFine, fyFine, seed);
                float coarse = grainNoise(x * invCoarse, fyCoarse, seedCoarse);
                float texture = (fine * fineW + coarse * coarseW)
                        * grainScaleAmt * smoothstep(.025f, .11f, lightness);
                if (!uniform) texture *= .38f + .62f * 4f * lightness * (1f - lightness);
                pixels[rowOff + x] = 0xff000000 | (clamp255(r + texture) << 16)
                        | (clamp255(g + texture) << 8) | clamp255(b + texture);
            }
        });
    }

    private static float grainNoise(float x, float y, int seed) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        float dx = x - ix, dy = y - iy;
        float top = hashNoise(ix, iy, seed) * (1f - dx) + hashNoise(ix + 1, iy, seed) * dx;
        float bottom = hashNoise(ix, iy + 1, seed) * (1f - dx) + hashNoise(ix + 1, iy + 1, seed) * dx;
        return top * (1f - dy) + bottom * dy;
    }

    private static void sampleLsm(RawArchive.Frame frame, int sx, int sy, float[] out) {
        float gx = clamp(sx / (float) Math.max(1, frame.width - 1), 0f, 1f) * (frame.lsmCols - 1);
        float gy = clamp(sy / (float) Math.max(1, frame.height - 1), 0f, 1f) * (frame.lsmRows - 1);
        int x0 = (int) gx, y0 = (int) gy;
        int x1 = Math.min(frame.lsmCols - 1, x0 + 1);
        int y1 = Math.min(frame.lsmRows - 1, y0 + 1);
        float tx = gx - x0, ty = gy - y0;
        int i00 = (y0 * frame.lsmCols + x0) * 4;
        int i10 = (y0 * frame.lsmCols + x1) * 4;
        int i01 = (y1 * frame.lsmCols + x0) * 4;
        int i11 = (y1 * frame.lsmCols + x1) * 4;
        for (int c = 0; c < 4; c++) {
            float top = frame.lsmGains[i00 + c] * (1f - tx) + frame.lsmGains[i10 + c] * tx;
            float bot = frame.lsmGains[i01 + c] * (1f - tx) + frame.lsmGains[i11 + c] * tx;
            out[c] = clamp(top * (1f - ty) + bot * ty, 0.5f, 3.5f);
        }
    }

    private static int[] borderPadding(int w, int h, Look look) {
        String border = look.frameBorder;
        boolean mediumFormat = border.contains("Medium Format");
        boolean cinemascope = border.contains("Cinemascope");
        int padSide, padTop, padBottom;
        if (mediumFormat) {
            padSide = Math.max(26, Math.round(Math.min(w, h) * 0.075f));
            padTop = padSide;
            padBottom = Math.max(34, Math.round(Math.min(w, h) * 0.095f));
        } else if (cinemascope) {
            padSide = Math.max(16, Math.round(w * 0.025f));
            float currentAspect = w / (float) Math.max(1, h);
            int desiredTotalH = currentAspect < 2.35f
                    ? Math.round(w / 2.39f)
                    : Math.round(h * 1.14f);
            int extraH = Math.max(dpToPx(48, Math.min(w, h)), desiredTotalH - h);
            padTop = extraH / 2;
            padBottom = extraH - padTop;
        } else {
            padSide = Math.max(16, Math.round(Math.min(w, h) * 0.045f));
            padTop = padSide;
            padBottom = Math.max(36, Math.round(Math.min(w, h) * 0.115f));
        }
        return new int[]{padSide, padTop, padBottom};
    }

    static Bitmap applyFrameBorder(Bitmap src, Look look, RawArchive.Frame frame) {
        if (src == null || look == null || look.frameBorder == null || look.frameBorder.startsWith("None")) return src;
        int w = src.getWidth(), h = src.getHeight();
        String border = look.frameBorder;
        boolean museum = border.startsWith("Museum");
        boolean mediumFormat = border.contains("Medium Format");
        boolean cinemascope = border.contains("Cinemascope");

        int[] pad = borderPadding(w, h, look);
        int padSide = pad[0], padTop = pad[1], padBottom = pad[2];

        int outW = w + padSide * 2;
        int outH = h + padTop + padBottom;
        Bitmap bordered = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && src.getColorSpace() != null) {
            try {
                bordered = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888, false, src.getColorSpace());
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (bordered == null) {
            bordered = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888);
        }
        Canvas canvas = new Canvas(bordered);
        int bgColor = museum ? 0xFFF7F5EF : cinemascope ? 0xFF000000 : mediumFormat ? 0xFF060708 : 0xFF0A0C0D;
        canvas.drawColor(bgColor);
        canvas.drawBitmap(src, padSide, padTop, null);

        Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(Math.max(1f, Math.min(w, h) * 0.002f));
        linePaint.setColor(museum ? 0x331A1D1E : cinemascope ? 0x22FFFFFF : 0x55D7B574);
        canvas.drawRect(padSide, padTop, padSide + w, padTop + h, linePaint);

        if (mediumFormat) {
            // Authentic Hasselblad film gate notches: two characteristic semicircular magazine cutouts along the left edge
            Paint notchPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            notchPaint.setColor(bgColor);
            notchPaint.setStyle(Paint.Style.FILL);
            float notchR = Math.max(5f, padSide * 0.28f);
            float n1Y = padTop + h * 0.33f;
            float n2Y = padTop + h * 0.67f;
            canvas.drawCircle(padSide, n1Y, notchR, notchPaint);
            canvas.drawCircle(padSide, n2Y, notchR, notchPaint);
        }

        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        float textSize = Math.max(10f, Math.min(padBottom, padTop) * 0.32f);
        textPaint.setTextSize(textSize);
        textPaint.setLetterSpacing(0.14f);
        textPaint.setColor(museum ? 0xFF1F2324 : cinemascope ? 0xFFECEEEA : 0xFFD7B574);

        String brainName = look.brain == null ? "LIKA / FILM" : look.brain;
        String meta = brainName;
        if (frame != null) {
            String shutter = frame.exposureTimeNs > 0
                    ? (frame.exposureTimeNs < 1_000_000_000L
                        ? String.format(Locale.US, "1/%ds", Math.max(1, Math.round(1_000_000_000.0 / frame.exposureTimeNs)))
                        : String.format(Locale.US, "%.1fs", frame.exposureTimeNs / 1_000_000_000.0))
                    : "";
            String iso = frame.sensitivityIso > 0 ? ("ISO " + frame.sensitivityIso) : "";
            if (!shutter.isEmpty()) meta += "  ·  " + shutter;
            if (!iso.isEmpty()) meta += "  ·  " + iso;
        }

        if (mediumFormat) {
            // Medium Format 120 edge markings
            Paint topTextPaint = new Paint(textPaint);
            topTextPaint.setColor(0x99D7B574);
            topTextPaint.setTextSize(textSize * 0.90f);
            String topTag = "▲ 120 FILM  ·  HASSELBLAD 500 C/M  ·  PORTRA 400";
            canvas.drawText(topTag, padSide, padTop * 0.62f, topTextPaint);

            float textY = padTop + h + (padBottom * 0.60f);
            String bottomTag = "● 12 ●  " + meta;
            canvas.drawText(bottomTag, padSide, textY, textPaint);
        } else if (cinemascope) {
            // Cinemascope 2.39:1 slate markings
            float textY = padTop + h + (padBottom * 0.62f);
            String slate = "SCENE A · TAKE 1 · 24 FPS · 180° SHUTTER";
            canvas.drawText(slate, padSide, textY, textPaint);
            String rightCinema = "LIKA CINEMASCOPE · " + meta;
            float rw = textPaint.measureText(rightCinema);
            if (padSide + rw < outW - padSide - 16) {
                Paint subPaint = new Paint(textPaint);
                subPaint.setColor(0x88FFFFFF);
                canvas.drawText(rightCinema, outW - padSide - rw, textY, subPaint);
            }
        } else {
            // 35mm film rebate or Museum matte
            float textY = padTop + h + (padBottom * 0.58f);
            canvas.drawText(meta, padSide, textY, textPaint);

            String rightTag = museum ? "LIKA LENS ARCHIVE" : "► 500T  ·  KODAK SAFETY FILM";
            float rightWidth = textPaint.measureText(rightTag);
            if (padSide + rightWidth < outW - padSide - textPaint.measureText(meta) - 24) {
                textPaint.setColor(museum ? 0xFF767D79 : 0x99D7B574);
                canvas.drawText(rightTag, outW - padSide - rightWidth, textY, textPaint);
            }
        }
        src.recycle();
        return bordered;
    }

    private static int dpToPx(int dp, int baseDim) {
        return Math.max(16, Math.round(dp * (baseDim / 1000f)));
    }

    /** Fast downscaled 2x2 Bayer quad sampler: reads each quad directly, averaging G1+G2 for zero aliasing. */
    private static void sampleQuad(short[] raw, RawArchive.Frame frame, float white,
                                   int sx, int sy, float[] rgbOut) {
        int qx = Math.min(frame.width - 2, sx & ~1);
        int qy = Math.min(frame.height - 2, sy & ~1);
        if (qx < 0) qx = 0;
        if (qy < 0) qy = 0;
        float v00 = rawValue(raw, frame, white, qx, qy);
        float v10 = rawValue(raw, frame, white, qx + 1, qy);
        float v01 = rawValue(raw, frame, white, qx, qy + 1);
        float v11 = rawValue(raw, frame, white, qx + 1, qy + 1);
        switch (frame.cfa) {
            case CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GRBG:
                rgbOut[0] = v10; rgbOut[1] = 0.5f * (v00 + v11); rgbOut[2] = v01; break;
            case CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GBRG:
                rgbOut[0] = v01; rgbOut[1] = 0.5f * (v00 + v11); rgbOut[2] = v10; break;
            case CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_BGGR:
                rgbOut[0] = v11; rgbOut[1] = 0.5f * (v10 + v01); rgbOut[2] = v00; break;
            default: // RGGB
                rgbOut[0] = v00; rgbOut[1] = 0.5f * (v10 + v01); rgbOut[2] = v11; break;
        }
    }

    /** Single-pass edge-directed color-difference Bayer demosaicing inside a 3x3 window. */
    private static void demosaicPixel(short[] raw, RawArchive.Frame frame, float white,
                                      int x, int y, float[] rgbOut) {
        int w = frame.width, h = frame.height;
        int xm = x > 0 ? x - 1 : 0;
        int xp = x + 1 < w ? x + 1 : w - 1;
        int ym = y > 0 ? y - 1 : 0;
        int yp = y + 1 < h ? y + 1 : h - 1;
        float c00 = rawValue(raw, frame, white, xm, ym);
        float c10 = rawValue(raw, frame, white, x, ym);
        float c20 = rawValue(raw, frame, white, xp, ym);
        float c01 = rawValue(raw, frame, white, xm, y);
        float c11 = rawValue(raw, frame, white, x, y);
        float c21 = rawValue(raw, frame, white, xp, y);
        float c02 = rawValue(raw, frame, white, xm, yp);
        float c12 = rawValue(raw, frame, white, x, yp);
        float c22 = rawValue(raw, frame, white, xp, yp);

        int site = colorAt(x, y, frame.cfa);
        if (site == 1) {
            float g = c11;
            float gh = Math.max(0f, g + 0.5f * ((c01 - 0.5f * (c00 + c02)) + (c21 - 0.5f * (c20 + c22))));
            float gv = Math.max(0f, g + 0.5f * ((c10 - 0.5f * (c00 + c20)) + (c12 - 0.5f * (c02 + c22))));
            boolean horizIsRed = colorAt(x + 1 < w ? x + 1 : x - 1, y, frame.cfa) == 0;
            rgbOut[0] = horizIsRed ? gh : gv;
            rgbOut[1] = g;
            rgbOut[2] = horizIsRed ? gv : gh;
        } else {
            float dh = Math.abs(c01 - c21);
            float dv = Math.abs(c10 - c12);
            float wh = 1f / (1f + dh * dh * 256f);
            float wv = 1f / (1f + dv * dv * 256f);
            float g = (wh * 0.5f * (c01 + c21) + wv * 0.5f * (c10 + c12)) / (wh + wv);
            float opp = Math.max(0f, g + 0.25f * (c00 + c20 + c02 + c22) - 0.25f * (c01 + c21 + c10 + c12));
            if (site == 0) {
                rgbOut[0] = c11;
                rgbOut[1] = g;
                rgbOut[2] = opp;
            } else {
                rgbOut[0] = opp;
                rgbOut[1] = g;
                rgbOut[2] = c11;
            }
        }
    }

    private static float rawValue(short[] raw, RawArchive.Frame frame, float white, int x, int y) {
        int sample = raw[y * frame.width + x] & 0xffff;
        float black = frame.blackLevels[(y & 1) * 2 + (x & 1)];
        float norm = (sample - black) / Math.max(1f, white - black);
        // Subtract a small read-noise floor around black level when real black levels are present
        if (black > 0.5f) {
            norm = (norm - 0.0012f) / 0.9988f;
        }
        return clamp(norm, 0f, 4f);
    }

    private static float[] whiteBalanceGains(RawArchive.Frame frame, int kelvin) {
        float[] gains = frame.wbGains == null ? new float[]{1f, 1f, 1f, 1f} : frame.wbGains.clone();
        if (kelvin > 0) {
            float ratio = clamp(kelvin / 5600f, .55f, 1.45f);
            float tintShift = (float) Math.pow(ratio, .5);
            gains[0] *= tintShift;
            gains[3] /= tintShift;
        }
        float green = Math.max(.01f, (gains[1] + gains[2]) * .5f);
        float normal = 1f / green;
        for (int i = 0; i < gains.length; i++) gains[i] = clamp(gains[i] * normal, .25f, 4f);
        return gains;
    }

    private static float[] profileFor(RawArchive.Frame frame, int kelvin) {
        float[] first = frame.sensorToXyz1;
        float[] second = frame.sensorToXyz2;
        if (first == null && second == null) {
            return new float[]{1.75f,-.48f,-.20f, -.22f,1.42f,-.20f, .03f,-.53f,1.50f};
        }
        if (first == null) return second;
        if (second == null || frame.illuminant1 == 0 || frame.illuminant2 == 0 || frame.illuminant1 == frame.illuminant2) return first;
        float a = illuminantKelvin(frame.illuminant1), b = illuminantKelvin(frame.illuminant2);
        float t;
        if (kelvin > 0) {
            t = clamp((kelvin - a) / (b - a), 0f, 1f);
        } else if (frame.neutralPoint != null && frame.neutralPoint.length >= 3
                && (frame.neutralPoint[0] + frame.neutralPoint[1] + frame.neutralPoint[2]) > .0001f) {
            float rNorm = frame.neutralPoint[0] / Math.max(0.0001f, frame.neutralPoint[1]);
            float bNorm = frame.neutralPoint[2] / Math.max(0.0001f, frame.neutralPoint[1]);
            float estimatedKelvin = 5500f * (float) Math.pow(bNorm / Math.max(0.001f, rNorm), 0.65f);
            t = clamp((estimatedKelvin - a) / Math.max(1f, b - a), 0f, 1f);
        } else {
            t = clamp((5600f - a) / (b - a), 0f, 1f);
        }
        return blend(first, second, t);
    }

    private static float[] blend(float[] first, float[] second, float t) {
        float[] blend = new float[9];
        for (int i = 0; i < 9; i++) blend[i] = first[i] * (1f - t) + second[i] * t;
        return blend;
    }

    private static int renderPixelBudget(RawArchive.Frame frame) {
        long heap = Runtime.getRuntime().maxMemory();
        long rawBytes = frame.rawSamples == null ? 0L : frame.rawSamples.length;
        long workingBytes = Math.max(0L, (long) (heap * .75d) - rawBytes - 32L * 1024L * 1024L);
        long pixels = workingBytes / 5L;
        return (int) Math.max(1_000_000L, Math.min(MAX_RENDER_PIXELS, pixels));
    }

    private static float illuminantKelvin(int illuminant) {
        switch (illuminant) {
            case 17: return 2856f;
            case 18: return 4874f;
            case 19: return 6774f;
            case 20: return 5500f;
            case 21: return 5500f;
            case 22: return 6500f;
            case 23: return 7500f;
            case 24: return 5000f;
            case 3: return 3200f;
            case 4: return 4000f;
            case 10: return 6500f;
            case 11: return 7500f;
            default: return 5600f;
        }
    }

    private static int colorAt(int x, int y, int cfa) {
        int parity = ((y & 1) << 1) | (x & 1);
        switch (cfa) {
            case CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GRBG:
                return parity == 0 || parity == 3 ? 1 : parity == 1 ? 0 : 2;
            case CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GBRG:
                return parity == 0 || parity == 3 ? 1 : parity == 1 ? 2 : 0;
            case CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_BGGR:
                return parity == 0 ? 2 : parity == 3 ? 0 : 1;
            default:
                return parity == 0 ? 0 : parity == 3 ? 2 : 1;
        }
    }

    /**
     * Computes scene-linear exposure normalization for RAW frames.
     * Applies Camera2 CONTROL_POST_RAW_SENSITIVITY_BOOST and, when Easy Mode is active,
     * uses center-weighted metering to compensate for highlight-protecting RAW exposure so
     * subjects match viewfinder perception while highlights roll off through the filmic shoulder.
     */
    static float computeSceneExposureGain(short[] rawShorts, RawArchive.Frame frame, float white,
                                          float[] gains, float[] cameraToXyz, float[] crop,
                                          int orientation, boolean applyLsm, Look look) {
        if (frame == null) return 1.0f;
        float boostScale = frame.postRawSensitivityBoost > 100
                ? clamp(frame.postRawSensitivityBoost / 100f, 1.0f, 8.0f)
                : 1.0f;
        if (look == null || !look.easyMode || frame.manualExposure || rawShorts == null || frame.width <= 0 || frame.height <= 0) {
            return boostScale;
        }
        int width = frame.width, height = frame.height;
        float cropX = crop != null && crop.length >= 4 ? crop[0] : 0f;
        float cropY = crop != null && crop.length >= 4 ? crop[1] : 0f;
        float cropW = crop != null && crop.length >= 4 ? crop[2] : width;
        float cropH = crop != null && crop.length >= 4 ? crop[3] : height;
        int gridX = 32, gridY = 24;
        float[] allLuma = new float[gridX * gridY * 3];
        float[] midLuma = new float[gridX * gridY * 3];
        int allCount = 0, midCount = 0;
        boolean hasCct = frame.colorCorrectionTransform != null && frame.colorCorrectionTransform.length >= 9;
        float[] cct = frame.colorCorrectionTransform;
        float[] rgb = new float[3];
        float[] lsm = applyLsm ? new float[4] : null;
        boolean useQuad = width >= 2 && height >= 2;
        float g0 = gains[0] * boostScale, gg = (gains[1] + gains[2]) * 0.5f * boostScale, g3 = gains[3] * boostScale;

        for (int gy = 0; gy < gridY; gy++) {
            float dy = Math.min(cropY + cropH - .5f, cropY + (gy + .5f) * cropH / gridY);
            boolean centerY = (gy >= gridY / 4 && gy < (gridY * 3) / 4);
            for (int gx = 0; gx < gridX; gx++) {
                float dx = Math.min(cropX + cropW - .5f, cropX + (gx + .5f) * cropW / gridX);
                boolean centerX = (gx >= gridX / 4 && gx < (gridX * 3) / 4);
                int weight = (centerX && centerY) ? 3 : 1;
                int sx, sy;
                switch (orientation) {
                    case 90: sx = Math.min(width - 1, (int) dy); sy = Math.max(0, Math.min(height - 1, height - 1 - (int) dx)); break;
                    case 180: sx = Math.max(0, Math.min(width - 1, width - 1 - (int) dx)); sy = Math.max(0, Math.min(height - 1, height - 1 - (int) dy)); break;
                    case 270: sx = Math.max(0, Math.min(width - 1, width - 1 - (int) dy)); sy = Math.min(height - 1, (int) dx); break;
                    default: sx = Math.min(width - 1, (int) dx); sy = Math.min(height - 1, (int) dy); break;
                }
                if (useQuad) sampleQuad(rawShorts, frame, white, sx, sy, rgb);
                else demosaicPixel(rawShorts, frame, white, sx, sy, rgb);
                float r = rgb[0], g = rgb[1], b = rgb[2];
                if (applyLsm) {
                    sampleLsm(frame, sx, sy, lsm);
                    float sig = Math.max(r, Math.max(g, b));
                    float lsmWeight = smoothstep(0.003f, 0.030f, sig);
                    r *= 1f + (lsm[0] - 1f) * lsmWeight;
                    g *= 1f + ((lsm[1] + lsm[2]) * 0.5f - 1f) * lsmWeight;
                    b *= 1f + (lsm[3] - 1f) * lsmWeight;
                }
                r *= g0;
                g *= gg;
                b *= g3;

                float lr, lg, lb;
                if (hasCct) {
                    lr = cct[0] * r + cct[1] * g + cct[2] * b;
                    lg = cct[3] * r + cct[4] * g + cct[5] * b;
                    lb = cct[6] * r + cct[7] * g + cct[8] * b;
                } else {
                    float x = cameraToXyz[0] * r + cameraToXyz[1] * g + cameraToXyz[2] * b;
                    float y = cameraToXyz[3] * r + cameraToXyz[4] * g + cameraToXyz[5] * b;
                    float z = cameraToXyz[6] * r + cameraToXyz[7] * g + cameraToXyz[8] * b;
                    lr = 3.1339f * x - 1.6169f * y - .4906f * z;
                    lg = -.9785f * x + 1.9160f * y + .0333f * z;
                    lb = .0720f * x - .2290f * y + 1.4057f * z;
                }
                float luma = Math.max(0f, .2126f * Math.max(0f, lr) + .7152f * Math.max(0f, lg) + .0722f * Math.max(0f, lb));
                for (int w = 0; w < weight; w++) {
                    allLuma[allCount++] = luma;
                    if (luma >= 0f && luma < 0.92f) {
                        midLuma[midCount++] = luma;
                    }
                }
            }
        }
        if (midCount < 16 || allCount < 16) return boostScale;
        java.util.Arrays.sort(allLuma, 0, allCount);
        java.util.Arrays.sort(midLuma, 0, midCount);
        float p50 = midLuma[(int) ((midCount - 1) * 0.50f)];
        float p70 = midLuma[(int) ((midCount - 1) * 0.70f)];
        float p96 = allLuma[(int) ((allCount - 1) * 0.96f)];
        float midMetric = 0.65f * p50 + 0.35f * p70;
        // Respect capture compensation and noise instead of lifting every dark scene to daylight.
        // The pre-look preview already includes Camera2 exposure compensation, so do not add it twice.
        float targetMid = frame.previewMidTarget > 0f ? clamp(frame.previewMidTarget, .01f, .50f)
                : 0.18f * (float) Math.pow(2.0, clamp(frame.aeCompensationEv, -3f, 2f));
        if (midMetric >= targetMid) return boostScale;
        float neededGain = (float) Math.pow(targetMid / Math.max(0.004f, midMetric), 0.88);
        float isoConfidence = shadowRecoveryConfidence(frame.sensitivityIso);
        float maxGain = 1.5f + 1.5f * isoConfidence;
        float maxHighlightGain = p96 > 0.40f ? Math.max(1f, 2.8f / p96) : maxGain;
        float signalConfidence = smoothstep(.002f, .008f, p50 / boostScale);
        float sceneGain = 1f + (clamp(Math.min(neededGain, maxHighlightGain), 1f, maxGain) - 1f) * signalConfidence;
        return boostScale * sceneGain;
    }

    /**
     * Builds a smoothed coarse spatial local-luminance gain map so Easy Mode recovers dark
     * indoor shadows to match Pixel HDR+ / viewfinder latitude while keeping bright highlights clean.
     */
    static float[] buildLocalToneGainGrid(
            short[] rawShorts, RawArchive.Frame frame, float white, float[] gains,
            float[] cameraToXyz, boolean hasCct, float[] cct, float exposureScale,
            float[] crop, float cropWidth, float cropHeight, int orientation,
            boolean applyLsm, boolean flipH, int outW, int outH, int lgW, int lgH, int brainId) {
        if (lgW <= 0 || lgH <= 0 || rawShorts == null || frame == null) return null;
        int width = frame.width, height = frame.height;
        float[] logY = new float[lgW * lgH];
        float[] rgb = new float[3];
        float[] lsm = applyLsm ? new float[4] : null;
        float g0 = gains[0] * exposureScale, gg = (gains[1] + gains[2]) * 0.5f * exposureScale, g3 = gains[3] * exposureScale;
        for (int gy = 0; gy < lgH; gy++) {
            float oy = (gy + 0.5f) * outH / lgH;
            float dy = Math.min(crop[1] + cropHeight - .5f, crop[1] + (oy + .5f) * cropHeight / outH);
            int rowOff = gy * lgW;
            for (int gx = 0; gx < lgW; gx++) {
                float ox = (gx + 0.5f) * outW / lgW;
                float effectiveOx = flipH ? (outW - 1 - ox) : ox;
                float dx = Math.min(crop[0] + cropWidth - .5f, crop[0] + (effectiveOx + .5f) * cropWidth / outW);
                int sx, sy;
                switch (orientation) {
                    case 90: sx = Math.min(width - 1, (int) dy); sy = Math.max(0, Math.min(height - 1, height - 1 - (int) dx)); break;
                    case 180: sx = Math.max(0, Math.min(width - 1, width - 1 - (int) dx)); sy = Math.max(0, Math.min(height - 1, height - 1 - (int) dy)); break;
                    case 270: sx = Math.max(0, Math.min(width - 1, width - 1 - (int) dy)); sy = Math.min(height - 1, (int) dx); break;
                    default: sx = Math.min(width - 1, (int) dx); sy = Math.min(height - 1, (int) dy); break;
                }
                demosaicPixel(rawShorts, frame, white, sx, sy, rgb);
                float r = rgb[0], g = rgb[1], b = rgb[2];
                if (applyLsm) {
                    sampleLsm(frame, sx, sy, lsm);
                    float sig = Math.max(r, Math.max(g, b));
                    float lsmWeight = smoothstep(0.003f, 0.030f, sig);
                    r *= 1f + (lsm[0] - 1f) * lsmWeight;
                    g *= 1f + ((lsm[1] + lsm[2]) * 0.5f - 1f) * lsmWeight;
                    b *= 1f + (lsm[3] - 1f) * lsmWeight;
                }
                r *= g0;
                g *= gg;
                b *= g3;

                float lr, lg, lb;
                if (hasCct) {
                    lr = cct[0] * r + cct[1] * g + cct[2] * b;
                    lg = cct[3] * r + cct[4] * g + cct[5] * b;
                    lb = cct[6] * r + cct[7] * g + cct[8] * b;
                } else {
                    float x = cameraToXyz[0] * r + cameraToXyz[1] * g + cameraToXyz[2] * b;
                    float y = cameraToXyz[3] * r + cameraToXyz[4] * g + cameraToXyz[5] * b;
                    float z = cameraToXyz[6] * r + cameraToXyz[7] * g + cameraToXyz[8] * b;
                    lr = 3.1339f * x - 1.6169f * y - .4906f * z;
                    lg = -.9785f * x + 1.9160f * y + .0333f * z;
                    lb = .0720f * x - .2290f * y + 1.4057f * z;
                }
                float luma = Math.max(0f, .2126f * Math.max(0f, lr) + .7152f * Math.max(0f, lg) + .0722f * Math.max(0f, lb));
                logY[rowOff + gx] = (float) Math.log(luma + 0.005f);
            }
        }
        return finishLocalToneGrid(logY, lgW, lgH, frame, exposureScale, brainId);
    }

    private static float[] buildLocalToneGainGridFromLinear(
            float[] baseRgb, int outW, int outH, float evScale, float exposureScale,
            RawArchive.Frame frame, int lgW, int lgH, int brainId) {
        if (lgW <= 0 || lgH <= 0 || baseRgb == null || frame == null) return null;
        float[] logY = new float[lgW * lgH];
        for (int gy = 0; gy < lgH; gy++) {
            int oy = Math.min(outH - 1, (int) ((gy + 0.5f) * outH / lgH));
            int rowOff = gy * lgW;
            int srcRow = oy * outW;
            for (int gx = 0; gx < lgW; gx++) {
                int ox = Math.min(outW - 1, (int) ((gx + 0.5f) * outW / lgW));
                int idx = (srcRow + ox) * 3;
                float lr = baseRgb[idx] * evScale;
                float lg = baseRgb[idx + 1] * evScale;
                float lb = baseRgb[idx + 2] * evScale;
                float luma = Math.max(0f, .2126f * Math.max(0f, lr) + .7152f * Math.max(0f, lg) + .0722f * Math.max(0f, lb));
                logY[rowOff + gx] = (float) Math.log(luma + 0.005f);
            }
        }
        return finishLocalToneGrid(logY, lgW, lgH, frame, exposureScale, brainId);
    }

    private static float[] finishLocalToneGrid(float[] logY, int lgW, int lgH,
                                               RawArchive.Frame frame, float exposureScale, int brainId) {
        float[] tmp = new float[logY.length];
        blur(logY, tmp, lgW, lgH, true);
        blur(tmp, logY, lgW, lgH, false);
        blur(logY, tmp, lgW, lgH, true);
        blur(tmp, logY, lgW, lgH, false);

        float brainRecoveryScale = brainId == BRAIN_ID_BLEACH ? 0.45f
                : brainId == BRAIN_ID_KODACHROME ? 0.70f : 1.0f;
        float[] gainGrid = new float[logY.length];
        float exposureIntent = (float) Math.pow(2.0, Math.min(0f, frame.aeCompensationEv));
        float shadowConf = shadowRecoveryConfidence(frame.sensitivityIso);
        float invExp = 1f / Math.max(1f, exposureScale);
        for (int i = 0; i < logY.length; i++) {
            float avgY = Math.max(0f, (float) Math.exp(logY[i]) - 0.005f);
            if (avgY > 0.42f) {
                float excess = avgY - 0.42f;
                float compressed = 0.42f + excess / (1.0f + excess * 1.10f);
                gainGrid[i] = compressed / avgY;
            } else {
                float shadowNeed = smoothstep(0.20f, 0.015f, avgY);
                float signalConfidence = smoothstep(.002f, .008f, avgY * invExp);
                gainGrid[i] = 1.0f + .85f * brainRecoveryScale * shadowNeed * signalConfidence
                        * shadowConf * exposureIntent;
            }
        }
        return gainGrid;
    }

    private static float sampleLocalGain(float[] grid, int lgW, int lgH, int ox, int oy, int outW, int outH) {
        float gx = clamp((ox + 0.5f) / Math.max(1f, outW) * lgW - 0.5f, 0f, Math.max(0f, lgW - 1f));
        float gy = clamp((oy + 0.5f) / Math.max(1f, outH) * lgH - 0.5f, 0f, Math.max(0f, lgH - 1f));
        int x0 = (int) gx, y0 = (int) gy;
        int x1 = Math.min(lgW - 1, x0 + 1);
        int y1 = Math.min(lgH - 1, y0 + 1);
        float tx = gx - x0, ty = gy - y0;
        float v00 = grid[y0 * lgW + x0];
        float v10 = grid[y0 * lgW + x1];
        float v01 = grid[y1 * lgW + x0];
        float v11 = grid[y1 * lgW + x1];
        float top = v00 * (1f - tx) + v10 * tx;
        float bot = v01 * (1f - tx) + v11 * tx;
        return top * (1f - ty) + bot * ty;
    }

    /** A coupled display transform: tonal response, hue, chroma and gamut are shaped together. */
    static int renderLook(float r, float g, float b, Look look, int x, int y, int seed) {
        return renderLook(r, g, b, look, x, y, seed, null, null);
    }

    static int renderLook(float r, float g, float b, Look look, int x, int y, int seed, RawArchive.Frame frame) {
        return renderLook(r, g, b, look, x, y, seed, frame, null);
    }

    static int renderLook(float r, float g, float b, Look look, int x, int y, int seed, RawArchive.Frame frame, LutManager.Lut3D lut) {
        return renderPreparedLook(r, g, b, new PreparedLook(look, frame, lut), x, y, seed);
    }

    static int renderPreparedLook(float r, float g, float b, PreparedLook prep, int x, int y, int seed) {
        // Clamp negative off-diagonal matrix excursions before computing luminance so near-black
        // read noise never produces an artificially tiny denominator in `contrasted / oldY`.
        r = Math.max(0f, r);
        g = Math.max(0f, g);
        b = Math.max(0f, b);

        final int brainId = prep.brainId;
        if (brainId == BRAIN_ID_MONO) {
            // Panchromatic silver halide spectral response (Kodak Tri-X 400):
            // Elevated red/orange sensitivity renders skin tones smooth and luminous while
            // deepening blue skies for natural cloud separation.
            float luma = .3300f * r + .5900f * g + .0800f * b;
            r = g = b = luma;
        } else if (brainId == BRAIN_ID_LIKA_FILM) {
            // Kodak Vision3 500T dye crosstalk: cinema reds and clean tungsten-balanced shadows
            float fr = 1.09f * r - 0.06f * g - 0.03f * b;
            float fg = -0.02f * r + 1.01f * g + 0.01f * b;
            float fb = -0.04f * r + 0.05f * g + 0.99f * b;
            r = Math.max(0f, fr); g = Math.max(0f, fg); b = Math.max(0f, fb);
        } else if (brainId == BRAIN_ID_KODACHROME) {
            // K-14 Color Reversal: punchy crimson reds, clean golds, and deep cobalt blues
            float fr = 1.12f * r - 0.09f * g - 0.03f * b;
            float fg = 0.02f * r + 0.95f * g + 0.03f * b;
            float fb = -0.05f * r + 0.02f * g + 1.03f * b;
            r = Math.max(0f, fr); g = Math.max(0f, fg); b = Math.max(0f, fb);
        } else if (brainId == BRAIN_ID_FUJI_ETERNA) {
            // Fuji Eterna 500T: restrained reds with cool celadon/emerald green-cyan sensitivity
            float fr = 0.94f * r + 0.05f * g + 0.01f * b;
            float fg = -0.03f * r + 1.02f * g + 0.01f * b;
            float fb = -0.02f * r + 0.05f * g + 0.97f * b;
            r = Math.max(0f, fr); g = Math.max(0f, fg); b = Math.max(0f, fb);
        } else if (brainId == BRAIN_ID_BLEACH) {
            // ENR Skip-Bleach silver-retained dye profile
            float fr = 1.06f * r - 0.05f * g - 0.01f * b;
            float fg = 0.02f * r + 0.97f * g + 0.01f * b;
            float fb = 0.05f * g + 0.95f * b;
            r = Math.max(0f, fr); g = Math.max(0f, fg); b = Math.max(0f, fb);
        }

        float oldY = Math.max(.0001f, .2126f * r + .7152f * g + .0722f * b);

        float contrasted;
        if (prep.curveId == CURVE_LINEAR) {
            contrasted = oldY;
        } else if (prep.curveId == CURVE_LOG) {
            float logY = (float) Math.log1p(oldY * 7.0f) * INV_LOG1P_7;
            contrasted = logY <= 0f ? 0f : prep.logPivot * (float) Math.pow(logY * prep.invSafeLogPivot, prep.logContrast);
        } else {
            // Filmic S-Curve: power-law toe around pivot preserves shadow gradation down to true black without exploding read noise
            float gamma = oldY < prep.safePivot ? prep.toeContrast : prep.safeContrast;
            contrasted = oldY <= 0f ? 0f : prep.safePivot * (float) Math.pow(oldY / prep.safePivot, gamma);
        }

        float shadowWeight = 1f - smoothstep(.02f, .44f, contrasted);
        if (prep.liftedShadows) {
            float lift = 0.018f * smoothstep(0.002f, 0.022f, oldY);
            contrasted = lift + contrasted * (1.0f - 0.5f * lift);
        }
        contrasted *= 1f - prep.shadowDepth * .22f * shadowWeight;

        if (contrasted > prep.knee) {
            float excess = contrasted - prep.knee;
            contrasted = prep.knee + prep.shoulderSpan * excess / (prep.shoulderSpan + excess);
        }
        float ratio = Math.min(4.0f, contrasted / oldY);
        r *= ratio; g *= ratio; b *= ratio;

        r = Math.max(0f, r); g = Math.max(0f, g); b = Math.max(0f, b);
        float lRoot = fastCbrt(.41222147f*r + .53633254f*g + .05144599f*b);
        float mRoot = fastCbrt(.21190350f*r + .68069955f*g + .10739696f*b);
        float sRoot = fastCbrt(.08830246f*r + .28171884f*g + .62997870f*b);
        float lightness = clamp(.21045426f*lRoot + .79361779f*mRoot - .00407205f*sRoot, 0f, 1.25f);
        float a = 1.97799850f*lRoot - 2.42859221f*mRoot + .45059371f*sRoot;
        float bb = .02590404f*lRoot + .78277177f*mRoot - .80867577f*sRoot;
        float highlightWeight = smoothstep(.62f, 1f, lightness);
        shadowWeight = 1f - smoothstep(.12f, .50f, lightness);

        float highlightDesat;
        if (brainId == BRAIN_ID_NEUTRAL) {
            highlightDesat = 1f - .20f * smoothstep(.72f, 1.0f, lightness);
        } else if (prep.hlRecId == HL_SUBTRACTIVE) {
            highlightDesat = 1f - .46f * smoothstep(.60f, 0.98f, lightness);
        } else if (prep.hlRecId == HL_HARD) {
            highlightDesat = 1.0f;
        } else {
            highlightDesat = 1f - .35f * smoothstep(.70f, 1.0f, lightness);
        }

        float satBase = prep.satBase;
        float rawChromaSq = a * a + bb * bb;
        float rawChroma = rawChromaSq > 1e-8f ? (float) Math.sqrt(rawChromaSq) : 0f;

        // Detect skin tone hue angle in Oklab:
        // theta = atan2(bb, a). Skin tones cluster between ~0.45 and ~1.35 radians (~26° to 77°),
        // which lies strictly in Quadrant I (a > 0 && bb > 0) with tan(0.45)=0.483 <= bb/a <= tan(1.35)=4.455.
        float skinFactor = 0f;
        boolean needSkinFactor = (prep.applySmartVibrance && brainId != BRAIN_ID_MONO
                && brainId != BRAIN_ID_BLEACH && brainId != BRAIN_ID_NEUTRAL)
                || prep.applySkinProtection;
        if (needSkinFactor && rawChroma > 0.015f && a > 0f && bb > 0f) {
            float ratioBA = bb / a;
            if (ratioBA >= 0.483f && ratioBA <= 4.455f) {
                float hueAngle = (float) Math.atan2(bb, a);
                float d = Math.abs(hueAngle - 0.88f);
                skinFactor = smoothstep(0.45f, 0.05f, d);
            }
        }

        float vibranceBoost = 1.0f;
        if (prep.applySmartVibrance && brainId != BRAIN_ID_MONO && brainId != BRAIN_ID_BLEACH && brainId != BRAIN_ID_NEUTRAL) {
            // Boost moderate chroma tones without magnifying low-chroma tungsten wall tints
            float vibranceWeight = smoothstep(0.015f, 0.065f, rawChroma)
                    * (1.0f - skinFactor * 0.85f)
                    * (1.0f - smoothstep(0.12f, 0.35f, rawChroma));
            vibranceBoost += 0.18f * vibranceWeight;
        }

        if (prep.applySkinProtection && skinFactor > 0.05f) {
            // Keep skin saturation in an optimal, flattering corridor (avoiding sunburn or overly flushed skin)
            float maxSkinChroma = 0.145f;
            if (rawChroma * satBase > maxSkinChroma) {
                float excess = (rawChroma * satBase) - maxSkinChroma;
                satBase = (maxSkinChroma + excess * 0.30f) / Math.max(0.001f, rawChroma);
            }
        }

        // Suppress color read-noise speckles in deep near-black shadows (e.g. turned-off screens, dark corners)
        float shadowNoiseGate = smoothstep(0.0015f, 0.016f, oldY);
        float chromaScale = clamp(satBase * vibranceBoost, 0f, 1.8f) * (1f - .10f * highlightWeight) * highlightDesat * shadowNoiseGate;
        a *= chromaScale;
        bb *= chromaScale;
        float chroma = rawChroma * chromaScale;
        if (prep.rollOff > 0f && chroma > .13f) {
            float excess = chroma - .13f;
            float compressed = .13f + excess / (1f + excess * prep.rollOffSlope);
            float scale = compressed / chroma;
            a *= scale;
            bb *= scale;
        }

        float warmthGate = smoothstep(0.03f, 0.16f, lightness);
        float midWarmGate = smoothstep(0.08f, 0.30f, lightness);
        if (brainId == BRAIN_ID_MONO) {
            // Allow subtle warm-selenium / cool-silver paper toning on MONO / SILVER when warmth slider is shifted
            if (Math.abs(prep.look.warmth) > 0.01f) {
                float silverTone = clamp(prep.look.warmth, -1f, 1f) * warmthGate * (1f - 0.35f * highlightWeight);
                a += silverTone * 0.004f;
                bb += silverTone * 0.010f;
            }
        } else {
            // Attenuate synthetic film warmth when the pixel is already warm (e.g. tungsten lighting / warm skin)
            // and keep film warmth in midtones/highlights so dark shadows don't crush Blue into muddy yellow-green.
            float alreadyWarmAtten = 1.0f - 0.85f * smoothstep(0.012f, 0.065f, bb);
            float baseWarmth;
            if (brainId == BRAIN_ID_NEUTRAL) {
                baseWarmth = 0f;
            } else if (brainId == BRAIN_ID_KODACHROME) {
                baseWarmth = 0.28f * prep.ambientWarmthFactor * alreadyWarmAtten;
            } else if (brainId == BRAIN_ID_FUJI_ETERNA) {
                baseWarmth = -0.10f * prep.ambientWarmthFactor;
            } else if (brainId == BRAIN_ID_BLEACH) {
                baseWarmth = -0.10f;
            } else {
                baseWarmth = 0.22f * prep.ambientWarmthFactor * alreadyWarmAtten;
            }

            float filmWarm = clamp(baseWarmth, -1f, 1f) * midWarmGate;
            float userWarm = clamp(prep.look.warmth, -1f, 1f) * warmthGate;
            float warmth = clamp(filmWarm + userWarm, -1f, 1f);
            a += warmth * (.008f + .003f * highlightWeight);
            bb += warmth * (.010f + .004f * highlightWeight);

            // Illuminant-Adaptive Neutralization in Easy Mode:
            // Neutralizes the yellow-green jaundiced tint under fluorescent lighting
            if (prep.look.easyMode && prep.fluorescentSpike) {
                a += 0.0035f * warmthGate;
                bb -= 0.0070f * warmthGate;
            }

            // Authentic Film Stock Spectral Split-Toning & Hue Couplers:
            if (brainId == BRAIN_ID_LIKA_FILM) {
                // Kodak Vision3 500T: tungsten cool-teal shadow undertow with golden-amber highlight glow
                bb -= 0.009f * shadowWeight * warmthGate;
                a -= 0.003f * shadowWeight * warmthGate;
                a += 0.004f * highlightWeight;
                bb += 0.006f * highlightWeight;
            } else if (brainId == BRAIN_ID_KODACHROME) {
                // Kodachrome 64: rich crimson-gold couplers in midtones and deep cobalt skies in blues
                float midWeight = Math.max(0f, 1f - shadowWeight - highlightWeight);
                float warmSceneGate = prep.ambientWarmthFactor * alreadyWarmAtten;
                a += 0.010f * midWeight * midWarmGate * warmSceneGate;
                bb += 0.006f * midWeight * midWarmGate * warmSceneGate;
                if (bb < -0.01f) {
                    // Deepen cyan-blues slightly toward classic reversal cobalt
                    a += (-bb) * 0.10f * midWarmGate;
                }
            } else if (brainId == BRAIN_ID_FUJI_ETERNA) {
                // Fuji Eterna 500T: signature celadon/cyan shadow split-tone and emerald foliage greens
                bb -= 0.016f * shadowWeight * warmthGate;
                a -= 0.010f * shadowWeight * warmthGate;
                if (a < -0.005f && bb > 0.005f) {
                    // Rotate yellow-green foliage slightly toward Fuji celadon/emerald
                    float greenStrength = Math.min(-a, bb);
                    bb -= greenStrength * 0.25f;
                    a -= greenStrength * 0.08f;
                }
            } else if (brainId == BRAIN_ID_BLEACH) {
                // ENR Bleach Bypass: steely cyan-slate shadows and cool metallic highlights
                bb -= 0.014f * shadowWeight * warmthGate + 0.005f * highlightWeight;
                a -= 0.004f * shadowWeight * warmthGate;
            }
        }
        lightness = clamp(lightness, 0f, 1f);

        float lp = lightness + .39633778f*a + .21580376f*bb;
        float mp = lightness - .10556135f*a - .06385417f*bb;
        float sp = lightness - .08948418f*a - 1.29148555f*bb;
        lp *= lp * lp; mp *= mp * mp; sp *= sp * sp;
        float outR = 4.07674166f*lp - 3.30771159f*mp + .23096993f*sp;
        float outG = -1.26843800f*lp + 2.60975740f*mp - .34131940f*sp;
        float outB = -.00419609f*lp - .70341861f*mp + 1.70761470f*sp;
        for (int i = 0; i < 8 && (outR < 0f || outR > 1f || outG < 0f || outG > 1f || outB < 0f || outB > 1f); i++) {
            a *= .82f; bb *= .82f;
            lp = lightness + .39633778f*a + .21580376f*bb;
            mp = lightness - .10556135f*a - .06385417f*bb;
            sp = lightness - .08948418f*a - 1.29148555f*bb;
            lp *= lp * lp; mp *= mp * mp; sp *= sp * sp;
            outR = 4.07674166f*lp - 3.30771159f*mp + .23096993f*sp;
            outG = -1.26843800f*lp + 2.60975740f*mp - .34131940f*sp;
            outB = -.00419609f*lp - .70341861f*mp + 1.70761470f*sp;
        }

        if (prep.lut != null) {
            return prep.lut.applyToRgb(toSrgbFloat(outR), toSrgbFloat(outG), toSrgbFloat(outB), prep.lutIntensity);
        }
        return (0xFF << 24) | ((toSrgb(outR) & 0xFF) << 16) | ((toSrgb(outG) & 0xFF) << 8) | (toSrgb(outB) & 0xFF);
    }

    private static void applyHalation(int[] pixels, int w, int h, float amount, String aspect) {
        if (pixels == null || w <= 0 || h <= 0 || amount <= 0f) return;
        int scale = 8, sw = Math.max(1, (w + scale - 1) / scale), sh = Math.max(1, (h + scale - 1) / scale);
        float[] glow = new float[sw * sh];
        for (int y = 0; y < sh; y++) {
            int py = Math.min(h - 1, y * scale) * w;
            int gy = y * sw;
            for (int x = 0; x < sw; x++) {
                int c = pixels[py + Math.min(w - 1, x * scale)];
                float luma = (.2126f * ((c >> 16) & 0xff) + .7152f * ((c >> 8) & 0xff) + .0722f * (c & 0xff)) * 0.00392157f;
                glow[gy + x] = Math.max(0f, luma - .72f);
            }
        }
        float[] temp = new float[glow.length];
        boolean isAnamorphic = aspect != null && AppSettings.parseAspectRatio(aspect) > 2.0f;
        blur(glow, temp, sw, sh, true);
        blur(temp, glow, sw, sh, false);
        blur(glow, temp, sw, sh, true);
        blur(temp, glow, sw, sh, false);
        if (isAnamorphic) {
            blur(glow, temp, sw, sh, true);
            System.arraycopy(temp, 0, glow, 0, glow.length);
        }
        float strength = clamp(amount, 0f, 1f) * 1.6f;
        float invScale = 1f / scale;
        float maxGx = sw - 1f, maxGy = sh - 1f;
        java.util.stream.IntStream.range(0, h).parallel().forEach(y -> {
            int rowOffset = y * w;
            float gyf = Math.min(maxGy, y * invScale);
            int gy0 = (int) gyf;
            int gy1 = Math.min(sh - 1, gy0 + 1);
            float ty = gyf - gy0;
            float wy0 = 1f - ty;
            int gRow0 = gy0 * sw;
            int gRow1 = gy1 * sw;
            for (int x = 0; x < w; x++) {
                float gxf = Math.min(maxGx, x * invScale);
                int gx0 = (int) gxf;
                int gx1 = Math.min(sw - 1, gx0 + 1);
                float tx = gxf - gx0;
                float top = glow[gRow0 + gx0] * (1f - tx) + glow[gRow0 + gx1] * tx;
                float bot = glow[gRow1 + gx0] * (1f - tx) + glow[gRow1 + gx1] * tx;
                float halo = (top * wy0 + bot * ty) * strength;
                int c = pixels[rowOffset + x];
                int r = (c >> 16) & 0xff;
                int g = (c >> 8) & 0xff;
                int b = c & 0xff;
                pixels[rowOffset + x] = 0xff000000 | (clamp255(r + halo * 90f) << 16)
                        | (clamp255(g + halo * 28f) << 8) | b;
            }
        });
    }

    private static void applyChromaDenoise(int[] pixels, int w, int h, float amount) {
        if (pixels == null || w < 3 || h < 3 || amount <= 0f) return;
        final float strength = clamp(amount, 0f, 1f) * .72f;
        final int interiorRows = h - 2;
        final int numStrips = interiorRows < 32 ? 1
                : Math.min(Runtime.getRuntime().availableProcessors() * 2, Math.max(1, interiorRows / 32));
        final int rowsPerStrip = (interiorRows + numStrips - 1) / numStrips;
        final int[][] seamBefore = new int[numStrips][w];
        final int[][] seamAfter = new int[numStrips][w];
        for (int s = 0; s < numStrips; s++) {
            int yStart = 1 + s * rowsPerStrip;
            if (yStart > h - 2) break;
            int yEnd = Math.min(h - 2, yStart + rowsPerStrip - 1);
            System.arraycopy(pixels, (yStart - 1) * w, seamBefore[s], 0, w);
            System.arraycopy(pixels, (yEnd + 1) * w, seamAfter[s], 0, w);
        }
        java.util.stream.IntStream stripStream = java.util.stream.IntStream.range(0, numStrips);
        if (numStrips > 1) stripStream = stripStream.parallel();
        stripStream.forEach(s -> {
            int yStart = 1 + s * rowsPerStrip;
            if (yStart > h - 2) return;
            int yEnd = Math.min(h - 2, yStart + rowsPerStrip - 1);
            int[] prevRow = seamBefore[s].clone();
            int[] currRow = new int[w];
            System.arraycopy(pixels, yStart * w, currRow, 0, w);
            int[] nextRow = new int[w];
            if (yStart == yEnd) {
                System.arraycopy(seamAfter[s], 0, nextRow, 0, w);
            } else {
                System.arraycopy(pixels, (yStart + 1) * w, nextRow, 0, w);
            }
            for (int y = yStart; y <= yEnd; y++) {
                int rowOffset = y * w;
                for (int x = 1; x < w - 1; x++) {
                    int c = currRow[x];
                    int left = currRow[x - 1];
                    int right = currRow[x + 1];
                    int up = prevRow[x];
                    int down = nextRow[x];

                    float r = ((c >> 16) & 0xff) * 0.00392157f;
                    float g = ((c >> 8) & 0xff) * 0.00392157f;
                    float b = (c & 0xff) * 0.00392157f;
                    float luma = .2126f * r + .7152f * g + .0722f * b;
                    float cr = r - luma, cg = g - luma, cb = b - luma;

                    float nr = ((left >> 16) & 0xff) * 0.00392157f;
                    float ng = ((left >> 8) & 0xff) * 0.00392157f;
                    float nb = (left & 0xff) * 0.00392157f;
                    float nl = .2126f * nr + .7152f * ng + .0722f * nb;
                    float mr = nr - nl, mg = ng - nl, mb = nb - nl, neighbourLuma = nl;

                    nr = ((right >> 16) & 0xff) * 0.00392157f;
                    ng = ((right >> 8) & 0xff) * 0.00392157f;
                    nb = (right & 0xff) * 0.00392157f;
                    nl = .2126f * nr + .7152f * ng + .0722f * nb;
                    mr += nr - nl; mg += ng - nl; mb += nb - nl; neighbourLuma += nl;

                    nr = ((up >> 16) & 0xff) * 0.00392157f;
                    ng = ((up >> 8) & 0xff) * 0.00392157f;
                    nb = (up & 0xff) * 0.00392157f;
                    nl = .2126f * nr + .7152f * ng + .0722f * nb;
                    mr += nr - nl; mg += ng - nl; mb += nb - nl; neighbourLuma += nl;

                    nr = ((down >> 16) & 0xff) * 0.00392157f;
                    ng = ((down >> 8) & 0xff) * 0.00392157f;
                    nb = (down & 0xff) * 0.00392157f;
                    nl = .2126f * nr + .7152f * ng + .0722f * nb;
                    mr += nr - nl; mg += ng - nl; mb += nb - nl; neighbourLuma += nl;

                    mr *= .25f; mg *= .25f; mb *= .25f; neighbourLuma *= .25f;
                    float gradient = Math.abs(luma - neighbourLuma);
                    float blend = strength * Math.max(0f, 1f - gradient * 5f);
                    float resultR = luma + cr * (1f - blend) + mr * blend;
                    float resultG = luma + cg * (1f - blend) + mg * blend;
                    float resultB = luma + cb * (1f - blend) + mb * blend;

                    pixels[rowOffset + x] = 0xff000000 | (clamp255(resultR * 255f) << 16)
                            | (clamp255(resultG * 255f) << 8) | clamp255(resultB * 255f);
                }
                if (y < yEnd) {
                    int[] reuse = prevRow;
                    prevRow = currRow;
                    currRow = nextRow;
                    nextRow = reuse;
                    if (y + 1 == yEnd) {
                        System.arraycopy(seamAfter[s], 0, nextRow, 0, w);
                    } else {
                        System.arraycopy(pixels, (y + 2) * w, nextRow, 0, w);
                    }
                }
            }
        });
    }

    private static void blur(float[] src, float[] dst, int w, int h, boolean horizontal) {
        final int radius = 2;
        final float invCount = 0.2f;
        java.util.stream.IntStream rows = java.util.stream.IntStream.range(0, h);
        if (h >= 64 && w * h >= 4096) rows = rows.parallel();
        if (horizontal) {
            rows.forEach(y -> {
                int rowOffset = y * w;
                int leftEnd = Math.min(w, radius);
                for (int x = 0; x < leftEnd; x++) {
                    float sum = 0f;
                    for (int d = -radius; d <= radius; d++) sum += src[rowOffset + clampIndex(x + d, w)];
                    dst[rowOffset + x] = sum * invCount;
                }
                int rightStart = Math.max(leftEnd, w - radius);
                for (int x = leftEnd; x < rightStart; x++) {
                    int p = rowOffset + x;
                    dst[p] = (src[p - 2] + src[p - 1] + src[p] + src[p + 1] + src[p + 2]) * invCount;
                }
                for (int x = rightStart; x < w; x++) {
                    float sum = 0f;
                    for (int d = -radius; d <= radius; d++) sum += src[rowOffset + clampIndex(x + d, w)];
                    dst[rowOffset + x] = sum * invCount;
                }
            });
        } else {
            final int w2 = w * 2;
            rows.forEach(y -> {
                int rowOffset = y * w;
                if (y >= radius && y < h - radius) {
                    for (int x = 0; x < w; x++) {
                        int p = rowOffset + x;
                        dst[p] = (src[p - w2] + src[p - w] + src[p] + src[p + w] + src[p + w2]) * invCount;
                    }
                } else {
                    int ym2 = clampIndex(y - 2, h) * w;
                    int ym1 = clampIndex(y - 1, h) * w;
                    int y0 = y * w;
                    int yp1 = clampIndex(y + 1, h) * w;
                    int yp2 = clampIndex(y + 2, h) * w;
                    for (int x = 0; x < w; x++) {
                        dst[rowOffset + x] = (src[ym2 + x] + src[ym1 + x] + src[y0 + x] + src[yp1 + x] + src[yp2 + x]) * invCount;
                    }
                }
            });
        }
    }

    private static void applySharpen(int[] pixels, int w, int h, float amount) {
        if (pixels == null || w < 3 || h < 3 || amount <= 0f) return;
        final float weight = 1f + 4f * amount;
        final int interiorRows = h - 2;
        final int numStrips = interiorRows < 32 ? 1
                : Math.min(Runtime.getRuntime().availableProcessors() * 2, Math.max(1, interiorRows / 32));
        final int rowsPerStrip = (interiorRows + numStrips - 1) / numStrips;
        final int[][] seamBefore = new int[numStrips][w];
        final int[][] seamAfter = new int[numStrips][w];
        for (int s = 0; s < numStrips; s++) {
            int yStart = 1 + s * rowsPerStrip;
            if (yStart > h - 2) break;
            int yEnd = Math.min(h - 2, yStart + rowsPerStrip - 1);
            System.arraycopy(pixels, (yStart - 1) * w, seamBefore[s], 0, w);
            System.arraycopy(pixels, (yEnd + 1) * w, seamAfter[s], 0, w);
        }
        java.util.stream.IntStream stripStream = java.util.stream.IntStream.range(0, numStrips);
        if (numStrips > 1) stripStream = stripStream.parallel();
        stripStream.forEach(s -> {
            int yStart = 1 + s * rowsPerStrip;
            if (yStart > h - 2) return;
            int yEnd = Math.min(h - 2, yStart + rowsPerStrip - 1);
            int[] prevRow = seamBefore[s].clone();
            int[] currRow = new int[w];
            System.arraycopy(pixels, yStart * w, currRow, 0, w);
            int[] nextRow = new int[w];
            if (yStart == yEnd) {
                System.arraycopy(seamAfter[s], 0, nextRow, 0, w);
            } else {
                System.arraycopy(pixels, (yStart + 1) * w, nextRow, 0, w);
            }
            for (int y = yStart; y <= yEnd; y++) {
                int rowOffset = y * w;
                for (int x = 1; x < w - 1; x++) {
                    int c = currRow[x];
                    int left = currRow[x - 1];
                    int right = currRow[x + 1];
                    int up = prevRow[x];
                    int down = nextRow[x];

                    float r = ((c >> 16) & 0xff) * weight - amount * (
                            ((left >> 16) & 0xff) + ((right >> 16) & 0xff) + ((up >> 16) & 0xff) + ((down >> 16) & 0xff));
                    float g = ((c >> 8) & 0xff) * weight - amount * (
                            ((left >> 8) & 0xff) + ((right >> 8) & 0xff) + ((up >> 8) & 0xff) + ((down >> 8) & 0xff));
                    float b = (c & 0xff) * weight - amount * (
                            (left & 0xff) + (right & 0xff) + (up & 0xff) + (down & 0xff));

                    pixels[rowOffset + x] = 0xff000000 | (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b);
                }
                if (y < yEnd) {
                    int[] reuse = prevRow;
                    prevRow = currRow;
                    currRow = nextRow;
                    nextRow = reuse;
                    if (y + 1 == yEnd) {
                        System.arraycopy(seamAfter[s], 0, nextRow, 0, w);
                    } else {
                        System.arraycopy(pixels, (y + 2) * w, nextRow, 0, w);
                    }
                }
            }
        });
    }

    private static float fastCbrt(float x) {
        if (x <= 0f) return 0f;
        int i = Float.floatToRawIntBits(x);
        i = (i / 3) + 0x2a514067;
        float y = Float.intBitsToFloat(i);
        y = (2f * y + x / (y * y)) * 0.33333333f;
        y = (2f * y + x / (y * y)) * 0.33333333f;
        return y;
    }

    static float[] cropRect(int width, int height, String aspect) {
        float ratio = AppSettings.parseAspectRatio(aspect);
        if (ratio <= 0f) return new float[]{0f, 0f, width, height};
        if (width < height) ratio = 1f / ratio;
        float current = width / (float) height;
        if (Math.abs(current - ratio) < .001f) return new float[]{0f, 0f, width, height};
        if (current > ratio) {
            float cropWidth = height * ratio;
            return new float[]{(width - cropWidth) * .5f, 0f, cropWidth, height};
        }
        float cropHeight = width / ratio;
        return new float[]{0f, (height - cropHeight) * .5f, width, cropHeight};
    }

    private static int normalizeOrientation(int orientation) {
        int normalized = ((orientation % 360) + 360) % 360;
        return normalized == 90 || normalized == 180 || normalized == 270 ? normalized : 0;
    }

    private static int[] buildSrgbLut() {
        int[] lut = new int[65536];
        for (int i = 0; i < lut.length; i++) {
            float linear = i / 65535f;
            float encoded = linear <= .0031308f ? linear * 12.92f
                    : 1.055f * (float) Math.pow(linear, 1f / 2.4f) - .055f;
            lut[i] = clamp255(encoded * 255f);
        }
        return lut;
    }

    private static float[] buildSrgbFloatLut() {
        float[] lut = new float[65536];
        for (int i = 0; i < lut.length; i++) {
            float linear = i / 65535f;
            lut[i] = linear <= .0031308f ? linear * 12.92f
                    : 1.055f * (float) Math.pow(linear, 1f / 2.4f) - .055f;
        }
        return lut;
    }

    private static int scaleColor(int color, float scale) {
        int r = clamp255(((color >> 16) & 0xFF) * scale);
        int g = clamp255(((color >> 8) & 0xFF) * scale);
        int b = clamp255((color & 0xFF) * scale);
        return (0xFF << 24) | (r << 16) | (g << 8) | b;
    }
    private static float smoothstep(float edge0, float edge1, float x) {
        float t = clamp((x-edge0)/(edge1-edge0), 0f, 1f); return t*t*(3f-2f*t);
    }
    private static float hashNoise(int x, int y, int seed) {
        int n = x * 0x1f123bb5 ^ y * 0x5f356495 ^ seed;
        n ^= n >>> 16; n *= 0x7feb352d; n ^= n >>> 15; n *= 0x846ca68b; n ^= n >>> 16;
        return ((n & 0x00ffffff) / 8388607.5f) - 1f;
    }
    private static int clampIndex(int v, int size) { return Math.max(0, Math.min(size-1, v)); }
    private static float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }
    private static int clamp255(float v) { return Math.max(0, Math.min(255, Math.round(v))); }
    private static int toSrgb(float linear) {
        int index = Math.max(0, Math.min(65535, Math.round(clamp(linear, 0f, 1f) * 65535f)));
        return SRGB_LUT[index];
    }
    private static float toSrgbFloat(float linear) {
        int index = Math.max(0, Math.min(65535, Math.round(clamp(linear, 0f, 1f) * 65535f)));
        return SRGB_FLOAT_LUT[index];
    }
    private static float srgbToLinear(float encoded) {
        return encoded <= .04045f ? encoded / 12.92f : (float) Math.pow((encoded + .055f) / 1.055f, 2.4);
    }
}
