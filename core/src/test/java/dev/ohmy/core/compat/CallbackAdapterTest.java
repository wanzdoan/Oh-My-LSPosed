package dev.ohmy.core.compat;

import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class CallbackAdapterTest {
    private XposedInterface.Hooker installed;
    private int originalCalls;
    public String target(String value) { return value; }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }
    private void install(XC_MethodHook callback) throws Exception {
        XposedInterface.HookBuilder builder = proxy(XposedInterface.HookBuilder.class, (p,m,a) -> {
            if (m.getName().equals("intercept")) { installed = (XposedInterface.Hooker) a[0]; return null; }
            return p;
        });
        XposedBridge.attach(proxy(XposedInterface.class, (p,m,a) -> m.getName().equals("hook") ? builder : null));
        XposedBridge.hookMethod(getClass().getDeclaredMethod("target", String.class), callback);
    }
    private Object call(Throwable originalError) throws Throwable {
        return installed.intercept(proxy(XposedInterface.Chain.class, (p,m,a) -> switch (m.getName()) {
            case "getThisObject" -> this;
            case "getArgs" -> Arrays.asList("input");
            case "proceed" -> {
                originalCalls++;
                if (originalError != null) throw originalError;
                yield ((Object[]) a[0])[0];
            }
            default -> null;
        }));
    }
    @Test public void earlyResultSkipsOriginalAndStillRunsAfter() throws Throwable {
        install(new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) { p.setResult("early"); }
            @Override protected void afterHookedMethod(MethodHookParam p) { p.setResult(p.getResult()+"/after"); }
        });
        assertEquals("early/after", call(null)); assertEquals(0, originalCalls);
    }
    @Test public void modifiedArgumentsReachOriginal() throws Throwable {
        install(new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) { p.args[0] = "changed"; }
        });
        assertEquals("changed", call(null)); assertEquals(1, originalCalls);
    }
    @Test public void originalExceptionIsPreserved() throws Throwable {
        install(new XC_MethodHook() {});
        IllegalStateException error = new IllegalStateException("host");
        try { call(error); fail(); } catch (IllegalStateException caught) { assertSame(error, caught); }
    }
    @Test public void callbackFailureDoesNotReplaceHostResult() throws Throwable {
        install(new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) { p.setResult("broken"); throw new IllegalStateException(); }
        });
        assertEquals("input", call(null));
    }
}
