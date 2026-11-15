# GitHub Copilot Instructions — Lika Lens

Before modifying code in `com.scope.camera`, follow [`AGENTS.md`](../AGENTS.md) and [`docs/`](../docs/):

1. **Stack**: Pure Android Java 17 (`minSdk = 26`, `targetSdk = 35`, `enableKotlin = false`, `android.useAndroidX = false`). Use native Android Views and `UiKit` rather than external libraries or Kotlin.
2. **Colour & Processing Pipeline** (`docs/capture-processing-consistency.md`):
   - Freeze capture settings, `CaptureOrientation`, and pre-look `previewMidTarget` at shutter time.
   - Keep `PreviewLookLut` live preview aligned with `RawDeveloper.renderDisplayRgb`, and keep `RawDeveloper.LinearSceneCache` (`developFromLinearPixels`) pixel-identical to `RawDeveloper.developPixels`.
   - Keep photo editing non-destructive (`Save copy` never mutates original captures).
3. **Async & Lifecycle Safety** (`docs/workflow-state-machines.md`):
   - Gate UI callbacks with job/epoch tickets (`JobState`, `RequestEpoch`, `CaptureJobs`, `ResourceLease`) and activity liveness checks; recycle discarded bitmaps.
   - Publish files atomically through `MediaOutput`; never run disk/image/LUT decoding on the UI thread; shut down executors in `onDestroy()`.
4. **Release Build & Tests**:
   - Respect `android.enableR8.fullMode = true` and `app/proguard-rules.pro` for any reflection or API-level guards.
   - Keep all unit tests in `app/src/test/java/com/scope/camera/` (`./gradlew testDebugUnitTest`) passing and add regression coverage for pipeline, LUT, GIF, orientation, and state-machine changes.
