package com.scope.camera;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Unit tests for RawDeveloper and Look parameters, testing serialization round-trip,
 * deep copying, and tone grading math.
 */
public class RawDeveloperTest {

    @Test
    public void testLookJsonRoundTrip() {
        RawDeveloper.Look original = new RawDeveloper.Look();
        original.brain = AppSettings.BRAIN_KODACHROME;
        original.exposureEv = 0.5f;
        original.contrast = 1.35f;
        original.pivot = 0.25f;
        original.saturation = 1.15f;
        original.warmth = 0.12f;
        original.highlights = 0.75f;
        original.shadows = 0.40f;
        original.grain = 0.28f;
        original.halation = 0.14f;
        original.sharpening = 0.08f;
        original.chromaNr = 0.18f;
        original.vignette = 0.22f;
        original.aspectRatio = "16:9";
        original.flipHorizontal = true;
        original.highlightRecovery = "Reconstruct";
        original.shadowProfile = "Deep Cinema Blacks";
        original.toneCurve = "Filmic S-Curve";
        original.grainResponse = "Luminance-weighted";
        original.easyMode = false;
        original.skinProtection = true;
        original.smartVibrance = true;
        original.opticalCorrection = false;
        original.frameBorder = "35mm Negative";
        original.lutName = LutManager.LUT_PORTRA;
        original.lutIntensity = 0.85f;

        String json = original.toJson();
        assertNotNull("JSON serialization failed", json);

        RawDeveloper.Look restored = RawDeveloper.Look.fromJson(json);
        assertNotNull("JSON deserialization failed", restored);

        assertEquals(original.brain, restored.brain);
        assertEquals(original.exposureEv, restored.exposureEv, 0.001f);
        assertEquals(original.contrast, restored.contrast, 0.001f);
        assertEquals(original.pivot, restored.pivot, 0.001f);
        assertEquals(original.saturation, restored.saturation, 0.001f);
        assertEquals(original.warmth, restored.warmth, 0.001f);
        assertEquals(original.highlights, restored.highlights, 0.001f);
        assertEquals(original.shadows, restored.shadows, 0.001f);
        assertEquals(original.grain, restored.grain, 0.001f);
        assertEquals(original.halation, restored.halation, 0.001f);
        assertEquals(original.aspectRatio, restored.aspectRatio);
        assertEquals(original.flipHorizontal, restored.flipHorizontal);
        assertEquals(original.lutName, restored.lutName);
        assertEquals(original.lutIntensity, restored.lutIntensity, 0.001f);
        assertEquals(original.frameBorder, restored.frameBorder);
    }

    @Test
    public void testLookDeepCopy() {
        RawDeveloper.Look original = new RawDeveloper.Look();
        original.brain = AppSettings.BRAIN_MONO_SILVER;
        original.lutName = LutManager.LUT_ANSEL_BW;
        original.lutIntensity = 0.70f;
        original.contrast = 1.45f;

        RawDeveloper.Look copy = original.copy();
        assertNotNull(copy);
        assertEquals(original.brain, copy.brain);
        assertEquals(original.lutName, copy.lutName);
        assertEquals(original.lutIntensity, copy.lutIntensity, 0.001f);

        // Mutate copy and ensure original is unmodified
        copy.brain = AppSettings.BRAIN_FUJI_ETERNA;
        copy.lutName = LutManager.LUT_FUJI;
        copy.lutIntensity = 1.0f;

        assertEquals(AppSettings.BRAIN_MONO_SILVER, original.brain);
        assertEquals(LutManager.LUT_ANSEL_BW, original.lutName);
        assertEquals(0.70f, original.lutIntensity, 0.001f);
    }

