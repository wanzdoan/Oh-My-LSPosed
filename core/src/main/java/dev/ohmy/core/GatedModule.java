package dev.ohmy.core;

import android.content.SharedPreferences;
import android.util.Log;
import io.github.libxposed.api.XposedModule;

/** All feature callbacks pass through one process-scoped, fail-closed gate. */
public abstract class GatedModule extends XposedModule {
    private final String id;
    private final String[] targets;
    private boolean enabled;
    protected String hostProcess = "";

    protected GatedModule(String id, String... targets) {
        this.id = id;
        this.targets = targets;
    }

    @Override public final void onModuleLoaded(ModuleLoadedParam param) {
        hostProcess = param.getProcessName();
        boolean target = false;
        for (String pkg : targets) if (pkg.equals(hostProcess)) target = true;
        if (!target) return;
        try {
            SharedPreferences prefs = getRemotePreferences(ModuleIds.SETTINGS);
            enabled = prefs.getBoolean(ModuleIds.enabledKey(id), false);
            log(Log.INFO, "OhMy/" + id, "process=" + hostProcess + " enabled=" + enabled);
            if (enabled) featureLoaded(param);
        } catch (Throwable error) {
            enabled = false;
            log(Log.ERROR, "OhMy/" + id, "Initialization failed; feature disabled", error);
        }
    }

    private boolean accepts(String pkg) {
        if (!enabled) return false;
        for (String target : targets) if (target.equals(pkg) && target.equals(hostProcess)) return true;
        return false;
    }

    @Override public final void onPackageLoaded(PackageLoadedParam param) {
        if (param.isFirstPackage() && accepts(param.getPackageName())) {
            try { featurePackageLoaded(param); }
            catch (Throwable error) { log(Log.ERROR, "OhMy/" + id, "Package callback failed", error); }
        }
    }

    @Override public final void onPackageReady(PackageReadyParam param) {
        if (param.isFirstPackage() && accepts(param.getPackageName())) {
            try { featureReady(param); }
            catch (Throwable error) { log(Log.ERROR, "OhMy/" + id, "Hook installation failed", error); }
        }
    }

    protected void featureLoaded(ModuleLoadedParam param) { }
    protected void featurePackageLoaded(PackageLoadedParam param) { }
    protected void featureReady(PackageReadyParam param) { }
}
