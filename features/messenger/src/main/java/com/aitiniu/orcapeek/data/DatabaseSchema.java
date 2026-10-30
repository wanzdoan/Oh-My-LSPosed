package com.aitiniu.orcapeek.data;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public final class DatabaseSchema {
    private DatabaseSchema() {}

    public static boolean hasTable(SQLiteDatabase database, String name) {
        try (Cursor cursor = database.rawQuery(
                "SELECT 1 FROM sqlite_master WHERE type IN ('table','view') AND name=?",
                new String[]{name})) {
            return cursor.moveToFirst();
        }
    }
}
