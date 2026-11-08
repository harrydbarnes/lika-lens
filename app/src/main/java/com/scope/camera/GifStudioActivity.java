package com.scope.camera;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.GradientDrawable;
import android.media.ExifInterface;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
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
public class GifStudioActivity extends Activity {

    public static final String EXTRA_PHOTO_URIS = "extra_photo_uris";
    public static final String EXTRA_VIDEO_URI = "extra_video_uri";

    private static final int BG = 0xff0b0d0e;
    private static final int ACCENT = 0xffd7b574;
    private static final int SURFACE = 0xff141819;
    private static final int BORDER = 0xff252c2d;
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
    private String selectedLutProfile = "NONE"; // NONE, PORTRA, FUJI, ANSEL_BW, TEAL_ORANGE, AMBER_70S

    // Video range options (in seconds)
    private int videoStartSec = 0;
    private int videoDurationSec = 3; // 2, 3, 5, or all

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
    private LinearLayout filmstripLayout;
    private TextView modePhotosTab;
    private TextView modeVideoTab;
    private LinearLayout videoControlsGroup;
    private LinearLayout photoControlsGroup;
    private TextView generateButton;

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

        modePhotosTab = pill("PHOTOS", !isVideoMode);
        modePhotosTab.setOnClickListener(v -> {
            hapticClick(v);
            if (isVideoMode) {
                isVideoMode = false;
                updateModeUi();
                loadPreviewFramesAsync();
            }
        });
        topBar.addView(modePhotosTab, new LinearLayout.LayoutParams(-2, dp(30)));

