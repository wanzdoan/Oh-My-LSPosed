package com.aitiniu.orcapeek.ui;

public final class OverlayGeometry {
    private OverlayGeometry() {
    }

    public static int clampTop(int requestedTop, int contentHeight) {
        if (contentHeight <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(requestedTop, contentHeight - 1));
    }

    public static int clampHeight(int requestedHeight, int top, int contentHeight, int fallback) {
        if (contentHeight <= 0) {
            return Math.max(1, fallback);
        }
        int available = Math.max(1, contentHeight - clampTop(top, contentHeight));
        int desired = requestedHeight > 0 ? requestedHeight : fallback;
        return Math.max(1, Math.min(desired, available));
    }
}
