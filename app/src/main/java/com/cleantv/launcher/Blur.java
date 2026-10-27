package com.cleantv.launcher;

/**
 * A box blur on ARGB pixels. Several passes approximate a Gaussian blur. Used on a tiny bitmap that is then
 * scaled up, so it stays cheap on slow TV boxes that predate Android 12's RenderEffect.
 */
final class Blur {
    private Blur() {}

    static void boxBlur(int[] pixels, int width, int height, int radius, int passes) {
        int[] tmp = new int[pixels.length];
        for (int p = 0; p < passes; p++) {
            for (int y = 0; y < height; y++) blurLine(pixels, tmp, y * width, 1, width, radius);
            for (int x = 0; x < width; x++) blurLine(tmp, pixels, x, width, height, radius);
        }
    }

    /** Blurs one row or column of {@code length} pixels, repeating the edge pixels past either end. */
    private static void blurLine(int[] src, int[] dst, int start, int step, int length, int radius) {
        int window = 2 * radius + 1;
        for (int i = 0; i < length; i++) {
            int a = 0, r = 0, g = 0, b = 0;
            for (int k = -radius; k <= radius; k++) {
                int j = Math.max(0, Math.min(length - 1, i + k));
                int c = src[start + j * step];
                a += c >>> 24;
                r += (c >> 16) & 0xFF;
                g += (c >> 8) & 0xFF;
                b += c & 0xFF;
            }
            dst[start + i * step] = (a / window) << 24 | (r / window) << 16 | (g / window) << 8 | (b / window);
        }
    }
}
