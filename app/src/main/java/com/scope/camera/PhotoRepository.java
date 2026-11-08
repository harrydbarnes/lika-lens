package com.scope.camera;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Reads the app's rendered stills from Photos, with an app-folder fallback on older Android versions. */
final class PhotoRepository {
    static final class Photo {
        final Uri uri;
        final String name;
        final long timestampMillis;

        Photo(Uri uri, String name, long timestampMillis) {
            this.uri = uri;
            this.name = name;
            this.timestampMillis = timestampMillis;
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
        List<Photo> photos = all(context);
        return photos.isEmpty() ? null : photos.get(0);
    }

    static String archiveKey(String displayName) {
        if (displayName == null) return "";
        return displayName.toLowerCase(java.util.Locale.US).endsWith(".jpg")
                ? displayName.substring(0, displayName.length() - 4) : displayName;
    }

    private static List<Photo> queryMediaStore(Context context) {
        List<Photo> photos = new ArrayList<>();
        String[] columns = {MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED, MediaStore.Images.Media.DATE_MODIFIED};
        try (Cursor cursor = context.getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, columns,
                "(" + MediaStore.Images.Media.RELATIVE_PATH + " LIKE ? OR "
                        + MediaStore.Images.Media.RELATIVE_PATH + " LIKE ?) AND "
                        + MediaStore.Images.Media.MIME_TYPE + "=?",
                new String[]{Environment.DIRECTORY_PICTURES + "/Lika Lens/%",
                        Environment.DIRECTORY_PICTURES + "/SCOPE/%", "image/jpeg"},
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
        } catch (RuntimeException ignored) { }
        return photos;
    }

    private static List<Photo> queryAppFolder(Context context) {
        List<Photo> photos = new ArrayList<>();
        File pictures = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        for (String folderName : new String[]{"Lika Lens", "SCOPE"}) {
            File folder = new File(pictures, folderName);
            File[] files = folder.listFiles((dir, name) -> name.toLowerCase(java.util.Locale.US).endsWith(".jpg"));
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
            } catch (Exception ignored) { }
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
                } catch (Exception ignored) { }
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
