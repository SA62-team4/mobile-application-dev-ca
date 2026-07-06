package sg.edu.nus.iss.wellness

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sg.edu.nus.iss.wellness.adapter.RecommendationAdapter
import sg.edu.nus.iss.wellness.api.ApiClient
import sg.edu.nus.iss.wellness.api.ApiService
import sg.edu.nus.iss.wellness.api.RecommendationResponse
import sg.edu.nus.iss.wellness.databinding.ActivityRecommendationsBinding
import sg.edu.nus.iss.wellness.ui.addLoadingBlock
import sg.edu.nus.iss.wellness.ui.addStateBlock
import sg.edu.nus.iss.wellness.ui.apiErrorMessage
import sg.edu.nus.iss.wellness.ui.highlightTab
import sg.edu.nus.iss.wellness.ui.infoCard
import sg.edu.nus.iss.wellness.ui.showError
import sg.edu.nus.iss.wellness.ui.wireBottomNav

/**
 * Displays AI-generated wellness recommendations and lets the user request a new one.
 *
 * @author SA62 Team
 */
class RecommendationsActivity : AppCompatActivity() {
    private val scope = MainScope()
    private lateinit var binding: ActivityRecommendationsBinding
    private lateinit var tokenStore: TokenStore
    private lateinit var api: ApiService
    private lateinit var generateButton: Button
    private lateinit var statusContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tokenStore = TokenStore(this)
        if (tokenStore.token().isNullOrBlank()) {
            goToLogin()
            return
        }
        api = ApiClient.create(tokenStore)

        binding = ActivityRecommendationsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdge.apply(this, binding.rootContainer)

        highlightTab(
            listOf(
                binding.bottomNav.dashboardButton,
                binding.bottomNav.chatButton,
                binding.bottomNav.recommendationsButton,
                binding.bottomNav.profileButton
            ),
            binding.bottomNav.recommendationsButton
        )
        wireBottomNav(binding.bottomNav, RecommendationsActivity::class.java)

        val headerView = layoutInflater.inflate(R.layout.header_generate_button, binding.recommendationsListView, false)
        generateButton = headerView.findViewById(R.id.generateButton)
        statusContainer = headerView.findViewById(R.id.statusContainer)
        generateButton.setOnClickListener { generateRecommendation() }
        binding.recommendationsListView.addHeaderView(headerView)
        binding.recommendationsListView.adapter = RecommendationAdapter(this, emptyList())

        loadRecommendations()
    }

    private fun showLoading(title: String, detail: String) {
        binding.recommendationsListView.visibility = View.GONE
        binding.emptyStateContainer.visibility = View.VISIBLE
        binding.emptyStateContainer.removeAllViews()
        addStateBlock(binding.emptyStateContainer, title, detail, "...")
    }

    private fun showFetchError(title: String, detail: String) {
        binding.recommendationsListView.visibility = View.GONE
        binding.emptyStateContainer.visibility = View.VISIBLE
        binding.emptyStateContainer.removeAllViews()
        showError(binding.emptyStateContainer, title, detail)
    }

    private fun showContent() {
        binding.emptyStateContainer.visibility = View.GONE
        binding.emptyStateContainer.removeAllViews()
        binding.recommendationsListView.visibility = View.VISIBLE
    }

    private fun loadRecommendations() {
        showLoading("Loading recommendations", "Fetching generated guidance from the backend.")
        scope.launch {
            runCatching { api.recommendations() }
                .onSuccess { renderRecommendations(it) }
                .onFailure { showFetchError("Could not load recommendations.", "Check backend, Python AI service, and Ollama status.") }
        }
    }

    private fun renderRecommendations(recommendations: List<RecommendationResponse>) {
        showContent()
        statusContainer.removeAllViews()
        if (recommendations.isEmpty()) {
            statusContainer.addView(infoCard("No recommendations yet", "Generate one after adding wellness records."))
        }
        binding.recommendationsListView.adapter = RecommendationAdapter(this, recommendations)
    }

    private fun generateRecommendation() {
        statusContainer.removeAllViews()
        val (detailsView, progressViews) = addLoadingBlock(statusContainer, "Generating recommendation", "Accessing your 14-day logs...", "AI")
        val (progressBar, percentView) = progressViews
        generateButton.isEnabled = false

        val statusJob = scope.launch {
            val stages = listOf(
                "Analyzing sleep, activity, and mood patterns...",
                "Querying vector database for expert wellness guidelines...",
                "Local LLM is writing your custom wellness guide (usually takes 15-40s)...",
                "Almost ready! Structuring personalized action items...",
                "Completing final formatting and saving to backend...",
                "Still thinking... Local Ollama is heavily processing, thanks for your patience!"
            )
            for (message in stages) {
                delay(4000)
                detailsView.text = message
            }
        }

        val progressJob = scope.launch {
            var currentProgress = 0
            while (currentProgress < 95) {
                delay(300)
                currentProgress++
                progressBar.progress = currentProgress
                percentView.text = "$currentProgress%"

                if (currentProgress > 75) {
                    delay(200)
                }
                if (currentProgress > 88) {
                    delay(400)
                }
            }
        }

        scope.launch {
            runCatching { api.generateRecommendation() }
                .onSuccess {
                    statusJob.cancel()
                    progressJob.cancel()
                    progressBar.progress = 100
                    percentView.text = "100%"
                    delay(300)
                    generateButton.isEnabled = true
                    runCatching { api.recommendations() }
                        .onSuccess { renderRecommendations(it) }
                        .onFailure { statusContainer.removeAllViews() }
                }
                .onFailure {
                    statusJob.cancel()
                    progressJob.cancel()
                    generateButton.isEnabled = true
                    statusContainer.removeAllViews()
                    showError(
                        statusContainer,
                        apiErrorMessage("Could not generate recommendation", it),
                        "Do not pretend a recommendation was saved. Retry after services recover."
                    )
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
