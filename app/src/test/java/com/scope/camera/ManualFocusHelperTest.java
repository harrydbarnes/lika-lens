package com.scope.camera;

import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ManualFocusHelperTest {

    @Test
    public void manualFocusSupportRequiresPositiveRangeAndAfOff() {
        assertFalse(ManualFocusHelper.isManualFocusSupported(null, new int[]{CaptureRequest.CONTROL_AF_MODE_OFF}));
        assertFalse(ManualFocusHelper.isManualFocusSupported(0f, new int[]{CaptureRequest.CONTROL_AF_MODE_OFF}));
        assertFalse(ManualFocusHelper.isManualFocusSupported(-1f, new int[]{CaptureRequest.CONTROL_AF_MODE_OFF}));
        assertFalse(ManualFocusHelper.isManualFocusSupported(10f, null));
        assertFalse(ManualFocusHelper.isManualFocusSupported(10f, new int[]{CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE}));

        assertTrue(ManualFocusHelper.isManualFocusSupported(10f, new int[]{
                CaptureRequest.CONTROL_AF_MODE_OFF,
                CaptureRequest.CONTROL_AF_MODE_AUTO,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
        }));
    }

    @Test
    public void sliderEndpointsAndRoundTripPreserveBounds() {
        float minDist = 10f; // 10 cm closest focus
        assertEquals(0f, ManualFocusHelper.progressToDiopters(ManualFocusHelper.SLIDER_MAX, minDist), 0.0001f);
        assertEquals(minDist, ManualFocusHelper.progressToDiopters(0, minDist), 0.0001f);
        assertEquals(ManualFocusHelper.SLIDER_MAX, ManualFocusHelper.dioptersToProgress(0f, minDist));
        assertEquals(0, ManualFocusHelper.dioptersToProgress(minDist, minDist));

        for (int p = 0; p <= ManualFocusHelper.SLIDER_MAX; p += 50) {
            float d = ManualFocusHelper.progressToDiopters(p, minDist);
            assertTrue(d >= 0f && d <= minDist);
            int roundTrip = ManualFocusHelper.dioptersToProgress(d, minDist);
            assertTrue("Progress round-trip delta <= 2 for p=" + p + " got " + roundTrip,
                    Math.abs(p - roundTrip) <= 2);
        }
    }

    @Test
    public void formatFocusDistanceHandlesFixedInfinityCalibratedAndUncalibrated() {
        assertEquals("FIXED", ManualFocusHelper.formatFocusDistance(2f, 0f, CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION_CALIBRATED));
        assertEquals("∞", ManualFocusHelper.formatFocusDistance(0f, 10f, CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION_CALIBRATED));
        assertEquals("∞", ManualFocusHelper.formatFocusDistance(0.04f, 10f, CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION_UNCALIBRATED));

        assertEquals("1.00 m", ManualFocusHelper.formatFocusDistance(1.0f, 10f, CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION_CALIBRATED));
        assertEquals("25 cm", ManualFocusHelper.formatFocusDistance(4.0f, 10f, CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION_APPROXIMATE));
        assertEquals("2.50 D", ManualFocusHelper.formatFocusDistance(2.5f, 10f, CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION_UNCALIBRATED));
    }
}
