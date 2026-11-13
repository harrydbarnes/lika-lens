package com.scope.camera;

import android.hardware.camera2.CameraCharacteristics;

/** Settings frozen when a still request is submitted, before asynchronous camera callbacks. */
final class CaptureRecipe {
    final CameraCharacteristics characteristics;
    final int orientation;
    final RawDeveloper.Look look;
    final String dngPolicy;
    final String format;
    final boolean keepRaw;
    final float previewMidTarget;

    CaptureRecipe(CameraCharacteristics characteristics, int orientation, RawDeveloper.Look look,
                  String dngPolicy, String format, boolean keepRaw) {
        this(characteristics, orientation, look, dngPolicy, format, keepRaw, 0f);
    }

    CaptureRecipe(CameraCharacteristics characteristics, int orientation, RawDeveloper.Look look,
                  String dngPolicy, String format, boolean keepRaw, float previewMidTarget) {
        this.previewMidTarget = previewMidTarget;
        this.characteristics = characteristics;
        this.orientation = orientation;
        this.look = look.copy();
        this.dngPolicy = dngPolicy;
        this.format = format;
        this.keepRaw = keepRaw;
    }
}
