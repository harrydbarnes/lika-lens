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
        RawDeveloper.PreparedLook prepared = RawDeveloper.preparePreviewCubeLook(look.copy(), frame);
        final int[] pixels = new int[SIZE * SIZE * SIZE];
        final float inv = 1f / (SIZE - 1);
        java.util.stream.IntStream.range(0, SIZE).parallel().forEach(b -> {
            float bf = b * inv;
            int bOff = SIZE * b;
            for (int g = 0; g < SIZE; g++) {
                float gf = g * inv;
                int bgOff = SIZE * (g + bOff);
                for (int r = 0; r < SIZE; r++) {
                    pixels[r + bgOff] = RawDeveloper.renderDisplayRgb(r * inv, gf, bf, prepared);
                }
            }
        });
        return new PreviewLookLut(pixels);
    }
}
