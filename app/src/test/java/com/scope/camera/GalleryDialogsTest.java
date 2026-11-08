package com.scope.camera;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for GalleryDialogs utility class contracts.
 */
public class GalleryDialogsTest {

    @Test
    public void testClassModifiersAndConstructor() throws Exception {
        assertTrue(Modifier.isPublic(GalleryDialogs.class.getModifiers()));
        assertTrue(Modifier.isFinal(GalleryDialogs.class.getModifiers()));

        Constructor<?>[] constructors = GalleryDialogs.class.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertTrue(Modifier.isPrivate(constructors[0].getModifiers()));

        constructors[0].setAccessible(true);
        Object instance = constructors[0].newInstance();
        assertNotNull(instance);
    }
}
