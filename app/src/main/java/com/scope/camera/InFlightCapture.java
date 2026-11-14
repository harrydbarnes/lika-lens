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
        final CaptureRecipe recipe;

        CompletedPair(I image, R result, CaptureRecipe recipe) {
            this.image = image;
            this.result = result;
            this.recipe = recipe;
        }
    }

    final long captureId;
    final boolean expectsMetadata;
    final CaptureRecipe recipe;
    private I image;
    private R result;
    private enum Phase { WAITING, COMPLETED, CANCELLED }
    private Phase phase = Phase.WAITING;

    InFlightCapture(long captureId, boolean expectsMetadata) {
        this(captureId, expectsMetadata, null);
    }

    InFlightCapture(long captureId, boolean expectsMetadata, CaptureRecipe recipe) {
        this.captureId = captureId;
        this.expectsMetadata = expectsMetadata;
        this.recipe = recipe;
    }

    synchronized boolean isActive() {
        return phase == Phase.WAITING;
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
        if (!isActive()) return;
        phase = Phase.CANCELLED;
        if (this.image != null) {
            if (disposer != null) disposer.accept(this.image);
            this.image = null;
        }
        this.result = null;
    }

    private CompletedPair<I, R> takeCompletedPairIfReady() {
        if (!isActive() || this.image == null) return null;
        if (expectsMetadata && this.result == null) return null;
        phase = Phase.COMPLETED;
        CompletedPair<I, R> pair = new CompletedPair<>(this.image, this.result, this.recipe);
        this.image = null;
        this.result = null;
        return pair;
    }
}
