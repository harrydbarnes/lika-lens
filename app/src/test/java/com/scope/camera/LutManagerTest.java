package com.scope.camera;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Deterministic unit tests for LutManager and 3D LUT engine, verifying .CUBE parsing,
 * trilinear interpolation, intensity blending, and built-in film profiles.
 */
public class LutManagerTest {

    @Test
    public void testParse3dCube() throws IOException {
        String cubeData = "# Sample 3D LUT\n" +
                "TITLE \"Test 3D\"\n" +
                "LUT_3D_SIZE 2\n" +
                "DOMAIN_MIN 0.0 0.0 0.0\n" +
                "DOMAIN_MAX 1.0 1.0 1.0\n" +
                "0.0 0.0 0.0\n" +
                "1.0 0.0 0.0\n" +
                "0.0 1.0 0.0\n" +
                "1.0 1.0 0.0\n" +
                "0.0 0.0 1.0\n" +
                "1.0 0.0 1.0\n" +
                "0.0 1.0 1.0\n" +
                "1.0 1.0 1.0\n";

        ByteArrayInputStream is = new ByteArrayInputStream(cubeData.getBytes(StandardCharsets.UTF_8));
        LutManager.Lut3D lut = LutManager.parseCube(is, "Test 3D");

        assertNotNull(lut);
        assertEquals(2, lut.size);
        assertEquals("Test 3D", lut.title);
        assertEquals(2 * 2 * 2 * 3, lut.table.length);

        // Test trilinear interpolation at corners (identity map in this cube)
        int black = lut.applyToRgb(0.0f, 0.0f, 0.0f, 1.0f);
        assertEquals(0xff000000, black);

        int white = lut.applyToRgb(1.0f, 1.0f, 1.0f, 1.0f);
        assertEquals(0xffffffff, white);

        int mid = lut.applyToRgb(0.5f, 0.5f, 0.5f, 1.0f);
        int midR = (mid >> 16) & 0xff;
        int midG = (mid >> 8) & 0xff;
        int midB = mid & 0xff;
        assertTrue(Math.abs(midR - 128) <= 2);
        assertTrue(Math.abs(midG - 128) <= 2);
        assertTrue(Math.abs(midB - 128) <= 2);
    }

    @Test
    public void testParse1dCube() throws IOException {
        String cube1d = "TITLE \"Test 1D\"\n" +
                "LUT_1D_SIZE 2\n" +
                "0.1 0.2 0.3\n" +
                "0.9 0.8 0.7\n";

        ByteArrayInputStream is = new ByteArrayInputStream(cube1d.getBytes(StandardCharsets.UTF_8));
        LutManager.Lut3D lut = LutManager.parseCube(is, "Test 1D");

        assertNotNull(lut);
        assertTrue(lut.size > 0);
        assertNotNull(lut.table);
    }

    @Test
    public void testIntensityBlending() throws IOException {
        // Red inverter LUT: input R 0.0 -> 1.0, 1.0 -> 0.0
        String cubeData = "LUT_3D_SIZE 2\n" +
                "1.0 0.0 0.0\n" +
                "0.0 0.0 0.0\n" +
                "1.0 1.0 0.0\n" +
                "0.0 1.0 0.0\n" +
                "1.0 0.0 1.0\n" +
                "0.0 0.0 1.0\n" +
                "1.0 1.0 1.0\n" +
                "0.0 1.0 1.0\n";

        ByteArrayInputStream is = new ByteArrayInputStream(cubeData.getBytes(StandardCharsets.UTF_8));
        LutManager.Lut3D lut = LutManager.parseCube(is, "InvertRed");

        // Input pure red (r=1, g=0, b=0)
        // At 0% intensity: output is identity input (pure red)
        int out0 = lut.applyToRgb(1.0f, 0.0f, 0.0f, 0.0f);
        int r0 = (out0 >> 16) & 0xff;
        assertEquals(255, r0);

        // At 100% intensity: output is inverted red (r=0)
        int out100 = lut.applyToRgb(1.0f, 0.0f, 0.0f, 1.0f);
        int r100 = (out100 >> 16) & 0xff;
        assertEquals(0, r100);

        // At 50% intensity: output should be intermediate (~128)
        int out50 = lut.applyToRgb(1.0f, 0.0f, 0.0f, 0.5f);
        int r50 = (out50 >> 16) & 0xff;
        assertTrue("Expected blend around 128, got " + r50, Math.abs(r50 - 128) <= 4);
    }

