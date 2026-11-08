package com.scope.camera;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.DngCreator;
import android.media.ExifInterface;
import android.media.Image;
import android.graphics.ImageFormat;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Size;
import android.util.Range;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewConfiguration;
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
import java.io.FileInputStream;
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
import java.util.concurrent.atomic.AtomicInteger;

/** Lika Lens: Camera2 framing preview plus RAW_SENSOR/DNG capture and app-side development. */
public class MainActivity extends Activity implements TextureView.SurfaceTextureListener {
    private static final String TAG = "LikaLens";
    private static final int CAMERA_PERMISSION = 41;
    private static final int VIDEO_PERMISSION = 42;
    private static final int BG = 0xff0b0d0e;
    private static final int ACCENT = 0xffd7b574;
    private static final int MUTED = 0xffa0aaa4;
    private static final float MIN_INTERACTIVE_ZOOM = .5f;
    private static final float MAX_INTERACTIVE_ZOOM = 5f;

    private TextureView textureView;
    private TextView cameraState;
    private TextView brainButton;
    private TextView flashButton;
    private TextView aspectButton;
    private TextView timerButton;
    private TextView easyButton;
    private TextView adjustButton;
    private TextView captureButton;
    private TextView photoModeButton;
    private TextView videoModeButton;
    private TextView videoFpsButton;
    private TextView videoAspectButton;
    private TextView recordingLockButton;
    private TextView videoPauseButton;
    private TextView videoSnapButton;
    private TextView videoLockButton;
    private TextView flipButton;
    private TextView settingsButton;
    private TextView modeNote;
    private TextView messageView;
    private TextView outputModeText;
    private ImageView galleryButton;
    private TextView shutterValueText;
    private TextView isoValueText;
    private TextView whiteBalanceValueText;
    private TextView zoomSliderValue;
    private final List<TextView> lensButtons = new ArrayList<>();
    private View gridOverlay;
    private SensorManager sensorManager;
    private Sensor orientationSensor;
    private float deviceRollAngle = 0f;
    private float smoothRollAngle = 0f;
    private float devicePitchAngle = 0f;
    private float smoothPitchAngle = 0f;
    private float smoothTiltX = 0f;
    private float smoothTiltY = 0f;

