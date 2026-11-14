package com.cleantv.speed;

/** One finished speed test. Speeds in Mbps and times in ms; NaN for a phase that gave no result. */
final class TestRecord {
    private static final String VERSION = "1";

    final long time;
    final double download;
    final double upload;
    final double ping;
    final double jitter;
    final ConnectionInfo connection;

    TestRecord(long time, double download, double upload, double ping, double jitter, ConnectionInfo connection) {
        this.time = time;
        this.download = download;
        this.upload = upload;
        this.ping = ping;
        this.jitter = jitter;
        this.connection = connection;
    }

    /** True when at least one phase gave a result, so the test is worth keeping. */
    boolean hasResult() {
        return !Double.isNaN(download) || !Double.isNaN(upload) || !Double.isNaN(ping);
    }

    /** One line of tab-separated fields. Numbers are written locale-independently. */
    String encode() {
        return String.join("\t", VERSION, Long.toString(time), Double.toString(download), Double.toString(upload),
                Double.toString(ping), Double.toString(jitter), Integer.toString(connection.kind),
                clean(connection.band), clean(connection.generation), Integer.toString(connection.rssi),
                Integer.toString(connection.linkMbps));
    }

    /** The record from {@link #encode()}'s line, or null when the line isn't one. */
    static TestRecord decode(String line) {
        if (line == null) return null;
        String[] f = line.split("\t", -1);
        if (f.length != 11 || !VERSION.equals(f[0])) return null;
        try {
            ConnectionInfo connection = new ConnectionInfo(Integer.parseInt(f[6]), empty(f[7]), empty(f[8]),
                    Integer.parseInt(f[9]), Integer.parseInt(f[10]));
            return new TestRecord(Long.parseLong(f[1]), Double.parseDouble(f[2]), Double.parseDouble(f[3]),
                    Double.parseDouble(f[4]), Double.parseDouble(f[5]), connection);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String clean(String text) {
        return text == null ? "" : text.replace('\t', ' ').replace('\n', ' ');
    }

    private static String empty(String text) {
        return text.isEmpty() ? null : text;
    }
}
