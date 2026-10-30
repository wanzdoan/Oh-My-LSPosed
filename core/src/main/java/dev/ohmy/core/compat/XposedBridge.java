package dev.ohmy.core.compat;

import android.util.Log;
import java.lang.reflect.Executable;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import io.github.libxposed.api.XposedInterface;

public final class XposedBridge {
    private static XposedInterface backend;
    private XposedBridge() { }
    public static void attach(XposedInterface value) { backend = value; }
    public static void log(String message) { backend.log(Log.INFO, "OhMy/compat", message); }
    public static void log(Throwable error) { backend.log(Log.ERROR, "OhMy/compat", "Hook failed", error); }

    public static void hookAllMethods(Class<?> type, String name, XC_MethodHook callback) {
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().equals(name) && !Modifier.isAbstract(method.getModifiers())) hookMethod(method, callback);
        }
    }

    public static void hookMethod(Member member, XC_MethodHook callback) {
        backend.hook((Executable) member).setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH)
                .intercept(chain -> {
                    XC_MethodHook.MethodHookParam p = new XC_MethodHook.MethodHookParam();
                    p.method = member;
                    p.thisObject = chain.getThisObject();
                    p.args = chain.getArgs().toArray();
                    try { callback.beforeHookedMethod(p); }
                    catch (Throwable error) { log(error); p.finish(null, null); }
                    if (!p.early) {
                        try { p.finish(chain.proceed(p.args), null); }
                        catch (Throwable error) { p.finish(null, error); }
                    }
                    Object previous = p.getResult();
                    Throwable previousError = p.getThrowable();
                    try { callback.afterHookedMethod(p); }
                    catch (Throwable error) { log(error); p.finish(previous, previousError); }
                    if (p.hasThrowable()) throw p.getThrowable();
                    return p.getResult();
                });
    }

    public static Object invokeOriginalMethod(Member member, Object receiver, Object[] args) throws Throwable {
        try {
            return backend.getInvoker((Method) member).setType(XposedInterface.Invoker.Type.ORIGIN).invoke(receiver, args);
        } catch (java.lang.reflect.InvocationTargetException error) { throw error.getCause(); }
    }
}
