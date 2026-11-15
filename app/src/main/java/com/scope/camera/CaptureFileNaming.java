package com.scope.camera;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Locale;

/**
 * Pure filename sanitization, naming pattern resolution, collision prevention,
 * subfolder mapping, and JPEG quality mapping for captured and exported media.
 */
final class CaptureFileNaming {
    private static final int MAX_PREFIX_LEN = 24;
    private static final int RECENT_STEM_LIMIT = 64;
    private static final LinkedHashSet<String> RECENT_STEMS = new LinkedHashSet<>();

    interface StemExistsChecker {
        boolean exists(String candidateStem);
    }

    private CaptureFileNaming() { }

    static String sanitizePrefix(String prefix) {
        if (prefix == null) return "LIKA";
        String cleaned = prefix.trim().replaceAll("[^A-Za-z0-9_-]+", "_");
        cleaned = cleaned.replaceAll("_+", "_");
        while (cleaned.startsWith("_") || cleaned.startsWith("-")) {
            cleaned = cleaned.substring(1);
        }
        while (cleaned.endsWith("_") || cleaned.endsWith("-")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        if (cleaned.isEmpty()) return "LIKA";
        if (cleaned.length() > MAX_PREFIX_LEN) {
            cleaned = cleaned.substring(0, MAX_PREFIX_LEN);
            while (cleaned.endsWith("_") || cleaned.endsWith("-")) {
                cleaned = cleaned.substring(0, cleaned.length() - 1);
            }
            if (cleaned.isEmpty()) return "LIKA";
        }
        return cleaned;
    }

    static String resolvePrefix(String filePrefixPref, String customPrefixValue) {
        if (filePrefixPref != null && (filePrefixPref.startsWith("CUSTOM") || filePrefixPref.startsWith("Custom"))) {
            return sanitizePrefix(customPrefixValue);
        }
        if (filePrefixPref == null || filePrefixPref.trim().isEmpty()) {
            return "LIKA";
        }
        return sanitizePrefix(filePrefixPref);
    }

    static String sanitizeBrainToken(String brain) {
        if (brain == null || brain.trim().isEmpty()) return "LIKA";
        String token = brain.replaceAll("[^A-Za-z0-9]", "");
        return token.isEmpty() ? "LIKA" : token;
    }

    static String buildStillBaseStem(String prefix, String namingPattern, String brain, String timestampStamp, int rollNumber) {
        String safePrefix = sanitizePrefix(prefix);
        String safeStamp = (timestampStamp == null || timestampStamp.trim().isEmpty())
                ? String.valueOf(System.currentTimeMillis())
                : timestampStamp.trim();
        if (namingPattern != null && namingPattern.contains("Film Look")) {
            return safePrefix + "_" + sanitizeBrainToken(brain) + "_" + safeStamp;
        }
        if (namingPattern != null && namingPattern.contains("Roll counter")) {
            int safeRoll = Math.max(1, rollNumber);
            return safePrefix + "_ROLL_" + String.format(Locale.US, "%04d", safeRoll);
        }
        return safePrefix + "_" + safeStamp;
    }

    static String buildCopyBaseStem(String prefix, String timestampStamp) {
        String safePrefix = sanitizePrefix(prefix);
        String safeStamp = (timestampStamp == null || timestampStamp.trim().isEmpty())
                ? String.valueOf(System.currentTimeMillis())
                : timestampStamp.trim();
        return safePrefix + "_EDIT_" + safeStamp;
    }

    static String buildVideoStillBaseStem(String prefix, String timestampStamp) {
        String safePrefix = sanitizePrefix(prefix);
        String safeStamp = (timestampStamp == null || timestampStamp.trim().isEmpty())
                ? String.valueOf(System.currentTimeMillis())
                : timestampStamp.trim();
        return safePrefix + "_STILL_" + safeStamp;
    }

    static String buildVideoBaseStem(String prefix, String timestampStamp) {
        String safePrefix = sanitizePrefix(prefix);
        String safeStamp = (timestampStamp == null || timestampStamp.trim().isEmpty())
                ? String.valueOf(System.currentTimeMillis())
                : timestampStamp.trim();
        return safePrefix + "_VIDEO_" + safeStamp;
    }

    static String ensureUniqueStem(String candidateStem, StemExistsChecker existsChecker) {
        String base = (candidateStem == null || candidateStem.trim().isEmpty())
                ? ("LIKA_" + System.currentTimeMillis())
                : candidateStem.trim();
        synchronized (RECENT_STEMS) {
            String resolved = base;
            if (isTaken(resolved, existsChecker)) {
                resolved = null;
                for (int suffix = 2; suffix <= 9999; suffix++) {
                    String next = base + "_" + suffix;
                    if (!isTaken(next, existsChecker)) {
                        resolved = next;
                        break;
                    }
                }
                if (resolved == null) {
                    resolved = base + "_" + System.nanoTime();
                }
            }
            recordRecentStem(resolved);
            return resolved;
        }
    }

    private static boolean isTaken(String stem, StemExistsChecker existsChecker) {
        if (RECENT_STEMS.contains(stem)) return true;
        return existsChecker != null && existsChecker.exists(stem);
    }

    private static void recordRecentStem(String stem) {
        RECENT_STEMS.add(stem);
        while (RECENT_STEMS.size() > RECENT_STEM_LIMIT) {
            String oldest = RECENT_STEMS.iterator().next();
            RECENT_STEMS.remove(oldest);
        }
    }

    static String buildFolderSubpath(String folderOrgSetting, String brain, String monthFolder) {
        if (folderOrgSetting == null) return "";
        if (folderOrgSetting.contains("month")) {
            return monthFolder == null ? "" : monthFolder.trim();
        }
        if (folderOrgSetting.contains("film look")) {
            if (brain == null || brain.trim().isEmpty()) return "LIKA_FILM";
            String cleaned = brain.replaceAll("[^A-Za-z0-9_ ]", "_")
                    .trim()
                    .replaceAll("\\s+", "_")
                    .replaceAll("_+", "_");
            while (cleaned.startsWith("_")) cleaned = cleaned.substring(1);
            while (cleaned.endsWith("_")) cleaned = cleaned.substring(0, cleaned.length() - 1);
            return cleaned.isEmpty() ? "LIKA_FILM" : cleaned;
        }
        return "";
    }

    static int resolveJpegQuality(String qualityChoice) {
        if (qualityChoice == null) return 95;
        if (qualityChoice.startsWith("Maximum")) return 98;
        if (qualityChoice.startsWith("High")) return 95;
        if (qualityChoice.startsWith("Balanced")) return 90;
        if (qualityChoice.startsWith("Compact")) return 85;
        return 95;
    }

    static boolean stillStemExists(Context context, String stem, String orgSub) {
        if (context == null || stem == null || stem.isEmpty()) return false;
        if (RawArchive.exists(context, stem)) return true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                Uri filesUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                try (Cursor cursor = context.getContentResolver().query(
                        filesUri,
                        new String[]{MediaStore.MediaColumns._ID},
                        MediaStore.MediaColumns.DISPLAY_NAME + "=? OR " + MediaStore.MediaColumns.DISPLAY_NAME + "=?",
                        new String[]{stem + ".jpg", stem + ".dng"},
                        null)) {
                    if (cursor != null && cursor.moveToFirst()) return true;
                }
            } catch (RuntimeException ignored) { }
        }
        File pictures = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (pictures != null) {
            File baseDir = new File(pictures, "Lika Lens");
            File targetDir = (orgSub == null || orgSub.isEmpty()) ? baseDir : new File(baseDir, orgSub.replace('/', File.separatorChar));
            if (new File(targetDir, stem + ".jpg").exists()) return true;
            if (new File(new File(targetDir, "RAW"), stem + ".dng").exists()) return true;
        }
        return false;
    }

    static boolean videoStemExists(Context context, String stem) {
        if (context == null || stem == null || stem.isEmpty()) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                Uri videoUri = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                try (Cursor cursor = context.getContentResolver().query(
                        videoUri,
                        new String[]{MediaStore.MediaColumns._ID},
                        MediaStore.MediaColumns.DISPLAY_NAME + "=?",
                        new String[]{stem + ".mp4"},
                        null)) {
                    if (cursor != null && cursor.moveToFirst()) return true;
                }
            } catch (RuntimeException ignored) { }
        }
        File movies = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
        if (movies != null && new File(new File(movies, "Lika Lens"), stem + ".mp4").exists()) {
            return true;
        }
        return false;
    }
}
