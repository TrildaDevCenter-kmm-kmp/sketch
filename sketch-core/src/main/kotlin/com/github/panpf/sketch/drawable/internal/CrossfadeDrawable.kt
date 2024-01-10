/*
 * Copyright 2023 Coil Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * ------------------------------------------------------------------------
 * Copyright (C) 2022 panpf <panpfpanpf@outlook.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.panpf.sketch.drawable.internal

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Drawable.Callback
import android.os.Build.VERSION
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.TintAwareDrawable
import androidx.core.graphics.withSave
import androidx.vectordrawable.graphics.drawable.Animatable2Compat
import com.github.panpf.sketch.decode.internal.computeSizeMultiplier
import com.github.panpf.sketch.util.requiredMainThread
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A [Drawable] that crossfades from [start] to [end].
 *
 * NOTE: The animation can only be executed once as the [start]
 * drawable is dereferenced at the end of the transition.
 *
 * @param start The [Drawable] to crossfade from.
 * @param end The [Drawable] to crossfade to.
 * @param durationMillis The duration of the crossfade animation.
 * @param fadeStart If false, the start drawable will not fade out while the end drawable fades in.
 * @param preferExactIntrinsicSize If true, this drawable's intrinsic width/height will only be -1
 *  if [start] **and** [end] return -1 for that dimension. If false, the intrinsic width/height will
 *  be -1 if [start] **or** [end] return -1 for that dimension. This is useful for views that
 *  require an exact intrinsic size to scale the drawable.
 */
