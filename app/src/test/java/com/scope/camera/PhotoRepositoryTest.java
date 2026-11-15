package com.scope.camera;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for PhotoRepository domain logic, archive key extraction, and Photo model.
 */
public class PhotoRepositoryTest {

    @Test
    public void testArchiveKeyExtraction() {
        assertEquals("LIKA_20261108_120000", PhotoRepository.archiveKey("LIKA_20261108_120000.jpg"));
        assertEquals("LIKA_20261108_120000", PhotoRepository.archiveKey("LIKA_20261108_120000.JPG"));
        assertEquals("animation.gif", PhotoRepository.archiveKey("animation.gif"));
        assertEquals("raw_photo.dng", PhotoRepository.archiveKey("raw_photo.dng"));
        assertEquals("", PhotoRepository.archiveKey(null));
        assertEquals("", PhotoRepository.archiveKey(""));
    }

    @Test
    public void testPhotoModelFieldsAndGifDetection() {
        PhotoRepository.Photo jpegPhoto = new PhotoRepository.Photo(null, "sample_image.jpg", 100000L);
        assertNotNull(jpegPhoto);
        assertEquals("sample_image.jpg", jpegPhoto.name);
        assertEquals(100000L, jpegPhoto.timestampMillis);
        assertFalse(jpegPhoto.isGif);

        PhotoRepository.Photo gifPhoto = new PhotoRepository.Photo(null, "animated_clip.gif", 200000L);
        assertNotNull(gifPhoto);
        assertEquals("animated_clip.gif", gifPhoto.name);
        assertEquals(200000L, gifPhoto.timestampMillis);
        assertTrue(gifPhoto.isGif);

        PhotoRepository.Photo uppercaseGif = new PhotoRepository.Photo(null, "UPPERCASE.GIF", 300000L);
        assertNotNull(uppercaseGif);
        assertTrue(uppercaseGif.isGif);

        PhotoRepository.Photo nullNamePhoto = new PhotoRepository.Photo(null, null, 0L);
        assertNotNull(nullNamePhoto);
        assertFalse(nullNamePhoto.isGif);
    }

    @Test
    public void testMediaStoreSortOrderAvoidsLimitClause() {
        String sort = PhotoRepository.MEDIASTORE_SORT_ORDER;
        assertFalse(sort.toUpperCase(java.util.Locale.US).contains("LIMIT"));
        assertTrue(sort.contains("_id DESC"));
    }

    @Test
    public void testPickNewerSelectsMostRecentPhoto() {
        PhotoRepository.Photo older = new PhotoRepository.Photo(null, "older.jpg", 1000L);
        PhotoRepository.Photo newer = new PhotoRepository.Photo(null, "newer.jpg", 2000L);

        assertEquals(newer, PhotoRepository.pickNewer(older, newer));
        assertEquals(newer, PhotoRepository.pickNewer(newer, older));
        assertEquals(older, PhotoRepository.pickNewer(older, null));
        assertEquals(newer, PhotoRepository.pickNewer(null, newer));
        assertEquals(older, PhotoRepository.pickNewer(older, new PhotoRepository.Photo(null, "tie.jpg", 1000L)));
    }

    @Test
    public void testCollectAppFolderPhotosScansSubfoldersAndExcludesRaw() throws Exception {
        java.io.File root = java.nio.file.Files.createTempDirectory("photo_repo_scan_test").toFile();
        try {
            java.io.File monthDir = new java.io.File(root, "2026-11");
            java.io.File rawDir = new java.io.File(monthDir, "RAW");
            assertTrue(rawDir.mkdirs());
            assertTrue(new java.io.File(root, "root.jpg").createNewFile());
            assertTrue(new java.io.File(monthDir, "month.jpeg").createNewFile());
            assertTrue(new java.io.File(monthDir, "clip.gif").createNewFile());
            assertTrue(new java.io.File(monthDir, "ignored.dng").createNewFile());
            assertTrue(new java.io.File(rawDir, "raw_only.jpg").createNewFile());

            java.util.List<PhotoRepository.Photo> out = new java.util.ArrayList<>();
            PhotoRepository.collectAppFolderPhotos(root, out);

            java.util.Set<String> names = new java.util.HashSet<>();
            for (PhotoRepository.Photo p : out) names.add(p.name);
            assertEquals(3, names.size());
            assertTrue(names.contains("root.jpg"));
            assertTrue(names.contains("month.jpeg"));
            assertTrue(names.contains("clip.gif"));
            assertFalse(names.contains("ignored.dng"));
            assertFalse(names.contains("raw_only.jpg"));
        } finally {
            java.io.File[] stack = root.listFiles();
            if (stack != null) {
                for (java.io.File f : stack) deleteTree(f);
            }
            root.delete();
        }
    }

    private static void deleteTree(java.io.File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            java.io.File[] children = f.listFiles();
            if (children != null) {
                for (java.io.File c : children) deleteTree(c);
            }
        }
        f.delete();
    }
}
