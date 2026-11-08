# Lika Lens

Lika Lens is a native Android Camera2 app for point-and-shoot still photography and cinematic video. Inspired by Steve Yedlin and Rian Johnson's *Chromagraph*, it captures `RAW_SENSOR` where the device supports a RAW stream alongside the live preview, writes an untouched DNG, and develops a finished JPEG from sensor data. When RAW capture is unavailable, the app labels the JPEG fallback because the phone's rendering is already baked into that source.

The default **Lika / Film** rendering is intentionally opinionated. It is designed to feel darker and warmer than a typical phone JPEG, keep shadows deep, soften edge sharpening, roll off vivid colours, desaturate bright specular sources with subtractive highlight roll-off, and add restrained grain and halation. The viewfinder remains a framing preview. The finished look is developed after capture.

## Camera and controls

- **6 Signature cinematic film stocks**:
  - **Lika / Film**: Kodak Vision3 500T 35mm cinema negative stock — balanced toe, organic halation, amber warmth, and restrained 35mm grain.
  - **Kodachrome / 64**: Iconic vintage color reversal stock — punchy primaries, elevated midtone contrast, warm amber push, and crisp grain.
  - **Fuji / Eterna**: Soft cinema negative stock — gentle highlight roll-off, lifted vintage shadows with subtle cyan/teal undertones.
  - **Bleach Bypass**: Skip-bleach ENR chemistry — stark high contrast, 65% desaturated chroma, deep shadow density, and gritty silver texture.
  - **Mono / Silver**: Kodak Tri-X 400 silver gelatin monochrome — deep rich blacks, classic documentary contrast, and silver luminance texture.
  - **Neutral / Raw**: Linear unstyled sensor baseline — pure uncompressed reference grade for clean post-processing.
- **Processing and colour pipeline controls**:
  - **Tone curve geometry**: Choose between *Filmic S-Curve* (natural photographic shoulder and toe), *Linear Response* (direct sensor mapping), or *Log / Flat* (maximised dynamic range retention).
  - **Shadow profile**: Select *Deep Cinema Blacks* (crushed filmic toe), *Neutral / Open Shadows* (balanced detail retention), or *Lifted Vintage Blacks* (matte filmic floor).
  - **Highlight recovery**: Choose *Adaptive blend* (smooth saturation compression), *Subtractive roll-off* (authentic chromatic desaturation towards bright specular sources), or *Hard clip* (unmodified highlight edge).
  - **Grain response**: Select *Luminance-weighted* (emulsion behavior where grain is concentrated in midtones and cleaner in highlights/shadows) or *Uniform* (consistent texture across the entire tonal range).
  - **Colour space**: Support for *sRGB · Standard Display*, *Display P3 · Wide Gamut*, and *Rec. 709 · Cinema Broadcast*.
- **Viewfinder and shooting ergonomics**:
  - **Custom obsidian modal bottom sheets**: Bottom-anchored dark obsidian modal sheets with signature gold checkmarks (`#D7B574`), descriptions, and tactile feedback for film brain selection, aspect ratios, and video settings.
  - **Configurable long-press gesture**: Assign viewfinder long-press to *AE / AF lock*, *Lock exposure only (AE-L)*, *Reset exposure bias*, or *None* — intelligently guarded against vertical EV drag to avoid interaction clashes.
  - **Configurable double-tap gesture**: Double-tap viewfinder to *Toggle 1× / 2× zoom*, *Reset exposure bias*, or *None*.
  - **Reticle metering mode**: Configure tap targeting between *AF + AE Metering*, *AF Only (Continuous AE)*, or *AE Only (Continuous AF)*.
  - **Horizon sensitivity deadband**: Choose *Normal (±1.0°)* or *Precise (±0.5°)* deadband tolerance for the artificial horizon level indicator, backed by automatic hysteresis to eliminate jitter.
  - **Configurable volume rocker**: Hardware volume buttons assignable to *Shutter release*, *EV compensation bias* (±0.33 EV steps), *Zoom 1× / 2×*, or *Off* (system volume).
  - **Shutter ocular wink**: Toggleable 80ms leaf-shutter curtain blackout animation across the viewfinder on capture, simulating physical film camera blind closure.
  - **Anti-banding / flicker compensation**: Camera2 AC lighting cycle compensation toggleable between *Auto (50/60 Hz)*, *50 Hz (Europe/Asia)*, *60 Hz (Americas/Japan)*, or *Off*.
