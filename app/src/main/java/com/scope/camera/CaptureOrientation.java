package com.scope.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.view.Surface;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

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

    static int quantizeClockwiseDeviceRotation(int rawDegrees, int previousQuantized) {
        if (rawDegrees < 0) {
            return previousQuantized >= 0 ? normalizeCardinal(previousQuantized) : 0;
        }
        int normalized = ((rawDegrees % 360) + 360) % 360;
        if (previousQuantized < 0) {
            return ((normalized + 45) / 90 * 90) % 360;
        }
        int prev = normalizeCardinal(previousQuantized);
        int delta = Math.abs(normalized - prev);
        if (delta > 180) delta = 360 - delta;
        if (delta < 60) {
            return prev;
        }
        return ((normalized + 45) / 90 * 90) % 360;
    }

    private static int normalizeCardinal(int degrees) {
        int d = ((degrees % 360) + 360) % 360;
        return ((d + 45) / 90 * 90) % 360;
    }

    static int displayRotationToClockwiseDevice(int surfaceRotation) {
        switch (surfaceRotation) {
            case Surface.ROTATION_90: return 270;
            case Surface.ROTATION_180: return 180;
            case Surface.ROTATION_270: return 90;
            default: return 0;
        }
    }

    static int calculateCaptureOrientation(int sensorOrientation, boolean isFront, int clockwiseDeviceDegrees) {
        int sensor = ((sensorOrientation % 360) + 360) % 360;
        int device = normalizeCardinal(clockwiseDeviceDegrees);
        return isFront
                ? (sensor - device + 360) % 360
                : (sensor + device) % 360;
    }

    static Bitmap applyExifOrientation(Bitmap source, int exifOrientation) {
        if (source == null) return null;
        int degrees = rotation(exifOrientation);
        boolean mirror = mirrored(exifOrientation);
        if (degrees == 0 && !mirror) return source;
        Matrix matrix = new Matrix();
        if (mirror) matrix.setScale(-1f, 1f);
        matrix.postRotate(degrees);
        Bitmap rotated = Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
        if (rotated != source) source.recycle();
        return rotated;
    }

    static Bitmap orientBitmap(Context context, Uri uri, Bitmap source) {
        if (source == null || context == null || uri == null) return source;
        int orientation = ExifInterface.ORIENTATION_NORMAL;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in != null) {
                orientation = new ExifInterface(in).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            }
        } catch (IOException ignored) { }
        return applyExifOrientation(source, orientation);
    }

    static Bitmap orientJpeg(Bitmap source, byte[] bytes, int fallbackDegrees) {
        int orientation = ExifInterface.ORIENTATION_UNDEFINED;
        try {
            orientation = new ExifInterface(new ByteArrayInputStream(bytes)).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
        } catch (IOException ignored) { }
        // NORMAL is explicit: the HAL may already have physically rotated the JPEG pixels.
        if (orientation == ExifInterface.ORIENTATION_UNDEFINED) orientation = toExif(fallbackDegrees);
        return applyExifOrientation(source, orientation);
    }
}
