package com.cleantv.speed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Runs the real test against a local server that speaks the same two endpoints as Cloudflare's. */
public class SpeedTestTest {
    private LocalSpeedServer server;
    private String url;

    @Before
    public void startServer() throws IOException {
        server = new LocalSpeedServer();
        url = server.url();
    }

    @After
    public void stopServer() throws IOException {
        server.close();
    }

    private static SpeedTest.Settings quick() {
        SpeedTest.Settings settings = new SpeedTest.Settings();
        settings.pings = 3;
        settings.streams = 2;
        settings.phaseMs = 1_200;
        settings.warmupMs = 300;
        settings.sampleMs = 100;
        settings.downloadBytes = 2_000_000;
        settings.uploadBytes = 500_000;
        return settings;
    }

    @Test
    public void measuresEveryPhase() throws InterruptedException {
        Recorder recorder = new Recorder();
        new SpeedTest(url, quick(), recorder, Runnable::run).start();
        assertTrue("test finished", recorder.finished.await(20, TimeUnit.SECONDS));

        assertNull(recorder.error);
        assertEquals(List.of(SpeedTest.Phase.PING, SpeedTest.Phase.DOWNLOAD, SpeedTest.Phase.UPLOAD), recorder.phases);
        assertTrue(recorder.results.get(SpeedTest.Phase.PING) >= 0);
        assertTrue(recorder.results.get(SpeedTest.Phase.DOWNLOAD) > 0);
        assertTrue(recorder.results.get(SpeedTest.Phase.UPLOAD) > 0);
        assertTrue(recorder.liveReadings.get() > 0);
    }

    @Test
    public void reportsAnErrorWhenTheServerIsDown() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        Recorder recorder = new Recorder();
        new SpeedTest("http://127.0.0.1:" + port, quick(), recorder, Runnable::run).start();
        assertTrue("test finished", recorder.finished.await(20, TimeUnit.SECONDS));
        assertNotNull(recorder.error);
        assertTrue(recorder.results.isEmpty());
    }

    @Test
    public void nothingArrivesAfterCancel() throws InterruptedException {
        SpeedTest.Settings settings = quick();
        settings.phaseMs = 5_000;
        Recorder recorder = new Recorder();
        SpeedTest test = new SpeedTest(url, settings, recorder, Runnable::run);
        test.start();
        assertTrue(recorder.downloading.await(10, TimeUnit.SECONDS));
        test.cancel();
        int before = recorder.events.get();
        Thread.sleep(1_500);
        // At most one callback already past its check when cancel() ran.
        assertTrue(recorder.events.get() - before <= 1);
        assertEquals(1, recorder.finished.getCount());
    }

    private static final class Recorder implements SpeedTest.Listener {
        final List<SpeedTest.Phase> phases = new CopyOnWriteArrayList<>();
        final Map<SpeedTest.Phase, Double> results = new ConcurrentHashMap<>();
        final java.util.concurrent.atomic.AtomicInteger liveReadings = new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger events = new java.util.concurrent.atomic.AtomicInteger();
        final CountDownLatch downloading = new CountDownLatch(1);
        final CountDownLatch finished = new CountDownLatch(1);
        volatile IOException error;

        @Override
        public void onPhase(SpeedTest.Phase phase) {
            events.incrementAndGet();
            phases.add(phase);
            if (phase == SpeedTest.Phase.DOWNLOAD) downloading.countDown();
        }

        @Override
        public void onLive(SpeedTest.Phase phase, double value) {
            events.incrementAndGet();
            liveReadings.incrementAndGet();
        }

        @Override
        public void onResult(SpeedTest.Phase phase, double value, double jitter) {
            events.incrementAndGet();
            results.put(phase, value);
        }

        @Override
        public void onFinished(IOException error) {
            events.incrementAndGet();
            this.error = error;
            finished.countDown();
        }
    }
}
