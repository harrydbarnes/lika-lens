package com.scope.camera;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentUris;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.content.Intent;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

/** Native settings for capture, RAW development and the viewfinder. */
public class SettingsActivity extends Activity {
    private static final String TAG = "SettingsActivity";
    private static final int BG = 0xff0b0d0e;
    private static final int CARD = 0xff121617;
    private static final int LINE = 0xff293031;
    private static final int ACCENT = 0xffd7b574;
    private LinearLayout content;
    private ScrollView scroll;
    private LinearLayout categoryBar;
    private TextView headerSubtitleView;
    private String activeCategory = "LOOKS";
    private final java.util.Set<String> expandedAdvanced = new java.util.HashSet<>();
    private LinearLayout currentSectionCard;
    private ImageView heroImageView;
    private TextView heroCompareBadge;
    private TextView heroShotLabel;
    private View heroPlaceholderView;
    private Bitmap baseHeroBitmap;
    private Bitmap cachedHeroSource;
    private Bitmap currentHeroBitmap;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final JobState heroJob = new JobState();
    private final java.util.concurrent.ExecutorService heroWorker = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "lika-settings-worker");
        t.setDaemon(true);
        return t;
    });
    private enum HeroCompare { GRADED, ORIGINAL }
    private HeroCompare heroCompare = HeroCompare.GRADED;
    private Bitmap heroGradedBitmap;
    private Runnable heroRender;
    private String pendingBackupName;
    private String pendingBackupJson;
    private volatile boolean heroLoadStarted;
    private boolean hapticsEnabled = true;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    this::finish
            );
        }
        AppSettings.migrateIfNeeded(this);
        hapticsEnabled = AppSettings.getBoolean(this, AppSettings.HAPTICS, true);
        LutManager.init(this);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        build();
    }

    private void build() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(BG);
        page.setFitsSystemWindows(true);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), dp(13), dp(18), dp(13));
        TextView back = text("‹", 29, 0xffe8ece5, false);
        back.setGravity(Gravity.CENTER);
        header.addView(back, new LinearLayout.LayoutParams(dp(34), dp(40)));
        back.setOnClickListener(v -> finish());
        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setPadding(dp(8), 0, 0, 0);
        titleBox.addView(text("SETTINGS", 14, 0xfff1f2ec, true));
        headerSubtitleView = text(categorySubtitle(activeCategory), 9, 0xff89948e, false);
        LinearLayout.LayoutParams subtitleLp = new LinearLayout.LayoutParams(-2, -2);
        subtitleLp.topMargin = dp(3);
        titleBox.addView(headerSubtitleView, subtitleLp);
        header.addView(titleBox, new LinearLayout.LayoutParams(0, -2, 1));
        TextView reset = pill("RESET", false);
        header.addView(reset, new LinearLayout.LayoutParams(dp(54), dp(32)));
        reset.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Reset settings?")
                .setMessage("Restore default look and capture settings. Saved photographs are not affected.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Reset", (dialog, which) -> {
                    AppSettings.prefs(this).edit().clear().apply();
                    AppSettings.migrateIfNeeded(this);
                    hapticsEnabled = true;
                    rebuildContent(true);
                }).show());
        TextView done = pill("DONE", true);
        LinearLayout.LayoutParams doneLp = new LinearLayout.LayoutParams(dp(54), dp(32));
        doneLp.leftMargin = dp(6);
        header.addView(done, doneLp);
        done.setOnClickListener(v -> finish());
        page.addView(header);

        WindowMetricsHelper.WindowSizeInfo windowInfo = WindowMetricsHelper.computeWindowSizeInfo(this);
        categoryBar = new LinearLayout(this);
        categoryBar.setGravity(Gravity.CENTER_VERTICAL);
        updateCategoryBarPadding(windowInfo);
        page.addView(categoryBar);
        refreshCategoryBar();

        scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(5), dp(16), dp(26));
        FrameLayout wrapper = new FrameLayout(this);
        wrapper.addView(content, getContentLayoutParams(windowInfo));
        scroll.addView(wrapper, new FrameLayout.LayoutParams(-1, -2));
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(page);
        populateContent();
    }

    private FrameLayout.LayoutParams getContentLayoutParams(WindowMetricsHelper.WindowSizeInfo info) {
        int maxW = info.widthDp >= 760 ? dp(720) : ViewGroup.LayoutParams.MATCH_PARENT;
        return new FrameLayout.LayoutParams(maxW, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL);
    }

    private void updateCategoryBarPadding(WindowMetricsHelper.WindowSizeInfo info) {
        if (categoryBar == null) return;
        int extraPad = info.widthDp >= 760 ? dp((info.widthDp - 720) / 2) : 0;
        categoryBar.setPadding(dp(18) + extraPad, 0, dp(18) + extraPad, dp(8));
    }

    @Override
    protected void onResume() {
        super.onResume();
        hapticsEnabled = AppSettings.getBoolean(this, AppSettings.HAPTICS, true);
        AppUpdateManager.onResume(this);
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        WindowMetricsHelper.WindowSizeInfo info = WindowMetricsHelper.computeWindowSizeInfo(this);
        if (content != null) {
            content.setLayoutParams(getContentLayoutParams(info));
        }
        updateCategoryBarPadding(info);
    }

    private void rebuildContent() {
        rebuildContent(true);
    }

    private void rebuildContent(boolean scrollToTop) {
        final int prevScrollY = scroll != null ? scroll.getScrollY() : 0;
        finishSection();
        content.removeAllViews();
        populateContent();
        if (scroll != null) {
            scroll.post(() -> {
                if (scroll != null) {
                    scroll.scrollTo(0, scrollToTop ? 0 : prevScrollY);
                }
            });
        }
    }

    private static String categorySubtitle(String category) {
        if ("CAMERA".equals(category)) return "Camera & viewfinder";
        if ("VIDEO".equals(category)) return "Video & audio";
        if ("GENERAL".equals(category)) return "Files, storage & updates";
        return "Looks & Processing";
    }

    private void refreshCategoryBar() {
        if (categoryBar == null) return;
        categoryBar.removeAllViews();
        String[] categories = {"LOOKS", "CAMERA", "VIDEO", "GENERAL"};
        for (int i = 0; i < categories.length; i++) {
            final String category = categories[i];
            TextView tab = pill(category, category.equals(activeCategory));
            tab.setSingleLine(true);
            tab.setPadding(dp(4), 0, dp(4), 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(34), 1f);
            if (i > 0) lp.leftMargin = dp(5);
            categoryBar.addView(tab, lp);
            tab.setOnClickListener(v -> {
                if (hapticsEnabled) {
                    tab.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                }
                activeCategory = category;
                if (headerSubtitleView != null) {
                    headerSubtitleView.setText(categorySubtitle(category));
                }
                refreshCategoryBar();
                rebuildContent(true);
            });
        }
    }

    private void populateContent() {
        if ("CAMERA".equals(activeCategory)) {
            populateCameraSettings();
        } else if ("VIDEO".equals(activeCategory)) {
            populateVideoSettings();
        } else if ("GENERAL".equals(activeCategory)) {
            populateGeneralSettings();
        } else {
            populateLooksSettings();
        }
    }

    private void populateLooksSettings() {
        addEditorHero();
        beginSection("LOOK & FILM BRAIN", "Point-and-shoot defaults and film rendering base.");
        addSwitch("Easy point & shoot mode", "Simple automatic defaults; explicit look adjustments still take precedence", AppSettings.EASY_MODE, true);
        addChoice("Rendering brain", "Base film emulation for tone, colour, and contrast", AppSettings.BRAIN,
                AppSettings.BRAIN_CHOICES, AppSettings.BRAIN_LIKA_FILM);
        addChoice("Film push / pull", "Shift contrast, shadow density, and grain like push/pull development", AppSettings.FILM_PUSH_PULL,
                AppSettings.FILM_PUSH_PULL_CHOICES, AppSettings.FILM_PUSH_PULL_CHOICES[0]);
        addSwitch("Live film preview", "Preview your active film brain, adjustments, and LUT in the viewfinder", AppSettings.LIVE_FILM_PREVIEW, true);

        beginSection("PRIMARY TONE & ADJUST PANEL", "Core exposure, warmth, highlights, shadows, and contrast defaults.");
        addSlider("Exposure trim", "Exposure compensation in 0.1 EV steps", AppSettings.EXPOSURE, -20, 20, AppSettings.DEFAULT_EXPOSURE);
        addSlider("Warmth shift", "Shift the selected brain warmer or cooler", AppSettings.WARMTH, 0, 100, AppSettings.DEFAULT_WARMTH);
        addSlider("Highlights", "Smooth roll-off for bright tones", AppSettings.HIGHLIGHTS, 0, 100, AppSettings.DEFAULT_HIGHLIGHTS);
        addSlider("Shadows", "Shadow lift or depth control", AppSettings.SHADOWS, 0, 100, AppSettings.DEFAULT_SHADOWS);
        addSlider("Contrast", "Overall midtone contrast", AppSettings.CONTRAST, 50, 150, AppSettings.DEFAULT_CONTRAST);
        int adjustCount = AppSettings.getAdjustControls(this).size();
        addInfo("Customise Adjust panel…",
                adjustCount + (adjustCount == 1 ? " control active" : " controls active")
                        + " · Add, remove, or reorder sliders in the camera Adjust drawer",
                ACCENT,
                () -> AdjustControlsDialog.show(this, () -> rebuildContent(false)));

        beginSection("CUSTOM RECIPES & 3D LUTS", "Saved user recipes and 3D .cube colour lookup tables.");
        for (int i = 1; i <= 3; i++) {
            addCustomRecipeRow(i);
        }
        List<String> luts = LutManager.getAvailableLutNames(this);
        String currentLut = AppSettings.get(this, AppSettings.LUT_NAME, "None");
        addChoice("Default 3D LUT", "Primary 3D LUT blended during RAW development", AppSettings.LUT_NAME,
                luts.toArray(new String[0]), currentLut);
        addSlider("Default LUT intensity", "Blend strength between film brain and 3D LUT", AppSettings.LUT_INTENSITY,
                0, 100, AppSettings.getInt(this, AppSettings.LUT_INTENSITY, AppSettings.DEFAULT_LUT_INTENSITY));
        addInfo("Import .cube LUT…", "Browse storage to import a 3D or 1D .cube colour table", ACCENT, this::launchLutPicker);
        for (String lutName : luts) {
            if (!LutManager.isBuiltIn(lutName)) {
                addCustomLutRow(lutName);
            }
        }
        finishSection();

        addAdvancedSection("looks_advanced", "Advanced tone, colour & grain",
                "Pivot, tone curve, shadow profile, highlight recovery, grain & output gamut", () -> {
                    addSlider("Contrast pivot", "Mid-grey fulcrum around which contrast expands or compresses", AppSettings.PIVOT, 0, 100, AppSettings.DEFAULT_PIVOT);
                    addChoice("Tone curve", "Characteristic tonal response curve", AppSettings.TONE_CURVE,
                            AppSettings.TONE_CURVE_CHOICES, "Filmic S-Curve");
                    addChoice("Shadow profile", "Auto follows the active brain's toe; pick an option to override blacks", AppSettings.SHADOW_PROFILE,
                            AppSettings.SHADOW_PROFILE_CHOICES, AppSettings.SHADOW_PROFILE_AUTO);
                    addChoice("Highlight recovery", "Auto adapts to scene clipping; pick a manual override to lock behaviour", AppSettings.HIGHLIGHT_RECOVERY,
                            AppSettings.HIGHLIGHT_RECOVERY_CHOICES, AppSettings.HL_REC_AUTO);
                    addChoice("White balance", "Auto uses each frame's measured sensor white balance", AppSettings.WHITE_BALANCE_MODE,
                            new String[]{"Auto · as shot", "2800 K · incandescent", "3200 K · tungsten", "4000 K · warm LED", "5000 K · daylight", "5600 K · daylight", "6500 K · overcast", "7500 K · shade"}, "Auto · as shot");
                    addSlider("Colour intensity", "Saturation balance around neutral grey", AppSettings.SATURATION, 0, 160, AppSettings.DEFAULT_SATURATION);
                    addSlider("Saturated colour roll-off", "Compress highly saturated colours softly without shifting hue", AppSettings.COLOR_ROLLOFF, 0, 100, AppSettings.DEFAULT_COLOR_ROLLOFF);
                    addChoice("Skin tone protection", "Auto follows the selected brain; On or Off overrides even in Easy mode", AppSettings.SKIN_PROTECTION_MODE,
                            AppSettings.OVERRIDE_CHOICES, AppSettings.OVERRIDE_AUTO);
                    addChoice("Smart filmic vibrance", "Auto follows the selected brain; On or Off overrides even in Easy mode", AppSettings.SMART_VIBRANCE_MODE,
                            AppSettings.OVERRIDE_CHOICES, AppSettings.OVERRIDE_AUTO);
                    addSlider("Film grain", "Resolution-aware organic luminance texture", AppSettings.GRAIN, 0, 100, AppSettings.DEFAULT_GRAIN);
                    addChoice("Grain structure", "Film gauge texture (35 mm fine, 16 mm soft, or 8 mm coarse)", AppSettings.GRAIN_STRUCTURE,
                            AppSettings.GRAIN_STRUCTURE_CHOICES, AppSettings.GRAIN_STRUCTURE_CHOICES[0]);
                    addChoice("Grain response", "Midtone-weighted film response or uniform across exposure", AppSettings.GRAIN_RESPONSE,
                            new String[]{"Luminance-weighted", "Uniform"}, "Luminance-weighted");
                    addSlider("Halation", "Warm optical glow around bright light sources", AppSettings.HALATION, 0, 100, AppSettings.DEFAULT_HALATION);
                    addSlider("Vignette", "Subtle corner fall-off", AppSettings.VIGNETTE, 0, 100, AppSettings.DEFAULT_VIGNETTE);
                    addSlider("Colour noise reduction", "Reduce chroma speckle in high-ISO captures", AppSettings.COLOR_NR, 0, 100, AppSettings.DEFAULT_COLOR_NR);
                    addSlider("Output sharpening", "Subtle edge crispness applied after demosaicing", AppSettings.SHARPEN, 0, 100, AppSettings.DEFAULT_SHARPEN);
                    addChoice("Colour space", "Output colour gamut and embedded ICC profile", AppSettings.COLOR_SPACE,
                            AppSettings.COLOR_SPACE_CHOICES, AppSettings.COLOR_SPACE_SRGB);
                    addSwitch("Ultra HDR gainmap", "Embed ISO 21496-1 gainmap for highlight headroom on supported HDR displays", AppSettings.ULTRA_HDR, true);
                    addChoice("Frame border", "Optional film rebate or matte border around exported photos", AppSettings.FRAME_BORDER,
                            AppSettings.FRAME_BORDER_CHOICES, "None");
                    addSwitch("Optical lens correction", "Apply camera lens shading map during RAW development", AppSettings.OPTICAL_CORRECTION, true);
                });
    }

    private void populateCameraSettings() {
        beginSection("STILL CAPTURE & LENS", "Launch behaviour, framing ratio, default lens, focus, and feedback.");
        addChoice("App launch mode", "Mode selected when opening Lika Lens", AppSettings.LAUNCH_MODE,
                AppSettings.LAUNCH_MODES, "Photo (default)");
        addChoice("Frame shape", "Aspect ratio for viewfinder framing and processed JPEG; DNG stays full-sensor", AppSettings.ASPECT_RATIO,
                AppSettings.ASPECT_RATIOS, "4:3");
        addChoice("Default lens", "Camera lens requested on app launch where hardware supports it", AppSettings.DEFAULT_LENS,
                new String[]{"Auto / Wide 1×", "Ultra-wide .5×", "Telephoto 2×/5×", "Last used"}, "Auto / Wide 1×");
        addChoice("Flash mode", "Rear LED flash or front warm screen flash", AppSettings.FLASH,
                new String[]{"Off", "Auto", "Torch"}, "Off");
        addChoice("Self-timer", "Delay before shutter release", AppSettings.TIMER,
                new String[]{"Off", "3 seconds", "10 seconds"}, "Off");
        addSwitch("Mirror front camera", "Flip front-camera selfies horizontally to match preview", AppSettings.MIRROR_FRONT, true);
        addChoice("Focus mode", "Autofocus behaviour or manual focus distance", AppSettings.FOCUS_MODE,
                AppSettings.FOCUS_MODE_CHOICES, AppSettings.FOCUS_CONTINUOUS);
        addSwitch("Shutter sound", "Play capture sound when device ringer mode permits", AppSettings.SHUTTER_SOUND, true);
        addSwitch("Haptic feedback", "Tactile response on shutter, dials, and landmarks", AppSettings.HAPTICS, true);

        beginSection("COMPOSITION & METERING AIDS", "Viewfinder overlays (not baked into saved photographs).");
        addChoice("Composition grid", "Framing guide inside the viewfinder", AppSettings.GRID_STYLE,
                new String[]{"None", "2×2", "3×3", "4×4", "Golden ratio"}, "None");
        addSwitch("Visual level & horizon", "Horizon indicator and top-down flat-lay crosshair", AppSettings.VISUAL_LEVEL, true);
        addSwitch("Live tonal histogram", "Real-time tonal graph in the viewfinder corner", AppSettings.HISTOGRAM, false);
        addSwitch("Focus peaking", "Highlight sharp high-contrast edges in gold", AppSettings.FOCUS_PEAKING, false);
        addSwitch("Highlight zebra stripes", "Diagonal warning stripes over near-clipped bright areas", AppSettings.ZEBRA, false);
        finishSection();

        addAdvancedSection("camera_advanced", "Advanced manual, loupe & hardware controls",
                "Manual exposure, focus loupe, night mode, gestures, HUD & sensor status", () -> {
                    addSwitch("Show loupe when adjusting MF", "Automatically open 5× focus magnifier while dragging the manual focus slider", AppSettings.LOUPE_ON_MF_ADJUST, false);
                    addSwitch("Tap-to-focus optical loupe", "Show 5× focus magnifier while dragging the tap reticle", AppSettings.OPTICAL_LOUPE, false);
                    addChoice("Exposure mode", "Manual shutter and ISO require camera hardware support", AppSettings.EXPOSURE_MODE,
                            new String[]{"Auto exposure", "Manual exposure"}, "Auto exposure");
                    addChoice("Manual shutter", "Shutter interval when Manual exposure is selected", AppSettings.MANUAL_SHUTTER,
                            new String[]{"1/30 sec", "1/60 sec", "1/120 sec", "1/240 sec", "1/500 sec", "1/1000 sec", "1/2000 sec", "1/4000 sec"}, "1/120 sec");
                    addChoice("Manual ISO", "Sensor sensitivity when Manual exposure is selected", AppSettings.MANUAL_ISO,
                            new String[]{"ISO 50", "ISO 100", "ISO 200", "ISO 400", "ISO 800", "ISO 1600", "ISO 3200"}, "ISO 100");
                    addChoice("RAW capture size", "Resolution of RAW captures and editable sensor originals", AppSettings.RAW_SIZE,
                            new String[]{"Maximum sensor resolution", "12 MP · faster development"}, "Maximum sensor resolution");
                    addChoice("Astrophotography / Night mode", "Extended exposure for low-light stationary captures", AppSettings.ASTROPHOTOGRAPHY,
                            AppSettings.ASTRO_CHOICES, "Auto (when stationary at night)");
                    addChoice("Stabilization", "Optical and electronic stabilization for preview and video", AppSettings.STABILIZATION,
                            AppSettings.STABILIZATION_CHOICES, "Cinema OIS + EIS");
                    addChoice("Anti-banding / flicker", "Match mains frequency to prevent artificial lighting flicker", AppSettings.ANTI_BANDING,
                            new String[]{"Auto (50/60 Hz)", "50 Hz (Europe/Asia)", "60 Hz (Americas/Japan)", "Off"}, "Auto (50/60 Hz)");
                    addChoice("Reticle metering", "What a viewfinder tap sets (autofocus, exposure, or both)", AppSettings.RETICLE_METERING,
                            new String[]{"AF + AE Metering", "AF Only (Continuous AE)", "AE Only (Continuous AF)"}, "AF + AE Metering");
                    addChoice("Histogram display style", "Luminance curve, RGB parade, or compact bar", AppSettings.HISTOGRAM_MODE,
                            new String[]{"Luminance", "RGB Parade", "Compact"}, "Luminance");
                    addChoice("Horizon visibility", "Show horizon line only near level or at all times", AppSettings.HORIZON_MODE,
                            new String[]{"Smart (near level only)", "Always visible"}, "Smart (near level only)");
                    addChoice("Horizon sensitivity", "Tolerance for snapping the level indicator to gold", AppSettings.HORIZON_SENSITIVITY,
                            new String[]{"Normal (±1.0°)", "Precise (±0.5°)"}, "Normal (±1.0°)");
                    addSwitch("Show dirty lens warning", "Alert if haze or low scene contrast suggests smudged glass", AppSettings.DIRTY_LENS_WARNING, true);
                    addSwitch("Timer countdown light", "Pulse rear flash LED during self-timer countdown", AppSettings.TIMER_LIGHT, true);
                    addSwitch("Palm gesture timer", "Start a 3-second countdown when an open palm is detected", AppSettings.PALM_TIMER, false);
                    addChoice("Long press gesture", "Action when pressing and holding on the viewfinder", AppSettings.VIEWFINDER_LONG_PRESS,
                            new String[]{"AE / AF lock", "Lock exposure only (AE-L)", "Reset exposure bias", "None"}, "AE / AF lock");
                    addChoice("Double tap gesture", "Action when double-tapping the viewfinder", AppSettings.VIEWFINDER_DOUBLE_TAP,
                            new String[]{"Toggle 1× / 2× zoom", "Reset exposure bias", "None"}, "Toggle 1× / 2× zoom");
                    addChoice("Volume key action", "Hardware volume button behaviour inside camera", AppSettings.VOLUME_ROCKER,
                            new String[]{"Shutter", "Zoom", "Volume", "Off"}, "Shutter");
                    addSwitch("Shutter ocular wink", "Brief blackout pulse on shutter release", AppSettings.SHUTTER_WINK, true);
                    addSwitch("Exposure telemetry HUD", "Display live shutter, ISO, and white balance readout in viewfinder", AppSettings.SHOW_TELEMETRY, false);
                    addSwitch("Remaining shots counter", "Show estimated remaining shots inside the telemetry HUD", AppSettings.FRAME_COUNTER, true);
                    boolean hasRaw = AppSettings.getBoolean(this, AppSettings.RAW_SUPPORTED, false);
                    addInfo("RAW sensor support", hasRaw ? "Supported on the last opened camera" : "Not confirmed yet · open camera to check sensor capability", hasRaw ? ACCENT : 0xffdfb56f);
                    boolean hasManual = AppSettings.getBoolean(this, AppSettings.MANUAL_SUPPORTED, false);
                    addInfo("Manual sensor support", hasManual ? "Manual shutter, ISO, and exposure available on last opened camera" : "Not confirmed yet · depends on active camera sensor", hasManual ? ACCENT : 0xffdfb56f);
                    boolean hasMf = AppSettings.getBoolean(this, AppSettings.MANUAL_FOCUS_SUPPORTED, false);
                    addInfo("Manual focus support", hasMf ? "LENS_FOCUS_DISTANCE supported on last opened camera" : "Fixed-focus or not confirmed yet · depends on active lens", hasMf ? ACCENT : 0xffdfb56f);
                });
    }

    private void populateVideoSettings() {
        boolean hdrEnabled = AppSettings.getBoolean(this, AppSettings.VIDEO_HDR, false);
        beginSection("VIDEO RECORDING & SHUTTER", "Frame cadence, codec, HDR, shutter angle, and stabilization.");
        addChoice("Video frame rate", "Recording cadence (validated against active camera FPS ranges)", AppSettings.VIDEO_FPS,
                AppSettings.VIDEO_FPS_CHOICES, "24 fps (Cinematic)");
        addChoice("Video framing", "Recording aspect ratio", AppSettings.VIDEO_ASPECT,
                new String[]{"16:9 Widescreen", "2.39:1 Cinemascope"}, "16:9 Widescreen");
        addChoice("Video bitrate", "Target encoder bitrate (scaled automatically when HDR is active)", AppSettings.VIDEO_BITRATE,
                new String[]{"High · 24 Mbps", "Ultra · 35 Mbps", "Standard · 14 Mbps"}, "High · 24 Mbps");
        addChoice("Video codec",
                hdrEnabled ? "HEVC (H.265) is required while 10-bit HLG HDR is active"
                        : "H.264 for broad compatibility or HEVC for smaller files and 10-bit HDR",
                AppSettings.VIDEO_CODEC,
                new String[]{"H.264 · Universal", "HEVC (H.265) · High Efficiency"}, "H.264 · Universal");
        addSwitch("10-bit HLG HDR video", "Record 10-bit HLG HDR where the sensor supports HLG10; selects HEVC automatically", AppSettings.VIDEO_HDR, false);
        addChoice("Video shutter", "180° shutter targets half the frame interval (e.g. 1/48 s at 24 fps, 1/50 s at 25 fps) with Auto ISO",
                AppSettings.VIDEO_SHUTTER_MODE, AppSettings.VIDEO_SHUTTER_MODES, AppSettings.VIDEO_SHUTTER_180);
        addChoice("Stabilization", "Optical and electronic stabilization during recording", AppSettings.STABILIZATION,
                AppSettings.STABILIZATION_CHOICES, "Cinema OIS + EIS");

        beginSection("VIDEO AUDIO", "Microphone tuning for video recording.");
        addChoice("Audio source", "Natural stereo camcorder capture, voice focus, raw mic, or muted", AppSettings.VIDEO_AUDIO,
                AppSettings.VIDEO_AUDIO_CHOICES, "Camcorder · Stereo");
        finishSection();

        addAdvancedSection("video_advanced", "Advanced audio & capability notes",
                "Directional audio zoom and hardware capability status", () -> {
                    boolean audioZoomCapable = AppSettings.prefs(this).contains(AppSettings.AUDIO_ZOOM_SUPPORTED)
                            ? AppSettings.getBoolean(this, AppSettings.AUDIO_ZOOM_SUPPORTED, false)
                            : VideoRecordingCoordinator.probeAudioZoomSupported(this);
                    if (audioZoomCapable) {
                        addSwitch("Directional audio zoom", "Steer built-in microphone array pickup toward zoomed subject during recording", AppSettings.AUDIO_ZOOM, true);
                    } else {
                        String reason = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                                ? "Unavailable · requires Android 10 (API 29) or newer"
                                : "Unavailable · device does not expose a directional or multi-microphone array";
                        addInfo("Directional audio zoom", reason, 0xffaab3ad);
                    }
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        addInfo("10-bit HLG HDR capability", "Unavailable · requires Android 13 (API 33) or newer", 0xffaab3ad);
                    } else if (AppSettings.prefs(this).contains(AppSettings.VIDEO_HDR_SUPPORTED)) {
                        boolean hdrCapable = AppSettings.getBoolean(this, AppSettings.VIDEO_HDR_SUPPORTED, false);
                        addInfo("10-bit HLG HDR capability",
                                hdrCapable ? "Supported on active camera lens with HEVC Main10"
                                        : "Not supported on the last opened camera lens or encoder · falls back to SDR",
                                hdrCapable ? ACCENT : 0xffdfb56f);
                    }
                });
    }

    private void populateGeneralSettings() {
        String archiveLimit = AppSettings.get(this, AppSettings.EDITABLE_ARCHIVE_LIMIT, AppSettings.EDITABLE_ARCHIVE_512MB);
        beginSection("PHOTO OUTPUT & EDITABLE ORIGINALS", "Gallery exports and app-private sensor originals for re-editing.");
        addChoice("Photo output", "Gallery receives JPEGs (with private RAW edit cache); \"DNG original only\" saves to Pictures/Lika Lens/RAW (open in Photos/RAW apps)",
                AppSettings.PHOTO_OUTPUT, AppSettings.PHOTO_OUTPUT_CHOICES, AppSettings.PHOTO_OUTPUT_JPEG_ONLY);
        addChoice("Editable originals limit", "Maximum app-private RAW cache before oldest unpinned originals are trimmed",
                AppSettings.EDITABLE_ARCHIVE_LIMIT, AppSettings.EDITABLE_ARCHIVE_LIMIT_CHOICES, AppSettings.EDITABLE_ARCHIVE_512MB);
        addInfo("Editable originals cache",
                RawArchive.formatArchiveSummary(this, archiveLimit) + "\nTap to clear cache (Gallery JPEGs & DNGs stay untouched)",
                ACCENT, this::showClearArchiveDialog);

        beginSection("FILES, METADATA & ERGONOMICS", "Naming, folder layout, JPEG encoding, GPS, and handedness.");
        addSwitch("Save location (GPS)", "Embed GPS coordinates in exported JPEG, DNG, and video files when permitted", AppSettings.LOCATION, true);
        addChoice("Left-hand mode", "Shift header controls or swap camera flip and gallery placement",
                AppSettings.LEFT_HAND_MODE, AppSettings.LEFT_HAND_MODE_CHOICES, AppSettings.LEFT_HAND_OFF);
        addChoice("File naming pattern", "Format for exported file names", AppSettings.FILE_NAMING,
                new String[]{"Prefix + Timestamp", "Film Look + Timestamp", "Roll counter (e.g. ROLL_0001)"}, "Prefix + Timestamp");
        addChoice("File name prefix", "Prefix used for captured file names", AppSettings.FILE_PREFIX,
                new String[]{"LIKA", "STILLS", "REEL", "CUSTOM…"}, "LIKA");
        addChoice("Folder organisation", "Subfolder structure under Pictures/Lika Lens", AppSettings.FOLDER_ORG,
                new String[]{"Single folder (Pictures/Lika Lens)", "Organise by month (YYYY-MM)", "Organise by film look"}, "Single folder (Pictures/Lika Lens)");
        addChoice("JPEG quality", "Output compression quality for Gallery JPEGs", AppSettings.JPEG_QUALITY,
                new String[]{"Maximum · 98% (Archival)", "High · 95% (Recommended)", "Balanced · 90%", "Compact · 85%"}, "High · 95% (Recommended)");
        finishSection();

        addAdvancedSection("general_advanced", "Advanced metadata, storage & GIF defaults",
                "EXIF camera metadata, detailed capacity estimate, and GIF export defaults", () -> {
                    addSwitch("Include EXIF shooting params", "Write shutter speed, ISO, aperture, and focal length into JPEG EXIF", AppSettings.EXIF_PARAMS, true);
                    addInfo("Device storage & roll capacity", AppSettings.formatDetailedStorageBreakdown(this), ACCENT);
                    addChoice("Default GIF frame rate", "Playback cadence for animated GIF exports", AppSettings.GIF_FPS,
                            new String[]{"2 fps · Stop Motion", "5 fps · Retro Cinematic", "10 fps · Smooth (Recommended)", "15 fps · High Fluidity"}, "10 fps · Smooth (Recommended)");
                    addChoice("Default GIF resolution", "Output pixel size for GIF generation", AppSettings.GIF_RESOLUTION,
                            new String[]{"320px · Compact & Light", "480px · Standard (Recommended)", "640px · Pro HD"}, "480px · Standard (Recommended)");
                    addSwitch("Boomerang forward/reverse loop", "Ping-pong frames for seamless loops", AppSettings.GIF_BOOMERANG, false);
                });

        beginSection("BACKUP & RESTORE", "Save, share, or import settings, custom recipes, and custom LUTs.");
        addInfo("Save named backup", "Export settings, recipes, and custom LUTs to a file", ACCENT, () -> showBackupNameDialog(false));
        addInfo("Share backup", "Export and share a backup bundle with another app or device", ACCENT, () -> showBackupNameDialog(true));
        addInfo("Import backup", "Restore preferences, custom recipes, and LUTs from a backup file", 0xffe6eae4, this::launchBackupImportPicker);

        beginSection("UPDATES & ABOUT", "Application version and GitHub release channel.");
        addChoice("Update release channel", "Stable releases are recommended; Beta delivers pre-release builds",
                AppSettings.UPDATE_CHANNEL, AppSettings.UPDATE_CHANNEL_CHOICES, AppSettings.UPDATE_CHANNEL_STABLE);
        addSwitch("Check for updates on launch", "Check for newer releases in the background on startup",
                AppSettings.AUTO_CHECK_UPDATES, true);
        addInfo("Check for updates now", "Query GitHub Releases for a newer APK", ACCENT, this::checkAppUpdate);
        String verName = AppUpdateManager.getAppVersionName(this);
        int verCode = AppUpdateManager.getAppVersionCode(this);
        addInfo("Installed version", "Lika Lens v" + verName + " (Build " + verCode + ")", 0xffaab3ad);
        finishSection();
    }

    private void checkAppUpdate() {
        if (hapticsEnabled) {
            getWindow().getDecorView().performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
        Toast.makeText(this, "Checking GitHub for updates…", Toast.LENGTH_SHORT).show();
        AppUpdateManager.checkForUpdate(this, true, new AppUpdateManager.UpdateCallback() {
            @Override
            public void onUpdateAvailable(AppUpdateManager.UpdateInfo updateInfo) {
                AppUpdateManager.showUpdateDialog(SettingsActivity.this, updateInfo);
            }

            @Override
            public void onNoUpdateAvailable() {
                Toast.makeText(SettingsActivity.this, "You're running the latest version of Lika Lens.", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(SettingsActivity.this, "Update check failed: " + message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void beginSection(String title, String subtitle) {
        finishSection();
        if (title != null && !title.isEmpty()) {
            LinearLayout header = new LinearLayout(this);
            header.setOrientation(LinearLayout.VERTICAL);
            header.setPadding(dp(4), dp(16), dp(4), dp(6));
            TextView titleView = text(title, 9, ACCENT, true);
            titleView.setLetterSpacing(.16f);
            header.addView(titleView);
            if (subtitle != null && !subtitle.isEmpty()) {
                TextView sub = text(subtitle, 8, 0xff8d9892, false);
                sub.setPadding(0, dp(3), 0, 0);
                header.addView(sub);
            }
            content.addView(header);
        }
        currentSectionCard = new LinearLayout(this);
        currentSectionCard.setOrientation(LinearLayout.VERTICAL);
        currentSectionCard.setBackground(round(CARD, 16, LINE, dp(1)));
    }

    private void finishSection() {
        if (currentSectionCard != null) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(14);
            content.addView(currentSectionCard, lp);
            currentSectionCard = null;
        }
    }

    private void addRowToCurrentCard(View row) {
        if (currentSectionCard == null) beginSection("", null);
        if (currentSectionCard.getChildCount() > 0) {
            View divider = new View(this);
            divider.setBackgroundColor(0xff1f2628);
            LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(-1, dp(1));
            divLp.setMargins(dp(14), 0, dp(14), 0);
            currentSectionCard.addView(divider, divLp);
        }
        currentSectionCard.addView(row);
    }

    static int[] computeHeroScaledDimensions(int srcW, int srcH, int maxDimension) {
        if (srcW <= 0 || srcH <= 0 || maxDimension <= 0) return new int[]{1, 1};
        float scale = Math.min(1f, maxDimension / (float) Math.max(srcW, srcH));
        return new int[]{
                Math.max(1, Math.round(srcW * scale)),
                Math.max(1, Math.round(srcH * scale))
        };
    }

    /**
     * Computes uniform cover scaling and translation so portrait/square hero previews frame the
     * upper-middle region (~1/6 to 4/6 down from the top, centered around 5/12) instead of stretching.
     */
    static float[] computeHeroUpperMiddleTransform(int bmpW, int bmpH, int viewW, int viewH) {
        if (bmpW <= 0 || bmpH <= 0 || viewW <= 0 || viewH <= 0) {
            return new float[]{1f, 0f, 0f};
        }
        float scale = Math.max(viewW / (float) bmpW, viewH / (float) bmpH);
        float scaledW = bmpW * scale;
        float scaledH = bmpH * scale;
        float dx = (viewW - scaledW) * 0.5f;
        float anchorFraction = 5f / 12f; // Midpoint of [1/6, 4/6] from top
        float idealDy = viewH * 0.5f - scaledH * anchorFraction;
        float minDy = viewH - scaledH;
        float dy = Math.max(minDy, Math.min(0f, idealDy));
        return new float[]{scale, dx, dy};
    }

    private void setHeroBitmap(Bitmap bitmap) {
        currentHeroBitmap = bitmap;
        if (heroImageView == null) return;
        heroImageView.setImageBitmap(bitmap);
        applyHeroImageMatrix();
    }

    private void applyHeroImageMatrix() {
        if (heroImageView == null || currentHeroBitmap == null || currentHeroBitmap.isRecycled()) return;
        int viewW = heroImageView.getWidth();
        int viewH = heroImageView.getHeight();
        if (viewW <= 0 || viewH <= 0) return;
        float[] transform = computeHeroUpperMiddleTransform(
                currentHeroBitmap.getWidth(), currentHeroBitmap.getHeight(), viewW, viewH);
        Matrix matrix = new Matrix();
        matrix.setScale(transform[0], transform[0]);
        matrix.postTranslate(transform[1], transform[2]);
        heroImageView.setImageMatrix(matrix);
    }

    private void addEditorHero() {
        LinearLayout previewCard = new LinearLayout(this);
        previewCard.setOrientation(LinearLayout.VERTICAL);
        previewCard.setBackground(round(CARD, 16, LINE, dp(1)));
        previewCard.setPadding(dp(8), dp(8), dp(8), dp(9));

        FrameLayout frame = new FrameLayout(this);
        frame.setBackground(round(0xff0b0d0e, 12, LINE, dp(1)));
        frame.setClipToOutline(true);

        heroImageView = new ImageView(this);
        heroImageView.setScaleType(ImageView.ScaleType.MATRIX);
        heroImageView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if ((right - left) != (oldRight - oldLeft) || (bottom - top) != (oldBottom - oldTop)) {
                applyHeroImageMatrix();
            }
        });
        frame.addView(heroImageView, new FrameLayout.LayoutParams(-1, dp(192)));

        currentHeroBitmap = null;
        heroPlaceholderView = new PlaceholderArtwork();
        heroImageView.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xff283237, 0xff131a1c, 0xff090b0c}));
        frame.addView(heroPlaceholderView, new FrameLayout.LayoutParams(-1, dp(192)));

        LinearLayout caption = new LinearLayout(this);
        caption.setOrientation(LinearLayout.VERTICAL);
        caption.setPadding(dp(12), dp(22), dp(12), dp(10));
        caption.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00080b0c, 0xb8080b0c}));

        LinearLayout captionTop = new LinearLayout(this);
        captionTop.setGravity(Gravity.CENTER_VERTICAL);
        boolean hasCachedHero = baseHeroBitmap != null && !baseHeroBitmap.isRecycled();
        heroShotLabel = text(hasCachedHero ? "LAST FRAME · HOLD TO COMPARE" : "READY TO DEVELOP", 8, ACCENT, true);
        heroShotLabel.setLetterSpacing(.15f);
        captionTop.addView(heroShotLabel, new LinearLayout.LayoutParams(0, -2, 1));

        heroCompareBadge = text("ORIGINAL", 8, 0xfff0f2eb, true);
        heroCompareBadge.setBackground(round(0x88000000, 6, 0xffffffff, dp(1)));
        heroCompareBadge.setPadding(dp(6), dp(2), dp(6), dp(2));
        heroCompareBadge.setVisibility(View.GONE);
        captionTop.addView(heroCompareBadge);
        caption.addView(captionTop);

        TextView look = text(AppSettings.brain(this), 10, 0xfff1f2ec, true);
        LinearLayout.LayoutParams lookLp = new LinearLayout.LayoutParams(-1, -2);
        lookLp.topMargin = dp(3);
        caption.addView(look, lookLp);

        FrameLayout.LayoutParams captionLp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        frame.addView(caption, captionLp);

        if (hasCachedHero) {
            heroPlaceholderView.setVisibility(View.GONE);
            if (heroGradedBitmap != null && !heroGradedBitmap.isRecycled() && heroCompare == HeroCompare.GRADED) {
                setHeroBitmap(heroGradedBitmap);
            } else {
                setHeroBitmap(baseHeroBitmap);
                triggerHeroUpdate();
            }
        } else if (!heroLoadStarted) {
            heroLoadStarted = true;
            final int targetW = dp(640);
            final int targetH = dp(400);
            final android.content.Context appContext = getApplicationContext();
            heroWorker.execute(() -> {
                Uri latest = latestScopeImage(appContext);
                Bitmap base = latest == null ? null : decodePreview(appContext, latest, targetW, targetH);
                Bitmap scaled = null;
                if (base != null) {
                    int[] scaledDims = computeHeroScaledDimensions(base.getWidth(), base.getHeight(), 480);
                    scaled = (scaledDims[0] == base.getWidth() && scaledDims[1] == base.getHeight())
                            ? base.copy(Bitmap.Config.ARGB_8888, false)
                            : Bitmap.createScaledBitmap(base, scaledDims[0], scaledDims[1], true);
                }
                final Bitmap finalBase = base;
                final Bitmap finalScaled = scaled;
                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed() || heroJob.isClosed()) {
                        if (finalScaled != null && finalScaled != finalBase && !finalScaled.isRecycled()) finalScaled.recycle();
                        if (finalBase != null && !finalBase.isRecycled()) finalBase.recycle();
                        return;
                    }
                    baseHeroBitmap = finalBase;
                    cachedHeroSource = finalScaled;
                    if (finalBase != null) {
                        if (heroPlaceholderView != null) heroPlaceholderView.setVisibility(View.GONE);
                        if (heroShotLabel != null) heroShotLabel.setText("LAST FRAME · HOLD TO COMPARE");
                        setHeroBitmap(finalBase);
                        triggerHeroUpdate();
                    }
                });
            });
        }

        // Touch-and-hold to compare on the hero preview
        frame.setOnTouchListener((v, event) -> {
            if (baseHeroBitmap == null) return false;
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (hapticsEnabled) {
                    frame.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                }
                heroCompare = HeroCompare.ORIGINAL;
                setHeroBitmap(baseHeroBitmap);
                heroCompareBadge.setVisibility(View.VISIBLE);
                return true;
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                heroCompare = HeroCompare.GRADED;
                heroCompareBadge.setVisibility(View.GONE);
                if (heroGradedBitmap != null && !heroGradedBitmap.isRecycled()) setHeroBitmap(heroGradedBitmap);
                else triggerHeroUpdate();
                return true;
            }
            return false;
        });

        previewCard.addView(frame);

        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(-1, -2);
        previewLp.bottomMargin = dp(14);
        content.addView(previewCard, previewLp);

        // Tone quick vertical sliders card
        LinearLayout toneCard = new LinearLayout(this);
        toneCard.setOrientation(LinearLayout.VERTICAL);
        toneCard.setBackground(round(CARD, 16, LINE, dp(1)));
        toneCard.setPadding(dp(14), dp(12), dp(14), dp(12));

        TextView toneTitle = text("TONE PREVIEW", 9, ACCENT, true);
        toneTitle.setLetterSpacing(.15f);
        toneCard.addView(toneTitle);

        TextView toneHint = text("Drag sliders to preview look response in real time", 8, 0xff89948e, false);
        toneHint.setPadding(0, dp(3), 0, dp(9));
        toneCard.addView(toneHint);

        LinearLayout sliders = new LinearLayout(this);
        sliders.setGravity(Gravity.CENTER);
        addVerticalControl(sliders, "HIGHLIGHTS", AppSettings.HIGHLIGHTS, 0, 100, AppSettings.DEFAULT_HIGHLIGHTS);
        addVerticalControl(sliders, "SHADOWS", AppSettings.SHADOWS, 0, 100, AppSettings.DEFAULT_SHADOWS);
        addVerticalControl(sliders, "CONTRAST", AppSettings.CONTRAST, 50, 150, AppSettings.DEFAULT_CONTRAST);
        toneCard.addView(sliders, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout.LayoutParams toneLp = new LinearLayout.LayoutParams(-1, -2);
        toneLp.bottomMargin = dp(14);
        content.addView(toneCard, toneLp);
    }

    private void triggerHeroUpdate() {
        if (heroJob.isClosed() || heroImageView == null || cachedHeroSource == null || cachedHeroSource.isRecycled()) return;
        final long ticket = heroJob.begin(JobState.Phase.RENDERING);
        if (heroRender != null) mainHandler.removeCallbacks(heroRender);
        heroRender = () -> {
            if (!heroJob.accepts(ticket) || cachedHeroSource == null || cachedHeroSource.isRecycled()) return;
            final Bitmap source = cachedHeroSource.copy(Bitmap.Config.ARGB_8888, true);
            final RawDeveloper.Look recipe = RawDeveloper.Look.fromPreferences(this);
            heroWorker.execute(() -> {
                try {
                    if (!heroJob.accepts(ticket)) return;
                    Bitmap developed = RawDeveloper.developBitmap(source, recipe, true);
                    mainHandler.post(() -> {
                        if (!heroJob.accepts(ticket) || isDestroyed()) { if (developed != null) developed.recycle(); return; }
                        heroJob.move(ticket, developed == null ? JobState.Phase.FAILED : JobState.Phase.READY);
                        if (developed == null) return;
                        Bitmap old = heroGradedBitmap; heroGradedBitmap = developed;
                        if (heroCompare == HeroCompare.GRADED) setHeroBitmap(developed);
                        if (old != null && old != developed && !old.isRecycled()) old.recycle();
                    });
                } catch (Exception error) { heroJob.move(ticket, JobState.Phase.FAILED); Log.w(TAG, "Hero preview failed", error); }
                finally { if (source != null && !source.isRecycled()) source.recycle(); }
            });
        };
        mainHandler.postDelayed(heroRender, 60);
    }

    private void addVerticalControl(LinearLayout row, String label, String key, int min, int max, int fallback) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView name = text(label, 7, 0xffaeb5ad, true);
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(1);
        column.addView(name, new LinearLayout.LayoutParams(-1, dp(21)));
        int valueNow = Math.max(min, Math.min(max, AppSettings.getInt(this, key, fallback)));
        TextView value = text(String.valueOf(valueNow), 8, ACCENT, true);
        final boolean[] fineMode = {false};
        value.setGravity(Gravity.CENTER);
        column.addView(value, new LinearLayout.LayoutParams(-1, dp(18)));
        VerticalSlider slider = new VerticalSlider();
        slider.setRange(min, max);
        slider.setValue(valueNow);
        slider.setOnModeChanged(fine -> {
            fineMode[0] = fine;
            value.setText(AppSettings.getInt(SettingsActivity.this, key, fallback) + (fine ? " · FINE" : ""));
        });
        slider.setOnValueChanged(next -> {
            value.setText(next + (fineMode[0] ? " · FINE" : ""));
            AppSettings.prefs(SettingsActivity.this).edit().putInt(key, next).apply();
            triggerHeroUpdate();
        });
        LinearLayout.LayoutParams sliderLp = new LinearLayout.LayoutParams(-1, dp(122));
        column.addView(slider, sliderLp);
        row.addView(column, new LinearLayout.LayoutParams(0, -2, 1));
    }

    private static Uri latestScopeImage(android.content.Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            String[] columns = {MediaStore.Images.Media._ID};
            try (Cursor cursor = context.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    columns, "(" + MediaStore.Images.Media.RELATIVE_PATH + " LIKE ? OR "
                            + MediaStore.Images.Media.RELATIVE_PATH + " LIKE ?) AND "
                            + MediaStore.Images.Media.MIME_TYPE + "=?",
                    new String[]{Environment.DIRECTORY_PICTURES + "/Lika Lens/%",
                            Environment.DIRECTORY_PICTURES + "/SCOPE/%", "image/jpeg"},
                    MediaStore.Images.Media.DATE_ADDED + " DESC")) {
                if (cursor != null && cursor.moveToFirst()) {
                    long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID));
                    return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "Failed to query latest Scope image from MediaStore", e);
            }
        }
        java.util.List<File> candidates = new java.util.ArrayList<>();
        File pictures = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        for (String folderName : new String[]{"Lika Lens", "SCOPE"}) {
            File folder = new File(pictures, folderName);
            File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.US).endsWith(".jpg"));
            if (files != null) java.util.Collections.addAll(candidates, files);
        }
        if (candidates.isEmpty()) return null;
        candidates.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return Uri.fromFile(candidates.get(0));
    }

    private static Bitmap decodePreview(android.content.Context context, Uri uri, int targetWidth, int targetHeight) {
        try (InputStream boundsStream = context.getContentResolver().openInputStream(uri)) {
            if (boundsStream == null) return null;
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(boundsStream, null, bounds);
            int sample = 1;
            while (bounds.outWidth / sample > targetWidth * 2 || bounds.outHeight / sample > targetHeight * 2) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            try (InputStream imageStream = context.getContentResolver().openInputStream(uri)) {
                Bitmap decoded = imageStream == null ? null : BitmapFactory.decodeStream(imageStream, null, options);
                if (decoded == null) return null;
                int exifDegrees = GifMediaHelper.getExifOrientation(context, uri);
                return exifDegrees != 0 ? GifMediaHelper.rotateBitmap(decoded, exifDegrees) : decoded;
            }
        } catch (Exception ignored) { return null; }
    }

    private GradientDrawable round(int color, int radius, int strokeColor, int strokeWidth) {
        return UiKit.round(this, color, radius, strokeColor, strokeWidth);
    }

    private void addChoice(String title, String subtitle, String key, String[] options, String defaultValue) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(title, 11, 0xffe6eae4, true));
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView detail = text(subtitle, 8, 0xff89948e, false);
            detail.setPadding(0, dp(3), 0, 0);
            copy.addView(detail);
        }
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        String current = AppSettings.get(this, key, defaultValue);
        if (AppSettings.VOLUME_ROCKER.equals(key)) {
            if ("Shutter release".equals(current)) current = "Shutter";
            else if ("Zoom 1× / 2×".equals(current)) current = "Zoom";
        }
        String display = shortValue(current);
        if (AppSettings.FILE_PREFIX.equals(key) && current.startsWith("CUSTOM")) {
            display = "CUSTOM: " + AppSettings.get(this, AppSettings.CUSTOM_PREFIX_VALUE, "LIKA");
        }
        TextView value = pill(display, false);
        row.addView(value, new LinearLayout.LayoutParams(-2, dp(32)));
        row.setOnClickListener(v -> showChoiceDialog(title, key, options, defaultValue, value));
        addRowToCurrentCard(row);
    }

    private void showCustomPrefixDialog(TextView valueView) {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        }

        FrameLayout scrim = new FrameLayout(this);
        scrim.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scrim.setBackgroundColor(0x77000000);
        scrim.setOnClickListener(v -> dialog.dismiss());

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setClickable(true);
        box.setBackground(round(0xff0e1314, 18, 0xff2a3233, dp(1)));
        box.setPadding(dp(20), dp(18), dp(20), dp(18));

        TextView head = text("CUSTOM FILE PREFIX", 10, ACCENT, true);
        head.setLetterSpacing(.14f);
        box.addView(head);

        android.widget.EditText input = new android.widget.EditText(this);
        input.setText(AppSettings.get(this, AppSettings.CUSTOM_PREFIX_VALUE, "LIKA"));
        input.setTextColor(0xfff0f2eb);
        input.setHintTextColor(0xff6b746f);
        input.setHint("e.g. SCENE01");
        input.setSingleLine(true);
        input.setBackground(round(0xff161c1d, 10, 0xff323b3c, dp(1)));
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams inLp = new LinearLayout.LayoutParams(-1, -2);
        inLp.topMargin = dp(12);
        inLp.bottomMargin = dp(14);
        box.addView(input, inLp);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        TextView cancel = pill("CANCEL", false);
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(0, dp(36), 1f);
        cLp.rightMargin = dp(6);
        actions.addView(cancel, cLp);
        cancel.setOnClickListener(v -> dialog.dismiss());

        TextView save = pill("SAVE PREFIX", true);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(0, dp(36), 1f);
        sLp.leftMargin = dp(6);
        actions.addView(save, sLp);
        save.setOnClickListener(v -> {
            String raw = input.getText() == null ? "" : input.getText().toString().trim().toUpperCase(Locale.US);
            String clean = raw.replaceAll("[^A-Z0-9_-]", "");
            if (clean.isEmpty()) clean = "LIKA";
            if (clean.length() > 12) clean = clean.substring(0, 12);
            AppSettings.prefs(this).edit()
                    .putString(AppSettings.FILE_PREFIX, "CUSTOM…")
                    .putString(AppSettings.CUSTOM_PREFIX_VALUE, clean)
                    .apply();
            valueView.setText("CUSTOM: " + clean);
            dialog.dismiss();
        });
        box.addView(actions, new LinearLayout.LayoutParams(-1, -2));

        int dialogWidth = Math.min(dp(400), Math.round(getResources().getDisplayMetrics().widthPixels * 0.88f));
        FrameLayout.LayoutParams boxLp = new FrameLayout.LayoutParams(
                dialogWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        scrim.addView(box, boxLp);
        dialog.setContentView(scrim);
        dialog.show();
    }

    private static String choiceOptionHint(String key, String option) {
        if (!AppSettings.PHOTO_OUTPUT.equals(key) || option == null) return null;
        if (AppSettings.PHOTO_OUTPUT_JPEG_ONLY.equals(option)) {
            return "Saves graded JPEG to Gallery + private RAW cache for in-app re-editing";
        }
        if (AppSettings.PHOTO_OUTPUT_JPEG_DNG.equals(option)) {
            return "Saves graded JPEG, RAW edit cache, and ungraded DNG to Pictures/Lika Lens/RAW";
        }
        if (AppSettings.PHOTO_OUTPUT_DNG_ONLY.equals(option)) {
            return "Saves DNG to Pictures/Lika Lens/RAW only (not in Lika Lens grid; open in Photos/RAW apps)";
        }
        return null;
    }

    private void showChoiceDialog(String title, String key, String[] options, String defaultValue, TextView valueView) {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        }

        FrameLayout scrim = new FrameLayout(this);
        scrim.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scrim.setBackgroundColor(0x77000000);
        scrim.setOnClickListener(v -> dialog.dismiss());

        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setClickable(true);
        sheet.setBackground(round(0xff0e1314, 20, 0xff2a3233, dp(1)));
        sheet.setPadding(dp(20), dp(16), dp(20), dp(20));

        View handle = new View(this);
        handle.setBackground(round(0xff404846, 2, 0, 0));
        LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(dp(36), dp(4));
        handleLp.gravity = Gravity.CENTER_HORIZONTAL;
        handleLp.bottomMargin = dp(14);
        sheet.addView(handle, handleLp);

        TextView titleView = text(title.toUpperCase(Locale.US), 10, ACCENT, true);
        titleView.setLetterSpacing(.14f);
        titleView.setGravity(Gravity.CENTER);
        sheet.addView(titleView);

        View spacer = new View(this);
        sheet.addView(spacer, new LinearLayout.LayoutParams(-1, dp(12)));

        ScrollView optScroll = new ScrollView(this);
        optScroll.setVerticalScrollBarEnabled(false);
        LinearLayout optionsList = new LinearLayout(this);
        optionsList.setOrientation(LinearLayout.VERTICAL);

        String selected = AppSettings.get(this, key, defaultValue);
        if (AppSettings.VOLUME_ROCKER.equals(key)) {
            if ("Shutter release".equals(selected)) selected = "Shutter";
            else if ("Zoom 1× / 2×".equals(selected)) selected = "Zoom";
        }
        boolean hasOptionHints = AppSettings.PHOTO_OUTPUT.equals(key);
        for (String option : options) {
            boolean isCurrent = option.equals(selected);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(13), dp(14), dp(13));
            row.setBackground(round(isCurrent ? 0x22d7b574 : 0x00000000, 10, isCurrent ? 0x66d7b574 : 0, isCurrent ? dp(1) : 0));

            String hint = choiceOptionHint(key, option);
            if (hint != null) {
                LinearLayout labelCol = new LinearLayout(this);
                labelCol.setOrientation(LinearLayout.VERTICAL);
                labelCol.addView(text(option, 11, isCurrent ? ACCENT : 0xffe6eae4, isCurrent));
                TextView hintView = text(hint, 8, 0xff89948e, false);
                hintView.setPadding(0, dp(3), 0, 0);
                labelCol.addView(hintView);
                LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0, -2, 1f);
                labelLp.rightMargin = dp(8);
                row.addView(labelCol, labelLp);
            } else {
                TextView label = text(option, 11, isCurrent ? ACCENT : 0xffe6eae4, isCurrent);
                LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0, -2, 1f);
                labelLp.rightMargin = dp(8);
                row.addView(label, labelLp);
            }

            if (isCurrent) {
                TextView check = text("✓", 12, ACCENT, true);
                row.addView(check);
            }

            row.setOnClickListener(v -> {
                if (hapticsEnabled) {
                    row.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                }
                if (AppSettings.FILE_PREFIX.equals(key) && option.startsWith("CUSTOM")) {
                    dialog.dismiss();
                    showCustomPrefixDialog(valueView);
                    return;
                }
                AppSettings.prefs(this).edit().putString(key, option).apply();
                if (AppSettings.WHITE_BALANCE_MODE.equals(key)) {
                    int kelvin = AppSettings.parseWhiteBalanceModeKelvin(option);
                    AppSettings.setWhiteBalanceKelvin(this, kelvin);
                }
                if (AppSettings.VIDEO_CODEC.equals(key) && !option.contains("HEVC")
                        && AppSettings.getBoolean(this, AppSettings.VIDEO_HDR, false)) {
                    AppSettings.prefs(this).edit().putBoolean(AppSettings.VIDEO_HDR, false).apply();
                    Toast.makeText(this, "Turned off 10-bit HLG HDR because HDR requires HEVC.", Toast.LENGTH_LONG).show();
                    rebuildContent(false);
                } else if (AppSettings.EDITABLE_ARCHIVE_LIMIT.equals(key)) {
                    final android.content.Context appContext = getApplicationContext();
                    final String chosenLimit = option;
                    heroWorker.execute(() -> {
                        RawArchive.enforceStorageLimit(appContext, chosenLimit, null);
                        mainHandler.post(() -> {
                            if (!isFinishing() && !isDestroyed()) rebuildContent(false);
                        });
                    });
                } else {
                    valueView.setText(shortValue(option));
                }
                triggerHeroUpdate();
                dialog.dismiss();
            });

            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
            rowLp.bottomMargin = dp(4);
            optionsList.addView(row, rowLp);
        }
        optScroll.addView(optionsList, new FrameLayout.LayoutParams(-1, -2));
        int rowEstimateDp = hasOptionHints ? 76 : 52;
        sheet.addView(optScroll, new LinearLayout.LayoutParams(-1, dp(Math.min(380, options.length * rowEstimateDp + 12))));

        TextView cancel = pill("DONE", false);
        cancel.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(-1, dp(38));
        cancelLp.topMargin = dp(12);
        sheet.addView(cancel, cancelLp);
        cancel.setOnClickListener(v -> dialog.dismiss());

        FrameLayout.LayoutParams sheetLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
        );
        scrim.addView(sheet, sheetLp);
        dialog.setContentView(scrim);
        dialog.show();
    }

    private String shortValue(String value) {
        if (value.length() > 25) return value.substring(0, 22) + "…";
        return value;
    }

    private void addSlider(String title, String subtitle, String key, int min, int max, int defaultValue) {
        int currentVal = AppSettings.getInt(this, key, defaultValue);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(10));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = text(title, 11, 0xffe6eae4, true);
        top.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        TextView value = text(sliderValue(key, currentVal), 9, ACCENT, true);
        value.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        top.addView(value);
        row.addView(top);

        if (subtitle != null && !subtitle.isEmpty()) {
            TextView detail = text(subtitle, 8, 0xff89948e, false);
            detail.setPadding(0, dp(2), 0, 0);
            row.addView(detail);
        }

        SeekBar seek = new SeekBar(this);
        seek.setMax(max - min);
        seek.setProgress(Math.max(0, Math.min(max - min, currentVal - min)));
        seek.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        seek.setThumbTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        seek.setPadding(dp(1), dp(4), dp(1), 0);
        row.addView(seek, new LinearLayout.LayoutParams(-1, dp(32)));

        final int[] lastHapticVal = {currentVal};
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int selected = min + progress;
                value.setText(sliderValue(key, selected));
                if (fromUser) {
                    AppSettings.prefs(SettingsActivity.this).edit().putInt(key, selected).apply();
                    triggerHeroUpdate();
                    if (hapticsEnabled) {
                        boolean isLandmark = false;
                        if (AppSettings.EXPOSURE.equals(key) && selected == 0) isLandmark = true;
                        else if (AppSettings.WARMTH.equals(key) && selected == 50) isLandmark = true;
                        else if (AppSettings.CONTRAST.equals(key) && selected == 100) isLandmark = true;
                        else if (AppSettings.PIVOT.equals(key) && selected == 50) isLandmark = true;
                        else if (AppSettings.SATURATION.equals(key) && selected == 100) isLandmark = true;

                        if (isLandmark) {
                            seekBar.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                            lastHapticVal[0] = selected;
                        } else if (Math.abs(selected - lastHapticVal[0]) >= 6) {
                            seekBar.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                            lastHapticVal[0] = selected;
                        }
                    }
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        addRowToCurrentCard(row);
    }

    private String sliderValue(String key, int value) {
        if (AppSettings.EXPOSURE.equals(key)) return String.format(java.util.Locale.US, "%+.1f EV", value / 10f);
        if (AppSettings.WARMTH.equals(key)) return value == 50 ? "0" : value > 50 ? "WARM +" + (value - 50) : "COOL " + (50 - value);
        return String.valueOf(value);
    }

    private void addSwitch(String title, String subtitle, String key, boolean fallback) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(title, 11, 0xffe6eae4, true));
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView detail = text(subtitle, 8, 0xff89948e, false);
            detail.setPadding(0, dp(3), 0, 0);
            copy.addView(detail);
        }
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        Switch toggle = new Switch(this);
        toggle.setButtonTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        final boolean isLocation = AppSettings.LOCATION.equals(key);
        boolean hasLocationPerm = !isLocation
                || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        toggle.setChecked(AppSettings.getBoolean(this, key, fallback) && hasLocationPerm);
        toggle.setOnCheckedChangeListener((button, checked) -> {
            if (AppSettings.HAPTICS.equals(key)) hapticsEnabled = checked;
            if (hapticsEnabled) {
                toggle.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            }
            if (isLocation && checked) {
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                        && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1002);
                }
            }
            AppSettings.prefs(this).edit().putBoolean(key, checked).apply();
            if (AppSettings.VIDEO_HDR.equals(key)) {
                if (checked) {
                    String curCodec = AppSettings.get(this, AppSettings.VIDEO_CODEC, "H.264 · Universal");
                    if (!curCodec.contains("HEVC")) {
                        AppSettings.prefs(this).edit().putString(AppSettings.VIDEO_CODEC, "HEVC (H.265) · High Efficiency").apply();
                        Toast.makeText(this, "10-bit HLG HDR requires HEVC — switched codec to HEVC (H.265).", Toast.LENGTH_LONG).show();
                    }
                }
                rebuildContent(false);
            }
            triggerHeroUpdate();
        });
        row.addView(toggle);
        addRowToCurrentCard(row);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1002) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            AppSettings.prefs(this).edit().putBoolean(AppSettings.LOCATION, granted).apply();
            rebuildContent();
        }
    }

    private void addInfo(String title, String detailText, int color) {
        addInfo(title, detailText, color, null);
    }

    private void addInfo(String title, String detailText, int color, Runnable onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(14), dp(10), dp(14), dp(10));
        row.addView(text(title, 10, 0xffe6eae4, true));
        TextView detail = text(detailText, 8, color, false);
        detail.setPadding(0, dp(4), 0, 0);
        row.addView(detail);
        if (onClick != null) {
            row.setOnClickListener(v -> onClick.run());
        }
        addRowToCurrentCard(row);
    }

    private void addAdvancedSection(String advKey, String title, String subtitle, Runnable buildRows) {
        finishSection();
        boolean expanded = expandedAdvanced.contains(advKey);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(round(CARD, 16, LINE, dp(1)));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(12), dp(14), dp(12));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(title, 11, 0xffe6eae4, true));
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView detail = text(subtitle, 8, 0xff89948e, false);
            detail.setPadding(0, dp(3), 0, 0);
            copy.addView(detail);
        }
        header.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView togglePill = pill(expanded ? "HIDE ▴" : "SHOW ▾", expanded);
        LinearLayout.LayoutParams pillLp = new LinearLayout.LayoutParams(-2, dp(30));
        pillLp.leftMargin = dp(8);
        header.addView(togglePill, pillLp);
        card.addView(header);

        View topDivider = new View(this);
        topDivider.setBackgroundColor(0xff1f2628);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(-1, dp(1));
        divLp.setMargins(dp(14), 0, dp(14), 0);
        topDivider.setVisibility(expanded ? View.VISIBLE : View.GONE);
        card.addView(topDivider, divLp);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setVisibility(expanded ? View.VISIBLE : View.GONE);
        if (buildRows != null) {
            LinearLayout previousCard = currentSectionCard;
            currentSectionCard = body;
            buildRows.run();
            currentSectionCard = previousCard;
        }
        card.addView(body, new LinearLayout.LayoutParams(-1, -2));

        header.setOnClickListener(v -> {
            if (hapticsEnabled) {
                header.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            }
            boolean nextExpanded;
            if (expandedAdvanced.contains(advKey)) {
                expandedAdvanced.remove(advKey);
                nextExpanded = false;
            } else {
                expandedAdvanced.add(advKey);
                nextExpanded = true;
            }
            topDivider.setVisibility(nextExpanded ? View.VISIBLE : View.GONE);
            body.setVisibility(nextExpanded ? View.VISIBLE : View.GONE);
            togglePill.setText(nextExpanded ? "HIDE ▴" : "SHOW ▾");
            togglePill.setTextColor(nextExpanded ? ACCENT : 0xffd8ded7);
            togglePill.setBackground(UiKit.round(this,
                    nextExpanded ? 0xff1d1d1a : 0xff202626, 20f,
                    nextExpanded ? ACCENT : 0xff39413e, dp(1)));
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(14);
        content.addView(card, lp);
    }

    private void addCustomRecipeRow(int slot) {
        String recipeName = AppSettings.getCustomRecipeName(this, slot);
        String recipeData = AppSettings.getCustomRecipeData(this, slot);
        boolean hasRecipe = recipeName != null && !recipeName.trim().isEmpty() && recipeData != null && !recipeData.trim().isEmpty();

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        if (hasRecipe) {
            RawDeveloper.Look parsed = RawDeveloper.Look.fromJson(recipeData);
            String baseBrain = (parsed != null && parsed.brain != null && !parsed.brain.isEmpty())
                    ? parsed.brain : AppSettings.BRAIN_LIKA_FILM;
            copy.addView(text("Recipe " + slot + " · " + recipeName, 11, 0xffe6eae4, true));
            TextView detail = text("Base: " + baseBrain, 8, 0xff89948e, false);
            detail.setPadding(0, dp(3), 0, 0);
            copy.addView(detail);
            row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));

            TextView renameBtn = pill("RENAME", false);
            LinearLayout.LayoutParams renLp = new LinearLayout.LayoutParams(-2, dp(30));
            renLp.leftMargin = dp(6);
            row.addView(renameBtn, renLp);
            renameBtn.setOnClickListener(v -> showRenameRecipeDialog(slot, recipeName, recipeData));

            TextView deleteBtn = pill("DELETE", false);
            LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(-2, dp(30));
            delLp.leftMargin = dp(6);
            row.addView(deleteBtn, delLp);
            deleteBtn.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("Delete recipe \"" + recipeName + "\"?")
                    .setMessage("This clears custom recipe slot " + slot + ". Saved photos remain untouched.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete", (d, w) -> {
                        AppSettings.deleteCustomRecipe(this, slot);
                        String activeBrain = AppSettings.get(this, AppSettings.BRAIN, AppSettings.BRAIN_LIKA_FILM);
                        if (activeBrain != null && activeBrain.startsWith("[RECIPE " + slot)) {
                            AppSettings.prefs(this).edit().putString(AppSettings.BRAIN, AppSettings.BRAIN_LIKA_FILM).apply();
                        }
                        Toast.makeText(this, "Deleted recipe slot " + slot, Toast.LENGTH_SHORT).show();
                        rebuildContent(false);
                    })
                    .show());
        } else {
            copy.addView(text("Custom recipe slot " + slot, 11, 0xff9ea7a1, true));
            TextView detail = text("Empty · Save from camera Look menu or Photo Editor", 8, 0xff707a74, false);
            detail.setPadding(0, dp(3), 0, 0);
            copy.addView(detail);
            row.addView(copy, new LinearLayout.LayoutParams(-1, -2));
        }
        addRowToCurrentCard(row);
    }

    private void showRenameRecipeDialog(int slot, String currentName, String recipeData) {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        }

        FrameLayout scrim = new FrameLayout(this);
        scrim.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scrim.setBackgroundColor(0x77000000);
        scrim.setOnClickListener(v -> dialog.dismiss());

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setClickable(true);
        box.setBackground(round(0xff0e1314, 18, 0xff2a3233, dp(1)));
        box.setPadding(dp(20), dp(18), dp(20), dp(18));

        TextView head = text("RENAME RECIPE " + slot, 10, ACCENT, true);
        head.setLetterSpacing(.14f);
        box.addView(head);

        EditText input = new EditText(this);
        input.setText(currentName);
        input.setSelection(currentName.length());
        input.setTextColor(0xfff0f2eb);
        input.setHintTextColor(0xff6b746f);
        input.setHint("Recipe name");
        input.setSingleLine(true);
        input.setBackground(round(0xff161c1d, 10, 0xff323b3c, dp(1)));
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams inLp = new LinearLayout.LayoutParams(-1, -2);
        inLp.topMargin = dp(12);
        inLp.bottomMargin = dp(14);
        box.addView(input, inLp);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        TextView cancel = pill("CANCEL", false);
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(0, dp(36), 1f);
        cLp.rightMargin = dp(6);
        actions.addView(cancel, cLp);
        cancel.setOnClickListener(v -> dialog.dismiss());

        TextView save = pill("SAVE NAME", true);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(0, dp(36), 1f);
        sLp.leftMargin = dp(6);
        actions.addView(save, sLp);
        save.setOnClickListener(v -> {
            String nextName = input.getText() == null ? "" : input.getText().toString().trim();
            if (nextName.isEmpty()) nextName = "Recipe " + slot;
            AppSettings.saveCustomRecipe(this, slot, nextName, recipeData);
            dialog.dismiss();
            rebuildContent(false);
        });
        box.addView(actions, new LinearLayout.LayoutParams(-1, -2));

        int dialogWidth = Math.min(dp(400), Math.round(getResources().getDisplayMetrics().widthPixels * 0.88f));
        FrameLayout.LayoutParams boxLp = new FrameLayout.LayoutParams(
                dialogWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        scrim.addView(box, boxLp);
        dialog.setContentView(scrim);
        dialog.show();
    }

    private void addCustomLutRow(String lutName) {
        int cubeSize = LutManager.readCubeSize(this, lutName);
        String sizeLabel = cubeSize > 0 ? (cubeSize + "×" + cubeSize + "×" + cubeSize + " .cube") : "Custom .cube LUT";

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(11), dp(14), dp(11));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(lutName, 11, 0xffe6eae4, true));
        TextView detail = text("Custom LUT · " + sizeLabel, 8, 0xff89948e, false);
        detail.setPadding(0, dp(3), 0, 0);
        copy.addView(detail);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView deleteBtn = pill("DELETE", false);
        LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(-2, dp(30));
        delLp.leftMargin = dp(8);
        row.addView(deleteBtn, delLp);
        deleteBtn.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Delete imported LUT \"" + lutName + "\"?")
                .setMessage("Removes this .cube file from Lika Lens. Photos already exported are unaffected.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> {
                    LutManager.deleteCustomLut(this, lutName);
                    String activeLut = AppSettings.get(this, AppSettings.LUT_NAME, "None");
                    if (lutName.equalsIgnoreCase(activeLut)) {
                        AppSettings.prefs(this).edit().putString(AppSettings.LUT_NAME, "None").apply();
                    }
                    Toast.makeText(this, "Deleted LUT: " + lutName, Toast.LENGTH_SHORT).show();
                    triggerHeroUpdate();
                    rebuildContent(false);
                })
                .show());
        addRowToCurrentCard(row);
    }

    private void showClearArchiveDialog() {
        if (hapticsEnabled) {
            getWindow().getDecorView().performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
        String limitChoice = AppSettings.get(this, AppSettings.EDITABLE_ARCHIVE_LIMIT, AppSettings.EDITABLE_ARCHIVE_512MB);
        Dialog dialog = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        }

        FrameLayout scrim = new FrameLayout(this);
        scrim.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scrim.setBackgroundColor(0x77000000);
        scrim.setOnClickListener(v -> dialog.dismiss());

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setClickable(true);
        box.setBackground(round(0xff0e1314, 18, 0xff2a3233, dp(1)));
        box.setPadding(dp(20), dp(18), dp(20), dp(18));

        TextView head = text("CLEAR EDITABLE ORIGINALS?", 10, ACCENT, true);
        head.setLetterSpacing(.14f);
        box.addView(head);

        TextView stats = text(RawArchive.formatArchiveSummary(this, limitChoice), 10, 0xfff0f2eb, true);
        stats.setPadding(0, dp(8), 0, dp(6));
        box.addView(stats);

        TextView body = text(
                "Clearing editable originals removes cached app-private .sraw files used for RAW-based re-editing and switching film brains after capture.\n\n"
                        + "All processed JPEG photographs and explicitly saved DNG files in your Gallery stay completely untouched.",
                8.5f, 0xff9aa59f, false);
        body.setPadding(0, 0, 0, dp(16));
        box.addView(body);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        TextView cancel = pill("CANCEL", false);
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(0, dp(36), 1f);
        cLp.rightMargin = dp(6);
        actions.addView(cancel, cLp);
        cancel.setOnClickListener(v -> dialog.dismiss());

        TextView clearBtn = pill("CLEAR CACHE", true);
        LinearLayout.LayoutParams clLp = new LinearLayout.LayoutParams(0, dp(36), 1f);
        clLp.leftMargin = dp(6);
        actions.addView(clearBtn, clLp);
        clearBtn.setOnClickListener(v -> {
            dialog.dismiss();
            final android.content.Context appContext = getApplicationContext();
            heroWorker.execute(() -> {
                RawArchive.CleanupResult res = RawArchive.clearEditableOriginals(appContext);
                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    String msg;
                    if (res.deletedCount == 0 && res.skippedActiveCount == 0) {
                        msg = "Editable originals cache is already empty.";
                    } else {
                        msg = "Cleared " + res.deletedCount + " editable "
                                + (res.deletedCount == 1 ? "original" : "originals")
                                + " (" + RawArchive.formatBytes(res.freedBytes) + " freed)";
                        if (res.skippedActiveCount > 0) {
                            msg += " · " + res.skippedActiveCount + " in use kept";
                        }
                    }
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                    rebuildContent(false);
                });
            });
        });
        box.addView(actions, new LinearLayout.LayoutParams(-1, -2));

        int dialogWidth = Math.min(dp(420), Math.round(getResources().getDisplayMetrics().widthPixels * 0.90f));
        FrameLayout.LayoutParams boxLp = new FrameLayout.LayoutParams(
                dialogWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        scrim.addView(box, boxLp);
        dialog.setContentView(scrim);
        dialog.show();
    }

    private static final int REQUEST_CODE_PICK_LUT = 2055;
    private static final int REQUEST_CODE_EXPORT_BACKUP = 2056;
    private static final int REQUEST_CODE_IMPORT_BACKUP = 2057;

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
                Toast.makeText(this, "File picker unavailable", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showBackupNameDialog(boolean shareImmediately) {
        if (AppSettings.getBoolean(this, AppSettings.HAPTICS, true)) {
            getWindow().getDecorView().performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
        Dialog dialog = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        }

        FrameLayout scrim = new FrameLayout(this);
        scrim.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scrim.setBackgroundColor(0x77000000);
        scrim.setOnClickListener(v -> dialog.dismiss());

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setClickable(true);
        box.setBackground(round(0xff0e1314, 18, 0xff2a3233, dp(1)));
        box.setPadding(dp(20), dp(18), dp(20), dp(18));

        TextView head = text(shareImmediately ? "SHARE NAMED BACKUP" : "SAVE NAMED BACKUP", 10, ACCENT, true);
        head.setLetterSpacing(.14f);
        box.addView(head);

        int recipes = SettingsBackupManager.countLocalCustomRecipes(this);
        int customLuts = LutManager.getCustomLutNames(this).size();
        TextView info = text("Includes all camera & processing preferences, " + recipes
                        + (recipes == 1 ? " custom recipe" : " custom recipes") + ", and " + customLuts
                        + (customLuts == 1 ? " custom LUT." : " custom LUTs."),
                8, 0xff8d9892, false);
        info.setPadding(0, dp(6), 0, dp(10));
        box.addView(info);

        EditText input = new EditText(this);
        String defaultName = SettingsBackupManager.defaultBackupName();
        input.setText(defaultName);
        input.setSelection(defaultName.length());
        input.setTextColor(0xfff0f2eb);
        input.setHintTextColor(0xff6b746f);
        input.setHint("Backup name (e.g. Street Portra 400)");
        input.setSingleLine(true);
        input.setBackground(round(0xff161c1d, 10, 0xff323b3c, dp(1)));
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams inLp = new LinearLayout.LayoutParams(-1, -2);
        inLp.bottomMargin = dp(14);
        box.addView(input, inLp);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        TextView cancel = pill("CANCEL", false);
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(0, dp(36), 1f);
        cLp.rightMargin = dp(6);
        actions.addView(cancel, cLp);
        cancel.setOnClickListener(v -> dialog.dismiss());

        TextView confirm = pill(shareImmediately ? "SHARE" : "SAVE FILE", true);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(0, dp(36), 1f);
        sLp.leftMargin = dp(6);
        actions.addView(confirm, sLp);
        confirm.setOnClickListener(v -> {
            String chosenName = SettingsBackupManager.sanitizeBackupName(
                    input.getText() == null ? "" : input.getText().toString());
            dialog.dismiss();
            try {
                String json = SettingsBackupManager.createBackupJson(this, chosenName);
                if (shareImmediately) {
                    shareBackupFile(chosenName, json);
                } else {
                    launchBackupExportPicker(chosenName, json);
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to prepare settings backup", e);
                Toast.makeText(this, "Could not create backup: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        box.addView(actions, new LinearLayout.LayoutParams(-1, -2));

        int dialogWidth = Math.min(dp(420), Math.round(getResources().getDisplayMetrics().widthPixels * 0.90f));
        FrameLayout.LayoutParams boxLp = new FrameLayout.LayoutParams(
                dialogWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        scrim.addView(box, boxLp);
        dialog.setContentView(scrim);
        dialog.show();
    }

    private void launchBackupExportPicker(String backupName, String json) {
        pendingBackupName = backupName;
        pendingBackupJson = json;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, SettingsBackupManager.buildBackupFileName(backupName));
        try {
            startActivityForResult(intent, REQUEST_CODE_EXPORT_BACKUP);
        } catch (Exception e) {
            pendingBackupName = null;
            pendingBackupJson = null;
            Toast.makeText(this, "File saver unavailable. Try Share backup instead.", Toast.LENGTH_LONG).show();
        }
    }

    private void launchBackupImportPicker() {
        if (AppSettings.getBoolean(this, AppSettings.HAPTICS, true)) {
            getWindow().getDecorView().performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            startActivityForResult(intent, REQUEST_CODE_IMPORT_BACKUP);
        } catch (Exception e) {
            try {
                Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
                fallback.setType("*/*");
                startActivityForResult(fallback, REQUEST_CODE_IMPORT_BACKUP);
            } catch (Exception ex) {
                Toast.makeText(this, "File picker unavailable", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void shareBackupFile(String backupName, String json) {
        try {
            Uri uri = SettingsBackupManager.writeBackupForShare(this, backupName, json);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("application/json");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.putExtra(Intent.EXTRA_SUBJECT, "Lika Lens Backup: " + backupName);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "Share Lika Lens Backup"));
        } catch (Exception e) {
            Log.w(TAG, "Failed to share backup", e);
            Toast.makeText(this, "Could not share backup.", Toast.LENGTH_SHORT).show();
        }
    }

    private void showBackupSavedDialog(String backupName, String json) {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        }

        FrameLayout scrim = new FrameLayout(this);
        scrim.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scrim.setBackgroundColor(0x77000000);
        scrim.setOnClickListener(v -> dialog.dismiss());

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setClickable(true);
        box.setBackground(round(0xff0e1314, 18, 0xff2a3233, dp(1)));
        box.setPadding(dp(20), dp(18), dp(20), dp(18));

        TextView head = text("BACKUP SAVED", 10, ACCENT, true);
        head.setLetterSpacing(.14f);
        box.addView(head);

        TextView body = text("\"" + backupName + "\" has been saved.\nYou can keep it locally or share it with another device.",
                9, 0xffd8ded7, false);
        body.setPadding(0, dp(8), 0, dp(16));
        box.addView(body);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        TextView shareBtn = pill("SHARE", false);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(0, dp(36), 1f);
        sLp.rightMargin = dp(6);
        actions.addView(shareBtn, sLp);
        shareBtn.setOnClickListener(v -> {
            dialog.dismiss();
            shareBackupFile(backupName, json);
        });

        TextView doneBtn = pill("DONE", true);
        LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(0, dp(36), 1f);
        dLp.leftMargin = dp(6);
        actions.addView(doneBtn, dLp);
        doneBtn.setOnClickListener(v -> dialog.dismiss());
        box.addView(actions, new LinearLayout.LayoutParams(-1, -2));

        int dialogWidth = Math.min(dp(400), Math.round(getResources().getDisplayMetrics().widthPixels * 0.88f));
        FrameLayout.LayoutParams boxLp = new FrameLayout.LayoutParams(
                dialogWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        scrim.addView(box, boxLp);
        dialog.setContentView(scrim);
        dialog.show();
    }

    private void showImportConfirmDialog(SettingsBackupManager.BackupData data) {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        }

        FrameLayout scrim = new FrameLayout(this);
        scrim.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scrim.setBackgroundColor(0x77000000);
        scrim.setOnClickListener(v -> dialog.dismiss());

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setClickable(true);
        box.setBackground(round(0xff0e1314, 18, 0xff2a3233, dp(1)));
        box.setPadding(dp(20), dp(18), dp(20), dp(18));

        TextView head = text("RESTORE BACKUP?", 10, ACCENT, true);
        head.setLetterSpacing(.14f);
        box.addView(head);

        TextView nameView = text(data.name, 12, 0xfff0f2eb, true);
        nameView.setPadding(0, dp(8), 0, dp(4));
        box.addView(nameView);

        StringBuilder details = new StringBuilder(data.formatSummary());
        if (data.createdAt != null && !data.createdAt.isEmpty()) {
            details.append("\nCreated: ").append(data.createdAt);
        }
        details.append("\n\nThis replaces your current camera and film look preferences.");
        TextView body = text(details.toString(), 8, 0xff9aa59f, false);
        body.setPadding(0, dp(2), 0, dp(16));
        box.addView(body);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        TextView cancel = pill("CANCEL", false);
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(0, dp(36), 1f);
        cLp.rightMargin = dp(6);
        actions.addView(cancel, cLp);
        cancel.setOnClickListener(v -> dialog.dismiss());

        TextView restore = pill("RESTORE", true);
        LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(0, dp(36), 1f);
        rLp.leftMargin = dp(6);
        actions.addView(restore, rLp);
        restore.setOnClickListener(v -> {
            dialog.dismiss();
            try {
                SettingsBackupManager.RestoreResult res = SettingsBackupManager.restoreBackup(this, data);
                rebuildContent();
                String msg = "Restored \"" + res.backupName + "\"";
                if (res.failedLuts > 0) {
                    msg += " (" + res.failedLuts + " LUT failed)";
                }
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Log.w(TAG, "Failed to restore backup", e);
                Toast.makeText(this, "Could not restore backup: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        box.addView(actions, new LinearLayout.LayoutParams(-1, -2));

        int dialogWidth = Math.min(dp(420), Math.round(getResources().getDisplayMetrics().widthPixels * 0.90f));
        FrameLayout.LayoutParams boxLp = new FrameLayout.LayoutParams(
                dialogWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        scrim.addView(box, boxLp);
        dialog.setContentView(scrim);
        dialog.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_PICK_LUT && resultCode == RESULT_OK && data != null && data.getData() != null) {
            final Uri uri = data.getData();
            final android.content.Context appContext = getApplicationContext();
            heroWorker.execute(() -> {
                String name = LutManager.importLut(appContext, uri);
                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (name != null) {
                        AppSettings.prefs(this).edit().putString(AppSettings.LUT_NAME, name).apply();
                        Toast.makeText(this, "Imported 3D LUT: " + name, Toast.LENGTH_SHORT).show();
                        rebuildContent();
                    } else {
                        Toast.makeText(this, "Could not import LUT. Ensure valid .cube format.", Toast.LENGTH_LONG).show();
                    }
                });
            });
        } else if (requestCode == REQUEST_CODE_EXPORT_BACKUP) {
            String exportName = pendingBackupName;
            String exportJson = pendingBackupJson;
            pendingBackupName = null;
            pendingBackupJson = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null && exportJson != null) {
                try {
                    SettingsBackupManager.writeTextToUri(this, data.getData(), exportJson);
                    showBackupSavedDialog(exportName != null ? exportName : "Lika Lens Backup", exportJson);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to write backup file", e);
                    Toast.makeText(this, "Could not save backup: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        } else if (requestCode == REQUEST_CODE_IMPORT_BACKUP && resultCode == RESULT_OK && data != null && data.getData() != null) {
            try {
                String json = SettingsBackupManager.readTextFromUri(this, data.getData());
                SettingsBackupManager.BackupData parsed = SettingsBackupManager.parseBackup(json);
                showImportConfirmDialog(parsed);
            } catch (Exception e) {
                Log.w(TAG, "Failed to read/parse imported backup", e);
                Toast.makeText(this, "Invalid backup file: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    private TextView text(String value, float size, int color, boolean bold) {
        return UiKit.text(this, value, size, color, bold);
    }

    private TextView pill(String value, boolean active) {
        TextView view = UiKit.pill(this, value, active, 8f, 20f,
                ACCENT, 0xffd8ded7, 0xff1d1d1a, 0xff202626, ACCENT, 0xff39413e);
        view.setPadding(dp(9), 0, dp(9), 0);
        return view;
    }

    private interface OnValueChanged { void onChanged(int value); }
    private interface OnModeChanged { void onChanged(boolean fine); }

    private final class VerticalSlider extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path trackClip = new Path();
        private final RectF trackRect = new RectF();
        private final RectF thumbRect = new RectF();
        private int min = 0;
        private int max = 100;
        private int value;
        private OnValueChanged listener;
        private OnModeChanged modeListener;
        private boolean tracking;
        private boolean fineMode;
        private float lastY;
        private float fineStartY;
        private int fineStartValue;
        private final Runnable enterFineMode = () -> {
            if (!tracking || fineMode) return;
            fineMode = true;
            fineStartY = lastY;
            fineStartValue = value;
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            if (modeListener != null) modeListener.onChanged(true);
            invalidate();
        };

        VerticalSlider() {
            super(SettingsActivity.this);
            setClickable(true);
            setContentDescription("Vertical tone control");
        }

        void setRange(int min, int max) {
            this.min = min;
            this.max = Math.max(min + 1, max);
            this.value = Math.max(this.min, Math.min(this.max, this.value));
            invalidate();
        }

        void setValue(int value) { this.value = Math.max(min, Math.min(max, value)); invalidate(); }
        void setOnValueChanged(OnValueChanged listener) { this.listener = listener; }
        void setOnModeChanged(OnModeChanged listener) { this.modeListener = listener; }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() * 0.5f;
            float outerTop = dp(5);
            float outerBottom = getHeight() - dp(5);
            float trackHalfW = dp(15);
            float thumbHalfH = dp(6);
            float rangeTop = outerTop + thumbHalfH + dp(4);
            float rangeBottom = outerBottom - thumbHalfH - dp(4);
            float fraction = (value - min) / (float) Math.max(1, max - min);
            float thumbY = rangeBottom - (rangeBottom - rangeTop) * fraction;

            trackRect.set(cx - trackHalfW, outerTop, cx + trackHalfW, outerBottom);
            paint.setColor(0xff202626);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(trackRect, dp(11), dp(11), paint);

            // Subtle level fill clipped cleanly inside the rounded track housing
            canvas.save();
            trackClip.reset();
            trackClip.addRoundRect(trackRect, dp(11), dp(11), Path.Direction.CW);
            canvas.clipPath(trackClip);
            paint.setColor(0x28d7b574);
            canvas.drawRect(trackRect.left, thumbY, trackRect.right, trackRect.bottom, paint);

            // Center guide hairline + symmetrical scale markings directly on the slider track
            paint.setColor(0x3a8d9892);
            canvas.drawRect(cx - dp(0.5f), rangeTop, cx + dp(0.5f), rangeBottom, paint);
            for (int i = 0; i <= 10; i++) {
                float y = rangeBottom - (rangeBottom - rangeTop) * (i / 10f);
                boolean major = i % 5 == 0;
                float halfTick = major ? dp(9) : dp(5);
                float halfThickness = major ? dp(0.75f) : dp(0.5f);
                paint.setColor(major ? 0xff9aa49c : 0xff66706a);
                canvas.drawRect(cx - halfTick, y - halfThickness, cx + halfTick, y + halfThickness, paint);
            }
            canvas.restore();

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(fineMode ? ACCENT : 0xff343b39);
            canvas.drawRoundRect(trackRect, dp(11), dp(11), paint);

            // Compact, well-proportioned handle cap fitted cleanly over the track
            float thumbHalfW = trackHalfW + dp(2);
            thumbRect.set(cx - thumbHalfW, thumbY - thumbHalfH, cx + thumbHalfW, thumbY + thumbHalfH);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xfff2f1eb);
            canvas.drawRoundRect(thumbRect, dp(5), dp(5), paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(fineMode ? ACCENT : 0x55121617);
            canvas.drawRoundRect(thumbRect, dp(5), dp(5), paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(fineMode ? ACCENT : 0xff222829);
            canvas.drawRect(cx - dp(8), thumbY - dp(0.6f), cx + dp(8), thumbY + dp(0.6f), paint);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                tracking = true;
                fineMode = false;
                lastY = event.getY();
                getParent().requestDisallowInterceptTouchEvent(true);
                updateFromPosition(lastY);
                postDelayed(enterFineMode, 500);
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_MOVE && tracking) {
                lastY = event.getY();
                if (fineMode) value = Math.max(min, Math.min(max,
                        fineStartValue + Math.round((fineStartY - lastY) / Math.max(1f, dp(3)))));
                else updateFromPosition(lastY);
                if (listener != null) listener.onChanged(value);
                invalidate();
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                removeCallbacks(enterFineMode);
                tracking = false;
                fineMode = false;
                getParent().requestDisallowInterceptTouchEvent(false);
                if (modeListener != null) modeListener.onChanged(false);
                if (event.getAction() == MotionEvent.ACTION_UP) performClick();
                invalidate();
                return true;
            }
            return true;
        }

        private void updateFromPosition(float y) {
            float rangeTop = dp(5) + dp(6) + dp(4);
            float rangeBottom = getHeight() - dp(5) - dp(6) - dp(4);
            float fraction = (rangeBottom - y) / Math.max(1f, rangeBottom - rangeTop);
            value = Math.max(min, Math.min(max, min + Math.round(fraction * (max - min))));
            if (listener != null) listener.onChanged(value);
        }

        @Override public boolean performClick() {
            super.performClick();
            return true;
        }
    }

    /** A quiet abstract sunset frame gives the editor a photographic preview before the first capture. */
    private final class PlaceholderArtwork extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private LinearGradient skyShader;

        PlaceholderArtwork() { super(SettingsActivity.this); }

        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            skyShader = h > 0 ? new LinearGradient(0, 0, 0, h,
                    new int[]{0xff536068, 0xff73726b, 0xff9a8062, 0xff242727}, null, Shader.TileMode.CLAMP) : null;
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth(), h = getHeight();
            paint.setShader(skyShader);
            canvas.drawRect(0, 0, w, h, paint);
            paint.setShader(null);
            paint.setColor(0xfff3bf77);
            paint.setAlpha(210);
            canvas.drawCircle(w * .24f, h * .56f, Math.min(w, h) * .095f, paint);
            paint.setAlpha(255);
            paint.setColor(0x40212627);
            canvas.drawRect(w * .48f, h * .12f, w * .94f, h * .61f, paint);
            paint.setColor(0xff172020);
            canvas.drawRect(0, h * .68f, w, h, paint);
            paint.setColor(0x88d7b574);
            paint.setStrokeWidth(dp(1));
            for (int i = 1; i <= 5; i++) {
                float y = h * .68f + (h * .32f) * i / 6f;
                canvas.drawLine(0, y, w, y, paint);
            }
            for (int i = -2; i <= 5; i++) {
                canvas.drawLine(w * .5f, h * .68f, w * i / 5f, h, paint);
            }
            paint.setColor(0xff0d1112);
            paint.setStrokeWidth(dp(3));
            canvas.drawLine(w * .47f, h * .1f, w * .47f, h * .65f, paint);
        }
    }

    @Override protected void onPause() { AppUpdateManager.onPause(this); super.onPause(); }

    @Override
    protected void onDestroy() {
        heroJob.close();
        mainHandler.removeCallbacksAndMessages(null);
        heroWorker.shutdown();
        currentHeroBitmap = null;
        if (heroImageView != null) {
            heroImageView.setImageDrawable(null);
            heroImageView = null;
        }
        if (heroGradedBitmap != null && !heroGradedBitmap.isRecycled()) {
            heroGradedBitmap.recycle();
            heroGradedBitmap = null;
        }
        if (cachedHeroSource != null && !cachedHeroSource.isRecycled()) {
            cachedHeroSource.recycle();
            cachedHeroSource = null;
        }
        if (baseHeroBitmap != null && !baseHeroBitmap.isRecycled()) {
            baseHeroBitmap.recycle();
            baseHeroBitmap = null;
        }
        super.onDestroy();
    }

    private int dp(float value) { return UiKit.dp(this, value); }
}
