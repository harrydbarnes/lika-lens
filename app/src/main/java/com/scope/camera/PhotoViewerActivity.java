package com.scope.camera;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import java.util.ArrayList;
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

    enum CompareState {
        OFF,
        LOADING_ORIGINAL,
        SHOWING_ORIGINAL
    }

    enum GestureMode {
        IDLE,
        PINCH_ZOOM,
        PAN,
        DISMISS_DRAG,
        SWIPE_STEP
    }

    private Bitmap rawOriginalBitmap;
    private Bitmap developedBitmap;
    private CompareState compareState = CompareState.OFF;
    private int compareGeneration = 0;
    private volatile boolean currentHasRaw;
    private final JobState imageJob = new JobState();
    private boolean viewerActive;
    private TextView compareButton;
    private TextView compareBadge;
    private TextView zoomHud;
    private TextView titleView;
    private TextView editButton;
    private List<PhotoRepository.Photo> allPhotos = new java.util.ArrayList<>();
    private int currentIndex = -1;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "lika-viewer-worker");
        t.setDaemon(true);
        return t;
    });

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
    private GestureMode gestureMode = GestureMode.IDLE;

    private void hapticClick(View v) {
        UiKit.hapticClick(this, v);
    }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getWindow().setColorMode(android.content.pm.ActivityInfo.COLOR_MODE_HDR);
        }
        if (Build.VERSION.SDK_INT >= 34) {
            getWindow().setDesiredHdrHeadroom(3.0f);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    this::finishAfterTransition
            );
        }
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        String uriValue = getIntent().getStringExtra(EXTRA_URI);
        photoUri = uriValue == null ? null : Uri.parse(uriValue);
        archiveKey = getIntent().getStringExtra(PhotoEditorActivity.EXTRA_KEY);
        photoName = getIntent().getStringExtra(EXTRA_NAME);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        root.setFitsSystemWindows(true);

        photoView = new ImageView(this);
        photoView.setScaleType(ImageView.ScaleType.MATRIX);
        photoView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int newW = right - left;
            int newH = bottom - top;
            int oldW = oldRight - oldLeft;
            int oldH = oldBottom - oldTop;
            if ((newW != oldW || newH != oldH) && newW > 0 && newH > 0 && currentScale <= 1.001f) {
                fitImageToView();
            }
        });
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
        compareButton.setVisibility(View.GONE);
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
        editButton.setVisibility(View.GONE);
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
        infoCard = PhotoViewerMetadataSheet.build(this);
        infoCard.setVisibility(View.GONE);
        root.addView(infoCard, getInfoCardLayoutParams());

        setContentView(root);
        refreshPhotoListAsync(null);
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

    private static RawDeveloper.Look buildNeutralRawLook(RawArchive.Frame frame) {
        RawDeveloper.Look neutral = new RawDeveloper.Look();
        neutral.brain = AppSettings.BRAIN_NEUTRAL_RAW;
        neutral.toneCurve = "Linear Response";
        neutral.shadowProfile = "Neutral / Open Shadows";
        neutral.highlightRecovery = "Adaptive blend";
        neutral.easyMode = true;
        neutral.skinProtectionMode = AppSettings.OVERRIDE_OFF;
        neutral.smartVibranceMode = AppSettings.OVERRIDE_OFF;
        neutral.skinProtection = false;
        neutral.smartVibrance = false;
        neutral.contrast = 1.0f;
        neutral.pivot = 0.50f;
        neutral.saturation = 0.96f;
        neutral.shadows = 0.0f;
        neutral.highlights = 0.50f;
        neutral.grain = 0f;
        neutral.halation = 0f;
        neutral.vignette = 0f;
        neutral.sharpening = 0f;
        neutral.chromaNr = 0.25f;
        neutral.colorRollOff = 0.50f;
        neutral.exposureEv = 0f;
        neutral.warmth = 0f;
        neutral.lutName = "None";
        neutral.frameBorder = "None";
        if (frame != null) {
            neutral.aspectRatio = frame.aspectRatio;
        }
        return neutral;
    }

    private void preloadRawOriginal(String keyForLoad, int targetW, int targetH) {
        if (keyForLoad == null || !currentHasRaw) return;
        final int generation = compareGeneration;
        final android.content.Context appContext = getApplicationContext();
        worker.execute(() -> {
            RawArchive.pin(keyForLoad);
            try {
                if (generation != compareGeneration || !keyForLoad.equals(archiveKey) || isFinishing() || isDestroyed()) return;
                if (!RawArchive.exists(appContext, keyForLoad)) return;
                RawArchive.Frame frame = RawArchive.read(appContext, keyForLoad);
                RawDeveloper.Look neutral = buildNeutralRawLook(frame);
                RawDeveloper.Look savedCaptureLook = PhotoEditStore.load(appContext, keyForLoad);
                neutral.flipHorizontal = savedCaptureLook != null && savedCaptureLook.flipHorizontal;
                int targetDim = Math.max(1200, Math.max(targetW, targetH));
                Bitmap rawBmp = RawDeveloper.develop(frame, neutral, targetDim);
                if (rawBmp != null && targetW > 0 && targetH > 0
                        && (rawBmp.getWidth() != targetW || rawBmp.getHeight() != targetH)) {
                    Bitmap scaled = Bitmap.createScaledBitmap(rawBmp, targetW, targetH, true);
                    if (scaled != rawBmp) {
                        rawBmp.recycle();
                        rawBmp = scaled;
                    }
                }
                final Bitmap finalRaw = rawBmp;
                mainHandler.post(() -> {
                    if (generation != compareGeneration || !keyForLoad.equals(archiveKey) || isFinishing() || isDestroyed()) {
                        if (finalRaw != null && !finalRaw.isRecycled()) finalRaw.recycle();
                        return;
                    }
                    if (rawOriginalBitmap != null && rawOriginalBitmap != finalRaw && !rawOriginalBitmap.isRecycled()) {
                        rawOriginalBitmap.recycle();
                    }
                    rawOriginalBitmap = finalRaw;
                    if (compareState == CompareState.LOADING_ORIGINAL && rawOriginalBitmap != null) {
                        compareState = CompareState.SHOWING_ORIGINAL;
                        if (compareBadge != null) compareBadge.setText("ORIGINAL SENSOR RAW · UNGRADED");
                        photoView.setImageBitmap(rawOriginalBitmap);
                    }
                });
            } catch (Throwable ignored) {
                mainHandler.post(() -> {
                    if (generation == compareGeneration && compareState != CompareState.OFF && compareBadge != null) {
                        compareBadge.setText("NO RAW DATA");
                    }
                });
            } finally {
                RawArchive.unpin(keyForLoad);
            }
        });
    }

    private void startComparing() {
        if (compareState != CompareState.OFF) return;
        if (!currentHasRaw && rawOriginalBitmap == null) return;
        compareState = rawOriginalBitmap != null ? CompareState.SHOWING_ORIGINAL : CompareState.LOADING_ORIGINAL;
        if (compareBadge != null) {
            compareBadge.setText(compareState == CompareState.SHOWING_ORIGINAL
                    ? "ORIGINAL SENSOR RAW · UNGRADED"
                    : "DEVELOPING SENSOR ORIGINAL…");
            compareBadge.setVisibility(View.VISIBLE);
        }
        if (compareButton != null) compareButton.setTextColor(ACCENT);
        hapticClick(photoView);
        if (compareState == CompareState.SHOWING_ORIGINAL) {
            photoView.setImageBitmap(rawOriginalBitmap);
        } else if (archiveKey != null && currentHasRaw) {
            int targetW = developedBitmap != null ? developedBitmap.getWidth() : 2000;
            int targetH = developedBitmap != null ? developedBitmap.getHeight() : 2000;
            preloadRawOriginal(archiveKey, targetW, targetH);
        }
    }

    private void stopComparing() {
        if (compareState == CompareState.OFF) return;
        compareState = CompareState.OFF;
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
            @Override public boolean onScaleBegin(ScaleGestureDetector detector) {
                gestureMode = GestureMode.PINCH_ZOOM;
                return true;
            }
            @Override public boolean onScale(ScaleGestureDetector detector) {
                gestureMode = GestureMode.PINCH_ZOOM;
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
                gestureMode = GestureMode.IDLE;
                return true;
            }

            @Override public void onLongPress(MotionEvent e) {
                if (currentHasRaw) {
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
                    gestureMode = currentScale > 1.05f ? GestureMode.PAN : GestureMode.IDLE;
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    if (event.getPointerCount() > 1) {
                        gestureMode = GestureMode.PINCH_ZOOM;
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (!scaleDetector.isInProgress() && compareState == CompareState.OFF
                            && gestureMode != GestureMode.PINCH_ZOOM) {
                        float dx = event.getX() - startPoint.x;
                        float dy = event.getY() - startPoint.y;

                        if (currentScale <= 1.05f && gestureMode == GestureMode.IDLE
                                && dy > dp(110) && Math.abs(dx) < Math.abs(dy) * 0.7f) {
                            gestureMode = GestureMode.DISMISS_DRAG;
                            finishAfterTransition();
                            return true;
                        }

                        if (gestureMode == GestureMode.PAN || currentScale > 1.05f) {
                            gestureMode = GestureMode.PAN;
                            matrix.set(savedMatrix);
                            matrix.postTranslate(dx, dy);
                            clampMatrix();
                            photoView.setImageMatrix(matrix);
                        }
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (compareState != CompareState.OFF) {
                        stopComparing();
                        gestureMode = GestureMode.IDLE;
                        break;
                    }
                    if (gestureMode != GestureMode.PINCH_ZOOM
                            && gestureMode != GestureMode.DISMISS_DRAG
                            && !scaleDetector.isInProgress()
                            && currentScale <= 1.05f) {
                        float dx = event.getX() - startPoint.x;
                        float dy = event.getY() - startPoint.y;
                        if (Math.abs(dx) > dp(72) && Math.abs(dx) > Math.abs(dy) * 1.3f) {
                            gestureMode = GestureMode.SWIPE_STEP;
                            stepPhoto(dx < 0 ? 1 : -1);
                            gestureMode = GestureMode.IDLE;
                            return true;
                        }
                    }
                    gestureMode = GestureMode.IDLE;
                    if (currentScale < 1.0f) resetZoom();
                    break;
            }
            return true;
        });
    }

    private void refreshPhotoListAsync(Runnable onReady) {
        if (worker.isShutdown()) return;
        final android.content.Context appContext = getApplicationContext();
        worker.execute(() -> {
            List<PhotoRepository.Photo> loaded = PhotoRepository.all(appContext);
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                allPhotos = loaded;
                currentIndex = -1;
                if (photoUri != null) {
                    String target = photoUri.toString();
                    for (int i = 0; i < allPhotos.size(); i++) {
                        PhotoRepository.Photo p = allPhotos.get(i);
                        if (p.uri != null && (target.equals(p.uri.toString()) || (photoName != null && photoName.equals(p.name)))) {
                            currentIndex = i;
                            break;
                        }
                    }
                }
                if (onReady != null) onReady.run();
            });
        });
    }

    private void stepPhoto(int delta) {
        if (allPhotos == null || allPhotos.isEmpty()) {
            refreshPhotoListAsync(() -> {
                if (allPhotos != null && allPhotos.size() > 1 && currentIndex >= 0) stepPhoto(delta);
            });
            return;
        }
        if (allPhotos.size() <= 1 || currentIndex < 0) return;
        int nextIndex = currentIndex + delta;
        if (nextIndex < 0 || nextIndex >= allPhotos.size()) {
            showZoomHud(delta < 0 ? "NEWEST FRAME" : "OLDEST FRAME");
            return;
        }
        hapticClick(photoView);
        stopComparing();
        compareGeneration++;
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
        FrameLayout scrim = new FrameLayout(this);
        LinearLayout sheet = new LinearLayout(this);
        android.app.Dialog dialog = UiKit.createBottomSheetDialog(this, scrim, sheet, 440);

        TextView title = text("DELETE THIS FRAME?", 11, ACCENT, true);
        title.setLetterSpacing(.14f);
        sheet.addView(title);

        TextView sub = text("Removes " + (photoName != null ? photoName : "this photo") + " and its private editable cache. Any saved DNG original stays untouched.", 9, 0xff9ba59e, false);
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
            confirm.setEnabled(false);
            cancel.setEnabled(false);
            final Uri targetUri = photoUri;
            final String targetName = photoName;
            final String targetKey = archiveKey;
            final android.content.Context appContext = getApplicationContext();
            worker.execute(() -> {
                boolean deleted = PhotoRepository.delete(appContext, targetUri, targetName, targetKey);
                List<PhotoRepository.Photo> remaining = deleted ? PhotoRepository.all(appContext) : null;
                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    dialog.dismiss();
                    if (deleted) {
                        Toast.makeText(this, "Frame deleted", Toast.LENGTH_SHORT).show();
                        allPhotos = remaining != null ? remaining : new ArrayList<>();
                        if (allPhotos.isEmpty()) {
                            finish();
                        } else {
                            stopComparing();
                            compareGeneration++;
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
            });
        });

        sheet.addView(actions, new LinearLayout.LayoutParams(-1, -2));
        dialog.show();
    }

    private void clampMatrix() {
        if (photoView != null) {
            PhotoViewerGestureHelper.clampMatrix(matrix, imageWidth, imageHeight, photoView.getWidth(), photoView.getHeight());
        }
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
        float scale = PhotoViewerGestureHelper.computeFitScale(imageWidth, imageHeight, viewW, viewH);
        float dx = (viewW - imageWidth * scale) * 0.5f;
        float dy = (viewH - imageHeight * scale) * 0.5f;
        matrix.setScale(scale, scale);
        matrix.postTranslate(dx, dy);
        photoView.setImageMatrix(matrix);
    }

    private void toggleInfo() {
        infoVisible = !infoVisible;
        infoCard.setVisibility(infoVisible ? View.VISIBLE : View.GONE);
        infoButton.setTextColor(infoVisible ? ACCENT : 0xffd8ded7);
        if (infoVisible) populateInfoData();
    }

    private void populateInfoData() {
        PhotoViewerMetadataSheet.populate(this, infoCard, photoUri, archiveKey, imageWidth, imageHeight);
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

    private void updateEditButtonState(boolean isGif, boolean hasRaw) {
        if (editButton == null) return;
        if (isGif) {
            editButton.setVisibility(View.GONE);
            return;
        }
        editButton.setVisibility(View.VISIBLE);
        if (hasRaw) {
            editButton.setAlpha(1f);
            editButton.setTextColor(0xff1d1d1a);
            editButton.setBackground(round(ACCENT, 16, ACCENT, dp(1)));
        } else {
            editButton.setAlpha(0.55f);
            editButton.setTextColor(0xff9ba59e);
            editButton.setBackground(round(0xd8191e1e, 16, 0x556e7872, dp(1)));
        }
    }

    private void openEditor() {
        if (!currentHasRaw) {
            Toast.makeText(this, "Editable RAW original was cleared or unavailable · Processed JPEG stays intact", Toast.LENGTH_LONG).show();
            return;
        }
        Intent edit = new Intent(this, PhotoEditorActivity.class);
        edit.putExtra(PhotoEditorActivity.EXTRA_KEY, archiveKey);
        edit.putExtra(EXTRA_URI, photoUri == null ? null : photoUri.toString());
        startActivityForResult(edit, EDIT_REQUEST);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == EDIT_REQUEST && resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) {
                photoUri = data.getData();
                String nextName = data.getStringExtra(EXTRA_NAME);
                if (nextName != null) {
                    photoName = nextName;
                    archiveKey = PhotoRepository.archiveKey(nextName);
                }
                stopComparing();
                compareGeneration++;
                if (rawOriginalBitmap != null && !rawOriginalBitmap.isRecycled()) {
                    rawOriginalBitmap.recycle();
                }
                rawOriginalBitmap = null;
                loadPhoto();
                refreshPhotoListAsync(null);
                return;
            }
            loadPhoto();
        }
    }

    private void loadPhoto() {
        if (photoUri == null || photoView == null || imageJob.isClosed()) return;
        final long ticket = imageJob.begin(JobState.Phase.LOADING);
        final Uri uri = photoUri;
        final String name = photoName, key = archiveKey;
        final int maxW = getResources().getDisplayMetrics().widthPixels * 2;
        final int maxH = getResources().getDisplayMetrics().heightPixels * 2;
        final android.content.Context appContext = getApplicationContext();
        currentHasRaw = false;
        if (titleView != null) titleView.setText(name == null ? "LIKA LENS" : name);
        if (compareButton != null) compareButton.setVisibility(View.GONE);
        if (editButton != null) editButton.setVisibility(View.GONE);
        stopAnimatedPhoto();
        worker.execute(() -> {
            if (!imageJob.accepts(ticket)) return;
            try {
                final boolean hasRaw = key != null && RawArchive.exists(appContext, key);
                if (name != null && name.toLowerCase(Locale.US).endsWith(".gif") && Build.VERSION.SDK_INT >= 28) {
                    try {
                        android.graphics.drawable.Drawable drawable = android.graphics.ImageDecoder.decodeDrawable(
                                android.graphics.ImageDecoder.createSource(appContext.getContentResolver(), uri));
                        mainHandler.post(() -> {
                            if (!imageJob.move(ticket, JobState.Phase.READY) || isDestroyed() || isFinishing()) return;
                            currentHasRaw = hasRaw;
                            if (compareButton != null) compareButton.setVisibility(View.GONE);
                            updateEditButtonState(true, hasRaw);
                            imageWidth = drawable.getIntrinsicWidth();
                            imageHeight = drawable.getIntrinsicHeight();
                            currentScale = 1f;
                            photoView.setImageDrawable(drawable);
                            fitImageToView();
                            if (viewerActive && drawable instanceof android.graphics.drawable.AnimatedImageDrawable)
                                ((android.graphics.drawable.AnimatedImageDrawable) drawable).start();
                            photoView.post(this::fitImageToView);
                        });
                        return;
                    } catch (Exception decodeError) {
                        android.util.Log.w("PhotoViewerActivity", "ImageDecoder failed, falling back to BitmapFactory", decodeError);
                    }
                }
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                try (InputStream in = appContext.getContentResolver().openInputStream(uri)) {
                    BitmapFactory.decodeStream(in, null, options);
                }
                int sample = 1;
                while (options.outWidth / sample > maxW || options.outHeight / sample > maxH) sample *= 2;
                options.inJustDecodeBounds = false;
                options.inSampleSize = sample;
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                final Bitmap decoded;
                try (InputStream in = appContext.getContentResolver().openInputStream(uri)) {
                    decoded = in == null ? null : BitmapFactory.decodeStream(in, null, options);
                }
                if (decoded == null) throw new IllegalStateException("Could not decode photo");
                final Bitmap bitmap = CaptureOrientation.orientBitmap(appContext, uri, decoded);
                mainHandler.post(() -> {
                    if (!imageJob.move(ticket, JobState.Phase.READY) || isDestroyed() || isFinishing()) {
                        bitmap.recycle();
                        return;
                    }
                    currentHasRaw = hasRaw;
                    boolean isGif = name != null && name.toLowerCase(Locale.US).endsWith(".gif");
                    if (compareButton != null) compareButton.setVisibility(!isGif && hasRaw ? View.VISIBLE : View.GONE);
                    updateEditButtonState(isGif, hasRaw);
                    Bitmap old = developedBitmap;
                    developedBitmap = bitmap;
                    imageWidth = bitmap.getWidth();
                    imageHeight = bitmap.getHeight();
                    currentScale = 1f;
                    photoView.setImageBitmap(bitmap);
                    fitImageToView();
                    photoView.post(this::fitImageToView);
                    if (old != null && old != bitmap && !old.isRecycled()) old.recycle();
                    if (infoVisible) populateInfoData();
                    if (hasRaw && rawOriginalBitmap == null) preloadRawOriginal(key, imageWidth, imageHeight);
                });
            } catch (Exception error) {
                mainHandler.post(() -> {
                    if (imageJob.move(ticket, JobState.Phase.FAILED) && !isDestroyed())
                        Toast.makeText(this, "Could not open this frame.", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void stopAnimatedPhoto() {
        if (Build.VERSION.SDK_INT >= 28 && photoView != null && photoView.getDrawable() instanceof android.graphics.drawable.AnimatedImageDrawable)
            ((android.graphics.drawable.AnimatedImageDrawable) photoView.getDrawable()).stop();
    }
    @Override protected void onResume() {
        super.onResume(); viewerActive = true;
        if (Build.VERSION.SDK_INT >= 28 && photoView != null && photoView.getDrawable() instanceof android.graphics.drawable.AnimatedImageDrawable)
            ((android.graphics.drawable.AnimatedImageDrawable) photoView.getDrawable()).start();
    }
    @Override protected void onPause() { viewerActive = false; stopAnimatedPhoto(); super.onPause(); }

    private TextView text(String value, float size, int color, boolean bold) {
        return UiKit.text(this, value == null ? "LIKA LENS FRAME" : value, size, color, bold);
    }

    private TextView pill(String label, boolean active) {
        return UiKit.pill(this, label, active, 8f, 16f,
                ACCENT, 0xffd8ded7, 0xff1d1d1a, 0xd8191e1e, ACCENT, 0x556e7872);
    }

    private android.graphics.drawable.GradientDrawable round(int color, int radius, int strokeColor, int strokeWidth) {
        return UiKit.round(this, color, radius, strokeColor, strokeWidth);
    }

    private int dp(float value) { return UiKit.dp(this, value); }

    @Override protected void onDestroy() {
        imageJob.close(); compareGeneration++;
        stopAnimatedPhoto();
        mainHandler.removeCallbacksAndMessages(null);
        worker.shutdown();
        if (photoView != null) photoView.setImageDrawable(null);
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
