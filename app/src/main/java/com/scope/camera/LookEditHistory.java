package com.scope.camera;

import java.util.ArrayDeque;
import java.util.Deque;

/** Bounded, independent recipe snapshots; one slider drag is one edit. */
final class LookEditHistory {
    private final Deque<RawDeveloper.Look> undo = new ArrayDeque<>();
    private final Deque<RawDeveloper.Look> redo = new ArrayDeque<>();
    void record(RawDeveloper.Look look) {
        undo.push(look.copy());
        if (undo.size() > 40) undo.removeLast();
        redo.clear();
    }
    boolean canUndo() { return !undo.isEmpty(); }
    boolean canRedo() { return !redo.isEmpty(); }
    RawDeveloper.Look undo(RawDeveloper.Look current) {
        if (!canUndo()) return current;
        redo.push(current.copy());
        return undo.pop().copy();
    }
    RawDeveloper.Look redo(RawDeveloper.Look current) {
        if (!canRedo()) return current;
        undo.push(current.copy());
        return redo.pop().copy();
    }
}
