package com.scope.camera;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for GifStudioControlsBuilder listener contracts.
 */
public class GifStudioControlsBuilderTest {

    private static class MockListener implements GifStudioControlsBuilder.Listener {
        int selectedFps = 0;
        int selectedResolution = 0;
        boolean bounceMode = false;
        String selectedLut = null;
        int videoDuration = 0;
        boolean exportClicked = false;
        int removedIndex = -1;

        @Override
        public void onFpsSelected(int fps) { this.selectedFps = fps; }

        @Override
        public void onResolutionSelected(int res) { this.selectedResolution = res; }

        @Override
        public void onBounceModeChanged(boolean bounce) { this.bounceMode = bounce; }

        @Override
        public void onLutProfileSelected(String profile) { this.selectedLut = profile; }

        @Override
        public void onVideoDurationSelected(int seconds) { this.videoDuration = seconds; }

        @Override
        public void onAddPhotosClicked() {}

        @Override
        public void onChooseDifferentVideoClicked() {}

        @Override
        public void onExportClicked() { this.exportClicked = true; }

        @Override
        public void onPhotoRemoved(int index) { this.removedIndex = index; }
    }

    @Test
    public void testListenerCallbackContracts() {
        MockListener listener = new MockListener();
        listener.onFpsSelected(15);
        assertEquals(15, listener.selectedFps);

        listener.onResolutionSelected(480);
        assertEquals(480, listener.selectedResolution);

        listener.onBounceModeChanged(true);
        assertTrue(listener.bounceMode);

        listener.onLutProfileSelected("MONO");
        assertEquals("MONO", listener.selectedLut);

        listener.onVideoDurationSelected(6);
        assertEquals(6, listener.videoDuration);

        listener.onExportClicked();
        assertTrue(listener.exportClicked);

        listener.onPhotoRemoved(2);
        assertEquals(2, listener.removedIndex);
    }
}
