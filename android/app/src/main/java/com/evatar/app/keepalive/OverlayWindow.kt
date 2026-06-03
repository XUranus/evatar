package com.evatar.app.keepalive

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView

class OverlayWindow(private val context: Context) {

    companion object {
        private const val BUBBLE_SIZE_DP = 56
        private const val PULSE_SIZE_DP = 64
    }

    private var windowManager: WindowManager? = null
    private var container: FrameLayout? = null
    private var bubbleView: View? = null
    private var statusDot: View? = null
    private var isShowing = false
    private var pulseAnimator: ValueAnimator? = null

    private val density = context.resources.displayMetrics.density
    private val bubbleSize = (BUBBLE_SIZE_DP * density).toInt()
    private val pulseSize = (PULSE_SIZE_DP * density).toInt()

    fun show() {
        if (isShowing) return

        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        container = FrameLayout(context)

        // Pulse ring (animates behind the bubble)
        val pulseView = object : View(context) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x332196F3.toInt()
                style = Paint.Style.FILL
            }
            override fun onDraw(c: Canvas) {
                val r = width.coerceAtMost(height) / 2f
                c.drawCircle(r, r, r, paint)
            }
        }.apply {
            layoutParams = FrameLayout.LayoutParams(pulseSize, pulseSize).apply {
                gravity = Gravity.CENTER
            }
        }
        container?.addView(pulseView)

        // Main bubble
        bubbleView = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xCC1E88E5.toInt())  // Semi-transparent blue
                setStroke((2 * density).toInt(), 0x44FFFFFF)
            }
            layoutParams = FrameLayout.LayoutParams(bubbleSize, bubbleSize).apply {
                gravity = Gravity.CENTER
            }
        }
        container?.addView(bubbleView)

        // Status dot (green = active, yellow = syncing)
        statusDot = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF4CAF50.toInt())
                setStroke((1.5 * density).toInt(), Color.WHITE)
            }
            layoutParams = FrameLayout.LayoutParams((12 * density).toInt(), (12 * density).toInt()).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                marginEnd = (2 * density).toInt()
                bottomMargin = (2 * density).toInt()
            }
        }
        container?.addView(statusDot)

        // Tiny label below bubble
        val label = TextView(context).apply {
            text = "E"
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(bubbleSize, bubbleSize).apply {
                gravity = Gravity.CENTER
            }
        }
        container?.addView(label)

        val params = WindowManager.LayoutParams(
            bubbleSize + (16 * density).toInt(),
            bubbleSize + (16 * density).toInt(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = (8 * density).toInt()
            y = (200 * density).toInt()
        }

        // Drag support
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var moved = false

        container?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (dx * dx + dy * dy > 25) moved = true
                    params.x = initialX - dx.toInt()
                    params.y = initialY + dy.toInt()
                    windowManager?.updateViewLayout(container, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        // Tap: toggle expanded info (future feature)
                    }
                    true
                }
                else -> false
            }
        }

        windowManager?.addView(container, params)
        isShowing = true

        startPulseAnimation(pulseView)
    }

    private fun startPulseAnimation(pulseView: View) {
        pulseAnimator = ValueAnimator.ofFloat(0.8f, 1.2f).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val scale = anim.animatedValue as Float
                pulseView.scaleX = scale
                pulseView.scaleY = scale
                pulseView.alpha = (1.3f - scale).coerceIn(0.1f, 0.5f)
            }
            start()
        }
    }

    fun setStatusSyncing() {
        statusDot?.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xFFFFC107.toInt())  // Yellow
            setStroke((1.5 * density).toInt(), Color.WHITE)
        }
    }

    fun setStatusIdle() {
        statusDot?.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xFF4CAF50.toInt())  // Green
            setStroke((1.5 * density).toInt(), Color.WHITE)
        }
    }

    fun setStatusError() {
        statusDot?.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xFFF44336.toInt())  // Red
            setStroke((1.5 * density).toInt(), Color.WHITE)
        }
    }

    fun updateStatus(text: String) {
        // Update dot color based on status keyword
        when {
            text.contains("同步中") || text.contains("upload") -> setStatusSyncing()
            text.contains("错误") || text.contains("error") -> setStatusError()
            else -> setStatusIdle()
        }
    }

    fun hide() {
        if (!isShowing) return
        pulseAnimator?.cancel()
        pulseAnimator = null
        try {
            windowManager?.removeView(container)
        } catch (_: Exception) {}
        isShowing = false
    }
}
