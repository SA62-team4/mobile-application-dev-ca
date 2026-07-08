package sg.edu.nus.iss.wellness.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sg.edu.nus.iss.wellness.api.WellnessRecordResponse
import java.time.LocalDate

/**
 * Unit tests to verify the correctness of the DashboardDataHelper aggregation and filtering logic.
 * Decoupled from the Android framework to allow fast, local JVM testing.
 *
 * @author Jemilin Beulah Suria Christopher Raj
 */
class DashboardDataHelperTest {

    private fun record(
        id: Long = 1,
        date: String = LocalDate.now().toString(),
        sleep: Double = 7.0,
        weight: Double? = 65.0,
        exMin: Int = 30,
        mood: Int = 3,
        notes: String? = null
    ) = WellnessRecordResponse(
        id = id,
        recordDate = date,
        sleepHours = sleep,
        weightKg = weight,
        exerciseType = "Walking",
        exerciseMinutes = exMin,
        moodScore = mood,
        notes = notes,
        createdAt = null,
        updatedAt = null
    )

    // Averages sleep and mood, sums exercise minutes if multiple logs exist for the same day
    @Test
    fun `two records same date averages sleep and mood sums exercise`() {
        val today = LocalDate.now().toString()
        val records = listOf(
            record(id = 1, date = today, sleep = 6.0, exMin = 20, mood = 2),
            record(id = 2, date = today, sleep = 8.0, exMin = 40, mood = 4)
        )
        val aggregates = DashboardDataHelper.aggregateByDate(records)
        assertEquals(1, aggregates.size)
        val agg = aggregates.first()
        assertEquals(7.0, agg.sleepHours, 0.01)   // (6+8)/2 = 7.0
        assertEquals(65.0, agg.weightKg!!, 0.01)     // (65+65)/2 = 65.0
        assertEquals(60, agg.exerciseMinutes)       // 20+40
        assertEquals(3.0, agg.moodScore, 0.01)     // (2+4)/2 = 3.0
    }

    // Ensures multiple same-day records map to a single DayAggregate with averaged/summed values
    @Test
    fun `two records on same date produce one aggregate with correct values`() {
        val today = LocalDate.now().toString()
        val r1 = record(id = 1, date = today, sleep = 6.0, exMin = 10, mood = 2, notes = "Morning run")
        val r2 = record(id = 2, date = today, sleep = 8.0, exMin = 50, mood = 4, notes = "Felt tired")
        val aggregates = DashboardDataHelper.aggregateByDate(listOf(r1, r2))
        assertEquals(1, aggregates.size)
        val agg = aggregates.first()
        assertEquals(7.0, agg.sleepHours, 0.01)   // (6+8)/2
        assertEquals(65.0, agg.weightKg!!, 0.01)
        assertEquals(60, agg.exerciseMinutes)       // 10+50
        assertEquals(3.0, agg.moodScore, 0.01)     // (2+4)/2
    }

    @Test
    fun `same day weight logs are averaged for dashboard trend`() {
        val today = LocalDate.now().toString()
        val aggregates = DashboardDataHelper.aggregateByDate(
            listOf(
                record(id = 1, date = today, weight = 64.0),
                record(id = 2, date = today, weight = 66.0)
            )
        )
        assertEquals(1, aggregates.size)
        assertEquals(65.0, aggregates.first().weightKg!!, 0.01)
    }

    // Verifies malformed dates are skipped without failing the aggregation process
    @Test
    fun `record with unparseable date is silently excluded`() {
        val today = LocalDate.now().toString()
        val good = record(id = 1, date = today, sleep = 7.0)
        val bad = record(id = 2, date = "not-a-date", sleep = 5.0)
        val aggregates = DashboardDataHelper.aggregateByDate(listOf(good, bad))
        assertEquals(1, aggregates.size)
        assertEquals(7.0, aggregates.first().sleepHours, 0.01)
    }

    // Checks that floating-point values are correctly rounded for status badge thresholds
    @Test
    fun `sleep 6_99 rounds to 7_0 and earns GOOD badge not FAIR`() {
        val today = LocalDate.now()
        // Create records over the past 7 days with avg sleep of 6.99
        // Two records: 6.98 + 7.0 → avg = 6.99, rounds to 7.0
        val records = (0..6).map { offset ->
            record(
                id = offset.toLong(),
                date = today.minusDays(offset.toLong()).toString(),
                sleep = if (offset == 0) 6.93 else 7.0,
                exMin = 30,
                mood = 3
            )
        }
        // 6 days at 7.0 + 1 day at 6.93 = (6*7.0 + 6.93)/7 = 48.93/7 = 6.99
        val aggregates = DashboardDataHelper.aggregateByDate(records)
        val summary = DashboardDataHelper.computeWeeklySummary(aggregates)
        // The rounded avg should be 7.0, earning GOOD
        assertEquals(MetricBadge.GOOD, summary.sleepBadge)
    }

