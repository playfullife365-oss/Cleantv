package com.cleantv.speed;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import java.util.List;
import java.util.Locale;

/**
 * Saved tests at a glance: the latest, average and best speeds and ping, a chart of download and upload over time,
 * every kind of connection the TV has been tested on, and the most recent tests.
 */
public final class DashboardActivity extends Activity {
    private static final int RECENT_ROWS = 8;
    private static final long CONNECTION_REFRESH_MS = 2_000;

    private final Handler main = new Handler(Looper.getMainLooper());
    private HistoryStore store;
    private TextView connection;
    private TextView connectionDetail;
    private final Runnable refreshConnection = new Runnable() {
        @Override
        public void run() {
            ConnectionInfo now = ConnectionReader.read(DashboardActivity.this);
            connection.setText(getString(R.string.now, ConnectionReader.label(DashboardActivity.this, now)));
            connectionDetail.setText(ConnectionReader.detail(DashboardActivity.this, now));
            main.postDelayed(this, CONNECTION_REFRESH_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);
        store = new HistoryStore(this);
        connection = findViewById(R.id.connection);
        connectionDetail = findViewById(R.id.connection_detail);
        ((ImageView) findViewById(R.id.legend_download)).setColorFilter(getColor(R.color.download));
        ((ImageView) findViewById(R.id.legend_upload)).setColorFilter(getColor(R.color.upload));

        // The meter is underneath this screen; going back to it is the quickest way to run a test.
        findViewById(R.id.run_test).setOnClickListener(v -> {
            setResult(RESULT_OK);
            finish();
        });
        findViewById(R.id.clear_history).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setMessage(R.string.clear_confirm)
                .setPositiveButton(R.string.clear, (dialog, which) -> {
                    store.clear();
                    render();
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
        render();
        findViewById(R.id.run_test).requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        main.post(refreshConnection);
    }

    @Override
    protected void onPause() {
        main.removeCallbacks(refreshConnection);
        super.onPause();
    }

    private void render() {
        List<TestRecord> records = store.load();
        boolean empty = records.isEmpty();
        findViewById(R.id.empty).setVisibility(empty ? View.VISIBLE : View.GONE);
        findViewById(R.id.content).setVisibility(empty ? View.GONE : View.VISIBLE);
        findViewById(R.id.clear_history).setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty) return;

        History.Summary down = History.download(records);
        History.Summary up = History.upload(records);
        History.Summary ping = History.ping(records);
        kpi(R.id.kpi_download, R.string.download, R.color.download, down, R.string.mbps);
        kpi(R.id.kpi_upload, R.string.upload, R.color.upload, up, R.string.mbps);
        kpi(R.id.kpi_ping, R.string.ping, R.color.ping, ping, R.string.ms);

        View tests = findViewById(R.id.kpi_tests);
        tests.findViewById(R.id.kpi_dot).setVisibility(View.GONE);
        ((TextView) tests.findViewById(R.id.kpi_name)).setText(R.string.tests);
        ((TextView) tests.findViewById(R.id.kpi_value)).setText(String.valueOf(records.size()));
        ((TextView) tests.findViewById(R.id.kpi_unit)).setText(null);
        ((TextView) tests.findViewById(R.id.kpi_detail)).setText(getString(R.string.since,
                DateUtils.formatDateTime(this, records.get(0).time,
                        DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH)));

        ((HistoryChart) findViewById(R.id.chart)).setRecords(records);
        fillConnections(records);
        fillRecent(records);
    }

    /** One headline card: the latest figure, with the average and best below. */
    private void kpi(int id, int name, int color, History.Summary summary, int unit) {
        View card = findViewById(id);
        ((ImageView) card.findViewById(R.id.kpi_dot)).setColorFilter(getColor(color));
        ((TextView) card.findViewById(R.id.kpi_name)).setText(name);
        ((TextView) card.findViewById(R.id.kpi_value)).setText(figure(summary.latest));
        ((TextView) card.findViewById(R.id.kpi_unit)).setText(unit);
        ((TextView) card.findViewById(R.id.kpi_detail)).setText(summary.count == 0 ? null
                : getString(R.string.avg_best, figure(summary.average), figure(summary.best)));
    }

    private void fillConnections(List<TestRecord> records) {
        TableLayout table = findViewById(R.id.connections_table);
        table.removeAllViews();
        table.addView(row(true, getString(R.string.col_connection), getString(R.string.col_tests),
                getString(R.string.col_down), getString(R.string.col_up), getString(R.string.col_ping),
                getString(R.string.col_signal)));
        for (History.ConnectionSummary summary : History.byConnection(records)) {
            String signal = Double.isNaN(summary.rssi) ? getString(R.string.no_value)
                    : String.valueOf(Math.round(summary.rssi));
            table.addView(row(false, ConnectionReader.label(this, summary.latest), String.valueOf(summary.count),
                    figure(summary.download), figure(summary.upload), figure(summary.ping), signal));
        }
    }

    private void fillRecent(List<TestRecord> records) {
        TableLayout table = findViewById(R.id.recent_table);
        table.removeAllViews();
        table.addView(row(true, getString(R.string.col_when), getString(R.string.col_connection),
                getString(R.string.col_down), getString(R.string.col_up), getString(R.string.col_ping)));
        int flags = DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_ABBREV_MONTH;
        for (int i = records.size() - 1; i >= Math.max(0, records.size() - RECENT_ROWS); i--) {
            TestRecord record = records.get(i);
            table.addView(row(false, DateUtils.formatDateTime(this, record.time, flags),
                    ConnectionReader.label(this, record.connection),
                    figure(record.download), figure(record.upload), figure(record.ping)));
        }
    }

    /** A table row: text cells, the numeric ones right-aligned so the figures line up. */
    private TableRow row(boolean header, String... cells) {
        TableRow row = new TableRow(this);
        int pad = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6,
                getResources().getDisplayMetrics()));
        for (int i = 0; i < cells.length; i++) {
            TextView cell = new TextView(this);
            cell.setText(cells[i]);
            cell.setTextSize(TypedValue.COMPLEX_UNIT_SP, header ? 13 : 15);
            cell.setTextColor(getColor(header ? R.color.text_secondary : R.color.text_primary));
            cell.setSingleLine(true);
            cell.setEllipsize(TextUtils.TruncateAt.END);
            boolean numeric = isNumericColumn(cells.length, i);
            cell.setGravity(numeric ? Gravity.END : Gravity.START);
            cell.setPadding(i == 0 ? 0 : pad * 2, pad, 0, pad);
            row.addView(cell);
        }
        return row;
    }

    /** Both tables end in their numeric columns: the connections table after one text column, recent after two. */
    private static boolean isNumericColumn(int columns, int index) {
        int textColumns = columns == 6 ? 1 : 2;
        return index >= textColumns;
    }

    private String figure(double value) {
        return Double.isNaN(value) ? getString(R.string.no_value) : SpeedMath.format(value, Locale.getDefault());
    }
}
