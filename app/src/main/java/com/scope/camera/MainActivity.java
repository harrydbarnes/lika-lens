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
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
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
import android.location.Location;
import android.location.LocationManager;
import android.media.ExifInterface;
import android.media.Image;
import android.media.ImageReader;
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
import android.os.Vibrator;
import android.os.VibratorManager;
import android.os.VibrationEffect;
import android.window.OnBackInvokedDispatcher;
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
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
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
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Lika Lens: Camera2 framing preview plus RAW_SENSOR/DNG capture and app-side development. */
public class MainActivity extends Activity implements TextureView.SurfaceTextureListener {
    private static final String TAG = "LikaLens";
    private static final int CAMERA_PERMISSION = 41;
    private static final int VIDEO_PERMISSION = 42;
    private static final int LOCATION_PERMISSION = 43;
    private static final int BG = 0xff0b0d0e;
    private static final int ACCENT = 0xffd7b574;
    private static final int MUTED = 0xffa0aaa4;
    private static final float MIN_INTERACTIVE_ZOOM = .5f;
    private static final float MAX_INTERACTIVE_ZOOM = 5f;

    private TextureView textureView;
    private TextView cameraState;
    private TextView aeLockBadge;
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
    private TextView videoStabButton;
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
    private TextView framesValueText;
    private TextView framesTopLabel;
    private View framesBox;
    private volatile boolean timerCountdownRunning = false;
    private volatile boolean timerTorchActive = false;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerCountdownRunnable;
    private long lastDirtyLensAlertAt = 0L;
    private int dirtyLensAccumulator = 0;
    private long palmTriggerCooldownUntil = 0L;
    private int palmGestureAccumulator = 0;
    private TextView zoomSliderValue;
    private final List<TextView> lensButtons = new ArrayList<>();
    private CompositionGrid gridOverlay;
    private View viewfinderSpacer;
    private SensorManager sensorManager;
    private Sensor orientationSensor;
    private float deviceRollAngle = 0f;
    private float smoothRollAngle = 0f;
    private float devicePitchAngle = 0f;
    private float smoothPitchAngle = 0f;
    private float smoothTiltX = 0f;
    private float smoothTiltY = 0f;
    private float lockedVideoPitch = 0f;
    private float lockedVideoRoll = 0f;
    private boolean hasLockedVideoAnchor = false;
    private long lastTripodCropUpdateAt = 0L;
    private float deviceStabilityScore = 0f;
    private long deviceStationarySince = 0L;
    private boolean isDeviceStationary = false;

    private final SensorEventListener sensorEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
                float[] rotationMatrix = new float[9];
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
                // In rotationMatrix, row 2 represents the world Up vector (0, 0, 1) in device coordinates:
                // rotationMatrix[6] = Up.x, rotationMatrix[7] = Up.y, rotationMatrix[8] = Up.z
                float upX = rotationMatrix[6];
                float upY = rotationMatrix[7];
                float upZ = rotationMatrix[8];

                // Elevation tilt relative to the horizon:
                // 0° when aiming at the horizon (optical axis horizontal)
                // +90° when aiming straight down at table / -90° straight up at sky
                float tiltDegrees = (float) Math.toDegrees(Math.asin(Math.max(-1f, Math.min(1f, upZ))));
                devicePitchAngle = tiltDegrees;
                smoothPitchAngle = smoothPitchAngle * 0.82f + tiltDegrees * 0.18f;

                smoothTiltX = smoothTiltX * 0.80f + upX * 0.20f;
                smoothTiltY = smoothTiltY * 0.80f + upY * 0.20f;

