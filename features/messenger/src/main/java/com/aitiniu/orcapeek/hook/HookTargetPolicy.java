package com.aitiniu.orcapeek.hook;

public final class HookTargetPolicy {
    public static final String TARGET_PACKAGE = "com.facebook.orca";
    public static final String INBOX_MENU_CLASS =
            "com.facebook.messaging.threadlist.threaditemmenu.InboxThreadContextMenuFragment";

    private HookTargetPolicy() {
    }

    public static boolean isTargetPackage(String packageName) {
        return TARGET_PACKAGE.equals(packageName);
    }

    public static boolean isInboxMenu(Object candidate) {
        return candidate != null
                && INBOX_MENU_CLASS.equals(candidate.getClass().getName());
    }
}
