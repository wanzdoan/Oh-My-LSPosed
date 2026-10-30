// language: Java, file: IosActionMenu.java, runtime: Android API 26+, target: OrcaPeek v0.9
// v0.9: Icon on LEFT, label on RIGHT (matches iOS reference). Added Ghim/Pin. Better icon set.
package com.aitiniu.orcapeek.ui;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class IosActionMenu {
    // Priority order matches iOS Messenger context menu
    private static final List<String> PRIORITY = Arrays.asList(
            "mark as unread", "mark as read",
            "đánh dấu là chưa đọc", "đánh dấu là đã đọc",
            "pin", "ghim",
            "mute", "tắt thông báo",
            "archive", "lưu trữ",
            "delete", "xóa");

    private IosActionMenu() {
    }

    public static Result create(Activity activity, Dialog dialog, View originalContent) {
        List<Action> all = new ArrayList<>();
        collect(originalContent, originalContent, all);
        List<Action> primary = new ArrayList<>();
        for (String wanted : PRIORITY) {
            for (Action action : all) {
                if (!primary.contains(action) && action.normalized.equals(wanted)) {
                    primary.add(action);
                    break;
                }
            }
        }
        if (!containsNormalized(primary, "delete") && !containsNormalized(primary, "xóa")) {
            boolean vietnamese = containsNormalized(all, "lưu trữ")
                    || containsNormalized(all, "tắt thông báo");
            primary.add(new Action(vietnamese ? "Xóa" : "Delete",
                    vietnamese ? "xóa" : "delete", null, originalContent));
        }
        StringBuilder labels = new StringBuilder();
        for (Action action : all) {
            if (labels.length() > 0) {
                labels.append('|');
            }
            labels.append(action.normalized);
        }
        Log.i("OrcaPeek", "ORCAPEek_IOS_MENU_DISCOVERED all=" + all.size()
                + " primary=" + primary.size() + " labels=" + labels);
        if (primary.size() < 3) {
            return new Result(null, all.size());
        }

        LinearLayout menu = new LinearLayout(activity);
        menu.setOrientation(LinearLayout.VERTICAL);
        // v0.9: glass menu background — matches card glassmorphism
        GradientDrawable menuBg = new GradientDrawable();
        menuBg.setColor(Color.argb(250, 255, 255, 255));
        menuBg.setCornerRadius(dp(activity, 18));
        menuBg.setStroke(1, Color.argb(40, 0, 0, 0));
        menu.setBackground(menuBg);
        menu.setElevation(dp(activity, 8));
        menu.setClipToOutline(true);

        for (int index = 0; index < primary.size(); index++) {
            Action action = primary.get(index);
            menu.addView(createRow(activity, dialog, action), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 50)));
            if (index < primary.size() - 1) {
                View divider = new View(activity);
                divider.setBackgroundColor(Color.argb(22, 0, 0, 0));
                LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 1);
                dividerParams.leftMargin = dp(activity, 16);
                dividerParams.rightMargin = dp(activity, 16);
                menu.addView(divider, dividerParams);
            }
        }
        return new Result(menu, all.size());
    }

    private static View createRow(Activity activity, Dialog dialog, Action action) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(activity, 18), 0, dp(activity, 18), 0);
        row.setClickable(true);
        row.setFocusable(true);
        row.setContentDescription(action.label);
        row.setBackground(new RippleDrawable(
                ColorStateList.valueOf(Color.argb(20, 0, 0, 0)),
                new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT), null));

        TextView label = new TextView(activity);
        label.setText(action.label);
        label.setTextColor(Color.rgb(28, 28, 30));
        label.setTextSize(15f);
        label.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        label.setIncludeFontPadding(false);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelParams.rightMargin = dp(activity, 16);
        row.addView(label, labelParams);
        row.addView(new ActionIcon(activity, action.normalized),
                new LinearLayout.LayoutParams(dp(activity, 21), dp(activity, 21)));

        row.setOnClickListener(view -> {
            View target = action.target;
            if (target != null && target.isAttachedToWindow()) {
                target.performClick();
            } else {
                invokeDeferred(dialog, action);
            }
        });
        return row;
    }

    private static void collect(View view, View stop, List<Action> output) {
        String label = accessibilityLabel(view);
        String normalized = normalize(label);
        if (isKnown(normalized)) {
            View target = clickableAncestor(view, stop);
            if (target != null && !containsTarget(output, target)) {
                output.add(new Action(displayLabel(label), normalized, target, stop));
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                collect(group.getChildAt(index), stop, output);
            }
        }
    }

    private static String accessibilityLabel(View view) {
        CharSequence raw = view instanceof TextView ? ((TextView) view).getText() : null;
        if (raw == null || raw.length() == 0) {
            raw = view.getContentDescription();
        }
        AccessibilityNodeInfo info = null;
        try {
            if (raw == null || raw.length() == 0) {
                info = view.createAccessibilityNodeInfo();
                raw = info == null ? null : info.getText();
                if ((raw == null || raw.length() == 0) && info != null) {
                    raw = info.getContentDescription();
                }
            }
        } catch (Throwable ignored) {
            raw = null;
        } finally {
            if (info != null) {
                info.recycle();
            }
        }
        return raw == null ? "" : raw.toString().trim();
    }

    private static String normalize(String label) {
        String value = label.toLowerCase(Locale.ROOT).trim();
        int comma = value.indexOf(',');
        if (comma > 0) {
            value = value.substring(0, comma).trim();
        }
        return value;
    }

    private static String displayLabel(String label) {
        int comma = label.indexOf(',');
        return comma > 0 ? label.substring(0, comma).trim() : label;
    }

    private static boolean containsTarget(List<Action> actions, View target) {
        for (Action action : actions) {
            if (action.target == target) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsNormalized(List<Action> actions, String normalized) {
        for (Action action : actions) {
            if (action.normalized.equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    private static void invokeDeferred(Dialog dialog, Action action) {
        View target = findAction(action.root, action.normalized);
        if (target != null) {
            target.performClick();
            return;
        }
        scrollForward(action.root);
        action.root.postDelayed(() -> {
            View delayed = findAction(action.root, action.normalized);
            if (delayed != null) {
                Log.i("OrcaPeek", "ORCAPEek_IOS_MENU_DEFERRED_ACTION label="
                        + action.normalized);
                delayed.performClick();
            } else {
                Log.w("OrcaPeek", "ORCAPEek_IOS_MENU_ACTION_MISSING label="
                        + action.normalized);
                dialog.dismiss();
            }
        }, 180L);
    }

    private static View findAction(View view, String wanted) {
        if (isOwnOverlay(view)) {
            return null;
        }
        if (normalize(accessibilityLabel(view)).equals(wanted)) {
            View target = clickableAncestor(view, view.getRootView());
            if (target != null) {
                return target;
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                View found = findAction(group.getChildAt(index), wanted);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void scrollForward(View view) {
        if (isOwnOverlay(view)) {
            return;
        }
        if (view.canScrollVertically(1)) {
            view.scrollBy(0, Math.max(view.getHeight(), 400));
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                scrollForward(group.getChildAt(index));
            }
        }
    }

    private static boolean isOwnOverlay(View view) {
        return "orcapeek.dialog.ios-preview.v08".equals(view.getTag());
    }

    private static boolean isKnown(String value) {
        return value.equals("archive")            || value.equals("lưu trữ")
                || value.equals("mute")           || value.equals("tắt thông báo")
                || value.equals("mark as read")   || value.equals("mark as unread")
                || value.equals("đánh dấu là đã đọc")
                || value.equals("đánh dấu là chưa đọc")
                || value.equals("delete")         || value.equals("xóa")
                || value.equals("pin")            || value.equals("ghim")
                || value.startsWith("pin to")     || value.startsWith("ghim lên")
                || value.equals("restrict")       || value.equals("hạn chế")
                || value.equals("block")          || value.equals("chặn")
                || value.startsWith("create group chat")
                || value.startsWith("tạo nhóm chat");
    }

    private static View clickableAncestor(View view, View stop) {
        View current = view;
        while (current != null) {
            if (current.isClickable() || current.hasOnClickListeners()) {
                return current;
            }
            if (current == stop) {
                break;
            }
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return null;
    }

    private static GradientDrawable roundRect(int color, int radius) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(radius);
        return background;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static final class Action {
        private final String label;
        private final String normalized;
        private final View target;
        private final View root;

        private Action(String label, String normalized, View target, View root) {
            this.label = label;
            this.normalized = normalized;
            this.target = target;
            this.root = root;
        }
    }

    public static final class Result {
        public final View view;
        public final int discoveredActions;

        public Result(View view, int discoveredActions) {
            this.view = view;
            this.discoveredActions = discoveredActions;
        }
    }
}
