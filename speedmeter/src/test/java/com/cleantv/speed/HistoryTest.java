package com.cleantv.speed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class HistoryTest {
    private static final double EPS = 1e-9;
    private static final ConnectionInfo WIFI5 = new ConnectionInfo(ConnectionInfo.WIFI, "5 GHz", "Wi-Fi 6", -55, 866);
    private static final ConnectionInfo WIFI24 = new ConnectionInfo(ConnectionInfo.WIFI, "2.4 GHz", null, -70, 144);
    private static final ConnectionInfo CABLE = new ConnectionInfo(ConnectionInfo.ETHERNET, null, null, 0, 0);

    private static TestRecord record(long time, double down, double up, double ping, ConnectionInfo connection) {
        return new TestRecord(time, down, up, ping, 2, connection);
    }

    @Test
    public void encodeAndDecodeRoundTrip() {
        TestRecord original = new TestRecord(1_700_000_000_000L, 94.5, Double.NaN, 12.25, 1.5, WIFI5);
        TestRecord copy = TestRecord.decode(original.encode());
        assertEquals(original.time, copy.time);
        assertEquals(original.download, copy.download, EPS);
        assertTrue(Double.isNaN(copy.upload));
        assertEquals(original.ping, copy.ping, EPS);
        assertEquals(original.jitter, copy.jitter, EPS);
        assertEquals(WIFI5, copy.connection);

        TestRecord wired = TestRecord.decode(record(1, 500, 50, 5, CABLE).encode());
        assertNull(wired.connection.band);
        assertNull(wired.connection.generation);
    }

    @Test
    public void badLinesAreSkipped() {
        assertNull(TestRecord.decode(null));
        assertNull(TestRecord.decode(""));
        assertNull(TestRecord.decode("2\t1\t2\t3\t4\t5\t1\t\t\t0\t0"));
        assertNull(TestRecord.decode("1\tx\t2\t3\t4\t5\t1\t\t\t0\t0"));
    }

    @Test
    public void summariesSkipMissingValues() {
        List<TestRecord> records = List.of(
                record(1, 50, 10, 20, WIFI5),
                record(2, Double.NaN, 20, 30, WIFI5),
                record(3, 100, 15, 10, WIFI5));
        History.Summary down = History.download(records);
        assertEquals(2, down.count);
        assertEquals(100, down.latest, EPS);
        assertEquals(75, down.average, EPS);
        assertEquals(100, down.best, EPS);
        // Lower ping is better.
        assertEquals(10, History.ping(records).best, EPS);
        assertEquals(15, History.upload(records).latest, EPS);
        assertTrue(Double.isNaN(History.download(List.of()).average));
    }

    @Test
    public void groupsByConnectionNewestFirst() {
        List<TestRecord> records = List.of(
                record(1, 100, 20, 10, WIFI5),
                record(2, 20, 5, 40, WIFI24),
                record(3, 200, 40, 20, new ConnectionInfo(ConnectionInfo.WIFI, "5 GHz", "Wi-Fi 6", -65, 780)),
                record(4, 900, 90, 3, CABLE));
        List<History.ConnectionSummary> groups = History.byConnection(records);
        assertEquals(3, groups.size());
        assertEquals(ConnectionInfo.ETHERNET, groups.get(0).latest.kind);
        assertTrue(Double.isNaN(groups.get(0).rssi));

        History.ConnectionSummary fast = groups.get(1);
        assertEquals("5 GHz", fast.latest.band);
        assertEquals(780, fast.latest.linkMbps);
        assertEquals(2, fast.count);
        assertEquals(3, fast.lastTime);
        assertEquals(150, fast.download, EPS);
        assertEquals(-60, fast.rssi, EPS);
        assertEquals("2.4 GHz", groups.get(2).latest.band);
    }

    @Test
    public void appendKeepsTheNewest() {
        List<TestRecord> records = new ArrayList<>();
        for (int i = 0; i < History.LIMIT; i++) records.add(record(i, 1, 1, 1, CABLE));
        List<TestRecord> next = History.append(records, record(999, 1, 1, 1, CABLE));
        assertEquals(History.LIMIT, next.size());
        assertEquals(1, next.get(0).time);
        assertEquals(999, next.get(next.size() - 1).time);
    }

    @Test
    public void niceCeilings() {
        assertEquals(1, History.niceCeiling(0), EPS);
        assertEquals(1, History.niceCeiling(Double.NaN), EPS);
        assertEquals(2, History.niceCeiling(1.5), EPS);
        assertEquals(50, History.niceCeiling(43), EPS);
        assertEquals(100, History.niceCeiling(100), EPS);
        assertEquals(200, History.niceCeiling(101), EPS);
        assertEquals(1000, History.niceCeiling(940), EPS);
    }
}
