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
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Reads the app's rendered stills, GIFs, and videos from Photos/Movies, with legacy folder fallbacks. */
final class PhotoRepository {
    private static final String TAG = "PhotoRepository";
    static final class Photo {
        final Uri uri;
        final String name;
        final long timestampMillis;
        final boolean isGif;
        final boolean isVideo;
        final long durationMs;

        Photo(Uri uri, String name, long timestampMillis) {
            this(uri, name, timestampMillis, false, 0L);
        }

        Photo(Uri uri, String name, long timestampMillis, boolean isVideo, long durationMs) {
            this.uri = uri;
            this.name = name;
            this.timestampMillis = timestampMillis;
            String lower = name == null ? "" : name.toLowerCase(java.util.Locale.US);
            this.isVideo = isVideo || lower.endsWith(".mp4");
            this.isGif = !this.isVideo && lower.endsWith(".gif");
            this.durationMs = Math.max(0L, durationMs);
        }
    }

    private PhotoRepository() { }

    static List<Photo> all(Context context) {
        List<Photo> photos = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            photos.addAll(queryMediaStore(context));
            photos.addAll(queryMediaStoreVideos(context));
        } else {
            photos.addAll(queryLegacyVideoFolder());
        }
        photos.addAll(queryAppFolder(context));
        Collections.sort(photos, (a, b) -> Long.compare(b.timestampMillis, a.timestampMillis));
        return photos;
    }

    static final String MEDIASTORE_SORT_ORDER =
            MediaStore.Images.Media.DATE_ADDED + " DESC, " + MediaStore.Images.Media._ID + " DESC";
    static final String MEDIASTORE_VIDEO_SORT_ORDER =
            MediaStore.Video.Media.DATE_ADDED + " DESC, " + MediaStore.Video.Media._ID + " DESC";

    static Photo latest(Context context) {
        Photo newest = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            String[] columns = {MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.DATE_ADDED, MediaStore.Images.Media.DATE_MODIFIED};
            android.os.Bundle queryArgs = new android.os.Bundle();
            queryArgs.putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION,
                    "(" + MediaStore.Images.Media.RELATIVE_PATH + " LIKE ? OR "
                            + MediaStore.Images.Media.RELATIVE_PATH + " LIKE ?) AND ("
                            + MediaStore.Images.Media.MIME_TYPE + "=? OR "
                            + MediaStore.Images.Media.MIME_TYPE + "=?)");
            queryArgs.putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                    new String[]{Environment.DIRECTORY_PICTURES + "/Lika Lens/%",
                            Environment.DIRECTORY_PICTURES + "/SCOPE/%", "image/jpeg", "image/gif"});
            queryArgs.putString(android.content.ContentResolver.QUERY_ARG_SQL_SORT_ORDER, MEDIASTORE_SORT_ORDER);
            queryArgs.putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, 1);
            try (Cursor cursor = context.getContentResolver().query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, columns, queryArgs, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID));
                    String name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME));
                    long added = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED));
                    long modified = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED));
                    long timestamp = (added > 0 ? added : modified) * 1000L;
                    newest = new Photo(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id), name, timestamp);
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "Failed to query latest photo from MediaStore", e);
            }

            String[] videoCols = {MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.DATE_ADDED, MediaStore.Video.Media.DATE_MODIFIED,
                    MediaStore.Video.Media.DURATION};
            android.os.Bundle videoArgs = new android.os.Bundle();
            videoArgs.putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION,
                    "(" + MediaStore.Video.Media.RELATIVE_PATH + " LIKE ? OR "
                            + MediaStore.Video.Media.RELATIVE_PATH + " LIKE ?) AND "
                            + MediaStore.Video.Media.MIME_TYPE + "=?");
            videoArgs.putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                    new String[]{Environment.DIRECTORY_MOVIES + "/Lika Lens%",
                            Environment.DIRECTORY_MOVIES + "/SCOPE%", "video/mp4"});
            videoArgs.putString(android.content.ContentResolver.QUERY_ARG_SQL_SORT_ORDER, MEDIASTORE_VIDEO_SORT_ORDER);
            videoArgs.putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, 1);
            try (Cursor cursor = context.getContentResolver().query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, videoCols, videoArgs, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID));
                    String name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME));
                    long added = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED));
                    long modified = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED));
                    int durIdx = cursor.getColumnIndex(MediaStore.Video.Media.DURATION);
                    long durationMs = durIdx >= 0 ? cursor.getLong(durIdx) : 0L;
                    long timestamp = (added > 0 ? added : modified) * 1000L;
                    Photo latestVideo = new Photo(
                            ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id),
                            name, timestamp, true, durationMs);
                    newest = pickNewer(newest, latestVideo);
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "Failed to query latest video from MediaStore", e);
            }
        } else {
            List<Photo> videos = queryLegacyVideoFolder();
            if (!videos.isEmpty()) {
                Collections.sort(videos, (a, b) -> Long.compare(b.timestampMillis, a.timestampMillis));
                newest = pickNewer(newest, videos.get(0));
            }
        }
        List<Photo> photos = queryAppFolder(context);
        if (!photos.isEmpty()) {
            Collections.sort(photos, (a, b) -> Long.compare(b.timestampMillis, a.timestampMillis));
            newest = pickNewer(newest, photos.get(0));
        }
        return newest;
    }

    static Photo pickNewer(Photo first, Photo second) {
        if (first == null) return second;
        if (second == null) return first;
        return second.timestampMillis > first.timestampMillis ? second : first;
    }

    static String formatDuration(long durationMs) {
        long totalSec = Math.max(0L, Math.round(durationMs / 1000.0));
        long mins = totalSec / 60L;
        long secs = totalSec % 60L;
        return String.format(java.util.Locale.US, "%d:%02d", mins, secs);
    }

    static Bitmap loadThumbnail(Context context, Photo item, int targetPx) {
        if (context == null || item == null || item.uri == null) return null;
        int size = Math.max(128, targetPx);
        if (item.isVideo) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    Bitmap thumb = context.getContentResolver().loadThumbnail(
                            item.uri, new android.util.Size(size, size), null);
                    if (thumb != null) return thumb;
                } catch (Exception e) {
                    Log.d(TAG, "ContentResolver.loadThumbnail fallback for video: " + item.uri, e);
                }
            }
            android.media.MediaMetadataRetriever retriever = new android.media.MediaMetadataRetriever();
            try {
                retriever.setDataSource(context, item.uri);
                Bitmap frame = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    try {
                        frame = retriever.getScaledFrameAtTime(-1L,
                                android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC, size, size);
                    } catch (RuntimeException ignored) { }
                }
                if (frame == null) {
                    frame = retriever.getFrameAtTime(-1L,
                            android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                    if (frame != null) {
                        frame = GifMediaHelper.scaleDown(frame, size);
                    }
                }
                return frame;
            } catch (Throwable t) {
                Log.w(TAG, "Failed to decode video thumbnail: " + item.uri, t);
                return null;
            } finally {
                try { retriever.release(); } catch (Throwable ignored) { }
            }
        }
        try {
            android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = context.getContentResolver().openInputStream(item.uri)) {
                if (in == null) return null;
                android.graphics.BitmapFactory.decodeStream(in, null, bounds);
            }
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= size && bounds.outHeight / (sample * 2) >= size) {
                sample *= 2;
            }
            android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
            options.inSampleSize = sample;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            try (InputStream stream = context.getContentResolver().openInputStream(item.uri)) {
                if (stream == null) return null;
                Bitmap decoded = android.graphics.BitmapFactory.decodeStream(stream, null, options);
                return CaptureOrientation.orientBitmap(context, item.uri, decoded);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to decode thumbnail: " + item.uri, e);
            return null;
        }
    }

    static String archiveKey(String displayName) {
        if (displayName == null) return "";
        return displayName.toLowerCase(java.util.Locale.US).endsWith(".jpg")
                ? displayName.substring(0, displayName.length() - 4) : displayName;
    }

    static Uri saveBitmap(Context context, Bitmap bitmap, String name) {
        return saveBitmap(context, bitmap, name, "");
    }

    static Uri saveBitmap(Context context, Bitmap bitmap, String name, String subfolder) {
        if (bitmap == null) return null;
        int quality = CaptureFileNaming.resolveJpegQuality(
                AppSettings.get(context, AppSettings.JPEG_QUALITY, "High · 95% (Recommended)"));
        String dir = Environment.DIRECTORY_PICTURES + "/Lika Lens"
                + (subfolder != null && !subfolder.isEmpty() ? "/" + subfolder : "");
        try (MediaOutput output = MediaOutput.allocate(context, name, "image/jpeg", dir, true)) {
            try (OutputStream stream = output.open()) {
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)) {
                    throw new java.io.IOException("JPEG encode failed");
                }
            }
            output.publish();
            return output.result();
        } catch (Exception error) {
            Log.e(TAG, "Could not save photo", error);
            return null;
        }
    }

    static Uri saveGif(Context context,byte[] bytes,String name) {
        if(bytes==null || bytes.length==0) return null;
        try(MediaOutput output=MediaOutput.allocate(context,name,"image/gif",Environment.DIRECTORY_PICTURES+"/Lika Lens",true)) {
            try(OutputStream stream=output.open()) { stream.write(bytes); }
            output.publish();return output.result();
        } catch(Exception error) { Log.e(TAG,"Could not save GIF",error);return null; }
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
                MEDIASTORE_SORT_ORDER)) {
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

    private static List<Photo> queryMediaStoreVideos(Context context) {
        List<Photo> videos = new ArrayList<>();
        String[] columns = {MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DATE_ADDED, MediaStore.Video.Media.DATE_MODIFIED,
                MediaStore.Video.Media.DURATION};
        try (Cursor cursor = context.getContentResolver().query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, columns,
                "(" + MediaStore.Video.Media.RELATIVE_PATH + " LIKE ? OR "
                        + MediaStore.Video.Media.RELATIVE_PATH + " LIKE ?) AND "
                        + MediaStore.Video.Media.MIME_TYPE + "=?",
                new String[]{Environment.DIRECTORY_MOVIES + "/Lika Lens%",
                        Environment.DIRECTORY_MOVIES + "/SCOPE%", "video/mp4"},
                MEDIASTORE_VIDEO_SORT_ORDER)) {
            if (cursor == null) return videos;
            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
            int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME);
            int addedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED);
            int modifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED);
            int durationColumn = cursor.getColumnIndex(MediaStore.Video.Media.DURATION);
            while (cursor.moveToNext()) {
                long id = cursor.getLong(idColumn);
                String name = cursor.getString(nameColumn);
                long added = cursor.getLong(addedColumn);
                long modified = cursor.getLong(modifiedColumn);
                long durationMs = durationColumn >= 0 ? cursor.getLong(durationColumn) : 0L;
                long timestamp = (added > 0 ? added : modified) * 1000L;
                videos.add(new Photo(
                        ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id),
                        name, timestamp, true, durationMs));
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Failed to query video MediaStore", e);
        }
        return videos;
    }

    private static List<Photo> queryLegacyVideoFolder() {
        List<Photo> videos = new ArrayList<>();
        File movies = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
        if (movies != null) {
            for (String folderName : new String[]{"Lika Lens", "SCOPE"}) {
                collectAppFolderVideos(new File(movies, folderName), videos);
            }
        }
        return videos;
    }

    static void collectAppFolderVideos(File dir, List<Photo> out) {
        if (dir == null || !dir.isDirectory() || out == null) return;
        File[] entries = dir.listFiles();
        if (entries == null) return;
        for (File entry : entries) {
            if (entry == null) continue;
            if (entry.isDirectory()) {
                collectAppFolderVideos(entry, out);
            } else if (entry.isFile()) {
                String lower = entry.getName().toLowerCase(java.util.Locale.US);
                if (lower.endsWith(".mp4")) {
                    out.add(new Photo(Uri.fromFile(entry), entry.getName(), entry.lastModified(), true, 0L));
                }
            }
        }
    }

    private static List<Photo> queryAppFolder(Context context) {
        List<Photo> photos = new ArrayList<>();
        File pictures = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        for (String folderName : new String[]{"Lika Lens", "SCOPE"}) {
            collectAppFolderPhotos(new File(pictures, folderName), photos);
        }
        return photos;
    }

    static void collectAppFolderPhotos(File dir, List<Photo> out) {
        if (dir == null || !dir.isDirectory()) return;
        File[] entries = dir.listFiles();
        if (entries == null) return;
        for (File entry : entries) {
            if (entry == null) continue;
            if (entry.isDirectory()) {
                if (!"RAW".equalsIgnoreCase(entry.getName())) {
                    collectAppFolderPhotos(entry, out);
                }
            } else if (entry.isFile()) {
                String lower = entry.getName().toLowerCase(java.util.Locale.US);
                if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif")) {
                    out.add(new Photo(Uri.fromFile(entry), entry.getName(), entry.lastModified()));
                }
            }
        }
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
            // Only remove the app-private editable cache & edit recipe; explicitly saved DNGs are preserved.
            RawArchive.delete(context, stem);
            PhotoEditStore.reset(context, stem);
        }
        return deleted;
    }
}
