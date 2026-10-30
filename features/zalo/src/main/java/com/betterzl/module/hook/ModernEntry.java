package com.betterzl.module.hook;

import dev.ohmy.core.GatedModule;
import dev.ohmy.core.compat.XposedBridge;
import com.betterzl.module.settings.ZaloOptions;

public final class ModernEntry extends GatedModule {
    public ModernEntry() { super("zalo", "com.zing.zalo"); }
    @Override protected void featureReady(PackageReadyParam param) {
        XposedBridge.attach(this);
        ZaloOptions options = ZaloOptions.from(getRemotePreferences(ZaloOptions.GROUP).getAll());
        log(android.util.Log.INFO, "BetterZalo.Settings", "Applied " + options);
        HookEntry.INSTANCE.install(param.getClassLoader(), options);
        if (options.originalPhoto()) OriginalPhotoHook.INSTANCE.install(this, param.getClassLoader(), param.getApplicationInfo().sourceDir);
    }
}
