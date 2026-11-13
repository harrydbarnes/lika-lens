package com.scope.camera;

/**
 * Explicit state machine for video recording sessions and QuickTake dual-input arbitration.
 */
final class VideoStateMachine {

    enum State {
        IDLE,
        PERMISSION_PENDING,
        PERMISSION_READY,
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
    private long takeId;
    private State resumeAfterSwitch = State.RECORDING;

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
        ++takeId;
        trigger = quickTakeTrigger;
        return true;
    }

    synchronized void clearQuickTakeTrigger(Trigger quickTakeTrigger) {
        if (trigger == quickTakeTrigger) {
            trigger = Trigger.NONE;
            if (state == State.PERMISSION_PENDING || state == State.PERMISSION_READY) { state = State.IDLE; ++takeId; }
        }
    }

    synchronized boolean beginStandardTrigger() {
        if (state != State.IDLE || trigger != Trigger.NONE) {
            return false;
        }
        ++takeId;
        trigger = Trigger.STANDARD_VIDEO;
        return true;
    }

    synchronized boolean markPermissionPending() {
        if (state != State.IDLE) return false;
        state = State.PERMISSION_PENDING;
        return true;
    }

    synchronized boolean markPermissionReady() {
        if(state!=State.PERMISSION_PENDING) return false;
        state=State.PERMISSION_READY;return true;
    }
    boolean isPermissionReady() { return state==State.PERMISSION_READY; }

    synchronized void cancelPermissionPending() {
        if (state == State.PERMISSION_PENDING || state == State.PERMISSION_READY) {
            ++takeId;
            state = State.IDLE;
            trigger = Trigger.NONE;
        }
    }

    synchronized boolean beginPreparing() {
        if (trigger == Trigger.NONE) return false;
        if (state != State.IDLE && state != State.PERMISSION_READY) {
            return false;
        }
        state = State.PREPARING;
        return true;
    }

    synchronized boolean markRecording() {
        if (state != State.PREPARING && state != State.SWITCHING_CAMERA) {
            return false;
        }
        state = state == State.SWITCHING_CAMERA ? resumeAfterSwitch : State.RECORDING;
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
        resumeAfterSwitch = state;
        state = State.SWITCHING_CAMERA;
        return true;
    }

    synchronized void beginStopping() {
        state = State.STOPPING_STITCHING;
    }

    synchronized long takeId() { return takeId; }
    synchronized boolean owns(long id) { return id == takeId; }

    synchronized void reset() {
        ++takeId;
        resumeAfterSwitch = State.RECORDING;
        state = State.IDLE;
        trigger = Trigger.NONE;
    }
}
