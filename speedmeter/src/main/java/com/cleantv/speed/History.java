package com.cleantv.speed;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/** The figures on the dashboard, worked out from saved tests (oldest first). Pure logic. */
final class History {
    /** How many tests are kept. */
    static final int LIMIT = 100;

    private History() {}

    /** Latest, average and best of one measure, ignoring tests that lack it. NaN when no test has it. */
    static final class Summary {
        final int count;
        final double latest;
        final double average;
        final double best;

        Summary(int count, double latest, double average, double best) {
            this.count = count;
            this.latest = latest;
            this.average = average;
            this.best = best;
        }
    }

    /** One kind of connection the TV has been tested on, with its averages. */
    static final class ConnectionSummary {
        /** The most recent test's connection details. */
        final ConnectionInfo latest;
        final int count;
        final long lastTime;
        final double download;
        final double upload;
        final double ping;
        /** Mean signal in dBm, or NaN when no test had one. */
        final double rssi;

        ConnectionSummary(ConnectionInfo latest, int count, long lastTime, double download, double upload,
                double ping, double rssi) {
            this.latest = latest;
            this.count = count;
            this.lastTime = lastTime;
            this.download = download;
            this.upload = upload;
            this.ping = ping;
            this.rssi = rssi;
        }
    }

    /** {@code records} plus {@code added}, dropping the oldest beyond {@link #LIMIT}. */
    static List<TestRecord> append(List<TestRecord> records, TestRecord added) {
        List<TestRecord> result = new ArrayList<>(records);
        result.add(added);
        while (result.size() > LIMIT) result.remove(0);
        return result;
    }

    static Summary download(List<TestRecord> records) {
        return summarize(records, r -> r.download, true);
    }

    static Summary upload(List<TestRecord> records) {
        return summarize(records, r -> r.upload, true);
    }

    static Summary ping(List<TestRecord> records) {
        return summarize(records, r -> r.ping, false);
    }

    static Summary summarize(List<TestRecord> records, ToDoubleFunction<TestRecord> measure, boolean higherIsBetter) {
        int count = 0;
        double sum = 0;
        double latest = Double.NaN;
        double best = Double.NaN;
        for (TestRecord record : records) {
            double value = measure.applyAsDouble(record);
            if (Double.isNaN(value)) continue;
            count++;
            sum += value;
            latest = value;
            if (Double.isNaN(best) || (higherIsBetter ? value > best : value < best)) best = value;
        }
        return new Summary(count, latest, count == 0 ? Double.NaN : sum / count, best);
    }

    /** One summary per kind of connection, most recently used first. */
    static List<ConnectionSummary> byConnection(List<TestRecord> records) {
        Map<String, List<TestRecord>> groups = new LinkedHashMap<>();
        for (int i = records.size() - 1; i >= 0; i--) {
            TestRecord record = records.get(i);
            groups.computeIfAbsent(record.connection.key(), k -> new ArrayList<>()).add(record);
        }
        List<ConnectionSummary> result = new ArrayList<>();
        for (List<TestRecord> group : groups.values()) {
            // Groups were filled newest first.
            TestRecord newest = group.get(0);
            result.add(new ConnectionSummary(newest.connection, group.size(), newest.time,
                    mean(group, r -> r.download), mean(group, r -> r.upload), mean(group, r -> r.ping),
                    mean(group, r -> r.connection.rssi < 0 ? r.connection.rssi : Double.NaN)));
        }
        return result;
    }

    private static double mean(List<TestRecord> records, ToDoubleFunction<TestRecord> measure) {
        int count = 0;
        double sum = 0;
        for (TestRecord record : records) {
            double value = measure.applyAsDouble(record);
            if (Double.isNaN(value)) continue;
            count++;
            sum += value;
        }
        return count == 0 ? Double.NaN : sum / count;
    }

    /** The top of a chart axis: the smallest 1, 2 or 5 × 10ⁿ at or above {@code max}, and at least 1. */
    static double niceCeiling(double max) {
        if (!(max > 1)) return 1;
        double magnitude = Math.pow(10, Math.floor(Math.log10(max)));
        for (double step : new double[] {1, 2, 5, 10}) {
            if (step * magnitude >= max - 1e-9) return step * magnitude;
        }
        return 10 * magnitude;
    }
}
