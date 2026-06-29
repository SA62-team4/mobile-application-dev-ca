package sg.edu.nus.iss.wellness.dashboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View

/**
 * Custom view that draws a simple sparkline (line or bar) on an Android Canvas.
 * Keeps implementation lightweight by drawing directly to the canvas without third-party dependencies.
 *
 * @author SA62 Team
 */
class SparklineView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private var series: SparklineDataSeries? = null

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
        color = Color.parseColor("#64748B")
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
        linePaint.color = newSeries.color
        dotPaint.color = newSeries.color
        barPaint.color = newSeries.color
        labelPaint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10f, resources.displayMetrics)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val s = series ?: return
        if (s.points.isEmpty()) return
        if (s.mode == SparklineMode.BAR) drawBars(canvas, s) else drawLine(canvas, s)
        drawAxisLabels(canvas, s)
    }

    private fun drawLine(canvas: Canvas, s: SparklineDataSeries) {
        val chartH = height - labelAreaHeight - topPadding
        val count = s.points.size
        val minVal = s.points.min()
        val maxVal = s.points.max()
        val flat = maxVal - minVal < 0.001f  // identical values (e.g., 7.5h every day)
        val range = if (flat) 1f else maxVal - minVal

        fun xAt(i: Int) = if (count == 1) width / 2f else i * (width - 1f) / (count - 1)
        // Flat/single-value lines are centered vertically so they render mid-card, not at the bottom
        fun yAt(v: Float) = if (flat) topPadding + chartH / 2f
                            else topPadding + chartH * (1f - (v - minVal) / range)

        if (count >= 2) {
            val path = Path()
            path.moveTo(xAt(0), yAt(s.points[0]))
            for (i in 1 until count) path.lineTo(xAt(i), yAt(s.points[i]))
            canvas.drawPath(path, linePaint)
        }

        if (s.dotsAtAllPoints) {
            // Mood: dot at every point
            for (i in 0 until count) {
                canvas.drawCircle(xAt(i), yAt(s.points[i]), dotRadius, dotPaint)
            }
        } else {
            // Sleep: filled dot at end only
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
        barPaint.alpha = 204  // 80%

        s.points.forEachIndexed { i, value ->
            if (value <= 0f) return@forEachIndexed  // Skip drawing bars for zero-value or missing days
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
}
