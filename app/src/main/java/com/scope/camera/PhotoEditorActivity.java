package com.scope.camera;

import android.app.Activity;
import android.content.Intent;
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
import android.util.Log;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Re-develops one photo from its archived sensor data and saves only its look settings. */
public class PhotoEditorActivity extends Activity {
    private static final String TAG = "PhotoEditorActivity";
    static final String EXTRA_KEY = "scope_archive_key";
    private static final int BG = 0xff0b0d0e;
    private static final int ACCENT = 0xffd7b574;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<LookRecipeControl> controls = new ArrayList<>();
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
    private boolean isScrubbing = false;
    private LinearLayout brainBar;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        archiveKey = getIntent().getStringExtra(EXTRA_KEY);
        String uri = getIntent().getStringExtra(PhotoViewerActivity.EXTRA_URI);
        photoUri = uri == null ? null : Uri.parse(uri);
        LutManager.init(this);
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

        TextView recipeBtn = pill("RECIPE", false);
        LinearLayout.LayoutParams recLp = new LinearLayout.LayoutParams(dp(54), dp(32));
        recLp.leftMargin = dp(4);
        header.addView(recipeBtn, recLp);
        recipeBtn.setOnClickListener(v -> {
            hapticClick(v);
            showSaveRecipeDialog();
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

        HorizontalScrollView brainScroll = new HorizontalScrollView(this);
        brainScroll.setHorizontalScrollBarEnabled(false);
        brainBar = new LinearLayout(this);
        brainBar.setOrientation(LinearLayout.HORIZONTAL);
        brainBar.setGravity(Gravity.CENTER_VERTICAL);
        brainBar.setPadding(dp(14), dp(2), dp(14), dp(4));
        brainScroll.addView(brainBar, new FrameLayout.LayoutParams(-2, -2));

        tabBar = new LinearLayout(this);
        tabBar.setGravity(Gravity.CENTER_VERTICAL);
        tabBar.setPadding(dp(14), dp(2), dp(14), dp(6));

        WindowMetricsHelper.WindowSizeInfo windowInfo = WindowMetricsHelper.computeWindowSizeInfo(this);
        boolean twoPane = windowInfo.isTwoPaneRecommended;

        if (twoPane) {
            LinearLayout splitBody = new LinearLayout(this);
            splitBody.setOrientation(LinearLayout.HORIZONTAL);

            LinearLayout leftPane = new LinearLayout(this);
            leftPane.setOrientation(LinearLayout.VERTICAL);
            leftPane.addView(previewContainer, new LinearLayout.LayoutParams(-1, 0, 1f));
            leftPane.addView(status);
            splitBody.addView(leftPane, new LinearLayout.LayoutParams(0, -1, 1f));

            LinearLayout rightPane = new LinearLayout(this);
            rightPane.setOrientation(LinearLayout.VERTICAL);
            rightPane.setBackground(round(0xff0e1314, 0, 0x33454d49, dp(1)));
            rightPane.setPadding(0, dp(4), 0, 0);

            rightPane.addView(brainScroll);
            refreshBrainBar();
            rightPane.addView(tabBar);
            refreshTabs();

            ScrollView scroll = new ScrollView(this);
            scroll.setVerticalScrollBarEnabled(false);
            controlsList = new LinearLayout(this);
            controlsList.setOrientation(LinearLayout.VERTICAL);
            controlsList.setPadding(dp(16), dp(4), dp(16), dp(16));
            scroll.addView(controlsList);
            rightPane.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

            int paneWidth = Math.min(dp(440), (int) (windowInfo.widthDp * getResources().getDisplayMetrics().density * 0.42f));
            if (paneWidth < dp(360)) paneWidth = dp(360);
            splitBody.addView(rightPane, new LinearLayout.LayoutParams(paneWidth, -1));

            page.addView(splitBody, new LinearLayout.LayoutParams(-1, 0, 1f));
        } else {
            page.addView(previewContainer, new LinearLayout.LayoutParams(-1, 0, 1));
            page.addView(status);
            page.addView(brainScroll);
            refreshBrainBar();
            page.addView(tabBar);
            refreshTabs();

            ScrollView scroll = new ScrollView(this);
            scroll.setVerticalScrollBarEnabled(false);
            controlsList = new LinearLayout(this);
            controlsList.setOrientation(LinearLayout.VERTICAL);
            controlsList.setPadding(dp(14), dp(2), dp(14), dp(12));
            scroll.addView(controlsList);
            page.addView(scroll, new LinearLayout.LayoutParams(-1, dp(300)));
        }

        populateControls();
        if (currentGradedBitmap != null) {
            preview.setImageBitmap(currentGradedBitmap);
        } else if (rawOriginalBitmap != null) {
            preview.setImageBitmap(rawOriginalBitmap);
        }
        if (isLoupe1to1) {
            preview.setScaleType(ImageView.ScaleType.CENTER);
            if (loupeBadge != null) loupeBadge.setVisibility(View.VISIBLE);
        }
        setContentView(page);
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        build();
    }

    private void refreshTabs() {
        if (tabBar == null) return;
        tabBar.removeAllViews();
        String[] tabs = {"TONE", "COLOR", "LUT", "TEXTURE", "CROP"};
        for (String tab : tabs) {
            TextView pill = pill(tab, tab.equals(activeTab));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(32), 1f);
            if (!tab.equals("TONE")) lp.leftMargin = dp(4);
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
        } else if ("LUT".equals(activeTab)) {
            addLutControls();
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

    private static final int REQUEST_CODE_PICK_LUT = 1055;

    private void addLutControls() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackground(round(0xff121617, 12, 0xff293031, dp(1)));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.bottomMargin = dp(10);
        controlsList.addView(card, cardLp);

        TextView title = text("3D COLOR LOOKUP TABLE (LUT)", 10, ACCENT, true);
        card.addView(title);

        TextView subtitle = text("Select an authentic film stock 3D LUT or import custom .CUBE grading files.", 9, 0xff8e9690, false);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.topMargin = dp(2);
        subLp.bottomMargin = dp(8);
        card.addView(subtitle, subLp);

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout lutPills = new LinearLayout(this);
        lutPills.setOrientation(LinearLayout.HORIZONTAL);
        lutPills.setPadding(0, dp(2), 0, dp(4));

        List<String> available = LutManager.getAvailableLutNames(this);
        String currentLut = look.lutName == null ? "None" : look.lutName;

        for (int i = 0; i < available.size(); i++) {
            String name = available.get(i);
            boolean active = name.equalsIgnoreCase(currentLut);
            TextView pill = pill(name.toUpperCase(Locale.US), active);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(32));
            if (i > 0) lp.leftMargin = dp(6);
            lutPills.addView(pill, lp);
            pill.setOnClickListener(v -> {
                hapticClick(v);
                look.lutName = name;
                if (!name.equals("None") && look.lutIntensity <= 0.05f) {
                    look.lutIntensity = 1.0f;
                }
                populateControls();
                queuePreview();
            });
        }

        TextView importBtn = pill("+ IMPORT .CUBE", false);
        LinearLayout.LayoutParams impLp = new LinearLayout.LayoutParams(-2, dp(32));
        impLp.leftMargin = dp(6);
        lutPills.addView(importBtn, impLp);
        importBtn.setOnClickListener(v -> {
            hapticClick(v);
            launchLutPicker();
        });

        scroll.addView(lutPills);
        card.addView(scroll);

        if (look.lutName != null && !"None".equalsIgnoreCase(look.lutName)) {
            addControl("LUT Intensity", "%", 14, 0, 100, Math.round(look.lutIntensity * 100f));

            LinearLayout infoCard = new LinearLayout(this);
            infoCard.setOrientation(LinearLayout.VERTICAL);
            infoCard.setPadding(dp(12), dp(8), dp(12), dp(8));
            infoCard.setBackground(round(0xff161b1c, 10, 0xff2e3636, dp(1)));
            LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(-1, -2);
            infoLp.topMargin = dp(6);

            LutManager.Lut3D activeLut = LutManager.get(look.lutName);
            String lutDesc = getLutDescription(look.lutName, activeLut);
            TextView descView = text(lutDesc, 9, 0xffc4cbc3, false);
            infoCard.addView(descView);

            if (activeLut != null && activeLut.isCustom) {
                TextView delBtn = pill("DELETE IMPORTED LUT", false);
                LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(-2, dp(28));
                delLp.topMargin = dp(8);
                infoCard.addView(delBtn, delLp);
                delBtn.setOnClickListener(v -> {
                    hapticClick(v);
                    LutManager.deleteCustomLut(this, look.lutName);
                    look.lutName = "None";
                    Toast.makeText(this, "LUT deleted", Toast.LENGTH_SHORT).show();
                    populateControls();
                    queuePreview();
                });
            }
            controlsList.addView(infoCard, infoLp);
        }
    }

