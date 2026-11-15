package com.scope.camera;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Builds and populates the bottom EXIF metadata inspection card for PhotoViewerActivity.
 */
public final class PhotoViewerMetadataSheet {
    private static final String TAG = "PhotoViewerMetadataSheet";
    private static final int ACCENT = 0xffd7b574;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "lika-exif-io");
        t.setDaemon(true);
        return t;
    });
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private PhotoViewerMetadataSheet() {}

    public static LinearLayout build(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        int p16 = Math.round(16 * density);
        int p14 = Math.round(14 * density);
        int p6 = Math.round(6 * density);
        int p3 = Math.round(3 * density);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(p16, p14, p16, p14);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xdd121617);
        bg.setCornerRadius(14 * density);
        bg.setStroke(Math.round(1 * density), 0x99d7b574);
        card.setBackground(bg);

        TextView header = new TextView(context);
        header.setText("FRAME DETAILS & EXIF");
        header.setTextSize(9);
        header.setTextColor(ACCENT);
        header.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.setLetterSpacing(.12f);
        card.addView(header);

        TextView dataLine1 = new TextView(context);
        dataLine1.setText("READING METADATA…");
        dataLine1.setTextSize(9);
        dataLine1.setTextColor(0xfff0f2eb);
        dataLine1.setPadding(0, p6, 0, 0);
        dataLine1.setTag("exif_line1");
        card.addView(dataLine1);

        TextView dataLine2 = new TextView(context);
        dataLine2.setText("");
        dataLine2.setTextSize(8);
        dataLine2.setTextColor(0xff9ba59e);
        dataLine2.setPadding(0, p3, 0, 0);
        dataLine2.setTag("exif_line2");
        card.addView(dataLine2);

        return card;
    }

    public static void populate(Context context, View card, Uri photoUri, String archiveKey, int imageWidth, int imageHeight) {
        if (context == null || card == null || photoUri == null) return;
        final TextView line1 = card.findViewWithTag("exif_line1");
        final TextView line2 = card.findViewWithTag("exif_line2");
        if (line1 == null || line2 == null) return;

        card.setTag(photoUri);
        line1.setText("READING METADATA…");
        line2.setText("");
        final Context appContext = context.getApplicationContext();

        IO.execute(() -> {
            String shutter = "1/120s";
            String iso = "ISO 100";
            String focal = "Wide";
            String dimensions = imageWidth > 0 ? (imageWidth + " × " + imageHeight) : "";

            try (InputStream stream = appContext.getContentResolver().openInputStream(photoUri)) {
                if (stream != null) {
                    ExifInterface exif = new ExifInterface(stream);
                    String expTime = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME);
                    if (expTime != null) {
                        try {
                            double sec = Double.parseDouble(expTime);
                            shutter = formatShutterSpeed(sec);
                        } catch (Exception ignored) { shutter = expTime + "s"; }
                    }
                    String isoVal = exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS);
                    if (isoVal == null || isoVal.isEmpty()) isoVal = exif.getAttribute("PhotographicSensitivity");
                    if (isoVal != null && !isoVal.isEmpty()) iso = "ISO " + isoVal;
                    String fLen = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH);
                    if (fLen != null && !fLen.isEmpty()) focal = fLen + "mm";
                }
            } catch (Throwable t) {
                Log.w(TAG, "Failed to parse EXIF metadata", t);
            }

            final boolean hasRaw = archiveKey != null && RawArchive.exists(appContext, archiveKey);
            String mime = null;
            try {
                mime = appContext.getContentResolver().getType(photoUri);
            } catch (Throwable ignored) {
            }
            final boolean isGif = (mime != null && "image/gif".equalsIgnoreCase(mime))
                    || photoUri.toString().toLowerCase(Locale.US).endsWith(".gif");
            final String text1 = shutter + "  ·  " + iso + "  ·  " + focal + (dimensions.isEmpty() ? "" : "  ·  " + dimensions);
            final String text2 = hasRaw
                    ? "EDITABLE RAW ARCHIVE AVAILABLE  ·  NON-DESTRUCTIVE RE-EDIT"
                    : (isGif
                            ? "ANIMATED GIF  ·  EXPORTED LOOP"
                            : "PROCESSED JPEG  ·  NO EDITABLE RAW CACHE (CLEARED OR UNAVAILABLE)");

            MAIN.post(() -> {
                if (!photoUri.equals(card.getTag())) return;
                line1.setText(text1);
                line2.setText(text2);
            });
        });
    }

    public static String formatShutterSpeed(double sec) {
        if (sec <= 0) return "1/120s";
        return sec < 1.0 ? String.format(Locale.US, "1/%ds", Math.round(1.0 / sec)) : String.format(Locale.US, "%.1fs", sec);
    }
}
