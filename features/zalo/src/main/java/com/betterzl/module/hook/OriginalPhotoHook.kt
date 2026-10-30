package com.betterzl.module.hook

import android.os.Build
import android.os.Process
import android.util.Log
import io.github.libxposed.api.XposedModule
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher
import java.io.File
import java.lang.reflect.Modifier

/** Unlock only the photo picker's local eligibility, never global zCloud entitlement. */
object OriginalPhotoHook {
    private const val TAG = "BetterZalo.OriginalPhoto"

    fun install(module: XposedModule, loader: ClassLoader, hostApk: String) {
        try {
            loadDexKit(module)
            DexKitBridge.create(hostApk).use { bridge ->
                val history = bridge.findMethod(FindMethod.create().matcher(
                    MethodMatcher.create().usingStrings("LAST_SELECTION_MEDIA_QUALITY_").returnType("int").paramCount(0)
                ))
                val owners = history.map { it.declaredClassName }.distinct().filter { name ->
                    val methods = Class.forName(name, false, loader).declaredMethods.filter { Modifier.isStatic(it.modifiers) }
                    methods.count { it.returnType == Boolean::class.javaPrimitiveType && it.parameterCount == 0 } == 3 &&
                        methods.count { it.returnType == Int::class.javaPrimitiveType && it.parameterCount == 0 } >= 2 &&
                        methods.any { it.returnType == Int::class.javaPrimitiveType && it.parameterTypes.contentEquals(arrayOf(String::class.java)) }
                }
                require(owners.size == 1) { "Photo quality helper not unique: ${owners.size}" }
                val owner = owners.single()
                val booleans = bridge.findMethod(FindMethod.create().matcher(
                    MethodMatcher.create().declaredClass(owner).returnType("boolean").paramCount(0)
                ))
                // canSendOriginal = featureEnabled() && accountAllowed(). Both callees
                // are booleans in this helper; the global subscription method is elsewhere.
                val gates = booleans.filter { method ->
                    method.invokes.count { it.declaredClassName == owner && it.paramCount == 0 && it.returnTypeName == "boolean" } == 2
                }
                require(gates.size == 1) { "Original photo eligibility not unique: ${gates.size}" }
                val gate = gates.single()
                val flags = gate.invokes.filter { it.declaredClassName == owner && it.returnTypeName == "boolean" && it.paramCount == 0 }
                    .filter { method -> method.invokes.any { it.methodName == "getValue" && it.paramCount == 0 } }
                require(flags.size == 1) { "Photo feature flag not unique: ${flags.size}" }
                val eligibility = gate.getMethodInstance(loader)
                val featureFlag = flags.single().getMethodInstance(loader)
                require(Modifier.isStatic(eligibility.modifiers) && Modifier.isStatic(featureFlag.modifiers))
                module.hook(featureFlag).intercept { true }
                module.hook(eligibility).intercept { true }
                module.log(Log.INFO, TAG, "FOUND eligibility=$eligibility featureFlag=$featureFlag; original native send pipeline retained")
            }
        } catch (error: Throwable) {
            module.log(Log.ERROR, TAG, "Original photo hook unavailable; native behavior retained", error)
        }
    }

    private fun loadDexKit(module: XposedModule) {
        try { System.loadLibrary("dexkit"); return } catch (_: UnsatisfiedLinkError) { }
        val app = module.moduleApplicationInfo
        val library = File(app.nativeLibraryDir, "libdexkit.so")
        if (library.isFile) { System.load(library.path); return }
        val abi = Build.SUPPORTED_ABIS.first { it.contains("64") == Process.is64Bit() }
        System.load("${app.sourceDir}!/lib/$abi/libdexkit.so")
    }
}
