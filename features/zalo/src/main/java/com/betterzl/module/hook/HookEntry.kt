package com.betterzl.module.hook

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import com.betterzl.module.settings.ZaloOptions

object HookEntry {

    private const val TAG = "BetterZalo"
    private const val REQUEST_CODE_PICK_RINGTONE = 0x9922

    fun install(loader: ClassLoader, options: ZaloOptions) {
        with(HookSession(loader)) {
            YLog.info(msg = "[$TAG] Initializing BetterZalo hooks")
            if (options.antiRecall()) hookAntiRecallAdaptive()
            if (options.customRingtone()) hookCustomRingtoneAdaptive()
            hookStealthSeenAndTypingAdaptive(options.hideSeen(), options.hideTyping())
            if (options.liquidGlass()) FloatingBarHooks.install(loader)
        }
    }

    /**
     * TÍNH NĂNG 1: Chống thu hồi tin nhắn (Anti-Recall)
     * Hook vào 3 lớp phòng vệ:
     * 1. P4() / isRecalled(): Luôn trả về FALSE nếu tin nhắn là của đối phương -> Không bao giờ chuyển bubble thành "Đã thu hồi".
     * 2. Database update: Chặn phương thức g0 / updateMessageUndo trong v40.d / p60.e.
     * 3. UI text: Hiển thị tiền tố [Đã thu hồi] khi tin nhắn có cờ thu hồi.
     */
    private fun HookSession.hookAntiRecallAdaptive() {
        runCatching {
            // 1. Hook trực tiếp vào phương thức P4() của Class tin nhắn (p00.s hoặc j20.q)
            val msgClasses = listOf("p00.s", "j20.q")
            for (clsName in msgClasses) {
                val msgCls = clsName.toClassOrNull() ?: continue
                for (m in msgCls.declaredMethods) {
                    if (m.name == "P4" && m.parameterTypes.isEmpty() && m.returnType == java.lang.Boolean.TYPE) {
                        m.isAccessible = true
                        m.hook {
                            before {
                                val isSelf = checkIsSelfMessage(instance)
                                // Nếu là tin nhắn của người khác gửi thu hồi -> Ép P4() trả về false!
                                if (!isSelf) {
                                    resultFalse()
                                }
                            }
                        }
                        YLog.info(msg = "[$TAG] Hooked Message.P4() anti-recall in ${msgCls.name}")
                    }
                }
            }

            // 2. Chặn việc cập nhật type=36 xuống database trong DatabaseChat (v40.d / p60.e)
            val dbClassCandidates = listOf("v40.d", "p60.e")
            for (clsName in dbClassCandidates) {
                val dbCls = clsName.toClassOrNull() ?: continue
                for (m in dbCls.declaredMethods) {
                    if (m.parameterTypes.size == 3 &&
                        m.parameterTypes[1] == java.lang.Boolean.TYPE &&
                        !Modifier.isStatic(m.modifiers) &&
                        m.returnType == java.lang.Void.TYPE
                    ) {
                        m.isAccessible = true
                        m.hook {
                            before {
                                val message = args[0] ?: return@before
                                val isSelf = checkIsSelfMessage(message)
                                if (!isSelf) {
                                    YLog.info(msg = "[$TAG] Blocked database update of recalled message in ${dbCls.name}.${m.name}!")
                                    tagRecalledMessage(message)
                                    resultNull()
                                }
                            }
                        }
                        YLog.info(msg = "[$TAG] Hooked DB recall update method: ${dbCls.name}.${m.name}")
                    }
                }
            }

            // 3. Hook tầng hiển thị text của ChatRowUndo phòng khi tin nhắn đã lưu cờ 36 từ trước
            val chatRowUndoCls = "com.zing.zalo.ui.chat.chatrow.ChatRowUndo".toClassOrNull()
            if (chatRowUndoCls != null) {
                for (m in chatRowUndoCls.declaredMethods) {
                    if (m.parameterTypes.size == 1 &&
                        m.returnType == java.lang.String::class.java &&
                        !Modifier.isStatic(m.modifiers)
                    ) {
                        m.isAccessible = true
                        m.hook {
                            after {
                                val message = args[0] ?: return@after
                                val originalText = extractMessageText(message)
                                if (!originalText.isNullOrEmpty() && !originalText.contains("thu hồi")) {
                                    result = "[Đã thu hồi] $originalText"
                                }
                            }
                        }
                        YLog.info(msg = "[$TAG] Hooked ChatRowUndo text display method: ${m.name}")
                    }
                }
            }

            YLog.info(msg = "[$TAG] Anti-recall adaptive hooks installed.")
        }.onFailure {
            YLog.error(msg = "[$TAG] Failed to install anti-recall adaptive hooks", e = it)
        }
    }

