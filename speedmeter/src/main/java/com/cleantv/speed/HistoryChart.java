package com.cleantv.speed;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.format.DateUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Download and upload speed over the last tests, as two lines on one Mbps axis. Tests are evenly spaced, oldest on
 * the left. Each line is named at its end. When focused, left and right move a cursor from test to test, with a
 * card giving that test's figures.
 */
public final class HistoryChart extends View {
    /** How many tests the chart shows. */
    static final int SHOWN = 30;
    private static final int GRID_LINES = 4;

    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint endText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint downLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint upLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint downDot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint upDot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cursor = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint card = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cardTitle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cardText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path downPath = new Path();
    private final Path upPath = new Path();
    private final RectF plot = new RectF();
    private final RectF cardBox = new RectF();

    private List<TestRecord> records = new ArrayList<>();
    private double ceiling = 1;
    private final String[] gridLabels = new String[GRID_LINES + 1];
    private String firstDate = "";
    private String lastDate = "";
    private String downName = "";
    private String upName = "";
    private float downEndY;
    private float upEndY;
    /** The test under the cursor. */
    private int selected = -1;
    private String[] cardLines = new String[0];

    public HistoryChart(Context context) {
        this(context, null);
    }

    public HistoryChart(Context context, AttributeSet attrs) {
        super(context, attrs);
        float dp = getResources().getDisplayMetrics().density;
        int surface = context.getColor(R.color.chart_surface);
        int primary = context.getColor(R.color.text_primary);
        int secondary = context.getColor(R.color.text_secondary);

        grid.setColor(context.getColor(R.color.grid));
        grid.setStrokeWidth(Math.max(1, dp));
        axisText.setColor(secondary);
        axisText.setTextSize(sp(12));
        endText.setColor(primary);
        endText.setTextSize(sp(13));
        for (Paint line : new Paint[] {downLine, upLine}) {
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(2 * dp);
            line.setStrokeJoin(Paint.Join.ROUND);
            line.setStrokeCap(Paint.Cap.ROUND);
        }
        downLine.setColor(context.getColor(R.color.download));
        upLine.setColor(context.getColor(R.color.upload));
        downDot.setColor(context.getColor(R.color.download));
        upDot.setColor(context.getColor(R.color.upload));
        ring.setColor(surface);
        cursor.setColor(secondary);
        cursor.setStrokeWidth(Math.max(1, dp));
        card.setColor(context.getColor(R.color.tooltip));
        cardTitle.setColor(primary);
        cardTitle.setTextSize(sp(13));
        cardTitle.setFakeBoldText(true);
        cardText.setColor(primary);
        cardText.setTextSize(sp(13));

        downName = context.getString(R.string.download);
        upName = context.getString(R.string.upload);
        setFocusable(true);
        setBackgroundResource(R.drawable.chart_background);
    }

    /** Shows the last {@link #SHOWN} of {@code all} (oldest first), with the cursor on the newest. */
    void setRecords(List<TestRecord> all) {
        records = new ArrayList<>(all.subList(Math.max(0, all.size() - SHOWN), all.size()));
        double max = 0;
        for (TestRecord record : records) {
            if (!Double.isNaN(record.download)) max = Math.max(max, record.download);
            if (!Double.isNaN(record.upload)) max = Math.max(max, record.upload);
        }
        ceiling = History.niceCeiling(max);
        for (int i = 0; i <= GRID_LINES; i++) {
            gridLabels[i] = SpeedMath.format(ceiling * i / GRID_LINES, Locale.getDefault());
        }
        firstDate = records.isEmpty() ? "" : date(records.get(0).time, false);
        lastDate = records.size() < 2 ? "" : date(records.get(records.size() - 1).time, false);
        select(records.size() - 1);
        layoutPlot();
        setContentDescription(getContext().getString(R.string.chart_description, records.size()));
    }

    private String date(long time, boolean withTime) {
        int flags = DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH;
        if (withTime) flags |= DateUtils.FORMAT_SHOW_TIME;
        return DateUtils.formatDateTime(getContext(), time, flags);
    }

    private void select(int index) {
        selected = index;
        if (index < 0 || index >= records.size()) {
            cardLines = new String[0];
        } else {
            TestRecord r = records.get(index);
            Context c = getContext();
            cardLines = new String[] {
                date(r.time, true),
                c.getString(R.string.chart_card_line, downName, figure(r.download), c.getString(R.string.mbps)),
                c.getString(R.string.chart_card_line, upName, figure(r.upload), c.getString(R.string.mbps)),
                c.getString(R.string.chart_card_line, c.getString(R.string.ping), figure(r.ping),
                        c.getString(R.string.ms)),
                ConnectionReader.label(c, r.connection),
            };
            setContentDescription(String.join(", ", cardLines));
        }
        invalidate();
    }