                // Roll angle around the camera optical axis (device Z)
                float rollAngle = (float) Math.toDegrees(Math.atan2(upX, upY));
                updateHorizonRoll(rollAngle);
            } else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                float ax = event.values[0];
                float ay = event.values[1];
                float az = event.values.length > 2 ? event.values[2] : 0f;
                float norm = Math.max(0.1f, (float) Math.sqrt(ax * ax + ay * ay + az * az));
                float upX = ax / norm;
                float upY = ay / norm;
                float upZ = az / norm;

                float tiltDeg = (float) Math.toDegrees(Math.asin(Math.max(-1f, Math.min(1f, upZ))));
                devicePitchAngle = tiltDeg;
                smoothPitchAngle = smoothPitchAngle * 0.82f + tiltDeg * 0.18f;

                smoothTiltX = smoothTiltX * 0.80f + upX * 0.20f;
                smoothTiltY = smoothTiltY * 0.80f + upY * 0.20f;

                float angle = (float) Math.toDegrees(Math.atan2(upX, upY));
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

        float deltaMovement = Math.abs(deviceRollAngle - smoothRollAngle) + Math.abs(devicePitchAngle - smoothPitchAngle);
        deviceStabilityScore = deviceStabilityScore * 0.90f + deltaMovement * 0.10f;
        long nowUptime = SystemClock.uptimeMillis();
        if (deviceStabilityScore < 0.14f) {
            if (deviceStationarySince == 0L) {
                deviceStationarySince = nowUptime;
            } else if (nowUptime - deviceStationarySince > 1200L) {
                isDeviceStationary = true;
            }
        } else {
            deviceStationarySince = 0L;
            isDeviceStationary = false;
        }

        if (gridOverlay != null) {
            gridOverlay.postInvalidate();
        }
        if (videoRecording && captureSession != null && previewRequest != null) {
            String stab = AppSettings.get(this, AppSettings.STABILIZATION, "Cinema OIS + EIS");
            if (stab.startsWith("Locked") && hasLockedVideoAnchor) {
                long now = SystemClock.uptimeMillis();
                if (now - lastTripodCropUpdateAt > 40L) {
                    lastTripodCropUpdateAt = now;
                    try {
                        applyZoom(previewRequest);
                        captureSession.setRepeatingRequest(previewRequest.build(), null, cameraHandler);
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
    }

    private View shutterWink;
    private View screenFlashOverlay;
    private android.media.MediaActionSound mediaActionSound;
    private Vibrator vibrator;

    private void hapticClick(View v) {
        if (!AppSettings.getBoolean(this, AppSettings.HAPTICS, true)) return;
        boolean vibrated = false;
        try {
            if (vibrator != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                    && vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)) {
                vibrator.vibrate(VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.85f).compose());
                vibrated = true;
            }
        } catch (Throwable ignored) {
        }
        if (!vibrated && v != null) {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
    }

    private void hapticDetent() {
        if (!AppSettings.getBoolean(this, AppSettings.HAPTICS, true)) return;
        boolean vibrated = false;
        try {
            if (vibrator != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                    && vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK)) {
                vibrator.vibrate(VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.65f).compose());
                vibrated = true;
            }
        } catch (Throwable ignored) {
        }
        if (!vibrated && captureButton != null) {
            captureButton.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
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
        } catch (Throwable t) {
            Log.w(TAG, "Failed to play shutter/video sound", t);
        }
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
    private GestureOverlay overlay;
    private LinearLayout header;
    private View topSpacer;
    private LinearLayout telemetry;
    private ZoomPresetRow lensRow;
    private LinearLayout modeRow;
    private LinearLayout lookRow;
    private FrameLayout shootRow;
    private View deckSpacer;
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
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final Object captureLock = new Object();
    private Image pendingImage;
    private TotalCaptureResult pendingResult;
    private final AtomicInteger pendingRenders = new AtomicInteger(0);
    private volatile boolean captureInFlight;
    private ValueAnimator zoomAnimator;
    private int lastActiveLensIndex = -1;
    private boolean volumeKeyHolding;
    private final Handler volumeKeyHandler = new Handler(Looper.getMainLooper());
    private Runnable volumeQuickTakeRunnable;
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
    private volatile boolean isQuickTake;
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

    // Live exposure aids state (32-bin histogram + 24x24 zebra & peaking grid)
    private final int[] liveHistogramBins = new int[32];
    private final int[] liveHistR = new int[32];
    private final int[] liveHistG = new int[32];
    private final int[] liveHistB = new int[32];
    private boolean liveHighlightClipped = false;
    private boolean liveShadowClipped = false;
    private static final int EXPOSURE_GRID_SIZE = 24;
    private final boolean[] liveZebraCells = new boolean[EXPOSURE_GRID_SIZE * EXPOSURE_GRID_SIZE];
    private final boolean[] livePeakingCells = new boolean[EXPOSURE_GRID_SIZE * EXPOSURE_GRID_SIZE];
    private int[] liveSamplePixels = new int[64 * 48];
    private int[] liveSampleLuma = new int[64 * 48];
    private final Runnable exposureAidsSampler = new Runnable() {
        @Override public void run() {
            if (textureView != null && textureView.isAvailable() && gridOverlay != null) {
                boolean wantHist = AppSettings.getBoolean(MainActivity.this, AppSettings.HISTOGRAM, false);
                boolean wantZebra = AppSettings.getBoolean(MainActivity.this, AppSettings.ZEBRA, false);
                boolean wantPeak = AppSettings.getBoolean(MainActivity.this, AppSettings.FOCUS_PEAKING, false);
                boolean wantDirty = AppSettings.getBoolean(MainActivity.this, AppSettings.DIRTY_LENS_WARNING, true);
                boolean wantPalm = AppSettings.getBoolean(MainActivity.this, AppSettings.PALM_TIMER, false)
                        && !videoMode && !videoRecording && !timerCountdownRunning && !captureInFlight;
                if (wantHist || wantZebra || wantPeak || wantDirty || wantPalm) {
                    try {
                        Bitmap sample = textureView.getBitmap(64, 48);
                        if (sample != null) {
                            computeExposureAids(sample, wantHist, wantZebra, wantPeak, wantDirty, wantPalm);
                            sample.recycle();
                            gridOverlay.invalidate();
                        }
                    } catch (Throwable t) {
                        Log.w(TAG, "Failed to sample texture for exposure aids", t);
                    }
                    textureView.postDelayed(this, (wantHist || wantZebra || wantPeak || wantPalm) ? 120L : 450L);
                    return;
                }
            }
            if (textureView != null) textureView.postDelayed(this, 450L);
        }
    };

    private void checkPalmGesture(int[] px, int w, int h) {
        long now = SystemClock.elapsedRealtime();
        if (now < palmTriggerCooldownUntil || timerCountdownRunning || captureInFlight || videoMode || videoRecording) {
            palmGestureAccumulator = 0;
            return;
        }
        int yStart = Math.max(2, (int) (h * 0.08f));
        int yEnd = Math.min(h - 2, (int) (h * 0.78f));
        int xStart = Math.max(2, (int) (w * 0.08f));
        int xEnd = Math.min(w - 2, (int) (w * 0.92f));
        int skinCount = 0;
        int minX = w, maxX = 0, minY = h, maxY = 0;

        for (int y = yStart; y < yEnd; y++) {
            int row = y * w;
            for (int x = xStart; x < xEnd; x++) {
                int c = px[row + x];
                int r = (c >> 16) & 0xff;
                int g = (c >> 8) & 0xff;
                int b = c & 0xff;
                if (isSkinTonePixel(r, g, b)) {
                    skinCount++;
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }

        int frameArea = w * h;
        int boxW = maxX - minX + 1;
        int boxH = maxY - minY + 1;
        if (skinCount < frameArea * 0.035f || skinCount > frameArea * 0.30f || boxW < 8 || boxH < 9) {
            palmGestureAccumulator = Math.max(0, palmGestureAccumulator - 1);
            return;
        }

        float aspect = boxW / (float) boxH;
        if (aspect < 0.48f || aspect > 1.55f) {
            palmGestureAccumulator = Math.max(0, palmGestureAccumulator - 1);
            return;
        }

        // Distinguish an open 5-finger raised palm from a solid oval face:
        // Across the upper third of the hand bounding box (finger region), horizontal scanlines
        // exhibit multiple skin-to-background finger transitions (>= 3 fingers separated by gaps).
        int fingerZoneTop = minY + Math.max(1, boxH / 10);
        int fingerZoneBottom = minY + Math.max(3, (boxH * 42) / 100);
        int maxRunsInRow = 0;
        int multiFingerRows = 0;
        for (int y = fingerZoneTop; y <= fingerZoneBottom && y < h; y++) {
            int row = y * w;
            int runs = 0;
            boolean inSkin = false;
            for (int x = minX; x <= maxX && x < w; x++) {
                int c = px[row + x];
                boolean skin = isSkinTonePixel((c >> 16) & 0xff, (c >> 8) & 0xff, c & 0xff);
                if (skin && !inSkin) {
                    runs++;
                    inSkin = true;
                } else if (!skin) {
                    inSkin = false;
                }
            }
            if (runs > maxRunsInRow) maxRunsInRow = runs;
            if (runs >= 3) multiFingerRows++;
        }

        // Also check palm base solidity in the lower-middle half of the bounding box
        int palmMidY = minY + (boxH * 62) / 100;
        int palmSkinPixels = 0;
        if (palmMidY < h) {
            int row = palmMidY * w;
            for (int x = minX; x <= maxX && x < w; x++) {
                int c = px[row + x];
                if (isSkinTonePixel((c >> 16) & 0xff, (c >> 8) & 0xff, c & 0xff)) {
                    palmSkinPixels++;
                }
            }
        }
        float palmSolidity = palmSkinPixels / (float) Math.max(1, boxW);

        if (maxRunsInRow >= 3 && multiFingerRows >= 2 && palmSolidity >= 0.35f) {
            palmGestureAccumulator++;
            if (palmGestureAccumulator >= 4) {
                palmGestureAccumulator = 0;
                palmTriggerCooldownUntil = now + 8000L;
                runOnUiThread(() -> {
                    if (!timerCountdownRunning && !captureInFlight && !videoMode && !videoRecording) {
                        showMessage("✋ Palm detected · 3s timer");
                        startTimerCountdown(3);
                    }
                });
            }
        } else {
            palmGestureAccumulator = Math.max(0, palmGestureAccumulator - 1);
        }
    }

    private static boolean isSkinTonePixel(int r, int g, int b) {
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        return r > 85 && g > 38 && b > 20
                && r > g && g >= (b - 10)
                && (max - min) > 16
                && (r - g) >= 10 && (r - b) >= 18;
    }

    private void checkDirtyLens(int[] luma, int w, int h) {
        int total = w * h;
        long sumLuma = 0;
        int minLuma = 255;
        int maxLuma = 0;
        for (int i = 0; i < total; i++) {
            int y = luma[i];
            sumLuma += y;
            if (y < minLuma) minLuma = y;
            if (y > maxLuma) maxLuma = y;
        }
        int meanLuma = (int) (sumLuma / total);
        if (meanLuma < 55 || meanLuma > 225) {
            dirtyLensAccumulator = 0;
            return;
        }
        long gradientSum = 0;
        int edgeCount = 0;
        for (int y = 1; y < h - 1; y++) {
            int row = y * w;
            for (int x = 1; x < w - 1; x++) {
                int idx = row + x;
                gradientSum += Math.abs(luma[idx + 1] - luma[idx - 1]) + Math.abs(luma[idx + w] - luma[idx - w]);
                edgeCount += 2;
            }
        }
        float avgGradient = edgeCount > 0 ? ((float) gradientSum / edgeCount) : 0f;
        boolean hasSmudgeBloom = (minLuma > 36 && maxLuma >= 210 && avgGradient < 4.4f);
        if (hasSmudgeBloom) {
            dirtyLensAccumulator++;
            if (dirtyLensAccumulator >= 6) {
                long now = SystemClock.elapsedRealtime();
                if (now - lastDirtyLensAlertAt > 90_000L) {
                    lastDirtyLensAlertAt = now;
                    dirtyLensAccumulator = 0;
                    runOnUiThread(() -> showMessage("Wipe lens · Smudge or haze detected"));
                }
            }
        } else {
            dirtyLensAccumulator = Math.max(0, dirtyLensAccumulator - 1);
        }
    }

    private void computeExposureAids(Bitmap bmp, boolean wantHist, boolean wantZebra, boolean wantPeak, boolean wantDirty, boolean wantPalm) {
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        int total = w * h;
        if (liveSamplePixels.length < total) {
            liveSamplePixels = new int[total];
            liveSampleLuma = new int[total];
        }
        int[] px = liveSamplePixels;
        int[] luma = liveSampleLuma;
        bmp.getPixels(px, 0, w, 0, 0, w, h);
        if (wantHist) {
            Arrays.fill(liveHistogramBins, 0);
            Arrays.fill(liveHistR, 0);
            Arrays.fill(liveHistG, 0);
            Arrays.fill(liveHistB, 0);
        }
        int clipHigh = 0;
        int clipLow = 0;
        for (int i = 0; i < total; i++) {
            int c = px[i];
            int r = (c >> 16) & 0xff;
            int g = (c >> 8) & 0xff;
            int b = c & 0xff;
            int y = (r * 77 + g * 150 + b * 29) >> 8;
            luma[i] = y;
            if (wantHist) {
                int bin = Math.min(31, y >> 3);
                liveHistogramBins[bin]++;
                liveHistR[Math.min(31, r >> 3)]++;
                liveHistG[Math.min(31, g >> 3)]++;
                liveHistB[Math.min(31, b >> 3)]++;
                if (y >= 250) clipHigh++;
                if (y <= 4) clipLow++;
            }
        }
        if (wantHist) {
            liveHighlightClipped = clipHigh > (total * 0.012f);
            liveShadowClipped = clipLow > (total * 0.020f);
        }
        if (wantZebra || wantPeak) {
            for (int cy = 0; cy < EXPOSURE_GRID_SIZE; cy++) {
                int sy = Math.min(h - 1, (cy * h + (h / 2)) / EXPOSURE_GRID_SIZE);
                int rowOff = sy * w;
                int gridRow = cy * EXPOSURE_GRID_SIZE;
                for (int cx = 0; cx < EXPOSURE_GRID_SIZE; cx++) {
                    int idx = gridRow + cx;
                    int sx = Math.min(w - 1, (cx * w + (w / 2)) / EXPOSURE_GRID_SIZE);
                    int pIdx = rowOff + sx;
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
        if (wantDirty && !isFrontCamera()) {
            checkDirtyLens(luma, w, h);
        }
        if (wantPalm) {
            checkPalmGesture(px, w, h);
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
        volumeQuickTakeRunnable = () -> {
            if (!volumeKeyHolding || videoRecording || videoStarting || switchingVideoCamera) return;
            isQuickTake = true;
            if (AppSettings.getBoolean(MainActivity.this, AppSettings.HAPTICS, true) && captureButton != null) {
                captureButton.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            }
            startVideoRecording();
        };
        AppSettings.migrateBranding(this);
        LutManager.init(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    () -> {
                        if (videoScreenLocked) {
                            if (videoLockButton != null) videoLockButton.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                            return;
                        }
                        finish();
                    }
            );
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vm = (VibratorManager) getSystemService(VIBRATOR_MANAGER_SERVICE);
                if (vm != null) vibrator = vm.getDefaultVibrator();
            } else {
                vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            }
        } catch (Throwable ignored) {
        }
        Window window = getWindow();
        if (window != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                window.setColorMode(android.content.pm.ActivityInfo.COLOR_MODE_WIDE_COLOR_GAMUT);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.view.Display display = getDisplay();
                if (display != null) {
                    android.view.Display.Mode[] modes = display.getSupportedModes();
                    android.view.Display.Mode bestMode = null;
                    for (android.view.Display.Mode m : modes) {
                        if (bestMode == null || m.getRefreshRate() > bestMode.getRefreshRate()) {
                            bestMode = m;
                        }
                    }
                    if (bestMode != null && bestMode.getRefreshRate() >= 85f) {
                        WindowManager.LayoutParams lp = window.getAttributes();
                        if (lp != null) {
                            lp.preferredDisplayModeId = bestMode.getModeId();
                            window.setAttributes(lp);
                        }
                    }
                }
            }
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            window.clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
            window.setStatusBarColor(0x00000000);
            window.setNavigationBarColor(0xff0b0d0e);
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                WindowManager.LayoutParams lp = window.getAttributes();
                if (lp != null) {
                    lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                    window.setAttributes(lp);
                }
            }
        }
        buildCameraUi();
        View decor = window != null ? window.getDecorView() : null;
        if (decor != null) {
            decor.setOnSystemUiVisibilityChangeListener(visibility -> {
                if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                    applyFullScreenImmersive();
                }
            });
        }
        applyFullScreenImmersive();
        if (root != null) {
            root.post(this::applyFullScreenImmersive);
        }
        cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            orientationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            if (orientationSensor == null) {
                orientationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            }
        }
        startBackgroundThread();
        String launchMode = AppSettings.get(this, AppSettings.LAUNCH_MODE, "Photo (default)");
        String intentAction = getIntent() != null ? getIntent().getAction() : null;
        boolean isVideoIntent = MediaStore.INTENT_ACTION_VIDEO_CAMERA.equals(intentAction)
                || MediaStore.ACTION_VIDEO_CAPTURE.equals(intentAction);
        boolean launchInVideo = isVideoIntent;
        if (!isVideoIntent) {
            if ("Cinematic Video".equals(launchMode)) {
                launchInVideo = true;
            } else if ("Last used".equals(launchMode)) {
                launchInVideo = AppSettings.getBoolean(this, AppSettings.LAST_USED_MODE, false);
            }
        }
        if (launchInVideo) {
            root.post(() -> setVideoMode(true));
        }
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION);
        } else if (AppSettings.getBoolean(this, AppSettings.LOCATION, false)) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_PERMISSION);
            }
        }
    }

    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        applyFullScreenImmersive();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyFullScreenImmersive();
        }
    }

    private void applyFullScreenImmersive() {
        try {
            Window window = getWindow();
            if (window == null) return;
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            window.clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);

            View decor = window.getDecorView();
            if (decor != null) {
                decor.setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                );
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false);
                WindowInsetsController controller = window.getInsetsController();
                if (controller == null && decor != null) {
                    controller = decor.getWindowInsetsController();
                }
                if (controller != null) {
                    controller.hide(WindowInsets.Type.statusBars());
                    controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
            }
        } catch (Throwable t) {
            Log.w("LikaLens", "Could not apply fullscreen immersive mode", t);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        applyFullScreenImmersive();
        if (root != null) {
            root.postDelayed(this::applyFullScreenImmersive, 150L);
        }
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
        AppUpdateManager.onResume(this);
        checkAutomaticUpdates();
    }

    private static boolean hasCheckedUpdatesThisSession = false;

    private void checkAutomaticUpdates() {
        if (!AppSettings.getBoolean(this, AppSettings.AUTO_CHECK_UPDATES, true)) return;
        long lastCheck = AppSettings.prefs(this).getLong(AppSettings.LAST_UPDATE_CHECK_TIME, 0L);
        long now = System.currentTimeMillis();
        // Check immediately on app cold-start, or every 4 hours if kept alive in memory
        if (hasCheckedUpdatesThisSession && (now - lastCheck < 4 * 3600 * 1000L)) return;
        hasCheckedUpdatesThisSession = true;

        AppUpdateManager.checkForUpdate(this, false, new AppUpdateManager.UpdateCallback() {
            @Override
            public void onUpdateAvailable(AppUpdateManager.UpdateInfo updateInfo) {
                if (!isFinishing() && !isDestroyed()) {
                    AppUpdateManager.showUpdateDialog(MainActivity.this, updateInfo);
                }
            }

            @Override
            public void onNoUpdateAvailable() {}

            @Override
            public void onError(String message) {
                Log.d(TAG, "Background update check notice: " + message);
            }
        });
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
        ioExecutor.shutdown();
        if (mediaActionSound != null) {
            try {
                mediaActionSound.release();
            } catch (Throwable t) {
                Log.w(TAG, "Failed to release mediaActionSound", t);
            }
            mediaActionSound = null;
        }
        volumeKeyHandler.removeCallbacks(volumeQuickTakeRunnable);
        volumeKeyHolding = false;
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
            String action = AppSettings.get(this, AppSettings.VOLUME_ROCKER, "Shutter");
            if ("Shutter release".equals(action)) action = "Shutter";
            else if ("Zoom 1× / 2×".equals(action)) action = "Zoom";

            if ("Volume".equals(action)) {
                return super.onKeyDown(keyCode, event);
            }
            if ("Off".equals(action)) {
                return true;
            }
            if ("Shutter".equals(action)) {
                if (event.getRepeatCount() == 0) {
                    volumeKeyHolding = true;
                    if (videoMode || videoRecording) {
                        onShutterPressed();
                    } else {
                        volumeKeyHandler.removeCallbacks(volumeQuickTakeRunnable);
                        volumeKeyHandler.postDelayed(volumeQuickTakeRunnable, 450L);
                    }
                }
                return true;
            }
            if ("Zoom".equals(action)) {
                boolean zoomIn = (keyCode == KeyEvent.KEYCODE_VOLUME_UP);
                if (event.getRepeatCount() == 0) {
                    if (zoomIn) {
                        if (requestedZoom < 0.95f) chooseLens(1.0f);
                        else if (requestedZoom < 1.95f) chooseLens(2.0f);
                        else if (requestedZoom < 4.95f) chooseLens(5.0f);
                        else animateZoomTo(Math.min(MAX_INTERACTIVE_ZOOM, requestedZoom + 1.0f));
                    } else {
                        if (requestedZoom > 4.8f) chooseLens(2.0f);
                        else if (requestedZoom > 1.8f) chooseLens(1.0f);
                        else if (requestedZoom > 0.95f) chooseLens(0.5f);
                        else animateZoomTo(Math.max(MIN_INTERACTIVE_ZOOM, requestedZoom - 0.2f));
                    }
                } else {
                    float delta = zoomIn ? 0.08f : -0.08f;
                    float next = Math.max(MIN_INTERACTIVE_ZOOM, Math.min(MAX_INTERACTIVE_ZOOM, requestedZoom + delta));
                    setInteractiveZoom(next);
                }
                return true;
            }
            if ("EV compensation bias".equals(action)) {
                if (event.getRepeatCount() == 0) {
                    float step = keyCode == KeyEvent.KEYCODE_VOLUME_UP ? 0.33f : -0.33f;
                    float newEv = Math.max(-2f, Math.min(2f, currentEvOffset + step));
                    setExposureCompensationEv(newEv);
                    if (focusReticle != null) focusReticle.updateEv(newEv);
                    showMessage(String.format(Locale.US, "EV %+.1f", newEv));
                }
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (videoScreenLocked) {
                return true;
            }
            String action = AppSettings.get(this, AppSettings.VOLUME_ROCKER, "Shutter");
            if ("Shutter release".equals(action)) action = "Shutter";
            else if ("Zoom 1× / 2×".equals(action)) action = "Zoom";

            if ("Volume".equals(action)) {
                return super.onKeyUp(keyCode, event);
            }
            if ("Off".equals(action)) {
                return true;
            }
            if ("Shutter".equals(action)) {
                volumeKeyHandler.removeCallbacks(volumeQuickTakeRunnable);
                if (volumeKeyHolding) {
                    volumeKeyHolding = false;
                    if (isQuickTake) {
                        isQuickTake = false;
                        if (AppSettings.getBoolean(this, AppSettings.HAPTICS, true) && captureButton != null) {
                            captureButton.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                        }
                        stopVideoRecording(true);
                    } else if (!videoMode && !videoRecording) {
                        onShutterPressed();
                    }
                }
                return true;
            }
            if ("Zoom".equals(action)) {
                finishInteractiveZoom();
                return true;
            }
            if ("EV compensation bias".equals(action)) {
                return true;
            }
        }
        return super.onKeyUp(keyCode, event);
    }

    private void buildCameraUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        root.setFitsSystemWindows(false);
        textureView = new TextureView(this);
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

        overlay = new GestureOverlay();
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        header = row();
        flashButton = pill("ϟ  OFF", false);
        header.addView(flashButton, new LinearLayout.LayoutParams(dp(56), dp(32)));
        flashButton.setOnClickListener(v -> { hapticClick(v); cycleFlash(); });
        aspectButton = pill("4:3", false);
        LinearLayout.LayoutParams aspectLp = new LinearLayout.LayoutParams(-2, dp(32));
        aspectLp.leftMargin = dp(6);
        header.addView(aspectButton, aspectLp);
        aspectButton.setOnClickListener(v -> { hapticClick(v); chooseAspectRatio(); });

        timerButton = pill("◷", false);
        LinearLayout.LayoutParams timerLp = new LinearLayout.LayoutParams(dp(38), dp(32));
        timerLp.leftMargin = dp(6);
        header.addView(timerButton, timerLp);
        timerButton.setOnClickListener(v -> { hapticClick(v); cycleTimer(); });

        videoFpsButton = pill("24 FPS", true);
        LinearLayout.LayoutParams fpsLp = new LinearLayout.LayoutParams(-2, dp(32));
        fpsLp.leftMargin = dp(6);
        videoFpsButton.setVisibility(View.GONE);
        header.addView(videoFpsButton, fpsLp);
        videoFpsButton.setOnClickListener(v -> { hapticClick(v); cycleVideoFps(); });

        videoAspectButton = pill("16:9", false);
        LinearLayout.LayoutParams vaspectLp = new LinearLayout.LayoutParams(-2, dp(32));
        vaspectLp.leftMargin = dp(6);
        videoAspectButton.setVisibility(View.GONE);
        header.addView(videoAspectButton, vaspectLp);
        videoAspectButton.setOnClickListener(v -> { hapticClick(v); cycleVideoAspect(); });

        videoStabButton = pill("OIS+EIS", false);
        LinearLayout.LayoutParams vstabLp = new LinearLayout.LayoutParams(-2, dp(32));
        vstabLp.leftMargin = dp(6);
        videoStabButton.setVisibility(View.GONE);
        header.addView(videoStabButton, vstabLp);
        videoStabButton.setOnClickListener(v -> { hapticClick(v); cycleVideoStabilization(); });

        topSpacer = new View(this);
        header.addView(topSpacer, new LinearLayout.LayoutParams(0, 1, 1));
        brainButton = pill("FILM  ⌄", true);
        brainButton.setOnClickListener(v -> { hapticClick(v); chooseBrain(); });
        // The look selector sits by the shutter deck, leaving the privacy lane clear.
        settingsButton = pill("⚙", false);
        settingsButton.setTextSize(17f);
        settingsButton.setPadding(0, 0, 0, 0);
        settingsButton.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams settingsLp = new LinearLayout.LayoutParams(dp(34), dp(32));
        settingsLp.leftMargin = dp(6);
        settingsLp.rightMargin = 0;
        header.addView(settingsButton, settingsLp);
        settingsButton.setOnClickListener(v -> { hapticClick(v); startActivity(new Intent(this, SettingsActivity.class)); });

        viewfinderSpacer = new LinearLayout(this);

        GestureDetector tapDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onSingleTapConfirmed(MotionEvent e) {
                if (videoScreenLocked) return false;
                if (viewfinderSpacer != null) {
                    int[] loc = new int[2];
                    viewfinderSpacer.getLocationInWindow(loc);
                    handleViewfinderTap(loc[0] + e.getX(), loc[1] + e.getY());
                } else {
                    handleViewfinderTap(e.getX(), e.getY());
                }
                return true;
            }
            @Override public boolean onDoubleTap(MotionEvent e) {
                if (videoScreenLocked || videoStarting) return false;
                handleViewfinderDoubleTap();
                return true;
            }
            @Override public void onLongPress(MotionEvent e) {
                if (videoScreenLocked || isDraggingEv || isDraggingShadows) return;
                if (viewfinderSpacer != null) {
                    int[] loc = new int[2];
                    viewfinderSpacer.getLocationInWindow(loc);
                    handleViewfinderLongPress(loc[0] + e.getX(), loc[1] + e.getY());
                } else {
                    handleViewfinderLongPress(e.getX(), e.getY());
                }
            }
        });

        viewfinderSpacer.setOnTouchListener((v, event) -> {
            tapDetector.onTouchEvent(event);
            if (focusReticle != null && focusReticle.isVisible && event.getPointerCount() == 1) {
                int[] loc = new int[2];
                v.getLocationInWindow(loc);
                float curWinY = loc[1] + event.getY();
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        dragStartY = curWinY;
                        dragStartEv = currentEvOffset;
                        dragStartShadows = AppSettings.getInt(MainActivity.this, AppSettings.SHADOWS, AppSettings.DEFAULT_SHADOWS);
                        float rx = focusReticle.reticleX;
                        float curWinX = loc[0] + event.getX();
                        isDraggingShadows = (curWinX < rx - dp(12) && curWinX > rx - dp(78));
                        isDraggingEv = !isDraggingShadows && (Math.abs(curWinX - (rx + dp(28))) < dp(55)
                                || Math.abs(curWinX - rx) < dp(55));
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (isDraggingShadows) {
                            float dy = dragStartY - curWinY;
                            int deltaSh = Math.round((dy / dp(80)) * 50f);
                            int targetSh = Math.max(0, Math.min(100, dragStartShadows + deltaSh));
                            AppSettings.prefs(MainActivity.this).edit().putInt(AppSettings.SHADOWS, targetSh).apply();
                            applyLiveFilmPreview();
                            updateContextualStatus();
                            focusReticle.updateShadows(targetSh);
                            focusReticle.sampleLoupe(focusReticle.reticleX, focusReticle.reticleY);
                            scheduleFocusReticleHide();
                            return true;
                        } else if (isDraggingEv) {
                            float dy = dragStartY - curWinY;
                            float deltaEv = (dy / dp(80)) * 1.5f;
                            float targetEv = Math.max(-2f, Math.min(2f, dragStartEv + deltaEv));
                            setExposureCompensationEv(targetEv);
                            focusReticle.updateEv(targetEv);
                            focusReticle.sampleLoupe(focusReticle.reticleX, focusReticle.reticleY);
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

        aeLockBadge = new TextView(this);
        aeLockBadge.setText("AE-L  ✕");
        aeLockBadge.setTextSize(9.5f);
        aeLockBadge.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        aeLockBadge.setTextColor(ACCENT);
        aeLockBadge.setLetterSpacing(0.08f);
        aeLockBadge.setBackground(round(0xd8141718, 12, ACCENT, dp(1)));
        aeLockBadge.setPadding(dp(10), dp(4), dp(10), dp(4));
        aeLockBadge.setGravity(Gravity.CENTER);
        aeLockBadge.setVisibility(View.GONE);
        aeLockBadge.setClickable(true);
        aeLockBadge.setContentDescription("Exposure locked. Tap to unlock exposure.");
        aeLockBadge.setOnClickListener(v -> toggleAeLock());

        telemetry = row();
        telemetry.setPadding(dp(3), dp(6), dp(3), dp(8));
        View shutterBox = readoutView("SHUTTER", "AUTO");
        View isoBox = readoutView("ISO", "AUTO");
        View wbBox = readoutView("WB", "AUTO");
        framesBox = readoutView("FRAMES", "--");
        shutterBox.setOnClickListener(v -> { hapticClick(v); chooseShutterSpeed(); });
        shutterBox.setOnLongClickListener(v -> { toggleAeLock(); return true; });
        isoBox.setOnClickListener(v -> { hapticClick(v); chooseManualIso(); });
        isoBox.setOnLongClickListener(v -> { toggleAeLock(); return true; });
        wbBox.setOnClickListener(v -> { hapticClick(v); chooseWhiteBalance(); });
        wbBox.setOnLongClickListener(v -> { toggleAeLock(); return true; });
        framesBox.setOnClickListener(v -> { hapticClick(v); showMessage(AppSettings.formatStorageInfo(MainActivity.this)); });
        framesBox.setOnLongClickListener(v -> { hapticClick(v); showMessage(AppSettings.formatStorageInfo(MainActivity.this)); return true; });
        telemetry.addView(shutterBox, new LinearLayout.LayoutParams(0, -2, 1));
        telemetry.addView(isoBox, new LinearLayout.LayoutParams(0, -2, 1));
        telemetry.addView(wbBox, new LinearLayout.LayoutParams(0, -2, 1));
        telemetry.addView(framesBox, new LinearLayout.LayoutParams(0, -2, 1));
        telemetry.setClickable(true);
        telemetry.setContentDescription("Exposure telemetry. Tap SHUTTER, ISO, or WB to adjust; long-press to lock exposure.");
        telemetry.setOnLongClickListener(v -> { toggleAeLock(); return true; });

        lensRow = new ZoomPresetRow();
        lensRow.setGravity(Gravity.CENTER);
        lensRow.setPadding(dp(4), dp(4), dp(4), dp(4));
        lensRow.setClipToPadding(false);
        lensRow.setClipChildren(false);
        lensRow.setBackground(round(0xd016191a, 100, 0, 0));
        lensRow.setContentDescription("Zoom presets. Swipe horizontally to reveal precision zoom.");
        float[] zooms = new float[]{.5f, 1f, 2f, 5f};
        String[] unselectedLabels = new String[]{".5", "1", "2", "5"};
        for (int i = 0; i < zooms.length; i++) {
            final float zoom = zooms[i];
            boolean active = Math.abs(zoom - 1f) < .01f;
            TextView lens = new TextView(this);
            lens.setGravity(Gravity.CENTER);
            lens.setTextSize(13f);
            lens.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            lens.setTag(zoom);
            if (active) {
                lens.setText(zoom == 0.5f ? ".5x" : ((int) zoom) + "x");
                lens.setTextColor(BG);
                lens.setBackground(round(0xfffed36a, 100, 0, 0));
            } else {
                lens.setText(unselectedLabels[i]);
                lens.setTextColor(0xffffffff);
                lens.setBackground(null);
            }
            lensButtons.add(lens);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(44), dp(36));
            lp.setMargins(dp(1), 0, dp(1), 0);
            lensRow.addView(lens, lp);
            lens.setOnClickListener(v -> chooseLens(zoom));
        }

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

        modeRow = row();
        modeRow.setGravity(Gravity.CENTER);
        modeRow.setPadding(dp(4), dp(3), dp(4), dp(3));
        modeRow.setClipToPadding(false);
        modeRow.setClipChildren(false);
        modeRow.setBackground(round(0xd016191a, 100, 0, 0));
        photoModeButton = new TextView(this);
        photoModeButton.setText("PHOTO");
        photoModeButton.setGravity(Gravity.CENTER);
        photoModeButton.setTextSize(12f);
        photoModeButton.setTypeface(Typeface.DEFAULT_BOLD);
        photoModeButton.setContentDescription("Photo mode");

        videoModeButton = new TextView(this);
        videoModeButton.setText("VIDEO");
        videoModeButton.setGravity(Gravity.CENTER);
        videoModeButton.setTextSize(12f);
        videoModeButton.setTypeface(Typeface.DEFAULT_BOLD);
        videoModeButton.setContentDescription("Video mode");

        LinearLayout.LayoutParams modeLp = new LinearLayout.LayoutParams(dp(76), dp(32));
        modeLp.setMargins(dp(1), 0, dp(1), 0);
        modeRow.addView(photoModeButton, modeLp);
        modeRow.addView(videoModeButton, modeLp);
        photoModeButton.setOnClickListener(v -> { hapticClick(v); setVideoMode(false); });
        videoModeButton.setOnClickListener(v -> { hapticClick(v); setVideoMode(true); });

        lookRow = row();
        lookRow.setGravity(Gravity.CENTER_VERTICAL);
        lookRow.setClipToPadding(false);
        lookRow.setClipChildren(false);
        LinearLayout.LayoutParams lookLp = new LinearLayout.LayoutParams(dp(62), dp(32));
        lookLp.rightMargin = dp(8);
        lookRow.addView(brainButton, lookLp);

        modeNote = label("INTELLIGENT AUTO", 8, 0xffc9d0c8, false);
        modeNote.setLetterSpacing(.06f);
        modeNote.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        LinearLayout.LayoutParams noteTextLp = new LinearLayout.LayoutParams(0, -2, 1f);
        noteTextLp.gravity = Gravity.CENTER_VERTICAL;
        lookRow.addView(modeNote, noteTextLp);
        easyButton = pill("★ EASY", true);
        easyButton.setContentDescription("Toggle Easy Mode: scene-adaptive point and shoot");
        easyButton.setOnClickListener(v -> { hapticClick(v); toggleEasyMode(); });
        LinearLayout.LayoutParams easyLp = new LinearLayout.LayoutParams(dp(74), dp(32));
        easyLp.gravity = Gravity.CENTER_VERTICAL;
        easyLp.rightMargin = dp(8);
        lookRow.addView(easyButton, easyLp);
        adjustButton = pill("ADJUST", false);
        adjustButton.setContentDescription("Open quick look controls");
        adjustButton.setOnClickListener(v -> { hapticClick(v); toggleQuickAdjustPanel(); });
        LinearLayout.LayoutParams adjustLp = new LinearLayout.LayoutParams(dp(74), dp(32));
        adjustLp.gravity = Gravity.CENTER_VERTICAL;
        lookRow.addView(adjustButton, adjustLp);

        videoPauseButton = pill("❚❚ PAUSE", false);
        videoPauseButton.setContentDescription("Pause or resume video recording");
        videoPauseButton.setOnClickListener(v -> { hapticClick(v); toggleVideoPause(); });
        LinearLayout.LayoutParams pauseLp = new LinearLayout.LayoutParams(dp(66), dp(32));
        pauseLp.gravity = Gravity.CENTER_VERTICAL;
        pauseLp.rightMargin = dp(4);
        lookRow.addView(videoPauseButton, pauseLp);
        videoPauseButton.setVisibility(View.GONE);

        videoSnapButton = pill("◎ STILL", false);
        videoSnapButton.setContentDescription("Capture still photo while recording video");
        videoSnapButton.setOnClickListener(v -> { hapticClick(v); captureVideoStillFrame(); });
        LinearLayout.LayoutParams snapLp = new LinearLayout.LayoutParams(dp(58), dp(32));
        snapLp.gravity = Gravity.CENTER_VERTICAL;
        snapLp.rightMargin = dp(4);
        lookRow.addView(videoSnapButton, snapLp);
        videoSnapButton.setVisibility(View.GONE);

        recordingLockButton = pill("LOCK", false);
        recordingLockButton.setContentDescription("Lock video recording controls");
        recordingLockButton.setOnClickListener(v -> { hapticClick(v); setVideoScreenLocked(true); });
        LinearLayout.LayoutParams lockLp = new LinearLayout.LayoutParams(dp(54), dp(32));
        lockLp.gravity = Gravity.CENTER_VERTICAL;
        lookRow.addView(recordingLockButton, lockLp);
        recordingLockButton.setVisibility(View.GONE);

        deckSpacer = new View(this);

        shootRow = new FrameLayout(this);
        shootRow.setClipChildren(false);
        shootRow.setClipToPadding(false);
        galleryButton = new ImageView(this);
        galleryButton.setScaleType(ImageView.ScaleType.CENTER_CROP);
        galleryButton.setBackground(round(0xff202626, 14, 0x777d8580, dp(1)));
        galleryButton.setClipToOutline(true);
        galleryButton.setContentDescription("Open the latest frame. Long press for the library.");
        galleryButton.setImageResource(R.drawable.ic_lika_lens);
        galleryButton.setOnClickListener(v -> { hapticClick(v); openLatestFrame(); });
        galleryButton.setOnLongClickListener(v -> { hapticClick(v); openLibrary(); return true; });

        captureButton = label("", 12, BG, false);
        captureButton.setGravity(Gravity.CENTER);
        captureButton.setBackground(round(0xfffaf9f5, 100, 0xfffaf9f5, dp(2)));
        captureButton.setClickable(false);
        captureButton.setFocusable(false);

        shutterFrame = new FrameLayout(this);
        shutterFrame.setBackground(round(0x00202020, 100, 0xfffaf9f5, dp(3)));
        shutterFrame.addView(captureButton, new FrameLayout.LayoutParams(dp(72), dp(72), Gravity.CENTER));
        shutterFrame.setClickable(true);
        shutterFrame.setFocusable(true);
        shutterFrame.setOnTouchListener(new ShutterTouchListener());

        TextView flip = pill("⟳", false);
        flip.setTextSize(20f);
        flip.setBackground(round(0xff202626, 14, 0x777d8580, dp(1)));
        flip.setGravity(Gravity.CENTER);
        flip.setContentDescription("Switch front and rear camera");
        flip.setOnClickListener(v -> { hapticClick(v); toggleCameraFacing(); });
        flipButton = flip;

        outputModeText = label("WAITING FOR CAMERA SUPPORT", 7, 0xffd9dfd6, true);
        outputModeText.setGravity(Gravity.CENTER);
        outputModeText.setLetterSpacing(.12f);

        restoreShootRow();
        WindowMetricsHelper.WindowSizeInfo windowInfo = WindowMetricsHelper.computeWindowSizeInfo(this);
        boolean isWide = windowInfo.isLandscape || (windowInfo.widthDp >= windowInfo.heightDp);
        assembleCameraLayout(isWide);

        messageView = label("", 10, BG, true);
        messageView.setGravity(Gravity.CENTER);
        messageView.setPadding(dp(16), dp(10), dp(16), dp(10));
        messageView.setBackground(round(0xffe8efd9, 24, 0x00ffffff, 0));
        messageView.setVisibility(View.GONE);
        FrameLayout.LayoutParams messageLp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        messageLp.bottomMargin = dp(24);
        root.addView(messageView, messageLp);

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
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            WindowMetricsHelper.WindowSizeInfo wInfo = WindowMetricsHelper.computeWindowSizeInfo(MainActivity.this);
            boolean isWideWin = wInfo.isLandscape || (wInfo.widthDp >= wInfo.heightDp);
            assembleCameraLayout(isWideWin);
            if (textureView != null && textureView.isAvailable()) {
                configureTransform(textureView.getWidth(), textureView.getHeight());
            }
            if (gridOverlay != null) gridOverlay.invalidate();
            return insets;
        });
    }

    private void restoreShootRow() {
        if (shootRow == null) return;
        shootRow.removeAllViews();
        Rect safeInsets = WindowMetricsHelper.getSafeInsets(root);
        if (galleryButton != null) {
            if (galleryButton.getParent() != null) ((ViewGroup) galleryButton.getParent()).removeView(galleryButton);
            FrameLayout.LayoutParams galleryLp = new FrameLayout.LayoutParams(dp(52), dp(52), Gravity.START | Gravity.CENTER_VERTICAL);
            galleryLp.leftMargin = dp(24) + safeInsets.left;
            shootRow.addView(galleryButton, galleryLp);
        }
        if (shutterFrame != null) {
            if (shutterFrame.getParent() != null) ((ViewGroup) shutterFrame.getParent()).removeView(shutterFrame);
            FrameLayout.LayoutParams shutterLp = new FrameLayout.LayoutParams(dp(86), dp(86), Gravity.CENTER);
            shootRow.addView(shutterFrame, shutterLp);
        }
        if (flipButton != null) {
            if (flipButton.getParent() != null) ((ViewGroup) flipButton.getParent()).removeView(flipButton);
            FrameLayout.LayoutParams flipLp = new FrameLayout.LayoutParams(dp(52), dp(52), Gravity.END | Gravity.CENTER_VERTICAL);
            flipLp.rightMargin = dp(24) + safeInsets.right;
            shootRow.addView(flipButton, flipLp);
        }
    }

    private void assembleCameraLayout(boolean isWide) {
        if (overlay == null) return;
        overlay.removeAllViews();
        Rect safeInsets = WindowMetricsHelper.getSafeInsets(root);
        Rect cutoutRect = WindowMetricsHelper.getTopCutoutRect(root);
        int screenWidth = root != null && root.getWidth() > 0 ? root.getWidth() : getResources().getDisplayMetrics().widthPixels;
        int screenHeight = root != null && root.getHeight() > 0 ? root.getHeight() : getResources().getDisplayMetrics().heightPixels;
        int topSafe = computeTopSafe(safeInsets, cutoutRect);

        if (topSpacer != null) {
            if (cutoutRect != null) {
                float cutoutCenterX = (cutoutRect.left + cutoutRect.right) / 2f;
                boolean isCentered = Math.abs(cutoutCenterX - screenWidth / 2f) < dp(50);
                if (isCentered) {
                    topSpacer.setMinimumWidth(cutoutRect.width() + dp(28));
                } else {
                    topSpacer.setMinimumWidth(dp(16));
                }
            } else {
                topSpacer.setMinimumWidth(dp(16));
            }
        }

        if (!isWide) {
            overlay.setPadding(0, 0, 0, 0);
            overlay.setBackground(null);

            if (header != null) {
                if (header.getParent() != null) ((ViewGroup) header.getParent()).removeView(header);
                FrameLayout.LayoutParams headerLp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
                headerLp.topMargin = topSafe;
                int leftPad = dp(14) + safeInsets.left;
                int rightPad = dp(14) + Math.max(safeInsets.right, privacyRightInset(topSafe));
                if (cutoutRect != null) {
                    float cutoutCenterX = (cutoutRect.left + cutoutRect.right) / 2f;
                    if (cutoutCenterX < screenWidth / 3f) {
                        leftPad = Math.max(leftPad, cutoutRect.right + dp(8));
                    } else if (cutoutCenterX > screenWidth * 2f / 3f) {
                        rightPad = Math.max(rightPad, (screenWidth - cutoutRect.left) + dp(8));
                    }
                }
                header.setPadding(leftPad, 0, rightPad, 0);
                overlay.addView(header, headerLp);
            }

            RectF frame = framingRect(screenWidth, screenHeight);
            if (viewfinderSpacer != null) {
                if (viewfinderSpacer.getParent() != null) ((ViewGroup) viewfinderSpacer.getParent()).removeView(viewfinderSpacer);
                FrameLayout.LayoutParams vfLp = new FrameLayout.LayoutParams((int) frame.width(), (int) frame.height(), Gravity.TOP | Gravity.START);
                vfLp.leftMargin = (int) frame.left;
                vfLp.topMargin = (int) frame.top;
                overlay.addView(viewfinderSpacer, vfLp);
            }
            if (cameraState != null) {
                if (cameraState.getParent() != null) ((ViewGroup) cameraState.getParent()).removeView(cameraState);
                FrameLayout.LayoutParams csLp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                csLp.topMargin = (int) (frame.top + dp(28));
                overlay.addView(cameraState, csLp);
            }
            if (aeLockBadge != null) {
                if (aeLockBadge.getParent() != null) ((ViewGroup) aeLockBadge.getParent()).removeView(aeLockBadge);
                FrameLayout.LayoutParams badgeLp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                badgeLp.topMargin = (int) (frame.top + dp(46));
                overlay.addView(aeLockBadge, badgeLp);
            }
            if (telemetry != null) {
                if (telemetry.getParent() != null) ((ViewGroup) telemetry.getParent()).removeView(telemetry);
                FrameLayout.LayoutParams telLp = new FrameLayout.LayoutParams(dp(340), -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                telLp.topMargin = (int) (frame.top + dp(10));
                overlay.addView(telemetry, telLp);
            }
            if (lensRow != null) {
                if (lensRow.getParent() != null) ((ViewGroup) lensRow.getParent()).removeView(lensRow);
                FrameLayout.LayoutParams lensRowLp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                lensRowLp.topMargin = (int) (frame.bottom - dp(44) - dp(12));
                overlay.addView(lensRow, lensRowLp);
            }
            if (zoomSliderPanel != null) {
                if (zoomSliderPanel.getParent() != null) ((ViewGroup) zoomSliderPanel.getParent()).removeView(zoomSliderPanel);
                FrameLayout.LayoutParams zoomSliderLp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                zoomSliderLp.topMargin = (int) (frame.bottom - dp(44) - dp(12) - dp(40));
                zoomSliderLp.leftMargin = dp(24);
                zoomSliderLp.rightMargin = dp(24);
                overlay.addView(zoomSliderPanel, zoomSliderLp);
            }

            LinearLayout bottomDeck = new LinearLayout(this);
            bottomDeck.setOrientation(LinearLayout.VERTICAL);
            bottomDeck.setGravity(Gravity.CENTER_HORIZONTAL);
            bottomDeck.setClipChildren(false);
            bottomDeck.setClipToPadding(false);
            bottomDeck.setPadding(0, 0, 0, Math.max(dp(12), safeInsets.bottom + dp(6)));

            if (lookRow != null) {
                if (lookRow.getParent() != null) ((ViewGroup) lookRow.getParent()).removeView(lookRow);
                lookRow.setClipChildren(false);
                lookRow.setClipToPadding(false);
                lookRow.setPadding(dp(16) + safeInsets.left, 0, dp(16) + safeInsets.right, 0);
                LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(-1, -2);
                noteLp.bottomMargin = dp(10);
                bottomDeck.addView(lookRow, noteLp);
            }

            restoreShootRow();
            if (shootRow != null) {
                if (shootRow.getParent() != null) ((ViewGroup) shootRow.getParent()).removeView(shootRow);
                LinearLayout.LayoutParams shootLp = new LinearLayout.LayoutParams(-1, dp(96));
                shootLp.bottomMargin = dp(14);
                bottomDeck.addView(shootRow, shootLp);
            }

            if (modeRow != null) {
                if (modeRow.getParent() != null) ((ViewGroup) modeRow.getParent()).removeView(modeRow);
                LinearLayout.LayoutParams modeRowLp = new LinearLayout.LayoutParams(-2, -2);
                modeRowLp.gravity = Gravity.CENTER_HORIZONTAL;
                bottomDeck.addView(modeRow, modeRowLp);
            }

            FrameLayout.LayoutParams deckLp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
            overlay.addView(bottomDeck, deckLp);
        } else {
            // Landscape, Tablet, and Foldable Pro Camera Side-Grip layout
            overlay.setPadding(0, 0, 0, 0);
            overlay.setBackground(null);

            if (header != null) {
                if (header.getParent() != null) ((ViewGroup) header.getParent()).removeView(header);
                FrameLayout.LayoutParams headerLp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
                headerLp.topMargin = topSafe;
                header.setPadding(dp(14) + safeInsets.left, 0, dp(14) + Math.max(safeInsets.right, privacyRightInset(topSafe)), 0);
                overlay.addView(header, headerLp);
            }

            LinearLayout wideBody = new LinearLayout(this);
            wideBody.setOrientation(LinearLayout.HORIZONTAL);

            LinearLayout leftCanvas = new LinearLayout(this);
            leftCanvas.setOrientation(LinearLayout.VERTICAL);

            if (viewfinderSpacer != null) {
                if (viewfinderSpacer.getParent() != null) ((ViewGroup) viewfinderSpacer.getParent()).removeView(viewfinderSpacer);
                leftCanvas.addView(viewfinderSpacer, new LinearLayout.LayoutParams(-1, 0, 1f));
            }
            if (cameraState != null) {
                if (cameraState.getParent() != null) ((ViewGroup) cameraState.getParent()).removeView(cameraState);
                leftCanvas.addView(cameraState);
            }
            if (aeLockBadge != null) {
                if (aeLockBadge.getParent() != null) ((ViewGroup) aeLockBadge.getParent()).removeView(aeLockBadge);
                LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(-2, -2);
                badgeLp.gravity = Gravity.CENTER_HORIZONTAL;
                badgeLp.topMargin = dp(4);
                leftCanvas.addView(aeLockBadge, badgeLp);
            }
            if (telemetry != null) {
                if (telemetry.getParent() != null) ((ViewGroup) telemetry.getParent()).removeView(telemetry);
                LinearLayout.LayoutParams telLp = new LinearLayout.LayoutParams(dp(340), -2);
                telLp.gravity = Gravity.CENTER_HORIZONTAL;
                leftCanvas.addView(telemetry, telLp);
            }
            if (zoomSliderPanel != null) {
                if (zoomSliderPanel.getParent() != null) ((ViewGroup) zoomSliderPanel.getParent()).removeView(zoomSliderPanel);
                LinearLayout.LayoutParams zoomSliderLp = new LinearLayout.LayoutParams(dp(320), -2);
                zoomSliderLp.gravity = Gravity.CENTER_HORIZONTAL;
                zoomSliderLp.topMargin = dp(4);
                leftCanvas.addView(zoomSliderPanel, zoomSliderLp);
            }
            wideBody.addView(leftCanvas, new LinearLayout.LayoutParams(0, -1, 1f));

            LinearLayout sideGrip = new LinearLayout(this);
            sideGrip.setOrientation(LinearLayout.VERTICAL);
            sideGrip.setGravity(Gravity.CENTER_HORIZONTAL);
            sideGrip.setPadding(dp(8), dp(6), dp(8), dp(8));
            sideGrip.setBackground(round(0xb0121617, 16, 0x55454d49, dp(1)));

            if (modeRow != null) {
                if (modeRow.getParent() != null) ((ViewGroup) modeRow.getParent()).removeView(modeRow);
                sideGrip.addView(modeRow, new LinearLayout.LayoutParams(-2, -2));
            }
            if (lensRow != null) {
                if (lensRow.getParent() != null) ((ViewGroup) lensRow.getParent()).removeView(lensRow);
                LinearLayout.LayoutParams lensRowLp = new LinearLayout.LayoutParams(-2, -2);
                lensRowLp.topMargin = dp(6);
                sideGrip.addView(lensRow, lensRowLp);
            }
            if (lookRow != null) {
                if (lookRow.getParent() != null) ((ViewGroup) lookRow.getParent()).removeView(lookRow);
                LinearLayout.LayoutParams lookRowLp = new LinearLayout.LayoutParams(-2, -2);
                lookRowLp.topMargin = dp(6);
                sideGrip.addView(lookRow, lookRowLp);
            }

            View upperGripSpacer = new View(this);
            sideGrip.addView(upperGripSpacer, new LinearLayout.LayoutParams(1, 0, 1f));

            if (shutterFrame != null) {
                if (shutterFrame.getParent() != null) ((ViewGroup) shutterFrame.getParent()).removeView(shutterFrame);
                sideGrip.addView(shutterFrame, new LinearLayout.LayoutParams(dp(88), dp(88)));
            }

            View lowerGripSpacer = new View(this);
            sideGrip.addView(lowerGripSpacer, new LinearLayout.LayoutParams(1, 0, 1f));

            LinearLayout gripBottomRow = new LinearLayout(this);
            gripBottomRow.setOrientation(LinearLayout.HORIZONTAL);
            gripBottomRow.setGravity(Gravity.CENTER);
            if (galleryButton != null) {
                if (galleryButton.getParent() != null) ((ViewGroup) galleryButton.getParent()).removeView(galleryButton);
                gripBottomRow.addView(galleryButton, new LinearLayout.LayoutParams(dp(44), dp(44)));
            }
            if (flipButton != null) {
                if (flipButton.getParent() != null) ((ViewGroup) flipButton.getParent()).removeView(flipButton);
                LinearLayout.LayoutParams flipLp = new LinearLayout.LayoutParams(dp(44), dp(44));
                flipLp.leftMargin = dp(8);
                gripBottomRow.addView(flipButton, flipLp);
            }
            sideGrip.addView(gripBottomRow, new LinearLayout.LayoutParams(-2, -2));

            if (outputModeText != null) {
                if (outputModeText.getParent() != null) ((ViewGroup) outputModeText.getParent()).removeView(outputModeText);
                LinearLayout.LayoutParams formatLp = new LinearLayout.LayoutParams(-2, -2);
                formatLp.topMargin = dp(6);
                sideGrip.addView(outputModeText, formatLp);
            }

            wideBody.addView(sideGrip, new LinearLayout.LayoutParams(dp(110), -1));
            FrameLayout.LayoutParams wideBodyLp = new FrameLayout.LayoutParams(-1, -1);
            wideBodyLp.topMargin = topSafe + dp(38);
            overlay.addView(wideBody, wideBodyLp);
        }
    }

    private FrameLayout.LayoutParams getQuickAdjustLayoutParams() {
        WindowMetricsHelper.WindowSizeInfo windowInfo = WindowMetricsHelper.computeWindowSizeInfo(this);
        boolean isWide = windowInfo.isLandscape || (windowInfo.widthDp >= windowInfo.heightDp);
        if (isWide) {
            FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(dp(380), dp(320), Gravity.BOTTOM | Gravity.START);
            panelLp.leftMargin = dp(24);
            panelLp.bottomMargin = dp(24);
            return panelLp;
        } else {
            FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(-1, dp(340), Gravity.BOTTOM);
            panelLp.leftMargin = dp(8);
            panelLp.rightMargin = dp(8);
            panelLp.bottomMargin = dp(118);
            return panelLp;
        }
    }

    private void rebuildCameraUiLayout() {
        WindowMetricsHelper.WindowSizeInfo windowInfo = WindowMetricsHelper.computeWindowSizeInfo(this);
        boolean isWide = windowInfo.isLandscape || (windowInfo.widthDp >= windowInfo.heightDp);
        assembleCameraLayout(isWide);
        if (quickAdjustPanel != null) {
            quickAdjustPanel.setLayoutParams(getQuickAdjustLayoutParams());
        }
        root.post(() -> {
            if (textureView != null) {
                configureTransform(textureView.getWidth(), textureView.getHeight());
            }
            if (gridOverlay != null) {
                gridOverlay.invalidate();
            }
            updateVideoModeUi();
            updateLensButtons(requestedZoom);
            updateReadouts();
        });
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        rebuildCameraUiLayout();
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
        AppSettings.prefs(this).edit().putBoolean(AppSettings.LAST_USED_MODE, enabled).apply();
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

    private void cycleVideoStabilization() {
        String current = AppSettings.get(this, AppSettings.STABILIZATION, "Cinema OIS + EIS");
        int currentIndex = 0;
        for (int i = 0; i < AppSettings.STABILIZATION_CHOICES.length; i++) {
            if (AppSettings.STABILIZATION_CHOICES[i].equalsIgnoreCase(current)) {
                currentIndex = i;
                break;
            }
        }
        int nextIndex = (currentIndex + 1) % AppSettings.STABILIZATION_CHOICES.length;
        String next = AppSettings.STABILIZATION_CHOICES[nextIndex];
        AppSettings.prefs(this).edit().putString(AppSettings.STABILIZATION, next).apply();
        if (next.startsWith("Locked")) {
            lockedVideoPitch = smoothPitchAngle;
            lockedVideoRoll = smoothRollAngle;
            hasLockedVideoAnchor = true;
        } else {
            hasLockedVideoAnchor = false;
        }
        updateQuickButtons();
        updateReadouts();
        if (captureSession != null && previewRequest != null) {
            try {
                applyOpticalSettings(previewRequest, videoMode);
                applyZoom(previewRequest);
                captureSession.setRepeatingRequest(previewRequest.build(), null, cameraHandler);
            } catch (Throwable ignored) {
            }
        }
        showMessage("Stabilization: " + next);
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
        if (videoStabButton != null) videoStabButton.setVisibility(videoMode ? View.VISIBLE : View.GONE);
        updateContextualStatus();
        boolean activeVideoTake = (videoRecording || switchingVideoCamera) && !videoScreenLocked;
        if (recordingLockButton != null) recordingLockButton.setVisibility(activeVideoTake ? View.VISIBLE : View.GONE);
        if (videoPauseButton != null) {
            videoPauseButton.setVisibility(activeVideoTake ? View.VISIBLE : View.GONE);
            videoPauseButton.setText(videoPaused ? "▶ RESUME" : "❚❚ PAUSE");
            videoPauseButton.setTextColor(videoPaused ? ACCENT : 0xffe9ede6);
        }
        if (videoSnapButton != null) videoSnapButton.setVisibility(activeVideoTake ? View.VISIBLE : View.GONE);
        if ((videoRecording || switchingVideoCamera) && videoScreenLocked) {
            if (videoLockOverlay == null) buildVideoLockOverlay();
            videoLockOverlay.setVisibility(View.VISIBLE);
        } else if (videoLockOverlay != null) {
            videoLockOverlay.setVisibility(View.GONE);
        }

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
        if (videoStabButton != null) videoStabButton.setEnabled(idleControlsAvailable);
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
            boolean isVidActive = videoMode || isQuickTake;
            boolean recUi = videoRecording || switchingVideoCamera || isQuickTake;
            int fill = isVidActive ? (recUi ? 0xfff0443c : 0xffd93632) : 0xfffaf9f5;
            int stroke = isVidActive ? 0xffff827a : 0xfffaf9f5;
            captureButton.setBackground(round(fill, 100, fill, dp(2)));
            captureButton.setText(isVidActive && recUi ? "■" : "");
            captureButton.setTextColor(isVidActive ? 0xffffffff : BG);
            captureButton.setTextSize(recUi ? 18 : 12);
            captureButton.setContentDescription(isVidActive
                    ? recUi ? "Stop video recording" : "Start video recording"
                    : "Take photo");
            shutterFrame.setBackground(round(0x00202020, 100, stroke, dp(3)));
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
        updateTelemetryVisibility();
    }

    private void styleModeButton(TextView button, boolean active) {
        button.setTypeface(Typeface.DEFAULT_BOLD);
        if (active) {
            button.setTextColor(BG);
            button.setBackground(round(0xfffed36a, 100, 0, 0));
        } else {
            button.setTextColor(0xffffffff);
            button.setBackground(null);
        }
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
        if ((!videoMode && !isQuickTake) || videoRecording || videoStarting || switchingVideoCamera) return;
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
        if ((!videoMode && !isQuickTake && !pendingVideoStart) || videoRecording || videoStarting || cameraDevice == null) return;
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
        if ((!videoMode && !isQuickTake && !pendingVideoStart) || (!videoStarting && !switchingVideoCamera) || cameraDevice == null) {
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
                boolean speechEnhance = AppSettings.getBoolean(this, AppSettings.SPEECH_ENHANCEMENT, true);
                if (audioPref.startsWith("Unprocessed") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    audioSrc = MediaRecorder.AudioSource.UNPROCESSED;
                } else if (audioPref.contains("Speech") || audioPref.startsWith("Voice") || speechEnhance) {
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
            boolean wantHdr10 = AppSettings.getBoolean(this, AppSettings.VIDEO_HDR, false)
                    && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && !isContinuation
                    && isHlg10Supported();
            boolean wantHevc = (codecPref.contains("HEVC") || wantHdr10) && !isContinuation;
            boolean usingHevc = false;
            try {
                mediaRecorder.setVideoEncoder(wantHevc ? MediaRecorder.VideoEncoder.HEVC : MediaRecorder.VideoEncoder.H264);
                usingHevc = wantHevc;
            } catch (RuntimeException fallbackH264) {
                mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
                wantHdr10 = false;
            }
            if (wantHdr10 && usingHevc && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                try {
                    mediaRecorder.setVideoEncodingProfileLevel(
                            android.media.MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10,
                            android.media.MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel41);
                } catch (RuntimeException hdrFallback) {
                    wantHdr10 = false;
                }
            }
            if (recordAudio) {
                mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                mediaRecorder.setAudioChannels(2);
                mediaRecorder.setAudioSamplingRate(48000);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && AppSettings.getBoolean(this, AppSettings.AUDIO_ZOOM, true)) {
                    try {
                        float micZoom = Math.max(0f, Math.min(1f, (requestedZoom - 1.0f) / 4.0f));
                        mediaRecorder.setPreferredMicrophoneFieldDimension(micZoom);
                    } catch (Throwable ignored) {
                    }
                }
            }
            mediaRecorder.setVideoSize(videoSize.getWidth(), videoSize.getHeight());
            String fpsChoice = AppSettings.get(this, AppSettings.VIDEO_FPS, "24 fps (Cinematic)");
            int targetFps = VideoRecordingCoordinator.computeTargetFps(fpsChoice);
            mediaRecorder.setVideoFrameRate(targetFps);

            String bitrateChoice = AppSettings.get(this, AppSettings.VIDEO_BITRATE, "High · 24 Mbps");
            int bitrate = VideoRecordingCoordinator.computeBitrate(bitrateChoice);
            if (wantHdr10) bitrate = Math.round(bitrate * 1.25f);
            mediaRecorder.setVideoEncodingBitRate(bitrate);
            if (recordAudio) mediaRecorder.setAudioEncodingBitRate(160_000);
            int orientHint = lockedVideoOrientation >= 0 ? lockedVideoOrientation : jpegOrientation();
            mediaRecorder.setOrientationHint(orientHint);
            Location loc = getCaptureLocation();
            if (loc != null) {
                mediaRecorder.setLocation((float) loc.getLatitude(), (float) loc.getLongitude());
            }
            mediaRecorder.setOutputFile(videoTempFile.getAbsolutePath());
            mediaRecorder.prepare();

            closeSessionOnly();
            SurfaceTexture texture = textureView.getSurfaceTexture();
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            recordingPreviewSurface = new Surface(texture);
            List<Surface> outputs = Arrays.asList(recordingPreviewSurface, mediaRecorder.getSurface());
            CameraCaptureSession.StateCallback sessionCallback = new CameraCaptureSession.StateCallback() {
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
                        String stabPref = AppSettings.get(MainActivity.this, AppSettings.STABILIZATION, "Cinema OIS + EIS");
                        if (stabPref.startsWith("Locked")) {
                            lockedVideoPitch = smoothPitchAngle;
                            lockedVideoRoll = smoothRollAngle;
                            hasLockedVideoAnchor = true;
                        } else {
                            hasLockedVideoAnchor = false;
                        }
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
            };

            boolean configuredHdrSession = false;
            if (wantHdr10 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                try {
                    android.hardware.camera2.params.OutputConfiguration previewOut =
                            new android.hardware.camera2.params.OutputConfiguration(recordingPreviewSurface);
                    android.hardware.camera2.params.OutputConfiguration videoOut =
                            new android.hardware.camera2.params.OutputConfiguration(mediaRecorder.getSurface());
                    videoOut.setDynamicRangeProfile(android.hardware.camera2.params.DynamicRangeProfiles.HLG10);
                    List<android.hardware.camera2.params.OutputConfiguration> outConfigs = Arrays.asList(previewOut, videoOut);
                    java.util.concurrent.Executor camExec = command -> {
                        if (cameraHandler != null) cameraHandler.post(command);
                    };
                    android.hardware.camera2.params.SessionConfiguration sessionConfig =
                            new android.hardware.camera2.params.SessionConfiguration(
                                    android.hardware.camera2.params.SessionConfiguration.SESSION_REGULAR,
                                    outConfigs,
                                    camExec,
                                    sessionCallback);
                    cameraDevice.createCaptureSession(sessionConfig);
                    configuredHdrSession = true;
                } catch (Throwable hdrSessionErr) {
                    configuredHdrSession = false;
                }
            }
            if (!configuredHdrSession) {
                cameraDevice.createCaptureSession(outputs, sessionCallback, cameraHandler);
            }
        } catch (Exception e) {
            failVideoStart("Video recording could not start.");
        }
    }

    private boolean isHlg10Supported() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || characteristics == null) return false;
        try {
            android.hardware.camera2.params.DynamicRangeProfiles profiles =
                    characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES);
            if (profiles != null) {
                return profiles.getSupportedProfiles().contains(
                        android.hardware.camera2.params.DynamicRangeProfiles.HLG10);
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private Size chooseVideoSize(Size[] sizes) {
        return VideoRecordingCoordinator.chooseVideoSize(sizes);
    }

    private String videoProfileLabel() {
        String base = VideoRecordingCoordinator.formatProfileLabel(videoSize,
                AppSettings.get(this, AppSettings.VIDEO_FPS, "24 fps (Cinematic)"),
                AppSettings.get(this, AppSettings.VIDEO_ASPECT, "16:9 Widescreen"));
        String stab = AppSettings.get(this, AppSettings.STABILIZATION, "Cinema OIS + EIS");
        if (stab.startsWith("Locked")) {
            base += " · 🔒 LOCKED";
        } else if (stab.startsWith("Active")) {
            base += " · ⚡ ACTIVE";
        }
        String audioPref = AppSettings.get(this, AppSettings.VIDEO_AUDIO, "Camcorder · Stereo");
        boolean speechEnhance = AppSettings.getBoolean(this, AppSettings.SPEECH_ENHANCEMENT, true);
        if (!audioPref.startsWith("Muted")) {
            if (audioPref.contains("Speech") || audioPref.startsWith("Voice") || speechEnhance) {
                base += " · 🎙 VOICE";
            }
        }
        return base;
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
        hasLockedVideoAnchor = false;
        lockedVideoPitch = 0f;
        lockedVideoRoll = 0f;
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
            if (!"Video start cancelled.".equals(message)) {
                showMessage(hadPreviousSegments ? "Camera switch unsupported · saving recorded segments…" : message);
            }
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
        hasLockedVideoAnchor = false;
        lockedVideoPitch = 0f;
        lockedVideoRoll = 0f;
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
        VideoRecordingCoordinator.releaseMediaRecorder(mediaRecorder);
        mediaRecorder = null;
    }

    private void releaseRecordingPreviewSurface() {
        VideoRecordingCoordinator.releaseRecordingPreviewSurface(recordingPreviewSurface);
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
                final Uri finalVideoUri = destination;
                runOnUiThread(() -> {
                    showMessage(countSegs > 1
                            ? ("Stitched " + countSegs + "-camera video saved. Tap to create GIF.")
                            : "Video saved. Tap to create GIF.");
                    if (finalVideoUri != null && messageView != null) {
                        messageView.setOnClickListener(v -> {
                            Intent gifIntent = new Intent(MainActivity.this, GifStudioActivity.class);
                            gifIntent.putExtra(GifStudioActivity.EXTRA_VIDEO_URI, finalVideoUri.toString());
                            startActivity(gifIntent);
                        });
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to publish video", e);
                if (destination != null) {
                    try {
                        getContentResolver().delete(destination, null, null);
                    } catch (RuntimeException re) {
                        Log.w(TAG, "Failed to clean up destination video URI: " + destination, re);
                    }
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
        return VideoRecordingCoordinator.stitchVideoSegments(segments, outputFile);
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

        root.addView(quickAdjustPanel, getQuickAdjustLayoutParams());
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
        value.setPadding(dp(8), 0, 0, 0);
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

        View.OnClickListener resetSingle = v -> {
            seek.setProgress(fallback - min);
            updateQuickValue(value, key, fallback);
            AppSettings.prefs(MainActivity.this).edit().putInt(key, fallback).apply();
            applyLiveFilmPreview();
            updateContextualStatus();
            if (AppSettings.getBoolean(MainActivity.this, AppSettings.HAPTICS, true)) {
                seek.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            }
        };
        value.setOnClickListener(resetSingle);
        labelRow.setOnLongClickListener(v -> {
            resetSingle.onClick(v);
            return true;
        });

        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int next = min + progress;
                updateQuickValue(value, key, next);
                if (fromUser) {
                    AppSettings.prefs(MainActivity.this).edit().putInt(key, next).apply();
                    applyLiveFilmPreview();
                    updateContextualStatus();
                    if (AppSettings.getBoolean(MainActivity.this, AppSettings.HAPTICS, true)) {
                        boolean isLandmark = (next == fallback);
                        if (AppSettings.EXPOSURE.equals(key) && next == 0) isLandmark = true;
                        else if (AppSettings.CONTRAST.equals(key) && next == 100) isLandmark = true;
                        else if (AppSettings.SATURATION.equals(key) && next == 100) isLandmark = true;
                        else if (AppSettings.GRAIN.equals(key) && next == 0) isLandmark = true;
                        else if (AppSettings.HALATION.equals(key) && next == 0) isLandmark = true;
                        else if (AppSettings.SHARPEN.equals(key) && next == 0) isLandmark = true;

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
        if (quickAdjustPanel == null) {
            buildQuickAdjustPanel();
        }
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
        applyLiveFilmPreview();
        updateContextualStatus();
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
        if ("FRAMES".equals(name) || "ROLL".equals(name)) {
            framesTopLabel = top;
            framesValueText = bottom;
        }
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
        if (AppSettings.getBoolean(this, AppSettings.HISTOGRAM, false) && gridOverlay != null && gridOverlay.isHistogramHit(x, y)) {
            cycleHistogramMode();
            return;
        }
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

            Rect cropRegion = previewRequest != null ? previewRequest.get(CaptureRequest.SCALER_CROP_REGION) : null;
            if (cropRegion == null) {
                cropRegion = CameraExposureHelper.computeCropRect(activeArray, requestedZoom);
            }
            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
            boolean isFront = facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT;
            Rect focusRect = CameraExposureHelper.computeMeteringRect(cropRegion, nx, ny, isFront, 0.10f);

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
            } catch (Exception e) {
                Log.w(TAG, "Failed to apply manual focus/metering region", e);
            }
        }

        if (focusReticle != null) {
            focusReticle.showAt(x, y, currentEvOffset);
            focusReticle.sampleLoupe(x, y);
        }
        scheduleFocusReticleHide();
    }

    private void cycleHistogramMode() {
        String cur = AppSettings.get(this, AppSettings.HISTOGRAM_MODE, "Luminance");
        String next = "Luminance".equals(cur) ? "RGB Parade" : "RGB Parade".equals(cur) ? "Compact" : "Luminance";
        AppSettings.prefs(this).edit().putString(AppSettings.HISTOGRAM_MODE, next).apply();
        if (AppSettings.getBoolean(this, AppSettings.HAPTICS, true) && captureButton != null) {
            captureButton.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        }
        showMessage("HISTOGRAM · " + next.toUpperCase(Locale.US));
        if (gridOverlay != null) gridOverlay.invalidate();
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
            } catch (Exception e) {
                Log.w(TAG, "Failed to reset focus to continuous", e);
            }
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
        } catch (Exception e) {
            Log.w(TAG, "Failed to set exposure compensation", e);
        }
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
        } catch (Exception e) {
            Log.w(TAG, "Failed to toggle AE lock", e);
        }
        if (cameraState != null) {
            cameraState.setText(aeLocked ? "AE-L  ·  EXPOSURE LOCKED" : "EXPOSURE UNLOCKED");
            if (!aeLocked) updateVideoModeUi();
        }
        if (aeLockBadge != null) {
            aeLockBadge.setVisibility(aeLocked ? View.VISIBLE : View.GONE);
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
        if (videoStabButton != null) {
            String stab = AppSettings.get(this, AppSettings.STABILIZATION, "Cinema OIS + EIS");
            if (stab.startsWith("Locked")) {
                videoStabButton.setText("🔒 LOCKED");
                videoStabButton.setTextColor(ACCENT);
            } else if (stab.startsWith("Active")) {
                videoStabButton.setText("⚡ ACTIVE");
                videoStabButton.setTextColor(ACCENT);
            } else if (stab.startsWith("Optical")) {
                videoStabButton.setText("OIS ONLY");
                videoStabButton.setTextColor(0xffe9ede6);
            } else if (stab.startsWith("Off")) {
                videoStabButton.setText("STAB OFF");
                videoStabButton.setTextColor(0xff888888);
            } else {
                videoStabButton.setText("OIS+EIS");
                videoStabButton.setTextColor(0xffe9ede6);
            }
        }
        if (timerButton != null) {
            String timer = AppSettings.get(this, AppSettings.TIMER, "Off");
            timerButton.setText(timer.startsWith("3") ? "◷ 3s" : timer.startsWith("10") ? "◷ 10s" : "◷");
            timerButton.setContentDescription("Self timer: " + timer);
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
        String next = current.startsWith("Off") ? "Auto" : current.startsWith("Auto") ? "Torch" : "Off";
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
        } catch (Throwable e) {
            Log.e("LikaLens", "Could not open camera", e);
            openingCamera = false;
            runOnUiThread(() -> {
                cameraState.setText("CAMERA UNAVAILABLE");
                showMessage("Could not open this camera.");
            });
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

    private int privacyRightInset(int headerTop) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || root == null) return 0;
        WindowInsets insets = root.getRootWindowInsets();
        Rect privacy = insets == null ? null : insets.getPrivacyIndicatorBounds();
        if (privacy == null || privacy.isEmpty() || privacy.bottom <= headerTop
                || privacy.top >= headerTop + dp(32)) return 0;
        // Reserve only Android's top-right indicator bounds; keep left controls anchored.
        int width = root.getWidth() > 0 ? root.getWidth() : getResources().getDisplayMetrics().widthPixels;
        return Math.max(0, width - privacy.left + dp(6));
    }

    private int computeTopSafe(Rect safeInsets, Rect cutoutRect) {
        int screenWidth = root != null && root.getWidth() > 0 ? root.getWidth() : getResources().getDisplayMetrics().widthPixels;
        if (cutoutRect != null) {
            boolean centered = Math.abs(cutoutRect.centerX() - screenWidth / 2f) < dp(50);
            if (cutoutRect.width() > dp(120) && cutoutRect.height() > dp(36)) return cutoutRect.bottom + dp(6);
            if (centered && cutoutRect.height() <= dp(44)) return Math.max(dp(8), cutoutRect.centerY() - dp(16));
        }
        if (safeInsets.top <= dp(24)) return Math.max(dp(10), safeInsets.top + dp(6));
        return Math.max(dp(8), (safeInsets.top - dp(32)) / 2);
    }

    private RectF framingRect(float width, float height) {
        if (width <= 0 || height <= 0) return new RectF(0, 0, 0, 0);
        float ratio = aspectRatioSetting();
        WindowMetricsHelper.WindowSizeInfo windowInfo = WindowMetricsHelper.computeWindowSizeInfo(this);
        boolean isWide = windowInfo.isLandscape || (width >= height);
        if (!isWide) {
            Rect safeInsets = WindowMetricsHelper.getSafeInsets(root);
            Rect cutoutRect = WindowMetricsHelper.getTopCutoutRect(root);
            int topSafe = computeTopSafe(safeInsets, cutoutRect);
            float topZoneBottom = topSafe + dp(34);
            float viewfinderTopMin = topZoneBottom + dp(10);

            float bottomDeckHeight = dp(198) + Math.max(dp(12), safeInsets.bottom + dp(6));
            float viewfinderBottomMax = height - bottomDeckHeight - dp(10);

            float availableHeight = Math.max(dp(160), viewfinderBottomMax - viewfinderTopMin);
            float availableWidth = width;

            float targetRatio = 1f / ratio;
            float frameWidth = availableWidth;
            float frameHeight = frameWidth / targetRatio;
            if (frameHeight > availableHeight) {
                frameHeight = availableHeight;
                frameWidth = frameHeight * targetRatio;
            }
            float left = (width - frameWidth) * 0.5f;
            float excess = Math.max(0, availableHeight - frameHeight);
            float top = viewfinderTopMin + excess * 0.25f;
            return new RectF(left, top, left + frameWidth, top + frameHeight);
        } else {
            Rect safeInsets = WindowMetricsHelper.getSafeInsets(root);
            float gripWidth = dp(112);
            float topOffset = Math.max(dp(44), safeInsets.top + dp(8));
            float availableWidth = Math.max(dp(200), width - gripWidth - dp(24) - safeInsets.right - safeInsets.left);
            float availableHeight = Math.max(dp(200), height - topOffset - dp(16) - safeInsets.bottom);
            float frameWidth = availableWidth;
            float frameHeight = frameWidth / ratio;
            if (frameHeight > availableHeight) {
                frameHeight = availableHeight;
                frameWidth = frameHeight * ratio;
            }
            float left = safeInsets.left + dp(12) + (availableWidth - frameWidth) * 0.5f;
            float top = topOffset + (availableHeight - frameHeight) * 0.5f;
            return new RectF(left, top, left + frameWidth, top + frameHeight);
        }
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
                        runOnUiThread(() -> {
                            if (textureView != null) {
                                configureTransform(textureView.getWidth(), textureView.getHeight());
                                textureView.animate().alpha(1f).setDuration(160).start();
                            }
                        });
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
                    RawArchive.Frame frame = RawArchive.createFrame(archiveKey, image, capturedCharacteristics,
                            result, capturedOrientation, capturedAspect);
                    try {
                        developed = RawDeveloper.develop(frame, captureLook, 0);
                    } catch (IllegalArgumentException resolutionError) {
                        Log.w(TAG, "Full-resolution RAW render failed; retrying at a safe display size", resolutionError);
                        developed = RawDeveloper.develop(frame, captureLook, 3000);
                    }
                    if (keepEditableSource) {
                        try {
                            RawArchive.writeArchive(this, frame);
                            PhotoEditStore.save(this, archiveKey, captureLook);
                        } catch (Exception archiveErr) {
                            Log.w(TAG, "Could not save editable RAW archive", archiveErr);
                        }
                    }
                } else {
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);
                    RawDeveloper.Look jpegLook = RawDeveloper.Look.fromPreferences(this);
                    jpegLook.flipHorizontal = mirrorSelfie;
                    String savedBorder = jpegLook.frameBorder;
                    jpegLook.frameBorder = "None";
                    developed = RawDeveloper.developJpeg(bytes, jpegLook);
                    if (developed == null) throw new IllegalStateException("Renderer returned no image");
                    developed = rotateForDisplay(developed);
                    developed = cropToAspect(developed, capturedAspect);
                    if (savedBorder != null && !savedBorder.startsWith("None")) {
                        jpegLook.frameBorder = savedBorder;
                        developed = RawDeveloper.applyFrameBorder(developed, jpegLook, null);
                    }
                }
                if (developed == null) throw new IllegalStateException("Renderer returned no image");
                if (!raw || !"DNG only".equals(format) || !dngSaved) {
                    writeJpeg(developed, baseFileStem + ".jpg", result, orgSub);
                }
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
                pendingRenders.decrementAndGet();
            }
        });
    }

    private void capturePhoto() {
        if (cameraDevice == null || !started) {
            showMessage("Camera is starting up…");
            return;
        }
        if (timerCountdownRunning) {
            cancelTimerCountdown();
            return;
        }
        if (captureSession == null || captureInFlight) {
            return;
        }
        if (pendingRenders.get() >= 5) {
            showMessage("Developing previous frames…");
            return;
        }
        if (AppSettings.getBoolean(this, AppSettings.HAPTICS, true)) captureButton.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
        int timer = parseTimer(AppSettings.get(this, AppSettings.TIMER, "Off"));
        if (timer > 0) {
            startTimerCountdown(timer);
        } else {
            captureInFlight = true;
            captureButton.setAlpha(.65f);
            triggerCaptureWithScreenFlashIfNeeded();
        }
    }

    private void startTimerCountdown(int totalSeconds) {
        timerCountdownRunning = true;
        captureInFlight = true;
        if (captureButton != null) captureButton.setAlpha(0.65f);
        final boolean useTorch = AppSettings.getBoolean(this, AppSettings.TIMER_LIGHT, true) && flashSupported && !isFrontCamera();

        timerCountdownRunnable = new Runnable() {
            int remaining = totalSeconds;
            @Override
            public void run() {
                if (!timerCountdownRunning) {
                    cancelTimerCountdown();
                    return;
                }
                if (remaining <= 0) {
                    timerCountdownRunning = false;
                    setTimerTorchPulse(false);
                    triggerCaptureWithScreenFlashIfNeeded();
                    return;
                }
                showMessage(String.format(Locale.US, "⏱ %ds", remaining));
                hapticDetent();
                if (useTorch) {
                    setTimerTorchPulse(true);
                    timerHandler.postDelayed(() -> setTimerTorchPulse(false), 120L);
                    if (remaining <= 2) {
                        timerHandler.postDelayed(() -> {
                            if (timerCountdownRunning) {
                                setTimerTorchPulse(true);
                                timerHandler.postDelayed(() -> setTimerTorchPulse(false), 100L);
                            }
                        }, 500L);
                    }
                }
                remaining--;
                timerHandler.postDelayed(this, 1000L);
            }
        };
        timerHandler.post(timerCountdownRunnable);
    }

    private void cancelTimerCountdown() {
        timerCountdownRunning = false;
        captureInFlight = false;
        if (timerCountdownRunnable != null) {
            timerHandler.removeCallbacks(timerCountdownRunnable);
            timerCountdownRunnable = null;
        }
        setTimerTorchPulse(false);
        if (captureButton != null) captureButton.setAlpha(1.0f);
        showMessage("Timer canceled");
    }

    private void setTimerTorchPulse(boolean active) {
        if (timerTorchActive == active) return;
        timerTorchActive = active;
        refreshPreviewRequest();
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
            String astroSetting = AppSettings.get(this, AppSettings.ASTROPHOTOGRAPHY, "Auto (when stationary at night)");
            boolean isAstro = !astroSetting.startsWith("Off") && (isDeviceStationary || astroSetting.startsWith("Always"));
            if (isAstro && characteristics != null) {
                Range<Long> expRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
                Range<Integer> sensRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
                if (expRange != null && sensRange != null) {
                    // Engage long night integration: 2.0s or maximum supported exposure, with clean low ISO (200-400)
                    long targetAstroExp = Math.min(expRange.getUpper(), 2_000_000_000L); // 2 seconds
                    int targetAstroIso = Math.max(sensRange.getLower(), Math.min(sensRange.getUpper(), 200));
                    still.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
                    still.set(CaptureRequest.SENSOR_EXPOSURE_TIME, targetAstroExp);
                    still.set(CaptureRequest.SENSOR_SENSITIVITY, targetAstroIso);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        still.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY);
                    }
                    runOnUiThread(() -> showMessage("✨ Night integration exposure: " + String.format(Locale.US, "%.1fs", targetAstroExp / 1_000_000_000f)));
                }
            } else {
                setExposure(still);
            }
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
        if (characteristics == null) return 0;
        Integer sensor = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
        int sensorOrientation = sensor == null ? 90 : sensor;
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int deviceDegrees = rotation == Surface.ROTATION_90 ? 90 : rotation == Surface.ROTATION_180 ? 180 : rotation == Surface.ROTATION_270 ? 270 : 0;
        if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
            return (sensorOrientation + deviceDegrees) % 360;
        } else {
            return (sensorOrientation - deviceDegrees + 360) % 360;
        }
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

    private Location getCaptureLocation() {
        if (!AppSettings.getBoolean(this, AppSettings.LOCATION, false)) return null;
        if (AppSettings.getBoolean(this, AppSettings.STRIP_GEO, false)) return null;
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        try {
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (lm == null) return null;
            Location best = null;
            List<String> providers = lm.getProviders(true);
            if (providers != null) {
                for (String p : providers) {
                    Location l = lm.getLastKnownLocation(p);
                    if (l != null && (best == null || l.getTime() > best.getTime())) {
                        best = l;
                    }
                }
            }
            return best;
        } catch (Throwable t) {
            Log.w(TAG, "Failed to query capture location", t);
            return null;
        }
    }

    private void setExifGps(ExifInterface exif, Location loc) {
        if (exif == null || loc == null) return;
        double lat = loc.getLatitude();
        double lon = loc.getLongitude();
        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, formatExifCoord(Math.abs(lat)));
        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, lat >= 0 ? "N" : "S");
        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, formatExifCoord(Math.abs(lon)));
        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, lon >= 0 ? "E" : "W");
        if (loc.hasAltitude()) {
            double alt = loc.getAltitude();
            exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, ((int) Math.round(Math.abs(alt))) + "/1");
            exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, alt >= 0 ? "0" : "1");
        }
        if (loc.getTime() > 0) {
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.US);
            timeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy:MM:dd", Locale.US);
            dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = new Date(loc.getTime());
            exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, timeFormat.format(date));
            exif.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, dateFormat.format(date));
        }
    }

    private static String formatExifCoord(double coord) {
        int degrees = (int) coord;
        coord = (coord - degrees) * 60;
        int minutes = (int) coord;
        coord = (coord - minutes) * 60000;
        int seconds = (int) Math.round(coord);
        return degrees + "/1," + minutes + "/1," + seconds + "/1000";
    }

    private void writeDng(Image image, TotalCaptureResult result, CameraCharacteristics captureCharacteristics, String name, String subfolder) throws Exception {
        if (result == null || captureCharacteristics == null) throw new IllegalStateException("Missing capture metadata");
        OutputTarget target = createOutput(name, "image/x-adobe-dng", subfolder);
        boolean published = false;
        DngCreator creator = null;
        try {
            creator = new DngCreator(captureCharacteristics, result);
            Location loc = getCaptureLocation();
            if (loc != null) {
                creator.setLocation(loc);
            }
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
                boolean writeLocation = AppSettings.getBoolean(this, AppSettings.LOCATION, false);
                if (stripGeo || !writeLocation) {
                    exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, null);
                    exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, null);
                    exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, null);
                    exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, null);
                    exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, null);
                    exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, null);
                    exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, null);
                    exif.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, null);
                    exif.setAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD, null);
                } else {
                    Location loc = getCaptureLocation();
                    if (loc != null) {
                        setExifGps(exif, loc);
                    }
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
            Uri collection;
            if (mime.startsWith("image/")) {
                collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            } else if (mime.startsWith("video/")) {
                collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            } else {
                collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            }
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
                try {
                    getContentResolver().delete(uri, null, null);
                } catch (RuntimeException e) {
                    Log.w(TAG, "Failed to discard output URI: " + uri, e);
                }
            } else if (file != null) {
                try {
                    file.delete();
                } catch (RuntimeException e) {
                    Log.w(TAG, "Failed to delete output file: " + file, e);
                }
            }
        }
    }

    private void chooseBrain() {
        List<String> brainList = new ArrayList<>(Arrays.asList(AppSettings.BRAIN_CHOICES));
        List<String> descList = new ArrayList<>(Arrays.asList(AppSettings.BRAIN_DESCRIPTIONS));

        for (int i = 1; i <= 3; i++) {
            String rName = AppSettings.getCustomRecipeName(this, i);
            if (rName != null && !rName.trim().isEmpty()) {
                brainList.add("[RECIPE " + i + "] " + rName);
                descList.add("Custom User Recipe · Slot " + i);
            }
        }
        String curPushPull = AppSettings.get(this, AppSettings.FILM_PUSH_PULL, AppSettings.FILM_PUSH_PULL_CHOICES[0]);
        brainList.add("+ FILM PUSH / PULL RATING...");
        descList.add("Current: " + curPushPull);
        brainList.add("+ SAVE CURRENT AS RECIPE");
        descList.add("Store current quick adjust parameters into recipe slot");
        brainList.add("+ 3D LUT COLOR GRADE...");
        String curLut = AppSettings.get(this, AppSettings.LUT_NAME, "None");
        descList.add("Current: " + curLut + " · Select 3D film lookup table");

        String[] brains = brainList.toArray(new String[0]);
        String[] descriptions = descList.toArray(new String[0]);
        String selected = AppSettings.brain(this);
        int checked = Math.max(0, brainList.indexOf(selected));

        showCustomBottomSheet("PROCESSING BRAIN & RECIPES", brains, descriptions, checked, index -> {
            String picked = brains[index];
            if (picked.startsWith("+ FILM PUSH")) {
                showPushPullDialog();
                return;
            }
            if (picked.startsWith("+ 3D LUT")) {
                showLutDialog();
                return;
            }
            if (picked.startsWith("+ SAVE CURRENT")) {
                promptSaveCurrentLookAsRecipe();
                return;
            }
            if (picked.startsWith("[RECIPE ")) {
                int slot = Character.getNumericValue(picked.charAt(8));
                String rData = AppSettings.getCustomRecipeData(this, slot);
                if (rData != null) {
                    RawDeveloper.Look look = RawDeveloper.Look.fromJson(rData);
                    if (look != null) {
                        applyCustomRecipeToPreferences(look);
                        AppSettings.prefs(this).edit().putString(AppSettings.BRAIN, picked).apply();
                        showMessage("APPLIED " + picked);
                        updateBrainButton();
                        updateQuickButtons();
                        return;
                    }
                }
            }
            AppSettings.prefs(this).edit().putString(AppSettings.BRAIN, picked).apply();
            updateBrainButton();
        });
    }

    private void showPushPullDialog() {
        String[] items = AppSettings.FILM_PUSH_PULL_CHOICES;
        String[] subs = {
                "Nominal box speed · balanced tonal latitude & fine grain",
                "Develop +1 stop · steeper S-curve, punchy contrast & grain",
                "Develop +2 stops · deep crushed blacks & coarse night grain",
                "Overexpose & pull -1 stop · open pastel shadows & soft highlights"
        };
        String current = AppSettings.get(this, AppSettings.FILM_PUSH_PULL, items[0]);
        int checked = 0;
        for (int i = 0; i < items.length; i++) {
            if (items[i].equals(current)) {
                checked = i;
                break;
            }
        }
        showCustomBottomSheet("FILM PUSH / PULL PROCESSING", items, subs, checked, index -> {
            String picked = items[index];
            AppSettings.prefs(this).edit().putString(AppSettings.FILM_PUSH_PULL, picked).apply();
            showMessage("FILM PROCESS: " + picked.split("·")[0].trim());
            updateBrainButton();
        });
    }

    private void showLutDialog() {
        List<String> luts = LutManager.getAvailableLutNames(this);
        String current = AppSettings.get(this, AppSettings.LUT_NAME, "None");
        String[] items = luts.toArray(new String[0]);
        int checked = Math.max(0, luts.indexOf(current));
        new AlertDialog.Builder(this)
                .setTitle("3D LUT COLOR GRADE")
                .setSingleChoiceItems(items, checked, (dialog, which) -> {
                    String picked = items[which];
                    AppSettings.prefs(this).edit().putString(AppSettings.LUT_NAME, picked).apply();
                    showMessage("LUT: " + picked);
                    applyLiveFilmPreview();
                    updateBrainButton();
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void applyCustomRecipeToPreferences(RawDeveloper.Look look) {
        if (look.lutName != null) {
            AppSettings.prefs(this).edit().putString(AppSettings.LUT_NAME, look.lutName).apply();
        }
        if (look.filmPushPull != null) {
            AppSettings.prefs(this).edit().putString(AppSettings.FILM_PUSH_PULL, look.filmPushPull).apply();
        }
        AppSettings.prefs(this).edit()
                .putInt(AppSettings.WARMTH, Math.round(look.warmth * 50f + 50f))
                .putInt(AppSettings.CONTRAST, Math.round(look.contrast * 100f))
                .putInt(AppSettings.PIVOT, Math.round(look.pivot * 100f))
                .putInt(AppSettings.SATURATION, Math.round(look.saturation * 100f))
                .putInt(AppSettings.COLOR_ROLLOFF, Math.round(look.colorRollOff * 100f))
                .putInt(AppSettings.HIGHLIGHTS, Math.round(look.highlights * 100f))
                .putInt(AppSettings.SHADOWS, Math.round(look.shadows * 100f))
                .putInt(AppSettings.GRAIN, Math.round(look.grain * 100f))
                .putInt(AppSettings.HALATION, Math.round(look.halation * 100f))
                .putInt(AppSettings.SHARPEN, Math.round(look.sharpening * 100f))
                .putInt(AppSettings.COLOR_NR, Math.round(look.chromaNr * 100f))
                .putInt(AppSettings.VIGNETTE, Math.round(look.vignette * 100f))
                .apply();
    }

    private void promptSaveCurrentLookAsRecipe() {
        String[] options = new String[3];
        for (int i = 1; i <= 3; i++) {
            String name = AppSettings.getCustomRecipeName(this, i);
            options[i - 1] = "Slot " + i + ": " + (name != null ? name : "(Empty)");
        }
        new AlertDialog.Builder(this)
                .setTitle("SAVE TO RECIPE SLOT")
                .setItems(options, (dialog, which) -> {
                    int slot = which + 1;
                    android.widget.EditText input = new android.widget.EditText(this);
                    String existing = AppSettings.getCustomRecipeName(this, slot);
                    input.setText(existing != null ? existing : (AppSettings.brain(this) + " Custom"));
                    input.setSelectAllOnFocus(true);
                    new AlertDialog.Builder(this)
                            .setTitle("NAME RECIPE " + slot)
                            .setView(input)
                            .setPositiveButton("Save", (d, w) -> {
                                String name = input.getText().toString().trim();
                                if (name.isEmpty()) name = "Recipe " + slot;
                                RawDeveloper.Look look = RawDeveloper.Look.fromPreferences(this);
                                AppSettings.saveCustomRecipe(this, slot, name, look.toJson());
                                showMessage("SAVED RECIPE " + slot + " (" + name + ")");
                                updateBrainButton();
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showCustomBottomSheet(String title, String[] items, String[] subtitles, int selectedIndex, BottomSheetCallback callback) {
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
            LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(0, -2, 1f);
            copyLp.rightMargin = dp(8);
            row.addView(copy, copyLp);

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
        ioExecutor.execute(() -> {
            PhotoRepository.Photo latest = PhotoRepository.latest(this);
            if (latest == null) {
                runOnUiThread(() -> {
                    if (galleryButton != null) galleryButton.setImageResource(R.drawable.ic_lika_lens);
                });
                return;
            }
            try (InputStream stream = getContentResolver().openInputStream(latest.uri)) {
                if (stream == null) return;
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 16;
                options.inPreferredConfig = Bitmap.Config.RGB_565;
                Bitmap thumbnail = BitmapFactory.decodeStream(stream, null, options);
                if (thumbnail != null) {
                    runOnUiThread(() -> {
                        if (galleryButton != null) galleryButton.setImageBitmap(thumbnail);
                    });
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to load latest photo thumbnail for gallery button", e);
            }
        });
    }

    private void updateBrainButton() {
        if (brainButton != null) {
            String brain = AppSettings.brain(this);
            String lut = AppSettings.get(this, AppSettings.LUT_NAME, "None");
            String pushPull = AppSettings.get(this, AppSettings.FILM_PUSH_PULL, AppSettings.FILM_PUSH_PULL_CHOICES[0]);
            String label;
            if (brain.startsWith("[RECIPE")) label = "RCP" + brain.charAt(8);
            else if (brain.startsWith("KODACHROME")) label = "KODA";
            else if (brain.startsWith("FUJI") || brain.contains("ETERNA")) label = "ETERNA";
            else if (brain.startsWith("BLEACH")) label = "BLEACH";
            else if (brain.startsWith("MONO")) label = "MONO";
            else if (brain.startsWith("NEUTRAL")) label = "RAW";
            else label = "FILM";
            if (pushPull.startsWith("+1")) label += " +1";
            else if (pushPull.startsWith("+2")) label += " +2";
            else if (pushPull.startsWith("-1")) label += " -1";
            if (!"None".equalsIgnoreCase(lut)) {
                label += " · LUT";
            }
            brainButton.setText(label + "  ⌄");
        }
        applyLiveFilmPreview();
    }

    private void applyLiveFilmPreview() {
        if (textureView == null) return;
        boolean livePreview = AppSettings.getBoolean(this, AppSettings.LIVE_FILM_PREVIEW, true);
        String brain = AppSettings.brain(this);
        String activeLut = AppSettings.get(this, AppSettings.LUT_NAME, "None");
        if (!livePreview) {
            textureView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            textureView.invalidate();
            return;
        }

        RawDeveloper.Look look = RawDeveloper.Look.fromPreferences(this);
        String pp = look.filmPushPull != null ? look.filmPushPull : "";
        boolean hasPushPull = pp.startsWith("+1") || pp.startsWith("+2") || pp.startsWith("-1");
        if (AppSettings.BRAIN_NEUTRAL_RAW.equals(brain) && "None".equalsIgnoreCase(activeLut)
                && !isCustomLookActive() && look.whiteBalanceKelvin <= 0 && !hasPushPull) {
            textureView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            textureView.invalidate();
            return;
        }

        Paint paint = new Paint();
        ColorMatrix cm = new ColorMatrix();
        if (brain.startsWith("MONO") || brain.contains("SILVER")) {
            // Panchromatic silver halide luma (Tri-X 400): elevated red/orange response + silver contrast
            float scale = 1.12f;
            float translate = (-0.42f * scale + 0.42f) * 255f - 4f;
            float rW = 0.33f * scale, gW = 0.59f * scale, bW = 0.08f * scale;
            cm.set(new float[] {
                rW, gW, bW, 0, translate,
                rW, gW, bW, 0, translate,
                rW, gW, bW, 0, translate,
                0,  0,  0,  1, 0
            });
        } else if (brain.contains("KODACHROME")) {
            ColorMatrix warmReversal = new ColorMatrix(new float[] {
                1.14f,  0.02f, -0.04f, 0, -6f,
               -0.02f,  1.05f, -0.01f, 0, -8f,
               -0.05f, -0.02f,  0.90f, 0, -12f,
                0,      0,      0,     1,   0f
            });
            cm.postConcat(warmReversal);
        } else if (brain.contains("FUJI") || brain.contains("ETERNA")) {
            ColorMatrix fuji = new ColorMatrix(new float[] {
                0.93f, -0.01f,  0.02f, 0, -2f,
                0.00f,  0.97f,  0.02f, 0,  2f,
                0.02f,  0.02f,  1.02f, 0,  5f,
                0,      0,      0,     1,  0f
            });
            cm.postConcat(fuji);
        } else if (brain.contains("BLEACH")) {
            cm.setSaturation(0.32f);
            ColorMatrix contrast = new ColorMatrix();
            float scale = 1.24f;
            float translate = (-0.42f * scale + 0.42f) * 255f - 10f;
            contrast.set(new float[] {
                scale, 0, 0, 0, translate - 2f,
                0, scale, 0, 0, translate,
                0, 0, scale, 0, translate + 3f,
                0, 0, 0, 1, 0
            });
            cm.postConcat(contrast);
        } else if (!AppSettings.BRAIN_NEUTRAL_RAW.equals(brain)) {
            // LIKA / FILM: Kodak Vision3 500T filmic density with warm midtones and natural shadow gradation
            ColorMatrix film = new ColorMatrix(new float[] {
                1.04f,  0.02f, -0.02f, 0, -4f,
               -0.01f,  0.99f,  0.01f, 0, -5f,
                0.01f,  0.02f,  0.93f, 0, -7f,
                0,      0,      0,     1,  0f
            });
            cm.postConcat(film);
        }

        // Incorporate user developer adjustments (exposureEv, contrast, shadows, warmth, saturation, manual Kelvin WB, push/pull)
        float evDelta = look.exposureEv - (AppSettings.DEFAULT_EXPOSURE / 10f);
        float evScale = (float) Math.pow(2.0, evDelta * 0.45f);
        float contrastRatio = look.contrast / (AppSettings.DEFAULT_CONTRAST / 100f);
        float shadowDelta = look.shadows - (AppSettings.DEFAULT_SHADOWS / 100f);
        float warmthDelta = look.warmth;
        if (look.whiteBalanceKelvin > 0 && !brain.startsWith("MONO")) {
            warmthDelta += Math.max(-0.6f, Math.min(0.6f, (look.whiteBalanceKelvin - 5600f) / 3600f));
        }
        float satRatio = look.saturation / (AppSettings.DEFAULT_SATURATION / 100f);
        if (pp.startsWith("+1")) {
            contrastRatio *= 1.14f;
            shadowDelta += 0.14f;
            satRatio *= 1.06f;
        } else if (pp.startsWith("+2")) {
            contrastRatio *= 1.28f;
            shadowDelta += 0.28f;
            satRatio *= 0.92f;
        } else if (pp.startsWith("-1")) {
            contrastRatio *= 0.88f;
            shadowDelta -= 0.18f;
            satRatio *= 0.94f;
        }

        if (Math.abs(satRatio - 1.0f) > 0.02f && !brain.startsWith("MONO")) {
            ColorMatrix satMatrix = new ColorMatrix();
            satMatrix.setSaturation(Math.max(0f, Math.min(1.8f, satRatio)));
            cm.postConcat(satMatrix);
        }
        if (Math.abs(evScale - 1.0f) > 0.01f || Math.abs(contrastRatio - 1.0f) > 0.01f
                || Math.abs(shadowDelta) > 0.01f || Math.abs(warmthDelta) > 0.01f) {
            float combinedScale = evScale * (0.35f + 0.65f * contrastRatio);
            float pivotOffset = (1.0f - contrastRatio) * 95f - shadowDelta * 28f;
            float rScale = combinedScale * (1.0f + warmthDelta * 0.08f);
            float gScale = combinedScale * (1.0f + warmthDelta * 0.01f);
            float bScale = combinedScale * (1.0f - warmthDelta * 0.10f);
            float rOff = pivotOffset + warmthDelta * 8f;
            float gOff = pivotOffset + warmthDelta * 2f;
            float bOff = pivotOffset - warmthDelta * 10f;
            ColorMatrix gradingAdjust = new ColorMatrix(new float[] {
                rScale, 0,      0,      0, rOff,
                0,      gScale, 0,      0, gOff,
                0,      0,      bScale, 0, bOff,
                0,      0,      0,      1, 0
            });
            cm.postConcat(gradingAdjust);
        }

        // Apply active 3D LUT matrix in viewfinder
        if (LutManager.LUT_PORTRA.equalsIgnoreCase(activeLut)) {
            ColorMatrix portra = new ColorMatrix(new float[] {
                1.12f,  0.03f, -0.04f, 0,  6f,
               -0.01f,  1.05f, -0.01f, 0,  3f,
               -0.06f, -0.03f,  0.94f, 0, -4f,
                0,      0,      0,     1,  0
            });
            cm.postConcat(portra);
        } else if (LutManager.LUT_FUJI.equalsIgnoreCase(activeLut)) {
            ColorMatrix fuji = new ColorMatrix(new float[] {
                0.96f, -0.01f,  0.03f, 0, -2f,
                0.01f,  1.04f,  0.02f, 0,  5f,
                0.03f,  0.02f,  1.06f, 0,  8f,
                0,      0,      0,     1,  0
            });
            cm.postConcat(fuji);
        } else if (LutManager.LUT_TEAL_ORANGE.equalsIgnoreCase(activeLut)) {
            ColorMatrix tealOrange = new ColorMatrix(new float[] {
                1.22f, -0.04f, -0.08f, 0,  8f,
               -0.02f,  1.02f, -0.04f, 0,  2f,
               -0.12f,  0.06f,  1.18f, 0, -6f,
                0,      0,      0,     1,  0
            });
            cm.postConcat(tealOrange);
        } else if (LutManager.LUT_ANSEL_BW.equalsIgnoreCase(activeLut)) {
            ColorMatrix ansel = new ColorMatrix();
            ansel.setSaturation(0f);
            ColorMatrix sc = new ColorMatrix();
            float scale = 1.30f;
            float tr = (-0.5f * scale + 0.5f) * 255f;
            sc.set(new float[] {
                scale, 0, 0, 0, tr,
                0, scale, 0, 0, tr,
                0, 0, scale, 0, tr,
                0, 0, 0, 1, 0
            });
            ansel.postConcat(sc);
            cm.postConcat(ansel);
        } else if (LutManager.LUT_AMBER_70S.equalsIgnoreCase(activeLut)) {
            ColorMatrix amber = new ColorMatrix(new float[] {
                1.20f,  0.04f, -0.04f, 0, 10f,
                0.02f,  1.06f, -0.02f, 0,  4f,
               -0.10f, -0.06f,  0.80f, 0, -8f,
                0,      0,      0,     1,  0
            });
            cm.postConcat(amber);
        } else if (LutManager.LUT_BLEACH.equalsIgnoreCase(activeLut)) {
            ColorMatrix bleach = new ColorMatrix();
            bleach.setSaturation(0.32f);
            ColorMatrix sc = new ColorMatrix();
            float scale = 1.35f;
            float tr = (-0.5f * scale + 0.5f) * 255f;
            sc.set(new float[] {
                scale, 0, 0, 0, tr,
                0, scale, 0, 0, tr,
                0, 0, scale, 0, tr,
                0, 0, 0, 1, 0
            });
            bleach.postConcat(sc);
            cm.postConcat(bleach);
        }

        paint.setColorFilter(new ColorMatrixColorFilter(cm));
        textureView.setLayerType(View.LAYER_TYPE_HARDWARE, paint);
        textureView.invalidate();
    }

    private void updateContextualStatus() {
        if (modeNote == null) return;
        if (videoMode) {
            String fps = AppSettings.get(this, AppSettings.VIDEO_FPS, "24 fps (Cinematic)");
            String fpsTag = fps.startsWith("24") ? "24 FPS CINEMATIC" : fps.startsWith("30") ? "30 FPS" : "60 FPS";
            modeNote.setText(videoRecording ? "TAP ⟳ TO SWITCH" : "CINEMATIC VIDEO · " + fpsTag);
            return;
        }
        boolean easy = AppSettings.getBoolean(this, AppSettings.EASY_MODE, true);
        String astroPref = AppSettings.get(this, AppSettings.ASTROPHOTOGRAPHY, "Auto (when stationary at night)");
        boolean astroActive = !astroPref.startsWith("Off") && (isDeviceStationary || astroPref.startsWith("Always"));
        if (astroActive) {
            modeNote.setText("✨ ASTRO / NIGHT STACKING");
        } else if (isCustomLookActive()) {
            modeNote.setText("CUSTOM LOOK ACTIVE");
        } else if (easy) {
            modeNote.setText("INTELLIGENT AUTO");
        } else {
            modeNote.setText("MANUAL PHOTO · TAP HUD");
        }
    }

    private boolean isCustomLookActive() {
        return AppSettings.getInt(this, AppSettings.EXPOSURE, AppSettings.DEFAULT_EXPOSURE) != AppSettings.DEFAULT_EXPOSURE
                || AppSettings.getInt(this, AppSettings.WARMTH, AppSettings.DEFAULT_WARMTH) != AppSettings.DEFAULT_WARMTH
                || AppSettings.getInt(this, AppSettings.CONTRAST, AppSettings.DEFAULT_CONTRAST) != AppSettings.DEFAULT_CONTRAST
                || AppSettings.getInt(this, AppSettings.SATURATION, AppSettings.DEFAULT_SATURATION) != AppSettings.DEFAULT_SATURATION
                || AppSettings.getInt(this, AppSettings.HIGHLIGHTS, AppSettings.DEFAULT_HIGHLIGHTS) != AppSettings.DEFAULT_HIGHLIGHTS
                || AppSettings.getInt(this, AppSettings.SHADOWS, AppSettings.DEFAULT_SHADOWS) != AppSettings.DEFAULT_SHADOWS
                || AppSettings.getInt(this, AppSettings.GRAIN, AppSettings.DEFAULT_GRAIN) != AppSettings.DEFAULT_GRAIN
                || AppSettings.getInt(this, AppSettings.HALATION, AppSettings.DEFAULT_HALATION) != AppSettings.DEFAULT_HALATION
                || AppSettings.getInt(this, AppSettings.SHARPEN, AppSettings.DEFAULT_SHARPEN) != AppSettings.DEFAULT_SHARPEN;
    }

    private void updateEasyButton() {
        if (easyButton == null) return;
        if (videoMode) {
            easyButton.setVisibility(View.GONE);
            updateContextualStatus();
            return;
        }
        easyButton.setVisibility(View.VISIBLE);
        boolean easy = AppSettings.getBoolean(this, AppSettings.EASY_MODE, true);
        if (easy) {
            easyButton.setText("★ EASY");
            easyButton.setTextColor(ACCENT);
            easyButton.setBackground(round(0x33d7b574, 15, ACCENT, dp(1)));
        } else {
            easyButton.setText("EASY");
            easyButton.setTextColor(0xff9ea8a2);
            easyButton.setBackground(round(0xff181d1c, 15, 0x44606963, dp(1)));
        }
        updateContextualStatus();
    }

    private void toggleEasyMode() {
        boolean current = AppSettings.getBoolean(this, AppSettings.EASY_MODE, true);
        boolean next = !current;
        AppSettings.prefs(this).edit().putBoolean(AppSettings.EASY_MODE, next).apply();
        updateEasyButton();
        updateReadouts();
        showMessage(next ? "EASY P&S ON  ·  Smart film science & scene adaptive"
                         : "CUSTOM PRO MODE  ·  Tap SHUTTER / ISO / WB to adjust");
        if (AppSettings.getBoolean(this, AppSettings.HAPTICS, true) && easyButton != null) {
            easyButton.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
        }
    }

    private void chooseLens(float factor) {
        hapticDetent();
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

            // If the current camera can handle this zoom level natively, animate smoothly without restarting
            if (mainCameraId.equals(cameraId) && canZoomOnCurrentCamera(factor)) {
                physicalFocalFactor = 1f;
                float targetZoom = factor;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && characteristics != null) {
                    Range<Float> range = characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
                    if (range != null) {
                        targetZoom = Math.max(factor, range.getLower());
                    }
                }
                animateZoomTo(targetZoom);
                return;
            }

            // If we are currently on an auxiliary camera, return to mainCameraId when factor >= 1f
            if (!mainCameraId.equals(cameraId) && factor >= 1f) {
                selectCameraForLens(mainCameraId, factor, false);
                return;
            }

            // Prefer a real wider RAW camera when the device exposes one and mainCameraId cannot zoom wide.
            if (factor < 1f) {
                CameraCharacteristics main = cameraManager.getCameraCharacteristics(mainCameraId);
                float logicalWideZoom = supportedWideZoom(main, factor);
                if (logicalWideZoom > 0f) {
                    physicalFocalFactor = 1f;
                    if (mainCameraId.equals(cameraId)) {
                        animateZoomTo(logicalWideZoom);
                    } else {
                        selectCameraForLens(mainCameraId, logicalWideZoom, false);
                    }
                    return;
                }

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
                if (rawWideId != null) {
                    physicalFocalFactor = rawWideFactor;
                    selectCameraForLens(rawWideId, rawWideFactor, false);
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

            if (mainCameraId.equals(cameraId)) {
                animateZoomTo(factor);
                return;
            }
            selectCameraForLens(mainCameraId, factor, false);
        } catch (CameraAccessException e) {
            showMessage("Lens selection is unavailable.");
        }
    }

    private boolean canZoomOnCurrentCamera(float factor) {
        if (characteristics == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Range<Float> range = characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
            if (range != null) {
                if (factor >= 1.0f) {
                    return factor <= range.getUpper() + 0.05f;
                } else {
                    return range.getLower() < 0.98f;
                }
            }
        }
        return factor >= 1.0f && mainCameraId != null && mainCameraId.equals(cameraId);
    }

    private float supportedWideZoom(CameraCharacteristics camera, float factor) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || camera == null) return -1f;
        Range<Float> range = camera.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
        if (range == null || range.getLower() >= 0.98f || range.getUpper() < 1f) return -1f;
        return Math.max(factor, range.getLower());
    }

    private void selectCameraForLens(String id, float factor, boolean jpegWide) {
        AppSettings.prefs(this).edit().putString(AppSettings.LAST_CAMERA_ID, id).apply();
        boolean mustRestart = !id.equals(cameraId);
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
            } catch (CameraAccessException | IllegalStateException e) {
                Log.w(TAG, "Failed to apply interactive zoom", e);
            }
        }
    }

    private void finishInteractiveZoom() {
        if (videoScreenLocked || videoStarting || switchingVideoCamera) return;
        if (canZoomOnCurrentCamera(requestedZoom) && mainCameraId != null && mainCameraId.equals(cameraId)) {
            return;
        }
        if (requestedZoom < 1f) chooseLens(requestedZoom);
        else chooseContinuousLens(requestedZoom);
    }

    /** Selects the longest physical lens that does not exceed the requested zoom. */
    private void chooseContinuousLens(float factor) {
        if (canZoomOnCurrentCamera(factor) && mainCameraId != null && mainCameraId.equals(cameraId)) {
            return;
        }
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
        return CameraExposureHelper.zoomProgressFor(zoom);
    }

    private float zoomForProgress(int progress) {
        return CameraExposureHelper.zoomForProgress(progress);
    }

    private void animateZoomTo(float targetZoom) {
        hapticDetent();
        if (zoomAnimator != null) zoomAnimator.cancel();
        if (captureSession == null || previewRequest == null) {
            requestedZoom = targetZoom;
            updateLensButtons(targetZoom);
            return;
        }
        float startZoom = requestedZoom;
        if (Math.abs(startZoom - targetZoom) < 0.01f) {
            requestedZoom = targetZoom;
            updateLensButtons(targetZoom);
            return;
        }
        zoomAnimator = ValueAnimator.ofFloat(startZoom, targetZoom);
        zoomAnimator.setDuration(260);
        zoomAnimator.setInterpolator(new DecelerateInterpolator(1.5f));
        final long[] lastRequestTime = {0L};
        zoomAnimator.addUpdateListener(animation -> {
            requestedZoom = (float) animation.getAnimatedValue();
            updateZoomSliderUi();
            if (previewRequest == null || captureSession == null) return;
            long now = SystemClock.uptimeMillis();
            if (now - lastRequestTime[0] >= 24L || animation.getAnimatedFraction() >= 1f) {
                lastRequestTime[0] = now;
                try {
                    applyZoom(previewRequest);
                    captureSession.setRepeatingRequest(previewRequest.build(), null, cameraHandler);
                } catch (CameraAccessException | IllegalStateException e) {
                    Log.w(TAG, "Failed to update zoom during animation", e);
                }
            }
        });
        zoomAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                requestedZoom = targetZoom;
                if (previewRequest != null && captureSession != null) {
                    try {
                        applyZoom(previewRequest);
                        captureSession.setRepeatingRequest(previewRequest.build(), null, cameraHandler);
                    } catch (CameraAccessException | IllegalStateException e) {
                        Log.w(TAG, "Failed to apply final zoom", e);
                    }
                }
                updateLensButtons(targetZoom);
                updateZoomSliderUi();
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
        String[] unselectedLabels = new String[]{".5", "1", "2", "5"};
        int closestIndex = 0;
        float minDiff = Float.MAX_VALUE;
        for (int i = 0; i < zooms.length; i++) {
            float diff = Math.abs(zooms[i] - factor);
            if (diff < minDiff) {
                minDiff = diff;
                closestIndex = i;
            }
        }
        boolean indexChanged = (closestIndex != lastActiveLensIndex);
        if (indexChanged) {
            lastActiveLensIndex = closestIndex;
        }
        for (int i = 0; i < lensButtons.size(); i++) {
            TextView button = lensButtons.get(i);
            boolean active = (i == closestIndex);
            if (active) {
                button.setBackground(round(0xfffed36a, 100, 0, 0));
                button.setTextColor(BG);
                if (minDiff < 0.08f) {
                    float z = zooms[i];
                    button.setText(z == 0.5f ? ".5x" : ((int) z) + "x");
                } else {
                    button.setText(String.format(Locale.US, "%.1fx", factor));
                }
                if (indexChanged) {
                    button.setScaleX(0.92f);
                    button.setScaleY(0.92f);
                    button.animate().scaleX(1.0f).scaleY(1.0f).setDuration(180).setInterpolator(new DecelerateInterpolator()).start();
                }
            } else {
                button.setBackground(null);
                button.setTextColor(0xffffffff);
                if (i < unselectedLabels.length) {
                    button.setText(unselectedLabels[i]);
                }
                button.setScaleX(1.0f);
                button.setScaleY(1.0f);
            }
        }
        updateZoomSliderUi();
    }

    private void applyZoom(CaptureRequest.Builder builder) {
        if (characteristics == null || builder == null) return;
        Rect active = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        if (active == null) return;

        String stab = AppSettings.get(this, AppSettings.STABILIZATION, "Cinema OIS + EIS");
        boolean isLockedTripod = stab.startsWith("Locked") && (videoRecording || videoStarting) && hasLockedVideoAnchor;

        if (isLockedTripod) {
            float effectiveZoom = Math.max(1.08f, requestedZoom * 1.08f);
            int cropW = Math.max(1, Math.round(active.width() / effectiveZoom));
            int cropH = Math.max(1, Math.round(active.height() / effectiveZoom));

            float dPitch = smoothPitchAngle - lockedVideoPitch;
            float dRoll = smoothRollAngle - lockedVideoRoll;
            if (Math.abs(dPitch) > 4.5f) {
                lockedVideoPitch += (dPitch - Math.signum(dPitch) * 4.5f) * 0.15f;
                dPitch = smoothPitchAngle - lockedVideoPitch;
            }
            if (Math.abs(dRoll) > 4.5f) {
                lockedVideoRoll += (dRoll - Math.signum(dRoll) * 4.5f) * 0.15f;
                dRoll = smoothRollAngle - lockedVideoRoll;
            }

            float pxPerDeg = active.height() / 65.0f;
            int maxShiftX = (active.width() - cropW) / 2;
            int maxShiftY = (active.height() - cropH) / 2;
            int shiftX = Math.max(-maxShiftX, Math.min(maxShiftX, Math.round(-dRoll * pxPerDeg)));
            int shiftY = Math.max(-maxShiftY, Math.min(maxShiftY, Math.round(dPitch * pxPerDeg)));

            int left = active.centerX() + shiftX - cropW / 2;
            int top = active.centerY() + shiftY - cropH / 2;
            builder.set(CaptureRequest.SCALER_CROP_REGION, new Rect(left, top, left + cropW, top + cropH));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, 1.0f);
                } catch (Throwable ignored) {
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Range<Float> ratioRange = characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
            if (ratioRange != null) {
                float minRatio = ratioRange.getLower();
                float maxRatio = ratioRange.getUpper();
                float clampedRatio = Math.max(minRatio, Math.min(maxRatio, requestedZoom));
                builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, clampedRatio);
                float excessZoom = requestedZoom / clampedRatio;
                if (excessZoom > 1.01f) {
                    int cropW = Math.max(1, Math.round(active.width() / excessZoom));
                    int cropH = Math.max(1, Math.round(active.height() / excessZoom));
                    int left = active.centerX() - cropW / 2;
                    int top = active.centerY() - cropH / 2;
                    builder.set(CaptureRequest.SCALER_CROP_REGION, new Rect(left, top, left + cropW, top + cropH));
                } else {
                    builder.set(CaptureRequest.SCALER_CROP_REGION, active);
                }
            } else {
                applyDigitalCrop(builder, active);
            }
        } else {
            applyDigitalCrop(builder, active);
        }

        if ((videoRecording || videoStarting) && mediaRecorder != null
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && AppSettings.getBoolean(this, AppSettings.AUDIO_ZOOM, true)) {
            try {
                float micZoom = Math.max(0f, Math.min(1f, (requestedZoom - 1.0f) / 4.0f));
                mediaRecorder.setPreferredMicrophoneFieldDimension(micZoom);
            } catch (Throwable ignored) {
            }
        }
    }

    private void applyDigitalCrop(CaptureRequest.Builder builder, Rect active) {
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
        boolean wantEis = isVideo && (stab.startsWith("Cinema") || stab.startsWith("Locked") || stab.startsWith("Active"));

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
        if (timerTorchActive && !isFrontCamera()) {
            builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
        } else if (mode.startsWith("Torch")) {
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
        } catch (CameraAccessException | IllegalStateException e) {
            Log.w(TAG, "Failed to refresh preview request", e);
        }
    }

    private long shutterNanoseconds(String value) {
        return CameraExposureHelper.shutterNanoseconds(value);
    }

    private int parseIso(String value) {
        return CameraExposureHelper.parseIso(value);
    }

    private void updateReadouts() {
        boolean manual = AppSettings.get(this, AppSettings.EXPOSURE_MODE, "Auto exposure").startsWith("Manual") && manualSupported;
        if (shutterValueText != null) {
            if (manual) {
                shutterValueText.setText(AppSettings.get(this, AppSettings.MANUAL_SHUTTER, "1/120 sec").replace(" sec", ""));
                shutterValueText.setTextColor(ACCENT);
            } else if (videoMode) {
                String fps = AppSettings.get(this, AppSettings.VIDEO_FPS, "24 fps (Cinematic)");
                shutterValueText.setText(fps.startsWith("24") ? "1/48 (180°)" : fps.startsWith("30") ? "1/60 (180°)" : "1/120 (180°)");
                shutterValueText.setTextColor(0xfff0f1ea);
            } else {
                shutterValueText.setText("AUTO");
                shutterValueText.setTextColor(0xfff0f1ea);
            }
        }
        if (isoValueText != null) {
            isoValueText.setText(manual ? AppSettings.get(this, AppSettings.MANUAL_ISO, "ISO 100") : "AUTO");
            isoValueText.setTextColor(manual ? ACCENT : 0xfff0f1ea);
        }
        int wb = AppSettings.resolveWhiteBalanceKelvin(this);
        if (whiteBalanceValueText != null) {
            whiteBalanceValueText.setText(wb <= 0 ? "AUTO" : wb + " K");
            whiteBalanceValueText.setTextColor(wb > 0 ? ACCENT : 0xfff0f1ea);
        }
        if (framesValueText != null && framesBox != null) {
            boolean showFrames = AppSettings.getBoolean(this, AppSettings.FRAME_COUNTER, true);
            framesBox.setVisibility(showFrames ? View.VISIBLE : View.GONE);
            if (showFrames) {
                if (videoMode) {
                    if (framesTopLabel != null) framesTopLabel.setText("REEL");
                    long mins = AppSettings.getEstimatedRemainingVideoMinutes(this);
                    long h = mins / 60;
                    long m = mins % 60;
                    framesValueText.setText(h > 0 ? (h + "h" + m + "m") : (m + "m"));
                } else {
                    if (framesTopLabel != null) framesTopLabel.setText("FRAMES");
                    long count = AppSettings.getEstimatedRemainingFrames(this);
                    framesValueText.setText(count > 9999 ? "9999+" : String.valueOf(count));
                }
            }
        }
        updateTelemetryVisibility();
    }

    private void updateTelemetryVisibility() {
        boolean easyMode = AppSettings.getBoolean(this, AppSettings.EASY_MODE, true);
        boolean manualExp = AppSettings.get(this, AppSettings.EXPOSURE_MODE, "Auto exposure").startsWith("Manual");
        int wb = AppSettings.resolveWhiteBalanceKelvin(this);
        boolean showTelemetry = AppSettings.getBoolean(this, AppSettings.SHOW_TELEMETRY, false)
                || !easyMode || manualExp || wb > 0;
        boolean activeVideo = videoRecording || videoStarting || switchingVideoCamera;
        if (telemetry != null) {
            telemetry.setVisibility(showTelemetry ? View.VISIBLE : View.GONE);
        }
        if (cameraState != null) {
            cameraState.setVisibility((showTelemetry || activeVideo) ? View.VISIBLE : View.GONE);
        }
        if (outputModeText != null) {
            outputModeText.setVisibility(View.GONE);
        }
    }

    private void chooseShutterSpeed() {
        String[] options = {
                "Auto exposure",
                "1/30 sec",
                "1/60 sec",
                "1/120 sec",
                "1/240 sec",
                "1/500 sec",
                "1/1000 sec",
                "1/2000 sec",
                "1/4000 sec"
        };
        String[] subtitles = {
                "Intelligent scene-adaptive shutter & ISO",
                "Slow shutter · Handheld low-light limit",
                "Standard indoor / 30fps 180° cinema shutter",
                "Everyday walkaround · freezes casual motion",
                "Street & portraits · crisp subject motion",
                "Fast action & outdoor daylight",
                "High-speed daylight action",
                "Very bright sun · wide-aperture control",
                "Maximum high-speed shutter freeze"
        };
        boolean isManual = AppSettings.get(this, AppSettings.EXPOSURE_MODE, "Auto exposure").startsWith("Manual");
        String curShutter = AppSettings.get(this, AppSettings.MANUAL_SHUTTER, "1/120 sec");
        int currentIndex = 0;
        if (isManual) {
            for (int i = 1; i < options.length; i++) {
                if (options[i].equals(curShutter)) {
                    currentIndex = i;
                    break;
                }
            }
        }
        showCustomBottomSheet("SHUTTER SPEED", options, subtitles, currentIndex, index -> {
            if (index == 0) {
                AppSettings.prefs(this).edit().putString(AppSettings.EXPOSURE_MODE, "Auto exposure").apply();
                showMessage("AUTO EXPOSURE ACTIVE");
            } else {
                if (!manualSupported) {
                    showMessage("Manual sensor exposure not supported on this lens");
                    return;
                }
                AppSettings.prefs(this).edit()
                        .putString(AppSettings.EXPOSURE_MODE, "Manual exposure")
                        .putString(AppSettings.MANUAL_SHUTTER, options[index])
                        .apply();
                showMessage("MANUAL SHUTTER · " + options[index].replace(" sec", "s"));
            }
            updateReadouts();
            refreshPreviewRequest();
        });
    }

    private void chooseManualIso() {
        String[] options = {
                "Auto ISO",
                "ISO 50",
                "ISO 100",
                "ISO 200",
                "ISO 400",
                "ISO 800",
                "ISO 1600",
                "ISO 3200"
        };
        String[] subtitles = {
                "Automatic sensor gain & exposure",
                "Base lowest gain · maximum dynamic range",
                "Daylight fine grain baseline",
                "Bright overcast & shaded daylight",
                "Classic Tri-X / Portra 400 speed",
                "Tungsten indoor & evening cinema",
                "Low-light night street",
                "Pushed high-sensitivity night"
        };
        boolean isManual = AppSettings.get(this, AppSettings.EXPOSURE_MODE, "Auto exposure").startsWith("Manual");
        String curIso = AppSettings.get(this, AppSettings.MANUAL_ISO, "ISO 100");
        int currentIndex = 0;
        if (isManual) {
            for (int i = 1; i < options.length; i++) {
                if (options[i].equals(curIso)) {
                    currentIndex = i;
                    break;
                }
            }
        }
        showCustomBottomSheet("SENSOR ISO SENSITIVITY", options, subtitles, currentIndex, index -> {
            if (index == 0) {
                AppSettings.prefs(this).edit().putString(AppSettings.EXPOSURE_MODE, "Auto exposure").apply();
                showMessage("AUTO ISO & EXPOSURE ACTIVE");
            } else {
                if (!manualSupported) {
                    showMessage("Manual sensor ISO not supported on this lens");
                    return;
                }
                AppSettings.prefs(this).edit()
                        .putString(AppSettings.EXPOSURE_MODE, "Manual exposure")
                        .putString(AppSettings.MANUAL_ISO, options[index])
                        .apply();
                showMessage("MANUAL " + options[index]);
            }
            updateReadouts();
            refreshPreviewRequest();
        });
    }

    private void chooseWhiteBalance() {
        String[] options = {
                "Auto · as shot",
                "2800 K · incandescent",
                "3200 K · tungsten",
                "4000 K · warm LED",
                "5000 K · direct sun",
                "5600 K · daylight",
                "6500 K · overcast",
                "7500 K · open shade"
        };
        int[] kelvins = {0, 2800, 3200, 4000, 5000, 5600, 6500, 7500};
        String[] subtitles = {
                "Uses sensor neutral point & illuminant adaptation",
                "Warm household filament bulbs",
                "Kodak Vision3 500T native tungsten balance",
                "Indoor fluorescent & warm studio LED",
                "Clean noon sun",
                "Standard 5600K daylight film balance",
                "Cloudy sky · adds pleasing golden warmth",
                "Deep blue shade · maximum amber correction"
        };
        int currentWb = AppSettings.resolveWhiteBalanceKelvin(this);
        int currentIndex = 0;
        for (int i = 1; i < kelvins.length; i++) {
            if (Math.abs(currentWb - kelvins[i]) <= 250) {
                currentIndex = i;
                break;
            }
        }
        showCustomBottomSheet("WHITE BALANCE", options, subtitles, currentIndex, index -> {
            int chosenKelvin = kelvins[index];
            AppSettings.setWhiteBalanceKelvin(this, chosenKelvin);
            updateReadouts();
            applyLiveFilmPreview();
            showMessage(chosenKelvin == 0 ? "WHITE BALANCE · AUTO" : "WHITE BALANCE · " + chosenKelvin + " K");
        });
    }

    private void configureTransform(int viewWidth, int viewHeight) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread(() -> configureTransform(viewWidth, viewHeight));
            return;
        }
        if (textureView == null || previewSize == null || characteristics == null || viewWidth <= 0 || viewHeight <= 0) return;
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
        matrix.postTranslate(frame.centerX() - centerX, frame.centerY() - centerY);
        textureView.setTransform(matrix);

        if (lensRow != null && frame.bottom > 0) {
            ViewGroup.LayoutParams lp = lensRow.getLayoutParams();
            if (lp instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) lp;
                int desiredTop = (int) (frame.bottom - dp(44) - dp(12));
                if (flp.topMargin != desiredTop) {
                    flp.topMargin = desiredTop;
                    lensRow.setLayoutParams(flp);
                }
            }
        }
        if (zoomSliderPanel != null && frame.bottom > 0) {
            ViewGroup.LayoutParams lp = zoomSliderPanel.getLayoutParams();
            if (lp instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) lp;
                int desiredTop = (int) (frame.bottom - dp(44) - dp(12) - dp(40));
                if (flp.topMargin != desiredTop) {
                    flp.topMargin = desiredTop;
                    zoomSliderPanel.setLayoutParams(flp);
                }
            }
        }
        if (viewfinderSpacer != null && frame.height() > 0) {
            ViewGroup.LayoutParams lp = viewfinderSpacer.getLayoutParams();
            if (lp instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) lp;
                if (flp.width != (int) frame.width() || flp.height != (int) frame.height()
                        || flp.leftMargin != (int) frame.left || flp.topMargin != (int) frame.top) {
                    flp.width = (int) frame.width();
                    flp.height = (int) frame.height();
                    flp.leftMargin = (int) frame.left;
                    flp.topMargin = (int) frame.top;
                    viewfinderSpacer.setLayoutParams(flp);
                }
            }
        }
    }

    private final class ShutterTouchListener implements View.OnTouchListener {
        private boolean isDown = false;
        private final Handler shutterHandler = new Handler(Looper.getMainLooper());
        private final Runnable quickTakeRunnable = () -> {
            if (!isDown || videoRecording || videoStarting || switchingVideoCamera) return;
            isQuickTake = true;
            if (AppSettings.getBoolean(MainActivity.this, AppSettings.HAPTICS, true)) {
                shutterFrame.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            }
            startVideoRecording();
        };

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (!shutterFrame.isEnabled() || (captureButton != null && !captureButton.isEnabled())) {
                return false;
            }
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    isDown = true;
                    isQuickTake = false;
                    hapticClick(v);
                    shutterFrame.animate().cancel();
                    captureButton.animate().cancel();
                    shutterFrame.animate().scaleX(0.88f).scaleY(0.88f).setDuration(70).setInterpolator(new DecelerateInterpolator()).start();
                    captureButton.animate().scaleX(0.92f).scaleY(0.92f).setDuration(70).setInterpolator(new DecelerateInterpolator()).start();

                    if (!videoMode && !videoRecording && !videoStarting && !switchingVideoCamera) {
                        shutterHandler.postDelayed(quickTakeRunnable, 450L);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    shutterHandler.removeCallbacks(quickTakeRunnable);
                    if (isDown) {
                        isDown = false;
                        shutterFrame.animate().cancel();
                        captureButton.animate().cancel();
                        shutterFrame.animate().scaleX(1.0f).scaleY(1.0f).setDuration(220)
                                .setInterpolator(new android.view.animation.OvershootInterpolator(2.0f)).start();
                        captureButton.animate().scaleX(1.0f).scaleY(1.0f).setDuration(220)
                                .setInterpolator(new android.view.animation.OvershootInterpolator(1.8f)).start();

                        if (isQuickTake) {
                            isQuickTake = false;
                            if (AppSettings.getBoolean(MainActivity.this, AppSettings.HAPTICS, true)) {
                                v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                            }
                            stopVideoRecording(true);
                            return true;
                        }

                        float x = event.getX(), y = event.getY();
                        int slop = dp(32);
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
                    shutterHandler.removeCallbacks(quickTakeRunnable);
                    if (isDown) {
                        isDown = false;
                        shutterFrame.animate().cancel();
                        captureButton.animate().cancel();
                        shutterFrame.animate().scaleX(1.0f).scaleY(1.0f).setDuration(160)
                                .setInterpolator(new DecelerateInterpolator()).start();
                        captureButton.animate().scaleX(1.0f).scaleY(1.0f).setDuration(160)
                                .setInterpolator(new DecelerateInterpolator()).start();
                        if (isQuickTake) {
                            isQuickTake = false;
                            stopVideoRecording(true);
                        }
                        return true;
                    }
                    break;
            }
            return false;
        }
    }

    private final class FocusReticleOverlay extends View {
        private float reticleX = -1f;
        private float reticleY = -1f;
        private float reticleScale = 1.0f;
        private float evBias = 0f;
        private int shadowDepth = 50;
        private boolean showEvSlider = false;
        private boolean isVisible = false;
        private Bitmap loupeBitmap;
        private long lastLoupeSample;
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
            sampleLoupe(x, y);
            invalidate();
        }

        void sampleLoupe(float x, float y) {
            if (!AppSettings.getBoolean(MainActivity.this, AppSettings.OPTICAL_LOUPE, false)) return;
            long now = SystemClock.elapsedRealtime();
            if (now - lastLoupeSample < 60) return;
            lastLoupeSample = now;
            if (textureView == null || !textureView.isAvailable()) return;
            try {
                int tvW = textureView.getWidth();
                int tvH = textureView.getHeight();
                if (tvW <= 0 || tvH <= 0) return;
                int cropW = dp(44);
                int cropH = dp(44);
                int left = Math.max(0, Math.min(tvW - cropW, (int) (x - cropW / 2f)));
                int top = Math.max(0, Math.min(tvH - cropH, (int) (y - cropH / 2f)));
                Bitmap full = textureView.getBitmap(tvW / 3, tvH / 3);
                if (full != null) {
                    int sLeft = Math.max(0, Math.min(full.getWidth() - 32, (left * full.getWidth()) / tvW));
                    int sTop = Math.max(0, Math.min(full.getHeight() - 32, (top * full.getHeight()) / tvH));
                    int sSize = Math.min(32, Math.min(full.getWidth() - sLeft, full.getHeight() - sTop));
                    if (sSize > 6) {
                        Bitmap crop = Bitmap.createBitmap(full, sLeft, sTop, sSize, sSize);
                        if (loupeBitmap != null && !loupeBitmap.isRecycled()) loupeBitmap.recycle();
                        loupeBitmap = crop;
                        invalidate();
                    }
                    full.recycle();
                }
            } catch (Throwable t) {
                Log.w(TAG, "Failed to sample loupe bitmap", t);
            }
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
            if (loupeBitmap != null && !loupeBitmap.isRecycled()) {
                loupeBitmap.recycle();
                loupeBitmap = null;
            }
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

            // Pro Optical Loupe (5x Magnifier Lens with Leica Gold Ring)
            if (AppSettings.getBoolean(MainActivity.this, AppSettings.OPTICAL_LOUPE, false)
                    && loupeBitmap != null && !loupeBitmap.isRecycled()) {
                float loupeR = dp(42);
                float loupeX = Math.max(loupeR + dp(16), Math.min(getWidth() - loupeR - dp(16), reticleX));
                float loupeY = Math.max(loupeR + dp(72), reticleY - dp(92));

                Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                shadowPaint.setColor(0x88000000);
                canvas.drawCircle(loupeX, loupeY + dp(3), loupeR + dp(2), shadowPaint);

                canvas.save();
                Path circlePath = new Path();
                circlePath.addCircle(loupeX, loupeY, loupeR, Path.Direction.CW);
                canvas.clipPath(circlePath);

                Rect src = new Rect(0, 0, loupeBitmap.getWidth(), loupeBitmap.getHeight());
                RectF dst = new RectF(loupeX - loupeR, loupeY - loupeR, loupeX + loupeR, loupeY + loupeR);
                canvas.drawBitmap(loupeBitmap, src, dst, null);

                Paint chPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                chPaint.setColor(0x99FFFFFF);
                chPaint.setStrokeWidth(dp(1f));
                canvas.drawLine(loupeX - dp(14), loupeY, loupeX + dp(14), loupeY, chPaint);
                canvas.drawLine(loupeX, loupeY - dp(14), loupeX, loupeY + dp(14), chPaint);
                canvas.drawCircle(loupeX, loupeY, dp(2), chPaint);
                canvas.restore();

                Paint bezelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                bezelPaint.setColor(ACCENT);
                bezelPaint.setStyle(Paint.Style.STROKE);
                bezelPaint.setStrokeWidth(dp(2.2f));
                canvas.drawCircle(loupeX, loupeY, loupeR, bezelPaint);

                Paint badgeBg = new Paint(Paint.ANTI_ALIAS_FLAG);
                badgeBg.setColor(0xDD0B0D0E);
                badgeBg.setStyle(Paint.Style.FILL);
                RectF badgeRect = new RectF(loupeX - dp(26), loupeY - loupeR - dp(13), loupeX + dp(26), loupeY - loupeR + dp(5));
                canvas.drawRoundRect(badgeRect, dp(4), dp(4), badgeBg);
                bezelPaint.setStrokeWidth(dp(1f));
                canvas.drawRoundRect(badgeRect, dp(4), dp(4), bezelPaint);

                Paint badgeText = new Paint(Paint.ANTI_ALIAS_FLAG);
                badgeText.setColor(ACCENT);
                badgeText.setTextSize(dp(8.5f));
                badgeText.setTypeface(Typeface.DEFAULT_BOLD);
                badgeText.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("5× FOCUS", loupeX, loupeY - loupeR - dp(1.5f), badgeText);
            }
        }
    }

    private final class GestureOverlay extends FrameLayout {
        GestureOverlay() {
            super(MainActivity.this);
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
            setClipToPadding(false);
            setClipChildren(false);
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
            try {
                reader.setOnImageAvailableListener(null, null);
            } catch (RuntimeException e) {
                Log.w(TAG, "Failed to clear ImageReader listener", e);
            }
            reader.close();
        }
    }

    private final class CompositionGrid extends View {
        private final Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint aidPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF currentHistRect = new RectF();

        CompositionGrid(Activity activity) {
            super(activity);
            maskPaint.setColor(BG);
            gridPaint.setColor(0xb8f2f3ec);
            gridPaint.setStyle(Paint.Style.STROKE);
            gridPaint.setStrokeWidth(activity.getResources().getDisplayMetrics().density);
            framePaint.setColor(0x18ffffff);
            framePaint.setStyle(Paint.Style.STROKE);
            framePaint.setStrokeWidth(activity.getResources().getDisplayMetrics().density);
        }

        boolean isHistogramHit(float x, float y) {
            return !currentHistRect.isEmpty() && currentHistRect.contains(x, y);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            RectF frame = framingRect(w, h);
            if (frame.width() <= 0 || frame.height() <= 0) return;
            float density = getResources().getDisplayMetrics().density;
            float cornerRadius = Math.min(density * 24f, Math.min(frame.width(), frame.height()) * 0.5f);

            // Mask outside the curved viewfinder card with solid Leica obsidian black
            Path maskPath = new Path();
            maskPath.setFillType(Path.FillType.EVEN_ODD);
            maskPath.addRect(0, 0, w, h, Path.Direction.CW);
            maskPath.addRoundRect(frame, cornerRadius, cornerRadius, Path.Direction.CW);
            maskPaint.setColor(BG);
            maskPaint.setStyle(Paint.Style.FILL);
            canvas.drawPath(maskPath, maskPaint);

            // Clip inner aids to the curved viewfinder
            Path clipPath = new Path();
            clipPath.addRoundRect(frame, cornerRadius, cornerRadius, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(clipPath);

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

            // Live RGB / Luminance Histogram Overlay & Clipping LEDs
            if (AppSettings.getBoolean(MainActivity.this, AppSettings.HISTOGRAM, false)) {
                float histW = density * 92f;
                float histH = density * 44f;
                float histRight = frame.right - density * 12f;
                float histLeft = histRight - histW;
                float histTop = Math.max(frame.top + density * 12f, density * 96f);
                float histBottom = histTop + histH;
                currentHistRect.set(histLeft, histTop, histRight, histBottom);

                aidPaint.setStyle(Paint.Style.FILL);
                aidPaint.setColor(0xD20A0D0E);
                canvas.drawRoundRect(currentHistRect, density * 6f, density * 6f, aidPaint);

                aidPaint.setStyle(Paint.Style.STROKE);
                aidPaint.setStrokeWidth(density * 0.9f);
                aidPaint.setColor(0x66D7B574);
                canvas.drawRoundRect(currentHistRect, density * 6f, density * 6f, aidPaint);

                // Clipping LEDs at top corners
                aidPaint.setStyle(Paint.Style.FILL);
                aidPaint.setColor(liveShadowClipped ? 0xFFFFB03A : 0x2289948E);
                canvas.drawCircle(histLeft + density * 8f, histTop + density * 7f, density * 2.2f, aidPaint);
                aidPaint.setColor(liveHighlightClipped ? 0xFFFF3B30 : 0x2289948E);
                canvas.drawCircle(histRight - density * 8f, histTop + density * 7f, density * 2.2f, aidPaint);

                String mode = AppSettings.get(MainActivity.this, AppSettings.HISTOGRAM_MODE, "Luminance");

                Paint modePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                modePaint.setColor(0x88D7B574);
                modePaint.setTextSize(density * 7.5f);
                modePaint.setTypeface(Typeface.DEFAULT_BOLD);
                modePaint.setTextAlign(Paint.Align.CENTER);
                String tag = "RGB Parade".equals(mode) ? "RGB" : "Compact".equals(mode) ? "MINI" : "LUMA";
                canvas.drawText(tag, currentHistRect.centerX(), histTop + density * 8.5f, modePaint);

                float barW = (histW - density * 8f) / 32f;
                float maxBarH = histH - density * 14f;

                int maxBin = 1;
                for (int i = 0; i < 32; i++) {
                    if (liveHistogramBins[i] > maxBin) maxBin = liveHistogramBins[i];
                    if (liveHistR[i] > maxBin) maxBin = liveHistR[i];
                    if (liveHistG[i] > maxBin) maxBin = liveHistG[i];
                    if (liveHistB[i] > maxBin) maxBin = liveHistB[i];
                }

                if ("RGB Parade".equals(mode)) {
                    for (int channel = 0; channel < 3; channel++) {
                        int[] bins = channel == 0 ? liveHistR : channel == 1 ? liveHistG : liveHistB;
                        int col = channel == 0 ? 0x99FF5544 : channel == 1 ? 0x9944DD66 : 0x994499FF;
                        aidPaint.setStyle(Paint.Style.FILL);
                        aidPaint.setColor(col);
                        for (int i = 0; i < 32; i++) {
                            float norm = (float) bins[i] / maxBin;
                            float bh = Math.max(density * 1.0f, norm * maxBarH);
                            float bx = histLeft + density * 4f + i * barW;
                            canvas.drawRect(bx, histBottom - density * 4f - bh, bx + Math.max(1f, barW - density * 0.5f), histBottom - density * 4f, aidPaint);
                        }
                    }
                } else if ("Compact".equals(mode)) {
                    Path spark = new Path();
                    spark.moveTo(histLeft + density * 4f, histBottom - density * 4f);
                    for (int i = 0; i < 32; i++) {
                        float norm = (float) liveHistogramBins[i] / maxBin;
                        float bh = norm * maxBarH;
                        float bx = histLeft + density * 4f + i * barW + barW * 0.5f;
                        spark.lineTo(bx, histBottom - density * 4f - bh);
                    }
                    spark.lineTo(histRight - density * 4f, histBottom - density * 4f);
                    aidPaint.setStyle(Paint.Style.FILL);
                    aidPaint.setColor(0x33D7B574);
                    canvas.drawPath(spark, aidPaint);
                    aidPaint.setStyle(Paint.Style.STROKE);
                    aidPaint.setStrokeWidth(density * 1.2f);
                    aidPaint.setColor(ACCENT);
                    canvas.drawPath(spark, aidPaint);
                } else {
                    aidPaint.setStyle(Paint.Style.FILL);
                    for (int i = 0; i < 32; i++) {
                        float norm = (float) liveHistogramBins[i] / maxBin;
                        float bh = Math.max(density * 1.2f, norm * maxBarH);
                        float bx = histLeft + density * 4f + i * barW;
                        aidPaint.setColor(i >= 30 ? 0xDDFF4444 : (i == 0 && liveShadowClipped) ? 0xDDFFA030 : 0xCCD7B574);
                        canvas.drawRect(bx, histBottom - density * 4f - bh, bx + Math.max(1f, barW - density * 0.6f), histBottom - density * 4f, aidPaint);
                    }
                }
            } else {
                currentHistRect.setEmpty();
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

            canvas.restore();

            framePaint.setColor(0x18ffffff);
            framePaint.setStyle(Paint.Style.STROKE);
            framePaint.setStrokeWidth(density * 1.0f);
            canvas.drawRoundRect(frame, cornerRadius, cornerRadius, framePaint);
        }
    }

    private void closeCamera() {
        if (timerCountdownRunning) cancelTimerCountdown();
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
        if (requestCode == LOCATION_PERMISSION) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted) showMessage("GPS geotagging active");
        }
    }
}
