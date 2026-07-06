package sg.edu.nus.iss.wellness

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import retrofit2.HttpException
import sg.edu.nus.iss.wellness.api.ApiClient
import sg.edu.nus.iss.wellness.api.ApiService
import sg.edu.nus.iss.wellness.api.RecommendationResponse
import sg.edu.nus.iss.wellness.api.WellnessRecordRequest
import sg.edu.nus.iss.wellness.api.WellnessRecordResponse
import sg.edu.nus.iss.wellness.dashboard.DailySnapshot
import sg.edu.nus.iss.wellness.dashboard.DashboardDataHelper
import sg.edu.nus.iss.wellness.dashboard.DateRangeFilter
import sg.edu.nus.iss.wellness.dashboard.InsightTeaser
import sg.edu.nus.iss.wellness.dashboard.MetricBadge
import sg.edu.nus.iss.wellness.dashboard.MetricType
import sg.edu.nus.iss.wellness.dashboard.SparklineDataSeries
import sg.edu.nus.iss.wellness.dashboard.SparklineView
import sg.edu.nus.iss.wellness.dashboard.WeeklyMetricSummary
import sg.edu.nus.iss.wellness.databinding.ActivityDashboardBinding
import sg.edu.nus.iss.wellness.ui.*
import java.time.LocalDate
import java.util.Calendar

/**
 * Wellness dashboard: today's snapshot, metric trends, AI insight teaser, and records history.
 *
 * @author SA62 Team
 */
class DashboardActivity : AppCompatActivity() {
    private val scope = MainScope()
    private lateinit var binding: ActivityDashboardBinding
    private lateinit var tokenStore: TokenStore
    private lateinit var api: ApiService

    private var activeFilter: DateRangeFilter? = null
    private var cachedRecords: List<WellnessRecordResponse> = emptyList()
    private var cachedRecommendations: List<RecommendationResponse> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tokenStore = TokenStore(this)
        if (tokenStore.token().isNullOrBlank()) {
            goToLogin()
            return
        }
        api = ApiClient.create(tokenStore)

        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdge.apply(this, binding.rootContainer)

        highlightTab(
            listOf(
                binding.bottomNav.dashboardButton,
                binding.bottomNav.chatButton,
                binding.bottomNav.recommendationsButton,
                binding.bottomNav.profileButton
            ),
            binding.bottomNav.dashboardButton
        )

