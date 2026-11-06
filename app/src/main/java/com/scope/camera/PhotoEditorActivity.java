package com.scope.camera;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Re-develops one photo from its archived sensor data and saves only its look settings. */
public class PhotoEditorActivity extends Activity {
    static final String EXTRA_KEY = "scope_archive_key";
    private static final int BG = 0xff0b0d0e;
    private static final int ACCENT = 0xffd7b574;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<Control> controls = new ArrayList<>();
    private RawArchive.Frame frame;
    private RawDeveloper.Look look;
    private String archiveKey;
    private Uri photoUri;
    private ImageView preview;
    private TextView status;
    private TextView saveButton;
    private LinearLayout controlsList;
    private int renderGeneration;

    private static final class Control {
        final int type;
        final SeekBar seek;
        final TextView value;
        Control(int type, SeekBar seek, TextView value) { this.type = type; this.seek = seek; this.value = value; }
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        archiveKey = getIntent().getStringExtra(EXTRA_KEY);
        String uri = getIntent().getStringExtra(PhotoViewerActivity.EXTRA_URI);
        photoUri = uri == null ? null : Uri.parse(uri);
        look = PhotoEditStore.load(this, archiveKey);
        build();
        loadSource();
    }

    private void build() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(BG);
        page.setFitsSystemWindows(true);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(10), dp(16), dp(10));
        TextView back = text("‹", 28, 0xffedf0e9, true);
        back.setGravity(Gravity.CENTER);
        header.addView(back, new LinearLayout.LayoutParams(dp(38), dp(40)));
        back.setOnClickListener(v -> finish());
        TextView title = text("DEVELOP", 13, 0xfff1f2ec, true);
        title.setPadding(dp(8), 0, 0, 0);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView reset = pill("RESET", false);
        header.addView(reset, new LinearLayout.LayoutParams(dp(56), dp(32)));
        reset.setOnClickListener(v -> resetLook());
        saveButton = pill("SAVE", true);
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(dp(58), dp(32));
        saveLp.leftMargin = dp(6);
        header.addView(saveButton, saveLp);
        saveButton.setOnClickListener(v -> saveRender());
        page.addView(header);

        preview = new ImageView(this);
        preview.setBackgroundColor(0xff121617);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        page.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1));
        status = text("READING SENSOR ORIGINAL", 8, 0xff9ba59e, false);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(8), dp(8), dp(8), dp(3));
        page.addView(status);
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        controlsList = new LinearLayout(this);
        controlsList.setOrientation(LinearLayout.VERTICAL);
        controlsList.setPadding(dp(14), dp(4), dp(14), dp(12));
        scroll.addView(controlsList);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, dp(300)));
        populateControls();
        setContentView(page);
    }

    private void populateControls() {
        controls.clear();
        controlsList.removeAllViews();
        addControl("Exposure", "EV", 0, -20, 20, Math.round(look.exposureEv * 10f));
        addControl("White balance", "K", 1, 0, 44, look.whiteBalanceKelvin <= 0 ? 0 : 1 + (look.whiteBalanceKelvin - 3200) / 100);
        addControl("Warmth shift", "", 12, 0, 100, Math.round(look.warmth * 50f + 50f));
        addControl("Contrast", "", 2, 50, 150, Math.round(look.contrast * 100));
        addControl("Colour", "", 3, 50, 160, Math.round(look.saturation * 100));
        addControl("Saturated colour roll-off", "", 13, 0, 100, Math.round(look.colorRollOff * 100));
        addControl("Highlights", "", 4, 0, 100, Math.round(look.highlights * 100));
        addControl("Shadows", "", 5, 0, 100, Math.round(look.shadows * 100));
        addControl("Pivot", "", 8, 0, 100, Math.round(look.pivot * 100));
        addControl("Grain", "", 6, 0, 100, Math.round(look.grain * 100));
        addControl("Halation", "", 7, 0, 100, Math.round(look.halation * 100));
        addControl("Sharpening", "", 9, 0, 100, Math.round(look.sharpening * 100));
        addControl("Colour noise reduction", "", 10, 0, 100, Math.round(look.chromaNr * 100));
        addControl("Vignette", "", 11, 0, 100, Math.round(look.vignette * 100));
    }

    private void addControl(String label, String unit, int type, int min, int max, int current) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(8), dp(12), dp(6));
        card.setBackground(round(0xff121617, 12, 0xff293031, dp(1)));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.bottomMargin = dp(6);
        controlsList.addView(card, cardLp);
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = text(label, 10, 0xffe6eae4, true);
        head.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        TextView value = text("", 9, ACCENT, true);
        value.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        head.addView(value);
        card.addView(head);
        SeekBar seek = new SeekBar(this);
        seek.setMax(max - min);
        seek.setProgress(Math.max(0, Math.min(max - min, current - min)));
        seek.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        seek.setThumbTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        card.addView(seek, new LinearLayout.LayoutParams(-1, dp(30)));
        Control control = new Control(type, seek, value);
        controls.add(control);
        updateControlLabel(control, min + seek.getProgress());
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int selected = min + progress;
                updateLook(type, selected);
                updateControlLabel(control, selected);
                if (fromUser) queuePreview();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
    }

    private void updateLook(int type, int value) {
        switch (type) {
            case 0: look.exposureEv = value / 10f; break;
            case 1: look.whiteBalanceKelvin = value == 0 ? 0 : 3200 + (value - 1) * 100; break;
            case 2: look.contrast = value / 100f; break;
            case 3: look.saturation = value / 100f; break;
            case 4: look.highlights = value / 100f; break;
            case 5: look.shadows = value / 100f; break;
            case 6: look.grain = value / 100f; break;
            case 7: look.halation = value / 100f; break;
            case 8: look.pivot = value / 100f; break;
            case 9: look.sharpening = value / 100f; break;
            case 10: look.chromaNr = value / 100f; break;
            case 11: look.vignette = value / 100f; break;
            case 12: look.warmth = (value - 50) / 50f; break;
            case 13: look.colorRollOff = value / 100f; break;
        }
    }

    private void updateControlLabel(Control control, int value) {
        switch (control.type) {
            case 0: control.value.setText(String.format(Locale.US, "%+.1f EV", value / 10f)); break;
            case 1: control.value.setText(value == 0 ? "AUTO" : (3200 + (value - 1) * 100) + " K"); break;
            case 2: case 3: control.value.setText(String.format(Locale.US, "%.2f", value / 100f)); break;
            case 12: control.value.setText(value == 50 ? "0" : value > 50 ? "WARM +" + (value - 50) : "COOL " + (50 - value)); break;
            default: control.value.setText(value + "%"); break;
        }
    }

    private void loadSource() {
        status.setText("READING SENSOR ORIGINAL");
        worker.execute(() -> {
            try {
                RawArchive.Frame loaded = RawArchive.read(this, archiveKey);
                Bitmap bitmap = RawDeveloper.develop(loaded, look.copy(), 1400);
                mainHandler.post(() -> {
                    frame = loaded;
                    setPreview(bitmap);
                    status.setText("RAW ORIGINAL  ·  LOOK PREVIEW");
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    status.setText("COULD NOT OPEN SENSOR ORIGINAL");
                    saveButton.setEnabled(false);
                    Toast.makeText(this, "Could not develop this frame.", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void queuePreview() {
        status.setText("DEVELOPING PREVIEW");
        renderGeneration++;
        mainHandler.removeCallbacks(previewRunnable);
        mainHandler.postDelayed(previewRunnable, 180);
    }

    private final Runnable previewRunnable = this::renderPreviewNow;

    private void renderPreviewNow() {
        if (frame == null) return;
        int generation = renderGeneration;
        RawDeveloper.Look snapshot = look.copy();
        RawArchive.Frame source = frame;
        worker.execute(() -> {
            Bitmap bitmap = RawDeveloper.develop(source, snapshot, 1400);
            mainHandler.post(() -> {
                if (generation != renderGeneration || isFinishing()) { if (bitmap != null) bitmap.recycle(); return; }
                setPreview(bitmap);
                status.setText("RAW ORIGINAL  ·  LOOK PREVIEW");
            });
        });
    }

    private void setPreview(Bitmap bitmap) {
        if (bitmap == null) return;
        Bitmap previous = (Bitmap) preview.getTag();
        preview.setImageBitmap(bitmap);
        preview.setTag(bitmap);
        if (previous != null && previous != bitmap && !previous.isRecycled()) previous.recycle();
    }

    private void resetLook() {
        PhotoEditStore.reset(this, archiveKey);
        look = PhotoEditStore.load(this, archiveKey);
        populateControls();
        queuePreview();
    }

    private void saveRender() {
        if (frame == null || photoUri == null) return;
        saveButton.setEnabled(false);
        saveButton.setText("SAVE…");
        status.setText("DEVELOPING FULL FRAME");
        RawDeveloper.Look snapshot = look.copy();
        RawArchive.Frame source = frame;
        worker.execute(() -> {
            Bitmap bitmap = null;
            String failure = null;
            try {
                bitmap = RawDeveloper.develop(source, snapshot, 0);
                try (OutputStream stream = getContentResolver().openOutputStream(photoUri, "w")) {
                    if (stream == null || !bitmap.compress(Bitmap.CompressFormat.JPEG, 96, stream)) {
                        throw new IllegalStateException("Could not write JPEG render");
                    }
                }
                PhotoEditStore.save(this, archiveKey, snapshot);
            } catch (Exception e) {
                failure = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            } finally {
                if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            }
            String error = failure;
            mainHandler.post(() -> {
                if (error == null) {
                    Toast.makeText(this, "Updated render saved. RAW original is unchanged.", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                } else {
                    saveButton.setEnabled(true);
                    saveButton.setText("SAVE");
                    status.setText("SAVE FAILED");
                    Toast.makeText(this, "Could not save render: " + error, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private TextView pill(String label, boolean active) {
        TextView view = text(label, 8, active ? ACCENT : 0xffd8ded7, true);
        view.setGravity(Gravity.CENTER);
        view.setBackground(round(active ? 0xff1d1d1a : 0xff202626, 18,
                active ? ACCENT : 0xff39413e, dp(1)));
        return view;
    }

    private GradientDrawable round(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius));
        if (strokeWidth > 0) d.setStroke(strokeWidth, strokeColor);
        return d;
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        worker.shutdown();
        Bitmap bitmap = (Bitmap) preview.getTag();
        if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        super.onDestroy();
    }
}
