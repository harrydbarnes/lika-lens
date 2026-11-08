package com.scope.camera;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Leica-styled Animated GIF Studio.
 *
 * Supports:
 * - Creating animated GIFs from a sequence of photos (with auto-burst detection or manual selection).
 * - Extracting animated GIFs from video recordings (.mp4) with duration trimming and rotation handling.
 * - Live animated preview loop before exporting.
 * - Frame rate selection (2 fps, 5 fps, 10 fps, 15 fps).
 * - Resolution scaling (320px, 480px, 640px).
 * - Boomerang / Bounce looping mode.
 * - Leica 3D film color simulation (Portra, Fuji, Tri-X, Ansel B&W, Teal & Orange).
 * - Full adaptive layout: dual-pane on tablets/landscape, single-column on phones.
 */
public class GifStudioActivity extends Activity implements GifStudioControlsBuilder.Listener {

    public static final String EXTRA_PHOTO_URIS = "extra_photo_uris";
    public static final String EXTRA_VIDEO_URI = "extra_video_uri";

    private static final int BG = 0xff0b0d0e;
    private static final int ACCENT = 0xffd7b574;
    private static final int MUTED = 0xff8c9690;

    private static final int PICK_PHOTO_REQUEST = 61;
    private static final int PICK_VIDEO_REQUEST = 62;

    // Source state
    private boolean isVideoMode = false;
    private final List<Uri> photoUris = new ArrayList<>();
    private Uri videoUri = null;
    private long videoDurationMs = 0;
    private int videoRotation = 0;

    // Tuning options
    private int selectedFps = 10; // 2, 5, 10, 15
    private int selectedResolution = 480; // 320, 480, 640
    private boolean bounceMode = false;
    private String selectedLutProfile = "NONE";

    // Video range options (in seconds)
    private int videoStartSec = 0;
    private int videoDurationSec = 3;

    // Live preview state
    private ImageView previewImageView;
    private TextView previewTimecode;
    private final List<Bitmap> previewFrames = new ArrayList<>();
    private int currentPreviewIndex = 0;
    private boolean isPreviewPlaying = true;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    // UI elements
    private FrameLayout rootLayout;
    private TextView modePhotosTab;
    private TextView modeVideoTab;

    private final GifStudioControlsBuilder controlsBuilder = new GifStudioControlsBuilder(this, this);

    @Override public void onFpsSelected(int fps) { selectedFps = fps; loadPreviewFramesAsync(); }
    @Override public void onResolutionSelected(int res) { selectedResolution = res; }
    @Override public void onBounceModeChanged(boolean bounce) { bounceMode = bounce; loadPreviewFramesAsync(); }
    @Override public void onLutProfileSelected(String profile) { selectedLutProfile = profile; loadPreviewFramesAsync(); }
    @Override public void onVideoDurationSelected(int seconds) { videoDurationSec = seconds; loadPreviewFramesAsync(); }
    @Override public void onAddPhotosClicked() { promptPickPhotos(); }
    @Override public void onChooseDifferentVideoClicked() { promptPickVideo(); }
    @Override public void onExportClicked() { startGifExport(); }
    @Override
    public void onPhotoRemoved(int index) {
        if (index >= 0 && index < photoUris.size()) {
            photoUris.remove(index);
            refreshFilmstripUi();
            loadPreviewFramesAsync();
        }
    }


