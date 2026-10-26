package com.cleantv.launcher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Tile order and hidden apps, as plain lists of app keys. No Android types, so it is tested on the JVM.
 * A key is a flattened component name ("package/class"), which never contains a newline.
 */
final class AppOrder {
    private AppOrder() {}

    /**
     * The tiles to show: apps from {@code saved} in their saved order, then any apps not in it (new installs)
     * in {@code installed} order. Hidden and uninstalled apps are left out.
     */
    static List<String> arrange(List<String> installed, List<String> saved, Collection<String> hidden) {
        Set<String> present = new LinkedHashSet<>(installed);
        present.removeAll(hidden);
        List<String> out = new ArrayList<>(present.size());
        for (String key : saved) {
            if (present.remove(key)) out.add(key);
        }
        out.addAll(present);
        return out;
    }

    /** {@code order} with {@code key} moved by {@code delta} places, stopping at either end. */
    static List<String> move(List<String> order, String key, int delta) {
        List<String> out = new ArrayList<>(order);
        int from = out.indexOf(key);
        if (from < 0) return out;
        int to = Math.max(0, Math.min(out.size() - 1, from + delta));
        out.remove(from);
        out.add(to, key);
        return out;
    }

    static List<String> moveToStart(List<String> order, String key) {
        return move(order, key, -order.size());
    }

    static String encode(Collection<String> keys) {
        return String.join("\n", keys);
    }

    static List<String> decode(String stored) {
        if (stored == null || stored.isEmpty()) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        for (String key : stored.split("\n")) {
            if (!key.isEmpty()) out.add(key);
        }
        return out;
    }
}
