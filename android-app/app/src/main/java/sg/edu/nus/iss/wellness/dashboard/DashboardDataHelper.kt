package sg.edu.nus.iss.wellness.dashboard

import sg.edu.nus.iss.wellness.api.RecommendationResponse
import sg.edu.nus.iss.wellness.api.WellnessRecordResponse
import java.time.LocalDate

/**
 * Dashboard aggregations.
 *
 * @author Jemilin Beulah Suria Christopher Raj
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
    val weightKg: Double?,
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

enum class MetricType { SLEEP, ACTIVITY, MOOD, WEIGHT }

data class BodyMetricsSummary(
    val latestWeightKg: Double?,
    val latestWeightDate: LocalDate?,
    val heightCm: Double?,
    val bmi: Double?
)

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

    /** Aggregates valid records by date, sorted oldest to newest. */
    fun aggregateByDate(records: List<WellnessRecordResponse>): List<DayAggregate> {
        val grouped = records
            .mapNotNull { record ->
                val date = runCatching { LocalDate.parse(record.recordDate) }.getOrNull()
                if (date != null) date to record else null
            }
            .groupBy { it.first }

        return grouped.entries.map { (date, pairs) ->
            val recs = pairs.map { it.second }
            val weights = recs.mapNotNull { it.weightKg }
            DayAggregate(
                date = date,
                sleepHours = round1dp(recs.map { it.sleepHours }.average()),
                weightKg = weights.takeIf { it.isNotEmpty() }?.let { round1dp(it.average()) },
                exerciseMinutes = recs.sumOf { it.exerciseMinutes },
                moodScore = round1dp(recs.map { it.moodScore.toDouble() }.average())
            )
        }.sortedBy { it.date }
    }

    /** Returns the latest aggregate as a snapshot. */
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

    /** Computes the latest 7-day summary. */
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

    /** Builds 7-day sparkline points, leaving missing days empty. */
    fun buildSparklineSeries(
        aggregates: List<DayAggregate>,
        metricType: MetricType,
        color: Int
    ): SparklineDataSeries {
        val today = LocalDate.now()
        val weekAgo = today.minusDays(6)
        val week = aggregates.filter { !it.date.isBefore(weekAgo) && !it.date.isAfter(today) }

        val mode = if (metricType == MetricType.ACTIVITY) SparklineMode.BAR else SparklineMode.LINE
        val plotted = if (metricType == MetricType.WEIGHT) {
            week.mapNotNull { agg -> agg.weightKg?.let { agg.date to it } }
        } else {
            week.map { agg ->
                val value = when (metricType) {
                    MetricType.SLEEP -> agg.sleepHours
                    MetricType.ACTIVITY -> agg.exerciseMinutes.toDouble()
                    MetricType.MOOD -> agg.moodScore
                    MetricType.WEIGHT -> error("Weight is handled above")
                }
                agg.date to value
            }
        }
        val points = plotted.map { it.second.toFloat() }
        val dates = plotted.map { agg ->
            agg.first.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
        }
        return SparklineDataSeries(
            points = points,
            dates = dates,
            mode = mode,
            color = color,
            dotsAtAllPoints = metricType == MetricType.MOOD || metricType == MetricType.WEIGHT
        )
    }

    /** Summarises the latest weight and derived BMI from the profile height. */
    fun buildBodyMetricsSummary(aggregates: List<DayAggregate>, heightCm: Double?): BodyMetricsSummary {
        val latest = aggregates.lastOrNull()
        val latestWeight = latest?.weightKg
        val bmi = if (latestWeight != null && heightCm != null && heightCm > 0.0) {
            val heightM = heightCm / 100.0
            round1dp(latestWeight / (heightM * heightM))
        } else {
            null
        }
        return BodyMetricsSummary(
            latestWeightKg = latestWeight,
            latestWeightDate = latest?.date,
            heightCm = heightCm,
            bmi = bmi
        )
    }

    /** Applies an inclusive date range, skipping invalid dates. */
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

    /** Builds a short teaser from the latest recommendation. */
    fun buildInsightTeaser(recommendations: List<RecommendationResponse>): InsightTeaser? {
        val rec = recommendations.firstOrNull() ?: return null
        val text = rec.recommendationText
        val excerpt = if (text.length > 120) text.take(120) + "…" else text
        return InsightTeaser(title = rec.title, excerpt = excerpt, createdAt = rec.createdAt)
    }

    // Avoid threshold drift from floating-point precision.
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
