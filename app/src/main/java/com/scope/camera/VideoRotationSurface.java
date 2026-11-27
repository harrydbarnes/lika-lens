package com.scope.camera;

import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Rotates camera pixels into the first camera's recording coordinates before encoding. */
final class VideoRotationSurface implements AutoCloseable {
    private static final String TAG = "VideoRotationSurface";
    private static final String VERTEX = "attribute vec2 p; attribute vec2 uv; uniform mat4 textureMatrix; "
            + "varying vec2 v; void main(){gl_Position=vec4(p,0.,1.);v=(textureMatrix*vec4(uv,0.,1.)).xy;}";
    private static final String FRAGMENT = "#extension GL_OES_EGL_image_external : require\n"
            + "#ifdef GL_FRAGMENT_PRECISION_HIGH\nprecision highp float;\n#else\nprecision mediump float;\n#endif\n"
            + "uniform samplerExternalOES camera; uniform sampler2D cube; uniform float useLut; uniform float lutSize; varying vec2 v;\n"
            + "vec3 sampleCube(vec3 p) {\n"
            + " float invW = 1.0 / (lutSize * lutSize);\n"
            + " float invH = 1.0 / lutSize;\n"
            + " float u = (p.r + lutSize * p.g + 0.5) * invW;\n"
            + " float w = (p.b + 0.5) * invH;\n"
            + " return texture2D(cube, vec2(u, w)).rgb;\n"
            + "}\n"
            + "void main(){\n"
            + " vec4 c = texture2D(camera, v);\n"
            + " if (useLut > 0.5) {\n"
            + "  vec3 p = clamp(c.rgb, 0.0, 1.0) * (lutSize - 1.0);\n"
            + "  vec3 lo = floor(p); vec3 hi = min(lo + 1.0, vec3(lutSize - 1.0)); vec3 t = p - lo;\n"
            + "  vec3 z0 = mix(mix(sampleCube(lo), sampleCube(vec3(hi.r, lo.g, lo.b)), t.r),\n"
            + "                mix(sampleCube(vec3(lo.r, hi.g, lo.b)), sampleCube(vec3(hi.r, hi.g, lo.b)), t.r), t.g);\n"
            + "  vec3 z1 = mix(mix(sampleCube(vec3(lo.r, lo.g, hi.b)), sampleCube(vec3(hi.r, lo.g, hi.b)), t.r),\n"
            + "                mix(sampleCube(vec3(lo.r, hi.g, hi.b)), sampleCube(hi), t.r), t.g);\n"
            + "  c.rgb = mix(z0, z1, t.b);\n"
            + " }\n"
            + " gl_FragColor = c;\n"
            + "}";
    private final HandlerThread thread = new HandlerThread("VideoRotation");
    private final Handler handler;
    private final int width, height;
    private final PreviewLookLut lookLut;
    private final FloatBuffer vertices, coordinates;
    private final float[] textureMatrix = new float[16];
    private final float[] samplingMatrix = new float[16];
    private final Runnable onFailure;
    enum State {
        INITIALIZING,
        ACTIVE,
        DRAINING,
        CLOSED,
        FAILED
    }

    private EGLDisplay display = EGL14.EGL_NO_DISPLAY;
    private EGLContext context = EGL14.EGL_NO_CONTEXT;
    private EGLSurface window = EGL14.EGL_NO_SURFACE;
    private SurfaceTexture texture;
    private Surface input;
    private int program, textureId, lutTextureId;
    private int positionAttr, uvAttr, textureMatrixUniform, cameraUniform;
    private int cubeUniform, useLutUniform, lutSizeUniform;
    private volatile State state = State.INITIALIZING;

    VideoRotationSurface(Surface encoder, int width, int height, int rotation, Runnable onFailure) {
        this(encoder, width, height, rotation, null, onFailure);
    }

