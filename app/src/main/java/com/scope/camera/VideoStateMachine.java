package com.scope.camera;

/**
 * Explicit state machine for video recording sessions and QuickTake dual-input arbitration.
 */
final class VideoStateMachine {

    enum State {
        IDLE,
        PERMISSION_PENDING,
        PREPARING,
        RECORDING,
        PAUSED,
        SWITCHING_CAMERA,
        STOPPING_STITCHING
    }

    enum Trigger {
        NONE,
        STANDARD_VIDEO,
        QUICK_TAKE_SHUTTER,
        QUICK_TAKE_VOLUME
    }

    private volatile State state = State.IDLE;
    private volatile Trigger trigger = Trigger.NONE;

    synchronized State getState() {
        return state;
    }

    synchronized Trigger getTrigger() {
        return trigger;
    }

    boolean isIdle() {
        return state == State.IDLE;
    }

    boolean isPermissionPending() {
        return state == State.PERMISSION_PENDING;
    }

    boolean isPreparing() {
        return state == State.PREPARING;
    }

    boolean isRecordingOrPaused() {
        State current = state;
        return current == State.RECORDING || current == State.PAUSED;
    }

    boolean isPaused() {
        return state == State.PAUSED;
    }

    boolean isSwitchingCamera() {
        return state == State.SWITCHING_CAMERA;
    }

    boolean isStopping() {
        return state == State.STOPPING_STITCHING;
    }

    boolean isBusy() {
        State current = state;
        return current == State.PREPARING
                || current == State.RECORDING
                || current == State.PAUSED
                || current == State.SWITCHING_CAMERA
                || current == State.STOPPING_STITCHING;
    }

    boolean isQuickTake() {
        Trigger current = trigger;
        return current == Trigger.QUICK_TAKE_SHUTTER || current == Trigger.QUICK_TAKE_VOLUME;
    }

    boolean isTriggeredBy(Trigger expected) {
        return trigger == expected;
    }

    synchronized boolean tryBeginQuickTake(Trigger quickTakeTrigger) {
        if (quickTakeTrigger != Trigger.QUICK_TAKE_SHUTTER
                && quickTakeTrigger != Trigger.QUICK_TAKE_VOLUME) {
            return false;
        }
        if (state != State.IDLE || trigger != Trigger.NONE) {
            return false;
        }
        trigger = quickTakeTrigger;
        return true;
    }

    synchronized void clearQuickTakeTrigger(Trigger quickTakeTrigger) {
        if (trigger == quickTakeTrigger) {
            trigger = Trigger.NONE;
        }
    }

    synchronized boolean beginStandardTrigger() {
        if (state != State.IDLE || trigger != Trigger.NONE) {
            return false;
        }
        trigger = Trigger.STANDARD_VIDEO;
        return true;
    }

    synchronized boolean markPermissionPending() {
        if (state != State.IDLE) return false;
        state = State.PERMISSION_PENDING;
        return true;
    }

    synchronized void cancelPermissionPending() {
        if (state == State.PERMISSION_PENDING) {
            state = State.IDLE;
            trigger = Trigger.NONE;
        }
    }

    synchronized boolean beginPreparing() {
        if (state != State.IDLE && state != State.PERMISSION_PENDING) {
            return false;
        }
        state = State.PREPARING;
        return true;
    }

    synchronized boolean markRecording() {
        if (state != State.PREPARING && state != State.SWITCHING_CAMERA) {
            return false;
        }
        state = State.RECORDING;
        return true;
    }

    synchronized boolean togglePause() {
        if (state == State.RECORDING) {
            state = State.PAUSED;
            return true;
        }
        if (state == State.PAUSED) {
            state = State.RECORDING;
            return true;
        }
        return false;
    }

    synchronized boolean beginCameraSwitch() {
        if (state != State.RECORDING && state != State.PAUSED) {
            return false;
        }
        state = State.SWITCHING_CAMERA;
        return true;
    }

    synchronized void beginStopping() {
        state = State.STOPPING_STITCHING;
    }

    synchronized void reset() {
        state = State.IDLE;
        trigger = Trigger.NONE;
    }
}
