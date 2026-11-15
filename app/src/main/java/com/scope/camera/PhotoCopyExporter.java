package com.scope.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Creates an independently editable render without changing the source photo or its recipe. */
final class PhotoCopyExporter {
    private static final String[] GPS_TAGS = {
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_PROCESSING_METHOD
    };

    private PhotoCopyExporter() { }

    static PhotoRepository.Photo export(Context context, String sourceKey, RawArchive.Frame frame,
                                        RawDeveloper.Look look) throws Exception {
        return export(context, sourceKey, null, frame, look);
    }

    static PhotoRepository.Photo export(Context context, String sourceKey, Uri sourceUri,
                                        RawArchive.Frame frame, RawDeveloper.Look look) throws Exception {
        String prefix = CaptureFileNaming.resolvePrefix(
                AppSettings.get(context, AppSettings.FILE_PREFIX, "LIKA"),
                AppSettings.get(context, AppSettings.CUSTOM_PREFIX_VALUE, "LIKA"));
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date());
        String folderOrg = AppSettings.get(context, AppSettings.FOLDER_ORG, "Single folder (Pictures/Lika Lens)");
        String month = new SimpleDateFormat("yyyy-MM", Locale.US).format(new Date());
        String subfolder = CaptureFileNaming.buildFolderSubpath(folderOrg, look == null ? null : look.brain, month);
        String key = CaptureFileNaming.ensureUniqueStem(
                CaptureFileNaming.buildCopyBaseStem(prefix, stamp),
                candidate -> CaptureFileNaming.stillStemExists(context, candidate, subfolder));

        File archive = RawArchive.file(context, key);
        Bitmap bitmap = null;
        Uri output = null;
        boolean copiedArchive = false;
        RawArchive.pin(sourceKey);
        RawArchive.pin(key);
        try {
            bitmap = RawDeveloper.develop(frame, look, 0);
            File sourceFile = RawArchive.file(context, sourceKey);
            if (sourceFile.isFile() && RawArchive.hasSufficientSpaceForArchive(context)) {
                File parent = archive.getParentFile();
                if (parent != null && (parent.isDirectory() || parent.mkdirs())) {
                    Files.copy(sourceFile.toPath(), archive.toPath());
                    copiedArchive = true;
                }
            }
            output = PhotoRepository.saveBitmap(context, bitmap, key + ".jpg", subfolder);
            if (output == null) throw new IllegalStateException("Could not save the photo copy");

            ExifInterface sourceExif = readExif(context, sourceUri);
            boolean writeExifParams = AppSettings.getBoolean(context, AppSettings.EXIF_PARAMS, true);
            boolean writeLocation = AppSettings.getBoolean(context, AppSettings.LOCATION, true);

            try (ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(output, "rw")) {
                if (pfd != null) {
                    ExifInterface exif = new ExifInterface(pfd.getFileDescriptor());
                    exif.setAttribute(ExifInterface.TAG_MAKE, Build.MANUFACTURER);
                    exif.setAttribute(ExifInterface.TAG_MODEL, Build.MODEL);
                    exif.setAttribute(ExifInterface.TAG_SOFTWARE, "Lika Lens");
                    exif.setAttribute(ExifInterface.TAG_ORIENTATION, String.valueOf(ExifInterface.ORIENTATION_NORMAL));
                    exif.setAttribute(ExifInterface.TAG_DATETIME,
                            new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(new Date()));

                    if (writeExifParams) {
                        if (frame != null && frame.exposureTimeNs > 0) {
                            exif.setAttribute(ExifInterface.TAG_EXPOSURE_TIME,
                                    String.valueOf(frame.exposureTimeNs / 1_000_000_000.0));
                        } else if (sourceExif != null) {
                            copyAttributeIfPresent(sourceExif, exif, ExifInterface.TAG_EXPOSURE_TIME);
                        }
                        if (frame != null && frame.sensitivityIso > 0) {
                            exif.setAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS,
                                    String.valueOf(frame.sensitivityIso));
                        } else if (sourceExif != null) {
                            copyAttributeIfPresent(sourceExif, exif, ExifInterface.TAG_ISO_SPEED_RATINGS);
                        }
                        if (sourceExif != null) {
                            copyAttributeIfPresent(sourceExif, exif, ExifInterface.TAG_FOCAL_LENGTH);
                            copyAttributeIfPresent(sourceExif, exif, ExifInterface.TAG_F_NUMBER);
                        }
                    }

                    if (writeLocation) {
                        if (sourceExif != null) {
                            for (String tag : GPS_TAGS) {
                                copyAttributeIfPresent(sourceExif, exif, tag);
                            }
                        }
                    } else {
                        for (String tag : GPS_TAGS) {
                            exif.setAttribute(tag, null);
                        }
                    }
                    exif.saveAttributes();
                }
            } catch (Exception e) {
                android.util.Log.w("PhotoCopyExporter", "Copy saved without additional EXIF", e);
            }
            if (copiedArchive) {
                PhotoEditStore.saveEdited(context, key, look);
                String limitChoice = AppSettings.get(context, AppSettings.EDITABLE_ARCHIVE_LIMIT, AppSettings.EDITABLE_ARCHIVE_512MB);
                RawArchive.enforceStorageLimit(context, limitChoice, key);
            }
            return new PhotoRepository.Photo(output, key + ".jpg", System.currentTimeMillis());
        } catch (Exception e) {
            if (output != null) PhotoRepository.delete(context, output, key + ".jpg", key);
            if (archive.isFile() && !archive.delete()) android.util.Log.w("PhotoCopyExporter", "Could not remove incomplete archive");
            throw e;
        } finally {
            RawArchive.unpin(key);
            RawArchive.unpin(sourceKey);
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        }
    }

    private static ExifInterface readExif(Context context, Uri uri) {
        if (context == null || uri == null) return null;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in != null) return new ExifInterface(in);
        } catch (Exception ignored) { }
        return null;
    }

    private static void copyAttributeIfPresent(ExifInterface src, ExifInterface dst, String tag) {
        String val = src.getAttribute(tag);
        if (val != null && !val.isEmpty()) {
            dst.setAttribute(tag, val);
        }
    }
}