    @Test
    public void testFilmicToePreservesShadowGradationBelowPivot() {
        RawDeveloper.Look look = new RawDeveloper.Look();
        look.brain = AppSettings.BRAIN_LIKA_FILM;
        look.contrast = 1.12f;
        look.pivot = 0.20f;
        look.shadows = 0.36f;
        look.grain = 0f;
        look.warmth = 0f;
        look.easyMode = true;

        // Test scene-linear shadow tones well below the 0.20 pivot (e.g. 0.02 and 0.05)
        int darkShadow = RawDeveloper.renderLook(0.02f, 0.02f, 0.02f, look, 0, 0, 1);
        int midShadow = RawDeveloper.renderLook(0.05f, 0.05f, 0.05f, look, 0, 0, 1);
        int lowerMidtone = RawDeveloper.renderLook(0.12f, 0.12f, 0.12f, look, 0, 0, 1);

        int rDark = (darkShadow >> 16) & 0xff;
        int rMid = (midShadow >> 16) & 0xff;
        int rLowMid = (lowerMidtone >> 16) & 0xff;

        // Shadows below pivot must not be hard-clipped to 0 and must maintain strictly monotonic gradation
        org.junit.Assert.assertTrue("Shadow at linear 0.02 should not be crushed to black, got " + rDark, rDark > 15);
        org.junit.Assert.assertTrue("Shadow gradation must be monotonic (0.02 < 0.05)", rMid > rDark + 10);
        org.junit.Assert.assertTrue("Shadow gradation must be monotonic (0.05 < 0.12)", rLowMid > rMid + 15);
    }

    @Test
    public void testSceneExposureGainNormalizesUnderexposedRawAndAppliesPostRawBoost() {
        RawArchive.Frame frame = new RawArchive.Frame();
        frame.width = 32;
        frame.height = 24;
        frame.staticWhiteLevel = 1023;
        frame.postRawSensitivityBoost = 200; // 2.0x ISP digital post-RAW boost
        frame.blackLevels = new float[]{0f, 0f, 0f, 0f};
        frame.wbGains = new float[]{1f, 1f, 1f, 1f};

        // Populate synthetic sensor samples where raw linear values are ~0.025 (underexposed indoor scene)
        short[] samples = new short[frame.width * frame.height];
        java.util.Arrays.fill(samples, (short) 26); // ~0.0254 of 1023 white level

        RawDeveloper.Look look = new RawDeveloper.Look();
        look.easyMode = true;
        float[] identity = new float[]{1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f};
        float[] crop = new float[]{0f, 0f, 32f, 24f};

        float gainEasy = RawDeveloper.computeSceneExposureGain(
                samples, frame, 1023f, frame.wbGains, identity, crop, 0, false, look);
        org.junit.Assert.assertTrue("Easy mode should apply postRawBoost * sceneGain (> 2.5x), got " + gainEasy, gainEasy > 2.5f);

        look.easyMode = false;
        float gainManual = RawDeveloper.computeSceneExposureGain(
                samples, frame, 1023f, frame.wbGains, identity, crop, 0, false, look);
        assertEquals(2.0f, gainManual, 0.01f);
    }

    @Test
    public void testDevelopBitmapInMemPipelineAndBorder() {
        android.graphics.Bitmap testBmp = android.graphics.Bitmap.createBitmap(100, 80, android.graphics.Bitmap.Config.ARGB_8888);
        RawDeveloper.Look look = new RawDeveloper.Look();
        look.brain = AppSettings.BRAIN_LIKA_FILM;
        look.frameBorder = "None";

        android.graphics.Bitmap devNoBorder = RawDeveloper.developBitmap(testBmp, look, false);
        assertNotNull(devNoBorder);
        assertEquals(100, devNoBorder.getWidth());
        assertEquals(80, devNoBorder.getHeight());

        look.frameBorder = "Museum · White Fine Art";
        android.graphics.Bitmap devBordered = RawDeveloper.developBitmap(testBmp, look, false);
        assertNotNull(devBordered);
        // Border padding adds width and height
        org.junit.Assert.assertTrue("Bordered bitmap width should expand", devBordered.getWidth() > 100);
        org.junit.Assert.assertTrue("Bordered bitmap height should expand", devBordered.getHeight() > 80);
    }
}
