package com.cleantv.speed;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shows how the TV is connected (Wi-Fi band, signal and link rate, or Ethernet) and runs a speed test: ping and
 * jitter, then download and upload, with the dial following along.
 */
public final class MainActivity extends Activity implements SpeedTest.Listener {
    private static final long CONNECTION_REFRESH_MS = 2_000;

    private final Handler main = new Handler(Looper.getMainLooper());
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
    /** The Wi-Fi link rate seen during the test, in Mbps, or 0 when not on Wi-Fi. */
    private int linkMbps;
    /** This run's download result in Mbps, or -1 before there is one. */
    private double downloadMbps = -1;

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
        gauge.setPhase(getString(R.string.ready), getColor(R.color.download));
        gauge.setReading("0", getString(R.string.mbps), 0);
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
        linkMbps = 0;
        downloadMbps = -1;
        showConnection();
        status.setText(R.string.testing);
        start.setText(R.string.stop);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        test = new SpeedTest(SpeedTest.CLOUDFLARE, new SpeedTest.Settings(), this, main::post);
        test.start();
    }

    private void stop() {
        test.cancel();
        end(getString(R.string.stopped));
        gauge.setPhase(getString(R.string.ready), getColor(R.color.download));
        gauge.setReading("0", getString(R.string.mbps), 0);
    }

    private void end(CharSequence message) {
        test = null;
        status.setText(message);
        start.setText(R.string.again);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
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
    public void onResult(SpeedTest.Phase phase, double value, double jitterMs) {
        switch (phase) {
            case PING:
                ping.setText(format(value));
                jitter.setText(format(jitterMs));
                break;
            case DOWNLOAD:
                downloadMbps = value;
                download.setText(format(value));
                gauge.setReading(format(value), getString(R.string.mbps), SpeedMath.dialFraction(value));
                break;
            default:
                upload.setText(format(value));
                gauge.setReading(format(value), getString(R.string.mbps), SpeedMath.dialFraction(value));
        }
    }

    @Override
    public void onFinished(IOException error) {
        CharSequence message;
        if (error != null) {
            message = getString(R.string.failed);
        } else if (linkMbps > 0) {
            message = getString(R.string.finished_link, linkMbps);
        } else {
            message = getString(R.string.finished);
        }
        end(message);
        // Leave the download figure on the dial: it's the number most people came for.
        if (downloadMbps >= 0) {
            gauge.setPhase(getString(R.string.download), getColor(R.color.download));
            gauge.setReading(format(downloadMbps), getString(R.string.mbps), SpeedMath.dialFraction(downloadMbps));
        }
    }

    private static String format(double value) {
        return SpeedMath.format(value, Locale.getDefault());
    }

    /** Fills in the connection lines from the active network. */
    @SuppressWarnings("deprecation") // WifiManager.getConnectionInfo still works, and needs no location permission.
    private void showConnection() {
        ConnectivityManager connectivity = getSystemService(ConnectivityManager.class);
        Network network = connectivity == null ? null : connectivity.getActiveNetwork();
        NetworkCapabilities caps = network == null ? null : connectivity.getNetworkCapabilities(network);
        if (caps == null) {
            connection.setText(R.string.offline);
            connectionDetail.setText(R.string.offline_detail);
            return;
        }
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            connection.setText(R.string.ethernet);
            connectionDetail.setText(R.string.ethernet_detail);
            return;
        }
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            connection.setText(R.string.other_network);
            connectionDetail.setText(null);
            return;
        }

        WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiInfo info = wifi == null ? null : wifi.getConnectionInfo();
        List<String> parts = new ArrayList<>();
        parts.add(getString(R.string.wifi));
        if (info == null) {
            connection.setText(getString(R.string.wifi));
            connectionDetail.setText(null);
            return;
        }
        String band = SpeedMath.band(info.getFrequency());
        if (band != null) parts.add(band);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            String generation = SpeedMath.wifiGeneration(info.getWifiStandard());
            if (generation != null) parts.add(generation);
        }
        connection.setText(String.join(" · ", parts));

        int rssi = info.getRssi();
        String level = getResources().getStringArray(R.array.signal_levels)[SpeedMath.signalLevel(rssi)];
        int link = info.getLinkSpeed();
        if (link > 0) {
            connectionDetail.setText(getString(R.string.signal_link, level, rssi, link));
            if (test != null) linkMbps = Math.max(linkMbps, link);
        } else {
            connectionDetail.setText(getString(R.string.signal_only, level, rssi));
        }
    }
}
