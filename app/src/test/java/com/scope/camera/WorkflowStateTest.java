package com.scope.camera;

import org.junit.Test;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import static org.junit.Assert.*;

public class WorkflowStateTest {
    @Test public void obsoletePreviewCannotReplaceOrFailLatestPreview() {
        JobState job=new JobState();long old=job.begin(JobState.Phase.RENDERING);
        long latest=job.begin(JobState.Phase.RENDERING);
        assertFalse(job.move(old,JobState.Phase.READY));assertFalse(job.move(old,JobState.Phase.FAILED));
        assertTrue(job.move(latest,JobState.Phase.READY));assertFalse(job.isBusy());
        assertFalse(job.move(latest,JobState.Phase.RENDERING));assertFalse(job.move(latest,JobState.Phase.FAILED));
    }
    @Test public void exportIsSingleFlightAndRetryableAfterFailure() {
        JobState job=new JobState();long first=job.tryBegin(JobState.Phase.EXTRACTING);
        assertNotEquals(0,first);assertEquals(0,job.tryBegin(JobState.Phase.EXTRACTING));
        job.move(first,JobState.Phase.FAILED);assertNotEquals(0,job.tryBegin(JobState.Phase.EXTRACTING));
    }
    @Test public void cancellationAndDisposalRejectLateCompletionAndProgress() {
        JobState job=new JobState();long ticket=job.begin(JobState.Phase.LOADING);job.cancel();
        assertFalse(job.accepts(ticket));long next=job.begin(JobState.Phase.SAVING);job.close();
        assertFalse(job.move(next,JobState.Phase.READY));assertEquals(0,job.begin(JobState.Phase.LOADING));
    }
    @Test public void oldCameraAndSessionCallbacksLoseOwnership() {
        RequestEpoch camera=new RequestEpoch();long first=camera.next();camera.invalidate();assertFalse(camera.owns(first));
        long second=camera.next();assertTrue(camera.owns(second));assertFalse(camera.owns(first));
    }
    @Test public void failedVideoJobPreservesEverySegmentAndFreezesInputList() {
        ArrayList<File> files=new ArrayList<>(Arrays.asList(new File("one.mp4"),new File("two.mp4")));
        VideoSaveJob job=new VideoSaveJob(files);files.clear();job.stitching();job.failed();
        assertEquals(2,job.segments.size());assertFalse(job.mayDeleteSegments());
        assertEquals(VideoSaveJob.Phase.RECOVERABLE_FAILURE,job.phase());
    }
    @Test public void segmentCleanupRequiresSuccessfulPublication() {
        VideoSaveJob job=new VideoSaveJob(Arrays.asList(new File("one.mp4")));
        assertFalse(job.mayDeleteSegments());job.publishing();assertFalse(job.mayDeleteSegments());
        job.saved();assertTrue(job.mayDeleteSegments());
    }
    @Test public void captureQueueTracksConcurrentJobsAndRetainsFailureOutcome() {
        CaptureJobs jobs=new CaptureJobs();long one=jobs.begin(),two=jobs.begin();
        jobs.move(one,CaptureJobs.Phase.ARCHIVING);jobs.finish(two,false);
        assertEquals(1,jobs.size());assertEquals(CaptureJobs.Phase.FAILED,jobs.phase(two));
        jobs.finish(one,true);assertEquals(0,jobs.size());assertEquals(CaptureJobs.Phase.SAVED,jobs.phase(one));
    }
    @Test public void publicationRejectsUnwrittenOrDiscardedOutputs() {
        MediaOutput.Lifecycle state=new MediaOutput.Lifecycle();
        assertThrows(IllegalStateException.class,state::publish);state.open();
        assertThrows(IllegalStateException.class,state::publish);state.discard();state.written();
        assertThrows(IllegalStateException.class,state::publish);
    }
    @Test public void successfulPublicationSurvivesAutomaticClose() {
        MediaOutput.Lifecycle state=new MediaOutput.Lifecycle();state.open();state.written();state.publish();state.discard();
        assertEquals(MediaOutput.Phase.PUBLISHED,state.phase());
        assertThrows(IllegalStateException.class,state::open);
    }
    @Test public void quickTakeReleaseCancelsPendingPermissionIntent() {
        VideoStateMachine video=new VideoStateMachine();video.tryBeginQuickTake(VideoStateMachine.Trigger.QUICK_TAKE_SHUTTER);
        long intent=video.takeId();video.markPermissionPending();video.clearQuickTakeTrigger(VideoStateMachine.Trigger.QUICK_TAKE_SHUTTER);
        assertFalse(video.isPermissionPending());assertFalse(video.owns(intent));assertFalse(video.beginPreparing());
    }
    @Test public void pausedCameraSwitchReturnsToPausedAndNewTakeInvalidatesOldCallbacks() {
        VideoStateMachine video=new VideoStateMachine();video.beginStandardTrigger();video.beginPreparing();video.markRecording();
        video.togglePause();video.beginCameraSwitch();video.markRecording();assertTrue(video.isPaused());
        long old=video.takeId();video.reset();video.beginStandardTrigger();assertFalse(video.owns(old));
    }
    @Test public void cancelledStandardPermissionCannotStartWithoutANewIntent() {
        VideoStateMachine video=new VideoStateMachine();video.beginStandardTrigger();video.markPermissionPending();video.cancelPermissionPending();
        assertFalse(video.beginPreparing());assertTrue(video.beginStandardTrigger());assertTrue(video.beginPreparing());
    }
    @Test public void pinchTakesGestureOwnershipAndOldReleaseDoesNotCancelIt() {
        InteractionState.Gesture gesture=new InteractionState.Gesture();gesture.begin(InteractionState.Gesture.Mode.EV);
        assertFalse(gesture.begin(InteractionState.Gesture.Mode.ZOOM_ROW));assertTrue(gesture.begin(InteractionState.Gesture.Mode.PINCH));
        gesture.end(InteractionState.Gesture.Mode.EV);assertTrue(gesture.is(InteractionState.Gesture.Mode.PINCH));
        gesture.cancel();assertTrue(gesture.is(InteractionState.Gesture.Mode.IDLE));
    }
    @Test public void cancelledUnlockHoldMustStartAFullNewHold() {
        InteractionState.Lock lock=new InteractionState.Lock();lock.set(true);lock.hold(1000);lock.cancelHold();
        assertTrue(lock.locked());assertFalse(lock.holding());lock.hold(5000);assertEquals(3000,lock.remaining(5000));
        assertEquals(0,lock.remaining(8001));lock.set(false);assertFalse(lock.locked());assertFalse(lock.holding());
    }
    @Test public void stationaryDetectionRequiresSettlingAndResetsOnMovement() {
        InteractionState.Motion motion=new InteractionState.Motion();motion.sample(0f,0);assertFalse(motion.stationary());
        motion.sample(0f,1201);assertTrue(motion.stationary());motion.sample(.2f,1300);assertFalse(motion.stationary());
        motion.sample(0f,1400);motion.reset();assertFalse(motion.stationary());
    }
    @Test public void detectorCooldownSurvivesTrackingResetAndExpiresMonotonically() {
        InteractionState.Detection signal=new InteractionState.Detection(2,8000);
        assertFalse(signal.hit(0));assertTrue(signal.hit(10));signal.resetTracking();
        assertFalse(signal.hit(100));assertTrue(signal.cooling(8009));assertFalse(signal.cooling(8010));
        assertFalse(signal.hit(8011));assertTrue(signal.hit(8012));
    }
    @Test public void gifExportFreezesPhotoSelection() {
        GifExporter.Params params=new GifExporter.Params();params.photoUris=new ArrayList<>();params.photoUris.add(null);
        GifExporter.Params snapshot=params.snapshot();params.photoUris.clear();assertEquals(1,snapshot.photoUris.size());
    }
    @Test public void updateDownloadIsSingleFlightAndClearingInvalidatesItsCallbacks() {
        AppUpdateManager.UpdateSession updates=new AppUpdateManager.UpdateSession();
        AppUpdateManager.UpdateInfo release=new AppUpdateManager.UpdateInfo("v1","Title","",true,"https://example.com/app.apk","app.apk",100,"");
        long ticket=updates.beginDownload(release);assertNotEquals(0,ticket);assertEquals(0,updates.beginDownload(release));
        assertFalse(updates.select(release));updates.clear();assertFalse(updates.owns(ticket));
        assertEquals(AppUpdateManager.UpdateState.AVAILABLE,updates.state);
    }
    @Test public void readerWaitsForBothRenderingAndDngLeasesBeforeClosing() {
        java.util.concurrent.atomic.AtomicInteger disposed=new java.util.concurrent.atomic.AtomicInteger();
        ResourceLease reader=new ResourceLease(disposed::incrementAndGet);
        assertTrue(reader.acquire());assertTrue(reader.acquire());reader.close();
        assertEquals(ResourceLease.Phase.CLOSING,reader.phase());assertFalse(reader.acquire());
        reader.release();assertEquals(0,disposed.get());reader.release();assertEquals(1,disposed.get());
        reader.close();assertEquals(1,disposed.get());assertEquals(ResourceLease.Phase.CLOSED,reader.phase());
    }
    @Test public void permissionResolutionIsSeparateFromPermissionPromptAndCanBeCancelled() {
        VideoStateMachine video=new VideoStateMachine();video.beginStandardTrigger();video.markPermissionPending();
        assertFalse(video.beginPreparing());assertTrue(video.markPermissionReady());
        assertFalse(video.isPermissionPending());assertTrue(video.isPermissionReady());
        video.cancelPermissionPending();assertFalse(video.beginPreparing());
    }

}
