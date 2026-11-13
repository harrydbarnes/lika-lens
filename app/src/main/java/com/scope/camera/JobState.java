package com.scope.camera;

/** A generation-owned operation. Old callbacks cannot finish, fail or revive a newer job. */
final class JobState {
    enum Phase { IDLE, LOADING, RENDERING, EXTRACTING, GRADING, ENCODING, SAVING, READY, FAILED, CANCELLED, CLOSED }
    private long generation;
    private Phase phase = Phase.IDLE;

    synchronized long begin(Phase next) {
        if (phase == Phase.CLOSED) return 0;
        phase = next;
        return ++generation;
    }
    synchronized long tryBegin(Phase next) { return isBusy() ? 0 : begin(next); }
    synchronized boolean accepts(long ticket) { return ticket != 0 && ticket == generation && phase != Phase.CLOSED && phase != Phase.CANCELLED; }
    synchronized boolean move(long ticket, Phase next) {
        if (!accepts(ticket) || phase==Phase.READY || phase==Phase.FAILED) return false;
        phase = next;
        return true;
    }
    synchronized Phase phase() { return phase; }
    synchronized boolean isClosed() { return phase == Phase.CLOSED; }
    synchronized boolean isBusy() {
        return phase == Phase.LOADING || phase == Phase.RENDERING || phase == Phase.EXTRACTING
                || phase == Phase.GRADING || phase == Phase.ENCODING || phase == Phase.SAVING;
    }
    synchronized void cancel() { if (phase != Phase.CLOSED) { ++generation; phase = Phase.CANCELLED; } }
    synchronized void close() { ++generation; phase = Phase.CLOSED; }
}
