package tn.loukious.facebookappadsremover.ui

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Toast
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import dev.ohmy.core.ui.ModuleSwitch
import android.graphics.Color
import android.content.res.ColorStateList
import com.google.android.material.button.MaterialButton
import tn.loukious.facebookappadsremover.R
import tn.loukious.facebookappadsremover.core.SessionBackup
import tn.loukious.facebookappadsremover.core.Settings
import tn.loukious.facebookappadsremover.core.AdSettingsMigration
import io.github.libxposed.service.XposedService
import dev.ohmy.core.ModuleApplication

/**
 * Settings screen — the toggle surface for every feature the module ports.
 *
 * CHANNEL: toggles live in the framework's remote-preferences storage
 * (Vector/LSPosed daemon), reached from the module app through the libxposed
 * SERVICE library — XposedServiceHelper.registerListener delivers the
 * framework binder to our manifest-merged XposedProvider, and
 * service.getRemotePreferences(NAME) returns a WRITABLE SharedPreferences
 * whose edits go over binder. The hooks inside the Facebook process read the
 * same group read-only via XposedInterface.getRemotePreferences. Verified the
 * hard way (2026-09-06): plain SharedPreferences files in the module's own
 * storage — credential-encrypted OR device-protected — are never read by the
 * daemon; writes must go through the service.
 *
 * Until the service binds, switches show disabled with defaults. The daemon
 * pushes change notifications to hooked processes, so toggles may apply
 * without an FB restart (documented as restart-required regardless).
 *
 * The in-FB floating panel the original mod had is a later milestone (M6)
 * and will read the same keys.
 */
class MainActivity : AppCompatActivity(), ModuleApplication.ServiceObserver {

    private var prefs: SharedPreferences? = null
    private var serviceBound = false
    private var resyncing = false
    private lateinit var serviceHint: TextView
    private lateinit var togglesList: LinearLayout

    /**
     * System file browser for importing a specific session export. The FB
     * process cannot open another app's content URIs without a grant, so the
     * picked JSON is read here (~15 KB) and shipped inside the broadcast.
     */
    private val pickSessionFile = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri -> onSessionFilePicked(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The manager uses a fixed white palette, independent of Facebook.
        super.onCreate(savedInstanceState)
        setContentView(R.layout.facebook_activity_main)
        findViewById<View>(android.R.id.content).setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
        findViewById<View>(R.id.facebook_back_to_modules).setOnClickListener { finish() }

        serviceHint = TextView(this)
        serviceHint.text = getString(R.string.facebook_service_waiting)
        serviceHint.textSize = 13f
        serviceHint.setTextColor(MUTED)
        serviceHint.setPadding(0, dp(8), 0, dp(4))

        togglesList = findViewById(R.id.facebook_toggles)
        togglesList.addView(serviceHint)
        for (section in TOGGLE_SECTIONS) {
            addSectionHeader(togglesList, section.title)
            for (toggle in section.toggles) {
                addToggleRow(togglesList, toggle)
            }
            // Non-toggle controls that live inside their feature's section.
            when (section.extra) {
                SectionExtra.KEYWORDS -> addKeywordInput(togglesList)
                SectionExtra.SESSION -> addSessionButtons(togglesList)
                SectionExtra.LAUNCHER -> addLauncherIconRow(togglesList)
                SectionExtra.NONE -> {}
            }
        }

    }

    override fun onStart() {
        super.onStart()
        ModuleApplication.addObserver(this)
    }

    override fun onStop() {
        ModuleApplication.removeObserver(this)
        super.onStop()
    }

    override fun onXposedServiceChanged(service: XposedService?) {
        runOnUiThread {
            prefs = service?.let { runCatching { it.getRemotePreferences(Settings.NAME) }.getOrNull() }
            if (prefs == null) {
                onServiceGone()
            } else {
                serviceBound = false
                migrateFromLocalStorage()
                migrateSplitAdToggles()
                onServiceReady()
            }
        }
    }

