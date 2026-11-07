package com.scope.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.hardware.camera2.CameraCharacteristics;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Scene-linear RAW development followed by the authored Lika Lens display rendering. */
final class RawDeveloper {
    private static final int MAX_RENDER_PIXELS = 50_000_000;
    private static final int[] SRGB_LUT = buildSrgbLut();
    private RawDeveloper() { }

    static final class Look {
        String brain = "LIKA / FILM";
        String grainStructure = "Fine · 35 mm";
        int whiteBalanceKelvin;
        float exposureEv;
        float warmth;
        float contrast = 1.12f;
        float pivot = .20f;
        float saturation = .98f;
        float colorRollOff = .72f;
        float highlights = .68f;
        float shadows = .36f;
        float grain = .22f;
        float halation = .16f;
        float sharpening = .06f;
        float chromaNr = .15f;
        float vignette = .10f;
        String aspectRatio;
        boolean flipHorizontal;

        static Look fromPreferences(Context context) {
            Look look = new Look();
            look.brain = AppSettings.brain(context);
            look.whiteBalanceKelvin = AppSettings.getInt(context, AppSettings.WHITE_BALANCE, 0);
            look.exposureEv = AppSettings.getInt(context, AppSettings.EXPOSURE, AppSettings.DEFAULT_EXPOSURE) / 10f;
            look.warmth = (AppSettings.getInt(context, AppSettings.WARMTH, AppSettings.DEFAULT_WARMTH) - 50) / 50f;
            look.contrast = AppSettings.getInt(context, AppSettings.CONTRAST, AppSettings.DEFAULT_CONTRAST) / 100f;
            look.pivot = AppSettings.getInt(context, AppSettings.PIVOT, AppSettings.DEFAULT_PIVOT) / 100f;
            look.saturation = AppSettings.getInt(context, AppSettings.SATURATION, AppSettings.DEFAULT_SATURATION) / 100f;
            look.colorRollOff = AppSettings.getInt(context, AppSettings.COLOR_ROLLOFF, AppSettings.DEFAULT_COLOR_ROLLOFF) / 100f;
            look.highlights = AppSettings.getInt(context, AppSettings.HIGHLIGHTS, AppSettings.DEFAULT_HIGHLIGHTS) / 100f;
            look.shadows = AppSettings.getInt(context, AppSettings.SHADOWS, AppSettings.DEFAULT_SHADOWS) / 100f;
            look.grain = AppSettings.getInt(context, AppSettings.GRAIN, AppSettings.DEFAULT_GRAIN) / 100f;
            look.halation = AppSettings.getInt(context, AppSettings.HALATION, AppSettings.DEFAULT_HALATION) / 100f;
            look.sharpening = AppSettings.getInt(context, AppSettings.SHARPEN, AppSettings.DEFAULT_SHARPEN) / 100f;
            look.chromaNr = AppSettings.getInt(context, AppSettings.COLOR_NR, AppSettings.DEFAULT_COLOR_NR) / 100f;
            look.vignette = AppSettings.getInt(context, AppSettings.VIGNETTE, AppSettings.DEFAULT_VIGNETTE) / 100f;
            look.grainStructure = AppSettings.get(context, "grain_structure", "Fine · 35 mm");
            look.aspectRatio = AppSettings.get(context, AppSettings.ASPECT_RATIO, "4:3");
            return look;
        }

        Look copy() {
            Look c = new Look();
            c.brain = brain; c.grainStructure = grainStructure;
            c.whiteBalanceKelvin = whiteBalanceKelvin; c.exposureEv = exposureEv; c.warmth = warmth;
            c.contrast = contrast; c.pivot = pivot; c.saturation = saturation; c.colorRollOff = colorRollOff;
            c.highlights = highlights; c.shadows = shadows; c.grain = grain;
            c.halation = halation; c.sharpening = sharpening; c.chromaNr = chromaNr;
            c.vignette = vignette;
            c.aspectRatio = aspectRatio;
            c.flipHorizontal = flipHorizontal;
            return c;
        }
    }

