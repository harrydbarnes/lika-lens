# Lika Lens

Lika Lens is a native Android Camera2 app for point-and-shoot still photography. It captures `RAW_SENSOR` where the device supports a RAW stream alongside the live preview, writes an untouched DNG, then develops a finished JPEG from sensor data. When RAW capture is unavailable, the app labels the JPEG fallback because the phone's rendering is already baked into that source.

The default **Lika / Film** rendering is intentionally opinionated. It is designed to feel darker and warmer than a typical phone JPEG, keep shadows deep, soften edge sharpening, roll off vivid colours, and add restrained grain and halation. The viewfinder remains a framing preview. The finished look is developed after capture.

## Camera and controls

- Clean point-and-shoot camera screen with one-tap RAW, Mono or Lika / Film mode switching.
- **Adjust** opens a quick control panel for exposure, warmth, shadow depth, contrast, colour intensity, saturated colour roll-off, highlight roll-off, grain, halation and sharpening. The panel is hidden until requested; changes apply to the next capture.
- Full settings page includes white balance, noise reduction, vignette, grain structure, RAW size, manual shutter and ISO, focus behaviour, frame shape, JPEG quality, file naming, export, haptics and composition grid.
- Camera2 live preview, rear lens switching, digital zoom, flash, timer and composition grid.
- Video mode records H.264 MP4 at up to 1080p / 30 fps, with microphone audio when allowed. Recording starts with touch controls locked; hold the unlock control for three seconds to unlock, then use the on-screen lock control to lock again. Leaving the app stops and saves the clip to Movies / Lika Lens.
- Video uses the phone's Camera2 video pipeline and does not yet receive the RAW photo colour rendering.
- RAW development uses sensor black and white levels, camera colour matrices and capture white-balance metadata before the Lika Lens display rendering.
- Output is cropped and rotated during rendering, with a memory-aware resolution up to 50 megapixels.
- A compressed private sensor archive supports per-photo re-development. The editor can adjust exposure, white balance, warmth, contrast, colour, colour roll-off, highlights, shadows, pivot, grain, halation, sharpening, colour noise reduction and vignette. Saving replaces the JPEG render and leaves the DNG and sensor archive intact.
- A date-grouped library, full-screen viewer, image import flow and editor. Existing SCOPE captures remain visible after the rename.

The image direction is informed by [WIRED's interview with Steve Yedlin](https://www.wired.com/story/the-hollywood-color-nerd-who-shot-knives-out-explains-his-viral-iphone-camera-app/) and [AppleInsider's overview of Chromagraph](https://appleinsider.com/articles/26/10/28/rian-johnsons-chromagraph-camera-app-brings-his-cinematic-style-to-your-iphone-photos). Lika Lens uses an original transform, not Chromagraph's proprietary colour math. Close visual matching still requires paired captures from the same phone and scene for calibration.

## Build in Android Studio

Open this folder as a project. Use JDK 17, Android Gradle Plugin 9.5.0, Gradle 9.6.0, Android SDK Platform 35 and Build Tools 36.0.0. AGP 9.5 requires Gradle 9.6.0 and uses Build Tools 36.0.0 by default. The Java-only app has no third-party library dependencies. The build targets API 35 and supports API 26 and newer.

The **Build Android APK** GitHub Actions workflow runs on pushes to `main`, pull requests and manual dispatch. It installs Android SDK Platform 35 and Build Tools 36.0.0, runs `assembleDebug`, and publishes `lika-lens-debug-apk` as a downloadable workflow artifact for 14 days.

To build locally, install the listed Android SDK components in Android Studio. Alternatively, push to `main` or run the workflow manually to produce a downloadable APK.

## Current limits

RAW support varies by device, camera and stream combination. Lika Lens tries the selected RAW size with the live preview and falls back to a phone-rendered JPEG if the combination is unsupported. Histogram, zebra and focus-peaking overlays, location metadata and optical corrections remain inactive.
