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
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;
import android.util.Log;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Full-screen viewer with interactive pinch-to-zoom, video playback, EXIF metadata inspection, sharing, and editor access. */
public class PhotoViewerActivity extends Activity {
    private static final String TAG = "PhotoViewerActivity";
    static final String EXTRA_URI = "scope_image_uri";
    static final String EXTRA_NAME = "scope_image_name";
    static final String EXTRA_FROM_LIBRARY = "scope_from_library";
    static final String EXTRA_IMPORTED = "scope_imported";
    static final String EXTRA_IS_VIDEO = "scope_is_video";
    static final String EXTRA_DURATION_MS = "scope_duration_ms";
    static final String EXTRA_OPEN_THUMBNAIL = "scope_open_thumbnail";
    static final String EXTRA_OPEN_BOUNDS = "scope_open_bounds";
    private static final int EDIT_REQUEST = 51;
    private static final int BG = 0xff0b0d0e;
    private static final int ACCENT = 0xffd7b574;

    private ImageView photoView;
    private ImageView openingThumbnail;
    private boolean openingAnimationFinished;
    private boolean openingMediaReady;
    private FrameLayout videoContainer;
    private VideoView videoView;
    private View videoGestureOverlay;
    private TextView videoPlayOverlayButton;
    private LinearLayout videoScrubberBar;
    private TextView videoPlayPauseButton;
    private SeekBar videoSeekBar;
    private TextView videoTimeText;
    private boolean videoPrepared;
    private boolean videoScrubbing;
    private boolean currentIsVideo;
    private long currentVideoDurationMs;
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

    private final Runnable videoProgressTicker = new Runnable() {
        @Override
        public void run() {
            if (!currentIsVideo || videoView == null || isFinishing() || isDestroyed()) return;
            updateVideoPlaybackUi();
            if (videoView.isPlaying()) {
                mainHandler.postDelayed(this, 200L);
            }
        }
    };

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
        currentIsVideo = getIntent().getBooleanExtra(EXTRA_IS_VIDEO, false);
        currentVideoDurationMs = Math.max(0L, getIntent().getLongExtra(EXTRA_DURATION_MS, 0L));

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

        buildVideoPlayerView(root);
        initTouchGestures();

