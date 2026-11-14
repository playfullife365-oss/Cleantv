package com.cleantv.speed;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * A round speed dial: a 240° track marked 0 to 1000 Mbps, an arc that fills to the current speed in the phase's
 * colour, and the figure, unit and phase name in the middle. Moves to each new reading smoothly.
 */
public final class GaugeView extends View {
    private static final float START_ANGLE = 150f;
    private static final float SWEEP = 240f;

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knob = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint marks = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint value = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint unit = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private final String[] markText = new String[SpeedMath.SCALE.length];
    private final float[] markX = new float[SpeedMath.SCALE.length];
    private final float[] markY = new float[SpeedMath.SCALE.length];

    private float centreX;
    private float centreY;
    private float radius;
    /** The fraction of the dial drawn now; animates towards the latest reading. */
    private float shown;
    private ValueAnimator animator;
    private String valueText = "";
    private String unitText = "";
    private String labelText = "";

    public GaugeView(Context context) {
        this(context, null);
    }

    public GaugeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeCap(Paint.Cap.ROUND);
        track.setColor(context.getColor(R.color.track));
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeCap(Paint.Cap.ROUND);
        arc.setColor(context.getColor(R.color.download));
        knob.setColor(context.getColor(R.color.text_primary));
        marks.setColor(context.getColor(R.color.text_secondary));
        marks.setTextAlign(Paint.Align.CENTER);
        value.setColor(context.getColor(R.color.text_primary));
        value.setTextAlign(Paint.Align.CENTER);
        value.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        unit.setColor(context.getColor(R.color.text_secondary));
        unit.setTextAlign(Paint.Align.CENTER);
        label.setColor(context.getColor(R.color.download));
        label.setTextAlign(Paint.Align.CENTER);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        for (int i = 0; i < markText.length; i++) markText[i] = String.valueOf((int) SpeedMath.SCALE[i]);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    /** Names the phase above the figure and colours the arc for it. */
    void setPhase(CharSequence name, int color) {
        labelText = name == null ? "" : name.toString();
        label.setColor(color);
        arc.setColor(color);
        updateDescription();
        invalidate();
    }

    /** Shows {@code text} with its unit, and moves the arc to {@code fraction} of the dial. */
    void setReading(String text, String unitName, double fraction) {
        valueText = text;
        unitText = unitName;
        float target = (float) Math.max(0, Math.min(1, fraction));
        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat(shown, target);
        animator.setDuration(300);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            shown = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
        updateDescription();
        invalidate();
    }

    private void updateDescription() {
        setContentDescription((labelText + " " + valueText + " " + unitText).trim());
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Square, as large as the space allows.
        int size = Math.min(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        float size = Math.min(w, h);
        float stroke = size * 0.06f;
        centreX = w / 2f;
        // The dial is open at the bottom, so sit it a little low to look centred.
        centreY = h / 2f + size * 0.06f;
        radius = size / 2f - stroke;
        bounds.set(centreX - radius, centreY - radius, centreX + radius, centreY + radius);
        track.setStrokeWidth(stroke);
        arc.setStrokeWidth(stroke);
        marks.setTextSize(size * 0.042f);
        value.setTextSize(size * 0.2f);
        unit.setTextSize(size * 0.055f);
        label.setTextSize(size * 0.058f);

        // Marks sit just inside the track, centred on their angle.
        float markRadius = radius - stroke * 1.6f;
        float textMiddle = (marks.descent() + marks.ascent()) / 2f;
        int last = markText.length - 1;
        for (int i = 0; i <= last; i++) {
            double angle = Math.toRadians(START_ANGLE + SWEEP * i / last);
            markX[i] = centreX + (float) (Math.cos(angle) * markRadius);
            markY[i] = centreY + (float) (Math.sin(angle) * markRadius) - textMiddle;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawArc(bounds, START_ANGLE, SWEEP, false, track);
        if (shown > 0.001f) {
            float sweep = SWEEP * shown;
            canvas.drawArc(bounds, START_ANGLE, sweep, false, arc);
            double end = Math.toRadians(START_ANGLE + sweep);
            canvas.drawCircle(centreX + (float) (Math.cos(end) * radius), centreY + (float) (Math.sin(end) * radius),
                    track.getStrokeWidth() * 0.28f, knob);
        }
        for (int i = 0; i < markText.length; i++) canvas.drawText(markText[i], markX[i], markY[i], marks);

        float valueSize = value.getTextSize();
        canvas.drawText(labelText, centreX, centreY - valueSize * 0.85f, label);
        canvas.drawText(valueText, centreX, centreY + valueSize * 0.3f, value);
        canvas.drawText(unitText, centreX, centreY + valueSize * 0.3f + unit.getTextSize() * 1.6f, unit);
    }
}
