package com.scope.camera;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.hardware.camera2.DngCreator;
import android.media.Image;
import android.graphics.ImageFormat;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.util.Size;
import android.util.Range;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Lika Lens: Camera2 framing preview plus RAW_SENSOR/DNG capture and app-side development. */
public class MainActivity extends Activity implements TextureView.SurfaceTextureListener {
    private static final int CAMERA_PERMISSION = 41;
    private static final int BG = 0xff0b0d0e;
    private static final int ACCENT = 0xffd7b574;
    private static final int MUTED = 0xffa0aaa4;

    private TextureView textureView;
    private TextView cameraState;
    private TextView brainButton;
    private TextView flashButton;
    private TextView aspectButton;
    private TextView timerButton;
    private TextView adjustButton;
    private TextView captureButton;
    private TextView messageView;
    private TextView outputModeText;
    private ImageView galleryButton;
    private TextView shutterValueText;
    private TextView isoValueText;
    private TextView whiteBalanceValueText;
    private final List<TextView> lensButtons = new ArrayList<>();
    private View gridOverlay;
    private FrameLayout quickAdjustPanel;
    private LinearLayout quickControlsList;
    private FrameLayout root;
    private CameraManager cameraManager;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private CaptureRequest.Builder previewRequest;
    private ImageReader rawReader;
    private ImageReader jpegReader;
    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private final ExecutorService processor = Executors.newSingleThreadExecutor();
    private final Object captureLock = new Object();
    private Image pendingImage;
    private TotalCaptureResult pendingResult;
    private volatile boolean processingCapture;
    private volatile boolean captureInFlight;
    private ValueAnimator zoomAnimator;
    private boolean openingCamera;
    private boolean rawSupported;
    private boolean manualSupported;
    private boolean flashSupported;
    private boolean useRawForThisCamera;
    private boolean started;
    private String cameraId;
    private String mainCameraId;
    private CameraCharacteristics characteristics;
    private Size rawSize;
    private Size previewSize;
    private float primaryFocalLength = 1f;
    private float physicalFocalFactor = 1f;
    private float requestedZoom = 1f;
    private int displayRotation;
    private long toastUntil;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        AppSettings.migrateBranding(this);
        Window window = getWindow();
        window.setStatusBarColor(0xff0b0d0e);
        window.setNavigationBarColor(0xff0b0d0e);
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        buildCameraUi();
        cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
        startBackgroundThread();
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        updateBrainButton();
        updateQuickButtons();
        updateReadouts();
        updateGalleryButton();
        if (gridOverlay != null) gridOverlay.setVisibility(AppSettings.getBoolean(this, AppSettings.GRID, false) ? View.VISIBLE : View.GONE);
        if (textureView != null && textureView.isAvailable()) openCameraIfReady();
    }

    @Override protected void onPause() {
        closeCamera();
        super.onPause();
    }

    @Override protected void onDestroy() {
        closeCamera();
        if (cameraThread != null) {
            cameraThread.quitSafely();
            try { cameraThread.join(500); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            cameraThread = null;
            cameraHandler = null;
        }
        processor.shutdown();
        super.onDestroy();
    }

    private void buildCameraUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        root.setFitsSystemWindows(true);
        textureView = new TextureView(this);
        textureView.setClipToOutline(true);
        textureView.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(14));
            }
        });
        textureView.setSurfaceTextureListener(this);
        root.addView(textureView, new FrameLayout.LayoutParams(-1, -1));
        gridOverlay = new CompositionGrid(this);
        gridOverlay.setVisibility(AppSettings.getBoolean(this, AppSettings.GRID, false) ? View.VISIBLE : View.GONE);
        root.addView(gridOverlay, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setPadding(dp(12), dp(14), dp(12), dp(14));
        overlay.setBackground(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x98080b0c, 0x00080b0c, 0x10080b0c, 0xd8080b0c}));
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout header = row();
        flashButton = pill("ϟ  OFF", false);
        header.addView(flashButton, new LinearLayout.LayoutParams(dp(60), dp(38)));
        flashButton.setOnClickListener(v -> cycleFlash());
        aspectButton = pill("4:3", false);
        LinearLayout.LayoutParams aspectLp = new LinearLayout.LayoutParams(dp(50), dp(38));
        aspectLp.leftMargin = dp(6);
        header.addView(aspectButton, aspectLp);
        aspectButton.setOnClickListener(v -> chooseAspectRatio());
        View topSpacer = new View(this);
        header.addView(topSpacer, new LinearLayout.LayoutParams(0, 1, 1));
        brainButton = pill("FILM  ⌄", true);
        LinearLayout.LayoutParams brainLp = new LinearLayout.LayoutParams(dp(64), dp(38));
        brainLp.leftMargin = dp(6);
        brainButton.setOnClickListener(v -> chooseBrain());
        header.addView(brainButton, brainLp);
        timerButton = pill("◷  OFF", false);
        LinearLayout.LayoutParams timerLp = new LinearLayout.LayoutParams(dp(58), dp(38));
        timerLp.leftMargin = dp(6);
        header.addView(timerButton, timerLp);
        timerButton.setOnClickListener(v -> cycleTimer());
        TextView settings = pill("⚙", false);
        LinearLayout.LayoutParams settingsLp = new LinearLayout.LayoutParams(dp(36), dp(38));
        settingsLp.leftMargin = dp(6);
        header.addView(settings, settingsLp);
        settings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        overlay.addView(header);

        LinearLayout spacer = new LinearLayout(this);
        overlay.addView(spacer, new LinearLayout.LayoutParams(-1, 0, 1));

        cameraState = label("CHECKING CAMERA SUPPORT…", 8, 0xffe2e7de, true);
        cameraState.setLetterSpacing(.09f);
        cameraState.setGravity(Gravity.CENTER);
        overlay.addView(cameraState);

        LinearLayout telemetry = row();
        telemetry.setPadding(dp(3), dp(9), dp(3), dp(11));
        telemetry.addView(readoutView("SHUTTER", "AUTO"), new LinearLayout.LayoutParams(0, -2, 1));
        telemetry.addView(readoutView("ISO", "AUTO"), new LinearLayout.LayoutParams(0, -2, 1));
        telemetry.addView(readoutView("WB", "5600 K"), new LinearLayout.LayoutParams(0, -2, 1));
        overlay.addView(telemetry);

        LinearLayout lensRow = row();
        lensRow.setGravity(Gravity.CENTER);
        float[] zooms = new float[]{.5f, 1f, 2f, 5f};
        String[] names = new String[]{".5×", "1×", "2×", "5×"};
        for (int i = 0; i < zooms.length; i++) {
            final float zoom = zooms[i];
            TextView lens = pill(names[i], Math.abs(zoom - 1f) < .01f);
            lens.setTag(zoom);
            lensButtons.add(lens);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(48), dp(32));
            lp.setMargins(dp(4), 0, dp(4), 0);
            lensRow.addView(lens, lp);
            lens.setOnClickListener(v -> chooseLens(zoom));
        }
        overlay.addView(lensRow);

        LinearLayout lookRow = row();
        TextView viewfinderNote = label("RAW DEVELOPED AFTER CAPTURE", 7, 0xffc9d0c8, false);
        viewfinderNote.setLetterSpacing(.055f);
        LinearLayout.LayoutParams noteTextLp = new LinearLayout.LayoutParams(0, -2, 1f);
        lookRow.addView(viewfinderNote, noteTextLp);
        adjustButton = pill("ADJUST", false);
        adjustButton.setContentDescription("Open quick look controls");
        adjustButton.setOnClickListener(v -> toggleQuickAdjustPanel());
        lookRow.addView(adjustButton, new LinearLayout.LayoutParams(dp(72), dp(30)));
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(-1, -2);
        noteLp.topMargin = dp(13);
        lookRow.setPadding(0, 0, 0, dp(3));
        overlay.addView(lookRow, noteLp);

        LinearLayout shootRow = row();
        shootRow.setGravity(Gravity.CENTER_VERTICAL);
        shootRow.setPadding(0, dp(8), 0, 0);
        galleryButton = new ImageView(this);
        galleryButton.setScaleType(ImageView.ScaleType.CENTER_CROP);
        galleryButton.setBackground(round(0xff202626, 12, 0x777d8580, dp(1)));
        galleryButton.setClipToOutline(true);
        galleryButton.setContentDescription("Open the latest frame. Long press for the library.");
        LinearLayout.LayoutParams sideLp = new LinearLayout.LayoutParams(dp(48), dp(48));
        shootRow.addView(galleryButton, sideLp);
        galleryButton.setImageResource(R.drawable.ic_lika_lens);
        galleryButton.setOnClickListener(v -> openLatestFrame());
        galleryButton.setOnLongClickListener(v -> { openLibrary(); return true; });

        View shootSpacer = new View(this);
        shootRow.addView(shootSpacer, new LinearLayout.LayoutParams(0, 1, 1));
        captureButton = label("", 12, BG, false);
        captureButton.setGravity(Gravity.CENTER);
        captureButton.setBackground(round(0xfffaf9f5, 100, 0xfffaf9f5, dp(2)));
        FrameLayout shutterFrame = new FrameLayout(this);
        shutterFrame.setBackground(round(0x00202020, 100, 0xfffaf9f5, dp(2)));
        shutterFrame.addView(captureButton, new FrameLayout.LayoutParams(dp(54), dp(54), Gravity.CENTER));
        shootRow.addView(shutterFrame, new LinearLayout.LayoutParams(dp(68), dp(68)));
        shutterFrame.setOnClickListener(v -> capturePhoto());
        captureButton.setOnClickListener(v -> capturePhoto());
        shootRow.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        TextView flip = pill("⟳", false);
        shootRow.addView(flip, new LinearLayout.LayoutParams(dp(48), dp(48)));
        flip.setOnClickListener(v -> toggleCameraFacing());
        overlay.addView(shootRow);

        outputModeText = label("WAITING FOR CAMERA SUPPORT", 7, 0xffd9dfd6, true);
        outputModeText.setGravity(Gravity.CENTER);
        outputModeText.setLetterSpacing(.12f);
        LinearLayout.LayoutParams formatLp = new LinearLayout.LayoutParams(-1, -2);
        formatLp.topMargin = dp(7);
        overlay.addView(outputModeText, formatLp);

        buildQuickAdjustPanel();

        messageView = label("", 10, BG, true);
        messageView.setGravity(Gravity.CENTER);
        messageView.setPadding(dp(16), dp(10), dp(16), dp(10));
        messageView.setBackground(round(0xffe8efd9, 24, 0x00ffffff, 0));
        messageView.setVisibility(View.GONE);
        FrameLayout.LayoutParams messageLp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        messageLp.bottomMargin = dp(24);
        root.addView(messageView, messageLp);
        setContentView(root);
    }

    private void buildQuickAdjustPanel() {
        quickAdjustPanel = new FrameLayout(this);
        quickAdjustPanel.setBackground(round(0xf0111516, 18, 0xff48504b, dp(1)));
        quickAdjustPanel.setClickable(true);
        quickAdjustPanel.setElevation(dp(12));

        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(14), dp(10), dp(14), dp(8));
        quickAdjustPanel.addView(sheet, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout heading = row();
        TextView title = label("LOOK CONTROLS", 9, 0xfff1f2ec, true);
        title.setLetterSpacing(.13f);
        heading.addView(title, new LinearLayout.LayoutParams(0, dp(34), 1f));
        TextView reset = pill("RESET", false);
        heading.addView(reset, new LinearLayout.LayoutParams(dp(52), dp(30)));
        reset.setOnClickListener(v -> resetQuickLook());
        TextView done = pill("DONE", true);
        LinearLayout.LayoutParams doneLp = new LinearLayout.LayoutParams(dp(52), dp(30));
        doneLp.leftMargin = dp(6);
        heading.addView(done, doneLp);
        done.setOnClickListener(v -> closeQuickAdjustPanel());
        sheet.addView(heading);
        TextView note = label("FOR THE NEXT PHOTO  ·  RAW DEVELOPED AFTER CAPTURE", 7, 0xff9aa49e, false);
        note.setLetterSpacing(.04f);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(-1, -2);
        noteLp.bottomMargin = dp(5);
        sheet.addView(note, noteLp);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        quickControlsList = new LinearLayout(this);
        quickControlsList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(quickControlsList);
        sheet.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        populateQuickControls();

        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(-1, dp(340), Gravity.BOTTOM);
        panelLp.leftMargin = dp(8);
        panelLp.rightMargin = dp(8);
        panelLp.bottomMargin = dp(118);
        root.addView(quickAdjustPanel, panelLp);
        quickAdjustPanel.setVisibility(View.GONE);
    }

    private void populateQuickControls() {
        if (quickControlsList == null) return;
        quickControlsList.removeAllViews();
        addQuickSlider("Exposure", AppSettings.EXPOSURE, -20, 20, AppSettings.DEFAULT_EXPOSURE);
        addQuickSlider("Warmth shift", AppSettings.WARMTH, 0, 100, AppSettings.DEFAULT_WARMTH);
        addQuickSlider("Shadow depth", AppSettings.SHADOWS, 0, 100, AppSettings.DEFAULT_SHADOWS);
        addQuickSlider("Contrast", AppSettings.CONTRAST, 50, 150, AppSettings.DEFAULT_CONTRAST);
        addQuickSlider("Colour", AppSettings.SATURATION, 0, 160, AppSettings.DEFAULT_SATURATION);
        addQuickSlider("Saturated colour roll-off", AppSettings.COLOR_ROLLOFF, 0, 100, AppSettings.DEFAULT_COLOR_ROLLOFF);
        addQuickSlider("Highlight roll-off", AppSettings.HIGHLIGHTS, 0, 100, AppSettings.DEFAULT_HIGHLIGHTS);
        addQuickSlider("Grain", AppSettings.GRAIN, 0, 100, AppSettings.DEFAULT_GRAIN);
        addQuickSlider("Halation", AppSettings.HALATION, 0, 100, AppSettings.DEFAULT_HALATION);
        addQuickSlider("Sharpening", AppSettings.SHARPEN, 0, 100, AppSettings.DEFAULT_SHARPEN);
    }

    private void addQuickSlider(String title, String key, int min, int max, int fallback) {
        LinearLayout control = new LinearLayout(this);
        control.setOrientation(LinearLayout.VERTICAL);
        control.setPadding(dp(9), dp(4), dp(9), dp(2));
        control.setBackground(round(0xff171c1d, 10, 0xff293132, dp(1)));
        LinearLayout.LayoutParams controlLp = new LinearLayout.LayoutParams(-1, -2);
        controlLp.bottomMargin = dp(5);
        quickControlsList.addView(control, controlLp);

        LinearLayout labelRow = row();
        TextView name = label(title, 8, 0xffe6eae4, true);
        labelRow.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView value = label("", 8, ACCENT, true);
        value.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        labelRow.addView(value);
        control.addView(labelRow);

        SeekBar seek = new SeekBar(this);
        seek.setMax(max - min);
        int selected = Math.max(min, Math.min(max, AppSettings.getInt(this, key, fallback)));
        seek.setProgress(selected - min);
        seek.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        seek.setThumbTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        control.addView(seek, new LinearLayout.LayoutParams(-1, dp(28)));
        updateQuickValue(value, key, selected);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int next = min + progress;
                updateQuickValue(value, key, next);
                if (fromUser) AppSettings.prefs(MainActivity.this).edit().putInt(key, next).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
    }

    private void updateQuickValue(TextView value, String key, int number) {
        if (AppSettings.EXPOSURE.equals(key)) value.setText(String.format(Locale.US, "%+.1f EV", number / 10f));
        else if (AppSettings.WARMTH.equals(key)) value.setText(number == 50 ? "0" : number > 50 ? "WARM +" + (number - 50) : "COOL " + (50 - number));
        else value.setText(number + "%");
    }

    private void toggleQuickAdjustPanel() {
        if (quickAdjustPanel == null) return;
        if (quickAdjustPanel.getVisibility() == View.VISIBLE) closeQuickAdjustPanel();
        else {
            populateQuickControls();
            quickAdjustPanel.setAlpha(0f);
            quickAdjustPanel.setVisibility(View.VISIBLE);
            quickAdjustPanel.animate().alpha(1f).setDuration(150).start();
            adjustButton.setText("CLOSE ×");
        }
    }

    private void closeQuickAdjustPanel() {
        if (quickAdjustPanel != null) quickAdjustPanel.setVisibility(View.GONE);
        if (adjustButton != null) adjustButton.setText("ADJUST");
    }

    private void resetQuickLook() {
        AppSettings.prefs(this).edit()
                .putInt(AppSettings.EXPOSURE, AppSettings.DEFAULT_EXPOSURE)
                .putInt(AppSettings.WARMTH, AppSettings.DEFAULT_WARMTH)
                .putInt(AppSettings.CONTRAST, AppSettings.DEFAULT_CONTRAST)
                .putInt(AppSettings.PIVOT, AppSettings.DEFAULT_PIVOT)
                .putInt(AppSettings.SATURATION, AppSettings.DEFAULT_SATURATION)
                .putInt(AppSettings.COLOR_ROLLOFF, AppSettings.DEFAULT_COLOR_ROLLOFF)
                .putInt(AppSettings.HIGHLIGHTS, AppSettings.DEFAULT_HIGHLIGHTS)
                .putInt(AppSettings.SHADOWS, AppSettings.DEFAULT_SHADOWS)
                .putInt(AppSettings.GRAIN, AppSettings.DEFAULT_GRAIN)
                .putInt(AppSettings.HALATION, AppSettings.DEFAULT_HALATION)
                .putInt(AppSettings.SHARPEN, AppSettings.DEFAULT_SHARPEN)
                .putInt(AppSettings.COLOR_NR, AppSettings.DEFAULT_COLOR_NR)
                .putInt(AppSettings.VIGNETTE, AppSettings.DEFAULT_VIGNETTE)
                .apply();
        populateQuickControls();
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private View readoutView(String name, String value) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        TextView top = label(name, 7, 0xff9da8a0, false);
        top.setLetterSpacing(.12f);
        TextView bottom = label(value, 11, 0xfff0f1ea, true);
        bottom.setPadding(0, dp(4), 0, 0);
        if ("SHUTTER".equals(name)) shutterValueText = bottom;
        if ("ISO".equals(name)) isoValueText = bottom;
        if ("WB".equals(name) || "WHITE BALANCE".equals(name)) whiteBalanceValueText = bottom;
        box.addView(top);
        box.addView(bottom);
        return box;
    }

    private TextView label(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private TextView pill(String value, boolean active) {
        TextView view = label(value, 9, active ? ACCENT : 0xffe9ede6, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(8), 0, dp(8), 0);
        view.setBackground(round(active ? 0xe21c1d1b : 0xd8191e1e, 22, active ? ACCENT : 0x556e7872, dp(1)));
        view.setClickable(true);
        return view;
    }

    private GradientDrawable round(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        if (strokeWidth > 0) d.setStroke(strokeWidth, strokeColor);
        return d;
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void updateQuickButtons() {
        if (aspectButton != null) aspectButton.setText(AppSettings.get(this, AppSettings.ASPECT_RATIO, "4:3"));
        if (timerButton != null) {
            String timer = AppSettings.get(this, AppSettings.TIMER, "Off");
            timerButton.setText("◷  " + (timer.startsWith("3") ? "3S" : timer.startsWith("10") ? "10S" : "OFF"));
        }
        if (flashButton != null) {
            String flash = AppSettings.get(this, AppSettings.FLASH, "Off");
            flashButton.setText("ϟ  " + (!flashSupported ? "OFF" : flash.toUpperCase(Locale.US)));
            flashButton.setTextColor("Off".equals(flash) || !flashSupported ? 0xffe9ede6 : ACCENT);
            flashButton.setAlpha(flashSupported ? 1f : .72f);
        }
    }

    private void cycleTimer() {
        String current = AppSettings.get(this, AppSettings.TIMER, "Off");
        String next = current.startsWith("Off") ? "3 seconds" : current.startsWith("3") ? "10 seconds" : "Off";
        AppSettings.prefs(this).edit().putString(AppSettings.TIMER, next).apply();
        updateQuickButtons();
    }

    private void cycleFlash() {
        if (!flashSupported) {
            showMessage("This camera does not report a flash unit.");
            return;
        }
        String current = AppSettings.get(this, AppSettings.FLASH, "Off");
        String next = current.startsWith("Off") ? "Torch" : current.startsWith("Torch") ? "Auto" : "Off";
        AppSettings.prefs(this).edit().putString(AppSettings.FLASH, next).apply();
        updateQuickButtons();
        refreshPreviewRequest();
    }

    private void chooseAspectRatio() {
        String[] ratios = {"4:3", "3:2", "16:9", "1:1"};
        String current = AppSettings.get(this, AppSettings.ASPECT_RATIO, "4:3");
        int selected = Math.max(0, Arrays.asList(ratios).indexOf(current));
        new AlertDialog.Builder(this).setTitle("Frame shape")
                .setSingleChoiceItems(ratios, selected, (dialog, which) -> {
                    AppSettings.prefs(this).edit().putString(AppSettings.ASPECT_RATIO, ratios[which]).apply();
                    updateQuickButtons();
                    dialog.dismiss();
                }).setNegativeButton("Cancel", null).show();
    }

    private void startBackgroundThread() {
        cameraThread = new HandlerThread("LikaLens-Camera2");
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());
    }

    private void openCameraIfReady() {
        if (started || openingCamera || textureView == null || !textureView.isAvailable()) return;
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return;
        if (cameraManager == null) cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
        try {
            if (cameraId == null) chooseMainCamera();
            if (cameraId == null) {
                cameraState.setText("NO CAMERA FOUND");
                return;
            }
            characteristics = cameraManager.getCameraCharacteristics(cameraId);
            Boolean flashValue = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
            flashSupported = flashValue != null && flashValue;
            updateQuickButtons();
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) throw new CameraAccessException(CameraAccessException.CAMERA_ERROR);
            int[] capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
            Size[] rawOutputs = map.getOutputSizes(ImageFormat.RAW_SENSOR);
            rawSupported = capabilities != null && Arrays.stream(capabilities).anyMatch(v -> v == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)
                    && rawOutputs != null && rawOutputs.length > 0;
            manualSupported = capabilities != null && Arrays.stream(capabilities).anyMatch(v -> v == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR);
            AppSettings.prefs(this).edit().putBoolean(AppSettings.MANUAL_SUPPORTED, manualSupported).apply();
            updateReadouts();
            useRawForThisCamera = rawSupported;
            AppSettings.prefs(this).edit().putBoolean(AppSettings.RAW_SUPPORTED, rawSupported).apply();
            rawSize = rawSupported ? chooseRawSize(map.getOutputSizes(ImageFormat.RAW_SENSOR)) : null;
            previewSize = choosePreviewSize(map.getOutputSizes(SurfaceTexture.class));
            if (previewSize == null) previewSize = new Size(1280, 720);
            prepareReader(map);
            openingCamera = true;
            cameraManager.openCamera(cameraId, cameraStateCallback, cameraHandler);
        } catch (SecurityException | CameraAccessException e) {
            openingCamera = false;
            cameraState.setText("CAMERA UNAVAILABLE");
            showMessage("Could not open this camera.");
        }
    }

    private void chooseMainCamera() throws CameraAccessException {
        String best = null;
        long bestArea = -1;
        boolean foundRaw = false;
        float focal = 1f;
        for (String id : cameraManager.getCameraIdList()) {
            CameraCharacteristics c = cameraManager.getCameraCharacteristics(id);
            Integer facing = c.get(CameraCharacteristics.LENS_FACING);
            if (facing == null || facing != CameraCharacteristics.LENS_FACING_BACK) continue;
            StreamConfigurationMap map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) continue;
            int[] caps = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
            Size[] rawSizes = map.getOutputSizes(ImageFormat.RAW_SENSOR);
            boolean raw = caps != null && Arrays.stream(caps).anyMatch(v -> v == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)
                    && rawSizes != null && rawSizes.length > 0;
            Size[] sizes = raw ? map.getOutputSizes(ImageFormat.RAW_SENSOR) : map.getOutputSizes(ImageFormat.JPEG);
            long area = sizes == null ? 0 : Arrays.stream(sizes).mapToLong(s -> (long) s.getWidth() * s.getHeight()).max().orElse(0);
            if ((raw && !foundRaw) || (raw == foundRaw && area > bestArea)) {
                best = id;
                bestArea = area;
                foundRaw = raw;
                float[] lenses = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                if (lenses != null && lenses.length > 0) focal = lenses[0];
            }
        }
        cameraId = best;
        mainCameraId = best;
        primaryFocalLength = focal;
        physicalFocalFactor = 1f;
    }

    private void prepareReader(StreamConfigurationMap map) {
        closeReader(rawReader); rawReader = null;
        closeReader(jpegReader); jpegReader = null;
        if (useRawForThisCamera && rawSize != null) {
            rawReader = ImageReader.newInstance(rawSize.getWidth(), rawSize.getHeight(), ImageFormat.RAW_SENSOR, 2);
            rawReader.setOnImageAvailableListener(this::onImageAvailable, cameraHandler);
            cameraState.setText("RAW SENSOR READY  ·  " + Math.round(rawSize.getWidth() * rawSize.getHeight() / 1000000f) + " MP");
            outputModeText.setText("RAW SENSOR  ·  DNG ORIGINAL + LIKA LENS JPEG");
        } else {
            Size jpegSize = chooseJpegSize(map.getOutputSizes(ImageFormat.JPEG));
            if (jpegSize == null) jpegSize = new Size(1920, 1080);
            jpegReader = ImageReader.newInstance(jpegSize.getWidth(), jpegSize.getHeight(), ImageFormat.JPEG, 2);
            jpegReader.setOnImageAvailableListener(this::onImageAvailable, cameraHandler);
            cameraState.setText("RAW NOT AVAILABLE  ·  JPEG LOOK FALLBACK");
            outputModeText.setText("JPEG FALLBACK  ·  SENSOR RAW NOT AVAILABLE");
        }
    }

    private Size chooseRawSize(Size[] sizes) {
        if (sizes == null || sizes.length == 0) return null;
        String choice = AppSettings.get(this, AppSettings.RAW_SIZE, "Maximum sensor resolution");
        List<Size> candidates = new ArrayList<>(Arrays.asList(sizes));
        candidates.sort(Comparator.comparingLong(s -> (long) s.getWidth() * s.getHeight()));
        if (choice.startsWith("12 MP")) {
            Size selected = null;
            for (Size s : candidates) if ((long) s.getWidth() * s.getHeight() <= 13_000_000L) selected = s;
            if (selected != null) return selected;
        }
        return candidates.get(candidates.size() - 1);
    }

    private Size chooseJpegSize(Size[] sizes) {
        if (sizes == null || sizes.length == 0) return null;
        return Arrays.stream(sizes).filter(s -> (long) s.getWidth() * s.getHeight() <= 16_000_000L)
                .max(Comparator.comparingLong(s -> (long) s.getWidth() * s.getHeight()))
                .orElseGet(() -> Arrays.stream(sizes).min(Comparator.comparingLong(s -> Math.abs(s.getWidth() - 1920) + Math.abs(s.getHeight() - 1080))).orElse(sizes[0]));
    }

    private Size choosePreviewSize(Size[] sizes) {
        if (sizes == null || sizes.length == 0) return null;
        return Arrays.stream(sizes)
                .filter(s -> s.getWidth() <= 1920 && s.getHeight() <= 1080)
                .min(Comparator.comparingDouble(s -> Math.abs((s.getWidth() / (double) s.getHeight()) - 16.0 / 9.0)
                        + Math.abs(s.getWidth() - 1280) / 4000.0))
                .orElse(sizes[0]);
    }

    private final CameraDevice.StateCallback cameraStateCallback = new CameraDevice.StateCallback() {
        @Override public void onOpened(CameraDevice camera) {
            openingCamera = false;
            cameraDevice = camera;
            started = true;
            createPreviewSession();
        }
        @Override public void onDisconnected(CameraDevice camera) {
            openingCamera = false;
            camera.close();
            if (cameraDevice == camera) cameraDevice = null;
            started = false;
        }
        @Override public void onError(CameraDevice camera, int error) {
            openingCamera = false;
            camera.close();
            if (cameraDevice == camera) cameraDevice = null;
            started = false;
            runOnUiThread(() -> cameraState.setText("CAMERA ERROR · " + error));
        }
    };

    private void createPreviewSession() {
        if (cameraDevice == null || textureView == null || !textureView.isAvailable()) return;
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            Surface previewSurface = new Surface(texture);
            List<Surface> outputs = new ArrayList<>();
            outputs.add(previewSurface);
            if (useRawForThisCamera && rawReader != null) outputs.add(rawReader.getSurface());
            else if (jpegReader != null) outputs.add(jpegReader.getSurface());
            cameraDevice.createCaptureSession(outputs, new CameraCaptureSession.StateCallback() {
                @Override public void onConfigured(CameraCaptureSession session) {
                    if (cameraDevice == null) { session.close(); return; }
                    captureSession = session;
                    try {
                        previewRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        previewRequest.addTarget(previewSurface);
                        previewRequest.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
                        setExposure(previewRequest);
                        setFlash(previewRequest, true);
                        setFocusMode(previewRequest);
                        applyZoom(previewRequest);
                        captureSession.setRepeatingRequest(previewRequest.build(), null, cameraHandler);
                        configureTransform(textureView.getWidth(), textureView.getHeight());
                        textureView.animate().alpha(1f).setDuration(160).start();
                    } catch (CameraAccessException e) {
                        runOnUiThread(() -> showMessage("Preview session failed."));
                    }
                }
                @Override public void onConfigureFailed(CameraCaptureSession session) {
                    session.close();
                    if (useRawForThisCamera) {
                        useRawForThisCamera = false;
                        rawSupported = false;
                        AppSettings.prefs(MainActivity.this).edit().putBoolean(AppSettings.RAW_SUPPORTED, false).apply();
                        runOnUiThread(() -> {
                            cameraState.setText("RAW STREAM COMBINATION UNSUPPORTED · USING JPEG");
                            recreateJpegSession();
                        });
                    } else runOnUiThread(() -> showMessage("Camera preview could not be configured."));
                }
            }, cameraHandler);
        } catch (CameraAccessException | IllegalStateException e) {
            showMessage("Could not start camera preview.");
        }
    }

    private void recreateJpegSession() {
        if (cameraDevice == null) return;
        try {
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            prepareReader(map);
            closeSessionOnly();
            createPreviewSession();
        } catch (RuntimeException e) {
            showMessage("JPEG fallback could not start on this camera.");
        }
    }

    private void closeSessionOnly() {
        if (captureSession != null) { captureSession.close(); captureSession = null; }
    }

    private void onImageAvailable(ImageReader reader) {
        Image image = reader.acquireNextImage();
        if (image == null) return;
        synchronized (captureLock) {
            if (pendingImage != null) pendingImage.close();
            pendingImage = image;
            processPairIfReady();
        }
    }

    private void processPairIfReady() {
        if (pendingImage == null || processingCapture || (useRawForThisCamera && pendingResult == null)) return;
        Image image = pendingImage;
        TotalCaptureResult result = pendingResult;
        pendingImage = null;
        pendingResult = null;
        processingCapture = true;
        captureInFlight = false;
        CameraCharacteristics capturedCharacteristics = characteristics;
        int capturedOrientation = jpegOrientation();
        String capturedAspect = AppSettings.get(this, AppSettings.ASPECT_RATIO, "4:3");
        RawDeveloper.Look captureLook = RawDeveloper.Look.fromPreferences(this);
        processor.execute(() -> {
            boolean raw = image.getFormat() == ImageFormat.RAW_SENSOR;
            String archiveKey = null;
            boolean keepEditableSource = raw
                    && AppSettings.getBoolean(this, AppSettings.KEEP_RAW, true)
                    && !"Rendered JPEG only".equals(AppSettings.get(this, AppSettings.CAPTURE_FORMAT, "RAW + rendered JPEG"));
            try {
                String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date());
                String format = AppSettings.get(this, AppSettings.CAPTURE_FORMAT, "RAW + rendered JPEG");
                boolean saveDng = raw && (AppSettings.getBoolean(this, AppSettings.KEEP_RAW, true) || "DNG only".equals(format))
                        && !"Rendered JPEG only".equals(format);
                String prefix = AppSettings.get(this, AppSettings.FILE_PREFIX, "LIKA");
                if (prefix.startsWith("Custom")) prefix = "LIKA";
                archiveKey = prefix + "_" + stamp;
                if (saveDng) writeDng(image, result, capturedCharacteristics, archiveKey + ".dng");
                Bitmap developed;
                if (raw) {
                    RawArchive.Frame frame = RawArchive.write(this, archiveKey, image, capturedCharacteristics,
                            result, capturedOrientation, capturedAspect);
                    developed = RawDeveloper.develop(frame, captureLook, 0);
                } else {
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);
                    developed = RawDeveloper.developJpeg(bytes, this);
                }
                if (developed == null) throw new IllegalStateException("Renderer returned no image");
                if (!raw) {
                    developed = rotateForDisplay(developed);
                    developed = cropToAspect(developed);
                }
                if (!raw || !"DNG only".equals(format)) writeJpeg(developed, prefix + "_" + stamp + ".jpg");
                if (raw && keepEditableSource) PhotoEditStore.save(this, archiveKey, captureLook);
                developed.recycle();
                String resultMessage = !raw ? "JPEG fallback captured and rendered"
                        : "DNG only".equals(format) ? "DNG original saved"
                        : saveDng ? "DNG original + Lika Lens render saved" : "Lika Lens RAW render saved";
                runOnUiThread(() -> {
                    showMessage(resultMessage);
                    captureButton.setAlpha(1f);
                    updateGalleryButton();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    showMessage("Could not develop frame: " + e.getClass().getSimpleName());
                    captureButton.setAlpha(1f);
                });
            } finally {
                image.close();
                if (raw && !keepEditableSource && archiveKey != null) RawArchive.delete(this, archiveKey);
                synchronized (captureLock) { processingCapture = false; }
            }
        });
    }

    private void capturePhoto() {
        if (captureSession == null || cameraDevice == null || processingCapture || captureInFlight) {
            showMessage("Camera is warming up.");
            return;
        }
        captureInFlight = true;
        if (AppSettings.getBoolean(this, AppSettings.HAPTICS, true)) captureButton.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
        int timer = parseTimer(AppSettings.get(this, AppSettings.TIMER, "Off"));
        captureButton.setAlpha(.55f);
        if (timer > 0) {
            showMessage("Capturing in " + timer + "…");
            cameraHandler.postDelayed(this::performCapture, timer * 1000L);
        } else performCapture();
    }

    private int parseTimer(String value) {
        if (value.startsWith("3")) return 3;
        if (value.startsWith("10")) return 10;
        return 0;
    }

    private void performCapture() {
        if (captureSession == null || cameraDevice == null) return;
        try {
            Surface target = useRawForThisCamera && rawReader != null ? rawReader.getSurface() : jpegReader.getSurface();
            CaptureRequest.Builder still = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            still.addTarget(target);
            still.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
            setExposure(still);
            still.set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation());
            setFocusMode(still);
            setFlash(still, false);
            if (AppSettings.get(this, "focus_mode", "Continuous picture autofocus").startsWith("Single")) {
                still.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
            }
            applyZoom(still);
            captureSession.capture(still.build(), new CameraCaptureSession.CaptureCallback() {
                @Override public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    synchronized (captureLock) {
                        pendingResult = result;
                        processPairIfReady();
                    }
                }
                @Override public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request, CaptureFailure failure) {
                    captureInFlight = false;
                    runOnUiThread(() -> { captureButton.setAlpha(1f); showMessage("Capture failed. Try again."); });
                }
            }, cameraHandler);
        } catch (CameraAccessException | IllegalStateException e) {
            captureInFlight = false;
            captureButton.setAlpha(1f);
            showMessage("Camera capture failed.");
        }
    }

    private int jpegOrientation() {
        Integer sensor = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int degrees = rotation == Surface.ROTATION_90 ? 90 : rotation == Surface.ROTATION_180 ? 180 : rotation == Surface.ROTATION_270 ? 270 : 0;
        return ((sensor == null ? 90 : sensor) + degrees + 360) % 360;
    }

    private Bitmap rotateForDisplay(Bitmap bitmap) {
        int rotation = jpegOrientation();
        if (rotation == 0 || rotation == 180) return bitmap;
        Matrix matrix = new Matrix();
        matrix.postRotate(rotation == 90 ? 90 : -90);
        Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        if (rotated != bitmap) bitmap.recycle();
        return rotated;
    }

    /** Applies the selected composition to the render. The DNG remains full-frame. */
    private Bitmap cropToAspect(Bitmap bitmap) {
        String value = AppSettings.get(this, AppSettings.ASPECT_RATIO, "4:3");
        String[] parts = value.split(":");
        if (parts.length != 2) return bitmap;
        float ratio;
        try { ratio = Float.parseFloat(parts[0]) / Float.parseFloat(parts[1]); }
        catch (NumberFormatException e) { return bitmap; }
        if (bitmap.getWidth() < bitmap.getHeight()) ratio = 1f / ratio;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float currentRatio = width / (float) height;
        if (Math.abs(currentRatio - ratio) < .01f) return bitmap;
        int cropW = width;
        int cropH = height;
        if (currentRatio > ratio) cropW = Math.max(1, Math.round(height * ratio));
        else cropH = Math.max(1, Math.round(width / ratio));
        Bitmap cropped = Bitmap.createBitmap(bitmap, (width - cropW) / 2, (height - cropH) / 2, cropW, cropH);
        if (cropped != bitmap) bitmap.recycle();
        return cropped;
    }

    private void writeDng(Image image, TotalCaptureResult result, CameraCharacteristics captureCharacteristics, String name) throws Exception {
        if (result == null || captureCharacteristics == null) throw new IllegalStateException("Missing capture metadata");
        OutputTarget target = createOutput(name, "image/x-adobe-dng", "RAW");
        try (OutputStream stream = target.open()) {
            DngCreator creator = new DngCreator(captureCharacteristics, result);
            creator.writeImage(stream, image);
            creator.close();
        }
        target.publish();
    }

    private void writeJpeg(Bitmap bitmap, String name) throws Exception {
        OutputTarget target = createOutput(name, "image/jpeg", "");
        try (OutputStream stream = target.open()) {
            String qualityChoice = AppSettings.get(this, "jpeg_quality", "Maximum · 96%");
            int quality = qualityChoice.startsWith("High") ? 92 : qualityChoice.startsWith("Balanced") ? 85 : 96;
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream);
        }
        target.publish();
    }

    private OutputTarget createOutput(String name, String mime, String subfolder) throws Exception {
        boolean publishToPhotos = !"image/jpeg".equals(mime) || AppSettings.getBoolean(this, AppSettings.AUTO_EXPORT, true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && publishToPhotos) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            String path = Environment.DIRECTORY_PICTURES + "/Lika Lens" + (subfolder.isEmpty() ? "" : "/" + subfolder);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, path);
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri collection = mime.equals("image/jpeg")
                    ? MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    : MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            Uri uri = getContentResolver().insert(collection, values);
            if (uri == null) throw new IllegalStateException("Could not create media file");
            return new OutputTarget(uri, null);
        }
        File parent = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Lika Lens" + (subfolder.isEmpty() ? "" : File.separator + subfolder));
        if (!parent.exists() && !parent.mkdirs()) throw new IllegalStateException("Could not create output folder");
        return new OutputTarget(null, new File(parent, name));
    }

    private final class OutputTarget {
        final Uri uri;
        final File file;
        OutputTarget(Uri uri, File file) { this.uri = uri; this.file = file; }
        OutputStream open() throws Exception {
            return uri != null ? getContentResolver().openOutputStream(uri, "w") : new FileOutputStream(file);
        }
        void publish() {
            if (uri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.IS_PENDING, 0);
                getContentResolver().update(uri, values, null, null);
            }
        }
    }

    private void chooseBrain() {
        String[] brains = {"LIKA / FILM", "NEUTRAL / RAW", "MONO / SILVER"};
        String selected = AppSettings.brain(this);
        int checked = Arrays.asList(brains).indexOf(selected);
        new AlertDialog.Builder(this)
                .setTitle("Processing brain")
                .setSingleChoiceItems(brains, Math.max(0, checked), (dialog, which) -> {
                    AppSettings.prefs(this).edit().putString(AppSettings.BRAIN, brains[which]).apply();
                    updateBrainButton();
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openLatestFrame() {
        PhotoRepository.Photo latest = PhotoRepository.latest(this);
        if (latest == null) {
            openLibrary();
            return;
        }
        Intent view = new Intent(this, PhotoViewerActivity.class);
        view.putExtra(PhotoViewerActivity.EXTRA_URI, latest.uri.toString());
        view.putExtra(PhotoViewerActivity.EXTRA_NAME, latest.name);
        view.putExtra(PhotoEditorActivity.EXTRA_KEY, PhotoRepository.archiveKey(latest.name));
        startActivity(view);
    }

    private void openLibrary() {
        startActivity(new Intent(this, GalleryActivity.class));
    }

    private void updateGalleryButton() {
        if (galleryButton == null) return;
        PhotoRepository.Photo latest = PhotoRepository.latest(this);
        if (latest == null) {
            galleryButton.setImageResource(R.drawable.ic_lika_lens);
            return;
        }
        try (InputStream stream = getContentResolver().openInputStream(latest.uri)) {
            if (stream == null) return;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 16;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap thumbnail = BitmapFactory.decodeStream(stream, null, options);
            if (thumbnail != null) galleryButton.setImageBitmap(thumbnail);
        } catch (Exception ignored) { }
    }

    private void updateBrainButton() {
        if (brainButton != null) {
            String brain = AppSettings.brain(this);
            brainButton.setText((brain.startsWith("NEUTRAL") ? "RAW" : brain.startsWith("MONO") ? "MONO" : "FILM") + "  ⌄");
        }
    }

    private void chooseLens(float factor) {
        if (cameraManager == null || mainCameraId == null) {
            requestedZoom = Math.max(1f, factor);
            updateLensButtons(requestedZoom);
            return;
        }
        try {
            Integer facing = characteristics == null ? null : characteristics.get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                physicalFocalFactor = 1f;
                animateZoomTo(Math.max(1f, factor));
                return;
            }
            String bestId = mainCameraId;
            float bestPhysical = 1f;
            float score = Float.MAX_VALUE;
            for (String id : cameraManager.getCameraIdList()) {
                CameraCharacteristics c = cameraManager.getCameraCharacteristics(id);
                Integer lensFacing = c.get(CameraCharacteristics.LENS_FACING);
                if (lensFacing == null || lensFacing != CameraCharacteristics.LENS_FACING_BACK) continue;
                float[] focal = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                if (focal == null || focal.length == 0) continue;
                float physical = focal[0] / Math.max(.1f, primaryFocalLength);
                int[] caps = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
                boolean raw = caps != null && Arrays.stream(caps).anyMatch(v -> v == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW);
                if (AppSettings.getBoolean(this, AppSettings.KEEP_RAW, true) && !raw) continue;
                float candidate = Math.abs((float) Math.log(Math.max(.1f, physical) / Math.max(.1f, factor)));
                if (candidate < score) { score = candidate; bestId = id; bestPhysical = physical; }
            }
            float targetZoom = factor;
            if (factor < bestPhysical - .05f) {
                targetZoom = bestPhysical;
                showMessage("This camera has no wider RAW lens. Using its widest supported view.");
            }
            physicalFocalFactor = bestPhysical;
            if (!bestId.equals(cameraId)) {
                requestedZoom = targetZoom;
                updateLensButtons(targetZoom);
                if (AppSettings.getBoolean(this, AppSettings.HAPTICS, true) && captureButton != null) {
                    captureButton.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                }
                if (textureView != null) textureView.setAlpha(.82f);
                cameraId = bestId;
                closeCamera();
                openCameraIfReady();
            } else {
                animateZoomTo(targetZoom);
            }
        } catch (CameraAccessException e) {
            showMessage("Lens selection is unavailable.");
        }
    }

    private void animateZoomTo(float targetZoom) {
        if (AppSettings.getBoolean(this, AppSettings.HAPTICS, true) && captureButton != null) {
            captureButton.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
        }
        if (zoomAnimator != null) zoomAnimator.cancel();
        if (captureSession == null || previewRequest == null) {
            requestedZoom = targetZoom;
            updateLensButtons(targetZoom);
            return;
        }
        float startZoom = requestedZoom;
        zoomAnimator = ValueAnimator.ofFloat(startZoom, targetZoom);
        zoomAnimator.setDuration(230);
        zoomAnimator.setInterpolator(new DecelerateInterpolator(1.4f));
        zoomAnimator.addUpdateListener(animation -> {
            requestedZoom = (float) animation.getAnimatedValue();
            if (previewRequest == null || captureSession == null) return;
            try {
                applyZoom(previewRequest);
                captureSession.setRepeatingRequest(previewRequest.build(), null, cameraHandler);
            } catch (CameraAccessException | IllegalStateException ignored) { }
        });
        zoomAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                updateLensButtons(targetZoom);
            }
        });
        updateLensButtons(targetZoom);
        zoomAnimator.start();
    }

    private void toggleCameraFacing() {
        if (cameraManager == null || characteristics == null) return;
        Integer currentFacing = characteristics.get(CameraCharacteristics.LENS_FACING);
        int targetFacing = currentFacing != null && currentFacing == CameraCharacteristics.LENS_FACING_FRONT
                ? CameraCharacteristics.LENS_FACING_BACK : CameraCharacteristics.LENS_FACING_FRONT;
        try {
            String bestId = null;
            long bestArea = -1;
            boolean bestHasRaw = false;
            float bestFocal = 1f;
            for (String id : cameraManager.getCameraIdList()) {
                CameraCharacteristics candidate = cameraManager.getCameraCharacteristics(id);
                Integer facing = candidate.get(CameraCharacteristics.LENS_FACING);
                if (facing == null || facing != targetFacing) continue;
                StreamConfigurationMap map = candidate.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) continue;
                int[] caps = candidate.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
                Size[] raw = map.getOutputSizes(ImageFormat.RAW_SENSOR);
                boolean hasRaw = caps != null && Arrays.stream(caps).anyMatch(v -> v == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)
                        && raw != null && raw.length > 0;
                Size[] sizes = hasRaw ? raw : map.getOutputSizes(ImageFormat.JPEG);
                long area = sizes == null ? 0 : Arrays.stream(sizes).mapToLong(s -> (long) s.getWidth() * s.getHeight()).max().orElse(0);
                if ((hasRaw && !bestHasRaw) || (hasRaw == bestHasRaw && area > bestArea)) {
                    bestId = id;
                    bestArea = area;
                    bestHasRaw = hasRaw;
                    float[] focal = candidate.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                    bestFocal = focal == null || focal.length == 0 ? 1f : focal[0];
                }
            }
            if (bestId == null) {
                showMessage("This device has no camera on the other side.");
                return;
            }
            cameraId = bestId;
            requestedZoom = 1f;
            physicalFocalFactor = 1f;
            if (targetFacing == CameraCharacteristics.LENS_FACING_BACK) {
                mainCameraId = bestId;
                primaryFocalLength = bestFocal;
            }
            updateLensButtons(1f);
            closeCamera();
            openCameraIfReady();
        } catch (CameraAccessException e) {
            showMessage("Could not switch camera.");
        }
    }

    private void updateLensButtons(float factor) {
        for (TextView button : lensButtons) {
            Object tag = button.getTag();
            if (!(tag instanceof Float)) continue;
            boolean active = Math.abs((Float) tag - factor) < .08f;
            button.setTextColor(active ? ACCENT : 0xffe9ede6);
            button.setBackground(round(active ? 0xe21c1d1b : 0xd8191e1e, 22, active ? ACCENT : 0x556e7872, dp(1)));
        }
    }

    private void applyZoom(CaptureRequest.Builder builder) {
        if (characteristics == null) return;
        Rect active = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        if (active == null) return;
        float digital = Math.max(1f, requestedZoom / Math.max(.1f, physicalFocalFactor));
        int cropW = Math.max(1, Math.round(active.width() / digital));
        int cropH = Math.max(1, Math.round(active.height() / digital));
        int left = active.centerX() - cropW / 2;
        int top = active.centerY() - cropH / 2;
        builder.set(CaptureRequest.SCALER_CROP_REGION, new Rect(left, top, left + cropW, top + cropH));
    }

    private void setFocusMode(CaptureRequest.Builder builder) {
        int[] modes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
        if (modes == null) return;
        String preference = AppSettings.get(this, "focus_mode", "Continuous picture autofocus");
        int wanted = preference.startsWith("Single") ? CaptureRequest.CONTROL_AF_MODE_AUTO
                : CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE;
        if (Arrays.stream(modes).anyMatch(v -> v == wanted)) builder.set(CaptureRequest.CONTROL_AF_MODE, wanted);
        else if (Arrays.stream(modes).anyMatch(v -> v == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)) {
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        }
    }

    private void setExposure(CaptureRequest.Builder builder) {
        String mode = AppSettings.get(this, AppSettings.EXPOSURE_MODE, "Auto exposure");
        if (!mode.startsWith("Manual") || !manualSupported) {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            return;
        }
        Range<Long> exposureRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
        Range<Integer> sensitivityRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
        if (exposureRange == null || sensitivityRange == null) {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            return;
        }
        long exposure = shutterNanoseconds(AppSettings.get(this, AppSettings.MANUAL_SHUTTER, "1/120 sec"));
        int iso = parseIso(AppSettings.get(this, AppSettings.MANUAL_ISO, "ISO 100"));
        exposure = Math.max(exposureRange.getLower(), Math.min(exposureRange.getUpper(), exposure));
        iso = Math.max(sensitivityRange.getLower(), Math.min(sensitivityRange.getUpper(), iso));
        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
        builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposure);
        builder.set(CaptureRequest.SENSOR_SENSITIVITY, iso);
    }

    private void setFlash(CaptureRequest.Builder builder, boolean preview) {
        String mode = AppSettings.get(this, AppSettings.FLASH, "Off");
        if (!flashSupported) {
            builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
            return;
        }
        if (mode.startsWith("Torch")) {
            builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
        } else if (!preview && mode.startsWith("Auto")
                && !AppSettings.get(this, AppSettings.EXPOSURE_MODE, "Auto exposure").startsWith("Manual")) {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
        } else {
            builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
        }
    }

    private void refreshPreviewRequest() {
        if (previewRequest == null || captureSession == null) return;
        try {
            setExposure(previewRequest);
            setFlash(previewRequest, true);
            captureSession.setRepeatingRequest(previewRequest.build(), null, cameraHandler);
        } catch (CameraAccessException | IllegalStateException ignored) { }
    }

    private long shutterNanoseconds(String value) {
        if (value.startsWith("1/30")) return 33_333_333L;
        if (value.startsWith("1/60")) return 16_666_667L;
        if (value.startsWith("1/240")) return 4_166_667L;
        if (value.startsWith("1/500")) return 2_000_000L;
        if (value.startsWith("1/1000")) return 1_000_000L;
        return 8_333_333L;
    }

    private int parseIso(String value) {
        try { return Integer.parseInt(value.replace("ISO", "").trim()); }
        catch (NumberFormatException ignored) { return 100; }
    }

    private void updateReadouts() {
        boolean manual = AppSettings.get(this, AppSettings.EXPOSURE_MODE, "Auto exposure").startsWith("Manual") && manualSupported;
        if (shutterValueText != null) shutterValueText.setText(manual ? AppSettings.get(this, AppSettings.MANUAL_SHUTTER, "1/120 sec").replace(" sec", "") : "AUTO");
        if (isoValueText != null) isoValueText.setText(manual ? AppSettings.get(this, AppSettings.MANUAL_ISO, "ISO 100") : "AUTO");
        int wb = AppSettings.getInt(this, AppSettings.WHITE_BALANCE, 0);
        if (whiteBalanceValueText != null) whiteBalanceValueText.setText(wb <= 0 ? "AUTO" : wb + " K");
    }

    private void configureTransform(int viewWidth, int viewHeight) {
        if (textureView == null || previewSize == null || viewWidth == 0 || viewHeight == 0) return;
        Matrix matrix = new Matrix();
        float viewRatio = viewWidth / (float) viewHeight;
        float bufferRatio = previewSize.getHeight() / (float) previewSize.getWidth();
        float scale = Math.max(viewRatio / bufferRatio, bufferRatio / viewRatio);
        matrix.setScale(scale, scale, viewWidth / 2f, viewHeight / 2f);
        matrix.postRotate(90f, viewWidth / 2f, viewHeight / 2f);
        textureView.setTransform(matrix);
    }

    private void closeReader(ImageReader reader) {
        if (reader != null) {
            try { reader.setOnImageAvailableListener(null, null); } catch (RuntimeException ignored) {}
            reader.close();
        }
    }

    private static final class CompositionGrid extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        CompositionGrid(Activity activity) {
            super(activity);
            paint.setColor(0x99f2f3ec);
            paint.setStrokeWidth(activity.getResources().getDisplayMetrics().density);
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth(), h = getHeight();
            canvas.drawLine(w / 3f, 0, w / 3f, h, paint);
            canvas.drawLine(w * 2f / 3f, 0, w * 2f / 3f, h, paint);
            canvas.drawLine(0, h / 3f, w, h / 3f, paint);
            canvas.drawLine(0, h * 2f / 3f, w, h * 2f / 3f, paint);
        }
    }

    private void closeCamera() {
        if (zoomAnimator != null) { zoomAnimator.cancel(); zoomAnimator = null; }
        if (textureView != null) textureView.animate().cancel();
        started = false;
        openingCamera = false;
        captureInFlight = false;
        closeSessionOnly();
        if (cameraDevice != null) { cameraDevice.close(); cameraDevice = null; }
        closeReader(rawReader); rawReader = null;
        closeReader(jpegReader); jpegReader = null;
        synchronized (captureLock) {
            if (pendingImage != null) { pendingImage.close(); pendingImage = null; }
            pendingResult = null;
        }
    }

    private void showMessage(String message) {
        if (messageView == null) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); return; }
        messageView.setText(message);
        messageView.setVisibility(View.VISIBLE);
        toastUntil = System.currentTimeMillis() + 2100;
        messageView.removeCallbacks(hideMessage);
        messageView.postDelayed(hideMessage, 2100);
    }

    private final Runnable hideMessage = () -> {
        if (messageView != null && System.currentTimeMillis() >= toastUntil) messageView.setVisibility(View.GONE);
    };

    @Override public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) { openCameraIfReady(); }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) { configureTransform(width, height); }
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) { closeCamera(); return true; }
    @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) { }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) openCameraIfReady();
        else if (requestCode == CAMERA_PERMISSION) {
            cameraState.setText("CAMERA PERMISSION REQUIRED");
            showMessage("Allow camera access to use Lika Lens.");
        }
    }
}
