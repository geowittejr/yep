package com.georgewitte.yep.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.format.DateFormat
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.TextView
import com.georgewitte.yep.R
import com.georgewitte.yep.logic.Check
import com.georgewitte.yep.logic.Checks
import com.georgewitte.yep.logic.Freshness
import com.georgewitte.yep.logic.TimeText
import java.time.ZoneId
import java.util.Date
import java.util.Locale

/**
 * A card you press and hold until it fills up. Holding (instead of tapping) is deliberate: the extra
 * second of attention is what makes the moment stick in memory, and it prevents pocket taps.
 *
 * A quick tap calls the normal click listener (used to open history). Accessibility services get a
 * "Confirm" action instead of the hold gesture.
 */
@SuppressLint("ViewConstructor")
class HoldToConfirmCard(context: Context) : LinearLayout(context) {

    var onConfirmed: (() -> Unit)? = null

    private val emojiView = TextView(context)
    private val titleView = TextView(context)
    private val statusView = TextView(context)
    private val badgeView = TextView(context)

    private val radius = context.dp(22).toFloat()
    private val stroke = context.dp(1.5f).toFloat()
    private val shape = Path()
    private val outline = Path()
    private val bounds = RectF()
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.hold_fill) }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
    }

    private var progress = 0f
    private var holding = false
    private var completedThisPress = false
    private var holdStartedAt = 0L
    private var settleAnimator: ValueAnimator? = null
    private var boundStatus = ""

    /** Driven by frame time rather than ValueAnimator, so "Remove animations" can't turn a hold into a tap. */
    private val frame = object : Runnable {
        override fun run() {
            if (!holding) return
            progress = ((SystemClock.uptimeMillis() - holdStartedAt) / HOLD_MS.toFloat()).coerceIn(0f, 1f)
            invalidate()
            if (progress >= 1f) complete() else postOnAnimation(this)
        }
    }

    private val restoreStatus = Runnable { statusView.text = boundStatus }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setWillNotDraw(false)
        isClickable = true
        isFocusable = true
        minimumHeight = context.dp(92)
        val padding = context.dp(18)
        setPadding(padding, padding, padding, padding)

        emojiView.textSize = 30f
        emojiView.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO

        titleView.textSize = 18f
        titleView.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        titleView.setTextColor(context.getColor(R.color.text))

        statusView.textSize = 14f
        statusView.setPadding(0, context.dp(3), 0, 0)

        badgeView.textSize = 13f
        badgeView.gravity = Gravity.CENTER
        badgeView.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        badgeView.minWidth = context.dp(44)
        badgeView.minHeight = context.dp(44)
        badgeView.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO

        val column = LinearLayout(context).apply {
            orientation = VERTICAL
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            addView(titleView)
            addView(statusView)
        }
        addView(emojiView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            marginEnd = context.dp(16)
        })
        addView(column, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(badgeView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            marginStart = context.dp(12)
        })
    }

    fun bind(check: Check, now: Long) {
        emojiView.text = check.emoji
        titleView.text = check.name
        val last = check.lastConfirmed
        val freshness = Checks.freshness(now, last)
        boundStatus = if (last == null) {
            context.getString(R.string.never_confirmed)
        } else {
            val timeFormat = DateFormat.getTimeFormat(context)
            val text = TimeText.status(now, last, ZoneId.systemDefault(), Locale.getDefault()) {
                timeFormat.format(Date(it))
            }
            if (freshness == Freshness.FRESH) text else context.getString(R.string.last_prefix, text)
        }
        removeCallbacks(restoreStatus)
        statusView.text = boundStatus

        val fresh = freshness == Freshness.FRESH
        statusView.setTextColor(context.getColor(if (fresh) R.color.good else R.color.text_muted))
        backgroundPaint.color = context.getColor(if (fresh) R.color.good_soft else R.color.surface)
        strokePaint.color = context.getColor(if (fresh) R.color.good_soft else R.color.outline)
        if (fresh) {
            badgeView.text = "✓"
            badgeView.textSize = 22f
            badgeView.setTextColor(context.getColor(R.color.on_good))
            badgeView.background = roundedRect(context.getColor(R.color.good), context.dp(22).toFloat())
        } else {
            badgeView.text = context.getString(R.string.hold_badge)
            badgeView.textSize = 13f
            badgeView.setTextColor(context.getColor(R.color.brand))
            badgeView.background = roundedRect(
                context.getColor(android.R.color.transparent), context.dp(22).toFloat(),
                context.getColor(R.color.brand), context.dp(1.5f),
            )
        }
        contentDescription = context.getString(R.string.card_description, check.name, boundStatus)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bounds.set(0f, 0f, w.toFloat(), h.toFloat())
        shape.reset()
        shape.addRoundRect(bounds, radius, radius, Path.Direction.CW)
        val half = stroke / 2
        outline.reset()
        outline.addRoundRect(RectF(half, half, w - half, h - half), radius - half, radius - half, Path.Direction.CW)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawPath(shape, backgroundPaint)
        if (progress > 0f) {
            val filled = width * progress
            canvas.save()
            canvas.clipPath(shape)
            if (layoutDirection == LAYOUT_DIRECTION_RTL) {
                canvas.drawRect(width - filled, 0f, width.toFloat(), height.toFloat(), fillPaint)
            } else {
                canvas.drawRect(0f, 0f, filled, height.toFloat(), fillPaint)
            }
            canvas.restore()
        }
        canvas.drawPath(outline, strokePaint)
        super.onDraw(canvas)
    }

    @SuppressLint("ClickableViewAccessibility") // Taps still go through performClick(); see ACTION_UP.
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startHold()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (holding && !isInside(event)) release()
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!completedThisPress) {
                    val heldFor = SystemClock.uptimeMillis() - holdStartedAt
                    val wasHolding = holding
                    release()
                    if (wasHolding && heldFor < TAP_MS) {
                        performClick()
                    } else if (wasHolding) {
                        showHint()
                    }
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                release()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.addAction(
            AccessibilityNodeInfo.AccessibilityAction(R.id.action_confirm, context.getString(R.string.a11y_confirm)),
        )
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        if (action == R.id.action_confirm) {
            completedThisPress = false
            complete()
            return true
        }
        return super.performAccessibilityAction(action, arguments)
    }

    override fun onDetachedFromWindow() {
        holding = false
        removeCallbacks(frame)
        removeCallbacks(restoreStatus)
        settleAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    private fun startHold() {
        settleAnimator?.cancel()
        holding = true
        completedThisPress = false
        holdStartedAt = SystemClock.uptimeMillis()
        progress = 0f
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        postOnAnimation(frame)
    }

    private fun release() {
        if (!holding) return
        holding = false
        removeCallbacks(frame)
        settleTo(0f, startDelay = 0L, duration = 180L)
    }

    private fun complete() {
        holding = false
        completedThisPress = true
        removeCallbacks(frame)
        progress = 1f
        invalidate()
        val haptic = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
        performHapticFeedback(haptic)
        onConfirmed?.invoke()
        settleTo(0f, startDelay = 250L, duration = 350L)
    }

    private fun settleTo(target: Float, startDelay: Long, duration: Long) {
        settleAnimator?.cancel()
        settleAnimator = ValueAnimator.ofFloat(progress, target).apply {
            this.startDelay = startDelay
            this.duration = duration
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun showHint() {
        statusView.text = context.getString(R.string.keep_holding)
        removeCallbacks(restoreStatus)
        postDelayed(restoreStatus, 1_800L)
    }

    private fun isInside(event: MotionEvent): Boolean =
        event.x >= 0 && event.y >= 0 && event.x <= width && event.y <= height

    private companion object {
        const val HOLD_MS = 900L
        const val TAP_MS = 250L
    }
}
