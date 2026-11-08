package com.scope.camera;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for ApkFileProvider contract and type resolution.
 */
public class ApkFileProviderTest {

    @Test
    public void testAuthorityConstant() {
        assertEquals("com.scope.camera.apkprovider", ApkFileProvider.AUTHORITY);
    }

    @Test
    public void testGetTypeReturnsApkMimeType() {
        ApkFileProvider provider = new ApkFileProvider();
        assertEquals("application/vnd.android.package-archive", provider.getType(null));
    }

    @Test
    public void testLifecycleAndNoOpCrud() {
        ApkFileProvider provider = new ApkFileProvider();
        assertTrue(provider.onCreate());
        assertNull(provider.insert(null, null));
        assertEquals(0, provider.delete(null, null, null));
        assertEquals(0, provider.update(null, null, null, null));
    }
}