- **File management and output pipeline**:
  - **DNG RAW policy**: Choose between *Save DNG beside JPEG (Pictures/RAW)*, *Save in app-private archive only* (for non-cluttering in-app re-development without polluting device photo galleries), or *Do not save RAW (JPEG only)*.
  - **Folder organisation**: Save captures to *Single folder (Pictures/Lika Lens)*, *Organise by month (YYYY-MM)*, or *Organise by film look* (e.g. `Pictures/Lika Lens/KODACHROME_64`).
  - **File naming patterns**: Choose between *Prefix + Timestamp* (e.g. `LIKA_20261107_162345`), *Film Look + Timestamp* (e.g. `KODACHROME64_20261107_162345`), or *Monotonic Roll Counter* (e.g. `LIKA_ROLL_0042`).
  - **JPEG quality tiers**: Configurable compression factor between *Maximum · 98% (Archival)*, *High · 95% (Recommended)*, *Balanced · 90%*, and *Compact · 85%*.
  - **EXIF parameters toggle**: Toggle embedding of detailed camera shooting parameters (`TAG_EXPOSURE_TIME`, `TAG_ISO_SPEED_RATINGS`, `TAG_FOCAL_LENGTH`, `TAG_F_NUMBER`).
  - **Privacy GPS stripping**: Automatic stripping of all GPS and location coordinates from exported files for privacy preservation.
