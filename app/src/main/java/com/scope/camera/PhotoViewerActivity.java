package com.scope.camera;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.transition.ChangeBounds;
import android.transition.ChangeImageTransform;
import android.transition.TransitionSet;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Full-screen viewer with interactive pinch-to-zoom, EXIF metadata inspection, sharing, and editor access. */
public class PhotoViewerActivity extends Activity {
    static final String EXTRA_URI = "scope_image_uri";
    static final String EXTRA_NAME = "scope_image_name";
    static final String EXTRA_FROM_LIBRARY = "scope_from_library";
    static final String EXTRA_IMPORTED = "scope_imported";
    private static final int EDIT_REQUEST = 51;
    private static final int BG = 0xff0b0d0e;
    private static final int ACCENT = 0xffd7b574;

    private ImageView photoView;
    private Uri photoUri;
    private String archiveKey;
    private String photoName;
    private LinearLayout infoCard;
    private TextView infoButton;
    private boolean infoVisible;

    // Zoom & Pan state
    private final Matrix matrix = new Matrix();
    private final Matrix savedMatrix = new Matrix();
    private float currentScale = 1f;
    private final PointF startPoint = new PointF();
    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;
    private int imageWidth = 0;
    private int imageHeight = 0;
    private float downY = 0f;
    private boolean swipingDownToDismiss = false;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        TransitionSet enter = new TransitionSet().addTransition(new ChangeBounds()).addTransition(new ChangeImageTransform()).setDuration(240);
        TransitionSet exit = new TransitionSet().addTransition(new ChangeBounds()).addTransition(new ChangeImageTransform()).setDuration(220);
        getWindow().setSharedElementEnterTransition(enter);
        getWindow().setSharedElementReturnTransition(exit);

        String uriValue = getIntent().getStringExtra(EXTRA_URI);
        photoUri = uriValue == null ? null : Uri.parse(uriValue);
        archiveKey = getIntent().getStringExtra(PhotoEditorActivity.EXTRA_KEY);
        photoName = getIntent().getStringExtra(EXTRA_NAME);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        root.setFitsSystemWindows(true);

        photoView = new ImageView(this);
        photoView.setScaleType(ImageView.ScaleType.MATRIX);
        photoView.setTransitionName("scope_photo");
        root.addView(photoView, new FrameLayout.LayoutParams(-1, -1));

        initTouchGestures();

