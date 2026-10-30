package com.aitiniu.orcapeek.hook;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import com.aitiniu.orcapeek.data.RecentMessagesRepository;
import com.aitiniu.orcapeek.data.DatabaseSchema;

import java.io.File;

public final class ReadReceiptGuard {
    private static final String TAG = "OrcaPeek";
    private static final long RESTORE_DELAY_MS = 10_000L;
    private static final long ARM_TIMEOUT_MS = 20_000L;
    private static final long PREVIEW_MAX_HOLD_MS = 5 * 60_000L;
    private static final long DUPLICATE_ARM_WINDOW_MS = 750L;
    private static final String JOURNAL = "orcapeek_rr_guard";
    private static final String TRIGGER_RECOVERY = "orcapeek_rr_trigger_recovery";
    private static final String THREAD_UPDATE_TRIGGER =
            "trigger__threads__sync_group_isolation_violation_update";
    private static State active;
    private static long generation;
    private static long lastArmAt;
    private static String lastArmTitle = "";

    private ReadReceiptGuard() {
    }

    public static synchronized boolean arm(Context context, String accessibilityLabel) {
        recoverStaleLocked(context);
        String title = RecentMessagesRepository.extractConversationTitle(accessibilityLabel);
        if (title.isEmpty()) {
            Log.w(TAG, "ORCAPEek_RR_OVERRIDE_FAILED reason=no-title");
            return false;
        }
        long now = SystemClock.elapsedRealtime();
        if (active != null && title.equals(lastArmTitle)
                && now - lastArmAt <= DUPLICATE_ARM_WINDOW_MS) {
            Log.i(TAG, "ORCAPEek_RR_OVERRIDE_DUPLICATE_IGNORED");
            return true;
        }
        restoreLocked("re-arm");
        if (active != null) {
            // Preserve the journal and original values if restoration failed.
            return false;
        }
        File directory = context.getDatabasePath("placeholder").getParentFile();
        File[] databases = directory == null ? null : directory.listFiles(file ->
                file.isFile()
                        && file.getName().startsWith("msys_database_")
                        && !file.getName().contains("-"));
        if (databases == null) {
            return false;
        }
        for (File file : databases) {
            State state = apply(context, file, title);
            if (state != null) {
                active = state;
                lastArmAt = now;
                lastArmTitle = title;
                long token = ++generation;
                new Handler(Looper.getMainLooper()).postDelayed(
                        () -> restoreIfCurrent(token, "arm-timeout"), ARM_TIMEOUT_MS);
                Log.i(TAG, "ORCAPEek_RR_OVERRIDE_APPLIED scope=thread"
                        + " previous=" + state.originalDisabled
                        + " current=1 unread=" + state.originalUnread);
                return true;
            }
        }
        Log.w(TAG, "ORCAPEek_RR_OVERRIDE_FAILED reason=no-thread-match");
        return false;
    }

    public static synchronized boolean matchesConversation(String label) {
        return active != null && lastArmTitle.equals(
                RecentMessagesRepository.extractConversationTitle(label));
    }

    public static synchronized void onConversationOpened() {
        if (active == null) {
            return;
        }
        long token = ++generation;
        Log.i(TAG, "ORCAPEek_RR_OVERRIDE_ACTIVE_ON_OPEN current=1");
        new Handler(Looper.getMainLooper()).postDelayed(
                () -> restoreIfCurrent(token, "post-open-timeout"), RESTORE_DELAY_MS);
    }

    public static synchronized void holdForPreview() {
        if (active == null) {
            return;
        }
        long token = ++generation;
        new Handler(Looper.getMainLooper()).postDelayed(
                () -> restoreIfCurrent(token, "preview-max-timeout"), PREVIEW_MAX_HOLD_MS);
        Log.i(TAG, "ORCAPEek_RR_OVERRIDE_HELD_FOR_PREVIEW current=1");
    }

    public static synchronized void restore(String reason) {
        restoreLocked(reason);
    }

