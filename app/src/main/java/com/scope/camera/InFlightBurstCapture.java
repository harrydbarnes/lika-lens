package com.scope.camera;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Coordinates an ordered multi-frame Camera2 burst capture, matching asynchronous ImageReader
 * frames with TotalCaptureResult metadata by sensor timestamp (with FIFO fallback) so frames
 * can be streamed into a stacking accumulator without exhausting ImageReader buffer slots.
 */
final class InFlightBurstCapture<I, R> {

    static final class BurstStep<I, R> {
        final I image;
        final R result;
        final CaptureRecipe recipe;
        final int frameIndex;
        final int completedCount;
        final boolean isLast;
        final boolean failed;

        BurstStep(I image, R result, CaptureRecipe recipe, int frameIndex,
                  int completedCount, boolean isLast, boolean failed) {
            this.image = image;
            this.result = result;
            this.recipe = recipe;
            this.frameIndex = frameIndex;
            this.completedCount = completedCount;
            this.isLast = isLast;
            this.failed = failed;
        }
    }

    private static final class PendingItem<T> {
        final T item;
        final long timestampNs;

        PendingItem(T item, long timestampNs) {
            this.item = item;
            this.timestampNs = timestampNs;
        }
    }

    private enum Phase { WAITING, COMPLETED, CANCELLED }

    final long captureId;
    final int targetFrames;
    final boolean expectsMetadata;
    final CaptureRecipe recipe;

    private final List<PendingItem<I>> pendingImages = new ArrayList<>();
    private final List<PendingItem<R>> pendingResults = new ArrayList<>();
    private int pairedCount = 0;
    private int failedFrames = 0;
    private Phase phase = Phase.WAITING;

    InFlightBurstCapture(long captureId, int targetFrames, boolean expectsMetadata, CaptureRecipe recipe) {
        this.captureId = captureId;
        this.targetFrames = Math.max(1, targetFrames);
        this.expectsMetadata = expectsMetadata;
        this.recipe = recipe;
    }

    synchronized boolean isActive() {
        return phase == Phase.WAITING;
    }

    synchronized int pairedCount() {
        return pairedCount;
    }

    synchronized BurstStep<I, R> attachImage(I image, long timestampNs, Consumer<I> disposer) {
        if (image == null) return null;
        if (!isActive()) {
            if (disposer != null) disposer.accept(image);
            return null;
        }
        int matchIdx = findMatchingIndex(pendingResults, timestampNs);
        if (matchIdx >= 0) {
            R result = pendingResults.remove(matchIdx).item;
            return emitPaired(image, result, disposer);
        }
        if (!expectsMetadata) {
            R fallbackResult = pendingResults.isEmpty() ? null : pendingResults.remove(0).item;
            return emitPaired(image, fallbackResult, disposer);
        }
        pendingImages.add(new PendingItem<>(image, timestampNs));
        return null;
    }

    synchronized BurstStep<I, R> attachResult(R result, long timestampNs, Consumer<I> disposer) {
        if (result == null || !isActive()) return null;
        int matchIdx = findMatchingIndex(pendingImages, timestampNs);
        if (matchIdx >= 0) {
            I image = pendingImages.remove(matchIdx).item;
            return emitPaired(image, result, disposer);
        }
        if (pairedCount + failedFrames < targetFrames) {
            pendingResults.add(new PendingItem<>(result, timestampNs));
        }
        return null;
    }

    synchronized BurstStep<I, R> markFrameFailed(Consumer<I> disposer) {
        if (!isActive()) return null;
        failedFrames++;
        if (pairedCount + failedFrames >= targetFrames) {
            return finishOnPartialOrFailure(disposer);
        }
        return null;
    }

    synchronized BurstStep<I, R> abortSequence(Consumer<I> disposer) {
        if (!isActive()) return null;
        return finishOnPartialOrFailure(disposer);
    }

    synchronized void cancel(Consumer<I> disposer) {
        if (!isActive()) return;
        phase = Phase.CANCELLED;
        disposePendingImages(disposer);
        pendingResults.clear();
    }

    private BurstStep<I, R> emitPaired(I image, R result, Consumer<I> disposer) {
        int index = pairedCount++;
        boolean isLast = (pairedCount + failedFrames) >= targetFrames;
        if (isLast) {
            phase = Phase.COMPLETED;
            disposePendingImages(disposer);
            pendingResults.clear();
        }
        return new BurstStep<>(image, result, recipe, index, pairedCount, isLast, false);
    }

    private BurstStep<I, R> finishOnPartialOrFailure(Consumer<I> disposer) {
        disposePendingImages(disposer);
        pendingResults.clear();
        if (pairedCount > 0) {
            phase = Phase.COMPLETED;
            return new BurstStep<>(null, null, recipe, pairedCount - 1, pairedCount, true, false);
        }
        phase = Phase.CANCELLED;
        return new BurstStep<>(null, null, recipe, -1, 0, true, true);
    }

    private void disposePendingImages(Consumer<I> disposer) {
        if (disposer != null) {
            for (PendingItem<I> pending : pendingImages) {
                if (pending.item != null) disposer.accept(pending.item);
            }
        }
        pendingImages.clear();
    }

    private static <T> int findMatchingIndex(List<PendingItem<T>> list, long timestampNs) {
        if (list.isEmpty()) return -1;
        if (timestampNs > 0L) {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).timestampNs == timestampNs) return i;
            }
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).timestampNs <= 0L) return i;
            }
            return -1;
        }
        return 0;
    }
}
