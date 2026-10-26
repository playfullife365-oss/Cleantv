package com.cleantv.launcher;

import android.content.ComponentName;
import android.graphics.drawable.Drawable;

/** One app the launcher can open: the activity to start, and how to draw its tile. */
final class AppEntry {
    final String key;
    final ComponentName component;
    /** LEANBACK_LAUNCHER for TV apps, LAUNCHER for phone apps that have no TV entry. */
    final String category;
    final CharSequence label;
    /** The app's 16:9 TV banner, or null if it has none. */
    final Drawable banner;
    final Drawable icon;

    AppEntry(ComponentName component, String category, CharSequence label, Drawable banner, Drawable icon) {
        this.key = component.flattenToShortString();
        this.component = component;
        this.category = category;
        this.label = label;
        this.banner = banner;
        this.icon = icon;
    }
}