    VideoRotationSurface(Surface encoder, int width, int height, int rotation, PreviewLookLut lookLut, Runnable onFailure) {
        this.width = width;
        this.height = height;
        this.lookLut = lookLut;
        this.onFailure = onFailure;
        vertices = buffer(VideoRecordingCoordinator.recordingVertices(rotation, width, height));
        coordinates = buffer(new float[]{0, 0, 1, 0, 0, 1, 1, 1});
        thread.start();
        handler = new Handler(thread.getLooper());
        CountDownLatch ready = new CountDownLatch(1);
        RuntimeException[] failure = new RuntimeException[1];
        handler.post(() -> {
            try { initialize(encoder); }
            catch (RuntimeException e) { state = State.FAILED; failure[0] = e; }
            finally { ready.countDown(); }
        });
        try {
            if (!ready.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Video rotation initialization timed out");
            if (failure[0] != null) throw failure[0];
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            close();
            throw new IllegalStateException("Video rotation initialization interrupted", e);
        } catch (RuntimeException e) { close(); throw e; }
    }

    static ByteBuffer packLutRgbaBuffer(PreviewLookLut lut) {
        int[] px = lut.pixels;
        ByteBuffer buf = ByteBuffer.allocateDirect(px.length * 4).order(ByteOrder.nativeOrder());
        for (int c : px) {
            buf.put((byte) ((c >> 16) & 0xff));
            buf.put((byte) ((c >> 8) & 0xff));
            buf.put((byte) (c & 0xff));
            buf.put((byte) 0xff);
        }
        buf.position(0);
        return buf;
    }

    Surface inputSurface() { return input; }

    State getState() { return state; }

    private void initialize(Surface encoder) {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        int[] version = new int[2];
        check(EGL14.eglInitialize(display, version, 0, version, 1), "initialize EGL");
        EGLConfig[] configs = new EGLConfig[1];
        int[] count = new int[1];
        int[] attributes = {EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_SURFACE_TYPE,
                EGL14.EGL_WINDOW_BIT, 0x3142 /* EGL_RECORDABLE_ANDROID */, 1, EGL14.EGL_NONE};
        check(EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) && count[0] > 0, "choose recording config");
        context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
                new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE}, 0);
        check(context != EGL14.EGL_NO_CONTEXT, "create recording context");
        window = EGL14.eglCreateWindowSurface(display, configs[0], encoder, new int[]{EGL14.EGL_NONE}, 0);
        check(window != EGL14.EGL_NO_SURFACE, "create recording surface");
        check(EGL14.eglMakeCurrent(display, window, window, context), "bind recording surface");
        int vertex = shader(GLES20.GL_VERTEX_SHADER, VERTEX);
        int fragment = shader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT);
        program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertex);
        GLES20.glAttachShader(program, fragment);
        GLES20.glLinkProgram(program);
        GLES20.glDeleteShader(vertex);
        GLES20.glDeleteShader(fragment);
        int[] linked = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0);
        check(linked[0] != 0, "link rotation shader");
        positionAttr = GLES20.glGetAttribLocation(program, "p");
        uvAttr = GLES20.glGetAttribLocation(program, "uv");
        textureMatrixUniform = GLES20.glGetUniformLocation(program, "textureMatrix");
        cameraUniform = GLES20.glGetUniformLocation(program, "camera");
        cubeUniform = GLES20.glGetUniformLocation(program, "cube");
        useLutUniform = GLES20.glGetUniformLocation(program, "useLut");
        lutSizeUniform = GLES20.glGetUniformLocation(program, "lutSize");
        int[] names = new int[1];
        GLES20.glGenTextures(1, names, 0);
        textureId = names[0];
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glUseProgram(program);
        GLES20.glUniform1i(cameraUniform, 0);
        if (lookLut != null && lookLut.pixels != null
                && lookLut.pixels.length == PreviewLookLut.SIZE * PreviewLookLut.SIZE * PreviewLookLut.SIZE) {
            int[] lutNames = new int[1];
            GLES20.glGenTextures(1, lutNames, 0);
            lutTextureId = lutNames[0];
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, lutTextureId);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                    PreviewLookLut.SIZE * PreviewLookLut.SIZE, PreviewLookLut.SIZE, 0,
                    GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, packLutRgbaBuffer(lookLut));
            GLES20.glUniform1i(cubeUniform, 1);
            GLES20.glUniform1f(useLutUniform, 1.0f);
            GLES20.glUniform1f(lutSizeUniform, (float) PreviewLookLut.SIZE);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        } else {
            GLES20.glUniform1i(cubeUniform, 1);
            GLES20.glUniform1f(useLutUniform, 0.0f);
            GLES20.glUniform1f(lutSizeUniform, (float) PreviewLookLut.SIZE);
        }
        GLES20.glViewport(0, 0, width, height);
        GLES20.glClearColor(0, 0, 0, 1);
        GLES20.glEnableVertexAttribArray(positionAttr);
        GLES20.glEnableVertexAttribArray(uvAttr);
        vertices.position(0);
        coordinates.position(0);
        GLES20.glVertexAttribPointer(positionAttr, 2, GLES20.GL_FLOAT, false, 0, vertices);
        GLES20.glVertexAttribPointer(uvAttr, 2, GLES20.GL_FLOAT, false, 0, coordinates);
        check(GLES20.glGetError() == GLES20.GL_NO_ERROR, "configure rotation shader");
        texture = new SurfaceTexture(textureId);
        texture.setDefaultBufferSize(width, height);
        input = new Surface(texture);
        state = State.ACTIVE;
        texture.setOnFrameAvailableListener(t -> renderFrame(), handler);
    }

    private void renderFrame() {
        if (state != State.ACTIVE) return;
        try {
            texture.updateTexImage();
            texture.getTransformMatrix(textureMatrix);
            VideoRecordingCoordinator.recordingTextureMatrix(textureMatrix, samplingMatrix);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            GLES20.glUniformMatrix4fv(textureMatrixUniform, 1, false, samplingMatrix, 0);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            check(EGLExt.eglPresentationTimeANDROID(display, window, texture.getTimestamp()), "set frame timestamp");
            check(EGL14.eglSwapBuffers(display, window), "submit recording frame");
        } catch (RuntimeException e) {
            state = State.FAILED;
            Log.e(TAG, "Video rotation failed", e);
            onFailure.run();
        }
    }

    /** Stop submitting frames before MediaRecorder.stop/reset invalidates its input. */
    void stopRendering() {
        synchronized (this) {
            if (state == State.CLOSED || state == State.FAILED) return;
            state = State.DRAINING;
        }
        CountDownLatch drained = new CountDownLatch(1);
        handler.post(drained::countDown);
        try { if (!drained.await(3, TimeUnit.SECONDS)) Log.w(TAG, "Rotation frame drain timed out"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    @Override public void close() {
        synchronized (this) {
            if (state == State.CLOSED) return;
            state = State.CLOSED;
        }
        CountDownLatch released = new CountDownLatch(1);
        handler.post(() -> {
            try {
                if (texture != null) texture.setOnFrameAvailableListener(null);
                if (input != null) input.release();
                if (texture != null) texture.release();
                if (program != 0) GLES20.glDeleteProgram(program);
                if (textureId != 0) GLES20.glDeleteTextures(1, new int[]{textureId}, 0);
                if (lutTextureId != 0) GLES20.glDeleteTextures(1, new int[]{lutTextureId}, 0);
                if (display != EGL14.EGL_NO_DISPLAY) {
                    EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
                    if (window != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, window);
                    if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context);
                    EGL14.eglReleaseThread();
                    EGL14.eglTerminate(display);
                }
            } finally { released.countDown(); thread.quitSafely(); }
        });
        try { if (!released.await(5, TimeUnit.SECONDS)) Log.w(TAG, "Rotation surface release timed out"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static int shader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            String error = GLES20.glGetShaderInfoLog(shader);
            GLES20.glDeleteShader(shader);
            throw new IllegalStateException("Video shader: " + error);
        }
        return shader;
    }

    private static FloatBuffer buffer(float[] values) {
        FloatBuffer buffer = ByteBuffer.allocateDirect(values.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        buffer.put(values).position(0);
        return buffer;
    }

    private static void check(boolean ok, String action) {
        if (!ok) throw new IllegalStateException("Could not " + action);
    }
}
