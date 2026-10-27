package com.cleantv.launcher;

import android.content.Context;
import android.graphics.Rect;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * An app tile in the style of Apple TV: a rounded 16:9 card showing the app's banner (or its icon when it has
 * none), with the name underneath. When focused, the card lifts, tilts towards the direction you moved,
 * settles with a little overshoot, and a highlight sweeps across it.
 */
final class TileView extends LinearLayout {
    private static final float FOCUSED_SCALE = 1.12f;
    private static final float TILT_DEGREES = 6f;
    private static final int FOCUS_MS = 380;
    private static final int UNFOCUS_MS = 180;
    private static final int SHINE_MS = 650;

    final AppEntry app;
    private final FrameLayout card;
    private final View shine;
    private final TextView caption;

    TileView(Context context, AppEntry app) {
        super(context);
        this.app = app;
        setOrientation(VERTICAL);
        setClipChildren(false);
        setClipToPadding(false);
        setFocusable(true);
        setClickable(true);
        setLongClickable(true);
        setContentDescription(app.label);

        card = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int width = MeasureSpec.getSize(widthMeasureSpec);
                super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(width * 9 / 16, MeasureSpec.EXACTLY));
            }
        };
        card.setBackgroundResource(R.drawable.tile_background);
        card.setClipToOutline(true);
        card.setCameraDistance(8000 * getResources().getDisplayMetrics().density);

        ImageView image = new ImageView(context);
        if (app.banner != null) {
            image.setImageDrawable(app.banner);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(image, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        } else {
            image.setImageDrawable(app.icon);
            int size = getResources().getDimensionPixelSize(R.dimen.tile_icon);
            card.addView(image, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
        }

        shine = new View(context);
        shine.setBackgroundResource(R.drawable.tile_shine);
        shine.setAlpha(0f);
        card.addView(shine, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        addView(card, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        caption = new TextView(context);
        caption.setText(app.label);
        caption.setTextColor(context.getColor(R.color.text_primary));
        caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        caption.setSingleLine(true);
        caption.setEllipsize(TextUtils.TruncateAt.END);
        caption.setGravity(Gravity.CENTER_HORIZONTAL);
        caption.setAlpha(0f);
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        // Room below the card for it to grow into.
        lp.topMargin = dp(12);
        addView(caption, lp);
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        card.animate().cancel();
        shine.animate().cancel();
        if (gainFocus) {
            // Start tilted as if pushed by the move, then spring back flat.
            card.setRotationY(direction == FOCUS_RIGHT ? -TILT_DEGREES : direction == FOCUS_LEFT ? TILT_DEGREES : 0);
            card.setRotationX(direction == FOCUS_DOWN ? TILT_DEGREES : direction == FOCUS_UP ? -TILT_DEGREES : 0);
            card.animate()
                    .scaleX(FOCUSED_SCALE).scaleY(FOCUSED_SCALE)
                    .rotationX(0).rotationY(0)
                    .translationZ(dp(16))
                    .setInterpolator(new OvershootInterpolator(1.5f))
                    .setDuration(FOCUS_MS)
                    .start();

            int width = card.getWidth();
            shine.setTranslationX(-width);
            shine.setAlpha(1f);
            shine.animate()
                    .translationX(width)
                    .setInterpolator(new DecelerateInterpolator())
                    .setDuration(SHINE_MS)
                    .withEndAction(() -> shine.setAlpha(0f))
                    .start();
        } else {
            shine.setAlpha(0f);
            card.animate()
                    .scaleX(1f).scaleY(1f)
                    .rotationX(0).rotationY(0)
                    .translationZ(0)
                    .setInterpolator(new DecelerateInterpolator())
                    .setDuration(UNFOCUS_MS)
                    .start();
        }
        caption.animate()
                .alpha(gainFocus ? 1f : 0f)
                .translationY(gainFocus ? dp(6) : 0)
                .setDuration(gainFocus ? FOCUS_MS : UNFOCUS_MS)
                .start();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
