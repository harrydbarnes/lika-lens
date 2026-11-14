# Workflow ownership and state transitions

This change builds on the capture/preview branch. Existing CameraLifecycleState,
StillCaptureState, VideoStateMachine, comparison/gesture states and encoder states remain
in use. Orthogonal jobs own their own state, so rendering a previous photo does not block
the camera from taking another photo.

## Changes by workflow

| Workflow | Ownership and transitions | Visible behaviour |
| --- | --- | --- |
| Camera open/session creation | RequestEpoch invalidates callbacks from replaced requests; callbacks verify the owning device, session and activity | Old callbacks cannot reopen the camera or replace a new lens session after closing/switching |
| Photo development | CaptureJobs records DEVELOPING, ARCHIVING, PUBLISHING, SAVED/FAILED by capture ID | The existing five-frame development limit remains independent of shutter state |
| Reader resources | ResourceLease moves OPEN → CLOSING → CLOSED and waits for acquired frames | Closing/switching the camera does not invalidate an image still being developed or written as DNG |
| Countdown, torch and screen flash | Delayed capture epoch invalidates obsolete callbacks; cancellation restores flash brightness | Cancelling/leaving does not fire an old capture or let old pulses interfere with a newer countdown |
| Photo/video mode and QuickTake permissions | A take ID owns trigger/permission/preparation; PERMISSION_PENDING and PERMISSION_READY are distinct | Releasing QuickTake, changing mode or stopping the activity cancels the appropriate pending intent; a permission prompt alone cannot start the recorder |
| Paused camera switch | VideoStateMachine retains the target state across SWITCHING_CAMERA | A paused recording stays paused after switching cameras |
| Video finalisation | A separate VideoSaveJob owns captured segments through stitching and publication | Recording controls can return to idle while an independent save is in progress |
| Video recovery | Only SAVED jobs can delete source segments; recordings use the private video-recovery directory rather than cache | Failed stitching never silently saves just the first segment. Retry is offered, and preserved segments can be saved individually or discarded after restarting without interrupting active permissions |
| Recording control lock | UNLOCKED, LOCKED and HOLDING states own the unlock countdown | Cancelling a hold resets the required three-second hold |
| Focus/AE | CONTINUOUS, TARGETED and LOCKED AF state is separate from the AE lock | Hiding the reticle does not release locked focus; releasing AE/AF lock restores continuous AF |
| Viewfinder input | One gesture owner for pinch, zoom row, zoom slider, EV and shadows | Pinch can take over; the previous gesture's release cannot cancel the new owner |
| Stability/detection | Motion uses MOVING/SETTLING/STILL; palm and dirty-lens detection use IDLE/TRACKING/COOLDOWN | Pause resets tracking and motion while preserving detector cooldowns |
| Editor jobs | Source, preview and save each have a JobState and generation; saving freezes its recipe and disables edit inputs | Obsolete previews cannot replace newer results or overwrite saving status; errors have retryable terminal states |
| Editor slider history | IDLE/SCRUBBING edit transaction selects preview quality and undo boundaries | A drag remains one edit with a final-quality render on release |
| GIF source/picker/playback | PHOTOS/VIDEO selection commits on picker success; picker, screen visibility and playback preference are separate states | Cancelling Video selection preserves the previous source; loading frames and resuming preserve a manually paused preview |
| GIF loading/export | Frozen requests, generations, single-flight export, explicit extracting/grading/encoding/saving states, cooperative cancellation | Rapid adjustments cannot display old frames, duplicate exports are blocked, closed screens cannot restart playback; obsolete frame extraction stops between frames |
| Gallery work | BROWSING/SELECTING is separate from the export job; copy recipes and selected photo lists are frozen | Batch-copy and proof-sheet callbacks are ignored after closure; selection changes do not change an in-flight export |
| Settings preview | JobState owns a coalesced worker; ORIGINAL/GRADED comparison is independent of badge visibility | Work uses a private bitmap copy, rejects obsolete output and releases superseded graded images |
| Viewer loading/playback | Generation-owned background image decoding; comparison callbacks also reject destroyed activities | Photo decoding leaves the UI thread, old results are discarded, and animated GIFs pause with the activity |
| Updates | One UpdateSession owns release/APK/state and a download ticket; checks have their own JobState | Duplicate downloads are rejected, obsolete checks are ignored, permission/installer return and deferred installation are handled by the session |
| Media output | Shared MediaOutput allocates a pending output, writes it, closes the stream, then publishes; incomplete outputs roll back | Photo, DNG, GIF, copy, proof-sheet and video saves use the same publication contract; private files remain .part files until complete |
| Live look preview | DISABLED/BUILDING/ACTIVE/FALLBACK/DISPOSED plus an independent GPU capability | Existing generation protection remains; an unsupported GPU stays on the fallback |

## Job and publication contracts

- A new request invalidates previous callbacks. Cancellation and disposal invalidate the current
  generation. READY and FAILED are terminal for that generation; retry creates a new one.
- UI callbacks must validate their job and screen before displaying a result. Rejected bitmaps
  are recycled. A closed screen cannot be revived by completing background work.
- Photo-copy and video publication may finish after their activity closes while the process
  remains alive. GIF extraction/encoding cancels cooperatively when the studio is destroyed.
- MediaOutput may publish only after a successful stream close. A stream-open, encode, write,
  close or publication failure leaves the transaction unpublished and eligible for rollback.
- Video source segments are deleted only after the entire output is published. If the process
  dies, the recovery folder remains. Restart recovery saves each segment individually so that
  recordings from unrelated takes are never accidentally joined together.
- These are process-local jobs, not a persistent background scheduler. An Android process kill
  can interrupt image/GIF computation. The recoverable video files and fully published media
  are durable; partial image work is not promised to resume automatically.

## Regression coverage

WorkflowStateTest exercises stale callbacks, cancellation/disposal, single-flight exports,
terminal job states, take invalidation and permission resolution, paused camera switches,
segment retention, capture outcomes, output publication guards, reader leases, gesture
ownership, unlock cancellation, detector cooldowns, motion settling, frozen GIF selections
and update download ownership. Existing image/colour and state-machine tests also run.

## Device verification before release

1. Stop a multi-camera recording. Exercise stitch/publication failure and confirm all source
   segments remain, Retry works, and restart recovery saves the preserved segments.
2. Start QuickTake without microphone permission, release it, then grant permission. It must
   not start recording. Also test standard video permission grant, denial, Home and mode changes.
3. Pause a video, switch the front/rear camera and lenses, and confirm recording stays paused.
4. Rapidly switch lenses, background/resume during opening, and take a RAW/DNG photo immediately
   before leaving the camera. Check for stale sessions, reader-close errors and corrupt outputs.
5. Cancel/restart timers and interrupt screen flash. Confirm no delayed capture, stale torch
   pulse or persistent display-brightness override. Verify AE/AF remains locked after 3.5s.
6. Scrub editor/GIF/settings controls rapidly, cancel pickers, close/reopen screens, and rotate.
   Check preview identity, pause preference, input disabling during save, and undo boundaries.
7. Interrupt saves and downloads. Verify incomplete media stays unpublished, retry labels are
   correct, update dialogs detach on pause, and completed APKs can be installed on return.

No physical camera/GPU/permission-dialog testing was available in the development environment.
