package sg.edu.nus.iss.wellness

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import coil.load
import coil.transform.CircleCropTransformation
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import sg.edu.nus.iss.wellness.api.ApiClient
import sg.edu.nus.iss.wellness.databinding.ActivityProfileBinding
import sg.edu.nus.iss.wellness.ui.highlightTab
import sg.edu.nus.iss.wellness.ui.wireBottomNav

/**
 * Profile screen with logout and Google account picture.
 *
 * @author Abu Bakar Nasir
 * @author Tiong Zhong Cheng
 * @author Chua Wei Yi Justin
 */
class ProfileActivity : AppCompatActivity() {
    private val scope = MainScope()
    private lateinit var binding: ActivityProfileBinding
    private lateinit var tokenStore: TokenStore
    private lateinit var api: sg.edu.nus.iss.wellness.api.ApiService

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

        api = ApiClient.create(tokenStore)

        binding.displayNameText.text = tokenStore.displayName().ifBlank { "Wellness user" }
        binding.emailText.text = tokenStore.email().ifBlank { "No email stored" }

        // Google account picture, if one was captured at sign-in. Falls back to the
        // placeholder for email/password logins or while the image loads.
        val photoUrl = tokenStore.photoUrl()
        if (photoUrl.isNotBlank()) {
            binding.profileImage.load(photoUrl) {
                placeholder(R.drawable.ic_profile_placeholder)
                error(R.drawable.ic_profile_placeholder)
                transformations(CircleCropTransformation())
            }
        }

        highlightTab(
            listOf(
                binding.bottomNav.dashboardButton,
                binding.bottomNav.chatButton,
                binding.bottomNav.recommendationsButton,
                binding.bottomNav.profileButton
            ),
            binding.bottomNav.profileButton
        )
        wireBottomNav(binding.bottomNav, ProfileActivity::class.java)

        binding.saveHeightButton.setOnClickListener { saveHeight() }

        binding.privacyButton.setOnClickListener {
            startActivity(Intent(this, PrivacyActivity::class.java))
        }

        binding.logoutButton.setOnClickListener {
            scope.launch {
                runCatching { ApiClient.create(tokenStore).logout() }
                InsightNotificationScheduler.cancel(this@ProfileActivity)
                tokenStore.clear()
                goToLogin()
            }
        }

        loadProfile()
    }

    private fun loadProfile() {
        scope.launch {
            runCatching { api.profile() }
                .onSuccess { profile ->
                    binding.heightInput.setText(profile.heightCm?.let { formatHeight(it) }.orEmpty())
                }
                .onFailure { error ->
                    if (error.isAuthFailure()) {
                        tokenStore.clear()
                        goToLogin()
                    }
                }
        }
    }

    private fun saveHeight() {
        val raw = binding.heightInput.text.toString().trim()
        val height = raw.takeIf { it.isNotBlank() }?.toDoubleOrNull()
        if (height != null && height <= 0.0) {
            binding.heightInput.error = "Height must be greater than 0."
            return
        }

        scope.launch {
            runCatching { api.updateProfile(sg.edu.nus.iss.wellness.api.AccountProfileUpdateRequest(height)) }
                .onSuccess { profile ->
                    binding.heightInput.setText(profile.heightCm?.let { formatHeight(it) }.orEmpty())
                    android.widget.Toast.makeText(this@ProfileActivity, "Height saved", android.widget.Toast.LENGTH_SHORT).show()
                }
                .onFailure { error ->
                    if (error.isAuthFailure()) {
                        tokenStore.clear()
                        goToLogin()
                    } else {
                        android.widget.Toast.makeText(this@ProfileActivity, "Could not save height", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }

    private fun formatHeight(heightCm: Double): String = if (heightCm == heightCm.toInt().toDouble()) {
        heightCm.toInt().toString()
    } else {
        heightCm.toString()
    }

    private fun Throwable.isAuthFailure(): Boolean = this is retrofit2.HttpException && this.code() in listOf(401, 403)

    private fun goToLogin() {
        // Clear the whole task so Back cannot reveal the stale Dashboard after logout.
        val intent = Intent(this, LoginActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
