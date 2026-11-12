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
            + "precision mediump float; uniform samplerExternalOES camera; varying vec2 v; "
            + "void main(){gl_FragColor=texture2D(camera,v);}";
    private final HandlerThread thread = new HandlerThread("VideoRotation");
    private final Handler handler;
    private final int width, height;
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
    private int program, textureId;
    private volatile State state = State.INITIALIZING;

    VideoRotationSurface(Surface encoder, int width, int height, int rotation, Runnable onFailure) {
        this.width = width;
        this.height = height;
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
        int[] names = new int[1];
        GLES20.glGenTextures(1, names, 0);
        textureId = names[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
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
            GLES20.glViewport(0, 0, width, height);
            GLES20.glClearColor(0, 0, 0, 1);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            GLES20.glUseProgram(program);
            int position = GLES20.glGetAttribLocation(program, "p");
            int uv = GLES20.glGetAttribLocation(program, "uv");
            vertices.position(0);
            coordinates.position(0);
            GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 0, vertices);
            GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false, 0, coordinates);
            GLES20.glEnableVertexAttribArray(position);
            GLES20.glEnableVertexAttribArray(uv);
            GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "textureMatrix"), 1, false, samplingMatrix, 0);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "camera"), 0);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            check(GLES20.glGetError() == GLES20.GL_NO_ERROR, "rotate recording frame");
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
