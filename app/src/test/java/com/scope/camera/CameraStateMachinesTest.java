package com.scope.camera;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for explicit camera, capture, and video state machines.
 */
public class CameraStateMachinesTest {

    @Test
    public void testCameraLifecycleStatePredicates() {
        assertTrue(CameraLifecycleState.CLOSED.canOpen());
        assertTrue(CameraLifecycleState.ERROR.canOpen());
        assertFalse(CameraLifecycleState.OPENING.canOpen());
        assertFalse(CameraLifecycleState.OPENED.canOpen());
        assertFalse(CameraLifecycleState.CONFIGURING_PREVIEW.canOpen());
        assertFalse(CameraLifecycleState.PREVIEW_READY.canOpen());
        assertFalse(CameraLifecycleState.CLOSING.canOpen());

        assertTrue(CameraLifecycleState.OPENING.isOpening());
        assertFalse(CameraLifecycleState.OPENED.isOpening());

        assertFalse(CameraLifecycleState.CLOSED.isStarted());
        assertFalse(CameraLifecycleState.OPENING.isStarted());
        assertTrue(CameraLifecycleState.OPENED.isStarted());
        assertTrue(CameraLifecycleState.CONFIGURING_PREVIEW.isStarted());
        assertTrue(CameraLifecycleState.PREVIEW_READY.isStarted());
        assertFalse(CameraLifecycleState.CLOSING.isStarted());
        assertFalse(CameraLifecycleState.ERROR.isStarted());
    }

    @Test
    public void testStillCaptureStatePredicates() {
        assertFalse(StillCaptureState.IDLE.isBusy());
        assertFalse(StillCaptureState.IDLE.isTimerRunning());

        assertTrue(StillCaptureState.TIMER_COUNTDOWN.isBusy());
        assertTrue(StillCaptureState.TIMER_COUNTDOWN.isTimerRunning());

        assertTrue(StillCaptureState.SCREEN_FLASH.isBusy());
        assertFalse(StillCaptureState.SCREEN_FLASH.isTimerRunning());

        assertTrue(StillCaptureState.CAPTURING.isBusy());
        assertFalse(StillCaptureState.CAPTURING.isTimerRunning());
    }

    @Test
    public void testInFlightCaptureJpegCompletesOnImageOnly() {
        List<String> disposed = new ArrayList<>();
        InFlightCapture<String, String> token = new InFlightCapture<>(1L, false);
        assertTrue(token.isActive());

        InFlightCapture.CompletedPair<String, String> pair = token.attachImage("jpeg-frame", disposed::add);
        assertNotNull(pair);
        assertEquals("jpeg-frame", pair.image);
        assertNull(pair.result);
        assertFalse(token.isActive());
        assertTrue(disposed.isEmpty());

        // Late image or result after completion is rejected and late image is disposed
        assertNull(token.attachResult("late-result"));
        assertNull(token.attachImage("late-image", disposed::add));
        assertEquals(1, disposed.size());
        assertEquals("late-image", disposed.get(0));
    }

    @Test
    public void testInFlightCaptureRawJoinsInBothOrders() {
        List<String> disposed = new ArrayList<>();

        // Image arrives first, metadata second
        InFlightCapture<String, String> first = new InFlightCapture<>(2L, true);
        assertNull(first.attachImage("raw-1", disposed::add));
        assertTrue(first.isActive());
        InFlightCapture.CompletedPair<String, String> completed1 = first.attachResult("meta-1");
        assertNotNull(completed1);
        assertEquals("raw-1", completed1.image);
        assertEquals("meta-1", completed1.result);
        assertFalse(first.isActive());

        // Metadata arrives first, image second
        InFlightCapture<String, String> second = new InFlightCapture<>(3L, true);
        assertNull(second.attachResult("meta-2"));
        assertTrue(second.isActive());
        InFlightCapture.CompletedPair<String, String> completed2 = second.attachImage("raw-2", disposed::add);
        assertNotNull(completed2);
        assertEquals("raw-2", completed2.image);
        assertEquals("meta-2", completed2.result);
        assertFalse(second.isActive());
        assertTrue(disposed.isEmpty());
    }

    @Test
    public void testInFlightCaptureCancelDisposesPendingImageAndIgnoresLateCallbacks() {
        List<String> disposed = new ArrayList<>();
        InFlightCapture<String, String> token = new InFlightCapture<>(4L, true);

        assertNull(token.attachImage("stale-image", disposed::add));
        token.cancel(disposed::add);
        assertFalse(token.isActive());
        assertEquals(1, disposed.size());
        assertEquals("stale-image", disposed.get(0));

        assertNull(token.attachResult("stale-result"));
        assertNull(token.attachImage("extra-image", disposed::add));
        assertEquals(2, disposed.size());
        assertEquals("extra-image", disposed.get(1));
    }

