package com.cleantv.speed;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Measures ping, download and upload against a speed test server that offers {@code __down?bytes=N} and
 * {@code __up} (Cloudflare's speed test does). Runs on its own threads; every callback goes through the
 * {@code callbacks} executor, and none arrive after {@link #cancel()}.
 *
 * <p>Download and upload each use several connections at once for a fixed time, like the well-known speed tests,
 * because one TCP connection rarely fills a fast line. The first seconds are left out of the result while the
 * connections ramp up.
 */
final class SpeedTest {
    enum Phase { PING, DOWNLOAD, UPLOAD }

    interface Listener {
        void onPhase(Phase phase);

        /** A live reading while a phase runs: Mbps, or milliseconds for ping. */
        void onLive(Phase phase, double value);

        /** A phase's result: Mbps, or for ping the median in ms with {@code jitter} in ms. */
        void onResult(Phase phase, double value, double jitter);

        /** The test ended. {@code error} is null when every phase gave a result. */
        void onFinished(IOException error);
    }

    static final String CLOUDFLARE = "https://speed.cloudflare.com/";

    static final class Settings {
        int pings = 10;
        int streams = 4;
        long phaseMs = 10_000;
        long warmupMs = 2_000;
        long sampleMs = 250;
        long downloadBytes = 25_000_000;
        int uploadBytes = 4_000_000;
    }

    private final String server;
    private final Settings settings;
    private final Listener listener;
    private final Executor callbacks;
    private final Set<HttpURLConnection> open = ConcurrentHashMap.newKeySet();
    private volatile boolean cancelled;
    private Thread thread;

    SpeedTest(String server, Settings settings, Listener listener, Executor callbacks) {
        this.server = server.endsWith("/") ? server : server + "/";
        this.settings = settings;
        this.listener = listener;
        this.callbacks = callbacks;
    }

    void start() {
        thread = new Thread(this::run, "speed-test");
        thread.start();
    }

    /** Stops the test as soon as possible. Call it from the callbacks' thread so no callback slips through. */
    void cancel() {
        cancelled = true;
        disconnectAll();
        if (thread != null) thread.interrupt();
    }

    private void run() {
        IOException error = null;
        try {
            post(() -> listener.onPhase(Phase.PING));
            double[] pings = ping();
            double median = SpeedMath.median(pings);
            double jitter = SpeedMath.jitter(pings);
            post(() -> listener.onResult(Phase.PING, median, jitter));

            for (Phase phase : new Phase[] {Phase.DOWNLOAD, Phase.UPLOAD}) {
                if (cancelled) return;
                post(() -> listener.onPhase(phase));
                try {
                    double mbps = transfer(phase);
                    post(() -> listener.onResult(phase, mbps, 0));
                } catch (IOException e) {
                    // Try the other direction anyway: upload can work when download doesn't, and the other way round.
                    if (error == null) error = e;
                }
            }
        } catch (IOException e) {
            error = e;
        } catch (InterruptedException e) {
            return;
        }
        IOException result = error;
        post(() -> listener.onFinished(result));
    }

    /** Round trips to the server in ms, on a connection that is already open. */
    private double[] ping() throws IOException, InterruptedException {
        // The first request pays for DNS, TCP and TLS; don't count it.
        request(server + "__down?bytes=0");
        double[] samples = new double[settings.pings];
        for (int i = 0; i < samples.length; i++) {
            checkCancelled();
            long start = System.nanoTime();
            request(server + "__down?bytes=0");
            samples[i] = (System.nanoTime() - start) / 1e6;
            double sample = samples[i];
            post(() -> listener.onLive(Phase.PING, sample));
            Thread.sleep(100);
        }
        return samples;
    }

    /** One small GET, read to the end so the connection goes back to the pool for the next ping. */
    private void request(String url) throws IOException {
        HttpURLConnection connection = open(url);
        try {
            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) throw new IOException("Server answered " + code);
            try (InputStream in = connection.getInputStream()) {
                drain(in);
            }
        } finally {
            open.remove(connection);
        }
    }

    /** Moves data both ways for {@link Settings#phaseMs} and returns the speed in Mbps after the warm-up. */
    private double transfer(Phase phase) throws IOException, InterruptedException {
        AtomicLong bytes = new AtomicLong();
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicReference<IOException> failure = new AtomicReference<>();
        Thread[] workers = new Thread[settings.streams];
        for (int i = 0; i < workers.length; i++) {
            workers[i] = new Thread(() -> stream(phase == Phase.UPLOAD, bytes, running, failure), "speed-" + i);
            workers[i].start();
        }

        long start = System.nanoTime();
        long last = start;
        long lastBytes = 0;
        long warmAt = -1;
        long warmBytes = 0;
        double live = 0;
        try {
            while (true) {
                Thread.sleep(settings.sampleMs);
                checkCancelled();
                long now = System.nanoTime();
                long total = bytes.get();
                double instant = SpeedMath.mbps(total - lastBytes, now - last);
                // Smooth the needle a little; the result itself comes from the totals below.
                live = live == 0 ? instant : live * 0.6 + instant * 0.4;
                double shown = live;
                post(() -> listener.onLive(phase, shown));
                last = now;
                lastBytes = total;

                long elapsedMs = (now - start) / 1_000_000;
                if (warmAt < 0 && elapsedMs >= settings.warmupMs) {
                    warmAt = now;
                    warmBytes = total;
                }
                if (elapsedMs >= settings.phaseMs) break;
                // Nothing at all got through and the server is refusing: no point waiting out the clock.
                if (total == 0 && failure.get() != null && elapsedMs >= 3_000) break;
            }
        } finally {
            running.set(false);
            disconnectAll();
            for (Thread worker : workers) worker.join(2_000);
        }

        if (lastBytes == 0) {
            IOException e = failure.get();
            throw e != null ? e : new IOException("No data moved");
        }
        if (warmAt >= 0 && last > warmAt && lastBytes > warmBytes) {
            return SpeedMath.mbps(lastBytes - warmBytes, last - warmAt);
        }
        return SpeedMath.mbps(lastBytes, last - start);
    }

    /** One connection's loop: download or upload, again and again, until the phase ends. */
    private void stream(boolean upload, AtomicLong bytes, AtomicBoolean running, AtomicReference<IOException> failure) {
        byte[] buffer = new byte[64 * 1024];
        // Random data, so nothing on the way can compress it.
        if (upload) new Random().nextBytes(buffer);
        while (running.get() && !cancelled) {
            HttpURLConnection connection = null;
            try {
                if (upload) {
                    connection = open(server + "__up");
                    connection.setDoOutput(true);
                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("Content-Type", "application/octet-stream");
                    connection.setFixedLengthStreamingMode(settings.uploadBytes);
                    try (OutputStream out = connection.getOutputStream()) {
                        int left = settings.uploadBytes;
                        while (left > 0 && running.get()) {
                            int n = Math.min(buffer.length, left);
                            out.write(buffer, 0, n);
                            left -= n;
                            bytes.addAndGet(n);
                        }
                    }
                    if (!running.get()) break;
                    int code = connection.getResponseCode();
                    if (code != HttpURLConnection.HTTP_OK) throw new IOException("Server answered " + code);
                    try (InputStream in = connection.getInputStream()) {
                        drain(in);
                    }
                } else {
                    connection = open(server + "__down?bytes=" + settings.downloadBytes);
                    int code = connection.getResponseCode();
                    if (code != HttpURLConnection.HTTP_OK) throw new IOException("Server answered " + code);
                    try (InputStream in = connection.getInputStream()) {
                        int n;
                        while (running.get() && (n = in.read(buffer)) != -1) bytes.addAndGet(n);
                    }
                }
            } catch (IOException e) {
                if (!running.get() || cancelled) break;
                failure.compareAndSet(null, e);
                try {
                    Thread.sleep(300);
                } catch (InterruptedException interrupted) {
                    break;
                }
            } finally {
                if (connection != null) {
                    open.remove(connection);
                    connection.disconnect();
                }
            }
        }
    }

    private HttpURLConnection open(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(8_000);
        connection.setReadTimeout(10_000);
        connection.setUseCaches(false);
        connection.setRequestProperty("Cache-Control", "no-store");
        connection.setRequestProperty("User-Agent", "CleanTV-Speed");
        open.add(connection);
        // cancel() may have run between the check and the add; make sure this one is closed too.
        if (cancelled) {
            open.remove(connection);
            throw new IOException("Cancelled");
        }
        return connection;
    }

    /** Closes every open connection, which unblocks any thread stuck reading or writing. */
    private void disconnectAll() {
        for (HttpURLConnection connection : open) connection.disconnect();
        open.clear();
    }

    private static void drain(InputStream in) throws IOException {
        byte[] buffer = new byte[8 * 1024];
        while (in.read(buffer) != -1) {
            // Discard.
        }
    }

    private void checkCancelled() throws InterruptedException {
        if (cancelled || Thread.currentThread().isInterrupted()) throw new InterruptedException();
    }

    private void post(Runnable callback) {
        if (cancelled) return;
        callbacks.execute(() -> {
            if (!cancelled) callback.run();
        });
    }
}
