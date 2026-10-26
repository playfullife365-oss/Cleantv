package com.cleantv.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** The person's tile order and hidden apps, kept in SharedPreferences. */
final class LayoutStore {
    private static final String ORDER = "order";
    private static final String HIDDEN = "hidden";

    private final SharedPreferences prefs;

    LayoutStore(Context context) {
        prefs = context.getSharedPreferences("layout", Context.MODE_PRIVATE);
    }

    List<String> order() {
        return AppOrder.decode(prefs.getString(ORDER, null));
    }

    void saveOrder(List<String> order) {
        prefs.edit().putString(ORDER, AppOrder.encode(order)).apply();
    }

    Set<String> hidden() {
        return new LinkedHashSet<>(AppOrder.decode(prefs.getString(HIDDEN, null)));
    }

    void setHidden(String key, boolean hide) {
        Set<String> hidden = hidden();
        if (hide) hidden.add(key); else hidden.remove(key);
        prefs.edit().putString(HIDDEN, AppOrder.encode(hidden)).apply();
    }
}