- **Grouped inset settings architecture**: Clean grouped card sections with hairline dividers (`#1F2628`) and obsidian dark surfaces, replacing fragmented card layouts with cohesive Leica FOTOS-inspired styling.
- **Live interactive hero preview with hold-to-compare**: Real-time re-development hero preview in Settings that reflects slider and look changes dynamically. Touch and hold the hero to reveal the unedited raw capture with an `ORIGINAL` badge, releasing smoothly cross-fades back to the developed look.
- **Focus reticle & vertical EV bias slide**: Animated gold cine bracket reticle with center pip that springs from 1.35× to 1.0× on viewfinder tap, setting mapped sensor AF/AE regions. A vertical exposure bias line with a golden sun icon tracks vertical drags to adjust EV compensation in real-time with numeric readouts (`+0.7 EV`, `-1.0 EV`), smoothly fading out after 3.5s of inactivity.
- **Exposure lock (AE-L) & telemetry tap**: Tapping the telemetry bar or reticle locks current sensor exposure parameters (`CONTROL_AE_LOCK`) and displays a gold `AE-L` indicator.
- **35mm equivalent focal length labels**: Zoom preset buttons feature two-line formatted labels displaying both magnification factor and 35mm full-frame optical focal length equivalents (`.5× / 14mm`, `1× / 28mm`, `2× / 50mm`, `5× / 135mm`).
- **Tactile shutter button & damped spring physics**: Custom spring physics on the mechanical shutter release button. Touching down scales the shutter inward to 0.88× with a subtle `KEYBOARD_TAP` click; releasing springs outward with an `OvershootInterpolator` and fires a crisp `CONTEXT_CLICK` haptic pulse with touch-slop cancel protection.
- **Mechanical leaf shutter audio**: Dedicated leaf shutter audio playback via `MediaActionSound.SHUTTER_CLICK` on photo capture and video start/stop tones, toggleable under **Capture > Shutter sound**.
- **Precision slider detents & instant reset**: Tactile detents (`KEYBOARD_TAP`) when crossing neutral landmark positions (0.0 EV, 50 neutral warmth, 100 contrast/saturation, 50 pivot, 1.0×/2.0×/5.0× zoom) and micro-ticks (`CLOCK_TICK`) across scrubbing. In the editor, every slider features a dedicated hairline `RESET` tag and value tap listener to instantly restore that parameter to its neutral landmark default.
- **Zero-latency hold-to-compare**: Both the Post-Capture Editor and Full-Screen Photo Viewer feature hold-to-compare (`COMPARE` header pill or long-press on photo/preview) that instantly decodes and displays the neutral untouched RAW sensor original with a gold `ORIGINAL SENSOR RAW` badge, cross-fading back to the developed grade on release.
- **1:1 Native pixel crop loupe inspector**: Double-tapping the photo in the editor toggles 1:1 unscaled pixel crop mode with a `1:1 LOUPE · 100% CROP` badge, allowing cinematographers to closely inspect film grain structure, halation edges, and sensor micro-contrast at native 100% resolution.
- **6-Look scrollable preset bar**: Instant aesthetic look switching in the editor across all 6 film stocks: `LIKA / FILM`, `KODACHROME / 64`, `FUJI / ETERNA`, `BLEACH BYPASS`, `MONO / SILVER`, and `NEUTRAL / RAW`.
- **Cinematic aspect ratios**: Viewfinder guides, live capture and post-capture development in `2.39:1` (Anamorphic Cinemascope), `1.85:1` (Flat Widescreen), `1.66:1` (European Widescreen), `16:9` (HD), `3:2` (35mm), `4:3` (Standard) and `1:1` (Square). Aspect ratio switches in the editor are completely lossless because the raw sensor bounds are preserved in full.
- **Cinematic video mode**: Enhanced motion recording with dedicated **24 fps cinematic mode** (matching the 180° shutter standard with `1/48s` telemetry), 30 fps and 60 fps options, high-bitrate encoding (up to 35 Mbps), and 2.39:1 Scope video letterbox framing. Includes recording lock overlay to prevent accidental touches.
- **Visual level (artificial horizon)**: Real-time sensor-driven horizon bar in the viewfinder with a center reference reticle. Rotates smoothly with device roll across portrait and landscape orientations, illuminating in signature gold (`#D7B574`) when level within the configured deadband.
- **High-performance RAW pipeline**: Multi-threaded CPU RAW development via parallel ForkJoin row processing (`IntStream.range().parallel()`) for 3-4x faster rendering without UI stalls.
- **Subtractive highlight roll-off & ambient warmth**: Eliminates fluorescent/indoor greenish-yellow casts by adaptively balancing color gains and smoothly desaturating highlights toward luminance white.
- **4-Tab post-capture editor**: Reorganized into `TONE` (exposure, contrast, pivot, shadows, highlights), `COLOR` (warmth, white balance, saturation, colour roll-off, colour NR), `TEXTURE` (grain intensity, grain structure 35mm/16mm/8mm, halation, vignette, sharpening), and `CROP` (aspect ratio pills and horizontal flip).
- **Interactive photo viewer with zoom HUD**: Pinch-to-zoom (1.0× to 5.0×) with fluid pan tracking, double-tap 2.5× loupe zoom, dynamic magnification HUD (`100% · FIT`, `250% · DETAIL`), downward swipe-to-dismiss gesture, system photo sharing (`Intent.ACTION_SEND`), and an animated photographic EXIF metadata inspection card.
- **Lens memory**: Restores the user's preferred default lens (`Auto / Wide 1×`, `Ultra-wide .5×`, `Telephoto 2×/5×`, or `Last used`).
- **Quick look panel**: Tap **ADJUST** on the camera screen for quick-access sliders that apply to subsequent captures.
- **Clean point-and-shoot camera screen**: One-tap film brain switching with custom bottom sheet, haptic shutter, self-timer, and flash controls.

