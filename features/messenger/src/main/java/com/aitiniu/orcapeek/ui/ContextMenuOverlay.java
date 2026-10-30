package com.aitiniu.orcapeek.ui;

import android.app.Activity;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class ContextMenuOverlay {
    private static final Map<View, Boolean> WRAPPED =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile long lastWrappedAt;

    private ContextMenuOverlay() {
    }

    public static View wrap(Object fragment, View actionDrawer) {
        if (actionDrawer == null || WRAPPED.put(actionDrawer, Boolean.TRUE) != null) {
            return actionDrawer;
        }
        Activity activity = findActivity(fragment);
        PendingPreviewStore.Snapshot snapshot = PendingPreviewStore.peekFresh();
        if (activity == null || snapshot == null) {
            return actionDrawer;
        }
        lastWrappedAt = SystemClock.elapsedRealtime();

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setClipToPadding(false);
        root.setPadding(dp(activity, 10), dp(activity, 8), dp(activity, 10), dp(activity, 8));

        Runnable opener = () -> {
            View source = snapshot.sourceView();
            activity.onBackPressed();
            if (source != null) {
                source.postDelayed(source::performClick, 160L);
            }
        };
        View preview = IosConversationPreview.create(activity, snapshot, opener);
        root.addView(preview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams drawerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        drawerParams.topMargin = dp(activity, 10);
        root.addView(actionDrawer, drawerParams);
        return root;
    }

    public static boolean wasRecentlyWrapped() {
        return SystemClock.elapsedRealtime() - lastWrappedAt < 1_500L;
    }

    private static Activity findActivity(Object fragment) {
        try {
            Method method = fragment.getClass().getMethod("getActivity");
            Object result = method.invoke(fragment);
            return result instanceof Activity ? (Activity) result : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