    @Test
    public void testBuiltInFilmProfilesGeneration() {
        String[] profiles = {
                LutManager.LUT_PORTRA,
                LutManager.LUT_FUJI,
                LutManager.LUT_TEAL_ORANGE,
                LutManager.LUT_ANSEL_BW,
                LutManager.LUT_AMBER_70S,
                LutManager.LUT_BLEACH
        };

        for (String profile : profiles) {
            LutManager.Lut3D lut = LutManager.generateBuiltIn(profile, 17);
            assertNotNull("Built-in profile should generate: " + profile, lut);
            assertEquals(17, lut.size);
            assertEquals(17 * 17 * 17 * 3, lut.table.length);

            // Verify monochrome in Ansel Silver B&W
            if (LutManager.LUT_ANSEL_BW.equals(profile)) {
                int color = lut.applyToRgb(0.8f, 0.2f, 0.1f, 1.0f);
                int r = (color >> 16) & 0xff;
                int g = (color >> 8) & 0xff;
                int b = color & 0xff;
                assertTrue("Ansel B&W R and G match: " + r + " vs " + g, Math.abs(r - g) <= 2);
                assertTrue("Ansel B&W G and B match: " + g + " vs " + b, Math.abs(g - b) <= 2);
            }
        }
    }

    @Test
    public void testInvalidCubeHandling() {
        try {
            ByteArrayInputStream is = new ByteArrayInputStream("Not a cube file\n".getBytes(StandardCharsets.UTF_8));
            LutManager.parseCube(is, "Corrupted");
            fail("Expected IOException on invalid CUBE format");
        } catch (IOException expected) {
            // Success
        }
    }

    @Test
    public void testParseCubeHandlesTabsCrlfAndTrailingSpaces() throws IOException {
        String cubeData = "  # Comment with leading space\r\n" +
                "\tTITLE\tUnquoted_Title  \r\n" +
                "LUT_3D_SIZE\t2  \r\n" +
                "LUT_3D_INPUT_RANGE 0.0 1.0\r\n" +
                "  0.0\t0.0   0.0  \r\n" +
                "1.0 0.0 0.0\t\r\n" +
                "0.0\t1.0\t0.0\r\n" +
                "1.0 1.0 0.0\r\n" +
                "0.0 0.0 1.0\r\n" +
                "1.0 0.0 1.0\r\n" +
                "0.0 1.0 1.0\r\n" +
                "1.0 1.0 1.0  \r\n";

        ByteArrayInputStream is = new ByteArrayInputStream(cubeData.getBytes(StandardCharsets.UTF_8));
        LutManager.Lut3D lut = LutManager.parseCube(is, "Fallback");
        assertNotNull(lut);
        assertEquals(2, lut.size);
        assertEquals("Unquoted_Title", lut.title);
        assertEquals(0xff000000, lut.applyToRgb(0f, 0f, 0f, 1f));
        assertEquals(0xffffffff, lut.applyToRgb(1f, 1f, 1f, 1f));
        assertEquals(0xff00ff00, lut.applyToRgb(0f, 1f, 0f, 1f));
    }

    @Test
    public void testGetBuiltInCachesBySizeAndRejectsNoneOrUnknown() {
        LutManager.Lut3D first17 = LutManager.getBuiltIn(LutManager.LUT_PORTRA, 17);
        LutManager.Lut3D second17 = LutManager.getBuiltIn(LutManager.LUT_PORTRA, 17);
        assertNotNull(first17);
        assertEquals(17, first17.size);
        org.junit.Assert.assertSame("Repeated getBuiltIn(17) should reuse cached instance", first17, second17);

        LutManager.Lut3D full33 = LutManager.getBuiltIn(LutManager.LUT_PORTRA, 33);
        assertNotNull(full33);
        assertEquals(33, full33.size);
        org.junit.Assert.assertSame(full33, LutManager.get(LutManager.LUT_PORTRA));

        org.junit.Assert.assertNull(LutManager.getBuiltIn(LutManager.LUT_NONE, 17));
        org.junit.Assert.assertNull(LutManager.getBuiltIn("Nonexistent LUT", 17));
        org.junit.Assert.assertNull(LutManager.getBuiltIn(null, 17));
    }

