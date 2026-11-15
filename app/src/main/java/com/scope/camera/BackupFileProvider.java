package com.scope.camera;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Pure Android SDK ContentProvider for securely sharing named Lika Lens settings
 * backups via ACTION_SEND with FLAG_GRANT_READ_URI_PERMISSION.
 */
public class BackupFileProvider extends ContentProvider {
    public static final String AUTHORITY = "com.scope.camera.backupprovider";
    static final String MIME_TYPE = "application/json";

    public static File getBackupCacheDir(Context context) {
        return new File(context.getCacheDir(), "backups");
    }

    public static Uri getUriForFile(Context context, File file) {
        return new Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .path(file.getName())
                .build();
    }

    static File resolveSafeFile(File dir, String segment) {
        if (dir == null || segment == null) return null;
        String trimmed = segment.trim();
        if (trimmed.isEmpty() || trimmed.contains("/") || trimmed.contains("\\")
                || ".".equals(trimmed) || "..".equals(trimmed) || trimmed.contains("..")) {
            return null;
        }
        File candidate = new File(dir, trimmed);
        try {
            String dirCanonical = dir.getCanonicalPath();
            String fileCanonical = candidate.getCanonicalPath();
            if (!fileCanonical.startsWith(dirCanonical + File.separator)) {
                return null;
            }
        } catch (IOException e) {
            return null;
        }
        return candidate;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        Context context = getContext();
        if (context == null || uri == null) throw new FileNotFoundException("Invalid backup URI");
        File file = resolveSafeFile(getBackupCacheDir(context), uri.getLastPathSegment());
        if (file == null || !file.exists() || !file.isFile()) {
            throw new FileNotFoundException("Backup file not found: " + uri);
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return MIME_TYPE;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        Context context = getContext();
        if (context == null || uri == null) return null;
        File file = resolveSafeFile(getBackupCacheDir(context), uri.getLastPathSegment());
        String[] cols = projection != null ? projection : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor cursor = new MatrixCursor(cols);
        if (file != null && file.exists() && file.isFile()) {
            MatrixCursor.RowBuilder row = cursor.newRow();
            for (String col : cols) {
                if (OpenableColumns.DISPLAY_NAME.equals(col)) {
                    row.add(file.getName());
                } else if (OpenableColumns.SIZE.equals(col)) {
                    row.add(file.length());
                } else {
                    row.add(null);
                }
            }
        }
        return cursor;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) { return null; }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
