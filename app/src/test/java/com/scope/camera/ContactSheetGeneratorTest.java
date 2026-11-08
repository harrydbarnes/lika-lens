package com.scope.camera;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for ContactSheetGenerator verifying validation and empty photo state handling.
 */
public class ContactSheetGeneratorTest {

    @Test
    public void testEmptyPhotosTriggersErrorCallback() {
        List<PhotoRepository.Photo> emptyList = new ArrayList<>();
        AtomicBoolean errorCalled = new AtomicBoolean(false);
        AtomicReference<String> errorMessage = new AtomicReference<>();

        ContactSheetGenerator.generate(null, emptyList, new ContactSheetGenerator.Callback() {
            @Override
            public void onCompleted(android.net.Uri savedUri, String fileName) {}

            @Override
            public void onError(String message) {
                errorCalled.set(true);
                errorMessage.set(message);
            }
        });

        assertTrue(errorCalled.get());
        assertEquals("No photos to generate proof sheet", errorMessage.get());
    }
}
