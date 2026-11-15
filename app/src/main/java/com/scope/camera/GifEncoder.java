package com.scope.camera;

import android.graphics.Bitmap;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * High-performance, self-contained GIF89a multi-frame encoder.
 *
 * Implements:
 * - GIF89a specification with Logical Screen Descriptor & Image Descriptors.
 * - Netscape 2.0 Application Extension for infinite looping.
 * - NeuQuant 256-color neural-network color quantization for smooth photographic gradients.
 * - Variable-length LZW compression sub-block streaming.
 * - Support for both Android Bitmaps and raw ARGB pixel buffers (deterministic JVM testing).
 */
public final class GifEncoder {

    public interface OnProgressListener {
        void onProgress(int currentFrame, int totalFrames, String message);
    }

    private OutputStream out;
    private int width = 0;
    private int height = 0;
    enum State {
        NEW,
        HEADER_WRITTEN,
        WRITING_FRAMES,
        FINISHED
    }

    private int delayCentiseconds = 10; // 100ms = 10 fps default
    private int repeat = 0; // 0 = loop forever
    private int sampleQuality = 10; // 1 = best, 10 = standard fast, 20 = draft
    private State state = State.NEW;
    private int frameCount = 0;
    private OnProgressListener progressListener;
    private int[] framePixelBuf;
    private byte[] indexedPixelBuf;
    private final LzwEncoder lzwEncoder = new LzwEncoder();

    public GifEncoder() {}

    /** Sets the delay between frames in milliseconds. Converted to 1/100s for GIF89a. */
    public void setDelay(int ms) {
        this.delayCentiseconds = Math.max(1, Math.round(ms / 10.0f));
    }

    /** Sets loop count. 0 = loop continuously. -1 = play once. */
    public void setRepeat(int loopCount) {
        this.repeat = loopCount;
    }

    /** Sets the target dimensions for all frames. */
    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    /** Sets NeuQuant color quantization sample rate (1..30). Default 10. */
    public void setQuality(int quality) {
        this.sampleQuality = Math.max(1, Math.min(30, quality));
    }

    public void setProgressListener(OnProgressListener listener) {
        this.progressListener = listener;
    }

    State getState() {
        return state;
    }

    /** Begins GIF stream writing header and screen descriptor. */
    public boolean start(OutputStream os) {
        if (os == null) return false;
        this.out = os;
        this.frameCount = 0;
        try {
            writeString("GIF89a");
            this.state = State.HEADER_WRITTEN;
            return true;
        } catch (IOException e) {
            this.state = State.NEW;
            return false;
        }
    }

    /** Adds a frame from an Android Bitmap. */
    public boolean addFrame(Bitmap bitmap) {
        if (!isAcceptingFrames() || bitmap == null) return false;
        int frameW = width > 0 ? width : bitmap.getWidth();
        int frameH = height > 0 ? height : bitmap.getHeight();

        Bitmap scaled = bitmap;
        boolean needsRecycle = false;
        if (bitmap.getWidth() != frameW || bitmap.getHeight() != frameH) {
            scaled = Bitmap.createScaledBitmap(bitmap, frameW, frameH, true);
            needsRecycle = (scaled != bitmap);
        }

        int numPixels = frameW * frameH;
        if (framePixelBuf == null || framePixelBuf.length < numPixels) {
            framePixelBuf = new int[numPixels];
        }
        scaled.getPixels(framePixelBuf, 0, frameW, 0, 0, frameW, frameH);

        if (needsRecycle) {
            scaled.recycle();
        }

        return addFrame(frameW, frameH, framePixelBuf);
    }

