package sg.edu.nus.iss.wellness

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import retrofit2.HttpException
import sg.edu.nus.iss.wellness.api.ApiClient
import sg.edu.nus.iss.wellness.api.ApiService
import sg.edu.nus.iss.wellness.api.ChatRequest
import sg.edu.nus.iss.wellness.api.ChatResponse
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
import java.io.IOException
import java.time.LocalDate
import java.util.Calendar

/**
 * Authenticated app shell with a wellness dashboard, chatbot, recommendations, and profile.
 *
 * @author SA62 Team
 */
class HomeActivity : Activity() {
    private val scope = MainScope()
    private lateinit var tokenStore: TokenStore
    private lateinit var api: ApiService
    private lateinit var titleText: TextView
    private lateinit var content: LinearLayout
    private lateinit var recordsButton: Button
    private lateinit var chatButton: Button
    private lateinit var recommendationsButton: Button
    private lateinit var profileButton: Button

    private var activeFilter: DateRangeFilter? = null
    private var cachedRecords: List<WellnessRecordResponse> = emptyList()
    private var cachedRecommendations: List<RecommendationResponse> = emptyList()
    private var recordsSection: LinearLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tokenStore = TokenStore(this)
        if (tokenStore.token().isNullOrBlank()) {
            goToLogin()
            return
        }
        api = ApiClient.create(tokenStore)
        setContentView(R.layout.activity_home)
        EdgeToEdge.apply(this, findViewById(R.id.rootContainer))

        titleText = findViewById(R.id.titleText)
        content = findViewById(R.id.contentContainer)
        recordsButton = findViewById(R.id.recordsButton)
        chatButton = findViewById(R.id.chatButton)
        recommendationsButton = findViewById(R.id.recommendationsButton)
        profileButton = findViewById(R.id.profileButton)

        recordsButton.setOnClickListener { showDashboard() }
        chatButton.setOnClickListener { showChat() }
        recommendationsButton.setOnClickListener { showRecommendations() }
        profileButton.setOnClickListener { showProfile() }

