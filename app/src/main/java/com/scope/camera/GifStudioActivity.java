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
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
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
    private enum SourceMode { PHOTOS, VIDEO }
    private SourceMode sourceMode = SourceMode.PHOTOS;
    private enum ScreenState { ACTIVE, PAUSED, CLOSED }
    private ScreenState screenState = ScreenState.PAUSED;
    private enum Playback { PLAYING, PAUSED }
    private Playback playback = Playback.PLAYING;
    private enum Picker { NONE, PHOTOS, VIDEO }
    private Picker picker = Picker.NONE;
    private final JobState previewJob = new JobState();
    private final JobState exportJob = new JobState();
    private GifStudioDialogs.ProgressHandle exportProgress;
    private boolean isVideoMode() { return sourceMode == SourceMode.VIDEO; }
    private final List<Uri> photoUris = new ArrayList<>();
    private Uri videoUri = null;

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
    private final List<Bitmap> baseFrames = new ArrayList<>();
    private final List<Bitmap> previewFrames = new ArrayList<>();
    private String cachedBaseKey = "";
    private int currentPreviewIndex = 0;
    private final StringBuilder timecodeBuilder = new StringBuilder(32);

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService previewWorker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "GifPreviewWorker");
        t.setDaemon(true);
        return t;
    });
    private final ExecutorService exportWorker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "GifExportWorker");
        t.setDaemon(true);
        return t;
    });

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
            if (screenState != ScreenState.ACTIVE || playback != Playback.PLAYING || previewFrames.isEmpty()) return;
            currentPreviewIndex = (currentPreviewIndex + 1) % previewFrames.size();
            if (previewImageView != null && currentPreviewIndex < previewFrames.size()) {
                Bitmap bmp = previewFrames.get(currentPreviewIndex);
                if (bmp != null && !bmp.isRecycled()) {
                    previewImageView.setImageBitmap(bmp);
                }
            }
            if (previewTimecode != null) {
                timecodeBuilder.setLength(0);
                timecodeBuilder.append("FRAME ")
                        .append(currentPreviewIndex + 1)
                        .append(" / ")
                        .append(previewFrames.size())
                        .append(" · ")
                        .append(selectedFps)
                        .append(" FPS");
                previewTimecode.setText(timecodeBuilder);
            }
            int delayMs = Math.max(20, 1000 / selectedFps);
            mainHandler.postDelayed(this, delayMs);
        }
    };

    private void hapticClick(View v) {
        UiKit.hapticClick(this, v);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        selectedFps = parseOptionInt(AppSettings.get(this, AppSettings.GIF_FPS, "10"), new int[]{2, 5, 10, 15}, 10);
        selectedResolution = parseOptionInt(AppSettings.get(this, AppSettings.GIF_RESOLUTION, "480"), new int[]{320, 480, 640}, 480);
        bounceMode = AppSettings.getBoolean(this, AppSettings.GIF_BOOMERANG, false);

        // Parse initial extras
        ArrayList<String> uriStrings = getIntent().getStringArrayListExtra(EXTRA_PHOTO_URIS);
        if (uriStrings != null) {
            for (String s : uriStrings) photoUris.add(Uri.parse(s));
        }

        String vUri = getIntent().getStringExtra(EXTRA_VIDEO_URI);
        if (vUri != null && !vUri.trim().isEmpty()) {
            videoUri = Uri.parse(vUri);
            sourceMode = SourceMode.VIDEO;
        }

        buildUi();

        // If nothing passed, default to loading latest photos from library off the UI thread
        if (photoUris.isEmpty() && videoUri == null) {
            if (previewTimecode != null) previewTimecode.setText("LOADING LIBRARY…");
            final android.content.Context appCtx = getApplicationContext();
            previewWorker.execute(() -> {
                List<PhotoRepository.Photo> all = PhotoRepository.all(appCtx);
                final List<Uri> initial = new ArrayList<>(5);
                for (int i = 0; i < all.size() && initial.size() < 5; i++) {
                    if (!all.get(i).isGif) initial.add(all.get(i).uri);
                }
                mainHandler.post(() -> {
                    if (screenState == ScreenState.CLOSED || !photoUris.isEmpty() || videoUri != null) return;
                    photoUris.addAll(initial);
                    refreshFilmstripUi();
                    loadPreviewFramesAsync();
                });
            });
        } else {
            loadPreviewFramesAsync();
        }
    }

    private static int parseOptionInt(String raw, int[] allowed, int fallback) {
        if (raw == null || raw.isEmpty()) return fallback;
        int end = 0;
        while (end < raw.length() && Character.isDigit(raw.charAt(end))) end++;
        if (end == 0) return fallback;
        try {
            int parsed = Integer.parseInt(raw.substring(0, end));
            for (int v : allowed) {
                if (v == parsed) return parsed;
            }
        } catch (NumberFormatException ignored) {}
        return fallback;
    }

    @Override
    protected void onResume() {
        super.onResume();
        screenState = ScreenState.ACTIVE;
        startPreviewPlayback();
    }

    @Override
    protected void onPause() {
        screenState = ScreenState.PAUSED;
        stopPreviewPlayback();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        screenState = ScreenState.CLOSED;
        previewJob.close();
        exportJob.close();
        if (exportProgress != null) exportProgress.dismiss();
        stopPreviewPlayback();
        mainHandler.removeCallbacksAndMessages(null);
        controlsBuilder.destroy();
        recycleBaseFrames();
        previewWorker.shutdownNow();
        exportWorker.shutdownNow();
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

        modePhotosTab = controlsBuilder.pill("PHOTOS", !isVideoMode());
        modePhotosTab.setOnClickListener(v -> {
            hapticClick(v);
            if (isVideoMode()) {
                sourceMode = SourceMode.PHOTOS;
                updateModeUi();
                loadPreviewFramesAsync();
            }
        });
        topBar.addView(modePhotosTab, new LinearLayout.LayoutParams(-2, dp(30)));

        modeVideoTab = controlsBuilder.pill("VIDEO", isVideoMode());
        LinearLayout.LayoutParams mvLp = new LinearLayout.LayoutParams(-2, dp(30));
        mvLp.leftMargin = dp(6);
        topBar.addView(modeVideoTab, mvLp);
        modeVideoTab.setOnClickListener(v -> {
            hapticClick(v);
            if (!isVideoMode()) {
                if (videoUri == null) promptPickVideo();
                else {
                    sourceMode = SourceMode.VIDEO;
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
                isVideoMode(),
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
        controlsBuilder.applyPillStyle(modePhotosTab, !isVideoMode());
        controlsBuilder.applyPillStyle(modeVideoTab, isVideoMode());
        if (controlsBuilder.photoControlsGroup != null) {
            controlsBuilder.photoControlsGroup.setVisibility(isVideoMode() ? View.GONE : View.VISIBLE);
        }
        if (controlsBuilder.videoControlsGroup != null) {
            controlsBuilder.videoControlsGroup.setVisibility(isVideoMode() ? View.VISIBLE : View.GONE);
        }
    }


    private void togglePreviewPlayPause() {
        hapticClick(previewImageView);
        playback = playback == Playback.PLAYING ? Playback.PAUSED : Playback.PLAYING;
        if (playback == Playback.PLAYING) startPreviewPlayback();
        else stopPreviewPlayback();
    }

    private void startPreviewPlayback() {
        if (screenState != ScreenState.ACTIVE || playback != Playback.PLAYING) return;
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
        if (screenState == ScreenState.CLOSED) return;
        final long ticket = previewJob.begin(JobState.Phase.LOADING);
        final boolean video = isVideoMode();
        final Uri sourceVideo = videoUri;
        final List<Uri> photos = new ArrayList<>(photoUris);
        final int fps = selectedFps, start = videoStartSec, duration = videoDurationSec;
        final String lut = selectedLutProfile;
        final String baseKey = buildBaseKey(video, sourceVideo, photos, fps, start, duration, lut);
        stopPreviewPlayback();

        if (baseKey.equals(cachedBaseKey) && !baseFrames.isEmpty()) {
            rebuildPreviewSequence(bounceMode);
            currentPreviewIndex = 0;
            previewJob.move(ticket, JobState.Phase.READY);
            updatePreviewBitmap();
            startPreviewPlayback();
            return;
        }

        if (previewTimecode != null) previewTimecode.setText("PREPARING FRAMES…");
        previewWorker.execute(() -> {
            if (!previewJob.accepts(ticket)) return;
            List<Bitmap> loaded;
            try {
                loaded = GifMediaHelper.loadPreviewFrames(getApplicationContext(), video, sourceVideo, photos,
                        fps, start, duration, lut, false, null, () -> previewJob.accepts(ticket));
            } catch (Exception error) {
                mainHandler.post(() -> {
                    if (previewJob.move(ticket, JobState.Phase.FAILED) && screenState != ScreenState.CLOSED && previewTimecode != null) {
                        previewTimecode.setText("COULD NOT LOAD PREVIEW");
                    }
                });
                return;
            }
            mainHandler.post(() -> {
                if (!previewJob.accepts(ticket) || screenState == ScreenState.CLOSED) {
                    for (Bitmap b : loaded) if (b != null && !b.isRecycled()) b.recycle();
                    return;
                }
                recycleBaseFrames();
                baseFrames.addAll(loaded);
                cachedBaseKey = baseKey;
                rebuildPreviewSequence(bounceMode);
                currentPreviewIndex = 0;
                previewJob.move(ticket, JobState.Phase.READY);
                if (!previewFrames.isEmpty()) {
                    updatePreviewBitmap();
                    startPreviewPlayback();
                } else if (previewTimecode != null) {
                    previewTimecode.setText(video ? "SELECT A VIDEO TO START" : "SELECT PHOTOS TO START");
                }
            });
        });
    }

    private static String buildBaseKey(boolean video, Uri sourceVideo, List<Uri> photos,
                                       int fps, int start, int duration, String lut) {
        StringBuilder sb = new StringBuilder(64);
        sb.append(video ? "V|" : "P|").append(fps).append('|').append(start)
                .append('|').append(duration).append('|').append(lut).append('|');
        if (video) {
            sb.append(sourceVideo);
        } else {
            for (int i = 0; i < photos.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(photos.get(i));
            }
        }
        return sb.toString();
    }

    private void rebuildPreviewSequence(boolean bounce) {
        if (previewImageView != null) previewImageView.setImageDrawable(null);
        previewFrames.clear();
        previewFrames.addAll(baseFrames);
        if (bounce && baseFrames.size() > 2) {
            for (int i = baseFrames.size() - 2; i > 0; i--) {
                previewFrames.add(baseFrames.get(i));
            }
        }
    }

    private void recycleBaseFrames() {
        if (previewImageView != null) previewImageView.setImageDrawable(null);
        previewFrames.clear();
        cachedBaseKey = "";
        for (Bitmap b : baseFrames) {
            if (b != null && !b.isRecycled()) b.recycle();
        }
        baseFrames.clear();
    }

    // =========================================================================
    // GIF Export Pipeline
    // =========================================================================
    private void startGifExport() {
        if (exportJob.isBusy() || exportJob.isClosed()) return;
        GifExporter.Params params = new GifExporter.Params();
        params.isVideoMode = isVideoMode();
        params.videoUri = videoUri;
        params.photoUris = new ArrayList<>(photoUris);
        params.targetRes = selectedResolution;
        params.targetFps = selectedFps;
        params.boomerang = bounceMode;
        params.lutProfile = selectedLutProfile;
        params.videoStartSec = videoStartSec;
        params.videoDurationSec = videoDurationSec;

        exportProgress = GifExporter.startExport(this, params, exportWorker, mainHandler, exportJob);
    }

    // =========================================================================
    // System Pickers
    // =========================================================================
    private void promptPickPhotos() {
        if (picker != Picker.NONE) return;
        picker = Picker.PHOTOS;
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            startActivityForResult(Intent.createChooser(intent, "Select Photos for GIF"), PICK_PHOTO_REQUEST);
        } catch (RuntimeException e) {
            picker = Picker.NONE;
            Toast.makeText(this, "No photo picker available.", Toast.LENGTH_SHORT).show();
        }
    }

    private void promptPickVideo() {
        if (picker != Picker.NONE) return;
        picker = Picker.VIDEO;
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("video/*");
        try {
            startActivityForResult(Intent.createChooser(intent, "Select Video for GIF"), PICK_VIDEO_REQUEST);
        } catch (RuntimeException e) {
            picker = Picker.NONE;
            Toast.makeText(this, "No video picker available.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        picker = Picker.NONE;
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
            sourceMode = SourceMode.PHOTOS;
            updateModeUi();
            refreshFilmstripUi();
            loadPreviewFramesAsync();
        } else if (requestCode == PICK_VIDEO_REQUEST) {
            if (data.getData() != null) {
                videoUri = data.getData();
                sourceMode = SourceMode.VIDEO;
                updateModeUi();
                loadPreviewFramesAsync();
            }
        }
    }

    // =========================================================================
    // UI Helpers
    // =========================================================================
    private TextView text(String s, int sp, int color, boolean bold) {
        TextView tv = UiKit.text(this, s, sp, color, bold);
        tv.setTypeface(android.graphics.Typeface.MONOSPACE, bold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        return tv;
    }

    private int dp(int v) {
        return UiKit.dp(this, v);
    }
}

