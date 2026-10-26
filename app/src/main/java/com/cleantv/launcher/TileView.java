package com.cleantv.launcher;

import android.content.Context;
import android.content.res.Resources;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A 16:9 app tile: the app's TV banner, or its icon and name when it has no banner. Grows when focused. */
final class TileView extends FrameLayout {
    private static final float FOCUSED_SCALE = 1.08f;
    private static final int ANIMATION_MS = 150;

    final AppEntry app;

    TileView(Context context, AppEntry app) {
        super(context);
        this.app = app;
        Resources res = context.getResources();
        setFocusable(true);
        setClickable(true);
        setLongClickable(true);
        setBackgroundResource(R.drawable.tile_background);
        setClipToOutline(true);
        setContentDescription(app.label);

        if (app.banner != null) {
            ImageView banner = new ImageView(context);
            banner.setImageDrawable(app.banner);
            banner.setScaleType(ImageView.ScaleType.CENTER_CROP);
            int ring = dp(3);
            banner.setPadding(ring, ring, ring, ring);
            addView(banner, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        } else {
            LinearLayout column = new LinearLayout(context);
            column.setOrientation(LinearLayout.VERTICAL);
            column.setGravity(Gravity.CENTER);
            int pad = dp(8);
            column.setPadding(pad, pad, pad, pad);

            ImageView icon = new ImageView(context);
            icon.setImageDrawable(app.icon);
            int size = res.getDimensionPixelSize(R.dimen.tile_icon);
            column.addView(icon, new LinearLayout.LayoutParams(size, size));

            TextView label = new TextView(context);
            label.setText(app.label);
            label.setTextColor(context.getColor(R.color.text_primary));
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(6);
            column.addView(label, lp);

            addView(column, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        }

        setOnFocusChangeListener((v, focused) -> animate()
                .scaleX(focused ? FOCUSED_SCALE : 1f)
                .scaleY(focused ? FOCUSED_SCALE : 1f)
                .translationZ(focused ? dp(8) : 0)
                .setDuration(ANIMATION_MS)
                .start());
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = width * 9 / 16;
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
