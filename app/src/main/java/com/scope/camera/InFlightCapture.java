package com.scope.camera;

import java.util.function.Consumer;

/**
 * Coordinates the asynchronous dual-producer join barrier for a single still capture
 * (ImageReader frame + Camera2 TotalCaptureResult), preventing late callbacks from
 * pairing with a subsequent capture.
 */
final class InFlightCapture<I, R> {

    static final class CompletedPair<I, R> {
        final I image;
        final R result;

        CompletedPair(I image, R result) {
            this.image = image;
            this.result = result;
        }
    }

    final long captureId;
    final boolean expectsMetadata;
    private I image;
    private R result;
    private boolean cancelled;
    private boolean completed;

    InFlightCapture(long captureId, boolean expectsMetadata) {
        this.captureId = captureId;
        this.expectsMetadata = expectsMetadata;
    }

    synchronized boolean isActive() {
        return !cancelled && !completed;
    }

    synchronized CompletedPair<I, R> attachImage(I newImage, Consumer<I> disposer) {
        if (newImage == null) return null;
        if (!isActive()) {
            if (disposer != null) disposer.accept(newImage);
            return null;
        }
        if (this.image != null && this.image != newImage && disposer != null) {
            disposer.accept(this.image);
        }
        this.image = newImage;
        return takeCompletedPairIfReady();
    }

    synchronized CompletedPair<I, R> attachResult(R newResult) {
        if (newResult == null || !isActive()) return null;
        this.result = newResult;
        return takeCompletedPairIfReady();
    }

    synchronized void cancel(Consumer<I> disposer) {
        if (cancelled || completed) return;
        cancelled = true;
        if (this.image != null) {
            if (disposer != null) disposer.accept(this.image);
            this.image = null;
        }
        this.result = null;
    }

    private CompletedPair<I, R> takeCompletedPairIfReady() {
        if (!isActive() || this.image == null) return null;
        if (expectsMetadata && this.result == null) return null;
        this.completed = true;
        CompletedPair<I, R> pair = new CompletedPair<>(this.image, this.result);
        this.image = null;
        this.result = null;
        return pair;
    }
}
