# Camera-switch video orientation

The take keeps one playback rotation matrix, taken from the camera at recording start. Rear and front sensors can have opposite mounts (90° and 270° on the tested Pixel 11 Pro). Assigning the same MP4 orientation hint to every segment does not change their encoded pixels, so the switched camera could play upside down.

When a segment needs a different rotation, `VideoRotationSurface` inserts a recordable EGL surface between Camera2 and MediaRecorder. It retains the SurfaceTexture's valid crop, removes preview-specific producer rotation/mirroring, and rotates the pixels into the original take's coordinates before encoding. Segments with matching rotation retain the direct recording surface. Selfie video recordings retain the existing non-mirrored output. In video mode the front-camera preview is also unmirrored so the saved recording matches what was shown; photo mode retains its mirrored viewfinder and selfie-still preference. Tap-to-focus uses the displayed orientation.

Quarter-turn corrections fit the frame without stretching. Camera sessions, `VideoRotationSurface` draining/closing, `MediaRecorder.stop()`, and `CameraDevice.close()` run off the UI thread on `cameraHandler` synchronized via `videoPipelineLock`, handing off to the target camera cleanly in `onClosed` (with a 500 ms fallback) while skipping still `ImageReader` allocation and reusing cached HEVC Main10 / audio-capability probes. An initialization/render failure reports the error and retains footage already recorded instead of silently producing incorrectly oriented pixels.

## Device checks for preview parity

- Record front and rear video in portrait and landscape with recognisable text, then compare the live viewfinder, gallery thumbnail, opening animation and playback. Repeat with a front/rear switch during a take.
- Tap both sides of the front viewfinder in video mode and confirm focus/metering follow the tap. Save a video still with the selfie mirror setting both on and off, and check Quick Take in photo mode.

## Verification

- `assembleDebug` and `testDebugUnitTest`: passed, 81 tests, zero failures/errors.
- Regression tests cover both opposite-sensor switch directions, matching orientations, clockwise quarter turns, half-turn vertex mapping, producer-transform removal, and crop preservation.
- Reproduced the original upside-down selfie in a saved MP4 on Pixel 11 Pro.
- Recorded and decoded finalized rear → selfie → rear and selfie → rear → selfie takes. Selfie frames are upright; return switches complete. Video and stereo AAC tracks decode at native timestamp precision without errors.
- Live configuration: portrait, H.264, 1080p, 24 fps. Other devices, codecs, HDR and frame rates were not live-tested.
- QA videos are local-only and excluded from Git. Only videos created by this QA run were removed from the phone; pre-existing recordings were preserved.

References: [MediaRecorder orientation hints](https://developer.android.com/reference/android/media/MediaRecorder#setOrientationHint(int)), [SurfaceTexture transforms](https://developer.android.com/reference/android/graphics/SurfaceTexture#getTransformMatrix(float[])).
