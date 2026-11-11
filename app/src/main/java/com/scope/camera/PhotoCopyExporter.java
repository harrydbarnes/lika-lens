package com.scope.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.nio.file.Files;
import java.util.UUID;

/** Creates an independently editable render without changing the source photo or its recipe. */
final class PhotoCopyExporter {
    private PhotoCopyExporter() { }

    static PhotoRepository.Photo export(Context context, String sourceKey, RawArchive.Frame frame,
                      RawDeveloper.Look look) throws Exception {
        String key = "LIKA_EDIT_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
        File archive = RawArchive.file(context, key);
        Bitmap bitmap = null;
        Uri output = null;
        try {
            bitmap = RawDeveloper.develop(frame, look, 0);
            File parent = archive.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                throw new IllegalStateException("Could not preserve the editable original");
            }
            Files.copy(RawArchive.file(context, sourceKey).toPath(), archive.toPath());
            output = PhotoRepository.saveBitmap(context, bitmap, key + ".jpg");
            if (output == null) throw new IllegalStateException("Could not save the photo copy");
            try (ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(output, "rw")) {
                if (pfd != null) {
                    android.media.ExifInterface exif = new android.media.ExifInterface(pfd.getFileDescriptor());
                    exif.setAttribute(android.media.ExifInterface.TAG_MAKE, android.os.Build.MANUFACTURER);
                    exif.setAttribute(android.media.ExifInterface.TAG_MODEL, android.os.Build.MODEL);
                    exif.setAttribute(android.media.ExifInterface.TAG_SOFTWARE, "Lika Lens");
                    if (frame.exposureTimeNs > 0) exif.setAttribute(android.media.ExifInterface.TAG_EXPOSURE_TIME,
                            String.valueOf(frame.exposureTimeNs / 1_000_000_000.0));
                    if (frame.sensitivityIso > 0) exif.setAttribute(android.media.ExifInterface.TAG_ISO_SPEED_RATINGS,
                            String.valueOf(frame.sensitivityIso));
                    exif.saveAttributes();
                }
            } catch (Exception e) {
                android.util.Log.w("PhotoCopyExporter", "Copy saved without additional EXIF", e);
            }
            PhotoEditStore.saveEdited(context, key, look);
            return new PhotoRepository.Photo(output, key + ".jpg", System.currentTimeMillis());
        } catch (Exception e) {
            if (output != null) PhotoRepository.delete(context, output, key + ".jpg", key);
            if (archive.isFile() && !archive.delete()) android.util.Log.w("PhotoCopyExporter", "Could not remove incomplete archive");
            throw e;
        } finally {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        }
    }
}
