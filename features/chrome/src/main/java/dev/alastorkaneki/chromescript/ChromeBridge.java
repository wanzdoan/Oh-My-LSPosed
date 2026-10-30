package dev.alastorkaneki.chromescript;

import android.app.Activity;
import android.content.Context;
import android.view.View;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Reflection bridge to Chrome's active Tab, WebContents, and main RenderFrameHost. */
final class ChromeBridge {
    static final class Page {
        final String url;
        final Object tab;
        final Object web;
        final boolean incognito;

        Page(String url, Object tab, Object web, boolean incognito) {
            this.url = url;
            this.tab = tab;
            this.web = web;
            this.incognito = incognito;
        }
    }

    private static final int USERSCRIPT_WORLD_ID = 5287;
    private static final String ACTIVITY_TAB_PROVIDER =
            "org.chromium.chrome.browser.ActivityTabProvider";
    private static final String TAB_TYPE = "org.chromium.chrome.browser.tab.Tab";
    private static final Map<Activity, WeakReference<Object>> CACHE = new WeakHashMap<>();

    static Page page(Activity activity) {
        // Chrome's public Java names are mostly stripped in release builds. The provider type and
        // Supplier#get contract are retained, however, and are substantially more reliable than
        // looking for source-level accessor names such as getActivityTabProvider().
        Object suppliedTab = tabFromTypedActivityApi(activity);
        Page supplied = fromTab(suppliedTab);
        if (supplied != null && !supplied.url.isEmpty()) {
            remember(activity, suppliedTab);
            return supplied;
        }

        Object currentWebContents = call(activity, "getCurrentWebContents");
        Page direct = fromWeb(null, currentWebContents);
        if (direct != null && !direct.url.isEmpty()) return direct;

        Object provider = call(activity, "getActivityTabProvider");
        suppliedTab = call(provider, "get");
        supplied = fromTab(suppliedTab);
        if (supplied != null && !supplied.url.isEmpty()) {
            remember(activity, suppliedTab);
            return supplied;
        }

        Object tab = null;
        synchronized (CACHE) {
            WeakReference<Object> reference = CACHE.get(activity);
            if (reference != null) tab = reference.get();
        }
        Page cached = fromTab(tab);
        if (cached != null && !cached.url.isEmpty()) return cached;

        for (String name : new String[]{"getActivityTab", "getCurrentTab", "getTab", "getSelectedTab"}) {
            Object value = call(activity, name);
            Page page = fromTab(value);
            if (page != null && !page.url.isEmpty()) {
                remember(activity, value);
                return page;
            }
        }

        tab = find(activity);
        if (tab == null) return direct;
        remember(activity, tab);
        return fromTab(tab);
    }

    private static Object tabFromTypedActivityApi(Activity activity) {
        // Stock Chrome 152 keeps ActivityTabProvider as a field on the activity's base class. Its
        // field name is obfuscated (currently "u1"), so resolve it by the stable Chromium type.
        for (Field field : fields(activity.getClass())) {
            if (!ACTIVITY_TAB_PROVIDER.equals(field.getType().getName())) continue;
            try {
                field.setAccessible(true);
                Object tab = call(field.get(activity), "get");
                if (tab != null) return tab;
            } catch (Throwable ignored) { }
        }

        // Fallback for Chrome variants which expose the current tab directly through an
        // obfuscated zero-argument method while retaining the Tab return type.
        for (Method method : methods(activity.getClass())) {
            if (method.getParameterTypes().length != 0
                    || !TAB_TYPE.equals(method.getReturnType().getName())) continue;
            try {
                method.setAccessible(true);
                Object tab = method.invoke(activity);
                if (tab != null) return tab;
            } catch (Throwable ignored) { }
        }
        return null;
    }

    private static void remember(Activity activity, Object tab) {
        synchronized (CACHE) {
            CACHE.put(activity, new WeakReference<>(tab));
        }
    }

    /**
     * Executes on ordinary web pages. Current Chromium restricts evaluateJavaScript() to WebUI,
     * so the unrestricted test entry point is attempted first, then RenderFrameHost isolated-world
     * execution, then the old WebContents method for older Chrome versions.
     */
    static boolean exec(Page page, String javascript) {
        if (page == null || page.web == null || javascript == null) return false;

        if (invokeJavaScript(page.web, "evaluateJavaScriptForTests", javascript)) return true;

        Object frame = call(page.web, "getMainFrame");
        if (frame == null) frame = call(page.web, "getPrimaryMainFrame");
        if (frame != null
                && invokeJavaScript(frame, "executeJavaScriptInIsolatedWorld", javascript)) {
            return true;
        }

        return invokeJavaScript(page.web, "evaluateJavaScript", javascript);
    }

    private static boolean invokeJavaScript(Object target, String methodName, String javascript) {
        if (target == null) return false;
        for (Method method : methods(target.getClass())) {
            if (!methodName.equals(method.getName())) continue;
            Class<?>[] types = method.getParameterTypes();
            if (types.length == 0 || types[0] != String.class) continue;
            try {
                Object[] values = new Object[types.length];
                values[0] = javascript;
                for (int index = 1; index < types.length; index++) {
                    Class<?> type = types[index];
                    if (type == int.class || type == Integer.class) {
                        values[index] = USERSCRIPT_WORLD_ID;
                    } else if (type == long.class || type == Long.class) {
                        values[index] = (long) USERSCRIPT_WORLD_ID;
                    } else if (type == boolean.class || type == Boolean.class) {
                        values[index] = false;
                    } else {
                        values[index] = null;
                    }
                }
                method.setAccessible(true);
                method.invoke(target, values);
                return true;
            } catch (Throwable ignored) { }
        }
        return false;
    }

