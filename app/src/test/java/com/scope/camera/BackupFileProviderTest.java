package com.scope.camera;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for BackupFileProvider contract, MIME type, and safe path resolution.
 */
public class BackupFileProviderTest {

    @Test
    public void testAuthorityAndMimeType() {
        assertEquals("com.scope.camera.backupprovider", BackupFileProvider.AUTHORITY);
        BackupFileProvider provider = new BackupFileProvider();
        assertEquals("application/json", provider.getType(null));
        assertTrue(provider.onCreate());
        assertNull(provider.insert(null, null));
        assertEquals(0, provider.delete(null, null, null));
        assertEquals(0, provider.update(null, null, null, null));
    }

    @Test
    public void testResolveSafeFileRejectsTraversal() {
        File cacheDir = new File(System.getProperty("java.io.tmpdir"), "lika-backup-provider-test");
        assertNull(BackupFileProvider.resolveSafeFile(cacheDir, null));
        assertNull(BackupFileProvider.resolveSafeFile(cacheDir, ""));
        assertNull(BackupFileProvider.resolveSafeFile(cacheDir, ".."));
        assertNull(BackupFileProvider.resolveSafeFile(cacheDir, "../secret.json"));
        assertNull(BackupFileProvider.resolveSafeFile(cacheDir, "sub/backup.likalens.json"));
        assertNull(BackupFileProvider.resolveSafeFile(cacheDir, "sub\\backup.likalens.json"));

        File safe = BackupFileProvider.resolveSafeFile(cacheDir, "My_Backup.likalens.json");
        assertNotNull(safe);
        assertEquals("My_Backup.likalens.json", safe.getName());
    }
}
