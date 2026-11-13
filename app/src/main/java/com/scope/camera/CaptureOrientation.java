package com.scope.camera;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.media.ExifInterface;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/** Keeps TIFF orientation tags separate from the clockwise degree values used by Camera2. */
final class CaptureOrientation {
    private CaptureOrientation() { }

    static int toExif(int degrees) {
        switch (((degrees % 360) + 360) % 360) {
            case 90: return ExifInterface.ORIENTATION_ROTATE_90;
            case 180: return ExifInterface.ORIENTATION_ROTATE_180;
            case 270: return ExifInterface.ORIENTATION_ROTATE_270;
            default: return ExifInterface.ORIENTATION_NORMAL;
        }
    }

    static int rotation(int exif) {
        switch (exif) {
            case ExifInterface.ORIENTATION_ROTATE_90:
            case ExifInterface.ORIENTATION_TRANSVERSE: return 90;
            case ExifInterface.ORIENTATION_ROTATE_180:
            case ExifInterface.ORIENTATION_FLIP_VERTICAL: return 180;
            case ExifInterface.ORIENTATION_ROTATE_270:
            case ExifInterface.ORIENTATION_TRANSPOSE: return 270;
            default: return 0;
        }
    }

    static boolean mirrored(int exif) {
        return exif == ExifInterface.ORIENTATION_FLIP_HORIZONTAL
                || exif == ExifInterface.ORIENTATION_FLIP_VERTICAL
                || exif == ExifInterface.ORIENTATION_TRANSPOSE
                || exif == ExifInterface.ORIENTATION_TRANSVERSE;
    }

    static Bitmap orientJpeg(Bitmap source, byte[] bytes, int fallbackDegrees) {
        int orientation = ExifInterface.ORIENTATION_UNDEFINED;
        try {
            orientation = new ExifInterface(new ByteArrayInputStream(bytes)).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
        } catch (IOException ignored) { }
        // NORMAL is explicit: the HAL may already have physically rotated the JPEG pixels.
        if (orientation == ExifInterface.ORIENTATION_UNDEFINED) orientation = toExif(fallbackDegrees);
        int degrees = rotation(orientation);
        if (degrees == 0 && !mirrored(orientation)) return source;
        Matrix matrix = new Matrix();
        if (mirrored(orientation)) matrix.setScale(-1f, 1f);
        matrix.postRotate(degrees);
        Bitmap rotated = Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
        if (rotated != source) source.recycle();
        return rotated;
    }
}