The image direction is informed by [WIRED's interview with Steve Yedlin](https://www.wired.com/story/the-hollywood-color-nerd-who-shot-knives-out-explains-his-viral-iphone-camera-app/) and [GSM Arena's review of Chromagraph](https://m.gsmarena.com/we_check_out_chromagraph_rian_johnsons_viral_iphone_camera_app-amp-75357.php). Lika Lens uses an original transform, not Chromagraph's proprietary colour math.

## Colour Science: Chromagraph vs. Lika Lens

Both Chromagraph (by director Rian Johnson and team) and Lika Lens stem from the color science philosophy of cinematographer **Steve Yedlin, ASC** (*Knives Out*, *Star Wars: The Last Jedi*, *Looper*). Rather than accepting synthetic mobile processing (aggressive edge-sharpening halos, local tone-mapping HDR flattening, oversaturated unnatural skies), both apps intercept uncompressed raw sensor Bayer data and apply an authored display preparation transform.

However, real-world reviews of Chromagraph revealed critical practical shortcomings that Lika Lens explicitly identifies and solves:

| Pipeline Dimension | Chromagraph (iPhone) | Lika Lens (Native Android) |
| :--- | :--- | :--- |
| **Color Space Math** | Static 3×3 matrix with proprietary Look LUT | **Oklab Perceptual Color Space** — lightness ($L$) is mathematically decoupled from chromatic axes ($a, b$), eliminating dirty hue skewing when adjusting contrast. |
| **Indoor / Artificial Light** | Fixed warmth push yields an unpleasant **yellow-green / jaundiced cast** under fluorescent, LED office, and mixed artificial lighting. | **Illuminant-Adaptive Neutralization**: Continuously analyzes sensor `wbGains`. When green-gain spikes occur under fluorescent fixtures, base warmth is dynamically attenuated and compensatory magenta/blue offsets are applied, keeping neutrals clean while preserving golden warmth under daylight. |
| **Everyday Subject Vibrance** | Default tuning is muted and understated. Everyday subjects (food, clothing, flowers, city streets) often look **dull, muddy, or lifeless**. | **Selective Filmic Vibrance with Skin Protection**: Protects human skin tones in an anatomical Oklab hue corridor ($\theta = \text{atan2}(b, a) \in [0.45, 1.35]$ rad) while applying non-linear vibrance ($+22\%$) to low/mid-chroma non-skin tones. Everyday scenes pop with vitality without digital harshness. |
| **Contrast & Dynamic Range** | Unforgiving high contrast. Discarding computational HDR without adaptive curves causes **harsh white sky blowouts or crushed shadows**. | **Adaptive Shoulder & Highlight Recovery**: Subtractive highlight desaturation gracefully rolls bright specular sources and skies toward film paper white rather than clipping to nuclear cyan/yellow. Shadow toe response prevents crushing deep cinema blacks. |
| **Noise & Emulsion Texture** | Static simulated grain overlay regardless of ambient lighting conditions. | **ISO-Adaptive Film Grain**: At low ISO (< 100), grain scales down to 0.75× to preserve native lens micro-contrast and resolving power. At high ISO (> 800), grain scales to 0.65× and chroma denoise engages to prevent clumping on top of sensor thermal noise. |
| **Usability & Ergonomics** | Requires manual exposure compensation and lighting awareness; prone to ruined shots for casual users. | **Smart Point & Shoot ("Easy Mode")**: 1-tap viewfinder toggle (`[★ EASY]`) that enables scene-adaptive color intelligence out of the box. Point and shoot with full film transform benefits without fiddling with manual sliders. |

### Smart Point & Shoot ("Easy Mode")

Lika Lens features an instant **`[★ EASY]`** button right on the camera viewfinder in the bottom control strip:
- **`[★ EASY]` Active (Default)**: Viewfinder displays `SMART P&S · SCENE & ILLUMINANT ADAPTIVE`. Scene-adaptive warmth neutralization, intelligent skin protection, smart filmic vibrance, adaptive highlight compression, and ISO-scaled grain are all active in real time. Perfect for effortless daily shooting.
- **`[EASY]` Inactive (Pro Mode)**: Viewfinder displays `RAW DEVELOPED AFTER CAPTURE`. Full manual developer parameters (custom curves, specific Kelvin shifts, unadjusted film saturation) take complete precedence for cinematographers seeking absolute manual control.
- **Dedicated Settings Controls**: Under **Settings > Smart Point & Shoot**, users can independently toggle *Easy point & shoot mode*, *Intelligent skin tone protection*, and *Smart filmic vibrance*.

## Build in Android Studio

Open this folder as a project. Use JDK 17, Android Gradle Plugin 9.5.0, Gradle 9.6.0, Android SDK Platform 35 and Build Tools 36.0.0. The Java-only app has no third-party library dependencies. The build targets API 35 and supports API 26 and newer.

The **Build Android APK** GitHub Actions workflow runs on pushes to `main`, pull requests and manual dispatch. It installs Android SDK Platform 35 and Build Tools 36.0.0, runs `assembleDebug`, and publishes `lika-lens-debug-apk` as a downloadable workflow artifact for 14 days.

## Current limits

RAW support varies by device, camera and stream combination. Lika Lens tries the selected RAW size with the live preview and falls back to a phone-rendered JPEG if the combination is unsupported. Histogram, zebra and focus-peaking overlays, location metadata and optical corrections remain inactive.
