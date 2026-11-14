package com.cleantv.speed;

import java.util.Arrays;
import java.util.Locale;

/** The arithmetic behind the meter, kept free of Android so it can be unit tested. */
final class SpeedMath {
    /** The dial's marks in Mbps. The spacing between marks is even, so slow and fast connections both read well. */
    static final double[] SCALE = {0, 5, 10, 25, 50, 100, 250, 500, 1000};

    private SpeedMath() {}

    /** Megabits per second for {@code bytes} moved in {@code nanos} nanoseconds. */
    static double mbps(long bytes, long nanos) {
        if (bytes <= 0 || nanos <= 0) return 0;
        return bytes * 8_000.0 / nanos;
    }

    /** Where {@code mbps} sits on the dial, from 0 (empty) to 1 (full), linear between neighbouring marks. */
    static double dialFraction(double mbps) {
        if (!(mbps > 0)) return 0;
        int last = SCALE.length - 1;
        if (mbps >= SCALE[last]) return 1;
        int i = 0;
        while (mbps >= SCALE[i + 1]) i++;
        double within = (mbps - SCALE[i]) / (SCALE[i + 1] - SCALE[i]);
        return (i + within) / last;
    }

    /** The middle value, or the mean of the two middle values. 0 for no samples. */
    static double median(double[] samples) {
        if (samples.length == 0) return 0;
        double[] sorted = samples.clone();
        Arrays.sort(sorted);
        int mid = sorted.length / 2;
        return sorted.length % 2 == 1 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2;
    }

    /** Jitter as the mean difference between consecutive ping samples. 0 for fewer than two samples. */
    static double jitter(double[] samples) {
        if (samples.length < 2) return 0;
        double sum = 0;
        for (int i = 1; i < samples.length; i++) sum += Math.abs(samples[i] - samples[i - 1]);
        return sum / (samples.length - 1);
    }

    /** One decimal below 10, whole numbers above, so the figure doesn't jitter in its last digit. */
    static String format(double value, Locale locale) {
        if (!(value > 0)) return "0";
        return value < 10 ? String.format(locale, "%.1f", value) : String.format(locale, "%.0f", value);
    }

    /** 0 (very weak) to 4 (excellent) for a Wi-Fi signal in dBm. */
    static int signalLevel(int rssi) {
        if (rssi >= -55) return 4;
        if (rssi >= -67) return 3;
        if (rssi >= -75) return 2;
        if (rssi >= -85) return 1;
        return 0;
    }

    /** "2.4 GHz", "5 GHz" or "6 GHz" for a channel frequency in MHz, or null when unknown. */
    static String band(int mhz) {
        if (mhz >= 2400 && mhz < 2500) return "2.4 GHz";
        if (mhz >= 4900 && mhz < 5925) return "5 GHz";
        if (mhz >= 5925 && mhz <= 7125) return "6 GHz";
        return null;
    }

    /** The marketing name for a {@code ScanResult.WIFI_STANDARD_*} value, or null for older or unknown ones. */
    static String wifiGeneration(int standard) {
        switch (standard) {
            case 4: return "Wi-Fi 4";  // 802.11n
            case 5: return "Wi-Fi 5";  // 802.11ac
            case 6: return "Wi-Fi 6";  // 802.11ax
            case 8: return "Wi-Fi 7";  // 802.11be
            default: return null;
        }
    }
}
