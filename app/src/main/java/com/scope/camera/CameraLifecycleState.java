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

    static final int MAX_OPEN_RETRIES = 6;

    static boolean shouldRetryOpen(int attempt) {
        return attempt >= 0 && attempt < MAX_OPEN_RETRIES;
    }

    static long reopenRetryDelayMs(int attempt) {
        if (attempt <= 0) return 200L;
        if (attempt == 1) return 350L;
        if (attempt == 2) return 600L;
        if (attempt == 3) return 1000L;
        if (attempt == 4) return 1500L;
        return 2000L;
    }
}
