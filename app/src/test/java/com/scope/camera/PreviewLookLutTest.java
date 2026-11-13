package com.scope.camera;

import org.junit.Test;
import static org.junit.Assert.*;

public class PreviewLookLutTest {
    private static LutManager.Lut3D asLut(PreviewLookLut preview) {
        float[] rgb = new float[preview.pixels.length * 3];
        for (int i = 0; i < preview.pixels.length; i++) {
            int c = preview.pixels[i];
            rgb[i*3] = ((c >> 16) & 255) / 255f;
            rgb[i*3+1] = ((c >> 8) & 255) / 255f;
            rgb[i*3+2] = (c & 255) / 255f;
        }
        return new LutManager.Lut3D("preview", "preview", PreviewLookLut.SIZE, rgb, false);
    }

    @Test public void interpolatedPreviewTracksTheExportAcrossBrainsAndOffGridColours() {
        float[][] samples = {{.07f,.055f,.035f}, {.53f,.42f,.31f}, {.23f,.49f,.19f},
                {.81f,.16f,.12f}, {.21f,.32f,.72f}, {.87f,.85f,.79f}, {.36f,.36f,.36f}};
        for (String brain : AppSettings.BRAIN_CHOICES) {
            RawDeveloper.Look look = new RawDeveloper.Look();
            look.brain = brain;
            look.exposureEv = .3f;
            look.warmth = .1f;
            look.highlights = .8f;
            RawDeveloper.PreparedLook prepared = RawDeveloper.prepareDisplayLook(look);
            LutManager.Lut3D sampled = asLut(PreviewLookLut.build(look));
            for (float[] sample : samples) {
                int expected = RawDeveloper.renderDisplayRgb(sample[0], sample[1], sample[2], prepared);
                int actual = sampled.applyToRgb(sample[0], sample[1], sample[2], 1f);
                assertChannelsClose(brain, expected, actual, 8);
            }
        }
    }

    @Test public void lookupHonoursLutIntensityAndWarmIlluminantMetadata() {
        RawDeveloper.Look look = new RawDeveloper.Look();
        look.lutName = LutManager.LUT_TEAL_ORANGE;
        RawArchive.Frame warm = new RawArchive.Frame();
        warm.wbGains = new float[]{1f,1f,1f,2.8f};
        int index = 17 + PreviewLookLut.SIZE * (14 + PreviewLookLut.SIZE * 11);
        int[] colours = new int[3];
        float[] intensities = {0f, .5f, 1f};
        for (int i = 0; i < intensities.length; i++) {
            look.lutIntensity = intensities[i];
            PreviewLookLut lut = PreviewLookLut.build(look, warm);
            colours[i] = lut.pixels[index];
            int direct = RawDeveloper.renderDisplayRgb(17f/32f,14f/32f,11f/32f,
                    RawDeveloper.prepareDisplayLook(look, warm));
            assertEquals(direct, colours[i]);
        }
        assertNotEquals(colours[0], colours[1]);
        assertNotEquals(colours[1], colours[2]);
        assertNotEquals(PreviewLookLut.build(look).pixels[index], PreviewLookLut.build(look,warm).pixels[index]);
    }

    private static void assertChannelsClose(String label, int expected, int actual, int tolerance) {
        for (int shift : new int[]{16,8,0}) assertTrue(label + " channel " + shift,
                Math.abs(((expected >> shift) & 255) - ((actual >> shift) & 255)) <= tolerance);
    }
}
