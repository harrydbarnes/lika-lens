# Lika Lens

Lika Lens is a native Android Camera2 app for point-and-shoot still photography and cinematic video. Inspired by Steve Yedlin and Rian Johnson's *Chromagraph*, it captures `RAW_SENSOR` where the device supports a RAW stream alongside the live preview, writes an untouched DNG, and develops a finished JPEG from sensor data. When RAW capture is unavailable, the app labels the JPEG fallback because the phone's rendering is already baked into that source.

The default **Lika / Film** rendering is intentionally opinionated. It is designed to feel darker and warmer than a typical phone JPEG, keep shadows deep, soften edge sharpening, roll off vivid colours, desaturate bright specular sources with subtractive highlight roll-off, and add restrained grain and halation. The viewfinder remains a framing preview. The finished look is developed after capture.

## Camera and controls

- **Cinematic aspect ratios**: Viewfinder guides, live capture and post-capture development in `2.39:1` (Anamorphic Cinemascope), `1.85:1` (Flat Widescreen), `1.66:1` (European Widescreen), `16:9` (HD), `3:2` (35mm), `4:3` (Standard) and `1:1` (Square). Aspect ratio switches in the editor are completely lossless because the raw sensor bounds are preserved in full.
- **Cinematic video mode**: Enhanced motion recording with dedicated **24 fps cinematic mode** (matching the 180° shutter standard with `1/48s` telemetry), 30 fps and 60 fps options, high-bitrate encoding (up to 35 Mbps), and 2.39:1 Scope video letterbox framing. Includes recording lock overlay to prevent accidental touches.
- **Visual level (artificial horizon)**: Real-time sensor-driven horizon bar in the viewfinder with a center reference reticle. Rotates smoothly with device roll across portrait and landscape orientations, illuminating in signature gold (`#D7B574`) when level within ±1.0°.
- **High-performance RAW pipeline**: Multi-threaded CPU RAW development via parallel ForkJoin row processing (`IntStream.range().parallel()`) for 3-4x faster rendering without UI stalls.
- **Subtractive highlight roll-off & ambient warmth**: Eliminates fluorescent/indoor greenish-yellow casts by adaptively balancing color gains and smoothly desaturating highlights toward luminance white.
- **4-Tab post-capture editor**: Reorganized into `TONE` (exposure, contrast, pivot, shadows, highlights), `COLOR` (warmth, white balance, saturation, colour roll-off, colour NR), `TEXTURE` (grain intensity, grain structure 35mm/16mm/8mm, halation, vignette, sharpening), and `CROP` (aspect ratio pills and horizontal flip).
- **Interactive photo viewer**: Pinch-to-zoom (1.0× to 5.0×) with fluid pan tracking, double-tap zoom, downward swipe-to-dismiss gesture, system photo sharing (`Intent.ACTION_SEND`), and an animated photographic EXIF metadata inspection card.
- **Photographic EXIF metadata**: All saved JPEGs include standard EXIF tags (`TAG_EXPOSURE_TIME`, `TAG_PHOTOGRAPHIC_SENSITIVITY`, `TAG_FOCAL_LENGTH`, `TAG_F_NUMBER`, `TAG_MAKE`, `TAG_MODEL`, `TAG_SOFTWARE`).
- **Lens memory**: Restores the user's preferred default lens (`Auto / Wide 1×`, `Ultra-wide .5×`, `Telephoto 2×/5×`, or `Last used`).
- **Quick look panel**: Tap **ADJUST** on the camera screen for quick-access sliders that apply to subsequent captures.
- **Clean point-and-shoot camera screen**: One-tap RAW, Mono or Lika / Film mode switching, haptic shutter, self-timer, and flash controls.

The image direction is informed by [WIRED's interview with Steve Yedlin](https://www.wired.com/story/the-hollywood-color-nerd-who-shot-knives-out-explains-his-viral-iphone-camera-app/) and [GSM Arena's review of Chromagraph](https://m.gsmarena.com/we_check_out_chromagraph_rian_johnsons_viral_iphone_camera_app-amp-75357.php). Lika Lens uses an original transform, not Chromagraph's proprietary colour math.

## Build in Android Studio

Open this folder as a project. Use JDK 17, Android Gradle Plugin 9.5.0, Gradle 9.6.0, Android SDK Platform 35 and Build Tools 36.0.0. The Java-only app has no third-party library dependencies. The build targets API 35 and supports API 26 and newer.

The **Build Android APK** GitHub Actions workflow runs on pushes to `main`, pull requests and manual dispatch. It installs Android SDK Platform 35 and Build Tools 36.0.0, runs `assembleDebug`, and publishes `lika-lens-debug-apk` as a downloadable workflow artifact for 14 days.

## Current limits

RAW support varies by device, camera and stream combination. Lika Lens tries the selected RAW size with the live preview and falls back to a phone-rendered JPEG if the combination is unsupported. Histogram, zebra and focus-peaking overlays, location metadata and optical corrections remain inactive.
