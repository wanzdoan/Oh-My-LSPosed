package com.betterzl.module.settings;

import java.util.Map;

/** Process-start settings, independent of other modules and target app storage. */
public record ZaloOptions(boolean liquidGlass, boolean hideSeen, boolean hideTyping,
                          boolean customRingtone, boolean antiRecall, boolean originalPhoto) {
    public static final String GROUP = "better_zalo_settings";
    public static final String GLASS = "liquid_glass";
    public static final String SEEN = "hide_seen";
    public static final String TYPING = "hide_typing";
    public static final String RINGTONE = "custom_ringtone";
    public static final String RECALL = "anti_recall";
    public static final String ORIGINAL = "original_photo";

    public static ZaloOptions from(Map<String, ?> values) {
        return new ZaloOptions(read(values, GLASS, true), read(values, SEEN, true),
                read(values, TYPING, true), read(values, RINGTONE, true),
                read(values, RECALL, true), read(values, ORIGINAL, false));
    }

    private static boolean read(Map<String, ?> values, String key, boolean fallback) {
        Object value = values.get(key);
        return value instanceof Boolean ? (Boolean) value : fallback;
    }
}
