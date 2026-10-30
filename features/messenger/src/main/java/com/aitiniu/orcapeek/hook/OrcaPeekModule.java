package com.aitiniu.orcapeek.hook;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import com.aitiniu.orcapeek.ui.ContextMenuOverlay;
import com.aitiniu.orcapeek.ui.DialogPreviewOverlay;
import com.aitiniu.orcapeek.ui.PendingPreviewStore;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.ohmy.core.GatedModule;
import io.github.libxposed.api.XposedModuleInterface;

public final class OrcaPeekModule extends GatedModule {
    public OrcaPeekModule() { super("messenger", "com.facebook.orca"); }
    private static final String TAG = "OrcaPeek";
    private static final String BASE_CONTEXT_MENU =
            "com.facebook.xapp.messaging.contextmenu.fragment.AbstractContextMenuFragment";
    private static final AtomicBoolean ACTIVITY_HOOK_INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean DIALOG_HOOK_INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean LONG_PRESS_HOOK_INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean CLICK_PROBE_HOOK_INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean MESSENGER_HOOKS_INSTALLED = new AtomicBoolean();
    private boolean mainProcess;
    private static final AtomicBoolean RECOVERY_ATTEMPTED = new AtomicBoolean();

    @Override
    public void featureLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        mainProcess = HookTargetPolicy.TARGET_PACKAGE.equals(param.getProcessName());
        log(Log.INFO, TAG, "ORCAPEek_MODULE_LOADED process=" + param.getProcessName()
                + " api=" + getApiVersion()
                + " framework=" + getFrameworkName() + "/" + getFrameworkVersion());
    }

    @Override
    public void featurePackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        if (!mainProcess || !HookTargetPolicy.isTargetPackage(param.getPackageName())) {
            return;
        }
        log(Log.INFO, TAG, "ORCAPEek_PACKAGE_LOADED process="
                + param.getApplicationInfo().processName
                + " first=" + param.isFirstPackage());
        if (ACTIVITY_HOOK_INSTALLED.compareAndSet(false, true)) {
            install("activity canary", this::installActivityCanary);
        }
        if (LONG_PRESS_HOOK_INSTALLED.compareAndSet(false, true)) {
            install("long-press capture", this::installLongPressCapture);
        }
        if (CLICK_PROBE_HOOK_INSTALLED.compareAndSet(false, true)) {
            install("conversation click probe", this::installConversationClickProbe);
        }
        if (DIALOG_HOOK_INSTALLED.compareAndSet(false, true)) {
            install("dialog fallback", this::installDialogFallback);
        }
    }

    @Override
    public void featureReady(XposedModuleInterface.PackageReadyParam param) {
        if (!mainProcess || !HookTargetPolicy.isTargetPackage(param.getPackageName())) {
            return;
        }
        log(Log.INFO, TAG, "ORCAPEek_PACKAGE_READY classLoader="
                + param.getClassLoader().getClass().getName());
        if (MESSENGER_HOOKS_INSTALLED.compareAndSet(false, true)) {
            install("inbox menu canary", () -> installInboxMenuCanary(param.getClassLoader()));
            install("context menu overlay", () -> installContextMenuOverlay(param.getClassLoader()));
            // Never hook MSYS/JNI dispatchers: LSPosed may capture an unresolved
            // native entry point before Messenger registers its implementation.
            log(Log.INFO, TAG, "ORCAPEek_NATIVE_PROBES_DISABLED build=0.10.4");
        }
    }

    private void installActivityCanary() throws ReflectiveOperationException {
        Method onResume = Activity.class.getDeclaredMethod("onResume");
        onResume.setAccessible(true);
        hook(onResume)
                .setId("orcapeek.canary.activity.onResume")
                .intercept(chain -> {
                    Object result = chain.proceed();
                    Activity activity = (Activity) chain.getThisObject();
                    if (HookTargetPolicy.TARGET_PACKAGE.equals(activity.getPackageName())) {
                        safely("activity initialization", () -> {
                            if (RECOVERY_ATTEMPTED.compareAndSet(false, true)) {
                                ReadReceiptGuard.recover(activity.getApplicationContext());
                            }
                        });
                        log(Log.INFO, TAG, "ORCAPEek_ACTIVITY_RESUMED class="
                                + activity.getClass().getName());
                    }
                    return result;
                });
    }

    private void installLongPressCapture() throws ReflectiveOperationException {
        Method[] methods = View.class.getDeclaredMethods();
        int installed = 0;
        for (Method method : methods) {
            if (!"performLongClick".equals(method.getName())
                    || method.getReturnType() != boolean.class) {
                continue;
            }
            method.setAccessible(true);
            final int hookIndex = installed++;
            hook(method)
                    .setId("orcapeek.capture.performLongClick." + hookIndex)
                    .intercept(chain -> {
                        View target = (View) chain.getThisObject();
                        PendingPreviewStore.Candidate candidate =
                                PendingPreviewStore.captureCandidate(target);
                        Object result = chain.proceed();
                        if (Boolean.TRUE.equals(result) && candidate != null) {
                            safely("long press", () -> {
                                PendingPreviewStore.commit(candidate);
                                CharSequence description = target.getContentDescription();
                                ReadReceiptGuard.arm(target.getContext(),
                                        description == null ? "" : description.toString());
                                ReadReceiptProbe.armNextConversationClick();
                            });
                            log(Log.INFO, TAG, "ORCAPEek_LONG_PRESS_CAPTURED class="
                                    + target.getClass().getName());
                        }
                        return result;
                    });
        }
        if (installed == 0) {
            throw new NoSuchMethodException("View.performLongClick");
        }
    }

    private void installConversationClickProbe() throws ReflectiveOperationException {
        Method performClick = View.class.getDeclaredMethod("performClick");
        performClick.setAccessible(true);
        hook(performClick)
                .setId("orcapeek.probe.performClick")
                .intercept(chain -> {
                    View target = (View) chain.getThisObject();
                    safely("conversation click", () -> ReadReceiptProbe.beginFromConversationRow(target));
                    return chain.proceed();
                });
    }

    private void installDialogFallback() throws ReflectiveOperationException {
        Method show = Dialog.class.getDeclaredMethod("show");
        show.setAccessible(true);
        hook(show)
                .setId("orcapeek.dialog.show")
                .intercept(chain -> {
                    Object result = chain.proceed();
                    Dialog dialog = (Dialog) chain.getThisObject();
                    Window window = dialog.getWindow();
                    if (window != null) {
                        View decor = window.getDecorView();
                        decor.postDelayed(() -> {
                            if (dialog.isShowing() && decor.isAttachedToWindow()) {
                                safely("dialog preview", () -> {
                                    boolean attached = DialogPreviewOverlay.attach(dialog);
                                    log(Log.INFO, TAG, "ORCAPEek_DIALOG_SHOWN overlay=" + attached);
                                });
                            }
                        }, 350L);
                    }
                    return result;
                });

        Method dismiss = Dialog.class.getDeclaredMethod("dismiss");
        dismiss.setAccessible(true);
        hook(dismiss)
                .setId("orcapeek.dialog.dismiss")
                .intercept(chain -> {
                    Dialog dialog = (Dialog) chain.getThisObject();
                    boolean attached = DialogPreviewOverlay.isAttached(dialog);
                    boolean opening = attached && DialogPreviewOverlay.consumeOpening(dialog);
                    Object result = chain.proceed();
                    if (attached) {
                        safely("dialog cleanup", () -> {
                            if (!opening) {
                                ReadReceiptGuard.restore("preview-dismissed");
                            }
                            DialogPreviewOverlay.clear(dialog);
                        });
                        log(Log.INFO, TAG, "ORCAPEek_DIALOG_DISMISSED opening=" + opening);
                    }
                    return result;
                });
    }

    private void installInboxMenuCanary(ClassLoader classLoader)
            throws ReflectiveOperationException {
        Class<?> menuClass = Class.forName(
                HookTargetPolicy.INBOX_MENU_CLASS, false, classLoader);
        Method onStart = menuClass.getDeclaredMethod("onStart");
        onStart.setAccessible(true);
        hook(onStart)
                .setId("orcapeek.canary.inboxMenu.onStart")
                .intercept(chain -> {
                    log(Log.INFO, TAG, "ORCAPEek_INBOX_CONTEXT_MENU_OPENED");
                    return chain.proceed();
                });
    }

    private void installContextMenuOverlay(ClassLoader classLoader)
            throws ReflectiveOperationException {
        Class<?> baseClass = Class.forName(BASE_CONTEXT_MENU, false, classLoader);
        Method onCreateView = baseClass.getDeclaredMethod(
                "onCreateView",
                LayoutInflater.class,
                ViewGroup.class,
                Bundle.class);
        onCreateView.setAccessible(true);
        hook(onCreateView)
                .setId("orcapeek.overlay.contextMenu.onCreateView")
                .intercept(chain -> {
                    Object original = chain.proceed();
                    Object fragment = chain.getThisObject();
                    if (!(original instanceof View) || !HookTargetPolicy.isInboxMenu(fragment)) {
                        return original;
                    }
                    try {
                        View wrapped = ContextMenuOverlay.wrap(fragment, (View) original);
                        log(Log.INFO, TAG, "ORCAPEek_CONTEXT_MENU_WRAPPED result="
                                + wrapped.getClass().getName());
                        return wrapped;
                    } catch (Throwable error) {
                        log(Log.WARN, TAG, "ORCAPEek_OVERLAY_FAILED", error);
                        return original;
                    }
                });
    }

    private void safely(String operation, Runnable action) {
        try {
            action.run();
        } catch (Throwable error) {
            log(Log.WARN, TAG, "ORCAPEek_CALLBACK_FAILED " + operation, error);
        }
    }

    private void install(String name, Installer installer) {
        try {
            installer.install();
            log(Log.INFO, TAG, "ORCAPEek_INSTALLED " + name);
        } catch (Throwable error) {
            log(Log.ERROR, TAG, "ORCAPEek_INSTALL_FAILED " + name, error);
        }
    }

    @FunctionalInterface
    private interface Installer {
        void install() throws Throwable;
    }
}
