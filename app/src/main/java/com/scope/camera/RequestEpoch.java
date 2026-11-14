package com.scope.camera;

/** Ownership of a replaceable camera/session request, independent of resource nullability. */
final class RequestEpoch {
    private long value;
    synchronized long next() { return ++value; }
    synchronized void invalidate() { ++value; }
    synchronized boolean owns(long ticket) { return ticket == value; }
}
