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
    @Test public void archiveRoundTripKeepsOrientationAndExposureIntent() throws Exception {
        java.io.File file = java.io.File.createTempFile("lika-raw-v5", ".sraw");
        try {
            RawArchive.Frame frame = new RawArchive.Frame();
            frame.width=2; frame.height=2; frame.orientation=270; frame.key="fixture";
            frame.staticWhiteLevel=1023; frame.dynamicWhiteLevel=-1;
            frame.aeCompensationEv=-1.5f; frame.manualExposure=true; frame.previewMidTarget=.075f;
            frame.captureBuffer=ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            frame.captureBuffer.putShort((short)100).putShort((short)200).putShort((short)300).putShort((short)400);
            frame.rowStride=4; frame.pixelStride=2;
            RawArchive.writeArchive(file,frame);
            RawArchive.Frame restored = RawArchive.read(file);
            assertEquals(270,restored.orientation);
            assertEquals(-1.5f,restored.aeCompensationEv,.0001f);
            org.junit.Assert.assertTrue(restored.manualExposure);
            assertEquals(.075f,restored.previewMidTarget,.0001f);
            ByteBuffer samples=restored.rawBuffer();
            assertEquals(100,samples.getShort()); assertEquals(200,samples.getShort());
            assertEquals(300,samples.getShort()); assertEquals(400,samples.getShort());
        } finally { file.delete(); }
    }

    @Test public void versionFourArchiveRemainsReadableWithoutConsumingSensorBytesAsMetadata() throws Exception {
        java.io.File file=java.io.File.createTempFile("lika-raw-v4", ".sraw");
        try {
            try(java.io.DataOutputStream out=new java.io.DataOutputStream(new java.util.zip.DeflaterOutputStream(new java.io.FileOutputStream(file)))) {
                out.writeInt(0x53524157); out.writeInt(4);
                out.writeInt(2); out.writeInt(2); out.writeInt(0); out.writeInt(90);
                out.writeUTF("old"); out.writeUTF("4:3");
                out.writeInt(1023); out.writeInt(-1); out.writeLong(10000000);
                out.writeInt(800); out.writeInt(-3);
                for(int i=0;i<4;i++) out.writeFloat(64);
                for(int i=0;i<4;i++) out.writeFloat(1);
                for(int i=0;i<3;i++) out.writeFloat(0);
                // Three absent matrices, no lens-shading grid, then post-RAW sensitivity boost.
                out.writeBoolean(false); out.writeBoolean(false); out.writeBoolean(false);
                out.writeInt(0); out.writeInt(0); out.writeInt(0); out.writeInt(0); out.writeInt(200);
                for(int v:new int[]{100,200,300,400}) out.writeShort(v);
            }
            RawArchive.Frame restored=RawArchive.read(file);
            assertEquals(90,restored.orientation);
            assertEquals(200,restored.postRawSensitivityBoost);
            assertEquals(0f,restored.aeCompensationEv,0f);
            assertEquals(0f,restored.previewMidTarget,0f);
            org.junit.Assert.assertFalse(restored.manualExposure);
            assertEquals(100,restored.rawBuffer().getShort());
        } finally { file.delete(); }
    }

}
