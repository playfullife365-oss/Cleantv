package com.cleantv.launcher;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Test;

public class BlurTest {
    @Test
    public void flatColorStaysTheSame() {
        int[] pixels = new int[8 * 4];
        Arrays.fill(pixels, 0xFF336699);
        int[] expected = pixels.clone();
        Blur.boxBlur(pixels, 8, 4, 2, 3);
        assertArrayEquals(expected, pixels);
    }

    @Test
    public void brightDotSpreadsToItsNeighbours() {
        int w = 9, h = 9;
        int[] pixels = new int[w * h];
        Arrays.fill(pixels, 0xFF000000);
        pixels[4 * w + 4] = 0xFFFFFFFF;
        Blur.boxBlur(pixels, w, h, 1, 1);
        int centre = pixels[4 * w + 4] & 0xFF;
        int neighbour = pixels[4 * w + 5] & 0xFF;
        int far = pixels[0] & 0xFF;
        assertTrue(centre < 255);
        assertTrue(neighbour > 0);
        assertEquals(0, far);
        // Alpha stays opaque.
        assertEquals(0xFF, pixels[4 * w + 5] >>> 24);
    }

    @Test
    public void channelsBlurSeparately() {
        int[] pixels = {0xFFFF0000, 0xFF0000FF};
        Blur.boxBlur(pixels, 2, 1, 1, 1);
        // Each pixel averages itself, its neighbour and its repeated edge: red and blue never mix into green.
        assertEquals(0, (pixels[0] >> 8) & 0xFF);
        assertTrue(((pixels[0] >> 16) & 0xFF) > (pixels[0] & 0xFF));
        assertTrue(((pixels[1] >> 16) & 0xFF) < (pixels[1] & 0xFF));
    }
}
