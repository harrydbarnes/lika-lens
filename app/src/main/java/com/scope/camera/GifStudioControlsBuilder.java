package com.scope.camera;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Builds the controls, filmstrip selector, speed/resolution/loop pills,
 * and film LUT picker pane for GifStudioActivity.
 */
public final class GifStudioControlsBuilder {
    private static final int ACCENT = 0xffd7b574;
    private static final int BG = 0xff0b0d0e;
    private static final int SURFACE = 0xff141819;
    private static final int BORDER = 0xff2a3233;
    private static final int MUTED = 0xff89948e;

    public interface Listener {
        void onFpsSelected(int fps);
        void onResolutionSelected(int res);
        void onBounceModeChanged(boolean bounce);
        void onLutProfileSelected(String profile);
        void onVideoDurationSelected(int seconds);
        void onAddPhotosClicked();
        void onChooseDifferentVideoClicked();
        void onExportClicked();
        void onPhotoRemoved(int index);
    }

    private final Activity activity;
    private final Listener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService thumbWorker = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "GifFilmstripThumbs");
        t.setDaemon(true);
        return t;
    });
    private final AtomicInteger filmstripGeneration = new AtomicInteger();
    private final LruCache<Uri, Bitmap> thumbCache = new LruCache<>(48);
    private volatile boolean destroyed;

    public LinearLayout photoControlsGroup;
    public LinearLayout videoControlsGroup;
    public LinearLayout filmstripLayout;
    public TextView generateButton;

    public GifStudioControlsBuilder(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
    }

    public View buildControlsPane(
            boolean isVideoMode,
            int selectedFps,
            int selectedResolution,
            boolean bounceMode,
            String selectedLutProfile,
            int videoDurationSec,
            List<Uri> photoUris
    ) {
        LinearLayout pane = new LinearLayout(activity);
        pane.setOrientation(LinearLayout.VERTICAL);
        pane.setBackgroundColor(BG);

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(14), dp(16), dp(16));

        // Section A: Frame Sequence (Photos) or Range Trimmer (Video)
        photoControlsGroup = new LinearLayout(activity);
        photoControlsGroup.setOrientation(LinearLayout.VERTICAL);
        photoControlsGroup.setVisibility(isVideoMode ? View.GONE : View.VISIBLE);
        buildPhotoFilmstripControls(photoControlsGroup, photoUris);
        content.addView(photoControlsGroup);

        videoControlsGroup = new LinearLayout(activity);
        videoControlsGroup.setOrientation(LinearLayout.VERTICAL);
        videoControlsGroup.setVisibility(isVideoMode ? View.VISIBLE : View.GONE);
        buildVideoRangeControls(videoControlsGroup, videoDurationSec);
        content.addView(videoControlsGroup);

        // Section B: Speed / Frame Rate
        content.addView(sectionHeader("FRAME RATE · SPEED"));
        LinearLayout fpsRow = new LinearLayout(activity);
        fpsRow.setOrientation(LinearLayout.HORIZONTAL);
        fpsRow.addView(fpsPill("2 FPS · SLOW", 2, selectedFps));
        fpsRow.addView(fpsPill("5 FPS · RETRO", 5, selectedFps));
        fpsRow.addView(fpsPill("10 FPS · SMOOTH", 10, selectedFps));
        fpsRow.addView(fpsPill("15 FPS · FAST", 15, selectedFps));
        content.addView(fpsRow);

        // Section C: Output Resolution
        content.addView(sectionHeader("RESOLUTION · QUALITY"));
        LinearLayout resRow = new LinearLayout(activity);
        resRow.setOrientation(LinearLayout.HORIZONTAL);
        resRow.addView(resPill("320PX · COMPACT", 320, selectedResolution));
        resRow.addView(resPill("480PX · STANDARD", 480, selectedResolution));
        resRow.addView(resPill("640PX · PRO HD", 640, selectedResolution));
        content.addView(resRow);

        // Section D: Loop Style (Forever vs Boomerang)
        content.addView(sectionHeader("LOOP BEHAVIOUR"));
        LinearLayout loopRow = new LinearLayout(activity);
        loopRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView standardLoop = pill("FORWARD LOOP", !bounceMode);
        loopRow.addView(standardLoop, new LinearLayout.LayoutParams(0, dp(34), 1f));

        TextView boomerangLoop = pill("BOOMERANG ⮀", bounceMode);
        LinearLayout.LayoutParams bmPillLp = new LinearLayout.LayoutParams(0, dp(34), 1f);
        bmPillLp.leftMargin = dp(8);
        loopRow.addView(boomerangLoop, bmPillLp);

        standardLoop.setOnClickListener(v -> {
            hapticClick(v);
            updateLoopPills(standardLoop, boomerangLoop, false);
            listener.onBounceModeChanged(false);
        });

        boomerangLoop.setOnClickListener(v -> {
            hapticClick(v);
            updateLoopPills(standardLoop, boomerangLoop, true);
            listener.onBounceModeChanged(true);
        });
        content.addView(loopRow);

        // Section E: Leica Film Look / 3D LUT preset
        content.addView(sectionHeader("FILM COLOR SIMULATION"));
        HorizontalScrollView lutScroll = new HorizontalScrollView(activity);
        lutScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout lutRow = new LinearLayout(activity);
        lutRow.setOrientation(LinearLayout.HORIZONTAL);
        lutRow.addView(lutPill("NATURAL", "NONE", selectedLutProfile));
        lutRow.addView(lutPill("PORTRA 400", LutManager.LUT_PORTRA, selectedLutProfile));
        lutRow.addView(lutPill("FUJI PRO", LutManager.LUT_FUJI, selectedLutProfile));
        lutRow.addView(lutPill("TRI-X B&W", LutManager.LUT_ANSEL_BW, selectedLutProfile));
        lutRow.addView(lutPill("TEAL & ORANGE", LutManager.LUT_TEAL_ORANGE, selectedLutProfile));
        lutRow.addView(lutPill("1970s AMBER", LutManager.LUT_AMBER_70S, selectedLutProfile));
        lutRow.addView(lutPill("BLEACH", LutManager.LUT_BLEACH, selectedLutProfile));
        lutScroll.addView(lutRow);
        content.addView(lutScroll);

        scroll.addView(content);
        pane.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        // Bottom Fixed Bar: Export GIF Button
        LinearLayout footer = new LinearLayout(activity);
        footer.setPadding(dp(16), dp(10), dp(16), dp(12));
        footer.setBackgroundColor(SURFACE);

        generateButton = new TextView(activity);
        generateButton.setText("EXPORT ANIMATED GIF");
        generateButton.setTextSize(11);
        generateButton.setTextColor(0xff0b0d0e);
        generateButton.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
        generateButton.setLetterSpacing(0.14f);
        generateButton.setGravity(Gravity.CENTER);
        generateButton.setBackground(round(ACCENT, 20, ACCENT, 0));
        generateButton.setOnClickListener(v -> {
            hapticClick(v);
            listener.onExportClicked();
        });
        footer.addView(generateButton, new LinearLayout.LayoutParams(-1, dp(44)));

        pane.addView(footer);
        return pane;
    }

    private void buildPhotoFilmstripControls(LinearLayout parent, List<Uri> photoUris) {
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("FRAME SEQUENCE", 9, 0xffd8ded6, true);
        title.setLetterSpacing(0.1f);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView addPhotos = pill("+ ADD FRAMES", false);
        header.addView(addPhotos, new LinearLayout.LayoutParams(-2, dp(28)));
        addPhotos.setOnClickListener(v -> {
            hapticClick(v);
            listener.onAddPhotosClicked();
        });
        parent.addView(header);

        HorizontalScrollView scroll = new HorizontalScrollView(activity);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setPadding(0, dp(8), 0, dp(12));

        filmstripLayout = new LinearLayout(activity);
        filmstripLayout.setOrientation(LinearLayout.HORIZONTAL);
        scroll.addView(filmstripLayout);
        parent.addView(scroll);
        refreshFilmstripUi(photoUris);
    }

    public void refreshFilmstripUi(List<Uri> photoUris) {
        if (filmstripLayout == null) return;
        final int gen = filmstripGeneration.incrementAndGet();
        clearFilmstripDrawables();
        filmstripLayout.removeAllViews();

        if (photoUris.isEmpty()) {
            TextView empty = text("No photos selected. Tap '+ ADD FRAMES' to choose.", 8, MUTED, false);
            empty.setPadding(dp(8), dp(16), dp(8), dp(16));
            filmstripLayout.addView(empty);
            return;
        }

        final int thumbPx = dp(64);
        for (int i = 0; i < photoUris.size(); i++) {
            final int index = i;
            final Uri uri = photoUris.get(i);

            FrameLayout tile = new FrameLayout(activity);
            tile.setBackground(round(SURFACE, 8, BORDER, dp(1)));
            tile.setClipToOutline(true);

            ImageView iv = new ImageView(activity);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setTag(uri);
            Bitmap cached = thumbCache.get(uri);
            if (cached != null && !cached.isRecycled()) {
                iv.setImageBitmap(cached);
            } else {
                thumbWorker.execute(() -> {
                    if (destroyed || filmstripGeneration.get() != gen) return;
                    Bitmap decoded = GifMediaHelper.decodeSampledBitmap(activity.getApplicationContext(), uri, thumbPx, thumbPx);
                    if (decoded == null) return;
                    thumbCache.put(uri, decoded);
                    mainHandler.post(() -> {
                        if (!destroyed && filmstripGeneration.get() == gen && uri.equals(iv.getTag())) {
                            iv.setImageBitmap(decoded);
                        }
                    });
                });
            }
            tile.addView(iv, new FrameLayout.LayoutParams(thumbPx, thumbPx));

            // Index tag
            TextView tag = text(String.valueOf(index + 1), 7, ACCENT, true);
            tag.setBackground(round(0xdd0b0d0e, 6, ACCENT, dp(1)));
            tag.setPadding(dp(5), dp(2), dp(5), dp(2));
            tile.addView(tag, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.LEFT));

            // Delete badge
            TextView del = text("✕", 8, 0xffffffff, true);
            del.setGravity(Gravity.CENTER);
            del.setBackground(round(0xaa000000, 8, 0, 0));
            del.setOnClickListener(v -> {
                hapticClick(v);
                listener.onPhotoRemoved(index);
            });
            tile.addView(del, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.TOP | Gravity.RIGHT));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(thumbPx, thumbPx);
            lp.rightMargin = dp(8);
            filmstripLayout.addView(tile, lp);
        }
    }

    private void clearFilmstripDrawables() {
        if (filmstripLayout == null) return;
        for (int i = 0; i < filmstripLayout.getChildCount(); i++) {
            View child = filmstripLayout.getChildAt(i);
            if (child instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) child;
                for (int j = 0; j < group.getChildCount(); j++) {
                    View inner = group.getChildAt(j);
                    if (inner instanceof ImageView) {
                        ((ImageView) inner).setImageDrawable(null);
                    }
                }
            }
        }
    }

    public void destroy() {
        destroyed = true;
        filmstripGeneration.incrementAndGet();
        mainHandler.removeCallbacksAndMessages(null);
        clearFilmstripDrawables();
        thumbCache.evictAll();
        thumbWorker.shutdownNow();
    }

    private void buildVideoRangeControls(LinearLayout parent, int videoDurationSec) {
        TextView header = text("TRIM DURATION", 9, 0xffd8ded6, true);
        header.setLetterSpacing(0.1f);
        parent.addView(header);

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(8), 0, dp(12));

        row.addView(durationPill("FIRST 2s", 2, videoDurationSec));
        row.addView(durationPill("FIRST 3s", 3, videoDurationSec));
        row.addView(durationPill("FIRST 5s", 5, videoDurationSec));
        row.addView(durationPill("ENTIRE CLIP", -1, videoDurationSec));
        parent.addView(row);

        TextView pickAnotherVideo = pill("CHOOSE DIFFERENT VIDEO", false);
        pickAnotherVideo.setOnClickListener(v -> {
            hapticClick(v);
            listener.onChooseDifferentVideoClicked();
        });
        parent.addView(pickAnotherVideo, new LinearLayout.LayoutParams(-2, dp(32)));
    }

    private TextView durationPill(String label, int seconds, int selectedDurationSec) {
        boolean selected = (selectedDurationSec == seconds);
        TextView tv = pill(label, selected);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(34), 1f);
        lp.rightMargin = dp(4);
        tv.setLayoutParams(lp);
        tv.setOnClickListener(v -> {
            hapticClick(v);
            selectExclusive(v);
            listener.onVideoDurationSelected(seconds);
        });
        return tv;
    }

    private TextView fpsPill(String label, int fps, int selectedFps) {
        boolean selected = (selectedFps == fps);
        TextView tv = pill(label, selected);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(34), 1f);
        lp.rightMargin = dp(4);
        tv.setLayoutParams(lp);
        tv.setOnClickListener(v -> {
            hapticClick(v);
            selectExclusive(v);
            listener.onFpsSelected(fps);
        });
        return tv;
    }

    private TextView resPill(String label, int res, int selectedRes) {
        boolean selected = (selectedRes == res);
        TextView tv = pill(label, selected);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(34), 1f);
        lp.rightMargin = dp(4);
        tv.setLayoutParams(lp);
        tv.setOnClickListener(v -> {
            hapticClick(v);
            selectExclusive(v);
            listener.onResolutionSelected(res);
        });
        return tv;
    }

    private TextView lutPill(String label, String lutId, String selectedLut) {
        boolean selected = selectedLut.equals(lutId);
        TextView tv = pill(label, selected);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(34));
        lp.rightMargin = dp(6);
        tv.setLayoutParams(lp);
        tv.setOnClickListener(v -> {
            hapticClick(v);
            selectExclusive(v);
            listener.onLutProfileSelected(lutId);
        });
        return tv;
    }

    private void selectExclusive(View v) {
        ViewGroup p = (ViewGroup) v.getParent();
        if (p == null) return;
        for (int i = 0; i < p.getChildCount(); i++) {
            View child = p.getChildAt(i);
            if (child instanceof TextView) applyPillStyle((TextView) child, child == v);
        }
    }

    public void updateLoopPills(TextView standard, TextView boomerang, boolean isBoomerang) {
        applyPillStyle(standard, !isBoomerang);
        applyPillStyle(boomerang, isBoomerang);
    }

    public TextView sectionHeader(String text) {
        TextView tv = text(text, 8, MUTED, true);
        tv.setLetterSpacing(0.12f);
        tv.setPadding(0, dp(12), 0, dp(6));
        return tv;
    }

    public TextView pill(String label, boolean active) {
        TextView tv = new TextView(activity);
        tv.setText(label);
        tv.setTextSize(8);
        tv.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
        tv.setLetterSpacing(0.08f);
        tv.setGravity(Gravity.CENTER);
        applyPillStyle(tv, active);
        return tv;
    }

    public void applyPillStyle(TextView tv, boolean active) {
        if (active) {
            tv.setTextColor(0xff0b0d0e);
            tv.setBackground(round(ACCENT, 16, ACCENT, 0));
        } else {
            tv.setTextColor(0xffd8ded6);
            tv.setBackground(round(0x22ffffff, 16, BORDER, dp(1)));
        }
    }

    public TextView text(String s, int sp, int color, boolean bold) {
        TextView tv = UiKit.text(activity, s, sp, color, bold);
        tv.setTypeface(android.graphics.Typeface.MONOSPACE, bold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        return tv;
    }

    public GradientDrawable round(int fill, int radiusDp, int strokeColor, int strokeWidthPx) {
        return UiKit.round(activity, fill, radiusDp, strokeColor, strokeWidthPx);
    }

    public int dp(int v) {
        return UiKit.dp(activity, v);
    }

    private void hapticClick(View v) {
        UiKit.hapticClick(activity, v);
    }
}
