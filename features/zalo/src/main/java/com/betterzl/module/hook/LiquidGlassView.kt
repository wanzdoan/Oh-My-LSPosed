package com.betterzl.module.hook

import android.content.Context
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.view.View
import dev.ohmy.core.compat.XposedBridge
import dev.ohmy.core.compat.XposedHelpers
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Native View/RenderNode port of the verified Miuix backdrop/lens pipeline.
 * Records ONLY the pager + original tab subtree, never the root/decor or itself.
 * No CPU readback, screenshots, window PixelCopy, network or Compose runtime in host.
 */
class LiquidGlassView(context: Context, private val controller: LiquidBarController, private val isForeground: Boolean) :
    View(context), SensorEventListener {
    private val capture = RenderNode("BetterZaloGlassBackdrop")
    private val surfaceCapture = RenderNode("BetterZaloGlassCombined")
    private val ambientPage = RenderNode("BetterZaloLightAmbientPage")
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val surfacePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var sheenKey = ""
    private var ambientShader: RuntimeShader? = null
    private var ambientEffect: RenderEffect? = null
    private var ambientSize = ""
    private var ambientCaptureSize = ""
    private var lens: RuntimeShader? = null
    private var bloom: RuntimeShader? = null
    private var saturation: RuntimeShader? = null
    private var cachedEffect: RenderEffect? = null
    private var cachedSize = ""
    private var sensorManager: SensorManager? = null
    var successfulFrames = 0L
        private set
    var ambientFrames = 0L
        private set
    var lastError: String? = null
        private set
    private var logged = false

    init {
        setWillNotDraw(false)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false
        isFocusable = false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isForeground) {
            sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)?.let {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
        }
    }

    override fun onDetachedFromWindow() {
        sensorManager?.unregisterListener(this)
        release()
        super.onDetachedFromWindow()
    }

    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0]
        val y = -event.values[1]
        val length = sqrt(x * x + y * y)
        val nextX = if (length > 0.98f) x / length else 0f
        val nextY = if (length > 0.98f) y / length else -1f
        controller.updateGravity(nextX, nextY)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onDraw(canvas: Canvas) {
        if (!controller.visible || !canvas.isHardwareAccelerated) return
        try {
            val moving = if (isForeground) controller.advance(SystemClock.uptimeMillis()) else false
            val bounds = if (isForeground) controller.indicator else controller.barBounds
            if (bounds.isEmpty) return
            ensureShaders()
            // Blur/material are always present. Loading only softens refraction;
            // replacing the live backdrop with a flat fallback caused visible flashing.
            if (isForeground) drawIndicator(canvas, bounds) else drawPanel(canvas, bounds)
            successfulFrames++
            if (!logged) {
                logged = true
                XposedBridge.log("[BetterZaloGlass] ${if (isForeground) "Indicator" else "Panel"} GPU lens rendered, hardware=true")
            }
            if (moving) postInvalidateOnAnimation()
        } catch (error: Throwable) {
            if (lastError == null) XposedBridge.log("[BetterZaloGlass] Renderer failed: $error")
            lastError = error.toString()
            // Keep native controls usable on driver errors; never throw into Zalo draw traversal.
            if (!isForeground) {
                paint.shader = null
                paint.color = if (controller.isDark) 0xee24262a.toInt() else 0xeef8f9fc.toInt()
                canvas.drawRoundRect(controller.barBounds, controller.barBounds.height()/2, controller.barBounds.height()/2, paint)
            }
        }
    }

    private fun ensureShaders() {
        if (lens == null) lens = RuntimeShader(if (isForeground) GlassShaders.LENS_DISPERSION else GlassShaders.LENS)
        if (bloom == null) bloom = RuntimeShader(GlassShaders.BLOOM)
        if (saturation == null) saturation = RuntimeShader(GlassShaders.SATURATION)
    }

    private fun recordBackdrop(node: RenderNode, bounds: RectF, padding: Float, withTabs: Boolean) {
        val width = (bounds.width() + 2*padding).toInt().coerceAtLeast(1)
        val height = (bounds.height() + 2*padding).toInt().coerceAtLeast(1)
        node.setPosition(0, 0, width, height)
        val recording = node.beginRecording(width, height)
        try {
            // Leave uncaptured pixels transparent. A short/empty native page must not
            // turn into an opaque white plate merely because the pager has no row there.
            recording.translate(padding - bounds.left, padding - bounds.top)
            // Reuse the host display list AFTER its normal traversal. Calling View.draw()
            // here consumes native dirty flags and leaves sibling icon lists stale.
            // The framework RenderNode already contains the view's left/top transform.
            recording.drawRenderNode(hostNode(controller.pager))
            if (withTabs) {
                // Recreate the already-blurred panel, then draw native icons/text above it.
                // The indicator refracts this combined image, as upstream CombinedBackdrop does.
                drawPanel(recording, controller.barBounds, shadow = false)
                recording.drawRenderNode(hostNode(controller.tabs))
            }
        } finally { node.endRecording() }
    }

    private fun hostNode(view: View): RenderNode =
        XposedHelpers.getObjectField(view, "mRenderNode") as RenderNode

    private fun lensEffect(bounds: RectF, padding: Float, indicator: Boolean): RenderEffect {
        val shader = lens!!
        shader.setFloatUniform("size", bounds.width(), bounds.height())
        shader.setFloatUniform("offset", -padding, -padding)
        val radius = bounds.height()/2
        shader.setFloatUniform("cornerRadii", radius, radius, radius, radius)
        val p = controller.press.coerceIn(0f, 1f)
        val strength = 0.35f + 0.65f * controller.refractionMix
        shader.setFloatUniform("refractionHeight", max(0.001f, controller.dp(if (indicator) 10f*p else 24f)))
        shader.setFloatUniform("refractionAmount", -controller.dp(if (indicator) 14f*p else 24f) * strength)
        shader.setFloatUniform("depthEffect", if (indicator) 1f else 0f)
        if (indicator) shader.setFloatUniform("chromaticAberration", 0.5f)
        val sizeKey = "${bounds.width()},${bounds.height()},$padding,${if (indicator) p else 0f},$strength"
        if (sizeKey != cachedSize || cachedEffect == null) {
            val effect = RenderEffect.createRuntimeShaderEffect(shader, "content")
            cachedEffect = if (indicator) effect else {
                val vibrant = RenderEffect.createRuntimeShaderEffect(saturation!!, "content")
                val blurred = RenderEffect.createBlurEffect(controller.dp(4f), controller.dp(4f), vibrant, Shader.TileMode.CLAMP)
                RenderEffect.createChainEffect(effect, blurred)
            }
            cachedSize = sizeKey
        }
        return cachedEffect!!
    }

    private fun drawPanel(canvas: Canvas, bounds: RectF, shadow: Boolean = true) {
        // A foreground view owns a separate panel renderer to avoid mutating its indicator uniforms.
        if (isForeground) {
            drawCombinedPanel(canvas, bounds)
            return
        }
        val padding = controller.dp(40f)
        recordBackdrop(capture, bounds, padding, false)
        capture.setRenderEffect(lensEffect(bounds, padding, false))
        if (shadow) {
            paint.shader = null; paint.color = Color.argb(18,0,0,0)
            paint.setShadowLayer(controller.dp(10f), 0f, controller.dp(2f), 0x33000000)
            canvas.drawRoundRect(bounds, bounds.height()/2, bounds.height()/2, paint)
            paint.clearShadowLayer()
        }
        clipPill(canvas, bounds) {
            canvas.save()
            canvas.translate(bounds.left-padding, bounds.top-padding)
            canvas.drawRenderNode(capture)
            canvas.restore()
            drawGlassSurface(canvas, bounds)
        }
        drawBloom(canvas, bounds, 0.75f, -45f)
    }

    // A cached native sub-renderer for the panel inside the indicator backdrop (no View attachment).
    private var combinedPanelShader: RuntimeShader? = null
    private var combinedPanelEffect: RenderEffect? = null
    private var combinedSize = ""
    private fun drawCombinedPanel(canvas: Canvas, bounds: RectF) {
        val padding = controller.dp(40f)
        recordBackdrop(capture, bounds, padding, false)
        val shader = combinedPanelShader ?: RuntimeShader(GlassShaders.LENS).also { combinedPanelShader = it }
        val r = bounds.height()/2
        shader.setFloatUniform("size", bounds.width(), bounds.height())
        shader.setFloatUniform("offset", -padding, -padding)
        shader.setFloatUniform("cornerRadii", r,r,r,r)
        shader.setFloatUniform("refractionHeight", controller.dp(24f))
        val strength = 0.35f + 0.65f * controller.refractionMix
        shader.setFloatUniform("refractionAmount", -controller.dp(24f) * strength)
        shader.setFloatUniform("depthEffect", 0f)
        val sizeKey = "${bounds.width()},${bounds.height()},$padding,$strength"
        if (combinedPanelEffect == null || sizeKey != combinedSize) {
            val vibrant = RenderEffect.createRuntimeShaderEffect(saturation!!,"content")
            val blurred = RenderEffect.createBlurEffect(controller.dp(4f),controller.dp(4f),vibrant,Shader.TileMode.CLAMP)
            combinedPanelEffect = RenderEffect.createChainEffect(RenderEffect.createRuntimeShaderEffect(shader,"content"),blurred)
            combinedSize = sizeKey
        }
        capture.setRenderEffect(combinedPanelEffect)
        clipPill(canvas,bounds) {
            canvas.save(); canvas.translate(bounds.left-padding,bounds.top-padding)
            canvas.drawRenderNode(capture); canvas.restore()
            drawGlassSurface(canvas, bounds)
        }
    }

    /** Shared material for panel/indicator. Light adds diffused real page hues;
     * dark retains its existing tint/reflection pipeline without the ambient pass. */
    private fun drawGlassSurface(canvas: Canvas, bounds: RectF) {
        val dark = controller.isDark
        if (!dark) drawLightAmbient(canvas, bounds)
        surfacePaint.color = if (dark) 0x26383c44 else 0x03000000
        canvas.drawRect(bounds, surfacePaint)
        val key = "${bounds.top},${bounds.bottom},$dark"
        if (sheenKey != key) {
            sheenPaint.shader = LinearGradient(
                0f, bounds.top, 0f, bounds.bottom,
                if (dark) intArrayOf(0x12ffffff, 0x00ffffff, 0x0a000000)
                else intArrayOf(0x08ffffff, 0x00ffffff, 0x03000000),
                floatArrayOf(0f, 0.42f, 1f), Shader.TileMode.CLAMP,
            )
            sheenKey = key
        }
        canvas.drawRect(bounds, sheenPaint)
    }

    private fun drawLightAmbient(canvas: Canvas, bounds: RectF) {
        // Only ~32x62 pixels are rasterized. Native view display lists are reused;
        // no screenshot/readback and no frame timer or additional host layout pass.
        val pager = controller.pager
        if (pager.width <= 0 || pager.height <= 0) return
        val w = 32
        val scale = w.toFloat() / pager.width
        val h = (pager.height * scale).toInt().coerceAtLeast(1)
        val captureKey = "${pager.left},${pager.top},${pager.width},${pager.height}"
        if (captureKey != ambientCaptureSize || !ambientPage.hasDisplayList()) {
            ambientPage.setPosition(0, 0, w, h)
            val recording = ambientPage.beginRecording(w, h)
            try {
                recording.scale(scale, scale)
                recording.translate(-pager.left.toFloat(), -pager.top.toFloat())
                recording.drawRenderNode(hostNode(pager))
            } finally { ambientPage.endRecording() }
            ambientCaptureSize = captureKey
        }
        val key = "$w,$h"
        if (ambientEffect == null || ambientSize != key) {
            val shader = ambientShader ?: RuntimeShader(LightAmbientShader.SOURCE).also { ambientShader = it }
            shader.setFloatUniform("sourceSize", w.toFloat(), h.toFloat())
            val softened = RenderEffect.createBlurEffect(0.5f, 0.5f, Shader.TileMode.CLAMP)
            ambientEffect = RenderEffect.createChainEffect(RenderEffect.createRuntimeShaderEffect(shader, "content"), softened)
            ambientSize = key
        }
        ambientPage.setRenderEffect(ambientEffect)
        ambientFrames++
        val checkpoint = canvas.save()
        try {
            canvas.translate(bounds.left, bounds.top)
            canvas.scale(bounds.width()/w, bounds.height()/h)
            canvas.drawRenderNode(ambientPage)
        } finally { canvas.restoreToCount(checkpoint) }
    }

    private fun drawIndicator(canvas: Canvas, bounds: RectF) {
        val padding = controller.dp(24f)
        recordBackdrop(surfaceCapture, bounds, padding, true)
        surfaceCapture.setRenderEffect(lensEffect(bounds,padding,true))
        val p = controller.press.coerceIn(0f,1f)
        // Clip BEFORE scaling: native tab widths can change while a page is loading.
        // Enlarging a duplicate icon/text image by 39% caused visible spill and ghosting.
        val checkpoint = canvas.save()
        path.reset()
        path.addRoundRect(controller.barBounds, controller.barBounds.height()/2, controller.barBounds.height()/2, Path.Direction.CW)
        canvas.clipPath(path)
        val scale = 1f + 0.06f*p
        canvas.scale(scale,scale,bounds.centerX(),bounds.centerY())
        clipPill(canvas,bounds) {
            canvas.save(); canvas.translate(bounds.left-padding,bounds.top-padding)
            canvas.drawRenderNode(surfaceCapture); canvas.restore()
            paint.shader=null
            paint.color=if(controller.isDark) Color.argb(((1-p)*18).toInt(),255,255,255)
                else Color.argb(((1-p)*14+6*p).toInt(),0,0,0)
            canvas.drawRect(bounds,paint)
        }
        drawBloom(canvas,bounds,p,90f)
        canvas.restoreToCount(checkpoint)
    }

    private inline fun clipPill(canvas: Canvas, bounds: RectF, block: () -> Unit) {
        canvas.save()
        path.reset(); path.addRoundRect(bounds,bounds.height()/2,bounds.height()/2,Path.Direction.CW)
        canvas.clipPath(path)
        block()
        canvas.restore()
    }

    private fun drawBloom(canvas: Canvas, bounds: RectF, alpha: Float, degrees: Float) {
        if (alpha <= 0.001f) return
        val shader=bloom!!
        val hw=bounds.width()/2; val hh=bounds.height()/2
        shader.setFloatUniform("halfView",hw,hh)
        shader.setFloatUniform("halfViewFloor",floor(hw),floor(hh))
        shader.setFloatUniform("cornerRadii",hh,hh,hh,hh)
        shader.setFloatUniform("strokeWidth",controller.dp(1f))
        val inner=controller.dp(2f)
        shader.setFloatUniform("innerBlurRadius",inner)
        shader.setFloatUniform("innerBlurRadiusSq",inner*inner)
        shader.setFloatUniform("highlightAlpha",alpha)
        shader.setColorUniform("strokeColor",Color.WHITE)
        shader.setFloatUniform("strokeAlphaMul",0.12f)
        val rad=(degrees*PI/180).toFloat()
        val gx=controller.gravityX
        val gy=controller.gravityY
        val dx=cos(rad)*gx-sin(rad)*gy
        val dy=sin(rad)*gx+cos(rad)*gy
        light(shader,"1",dx,dy,-0.05f,1f)
        light(shader,"2",0f,0.1f,-0.5f,0.4f)
        paint.shader=shader; paint.blendMode=BlendMode.PLUS; paint.alpha=255
        canvas.save(); canvas.translate(bounds.left,bounds.top)
        canvas.drawRect(0f,0f,bounds.width(),bounds.height(),paint)
        canvas.restore()
        paint.shader=null; paint.blendMode=null
    }

    private fun light(shader:RuntimeShader,suffix:String,x:Float,y:Float,z:Float,intensity:Float) {
        val len=sqrt(x*x+y*y+z*z).coerceAtLeast(1e-6f)
        shader.setFloatUniform("lightDir$suffix",x/len,y/len,z/len)
        shader.setColorUniform("lightColor$suffix",Color.WHITE)
        shader.setFloatUniform("lightIntensity$suffix",intensity)
    }

    fun release() { capture.discardDisplayList(); surfaceCapture.discardDisplayList(); ambientPage.discardDisplayList() }
}
