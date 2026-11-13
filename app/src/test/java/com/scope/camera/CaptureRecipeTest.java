package com.scope.camera;

import org.junit.Test;
import static org.junit.Assert.*;

public class CaptureRecipeTest {
    @Test public void asynchronousCompletionKeepsTheShutterRecipe() {
        RawDeveloper.Look look = new RawDeveloper.Look();
        look.brain = AppSettings.BRAIN_KODACHROME;
        look.aspectRatio = "4:3";
        look.flipHorizontal = true;
        CaptureRecipe recipe = new CaptureRecipe(null, 90, look, "Save DNG", "RAW + rendered JPEG", true, .12f);
        InFlightCapture<String,String> capture = new InFlightCapture<>(1L, true, recipe);
        look.brain = AppSettings.BRAIN_NEUTRAL_RAW;
        look.aspectRatio = "1:1";
        look.flipHorizontal = false;
        assertNull(capture.attachImage("frame", ignored -> {}));
        InFlightCapture.CompletedPair<String,String> pair = capture.attachResult("metadata");
        assertSame(recipe, pair.recipe);
        assertEquals(AppSettings.BRAIN_KODACHROME, pair.recipe.look.brain);
        assertEquals("4:3", pair.recipe.look.aspectRatio);
        assertTrue(pair.recipe.look.flipHorizontal);
        assertEquals(90, pair.recipe.orientation);
        assertEquals(.12f, pair.recipe.previewMidTarget, .0001f);
    }
}
