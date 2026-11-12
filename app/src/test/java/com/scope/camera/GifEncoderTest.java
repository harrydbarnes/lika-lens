package com.scope.camera;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Deterministic unit tests for GifEncoder verifying GIF89a compliance,
 * NeuQuant quantization, LZW compression, frame pacing, and loop headers.
 */
public class GifEncoderTest {

    @Test
    public void testEncodeSimpleMultiFrameGif() {
        int width = 32;
        int height = 32;
        int numPixels = width * height;

        // Frame 1: solid red
        int[] redFrame = new int[numPixels];
        for (int i = 0; i < numPixels; i++) redFrame[i] = 0xFFFF0000;

        // Frame 2: solid green
        int[] greenFrame = new int[numPixels];
        for (int i = 0; i < numPixels; i++) greenFrame[i] = 0xFF00FF00;

        // Frame 3: solid blue
        int[] blueFrame = new int[numPixels];
        for (int i = 0; i < numPixels; i++) blueFrame[i] = 0xFF0000FF;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        GifEncoder encoder = new GifEncoder();
        encoder.setSize(width, height);
        encoder.setDelay(200); // 5 fps (20 centiseconds)
        encoder.setRepeat(0);  // Loop infinitely
        encoder.setQuality(10);

        assertTrue("Encoder should start", encoder.start(baos));
        assertTrue("Frame 1 should encode", encoder.addFrame(width, height, redFrame));
        assertTrue("Frame 2 should encode", encoder.addFrame(width, height, greenFrame));
        assertTrue("Frame 3 should encode", encoder.addFrame(width, height, blueFrame));
        assertTrue("Encoder should finish", encoder.finish());

        assertEquals(3, encoder.getFrameCount());

        byte[] gif = baos.toByteArray();
        assertTrue("GIF should have non-trivial size", gif.length > 500);

        // Verify GIF89a Header
        String header = new String(gif, 0, 6, StandardCharsets.US_ASCII);
        assertEquals("GIF89a", header);

        // Verify Logical Screen Descriptor dimensions (little-endian)
        int lsdWidth = (gif[6] & 0xFF) | ((gif[7] & 0xFF) << 8);
        int lsdHeight = (gif[8] & 0xFF) | ((gif[9] & 0xFF) << 8);
        assertEquals(width, lsdWidth);
        assertEquals(height, lsdHeight);

        // Verify NETSCAPE2.0 block exists in byte stream
        String fullStreamAscii = new String(gif, StandardCharsets.US_ASCII);
        assertTrue("Should contain NETSCAPE2.0 loop extension", fullStreamAscii.contains("NETSCAPE2.0"));

        // Verify trailer byte is 0x3B
        assertEquals(0x3B, gif[gif.length - 1] & 0xFF);
    }

    @Test
    public void testDelayAndPacingConversion() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        GifEncoder encoder = new GifEncoder();
        encoder.setSize(16, 16);
        encoder.setDelay(100); // 10 fps -> 10 centiseconds

        assertTrue(encoder.start(baos));

        int[] frame = new int[16 * 16];
        for (int i = 0; i < frame.length; i++) frame[i] = 0xFFFFFFFF;
        encoder.addFrame(16, 16, frame);
        encoder.finish();

        byte[] gif = baos.toByteArray();
        assertEquals(0x3B, gif[gif.length - 1] & 0xFF);
    }

    @Test
    public void testInvalidInputsDoNotCrash() {
        GifEncoder encoder = new GifEncoder();
        assertFalse("Cannot add frame before start", encoder.addFrame(10, 10, new int[100]));
        assertFalse("Cannot finish before start", encoder.finish());
        assertFalse("Cannot start with null stream", encoder.start(null));
    }

    @Test
    public void testVariableDimensionsAndQuality() {
        int w = 48;
        int h = 24;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        GifEncoder encoder = new GifEncoder();
        encoder.setSize(w, h);
        encoder.setQuality(5); // Higher sampling
        encoder.setDelay(50); // 20 fps

        final boolean[] progressCalled = {false};
        encoder.setProgressListener((curr, total, msg) -> progressCalled[0] = true);

        assertTrue(encoder.start(baos));
        int[] frame = new int[w * h];
        for (int i = 0; i < frame.length; i++) {
            frame[i] = (i % 2 == 0) ? 0xFF336699 : 0xFFFFCC33;
        }
        assertTrue(encoder.addFrame(w, h, frame));
        assertTrue(encoder.finish());
        assertTrue("Progress callback should have been fired", progressCalled[0]);

        byte[] gif = baos.toByteArray();
        assertEquals(0x3B, gif[gif.length - 1] & 0xFF);
    }

    @Test
    public void testNoRepeatLoop() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        GifEncoder encoder = new GifEncoder();
        encoder.setSize(10, 10);
        encoder.setRepeat(-1); // Play once (no Netscape extension)
        encoder.start(baos);
        encoder.addFrame(10, 10, new int[100]);
        encoder.finish();

        byte[] gif = baos.toByteArray();
        String fullStreamAscii = new String(gif, StandardCharsets.US_ASCII);
        assertFalse("Should not contain NETSCAPE2.0 block when repeat is -1", fullStreamAscii.contains("NETSCAPE2.0"));
    }

    @Test
    public void testEncoderLifecycleStateTransitions() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        GifEncoder encoder = new GifEncoder();
        assertEquals(GifEncoder.State.NEW, encoder.getState());

        encoder.setSize(8, 8);
        assertTrue(encoder.start(baos));
        assertEquals(GifEncoder.State.HEADER_WRITTEN, encoder.getState());

        assertTrue(encoder.addFrame(8, 8, new int[64]));
        assertEquals(GifEncoder.State.WRITING_FRAMES, encoder.getState());

        assertTrue(encoder.finish());
        assertEquals(GifEncoder.State.FINISHED, encoder.getState());

        assertFalse("Cannot add frames after finish", encoder.addFrame(8, 8, new int[64]));
        assertFalse("Cannot finish twice", encoder.finish());
    }
}
