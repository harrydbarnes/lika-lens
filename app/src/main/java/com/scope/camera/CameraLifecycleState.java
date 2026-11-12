package com.scope.camera;

/** Explicit lifecycle states for the Camera2 device and preview capture session. */
enum CameraLifecycleState {
    CLOSED,
    OPENING,
    OPENED,
    CONFIGURING_PREVIEW,
    PREVIEW_READY,
    CLOSING,
    ERROR;

    boolean canOpen() {
        return this == CLOSED || this == ERROR;
    }

    boolean isStarted() {
        return this == OPENED || this == CONFIGURING_PREVIEW || this == PREVIEW_READY;
    }

    boolean isOpening() {
        return this == OPENING;
    }
}