        modeVideoTab = pill("VIDEO", isVideoMode);
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
        LinearLayout pane = new LinearLayout(this);
        pane.setOrientation(LinearLayout.VERTICAL);
        pane.setBackgroundColor(BG);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(14), dp(16), dp(16));

        // Section A: Frame Sequence (Photos) or Range Trimmer (Video)
        photoControlsGroup = new LinearLayout(this);
        photoControlsGroup.setOrientation(LinearLayout.VERTICAL);
        photoControlsGroup.setVisibility(isVideoMode ? View.GONE : View.VISIBLE);
        buildPhotoFilmstripControls(photoControlsGroup);
        content.addView(photoControlsGroup);

        videoControlsGroup = new LinearLayout(this);
        videoControlsGroup.setOrientation(LinearLayout.VERTICAL);
        videoControlsGroup.setVisibility(isVideoMode ? View.VISIBLE : View.GONE);
        buildVideoRangeControls(videoControlsGroup);
        content.addView(videoControlsGroup);

        // Section B: Speed / Frame Rate
        content.addView(sectionHeader("FRAME RATE · SPEED"));
        LinearLayout fpsRow = new LinearLayout(this);
        fpsRow.setOrientation(LinearLayout.HORIZONTAL);
        fpsRow.addView(fpsPill("2 FPS · SLOW", 2));
        fpsRow.addView(fpsPill("5 FPS · RETRO", 5));
        fpsRow.addView(fpsPill("10 FPS · SMOOTH", 10));
        fpsRow.addView(fpsPill("15 FPS · FAST", 15));
        content.addView(fpsRow);

        // Section C: Output Resolution
        content.addView(sectionHeader("RESOLUTION · QUALITY"));
        LinearLayout resRow = new LinearLayout(this);
        resRow.setOrientation(LinearLayout.HORIZONTAL);
        resRow.addView(resPill("320PX · COMPACT", 320));
        resRow.addView(resPill("480PX · STANDARD", 480));
        resRow.addView(resPill("640PX · PRO HD", 640));
        content.addView(resRow);

        // Section D: Loop Style (Forever vs Boomerang)
        content.addView(sectionHeader("LOOP BEHAVIOUR"));
        LinearLayout loopRow = new LinearLayout(this);
        loopRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView standardLoop = pill("FORWARD LOOP", !bounceMode);
        loopRow.addView(standardLoop, new LinearLayout.LayoutParams(0, dp(34), 1f));
        standardLoop.setOnClickListener(v -> {
            hapticClick(v);
            bounceMode = false;
            updateLoopPills(standardLoop, (TextView) ((LinearLayout) v.getParent()).getChildAt(1));
            loadPreviewFramesAsync();
        });

        TextView boomerangLoop = pill("BOOMERANG ⮀", bounceMode);
        LinearLayout.LayoutParams bmPillLp = new LinearLayout.LayoutParams(0, dp(34), 1f);
        bmPillLp.leftMargin = dp(8);
        loopRow.addView(boomerangLoop, bmPillLp);
        boomerangLoop.setOnClickListener(v -> {
            hapticClick(v);
            bounceMode = true;
            updateLoopPills(standardLoop, boomerangLoop);
            loadPreviewFramesAsync();
        });
        content.addView(loopRow);

        // Section E: Leica Film Look / 3D LUT preset
        content.addView(sectionHeader("FILM COLOR SIMULATION"));
        HorizontalScrollView lutScroll = new HorizontalScrollView(this);
        lutScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout lutRow = new LinearLayout(this);
        lutRow.setOrientation(LinearLayout.HORIZONTAL);
        lutRow.addView(lutPill("NATURAL", "NONE"));
        lutRow.addView(lutPill("PORTRA 400", LutManager.LUT_PORTRA));
        lutRow.addView(lutPill("FUJI PRO", LutManager.LUT_FUJI));
        lutRow.addView(lutPill("TRI-X B&W", LutManager.LUT_ANSEL_BW));
        lutRow.addView(lutPill("TEAL & ORANGE", LutManager.LUT_TEAL_ORANGE));
        lutRow.addView(lutPill("1970s AMBER", LutManager.LUT_AMBER_70S));
        lutRow.addView(lutPill("BLEACH", LutManager.LUT_BLEACH));
        lutScroll.addView(lutRow);
        content.addView(lutScroll);

        scroll.addView(content);
        pane.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        // Bottom Fixed Bar: Export GIF Button
        LinearLayout footer = new LinearLayout(this);
        footer.setPadding(dp(16), dp(10), dp(16), dp(12));
        footer.setBackgroundColor(SURFACE);

        generateButton = new TextView(this);
        generateButton.setText("EXPORT ANIMATED GIF");
        generateButton.setTextSize(11);
        generateButton.setTextColor(0xff0b0d0e);
        generateButton.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
        generateButton.setLetterSpacing(0.14f);
        generateButton.setGravity(Gravity.CENTER);
        generateButton.setBackground(round(ACCENT, 20, ACCENT, 0));
        generateButton.setOnClickListener(v -> {
            hapticClick(v);
            startGifExport();
        });
        footer.addView(generateButton, new LinearLayout.LayoutParams(-1, dp(44)));

        pane.addView(footer);
        return pane;
    }

    private void buildPhotoFilmstripControls(LinearLayout parent) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("FRAME SEQUENCE", 9, 0xffd8ded6, true);
        title.setLetterSpacing(0.1f);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView addPhotos = pill("+ ADD FRAMES", false);
        header.addView(addPhotos, new LinearLayout.LayoutParams(-2, dp(28)));
        addPhotos.setOnClickListener(v -> { hapticClick(v); promptPickPhotos(); });
        parent.addView(header);

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setPadding(0, dp(8), 0, dp(12));

        filmstripLayout = new LinearLayout(this);
        filmstripLayout.setOrientation(LinearLayout.HORIZONTAL);
        scroll.addView(filmstripLayout);
        parent.addView(scroll);
        refreshFilmstripUi();
    }

    private void refreshFilmstripUi() {
        if (filmstripLayout == null) return;
        filmstripLayout.removeAllViews();

        if (photoUris.isEmpty()) {
            TextView empty = text("No photos selected. Tap '+ ADD FRAMES' to choose.", 8, MUTED, false);
            empty.setPadding(dp(8), dp(16), dp(8), dp(16));
            filmstripLayout.addView(empty);
            return;
        }

        for (int i = 0; i < photoUris.size(); i++) {
            final int index = i;
            Uri uri = photoUris.get(i);

            FrameLayout tile = new FrameLayout(this);
            tile.setBackground(round(SURFACE, 8, BORDER, dp(1)));
            tile.setClipToOutline(true);

            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Bitmap thumb = decodeSampledBitmap(uri, dp(64), dp(64));
            if (thumb != null) iv.setImageBitmap(thumb);
            tile.addView(iv, new FrameLayout.LayoutParams(dp(64), dp(64)));

            // Index tag
            TextView tag = text(String.valueOf(index + 1), 7, ACCENT, true);
            tag.setBackground(round(0xdd0b0d0e, 6, ACCENT, dp(1)));
            tag.setPadding(dp(5), dp(2), dp(5), dp(2));
            tile.addView(tag, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.LEFT));

            // Delete badge
            TextView del = text("✕", 9, 0xfff0443c, true);
            del.setGravity(Gravity.CENTER);
            del.setBackground(round(0xdd0b0d0e, 10, 0xfff0443c, dp(1)));
            FrameLayout.LayoutParams delLp = new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.TOP | Gravity.RIGHT);
            delLp.setMargins(0, dp(2), dp(2), 0);
            tile.addView(del, delLp);
            del.setOnClickListener(v -> {
                hapticClick(v);
                if (photoUris.size() > index) {
                    photoUris.remove(index);
                    refreshFilmstripUi();
                    loadPreviewFramesAsync();
                }
            });

            LinearLayout.LayoutParams tileLp = new LinearLayout.LayoutParams(dp(64), dp(64));
            tileLp.rightMargin = dp(8);
            filmstripLayout.addView(tile, tileLp);
        }
    }

    private void buildVideoRangeControls(LinearLayout parent) {
        TextView title = text("VIDEO TRIMMER · DURATION", 9, 0xffd8ded6, true);
        title.setLetterSpacing(0.1f);
        parent.addView(title);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(8), 0, dp(12));

        TextView sec2 = durationPill("FIRST 2 SEC", 2);
        TextView sec3 = durationPill("FIRST 3 SEC", 3);
        TextView sec5 = durationPill("FIRST 5 SEC", 5);
        TextView secFull = durationPill("ENTIRE CLIP", -1);

        row.addView(sec2);
        row.addView(sec3);
        row.addView(sec5);
        row.addView(secFull);
        parent.addView(row);

        TextView pickAnotherVideo = pill("CHOOSE DIFFERENT VIDEO", false);
        pickAnotherVideo.setOnClickListener(v -> { hapticClick(v); promptPickVideo(); });
        parent.addView(pickAnotherVideo, new LinearLayout.LayoutParams(-2, dp(32)));
    }

    private TextView durationPill(String label, int seconds) {
        boolean selected = (videoDurationSec == seconds);
        TextView tv = pill(label, selected);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(34), 1f);
        lp.rightMargin = dp(4);
        tv.setLayoutParams(lp);
        tv.setOnClickListener(v -> {
            hapticClick(v);
            videoDurationSec = seconds;
            ViewGroup p = (ViewGroup) v.getParent();
            for (int i = 0; i < p.getChildCount(); i++) {
                View child = p.getChildAt(i);
                if (child instanceof TextView) applyPillStyle((TextView) child, child == v);
            }
            loadPreviewFramesAsync();
        });
        return tv;
    }

    private TextView fpsPill(String label, int fps) {
        boolean selected = (selectedFps == fps);
        TextView tv = pill(label, selected);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(34), 1f);
        lp.rightMargin = dp(4);
        tv.setLayoutParams(lp);
        tv.setOnClickListener(v -> {
            hapticClick(v);
            selectedFps = fps;
            ViewGroup p = (ViewGroup) v.getParent();
            for (int i = 0; i < p.getChildCount(); i++) {
                View child = p.getChildAt(i);
                if (child instanceof TextView) applyPillStyle((TextView) child, child == v);
            }
            loadPreviewFramesAsync();
        });
        return tv;
    }

    private TextView resPill(String label, int res) {
        boolean selected = (selectedResolution == res);
        TextView tv = pill(label, selected);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(34), 1f);
        lp.rightMargin = dp(4);
        tv.setLayoutParams(lp);
        tv.setOnClickListener(v -> {
            hapticClick(v);
            selectedResolution = res;
            ViewGroup p = (ViewGroup) v.getParent();
            for (int i = 0; i < p.getChildCount(); i++) {
                View child = p.getChildAt(i);
                if (child instanceof TextView) applyPillStyle((TextView) child, child == v);
            }
        });
        return tv;
    }

    private TextView lutPill(String label, String lutId) {
        boolean selected = selectedLutProfile.equals(lutId);
        TextView tv = pill(label, selected);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(34));
        lp.rightMargin = dp(6);
        tv.setLayoutParams(lp);
        tv.setOnClickListener(v -> {
            hapticClick(v);
            selectedLutProfile = lutId;
            ViewGroup p = (ViewGroup) v.getParent();
            for (int i = 0; i < p.getChildCount(); i++) {
                View child = p.getChildAt(i);
                if (child instanceof TextView) applyPillStyle((TextView) child, child == v);
            }
            loadPreviewFramesAsync();
        });
        return tv;
    }

    private void updateModeUi() {
        applyPillStyle(modePhotosTab, !isVideoMode);
        applyPillStyle(modeVideoTab, isVideoMode);
        if (photoControlsGroup != null) photoControlsGroup.setVisibility(isVideoMode ? View.GONE : View.VISIBLE);
        if (videoControlsGroup != null) videoControlsGroup.setVisibility(isVideoMode ? View.VISIBLE : View.GONE);
    }

    private void updateLoopPills(TextView standard, TextView boomerang) {
        applyPillStyle(standard, !bounceMode);
        applyPillStyle(boomerang, bounceMode);
    }

    private TextView sectionHeader(String text) {
        TextView tv = text(text, 8, MUTED, true);
        tv.setLetterSpacing(0.12f);
        tv.setPadding(0, dp(12), 0, dp(6));
        return tv;
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
            List<Bitmap> loaded = new ArrayList<>();
            try {
                if (isVideoMode) {
                    if (videoUri != null) {
                        loaded.addAll(extractVideoFrames(videoUri, 320, selectedFps, videoStartSec, videoDurationSec));
                    }
                } else {
                    for (Uri uri : photoUris) {
                        Bitmap bmp = decodeAndScaleBitmap(uri, 320);
                        if (bmp != null) loaded.add(bmp);
                    }
                }

                // Apply selected Film LUT if not NONE
                if (!"NONE".equals(selectedLutProfile)) {
                    LutManager.Lut3D lut = LutManager.generateBuiltIn(selectedLutProfile, 17);
                    for (int i = 0; i < loaded.size(); i++) {
                        Bitmap orig = loaded.get(i);
                        Bitmap graded = LutManager.applyToBitmap(orig, lut, 1.0f);
                        if (graded != null && graded != orig) {
                            orig.recycle();
                            loaded.set(i, graded);
                        }
                    }
                }

                // If boomerang mode, append reverse sequence
                if (bounceMode && loaded.size() > 2) {
                    List<Bitmap> bounced = new ArrayList<>(loaded);
                    for (int i = loaded.size() - 2; i > 0; i--) {
                        bounced.add(loaded.get(i)); // References same bitmap for preview
                    }
                    loaded = bounced;
                }
            } catch (Throwable t) {
                // Ignore decoding errors
            }

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

    private List<Bitmap> extractVideoFrames(Uri uri, int maxDimension, int fps, int startSec, int durationSec) {
        List<Bitmap> frames = new ArrayList<>();
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(this, uri);
            String durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            long totalMs = durStr != null ? Long.parseLong(durStr) : 0;
            videoDurationMs = totalMs;

            String rotStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
            videoRotation = rotStr != null ? Integer.parseInt(rotStr) : 0;

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
                        frame = retriever.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST,
                                maxDimension, maxDimension);
                    } catch (Throwable ignored) {}
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
            // Handled gracefully
        } finally {
            try { retriever.release(); } catch (Throwable ignored) {}
        }
        return frames;
    }

    private Bitmap decodeAndScaleBitmap(Uri uri, int maxDimension) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
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
            try (InputStream is2 = getContentResolver().openInputStream(uri)) {
                Bitmap bmp = BitmapFactory.decodeStream(is2, null, opts);
                if (bmp == null) return null;

                // Check EXIF rotation
                int orientation = getExifOrientation(uri);
                if (orientation != 0) {
                    bmp = rotateBitmap(bmp, orientation);
                }
                return scaleDown(bmp, maxDimension);
            }
        } catch (Throwable t) {
            return null;
        }
    }

    private Bitmap scaleDown(Bitmap src, int maxDimension) {
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

    private int getExifOrientation(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) return 0;
            ExifInterface exif = new ExifInterface(is);
            int tag = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (tag) {
                case ExifInterface.ORIENTATION_ROTATE_90: return 90;
                case ExifInterface.ORIENTATION_ROTATE_180: return 180;
                case ExifInterface.ORIENTATION_ROTATE_270: return 270;
                default: return 0;
            }
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private Bitmap rotateBitmap(Bitmap src, int degrees) {
        if (degrees == 0 || src == null) return src;
        Matrix m = new Matrix();
        m.postRotate(degrees);
        Bitmap rotated = Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
        if (rotated != src) src.recycle();
        return rotated;
    }

    private Bitmap decodeSampledBitmap(Uri uri, int reqW, int reqH) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) return null;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = 4;
            return BitmapFactory.decodeStream(is, null, opts);
        } catch (Throwable t) {
            return null;
        }
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
        if (isVideoMode && videoUri == null) {
            Toast.makeText(this, "Please choose a video first", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isVideoMode && photoUris.size() < 2) {
            Toast.makeText(this, "Please select at least 2 photos for an animated GIF", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show export modal dialog
        Dialog progressDialog = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        progressDialog.setCancelable(false);
        FrameLayout scrim = new FrameLayout(this);
        scrim.setBackgroundColor(0x99000000);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(round(SURFACE, 18, BORDER, dp(1)));
        card.setPadding(dp(24), dp(22), dp(24), dp(22));

        TextView title = text("CREATING ANIMATED GIF", 12, ACCENT, true);
        title.setLetterSpacing(0.12f);
        card.addView(title);

        TextView status = text("Extracting frames…", 9, 0xffd8ded6, false);
        status.setPadding(0, dp(8), 0, dp(14));
        card.addView(status);

        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(false);
        progress.setMax(100);
        progress.setProgress(5);
        card.addView(progress, new LinearLayout.LayoutParams(-1, dp(8)));

        int cardW = Math.min(dp(360), getResources().getDisplayMetrics().widthPixels - dp(48));
        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(cardW, -2, Gravity.CENTER);
        scrim.addView(card, cardLp);
        progressDialog.setContentView(scrim);
        progressDialog.show();

        final int targetRes = selectedResolution;
        final int targetFps = selectedFps;
        final boolean boomerang = bounceMode;
        final String lutProfile = selectedLutProfile;

        worker.execute(() -> {
            try {
                List<Bitmap> exportFrames = new ArrayList<>();
                mainHandler.post(() -> status.setText("Extracting and scaling frames…"));

                if (isVideoMode) {
                    exportFrames.addAll(extractVideoFrames(videoUri, targetRes, targetFps, videoStartSec, videoDurationSec));
                } else {
                    for (Uri uri : photoUris) {
                        Bitmap bmp = decodeAndScaleBitmap(uri, targetRes);
                        if (bmp != null) exportFrames.add(bmp);
                    }
                }

                if (exportFrames.isEmpty()) {
                    throw new IllegalStateException("No frames extracted for GIF");
                }

                // Apply selected Film LUT
                if (!"NONE".equals(lutProfile)) {
                    mainHandler.post(() -> status.setText("Applying " + lutProfile + " film color…"));
                    LutManager.Lut3D lut = LutManager.generateBuiltIn(lutProfile, 17);
                    for (int i = 0; i < exportFrames.size(); i++) {
                        Bitmap orig = exportFrames.get(i);
                        Bitmap graded = LutManager.applyToBitmap(orig, lut, 1.0f);
                        if (graded != null && graded != orig) {
                            orig.recycle();
                            exportFrames.set(i, graded);
                        }
                    }
                }

                // If boomerang mode, append reverse sequence
                if (boomerang && exportFrames.size() > 2) {
                    int originalCount = exportFrames.size();
                    for (int i = originalCount - 2; i > 0; i--) {
                        // Create a copy for reverse so recycling works safely
                        Bitmap copy = exportFrames.get(i).copy(Bitmap.Config.ARGB_8888, true);
                        exportFrames.add(copy);
                    }
                }

                int totalFrames = exportFrames.size();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                GifEncoder encoder = new GifEncoder();
                encoder.setDelay(1000 / targetFps);
                encoder.setRepeat(0); // Infinite loop
                encoder.setQuality(10); // High quality photographic sampling
                encoder.setSize(exportFrames.get(0).getWidth(), exportFrames.get(0).getHeight());

                encoder.start(baos);
                for (int i = 0; i < exportFrames.size(); i++) {
                    final int frameIdx = i + 1;
                    mainHandler.post(() -> {
                        status.setText(String.format(Locale.US, "Encoding frame %d of %d…", frameIdx, totalFrames));
                        progress.setProgress((frameIdx * 90) / totalFrames);
                    });
                    encoder.addFrame(exportFrames.get(i));
                    exportFrames.get(i).recycle(); // Free memory immediately
                }
                encoder.finish();

                byte[] gifBytes = baos.toByteArray();
                String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                String gifName = "LIKA_GIF_" + stamp + ".gif";

                mainHandler.post(() -> status.setText("Saving GIF to library…"));
                Uri savedUri = PhotoRepository.saveGif(GifStudioActivity.this, gifBytes, gifName);

                mainHandler.post(() -> {
                    progressDialog.dismiss();
                    if (savedUri != null) {
                        showExportSuccessDialog(savedUri, gifName, gifBytes.length, totalFrames, targetFps);
                    } else {
                        Toast.makeText(GifStudioActivity.this, "Failed to save GIF", Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Throwable t) {
                mainHandler.post(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(GifStudioActivity.this, "Export failed: " + t.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showExportSuccessDialog(Uri uri, String name, int byteSize, int frames, int fps) {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        FrameLayout scrim = new FrameLayout(this);
        scrim.setBackgroundColor(0xaa000000);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(round(SURFACE, 20, BORDER, dp(1)));
        card.setPadding(dp(22), dp(20), dp(22), dp(20));

        TextView title = text("GIF EXPORTED", 12, ACCENT, true);
        title.setLetterSpacing(0.12f);
        card.addView(title);

        String sizeStr = String.format(Locale.US, "%.1f MB", byteSize / (1024.0 * 1024.0));
        TextView info = text(name + "\n" + sizeStr + " · " + frames + " frames · " + fps + " fps", 9, 0xffd8ded6, false);
        info.setPadding(0, dp(6), 0, dp(18));
        card.addView(info);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        TextView shareBtn = pill("SHARE", false);
        actions.addView(shareBtn, new LinearLayout.LayoutParams(0, dp(38), 1f));
        shareBtn.setOnClickListener(v -> {
            hapticClick(v);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("image/gif");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "Share Lika Lens GIF"));
        });

        TextView viewBtn = pill("VIEW", true);
        LinearLayout.LayoutParams viewLp = new LinearLayout.LayoutParams(0, dp(38), 1f);
        viewLp.leftMargin = dp(8);
        actions.addView(viewBtn, viewLp);
        viewBtn.setOnClickListener(v -> {
            hapticClick(v);
            dialog.dismiss();
            Intent intent = new Intent(GifStudioActivity.this, PhotoViewerActivity.class);
            intent.putExtra(PhotoViewerActivity.EXTRA_URI, uri.toString());
            intent.putExtra(PhotoViewerActivity.EXTRA_NAME, name);
            intent.putExtra(PhotoViewerActivity.EXTRA_FROM_LIBRARY, true);
            startActivity(intent);
            finish();
        });

        card.addView(actions);

        TextView done = text("CLOSE", 9, MUTED, true);
        done.setGravity(Gravity.CENTER);
        done.setPadding(0, dp(14), 0, dp(4));
        done.setOnClickListener(v -> { dialog.dismiss(); finish(); });
        card.addView(done);

        int cardW = Math.min(dp(380), getResources().getDisplayMetrics().widthPixels - dp(40));
        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(cardW, -2, Gravity.CENTER);
        scrim.addView(card, cardLp);
        dialog.setContentView(scrim);
        dialog.show();
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
    private TextView pill(String label, boolean active) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(8);
        tv.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
        tv.setLetterSpacing(0.08f);
        tv.setGravity(Gravity.CENTER);
        applyPillStyle(tv, active);
        return tv;
    }

    private void applyPillStyle(TextView tv, boolean active) {
        if (active) {
            tv.setTextColor(0xff0b0d0e);
            tv.setBackground(round(ACCENT, 16, ACCENT, 0));
        } else {
            tv.setTextColor(0xffd8ded6);
            tv.setBackground(round(0x22ffffff, 16, BORDER, dp(1)));
        }
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(s);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        tv.setTypeface(android.graphics.Typeface.MONOSPACE, bold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        return tv;
    }

    private GradientDrawable round(int fill, int radiusDp, int strokeColor, int strokeWidthPx) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(fill);
        gd.setCornerRadius(dp(radiusDp));
        if (strokeWidthPx > 0) gd.setStroke(strokeWidthPx, strokeColor);
        return gd;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
