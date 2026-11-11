# Lika Lens interface comparison — 11 November 2026

Open `index.html` directly or visit the local preview at http://127.0.0.1:8766/ while its server is running. No publication was performed.

Four comparison boards: Camera, Library, Editor & Looks, Settings. Original screenshots and generated concepts are retained separately for inspection at full resolution. Generated concepts are illustrative UI proposals, not implemented screens. The built-in image generation tool used current app screenshots as references, preserving the dark/gold identity. It was prompted to simplify camera controls and expose Library, move library operations into selection, reduce the editor toolbar and unify Looks, and organise Settings around persistent defaults. Images may alter photographic content; no image-processing comparison is implied.

## Changes installed on the Pixel 11 Pro

- Camera header now clears the full status-bar and cutout inset, even in immersive mode. Removed the previous central-punch-hole alignment that put controls in Android's privacy-indicator lane. Header top moved from 44px to 193px on this phone. The viewfinder's layout uses the same safe margin.
- Removed an unconditional preview attachment in PhotoEditorActivity. It was subsequently attached a second time by both compact and dual-pane layout branches, causing a verified IllegalStateException when Edit opened.
- Installed version 0.1.79-insets-fix with version code 79 over the existing app using adb install -r. No app data was cleared. No photos were taken, saved or deleted. Broader redesigns remain concepts.

## Validation

- assembleDebug succeeded.
- testDebugUnitTest: 74 tests, zero failures/errors.
- git diff --check passed.
- Camera checked with Android's transient status bar revealed; controls are below the status lane. The expanded privacy pill was captured overlapping controls before the fix; the post-fix status screenshot demonstrates the reserved lane without an expanded pill.
- Editor opened on an existing RAW-backed frame and developed its preview. The screenshot shows its unchanged UI after the startup repair, including the narrow wrapping DEVELOP heading. The proposed editor uses a different existing photograph.
- No tablet/landscape, large-font or TalkBack validation performed. No recording, export or image-quality validation claimed.

## Local build toolchain

The existing Java installation was a JRE. Portable JDK 17 and Android SDK components were downloaded under `output/toolchain/` without changing global environment settings. Build commands set JAVA_HOME, ANDROID_HOME and TEMP/TMP only for that process. The toolchain and temporary files are locally ignored.

## Capture notes

01-camera-before.png, 02-library-before.png and 05-settings-before.png are captures of the original installed app. 00-camera-privacy-before.png captures the original overlap. 04-editor-before.png is the existing design after its startup repair. 06-camera-fixed.png is the unobstructed fixed camera, and 08-camera-status-fixed.png shows Android status icons above the fixed controls.
