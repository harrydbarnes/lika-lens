package com.scope.camera;

import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.BlackLevelPattern;
import android.hardware.camera2.params.ColorSpaceTransform;
import android.hardware.camera2.params.LensShadingMap;
import android.hardware.camera2.params.RggbChannelVector;
import android.media.Image;
import android.util.Rational;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/** Compressed, app-private sensor source and the capture metadata needed to develop it again. */
final class RawArchive {
    private static final int MAGIC = 0x53524157; // SRAW
    private static final int VERSION = 2;

    static final class Frame {
        int width;
        int height;
        int cfa;
        int orientation;
        String key;
        int staticWhiteLevel;
        int dynamicWhiteLevel;
        long exposureTimeNs;
        int sensitivityIso;
        int aeExposureCompensation;
        float[] blackLevels = new float[4];
        float[] wbGains = new float[]{1f, 1f, 1f, 1f};
        float[] neutralPoint = new float[3];
        float[] sensorToXyz1;
        float[] sensorToXyz2;
        int illuminant1;
        int illuminant2;
        String aspectRatio = "4:3";
        int lsmCols;
        int lsmRows;
        float[] lsmGains;
        byte[] rawSamples;
        ByteBuffer captureBuffer;
        int rowStride;
        int pixelStride = 2;

        ByteBuffer rawBuffer() {
            return rawSamples != null ? ByteBuffer.wrap(rawSamples).order(ByteOrder.BIG_ENDIAN)
                    : captureBuffer == null ? null : captureBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        }
    }

    private RawArchive() { }

    static boolean exists(Context context, String key) { return file(context, key).isFile(); }
    static void delete(Context context, String key) { File f = file(context, key); if (f.isFile()) f.delete(); }

    static File file(Context context, String key) {
        String safe = key == null ? "" : key.replaceAll("[^A-Za-z0-9_.-]", "_");
        return new File(new File(context.getFilesDir(), "raw-archive"), safe + ".sraw");
    }