    // Handles empty input with null snapshot and NO_DATA badges
    @Test
    fun `empty input returns null snapshot and NO_DATA badges`() {
        val aggregates = DashboardDataHelper.aggregateByDate(emptyList())
        assertNull(DashboardDataHelper.buildDailySnapshot(aggregates))
        val summary = DashboardDataHelper.computeWeeklySummary(aggregates)
        assertEquals(MetricBadge.NO_DATA, summary.sleepBadge)
        assertEquals(MetricBadge.NO_DATA, summary.activityBadge)
        assertEquals(MetricBadge.NO_DATA, summary.moodBadge)
    }

    // Date-range filter includes boundary dates (inclusive)
    @Test
    fun `date range filter includes boundary dates`() {
        val from = LocalDate.now().minusDays(5)
        val to = LocalDate.now().minusDays(2)
        val records = listOf(
            record(id = 1, date = from.minusDays(1).toString()),  // before range — excluded
            record(id = 2, date = from.toString()),                 // exactly from — included
            record(id = 3, date = from.plusDays(1).toString()),     // inside — included
            record(id = 4, date = to.toString()),                   // exactly to — included
            record(id = 5, date = to.plusDays(1).toString())        // after range — excluded
        )
        val filter = DateRangeFilter(from = from, to = to)
        val filtered = DashboardDataHelper.applyDateFilter(records, filter)
        assertEquals(3, filtered.size)
        assertEquals(setOf(2L, 3L, 4L), filtered.map { it.id }.toSet())
    }

    // Boundary checks for status badges
    @Test
    fun `sleep exactly 7_0 earns GOOD badge`() {
        val rounded = DashboardDataHelper.round1dp(7.0)
        assertEquals(7.0, rounded, 0.001)
        // Directly verify threshold logic by building a weekly summary
        val today = LocalDate.now()
        val records = (0..6).map { offset ->
            record(id = offset.toLong(), date = today.minusDays(offset.toLong()).toString(), sleep = 7.0)
        }
        val summary = DashboardDataHelper.computeWeeklySummary(DashboardDataHelper.aggregateByDate(records))
        assertEquals(MetricBadge.GOOD, summary.sleepBadge)
    }

    @Test
    fun `zero exercise days earns BELOW_TARGET badge`() {
        val today = LocalDate.now()
        val records = (0..6).map { offset ->
            record(id = offset.toLong(), date = today.minusDays(offset.toLong()).toString(), exMin = 0)
        }
        val summary = DashboardDataHelper.computeWeeklySummary(DashboardDataHelper.aggregateByDate(records))
        assertEquals(MetricBadge.BELOW_TARGET, summary.activityBadge)
    }

    @Test
    fun `mood exactly 3_0 earns GOOD badge`() {
        val today = LocalDate.now()
        val records = (0..6).map { offset ->
            record(id = offset.toLong(), date = today.minusDays(offset.toLong()).toString(), mood = 3)
        }
        val summary = DashboardDataHelper.computeWeeklySummary(DashboardDataHelper.aggregateByDate(records))
        assertEquals(MetricBadge.GOOD, summary.moodBadge)
    }

    @Test
    fun `body metrics summary calculates bmi from latest weight and height`() {
        val today = LocalDate.now()
        val aggregates = DashboardDataHelper.aggregateByDate(
            listOf(
                record(id = 1, date = today.minusDays(1).toString(), weight = 64.0),
                record(id = 2, date = today.toString(), weight = 66.0)
            )
        )

        val summary = DashboardDataHelper.buildBodyMetricsSummary(aggregates, 170.0)

        assertNotNull(summary.latestWeightKg)
        assertEquals(66.0, summary.latestWeightKg!!, 0.01)
        assertEquals(170.0, summary.heightCm!!, 0.01)
        assertEquals(22.8, summary.bmi!!, 0.1)
    }

    // BMI sparkline plots one point per weighted day, derived from the constant height
    @Test
    fun `bmi sparkline series derives a point per weighted day from height`() {
        val today = LocalDate.now()
        val aggregates = DashboardDataHelper.aggregateByDate(
            listOf(
                record(id = 1, date = today.minusDays(1).toString(), weight = 64.0),
                record(id = 2, date = today.toString(), weight = 66.0)
            )
        )

        val series = DashboardDataHelper.buildBmiSparklineSeries(aggregates, 170.0, 0)

        assertEquals(2, series.points.size)
        assertEquals(22.1f, series.points[0], 0.1f)
        assertEquals(22.8f, series.points[1], 0.1f)
    }

    // Without a height there is no way to compute BMI, so the series stays empty
    @Test
    fun `bmi sparkline series is empty when height is missing`() {
        val today = LocalDate.now()
        val aggregates = DashboardDataHelper.aggregateByDate(
            listOf(record(id = 1, date = today.toString(), weight = 66.0))
        )

        val series = DashboardDataHelper.buildBmiSparklineSeries(aggregates, null, 0)

        assertTrue(series.points.isEmpty())
    }
}