        // Clean top header: Back, readable filename title, Info, and optional Grid
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(10), dp(12), dp(10));
        header.setBackground(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xcc080b0c, 0x00080b0c}));

        TextView back = text("‹", 28, 0xffedf0e9, true);
        back.setGravity(Gravity.CENTER);
        header.addView(back, new LinearLayout.LayoutParams(dp(36), dp(40)));
        back.setOnClickListener(v -> { hapticClick(v); finishAfterTransition(); });

        titleView = text(photoName == null ? "LIKA LENS" : photoName, 11, 0xffe6eae4, true);
        titleView.setPadding(dp(6), 0, dp(8), 0);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        header.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1f));

        infoButton = pill("INFO", false);
        infoButton.setPadding(dp(12), 0, dp(12), 0);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(-2, dp(34));
        infoLp.leftMargin = dp(4);
        header.addView(infoButton, infoLp);
        infoButton.setOnClickListener(v -> { hapticClick(v); toggleInfo(); });

        if (!getIntent().getBooleanExtra(EXTRA_FROM_LIBRARY, false)) {
            TextView library = pill("GRID", false);
            library.setPadding(dp(12), 0, dp(12), 0);
            LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(-2, dp(34));
            gridLp.leftMargin = dp(6);
            header.addView(library, gridLp);
            library.setOnClickListener(v -> { hapticClick(v); startActivity(new Intent(this, GalleryActivity.class)); });
        }

        root.addView(header, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        compareBadge = text("ORIGINAL SENSOR RAW", 8, ACCENT, true);
        compareBadge.setBackground(round(0xdd0b0d0e, 14, ACCENT, dp(1)));
        compareBadge.setPadding(dp(12), dp(4), dp(12), dp(4));
        compareBadge.setVisibility(View.GONE);
        FrameLayout.LayoutParams cbLp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        cbLp.topMargin = dp(62);
        root.addView(compareBadge, cbLp);

        zoomHud = text("100% · FIT", 8, ACCENT, true);
        zoomHud.setBackground(round(0xdd0b0d0e, 12, 0x66d7b574, dp(1)));
        zoomHud.setPadding(dp(10), dp(4), dp(10), dp(4));
        zoomHud.setVisibility(View.GONE);
        FrameLayout.LayoutParams zhLp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        zhLp.bottomMargin = dp(80);
        root.addView(zoomHud, zhLp);

        // Darkroom bottom action deck (keeps header uncluttered and prevents title overlap)
        HorizontalScrollView actionScroll = new HorizontalScrollView(this);
        actionScroll.setHorizontalScrollBarEnabled(false);
        actionScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        actionScroll.setBackground(round(0xe6121617, 22, 0x4439413e, dp(1)));
        actionScroll.setPadding(dp(8), dp(6), dp(8), dp(6));

        LinearLayout bottomActions = new LinearLayout(this);
        bottomActions.setOrientation(LinearLayout.HORIZONTAL);
        bottomActions.setGravity(Gravity.CENTER_VERTICAL);

        compareButton = pill("RAW", false);
        compareButton.setPadding(dp(14), 0, dp(14), 0);
        compareButton.setVisibility(View.GONE);
        LinearLayout.LayoutParams compLp = new LinearLayout.LayoutParams(-2, dp(36));
        compLp.rightMargin = dp(6);
        bottomActions.addView(compareButton, compLp);
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
        editButton.setPadding(dp(14), 0, dp(14), 0);
        editButton.setVisibility(View.GONE);
        LinearLayout.LayoutParams editLp = new LinearLayout.LayoutParams(-2, dp(36));
        editLp.rightMargin = dp(6);
        bottomActions.addView(editButton, editLp);
        editButton.setOnClickListener(v -> { hapticClick(v); openEditor(); });

        TextView gifBtn = pill("GIF", false);
        gifBtn.setPadding(dp(14), 0, dp(14), 0);
        LinearLayout.LayoutParams gifLp = new LinearLayout.LayoutParams(-2, dp(36));
        gifLp.rightMargin = dp(6);
        bottomActions.addView(gifBtn, gifLp);
        gifBtn.setOnClickListener(v -> { hapticClick(v); openGifFromViewer(); });

        TextView shareBtn = pill("SHARE", false);
        shareBtn.setPadding(dp(14), 0, dp(14), 0);
        LinearLayout.LayoutParams shareLp = new LinearLayout.LayoutParams(-2, dp(36));
        shareLp.rightMargin = dp(6);
        bottomActions.addView(shareBtn, shareLp);
        shareBtn.setOnClickListener(v -> { hapticClick(v); sharePhoto(); });

        TextView deleteBtn = pill("DEL", false);
        deleteBtn.setPadding(dp(14), 0, dp(14), 0);
        bottomActions.addView(deleteBtn, new LinearLayout.LayoutParams(-2, dp(36)));
        deleteBtn.setOnClickListener(v -> { hapticClick(v); confirmDeleteCurrentPhoto(); });

        actionScroll.addView(bottomActions, new FrameLayout.LayoutParams(-2, -2));
        FrameLayout.LayoutParams actionLp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        actionLp.leftMargin = dp(12);
        actionLp.rightMargin = dp(12);
        actionLp.bottomMargin = dp(12);
        root.addView(actionScroll, actionLp);

        // EXIF Metadata bottom overlay card
        infoCard = PhotoViewerMetadataSheet.build(this);
        infoCard.setVisibility(View.GONE);
        root.addView(infoCard, getInfoCardLayoutParams());

        setupOpeningAnimation(root, saved);
        setContentView(root);
        refreshPhotoListAsync(null);
        loadPhoto();
    }

    private void setupOpeningAnimation(FrameLayout root, Bundle saved) {
        if (saved != null) return;
        byte[] bytes = getIntent().getByteArrayExtra(EXTRA_OPEN_THUMBNAIL);
        int[] bounds = getIntent().getIntArrayExtra(EXTRA_OPEN_BOUNDS);
        if (bytes == null || bounds == null || bounds.length != 4 || bounds[2] <= 0 || bounds[3] <= 0) return;
        Bitmap preview = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        if (preview == null) return;
        openingThumbnail = new ImageView(this);
        openingThumbnail.setImageBitmap(preview);
        openingThumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
        openingThumbnail.setClickable(true);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(bounds[2], bounds[3]);
        root.addView(openingThumbnail, lp);
        openingThumbnail.setVisibility(View.INVISIBLE);
        root.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
          @Override public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                               int oldLeft, int oldTop, int oldRight, int oldBottom) {
            if (right <= left || bottom <= top) return;
            root.removeOnLayoutChangeListener(this);
            if (openingThumbnail == null || isFinishing() || isDestroyed()) return;
            int[] rootPosition = new int[2];
            root.getLocationOnScreen(rootPosition);
            float startX = bounds[0] - rootPosition[0];
            float startY = bounds[1] - rootPosition[1];
            float scale = Math.min((float) root.getWidth() / preview.getWidth(),
                    (float) root.getHeight() / preview.getHeight());
            int targetWidth = Math.max(1, Math.round(preview.getWidth() * scale));
            int targetHeight = Math.max(1, Math.round(preview.getHeight() * scale));
            float targetX = (root.getWidth() - targetWidth) / 2f;
            float targetY = (root.getHeight() - targetHeight) / 2f;
            openingThumbnail.setX(startX);
            openingThumbnail.setY(startY);
            openingThumbnail.setVisibility(View.VISIBLE);
            android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(340);
            animator.setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f));
            animator.addUpdateListener(animation -> {
                if (openingThumbnail == null) return;
                float t = (float) animation.getAnimatedValue();
                FrameLayout.LayoutParams frame = (FrameLayout.LayoutParams) openingThumbnail.getLayoutParams();
                frame.width = Math.max(1, Math.round(bounds[2] + (targetWidth - bounds[2]) * t));
                frame.height = Math.max(1, Math.round(bounds[3] + (targetHeight - bounds[3]) * t));
                openingThumbnail.setLayoutParams(frame);
                openingThumbnail.setX(startX + (targetX - startX) * t);
                openingThumbnail.setY(startY + (targetY - startY) * t);
            });
            animator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator animation) {
                    openingAnimationFinished = true;
                    finishOpeningAnimationIfReady();
                }
            });
            animator.start();
            // Do not leave the preview blocking the viewer if decoding fails or
            // a device does not report its first rendered video frame.
            mainHandler.postDelayed(() -> {
                openingMediaReady = true;
                finishOpeningAnimationIfReady();
            }, 3000L);
          }
        });
    }

    private void finishOpeningAnimationIfReady() {
        if (!openingAnimationFinished || !openingMediaReady || openingThumbnail == null) return;
        ImageView thumbnail = openingThumbnail;
        openingThumbnail = null;
        thumbnail.animate().alpha(0f).setDuration(160).withEndAction(() -> {
            if (thumbnail.getParent() instanceof ViewGroup) {
                ((ViewGroup) thumbnail.getParent()).removeView(thumbnail);
            }
            thumbnail.setImageDrawable(null);
        }).start();
    }

    private FrameLayout.LayoutParams getInfoCardLayoutParams() {
        WindowMetricsHelper.WindowSizeInfo info = WindowMetricsHelper.computeWindowSizeInfo(this);
        int cardWidth = info.widthDp >= 600 ? dp(420) : ViewGroup.LayoutParams.MATCH_PARENT;
        int gravity = (info.widthDp >= 600 && info.isLandscape)
                ? (Gravity.BOTTOM | Gravity.END)
                : (Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT, gravity);
        lp.setMargins(dp(16), 0, dp(16), dp(currentIsVideo ? 118 : 72));
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

    private void buildVideoPlayerView(FrameLayout root) {
        videoContainer = new FrameLayout(this);
        videoContainer.setBackgroundColor(BG);
        videoContainer.setVisibility(View.GONE);

        videoView = new VideoView(this);
        videoView.setVisibility(View.GONE);
        videoContainer.addView(videoView, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));

        videoGestureOverlay = new View(this);
        videoContainer.addView(videoGestureOverlay, new FrameLayout.LayoutParams(-1, -1));

        videoPlayOverlayButton = text("▶", 24, ACCENT, true);
        videoPlayOverlayButton.setGravity(Gravity.CENTER);
        videoPlayOverlayButton.setBackground(round(0xcc0b0d0e, 32, ACCENT, dp(1)));
        videoPlayOverlayButton.setVisibility(View.GONE);
        videoContainer.addView(videoPlayOverlayButton, new FrameLayout.LayoutParams(dp(64), dp(64), Gravity.CENTER));
        videoPlayOverlayButton.setOnClickListener(v -> { hapticClick(v); toggleVideoPlayback(); });

        videoScrubberBar = new LinearLayout(this);
        videoScrubberBar.setOrientation(LinearLayout.HORIZONTAL);
        videoScrubberBar.setGravity(Gravity.CENTER_VERTICAL);
        videoScrubberBar.setBackground(round(0xdd121617, 16, 0x4439413e, dp(1)));
        videoScrubberBar.setPadding(dp(10), dp(6), dp(10), dp(6));

        videoPlayPauseButton = text("❚❚", 11, ACCENT, true);
        videoPlayPauseButton.setGravity(Gravity.CENTER);
        videoScrubberBar.addView(videoPlayPauseButton, new LinearLayout.LayoutParams(dp(32), dp(28)));
        videoPlayPauseButton.setOnClickListener(v -> { hapticClick(v); toggleVideoPlayback(); });

        videoSeekBar = new SeekBar(this);
        videoSeekBar.getThumb().setTint(ACCENT);
        videoSeekBar.getProgressDrawable().setTint(ACCENT);
        LinearLayout.LayoutParams seekLp = new LinearLayout.LayoutParams(0, -2, 1f);
        seekLp.leftMargin = dp(4);
        seekLp.rightMargin = dp(6);
        videoScrubberBar.addView(videoSeekBar, seekLp);

        videoTimeText = text("0:00 / 0:00", 9, 0xffe6eae4, true);
        videoScrubberBar.addView(videoTimeText, new LinearLayout.LayoutParams(-2, -2));

        FrameLayout.LayoutParams scrubLp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        scrubLp.leftMargin = dp(16);
        scrubLp.rightMargin = dp(16);
        scrubLp.bottomMargin = dp(68);
        videoContainer.addView(videoScrubberBar, scrubLp);

        videoSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && videoPrepared && videoView != null) {
                    int dur = Math.max(0, videoView.getDuration());
                    if (dur > 0) {
                        videoTimeText.setText(PhotoRepository.formatDuration(progress) + " / " + PhotoRepository.formatDuration(dur));
                    }
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {
                videoScrubbing = true;
            }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                videoScrubbing = false;
                if (videoPrepared && videoView != null) {
                    videoView.seekTo(seekBar.getProgress());
                    updateVideoPlaybackUi();
                }
            }
        });

        videoView.setOnPreparedListener(mp -> {
            if (!currentIsVideo || isFinishing() || isDestroyed()) return;
            videoPrepared = true;
            mp.setLooping(false);
            int dur = Math.max(0, videoView.getDuration());
            if (dur > 0) currentVideoDurationMs = dur;
            if (videoSeekBar != null) {
                videoSeekBar.setMax(Math.max(1, (int) currentVideoDurationMs));
            }
            if (viewerActive) {
                videoView.start();
            } else {
                videoView.seekTo(1);
            }
            updateVideoPlaybackUi();
            mainHandler.removeCallbacks(videoProgressTicker);
            mainHandler.post(videoProgressTicker);
        });
        videoView.setOnInfoListener((mp, what, extra) -> {
            if (what == android.media.MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                openingMediaReady = true;
                finishOpeningAnimationIfReady();
            }
            return false;
        });
        videoView.setOnCompletionListener(mp -> {
            mainHandler.removeCallbacks(videoProgressTicker);
            if (videoView != null && videoPrepared) {
                videoView.seekTo(0);
            }
            updateVideoPlaybackUi();
        });
        videoView.setOnErrorListener((mp, what, extra) -> {
            videoPrepared = false;
            openingMediaReady = true;
            finishOpeningAnimationIfReady();
            mainHandler.removeCallbacks(videoProgressTicker);
            Toast.makeText(this, "Could not play this video.", Toast.LENGTH_SHORT).show();
            return true;
        });

        final float[] touchDown = new float[2];
        videoGestureOverlay.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    touchDown[0] = event.getX();
                    touchDown[1] = event.getY();
                    return true;
                case MotionEvent.ACTION_UP:
                    float dx = event.getX() - touchDown[0];
                    float dy = event.getY() - touchDown[1];
                    if (dy > dp(110) && Math.abs(dx) < Math.abs(dy) * 0.7f) {
                        finishAfterTransition();
                        return true;
                    }
                    if (Math.abs(dx) > dp(72) && Math.abs(dx) > Math.abs(dy) * 1.3f) {
                        stepPhoto(dx < 0 ? 1 : -1);
                        return true;
                    }
                    if (Math.abs(dx) < dp(16) && Math.abs(dy) < dp(16)) {
                        toggleVideoPlayback();
                    }
                    return true;
            }
            return false;
        });

        root.addView(videoContainer, new FrameLayout.LayoutParams(-1, -1));
    }

    private void toggleVideoPlayback() {
        if (!currentIsVideo || videoView == null || !videoPrepared) return;
        if (videoView.isPlaying()) {
            videoView.pause();
            mainHandler.removeCallbacks(videoProgressTicker);
        } else {
            videoView.start();
            mainHandler.removeCallbacks(videoProgressTicker);
            mainHandler.post(videoProgressTicker);
        }
        updateVideoPlaybackUi();
    }

    private void updateVideoPlaybackUi() {
        if (!currentIsVideo || videoView == null) return;
        boolean playing = videoPrepared && videoView.isPlaying();
        if (videoPlayOverlayButton != null) {
            videoPlayOverlayButton.setVisibility(playing ? View.GONE : View.VISIBLE);
        }
        if (videoPlayPauseButton != null) {
            videoPlayPauseButton.setText(playing ? "❚❚" : "▶");
        }
        int dur = videoPrepared ? Math.max(0, videoView.getDuration()) : (int) Math.max(0L, currentVideoDurationMs);
        int pos = videoPrepared ? Math.max(0, videoView.getCurrentPosition()) : 0;
        if (videoSeekBar != null && !videoScrubbing) {
            if (dur > 0 && videoSeekBar.getMax() != dur) videoSeekBar.setMax(dur);
            videoSeekBar.setProgress(Math.min(pos, Math.max(1, videoSeekBar.getMax())));
        }
        if (videoTimeText != null) {
            videoTimeText.setText(PhotoRepository.formatDuration(pos) + " / " + PhotoRepository.formatDuration(dur));
        }
    }

    private void stopVideoPlayback() {
        mainHandler.removeCallbacks(videoProgressTicker);
        videoPrepared = false;
        videoScrubbing = false;
        if (videoView != null) {
            try {
                videoView.stopPlayback();
            } catch (Throwable ignored) {
            }
            videoView.setVisibility(View.GONE);
        }
        if (videoContainer != null) {
            videoContainer.setVisibility(View.GONE);
        }
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
            showZoomHud(delta < 0 ? "NEWEST" : "OLDEST");
            return;
        }
        hapticClick(photoView);
        stopComparing();
        compareGeneration++;
        currentIndex = nextIndex;
        PhotoRepository.Photo next = allPhotos.get(currentIndex);
        photoUri = next.uri;
        photoName = next.name;
        currentIsVideo = next.isVideo;
        currentVideoDurationMs = next.durationMs;
        archiveKey = PhotoRepository.archiveKey(next.name);
        if (rawOriginalBitmap != null && !rawOriginalBitmap.isRecycled()) {
            rawOriginalBitmap.recycle();
        }
        rawOriginalBitmap = null;
        if (next.isVideo || photoView.getVisibility() != View.VISIBLE) {
            loadPhoto();
        } else {
            photoView.animate().alpha(0.35f).setDuration(80).withEndAction(() -> {
                loadPhoto();
                photoView.animate().alpha(1f).setDuration(140).start();
            }).start();
        }
    }

    private void confirmDeleteCurrentPhoto() {
        if (photoUri == null) return;
        FrameLayout scrim = new FrameLayout(this);
        LinearLayout sheet = new LinearLayout(this);
        android.app.Dialog dialog = UiKit.createBottomSheetDialog(this, scrim, sheet, 440);

        TextView title = text(currentIsVideo ? "DELETE THIS VIDEO?" : "DELETE THIS FRAME?", 11, ACCENT, true);
        title.setLetterSpacing(.14f);
        sheet.addView(title);

        String defaultName = currentIsVideo ? "this video" : "this photo";
        TextView sub = text(currentIsVideo
                ? ("Removes " + (photoName != null ? photoName : defaultName) + " from Movies / Lika Lens. This cannot be undone.")
                : ("Removes " + (photoName != null ? photoName : defaultName) + " and its private editable cache. Any saved DNG original stays untouched."),
                9, 0xff9ba59e, false);
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
            final boolean wasVideo = currentIsVideo;
            final android.content.Context appContext = getApplicationContext();
            if (wasVideo) stopVideoPlayback();
            worker.execute(() -> {
                boolean deleted = PhotoRepository.delete(appContext, targetUri, targetName, targetKey);
                List<PhotoRepository.Photo> remaining = deleted ? PhotoRepository.all(appContext) : null;
                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    dialog.dismiss();
                    if (deleted) {
                        Toast.makeText(this, wasVideo ? "Video deleted" : "Frame deleted", Toast.LENGTH_SHORT).show();
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
                            currentIsVideo = next.isVideo;
                            currentVideoDurationMs = next.durationMs;
                            archiveKey = PhotoRepository.archiveKey(next.name);
                            if (rawOriginalBitmap != null && !rawOriginalBitmap.isRecycled()) {
                                rawOriginalBitmap.recycle();
                            }
                            rawOriginalBitmap = null;
                            loadPhoto();
                        }
                    } else {
                        Toast.makeText(this, "Could not delete this item", Toast.LENGTH_SHORT).show();
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
        if (infoCard != null) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) infoCard.getLayoutParams();
            int desiredBottom = dp(currentIsVideo ? 118 : 72);
            if (lp != null && lp.bottomMargin != desiredBottom) {
                lp.bottomMargin = desiredBottom;
                infoCard.setLayoutParams(lp);
            }
            infoCard.setVisibility(infoVisible ? View.VISIBLE : View.GONE);
        }
        infoButton.setTextColor(infoVisible ? ACCENT : 0xffd8ded7);
        if (infoVisible) populateInfoData();
    }

    private void populateInfoData() {
        PhotoViewerMetadataSheet.populate(this, infoCard, photoUri, archiveKey, imageWidth, imageHeight);
    }

    private boolean isCurrentItemVideo() {
        if (currentIsVideo) return true;
        if (photoName != null && photoName.toLowerCase(Locale.US).endsWith(".mp4")) return true;
        if (photoUri != null) {
            try {
                String mime = getContentResolver().getType(photoUri);
                if (mime != null && mime.startsWith("video/")) return true;
            } catch (Throwable ignored) { }
        }
        return false;
    }

    private void sharePhoto() {
        if (photoUri == null) return;
        try {
            boolean isVideo = isCurrentItemVideo();
            boolean isGif = !isVideo && photoName != null && photoName.toLowerCase(Locale.US).endsWith(".gif");
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType(isVideo ? "video/mp4" : (isGif ? "image/gif" : "image/jpeg"));
            share.putExtra(Intent.EXTRA_STREAM, photoUri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share,
                    isVideo ? "Share Lika Lens Video" : (isGif ? "Share Lika Lens GIF" : "Share Lika Lens Photo")));
        } catch (Exception e) {
            Toast.makeText(this, "Could not share this item.", Toast.LENGTH_SHORT).show();
        }
    }

    private void openGifFromViewer() {
        if (photoUri == null) return;
        if (isCurrentItemVideo()) {
            Intent intent = new Intent(this, GifStudioActivity.class);
            intent.putExtra(GifStudioActivity.EXTRA_VIDEO_URI, photoUri.toString());
            startActivity(intent);
            return;
        }
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
                if (!other.isGif && !other.isVideo && Math.abs(other.timestampMillis - current.timestampMillis) <= 15000L) {
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
        if (isGif || currentIsVideo) {
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
                currentIsVideo = false;
                currentVideoDurationMs = 0L;
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
        currentIsVideo = isCurrentItemVideo();
        if (titleView != null) titleView.setText(name == null ? "LIKA LENS" : name);
        if (compareButton != null) compareButton.setVisibility(View.GONE);
        if (editButton != null) editButton.setVisibility(View.GONE);
        stopAnimatedPhoto();
        stopVideoPlayback();

        if (currentIsVideo) {
            photoView.setVisibility(View.GONE);
            photoView.setImageDrawable(null);
            if (infoCard != null) {
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) infoCard.getLayoutParams();
                if (lp != null && lp.bottomMargin != dp(118)) {
                    lp.bottomMargin = dp(118);
                    infoCard.setLayoutParams(lp);
                }
            }
            if (videoContainer != null && videoView != null) {
                videoContainer.setVisibility(View.VISIBLE);
                videoView.setVisibility(View.VISIBLE);
                if (videoPlayOverlayButton != null) videoPlayOverlayButton.setVisibility(View.GONE);
                if (videoSeekBar != null) {
                    videoSeekBar.setMax((int) Math.max(1L, currentVideoDurationMs));
                    videoSeekBar.setProgress(0);
                }
                if (videoTimeText != null) {
                    videoTimeText.setText("0:00 / " + PhotoRepository.formatDuration(currentVideoDurationMs));
                }
                videoView.setVideoURI(uri);
                imageJob.move(ticket, JobState.Phase.READY);
                if (infoVisible) populateInfoData();
            } else {
                imageJob.move(ticket, JobState.Phase.FAILED);
            }
            return;
        }

        if (infoCard != null) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) infoCard.getLayoutParams();
            if (lp != null && lp.bottomMargin != dp(72)) {
                lp.bottomMargin = dp(72);
                infoCard.setLayoutParams(lp);
            }
        }
        photoView.setVisibility(View.VISIBLE);
        photoView.setAlpha(1f);

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
                            openingMediaReady = true;
                            finishOpeningAnimationIfReady();
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
                    openingMediaReady = true;
                    finishOpeningAnimationIfReady();
                    fitImageToView();
                    photoView.post(this::fitImageToView);
                    if (old != null && old != bitmap && !old.isRecycled()) old.recycle();
                    if (infoVisible) populateInfoData();
                    if (hasRaw && rawOriginalBitmap == null) preloadRawOriginal(key, imageWidth, imageHeight);
                });
            } catch (Exception error) {
                mainHandler.post(() -> {
                    if (imageJob.move(ticket, JobState.Phase.FAILED) && !isDestroyed()) {
                        openingMediaReady = true;
                        finishOpeningAnimationIfReady();
                        Toast.makeText(this, "Could not open this frame.", Toast.LENGTH_SHORT).show();
                    }
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
        if (currentIsVideo && videoView != null && videoPrepared) {
            updateVideoPlaybackUi();
        }
    }
    @Override protected void onPause() {
        viewerActive = false;
        stopAnimatedPhoto();
        mainHandler.removeCallbacks(videoProgressTicker);
        if (currentIsVideo && videoView != null && videoView.isPlaying()) {
            videoView.pause();
            updateVideoPlaybackUi();
        }
        super.onPause();
    }

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
        stopVideoPlayback();
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