        // Header controls overlay
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(10), dp(16), dp(10));
        header.setBackground(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xb8080b0c, 0x00080b0c}));

        TextView back = text("‹", 28, 0xffedf0e9, true);
        back.setGravity(Gravity.CENTER);
        header.addView(back, new LinearLayout.LayoutParams(dp(36), dp(40)));
        back.setOnClickListener(v -> finishAfterTransition());

        TextView title = text(photoName == null ? "LIKA LENS" : photoName, 10, 0xffe6eae4, true);
        title.setPadding(dp(8), 0, 0, 0);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        infoButton = pill("INFO", false);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(dp(50), dp(32));
        infoLp.leftMargin = dp(4);
        header.addView(infoButton, infoLp);
        infoButton.setOnClickListener(v -> toggleInfo());

        TextView shareBtn = pill("SHARE", false);
        LinearLayout.LayoutParams shareLp = new LinearLayout.LayoutParams(dp(54), dp(32));
        shareLp.leftMargin = dp(4);
        header.addView(shareBtn, shareLp);
        shareBtn.setOnClickListener(v -> sharePhoto());

        if (archiveKey != null && RawArchive.exists(this, archiveKey)) {
            TextView edit = pill("EDIT", true);
            LinearLayout.LayoutParams editLp = new LinearLayout.LayoutParams(dp(52), dp(32));
            editLp.leftMargin = dp(4);
            header.addView(edit, editLp);
            edit.setOnClickListener(v -> openEditor());
        }

        if (!getIntent().getBooleanExtra(EXTRA_FROM_LIBRARY, false)) {
            TextView library = pill("GRID", false);
            LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(dp(50), dp(32));
            gridLp.leftMargin = dp(4);
            header.addView(library, gridLp);
            library.setOnClickListener(v -> startActivity(new Intent(this, GalleryActivity.class)));
        }

        root.addView(header, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        // EXIF Metadata bottom overlay card
        infoCard = buildInfoCard();
        infoCard.setVisibility(View.GONE);
        FrameLayout.LayoutParams infoCardLp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        infoCardLp.setMargins(dp(16), 0, dp(16), dp(20));
        root.addView(infoCard, infoCardLp);

        setContentView(root);
        loadPhoto();
    }

    private void initTouchGestures() {
        scaleDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector detector) {
                float scaleFactor = detector.getScaleFactor();
                float newScale = currentScale * scaleFactor;
                if (newScale >= 0.85f && newScale <= 5.0f) {
                    currentScale = newScale;
                    matrix.postScale(scaleFactor, scaleFactor, detector.getFocusX(), detector.getFocusY());
                    clampMatrix();
                    photoView.setImageMatrix(matrix);
                }
                return true;
            }
        });

        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDoubleTap(MotionEvent e) {
                if (currentScale > 1.2f) {
                    resetZoom();
                } else {
                    currentScale = 2.5f;
                    matrix.postScale(2.5f, 2.5f, e.getX(), e.getY());
                    clampMatrix();
                    photoView.setImageMatrix(matrix);
                }
                return true;
            }
        });

        photoView.setOnTouchListener((v, event) -> {
            scaleDetector.onTouchEvent(event);
            gestureDetector.onTouchEvent(event);

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    savedMatrix.set(matrix);
                    startPoint.set(event.getX(), event.getY());
                    downY = event.getY();
                    swipingDownToDismiss = (currentScale <= 1.05f);
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (!scaleDetector.isInProgress()) {
                        float dx = event.getX() - startPoint.x;
                        float dy = event.getY() - startPoint.y;

                        if (currentScale <= 1.05f && swipingDownToDismiss && dy > dp(110) && Math.abs(dx) < Math.abs(dy) * 0.7f) {
                            finishAfterTransition();
                            return true;
                        }

                        if (currentScale > 1.05f) {
                            matrix.set(savedMatrix);
                            matrix.postTranslate(dx, dy);
                            clampMatrix();
                            photoView.setImageMatrix(matrix);
                        }
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (currentScale < 1.0f) resetZoom();
                    break;
            }
            return true;
        });
    }

    private void clampMatrix() {
        if (imageWidth == 0 || imageHeight == 0 || photoView.getWidth() == 0 || photoView.getHeight() == 0) return;
        RectF rect = new RectF(0, 0, imageWidth, imageHeight);
        matrix.mapRect(rect);
        float viewW = photoView.getWidth();
        float viewH = photoView.getHeight();
        float deltaX = 0f, deltaY = 0f;

        if (rect.width() < viewW) deltaX = (viewW - rect.width()) * 0.5f - rect.left;
        else if (rect.left > 0) deltaX = -rect.left;
        else if (rect.right < viewW) deltaX = viewW - rect.right;

        if (rect.height() < viewH) deltaY = (viewH - rect.height()) * 0.5f - rect.top;
        else if (rect.top > 0) deltaY = -rect.top;
        else if (rect.bottom < viewH) deltaY = viewH - rect.bottom;

        matrix.postTranslate(deltaX, deltaY);
    }

    private void resetZoom() {
        currentScale = 1.0f;
        fitImageToView();
    }

    private void fitImageToView() {
        if (imageWidth == 0 || imageHeight == 0 || photoView.getWidth() == 0 || photoView.getHeight() == 0) return;
        matrix.reset();
        float viewW = photoView.getWidth();
        float viewH = photoView.getHeight();
        float scale = Math.min(viewW / (float) imageWidth, viewH / (float) imageHeight);
        float dx = (viewW - imageWidth * scale) * 0.5f;
        float dy = (viewH - imageHeight * scale) * 0.5f;
        matrix.setScale(scale, scale);
        matrix.postTranslate(dx, dy);
        photoView.setImageMatrix(matrix);
    }

    private LinearLayout buildInfoCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(round(0xdd121617, 14, 0x99d7b574, dp(1)));

        TextView header = text("FRAME DETAILS & EXIF", 9, ACCENT, true);
        header.setLetterSpacing(.12f);
        card.addView(header);

        TextView dataLine1 = text("READING METADATA…", 9, 0xfff0f2eb, false);
        dataLine1.setPadding(0, dp(6), 0, 0);
        dataLine1.setTag("exif_line1");
        card.addView(dataLine1);

        TextView dataLine2 = text("", 8, 0xff9ba59e, false);
        dataLine2.setPadding(0, dp(3), 0, 0);
        dataLine2.setTag("exif_line2");
        card.addView(dataLine2);

        return card;
    }

    private void toggleInfo() {
        infoVisible = !infoVisible;
        infoCard.setVisibility(infoVisible ? View.VISIBLE : View.GONE);
        infoButton.setTextColor(infoVisible ? ACCENT : 0xffd8ded7);
        if (infoVisible) populateInfoData();
    }

    private void populateInfoData() {
        TextView line1 = infoCard.findViewWithTag("exif_line1");
        TextView line2 = infoCard.findViewWithTag("exif_line2");
        if (line1 == null || line2 == null || photoUri == null) return;

        String shutter = "1/120s";
        String iso = "ISO 100";
        String focal = "Wide";
        String dimensions = imageWidth > 0 ? (imageWidth + " × " + imageHeight) : "";

        try (InputStream stream = getContentResolver().openInputStream(photoUri)) {
            if (stream != null) {
                ExifInterface exif = new ExifInterface(stream);
                String expTime = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME);
                if (expTime != null) {
                    try {
                        double sec = Double.parseDouble(expTime);
                        shutter = sec < 1.0 ? String.format(Locale.US, "1/%ds", Math.round(1.0 / sec)) : String.format(Locale.US, "%.1fs", sec);
                    } catch (Exception ignored) { shutter = expTime + "s"; }
                }
                String isoVal = exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY);
                if (isoVal != null && !isoVal.isEmpty()) iso = "ISO " + isoVal;
                String fLen = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH);
                if (fLen != null && !fLen.isEmpty()) focal = fLen + "mm";
            }
        } catch (Throwable ignored) { }

        boolean hasRaw = archiveKey != null && RawArchive.exists(this, archiveKey);
        line1.setText(shutter + "  ·  " + iso + "  ·  " + focal + (dimensions.isEmpty() ? "" : "  ·  " + dimensions));
        line2.setText(hasRaw ? "RAW SENSOR ORIGINAL PRESERVED  ·  CHROMAGRAPH MOVIE LOOK"
                : "PROCESSED JPEG  ·  DIRECT CAMERA RENDER");
    }

    private void sharePhoto() {
        if (photoUri == null) return;
        try {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("image/jpeg");
            share.putExtra(Intent.EXTRA_STREAM, photoUri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "Share Lika Lens Photo"));
        } catch (Exception e) {
            Toast.makeText(this, "Could not share this photo.", Toast.LENGTH_SHORT).show();
        }
    }

    private void openEditor() {
        Intent edit = new Intent(this, PhotoEditorActivity.class);
        edit.putExtra(PhotoEditorActivity.EXTRA_KEY, archiveKey);
        edit.putExtra(EXTRA_URI, photoUri == null ? null : photoUri.toString());
        startActivityForResult(edit, EDIT_REQUEST);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == EDIT_REQUEST && resultCode == RESULT_OK) loadPhoto();
    }

    private void loadPhoto() {
        if (photoUri == null || photoView == null) return;
        try (InputStream boundsStream = getContentResolver().openInputStream(photoUri)) {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(boundsStream, null, bounds);
            imageWidth = bounds.outWidth;
            imageHeight = bounds.outHeight;
            int sample = 1;
            int maxWidth = getResources().getDisplayMetrics().widthPixels * 2;
            int maxHeight = getResources().getDisplayMetrics().heightPixels * 2;
            while (bounds.outWidth / sample > maxWidth || bounds.outHeight / sample > maxHeight) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            try (InputStream stream = getContentResolver().openInputStream(photoUri)) {
                Bitmap bitmap = stream == null ? null : BitmapFactory.decodeStream(stream, null, options);
                if (bitmap != null) {
                    photoView.setImageBitmap(bitmap);
                    photoView.post(this::fitImageToView);
                }
            }
        } catch (Exception ignored) {
            Toast.makeText(this, "Could not open this frame.", Toast.LENGTH_SHORT).show();
        }
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value == null ? "LIKA LENS FRAME" : value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private TextView pill(String label, boolean active) {
        TextView view = text(label, 8, active ? ACCENT : 0xffd8ded7, true);
        view.setGravity(Gravity.CENTER);
        view.setBackground(round(active ? 0xff1d1d1a : 0xd8191e1e, 16,
                active ? ACCENT : 0x556e7872, dp(1)));
        return view;
    }

    private android.graphics.drawable.GradientDrawable round(int color, int radius, int strokeColor, int strokeWidth) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        if (strokeWidth > 0) d.setStroke(strokeWidth, strokeColor);
        return d;
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
