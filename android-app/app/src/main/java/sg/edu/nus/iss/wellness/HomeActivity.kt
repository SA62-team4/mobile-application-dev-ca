package sg.edu.nus.iss.wellness

import android.app.Activity
import android.app.AlertDialog
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
import java.io.IOException
import java.time.LocalDate

/**
 * Authenticated app shell for records, chatbot, recommendations, and profile.
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

        recordsButton.setOnClickListener { showRecords() }
        chatButton.setOnClickListener { showChat() }
        recommendationsButton.setOnClickListener { showRecommendations() }
        profileButton.setOnClickListener { showProfile() }

        showRecords()
    }

    private fun showRecords() {
        selectTab(recordsButton)
        titleText.text = "Wellness Records"
        reset()
        addStateBlock("Loading records", "Fetching your latest wellness logs from the backend.", "...")
        scope.launch {
            runCatching { api.records() }
                .onSuccess { records -> renderRecords(records) }
                .onFailure { showError("Could not load records.", "Check that the backend is reachable from the emulator.") }
        }
    }

    private fun renderRecords(records: List<WellnessRecordResponse>) {
        reset()
        addButton("Add record", ButtonStyle.PRIMARY) { openRecordDialog(null) }
        if (records.isEmpty()) {
            addStateBlock("No records yet", "Add your first wellness log to start seeing history and recommendations.", "+")
            return
        }

        val latest = records.first()
        val metrics = horizontal()
        metrics.addView(pill("Sleep ${latest.sleepHours}h"))
        metrics.addView(pill("${latest.exerciseType ?: "Exercise"} ${latest.exerciseMinutes}m"))
        metrics.addView(pill("Mood ${latest.moodScore}/5"))
        content.addView(metrics)

        records.forEach { record ->
            val card = card()
            card.addView(title("${record.recordDate}", 16))
            card.addView(accent("Sleep ${record.sleepHours}h | ${record.exerciseType ?: "No exercise"} ${record.exerciseMinutes}min | Mood ${record.moodScore}/5"))
            card.addView(body(record.notes.orEmpty().ifBlank { "No notes added." }))
            val actions = horizontal()
            actions.addView(smallButton("Edit", ButtonStyle.SECONDARY) { openRecordDialog(record) })
            actions.addView(smallButton("Delete", ButtonStyle.DESTRUCTIVE) { confirmDelete(record.id) })
            card.addView(actions)
            content.addView(card)
        }
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
        reset()
        addStateBlock("Saving record", "Sending your wellness log to the backend.", "...")
        scope.launch {
            runCatching {
                if (id == null) api.createRecord(request) else api.updateRecord(id, request)
            }.onSuccess {
                showRecords()
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
                .onSuccess { showRecords() }
                .onFailure { showError("Could not delete record.", "Please retry after checking the backend connection.") }
        }
    }

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
            val card = card(fillColor = getColor(R.color.bg_subtle), stroke = getColor(R.color.bg_subtle))
            card.addView(title(rec.title, 16))
            card.addView(accent(rec.trendSummary))
            card.addView(body(rec.recommendationText))
            if (rec.actionItems.isNotEmpty()) {
                card.addView(caption("Actions"))
                rec.actionItems.forEach { card.addView(body("- $it")) }
            }
            rec.createdAt?.let { card.addView(caption("Generated $it")) }
            content.addView(card)
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

    private fun showProfile() {
        selectTab(profileButton)
        titleText.text = "Profile"
        reset()
        val card = card()
        card.addView(title(tokenStore.displayName().orEmpty().ifBlank { "Wellness user" }, 22))
        card.addView(body(tokenStore.email().orEmpty().ifBlank { "No email stored" }))
        card.addView(caption("SA62 Wellness App | Version 1.0"))
        content.addView(card)
        addButton("Logout", ButtonStyle.DESTRUCTIVE) {
            scope.launch {
                runCatching { api.logout() }
                tokenStore.clear()
                goToLogin()
            }
        }
    }

    private fun reset() {
        content.removeAllViews()
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

    private fun addButton(text: String, style: ButtonStyle, onClick: () -> Unit) {
        content.addView(styledButton(text, style, ViewGroup.LayoutParams.MATCH_PARENT, dp(56), onClick))
    }

    private fun smallButton(text: String, style: ButtonStyle, onClick: () -> Unit): Button =
        styledButton(text, style, 0, dp(48), onClick).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f).withEndMargin(dp(8))
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
            minHeight = dp(48)
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
