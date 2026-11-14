package com.cleantv.speed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Locale;
import org.junit.Test;

public class SpeedMathTest {
    private static final double EPS = 1e-9;

    @Test
    public void mbpsFromBytesAndTime() {
        // 12.5 MB in one second is 100 Mbps.
        assertEquals(100, SpeedMath.mbps(12_500_000, 1_000_000_000L), EPS);
        assertEquals(0, SpeedMath.mbps(0, 1_000_000_000L), EPS);
        assertEquals(0, SpeedMath.mbps(1000, 0), EPS);
    }

    @Test
    public void dialIsLinearBetweenMarks() {
        int last = SpeedMath.SCALE.length - 1;
        assertEquals(0, SpeedMath.dialFraction(0), EPS);
        assertEquals(0, SpeedMath.dialFraction(-3), EPS);
        assertEquals(0, SpeedMath.dialFraction(Double.NaN), EPS);
        assertEquals(1.0 / last, SpeedMath.dialFraction(5), EPS);
        assertEquals(1.5 / last, SpeedMath.dialFraction(7.5), EPS);
        assertEquals(5.0 / last, SpeedMath.dialFraction(100), EPS);
        assertEquals(1, SpeedMath.dialFraction(1000), EPS);
        assertEquals(1, SpeedMath.dialFraction(5000), EPS);
    }

    @Test
    public void dialNeverGoesBackwards() {
        double previous = -1;
        for (double mbps = 0; mbps <= 1200; mbps += 0.5) {
            double fraction = SpeedMath.dialFraction(mbps);
            assertEquals(true, fraction >= previous);
            previous = fraction;
        }
    }

    @Test
    public void medianAndJitter() {
        assertEquals(0, SpeedMath.median(new double[0]), EPS);
        assertEquals(20, SpeedMath.median(new double[] {30, 10, 20}), EPS);
        assertEquals(15, SpeedMath.median(new double[] {10, 20, 30, 5}), EPS);
        assertEquals(0, SpeedMath.jitter(new double[] {12}), EPS);
        // |20-10| + |15-20| = 15 over two steps.
        assertEquals(7.5, SpeedMath.jitter(new double[] {10, 20, 15}), EPS);
    }

    @Test
    public void formatting() {
        assertEquals("0", SpeedMath.format(0, Locale.US));
        assertEquals("4.3", SpeedMath.format(4.26, Locale.US));
        assertEquals("94", SpeedMath.format(94.4, Locale.US));
        assertEquals("4,3", SpeedMath.format(4.26, Locale.GERMANY));
    }

    @Test
    public void signalBandAndGeneration() {
        assertEquals(4, SpeedMath.signalLevel(-40));
        assertEquals(3, SpeedMath.signalLevel(-60));
        assertEquals(2, SpeedMath.signalLevel(-70));
        assertEquals(1, SpeedMath.signalLevel(-80));
        assertEquals(0, SpeedMath.signalLevel(-95));
        assertEquals("2.4 GHz", SpeedMath.band(2437));
        assertEquals("5 GHz", SpeedMath.band(5180));
        assertEquals("6 GHz", SpeedMath.band(5955));
        assertNull(SpeedMath.band(-1));
        assertEquals("Wi-Fi 6", SpeedMath.wifiGeneration(6));
        assertNull(SpeedMath.wifiGeneration(1));
    }
}
