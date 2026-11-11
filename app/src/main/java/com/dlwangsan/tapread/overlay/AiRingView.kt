package com.dlwangsan.tapread.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.dlwangsan.tapread.TtsManager
import kotlin.math.sin

/** Decorative only: the existing button remains the stable accessibility/touch target. */
class AiRingView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val matrix = Matrix()
    private var gradient: SweepGradient? = null
    private var animator: ValueAnimator? = null
    private var phase = 0f
    private var intensity = 0.18f
    private val strokeWidths = floatArrayOf(10f, 7f, 4.5f, 2.4f)
    private val strokeAlphas = intArrayOf(9, 14, 22, 240)
    private var state = TtsManager.PlaybackState.IDLE
    private val density = resources.displayMetrics.density

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    fun setPlaybackState(value: TtsManager.PlaybackState) {
        if (state == value) return
        state = value
        updateAnimation()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        gradient = SweepGradient(w / 2f, h / 2f, intArrayOf(
            Color.rgb(74, 168, 255), Color.rgb(88, 100, 248), Color.rgb(170, 101, 245),
            Color.rgb(243, 139, 200), Color.rgb(255, 187, 146), Color.rgb(112, 223, 239),
            Color.rgb(74, 168, 255)
        ), null)
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); updateAnimation() }
    override fun onDetachedFromWindow() { animator?.cancel(); animator = null; super.onDetachedFromWindow() }
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        // May run during View construction, before Kotlin fields are initialized.
        if (isAttachedToWindow) updateAnimation()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (isAttachedToWindow) updateAnimation()
    }

    private fun updateAnimation() {
        animator?.cancel()
        animator = null
        if (!isAttachedToWindow || !isShown || windowVisibility != VISIBLE || !ValueAnimator.areAnimatorsEnabled()) {
            intensity = if (state == TtsManager.PlaybackState.IDLE) 0.18f else 1f
            invalidate()
            return
        }
        if (state == TtsManager.PlaybackState.IDLE) {
            animator = ValueAnimator.ofFloat(intensity, 0.18f).apply {
                duration = 450L
                addUpdateListener { intensity = it.animatedValue as Float; invalidate() }
                start()
            }
        } else {
            val startIntensity = intensity
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = if (state == TtsManager.PlaybackState.PREPARING) 3000L else 5400L
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    phase = it.animatedValue as Float
                    intensity = (startIntensity + it.currentPlayTime / 350f).coerceAtMost(1f)
                    invalidate()
                }
                start()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(cx, cy) - 6.8f * density
        if (radius <= 0f) return
        matrix.setRotate(phase * 360f, cx, cy)
        gradient?.setLocalMatrix(matrix)
        paint.shader = gradient
        val breathe = if (state == TtsManager.PlaybackState.SPEAKING)
            0.75f + 0.25f * sin(phase * Math.PI * 4).toFloat() else 0.8f
        // Layered translucent strokes keep the glow inside the window, without software blur.
        for (index in strokeWidths.indices) {
            val stroke = strokeWidths[index]
            paint.strokeWidth = stroke * density
            paint.alpha = (strokeAlphas[index] * intensity * if (stroke > 2.4f) breathe else 1f).toInt()
            canvas.drawCircle(cx, cy, radius, paint)
        }
    }
}
