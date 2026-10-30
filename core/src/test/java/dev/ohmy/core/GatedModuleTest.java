package dev.ohmy.core;

import android.content.SharedPreferences;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;
import java.lang.reflect.Proxy;
import org.junit.Test;
import static org.junit.Assert.*;

public class GatedModuleTest {
    private static class Feature extends GatedModule {
        int loaded, packages, ready;
        Feature() { super("test", "com.test"); }
        @Override protected void featureLoaded(ModuleLoadedParam p) { loaded++; }
        @Override protected void featurePackageLoaded(PackageLoadedParam p) { packages++; }
        @Override protected void featureReady(PackageReadyParam p) { ready++; }
    }
    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }
    private Feature start(String process, boolean enabled, boolean failPrefs) {
        Feature f = new Feature();
        SharedPreferences prefs = proxy(SharedPreferences.class, (p,m,a) -> {
            if (m.getName().equals("getBoolean")) {
                assertEquals("test.enabled", a[0]);
                assertEquals(false, a[1]);
                return enabled;
            }
            return null;
        });
        XposedInterface framework = proxy(XposedInterface.class, (p,m,a) -> {
            if (m.getName().equals("getRemotePreferences")) {
                assertEquals(ModuleIds.SETTINGS, a[0]);
                if (failPrefs) throw new SecurityException("Unavailable");
                return prefs;
            }
            return null;
        });
        f.attachFramework(framework, () -> {});
        f.onModuleLoaded(proxy(XposedModuleInterface.ModuleLoadedParam.class,
                (p,m,a) -> m.getName().equals("getProcessName") ? process : null));
        return f;
    }
    private void ready(Feature f, String pkg, boolean first) {
        f.onPackageReady(proxy(XposedModuleInterface.PackageReadyParam.class, (p,m,a) -> switch (m.getName()) {
            case "getPackageName" -> pkg;
            case "isFirstPackage" -> first;
            default -> null;
        }));
    }
    @Test public void disabledFeatureInstallsNothing() {
        Feature f = start("com.test", false, false);
        ready(f, "com.test", true);
        assertEquals(0, f.loaded); assertEquals(0, f.ready);
    }
    @Test public void enabledFeatureOnlyReceivesItsMainPackage() {
        Feature f = start("com.test", true, false);
        ready(f, "other.app", true); ready(f, "com.test", false);
        assertEquals(0, f.ready);
        ready(f, "com.test", true);
        assertEquals(1, f.loaded); assertEquals(1, f.ready);
    }
    @Test public void secondaryProcessInstallsNothing() {
        Feature f = start("com.test:push", true, false);
        ready(f, "com.test", true);
        assertEquals(0, f.loaded); assertEquals(0, f.ready);
    }
    @Test public void missingPreferenceChannelFailsClosed() {
        Feature f = start("com.test", true, true);
        ready(f, "com.test", true);
        assertEquals(0, f.loaded); assertEquals(0, f.ready);
    }
}
