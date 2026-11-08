package com.scope.camera;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Date-grouped grid of Lika Lens renders with Android's document picker for imports. */
public class GalleryActivity extends Activity {
    private static final int BG = 0xff0b0d0e;
    private static final int IMPORT_REQUEST = 50;
    private LinearLayout list;
    private TextView countView;
    private ScrollView scroll;
    private int savedScrollY;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        build();
    }

    @Override protected void onResume() {
        super.onResume();
        loadPhotos();
    }

    @Override protected void onPause() {
        if (scroll != null) savedScrollY = scroll.getScrollY();
        super.onPause();
    }

    private void hapticClick(View v) {
        if (v != null && AppSettings.getBoolean(this, AppSettings.HAPTICS, true)) {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
    }

    private void build() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(BG);
        page.setFitsSystemWindows(true);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(12), dp(16), dp(12));
        TextView back = text("‹", 29, 0xffedf0e9, false);
        back.setGravity(Gravity.CENTER);
        header.addView(back, new LinearLayout.LayoutParams(dp(34), dp(40)));
        back.setOnClickListener(v -> { hapticClick(v); finishAfterTransition(); });

        LinearLayout title = new LinearLayout(this);
        title.setOrientation(LinearLayout.VERTICAL);
        title.setPadding(dp(8), 0, 0, 0);
        title.addView(text("LIBRARY", 14, 0xfff0f2eb, true));
        countView = text("0 PHOTOS", 8, 0xff89948e, false);
        countView.setPadding(0, dp(3), 0, 0);
        title.addView(countView);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        TextView pasteButton = pill("PASTE", false);
        pasteButton.setPadding(dp(8), 0, dp(8), 0);
        LinearLayout.LayoutParams pasteLp = new LinearLayout.LayoutParams(-2, dp(30));
        pasteLp.leftMargin = dp(4);
        header.addView(pasteButton, pasteLp);
        pasteButton.setOnClickListener(v -> { hapticClick(v); showBatchPasteLookDialog(); });

        TextView contactSheetButton = pill("PROOF SHEET", false);
        contactSheetButton.setPadding(dp(8), 0, dp(8), 0);
        LinearLayout.LayoutParams sheetLp = new LinearLayout.LayoutParams(-2, dp(30));
        sheetLp.leftMargin = dp(4);
        header.addView(contactSheetButton, sheetLp);
        contactSheetButton.setOnClickListener(v -> { hapticClick(v); generateContactSheet(); });

        TextView importButton = pill("IMPORT", true);
        importButton.setPadding(dp(8), 0, dp(8), 0);
        LinearLayout.LayoutParams importLp = new LinearLayout.LayoutParams(-2, dp(30));
        importLp.leftMargin = dp(4);
        header.addView(importButton, importLp);
        importButton.setOnClickListener(v -> { hapticClick(v); chooseImport(); });
        page.addView(header);

        scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(14), dp(3), dp(14), dp(25));
        scroll.addView(list);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(page);
    }

    private void loadPhotos() {
        if (list == null) return;
        list.removeAllViews();
        List<PhotoRepository.Photo> photos = PhotoRepository.all(this);
        countView.setText(String.format(Locale.US, "%02d PHOTOS", photos.size()));
        if (photos.isEmpty()) {
            LinearLayout empty = new LinearLayout(this);
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(28), dp(82), dp(28), dp(40));
            TextView title = text("NO FRAMES YET", 11, 0xffd8ded6, true);
            title.setGravity(Gravity.CENTER);
            empty.addView(title);
            TextView hint = text("Capture a frame or import an image to start your library.", 10, 0xff89948e, false);
            hint.setGravity(Gravity.CENTER);
            hint.setPadding(0, dp(10), 0, 0);
            empty.addView(hint);
            list.addView(empty);
            scroll.post(() -> scroll.scrollTo(0, savedScrollY));
            return;
        }

        String currentDay = "";
        LinearLayout row = null;
        for (PhotoRepository.Photo photo : photos) {
            String day = new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(new Date(photo.timestampMillis));
            if (!day.equals(currentDay)) {
                completeRow(row);
                currentDay = day;
                TextView heading = text(day.toUpperCase(Locale.getDefault()), 8, 0xffbbc1ba, true);
                heading.setLetterSpacing(.12f);
                LinearLayout.LayoutParams headingLp = new LinearLayout.LayoutParams(-1, dp(30));
                headingLp.topMargin = dp(4);
                list.addView(heading, headingLp);
                row = null;
            }
            if (row == null || row.getChildCount() == 3) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, dp(112));
                rowLp.bottomMargin = dp(3);
                list.addView(row, rowLp);
            }
            addPhotoTile(row, photo);
        }
        completeRow(row);
        scroll.post(() -> scroll.scrollTo(0, savedScrollY));
    }

    private void addPhotoTile(LinearLayout row, PhotoRepository.Photo photo) {
        FrameLayout tile = new FrameLayout(this);
        tile.setBackground(round(0xff202626, 9, 0xff293031, dp(1)));
        tile.setClipToOutline(true);
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setTransitionName("scope_photo");
        Bitmap thumbnail = decodeThumbnail(photo.uri);
        if (thumbnail != null) image.setImageBitmap(thumbnail);
        tile.addView(image, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
        lp.rightMargin = dp(3);
        row.addView(tile, lp);
        tile.setOnClickListener(v -> { hapticClick(v); openPhoto(photo, image); });
        tile.setOnLongClickListener(v -> {
            hapticClick(v);
            confirmDeleteTile(photo);
            return true;
        });
    }

    private void confirmDeleteTile(PhotoRepository.Photo photo) {
        android.app.Dialog dialog = new android.app.Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
            window.setLayout(android.view.WindowManager.LayoutParams.MATCH_PARENT, android.view.WindowManager.LayoutParams.MATCH_PARENT);
        }

        FrameLayout scrim = new FrameLayout(this);
        scrim.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scrim.setBackgroundColor(0x77000000);
        scrim.setOnClickListener(v -> dialog.dismiss());

        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setClickable(true);
        sheet.setBackground(round(0xff0e1314, 20, 0xff2a3233, dp(1)));
        sheet.setPadding(dp(20), dp(18), dp(20), dp(20));

        TextView title = text("DELETE FRAME?", 11, 0xffd7b574, true);
        title.setLetterSpacing(.12f);
        sheet.addView(title);

        TextView sub = text("Removes " + photo.name + " and its associated RAW archive.", 9, 0xff9aa49e, false);
        sub.setPadding(0, dp(6), 0, dp(16));
        sheet.addView(sub);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        TextView cancel = pill("CANCEL", false);
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(38), 1f));
        cancel.setOnClickListener(v -> dialog.dismiss());

        TextView del = pill("DELETE", true);
        del.setTextColor(0xfff0443c);
        del.setBackground(round(0x22f0443c, 18, 0xfff0443c, dp(1)));
        LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(0, dp(38), 1f);
        delLp.leftMargin = dp(8);
        actions.addView(del, delLp);
        del.setOnClickListener(v -> {
            hapticClick(v);
            PhotoRepository.delete(this, photo.uri, photo.name, PhotoRepository.archiveKey(photo.name));
            dialog.dismiss();
            loadPhotos();
        });
        sheet.addView(actions);

        FrameLayout.LayoutParams sheetLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
        );
        scrim.addView(sheet, sheetLp);
        dialog.setContentView(scrim);
        dialog.show();
    }

    private void completeRow(LinearLayout row) {
        if (row == null) return;
        while (row.getChildCount() < 3) {
            View spacer = new View(this);
            row.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        }
    }

    private Bitmap decodeThumbnail(Uri uri) {
        try (InputStream stream = getContentResolver().openInputStream(uri)) {
            if (stream == null) return null;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 16;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeStream(stream, null, options);
        } catch (Exception ignored) { return null; }
    }

    private void openPhoto(PhotoRepository.Photo photo, ImageView image) {
        Intent view = new Intent(this, PhotoViewerActivity.class);
        view.putExtra(PhotoViewerActivity.EXTRA_URI, photo.uri.toString());
        view.putExtra(PhotoViewerActivity.EXTRA_NAME, photo.name);
        view.putExtra(PhotoViewerActivity.EXTRA_FROM_LIBRARY, true);
        view.putExtra(PhotoEditorActivity.EXTRA_KEY, PhotoRepository.archiveKey(photo.name));
        Bundle transition = ActivityOptions.makeSceneTransitionAnimation(this, image, "scope_photo").toBundle();
        startActivity(view, transition);
    }

    private void chooseImport() {
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.setType("image/*");
        picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(picker, IMPORT_REQUEST);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != IMPORT_REQUEST || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
        catch (SecurityException ignored) { }
        Intent view = new Intent(this, PhotoViewerActivity.class);
        view.putExtra(PhotoViewerActivity.EXTRA_URI, uri.toString());
        view.putExtra(PhotoViewerActivity.EXTRA_NAME, "Imported image");
        view.putExtra(PhotoViewerActivity.EXTRA_IMPORTED, true);
        startActivity(view);
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private TextView pill(String value, boolean active) {
        TextView view = text(value, 8, active ? 0xffd7b574 : 0xffd8ded7, true);
        view.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(active ? 0xff1d1d1a : 0xff202626);
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), active ? 0xffd7b574 : 0xff39413e);
        view.setBackground(bg);
        return view;
    }

    private GradientDrawable round(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        if (strokeWidth > 0) d.setStroke(strokeWidth, strokeColor);
        return d;
    }

    private Bitmap decodeSampledBitmap(Uri uri, int reqW, int reqH) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(in, null, options);
            int scale = Math.max(1, Math.min(options.outWidth / reqW, options.outHeight / reqH));
            options.inSampleSize = scale;
            options.inJustDecodeBounds = false;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            try (InputStream in2 = getContentResolver().openInputStream(uri)) {
                return BitmapFactory.decodeStream(in2, null, options);
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void generateContactSheet() {
        List<PhotoRepository.Photo> photos = PhotoRepository.all(this);
        if (photos.isEmpty()) {
            Toast.makeText(this, "No photos to generate proof sheet", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "Developing 35mm Darkroom Proof Sheet…", Toast.LENGTH_SHORT).show();
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
                            Bitmap thumb = decodeSampledBitmap(p.uri, frameW, frameH);
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
                Uri savedUri = PhotoRepository.saveBitmap(this, sheet, fileName);
                sheet.recycle();

                runOnUiThread(() -> {
                    if (savedUri != null) {
                        Toast.makeText(this, "Proof sheet saved to Pictures/Lika Lens", Toast.LENGTH_LONG).show();
                        loadPhotos();
                        Intent view = new Intent(this, PhotoViewerActivity.class);
                        view.putExtra(PhotoViewerActivity.EXTRA_URI, savedUri.toString());
                        view.putExtra(PhotoViewerActivity.EXTRA_NAME, fileName);
                        view.putExtra(PhotoViewerActivity.EXTRA_FROM_LIBRARY, true);
                        startActivity(view);
                    } else {
                        Toast.makeText(this, "Could not save proof sheet", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Throwable t) {
                runOnUiThread(() -> Toast.makeText(this, "Error generating proof sheet: " + t.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void showBatchPasteLookDialog() {
        if (!PhotoEditStore.hasClipboard(this)) {
            Toast.makeText(this, "No copied recipe yet. Open DEVELOP on a photo and tap COPY.", Toast.LENGTH_SHORT).show();
            return;
        }
        RawDeveloper.Look copied = PhotoEditStore.pasteFromClipboard(this);
        if (copied == null) return;
        new AlertDialog.Builder(this)
                .setTitle("PASTE LOOK RECIPE")
                .setMessage("Brain: " + copied.brain + "\nApply this graded recipe to:")
                .setPositiveButton("All Editable RAW Photos", (dialog, which) -> {
                    List<PhotoRepository.Photo> photos = PhotoRepository.all(this);
                    int count = 0;
                    for (PhotoRepository.Photo p : photos) {
                        String key = PhotoRepository.archiveKey(p.name);
                        if (RawArchive.file(this, key).exists()) {
                            PhotoEditStore.save(this, key, copied.copy());
                            count++;
                        }
                    }
                    Toast.makeText(this, "Recipe applied to " + count + " RAW frame(s)", Toast.LENGTH_SHORT).show();
                    loadPhotos();
                })
                .setNeutralButton("Latest Photo Only", (dialog, which) -> {
                    PhotoRepository.Photo latest = PhotoRepository.latest(this);
                    if (latest != null) {
                        String key = PhotoRepository.archiveKey(latest.name);
                        PhotoEditStore.save(this, key, copied.copy());
                        Toast.makeText(this, "Recipe applied to " + latest.name, Toast.LENGTH_SHORT).show();
                        loadPhotos();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
