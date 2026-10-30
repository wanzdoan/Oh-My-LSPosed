package dev.alastorkaneki.chromescript;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.WeakHashMap;

/** Polls the selected Chrome tab and injects matching scripts after navigation. */
final class ChromeRuntime implements Runnable {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<Activity, ChromeRuntime> ACTIVE = new WeakHashMap<>();
    private static final long[] RETRY_DELAYS_MS = {
            0, 250, 500, 850, 1_300, 2_000, 3_000, 4_500, 6_500, 9_000
    };

    private final Activity activity;
    private String lastUrl = "";
    private int lastTabIdentity;
    private int lastWebIdentity;
    private long lastGeneration = -1;
    private int injectionAttempt;
    private long nextInjectionAt;
    private int unresolvedPolls;
    private boolean unresolvedLogged;

    private ChromeRuntime(Activity activity) {
        this.activity = activity;
    }

    static void start(Activity activity) {
        if (!isChromeBrowserActivity(activity)) return;
        stop(activity);
        ChromeRuntime runtime = new ChromeRuntime(activity);
        synchronized (ACTIVE) {
            ACTIVE.put(activity, runtime);
        }
        AppMenuIntegrator.start(activity);
        MAIN.post(runtime);
        ModuleLog.i("Runtime started for " + activity.getClass().getName());
    }

    static void stop(Activity activity) {
        ChromeRuntime runtime;
        synchronized (ACTIVE) {
            runtime = ACTIVE.remove(activity);
        }
        if (runtime != null) MAIN.removeCallbacks(runtime);
        AppMenuIntegrator.stop(activity);
    }

    static String url(Activity activity) {
        ChromeBridge.Page page = ChromeBridge.page(activity);
        return page == null ? "" : page.url;
    }

    static boolean runNow(Activity activity, Userscript script) {
        ChromeBridge.Page page = ChromeBridge.page(activity);
        return page != null && !page.incognito && ChromeBridge.exec(
                page, ScriptInjector.buildPayload(script, page.url, true));
    }

    static boolean commands(Activity activity) {
        ChromeBridge.Page page = ChromeBridge.page(activity);
        return page != null && ChromeBridge.exec(page, ScriptInjector.commandMenuPayload());
    }

    static void refresh(Activity activity) {
        ChromeRuntime runtime;
        synchronized (ACTIVE) {
            runtime = ACTIVE.get(activity);
        }
        if (runtime != null) {
            runtime.lastGeneration = -1;
            runtime.injectionAttempt = 0;
            runtime.nextInjectionAt = 0;
            MAIN.removeCallbacks(runtime);
            MAIN.post(runtime);
        }
    }

    /** Called by the Chromium navigation lifecycle hooks, including same-URL reloads. */
    static void navigationEvent() {
        MAIN.post(() -> {
            List<ChromeRuntime> runtimes;
            synchronized (ACTIVE) {
                runtimes = new ArrayList<>(ACTIVE.values());
            }
            for (ChromeRuntime runtime : runtimes) {
                runtime.resetInjection();
                MAIN.removeCallbacks(runtime);
                MAIN.post(runtime);
            }
        });
    }

    private void resetInjection() {
        lastUrl = "";
        lastTabIdentity = 0;
        lastWebIdentity = 0;
        lastGeneration = -1;
        injectionAttempt = 0;
        nextInjectionAt = 0;
    }

    @Override
    public void run() {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        try {
            ChromeBridge.Page page = ChromeBridge.page(activity);
            if (page != null && !page.incognito && UrlPatternMatcher.isInjectableScheme(page.url)) {
                unresolvedPolls = 0;
                unresolvedLogged = false;
                ForkSupport.injectInstallClickBridge(page);
                ForkSupport.consumeInstallMarker(activity, page);

                int tabIdentity = System.identityHashCode(page.tab);
                int webIdentity = System.identityHashCode(page.web);
                long generation = MonkeyStore.generation();
                boolean navigationChanged = tabIdentity != lastTabIdentity
                        || webIdentity != lastWebIdentity
                        || !page.url.equals(lastUrl);
                boolean libraryChanged = generation != lastGeneration;
                if (navigationChanged || libraryChanged) {
                    lastTabIdentity = tabIdentity;
                    lastWebIdentity = webIdentity;
                    lastUrl = page.url;
                    lastGeneration = generation;
                    injectionAttempt = 0;
                    nextInjectionAt = 0;
                    if (navigationChanged) ForkSupport.maybePromptDirectInstall(activity, page.url);
                }

                long now = SystemClock.uptimeMillis();
                // Keep checking after the initial retries: same-URL reloads and renderer
                // replacements must recover even when a Chrome navigation hook is unavailable.
                // The payload's per-document guard prevents duplicate execution.
                if (now >= nextInjectionAt) {
                    List<Userscript> scripts = MonkeyStore.matching(activity, page.url);
                    if (injectionAttempt == 0) {
                        ModuleLog.i("Auto-check at " + page.url + ": installed="
                                + MonkeyStore.list(activity).size() + ", matched=" + scripts.size());
                    }
                    int dispatched = 0;
                    for (Userscript script : scripts) {
                        if (ChromeBridge.exec(page,
                                ScriptInjector.buildPayload(script, page.url, false))) {
                            dispatched++;
                        }
                    }
                    injectionAttempt = Math.min(injectionAttempt + 1, RETRY_DELAYS_MS.length);
                    nextInjectionAt = now + (injectionAttempt < RETRY_DELAYS_MS.length
                            ? RETRY_DELAYS_MS[injectionAttempt] : 2_000);
                    if (injectionAttempt == 1) {
                        if (dispatched > 0) {
                            ModuleLog.i("Auto-injection scheduled for " + dispatched
                                    + " script(s) at " + page.url);
                        } else if (!scripts.isEmpty()) {
                            ModuleLog.w("Matched " + scripts.size()
                                    + " script(s), but Chrome rejected JavaScript execution", null);
                        }
                    }
                }
            } else if (!unresolvedLogged && ++unresolvedPolls >= 10) {
                unresolvedLogged = true;
                ModuleLog.w("Unable to resolve an injectable active Chrome tab", null);
            }
        } catch (Throwable error) {
            ModuleLog.w("Runtime poll failed", error);
        }
        MAIN.postDelayed(this, 350);
    }

    private static boolean isChromeBrowserActivity(Activity activity) {
        String name = activity.getClass().getName();
        return name.startsWith("org.chromium.chrome.browser.")
                || name.startsWith("com.google.android.apps.chrome.");
    }
}
