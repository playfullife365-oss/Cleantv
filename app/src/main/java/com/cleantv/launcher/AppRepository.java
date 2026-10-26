package com.cleantv.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import java.text.Collator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Finds the apps to show. Loads icons and banners, so call it off the main thread. */
final class AppRepository {
    private AppRepository() {}

    static List<AppEntry> load(Context context) {
        PackageManager pm = context.getPackageManager();
        Map<String, AppEntry> byPackage = new LinkedHashMap<>();
        // TV entries first; a phone entry is only used for a package that has no TV entry.
        addAll(pm, byPackage, Intent.CATEGORY_LEANBACK_LAUNCHER);
        addAll(pm, byPackage, Intent.CATEGORY_LAUNCHER);
        byPackage.remove(context.getPackageName());

        List<AppEntry> apps = new ArrayList<>(byPackage.values());
        Collator collator = Collator.getInstance();
        apps.sort((a, b) -> collator.compare(a.label.toString(), b.label.toString()));
        return apps;
    }

    private static void addAll(PackageManager pm, Map<String, AppEntry> byPackage, String category) {
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(category);
        for (ResolveInfo info : pm.queryIntentActivities(intent, 0)) {
            ActivityInfo activity = info.activityInfo;
            if (byPackage.containsKey(activity.packageName)) continue;
            ComponentName component = new ComponentName(activity.packageName, activity.name);
            // loadBanner falls back to the application's banner when the activity has none.
            byPackage.put(activity.packageName,
                    new AppEntry(component, category, info.loadLabel(pm), activity.loadBanner(pm), info.loadIcon(pm)));
        }
    }
}
