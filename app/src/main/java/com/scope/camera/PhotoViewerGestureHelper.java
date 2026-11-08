package com.scope.camera;

import android.graphics.Matrix;
import android.graphics.RectF;
import android.widget.ImageView;

/**
 * Handles matrix transformations, bounds clamping, and scale calculations for photo viewing.
 */
public class PhotoViewerGestureHelper {

    private final Matrix matrix = new Matrix();
    private float currentScale = 1.0f;

    public Matrix getMatrix() {
        return matrix;
    }

    public float getCurrentScale() {
        return currentScale;
    }

    public void setCurrentScale(float scale) {
        this.currentScale = scale;
    }

    public static float computeFitScale(int imageWidth, int imageHeight, float viewW, float viewH) {
        if (imageWidth <= 0 || imageHeight <= 0 || viewW <= 0 || viewH <= 0) return 1.0f;
        return Math.min(viewW / (float) imageWidth, viewH / (float) imageHeight);
    }

    public static void clampMatrix(Matrix matrix, int imageWidth, int imageHeight, float viewW, float viewH) {
        if (imageWidth <= 0 || imageHeight <= 0 || viewW <= 0 || viewH <= 0) return;
        RectF rect = new RectF(0, 0, imageWidth, imageHeight);
        matrix.mapRect(rect);
        float deltaX = 0f, deltaY = 0f;

        if (rect.width() < viewW) deltaX = (viewW - rect.width()) * 0.5f - rect.left;
        else if (rect.left > 0) deltaX = -rect.left;
        else if (rect.right < viewW) deltaX = viewW - rect.right;

        if (rect.height() < viewH) deltaY = (viewH - rect.height()) * 0.5f - rect.top;
        else if (rect.top > 0) deltaY = -rect.top;
        else if (rect.bottom < viewH) deltaY = viewH - rect.bottom;

        matrix.postTranslate(deltaX, deltaY);
    }

    public void fitImageToView(ImageView photoView, int imageWidth, int imageHeight) {
        if (imageWidth <= 0 || imageHeight <= 0 || photoView == null || photoView.getWidth() <= 0 || photoView.getHeight() <= 0) return;
        matrix.reset();
        float viewW = photoView.getWidth();
        float viewH = photoView.getHeight();
        float scale = computeFitScale(imageWidth, imageHeight, viewW, viewH);
        float dx = (viewW - imageWidth * scale) * 0.5f;
        float dy = (viewH - imageHeight * scale) * 0.5f;
        matrix.setScale(scale, scale);
        matrix.postTranslate(dx, dy);
        photoView.setImageMatrix(matrix);
    }

    public void resetZoom(ImageView photoView, int imageWidth, int imageHeight) {
        currentScale = 1.0f;
        fitImageToView(photoView, imageWidth, imageHeight);
    }
}