    private fun onServiceReady() {
        if (serviceBound) return
        serviceBound = true
        serviceHint.text = getString(R.string.facebook_service_ready)
        // Re-sync every switch with the real stored state, then unlock them.
        resyncing = true
        for (spec in TOGGLE_SECTIONS.flatMap { it.toggles }) {
            val switch = rows[spec.key] ?: continue
            switch.isChecked = prefs?.getBoolean(spec.key, spec.default) ?: spec.default
            switch.isEnabled = true
        }
        resyncing = false
        keywordInput?.let { input ->
            input.setText(prefs?.getString(Settings.FEED_KEYWORDS, "") ?: "")
            input.isEnabled = true
        }
    }

    private fun onServiceGone() {
        serviceBound = false
        serviceHint.text = getString(R.string.facebook_service_waiting)
        for (switch in rows.values) switch.isEnabled = false
        keywordInput?.isEnabled = false
    }

    /**
     * One-time carry-over from the plain-SharedPreferences writes of the first
     * settings build (which the framework never saw). Runs once per bind but
     * only pushes when the remote storage is still empty.
     */
    private fun migrateFromLocalStorage() {
        val remote = prefs ?: return
        runCatching {
            if (remote.all.isNotEmpty()) return
            val legacy = getSharedPreferences(Settings.NAME, MODE_PRIVATE)
            val old = legacy.all
            if (old.isEmpty()) return
            remote.edit().apply {
                for ((key, value) in old) {
                    when (value) {
                        is Boolean -> putBoolean(key, value)
                        is String -> putString(key, value)
                        is Int -> putInt(key, value)
                        is Long -> putLong(key, value)
                        is Float -> putFloat(key, value)
                        is Set<*> -> @Suppress("UNCHECKED_CAST") (value as? Set<String>)?.let { putStringSet(key, it) }
                    }
                }
            }.apply()
        }
    }

    /**
     * The previous Block ads setting was a master gate over feed, video,
     * stories, Marketplace and games. Convert it once into independent surface
     * preferences so an existing disabled master stays disabled after upgrade.
     * A user can then enable any individual ad family without the old master
     * overriding their choice. The retired Reels shopping setting is removed.
     */
    private fun migrateSplitAdToggles() {
        val remote = prefs ?: return
        runCatching {
            val updates = AdSettingsMigration.updates(remote.all)
            if (updates.isEmpty() && !remote.contains(AdSettingsMigration.RETIRED_SHOPPING)) {
                return
            }
            remote.edit().apply {
                for ((key, value) in updates) putBoolean(key, value)
                remove(AdSettingsMigration.RETIRED_SHOPPING)
                remove(Settings.LEGACY_ADS_ENABLED)
            }.apply()
        }.onFailure {
            android.util.Log.w("FBAR.Settings", "Ad toggle migration failed", it)
        }
    }

    private val rows = HashMap<String, ModuleSwitch>()
    private var keywordInput: android.widget.EditText? = null

