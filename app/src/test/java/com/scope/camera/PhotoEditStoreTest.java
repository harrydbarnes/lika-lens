package com.scope.camera;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Unit tests for PhotoEditStore key sanitization and prefixing logic.
 */
public class PhotoEditStoreTest {

    @Test
    public void testPrefixSanitization() {
        assertEquals("photo_123.", PhotoEditStore.prefix("photo 123"));
        assertEquals("test_image.", PhotoEditStore.prefix("test/image"));
        assertEquals("sample_name.", PhotoEditStore.prefix("sample:name"));
        assertEquals("IMG_20261108_120000.", PhotoEditStore.prefix("IMG_20261108_120000"));
        assertEquals(".", PhotoEditStore.prefix(null));
        assertEquals(".", PhotoEditStore.prefix(""));
    }
}
