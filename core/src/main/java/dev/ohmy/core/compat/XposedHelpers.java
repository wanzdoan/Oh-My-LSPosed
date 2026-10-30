package dev.ohmy.core.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

public final class XposedHelpers {
    private static final Map<Object, Map<String, Object>> EXTRAS = Collections.synchronizedMap(new WeakHashMap<>());
    private XposedHelpers() { }
    public static Class<?> findClassIfExists(String name, ClassLoader loader) {
        try { return Class.forName(name, false, loader); } catch (ClassNotFoundException ignored) { return null; }
    }
    public static void findAndHookMethod(Class<?> type, String name, Object... args) {
        Class<?>[] params = new Class<?>[args.length - 1];
        for (int i = 0; i < params.length; i++) params[i] = (Class<?>) args[i];
        try {
            Method method = type.getDeclaredMethod(name, params);
            method.setAccessible(true);
            XposedBridge.hookMethod(method, (XC_MethodHook) args[args.length - 1]);
        } catch (ReflectiveOperationException error) { throw new IllegalArgumentException(type.getName() + "." + name, error); }
    }
    public static Object getObjectField(Object object, String name) {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(object); }
            catch (NoSuchFieldException ignored) { }
            catch (IllegalAccessException error) { throw new IllegalStateException(error); }
        }
        throw new IllegalArgumentException(name);
    }
    public static void setAdditionalInstanceField(Object object, String name, Object value) {
        synchronized (EXTRAS) { EXTRAS.computeIfAbsent(object, key -> new HashMap<>()).put(name, value); }
    }
    public static Object getAdditionalInstanceField(Object object, String name) {
        synchronized (EXTRAS) { Map<String, Object> map = EXTRAS.get(object); return map == null ? null : map.get(name); }
    }
    public static Object callMethod(Object object, String name, Object... args) { return invoke(object.getClass(), object, name, args); }
    public static Object callStaticMethod(Class<?> type, String name, Object... args) { return invoke(type, null, name, args); }
    private static Object invoke(Class<?> type, Object object, String name, Object[] args) {
        Method best = null;
        int bestScore = -1;
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
            for (Method method : cursor.getDeclaredMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
                int score = 0;
                Class<?>[] params = method.getParameterTypes();
                for (int i = 0; i < args.length; i++) {
                    Class<?> boxed = box(params[i]);
                    if (args[i] == null) { if (params[i].isPrimitive()) { score = -1; break; } }
                    else if (boxed.equals(args[i].getClass())) score += 2;
                    else if (boxed.isInstance(args[i])) score++;
                    else { score = -1; break; }
                }
                if (score > bestScore) { bestScore = score; best = method; }
            }
        }
        if (best == null) throw new IllegalArgumentException(type.getName() + "." + name);
        try { best.setAccessible(true); return best.invoke(object, args); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    private static Class<?> box(Class<?> type) {
        if (type == boolean.class) return Boolean.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == char.class) return Character.class;
        return type;
    }
}
