# Camera-switch video orientation

The take keeps one playback rotation matrix, taken from the camera at recording start. Rear and front sensors can have opposite mounts (90° and 270° on the tested Pixel 11 Pro). Assigning the same MP4 orientation hint to every segment does not change their encoded pixels, so the switched camera could play upside down.

When a segment needs a different rotation, `VideoRotationSurface` inserts a recordable EGL surface between Camera2 and MediaRecorder. It retains the SurfaceTexture's valid crop, removes preview-specific producer rotation/mirroring, and rotates the pixels into the original take's coordinates before encoding. Segments with matching rotation retain the direct recording surface. The preview is independent, and selfie recordings retain the existing non-mirrored output.

Quarter-turn corrections fit the frame without stretching. Camera sessions and rendering are stopped before recorder teardown; the EGL context, texture and surfaces are released on their owning thread. An initialization/render failure reports the error and retains footage already recorded instead of silently producing incorrectly oriented pixels.

## Verification

- `assembleDebug` and `testDebugUnitTest`: passed, 81 tests, zero failures/errors.
- Regression tests cover both opposite-sensor switch directions, matching orientations, clockwise quarter turns, half-turn vertex mapping, producer-transform removal, and crop preservation.
- Reproduced the original upside-down selfie in a saved MP4 on Pixel 11 Pro.
- Recorded and decoded finalized rear → selfie → rear and selfie → rear → selfie takes. Selfie frames are upright; return switches complete. Video and stereo AAC tracks decode at native timestamp precision without errors.
- Live configuration: portrait, H.264, 1080p, 24 fps. Other devices, codecs, HDR and frame rates were not live-tested.
- QA videos are local-only and excluded from Git. Only videos created by this QA run were removed from the phone; pre-existing recordings were preserved.

References: [MediaRecorder orientation hints](https://developer.android.com/reference/android/media/MediaRecorder#setOrientationHint(int)), [SurfaceTexture transforms](https://developer.android.com/reference/android/graphics/SurfaceTexture#getTransformMatrix(float[])).
