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
    private String activeCategory = "PROCESSING";
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
        AppSettings.migrateBranding(this);
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
        TextView subtitle = text("Camera and image pipeline", 9, 0xff89948e, false);
        LinearLayout.LayoutParams subtitleLp = new LinearLayout.LayoutParams(-2, -2);
        subtitleLp.topMargin = dp(3);
        titleBox.addView(subtitle, subtitleLp);
        header.addView(titleBox, new LinearLayout.LayoutParams(0, -2, 1));
        TextView reset = pill("RESET", false);
        header.addView(reset, new LinearLayout.LayoutParams(dp(54), dp(32)));
        reset.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Reset settings?")
                .setMessage("Restore the Lika Lens starting look and capture defaults.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Reset", (dialog, which) -> {
                    AppSettings.prefs(this).edit().clear().apply();
                    hapticsEnabled = true;
                    rebuildContent();
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
        finishSection();
        content.removeAllViews();
        populateContent();
        scroll.post(() -> scroll.scrollTo(0, 0));
    }

    private void refreshCategoryBar() {
        if (categoryBar == null) return;
        categoryBar.removeAllViews();
        String[] categories = {"GENERAL", "CAMERA", "PROCESSING"};
        for (String category : categories) {
            TextView tab = pill(category, category.equals(activeCategory));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(34), 1f);
            if (!category.equals("GENERAL")) lp.leftMargin = dp(6);
            categoryBar.addView(tab, lp);
            tab.setOnClickListener(v -> {
                if (hapticsEnabled) {
                    tab.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                }
                activeCategory = category;
                refreshCategoryBar();
                rebuildContent();
            });
        }
    }

    private void populateContent() {
        if ("GENERAL".equals(activeCategory)) {
            populateGeneralSettings();
        } else if ("CAMERA".equals(activeCategory)) {
            populateCameraSettings();
        } else {
            populateProcessingSettings();
        }
    }

    private void populateProcessingSettings() {
        addEditorHero();
        beginSection("SMART POINT & SHOOT", "Automated scene-adaptive colour science for effortless shooting.");
        addSwitch("Easy point & shoot mode", "Automatically adapts warmth, protects skin tones and balances dynamic range", AppSettings.EASY_MODE, true);
        addChoice("Skin tone protection", "Auto follows the selected brain; On or Off overrides even in Easy Mode", AppSettings.SKIN_PROTECTION_MODE,
                AppSettings.OVERRIDE_CHOICES, AppSettings.OVERRIDE_AUTO);
        addChoice("Smart filmic vibrance", "Auto follows the selected brain; On or Off overrides even in Easy Mode", AppSettings.SMART_VIBRANCE_MODE,
                AppSettings.OVERRIDE_CHOICES, AppSettings.OVERRIDE_AUTO);

        beginSection("PROCESSING BRAIN", "The look is developed from sensor data after capture.");
        addChoice("Rendering brain", "Choose the image-making approach", AppSettings.BRAIN,
                AppSettings.BRAIN_CHOICES, AppSettings.BRAIN_LIKA_FILM);
        addChoice("Film push / pull rating", "Emulate chemical push/pull development latitude & grain", AppSettings.FILM_PUSH_PULL,
                AppSettings.FILM_PUSH_PULL_CHOICES, AppSettings.FILM_PUSH_PULL_CHOICES[0]);

        beginSection("COLOUR PIPELINE & TONE MAPPING", "Transfer functions, contrast geometry and gamut control.");
        addChoice("Colour space", "Target display gamut", AppSettings.COLOR_SPACE,
                AppSettings.COLOR_SPACE_CHOICES, AppSettings.COLOR_SPACE_SRGB);
        addSwitch("Ultra HDR gainmap", "Embed ISO 21496-1 HDR gainmap so film highlights and halation glow on OLED displays", AppSettings.ULTRA_HDR, true);
        addChoice("Tone curve", "Transfer curve geometry", AppSettings.TONE_CURVE,
                AppSettings.TONE_CURVE_CHOICES, "Filmic S-Curve");
        addChoice("Shadow profile", "Auto uses each brain's default toe; choose a manual override to lock blacks", AppSettings.SHADOW_PROFILE,
                AppSettings.SHADOW_PROFILE_CHOICES, AppSettings.SHADOW_PROFILE_AUTO);
        addChoice("Highlight recovery", "Auto adapts to scene highlight clipping; choose a manual override to lock roll-off", AppSettings.HIGHLIGHT_RECOVERY,
                AppSettings.HIGHLIGHT_RECOVERY_CHOICES, AppSettings.HL_REC_AUTO);

        beginSection("RAW DEVELOPMENT", "Tune the render while preserving the original DNG.");
        addChoice("White balance", "Auto uses this frame's sensor estimate", AppSettings.WHITE_BALANCE_MODE,
                new String[]{"Auto · as shot", "2800 K · incandescent", "3200 K · tungsten", "4000 K · warm LED", "5000 K · daylight", "5600 K · daylight", "6500 K · overcast", "7500 K · shade"}, "Auto · as shot");
        addSlider("Exposure trim", "Exposure compensation in 0.1 EV steps", AppSettings.EXPOSURE, -20, 20, AppSettings.DEFAULT_EXPOSURE);
        addSlider("Warmth shift", "Shift the selected brain warmer or cooler", AppSettings.WARMTH, 0, 100, AppSettings.DEFAULT_WARMTH);
        addSlider("Contrast", "Midtone contrast", AppSettings.CONTRAST, 50, 150, AppSettings.DEFAULT_CONTRAST);
        addSlider("Pivot", "Mid-grey anchor for the contrast curve", AppSettings.PIVOT, 0, 100, AppSettings.DEFAULT_PIVOT);
        addSlider("Colour intensity", "Colour intensity around neutral grey", AppSettings.SATURATION, 0, 160, AppSettings.DEFAULT_SATURATION);
        addSlider("Saturated colour roll-off", "Softly compress vivid colours while preserving hue", AppSettings.COLOR_ROLLOFF, 0, 100, AppSettings.DEFAULT_COLOR_ROLLOFF);
        addSlider("Highlight roll-off", "Compress bright tones smoothly", AppSettings.HIGHLIGHTS, 0, 100, AppSettings.DEFAULT_HIGHLIGHTS);
        addSlider("Shadow depth", "Set how firmly the image holds its dark tones", AppSettings.SHADOWS, 0, 100, AppSettings.DEFAULT_SHADOWS);
        addSlider("Colour noise reduction", "Reduce chroma noise in the developed image", AppSettings.COLOR_NR, 0, 100, AppSettings.DEFAULT_COLOR_NR);
        addSlider("Output sharpening", "Keep fine detail soft, then add only a little edge crispness", AppSettings.SHARPEN, 0, 100, AppSettings.DEFAULT_SHARPEN);

        beginSection("IMAGE CHARACTER", "Texture and optical effects are applied in the render.");
        addSlider("Film grain", "Fine luminance texture", AppSettings.GRAIN, 0, 100, AppSettings.DEFAULT_GRAIN);
        addChoice("Grain structure", "Character of the texture", "grain_structure",
                new String[]{"Fine · 35 mm", "Soft · 16 mm", "Coarse · 8 mm", "Monochrome"}, "Fine · 35 mm");
        addChoice("Grain response", "Luminance-weighted or uniform distribution", AppSettings.GRAIN_RESPONSE,
                new String[]{"Luminance-weighted", "Uniform"}, "Luminance-weighted");
        addSlider("Halation", "Warm response around bright highlights", AppSettings.HALATION, 0, 100, AppSettings.DEFAULT_HALATION);
        addSlider("Vignette", "Subtle edge fall-off", AppSettings.VIGNETTE, 0, 100, AppSettings.DEFAULT_VIGNETTE);
        addChoice("Frame border", "Optional film rebate or museum matte border on export", AppSettings.FRAME_BORDER,
                AppSettings.FRAME_BORDER_CHOICES, "None");
        addSwitch("Optical lens correction", "Apply sensor lens shading map and geometric distortion correction", AppSettings.OPTICAL_CORRECTION, true);

        beginSection("CUSTOM FILM RECIPES", "Store and manage personalized look presets.");
        for (int i = 1; i <= 3; i++) {
            final int slot = i;
            String name = AppSettings.getCustomRecipeName(this, slot);
            String desc = name != null ? ("Active: " + name + " (saved)") : "Empty preset slot (save from Camera or Develop)";
            addInfo("Recipe Slot " + slot, desc, name != null ? ACCENT : 0xff89948e);
        }

        beginSection("3D LUT (COLOR LOOKUP TABLES)", "Cinematic 3D film profiles and custom .CUBE grading tables.");
        List<String> luts = LutManager.getAvailableLutNames(this);
        String currentLut = AppSettings.get(this, AppSettings.LUT_NAME, "None");
        addChoice("Default 3D LUT", "Select primary 3D LUT applied during RAW development", AppSettings.LUT_NAME,
                luts.toArray(new String[0]), currentLut);
        addSlider("Default LUT intensity", "Blend strength between film brain and 3D LUT", AppSettings.LUT_INTENSITY,
                0, 100, AppSettings.getInt(this, AppSettings.LUT_INTENSITY, AppSettings.DEFAULT_LUT_INTENSITY));
        addInfo("Import .CUBE LUT...", "Tap to browse and import custom 3D LUT (.cube) files from storage", ACCENT, this::launchLutPicker);

        for (String lutName : luts) {
            if (!LutManager.isBuiltIn(lutName)) {
                int cubeSize = LutManager.readCubeSize(this, lutName);
                String desc = "Custom imported .cube" + (cubeSize > 0 ? (" (" + cubeSize + "³ grid)") : "");
                addInfo(lutName, desc, ACCENT);
            }
        }
        finishSection();
    }

    private void populateCameraSettings() {
        beginSection("CAPTURE", "Sensor, focus behaviour and timing.");
        addChoice("App launch mode", "Camera mode when opening Lika Lens", AppSettings.LAUNCH_MODE,
                AppSettings.LAUNCH_MODES, "Photo (default)");
        addChoice("Frame shape", "Match the viewfinder guide and rendered JPEG; keep the DNG full-frame", AppSettings.ASPECT_RATIO,
                AppSettings.ASPECT_RATIOS, "4:3");
        addChoice("Default lens", "Camera lens selected on app launch", AppSettings.DEFAULT_LENS,
                new String[]{"Auto / Wide 1×", "Ultra-wide .5×", "Telephoto 2×/5×", "Last used"}, "Auto / Wide 1×");
        addChoice("Flash mode", "Rear LED flash or front warm Retina screen illumination", AppSettings.FLASH,
                new String[]{"Off", "Auto", "Torch"}, "Off");
        addSwitch("Mirror front camera", "Save front-camera selfies horizontally flipped as previewed", AppSettings.MIRROR_FRONT, true);
        addSwitch("Show dirty lens warning", "Display a prompt if lens smudges or diffuse haze reduce contrast", AppSettings.DIRTY_LENS_WARNING, true);
        addChoice("Stabilization", "Optical (OIS) and electronic video stabilization", AppSettings.STABILIZATION,
                AppSettings.STABILIZATION_CHOICES, "Cinema OIS + EIS");
        addChoice("RAW capture size", "Uses the largest RAW stream the camera supports", AppSettings.RAW_SIZE,
                new String[]{"Maximum sensor resolution", "12 MP · faster development"}, "Maximum sensor resolution");
        addChoice("Exposure mode", "Manual sensor controls require device support", AppSettings.EXPOSURE_MODE,
                new String[]{"Auto exposure", "Manual exposure"}, "Auto exposure");
        addChoice("Manual shutter", "Used when Manual exposure is selected", AppSettings.MANUAL_SHUTTER,
                new String[]{"1/30 sec", "1/60 sec", "1/120 sec", "1/240 sec", "1/500 sec", "1/1000 sec", "1/2000 sec", "1/4000 sec"}, "1/120 sec");
        addChoice("Manual ISO", "Used when Manual exposure is selected", AppSettings.MANUAL_ISO,
                new String[]{"ISO 50", "ISO 100", "ISO 200", "ISO 400", "ISO 800", "ISO 1600", "ISO 3200"}, "ISO 100");
        addChoice("Self-timer", "Delay before the shutter fires", AppSettings.TIMER,
                new String[]{"Off", "3 seconds", "10 seconds"}, "Off");
        addSwitch("Timer countdown light", "Pulse rear LED flash rhythmically during self-timer countdown", AppSettings.TIMER_LIGHT, true);
        addSwitch("Palm gesture timer", "Show an open palm to the camera to start a 3-second hands-free countdown", AppSettings.PALM_TIMER, false);
        addChoice("Focus behaviour", "Camera2 autofocus mode", "focus_mode",
                new String[]{"Continuous picture autofocus", "Single autofocus"}, "Continuous picture autofocus");
        addChoice("Astrophotography / Night mode", "Long-exposure noise reduction when stationary or on tripod at night", AppSettings.ASTROPHOTOGRAPHY,
                AppSettings.ASTRO_CHOICES, "Auto (when stationary at night)");
        addChoice("Anti-banding / flicker", "Compensate for AC artificial lighting cycles", AppSettings.ANTI_BANDING,
                new String[]{"Auto (50/60 Hz)", "50 Hz (Europe/Asia)", "60 Hz (Americas/Japan)", "Off"}, "Auto (50/60 Hz)");
        addSwitch("Haptic shutter", "Short tactile response when a frame is taken", AppSettings.HAPTICS, true);
        addSwitch("Shutter sound", "Mechanical leaf shutter audio click on capture", AppSettings.SHUTTER_SOUND, true);

        beginSection("CINEMATIC VIDEO", "High-bitrate motion recording with mid-take camera switching.");
        addChoice("Video frame rate", "Cinematic motion blur cadence", AppSettings.VIDEO_FPS,
                new String[]{"24 fps (Cinematic)", "30 fps", "60 fps"}, "24 fps (Cinematic)");
        addChoice("Video framing", "Framing aspect ratio for video recording", AppSettings.VIDEO_ASPECT,
                new String[]{"16:9 Widescreen", "2.39:1 Cinemascope"}, "16:9 Widescreen");
        addChoice("Video bitrate", "Encoding target bitrate for motion detail", AppSettings.VIDEO_BITRATE,
                new String[]{"High · 24 Mbps", "Ultra · 35 Mbps", "Standard · 14 Mbps"}, "High · 24 Mbps");
        addChoice("Video codec", "Compression standard for recorded video", AppSettings.VIDEO_CODEC,
                new String[]{"H.264 · Universal", "HEVC (H.265) · High Efficiency"}, "H.264 · Universal");
        addSwitch("10-bit HLG HDR video", "Record 10-bit High Dynamic Range video when supported by the sensor", AppSettings.VIDEO_HDR, false);
        addChoice("Audio source", "Microphone processing during video recording", AppSettings.VIDEO_AUDIO,
                AppSettings.VIDEO_AUDIO_CHOICES, "Camcorder · Stereo");
        addSwitch("Directional audio zoom", "Focus microphone pickup toward the subject when zooming in", AppSettings.AUDIO_ZOOM, true);
        addInfo("Mid-Take Camera Switching", "Switch between front/rear cameras or lenses while recording. Segments are stitched automatically into a single video.", ACCENT);
        addInfo("180° Shutter Rule", "In 24 fps cinematic mode, the app targets a 1/48s exposure interval for authentic motion cadence.", 0xffaab3ad);

        beginSection("VIEWFINDER & SHOOTING ERGONOMICS", "Framing aids, exposure telemetry, gesture shortcuts and tactile controls.");
        addChoice("Left-hand mode", "Move top-left & key controls to the opposite side; optionally swap camera & gallery",
                AppSettings.LEFT_HAND_MODE, AppSettings.LEFT_HAND_MODE_CHOICES, AppSettings.LEFT_HAND_OFF);
        addSwitch("Live film stock viewfinder preview", "Real-time film color science simulation in the live viewfinder", AppSettings.LIVE_FILM_PREVIEW, true);
        addSwitch("Pro optical loupe (5×)", "High-magnification focus assist loupe with gold bezel during reticle adjustment", AppSettings.OPTICAL_LOUPE, false);
        addChoice("Composition grid", "Choose a guide overlay inside the viewfinder frame", AppSettings.GRID_STYLE,
                new String[]{"None", "2×2", "3×3", "4×4", "Golden ratio"}, "None");
        addSwitch("Visual level & flat-lay crosshair", "Horizon bar and top-down concentric crosshairs that illuminate gold when level", AppSettings.VISUAL_LEVEL, true);
        addChoice("Horizon visibility", "Display horizon line only near level to keep viewfinder clean, or always show", AppSettings.HORIZON_MODE,
                new String[]{"Smart (near level only)", "Always visible"}, "Smart (near level only)");
        addChoice("Horizon sensitivity", "Deadband tolerance for level detection", AppSettings.HORIZON_SENSITIVITY,
                new String[]{"Normal (±1.0°)", "Precise (±0.5°)"}, "Normal (±1.0°)");
        addSwitch("Live tonal histogram", "Real-time tonal distribution graph in the viewfinder corner", AppSettings.HISTOGRAM, false);
        addChoice("Histogram display style", "Tonal curve graph type (can also tap histogram in viewfinder to cycle)", AppSettings.HISTOGRAM_MODE,
                new String[]{"Luminance", "RGB Parade", "Compact"}, "Luminance");
        addSwitch("Highlight zebra stripes", "Diagonal warning stripes when sensor luminance approaches clipping (>95%)", AppSettings.ZEBRA, false);
        addSwitch("Focus peaking", "Highlight sharp in-focus contrast edges in gold", AppSettings.FOCUS_PEAKING, false);
        addChoice("Long press gesture", "Action when holding finger on viewfinder", AppSettings.VIEWFINDER_LONG_PRESS,
                new String[]{"AE / AF lock", "Lock exposure only (AE-L)", "Reset exposure bias", "None"}, "AE / AF lock");
        addChoice("Double tap gesture", "Action when double-tapping viewfinder", AppSettings.VIEWFINDER_DOUBLE_TAP,
                new String[]{"Toggle 1× / 2× zoom", "Reset exposure bias", "None"}, "Toggle 1× / 2× zoom");
        addChoice("Reticle metering", "Targeting behaviour for focus reticle tap (dual EV + Shadow sliders)", AppSettings.RETICLE_METERING,
                new String[]{"AF + AE Metering", "AF Only (Continuous AE)", "AE Only (Continuous AF)"}, "AF + AE Metering");
        addChoice("Volume key action", "Hardware volume key function", AppSettings.VOLUME_ROCKER,
                new String[]{"Shutter", "Zoom", "Volume", "Off"}, "Shutter");
        addSwitch("Shutter ocular wink", "Blackout pulse during optical leaf shutter exposure", AppSettings.SHUTTER_WINK, true);
        addSwitch("Exposure telemetry HUD", "Display live shutter speed, ISO, and white balance metering on the viewfinder", AppSettings.SHOW_TELEMETRY, false);
        addSwitch("Storage frame counter in HUD", "Show estimated remaining film frames in the exposure telemetry strip", AppSettings.FRAME_COUNTER, true);
        addInfo("Grid guides", "The frame outline and grid help compose the shot. They are not saved into the photo.", 0xffaab3ad);
        boolean hasRaw = AppSettings.getBoolean(this, AppSettings.RAW_SUPPORTED, false);
        addInfo("RAW sensor support", hasRaw ? "Available on the last opened camera" : "Not confirmed yet · open the camera to detect support", hasRaw ? ACCENT : 0xffdfb56f);
        boolean hasManual = AppSettings.getBoolean(this, AppSettings.MANUAL_SUPPORTED, false);
        addInfo("Manual sensor support", hasManual ? "Shutter and ISO controls are available" : "Not confirmed yet · depends on the selected camera", hasManual ? ACCENT : 0xffdfb56f);
        addInfo("Capture pipeline", "Camera2 RAW_SENSOR → DNG original → Lika Lens render", 0xffaab3ad);
        addInfo("Limitations", "If a camera cannot stream RAW with the live preview, Lika Lens falls back to a rendered JPEG and labels that capture.", 0xffaab3ad);
        finishSection();
    }

    private void populateGeneralSettings() {
        beginSection("FILE MANAGEMENT & OUTPUT", "Shareable renders, digital negatives and storage organisation.");
        addChoice("Photo output format", "Choose whether to export processed JPEG only, JPEG + DNG, or DNG original only",
                AppSettings.PHOTO_OUTPUT, AppSettings.PHOTO_OUTPUT_CHOICES, AppSettings.PHOTO_OUTPUT_JPEG_ONLY);
        addChoice("Editable originals cache limit", "Maximum private RAW storage kept for post-capture re-editing and switching brains",
                AppSettings.EDITABLE_ARCHIVE_LIMIT, AppSettings.EDITABLE_ARCHIVE_LIMIT_CHOICES, AppSettings.EDITABLE_ARCHIVE_512MB);
        addChoice("File naming pattern", "Format for exported files", AppSettings.FILE_NAMING,
                new String[]{"Prefix + Timestamp", "Film Look + Timestamp", "Roll counter (e.g. ROLL_0001)"}, "Prefix + Timestamp");
        addChoice("Folder organisation", "Structure under Pictures/Lika Lens", AppSettings.FOLDER_ORG,
                new String[]{"Single folder (Pictures/Lika Lens)", "Organise by month (YYYY-MM)", "Organise by film look"}, "Single folder (Pictures/Lika Lens)");
        addChoice("JPEG quality", "Encoder compression factor", AppSettings.JPEG_QUALITY,
                new String[]{"Maximum · 98% (Archival)", "High · 95% (Recommended)", "Balanced · 90%", "Compact · 85%"}, "High · 95% (Recommended)");
        addSwitch("Include EXIF shooting params", "Embed shutter, ISO, aperture, and focal length", AppSettings.EXIF_PARAMS, true);
        addChoice("File name prefix", "Organise Lika Lens captures in Photos", AppSettings.FILE_PREFIX,
                new String[]{"LIKA", "STILLS", "REEL", "CUSTOM…"}, "LIKA");
        addSwitch("Save location (GPS)", "Embed GPS coordinates in exported JPEG and DNG files", AppSettings.LOCATION, true);
        addInfo("Device storage & roll capacity", AppSettings.formatDetailedStorageBreakdown(this), ACCENT);

        beginSection("ANIMATED GIF CREATOR", "Stop-motion loops and video to animated GIF export.");
        addChoice("Default GIF frame rate", "Playback speed for exported animated GIFs", AppSettings.GIF_FPS,
                new String[]{"2 fps · Stop Motion", "5 fps · Retro Cinematic", "10 fps · Smooth (Recommended)", "15 fps · High Fluidity"}, "10 fps · Smooth (Recommended)");
        addChoice("Default GIF resolution", "Output dimension for GIF generation", AppSettings.GIF_RESOLUTION,
                new String[]{"320px · Compact & Light", "480px · Standard (Recommended)", "640px · Pro HD"}, "480px · Standard (Recommended)");
        addSwitch("Boomerang forward/reverse loop", "Ping-pong sequence for burst animations", AppSettings.GIF_BOOMERANG, false);

        beginSection("BACKUP & RESTORE", "Save, import or share your settings, custom film recipes and LUTs.");
        addInfo("Save named backup", "Export settings, recipes and custom LUTs to a named file", ACCENT, () -> showBackupNameDialog(false));
        addInfo("Share backup", "Name a backup file and share it with another app or user", ACCENT, () -> showBackupNameDialog(true));
        addInfo("Import backup", "Restore settings, custom recipes and LUTs from a backup file", 0xffe6eae4, this::launchBackupImportPicker);

        beginSection("UPDATES & RELEASES", "Continuous delivery from GitHub releases with Stable and Beta channels.");
        addChoice("Update release channel", "Choose between continuous beta builds or stable releases",
                AppSettings.UPDATE_CHANNEL, AppSettings.UPDATE_CHANNEL_CHOICES, AppSettings.UPDATE_CHANNEL_STABLE);
        addSwitch("Check for updates on launch", "Silently check for new versions when opening Lika Lens",
                AppSettings.AUTO_CHECK_UPDATES, true);
        addInfo("Check for updates now", "Query GitHub Releases API for the latest APK", ACCENT, this::checkAppUpdate);
        String verName = AppUpdateManager.getAppVersionName(this);
        int verCode = AppUpdateManager.getAppVersionCode(this);
        addInfo("Installed version", "Lika Lens v" + verName + " (Build " + verCode + ")", 0xffaab3ad);

        beginSection("APP", "About the current image pipeline.");
        addInfo("Viewfinder", "The device's live feed is only for framing. Lika Lens develops the final look from RAW after capture.", 0xffaab3ad);
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
        addVerticalControl(sliders, "PIVOT", AppSettings.PIVOT, AppSettings.DEFAULT_PIVOT);
        addVerticalControl(sliders, "HIGHLIGHTS", AppSettings.HIGHLIGHTS, AppSettings.DEFAULT_HIGHLIGHTS);
        addVerticalControl(sliders, "SHADOW DEPTH", AppSettings.SHADOWS, AppSettings.DEFAULT_SHADOWS);
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

    private void addVerticalControl(LinearLayout row, String label, String key, int fallback) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView name = text(label, 7, 0xffaeb5ad, true);
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(1);
        column.addView(name, new LinearLayout.LayoutParams(-1, dp(21)));
        int valueNow = AppSettings.getInt(this, key, fallback);
        TextView value = text(String.valueOf(valueNow), 8, ACCENT, true);
        final boolean[] fineMode = {false};
        value.setGravity(Gravity.CENTER);
        column.addView(value, new LinearLayout.LayoutParams(-1, dp(18)));
        VerticalSlider slider = new VerticalSlider();
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
        for (String option : options) {
            boolean isCurrent = option.equals(selected);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(13), dp(14), dp(13));
            row.setBackground(round(isCurrent ? 0x22d7b574 : 0x00000000, 10, isCurrent ? 0x66d7b574 : 0, isCurrent ? dp(1) : 0));

            TextView label = text(option, 11, isCurrent ? ACCENT : 0xffe6eae4, isCurrent);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0, -2, 1f);
            labelLp.rightMargin = dp(8);
            row.addView(label, labelLp);

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
                valueView.setText(shortValue(option));
                triggerHeroUpdate();
                dialog.dismiss();
            });

            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
            rowLp.bottomMargin = dp(4);
            optionsList.addView(row, rowLp);
        }
        optScroll.addView(optionsList, new FrameLayout.LayoutParams(-1, -2));
        sheet.addView(optScroll, new LinearLayout.LayoutParams(-1, dp(Math.min(380, options.length * 52 + 10))));

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

        void setValue(int value) { this.value = Math.max(0, Math.min(100, value)); invalidate(); }
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
            float thumbY = rangeBottom - (rangeBottom - rangeTop) * (value / 100f);

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
                if (fineMode) value = Math.max(0, Math.min(100,
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
            value = Math.max(0, Math.min(100, Math.round((rangeBottom - y) * 100f / Math.max(1f, rangeBottom - rangeTop))));
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
