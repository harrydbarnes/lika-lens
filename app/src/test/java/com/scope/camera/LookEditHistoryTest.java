package com.scope.camera;

import org.junit.Test;
import static org.junit.Assert.*;

public class LookEditHistoryTest {
    @Test public void undoAndRedoKeepIndependentRecipes() {
        LookEditHistory history = new LookEditHistory();
        RawDeveloper.Look look = new RawDeveloper.Look();
        look.exposureEv = 0;
        history.record(look);
        look.exposureEv = 1;
        RawDeveloper.Look previous = history.undo(look);
        assertEquals(0, previous.exposureEv, .001f);
        look.exposureEv = 9;
        assertEquals(1, history.redo(previous).exposureEv, .001f);
    }

    @Test public void newEditClearsRedoAndHistoryIsBounded() {
        LookEditHistory history = new LookEditHistory();
        RawDeveloper.Look look = new RawDeveloper.Look();
        for (int i = 0; i < 45; i++) { look.exposureEv = i; history.record(look); }
        int count = 0;
        while (history.canUndo()) { look = history.undo(look); count++; }
        assertEquals(40, count);
        assertTrue(history.canRedo());
        history.record(look);
        assertFalse(history.canRedo());
    }
}