    private float sp(int value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, getResources().getDisplayMetrics());
    }

    private String figure(double value) {
        return Double.isNaN(value) ? getContext().getString(R.string.no_value)
                : SpeedMath.format(value, Locale.getDefault());
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Step through the tests; at either end, let focus move on.
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && selected > 0) {
            select(selected - 1);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && selected < records.size() - 1) {
            select(selected + 1);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        layoutPlot();
    }

    /** Works out the plot area and the two lines. Called when the size or the data changes, never while drawing. */
    private void layoutPlot() {
        if (getWidth() == 0) return;
        float dp = getResources().getDisplayMetrics().density;
        float labelWidth = 0;
        for (String label : gridLabels) {
            if (label != null) labelWidth = Math.max(labelWidth, axisText.measureText(label));
        }
        float nameWidth = Math.max(endText.measureText(downName), endText.measureText(upName));
        float pad = 16 * dp;
        plot.set(getPaddingLeft() + pad + labelWidth + 10 * dp,
                getPaddingTop() + pad,
                getWidth() - getPaddingRight() - pad - nameWidth - 12 * dp,
                getHeight() - getPaddingBottom() - pad - axisText.getTextSize() - 8 * dp);

        downEndY = buildLine(downPath, true);
        upEndY = buildLine(upPath, false);
        // Keep the two end names from overlapping when the lines finish close together.
        float minGap = endText.getTextSize() * 1.2f;
        if (!Float.isNaN(downEndY) && !Float.isNaN(upEndY) && Math.abs(downEndY - upEndY) < minGap) {
            float middle = (downEndY + upEndY) / 2;
            boolean downAbove = downEndY <= upEndY;
            downEndY = middle + (downAbove ? -minGap / 2 : minGap / 2);
            upEndY = middle + (downAbove ? minGap / 2 : -minGap / 2);
        }
    }

    float x(int index) {
        if (records.size() < 2) return plot.centerX();
        return plot.left + plot.width() * index / (records.size() - 1);
    }

    float y(double mbps) {
        return plot.bottom - (float) (plot.height() * Math.min(1, mbps / ceiling));
    }

    /** Builds one series' line, breaking it where a test has no figure. Returns the last point's y, or NaN. */
    private float buildLine(Path path, boolean download) {
        path.reset();
        boolean drawing = false;
        float lastY = Float.NaN;
        for (int i = 0; i < records.size(); i++) {
            double value = download ? records.get(i).download : records.get(i).upload;
            if (Double.isNaN(value)) {
                drawing = false;
                continue;
            }
            float px = x(i);
            float py = y(value);
            if (drawing) path.lineTo(px, py); else path.moveTo(px, py);
            drawing = true;
            lastY = py;
        }
        return lastY;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float dp = getResources().getDisplayMetrics().density;
        // Recessive grid with its Mbps labels.
        float textMiddle = (axisText.descent() + axisText.ascent()) / 2;
        for (int i = 0; i <= GRID_LINES; i++) {
            float gy = plot.bottom - plot.height() * i / GRID_LINES;
            canvas.drawLine(plot.left, gy, plot.right, gy, grid);
            axisText.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(gridLabels[i], plot.left - 10 * dp, gy - textMiddle, axisText);
        }
        float dateY = plot.bottom + 8 * dp + axisText.getTextSize();
        axisText.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(firstDate, plot.left, dateY, axisText);
        axisText.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(lastDate, plot.right, dateY, axisText);

        canvas.drawPath(downPath, downLine);
        canvas.drawPath(upPath, upLine);
        // A single test has no line to draw, so mark its points.
        if (records.size() == 1) {
            drawPoint(canvas, 0, records.get(0).download, downDot, dp);
            drawPoint(canvas, 0, records.get(0).upload, upDot, dp);
        }

        // Each line named at its end, in text ink beside a dot of its colour.
        endText.setTextAlign(Paint.Align.LEFT);
        float nameMiddle = (endText.descent() + endText.ascent()) / 2;
        float nameX = plot.right + 12 * dp;
        if (!Float.isNaN(downEndY)) {
            canvas.drawCircle(nameX - 6 * dp, downEndY, 3 * dp, downDot);
            canvas.drawText(downName, nameX, downEndY - nameMiddle, endText);
        }
        if (!Float.isNaN(upEndY)) {
            canvas.drawCircle(nameX - 6 * dp, upEndY, 3 * dp, upDot);
            canvas.drawText(upName, nameX, upEndY - nameMiddle, endText);
        }

        if (isFocused() && selected >= 0 && selected < records.size()) drawCursor(canvas, dp);
    }

    /** An 8dp marker with a 2dp ring in the chart's background colour, so it stands clear of the lines. */
    private void drawPoint(Canvas canvas, int index, double value, Paint fill, float dp) {
        if (Double.isNaN(value)) return;
        canvas.drawCircle(x(index), y(value), 6 * dp, ring);
        canvas.drawCircle(x(index), y(value), 4 * dp, fill);
    }

    private void drawCursor(Canvas canvas, float dp) {
        float cx = x(selected);
        canvas.drawLine(cx, plot.top, cx, plot.bottom, cursor);
        TestRecord record = records.get(selected);
        drawPoint(canvas, selected, record.download, downDot, dp);
        drawPoint(canvas, selected, record.upload, upDot, dp);

        float lineHeight = cardText.getTextSize() * 1.45f;
        float pad = 10 * dp;
        float width = 0;
        for (int i = 0; i < cardLines.length; i++) {
            Paint paint = i == 0 ? cardTitle : cardText;
            width = Math.max(width, paint.measureText(cardLines[i]));
        }
        float cardWidth = width + 2 * pad;
        float cardHeight = lineHeight * cardLines.length + 2 * pad - (lineHeight - cardText.getTextSize());
        // Beside the cursor, on whichever side has room, and inside the plot.
        float left = cx + 14 * dp;
        if (left + cardWidth > plot.right) left = cx - 14 * dp - cardWidth;
        left = Math.max(plot.left, left);
        float top = plot.top;
        cardBox.set(left, top, left + cardWidth, top + cardHeight);
        canvas.drawRoundRect(cardBox, 8 * dp, 8 * dp, card);
        float baseline = top + pad + cardText.getTextSize() * 0.8f;
        for (int i = 0; i < cardLines.length; i++) {
            canvas.drawText(cardLines[i], left + pad, baseline + i * lineHeight, i == 0 ? cardTitle : cardText);
        }
    }
}