        showDashboard()
    }

    // Wellness Dashboard

    private fun showDashboard() {
        selectTab(recordsButton)
        titleText.text = "Dashboard"
        reset()
        addStateBlock("Loading dashboard", "Fetching your latest wellness data.", "...")
        scope.launch {
            val records = runCatching { api.records() }.getOrElse { err ->
                if (err is HttpException && err.code() in listOf(401, 403)) {
                    tokenStore.clear()
                    goToLogin()
                } else {
                    showError("Could not load dashboard.", "Check that the backend is reachable from the emulator.")
                }
                return@launch
            }
            val recommendations = runCatching { api.recommendations() }.getOrDefault(emptyList())
            cachedRecords = records
            cachedRecommendations = recommendations
            renderDashboard(records, recommendations)
        }
    }

    private fun renderDashboard(
        records: List<WellnessRecordResponse>,
        recommendations: List<RecommendationResponse>
    ) {
        reset()
        val aggregates = DashboardDataHelper.aggregateByDate(records)
        val snapshot = DashboardDataHelper.buildDailySnapshot(aggregates)
        val summary = DashboardDataHelper.computeWeeklySummary(aggregates)

        // Today's snapshot tiles
        renderSnapshotTiles(snapshot)

        // Metric cards with trend sparklines and status badges
        val primaryColor = getColor(R.color.secondary)
        val greenColor = getColor(R.color.metric_green)
        val amberColor = getColor(R.color.metric_amber)

        val sleepSeries = DashboardDataHelper.buildSparklineSeries(aggregates, MetricType.SLEEP, primaryColor)
        val actSeries = DashboardDataHelper.buildSparklineSeries(aggregates, MetricType.ACTIVITY, greenColor)
        val moodSeries = DashboardDataHelper.buildSparklineSeries(aggregates, MetricType.MOOD, amberColor)

        content.addView(buildMetricCard("Sleep", "💤", summary, sleepSeries, summary.sleepBadge, MetricType.SLEEP))
        content.addView(buildMetricCard("Activity", "🏃", summary, actSeries, summary.activityBadge, MetricType.ACTIVITY))
        content.addView(buildMetricCard("Mood", "😊", summary, moodSeries, summary.moodBadge, MetricType.MOOD))

        // AI insight teaser
        val teaser = DashboardDataHelper.buildInsightTeaser(recommendations)
        renderInsightCard(teaser) { selectTab(recommendationsButton); showRecommendations() }

        // Records section with date-range filter
        val section = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        recordsSection = section
        content.addView(section)
        buildRecordsSection(section, records)
    }

    // Today's snapshot tiles showing the most recent day's values
    private fun renderSnapshotTiles(snapshot: DailySnapshot?) {
        if (snapshot == null) {
            content.addView(infoCard("No records yet", "Add your first wellness log below to get started."))
            return
        }

        val tilesRow = horizontal()
        tilesRow.layoutParams = (tilesRow.layoutParams as LinearLayout.LayoutParams)
            .apply { bottomMargin = dp(4) }

        tilesRow.addView(snapshotTile("💤", "${snapshot.sleepHours}h", "Sleep"))
        tilesRow.addView(snapshotTile("🏃", "${snapshot.exerciseMinutes}min", "Activity"))
        tilesRow.addView(snapshotTile("😊", "${snapshot.moodScore}/5", "Mood"))
        content.addView(tilesRow)

        if (!snapshot.isToday) {
            content.addView(caption("Showing ${snapshot.date}  ·  No entry today").centered()
                .apply { setPadding(0, 0, 0, dp(12)) })
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

    // Metric card with sparkline trend and status badge
    private fun buildMetricCard(
        metricTitle: String,
        icon: String,
        summary: WeeklyMetricSummary,
        series: SparklineDataSeries,
        badge: MetricBadge,
        metric: MetricType
    ): LinearLayout {
        val cardView = card()

        // Header: icon + title (left) · badge (right)
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

        // Weekly summary text
        cardView.addView(body(summaryLine(summary, metric)))

        // Sparkline or no-data placeholder
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

    // AI wellness insight teaser card
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
        content.addView(cardView)
    }

    // Records section with date-range filter
    private fun buildRecordsSection(section: LinearLayout, allRecords: List<WellnessRecordResponse>) {
        section.removeAllViews()

        // Header row: title · Filter · Add
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

        // Active filter chip
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

        // Record cards
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
        val section = recordsSection ?: return
        buildRecordsSection(section, cachedRecords)
    }

    private fun clearFilter() {
        activeFilter = null
        refreshRecordsList()
    }

    // Wellness Record Management (CRUD)

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
        reset()
        addStateBlock("Saving record", "Sending your wellness log to the backend.", "...")
        scope.launch {
            runCatching {
                if (id == null) api.createRecord(request) else api.updateRecord(id, request)
            }.onSuccess {
                showDashboard()
            }.onFailure {
                showError("Could not save record.", "Check fields and backend connection.")
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
        reset()
        addStateBlock("Deleting record", "Removing the selected wellness log.", "...")
        scope.launch {
            runCatching { api.deleteRecord(id) }
                .onSuccess { showDashboard() }
                .onFailure { showError("Could not delete record.", "Please retry after checking the backend connection.") }
        }
    }

    // RAG Chatbot

    private fun showChat() {
        selectTab(chatButton)
        titleText.text = "RAG Chatbot"
        reset()
        val input = input("Ask a wellness question", "")
        content.addView(input)
        addButton("Send", ButtonStyle.PRIMARY) {
            val text = input.text.toString().trim()
            if (text.isBlank()) {
                addStateBlock("Question required", "Enter a wellness question before sending.", "!")
            } else {
                sendChat(text)
            }
        }
        loadChatHistory()
    }

    private fun sendChat(question: String) {
        addStateBlock("Thinking", "Local RAG and Ollama may take a little while.", "AI")
        scope.launch {
            runCatching { api.sendChat(ChatRequest(question)) }
                .onSuccess { showChat() }
                .onFailure { showError(apiErrorMessage("Chatbot unavailable", it), "Keep your question and retry when services are running.") }
        }
    }

    private fun loadChatHistory() {
        scope.launch {
            runCatching { api.chatHistory() }
                .onSuccess { messages ->
                    if (messages.isEmpty()) {
                        addStateBlock("No chat yet", "Ask a wellness habit question to start a RAG-backed conversation.", "?")
                    } else {
                        messages.forEach(::addChatPair)
                    }
                }
                .onFailure { addStateBlock("Could not load chat history", "You can still retry after checking backend connectivity.", "!") }
        }
    }

    private fun addChatPair(message: ChatResponse) {
        content.addView(chatBubble("You", message.question, true))
        content.addView(chatBubble("Assistant", message.answer, false))
        val sources = message.sources.orEmpty()
        if (sources.isNotEmpty()) {
            content.addView(caption("Sources: ${sources.joinToString { it.title }}"))
        }
    }

    // Wellness Recommendations

    private fun showRecommendations() {
        selectTab(recommendationsButton)
        titleText.text = "Recommendations"
        reset()
        addStateBlock("Loading recommendations", "Fetching generated guidance from the backend.", "...")
        scope.launch {
            runCatching { api.recommendations() }
                .onSuccess { renderRecommendations(it) }
                .onFailure { showError("Could not load recommendations.", "Check backend, Python AI service, and Ollama status.") }
        }
    }

    private fun renderRecommendations(recommendations: List<RecommendationResponse>) {
        reset()
        addButton("Generate recommendation", ButtonStyle.PRIMARY) { generateRecommendation() }
        if (recommendations.isEmpty()) {
            addStateBlock("No recommendations yet", "Generate one after adding wellness records.", "+")
            return
        }
        recommendations.forEach { rec ->
            val recCard = card(fillColor = getColor(R.color.bg_subtle), stroke = getColor(R.color.bg_subtle))
            recCard.addView(title(rec.title, 16))
            recCard.addView(accent(rec.trendSummary))
            recCard.addView(body(rec.recommendationText))
            if (rec.actionItems.isNotEmpty()) {
                recCard.addView(caption("Actions"))
                rec.actionItems.forEach { recCard.addView(body("- $it")) }
            }
            rec.createdAt?.let { recCard.addView(caption("Generated $it")) }
            content.addView(recCard)
        }
    }

    private fun generateRecommendation() {
        reset()
        addStateBlock("Generating recommendation", "Local AI may take up to a minute. Duplicate submissions are disabled.", "AI")
        scope.launch {
            runCatching { api.generateRecommendation() }
                .onSuccess { showRecommendations() }
                .onFailure { showError(apiErrorMessage("Could not generate recommendation", it), "Do not pretend a recommendation was saved. Retry after services recover.") }
        }
    }

    // User Profile

    private fun showProfile() {
        selectTab(profileButton)
        titleText.text = "Profile"
        reset()
        val profileCard = card()
        profileCard.addView(title(tokenStore.displayName().orEmpty().ifBlank { "Wellness user" }, 22))
        profileCard.addView(body(tokenStore.email().orEmpty().ifBlank { "No email stored" }))
        profileCard.addView(caption("SA62 Wellness App | Version 1.0"))
        content.addView(profileCard)
        addButton("Logout", ButtonStyle.DESTRUCTIVE) {
            scope.launch {
                runCatching { api.logout() }
                tokenStore.clear()
                goToLogin()
            }
        }
    }

    // UI Helpers

    private fun reset() {
        content.removeAllViews()
        recordsSection = null
    }

    private fun showError(title: String, detail: String) {
        reset()
        addStateBlock(title, detail, "!", true)
    }

    private fun addStateBlock(title: String, detail: String, icon: String, error: Boolean = false) {
        val block = card(
            fillColor = if (error) Color.rgb(254, 242, 242) else getColor(R.color.bg_surface),
            stroke = if (error) Color.rgb(254, 202, 202) else getColor(R.color.border_default)
        )
        block.gravity = Gravity.CENTER_HORIZONTAL
        val mark = TextView(this).apply {
            text = icon
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(if (error) getColor(R.color.error) else getColor(R.color.primary), dp(32))
            layoutParams = LinearLayout.LayoutParams(dp(64), dp(64)).withBottomMargin(dp(10))
        }
        block.addView(mark)
        block.addView(title(title, 20).centered())
        block.addView(body(detail).centered())
        content.addView(block)
    }

    private fun infoCard(heading: String, detail: String): LinearLayout =
        card().apply {
            addView(accent(heading))
            addView(body(detail))
        }

    private fun addButton(text: String, style: ButtonStyle, onClick: () -> Unit) {
        content.addView(styledButton(text, style, ViewGroup.LayoutParams.MATCH_PARENT, dp(56), onClick))
    }

    private fun smallButton(text: String, style: ButtonStyle, onClick: () -> Unit): Button =
        styledButton(text, style, 0, dp(48), onClick).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f).withEndMargin(dp(8))
        }

    private fun headerIconButton(text: String, style: ButtonStyle, onClick: () -> Unit): Button =
        styledButton(text, style, ViewGroup.LayoutParams.WRAP_CONTENT, dp(40), onClick).apply {
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40))
                .withEndMargin(dp(6))
        }

    private fun styledButton(text: String, style: ButtonStyle, width: Int, height: Int, onClick: () -> Unit): Button =
        Button(this).apply {
            this.text = text
            isAllCaps = false
            textSize = if (height <= dp(48)) 14f else 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(
                when (style) {
                    ButtonStyle.PRIMARY, ButtonStyle.DESTRUCTIVE -> getColor(R.color.text_on_primary)
                    ButtonStyle.SECONDARY -> getColor(R.color.primary)
                }
            )
            backgroundTintList = null
            setBackgroundResource(
                when (style) {
                    ButtonStyle.PRIMARY -> R.drawable.bg_button_primary
                    ButtonStyle.SECONDARY -> R.drawable.bg_button_secondary
                    ButtonStyle.DESTRUCTIVE -> R.drawable.bg_button_destructive
                }
            )
            minHeight = dp(40)
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(width, height).withBottomMargin(dp(12))
        }

    private fun card(
        fillColor: Int = getColor(R.color.bg_surface),
        stroke: Int = getColor(R.color.border_default)
    ): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = rounded(fillColor, dp(16), stroke)
            elevation = dp(1).toFloat()
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .withBottomMargin(dp(14))
        }

    private fun chatBubble(label: String, message: String, user: Boolean): LinearLayout =
        card(
            fillColor = if (user) getColor(R.color.primary) else getColor(R.color.bg_surface),
            stroke = if (user) getColor(R.color.primary) else getColor(R.color.border_default)
        ).apply {
            addView(caption(label).apply { setTextColor(if (user) Color.WHITE else getColor(R.color.text_secondary)) })
            addView(body(message).apply { setTextColor(if (user) Color.WHITE else getColor(R.color.text_primary)) })
        }

    private fun pill(text: String): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(getColor(R.color.primary_dark))
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = rounded(getColor(R.color.bg_subtle), dp(16))
            layoutParams = LinearLayout.LayoutParams(0, dp(32), 1f).withEndMargin(dp(8))
        }

    private fun title(text: String, size: Int): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size.toFloat()
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(getColor(R.color.text_primary))
            setPadding(0, 0, 0, dp(6))
        }

    private fun accent(text: String): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(getColor(R.color.primary_dark))
            setPadding(0, 0, 0, dp(6))
        }

    private fun body(text: String): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(getColor(R.color.text_secondary))
            setLineSpacing(0f, 1.1f)
            setPadding(0, 0, 0, dp(6))
        }

    private fun caption(text: String): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 12f
            setTextColor(getColor(R.color.text_secondary))
            setPadding(0, dp(4), 0, dp(4))
        }

    private fun input(hint: String, value: String, inputType: Int = InputType.TYPE_CLASS_TEXT): EditText =
        EditText(this).apply {
            this.hint = hint
            setText(value)
            this.inputType = inputType
            textSize = 16f
            setTextColor(getColor(R.color.text_primary))
            setHintTextColor(getColor(R.color.text_secondary))
            setSingleLine(hint != "Notes")
            minHeight = dp(60)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = rounded(getColor(R.color.bg_surface), dp(12), getColor(R.color.border_default))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .withBottomMargin(dp(12))
        }

    private fun horizontal(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .withBottomMargin(dp(12))
        }

    private fun selectTab(selected: Button) {
        listOf(recordsButton, chatButton, recommendationsButton, profileButton).forEach { button ->
            button.backgroundTintList = null
            button.setBackgroundResource(if (button == selected) R.drawable.bg_button_secondary else android.R.color.transparent)
            button.setTextColor(if (button == selected) getColor(R.color.primary) else getColor(R.color.text_secondary))
            button.isAllCaps = false
        }
    }

    private fun rounded(color: Int, radius: Int, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius.toFloat()
            setColor(color)
            stroke?.let { setStroke(dp(1), it) }
        }

    private fun TextView.centered(): TextView = apply {
        gravity = Gravity.CENTER
        textAlignment = View.TEXT_ALIGNMENT_CENTER
    }

    private fun LinearLayout.LayoutParams.withBottomMargin(value: Int): LinearLayout.LayoutParams = apply {
        bottomMargin = value
    }

    private fun LinearLayout.LayoutParams.withEndMargin(value: Int): LinearLayout.LayoutParams = apply {
        marginEnd = value
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun apiErrorMessage(prefix: String, throwable: Throwable): String {
        return when (throwable) {
            is HttpException -> when (throwable.code()) {
                401, 403 -> "$prefix. Please log out and log in again."
                503 -> "$prefix. Check Python AI service and Ollama."
                else -> "$prefix. Backend returned HTTP ${throwable.code()}."
            }
            is IOException -> "$prefix. Check that the backend is reachable from the emulator."
            else -> "$prefix. ${throwable.javaClass.simpleName}."
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

    private enum class ButtonStyle {
        PRIMARY,
        SECONDARY,
        DESTRUCTIVE
    }
}
