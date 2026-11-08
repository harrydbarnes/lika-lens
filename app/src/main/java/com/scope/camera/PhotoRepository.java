package com.scope.camera;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Reads the app's rendered stills from Photos, with an app-folder fallback on older Android versions. */
final class PhotoRepository {
    private static final String TAG = "PhotoRepository";
    static final class Photo {
        final Uri uri;
        final String name;
        final long timestampMillis;
        final boolean isGif;

        Photo(Uri uri, String name, long timestampMillis) {
            this.uri = uri;
            this.name = name;
            this.timestampMillis = timestampMillis;
            this.isGif = name != null && name.toLowerCase(java.util.Locale.US).endsWith(".gif");
        }
    }

    private PhotoRepository() { }

    static List<Photo> all(Context context) {
        List<Photo> photos = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) photos.addAll(queryMediaStore(context));
        photos.addAll(queryAppFolder(context));
        Collections.sort(photos, (a, b) -> Long.compare(b.timestampMillis, a.timestampMillis));
        return photos;
    }

    static Photo latest(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            String[] columns = {MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.DATE_ADDED, MediaStore.Images.Media.DATE_MODIFIED};
            try (Cursor cursor = context.getContentResolver().query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, columns,
                    "(" + MediaStore.Images.Media.RELATIVE_PATH + " LIKE ? OR "
                            + MediaStore.Images.Media.RELATIVE_PATH + " LIKE ?) AND ("
                            + MediaStore.Images.Media.MIME_TYPE + "=? OR "
                            + MediaStore.Images.Media.MIME_TYPE + "=?)",
                    new String[]{Environment.DIRECTORY_PICTURES + "/Lika Lens/%",
                            Environment.DIRECTORY_PICTURES + "/SCOPE/%", "image/jpeg", "image/gif"},
                    MediaStore.Images.Media.DATE_ADDED + " DESC LIMIT 1")) {
                if (cursor != null && cursor.moveToFirst()) {
                    long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID));
                    String name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME));
                    long added = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED));
                    long modified = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED));
                    long timestamp = (added > 0 ? added : modified) * 1000L;
                    return new Photo(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id), name, timestamp);
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "Failed to query latest photo from MediaStore", e);
            }
        }
        List<Photo> photos = queryAppFolder(context);
        if (!photos.isEmpty()) {
            Collections.sort(photos, (a, b) -> Long.compare(b.timestampMillis, a.timestampMillis));
            return photos.get(0);
        }
        return null;
    }

    static String archiveKey(String displayName) {
        if (displayName == null) return "";
        return displayName.toLowerCase(java.util.Locale.US).endsWith(".jpg")
                ? displayName.substring(0, displayName.length() - 4) : displayName;
    }

    static Uri saveBitmap(Context context, Bitmap bmp, String name) {
        if (bmp == null) return null;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Lika Lens");
                values.put(MediaStore.MediaColumns.IS_PENDING, 1);
                Uri uri = context.getContentResolver().insert(
                        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values);
                if (uri != null) {
                    try (OutputStream out = context.getContentResolver().openOutputStream(uri)) {
                        if (out != null) bmp.compress(Bitmap.CompressFormat.JPEG, 96, out);
                    }
                    values.clear();
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0);
                    context.getContentResolver().update(uri, values, null, null);
                    return uri;
                }
            }
            File parent = new File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Lika Lens");
            if (!parent.exists()) parent.mkdirs();
            File file = new File(parent, name);
            try (OutputStream out = new FileOutputStream(file)) {
                bmp.compress(Bitmap.CompressFormat.JPEG, 96, out);
            }
            return Uri.fromFile(file);
        } catch (Exception e) {
            return null;
        }
    }

    static Uri saveGif(Context context, byte[] gifBytes, String name) {
        if (gifBytes == null || gifBytes.length == 0) return null;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "image/gif");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Lika Lens");
                values.put(MediaStore.MediaColumns.IS_PENDING, 1);
                Uri uri = context.getContentResolver().insert(
                        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values);
                if (uri != null) {
                    try (OutputStream out = context.getContentResolver().openOutputStream(uri)) {
                        if (out != null) out.write(gifBytes);
                    }
                    values.clear();
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0);
                    context.getContentResolver().update(uri, values, null, null);
                    return uri;
                }
            }
            File parent = new File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Lika Lens");
            if (!parent.exists()) parent.mkdirs();
            File file = new File(parent, name);
            try (OutputStream out = new FileOutputStream(file)) {
                out.write(gifBytes);
            }
            return Uri.fromFile(file);
        } catch (Exception e) {
            return null;
        }
    }

    private static List<Photo> queryMediaStore(Context context) {
        List<Photo> photos = new ArrayList<>();
        String[] columns = {MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED, MediaStore.Images.Media.DATE_MODIFIED};
        try (Cursor cursor = context.getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, columns,
                "(" + MediaStore.Images.Media.RELATIVE_PATH + " LIKE ? OR "
                        + MediaStore.Images.Media.RELATIVE_PATH + " LIKE ?) AND ("
                        + MediaStore.Images.Media.MIME_TYPE + "=? OR "
                        + MediaStore.Images.Media.MIME_TYPE + "=?)",
                new String[]{Environment.DIRECTORY_PICTURES + "/Lika Lens/%",
                        Environment.DIRECTORY_PICTURES + "/SCOPE/%", "image/jpeg", "image/gif"},
                MediaStore.Images.Media.DATE_ADDED + " DESC")) {
            if (cursor == null) return photos;
            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
            int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);
            int addedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED);
            int modifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED);
            while (cursor.moveToNext()) {
                long id = cursor.getLong(idColumn);
                String name = cursor.getString(nameColumn);
                long added = cursor.getLong(addedColumn);
                long modified = cursor.getLong(modifiedColumn);
                long timestamp = (added > 0 ? added : modified) * 1000L;
                photos.add(new Photo(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id), name, timestamp));
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Failed to query MediaStore", e);
        }
        return photos;
    }

    private static List<Photo> queryAppFolder(Context context) {
        List<Photo> photos = new ArrayList<>();
        File pictures = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        for (String folderName : new String[]{"Lika Lens", "SCOPE"}) {
            File folder = new File(pictures, folderName);
            File[] files = folder.listFiles((dir, name) -> {
                String lower = name.toLowerCase(java.util.Locale.US);
                return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif");
            });
            if (files == null) continue;
            for (File file : files) photos.add(new Photo(Uri.fromFile(file), file.getName(), file.lastModified()));
        }
        return photos;
    }

    static boolean delete(Context context, Uri uri, String displayName, String archiveKey) {
        boolean deleted = false;
        if (uri != null) {
            try {
                if ("file".equalsIgnoreCase(uri.getScheme()) && uri.getPath() != null) {
                    File f = new File(uri.getPath());
                    deleted = f.delete();
                } else {
                    deleted = context.getContentResolver().delete(uri, null, null) > 0;
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to delete uri: " + uri, e);
            }
        }
        String stem = archiveKey != null && !archiveKey.isEmpty() ? archiveKey : archiveKey(displayName);
        if (stem != null && !stem.isEmpty()) {
            RawArchive.delete(context, stem);
            PhotoEditStore.reset(context, stem);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    Uri filesUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                    context.getContentResolver().delete(filesUri,
                            MediaStore.MediaColumns.DISPLAY_NAME + "=?",
                            new String[]{stem + ".dng"});
                } catch (Exception e) {
                    Log.w(TAG, "Failed to delete raw DNG from MediaStore: " + stem, e);
                }
            }
            File pictures = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            if (pictures != null) {
                File rawDng = new File(new File(new File(pictures, "Lika Lens"), "RAW"), stem + ".dng");
                if (rawDng.exists()) rawDng.delete();
            }
        }
        return deleted;
    }
}
