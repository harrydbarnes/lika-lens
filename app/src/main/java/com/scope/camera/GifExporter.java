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
    }

    private GifExporter() {}

    public static void startExport(
            Activity activity,
            Params params,
            ExecutorService worker,
            Handler mainHandler
    ) {
        if (params.isVideoMode && params.videoUri == null) {
            Toast.makeText(activity, "Please choose a video first", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!params.isVideoMode && params.photoUris.size() < 2) {
            Toast.makeText(activity, "Please select at least 2 photos for an animated GIF", Toast.LENGTH_SHORT).show();
            return;
        }

        GifStudioDialogs.ProgressHandle progressHandle = GifStudioDialogs.showExportProgressDialog(activity);

        worker.execute(() -> {
            try {
                List<Bitmap> exportFrames = new ArrayList<>();
                mainHandler.post(() -> progressHandle.statusView.setText("Extracting and scaling frames…"));

                if (params.isVideoMode) {
                    exportFrames.addAll(GifMediaHelper.extractVideoFrames(
                            activity,
                            params.videoUri,
                            params.targetRes,
                            params.targetFps,
                            params.videoStartSec,
                            params.videoDurationSec,
                            null
                    ));
                } else {
                    for (Uri uri : params.photoUris) {
                        Bitmap bmp = GifMediaHelper.decodeAndScaleBitmap(activity, uri, params.targetRes);
                        if (bmp != null) exportFrames.add(bmp);
                    }
                }

                if (exportFrames.isEmpty()) {
                    throw new IllegalStateException("No frames extracted for GIF");
                }

                // Apply selected Film LUT
                if (!"NONE".equals(params.lutProfile)) {
                    mainHandler.post(() -> progressHandle.statusView.setText("Applying " + params.lutProfile + " film color…"));
                    LutManager.Lut3D lut = LutManager.generateBuiltIn(params.lutProfile, 17);
                    for (int i = 0; i < exportFrames.size(); i++) {
                        Bitmap orig = exportFrames.get(i);
                        Bitmap graded = LutManager.applyToBitmap(orig, lut, 1.0f);
                        if (graded != null && graded != orig) {
                            orig.recycle();
                            exportFrames.set(i, graded);
                        }
                    }
                }

                // If boomerang mode, append reverse sequence
                if (params.boomerang && exportFrames.size() > 2) {
                    int originalCount = exportFrames.size();
                    for (int i = originalCount - 2; i > 0; i--) {
                        Bitmap copy = exportFrames.get(i).copy(Bitmap.Config.ARGB_8888, true);
                        exportFrames.add(copy);
                    }
                }

                int totalFrames = exportFrames.size();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                GifEncoder encoder = new GifEncoder();
                encoder.setDelay(1000 / params.targetFps);
                encoder.setRepeat(0); // Infinite loop
                encoder.setQuality(10); // High quality photographic sampling
                encoder.setSize(exportFrames.get(0).getWidth(), exportFrames.get(0).getHeight());

                encoder.start(baos);
                for (int i = 0; i < exportFrames.size(); i++) {
                    final int frameIdx = i + 1;
                    mainHandler.post(() -> {
                        progressHandle.update(
                                String.format(Locale.US, "Encoding frame %d of %d…", frameIdx, totalFrames),
                                (frameIdx * 90) / totalFrames
                        );
                    });
                    encoder.addFrame(exportFrames.get(i));
                    exportFrames.get(i).recycle(); // Free memory immediately
                }
                encoder.finish();

                byte[] gifBytes = baos.toByteArray();
                String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                String gifName = "LIKA_GIF_" + stamp + ".gif";

                mainHandler.post(() -> progressHandle.statusView.setText("Saving GIF to library…"));
                Uri savedUri = PhotoRepository.saveGif(activity, gifBytes, gifName);

                mainHandler.post(() -> {
                    progressHandle.dismiss();
                    if (savedUri != null) {
                        GifStudioDialogs.showExportSuccessDialog(
                                activity,
                                savedUri,
                                gifName,
                                gifBytes.length,
                                totalFrames,
                                params.targetFps,
                                () -> {
                                    Intent intent = new Intent(activity, PhotoViewerActivity.class);
                                    intent.putExtra(PhotoViewerActivity.EXTRA_URI, savedUri.toString());
                                    intent.putExtra(PhotoViewerActivity.EXTRA_NAME, gifName);
                                    intent.putExtra(PhotoViewerActivity.EXTRA_FROM_LIBRARY, true);
                                    activity.startActivity(intent);
                                    activity.finish();
                                },
                                activity::finish
                        );
                    } else {
                        Toast.makeText(activity, "Failed to save GIF", Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Throwable t) {
                mainHandler.post(() -> {
                    progressHandle.dismiss();
                    Toast.makeText(activity, "Export failed: " + t.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }
}
