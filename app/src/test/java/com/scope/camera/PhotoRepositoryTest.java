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
}
