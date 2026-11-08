package com.scope.camera;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.util.Log;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 35mm darkroom proof sheet generation engine for Lika Lens.
 * Renders contact proof sheets with Kodak Vision3 500T 35mm emulsion styling,
 * realistic sprocket holes, film borders, and gold select markers.
 */
public final class ContactSheetGenerator {
    private static final String TAG = "ContactSheetGenerator";

    private ContactSheetGenerator() {}

    public interface Callback {
        void onCompleted(Uri savedUri, String fileName);
        void onError(String message);
    }

    public static Bitmap decodeSampledBitmap(Context context, Uri uri, int reqW, int reqH) {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(in, null, options);
            int scale = Math.max(1, Math.min(options.outWidth / reqW, options.outHeight / reqH));
            options.inSampleSize = scale;
            options.inJustDecodeBounds = false;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            try (InputStream in2 = context.getContentResolver().openInputStream(uri)) {
                return BitmapFactory.decodeStream(in2, null, options);
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed to decode sampled bitmap for proof sheet: " + uri, t);
            return null;
        }
    }

    public static void generate(Activity activity, List<PhotoRepository.Photo> photos, Callback callback) {
        if (photos.isEmpty()) {
            if (callback != null) callback.onError("No photos to generate proof sheet");
            return;
        }

        new Thread(() -> {
            try {
                int count = Math.min(6, photos.size());
                int sheetW = 2400;
                int sheetH = 3000;
                Bitmap sheet = Bitmap.createBitmap(sheetW, sheetH, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(sheet);
                canvas.drawColor(0xFF090B0C);

                Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

                // Header
                textPaint.setColor(0xFFD7B574);
                textPaint.setTextSize(44f);
                textPaint.setLetterSpacing(0.08f);
                canvas.drawText("LIKA LENS ARCHIVE · 35MM DARKROOM PROOF SHEET", 80, 110, textPaint);

                String dateStr = new SimpleDateFormat("yyyy-MM-dd · HH:mm", Locale.US).format(new Date());
                textPaint.setColor(0xFF89948E);
                textPaint.setTextSize(26f);
                textPaint.setTypeface(android.graphics.Typeface.DEFAULT);
                canvas.drawText("DATE: " + dateStr + "   STOCK: KODAK VISION3 500T 35MM EMULSION", 80, 155, textPaint);

                // Gold rule
                Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                linePaint.setColor(0x66D7B574);
                linePaint.setStrokeWidth(3f);
                canvas.drawLine(80, 185, sheetW - 80, 185, linePaint);

                // 2 rows x 3 columns
                int cols = 3;
                int rows = 2;
                int gridMarginX = 80;
                int gridTop = 220;
                int availableW = sheetW - gridMarginX * 2;
                int colSpacing = 40;
                int frameW = (availableW - colSpacing * (cols - 1)) / cols;
                int frameH = Math.round(frameW * (2f / 3f));
                int rowHeightWithSprockets = frameH + 210;

                Paint sprocketPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                sprocketPaint.setColor(0xCCF0F2EB);

                Paint frameBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                frameBorderPaint.setStyle(Paint.Style.STROKE);
                frameBorderPaint.setStrokeWidth(4f);
                frameBorderPaint.setColor(0xFF1E2325);

                Paint leicaGoldMark = new Paint(Paint.ANTI_ALIAS_FLAG);
                leicaGoldMark.setColor(0xFFD7B574);
                leicaGoldMark.setStyle(Paint.Style.STROKE);
                leicaGoldMark.setStrokeWidth(6f);

                for (int r = 0; r < rows; r++) {
                    int rowTop = gridTop + r * rowHeightWithSprockets;
                    int sprockY1 = rowTop + 10;
                    int sprockW = 24, sprockH = 36, sprockR = 6;
                    for (int sx = gridMarginX; sx < sheetW - gridMarginX; sx += 48) {
                        canvas.drawRoundRect(new RectF(sx, sprockY1, sx + sprockW, sprockY1 + sprockH), sprockR, sprockR, sprocketPaint);
                    }

                    int photoY = sprockY1 + sprockH + 18;

                    for (int c = 0; c < cols; c++) {
                        int index = r * cols + c;
                        int photoX = gridMarginX + c * (frameW + colSpacing);

                        if (index < count) {
                            PhotoRepository.Photo p = photos.get(index);
                            Bitmap thumb = decodeSampledBitmap(activity, p.uri, frameW, frameH);
                            if (thumb != null) {
                                Rect src = new Rect(0, 0, thumb.getWidth(), thumb.getHeight());
                                Rect dst = new Rect(photoX, photoY, photoX + frameW, photoY + frameH);
                                canvas.drawBitmap(thumb, src, dst, null);
                                thumb.recycle();
                            } else {
                                Paint blank = new Paint();
                                blank.setColor(0xFF121516);
                                canvas.drawRect(photoX, photoY, photoX + frameW, photoY + frameH, blank);
                            }
                        } else {
                            Paint blank = new Paint();
                            blank.setColor(0xFF121516);
                            canvas.drawRect(photoX, photoY, photoX + frameW, photoY + frameH, blank);
                        }

                        canvas.drawRect(photoX, photoY, photoX + frameW, photoY + frameH, frameBorderPaint);

                        String frameNum = String.format(Locale.US, "► %02dA   KODAK SAFETY FILM", index + 1);
                        textPaint.setColor(0xFFD7B574);
                        textPaint.setTextSize(22f);
                        canvas.drawText(frameNum, photoX + 8, photoY + frameH + 30, textPaint);

                        if (index == 0) {
                            canvas.drawRect(photoX - 4, photoY - 4, photoX + frameW + 4, photoY + frameH + 4, leicaGoldMark);
                            textPaint.setTextSize(22f);
                            canvas.drawText("★ SELECT", photoX + frameW - 130, photoY + frameH + 30, textPaint);
                        }
                    }

                    int sprockY2 = photoY + frameH + 46;
                    for (int sx = gridMarginX; sx < sheetW - gridMarginX; sx += 48) {
                        canvas.drawRoundRect(new RectF(sx, sprockY2, sx + sprockW, sprockY2 + sprockH), sprockR, sprockR, sprocketPaint);
                    }
                }

                String fileName = "LIKA_PROOF_SHEET_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".jpg";
                Uri savedUri = PhotoRepository.saveBitmap(activity, sheet, fileName);
                sheet.recycle();

                if (callback != null) {
                    activity.runOnUiThread(() -> {
                        if (savedUri != null) {
                            callback.onCompleted(savedUri, fileName);
                        } else {
                            callback.onError("Could not save proof sheet");
                        }
                    });
                }
            } catch (Throwable t) {
                Log.e(TAG, "Error generating proof sheet", t);
                if (callback != null) {
                    activity.runOnUiThread(() -> callback.onError("Error generating proof sheet: " + t.getMessage()));
                }
            }
        }).start();
    }
}
