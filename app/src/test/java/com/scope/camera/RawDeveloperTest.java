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
        original.filmPushPull = AppSettings.FILM_PUSH_PULL_CHOICES[1];
        original.ultraHdr = true;

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
        assertEquals(original.filmPushPull, restored.filmPushPull);
        assertEquals(original.ultraHdr, restored.ultraHdr);
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
        org.junit.Assert.assertTrue("Shadow at linear 0.02 should not be crushed to black, got " + rDark, rDark > 25);
        org.junit.Assert.assertTrue("Shadow gradation must be monotonic (0.02 < 0.05)", rMid > rDark + 12);
        org.junit.Assert.assertTrue("Shadow gradation must be monotonic (0.05 < 0.12)", rLowMid > rMid + 18);
    }

    @Test
    public void testDarkShadowNoiseDoesNotExplodeIntoColorSpeckles() {
        RawDeveloper.Look look = new RawDeveloper.Look();
        look.brain = AppSettings.BRAIN_LIKA_FILM;
        look.contrast = 1.12f;
        look.pivot = 0.20f;
        look.shadows = 0.36f;
        look.grain = 0.22f;
        look.warmth = 0f;
        look.easyMode = true;

        // Simulate a noisy dark sensor pixel (e.g. turned-off TV screen) where off-diagonal
        // color matrix coefficients produce a negative green excursion alongside tiny R/B noise.
        int pixel = RawDeveloper.renderLook(0.008f, -0.003f, 0.012f, look, 10, 10, 42);
        int r = (pixel >> 16) & 0xff;
        int g = (pixel >> 8) & 0xff;
        int b = pixel & 0xff;

        org.junit.Assert.assertTrue("Dark noise R channel must stay dark (< 35), got " + r, r < 35);
        org.junit.Assert.assertTrue("Dark noise G channel must stay dark (< 35), got " + g, g < 35);
        org.junit.Assert.assertTrue("Dark noise B channel must stay dark (< 35), got " + b, b < 35);
        org.junit.Assert.assertTrue("Dark shadow noise must be desaturated (no magenta speckle), |R-G|=" + Math.abs(r - g), Math.abs(r - g) <= 6);
        org.junit.Assert.assertTrue("Dark shadow noise must be desaturated (no purple speckle), |B-G|=" + Math.abs(b - g), Math.abs(b - g) <= 6);
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
    public void testDevelopBitmapHandlesNull() {
        org.junit.Assert.assertNull(RawDeveloper.developBitmap(null, null, false));
    }

    @Test
    public void testMonoSilverUsesPanchromaticResponseAndSupportsPaperToning() {
        RawDeveloper.Look mono = new RawDeveloper.Look();
        mono.brain = AppSettings.BRAIN_MONO_SILVER;
        mono.grain = 0f;
        mono.warmth = 0f;
        mono.easyMode = true;

        // Pure red vs pure blue of equal energy: panchromatic Tri-X (0.33 R vs 0.08 B)
        // must render red/skin tones significantly more luminous than blue skies.
        int redPixel = RawDeveloper.renderLook(0.40f, 0.05f, 0.05f, mono, 0, 0, 1);
        int bluePixel = RawDeveloper.renderLook(0.05f, 0.05f, 0.40f, mono, 0, 0, 1);

        int rLuma = (redPixel >> 16) & 0xff;
        int bLuma = (bluePixel >> 16) & 0xff;
        org.junit.Assert.assertTrue("Panchromatic MONO / SILVER must render red brighter than blue (" + rLuma + " vs " + bLuma + ")",
                rLuma > bLuma + 25);

        // When warmth is shifted > 0 on MONO / SILVER, warm silver-gelatin toning should warm R over B
        mono.warmth = 0.50f;
        int tonedMid = RawDeveloper.renderLook(0.22f, 0.22f, 0.22f, mono, 0, 0, 1);
        int tr = (tonedMid >> 16) & 0xff;
        int tb = tonedMid & 0xff;
        org.junit.Assert.assertTrue("Warm-toned MONO / SILVER should have R > B, got R=" + tr + " B=" + tb, tr > tb);
    }

    @Test
    public void testWhiteBalanceModeParsingAndFormatting() {
        assertEquals(0, AppSettings.parseWhiteBalanceModeKelvin("Auto · as shot"));
        assertEquals(2800, AppSettings.parseWhiteBalanceModeKelvin("2800 K · incandescent"));
        assertEquals(3200, AppSettings.parseWhiteBalanceModeKelvin("3200 K · tungsten"));
        assertEquals(5600, AppSettings.parseWhiteBalanceModeKelvin("5600 K · daylight"));
        assertEquals(7500, AppSettings.parseWhiteBalanceModeKelvin("7500 K · shade"));
        assertEquals("3200 K · tungsten", AppSettings.formatWhiteBalanceMode(3200));
        assertEquals("Auto · as shot", AppSettings.formatWhiteBalanceMode(0));
    }

    @Test
    public void testLookPushPullAndUltraHdrSerialization() {
        RawDeveloper.Look look = new RawDeveloper.Look();
        look.filmPushPull = AppSettings.FILM_PUSH_PULL_CHOICES[1]; // +1 Stop Push
        look.ultraHdr = true;

        String json = look.toJson();
        assertNotNull(json);

        RawDeveloper.Look restored = RawDeveloper.Look.fromJson(json);
        assertNotNull(restored);
        assertEquals(look.filmPushPull, restored.filmPushPull);
        assertEquals(look.ultraHdr, restored.ultraHdr);

        RawDeveloper.Look copy = look.copy();
        assertNotNull(copy);
        assertEquals(look.filmPushPull, copy.filmPushPull);
        assertEquals(look.ultraHdr, copy.ultraHdr);
    }

    @Test
    public void testFilmPushPullModifiesContrastAndShadows() {
        RawDeveloper.Look box = new RawDeveloper.Look();
        box.brain = AppSettings.BRAIN_LIKA_FILM;
        box.filmPushPull = AppSettings.FILM_PUSH_PULL_CHOICES[0]; // Box Speed
        box.grain = 0f;

        RawDeveloper.Look push = box.copy();
        push.filmPushPull = AppSettings.FILM_PUSH_PULL_CHOICES[1]; // +1 Stop Push

        RawDeveloper.Look pull = box.copy();
        pull.filmPushPull = AppSettings.FILM_PUSH_PULL_CHOICES[3]; // -1 Stop Pull

        // Shadow sample (linear 0.04) and highlight sample (linear 0.55)
        int shadowBox = RawDeveloper.renderLook(0.04f, 0.04f, 0.04f, box, 0, 0, 1);
        int shadowPush = RawDeveloper.renderLook(0.04f, 0.04f, 0.04f, push, 0, 0, 1);
        int shadowPull = RawDeveloper.renderLook(0.04f, 0.04f, 0.04f, pull, 0, 0, 1);

        int sBoxR = (shadowBox >> 16) & 0xff;
        int sPushR = (shadowPush >> 16) & 0xff;
        int sPullR = (shadowPull >> 16) & 0xff;

        // Pushed film should develop deeper shadows (lower pixel value) than box speed
        org.junit.Assert.assertTrue("Push +1 should deepen shadow tones (got box=" + sBoxR + " push=" + sPushR + ")",
                sPushR <= sBoxR);

        // Pulled film should open shadows (higher pixel value) than box speed
        org.junit.Assert.assertTrue("Pull -1 should lift shadow tones (got box=" + sBoxR + " pull=" + sPullR + ")",
                sPullR >= sBoxR);
    }

    @Test
    public void testFrameBorderSerializationAndNullHandling() {
        RawDeveloper.Look lookMed = new RawDeveloper.Look();
        lookMed.frameBorder = "Medium Format 6×6 Rebate";
        String jsonMed = lookMed.toJson();
        RawDeveloper.Look restoredMed = RawDeveloper.Look.fromJson(jsonMed);
        assertNotNull(restoredMed);
        assertEquals("Medium Format 6×6 Rebate", restoredMed.frameBorder);
        org.junit.Assert.assertNull(RawDeveloper.applyFrameBorder(null, restoredMed, null));

        RawDeveloper.Look lookCinema = new RawDeveloper.Look();
        lookCinema.frameBorder = "Cinemascope 2.39:1 Matte";
        String jsonCinema = lookCinema.toJson();
        RawDeveloper.Look restoredCinema = RawDeveloper.Look.fromJson(jsonCinema);
        assertNotNull(restoredCinema);
        assertEquals("Cinemascope 2.39:1 Matte", restoredCinema.frameBorder);
        org.junit.Assert.assertNull(RawDeveloper.applyFrameBorder(null, restoredCinema, null));
    }

    @Test
    public void testAllBrainsProduceDistinctColorAndToneInEasyMode() {
        String[] brains = AppSettings.BRAIN_CHOICES;
        int[][] results = new int[brains.length][3];

        // Test warm midtone (indoor wall), shadow (dark fabric), and foliage green across all 6 Brains
        for (int i = 0; i < brains.length; i++) {
            RawDeveloper.Look look = new RawDeveloper.Look();
            look.brain = brains[i];
            look.grain = 0f;
            look.easyMode = true;
            results[i][0] = RawDeveloper.renderLook(0.28f, 0.18f, 0.10f, look, 0, 0, 1);
            results[i][1] = RawDeveloper.renderLook(0.05f, 0.03f, 0.018f, look, 0, 0, 1);
            results[i][2] = RawDeveloper.renderLook(0.14f, 0.28f, 0.10f, look, 0, 0, 1);
        }

        // Every pair of Brains must differ substantially across the 3 test swatches
        for (int i = 0; i < brains.length; i++) {
            for (int j = i + 1; j < brains.length; j++) {
                int maxChannelDiff = 0;
                for (int s = 0; s < 3; s++) {
                    int c1 = results[i][s];
                    int c2 = results[j][s];
                    maxChannelDiff = Math.max(maxChannelDiff, Math.abs(((c1 >> 16) & 0xff) - ((c2 >> 16) & 0xff)));
                    maxChannelDiff = Math.max(maxChannelDiff, Math.abs(((c1 >> 8) & 0xff) - ((c2 >> 8) & 0xff)));
                    maxChannelDiff = Math.max(maxChannelDiff, Math.abs((c1 & 0xff) - (c2 & 0xff)));
                }
                org.junit.Assert.assertTrue(
                        "Brains " + brains[i] + " and " + brains[j] + " must produce distinct rendering (maxDiff=" + maxChannelDiff
                                + ", s0=" + (results[i][0] & 0xffffff) + " vs " + (results[j][0] & 0xffffff)
                                + ", s1=" + (results[i][1] & 0xffffff) + " vs " + (results[j][1] & 0xffffff)
                                + ", s2=" + (results[i][2] & 0xffffff) + " vs " + (results[j][2] & 0xffffff) + ")",
                        maxChannelDiff >= 8);
            }
        }

        // Specifically verify FUJI / ETERNA lifts dark shadows compared to KODACHROME / 64 & BLEACH BYPASS
        int kodaShadowG = (results[1][1] >> 8) & 0xff;
        int fujiShadowG = (results[2][1] >> 8) & 0xff;
        int bleachShadowG = (results[3][1] >> 8) & 0xff;
        org.junit.Assert.assertTrue("FUJI ETERNA shadow (" + fujiShadowG + ") should be lifted above KODACHROME (" + kodaShadowG + ")",
                fujiShadowG > kodaShadowG + 10);
        org.junit.Assert.assertTrue("FUJI ETERNA shadow (" + fujiShadowG + ") should be lifted above BLEACH (" + bleachShadowG + ")",
                fujiShadowG > bleachShadowG + 15);
    }

    @Test
    public void testWarmIndoorLightingPreservesBlueChannelAndAvoidsYellowGreenCrush() {
        RawDeveloper.Look look = new RawDeveloper.Look();
        look.brain = AppSettings.BRAIN_LIKA_FILM;
        look.grain = 0f;
        look.easyMode = true;

        // Warm beige indoor wall under tungsten light (post-scene-gain linear values)
        int wall = RawDeveloper.renderLook(0.28f, 0.18f, 0.10f, look, 0, 0, 1);
        int r = (wall >> 16) & 0xff;
        int g = (wall >> 8) & 0xff;
        int b = wall & 0xff;

        org.junit.Assert.assertTrue("Warm indoor wall must retain healthy blue channel (>= 70), got " + b, b >= 70);
        org.junit.Assert.assertTrue("Warm indoor wall must stay well-exposed (G >= 105), got " + g, g >= 105);
        org.junit.Assert.assertTrue("Warm indoor wall should have natural R > G > B warmth", r > g && g > b);
    }

    @Test
    public void testHdrIndoorSceneExposureGainAndLocalShadowRecovery() {
        RawArchive.Frame frame = new RawArchive.Frame();
        frame.width = 48;
        frame.height = 36;
        frame.staticWhiteLevel = 1023;
        frame.postRawSensitivityBoost = 100;
        frame.blackLevels = new float[]{0f, 0f, 0f, 0f};
        frame.wbGains = new float[]{1f, 1f, 1f, 1f};

        // Top-right quadrant has bright lamp highlights (0.50); remainder is dark indoor shadows (0.012)
        short[] samples = new short[frame.width * frame.height];
        for (int y = 0; y < frame.height; y++) {
            for (int x = 0; x < frame.width; x++) {
                boolean lamp = (y < frame.height / 3) && (x > (frame.width * 2) / 3);
                samples[y * frame.width + x] = (short) (lamp ? 512 : 12);
            }
        }

        RawDeveloper.Look look = new RawDeveloper.Look();
        look.easyMode = true;
        float[] identity = new float[]{1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f};
        float[] crop = new float[]{0f, 0f, 48f, 36f};

        float sceneGain = RawDeveloper.computeSceneExposureGain(
                samples, frame, 1023f, frame.wbGains, identity, crop, 0, false, look);
        org.junit.Assert.assertTrue("Indoor recovery should lift real shadow signal without exceeding 3x, got " + sceneGain,
                sceneGain > 1f && sceneGain <= 3f);

        float[] localGrid = RawDeveloper.buildLocalToneGainGrid(
                samples, frame, 1023f, frame.wbGains, identity, true, identity, sceneGain,
                crop, 48f, 36f, 0, false, false, 48, 36, 12, 9, 0);
        assertNotNull(localGrid);
        float shadowGain = localGrid[7 * 12 + 2]; // bottom-left dark region
        float lampGain = localGrid[1 * 12 + 10];  // top-right bright lamp
        org.junit.Assert.assertTrue("Shadow region should get restrained local lift, got " + shadowGain,
                shadowGain > 1f && shadowGain <= 1.65f);
        org.junit.Assert.assertTrue("Bright region must not receive shadow amplification, got " + lampGain, lampGain <= 1.0f);
    }
    @Test public void recoveryRespectsIsoManualExposureAndDeliberateUnderexposure() {
        RawArchive.Frame frame = new RawArchive.Frame();
        frame.width = 32; frame.height = 24;
        frame.blackLevels = new float[]{0f,0f,0f,0f};
        frame.wbGains = new float[]{1f,1f,1f,1f};
        frame.colorCorrectionTransform = new float[]{1,0,0,0,1,0,0,0,1};
        short[] samples = new short[32*24];
        java.util.Arrays.fill(samples, (short) 40);
        RawDeveloper.Look look = new RawDeveloper.Look();
        float[] identity = frame.colorCorrectionTransform;
        float[] crop = {0,0,32,24};
        frame.sensitivityIso = 100;
        float lowIso = RawDeveloper.computeSceneExposureGain(samples,frame,1023,frame.wbGains,identity,crop,0,false,look);
        frame.sensitivityIso = 3200;
        float highIso = RawDeveloper.computeSceneExposureGain(samples,frame,1023,frame.wbGains,identity,crop,0,false,look);
        org.junit.Assert.assertTrue(lowIso > highIso);
        org.junit.Assert.assertTrue(highIso < 2.1f);
        frame.sensitivityIso = 100;
        frame.aeCompensationEv = -2f;
        float darkIntent = RawDeveloper.computeSceneExposureGain(samples,frame,1023,frame.wbGains,identity,crop,0,false,look);
        org.junit.Assert.assertTrue(darkIntent < lowIso);
        frame.manualExposure = true;
        assertEquals(1f, RawDeveloper.computeSceneExposureGain(samples,frame,1023,frame.wbGains,identity,crop,0,false,look),.001f);
        frame.manualExposure = false;
        java.util.Arrays.fill(samples, (short) 1);
        assertEquals(1f, RawDeveloper.computeSceneExposureGain(samples,frame,1023,frame.wbGains,identity,crop,0,false,look),.001f);
    }

    @Test public void previewReferenceDoesNotApplyCaptureCompensationTwice() {
        int[] preview = new int[32*24];
        java.util.Arrays.fill(preview, 0xff404040);
        float target = RawDeveloper.previewMidTarget(preview,32,24);
        org.junit.Assert.assertTrue(target > .045f && target < .06f);
        RawArchive.Frame frame = new RawArchive.Frame();
        frame.width=32; frame.height=24; frame.previewMidTarget=target;
        frame.blackLevels = new float[]{0,0,0,0};
        frame.colorCorrectionTransform = new float[]{1,0,0,0,1,0,0,0,1};
        short[] samples = new short[32*24];
        java.util.Arrays.fill(samples, (short) 40);
        RawDeveloper.Look look = new RawDeveloper.Look();
        float[] crop = {0,0,32,24};
        float normal = RawDeveloper.computeSceneExposureGain(samples,frame,1023,frame.wbGains,frame.colorCorrectionTransform,crop,0,false,look);
        frame.aeCompensationEv = -2f;
        assertEquals(normal, RawDeveloper.computeSceneExposureGain(samples,frame,1023,frame.wbGains,frame.colorCorrectionTransform,crop,0,false,look),.0001f);
    }

    @Test public void grainIsNeutralDeterministicAndScalesWithOutputSize() {
        RawDeveloper.Look look = new RawDeveloper.Look();
        look.grain=1f;
        RawDeveloper.PreparedLook prepared = RawDeveloper.prepareDisplayLook(look);
        int[] small = new int[80*60], large = new int[160*120];
        java.util.Arrays.fill(small,0xff808080);
        java.util.Arrays.fill(large,0xff808080);
        RawDeveloper.applyFilmGrain(small,80,60,prepared,17);
        RawDeveloper.applyFilmGrain(large,160,120,prepared,17);
        boolean changed=false;
        for (int y=0;y<60;y++) for (int x=0;x<80;x++) {
            int c=small[y*80+x];
            if(c != 0xff808080) changed=true;
            assertEquals(c,large[(y*2)*160+x*2]);
            assertEquals((c>>16)&255,c&255);
            assertEquals((c>>8)&255,c&255);
        }
        org.junit.Assert.assertTrue(changed);
        look.grain=0;
        int[] disabled={0xff123456};
        RawDeveloper.applyFilmGrain(disabled,1,1,RawDeveloper.prepareDisplayLook(look),17);
        assertEquals(0xff123456,disabled[0]);
    }

}
