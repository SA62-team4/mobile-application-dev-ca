package sg.edu.nus.iss.wellness.dashboard

import sg.edu.nus.iss.wellness.api.RecommendationResponse
import sg.edu.nus.iss.wellness.api.WellnessRecordResponse
import java.time.LocalDate

/**
 * Utility for aggregating and parsing wellness record data for the dashboard.
 * Keeps business logic decoupled from Android UI components to facilitate unit testing.
 *
 * @author SA62 Team
 */

data class DailySnapshot(
    val date: LocalDate,
    val isToday: Boolean,
    val sleepHours: Double,
    val exerciseMinutes: Int,
    val exerciseType: String?,
    val moodScore: Int
)

data class DayAggregate(
    val date: LocalDate,
    val sleepHours: Double,
    val exerciseMinutes: Int,
    val moodScore: Double,
    val hasData: Boolean = true
)

data class WeeklyMetricSummary(
    val averageSleepHours: Double,
    val exerciseDays: Int,
    val totalExerciseMinutes: Int,
    val averageMoodScore: Double,
    val sleepBadge: MetricBadge,
    val activityBadge: MetricBadge,
    val moodBadge: MetricBadge,
    val recordCount: Int
)

enum class MetricBadge(val displayText: String, val colorHex: String) {
    EXCELLENT("Excellent", "#2E7D32"),
    GOOD("Good", "#00695C"),
    FAIR("Fair", "#E65100"),
    BELOW_TARGET("Below target", "#DC2626"),
    NO_DATA("No data", "#64748B")
}

enum class SparklineMode { LINE, BAR }

enum class MetricType { SLEEP, ACTIVITY, MOOD }

data class SparklineDataSeries(
    val points: List<Float>,
    val dates: List<String>,
    val mode: SparklineMode,
    val color: Int,
    val dotsAtAllPoints: Boolean = false
)

data class InsightTeaser(
    val title: String,
    val excerpt: String,
    val createdAt: String?
)

data class DateRangeFilter(
    val from: LocalDate,
    val to: LocalDate
)

object DashboardDataHelper {

    /**
     * Groups wellness records by calendar date. Skips records with invalid or malformed dates.
     * For each date, averages sleep duration and mood score, and sums exercise minutes.
     * Raw notes remain intact on individual record entities to allow detailed view.
     * Returns a list of daily aggregates sorted chronologically.
     */
    fun aggregateByDate(records: List<WellnessRecordResponse>): List<DayAggregate> {
        val grouped = records
            .mapNotNull { record ->
                val date = runCatching { LocalDate.parse(record.recordDate) }.getOrNull()
                if (date != null) date to record else null
            }
            .groupBy { it.first }

        return grouped.entries.map { (date, pairs) ->
            val recs = pairs.map { it.second }
            DayAggregate(
                date = date,
                sleepHours = round1dp(recs.map { it.sleepHours }.average()),
                exerciseMinutes = recs.sumOf { it.exerciseMinutes },
                moodScore = round1dp(recs.map { it.moodScore.toDouble() }.average())
            )
        }.sortedBy { it.date }
    }

    /**
     * Returns the most-recent day's data as a snapshot, or null when aggregates is empty.
     */
    fun buildDailySnapshot(aggregates: List<DayAggregate>): DailySnapshot? {
        val latest = aggregates.lastOrNull() ?: return null
        return DailySnapshot(
            date = latest.date,
            isToday = latest.date == LocalDate.now(),
            sleepHours = latest.sleepHours,
            exerciseMinutes = latest.exerciseMinutes,
            exerciseType = null,
            moodScore = latest.moodScore.toInt()
        )
    }