        loadDashboard()
    }

    private fun resetDashboard() {
        binding.snapshotContainer.removeAllViews()
        binding.metricsContainer.removeAllViews()
        binding.insightContainer.removeAllViews()
        binding.recordsContainer.removeAllViews()
    }

    private fun loadDashboard() {
        resetDashboard()
        addStateBlock(binding.snapshotContainer, "Loading dashboard", "Fetching your latest wellness data.", "...")
        scope.launch {
            val records = runCatching { api.records() }.getOrElse { err ->
                if (err is HttpException && err.code() in listOf(401, 403)) {
                    tokenStore.clear()
                    goToLogin()
                } else {
                    resetDashboard()
                    showError(binding.snapshotContainer, "Could not load dashboard.", "Check that the backend is reachable from the emulator.")
                }
                return@launch
            }
            val recommendations = runCatching { api.recommendations() }.getOrDefault(emptyList())
            cachedRecords = records
            cachedRecommendations = recommendations
            renderDashboard(records, recommendations)
        }
    }

    private fun renderDashboard(records: List<WellnessRecordResponse>, recommendations: List<RecommendationResponse>) {
        resetDashboard()
        val aggregates = DashboardDataHelper.aggregateByDate(records)
        val snapshot = DashboardDataHelper.buildDailySnapshot(aggregates)
        val summary = DashboardDataHelper.computeWeeklySummary(aggregates)

        renderSnapshotTiles(snapshot)

        val primaryColor = getColor(R.color.secondary)
        val greenColor = getColor(R.color.metric_green)
        val amberColor = getColor(R.color.metric_amber)

        val sleepSeries = DashboardDataHelper.buildSparklineSeries(aggregates, MetricType.SLEEP, primaryColor)
        val actSeries = DashboardDataHelper.buildSparklineSeries(aggregates, MetricType.ACTIVITY, greenColor)
        val moodSeries = DashboardDataHelper.buildSparklineSeries(aggregates, MetricType.MOOD, amberColor)

        binding.metricsContainer.addView(buildMetricCard("Sleep", "💤", summary, sleepSeries, summary.sleepBadge, MetricType.SLEEP))
        binding.metricsContainer.addView(buildMetricCard("Activity", "🏃", summary, actSeries, summary.activityBadge, MetricType.ACTIVITY))
        binding.metricsContainer.addView(buildMetricCard("Mood", "😊", summary, moodSeries, summary.moodBadge, MetricType.MOOD))

        val teaser = DashboardDataHelper.buildInsightTeaser(recommendations)
        renderInsightCard(teaser) { /* T-701G: navigate to RecommendationsActivity */ }

        buildRecordsSection(binding.recordsContainer, records)
    }

    private fun renderSnapshotTiles(snapshot: DailySnapshot?) {
        if (snapshot == null) {
            binding.snapshotContainer.addView(infoCard("No records yet", "Add your first wellness log below to get started."))
            return
        }

        val tilesRow = horizontal()
        tilesRow.layoutParams = (tilesRow.layoutParams as LinearLayout.LayoutParams)
            .apply { bottomMargin = dp(4) }

        tilesRow.addView(snapshotTile("💤", "${snapshot.sleepHours}h", "Sleep"))
        tilesRow.addView(snapshotTile("🏃", "${snapshot.exerciseMinutes}min", "Activity"))
        tilesRow.addView(snapshotTile("😊", "${snapshot.moodScore}/5", "Mood"))
        binding.snapshotContainer.addView(tilesRow)

        if (!snapshot.isToday) {
            binding.snapshotContainer.addView(
                caption("Showing ${snapshot.date}  ·  No entry today").centered()
                    .apply { setPadding(0, 0, 0, dp(12)) }
            )
        }
    }

    private fun snapshotTile(icon: String, value: String, label: String): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(14), dp(8), dp(14))
            background = rounded(getColor(R.color.bg_surface), dp(12), getColor(R.color.border_default))
            elevation = dp(1).toFloat()
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .withEndMargin(dp(8))

            addView(TextView(context).apply {
                text = icon; textSize = 22f; gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            })
            addView(TextView(context).apply {
                text = value; textSize = 18f; typeface = Typeface.DEFAULT_BOLD
                setTextColor(getColor(R.color.text_primary)); gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            })
            addView(TextView(context).apply {
                text = label; textSize = 11f
                setTextColor(getColor(R.color.text_secondary)); gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            })
        }

    private fun buildMetricCard(
        metricTitle: String,
        icon: String,
        summary: WeeklyMetricSummary,
        series: SparklineDataSeries,
        badge: MetricBadge,
        metric: MetricType
    ): LinearLayout {
        val cardView = card()

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).withBottomMargin(dp(6))
        }
        header.addView(accent("$icon  $metricTitle").apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        header.addView(badgePill(badge))
        cardView.addView(header)

        cardView.addView(body(summaryLine(summary, metric)))

        if (series.points.isEmpty()) {
            cardView.addView(body("No data yet for the past 7 days."))
        } else {
            cardView.addView(SparklineView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).withBottomMargin(dp(4))
                setData(series)
            })
        }

        return cardView
    }

    private fun badgePill(badge: MetricBadge): TextView {
        val bgColor = when (badge) {
            MetricBadge.EXCELLENT -> getColor(R.color.badge_excellent)
            MetricBadge.GOOD -> getColor(R.color.badge_good)
            MetricBadge.FAIR -> getColor(R.color.badge_fair)
            MetricBadge.BELOW_TARGET -> getColor(R.color.error)
            MetricBadge.NO_DATA -> getColor(R.color.text_secondary)
        }
        return TextView(this).apply {
            text = badge.displayText
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = rounded(bgColor, dp(12))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    private fun summaryLine(summary: WeeklyMetricSummary, metric: MetricType): String = when (metric) {
        MetricType.SLEEP -> "Avg ${summary.averageSleepHours} h · ${summary.recordCount} days logged"
        MetricType.ACTIVITY -> "${summary.exerciseDays} active days · ${summary.totalExerciseMinutes} min total"
        MetricType.MOOD -> "Avg mood ${summary.averageMoodScore} / 5"
    }

    private fun renderInsightCard(teaser: InsightTeaser?, onTap: () -> Unit) {
        val cardView = card(fillColor = getColor(R.color.bg_subtle), stroke = getColor(R.color.bg_subtle))
        cardView.setOnClickListener { onTap() }

        if (teaser == null) {
            cardView.addView(accent("💡 Insights"))
            cardView.addView(body("No recommendations yet. Tap to generate →"))
        } else {
            cardView.addView(accent("💡 Latest Insight"))
            cardView.addView(title(teaser.title, 15))
            cardView.addView(body(teaser.excerpt))
            teaser.createdAt?.let { cardView.addView(caption("Generated $it")) }
            cardView.addView(caption("View details →"))
        }
        binding.insightContainer.addView(cardView)
    }

    private fun buildRecordsSection(section: LinearLayout, allRecords: List<WellnessRecordResponse>) {
        section.removeAllViews()

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).withBottomMargin(dp(10))
        }
        header.addView(title("Recent Records", 16).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(0, 0, 0, 0)
        })
        header.addView(headerIconButton("📅 Filter", ButtonStyle.SECONDARY) { showDateRangeFilterDialog() })
        header.addView(headerIconButton("+ Add", ButtonStyle.PRIMARY) { openRecordDialog(null) })
        section.addView(header)

        activeFilter?.let { filter ->
            val chip = TextView(this).apply {
                text = "Filtered: ${filter.from} – ${filter.to}  ✕"
                textSize = 13f
                setTextColor(getColor(R.color.primary_dark))
                setPadding(dp(12), dp(6), dp(12), dp(6))
                background = rounded(getColor(R.color.bg_subtle), dp(16), getColor(R.color.primary))
                setOnClickListener { clearFilter() }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).withBottomMargin(dp(10))
            }
            section.addView(chip)
        }

        val filtered = DashboardDataHelper.applyDateFilter(allRecords, activeFilter)
        when {
            allRecords.isEmpty() -> {
                section.addView(infoCard("No records yet", "Add your first wellness log to start seeing history."))
            }
            filtered.isEmpty() -> {
                section.addView(body("No records in this date range."))
                section.addView(headerIconButton("Clear filter", ButtonStyle.SECONDARY) { clearFilter() }.apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)
                    ).withBottomMargin(dp(8))
                })
            }
            else -> filtered.forEach { record ->
                val recordCard = card()
                recordCard.addView(title(record.recordDate, 16))
                recordCard.addView(accent("Sleep ${record.sleepHours}h | ${record.exerciseType ?: "No exercise"} ${record.exerciseMinutes}min | Mood ${record.moodScore}/5"))
                recordCard.addView(body(record.notes.orEmpty().ifBlank { "No notes added." }))
                val actions = horizontal()
                actions.addView(smallButton("Edit", ButtonStyle.SECONDARY) { openRecordDialog(record) })
                actions.addView(smallButton("Delete", ButtonStyle.DESTRUCTIVE) { confirmDelete(record.id) })
                recordCard.addView(actions)
                section.addView(recordCard)
            }
        }
    }

    private fun showDateRangeFilterDialog() {
        val today = LocalDate.now()
        val startDefault = today.minusDays(7)
        DatePickerDialog(
            this,
            { _, y1, m1, d1 ->
                val startDate = LocalDate.of(y1, m1 + 1, d1)
                val endCal = Calendar.getInstance()
                DatePickerDialog(
                    this,
                    { _, y2, m2, d2 ->
                        val endDate = LocalDate.of(y2, m2 + 1, d2)
                        if (endDate.isBefore(startDate)) {
                            Toast.makeText(this, "End date must be after start date", Toast.LENGTH_SHORT).show()
                        } else {
                            activeFilter = DateRangeFilter(startDate, endDate)
                            refreshRecordsList()
                        }
                    },
                    endCal.get(Calendar.YEAR),
                    endCal.get(Calendar.MONTH),
                    endCal.get(Calendar.DAY_OF_MONTH)
                ).show()
            },
            startDefault.year,
            startDefault.monthValue - 1,
            startDefault.dayOfMonth
        ).show()
    }

    private fun refreshRecordsList() {
        buildRecordsSection(binding.recordsContainer, cachedRecords)
    }

    private fun clearFilter() {
        activeFilter = null
        refreshRecordsList()
    }

    private fun openRecordDialog(record: WellnessRecordResponse?) {
        val view = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val date = input("Date YYYY-MM-DD", record?.recordDate ?: LocalDate.now().toString(), InputType.TYPE_CLASS_DATETIME)
        val sleep = input("Sleep hours", record?.sleepHours?.toString() ?: "7.0", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        val exerciseType = input("Exercise type", record?.exerciseType ?: "Walking")
        val exerciseMinutes = input("Exercise minutes", record?.exerciseMinutes?.toString() ?: "20", InputType.TYPE_CLASS_NUMBER)
        val mood = input("Mood score 1-5", record?.moodScore?.toString() ?: "3", InputType.TYPE_CLASS_NUMBER)
        val notes = input("Notes", record?.notes ?: "")
        listOf(date, sleep, exerciseType, exerciseMinutes, mood, notes).forEach(view::addView)

        AlertDialog.Builder(this)
            .setTitle(if (record == null) "Add record" else "Edit record")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                val request = WellnessRecordRequest(
                    recordDate = date.text.toString(),
                    sleepHours = sleep.text.toString().toDoubleOrNull() ?: 0.0,
                    exerciseType = exerciseType.text.toString(),
                    exerciseMinutes = exerciseMinutes.text.toString().toIntOrNull() ?: 0,
                    moodScore = mood.text.toString().toIntOrNull() ?: 3,
                    notes = notes.text.toString()
                )
                saveRecord(record?.id, request)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveRecord(id: Long?, request: WellnessRecordRequest) {
        resetDashboard()
        addStateBlock(binding.snapshotContainer, "Saving record", "Sending your wellness log to the backend.", "...")
        scope.launch {
            runCatching {
                if (id == null) api.createRecord(request) else api.updateRecord(id, request)
            }.onSuccess {
                loadDashboard()
            }.onFailure {
                resetDashboard()
                showError(binding.snapshotContainer, "Could not save record.", "Check fields and backend connection.")
            }
        }
    }

    private fun confirmDelete(id: Long) {
        AlertDialog.Builder(this)
            .setTitle("Delete record?")
            .setMessage("This removes the wellness log after backend confirmation.")
            .setPositiveButton("Delete") { _, _ -> deleteRecord(id) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteRecord(id: Long) {
        resetDashboard()
        addStateBlock(binding.snapshotContainer, "Deleting record", "Removing the selected wellness log.", "...")
        scope.launch {
            runCatching { api.deleteRecord(id) }
                .onSuccess { loadDashboard() }
                .onFailure {
                    resetDashboard()
                    showError(binding.snapshotContainer, "Could not delete record.", "Please retry after checking the backend connection.")
                }
        }
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
