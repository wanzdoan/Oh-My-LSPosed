package com.betterzl.module.hook

/** Soften lens distortion during initial loading. Never controls blur or material opacity. */
internal class BackdropStability {
    var mix = 0f; private set
    private var lastUnstable = 0L
    private var lastFrame = 0L

    fun invalidate(now: Long) { lastUnstable = now }

    fun advance(now: Long, unstable: Boolean): Boolean {
        val dt = if (lastFrame == 0L) 0L else (now - lastFrame).coerceIn(0L, 50L)
        lastFrame = now
        if (unstable) invalidate(now)
        val target = if (now - lastUnstable >= 180L) 1f else 0f
        val step = dt / 180f
        mix = if (target > mix) (mix + step).coerceAtMost(target) else (mix - step).coerceAtLeast(target)
        return mix < 1f
    }
}
