# Lika Lens

Lika Lens is a pure-Java Android Camera2 app for point-and-shoot still photography, cinematic video, and non-destructive RAW darkroom editing. Inspired by Steve Yedlin and Rian Johnson's *Chromagraph*, it captures `RAW_SENSOR` where supported, writes an untouched DNG, and develops a finished JPEG from linear sensor data (falling back cleanly to JPEG grading when a lens doesn't expose RAW).

On Android 13+, a live `RuntimeShader` 33³ LUT previews the active film stock in real time on the viewfinder.

## Key Features

- **6 Film Stocks & Custom 3D LUTs**: *Lika / Film*, *Kodachrome / 64*, *Fuji / Eterna*, *Bleach Bypass*, *Mono / Silver*, and *Neutral / Raw*, plus `.cube` 3D LUT import (`LutManager`).
- **Oklab Colour Pipeline & Smart P&S (`[★ EASY]`)**: Illuminant-adaptive warmth neutralization, skin-tone protection, subtractive highlight roll-off, ISO-adaptive grain, and optical Lens Shading Map (LSM) vignette correction.
- **Viewfinder & Framing**: Curved 24dp viewfinder card, capsule zoom pill (`.5×` / `1×` / `2×` / `5×`) with 120 fps GPU scale-bridged `1× ↔ 0.5×` transitions, dual EV + shadow reticle sliders, manual focus loupe, live histogram/zebras/focus peaking, smart horizon level & flat-lay crosshair, and front-camera screen flash.
- **Cinematic Video**: 24/30/60 fps recording (up to 35 Mbps, H.264/HEVC), 180° shutter mode, pause/resume, full-grade still capture during recording, and mid-recording camera/lens switching with automatic multi-segment `.mp4` stitching.
- **Non-Destructive Darkroom & Gallery**: 4-tab editor (`TONE`, `COLOR`, `TEXTURE`, `CROP`), 1:1 native pixel loupe, zero-jump hold-to-compare against sensor RAW, recipe copy/paste, 35mm film rebate & museum matte borders, contact sheet generator, and **GIF Studio** export.
- **Privacy & Storage**: Configurable DNG policy (alongside JPEG, app-private archive, or JPEG-only), custom file prefixes/folders, automatic GPS stripping, and in-app GitHub release updates + settings backup/restore.

> Image direction is informed by [WIRED's interview with Steve Yedlin](https://www.wired.com/story/the-hollywood-color-nerd-who-shot-knives-out-explains-his-viral-iphone-camera-app/) and [GSMArena's review of Chromagraph](https://m.gsmarena.com/we_check_out_chromagraph_rian_johnsons_viral_iphone_camera_app-amp-75357.php). Lika Lens uses an original Oklab transform, not Chromagraph's proprietary math.

## Colour Science: Chromagraph vs. Lika Lens

| Pipeline Dimension | Chromagraph (iPhone) | Lika Lens (Native Android) |
| :--- | :--- | :--- |
| **Colour Space** | Static 3×3 matrix + Look LUT | **Oklab perceptual space** — lightness ($L$) is decoupled from chroma ($a, b$) to prevent hue skewing. |
| **Indoor Lighting** | Fixed warmth push can cast yellow-green under fluorescents/LEDs | **Illuminant-adaptive neutralization** analyzes `wbGains` to attenuate warmth under green-heavy artificial light. |
| **Subject Vibrance** | Muted default tuning | **Skin-protected vibrance** protects Oklab skin hues while boosting muted non-skin tones. |
| **Highlights** | High-contrast clipping on bright skies | **Subtractive highlight roll-off** desaturates bright speculars smoothly toward paper white. |
| **Film Grain** | Static overlay | **ISO-adaptive grain** scales down at low ISO for detail and pairs with chroma NR at high ISO. |
| **Point & Shoot** | Manual exposure awareness required | **`[★ EASY]` mode** enables scene-adaptive colour intelligence out of the box; toggle off for full manual control. |

## Adaptive Layouts

Lika Lens adapts across compact phones, foldables, and tablets (`WindowMetricsHelper`):
- **Portrait Phones (`< 600dp`)**: Vertical rangefinder layout with top controls, curved viewfinder, zoom pill, and bottom shutter deck.
- **Tablets, Foldables & Landscape (`>= 600dp`)**: Side-grip rail with thumb shutter/lens controls and seamless fold/unfold reflow without restarting the camera session.
- **Darkroom Editor & Gallery**: Dual-pane editor on wide screens and adaptive 2–6 column gallery grid with display-cutout / system-bar inset handling.

## Build & CI

- **Stack**: Pure Java 17 (`enableKotlin = false`, `android.useAndroidX = false`), zero third-party dependencies, `minSdk = 26`, `targetSdk = 35`.
- **Requirements**: JDK 17, Gradle **9.6.0**, Android SDK Platform 35, Build Tools 36.0.0.
- **Tests**: Run `gradle testDebugUnitTest` (34 JUnit test suites in `app/src/test/java/com/scope/camera/`).
- **GitHub Actions** ([`android-apk.yml`](.github/workflows/android-apk.yml)): Runs `testDebugUnitTest` and `assembleRelease` on pushes to `main`, PRs, and manual dispatch, publishing signed release APKs to GitHub Releases.

## Current Limits

RAW support varies by device, lens, and stream combination. Lika Lens uses `RAW_SENSOR` where supported and falls back to JPEG grading when a lens/stream combination (such as ultrawide `0.5×` on some devices) does not expose RAW. GPS metadata is stripped from exports for privacy.
