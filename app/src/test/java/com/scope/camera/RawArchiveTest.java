package com.scope.camera;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for RawArchive cache measurement, FIFO limit enforcement, pinning, and clear operations.
 */
public class RawArchiveTest {

    private static File writeDummyArchive(File dir, String key, int bytes, long lastModified) throws Exception {
        File f = new File(dir, RawArchive.sanitizeKey(key) + ".sraw");
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(new byte[bytes]);
        }
        assertTrue(f.setLastModified(lastModified));
        return f;
    }

    @Test
    public void testPinReferenceCountingProtectsConcurrentUsers() {
        String key = "PIN_TEST_KEY";
        assertFalse(RawArchive.isPinned(key));
        RawArchive.pin(key);
        RawArchive.pin(key);
        assertTrue(RawArchive.isPinned(key));
        RawArchive.unpin(key);
        assertTrue(RawArchive.isPinned(key));
        RawArchive.unpin(key);
        assertFalse(RawArchive.isPinned(key));
    }

    @Test
    public void testEnforceStorageLimitDeletesOldestUnpinnedAndSkipsProtected() throws Exception {
        File dir = Files.createTempDirectory("raw_archive_limit_test").toFile();
        try {
            File f1 = writeDummyArchive(dir, "A_OLDEST_PINNED", 1000, 1000L);
            File f2 = writeDummyArchive(dir, "B_OLD", 1000, 2000L);
            File f3 = writeDummyArchive(dir, "C_MID", 1000, 3000L);
            File f4 = writeDummyArchive(dir, "D_NEWEST_PROTECTED", 1000, 4000L);
            File tmp = new File(dir, "IN_PROGRESS.sraw.tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(new byte[500]);
            }

            assertEquals(4, RawArchive.countArchives(dir));
            assertEquals(4000L, RawArchive.calculateDirectoryBytes(dir));

            RawArchive.pin("A_OLDEST_PINNED");
            try {
                RawArchive.CleanupResult result = RawArchive.enforceStorageLimit(dir, 2500L, "D_NEWEST_PROTECTED");
                assertEquals(2, result.deletedCount);
                assertEquals(2000L, result.freedBytes);
                assertEquals(1, result.skippedActiveCount);
                assertTrue("Pinned archive must never be deleted", f1.isFile());
                assertFalse("Oldest unpinned archive should be pruned", f2.isFile());
                assertFalse("Second oldest unpinned archive should be pruned to meet cap", f3.isFile());
                assertTrue("Protected newly written archive must survive", f4.isFile());
                assertTrue("In-progress .tmp file must be ignored", tmp.isFile());
                assertEquals(2000L, RawArchive.calculateDirectoryBytes(dir));
            } finally {
                RawArchive.unpin("A_OLDEST_PINNED");
            }
        } finally {
            for (File f : dir.listFiles()) f.delete();
            dir.delete();
        }
    }

    @Test
    public void testClearEditableOriginalsRemovesUnpinnedAndKeepsPinnedAndSidecar() throws Exception {
        File dir = Files.createTempDirectory("raw_archive_clear_test").toFile();
        try {
            File f1 = writeDummyArchive(dir, "FRAME_1", 512, 1000L);
            File f2 = writeDummyArchive(dir, "FRAME_2_ACTIVE", 512, 2000L);
            RawArchive.pin("FRAME_2_ACTIVE");
            try {
                RawArchive.CleanupResult res = RawArchive.clearEditableOriginals(dir);
                assertEquals(1, res.deletedCount);
                assertEquals(512L, res.freedBytes);
                assertEquals(1, res.skippedActiveCount);
                assertFalse(f1.isFile());
                assertTrue("Pinned editable original must remain while open", f2.isFile());
            } finally {
                RawArchive.unpin("FRAME_2_ACTIVE");
            }
            RawArchive.CleanupResult res2 = RawArchive.clearEditableOriginals(dir);
            assertEquals(1, res2.deletedCount);
            assertEquals(0, res2.skippedActiveCount);
            assertFalse(f2.isFile());
        } finally {
            for (File f : dir.listFiles()) f.delete();
            dir.delete();
        }
    }

    @Test
    public void testFormatBytes() {
        assertEquals("512 B", RawArchive.formatBytes(512L));
        assertEquals("2 KB", RawArchive.formatBytes(2048L));
        assertEquals("1.5 MB", RawArchive.formatBytes(1572864L));
    }
}