    @Test
    public void testReadCubeSizeHandlesBuiltInNoneAndNullContext() {
        assertEquals(33, LutManager.readCubeSize(null, LutManager.LUT_TEAL_ORANGE));
        assertEquals(0, LutManager.readCubeSize(null, LutManager.LUT_NONE));
        assertEquals(0, LutManager.readCubeSize(null, null));
        assertEquals(0, LutManager.readCubeSize(null, "custom_without_dir"));
    }

    @Test
    public void testBuiltInLutsPreserveEndpointsAndProduceDistinctGrades() {
        String[] profiles = {
                LutManager.LUT_PORTRA,
                LutManager.LUT_FUJI,
                LutManager.LUT_TEAL_ORANGE,
                LutManager.LUT_ANSEL_BW,
                LutManager.LUT_AMBER_70S,
                LutManager.LUT_BLEACH
        };
        int[][] midSwatches = new int[profiles.length][2];
        for (int i = 0; i < profiles.length; i++) {
            LutManager.Lut3D lut = LutManager.generateBuiltIn(profiles[i], 17);
            int black = lut.applyToRgb(0f, 0f, 0f, 1f);
            int white = lut.applyToRgb(1f, 1f, 1f, 1f);
            for (int shift : new int[]{16, 8, 0}) {
                int bCh = (black >> shift) & 0xff;
                int wCh = (white >> shift) & 0xff;
                assertTrue(profiles[i] + " black point must stay anchored near 0 (<= 6), got " + bCh, bCh <= 6);
                assertTrue(profiles[i] + " white point must stay luminous near 255 (>= 248), got " + wCh, wCh >= 248);
            }
            midSwatches[i][0] = lut.applyToRgb(0.58f, 0.44f, 0.34f, 1f);
            midSwatches[i][1] = lut.applyToRgb(0.28f, 0.56f, 0.24f, 1f);
        }
        for (int i = 0; i < profiles.length; i++) {
            for (int j = i + 1; j < profiles.length; j++) {
                int maxDiff = 0;
                for (int s = 0; s < 2; s++) {
                    for (int shift : new int[]{16, 8, 0}) {
                        maxDiff = Math.max(maxDiff, Math.abs(((midSwatches[i][s] >> shift) & 0xff)
                                - ((midSwatches[j][s] >> shift) & 0xff)));
                    }
                }
                assertTrue("Built-in LUTs " + profiles[i] + " and " + profiles[j] + " must be distinct (maxDiff=" + maxDiff + ")",
                        maxDiff >= 8);
            }
        }
    }

    @Test
    public void testExportLookAsCubeRoundTripMatchesRenderDisplayRgb() throws Exception {
        RawDeveloper.Look look = new RawDeveloper.Look();
        look.brain = AppSettings.BRAIN_KODACHROME;
        look.warmth = 0.25f;
        look.contrast = 1.18f;
        look.saturation = 1.06f;
        look.lutName = LutManager.LUT_PORTRA;
        look.lutIntensity = 0.6f;

        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        LutManager.exportLookAsCube(look, "Kodachrome Test \"Quoted\"", null, baos);

        byte[] bytes = baos.toByteArray();
        assertTrue(bytes.length > 100000);

        LutManager.Lut3D parsed = LutManager.parseCube(new ByteArrayInputStream(bytes), "Fallback");
        assertNotNull(parsed);
        assertEquals(33, parsed.size);
        assertEquals("Kodachrome Test 'Quoted'", parsed.title);

        RawDeveloper.PreparedLook prepared = RawDeveloper.preparePreviewCubeLook(look.copy(), null);
        float[][] probes = {
                {0f, 0f, 0f},
                {1f, 1f, 1f},
                {0.5f, 0.5f, 0.5f},
                {0.62f, 0.44f, 0.34f},
                {0.25f, 0.58f, 0.30f},
                {0.18f, 0.36f, 0.72f}
        };
        for (float[] p : probes) {
            int expected = RawDeveloper.renderDisplayRgb(p[0], p[1], p[2], prepared);
            int actual = parsed.applyToRgb(p[0], p[1], p[2], 1f);
            for (int shift : new int[]{16, 8, 0}) {
                int diff = Math.abs(((expected >> shift) & 0xff) - ((actual >> shift) & 0xff));
                assertTrue("Exported .cube mismatch at (" + p[0] + "," + p[1] + "," + p[2] + "): diff=" + diff,
                        diff <= 3);
            }
        }
    }
}
