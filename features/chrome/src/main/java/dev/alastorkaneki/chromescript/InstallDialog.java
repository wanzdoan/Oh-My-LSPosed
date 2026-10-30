package dev.alastorkaneki.chromescript;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.net.URI;

/** Downloads, validates, previews, and installs a userscript without patching Chrome. */
final class InstallDialog {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private InstallDialog() { }

    static void show(Activity activity, String pageUrl) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        boolean[] cancelled = {false};
        AlertDialog progress = new AlertDialog.Builder(activity)
                .setTitle("Install userscript")
                .setMessage("Resolving and validating source…")
                .setNegativeButton("Cancel", (dialog, which) -> cancelled[0] = true)
                .create();
        progress.setOnCancelListener(dialog -> cancelled[0] = true);
        progress.show();

        Thread worker = new Thread(() -> {
            try {
                String resolved = ForkSupport.resolveInstallUrl(pageUrl);
                String source = MonkeyStore.fetch(resolved);
                Userscript parsed = UserscriptMetadataParser.parse(
                        source, fileName(resolved), resolved);
                MAIN.post(() -> {
                    if (cancelled[0] || activity.isFinishing() || activity.isDestroyed()) return;
                    progress.dismiss();
                    preview(activity, resolved, source, parsed);
                });
            } catch (Throwable error) {
                MAIN.post(() -> {
                    progress.dismiss();
                    if (!cancelled[0]) Ui.toast(activity,
                            "Cannot install userscript: " + safe(error));
                });
            }
        }, "ChromeScriptInstaller");
        worker.start();
    }

    private static void preview(
            Activity activity,
            String url,
            String source,
            Userscript script
    ) {
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(activity, 16);
        body.setPadding(pad, pad, pad, pad);
        body.addView(Ui.label(activity, script.name, 20, true));
        body.addView(Ui.label(activity,
                "Version " + script.version + (script.author.isEmpty() ? "" : " • " + script.author),
                14, false));
        body.addView(Ui.label(activity, url, 12, false));

        TextView warning = Ui.label(activity,
                "Userscripts can read and modify matching pages. Install only code you trust.",
                14, true);
        warning.setPadding(0, pad, 0, pad);
        body.addView(warning);

        TextView code = Ui.label(activity,
                source.length() > 16000 ? source.substring(0, 16000) + "\n…" : source,
                11, false);
        code.setTextIsSelectable(true);
        body.addView(code, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(activity);
        scroll.addView(body);
        new AlertDialog.Builder(activity)
                .setTitle("Review installation")
                .setView(scroll)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Install", (dialog, which) ->
                        MonkeyStore.importText(activity, source, fileName(url), url,
                                (ok, message, installed) -> {
                                    Ui.toast(activity, message);
                                    if (ok) ChromeRuntime.refresh(activity);
                                }))
                .show();
    }

    private static String fileName(String url) {
        try {
            String path = new URI(url).getPath();
            if (path != null && path.contains("/")) path = path.substring(path.lastIndexOf('/') + 1);
            return path == null || path.isEmpty() ? "remote.user.js" : path;
        } catch (Throwable ignored) {
            return "remote.user.js";
        }
    }

    private static String safe(Throwable error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
}