    /**
     * The keyword list for the feed filter (Settings.FEED_KEYWORDS). Saved on
     * every text change; loaded once the service binds (same lifecycle as the
     * switches).
     */
    private fun addKeywordInput(parent: ViewGroup) {
        // Material text field: boxed outline with a floating hint, so the
        // placeholder never overlaps the typed text (plain EditText inside
        // this theme drew both on top of each other).
        val layout = com.google.android.material.textfield.TextInputLayout(
            this, null,
            com.google.android.material.R.style.Widget_MaterialComponents_TextInputLayout_OutlinedBox,
        ).apply {
            hint = "Từ khóa, phân cách bằng dấu phẩy"
            boxBackgroundMode = com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE
            boxStrokeColor = BLUE
            defaultHintTextColor = ColorStateList.valueOf(MUTED)
            setBoxCornerRadii(dp(12).toFloat(), dp(12).toFloat(), dp(12).toFloat(), dp(12).toFloat())
            setPadding(0, dp(12), 0, dp(4))
        }

        val input = com.google.android.material.textfield.TextInputEditText(layout.context)
        input.background = null
        input.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15f)
        input.setTextColor(INK)
        input.setSingleLine(false)
        input.minLines = 1
        input.maxLines = 3
        input.isEnabled = false // until the service binds
        // Save on every change: focus-loss saving proved fragile (an EditText
        // keeps focus when a non-focusable view is tapped, and a force-stop
        // skips onPause entirely), which silently dropped typed keywords.
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                saveKeywords(s?.toString() ?: "")
            }
        })
        keywordInput = input
        layout.addView(input)
        parent.addView(layout)
    }

    private fun saveKeywords(value: String) {
        prefs?.edit()?.putString(Settings.FEED_KEYWORDS, value)?.apply()
    }

    /**
     * Session export/import — broadcasts into the Facebook process
     * (core.SessionBackup answers; it holds the session files and the
     * captured cookies).
     */
    private fun addSessionButtons(parent: ViewGroup) {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(0, dp(12), 0, dp(4))

        val export = sessionButton("Xuất phiên")
        export.setOnClickListener { sendSession(SessionBackup.ACTION_EXPORT) }
        row.addView(export, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val spacer = View(this)
        row.addView(spacer, LinearLayout.LayoutParams(dp(8), ViewGroup.LayoutParams.WRAP_CONTENT))

        val import = sessionButton("Nhập bản mới nhất")
        import.setOnClickListener { sendSession(SessionBackup.ACTION_IMPORT) }
        row.addView(import, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        parent.addView(row)

        val fromFile = sessionButton("Nhập từ tệp…")
        fromFile.setOnClickListener { pickSessionFile.launch(arrayOf("application/json", "text/*", "*/*")) }
        parent.addView(fromFile)

        val caption = TextView(this)
        caption.text = "Bản xuất lưu tại Download/FacebookAppAdsRemover. Sau khi nhập phiên, khởi động lại Facebook."
        caption.textSize = 13f
        caption.setTextColor(MUTED)
        caption.setPadding(0, dp(8), 0, dp(8))
        parent.addView(caption)
    }

    private fun sendSession(action: String) {
        val intent = Intent(action).setPackage("com.facebook.katana")
        val sent = runCatching { sendBroadcast(intent) }.isSuccess
        Toast.makeText(
            this,
            if (sent) "Đã gửi yêu cầu tới Facebook" else "Không gửi được yêu cầu tới Facebook",
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun onSessionFilePicked(uri: android.net.Uri?) {
        if (uri == null) return // picker cancelled
        val text = runCatching {
            contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
        }.getOrNull()
        if (text.isNullOrBlank()) {
            Toast.makeText(this, "Không đọc được tệp đã chọn", Toast.LENGTH_LONG).show()
            return
        }
        // Quick client-side sanity check so obvious wrong files fail here
        // rather than as a silent FB-side toast.
        if (!text.trimStart().startsWith("{") || !text.contains("\"files\"")) {
            Toast.makeText(this, "Tệp này không phải bản xuất phiên đăng nhập", Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(SessionBackup.ACTION_IMPORT_DATA)
            .setPackage("com.facebook.katana")
            .putExtra(SessionBackup.EXTRA_DATA, text)
        runCatching { sendBroadcast(intent) }
            .onSuccess { Toast.makeText(this, "Đã gửi yêu cầu tới Facebook", Toast.LENGTH_SHORT).show() }
            .onFailure {
                Toast.makeText(this, "Không gửi được yêu cầu tới Facebook", Toast.LENGTH_SHORT).show()
            }
    }

    private fun addSectionHeader(parent: ViewGroup, title: String) {
        val tv = TextView(this)
        tv.text = title
        tv.textSize = 18f
        tv.setTextColor(INK)
        tv.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        tv.setPadding(0, dp(28), 0, dp(8))
        tv.isAccessibilityHeading = true
        parent.addView(tv)
    }

    private fun addToggleRow(parent: ViewGroup, spec: ToggleSpec) {
        val switch = buildToggleRow(parent, spec.title, spec.subtitle)
        switch.isChecked = spec.default
        switch.isEnabled = false // until the service binds
        switch.setOnCheckedChangeListener { _, checked ->
            if (resyncing) return@setOnCheckedChangeListener
            prefs?.edit()?.putBoolean(spec.key, checked)?.apply()
        }
        rows[spec.key] = switch
    }

    /**
     * The row layout every switch on this screen uses: title + subtitle on the
     * left, switch on the right, whole row as the click target (the switch
     * itself is a small target). Callers own the switch's initial state,
     * enabled state and listener.
     */
    private fun buildToggleRow(parent: ViewGroup, title: String, subtitle: String): ModuleSwitch {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, dp(12), 0, dp(12))
        row.minimumHeight = dp(72)
        val ripple = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
        row.setBackgroundResource(ripple.resourceId)

        val text = LinearLayout(this)
        text.orientation = LinearLayout.VERTICAL
        text.setPadding(0, 0, dp(12), 0)
        text.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

        val titleView = TextView(this)
        titleView.text = title
        titleView.textSize = 16f
        titleView.setTextColor(INK)
        text.addView(titleView)

        val subtitleView = TextView(this)
        subtitleView.text = subtitle
        subtitleView.maxLines = 2
        subtitleView.ellipsize = android.text.TextUtils.TruncateAt.END
        subtitleView.setOnLongClickListener {
            AlertDialog.Builder(this).setTitle(title).setMessage(subtitle)
                .setPositiveButton(android.R.string.ok, null).show()
            true
        }
        subtitleView.textSize = 13f
        subtitleView.setTextColor(MUTED)
        subtitleView.setPadding(0, dp(2), 0, 0)
        text.addView(subtitleView)
        row.addView(text)

        val switch = ModuleSwitch(this)
        switch.contentDescription = title
        row.addView(switch, LinearLayout.LayoutParams(dp(64), dp(48)))

        // Whole row toggles, not just the small switch target.
        row.setOnClickListener { if (switch.isEnabled) switch.toggle() }
        parent.addView(row)
        parent.addView(View(this).apply { setBackgroundColor(Color.rgb(239, 242, 246)) },
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))
        return switch
    }

    /**
     * Launcher-icon switch (ui.LauncherIcon). The odd one out on this screen:
     * its value lives in PackageManager's component state rather than the
     * framework's remote preferences, so it needs no service and is enabled
     * immediately instead of waiting for the bind.
     *
     * Hiding is confirmed first, because the icon is this app's only entry
     * point — there is no second screen to fall back to.
     */
    private fun addLauncherIconRow(parent: ViewGroup) {
        val switch = buildToggleRow(
            parent,
            "Hiện biểu tượng ứng dụng",
            "Hiện OhMyLSPosed trong danh sách ứng dụng",
        )
        switch.isChecked = LauncherIcon.isVisible(this)
        switch.isEnabled = true

        // Reverting from inside the listener re-enters it; this is the same
        // guard the prefs switches use via `resyncing`.
        var reverting = false
        fun revert() {
            reverting = true
            switch.isChecked = true
            reverting = false
        }

        switch.setOnCheckedChangeListener { _, checked ->
            if (reverting) return@setOnCheckedChangeListener
            if (checked) {
                LauncherIcon.setVisible(this, true)
                return@setOnCheckedChangeListener
            }
            AlertDialog.Builder(this)
                .setTitle("Ẩn biểu tượng ứng dụng?")
                .setMessage(
                    "Bạn có thể mở lại OhMyLSPosed từ danh sách module trong LSPosed."
                )
                // Both the button and a back/outside dismissal must put the
                // switch back, or it would read "hidden" while the icon is
                // still in the drawer.
                .setNegativeButton("Hủy") { _, _ -> revert() }
                .setOnCancelListener { revert() }
                .setPositiveButton("Ẩn") { _, _ -> LauncherIcon.setVisible(this, false) }
                .show()
        }
    }

    private fun sessionButton(label: String) = MaterialButton(
        this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
    ).apply {
        text = label
        isAllCaps = false
        textSize = 14f
        setTextColor(BLUE)
        strokeColor = ColorStateList.valueOf(Color.rgb(218, 225, 237))
        backgroundTintList = ColorStateList.valueOf(Color.WHITE)
        cornerRadius = dp(12)
        minimumHeight = dp(48)
        setPadding(dp(12), 0, dp(12), 0)
    }

    private companion object {
        val INK = Color.rgb(27, 34, 48)
        val MUTED = Color.rgb(113, 122, 137)
        val BLUE = Color.rgb(37, 99, 235)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
}
