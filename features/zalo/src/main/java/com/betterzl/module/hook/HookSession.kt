package com.betterzl.module.hook

import dev.ohmy.core.compat.XC_MethodHook
import dev.ohmy.core.compat.XposedBridge
import java.lang.reflect.Method

/** The small callback vocabulary used by the imported hooks, backed by LibXposed. */
internal class HookSession(private val loader: ClassLoader) {
    fun String.toClassOrNull(): Class<*>? = runCatching { Class.forName(this, false, loader) }.getOrNull()
    fun String.toClass(): Class<*> = Class.forName(this, false, loader)
    class MethodQuery { var name: String = "" }
    fun Class<*>.method(query: MethodQuery.() -> Unit): Method {
        val name = MethodQuery().apply(query).name
        return declaredMethods.first { it.name == name }.apply { isAccessible = true }
    }
    fun Method.hook(block: Callbacks.() -> Unit) {
        val callbacks = Callbacks().apply(block)
        XposedBridge.hookMethod(this, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) { callbacks.before?.invoke(Call(param)) }
            override fun afterHookedMethod(param: MethodHookParam) { callbacks.after?.invoke(Call(param)) }
        })
    }
    class Callbacks {
        internal var before: (Call.() -> Unit)? = null
        internal var after: (Call.() -> Unit)? = null
        fun before(block: Call.() -> Unit) { before = block }
        fun after(block: Call.() -> Unit) { after = block }
    }
    class Call(private val param: XC_MethodHook.MethodHookParam) {
        val args: Array<Any?> get() = param.args
        val instance: Any get() = param.thisObject
        var result: Any?
            get() = param.result
            set(value) { param.result = value }
        fun resultFalse() { result = false }
        fun resultNull() { result = null }
    }
}

internal object YLog {
    fun info(msg: String) = XposedBridge.log(msg)
    fun error(msg: String, e: Throwable? = null) = XposedBridge.log("$msg ${e ?: ""}")
}
