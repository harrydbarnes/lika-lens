package com.scope.camera;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Unit tests for RawArchive.Frame data structures and buffer extraction.
 */
public class RawArchiveTest {

    @Test
    public void testFrameDefaults() {
        RawArchive.Frame frame = new RawArchive.Frame();
        assertEquals("4:3", frame.aspectRatio);
        assertEquals(2, frame.pixelStride);
        assertEquals(100, frame.postRawSensitivityBoost);
        assertNotNull(frame.blackLevels);
        assertEquals(4, frame.blackLevels.length);
        assertNotNull(frame.wbGains);
        assertEquals(4, frame.wbGains.length);
        assertEquals(1.0f, frame.wbGains[0], 0.001f);
        assertEquals(1.0f, frame.wbGains[1], 0.001f);
    }

    @Test
    public void testRawBufferNullWhenEmpty() {
        RawArchive.Frame frame = new RawArchive.Frame();
        assertNull(frame.rawBuffer());
    }

    @Test
    public void testRawBufferFromSamplesUsesBigEndian() {
        RawArchive.Frame frame = new RawArchive.Frame();
        byte[] samples = new byte[]{1, 2, 3, 4};
        frame.rawSamples = samples;

        ByteBuffer buffer = frame.rawBuffer();
        assertNotNull(buffer);
        assertEquals(ByteOrder.BIG_ENDIAN, buffer.order());
        assertEquals(4, buffer.remaining());
        assertEquals(1, buffer.get());
    }

    @Test
    public void testRawBufferFromCaptureBufferUsesLittleEndian() {
        RawArchive.Frame frame = new RawArchive.Frame();
        ByteBuffer capture = ByteBuffer.allocateDirect(16).order(ByteOrder.LITTLE_ENDIAN);
        capture.putInt(42);
        capture.flip();
        frame.captureBuffer = capture;

        ByteBuffer buffer = frame.rawBuffer();
        assertNotNull(buffer);
        assertEquals(ByteOrder.LITTLE_ENDIAN, buffer.order());
        assertEquals(42, buffer.getInt());
    }

    @Test
    public void testColorCorrectionTransformField() {
        RawArchive.Frame frame = new RawArchive.Frame();
        assertNull(frame.colorCorrectionTransform);
        frame.colorCorrectionTransform = new float[]{1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f};
        assertNotNull(frame.colorCorrectionTransform);
        assertEquals(9, frame.colorCorrectionTransform.length);
    }
}
