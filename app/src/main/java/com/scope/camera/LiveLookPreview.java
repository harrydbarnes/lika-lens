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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Builds look tables off the UI thread; the camera itself continues to preview at full rate. */
final class LiveLookPreview {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicInteger generation = new AtomicInteger();
    private Runnable pending;
    private String currentKey;
    private TextureView currentView;
    private boolean closed;

    boolean apply(TextureView view, RawDeveloper.Look look, RawArchive.Frame frame, Runnable fallback) {
        if (Build.VERSION.SDK_INT < 33 || closed) return false;
        RawDeveloper.PreparedLook prepared = RawDeveloper.prepareDisplayLook(look, frame);
        String key = look.toJson() + ":" + Math.round(prepared.ambientWarmthFactor * 20f)
                + ":" + prepared.fluorescentSpike;
        if (view == currentView && key.equals(currentKey)) return true;
        int ticket = generation.incrementAndGet();
        if (pending != null) main.removeCallbacks(pending);
        // Keep the previous effect visible while its replacement is built to avoid slider flicker.
        if (currentView != null && currentView != view) Api33.clear(currentView);
        currentKey = key;
        currentView = view;
        RawDeveloper.Look snapshot = look.copy();
        // Coalesce slider changes instead of queuing a table rebuild for every touch event.
        pending = () -> worker.execute(() -> {
            if (generation.get() != ticket) return;
            try {
                PreviewLookLut table = PreviewLookLut.build(snapshot, frame);
                main.post(() -> {
                    if (generation.get() != ticket || closed) return;
                    try {
                        Api33.apply(view, table);
                    } catch (RuntimeException error) {
                        Log.w("LikaLens", "Shared look preview unavailable on this GPU", error);
                        clear();
                        fallback.run();
                    }
                });
            } catch (RuntimeException error) {
                Log.w("LikaLens", "Could not build shared look preview", error);
                main.post(() -> { if (generation.get() == ticket) { clear(); fallback.run(); } });
            }
        });
        main.postDelayed(pending, 60L);
        return true;
    }

    void clear() {
        generation.incrementAndGet();
        if (pending != null) main.removeCallbacks(pending);
        pending = null;
        if (currentView != null && Build.VERSION.SDK_INT >= 33) Api33.clear(currentView);
        currentView = null;
        currentKey = null;
    }

    void close() {
        closed = true;
        clear();
        worker.shutdown();
    }

    @TargetApi(33)
    private static final class Api33 {
        // Flattened cube: x = red + SIZE * green, y = blue. Sample texel centres explicitly.
        private static final String PROGRAM = ""
                + "uniform shader camera; uniform shader cube; uniform float size;"
                + "half3 point(float3 p) { return cube.eval(float2(p.r + size*p.g + 0.5, p.b + 0.5)).rgb; }"
                + "half4 main(float2 pos) {"
                + " half4 c = camera.eval(pos);"
                + " float3 p = clamp(float3(c.rgb) / max(float(c.a), 0.0001), 0.0, 1.0) * (size-1.0);"
                + " float3 a=floor(p); float3 b=min(a+1.0, float3(size-1.0)); float3 t=p-a;"
                + " half3 z0=mix(mix(point(a),point(float3(b.r,a.g,a.b)),t.r),"
                + "               mix(point(float3(a.r,b.g,a.b)),point(float3(b.r,b.g,a.b)),t.r),t.g);"
                + " half3 z1=mix(mix(point(float3(a.r,a.g,b.b)),point(float3(b.r,a.g,b.b)),t.r),"
                + "               mix(point(float3(a.r,b.g,b.b)),point(b),t.r),t.g);"
                + " return half4(mix(z0,z1,t.b)*c.a,c.a); }";

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
