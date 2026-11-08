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

/**
 * Pure Android SDK ContentProvider for securely serving downloaded APK packages
 * to the Android OS PackageInstaller with FLAG_GRANT_READ_URI_PERMISSION.
 */
public class ApkFileProvider extends ContentProvider {
    public static final String AUTHORITY = "com.scope.camera.apkprovider";

    public static Uri getUriForFile(Context context, File file) {
        return new Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .path(file.getName())
                .build();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        Context context = getContext();
        if (context == null) throw new FileNotFoundException("Context is null");
        String filename = uri.getLastPathSegment();
        if (filename == null) throw new FileNotFoundException("Invalid URI: " + uri);
        File updateDir = new File(context.getCacheDir(), "updates");
        File file = new File(updateDir, filename);
        if (!file.exists()) {
            throw new FileNotFoundException("APK file not found: " + file.getAbsolutePath());
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return "application/vnd.android.package-archive";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        Context context = getContext();
        if (context == null) return null;
        String filename = uri.getLastPathSegment();
        File file = new File(new File(context.getCacheDir(), "updates"), filename != null ? filename : "");
        String[] cols = projection != null ? projection : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor cursor = new MatrixCursor(cols);
        if (file.exists()) {
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
