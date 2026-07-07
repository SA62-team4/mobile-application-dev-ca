package sg.edu.nus.iss.wellness

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import retrofit2.HttpException
import sg.edu.nus.iss.wellness.adapter.RecordsAdapter
import sg.edu.nus.iss.wellness.api.ApiClient
import sg.edu.nus.iss.wellness.api.ApiService
import sg.edu.nus.iss.wellness.api.RecommendationResponse
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
 * @author Abu Bakar Nasir
 */
class DashboardActivity : AppCompatActivity() {
    private val scope = MainScope()
    private lateinit var binding: ActivityDashboardBinding
    private lateinit var tokenStore: TokenStore
    private lateinit var api: ApiService

    private lateinit var snapshotContainer: LinearLayout
    private lateinit var metricsContainer: LinearLayout
    private lateinit var insightContainer: LinearLayout
    private lateinit var filterChip: TextView
    private lateinit var recordsEmptyContainer: LinearLayout

    private var activeFilter: DateRangeFilter? = null
    private var cachedRecords: List<WellnessRecordResponse> = emptyList()
    private var cachedRecommendations: List<RecommendationResponse> = emptyList()

    private val recordFormLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            loadDashboard()
        }
    }

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
        wireBottomNav(binding.bottomNav, DashboardActivity::class.java)

        val snapshotHeader = layoutInflater.inflate(R.layout.header_dashboard_snapshot, binding.dashboardListView, false)
        snapshotContainer = snapshotHeader.findViewById(R.id.snapshotContainer)
        binding.dashboardListView.addHeaderView(snapshotHeader)

        val metricsHeader = layoutInflater.inflate(R.layout.header_dashboard_metrics, binding.dashboardListView, false)
        metricsContainer = metricsHeader.findViewById(R.id.metricsContainer)
        binding.dashboardListView.addHeaderView(metricsHeader)

        val insightHeader = layoutInflater.inflate(R.layout.header_dashboard_insight, binding.dashboardListView, false)
        insightContainer = insightHeader.findViewById(R.id.insightContainer)
        binding.dashboardListView.addHeaderView(insightHeader)

        val recordsHeader = layoutInflater.inflate(R.layout.header_dashboard_records, binding.dashboardListView, false)
        filterChip = recordsHeader.findViewById(R.id.filterChip)
        recordsEmptyContainer = recordsHeader.findViewById(R.id.recordsEmptyContainer)
        recordsHeader.findViewById<Button>(R.id.filterButton).setOnClickListener { showDateRangeFilterDialog() }
        recordsHeader.findViewById<Button>(R.id.addRecordButton).setOnClickListener { openRecordForm(null) }
        filterChip.setOnClickListener { clearFilter() }
        binding.dashboardListView.addHeaderView(recordsHeader)

        binding.dashboardListView.adapter = RecordsAdapter(this, emptyList(), ::openRecordForm) { confirmDelete(it.id) }

        loadDashboard()
    }

    private fun showLoading(title: String, detail: String) {
        binding.dashboardListView.visibility = View.GONE
        binding.emptyStateContainer.visibility = View.VISIBLE
        binding.emptyStateContainer.removeAllViews()
        addStateBlock(binding.emptyStateContainer, title, detail, "...")
    }

    private fun showFetchError(title: String, detail: String) {
        binding.dashboardListView.visibility = View.GONE
        binding.emptyStateContainer.visibility = View.VISIBLE
        binding.emptyStateContainer.removeAllViews()
        showError(binding.emptyStateContainer, title, detail)
    }

    private fun showContent() {
        binding.emptyStateContainer.visibility = View.GONE
        binding.emptyStateContainer.removeAllViews()
        binding.dashboardListView.visibility = View.VISIBLE
    }

    private fun loadDashboard() {
        showLoading("Loading dashboard", "Fetching your latest wellness data.")
        scope.launch {
            val records = runCatching { api.records() }.getOrElse { err ->
                if (err is HttpException && err.code() in listOf(401, 403)) {
                    tokenStore.clear()
                    goToLogin()
                } else {
                    showFetchError("Could not load dashboard.", "Check that the backend is reachable from the emulator.")
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
        showContent()
        snapshotContainer.removeAllViews()
        metricsContainer.removeAllViews()
        insightContainer.removeAllViews()

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

        metricsContainer.addView(buildMetricCard("Sleep", "💤", summary, sleepSeries, summary.sleepBadge, MetricType.SLEEP))
        metricsContainer.addView(buildMetricCard("Activity", "🏃", summary, actSeries, summary.activityBadge, MetricType.ACTIVITY))
        metricsContainer.addView(buildMetricCard("Mood", "😊", summary, moodSeries, summary.moodBadge, MetricType.MOOD))

        val teaser = DashboardDataHelper.buildInsightTeaser(recommendations)
        renderInsightCard(teaser) {
            startActivity(Intent(this, RecommendationsActivity::class.java))
            finish()
        }

        renderRecordsList(records)
    }

    private fun renderSnapshotTiles(snapshot: DailySnapshot?) {
        if (snapshot == null) {
            snapshotContainer.addView(infoCard("No records yet", "Add your first wellness log below to get started."))
            return
        }

        val tilesRow = horizontal()
        tilesRow.layoutParams = (tilesRow.layoutParams as LinearLayout.LayoutParams)
            .apply { bottomMargin = dp(4) }

        tilesRow.addView(snapshotTile("💤", "${snapshot.sleepHours}h", "Sleep"))
        tilesRow.addView(snapshotTile("🏃", "${snapshot.exerciseMinutes}min", "Activity"))
        tilesRow.addView(snapshotTile("😊", "${snapshot.moodScore}/5", "Mood"))
        snapshotContainer.addView(tilesRow)

        if (!snapshot.isToday) {
            snapshotContainer.addView(
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
        insightContainer.addView(cardView)
    }

    private fun renderRecordsList(allRecords: List<WellnessRecordResponse>) {
        recordsEmptyContainer.removeAllViews()

        val filter = activeFilter
        if (filter != null) {
            filterChip.visibility = View.VISIBLE
            filterChip.text = "Filtered: ${filter.from} – ${filter.to}  ✕"
        } else {
            filterChip.visibility = View.GONE
        }

        val filtered = DashboardDataHelper.applyDateFilter(allRecords, activeFilter)
        when {
            allRecords.isEmpty() -> {
                recordsEmptyContainer.addView(infoCard("No records yet", "Add your first wellness log to start seeing history."))
            }
            filtered.isEmpty() -> {
                recordsEmptyContainer.addView(body("No records in this date range."))
                recordsEmptyContainer.addView(headerIconButton("Clear filter", ButtonStyle.SECONDARY) { clearFilter() })
            }
            else -> Unit
        }

        binding.dashboardListView.adapter = RecordsAdapter(this, filtered, ::openRecordForm) { confirmDelete(it.id) }
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
        renderRecordsList(cachedRecords)
    }

    private fun clearFilter() {
        activeFilter = null
        refreshRecordsList()
    }

    private fun openRecordForm(record: WellnessRecordResponse?) {
        val intent = Intent(this, RecordFormActivity::class.java)
        if (record != null) {
            intent.putExtra(Constants.EXTRA_RECORD_ID, record.id)
            intent.putExtra(Constants.EXTRA_RECORD_DATE, record.recordDate)
            intent.putExtra(Constants.EXTRA_RECORD_SLEEP_HOURS, record.sleepHours)
            intent.putExtra(Constants.EXTRA_RECORD_EXERCISE_TYPE, record.exerciseType)
            intent.putExtra(Constants.EXTRA_RECORD_EXERCISE_MINUTES, record.exerciseMinutes)
            intent.putExtra(Constants.EXTRA_RECORD_MOOD_SCORE, record.moodScore)
            intent.putExtra(Constants.EXTRA_RECORD_NOTES, record.notes)
        }
        recordFormLauncher.launch(intent)
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
        showLoading("Deleting record", "Removing the selected wellness log.")
        scope.launch {
            runCatching { api.deleteRecord(id) }
                .onSuccess { loadDashboard() }
                .onFailure {
                    showFetchError("Could not delete record.", "Please retry after checking the backend connection.")
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