    /** maxDimension=0 renders at the sensor's available resolution, within the safety pixel budget. */
    static Bitmap develop(RawArchive.Frame frame, Look look, int maxDimension) {
        if (frame == null || (frame.rawSamples == null && frame.captureBuffer == null)) return null;
        int width = frame.width, height = frame.height;
        int orientation = normalizeOrientation(frame.orientation);
        int displayWidth = orientation == 90 || orientation == 270 ? height : width;
        int displayHeight = orientation == 90 || orientation == 270 ? width : height;
        String aspect = look.aspectRatio != null ? look.aspectRatio : frame.aspectRatio;
        float[] crop = cropRect(displayWidth, displayHeight, aspect);
        float cropWidth = crop[2], cropHeight = crop[3];
        long cropPixels = Math.max(1L, (long) (cropWidth * cropHeight));
        int step = maxDimension > 0
                ? Math.max(1, (int) Math.ceil(Math.max(cropWidth, cropHeight) / maxDimension))
                : Math.max(1, (int) Math.ceil(Math.sqrt(cropPixels / (double) renderPixelBudget(frame))));
        int outW = Math.max(1, (int) Math.ceil(cropWidth / step));
        int outH = Math.max(1, (int) Math.ceil(cropHeight / step));
        ByteBuffer raw = frame.rawBuffer();
        float[] cameraToXyz = profileFor(frame, look.whiteBalanceKelvin);
        float white = frame.dynamicWhiteLevel > 0 ? frame.dynamicWhiteLevel : frame.staticWhiteLevel;
        if (white <= 0) white = 65535f;
        float[] gains = whiteBalanceGains(frame, look.whiteBalanceKelvin);
        float exposureScale = (float) Math.pow(2.0, look.exposureEv);
        Bitmap bitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888);
        int[] allPixels = new int[outW * outH];
        int seed = frame.key == null ? width * 31 + height : frame.key.hashCode();

        java.util.stream.IntStream.range(0, outH).parallel().forEach(oy -> {
            float dy = Math.min(crop[1] + cropHeight - .5f,
                    crop[1] + (oy + .5f) * cropHeight / outH);
            int rowOffset = oy * outW;
            for (int ox = 0; ox < outW; ox++) {
                float effectiveOx = look.flipHorizontal ? (outW - 1 - ox) : ox;
                float dx = Math.min(crop[0] + cropWidth - .5f,
                        crop[0] + (effectiveOx + .5f) * cropWidth / outW);
                int sx, sy;
                switch (orientation) {
                    case 90: sx = Math.min(width - 1, (int) dy); sy = Math.max(0, Math.min(height - 1, height - 1 - (int) dx)); break;
                    case 180: sx = Math.max(0, Math.min(width - 1, width - 1 - (int) dx)); sy = Math.max(0, Math.min(height - 1, height - 1 - (int) dy)); break;
                    case 270: sx = Math.max(0, Math.min(width - 1, width - 1 - (int) dy)); sy = Math.min(height - 1, (int) dx); break;
                    default: sx = Math.min(width - 1, (int) dx); sy = Math.min(height - 1, (int) dy); break;
                }
                float r = interpolate(raw, frame, white, sx, sy, 0);
                float g = interpolate(raw, frame, white, sx, sy, 1);
                float b = interpolate(raw, frame, white, sx, sy, 2);
                r *= gains[0];
                g *= (gains[1] + gains[2]) * .5f;
                b *= gains[3];
                r *= exposureScale; g *= exposureScale; b *= exposureScale;

                float x = cameraToXyz[0] * r + cameraToXyz[1] * g + cameraToXyz[2] * b;
                float y = cameraToXyz[3] * r + cameraToXyz[4] * g + cameraToXyz[5] * b;
                float z = cameraToXyz[6] * r + cameraToXyz[7] * g + cameraToXyz[8] * b;
                // XYZ D50 to linear sRGB. The camera profile is kept separate from the authored look.
                float lr = 3.1339f * x - 1.6169f * y - .4906f * z;
                float lg = -.9785f * x + 1.9160f * y + .0333f * z;
                float lb = .0720f * x - .2290f * y + 1.4057f * z;
                int color = renderLook(lr, lg, lb, look, ox, oy, seed, frame);
                if (look.vignette > 0f) {
                    float nx = outW <= 1 ? 0f : ox / (float) (outW - 1) * 2f - 1f;
                    float ny = outH <= 1 ? 0f : oy / (float) (outH - 1) * 2f - 1f;
                    float falloff = Math.max(.72f, 1f - look.vignette * .18f * (nx * nx + ny * ny));
                    color = scaleColor(color, falloff);
                }
                allPixels[rowOffset + ox] = color;
            }
        });
        bitmap.setPixels(allPixels, 0, outW, 0, 0, outW, outH);

