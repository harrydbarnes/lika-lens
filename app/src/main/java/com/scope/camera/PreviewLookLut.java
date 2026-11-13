package com.scope.camera;

/** A sampled version of the export's point colour transform. Spatial effects stay export-only. */
final class PreviewLookLut {
    static final int SIZE = 33;
    final int[] pixels;

    private PreviewLookLut(int[] pixels) { this.pixels = pixels; }

    static PreviewLookLut build(RawDeveloper.Look look) {
        return build(look, null);
    }

    static PreviewLookLut build(RawDeveloper.Look look, RawArchive.Frame frame) {
        RawDeveloper.PreparedLook prepared = RawDeveloper.prepareDisplayLook(look.copy(), frame);
        int[] pixels = new int[SIZE * SIZE * SIZE];
        for (int b = 0; b < SIZE; b++) for (int g = 0; g < SIZE; g++) for (int r = 0; r < SIZE; r++) {
            pixels[r + SIZE * (g + SIZE * b)] = RawDeveloper.renderDisplayRgb(
                    r / (float) (SIZE - 1), g / (float) (SIZE - 1), b / (float) (SIZE - 1), prepared);
        }
        return new PreviewLookLut(pixels);
    }
}
