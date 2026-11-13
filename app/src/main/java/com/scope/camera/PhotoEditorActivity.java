package com.scope.camera;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.PopupMenu;
import android.text.TextUtils;
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
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

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
    private final JobState sourceJob = new JobState();
    private final JobState previewJob = new JobState();
    private final JobState saveJob = new JobState();
    private long previewTicket;

    enum CompareMode {
        OFF,
        HOLD_PREVIEW,
        LOCKED_TOGGLE
    }

    private Bitmap rawOriginalBitmap;
    private Bitmap currentGradedBitmap;
    private CompareMode compareMode = CompareMode.OFF;
    private TextView compareButton;
    private TextView compareBadge;
    private TextView loupeBadge;
    private boolean isLoupe1to1 = false;
    private enum EditGesture { IDLE, SCRUBBING }
    private EditGesture editGesture = EditGesture.IDLE;
    private ScrollView controlsScroll;
    private final LookEditHistory history = new LookEditHistory();
    private TextView undoButton, redoButton;
    private boolean advancedLooks;
    private RawDeveloper.Look initialLook;
    private volatile int thumbnailGeneration;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        archiveKey = getIntent().getStringExtra(EXTRA_KEY);
        String uri = getIntent().getStringExtra(PhotoViewerActivity.EXTRA_URI);
        photoUri = uri == null ? null : Uri.parse(uri);
        LutManager.init(this);
        look = PhotoEditStore.load(this, archiveKey);
        initialLook = look.copy();
        if (android.os.Build.VERSION.SDK_INT >= 33) getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::requestCloseEditor);
        build();
        loadSource();
    }

    private void hapticClick(View v) {
        if (v != null && AppSettings.getBoolean(this, AppSettings.HAPTICS, true)) {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
    }

    private LinearLayout tabBar;
    private String activeTab = "Looks";

    private boolean isComparing() {
        return compareMode != CompareMode.OFF;
    }

    private void startComparing(CompareMode targetMode) {
        if (rawOriginalBitmap == null || targetMode == CompareMode.OFF) return;
        if (compareMode == targetMode) return;
        boolean alreadyComparing = isComparing();
        compareMode = targetMode;
        if (alreadyComparing) return;
        if (compareBadge != null) compareBadge.setVisibility(View.VISIBLE);
        if (compareButton != null) { compareButton.setTextColor(ACCENT); compareButton.setText("Original"); compareButton.setSelected(true); }
        preview.setImageBitmap(rawOriginalBitmap);
        if (AppSettings.getBoolean(this, AppSettings.HAPTICS, true)) {
            preview.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
    }

    private void stopComparing() {
        if (compareMode == CompareMode.OFF) return;
        compareMode = CompareMode.OFF;
        if (compareBadge != null) compareBadge.setVisibility(View.GONE);
        if (compareButton != null) { compareButton.setTextColor(0xffd8ded7); compareButton.setText("Compare"); compareButton.setSelected(false); }
        if (currentGradedBitmap != null) {
            preview.setImageBitmap(currentGradedBitmap);
        }
        if (AppSettings.getBoolean(this, AppSettings.HAPTICS, true)) {
            preview.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        }
    }

    private void applyBrainPreset(String preset) {
        recordEdit();
        look = presetLook(preset);
        refreshEditingState();
    }
    private RawDeveloper.Look presetLook(String preset) {
        RawDeveloper.Look look = this.look.copy();
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
        return look;
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
        back.setOnClickListener(v -> { hapticClick(v); requestCloseEditor(); });
        TextView title = text("Edit photo", 16, 0xfff1f2ec, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(dp(8), 0, dp(8), 0);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView more = pill("⋮", false);
        more.setTextSize(22);
        more.setContentDescription("More editing actions");
        header.addView(more, new LinearLayout.LayoutParams(dp(44), dp(44)));
        more.setOnClickListener(v -> showEditorMenu(more));
        saveButton = pill("Save copy", true);
        saveButton.setPadding(dp(14), 0, dp(14), 0);
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(-2, dp(44));
        saveLp.leftMargin = dp(8);
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
                startComparing(CompareMode.HOLD_PREVIEW);
            }
        });

        preview.setOnTouchListener((v, event) -> {
            previewGestureDetector.onTouchEvent(event);
            if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                if (compareMode == CompareMode.HOLD_PREVIEW) {
                    stopComparing();
                }
            }
            return true;
        });

        status = text(frame == null ? "Edits affect this photo only · Loading original…"
                : "Edits affect this photo only · Original preserved", 10, 0xff9ba59e, false);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(8), dp(6), dp(8), dp(2));
        LinearLayout editingTools = new LinearLayout(this);
        editingTools.setGravity(Gravity.CENTER_VERTICAL);
        editingTools.setPadding(dp(14), dp(4), dp(14), dp(8));
        undoButton = pill("Undo", false);
        redoButton = pill("Redo", false);
        editingTools.addView(undoButton, new LinearLayout.LayoutParams(dp(70), dp(44)));
        LinearLayout.LayoutParams redoLp = new LinearLayout.LayoutParams(dp(70), dp(44));
        redoLp.leftMargin = dp(6);
        editingTools.addView(redoButton, redoLp);
        editingTools.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        compareButton = pill(isComparing() ? "Original" : "Compare", isComparing());
        compareButton.setSelected(isComparing());
        compareButton.setContentDescription("Compare with original");
        editingTools.addView(compareButton, new LinearLayout.LayoutParams(dp(90), dp(44)));
        compareButton.setOnClickListener(v -> {
            hapticClick(v);
            if (isComparing()) stopComparing(); else startComparing(CompareMode.LOCKED_TOGGLE);
            compareButton.setSelected(isComparing());
            compareButton.setText(isComparing() ? "Original" : "Compare");
        });
        undoButton.setOnClickListener(v -> { look = history.undo(look); refreshEditingState(); });
        redoButton.setOnClickListener(v -> { look = history.redo(look); refreshEditingState(); });
        updateHistoryButtons();

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
            leftPane.addView(editingTools);
            splitBody.addView(leftPane, new LinearLayout.LayoutParams(0, -1, 1f));

            LinearLayout rightPane = new LinearLayout(this);
            rightPane.setOrientation(LinearLayout.VERTICAL);
            rightPane.setBackground(round(0xff0e1314, 0, 0x33454d49, dp(1)));
            rightPane.setPadding(0, dp(4), 0, 0);

            // Presets are grouped inside Looks.
            rightPane.addView(tabBar);
            refreshTabs();

            ScrollView scroll = new ScrollView(this);
            controlsScroll = scroll;
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
            page.addView(editingTools);
            page.addView(tabBar);
            refreshTabs();

            ScrollView scroll = new ScrollView(this);
            controlsScroll = scroll;
            scroll.setVerticalScrollBarEnabled(false);
            controlsList = new LinearLayout(this);
            controlsList.setOrientation(LinearLayout.VERTICAL);
            controlsList.setPadding(dp(14), dp(2), dp(14), dp(12));
            scroll.addView(controlsList);
            page.addView(scroll, new LinearLayout.LayoutParams(-1, dp(300)));
        }

        populateControls();
        if (isComparing() && rawOriginalBitmap != null) {
            preview.setImageBitmap(rawOriginalBitmap);
            compareBadge.setVisibility(View.VISIBLE);
        } else if (currentGradedBitmap != null) {
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
        String[] tabs = {"Looks", "Tone", "Colour", "Effects", "Crop"};
        for (String tab : tabs) {
            TextView pill = pill(tab, tab.equals(activeTab));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1f);
            if (!tab.equals("Looks")) lp.leftMargin = dp(4);
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
        thumbnailGeneration++;
        controls.clear();
        controlsList.removeAllViews();
        if (controlsScroll != null && !WindowMetricsHelper.computeWindowSizeInfo(this).isTwoPaneRecommended) {
            controlsScroll.setLayoutParams(new LinearLayout.LayoutParams(-1,
                    dp("Looks".equals(activeTab) && !advancedLooks ? 220 : 300)));
        }
        if ("Looks".equals(activeTab)) {
            addLooksControls();
        } else if ("Tone".equals(activeTab)) {
            addControl("Exposure", "EV", 0, -20, 20, Math.round(look.exposureEv * 10f));
            addControl("Contrast", "", 2, 50, 150, Math.round(look.contrast * 100));
            addControl("Highlights", "", 4, 0, 100, Math.round(look.highlights * 100));
            addControl("Shadows", "", 5, 0, 100, Math.round(look.shadows * 100));
            addControl("Pivot", "", 8, 0, 100, Math.round(look.pivot * 100));
        } else if ("Colour".equals(activeTab)) {
            addControl("White balance", "K", 1, 0, 44, look.whiteBalanceKelvin <= 0 ? 0 : 1 + (look.whiteBalanceKelvin - 3200) / 100);
            addControl("Warmth shift", "", 12, 0, 100, Math.round(look.warmth * 50f + 50f));
            addControl("Colour intensity", "", 3, 50, 160, Math.round(look.saturation * 100));
            addControl("Saturated colour roll-off", "", 13, 0, 100, Math.round(look.colorRollOff * 100));

        } else if ("Effects".equals(activeTab)) {
            addControl("Film grain", "", 6, 0, 100, Math.round(look.grain * 100));
            addGrainStructureChoice();
            addControl("Halation", "", 7, 0, 100, Math.round(look.halation * 100));
            addControl("Sharpening", "", 9, 0, 100, Math.round(look.sharpening * 100));
            addControl("Colour noise reduction", "", 10, 0, 100, Math.round(look.chromaNr * 100));
            addControl("Vignette", "", 11, 0, 100, Math.round(look.vignette * 100));
        } else if ("Crop".equals(activeTab)) {
            addCropControls();
        }
        updateInputAvailability();
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
                recordEdit();
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
                recordEdit();
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
                    recordEdit();
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
                recordEdit();
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
                recordEdit();
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
            recordEdit();
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
                recordEdit();
                look.frameBorder = b;
                populateControls();
                queuePreview();
            });
        }
    }

    private void resetControlToDefault(int type) {
        recordEdit();
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
                    if (fromUser && editGesture != EditGesture.SCRUBBING) recordEdit();
                    LookRecipeControl.applyToLook(look, ctrlType, value);
                    if (fromUser) {
                        editGesture = isTrackingTouch ? EditGesture.SCRUBBING : EditGesture.IDLE;
                        queuePreview(isTrackingTouch ? 40 : 160);
                    }
                }

                @Override public void onTrackingStarted() { recordEdit(); editGesture = EditGesture.SCRUBBING; }
                @Override public void onTrackingStopped() { editGesture = EditGesture.IDLE; }
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
        final long loadTicket = sourceJob.begin(JobState.Phase.LOADING);
        final RawDeveloper.Look loadLook = look.copy();
        saveButton.setEnabled(false);
        status.setText("READING SENSOR ORIGINAL");
        worker.execute(() -> {
            try {
                RawArchive.Frame loaded = RawArchive.read(this, archiveKey);
                RawDeveloper.Look neutralLook = new RawDeveloper.Look();
                neutralLook.brain = AppSettings.BRAIN_NEUTRAL_RAW;
                neutralLook.toneCurve = "Linear Response";
                neutralLook.shadowProfile = "Neutral / Open Shadows";
                neutralLook.highlightRecovery = "Adaptive blend";
                neutralLook.easyMode = true;
                neutralLook.skinProtection = false;
                neutralLook.smartVibrance = false;
                neutralLook.contrast = 1.0f;
                neutralLook.pivot = 0.50f;
                neutralLook.saturation = 0.96f;
                neutralLook.shadows = 0.0f;
                neutralLook.highlights = 0.50f;
                neutralLook.grain = 0f;
                neutralLook.halation = 0f;
                neutralLook.vignette = 0f;
                neutralLook.sharpening = 0f;
                neutralLook.chromaNr = 0.25f;
                neutralLook.colorRollOff = 0.5f;
                neutralLook.exposureEv = 0f;
                neutralLook.warmth = 0f;
                neutralLook.lutName = "None";
                neutralLook.frameBorder = "None";
                neutralLook.aspectRatio = loadLook.aspectRatio != null ? loadLook.aspectRatio : loaded.aspectRatio;
                neutralLook.flipHorizontal = loadLook.flipHorizontal;
                Bitmap bitmap = RawDeveloper.develop(loaded, loadLook, 1400);
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
                    if (!sourceJob.accepts(loadTicket) || isFinishing() || isDestroyed()) {
                        if (bitmap != null) bitmap.recycle();
                        if (finalNeutralBmp != null) finalNeutralBmp.recycle();
                        return;
                    }
                    sourceJob.move(loadTicket, JobState.Phase.READY);
                    saveButton.setEnabled(true);
                    frame = loaded;
                    rawOriginalBitmap = finalNeutralBmp;
                    setPreview(bitmap);
                    populateControls();
                    status.setText("Edits affect this photo only · Original preserved");
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (!sourceJob.move(loadTicket, JobState.Phase.FAILED) || isDestroyed()) return;
                    status.setText("COULD NOT OPEN SENSOR ORIGINAL");
                    saveButton.setEnabled(false);
                    Toast.makeText(this, "Could not develop this frame.", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void queuePreview() {
        queuePreview(editGesture == EditGesture.SCRUBBING ? 40 : 160);
    }

    private void queuePreview(long delayMs) {
        if (previewJob.isClosed() || saveJob.isBusy()) return;
        status.setText(editGesture == EditGesture.SCRUBBING ? "SCRUBBING PREVIEW" : "DEVELOPING PREVIEW");
        previewTicket = previewJob.begin(JobState.Phase.RENDERING);
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
        long generation = previewTicket;
        RawDeveloper.Look snapshot = look.copy();
        RawArchive.Frame source = frame;
        int maxDim = editGesture == EditGesture.SCRUBBING ? 720 : 1600;
        worker.execute(() -> {
            if (!previewJob.accepts(generation)) return;
            try {
                Bitmap bitmap = RawDeveloper.develop(source, snapshot, maxDim);
                mainHandler.post(() -> {
                    if (!previewJob.accepts(generation) || isFinishing() || isDestroyed()) { if (bitmap != null) bitmap.recycle(); return; }
                    previewJob.move(generation, bitmap == null ? JobState.Phase.FAILED : JobState.Phase.READY);
                    setPreview(bitmap);
                    if (!saveJob.isBusy()) status.setText(bitmap == null ? "Could not develop preview" : "Edits affect this photo only · Original preserved");
                });
            } catch (Exception error) {
                mainHandler.post(() -> { if (previewJob.move(generation, JobState.Phase.FAILED) && !isDestroyed() && !saveJob.isBusy()) status.setText("Could not develop preview. Adjust a control to retry."); });
            }
        });
    }

    private void setPreview(Bitmap bitmap) {
        if (bitmap == null) return;
        Bitmap previous = currentGradedBitmap;
        currentGradedBitmap = bitmap;
        if (!isComparing()) {
            preview.setImageBitmap(bitmap);
        }
        if (previous != null && previous != bitmap && previous != rawOriginalBitmap && !previous.isRecycled()) {
            previous.recycle();
        }
    }

    private void resetLook() {
        recordEdit();
        look = RawDeveloper.Look.fromPreferences(this);
        populateControls();
        queuePreview();
    }

    private void saveRender() {
        if (frame == null || photoUri == null) return;
        final long saveTicket = saveJob.tryBegin(JobState.Phase.SAVING);
        if (saveTicket == 0) return;
        previewJob.cancel();
        mainHandler.removeCallbacks(previewRunnable);
        saveButton.setEnabled(false);
        updateInputAvailability();
        saveButton.setText("Saving…");
        status.setText("Saving copy to Pictures/Lika Lens…");
        RawDeveloper.Look snapshot = look.copy();
        RawArchive.Frame source = frame;
        worker.execute(() -> {
            String failure = null;
            PhotoRepository.Photo output = null;
            try { output = PhotoCopyExporter.export(getApplicationContext(), archiveKey, source, snapshot); }
            catch (Exception e) { failure = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); }
            final String error = failure;
            final PhotoRepository.Photo savedPhoto = output;
            mainHandler.post(() -> {
                if (!saveJob.move(saveTicket, error == null ? JobState.Phase.READY : JobState.Phase.FAILED) || isFinishing() || isDestroyed()) return;
                if (error == null) {
                    Toast.makeText(this, "Copy saved to Pictures/Lika Lens. Original unchanged.", Toast.LENGTH_LONG).show();
                    setResult(RESULT_OK, new Intent().setData(savedPhoto.uri)
                            .putExtra(PhotoViewerActivity.EXTRA_NAME, savedPhoto.name));
                    finish();
                } else {
                    saveButton.setEnabled(true);
                    updateInputAvailability();
                    saveButton.setText("Save copy");
                    status.setText("Could not save copy");
                    new AlertDialog.Builder(this).setTitle("Save failed").setMessage(error).setPositiveButton("OK", null).show();
                }
            });
        });
    }
    @Override public void onBackPressed() { requestCloseEditor(); }
    private void requestCloseEditor() {
        if (saveJob.isBusy()) { Toast.makeText(this, "Finishing your photo copy…", Toast.LENGTH_SHORT).show(); return; }
        if (initialLook != null && !initialLook.toJson().equals(look.toJson())) {
            new AlertDialog.Builder(this).setTitle("Discard adjustments?")
                    .setMessage("Your original is unchanged. These unsaved adjustments will be lost.")
                    .setNegativeButton("Keep editing", null).setPositiveButton("Discard", (d, w) -> finish()).show();
        } else finish();
    }
    private void updateInputAvailability() {
        boolean enabled=sourceJob.phase()==JobState.Phase.READY && !saveJob.isBusy();
        setInputTreeEnabled(controlsList,enabled);setInputTreeEnabled(tabBar,enabled);
        if(undoButton!=null) undoButton.setEnabled(enabled && history.canUndo());
        if(redoButton!=null) redoButton.setEnabled(enabled && history.canRedo());
    }
    private void setInputTreeEnabled(View view,boolean enabled) {
        if(view==null) return;view.setEnabled(enabled);
        if(view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group=(android.view.ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++) setInputTreeEnabled(group.getChildAt(i),enabled);
        }
    }
    private void recordEdit() { history.record(look); updateHistoryButtons(); }
    private void updateHistoryButtons() {
        if (undoButton == null || redoButton == null) return;
        undoButton.setEnabled(history.canUndo()); redoButton.setEnabled(history.canRedo());
        undoButton.setAlpha(history.canUndo() ? 1f : .4f); redoButton.setAlpha(history.canRedo() ? 1f : .4f);
    }
    private void refreshEditingState() {
        stopComparing();
        if (compareButton != null) { compareButton.setText("Compare"); compareButton.setSelected(false); }
        populateControls(); updateHistoryButtons(); queuePreview();
    }
    private void showEditorMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        for (String title : new String[]{"Copy look", "Paste look", "Save as custom look…", "Reset adjustments…"}) menu.getMenu().add(title);
        menu.getMenu().getItem(1).setEnabled(PhotoEditStore.hasClipboard(this));
        menu.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            if (title.equals("Copy look")) {
                PhotoEditStore.copyToClipboard(this, look);
                Toast.makeText(this, "Look copied", Toast.LENGTH_SHORT).show();
            } else if (title.equals("Paste look")) {
                RawDeveloper.Look copied = PhotoEditStore.pasteFromClipboard(this);
                if (copied != null) { recordEdit(); look = copied; refreshEditingState(); }
            } else if (title.equals("Save as custom look…")) showSaveRecipeDialog();
            else new AlertDialog.Builder(this).setTitle("Reset adjustments?")
                    .setMessage("Restore default adjustments in this editor. The original photo stays unchanged.")
                    .setNegativeButton("Cancel", null).setPositiveButton("Reset", (d, w) -> resetLook()).show();
            return true;
        });
        menu.show();
    }
    private void addLooksControls() {
        HorizontalScrollView strip = new HorizontalScrollView(this);
        strip.setHorizontalScrollBarEnabled(false);
        LinearLayout looks = new LinearLayout(this);
        strip.addView(looks); controlsList.addView(strip);
        final int generation = thumbnailGeneration;
        for (String name : AppSettings.BRAIN_CHOICES) {
            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setPadding(dp(4), dp(4), dp(4), dp(4));
            boolean active = name.equals(look.brain);
            tile.setBackground(round(0xff121617, 12, active ? ACCENT : 0xff293031, dp(active ? 2 : 1)));
            ImageView thumb = new ImageView(this);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            tile.addView(thumb, new LinearLayout.LayoutParams(dp(88), dp(78)));
            TextView label = text(name.replace(" / ", " "), 10, active ? ACCENT : 0xffe8ece5, true);
            label.setMaxLines(2); label.setGravity(Gravity.CENTER);
            tile.addView(label, new LinearLayout.LayoutParams(dp(88), dp(40)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2); lp.rightMargin = dp(8);
            looks.addView(tile, lp);
            tile.setContentDescription(name + (active ? ", selected" : ""));
            tile.setOnClickListener(v -> { hapticClick(v); applyBrainPreset(name); });
            if (frame != null) {
                RawArchive.Frame source = frame;
                RawDeveloper.Look recipe = presetLook(name);
                worker.execute(() -> {
                    if (generation != thumbnailGeneration) return;
                    Bitmap bitmap;
                    try { bitmap = RawDeveloper.develop(source, recipe, 220); }
                    catch (Exception e) { Log.w(TAG, "Could not render look thumbnail", e); return; }
                    mainHandler.post(() -> {
                        if (generation != thumbnailGeneration || isFinishing()) { if (bitmap != null) bitmap.recycle(); return; }
                        thumb.setImageBitmap(bitmap);
                    });
                });
            }
        }
        for (int slot = 1; slot <= 3; slot++) {
            String name = AppSettings.getCustomRecipeName(this, slot);
            final int selectedSlot = slot;
            if (name == null) continue;
            TextView custom = pill(name, false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(44)); lp.topMargin = dp(8);
            controlsList.addView(custom, lp);
            custom.setOnClickListener(v -> {
                RawDeveloper.Look customLook = RawDeveloper.Look.fromJson(AppSettings.getCustomRecipeData(this, selectedSlot));
                if (customLook != null) { recordEdit(); look = customLook; refreshEditingState(); }
            });
        }
        TextView advanced = pill(advancedLooks ? "Hide advanced colour / LUTs" : "Advanced colour / LUTs", false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(44)); lp.topMargin = dp(12);
        controlsList.addView(advanced, lp);
        advanced.setOnClickListener(v -> { advancedLooks = !advancedLooks; populateControls(); });
        if (advancedLooks) addLutControls();
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
        sourceJob.close(); previewJob.close(); saveJob.close();
        thumbnailGeneration++;
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
