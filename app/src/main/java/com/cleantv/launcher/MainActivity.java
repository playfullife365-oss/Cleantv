package com.cleantv.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** The home screen: a clock, two buttons and a grid of app tiles that can be reordered and hidden. */
public final class MainActivity extends Activity {
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private LayoutStore store;
    private ScrollView scroll;
    private LinearLayout grid;
    private int columns;

    /** Every installed app by key, from the last load. */
    private final Map<String, AppEntry> apps = new HashMap<>();
    /** Installed app keys in name order, from the last load. */
    private final List<String> installed = new ArrayList<>();
    /** The tiles on screen, in order. */
    private List<String> visible = new ArrayList<>();
    private final BroadcastReceiver packagesChanged = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            reload();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        store = new LayoutStore(this);
        scroll = findViewById(R.id.scroll);
        grid = findViewById(R.id.grid);
        columns = getResources().getInteger(R.integer.columns);

        findViewById(R.id.settings_button).setOnClickListener(v -> start(new Intent(Settings.ACTION_SETTINGS),
                getString(R.string.settings)));
        findViewById(R.id.hidden_button).setOnClickListener(v -> showHiddenApps());
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(packagesChanged, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(packagesChanged, filter);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Apps can be installed, disabled or updated while we are in the background.
        reload();
    }

    @Override
    protected void onStop() {
        unregisterReceiver(packagesChanged);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        loader.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home pressed while already home: go back to the first tile.
        scroll.smoothScrollTo(0, 0);
        if (!visible.isEmpty()) focusTile(visible.get(0));
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        // Back on the home screen has nowhere to go.
    }

    private void reload() {
        loader.execute(() -> {
            List<AppEntry> loaded = AppRepository.load(this);
            main.post(() -> {
                if (isDestroyed()) return;
                apps.clear();
                installed.clear();
                for (AppEntry app : loaded) {
                    apps.put(app.key, app);
                    installed.add(app.key);
                }
                render(null);
            });
        });
    }

    /**
     * Rebuilds the grid from the last load and the saved layout, then focuses {@code focusKey}, or when that is
     * null, the app that had focus before. Focus on a button stays on the button.
     */
    private void render(String focusKey) {
        View focus = getCurrentFocus();
        if (focusKey == null && focus instanceof TileView) focusKey = ((TileView) focus).app.key;
        boolean hadTileFocus = focus == null || focus instanceof TileView;

        visible = AppOrder.arrange(installed, store.order(), store.hidden());
        grid.removeAllViews();
        grid.addView(sectionTitle(visible.isEmpty() ? R.string.no_apps : R.string.section_apps));

        int gap = getResources().getDimensionPixelSize(R.dimen.tile_gap);
        for (int start = 0; start < visible.size(); start += columns) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setClipChildren(false);
            row.setClipToPadding(false);
            for (int i = start; i < start + columns; i++) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
                lp.setMargins(gap / 2, gap / 2, gap / 2, gap / 2);
                // Pad the last row so its tiles are the same size as the others.
                row.addView(i < visible.size() ? tile(apps.get(visible.get(i))) : new Space(this), lp);
            }
            grid.addView(row);
        }

        if (!hadTileFocus) return;
        if (focusKey != null && visible.contains(focusKey)) {
            focusTile(focusKey);
        } else if (!visible.isEmpty()) {
            focusTile(visible.get(0));
        }
    }

    private TextView sectionTitle(int text) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextColor(getColor(R.color.text_secondary));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        int gap = getResources().getDimensionPixelSize(R.dimen.tile_gap);
        title.setPadding(gap / 2, 0, 0, gap / 2);
        return title;
    }

    private TileView tile(AppEntry app) {
        TileView tile = new TileView(this, app);
        tile.setOnClickListener(v -> launch(app));
        tile.setOnLongClickListener(v -> {
            showOptions(app);
            return true;
        });
        tile.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode != KeyEvent.KEYCODE_MENU) return false;
            if (event.getAction() == KeyEvent.ACTION_UP) showOptions(app);
            return true;
        });
        return tile;
    }

    private void focusTile(String key) {
        for (int r = 0; r < grid.getChildCount(); r++) {
            View row = grid.getChildAt(r);
            if (!(row instanceof LinearLayout)) continue;
            LinearLayout tiles = (LinearLayout) row;
            for (int c = 0; c < tiles.getChildCount(); c++) {
                View child = tiles.getChildAt(c);
                if (child instanceof TileView && ((TileView) child).app.key.equals(key)) {
                    child.requestFocus();
                    return;
                }
            }
        }
    }

    private void launch(AppEntry app) {
        Intent intent = new Intent(Intent.ACTION_MAIN)
                .addCategory(app.category)
                .setComponent(app.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        start(intent, app.label);
    }

    private void start(Intent intent, CharSequence name) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, getString(R.string.cannot_open, name), Toast.LENGTH_SHORT).show();
        }
    }

    private void showOptions(AppEntry app) {
        String[] items = {
            getString(R.string.move_left),
            getString(R.string.move_right),
            getString(R.string.move_up),
            getString(R.string.move_down),
            getString(R.string.move_first),
            getString(R.string.hide),
            getString(R.string.app_info),
        };
        new AlertDialog.Builder(this)
                .setTitle(app.label)
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0: move(app.key, AppOrder.move(visible, app.key, -1)); break;
                        case 1: move(app.key, AppOrder.move(visible, app.key, 1)); break;
                        case 2: move(app.key, AppOrder.move(visible, app.key, -columns)); break;
                        case 3: move(app.key, AppOrder.move(visible, app.key, columns)); break;
                        case 4: move(app.key, AppOrder.moveToStart(visible, app.key)); break;
                        case 5: hide(app.key); break;
                        default:
                            start(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", app.component.getPackageName(), null)), app.label);
                    }
                })
                .show();
    }

    private void move(String key, List<String> order) {
        store.saveOrder(order);
        render(key);
    }

    private void hide(String key) {
        // Keep focus in the same place: on the tile that takes the hidden one's spot.
        int index = visible.indexOf(key);
        store.setHidden(key, true);
        List<String> remaining = new ArrayList<>(visible);
        remaining.remove(key);
        render(remaining.isEmpty() ? null : remaining.get(Math.min(index, remaining.size() - 1)));
    }

    private void showHiddenApps() {
        List<AppEntry> hidden = new ArrayList<>();
        for (String key : store.hidden()) {
            AppEntry app = apps.get(key);
            if (app != null) hidden.add(app);
        }
        if (hidden.isEmpty()) {
            Toast.makeText(this, R.string.no_hidden_apps, Toast.LENGTH_SHORT).show();
            return;
        }
        CharSequence[] labels = new CharSequence[hidden.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = hidden.get(i).label;
        new AlertDialog.Builder(this)
                .setTitle(R.string.unhide_title)
                .setItems(labels, (dialog, which) -> {
                    store.setHidden(hidden.get(which).key, false);
                    render(null);
                })
                .show();
    }
}