    private String getLutDescription(String name, LutManager.Lut3D lut) {
        if (LutManager.LUT_PORTRA.equalsIgnoreCase(name)) {
            return "Kodak Portra 400 · Warm golden skin tones, creamy highlight roll-off & gentle pastel shadows.";
        } else if (LutManager.LUT_FUJI.equalsIgnoreCase(name)) {
            return "Fujifilm Pro 400H · Luminous minty foliage greens, airy highlights & soft lavender shadows.";
        } else if (LutManager.LUT_TEAL_ORANGE.equalsIgnoreCase(name)) {
            return "Cine Teal & Orange · Hollywood split-tone with cool cyan shadows & golden amber highlights.";
        } else if (LutManager.LUT_ANSEL_BW.equalsIgnoreCase(name)) {
            return "Ansel Silver B&W · Zone System silver monochrome with deep carbon blacks & luminous highlights.";
        } else if (LutManager.LUT_AMBER_70S.equalsIgnoreCase(name)) {
            return "Warm Amber 70s · Vintage 1970s color slide emulation with honey highlights & faded warm shadows.";
        } else if (LutManager.LUT_BLEACH.equalsIgnoreCase(name)) {
            return "Bleach Bypass · Retained silver emulsion with high contrast, crushed blacks & metallic highlights.";
        } else if (lut != null && lut.isCustom) {
            return "Custom 3D CUBE · " + lut.size + "×" + lut.size + "×" + lut.size + " Grid · " + lut.title;
        }
        return "3D Color Lookup Table applied with trilinear interpolation.";
    }

