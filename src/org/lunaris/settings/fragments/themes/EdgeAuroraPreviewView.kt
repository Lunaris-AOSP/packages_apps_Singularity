/*
 * Copyright (C) 2024-2026 Lunaris AOSP
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lunaris.settings.fragments.themes

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.android.internal.util.lunaris.EdgeAuroraConfig
import com.android.internal.util.lunaris.EdgeAuroraConfig.Edge
import com.android.internal.util.lunaris.EdgeAuroraConfig.Profile
import com.android.internal.util.lunaris.EdgeAuroraRenderer
import kotlin.math.abs
import kotlin.math.min

class EdgeAuroraPreviewView
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val renderer = EdgeAuroraRenderer()
    private val phone = RectF()
    private val path = Path()
    var profiles: List<Profile> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    var onProfileMoved: ((Profile) -> Unit)? = null
    private var dragging: Profile? = null
    private val frame =
        object : Runnable {
            override fun run() {
                if (isAttachedToWindow && windowVisibility == VISIBLE) {
                    if (ValueAnimator.areAnimatorsEnabled()) invalidate()
                    postDelayed(this, if (ValueAnimator.areAnimatorsEnabled()) 33 else 250)
                }
            }
        }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        removeCallbacks(frame)
        post(frame)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(frame)
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        removeCallbacks(frame)
        if (visibility == VISIBLE) post(frame)
    }

    override fun onDraw(canvas: Canvas) {
        val d = resources.displayMetrics.density
        val h = height - 24f * d
        val w = min(width - 48f * d, h * .52f)
        phone.set((width - w) / 2, 12f * d, (width + w) / 2, height - 12f * d)
        paint.style = Paint.Style.FILL
        paint.color = 0xff101116.toInt()
        canvas.drawRoundRect(phone, 22f * d, 22f * d, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = d
        paint.color = 0xff737681.toInt()
        canvas.drawRoundRect(phone, 22f * d, 22f * d, paint)
        paint.style = Paint.Style.FILL
        path.reset()
        path.addRoundRect(phone, 22f * d, 22f * d, Path.Direction.CW)
        val save = canvas.save()
        canvas.clipPath(path)
        val items =
            dragging?.let { listOf(it) }
                ?: profiles.ifEmpty {
                    listOf(Profile("sample").apply { motion = EdgeAuroraConfig.Motion.AURORA })
                }
        EdgeAuroraConfig.resolveOverlaps(items).forEach {
            renderer.draw(
                canvas,
                it,
                phone,
                d * .55f,
                if (ValueAnimator.areAnimatorsEnabled())
                    (SystemClock.uptimeMillis() % 3600000) / 1000f
                else 0f,
            )
        }
        canvas.restoreToCount(save)
        paint.color = Color.LTGRAY
        paint.textSize = 18f * d
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(
            android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date()),
            phone.centerX(),
            phone.top + 46f * d,
            paint,
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (onProfileMoved == null || profiles.size != 1 || phone.isEmpty)
            return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = profiles.first().copy()
                parent.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> Unit
            MotionEvent.ACTION_UP -> {
                updateDrag(event.x, event.y)
                dragging?.let { onProfileMoved?.invoke(it) }
                dragging = null
                parent.requestDisallowInterceptTouchEvent(false)
                performClick()
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                dragging = null
                parent.requestDisallowInterceptTouchEvent(false)
                invalidate()
                return true
            }
            else -> return false
        }
        updateDrag(event.x, event.y)
        return true
    }

    private fun updateDrag(x: Float, y: Float) {
        val profile = dragging ?: return
        val distances =
            listOf(
                abs(x - phone.left),
                abs(x - phone.right),
                abs(y - phone.top),
                abs(y - phone.bottom),
            )
        profile.edge = Edge.values()[distances.indexOf(distances.minOrNull())]
        profile.position =
            if (profile.edge == Edge.LEFT || profile.edge == Edge.RIGHT)
                (y - phone.top) / phone.height() - profile.length / 2f
            else (x - phone.left) / phone.width() - profile.length / 2f
        profile.clamp()
        invalidate()
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
