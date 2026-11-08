package com.scope.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Media decoding, EXIF orientation correction, scaling, and video frame extraction
 * utilities for GIF Studio.
 */
public final class GifMediaHelper {
    private static final String TAG = "GifMediaHelper";

    private GifMediaHelper() {}

    public interface VideoMetadataListener {
        void onMetadata(long durationMs, int rotation);
    }

    public static List<Bitmap> extractVideoFrames(
            Context context,
            Uri uri,
            int maxDimension,
            int fps,
            int startSec,
            int durationSec,
            VideoMetadataListener listener
    ) {
        List<Bitmap> frames = new ArrayList<>();
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, uri);
            String durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            long totalMs = durStr != null ? Long.parseLong(durStr) : 0;

            String rotStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
            int videoRotation = rotStr != null ? Integer.parseInt(rotStr) : 0;

            if (listener != null) {
                listener.onMetadata(totalMs, videoRotation);
            }

            long startMs = startSec * 1000L;
            long endMs = durationSec > 0 ? Math.min(totalMs, startMs + durationSec * 1000L) : totalMs;
            if (endMs <= startMs) endMs = Math.min(totalMs, startMs + 3000L);

            int intervalMs = Math.max(33, 1000 / fps);
            int maxFrames = 60; // Safe upper bound for memory

            for (long timeMs = startMs; timeMs < endMs && frames.size() < maxFrames; timeMs += intervalMs) {
                long timeUs = timeMs * 1000L;
                Bitmap frame = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    try {
                        frame = retriever.getScaledFrameAtTime(
                                timeUs,
                                MediaMetadataRetriever.OPTION_CLOSEST,
                                maxDimension,
                                maxDimension
                        );
                    } catch (Throwable t) {
                        Log.w(TAG, "getScaledFrameAtTime failed at " + timeUs, t);
                    }
                }
                if (frame == null) {
                    frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST);
                    if (frame != null) {
                        frame = scaleDown(frame, maxDimension);
                    }
                }
                if (frame != null) {
                    if (videoRotation != 0) {
                        frame = rotateBitmap(frame, videoRotation);
                    }
                    frames.add(frame);
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error extracting video frames from: " + uri, t);
        } finally {
            try {
                retriever.release();
            } catch (Throwable t) {
                Log.w(TAG, "Failed to release retriever", t);
            }
        }
        return frames;
    }

    public static Bitmap decodeAndScaleBitmap(Context context, Uri uri, int maxDimension) {
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is == null) return null;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(is, null, opts);

            int sample = 1;
            while ((opts.outWidth / sample) > maxDimension * 2 || (opts.outHeight / sample) > maxDimension * 2) {
                sample *= 2;
            }

            opts.inJustDecodeBounds = false;
            opts.inSampleSize = sample;
            try (InputStream is2 = context.getContentResolver().openInputStream(uri)) {
                Bitmap bmp = BitmapFactory.decodeStream(is2, null, opts);
                if (bmp == null) return null;

                int orientation = getExifOrientation(context, uri);
                if (orientation != 0) {
                    bmp = rotateBitmap(bmp, orientation);
                }
                return scaleDown(bmp, maxDimension);
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed to decode and scale bitmap: " + uri, t);
            return null;
        }
    }

    public static Bitmap scaleDown(Bitmap src, int maxDimension) {
        if (src == null) return null;
        int w = src.getWidth();
        int h = src.getHeight();
        if (w <= maxDimension && h <= maxDimension) return src;

        float ratio = Math.min((float) maxDimension / w, (float) maxDimension / h);
        int dstW = Math.max(1, Math.round(w * ratio));
        int dstH = Math.max(1, Math.round(h * ratio));

        Bitmap scaled = Bitmap.createScaledBitmap(src, dstW, dstH, true);
        if (scaled != src) src.recycle();
        return scaled;
    }

    public static int getExifOrientation(Context context, Uri uri) {
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is == null) return 0;
            ExifInterface exif = new ExifInterface(is);
            int tag = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (tag) {
                case ExifInterface.ORIENTATION_ROTATE_90: return 90;
                case ExifInterface.ORIENTATION_ROTATE_180: return 180;
                case ExifInterface.ORIENTATION_ROTATE_270: return 270;
                default: return 0;
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed to read EXIF orientation: " + uri, t);
            return 0;
        }
    }

    public static Bitmap rotateBitmap(Bitmap src, int degrees) {
        if (degrees == 0 || src == null) return src;
        Matrix m = new Matrix();
        m.postRotate(degrees);
        Bitmap rotated = Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
        if (rotated != src) src.recycle();
        return rotated;
    }

    public static Bitmap decodeSampledBitmap(Context context, Uri uri, int reqW, int reqH) {
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is == null) return null;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = 4;
            return BitmapFactory.decodeStream(is, null, opts);
        } catch (Throwable t) {
            Log.w(TAG, "Failed to decode sampled preview: " + uri, t);
            return null;
        }
    }

    public static List<Bitmap> loadPreviewFrames(
            Context context,
            boolean isVideoMode,
            Uri videoUri,
            List<Uri> photoUris,
            int fps,
            int startSec,
            int durationSec,
            String lutProfile,
            boolean bounceMode,
            VideoMetadataListener listener
    ) {
        List<Bitmap> loaded = new ArrayList<>();
        try {
            if (isVideoMode) {
                if (videoUri != null) {
                    loaded.addAll(extractVideoFrames(context, videoUri, 320, fps, startSec, durationSec, listener));
                }
            } else {
                for (Uri uri : photoUris) {
                    Bitmap bmp = decodeAndScaleBitmap(context, uri, 320);
                    if (bmp != null) loaded.add(bmp);
                }
            }

            if (!"NONE".equals(lutProfile)) {
                LutManager.Lut3D lut = LutManager.generateBuiltIn(lutProfile, 17);
                for (int i = 0; i < loaded.size(); i++) {
                    Bitmap orig = loaded.get(i);
                    Bitmap graded = LutManager.applyToBitmap(orig, lut, 1.0f);
                    if (graded != null && graded != orig) {
                        orig.recycle();
                        loaded.set(i, graded);
                    }
                }
            }

            if (bounceMode && loaded.size() > 2) {
                List<Bitmap> bounced = new ArrayList<>(loaded);
                for (int i = loaded.size() - 2; i > 0; i--) {
                    bounced.add(loaded.get(i));
                }
                loaded = bounced;
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed to load preview frames", t);
        }
        return loaded;
    }
}
