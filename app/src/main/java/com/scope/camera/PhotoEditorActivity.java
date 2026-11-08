package com.scope.camera;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
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

    private Bitmap rawOriginalBitmap;
    private Bitmap currentGradedBitmap;
    private boolean isComparing = false;
    private TextView compareButton;
    private TextView compareBadge;
    private TextView loupeBadge;
    private boolean isLoupe1to1 = false;
    private LinearLayout brainBar;

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

    private void hapticClick(View v) {
        if (v != null && AppSettings.getBoolean(this, AppSettings.HAPTICS, true)) {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
    }

    private LinearLayout tabBar;
    private String activeTab = "TONE";

    private void startComparing() {
        if (rawOriginalBitmap == null || isComparing) return;
        isComparing = true;
        if (compareBadge != null) compareBadge.setVisibility(View.VISIBLE);
        if (compareButton != null) compareButton.setTextColor(ACCENT);
        preview.setImageBitmap(rawOriginalBitmap);
        if (AppSettings.getBoolean(this, AppSettings.HAPTICS, true)) {
            preview.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
    }

    private void stopComparing() {
        if (!isComparing) return;
        isComparing = false;
        if (compareBadge != null) compareBadge.setVisibility(View.GONE);
        if (compareButton != null) compareButton.setTextColor(0xffd8ded7);
        if (currentGradedBitmap != null) {
            preview.setImageBitmap(currentGradedBitmap);
        }
        if (AppSettings.getBoolean(this, AppSettings.HAPTICS, true)) {
            preview.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        }
    }

    private void refreshBrainBar() {
        if (brainBar == null) return;
        brainBar.removeAllViews();
        String currentBrain = look.brain == null ? AppSettings.BRAIN_LIKA_FILM : look.brain;
        String[] brains = AppSettings.BRAIN_CHOICES;
        for (int i = 0; i < brains.length; i++) {
            String b = brains[i];
            boolean active = currentBrain.equals(b);
            TextView pill = pill(b, active);
            pill.setPadding(dp(12), dp(6), dp(12), dp(6));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(30));
            if (i > 0) lp.leftMargin = dp(6);
            brainBar.addView(pill, lp);
            pill.setOnClickListener(v -> {
                hapticClick(v);
                applyBrainPreset(b);
            });
        }
    }

    private void applyBrainPreset(String preset) {
        look.brain = preset;
        if (AppSettings.BRAIN_MONO_SILVER.equals(preset) || "MONOCHROME".equals(preset)) {
            look.brain = AppSettings.BRAIN_MONO_SILVER;
            look.contrast = 1.25f;
            look.saturation = 0.0f;
            look.warmth = 0.0f;
            look.grain = 0.32f;
            look.halation = 0.10f;
            look.colorRollOff = 0.50f;
            look.toneCurve = "Filmic S-Curve";
            look.shadowProfile = "Deep Cinema Blacks";
        } else if (AppSettings.BRAIN_KODACHROME.equals(preset)) {
            look.contrast = 1.25f;
            look.saturation = 1.16f;
            look.warmth = 0.14f;
            look.grain = 0.18f;
            look.halation = 0.18f;
            look.colorRollOff = 0.78f;
            look.toneCurve = "Filmic S-Curve";
            look.shadowProfile = "Deep Cinema Blacks";
        } else if (AppSettings.BRAIN_FUJI_ETERNA.equals(preset)) {
            look.contrast = 0.95f;
            look.saturation = 0.88f;
            look.warmth = -0.04f;
            look.grain = 0.24f;
            look.halation = 0.12f;
            look.colorRollOff = 0.82f;
            look.toneCurve = "Filmic S-Curve";
            look.shadowProfile = "Lifted Vintage Blacks";
        } else if (AppSettings.BRAIN_BLEACH_BYPASS.equals(preset)) {
            look.contrast = 1.38f;
            look.saturation = 0.35f;
            look.warmth = -0.06f;
            look.grain = 0.38f;
            look.halation = 0.08f;
            look.colorRollOff = 0.40f;
            look.toneCurve = "Filmic S-Curve";
            look.shadowProfile = "Deep Cinema Blacks";
        } else if (AppSettings.BRAIN_NEUTRAL_RAW.equals(preset) || "NEUTRAL RAW".equals(preset)) {
            look.brain = AppSettings.BRAIN_NEUTRAL_RAW;
            look.contrast = 1.0f;
            look.saturation = 1.0f;
            look.warmth = 0.0f;
            look.grain = 0.0f;
            look.halation = 0.0f;
            look.vignette = 0.0f;
            look.exposureEv = 0.0f;
            look.whiteBalanceKelvin = 0;
            look.highlights = 0.50f;
            look.shadows = 0.50f;
            look.pivot = 0.50f;
            look.sharpening = 0.0f;
            look.chromaNr = 0.0f;
            look.colorRollOff = 0.50f;
            look.toneCurve = "Linear Response";
            look.shadowProfile = "Neutral / Open Shadows";
        } else { // "LIKA / FILM"
            look.brain = AppSettings.BRAIN_LIKA_FILM;
            look.contrast = 1.12f;
            look.saturation = 0.98f;
            look.warmth = 0.08f;
            look.grain = 0.22f;
            look.halation = 0.16f;
            look.colorRollOff = 0.72f;
            look.toneCurve = "Filmic S-Curve";
            look.shadowProfile = "Deep Cinema Blacks";
        }
        refreshBrainBar();
        populateControls();
        queuePreview();
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
        header.addView(back, new LinearLayout.LayoutParams(dp(36), dp(40)));
        back.setOnClickListener(v -> { hapticClick(v); finish(); });
        TextView title = text("DEVELOP", 13, 0xfff1f2ec, true);
        title.setPadding(dp(8), 0, 0, 0);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

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

        TextView copyBtn = pill("COPY", false);
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(dp(46), dp(32));
        copyLp.leftMargin = dp(4);
        header.addView(copyBtn, copyLp);
        copyBtn.setOnClickListener(v -> {
            hapticClick(v);
            PhotoEditStore.copyToClipboard(this, look);
            Toast.makeText(this, "Look recipe copied", Toast.LENGTH_SHORT).show();
        });

        TextView pasteBtn = pill("PASTE", false);
        LinearLayout.LayoutParams pasteLp = new LinearLayout.LayoutParams(dp(50), dp(32));
        pasteLp.leftMargin = dp(4);
        header.addView(pasteBtn, pasteLp);
        pasteBtn.setOnClickListener(v -> {
            hapticClick(v);
            if (!PhotoEditStore.hasClipboard(this)) {
                Toast.makeText(this, "No copied recipe yet", Toast.LENGTH_SHORT).show();
                return;
            }
            look = PhotoEditStore.pasteFromClipboard(this);
            refreshBrainBar();
            populateControls();
            queuePreview();
            Toast.makeText(this, "Look recipe pasted", Toast.LENGTH_SHORT).show();
        });

        TextView reset = pill("RESET", false);
        LinearLayout.LayoutParams resetLp = new LinearLayout.LayoutParams(dp(50), dp(32));
        resetLp.leftMargin = dp(4);
        header.addView(reset, resetLp);
        reset.setOnClickListener(v -> { hapticClick(v); resetLook(); });

        saveButton = pill("SAVE", true);
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(dp(50), dp(32));
        saveLp.leftMargin = dp(4);
        header.addView(saveButton, saveLp);
        saveButton.setOnClickListener(v -> { hapticClick(v); saveRender(); });
        page.addView(header);

        FrameLayout previewContainer = new FrameLayout(this);
        preview = new ImageView(this);
        preview.setBackgroundColor(0xff121617);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        previewContainer.addView(preview, new FrameLayout.LayoutParams(-1, -1));

        compareBadge = text("ORIGINAL SENSOR RAW", 8, ACCENT, true);
        compareBadge.setBackground(round(0xdd0b0d0e, 14, ACCENT, dp(1)));
        compareBadge.setPadding(dp(12), dp(4), dp(12), dp(4));
        compareBadge.setVisibility(View.GONE);
        FrameLayout.LayoutParams cbLp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        cbLp.topMargin = dp(12);
        previewContainer.addView(compareBadge, cbLp);

        loupeBadge = text("1:1 LOUPE · 100% CROP", 8, ACCENT, true);
        loupeBadge.setBackground(round(0xdd0b0d0e, 14, 0x99d7b574, dp(1)));
        loupeBadge.setPadding(dp(10), dp(4), dp(10), dp(4));
        loupeBadge.setVisibility(View.GONE);
        FrameLayout.LayoutParams lbLp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lbLp.bottomMargin = dp(10);
        previewContainer.addView(loupeBadge, lbLp);

        GestureDetector previewGestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDoubleTap(MotionEvent e) {
                isLoupe1to1 = !isLoupe1to1;
                preview.setScaleType(isLoupe1to1 ? ImageView.ScaleType.CENTER : ImageView.ScaleType.FIT_CENTER);
                loupeBadge.setVisibility(isLoupe1to1 ? View.VISIBLE : View.GONE);
                hapticClick(preview);
                return true;
            }
            @Override public void onLongPress(MotionEvent e) {
                startComparing();
            }
        });

        preview.setOnTouchListener((v, event) -> {
            previewGestureDetector.onTouchEvent(event);
            if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                if (isComparing) {
                    stopComparing();
                }
            }
            return true;
        });

        page.addView(previewContainer, new LinearLayout.LayoutParams(-1, 0, 1));

        status = text("READING SENSOR ORIGINAL", 8, 0xff9ba59e, false);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(8), dp(6), dp(8), dp(2));
        page.addView(status);

        HorizontalScrollView brainScroll = new HorizontalScrollView(this);
        brainScroll.setHorizontalScrollBarEnabled(false);
        brainBar = new LinearLayout(this);
        brainBar.setOrientation(LinearLayout.HORIZONTAL);
        brainBar.setGravity(Gravity.CENTER_VERTICAL);
        brainBar.setPadding(dp(14), dp(2), dp(14), dp(4));
        brainScroll.addView(brainBar, new FrameLayout.LayoutParams(-2, -2));
        page.addView(brainScroll);
        refreshBrainBar();

        tabBar = new LinearLayout(this);
        tabBar.setGravity(Gravity.CENTER_VERTICAL);
        tabBar.setPadding(dp(14), dp(2), dp(14), dp(6));
        page.addView(tabBar);
        refreshTabs();

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        controlsList = new LinearLayout(this);
        controlsList.setOrientation(LinearLayout.VERTICAL);
        controlsList.setPadding(dp(14), dp(2), dp(14), dp(12));
        scroll.addView(controlsList);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, dp(300)));
        populateControls();
        setContentView(page);
    }

    private void refreshTabs() {
        if (tabBar == null) return;
        tabBar.removeAllViews();
        String[] tabs = {"TONE", "COLOR", "TEXTURE", "CROP"};
        for (String tab : tabs) {
            TextView pill = pill(tab, tab.equals(activeTab));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(32), 1f);
            if (!tab.equals("TONE")) lp.leftMargin = dp(6);
            tabBar.addView(pill, lp);
            pill.setOnClickListener(v -> {
                hapticClick(v);
                activeTab = tab;
                refreshTabs();
                populateControls();
            });
        }
    }

    private void populateControls() {
        controls.clear();
        controlsList.removeAllViews();
        if ("TONE".equals(activeTab)) {
            addControl("Exposure", "EV", 0, -20, 20, Math.round(look.exposureEv * 10f));
            addControl("Contrast", "", 2, 50, 150, Math.round(look.contrast * 100));
            addControl("Highlights", "", 4, 0, 100, Math.round(look.highlights * 100));
            addControl("Shadows", "", 5, 0, 100, Math.round(look.shadows * 100));
            addControl("Pivot", "", 8, 0, 100, Math.round(look.pivot * 100));
        } else if ("COLOR".equals(activeTab)) {
            addControl("White balance", "K", 1, 0, 44, look.whiteBalanceKelvin <= 0 ? 0 : 1 + (look.whiteBalanceKelvin - 3200) / 100);
            addControl("Warmth shift", "", 12, 0, 100, Math.round(look.warmth * 50f + 50f));
            addControl("Colour intensity", "", 3, 50, 160, Math.round(look.saturation * 100));
            addControl("Saturated colour roll-off", "", 13, 0, 100, Math.round(look.colorRollOff * 100));
        } else if ("TEXTURE".equals(activeTab)) {
            addControl("Film grain", "", 6, 0, 100, Math.round(look.grain * 100));
            addGrainStructureChoice();
            addControl("Halation", "", 7, 0, 100, Math.round(look.halation * 100));
            addControl("Sharpening", "", 9, 0, 100, Math.round(look.sharpening * 100));
            addControl("Colour noise reduction", "", 10, 0, 100, Math.round(look.chromaNr * 100));
            addControl("Vignette", "", 11, 0, 100, Math.round(look.vignette * 100));
        } else if ("CROP".equals(activeTab)) {
            addCropControls();
        }
    }

    private void addGrainStructureChoice() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(8), dp(12), dp(8));
        card.setBackground(round(0xff121617, 12, 0xff293031, dp(1)));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.bottomMargin = dp(6);
        controlsList.addView(card, cardLp);

        TextView label = text("Grain Structure", 10, 0xffe6eae4, true);
        card.addView(label);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, dp(32));
        rowLp.topMargin = dp(6);
        card.addView(row, rowLp);

        String[] structures = {"Fine · 35 mm", "Soft · 16 mm", "Coarse · 8 mm"};
        String current = look.grainStructure == null ? "Fine · 35 mm" : look.grainStructure;
        for (int i = 0; i < structures.length; i++) {
            String s = structures[i];
            String labelShort = s.split("·")[1].trim();
            boolean active = current.startsWith(s.substring(0, 4));
            TextView pill = pill(labelShort, active);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
            if (i > 0) lp.leftMargin = dp(4);
            row.addView(pill, lp);
            pill.setOnClickListener(v -> {
                hapticClick(v);
                look.grainStructure = s;
                populateControls();
                queuePreview();
            });
        }
    }

    private void addCropControls() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackground(round(0xff121617, 12, 0xff293031, dp(1)));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.bottomMargin = dp(8);
        controlsList.addView(card, cardLp);

        TextView title = text("FRAME SHAPE & ASPECT RATIO", 9, ACCENT, true);
        title.setLetterSpacing(.12f);
        card.addView(title);
        TextView sub = text("Non-destructive crop redeveloped from full sensor RAW", 8, 0xff89948e, false);
        sub.setPadding(0, dp(2), 0, dp(8));
        card.addView(sub);

        String currentAspect = look.aspectRatio != null ? look.aspectRatio : (frame != null ? frame.aspectRatio : "4:3");
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams row1Lp = new LinearLayout.LayoutParams(-1, dp(34));
        row1Lp.bottomMargin = dp(6);
        card.addView(row1, row1Lp);

        String[] primaryRatios = {"2.39:1", "1.85:1", "1.66:1", "16:9"};
        for (int i = 0; i < primaryRatios.length; i++) {
            String ratio = primaryRatios[i];
            boolean selected = ratio.equals(currentAspect) || (currentAspect != null && currentAspect.startsWith(ratio));
            TextView pill = pill(ratio, selected);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
            if (i > 0) lp.leftMargin = dp(4);
            row1.addView(pill, lp);
            pill.setOnClickListener(v -> {
                hapticClick(v);
                look.aspectRatio = ratio;
                populateControls();
                queuePreview();
            });
        }

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams row2Lp = new LinearLayout.LayoutParams(-1, dp(34));
        row2Lp.bottomMargin = dp(6);
        card.addView(row2, row2Lp);

        String[] secondaryRatios = {"3:2", "4:3", "1:1"};
        for (int i = 0; i < secondaryRatios.length; i++) {
            String ratio = secondaryRatios[i];
            boolean selected = ratio.equals(currentAspect) || (currentAspect != null && currentAspect.startsWith(ratio));
            TextView pill = pill(ratio, selected);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
            if (i > 0) lp.leftMargin = dp(4);
            row2.addView(pill, lp);
            pill.setOnClickListener(v -> {
                hapticClick(v);
                look.aspectRatio = ratio;
                populateControls();
                queuePreview();
            });
        }

        LinearLayout mirrorCard = new LinearLayout(this);
        mirrorCard.setOrientation(LinearLayout.HORIZONTAL);
        mirrorCard.setGravity(Gravity.CENTER_VERTICAL);
        mirrorCard.setPadding(dp(12), dp(10), dp(12), dp(10));
        mirrorCard.setBackground(round(0xff121617, 12, 0xff293031, dp(1)));
        LinearLayout.LayoutParams mirrorLp = new LinearLayout.LayoutParams(-1, -2);
        mirrorLp.bottomMargin = dp(8);
        controlsList.addView(mirrorCard, mirrorLp);

        TextView mirrorLabel = text("Flip / Mirror Horizontal", 10, 0xffe6eae4, true);
        mirrorCard.addView(mirrorLabel, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView flipPill = pill(look.flipHorizontal ? "MIRRORED" : "NORMAL", look.flipHorizontal);
        mirrorCard.addView(flipPill, new LinearLayout.LayoutParams(dp(88), dp(32)));
        flipPill.setOnClickListener(v -> {
            hapticClick(v);
            look.flipHorizontal = !look.flipHorizontal;
            populateControls();
            queuePreview();
        });

        LinearLayout borderCard = new LinearLayout(this);
        borderCard.setOrientation(LinearLayout.VERTICAL);
        borderCard.setPadding(dp(12), dp(10), dp(12), dp(10));
        borderCard.setBackground(round(0xff121617, 12, 0xff293031, dp(1)));
        controlsList.addView(borderCard, new LinearLayout.LayoutParams(-1, -2));

        TextView borderTitle = text("FRAME BORDER & REBATE", 9, ACCENT, true);
        borderTitle.setLetterSpacing(.12f);
        borderCard.addView(borderTitle);

        LinearLayout borderRow = new LinearLayout(this);
        borderRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams borderRowLp = new LinearLayout.LayoutParams(-1, dp(34));
        borderRowLp.topMargin = dp(8);
        borderCard.addView(borderRow, borderRowLp);

        String[] borders = {"None", "35mm Film Rebate", "Museum Matte"};
        String[] shortLabels = {"NONE", "35MM REBATE", "MUSEUM MATTE"};
        String curBorder = look.frameBorder == null ? "None" : look.frameBorder;
        for (int i = 0; i < borders.length; i++) {
            final String b = borders[i];
            boolean active = curBorder.equals(b);
            TextView bp = pill(shortLabels[i], active);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
            if (i > 0) lp.leftMargin = dp(4);
            borderRow.addView(bp, lp);
            bp.setOnClickListener(v -> {
                hapticClick(v);
                look.frameBorder = b;
                populateControls();
                queuePreview();
            });
        }
    }

    private void resetControlToDefault(int type, SeekBar seek) {
        int defaultVal;
        switch (type) {
            case 0: defaultVal = 0; break;
            case 1: defaultVal = 0; break;
            case 2: defaultVal = 100; break;
            case 3: defaultVal = 100; break;
            case 4: defaultVal = 50; break;
            case 5: defaultVal = 50; break;
            case 6: defaultVal = 0; break;
            case 7: defaultVal = 0; break;
            case 8: defaultVal = 50; break;
            case 9: defaultVal = 50; break;
            case 10: defaultVal = 50; break;
            case 11: defaultVal = 0; break;
            case 12: defaultVal = 50; break;
            case 13: defaultVal = 50; break;
            default: defaultVal = 0; break;
        }
        updateLook(type, defaultVal);
        seek.setProgress(defaultVal - getControlMin(type));
        for (Control c : controls) {
            if (c.type == type) {
                updateControlLabel(c, defaultVal);
                break;
            }
        }
        if (AppSettings.getBoolean(this, AppSettings.HAPTICS, true)) {
            seek.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
        queuePreview();
    }

    private int getControlMin(int type) {
        if (type == 0) return -20;
        if (type == 2 || type == 3) return 50;
        return 0;
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

        TextView resetTag = text("RESET", 7, 0x66d7b574, true);
        resetTag.setPadding(dp(6), dp(2), dp(6), dp(2));
        resetTag.setBackground(round(0x22d7b574, 8, 0x44d7b574, dp(1)));
        head.addView(resetTag);

        TextView value = text("", 9, ACCENT, true);
        value.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        value.setPadding(dp(8), 0, 0, 0);
        head.addView(value);
        card.addView(head);
        SeekBar seek = new SeekBar(this);
        seek.setMax(max - min);
        seek.setProgress(Math.max(0, Math.min(max - min, current - min)));
        seek.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        seek.setThumbTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        card.addView(seek, new LinearLayout.LayoutParams(-1, dp(30)));

        View.OnClickListener resetListener = v -> resetControlToDefault(type, seek);
        resetTag.setOnClickListener(resetListener);
        value.setOnClickListener(resetListener);
        head.setOnLongClickListener(v -> {
            resetControlToDefault(type, seek);
            return true;
        });

        Control control = new Control(type, seek, value);
        controls.add(control);
        updateControlLabel(control, min + seek.getProgress());
        final int[] lastHapticVal = {current};
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int selected = min + progress;
                updateLook(type, selected);
                updateControlLabel(control, selected);
                if (fromUser) {
                    if (AppSettings.getBoolean(PhotoEditorActivity.this, AppSettings.HAPTICS, true)) {
                        boolean isLandmark = false;
                        if (type == 0 && selected == 0) isLandmark = true;
                        else if (type == 1 && selected == 0) isLandmark = true;
                        else if (type == 2 && selected == 100) isLandmark = true;
                        else if (type == 3 && selected == 100) isLandmark = true;
                        else if (type == 4 && selected == 0) isLandmark = true;
                        else if (type == 5 && selected == 0) isLandmark = true;
                        else if (type == 6 && selected == 0) isLandmark = true;
                        else if (type == 7 && selected == 0) isLandmark = true;
                        else if (type == 8 && selected == 50) isLandmark = true;
                        else if (type == 9 && selected == 50) isLandmark = true;
                        else if (type == 10 && selected == 50) isLandmark = true;
                        else if (type == 11 && selected == 0) isLandmark = true;
                        else if (type == 12 && selected == 50) isLandmark = true;
                        else if (type == 13 && selected == 50) isLandmark = true;

                        if (isLandmark) {
                            bar.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                            lastHapticVal[0] = selected;
                        } else if (Math.abs(selected - lastHapticVal[0]) >= 6) {
                            bar.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                            lastHapticVal[0] = selected;
                        }
                    }
                    queuePreview();
                }
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
                RawDeveloper.Look neutralLook = new RawDeveloper.Look();
                neutralLook.brain = "NEUTRAL RAW";
                neutralLook.contrast = 1.0f;
                neutralLook.pivot = 0.5f;
                neutralLook.saturation = 1.0f;
                neutralLook.grain = 0f;
                neutralLook.halation = 0f;
                neutralLook.vignette = 0f;
                neutralLook.sharpening = 0f;
                neutralLook.chromaNr = 0f;
                neutralLook.colorRollOff = 0.5f;
                neutralLook.exposureEv = 0f;
                neutralLook.warmth = 0f;
                neutralLook.aspectRatio = look.aspectRatio != null ? look.aspectRatio : loaded.aspectRatio;
                neutralLook.flipHorizontal = look.flipHorizontal;
                Bitmap bitmap = RawDeveloper.develop(loaded, look.copy(), 1400);
                int targetW = bitmap != null ? bitmap.getWidth() : 1400;
                int targetH = bitmap != null ? bitmap.getHeight() : 1400;
                int targetDim = Math.max(targetW, targetH);
                Bitmap neutralBmp = RawDeveloper.develop(loaded, neutralLook, targetDim);
                if (neutralBmp != null && bitmap != null && (neutralBmp.getWidth() != targetW || neutralBmp.getHeight() != targetH)) {
                    Bitmap scaled = Bitmap.createScaledBitmap(neutralBmp, targetW, targetH, true);
                    if (scaled != neutralBmp) {
                        neutralBmp.recycle();
                        neutralBmp = scaled;
                    }
                }
                final Bitmap finalNeutralBmp = neutralBmp;
                mainHandler.post(() -> {
                    frame = loaded;
                    rawOriginalBitmap = finalNeutralBmp;
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
        Bitmap previous = currentGradedBitmap;
        currentGradedBitmap = bitmap;
        if (!isComparing) {
            preview.setImageBitmap(bitmap);
        }
        if (previous != null && previous != bitmap && previous != rawOriginalBitmap && !previous.isRecycled()) {
            previous.recycle();
        }
    }

    private void resetLook() {
        PhotoEditStore.reset(this, archiveKey);
        look = PhotoEditStore.load(this, archiveKey);
        refreshBrainBar();
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
                try (android.os.ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(photoUri, "rw")) {
                    if (pfd != null) {
                        android.media.ExifInterface exif = new android.media.ExifInterface(pfd.getFileDescriptor());
                        exif.setAttribute(android.media.ExifInterface.TAG_MAKE, android.os.Build.MANUFACTURER);
                        exif.setAttribute(android.media.ExifInterface.TAG_MODEL, android.os.Build.MODEL);
                        exif.setAttribute(android.media.ExifInterface.TAG_SOFTWARE, "Lika Lens · Chromagraph Pipeline");
                        if (source.exposureTimeNs > 0) {
                            exif.setAttribute(android.media.ExifInterface.TAG_EXPOSURE_TIME, String.valueOf(source.exposureTimeNs / 1_000_000_000.0));
                        }
                        if (source.sensitivityIso > 0) {
                            exif.setAttribute(android.media.ExifInterface.TAG_ISO_SPEED_RATINGS, String.valueOf(source.sensitivityIso));
                        }
                        exif.saveAttributes();
                    }
                } catch (Throwable ignored) { }
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
        if (rawOriginalBitmap != null && !rawOriginalBitmap.isRecycled()) {
            rawOriginalBitmap.recycle();
            rawOriginalBitmap = null;
        }
        if (currentGradedBitmap != null && !currentGradedBitmap.isRecycled()) {
            currentGradedBitmap.recycle();
            currentGradedBitmap = null;
        }
        super.onDestroy();
    }
}
