package com.cleantv.launcher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import java.util.HashMap;
import java.util.Map;

/** Blurred backdrops made from app banners or icons, cached per app. */
final class Backdrops {
    private static final int WIDTH = 64;
    private static final int HEIGHT = 36;

    private final Map<String, Bitmap> cache = new HashMap<>();

    Bitmap get(AppEntry app) {
        Bitmap bitmap = cache.get(app.key);
        if (bitmap == null) {
            bitmap = blurred(app.banner != null ? app.banner : app.icon);
            cache.put(app.key, bitmap);
        }
        return bitmap;
    }

    void clear() {
        cache.clear();
    }

    private static Bitmap blurred(Drawable drawable) {
        Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        // The drawable is also shown by a tile: draw it at our size, then put its bounds back.
        Rect bounds = drawable.copyBounds();
        drawable.setBounds(cover(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight()));
        drawable.draw(new Canvas(bitmap));
        drawable.setBounds(bounds);

        int[] pixels = new int[WIDTH * HEIGHT];
        bitmap.getPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT);
        Blur.boxBlur(pixels, WIDTH, HEIGHT, 3, 3);
        bitmap.setPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT);
        return bitmap;
    }

    /** Bounds that scale an image of the given size to fill the bitmap, cropping what overflows. */
    private static Rect cover(int width, int height) {
        if (width <= 0 || height <= 0) return new Rect(0, 0, WIDTH, HEIGHT);
        float scale = Math.max((float) WIDTH / width, (float) HEIGHT / height);
        int w = Math.round(width * scale);
        int h = Math.round(height * scale);
        int left = (WIDTH - w) / 2;
        int top = (HEIGHT - h) / 2;
        return new Rect(left, top, left + w, top + h);
    }
}
