package com.locket.gold;

import dev.ohmy.core.GatedModule;
import dev.ohmy.core.compat.XposedBridge;

public final class ModernEntry extends GatedModule {
    public ModernEntry() { super("locket", "com.locket.Locket"); }
    @Override protected void featureReady(PackageReadyParam param) {
        XposedBridge.attach(this);
        new HookMain().install(param.getPackageName(), hostProcess, param.getClassLoader());
    }
}
