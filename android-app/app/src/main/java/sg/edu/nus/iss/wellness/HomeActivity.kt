package sg.edu.nus.iss.wellness

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.*
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import retrofit2.HttpException
import sg.edu.nus.iss.wellness.api.*
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

        findViewById<Button>(R.id.recordsButton).setOnClickListener { showRecords() }
        findViewById<Button>(R.id.chatButton).setOnClickListener { showChat() }
        findViewById<Button>(R.id.recommendationsButton).setOnClickListener { showRecommendations() }
        findViewById<Button>(R.id.profileButton).setOnClickListener { showProfile() }

        showRecords()
    }

    private fun showRecords() {
        titleText.text = "Wellness Records"
        reset("Loading records...")
        scope.launch {
            runCatching { api.records() }
                .onSuccess { records -> renderRecords(records) }
                .onFailure { showMessage("Could not load records.") }
        }
    }

    private fun renderRecords(records: List<WellnessRecordResponse>) {
        reset()
        addButton("Add record") { openRecordDialog(null) }
        if (records.isEmpty()) {
            addText("No records yet. Add your first wellness log.")
            return
        }
        records.forEach { record ->
            addText(
                "${record.recordDate}\nSleep: ${record.sleepHours}h | Exercise: ${record.exerciseType ?: "None"} ${record.exerciseMinutes}min | Mood: ${record.moodScore}/5\n${record.notes.orEmpty()}"
            )
            val row = horizontal()
            row.addView(button("Edit") { openRecordDialog(record) })
            row.addView(button("Delete") { deleteRecord(record.id) })
            content.addView(row)
        }
    }

    private fun openRecordDialog(record: WellnessRecordResponse?) {
        val view = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 0)
        }
        val date = input("Date YYYY-MM-DD", record?.recordDate ?: LocalDate.now().toString())
        val sleep = input("Sleep hours", record?.sleepHours?.toString() ?: "7.0")
        val exerciseType = input("Exercise type", record?.exerciseType ?: "Walking")
        val exerciseMinutes = input("Exercise minutes", record?.exerciseMinutes?.toString() ?: "20")
        val mood = input("Mood score 1-5", record?.moodScore?.toString() ?: "3")
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
        scope.launch {
            runCatching {
                if (id == null) api.createRecord(request) else api.updateRecord(id, request)
            }.onSuccess {
                showRecords()
            }.onFailure {
                showMessage("Could not save record. Check fields and backend connection.")
            }
        }
    }

    private fun deleteRecord(id: Long) {
        scope.launch {
            runCatching { api.deleteRecord(id) }
                .onSuccess { showRecords() }
                .onFailure { showMessage("Could not delete record.") }
        }
    }

    private fun showChat() {
        titleText.text = "RAG Chatbot"
        reset()
        val question = input("Ask a wellness question", "")
        addView(question)
        addButton("Send") {
            val text = question.text.toString().trim()
            if (text.isBlank()) {
                showMessage("Enter a question first.")
            } else {
                sendChat(text)
            }
        }
        loadChatHistory()
    }

    private fun sendChat(question: String) {
        addText("Thinking...")
        scope.launch {
            runCatching { api.sendChat(ChatRequest(question)) }
                .onSuccess { showChat() }
                .onFailure { showMessage(apiErrorMessage("Chatbot unavailable", it)) }
        }
    }

    private fun loadChatHistory() {
        scope.launch {
            runCatching { api.chatHistory() }
                .onSuccess { messages ->
                    messages.forEach { msg ->
                        addText("You: ${msg.question}\nAssistant: ${msg.answer}")
                    }
                }
                .onFailure { addText("Could not load chat history.") }
        }
    }

    private fun showRecommendations() {
        titleText.text = "Recommendations"
        reset("Loading recommendations...")
        scope.launch {
            runCatching { api.recommendations() }
                .onSuccess { renderRecommendations(it) }
                .onFailure { showMessage("Could not load recommendations.") }
        }
    }

    private fun renderRecommendations(recommendations: List<RecommendationResponse>) {
        reset()
        addButton("Generate recommendation") { generateRecommendation() }
        if (recommendations.isEmpty()) {
            addText("No recommendations yet. Generate one after adding records.")
            return
        }
        recommendations.forEach { rec ->
            addText(
                "${rec.title}\n${rec.trendSummary}\n\n${rec.recommendationText}\n\nActions:\n" +
                        rec.actionItems.joinToString("\n") { "- $it" }
            )
        }
    }

    private fun generateRecommendation() {
        reset("Generating recommendation. Local AI can take up to a minute...")
        scope.launch {
            runCatching { api.generateRecommendation() }
                .onSuccess { showRecommendations() }
                .onFailure { showMessage(apiErrorMessage("Could not generate recommendation", it)) }
        }
    }

    private fun showProfile() {
        titleText.text = "Profile"
        reset()
        addText("${tokenStore.displayName()}\n${tokenStore.email()}")
        addButton("Logout") {
            scope.launch {
                runCatching { api.logout() }
                tokenStore.clear()
                goToLogin()
            }
        }
    }

    private fun reset(message: String? = null) {
        content.removeAllViews()
        message?.let { addText(it) }
    }

    private fun addText(text: String) {
        content.addView(TextView(this).apply {
            this.text = text
            textSize = 16f
            setPadding(0, 12, 0, 12)
        })
    }

    private fun addButton(text: String, onClick: () -> Unit) {
        content.addView(button(text, onClick))
    }

    private fun button(text: String, onClick: () -> Unit): Button =
        Button(this).apply {
            this.text = text
            setOnClickListener { onClick() }
        }

    private fun input(hint: String, value: String): EditText =
        EditText(this).apply {
            this.hint = hint
            setText(value)
        }

    private fun horizontal(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

    private fun addView(view: android.view.View) {
        content.addView(view)
    }

    private fun showMessage(message: String) {
        reset(message)
    }

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
}
