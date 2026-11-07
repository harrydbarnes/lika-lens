package com.scope.camera;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentUris;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.io.File;
import java.io.InputStream;

/** Native settings for capture, RAW development and the viewfinder. */
public class SettingsActivity extends Activity {
    private static final int BG = 0xff0b0d0e;
    private static final int CARD = 0xff121617;
    private static final int LINE = 0xff293031;
    private static final int ACCENT = 0xffd7b574;
    private LinearLayout content;
    private ScrollView scroll;
    private LinearLayout categoryBar;
    private String activeCategory = "PROCESSING";

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        AppSettings.migrateBranding(this);
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
                    rebuildContent();
                }).show());
        TextView done = pill("DONE", true);
        LinearLayout.LayoutParams doneLp = new LinearLayout.LayoutParams(dp(54), dp(32));
        doneLp.leftMargin = dp(6);
        header.addView(done, doneLp);
        done.setOnClickListener(v -> finish());
        page.addView(header);

        categoryBar = new LinearLayout(this);
        categoryBar.setGravity(Gravity.CENTER_VERTICAL);
        categoryBar.setPadding(dp(18), 0, dp(18), dp(8));
        page.addView(categoryBar);
        refreshCategoryBar();

        scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(5), dp(16), dp(26));
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(page);
        populateContent();
    }

    private void rebuildContent() {
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
        addSection("PROCESSING BRAIN", "The look is developed from sensor data after capture.");
        addChoice("Rendering brain", "Choose the image-making approach", AppSettings.BRAIN,
                new String[]{"LIKA / FILM", "NEUTRAL / RAW", "MONO / SILVER"}, "LIKA / FILM");
        addChoice("Capture output", "DNG stays untouched; Lika Lens develops the JPEG", AppSettings.CAPTURE_FORMAT,
                new String[]{"RAW + rendered JPEG", "DNG only", "Rendered JPEG only"}, "RAW + rendered JPEG");
        addSwitch("Keep sensor original", "Save the untouched RAW file beside the render", AppSettings.KEEP_RAW, true);
        addSection("RAW DEVELOPMENT", "Tune the render while preserving the original DNG.");
        addChoice("White balance", "Auto uses this frame's sensor estimate", AppSettings.WHITE_BALANCE_MODE,
                new String[]{"Auto · as shot", "3200 K · tungsten", "4000 K · warm LED", "5000 K · daylight", "5600 K · daylight", "6500 K · overcast", "7500 K · shade"}, "Auto · as shot");
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

        addSection("IMAGE CHARACTER", "Texture and optical effects are applied in the render.");
        addSlider("Film grain", "Fine luminance texture", AppSettings.GRAIN, 0, 100, AppSettings.DEFAULT_GRAIN);
        addChoice("Grain structure", "Character of the texture", "grain_structure",
                new String[]{"Fine · 35 mm", "Soft · 16 mm", "Coarse · 8 mm", "Monochrome"}, "Fine · 35 mm");
        addSlider("Halation", "Warm response around bright highlights", AppSettings.HALATION, 0, 100, AppSettings.DEFAULT_HALATION);
        addSlider("Vignette", "Subtle edge fall-off", AppSettings.VIGNETTE, 0, 100, AppSettings.DEFAULT_VIGNETTE);
        addInfo("Optical correction", "Lens shading and chromatic-aberration correction are not active in this build.", 0xffaab3ad);
    }

    private void populateCameraSettings() {
        addSection("CAPTURE", "Sensor, focus behaviour and timing.");
        addChoice("Frame shape", "Match the viewfinder guide and rendered JPEG; keep the DNG full-frame", AppSettings.ASPECT_RATIO,
                AppSettings.ASPECT_RATIOS, "4:3");
        addChoice("Default lens", "Camera lens selected on app launch", AppSettings.DEFAULT_LENS,
                new String[]{"Auto / Wide 1×", "Ultra-wide .5×", "Telephoto 2×/5×", "Last used"}, "Auto / Wide 1×");
        addChoice("Flash mode", "Flash hardware and RAW capture compatibility vary by camera", AppSettings.FLASH,
                new String[]{"Off", "Torch", "Auto"}, "Off");
        addChoice("RAW capture size", "Uses the largest RAW stream the camera supports", AppSettings.RAW_SIZE,
                new String[]{"Maximum sensor resolution", "12 MP · faster development"}, "Maximum sensor resolution");
        addChoice("Exposure mode", "Manual sensor controls require device support", AppSettings.EXPOSURE_MODE,
                new String[]{"Auto exposure", "Manual exposure"}, "Auto exposure");
        addChoice("Manual shutter", "Used when Manual exposure is selected", AppSettings.MANUAL_SHUTTER,
                new String[]{"1/30 sec", "1/60 sec", "1/120 sec", "1/240 sec", "1/500 sec", "1/1000 sec"}, "1/120 sec");
        addChoice("Manual ISO", "Used when Manual exposure is selected", AppSettings.MANUAL_ISO,
                new String[]{"ISO 50", "ISO 100", "ISO 200", "ISO 400", "ISO 800", "ISO 1600"}, "ISO 100");
        addChoice("Self-timer", "Delay before the shutter fires", AppSettings.TIMER,
                new String[]{"Off", "3 seconds", "10 seconds"}, "Off");
        addChoice("Focus behaviour", "Camera2 autofocus mode", "focus_mode",
                new String[]{"Continuous picture autofocus", "Single autofocus"}, "Continuous picture autofocus");
        addSwitch("Haptic shutter", "Short tactile response when a frame is taken", AppSettings.HAPTICS, true);

        addSection("CINEMATIC VIDEO", "High-bitrate motion recording with film shutter timing.");
        addChoice("Video frame rate", "Cinematic motion blur cadence", AppSettings.VIDEO_FPS,
                new String[]{"24 fps (Cinematic)", "30 fps", "60 fps"}, "24 fps (Cinematic)");
        addChoice("Video framing", "Framing aspect ratio for video recording", AppSettings.VIDEO_ASPECT,
                new String[]{"16:9 Widescreen", "2.39:1 Cinemascope"}, "16:9 Widescreen");
        addChoice("Video bitrate", "Encoding target bitrate for motion detail", AppSettings.VIDEO_BITRATE,
                new String[]{"High · 24 Mbps", "Ultra · 35 Mbps", "Standard · 14 Mbps"}, "High · 24 Mbps");
        addInfo("180° Shutter Rule", "In 24 fps cinematic mode, the app targets a 1/48s exposure interval for authentic motion cadence.", 0xffaab3ad);

        addSection("VIEWFINDER", "Framing and exposure aids.");
        addChoice("Composition grid", "Choose a guide overlay inside the viewfinder frame", AppSettings.GRID_STYLE,
                new String[]{"None", "2×2", "3×3", "4×4", "Golden ratio"}, "None");
        addSwitch("Visual level", "Artificial horizon bar that illuminates gold when level", AppSettings.VISUAL_LEVEL, true);
        addInfo("Grid guides", "The frame outline and grid help compose the shot. They are not saved into the photo.", 0xffaab3ad);
        addInfo("Exposure aids", "Histogram, zebra and focus-peaking overlays are planned.", 0xffaab3ad);
        boolean hasRaw = AppSettings.getBoolean(this, AppSettings.RAW_SUPPORTED, false);
        addInfo("RAW sensor support", hasRaw ? "Available on the last opened camera" : "Not confirmed yet · open the camera to detect support", hasRaw ? ACCENT : 0xffdfb56f);
        boolean hasManual = AppSettings.getBoolean(this, AppSettings.MANUAL_SUPPORTED, false);
        addInfo("Manual sensor support", hasManual ? "Shutter and ISO controls are available" : "Not confirmed yet · depends on the selected camera", hasManual ? ACCENT : 0xffdfb56f);
        addInfo("Capture pipeline", "Camera2 RAW_SENSOR → DNG original → Lika Lens render", 0xffaab3ad);
        addInfo("Limitations", "If a camera cannot stream RAW with the live preview, Lika Lens falls back to a rendered JPEG and labels that capture.", 0xffaab3ad);
    }

    private void populateGeneralSettings() {
        addSection("OUTPUT & FILES", "Shareable renders and a recoverable negative.");
        addChoice("Rendered file", "Current Android build exports JPEG", "render_format",
                new String[]{"JPEG · maximum quality"}, "JPEG · maximum quality");
        addChoice("JPEG quality", "Compression level for the rendered image", "jpeg_quality",
                new String[]{"Maximum · 96%", "High · 92%", "Balanced · 85%"}, "Maximum · 96%");
        addChoice("Colour space", "Lika Lens currently renders an sRGB JPEG", AppSettings.COLOR_SPACE,
                new String[]{"sRGB · supported"}, "sRGB · supported");
        addChoice("File name prefix", "Organise Lika Lens captures in Photos", AppSettings.FILE_PREFIX,
                new String[]{"LIKA", "STILLS", "Custom later"}, "LIKA");
        addSwitch("Save rendered JPEG to Photos", "Add the developed still to Pictures/Lika Lens", AppSettings.AUTO_EXPORT, true);
        addSwitch("Write location metadata", "Location permission and metadata support are not enabled in this build", AppSettings.LOCATION, false);
        addSection("APP", "About the current image pipeline.");
        addInfo("Viewfinder", "The device's live feed is only for framing. Lika Lens develops the final look from RAW after capture.", 0xffaab3ad);
    }

    private void addSection(String title, String subtitle) {
        LinearLayout card = card();
        TextView titleView = text(title, 9, ACCENT, true);
        titleView.setLetterSpacing(.16f);
        card.addView(titleView);
        TextView sub = text(subtitle, 9, 0xff8d9892, false);
        sub.setPadding(0, dp(5), 0, dp(2));
        card.addView(sub);
        addCard(card);
    }

    private void addEditorHero() {
        LinearLayout previewCard = card();
        previewCard.setPadding(dp(8), dp(8), dp(8), dp(9));
        FrameLayout frame = new FrameLayout(this);
        frame.setBackground(round(0xff0b0d0e, 14, LINE, dp(1)));
        frame.setClipToOutline(true);
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        frame.addView(image, new FrameLayout.LayoutParams(-1, dp(192)));
        Uri latest = latestScopeImage();
        Bitmap preview = latest == null ? null : decodePreview(latest, dp(640), dp(400));
        if (preview != null) {
            image.setImageBitmap(preview);
        } else {
            image.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{0xff283237, 0xff131a1c, 0xff090b0c}));
            PlaceholderArtwork art = new PlaceholderArtwork();
            frame.addView(art, new FrameLayout.LayoutParams(-1, dp(192)));
        }
        LinearLayout caption = new LinearLayout(this);
        caption.setOrientation(LinearLayout.VERTICAL);
        caption.setPadding(dp(12), dp(22), dp(12), dp(10));
        caption.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00080b0c, 0xb8080b0c}));
        TextView shot = text(preview == null ? "READY TO DEVELOP" : "LAST FRAME", 8, ACCENT, true);
        shot.setLetterSpacing(.15f);
        caption.addView(shot);
        TextView look = text(AppSettings.brain(this), 10, 0xfff1f2ec, true);
        LinearLayout.LayoutParams lookLp = new LinearLayout.LayoutParams(-1, -2);
        lookLp.topMargin = dp(3);
        caption.addView(look, lookLp);
        FrameLayout.LayoutParams captionLp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        frame.addView(caption, captionLp);
        previewCard.addView(frame);
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(-1, -2);
        previewLp.bottomMargin = dp(8);
        content.addView(previewCard, previewLp);

        LinearLayout toneCard = card();
        TextView toneTitle = text("TONE", 9, ACCENT, true);
        toneTitle.setLetterSpacing(.15f);
        toneCard.addView(toneTitle);
        TextView toneHint = text("Shapes the next capture · hold a slider for fine adjustment", 8, 0xff89948e, false);
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
        });
        LinearLayout.LayoutParams sliderLp = new LinearLayout.LayoutParams(-1, dp(122));
        column.addView(slider, sliderLp);
        row.addView(column, new LinearLayout.LayoutParams(0, -2, 1));
    }

    private Uri latestScopeImage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            String[] columns = {MediaStore.Images.Media._ID};
            try (Cursor cursor = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
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
            } catch (RuntimeException ignored) { }
        }
        java.util.List<File> candidates = new java.util.ArrayList<>();
        File pictures = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        for (String folderName : new String[]{"Lika Lens", "SCOPE"}) {
            File folder = new File(pictures, folderName);
            File[] files = folder.listFiles((dir, name) -> name.toLowerCase(java.util.Locale.US).endsWith(".jpg"));
            if (files != null) java.util.Collections.addAll(candidates, files);
        }
        if (candidates.isEmpty()) return null;
        candidates.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return Uri.fromFile(candidates.get(0));
    }

    private Bitmap decodePreview(Uri uri, int targetWidth, int targetHeight) {
        try (InputStream boundsStream = getContentResolver().openInputStream(uri)) {
            if (boundsStream == null) return null;
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(boundsStream, null, bounds);
            int sample = 1;
            while (bounds.outWidth / sample > targetWidth * 2 || bounds.outHeight / sample > targetHeight * 2) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            try (InputStream imageStream = getContentResolver().openInputStream(uri)) {
                return imageStream == null ? null : BitmapFactory.decodeStream(imageStream, null, options);
            }
        } catch (Exception ignored) { return null; }
    }

    private GradientDrawable round(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        if (strokeWidth > 0) d.setStroke(strokeWidth, strokeColor);
        return d;
    }

    private void addChoice(String title, String subtitle, String key, String[] options, String defaultValue) {
        LinearLayout card = card();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(title, 11, 0xffe6eae4, true));
        TextView detail = text(subtitle, 8, 0xff89948e, false);
        detail.setPadding(0, dp(4), 0, 0);
        copy.addView(detail);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        String current = AppSettings.get(this, key, defaultValue);
        TextView value = pill(shortValue(current), false);
        row.addView(value, new LinearLayout.LayoutParams(-2, dp(32)));
        row.setOnClickListener(v -> {
            String selected = AppSettings.get(this, key, defaultValue);
            int checked = 0;
            for (int i = 0; i < options.length; i++) if (options[i].equals(selected)) checked = i;
            new AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(options, checked, (dialog, which) -> {
                AppSettings.prefs(this).edit().putString(key, options[which]).apply();
                if (AppSettings.WHITE_BALANCE_MODE.equals(key)) {
                    String wbPreset = options[which];
                    int kelvin = wbPreset.startsWith("Auto") ? 0 : wbPreset.startsWith("3200") ? 3200 : wbPreset.startsWith("4000") ? 4000
                            : wbPreset.startsWith("5000") ? 5000 : wbPreset.startsWith("6500") ? 6500
                            : wbPreset.startsWith("7500") ? 7500 : 5600;
                    AppSettings.prefs(this).edit().putInt(AppSettings.WHITE_BALANCE, kelvin).apply();
                }
                value.setText(shortValue(options[which]));
                dialog.dismiss();
            }).setNegativeButton("Cancel", null).show();
        });
        card.addView(row);
        addCard(card);
    }

    private String shortValue(String value) {
        if (value.length() > 25) return value.substring(0, 22) + "…";
        return value;
    }

    private void addSlider(String title, String subtitle, String key, int min, int max, int defaultValue) {
        LinearLayout card = card();
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = text(title, 11, 0xffe6eae4, true);
        top.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        TextView value = text(sliderValue(key, AppSettings.getInt(this, key, defaultValue)), 9, ACCENT, true);
        value.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        top.addView(value);
        card.addView(top);
        TextView detail = text(subtitle, 8, 0xff89948e, false);
        detail.setPadding(0, dp(3), 0, 0);
        card.addView(detail);
        SeekBar seek = new SeekBar(this);
        seek.setMax(max - min);
        seek.setProgress(Math.max(0, Math.min(max - min, AppSettings.getInt(this, key, defaultValue) - min)));
        seek.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        seek.setThumbTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        seek.setPadding(dp(1), 0, dp(1), 0);
        card.addView(seek, new LinearLayout.LayoutParams(-1, dp(31)));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int selected = min + progress;
                value.setText(sliderValue(key, selected));
                if (fromUser) AppSettings.prefs(SettingsActivity.this).edit().putInt(key, selected).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        addCard(card);
    }

    private String sliderValue(String key, int value) {
        if (AppSettings.EXPOSURE.equals(key)) return String.format(java.util.Locale.US, "%+.1f EV", value / 10f);
        if (AppSettings.WARMTH.equals(key)) return value == 50 ? "0" : value > 50 ? "WARM +" + (value - 50) : "COOL " + (50 - value);
        return String.valueOf(value);
    }

    private void addSwitch(String title, String subtitle, String key, boolean fallback) {
        LinearLayout card = card();
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(title, 11, 0xffe6eae4, true));
        TextView detail = text(subtitle, 8, 0xff89948e, false);
        detail.setPadding(0, dp(4), 0, 0);
        copy.addView(detail);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        Switch toggle = new Switch(this);
        toggle.setButtonTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        toggle.setChecked(AppSettings.getBoolean(this, key, fallback));
        if (AppSettings.LOCATION.equals(key)) toggle.setEnabled(false);
        toggle.setOnCheckedChangeListener((button, checked) -> AppSettings.prefs(this).edit().putBoolean(key, checked).apply());
        row.addView(toggle);
        card.addView(row);
        addCard(card);
    }

    private void addInfo(String title, String detailText, int color) {
        LinearLayout card = card();
        card.addView(text(title, 10, 0xffe6eae4, true));
        TextView detail = text(detailText, 8, color, false);
        detail.setPadding(0, dp(5), 0, 0);
        card.addView(detail);
        addCard(card);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(13), dp(12), dp(13), dp(12));
        GradientDrawable background = new GradientDrawable();
        background.setColor(CARD);
        background.setCornerRadius(dp(14));
        background.setStroke(dp(1), LINE);
        card.setBackground(background);
        return card;
    }

    private void addCard(LinearLayout card) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        content.addView(card, lp);
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private TextView pill(String value, boolean active) {
        TextView view = text(value, 8, active ? ACCENT : 0xffd8ded7, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(9), 0, dp(9), 0);
        GradientDrawable background = new GradientDrawable();
        background.setColor(active ? 0xff1d1d1a : 0xff202626);
        background.setCornerRadius(dp(20));
        background.setStroke(dp(1), active ? ACCENT : 0xff39413e);
        view.setBackground(background);
        return view;
    }

    private interface OnValueChanged { void onChanged(int value); }
    private interface OnModeChanged { void onChanged(boolean fine); }

    private final class VerticalSlider extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
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
            float cx = getWidth() * .47f;
            float top = dp(9), bottom = getHeight() - dp(9);
            paint.setColor(0xff202626);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(new RectF(cx - dp(13), top, cx + dp(13), bottom), dp(12), dp(12), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(0xff343b39);
            canvas.drawRoundRect(new RectF(cx - dp(13), top, cx + dp(13), bottom), dp(12), dp(12), paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xff717a73);
            for (int i = 0; i <= 10; i++) {
                float y = bottom - (bottom - top) * (i / 10f);
                float tick = i % 5 == 0 ? dp(8) : dp(4);
                canvas.drawRect(cx + dp(4), y - dp(0.5f), cx + dp(4) + tick, y + dp(0.5f), paint);
            }
            float thumbY = bottom - (bottom - top) * (value / 100f);
            paint.setColor(0xfff2f1eb);
            canvas.drawRoundRect(new RectF(dp(6), thumbY - dp(8), getWidth() - dp(6), thumbY + dp(8)), dp(9), dp(9), paint);
            paint.setColor(0x66ffffff);
            canvas.drawRoundRect(new RectF(dp(9), thumbY - dp(6), getWidth() - dp(9), thumbY - dp(4)), dp(2), dp(2), paint);
            if (fineMode) {
                paint.setColor(ACCENT);
                canvas.drawCircle(getWidth() - dp(9), top + dp(2), dp(3), paint);
            }
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
            float top = dp(9), bottom = getHeight() - dp(9);
            value = Math.max(0, Math.min(100, Math.round((bottom - y) * 100f / Math.max(1f, bottom - top))));
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

        PlaceholderArtwork() { super(SettingsActivity.this); }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth(), h = getHeight();
            paint.setShader(new LinearGradient(0, 0, 0, h,
                    new int[]{0xff536068, 0xff73726b, 0xff9a8062, 0xff242727}, null, Shader.TileMode.CLAMP));
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

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
