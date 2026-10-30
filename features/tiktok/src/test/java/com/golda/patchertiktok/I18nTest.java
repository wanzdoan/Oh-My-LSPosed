package com.golda.patchertiktok;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.Locale;
import java.util.Map;

import org.junit.Test;

public class I18nTest {
    @Test
    public void everyTableHasEveryString() {
        for (Map.Entry<String, String[]> table : I18n.tables().entrySet()) {
            assertEquals(table.getKey(), I18n.S.values().length, table.getValue().length);
            for (String value : table.getValue()) assertFalse(table.getKey(), value.trim().isEmpty());
        }
    }

    @Test
    public void vietnameseUsesRecoveredTranslation() {
        I18n.use(Locale.forLanguageTag("vi-VN"));
        assertEquals("Chặn quảng cáo", I18n.get(I18n.S.ADS));
        assertEquals("Bảng tin", I18n.get(I18n.S.SECTION_FEED));
        assertEquals("Giữ chế độ ẩn giao diện", I18n.get(I18n.S.REMEMBER_CLEAN));
        assertEquals("Hình trong hình", I18n.get(I18n.S.PIP_FEED));
        assertEquals("TiktokPatchXposed", I18n.get(I18n.S.APP_NAME));
        I18n.use(Locale.ENGLISH);
    }

    @Test
    public void vietnamesePreservesEveryFormatArgument() {
        String[] english = I18n.tables().get("en");
        String[] vietnamese = I18n.tables().get("vi");
        java.util.regex.Pattern format = java.util.regex.Pattern.compile("%(?:\\d+\\$)?[ds]");
        for (int i = 0; i < english.length; i++) {
            java.util.List<String> expected = new java.util.ArrayList<>();
            java.util.List<String> actual = new java.util.ArrayList<>();
            java.util.regex.Matcher source = format.matcher(english[i]);
            java.util.regex.Matcher translated = format.matcher(vietnamese[i]);
            while (source.find()) expected.add(source.group());
            while (translated.find()) actual.add(translated.group());
            assertEquals(I18n.S.values()[i].name(), expected, actual);
        }
    }

    @Test
    public void arabicHasItsOwnTable() {
        I18n.use(new Locale("ar"));
        assertEquals("حظر الإعلانات", I18n.get(I18n.S.ADS));
        I18n.use(Locale.ENGLISH);
    }

    @Test
    public void unknownLanguageFallsBackToEnglish() {
        I18n.use(new Locale("ja"));
        assertEquals("Block ads", I18n.get(I18n.S.ADS));
        I18n.use(new Locale("kk"));
        assertEquals("Блокировать рекламу", I18n.get(I18n.S.ADS));
        I18n.use(new Locale("in"));
        assertEquals("Blokir iklan", I18n.get(I18n.S.ADS));
        I18n.use(Locale.ENGLISH);
    }
}
