package com.scope.camera;

import android.hardware.camera2.CameraCharacteristics;

/** Settings frozen when a still request is submitted, before asynchronous camera callbacks. */
final class CaptureRecipe {
    final CameraCharacteristics characteristics;
    final int orientation;
    final RawDeveloper.Look look;
    final String photoOutput;
    final float previewMidTarget;

    CaptureRecipe(CameraCharacteristics characteristics, int orientation, RawDeveloper.Look look,
                  String photoOutput) {
        this(characteristics, orientation, look, photoOutput, 0f);
    }

    CaptureRecipe(CameraCharacteristics characteristics, int orientation, RawDeveloper.Look look,
                  String photoOutput, float previewMidTarget) {
        this.previewMidTarget = previewMidTarget;
        this.characteristics = characteristics;
        this.orientation = orientation;
        this.look = look.copy();
        this.photoOutput = photoOutput != null ? photoOutput : AppSettings.PHOTO_OUTPUT_JPEG_ONLY;
    }
}