        if (look.chromaNr > 0f) applyChromaDenoise(bitmap, look.chromaNr);
        if (look.halation > 0f) applyHalation(bitmap, look.halation, aspect);
        if (look.sharpening > 0f) applySharpen(bitmap, Math.min(.14f, look.sharpening * .14f));
        return bitmap;
    }

    /** JPEG fallback keeps the file usable, but cannot undo the phone's baked image processing. */
    static Bitmap developJpeg(byte[] bytes, Context context) {
        Bitmap source = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        if (source == null) return null;
        int maxSide = 3000;
        float scale = Math.min(1f, maxSide / (float) Math.max(source.getWidth(), source.getHeight()));
        Bitmap bitmap = scale < 1 ? Bitmap.createScaledBitmap(source,
                Math.max(1, Math.round(source.getWidth() * scale)), Math.max(1, Math.round(source.getHeight() * scale)), true) : source;
        if (bitmap != source) source.recycle();
        Look look = Look.fromPreferences(context);
        float exposureScale = (float) Math.pow(2.0, look.exposureEv);
        float wbRatio = look.whiteBalanceKelvin <= 0 ? 1f : clamp(look.whiteBalanceKelvin / 5600f, .55f, 1.45f);
        float wbShift = (float) Math.sqrt(wbRatio);
        int w = bitmap.getWidth(), h = bitmap.getHeight();
        int[] pixels = new int[w * h];
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
        java.util.stream.IntStream.range(0, h).parallel().forEach(y -> {
            int rowOffset = y * w;
            for (int x = 0; x < w; x++) {
                int c = pixels[rowOffset + x];
                float r = srgbToLinear(Color.red(c) / 255f) * exposureScale * wbShift;
                float g = srgbToLinear(Color.green(c) / 255f) * exposureScale;
                float b = srgbToLinear(Color.blue(c) / 255f) * exposureScale / wbShift;
                int rendered = renderLook(r, g, b, look, x, y, 0x53434f50, null);
                if (look.vignette > 0f) {
                    float nx = w <= 1 ? 0f : x / (float) (w - 1) * 2f - 1f;
                    float ny = h <= 1 ? 0f : y / (float) (h - 1) * 2f - 1f;
                    float falloff = Math.max(.72f, 1f - look.vignette * .18f * (nx * nx + ny * ny));
                    rendered = scaleColor(rendered, falloff);
                }
                pixels[rowOffset + x] = rendered;
            }
        });
        Bitmap result = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888);
        bitmap.recycle();
        if (look.chromaNr > 0f) applyChromaDenoise(result, look.chromaNr);
        if (look.halation > 0f) applyHalation(result, look.halation, look.aspectRatio);
        if (look.sharpening > 0f) applySharpen(result, Math.min(.14f, look.sharpening * .14f));
        return result;
    }

    private static float interpolate(ByteBuffer raw, RawArchive.Frame frame, float white,
                                     int x, int y, int wanted) {
        int site = colorAt(x, y, frame.cfa);
        if (site == wanted) return rawValue(raw, frame, white, x, y);
        if (wanted == 1 && site != 1) {
            float gh = directionalGreen(raw, frame, white, x, y, true);
            float gv = directionalGreen(raw, frame, white, x, y, false);
            if (x == 0 || x == frame.width - 1 || y == 0 || y == frame.height - 1) return (gh + gv) * .5f;
            float ghGradient = Math.abs(rawValue(raw, frame, white, x - 1, y) - rawValue(raw, frame, white, x + 1, y));
            float gvGradient = Math.abs(rawValue(raw, frame, white, x, y - 1) - rawValue(raw, frame, white, x, y + 1));
            return ghGradient <= gvGradient ? gh : gv;
        }
        float sum = 0f, weights = 0f;
        for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
            int nx = x + dx, ny = y + dy;
            if ((dx == 0 && dy == 0) || nx < 0 || ny < 0 || nx >= frame.width || ny >= frame.height
                    || colorAt(nx, ny, frame.cfa) != wanted) continue;
            float distanceWeight = (dx == 0 || dy == 0) ? 1f : .72f;
            sum += rawValue(raw, frame, white, nx, ny) * distanceWeight;
            weights += distanceWeight;
        }
        if (weights > 0f) return sum / weights;
        return rawValue(raw, frame, white, x, y);
    }

    private static float directionalGreen(ByteBuffer raw, RawArchive.Frame frame, float white,
                                           int x, int y, boolean horizontal) {
        float sum = 0f;
        int count = 0;
        for (int direction = -1; direction <= 1; direction += 2) {
            int nx = horizontal ? x + direction : x;
            int ny = horizontal ? y : y + direction;
            if (nx >= 0 && ny >= 0 && nx < frame.width && ny < frame.height
                    && colorAt(nx, ny, frame.cfa) == 1) {
                sum += rawValue(raw, frame, white, nx, ny);
                count++;
            }
        }
        return count == 0 ? rawValue(raw, frame, white, x, y) : sum / count;
    }

    private static float rawValue(ByteBuffer raw, RawArchive.Frame frame, float white, int x, int y) {
        if (x < 0 || y < 0 || x >= frame.width || y >= frame.height) return 0f;
        int offset = frame.rawSamples != null ? (y * frame.width + x) * 2
                : y * frame.rowStride + x * frame.pixelStride;
        if (offset < 0 || offset + 1 >= raw.limit()) return 0f;
        int sample = raw.getShort(offset) & 0xffff;
        float black = frame.blackLevels[(y & 1) * 2 + (x & 1)];
        return clamp((sample - black) / Math.max(1f, white - black), 0f, 4f);
    }

    private static float[] whiteBalanceGains(RawArchive.Frame frame, int kelvin) {
        float[] gains = frame.wbGains == null ? new float[]{1f, 1f, 1f, 1f} : frame.wbGains.clone();
        if (kelvin > 0) {
            float ratio = clamp(kelvin / 5600f, .55f, 1.45f);
            float tintShift = (float) Math.pow(ratio, .5);
            gains[0] *= tintShift;
            gains[3] /= tintShift;
        }
        float green = Math.max(.01f, (gains[1] + gains[2]) * .5f);
        float normal = 1f / green;
        for (int i = 0; i < gains.length; i++) gains[i] = clamp(gains[i] * normal, .25f, 4f);
        return gains;
    }

    private static float[] profileFor(RawArchive.Frame frame, int kelvin) {
        float[] first = frame.sensorToXyz1;
        float[] second = frame.sensorToXyz2;
        if (first == null && second == null) {
            return new float[]{1.75f,-.48f,-.20f, -.22f,1.42f,-.20f, .03f,-.53f,1.50f};
        }
        if (first == null) return second;
        if (second == null || frame.illuminant1 == 0 || frame.illuminant2 == 0 || frame.illuminant1 == frame.illuminant2) return first;
        float a = illuminantKelvin(frame.illuminant1), b = illuminantKelvin(frame.illuminant2);
        float t;
        if (kelvin > 0) {
            t = clamp((kelvin - a) / (b - a), 0f, 1f);
        } else if (frame.neutralPoint != null && frame.neutralPoint.length >= 3
                && (frame.neutralPoint[0] + frame.neutralPoint[1] + frame.neutralPoint[2]) > .0001f) {
            // Choose the interpolation whose evaluated neutral is closest to the D50 display-prep white.
            float best = Float.MAX_VALUE, bestT = 0f;
            for (int n = 0; n <= 100; n++) {
                float candidate = n / 100f;
                float[] m = blend(first, second, candidate);
                float x = m[0]*frame.neutralPoint[0] + m[1]*frame.neutralPoint[1] + m[2]*frame.neutralPoint[2];
                float y = m[3]*frame.neutralPoint[0] + m[4]*frame.neutralPoint[1] + m[5]*frame.neutralPoint[2];
                float z = m[6]*frame.neutralPoint[0] + m[7]*frame.neutralPoint[1] + m[8]*frame.neutralPoint[2];
                float sum = x + y + z;
                if (sum <= .0001f) continue;
                float dx = x/sum - .3457f, dy = y/sum - .3585f;
                float error = dx*dx + dy*dy;
                if (error < best) { best = error; bestT = candidate; }
            }
            t = bestT;
        } else {
            t = clamp((5600f - a) / (b - a), 0f, 1f);
        }
        return blend(first, second, t);
    }

    private static float[] blend(float[] first, float[] second, float t) {
        float[] blend = new float[9];
        for (int i = 0; i < 9; i++) blend[i] = first[i] * (1f - t) + second[i] * t;
        return blend;
    }

    private static int renderPixelBudget(RawArchive.Frame frame) {
        long heap = Runtime.getRuntime().maxMemory();
        long rawBytes = frame.rawSamples == null ? 0L : frame.rawSamples.length;
        long workingBytes = Math.max(0L, (long) (heap * .75d) - rawBytes - 32L * 1024L * 1024L);
        long pixels = workingBytes / 5L;
        return (int) Math.max(1_000_000L, Math.min(MAX_RENDER_PIXELS, pixels));
    }

    private static float illuminantKelvin(int illuminant) {
        switch (illuminant) {
            case 17: return 2856f;
            case 18: return 4874f;
            case 19: return 6774f;
            case 20: return 5500f;
            case 21: return 5500f;
            case 22: return 6500f;
            case 23: return 7500f;
            case 24: return 5000f;
            case 3: return 3200f;
            case 4: return 4000f;
            case 10: return 6500f;
            case 11: return 7500f;
            default: return 5600f;
        }
    }

    private static int colorAt(int x, int y, int cfa) {
        int parity = ((y & 1) << 1) | (x & 1);
        switch (cfa) {
            case CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GRBG:
                return parity == 0 || parity == 3 ? 1 : parity == 1 ? 0 : 2;
            case CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GBRG:
                return parity == 0 || parity == 3 ? 1 : parity == 1 ? 2 : 0;
            case CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_BGGR:
                return parity == 0 ? 2 : parity == 3 ? 0 : 1;
            default:
                return parity == 0 ? 0 : parity == 3 ? 2 : 1;
        }
    }

    /** A coupled display transform: tonal response, hue, chroma and gamut are shaped together. */
    private static int renderLook(float r, float g, float b, Look look, int x, int y, int seed) {
        return renderLook(r, g, b, look, x, y, seed, null);
    }

    private static int renderLook(float r, float g, float b, Look look, int x, int y, int seed, RawArchive.Frame frame) {
        if (look.brain.startsWith("MONO")) {
            float luma = Math.max(0f, .2126f * r + .7152f * g + .0722f * b);
            r = g = b = luma;
        }
        float oldY = Math.max(.00001f, .2126f * r + .7152f * g + .0722f * b);
        float contrasted = Math.max(0f, (oldY - look.pivot) * look.contrast + look.pivot);
        float shadowWeight = 1f - smoothstep(.02f, .44f, contrasted);
        contrasted *= 1f - clamp(look.shadows, 0f, 1f) * .22f * shadowWeight;
        float shoulder = clamp(look.highlights, 0f, 1f);
        if (contrasted > .68f) {
            float excess = contrasted - .68f;
            contrasted = .68f + excess / (1f + excess * (1.8f + shoulder * 5f));
        }
        float ratio = contrasted / oldY;
        r *= ratio; g *= ratio; b *= ratio;

        r = Math.max(0f, r); g = Math.max(0f, g); b = Math.max(0f, b);
        float lRoot = (float) Math.cbrt(.41222147f*r + .53633254f*g + .05144599f*b);
        float mRoot = (float) Math.cbrt(.21190350f*r + .68069955f*g + .10739696f*b);
        float sRoot = (float) Math.cbrt(.08830246f*r + .28171884f*g + .62997870f*b);
        float lightness = clamp(.21045426f*lRoot + .79361779f*mRoot - .00407205f*sRoot, 0f, 1.25f);
        float a = 1.97799850f*lRoot - 2.42859221f*mRoot + .45059371f*sRoot;
        float bb = .02590404f*lRoot + .78277177f*mRoot - .80867577f*sRoot;
        float highlightWeight = smoothstep(.62f, 1f, lightness);
        shadowWeight = 1f - smoothstep(.12f, .50f, lightness);

        // Subtractive highlight roll-off: desaturate towards highlight source as lightness approaches 1.0
        float highlightDesat = 1f - .35f * smoothstep(.70f, 1.0f, lightness);
        float chromaScale = clamp(look.saturation, 0f, 1.6f) * (1f - .13f * highlightWeight) * highlightDesat;
        a *= chromaScale;
        bb *= chromaScale;
        float chroma = (float) Math.sqrt(a * a + bb * bb);
        float rollOff = clamp(look.colorRollOff, 0f, 1f);
        if (rollOff > 0f && chroma > .13f) {
            float excess = chroma - .13f;
            float compressed = .13f + excess / (1f + excess * (2f + rollOff * 5.5f));
            float scale = compressed / chroma;
            a *= scale;
            bb *= scale;
        }
        if (!look.brain.startsWith("MONO")) {
            // Adapt base warmth push to ambient illumination to avoid fluorescent/indoor green-yellow tint
            float ambientWarmthFactor = 1.0f;
            if (look.whiteBalanceKelvin > 0 && look.whiteBalanceKelvin < 4000) {
                ambientWarmthFactor = clamp(0.5f + 0.5f * (look.whiteBalanceKelvin - 2800f) / 1200f, 0.35f, 1.0f);
            } else if (frame != null && frame.wbGains != null && frame.wbGains.length >= 4) {
                float greenGain = (frame.wbGains[1] + frame.wbGains[2]) * .5f;
                float redBlueGain = (frame.wbGains[0] + frame.wbGains[3]) * .5f;
                if (redBlueGain > 0.01f && (greenGain / redBlueGain) > 1.12f) {
                    ambientWarmthFactor = 0.65f;
                }
            }
            float baseWarmth = look.brain.startsWith("NEUTRAL") ? 0f : (.42f * ambientWarmthFactor);
            float warmth = clamp(look.warmth + baseWarmth, -1f, 1f);
            a += warmth * (.006f + .004f * shadowWeight + .002f * highlightWeight);
            bb += warmth * (.014f + .006f * shadowWeight + .008f * highlightWeight);
        }
        lightness = clamp(lightness, 0f, 1f);

        float lp = lightness + .39633778f*a + .21580376f*bb;
        float mp = lightness - .10556135f*a - .06385417f*bb;
        float sp = lightness - .08948418f*a - 1.29148555f*bb;
        lp *= lp * lp; mp *= mp * mp; sp *= sp * sp;
        float outR = 4.07674166f*lp - 3.30771159f*mp + .23096993f*sp;
        float outG = -1.26843800f*lp + 2.60975740f*mp - .34131940f*sp;
        float outB = -.00419609f*lp - .70341861f*mp + 1.70761470f*sp;
        for (int i = 0; i < 8 && (outR < 0f || outR > 1f || outG < 0f || outG > 1f || outB < 0f || outB > 1f); i++) {
            a *= .82f; bb *= .82f;
            lp = lightness + .39633778f*a + .21580376f*bb;
            mp = lightness - .10556135f*a - .06385417f*bb;
            sp = lightness - .08948418f*a - 1.29148555f*bb;
            lp *= lp * lp; mp *= mp * mp; sp *= sp * sp;
            outR = 4.07674166f*lp - 3.30771159f*mp + .23096993f*sp;
            outG = -1.26843800f*lp + 2.60975740f*mp - .34131940f*sp;
            outB = -.00419609f*lp - .70341861f*mp + 1.70761470f*sp;
        }

        float grainAmount = clamp(look.grain, 0f, 1f);
        if (grainAmount > 0f) {
            int grainScale = look.grainStructure.startsWith("Coarse") ? 5 : look.grainStructure.startsWith("Soft") ? 2 : 1;
            float fine = hashNoise(x, y, seed);
            float coarse = hashNoise(x / (grainScale * 3), y / (grainScale * 3), seed ^ 0x51ed270b);
            float texture = (fine * (grainScale == 1 ? .72f : .40f) + coarse * (grainScale == 1 ? .28f : .60f)) * grainAmount * .05f;
            texture *= .38f + .62f * 4f * lightness * (1f - lightness);
            outR += texture; outG += texture; outB += texture;
        }
        return Color.rgb(toSrgb(outR), toSrgb(outG), toSrgb(outB));
    }

    private static void applyHalation(Bitmap bitmap, float amount) {
        applyHalation(bitmap, amount, null);
    }

    private static void applyHalation(Bitmap bitmap, float amount, String aspect) {
        int w = bitmap.getWidth(), h = bitmap.getHeight();
        int scale = 8, sw = Math.max(1, (w + scale - 1) / scale), sh = Math.max(1, (h + scale - 1) / scale);
        float[] glow = new float[sw * sh];
        for (int y = 0; y < sh; y++) for (int x = 0; x < sw; x++) {
            int c = bitmap.getPixel(Math.min(w - 1, x * scale), Math.min(h - 1, y * scale));
            float luma = (.2126f*Color.red(c) + .7152f*Color.green(c) + .0722f*Color.blue(c)) / 255f;
            glow[y * sw + x] = Math.max(0f, luma - .72f);
        }
        float[] temp = new float[glow.length];
        boolean isAnamorphic = aspect != null && AppSettings.parseAspectRatio(aspect) > 2.0f;
        blur(glow, temp, sw, sh, true);
        blur(temp, glow, sw, sh, false);
        blur(glow, temp, sw, sh, true);
        blur(temp, glow, sw, sh, false);
        if (isAnamorphic) {
            blur(glow, temp, sw, sh, true);
            System.arraycopy(temp, 0, glow, 0, glow.length);
        }
        int[] row = new int[w];
        float strength = clamp(amount, 0f, 1f) * 1.6f;
        for (int y = 0; y < h; y++) {
            bitmap.getPixels(row, 0, w, 0, y, w, 1);
            int gy = Math.min(sh - 1, y / scale) * sw;
            for (int x = 0; x < w; x++) {
                float halo = glow[gy + Math.min(sw - 1, x / scale)] * strength;
                int c = row[x];
                row[x] = Color.rgb(clamp255(Color.red(c) + halo * 90f),
                        clamp255(Color.green(c) + halo * 28f), Color.blue(c));
            }
            bitmap.setPixels(row, 0, w, 0, y, w, 1);
        }
    }

    private static void applyChromaDenoise(Bitmap bitmap, float amount) {
        int w = bitmap.getWidth(), h = bitmap.getHeight();
        if (w < 3 || h < 3) return;
        float strength = clamp(amount, 0f, 1f) * .72f;
        int[] previous = new int[w], current = new int[w], next = new int[w];
        int[] out = new int[w];
        bitmap.getPixels(previous, 0, w, 0, 0, w, 1);
        bitmap.getPixels(current, 0, w, 0, 1, w, 1);
        for (int y = 1; y < h - 1; y++) {
            bitmap.getPixels(next, 0, w, 0, y + 1, w, 1);
            System.arraycopy(current, 0, out, 0, w);
            for (int x = 1; x < w - 1; x++) {
                int c = current[x], left = current[x-1], right = current[x+1], up = previous[x], down = next[x];
                float r = Color.red(c)/255f, g = Color.green(c)/255f, b = Color.blue(c)/255f;
                float luma = .2126f*r + .7152f*g + .0722f*b;
                float cr = r-luma, cg = g-luma, cb = b-luma;
                float mr=0f, mg=0f, mb=0f, neighbourLuma=0f;
                float nr=Color.red(left)/255f, ng=Color.green(left)/255f, nb=Color.blue(left)/255f;
                float nl=.2126f*nr + .7152f*ng + .0722f*nb;
                mr += nr-nl; mg += ng-nl; mb += nb-nl; neighbourLuma += nl;
                nr=Color.red(right)/255f; ng=Color.green(right)/255f; nb=Color.blue(right)/255f;
                nl=.2126f*nr + .7152f*ng + .0722f*nb;
                mr += nr-nl; mg += ng-nl; mb += nb-nl; neighbourLuma += nl;
                nr=Color.red(up)/255f; ng=Color.green(up)/255f; nb=Color.blue(up)/255f;
                nl=.2126f*nr + .7152f*ng + .0722f*nb;
                mr += nr-nl; mg += ng-nl; mb += nb-nl; neighbourLuma += nl;
                nr=Color.red(down)/255f; ng=Color.green(down)/255f; nb=Color.blue(down)/255f;
                nl=.2126f*nr + .7152f*ng + .0722f*nb;
                mr += nr-nl; mg += ng-nl; mb += nb-nl; neighbourLuma += nl;
                mr *= .25f; mg *= .25f; mb *= .25f; neighbourLuma *= .25f;
                float gradient = Math.abs(luma-neighbourLuma);
                float blend = strength * Math.max(0f, 1f-gradient*5f);
                float resultR = luma + cr*(1f-blend) + mr*blend;
                float resultG = luma + cg*(1f-blend) + mg*blend;
                float resultB = luma + cb*(1f-blend) + mb*blend;
                out[x] = Color.rgb(clamp255(resultR*255f), clamp255(resultG*255f), clamp255(resultB*255f));
            }
            bitmap.setPixels(out, 0, w, 0, y, w, 1);
            int[] old = previous; previous = current; current = next; next = old;
        }
    }

    private static void blur(float[] src, float[] dst, int w, int h, boolean horizontal) {
        int radius = 2;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            float sum = 0f; int count = 0;
            for (int d = -radius; d <= radius; d++) {
                int sx = horizontal ? clampIndex(x + d, w) : x;
                int sy = horizontal ? y : clampIndex(y + d, h);
                sum += src[sy * w + sx]; count++;
            }
            dst[y * w + x] = sum / count;
        }
    }

    private static void applySharpen(Bitmap bitmap, float amount) {
        int w = bitmap.getWidth(), h = bitmap.getHeight();
        if (w < 3 || h < 3) return;
        int[] previous = new int[w], current = new int[w], next = new int[w];
        int[] out = new int[w];
        bitmap.getPixels(previous, 0, w, 0, 0, w, 1);
        bitmap.getPixels(current, 0, w, 0, 1, w, 1);
        for (int y = 1; y < h - 1; y++) {
            bitmap.getPixels(next, 0, w, 0, y + 1, w, 1);
            out[0] = current[0];
            out[w - 1] = current[w - 1];
            for (int x = 1; x < w - 1; x++) {
                int c = current[x];
                float r = Color.red(c) * (1f + 4f*amount) - amount*(Color.red(current[x-1])+Color.red(current[x+1])+Color.red(previous[x])+Color.red(next[x]));
                float g = Color.green(c) * (1f + 4f*amount) - amount*(Color.green(current[x-1])+Color.green(current[x+1])+Color.green(previous[x])+Color.green(next[x]));
                float b = Color.blue(c) * (1f + 4f*amount) - amount*(Color.blue(current[x-1])+Color.blue(current[x+1])+Color.blue(previous[x])+Color.blue(next[x]));
                out[x] = Color.rgb(clamp255(r), clamp255(g), clamp255(b));
            }
            bitmap.setPixels(out, 0, w, 0, y, w, 1);
            int[] old = previous; previous = current; current = next; next = old;
        }
    }

    static float[] cropRect(int width, int height, String aspect) {
        float ratio = AppSettings.parseAspectRatio(aspect);
        if (ratio <= 0f) return new float[]{0f, 0f, width, height};
        if (width < height) ratio = 1f / ratio;
        float current = width / (float) height;
        if (Math.abs(current - ratio) < .001f) return new float[]{0f, 0f, width, height};
        if (current > ratio) {
            float cropWidth = height * ratio;
            return new float[]{(width - cropWidth) * .5f, 0f, cropWidth, height};
        }
        float cropHeight = width / ratio;
        return new float[]{0f, (height - cropHeight) * .5f, width, cropHeight};
    }

    private static int normalizeOrientation(int orientation) {
        int normalized = ((orientation % 360) + 360) % 360;
        return normalized == 90 || normalized == 180 || normalized == 270 ? normalized : 0;
    }

    private static int[] buildSrgbLut() {
        int[] lut = new int[65536];
        for (int i = 0; i < lut.length; i++) {
            float linear = i / 65535f;
            float encoded = linear <= .0031308f ? linear * 12.92f
                    : 1.055f * (float) Math.pow(linear, 1f / 2.4f) - .055f;
            lut[i] = clamp255(encoded * 255f);
        }
        return lut;
    }

    private static int scaleColor(int color, float scale) {
        return Color.rgb(clamp255(Color.red(color)*scale), clamp255(Color.green(color)*scale), clamp255(Color.blue(color)*scale));
    }
    private static float smoothstep(float edge0, float edge1, float x) {
        float t = clamp((x-edge0)/(edge1-edge0), 0f, 1f); return t*t*(3f-2f*t);
    }
    private static float hashNoise(int x, int y, int seed) {
        int n = x * 0x1f123bb5 ^ y * 0x5f356495 ^ seed;
        n ^= n >>> 16; n *= 0x7feb352d; n ^= n >>> 15; n *= 0x846ca68b; n ^= n >>> 16;
        return ((n & 0x00ffffff) / 8388607.5f) - 1f;
    }
    private static int clampIndex(int v, int size) { return Math.max(0, Math.min(size-1, v)); }
    private static float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }
    private static int clamp255(float v) { return Math.max(0, Math.min(255, Math.round(v))); }
    private static int toSrgb(float linear) {
        int index = Math.max(0, Math.min(65535, Math.round(clamp(linear, 0f, 1f) * 65535f)));
        return SRGB_LUT[index];
    }
    private static float srgbToLinear(float encoded) {
        return encoded <= .04045f ? encoded / 12.92f : (float) Math.pow((encoded + .055f) / 1.055f, 2.4);
    }
}
