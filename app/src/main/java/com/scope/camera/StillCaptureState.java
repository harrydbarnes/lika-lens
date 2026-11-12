package com.scope.camera;

/** Explicit lifecycle states for still photo capture (timer, screen flash, sensor capture). */
enum StillCaptureState {
    IDLE,
    TIMER_COUNTDOWN,
    SCREEN_FLASH,
    CAPTURING;

    boolean isBusy() {
        return this != IDLE;
    }

    boolean isTimerRunning() {
        return this == TIMER_COUNTDOWN;
    }
}