open class CrossfadeDrawable @JvmOverloads constructor(
    start: Drawable?,
    end: Drawable?,
    val fitScale: Boolean = true,
    val durationMillis: Int = DEFAULT_DURATION,
    val fadeStart: Boolean = true,
    val preferExactIntrinsicSize: Boolean = false,
) : Drawable(), Animatable2Compat, Callback {

    companion object {
        private const val STATE_START = 0
        private const val STATE_RUNNING = 1
        private const val STATE_DONE = 2

        const val DEFAULT_DURATION = 200
    }

    private val callbacks = mutableListOf<Animatable2Compat.AnimationCallback>()
    private val handler by lazy { Handler(Looper.getMainLooper()) }

    private val intrinsicWidth =
        computeIntrinsicDimension(start?.intrinsicWidth, end?.intrinsicWidth)
    private val intrinsicHeight =
        computeIntrinsicDimension(start?.intrinsicHeight, end?.intrinsicHeight)

    private var startTimeMillis = 0L
    private var maxAlpha = 255
    private var state = STATE_START

    var start: Drawable? = start?.mutate()
        private set
    val end: Drawable? = end?.mutate()

    init {
        require(durationMillis > 0) { "durationMillis must be > 0." }
        setChildCallback()
    }

    private fun setChildCallback() {
        this.start?.apply {
            if (callback == null || callbacks !== this@CrossfadeDrawable) {
                callback = this@CrossfadeDrawable
            }
        }
        this.end?.apply {
            if (callback == null || callbacks !== this@CrossfadeDrawable) {
                callback = this@CrossfadeDrawable
            }
        }
    }

    override fun draw(canvas: Canvas) {
        if (state == STATE_START) {
            start?.apply {
                alpha = maxAlpha
                canvas.withSave { draw(canvas) }
            }
            return
        }

        if (state == STATE_DONE) {
            end?.apply {
                alpha = maxAlpha
                canvas.withSave { draw(canvas) }
            }
            return
        }

        val percent = (SystemClock.uptimeMillis() - startTimeMillis) / durationMillis.toDouble()
        val endAlpha = (percent.coerceIn(0.0, 1.0) * maxAlpha).toInt()
        val startAlpha = if (fadeStart) maxAlpha - endAlpha else maxAlpha
        val isDone = percent >= 1.0

        // Draw the start drawable.
        if (!isDone) {
            start?.apply {
                alpha = startAlpha
                canvas.withSave { draw(canvas) }
            }
        }

        // Draw the end drawable.
        end?.apply {
            alpha = endAlpha
            canvas.withSave { draw(canvas) }
        }

        if (isDone) {
            markDone()
        } else {
            invalidateSelf()
        }
    }

    override fun getAlpha() = maxAlpha

    override fun setAlpha(alpha: Int) {
        require(alpha in 0..255) { "Invalid alpha: $alpha" }
        maxAlpha = alpha
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun getOpacity(): Int {
        val start = start
        val end = end

        if (state == STATE_START) {
            return start?.opacity ?: PixelFormat.TRANSPARENT
        }

        if (state == STATE_DONE) {
            return end?.opacity ?: PixelFormat.TRANSPARENT
        }

        return when {
            start != null && end != null -> resolveOpacity(start.opacity, end.opacity)
            start != null -> start.opacity
            end != null -> end.opacity
            else -> PixelFormat.TRANSPARENT
        }
    }

    override fun getColorFilter(): ColorFilter? =
        if (VERSION.SDK_INT >= 21) {
            when (state) {
                STATE_START -> start?.colorFilter
                STATE_RUNNING -> end?.colorFilter ?: start?.colorFilter
                STATE_DONE -> end?.colorFilter
                else -> null
            }
        } else {
            null
        }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        start?.colorFilter = colorFilter
        end?.colorFilter = colorFilter
    }

    override fun onBoundsChange(bounds: Rect) {
        /*
         Why set callback here?
         Because when start and the current Drawable of ImageView are the same instance,
         the callback of start will become null after setImageDrawable() is executed.
         Of course, setChildCallback should be called in the setCallback method,
         but it is final and can only be called in onBoundsChange
         */
        setChildCallback()
        start?.let { updateBounds(it, bounds) }
        end?.let { updateBounds(it, bounds) }
    }

    override fun onLevelChange(level: Int): Boolean {
        val startChanged = start?.setLevel(level) ?: false
        val endChanged = end?.setLevel(level) ?: false
        return startChanged || endChanged
    }

    override fun onStateChange(state: IntArray): Boolean {
        val startChanged = start?.setState(state) ?: false
        val endChanged = end?.setState(state) ?: false
        return startChanged || endChanged
    }

    override fun getIntrinsicWidth() = intrinsicWidth

    override fun getIntrinsicHeight() = intrinsicHeight

    override fun setTint(tintColor: Int) {
        start?.let { DrawableCompat.setTint(it, tintColor) }
        end?.let { DrawableCompat.setTint(it, tintColor) }
    }

    override fun setTintList(tint: ColorStateList?) {
        start?.let { DrawableCompat.setTintList(it, tint) }
        end?.let { DrawableCompat.setTintList(it, tint) }
    }

    @SuppressLint("RestrictedApi")
    override fun setTintMode(tintMode: PorterDuff.Mode?) {
        if (VERSION.SDK_INT >= 21) {
            start?.setTintMode(tintMode)
            end?.setTintMode(tintMode)
        } else {
            if (start is TintAwareDrawable && tintMode != null) {
                (start as TintAwareDrawable).setTintMode(tintMode)
            }
            if (end is TintAwareDrawable && tintMode != null) {
                (end as TintAwareDrawable).setTintMode(tintMode)
            }
        }
    }

    @RequiresApi(29)
    override fun setTintBlendMode(blendMode: BlendMode?) {
        start?.setTintBlendMode(blendMode)
        end?.setTintBlendMode(blendMode)
    }

    override fun isRunning() = state == STATE_RUNNING

    override fun start() {
        (start as? Animatable)?.start()
        (end as? Animatable)?.start()

        if (state != STATE_START) {
            return
        }

        state = STATE_RUNNING
        startTimeMillis = SystemClock.uptimeMillis()
        handler.post {
            callbacks.forEach { it.onAnimationStart(this) }
        }

        invalidateSelf()
    }

    override fun stop() {
        (start as? Animatable)?.stop()
        (end as? Animatable)?.stop()

        if (state != STATE_DONE) {
            markDone()
        }
    }

    override fun registerAnimationCallback(callback: Animatable2Compat.AnimationCallback) {
        requiredMainThread()    // Consistent with AnimatedImageDrawable
        callbacks.add(callback)
    }

    override fun unregisterAnimationCallback(callback: Animatable2Compat.AnimationCallback): Boolean {
        return callbacks.remove(callback)
    }

    override fun clearAnimationCallbacks() = callbacks.clear()

    /** Update the [Drawable]'s bounds inside [targetBounds] preserving aspect ratio. */
    private fun updateBounds(drawable: Drawable, targetBounds: Rect) {
        val width = drawable.intrinsicWidth
        val height = drawable.intrinsicHeight
        if (width <= 0 || height <= 0) {
            drawable.bounds = targetBounds
            return
        }

        val targetWidth = targetBounds.width()
        val targetHeight = targetBounds.height()
        val multiplier = computeSizeMultiplier(width, height, targetWidth, targetHeight, fitScale)
        val dx = ((targetWidth - multiplier * width) / 2).roundToInt()
        val dy = ((targetHeight - multiplier * height) / 2).roundToInt()

        val left = targetBounds.left + dx
        val top = targetBounds.top + dy
        val right = targetBounds.right - dx
        val bottom = targetBounds.bottom - dy
        drawable.setBounds(left, top, right, bottom)
    }

    private fun computeIntrinsicDimension(startSize: Int?, endSize: Int?): Int {
//        if (!preferExactIntrinsicSize && (startSize == -1 || endSize == -1)) return -1
//        return max(startSize ?: -1, endSize ?: -1)
        if (preferExactIntrinsicSize || (startSize != -1 && endSize != -1)) {
            return max(startSize ?: -1, endSize ?: -1)
        }
        return -1
    }

    private fun markDone() {
        state = STATE_DONE
        start = null
        handler.post {
            callbacks.forEach { it.onAnimationEnd(this) }
        }
    }

    override fun mutate(): CrossfadeDrawable {
        val newStart = start?.mutate()
        val newEnd = end?.mutate()
        return if (newStart !== start || newEnd !== end) {
            CrossfadeDrawable(
                start = newStart,
                end = newEnd,
                fitScale = fitScale,
                durationMillis = durationMillis,
                fadeStart = fadeStart,
                preferExactIntrinsicSize = preferExactIntrinsicSize
            )
        } else {
            this
        }
    }

    override fun invalidateDrawable(who: Drawable) {
        invalidateSelf()
    }

    override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
        scheduleSelf(what, `when`)
    }

    override fun unscheduleDrawable(who: Drawable, what: Runnable) {
        unscheduleSelf(what)
    }
}