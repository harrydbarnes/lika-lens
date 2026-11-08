package com.scope.camera;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class PhotoViewerMetadataSheetTest {

    @Test
    public void testFormatShutterSpeed() {
        assertEquals("1/120s", PhotoViewerMetadataSheet.formatShutterSpeed(0));
        assertEquals("1/1000s", PhotoViewerMetadataSheet.formatShutterSpeed(1.0 / 1000.0));
        assertEquals("1/250s", PhotoViewerMetadataSheet.formatShutterSpeed(1.0 / 250.0));
        assertEquals("1/60s", PhotoViewerMetadataSheet.formatShutterSpeed(1.0 / 60.0));
        assertEquals("1.5s", PhotoViewerMetadataSheet.formatShutterSpeed(1.5));
        assertEquals("2.0s", PhotoViewerMetadataSheet.formatShutterSpeed(2.0));
    }
}
