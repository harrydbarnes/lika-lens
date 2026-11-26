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
- RAW comparison in the viewer restores `flipHorizontal` from the photo's saved capture recipe,
  so held selfie originals use the same mirror direction as their developed JPEG.
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
- Local recovery uses up to 0.85 additional gain at its maximum, with signal/ISO/exposure-intent
  and bright-highlight gating.
- Sensor highlight reconstruction smoothly lifts saturated Bayer photosites toward unclipped
  channel energy before the camera colour matrix so clipped skies and lamps never cast magenta.
  Full-resolution demosaicing uses 5×5 Hamilton-Adams edge-directed green interpolation with
  diagonal chroma weighting; `COLOR_CORRECTION_TRANSFORM` is validated before replacing the DNG
  dual-illuminant matrix.
- The filmic H&D tone curve transitions $C^1$-smoothly across `safePivot`. Each colour brain uses
  smooth angular Oklab DIR hue couplers, Beer-Lambert subtractive CMY dye density (neutral-grey
  invariant), and a paper-white-preserving highlight bell; `BLEACH BYPASS` applies ENR tonal-zone
  silver retention; `MONO / SILVER` uses chroma-aware panchromatic response and darkroom split-tone
  paper toning; built-in 3D LUTs run in linear→Oklab space with anchored black/white endpoints.
- Halation is anchored to a 240-cell reference long edge with dual-scale tight+wide scatter and
  brain-specific spectral tint (achromatic silver bloom on `MONO / SILVER`, platinum-silver on
  `BLEACH BYPASS`, red-orange anti-halation backscatter on colour film). Chroma NR uses 8-neighbor
  per-neighbor bilateral luminance weights, sharpening is edge-cored luminance-only with halo
  overshoot limiting, and grain uses Hermite interpolation with specular highlight taper anchored
  to a 3000 px reference long edge.
- JPEG fallback and video still captures crop before grading, receive capture/preview `wbGains`
  and exposure metadata, and attach smoothed ISO 21496-1 Ultra HDR gainmaps aligned to any border.
  Editor brain presets reset trim sliders to neutral baselines so brain character is applied once
  and matches the camera viewfinder.
- Warm illumination reduces added Film/Koda warmth rather than stacking a full synthetic
  tint on top of warm indoor light. The user's warmth adjustment remains available.
- Automatic night integration requires a stationary, dim scene (ISO >= 800 and shutter
  >= 25ms), flash off and automatic exposure. It trades sensor ISO for shutter time at the metered
  exposure, capped at 2 seconds. Explicit Always mode retains its long-exposure behaviour.
- Explicit Advanced tonal/color overrides (`SKIN_PROTECTION_MODE`, `SMART_VIBRANCE_MODE`,
  `SHADOW_PROFILE`, `HIGHLIGHT_RECOVERY`) are honoured even when `EASY_MODE` is enabled, while
  defaulting to brain-native / scene-adaptive (`Auto`) behaviour when unpinned.
- `COLOR_SPACE_P3` runs sRGB→Display P3 output gamut conversion in both `RawDeveloper` exports and
  `PreviewLookLut` live preview.
- Under `PHOTO_OUTPUT_JPEG_ONLY` (Option B), processed JPEGs publish to Gallery while app-private
  `.sraw` originals are retained behind a configurable storage budget (`EDITABLE_ARCHIVE_LIMIT`)
  for non-destructive brain switching and re-editing. Explicitly requested DNGs and pinned active
  `.sraw` edits are exempt from LRU cache eviction.

## Validation and device checklist

Unit coverage includes the four degree-to-TIFF mappings, all eight JPEG transform mappings,
capture recipe retention, archive v4 compatibility/v5 round trips, `.sraw` cache pinning & limit
enforcement, `AppSettings` v1→v2 migration, all six preview brains, off-grid interpolation,
LUT strength, warm illuminant metadata, ISO/compensation/manual recovery, pre-look metering,
night integration, sensor highlight reconstruction, colour-matrix validation, $C^1$ filmic curve
monotonicity, photochemical per-brain signatures, achromatic monochrome halation, luminance-only
cored sharpening, bilateral chroma NR, built-in LUT black/white preservation, resolution-independent
neutral grain, `ManualFocusHelper`, `CaptureFileNaming`, and `VideoRecordingCoordinator` (25 fps,
HLG10 HDR, 180° Auto-ISO, audio zoom, mid-take switches).

Before releasing to users, test a real camera device:

1. Capture rear-camera DNG/JPEG pairs in portrait, reverse portrait and both landscape
   orientations. Inspect DNG TIFF Orientation and open each in Google Photos and a RAW editor.
2. Turn/move the phone and change brain settings immediately after pressing the shutter.
   Both outputs should retain the submitted shot's settings and orientation.
3. Photograph the same warm indoor scene with Film, Koda and Neutral, with grain at 0 and 22.
   Compare preview and saved SDR image for brightness and neutral surfaces. Repeat with HDR
   display and Display P3 enabled to distinguish gallery HDR brightness and wide gamut output.
4. Repeat daylight, skin, foliage and high-contrast window scenes through all six brains.
   Use a tripod and identical exposure/recipe settings, then render the same archived RAW
   through every brain to isolate the colour transform from capture differences.
5. Check imported LUT strengths 0/50/100%, toggle live preview, scrub sliders, switch lenses,
   background/resume the app, and confirm no stale effect or flickering ungraded frames.
6. Confirm a steady, normally lit scene does not activate night integration. In dim light,
   check that automatic integration reduces ISO without a brightness jump.
7. Verify Option B `Processed JPEG only` saves only JPEG to MediaStore Gallery, retains private
   `.sraw` for re-editing, enforces the storage limit without removing open edits or DNGs, and
   clears unpinned `.sraw` caches cleanly from General settings.
8. Verify Camera2 manual focus diopter range, optional 5× loupe activation (`LOUPE_ON_MF_ADJUST`
   and manual pill), 25 fps PAL / HLG10 HDR / 180° shutter Auto-ISO video, and ringer-mode shutter
   sound compliance.
9. Capture front-camera selfies with mirroring enabled and disabled; hold to compare the RAW original
   and confirm its display direction matches the processed JPEG.
