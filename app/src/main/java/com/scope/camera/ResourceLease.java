package com.scope.camera;

/** Closing a camera reader must not invalidate frames still being developed or written as DNG. */
final class ResourceLease {
    enum Phase { OPEN, CLOSING, CLOSED }
    private Phase phase=Phase.OPEN;
    private int users;
    private final Runnable dispose;
    ResourceLease(Runnable dispose) { this.dispose=dispose; }
    synchronized boolean acquire() { if(phase!=Phase.OPEN) return false;++users;return true; }
    void release() {
        boolean close;
        synchronized(this) { if(users<=0) throw new IllegalStateException("Unbalanced resource release");--users;close=finishClose(); }
        if(close) dispose.run();
    }
    void close() {
        boolean close;
        synchronized(this) { if(phase==Phase.OPEN) phase=Phase.CLOSING;close=finishClose(); }
        if(close) dispose.run();
    }
    private boolean finishClose() {
        if(phase==Phase.CLOSING && users==0) { phase=Phase.CLOSED;return true; }
        return false;
    }
    synchronized Phase phase() { return phase; }
}
