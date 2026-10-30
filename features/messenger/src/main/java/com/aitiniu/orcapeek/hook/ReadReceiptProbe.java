package com.aitiniu.orcapeek.hook;

import android.os.SystemClock;
import android.view.View;
import java.util.concurrent.atomic.AtomicLong;

/** Tracks a single preview-to-conversation transition; never inspects MSYS tasks. */
public final class ReadReceiptProbe {
    private static final AtomicLong ARMED_UNTIL = new AtomicLong();
    private ReadReceiptProbe() {}

    public static void beginFromConversationRow(View view) {
        if (view == null || !HookTargetPolicy.TARGET_PACKAGE.equals(
                view.getContext().getPackageName()) || view.getContentDescription() == null) {
            return;
        }
        if (SystemClock.elapsedRealtime() <= ARMED_UNTIL.get()
                && ReadReceiptGuard.matchesConversation(view.getContentDescription().toString())) {
            ARMED_UNTIL.set(0L);
            ReadReceiptGuard.onConversationOpened();
        }
    }

    public static void armNextConversationClick() {
        ARMED_UNTIL.set(SystemClock.elapsedRealtime() + 15_000L);
    }
}
