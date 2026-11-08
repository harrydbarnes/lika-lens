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
}
