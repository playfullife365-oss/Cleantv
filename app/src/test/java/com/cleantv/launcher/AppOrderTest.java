package com.cleantv.launcher;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class AppOrderTest {
    private static final List<String> NONE = Collections.emptyList();

    @Test
    public void noSavedOrderKeepsInstalledOrder() {
        assertEquals(Arrays.asList("a", "b", "c"), AppOrder.arrange(Arrays.asList("a", "b", "c"), NONE, NONE));
    }

    @Test
    public void savedOrderComesFirstAndNewAppsGoLast() {
        List<String> installed = Arrays.asList("a", "b", "c", "new");
        List<String> saved = Arrays.asList("c", "a", "b");
        assertEquals(Arrays.asList("c", "a", "b", "new"), AppOrder.arrange(installed, saved, NONE));
    }

    @Test
    public void uninstalledAndHiddenAppsAreLeftOut() {
        List<String> installed = Arrays.asList("a", "b", "c");
        List<String> saved = Arrays.asList("gone", "c", "b", "a");
        assertEquals(Arrays.asList("c", "a"), AppOrder.arrange(installed, saved, Collections.singleton("b")));
    }

    @Test
    public void duplicateKeysAppearOnce() {
        assertEquals(Arrays.asList("b", "a"),
                AppOrder.arrange(Arrays.asList("a", "b", "a"), Arrays.asList("b", "b"), NONE));
    }

    @Test
    public void moveShiftsAndStopsAtTheEnds() {
        List<String> order = Arrays.asList("a", "b", "c", "d");
        assertEquals(Arrays.asList("a", "c", "b", "d"), AppOrder.move(order, "b", 1));
        assertEquals(Arrays.asList("b", "a", "c", "d"), AppOrder.move(order, "b", -1));
        assertEquals(Arrays.asList("a", "c", "d", "b"), AppOrder.move(order, "b", 10));
        assertEquals(Arrays.asList("b", "a", "c", "d"), AppOrder.move(order, "b", -10));
        assertEquals(order, AppOrder.move(order, "missing", 1));
    }

    @Test
    public void moveToStart() {
        assertEquals(Arrays.asList("d", "a", "b", "c"), AppOrder.moveToStart(Arrays.asList("a", "b", "c", "d"), "d"));
    }

    @Test
    public void encodeDecodeRoundTrip() {
        List<String> keys = Arrays.asList("com.a/.Main", "com.b/com.b.Tv");
        assertEquals(keys, AppOrder.decode(AppOrder.encode(keys)));
        assertEquals(NONE, AppOrder.decode(null));
        assertEquals(NONE, AppOrder.decode(""));
    }
}
