package com.betterzl.module.hook

import android.content.res.Configuration
import android.graphics.RectF
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.AbsListView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.ProgressBar
import dev.ohmy.core.compat.XposedBridge
import com.betterzl.module.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Owns only children of the native main-tab root; no decor/window overlay or reparenting. */
class LiquidBarController private constructor(
    val root: ViewGroup,
    val tabs: ViewGroup,
    val pager: View,
    private val actionBar: View?,
) : View.OnAttachStateChangeListener {
    val density = root.resources.displayMetrics.density
    val barBounds = RectF()
    val indicator = RectF()
    val progress = WeakHashMap<View, Float>()
    private val listPadding = WeakHashMap<View, Int>()
    private val background = LiquidGlassView(root.context, this, false)
    private val foreground = LiquidGlassView(root.context, this, true)
    private var scanAt = 0L
    private var initialLayoutLogged = false
    private var imeHidden = false
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var touched = false
    private var dragX = 0f
    private var selected = 0
    private val motion = GlassMotion()
    val press: Float get() = motion.press
    private var motionBarWidth = 0f
    private var measureCalls = 0
    private var nativeBackgroundClears = 0
    private var hostDark: Boolean? = null
    private val backdropStability = BackdropStability()
    val refractionMix: Float get() = backdropStability.mix
    private var layoutGeometry = ""
    private var watchLoadingUntil = SystemClock.uptimeMillis() + 5000L
    private var lastTabChange = SystemClock.uptimeMillis()
    private var lastHostLayout = lastTabChange
    private var loadingScanAt = 0L
    private var pageLoading = false
    private var backdropGuardFrames = 0L
    var gravityX = 0f
        private set
    var gravityY = -1f
        private set
    private val touchSlop = ViewConfiguration.get(root.context).scaledTouchSlop
    val isDark: Boolean get() = hostDark ?: ((root.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES)
    val items: List<View> get() {
        val row = (0 until tabs.childCount).map { tabs.getChildAt(it) }
            .filterIsInstance<ViewGroup>().firstOrNull { group ->
                group.childCount == 5 && group.getChildAt(0).javaClass.name.endsWith("\$TabMainView")
            } ?: return emptyList()
        return (0 until row.childCount).map { row.getChildAt(it) }
    }
    val visible: Boolean get() = tabs.visibility == View.VISIBLE && !imeHidden && tabs.isShown && !barBounds.isEmpty

    init {
        root.addView(background, root.indexOfChild(tabs), FrameLayout.LayoutParams(-1, -1))
        root.addView(foreground, root.indexOfChild(tabs) + 1, FrameLayout.LayoutParams(-1, -1))
        // Native tips remain above both glass layers: preserve the existing tip elevation.
        background.elevation = tabs.elevation
        foreground.elevation = tabs.elevation + dp(0.1f)
        tabs.background = null
        tabs.clipToOutline = true
        tabs.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, view.height / 2f)
            }
        }
        for (i in 0 until tabs.childCount) {
            val child = tabs.getChildAt(i)
            if (child.javaClass.name.endsWith(".RevealView")) child.alpha = 0f
            if (child is LinearLayout) child.layoutParams = child.layoutParams.apply { height = dp(64f).toInt() }
        }
        // Native labels were only 6dp from a docked edge. Keep them outside the 10dp
        // lens rim so a pressed pill does not stretch glyphs at its lower boundary.
        items.filterIsInstance<ViewGroup>().forEach { item ->
            for (i in 0 until item.childCount) {
                val child = item.getChildAt(i)
                if (child is android.widget.TextView) {
                    (child.layoutParams as? FrameLayout.LayoutParams)?.let {
                        it.bottomMargin = dp(14f).toInt()
                        child.layoutParams = it
                    }
                }
            }
            val iconId = root.resources.getIdentifier("frametabsLayout", "id", "com.zing.zalo")
            item.findViewById<View>(iconId)?.let { it.translationY *= 1.25f }
        }
        root.addOnAttachStateChangeListener(this)
        root.viewTreeObserver.addOnScrollChangedListener {
            if (visible) { background.invalidate(); foreground.invalidate() }
        }
        root.viewTreeObserver.addOnPreDrawListener {
            if (root.isAttachedToWindow) {
                updateInsetsAndVisibility()
                val now = SystemClock.uptimeMillis()
                if (visible) updateBackdropStability(now)
                if (visible && now - scanAt > 600L) {
                    scanAt = now
                    protectScrollableContent(pager)
                    refreshHostTheme()
                }
            }
            true
        }
        XposedBridge.log("[BetterZaloGlass] Attached to native MainTabFrameLayout; 5 original items retained")
    }

    fun dp(value: Float) = value * density

    fun updateGravity(x: Float, y: Float) {
        if (abs(gravityX-x) + abs(gravityY-y) < 0.015f) return
        gravityX = x; gravityY = y
        if (visible) { background.invalidate(); foreground.invalidate() }
    }

    // Zalo's own Light/Dark preference is independent of Android uiMode. Read an opaque
    // background from the visible native page instead of guessing from system night mode.
    private fun refreshHostTheme() {
        val visibleRect = Rect()
        fun sample(view: View): Boolean? {
            if (view.visibility != View.VISIBLE || !view.getGlobalVisibleRect(visibleRect) ||
                visibleRect.width() < root.width * 0.6f || visibleRect.height() < root.height * 0.3f) return null
            val drawable = view.background
            if (drawable != null) {
                val color = if (drawable is ColorDrawable) drawable.color else runCatching {
                    val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
                    val oldBounds = Rect(drawable.bounds)
                    try { drawable.setBounds(0,0,2,2); drawable.draw(Canvas(bitmap)); bitmap.getPixel(1,1) }
                    finally { drawable.bounds = oldBounds; bitmap.recycle() }
                }.getOrNull()
                if (color != null && Color.alpha(color) > 220) return Color.luminance(color) < 0.3f
            }
            if (view is ViewGroup) for (i in 0 until view.childCount) sample(view.getChildAt(i))?.let { return it }
            return null
        }
        val value = sample(pager) ?: return
        if (hostDark != value) {
            hostDark = value
            background.invalidate(); foreground.invalidate()
        }
    }

    fun prepareMeasure(width: Int) {
        // CustomMainTab schedules a 50ms width-recalculation task when its width changes.
        // Narrow it BEFORE the host measurement; alternating full/narrow measurements
        // otherwise reschedules that task forever and causes an idle layout loop.
        val params = tabs.layoutParams as? FrameLayout.LayoutParams ?: return
        val side = min(dp(28f).toInt(), width / 10)
        params.width = ViewGroup.LayoutParams.MATCH_PARENT
        params.height = dp(64f).toInt()
        params.leftMargin = side
        params.rightMargin = side
    }

    fun measure() {
        measureCalls++
        val w = root.measuredWidth
        val h = root.measuredHeight
        if (w <= 0 || h <= 0) return
        val side = min(dp(28f).toInt(), w / 10)
        tabs.measure(exact(w - side * 2), exact(dp(64f).toInt()))
        val top = actionBar?.takeIf { it.visibility != View.GONE }?.measuredHeight ?: pager.top
        // Host originally subtracts its docked tab height. Restore that area as backdrop.
        pager.measure(exact(w), exact(max(0, h - top)))
        background.measure(exact(w), exact(h))
        foreground.measure(exact(w), exact(h))
    }

    fun layout() {
        if (root.width <= 0) return
        val geometry = "${root.width},${root.height},${pager.measuredWidth},${pager.measuredHeight}," +
            items.joinToString { "${it.left}:${it.width}" }
        if (geometry != layoutGeometry) {
            layoutGeometry = geometry
            lastHostLayout = SystemClock.uptimeMillis()
        }
        val insets = root.rootWindowInsets?.getInsets(WindowInsets.Type.navigationBars() or WindowInsets.Type.displayCutout())
        val safeBottom = insets?.bottom ?: 0
        val gap = if (safeBottom > 0) dp(24f).toInt() else dp(44f).toInt()
        val side = min(dp(28f).toInt(), root.width / 10)
        val bottom = root.height - safeBottom - gap
        val top = bottom - tabs.measuredHeight
        tabs.layout(side, top, root.width - side, bottom)
        barBounds.set(tabs.left.toFloat(), top.toFloat(), tabs.right.toFloat(), bottom.toFloat())
        pager.layout(pager.left, pager.top, pager.left + pager.measuredWidth, pager.top + pager.measuredHeight)
        background.layout(0, 0, root.width, root.height)
        foreground.layout(0, 0, root.width, root.height)
        tabs.invalidateOutline()
        updateInsetsAndVisibility()
        if (!initialLayoutLogged && items.size == 5) {
            initialLayoutLogged = true
            root.postDelayed({ writeState() }, 500)
            root.postDelayed({ writeState() }, 10000)
        }
        background.invalidate()
        foreground.invalidate()
    }

    fun onTabProgress(item: View, value: Float) {
        if (!value.isFinite()) return
        val next = value.coerceIn(0f, 1f)
        if (progress[item] != next) {
            lastTabChange = SystemClock.uptimeMillis()
            watchLoadingUntil = lastTabChange + 5000L
            backdropStability.invalidate(lastTabChange)
        }
        progress[item] = next
        val iconId = root.resources.getIdentifier("frametabsLayout", "id", "com.zing.zalo")
        item.findViewById<View>(iconId)?.translationY = -dp(10f)*value.coerceIn(0f,1f)
        // Resolve once at draw time, after the host updates all five progress values.
        foreground.invalidate()
        background.invalidate()
    }

    private fun updateBackdropStability(now: Long) {
        if ((now < watchLoadingUntil || refractionMix < 1f) && now - loadingScanAt >= 100L) {
            loadingScanAt = now
            var remaining = 160
            val visibleRect = Rect()
            fun loading(view: View): Boolean {
                if (--remaining < 0 || !view.isShown || view.alpha <= 0f || !view.getGlobalVisibleRect(visibleRect)) return false
                if (view is ProgressBar && view.isIndeterminate) return true
                // Also cover host loading widgets that wrap their own animated drawable.
                val name = view.javaClass.simpleName
                if ((view !is ViewGroup || view.childCount == 0) &&
                    (name.contains("Loading", true) || name.contains("ProgressView", true))) return true
                if (view is ViewGroup) for (i in 0 until view.childCount) if (loading(view.getChildAt(i))) return true
                return false
            }
            pageLoading = loading(pager)
        }
        val unstable = pageLoading || pager.isLayoutRequested || !pager.isLaidOut ||
            now - lastHostLayout < 120L || now - lastTabChange < 140L
        if (unstable) backdropGuardFrames++
        if (backdropStability.advance(now, unstable)) {
            background.postInvalidateOnAnimation()
            foreground.postInvalidateOnAnimation()
        }
    }


    private fun updateInsetsAndVisibility() {
        // The host theme engine can reapply white_top_line/BottomNav after inflation.
        // Keep that opaque native plate from covering the shared full-width material.
        if (tabs.background != null) {
            tabs.background = null
            nativeBackgroundClears++
        }
        imeHidden = root.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true
        if ((!visible || !root.hasWindowFocus()) && (touched || dragging)) {
            // A loading page can hide/detach the native tab before ACTION_UP arrives.
            touched = false
            dragging = false
            motion.reset()
            root.parent?.requestDisallowInterceptTouchEvent(false)
        }
        // Alpha, rather than GONE, retains the host's own navigation visibility decisions.
        tabs.alpha = if (imeHidden) 0f else 1f
        val layerVisibility = if (visible) View.VISIBLE else View.INVISIBLE
        background.visibility = layerVisibility
        foreground.visibility = layerVisibility
    }

    /** Observe taps; take over only an unambiguous horizontal indicator drag. */
    fun touch(event: MotionEvent, cancelNative: (MotionEvent) -> Unit): Boolean {
        if (!visible) {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                touched = false
                dragging = false
                motion.reset()
                root.parent?.requestDisallowInterceptTouchEvent(false)
            }
            return false
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y; touched = true; dragging = false
                foreground.invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && abs(event.x - downX) > touchSlop && abs(event.x - downX) > abs(event.y - downY) * 1.5f) {
                    dragging = true
                    val cancel = MotionEvent.obtain(event)
                    cancel.action = MotionEvent.ACTION_CANCEL
                    cancelNative(cancel)
                    cancel.recycle()
                    root.parent?.requestDisallowInterceptTouchEvent(true)
                }
                if (dragging) {
                    dragX = (event.x + tabs.left).coerceIn(barBounds.left, barBounds.right)
                    foreground.invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                touched = false
                val wasDragging = dragging
                if (dragging && event.actionMasked == MotionEvent.ACTION_UP) {
                    val all = items
                    val nearest = all.minByOrNull { abs((tabs.left + it.left + it.width / 2f) - dragX) }
                    nearest?.performClick()
                }
                dragging = false
                foreground.invalidate()
                root.parent?.requestDisallowInterceptTouchEvent(false)
                root.postDelayed({ writeState() }, 600)
                return wasDragging
            }
        }
        return false
    }

    /** Resolve one coherent native layout, then constrain all motion to the bar. */
    fun advance(now: Long): Boolean {
        val all = items
        if (all.size != 5 || barBounds.width() <= dp(8f) || barBounds.height() <= dp(8f)) {
            indicator.setEmpty()
            return false
        }
        if (abs(motionBarWidth - barBounds.width()) > 1f) {
            motion.reset()
            motionBarWidth = barBounds.width()
        }
        // Selection already initialized before attachment in some host versions.
        if (progress.isEmpty()) {
            selected = all.indexOfFirst { item ->
                (item as? ViewGroup)?.let { group ->
                    (0 until group.childCount).map { group.getChildAt(it) }
                        .filterIsInstance<android.widget.TextView>().any { it.visibility == View.VISIBLE }
                } == true
            }.coerceAtLeast(0)
        } else {
            val best = all.indices.maxByOrNull { progress[all[it]] ?: 0f }
            if (best != null && (progress[all[best]] ?: 0f) > (progress[all[selected.coerceIn(all.indices)]] ?: 0f)) selected = best
        }
        val native = all[selected.coerceIn(all.indices)]
        // A tab can briefly be unmeasured while its new page/resources attach.
        if (native.width <= 0) return false
        val targetWidth = native.width.toFloat().coerceIn(dp(48f).coerceAtMost(barBounds.width()), barBounds.width())
        val targetX = if (dragging) dragX - targetWidth / 2 else tabs.left + native.left.toFloat()
        val moving = motion.advance(now, targetX, targetWidth, barBounds.left, barBounds.right, touched)
        indicator.set(motion.x + dp(4f), barBounds.top + dp(4f), motion.x + motion.width - dp(4f), barBounds.bottom - dp(4f))
        return moving
    }

    private fun protectScrollableContent(view: View) {
        if (view.visibility != View.VISIBLE) return
        val large = view.height > root.height * 0.35f && view.width > root.width * 0.7f
        val vertical = view is AbsListView || view is ScrollView || view.javaClass.name.let {
            it.contains("RecyclerView") || it.contains("NestedScrollView")
        }
        if (large && vertical && view is ViewGroup) {
            val original = listPadding.getOrPut(view) { view.paddingBottom }
            val safe = root.height - barBounds.top.toInt() + dp(12f).toInt()
            if (view.paddingBottom != original + safe) {
                view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, original + safe)
                view.clipToPadding = false
            }
            // An outer scrolling viewport owns the reserve, not its nested horizontal lists.
            return
        }
        if (view is ViewGroup) for (i in 0 until view.childCount) protectScrollableContent(view.getChildAt(i))
    }

    private fun writeState() = runCatching {
        if (!root.isAttachedToWindow) return@runCatching
        val data = JSONObject().put("version", BuildConfig.VERSION_NAME).put("api", android.os.Build.VERSION.SDK_INT)
            .put("pid", android.os.Process.myPid())
            .put("root", "${root.width}x${root.height}").put("bar", barBounds.toShortString())
            .put("indicator", indicator.toShortString()).put("press", press)
            .put("renderPolicy", "continuous-blur")
            .put("pager", "${pager.left},${pager.top},${pager.right},${pager.bottom}")
            .put("itemCount", items.size).put("selected", selected).put("visible", visible)
            .put("imeHidden", imeHidden).put("hardwareAccelerated", root.isHardwareAccelerated)
            .put("shaderFrames", foreground.successfulFrames + background.successfulFrames)
            .put("lightAmbientFrames", foreground.ambientFrames + background.ambientFrames)
            .put("hostDark", isDark)
            .put("refractionMix", refractionMix).put("pageLoading", pageLoading)
            .put("backdropGuardFrames", backdropGuardFrames)
            .put("measureCalls", measureCalls)
            .put("nativeBackgroundClears", nativeBackgroundClears)
            .put("nativeTabBackground", tabs.background?.javaClass?.name ?: JSONObject.NULL)
            .put("shaderError", foreground.lastError ?: background.lastError ?: JSONObject.NULL)
            .put("paddedScrollContainers", listPadding.size)
        val bounds = JSONArray()
        items.forEach { bounds.put("${it.left},${it.top},${it.right},${it.bottom}") }
        data.put("items", bounds)
        File(root.context.filesDir, "betterzl-glass-state.json").writeText(data.toString(2))
    }

    override fun onViewAttachedToWindow(view: View) { background.invalidate(); foreground.invalidate() }
    override fun onViewDetachedFromWindow(view: View) { motion.reset(); touched = false; dragging = false; background.release(); foreground.release() }

    companion object {
        private fun exact(px: Int) = View.MeasureSpec.makeMeasureSpec(px, View.MeasureSpec.EXACTLY)
        fun create(root: ViewGroup): LiquidBarController? {
            fun id(name: String) = root.resources.getIdentifier(name, "id", "com.zing.zalo")
            val tabs = root.findViewById<View>(id("sliding_tabs")) as? ViewGroup ?: return null
            val pager = root.findViewById<View>(id("pager")) ?: return null
            if (tabs.javaClass.name != "com.zing.zalo.ui.maintab.widget.CustomMainTab") return null
            return LiquidBarController(root, tabs, pager, root.findViewById(id("zalo_action_bar")))
        }
    }
}
