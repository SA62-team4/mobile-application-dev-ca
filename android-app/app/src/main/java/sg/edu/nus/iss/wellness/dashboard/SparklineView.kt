package sg.edu.nus.iss.wellness.dashboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.toColorInt
import kotlin.math.abs

/**
 * Canvas sparkline view. Tap or drag along the chart to reveal the value of the
 * nearest point in a small tooltip.
 *
 * @author Jemilin Beulah Suria Christopher Raj
 */
class SparklineView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private var series: SparklineDataSeries? = null

    // Point positions from the last draw, reused for hit-testing taps.
    private var pointXs = FloatArray(0)
    private var pointYs = FloatArray(0)
    private var activeIndex: Int? = null

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = "#64748B".toColorInt()
    }

    private val tooltipBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = "#111827".toColorInt()
    }

    private val tooltipTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
    }

    private val topPadding = 12f
    private val dotRadius = 5f
    private val labelAreaHeight: Float get() = labelPaint.textSize + 8f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredH = (56 * resources.displayMetrics.density).toInt()
        setMeasuredDimension(
            resolveSize(MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(200), widthMeasureSpec),
            resolveSize(desiredH, heightMeasureSpec)
        )
    }

    fun setData(newSeries: SparklineDataSeries) {
        series = newSeries
        activeIndex = null
        linePaint.color = newSeries.color
        dotPaint.color = newSeries.color
        barPaint.color = newSeries.color
        labelPaint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10f, resources.displayMetrics)
        tooltipTextPaint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, resources.displayMetrics)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val s = series ?: return
        if (s.points.isEmpty()) return
        if (s.mode == SparklineMode.BAR) drawBars(canvas, s) else drawLine(canvas, s)
        drawAxisLabels(canvas, s)
        activeIndex?.let { drawTooltip(canvas, s, it) }
    }

    private fun drawLine(canvas: Canvas, s: SparklineDataSeries) {
        val chartH = height - labelAreaHeight - topPadding
        val count = s.points.size
        val minVal = s.points.min()
        val maxVal = s.points.max()
        val flat = maxVal - minVal < 0.001f
        val range = if (flat) 1f else maxVal - minVal

        fun xAt(i: Int) = if (count == 1) width / 2f else i * (width - 1f) / (count - 1)
        // Center flat lines vertically.
        fun yAt(v: Float) = if (flat) topPadding + chartH / 2f
                            else topPadding + chartH * (1f - (v - minVal) / range)

        recordPoints(count) { i -> xAt(i) to yAt(s.points[i]) }

        if (count >= 2) {
            val path = Path()
            path.moveTo(xAt(0), yAt(s.points[0]))
            for (i in 1 until count) path.lineTo(xAt(i), yAt(s.points[i]))
            canvas.drawPath(path, linePaint)
        }

        if (s.dotsAtAllPoints) {
            // Mood dots.
            for (i in 0 until count) {
                canvas.drawCircle(xAt(i), yAt(s.points[i]), dotRadius, dotPaint)
            }
        } else {
            // Sleep endpoint.
            canvas.drawCircle(xAt(count - 1), yAt(s.points.last()), dotRadius + 1f, dotPaint)
        }
    }

    private fun drawBars(canvas: Canvas, s: SparklineDataSeries) {
        val chartH = height - labelAreaHeight - topPadding
        val maxVal = s.points.max().coerceAtLeast(1f)
        val count = s.points.size
        val slotW = width.toFloat() / count
        val barW = slotW * 0.6f
        val minBarH = 4f
        barPaint.alpha = 204

        recordPoints(count) { i ->
            val barH = ((s.points[i] / maxVal) * chartH).coerceAtLeast(minBarH)
            (i * slotW + slotW / 2f) to (topPadding + chartH - barH)
        }

        s.points.forEachIndexed { i, value ->
            if (value <= 0f) return@forEachIndexed
            val barH = ((value / maxVal) * chartH).coerceAtLeast(minBarH)
            val left = i * slotW + (slotW - barW) / 2f
            val top = topPadding + chartH - barH
            canvas.drawRoundRect(RectF(left, top, left + barW, topPadding + chartH), 4f, 4f, barPaint)
        }
    }

    private fun drawAxisLabels(canvas: Canvas, s: SparklineDataSeries) {
        val y = height - 2f
        val count = s.points.size
        s.dates.forEachIndexed { i, label ->
            val x = if (count == 1) width / 2f else i * (width - 1f) / (count - 1)
            canvas.drawText(label, x, y, labelPaint)
        }
    }

    private fun drawTooltip(canvas: Canvas, s: SparklineDataSeries, idx: Int) {
        if (idx !in pointXs.indices) return
        val px = pointXs[idx]
        val py = pointYs[idx]

        // Emphasise the selected point.
        canvas.drawCircle(px, py, dotRadius + 2f, dotPaint)

        val label = s.dates.getOrNull(idx)
        val value = formatValue(s.points[idx]) + s.valueSuffix
        val text = if (label != null) "$label  $value" else value

        val padH = 12f
        val padV = 7f
        val boxW = tooltipTextPaint.measureText(text) + padH * 2
        val boxH = tooltipTextPaint.textSize + padV * 2
        val left = (px - boxW / 2f).coerceIn(2f, (width - boxW - 2f).coerceAtLeast(2f))
        val top = if (py - boxH - 10f >= 0f) py - boxH - 10f else py + 10f

        canvas.drawRoundRect(RectF(left, top, left + boxW, top + boxH), 8f, 8f, tooltipBgPaint)
        val baseline = top + boxH / 2f - (tooltipTextPaint.descent() + tooltipTextPaint.ascent()) / 2f
        canvas.drawText(text, left + boxW / 2f, baseline, tooltipTextPaint)
    }

    private inline fun recordPoints(count: Int, xy: (Int) -> Pair<Float, Float>) {
        if (pointXs.size != count) {
            pointXs = FloatArray(count)
            pointYs = FloatArray(count)
        }
        for (i in 0 until count) {
            val (x, y) = xy(i)
            pointXs[i] = x
            pointYs[i] = y
        }
    }

    private fun formatValue(v: Float): String {
        val rounded = Math.round(v * 10) / 10.0
        return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val s = series ?: return false
        if (s.points.isEmpty() || pointXs.isEmpty()) return false
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                // Let the user scrub without the scroll view stealing the gesture.
                parent?.requestDisallowInterceptTouchEvent(true)
                activeIndex = nearestIndex(event.x)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                if (event.action == MotionEvent.ACTION_UP) performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun nearestIndex(x: Float): Int {
        var best = 0
        var bestDist = Float.MAX_VALUE
        for (i in pointXs.indices) {
            val d = abs(pointXs[i] - x)
            if (d < bestDist) {
                bestDist = d
                best = i
            }
        }
        return best
    }
}
