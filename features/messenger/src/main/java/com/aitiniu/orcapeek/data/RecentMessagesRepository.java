// language: Java, file: RecentMessagesRepository.java, runtime: Android API 26+, target: OrcaPeek v0.10.1
// Fixed: Dual-schema attachment query without invalid column references.
// Legacy (client_messages) uses client_attachments + client_attachment_store_files.
// Modern (messages) uses attachments.
package com.aitiniu.orcapeek.data;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class RecentMessagesRepository {
    private static final int MAX_MESSAGES = 7;

    public static final int TYPE_PHOTO   = 2;
    public static final int TYPE_XMA     = 7;
    public static final int TYPE_STICKER = 1;
    public static final int TYPE_GIF     = 15;
    public static final int TYPE_VIDEO   = 4;

    private static final int CTYPE_STICKER = 4;

    private RecentMessagesRepository() {
    }

    public interface Callback {
        void onResult(Result result);
    }

    public static void load(Context context, String accessibilityLabel, Callback callback) {
        Context appContext = context.getApplicationContext();
        String requestedTitle = extractConversationTitle(accessibilityLabel);
        new Thread(() -> {
            Result result;
            try {
                result = query(appContext, requestedTitle);
            } catch (Throwable error) {
                Log.w("OrcaPeek", "ORCAPEek_PREVIEW_FAILED " + error.getClass().getSimpleName());
                result = Result.error("Preview unavailable");
            }
            final Result delivered = result;
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    callback.onResult(delivered);
                } catch (Throwable error) {
                    Log.w("OrcaPeek", "ORCAPEek_PREVIEW_RENDER_FAILED "
                            + error.getClass().getSimpleName());
                }
            });
        }, "OrcaPeek-db-preview").start();
    }

    public static String extractConversationTitle(String label) {
        if (label == null) {
            return "";
        }
        String value = label.trim();
        value = value.replaceFirst(
                "(?i),?\\s*\\d+\\s+(new messages?|tin nhắn mới).*$", "").trim();
        int comma = value.indexOf(',');
        if (comma > 0) {
            value = value.substring(0, comma).trim();
        }
        return value;
    }

    private static Result query(Context context, String requestedTitle) {
        if (requestedTitle.isEmpty()) {
            return Result.error("Không đọc được tên cuộc trò chuyện từ hàng đã chọn");
        }
        File databaseDirectory = context.getDatabasePath("placeholder").getParentFile();
        File[] candidates = databaseDirectory == null ? null : databaseDirectory.listFiles(
                file -> file.isFile()
                        && file.getName().startsWith("msys_database_")
                        && !file.getName().endsWith("-wal")
                        && !file.getName().endsWith("-shm")
                        && !file.getName().endsWith("-journal"));
        if (candidates == null || candidates.length == 0) {
            return Result.error("Không tìm thấy cơ sở dữ liệu Messenger");
        }
        for (File database : candidates) {
            Result result = queryDatabase(database, requestedTitle);
            if (result.success) {
                return result;
            }
        }
        return Result.error("Chua khop duoc cuoc tro chuyen \"" + requestedTitle + "\"");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Legacy schema: client_threads / client_messages
    // ──────────────────────────────────────────────────────────────────────────
    private static Result queryDatabase(File file, String requestedTitle) {
        SQLiteDatabase database = null;
        Cursor threadCursor = null;
        Cursor messageCursor = null;
        try {
            database = SQLiteDatabase.openDatabase(
                    file.getAbsolutePath(),
                    null,
                    SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
            if (!DatabaseSchema.hasTable(database, "client_threads")) {
                return queryModernSchema(database, file, requestedTitle);
            }
            // Tier 1: exact match
            threadCursor = database.rawQuery(
                    "SELECT pk, COALESCE(NULLIF(thread_name, ''), default_thread_name), "
                            + "COALESCE(NULLIF(thread_picture_url, ''), "
                            + "default_other_participant_profile_picture_url_list, '') "
                            + "FROM client_threads "
                            + "WHERE thread_name = ? OR default_thread_name = ? "
                            + "OR default_thread_name_without_nickname = ? "
                            + "ORDER BY last_activity_authoritative_ts_ms DESC LIMIT 1",
                    new String[]{requestedTitle, requestedTitle, requestedTitle});
            if (!threadCursor.moveToFirst()) {
                threadCursor.close();
                threadCursor = null;
                // Tier 2: LIKE prefix
                String likeArg = requestedTitle + "%";
                threadCursor = database.rawQuery(
                        "SELECT pk, COALESCE(NULLIF(thread_name, ''), default_thread_name), "
                                + "COALESCE(NULLIF(thread_picture_url, ''), "
                                + "default_other_participant_profile_picture_url_list, '') "
                                + "FROM client_threads "
                                + "WHERE thread_name LIKE ? OR default_thread_name LIKE ? "
                                + "OR default_thread_name_without_nickname LIKE ? "
                                + "ORDER BY last_activity_authoritative_ts_ms DESC LIMIT 1",
                        new String[]{likeArg, likeArg, likeArg});
            }
            if (!threadCursor.moveToFirst()) {
                threadCursor.close();
                threadCursor = null;
                // Tier 3: Java fold-scan
                threadCursor = database.rawQuery(
                        "SELECT pk, COALESCE(NULLIF(thread_name, ''), default_thread_name), "
                                + "COALESCE(NULLIF(thread_picture_url, ''), "
                                + "default_other_participant_profile_picture_url_list, '') "
                                + "FROM client_threads "
                                + "ORDER BY last_activity_authoritative_ts_ms DESC LIMIT 300",
                        null);
                String foldedRequest = fold(requestedTitle);
                boolean found = false;
                while (threadCursor.moveToNext()) {
                    String name = threadCursor.isNull(1) ? "" : threadCursor.getString(1);
                    if (fold(name).contains(foldedRequest) || foldedRequest.contains(fold(name))) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    threadCursor.close();
                    threadCursor = null;
                    return queryModernSchema(database, file, requestedTitle);
                }
            }
            long threadPk = threadCursor.getLong(0);
            String resolvedTitle = textOr(threadCursor, 1, requestedTitle);
            String avatarUrl = textOr(threadCursor, 2, "");
            long selfContactPk = parseSelfContactPk(file.getName());

            // Correct schema for client_messages: links to client_attachments and client_attachment_store_files
            messageCursor = database.rawQuery(
                    "SELECT m.text, m.message_content_type, m.sender_contact_pk, "
                            + "COALESCE(NULLIF(c.short_displayed_name, ''), c.displayed_name, ''), "
                            + "m.authoritative_ts_ms, "
                            + "ca.attachment_type, casf.persisted_path, ca.preview_width, ca.preview_height "
                            + "FROM client_messages m "
                            + "LEFT JOIN client_contacts c ON c.pk = m.sender_contact_pk "
                            + "LEFT JOIN client_attachments ca ON ca.message_pk = m.pk "
                            + "LEFT JOIN client_attachment_store_files casf ON casf.message_pk = m.pk "
                            + "WHERE m.thread_pk = ? AND m.is_hidden = 0 AND m.is_tombstoned = 0 "
                            + "ORDER BY m.authoritative_ts_ms DESC LIMIT " + MAX_MESSAGES,
                    new String[]{Long.toString(threadPk)});
            List<Message> messages = readMessages(messageCursor, selfContactPk);
            Collections.reverse(messages);
            if (messages.isEmpty()) {
                return new Result(true, resolvedTitle, avatarUrl, messages,
                        "Cuộc trò chuyện chưa có tin nhắn hiển thị");
            }
            return new Result(true, resolvedTitle, avatarUrl, messages,
                    String.format(Locale.ROOT, "%d tin nhắn gần nhất", messages.size()));
        } catch (Throwable error) {
            Log.e("OrcaPeek", "ORCAPEek_LEGACY_FAILED error=" + error.getMessage(), error);
            return Result.error("Lỗi đọc dữ liệu: " + error.getClass().getSimpleName());
        } finally {
            if (messageCursor != null) messageCursor.close();
            if (threadCursor != null) threadCursor.close();
            if (database != null) database.close();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Modern schema: threads / messages / attachments
    // ──────────────────────────────────────────────────────────────────────────
    private static Result queryModernSchema(
            SQLiteDatabase database, File file, String requestedTitle) {
        Cursor threadCursor = null;
        Cursor messageCursor = null;
        try {
            // Tier 1: exact match
            threadCursor = database.rawQuery(
                    "SELECT t.thread_key, "
                            + "COALESCE(NULLIF(t.thread_name, ''), "
                            + "NULLIF(p.thread_name, ''), NULLIF(c.name, ''), ?), "
                            + "COALESCE(NULLIF(t.thread_picture_url, ''), "
                            + "NULLIF(p.other_participant_profile_picture_url, ''), "
                            + "NULLIF(c.profile_picture_url, ''), '') "
                            + "FROM threads t "
                            + "LEFT JOIN _cached_participant_thread_info p "
                            + "ON p.thread_key = t.thread_key "
                            + "LEFT JOIN contacts c ON c.id = t.thread_key "
                            + "WHERE t.thread_name = ? OR p.thread_name = ? "
                            + "OR p.thread_name_without_nickname = ? OR c.name = ? "
                            + "ORDER BY t.last_activity_timestamp_ms DESC LIMIT 1",
                    new String[]{requestedTitle, requestedTitle, requestedTitle,
                            requestedTitle, requestedTitle});
            if (!threadCursor.moveToFirst()) {
                threadCursor.close();
                threadCursor = null;
                // Tier 2: LIKE prefix
                String likeArg = requestedTitle + "%";
                threadCursor = database.rawQuery(
                        "SELECT t.thread_key, "
                                + "COALESCE(NULLIF(t.thread_name, ''), "
                                + "NULLIF(p.thread_name, ''), NULLIF(c.name, ''), ?), "
                                + "COALESCE(NULLIF(t.thread_picture_url, ''), "
                                + "NULLIF(p.other_participant_profile_picture_url, ''), "
                                + "NULLIF(c.profile_picture_url, ''), '') "
                                + "FROM threads t "
                                + "LEFT JOIN _cached_participant_thread_info p "
                                + "ON p.thread_key = t.thread_key "
                                + "LEFT JOIN contacts c ON c.id = t.thread_key "
                                + "WHERE t.thread_name LIKE ? OR p.thread_name LIKE ? "
                                + "OR p.thread_name_without_nickname LIKE ? OR c.name LIKE ? "
                                + "ORDER BY t.last_activity_timestamp_ms DESC LIMIT 1",
                        new String[]{requestedTitle, likeArg, likeArg, likeArg, likeArg});
            }
            if (!threadCursor.moveToFirst()) {
                threadCursor.close();
                threadCursor = null;
                // Tier 3: Java fold-scan
                threadCursor = database.rawQuery(
                        "SELECT t.thread_key, "
                                + "COALESCE(NULLIF(t.thread_name, ''), "
                                + "NULLIF(p.thread_name, ''), NULLIF(c.name, ''), ''), "
                                + "COALESCE(NULLIF(t.thread_picture_url, ''), "
                                + "NULLIF(p.other_participant_profile_picture_url, ''), "
                                + "NULLIF(c.profile_picture_url, ''), '') "
                                + "FROM threads t "
                                + "LEFT JOIN _cached_participant_thread_info p "
                                + "ON p.thread_key = t.thread_key "
                                + "LEFT JOIN contacts c ON c.id = t.thread_key "
                                + "ORDER BY t.last_activity_timestamp_ms DESC LIMIT 300",
                        null);
                String foldedRequest = fold(requestedTitle);
                boolean found = false;
                while (threadCursor.moveToNext()) {
                    String name = threadCursor.isNull(1) ? "" : threadCursor.getString(1);
                    if (fold(name).contains(foldedRequest) || foldedRequest.contains(fold(name))) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    Log.w("OrcaPeek", "ORCAPEek_PREVIEW_SCHEMA_FAILED schema=threads"
                            + " reason=no-match-all-tiers");
                    return Result.error("no-match");
                }
            }
            long threadKey = threadCursor.getLong(0);
            String resolvedTitle = textOr(threadCursor, 1, requestedTitle);
            String avatarUrl = textOr(threadCursor, 2, "");
            long selfContactPk = parseSelfContactPk(file.getName());

            // Modern schema: attachments joined via message_id
            messageCursor = database.rawQuery(
                    "SELECT m.text, "
                            + "CASE WHEN m.sticker_id IS NOT NULL THEN 4 "
                            + "WHEN a.attachment_type IS NOT NULL THEN a.attachment_type "
                            + "WHEN m.text IS NULL OR m.text = '' THEN 3 ELSE 0 END, "
                            + "m.sender_id, COALESCE(NULLIF(c.first_name, ''), c.name, ''), "
                            + "m.timestamp_ms, "
                            + "a.attachment_type, a.preview_url, a.preview_width, a.preview_height "
                            + "FROM messages m "
                            + "LEFT JOIN contacts c ON c.id = m.sender_id "
                            + "LEFT JOIN attachments a ON a.message_id = m.message_id "
                            + "WHERE m.thread_key = ? AND COALESCE(m.is_unsent, 0) = 0 "
                            + "AND COALESCE(m.message_hidden_state, 0) = 0 "
                            + "ORDER BY m.timestamp_ms DESC LIMIT " + MAX_MESSAGES,
                    new String[]{Long.toString(threadKey)});
            List<Message> messages = readMessages(messageCursor, selfContactPk);
            Collections.reverse(messages);
            Log.i("OrcaPeek", "ORCAPEek_PREVIEW_SCHEMA schema=threads threadKey="
                    + threadKey + " messages=" + messages.size());
            if (messages.isEmpty()) {
                return new Result(true, resolvedTitle, avatarUrl, messages,
                        "Cuộc trò chuyện chưa có tin nhắn hiển thị");
            }
            return new Result(true, resolvedTitle, avatarUrl, messages,
                    String.format(Locale.ROOT, "%d tin nhắn gần nhất", messages.size()));
        } catch (Throwable error) {
            Log.w("OrcaPeek", "ORCAPEek_PREVIEW_SCHEMA_FAILED schema=threads error="
                    + error.getClass().getSimpleName());
            return Result.error("Lỗi đọc dữ liệu: " + error.getClass().getSimpleName());
        } finally {
            if (messageCursor != null) messageCursor.close();
            if (threadCursor != null) threadCursor.close();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Shared row reader — uniform columns across both schemas:
    //   0=text, 1=resolvedType, 2=senderId, 3=senderName, 4=timestampMs,
    //   5=attachmentType, 6=mediaUrlOrPath, 7=previewWidth, 8=previewHeight
    // ──────────────────────────────────────────────────────────────────────────
    private static List<Message> readMessages(Cursor cursor, long selfContactPk) {
        List<Message> messages = new ArrayList<>();
        while (cursor.moveToNext()) {
            String body      = textOr(cursor, 0, "").trim();
            int resolvedType = cursor.isNull(1) ? 0 : cursor.getInt(1);
            long senderPk    = cursor.isNull(2) ? -1L : cursor.getLong(2);
            String sender    = textOr(cursor, 3, "");
            long ts          = cursor.isNull(4) ? 0L : cursor.getLong(4);

            int attachType   = cursor.isNull(5) ? -1 : cursor.getInt(5);
            String mediaUrl  = textOr(cursor, 6, null);
            int mediaW       = cursor.isNull(7) ? 0 : cursor.getInt(7);
            int mediaH       = cursor.isNull(8) ? 0 : cursor.getInt(8);

            boolean isMedia = attachType == TYPE_PHOTO || attachType == TYPE_GIF
                    || attachType == TYPE_VIDEO;
            boolean isSticker = resolvedType == CTYPE_STICKER || attachType == TYPE_STICKER;
            boolean isXma = attachType == TYPE_XMA;

            if (isMedia && mediaUrl != null) {
                body = "";
            } else if (isSticker) {
                body = "";
            } else if (isXma && body.isEmpty()) {
                body = "🔗 Liên kết đính kèm";
            } else if (body.isEmpty()) {
                body = attachmentLabel(resolvedType, attachType);
            }

            messages.add(new Message(
                    sender, body,
                    senderPk == selfContactPk,
                    ts,
                    resolvedType,
                    mediaUrl,
                    mediaW,
                    mediaH,
                    isSticker,
                    attachType == TYPE_GIF));
        }
        return messages;
    }

    private static long parseSelfContactPk(String databaseName) {
        try {
            return Long.parseLong(databaseName.substring("msys_database_".length()));
        } catch (Throwable ignored) {
            return Long.MIN_VALUE;
        }
    }

    static String fold(String s) {
        if (s == null) return "";
        String nfd = Normalizer.normalize(s, Normalizer.Form.NFD);
        StringBuilder sb = new StringBuilder(nfd.length());
        for (int i = 0; i < nfd.length(); i++) {
            char c = nfd.charAt(i);
            if (Character.getType(c) != Character.NON_SPACING_MARK) {
                sb.append(c);
            }
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    private static String attachmentLabel(int contentType, int attachType) {
        if (attachType == TYPE_PHOTO) return "📷 Ảnh";
        if (attachType == TYPE_GIF)   return "GIF";
        if (attachType == TYPE_VIDEO) return "🎬 Video";
        if (attachType == TYPE_XMA)   return "🔗 Liên kết đính kèm";
        if (contentType == CTYPE_STICKER) return "Nhãn dán";
        return "Nội dung đính kèm";
    }

    private static String textOr(Cursor cursor, int column, String fallback) {
        return cursor.isNull(column) ? fallback : cursor.getString(column);
    }

    public static final class Result {
        public final boolean success;
        public final String title;
        public final String avatarUrl;
        public final List<Message> messages;
        public final String status;

        private Result(boolean success, String title, String avatarUrl,
                List<Message> messages, String status) {
            this.success = success;
            this.title = title;
            this.avatarUrl = avatarUrl;
            this.messages = messages;
            this.status = status;
        }

        private static Result error(String status) {
            return new Result(false, "Better Messenger", "", Collections.emptyList(), status);
        }
    }

    public static final class Message {
        public final String sender;
        public final String body;
        public final boolean outgoing;
        public final long timestampMs;
        public final int contentType;
        public final String mediaUrl;
        public final int mediaWidth;
        public final int mediaHeight;
        public final boolean isSticker;
        public final boolean isGif;

        private Message(String sender, String body, boolean outgoing,
                long timestampMs, int contentType,
                String mediaUrl, int mediaWidth, int mediaHeight,
                boolean isSticker, boolean isGif) {
            this.sender = sender;
            this.body = body;
            this.outgoing = outgoing;
            this.timestampMs = timestampMs;
            this.contentType = contentType;
            this.mediaUrl = mediaUrl;
            this.mediaWidth = mediaWidth;
            this.mediaHeight = mediaHeight;
            this.isSticker = isSticker;
            this.isGif = isGif;
        }
    }
}
