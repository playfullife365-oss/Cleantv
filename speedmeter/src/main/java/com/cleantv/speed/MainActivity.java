package com.cleantv.speed;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import java.io.IOException;
import java.util.Locale;

/**
 * Shows how the TV is connected (Wi-Fi band, signal and link rate, or Ethernet) and runs a speed test: ping and
 * jitter, then download and upload, with the dial following along. Each finished test is saved for the dashboard.
 */
public final class MainActivity extends Activity implements SpeedTest.Listener {
    private static final long CONNECTION_REFRESH_MS = 2_000;
    private static final int REQUEST_DASHBOARD = 1;

    private final Handler main = new Handler(Looper.getMainLooper());
    private HistoryStore history;
    private GaugeView gauge;
    private TextView connection;
    private TextView connectionDetail;
    private TextView download;
    private TextView upload;
    private TextView ping;
    private TextView jitter;
    private TextView start;
    private TextView status;
    private SpeedTest test;

    // This run's results, NaN until each phase gives one.
    private double downloadMbps;
    private double uploadMbps;
    private double pingMs;
    private double jitterMs;
    /** The connection during this run, with the highest Wi-Fi link rate seen. */
    private ConnectionInfo testConnection;

    private final Runnable refreshConnection = new Runnable() {
        @Override
        public void run() {
            showConnection();
            main.postDelayed(this, CONNECTION_REFRESH_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        history = new HistoryStore(this);
        gauge = findViewById(R.id.gauge);
        connection = findViewById(R.id.connection);
        connectionDetail = findViewById(R.id.connection_detail);
        download = stat(R.id.download, R.string.download, R.string.mbps, R.color.download);
        upload = stat(R.id.upload, R.string.upload, R.string.mbps, R.color.upload);
        ping = stat(R.id.ping, R.string.ping, R.string.ms, R.color.ping);
        jitter = stat(R.id.jitter, R.string.jitter, R.string.ms, R.color.ping);
        start = findViewById(R.id.start);
        status = findViewById(R.id.status);

        start.setOnClickListener(v -> {
            if (test != null) stop(); else begin();
        });
        findViewById(R.id.open_dashboard).setOnClickListener(v ->
                startActivityForResult(new Intent(this, DashboardActivity.class), REQUEST_DASHBOARD));
        showIdleDial();
        start.requestFocus();
    }

    /** Fills in one of the result cards and returns its figure. */
    private TextView stat(int id, int name, int unit, int color) {
        View card = findViewById(id);
        ((TextView) card.findViewById(R.id.stat_name)).setText(name);
        ((TextView) card.findViewById(R.id.stat_unit)).setText(unit);
        ((ImageView) card.findViewById(R.id.stat_dot)).setColorFilter(getColor(color));
        return card.findViewById(R.id.stat_value);
    }

    @Override
    protected void onResume() {
        super.onResume();
        main.post(refreshConnection);
    }

    @Override
    protected void onPause() {
        main.removeCallbacks(refreshConnection);
        // Don't keep using data in the background.
        if (test != null) stop();
        super.onPause();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // "Run a test" on the dashboard.
        if (requestCode == REQUEST_DASHBOARD && resultCode == RESULT_OK && test == null) begin();
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        // Play/pause on the remote starts and stops the test from anywhere on the screen.
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) {
            start.performClick();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    private void begin() {
        for (TextView figure : new TextView[] {download, upload, ping, jitter}) figure.setText(R.string.no_value);
        downloadMbps = uploadMbps = pingMs = jitterMs = Double.NaN;
        testConnection = null;
        status.setText(R.string.testing);
        start.setText(R.string.stop);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        test = new SpeedTest(SpeedTest.CLOUDFLARE, new SpeedTest.Settings(), this, main::post);
        showConnection();
        test.start();
    }

    private void stop() {
        test.cancel();
        end(getString(R.string.stopped));
        showIdleDial();
    }

    private void end(CharSequence message) {
        test = null;
        status.setText(message);
        start.setText(R.string.again);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void showIdleDial() {
        gauge.setPhase(getString(R.string.ready), getColor(R.color.download));
        gauge.setReading("0", getString(R.string.mbps), 0);
    }

    @Override
    public void onPhase(SpeedTest.Phase phase) {
        switch (phase) {
            case PING:
                gauge.setPhase(getString(R.string.ping), getColor(R.color.ping));
                gauge.setReading("0", getString(R.string.ms), 0);
                break;
            case DOWNLOAD:
                gauge.setPhase(getString(R.string.download), getColor(R.color.download));
                gauge.setReading("0", getString(R.string.mbps), 0);
                break;
            default:
                gauge.setPhase(getString(R.string.upload), getColor(R.color.upload));
                gauge.setReading("0", getString(R.string.mbps), 0);
        }
    }

    @Override
    public void onLive(SpeedTest.Phase phase, double value) {
        if (phase == SpeedTest.Phase.PING) {
            // The dial is in Mbps, so ping only shows the figure.
            gauge.setReading(format(value), getString(R.string.ms), 0);
        } else {
            gauge.setReading(format(value), getString(R.string.mbps), SpeedMath.dialFraction(value));
        }
    }

    @Override
    public void onResult(SpeedTest.Phase phase, double value, double jitterResult) {
        switch (phase) {
            case PING:
                pingMs = value;
                jitterMs = jitterResult;
                ping.setText(format(value));
                jitter.setText(format(jitterResult));
                break;
            case DOWNLOAD:
                downloadMbps = value;
                download.setText(format(value));
                gauge.setReading(format(value), getString(R.string.mbps), SpeedMath.dialFraction(value));
                break;
            default:
                uploadMbps = value;
                upload.setText(format(value));
                gauge.setReading(format(value), getString(R.string.mbps), SpeedMath.dialFraction(value));
        }
    }

    @Override
    public void onFinished(IOException error) {
        ConnectionInfo used = testConnection != null ? testConnection : ConnectionReader.read(this);
        TestRecord record = new TestRecord(System.currentTimeMillis(), downloadMbps, uploadMbps, pingMs, jitterMs,
                used);
        if (record.hasResult()) history.add(record);

        CharSequence message;
        if (error != null) {
            message = getString(R.string.failed);
        } else if (used.linkMbps > 0) {
            message = getString(R.string.finished_link, used.linkMbps);
        } else {
            message = getString(R.string.finished);
        }
        end(message);
        // Leave the download figure on the dial: it's the number most people came for.
        if (!Double.isNaN(downloadMbps)) {
            gauge.setPhase(getString(R.string.download), getColor(R.color.download));
            gauge.setReading(format(downloadMbps), getString(R.string.mbps), SpeedMath.dialFraction(downloadMbps));
        }
    }

    private static String format(double value) {
        return SpeedMath.format(value, Locale.getDefault());
    }

    /** Fills in the connection lines, and while testing, remembers the connection for the saved result. */
    private void showConnection() {
        ConnectionInfo now = ConnectionReader.read(this);
        connection.setText(ConnectionReader.label(this, now));
        connectionDetail.setText(ConnectionReader.detail(this, now));
        if (test == null) return;
        if (testConnection == null || !testConnection.key().equals(now.key())) {
            testConnection = now;
        } else if (now.linkMbps > testConnection.linkMbps) {
            testConnection = now;
        }
    }
}
