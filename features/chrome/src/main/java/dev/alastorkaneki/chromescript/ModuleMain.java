package dev.alastorkaneki.chromescript;

import android.app.Activity;
import android.util.Log;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;
import dev.ohmy.core.GatedModule;
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam;

/** Modern LibXposed entry point loaded only in stock Chrome processes. */
public final class ModuleMain extends GatedModule {
    public ModuleMain() { super("chrome", "com.android.chrome"); }
    private static final String TAG = "ChromeScript";
    private static final String TARGET_PACKAGE = "com.android.chrome";
    private boolean navigationHooksInstalled;

    @Override
    public void featureReady(PackageReadyParam param) {
        if (!param.isFirstPackage() || !TARGET_PACKAGE.equals(param.getPackageName())) return;

        ModuleLog.install((priority, message, error) -> {
            if (error == null) log(priority, TAG, message);
            else log(priority, TAG, message, error);
        });

        try {
            hookActivityLifecycle();
            log(Log.INFO, TAG, "Hooks installed for stock Chrome at "
                    + param.getApplicationInfo().sourceDir);
        } catch (Throwable error) {
            log(Log.ERROR, TAG, "Unable to install Chrome activity hooks", error);
        }
    }

    private void hookActivityLifecycle() throws ReflectiveOperationException {
        Method onResume = Activity.class.getDeclaredMethod("onResume");
        Method onPause = Activity.class.getDeclaredMethod("onPause");
        Method onDestroy = Activity.class.getDeclaredMethod("onDestroy");

        hook(onResume)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    Object owner = chain.getThisObject();
                    if (owner instanceof Activity) {
                        Activity activity = (Activity) owner;
                        ensureChromeNavigationHooks(activity.getClass().getClassLoader());
                        ChromeRuntime.start(activity);
                    }
                    return result;
                });

        hook(onPause)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    Object owner = chain.getThisObject();
                    if (owner instanceof Activity) ChromeRuntime.stop((Activity) owner);
                    return result;
                });

        hook(onDestroy)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    Object owner = chain.getThisObject();
                    if (owner instanceof Activity) ChromeRuntime.stop((Activity) owner);
                    return result;
                });
    }

    private synchronized void ensureChromeNavigationHooks(ClassLoader classLoader) {
        if (navigationHooksInstalled) return;
        try {
            hookChromeNavigation(classLoader);
            navigationHooksInstalled = true;
            log(Log.INFO, TAG, "Chromium navigation hooks installed");
        } catch (Throwable error) {
            log(Log.WARN, TAG, "Chromium navigation hooks are not available yet", error);
        }
    }

    private void hookChromeNavigation(ClassLoader classLoader) throws ReflectiveOperationException {
        Class<?> observer = Class.forName(
                "org.chromium.content.browser.webcontents.WebContentsObserverProxy",
                false,
                classLoader);

        // These callbacks are stable JNI entry points in Chromium. Resetting the runtime here
        // covers first load, same-URL reloads, history restores, and renderer replacement.
        hook(observer.getDeclaredMethod("primaryMainDocumentElementAvailable"))
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    ChromeRuntime.navigationEvent();
                    return result;
                });

        for (Method method : observer.getDeclaredMethods()) {
            if (!"documentLoadedInPrimaryMainFrame".equals(method.getName())) continue;
            hook(method)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        ChromeRuntime.navigationEvent();
                        return result;
                    });
        }
    }
}
