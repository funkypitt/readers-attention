package com.freedomfighter.readersattention.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

/**
 * A ring that grows while breathing in, stays while holding, shrinks while breathing out:
 * one stroke, the text's colour, nothing else. Five breaths of 3 s in, 3 s hold, 6 s out.
 */
class BreathView(context: Context, color: Int, private val density: Float) : View(context) {
    companion object {
        const val IN_MS = 3_000L
        const val HOLD_MS = 3_000L
        const val OUT_MS = 6_000L
        const val BREATH_MS = IN_MS + HOLD_MS + OUT_MS
        const val BREATHS = 5
        const val TOTAL_MS = BREATHS * BREATH_MS
        const val PHASE_IN = 0
        const val PHASE_HOLD = 1
        const val PHASE_OUT = 2

        /** The breath (from 1) and the phase at [elapsed] ms; null once the five are done. */
        fun at(elapsed: Long): Pair<Int, Int>? {
            if (elapsed >= TOTAL_MS) return null
            val inBreath = elapsed % BREATH_MS
            val phase = when { inBreath < IN_MS -> PHASE_IN; inBreath < IN_MS + HOLD_MS -> PHASE_HOLD; else -> PHASE_OUT }
            return (elapsed / BREATH_MS).toInt() + 1 to phase
        }

        /** How full the ring is, 0..1, at [elapsed] ms. */
        fun size(elapsed: Long): Float {
            if (elapsed >= TOTAL_MS) return 0.3f
            val t = elapsed % BREATH_MS
            return when {
                t < IN_MS -> 0.3f + 0.7f * t / IN_MS
                t < IN_MS + HOLD_MS -> 1f
                else -> 1f - 0.7f * (t - IN_MS - HOLD_MS) / OUT_MS
            }
        }
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = 2f * density }
    var fraction = 0.3f
        set(v) { field = v; invalidate() }

    override fun onDraw(canvas: Canvas) {
        val r = (minOf(width, height) / 2f - 4f * density) * fraction
        canvas.drawCircle(width / 2f, height / 2f, r, paint)
    }
}
