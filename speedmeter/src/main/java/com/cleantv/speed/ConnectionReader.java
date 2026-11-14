package com.cleantv.speed;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;

/** Reads how the TV is connected right now, and puts it into words. */
final class ConnectionReader {
    private ConnectionReader() {}

    @SuppressWarnings("deprecation") // WifiManager.getConnectionInfo still works, and needs no location permission.
    static ConnectionInfo read(Context context) {
        ConnectivityManager connectivity = context.getSystemService(ConnectivityManager.class);
        Network network = connectivity == null ? null : connectivity.getActiveNetwork();
        NetworkCapabilities caps = network == null ? null : connectivity.getNetworkCapabilities(network);
        if (caps == null) return new ConnectionInfo(ConnectionInfo.NONE, null, null, 0, 0);
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            return new ConnectionInfo(ConnectionInfo.ETHERNET, null, null, 0, 0);
        }
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return new ConnectionInfo(ConnectionInfo.OTHER, null, null, 0, 0);
        }

        WifiManager wifi = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiInfo info = wifi == null ? null : wifi.getConnectionInfo();
        if (info == null) return new ConnectionInfo(ConnectionInfo.WIFI, null, null, 0, 0);
        String generation = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                ? SpeedMath.wifiGeneration(info.getWifiStandard()) : null;
        int rssi = info.getRssi();
        return new ConnectionInfo(ConnectionInfo.WIFI, SpeedMath.band(info.getFrequency()), generation,
                // -127 means "no reading".
                rssi < 0 && rssi > -127 ? rssi : 0, Math.max(0, info.getLinkSpeed()));
    }

    /** "Wi-Fi · 5 GHz · Wi-Fi 6", "Ethernet", and so on. */
    static String label(Context context, ConnectionInfo info) {
        switch (info.kind) {
            case ConnectionInfo.NONE: return context.getString(R.string.offline);
            case ConnectionInfo.ETHERNET: return context.getString(R.string.ethernet);
            case ConnectionInfo.OTHER: return context.getString(R.string.other_network);
            default:
                StringBuilder text = new StringBuilder(context.getString(R.string.wifi));
                if (info.band != null) text.append(" · ").append(info.band);
                if (info.generation != null) text.append(" · ").append(info.generation);
                return text.toString();
        }
    }

    /** Signal and link rate on Wi-Fi, a short note otherwise. Null when there's nothing to say. */
    static String detail(Context context, ConnectionInfo info) {
        switch (info.kind) {
            case ConnectionInfo.NONE: return context.getString(R.string.offline_detail);
            case ConnectionInfo.ETHERNET: return context.getString(R.string.ethernet_detail);
            case ConnectionInfo.OTHER: return null;
            default:
                if (info.rssi >= 0) return null;
                String level = signalName(context, info.rssi);
                return info.linkMbps > 0
                        ? context.getString(R.string.signal_link, level, info.rssi, info.linkMbps)
                        : context.getString(R.string.signal_only, level, info.rssi);
        }
    }

    static String signalName(Context context, int rssi) {
        return context.getResources().getStringArray(R.array.signal_levels)[SpeedMath.signalLevel(rssi)];
    }
}
