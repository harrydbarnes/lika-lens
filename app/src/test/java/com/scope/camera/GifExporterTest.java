package com.scope.camera;

import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for GifExporter parameter structures and state models.
 */
public class GifExporterTest {

    @Test
    public void testParamsDefaultInitialization() {
        GifExporter.Params params = new GifExporter.Params();
        assertFalse(params.isVideoMode);
        assertFalse(params.boomerang);
        assertEquals(0, params.targetRes);
        assertEquals(0, params.targetFps);
    }

    @Test
    public void testParamsCustomConfiguration() {
        GifExporter.Params params = new GifExporter.Params();
        params.isVideoMode = true;
        params.targetRes = 480;
        params.targetFps = 15;
        params.boomerang = true;
        params.lutProfile = "PORTRA";
        params.videoStartSec = 2;
        params.videoDurationSec = 5;
        params.photoUris = new ArrayList<>();

        assertTrue(params.isVideoMode);
        assertTrue(params.boomerang);
        assertEquals(480, params.targetRes);
        assertEquals(15, params.targetFps);
        assertEquals("PORTRA", params.lutProfile);
        assertEquals(2, params.videoStartSec);
        assertEquals(5, params.videoDurationSec);
        assertNotNull(params.photoUris);
        assertEquals(0, params.photoUris.size());
    }
}