    @Test
    public void testInFlightCaptureReplacementImageDisposesPrevious() {
        List<String> disposed = new ArrayList<>();
        InFlightCapture<String, String> token = new InFlightCapture<>(5L, true);

        assertNull(token.attachImage("img-a", disposed::add));
        assertNull(token.attachImage("img-b", disposed::add));
        assertEquals(1, disposed.size());
        assertEquals("img-a", disposed.get(0));

        InFlightCapture.CompletedPair<String, String> pair = token.attachResult("meta");
        assertNotNull(pair);
        assertEquals("img-b", pair.image);
    }

    @Test
    public void testVideoStateMachineQuickTakeMutualExclusion() {
        VideoStateMachine sm = new VideoStateMachine();
        assertEquals(VideoStateMachine.State.IDLE, sm.getState());
        assertEquals(VideoStateMachine.Trigger.NONE, sm.getTrigger());
        assertTrue(sm.isIdle());
        assertFalse(sm.isBusy());

        assertFalse(sm.tryBeginQuickTake(VideoStateMachine.Trigger.NONE));
        assertFalse(sm.tryBeginQuickTake(VideoStateMachine.Trigger.STANDARD_VIDEO));

        assertTrue(sm.tryBeginQuickTake(VideoStateMachine.Trigger.QUICK_TAKE_SHUTTER));
        assertTrue(sm.isQuickTake());
        assertTrue(sm.isTriggeredBy(VideoStateMachine.Trigger.QUICK_TAKE_SHUTTER));
        assertFalse(sm.tryBeginQuickTake(VideoStateMachine.Trigger.QUICK_TAKE_VOLUME));
        assertFalse(sm.beginStandardTrigger());

        // Clearing with wrong trigger leaves current owner intact
        sm.clearQuickTakeTrigger(VideoStateMachine.Trigger.QUICK_TAKE_VOLUME);
        assertTrue(sm.isTriggeredBy(VideoStateMachine.Trigger.QUICK_TAKE_SHUTTER));

        sm.clearQuickTakeTrigger(VideoStateMachine.Trigger.QUICK_TAKE_SHUTTER);
        assertFalse(sm.isQuickTake());
        assertTrue(sm.tryBeginQuickTake(VideoStateMachine.Trigger.QUICK_TAKE_VOLUME));
        assertTrue(sm.isTriggeredBy(VideoStateMachine.Trigger.QUICK_TAKE_VOLUME));
    }

    @Test
    public void testVideoStateMachineRecordingPauseSwitchAndPermissionFlow() {
        VideoStateMachine sm = new VideoStateMachine();
        assertTrue(sm.beginStandardTrigger());
        assertTrue(sm.markPermissionPending());
        assertTrue(sm.isPermissionPending());
        assertFalse(sm.isBusy());

        sm.cancelPermissionPending();
        assertTrue(sm.isIdle());
        assertEquals(VideoStateMachine.Trigger.NONE, sm.getTrigger());

        assertTrue(sm.beginStandardTrigger());
        assertTrue(sm.markPermissionPending());
        assertTrue(sm.beginPreparing());
        assertTrue(sm.isPreparing());
        assertTrue(sm.isBusy());
        assertFalse(sm.tryBeginQuickTake(VideoStateMachine.Trigger.QUICK_TAKE_SHUTTER));

        assertTrue(sm.markRecording());
        assertTrue(sm.isRecordingOrPaused());
        assertFalse(sm.isPaused());

        assertTrue(sm.togglePause());
        assertTrue(sm.isPaused());
        assertTrue(sm.isRecordingOrPaused());

        // Camera switch allowed from both RECORDING and PAUSED
        assertTrue(sm.beginCameraSwitch());
        assertTrue(sm.isSwitchingCamera());
        assertTrue(sm.isBusy());
        assertFalse(sm.isRecordingOrPaused());

        assertTrue(sm.markRecording());
        assertTrue(sm.isRecordingOrPaused());

        sm.beginStopping();
        assertTrue(sm.isStopping());
        assertTrue(sm.isBusy());

        sm.reset();
        assertTrue(sm.isIdle());
        assertEquals(VideoStateMachine.Trigger.NONE, sm.getTrigger());
        assertFalse(sm.isBusy());
    }
}
