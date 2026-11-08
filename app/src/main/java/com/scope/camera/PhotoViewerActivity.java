package com.scope.camera;

import android.app.Activity;
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
import android.os.Handler;
import android.os.Looper;
import android.transition.ChangeBounds;
import android.transition.ChangeImageTransform;
import android.transition.TransitionSet;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Full-screen viewer with interactive pinch-to-zoom, EXIF metadata inspection, sharing, and editor access. */
public class PhotoViewerActivity extends Activity {
    private static final String TAG = "PhotoViewerActivity";
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

    private Bitmap rawOriginalBitmap;
    private Bitmap developedBitmap;
    private boolean isComparing = false;
    private TextView compareButton;
    private TextView compareBadge;
    private TextView zoomHud;
    private TextView titleView;
    private TextView editButton;
    private List<PhotoRepository.Photo> allPhotos = new java.util.ArrayList<>();
    private int currentIndex = -1;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

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

    private void hapticClick(View v) {
        if (v != null && AppSettings.getBoolean(this, AppSettings.HAPTICS, true)) {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
    }

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
        header.setPadding(dp(12), dp(10), dp(12), dp(10));
        header.setBackground(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xb8080b0c, 0x00080b0c}));

        TextView back = text("‹", 28, 0xffedf0e9, true);
        back.setGravity(Gravity.CENTER);
        header.addView(back, new LinearLayout.LayoutParams(dp(34), dp(40)));
        back.setOnClickListener(v -> { hapticClick(v); finishAfterTransition(); });

        titleView = text(photoName == null ? "LIKA LENS" : photoName, 9, 0xffe6eae4, true);
        titleView.setPadding(dp(6), 0, 0, 0);
        titleView.setMaxLines(1);
        header.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1));

        infoButton = pill("INFO", false);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(dp(44), dp(32));
        infoLp.leftMargin = dp(4);
        header.addView(infoButton, infoLp);
        infoButton.setOnClickListener(v -> { hapticClick(v); toggleInfo(); });

        TextView shareBtn = pill("SHARE", false);
        LinearLayout.LayoutParams shareLp = new LinearLayout.LayoutParams(dp(50), dp(32));
        shareLp.leftMargin = dp(4);
        header.addView(shareBtn, shareLp);
        shareBtn.setOnClickListener(v -> { hapticClick(v); sharePhoto(); });

        TextView deleteBtn = pill("DEL", false);
        LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(dp(40), dp(32));
        delLp.leftMargin = dp(4);
        header.addView(deleteBtn, delLp);
        deleteBtn.setOnClickListener(v -> { hapticClick(v); confirmDeleteCurrentPhoto(); });

        compareButton = pill("RAW", false);
        LinearLayout.LayoutParams compLp = new LinearLayout.LayoutParams(dp(44), dp(32));
        compLp.leftMargin = dp(4);
        header.addView(compareButton, compLp);
        compareButton.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startComparing();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    stopComparing();
                    return true;
            }
            return false;
        });

        editButton = pill("EDIT", true);
        LinearLayout.LayoutParams editLp = new LinearLayout.LayoutParams(dp(46), dp(32));
        editLp.leftMargin = dp(4);
        header.addView(editButton, editLp);
        editButton.setOnClickListener(v -> { hapticClick(v); openEditor(); });

        TextView gifBtn = pill("GIF", false);
        LinearLayout.LayoutParams gifLp = new LinearLayout.LayoutParams(dp(44), dp(32));
        gifLp.leftMargin = dp(4);
        header.addView(gifBtn, gifLp);
        gifBtn.setOnClickListener(v -> { hapticClick(v); openGifFromViewer(); });

        if (!getIntent().getBooleanExtra(EXTRA_FROM_LIBRARY, false)) {
            TextView library = pill("GRID", false);
            LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(dp(44), dp(32));
            gridLp.leftMargin = dp(4);
            header.addView(library, gridLp);
            library.setOnClickListener(v -> { hapticClick(v); startActivity(new Intent(this, GalleryActivity.class)); });
        }

        root.addView(header, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        compareBadge = text("ORIGINAL SENSOR RAW", 8, ACCENT, true);
        compareBadge.setBackground(round(0xdd0b0d0e, 14, ACCENT, dp(1)));
        compareBadge.setPadding(dp(12), dp(4), dp(12), dp(4));
        compareBadge.setVisibility(View.GONE);
        FrameLayout.LayoutParams cbLp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        cbLp.topMargin = dp(56);
        root.addView(compareBadge, cbLp);

        zoomHud = text("100% · FIT", 8, ACCENT, true);
        zoomHud.setBackground(round(0xdd0b0d0e, 12, 0x66d7b574, dp(1)));
        zoomHud.setPadding(dp(10), dp(4), dp(10), dp(4));
        zoomHud.setVisibility(View.GONE);
        FrameLayout.LayoutParams zhLp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        zhLp.bottomMargin = dp(80);
        root.addView(zoomHud, zhLp);

        // EXIF Metadata bottom overlay card
        infoCard = buildInfoCard();
        infoCard.setVisibility(View.GONE);
        root.addView(infoCard, getInfoCardLayoutParams());

        setContentView(root);
        refreshPhotoList();
        loadPhoto();
    }

    private FrameLayout.LayoutParams getInfoCardLayoutParams() {
        WindowMetricsHelper.WindowSizeInfo info = WindowMetricsHelper.computeWindowSizeInfo(this);
        int cardWidth = info.widthDp >= 600 ? dp(420) : ViewGroup.LayoutParams.MATCH_PARENT;
        int gravity = (info.widthDp >= 600 && info.isLandscape)
                ? (Gravity.BOTTOM | Gravity.END)
                : (Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT, gravity);
        lp.setMargins(dp(16), 0, dp(16), dp(20));
        return lp;
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (infoCard != null) {
            infoCard.setLayoutParams(getInfoCardLayoutParams());
        }
        if (photoView != null) {
            photoView.post(this::fitImageToView);
        }
    }

    private void startComparing() {
        if (isComparing) return;
        isComparing = true;
        if (compareBadge != null) {
            compareBadge.setText(rawOriginalBitmap != null ? "ORIGINAL SENSOR RAW" : "DEVELOPING SENSOR ORIGINAL…");
            compareBadge.setVisibility(View.VISIBLE);
        }
        if (compareButton != null) compareButton.setTextColor(ACCENT);
        if (AppSettings.getBoolean(this, AppSettings.HAPTICS, true)) {
            photoView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
        if (rawOriginalBitmap != null) {
            photoView.setImageBitmap(rawOriginalBitmap);
        } else if (archiveKey != null && RawArchive.exists(this, archiveKey)) {
            worker.execute(() -> {
                try {
                    RawArchive.Frame frame = RawArchive.read(PhotoViewerActivity.this, archiveKey);
                    RawDeveloper.Look neutral = new RawDeveloper.Look();
                    neutral.brain = "NEUTRAL RAW";
                    neutral.contrast = 1.0f;
                    neutral.pivot = 0.5f;
                    neutral.saturation = 1.0f;
                    neutral.grain = 0f;
                    neutral.halation = 0f;
                    neutral.vignette = 0f;
                    neutral.sharpening = 0f;
                    neutral.chromaNr = 0f;
                    neutral.colorRollOff = 0.5f;
                    neutral.exposureEv = 0f;
                    neutral.warmth = 0f;
                    neutral.aspectRatio = frame.aspectRatio;

                    int targetW = developedBitmap != null ? developedBitmap.getWidth() : 2000;
                    int targetH = developedBitmap != null ? developedBitmap.getHeight() : 2000;
                    int targetDim = Math.max(targetW, targetH);
                    Bitmap rawBmp = RawDeveloper.develop(frame, neutral, targetDim);
                    if (rawBmp != null && developedBitmap != null && (rawBmp.getWidth() != targetW || rawBmp.getHeight() != targetH)) {
                        Bitmap scaled = Bitmap.createScaledBitmap(rawBmp, targetW, targetH, true);
                        if (scaled != rawBmp) {
                            rawBmp.recycle();
                            rawBmp = scaled;
                        }
                    }
                    final Bitmap finalRaw = rawBmp;
                    mainHandler.post(() -> {
                        rawOriginalBitmap = finalRaw;
                        if (compareBadge != null) compareBadge.setText("ORIGINAL SENSOR RAW");
                        if (isComparing && rawOriginalBitmap != null) {
                            photoView.setImageBitmap(rawOriginalBitmap);
                        }
                    });
                } catch (Throwable ignored) {
                    mainHandler.post(() -> {
                        if (compareBadge != null) compareBadge.setText("NO RAW DATA");
                    });
                }
            });
        }
    }

    private void stopComparing() {
        if (!isComparing) return;
        isComparing = false;
        if (compareBadge != null) compareBadge.setVisibility(View.GONE);
        if (compareButton != null) compareButton.setTextColor(0xffd8ded7);
        if (developedBitmap != null) {
            photoView.setImageBitmap(developedBitmap);
        }
        if (AppSettings.getBoolean(this, AppSettings.HAPTICS, true)) {
            photoView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        }
    }

    private final Runnable hideZoomHudRunnable = () -> {
        if (zoomHud != null) zoomHud.setVisibility(View.GONE);
    };

    private void showZoomHud(String text) {
        if (zoomHud == null) return;
        zoomHud.setText(text);
        zoomHud.setVisibility(View.VISIBLE);
        mainHandler.removeCallbacks(hideZoomHudRunnable);
        mainHandler.postDelayed(hideZoomHudRunnable, 1200);
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
                    showZoomHud(Math.round(currentScale * 100) + "%");
                }
                return true;
            }
        });

        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDoubleTap(MotionEvent e) {
                hapticClick(photoView);
                if (currentScale > 1.2f) {
                    resetZoom();
                    showZoomHud("100% · FIT");
                } else {
                    currentScale = 2.5f;
                    matrix.postScale(2.5f, 2.5f, e.getX(), e.getY());
                    clampMatrix();
                    photoView.setImageMatrix(matrix);
                    showZoomHud("250% · DETAIL");
                }
                return true;
            }

            @Override public void onLongPress(MotionEvent e) {
                if (archiveKey != null && RawArchive.exists(PhotoViewerActivity.this, archiveKey)) {
                    startComparing();
                }
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
                    if (isComparing) {
                        stopComparing();
                    }
                    if (!scaleDetector.isInProgress() && currentScale <= 1.05f) {
                        float dx = event.getX() - startPoint.x;
                        float dy = event.getY() - startPoint.y;
                        if (Math.abs(dx) > dp(72) && Math.abs(dx) > Math.abs(dy) * 1.3f) {
                            stepPhoto(dx < 0 ? 1 : -1);
                            return true;
                        }
                    }
                    if (currentScale < 1.0f) resetZoom();
                    break;
            }
            return true;
        });
    }

    private void refreshPhotoList() {
        allPhotos = PhotoRepository.all(this);
        currentIndex = -1;
        if (photoUri == null) return;
        String target = photoUri.toString();
        for (int i = 0; i < allPhotos.size(); i++) {
            PhotoRepository.Photo p = allPhotos.get(i);
            if (p.uri != null && (target.equals(p.uri.toString()) || (photoName != null && photoName.equals(p.name)))) {
                currentIndex = i;
                break;
            }
        }
    }

    private void stepPhoto(int delta) {
        if (allPhotos == null || allPhotos.isEmpty()) refreshPhotoList();
        if (allPhotos == null || allPhotos.size() <= 1 || currentIndex < 0) return;
        int nextIndex = currentIndex + delta;
        if (nextIndex < 0 || nextIndex >= allPhotos.size()) {
            showZoomHud(delta < 0 ? "NEWEST FRAME" : "OLDEST FRAME");
            return;
        }
        hapticClick(photoView);
        currentIndex = nextIndex;
        PhotoRepository.Photo next = allPhotos.get(currentIndex);
        photoUri = next.uri;
        photoName = next.name;
        archiveKey = PhotoRepository.archiveKey(next.name);
        if (rawOriginalBitmap != null && !rawOriginalBitmap.isRecycled()) {
            rawOriginalBitmap.recycle();
        }
        rawOriginalBitmap = null;
        photoView.animate().alpha(0.35f).setDuration(80).withEndAction(() -> {
            loadPhoto();
            photoView.animate().alpha(1f).setDuration(140).start();
        }).start();
    }

    private void confirmDeleteCurrentPhoto() {
        if (photoUri == null) return;
        android.app.Dialog dialog = new android.app.Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
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

        TextView title = text("DELETE THIS FRAME?", 11, ACCENT, true);
        title.setLetterSpacing(.14f);
        sheet.addView(title);

        TextView sub = text("Removes " + (photoName != null ? photoName : "this photo") + " and its associated RAW archive.", 9, 0xff9ba59e, false);
        sub.setPadding(0, dp(6), 0, dp(16));
        sheet.addView(sub);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        TextView cancel = pill("CANCEL", false);
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(0, dp(38), 1f);
        cancelLp.rightMargin = dp(6);
        actions.addView(cancel, cancelLp);
        cancel.setOnClickListener(v -> dialog.dismiss());

        TextView confirm = pill("DELETE", true);
        confirm.setTextColor(0xffff6b6b);
        confirm.setBackground(round(0x33ff5252, 16, 0xffff6b6b, dp(1)));
        LinearLayout.LayoutParams confirmLp = new LinearLayout.LayoutParams(0, dp(38), 1f);
        confirmLp.leftMargin = dp(6);
        actions.addView(confirm, confirmLp);
        confirm.setOnClickListener(v -> {
            dialog.dismiss();
            boolean deleted = PhotoRepository.delete(this, photoUri, photoName, archiveKey);
            if (deleted) {
                Toast.makeText(this, "Frame deleted", Toast.LENGTH_SHORT).show();
                refreshPhotoList();
                if (allPhotos.isEmpty()) {
                    finish();
                } else {
                    int idx = Math.max(0, Math.min(currentIndex, allPhotos.size() - 1));
                    currentIndex = idx;
                    PhotoRepository.Photo next = allPhotos.get(idx);
                    photoUri = next.uri;
                    photoName = next.name;
                    archiveKey = PhotoRepository.archiveKey(next.name);
                    if (rawOriginalBitmap != null && !rawOriginalBitmap.isRecycled()) {
                        rawOriginalBitmap.recycle();
                    }
                    rawOriginalBitmap = null;
                    loadPhoto();
                }
            } else {
                Toast.makeText(this, "Could not delete this frame", Toast.LENGTH_SHORT).show();
            }
        });

        sheet.addView(actions, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout.LayoutParams sheetLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
        );
        scrim.addView(sheet, sheetLp);
        dialog.setContentView(scrim);
        dialog.show();
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
                String isoVal = exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS);
                if ((isoVal == null || isoVal.isEmpty())) isoVal = exif.getAttribute("PhotographicSensitivity");
                if (isoVal != null && !isoVal.isEmpty()) iso = "ISO " + isoVal;
                String fLen = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH);
                if (fLen != null && !fLen.isEmpty()) focal = fLen + "mm";
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed to parse EXIF metadata", t);
        }

        boolean hasRaw = archiveKey != null && RawArchive.exists(this, archiveKey);
        line1.setText(shutter + "  ·  " + iso + "  ·  " + focal + (dimensions.isEmpty() ? "" : "  ·  " + dimensions));
        line2.setText(hasRaw ? "RAW SENSOR ORIGINAL PRESERVED  ·  CHROMAGRAPH MOVIE LOOK"
                : "PROCESSED JPEG  ·  DIRECT CAMERA RENDER");
    }

    private void sharePhoto() {
        if (photoUri == null) return;
        try {
            boolean isGif = photoName != null && photoName.toLowerCase(Locale.US).endsWith(".gif");
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType(isGif ? "image/gif" : "image/jpeg");
            share.putExtra(Intent.EXTRA_STREAM, photoUri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, isGif ? "Share Lika Lens GIF" : "Share Lika Lens Photo"));
        } catch (Exception e) {
            Toast.makeText(this, "Could not share this photo.", Toast.LENGTH_SHORT).show();
        }
    }

    private void openGifFromViewer() {
        if (photoUri == null) return;
        boolean isGif = photoName != null && photoName.toLowerCase(Locale.US).endsWith(".gif");
        if (isGif) {
            sharePhoto();
            return;
        }

        ArrayList<String> burstUris = new ArrayList<>();
        burstUris.add(photoUri.toString());
        if (currentIndex >= 0 && !allPhotos.isEmpty()) {
            PhotoRepository.Photo current = allPhotos.get(currentIndex);
            for (int i = 0; i < allPhotos.size(); i++) {
                if (i == currentIndex) continue;
                PhotoRepository.Photo other = allPhotos.get(i);
                if (!other.isGif && Math.abs(other.timestampMillis - current.timestampMillis) <= 15000L) {
                    burstUris.add(other.uri.toString());
                }
            }
        }

        Intent intent = new Intent(this, GifStudioActivity.class);
        intent.putStringArrayListExtra(GifStudioActivity.EXTRA_PHOTO_URIS, burstUris);
        startActivity(intent);
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
        if (titleView != null) {
            titleView.setText(photoName == null ? "LIKA LENS" : photoName);
        }
        boolean hasRaw = archiveKey != null && RawArchive.exists(this, archiveKey);
        if (compareButton != null) compareButton.setVisibility(hasRaw ? View.VISIBLE : View.GONE);
        if (editButton != null) editButton.setVisibility(hasRaw ? View.VISIBLE : View.GONE);

        boolean isGif = photoName != null && photoName.toLowerCase(Locale.US).endsWith(".gif");
        if (isGif && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                android.graphics.ImageDecoder.Source source = android.graphics.ImageDecoder.createSource(getContentResolver(), photoUri);
                android.graphics.drawable.Drawable drawable = android.graphics.ImageDecoder.decodeDrawable(source);
                photoView.setImageDrawable(drawable);
                if (drawable instanceof android.graphics.drawable.AnimatedImageDrawable) {
                    ((android.graphics.drawable.AnimatedImageDrawable) drawable).start();
                }
                photoView.post(this::fitImageToView);
                return;
            } catch (Exception e) {
                Log.w(TAG, "ImageDecoder failed, falling back to BitmapFactory", e);
            }
        }

        try (InputStream boundsStream = getContentResolver().openInputStream(photoUri)) {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(boundsStream, null, bounds);
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
                    if (developedBitmap != null && developedBitmap != bitmap && !developedBitmap.isRecycled()) {
                        developedBitmap.recycle();
                    }
                    developedBitmap = bitmap;
                    imageWidth = bitmap.getWidth();
                    imageHeight = bitmap.getHeight();
                    currentScale = 1.0f;
                    photoView.setImageBitmap(bitmap);
                    photoView.post(this::fitImageToView);
                    if (infoVisible) populateInfoData();
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

    @Override protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        worker.shutdown();
        if (rawOriginalBitmap != null && !rawOriginalBitmap.isRecycled()) {
            rawOriginalBitmap.recycle();
            rawOriginalBitmap = null;
        }
        if (developedBitmap != null && !developedBitmap.isRecycled()) {
            developedBitmap.recycle();
            developedBitmap = null;
        }
        super.onDestroy();
    }
}