    public static synchronized void recover(Context context) {
        recoverThreadUpdateTriggerLocked(context);
        recoverStaleLocked(context);
    }

    private static synchronized void restoreIfCurrent(long token, String reason) {
        if (active != null && generation == token) {
            restoreLocked(reason);
        }
    }

    private static State apply(Context context, File file, String title) {
        SQLiteDatabase database = null;
        Cursor cursor = null;
        try {
            database = SQLiteDatabase.openDatabase(
                    file.getAbsolutePath(), null,
                    SQLiteDatabase.OPEN_READWRITE | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
            if (!DatabaseSchema.hasTable(database, "client_threads")) {
                return applyModern(context, file, database, title);
            }
            cursor = database.rawQuery(
                    "SELECT pk, read_receipts_disabled_v2, unread_message_count, "
                            + "last_read_watermark_timestamp_ms, "
                            + "last_read_watermark_authoritative_ts_ms, last_read_sort_order "
                            + "FROM client_threads WHERE thread_name = ? OR default_thread_name = ? "
                            + "OR default_thread_name_without_nickname = ? "
                            + "ORDER BY last_activity_timestamp_ms DESC LIMIT 1",
                    new String[]{title, title, title});
            if (!cursor.moveToFirst()) {
                cursor.close();
                cursor = null;
                return applyModern(context, file, database, title);
            }
            State state = new State();
            state.context = context.getApplicationContext();
            state.database = file;
            state.threadPk = cursor.getLong(0);
            state.originalDisabled = cursor.getInt(1);
            state.originalUnread = cursor.getInt(2);
            state.originalWatermark = cursor.getLong(3);
            state.originalAuthoritativeWatermark = cursor.isNull(4) ? null : cursor.getLong(4);
            state.originalReadSortOrder = cursor.isNull(5) ? null : cursor.getLong(5);

            persistState(state);

            ContentValues values = new ContentValues();
            values.put("read_receipts_disabled_v2", 1);
            int changed = database.update(
                    "client_threads", values, "pk = ?",
                    new String[]{Long.toString(state.threadPk)});
            if (changed == 1) {
                return state;
            }
            clearJournal(state.context);
            return null;
        } catch (Throwable error) {
            Log.w(TAG, "ORCAPEek_RR_OVERRIDE_FAILED error="
                    + error.getClass().getSimpleName());
            return null;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            if (database != null) {
                database.close();
            }
        }
    }

    private static State applyModern(Context context, File file,
            SQLiteDatabase database, String title) {
        Cursor cursor = null;
        try {
            cursor = database.rawQuery(
                    "SELECT t.thread_key, t.read_receipts_disabled_v2, "
                            + "t.unread_message_count, t.last_read_watermark_timestamp_ms "
                            + "FROM threads t "
                            + "LEFT JOIN _cached_participant_thread_info p "
                            + "ON p.thread_key = t.thread_key "
                            + "LEFT JOIN contacts c ON c.id = t.thread_key "
                            + "WHERE t.thread_name = ? OR p.thread_name = ? "
                            + "OR p.thread_name_without_nickname = ? OR c.name = ? "
                            + "ORDER BY t.last_activity_timestamp_ms DESC LIMIT 1",
                    new String[]{title, title, title, title});
            if (!cursor.moveToFirst()) {
                return null;
            }
            State state = new State();
            state.context = context.getApplicationContext();
            state.database = file;
            state.threadPk = cursor.getLong(0);
            state.originalDisabled = cursor.getInt(1);
            state.originalUnread = cursor.getInt(2);
            state.originalWatermark = cursor.getLong(3);
            state.modernSchema = true;
            persistState(state);

            ContentValues values = new ContentValues();
            values.put("read_receipts_disabled_v2", 1);
            int changed = updateModernThread(
                    state.context, state.database, database, values,
                    "thread_key = ?", new String[]{Long.toString(state.threadPk)});
            if (changed == 1) {
                Log.i(TAG, "ORCAPEek_RR_SCHEMA schema=threads threadKey="
                        + state.threadPk);
                return state;
            }
            clearJournal(state.context);
            return null;
        } catch (Throwable error) {
            Log.w(TAG, "ORCAPEek_RR_SCHEMA_FAILED schema=threads error="
                    + error.getClass().getSimpleName());
            clearJournal(context.getApplicationContext());
            return null;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private static void restoreLocked(String reason) {
        State state = active;
        if (state == null) {
            return;
        }
        generation++;
        SQLiteDatabase database = null;
        try {
            database = SQLiteDatabase.openDatabase(
                    state.database.getAbsolutePath(), null,
                    SQLiteDatabase.OPEN_READWRITE | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
            ContentValues values = new ContentValues();
            values.put("read_receipts_disabled_v2", state.originalDisabled);
            values.put("unread_message_count", state.originalUnread);
            values.put("last_read_watermark_timestamp_ms", state.originalWatermark);
            if (!state.modernSchema) {
                if (state.originalAuthoritativeWatermark == null) {
                    values.putNull("last_read_watermark_authoritative_ts_ms");
                } else {
                    values.put("last_read_watermark_authoritative_ts_ms",
                            state.originalAuthoritativeWatermark);
                }
                if (state.originalReadSortOrder == null) {
                    values.putNull("last_read_sort_order");
                } else {
                    values.put("last_read_sort_order", state.originalReadSortOrder);
                }
            }
            int changed;
            if (state.modernSchema) {
                changed = updateModernThread(
                        state.context, state.database, database, values,
                        "thread_key = ?", new String[]{Long.toString(state.threadPk)});
            } else {
                changed = database.update(
                        "client_threads", values, "pk = ?",
                        new String[]{Long.toString(state.threadPk)});
            }
            Log.i(TAG, "ORCAPEek_RR_OVERRIDE_RESTORED reason=" + reason
                    + " rows=" + changed + " disabled=" + state.originalDisabled
                    + " unread=" + state.originalUnread);
            if (changed == 1) {
                clearJournal(state.context);
                active = null;
            }
        } catch (Throwable error) {
            Log.e(TAG, "ORCAPEek_RR_OVERRIDE_RESTORE_FAILED", error);
        } finally {
            if (database != null) {
                database.close();
            }
        }
    }

    private static void recoverStaleLocked(Context context) {
        if (active != null) {
            return;
        }
        Context applicationContext = context.getApplicationContext();
        SharedPreferences preferences = applicationContext.getSharedPreferences(
                JOURNAL, Context.MODE_PRIVATE);
        if (!preferences.getBoolean("active", false)) {
            return;
        }
        State state = new State();
        state.context = applicationContext;
        state.database = new File(preferences.getString("database", ""));
        state.threadPk = preferences.getLong("thread_pk", -1L);
        state.originalDisabled = preferences.getInt("disabled", 0);
        state.originalUnread = preferences.getInt("unread", 0);
        state.originalWatermark = preferences.getLong("watermark", 0L);
        state.modernSchema = preferences.getBoolean("modern_schema", false);
        if (preferences.getBoolean("has_authoritative", false)) {
            state.originalAuthoritativeWatermark = preferences.getLong("authoritative", 0L);
        }
        if (preferences.getBoolean("has_sort_order", false)) {
            state.originalReadSortOrder = preferences.getLong("sort_order", 0L);
        }
        if (!state.database.isFile() || state.threadPk < 0L) {
            clearJournal(applicationContext);
            Log.w(TAG, "ORCAPEek_RR_OVERRIDE_RECOVERY_DISCARDED invalid=true");
            return;
        }
        active = state;
        generation++;
        Log.i(TAG, "ORCAPEek_RR_OVERRIDE_RECOVERY_FOUND");
        restoreLocked("process-recovery");
    }

    private static void persistState(State state) {
        SharedPreferences.Editor editor = state.context.getSharedPreferences(
                        JOURNAL, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .putBoolean("active", true)
                .putString("database", state.database.getAbsolutePath())
                .putLong("thread_pk", state.threadPk)
                .putInt("disabled", state.originalDisabled)
                .putInt("unread", state.originalUnread)
                .putLong("watermark", state.originalWatermark)
                .putBoolean("modern_schema", state.modernSchema)
                .putBoolean("has_authoritative", state.originalAuthoritativeWatermark != null)
                .putBoolean("has_sort_order", state.originalReadSortOrder != null);
        if (state.originalAuthoritativeWatermark != null) {
            editor.putLong("authoritative", state.originalAuthoritativeWatermark);
        }
        if (state.originalReadSortOrder != null) {
            editor.putLong("sort_order", state.originalReadSortOrder);
        }
        if (!editor.commit()) {
            throw new IllegalStateException("Cannot persist read receipt recovery journal");
        }
    }

    private static void clearJournal(Context context) {
        context.getSharedPreferences(JOURNAL, Context.MODE_PRIVATE).edit().clear().commit();
    }

    private static int updateModernThread(Context context, File file,
            SQLiteDatabase database, ContentValues values,
            String whereClause, String[] whereArgs) {
        Cursor cursor = null;
        String triggerSql = null;
        try {
            cursor = database.rawQuery(
                    "SELECT sql FROM sqlite_master WHERE type = 'trigger' AND name = ?",
                    new String[]{THREAD_UPDATE_TRIGGER});
            if (cursor.moveToFirst() && !cursor.isNull(0)) {
                triggerSql = cursor.getString(0);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        if (triggerSql == null || triggerSql.isEmpty()) {
            return database.update("threads", values, whereClause, whereArgs);
        }

        // SQLite DDL is transactional. Readers never see a committed schema without
        // the trigger, and process death rolls back both the data and schema changes.
        database.beginTransaction();
        try {
            database.execSQL("DROP TRIGGER IF EXISTS " + THREAD_UPDATE_TRIGGER);
            int changed = database.update("threads", values, whereClause, whereArgs);
            database.execSQL(triggerSql);
            database.setTransactionSuccessful();
            return changed;
        } finally {
            database.endTransaction();
        }
    }

    private static void recoverThreadUpdateTriggerLocked(Context context) {
        Context applicationContext = context.getApplicationContext();
        SharedPreferences recovery = applicationContext.getSharedPreferences(
                TRIGGER_RECOVERY, Context.MODE_PRIVATE);
        if (!recovery.getBoolean("active", false)) {
            return;
        }
        File file = new File(recovery.getString("database", ""));
        String sql = recovery.getString("sql", "");
        if (!file.isFile() || sql.isEmpty()) {
            recovery.edit().clear().commit();
            return;
        }
        SQLiteDatabase database = null;
        try {
            database = SQLiteDatabase.openDatabase(
                    file.getAbsolutePath(), null,
                    SQLiteDatabase.OPEN_READWRITE | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
            try (Cursor existing = database.rawQuery(
                    "SELECT 1 FROM sqlite_master WHERE type='trigger' AND name=?",
                    new String[]{THREAD_UPDATE_TRIGGER})) {
                if (!existing.moveToFirst()) {
                    database.execSQL(sql);
                }
            }
            recovery.edit().clear().commit();
            Log.i(TAG, "ORCAPEek_RR_TRIGGER_RECOVERED");
        } catch (Throwable error) {
            Log.e(TAG, "ORCAPEek_RR_TRIGGER_RECOVERY_FAILED", error);
        } finally {
            if (database != null) {
                database.close();
            }
        }
    }

    private static final class State {
        private Context context;
        private File database;
        private long threadPk;
        private int originalDisabled;
        private int originalUnread;
        private long originalWatermark;
        private Long originalAuthoritativeWatermark;
        private Long originalReadSortOrder;
        private boolean modernSchema;
    }
}
