package com.scope.camera;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class PhotoViewerGestureHelperTest {

    @Test
    public void testComputeFitScale() {
        assertEquals(1.0f, PhotoViewerGestureHelper.computeFitScale(0, 0, 1000, 1000), 0.001f);
        assertEquals(1.0f, PhotoViewerGestureHelper.computeFitScale(1000, 1000, 0, 0), 0.001f);

        // Aspect fit landscape image into portrait screen:
        // 4000x3000 into 1080x2400
        // Scale is min(1080/4000, 2400/3000) = min(0.27, 0.8) = 0.27
        float scale = PhotoViewerGestureHelper.computeFitScale(4000, 3000, 1080, 2400);
        assertEquals(0.27f, scale, 0.001f);

        // Aspect fit portrait image into landscape screen:
        // 3000x4000 into 2400x1080
        // Scale is min(2400/3000, 1080/4000) = min(0.8, 0.27) = 0.27
        scale = PhotoViewerGestureHelper.computeFitScale(3000, 4000, 2400, 1080);
        assertEquals(0.27f, scale, 0.001f);
    }
}