    private static Page fromTab(Object tab) {
        if (tab == null) return null;
        Object web = call(tab, "getWebContents");
        String url = url(call(tab, "getUrl"));
        if (url == null) url = url(call(tab, "getOriginalUrl"));
        Page webPage = fromWeb(tab, web);
        if ((url == null || url.isEmpty()) && webPage != null) url = webPage.url;
        Object incognito = call(tab, "isIncognito");
        boolean privateMode = incognito instanceof Boolean && (Boolean) incognito;
        if (!privateMode && webPage != null) privateMode = webPage.incognito;
        return new Page(url == null ? "" : url, tab, web, privateMode);
    }

    private static Page fromWeb(Object tab, Object web) {
        if (web == null) return null;
        String url = url(call(web, "getVisibleUrl"));
        if (url == null) url = url(call(web, "getLastCommittedUrl"));
        if (url == null) url = url(call(web, "getUrl"));
        Object incognito = call(web, "isIncognito");
        return new Page(
                url == null ? "" : url,
                tab == null ? web : tab,
                web,
                incognito instanceof Boolean && (Boolean) incognito
        );
    }

    private static Object find(Activity activity) {
        ArrayDeque<Object[]> queue = new ArrayDeque<>();
        IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();
        queue.add(new Object[]{activity, 0});
        int count = 0;

        while (!queue.isEmpty() && count++ < 520) {
            Object[] node = queue.removeFirst();
            Object value = node[0];
            int depth = (Integer) node[1];
            if (value == null || seen.put(value, true) != null) continue;
            if (tab(value)) return value;
            if (depth >= 7) continue;

            for (Field field : fields(value.getClass())) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                try {
                    field.setAccessible(true);
                    Object child = field.get(value);
                    if (tab(child)) return child;
                    if (traverse(child, activity.getClass().getClassLoader())) {
                        queue.addLast(new Object[]{child, depth + 1});
                    }
                } catch (Throwable ignored) { }
            }
        }
        return null;
    }

    private static boolean tab(Object value) {
        if (value == null) return false;
        Object web = call(value, "getWebContents");
        if (web == null) return false;
        return call(value, "getUrl") != null
                || call(value, "getOriginalUrl") != null
                || call(value, "isIncognito") != null;
    }

    private static boolean traverse(Object value, ClassLoader loader) {
        if (value == null
                || value instanceof Context
                || value instanceof View
                || value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof Class
                || value instanceof Thread) return false;
        String name = value.getClass().getName();
        return value.getClass().getClassLoader() == loader
                || name.startsWith("org.chromium")
                || name.startsWith("com.google.android.apps.chrome");
    }

    private static Object call(Object object, String name) {
        if (object == null) return null;
        for (Method method : methods(object.getClass())) {
            if (!name.equals(method.getName()) || method.getParameterTypes().length != 0) continue;
            try {
                method.setAccessible(true);
                return method.invoke(object);
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static String url(Object object) {
        if (object == null) return null;
        if (object instanceof CharSequence) return object.toString();
        Object spec = call(object, "getSpec");
        if (spec instanceof CharSequence) return spec.toString();

        // Release Chrome runs R8 over GURL. In Chrome 152 getSpec() is named k() and the URL is
        // stored in an obfuscated String field, while Object#toString() is left unchanged. Resolve
        // the value structurally so matching keeps working across Chrome obfuscation changes.
        if ("org.chromium.url.GURL".equals(object.getClass().getName())) {
            for (Field field : fields(object.getClass())) {
                if (field.getType() != String.class || Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    String value = (String) field.get(object);
                    if (hasScheme(value)) return value;
                } catch (Throwable ignored) { }
            }
            for (Method method : methods(object.getClass())) {
                if (method.getParameterTypes().length != 0
                        || method.getReturnType() != String.class) continue;
                try {
                    method.setAccessible(true);
                    String value = (String) method.invoke(object);
                    if (hasScheme(value)) return value;
                } catch (Throwable ignored) { }
            }
        }
        String value = object.toString();
        return hasScheme(value) ? value : null;
    }

    private static boolean hasScheme(String value) {
        if (value == null) return false;
        int colon = value.indexOf(':');
        if (colon <= 0) return false;
        for (int index = 0; index < colon; index++) {
            char character = value.charAt(index);
            if (!(Character.isLetterOrDigit(character)
                    || character == '+' || character == '-' || character == '.')) return false;
        }
        return Character.isLetter(value.charAt(0));
    }

    private static Method[] methods(Class<?> type) {
        List<Method> result = new ArrayList<>();
        for (Class<?> cursor = type; cursor != null && cursor != Object.class;
             cursor = cursor.getSuperclass()) {
            try { Collections.addAll(result, cursor.getDeclaredMethods()); }
            catch (Throwable ignored) { }
        }
        return result.toArray(new Method[0]);
    }

    private static Field[] fields(Class<?> type) {
        List<Field> result = new ArrayList<>();
        for (Class<?> cursor = type; cursor != null && cursor != Object.class;
             cursor = cursor.getSuperclass()) {
            try { Collections.addAll(result, cursor.getDeclaredFields()); }
            catch (Throwable ignored) { }
        }
        return result.toArray(new Field[0]);
    }
}