    static Frame createFrame(String key, Image image, CameraCharacteristics characteristics,
                             TotalCaptureResult result, int orientation, String aspectRatio) {
        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer source = plane.getBuffer().duplicate().order(ByteOrder.LITTLE_ENDIAN);
        source.position(0);
        int width = image.getWidth();
        int height = image.getHeight();
        int pixelStride = plane.getPixelStride() > 0 ? plane.getPixelStride() : 2;
        int rowStride = plane.getRowStride() > 0 ? plane.getRowStride() : width * pixelStride;
        Integer cfaValue = characteristics.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT);
        int cfa = cfaValue == null ? CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGGB : cfaValue;
        Integer staticWhite = characteristics.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL);
        Integer dynamicWhite = result == null ? null : result.get(CaptureResult.SENSOR_DYNAMIC_WHITE_LEVEL);
        BlackLevelPattern staticBlack = characteristics.get(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN);
        float[] dynamicBlack = result == null ? null : result.get(CaptureResult.SENSOR_DYNAMIC_BLACK_LEVEL);
        long exposure = result == null || result.get(CaptureResult.SENSOR_EXPOSURE_TIME) == null
                ? 0L : result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
        Integer isoValue = result == null ? null : result.get(CaptureResult.SENSOR_SENSITIVITY);
        Integer compensation = result == null ? null : result.get(CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION);
        RggbChannelVector gains = result == null ? null : result.get(CaptureResult.COLOR_CORRECTION_GAINS);
        Rational[] neutral = result == null ? null : result.get(CaptureResult.SENSOR_NEUTRAL_COLOR_POINT);
        float[] neutralValues = new float[3];
        if (neutral != null && neutral.length >= 3) {
            for (int i = 0; i < 3; i++) neutralValues[i] = neutral[i].floatValue();
        }
        float[] wb = gains == null ? new float[]{1f, 1f, 1f, 1f}
                : new float[]{gains.getRed(), gains.getGreenEven(), gains.getGreenOdd(), gains.getBlue()};

        Integer illum1 = characteristics.get(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT1);
        Byte illum2 = characteristics.get(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT2);
        float[] toXyz1 = profileMatrix(characteristics.get(CameraCharacteristics.SENSOR_FORWARD_MATRIX1),
                characteristics.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM1),
                characteristics.get(CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM1));
        float[] toXyz2 = profileMatrix(characteristics.get(CameraCharacteristics.SENSOR_FORWARD_MATRIX2),
                characteristics.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM2),
                characteristics.get(CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM2));

        Frame frame = new Frame();
        frame.width = width; frame.height = height; frame.cfa = cfa; frame.orientation = orientation;
        frame.key = key; frame.aspectRatio = aspectRatio == null ? "4:3" : aspectRatio;
        frame.staticWhiteLevel = staticWhite == null ? 65535 : staticWhite;
        frame.dynamicWhiteLevel = dynamicWhite == null ? -1 : dynamicWhite;
        frame.exposureTimeNs = exposure; frame.sensitivityIso = isoValue == null ? 0 : isoValue;
        frame.aeExposureCompensation = compensation == null ? 0 : compensation;
        frame.blackLevels = new float[4];
        for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++) {
            float black = staticBlack == null ? 64f : staticBlack.getOffsetForIndex(x, y);
            if (dynamicBlack != null && dynamicBlack.length >= 4) black = dynamicBlackFor(dynamicBlack, x, y);
            frame.blackLevels[y * 2 + x] = black;
        }
        frame.wbGains = wb; frame.neutralPoint = neutralValues;
        frame.sensorToXyz1 = toXyz1; frame.sensorToXyz2 = toXyz2;
        frame.illuminant1 = illum1 == null ? 0 : illum1;
        frame.illuminant2 = illum2 == null ? 0 : Byte.toUnsignedInt(illum2);
        if (result != null) {
            try {
                LensShadingMap lsm = result.get(CaptureResult.STATISTICS_LENS_SHADING_CORRECTION_MAP);
                if (lsm != null && lsm.getColumnCount() > 0 && lsm.getRowCount() > 0) {
                    frame.lsmCols = lsm.getColumnCount();
                    frame.lsmRows = lsm.getRowCount();
                    frame.lsmGains = new float[lsm.getGainFactorCount()];
                    lsm.copyGainFactors(frame.lsmGains, 0);
                }
            } catch (Throwable ignored) { }
        }
        frame.captureBuffer = source;
        frame.rowStride = rowStride; frame.pixelStride = pixelStride;
        return frame;
    }

    static void writeArchive(Context context, Frame frame) throws IOException {
        if (frame == null || frame.captureBuffer == null) return;
        File target = file(context, frame.key);
        File folder = target.getParentFile();
        if (folder == null || (!folder.exists() && !folder.mkdirs())) throw new IOException("Could not create RAW archive folder");
        File temporary = new File(folder, target.getName() + ".tmp");
        ByteBuffer source = frame.captureBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        source.position(0);
        int width = frame.width;
        int height = frame.height;
        int rowStride = frame.rowStride;
        int pixelStride = frame.pixelStride;

        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                new DeflaterOutputStream(new FileOutputStream(temporary), new Deflater(4))))) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(frame.width); out.writeInt(frame.height); out.writeInt(frame.cfa);
            out.writeInt(frame.orientation);
            out.writeUTF(frame.key == null ? "" : frame.key);
            out.writeUTF(frame.aspectRatio);
            out.writeInt(frame.staticWhiteLevel); out.writeInt(frame.dynamicWhiteLevel);
            out.writeLong(frame.exposureTimeNs);
            out.writeInt(frame.sensitivityIso); out.writeInt(frame.aeExposureCompensation);
            for (float v : frame.blackLevels) out.writeFloat(v);
            for (float v : frame.wbGains) out.writeFloat(v);
            for (float v : frame.neutralPoint) out.writeFloat(v);
            writeMatrix(out, frame.sensorToXyz1);
            writeMatrix(out, frame.sensorToXyz2);
            out.writeInt(frame.illuminant1); out.writeInt(frame.illuminant2);
            if (frame.lsmGains != null && frame.lsmCols > 0 && frame.lsmRows > 0) {
                out.writeInt(frame.lsmCols);
                out.writeInt(frame.lsmRows);
                for (float g : frame.lsmGains) out.writeFloat(g);
            } else {
                out.writeInt(0);
                out.writeInt(0);
            }

            for (int y = 0; y < height; y++) {
                int row = y * rowStride;
                for (int x = 0; x < width; x++) {
                    int offset = row + x * pixelStride;
                    int sample = offset >= 0 && offset + 1 < source.limit() ? source.getShort(offset) & 0xffff : 0;
                    out.writeShort(sample);
                }
            }
        }
        if (target.exists() && !target.delete()) throw new IOException("Could not replace RAW archive");
        if (!temporary.renameTo(target)) {
            temporary.delete();
            throw new IOException("Could not finish RAW archive");
        }
    }

    static Frame write(Context context, String key, Image image, CameraCharacteristics characteristics,
                       TotalCaptureResult result, int orientation, String aspectRatio) throws IOException {
        Frame frame = createFrame(key, image, characteristics, result, orientation, aspectRatio);
        writeArchive(context, frame);
        return frame;
    }

    static Frame read(Context context, String key) throws IOException {
        File source = file(context, key);
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new InflaterInputStream(new FileInputStream(source))))) {
            int magic = in.readInt();
            int version = in.readInt();
            if (magic != MAGIC || (version != 1 && version != VERSION)) throw new IOException("Unsupported RAW archive");
            Frame frame = new Frame();
            frame.width = in.readInt(); frame.height = in.readInt(); frame.cfa = in.readInt();
            frame.orientation = in.readInt();
            frame.key = in.readUTF();
            frame.aspectRatio = in.readUTF();
            frame.staticWhiteLevel = in.readInt();
            frame.dynamicWhiteLevel = in.readInt();
            frame.exposureTimeNs = in.readLong();
            frame.sensitivityIso = in.readInt();
            frame.aeExposureCompensation = in.readInt();
            for (int i = 0; i < 4; i++) frame.blackLevels[i] = in.readFloat();
            for (int i = 0; i < 4; i++) frame.wbGains[i] = in.readFloat();
            for (int i = 0; i < 3; i++) frame.neutralPoint[i] = in.readFloat();
            frame.sensorToXyz1 = readMatrix(in);
            frame.sensorToXyz2 = readMatrix(in);
            frame.illuminant1 = in.readInt();
            frame.illuminant2 = in.readInt();
            if (version >= 2) {
                frame.lsmCols = in.readInt();
                frame.lsmRows = in.readInt();
                if (frame.lsmCols > 0 && frame.lsmRows > 0 && frame.lsmCols <= 128 && frame.lsmRows <= 128) {
                    int count = frame.lsmCols * frame.lsmRows * 4;
                    frame.lsmGains = new float[count];
                    for (int i = 0; i < count; i++) frame.lsmGains[i] = in.readFloat();
                }
            }
            long bytes = (long) frame.width * frame.height * 2L;
            if (frame.width <= 0 || frame.height <= 0 || bytes > Integer.MAX_VALUE
                    || bytes > Runtime.getRuntime().maxMemory() * .60d) {
                throw new IOException("RAW original is too large to edit on this device");
            }
            frame.rawSamples = new byte[(int) bytes];
            in.readFully(frame.rawSamples);
            frame.rowStride = frame.width * 2;
            frame.pixelStride = 2;
            return frame;
        }
    }

    private static float dynamicBlackFor(float[] blackLevels, int x, int y) {
        return blackLevels[((y & 1) << 1) | (x & 1)];
    }

    private static int colorAt(int x, int y, int cfa) {
        int p = ((y & 1) << 1) | (x & 1);
        if (cfa == CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GRBG) return new int[]{1, 0, 2, 1}[p];
        if (cfa == CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GBRG) return new int[]{1, 2, 0, 1}[p];
        if (cfa == CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_BGGR) return new int[]{2, 1, 1, 0}[p];
        return new int[]{0, 1, 1, 2}[p];
    }

    private static float[] profileMatrix(ColorSpaceTransform forward, ColorSpaceTransform color,
                                          ColorSpaceTransform calibration) {
        float[] calibrationMatrix = matrix(calibration);
        if (calibrationMatrix == null) calibrationMatrix = identity();
        float[] actualToReference = inverse(calibrationMatrix);
        float[] forwardMatrix = matrix(forward);
        if (forwardMatrix != null) return multiply(forwardMatrix, actualToReference);
        float[] colorMatrix = matrix(color);
        if (colorMatrix == null) return null;
        return multiply(inverse(colorMatrix), actualToReference);
    }

    private static float[] matrix(ColorSpaceTransform transform) {
        if (transform == null) return null;
        float[] values = new float[9];
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                Rational value = transform.getElement(col, row);
                values[row * 3 + col] = value.floatValue();
            }
        }
        return values;
    }

    private static void writeMatrix(DataOutputStream out, float[] matrix) throws IOException {
        out.writeBoolean(matrix != null);
        if (matrix != null) for (float value : matrix) out.writeFloat(value);
    }

    private static float[] readMatrix(DataInputStream in) throws IOException {
        if (!in.readBoolean()) return null;
        float[] values = new float[9];
        for (int i = 0; i < 9; i++) values[i] = in.readFloat();
        return values;
    }

    private static float[] identity() { return new float[]{1,0,0, 0,1,0, 0,0,1}; }

    private static float[] multiply(float[] a, float[] b) {
        float[] c = new float[9];
        for (int r = 0; r < 3; r++) for (int col = 0; col < 3; col++)
            c[r * 3 + col] = a[r * 3] * b[col] + a[r * 3 + 1] * b[3 + col] + a[r * 3 + 2] * b[6 + col];
        return c;
    }

    private static float[] inverse(float[] m) {
        float a=m[0], b=m[1], c=m[2], d=m[3], e=m[4], f=m[5], g=m[6], h=m[7], i=m[8];
        float det = a*(e*i-f*h)-b*(d*i-f*g)+c*(d*h-e*g);
        if (Math.abs(det) < 1e-8f) return identity();
        float k = 1f / det;
        return new float[]{(e*i-f*h)*k,(c*h-b*i)*k,(b*f-c*e)*k,
                (f*g-d*i)*k,(a*i-c*g)*k,(c*d-a*f)*k,
                (d*h-e*g)*k,(b*g-a*h)*k,(a*e-b*d)*k};
    }
}
