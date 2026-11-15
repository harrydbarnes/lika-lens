# CLAUDE.md

Lika Lens (`com.scope.camera`) is a pure Java 17 Android film camera and RAW darkroom (`minSdk = 26`, `targetSdk = 35`, `enableKotlin = false`, `android.useAndroidX = false`). Read [`AGENTS.md`](AGENTS.md) and the docs under [`docs/`](docs/) before making architectural, colour-pipeline, or UI changes.

## Core Invariants
- **Pure Java & Native Views**: Keep zero Kotlin/AndroidX dependencies. Share native `android.view` styling and dark/gold tokens (`#0B0D0E` / `#D7B574`) via `UiKit`. See `design-qa.md` for Library/Editor design boundaries.
- **Capture & Colour Parity** (`docs/capture-processing-consistency.md`):
  - Freeze capture characteristics, clockwise `CaptureOrientation`, look recipe, aspect ratio, selfie mirroring, RAW policy, and pre-look `previewMidTarget` at shutter press.
  - Keep `PreviewLookLut` (`RuntimeShader` preview), `RawDeveloper.renderDisplayRgb`, `RawDeveloper.LinearSceneCache` (`prepareLinearScene` / `developFromLinear` / `developFromLinearPixels`), and `RawDeveloper.develop` / `developPixels` aligned and pixel-consistent.
  - Preserve non-destructive editing (`Save copy` never overwrites originals), warm-illuminant attenuation, highlight headroom, `3000f / max(w, h)` grain scaling, and Ultra HDR gainmap border alignment.
- **State Machines & Resource Safety** (`docs/workflow-state-machines.md`, `docs/video-orientation-fix.md`):
  - Enforce generation/job ownership (`JobState`, `RequestEpoch`, `CaptureJobs`, `ResourceLease`) and `!isFinishing() && !isDestroyed()` before posting results to UI; recycle rejected bitmaps.
  - Publish media only via `MediaOutput` after stream close; delete video segments only after stitched publication.
  - Keep I/O, LUT decoding, `BitmapFactory`, and gallery indexing off the main thread; shut down executors in `onDestroy()`; never recycle bitmaps in `LruCache.entryRemoved()` if UI views may still reference them.
- **R8 & Unit Tests**:
  - Release builds use `android.enableR8.fullMode = true` + resource shrinking; update `app/proguard-rules.pro` if reflection is added.
  - Run/update JUnit tests in `app/src/test/java/com/scope/camera/` (`./gradlew testDebugUnitTest`) alongside any pipeline or state-machine change.
