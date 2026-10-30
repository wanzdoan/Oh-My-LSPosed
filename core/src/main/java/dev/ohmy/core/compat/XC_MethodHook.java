package dev.ohmy.core.compat;

import java.lang.reflect.Member;

/** Private callback adapter for the two imported legacy features. No legacy API is bundled. */
public abstract class XC_MethodHook {
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable { }
    protected void afterHookedMethod(MethodHookParam param) throws Throwable { }

    public static final class MethodHookParam {
        public Member method;
        public Object thisObject;
        public Object[] args;
        private Object result;
        private Throwable throwable;
        boolean early;
        public Object getResult() { return result; }
        public Throwable getThrowable() { return throwable; }
        public boolean hasThrowable() { return throwable != null; }
        public void setResult(Object value) { result = value; throwable = null; early = true; }
        public void setThrowable(Throwable value) { throwable = value; result = null; early = true; }
        void finish(Object value, Throwable error) { result = value; throwable = error; early = false; }
    }
}
