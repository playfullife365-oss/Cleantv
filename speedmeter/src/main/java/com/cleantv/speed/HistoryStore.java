package com.cleantv.speed;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;

/** Saved tests, oldest first, kept on the TV only. */
final class HistoryStore {
    private static final String KEY = "tests";
    private final SharedPreferences prefs;

    HistoryStore(Context context) {
        prefs = context.getSharedPreferences("history", Context.MODE_PRIVATE);
    }

    List<TestRecord> load() {
        List<TestRecord> records = new ArrayList<>();
        String saved = prefs.getString(KEY, "");
        if (saved.isEmpty()) return records;
        for (String line : saved.split("\n")) {
            TestRecord record = TestRecord.decode(line);
            if (record != null) records.add(record);
        }
        return records;
    }

    void add(TestRecord record) {
        save(History.append(load(), record));
    }

    void clear() {
        prefs.edit().remove(KEY).apply();
    }

    private void save(List<TestRecord> records) {
        StringBuilder text = new StringBuilder();
        for (TestRecord record : records) {
            if (text.length() > 0) text.append('\n');
            text.append(record.encode());
        }
        prefs.edit().putString(KEY, text.toString()).apply();
    }
}
