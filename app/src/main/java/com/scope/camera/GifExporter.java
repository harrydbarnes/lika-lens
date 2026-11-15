package com.scope.camera;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/**
 * Handles encoding, color grading, boomerang frame ordering, and file saving for GIF exports.
 */
public final class GifExporter {

    public static class Params {
        public boolean isVideoMode;
        public Uri videoUri;
        public List<Uri> photoUris;
        public int targetRes;
        public int targetFps;
        public boolean boomerang;
        public String lutProfile;
        public int videoStartSec;
        public int videoDurationSec;
        Params snapshot() {
            Params copy=new Params();copy.isVideoMode=isVideoMode;copy.videoUri=videoUri;
            copy.photoUris=photoUris==null?new ArrayList<>():new ArrayList<>(photoUris);
            copy.targetRes=targetRes;copy.targetFps=targetFps;copy.boomerang=boomerang;
            copy.lutProfile=lutProfile;copy.videoStartSec=videoStartSec;copy.videoDurationSec=videoDurationSec;
            return copy;
        }
    }

    private GifExporter() {}

    public static void startExport(Activity activity, Params params, ExecutorService worker, Handler mainHandler) {
        startExport(activity, params, worker, mainHandler, new JobState());
    }

    static GifStudioDialogs.ProgressHandle startExport(Activity activity, Params input, ExecutorService worker,
                                                       Handler mainHandler, JobState job) {
        if (input.isVideoMode && input.videoUri == null) {
            Toast.makeText(activity, "Please choose a video first", Toast.LENGTH_SHORT).show(); return null;
        }
        if (!input.isVideoMode && (input.photoUris==null || input.photoUris.size() < 2)) {
            Toast.makeText(activity, "Please select at least 2 photos for an animated GIF", Toast.LENGTH_SHORT).show(); return null;
        }
        long ticket = job.tryBegin(JobState.Phase.EXTRACTING);
        if (ticket == 0) return null;
        Params params = input.snapshot();
        android.content.Context context = activity.getApplicationContext();
        GifStudioDialogs.ProgressHandle progress = GifStudioDialogs.showExportProgressDialog(activity);
        worker.execute(() -> {
            List<Bitmap> frames = new ArrayList<>();
            try {
                requireCurrent(job, ticket);
                LutManager.Lut3D lut = !"NONE".equals(params.lutProfile)
                        ? LutManager.getBuiltIn(params.lutProfile, 17)
                        : null;
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                GifEncoder encoder = new GifEncoder();
                encoder.setDelay(1000 / Math.max(1, params.targetFps));
                encoder.setRepeat(0);
                encoder.setQuality(10);
                if (!encoder.start(bytes)) throw new IllegalStateException("Could not start GIF encoder");

                int encodedTotal;
                if (!params.isVideoMode && !params.boomerang) {
                    // Stream one photo at a time so only a single decoded/graded Bitmap is live in RAM.
                    int estTotal = params.photoUris.size();
                    encodedTotal = 0;
                    job.move(ticket, JobState.Phase.ENCODING);
                    for (int i = 0; i < params.photoUris.size(); i++) {
                        requireCurrent(job, ticket);
                        Bitmap frame = GifMediaHelper.decodeAndScaleBitmap(context, params.photoUris.get(i), params.targetRes);
                        if (frame == null) continue;
                        try {
                            if (lut != null) {
                                Bitmap graded = LutManager.applyToBitmap(frame, lut, 1f);
                                if (graded != null && graded != frame) {
                                    frame.recycle();
                                    frame = graded;
                                }
                            }
                            if (encodedTotal == 0) {
                                encoder.setSize(frame.getWidth(), frame.getHeight());
                            }
                            if (!encoder.addFrame(frame)) throw new IllegalStateException("Could not encode GIF frame");
                            encodedTotal++;
                            final int n = encodedTotal;
                            final int denom = Math.max(1, estTotal);
                            mainHandler.post(() -> {
                                if (current(activity, job, ticket)) {
                                    progress.update("Encoding frame " + n + " of " + denom + "…", Math.min(90, n * 90 / denom));
                                }
                            });
                        } finally {
                            if (!frame.isRecycled()) frame.recycle();
                        }
                    }
                    if (encodedTotal == 0) throw new IllegalStateException("No frames extracted for GIF");
                } else {
                    if (params.isVideoMode) {
                        frames.addAll(GifMediaHelper.extractVideoFrames(context, params.videoUri,
                                params.targetRes, params.targetFps, params.videoStartSec, params.videoDurationSec, null, () -> job.accepts(ticket)));
                    } else {
                        for (Uri uri : params.photoUris) {
                            requireCurrent(job, ticket);
                            Bitmap b = GifMediaHelper.decodeAndScaleBitmap(context, uri, params.targetRes);
                            if (b != null) frames.add(b);
                        }
                    }
                    requireCurrent(job, ticket);
                    if (frames.isEmpty()) throw new IllegalStateException("No frames extracted for GIF");
                    if (lut != null) {
                        job.move(ticket, JobState.Phase.GRADING);
                        for (int i = 0; i < frames.size(); i++) {
                            requireCurrent(job, ticket);
                            Bitmap original = frames.get(i);
                            Bitmap graded = LutManager.applyToBitmap(original, lut, 1f);
                            if (graded != null && graded != original) {
                                original.recycle();
                                frames.set(i, graded);
                            }
                        }
                    }
                    int baseCount = frames.size();
                    boolean doBounce = params.boomerang && baseCount > 2;
                    final int bounceTotal = doBounce ? baseCount * 2 - 2 : baseCount;
                    encodedTotal = bounceTotal;
                    job.move(ticket, JobState.Phase.ENCODING);
                    encoder.setSize(frames.get(0).getWidth(), frames.get(0).getHeight());
                    int encodedIdx = 0;
                    for (int i = 0; i < baseCount; i++) {
                        requireCurrent(job, ticket);
                        Bitmap frame = frames.get(i);
                        if (!encoder.addFrame(frame)) throw new IllegalStateException("Could not encode GIF frame");
                        // Recycle immediately when not needed for the boomerang return pass (ends: 0 and baseCount-1)
                        if (!doBounce || i == 0 || i == baseCount - 1) {
                            frame.recycle();
                            frames.set(i, null);
                        }
                        final int n = ++encodedIdx;
                        mainHandler.post(() -> {
                            if (current(activity, job, ticket)) {
                                progress.update("Encoding frame " + n + " of " + bounceTotal + "…", n * 90 / bounceTotal);
                            }
                        });
                    }
                    if (doBounce) {
                        for (int i = baseCount - 2; i > 0; i--) {
                            requireCurrent(job, ticket);
                            Bitmap frame = frames.get(i);
                            if (!encoder.addFrame(frame)) throw new IllegalStateException("Could not encode GIF frame");
                            frame.recycle();
                            frames.set(i, null);
                            final int n = ++encodedIdx;
                            mainHandler.post(() -> {
                                if (current(activity, job, ticket)) {
                                    progress.update("Encoding frame " + n + " of " + bounceTotal + "…", n * 90 / bounceTotal);
                                }
                            });
                        }
                    }
                }
                if (!encoder.finish()) throw new IllegalStateException("Could not finish GIF encoder");
                requireCurrent(job, ticket);
                job.move(ticket, JobState.Phase.SAVING);
                byte[] gifBytes = bytes.toByteArray();
                String name = "LIKA_GIF_" + new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date()) + ".gif";
                Uri saved = PhotoRepository.saveGif(context, gifBytes, name);
                if (saved == null) throw new IllegalStateException("Could not save GIF");
                final int totalFinal = encodedTotal;
                mainHandler.post(() -> {
                    if (!current(activity, job, ticket)) return;
                    job.move(ticket, JobState.Phase.READY);
                    progress.dismiss();
                    GifStudioDialogs.showExportSuccessDialog(activity, saved, name, gifBytes.length, totalFinal, params.targetFps,
                            () -> {
                                Intent intent = new Intent(activity, PhotoViewerActivity.class);
                                intent.putExtra(PhotoViewerActivity.EXTRA_URI, saved.toString());
                                intent.putExtra(PhotoViewerActivity.EXTRA_NAME, name);
                                intent.putExtra(PhotoViewerActivity.EXTRA_FROM_LIBRARY, true);
                                activity.startActivity(intent);
                                activity.finish();
                            }, activity::finish);
                });
            } catch (java.util.concurrent.CancellationException ignored) {
                mainHandler.post(progress::dismiss);
            } catch (Exception error) {
                mainHandler.post(() -> {
                    if (current(activity, job, ticket)) {
                        job.move(ticket, JobState.Phase.FAILED);
                        progress.dismiss();
                        Toast.makeText(activity, "Export failed: " + error.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            } finally {
                for (Bitmap b : frames) if (b != null && !b.isRecycled()) b.recycle();
            }
        });
        return progress;
    }

    private static void requireCurrent(JobState job,long ticket) {
        if(!job.accepts(ticket)) throw new java.util.concurrent.CancellationException();
    }
    private static boolean current(Activity activity,JobState job,long ticket) {
        return job.accepts(ticket) && !activity.isFinishing() && !activity.isDestroyed();
    }
}