    /**
     * Computes a weekly summary for the past 7 calendar days (including today).
     * Averages and sums are rounded to avoid floating-point precision boundary issues when checking thresholds.
     */
    fun computeWeeklySummary(aggregates: List<DayAggregate>): WeeklyMetricSummary {
        val today = LocalDate.now()
        val weekAgo = today.minusDays(6)
        val week = aggregates.filter { !it.date.isBefore(weekAgo) && !it.date.isAfter(today) }

        if (week.isEmpty()) {
            return WeeklyMetricSummary(
                averageSleepHours = 0.0,
                exerciseDays = 0,
                totalExerciseMinutes = 0,
                averageMoodScore = 0.0,
                sleepBadge = MetricBadge.NO_DATA,
                activityBadge = MetricBadge.NO_DATA,
                moodBadge = MetricBadge.NO_DATA,
                recordCount = 0
            )
        }

        val avgSleep = round1dp(week.map { it.sleepHours }.average())
        val exerciseDays = week.count { it.exerciseMinutes > 0 }
        val totalMinutes = week.sumOf { it.exerciseMinutes }
        val avgMood = round1dp(week.map { it.moodScore }.average())

        return WeeklyMetricSummary(
            averageSleepHours = avgSleep,
            exerciseDays = exerciseDays,
            totalExerciseMinutes = totalMinutes,
            averageMoodScore = avgMood,
            sleepBadge = sleepBadge(avgSleep),
            activityBadge = activityBadge(exerciseDays),
            moodBadge = moodBadge(avgMood),
            recordCount = week.size
        )
    }

    /**
     * Prepares data points for trend sparklines over the past 7 days.
     * Only logged days are included; gaps are left empty rather than filled with zeroes.
     */
    fun buildSparklineSeries(
        aggregates: List<DayAggregate>,
        metricType: MetricType,
        color: Int
    ): SparklineDataSeries {
        val today = LocalDate.now()
        val weekAgo = today.minusDays(6)
        val week = aggregates.filter { !it.date.isBefore(weekAgo) && !it.date.isAfter(today) }

        val mode = if (metricType == MetricType.ACTIVITY) SparklineMode.BAR else SparklineMode.LINE
        val points = week.map { agg ->
            when (metricType) {
                MetricType.SLEEP -> agg.sleepHours.toFloat()
                MetricType.ACTIVITY -> agg.exerciseMinutes.toFloat()
                MetricType.MOOD -> agg.moodScore.toFloat()
            }
        }
        val dates = week.map { agg ->
            agg.date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
        }
        return SparklineDataSeries(
            points = points,
            dates = dates,
            mode = mode,
            color = color,
            dotsAtAllPoints = metricType == MetricType.MOOD
        )
    }

    /**
     * Filters records to those whose parsed date falls within filter.from..filter.to inclusive.
     * Returns all records when filter is null. Silently skips records with unparseable dates.
     */
    fun applyDateFilter(
        records: List<WellnessRecordResponse>,
        filter: DateRangeFilter?
    ): List<WellnessRecordResponse> {
        if (filter == null) return records
        return records.filter { record ->
            val date = runCatching { LocalDate.parse(record.recordDate) }.getOrNull()
                ?: return@filter false
            !date.isBefore(filter.from) && !date.isAfter(filter.to)
        }
    }

    /**
     * Returns an InsightTeaser from the most recent recommendation, or null if none exist.
     * Excerpt is capped at 120 characters (UI contracts).
     */
    fun buildInsightTeaser(recommendations: List<RecommendationResponse>): InsightTeaser? {
        val rec = recommendations.firstOrNull() ?: return null
        val text = rec.recommendationText
        val excerpt = if (text.length > 120) text.take(120) + "…" else text
        return InsightTeaser(title = rec.title, excerpt = excerpt, createdAt = rec.createdAt)
    }

    // Round to 1 decimal place to avoid floating-point precision issues
    fun round1dp(value: Double): Double = Math.round(value * 10) / 10.0

    private fun sleepBadge(avg: Double): MetricBadge = when {
        avg >= 8.0 -> MetricBadge.EXCELLENT
        avg >= 7.0 -> MetricBadge.GOOD
        avg >= 6.0 -> MetricBadge.FAIR
        else -> MetricBadge.BELOW_TARGET
    }

    private fun activityBadge(days: Int): MetricBadge = when {
        days >= 5 -> MetricBadge.EXCELLENT
        days >= 3 -> MetricBadge.GOOD
        days >= 1 -> MetricBadge.FAIR
        else -> MetricBadge.BELOW_TARGET
    }

    private fun moodBadge(avg: Double): MetricBadge = when {
        avg >= 4.0 -> MetricBadge.EXCELLENT
        avg >= 3.0 -> MetricBadge.GOOD
        avg >= 2.0 -> MetricBadge.FAIR
        else -> MetricBadge.BELOW_TARGET
    }
}
