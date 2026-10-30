package dev.alastorkaneki.chromescript;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Material-themed manager rendered inside stock Chrome's own Activity. */
final class ManagerDialog {
    private static final Map<Activity, Dialog> OPEN = new WeakHashMap<>();

    private ManagerDialog() { }

    static void show(Activity activity, String currentUrl) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            activity.runOnUiThread(() -> show(activity, currentUrl));
            return;
        }
        if (activity.isFinishing() || activity.isDestroyed()) return;
        synchronized (OPEN) {
            Dialog old = OPEN.remove(activity);
            if (old != null) old.dismiss();
        }

        String url = currentUrl == null ? "" : currentUrl;
        ScrollView scroll = new ScrollView(activity);
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(activity, 16);
        body.setPadding(pad, pad, pad, pad);
        scroll.addView(body);

        List<Userscript> scripts = MonkeyStore.list(activity);
        TextView summary = Ui.label(activity,
                scripts.size() + " installed • " + (MonkeyStore.globalEnabled(activity)
                        ? "runtime enabled" : "runtime paused"), 14, false);
        body.addView(summary);

        Switch enabled = new Switch(activity);
        enabled.setText("Enable userscripts");
        enabled.setChecked(MonkeyStore.globalEnabled(activity));
        enabled.setOnCheckedChangeListener((view, checked) -> {
            MonkeyStore.globalEnabled(activity, checked);
            ChromeRuntime.refresh(activity);
        });
        body.addView(enabled);

        if (!url.isEmpty()) {
            TextView current = Ui.label(activity, "Current page\n" + url, 12, false);
            current.setPadding(0, pad, 0, Ui.dp(activity, 6));
            current.setTextIsSelectable(true);
            body.addView(current);

            Button site = Ui.button(activity,
                    MonkeyStore.hostDisabled(activity, url) ? "Enable this site" : "Disable this site");
            site.setOnClickListener(view -> {
                boolean disabled = !MonkeyStore.hostDisabled(activity, url);
                MonkeyStore.hostDisabled(activity, url, disabled);
                ChromeRuntime.refresh(activity);
                show(activity, url);
            });
            body.addView(site);

            if (ForkSupport.isInstallablePage(url)) {
                Button installCurrent = Ui.button(activity, "Install from current page");
                installCurrent.setOnClickListener(view -> {
                    close(activity);
                    InstallDialog.show(activity, url);
                });
                body.addView(installCurrent);
            }
        }

        Button addJs = Ui.button(activity, "New JavaScript");
        addJs.setOnClickListener(view -> edit(activity,
                UserscriptMetadataParser.parse(jsTemplate(), "new.user.js", ""), url));
        Button addCss = Ui.button(activity, "New CSS style");
        addCss.setOnClickListener(view -> edit(activity,
                UserscriptMetadataParser.parse(cssTemplate(), "new.user.css", ""), url));
        body.addView(Ui.row(activity, addJs, addCss));

        Button installUrl = Ui.button(activity, "Install URL");
        installUrl.setOnClickListener(view -> promptInstallUrl(activity));
        Button importClip = Ui.button(activity, "Import clipboard");
        importClip.setOnClickListener(view -> importClipboard(activity, url));
        body.addView(Ui.row(activity, installUrl, importClip));

        Button greasy = Ui.button(activity, "Greasy Fork");
        greasy.setOnClickListener(view -> ForkSupport.openSite(activity, "greasyfork.org"));
        Button sleazy = Ui.button(activity, "Sleazy Fork");
        sleazy.setOnClickListener(view -> ForkSupport.openSite(activity, "sleazyfork.org"));
        body.addView(Ui.row(activity, greasy, sleazy));

        Button update = Ui.button(activity, "Check updates");
        update.setOnClickListener(view -> {
            Ui.toast(activity, "Checking userscript updates…");
            MonkeyStore.updateAll(activity, (ok, message, script) -> {
                Ui.toast(activity, message);
                if (ok) activity.runOnUiThread(() -> show(activity, url));
            });
        });
        Button commands = Ui.button(activity, "Page commands");
        commands.setOnClickListener(view -> {
            close(activity);
            if (!ChromeRuntime.commands(activity)) Ui.toast(activity, "No page command menu available");
        });
        body.addView(Ui.row(activity, update, commands));

        Button backup = Ui.button(activity, "Copy backup");
        backup.setOnClickListener(view -> copyBackup(activity));
        Button restore = Ui.button(activity, "Restore clipboard");
        restore.setOnClickListener(view -> restoreClipboard(activity, url));
        body.addView(Ui.row(activity, backup, restore));

        TextView heading = Ui.label(activity, "Installed scripts", 20, true);
        heading.setPadding(0, pad, 0, Ui.dp(activity, 8));
        body.addView(heading);

        if (scripts.isEmpty()) {
            body.addView(Ui.label(activity,
                    "No userscripts yet. Install from a URL, Fork page, clipboard, or create one.",
                    14, false));
        } else {
            for (Userscript script : scripts) body.addView(scriptCard(activity, script, url));
        }

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("ChromeScript Userscripts")
                .setView(scroll)
                .setNegativeButton("Close", null)
                .create();
        dialog.setOnDismissListener(ignored -> {
            synchronized (OPEN) {
                if (OPEN.get(activity) == dialog) OPEN.remove(activity);
            }
        });
        synchronized (OPEN) {
            OPEN.put(activity, dialog);
        }
        dialog.show();
    }

    private static View scriptCard(Activity activity, Userscript script, String currentUrl) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(activity, 12);
        card.setPadding(pad, pad, pad, pad);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Ui.color(activity,
                android.R.attr.colorBackground, Color.rgb(245, 245, 245)));
        background.setStroke(Ui.dp(activity, 1), Ui.color(activity,
                android.R.attr.textColorSecondary, Color.GRAY));
        background.setCornerRadius(Ui.dp(activity, 14));
        card.setBackground(background);

        Switch toggle = new Switch(activity);
        toggle.setText(script.name);
        toggle.setChecked(script.enabled);
        toggle.setTextSize(17);
        toggle.setOnCheckedChangeListener((view, checked) -> {
            try {
                MonkeyStore.setEnabled(activity, script.id, checked);
                ChromeRuntime.refresh(activity);
            } catch (Throwable error) {
                Ui.toast(activity, "Cannot change script: " + safe(error));
            }
        });
        card.addView(toggle);

        String details = (Userscript.KIND_CSS.equals(script.kind) ? "CSS" : "JS")
                + " • v" + script.version
                + " • " + script.matches.size() + " match rule(s)";
        if (!currentUrl.isEmpty()) {
            details += UrlPatternMatcher.matches(script, currentUrl)
                    ? " • matches this page" : " • does not match this page";
        }
        TextView meta = Ui.label(activity, details, 12, false);
        meta.setTextColor(Ui.secondaryText(activity));
        card.addView(meta);

        Button edit = Ui.button(activity, "Edit");
        edit.setOnClickListener(view -> edit(activity, script, currentUrl));
        Button run = Ui.button(activity, "Run now");
        run.setOnClickListener(view -> {
            close(activity);
            Ui.toast(activity, ChromeRuntime.runNow(activity, script)
                    ? "Executed " + script.name : "Could not inject into this page");
        });
        Button delete = Ui.button(activity, "Delete");
        delete.setOnClickListener(view -> confirmDelete(activity, script, currentUrl));
        card.addView(Ui.row(activity, edit, run, delete));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, Ui.dp(activity, 10));
        card.setLayoutParams(params);
        return card;
    }

    private static void edit(Activity activity, Userscript original, String currentUrl) {
        EditText source = new EditText(activity);
        source.setText(original.source);
        source.setTextSize(13);
        source.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        source.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        source.setMinLines(16);
        source.setHorizontallyScrolling(true);
        source.setPadding(Ui.dp(activity, 12), Ui.dp(activity, 8),
                Ui.dp(activity, 12), Ui.dp(activity, 8));

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Edit " + original.name)
                .setView(source)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    try {
                        Userscript edited = original.copy();
                        edited.source = source.getText().toString();
                        MonkeyStore.save(activity, edited);
                        ChromeRuntime.refresh(activity);
                        dialog.dismiss();
                        show(activity, currentUrl);
                    } catch (Throwable error) {
                        Ui.toast(activity, "Cannot save: " + safe(error));
                    }
                }));
        dialog.show();
    }

    private static void promptInstallUrl(Activity activity) {
        EditText input = new EditText(activity);
        input.setHint("https://example.org/script.user.js");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        new AlertDialog.Builder(activity)
                .setTitle("Install userscript URL")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Review", (dialog, which) -> {
                    String value = input.getText().toString().trim();
                    if (!value.isEmpty()) {
                        close(activity);
                        InstallDialog.show(activity, value);
                    }
                }).show();
    }

    private static void importClipboard(Activity activity, String currentUrl) {
        String text = clipboardText(activity);
        if (text == null || text.trim().isEmpty()) {
            Ui.toast(activity, "Clipboard is empty");
            return;
        }
        if (!ViolentmonkeyCompat.looksLikeUserscript(text)) {
            Ui.toast(activity, "Clipboard does not contain a valid userscript metadata block");
            return;
        }
        MonkeyStore.importText(activity, text, "clipboard.user.js", "",
                (ok, message, script) -> {
                    Ui.toast(activity, message);
                    if (ok) activity.runOnUiThread(() -> show(activity, currentUrl));
                });
    }

    private static void copyBackup(Activity activity) {
        try {
            String backup = MonkeyStore.backup(activity);
            ClipboardManager clipboard = (ClipboardManager)
                    activity.getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("ChromeScript backup", backup));
            Ui.toast(activity, "Backup copied to clipboard");
        } catch (Throwable error) {
            Ui.toast(activity, "Cannot create backup: " + safe(error));
        }
    }

    private static void restoreClipboard(Activity activity, String currentUrl) {
        String text = clipboardText(activity);
        if (text == null || text.trim().isEmpty()) {
            Ui.toast(activity, "Clipboard is empty");
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle("Restore userscripts")
                .setMessage("Replace the current userscript library with the clipboard backup?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Restore", (dialog, which) -> {
                    try {
                        MonkeyStore.restore(activity, text);
                        ChromeRuntime.refresh(activity);
                        show(activity, currentUrl);
                    } catch (Throwable error) {
                        Ui.toast(activity, "Invalid backup: " + safe(error));
                    }
                }).show();
    }

    private static void confirmDelete(Activity activity, Userscript script, String currentUrl) {
        new AlertDialog.Builder(activity)
                .setTitle("Delete " + script.name + "?")
                .setMessage("This cannot be undone unless you have a backup.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    try {
                        MonkeyStore.remove(activity, script.id);
                        ChromeRuntime.refresh(activity);
                        show(activity, currentUrl);
                    } catch (Throwable error) {
                        Ui.toast(activity, "Cannot delete: " + safe(error));
                    }
                }).show();
    }

    private static String clipboardText(Activity activity) {
        try {
            ClipboardManager clipboard = (ClipboardManager)
                    activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (!clipboard.hasPrimaryClip()) return null;
            CharSequence value = clipboard.getPrimaryClip().getItemAt(0).coerceToText(activity);
            return value == null ? null : value.toString();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void close(Activity activity) {
        synchronized (OPEN) {
            Dialog dialog = OPEN.remove(activity);
            if (dialog != null) dialog.dismiss();
        }
    }

    private static String jsTemplate() {
        return "// ==UserScript==\n"
                + "// @name New userscript\n"
                + "// @namespace local.chromescript\n"
                + "// @version 1.0.0\n"
                + "// @match *://*/*\n"
                + "// @run-at document-end\n"
                + "// ==/UserScript==\n\n"
                + "console.log('ChromeScript is running');\n";
    }

    private static String cssTemplate() {
        return "/* ==UserStyle==\n"
                + "@name New userstyle\n"
                + "@namespace local.chromescript\n"
                + "@version 1.0.0\n"
                + "@match *://*/*\n"
                + "==/UserStyle== */\n\n"
                + "body { }\n";
    }

    private static String safe(Throwable error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
}
