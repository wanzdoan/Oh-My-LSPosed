package com.betterzl.module.hook

import kotlin.math.abs
import kotlin.math.exp

/** Bounded, critically damped motion. Exact integration stays stable after slow host frames. */
internal class GlassMotion {
    var x = Float.NaN; private set
    var width = 0f; private set
    var press = 0f; private set
    private var vx = 0f
    private var vw = 0f
    private var vp = 0f
    private var lastFrame = 0L
    private var moving = false

    fun reset() { x = Float.NaN; vx = 0f; vw = 0f; vp = 0f; press = 0f; lastFrame = 0L; moving = false }

    fun advance(now: Long, targetX: Float, targetWidth: Float, left: Float, right: Float, pressed: Boolean): Boolean {
        if (!targetX.isFinite() || !targetWidth.isFinite() || !left.isFinite() || !right.isFinite() || right <= left) return false
        val tw = targetWidth.coerceIn(1f, right - left)
        val tx = targetX.coerceIn(left, right - tw)
        if (!x.isFinite()) { x = tx; width = tw }
        val elapsed = now - lastFrame
        val dt = if (lastFrame == 0L || (!moving && elapsed > 120L)) 1f / 60f
            else (elapsed / 1000f).coerceIn(0.001f, 0.25f)
        lastFrame = now
        val sx = spring(x, vx, tx, 22f, dt); x = sx.first; vx = sx.second
        val sw = spring(width, vw, tw, 26f, dt); width = sw.first; vw = sw.second
        val sp = spring(press, vp, if (pressed) 1f else 0f, 26f, dt); press = sp.first; vp = sp.second
        width = width.coerceIn(1f, right - left)
        val boundedX = x.coerceIn(left, right - width)
        if (boundedX != x) vx = 0f
        x = boundedX
        press = press.coerceIn(0f, 1f)
        moving = abs(tx - x) > 0.15f || abs(tw - width) > 0.15f || abs(vx) > 1f || abs(vw) > 1f ||
            abs((if (pressed) 1f else 0f) - press) > 0.002f || abs(vp) > 0.01f
        if (!moving) { x = tx; width = tw; press = if (pressed) 1f else 0f; vx = 0f; vw = 0f; vp = 0f }
        return moving
    }

    private fun spring(value: Float, velocity: Float, target: Float, omega: Float, dt: Float): Pair<Float, Float> {
        val displacement = value - target
        val c = velocity + omega * displacement
        val decay = exp(-omega * dt)
        return Pair(target + (displacement + c * dt) * decay, (velocity - omega * c * dt) * decay)
    }
}
