package com.betterzl.module.hook

import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import dev.ohmy.core.compat.XC_MethodHook
import dev.ohmy.core.compat.XposedBridge
import dev.ohmy.core.compat.XposedHelpers

/** Named host classes + resource IDs: no obfuscated fields/methods or screen-wide hooks. */
object FloatingBarHooks {
    private const val STATE = "betterzl.liquidGlass.controller"
    private var installed = false

    fun install(loader: ClassLoader) {
        if (installed || Build.VERSION.SDK_INT < 33) return
        runCatching {
            val rootClass = loader.loadClass("com.zing.zalo.uicontrol.MainTabFrameLayout")
            val tabClass = loader.loadClass("com.zing.zalo.ui.maintab.widget.CustomMainTab\$TabMainView")
            XposedBridge.hookAllMethods(rootClass, "onFinishInflate", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) = safely {
                    val root = param.thisObject as ViewGroup
                    if (state(root) == null) {
                        LiquidBarController.create(root)?.let { controller ->
                            XposedHelpers.setAdditionalInstanceField(root, STATE, controller)
                        }
                    }
                }
            })
            XposedBridge.hookAllMethods(rootClass, "onMeasure", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) = safely {
                    state(param.thisObject)?.prepareMeasure(View.MeasureSpec.getSize(param.args[0] as Int))
                }
                override fun afterHookedMethod(param: MethodHookParam) = safely {
                    state(param.thisObject)?.measure()
                }
            })
            XposedBridge.hookAllMethods(rootClass, "onLayout", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) = safely {
                    state(param.thisObject)?.layout()
                }
            })
            // Public descriptive method retained in host, verified against installed APK.
            XposedBridge.hookAllMethods(tabClass, "setProgess", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) = safely {
                    val tab = param.thisObject as View
                    controllerFor(tab)?.onTabProgress(tab, param.args[0] as Float)
                }
            })
            // Observe native touch dispatch. Taps still reach the original five native listeners;
            // horizontal pill drags cancel the original tap and invoke the original chosen item.
            XposedHelpers.findAndHookMethod(ViewGroup::class.java, "dispatchTouchEvent",
                MotionEvent::class.java, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) = safely {
                        val view = param.thisObject as ViewGroup
                        if (view.javaClass.name != "com.zing.zalo.ui.maintab.widget.CustomMainTab") return@safely
                        val controller = controllerFor(view) ?: return@safely
                        val event = param.args[0] as MotionEvent
                        if (controller.touch(event) { cancel ->
                                XposedBridge.invokeOriginalMethod(param.method, view, arrayOf(cancel))
                            }) param.result = true
                    }
                })
            installed = true
            XposedBridge.log("[BetterZaloGlass] Hooks installed; API=${Build.VERSION.SDK_INT}")
        }.onFailure { XposedBridge.log("[BetterZaloGlass] Install failed: $it") }
    }

    private fun state(root: Any): LiquidBarController? =
        XposedHelpers.getAdditionalInstanceField(root, STATE) as? LiquidBarController

    private fun controllerFor(view: View): LiquidBarController? {
        var cursor: View? = view
        while (cursor != null) {
            state(cursor)?.let { return it }
            cursor = cursor.parent as? View
        }
        return null
    }

    private inline fun safely(block: () -> Unit) {
        try { block() } catch (error: Throwable) {
            XposedBridge.log("[BetterZaloGlass] Host hook isolated: $error")
        }
    }
}
