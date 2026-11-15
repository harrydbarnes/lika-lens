package com.scope.camera;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.RenderEffect;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Builds look tables off the UI thread; the camera itself continues to preview at full rate. */
final class LiveLookPreview {
    private static final int CACHE_CAPACITY = 6;
    private static final Map<String, PreviewLookLut> LUT_CACHE = Collections.synchronizedMap(
            new LinkedHashMap<String, PreviewLookLut>(CACHE_CAPACITY + 1, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, PreviewLookLut> eldest) {
                    return size() > CACHE_CAPACITY;
                }
            });

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicInteger generation = new AtomicInteger();
    private Runnable pending;
    private String currentKey;
    private TextureView currentView;
    enum Phase { DISABLED, BUILDING, ACTIVE, FALLBACK, DISPOSED }
    private Phase phase=Phase.DISABLED;
    private enum Capability { UNKNOWN, SUPPORTED, UNSUPPORTED }
    private Capability capability=Capability.UNKNOWN;

    static void clearCache() {
        LUT_CACHE.clear();
    }

    static PreviewLookLut getCachedLut(String key) {
        return key == null ? null : LUT_CACHE.get(key);
    }

    static void putCachedLut(String key, PreviewLookLut table) {
        if (key != null && table != null) {
            LUT_CACHE.put(key, table);
        }
    }

    boolean apply(TextureView view, RawDeveloper.Look look, RawArchive.Frame frame, Runnable fallback) {
        if(phase==Phase.DISPOSED) return false;
        if(Build.VERSION.SDK_INT<33 || capability==Capability.UNSUPPORTED) { phase=Phase.FALLBACK;return false; }
        String key = RawDeveloper.displayLookKey(look, frame);
        if (view == currentView && key.equals(currentKey) && (phase == Phase.ACTIVE || phase == Phase.BUILDING)) {
            return true;
        }
        String previousKey = currentKey;
        TextureView previousView = currentView;
        boolean hadActiveEffect = phase == Phase.ACTIVE && previousView == view;
        if (pending != null) {
            main.removeCallbacks(pending);
            pending = null;
        }
        if (currentView != null && currentView != view) Api33.clear(currentView);

        PreviewLookLut cached = getCachedLut(key);
        if (cached != null) {
            int ticket = generation.incrementAndGet();
            currentKey = key;
            currentView = view;
            try {
                if (generation.get() == ticket) {
                    Api33.apply(view, cached);
                    capability = Capability.SUPPORTED;
                    phase = Phase.ACTIVE;
                }
                return true;
            } catch (RuntimeException error) {
                Log.w("LikaLens", "Shared look preview unavailable on this GPU", error);
                capability = Capability.UNSUPPORTED;
                clear();
                phase = Phase.FALLBACK;
                if (fallback != null) fallback.run();
                return true;
            }
        }

        // Before an exact 3D shader is active on this view, immediately paint the ColorMatrix
        // approximation so the first preview frame already carries the selected brain's look.
        if (!hadActiveEffect && fallback != null) {
            fallback.run();
        }

        phase = Phase.BUILDING;
        int ticket = generation.incrementAndGet();
        currentKey = key;
        currentView = view;
        RawDeveloper.Look snapshot = look.copy();
        Runnable buildTask = () -> worker.execute(() -> {
            if (generation.get() != ticket) return;
            try {
                PreviewLookLut table = PreviewLookLut.build(snapshot, frame);
                putCachedLut(key, table);
                main.post(() -> {
                    if (generation.get() != ticket || phase==Phase.DISPOSED) return;
                    try {
                        Api33.apply(view, table);
                        capability=Capability.SUPPORTED;phase=Phase.ACTIVE;
                    } catch (RuntimeException error) {
                        Log.w("LikaLens", "Shared look preview unavailable on this GPU", error);
                        capability=Capability.UNSUPPORTED;
                        clear();phase=Phase.FALLBACK;
                        if (fallback != null) fallback.run();
                    }
                });
            } catch (RuntimeException error) {
                Log.w("LikaLens", "Could not build shared look preview", error);
                main.post(() -> {
                    if (generation.get() == ticket) {
                        clear();
                        phase = Phase.FALLBACK;
                        if (fallback != null) fallback.run();
                    }
                });
            }
        });

        boolean isSliderUpdate = previousKey != null && previousView == view;
        if (isSliderUpdate) {
            // Coalesce slider changes instead of queuing a table rebuild for every touch event.
            pending = buildTask;
            main.postDelayed(pending, 60L);
        } else {
            // Initial startup / resume: start building immediately with zero artificial delay.
            buildTask.run();
        }
        return true;
    }

    void clear() {
        generation.incrementAndGet();
        if(phase!=Phase.DISPOSED) phase=Phase.DISABLED;
        if (pending != null) main.removeCallbacks(pending);
        pending = null;
        if (currentView != null && Build.VERSION.SDK_INT >= 33) Api33.clear(currentView);
        currentView = null;
        currentKey = null;
    }

    void close() {
        clear();
        phase=Phase.DISPOSED;
        worker.shutdown();
    }

    @TargetApi(33)
    private static final class Api33 {
        // Flattened sRGB cube: x = red + SIZE * green, y = blue. Sample texel centres explicitly.
        // Convert between RenderNode working color space (e.g. Display P3 on wide-gamut displays)
        // and the sRGB input/output buffer since setInputBuffer disables automatic conversion.
        private static final String PROGRAM = ""
                + "uniform shader camera; uniform shader cube; uniform float size;"
                + "float3 linToSrgb(float3 c) {"
                + " float3 v = clamp(c, 0.0, 1.0);"
                + " return mix(v * 12.92, 1.055 * pow(max(v, float3(0.0031308)), float3(1.0 / 2.4)) - 0.055, step(float3(0.0031308), v));"
                + "}"
                + "float3 srgbToLin(float3 c) {"
                + " float3 v = clamp(c, 0.0, 1.0);"
                + " return mix(v / 12.92, pow(max((v + 0.055) / 1.055, float3(0.0)), float3(2.4)), step(float3(0.04045), v));"
                + "}"
                + "float3 point(float3 p) { return float3(cube.eval(float2(p.r + size * p.g + 0.5, p.b + 0.5)).rgb); }"
                + "half4 main(float2 pos) {"
                + " half4 c = camera.eval(pos);"
                + " float alpha = max(float(c.a), 0.0001);"
                + " float3 ws = float3(c.rgb) / alpha;"
                + " float3 srgbIn = linToSrgb(toLinearSrgb(ws));"
                + " float3 p = clamp(srgbIn, 0.0, 1.0) * (size - 1.0);"
                + " float3 lo = floor(p); float3 hi = min(lo + 1.0, float3(size - 1.0)); float3 t = p - lo;"
                + " float3 z0 = mix(mix(point(lo), point(float3(hi.r, lo.g, lo.b)), t.r),"
                + "                 mix(point(float3(lo.r, hi.g, lo.b)), point(float3(hi.r, hi.g, lo.b)), t.r), t.g);"
                + " float3 z1 = mix(mix(point(float3(lo.r, lo.g, hi.b)), point(float3(hi.r, lo.g, hi.b)), t.r),"
                + "                 mix(point(float3(lo.r, hi.g, hi.b)), point(hi), t.r), t.g);"
                + " float3 srgbOut = mix(z0, z1, t.b);"
                + " half3 wsOut = half3(fromLinearSrgb(srgbToLin(srgbOut)));"
                + " return half4(wsOut * c.a, c.a); }";

        static void apply(TextureView view, PreviewLookLut table) {
            Bitmap bitmap = Bitmap.createBitmap(table.pixels, PreviewLookLut.SIZE * PreviewLookLut.SIZE,
                    PreviewLookLut.SIZE, Bitmap.Config.ARGB_8888);
            BitmapShader cube = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            cube.setFilterMode(BitmapShader.FILTER_MODE_NEAREST);
            RuntimeShader shader = new RuntimeShader(PROGRAM);
            shader.setInputBuffer("cube", cube);
            shader.setFloatUniform("size", (float) PreviewLookLut.SIZE);
            // Clear the older matrix paint so the look is applied exactly once.
            view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            view.setRenderEffect(RenderEffect.createRuntimeShaderEffect(shader, "camera"));
            view.invalidate();
            // RenderEffect retains its shader/bitmap. Let GC reclaim them when the effect is replaced.
        }

        static void clear(TextureView view) { view.setRenderEffect(null); }
    }
}
