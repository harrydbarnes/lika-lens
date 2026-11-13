package com.scope.camera;

import android.media.ExifInterface;
import org.junit.Test;
import static org.junit.Assert.*;

public class CaptureOrientationTest {
    @Test public void mapsCameraDegreesToTiffValuesRatherThanWritingDegrees() {
        assertEquals(ExifInterface.ORIENTATION_NORMAL, CaptureOrientation.toExif(0));
        assertEquals(ExifInterface.ORIENTATION_ROTATE_90, CaptureOrientation.toExif(90));
        assertEquals(ExifInterface.ORIENTATION_ROTATE_180, CaptureOrientation.toExif(180));
        assertEquals(ExifInterface.ORIENTATION_ROTATE_270, CaptureOrientation.toExif(270));
        assertEquals(ExifInterface.ORIENTATION_ROTATE_270, CaptureOrientation.toExif(-90));
        assertEquals(ExifInterface.ORIENTATION_ROTATE_90, CaptureOrientation.toExif(450));
    }

    @Test public void allEightExifTransformsMapAnAsymmetricCornerCorrectly() {
        // Coordinates around the image centre: x=2,y=1, so transpose and transverse differ.
        int[][] expected = {{2,1}, {-2,1}, {-2,-1}, {2,-1}, {1,2}, {-1,2}, {-1,-2}, {1,-2}};
        for (int exif = 1; exif <= 8; exif++) {
            int x = CaptureOrientation.mirrored(exif) ? -2 : 2, y = 1;
            int turns = CaptureOrientation.rotation(exif) / 90;
            for (int i = 0; i < turns; i++) { int oldX = x; x = -y; y = oldX; }
            assertArrayEquals("EXIF " + exif, expected[exif - 1], new int[]{x,y});
        }
    }
}
