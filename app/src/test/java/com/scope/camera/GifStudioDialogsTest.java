package com.scope.camera;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Unit tests for GifStudioDialogs models and ProgressHandle contracts.
 */
public class GifStudioDialogsTest {

    @Test
    public void testProgressHandleNullSafety() {
        GifStudioDialogs.ProgressHandle handle = new GifStudioDialogs.ProgressHandle(null, null, null);
        assertNotNull(handle);
        assertNull(handle.dialog);
        assertNull(handle.statusView);
        assertNull(handle.progressBar);

        // Verify null-safe method invocations
        handle.update("Rendering frames…", 50);
        handle.dismiss();
    }
}