    private void launchLutPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            startActivityForResult(intent, REQUEST_CODE_PICK_LUT);
        } catch (Exception e) {
            try {
                Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
                fallback.setType("*/*");
                startActivityForResult(fallback, REQUEST_CODE_PICK_LUT);
            } catch (Exception ex) {
                Toast.makeText(this, "File picker unavailable: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_PICK_LUT && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            String importedName = LutManager.importLut(this, uri);
            if (importedName != null) {
                look.lutName = importedName;
                look.lutIntensity = 1.0f;
                Toast.makeText(this, "Imported 3D LUT: " + importedName, Toast.LENGTH_SHORT).show();
                populateControls();
                queuePreview();
            } else {
                Toast.makeText(this, "Could not import LUT. Ensure file is a valid .cube format.", Toast.LENGTH_LONG).show();
            }
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

    private void resetControlToDefault(int type) {
        int defaultVal = LookRecipeControl.getDefaultValue(type);
        LookRecipeControl.applyToLook(look, type, defaultVal);
        for (LookRecipeControl c : controls) {
            if (c.type == type) {
                c.seek.setProgress(defaultVal - LookRecipeControl.getControlMin(type));
                c.updateLabel(defaultVal);
                if (AppSettings.getBoolean(this, AppSettings.HAPTICS, true)) {
                    c.seek.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                }
                break;
            }
        }
        queuePreview();
    }

    private void addControl(String label, String unit, int type, int min, int max, int current) {
        LookRecipeControl[] holder = new LookRecipeControl[1];
        View card = LookRecipeControl.createCard(this, label, unit, type, min, max, current,
            new LookRecipeControl.OnChangeListener() {
                @Override
                public void onValueChanged(int ctrlType, int value, boolean fromUser, boolean isTrackingTouch) {
                    LookRecipeControl.applyToLook(look, ctrlType, value);
                    if (fromUser) {
                        isScrubbing = isTrackingTouch;
                        queuePreview(isTrackingTouch ? 40 : 160);
                    }
                }

                @Override
                public void onReset(int ctrlType) {
                    resetControlToDefault(ctrlType);
                }
            }, holder);

        if (holder[0] != null) {
            controls.add(holder[0]);
        }
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.bottomMargin = dp(6);
        controlsList.addView(card, cardLp);
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
        queuePreview(isScrubbing ? 40 : 160);
    }

    private void queuePreview(long delayMs) {
        status.setText(isScrubbing ? "SCRUBBING PREVIEW" : "DEVELOPING PREVIEW");
        renderGeneration++;
        mainHandler.removeCallbacks(previewRunnable);
        if (delayMs <= 0) {
            previewRunnable.run();
        } else {
            mainHandler.postDelayed(previewRunnable, delayMs);
        }
    }

    private final Runnable previewRunnable = this::renderPreviewNow;

    private void renderPreviewNow() {
        if (frame == null) return;
        int generation = renderGeneration;
        RawDeveloper.Look snapshot = look.copy();
        RawArchive.Frame source = frame;
        int maxDim = isScrubbing ? 720 : 1600;
        worker.execute(() -> {
            Bitmap bitmap = RawDeveloper.develop(source, snapshot, maxDim);
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
                } catch (Throwable t) {
                    Log.w(TAG, "Failed to write EXIF attributes to updated JPEG", t);
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

    private void showSaveRecipeDialog() {
        String[] options = new String[3];
        for (int i = 1; i <= 3; i++) {
            String name = AppSettings.getCustomRecipeName(this, i);
            options[i - 1] = "Slot " + i + ": " + (name != null ? name : "(Empty)");
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle("SAVE AS CUSTOM RECIPE")
                .setItems(options, (dialog, which) -> {
                    int slot = which + 1;
                    promptRecipeName(slot);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void promptRecipeName(int slot) {
        android.widget.EditText input = new android.widget.EditText(this);
        String existing = AppSettings.getCustomRecipeName(this, slot);
        input.setText(existing != null ? existing : (look.brain + " Custom"));
        input.setSelectAllOnFocus(true);
        input.setTextColor(0xFFF1F2EC);
        new android.app.AlertDialog.Builder(this)
                .setTitle("RECIPE " + slot + " NAME")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) name = "Recipe " + slot;
                    AppSettings.saveCustomRecipe(this, slot, name, look.toJson());
                    Toast.makeText(this, "Saved to Recipe " + slot + " (" + name + ")", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

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