    /** Adds a frame from an ARGB integer pixel array. */
    public boolean addFrame(int frameW, int frameH, int[] pixels) {
        if (!isAcceptingFrames() || pixels == null || frameW <= 0 || frameH <= 0) return false;
        int numPixels = frameW * frameH;
        if (pixels.length < numPixels) return false;
        try {
            if (width <= 0 || height <= 0) {
                width = frameW;
                height = height <= 0 ? frameH : height;
            }

            if (state == State.HEADER_WRITTEN) {
                writeLogicalScreenDescriptor();
                if (repeat >= 0) {
                    writeNetscapeExt();
                }
                state = State.WRITING_FRAMES;
            }

            if (progressListener != null) {
                progressListener.onProgress(frameCount + 1, -1, "Quantizing colors for frame " + (frameCount + 1));
            }

            // Quantize colors using NeuQuant directly from ARGB pixels
            NeuQuant quantizer = new NeuQuant(pixels, numPixels, sampleQuality);
            byte[] colorTable = quantizer.process();
            if (indexedPixelBuf == null || indexedPixelBuf.length < numPixels) {
                indexedPixelBuf = new byte[numPixels];
            }
            quantizer.mapPixels(pixels, numPixels, indexedPixelBuf);

            writeGraphicControlExt();
            writeImageDescriptor(frameW, frameH);
            out.write(colorTable); // 256 * 3 bytes local color table

            if (progressListener != null) {
                progressListener.onProgress(frameCount + 1, -1, "Compressing frame " + (frameCount + 1));
            }

            lzwEncoder.encode(frameW, frameH, indexedPixelBuf, 8, out);

            frameCount++;
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Finishes GIF encoding by writing trailer byte 0x3B and flushing. */
    public boolean finish() {
        if (!isAcceptingFrames()) return false;
        boolean ok = true;
        try {
            out.write(0x3B); // GIF trailer
            out.flush();
        } catch (IOException e) {
            ok = false;
        } finally {
            state = State.FINISHED;
            framePixelBuf = null;
            indexedPixelBuf = null;
        }
        return ok;
    }

    private boolean isAcceptingFrames() {
        return state == State.HEADER_WRITTEN || state == State.WRITING_FRAMES;
    }

    public int getFrameCount() {
        return frameCount;
    }

    private void writeString(String s) throws IOException {
        byte[] bytes = s.getBytes(StandardCharsets.US_ASCII);
        out.write(bytes);
    }

    private void writeShort(int value) throws IOException {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
    }

    private void writeLogicalScreenDescriptor() throws IOException {
        writeShort(width);
        writeShort(height);
        // Packed: Global Color Table Flag (0), Color Resolution (7 = 8 bits/pixel), Sort Flag (0), Size (0)
        out.write(0x70);
        out.write(0x00); // Background color index
        out.write(0x00); // Pixel aspect ratio
    }

    private void writeNetscapeExt() throws IOException {
        out.write(0x21); // Extension Introducer
        out.write(0xFF); // Application Extension
        out.write(0x0B); // Block size 11
        writeString("NETSCAPE2.0");
        out.write(0x03); // Sub-block size 3
        out.write(0x01); // Loop sub-block ID
        writeShort(repeat); // Loop count (0 = infinite)
        out.write(0x00); // Block terminator
    }

    private void writeGraphicControlExt() throws IOException {
        out.write(0x21); // Extension Introducer
        out.write(0xF9); // Graphic Control Label
        out.write(0x04); // Block size 4
        // Packed: Disposal Method = 2 (restore to background), User Input = 0, Transparent Color = 0
        out.write(0x08);
        writeShort(delayCentiseconds);
        out.write(0x00); // Transparent color index
        out.write(0x00); // Block terminator
    }

    private void writeImageDescriptor(int frameW, int frameH) throws IOException {
        out.write(0x2C); // Image Separator
        writeShort(0); // Left
        writeShort(0); // Top
        writeShort(frameW);
        writeShort(frameH);
        // Packed: Local Color Table Flag (1), Interlace (0), Sort (0), Size (7 = 256 colors)
        out.write(0x87);
    }

    // =========================================================================
    // NeuQuant Neural-Net Color Quantization (Anthony Dekker)
    // =========================================================================
    private static final class NeuQuant {
        private static final int NETSIZE = 256;
        private static final int PRIME1 = 499;
        private static final int PRIME2 = 491;
        private static final int PRIME3 = 487;
        private static final int PRIME4 = 503;

        private static final int MAXNETPOS = (NETSIZE - 1);
        private static final int NETBIASSHIFT = 4;
        private static final int NCYCLES = 60; // 60 iterations for fast mobile quantization

        private static final int INTRADIUS = (NETSIZE >> 3);
        private static final int RADIUSBIASSHIFT = 6;
        private static final int RADIUSBIAS = (1 << RADIUSBIASSHIFT);
        private static final int INITRAD = (INTRADIUS * RADIUSBIAS);
        private static final int RADIUSDEC = 30;

        private static final int ALPHABIASSHIFT = 10;
        private static final int INITALPHA = (1 << ALPHABIASSHIFT);
        private static final int GAMMA = 1024;
        private static final int BETA = (GAMMA >> 10);
        private static final int BETASHIFT = 10;
        private static final int BETAGAMMA = (GAMMA << 10);
        private static final int RAD_FACTOR = 256;

        private final int[] pixels;
        private final int numPixels;
        private final int samplefac;

        // Flattened [i * 4 + 0..3] = {B, G, R, originalIndex}
        private final int[] network = new int[NETSIZE * 4];
        private final int[] netindex = new int[256];
        private final int[] bias = new int[NETSIZE];
        private final int[] freq = new int[NETSIZE];
        private final int[] radpower = new int[NETSIZE];

        NeuQuant(int[] pixels, int numPixels, int sample) {
            this.pixels = pixels;
            this.numPixels = numPixels;
            this.samplefac = sample;

            int initFreq = INITALPHA / NETSIZE;
            for (int i = 0; i < NETSIZE; i++) {
                int base = i << 2;
                int v = (i << (NETBIASSHIFT + 8)) / NETSIZE;
                network[base] = v;
                network[base + 1] = v;
                network[base + 2] = v;
                freq[i] = initFreq;
                bias[i] = 0;
            }
        }

        byte[] process() {
            learn();
            unbiasnet();
            inxbuild();
            return colorMap();
        }

        byte[] colorMap() {
            byte[] map = new byte[3 * NETSIZE];
            int[] index = new int[NETSIZE];
            for (int i = 0; i < NETSIZE; i++) {
                index[network[(i << 2) + 3]] = i;
            }
            int k = 0;
            for (int i = 0; i < NETSIZE; i++) {
                int j = index[i] << 2;
                map[k++] = (byte) network[j + 2]; // Red
                map[k++] = (byte) network[j + 1]; // Green
                map[k++] = (byte) network[j];     // Blue
            }
            return map;
        }

        void mapPixels(int[] pixels, int count, byte[] mapped) {
            if (count >= 65536) {
                int chunks = 8;
                int chunkSize = (count + chunks - 1) / chunks;
                java.util.stream.IntStream.range(0, chunks).parallel().forEach(c -> {
                    int start = c * chunkSize;
                    int end = Math.min(count, start + chunkSize);
                    for (int i = start; i < end; i++) {
                        int p = pixels[i];
                        mapped[i] = (byte) inxsearch(p & 0xFF, (p >> 8) & 0xFF, (p >> 16) & 0xFF);
                    }
                });
            } else {
                for (int i = 0; i < count; i++) {
                    int p = pixels[i];
                    mapped[i] = (byte) inxsearch(p & 0xFF, (p >> 8) & 0xFF, (p >> 16) & 0xFF);
                }
            }
        }

        private void inxbuild() {
            int previouscol = 0;
            int startpos = 0;
            for (int i = 0; i < NETSIZE; i++) {
                int pBase = i << 2;
                int smallpos = i;
                int smallval = network[pBase + 1]; // Index on Green
                for (int j = i + 1; j < NETSIZE; j++) {
                    int qVal = network[(j << 2) + 1];
                    if (qVal < smallval) {
                        smallpos = j;
                        smallval = qVal;
                    }
                }
                if (i != smallpos) {
                    int qBase = smallpos << 2;
                    int tmp = network[qBase]; network[qBase] = network[pBase]; network[pBase] = tmp;
                    tmp = network[qBase + 1]; network[qBase + 1] = network[pBase + 1]; network[pBase + 1] = tmp;
                    tmp = network[qBase + 2]; network[qBase + 2] = network[pBase + 2]; network[pBase + 2] = tmp;
                    tmp = network[qBase + 3]; network[qBase + 3] = network[pBase + 3]; network[pBase + 3] = tmp;
                }
                if (smallval != previouscol) {
                    netindex[previouscol] = (startpos + i) >> 1;
                    for (int j = previouscol + 1; j < smallval; j++) netindex[j] = i;
                    previouscol = smallval;
                    startpos = i;
                }
            }
            netindex[previouscol] = (startpos + MAXNETPOS) >> 1;
            for (int j = previouscol + 1; j < 256; j++) netindex[j] = MAXNETPOS;
        }

        private int inxsearch(int b, int g, int r) {
            int bestd = 1000;
            int best = -1;
            int i = netindex[g];
            int j = i - 1;
            int[] net = this.network;

            while ((i < NETSIZE) || (j >= 0)) {
                if (i < NETSIZE) {
                    int base = i << 2;
                    int dist = net[base + 1] - g;
                    if (dist >= bestd) i = NETSIZE;
                    else {
                        i++;
                        if (dist < 0) dist = -dist;
                        int a = net[base] - b; if (a < 0) a = -a;
                        dist += a;
                        if (dist < bestd) {
                            a = net[base + 2] - r; if (a < 0) a = -a;
                            dist += a;
                            if (dist < bestd) {
                                bestd = dist;
                                best = net[base + 3];
                            }
                        }
                    }
                }
                if (j >= 0) {
                    int base = j << 2;
                    int dist = g - net[base + 1];
                    if (dist >= bestd) j = -1;
                    else {
                        j--;
                        if (dist < 0) dist = -dist;
                        int a = net[base] - b; if (a < 0) a = -a;
                        dist += a;
                        if (dist < bestd) {
                            a = net[base + 2] - r; if (a < 0) a = -a;
                            dist += a;
                            if (dist < bestd) {
                                bestd = dist;
                                best = net[base + 3];
                            }
                        }
                    }
                }
            }
            return best;
        }

        private void learn() {
            int[] p = this.pixels;
            int lim = this.numPixels;
            int alphadec = 30 + ((samplefac - 1) / 3);
            int pix = 0;
            int samplepixels = lim / samplefac;
            int delta = samplepixels / NCYCLES;
            if (delta == 0) delta = 1;
            int alpha = INITALPHA;
            int radius = INITRAD;

            int rad = radius >> RADIUSBIASSHIFT;
            if (rad <= 1) rad = 0;
            if (rad > 0) {
                for (int i = 0; i <= rad && i < radpower.length; i++) {
                    radpower[i] = alpha * (((rad * rad - i * i) * RAD_FACTOR) / (rad * rad));
                }
            }

            int step;
            if (lim < PRIME4) {
                step = 1;
            } else if ((lim % PRIME1) != 0) {
                step = PRIME1;
            } else if ((lim % PRIME2) != 0) {
                step = PRIME2;
            } else if ((lim % PRIME3) != 0) {
                step = PRIME3;
            } else {
                step = PRIME4;
            }

            int i = 0;
            while (i < samplepixels) {
                int argb = p[pix];
                int b = (argb & 0xFF) << NETBIASSHIFT;
                int g = ((argb >> 8) & 0xFF) << NETBIASSHIFT;
                int r = ((argb >> 16) & 0xFF) << NETBIASSHIFT;
                int j = contest(b, g, r);

                altersingle(alpha, j, b, g, r);
                if (rad != 0) alterneigh(rad, j, b, g, r);

                pix += step;
                if (pix >= lim) pix -= lim;

                i++;
                if (i % delta == 0) {
                    alpha -= alpha / alphadec;
                    radius -= radius / RADIUSDEC;
                    rad = radius >> RADIUSBIASSHIFT;
                    if (rad <= 1) rad = 0;
                    if (rad > 0) {
                        for (int k = 0; k <= rad && k < radpower.length; k++) {
                            radpower[k] = alpha * (((rad * rad - k * k) * RAD_FACTOR) / (rad * rad));
                        }
                    }
                }
            }
        }

        private void altersingle(int alpha, int i, int b, int g, int r) {
            int base = i << 2;
            network[base] -= (alpha * (network[base] - b)) / INITALPHA;
            network[base + 1] -= (alpha * (network[base + 1] - g)) / INITALPHA;
            network[base + 2] -= (alpha * (network[base + 2] - r)) / INITALPHA;
        }

        private void alterneigh(int rad, int i, int b, int g, int r) {
            int lo = Math.max(0, i - rad);
            int hi = Math.min(NETSIZE, i + rad);
            int j = i + 1;
            int k = i - 1;
            int q = 1;
            int[] net = this.network;
            while ((j < hi) || (k >= lo)) {
                int a = (q < radpower.length) ? radpower[q++] : 0;
                if (j < hi) {
                    int base = (j++) << 2;
                    net[base] -= (a * (net[base] - b)) / (INITALPHA * RAD_FACTOR);
                    net[base + 1] -= (a * (net[base + 1] - g)) / (INITALPHA * RAD_FACTOR);
                    net[base + 2] -= (a * (net[base + 2] - r)) / (INITALPHA * RAD_FACTOR);
                }
                if (k >= lo) {
                    int base = (k--) << 2;
                    net[base] -= (a * (net[base] - b)) / (INITALPHA * RAD_FACTOR);
                    net[base + 1] -= (a * (net[base + 1] - g)) / (INITALPHA * RAD_FACTOR);
                    net[base + 2] -= (a * (net[base + 2] - r)) / (INITALPHA * RAD_FACTOR);
                }
            }
        }

        private int contest(int b, int g, int r) {
            int bestd = Integer.MAX_VALUE;
            int bestbiasd = bestd;
            int bestpos = -1;
            int bestbiaspos = bestpos;
            int[] net = this.network;

            for (int i = 0; i < NETSIZE; i++) {
                int base = i << 2;
                int db = net[base] - b; if (db < 0) db = -db;
                int dg = net[base + 1] - g; if (dg < 0) dg = -dg;
                int dr = net[base + 2] - r; if (dr < 0) dr = -dr;
                int dist = db + dg + dr;
                if (dist < bestd) {
                    bestd = dist;
                    bestpos = i;
                }
                int biasdist = dist - ((bias[i]) >> (BETASHIFT - NETBIASSHIFT));
                if (biasdist < bestbiasd) {
                    bestbiasd = biasdist;
                    bestbiaspos = i;
                }
                int betafreq = (freq[i] >> BETASHIFT);
                freq[i] -= betafreq;
                bias[i] += (betafreq << BETASHIFT);
            }
            freq[bestpos] += BETA;
            bias[bestpos] -= BETAGAMMA;
            return bestbiaspos;
        }

        private void unbiasnet() {
            for (int i = 0; i < NETSIZE; i++) {
                int base = i << 2;
                network[base] >>= NETBIASSHIFT;
                network[base + 1] >>= NETBIASSHIFT;
                network[base + 2] >>= NETBIASSHIFT;
                network[base + 3] = i; // Save original position
            }
        }
    }

    // =========================================================================
    // LZW Variable-Length Code Compression
    // =========================================================================
    private static final class LzwEncoder {
        private static final int EOF = -1;
        private static final int BITS = 12;
        private static final int HSIZE = 5003; // Prime hash table size > 4096

        private byte[] pixAry;
        private int initCodeSize;

        private int remaining;
        private int curPixel;

        private int n_bits;
        private final int maxbits = BITS;
        private int maxcode;
        private final int maxmaxcode = 1 << BITS;

        private final int[] htab = new int[HSIZE];
        private final int[] codetab = new int[HSIZE];
        private int free_ent = 0;
        private boolean clear_flg = false;

        private int g_init_bits;
        private int ClearCode;
        private int EOFCode;

        private int cur_accum = 0;
        private int cur_bits = 0;

        private static final int[] MASKS = {
                0x0000, 0x0001, 0x0003, 0x0007, 0x000F,
                0x001F, 0x003F, 0x007F, 0x00FF, 0x01FF,
                0x03FF, 0x07FF, 0x0FFF, 0x1FFF, 0x3FFF,
                0x7FFF, 0xFFFF
        };

        private int a_count;
        private final byte[] accum = new byte[256];

        void encode(int width, int height, byte[] pixels, int colorDepth, OutputStream os) throws IOException {
            this.pixAry = pixels;
            this.initCodeSize = Math.max(2, colorDepth);
            this.remaining = width * height;
            this.curPixel = 0;
            this.cur_accum = 0;
            this.cur_bits = 0;
            this.clear_flg = false;
            this.a_count = 0;
            os.write(initCodeSize);
            compress(initCodeSize + 1, os);
            os.write(0); // Sub-block terminator
            this.pixAry = null;
        }

        private void compress(int init_bits, OutputStream outs) throws IOException {
            g_init_bits = init_bits;
            clear_flg = false;
            n_bits = g_init_bits;
            maxcode = MAXCODE(n_bits);

            ClearCode = 1 << (init_bits - 1);
            EOFCode = ClearCode + 1;
            free_ent = ClearCode + 2;

            a_count = 0;
            int ent = nextPixel();

            int hshift = 0;
            for (int fcode = HSIZE; fcode < 65536; fcode *= 2) ++hshift;
            hshift = 8 - hshift;

            int hsize_reg = HSIZE;
            cl_hash(hsize_reg);
            output(ClearCode, outs);

            int c;
            outer_loop:
            while ((c = nextPixel()) != EOF) {
                int fcode = (c << maxbits) + ent;
                int i = (c << hshift) ^ ent;

                if (htab[i] == fcode) {
                    ent = codetab[i];
                    continue;
                } else if (htab[i] >= 0) {
                    int disp = hsize_reg - i;
                    if (i == 0) disp = 1;
                    do {
                        if ((i -= disp) < 0) i += hsize_reg;
                        if (htab[i] == fcode) {
                            ent = codetab[i];
                            continue outer_loop;
                        }
                    } while (htab[i] >= 0);
                }
                output(ent, outs);
                ent = c;
                if (free_ent < maxmaxcode) {
                    codetab[i] = free_ent++;
                    htab[i] = fcode;
                } else {
                    cl_block(outs);
                }
            }
            output(ent, outs);
            output(EOFCode, outs);
        }

        private int MAXCODE(int n_bits) {
            return (1 << n_bits) - 1;
        }

        private int nextPixel() {
            if (remaining == 0) return EOF;
            --remaining;
            return pixAry[curPixel++] & 0xFF;
        }

        private void output(int code, OutputStream outs) throws IOException {
            cur_accum &= MASKS[cur_bits];
            if (cur_bits > 0) cur_accum |= (code << cur_bits);
            else cur_accum = code;

            cur_bits += n_bits;

            while (cur_bits >= 8) {
                char_out((byte) (cur_accum & 0xFF), outs);
                cur_accum >>= 8;
                cur_bits -= 8;
            }

            if (free_ent > maxcode || clear_flg) {
                if (clear_flg) {
                    maxcode = MAXCODE(n_bits = g_init_bits);
                    clear_flg = false;
                } else {
                    ++n_bits;
                    if (n_bits == maxbits) maxcode = maxmaxcode;
                    else maxcode = MAXCODE(n_bits);
                }
            }

            if (code == EOFCode) {
                while (cur_bits > 0) {
                    char_out((byte) (cur_accum & 0xFF), outs);
                    cur_accum >>= 8;
                    cur_bits -= 8;
                }
                flush_char(outs);
            }
        }

        private void cl_block(OutputStream outs) throws IOException {
            cl_hash(HSIZE);
            free_ent = ClearCode + 2;
            clear_flg = true;
            output(ClearCode, outs);
        }

        private void cl_hash(int hsize) {
            java.util.Arrays.fill(htab, 0, hsize, -1);
        }

        private void char_out(byte c, OutputStream outs) throws IOException {
            accum[a_count++] = c;
            if (a_count >= 254) flush_char(outs);
        }

        private void flush_char(OutputStream outs) throws IOException {
            if (a_count > 0) {
                outs.write(a_count);
                outs.write(accum, 0, a_count);
                a_count = 0;
            }
        }
    }
}