    private final SensorEventListener sensorEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
                float[] rotationMatrix = new float[9];
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
                float[] orientation = new float[3];
                SensorManager.getOrientation(rotationMatrix, orientation);
                float pitchDegrees = (float) Math.toDegrees(orientation[1]);
                float rollDegrees = (float) Math.toDegrees(orientation[2]);
                devicePitchAngle = pitchDegrees;
                smoothPitchAngle = smoothPitchAngle * 0.82f + pitchDegrees * 0.18f;
                smoothTiltX = smoothTiltX * 0.80f + (float) Math.sin(orientation[2]) * 0.20f;
                smoothTiltY = smoothTiltY * 0.80f + (float) Math.sin(orientation[1]) * 0.20f;
                updateHorizonRoll(rollDegrees);
            } else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                float ax = event.values[0];
                float ay = event.values[1];
                float az = event.values.length > 2 ? event.values[2] : 0f;
                float norm = Math.max(0.1f, (float) Math.sqrt(ax * ax + ay * ay + az * az));
                float pitchDeg = (float) Math.toDegrees(Math.asin(Math.max(-1f, Math.min(1f, az / norm))));
                devicePitchAngle = pitchDeg;
                smoothPitchAngle = smoothPitchAngle * 0.82f + pitchDeg * 0.18f;
                smoothTiltX = smoothTiltX * 0.80f + (ax / norm) * 0.20f;
                smoothTiltY = smoothTiltY * 0.80f + (ay / norm) * 0.20f;
                float angle = (float) Math.toDegrees(Math.atan2(ax, ay));
                updateHorizonRoll(angle);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };

    private boolean wasHorizonLevel = false;

    private void updateHorizonRoll(float angle) {
        float normalized = ((angle % 360f) + 360f) % 360f;
        float nearestCardinal = Math.round(normalized / 90f) * 90f;
        float diff = normalized - nearestCardinal;
        if (diff > 180f) diff -= 360f;
        if (diff < -180f) diff += 360f;

        deviceRollAngle = diff;
        smoothRollAngle = smoothRollAngle * 0.82f + deviceRollAngle * 0.18f;

        boolean showLevel = AppSettings.getBoolean(this, AppSettings.VISUAL_LEVEL, true);
        if (showLevel && AppSettings.getBoolean(this, AppSettings.HAPTICS, true)) {
            String sens = AppSettings.get(this, AppSettings.HORIZON_SENSITIVITY, "Normal (±1.0°)");
            float deadband = sens.contains("0.5") ? 0.5f : 1.0f;
            boolean isFlatLay = Math.abs(smoothPitchAngle) > 65f;
            boolean isLevel = isFlatLay
                    ? (Math.hypot(smoothTiltX, smoothTiltY) <= (deadband * 0.025f))
                    : (Math.abs(smoothRollAngle) <= deadband);
            if (isLevel && !wasHorizonLevel) {
                View target = gridOverlay != null ? gridOverlay : captureButton;
                if (target != null) {
                    target.performHapticFeedback(HapticFeedbackConstants.CONFIRM, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                }
                wasHorizonLevel = true;
            } else if (!isLevel && (isFlatLay ? Math.hypot(smoothTiltX, smoothTiltY) > (deadband * 0.038f) : Math.abs(smoothRollAngle) > (deadband + 0.45f))) {
                wasHorizonLevel = false;
            }
        }

        if (gridOverlay != null) {
            gridOverlay.postInvalidate();
        }
    }

    private View shutterWink;
    private View screenFlashOverlay;
    private android.media.MediaActionSound mediaActionSound;

    private void hapticClick(View v) {
        if (v != null && AppSettings.getBoolean(this, AppSettings.HAPTICS, true)) {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
    }

    private void triggerShutterWink() {
        if (shutterWink == null) return;
        if (!AppSettings.getBoolean(this, AppSettings.SHUTTER_WINK, true)) return;
        runOnUiThread(() -> {
            shutterWink.animate().cancel();
            shutterWink.setAlpha(0.88f);
            shutterWink.setVisibility(View.VISIBLE);
            shutterWink.animate()
                    .alpha(0f)
                    .setDuration(120)
                    .setStartDelay(40)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(() -> shutterWink.setVisibility(View.GONE))
                    .start();
        });
    }

    private void playShutterSound(int soundType) {
        if (!AppSettings.getBoolean(this, AppSettings.SHUTTER_SOUND, true)) return;
        try {
            if (mediaActionSound == null) {
                mediaActionSound = new android.media.MediaActionSound();
                mediaActionSound.load(android.media.MediaActionSound.SHUTTER_CLICK);
                mediaActionSound.load(android.media.MediaActionSound.START_VIDEO_RECORDING);
                mediaActionSound.load(android.media.MediaActionSound.STOP_VIDEO_RECORDING);
            }
            mediaActionSound.play(soundType);
        } catch (Throwable ignored) {}
    }

    private FocusReticleOverlay focusReticle;
    private final Handler focusHandler = new Handler(Looper.getMainLooper());
    private boolean hasManualFocusPoint = false;
    private MeteringRectangle[] currentAfRegions;
    private MeteringRectangle[] currentAeRegions;
    private int currentAeIndex = 0;
    private float currentEvOffset = 0f;
    private boolean aeLocked = false;
    private float dragStartY = 0f;
    private float dragStartEv = 0f;
    private int dragStartShadows = 50;
    private boolean isDraggingEv = false;
    private boolean isDraggingShadows = false;

    private LinearLayout zoomSliderPanel;
    private SeekBar zoomSlider;
    private ScaleGestureDetector scaleGestureDetector;
    private FrameLayout quickAdjustPanel;
    private LinearLayout quickControlsList;
    private FrameLayout root;
    private FrameLayout shutterFrame;
    private FrameLayout videoLockOverlay;
    private CameraManager cameraManager;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private CaptureRequest.Builder previewRequest;
    private Surface previewSurface;
    private ImageReader rawReader;
    private ImageReader jpegReader;
    private MediaRecorder mediaRecorder;
    private Surface recordingPreviewSurface;
    private File videoTempFile;
    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private final ExecutorService processor = Executors.newSingleThreadExecutor();
    private final Object captureLock = new Object();
    private Image pendingImage;
    private TotalCaptureResult pendingResult;
    private final AtomicInteger pendingRenders = new AtomicInteger(0);
    private volatile boolean captureInFlight;
    private ValueAnimator zoomAnimator;
    private boolean openingCamera;
    private boolean rawSupported;
    private boolean manualSupported;
    private boolean flashSupported;
    private boolean useRawForThisCamera;
    private boolean forceJpegForWide;
    private boolean pinchZooming;
    private boolean zoomRowDragging;
    private boolean zoomSliderScrubbing;
    private float zoomRowStartX;
    private float zoomRowStartY;
    private float zoomRowStartZoom;
    private int zoomTouchSlop;
    private boolean videoMode;
    private volatile boolean videoStarting;
    private volatile boolean videoRecording;
    private volatile boolean switchingVideoCamera;
    private boolean videoPaused;
    private long videoPauseStartedAt;
    private long accumulatedVideoDurationMs;
    private int lockedVideoOrientation = -1;
    private final List<File> videoSegments = new ArrayList<>();
    private boolean videoScreenLocked;
    private boolean unlockHolding;
    private volatile boolean pendingVideoStart;
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
    private long videoStartedAt;
    private long unlockHoldStartedAt;
    private Size videoSize;

    // Live exposure aids state (32-bin histogram + 16x12 zebra & peaking grid)
    private final int[] liveHistogramBins = new int[32];
    private final boolean[] liveZebraCells = new boolean[16 * 12];
    private final boolean[] livePeakingCells = new boolean[16 * 12];
    private final Runnable exposureAidsSampler = new Runnable() {
        @Override public void run() {
            if (textureView != null && textureView.isAvailable() && gridOverlay != null) {
                boolean wantHist = AppSettings.getBoolean(MainActivity.this, AppSettings.HISTOGRAM, false);
                boolean wantZebra = AppSettings.getBoolean(MainActivity.this, AppSettings.ZEBRA, false);
                boolean wantPeak = AppSettings.getBoolean(MainActivity.this, AppSettings.FOCUS_PEAKING, false);
                if (wantHist || wantZebra || wantPeak) {
                    try {
                        Bitmap sample = textureView.getBitmap(64, 48);
                        if (sample != null) {
                            computeExposureAids(sample, wantHist, wantZebra, wantPeak);
                            sample.recycle();
                            gridOverlay.invalidate();
                        }
                    } catch (Throwable ignored) {}
                    textureView.postDelayed(this, 110L);
                    return;
                }
            }
            if (textureView != null) textureView.postDelayed(this, 450L);
        }
    };

    private void computeExposureAids(Bitmap bmp, boolean wantHist, boolean wantZebra, boolean wantPeak) {
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        int[] px = new int[w * h];
        bmp.getPixels(px, 0, w, 0, 0, w, h);
        int[] luma = new int[w * h];
        if (wantHist) Arrays.fill(liveHistogramBins, 0);
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            int r = (c >> 16) & 0xff;
            int g = (c >> 8) & 0xff;
            int b = c & 0xff;
            int y = (r * 77 + g * 150 + b * 29) >> 8;
            luma[i] = y;
            if (wantHist) {
                int bin = Math.min(31, y >> 3);
                liveHistogramBins[bin]++;
            }
        }
        if (wantZebra || wantPeak) {
            for (int cy = 0; cy < 12; cy++) {
                for (int cx = 0; cx < 16; cx++) {
                    int idx = cy * 16 + cx;
                    int sx = cx * 4 + 2;
                    int sy = cy * 4 + 2;
                    int pIdx = sy * w + sx;
                    int yVal = luma[pIdx];
                    liveZebraCells[idx] = wantZebra && (yVal >= 242);
                    if (wantPeak && sx > 0 && sx < w - 1 && sy > 0 && sy < h - 1) {
                        int gx = Math.abs(luma[pIdx + 1] - luma[pIdx - 1]);
                        int gy = Math.abs(luma[pIdx + w] - luma[pIdx - w]);
                        livePeakingCells[idx] = (gx + gy) > 52;
                    } else {
                        livePeakingCells[idx] = false;
                    }
                }
            }
        }
    }

    private final Runnable hideZoomSlider = () -> {
        if (zoomSliderPanel != null && !pinchZooming && !zoomRowDragging && !zoomSliderScrubbing) {
            zoomSliderPanel.setVisibility(View.GONE);
        }
    };

    private final Runnable videoTimerTick = new Runnable() {
        @Override public void run() {
            if ((!videoRecording && !switchingVideoCamera) || outputModeText == null) return;
            long activeMs = (videoRecording && !videoPaused && !switchingVideoCamera)
                    ? Math.max(0L, SystemClock.elapsedRealtime() - videoStartedAt) : 0L;
            long seconds = (accumulatedVideoDurationMs + activeMs) / 1000L;
            String time = String.format(Locale.US, "%02d:%02d", seconds / 60L, seconds % 60L);
            String statePrefix = videoPaused ? "❚❚ PAUSED  " : "● REC  ";
            int segCount = videoSegments.size() + 1;
            String segInfo = segCount > 1 ? (" · SEG " + segCount) : "";
            outputModeText.setText(statePrefix + time + segInfo + "  ·  " + videoProfileLabel());
            cameraState.setText((videoPaused ? "VIDEO PAUSED  ·  " : "VIDEO RECORDING  ·  ") + time + segInfo);
            outputModeText.postDelayed(this, 250);
        }
    };

    private final Runnable unlockHoldTick = new Runnable() {
        @Override public void run() {
            if (!videoScreenLocked || !unlockHolding || videoLockButton == null) return;
            long remaining = 3000L - (SystemClock.elapsedRealtime() - unlockHoldStartedAt);
            if (remaining <= 0L) {
                unlockHolding = false;
                setVideoScreenLocked(false);
                return;
            }
            videoLockButton.setText(String.format(Locale.US, "HOLD %.1f SEC", remaining / 1000f));
            videoLockButton.postDelayed(this, 90L);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        AppSettings.migrateBranding(this);
        Window window = getWindow();
        window.setStatusBarColor(0xff0b0d0e);
        window.setNavigationBarColor(0xff0b0d0e);
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        buildCameraUi();
        cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            orientationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            if (orientationSensor == null) {
                orientationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            }
        }
        startBackgroundThread();
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (sensorManager != null && orientationSensor != null) {
            sensorManager.registerListener(sensorEventListener, orientationSensor, SensorManager.SENSOR_DELAY_UI);
        }
        updateBrainButton();
        updateEasyButton();
        updateQuickButtons();
        updateReadouts();
        updateGalleryButton();
        if (gridOverlay != null) gridOverlay.invalidate();
        if (textureView != null) {
            textureView.removeCallbacks(exposureAidsSampler);
            textureView.postDelayed(exposureAidsSampler, 200L);
            if (textureView.isAvailable()) openCameraIfReady();
        }
    }

    @Override protected void onPause() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(sensorEventListener);
        }
        if (textureView != null) {
            textureView.removeCallbacks(exposureAidsSampler);
        }
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
        if (mediaActionSound != null) {
            try { mediaActionSound.release(); } catch (Throwable ignored) {}
            mediaActionSound = null;
        }
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (videoScreenLocked) {
            if (videoLockButton != null) videoLockButton.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
            return;
        }
        super.onBackPressed();
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (videoScreenLocked) {
                if (videoLockButton != null) videoLockButton.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                return true;
            }
            if (event.getRepeatCount() == 0) {
                String action = AppSettings.get(this, AppSettings.VOLUME_ROCKER, "Shutter release");
                if ("Shutter release".equals(action)) {
                    onShutterPressed();
                    return true;
                } else if ("EV compensation bias".equals(action)) {
                    float step = keyCode == KeyEvent.KEYCODE_VOLUME_UP ? 0.33f : -0.33f;
                    float newEv = Math.max(-2f, Math.min(2f, currentEvOffset + step));
                    setExposureCompensationEv(newEv);
                    if (focusReticle != null) focusReticle.updateEv(newEv);
                    showMessage(String.format(Locale.US, "EV %+.1f", newEv));
                    return true;
                } else if ("Zoom 1× / 2×".equals(action)) {
                    chooseLens(keyCode == KeyEvent.KEYCODE_VOLUME_UP ? 2.0f : 1.0f);
                    return true;
                } else if ("Off".equals(action)) {
                    return super.onKeyDown(keyCode, event);
                }
            }
        }
        return super.onKeyDown(keyCode, event);
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
        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScaleBegin(ScaleGestureDetector detector) {
                if (videoScreenLocked || videoStarting) return false;
                pinchZooming = true;
                showZoomSlider();
                return true;
            }
            @Override public boolean onScale(ScaleGestureDetector detector) {
                setInteractiveZoom(requestedZoom * detector.getScaleFactor());
                return true;
            }
            @Override public void onScaleEnd(ScaleGestureDetector detector) {
                pinchZooming = false;
                finishInteractiveZoom();
                scheduleZoomSliderHide();
            }
        });
        root.addView(textureView, new FrameLayout.LayoutParams(-1, -1));
        gridOverlay = new CompositionGrid(this);
        root.addView(gridOverlay, new FrameLayout.LayoutParams(-1, -1));

        focusReticle = new FocusReticleOverlay(this);
        focusReticle.setVisibility(View.GONE);
        focusReticle.setClickable(false);
        focusReticle.setFocusable(false);
        root.addView(focusReticle, new FrameLayout.LayoutParams(-1, -1));

        GestureOverlay overlay = new GestureOverlay();
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setPadding(dp(12), dp(14), dp(12), dp(14));
        overlay.setBackground(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x98080b0c, 0x00080b0c, 0x10080b0c, 0xd8080b0c}));
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout header = row();
        flashButton = pill("ϟ  OFF", false);
        header.addView(flashButton, new LinearLayout.LayoutParams(dp(60), dp(38)));
        flashButton.setOnClickListener(v -> { hapticClick(v); cycleFlash(); });
        aspectButton = pill("4:3", false);
        LinearLayout.LayoutParams aspectLp = new LinearLayout.LayoutParams(-2, dp(38));
        aspectLp.leftMargin = dp(6);
        header.addView(aspectButton, aspectLp);
        aspectButton.setOnClickListener(v -> { hapticClick(v); chooseAspectRatio(); });

        videoFpsButton = pill("24 FPS", true);
        LinearLayout.LayoutParams fpsLp = new LinearLayout.LayoutParams(-2, dp(38));
        fpsLp.leftMargin = dp(6);
        videoFpsButton.setVisibility(View.GONE);
        header.addView(videoFpsButton, fpsLp);
        videoFpsButton.setOnClickListener(v -> { hapticClick(v); cycleVideoFps(); });

        videoAspectButton = pill("16:9", false);
        LinearLayout.LayoutParams vaspectLp = new LinearLayout.LayoutParams(-2, dp(38));
        vaspectLp.leftMargin = dp(6);
        videoAspectButton.setVisibility(View.GONE);
        header.addView(videoAspectButton, vaspectLp);
        videoAspectButton.setOnClickListener(v -> { hapticClick(v); cycleVideoAspect(); });

        View topSpacer = new View(this);
        header.addView(topSpacer, new LinearLayout.LayoutParams(0, 1, 1));
        brainButton = pill("FILM  ⌄", true);
        LinearLayout.LayoutParams brainLp = new LinearLayout.LayoutParams(dp(64), dp(38));
        brainLp.leftMargin = dp(6);
        brainButton.setOnClickListener(v -> { hapticClick(v); chooseBrain(); });
        header.addView(brainButton, brainLp);
        timerButton = pill("◷  OFF", false);
        LinearLayout.LayoutParams timerLp = new LinearLayout.LayoutParams(dp(58), dp(38));
        timerLp.leftMargin = dp(6);
        header.addView(timerButton, timerLp);
        timerButton.setOnClickListener(v -> { hapticClick(v); cycleTimer(); });
        settingsButton = pill("⚙", false);
        LinearLayout.LayoutParams settingsLp = new LinearLayout.LayoutParams(dp(36), dp(38));
        settingsLp.leftMargin = dp(6);
        header.addView(settingsButton, settingsLp);
        settingsButton.setOnClickListener(v -> { hapticClick(v); startActivity(new Intent(this, SettingsActivity.class)); });
        overlay.addView(header);

        LinearLayout spacer = new LinearLayout(this);
        overlay.addView(spacer, new LinearLayout.LayoutParams(-1, 0, 1));

        GestureDetector tapDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onSingleTapConfirmed(MotionEvent e) {
                if (videoScreenLocked) return false;
                handleViewfinderTap(e.getX(), e.getY());
                return true;
            }
            @Override public boolean onDoubleTap(MotionEvent e) {
                if (videoScreenLocked || videoStarting) return false;
                handleViewfinderDoubleTap();
                return true;
            }
            @Override public void onLongPress(MotionEvent e) {
                if (videoScreenLocked || isDraggingEv || isDraggingShadows) return;
                handleViewfinderLongPress(e.getX(), e.getY());
            }
        });

        spacer.setOnTouchListener((v, event) -> {
            tapDetector.onTouchEvent(event);
            if (focusReticle != null && focusReticle.isVisible && event.getPointerCount() == 1) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        dragStartY = event.getY();
                        dragStartEv = currentEvOffset;
                        dragStartShadows = AppSettings.getInt(MainActivity.this, AppSettings.SHADOWS, AppSettings.DEFAULT_SHADOWS);
                        float rx = focusReticle.reticleX;
                        isDraggingShadows = (event.getX() < rx - dp(12) && event.getX() > rx - dp(78));
                        isDraggingEv = !isDraggingShadows && (Math.abs(event.getX() - (rx + dp(28))) < dp(55)
                                || Math.abs(event.getX() - rx) < dp(55));
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (isDraggingShadows) {
                            float dy = dragStartY - event.getY();
                            int deltaSh = Math.round((dy / dp(80)) * 50f);
                            int targetSh = Math.max(0, Math.min(100, dragStartShadows + deltaSh));
                            AppSettings.prefs(MainActivity.this).edit().putInt(AppSettings.SHADOWS, targetSh).apply();
                            focusReticle.updateShadows(targetSh);
                            scheduleFocusReticleHide();
                            return true;
                        } else if (isDraggingEv) {
                            float dy = dragStartY - event.getY();
                            float deltaEv = (dy / dp(80)) * 1.5f;
                            float targetEv = Math.max(-2f, Math.min(2f, dragStartEv + deltaEv));
                            setExposureCompensationEv(targetEv);
                            focusReticle.updateEv(targetEv);
                            scheduleFocusReticleHide();
                            return true;
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        isDraggingEv = false;
                        isDraggingShadows = false;
                        scheduleFocusReticleHide();
                        break;
                }
            }
            return true;
        });

        cameraState = label("CHECKING CAMERA SUPPORT…", 8, 0xffe2e7de, true);
        cameraState.setLetterSpacing(.09f);
        cameraState.setGravity(Gravity.CENTER);
        overlay.addView(cameraState);

        LinearLayout telemetry = row();
        telemetry.setPadding(dp(3), dp(9), dp(3), dp(11));
        telemetry.addView(readoutView("SHUTTER", "AUTO"), new LinearLayout.LayoutParams(0, -2, 1));
        telemetry.addView(readoutView("ISO", "AUTO"), new LinearLayout.LayoutParams(0, -2, 1));
        telemetry.addView(readoutView("WB", "5600 K"), new LinearLayout.LayoutParams(0, -2, 1));
        telemetry.setClickable(true);
        telemetry.setContentDescription("Exposure telemetry. Tap to lock or unlock exposure.");
        telemetry.setOnClickListener(v -> toggleAeLock());
        overlay.addView(telemetry);

        ZoomPresetRow lensRow = new ZoomPresetRow();
        lensRow.setGravity(Gravity.CENTER);
        lensRow.setContentDescription("Zoom presets. Swipe horizontally to reveal precision zoom.");
        float[] zooms = new float[]{.5f, 1f, 2f, 5f};
        String[] names = new String[]{".5×", "1×", "2×", "5×"};
        String[] mmEquivs = new String[]{"14mm", "28mm", "50mm", "135mm"};
        for (int i = 0; i < zooms.length; i++) {
            final float zoom = zooms[i];
            boolean active = Math.abs(zoom - 1f) < .01f;
            TextView lens = lensPill(names[i], mmEquivs[i], active);
            lens.setTag(zoom);
            lensButtons.add(lens);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(54), dp(36));
            lp.setMargins(dp(3), 0, dp(3), 0);
            lensRow.addView(lens, lp);
            lens.setOnClickListener(v -> chooseLens(zoom));
        }
        overlay.addView(lensRow);

        zoomSliderPanel = row();
        zoomSliderPanel.setGravity(Gravity.CENTER_VERTICAL);
        zoomSliderPanel.setPadding(dp(10), dp(1), dp(10), 0);
        zoomSliderPanel.setBackground(round(0xd8191e1e, 13, 0x66454d49, dp(1)));
        zoomSliderValue = label("1.0×", 8, ACCENT, true);
        zoomSliderPanel.addView(zoomSliderValue, new LinearLayout.LayoutParams(dp(38), dp(30)));
        zoomSlider = new SeekBar(this);
        zoomSlider.setMax(1000);
        zoomSlider.setProgress(zoomProgressFor(MIN_INTERACTIVE_ZOOM));
        zoomSlider.setContentDescription("Precision zoom from 0.5× to 5×");
        zoomSlider.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        zoomSlider.setThumbTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        zoomSliderPanel.addView(zoomSlider, new LinearLayout.LayoutParams(0, dp(32), 1f));
        zoomSliderPanel.setVisibility(View.GONE);
        LinearLayout.LayoutParams zoomSliderLp = new LinearLayout.LayoutParams(-1, -2);
        zoomSliderLp.topMargin = dp(4);
        overlay.addView(zoomSliderPanel, zoomSliderLp);
        final float[] lastDetentZoom = {1.0f};
        zoomSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    float z = zoomForProgress(progress);
                    setInteractiveZoom(z);
                    if (AppSettings.getBoolean(MainActivity.this, AppSettings.HAPTICS, true)) {
                        float[] landmarks = new float[]{0.5f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f};
                        for (float lm : landmarks) {
                            if ((lastDetentZoom[0] < lm && z >= lm) || (lastDetentZoom[0] > lm && z <= lm)) {
                                seekBar.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                                break;
                            }
                        }
                        lastDetentZoom[0] = z;
                    }
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {
                zoomSliderScrubbing = true;
                showZoomSlider();
            }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                zoomSliderScrubbing = false;
                finishInteractiveZoom();
                scheduleZoomSliderHide();
            }
        });

        LinearLayout modeRow = row();
        modeRow.setGravity(Gravity.CENTER);
        modeRow.setPadding(0, dp(6), 0, 0);
        photoModeButton = pill("PHOTO", true);
        videoModeButton = pill("VIDEO", false);
        photoModeButton.setContentDescription("Photo mode");
        videoModeButton.setContentDescription("Video mode");
        LinearLayout.LayoutParams modeLp = new LinearLayout.LayoutParams(dp(68), dp(30));
        modeLp.setMargins(dp(3), 0, dp(3), 0);
        modeRow.addView(photoModeButton, modeLp);
        modeRow.addView(videoModeButton, modeLp);
        photoModeButton.setOnClickListener(v -> { hapticClick(v); setVideoMode(false); });
        videoModeButton.setOnClickListener(v -> { hapticClick(v); setVideoMode(true); });
        overlay.addView(modeRow);

        LinearLayout lookRow = row();
        modeNote = label("RAW DEVELOPED AFTER CAPTURE", 7, 0xffc9d0c8, false);
        modeNote.setLetterSpacing(.055f);
        LinearLayout.LayoutParams noteTextLp = new LinearLayout.LayoutParams(0, -2, 1f);
        lookRow.addView(modeNote, noteTextLp);
        easyButton = pill("★ EASY", true);
        easyButton.setContentDescription("Toggle Easy Mode: scene-adaptive point and shoot");
        easyButton.setOnClickListener(v -> { hapticClick(v); toggleEasyMode(); });
        LinearLayout.LayoutParams easyLp = new LinearLayout.LayoutParams(dp(72), dp(30));
        easyLp.rightMargin = dp(6);
        lookRow.addView(easyButton, easyLp);
        adjustButton = pill("ADJUST", false);
        adjustButton.setContentDescription("Open quick look controls");
        adjustButton.setOnClickListener(v -> { hapticClick(v); toggleQuickAdjustPanel(); });
        lookRow.addView(adjustButton, new LinearLayout.LayoutParams(dp(72), dp(30)));

        videoPauseButton = pill("❚❚ PAUSE", false);
        videoPauseButton.setContentDescription("Pause or resume video recording");
        videoPauseButton.setOnClickListener(v -> { hapticClick(v); toggleVideoPause(); });
        LinearLayout.LayoutParams pauseLp = new LinearLayout.LayoutParams(dp(66), dp(30));
        pauseLp.rightMargin = dp(4);
        lookRow.addView(videoPauseButton, pauseLp);
        videoPauseButton.setVisibility(View.GONE);

        videoSnapButton = pill("◎ STILL", false);
        videoSnapButton.setContentDescription("Capture still photo while recording video");
        videoSnapButton.setOnClickListener(v -> { hapticClick(v); captureVideoStillFrame(); });
        LinearLayout.LayoutParams snapLp = new LinearLayout.LayoutParams(dp(58), dp(30));
        snapLp.rightMargin = dp(4);
        lookRow.addView(videoSnapButton, snapLp);
        videoSnapButton.setVisibility(View.GONE);

        recordingLockButton = pill("LOCK", false);
        recordingLockButton.setContentDescription("Lock video recording controls");
        recordingLockButton.setOnClickListener(v -> { hapticClick(v); setVideoScreenLocked(true); });
        lookRow.addView(recordingLockButton, new LinearLayout.LayoutParams(dp(54), dp(30)));
        recordingLockButton.setVisibility(View.GONE);
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
        galleryButton.setOnClickListener(v -> { hapticClick(v); openLatestFrame(); });
        galleryButton.setOnLongClickListener(v -> { hapticClick(v); openLibrary(); return true; });

        View shootSpacer = new View(this);
        shootRow.addView(shootSpacer, new LinearLayout.LayoutParams(0, 1, 1));
        captureButton = label("", 12, BG, false);
        captureButton.setGravity(Gravity.CENTER);
        captureButton.setBackground(round(0xfffaf9f5, 100, 0xfffaf9f5, dp(2)));
        captureButton.setClickable(false);
        captureButton.setFocusable(false);

        shutterFrame = new FrameLayout(this);
        shutterFrame.setBackground(round(0x00202020, 100, 0xfffaf9f5, dp(2)));
        shutterFrame.addView(captureButton, new FrameLayout.LayoutParams(dp(54), dp(54), Gravity.CENTER));
        shootRow.addView(shutterFrame, new LinearLayout.LayoutParams(dp(68), dp(68)));
        shutterFrame.setClickable(true);
        shutterFrame.setFocusable(true);
        shutterFrame.setOnTouchListener(new View.OnTouchListener() {
            private boolean isDown = false;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (!shutterFrame.isEnabled() || (captureButton != null && !captureButton.isEnabled())) {
                    return false;
                }
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        isDown = true;
                        hapticClick(v);
                        shutterFrame.animate().cancel();
                        captureButton.animate().cancel();
                        shutterFrame.animate().scaleX(0.88f).scaleY(0.88f).setDuration(70).setInterpolator(new DecelerateInterpolator()).start();
                        captureButton.animate().scaleX(0.92f).scaleY(0.92f).setDuration(70).setInterpolator(new DecelerateInterpolator()).start();
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (isDown) {
                            isDown = false;
                            shutterFrame.animate().cancel();
                            captureButton.animate().cancel();
                            shutterFrame.animate().scaleX(1.0f).scaleY(1.0f).setDuration(220)
                                    .setInterpolator(new android.view.animation.OvershootInterpolator(2.0f)).start();
                            captureButton.animate().scaleX(1.0f).scaleY(1.0f).setDuration(220)
                                    .setInterpolator(new android.view.animation.OvershootInterpolator(1.8f)).start();
                            float x = event.getX(), y = event.getY();
                            int slop = dp(24);
                            if (x >= -slop && x <= v.getWidth() + slop && y >= -slop && y <= v.getHeight() + slop) {
                                if (AppSettings.getBoolean(MainActivity.this, AppSettings.HAPTICS, true)) {
                                    v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                                }
                                onShutterPressed();
                            }
                            return true;
                        }
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        if (isDown) {
                            isDown = false;
                            shutterFrame.animate().cancel();
                            captureButton.animate().cancel();
                            shutterFrame.animate().scaleX(1.0f).scaleY(1.0f).setDuration(160)
                                    .setInterpolator(new DecelerateInterpolator()).start();
                            captureButton.animate().scaleX(1.0f).scaleY(1.0f).setDuration(160)
                                    .setInterpolator(new DecelerateInterpolator()).start();
                            return true;
                        }
                        break;
                }
                return false;
            }
        });

        shootRow.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        TextView flip = pill("⟳", false);
        flip.setTextSize(18f);
        flip.setContentDescription("Switch front and rear camera");
        shootRow.addView(flip, new LinearLayout.LayoutParams(dp(48), dp(48)));
        flip.setOnClickListener(v -> { hapticClick(v); toggleCameraFacing(); });
        flipButton = flip;
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
        buildVideoLockOverlay();

        shutterWink = new View(this);
        shutterWink.setBackgroundColor(0xff060809);
        shutterWink.setVisibility(View.GONE);
        shutterWink.setClickable(false);
        shutterWink.setFocusable(false);
        root.addView(shutterWink, new FrameLayout.LayoutParams(-1, -1));

        screenFlashOverlay = new View(this);
        screenFlashOverlay.setBackgroundColor(0xfffff4e0);
        screenFlashOverlay.setVisibility(View.GONE);
        screenFlashOverlay.setClickable(false);
        screenFlashOverlay.setFocusable(false);
        root.addView(screenFlashOverlay, new FrameLayout.LayoutParams(-1, -1));

        updateVideoModeUi();
        setContentView(root);
    }

    private void buildVideoLockOverlay() {
        videoLockOverlay = new FrameLayout(this);
        videoLockOverlay.setClickable(true);
        videoLockOverlay.setFocusable(true);
        videoLockOverlay.setBackgroundColor(0xb90a0d0d);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        content.setPadding(dp(28), dp(24), dp(28), dp(24));

        TextView status = label("●  RECORDING LOCKED", 10, 0xffff6b63, true);
        status.setGravity(Gravity.CENTER);
        status.setLetterSpacing(.12f);
        content.addView(status, new LinearLayout.LayoutParams(-1, -2));

        TextView instruction = label("Hold to unlock controls", 15, 0xfff3f2ec, true);
        instruction.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams instructionLp = new LinearLayout.LayoutParams(-1, -2);
        instructionLp.topMargin = dp(14);
        content.addView(instruction, instructionLp);

        videoLockButton = pill("HOLD 3 SECONDS", true);
        videoLockButton.setTextSize(10);
        videoLockButton.setMinimumHeight(dp(48));
        videoLockButton.setContentDescription("Hold for three seconds to unlock recording controls");
        LinearLayout.LayoutParams unlockLp = new LinearLayout.LayoutParams(-1, dp(52));
        unlockLp.topMargin = dp(22);
        content.addView(videoLockButton, unlockLp);
        videoLockButton.setOnTouchListener((view, event) -> {
            if (!videoScreenLocked) return false;
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                unlockHolding = true;
                unlockHoldStartedAt = SystemClock.elapsedRealtime();
                videoLockButton.setText("HOLD 3.0 SEC");
                videoLockButton.removeCallbacks(unlockHoldTick);
                videoLockButton.post(unlockHoldTick);
                return true;
            }
            if (event.getAction() == android.view.MotionEvent.ACTION_UP
                    || event.getAction() == android.view.MotionEvent.ACTION_CANCEL) {
                unlockHolding = false;
                videoLockButton.removeCallbacks(unlockHoldTick);
                if (videoScreenLocked) videoLockButton.setText("HOLD 3 SECONDS");
                return true;
            }
            return true;
        });

        videoLockOverlay.addView(content, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        root.addView(videoLockOverlay, new FrameLayout.LayoutParams(-1, -1));
        videoLockOverlay.setVisibility(View.GONE);
    }

    private void setVideoMode(boolean enabled) {
        if (videoRecording || videoStarting || videoMode == enabled) return;
        videoMode = enabled;
        if (quickAdjustPanel != null) quickAdjustPanel.setVisibility(View.GONE);
        updateVideoModeUi();
        updateQuickButtons();
        updateReadouts();
        if (gridOverlay != null) gridOverlay.invalidate();
        if (textureView != null) configureTransform(textureView.getWidth(), textureView.getHeight());
    }

    private void cycleVideoFps() {
        String current = AppSettings.get(this, AppSettings.VIDEO_FPS, "24 fps (Cinematic)");
        String next = current.startsWith("24") ? "30 fps" : current.startsWith("30") ? "60 fps" : "24 fps (Cinematic)";
        AppSettings.prefs(this).edit().putString(AppSettings.VIDEO_FPS, next).apply();
        updateQuickButtons();
        updateReadouts();
        updateVideoModeUi();
    }

    private void cycleVideoAspect() {
        String current = AppSettings.get(this, AppSettings.VIDEO_ASPECT, "16:9 Widescreen");
        String next = current.contains("2.39") ? "16:9 Widescreen" : "2.39:1 Cinemascope";
        AppSettings.prefs(this).edit().putString(AppSettings.VIDEO_ASPECT, next).apply();
        updateQuickButtons();
        if (gridOverlay != null) gridOverlay.invalidate();
        if (textureView != null) configureTransform(textureView.getWidth(), textureView.getHeight());
    }

    private void updateVideoModeUi() {
        if (photoModeButton == null) return;
        styleModeButton(photoModeButton, !videoMode);
        styleModeButton(videoModeButton, videoMode);
        if (brainButton != null) brainButton.setVisibility(videoMode ? View.GONE : View.VISIBLE);
        if (aspectButton != null) aspectButton.setVisibility(videoMode ? View.GONE : View.VISIBLE);
        if (timerButton != null) timerButton.setVisibility(videoMode ? View.GONE : View.VISIBLE);
        if (adjustButton != null) adjustButton.setVisibility(videoMode ? View.GONE : View.VISIBLE);
        if (easyButton != null) easyButton.setVisibility(videoMode ? View.GONE : View.VISIBLE);
        if (videoFpsButton != null) videoFpsButton.setVisibility(videoMode ? View.VISIBLE : View.GONE);
        if (videoAspectButton != null) videoAspectButton.setVisibility(videoMode ? View.VISIBLE : View.GONE);
        if (modeNote != null) {
            if (videoMode) {
                String fps = AppSettings.get(this, AppSettings.VIDEO_FPS, "24 fps (Cinematic)");
                String fpsTag = fps.startsWith("24") ? "24 FPS CINEMATIC" : fps.startsWith("30") ? "30 FPS" : "60 FPS";
                modeNote.setText(videoRecording ? "TAP ⟳ OR LENS TO SWITCH MID-TAKE" : "CINEMATIC VIDEO · " + fpsTag);
            } else {
                boolean easy = AppSettings.getBoolean(this, AppSettings.EASY_MODE, true);
                modeNote.setText(easy ? "SMART P&S  ·  SCENE & ILLUMINANT ADAPTIVE" : "RAW DEVELOPED AFTER CAPTURE");
            }
        }
        boolean activeVideoTake = (videoRecording || switchingVideoCamera) && !videoScreenLocked;
        if (recordingLockButton != null) recordingLockButton.setVisibility(activeVideoTake ? View.VISIBLE : View.GONE);
        if (videoPauseButton != null) {
            videoPauseButton.setVisibility(activeVideoTake ? View.VISIBLE : View.GONE);
            videoPauseButton.setText(videoPaused ? "▶ RESUME" : "❚❚ PAUSE");
            videoPauseButton.setTextColor(videoPaused ? ACCENT : 0xffe9ede6);
        }
        if (videoSnapButton != null) videoSnapButton.setVisibility(activeVideoTake ? View.VISIBLE : View.GONE);
        if (videoLockOverlay != null) videoLockOverlay.setVisibility((videoRecording || switchingVideoCamera) && videoScreenLocked ? View.VISIBLE : View.GONE);

        boolean idleControlsAvailable = !videoRecording && !videoStarting && !switchingVideoCamera;
        boolean liveSwitchAvailable = !videoStarting && !switchingVideoCamera && !videoScreenLocked;
        photoModeButton.setEnabled(idleControlsAvailable);
        videoModeButton.setEnabled(idleControlsAvailable);
        if (flashButton != null) flashButton.setEnabled(idleControlsAvailable);
        if (aspectButton != null) aspectButton.setEnabled(idleControlsAvailable);
        if (timerButton != null) timerButton.setEnabled(idleControlsAvailable);
        if (brainButton != null) brainButton.setEnabled(idleControlsAvailable);
        if (flipButton != null) flipButton.setEnabled(liveSwitchAvailable);
        if (settingsButton != null) settingsButton.setEnabled(idleControlsAvailable);
        if (galleryButton != null) galleryButton.setEnabled(idleControlsAvailable);
        if (videoFpsButton != null) videoFpsButton.setEnabled(idleControlsAvailable);
        if (videoAspectButton != null) videoAspectButton.setEnabled(idleControlsAvailable);
        for (TextView lens : lensButtons) lens.setEnabled(liveSwitchAvailable);
        boolean zoomAvailable = !videoStarting && !switchingVideoCamera && !videoScreenLocked;
        if (zoomSlider != null) zoomSlider.setEnabled(zoomAvailable);
        if (!zoomAvailable && zoomSliderPanel != null) {
            zoomSliderPanel.removeCallbacks(hideZoomSlider);
            zoomSliderPanel.setVisibility(View.GONE);
        }
        if (adjustButton != null) adjustButton.setEnabled(idleControlsAvailable);
        if (easyButton != null) easyButton.setEnabled(idleControlsAvailable);
        if (recordingLockButton != null) recordingLockButton.setEnabled(activeVideoTake);
        if (videoPauseButton != null) videoPauseButton.setEnabled(videoRecording && !switchingVideoCamera && !videoScreenLocked);
        if (videoSnapButton != null) videoSnapButton.setEnabled(videoRecording && !switchingVideoCamera && !videoScreenLocked);
        if (captureButton != null) captureButton.setEnabled(!videoStarting && !switchingVideoCamera && (videoRecording || cameraDevice != null));
        if (shutterFrame != null) shutterFrame.setEnabled(!videoStarting && !switchingVideoCamera && (videoRecording || cameraDevice != null));

        if (captureButton != null && shutterFrame != null) {
            boolean recUi = videoRecording || switchingVideoCamera;
            int fill = videoMode ? (recUi ? 0xfff0443c : 0xffd93632) : 0xfffaf9f5;
            int stroke = videoMode ? 0xffff827a : 0xfffaf9f5;
            captureButton.setBackground(round(fill, 100, fill, dp(2)));
            captureButton.setText(videoMode && recUi ? "■" : "");
            captureButton.setTextColor(videoMode ? 0xffffffff : BG);
            captureButton.setTextSize(recUi ? 18 : 12);
            captureButton.setContentDescription(videoMode
                    ? recUi ? "Stop video recording" : "Start video recording"
                    : "Take photo");
            shutterFrame.setBackground(round(0x00202020, 100, stroke, dp(2)));
        }

        if (cameraState != null && outputModeText != null) {
            if (switchingVideoCamera) {
                cameraState.setText("SWITCHING CAMERA · CONTINUING TAKE…");
            } else if (videoRecording) {
                cameraState.setText(videoPaused ? "VIDEO PAUSED" : "VIDEO RECORDING");
            } else if (videoStarting) {
                cameraState.setText("STARTING VIDEO");
                outputModeText.setText("PREPARING VIDEO RECORDING");
            } else if (videoMode) {
                String codecChoice = AppSettings.get(this, AppSettings.VIDEO_CODEC, "H.264 · Universal");
                String codecTag = codecChoice.contains("HEVC") ? "HEVC" : "H.264";
                cameraState.setText(cameraDevice == null ? "WAITING FOR CAMERA" : "VIDEO READY");
                outputModeText.setText(codecTag + " VIDEO  ·  " + videoProfileLabel());
            } else if (rawSupported && rawSize != null) {
                cameraState.setText("RAW SENSOR READY  ·  " + Math.round(rawSize.getWidth() * rawSize.getHeight() / 1000000f) + " MP");
                outputModeText.setText("RAW SENSOR  ·  DNG ORIGINAL + LIKA LENS JPEG");
            } else if (jpegReader != null) {
                cameraState.setText("RAW NOT AVAILABLE  ·  JPEG LOOK FALLBACK");
                outputModeText.setText("JPEG FALLBACK  ·  SENSOR RAW NOT AVAILABLE");
            }
        }
    }

    private void styleModeButton(TextView button, boolean active) {
        button.setTextColor(active ? ACCENT : 0xffe9ede6);
        button.setBackground(round(active ? 0xe21c1d1b : 0xd8191e1e, 22,
                active ? ACCENT : 0x556e7872, dp(1)));
    }

    private void onShutterPressed() {
        if (videoMode) {
            if (videoRecording || switchingVideoCamera) stopVideoRecording(true);
            else startVideoRecording();
        } else capturePhoto();
    }

    private void setVideoScreenLocked(boolean locked) {
        videoScreenLocked = locked && (videoRecording || switchingVideoCamera);
        unlockHolding = false;
        if (videoLockButton != null) {
            videoLockButton.removeCallbacks(unlockHoldTick);
            videoLockButton.setText("HOLD 3 SECONDS");
        }
        updateVideoModeUi();
    }

    private void toggleVideoPause() {
        if (!videoRecording || mediaRecorder == null || switchingVideoCamera) return;
        try {
            if (!videoPaused) {
                mediaRecorder.pause();
                videoPaused = true;
                videoPauseStartedAt = SystemClock.elapsedRealtime();
                accumulatedVideoDurationMs += Math.max(0L, videoPauseStartedAt - videoStartedAt);
                showMessage("Recording paused");
            } else {
                mediaRecorder.resume();
                videoPaused = false;
                videoStartedAt = SystemClock.elapsedRealtime();
                showMessage("Recording resumed");
            }
            updateVideoModeUi();
        } catch (RuntimeException e) {
            showMessage("Pause is not supported on this encoder.");
        }
    }

    private void captureVideoStillFrame() {
        if (!videoRecording || textureView == null || !textureView.isAvailable()) return;
        triggerShutterWink();
        playShutterSound(android.media.MediaActionSound.SHUTTER_CLICK);
        Bitmap frameBmp = textureView.getBitmap();
        if (frameBmp == null) {
            showMessage("Could not capture video frame.");
            return;
        }
        boolean mirror = isFrontCamera() && AppSettings.getBoolean(this, AppSettings.MIRROR_FRONT, true);
        String vAspect = AppSettings.get(this, AppSettings.VIDEO_ASPECT, "16:9 Widescreen");
        String targetAspect = vAspect.contains("2.39") ? "2.39:1" : "16:9";
        processor.execute(() -> {
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                frameBmp.compress(Bitmap.CompressFormat.JPEG, 95, bos);
                frameBmp.recycle();
                Bitmap developed = RawDeveloper.developJpeg(bos.toByteArray(), this, mirror);
                if (developed != null) {
                    developed = cropToAspect(developed, targetAspect);
                    String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date());
                    String prefix = resolvedFilePrefix();
                    writeJpeg(developed, prefix + "_STILL_" + stamp + ".jpg", null, "");
                    developed.recycle();
                    runOnUiThread(() -> {
                        showMessage("Video still saved to Photos");
                        updateGalleryButton();
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> showMessage("Could not save video still."));
            }
        });
    }

    private void pauseCurrentSegmentForCameraSwitch() {
        if (!videoRecording || mediaRecorder == null) return;
        switchingVideoCamera = true;
        if (!videoPaused) {
            accumulatedVideoDurationMs += Math.max(0L, SystemClock.elapsedRealtime() - videoStartedAt);
        }
        videoPaused = false;
        File finishedSegment = videoTempFile;
        videoTempFile = null;
        try {
            mediaRecorder.stop();
            if (finishedSegment != null && finishedSegment.isFile() && finishedSegment.length() > 1024) {
                synchronized (videoSegments) {
                    videoSegments.add(finishedSegment);
                }
            } else if (finishedSegment != null) {
                finishedSegment.delete();
            }
        } catch (RuntimeException e) {
            if (finishedSegment != null) finishedSegment.delete();
        }
        videoRecording = false;
        releaseMediaRecorder();
        closeSessionOnly();
        releaseRecordingPreviewSurface();
    }

    private void startVideoRecording() {
        if (!videoMode || videoRecording || videoStarting || switchingVideoCamera) return;
        if (cameraDevice == null || !started) {
            showMessage("Camera is warming up.");
            return;
        }
        List<String> requestedPermissions = new ArrayList<>();
        String audioSetting = AppSettings.get(this, AppSettings.VIDEO_AUDIO, "Camcorder · Stereo");
        boolean wantAudio = !audioSetting.startsWith("Muted");
        if (wantAudio && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestedPermissions.add(Manifest.permission.RECORD_AUDIO);
        }
        if (Build.VERSION.SDK_INT < 29
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestedPermissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (!requestedPermissions.isEmpty()) {
            pendingVideoStart = true;
            requestPermissions(requestedPermissions.toArray(new String[0]), VIDEO_PERMISSION);
            return;
        }
        synchronized (videoSegments) {
            for (File f : videoSegments) if (f != null && f.exists()) f.delete();
            videoSegments.clear();
        }
        accumulatedVideoDurationMs = 0L;
        videoPaused = false;
        lockedVideoOrientation = jpegOrientation();
        beginVideoRecording(wantAudio && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED);
    }

    private void beginVideoRecording(boolean withAudio) {
        if (!videoMode || videoRecording || videoStarting || cameraDevice == null) return;
        pendingVideoStart = false;
        videoStarting = true;
        runOnUiThread(this::updateVideoModeUi);
        if (cameraHandler == null) {
            failVideoStart("Camera is not ready for video.");
            return;
        }
        cameraHandler.post(() -> prepareVideoRecording(withAudio));
    }

    private void prepareVideoRecording(boolean withAudio) {
        if (!videoMode || (!videoStarting && !switchingVideoCamera) || cameraDevice == null) {
            failVideoStart("Video recording could not start.");
            return;
        }
        boolean isContinuation = switchingVideoCamera;
        try {
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (videoSize == null || !isContinuation) {
                videoSize = map == null ? null : chooseVideoSize(map.getOutputSizes(MediaRecorder.class));
            }
            if (videoSize == null) throw new IllegalStateException("No video size is supported");

            String audioPref = AppSettings.get(this, AppSettings.VIDEO_AUDIO, "Camcorder · Stereo");
            boolean recordAudio = withAudio && !audioPref.startsWith("Muted");

            String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date());
            videoTempFile = File.createTempFile("LIKA_VIDEO_SEG_" + stamp + "_", ".mp4", getCacheDir());
            mediaRecorder = new MediaRecorder();
            if (recordAudio) {
                int audioSrc = MediaRecorder.AudioSource.CAMCORDER;
                if (audioPref.startsWith("Unprocessed") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    audioSrc = MediaRecorder.AudioSource.UNPROCESSED;
                } else if (audioPref.startsWith("Voice")) {
                    audioSrc = MediaRecorder.AudioSource.VOICE_RECOGNITION;
                }
                try {
                    mediaRecorder.setAudioSource(audioSrc);
                } catch (RuntimeException fallbackMic) {
                    mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                }
            }
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            String codecPref = AppSettings.get(this, AppSettings.VIDEO_CODEC, "H.264 · Universal");
            boolean wantHevc = codecPref.contains("HEVC") && !isContinuation;
            try {
                mediaRecorder.setVideoEncoder(wantHevc ? MediaRecorder.VideoEncoder.HEVC : MediaRecorder.VideoEncoder.H264);
            } catch (RuntimeException fallbackH264) {
                mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            }
            if (recordAudio) {
                mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                mediaRecorder.setAudioChannels(2);
                mediaRecorder.setAudioSamplingRate(48000);
            }
            mediaRecorder.setVideoSize(videoSize.getWidth(), videoSize.getHeight());
            String fpsChoice = AppSettings.get(this, AppSettings.VIDEO_FPS, "24 fps (Cinematic)");
            int targetFps = fpsChoice.startsWith("60") ? 60 : fpsChoice.startsWith("30") ? 30 : 24;
            mediaRecorder.setVideoFrameRate(targetFps);

            String bitrateChoice = AppSettings.get(this, AppSettings.VIDEO_BITRATE, "High · 24 Mbps");
            int bitrate = bitrateChoice.startsWith("Ultra") ? 35_000_000
                    : bitrateChoice.startsWith("Standard") ? 14_000_000
                    : 24_000_000;
            mediaRecorder.setVideoEncodingBitRate(bitrate);
            if (recordAudio) mediaRecorder.setAudioEncodingBitRate(160_000);
            int orientHint = lockedVideoOrientation >= 0 ? lockedVideoOrientation : jpegOrientation();
            mediaRecorder.setOrientationHint(orientHint);
            mediaRecorder.setOutputFile(videoTempFile.getAbsolutePath());
            mediaRecorder.prepare();

            closeSessionOnly();
            SurfaceTexture texture = textureView.getSurfaceTexture();
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            recordingPreviewSurface = new Surface(texture);
            List<Surface> outputs = Arrays.asList(recordingPreviewSurface, mediaRecorder.getSurface());
            cameraDevice.createCaptureSession(outputs, new CameraCaptureSession.StateCallback() {
                @Override public void onConfigured(CameraCaptureSession session) {
                    if (cameraDevice == null || mediaRecorder == null) {
                        session.close();
                        failVideoStart("Video recording could not start.");
                        return;
                    }
                    captureSession = session;
                    try {
                        CaptureRequest.Builder request = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                        request.addTarget(recordingPreviewSurface);
                        request.addTarget(mediaRecorder.getSurface());
                        request.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
                        previewRequest = request;
                        if (characteristics != null) {
                            Range<Integer>[] fpsRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
                            Range<Integer> targetRange = null;
                            if (fpsRanges != null) {
                                for (Range<Integer> r : fpsRanges) {
                                    if (r.getUpper() == targetFps) {
                                        if (targetRange == null || r.getLower() == targetFps) {
                                            targetRange = r;
                                            if (r.getLower() == targetFps) break;
                                        }
                                    }
                                }
                                if (targetRange == null) {
                                    for (Range<Integer> r : fpsRanges) {
                                        if (targetRange == null || Math.abs(r.getUpper() - targetFps) < Math.abs(targetRange.getUpper() - targetFps)) {
                                            targetRange = r;
                                        }
                                    }
                                }
                            }
                            if (targetRange != null) {
                                request.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, targetRange);
                            }
                        }
                        setExposure(request);
                        setFocusMode(request);
                        setFlash(request, true);
                        applyOpticalSettings(request, true);
                        applyZoom(request);
                        session.setRepeatingRequest(request.build(), null, cameraHandler);
                        mediaRecorder.start();
                        if (!isContinuation) {
                            playShutterSound(android.media.MediaActionSound.START_VIDEO_RECORDING);
                        }
                        videoStarting = false;
                        switchingVideoCamera = false;
                        videoRecording = true;
                        videoPaused = false;
                        videoStartedAt = SystemClock.elapsedRealtime();
                        runOnUiThread(() -> {
                            configureTransform(textureView.getWidth(), textureView.getHeight());
                            textureView.animate().alpha(1f).setDuration(140).start();
                            updateVideoModeUi();
                            outputModeText.removeCallbacks(videoTimerTick);
                            outputModeText.post(videoTimerTick);
                        });
                    } catch (CameraAccessException | RuntimeException e) {
                        failVideoStart("Video recording could not start.");
                    }
                }

                @Override public void onConfigureFailed(CameraCaptureSession session) {
                    session.close();
                    failVideoStart("This camera could not configure video recording.");
                }
            }, cameraHandler);
        } catch (Exception e) {
            failVideoStart("Video recording could not start.");
        }
    }

    private Size chooseVideoSize(Size[] sizes) {
        if (sizes == null || sizes.length == 0) return null;
        return Arrays.stream(sizes)
                .filter(s -> s.getWidth() >= s.getHeight() && s.getWidth() <= 1920 && s.getHeight() <= 1080)
                .max(Comparator.comparingLong(s -> (long) s.getWidth() * s.getHeight()))
                .orElseGet(() -> Arrays.stream(sizes)
                        .filter(s -> s.getWidth() >= s.getHeight())
                        .min(Comparator.comparingLong(s -> Math.abs((long) s.getWidth() * s.getHeight() - 1920L * 1080L)))
                        .orElse(sizes[0]));
    }

    private String videoProfileLabel() {
        String fpsSetting = AppSettings.get(this, AppSettings.VIDEO_FPS, "24 fps (Cinematic)");
        String fpsLabel = fpsSetting.startsWith("60") ? "60 FPS" : fpsSetting.startsWith("30") ? "30 FPS" : "24 FPS CINEMATIC";
        String vAspect = AppSettings.get(this, AppSettings.VIDEO_ASPECT, "16:9 Widescreen");
        String aspectLabel = vAspect.contains("2.39") ? " · 2.39:1 SCOPE" : "";
        if (videoSize == null) return "1080P / " + fpsLabel + aspectLabel;
        int vertical = Math.min(videoSize.getWidth(), videoSize.getHeight());
        return vertical + "P / " + fpsLabel + aspectLabel;
    }

    private void failVideoStart(String message) {
        boolean hadPreviousSegments;
        synchronized (videoSegments) {
            hadPreviousSegments = !videoSegments.isEmpty();
        }
        videoStarting = false;
        switchingVideoCamera = false;
        videoRecording = false;
        videoPaused = false;
        videoScreenLocked = false;
        releaseMediaRecorder();
        closeSessionOnly();
        releaseRecordingPreviewSurface();
        if (videoTempFile != null) videoTempFile.delete();
        videoTempFile = null;
        if (hadPreviousSegments) {
            List<File> toPublish;
            synchronized (videoSegments) {
                toPublish = new ArrayList<>(videoSegments);
                videoSegments.clear();
            }
            publishVideoSegments(toPublish);
        }
        runOnUiThread(() -> {
            updateVideoModeUi();
            showMessage(hadPreviousSegments ? "Camera switch unsupported · saving recorded segments…" : message);
            if (cameraDevice != null && textureView != null && textureView.isAvailable()) createPreviewSession();
        });
    }

    private void stopVideoRecording(boolean resumePreview) {
        if (videoStarting && !switchingVideoCamera) {
            pendingVideoStart = false;
            failVideoStart("Video start cancelled.");
            return;
        }
        if (!videoRecording && !switchingVideoCamera && mediaRecorder == null && videoSegments.isEmpty()) return;
        if (outputModeText != null) outputModeText.removeCallbacks(videoTimerTick);
        File recordedFile = videoTempFile;
        if (mediaRecorder != null && videoRecording) {
            try {
                mediaRecorder.stop();
                if (recordedFile != null && recordedFile.isFile() && recordedFile.length() > 1024) {
                    synchronized (videoSegments) {
                        videoSegments.add(recordedFile);
                    }
                } else if (recordedFile != null) {
                    recordedFile.delete();
                }
            } catch (RuntimeException ignored) {
                if (recordedFile != null) recordedFile.delete();
            }
        }
        videoRecording = false;
        videoStarting = false;
        switchingVideoCamera = false;
        videoPaused = false;
        videoScreenLocked = false;
        unlockHolding = false;
        lockedVideoOrientation = -1;
        releaseMediaRecorder();
        closeSessionOnly();
        releaseRecordingPreviewSurface();
        videoTempFile = null;

        List<File> finishedSegments;
        synchronized (videoSegments) {
            finishedSegments = new ArrayList<>(videoSegments);
            videoSegments.clear();
        }
        boolean hasValidVideo = !finishedSegments.isEmpty();
        if (hasValidVideo) {
            playShutterSound(android.media.MediaActionSound.STOP_VIDEO_RECORDING);
            publishVideoSegments(finishedSegments);
        }
        updateVideoModeUi();
        if (hasValidVideo) {
            showMessage(finishedSegments.size() > 1
                    ? ("Stitching " + finishedSegments.size() + " camera segments & saving video…")
                    : "Saving video to Movies / Lika Lens…");
        } else {
            showMessage("Video was too short to save.");
        }
        if (resumePreview && cameraDevice != null && textureView != null && textureView.isAvailable()) createPreviewSession();
    }

    private void releaseMediaRecorder() {
        if (mediaRecorder == null) return;
        try { mediaRecorder.reset(); } catch (RuntimeException ignored) { }
        try { mediaRecorder.release(); } catch (RuntimeException ignored) { }
        mediaRecorder = null;
    }

    private void releaseRecordingPreviewSurface() {
        if (recordingPreviewSurface == null) return;
        try { recordingPreviewSurface.release(); } catch (RuntimeException ignored) { }
        recordingPreviewSurface = null;
    }

    private void publishVideoSegments(List<File> segments) {
        processor.execute(() -> {
            File finalSource = null;
            Uri destination = null;
            File legacyFile = null;
            try {
                if (segments.size() == 1) {
                    finalSource = segments.get(0);
                } else {
                    String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date());
                    File stitched = File.createTempFile("LIKA_STITCHED_" + stamp + "_", ".mp4", getCacheDir());
                    if (stitchVideoSegments(segments, stitched)) {
                        finalSource = stitched;
                    } else {
                        stitched.delete();
                        finalSource = segments.get(0);
                    }
                }
                String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                String prefix = resolvedFilePrefix();
                String name = prefix + "_VIDEO_" + stamp + ".mp4";
                ContentValues values = new ContentValues();
                values.put(MediaStore.Video.Media.DISPLAY_NAME, name);
                values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
                if (Build.VERSION.SDK_INT >= 29) {
                    values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Lika Lens");
                    values.put(MediaStore.Video.Media.IS_PENDING, 1);
                } else {
                    File folder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Lika Lens");
                    if (!folder.exists() && !folder.mkdirs()) throw new IllegalStateException("Could not create Movies folder");
                    legacyFile = new File(folder, name);
                    values.put(MediaStore.Video.Media.DATA, legacyFile.getAbsolutePath());
                }
                destination = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
                if (destination == null) throw new IllegalStateException("Could not create video entry");
                try (InputStream input = new FileInputStream(finalSource);
                     OutputStream output = getContentResolver().openOutputStream(destination, "w")) {
                    if (output == null) throw new IllegalStateException("Could not open video destination");
                    byte[] buffer = new byte[64 * 1024];
                    int count;
                    while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                }
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues publish = new ContentValues();
                    publish.put(MediaStore.Video.Media.IS_PENDING, 0);
                    getContentResolver().update(destination, publish, null, null);
                } else if (legacyFile != null) {
                    MediaScannerConnection.scanFile(this, new String[]{legacyFile.getAbsolutePath()}, new String[]{"video/mp4"}, null);
                }
                final int countSegs = segments.size();
                runOnUiThread(() -> showMessage(countSegs > 1
                        ? ("Stitched " + countSegs + "-camera video saved to Movies / Lika Lens.")
                        : "Video saved to Movies / Lika Lens."));
            } catch (Exception e) {
                Log.e(TAG, "Failed to publish video", e);
                if (destination != null) {
                    try { getContentResolver().delete(destination, null, null); } catch (RuntimeException ignored) { }
                }
                if (legacyFile != null && legacyFile.exists()) legacyFile.delete();
                runOnUiThread(() -> showMessage("Could not save the video."));
            } finally {
                for (File seg : segments) {
                    if (seg != null && seg.exists()) seg.delete();
                }
                if (finalSource != null && finalSource.exists()) finalSource.delete();
            }
        });
    }

    /** Seamlessly stitches multiple MP4 segments recorded across camera/lens switches into one continuous MP4. */
    private boolean stitchVideoSegments(List<File> segments, File outputFile) {
        MediaMuxer muxer = null;
        try {
            muxer = new MediaMuxer(outputFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int outVideoTrack = -1;
            int outAudioTrack = -1;
            boolean muxerStarted = false;

            // Read orientation hint from first segment
            try {
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                retriever.setDataSource(segments.get(0).getAbsolutePath());
                String rot = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
                if (rot != null) {
                    muxer.setOrientationHint(Integer.parseInt(rot));
                }
                retriever.release();
            } catch (Throwable ignored) {}

            // Discover tracks from first valid segment
            for (File seg : segments) {
                if (seg == null || !seg.exists() || seg.length() == 0) continue;
                MediaExtractor extractor = new MediaExtractor();
                try {
                    extractor.setDataSource(seg.getAbsolutePath());
                    for (int i = 0; i < extractor.getTrackCount(); i++) {
                        MediaFormat fmt = extractor.getTrackFormat(i);
                        String mime = fmt.getString(MediaFormat.KEY_MIME);
                        if (mime == null) continue;
                        if (mime.startsWith("video/") && outVideoTrack < 0) {
                            outVideoTrack = muxer.addTrack(fmt);
                        } else if (mime.startsWith("audio/") && outAudioTrack < 0) {
                            outAudioTrack = muxer.addTrack(fmt);
                        }
                    }
                    if (outVideoTrack >= 0) break;
                } finally {
                    extractor.release();
                }
            }

            if (outVideoTrack < 0) return false;
            muxer.start();
            muxerStarted = true;

            ByteBuffer buffer = ByteBuffer.allocateDirect(2 * 1024 * 1024);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            long timelineOffsetUs = 0L;

            for (File seg : segments) {
                if (seg == null || !seg.exists() || seg.length() == 0) continue;
                MediaExtractor extractor = new MediaExtractor();
                try {
                    extractor.setDataSource(seg.getAbsolutePath());
                    int segVideoTrack = -1;
                    int segAudioTrack = -1;
                    for (int i = 0; i < extractor.getTrackCount(); i++) {
                        MediaFormat fmt = extractor.getTrackFormat(i);
                        String mime = fmt.getString(MediaFormat.KEY_MIME);
                        if (mime == null) continue;
                        if (mime.startsWith("video/") && segVideoTrack < 0) segVideoTrack = i;
                        else if (mime.startsWith("audio/") && segAudioTrack < 0) segAudioTrack = i;
                    }

                    long maxSegPtsUs = 0L;
                    if (segVideoTrack >= 0) {
                        extractor.selectTrack(segVideoTrack);
                        long firstVideoPts = -1L;
                        while (true) {
                            buffer.clear();
                            int sampleSize = extractor.readSampleData(buffer, 0);
                            if (sampleSize < 0) break;
                            long rawPts = extractor.getSampleTime();
                            if (firstVideoPts < 0) firstVideoPts = Math.max(0L, rawPts);
                            long relPts = Math.max(0L, rawPts - firstVideoPts);
                            info.offset = 0;
                            info.size = sampleSize;
                            info.presentationTimeUs = timelineOffsetUs + relPts;
                            info.flags = extractor.getSampleFlags();
                            muxer.writeSampleData(outVideoTrack, buffer, info);
                            if (relPts > maxSegPtsUs) maxSegPtsUs = relPts;
                            extractor.advance();
                        }
                        extractor.unselectTrack(segVideoTrack);
                    }

                    if (segAudioTrack >= 0 && outAudioTrack >= 0) {
                        extractor.selectTrack(segAudioTrack);
                        long firstAudioPts = -1L;
                        while (true) {
                            buffer.clear();
                            int sampleSize = extractor.readSampleData(buffer, 0);
                            if (sampleSize < 0) break;
                            long rawPts = extractor.getSampleTime();
                            if (firstAudioPts < 0) firstAudioPts = Math.max(0L, rawPts);
                            long relPts = Math.max(0L, rawPts - firstAudioPts);
                            info.offset = 0;
                            info.size = sampleSize;
                            info.presentationTimeUs = timelineOffsetUs + relPts;
                            info.flags = extractor.getSampleFlags();
                            muxer.writeSampleData(outAudioTrack, buffer, info);
                            if (relPts > maxSegPtsUs) maxSegPtsUs = relPts;
                            extractor.advance();
                        }
                        extractor.unselectTrack(segAudioTrack);
                    }

                    timelineOffsetUs += maxSegPtsUs + 33_333L;
                } finally {
                    extractor.release();
                }
            }

            if (muxerStarted) muxer.stop();
            return outputFile.exists() && outputFile.length() > 1024;
        } catch (Exception e) {
            Log.e(TAG, "Stitching video segments failed", e);
            return false;
        } finally {
            if (muxer != null) {
                try { muxer.release(); } catch (RuntimeException ignored) {}
            }
        }
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
        final int[] lastHapticVal = {selected};
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int next = min + progress;
                updateQuickValue(value, key, next);
                if (fromUser) {
                    AppSettings.prefs(MainActivity.this).edit().putInt(key, next).apply();
                    if (AppSettings.getBoolean(MainActivity.this, AppSettings.HAPTICS, true)) {
                        boolean isLandmark = false;
                        if (AppSettings.EXPOSURE.equals(key) && next == 0) isLandmark = true;
                        else if (AppSettings.WARMTH.equals(key) && next == 50) isLandmark = true;
                        else if (AppSettings.CONTRAST.equals(key) && next == 100) isLandmark = true;
                        else if (AppSettings.SHADOWS.equals(key) && next == 50) isLandmark = true;
                        else if (AppSettings.SATURATION.equals(key) && next == 100) isLandmark = true;
                        else if (AppSettings.HIGHLIGHTS.equals(key) && next == 50) isLandmark = true;
                        else if (AppSettings.GRAIN.equals(key) && next == 0) isLandmark = true;
                        else if (AppSettings.HALATION.equals(key) && next == 0) isLandmark = true;
                        else if (AppSettings.SHARPEN.equals(key) && next == 50) isLandmark = true;

                        if (isLandmark) {
                            bar.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                            lastHapticVal[0] = next;
                        } else if (Math.abs(next - lastHapticVal[0]) >= 6) {
                            bar.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                            lastHapticVal[0] = next;
                        }
                    }
                }
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

    private TextView lensPill(String zoomName, String mmName, boolean active) {
        TextView view = new TextView(this);
        view.setGravity(Gravity.CENTER);
        view.setLineSpacing(dp(-1), 1f);
        view.setText(formatLensSpannable(zoomName, mmName, active));
        view.setBackground(round(active ? 0xe21c1d1b : 0xd8191e1e, 22, active ? ACCENT : 0x556e7872, dp(1)));
        view.setClickable(true);
        return view;
    }

    private CharSequence formatLensSpannable(String zoomName, String mmName, boolean active) {
        android.text.SpannableString s = new android.text.SpannableString(zoomName + "\n" + mmName);
        int split = zoomName.length();
        s.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, split, 0);
        s.setSpan(new android.text.style.RelativeSizeSpan(0.72f), split + 1, s.length(), 0);
        s.setSpan(new android.text.style.ForegroundColorSpan(active ? ACCENT : 0xffe9ede6), 0, split, 0);
        s.setSpan(new android.text.style.ForegroundColorSpan(active ? 0xccffffff : 0x88a0aaa4), split + 1, s.length(), 0);
        return s;
    }

    private void handleViewfinderTap(float x, float y) {
        if (textureView == null || captureSession == null || previewRequest == null || cameraDevice == null) return;
        RectF frame = framingRect(textureView.getWidth(), textureView.getHeight());
        if (!frame.contains(x, y)) return;

        if (AppSettings.getBoolean(this, AppSettings.HAPTICS, true)) {
            captureButton.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }

        String metering = AppSettings.get(this, AppSettings.RETICLE_METERING, "AF + AE Metering");
        boolean doAf = !metering.contains("AE Only");
        boolean doAe = !metering.contains("AF Only");

        Rect activeArray = characteristics != null ? characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) : null;
        if (activeArray != null) {
            float nx = (x - frame.left) / frame.width();
            float ny = (y - frame.top) / frame.height();
            nx = Math.max(0f, Math.min(1f, nx));
            ny = Math.max(0f, Math.min(1f, ny));

            int sensorX = (int) (ny * activeArray.width());
            int sensorY = (int) ((1f - nx) * activeArray.height());

            int boxW = Math.max(50, activeArray.width() / 10);
            int boxH = Math.max(50, activeArray.height() / 10);
            Rect focusRect = new Rect(
                    Math.max(0, sensorX - boxW / 2),
                    Math.max(0, sensorY - boxH / 2),
                    Math.min(activeArray.width(), sensorX + boxW / 2),
                    Math.min(activeArray.height(), sensorY + boxH / 2)
            );

            MeteringRectangle meteringRect = new MeteringRectangle(focusRect, MeteringRectangle.METERING_WEIGHT_MAX);
            currentAfRegions = doAf ? new MeteringRectangle[]{meteringRect} : null;
            currentAeRegions = doAe ? new MeteringRectangle[]{meteringRect} : null;
            hasManualFocusPoint = doAf;

            try {
                Integer maxAf = characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF);
                Integer maxAe = characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE);
                if (doAf) {
                    previewRequest.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
                    if (maxAf != null && maxAf > 0) previewRequest.set(CaptureRequest.CONTROL_AF_REGIONS, currentAfRegions);
                    previewRequest.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
                }
                if (doAe && maxAe != null && maxAe > 0) {
                    previewRequest.set(CaptureRequest.CONTROL_AE_REGIONS, currentAeRegions);
                }

                captureSession.capture(previewRequest.build(), null, cameraHandler);
                if (doAf) previewRequest.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
                captureSession.setRepeatingRequest(previewRequest.build(), null, cameraHandler);
            } catch (Exception ignored) {}
        }

        if (focusReticle != null) {
            focusReticle.showAt(x, y, currentEvOffset);
        }
        scheduleFocusReticleHide();
    }

    private void handleViewfinderDoubleTap() {
        String action = AppSettings.get(this, AppSettings.VIEWFINDER_DOUBLE_TAP, "Toggle 1× / 2× zoom");
        if ("Toggle 1× / 2× zoom".equals(action)) {
            if (AppSettings.getBoolean(this, AppSettings.HAPTICS, true) && captureButton != null) {
                captureButton.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            }
            chooseLens(requestedZoom >= 1.8f ? 1.0f : 2.0f);
        } else if ("Reset exposure bias".equals(action)) {
            setExposureCompensationEv(0f);
            if (focusReticle != null) focusReticle.updateEv(0f);
            if (AppSettings.getBoolean(this, AppSettings.HAPTICS, true) && captureButton != null) {
                captureButton.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            }
            showMessage("EV RESET TO 0.0");
        }
    }

    private void handleViewfinderLongPress(float x, float y) {
        String action = AppSettings.get(this, AppSettings.VIEWFINDER_LONG_PRESS, "AE / AF lock");
        if ("AE / AF lock".equals(action)) {
            handleViewfinderTap(x, y);
            if (!aeLocked) toggleAeLock();
            showMessage("AE / AF LOCKED");
        } else if ("Lock exposure only (AE-L)".equals(action)) {
            toggleAeLock();
        } else if ("Reset exposure bias".equals(action)) {
            setExposureCompensationEv(0f);
            if (focusReticle != null) focusReticle.updateEv(0f);
            if (AppSettings.getBoolean(this, AppSettings.HAPTICS, true) && captureButton != null) {
                captureButton.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            }
            showMessage("EV RESET TO 0.0");
        }
    }

    private final Runnable hideFocusReticleRunnable = new Runnable() {
        @Override public void run() {
            if (focusReticle != null) focusReticle.fadeOut();
            resetFocusToContinuous();
        }
    };

    private void scheduleFocusReticleHide() {
        focusHandler.removeCallbacks(hideFocusReticleRunnable);
        focusHandler.postDelayed(hideFocusReticleRunnable, 3500);
    }

    private void resetFocusToContinuous() {
        hasManualFocusPoint = false;
        currentAfRegions = null;
        currentAeRegions = null;
        if (captureSession != null && previewRequest != null && cameraHandler != null) {
            try {
                previewRequest.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                previewRequest.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
                Integer maxAf = characteristics != null ? characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) : null;
                Integer maxAe = characteristics != null ? characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE) : null;
                if (maxAf != null && maxAf > 0) previewRequest.set(CaptureRequest.CONTROL_AF_REGIONS, null);
                if (maxAe != null && maxAe > 0) previewRequest.set(CaptureRequest.CONTROL_AE_REGIONS, null);
                captureSession.setRepeatingRequest(previewRequest.build(), null, cameraHandler);
            } catch (Exception ignored) {}
        }
    }

    private void setExposureCompensationEv(float targetEv) {
        currentEvOffset = targetEv;
        if (characteristics == null || previewRequest == null || captureSession == null) return;
        Range<Integer> range = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
        android.util.Rational step = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP);
        if (range == null || step == null) return;

        float stepVal = step.floatValue();
        if (stepVal <= 0) stepVal = 1f / 3f;

        int index = Math.round(targetEv / stepVal);
        index = Math.max(range.getLower(), Math.min(range.getUpper(), index));
        currentAeIndex = index;

        try {
            previewRequest.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, currentAeIndex);
            captureSession.setRepeatingRequest(previewRequest.build(), null, cameraHandler);
        } catch (Exception ignored) {}
    }

    private void toggleAeLock() {
        if (characteristics == null || previewRequest == null || captureSession == null) return;
        aeLocked = !aeLocked;
        if (captureButton != null && AppSettings.getBoolean(this, AppSettings.HAPTICS, true)) {
            captureButton.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
        }
        try {
            previewRequest.set(CaptureRequest.CONTROL_AE_LOCK, aeLocked);
            captureSession.setRepeatingRequest(previewRequest.build(), null, cameraHandler);
        } catch (Exception ignored) {}
        if (cameraState != null) {
            cameraState.setText(aeLocked ? "AE-L  ·  EXPOSURE LOCKED" : "EXPOSURE UNLOCKED");
            if (!aeLocked) updateVideoModeUi();
        }
        if (shutterValueText != null) shutterValueText.setTextColor(aeLocked ? ACCENT : 0xfff0f1ea);
        if (isoValueText != null) isoValueText.setTextColor(aeLocked ? ACCENT : 0xfff0f1ea);
    }

    private GradientDrawable round(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        if (strokeWidth > 0) d.setStroke(strokeWidth, strokeColor);
        return d;
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private boolean isFrontCamera() {
        if (characteristics == null) return false;
        Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
        return facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT;
    }

    private String resolvedFilePrefix() {
        String prefix = AppSettings.get(this, AppSettings.FILE_PREFIX, "LIKA");
        if (prefix.startsWith("CUSTOM") || prefix.startsWith("Custom")) {
            String custom = AppSettings.get(this, AppSettings.CUSTOM_PREFIX_VALUE, "LIKA").trim();
            return custom.isEmpty() ? "LIKA" : custom;
        }
        return prefix;
    }

    private void updateQuickButtons() {
        if (aspectButton != null) aspectButton.setText(AppSettings.get(this, AppSettings.ASPECT_RATIO, "4:3"));
        if (videoFpsButton != null) {
            String fps = AppSettings.get(this, AppSettings.VIDEO_FPS, "24 fps (Cinematic)");
            videoFpsButton.setText(fps.startsWith("24") ? "24 FPS" : fps.startsWith("30") ? "30 FPS" : "60 FPS");
        }
        if (videoAspectButton != null) {
            String vAsp = AppSettings.get(this, AppSettings.VIDEO_ASPECT, "16:9 Widescreen");
            videoAspectButton.setText(vAsp.contains("2.39") ? "2.39:1" : "16:9");
        }
        if (timerButton != null) {
            String timer = AppSettings.get(this, AppSettings.TIMER, "Off");
            timerButton.setText("◷  " + (timer.startsWith("3") ? "3S" : timer.startsWith("10") ? "10S" : "OFF"));
        }
        if (flashButton != null) {
            boolean flashAvailable = flashSupported || isFrontCamera();
            String flash = AppSettings.get(this, AppSettings.FLASH, "Off");
            flashButton.setText("ϟ  " + (!flashAvailable ? "OFF" : flash.toUpperCase(Locale.US)));
            flashButton.setTextColor("Off".equals(flash) || !flashAvailable ? 0xffe9ede6 : ACCENT);
            flashButton.setAlpha(flashAvailable ? 1f : .72f);
        }
    }

    private void cycleTimer() {
        String current = AppSettings.get(this, AppSettings.TIMER, "Off");
        String next = current.startsWith("Off") ? "3 seconds" : current.startsWith("3") ? "10 seconds" : "Off";
        AppSettings.prefs(this).edit().putString(AppSettings.TIMER, next).apply();
        updateQuickButtons();
    }

    private void cycleFlash() {
        boolean front = isFrontCamera();
        if (!flashSupported && !front) {
            showMessage("This camera does not report a flash unit.");
            return;
        }
        String current = AppSettings.get(this, AppSettings.FLASH, "Off");
        String next = current.startsWith("Off") ? "Torch" : current.startsWith("Torch") ? "Auto" : "Off";
        AppSettings.prefs(this).edit().putString(AppSettings.FLASH, next).apply();
        updateQuickButtons();
        if (front && !"Off".equals(next)) {
            showMessage("RETINA SCREEN FLASH · Warm illumination active");
        }
        refreshPreviewRequest();
    }

    private void chooseAspectRatio() {
        String current = AppSettings.get(this, AppSettings.ASPECT_RATIO, "4:3");
        int selected = Math.max(0, Arrays.asList(AppSettings.ASPECT_RATIOS).indexOf(current));
        showCustomBottomSheet("FRAME SHAPE", AppSettings.ASPECT_RATIO_LABELS, null, selected, which -> {
            AppSettings.prefs(this).edit().putString(AppSettings.ASPECT_RATIO, AppSettings.ASPECT_RATIOS[which]).apply();
            updateQuickButtons();
            if (gridOverlay != null) gridOverlay.invalidate();
            if (cameraDevice != null && characteristics != null) {
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map != null) {
                    Size selectedPreview = choosePreviewSize(map.getOutputSizes(SurfaceTexture.class));
                    if (selectedPreview != null) previewSize = selectedPreview;
                    closeSessionOnly();
                    createPreviewSession();
                }
            }
        });
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
            if (!switchingVideoCamera || videoSize == null) {
                videoSize = chooseVideoSize(map.getOutputSizes(MediaRecorder.class));
            }
            int[] capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
            Size[] rawOutputs = map.getOutputSizes(ImageFormat.RAW_SENSOR);
            rawSupported = capabilities != null && Arrays.stream(capabilities).anyMatch(v -> v == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)
                    && rawOutputs != null && rawOutputs.length > 0;
            manualSupported = capabilities != null && Arrays.stream(capabilities).anyMatch(v -> v == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR);
            AppSettings.prefs(this).edit().putBoolean(AppSettings.MANUAL_SUPPORTED, manualSupported).apply();
            updateReadouts();
            useRawForThisCamera = rawSupported && !forceJpegForWide;
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
        String defaultLens = AppSettings.get(this, AppSettings.DEFAULT_LENS, "Auto / Wide 1×");
        String lastId = AppSettings.get(this, AppSettings.LAST_CAMERA_ID, null);

        String best = null;
        long bestArea = -1;
        boolean foundRaw = false;
        float focal = 1f;

        if ("Last used".equals(defaultLens) && lastId != null) {
            for (String id : cameraManager.getCameraIdList()) {
                if (id.equals(lastId)) {
                    CameraCharacteristics c = cameraManager.getCameraCharacteristics(id);
                    float[] lenses = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                    best = id;
                    focal = lenses != null && lenses.length > 0 ? lenses[0] : 1f;
                    break;
                }
            }
        }

        if (best == null) {
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
            rawReader = ImageReader.newInstance(rawSize.getWidth(), rawSize.getHeight(), ImageFormat.RAW_SENSOR, 4);
            rawReader.setOnImageAvailableListener(this::onImageAvailable, cameraHandler);
            cameraState.setText("RAW SENSOR READY  ·  " + Math.round(rawSize.getWidth() * rawSize.getHeight() / 1000000f) + " MP");
            outputModeText.setText("RAW SENSOR  ·  DNG ORIGINAL + LIKA LENS JPEG");
        } else {
            Size jpegSize = chooseJpegSize(map.getOutputSizes(ImageFormat.JPEG));
            if (jpegSize == null) jpegSize = new Size(1920, 1080);
            jpegReader = ImageReader.newInstance(jpegSize.getWidth(), jpegSize.getHeight(), ImageFormat.JPEG, 4);
            jpegReader.setOnImageAvailableListener(this::onImageAvailable, cameraHandler);
            if (forceJpegForWide && rawSupported) {
                cameraState.setText("WIDE FOV READY  ·  JPEG INPUT");
                outputModeText.setText("WIDE SENSOR  ·  LIKA LENS RENDER");
            } else {
                cameraState.setText("RAW NOT AVAILABLE  ·  JPEG LOOK FALLBACK");
                outputModeText.setText("JPEG FALLBACK  ·  SENSOR RAW NOT AVAILABLE");
            }
        }
        updateVideoModeUi();
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
        float targetRatio = aspectRatioSetting();
        return Arrays.stream(sizes)
                .filter(s -> s.getWidth() >= s.getHeight())
                .min(Comparator.comparingDouble(s -> Math.abs((s.getWidth() / (double) s.getHeight()) - targetRatio) * 4.0
                        + Math.abs(s.getWidth() - 1280) / 4000.0))
                .orElse(sizes[0]);
    }

    private float aspectRatioSetting() {
        if (videoMode) {
            String vAspect = AppSettings.get(this, AppSettings.VIDEO_ASPECT, "16:9 Widescreen");
            if (vAspect.contains("2.39")) return 2.39f;
            return 16f / 9f;
        }
        return AppSettings.parseAspectRatio(AppSettings.get(this, AppSettings.ASPECT_RATIO, "4:3"));
    }

    private RectF framingRect(float width, float height) {
        float ratio = aspectRatioSetting();
        if (width < height) ratio = 1f / ratio;
        float frameWidth = width;
        float frameHeight = frameWidth / ratio;
        if (frameHeight > height) {
            frameHeight = height;
            frameWidth = frameHeight * ratio;
        }
        float left = (width - frameWidth) * .5f;
        float top = (height - frameHeight) * .5f;
        return new RectF(left, top, left + frameWidth, top + frameHeight);
    }

    private final CameraDevice.StateCallback cameraStateCallback = new CameraDevice.StateCallback() {
        @Override public void onOpened(CameraDevice camera) {
            openingCamera = false;
            cameraDevice = camera;
            started = true;
            if (switchingVideoCamera) {
                boolean hasMic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
                prepareVideoRecording(hasMic);
            } else if (pendingVideoStart) {
                pendingVideoStart = false;
                beginVideoRecording(checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED);
            } else createPreviewSession();
            runOnUiThread(MainActivity.this::updateVideoModeUi);
        }
        @Override public void onDisconnected(CameraDevice camera) {
            openingCamera = false;
            if (videoRecording || videoStarting || switchingVideoCamera || mediaRecorder != null) stopVideoRecording(false);
            camera.close();
            if (cameraDevice == camera) cameraDevice = null;
            started = false;
            runOnUiThread(MainActivity.this::updateVideoModeUi);
        }
        @Override public void onError(CameraDevice camera, int error) {
            openingCamera = false;
            if (videoRecording || videoStarting || switchingVideoCamera || mediaRecorder != null) stopVideoRecording(false);
            camera.close();
            if (cameraDevice == camera) cameraDevice = null;
            started = false;
            runOnUiThread(() -> {
                cameraState.setText("CAMERA ERROR · " + error);
                updateVideoModeUi();
            });
        }
    };

    private void createPreviewSession() {
        if (cameraDevice == null || textureView == null || !textureView.isAvailable()) return;
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            previewSurface = new Surface(texture);
            Surface sessionPreviewSurface = previewSurface;
            List<Surface> outputs = new ArrayList<>();
            outputs.add(sessionPreviewSurface);
            if (useRawForThisCamera && rawReader != null) outputs.add(rawReader.getSurface());
            else if (jpegReader != null) outputs.add(jpegReader.getSurface());
            cameraDevice.createCaptureSession(outputs, new CameraCaptureSession.StateCallback() {
                @Override public void onConfigured(CameraCaptureSession session) {
                    if (cameraDevice == null) { session.close(); return; }
                    captureSession = session;
                    try {
                        previewRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        previewRequest.addTarget(sessionPreviewSurface);
                        previewRequest.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
                        setExposure(previewRequest);
                        setFlash(previewRequest, true);
                        setFocusMode(previewRequest);
                        applyOpticalSettings(previewRequest, false);
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
        if (previewSurface != null) { previewSurface.release(); previewSurface = null; }
        previewRequest = null;
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
        if (pendingImage == null || (useRawForThisCamera && pendingResult == null)) return;
        Image image = pendingImage;
        TotalCaptureResult result = pendingResult;
        pendingImage = null;
        pendingResult = null;
        captureInFlight = false;
        runOnUiThread(() -> captureButton.setAlpha(1f));
        CameraCharacteristics capturedCharacteristics = characteristics;
        int capturedOrientation = jpegOrientation();
        String capturedAspect = AppSettings.get(this, AppSettings.ASPECT_RATIO, "4:3");
        boolean mirrorSelfie = isFrontCamera() && AppSettings.getBoolean(this, AppSettings.MIRROR_FRONT, true);
        RawDeveloper.Look captureLook = RawDeveloper.Look.fromPreferences(this);
        if (mirrorSelfie) captureLook.flipHorizontal = true;
        pendingRenders.incrementAndGet();
        processor.execute(() -> {
            boolean raw = image.getFormat() == ImageFormat.RAW_SENSOR;
            String archiveKey = null;
            String dngPolicy = AppSettings.get(this, AppSettings.DNG_POLICY, "Save DNG beside JPEG (Pictures/RAW)");
            boolean keepEditableSource = raw && !dngPolicy.startsWith("Do not save RAW")
                    && AppSettings.getBoolean(this, AppSettings.KEEP_RAW, true)
                    && !"Rendered JPEG only".equals(AppSettings.get(this, AppSettings.CAPTURE_FORMAT, "RAW + rendered JPEG"));
            try {
                String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date());
                String format = AppSettings.get(this, AppSettings.CAPTURE_FORMAT, "RAW + rendered JPEG");
                boolean saveDng = raw && dngPolicy.startsWith("Save DNG")
                        && (AppSettings.getBoolean(this, AppSettings.KEEP_RAW, true) || "DNG only".equals(format))
                        && !"Rendered JPEG only".equals(format);

                String prefix = resolvedFilePrefix();

                String namingPattern = AppSettings.get(this, AppSettings.FILE_NAMING, "Prefix + Timestamp");
                String baseFileStem;
                if (namingPattern.contains("Film Look")) {
                    String stockName = captureLook.brain != null ? captureLook.brain.replaceAll("[^A-Za-z0-9]", "") : "LIKA";
                    baseFileStem = stockName + "_" + stamp;
                } else if (namingPattern.contains("Roll counter")) {
                    int roll = AppSettings.getInt(this, "roll_counter", 1);
                    AppSettings.prefs(this).edit().putInt("roll_counter", roll + 1).apply();
                    baseFileStem = prefix + "_ROLL_" + String.format(Locale.US, "%04d", roll);
                } else {
                    baseFileStem = prefix + "_" + stamp;
                }

                String folderOrg = AppSettings.get(this, AppSettings.FOLDER_ORG, "Single folder (Pictures/Lika Lens)");
                String orgSub = "";
                if (folderOrg.contains("month")) {
                    orgSub = new SimpleDateFormat("yyyy-MM", Locale.US).format(new Date());
                } else if (folderOrg.contains("film look")) {
                    orgSub = captureLook.brain != null ? captureLook.brain.replaceAll("[^A-Za-z0-9_ ]", "").replace(' ', '_') : "LIKA_FILM";
                }

                archiveKey = prefix + "_" + stamp;
                boolean dngSaved = false;
                String dngWarning = null;
                if (saveDng) {
                    try {
                        String dngSubfolder = orgSub.isEmpty() ? "RAW" : orgSub + "/RAW";
                        writeDng(image, result, capturedCharacteristics, baseFileStem + ".dng", dngSubfolder);
                        dngSaved = true;
                    } catch (Exception dngError) {
                        dngWarning = dngError.getClass().getSimpleName();
                        Log.e(TAG, "DNG export failed; continuing with the Lika Lens render", dngError);
                    }
                }
                Bitmap developed;
                if (raw) {
                    RawArchive.Frame frame = RawArchive.write(this, archiveKey, image, capturedCharacteristics,
                            result, capturedOrientation, capturedAspect);
                    try {
                        developed = RawDeveloper.develop(frame, captureLook, 0);
                    } catch (IllegalArgumentException resolutionError) {
                        Log.w(TAG, "Full-resolution RAW render failed; retrying at a safe display size", resolutionError);
                        developed = RawDeveloper.develop(frame, captureLook, 3000);
                    }
                } else {
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);
                    developed = RawDeveloper.developJpeg(bytes, this, mirrorSelfie);
                }
                if (developed == null) throw new IllegalStateException("Renderer returned no image");
                if (!raw) {
                    developed = rotateForDisplay(developed);
                    developed = cropToAspect(developed, capturedAspect);
                }
                if (!raw || !"DNG only".equals(format) || !dngSaved) {
                    writeJpeg(developed, baseFileStem + ".jpg", result, orgSub);
                }
                if (raw && keepEditableSource) PhotoEditStore.save(this, archiveKey, captureLook);
                developed.recycle();
                String resultMessage = dngWarning != null ? "Photo rendered; DNG export failed"
                        : !raw ? "JPEG fallback captured and rendered"
                        : "DNG only".equals(format) ? "DNG original saved"
                        : dngSaved ? "DNG original + Lika Lens render saved" : "Lika Lens RAW render saved";
                runOnUiThread(() -> {
                    showMessage(resultMessage);
                    updateGalleryButton();
                });
            } catch (Exception e) {
                Log.e(TAG, "Photo development failed", e);
                String detail = e.getMessage();
                if (detail != null) {
                    detail = detail.replace('\n', ' ').trim();
                    if (detail.length() > 72) detail = detail.substring(0, 72);
                }
                final String errorDetail = detail;
                runOnUiThread(() -> {
                    showMessage("Photo render failed: " + e.getClass().getSimpleName()
                            + (errorDetail == null || errorDetail.isEmpty() ? "" : " · " + errorDetail));
                });
            } finally {
                image.close();
                if (raw && !keepEditableSource && archiveKey != null) RawArchive.delete(this, archiveKey);
                pendingRenders.decrementAndGet();
            }
        });
    }

    private void capturePhoto() {
        if (cameraDevice == null || !started) {
            showMessage("Camera is starting up…");
            return;
        }
        if (captureSession == null || captureInFlight) {
            return;
        }
        if (pendingRenders.get() >= 3) {
            showMessage("Developing previous frames…");
            return;
        }
        captureInFlight = true;
        if (AppSettings.getBoolean(this, AppSettings.HAPTICS, true)) captureButton.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
        int timer = parseTimer(AppSettings.get(this, AppSettings.TIMER, "Off"));
        captureButton.setAlpha(.65f);
        if (timer > 0) {
            showMessage("Capturing in " + timer + "…");
            cameraHandler.postDelayed(this::triggerCaptureWithScreenFlashIfNeeded, timer * 1000L);
        } else triggerCaptureWithScreenFlashIfNeeded();
    }

    private void triggerCaptureWithScreenFlashIfNeeded() {
        String flashMode = AppSettings.get(this, AppSettings.FLASH, "Off");
        if (isFrontCamera() && !"Off".equals(flashMode) && screenFlashOverlay != null) {
            runOnUiThread(() -> {
                WindowManager.LayoutParams lp = getWindow().getAttributes();
                final float prevBrightness = lp.screenBrightness;
                lp.screenBrightness = 1.0f;
                getWindow().setAttributes(lp);
                screenFlashOverlay.setAlpha(0.95f);
                screenFlashOverlay.setVisibility(View.VISIBLE);
                cameraHandler.postDelayed(() -> {
                    performCapture();
                    runOnUiThread(() -> {
                        screenFlashOverlay.animate().alpha(0f).setDuration(240).withEndAction(() -> {
                            screenFlashOverlay.setVisibility(View.GONE);
                            WindowManager.LayoutParams restore = getWindow().getAttributes();
                            restore.screenBrightness = prevBrightness;
                            getWindow().setAttributes(restore);
                        }).start();
                    });
                }, 320L);
            });
        } else {
            performCapture();
        }
    }

    private int parseTimer(String value) {
        if (value.startsWith("3")) return 3;
        if (value.startsWith("10")) return 10;
        return 0;
    }

    private void performCapture() {
        if (captureSession == null || cameraDevice == null) return;
        triggerShutterWink();
        playShutterSound(android.media.MediaActionSound.SHUTTER_CLICK);
        if (AppSettings.getBoolean(this, AppSettings.HAPTICS, true) && captureButton != null) {
            captureButton.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
        }
        try {
            Surface target = useRawForThisCamera && rawReader != null ? rawReader.getSurface() : jpegReader.getSurface();
            CaptureRequest.Builder still = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            still.addTarget(target);
            still.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
            setExposure(still);
            still.set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation());
            setFocusMode(still);
            setFlash(still, false);
            applyOpticalSettings(still, false);
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
    private Bitmap cropToAspect(Bitmap bitmap, String value) {
        if (value == null) value = AppSettings.get(this, AppSettings.ASPECT_RATIO, "4:3");
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

    private void writeDng(Image image, TotalCaptureResult result, CameraCharacteristics captureCharacteristics, String name, String subfolder) throws Exception {
        if (result == null || captureCharacteristics == null) throw new IllegalStateException("Missing capture metadata");
        OutputTarget target = createOutput(name, "image/x-adobe-dng", subfolder);
        boolean published = false;
        DngCreator creator = null;
        try {
            creator = new DngCreator(captureCharacteristics, result);
            try (OutputStream stream = target.open()) {
                creator.writeImage(stream, image);
            }
            target.publish();
            published = true;
        } finally {
            if (creator != null) creator.close();
            if (!published) target.discard();
        }
    }

    private void writeJpeg(Bitmap bitmap, String name, TotalCaptureResult result, String subfolder) throws Exception {
        OutputTarget target = createOutput(name, "image/jpeg", subfolder);
        boolean published = false;
        File temp = File.createTempFile("lika_export_", ".jpg", getCacheDir());
        try {
            String qualityChoice = AppSettings.get(this, AppSettings.JPEG_QUALITY, "High · 95% (Recommended)");
            int quality;
            if (qualityChoice.startsWith("Maximum")) quality = 98;
            else if (qualityChoice.startsWith("High")) quality = 95;
            else if (qualityChoice.startsWith("Balanced")) quality = 90;
            else if (qualityChoice.startsWith("Compact")) quality = 85;
            else quality = 95;

            try (FileOutputStream tempOut = new FileOutputStream(temp)) {
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, tempOut)) {
                    throw new IllegalStateException("JPEG encoder could not write the image");
                }
            }

            try {
                ExifInterface exif = new ExifInterface(temp.getAbsolutePath());
                exif.setAttribute(ExifInterface.TAG_MAKE, Build.MANUFACTURER);
                exif.setAttribute(ExifInterface.TAG_MODEL, Build.MODEL);
                exif.setAttribute(ExifInterface.TAG_SOFTWARE, "Lika Lens (Cinematic Raw)");
                String timeStamp = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(new Date());
                exif.setAttribute(ExifInterface.TAG_DATETIME, timeStamp);

                boolean writeExifParams = AppSettings.getBoolean(this, AppSettings.EXIF_PARAMS, true);
                if (writeExifParams && result != null) {
                    Long exp = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
                    if (exp != null && exp > 0) {
                        exif.setAttribute(ExifInterface.TAG_EXPOSURE_TIME, String.valueOf(exp / 1_000_000_000.0));
                    }
                    Integer iso = result.get(CaptureResult.SENSOR_SENSITIVITY);
                    if (iso != null && iso > 0) {
                        exif.setAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS, String.valueOf(iso));
                    }
                    Float focal = result.get(CaptureResult.LENS_FOCAL_LENGTH);
                    if (focal != null && focal > 0) {
                        exif.setAttribute(ExifInterface.TAG_FOCAL_LENGTH, String.valueOf(focal));
                    }
                    Float aperture = result.get(CaptureResult.LENS_APERTURE);
                    if (aperture != null && aperture > 0) {
                        exif.setAttribute(ExifInterface.TAG_F_NUMBER, String.valueOf(aperture));
                    }
                }

                boolean stripGeo = AppSettings.getBoolean(this, AppSettings.STRIP_GEO, false);
                if (stripGeo) {
                    exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, null);
                    exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, null);
                    exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, null);
                    exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, null);
                    exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, null);
                    exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, null);
                    exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, null);
                    exif.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, null);
                    exif.setAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD, null);
                }
                exif.saveAttributes();
            } catch (Exception exifError) {
                Log.w(TAG, "Could not write EXIF attributes to JPEG", exifError);
            }

            try (InputStream in = new FileInputStream(temp); OutputStream out = target.open()) {
                byte[] buf = new byte[32768];
                int len;
                while ((len = in.read(buf)) != -1) {
                    out.write(buf, 0, len);
                }
            }
            target.publish();
            published = true;
        } finally {
            if (temp.exists()) temp.delete();
            if (!published) target.discard();
        }
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
        void discard() {
            if (uri != null) {
                try { getContentResolver().delete(uri, null, null); } catch (RuntimeException ignored) { }
            } else if (file != null) {
                try { file.delete(); } catch (RuntimeException ignored) { }
            }
        }
    }

    private void chooseBrain() {
        String[] brains = AppSettings.BRAIN_CHOICES;
        String[] descriptions = AppSettings.BRAIN_DESCRIPTIONS;
        String selected = AppSettings.brain(this);
        int checked = Math.max(0, Arrays.asList(brains).indexOf(selected));
        showCustomBottomSheet("PROCESSING BRAIN", brains, descriptions, checked, index -> {
            AppSettings.prefs(this).edit().putString(AppSettings.BRAIN, brains[index]).apply();
            updateBrainButton();
        });
    }

    private void showCustomBottomSheet(String title, String[] items, String[] subtitles, int selectedIndex, BottomSheetCallback callback) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
        }
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setBackground(round(0xff0e1314, 20, 0xff2a3233, dp(1)));
        sheet.setPadding(dp(20), dp(16), dp(20), dp(20));

        View handle = new View(this);
        handle.setBackground(round(0xff404846, 2, 0, 0));
        LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(dp(36), dp(4));
        handleLp.gravity = Gravity.CENTER_HORIZONTAL;
        handleLp.bottomMargin = dp(14);
        sheet.addView(handle, handleLp);

        TextView titleView = label(title.toUpperCase(Locale.US), 10, ACCENT, true);
        titleView.setLetterSpacing(.14f);
        titleView.setGravity(Gravity.CENTER);
        sheet.addView(titleView);

        View spacer = new View(this);
        sheet.addView(spacer, new LinearLayout.LayoutParams(-1, dp(12)));

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        for (int i = 0; i < items.length; i++) {
            final int index = i;
            boolean isCurrent = i == selectedIndex;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(12), dp(14), dp(12));
            row.setBackground(round(isCurrent ? 0x22d7b574 : 0x00000000, 10, isCurrent ? 0x66d7b574 : 0, isCurrent ? dp(1) : 0));

            LinearLayout copy = new LinearLayout(this);
            copy.setOrientation(LinearLayout.VERTICAL);
            TextView itemLabel = label(items[i], 11, isCurrent ? ACCENT : 0xffe6eae4, isCurrent);
            copy.addView(itemLabel);
            if (subtitles != null && i < subtitles.length && subtitles[i] != null && !subtitles[i].isEmpty()) {
                TextView sub = label(subtitles[i], 8, 0xff8a948e, false);
                sub.setPadding(0, dp(2), 0, 0);
                copy.addView(sub);
            }
            row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));

            if (isCurrent) {
                TextView check = label("✓", 12, ACCENT, true);
                row.addView(check);
            }

            row.setOnClickListener(v -> {
                if (AppSettings.getBoolean(this, AppSettings.HAPTICS, true)) {
                    row.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                }
                callback.onSelected(index);
                dialog.dismiss();
            });

            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
            rowLp.bottomMargin = dp(4);
            list.addView(row, rowLp);
        }
        scroll.addView(list, new FrameLayout.LayoutParams(-1, -2));
        sheet.addView(scroll, new LinearLayout.LayoutParams(-1, dp(Math.min(380, items.length * 54 + 10))));

        TextView cancel = pill("CLOSE", false);
        cancel.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(-1, dp(38));
        cancelLp.topMargin = dp(12);
        sheet.addView(cancel, cancelLp);
        sheet.setMinimumWidth(getResources().getDisplayMetrics().widthPixels);
        dialog.setContentView(sheet, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        }
    }

    private interface BottomSheetCallback {
        void onSelected(int index);
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
            String label;
            if (brain.startsWith("KODACHROME")) label = "KODA";
            else if (brain.startsWith("FUJI") || brain.contains("ETERNA")) label = "ETERNA";
            else if (brain.startsWith("BLEACH")) label = "BLEACH";
            else if (brain.startsWith("MONO")) label = "MONO";
            else if (brain.startsWith("NEUTRAL")) label = "RAW";
            else label = "FILM";
            brainButton.setText(label + "  ⌄");
        }
    }

    private void updateEasyButton() {
        if (easyButton == null) return;
        if (videoMode) {
            easyButton.setVisibility(View.GONE);
            return;
        }
        easyButton.setVisibility(View.VISIBLE);
        boolean easy = AppSettings.getBoolean(this, AppSettings.EASY_MODE, true);
        if (easy) {
            easyButton.setText("★ EASY");
            easyButton.setTextColor(ACCENT);
            easyButton.setBackground(round(0x33d7b574, 15, ACCENT, dp(1)));
            if (modeNote != null) {
                modeNote.setText("SMART P&S  ·  SCENE & ILLUMINANT ADAPTIVE");
            }
        } else {
            easyButton.setText("EASY");
            easyButton.setTextColor(0xff9ea8a2);
            easyButton.setBackground(round(0xff181d1c, 15, 0x44606963, dp(1)));
            if (modeNote != null) {
                modeNote.setText("RAW DEVELOPED AFTER CAPTURE");
            }
        }
    }

    private void toggleEasyMode() {
        boolean current = AppSettings.getBoolean(this, AppSettings.EASY_MODE, true);
        boolean next = !current;
        AppSettings.prefs(this).edit().putBoolean(AppSettings.EASY_MODE, next).apply();
        updateEasyButton();
        showMessage(next ? "EASY P&S ON  ·  Smart film science & scene adaptive"
                         : "CUSTOM PRO MODE  ·  Manual developer controls active");
        if (AppSettings.getBoolean(this, AppSettings.HAPTICS, true) && easyButton != null) {
            easyButton.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
        }
    }

    private void chooseLens(float factor) {
        hapticClick(captureButton);
        if (cameraManager == null || mainCameraId == null) {
            requestedZoom = Math.max(.5f, factor);
            updateLensButtons(requestedZoom);
            return;
        }
        try {
            Integer facing = characteristics == null ? null : characteristics.get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                float frontWideZoom = factor < 1f ? supportedWideZoom(characteristics, factor) : -1f;
                if (frontWideZoom > 0f) {
                    physicalFocalFactor = 1f;
                    selectCameraForLens(cameraId, frontWideZoom, rawSupported);
                    return;
                }
                physicalFocalFactor = 1f;
                animateZoomTo(Math.max(1f, factor));
                return;
            }

            // Prefer a real wider RAW camera when the device exposes one.
            if (factor < 1f) {
                String rawWideId = null;
                float rawWideFactor = 1f;
                float rawWideScore = Float.MAX_VALUE;
                for (String id : cameraManager.getCameraIdList()) {
                    CameraCharacteristics c = cameraManager.getCameraCharacteristics(id);
                    Integer lensFacing = c.get(CameraCharacteristics.LENS_FACING);
                    if (lensFacing == null || lensFacing != CameraCharacteristics.LENS_FACING_BACK) continue;
                    float[] focal = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                    if (focal == null || focal.length == 0) continue;
                    int[] caps = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
                    StreamConfigurationMap map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    Size[] rawOutputs = map == null ? null : map.getOutputSizes(ImageFormat.RAW_SENSOR);
                    boolean raw = caps != null && Arrays.stream(caps).anyMatch(v -> v == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)
                            && rawOutputs != null && rawOutputs.length > 0;
                    float physical = focal[0] / Math.max(.1f, primaryFocalLength);
                    if (!raw || physical >= .95f) continue;
                    float candidate = Math.abs((float) Math.log(physical / factor));
                    if (candidate < rawWideScore) {
                        rawWideScore = candidate;
                        rawWideId = id;
                        rawWideFactor = physical;
                    }
                }
                if (rawWideId != null && rawWideScore < .12f) {
                    physicalFocalFactor = rawWideFactor;
                    selectCameraForLens(rawWideId, rawWideFactor, false);
                    return;
                }

                // A logical multi-camera can zoom below 1x by switching to its ultra-wide
                // sensor. RAW streams do not follow logical zoom on many devices, so use
                // the camera's JPEG stream for this one lens and keep the Lika render.
                CameraCharacteristics main = cameraManager.getCameraCharacteristics(mainCameraId);
                float logicalWideZoom = supportedWideZoom(main, factor);
                if (logicalWideZoom > 0f) {
                    physicalFocalFactor = 1f;
                    selectCameraForLens(mainCameraId, logicalWideZoom, true);
                    return;
                }

                // Fall back to a separately exposed wider JPEG camera on older camera stacks.
                String wideId = null;
                float wideFactor = 1f;
                float wideScore = Float.MAX_VALUE;
                for (String id : cameraManager.getCameraIdList()) {
                    CameraCharacteristics c = cameraManager.getCameraCharacteristics(id);
                    Integer lensFacing = c.get(CameraCharacteristics.LENS_FACING);
                    if (lensFacing == null || lensFacing != CameraCharacteristics.LENS_FACING_BACK) continue;
                    float[] focal = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                    if (focal == null || focal.length == 0) continue;
                    float physical = focal[0] / Math.max(.1f, primaryFocalLength);
                    if (physical >= .95f) continue;
                    float candidate = Math.abs((float) Math.log(physical / factor));
                    if (candidate < wideScore) {
                        wideScore = candidate;
                        wideId = id;
                        wideFactor = physical;
                    }
                }
                if (wideId != null) {
                    physicalFocalFactor = wideFactor;
                    float actualFactor = wideFactor > factor ? wideFactor : factor;
                    if (actualFactor > factor + .05f) {
                        showMessage("Using the widest available lens at " + String.format(Locale.US, "%.1fx", actualFactor) + ".");
                    }
                    selectCameraForLens(wideId, actualFactor, false);
                    return;
                }
                showMessage("This device does not expose a 0.5× camera view.");
                updateLensButtons(requestedZoom);
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
                StreamConfigurationMap map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                Size[] rawOutputs = map == null ? null : map.getOutputSizes(ImageFormat.RAW_SENSOR);
                raw = raw && rawOutputs != null && rawOutputs.length > 0;
                if (AppSettings.getBoolean(this, AppSettings.KEEP_RAW, true) && !raw) continue;
                float candidate = Math.abs((float) Math.log(Math.max(.1f, physical) / Math.max(.1f, factor)));
                if (candidate < score) { score = candidate; bestId = id; bestPhysical = physical; }
            }
            physicalFocalFactor = bestPhysical;
            selectCameraForLens(bestId, factor, false);
        } catch (CameraAccessException e) {
            showMessage("Lens selection is unavailable.");
        }
    }

    private float supportedWideZoom(CameraCharacteristics camera, float factor) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || camera == null) return -1f;
        Range<Float> range = camera.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
        if (range == null || range.getLower() > factor + .01f || range.getUpper() < 1f) return -1f;
        return Math.max(factor, range.getLower());
    }

    private void selectCameraForLens(String id, float factor, boolean jpegWide) {
        AppSettings.prefs(this).edit().putString(AppSettings.LAST_CAMERA_ID, id).apply();
        boolean mustRestart = !id.equals(cameraId) || forceJpegForWide != jpegWide;
        forceJpegForWide = jpegWide;
        if (mustRestart) {
            if (AppSettings.getBoolean(this, AppSettings.HAPTICS, true) && captureButton != null) {
                captureButton.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
            }
            requestedZoom = factor;
            updateLensButtons(factor);
            if (textureView != null) textureView.setAlpha(.82f);
            cameraId = id;
            restartCameraForLens(factor);
        } else {
            animateZoomTo(factor);
        }
    }

    private void restartCameraForLens(float factor) {
        if (videoRecording) {
            pauseCurrentSegmentForCameraSwitch();
        }
        requestedZoom = factor;
        updateLensButtons(factor);
        if (textureView != null) textureView.setAlpha(.82f);
        closeCamera();
        openCameraIfReady();
    }

    private void setInteractiveZoom(float factor) {
        if (videoScreenLocked || videoStarting || switchingVideoCamera) return;
        requestedZoom = Math.max(MIN_INTERACTIVE_ZOOM, Math.min(MAX_INTERACTIVE_ZOOM, factor));
        updateLensButtons(requestedZoom);
        if (previewRequest != null && captureSession != null) {
            try {
                applyZoom(previewRequest);
                captureSession.setRepeatingRequest(previewRequest.build(), null, cameraHandler);
            } catch (CameraAccessException | IllegalStateException ignored) { }
        }
    }

    private void finishInteractiveZoom() {
        if (videoScreenLocked || videoStarting || switchingVideoCamera) return;
        if (requestedZoom < 1f) chooseLens(requestedZoom);
        else chooseContinuousLens(requestedZoom);
    }

    /** Selects the longest physical lens that does not exceed the requested zoom. */
    private void chooseContinuousLens(float factor) {
        if (cameraManager == null || mainCameraId == null) {
            chooseLens(factor);
            return;
        }
        try {
            Integer facing = characteristics == null ? null : characteristics.get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                chooseLens(factor);
                return;
            }
            String bestId = mainCameraId;
            float bestPhysical = 1f;
            boolean keepRaw = AppSettings.getBoolean(this, AppSettings.KEEP_RAW, true);
            for (String id : cameraManager.getCameraIdList()) {
                CameraCharacteristics candidate = cameraManager.getCameraCharacteristics(id);
                Integer candidateFacing = candidate.get(CameraCharacteristics.LENS_FACING);
                if (candidateFacing == null || candidateFacing != CameraCharacteristics.LENS_FACING_BACK) continue;
                float[] focal = candidate.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                if (focal == null || focal.length == 0) continue;
                float physical = focal[0] / Math.max(.1f, primaryFocalLength);
                if (physical > factor + .001f || physical < bestPhysical) continue;
                int[] caps = candidate.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
                StreamConfigurationMap map = candidate.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                Size[] rawOutputs = map == null ? null : map.getOutputSizes(ImageFormat.RAW_SENSOR);
                boolean raw = caps != null && Arrays.stream(caps).anyMatch(v -> v == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)
                        && rawOutputs != null && rawOutputs.length > 0;
                if (keepRaw && !raw) continue;
                bestId = id;
                bestPhysical = physical;
            }
            physicalFocalFactor = bestPhysical;
            selectCameraForLens(bestId, factor, false);
        } catch (CameraAccessException e) {
            showMessage("Zoom selection is unavailable.");
        }
    }

    private void showZoomSlider() {
        if (zoomSliderPanel == null) return;
        zoomSliderPanel.removeCallbacks(hideZoomSlider);
        zoomSliderPanel.setVisibility(View.VISIBLE);
        updateZoomSliderUi();
    }

    private void scheduleZoomSliderHide() {
        if (zoomSliderPanel == null) return;
        zoomSliderPanel.removeCallbacks(hideZoomSlider);
        zoomSliderPanel.postDelayed(hideZoomSlider, 1800L);
    }

    private void updateZoomSliderUi() {
        if (zoomSliderValue != null) zoomSliderValue.setText(String.format(Locale.US, "%.1f×", requestedZoom));
        if (zoomSlider != null) {
            int progress = zoomProgressFor(requestedZoom);
            if (zoomSlider.getProgress() != progress) zoomSlider.setProgress(progress);
        }
    }

    private int zoomProgressFor(float zoom) {
        float t = (float) (Math.log(Math.max(MIN_INTERACTIVE_ZOOM, Math.min(MAX_INTERACTIVE_ZOOM, zoom)) / MIN_INTERACTIVE_ZOOM)
                / Math.log(MAX_INTERACTIVE_ZOOM / MIN_INTERACTIVE_ZOOM));
        return Math.max(0, Math.min(1000, Math.round(t * 1000f)));
    }

    private float zoomForProgress(int progress) {
        float t = Math.max(0, Math.min(1000, progress)) / 1000f;
        return MIN_INTERACTIVE_ZOOM * (float) Math.pow(MAX_INTERACTIVE_ZOOM / MIN_INTERACTIVE_ZOOM, t);
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
            if (videoRecording) {
                pauseCurrentSegmentForCameraSwitch();
            }
            cameraId = bestId;
            forceJpegForWide = false;
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
        float[] zooms = new float[]{.5f, 1f, 2f, 5f};
        String[] names = new String[]{".5×", "1×", "2×", "5×"};
        String[] mmEquivs = new String[]{"14mm", "28mm", "50mm", "135mm"};
        for (int i = 0; i < lensButtons.size(); i++) {
            TextView button = lensButtons.get(i);
            Object tag = button.getTag();
            if (!(tag instanceof Float)) continue;
            boolean active = Math.abs((Float) tag - factor) < .08f;
            if (i < names.length) {
                button.setText(formatLensSpannable(names[i], mmEquivs[i], active));
            }
            button.setBackground(round(active ? 0xe21c1d1b : 0xd8191e1e, 22, active ? ACCENT : 0x556e7872, dp(1)));
        }
        updateZoomSliderUi();
    }

    private void applyZoom(CaptureRequest.Builder builder) {
        if (characteristics == null) return;
        Rect active = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        if (active == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Range<Float> ratioRange = characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
            if (requestedZoom < 1f && ratioRange != null
                    && requestedZoom >= ratioRange.getLower() && requestedZoom <= ratioRange.getUpper()) {
                builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, requestedZoom);
                // Keep the crop shape equal to the sensor array while the logical camera
                // changes lenses; aspect-ratio framing is applied by the saved render.
                builder.set(CaptureRequest.SCALER_CROP_REGION, active);
                return;
            }
            builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, 1f);
        }
        float digital = Math.max(1f, requestedZoom / Math.max(.1f, physicalFocalFactor));
        int cropW = Math.max(1, Math.round(active.width() / digital));
        int cropH = Math.max(1, Math.round(active.height() / digital));
        int left = active.centerX() - cropW / 2;
        int top = active.centerY() - cropH / 2;
        builder.set(CaptureRequest.SCALER_CROP_REGION, new Rect(left, top, left + cropW, top + cropH));
    }

    private void setFocusMode(CaptureRequest.Builder builder) {
        if (hasManualFocusPoint && currentAfRegions != null) {
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
            builder.set(CaptureRequest.CONTROL_AF_REGIONS, currentAfRegions);
            return;
        }
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

    private void applyOpticalSettings(CaptureRequest.Builder builder, boolean isVideo) {
        if (characteristics == null || builder == null) return;
        String stab = AppSettings.get(this, AppSettings.STABILIZATION, "Cinema OIS + EIS");
        boolean wantOis = !stab.startsWith("Off");
        boolean wantEis = isVideo && stab.startsWith("Cinema");

        int[] oisModes = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION);
        if (oisModes != null) {
            int targetOis = wantOis ? CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON
                    : CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF;
            if (Arrays.stream(oisModes).anyMatch(m -> m == targetOis)) {
                builder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, targetOis);
            }
        }

        int[] eisModes = characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES);
        if (eisModes != null) {
            int targetEis = wantEis ? CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON
                    : CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF;
            if (Arrays.stream(eisModes).anyMatch(m -> m == targetEis)) {
                builder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, targetEis);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            boolean wantDistortion = AppSettings.getBoolean(this, AppSettings.OPTICAL_CORRECTION, true);
            int[] distModes = characteristics.get(CameraCharacteristics.DISTORTION_CORRECTION_AVAILABLE_MODES);
            if (distModes != null) {
                int wanted = wantDistortion
                        ? (isVideo ? CaptureRequest.DISTORTION_CORRECTION_MODE_FAST : CaptureRequest.DISTORTION_CORRECTION_MODE_HIGH_QUALITY)
                        : CaptureRequest.DISTORTION_CORRECTION_MODE_OFF;
                if (Arrays.stream(distModes).anyMatch(m -> m == wanted)) {
                    builder.set(CaptureRequest.DISTORTION_CORRECTION_MODE, wanted);
                }
            }
        }
    }

    private void setExposure(CaptureRequest.Builder builder) {
        String antiBanding = AppSettings.get(this, AppSettings.ANTI_BANDING, "Auto (50/60 Hz)");
        int abMode = CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO;
        if (antiBanding.startsWith("50")) {
            abMode = CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_50HZ;
        } else if (antiBanding.startsWith("60")) {
            abMode = CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_60HZ;
        } else if (antiBanding.startsWith("Off")) {
            abMode = CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_OFF;
        }
        builder.set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, abMode);

        String mode = AppSettings.get(this, AppSettings.EXPOSURE_MODE, "Auto exposure");
        if (!mode.startsWith("Manual") || !manualSupported) {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            builder.set(CaptureRequest.CONTROL_AE_LOCK, aeLocked);
            builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, currentAeIndex);
            if (currentAeRegions != null) {
                builder.set(CaptureRequest.CONTROL_AE_REGIONS, currentAeRegions);
            }
            return;
        }
        Range<Long> exposureRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
        Range<Integer> sensitivityRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
        if (exposureRange == null || sensitivityRange == null) {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            builder.set(CaptureRequest.CONTROL_AE_LOCK, aeLocked);
            builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, currentAeIndex);
            if (currentAeRegions != null) {
                builder.set(CaptureRequest.CONTROL_AE_REGIONS, currentAeRegions);
            }
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
            applyOpticalSettings(previewRequest, videoMode);
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
        if (shutterValueText != null) {
            if (manual) {
                shutterValueText.setText(AppSettings.get(this, AppSettings.MANUAL_SHUTTER, "1/120 sec").replace(" sec", ""));
            } else if (videoMode) {
                String fps = AppSettings.get(this, AppSettings.VIDEO_FPS, "24 fps (Cinematic)");
                shutterValueText.setText(fps.startsWith("24") ? "1/48 (180°)" : fps.startsWith("30") ? "1/60 (180°)" : "1/120 (180°)");
            } else {
                shutterValueText.setText("AUTO");
            }
        }
        if (isoValueText != null) isoValueText.setText(manual ? AppSettings.get(this, AppSettings.MANUAL_ISO, "ISO 100") : "AUTO");
        int wb = AppSettings.getInt(this, AppSettings.WHITE_BALANCE, 0);
        if (whiteBalanceValueText != null) whiteBalanceValueText.setText(wb <= 0 ? "AUTO" : wb + " K");
    }

    private void configureTransform(int viewWidth, int viewHeight) {
        if (textureView == null || previewSize == null || characteristics == null || viewWidth == 0 || viewHeight == 0) return;
        Integer sensorValue = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        Integer facingValue = characteristics.get(CameraCharacteristics.LENS_FACING);
        int sensorOrientation = sensorValue == null ? 0 : sensorValue;
        int facing = facingValue == null ? CameraCharacteristics.LENS_FACING_BACK : facingValue;
        int surfaceRotation = getWindowManager().getDefaultDisplay().getRotation();
        int surfaceRotationDegrees = surfaceRotation * 90;
        int sign = facing == CameraCharacteristics.LENS_FACING_FRONT ? 1 : -1;
        int relativeRotation = (sensorOrientation - surfaceRotationDegrees * sign + 360) % 360;
        boolean rotationRequired = relativeRotation % 180 != 0;
        RectF frame = framingRect(viewWidth, viewHeight);

        // First compensate for TextureView's non-uniform full-screen fill, then
        // scale the corrected image into the selected frame. The overlay masks
        // everything outside that frame so it matches the rendered JPEG crop.
        float scaleX;
        float scaleY;
        if (sensorOrientation == 0) {
            scaleX = rotationRequired
                    ? viewWidth / (float) previewSize.getWidth()
                    : viewWidth / (float) previewSize.getHeight();
            scaleY = rotationRequired
                    ? viewHeight / (float) previewSize.getHeight()
                    : viewHeight / (float) previewSize.getWidth();
        } else {
            scaleX = rotationRequired
                    ? viewWidth / (float) previewSize.getHeight()
                    : viewWidth / (float) previewSize.getWidth();
            scaleY = rotationRequired
                    ? viewHeight / (float) previewSize.getWidth()
                    : viewHeight / (float) previewSize.getHeight();
        }
        float finalScale = Math.max(scaleX, scaleY);
        float centerX = viewWidth / 2f;
        float centerY = viewHeight / 2f;
        Matrix matrix = new Matrix();
        if (rotationRequired) {
            matrix.setScale(1f / scaleX * finalScale, 1f / scaleY * finalScale, centerX, centerY);
        } else {
            matrix.setScale(
                    viewHeight / (float) viewWidth / scaleY * finalScale,
                    viewWidth / (float) viewHeight / scaleX * finalScale,
                    centerX,
                    centerY);
        }
        // Only compensate for actual display rotation. In portrait, the old
        // hard-coded 90° rotation turned the preview sideways and into a band.
        matrix.postRotate(-surfaceRotationDegrees, centerX, centerY);
        float frameScale = Math.min(frame.width() / viewWidth, frame.height() / viewHeight);
        matrix.postScale(frameScale, frameScale, centerX, centerY);
        textureView.setTransform(matrix);
    }

    private final class FocusReticleOverlay extends View {
        private float reticleX = -1f;
        private float reticleY = -1f;
        private float reticleScale = 1.0f;
        private float evBias = 0f;
        private int shadowDepth = 50;
        private boolean showEvSlider = false;
        private boolean isVisible = false;
        private final Paint reticlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint sunPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        FocusReticleOverlay(Activity activity) {
            super(activity);
            reticlePaint.setColor(ACCENT);
            reticlePaint.setStyle(Paint.Style.STROKE);
            reticlePaint.setStrokeWidth(dp(1.5f));

            sunPaint.setColor(ACCENT);
            sunPaint.setStyle(Paint.Style.STROKE);
            sunPaint.setStrokeWidth(dp(1.25f));

            textPaint.setColor(ACCENT);
            textPaint.setTextSize(dp(10));
            textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        }

        void showAt(float x, float y, float currentEv) {
            reticleX = x;
            reticleY = y;
            evBias = currentEv;
            shadowDepth = AppSettings.getInt(MainActivity.this, AppSettings.SHADOWS, AppSettings.DEFAULT_SHADOWS);
            showEvSlider = true;
            isVisible = true;
            setAlpha(1f);
            setVisibility(View.VISIBLE);

            animate().cancel();
            ValueAnimator anim = ValueAnimator.ofFloat(1.35f, 1.0f);
            anim.setDuration(160);
            anim.setInterpolator(new DecelerateInterpolator());
            anim.addUpdateListener(animation -> {
                reticleScale = (float) animation.getAnimatedValue();
                invalidate();
            });
            anim.start();
            invalidate();
        }

        void updateEv(float ev) {
            evBias = ev;
            invalidate();
        }

        void updateShadows(int shadows) {
            shadowDepth = Math.max(0, Math.min(100, shadows));
            invalidate();
        }

        void fadeOut() {
            animate().cancel();
            animate().alpha(0f).setDuration(350).withEndAction(() -> {
                isVisible = false;
                setVisibility(View.GONE);
            }).start();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (!isVisible || reticleX < 0 || reticleY < 0) return;

            float boxSize = dp(56) * reticleScale;
            float half = boxSize / 2f;
            float cornerLen = dp(12) * reticleScale;

            // Draw 4 corner brackets
            canvas.drawLine(reticleX - half, reticleY - half, reticleX - half + cornerLen, reticleY - half, reticlePaint);
            canvas.drawLine(reticleX - half, reticleY - half, reticleX - half, reticleY - half + cornerLen, reticlePaint);
            canvas.drawLine(reticleX + half, reticleY - half, reticleX + half - cornerLen, reticleY - half, reticlePaint);
            canvas.drawLine(reticleX + half, reticleY - half, reticleX + half, reticleY - half + cornerLen, reticlePaint);
            canvas.drawLine(reticleX - half, reticleY + half, reticleX - half + cornerLen, reticleY + half, reticlePaint);
            canvas.drawLine(reticleX - half, reticleY + half, reticleX - half, reticleY + half - cornerLen, reticlePaint);
            canvas.drawLine(reticleX + half, reticleY + half, reticleX + half - cornerLen, reticleY + half, reticlePaint);
            canvas.drawLine(reticleX + half, reticleY + half, reticleX + half, reticleY + half - cornerLen, reticlePaint);

            // Center reticle pip
            canvas.drawCircle(reticleX, reticleY, dp(1.5f), reticlePaint);

            if (showEvSlider) {
                float trackHeight = dp(80);
                float trackTop = reticleY - trackHeight / 2f;
                float trackBottom = reticleY + trackHeight / 2f;

                // Right-side EV Bias rail (Sun icon)
                float trackX = reticleX + half + dp(16);
                reticlePaint.setAlpha(80);
                canvas.drawLine(trackX, trackTop, trackX, trackBottom, reticlePaint);
                reticlePaint.setAlpha(255);

                float normEv = Math.max(-2f, Math.min(2f, evBias)) / 2f;
                float sunY = reticleY - normEv * (trackHeight / 2f);

                sunPaint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(trackX, sunY, dp(4), sunPaint);
                sunPaint.setStyle(Paint.Style.STROKE);
                for (int i = 0; i < 6; i++) {
                    double angle = i * Math.PI / 3.0;
                    float r1 = dp(5.5f);
                    float r2 = dp(8.0f);
                    canvas.drawLine(
                            trackX + (float) Math.cos(angle) * r1,
                            sunY + (float) Math.sin(angle) * r1,
                            trackX + (float) Math.cos(angle) * r2,
                            sunY + (float) Math.sin(angle) * r2,
                            sunPaint
                    );
                }

                String evText = String.format(Locale.US, "%+.1f", evBias);
                if (Math.abs(evBias) < 0.05f) evText = "0.0";
                canvas.drawText(evText, trackX + dp(12), sunY + dp(3.5f), textPaint);

                // Left-side Shadow Depth rail (Contrast / Half-Moon icon — Pixel Dual Exposure style)
                float leftTrackX = reticleX - half - dp(16);
                reticlePaint.setAlpha(80);
                canvas.drawLine(leftTrackX, trackTop, leftTrackX, trackBottom, reticlePaint);
                reticlePaint.setAlpha(255);

                float normSh = (Math.max(0, Math.min(100, shadowDepth)) - 50f) / 50f;
                float moonY = reticleY - normSh * (trackHeight / 2f);

                sunPaint.setStyle(Paint.Style.STROKE);
                canvas.drawCircle(leftTrackX, moonY, dp(5.5f), sunPaint);
                sunPaint.setStyle(Paint.Style.FILL);
                RectF arcRect = new RectF(leftTrackX - dp(5.5f), moonY - dp(5.5f), leftTrackX + dp(5.5f), moonY + dp(5.5f));
                canvas.drawArc(arcRect, 90f, 180f, true, sunPaint);

                String shText = shadowDepth + "%";
                float shWidth = textPaint.measureText(shText);
                canvas.drawText(shText, leftTrackX - dp(10) - shWidth, moonY + dp(3.5f), textPaint);
            }
        }
    }

    private final class GestureOverlay extends LinearLayout {
        GestureOverlay() {
            super(MainActivity.this);
            setOrientation(LinearLayout.VERTICAL);
        }

        @Override public boolean dispatchTouchEvent(MotionEvent event) {
            if (!videoScreenLocked && scaleGestureDetector != null) {
                scaleGestureDetector.onTouchEvent(event);
            }
            return super.dispatchTouchEvent(event);
        }
    }

    private final class ZoomPresetRow extends LinearLayout {
        private final int touchSlop = ViewConfiguration.get(MainActivity.this).getScaledTouchSlop();

        ZoomPresetRow() {
            super(MainActivity.this);
            setOrientation(LinearLayout.HORIZONTAL);
        }

        @Override public boolean onInterceptTouchEvent(MotionEvent event) {
            if (videoScreenLocked || videoStarting || switchingVideoCamera) return false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    zoomRowStartX = event.getX();
                    zoomRowStartY = event.getY();
                    zoomRowStartZoom = requestedZoom;
                    zoomRowDragging = false;
                    break;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getX() - zoomRowStartX;
                    float dy = event.getY() - zoomRowStartY;
                    if (Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)) {
                        zoomRowDragging = true;
                        showZoomSlider();
                        return true;
                    }
                    break;
            }
            return zoomRowDragging;
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (videoScreenLocked || videoStarting || switchingVideoCamera) return false;
            if (!zoomRowDragging) return false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_MOVE:
                    float distanceInDp = (event.getX() - zoomRowStartX) / getResources().getDisplayMetrics().density;
                    float targetZoom = zoomRowStartZoom * (float) Math.pow(2d, distanceInDp / 110d);
                    setInteractiveZoom(targetZoom);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    zoomRowDragging = false;
                    finishInteractiveZoom();
                    scheduleZoomSliderHide();
                    performClick();
                    return true;
                default:
                    return true;
            }
        }

        @Override public boolean performClick() {
            super.performClick();
            return true;
        }
    }

    private void closeReader(ImageReader reader) {
        if (reader != null) {
            try { reader.setOnImageAvailableListener(null, null); } catch (RuntimeException ignored) {}
            reader.close();
        }
    }

    private final class CompositionGrid extends View {
        private final Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint aidPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        CompositionGrid(Activity activity) {
            super(activity);
            maskPaint.setColor(0x99080b0c);
            gridPaint.setColor(0xb8f2f3ec);
            gridPaint.setStyle(Paint.Style.STROKE);
            gridPaint.setStrokeWidth(activity.getResources().getDisplayMetrics().density);
            framePaint.setColor(0x99d7b574);
            framePaint.setStyle(Paint.Style.STROKE);
            framePaint.setStrokeWidth(activity.getResources().getDisplayMetrics().density * 1.25f);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth(), h = getHeight();
            RectF frame = framingRect(w, h);
            canvas.drawRect(0, 0, w, frame.top, maskPaint);
            canvas.drawRect(0, frame.bottom, w, h, maskPaint);
            canvas.drawRect(0, frame.top, frame.left, frame.bottom, maskPaint);
            canvas.drawRect(frame.right, frame.top, w, frame.bottom, maskPaint);

            float density = getResources().getDisplayMetrics().density;

            // Live Zebra Stripes & Focus Peaking Overlays
            boolean showZebra = AppSettings.getBoolean(MainActivity.this, AppSettings.ZEBRA, false);
            boolean showPeaking = AppSettings.getBoolean(MainActivity.this, AppSettings.FOCUS_PEAKING, false);
            if (showZebra || showPeaking) {
                float cellW = frame.width() / 24f;
                float cellH = frame.height() / 24f;
                for (int gy = 0; gy < 24; gy++) {
                    for (int gx = 0; gx < 24; gx++) {
                        int idx = gy * 24 + gx;
                        float cellLeft = frame.left + gx * cellW;
                        float cellTop = frame.top + gy * cellH;
                        if (showZebra && liveZebraCells[idx]) {
                            aidPaint.setStyle(Paint.Style.STROKE);
                            aidPaint.setStrokeWidth(density * 1.1f);
                            aidPaint.setColor(0xBBFF6B57);
                            canvas.drawLine(cellLeft, cellTop + cellH, cellLeft + cellW, cellTop, aidPaint);
                            canvas.drawLine(cellLeft + cellW * 0.4f, cellTop + cellH, cellLeft + cellW, cellTop + cellH * 0.4f, aidPaint);
                        }
                        if (showPeaking && livePeakingCells[idx]) {
                            aidPaint.setStyle(Paint.Style.FILL);
                            aidPaint.setColor(0xCCDFBE7F);
                            canvas.drawCircle(cellLeft + cellW * 0.5f, cellTop + cellH * 0.5f, density * 1.8f, aidPaint);
                        }
                    }
                }
            }

            String style = AppSettings.get(MainActivity.this, AppSettings.GRID_STYLE, "None");
            float[] divisions = "2×2".equals(style) ? new float[]{.5f}
                    : "3×3".equals(style) ? new float[]{1f / 3f, 2f / 3f}
                    : "4×4".equals(style) ? new float[]{.25f, .5f, .75f}
                    : "Golden ratio".equals(style) ? new float[]{.382f, .618f}
                    : new float[0];
            for (float division : divisions) {
                float x = frame.left + frame.width() * division;
                float y = frame.top + frame.height() * division;
                canvas.drawLine(x, frame.top, x, frame.bottom, gridPaint);
                canvas.drawLine(frame.left, y, frame.right, y, gridPaint);
            }
            canvas.drawRect(frame, framePaint);

            // Live Luminance Histogram Overlay
            if (AppSettings.getBoolean(MainActivity.this, AppSettings.HISTOGRAM, false)) {
                float histW = density * 86f;
                float histH = density * 38f;
                float histRight = frame.right - density * 12f;
                float histLeft = histRight - histW;
                float histTop = Math.max(frame.top + density * 12f, density * 96f);
                float histBottom = histTop + histH;

                aidPaint.setStyle(Paint.Style.FILL);
                aidPaint.setColor(0xAA0C0F10);
                RectF histRect = new RectF(histLeft, histTop, histRight, histBottom);
                canvas.drawRoundRect(histRect, density * 6f, density * 6f, aidPaint);

                aidPaint.setStyle(Paint.Style.STROKE);
                aidPaint.setStrokeWidth(density * 0.9f);
                aidPaint.setColor(0x66D7B574);
                canvas.drawRoundRect(histRect, density * 6f, density * 6f, aidPaint);

                float barW = (histW - density * 8f) / 32f;
                float maxBarH = histH - density * 8f;
                aidPaint.setStyle(Paint.Style.FILL);
                for (int i = 0; i < 32; i++) {
                    float v = liveHistogramBins[i];
                    float bh = Math.max(density * 1.2f, v * maxBarH);
                    float bx = histLeft + density * 4f + i * barW;
                    aidPaint.setColor(i >= 30 ? 0xDDFF6B57 : 0xCCD7B574);
                    canvas.drawRect(bx, histBottom - density * 4f - bh, bx + Math.max(1f, barW - density * 0.6f), histBottom - density * 4f, aidPaint);
                }
            }

            boolean showLevel = AppSettings.getBoolean(MainActivity.this, AppSettings.VISUAL_LEVEL, true);
            if (showLevel) {
                float cx = frame.centerX();
                float cy = frame.centerY();
                String sens = AppSettings.get(MainActivity.this, AppSettings.HORIZON_SENSITIVITY, "Normal (±1.0°)");
                float deadband = sens.contains("0.5") ? 0.5f : 1.0f;
                String horizonMode = AppSettings.get(MainActivity.this, AppSettings.HORIZON_MODE, "Smart (near level only)");
                boolean smartNearOnly = horizonMode.startsWith("Smart");

                Paint levelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                levelPaint.setStyle(Paint.Style.STROKE);
                levelPaint.setStrokeWidth(density * 1.5f);

                boolean isFlatLay = Math.abs(smoothPitchAngle) > 65f;
                if (isFlatLay) {
                    // Top-Down Flat-Lay Crosshair Level (Pixel / iPhone style)
                    float offsetX = smoothTiltX * density * 90f;
                    float offsetY = smoothTiltY * density * 90f;
                    float dist = (float) Math.hypot(offsetX, offsetY);
                    float maxFlatDist = smartNearOnly ? density * 26f : Float.MAX_VALUE;
                    if (dist <= maxFlatDist) {
                        float flatAlpha = smartNearOnly ? Math.max(0f, Math.min(1f, (maxFlatDist - dist) / (density * 10f))) : 1f;
                        boolean aligned = dist <= density * (deadband * 2.2f);

                        levelPaint.setColor(aligned ? ACCENT : (0x00FFFFFF | (Math.min(0x66, Math.round(0x66 * flatAlpha)) << 24)));
                        levelPaint.setStrokeWidth(aligned ? density * 2.0f : density * 1.3f);
                        float crossSize = density * 12f;
                        // Fixed center crosshair
                        canvas.drawLine(cx - crossSize, cy, cx + crossSize, cy, levelPaint);
                        canvas.drawLine(cx, cy - crossSize, cx, cy + crossSize, levelPaint);
                        canvas.drawCircle(cx, cy, density * 16f, levelPaint);

                        // Floating gravity crosshair
                        float fx = aligned ? cx : cx + offsetX;
                        float fy = aligned ? cy : cy + offsetY;
                        levelPaint.setColor(aligned ? ACCENT : (0x00DFBE7F | (Math.min(0xCC, Math.round(0xCC * flatAlpha)) << 24)));
                        canvas.drawLine(fx - crossSize * 0.8f, fy, fx + crossSize * 0.8f, fy, levelPaint);
                        canvas.drawLine(fx, fy - crossSize * 0.8f, fx, fy + crossSize * 0.8f, levelPaint);
                        canvas.drawCircle(fx, fy, density * 8f, levelPaint);
                    }
                } else {
                    float absRoll = Math.abs(smoothRollAngle);
                    float maxVisibleRoll = smartNearOnly ? 12.0f : Float.MAX_VALUE;
                    if (absRoll <= maxVisibleRoll) {
                        float rollAlpha = smartNearOnly ? Math.max(0f, Math.min(1f, (maxVisibleRoll - absRoll) / 5.0f)) : 1f;
                        boolean isLevel = absRoll <= deadband;

                        // Fixed center reference reticle
                        levelPaint.setColor(0x00FFFFFF | (Math.min(0x55, Math.round(0x55 * rollAlpha)) << 24));
                        canvas.drawLine(cx - density * 44, cy, cx - density * 14, cy, levelPaint);
                        canvas.drawLine(cx + density * 14, cy, cx + density * 44, cy, levelPaint);
                        canvas.drawCircle(cx, cy, density * 2.5f, levelPaint);

                        // Rotating horizon bar
                        canvas.save();
                        canvas.rotate(-smoothRollAngle, cx, cy);
                        int barColor = isLevel ? ACCENT : (0x00FFFFFF | (Math.min(0x99, Math.round(0x99 * rollAlpha)) << 24));
                        levelPaint.setColor(barColor);
                        levelPaint.setStrokeWidth(isLevel ? density * 2.2f : density * 1.5f);
                        canvas.drawLine(cx - density * 56, cy, cx - density * 20, cy, levelPaint);
                        canvas.drawLine(cx + density * 20, cy, cx + density * 56, cy, levelPaint);
                        if (isLevel) {
                            levelPaint.setStyle(Paint.Style.FILL);
                            canvas.drawCircle(cx, cy, density * 3.5f, levelPaint);
                        }
                        canvas.restore();
                    }
                }
            }
        }
    }

    private void closeCamera() {
        if (!switchingVideoCamera && (videoRecording || videoStarting || mediaRecorder != null)) stopVideoRecording(false);
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
        if (requestCode == VIDEO_PERMISSION) {
            pendingVideoStart = false;
            boolean storageAllowed = Build.VERSION.SDK_INT >= 29
                    || checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
            if (!storageAllowed) {
                showMessage("Storage access is needed to save video on this Android version.");
                updateVideoModeUi();
                return;
            }
            boolean audioAllowed = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
            if (!audioAllowed) showMessage("Microphone access denied. Recording silent video.");
            if (cameraDevice != null && started) beginVideoRecording(audioAllowed);
            else pendingVideoStart = true;
            return;
        }
        if (requestCode == CAMERA_PERMISSION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) openCameraIfReady();
        else if (requestCode == CAMERA_PERMISSION) {
            cameraState.setText("CAMERA PERMISSION REQUIRED");
            showMessage("Allow camera access to use Lika Lens.");
        }
    }
}