    /**
     * TÍNH NĂNG 2: Import file MP3 làm nhạc chuông cuộc gọi đến
     * Triển khai chuẩn native của Zalo:
     * - Lưu file MP3 vào bộ nhớ Zalo: files/custom_ringtone.mp3
     * - Đặt cấu hình Zalo:
     *     TYPE_RINGTONE = 2 (Hệ thống / Custom URI)
     *     URI_SELECTED_SYSTEM_RINGTONE = file:///data/user/0/com.zing.zalo/files/custom_ringtone.mp3
     * - Khi có cuộc gọi đến, Zalo tự động đọc URI này và phát trực tiếp file MP3 của bạn!
     */
    private fun HookSession.hookCustomRingtoneAdaptive() {
        runCatching {
            // 1. Hook nút bấm trong màn hình Cài đặt nhạc chuông
            val ringtoneViewCls = "com.zing.zalo.ui.call.settingringtone.presenter.settingringtone.SettingRingtoneViewV2".toClassOrNull()
            if (ringtoneViewCls != null) {
                val initMethod = ringtoneViewCls.declaredMethods.firstOrNull { it.name == "r5" && !Modifier.isStatic(it.modifiers) }
                    ?: ringtoneViewCls.declaredMethods.firstOrNull { it.name == "F5" && !Modifier.isStatic(it.modifiers) }

                if (initMethod != null) {
                    initMethod.isAccessible = true
                    initMethod.hook {
                        after {
                            val hostView = (if (args.isNotEmpty()) args[0] as? View else null)
                                ?: (instance.javaClass.methods.firstOrNull { it.name == "getView" }?.invoke(instance) as? View)
                                ?: return@after

                            val activity = findHostActivity(instance) ?: hostView.context as? Activity ?: return@after

                            if (hostView is ViewGroup) {
                                if (hostView.findViewWithTag<View>("betterzalo_mp3_btn") != null) return@after

                                val targetContainer = hostView.findViewById<ViewGroup>(
                                    hostView.resources.getIdentifier("scroll_main_layout", "id", "com.zing.zalo")
                                )?.getChildAt(0) as? ViewGroup ?: hostView

                                if (targetContainer.findViewWithTag<View>("betterzalo_mp3_btn") != null) return@after

                                val ctx = hostView.context
                                val customBtn = Button(ctx).apply {
                                    tag = "betterzalo_mp3_btn"
                                    text = "🎵 Chọn nhạc chuông MP3 từ máy (BetterZalo)"
                                    setTextColor(Color.WHITE)
                                    setBackgroundColor(Color.parseColor("#0068FF"))
                                    setPadding(30, 20, 30, 20)
                                    setOnClickListener {
                                        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                                            type = "audio/*"
                                            addCategory(Intent.CATEGORY_OPENABLE)
                                        }
                                        // Sử dụng trực tiếp activity để nhận onActivityResult chuẩn
                                        activity.startActivityForResult(intent, REQUEST_CODE_PICK_RINGTONE)
                                    }
                                }

                                val layoutParams = LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT
                                ).apply {
                                    setMargins(40, 25, 40, 20)
                                }

                                targetContainer.post {
                                    if (targetContainer.findViewWithTag<View>("betterzalo_mp3_btn") == null) {
                                        targetContainer.addView(customBtn, 0, layoutParams)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. Hook onActivityResult trên TẤT CẢ Activity của Zalo để đảm bảo 100% bắt được kết quả chọn file MP3
            "android.app.Activity".toClass().method {
                name = "onActivityResult"
            }.hook {
                before {
                    val requestCode = args[0] as? Int ?: 0
                    val resultCode = args[1] as? Int ?: 0
                    val intent = args[2] as? Intent

                    if (requestCode == REQUEST_CODE_PICK_RINGTONE && resultCode == Activity.RESULT_OK) {
                        val uri: Uri? = intent?.data
                        val activity = instance as? Activity
                        if (uri != null && activity != null) {
                            runCatching {
                                val targetFile = File(activity.filesDir, "custom_ringtone.mp3")
                                activity.contentResolver.openInputStream(uri)?.use { inStream ->
                                    FileOutputStream(targetFile).use { outStream ->
                                        inStream.copyTo(outStream)
                                    }
                                }
                                val ringtoneUri = Uri.fromFile(targetFile).toString()

                                // Cập nhật trực tiếp vào cơ chế Ringtone chuẩn của Zalo (ra.f hoặc cc.g)
                                val prefClasses = listOf("ra.f", "cc.g")
                                for (prefClsName in prefClasses) {
                                    val prefCls = prefClsName.toClassOrNull() ?: continue
                                    // Set URI_SELECTED_SYSTEM_RINGTONE
                                    for (m in prefCls.declaredMethods) {
                                        if (m.parameterTypes.size == 1 &&
                                            m.parameterTypes[0] == java.lang.String::class.java &&
                                            Modifier.isStatic(m.modifiers)
                                        ) {
                                            if (m.name == "H3" || m.name == "L3" || m.name == "h4") {
                                                m.isAccessible = true
                                                m.invoke(null, ringtoneUri)
                                            }
                                        }
                                        // Set TYPE_RINGTONE = 2
                                        if (m.parameterTypes.size == 1 &&
                                            m.parameterTypes[0] == java.lang.Integer.TYPE &&
                                            Modifier.isStatic(m.modifiers)
                                        ) {
                                            if (m.name == "E3" || m.name == "I3") {
                                                m.isAccessible = true
                                                m.invoke(null, 2)
                                            }
                                        }
                                    }
                                }

                                Toast.makeText(activity, "Đã cài đặt nhạc chuông MP3 thành công!", Toast.LENGTH_LONG).show()
                                YLog.info(msg = "[$TAG] Successfully set custom ringtone: $ringtoneUri")
                            }.onFailure { err ->
                                Toast.makeText(activity, "Lỗi cài đặt nhạc chuông: ${err.message}", Toast.LENGTH_SHORT).show()
                                YLog.error(msg = "[$TAG] Failed to save custom ringtone", e = err)
                            }
                        }
                    }
                }
            }

            // 3. Hook dd1.h.d() - Quản lý LOẠI nhạc chuông hiện tại của Zalo
            // Ép trả về 2 (Nhạc chuông hệ thống / Custom URI) để UI ở ngoài (Cài đặt -> Cuộc gọi)
            // và radio trong màn hình Nhạc chuông luôn hiển thị đúng trạng thái đã chọn file MP3!
            val ringtoneHelperClasses = listOf("dd1.h", "jg1.h")
            for (clsName in ringtoneHelperClasses) {
                val cls = clsName.toClassOrNull() ?: continue
                for (m in cls.declaredMethods) {
                    if (Modifier.isStatic(m.modifiers) &&
                        m.name == "d" &&
                        m.returnType == java.lang.Integer.TYPE
                    ) {
                        m.isAccessible = true
                        m.hook {
                            after {
                                val targetFile = listOf(
                                    File("/data/user/0/com.zing.zalo/files/custom_ringtone.mp3"),
                                    File("/data/data/com.zing.zalo/files/custom_ringtone.mp3")
                                ).firstOrNull { it.exists() && it.length() > 0 }

                                if (targetFile != null) {
                                    result = 2 // Type 2 = Nhạc chuông hệ thống / Custom MP3
                                }
                            }
                        }
                        YLog.info(msg = "[$TAG] Hooked ringtone type provider: ${cls.name}.${m.name}() -> 2")
                    }
                }
            }

            // 4. Hook đè phương thức trả về URI nhạc chuông hệ thống (ra.f.P() hoặc cc.g.Y())
            // Khi Zalo chuẩn bị đổ chuông, nếu file custom tồn tại -> Trả về thẳng URI file MP3!
            val ringtoneGetterClasses = listOf("ra.f", "cc.g")
            for (clsName in ringtoneGetterClasses) {
                val cls = clsName.toClassOrNull() ?: continue
                for (m in cls.declaredMethods) {
                    if (Modifier.isStatic(m.modifiers) &&
                        m.parameterTypes.isEmpty() &&
                        m.returnType == java.lang.String::class.java &&
                        (m.name == "P" || m.name == "Y" || m.name == "O")
                    ) {
                        m.isAccessible = true
                        m.hook {
                            after {
                                val targetFile = listOf(
                                    File("/data/user/0/com.zing.zalo/files/custom_ringtone.mp3"),
                                    File("/data/data/com.zing.zalo/files/custom_ringtone.mp3")
                                ).firstOrNull { it.exists() && it.length() > 0 }

                                if (targetFile != null) {
                                    result = Uri.fromFile(targetFile).toString()
                                }
                            }
                        }
                        YLog.info(msg = "[$TAG] Hooked ringtone URI getter: ${cls.name}.${m.name}()")
                    }
                }
            }

            YLog.info(msg = "[$TAG] Custom ringtone adaptive hooks installed.")
        }.onFailure {
            YLog.error(msg = "[$TAG] Failed to install custom ringtone adaptive hooks", e = it)
        }
    }

    /**
     * TÍNH NĂNG 3: Ẩn trạng thái "Đã xem" & Ẩn "Đang soạn tin nhắn..."
     */
    private fun HookSession.hookStealthSeenAndTypingAdaptive(hideSeen: Boolean, hideTyping: Boolean) {
        runCatching {
            if (hideSeen) {
                // 1. ẨN ĐÃ XEM (Seen message status):
                // Chặn TẤT CẢ các ngả ghi nhận & gửi trạng thái Seen trong SendSeenManager (le0.m0 / tg0.h0)
                val seenManagerCandidates = listOf("le0.m0", "tg0.h0")
                for (clsName in seenManagerCandidates) {
                    val seenCls = clsName.toClassOrNull() ?: continue
                    for (m in seenCls.declaredMethods) {
                        // Chặn các hàm thêm tin nhắn vào hàng đợi Seen và hàm gửi Seen lên server:
                        // d(bVar), e(list), g(), i(list, boolean), b(long, String)
                        if (m.name in listOf("d", "e", "g", "i", "b")) {
                            m.isAccessible = true
                            m.hook {
                                before {
                                    YLog.info(msg = "[$TAG] Blocked Seen method: ${seenCls.name}.${m.name}()")
                                    resultNull()
                                }
                            }
                            YLog.info(msg = "[$TAG] Hooked Seen manager: ${seenCls.name}.${m.name}")
                        }
                    }
                }

                // Chặn thêm ở tầng Network Request Packet (t00.a0 / n20.x):
                // Phương thức e(List list, boolean z3, int i, int i3, t tVar)
                // Nếu z3 == true (seen = 1) -> Chặn hoàn toàn!
                val netApiCandidates = listOf("t00.a0", "n20.x")
                for (clsName in netApiCandidates) {
                    val apiCls = clsName.toClassOrNull() ?: continue
                    for (m in apiCls.declaredMethods) {
                        if (m.name == "e" && m.parameterTypes.size >= 2 && m.parameterTypes[1] == java.lang.Boolean.TYPE) {
                            m.isAccessible = true
                            m.hook {
                                before {
                                    val isSeen = args[1] as? Boolean ?: false
                                    if (isSeen) {
                                        YLog.info(msg = "[$TAG] Blocked raw seen packet in ${apiCls.name}.${m.name}()")
                                        resultNull()
                                    }
                                }
                            }
                            YLog.info(msg = "[$TAG] Hooked network seen sender: ${apiCls.name}.${m.name}")
                        }
                    }
                }

            }
            // 2. ẨN ĐANG SOẠN TIN NHẮN (Typing indicator):
            if (hideTyping) {
                val repoCandidates = listOf("f20.s", "m00.q")
                for (clsName in repoCandidates) {
                    val repoCls = clsName.toClassOrNull() ?: continue
                    for (m in repoCls.declaredMethods) {
                        if ((m.name == "S" || m.name == "s") && m.parameterTypes.size == 4 &&
                            m.parameterTypes[0] == java.lang.String::class.java &&
                            m.parameterTypes[1] == java.lang.Integer.TYPE
                        ) {
                            m.isAccessible = true
                            m.hook {
                                before {
                                    YLog.info(msg = "[$TAG] Blocked outgoing typing status in ${repoCls.name}.${m.name}()")
                                    resultNull()
                                }
                            }
                            YLog.info(msg = "[$TAG] Hooked typing status method: ${repoCls.name}.${m.name}")
                        }
                    }
                }

            }
            YLog.info(msg = "[$TAG] Privacy hooks: hideSeen=$hideSeen hideTyping=$hideTyping")
        }.onFailure {
            YLog.error(msg = "[$TAG] Failed to install stealth seen & typing hooks", e = it)
        }
    }

    // Helper functions
    private fun checkIsSelfMessage(message: Any): Boolean {
        return runCatching {
            // Kiểm tra method P3() trước
            val p3Method = message.javaClass.declaredMethods.firstOrNull { it.name == "P3" && it.parameterTypes.isEmpty() }
            if (p3Method != null) {
                p3Method.isAccessible = true
                return p3Method.invoke(message) as Boolean
            }
            val rField = message.javaClass.declaredFields.firstOrNull { it.name == "r" && it.type == java.lang.Boolean.TYPE }
            if (rField != null) {
                rField.isAccessible = true
                rField.getBoolean(message)
            } else false
        }.getOrDefault(false)
    }

    private fun extractMessageText(message: Any): String? {
        return runCatching {
            val fField = message.javaClass.declaredFields.firstOrNull {
                it.type == java.lang.String::class.java && (it.name == "f" || it.name == "f78398f" || it.name == "d")
            }
            if (fField != null) {
                fField.isAccessible = true
                fField.get(message) as? String
            } else null
        }.getOrNull()
    }

    private fun tagRecalledMessage(message: Any) {
        runCatching {
            val fField = message.javaClass.declaredFields.firstOrNull {
                it.type == java.lang.String::class.java && (it.name == "f" || it.name == "f78398f" || it.name == "d")
            }
            if (fField != null) {
                fField.isAccessible = true
                val originalMsg = fField.get(message) as? String ?: ""
                if (!originalMsg.startsWith("[Đã thu hồi] ")) {
                    fField.set(message, "[Đã thu hồi] $originalMsg")
                }
            }
        }
    }

    private fun findHostActivity(instance: Any): Activity? {
        return runCatching {
            val getAct = instance.javaClass.methods.firstOrNull { it.name == "getActivity" && it.parameterTypes.isEmpty() }
            if (getAct != null) {
                getAct.invoke(instance) as? Activity
            } else {
                val ctxField = instance.javaClass.declaredFields.firstOrNull { Context::class.java.isAssignableFrom(it.type) }
                ctxField?.isAccessible = true
                ctxField?.get(instance) as? Activity
            }
        }.getOrNull()
    }
}
