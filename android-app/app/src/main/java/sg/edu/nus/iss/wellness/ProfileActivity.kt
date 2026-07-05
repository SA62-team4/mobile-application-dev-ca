package sg.edu.nus.iss.wellness

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import sg.edu.nus.iss.wellness.api.ApiClient
import sg.edu.nus.iss.wellness.databinding.ActivityProfileBinding
import sg.edu.nus.iss.wellness.ui.highlightTab

/**
 * Displays the signed-in user's profile and handles logout.
 *
 * @author SA62 Team
 */
class ProfileActivity : AppCompatActivity() {
    private val scope = MainScope()
    private lateinit var binding: ActivityProfileBinding
    private lateinit var tokenStore: TokenStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tokenStore = TokenStore(this)
        if (tokenStore.token().isNullOrBlank()) {
            goToLogin()
            return
        }

        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdge.apply(this, binding.rootContainer)

        binding.displayNameText.text = tokenStore.displayName().ifBlank { "Wellness user" }
        binding.emailText.text = tokenStore.email().ifBlank { "No email stored" }

        highlightTab(
            listOf(
                binding.bottomNav.dashboardButton,
                binding.bottomNav.chatButton,
                binding.bottomNav.recommendationsButton,
                binding.bottomNav.profileButton
            ),
            binding.bottomNav.profileButton
        )

        binding.logoutButton.setOnClickListener {
            scope.launch {
                runCatching { ApiClient.create(tokenStore).logout() }
                tokenStore.clear()
                goToLogin()
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
