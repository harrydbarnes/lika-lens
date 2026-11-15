package com.scope.camera;

import android.media.ExifInterface;
import android.view.Surface;
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

    @Test public void quantizeClockwiseDeviceRotationApplies60DegreeHysteresis() {
        assertEquals(0, CaptureOrientation.quantizeClockwiseDeviceRotation(-1, -1));
        assertEquals(90, CaptureOrientation.quantizeClockwiseDeviceRotation(-1, 90));
        assertEquals(0, CaptureOrientation.quantizeClockwiseDeviceRotation(20, -1));
        assertEquals(90, CaptureOrientation.quantizeClockwiseDeviceRotation(85, -1));
        // Near the 45 deg boundary, previous 0 deg holds until >= 60 deg tilt.
        assertEquals(0, CaptureOrientation.quantizeClockwiseDeviceRotation(55, 0));
        assertEquals(90, CaptureOrientation.quantizeClockwiseDeviceRotation(60, 0));
        // Wrap-around across 360/0 keeps 270 deg until tilted >= 60 deg past 270 (i.e. 330+).
        assertEquals(270, CaptureOrientation.quantizeClockwiseDeviceRotation(325, 270));
        assertEquals(0, CaptureOrientation.quantizeClockwiseDeviceRotation(330, 270));
    }

    @Test public void displayRotationMapsCounterClockwiseSurfaceRotationToClockwiseDeviceTilt() {
        assertEquals(0, CaptureOrientation.displayRotationToClockwiseDevice(Surface.ROTATION_0));
        assertEquals(270, CaptureOrientation.displayRotationToClockwiseDevice(Surface.ROTATION_90));
        assertEquals(180, CaptureOrientation.displayRotationToClockwiseDevice(Surface.ROTATION_180));
        assertEquals(90, CaptureOrientation.displayRotationToClockwiseDevice(Surface.ROTATION_270));
    }

    @Test public void calculateCaptureOrientationHandlesRearAndFrontAcrossAllTilts() {
        // Rear camera with typical 90 deg sensor orientation
        assertEquals(90, CaptureOrientation.calculateCaptureOrientation(90, false, 0));
        assertEquals(180, CaptureOrientation.calculateCaptureOrientation(90, false, 90));
        assertEquals(270, CaptureOrientation.calculateCaptureOrientation(90, false, 180));
        assertEquals(0, CaptureOrientation.calculateCaptureOrientation(90, false, 270));

        // Front camera with typical 270 deg sensor orientation
        assertEquals(270, CaptureOrientation.calculateCaptureOrientation(270, true, 0));
        assertEquals(180, CaptureOrientation.calculateCaptureOrientation(270, true, 90));
        assertEquals(90, CaptureOrientation.calculateCaptureOrientation(270, true, 180));
        assertEquals(0, CaptureOrientation.calculateCaptureOrientation(270, true, 270));
    }
}