    private final Runnable previewLoopRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isPreviewPlaying || previewFrames.isEmpty()) return;
            currentPreviewIndex = (currentPreviewIndex + 1) % previewFrames.size();
            if (previewImageView != null && currentPreviewIndex < previewFrames.size()) {
                Bitmap bmp = previewFrames.get(currentPreviewIndex);
                if (bmp != null && !bmp.isRecycled()) {
                    previewImageView.setImageBitmap(bmp);
                }
            }
            if (previewTimecode != null) {
                previewTimecode.setText(String.format(Locale.US, "FRAME %d / %d · %d FPS",
                        currentPreviewIndex + 1, previewFrames.size(), selectedFps));
            }
            int delayMs = Math.max(20, 1000 / selectedFps);
            mainHandler.postDelayed(this, delayMs);
        }
    };

    private void hapticClick(View v) {
        if (v != null && AppSettings.getBoolean(this, AppSettings.HAPTICS, true)) {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        // Parse initial extras
        ArrayList<String> uriStrings = getIntent().getStringArrayListExtra(EXTRA_PHOTO_URIS);
        if (uriStrings != null) {
            for (String s : uriStrings) photoUris.add(Uri.parse(s));
        }

        String vUri = getIntent().getStringExtra(EXTRA_VIDEO_URI);
        if (vUri != null) {
            videoUri = Uri.parse(vUri);
            isVideoMode = true;
        }

        // If nothing passed, default to loading latest photos from library
        if (photoUris.isEmpty() && videoUri == null) {
            List<PhotoRepository.Photo> all = PhotoRepository.all(this);
            int count = Math.min(5, all.size());
            for (int i = 0; i < count; i++) {
                if (!all.get(i).isGif) photoUris.add(all.get(i).uri);
            }
        }

        buildUi();
        loadPreviewFramesAsync();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startPreviewPlayback();
    }

    @Override
    protected void onPause() {
        stopPreviewPlayback();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        stopPreviewPlayback();
        recyclePreviewFrames();
        worker.shutdown();
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        buildUi();
        updatePreviewBitmap();
    }

    private void buildUi() {
        WindowMetricsHelper.WindowSizeInfo windowInfo = WindowMetricsHelper.computeWindowSizeInfo(this);
        boolean isWide = windowInfo.isTwoPaneRecommended || (windowInfo.widthDp >= 600 && windowInfo.isLandscape);

        rootLayout = new FrameLayout(this);
        rootLayout.setBackgroundColor(BG);
        rootLayout.setFitsSystemWindows(true);

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(isWide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);

        // Left Pane (Preview + playback HUD)
        View previewPane = buildPreviewPane(isWide);
        // Right Pane (Controls, filmstrip, tuning, export)
        View controlsPane = buildControlsPane(isWide);

        if (isWide) {
            LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(0, -1, 1.1f);
            mainLayout.addView(previewPane, leftLp);

            LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(0, -1, 0.9f);
            mainLayout.addView(controlsPane, rightLp);
        } else {
            LinearLayout.LayoutParams topLp = new LinearLayout.LayoutParams(-1, dp(260));
            mainLayout.addView(previewPane, topLp);

            LinearLayout.LayoutParams bottomLp = new LinearLayout.LayoutParams(-1, 0, 1f);
            mainLayout.addView(controlsPane, bottomLp);
        }

        rootLayout.addView(mainLayout, new FrameLayout.LayoutParams(-1, -1));
        setContentView(rootLayout);
    }

    private View buildPreviewPane(boolean isWide) {
        FrameLayout container = new FrameLayout(this);
        container.setBackgroundColor(0xff080a0b);

        previewImageView = new ImageView(this);
        previewImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        previewImageView.setOnClickListener(v -> togglePreviewPlayPause());
        container.addView(previewImageView, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));

        // Top bar overlay: Close button & Mode tabs
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(12), dp(10), dp(12), dp(10));
        topBar.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xdd080b0c, 0x00080b0c}));

        TextView back = text("‹", 28, 0xffedf0e9, true);
        back.setGravity(Gravity.CENTER);
        topBar.addView(back, new LinearLayout.LayoutParams(dp(36), dp(40)));
        back.setOnClickListener(v -> { hapticClick(v); finish(); });

        TextView title = text("GIF STUDIO", 11, 0xffedf0e9, true);
        title.setLetterSpacing(0.12f);
        title.setPadding(dp(8), 0, 0, 0);
        topBar.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        modePhotosTab = controlsBuilder.pill("PHOTOS", !isVideoMode);
        modePhotosTab.setOnClickListener(v -> {
            hapticClick(v);
            if (isVideoMode) {
                isVideoMode = false;
                updateModeUi();
                loadPreviewFramesAsync();
            }
        });
        topBar.addView(modePhotosTab, new LinearLayout.LayoutParams(-2, dp(30)));

        modeVideoTab = controlsBuilder.pill("VIDEO", isVideoMode);
        LinearLayout.LayoutParams mvLp = new LinearLayout.LayoutParams(-2, dp(30));
        mvLp.leftMargin = dp(6);
        topBar.addView(modeVideoTab, mvLp);
        modeVideoTab.setOnClickListener(v -> {
            hapticClick(v);
            if (!isVideoMode) {
                isVideoMode = true;
                if (videoUri == null) promptPickVideo();
                else {
                    updateModeUi();
                    loadPreviewFramesAsync();
                }
            }
        });

        container.addView(topBar, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        // Bottom playback timecode badge
        LinearLayout bottomInfo = new LinearLayout(this);
        bottomInfo.setOrientation(LinearLayout.HORIZONTAL);
        bottomInfo.setGravity(Gravity.CENTER_VERTICAL);
        bottomInfo.setPadding(dp(14), dp(8), dp(14), dp(8));
        bottomInfo.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xdd080b0c, 0x00080b0c}));

        previewTimecode = text("LOADING PREVIEW…", 8, ACCENT, true);
        previewTimecode.setLetterSpacing(0.08f);
        bottomInfo.addView(previewTimecode, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView playPauseBtn = text("TAP TO PAUSE", 8, MUTED, false);
        bottomInfo.addView(playPauseBtn);

        container.addView(bottomInfo, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        return container;
    }

    private View buildControlsPane(boolean isWide) {
        return controlsBuilder.buildControlsPane(
                isVideoMode,
                selectedFps,
                selectedResolution,
                bounceMode,
                selectedLutProfile,
                videoDurationSec,
                photoUris
        );
    }

    private void refreshFilmstripUi() {
        controlsBuilder.refreshFilmstripUi(photoUris);
    }

    private void updateModeUi() {
        controlsBuilder.applyPillStyle(modePhotosTab, !isVideoMode);
        controlsBuilder.applyPillStyle(modeVideoTab, isVideoMode);
        if (controlsBuilder.photoControlsGroup != null) {
            controlsBuilder.photoControlsGroup.setVisibility(isVideoMode ? View.GONE : View.VISIBLE);
        }
        if (controlsBuilder.videoControlsGroup != null) {
            controlsBuilder.videoControlsGroup.setVisibility(isVideoMode ? View.VISIBLE : View.GONE);
        }
    }


    private void togglePreviewPlayPause() {
        hapticClick(previewImageView);
        isPreviewPlaying = !isPreviewPlaying;
        if (isPreviewPlaying) startPreviewPlayback();
        else stopPreviewPlayback();
    }

    private void startPreviewPlayback() {
        isPreviewPlaying = true;
        mainHandler.removeCallbacks(previewLoopRunnable);
        mainHandler.post(previewLoopRunnable);
    }

    private void stopPreviewPlayback() {
        mainHandler.removeCallbacks(previewLoopRunnable);
    }

    private void updatePreviewBitmap() {
        if (!previewFrames.isEmpty() && previewImageView != null) {
            Bitmap bmp = previewFrames.get(Math.min(currentPreviewIndex, previewFrames.size() - 1));
            if (bmp != null && !bmp.isRecycled()) {
                previewImageView.setImageBitmap(bmp);
            }
        }
    }

    // =========================================================================
    // Frame Extraction & Preview Loading
    // =========================================================================
    private void loadPreviewFramesAsync() {
        stopPreviewPlayback();
        if (previewTimecode != null) previewTimecode.setText("PREPARING FRAMES…");

        worker.execute(() -> {
            List<Bitmap> loaded = GifMediaHelper.loadPreviewFrames(
                    this,
                    isVideoMode,
                    videoUri,
                    photoUris,
                    selectedFps,
                    videoStartSec,
                    videoDurationSec,
                    selectedLutProfile,
                    bounceMode,
                    (dur, rot) -> {
                        videoDurationMs = dur;
                        videoRotation = rot;
                    }
            );

            final List<Bitmap> finalFrames = loaded;
            mainHandler.post(() -> {
                recyclePreviewFrames();
                previewFrames.addAll(finalFrames);
                currentPreviewIndex = 0;
                if (!previewFrames.isEmpty()) {
                    updatePreviewBitmap();
                    startPreviewPlayback();
                } else {
                    if (previewTimecode != null) {
                        previewTimecode.setText(isVideoMode ? "SELECT A VIDEO TO START" : "SELECT PHOTOS TO START");
                    }
                }
            });
        });
    }

    private void recyclePreviewFrames() {
        for (Bitmap b : previewFrames) {
            if (b != null && !b.isRecycled()) b.recycle();
        }
        previewFrames.clear();
    }

    // =========================================================================
    // GIF Export Pipeline
    // =========================================================================
    private void startGifExport() {
        GifExporter.Params params = new GifExporter.Params();
        params.isVideoMode = isVideoMode;
        params.videoUri = videoUri;
        params.photoUris = photoUris;
        params.targetRes = selectedResolution;
        params.targetFps = selectedFps;
        params.boomerang = bounceMode;
        params.lutProfile = selectedLutProfile;
        params.videoStartSec = videoStartSec;
        params.videoDurationSec = videoDurationSec;

        GifExporter.startExport(this, params, worker, mainHandler);
    }

    // =========================================================================
    // System Pickers
    // =========================================================================
    private void promptPickPhotos() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(Intent.createChooser(intent, "Select Photos for GIF"), PICK_PHOTO_REQUEST);
    }

    private void promptPickVideo() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("video/*");
        startActivityForResult(Intent.createChooser(intent, "Select Video for GIF"), PICK_VIDEO_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;

        if (requestCode == PICK_PHOTO_REQUEST) {
            if (data.getClipData() != null) {
                int count = data.getClipData().getItemCount();
                for (int i = 0; i < count; i++) {
                    Uri u = data.getClipData().getItemAt(i).getUri();
                    if (u != null && !photoUris.contains(u)) photoUris.add(u);
                }
            } else if (data.getData() != null) {
                Uri u = data.getData();
                if (!photoUris.contains(u)) photoUris.add(u);
            }
            isVideoMode = false;
            updateModeUi();
            refreshFilmstripUi();
            loadPreviewFramesAsync();
        } else if (requestCode == PICK_VIDEO_REQUEST) {
            if (data.getData() != null) {
                videoUri = data.getData();
                isVideoMode = true;
                updateModeUi();
                loadPreviewFramesAsync();
            }
        }
    }

    // =========================================================================
    // UI Helpers
    // =========================================================================
    private TextView text(String s, int sp, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(s);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        tv.setTypeface(android.graphics.Typeface.MONOSPACE, bold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        return tv;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}

