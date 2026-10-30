package com.aitiniu.orcapeek.data;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class RecentMessagesRepositoryTest {
    @Test
    public void extractsEnglishUnreadCount() {
        assertEquals("phic", RecentMessagesRepository.extractConversationTitle(
                "phic, 14 new messages"));
    }

    @Test
    public void extractsVietnameseUnreadCount() {
        assertEquals("Ngọc Trâm", RecentMessagesRepository.extractConversationTitle(
                "Ngọc Trâm, 3 tin nhắn mới"));
    }
}
