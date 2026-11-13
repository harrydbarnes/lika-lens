# Capture orientation and processing consistency

The still request freezes its camera characteristics, clockwise orientation, brain recipe,
aspect ratio, selfie mirroring, RAW policy and pre-look preview midtone reference. These
travel with the image/result pair rather than being read again after the camera callback.

## Orientation

- DNG export maps clockwise degrees to TIFF/EXIF orientation values with `CaptureOrientation.toExif`.
  Sensor mosaic samples remain in sensor order.
- JPEG fallback honours all eight EXIF transforms before grading/cropping. An explicit NORMAL
  tag means the HAL has already oriented the pixels. Only a missing tag uses the frozen
  Camera2 orientation. Exported JPEG pixels are upright and tagged NORMAL.
- This affects newly captured files. Existing DNG files are not rewritten.

## Preview

On Android 13 and later, a 33-point RGB cube is generated using the export's
`RawDeveloper.renderDisplayRgb` transform. A RuntimeShader interpolates the cube on the live
camera texture. All six brains, tone controls, white balance adjustments, built-in and imported
LUTs and LUT intensity therefore use the same point colour transform as JPEG development.
Preview AWB metadata supplies the same warm-illuminant attenuation used by RAW development.
Table builds run off the UI thread, coalesce slider changes and discard obsolete results.

Android 8–12 and GPUs that reject the effect use the existing matrix approximation;
that fallback now honours built-in LUT intensity. Imported LUTs still require Android 13+
for the shared live preview.

The Camera2 preview remains an ISP-processed display image, whereas RAW development starts
with sensor data. The pre-effect preview midtone reference guides Easy-mode RAW recovery
at capture time. It is not applied a second time as exposure compensation. Recovery is
bounded by ISO, shadow signal and highlights, and disabled for manual sensor exposures.
RAW archive version 5 persists this exposure intent while retaining readers for versions 1–4.

This is a closer preview, not guaranteed pixel identity: demosaicing, lens corrections,
local RAW tone recovery, grain, halation, borders, sharpening and HDR gallery rendering
can still differ. The six authored looks are not claimed to be measured film-stock profiles.

## Noise and automatic night integration

- Global scene recovery is capped at 3x at low ISO and reduced as ISO rises; nearly black
  sensor noise is not treated as recoverable subject detail.
- Local recovery is reduced from 1.15 to 0.65 additional gain at its maximum, with further
  signal/ISO/exposure-intent gating.
- Grain is a neutral, deterministic output texture after denoising, halation and sharpening,
  with coordinates tied to a common image-size reference.
- Warm illumination reduces added Film/Koda warmth rather than stacking a full synthetic
  tint on top of warm indoor light. The user's warmth adjustment remains available.
- Automatic night integration requires a stationary, dim scene (ISO >= 800 and shutter
  >= 25ms), flash off and automatic exposure. It trades sensor ISO for shutter time at the metered
  exposure, capped at 2 seconds. Explicit Always mode retains its long-exposure behaviour.

## Validation and device checklist

Unit coverage includes the four degree-to-TIFF mappings, all eight JPEG transform mappings,
capture recipe retention, archive v4 compatibility/v5 round trips, all six preview brains,
off-grid interpolation, LUT strength, warm illuminant metadata, ISO/compensation/manual
recovery, pre-look metering, night integration and resolution-independent neutral grain.

Before releasing to users, test a real camera device:

1. Capture rear-camera DNG/JPEG pairs in portrait, reverse portrait and both landscape
   orientations. Inspect DNG TIFF Orientation and open each in Google Photos and a RAW editor.
2. Turn/move the phone and change brain settings immediately after pressing the shutter.
   Both outputs should retain the submitted shot's settings and orientation.
3. Photograph the same warm indoor scene with Film, Koda and Neutral, with grain at 0 and 22.
   Compare preview and saved SDR image for brightness and neutral surfaces. Repeat with HDR
   display enabled to distinguish gallery HDR brightness from the SDR grade.
4. Repeat daylight, skin, foliage and high-contrast window scenes through all six brains.
   Use a tripod and identical exposure/recipe settings, then render the same archived RAW
   through every brain to isolate the colour transform from capture differences.
5. Check imported LUT strengths 0/50/100%, toggle live preview, scrub sliders, switch lenses,
   background/resume the app, and confirm no stale effect or flickering ungraded frames.
6. Confirm a steady, normally lit scene does not activate night integration. In dim light,
   check that automatic integration reduces ISO without a brightness jump.
