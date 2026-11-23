package com.scope.camera;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.media.AudioManager;
import android.media.ExifInterface;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaActionSound;
import android.media.MediaRecorder;
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
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
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

    private volatile boolean activityResumed;
    private TextureView textureView;
    private final LiveLookPreview liveLookPreview = new LiveLookPreview();
    private TextView cameraState;
    private TextView aeLockBadge;
    private TextView brainButton;
    private TextView flashButton;
    private TextView aspectButton;
    private TextView timerButton;
    private TextView focusToggleButton;
    private TextView loupeToggleButton;
    private LinearLayout manualFocusPanel;
    private SeekBar manualFocusSeekBar;
    private TextView manualFocusValue;
    private TextView manualFocusExitButton;
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
    private ImageView flipButton;
    private ImageView settingsButton;
    private TextView modeNote;
    private TextView messageView;
    private TextView outputModeText;
    private FrameLayout galleryContainer;
    private ImageView galleryButton;
    private View galleryProcessingScrim;
    private android.widget.ProgressBar galleryProcessingSpinner;
    private TextView shutterValueText;
    private TextView isoValueText;
    private TextView whiteBalanceValueText;
    private TextView framesValueText;
    private TextView framesTopLabel;
    private View framesBox;
    private volatile StillCaptureState stillCaptureState = StillCaptureState.IDLE;
    private volatile boolean timerTorchActive = false;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerCountdownRunnable;
    private final RequestEpoch delayedCaptureEpoch = new RequestEpoch();
    private float originalFlashBrightness=Float.NaN;
    private final ExposureAidsAnalyzer exposureAidsAnalyzer = new ExposureAidsAnalyzer();
    private TextView zoomSliderValue;
    private final List<TextView> lensButtons = new ArrayList<>();
    private CompositionGrid gridOverlay;
    private View viewfinderSpacer;
    private SensorManager sensorManager;
    private Sensor orientationSensor;
    private android.view.OrientationEventListener orientationEventListener;
    private volatile int quantizedDeviceRotation = -1;
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
    private final InteractionState.Motion motion = new InteractionState.Motion();

    private volatile boolean prefVisualLevel = true;
    private volatile boolean prefHaptics = true;
    private volatile String prefHorizonSensitivity = "Normal (±1.0°)";
    private volatile String prefHorizonMode = "Smart (near level only)";
    private volatile String prefStabilization = "Cinema OIS + EIS";
    private volatile String prefGridStyle = "None";
    private volatile boolean prefHistogram = false;
    private volatile String prefHistogramMode = "Luminance";
    private volatile boolean prefZebra = false;
    private volatile boolean prefFocusPeaking = false;
    private volatile boolean prefOpticalLoupe = false;
    private volatile boolean prefLoupeOnMfAdjust = false;
    private volatile boolean prefLiveFilmPreview = true;
    private volatile boolean prefDirtyLensWarning = true;
    private volatile boolean prefPalmTimer = false;
    private volatile String prefAspectRatio = "4:3";

    private final float[] rotationMatrixBuf = new float[9];

    private final SharedPreferences.OnSharedPreferenceChangeListener hotPrefsListener =
            (prefs, key) -> refreshHotPrefs(prefs);

    private void refreshHotPrefs() {
        refreshHotPrefs(AppSettings.prefs(this));
    }

    private void refreshHotPrefs(SharedPreferences prefs) {
        prefVisualLevel = prefs.getBoolean(AppSettings.VISUAL_LEVEL, true);
        prefHaptics = prefs.getBoolean(AppSettings.HAPTICS, true);
        prefHorizonSensitivity = prefs.getString(AppSettings.HORIZON_SENSITIVITY, "Normal (±1.0°)");
        prefHorizonMode = prefs.getString(AppSettings.HORIZON_MODE, "Smart (near level only)");
        prefStabilization = prefs.getString(AppSettings.STABILIZATION, "Cinema OIS + EIS");
        prefGridStyle = prefs.getString(AppSettings.GRID_STYLE, "None");
        prefHistogram = prefs.getBoolean(AppSettings.HISTOGRAM, false);
        prefHistogramMode = prefs.getString(AppSettings.HISTOGRAM_MODE, "Luminance");
        prefZebra = prefs.getBoolean(AppSettings.ZEBRA, false);
        prefFocusPeaking = prefs.getBoolean(AppSettings.FOCUS_PEAKING, false);
        prefOpticalLoupe = prefs.getBoolean(AppSettings.OPTICAL_LOUPE, false);
        prefLoupeOnMfAdjust = prefs.getBoolean(AppSettings.LOUPE_ON_MF_ADJUST, false);
        prefLiveFilmPreview = prefs.getBoolean(AppSettings.LIVE_FILM_PREVIEW, true);
        prefDirtyLensWarning = prefs.getBoolean(AppSettings.DIRTY_LENS_WARNING, true);
        prefPalmTimer = prefs.getBoolean(AppSettings.PALM_TIMER, false);
        prefAspectRatio = prefs.getString(AppSettings.ASPECT_RATIO, "4:3");
        invalidateFramingRectCache();
    }

    private final SensorEventListener sensorEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
                SensorManager.getRotationMatrixFromVector(rotationMatrixBuf, event.values);
                // In rotationMatrix, row 2 represents the world Up vector (0, 0, 1) in device coordinates:
                // rotationMatrix[6] = Up.x, rotationMatrix[7] = Up.y, rotationMatrix[8] = Up.z
                float upX = rotationMatrixBuf[6];
                float upY = rotationMatrixBuf[7];
                float upZ = rotationMatrixBuf[8];

                // Elevation tilt relative to the horizon:
                // 0° when aiming at the horizon (optical axis horizontal)
                // +90° when aiming straight down at table / -90° straight up at sky
                float tiltDegrees = (float) Math.toDegrees(Math.asin(Math.max(-1f, Math.min(1f, upZ))));
                devicePitchAngle = tiltDegrees;
                if (!hasOrientationSample) {
                    smoothPitchAngle = tiltDegrees;
                    smoothTiltX = upX;
                    smoothTiltY = upY;
                } else {
                    smoothPitchAngle = smoothPitchAngle * 0.82f + tiltDegrees * 0.18f;
                    smoothTiltX = smoothTiltX * 0.80f + upX * 0.20f;
                    smoothTiltY = smoothTiltY * 0.80f + upY * 0.20f;
                }

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
                if (!hasOrientationSample) {
                    smoothPitchAngle = tiltDeg;
                    smoothTiltX = upX;
                    smoothTiltY = upY;
                } else {
                    smoothPitchAngle = smoothPitchAngle * 0.82f + tiltDeg * 0.18f;
                    smoothTiltX = smoothTiltX * 0.80f + upX * 0.20f;
                    smoothTiltY = smoothTiltY * 0.80f + upY * 0.20f;
                }

                float angle = (float) Math.toDegrees(Math.atan2(upX, upY));
                updateHorizonRoll(angle);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };

    private boolean wasHorizonLevel = false;
    private boolean hasOrientationSample = false;

    private void updateHorizonRoll(float angle) {
        float normalized = ((angle % 360f) + 360f) % 360f;
        float nearestCardinal = Math.round(normalized / 90f) * 90f;
        float diff = normalized - nearestCardinal;
        if (diff > 180f) diff -= 360f;
        if (diff < -180f) diff += 360f;

        deviceRollAngle = diff;
        if (!hasOrientationSample) {
            smoothRollAngle = deviceRollAngle;
        } else {
            smoothRollAngle = smoothRollAngle * 0.82f + deviceRollAngle * 0.18f;
        }

        boolean showLevel = prefVisualLevel;
        if (showLevel && prefHaptics) {
            float deadband = prefHorizonSensitivity.contains("0.5") ? 0.5f : 1.0f;
            boolean isFlatLay = Math.abs(smoothPitchAngle) > 65f;
            boolean isLevel = isFlatLay
                    ? (Math.hypot(smoothTiltX, smoothTiltY) <= (deadband * 0.025f))
                    : (Math.abs(smoothRollAngle) <= deadband);
            if (!hasOrientationSample) {
                wasHorizonLevel = isLevel;
            } else if (isLevel && !wasHorizonLevel) {
                View target = gridOverlay != null ? gridOverlay : captureButton;
                if (target != null) {
                    target.performHapticFeedback(HapticFeedbackConstants.CONFIRM, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                }
                wasHorizonLevel = true;
            } else if (!isLevel && (isFlatLay ? Math.hypot(smoothTiltX, smoothTiltY) > (deadband * 0.038f) : Math.abs(smoothRollAngle) > (deadband + 0.45f))) {
                wasHorizonLevel = false;
            }
        } else if (!hasOrientationSample) {
            wasHorizonLevel = false;
        }
        hasOrientationSample = true;

        float deltaMovement = Math.abs(deviceRollAngle - smoothRollAngle) + Math.abs(devicePitchAngle - smoothPitchAngle);
        deviceStabilityScore = deviceStabilityScore * 0.90f + deltaMovement * 0.10f;
        boolean wasStationary = motion.stationary();
        long nowUptime = SystemClock.uptimeMillis();
        motion.sample(deviceStabilityScore, nowUptime);
        if (motion.stationary() != wasStationary && modeNote != null && !videoMode) {
            runOnUiThread(this::updateContextualStatus);
        }

        if (showLevel && gridOverlay != null) {
            gridOverlay.postInvalidate();
        }
        if (videoState.isRecordingOrPaused() && captureSession != null && previewRequest != null) {
            if (prefStabilization.startsWith("Locked") && hasLockedVideoAnchor) {
                long now = SystemClock.uptimeMillis();
                if (now - lastTripodCropUpdateAt > 40L) {
                    lastTripodCropUpdateAt = now;
                    try {
                        applyZoom(previewRequest);
                        captureSession.setRepeatingRequest(previewRequest.build(), previewMetadataCallback, cameraHandler);
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
        if (!prefHaptics) return;
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
        if (!prefHaptics) return;
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
        boolean userEnabled = AppSettings.getBoolean(this, AppSettings.SHUTTER_SOUND, true);
        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        int ringerMode = audioManager != null ? audioManager.getRingerMode() : AudioManager.RINGER_MODE_NORMAL;
        boolean mandatory = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && MediaActionSound.mustPlayShutterSound();
        if (!CameraExposureHelper.shouldPlayShutterSound(userEnabled, ringerMode, mandatory)) return;
        try {
            if (mediaActionSound == null) {
                mediaActionSound = new MediaActionSound();
                mediaActionSound.load(MediaActionSound.SHUTTER_CLICK);
                mediaActionSound.load(MediaActionSound.START_VIDEO_RECORDING);
                mediaActionSound.load(MediaActionSound.STOP_VIDEO_RECORDING);
            }
            mediaActionSound.play(soundType);
        } catch (Throwable t) {
            Log.w(TAG, "Failed to play shutter/video sound", t);
        }
    }

    private FocusReticleOverlay focusReticle;
    private final Handler focusHandler = new Handler(Looper.getMainLooper());
    private enum FocusMode { CONTINUOUS, TARGETED, LOCKED }
    private FocusMode focusMode = FocusMode.CONTINUOUS;
    private MeteringRectangle[] currentAfRegions;
    private MeteringRectangle[] currentAeRegions;
    private int currentAeIndex = 0;
    private float currentEvOffset = 0f;
    private boolean aeLocked = false;
    private float dragStartY = 0f;
    private float dragStartEv = 0f;
    private int dragStartShadows = 50;
    private volatile boolean manualLoupeActive = false;
    private volatile boolean mfDragLoupeActive = false;
    private volatile float lastLiveFocusDistance = Float.NaN;
    private String lastAutoFocusMode = AppSettings.FOCUS_CONTINUOUS;
    private boolean manualFocusSliderSyncing = false;
    private long lastMfPreviewRequestAt = 0L;

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
    private LinearLayout headerLeftGroup;
    private LinearLayout headerRightGroup;
    private View topSpacer;
    private LinearLayout telemetry;
    private ZoomPresetRow lensRow;
    private LinearLayout modeRow;
    private LinearLayout lookRow;
    private FrameLayout shootRow;
    private View deckSpacer;
    private String appliedLeftHandMode;
    private CameraManager cameraManager;
    private volatile CameraDevice cameraDevice;
    private volatile CameraCaptureSession captureSession;
    private CaptureRequest.Builder previewRequest;
    private volatile RawArchive.Frame previewColourMetadata;
    private long lastPreviewMetadataAt;
    private final CameraCaptureSession.CaptureCallback previewMetadataCallback = new CameraCaptureSession.CaptureCallback() {
        @Override public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            if (session == captureSession) {
                Float liveDist = result.get(CaptureResult.LENS_FOCUS_DISTANCE);
                if (liveDist != null && Float.isFinite(liveDist) && liveDist >= 0f) {
                    lastLiveFocusDistance = liveDist;
                }
            }
            if (session != captureSession) return;
            if (pendingPostConfigZoom > 0f && pendingPostConfigSessionTicket >= 0L) {
                triggerPendingPostConfigZoom(session, pendingPostConfigSessionTicket);
            }
            Long exposure = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
            Integer iso = result.get(CaptureResult.SENSOR_SENSITIVITY);
            if (exposure != null && exposure > 0L && iso != null && iso > 0) {
                lastMeteredExposureNs = exposure;
                lastMeteredIso = iso;
                if (VideoRecordingCoordinator.canAutoEngage180Shutter(
                        videoMode, video180Active, aeLocked, isVideo180Requested(), manualSupported)) {
                    maybeEngageVideo180FromMetered(exposure, iso);
                }
            }
            long now = SystemClock.elapsedRealtime();
            if (now - lastPreviewMetadataAt < 500L) return;
            lastPreviewMetadataAt = now;
            if (videoState.isRecordingOrPaused()) {
                runOnUiThread(MainActivity.this::updateReadouts);
                return;
            }
            android.hardware.camera2.params.RggbChannelVector gains = result.get(CaptureResult.COLOR_CORRECTION_GAINS);
            RawArchive.Frame metadata = new RawArchive.Frame();
            if (gains != null) metadata.wbGains = new float[]{gains.getRed(), gains.getGreenEven(), gains.getGreenOdd(), gains.getBlue()};
            metadata.exposureTimeNs = exposure == null ? 0L : exposure;
            metadata.sensitivityIso = iso == null ? 0 : iso;
            previewColourMetadata = metadata;
            runOnUiThread(() -> {
                if (prefLiveFilmPreview) {
                    applyLiveFilmPreview();
                }
                updateContextualStatus();
            });
        }
    };
    private Surface previewSurface;
    private SurfaceTexture previewSurfaceTexture;
    private ImageReader rawReader;
    private ImageReader jpegReader;
    private MediaRecorder mediaRecorder;
    private VideoRotationSurface videoRotationSurface;
    private Surface recordingEncoderSurface;
    private Surface recordingInputSurface;
    private Surface recordingPreviewSurface;
    private File videoTempFile;
    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private final Handler cameraUiHandler = new Handler(Looper.getMainLooper());
    private int cameraOpenRetryCount = 0;
    private boolean cameraRetryPromptActive = false;
    private boolean availabilityCallbackRegistered = false;
    private final Runnable cameraReopenRetryRunnable = this::attemptCameraReopenRetry;
    private final CameraManager.AvailabilityCallback cameraAvailabilityCallback =
            new CameraManager.AvailabilityCallback() {
                @Override public void onCameraAvailable(String availableCameraId) {
                    if (availableCameraId == null || !availableCameraId.equals(cameraId)) return;
                    cameraUiHandler.post(() -> {
                        if (activityResumed && !isFinishing() && !isDestroyed()
                                && cameraLifecycleState.canOpen()
                                && textureView != null && textureView.isAvailable()) {
                            openCameraIfReady();
                        }
                    });
                }
            };
    private AppUpdateManager.UpdateInfo pendingAutoUpdateInfo;
    private final ExecutorService processor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "CaptureProcessor");
        t.setDaemon(true);
        return t;
    });
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "CameraIO");
        t.setDaemon(true);
        return t;
    });
    private final Object captureLock = new Object();
    private InFlightCapture<Image, TotalCaptureResult> activeCapture;
    private InFlightBurstCapture<Image, TotalCaptureResult> activeBurstCapture;
    private NightStackSession activeBurstSession;
    private final java.util.Map<ImageReader,ResourceLease> readerLeases=new java.util.IdentityHashMap<>();
    private final java.util.Map<Image,ResourceLease> imageLeases=new java.util.IdentityHashMap<>();
    private long nextCaptureId = 1L;
    private final CaptureJobs captureJobs = new CaptureJobs();
    private static final AtomicInteger videoSaveJobs = new AtomicInteger();
    private final RequestEpoch cameraEpoch = new RequestEpoch();
    private final RequestEpoch sessionEpoch = new RequestEpoch();
    private final InteractionState.Gesture gesture = new InteractionState.Gesture();
    private ValueAnimator zoomAnimator;
    private int lastActiveLensIndex = -1;
    private boolean volumeKeyHolding;
    private final Handler volumeKeyHandler = new Handler(Looper.getMainLooper());
    private Runnable volumeQuickTakeRunnable;
    private volatile CameraLifecycleState cameraLifecycleState = CameraLifecycleState.CLOSED;
    private boolean rawSupported;
    private boolean manualSupported;
    private boolean manualFocusSupported;
    private float minFocusDiopters = 0f;
    private Integer focusDistanceCalibration = null;
    private boolean hasUltraWideSupport = false;
    private float maxCameraZoom = 1f;
    private boolean flashSupported;
    private boolean useRawForThisCamera;
    private boolean forceJpegForWide;
    private volatile boolean videoHdrSupported = false;
    private volatile boolean audioZoomSupported = false;
    private volatile boolean unprocessedAudioSupported = false;
    private volatile boolean activeTakeUsingHevc = false;
    private volatile boolean activeTakeUsingHdr10 = false;
    private volatile int activeTakeFps = 24;
    private volatile boolean video180Active = false;
    private volatile int video180Iso = 0;
    private volatile int video180EffectiveFps = 24;
    private volatile boolean video180FallbackNotified = false;
    private volatile long lastMeteredExposureNs = 0L;
    private volatile int lastMeteredIso = 0;
    private volatile long lastVideo180StepAt = 0L;
    private float pendingZoomSettleScale = 1.0f;
    private volatile float pendingPostConfigZoom = -1f;
    private volatile long pendingPostConfigSessionTicket = -1L;
    private Runnable pendingPostConfigFallback;
    private final AtomicBoolean pendingZoomRequest = new AtomicBoolean(false);
    private final List<ImageReader> retiredReaders = new ArrayList<>();
    private float zoomRowStartX;
    private float zoomRowStartY;
    private float zoomRowStartZoom;
    private boolean videoMode;
    private final VideoStateMachine videoState = new VideoStateMachine();
    private long videoPauseStartedAt;
    private long accumulatedVideoDurationMs;
    private int lockedVideoOrientation = -1;
    private final List<File> videoSegments = new ArrayList<>();
    private AlertDialog videoRecoveryDialog;
    private boolean videoRecoveryDeferred;
    private final InteractionState.Lock videoLock = new InteractionState.Lock();
    private String cameraId;
    private String mainCameraId;
    private CameraCharacteristics characteristics;
    private Size rawSize;
    private Size previewSize;
    private float primaryFocalLength = 1f;
    private float physicalFocalFactor = 1f;
    private volatile float requestedZoom = 1f;
    private long toastUntil;
    private long videoStartedAt;

    private Size videoSize;

    // Live exposure aids state (32-bin histogram + 24x24 zebra & peaking grid)
    private final AtomicBoolean exposureAidBusy = new AtomicBoolean(false);
    private static final int EXPOSURE_GRID_SIZE = ExposureAidsAnalyzer.GRID_SIZE;
    private final ExposureAidsAnalyzer.Callback exposureAidsCallback = new ExposureAidsAnalyzer.Callback() {
        @Override public void onDirtyLensDetected() {
            runOnUiThread(() -> showMessage("Wipe lens · Smudge or haze detected"));
        }
        @Override public void onPalmDetected() {
            runOnUiThread(() -> {
                if (!stillCaptureState.isBusy() && !videoMode && !videoState.isRecordingOrPaused()) {
                    showMessage("✋ Palm detected · 3s timer");
                    startTimerCountdown(3);
                }
            });
        }
        @Override public void onVideo180MeanLuma(double meanLuma) {
            updateVideo180AutoIsoFromLuma(meanLuma);
        }
    };
    private final Runnable exposureAidsSampler = new Runnable() {
        @Override public void run() {
            final TextureView tv = textureView;
            final CompositionGrid grid = gridOverlay;
            if (tv != null && tv.isAvailable() && grid != null) {
                final boolean wantHist = prefHistogram;
                final boolean wantZebra = prefZebra;
                final boolean wantPeak = prefFocusPeaking;
                final boolean wantDirty = exposureAidsAnalyzer.shouldCheckDirtyLens(
                        cameraId, prefDirtyLensWarning, isFrontCamera());
                final boolean wantPalm = prefPalmTimer
                        && !videoMode && !videoState.isRecordingOrPaused() && !stillCaptureState.isBusy();
                final boolean want180Luma = videoMode && video180Active && !aeLocked && isVideo180Requested();
                if (!wantHist && !wantZebra && !wantPeak && !wantDirty && !wantPalm && !want180Luma) {
                    tv.postDelayed(this, 1000L);
                    return;
                }
                if (exposureAidBusy.compareAndSet(false, true)) {
                    Bitmap sample = null;
                    try {
                        sample = tv.getBitmap(64, 48);
                    } catch (Throwable t) {
                        Log.w(TAG, "Failed to sample texture for exposure aids", t);
                    }
                    if (sample != null) {
                        final Bitmap sampleRef = sample;
                        final int sampleW = sampleRef.getWidth();
                        final int sampleH = sampleRef.getHeight();
                        ioExecutor.execute(() -> {
                            try {
                                boolean palmAllowed = !stillCaptureState.isBusy() && !videoMode && !videoState.isRecordingOrPaused();
                                exposureAidsAnalyzer.analyzeBitmap(sampleRef, sampleW, sampleH,
                                        wantHist, wantZebra, wantPeak, wantDirty, wantPalm, want180Luma,
                                        palmAllowed, SystemClock.elapsedRealtime(), exposureAidsCallback);
                                if (wantHist || wantZebra || wantPeak) {
                                    grid.postInvalidate();
                                }
                            } catch (Throwable t) {
                                Log.w(TAG, "Failed to analyze exposure aids", t);
                            } finally {
                                if (!sampleRef.isRecycled()) sampleRef.recycle();
                                exposureAidBusy.set(false);
                            }
                        });
                    } else {
                        exposureAidBusy.set(false);
                    }
                }
                long nextDelay = (wantHist || wantZebra || wantPeak || wantPalm) ? 120L
                        : want180Luma ? 320L : 2000L;
                tv.postDelayed(this, nextDelay);
                return;
            }
            if (tv != null) tv.postDelayed(this, 450L);
        }
    };

    private final Runnable hideZoomSlider = () -> {
        if (zoomSliderPanel != null && !gesture.is(InteractionState.Gesture.Mode.PINCH) && !gesture.is(InteractionState.Gesture.Mode.ZOOM_ROW) && !gesture.is(InteractionState.Gesture.Mode.ZOOM_SLIDER)) {
            zoomSliderPanel.setVisibility(View.GONE);
        }
    };

    private final Runnable videoTimerTick = new Runnable() {
        @Override public void run() {
            boolean recording = videoState.isRecordingOrPaused();
            boolean switching = videoState.isSwitchingCamera();
            boolean paused = videoState.isPaused();
            if ((!recording && !switching) || outputModeText == null) return;
            long activeMs = (recording && !paused && !switching)
                    ? Math.max(0L, SystemClock.elapsedRealtime() - videoStartedAt) : 0L;
            long seconds = (accumulatedVideoDurationMs + activeMs) / 1000L;
            String time = String.format(Locale.US, "%02d:%02d", seconds / 60L, seconds % 60L);
            String statePrefix = paused ? "❚❚ PAUSED  " : "● REC  ";
            int segCount = videoSegments.size() + 1;
            String segInfo = segCount > 1 ? (" · SEG " + segCount) : "";
            outputModeText.setText(statePrefix + time + segInfo + "  ·  " + videoProfileLabel());
            cameraState.setText((paused ? "VIDEO PAUSED  ·  " : "VIDEO RECORDING  ·  ") + time + segInfo);
            outputModeText.postDelayed(this, 250);
        }
    };

    private final Runnable unlockHoldTick = new Runnable() {
        @Override public void run() {
            if (!videoLock.locked() || !videoLock.holding() || videoLockButton == null) return;
            long remaining = 3000L - (3000 - videoLock.remaining(SystemClock.elapsedRealtime()));
            if (remaining <= 0L) {
                videoLock.cancelHold();
                setVideoScreenLocked(false);
                return;
            }
            videoLockButton.setText(String.format(Locale.US, "HOLD %.1f SEC", remaining / 1000f));
            videoLockButton.postDelayed(this, 90L);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        AppUpdateManager.ensureLifecycleTracking(this);
        volumeQuickTakeRunnable = () -> {
            if (!volumeKeyHolding || cameraDevice == null || !cameraLifecycleState.isStarted()) return;
            if (!videoState.tryBeginQuickTake(VideoStateMachine.Trigger.QUICK_TAKE_VOLUME)) return;
            if (AppSettings.getBoolean(MainActivity.this, AppSettings.HAPTICS, true) && captureButton != null) {
                captureButton.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            }
            startVideoRecording();
        };
        AppSettings.migrateBranding(this);
        refreshHotPrefs();
        AppSettings.prefs(this).registerOnSharedPreferenceChangeListener(hotPrefsListener);
        ioExecutor.execute(() -> LutManager.init(getApplicationContext()));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    () -> {
                        if (videoLock.locked()) {
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
        orientationEventListener = new android.view.OrientationEventListener(this) {
            @Override
            public void onOrientationChanged(int orientation) {
                if (orientation == ORIENTATION_UNKNOWN) return;
                quantizedDeviceRotation = CaptureOrientation.quantizeClockwiseDeviceRotation(
                        orientation, quantizedDeviceRotation);
            }
        };
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
        activityResumed = true;
        video180FallbackNotified = false;
        cameraOpenRetryCount = 0;
        registerCameraAvailabilityCallback();
        refreshHotPrefs();
        applyFullScreenImmersive();
        if (root != null) {
            root.postDelayed(this::applyFullScreenImmersive, 150L);
        }
        if (sensorManager != null && orientationSensor != null) {
            sensorManager.registerListener(sensorEventListener, orientationSensor, SensorManager.SENSOR_DELAY_UI);
        }
        quantizedDeviceRotation = CaptureOrientation.displayRotationToClockwiseDevice(
                getWindowManager().getDefaultDisplay().getRotation());
        if (orientationEventListener != null && orientationEventListener.canDetectOrientation()) {
            orientationEventListener.enable();
        }
        updateBrainButton();
        updateEasyButton();
        updateQuickButtons();
        updateManualFocusUi();
        updateLoupeButtonUi();
        if (quickAdjustPanel != null && quickAdjustPanel.getVisibility() == View.VISIBLE) {
            populateQuickControls();
        }
        updateReadouts();
        updateGalleryButton();
        updateGalleryProcessingUi();
        String currentLeftHand = AppSettings.get(this, AppSettings.LEFT_HAND_MODE, AppSettings.LEFT_HAND_OFF);
        if (overlay != null && !currentLeftHand.equals(appliedLeftHandMode)) {
            rebuildCameraUiLayout();
        }
        if (gridOverlay != null) gridOverlay.invalidate();
        if (textureView != null) {
            textureView.removeCallbacks(exposureAidsSampler);
            textureView.postDelayed(exposureAidsSampler, 200L);
            if (textureView.isAvailable()) {
                openCameraIfReady();
                applyLiveFilmPreview();
                if(videoState.isPermissionReady() && cameraDevice!=null && cameraLifecycleState.isStarted())
                    beginVideoRecording(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED);
            }
        }
        AppUpdateManager.onResume(this);
        checkAutomaticUpdates();
        offerVideoRecovery();
    }

    private void checkAutomaticUpdates() {
        if (pendingAutoUpdateInfo != null) {
            AppUpdateManager.UpdateInfo pending = pendingAutoUpdateInfo;
            pendingAutoUpdateInfo = null;
            if (AppUpdateManager.isNewerVersion(pending.tagName,
                    AppUpdateManager.getAppVersionName(this),
                    AppUpdateManager.getAppVersionCode(this))
                    && AppUpdateManager.canShowAutoUpdateDialog(pending)) {
                AppUpdateManager.showUpdateDialog(MainActivity.this, pending);
                return;
            }
        }

        final AppUpdateManager.AutoCheckAttempt attempt =
                AppUpdateManager.beginAutoCheckIfDue(this, System.currentTimeMillis());
        if (attempt == null) return;

        AppUpdateManager.checkForUpdate(this, false, new AppUpdateManager.UpdateCallback() {
            @Override
            public void onUpdateAvailable(AppUpdateManager.UpdateInfo updateInfo) {
                if (!AppUpdateManager.canShowAutoUpdateDialog(updateInfo)) return;
                if (activityResumed && !isFinishing() && !isDestroyed()) {
                    pendingAutoUpdateInfo = null;
                    AppUpdateManager.showUpdateDialog(MainActivity.this, updateInfo);
                } else if (!isFinishing() && !isDestroyed()) {
                    pendingAutoUpdateInfo = updateInfo;
                }
            }

            @Override
            public void onNoUpdateAvailable() {
                pendingAutoUpdateInfo = null;
            }

            @Override
            public void onError(String message) {
                AppUpdateManager.markAutoCheckFailed(attempt.fromBackgroundReturn);
                Log.d(TAG, "Background update check notice: " + message);
            }
        });
    }

    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_UI_HIDDEN) {
            AppUpdateManager.markAppBackgrounded();
        }
    }

    @Override protected void onPause() {
        activityResumed = false;
        cameraUiHandler.removeCallbacks(cameraReopenRetryRunnable);
        unregisterCameraAvailabilityCallback();
        AppUpdateManager.onPause(this);
        if (videoRecoveryDialog != null && videoRecoveryDialog.isShowing()) {
            videoRecoveryDialog.dismiss();
            videoRecoveryDialog = null;
        }
        gesture.cancel();motion.reset();exposureAidsAnalyzer.resetDetectors();
        videoLock.cancelHold();
        if(videoState.isQuickTake()) videoState.cancelPermissionPending();
        volumeKeyHandler.removeCallbacks(volumeQuickTakeRunnable);
        volumeKeyHolding = false;
        focusHandler.removeCallbacksAndMessages(null);
        stopLoupeRefresh();
        mfDragLoupeActive = false;
        manualLoupeActive = false;
        updateLoupeButtonUi();
        if (focusMode != FocusMode.LOCKED
                && !isManualFocusActive()
                && (focusMode != FocusMode.CONTINUOUS || currentAfRegions != null || currentAeRegions != null)) {
            if (focusReticle != null) focusReticle.fadeOut();
            resetFocusToPreferredAfMode();
        } else if (focusReticle != null && (focusReticle.isVisible || focusReticle.getVisibility() == View.VISIBLE)) {
            focusReticle.fadeOut();
        }
        timerHandler.removeCallbacksAndMessages(null);
        liveLookPreview.clear();
        if (sensorManager != null) {
            sensorManager.unregisterListener(sensorEventListener);
        }
        if (orientationEventListener != null) {
            orientationEventListener.disable();
        }
        hasOrientationSample = false;
        if (textureView != null) {
            textureView.removeCallbacks(exposureAidsSampler);
        }
        closeCamera();
        super.onPause();
    }

    @Override protected void onStop() {
        videoState.cancelPermissionPending();
        super.onStop();
    }

    @Override protected void onDestroy() {
        cameraUiHandler.removeCallbacksAndMessages(null);
        unregisterCameraAvailabilityCallback();
        liveLookPreview.close();
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
        AppSettings.prefs(this).unregisterOnSharedPreferenceChangeListener(hotPrefsListener);
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (videoLock.locked()) {
            if (videoLockButton != null) videoLockButton.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
            return;
        }
        super.onBackPressed();
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (videoLock.locked()) {
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
                    if (videoMode || videoState.isRecordingOrPaused()) {
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
            if (videoLock.locked()) {
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
                    if (videoState.isTriggeredBy(VideoStateMachine.Trigger.QUICK_TAKE_VOLUME)) {
                        videoState.clearQuickTakeTrigger(VideoStateMachine.Trigger.QUICK_TAKE_VOLUME);
                        if (AppSettings.getBoolean(this, AppSettings.HAPTICS, true) && captureButton != null) {
                            captureButton.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                        }
                        stopVideoRecording(true);
                    } else if (!videoMode && !videoState.isRecordingOrPaused() && !videoState.isQuickTake()) {
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
                if (videoLock.locked() || videoState.isPreparing()) return false;
                gesture.begin(InteractionState.Gesture.Mode.PINCH);
                showZoomSlider();
                return true;
            }
            @Override public boolean onScale(ScaleGestureDetector detector) {
                setInteractiveZoom(requestedZoom * detector.getScaleFactor());
                return true;
            }
            @Override public void onScaleEnd(ScaleGestureDetector detector) {
                gesture.end(InteractionState.Gesture.Mode.PINCH);
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
        headerLeftGroup = row();
        headerLeftGroup.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        headerRightGroup = row();
        headerRightGroup.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);

        flashButton = pill("OFF", false);
        flashButton.setMinimumWidth(dp(54));
        flashButton.setPadding(dp(8), 0, dp(9), 0);
        setPillIcon(flashButton, R.drawable.ic_flash, 16, 0xffe9ede6, 4, "OFF");
        flashButton.setOnClickListener(v -> { hapticClick(v); cycleFlash(); });

        aspectButton = pill("4:3", false);
        aspectButton.setPadding(dp(8), 0, dp(8), 0);
        aspectButton.setOnClickListener(v -> { hapticClick(v); chooseAspectRatio(); });

        timerButton = pill("", false);
        timerButton.setMinimumWidth(dp(36));
        timerButton.setPadding(dp(8), 0, dp(8), 0);
        setPillIcon(timerButton, R.drawable.ic_timer, 19, 0xffe9ede6, 4, "");
        timerButton.setOnClickListener(v -> { hapticClick(v); cycleTimer(); });

        focusToggleButton = pill("AF", false);
        focusToggleButton.setMinimumWidth(dp(40));
        focusToggleButton.setPadding(dp(8), 0, dp(8), 0);
        focusToggleButton.setContentDescription("Focus mode: Continuous AF, Single AF, or Manual Focus. Tap to choose; long press to toggle MF.");
        focusToggleButton.setOnClickListener(v -> { hapticClick(v); chooseFocusMode(); });
        focusToggleButton.setOnLongClickListener(v -> { hapticClick(v); toggleQuickManualFocus(); return true; });

        loupeToggleButton = pill("5×", false);
        loupeToggleButton.setMinimumWidth(dp(50));
        loupeToggleButton.setPadding(dp(8), 0, dp(8), 0);
        setPillIcon(loupeToggleButton, R.drawable.ic_focus_loupe, 15, 0xffe9ede6, 4, "5×");
        loupeToggleButton.setContentDescription("Toggle 5× focus loupe");
        loupeToggleButton.setOnClickListener(v -> { hapticClick(v); setManualLoupeActive(!manualLoupeActive); });

        videoFpsButton = pill("24 FPS", true);
        videoFpsButton.setPadding(dp(7), 0, dp(7), 0);
        videoFpsButton.setVisibility(View.GONE);
        videoFpsButton.setOnClickListener(v -> { hapticClick(v); cycleVideoFps(); });

        videoAspectButton = pill("16:9", false);
        videoAspectButton.setPadding(dp(7), 0, dp(7), 0);
        videoAspectButton.setVisibility(View.GONE);
        videoAspectButton.setOnClickListener(v -> { hapticClick(v); cycleVideoAspect(); });

        videoStabButton = pill("OIS+EIS", false);
        videoStabButton.setPadding(dp(7), 0, dp(7), 0);
        videoStabButton.setVisibility(View.GONE);
        videoStabButton.setOnClickListener(v -> { hapticClick(v); cycleVideoStabilization(); });

        for (TextView hdrBtn : new TextView[]{
                flashButton, aspectButton, timerButton,
                focusToggleButton, loupeToggleButton,
                videoFpsButton, videoAspectButton, videoStabButton}) {
            hdrBtn.setSingleLine(true);
            hdrBtn.setEllipsize(android.text.TextUtils.TruncateAt.END);
        }

        topSpacer = new View(this);
        layoutHeaderButtons(false, WindowMetricsHelper.computeHeaderCutoutZones(0, 0, 0, 0, 0, null, 0, 0));
        brainButton = pill("FILM  ⌄", true);
        brainButton.setOnClickListener(v -> { hapticClick(v); chooseBrain(); });

        settingsButton = new ImageView(this);
        settingsButton.setImageResource(R.drawable.ic_settings);
        settingsButton.setColorFilter(0xffe9ede6);
        settingsButton.setScaleType(ImageView.ScaleType.FIT_CENTER);
        settingsButton.setPadding(dp(9), dp(9), dp(9), dp(9));
        settingsButton.setBackground(round(0xd8191e1e, 20, 0x556e7872, dp(1)));
        settingsButton.setContentDescription("Open camera settings");
        settingsButton.setClickable(true);
        settingsButton.setFocusable(true);
        settingsButton.setOnClickListener(v -> { hapticClick(v); startActivity(new Intent(this, SettingsActivity.class)); });

        viewfinderSpacer = new LinearLayout(this);

        GestureDetector tapDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onSingleTapConfirmed(MotionEvent e) {
                if (videoLock.locked()) return false;
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
                if (videoLock.locked() || videoState.isPreparing()) return false;
                handleViewfinderDoubleTap();
                return true;
            }
            @Override public void onLongPress(MotionEvent e) {
                if (videoLock.locked() || gesture.is(InteractionState.Gesture.Mode.EV) || gesture.is(InteractionState.Gesture.Mode.SHADOWS)) return;
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
                        if(curWinX < rx-dp(12) && curWinX > rx-dp(78)) gesture.begin(InteractionState.Gesture.Mode.SHADOWS);
                        else if(Math.abs(curWinX-(rx+dp(28)))<dp(55) || Math.abs(curWinX-rx)<dp(55)) gesture.begin(InteractionState.Gesture.Mode.EV);
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (gesture.is(InteractionState.Gesture.Mode.SHADOWS)) {
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
                        } else if (gesture.is(InteractionState.Gesture.Mode.EV)) {
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
                        gesture.end(InteractionState.Gesture.Mode.EV);
                        gesture.end(InteractionState.Gesture.Mode.SHADOWS);
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
                gesture.begin(InteractionState.Gesture.Mode.ZOOM_SLIDER);
                showZoomSlider();
            }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                gesture.end(InteractionState.Gesture.Mode.ZOOM_SLIDER);
                finishInteractiveZoom();
                scheduleZoomSliderHide();
            }
        });

        manualFocusPanel = row();
        manualFocusPanel.setGravity(Gravity.CENTER_VERTICAL);
        manualFocusPanel.setPadding(dp(10), dp(2), dp(8), dp(2));
        manualFocusPanel.setBackground(round(0xd8191e1e, 13, 0x66454d49, dp(1)));
        manualFocusValue = label("∞", 9, ACCENT, true);
        manualFocusValue.setGravity(Gravity.CENTER);
        manualFocusPanel.addView(manualFocusValue, new LinearLayout.LayoutParams(dp(50), dp(30)));
        TextView nearCap = label("NEAR", 7.5f, MUTED, true);
        nearCap.setPadding(dp(2), 0, dp(4), 0);
        manualFocusPanel.addView(nearCap, new LinearLayout.LayoutParams(-2, -2));
        manualFocusSeekBar = new SeekBar(this);
        manualFocusSeekBar.setMax(ManualFocusHelper.SLIDER_MAX);
        manualFocusSeekBar.setProgress(ManualFocusHelper.SLIDER_MAX);
        manualFocusSeekBar.setContentDescription("Manual focus distance from near to infinity");
        manualFocusSeekBar.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        manualFocusSeekBar.setThumbTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        manualFocusPanel.addView(manualFocusSeekBar, new LinearLayout.LayoutParams(0, dp(32), 1f));
        TextView infCap = label("∞", 11, MUTED, true);
        infCap.setPadding(dp(4), 0, dp(6), 0);
        manualFocusPanel.addView(infCap, new LinearLayout.LayoutParams(-2, -2));
        manualFocusExitButton = pill("AF", false);
        manualFocusExitButton.setMinimumWidth(dp(38));
        manualFocusExitButton.setPadding(dp(8), 0, dp(8), 0);
        manualFocusExitButton.setTextSize(10f);
        manualFocusExitButton.setContentDescription("Return to autofocus");
        manualFocusExitButton.setOnClickListener(v -> { hapticClick(v); exitManualFocusToAf(); });
        manualFocusPanel.addView(manualFocusExitButton, new LinearLayout.LayoutParams(-2, dp(26)));
        manualFocusPanel.setVisibility(View.GONE);
        manualFocusSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser || manualFocusSliderSyncing) return;
                AppSettings.prefs(MainActivity.this).edit().putInt(AppSettings.MANUAL_FOCUS_DISTANCE, progress).apply();
                float diopters = ManualFocusHelper.progressToDiopters(progress, minFocusDiopters);
                if (manualFocusValue != null) {
                    manualFocusValue.setText(ManualFocusHelper.formatFocusDistance(diopters, minFocusDiopters, focusDistanceCalibration));
                }
                applyManualFocusToPreview(diopters, false);
                if ((mfDragLoupeActive || manualLoupeActive) && focusReticle != null) {
                    focusReticle.invalidate();
                    refreshActiveLoupeSample();
                }
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {
                focusHandler.removeCallbacks(mfDragLoupeHideRunnable);
                if (prefLoupeOnMfAdjust) {
                    mfDragLoupeActive = true;
                    if (focusReticle != null) {
                        focusReticle.prepareForVisible();
                    }
                    refreshActiveLoupeSample();
                    startLoupeRefresh();
                }
            }

            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                float diopters = ManualFocusHelper.progressToDiopters(seekBar.getProgress(), minFocusDiopters);
                applyManualFocusToPreview(diopters, true);
                if (mfDragLoupeActive) {
                    focusHandler.removeCallbacks(mfDragLoupeHideRunnable);
                    focusHandler.postDelayed(mfDragLoupeHideRunnable, 700L);
                }
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
        modeNote.setSingleLine(true);
        modeNote.setEllipsize(android.text.TextUtils.TruncateAt.END);
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
        galleryContainer = new FrameLayout(this);
        galleryContainer.setBackground(round(0xff202626, 14, 0x777d8580, dp(1)));
        galleryContainer.setForeground(round(0x00000000, 14, 0x777d8580, dp(1)));
        galleryContainer.setClipToOutline(true);

        galleryButton = new ImageView(this);
        galleryButton.setScaleType(ImageView.ScaleType.CENTER_CROP);
        galleryButton.setContentDescription("Open the latest frame. Long press for the library.");
        galleryButton.setImageResource(R.drawable.ic_lika_lens);
        galleryButton.setOnClickListener(v -> { hapticClick(v); openLatestFrame(); });
        galleryButton.setOnLongClickListener(v -> { hapticClick(v); openLibrary(); return true; });
        galleryContainer.addView(galleryButton, new FrameLayout.LayoutParams(-1, -1));

        galleryProcessingScrim = new View(this);
        galleryProcessingScrim.setBackgroundColor(0x880b0d0e);
        galleryProcessingScrim.setVisibility(View.GONE);
        galleryProcessingScrim.setClickable(false);
        galleryProcessingScrim.setFocusable(false);
        galleryContainer.addView(galleryProcessingScrim, new FrameLayout.LayoutParams(-1, -1));

        galleryProcessingSpinner = new android.widget.ProgressBar(this);
        galleryProcessingSpinner.setIndeterminate(true);
        galleryProcessingSpinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        galleryProcessingSpinner.setVisibility(View.GONE);
        galleryProcessingSpinner.setClickable(false);
        galleryProcessingSpinner.setFocusable(false);
        galleryContainer.addView(galleryProcessingSpinner, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));

        captureButton = label("", 12, BG, false);
        captureButton.setGravity(Gravity.CENTER);
        captureButton.setBackground(round(0xfffaf9f5, 100, 0xfffaf9f5, dp(2)));
        captureButton.setClickable(false);
        captureButton.setFocusable(false);

        shutterFrame = new FrameLayout(this);
        shutterFrame.setBackground(round(0x00202020, 100, 0xfffaf9f5, dp(3)));
        shutterFrame.addView(captureButton, new FrameLayout.LayoutParams(dp(80), dp(80), Gravity.CENTER));
        shutterFrame.setClickable(true);
        shutterFrame.setFocusable(true);
        shutterFrame.setOnTouchListener(new ShutterTouchListener());

        ImageView flip = new ImageView(this);
        flip.setImageResource(R.drawable.ic_switch_camera);
        flip.setColorFilter(0xffe9ede6);
        flip.setScaleType(ImageView.ScaleType.FIT_CENTER);
        flip.setPadding(dp(10), dp(10), dp(10), dp(10));
        flip.setBackground(round(0xff202626, 14, 0x777d8580, dp(1)));
        flip.setContentDescription("Switch front and rear camera");
        flip.setClickable(true);
        flip.setFocusable(true);
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
            if (quickAdjustPanel != null) {
                quickAdjustPanel.setLayoutParams(getQuickAdjustLayoutParams());
            }
            if (textureView != null && textureView.isAvailable()) {
                configureTransform(textureView.getWidth(), textureView.getHeight());
            }
            if (gridOverlay != null) gridOverlay.invalidate();
            return insets;
        });
    }

    private View gallerySlotView() {
        return galleryContainer != null ? galleryContainer : galleryButton;
    }

    private void restoreShootRow() {
        if (shootRow == null) return;
        shootRow.removeAllViews();
        Rect safeInsets = WindowMetricsHelper.getSafeInsets(root);
        boolean swapShutterRow = AppSettings.isLeftHandSwapShutterRow(this);
        View gallerySlot = gallerySlotView();
        if (gallerySlot != null) {
            if (gallerySlot.getParent() != null) ((ViewGroup) gallerySlot.getParent()).removeView(gallerySlot);
            FrameLayout.LayoutParams galleryLp = new FrameLayout.LayoutParams(
                    dp(54), dp(54), (swapShutterRow ? Gravity.END : Gravity.START) | Gravity.CENTER_VERTICAL);
            if (swapShutterRow) {
                galleryLp.rightMargin = dp(24) + safeInsets.right;
            } else {
                galleryLp.leftMargin = dp(24) + safeInsets.left;
            }
            shootRow.addView(gallerySlot, galleryLp);
        }
        if (shutterFrame != null) {
            if (shutterFrame.getParent() != null) ((ViewGroup) shutterFrame.getParent()).removeView(shutterFrame);
            if (captureButton != null) {
                captureButton.setLayoutParams(new FrameLayout.LayoutParams(dp(80), dp(80), Gravity.CENTER));
            }
            FrameLayout.LayoutParams shutterLp = new FrameLayout.LayoutParams(dp(96), dp(96), Gravity.CENTER);
            shootRow.addView(shutterFrame, shutterLp);
        }
        if (flipButton != null) {
            if (flipButton.getParent() != null) ((ViewGroup) flipButton.getParent()).removeView(flipButton);
            flipButton.setPadding(dp(10), dp(10), dp(10), dp(10));
            FrameLayout.LayoutParams flipLp = new FrameLayout.LayoutParams(
                    dp(54), dp(54), (swapShutterRow ? Gravity.START : Gravity.END) | Gravity.CENTER_VERTICAL);
            if (swapShutterRow) {
                flipLp.leftMargin = dp(24) + safeInsets.left;
            } else {
                flipLp.rightMargin = dp(24) + safeInsets.right;
            }
            shootRow.addView(flipButton, flipLp);
        }
    }

    private void addHeaderPill(LinearLayout group, View pill, int leftMarginPx) {
        if (group == null || pill == null) return;
        if (pill.getParent() != null) ((ViewGroup) pill.getParent()).removeView(pill);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(32));
        lp.leftMargin = leftMarginPx;
        group.addView(pill, lp);
    }

    private void layoutHeaderButtons(boolean leftHand, WindowMetricsHelper.HeaderCutoutZones zones) {
        if (header == null || headerLeftGroup == null || headerRightGroup == null) return;
        header.removeAllViews();
        headerLeftGroup.removeAllViews();
        headerRightGroup.removeAllViews();
        if (topSpacer != null && topSpacer.getParent() != null) {
            ((ViewGroup) topSpacer.getParent()).removeView(topSpacer);
        }
        final int gap = dp(4);
        boolean splitCentered = zones != null && zones.splitCenteredCutout;

        if (splitCentered) {
            if (!leftHand) {
                addHeaderPill(headerLeftGroup, flashButton, 0);
                addHeaderPill(headerLeftGroup, aspectButton, gap);
                addHeaderPill(headerLeftGroup, timerButton, gap);
                addHeaderPill(headerLeftGroup, videoFpsButton, gap);

                addHeaderPill(headerRightGroup, focusToggleButton, 0);
                addHeaderPill(headerRightGroup, loupeToggleButton, gap);
                addHeaderPill(headerRightGroup, videoAspectButton, 0);
                addHeaderPill(headerRightGroup, videoStabButton, gap);
            } else {
                addHeaderPill(headerLeftGroup, focusToggleButton, 0);
                addHeaderPill(headerLeftGroup, loupeToggleButton, gap);
                addHeaderPill(headerLeftGroup, timerButton, gap);
                addHeaderPill(headerLeftGroup, videoStabButton, 0);
                addHeaderPill(headerLeftGroup, videoAspectButton, gap);

                addHeaderPill(headerRightGroup, aspectButton, 0);
                addHeaderPill(headerRightGroup, videoFpsButton, 0);
                addHeaderPill(headerRightGroup, flashButton, gap);
            }
            header.addView(headerLeftGroup, new LinearLayout.LayoutParams(zones.leftZoneWidth, dp(32)));
            if (topSpacer != null) {
                topSpacer.setMinimumWidth(0);
                header.addView(topSpacer, new LinearLayout.LayoutParams(zones.spacerWidth, dp(32)));
            }
            header.addView(headerRightGroup, new LinearLayout.LayoutParams(zones.rightZoneWidth, dp(32)));
        } else if (!leftHand) {
            addHeaderPill(headerLeftGroup, flashButton, 0);
            addHeaderPill(headerLeftGroup, aspectButton, gap);
            addHeaderPill(headerLeftGroup, timerButton, gap);
            addHeaderPill(headerLeftGroup, videoFpsButton, gap);

            addHeaderPill(headerRightGroup, focusToggleButton, 0);
            addHeaderPill(headerRightGroup, loupeToggleButton, gap);
            addHeaderPill(headerRightGroup, videoAspectButton, 0);
            addHeaderPill(headerRightGroup, videoStabButton, gap);

            header.addView(headerLeftGroup, new LinearLayout.LayoutParams(-2, dp(32)));
            if (topSpacer != null) {
                topSpacer.setMinimumWidth(dp(12));
                header.addView(topSpacer, new LinearLayout.LayoutParams(0, 1, 1));
            }
            header.addView(headerRightGroup, new LinearLayout.LayoutParams(-2, dp(32)));
        } else {
            addHeaderPill(headerRightGroup, videoStabButton, 0);
            addHeaderPill(headerRightGroup, videoAspectButton, gap);
            addHeaderPill(headerRightGroup, videoFpsButton, gap);
            addHeaderPill(headerRightGroup, loupeToggleButton, 0);
            addHeaderPill(headerRightGroup, focusToggleButton, gap);
            addHeaderPill(headerRightGroup, timerButton, gap);
            addHeaderPill(headerRightGroup, aspectButton, gap);
            addHeaderPill(headerRightGroup, flashButton, gap);

            if (topSpacer != null) {
                topSpacer.setMinimumWidth(dp(12));
                header.addView(topSpacer, new LinearLayout.LayoutParams(0, 1, 1));
            }
            header.addView(headerRightGroup, new LinearLayout.LayoutParams(-2, dp(32)));
        }
    }

    private void attachHeaderOverlay(int screenWidth, int topSafe, Rect safeInsets, Rect cutoutRect, boolean leftHand) {
        if (header == null || overlay == null) return;
        if (header.getParent() != null) ((ViewGroup) header.getParent()).removeView(header);
        FrameLayout.LayoutParams headerLp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        headerLp.topMargin = topSafe;
        int leftPad = dp(12) + safeInsets.left;
        int rightPad = dp(12) + Math.max(safeInsets.right, privacyRightInset(topSafe));
        if (cutoutRect != null) {
            float cutoutCenterX = (cutoutRect.left + cutoutRect.right) / 2f;
            if (cutoutCenterX < screenWidth / 3f) {
                leftPad = Math.max(leftPad, cutoutRect.right + dp(8));
            } else if (cutoutCenterX > screenWidth * 2f / 3f) {
                rightPad = Math.max(rightPad, (screenWidth - cutoutRect.left) + dp(8));
            }
        }
        WindowMetricsHelper.HeaderCutoutZones zones = WindowMetricsHelper.computeHeaderCutoutZones(
                screenWidth, leftPad, rightPad, topSafe, dp(32), cutoutRect, dp(6), dp(50));
        layoutHeaderButtons(leftHand, zones);
        header.setPadding(leftPad, 0, rightPad, 0);
        overlay.addView(header, headerLp);
    }

    private void layoutLookRow(boolean leftHand) {
        if (lookRow == null) return;
        lookRow.removeAllViews();
        if (!leftHand) {
            if (brainButton != null) {
                LinearLayout.LayoutParams lookLp = new LinearLayout.LayoutParams(dp(62), dp(32));
                lookLp.rightMargin = dp(8);
                lookRow.addView(brainButton, lookLp);
            }
            if (modeNote != null) {
                modeNote.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
                LinearLayout.LayoutParams noteTextLp = new LinearLayout.LayoutParams(0, -2, 1f);
                noteTextLp.gravity = Gravity.CENTER_VERTICAL;
                lookRow.addView(modeNote, noteTextLp);
            }
            if (easyButton != null) {
                LinearLayout.LayoutParams easyLp = new LinearLayout.LayoutParams(dp(74), dp(32));
                easyLp.gravity = Gravity.CENTER_VERTICAL;
                easyLp.rightMargin = dp(8);
                lookRow.addView(easyButton, easyLp);
            }
            if (adjustButton != null) {
                LinearLayout.LayoutParams adjustLp = new LinearLayout.LayoutParams(dp(74), dp(32));
                adjustLp.gravity = Gravity.CENTER_VERTICAL;
                lookRow.addView(adjustButton, adjustLp);
            }
            if (videoPauseButton != null) {
                LinearLayout.LayoutParams pauseLp = new LinearLayout.LayoutParams(dp(66), dp(32));
                pauseLp.gravity = Gravity.CENTER_VERTICAL;
                pauseLp.rightMargin = dp(4);
                lookRow.addView(videoPauseButton, pauseLp);
            }
            if (videoSnapButton != null) {
                LinearLayout.LayoutParams snapLp = new LinearLayout.LayoutParams(dp(58), dp(32));
                snapLp.gravity = Gravity.CENTER_VERTICAL;
                snapLp.rightMargin = dp(4);
                lookRow.addView(videoSnapButton, snapLp);
            }
            if (recordingLockButton != null) {
                LinearLayout.LayoutParams lockLp = new LinearLayout.LayoutParams(dp(54), dp(32));
                lockLp.gravity = Gravity.CENTER_VERTICAL;
                lookRow.addView(recordingLockButton, lockLp);
            }
        } else {
            if (recordingLockButton != null) {
                LinearLayout.LayoutParams lockLp = new LinearLayout.LayoutParams(dp(54), dp(32));
                lockLp.gravity = Gravity.CENTER_VERTICAL;
                lockLp.rightMargin = dp(4);
                lookRow.addView(recordingLockButton, lockLp);
            }
            if (videoSnapButton != null) {
                LinearLayout.LayoutParams snapLp = new LinearLayout.LayoutParams(dp(58), dp(32));
                snapLp.gravity = Gravity.CENTER_VERTICAL;
                snapLp.rightMargin = dp(4);
                lookRow.addView(videoSnapButton, snapLp);
            }
            if (videoPauseButton != null) {
                LinearLayout.LayoutParams pauseLp = new LinearLayout.LayoutParams(dp(66), dp(32));
                pauseLp.gravity = Gravity.CENTER_VERTICAL;
                pauseLp.rightMargin = dp(4);
                lookRow.addView(videoPauseButton, pauseLp);
            }
            if (adjustButton != null) {
                LinearLayout.LayoutParams adjustLp = new LinearLayout.LayoutParams(dp(74), dp(32));
                adjustLp.gravity = Gravity.CENTER_VERTICAL;
                adjustLp.rightMargin = dp(8);
                lookRow.addView(adjustButton, adjustLp);
            }
            if (easyButton != null) {
                LinearLayout.LayoutParams easyLp = new LinearLayout.LayoutParams(dp(74), dp(32));
                easyLp.gravity = Gravity.CENTER_VERTICAL;
                easyLp.rightMargin = dp(8);
                lookRow.addView(easyButton, easyLp);
            }
            if (modeNote != null) {
                modeNote.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
                LinearLayout.LayoutParams noteTextLp = new LinearLayout.LayoutParams(0, -2, 1f);
                noteTextLp.gravity = Gravity.CENTER_VERTICAL;
                lookRow.addView(modeNote, noteTextLp);
            }
            if (brainButton != null) {
                LinearLayout.LayoutParams lookLp = new LinearLayout.LayoutParams(dp(62), dp(32));
                lookLp.leftMargin = dp(8);
                lookRow.addView(brainButton, lookLp);
            }
        }
    }

    private void assembleCameraLayout(boolean isWide) {
        if (overlay == null) return;
        overlay.removeAllViews();
        String leftHandSetting = AppSettings.get(this, AppSettings.LEFT_HAND_MODE, AppSettings.LEFT_HAND_OFF);
        appliedLeftHandMode = leftHandSetting;
        boolean leftHand = AppSettings.isLeftHandMode(leftHandSetting);
        boolean swapShutterRow = AppSettings.isLeftHandSwapShutterRow(leftHandSetting);
        layoutLookRow(leftHand);

        Rect safeInsets = WindowMetricsHelper.getSafeInsets(root);
        Rect cutoutRect = WindowMetricsHelper.getTopCutoutRect(root);
        int screenWidth = root != null && root.getWidth() > 0 ? root.getWidth() : getResources().getDisplayMetrics().widthPixels;
        int screenHeight = root != null && root.getHeight() > 0 ? root.getHeight() : getResources().getDisplayMetrics().heightPixels;
        int topSafe = computeTopSafe(safeInsets, cutoutRect);

        if (!isWide) {
            overlay.setPadding(0, 0, 0, 0);
            overlay.setBackground(null);

            attachHeaderOverlay(screenWidth, topSafe, safeInsets, cutoutRect, leftHand);

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
            if (manualFocusPanel != null) {
                if (manualFocusPanel.getParent() != null) ((ViewGroup) manualFocusPanel.getParent()).removeView(manualFocusPanel);
                FrameLayout.LayoutParams mfLp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                mfLp.topMargin = (int) (frame.bottom - dp(44) - dp(12) - dp(42));
                mfLp.leftMargin = dp(24);
                mfLp.rightMargin = dp(24);
                overlay.addView(manualFocusPanel, mfLp);
            }
            if (zoomSliderPanel != null) {
                if (zoomSliderPanel.getParent() != null) ((ViewGroup) zoomSliderPanel.getParent()).removeView(zoomSliderPanel);
                FrameLayout.LayoutParams zoomSliderLp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                boolean mfOpen = manualFocusPanel != null && manualFocusPanel.getVisibility() == View.VISIBLE;
                zoomSliderLp.topMargin = (int) (frame.bottom - dp(44) - dp(12) - (mfOpen ? dp(84) : dp(40)));
                zoomSliderLp.leftMargin = dp(24);
                zoomSliderLp.rightMargin = dp(24);
                overlay.addView(zoomSliderPanel, zoomSliderLp);
            }

            LinearLayout bottomDeck = new LinearLayout(this);
            bottomDeck.setOrientation(LinearLayout.VERTICAL);
            bottomDeck.setGravity(Gravity.CENTER_HORIZONTAL);
            bottomDeck.setClipChildren(false);
            bottomDeck.setClipToPadding(false);
            bottomDeck.setPadding(0, 0, 0, Math.max(dp(24), safeInsets.bottom + dp(22)));

            if (lookRow != null) {
                if (lookRow.getParent() != null) ((ViewGroup) lookRow.getParent()).removeView(lookRow);
                lookRow.setClipChildren(false);
                lookRow.setClipToPadding(false);
                lookRow.setPadding(dp(16) + safeInsets.left, 0, dp(16) + safeInsets.right, 0);
                LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(-1, -2);
                noteLp.bottomMargin = dp(16);
                bottomDeck.addView(lookRow, noteLp);
            }

            restoreShootRow();
            if (shootRow != null) {
                if (shootRow.getParent() != null) ((ViewGroup) shootRow.getParent()).removeView(shootRow);
                LinearLayout.LayoutParams shootLp = new LinearLayout.LayoutParams(-1, dp(104));
                shootLp.bottomMargin = dp(18);
                bottomDeck.addView(shootRow, shootLp);
            }

            FrameLayout bottomModeBar = new FrameLayout(this);
            bottomModeBar.setClipChildren(false);
            bottomModeBar.setClipToPadding(false);
            if (settingsButton != null) {
                if (settingsButton.getParent() != null) ((ViewGroup) settingsButton.getParent()).removeView(settingsButton);
                FrameLayout.LayoutParams settingsLp = new FrameLayout.LayoutParams(
                        dp(40), dp(40), (leftHand ? Gravity.END : Gravity.START) | Gravity.CENTER_VERTICAL);
                if (leftHand) {
                    settingsLp.rightMargin = dp(24) + safeInsets.right;
                } else {
                    settingsLp.leftMargin = dp(24) + safeInsets.left;
                }
                bottomModeBar.addView(settingsButton, settingsLp);
            }
            if (modeRow != null) {
                if (modeRow.getParent() != null) ((ViewGroup) modeRow.getParent()).removeView(modeRow);
                FrameLayout.LayoutParams modeRowLp = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER);
                bottomModeBar.addView(modeRow, modeRowLp);
            }
            bottomDeck.addView(bottomModeBar, new LinearLayout.LayoutParams(-1, -2));

            FrameLayout.LayoutParams deckLp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
            overlay.addView(bottomDeck, deckLp);
        } else {
            // Landscape, Tablet, and Foldable Pro Camera Side-Grip layout
            overlay.setPadding(0, 0, 0, 0);
            overlay.setBackground(null);

            WindowMetricsHelper.WindowSizeInfo windowInfo = WindowMetricsHelper.computeWindowSizeInfo(this);
            int gripWidthDp = wideSideGripWidthDp(windowInfo);
            int gripWidthPx = dp(gripWidthDp);
            boolean compactHeight = windowInfo.heightDp < 440;
            int leftCanvasWidthPx = Math.max(dp(200), screenWidth - safeInsets.left - safeInsets.right - gripWidthPx);

            attachHeaderOverlay(screenWidth, topSafe, safeInsets, cutoutRect, leftHand);

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
                int telWidth = Math.min(dp(340), Math.max(dp(200), leftCanvasWidthPx - dp(16)));
                LinearLayout.LayoutParams telLp = new LinearLayout.LayoutParams(telWidth, -2);
                telLp.gravity = Gravity.CENTER_HORIZONTAL;
                leftCanvas.addView(telemetry, telLp);
            }
            if (zoomSliderPanel != null) {
                if (zoomSliderPanel.getParent() != null) ((ViewGroup) zoomSliderPanel.getParent()).removeView(zoomSliderPanel);
                int sliderWidth = Math.min(dp(320), Math.max(dp(180), leftCanvasWidthPx - dp(24)));
                LinearLayout.LayoutParams zoomSliderLp = new LinearLayout.LayoutParams(sliderWidth, -2);
                zoomSliderLp.gravity = Gravity.CENTER_HORIZONTAL;
                zoomSliderLp.topMargin = dp(4);
                leftCanvas.addView(zoomSliderPanel, zoomSliderLp);
            }
            if (manualFocusPanel != null) {
                if (manualFocusPanel.getParent() != null) ((ViewGroup) manualFocusPanel.getParent()).removeView(manualFocusPanel);
                int mfWidth = Math.min(dp(340), Math.max(dp(200), leftCanvasWidthPx - dp(24)));
                LinearLayout.LayoutParams mfLp = new LinearLayout.LayoutParams(mfWidth, -2);
                mfLp.gravity = Gravity.CENTER_HORIZONTAL;
                mfLp.topMargin = dp(4);
                leftCanvas.addView(manualFocusPanel, mfLp);
            }
            if (lookRow != null) {
                if (lookRow.getParent() != null) ((ViewGroup) lookRow.getParent()).removeView(lookRow);
                lookRow.setClipChildren(false);
                lookRow.setClipToPadding(false);
                int startPad = leftHand ? dp(12) : dp(68);
                int endPad = leftHand ? dp(68) : dp(12);
                lookRow.setPadding(startPad, dp(4), endPad, dp(10));
                LinearLayout.LayoutParams lookRowLp = new LinearLayout.LayoutParams(-1, -2);
                lookRowLp.topMargin = dp(4);
                leftCanvas.addView(lookRow, lookRowLp);
            }

            LinearLayout sideGrip = new LinearLayout(this);
            sideGrip.setOrientation(LinearLayout.VERTICAL);
            sideGrip.setGravity(Gravity.CENTER_HORIZONTAL);
            sideGrip.setPadding(dp(8), dp(8), dp(8), dp(10));
            sideGrip.setBackground(round(0xb0121617, 16, 0x55454d49, dp(1)));

            if (modeRow != null) {
                if (modeRow.getParent() != null) ((ViewGroup) modeRow.getParent()).removeView(modeRow);
                sideGrip.addView(modeRow, new LinearLayout.LayoutParams(-2, -2));
            }
            if (lensRow != null) {
                if (lensRow.getParent() != null) ((ViewGroup) lensRow.getParent()).removeView(lensRow);
                LinearLayout.LayoutParams lensRowLp = new LinearLayout.LayoutParams(-2, -2);
                lensRowLp.topMargin = compactHeight ? dp(4) : dp(8);
                sideGrip.addView(lensRow, lensRowLp);
            }

            View upperGripSpacer = new View(this);
            sideGrip.addView(upperGripSpacer, new LinearLayout.LayoutParams(1, 0, 1f));

            if (shutterFrame != null) {
                if (shutterFrame.getParent() != null) ((ViewGroup) shutterFrame.getParent()).removeView(shutterFrame);
                int shutterOuter = compactHeight ? dp(76) : dp(88);
                int shutterInner = compactHeight ? dp(62) : dp(72);
                if (captureButton != null) {
                    captureButton.setLayoutParams(new FrameLayout.LayoutParams(shutterInner, shutterInner, Gravity.CENTER));
                }
                sideGrip.addView(shutterFrame, new LinearLayout.LayoutParams(shutterOuter, shutterOuter));
            }

            View lowerGripSpacer = new View(this);
            sideGrip.addView(lowerGripSpacer, new LinearLayout.LayoutParams(1, 0, 1f));

            int bottomBtnSize = compactHeight ? dp(40) : dp(44);
            int bottomBtnPad = compactHeight ? dp(8) : dp(9);
            LinearLayout gripBottomRow = new LinearLayout(this);
            gripBottomRow.setOrientation(LinearLayout.HORIZONTAL);
            gripBottomRow.setGravity(Gravity.CENTER);
            View gallerySlot = gallerySlotView();
            if (!swapShutterRow) {
                if (gallerySlot != null) {
                    if (gallerySlot.getParent() != null) ((ViewGroup) gallerySlot.getParent()).removeView(gallerySlot);
                    gripBottomRow.addView(gallerySlot, new LinearLayout.LayoutParams(bottomBtnSize, bottomBtnSize));
                }
                if (flipButton != null) {
                    if (flipButton.getParent() != null) ((ViewGroup) flipButton.getParent()).removeView(flipButton);
                    flipButton.setPadding(bottomBtnPad, bottomBtnPad, bottomBtnPad, bottomBtnPad);
                    LinearLayout.LayoutParams flipLp = new LinearLayout.LayoutParams(bottomBtnSize, bottomBtnSize);
                    flipLp.leftMargin = dp(10);
                    gripBottomRow.addView(flipButton, flipLp);
                }
            } else {
                if (flipButton != null) {
                    if (flipButton.getParent() != null) ((ViewGroup) flipButton.getParent()).removeView(flipButton);
                    flipButton.setPadding(bottomBtnPad, bottomBtnPad, bottomBtnPad, bottomBtnPad);
                    gripBottomRow.addView(flipButton, new LinearLayout.LayoutParams(bottomBtnSize, bottomBtnSize));
                }
                if (gallerySlot != null) {
                    if (gallerySlot.getParent() != null) ((ViewGroup) gallerySlot.getParent()).removeView(gallerySlot);
                    LinearLayout.LayoutParams galleryLp = new LinearLayout.LayoutParams(bottomBtnSize, bottomBtnSize);
                    galleryLp.leftMargin = dp(10);
                    gripBottomRow.addView(gallerySlot, galleryLp);
                }
            }
            sideGrip.addView(gripBottomRow, new LinearLayout.LayoutParams(-2, -2));

            if (outputModeText != null) {
                if (outputModeText.getParent() != null) ((ViewGroup) outputModeText.getParent()).removeView(outputModeText);
                LinearLayout.LayoutParams formatLp = new LinearLayout.LayoutParams(-2, -2);
                formatLp.topMargin = compactHeight ? dp(4) : dp(6);
                sideGrip.addView(outputModeText, formatLp);
            }

            if (leftHand) {
                wideBody.addView(sideGrip, new LinearLayout.LayoutParams(gripWidthPx, -1));
                wideBody.addView(leftCanvas, new LinearLayout.LayoutParams(0, -1, 1f));
            } else {
                wideBody.addView(leftCanvas, new LinearLayout.LayoutParams(0, -1, 1f));
                wideBody.addView(sideGrip, new LinearLayout.LayoutParams(gripWidthPx, -1));
            }
            FrameLayout.LayoutParams wideBodyLp = new FrameLayout.LayoutParams(-1, -1);
            wideBodyLp.topMargin = topSafe + dp(38);
            wideBodyLp.leftMargin = safeInsets.left;
            wideBodyLp.rightMargin = safeInsets.right;
            wideBodyLp.bottomMargin = safeInsets.bottom;
            overlay.addView(wideBody, wideBodyLp);

            if (settingsButton != null) {
                if (settingsButton.getParent() != null) ((ViewGroup) settingsButton.getParent()).removeView(settingsButton);
                FrameLayout.LayoutParams settingsLp = new FrameLayout.LayoutParams(
                        dp(40), dp(40), Gravity.BOTTOM | (leftHand ? Gravity.END : Gravity.START));
                if (leftHand) {
                    settingsLp.rightMargin = dp(16) + safeInsets.right;
                } else {
                    settingsLp.leftMargin = dp(16) + safeInsets.left;
                }
                settingsLp.bottomMargin = dp(10) + safeInsets.bottom;
                overlay.addView(settingsButton, settingsLp);
            }
        }
    }

    private int wideSideGripWidthDp(WindowMetricsHelper.WindowSizeInfo windowInfo) {
        if (windowInfo != null && windowInfo.widthDp >= 840 && windowInfo.heightDp >= 520) {
            return 220;
        }
        return 212;
    }

    private FrameLayout.LayoutParams getQuickAdjustLayoutParams() {
        WindowMetricsHelper.WindowSizeInfo windowInfo = WindowMetricsHelper.computeWindowSizeInfo(this);
        boolean isWide = windowInfo.isLandscape || (windowInfo.widthDp >= windowInfo.heightDp);
        if (isWide) {
            boolean leftHand = AppSettings.isLeftHandMode(this);
            Rect safeInsets = WindowMetricsHelper.getSafeInsets(root);
            Rect cutoutRect = WindowMetricsHelper.getTopCutoutRect(root);
            int topSafe = computeTopSafe(safeInsets, cutoutRect);
            int screenWidth = root != null && root.getWidth() > 0 ? root.getWidth() : getResources().getDisplayMetrics().widthPixels;
            int screenHeight = root != null && root.getHeight() > 0 ? root.getHeight() : getResources().getDisplayMetrics().heightPixels;
            int gripWidthPx = dp(wideSideGripWidthDp(windowInfo));
            int leftCanvasWidthPx = Math.max(dp(260), screenWidth - safeInsets.left - safeInsets.right - gripWidthPx);
            int panelW = Math.min(dp(380), Math.max(dp(260), leftCanvasWidthPx - dp(32)));
            int panelH = Math.min(dp(320), Math.max(dp(200), screenHeight - topSafe - dp(110) - safeInsets.bottom));
            FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(
                    panelW, panelH, Gravity.BOTTOM | (leftHand ? Gravity.END : Gravity.START));
            if (leftHand) {
                panelLp.rightMargin = dp(16) + safeInsets.right;
            } else {
                panelLp.leftMargin = dp(16) + safeInsets.left;
            }
            panelLp.bottomMargin = dp(60) + safeInsets.bottom;
            return panelLp;
        } else {
            Rect safeInsets = WindowMetricsHelper.getSafeInsets(root);
            Rect cutoutRect = WindowMetricsHelper.getTopCutoutRect(root);
            int topSafe = computeTopSafe(safeInsets, cutoutRect);
            int screenHeight = root != null && root.getHeight() > 0 ? root.getHeight() : getResources().getDisplayMetrics().heightPixels;
            int bottomClearance = dp(236) + safeInsets.bottom;
            int topClearance = topSafe + dp(56);
            int maxHeight = Math.max(dp(170), screenHeight - bottomClearance - topClearance);
            int panelH = Math.min(dp(340), maxHeight);
            FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(-1, panelH, Gravity.BOTTOM);
            panelLp.leftMargin = dp(8) + safeInsets.left;
            panelLp.rightMargin = dp(8) + safeInsets.right;
            panelLp.bottomMargin = bottomClearance;
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
            if (!videoLock.locked()) return false;
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                videoLock.hold(SystemClock.elapsedRealtime());

                videoLockButton.setText("HOLD 3.0 SEC");
                videoLockButton.removeCallbacks(unlockHoldTick);
                videoLockButton.post(unlockHoldTick);
                return true;
            }
            if (event.getAction() == android.view.MotionEvent.ACTION_UP
                    || event.getAction() == android.view.MotionEvent.ACTION_CANCEL) {
                videoLock.cancelHold();
                videoLockButton.removeCallbacks(unlockHoldTick);
                if (videoLock.locked()) videoLockButton.setText("HOLD 3 SECONDS");
                return true;
            }
            return true;
        });

        videoLockOverlay.addView(content, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        root.addView(videoLockOverlay, new FrameLayout.LayoutParams(-1, -1));
        videoLockOverlay.setVisibility(View.GONE);
    }

    private void setVideoMode(boolean enabled) {
        if (videoState.isBusy() || videoMode == enabled) return;
        videoState.cancelPermissionPending();
        cancelDelayedCapture();
        videoMode = enabled;
        video180Active = false;
        video180Iso = 0;
        video180FallbackNotified = false;
        lastVideo180StepAt = 0L;
        AppSettings.prefs(this).edit().putBoolean(AppSettings.LAST_USED_MODE, enabled).apply();
        if (quickAdjustPanel != null) quickAdjustPanel.setVisibility(View.GONE);
        updateVideoModeUi();
        updateQuickButtons();
        updateReadouts();
        refreshPreviewRequest();
        if (gridOverlay != null) gridOverlay.invalidate();
        if (textureView != null) configureTransform(textureView.getWidth(), textureView.getHeight());
    }

    private void cycleVideoFps() {
        String current = AppSettings.get(this, AppSettings.VIDEO_FPS, "24 fps (Cinematic)");
        int currentIndex = 0;
        for (int i = 0; i < AppSettings.VIDEO_FPS_CHOICES.length; i++) {
            if (VideoRecordingCoordinator.computeTargetFps(AppSettings.VIDEO_FPS_CHOICES[i])
                    == VideoRecordingCoordinator.computeTargetFps(current)) {
                currentIndex = i;
                break;
            }
        }
        String next = null;
        int skipped = 0;
        for (int offset = 1; offset <= AppSettings.VIDEO_FPS_CHOICES.length; offset++) {
            String candidate = AppSettings.VIDEO_FPS_CHOICES[(currentIndex + offset) % AppSettings.VIDEO_FPS_CHOICES.length];
            int candidateFps = VideoRecordingCoordinator.computeTargetFps(candidate);
            if (characteristics == null
                    || resolveVideoFpsForCamera(characteristics, candidateFps, videoSize).exactMatch) {
                next = candidate;
                break;
            }
            skipped++;
        }
        if (next == null) {
            next = AppSettings.VIDEO_FPS_CHOICES[(currentIndex + 1) % AppSettings.VIDEO_FPS_CHOICES.length];
        }
        AppSettings.prefs(this).edit().putString(AppSettings.VIDEO_FPS, next).apply();
        video180Active = false;
        video180Iso = 0;
        video180FallbackNotified = false;
        lastVideo180StepAt = 0L;
        updateQuickButtons();
        updateReadouts();
        updateVideoModeUi();
        refreshPreviewRequest();
        if (skipped > 0) {
            showMessage("Frame rate: " + next + " (unsupported rates skipped on this lens)");
        }
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
                captureSession.setRepeatingRequest(previewRequest.build(), previewMetadataCallback, cameraHandler);
            } catch (Throwable ignored) {
            }
        }
        showMessage("Stabilization: " + next);
    }

    private void updateVideoModeUi() {
        if (photoModeButton == null) return;
        final boolean recording = videoState.isRecordingOrPaused();
        final boolean starting = videoState.isPreparing();
        final boolean switching = videoState.isSwitchingCamera();
        final boolean paused = videoState.isPaused();
        final boolean quickTake = videoState.isQuickTake();

        styleModeButton(photoModeButton, !videoMode);
        styleModeButton(videoModeButton, videoMode);
        if (brainButton != null) brainButton.setVisibility(videoMode ? View.GONE : View.VISIBLE);
        if (aspectButton != null) aspectButton.setVisibility(videoMode ? View.GONE : View.VISIBLE);
        if (timerButton != null) timerButton.setVisibility(videoMode ? View.GONE : View.VISIBLE);
        if (focusToggleButton != null) focusToggleButton.setVisibility(videoMode ? View.GONE : View.VISIBLE);
        if (loupeToggleButton != null) loupeToggleButton.setVisibility(videoMode ? View.GONE : View.VISIBLE);
        if (adjustButton != null) adjustButton.setVisibility(videoMode ? View.GONE : View.VISIBLE);
        if (easyButton != null) easyButton.setVisibility(videoMode ? View.GONE : View.VISIBLE);
        if (videoFpsButton != null) videoFpsButton.setVisibility(videoMode ? View.VISIBLE : View.GONE);
        if (videoAspectButton != null) videoAspectButton.setVisibility(videoMode ? View.VISIBLE : View.GONE);
        if (videoStabButton != null) videoStabButton.setVisibility(videoMode ? View.VISIBLE : View.GONE);
        if (videoMode) {
            mfDragLoupeActive = false;
            if (manualLoupeActive) {
                setManualLoupeActive(false);
            }
        }
        updateManualFocusUi();
        updateContextualStatus();
        boolean activeVideoTake = (recording || switching) && !videoLock.locked();
        if (recordingLockButton != null) recordingLockButton.setVisibility(activeVideoTake ? View.VISIBLE : View.GONE);
        if (videoPauseButton != null) {
            videoPauseButton.setVisibility(activeVideoTake ? View.VISIBLE : View.GONE);
            videoPauseButton.setText(paused ? "▶ RESUME" : "❚❚ PAUSE");
            videoPauseButton.setTextColor(paused ? ACCENT : 0xffe9ede6);
        }
        if (videoSnapButton != null) videoSnapButton.setVisibility(activeVideoTake ? View.VISIBLE : View.GONE);
        if ((recording || switching) && videoLock.locked()) {
            if (videoLockOverlay == null) buildVideoLockOverlay();
            videoLockOverlay.setVisibility(View.VISIBLE);
        } else if (videoLockOverlay != null) {
            videoLockOverlay.setVisibility(View.GONE);
        }

        boolean idleControlsAvailable = !recording && !starting && !switching;
        boolean liveSwitchAvailable = !starting && !switching && !videoLock.locked();
        photoModeButton.setEnabled(idleControlsAvailable);
        videoModeButton.setEnabled(idleControlsAvailable);
        if (flashButton != null) flashButton.setEnabled(idleControlsAvailable);
        if (aspectButton != null) aspectButton.setEnabled(idleControlsAvailable);
        if (timerButton != null) timerButton.setEnabled(idleControlsAvailable);
        if (focusToggleButton != null) focusToggleButton.setEnabled(idleControlsAvailable);
        if (loupeToggleButton != null) loupeToggleButton.setEnabled(idleControlsAvailable);
        if (manualFocusSeekBar != null) manualFocusSeekBar.setEnabled(idleControlsAvailable);
        if (manualFocusExitButton != null) manualFocusExitButton.setEnabled(idleControlsAvailable);
        if (brainButton != null) brainButton.setEnabled(idleControlsAvailable);
        if (flipButton != null) flipButton.setEnabled(liveSwitchAvailable);
        if (settingsButton != null) settingsButton.setEnabled(idleControlsAvailable);
        if (galleryButton != null) galleryButton.setEnabled(idleControlsAvailable);
        if (galleryContainer != null) galleryContainer.setAlpha(idleControlsAvailable ? 1f : 0.45f);
        if (videoFpsButton != null) videoFpsButton.setEnabled(idleControlsAvailable);
        if (videoAspectButton != null) videoAspectButton.setEnabled(idleControlsAvailable);
        if (videoStabButton != null) videoStabButton.setEnabled(idleControlsAvailable);
        for (TextView lens : lensButtons) lens.setEnabled(liveSwitchAvailable);
        boolean zoomAvailable = !starting && !switching && !videoLock.locked();
        if (zoomSlider != null) zoomSlider.setEnabled(zoomAvailable);
        if (!zoomAvailable && zoomSliderPanel != null) {
            zoomSliderPanel.removeCallbacks(hideZoomSlider);
            zoomSliderPanel.setVisibility(View.GONE);
        }
        if (adjustButton != null) adjustButton.setEnabled(idleControlsAvailable);
        if (easyButton != null) easyButton.setEnabled(idleControlsAvailable);
        if (recordingLockButton != null) recordingLockButton.setEnabled(activeVideoTake);
        if (videoPauseButton != null) videoPauseButton.setEnabled(recording && !switching && !videoLock.locked());
        if (videoSnapButton != null) videoSnapButton.setEnabled(recording && !switching && !videoLock.locked());
        if (captureButton != null) captureButton.setEnabled(!starting && !switching && (recording || cameraDevice != null));
        if (shutterFrame != null) shutterFrame.setEnabled(!starting && !switching && (recording || cameraDevice != null));

        if (captureButton != null && shutterFrame != null) {
            boolean isVidActive = videoMode || quickTake;
            boolean recUi = recording || switching || quickTake;
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
            if (switching) {
                cameraState.setText("SWITCHING CAMERA · CONTINUING TAKE…");
            } else if (recording) {
                cameraState.setText(paused ? "VIDEO PAUSED" : "VIDEO RECORDING");
            } else if (starting) {
                cameraState.setText("STARTING VIDEO");
                outputModeText.setText("PREPARING VIDEO RECORDING");
            } else if (videoMode) {
                boolean hdrEnabled = AppSettings.getBoolean(this, AppSettings.VIDEO_HDR, false);
                String codecChoice = AppSettings.get(this, AppSettings.VIDEO_CODEC, "H.264 · Universal");
                String codecTag;
                if (hdrEnabled) {
                    codecTag = videoHdrSupported ? "HEVC · HLG10 HDR" : "HEVC (HLG10 UNAVAILABLE · SDR)";
                } else {
                    codecTag = codecChoice.contains("HEVC") ? "HEVC" : "H.264";
                }
                cameraState.setText(cameraDevice == null ? "WAITING FOR CAMERA" : "VIDEO READY");
                outputModeText.setText(codecTag + " VIDEO  ·  " + videoProfileLabel());
            } else if (useRawForThisCamera && rawSize != null) {
                cameraState.setText("RAW SENSOR READY  ·  " + Math.round(rawSize.getWidth() * rawSize.getHeight() / 1000000f) + " MP");
                outputModeText.setText(stillOutputSummary());
            } else if (jpegReader != null) {
                boolean dngWanted = AppSettings.shouldSaveDng(AppSettings.get(this, AppSettings.PHOTO_OUTPUT, AppSettings.PHOTO_OUTPUT_JPEG_ONLY));
                if (forceJpegForWide && rawSupported) {
                    cameraState.setText("WIDE FOV READY  ·  JPEG INPUT");
                    outputModeText.setText(dngWanted ? "WIDE LENS · DNG UNAVAILABLE HERE (JPEG SAVED)" : "WIDE SENSOR  ·  LIKA LENS RENDER");
                } else {
                    cameraState.setText(dngWanted ? "JPEG FALLBACK  ·  DNG UNAVAILABLE ON THIS LENS" : "RAW NOT AVAILABLE  ·  JPEG LOOK FALLBACK");
                    outputModeText.setText("JPEG FALLBACK  ·  SENSOR RAW NOT AVAILABLE");
                }
            }
        }
        updateTelemetryVisibility();
    }

    private String stillOutputSummary() {
        String output = AppSettings.get(this, AppSettings.PHOTO_OUTPUT, AppSettings.PHOTO_OUTPUT_JPEG_ONLY);
        if (AppSettings.isDngOnly(output)) return "RAW SENSOR  ·  DNG ORIGINAL ONLY";
        if (AppSettings.shouldSaveDng(output)) return "RAW SENSOR  ·  JPEG + DNG ORIGINAL";
        return "RAW SENSOR  ·  PROCESSED JPEG (EDITABLE RAW)";
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
            if (videoState.isRecordingOrPaused() || videoState.isSwitchingCamera()) stopVideoRecording(true);
            else startVideoRecording();
        } else capturePhoto();
    }

    private void setVideoScreenLocked(boolean locked) {
        videoLock.set(locked && (videoState.isRecordingOrPaused() || videoState.isSwitchingCamera()));
        videoLock.cancelHold();
        if (videoLockButton != null) {
            videoLockButton.removeCallbacks(unlockHoldTick);
            videoLockButton.setText("HOLD 3 SECONDS");
        }
        updateVideoModeUi();
    }

    private void toggleVideoPause() {
        if (!videoState.isRecordingOrPaused() || mediaRecorder == null || videoState.isSwitchingCamera()) return;
        try {
            if (!videoState.isPaused()) {
                mediaRecorder.pause();
                videoState.togglePause();
                videoPauseStartedAt = SystemClock.elapsedRealtime();
                accumulatedVideoDurationMs += Math.max(0L, videoPauseStartedAt - videoStartedAt);
                showMessage("Recording paused");
            } else {
                mediaRecorder.resume();
                videoState.togglePause();
                videoStartedAt = SystemClock.elapsedRealtime();
                showMessage("Recording resumed");
            }
            updateVideoModeUi();
        } catch (RuntimeException e) {
            showMessage("Pause is not supported on this encoder.");
        }
    }

    private void captureVideoStillFrame() {
        if (!videoState.isRecordingOrPaused() || textureView == null || !textureView.isAvailable()) return;
        triggerShutterWink();
        playShutterSound(android.media.MediaActionSound.SHUTTER_CLICK);
        final Bitmap rawBitmap = textureView.getBitmap();
        if (rawBitmap == null) {
            showMessage("Could not capture video frame.");
            return;
        }
        final boolean isFront = isFrontCamera();
        final boolean wantMirror = AppSettings.getBoolean(this, AppSettings.MIRROR_FRONT, true);
        final int displayClockwise = CaptureOrientation.displayRotationToClockwiseDevice(
                getWindowManager().getDefaultDisplay().getRotation());
        final int physicalClockwise = quantizedDeviceRotation >= 0 ? quantizedDeviceRotation : displayClockwise;
        final int relativeRotation = ((physicalClockwise - displayClockwise) % 360 + 360) % 360;
        String vAspect = AppSettings.get(this, AppSettings.VIDEO_ASPECT, "16:9 Widescreen");
        String targetAspect = vAspect.contains("2.39") ? "2.39:1" : "16:9";
        final long stillJob = captureJobs.begin();
        updateGalleryProcessingUi();
        final RawDeveloper.Look stillLook = RawDeveloper.Look.fromPreferences(this);
        // TextureView.getBitmap() on front camera is already mirrored in upright coordinates after rotation.
        stillLook.flipHorizontal = isFront && !wantMirror;
        processor.execute(() -> {
            boolean stillSucceeded = false;
            Bitmap frameBmp = rawBitmap;
            try {
                if (relativeRotation != 0) {
                    Matrix m = new Matrix();
                    m.postRotate(relativeRotation);
                    Bitmap rotated = Bitmap.createBitmap(frameBmp, 0, 0, frameBmp.getWidth(), frameBmp.getHeight(), m, true);
                    if (rotated != frameBmp) {
                        frameBmp.recycle();
                        frameBmp = rotated;
                    }
                }
                String savedBorder = stillLook.frameBorder;
                stillLook.frameBorder = "None";
                Bitmap developed = RawDeveloper.developBitmap(frameBmp, stillLook, true);
                if (developed != null) {
                    developed = cropToAspect(developed, targetAspect);
                    if (savedBorder != null && !savedBorder.startsWith("None")) {
                        stillLook.frameBorder = savedBorder;
                        developed = RawDeveloper.applyFrameBorder(developed, stillLook, null);
                    }
                    String stamp = formatCaptureStamp();
                    String prefix = resolvedFilePrefix();
                    String stem = CaptureFileNaming.ensureUniqueStem(
                            CaptureFileNaming.buildVideoStillBaseStem(prefix, stamp),
                            candidate -> CaptureFileNaming.stillStemExists(MainActivity.this, candidate, ""));
                    writeJpeg(developed, stem + ".jpg", null, "");
                    Bitmap quickThumbnail = createGalleryThumbnail(developed);
                    developed.recycle();
                    stillSucceeded = true;
                    runOnUiThread(() -> {
                        showMessage("Video still saved to Photos");
                        applyGalleryThumbnail(quickThumbnail);
                    });
                }
            } catch (Exception e) {
                if (!frameBmp.isRecycled()) frameBmp.recycle();
                runOnUiThread(() -> showMessage("Could not save video still."));
            } finally {
                captureJobs.finish(stillJob, stillSucceeded);
                runOnUiThread(this::updateGalleryProcessingUi);
            }
        });
    }

    private void pauseCurrentSegmentForCameraSwitch() {
        if (!videoState.isRecordingOrPaused() || mediaRecorder == null) return;
        boolean wasPaused = videoState.isPaused();
        if (!videoState.beginCameraSwitch()) return;
        if (!wasPaused) {
            accumulatedVideoDurationMs += Math.max(0L, SystemClock.elapsedRealtime() - videoStartedAt);
        }
        File finishedSegment = videoTempFile;
        videoTempFile = null;
        closeSessionOnly();
        if (videoRotationSurface != null) videoRotationSurface.stopRendering();
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
        releaseMediaRecorder();
        closeSessionOnly();
        releaseRecordingPreviewSurface();
    }

    private void startVideoRecording() {
        if ((!videoMode && !videoState.isQuickTake())
                || videoState.isBusy()
                || videoState.isPermissionPending()
                || videoState.isPermissionReady()) return;
        if (cameraDevice == null || !cameraLifecycleState.isStarted()) {
            videoState.reset();
            showMessage("Camera is warming up.");
            return;
        }
        if (!videoState.isQuickTake()) {
            videoState.beginStandardTrigger();
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
            videoState.markPermissionPending();
            requestPermissions(requestedPermissions.toArray(new String[0]), VIDEO_PERMISSION);
            return;
        }
        beginVideoRecording(wantAudio && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED);
    }

    private void beginVideoRecording(boolean withAudio) {
        if ((!videoMode && !videoState.isQuickTake() && !videoState.isPermissionReady())
                || cameraDevice == null || !videoState.beginPreparing()) {
            return;
        }
        synchronized (videoSegments) {
            for (File f : videoSegments) if (f != null && f.exists()) f.delete();
            videoSegments.clear();
        }
        accumulatedVideoDurationMs = 0L;
        lockedVideoOrientation = jpegOrientation();
        runOnUiThread(this::updateVideoModeUi);
        if (cameraHandler == null) {
            failVideoStart("Camera is not ready for video.");
            return;
        }
        cameraHandler.post(() -> prepareVideoRecording(withAudio));
    }

    private void prepareVideoRecording(boolean withAudio) {
        if ((!videoMode && !videoState.isQuickTake() && !videoState.isPermissionReady())
                || (!videoState.isPreparing() && !videoState.isSwitchingCamera())
                || cameraDevice == null) {
            failVideoStart("Video recording could not start.");
            return;
        }
        boolean isContinuation = videoState.isSwitchingCamera();
        try {
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (videoSize == null || !isContinuation) {
                videoSize = map == null ? null : chooseVideoSize(map.getOutputSizes(MediaRecorder.class));
            }
            if (videoSize == null) throw new IllegalStateException("No video size is supported");

            String audioPref = AppSettings.get(this, AppSettings.VIDEO_AUDIO, "Camcorder · Stereo");
            VideoRecordingCoordinator.AudioConfig audioCfg = VideoRecordingCoordinator.resolveAudioConfig(
                    audioPref, withAudio, unprocessedAudioSupported, audioZoomSupported);
            if (audioCfg.unprocessedFallback && !isContinuation) {
                runOnUiThread(() -> showMessage("Unprocessed mic unsupported · using stereo camcorder audio"));
            }

            String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date());
            videoTempFile = File.createTempFile("LIKA_VIDEO_SEG_" + stamp + "_", ".mp4", VideoSaveJob.recoveryDirectory(getFilesDir()));
            mediaRecorder = new MediaRecorder();
            if (audioCfg.enabled) {
                try {
                    mediaRecorder.setAudioSource(audioCfg.audioSource);
                } catch (RuntimeException fallbackMic) {
                    mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                }
            }
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);

            String codecPref = AppSettings.get(this, AppSettings.VIDEO_CODEC, "H.264 · Universal");
            String fpsChoice = AppSettings.get(this, AppSettings.VIDEO_FPS, "24 fps (Cinematic)");
            int requestedFps = VideoRecordingCoordinator.computeTargetFps(fpsChoice);
            final VideoRecordingCoordinator.FpsRangeDecision fpsDecision = resolveVideoFpsForCamera(
                    characteristics, isContinuation ? activeTakeFps : requestedFps, videoSize);
            if (!isContinuation) {
                activeTakeFps = fpsDecision.effectiveFps;
                if (!fpsDecision.exactMatch) {
                    final int chosenFps = activeTakeFps;
                    runOnUiThread(() -> showMessage(requestedFps + " fps unsupported on this lens · recording at " + chosenFps + " fps"));
                }
            }

            boolean hdrRequested = AppSettings.getBoolean(this, AppSettings.VIDEO_HDR, false);
            int hevcMain10Level = (hdrRequested || (isContinuation && activeTakeUsingHdr10))
                    ? VideoRecordingCoordinator.findSupportedHevcMain10Level() : -1;
            boolean wantHdr10;
            boolean wantHevc;
            if (isContinuation) {
                wantHdr10 = activeTakeUsingHdr10 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        && isHlg10Supported(characteristics) && hevcMain10Level > 0;
                wantHevc = activeTakeUsingHevc || wantHdr10;
            } else {
                wantHdr10 = hdrRequested
                        && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        && isHlg10Supported(characteristics)
                        && hevcMain10Level > 0;
                if (hdrRequested && !wantHdr10) {
                    runOnUiThread(() -> showMessage("10-bit HLG HDR unsupported on this lens · recording SDR"));
                }
                wantHevc = codecPref.contains("HEVC") || wantHdr10;
            }

            boolean usingHevc = false;
            try {
                mediaRecorder.setVideoEncoder(wantHevc ? MediaRecorder.VideoEncoder.HEVC : MediaRecorder.VideoEncoder.H264);
                usingHevc = wantHevc;
            } catch (RuntimeException fallbackH264) {
                if (isContinuation && activeTakeUsingHevc) {
                    throw new IllegalStateException("Target lens cannot continue HEVC segment", fallbackH264);
                }
                mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
                wantHdr10 = false;
            }
            if (wantHdr10 && usingHevc && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && hevcMain10Level > 0) {
                try {
                    mediaRecorder.setVideoEncodingProfileLevel(
                            android.media.MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10,
                            hevcMain10Level);
                } catch (RuntimeException hdrFallback) {
                    if (isContinuation && activeTakeUsingHdr10) {
                        throw hdrFallback;
                    }
                    wantHdr10 = false;
                }
            } else if (!usingHevc) {
                wantHdr10 = false;
            }
            if (!isContinuation) {
                activeTakeUsingHevc = usingHevc;
                activeTakeUsingHdr10 = wantHdr10;
            }

            if (audioCfg.enabled) {
                mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                mediaRecorder.setAudioChannels(audioCfg.channels);
                mediaRecorder.setAudioSamplingRate(48000);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                        && audioCfg.allowAudioZoom
                        && AppSettings.getBoolean(this, AppSettings.AUDIO_ZOOM, true)) {
                    try {
                        float micZoom = Math.max(0f, Math.min(1f, (requestedZoom - 1.0f) / 4.0f));
                        mediaRecorder.setPreferredMicrophoneFieldDimension(micZoom);
                    } catch (Throwable ignored) {
                    }
                }
            }
            mediaRecorder.setVideoSize(videoSize.getWidth(), videoSize.getHeight());
            mediaRecorder.setVideoFrameRate(activeTakeFps);

            String bitrateChoice = AppSettings.get(this, AppSettings.VIDEO_BITRATE, "High · 24 Mbps");
            int bitrate = VideoRecordingCoordinator.computeBitrate(bitrateChoice);
            if (wantHdr10) bitrate = Math.round(bitrate * 1.25f);
            mediaRecorder.setVideoEncodingBitRate(bitrate);
            if (audioCfg.enabled) mediaRecorder.setAudioEncodingBitRate(160_000);
            if (!isContinuation) {
                lockedVideoOrientation = jpegOrientation();
            }
            int currentCamOrient = jpegOrientation();
            int orientHint = lockedVideoOrientation >= 0 ? lockedVideoOrientation : currentCamOrient;
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
            recordingEncoderSurface = mediaRecorder.getSurface();
            int pixelRotation = !isContinuation ? 0
                    : VideoRecordingCoordinator.rotationCorrection(currentCamOrient, orientHint);
            Log.i("LikaLens", "Video orientation camera=" + cameraId + " front=" + isFrontCamera()
                    + " cameraRotation=" + currentCamOrient + " takeRotation=" + orientHint
                    + " pixelRotation=" + pixelRotation + " hdr10=" + wantHdr10 + " fps=" + activeTakeFps);
            if (pixelRotation != 0) {
                final MediaRecorder segmentRecorder = mediaRecorder;
                videoRotationSurface = new VideoRotationSurface(recordingEncoderSurface,
                        videoSize.getWidth(), videoSize.getHeight(), pixelRotation,
                        () -> runOnUiThread(() -> {
                            if (mediaRecorder != segmentRecorder) return;
                            if (videoState.isRecordingOrPaused()) {
                                stopVideoRecording(true);
                                showMessage("Video rotation failed · recorded footage saved");
                            } else failVideoStart("Could not orient this camera for video.");
                        }));
                recordingInputSurface = videoRotationSurface.inputSurface();
            } else recordingInputSurface = recordingEncoderSurface;
            final Surface segmentInput = recordingInputSurface;
            List<Surface> outputs = Arrays.asList(recordingPreviewSurface, segmentInput);
            final long takeTicket = videoState.takeId();
            final long sessionTicket = sessionEpoch.next();
            final CameraDevice owner = cameraDevice;
            CameraCaptureSession.StateCallback sessionCallback = new CameraCaptureSession.StateCallback() {
                @Override public void onConfigured(CameraCaptureSession session) {
                    if (!activityResumed || cameraDevice != owner || !sessionEpoch.owns(sessionTicket) || !videoState.owns(takeTicket)) {
                        session.close(); return;
                    }
                    if (cameraDevice == null || mediaRecorder == null) {
                        session.close();
                        failVideoStart("Video recording could not start.");
                        return;
                    }
                    captureSession = session;
                    try {
                        CaptureRequest.Builder request = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                        request.addTarget(recordingPreviewSurface);
                        request.addTarget(segmentInput);
                        request.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
                        previewRequest = request;
                        if (fpsDecision.hasRange()) {
                            request.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                                    new Range<>(fpsDecision.lower, fpsDecision.upper));
                        }
                        setExposure(request);
                        setFocusMode(request);
                        setFlash(request, true);
                        applyOpticalSettings(request, true);
                        applyZoom(request);
                        session.setRepeatingRequest(request.build(), previewMetadataCallback, cameraHandler);
                        mediaRecorder.start();
                        if (!isContinuation) {
                            playShutterSound(android.media.MediaActionSound.START_VIDEO_RECORDING);
                        }
                        videoState.markRecording();
                        if (videoState.isPaused()) { mediaRecorder.pause(); videoPauseStartedAt = SystemClock.elapsedRealtime(); }
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
                    if (sessionEpoch.owns(sessionTicket) && videoState.owns(takeTicket))
                        failVideoStart("This camera could not configure video recording.");
                }
            };

            boolean configuredHdrSession = false;
            if (wantHdr10 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                try {
                    android.hardware.camera2.params.OutputConfiguration previewOut =
                            new android.hardware.camera2.params.OutputConfiguration(recordingPreviewSurface);
                    android.hardware.camera2.params.OutputConfiguration videoOut =
                            new android.hardware.camera2.params.OutputConfiguration(segmentInput);
                    videoOut.setDynamicRangeProfile(android.hardware.camera2.params.DynamicRangeProfiles.HLG10);
                    android.hardware.camera2.params.DynamicRangeProfiles drProfiles =
                            characteristics != null ? characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES) : null;
                    if (drProfiles != null) {
                        java.util.Set<Long> constraints = drProfiles.getProfileCaptureRequestConstraints(
                                android.hardware.camera2.params.DynamicRangeProfiles.HLG10);
                        if (constraints != null && !constraints.isEmpty()
                                && !constraints.contains(android.hardware.camera2.params.DynamicRangeProfiles.STANDARD)
                                && constraints.contains(android.hardware.camera2.params.DynamicRangeProfiles.HLG10)) {
                            previewOut.setDynamicRangeProfile(android.hardware.camera2.params.DynamicRangeProfiles.HLG10);
                        }
                    }
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
        return isHlg10Supported(characteristics);
    }

    private boolean isHlg10Supported(CameraCharacteristics targetChars) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || targetChars == null) return false;
        try {
            android.hardware.camera2.params.DynamicRangeProfiles profiles =
                    targetChars.get(CameraCharacteristics.REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES);
            if (profiles != null) {
                return profiles.getSupportedProfiles().contains(
                        android.hardware.camera2.params.DynamicRangeProfiles.HLG10);
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private VideoRecordingCoordinator.FpsRangeDecision resolveVideoFpsForCamera(
            CameraCharacteristics targetChars, int requestedFps, Size targetSize) {
        if (targetChars == null) {
            return new VideoRecordingCoordinator.FpsRangeDecision(requestedFps, requestedFps, requestedFps, requestedFps, true);
        }
        Range<Integer>[] ranges = targetChars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
        List<int[]> rangeList = new ArrayList<>();
        if (ranges != null) {
            for (Range<Integer> r : ranges) {
                if (r != null) {
                    rangeList.add(new int[]{r.getLower(), r.getUpper()});
                }
            }
        }
        long minDurationNs = 0L;
        if (targetSize != null) {
            try {
                StreamConfigurationMap map = targetChars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map != null) {
                    minDurationNs = map.getOutputMinFrameDuration(MediaRecorder.class, targetSize);
                }
            } catch (Throwable ignored) {
            }
        }
        return VideoRecordingCoordinator.resolveFpsRange(requestedFps, rangeList, minDurationNs);
    }

    private boolean canContinueVideoOnCamera(String targetId) {
        if (!videoState.isRecordingOrPaused() || cameraManager == null || targetId == null) return true;
        try {
            CameraCharacteristics targetChars = cameraManager.getCameraCharacteristics(targetId);
            StreamConfigurationMap targetMap = targetChars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            boolean sizeSupported = false;
            if (targetMap != null && videoSize != null) {
                Size[] outSizes = targetMap.getOutputSizes(MediaRecorder.class);
                if (outSizes != null) {
                    for (Size s : outSizes) {
                        if (s != null && s.getWidth() == videoSize.getWidth() && s.getHeight() == videoSize.getHeight()) {
                            sizeSupported = true;
                            break;
                        }
                    }
                }
            }
            boolean targetHdr10 = isHlg10Supported(targetChars);
            int targetOrient = jpegOrientationForCharacteristics(targetChars);
            VideoRecordingCoordinator.SwitchCompatibility compat = VideoRecordingCoordinator.checkMidTakeSwitch(
                    sizeSupported, activeTakeUsingHdr10, targetHdr10, lockedVideoOrientation, targetOrient);
            if (!compat.allowed) {
                showMessage(compat.reason);
                return false;
            }
            return true;
        } catch (Throwable t) {
            showMessage("Cannot switch to that camera during active recording.");
            return false;
        }
    }

    private int jpegOrientationForCharacteristics(CameraCharacteristics targetChars) {
        if (targetChars == null) return 90;
        Integer sensorObj = targetChars.get(CameraCharacteristics.SENSOR_ORIENTATION);
        Integer facingObj = targetChars.get(CameraCharacteristics.LENS_FACING);
        int sensorOrientation = sensorObj == null ? 90 : sensorObj;
        boolean front = facingObj != null && facingObj == CameraCharacteristics.LENS_FACING_FRONT;
        int clockwiseDevice = quantizedDeviceRotation >= 0
                ? quantizedDeviceRotation
                : CaptureOrientation.displayRotationToClockwiseDevice(getWindowManager().getDefaultDisplay().getRotation());
        return CaptureOrientation.calculateCaptureOrientation(sensorOrientation, front, clockwiseDevice);
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
        if (!audioPref.startsWith("Muted")) {
            if (audioPref.contains("Speech") || audioPref.startsWith("Voice")) {
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
        videoState.reset();
        videoLock.set(false);
        activeTakeUsingHevc = false;
        activeTakeUsingHdr10 = false;
        activeTakeFps = 24;
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
            if (cameraDevice != null && textureView != null && textureView.isAvailable()) ensurePreviewReaderAndCreateSession();
        });
    }

    private void stopVideoRecording(boolean resumePreview) {
        if (videoState.isPreparing()) {
            failVideoStart("Video start cancelled.");
            return;
        }
        if (!videoState.isRecordingOrPaused() && !videoState.isSwitchingCamera()
                && mediaRecorder == null && videoSegments.isEmpty()) {
            return;
        }
        boolean wasRecording = videoState.isRecordingOrPaused();
        videoState.beginStopping();
        if (outputModeText != null) outputModeText.removeCallbacks(videoTimerTick);
        File recordedFile = videoTempFile;
        closeSessionOnly();
        if (videoRotationSurface != null) videoRotationSurface.stopRendering();
        if (mediaRecorder != null && wasRecording) {
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
        videoState.reset();
        videoLock.set(false);
        videoLock.cancelHold();
        lockedVideoOrientation = -1;
        activeTakeUsingHevc = false;
        activeTakeUsingHdr10 = false;
        activeTakeFps = 24;
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
        if (resumePreview && cameraDevice != null && textureView != null && textureView.isAvailable()) ensurePreviewReaderAndCreateSession();
    }

    private void releaseMediaRecorder() {
        closeSessionOnly();
        if (videoRotationSurface != null) {
            videoRotationSurface.close();
            videoRotationSurface = null;
        }
        VideoRecordingCoordinator.releaseMediaRecorder(mediaRecorder);
        mediaRecorder = null;
        if (recordingEncoderSurface != null) recordingEncoderSurface.release();
        recordingEncoderSurface = null;
        recordingInputSurface = null;
    }

    private void releaseRecordingPreviewSurface() {
        VideoRecordingCoordinator.releaseRecordingPreviewSurface(recordingPreviewSurface);
        recordingPreviewSurface = null;
    }

    private void publishVideoSegments(List<File> segments) {
        final VideoSaveJob job=new VideoSaveJob(segments);
        videoSaveJobs.incrementAndGet();
        processor.execute(() -> {
            File stitched=null;
            try {
                File source=job.segments.get(0);
                if(job.segments.size()>1) {
                    job.stitching();
                    stitched=File.createTempFile("LIKA_STITCHED_", ".mp4",getCacheDir());
                    if(!stitchVideoSegments(job.segments,stitched)) throw new IllegalStateException("Stitching failed; segments preserved");
                    source=stitched;
                }
                job.publishing();
                String videoStem = CaptureFileNaming.ensureUniqueStem(
                        CaptureFileNaming.buildVideoBaseStem(resolvedFilePrefix(), formatCaptureStamp()),
                        candidate -> CaptureFileNaming.videoStemExists(MainActivity.this, candidate));
                String name = videoStem + ".mp4";
                final Uri result;
                try(MediaOutput output=MediaOutput.allocate(this,name,"video/mp4",Environment.DIRECTORY_MOVIES+"/Lika Lens",true)) {
                    try(InputStream in=new FileInputStream(source);OutputStream out=output.open()) {
                        byte[] buffer=new byte[64*1024];int count;
                        while((count=in.read(buffer))!=-1) out.write(buffer,0,count);
                    }
                    output.publish();result=output.result();
                }
                job.saved();
                runOnUiThread(() -> {
                    if(!activityResumed || isFinishing() || isDestroyed()) return;
                    showMessage(job.segments.size()>1?"Stitched video saved. Tap to create GIF.":"Video saved. Tap to create GIF.");
                    if(messageView!=null) messageView.setOnClickListener(v -> {
                        Intent intent=new Intent(this,GifStudioActivity.class);
                        intent.putExtra(GifStudioActivity.EXTRA_VIDEO_URI,result.toString());startActivity(intent);
                    });
                });
            } catch(Exception error) {
                job.failed();Log.e(TAG,"Video save failed; source segments preserved",error);
                runOnUiThread(() -> showVideoRecovery(job.segments));
            } finally {
                if(job.mayDeleteSegments()) for(File segment:job.segments) segment.delete();
                if(stitched!=null) stitched.delete();
                videoSaveJobs.decrementAndGet();
            }
        });
    }

    private void showVideoRecovery(List<File> segments) {
        if (!activityResumed || isFinishing() || isDestroyed()) return;
        new AlertDialog.Builder(this).setTitle("Video not saved")
                .setMessage("Your original recording segments have been preserved. Retry joining and saving them, or keep them for later.")
                .setPositiveButton("Retry", (d,w) -> publishVideoSegments(segments))
                .setNegativeButton("Keep for later", null).show();
    }

    private void offerVideoRecovery() {
        if (videoSaveJobs.get() > 0 || videoState.isBusy()
                || videoState.isPermissionPending() || videoState.isPermissionReady()
                || videoRecoveryDeferred
                || (videoRecoveryDialog != null && videoRecoveryDialog.isShowing())) {
            return;
        }
        try {
            File[] files = VideoSaveJob.recoveryDirectory(getFilesDir()).listFiles((dir, name) -> name.endsWith(".mp4"));
            if (files == null || files.length == 0) return;
            java.util.List<File> validFiles = new java.util.ArrayList<>();
            for (File f : files) {
                if (f != null && f.isFile() && f.length() > 1024) validFiles.add(f);
                else if (f != null && f.isFile()) f.delete();
            }
            if (!validFiles.isEmpty()) {
                validFiles.sort(java.util.Comparator.comparing(File::getName));
                // Each recording's segment names carry a timestamp; offer individual segments on restart
                // rather than stitching files from unrelated recordings into one take.
                AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Preserved video recording")
                        .setMessage("A recording was not fully saved. Save its preserved segments individually?")
                        .setPositiveButton("Save segments", (d, w) -> {
                            for (File f : validFiles) publishVideoSegments(java.util.Collections.singletonList(f));
                        })
                        .setNeutralButton("Discard", (d, w) -> {
                            for (File f : validFiles) f.delete();
                        })
                        .setNegativeButton("Later", (d, w) -> videoRecoveryDeferred = true)
                        .create();
                dialog.setOnCancelListener(d -> videoRecoveryDeferred = true);
                dialog.setOnDismissListener(d -> {
                    if (videoRecoveryDialog == dialog) videoRecoveryDialog = null;
                });
                videoRecoveryDialog = dialog;
                dialog.show();
            }
        } catch (java.io.IOException e) { Log.w(TAG, "Could not check video recovery", e); }
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
        title.setLetterSpacing(.11f);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        heading.addView(title, new LinearLayout.LayoutParams(0, dp(34), 1f));
        TextView customise = pill("CUSTOMISE", false);
        customise.setContentDescription("Customise Adjust panel controls");
        LinearLayout.LayoutParams custLp = new LinearLayout.LayoutParams(-2, dp(30));
        custLp.rightMargin = dp(5);
        heading.addView(customise, custLp);
        customise.setOnClickListener(v -> {
            hapticClick(v);
            AdjustControlsDialog.show(this, () -> {
                populateQuickControls();
                updateContextualStatus();
            });
        });
        TextView reset = pill("RESET", false);
        heading.addView(reset, new LinearLayout.LayoutParams(dp(50), dp(30)));
        reset.setOnClickListener(v -> resetQuickLook());
        TextView done = pill("DONE", true);
        LinearLayout.LayoutParams doneLp = new LinearLayout.LayoutParams(dp(50), dp(30));
        doneLp.leftMargin = dp(5);
        heading.addView(done, doneLp);
        done.setOnClickListener(v -> closeQuickAdjustPanel());
        sheet.addView(heading);
        TextView note = label("FOR THE NEXT PHOTO  ·  NON-DESTRUCTIVE OVERRIDES", 7, 0xff9aa49e, false);
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
        for (String key : AppSettings.getAdjustControls(this)) {
            if (AppSettings.EXPOSURE.equals(key)) {
                addQuickSlider("Exposure trim", AppSettings.EXPOSURE, -20, 20, AppSettings.DEFAULT_EXPOSURE);
            } else if (AppSettings.WARMTH.equals(key)) {
                addQuickSlider("Warmth shift", AppSettings.WARMTH, 0, 100, AppSettings.DEFAULT_WARMTH);
            } else if (AppSettings.HIGHLIGHTS.equals(key)) {
                addQuickSlider("Highlights", AppSettings.HIGHLIGHTS, 0, 100, AppSettings.DEFAULT_HIGHLIGHTS);
            } else if (AppSettings.SHADOWS.equals(key)) {
                addQuickSlider("Shadows", AppSettings.SHADOWS, 0, 100, AppSettings.DEFAULT_SHADOWS);
            } else if (AppSettings.CONTRAST.equals(key)) {
                addQuickSlider("Contrast", AppSettings.CONTRAST, 50, 150, AppSettings.DEFAULT_CONTRAST);
            } else if (AppSettings.SATURATION.equals(key)) {
                addQuickSlider("Colour intensity", AppSettings.SATURATION, 0, 160, AppSettings.DEFAULT_SATURATION);
            } else if (AppSettings.COLOR_ROLLOFF.equals(key)) {
                addQuickSlider("Colour roll-off", AppSettings.COLOR_ROLLOFF, 0, 100, AppSettings.DEFAULT_COLOR_ROLLOFF);
            } else if (AppSettings.GRAIN.equals(key)) {
                addQuickSlider("Film grain", AppSettings.GRAIN, 0, 100, AppSettings.DEFAULT_GRAIN);
            } else if (AppSettings.GRAIN_STRUCTURE.equals(key)) {
                addQuickPills("Grain structure", AppSettings.GRAIN_STRUCTURE,
                        AppSettings.GRAIN_STRUCTURE_CHOICES,
                        new String[]{"35 mm", "16 mm", "8 mm"},
                        AppSettings.GRAIN_STRUCTURE_CHOICES[0]);
            } else if (AppSettings.HALATION.equals(key)) {
                addQuickSlider("Halation", AppSettings.HALATION, 0, 100, AppSettings.DEFAULT_HALATION);
            } else if (AppSettings.SHARPEN.equals(key)) {
                addQuickSlider("Sharpening", AppSettings.SHARPEN, 0, 100, AppSettings.DEFAULT_SHARPEN);
            } else if (AppSettings.PIVOT.equals(key)) {
                addQuickSlider("Contrast pivot", AppSettings.PIVOT, 0, 100, AppSettings.DEFAULT_PIVOT);
            } else if (AppSettings.TONE_CURVE.equals(key)) {
                addQuickPills("Tone curve", AppSettings.TONE_CURVE,
                        AppSettings.TONE_CURVE_CHOICES,
                        new String[]{"Filmic", "Linear", "Log"},
                        "Filmic S-Curve");
            } else if (AppSettings.SHADOW_PROFILE.equals(key)) {
                addQuickPills("Shadow profile", AppSettings.SHADOW_PROFILE,
                        AppSettings.SHADOW_PROFILE_CHOICES,
                        new String[]{"Auto", "Deep", "Neutral", "Lifted"},
                        AppSettings.SHADOW_PROFILE_AUTO);
            } else if (AppSettings.HIGHLIGHT_RECOVERY.equals(key)) {
                addQuickPills("Highlight recovery", AppSettings.HIGHLIGHT_RECOVERY,
                        AppSettings.HIGHLIGHT_RECOVERY_CHOICES,
                        new String[]{"Auto", "Adaptive", "Subtract", "Hard"},
                        AppSettings.HL_REC_AUTO);
            } else if (AppSettings.COLOR_NR.equals(key)) {
                addQuickSlider("Colour noise reduction", AppSettings.COLOR_NR, 0, 100, AppSettings.DEFAULT_COLOR_NR);
            } else if (AppSettings.VIGNETTE.equals(key)) {
                addQuickSlider("Vignette", AppSettings.VIGNETTE, 0, 100, AppSettings.DEFAULT_VIGNETTE);
            } else if (AppSettings.LUT_INTENSITY.equals(key)) {
                addQuickSlider("LUT intensity", AppSettings.LUT_INTENSITY, 0, 100, AppSettings.DEFAULT_LUT_INTENSITY);
            }
        }
    }

    private void addQuickPills(String title, String key, String[] values, String[] shortLabels, String fallback) {
        LinearLayout control = new LinearLayout(this);
        control.setOrientation(LinearLayout.VERTICAL);
        control.setPadding(dp(9), dp(6), dp(9), dp(6));
        control.setBackground(round(0xff171c1d, 10, 0xff293132, dp(1)));
        LinearLayout.LayoutParams controlLp = new LinearLayout.LayoutParams(-1, -2);
        controlLp.bottomMargin = dp(5);
        quickControlsList.addView(control, controlLp);

        String current = AppSettings.get(this, key, fallback);
        LinearLayout labelRow = row();
        labelRow.addView(label(title, 8, 0xffe6eae4, true), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView activeLabel = label(current, 7.5f, ACCENT, true);
        activeLabel.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        labelRow.addView(activeLabel);
        control.addView(labelRow);

        LinearLayout pillsRow = new LinearLayout(this);
        pillsRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams pillsLp = new LinearLayout.LayoutParams(-1, dp(28));
        pillsLp.topMargin = dp(5);
        control.addView(pillsRow, pillsLp);

        TextView[] buttons = new TextView[values.length];
        for (int i = 0; i < values.length; i++) {
            final String option = values[i];
            final String shortLabel = (shortLabels != null && i < shortLabels.length) ? shortLabels[i] : option;
            boolean active = option.equalsIgnoreCase(current);
            TextView btn = pill(shortLabel, active);
            btn.setTextSize(8f);
            btn.setPadding(dp(4), 0, dp(4), 0);
            buttons[i] = btn;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
            if (i > 0) lp.leftMargin = dp(4);
            pillsRow.addView(btn, lp);
            btn.setOnClickListener(v -> {
                hapticClick(v);
                AppSettings.prefs(MainActivity.this).edit().putString(key, option).apply();
                activeLabel.setText(option);
                for (int j = 0; j < values.length; j++) {
                    boolean sel = values[j].equalsIgnoreCase(option);
                    buttons[j].setTextColor(sel ? ACCENT : 0xffe9ede6);
                    buttons[j].setBackground(round(sel ? 0xe21c1d1b : 0xd8191e1e, 22, sel ? ACCENT : 0x556e7872, dp(1)));
                }
                applyLiveFilmPreview();
                updateContextualStatus();
            });
        }
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
                .putString(AppSettings.GRAIN_STRUCTURE, AppSettings.GRAIN_STRUCTURE_CHOICES[0])
                .putString(AppSettings.GRAIN_RESPONSE, "Luminance-weighted")
                .putInt(AppSettings.HALATION, AppSettings.DEFAULT_HALATION)
                .putInt(AppSettings.SHARPEN, AppSettings.DEFAULT_SHARPEN)
                .putInt(AppSettings.COLOR_NR, AppSettings.DEFAULT_COLOR_NR)
                .putInt(AppSettings.VIGNETTE, AppSettings.DEFAULT_VIGNETTE)
                .putInt(AppSettings.LUT_INTENSITY, AppSettings.DEFAULT_LUT_INTENSITY)
                .putString(AppSettings.TONE_CURVE, "Filmic S-Curve")
                .putString(AppSettings.SHADOW_PROFILE, AppSettings.SHADOW_PROFILE_AUTO)
                .putString(AppSettings.HIGHLIGHT_RECOVERY, AppSettings.HL_REC_AUTO)
                .putString(AppSettings.SKIN_PROTECTION_MODE, AppSettings.OVERRIDE_AUTO)
                .putString(AppSettings.SMART_VIBRANCE_MODE, AppSettings.OVERRIDE_AUTO)
                .putInt(AppSettings.WHITE_BALANCE, 0)
                .putString(AppSettings.WHITE_BALANCE_MODE, "Auto · as shot")
                .apply();
        updateReadouts();
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

    private void setPillIcon(TextView view, int drawableRes, int iconDp, int tintColor, int gapDp, String text) {
        if (view == null) return;
        android.graphics.drawable.Drawable d = getDrawable(drawableRes);
        if (d != null) {
            d = d.mutate();
            d.setTint(tintColor);
            int size = dp(iconDp);
            d.setBounds(0, 0, size, size);
        }
        view.setText(text);
        view.setTextColor(tintColor);
        view.setCompoundDrawablePadding(text != null && !text.isEmpty() ? dp(gapDp) : 0);
        view.setCompoundDrawablesRelative(d, null, null, null);
    }

    private void handleViewfinderTap(float x, float y) {
        if (textureView == null || captureSession == null || previewRequest == null || cameraDevice == null) return;
        if (prefHistogram && gridOverlay != null && gridOverlay.isHistogramHit(x, y)) {
            cycleHistogramMode();
            return;
        }
        RectF frame = framingRect(textureView.getWidth(), textureView.getHeight());
        if (!frame.contains(x, y)) return;

        if (prefHaptics) {
            captureButton.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }

        boolean mfActive = isManualFocusActive();
        String metering = AppSettings.get(this, AppSettings.RETICLE_METERING, "AF + AE Metering");
        int[] afModes = characteristics != null ? characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) : null;
        boolean canSingleAf = hasCapability(afModes, CaptureRequest.CONTROL_AF_MODE_AUTO);
        boolean doAf = !mfActive && canSingleAf && !metering.contains("AE Only");
        boolean doAe = !metering.contains("AF Only");

        Rect activeArray = characteristics != null ? characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) : null;
        if (activeArray != null && (doAf || doAe)) {
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
            if (!mfActive) {
                focusMode = doAf ? FocusMode.TARGETED : FocusMode.CONTINUOUS;
            }

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

                if (doAf) {
                    captureSession.capture(previewRequest.build(), null, cameraHandler);
                    previewRequest.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
                }
                captureSession.setRepeatingRequest(previewRequest.build(), previewMetadataCallback, cameraHandler);
            } catch (Exception e) {
                Log.w(TAG, "Failed to apply focus/metering region", e);
            }
        }

        if (focusReticle != null) {
            focusReticle.showAt(x, y, currentEvOffset);
            refreshActiveLoupeSample();
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
            if (!isManualFocusActive()) {
                focusMode = FocusMode.LOCKED;
            }
            focusHandler.removeCallbacks(hideFocusReticleRunnable);
            if (!aeLocked) toggleAeLock();
            showMessage(isManualFocusActive() ? "AE LOCKED (MF HELD)" : "AE / AF LOCKED");
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
            if (focusReticle != null) focusReticle.fadeOutReticle();
            if (focusMode != FocusMode.LOCKED && !isManualFocusActive()) {
                resetFocusToPreferredAfMode();
            }
        }
    };

    private void scheduleFocusReticleHide() {
        focusHandler.removeCallbacks(hideFocusReticleRunnable);
        focusHandler.postDelayed(hideFocusReticleRunnable, 3500);
    }

    private void resetFocusToPreferredAfMode() {
        if (isManualFocusActive()) {
            refreshPreviewRequest();
            return;
        }
        focusMode = FocusMode.CONTINUOUS;
        currentAfRegions = null;
        currentAeRegions = null;
        if (captureSession != null && previewRequest != null && cameraHandler != null) {
            try {
                setFocusMode(previewRequest);
                previewRequest.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
                Integer maxAf = characteristics != null ? characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) : null;
                Integer maxAe = characteristics != null ? characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE) : null;
                if (maxAf != null && maxAf > 0) previewRequest.set(CaptureRequest.CONTROL_AF_REGIONS, null);
                if (maxAe != null && maxAe > 0) previewRequest.set(CaptureRequest.CONTROL_AE_REGIONS, null);
                captureSession.setRepeatingRequest(previewRequest.build(), previewMetadataCallback, cameraHandler);
            } catch (Exception e) {
                Log.w(TAG, "Failed to reset focus mode", e);
            }
        }
    }

    private void setExposureCompensationEv(float targetEv) {
        currentEvOffset = targetEv;
        if (videoMode && video180Active && !aeLocked) {
            lastVideo180StepAt = 0L;
        }
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
            captureSession.setRepeatingRequest(previewRequest.build(), previewMetadataCallback, cameraHandler);
        } catch (Exception e) {
            Log.w(TAG, "Failed to set exposure compensation", e);
        }
    }

    private void toggleAeLock() {
        if (characteristics == null || previewRequest == null || captureSession == null) return;
        aeLocked = !aeLocked;
        if (!aeLocked) {
            lastVideo180StepAt = 0L;
            if (focusMode == FocusMode.LOCKED) resetFocusToPreferredAfMode();
        }
        if (captureButton != null && AppSettings.getBoolean(this, AppSettings.HAPTICS, true)) {
            captureButton.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
        }
        try {
            previewRequest.set(CaptureRequest.CONTROL_AE_LOCK, aeLocked);
            captureSession.setRepeatingRequest(previewRequest.build(), previewMetadataCallback, cameraHandler);
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
        updateReadouts();
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
        return CaptureFileNaming.resolvePrefix(
                AppSettings.get(this, AppSettings.FILE_PREFIX, "LIKA"),
                AppSettings.get(this, AppSettings.CUSTOM_PREFIX_VALUE, "LIKA"));
    }

    private void updateQuickButtons() {
        if (aspectButton != null) aspectButton.setText(AppSettings.get(this, AppSettings.ASPECT_RATIO, "4:3"));
        if (videoFpsButton != null) {
            String fps = AppSettings.get(this, AppSettings.VIDEO_FPS, "24 fps (Cinematic)");
            videoFpsButton.setText(fps.startsWith("24") ? "24 FPS"
                    : fps.startsWith("25") ? "25 FPS"
                    : fps.startsWith("30") ? "30 FPS" : "60 FPS");
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
            String timerLabel = timer.startsWith("3") ? "3s" : timer.startsWith("10") ? "10s" : "";
            int timerColor = timerLabel.isEmpty() ? 0xffe9ede6 : ACCENT;
            setPillIcon(timerButton, R.drawable.ic_timer, 19, timerColor, 4, timerLabel);
            timerButton.setContentDescription("Self timer: " + timer);
        }
        if (flashButton != null) {
            boolean flashAvailable = flashSupported || isFrontCamera();
            String flash = AppSettings.get(this, AppSettings.FLASH, "Off");
            if (!isFrontCamera() && "Max".equalsIgnoreCase(flash)) flash = "Auto";
            String flashLabel = !flashAvailable ? "OFF" : flash.toUpperCase(Locale.US);
            int flashColor = "Off".equalsIgnoreCase(flash) || !flashAvailable ? 0xffe9ede6 : ACCENT;
            setPillIcon(flashButton, R.drawable.ic_flash, 16, flashColor, 4, flashLabel);
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
        String next;
        if (front) {
            next = current.startsWith("Off") ? "Auto"
                    : current.startsWith("Auto") ? "Torch"
                    : current.startsWith("Torch") ? "Max" : "Off";
        } else {
            if ("Max".equalsIgnoreCase(current)) current = "Auto";
            next = current.startsWith("Off") ? "Auto" : current.startsWith("Auto") ? "Torch" : "Off";
        }
        AppSettings.prefs(this).edit().putString(AppSettings.FLASH, next).apply();
        updateQuickButtons();
        updateContextualStatus();
        if (front && "Max".equals(next)) {
            showMessage("MAX SCREEN FLASH · Full white illumination");
        } else if (front && !"Off".equals(next)) {
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

    private void registerCameraAvailabilityCallback() {
        if (availabilityCallbackRegistered) return;
        if (cameraManager == null) cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
        if (cameraManager == null) return;
        try {
            cameraManager.registerAvailabilityCallback(cameraAvailabilityCallback,
                    cameraHandler != null ? cameraHandler : cameraUiHandler);
            availabilityCallbackRegistered = true;
        } catch (Throwable ignored) {
        }
    }

    private void unregisterCameraAvailabilityCallback() {
        if (!availabilityCallbackRegistered || cameraManager == null) return;
        try {
            cameraManager.unregisterAvailabilityCallback(cameraAvailabilityCallback);
        } catch (Throwable ignored) {
        } finally {
            availabilityCallbackRegistered = false;
        }
    }

    private void scheduleCameraReopenRetry() {
        cameraUiHandler.post(() -> {
            if (!activityResumed || isFinishing() || isDestroyed()) return;
            if (!cameraLifecycleState.canOpen()) return;
            if (!CameraLifecycleState.shouldRetryOpen(cameraOpenRetryCount)) return;
            long delayMs = CameraLifecycleState.reopenRetryDelayMs(cameraOpenRetryCount);
            cameraOpenRetryCount++;
            cameraUiHandler.removeCallbacks(cameraReopenRetryRunnable);
            cameraUiHandler.postDelayed(cameraReopenRetryRunnable, delayMs);
        });
    }

    private void attemptCameraReopenRetry() {
        if (!activityResumed || isFinishing() || isDestroyed()) return;
        if (cameraLifecycleState.canOpen() && textureView != null && textureView.isAvailable()) {
            openCameraIfReady();
        }
    }

    private void setCameraStateRetryPrompt(String text, boolean allowTapRetry) {
        cameraRetryPromptActive = allowTapRetry;
        if (cameraState == null) return;
        cameraState.setText(allowTapRetry ? (text + " · TAP TO RETRY") : text);
        if (allowTapRetry) {
            cameraState.setClickable(true);
            cameraState.setVisibility(View.VISIBLE);
            cameraState.setOnClickListener(v -> {
                hapticClick(v);
                setCameraStateRetryPrompt("RECONNECTING CAMERA…", false);
                updateTelemetryVisibility();
                cameraOpenRetryCount = 0;
                openCameraIfReady();
            });
        } else {
            cameraState.setClickable(false);
            cameraState.setOnClickListener(null);
        }
    }

    private void openCameraIfReady() {
        if (!activityResumed || isFinishing() || isDestroyed()
                || !cameraLifecycleState.canOpen() || textureView == null || !textureView.isAvailable()) return;
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return;
        if (cameraManager == null) cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
        setCameraStateRetryPrompt(cameraState != null ? String.valueOf(cameraState.getText()) : "", false);
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
            if (!videoState.isSwitchingCamera() || videoSize == null) {
                videoSize = chooseVideoSize(map.getOutputSizes(MediaRecorder.class));
            }
            int[] capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
            rawSupported = isRawCapable(capabilities, map);
            manualSupported = hasCapability(capabilities, CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR);
            Float minFocusDist = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
            int[] afModes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
            manualFocusSupported = ManualFocusHelper.isManualFocusSupported(minFocusDist, afModes);
            minFocusDiopters = minFocusDist != null && Float.isFinite(minFocusDist) ? Math.max(0f, minFocusDist) : 0f;
            focusDistanceCalibration = characteristics.get(CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION);
            hasUltraWideSupport = computeHasUltraWideSupport(characteristics);
            maxCameraZoom = computeMaxCameraZoom(characteristics);
            audioZoomSupported = VideoRecordingCoordinator.probeAudioZoomSupported(this);
            unprocessedAudioSupported = VideoRecordingCoordinator.probeUnprocessedAudioSupported(this);
            videoHdrSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && isHlg10Supported(characteristics)
                    && VideoRecordingCoordinator.findSupportedHevcMain10Level() > 0;
            video180Active = false;
            video180Iso = 0;
            video180FallbackNotified = false;
            lastMeteredExposureNs = 0L;
            lastMeteredIso = 0;
            lastVideo180StepAt = 0L;
            AppSettings.prefs(this).edit()
                    .putBoolean(AppSettings.MANUAL_SUPPORTED, manualSupported)
                    .putBoolean(AppSettings.RAW_SUPPORTED, rawSupported)
                    .putBoolean(AppSettings.MANUAL_FOCUS_SUPPORTED, manualFocusSupported)
                    .putBoolean(AppSettings.VIDEO_HDR_SUPPORTED, videoHdrSupported)
                    .putBoolean(AppSettings.AUDIO_ZOOM_SUPPORTED, audioZoomSupported)
                    .apply();
            updateReadouts();
            useRawForThisCamera = rawSupported && !forceJpegForWide;
            rawSize = rawSupported ? chooseRawSize(map.getOutputSizes(ImageFormat.RAW_SENSOR)) : null;
            previewSize = choosePreviewSize(map.getOutputSizes(SurfaceTexture.class));
            if (previewSize == null) previewSize = new Size(1280, 720);
            prepareReader(map);
            runOnUiThread(() -> {
                updateManualFocusUi();
                updateLensButtons(requestedZoom);
                if (videoMode) {
                    updateVideoModeUi();
                }
                if (!videoMode && !manualFocusSupported
                        && AppSettings.FOCUS_MANUAL.equals(AppSettings.get(this, AppSettings.FOCUS_MODE, AppSettings.FOCUS_CONTINUOUS))) {
                    showMessage("Fixed-focus lens · manual focus not supported here");
                }
            });
            cameraLifecycleState = CameraLifecycleState.OPENING;
            cameraManager.openCamera(cameraId, cameraStateCallback(cameraEpoch.next()), cameraHandler);
        } catch (Throwable e) {
            Log.e("LikaLens", "Could not open camera", e);
            cameraLifecycleState = CameraLifecycleState.ERROR;
            boolean canRetry = !(e instanceof SecurityException)
                    && activityResumed && !isFinishing() && !isDestroyed()
                    && CameraLifecycleState.shouldRetryOpen(cameraOpenRetryCount);
            runOnUiThread(() -> {
                if (canRetry) {
                    setCameraStateRetryPrompt("RECONNECTING CAMERA…", false);
                    scheduleCameraReopenRetry();
                } else {
                    setCameraStateRetryPrompt("CAMERA UNAVAILABLE", !(e instanceof SecurityException));
                    showMessage("Could not open this camera.");
                }
            });
        }
    }

    private boolean computeHasUltraWideSupport(CameraCharacteristics activeChars) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && activeChars != null) {
            Range<Float> range = activeChars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
            if (range != null && range.getLower() < CameraExposureHelper.WIDE_ZOOM_THRESHOLD) {
                return true;
            }
        }
        if (cameraManager != null) {
            try {
                if (mainCameraId != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    CameraCharacteristics mainChars = cameraManager.getCameraCharacteristics(mainCameraId);
                    Range<Float> mainRange = mainChars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
                    if (mainRange != null && mainRange.getLower() < CameraExposureHelper.WIDE_ZOOM_THRESHOLD) {
                        return true;
                    }
                }
                for (String id : cameraManager.getCameraIdList()) {
                    CameraCharacteristics c = cameraManager.getCameraCharacteristics(id);
                    Integer facing = c.get(CameraCharacteristics.LENS_FACING);
                    if (facing == null || facing != CameraCharacteristics.LENS_FACING_BACK) continue;
                    float[] focals = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                    if (focals != null && focals.length > 0 && primaryFocalLength > 0f
                            && (focals[0] / primaryFocalLength) < 0.95f) {
                        return true;
                    }
                }
            } catch (CameraAccessException ignored) {
            }
        }
        return false;
    }

    private float computeMaxCameraZoom(CameraCharacteristics activeChars) {
        float maxZoom = 1f;
        if (activeChars != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Range<Float> range = activeChars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
                if (range != null) maxZoom = Math.max(maxZoom, range.getUpper());
            }
            Float maxDigital = activeChars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
            if (maxDigital != null && Float.isFinite(maxDigital)) {
                maxZoom = Math.max(maxZoom, maxDigital);
            }
        }
        return Math.min(MAX_INTERACTIVE_ZOOM, maxZoom);
    }

    private static boolean hasCapability(int[] caps, int target) {
        if (caps == null) return false;
        for (int v : caps) {
            if (v == target) return true;
        }
        return false;
    }

    private static boolean isRawCapable(CameraCharacteristics c, StreamConfigurationMap map) {
        if (c == null || map == null) return false;
        return isRawCapable(c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES), map);
    }

    private static boolean isRawCapable(int[] caps, StreamConfigurationMap map) {
        if (map == null || !hasCapability(caps, CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)) {
            return false;
        }
        Size[] rawSizes = map.getOutputSizes(ImageFormat.RAW_SENSOR);
        return rawSizes != null && rawSizes.length > 0;
    }

    private List<CameraExposureHelper.LensCandidate> collectLensCandidates() throws CameraAccessException {
        List<CameraExposureHelper.LensCandidate> candidates = new ArrayList<>();
        if (cameraManager == null) return candidates;
        for (String id : cameraManager.getCameraIdList()) {
            CameraCharacteristics c = cameraManager.getCameraCharacteristics(id);
            Integer facing = c.get(CameraCharacteristics.LENS_FACING);
            boolean isBack = facing != null && facing == CameraCharacteristics.LENS_FACING_BACK;
            StreamConfigurationMap map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) continue;
            boolean raw = isRawCapable(c, map);
            Size[] sizes = raw ? map.getOutputSizes(ImageFormat.RAW_SENSOR) : map.getOutputSizes(ImageFormat.JPEG);
            long area = maxSizeArea(sizes);
            float[] lenses = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
            float focal = lenses != null && lenses.length > 0 ? lenses[0] : 1f;
            Float minZoom = null;
            Float maxZoom = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Range<Float> range = c.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
                if (range != null) {
                    minZoom = range.getLower();
                    maxZoom = range.getUpper();
                }
            }
            candidates.add(new CameraExposureHelper.LensCandidate(id, isBack, raw, area, focal, minZoom, maxZoom));
        }
        return candidates;
    }

    private void chooseMainCamera() throws CameraAccessException {
        String defaultLens = AppSettings.get(this, AppSettings.DEFAULT_LENS, "Auto / Wide 1×");
        String lastId = AppSettings.get(this, AppSettings.LAST_CAMERA_ID, null);
        List<CameraExposureHelper.LensCandidate> candidates = collectLensCandidates();
        CameraExposureHelper.StartupLensChoice choice =
                CameraExposureHelper.resolveStartupLens(candidates, defaultLens, lastId);
        mainCameraId = choice.mainCameraId;
        primaryFocalLength = choice.primaryFocalLength;
        cameraId = choice.activeCameraId;
        requestedZoom = choice.initialZoom;
        physicalFocalFactor = choice.physicalFocalFactor;
        forceJpegForWide = choice.forceJpegForWide;
        runOnUiThread(() -> updateLensButtons(requestedZoom));
    }

    private static long maxSizeArea(Size[] sizes) {
        if (sizes == null || sizes.length == 0) return 0L;
        long max = 0L;
        for (Size s : sizes) {
            long area = (long) s.getWidth() * s.getHeight();
            if (area > max) max = area;
        }
        return max;
    }

    private void prepareReader(StreamConfigurationMap map) {
        closeReader(rawReader); rawReader = null;
        closeReader(jpegReader); jpegReader = null;
        if (useRawForThisCamera && rawSize != null) {
            rawReader = ImageReader.newInstance(rawSize.getWidth(), rawSize.getHeight(), ImageFormat.RAW_SENSOR, 4);
            rawReader.setOnImageAvailableListener(this::onImageAvailable, cameraHandler);
            cameraState.setText("RAW SENSOR READY  ·  " + Math.round(rawSize.getWidth() * rawSize.getHeight() / 1000000f) + " MP");
            outputModeText.setText(stillOutputSummary());
        } else {
            Size jpegSize = chooseJpegSize(map.getOutputSizes(ImageFormat.JPEG));
            if (jpegSize == null) jpegSize = new Size(1920, 1080);
            jpegReader = ImageReader.newInstance(jpegSize.getWidth(), jpegSize.getHeight(), ImageFormat.JPEG, 4);
            jpegReader.setOnImageAvailableListener(this::onImageAvailable, cameraHandler);
            String photoOutput = AppSettings.get(this, AppSettings.PHOTO_OUTPUT, AppSettings.PHOTO_OUTPUT_JPEG_ONLY);
            boolean wantsDng = AppSettings.shouldSaveDng(photoOutput);
            if (forceJpegForWide && rawSupported) {
                cameraState.setText(wantsDng ? "WIDE LENS · JPEG FALLBACK (DNG UNAVAILABLE)" : "WIDE FOV READY · JPEG INPUT");
                outputModeText.setText("WIDE SENSOR · LIKA LENS RENDER");
            } else {
                cameraState.setText(wantsDng ? "JPEG FALLBACK · DNG UNAVAILABLE ON THIS LENS" : "RAW NOT AVAILABLE · JPEG LOOK FALLBACK");
                outputModeText.setText("JPEG FALLBACK · SENSOR RAW NOT AVAILABLE");
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
        return AppSettings.parseAspectRatio(prefAspectRatio);
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

    private final RectF cachedFrameRect = new RectF();
    private float cachedFrameW = -1f;
    private float cachedFrameH = -1f;
    private float cachedFrameRatio = -1f;
    private boolean cachedFrameVideoMode;

    private void invalidateFramingRectCache() {
        cachedFrameW = -1f;
    }

    private RectF framingRect(float width, float height) {
        RectF out = new RectF();
        framingRectInto(width, height, out);
        return out;
    }

    private void framingRectInto(float width, float height, RectF out) {
        if (width <= 0 || height <= 0) {
            out.set(0, 0, 0, 0);
            return;
        }
        float ratio = aspectRatioSetting();
        if (width == cachedFrameW && height == cachedFrameH
                && ratio == cachedFrameRatio && videoMode == cachedFrameVideoMode
                && root != null && root.getRootWindowInsets() != null) {
            out.set(cachedFrameRect);
            return;
        }
        WindowMetricsHelper.WindowSizeInfo windowInfo = WindowMetricsHelper.computeWindowSizeInfo(this);
        boolean isWide = windowInfo.isLandscape || (width >= height);
        if (!isWide) {
            Rect safeInsets = WindowMetricsHelper.getSafeInsets(root);
            Rect cutoutRect = WindowMetricsHelper.getTopCutoutRect(root);
            int topSafe = computeTopSafe(safeInsets, cutoutRect);
            float topZoneBottom = topSafe + dp(32);
            float viewfinderTopMin = topZoneBottom + dp(8);

            float bottomDeckHeight = dp(210) + Math.max(dp(24), safeInsets.bottom + dp(22));
            float viewfinderBottomMax = height - bottomDeckHeight - dp(8);

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
            float top = viewfinderTopMin;
            out.set(left, top, left + frameWidth, top + frameHeight);
        } else {
            Rect safeInsets = WindowMetricsHelper.getSafeInsets(root);
            Rect cutoutRect = WindowMetricsHelper.getTopCutoutRect(root);
            int topSafe = computeTopSafe(safeInsets, cutoutRect);
            boolean leftHand = AppSettings.isLeftHandMode(this);
            float gripWidth = dp(wideSideGripWidthDp(windowInfo));
            float topOffset = topSafe + dp(38);
            float bottomHudReserve = dp(96);
            float availableWidth = Math.max(dp(180), width - gripWidth - dp(24) - safeInsets.right - safeInsets.left);
            float availableHeight = Math.max(dp(160), height - topOffset - bottomHudReserve - safeInsets.bottom);
            float frameWidth = availableWidth;
            float frameHeight = frameWidth / ratio;
            if (frameHeight > availableHeight) {
                frameHeight = availableHeight;
                frameWidth = frameHeight * ratio;
            }
            float canvasLeft = safeInsets.left + (leftHand ? gripWidth : 0f) + dp(12);
            float left = canvasLeft + (availableWidth - frameWidth) * 0.5f;
            float top = topOffset + (availableHeight - frameHeight) * 0.5f;
            out.set(left, top, left + frameWidth, top + frameHeight);
        }
        if (root != null && root.getRootWindowInsets() != null) {
            cachedFrameW = width;
            cachedFrameH = height;
            cachedFrameRatio = ratio;
            cachedFrameVideoMode = videoMode;
            cachedFrameRect.set(out);
        }
    }

    private CameraDevice.StateCallback cameraStateCallback(long ticket) { return new CameraDevice.StateCallback() {
        @Override public void onOpened(CameraDevice camera) {
            if (!activityResumed || !cameraEpoch.owns(ticket)) {
                camera.close();
                if (cameraEpoch.owns(ticket)) {
                    cameraLifecycleState = CameraLifecycleState.CLOSED;
                }
                return;
            }
            cameraDevice = camera;
            cameraLifecycleState = CameraLifecycleState.OPENED;
            if (videoState.isSwitchingCamera()) {
                boolean hasMic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
                prepareVideoRecording(hasMic);
            } else if (videoState.isPermissionReady()) {
                beginVideoRecording(checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED);
            } else createPreviewSession();
            runOnUiThread(MainActivity.this::updateVideoModeUi);
        }
        @Override public void onDisconnected(CameraDevice camera) {
            if (!cameraEpoch.owns(ticket)) { camera.close(); return; }
            if (videoState.isBusy() || mediaRecorder != null) stopVideoRecording(false);
            camera.close();
            if (cameraDevice == camera) cameraDevice = null;
            cameraLifecycleState = CameraLifecycleState.CLOSED;
            runOnUiThread(() -> {
                updateVideoModeUi();
                if (activityResumed && !isFinishing() && !isDestroyed()) {
                    scheduleCameraReopenRetry();
                }
            });
        }
        @Override public void onError(CameraDevice camera, int error) {
            if (!cameraEpoch.owns(ticket)) { camera.close(); return; }
            if (videoState.isBusy() || mediaRecorder != null) stopVideoRecording(false);
            camera.close();
            if (cameraDevice == camera) cameraDevice = null;
            cameraLifecycleState = CameraLifecycleState.ERROR;
            runOnUiThread(() -> {
                boolean canRetry = activityResumed && !isFinishing() && !isDestroyed()
                        && error != CameraDevice.StateCallback.ERROR_CAMERA_DISABLED
                        && CameraLifecycleState.shouldRetryOpen(cameraOpenRetryCount);
                if (canRetry) {
                    setCameraStateRetryPrompt("RECONNECTING CAMERA…", false);
                    scheduleCameraReopenRetry();
                } else {
                    boolean allowTap = error != CameraDevice.StateCallback.ERROR_CAMERA_DISABLED;
                    setCameraStateRetryPrompt("CAMERA ERROR · " + error, allowTap);
                }
                updateVideoModeUi();
            });
        }
        @Override public void onClosed(CameraDevice camera) {
            if (cameraDevice == camera) cameraDevice = null;
            if (cameraEpoch.owns(ticket) && cameraLifecycleState == CameraLifecycleState.CLOSING) {
                cameraLifecycleState = CameraLifecycleState.CLOSED;
                if (activityResumed && !isFinishing() && !isDestroyed()) {
                    cameraUiHandler.post(MainActivity.this::openCameraIfReady);
                }
            }
        }
    }; }

    private void createPreviewSession() {
        createPreviewSession(false);
    }

    private void createPreviewSession(boolean seamless) {
        if (!seamless) {
            previewColourMetadata = null;
        }
        lastPreviewMetadataAt = 0L;
        if (cameraDevice == null || textureView == null || !textureView.isAvailable()) return;
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            if (texture == null) {
                if (cameraDevice != null) cameraLifecycleState = CameraLifecycleState.OPENED;
                return;
            }
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            if (previewSurface == null || !previewSurface.isValid() || previewSurfaceTexture != texture) {
                if (previewSurface != null) previewSurface.release();
                previewSurface = new Surface(texture);
                previewSurfaceTexture = texture;
            }
            Surface sessionPreviewSurface = previewSurface;
            List<Surface> outputs = new ArrayList<>();
            outputs.add(sessionPreviewSurface);
            if (useRawForThisCamera && rawReader != null) outputs.add(rawReader.getSurface());
            else if (jpegReader != null) outputs.add(jpegReader.getSurface());
            cameraLifecycleState = CameraLifecycleState.CONFIGURING_PREVIEW;
            final long sessionTicket = sessionEpoch.next();
            final CameraDevice owner = cameraDevice;
            cameraDevice.createCaptureSession(outputs, new CameraCaptureSession.StateCallback() {
                @Override public void onConfigured(CameraCaptureSession session) {
                    if (!activityResumed || cameraDevice != owner || !sessionEpoch.owns(sessionTicket)) { session.close(); return; }
                    captureSession = session;
                    cameraLifecycleState = CameraLifecycleState.PREVIEW_READY;
                    closeRetiredReaders();
                    try {
                        previewRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        previewRequest.addTarget(sessionPreviewSurface);
                        previewRequest.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
                        if (videoMode) {
                            applyVideoFpsRange(previewRequest);
                        }
                        setExposure(previewRequest);
                        setFlash(previewRequest, true);
                        setFocusMode(previewRequest);
                        applyOpticalSettings(previewRequest, videoMode);
                        applyZoom(previewRequest);
                        captureSession.setRepeatingRequest(previewRequest.build(), previewMetadataCallback, cameraHandler);
                        runOnUiThread(() -> {
                            if (!activityResumed || !sessionEpoch.owns(sessionTicket)) return;
                            cameraOpenRetryCount = 0;
                            cameraRetryPromptActive = false;
                            if (cameraState != null) {
                                cameraState.setClickable(false);
                                cameraState.setOnClickListener(null);
                            }
                            updateTelemetryVisibility();
                            cameraUiHandler.removeCallbacks(cameraReopenRetryRunnable);
                            if (textureView != null) {
                                textureView.setVisibility(View.VISIBLE);
                                if (textureView.getWidth() > 0 && textureView.getHeight() > 0) {
                                    configureTransform(textureView.getWidth(), textureView.getHeight());
                                } else {
                                    textureView.post(() -> {
                                        if (textureView != null && textureView.getWidth() > 0 && textureView.getHeight() > 0) {
                                            configureTransform(textureView.getWidth(), textureView.getHeight());
                                        }
                                    });
                                }
                                if (!seamless || textureView.getAlpha() < 0.99f) {
                                    textureView.animate().cancel();
                                    textureView.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(140L).start();
                                }
                                applyLiveFilmPreview();
                            }
                            if (pendingPostConfigZoom > 0f && captureSession == session && previewRequest != null) {
                                pendingPostConfigSessionTicket = sessionTicket;
                                if (pendingPostConfigFallback != null) {
                                    cameraUiHandler.removeCallbacks(pendingPostConfigFallback);
                                }
                                pendingPostConfigFallback = () -> triggerPendingPostConfigZoom(session, sessionTicket);
                                cameraUiHandler.postDelayed(pendingPostConfigFallback, 180L);
                            } else {
                                clearPendingPostConfigZoom();
                            }
                        });
                    } catch (CameraAccessException e) {
                        runOnUiThread(() -> showMessage("Preview session failed."));
                    }
                }
                @Override public void onConfigureFailed(CameraCaptureSession session) {
                    session.close();
                    if (!sessionEpoch.owns(sessionTicket)) return;
                    closeRetiredReaders();
                    clearPendingPostConfigZoom();
                    cameraLifecycleState = CameraLifecycleState.OPENED;
                    if (useRawForThisCamera) {
                        useRawForThisCamera = false;
                        rawSupported = false;
                        AppSettings.prefs(MainActivity.this).edit().putBoolean(AppSettings.RAW_SUPPORTED, false).apply();
                        runOnUiThread(() -> {
                            cameraState.setText("RAW STREAM COMBINATION UNSUPPORTED · USING JPEG");
                            recreateJpegSession();
                        });
                    } else {
                        runOnUiThread(() -> {
                            closeCamera();
                            if (activityResumed && !isFinishing() && !isDestroyed()
                                    && CameraLifecycleState.shouldRetryOpen(cameraOpenRetryCount)) {
                                setCameraStateRetryPrompt("RECONNECTING CAMERA…", false);
                                scheduleCameraReopenRetry();
                            } else {
                                setCameraStateRetryPrompt("CAMERA PREVIEW ERROR", true);
                                showMessage("Camera preview could not be configured.");
                            }
                        });
                    }
                }
            }, cameraHandler);
        } catch (CameraAccessException | IllegalStateException e) {
            closeRetiredReaders();
            pendingPostConfigZoom = -1f;
            if (cameraDevice != null) cameraLifecycleState = CameraLifecycleState.OPENED;
            showMessage("Could not start camera preview.");
        }
    }

    private void closeRetiredReaders() {
        final List<ImageReader> toClose;
        synchronized (retiredReaders) {
            if (retiredReaders.isEmpty()) return;
            toClose = new ArrayList<>(retiredReaders);
            retiredReaders.clear();
        }
        for (ImageReader reader : toClose) {
            closeReader(reader);
        }
    }

    private void recreateJpegSession() {
        recreateCurrentPreviewSession("JPEG fallback could not start on this camera.", false);
    }

    private void recreateCurrentPreviewSession(String errorMessage, boolean seamless) {
        if (cameraDevice == null || characteristics == null) return;
        try {
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) return;
            if (seamless) {
                synchronized (retiredReaders) {
                    if (rawReader != null) { retiredReaders.add(rawReader); rawReader = null; }
                    if (jpegReader != null) { retiredReaders.add(jpegReader); jpegReader = null; }
                }
                prepareReader(map);
                createPreviewSession(true);
            } else {
                closeRetiredReaders();
                closeSessionOnly();
                prepareReader(map);
                createPreviewSession(false);
            }
        } catch (RuntimeException e) {
            closeRetiredReaders();
            showMessage(errorMessage);
        }
    }

    private void ensurePreviewReaderAndCreateSession() {
        boolean desiredRaw = rawSupported && !forceJpegForWide;
        boolean readerMismatch = useRawForThisCamera != desiredRaw
                || (desiredRaw && rawReader == null)
                || (!desiredRaw && jpegReader == null);
        useRawForThisCamera = desiredRaw;
        if (readerMismatch && characteristics != null) {
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map != null) prepareReader(map);
        }
        createPreviewSession();
    }

    private void closeSessionOnly() {
        sessionEpoch.invalidate();
        previewColourMetadata = null;
        lastPreviewMetadataAt = 0L;
        if (captureSession != null) { captureSession.close(); captureSession = null; }
        if (previewSurface != null) { previewSurface.release(); previewSurface = null; }
        previewSurfaceTexture = null;
        previewRequest = null;
    }

    private void cancelActiveCaptureLocked() {
        if (activeCapture != null) {
            activeCapture.cancel(MainActivity.this::releaseCapturedImage);
            activeCapture = null;
        }
        if (activeBurstCapture != null) {
            activeBurstCapture.cancel(MainActivity.this::releaseCapturedImage);
            activeBurstCapture = null;
        }
        if (activeBurstSession != null) {
            final NightStackSession sessionToCancel = activeBurstSession;
            activeBurstSession = null;
            sessionToCancel.cancel();
            executeOnProcessor(sessionToCancel::releaseOwnedResources);
        }
    }

    private void executeOnProcessor(Runnable task) {
        try {
            processor.execute(task);
        } catch (RejectedExecutionException ignored) {
            task.run();
        }
    }

    private static long safeImageTimestamp(Image image) {
        if (image == null) return 0L;
        try {
            return image.getTimestamp();
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private static long safeResultTimestamp(TotalCaptureResult result) {
        if (result == null) return 0L;
        try {
            Long ts = result.get(CaptureResult.SENSOR_TIMESTAMP);
            return ts == null ? 0L : ts;
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private void onImageAvailable(ImageReader reader) {
        final Image image;
        synchronized(captureLock) {
            try { image=reader.acquireNextImage(); } catch(IllegalStateException closed) { return; }
            if(image==null) return;
            ResourceLease lease=readerLeases.computeIfAbsent(reader,r -> new ResourceLease(() -> {
                try { r.close(); } finally { synchronized(captureLock) { readerLeases.remove(r); } }
            }));
            if(!lease.acquire()) { image.close();return; }
            imageLeases.put(image,lease);
        }
        InFlightCapture.CompletedPair<Image, TotalCaptureResult> pair = null;
        InFlightBurstCapture.BurstStep<Image, TotalCaptureResult> burstStep = null;
        NightStackSession burstSession = null;
        synchronized (captureLock) {
            if (activeBurstCapture != null) {
                burstStep = activeBurstCapture.attachImage(
                        image, safeImageTimestamp(image), MainActivity.this::releaseCapturedImage);
                burstSession = activeBurstSession;
                if (burstStep != null && burstStep.isLast) {
                    activeBurstCapture = null;
                    activeBurstSession = null;
                }
            } else if (activeCapture != null) {
                pair = activeCapture.attachImage(image, MainActivity.this::releaseCapturedImage);
                if (pair != null) {
                    activeCapture = null;
                }
            } else {
                releaseCapturedImage(image);
            }
        }
        if (pair != null) {
            dispatchCompletedCapture(pair.image, pair.result, pair.recipe);
        } else if (burstStep != null) {
            handleBurstStep(burstStep, burstSession);
        }
    }

    private final class CaptureOutputMeta {
        final String baseFileStem;
        final String orgSub;
        final String archiveKey;
        final boolean keepEditableSource;
        final boolean saveDng;
        final Location captureLocation;

        CaptureOutputMeta(boolean raw, CaptureRecipe recipe) {
            String stamp = formatCaptureStamp();
            String photoOutput = recipe.photoOutput;
            boolean dngOnly = raw && AppSettings.isDngOnly(photoOutput);
            this.keepEditableSource = raw && !dngOnly;
            this.saveDng = raw && AppSettings.shouldSaveDng(photoOutput);
            this.captureLocation = getCaptureLocation();
            String prefix = resolvedFilePrefix();
            String folderOrg = AppSettings.get(MainActivity.this, AppSettings.FOLDER_ORG, "Single folder (Pictures/Lika Lens)");
            final String sub = CaptureFileNaming.buildFolderSubpath(folderOrg, recipe.look.brain, formatMonthFolder());
            this.orgSub = sub;
            String namingPattern = AppSettings.get(MainActivity.this, AppSettings.FILE_NAMING, "Prefix + Timestamp");
            boolean isRoll = namingPattern.contains("Roll counter");
            int roll = isRoll ? AppSettings.getInt(MainActivity.this, "roll_counter", 1) : 1;
            String rawStem = CaptureFileNaming.buildStillBaseStem(prefix, namingPattern, recipe.look.brain, stamp, roll);
            this.baseFileStem = CaptureFileNaming.ensureUniqueStem(
                    rawStem, candidate -> CaptureFileNaming.stillStemExists(MainActivity.this, candidate, sub));
            if (isRoll) {
                AppSettings.prefs(MainActivity.this).edit().putInt("roll_counter", roll + 1).apply();
            }
            this.archiveKey = this.baseFileStem;
        }
    }

    private final class NightStackSession {
        final long renderJob;
        final boolean raw;
        final CaptureRecipe recipe;
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        final AtomicBoolean jobFinished = new AtomicBoolean(false);

        // Accessed exclusively on single-threaded `processor`
        CaptureOutputMeta meta;
        TotalCaptureResult baseResult;
        boolean dngSaved;
        String dngWarning;
        NightStacker.RawAccumulator rawAccumulator;
        RawArchive.Frame baseRawFrame;
        ByteBuffer fallbackRawBuffer;
        NightStacker.RgbAccumulator rgbAccumulator;
        Bitmap fallbackJpegBitmap;
        int stackedFrames;

        NightStackSession(long renderJob, boolean raw, CaptureRecipe recipe) {
            this.renderJob = renderJob;
            this.raw = raw;
            this.recipe = recipe;
        }

        void prepareMetadataIfNeeded() {
            if (meta == null) {
                meta = new CaptureOutputMeta(raw, recipe);
            }
        }

        void accumulateFrame(Image image, TotalCaptureResult result) {
            prepareMetadataIfNeeded();
            if (raw) {
                Image.Plane plane = image.getPlanes()[0];
                int width = image.getWidth();
                int height = image.getHeight();
                int pixelStride = plane.getPixelStride() > 0 ? plane.getPixelStride() : 2;
                int rowStride = plane.getRowStride() > 0 ? plane.getRowStride() : width * pixelStride;
                if (baseRawFrame == null) {
                    RawArchive.Frame frame = RawArchive.createFrame(meta.archiveKey, image, recipe.characteristics,
                            result, recipe.orientation, recipe.look.aspectRatio);
                    frame.previewMidTarget = recipe.previewMidTarget;
                    short[] samples = NightStacker.extractRawShorts(
                            frame.captureBuffer, frame.width, frame.height, frame.rowStride, frame.pixelStride);
                    frame.captureBuffer = null;
                    if (meta.saveDng) {
                        try {
                            String dngSubfolder = meta.orgSub.isEmpty() ? "RAW" : meta.orgSub + "/RAW";
                            writeDng(image, result, recipe.characteristics, recipe.orientation,
                                    meta.baseFileStem + ".dng", dngSubfolder, meta.captureLocation);
                            dngSaved = true;
                        } catch (Exception dngError) {
                            dngWarning = dngError.getClass().getSimpleName();
                            Log.e(TAG, "DNG export failed; continuing with the Lika Lens render", dngError);
                        }
                    }
                    baseRawFrame = frame;
                    baseResult = result;
                    int whiteLevel = frame.dynamicWhiteLevel > 0 ? frame.dynamicWhiteLevel : frame.staticWhiteLevel;
                    try {
                        rawAccumulator = new NightStacker.RawAccumulator(frame.width, frame.height, whiteLevel);
                        rawAccumulator.addFrame(samples);
                    } catch (OutOfMemoryError oom) {
                        Log.w(TAG, "Night stack RAW buffer allocation exceeded memory; keeping frame 0", oom);
                        if (rawAccumulator != null) {
                            rawAccumulator.release();
                            rawAccumulator = null;
                        }
                        fallbackRawBuffer = NightStacker.toLittleEndianBuffer(samples);
                    }
                    stackedFrames = 1;
                } else if (rawAccumulator != null && width == rawAccumulator.width && height == rawAccumulator.height) {
                    short[] samples = NightStacker.extractRawShorts(plane.getBuffer(), width, height, rowStride, pixelStride);
                    rawAccumulator.addFrame(samples);
                    stackedFrames++;
                }
            } else {
                ByteBuffer buffer = image.getPlanes()[0].getBuffer().duplicate();
                buffer.position(0);
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                Bitmap decoded = RawDeveloper.decodeOrientedJpegForGrading(bytes, recipe.orientation);
                if (decoded == null) return;
                int w = decoded.getWidth();
                int h = decoded.getHeight();
                if (rgbAccumulator == null && fallbackJpegBitmap == null) {
                    baseResult = result;
                    try {
                        int[] pixels = new int[w * h];
                        decoded.getPixels(pixels, 0, w, 0, 0, w, h);
                        rgbAccumulator = new NightStacker.RgbAccumulator(w, h);
                        rgbAccumulator.addFrame(pixels);
                        decoded.recycle();
                    } catch (OutOfMemoryError oom) {
                        Log.w(TAG, "Night stack RGB buffer allocation exceeded memory; keeping frame 0", oom);
                        if (rgbAccumulator != null) {
                            rgbAccumulator.release();
                            rgbAccumulator = null;
                        }
                        fallbackJpegBitmap = decoded;
                    }
                    stackedFrames = 1;
                } else if (rgbAccumulator != null && w == rgbAccumulator.width && h == rgbAccumulator.height) {
                    int[] pixels = new int[w * h];
                    decoded.getPixels(pixels, 0, w, 0, 0, w, h);
                    decoded.recycle();
                    rgbAccumulator.addFrame(pixels);
                    stackedFrames++;
                } else {
                    decoded.recycle();
                }
            }
        }

        void cancel() {
            cancelled.set(true);
        }

        void releaseOwnedResources() {
            baseResult = null;
            if (baseRawFrame != null) {
                baseRawFrame.captureBuffer = null;
                baseRawFrame = null;
            }
            fallbackRawBuffer = null;
            if (rawAccumulator != null) {
                rawAccumulator.release();
                rawAccumulator = null;
            }
            if (rgbAccumulator != null) {
                rgbAccumulator.release();
                rgbAccumulator = null;
            }
            if (fallbackJpegBitmap != null && !fallbackJpegBitmap.isRecycled()) {
                fallbackJpegBitmap.recycle();
            }
            fallbackJpegBitmap = null;
            finishJob(false);
        }

        void finishJob(boolean succeeded) {
            if (jobFinished.compareAndSet(false, true)) {
                captureJobs.finish(renderJob, succeeded);
                runOnUiThread(MainActivity.this::updateGalleryProcessingUi);
            }
        }
    }

    private Bitmap finishGradedJpegBitmap(Bitmap orientedSource, CaptureRecipe recipe) {
        if (orientedSource == null) return null;
        RawDeveloper.Look jpegLook = recipe.look.copy();
        String savedBorder = jpegLook.frameBorder;
        jpegLook.frameBorder = "None";
        Bitmap developed = RawDeveloper.developBitmap(orientedSource, jpegLook, true);
        if (developed == null) return null;
        developed = cropToAspect(developed, recipe.look.aspectRatio);
        if (savedBorder != null && !savedBorder.startsWith("None")) {
            jpegLook.frameBorder = savedBorder;
            developed = RawDeveloper.applyFrameBorder(developed, jpegLook, null);
        }
        return developed;
    }

    private void publishDevelopedPhoto(long renderJob, boolean raw, CaptureRecipe recipe,
                                       CaptureOutputMeta meta, RawArchive.Frame rawFrame,
                                       TotalCaptureResult exifResult, Bitmap developed,
                                       boolean dngSaved, String dngWarning, int stackedFrames) throws Exception {
        boolean dngOnly = raw && AppSettings.isDngOnly(recipe.photoOutput);
        boolean skipJpegPublish = dngOnly && dngSaved;
        if (developed == null && !skipJpegPublish) throw new IllegalStateException("Renderer returned no image");
        boolean shouldKeepArchive = raw && rawFrame != null && (meta.keepEditableSource || !dngSaved);
        boolean skippedArchiveForLowSpace = false;
        if (shouldKeepArchive) {
            if (!RawArchive.hasSufficientSpaceForArchive(this)) {
                skippedArchiveForLowSpace = true;
                Log.w(TAG, "Skipping editable RAW archive due to low internal storage");
            } else {
                RawArchive.pin(meta.archiveKey);
                try {
                    captureJobs.move(renderJob, CaptureJobs.Phase.ARCHIVING);
                    RawArchive.writeArchive(this, rawFrame);
                    PhotoEditStore.save(this, meta.archiveKey, recipe.look);
                    String limitChoice = AppSettings.get(this, AppSettings.EDITABLE_ARCHIVE_LIMIT,
                            AppSettings.EDITABLE_ARCHIVE_512MB);
                    RawArchive.enforceStorageLimit(this, limitChoice, meta.archiveKey);
                } catch (Exception archiveErr) {
                    Log.w(TAG, "Could not save editable RAW archive", archiveErr);
                } finally {
                    RawArchive.unpin(meta.archiveKey);
                }
            }
        }
        Bitmap quickThumbnail = null;
        if (!skipJpegPublish) {
            captureJobs.move(renderJob, CaptureJobs.Phase.PUBLISHING);
            writeJpeg(developed, meta.baseFileStem + ".jpg", exifResult, meta.orgSub, true, meta.captureLocation);
            quickThumbnail = createGalleryThumbnail(developed);
        }
        if (developed != null && !developed.isRecycled()) {
            developed.recycle();
        }

        final Bitmap finalThumbnail = quickThumbnail;
        final String resultMessage;
        String suffix = skippedArchiveForLowSpace ? " · storage low (RAW edit cache skipped)" : "";
        String dngFolder = "Pictures/Lika Lens/" + (meta.orgSub.isEmpty() ? "RAW" : meta.orgSub + "/RAW");
        String dngOnlyMessage = "DNG saved to " + dngFolder + " · view in Photos/RAW app";
        if (stackedFrames > 1) {
            if (dngWarning != null) {
                resultMessage = String.format(Locale.US, "✨ Night stacked (%d frames); DNG failed", stackedFrames) + suffix;
            } else if (skipJpegPublish) {
                resultMessage = dngOnlyMessage;
            } else if (dngSaved) {
                resultMessage = String.format(Locale.US, "✨ Night stacked (%d frames) + DNG saved", stackedFrames) + suffix;
            } else {
                resultMessage = String.format(Locale.US, "✨ Night stacked (%d frames)", stackedFrames) + suffix;
            }
        } else {
            resultMessage = (dngWarning != null ? "Photo rendered; DNG export failed"
                    : !raw ? "JPEG fallback captured and rendered"
                    : skipJpegPublish ? dngOnlyMessage
                    : dngSaved ? "DNG original + Lika Lens render saved" : "Lika Lens RAW render saved") + suffix;
        }
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) {
                if (finalThumbnail != null && !finalThumbnail.isRecycled()) finalThumbnail.recycle();
                return;
            }
            showMessage(resultMessage);
            if (finalThumbnail != null) applyGalleryThumbnail(finalThumbnail);
            else updateGalleryButton();
        });
    }

    private void handleBurstStep(InFlightBurstCapture.BurstStep<Image, TotalCaptureResult> step,
                                 NightStackSession session) {
        if (step.isLast) {
            stillCaptureState = StillCaptureState.IDLE;
            runOnUiThread(() -> {
                if (captureButton != null) captureButton.setAlpha(1f);
                updateGalleryProcessingUi();
            });
        }
        if (session == null) {
            if (step.image != null) releaseCapturedImage(step.image);
            return;
        }
        if (step.failed && step.completedCount == 0) {
            if (step.image != null) releaseCapturedImage(step.image);
            runOnUiThread(() -> showMessage("Night capture failed. Try again."));
            executeOnProcessor(session::releaseOwnedResources);
            return;
        }
        final Image frameImage = step.image;
        final TotalCaptureResult frameResult = step.result;
        final boolean shouldFinalize = step.isLast;
        executeOnProcessor(() -> {
            try {
                if (session.cancelled.get()) {
                    return;
                }
                if (frameImage != null) {
                    session.accumulateFrame(frameImage, frameResult);
                }
            } catch (Throwable err) {
                Log.e(TAG, "Night burst frame accumulation failed", err);
            } finally {
                if (frameImage != null) {
                    releaseCapturedImage(frameImage);
                }
            }
            if (shouldFinalize) {
                if (session.cancelled.get()) {
                    session.releaseOwnedResources();
                } else {
                    finalizeNightStackSession(session);
                }
            }
        });
    }

    private void finalizeNightStackSession(NightStackSession session) {
        boolean renderSucceeded = false;
        try {
            if (session.cancelled.get()) return;
            session.prepareMetadataIfNeeded();
            Bitmap developed = null;
            if (session.raw) {
                RawArchive.Frame frame = session.baseRawFrame;
                if (frame == null) throw new IllegalStateException("No RAW burst frames captured");
                boolean skipJpegRender = AppSettings.isDngOnly(session.recipe.photoOutput) && session.dngSaved;
                if (!skipJpegRender) {
                    if (session.rawAccumulator != null) {
                        short[] stackedShorts = session.rawAccumulator.finishShorts();
                        session.rawAccumulator = null;
                        frame.captureBuffer = NightStacker.toLittleEndianBuffer(stackedShorts);
                        frame.rowStride = frame.width * 2;
                        frame.pixelStride = 2;
                    } else if (session.fallbackRawBuffer != null) {
                        frame.captureBuffer = session.fallbackRawBuffer;
                        frame.rowStride = frame.width * 2;
                        frame.pixelStride = 2;
                    }
                    try {
                        developed = RawDeveloper.develop(frame, session.recipe.look, 0);
                    } catch (IllegalArgumentException | OutOfMemoryError resolutionError) {
                        Log.w(TAG, "Full-resolution stacked RAW render failed; retrying at a safe display size", resolutionError);
                        developed = RawDeveloper.develop(frame, session.recipe.look, 3000);
                    }
                }
                publishDevelopedPhoto(session.renderJob, true, session.recipe, session.meta, frame,
                        session.baseResult, developed, session.dngSaved, session.dngWarning, session.stackedFrames);
            } else {
                Bitmap stackedSource = null;
                if (session.rgbAccumulator != null) {
                    int w = session.rgbAccumulator.width;
                    int h = session.rgbAccumulator.height;
                    int[] pixels = session.rgbAccumulator.finishPixels();
                    session.rgbAccumulator = null;
                    stackedSource = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888);
                } else if (session.fallbackJpegBitmap != null) {
                    stackedSource = session.fallbackJpegBitmap;
                    session.fallbackJpegBitmap = null;
                }
                developed = finishGradedJpegBitmap(stackedSource, session.recipe);
                publishDevelopedPhoto(session.renderJob, false, session.recipe, session.meta, null,
                        session.baseResult, developed, false, null, session.stackedFrames);
            }
            renderSucceeded = true;
        } catch (Exception e) {
            Log.e(TAG, "Night stacked photo development failed", e);
            String detail = e.getMessage();
            if (detail != null) {
                detail = detail.replace('\n', ' ').trim();
                if (detail.length() > 72) detail = detail.substring(0, 72);
            }
            final String errorDetail = detail;
            runOnUiThread(() -> showMessage("Photo render failed: " + e.getClass().getSimpleName()
                    + (errorDetail == null || errorDetail.isEmpty() ? "" : " · " + errorDetail)));
        } finally {
            session.finishJob(renderSucceeded);
            session.releaseOwnedResources();
        }
    }

    private void dispatchCompletedCapture(Image image, TotalCaptureResult result, CaptureRecipe recipe) {
        final long renderJob = captureJobs.begin();
        stillCaptureState = StillCaptureState.IDLE;
        runOnUiThread(() -> {
            if (captureButton != null) captureButton.setAlpha(1f);
            updateGalleryProcessingUi();
        });
        CameraCharacteristics capturedCharacteristics = recipe.characteristics;
        int capturedOrientation = recipe.orientation;
        RawDeveloper.Look captureLook = recipe.look;
        String capturedAspect = captureLook.aspectRatio;
        executeOnProcessor(() -> {
            boolean renderSucceeded = false;
            boolean raw = image.getFormat() == ImageFormat.RAW_SENSOR;
            RawArchive.Frame rawFrame = null;
            try {
                CaptureOutputMeta meta = new CaptureOutputMeta(raw, recipe);
                boolean dngSaved = false;
                String dngWarning = null;
                if (meta.saveDng) {
                    try {
                        String dngSubfolder = meta.orgSub.isEmpty() ? "RAW" : meta.orgSub + "/RAW";
                        writeDng(image, result, capturedCharacteristics, capturedOrientation,
                                meta.baseFileStem + ".dng", dngSubfolder, meta.captureLocation);
                        dngSaved = true;
                    } catch (Exception dngError) {
                        dngWarning = dngError.getClass().getSimpleName();
                        Log.e(TAG, "DNG export failed; continuing with the Lika Lens render", dngError);
                    }
                }
                Bitmap developed = null;
                boolean skipJpegRender = raw && AppSettings.isDngOnly(recipe.photoOutput) && dngSaved;
                if (raw) {
                    if (!skipJpegRender) {
                        rawFrame = RawArchive.createFrame(meta.archiveKey, image, capturedCharacteristics,
                                result, capturedOrientation, capturedAspect);
                        rawFrame.previewMidTarget = recipe.previewMidTarget;
                        try {
                            developed = RawDeveloper.develop(rawFrame, captureLook, 0);
                        } catch (IllegalArgumentException | OutOfMemoryError resolutionError) {
                            Log.w(TAG, "Full-resolution RAW render failed; retrying at a safe display size", resolutionError);
                            developed = RawDeveloper.develop(rawFrame, captureLook, 3000);
                        }
                    }
                } else {
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);
                    Bitmap oriented = RawDeveloper.decodeOrientedJpegForGrading(bytes, capturedOrientation);
                    developed = finishGradedJpegBitmap(oriented, recipe);
                }
                publishDevelopedPhoto(renderJob, raw, recipe, meta, rawFrame, result, developed, dngSaved, dngWarning, 1);
                renderSucceeded = true;
            } catch (Exception e) {
                Log.e(TAG, "Photo development failed", e);
                String detail = e.getMessage();
                if (detail != null) {
                    detail = detail.replace('\n', ' ').trim();
                    if (detail.length() > 72) detail = detail.substring(0, 72);
                }
                final String errorDetail = detail;
                runOnUiThread(() -> showMessage("Photo render failed: " + e.getClass().getSimpleName()
                        + (errorDetail == null || errorDetail.isEmpty() ? "" : " · " + errorDetail)));
            } finally {
                try { releaseCapturedImage(image); }
                finally {
                    captureJobs.finish(renderJob, renderSucceeded);
                    runOnUiThread(this::updateGalleryProcessingUi);
                }
            }
        });
    }

    private void capturePhoto() {
        if (cameraDevice == null || !cameraLifecycleState.isStarted()) {
            showMessage("Camera is starting up…");
            return;
        }
        if (stillCaptureState.isTimerRunning()) {
            cancelTimerCountdown();
            return;
        }
        if (captureSession == null || stillCaptureState.isBusy()) {
            return;
        }
        if (captureJobs.size() >= 4) {
            showMessage("Developing previous frames…");
            return;
        }
        if (AppSettings.getBoolean(this, AppSettings.HAPTICS, true)) captureButton.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
        int timer = parseTimer(AppSettings.get(this, AppSettings.TIMER, "Off"));
        if (timer > 0) {
            startTimerCountdown(timer);
        } else {
            stillCaptureState = StillCaptureState.CAPTURING;
            captureButton.setAlpha(.65f);
            updateGalleryProcessingUi();
            triggerCaptureWithScreenFlashIfNeeded();
        }
    }

    private void startTimerCountdown(int totalSeconds) {
        final long countdownTicket = delayedCaptureEpoch.next();
        stillCaptureState = StillCaptureState.TIMER_COUNTDOWN;
        if (captureButton != null) captureButton.setAlpha(0.65f);
        final boolean useTorch = AppSettings.getBoolean(this, AppSettings.TIMER_LIGHT, true) && flashSupported && !isFrontCamera();

        timerCountdownRunnable = new Runnable() {
            int remaining = totalSeconds;
            @Override
            public void run() {
                if (!delayedCaptureEpoch.owns(countdownTicket) || !stillCaptureState.isTimerRunning()) {
                    setTimerTorchPulse(false);
                    return;
                }
                if (remaining <= 0) {
                    stillCaptureState = StillCaptureState.CAPTURING;
                    setTimerTorchPulse(false);
                    updateGalleryProcessingUi();
                    triggerCaptureWithScreenFlashIfNeeded();
                    return;
                }
                showMessage(String.format(Locale.US, "⏱ %ds", remaining));
                hapticDetent();
                if (useTorch) {
                    setTimerTorchPulse(true);
                    timerHandler.postDelayed(() -> { if (delayedCaptureEpoch.owns(countdownTicket)) setTimerTorchPulse(false); }, 120L);
                    if (remaining <= 2) {
                        timerHandler.postDelayed(() -> {
                            if (delayedCaptureEpoch.owns(countdownTicket) && stillCaptureState.isTimerRunning()) {
                                setTimerTorchPulse(true);
                                timerHandler.postDelayed(() -> { if (delayedCaptureEpoch.owns(countdownTicket)) setTimerTorchPulse(false); }, 100L);
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
        delayedCaptureEpoch.invalidate();
        timerHandler.removeCallbacksAndMessages(null);
        stillCaptureState = StillCaptureState.IDLE;
        if (timerCountdownRunnable != null) {
            timerHandler.removeCallbacks(timerCountdownRunnable);
            timerCountdownRunnable = null;
        }
        setTimerTorchPulse(false);
        if (captureButton != null) captureButton.setAlpha(1.0f);
        updateGalleryProcessingUi();
        showMessage("Timer canceled");
    }

    private void setTimerTorchPulse(boolean active) {
        if (timerTorchActive == active) return;
        timerTorchActive = active;
        refreshPreviewRequest();
    }

    private void restoreScreenFlashBrightness() {
        if(!Float.isNaN(originalFlashBrightness)) {
            WindowManager.LayoutParams attributes=getWindow().getAttributes();
            attributes.screenBrightness=originalFlashBrightness;getWindow().setAttributes(attributes);
            originalFlashBrightness=Float.NaN;
        }
    }

    private void cancelDelayedCapture() {
        delayedCaptureEpoch.invalidate();
        timerHandler.removeCallbacksAndMessages(null);
        setTimerTorchPulse(false);
        boolean wasDelayed = stillCaptureState.isTimerRunning() || stillCaptureState == StillCaptureState.SCREEN_FLASH;
        if (wasDelayed) stillCaptureState = StillCaptureState.IDLE;
        runOnUiThread(() -> {
            if(screenFlashOverlay!=null) { screenFlashOverlay.animate().cancel();screenFlashOverlay.setVisibility(View.GONE); }
            restoreScreenFlashBrightness();
            if (wasDelayed && captureButton != null) captureButton.setAlpha(1.0f);
            updateGalleryProcessingUi();
        });
    }

    private void triggerCaptureWithScreenFlashIfNeeded() {
        final long flashTicket = delayedCaptureEpoch.next();
        String flashMode = AppSettings.get(this, AppSettings.FLASH, "Off");
        if (isFrontCamera() && !"Off".equals(flashMode) && screenFlashOverlay != null) {
            stillCaptureState = StillCaptureState.SCREEN_FLASH;
            updateGalleryProcessingUi();
            runOnUiThread(() -> {
                WindowManager.LayoutParams lp = getWindow().getAttributes();
                if(Float.isNaN(originalFlashBrightness)) originalFlashBrightness=lp.screenBrightness;
                lp.screenBrightness = 1.0f;
                getWindow().setAttributes(lp);
                boolean maxScreenFlash = "Max".equalsIgnoreCase(flashMode);
                screenFlashOverlay.setBackgroundColor(maxScreenFlash ? 0xffffffff : 0xfffff4e0);
                screenFlashOverlay.setAlpha(maxScreenFlash ? 1f : 0.95f);
                screenFlashOverlay.setVisibility(View.VISIBLE);
                cameraHandler.postDelayed(() -> {
                    if (delayedCaptureEpoch.owns(flashTicket) && activityResumed && stillCaptureState == StillCaptureState.SCREEN_FLASH) {
                        stillCaptureState = StillCaptureState.CAPTURING;
                        runOnUiThread(this::performCapture);
                    }
                    runOnUiThread(() -> {
                        if(!delayedCaptureEpoch.owns(flashTicket)) return;
                        screenFlashOverlay.animate().alpha(0f).setDuration(240).withEndAction(() -> {
                            if(!delayedCaptureEpoch.owns(flashTicket)) return;
                            screenFlashOverlay.setVisibility(View.GONE);
                            restoreScreenFlashBrightness();
                        }).start();
                    });
                }, "Max".equalsIgnoreCase(flashMode) ? 450L : 320L);
            });
        } else {
            stillCaptureState = StillCaptureState.CAPTURING;
            updateGalleryProcessingUi();
            performCapture();
        }
    }

    private int parseTimer(String value) {
        if (value.startsWith("3")) return 3;
        if (value.startsWith("10")) return 10;
        return 0;
    }

    private void performCapture() {
        if (captureSession == null || cameraDevice == null) {
            stillCaptureState = StillCaptureState.IDLE;
            runOnUiThread(() -> {
                if (captureButton != null) captureButton.setAlpha(1f);
                updateGalleryProcessingUi();
            });
            return;
        }
        triggerShutterWink();
        playShutterSound(android.media.MediaActionSound.SHUTTER_CLICK);
        if (AppSettings.getBoolean(this, AppSettings.HAPTICS, true) && captureButton != null) {
            captureButton.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
        }
        InFlightCapture<Image, TotalCaptureResult> captureToken = null;
        try {
            boolean rawCapture = useRawForThisCamera && rawReader != null;
            Surface target = rawCapture ? rawReader.getSurface() : jpegReader.getSurface();
            String astroSetting = AppSettings.get(this, AppSettings.ASTROPHOTOGRAPHY, "Auto (when stationary at night)");
            RawArchive.Frame meteredPreview = previewColourMetadata;
            long meteredExposure = meteredPreview == null ? 0L : meteredPreview.exposureTimeNs;
            int meteredIso = meteredPreview == null ? 0 : meteredPreview.sensitivityIso;
            boolean manualExposure = AppSettings.get(this, AppSettings.EXPOSURE_MODE, "Auto exposure").startsWith("Manual");
            String flashMode = AppSettings.get(this, AppSettings.FLASH, "Off");
            boolean isAstro = CameraExposureHelper.shouldUseNightIntegration(
                    astroSetting, motion.stationary(), manualExposure, meteredExposure, meteredIso, flashMode);
            final int capturedOrientation = jpegOrientation();
            RawDeveloper.Look captureLook = RawDeveloper.Look.fromPreferences(this);
            captureLook.flipHorizontal = isFrontCamera() && AppSettings.getBoolean(this, AppSettings.MIRROR_FRONT, true);
            CaptureRecipe recipe = new CaptureRecipe(characteristics, capturedOrientation, captureLook,
                    AppSettings.get(this, AppSettings.PHOTO_OUTPUT, AppSettings.PHOTO_OUTPUT_JPEG_ONLY),
                    samplePreviewMidTarget());
            if (isAstro) {
                performNightBurstCapture(target, rawCapture, recipe, astroSetting, meteredExposure, meteredIso);
                return;
            }
            CaptureRequest.Builder still = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            still.addTarget(target);
            setExposure(still);
            still.set(CaptureRequest.JPEG_ORIENTATION, capturedOrientation);
            setFocusMode(still);
            setFlash(still, false);
            applyOpticalSettings(still, false);
            if (!isManualFocusActive() && focusMode != FocusMode.LOCKED
                    && AppSettings.get(this, AppSettings.FOCUS_MODE, AppSettings.FOCUS_CONTINUOUS).startsWith("Single")) {
                still.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
            }
            applyZoom(still);
            synchronized (captureLock) {
                cancelActiveCaptureLocked();
                captureToken = new InFlightCapture<>(nextCaptureId++, rawCapture, recipe);
                activeCapture = captureToken;
            }
            final InFlightCapture<Image, TotalCaptureResult> submittedCapture = captureToken;
            runOnUiThread(this::updateGalleryProcessingUi);
            captureSession.capture(still.build(), new CameraCaptureSession.CaptureCallback() {
                @Override public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    InFlightCapture.CompletedPair<Image, TotalCaptureResult> pair = null;
                    synchronized (captureLock) {
                        if (activeCapture == submittedCapture) {
                            pair = submittedCapture.attachResult(result);
                            if (pair != null) {
                                activeCapture = null;
                            }
                        }
                    }
                    if (pair != null) {
                        dispatchCompletedCapture(pair.image, pair.result, pair.recipe);
                    }
                }
                @Override public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request, CaptureFailure failure) {
                    synchronized (captureLock) {
                        if (activeCapture == submittedCapture) {
                            submittedCapture.cancel(MainActivity.this::releaseCapturedImage);
                            activeCapture = null;
                        }
                    }
                    stillCaptureState = StillCaptureState.IDLE;
                    runOnUiThread(() -> {
                        if (captureButton != null) captureButton.setAlpha(1f);
                        updateGalleryProcessingUi();
                        showMessage("Capture failed. Try again.");
                    });
                }
            }, cameraHandler);
        } catch (CameraAccessException | IllegalStateException e) {
            synchronized (captureLock) {
                cancelActiveCaptureLocked();
            }
            stillCaptureState = StillCaptureState.IDLE;
            if (captureButton != null) captureButton.setAlpha(1f);
            updateGalleryProcessingUi();
            showMessage("Camera capture failed.");
        }
    }

    private void performNightBurstCapture(Surface target, boolean rawCapture, CaptureRecipe recipe,
                                          String astroSetting, long meteredExposure, int meteredIso)
            throws CameraAccessException {
        Range<Long> expRange = characteristics == null ? null : characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
        Range<Integer> sensRange = characteristics == null ? null : characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
        long targetExp = 0L;
        int targetIso = 0;
        int frameCount = CameraExposureHelper.NIGHT_STACK_FRAME_COUNT;
        boolean lockManual = expRange != null && sensRange != null;
        if (lockManual) {
            long[] params = CameraExposureHelper.computeNightStackParameters(
                    astroSetting, meteredExposure, meteredIso,
                    expRange.getLower(), expRange.getUpper(),
                    sensRange.getLower(), sensRange.getUpper());
            targetExp = params[0];
            targetIso = (int) params[1];
            frameCount = (int) params[2];
        }

        boolean singleAf = !isManualFocusActive() && focusMode != FocusMode.LOCKED
                && AppSettings.get(this, AppSettings.FOCUS_MODE, AppSettings.FOCUS_CONTINUOUS).startsWith("Single");

        List<CaptureRequest> burstRequests = new ArrayList<>(frameCount);
        for (int i = 0; i < frameCount; i++) {
            CaptureRequest.Builder frameReq = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            frameReq.addTarget(target);
            setExposure(frameReq);
            if (lockManual) {
                frameReq.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
                frameReq.set(CaptureRequest.SENSOR_EXPOSURE_TIME, targetExp);
                frameReq.set(CaptureRequest.SENSOR_SENSITIVITY, targetIso);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    frameReq.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY);
                }
            }
            frameReq.set(CaptureRequest.CONTROL_AWB_LOCK, true);
            frameReq.set(CaptureRequest.JPEG_ORIENTATION, recipe.orientation);
            setFocusMode(frameReq);
            setFlash(frameReq, false);
            applyOpticalSettings(frameReq, false);
            if (singleAf) {
                frameReq.set(CaptureRequest.CONTROL_AF_TRIGGER,
                        i == 0 ? CaptureRequest.CONTROL_AF_TRIGGER_START : CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
            }
            applyZoom(frameReq);
            burstRequests.add(frameReq.build());
        }

        final long renderJob = captureJobs.begin();
        final NightStackSession session = new NightStackSession(renderJob, rawCapture, recipe);
        final InFlightBurstCapture<Image, TotalCaptureResult> burstToken =
                new InFlightBurstCapture<>(nextCaptureId++, frameCount, rawCapture, recipe);

        synchronized (captureLock) {
            cancelActiveCaptureLocked();
            activeBurstCapture = burstToken;
            activeBurstSession = session;
        }

        final String toastMsg = lockManual
                ? String.format(Locale.US, "✨ Stacking %d frames · %.2fs each…", frameCount, targetExp / 1_000_000_000f)
                : String.format(Locale.US, "✨ Stacking %d night frames…", frameCount);
        runOnUiThread(() -> {
            updateGalleryProcessingUi();
            showMessage(toastMsg);
        });

        captureSession.captureBurst(burstRequests, new CameraCaptureSession.CaptureCallback() {
            @Override public void onCaptureCompleted(CameraCaptureSession camSession, CaptureRequest request, TotalCaptureResult result) {
                InFlightBurstCapture.BurstStep<Image, TotalCaptureResult> step = null;
                NightStackSession owningSession = null;
                synchronized (captureLock) {
                    if (activeBurstCapture == burstToken) {
                        step = burstToken.attachResult(
                                result, safeResultTimestamp(result), MainActivity.this::releaseCapturedImage);
                        owningSession = activeBurstSession;
                        if (step != null && step.isLast) {
                            activeBurstCapture = null;
                            activeBurstSession = null;
                        }
                    }
                }
                if (step != null) {
                    handleBurstStep(step, owningSession);
                }
            }

            @Override public void onCaptureFailed(CameraCaptureSession camSession, CaptureRequest request, CaptureFailure failure) {
                InFlightBurstCapture.BurstStep<Image, TotalCaptureResult> step = null;
                NightStackSession owningSession = null;
                synchronized (captureLock) {
                    if (activeBurstCapture == burstToken) {
                        step = burstToken.markFrameFailed(MainActivity.this::releaseCapturedImage);
                        owningSession = activeBurstSession;
                        if (step != null && step.isLast) {
                            activeBurstCapture = null;
                            activeBurstSession = null;
                        }
                    }
                }
                if (step != null) {
                    handleBurstStep(step, owningSession);
                }
            }

            @Override public void onCaptureSequenceAborted(CameraCaptureSession camSession, int sequenceId) {
                InFlightBurstCapture.BurstStep<Image, TotalCaptureResult> step = null;
                NightStackSession owningSession = null;
                synchronized (captureLock) {
                    if (activeBurstCapture == burstToken) {
                        step = burstToken.abortSequence(MainActivity.this::releaseCapturedImage);
                        owningSession = activeBurstSession;
                        if (step != null && step.isLast) {
                            activeBurstCapture = null;
                            activeBurstSession = null;
                        }
                    }
                }
                if (step != null) {
                    handleBurstStep(step, owningSession);
                }
            }
        }, cameraHandler);
    }

    private float samplePreviewMidTarget() {
        if (Looper.myLooper() != Looper.getMainLooper() || textureView == null || !textureView.isAvailable()) return 0f;
        Bitmap sample = null;
        try {
            // TextureView reads its camera buffer before the View's look effect, avoiding feedback.
            sample = textureView.getBitmap(64, 48);
            if (sample == null) return 0f;
            int[] pixels = new int[64 * 48];
            sample.getPixels(pixels, 0, 64, 0, 0, 64, 48);
            return RawDeveloper.previewMidTarget(pixels, 64, 48);
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not meter preview reference", e);
            return 0f;
        } finally {
            if (sample != null) sample.recycle();
        }
    }

    private int jpegOrientation() {
        if (characteristics == null) return 0;
        Integer sensor = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
        int sensorOrientation = sensor == null ? 90 : sensor;
        boolean isFront = facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT;
        int clockwiseDevice = quantizedDeviceRotation >= 0
                ? quantizedDeviceRotation
                : CaptureOrientation.displayRotationToClockwiseDevice(getWindowManager().getDefaultDisplay().getRotation());
        return CaptureOrientation.calculateCaptureOrientation(sensorOrientation, isFront, clockwiseDevice);
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
        if (!AppSettings.getBoolean(this, AppSettings.LOCATION, true)) return null;
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

    private static final SimpleDateFormat CAPTURE_STAMP_FMT = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US);
    private static final SimpleDateFormat MONTH_FOLDER_FMT = new SimpleDateFormat("yyyy-MM", Locale.US);
    private static final SimpleDateFormat EXIF_STAMP_FMT = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US);
    private static final SimpleDateFormat GPS_TIME_FMT;
    private static final SimpleDateFormat GPS_DATE_FMT;
    static {
        TimeZone utc = TimeZone.getTimeZone("UTC");
        GPS_TIME_FMT = new SimpleDateFormat("HH:mm:ss", Locale.US);
        GPS_TIME_FMT.setTimeZone(utc);
        GPS_DATE_FMT = new SimpleDateFormat("yyyy:MM:dd", Locale.US);
        GPS_DATE_FMT.setTimeZone(utc);
    }

    private static synchronized String formatCaptureStamp() {
        return CAPTURE_STAMP_FMT.format(new Date());
    }

    private static synchronized String formatMonthFolder() {
        return MONTH_FOLDER_FMT.format(new Date());
    }

    private static synchronized String formatExifTimestamp() {
        return EXIF_STAMP_FMT.format(new Date());
    }

    private static synchronized String formatGpsTime(Date date) {
        return GPS_TIME_FMT.format(date);
    }

    private static synchronized String formatGpsDate(Date date) {
        return GPS_DATE_FMT.format(date);
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
            Date date = new Date(loc.getTime());
            exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, formatGpsTime(date));
            exif.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, formatGpsDate(date));
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

    private void writeDng(Image image, TotalCaptureResult result, CameraCharacteristics captureCharacteristics, int orientation, String name, String subfolder, Location loc) throws Exception {
        if (result == null || captureCharacteristics == null) throw new IllegalStateException("Missing capture metadata");
        MediaOutput target = createOutput(name, "image/x-adobe-dng", subfolder);
        boolean published = false;
        DngCreator creator = null;
        try {
            creator = new DngCreator(captureCharacteristics, result);
            creator.setOrientation(CaptureOrientation.toExif(orientation));
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
        writeJpeg(bitmap, name, result, subfolder, false, null);
    }

    private void writeJpeg(Bitmap bitmap, String name, TotalCaptureResult result, String subfolder,
            boolean hasPrefetchedLocation, Location prefetchedLocation) throws Exception {
        MediaOutput target = createOutput(name, "image/jpeg", subfolder);
        boolean published = false;
        File temp = File.createTempFile("lika_export_", ".jpg", getCacheDir());
        try {
            int quality = CaptureFileNaming.resolveJpegQuality(
                    AppSettings.get(this, AppSettings.JPEG_QUALITY, "High · 95% (Recommended)"));

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
                exif.setAttribute(ExifInterface.TAG_ORIENTATION, String.valueOf(ExifInterface.ORIENTATION_NORMAL));
                exif.setAttribute(ExifInterface.TAG_DATETIME, formatExifTimestamp());

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

                boolean writeLocation = AppSettings.getBoolean(this, AppSettings.LOCATION, true);
                if (!writeLocation) {
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
                    Location loc = hasPrefetchedLocation ? prefetchedLocation : getCaptureLocation();
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

    private MediaOutput createOutput(String name, String mime, String subfolder) throws Exception {
        return MediaOutput.allocate(this, name, mime,
                Environment.DIRECTORY_PICTURES + "/Lika Lens" + (subfolder.isEmpty() ? "" : "/" + subfolder), true);
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
                        String resolvedBrain = (look.brain != null && !look.brain.startsWith("[RECIPE "))
                                ? look.brain : AppSettings.BRAIN_LIKA_FILM;
                        AppSettings.prefs(this).edit().putString(AppSettings.BRAIN, resolvedBrain).apply();
                        showMessage("APPLIED " + picked);
                        updateBrainButton();
                        updateQuickButtons();
                        updateContextualStatus();
                        populateQuickControls();
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
        if (look == null) return;
        SharedPreferences.Editor editor = AppSettings.prefs(this).edit();
        if (look.lutName != null) {
            editor.putString(AppSettings.LUT_NAME, look.lutName);
        }
        if (look.filmPushPull != null) {
            editor.putString(AppSettings.FILM_PUSH_PULL, look.filmPushPull);
        }
        if (look.grainStructure != null) {
            editor.putString(AppSettings.GRAIN_STRUCTURE, look.grainStructure);
        }
        if (look.grainResponse != null) {
            editor.putString(AppSettings.GRAIN_RESPONSE, look.grainResponse);
        }
        if (look.toneCurve != null) {
            editor.putString(AppSettings.TONE_CURVE, look.toneCurve);
        }
        if (look.shadowProfile != null) {
            editor.putString(AppSettings.SHADOW_PROFILE, look.shadowProfile);
        }
        if (look.highlightRecovery != null) {
            editor.putString(AppSettings.HIGHLIGHT_RECOVERY, look.highlightRecovery);
        }
        if (look.skinProtectionMode != null) {
            editor.putString(AppSettings.SKIN_PROTECTION_MODE, look.skinProtectionMode);
        } else {
            editor.putString(AppSettings.SKIN_PROTECTION_MODE,
                    look.skinProtection ? AppSettings.OVERRIDE_AUTO : AppSettings.OVERRIDE_OFF);
        }
        if (look.smartVibranceMode != null) {
            editor.putString(AppSettings.SMART_VIBRANCE_MODE, look.smartVibranceMode);
        } else {
            editor.putString(AppSettings.SMART_VIBRANCE_MODE,
                    look.smartVibrance ? AppSettings.OVERRIDE_AUTO : AppSettings.OVERRIDE_OFF);
        }
        int wbKelvin = Math.max(0, look.whiteBalanceKelvin);
        editor.putInt(AppSettings.EXPOSURE, Math.max(-20, Math.min(20, Math.round(look.exposureEv * 10f))))
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
                .putInt(AppSettings.LUT_INTENSITY, Math.max(0, Math.min(100, Math.round(look.lutIntensity * 100f))))
                .putInt(AppSettings.WHITE_BALANCE, wbKelvin)
                .putString(AppSettings.WHITE_BALANCE_MODE, AppSettings.formatWhiteBalanceMode(wbKelvin))
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
        FrameLayout scrim = new FrameLayout(this);
        LinearLayout sheet = new LinearLayout(this);
        Dialog dialog = UiKit.createBottomSheetDialog(this, scrim, sheet, 600);
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

    private boolean isCaptureProcessing() {
        return stillCaptureState == StillCaptureState.CAPTURING
                || stillCaptureState == StillCaptureState.SCREEN_FLASH
                || captureJobs.size() > 0;
    }

    private void updateGalleryProcessingUi() {
        if (galleryContainer == null) return;
        boolean processing = isCaptureProcessing();
        if (galleryProcessingScrim != null) {
            galleryProcessingScrim.setVisibility(processing ? View.VISIBLE : View.GONE);
        }
        if (galleryProcessingSpinner != null) {
            galleryProcessingSpinner.setVisibility(processing ? View.VISIBLE : View.GONE);
        }
        galleryContainer.setForeground(round(0x00000000, 14,
                processing ? ACCENT : 0x777d8580, dp(processing ? 2 : 1)));
    }

    private Bitmap createGalleryThumbnail(Bitmap source) {
        if (source == null || source.isRecycled()) return null;
        int w = source.getWidth();
        int h = source.getHeight();
        if (w <= 0 || h <= 0) return null;
        int maxDim = 192;
        if (w <= maxDim && h <= maxDim) {
            Bitmap copy = source.copy(Bitmap.Config.RGB_565, false);
            return copy != null ? copy : Bitmap.createScaledBitmap(source, w, h, true);
        }
        float scale = Math.min(maxDim / (float) w, maxDim / (float) h);
        int targetW = Math.max(1, Math.round(w * scale));
        int targetH = Math.max(1, Math.round(h * scale));
        return Bitmap.createScaledBitmap(source, targetW, targetH, true);
    }

    private void applyGalleryThumbnail(Bitmap thumbnail) {
        if (galleryButton == null || isFinishing() || isDestroyed()) {
            if (thumbnail != null && !thumbnail.isRecycled()) thumbnail.recycle();
            return;
        }
        if (thumbnail == null) {
            updateGalleryButton();
            return;
        }
        galleryButton.setImageBitmap(thumbnail);
        if (galleryContainer != null) {
            galleryContainer.animate().cancel();
            galleryContainer.setScaleX(0.86f);
            galleryContainer.setScaleY(0.86f);
            galleryContainer.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(180)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
    }

    private void updateGalleryButton() {
        if (isFinishing() || isDestroyed() || ioExecutor.isShutdown()) return;
        if (galleryButton == null) return;
        ioExecutor.execute(() -> {
            PhotoRepository.Photo latest = PhotoRepository.latest(this);
            if (latest == null) {
                runOnUiThread(() -> {
                    if (galleryButton != null && !isFinishing() && !isDestroyed()) {
                        galleryButton.setImageResource(R.drawable.ic_lika_lens);
                    }
                });
                return;
            }
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream stream = getContentResolver().openInputStream(latest.uri)) {
                if (stream == null) return;
                BitmapFactory.decodeStream(stream, null, bounds);
            } catch (Exception e) {
                Log.w(TAG, "Failed to inspect latest photo bounds for gallery button", e);
                return;
            }
            int sample = 1;
            while (bounds.outWidth > 0 && bounds.outHeight > 0
                    && bounds.outWidth / (sample * 2) >= 192
                    && bounds.outHeight / (sample * 2) >= 192) {
                sample *= 2;
            }
            try (InputStream stream = getContentResolver().openInputStream(latest.uri)) {
                if (stream == null) return;
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = sample;
                options.inPreferredConfig = Bitmap.Config.RGB_565;
                Bitmap decoded = BitmapFactory.decodeStream(stream, null, options);
                Bitmap thumbnail = CaptureOrientation.orientBitmap(this, latest.uri, decoded);
                if (thumbnail != null) {
                    runOnUiThread(() -> {
                        if (galleryButton != null && !isFinishing() && !isDestroyed()) {
                            galleryButton.setImageBitmap(thumbnail);
                        } else if (!thumbnail.isRecycled()) {
                            thumbnail.recycle();
                        }
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

    private final Paint legacyPaint = new Paint();
    private final ColorMatrix legacyCm = new ColorMatrix();
    private final ColorMatrix legacyAuxCm = new ColorMatrix();
    private final ColorMatrix legacyBeforeLutCm = new ColorMatrix();
    private final float[] legacyMatrixBuf = new float[20];
    private final float[] legacyBlended = new float[20];

    private void concatLegacyMatrix(float m00, float m01, float m02, float m04,
            float m10, float m11, float m12, float m14,
            float m20, float m21, float m22, float m24) {
        float[] m = legacyMatrixBuf;
        m[0] = m00; m[1] = m01; m[2] = m02; m[3] = 0f; m[4] = m04;
        m[5] = m10; m[6] = m11; m[7] = m12; m[8] = 0f; m[9] = m14;
        m[10] = m20; m[11] = m21; m[12] = m22; m[13] = 0f; m[14] = m24;
        m[15] = 0f; m[16] = 0f; m[17] = 0f; m[18] = 1f; m[19] = 0f;
        legacyAuxCm.set(m);
        legacyCm.postConcat(legacyAuxCm);
    }

    private void applyLiveFilmPreview() {
        if (!activityResumed || textureView == null || !textureView.isAvailable()) return;
        if (!prefLiveFilmPreview) {
            liveLookPreview.clear();
            textureView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            textureView.invalidate();
            return;
        }
        RawDeveloper.Look look = RawDeveloper.Look.fromPreferences(this);
        if (!liveLookPreview.apply(textureView, look, previewColourMetadata, () -> applyLegacyFilmPreview(look))) {
            applyLegacyFilmPreview(look);
        }
    }

    private void applyLegacyFilmPreview() {
        applyLegacyFilmPreview(RawDeveloper.Look.fromPreferences(this));
    }

    private void applyLegacyFilmPreview(RawDeveloper.Look look) {
        String brain = look.brain != null ? look.brain : AppSettings.brain(this);
        String activeLut = look.lutName != null ? look.lutName : "None";
        String pp = look.filmPushPull != null ? look.filmPushPull : "";
        boolean hasPushPull = pp.startsWith("+1") || pp.startsWith("+2") || pp.startsWith("-1");
        if (AppSettings.BRAIN_NEUTRAL_RAW.equals(brain) && "None".equalsIgnoreCase(activeLut)
                && !isCustomLookActive(look) && look.whiteBalanceKelvin <= 0 && !hasPushPull) {
            textureView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            textureView.invalidate();
            return;
        }

        ColorMatrix cm = legacyCm;
        cm.reset();
        if (brain.startsWith("MONO") || brain.contains("SILVER")) {
            // Panchromatic silver halide luma (Tri-X 400): elevated red/orange response + silver contrast
            float scale = 1.14f;
            float translate = (-0.42f * scale + 0.42f) * 255f - 2f;
            float rW = 0.33f * scale, gW = 0.59f * scale, bW = 0.08f * scale;
            concatLegacyMatrix(
                    rW, gW, bW, translate,
                    rW, gW, bW, translate,
                    rW, gW, bW, translate);
        } else if (brain.contains("KODACHROME")) {
            cm.setSaturation(1.18f);
            concatLegacyMatrix(
                    1.14f, -0.04f, -0.02f, -4f,
                    0.01f,  1.04f,  0.01f, -6f,
                   -0.04f,  0.01f,  1.06f, -10f);
        } else if (brain.contains("FUJI") || brain.contains("ETERNA")) {
            cm.setSaturation(0.84f);
            concatLegacyMatrix(
                    0.88f,  0.03f,  0.01f,  8f,
                   -0.02f,  0.92f,  0.02f, 12f,
                   -0.01f,  0.04f,  0.91f, 14f);
        } else if (brain.contains("BLEACH")) {
            cm.setSaturation(0.30f);
            float scale = 1.28f;
            float translate = (-0.42f * scale + 0.42f) * 255f - 8f;
            concatLegacyMatrix(
                    scale, 0f, 0f, translate - 3f,
                    0f, scale, 0f, translate,
                    0f, 0f, scale, translate + 5f);
        } else if (!AppSettings.BRAIN_NEUTRAL_RAW.equals(brain)) {
            // LIKA / FILM: Kodak Vision3 500T filmic density with warm midtones and clean shadow gradation
            concatLegacyMatrix(
                    1.04f, -0.02f, -0.01f,  2f,
                   -0.01f,  1.01f,  0.01f,  0f,
                   -0.02f,  0.03f,  0.98f, -2f);
        }

        // Incorporate user developer adjustments (exposureEv, contrast, shadows, warmth, saturation, manual Kelvin WB, push/pull)
        float evDelta = look.exposureEv;
        float evScale = (float) Math.pow(2.0, evDelta * 0.65f);
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
            legacyAuxCm.setSaturation(Math.max(0f, Math.min(1.8f, satRatio)));
            cm.postConcat(legacyAuxCm);
        }
        if (Math.abs(evScale - 1.0f) > 0.01f || Math.abs(contrastRatio - 1.0f) > 0.01f
                || Math.abs(shadowDelta) > 0.01f || Math.abs(warmthDelta) > 0.01f) {
            float combinedScale = evScale * (0.35f + 0.65f * contrastRatio);
            float pivotOffset = (1.0f - contrastRatio) * 95f - shadowDelta * 28f;
            float rScale = combinedScale * (1.0f + warmthDelta * 0.07f);
            float gScale = combinedScale * (1.0f + warmthDelta * 0.01f);
            float bScale = combinedScale * (1.0f - warmthDelta * 0.09f);
            float rOff = pivotOffset + warmthDelta * 7f;
            float gOff = pivotOffset + warmthDelta * 2f;
            float bOff = pivotOffset - warmthDelta * 9f;
            concatLegacyMatrix(
                    rScale, 0f, 0f, rOff,
                    0f, gScale, 0f, gOff,
                    0f, 0f, bScale, bOff);
        }

        // Legacy devices retain a matrix approximation; honour the actual LUT strength.
        legacyBeforeLutCm.set(cm);
        // Apply active 3D LUT matrix in viewfinder
        if (LutManager.LUT_PORTRA.equalsIgnoreCase(activeLut)) {
            concatLegacyMatrix(
                    1.12f,  0.03f, -0.04f,  6f,
                   -0.01f,  1.05f, -0.01f,  3f,
                   -0.06f, -0.03f,  0.94f, -4f);
        } else if (LutManager.LUT_FUJI.equalsIgnoreCase(activeLut)) {
            concatLegacyMatrix(
                    0.96f, -0.01f,  0.03f, -2f,
                    0.01f,  1.04f,  0.02f,  5f,
                    0.03f,  0.02f,  1.06f,  8f);
        } else if (LutManager.LUT_TEAL_ORANGE.equalsIgnoreCase(activeLut)) {
            concatLegacyMatrix(
                    1.22f, -0.04f, -0.08f,  8f,
                   -0.02f,  1.02f, -0.04f,  2f,
                   -0.12f,  0.06f,  1.18f, -6f);
        } else if (LutManager.LUT_ANSEL_BW.equalsIgnoreCase(activeLut)) {
            legacyAuxCm.setSaturation(0f);
            cm.postConcat(legacyAuxCm);
            float scale = 1.30f;
            float tr = (-0.5f * scale + 0.5f) * 255f;
            concatLegacyMatrix(
                    scale, 0f, 0f, tr,
                    0f, scale, 0f, tr,
                    0f, 0f, scale, tr);
        } else if (LutManager.LUT_AMBER_70S.equalsIgnoreCase(activeLut)) {
            concatLegacyMatrix(
                    1.20f,  0.04f, -0.04f, 10f,
                    0.02f,  1.06f, -0.02f,  4f,
                   -0.10f, -0.06f,  0.80f, -8f);
        } else if (LutManager.LUT_BLEACH.equalsIgnoreCase(activeLut)) {
            legacyAuxCm.setSaturation(0.32f);
            cm.postConcat(legacyAuxCm);
            float scale = 1.35f;
            float tr = (-0.5f * scale + 0.5f) * 255f;
            concatLegacyMatrix(
                    scale, 0f, 0f, tr,
                    0f, scale, 0f, tr,
                    0f, 0f, scale, tr);
        }

        float lutMix = Math.max(0f, Math.min(1f, look.lutIntensity));
        float[] before = legacyBeforeLutCm.getArray(), after = cm.getArray();
        for (int i = 0; i < legacyBlended.length; i++) {
            legacyBlended[i] = before[i] + (after[i] - before[i]) * lutMix;
        }
        cm.set(legacyBlended);
        legacyPaint.setColorFilter(new ColorMatrixColorFilter(cm));
        textureView.setLayerType(View.LAYER_TYPE_HARDWARE, legacyPaint);
        textureView.invalidate();
    }

    private boolean isNightStackingActive() {
        String astroSetting = AppSettings.get(this, AppSettings.ASTROPHOTOGRAPHY, "Auto (when stationary at night)");
        RawArchive.Frame meteredPreview = previewColourMetadata;
        long meteredExposure = meteredPreview == null ? 0L : meteredPreview.exposureTimeNs;
        int meteredIso = meteredPreview == null ? 0 : meteredPreview.sensitivityIso;
        boolean manualExposure = AppSettings.get(this, AppSettings.EXPOSURE_MODE, "Auto exposure").startsWith("Manual");
        String flashMode = AppSettings.get(this, AppSettings.FLASH, "Off");
        return CameraExposureHelper.shouldUseNightIntegration(
                astroSetting, motion.stationary(), manualExposure, meteredExposure, meteredIso, flashMode);
    }

    private void updateContextualStatus() {
        if (modeNote == null) return;
        final String status;
        if (videoMode) {
            String fps = AppSettings.get(this, AppSettings.VIDEO_FPS, "24 fps (Cinematic)");
            String fpsTag = fps.startsWith("24") ? "24 FPS CINEMATIC"
                    : fps.startsWith("25") ? "25 FPS PAL"
                    : fps.startsWith("30") ? "30 FPS" : "60 FPS";
            status = videoState.isRecordingOrPaused() ? "TAP ⟳ TO SWITCH" : "CINEMATIC VIDEO · " + fpsTag;
        } else {
            boolean easy = AppSettings.getBoolean(this, AppSettings.EASY_MODE, true);
            if (isNightStackingActive()) {
                status = "✨ ASTRO / NIGHT STACKING";
            } else if (isCustomLookActive()) {
                status = "CUSTOM LOOK ACTIVE";
            } else if (easy) {
                status = "INTELLIGENT AUTO";
            } else {
                status = "MANUAL PHOTO · TAP HUD";
            }
        }
        CharSequence currentText = modeNote.getText();
        if (currentText == null || !status.contentEquals(currentText)) {
            modeNote.setText(status);
        }
    }

    private boolean isCustomLookActive() {
        String grainStruct = AppSettings.get(this, AppSettings.GRAIN_STRUCTURE, AppSettings.GRAIN_STRUCTURE_CHOICES[0]);
        if ("Monochrome".equals(grainStruct)) grainStruct = AppSettings.GRAIN_STRUCTURE_CHOICES[0];
        return AppSettings.getInt(this, AppSettings.EXPOSURE, AppSettings.DEFAULT_EXPOSURE) != AppSettings.DEFAULT_EXPOSURE
                || AppSettings.getInt(this, AppSettings.WARMTH, AppSettings.DEFAULT_WARMTH) != AppSettings.DEFAULT_WARMTH
                || AppSettings.getInt(this, AppSettings.CONTRAST, AppSettings.DEFAULT_CONTRAST) != AppSettings.DEFAULT_CONTRAST
                || AppSettings.getInt(this, AppSettings.PIVOT, AppSettings.DEFAULT_PIVOT) != AppSettings.DEFAULT_PIVOT
                || AppSettings.getInt(this, AppSettings.SATURATION, AppSettings.DEFAULT_SATURATION) != AppSettings.DEFAULT_SATURATION
                || AppSettings.getInt(this, AppSettings.COLOR_ROLLOFF, AppSettings.DEFAULT_COLOR_ROLLOFF) != AppSettings.DEFAULT_COLOR_ROLLOFF
                || AppSettings.getInt(this, AppSettings.HIGHLIGHTS, AppSettings.DEFAULT_HIGHLIGHTS) != AppSettings.DEFAULT_HIGHLIGHTS
                || AppSettings.getInt(this, AppSettings.SHADOWS, AppSettings.DEFAULT_SHADOWS) != AppSettings.DEFAULT_SHADOWS
                || AppSettings.getInt(this, AppSettings.GRAIN, AppSettings.DEFAULT_GRAIN) != AppSettings.DEFAULT_GRAIN
                || AppSettings.getInt(this, AppSettings.HALATION, AppSettings.DEFAULT_HALATION) != AppSettings.DEFAULT_HALATION
                || AppSettings.getInt(this, AppSettings.SHARPEN, AppSettings.DEFAULT_SHARPEN) != AppSettings.DEFAULT_SHARPEN
                || AppSettings.getInt(this, AppSettings.COLOR_NR, AppSettings.DEFAULT_COLOR_NR) != AppSettings.DEFAULT_COLOR_NR
                || AppSettings.getInt(this, AppSettings.VIGNETTE, AppSettings.DEFAULT_VIGNETTE) != AppSettings.DEFAULT_VIGNETTE
                || AppSettings.getInt(this, AppSettings.LUT_INTENSITY, AppSettings.DEFAULT_LUT_INTENSITY) != AppSettings.DEFAULT_LUT_INTENSITY
                || !AppSettings.GRAIN_STRUCTURE_CHOICES[0].equals(grainStruct)
                || !"Filmic S-Curve".equals(AppSettings.get(this, AppSettings.TONE_CURVE, "Filmic S-Curve"))
                || !AppSettings.SHADOW_PROFILE_AUTO.equals(AppSettings.get(this, AppSettings.SHADOW_PROFILE, AppSettings.SHADOW_PROFILE_AUTO))
                || !AppSettings.HL_REC_AUTO.equals(AppSettings.get(this, AppSettings.HIGHLIGHT_RECOVERY, AppSettings.HL_REC_AUTO))
                || AppSettings.resolveWhiteBalanceKelvin(this) > 0;
    }

    private static boolean isCustomLookActive(RawDeveloper.Look look) {
        if (look == null) return false;
        String grainStruct = "Monochrome".equals(look.grainStructure) ? AppSettings.GRAIN_STRUCTURE_CHOICES[0] : look.grainStructure;
        return Math.round(look.exposureEv * 10f) != AppSettings.DEFAULT_EXPOSURE
                || Math.round(look.warmth * 50f + 50f) != AppSettings.DEFAULT_WARMTH
                || Math.round(look.contrast * 100f) != AppSettings.DEFAULT_CONTRAST
                || Math.round(look.pivot * 100f) != AppSettings.DEFAULT_PIVOT
                || Math.round(look.saturation * 100f) != AppSettings.DEFAULT_SATURATION
                || Math.round(look.colorRollOff * 100f) != AppSettings.DEFAULT_COLOR_ROLLOFF
                || Math.round(look.highlights * 100f) != AppSettings.DEFAULT_HIGHLIGHTS
                || Math.round(look.shadows * 100f) != AppSettings.DEFAULT_SHADOWS
                || Math.round(look.grain * 100f) != AppSettings.DEFAULT_GRAIN
                || Math.round(look.halation * 100f) != AppSettings.DEFAULT_HALATION
                || Math.round(look.sharpening * 100f) != AppSettings.DEFAULT_SHARPEN
                || Math.round(look.chromaNr * 100f) != AppSettings.DEFAULT_COLOR_NR
                || Math.round(look.vignette * 100f) != AppSettings.DEFAULT_VIGNETTE
                || Math.round(look.lutIntensity * 100f) != AppSettings.DEFAULT_LUT_INTENSITY
                || (grainStruct != null && !AppSettings.GRAIN_STRUCTURE_CHOICES[0].equals(grainStruct))
                || (look.toneCurve != null && !"Filmic S-Curve".equals(look.toneCurve))
                || (look.shadowProfile != null && !AppSettings.SHADOW_PROFILE_AUTO.equals(look.shadowProfile))
                || (look.highlightRecovery != null && !AppSettings.HL_REC_AUTO.equals(look.highlightRecovery))
                || look.whiteBalanceKelvin > 0;
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
                    selectCameraForLens(cameraId, frontWideZoom,
                            CameraExposureHelper.shouldUseJpegForWideZoom(rawSupported, frontWideZoom));
                    return;
                }
                physicalFocalFactor = 1f;
                selectCameraForLens(cameraId, Math.max(1f, factor), false);
                return;
            }

            // If the current camera can handle this zoom level natively, switch session streams if needed without restarting the device
            if (mainCameraId.equals(cameraId) && canZoomOnCurrentCamera(factor)) {
                physicalFocalFactor = 1f;
                float targetZoom = factor;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && characteristics != null) {
                    Range<Float> range = characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
                    if (range != null) {
                        targetZoom = Math.max(factor, range.getLower());
                    }
                }
                selectCameraForLens(mainCameraId, targetZoom,
                        CameraExposureHelper.shouldUseJpegForWideZoom(rawSupported, targetZoom));
                return;
            }

            // If we are currently on an auxiliary camera, return to mainCameraId (or telephoto) when factor >= 1f
            if (!mainCameraId.equals(cameraId) && factor >= 1f) {
                if (factor > 1.05f) {
                    chooseContinuousLens(factor);
                } else {
                    selectCameraForLens(mainCameraId, factor, false);
                }
                return;
            }

            // Prefer a real wider RAW camera when the device exposes one and mainCameraId cannot zoom wide.
            if (factor < 1f) {
                CameraCharacteristics main = cameraManager.getCameraCharacteristics(mainCameraId);
                float logicalWideZoom = supportedWideZoom(main, factor);
                if (logicalWideZoom > 0f) {
                    physicalFocalFactor = 1f;
                    StreamConfigurationMap mainMap = main.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    boolean mainRaw = isRawCapable(main, mainMap);
                    selectCameraForLens(mainCameraId, logicalWideZoom,
                            CameraExposureHelper.shouldUseJpegForWideZoom(mainRaw, logicalWideZoom));
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
                    StreamConfigurationMap map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    boolean raw = isRawCapable(c, map);
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

            if (factor > 1.05f) {
                chooseContinuousLens(factor);
            } else {
                selectCameraForLens(mainCameraId, factor, false);
            }
        } catch (CameraAccessException e) {
            showMessage("Lens selection is unavailable.");
        }
    }

    private boolean canZoomOnCurrentCamera(float factor) {
        if (characteristics == null) return false;
        Float minRatio = null;
        Float maxRatio = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Range<Float> range = characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
            if (range != null) {
                minRatio = range.getLower();
                maxRatio = range.getUpper();
            }
        }
        return CameraExposureHelper.canZoomOnCamera(
                true, minRatio, maxRatio, mainCameraId != null && mainCameraId.equals(cameraId), factor);
    }

    private float supportedWideZoom(CameraCharacteristics camera, float factor) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || camera == null) return -1f;
        Range<Float> range = camera.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
        if (range == null) return -1f;
        return CameraExposureHelper.resolveWideZoomRatio(range.getLower(), range.getUpper(), factor);
    }

    private void selectCameraForLens(String id, float factor, boolean jpegWide) {
        if (id == null) return;
        float resolvedFactor = CameraExposureHelper.resolveZoomForSessionMode(factor, jpegWide);
        boolean mustRestart = !id.equals(cameraId);
        if (mustRestart && videoState.isRecordingOrPaused() && !canContinueVideoOnCamera(id)) {
            updateLensButtons(requestedZoom);
            return;
        }
        AppSettings.prefs(this).edit().putString(AppSettings.LAST_CAMERA_ID, id).apply();
        boolean prevForceJpegForWide = forceJpegForWide;
        boolean mustReconfigureSession = CameraExposureHelper.needsPreviewSessionReconfig(
                cameraId, id, prevForceJpegForWide, jpegWide);
        if (!videoMode && AppSettings.shouldSaveDng(AppSettings.get(this, AppSettings.PHOTO_OUTPUT, AppSettings.PHOTO_OUTPUT_JPEG_ONLY))) {
            boolean targetHasRaw = true;
            if (jpegWide) {
                targetHasRaw = false;
            } else if (cameraManager != null && mustRestart) {
                try {
                    CameraCharacteristics targetChars = cameraManager.getCameraCharacteristics(id);
                    StreamConfigurationMap targetMap = targetChars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    targetHasRaw = isRawCapable(targetChars, targetMap);
                } catch (CameraAccessException ignored) {
                }
            }
            if (!targetHasRaw && (mustRestart || !prevForceJpegForWide)) {
                showMessage("JPEG fallback on this lens · RAW unavailable here");
            }
        }
        boolean isInteractive = gesture.is(InteractionState.Gesture.Mode.PINCH)
                || gesture.is(InteractionState.Gesture.Mode.ZOOM_SLIDER)
                || gesture.is(InteractionState.Gesture.Mode.ZOOM_ROW);
        if (mustRestart) {
            forceJpegForWide = jpegWide;
            pendingPostConfigZoom = -1f;
            if (prefHaptics && captureButton != null) {
                captureButton.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
            }
            requestedZoom = resolvedFactor;
            updateLensButtons(resolvedFactor);
            if (textureView != null) textureView.setAlpha(.82f);
            cameraId = id;
            restartCameraForLens(resolvedFactor);
        } else if (mustReconfigureSession) {
            if (videoState.isRecordingOrPaused() || videoState.isPreparing() || videoState.isSwitchingCamera()) {
                pendingPostConfigZoom = -1f;
                forceJpegForWide = jpegWide;
                animateZoomTo(resolvedFactor);
            } else if (isInteractive) {
                forceJpegForWide = jpegWide;
                reconfigureSessionForZoom(resolvedFactor, true);
            } else {
                float preZoomTarget = CameraExposureHelper.preReconfigAnimationTarget(
                        prevForceJpegForWide, jpegWide, requestedZoom, resolvedFactor);
                if (preZoomTarget > 0f && captureSession != null && previewRequest != null) {
                    long animMs = jpegWide ? 130L : 220L;
                    animateZoomTo(preZoomTarget, animMs, () -> {
                        if (!activityResumed || !id.equals(cameraId)) return;
                        forceJpegForWide = jpegWide;
                        reconfigureSessionForZoom(resolvedFactor, false);
                    }, false);
                } else {
                    forceJpegForWide = jpegWide;
                    reconfigureSessionForZoom(resolvedFactor, false);
                }
            }
        } else {
            forceJpegForWide = jpegWide;
            animateZoomTo(resolvedFactor);
        }
    }

    private void reconfigureSessionForZoom(float targetFactor, boolean interactiveGesture) {
        if (zoomAnimator != null) {
            zoomAnimator.cancel();
            zoomAnimator = null;
        }
        float sessionStartZoom = CameraExposureHelper.sessionInitialZoomForReconfig(
                forceJpegForWide, requestedZoom, targetFactor, interactiveGesture);
        pendingPostConfigZoom = CameraExposureHelper.postReconfigZoomTarget(
                forceJpegForWide, sessionStartZoom, targetFactor, interactiveGesture);
        requestedZoom = sessionStartZoom;
        updateLensButtons(targetFactor);
        useRawForThisCamera = rawSupported && !forceJpegForWide;
        if (cameraLifecycleState == CameraLifecycleState.OPENING) {
            pendingPostConfigZoom = -1f;
            requestedZoom = targetFactor;
            if (textureView != null) textureView.setAlpha(.82f);
            closeCamera(videoState.isSwitchingCamera());
            openCameraIfReady();
            return;
        }
        if (cameraDevice == null || characteristics == null
                || cameraLifecycleState == CameraLifecycleState.CLOSED
                || cameraLifecycleState == CameraLifecycleState.CLOSING
                || cameraLifecycleState == CameraLifecycleState.ERROR) {
            pendingPostConfigZoom = -1f;
            requestedZoom = targetFactor;
            return;
        }
        recreateCurrentPreviewSession("Wide lens preview could not start on this camera.", true);
    }

    private void restartCameraForLens(float factor) {
        if (videoState.isRecordingOrPaused()) {
            pauseCurrentSegmentForCameraSwitch();
        }
        requestedZoom = factor;
        updateLensButtons(factor);
        if (textureView != null) textureView.setAlpha(.82f);
        closeCamera(true);
        openCameraIfReady();
    }

    private void setInteractiveZoom(float factor) {
        if (videoLock.locked() || videoState.isPreparing() || videoState.isSwitchingCamera()) return;
        requestedZoom = Math.max(MIN_INTERACTIVE_ZOOM, Math.min(MAX_INTERACTIVE_ZOOM, factor));
        updateLensButtons(requestedZoom);
        if (!videoState.isRecordingOrPaused()
                && !forceJpegForWide
                && CameraExposureHelper.shouldUseJpegForWideZoom(rawSupported, requestedZoom)
                && canZoomOnCurrentCamera(requestedZoom)) {
            float wideTarget = supportedWideZoom(characteristics, requestedZoom);
            selectCameraForLens(cameraId, wideTarget > 0f ? wideTarget : requestedZoom, true);
            return;
        }
        submitRepeatingZoomUpdate();
    }

    private void finishInteractiveZoom() {
        if (videoLock.locked() || videoState.isPreparing() || videoState.isSwitchingCamera()) return;
        if (canZoomOnCurrentCamera(requestedZoom) && mainCameraId != null && mainCameraId.equals(cameraId)) {
            boolean wantJpegWide = CameraExposureHelper.shouldUseJpegForWideZoom(rawSupported, requestedZoom);
            if (wantJpegWide != forceJpegForWide) {
                float target = wantJpegWide ? supportedWideZoom(characteristics, requestedZoom) : requestedZoom;
                selectCameraForLens(cameraId, target > 0f ? target : requestedZoom, wantJpegWide);
            } else if (!wantJpegWide && requestedZoom < 1.0f) {
                animateZoomTo(CameraExposureHelper.resolveZoomForSessionMode(requestedZoom, false));
            }
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
            List<CameraExposureHelper.LensCandidate> candidates = collectLensCandidates();
            CameraExposureHelper.LensCandidate primary = null;
            for (CameraExposureHelper.LensCandidate c : candidates) {
                if (mainCameraId.equals(c.id)) {
                    primary = c;
                    break;
                }
            }
            if (primary == null) {
                primary = CameraExposureHelper.selectPrimaryBackCamera(candidates);
            }
            CameraExposureHelper.LensCandidate best =
                    CameraExposureHelper.selectContinuousPhysicalLens(candidates, primary, factor);
            if (best != null) {
                physicalFocalFactor = (best.isBack && primary != null) ? (best.focalLength / primary.focalLength) : 1f;
                selectCameraForLens(best.id, factor, false);
            } else {
                physicalFocalFactor = 1f;
                selectCameraForLens(mainCameraId, factor, false);
            }
        } catch (CameraAccessException e) {
            showMessage("Zoom selection is unavailable.");
        }
    }

    private void syncPortraitZoomSliderClearance() {
        if (zoomSliderPanel == null || textureView == null) return;
        ViewGroup.LayoutParams lp = zoomSliderPanel.getLayoutParams();
        if (lp instanceof FrameLayout.LayoutParams && zoomSliderPanel.getParent() == overlay) {
            RectF frame = framingRect(textureView.getWidth(), textureView.getHeight());
            if (frame.bottom > 0) {
                boolean mfOpen = manualFocusPanel != null && manualFocusPanel.getVisibility() == View.VISIBLE;
                int desiredTop = (int) (frame.bottom - dp(44) - dp(12) - (mfOpen ? dp(84) : dp(40)));
                FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) lp;
                if (flp.topMargin != desiredTop) {
                    flp.topMargin = desiredTop;
                    zoomSliderPanel.setLayoutParams(flp);
                }
            }
        }
    }

    private void showZoomSlider() {
        if (zoomSliderPanel == null) return;
        zoomSliderPanel.removeCallbacks(hideZoomSlider);
        syncPortraitZoomSliderClearance();
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

    private void clearPendingPostConfigZoom() {
        pendingPostConfigZoom = -1f;
        pendingPostConfigSessionTicket = -1L;
        Runnable fallback = pendingPostConfigFallback;
        pendingPostConfigFallback = null;
        if (fallback != null) {
            cameraUiHandler.removeCallbacks(fallback);
        }
    }

    private void triggerPendingPostConfigZoom(CameraCaptureSession session, long ticket) {
        final float postZoom = pendingPostConfigZoom;
        if (postZoom <= 0f || pendingPostConfigSessionTicket != ticket || !sessionEpoch.owns(ticket)) {
            return;
        }
        clearPendingPostConfigZoom();
        runOnUiThread(() -> {
            if (!activityResumed || isFinishing() || isDestroyed()
                    || !sessionEpoch.owns(ticket)
                    || captureSession != session
                    || previewRequest == null) {
                return;
            }
            animateZoomTo(postZoom, 300L, null, false);
        });
    }

    private void submitRepeatingZoomUpdate() {
        if (cameraHandler == null || previewRequest == null || captureSession == null) return;
        if (!pendingZoomRequest.compareAndSet(false, true)) return;
        cameraHandler.post(() -> {
            pendingZoomRequest.set(false);
            CameraCaptureSession session = captureSession;
            CaptureRequest.Builder req = previewRequest;
            if (session == null || req == null) return;
            try {
                applyZoom(req);
                session.setRepeatingRequest(req.build(), previewMetadataCallback, cameraHandler);
            } catch (CameraAccessException | IllegalStateException e) {
                Log.w(TAG, "Failed to update zoom request", e);
            }
        });
    }

    private void animateZoomTo(float targetZoom) {
        animateZoomTo(targetZoom, 280L, null, true);
    }

    private void animateZoomTo(float targetZoom, long durationMs, Runnable onComplete, boolean withHaptic) {
        if (withHaptic) hapticDetent();
        if (zoomAnimator != null) zoomAnimator.cancel();
        if (captureSession == null || previewRequest == null) {
            requestedZoom = targetZoom;
            updateLensButtons(targetZoom);
            if (onComplete != null) onComplete.run();
            return;
        }
        float startZoom = requestedZoom;
        if (Math.abs(startZoom - targetZoom) < 0.01f) {
            requestedZoom = targetZoom;
            updateLensButtons(targetZoom);
            if (onComplete != null) onComplete.run();
            return;
        }
        zoomAnimator = ValueAnimator.ofFloat(0f, 1f);
        zoomAnimator.setDuration(durationMs);
        zoomAnimator.setInterpolator(new android.view.animation.PathInterpolator(0.22f, 1.0f, 0.36f, 1.0f));
        final boolean[] cancelled = {false};
        zoomAnimator.addUpdateListener(animation -> {
            float fraction = (float) animation.getAnimatedValue();
            requestedZoom = CameraExposureHelper.interpolateZoom(startZoom, targetZoom, fraction);
            updateZoomSliderUi();
            submitRepeatingZoomUpdate();
        });
        zoomAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationCancel(android.animation.Animator animation) {
                cancelled[0] = true;
            }
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                if (cancelled[0]) return;
                requestedZoom = targetZoom;
                submitRepeatingZoomUpdate();
                updateLensButtons(targetZoom);
                updateZoomSliderUi();
                if (onComplete != null) onComplete.run();
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
                boolean hasRaw = isRawCapable(candidate, map);
                Size[] sizes = hasRaw ? map.getOutputSizes(ImageFormat.RAW_SENSOR) : map.getOutputSizes(ImageFormat.JPEG);
                long area = maxSizeArea(sizes);
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
            if (videoState.isRecordingOrPaused() && !canContinueVideoOnCamera(bestId)) {
                return;
            }
            if (videoState.isRecordingOrPaused()) {
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
            closeCamera(true);
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
        boolean wideAvailable = hasUltraWideSupport || (isFrontCamera() && supportedWideZoom(characteristics, 0.5f) > 0f);
        for (int i = 0; i < lensButtons.size(); i++) {
            TextView button = lensButtons.get(i);
            boolean active = (i == closestIndex);
            button.setAlpha((i == 0 && characteristics != null && !wideAvailable && !active) ? 0.50f : 1.0f);
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
        boolean isLockedTripod = stab.startsWith("Locked") && (videoState.isRecordingOrPaused() || videoState.isPreparing()) && hasLockedVideoAnchor;

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

        String audioPref = AppSettings.get(this, AppSettings.VIDEO_AUDIO, "Camcorder · Stereo");
        if ((videoState.isRecordingOrPaused() || videoState.isPreparing()) && mediaRecorder != null
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && audioZoomSupported
                && AppSettings.getBoolean(this, AppSettings.AUDIO_ZOOM, true)
                && !audioPref.startsWith("Unprocessed")
                && !audioPref.startsWith("Muted")
                && !audioPref.contains("Speech")
                && !audioPref.startsWith("Voice")) {
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
        if (characteristics == null || builder == null) return;
        int[] modes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
        if (isManualFocusActive()) {
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
            builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, currentManualFocusDiopters());
            Integer maxAf = characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF);
            if (maxAf != null && maxAf > 0) {
                builder.set(CaptureRequest.CONTROL_AF_REGIONS, null);
            }
            return;
        }
        if ((focusMode != FocusMode.CONTINUOUS) && currentAfRegions != null
                && hasCapability(modes, CaptureRequest.CONTROL_AF_MODE_AUTO)) {
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
            builder.set(CaptureRequest.CONTROL_AF_REGIONS, currentAfRegions);
            return;
        }
        if (modes == null) return;
        String preference = AppSettings.get(this, AppSettings.FOCUS_MODE, AppSettings.FOCUS_CONTINUOUS);
        int wanted = !videoMode && preference.startsWith("Single")
                ? CaptureRequest.CONTROL_AF_MODE_AUTO
                : (videoMode ? CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO : CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        if (hasCapability(modes, wanted)) {
            builder.set(CaptureRequest.CONTROL_AF_MODE, wanted);
        } else if (hasCapability(modes, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)) {
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        } else if (hasCapability(modes, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)) {
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
        } else if (hasCapability(modes, CaptureRequest.CONTROL_AF_MODE_AUTO)) {
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
        } else if (hasCapability(modes, CaptureRequest.CONTROL_AF_MODE_OFF)) {
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
        }
    }

    private boolean isManualFocusActive() {
        return !videoMode
                && manualFocusSupported
                && AppSettings.FOCUS_MANUAL.equals(AppSettings.get(this, AppSettings.FOCUS_MODE, AppSettings.FOCUS_CONTINUOUS));
    }

    private float currentManualFocusDiopters() {
        int progress = AppSettings.getInt(this, AppSettings.MANUAL_FOCUS_DISTANCE, ManualFocusHelper.SLIDER_MAX);
        return ManualFocusHelper.progressToDiopters(progress, minFocusDiopters);
    }

    private void updateManualFocusUi() {
        String pref = AppSettings.get(this, AppSettings.FOCUS_MODE, AppSettings.FOCUS_CONTINUOUS);
        if (!AppSettings.FOCUS_MANUAL.equals(pref)) {
            lastAutoFocusMode = pref;
        }
        boolean mfActive = isManualFocusActive();
        if (focusToggleButton != null) {
            focusToggleButton.setVisibility(videoMode ? View.GONE : View.VISIBLE);
            if (!manualFocusSupported && !videoMode) {
                focusToggleButton.setText("FIXED");
                focusToggleButton.setTextColor(0xffaab3ad);
                focusToggleButton.setBackground(round(0xd8191e1e, 22, 0x44606963, dp(1)));
            } else if (mfActive) {
                focusToggleButton.setText("MF");
                focusToggleButton.setTextColor(BG);
                focusToggleButton.setBackground(round(ACCENT, 22, ACCENT, dp(1)));
            } else if (AppSettings.FOCUS_SINGLE.equals(pref)) {
                focusToggleButton.setText("AF-S");
                focusToggleButton.setTextColor(ACCENT);
                focusToggleButton.setBackground(round(0xe21c1d1b, 22, ACCENT, dp(1)));
            } else {
                focusToggleButton.setText("AF-C");
                focusToggleButton.setTextColor(0xffe9ede6);
                focusToggleButton.setBackground(round(0xd8191e1e, 22, 0x556e7872, dp(1)));
            }
        }
        if (manualFocusPanel != null) {
            manualFocusPanel.setVisibility(mfActive ? View.VISIBLE : View.GONE);
            syncPortraitZoomSliderClearance();
        }
        if (mfActive) {
            int progress = AppSettings.getInt(this, AppSettings.MANUAL_FOCUS_DISTANCE, ManualFocusHelper.SLIDER_MAX);
            progress = Math.max(0, Math.min(ManualFocusHelper.SLIDER_MAX, progress));
            if (manualFocusSeekBar != null && manualFocusSeekBar.getProgress() != progress) {
                manualFocusSliderSyncing = true;
                manualFocusSeekBar.setProgress(progress);
                manualFocusSliderSyncing = false;
            }
            float diopters = ManualFocusHelper.progressToDiopters(progress, minFocusDiopters);
            if (manualFocusValue != null) {
                manualFocusValue.setText(ManualFocusHelper.formatFocusDistance(diopters, minFocusDiopters, focusDistanceCalibration));
            }
        }
    }

    private void updateLoupeButtonUi() {
        if (loupeToggleButton == null) return;
        boolean active = manualLoupeActive && !videoMode;
        int tint = active ? BG : 0xffe9ede6;
        loupeToggleButton.setBackground(round(active ? ACCENT : 0xd8191e1e, 22, active ? ACCENT : 0x556e7872, dp(1)));
        setPillIcon(loupeToggleButton, R.drawable.ic_focus_loupe, 15, tint, 4, "5×");
    }

    private void applyManualFocusToPreview(float diopters, boolean forceSend) {
        if (!isManualFocusActive() || previewRequest == null || captureSession == null || cameraHandler == null) return;
        long now = SystemClock.uptimeMillis();
        if (!forceSend && now - lastMfPreviewRequestAt < 33L) return;
        lastMfPreviewRequestAt = now;
        float clamped = ManualFocusHelper.clampedDiopters(diopters, minFocusDiopters);
        try {
            previewRequest.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
            previewRequest.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
            previewRequest.set(CaptureRequest.LENS_FOCUS_DISTANCE, clamped);
            captureSession.setRepeatingRequest(previewRequest.build(), previewMetadataCallback, cameraHandler);
        } catch (CameraAccessException | IllegalStateException e) {
            Log.w(TAG, "Failed to update manual focus distance", e);
        }
    }

    private void chooseFocusMode() {
        String mfSubtitle = manualFocusSupported
                ? "Lock AF and rack focus smoothly from near to infinity"
                : "Fixed-focus or unsupported on the active camera lens";
        String[] subtitles = {
                "Tracks subject movement continuously before capture",
                "Focuses on tap or when capturing a frame",
                mfSubtitle
        };
        String current = AppSettings.get(this, AppSettings.FOCUS_MODE, AppSettings.FOCUS_CONTINUOUS);
        int selected = 0;
        for (int i = 0; i < AppSettings.FOCUS_MODE_CHOICES.length; i++) {
            if (AppSettings.FOCUS_MODE_CHOICES[i].equals(current)) {
                selected = i;
                break;
            }
        }
        showCustomBottomSheet("FOCUS MODE", AppSettings.FOCUS_MODE_CHOICES, subtitles, selected,
                which -> activateFocusModePreference(AppSettings.FOCUS_MODE_CHOICES[which]));
    }

    private void toggleQuickManualFocus() {
        if (isManualFocusActive()) {
            exitManualFocusToAf();
        } else {
            activateFocusModePreference(AppSettings.FOCUS_MANUAL);
        }
    }

    private void exitManualFocusToAf() {
        String restore = AppSettings.FOCUS_SINGLE.equals(lastAutoFocusMode)
                ? AppSettings.FOCUS_SINGLE : AppSettings.FOCUS_CONTINUOUS;
        activateFocusModePreference(restore);
    }

    private void activateFocusModePreference(String targetMode) {
        if (AppSettings.FOCUS_MANUAL.equals(targetMode)) {
            if (!manualFocusSupported) {
                showMessage("Manual focus not supported on this lens (fixed focus)");
                return;
            }
            String current = AppSettings.get(this, AppSettings.FOCUS_MODE, AppSettings.FOCUS_CONTINUOUS);
            if (!AppSettings.FOCUS_MANUAL.equals(current)) {
                lastAutoFocusMode = current;
                if (Float.isFinite(lastLiveFocusDistance) && lastLiveFocusDistance >= 0f && minFocusDiopters > 0f) {
                    int initialProgress = ManualFocusHelper.dioptersToProgress(lastLiveFocusDistance, minFocusDiopters);
                    AppSettings.prefs(this).edit()
                            .putString(AppSettings.FOCUS_MODE, AppSettings.FOCUS_MANUAL)
                            .putInt(AppSettings.MANUAL_FOCUS_DISTANCE, initialProgress)
                            .apply();
                } else {
                    AppSettings.prefs(this).edit().putString(AppSettings.FOCUS_MODE, AppSettings.FOCUS_MANUAL).apply();
                }
            }
            focusMode = FocusMode.CONTINUOUS;
            currentAfRegions = null;
            updateManualFocusUi();
            refreshPreviewRequest();
            showMessage("MANUAL FOCUS · Drag slider or toggle 5× loupe");
            return;
        }
        lastAutoFocusMode = targetMode;
        AppSettings.prefs(this).edit().putString(AppSettings.FOCUS_MODE, targetMode).apply();
        focusMode = FocusMode.CONTINUOUS;
        currentAfRegions = null;
        mfDragLoupeActive = false;
        focusHandler.removeCallbacks(mfDragLoupeHideRunnable);
        updateManualFocusUi();
        updateLoupeOverlayVisibility();
        resetFocusToPreferredAfMode();
        showMessage(AppSettings.FOCUS_SINGLE.equals(targetMode) ? "SINGLE AUTOFOCUS (AF-S)" : "CONTINUOUS AUTOFOCUS (AF-C)");
    }

    private final Runnable mfDragLoupeHideRunnable = () -> {
        mfDragLoupeActive = false;
        updateLoupeOverlayVisibility();
    };

    private final Runnable loupeRefreshRunnable = new Runnable() {
        @Override public void run() {
            if (!activityResumed || videoMode || (!manualLoupeActive && !mfDragLoupeActive)) {
                return;
            }
            refreshActiveLoupeSample(false);
            focusHandler.postDelayed(this, 100L);
        }
    };

    private void setManualLoupeActive(boolean active) {
        if (videoMode) active = false;
        manualLoupeActive = active;
        updateLoupeButtonUi();
        if (active) {
            startLoupeRefresh();
        } else if (!mfDragLoupeActive) {
            stopLoupeRefresh();
        }
        updateLoupeOverlayVisibility();
    }

    private void startLoupeRefresh() {
        focusHandler.removeCallbacks(loupeRefreshRunnable);
        if (!videoMode && (manualLoupeActive || mfDragLoupeActive)) {
            focusHandler.postDelayed(loupeRefreshRunnable, 90L);
        }
    }

    private void stopLoupeRefresh() {
        focusHandler.removeCallbacks(loupeRefreshRunnable);
    }

    private void refreshActiveLoupeSample() {
        refreshActiveLoupeSample(true);
    }

    private void refreshActiveLoupeSample(boolean force) {
        if (focusReticle == null || videoMode) return;
        if (!focusReticle.isLoupeWanted()) return;
        focusReticle.sampleLoupe(focusReticle.reticleX, focusReticle.reticleY, force);
    }

    private void updateLoupeOverlayVisibility() {
        if (!manualLoupeActive && !mfDragLoupeActive) {
            stopLoupeRefresh();
        }
        if (focusReticle == null) return;
        if (focusReticle.isLoupeWanted() || focusReticle.isVisible) {
            focusReticle.prepareForVisible();
            refreshActiveLoupeSample(true);
        } else {
            focusReticle.fadeOut();
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

    private boolean isVideo180Requested() {
        return videoMode
                && AppSettings.VIDEO_SHUTTER_180.equals(
                        AppSettings.get(this, AppSettings.VIDEO_SHUTTER_MODE, AppSettings.VIDEO_SHUTTER_180))
                && !AppSettings.get(this, AppSettings.EXPOSURE_MODE, "Auto exposure").startsWith("Manual");
    }

    private int currentEffectiveVideoFps() {
        if ((videoState.isRecordingOrPaused() || videoState.isPreparing() || videoState.isSwitchingCamera())
                && activeTakeFps > 0) {
            return activeTakeFps;
        }
        int requestedFps = VideoRecordingCoordinator.computeTargetFps(
                AppSettings.get(this, AppSettings.VIDEO_FPS, "24 fps (Cinematic)"));
        if (characteristics == null) return requestedFps;
        return resolveVideoFpsForCamera(characteristics, requestedFps, videoSize).effectiveFps;
    }

    private void applyVideoFpsRange(CaptureRequest.Builder builder) {
        if (builder == null || characteristics == null || !videoMode) return;
        int targetFps = currentEffectiveVideoFps();
        VideoRecordingCoordinator.FpsRangeDecision fpsDecision =
                resolveVideoFpsForCamera(characteristics, targetFps, videoSize);
        if (fpsDecision.hasRange()) {
            builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                    new Range<>(fpsDecision.lower, fpsDecision.upper));
        }
    }

    private void maybeEngageVideo180FromMetered(long meteredExposureNs, int meteredIso) {
        CameraCharacteristics ch = characteristics;
        if (ch == null || !VideoRecordingCoordinator.canAutoEngage180Shutter(
                videoMode, video180Active, aeLocked, isVideo180Requested(), manualSupported)) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (now - lastVideo180StepAt < 350L) return;
        lastVideo180StepAt = now;
        Range<Long> expRange = ch.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
        Range<Integer> sensRange = ch.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
        if (expRange == null || sensRange == null) return;
        int effFps = currentEffectiveVideoFps();
        VideoRecordingCoordinator.Shutter180Plan plan = VideoRecordingCoordinator.evaluate180Shutter(
                true, true, effFps,
                expRange.getLower(), expRange.getUpper(),
                sensRange.getLower(), sensRange.getUpper(),
                meteredExposureNs, meteredIso, currentEvOffset);
        if (!plan.engaged) {
            if (!video180FallbackNotified && plan.fallbackReason != null
                    && plan.fallbackReason.startsWith("Bright scene")) {
                video180FallbackNotified = true;
                runOnUiThread(() -> showMessage("Bright scene · Auto shutter active for highlight safety (ND needed for 180°)"));
            }
            return;
        }
        video180EffectiveFps = effFps;
        video180Iso = plan.initialIso;
        video180Active = true;
        refreshPreviewRequest();
        runOnUiThread(this::updateReadouts);
    }

    private void updateVideo180AutoIsoFromLuma(double meanLuma) {
        CameraCharacteristics ch = characteristics;
        if (ch == null || !videoMode || !video180Active || aeLocked || !isVideo180Requested()) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastVideo180StepAt < 250L) return;
        Range<Integer> sensRange = ch.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
        if (sensRange == null) return;
        final int curIso = video180Iso;
        final int nextIso = VideoRecordingCoordinator.step180AutoIso(
                curIso, meanLuma, currentEvOffset, sensRange.getLower(), sensRange.getUpper());
        if (nextIso == curIso) {
            lastVideo180StepAt = now;
            return;
        }
        lastVideo180StepAt = nextIso < 0 ? (now + 1000L) : now;
        Handler camH = cameraHandler;
        Runnable applyStep = () -> {
            if (!videoMode || !isVideo180Requested()) return;
            boolean notifyFallback = false;
            if (nextIso < 0) {
                video180Active = false;
                video180Iso = 0;
                if (!video180FallbackNotified) {
                    video180FallbackNotified = true;
                    notifyFallback = true;
                }
            } else {
                video180Iso = nextIso;
            }
            refreshPreviewRequest();
            final boolean showFallbackToast = notifyFallback;
            runOnUiThread(() -> {
                updateReadouts();
                if (showFallbackToast) {
                    showMessage("Bright scene · Auto shutter active for highlight safety (ND needed for 180°)");
                }
            });
        };
        if (camH != null) {
            camH.post(applyStep);
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
        if (mode.startsWith("Manual") && manualSupported && characteristics != null) {
            Range<Long> exposureRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
            Range<Integer> sensitivityRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
            if (exposureRange != null && sensitivityRange != null) {
                long exposure = shutterNanoseconds(AppSettings.get(this, AppSettings.MANUAL_SHUTTER, "1/120 sec"));
                int iso = parseIso(AppSettings.get(this, AppSettings.MANUAL_ISO, "ISO 100"));
                exposure = Math.max(exposureRange.getLower(), Math.min(exposureRange.getUpper(), exposure));
                iso = Math.max(sensitivityRange.getLower(), Math.min(sensitivityRange.getUpper(), iso));
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
                builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposure);
                builder.set(CaptureRequest.SENSOR_SENSITIVITY, iso);
                return;
            }
        }
        if (videoMode && video180Active && isVideo180Requested() && manualSupported
                && video180Iso > 0 && characteristics != null) {
            Range<Long> exposureRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
            Range<Integer> sensitivityRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
            if (exposureRange != null && sensitivityRange != null) {
                long shutterNs = VideoRecordingCoordinator.target180ShutterNs(video180EffectiveFps);
                long frameNs = VideoRecordingCoordinator.frameDurationNs(video180EffectiveFps);
                shutterNs = Math.max(exposureRange.getLower(), Math.min(exposureRange.getUpper(), shutterNs));
                Long maxFrameNs = characteristics.get(CameraCharacteristics.SENSOR_INFO_MAX_FRAME_DURATION);
                if (maxFrameNs != null && maxFrameNs > 0L) {
                    frameNs = Math.min(frameNs, maxFrameNs);
                }
                if (frameNs < shutterNs) frameNs = shutterNs;
                int iso = Math.max(sensitivityRange.getLower(), Math.min(sensitivityRange.getUpper(), video180Iso));
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
                builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, shutterNs);
                builder.set(CaptureRequest.SENSOR_SENSITIVITY, iso);
                if (maxFrameNs == null || frameNs <= maxFrameNs) {
                    builder.set(CaptureRequest.SENSOR_FRAME_DURATION, frameNs);
                }
                return;
            }
        }
        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
        builder.set(CaptureRequest.CONTROL_AE_LOCK, aeLocked);
        builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, currentAeIndex);
        if (currentAeRegions != null) {
            builder.set(CaptureRequest.CONTROL_AE_REGIONS, currentAeRegions);
        }
    }

    private void setFlash(CaptureRequest.Builder builder, boolean preview) {
        String mode = AppSettings.get(this, AppSettings.FLASH, "Off");
        if (!isFrontCamera() && "Max".equalsIgnoreCase(mode)) mode = "Auto";
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
            if (videoMode) {
                applyVideoFpsRange(previewRequest);
            }
            if (!isVideo180Requested()) {
                video180Active = false;
                video180Iso = 0;
            }
            setExposure(previewRequest);
            setFlash(previewRequest, true);
            setFocusMode(previewRequest);
            applyOpticalSettings(previewRequest, videoMode);
            captureSession.setRepeatingRequest(previewRequest.build(), previewMetadataCallback, cameraHandler);
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
        boolean hud180 = !manual && videoMode && video180Active && isVideo180Requested() && video180Iso > 0;
        if (shutterValueText != null) {
            if (manual) {
                shutterValueText.setText(AppSettings.get(this, AppSettings.MANUAL_SHUTTER, "1/120 sec").replace(" sec", ""));
                shutterValueText.setTextColor(ACCENT);
            } else if (hud180) {
                shutterValueText.setText(VideoRecordingCoordinator.format180ShutterLabel(video180EffectiveFps));
                shutterValueText.setTextColor(ACCENT);
            } else {
                shutterValueText.setText("AUTO");
                shutterValueText.setTextColor(aeLocked ? ACCENT : 0xfff0f1ea);
            }
        }
        if (isoValueText != null) {
            if (manual) {
                isoValueText.setText(AppSettings.get(this, AppSettings.MANUAL_ISO, "ISO 100"));
                isoValueText.setTextColor(ACCENT);
            } else if (hud180) {
                isoValueText.setText("ISO " + video180Iso);
                isoValueText.setTextColor(aeLocked ? ACCENT : 0xfff0f1ea);
            } else {
                isoValueText.setText("AUTO");
                isoValueText.setTextColor(aeLocked ? ACCENT : 0xfff0f1ea);
            }
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
        updateContextualStatus();
    }

    private void updateTelemetryVisibility() {
        boolean easyMode = AppSettings.getBoolean(this, AppSettings.EASY_MODE, true);
        boolean manualExp = AppSettings.get(this, AppSettings.EXPOSURE_MODE, "Auto exposure").startsWith("Manual");
        int wb = AppSettings.resolveWhiteBalanceKelvin(this);
        boolean showTelemetry = AppSettings.getBoolean(this, AppSettings.SHOW_TELEMETRY, false)
                || !easyMode || manualExp || wb > 0;
        boolean activeVideo = videoState.isRecordingOrPaused() || videoState.isPreparing() || videoState.isSwitchingCamera();
        if (telemetry != null) {
            telemetry.setVisibility(showTelemetry ? View.VISIBLE : View.GONE);
        }
        if (cameraState != null) {
            cameraState.setVisibility((showTelemetry || activeVideo || cameraRetryPromptActive) ? View.VISIBLE : View.GONE);
        }
        if (outputModeText != null) {
            outputModeText.setVisibility(View.GONE);
        }
    }

    private void chooseShutterSpeed() {
        String[] baseOptions = {
                "1/30 sec", "1/60 sec", "1/120 sec", "1/240 sec",
                "1/500 sec", "1/1000 sec", "1/2000 sec", "1/4000 sec"
        };
        String[] baseSubtitles = {
                "Slow shutter · Handheld low-light limit",
                "Standard indoor / 30fps 180° cinema shutter",
                "Everyday walkaround · freezes casual motion",
                "Street & portraits · crisp subject motion",
                "Fast action & outdoor daylight",
                "High-speed daylight action",
                "Very bright sun · wide-aperture control",
                "Maximum high-speed shutter freeze"
        };
        Range<Long> expRange = characteristics != null ? characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE) : null;
        Long minNs = expRange != null ? expRange.getLower() : null;
        Long maxNs = expRange != null ? expRange.getUpper() : null;
        List<String> optList = new ArrayList<>();
        List<String> subList = new ArrayList<>();
        optList.add("Auto exposure");
        subList.add("Intelligent scene-adaptive shutter & ISO");
        for (int i = 0; i < baseOptions.length; i++) {
            long ns = shutterNanoseconds(baseOptions[i]);
            if (CameraExposureHelper.isShutterNanosecondsSupported(ns, minNs, maxNs)) {
                optList.add(baseOptions[i]);
                subList.add(baseSubtitles[i]);
            }
        }
        String[] options = optList.toArray(new String[0]);
        String[] subtitles = subList.toArray(new String[0]);
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
        String[] baseOptions = {
                "ISO 50", "ISO 100", "ISO 200", "ISO 400",
                "ISO 800", "ISO 1600", "ISO 3200"
        };
        String[] baseSubtitles = {
                "Base lowest gain · maximum dynamic range",
                "Daylight fine grain baseline",
                "Bright overcast & shaded daylight",
                "Classic Tri-X / Portra 400 speed",
                "Tungsten indoor & evening cinema",
                "Low-light night street",
                "Pushed high-sensitivity night"
        };
        Range<Integer> sensRange = characteristics != null ? characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE) : null;
        Integer minIso = sensRange != null ? sensRange.getLower() : null;
        Integer maxIso = sensRange != null ? sensRange.getUpper() : null;
        List<String> optList = new ArrayList<>();
        List<String> subList = new ArrayList<>();
        optList.add("Auto ISO");
        subList.add("Automatic sensor gain & exposure");
        for (int i = 0; i < baseOptions.length; i++) {
            int iso = parseIso(baseOptions[i]);
            if (CameraExposureHelper.isIsoSupported(iso, minIso, maxIso)) {
                optList.add(baseOptions[i]);
                subList.add(baseSubtitles[i]);
            }
        }
        String[] options = optList.toArray(new String[0]);
        String[] subtitles = subList.toArray(new String[0]);
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
        if (manualFocusPanel != null && frame.bottom > 0) {
            ViewGroup.LayoutParams lp = manualFocusPanel.getLayoutParams();
            if (lp instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) lp;
                int desiredTop = (int) (frame.bottom - dp(44) - dp(12) - dp(42));
                if (flp.topMargin != desiredTop) {
                    flp.topMargin = desiredTop;
                    manualFocusPanel.setLayoutParams(flp);
                }
            }
        }
        if (zoomSliderPanel != null && frame.bottom > 0) {
            ViewGroup.LayoutParams lp = zoomSliderPanel.getLayoutParams();
            if (lp instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) lp;
                boolean mfOpen = manualFocusPanel != null && manualFocusPanel.getVisibility() == View.VISIBLE;
                int desiredTop = (int) (frame.bottom - dp(44) - dp(12) - (mfOpen ? dp(84) : dp(40)));
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
            if (!isDown || cameraDevice == null || !cameraLifecycleState.isStarted()) return;
            if (!videoState.tryBeginQuickTake(VideoStateMachine.Trigger.QUICK_TAKE_SHUTTER)) return;
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
                    hapticClick(v);
                    shutterFrame.animate().cancel();
                    captureButton.animate().cancel();
                    shutterFrame.animate().scaleX(0.88f).scaleY(0.88f).setDuration(70).setInterpolator(new DecelerateInterpolator()).start();
                    captureButton.animate().scaleX(0.92f).scaleY(0.92f).setDuration(70).setInterpolator(new DecelerateInterpolator()).start();

                    if (!videoMode && videoState.isIdle()) {
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

                        if (videoState.isTriggeredBy(VideoStateMachine.Trigger.QUICK_TAKE_SHUTTER)) {
                            videoState.clearQuickTakeTrigger(VideoStateMachine.Trigger.QUICK_TAKE_SHUTTER);
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
                        if (videoState.isTriggeredBy(VideoStateMachine.Trigger.QUICK_TAKE_SHUTTER)) {
                            videoState.clearQuickTakeTrigger(VideoStateMachine.Trigger.QUICK_TAKE_SHUTTER);
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
        private Bitmap loupeSampleBuffer;
        private long lastLoupeSample;
        private final Paint reticlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint sunPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint chPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bezelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint badgeBg = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint badgeText = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint loupeBitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final RectF arcRect = new RectF();
        private final Rect loupeSrc = new Rect();
        private final RectF loupeDst = new RectF();
        private final RectF badgeRect = new RectF();
        private final Path loupeClipPath = new Path();
        private float cachedLoupeClipX = -1f;
        private float cachedLoupeClipY = -1f;
        private float cachedLoupeClipR = -1f;

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

            shadowPaint.setColor(0x88000000);

            chPaint.setColor(0x99FFFFFF);
            chPaint.setStrokeWidth(dp(1f));

            bezelPaint.setColor(ACCENT);
            bezelPaint.setStyle(Paint.Style.STROKE);

            badgeBg.setColor(0xDD0B0D0E);
            badgeBg.setStyle(Paint.Style.FILL);

            badgeText.setColor(ACCENT);
            badgeText.setTextSize(dp(8.5f));
            badgeText.setTypeface(Typeface.DEFAULT_BOLD);
            badgeText.setTextAlign(Paint.Align.CENTER);
        }

        boolean isLoupeWanted() {
            return !videoMode && (manualLoupeActive || mfDragLoupeActive || (prefOpticalLoupe && isVisible));
        }

        void prepareForVisible() {
            animate().withEndAction(null);
            animate().cancel();
            setAlpha(1f);
            setVisibility(View.VISIBLE);
            invalidate();
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
            if (isLoupeWanted()) {
                sampleLoupe(x, y, true);
            }
            invalidate();
        }

        void sampleLoupe(float x, float y) {
            sampleLoupe(x, y, false);
        }

        void sampleLoupe(float x, float y, boolean force) {
            if (!isLoupeWanted()) return;
            long now = SystemClock.elapsedRealtime();
            if (!force && now - lastLoupeSample < 60) return;
            lastLoupeSample = now;
            if (textureView == null || !textureView.isAvailable()) return;
            try {
                int tvW = textureView.getWidth();
                int tvH = textureView.getHeight();
                if (tvW <= 0 || tvH <= 0) return;
                RectF frame = framingRect(tvW, tvH);
                float anchorX = x >= 0f ? x : frame.centerX();
                float anchorY = y >= 0f ? y : frame.centerY();

                int sampleW = Math.min(tvW, 1280);
                int sampleH = Math.max(1, Math.round(sampleW * (tvH / (float) tvW)));
                if (loupeSampleBuffer == null || loupeSampleBuffer.isRecycled()
                        || loupeSampleBuffer.getWidth() != sampleW || loupeSampleBuffer.getHeight() != sampleH) {
                    if (loupeSampleBuffer != null && !loupeSampleBuffer.isRecycled()) {
                        loupeSampleBuffer.recycle();
                    }
                    loupeSampleBuffer = Bitmap.createBitmap(sampleW, sampleH, Bitmap.Config.ARGB_8888);
                }
                Bitmap full = textureView.getBitmap(loupeSampleBuffer);
                if (full != null) {
                    int cropSrcSize = Math.max(24, Math.min(Math.min(full.getWidth(), full.getHeight()),
                            Math.round(dp(24) * (full.getWidth() / (float) tvW))));
                    int centerSx = Math.round((anchorX / (float) tvW) * full.getWidth());
                    int centerSy = Math.round((anchorY / (float) tvH) * full.getHeight());
                    int sLeft = Math.max(0, Math.min(full.getWidth() - cropSrcSize, centerSx - cropSrcSize / 2));
                    int sTop = Math.max(0, Math.min(full.getHeight() - cropSrcSize, centerSy - cropSrcSize / 2));
                    Bitmap crop = Bitmap.createBitmap(full, sLeft, sTop, cropSrcSize, cropSrcSize);
                    if (loupeBitmap != null && !loupeBitmap.isRecycled()) loupeBitmap.recycle();
                    loupeBitmap = crop;
                    invalidate();
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

        void fadeOutReticle() {
            if (isLoupeWanted()) {
                isVisible = false;
                invalidate();
            } else {
                fadeOut();
            }
        }

        void fadeOut() {
            if (loupeBitmap != null && !loupeBitmap.isRecycled()) {
                loupeBitmap.recycle();
                loupeBitmap = null;
            }
            if (loupeSampleBuffer != null && !loupeSampleBuffer.isRecycled()) {
                loupeSampleBuffer.recycle();
                loupeSampleBuffer = null;
            }
            animate().cancel();
            animate().alpha(0f).setDuration(350).withEndAction(() -> {
                isVisible = false;
                reticleX = -1f;
                reticleY = -1f;
                setVisibility(View.GONE);
            }).start();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            boolean drawReticle = isVisible && reticleX >= 0 && reticleY >= 0;
            boolean drawLoupe = isLoupeWanted() && loupeBitmap != null && !loupeBitmap.isRecycled();
            if (!drawReticle && !drawLoupe) return;

            if (drawReticle) {
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
                    float r1 = dp(5.5f);
                    float r2 = dp(8.0f);
                    for (int i = 0; i < 6; i++) {
                        double angle = i * Math.PI / 3.0;
                        float cosA = (float) Math.cos(angle);
                        float sinA = (float) Math.sin(angle);
                        canvas.drawLine(
                                trackX + cosA * r1,
                                sunY + sinA * r1,
                                trackX + cosA * r2,
                                sunY + sinA * r2,
                                sunPaint
                        );
                    }

                    String evText = Math.abs(evBias) < 0.05f ? "0.0" : String.format(Locale.US, "%+.1f", evBias);
                    canvas.drawText(evText, trackX + dp(12), sunY + dp(3.5f), textPaint);

                    // Left-side Shadow Depth rail (Contrast / Half-Moon icon — Pixel Dual Exposure style)
                    float leftTrackX = reticleX - half - dp(16);
                    reticlePaint.setAlpha(80);
                    canvas.drawLine(leftTrackX, trackTop, leftTrackX, trackBottom, reticlePaint);
                    reticlePaint.setAlpha(255);

                    float normSh = (Math.max(0, Math.min(100, shadowDepth)) - 50f) / 50f;
                    float moonY = reticleY - normSh * (trackHeight / 2f);

                    sunPaint.setStyle(Paint.Style.STROKE);
                    canvas.drawCircle(leftTrackX, moonY, r1, sunPaint);
                    sunPaint.setStyle(Paint.Style.FILL);
                    arcRect.set(leftTrackX - r1, moonY - r1, leftTrackX + r1, moonY + r1);
                    canvas.drawArc(arcRect, 90f, 180f, true, sunPaint);

                    String shText = shadowDepth + "%";
                    float shWidth = textPaint.measureText(shText);
                    canvas.drawText(shText, leftTrackX - dp(10) - shWidth, moonY + dp(3.5f), textPaint);
                }
            }

            // Pro Optical Loupe (5x Magnifier Lens with Leica Gold Ring)
            if (drawLoupe) {
                RectF frame = framingRect(getWidth(), getHeight());
                float ax = reticleX >= 0f ? reticleX : frame.centerX();
                float ay = reticleY >= 0f ? reticleY : frame.centerY();
                float loupeR = dp(44);
                boolean isWide = WindowMetricsHelper.computeWindowSizeInfo(MainActivity.this).isLandscape
                        || getWidth() >= getHeight();
                float topGuard = frame.top + loupeR + (isWide ? dp(18) : dp(56));
                float bottomExtra = (!isWide && manualFocusPanel != null && manualFocusPanel.getVisibility() == View.VISIBLE ? dp(44) : 0f)
                        + (!isWide && zoomSliderPanel != null && zoomSliderPanel.getVisibility() == View.VISIBLE ? dp(44) : 0f);
                float bottomGuard = frame.bottom - loupeR - (isWide ? dp(12) : dp(72) + bottomExtra);
                if (bottomGuard < topGuard) bottomGuard = topGuard;

                float minX = frame.left + loupeR + dp(12);
                float maxX = Math.max(minX, frame.right - loupeR - dp(12));
                float loupeX = Math.max(minX, Math.min(maxX, ax));
                float loupeY = ay - dp(92);
                if (loupeY < topGuard) {
                    float belowY = ay + dp(92);
                    loupeY = (belowY <= bottomGuard) ? belowY : topGuard;
                }
                loupeY = Math.max(topGuard, Math.min(bottomGuard, loupeY));

                canvas.drawCircle(loupeX, loupeY + dp(3), loupeR + dp(2), shadowPaint);

                canvas.save();
                if (loupeX != cachedLoupeClipX || loupeY != cachedLoupeClipY || loupeR != cachedLoupeClipR) {
                    loupeClipPath.rewind();
                    loupeClipPath.addCircle(loupeX, loupeY, loupeR, Path.Direction.CW);
                    cachedLoupeClipX = loupeX;
                    cachedLoupeClipY = loupeY;
                    cachedLoupeClipR = loupeR;
                }
                canvas.clipPath(loupeClipPath);

                loupeSrc.set(0, 0, loupeBitmap.getWidth(), loupeBitmap.getHeight());
                loupeDst.set(loupeX - loupeR, loupeY - loupeR, loupeX + loupeR, loupeY + loupeR);
                canvas.drawBitmap(loupeBitmap, loupeSrc, loupeDst, loupeBitmapPaint);

                float chArm = dp(14);
                canvas.drawLine(loupeX - chArm, loupeY, loupeX + chArm, loupeY, chPaint);
                canvas.drawLine(loupeX, loupeY - chArm, loupeX, loupeY + chArm, chPaint);
                canvas.drawCircle(loupeX, loupeY, dp(2), chPaint);
                canvas.restore();

                bezelPaint.setStrokeWidth(dp(2.2f));
                canvas.drawCircle(loupeX, loupeY, loupeR, bezelPaint);

                badgeRect.set(loupeX - dp(26), loupeY - loupeR - dp(13), loupeX + dp(26), loupeY - loupeR + dp(5));
                float badgeRadius = dp(4);
                canvas.drawRoundRect(badgeRect, badgeRadius, badgeRadius, badgeBg);
                bezelPaint.setStrokeWidth(dp(1f));
                canvas.drawRoundRect(badgeRect, badgeRadius, badgeRadius, bezelPaint);

                canvas.drawText("5× FOCUS", loupeX, loupeY - loupeR - dp(1.5f), badgeText);
            }
        }
    }

    private final class GestureOverlay extends FrameLayout {
        GestureOverlay() {
            super(MainActivity.this);
        }

        @Override public boolean dispatchTouchEvent(MotionEvent event) {
            if (!videoLock.locked() && scaleGestureDetector != null) {
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
            if (videoLock.locked() || videoState.isPreparing() || videoState.isSwitchingCamera()) return false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    zoomRowStartX = event.getX();
                    zoomRowStartY = event.getY();
                    zoomRowStartZoom = requestedZoom;
                    gesture.end(InteractionState.Gesture.Mode.ZOOM_ROW);
                    break;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getX() - zoomRowStartX;
                    float dy = event.getY() - zoomRowStartY;
                    if (Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)) {
                        gesture.begin(InteractionState.Gesture.Mode.ZOOM_ROW);
                        showZoomSlider();
                        return true;
                    }
                    break;
            }
            return gesture.is(InteractionState.Gesture.Mode.ZOOM_ROW);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (videoLock.locked() || videoState.isPreparing() || videoState.isSwitchingCamera()) return false;
            if (!gesture.is(InteractionState.Gesture.Mode.ZOOM_ROW)) return false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_MOVE:
                    float distanceInDp = (event.getX() - zoomRowStartX) / getResources().getDisplayMetrics().density;
                    float targetZoom = zoomRowStartZoom * (float) Math.pow(2d, distanceInDp / 110d);
                    setInteractiveZoom(targetZoom);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    gesture.end(InteractionState.Gesture.Mode.ZOOM_ROW);
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

    private void releaseCapturedImage(Image image) {
        final ResourceLease lease;
        synchronized(captureLock) { lease=imageLeases.remove(image); }
        try { image.close(); } finally { if(lease!=null) lease.release(); }
    }

    private void closeReader(ImageReader reader) {
        if(reader==null) return;
        final ResourceLease lease;
        synchronized(captureLock) {
            try { reader.setOnImageAvailableListener(null,null); }
            catch(RuntimeException error) { Log.w(TAG,"Could not clear reader callback",error); }
            lease=readerLeases.computeIfAbsent(reader,r -> new ResourceLease(() -> {
                try { r.close(); } finally { synchronized(captureLock) { readerLeases.remove(r); } }
            }));
        }
        lease.close();
    }

    private static final float[] GRID_DIV_2X2 = {.5f};
    private static final float[] GRID_DIV_3X3 = {1f / 3f, 2f / 3f};
    private static final float[] GRID_DIV_4X4 = {.25f, .5f, .75f};
    private static final float[] GRID_DIV_GOLDEN = {.382f, .618f};
    private static final float[] GRID_DIV_NONE = new float[0];

    private final class CompositionGrid extends View {
        private final Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint aidPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint modePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint levelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path maskPath = new Path();
        private final Path clipPath = new Path();
        private final Path sparkPath = new Path();
        private final RectF cachedFrame = new RectF();
        private final RectF cachedMaskFrame = new RectF();
        private float cachedMaskW = -1f;
        private float cachedMaskH = -1f;
        private float cachedCornerRadius = -1f;
        private final int[] drawHistBins = new int[32];
        private final int[] drawHistR = new int[32];
        private final int[] drawHistG = new int[32];
        private final int[] drawHistB = new int[32];
        private final boolean[] drawZebraCells = new boolean[EXPOSURE_GRID_SIZE * EXPOSURE_GRID_SIZE];
        private final boolean[] drawPeakingCells = new boolean[EXPOSURE_GRID_SIZE * EXPOSURE_GRID_SIZE];
        final RectF currentHistRect = new RectF();

        CompositionGrid(Activity activity) {
            super(activity);
            float density = activity.getResources().getDisplayMetrics().density;
            maskPaint.setColor(BG);
            maskPaint.setStyle(Paint.Style.FILL);
            gridPaint.setColor(0xb8f2f3ec);
            gridPaint.setStyle(Paint.Style.STROKE);
            gridPaint.setStrokeWidth(density);
            framePaint.setColor(0x18ffffff);
            framePaint.setStyle(Paint.Style.STROKE);
            framePaint.setStrokeWidth(density);

            modePaint.setColor(0x88D7B574);
            modePaint.setTextSize(density * 7.5f);
            modePaint.setTypeface(Typeface.DEFAULT_BOLD);
            modePaint.setTextAlign(Paint.Align.CENTER);

            levelPaint.setStyle(Paint.Style.STROKE);
            levelPaint.setStrokeWidth(density * 1.5f);
        }

        boolean isHistogramHit(float x, float y) {
            return !currentHistRect.isEmpty() && currentHistRect.contains(x, y);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            framingRectInto(w, h, cachedFrame);
            RectF frame = cachedFrame;
            if (frame.width() <= 0 || frame.height() <= 0) return;
            float density = getResources().getDisplayMetrics().density;
            float cornerRadius = Math.min(density * 24f, Math.min(frame.width(), frame.height()) * 0.5f);

            if (w != cachedMaskW || h != cachedMaskH || cornerRadius != cachedCornerRadius
                    || !frame.equals(cachedMaskFrame)) {
                maskPath.rewind();
                maskPath.setFillType(Path.FillType.EVEN_ODD);
                maskPath.addRect(0, 0, w, h, Path.Direction.CW);
                maskPath.addRoundRect(frame, cornerRadius, cornerRadius, Path.Direction.CW);
                clipPath.rewind();
                clipPath.addRoundRect(frame, cornerRadius, cornerRadius, Path.Direction.CW);
                cachedMaskFrame.set(frame);
                cachedMaskW = w;
                cachedMaskH = h;
                cachedCornerRadius = cornerRadius;
            }

            // Mask outside the curved viewfinder card with solid Leica obsidian black
            canvas.drawPath(maskPath, maskPaint);

            // Clip inner aids to the curved viewfinder
            canvas.save();
            canvas.clipPath(clipPath);

            final boolean showZebra = prefZebra;
            final boolean showPeaking = prefFocusPeaking;
            final boolean showHist = prefHistogram;
            final int clipFlags = exposureAidsAnalyzer.copyForDraw(
                    showZebra, drawZebraCells,
                    showPeaking, drawPeakingCells,
                    showHist, drawHistBins, drawHistR, drawHistG, drawHistB);
            final boolean shadowClipped = (clipFlags & 1) != 0;
            final boolean highlightClipped = (clipFlags & 2) != 0;

            // Live Zebra Stripes & Focus Peaking Overlays
            if (showZebra || showPeaking) {
                float cellW = frame.width() / 24f;
                float cellH = frame.height() / 24f;
                for (int gy = 0; gy < 24; gy++) {
                    for (int gx = 0; gx < 24; gx++) {
                        int idx = gy * 24 + gx;
                        float cellLeft = frame.left + gx * cellW;
                        float cellTop = frame.top + gy * cellH;
                        if (showZebra && drawZebraCells[idx]) {
                            aidPaint.setStyle(Paint.Style.STROKE);
                            aidPaint.setStrokeWidth(density * 1.1f);
                            aidPaint.setColor(0xBBFF6B57);
                            canvas.drawLine(cellLeft, cellTop + cellH, cellLeft + cellW, cellTop, aidPaint);
                            canvas.drawLine(cellLeft + cellW * 0.4f, cellTop + cellH, cellLeft + cellW, cellTop + cellH * 0.4f, aidPaint);
                        }
                        if (showPeaking && drawPeakingCells[idx]) {
                            aidPaint.setStyle(Paint.Style.FILL);
                            aidPaint.setColor(0xCCDFBE7F);
                            canvas.drawCircle(cellLeft + cellW * 0.5f, cellTop + cellH * 0.5f, density * 1.8f, aidPaint);
                        }
                    }
                }
            }

            String style = prefGridStyle;
            float[] divisions = "2×2".equals(style) ? GRID_DIV_2X2
                    : "3×3".equals(style) ? GRID_DIV_3X3
                    : "4×4".equals(style) ? GRID_DIV_4X4
                    : "Golden ratio".equals(style) ? GRID_DIV_GOLDEN
                    : GRID_DIV_NONE;
            for (float division : divisions) {
                float x = frame.left + frame.width() * division;
                float y = frame.top + frame.height() * division;
                canvas.drawLine(x, frame.top, x, frame.bottom, gridPaint);
                canvas.drawLine(frame.left, y, frame.right, y, gridPaint);
            }

            // Live RGB / Luminance Histogram Overlay & Clipping LEDs
            if (showHist) {
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
                aidPaint.setColor(shadowClipped ? 0xFFFFB03A : 0x2289948E);
                canvas.drawCircle(histLeft + density * 8f, histTop + density * 7f, density * 2.2f, aidPaint);
                aidPaint.setColor(highlightClipped ? 0xFFFF3B30 : 0x2289948E);
                canvas.drawCircle(histRight - density * 8f, histTop + density * 7f, density * 2.2f, aidPaint);

                String mode = prefHistogramMode;
                modePaint.setTextSize(density * 7.5f);
                String tag = "RGB Parade".equals(mode) ? "RGB" : "Compact".equals(mode) ? "MINI" : "LUMA";
                canvas.drawText(tag, currentHistRect.centerX(), histTop + density * 8.5f, modePaint);

                float barW = (histW - density * 8f) / 32f;
                float maxBarH = histH - density * 14f;

                int maxBin = 1;
                for (int i = 0; i < 32; i++) {
                    if (drawHistBins[i] > maxBin) maxBin = drawHistBins[i];
                    if (drawHistR[i] > maxBin) maxBin = drawHistR[i];
                    if (drawHistG[i] > maxBin) maxBin = drawHistG[i];
                    if (drawHistB[i] > maxBin) maxBin = drawHistB[i];
                }

                if ("RGB Parade".equals(mode)) {
                    for (int channel = 0; channel < 3; channel++) {
                        int[] bins = channel == 0 ? drawHistR : channel == 1 ? drawHistG : drawHistB;
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
                    sparkPath.rewind();
                    sparkPath.moveTo(histLeft + density * 4f, histBottom - density * 4f);
                    for (int i = 0; i < 32; i++) {
                        float norm = (float) drawHistBins[i] / maxBin;
                        float bh = norm * maxBarH;
                        float bx = histLeft + density * 4f + i * barW + barW * 0.5f;
                        sparkPath.lineTo(bx, histBottom - density * 4f - bh);
                    }
                    sparkPath.lineTo(histRight - density * 4f, histBottom - density * 4f);
                    aidPaint.setStyle(Paint.Style.FILL);
                    aidPaint.setColor(0x33D7B574);
                    canvas.drawPath(sparkPath, aidPaint);
                    aidPaint.setStyle(Paint.Style.STROKE);
                    aidPaint.setStrokeWidth(density * 1.2f);
                    aidPaint.setColor(ACCENT);
                    canvas.drawPath(sparkPath, aidPaint);
                } else {
                    aidPaint.setStyle(Paint.Style.FILL);
                    for (int i = 0; i < 32; i++) {
                        float norm = (float) drawHistBins[i] / maxBin;
                        float bh = Math.max(density * 1.2f, norm * maxBarH);
                        float bx = histLeft + density * 4f + i * barW;
                        aidPaint.setColor(i >= 30 ? 0xDDFF4444 : (i == 0 && shadowClipped) ? 0xDDFFA030 : 0xCCD7B574);
                        canvas.drawRect(bx, histBottom - density * 4f - bh, bx + Math.max(1f, barW - density * 0.6f), histBottom - density * 4f, aidPaint);
                    }
                }
            } else {
                currentHistRect.setEmpty();
            }

            if (prefVisualLevel) {
                float cx = frame.centerX();
                float cy = frame.centerY();
                String sens = prefHorizonSensitivity;
                float deadband = sens != null && sens.contains("0.5") ? 0.5f : 1.0f;
                String horizonMode = prefHorizonMode;
                boolean smartNearOnly = horizonMode == null || horizonMode.startsWith("Smart");

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

            canvas.drawRoundRect(frame, cornerRadius, cornerRadius, framePaint);
        }
    }

    private void closeCamera() {
        closeCamera(false);
    }

    private void closeCamera(boolean preserveVideoSwitch) {
        cameraEpoch.invalidate();
        cameraUiHandler.removeCallbacks(cameraReopenRetryRunnable);
        cancelDelayedCapture();
        if(activityResumed || videoState.isQuickTake()) videoState.cancelPermissionPending();
        if (stillCaptureState.isTimerRunning()) cancelTimerCountdown();
        boolean shouldStopVideo = videoState.isRecordingOrPaused()
                || videoState.isPreparing()
                || mediaRecorder != null
                || (!preserveVideoSwitch && videoState.isSwitchingCamera());
        if (shouldStopVideo) {
            stopVideoRecording(false);
        }
        if (zoomAnimator != null) { zoomAnimator.cancel(); zoomAnimator = null; }
        pendingPostConfigZoom = -1f;
        if (textureView != null) {
            textureView.animate().cancel();
            textureView.setScaleX(1f);
            textureView.setScaleY(1f);
        }
        cameraLifecycleState = CameraLifecycleState.CLOSING;
        stillCaptureState = StillCaptureState.IDLE;
        runOnUiThread(this::updateGalleryProcessingUi);
        closeSessionOnly();
        if (cameraDevice != null) { cameraDevice.close(); cameraDevice = null; }
        closeRetiredReaders();
        closeReader(rawReader); rawReader = null;
        closeReader(jpegReader); jpegReader = null;
        synchronized (captureLock) {
            cancelActiveCaptureLocked();
        }
        cameraLifecycleState = CameraLifecycleState.CLOSED;
    }

    private void showMessage(String message) {
        if(isFinishing() || isDestroyed()) return;
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

    @Override public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        if (width > 0 && height > 0) {
            configureTransform(width, height);
        }
        if (activityResumed) {
            openCameraIfReady();
            applyLiveFilmPreview();
        }
    }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) { configureTransform(width, height); }
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        closeCamera();
        if (previewSurface != null) { previewSurface.release(); previewSurface = null; }
        previewSurfaceTexture = null;
        return true;
    }
    @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) { }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == VIDEO_PERMISSION) {
            if (!videoState.isPermissionPending()) return;
            boolean storageAllowed = Build.VERSION.SDK_INT >= 29
                    || checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
            if (!storageAllowed) {
                videoState.cancelPermissionPending();
                showMessage("Storage access is needed to save video on this Android version.");
                updateVideoModeUi();
                return;
            }
            boolean audioAllowed = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
            if (!audioAllowed) showMessage("Microphone access denied. Recording silent video.");
            videoState.markPermissionReady();
            if (activityResumed && cameraDevice != null && cameraLifecycleState.isStarted()) beginVideoRecording(audioAllowed);
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
