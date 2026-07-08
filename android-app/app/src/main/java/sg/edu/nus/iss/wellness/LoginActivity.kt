package sg.edu.nus.iss.wellness

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import retrofit2.HttpException
import sg.edu.nus.iss.wellness.api.ApiClient
import sg.edu.nus.iss.wellness.api.GoogleAuthRequest
import sg.edu.nus.iss.wellness.api.LoginRequest
import sg.edu.nus.iss.wellness.databinding.ActivityLoginBinding

/**
 * Login screen for email/password and Google SSO.
 *
 * @author Kumaraguru Surya
 * @author Tang Chee Seng
 * @author Chua Wei Yi Justin
 */
class LoginActivity : AppCompatActivity() {

    private val scope = MainScope()
    private lateinit var tokenStore: TokenStore
    private var googleSignInClient: GoogleSignInClient? = null
    private lateinit var binding: ActivityLoginBinding

    companion object {
        private const val RC_GOOGLE_SIGN_IN = 9001
        private const val TAG = "LoginActivity"
    }

    private fun showStatus(message: String, error: Boolean = false) {
        binding.statusText.visibility = View.VISIBLE
        binding.statusText.setBackgroundResource(
            if (error) R.drawable.bg_status_error else R.drawable.bg_status_success
        )
        binding.statusText.text = message
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tokenStore = TokenStore(this)

        if (!tokenStore.token().isNullOrBlank()) {
            startActivity(Intent(this, DashboardActivity::class.java))
            finish()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdge.apply(this, binding.root)

        // Only configure Google Sign-In when a Web Client ID is provided. requestIdToken("")
        // throws IllegalArgumentException and would crash the app on launch (e.g. builds without
        // GOOGLE_WEB_CLIENT_ID in local.properties). When absent, disable the button instead.
        val googleButton = binding.googleSignInButton        
        val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
        if (webClientId.isNotBlank()) {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build()
            googleSignInClient = GoogleSignIn.getClient(this, gso)
        } else {
            Log.w(TAG, "GOOGLE_WEB_CLIENT_ID not configured; Google sign-in disabled.")
            googleButton.isEnabled = false
        }

        binding.loginButton.setOnClickListener {
            val email = binding.emailInput.text.toString().trim()
            val password = binding.passwordInput.text.toString()
            if (email.isBlank() || password.isBlank()) {
                binding.statusText.visibility = View.VISIBLE
                binding.statusText.setBackgroundResource(R.drawable.bg_status_error)
                binding.statusText.text = "Email and password are required."
                return@setOnClickListener
            }
            scope.launch {
                runCatching {
                    binding.statusText.visibility = View.VISIBLE
                    binding.statusText.setBackgroundResource(R.drawable.bg_status_success)
                    binding.statusText.text = "Logging in..."
                    ApiClient.create(tokenStore).login(LoginRequest(email, password))
                }.onSuccess { response ->
                    onLoginSuccess(response.token, response.user.displayName, response.user.email)
                }.onFailure { error ->
                    if (error is HttpException && error.code() == 403) {
                        // Account is deactivated (not bad credentials). Unlock the standing
                        // Reactivate button and point the user at it; active users never reach here.
                        binding.reactivateButton.isEnabled = true
                        binding.reactivateButton.alpha = 1f
                        showStatus(
                            "This account is deactivated. Tap \"Reactivate account\" below to restore it.",
                            error = true
                        )
                    } else {
                        showStatus("Login failed. Check your credentials or backend connection.", error = true)
                    }
                }
            }
        }

        googleButton.setOnClickListener {
            val client = googleSignInClient
            if (client == null) {
                showStatus("Google sign-in is not configured in this build.", error = true)
                return@setOnClickListener
            }
            Log.d(TAG, "Google sign-in tapped; webClientId=$webClientId")
            showStatus("Opening Google sign-in...")
            @Suppress("DEPRECATION")
            startActivityForResult(client.signInIntent, RC_GOOGLE_SIGN_IN)
        }

        binding.registerButton.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        binding.reactivateButton.setOnClickListener {
            showReactivateDialog()
        }
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != RC_GOOGLE_SIGN_IN) return
        Log.d(TAG, "onActivityResult: resultCode=$resultCode, hasData=${data != null}")

        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(data)
                .getResult(ApiException::class.java)
            val idToken = account.idToken
            Log.d(TAG, "Sign-in success: email=${account.email}, idToken=${if (idToken.isNullOrBlank()) "MISSING" else "present"}")
            if (idToken.isNullOrBlank()) {
                showStatus("Google sign-in failed: no ID token returned (check Web Client ID).", error = true)
                return
            }
            exchangeGoogleToken(idToken, account.photoUrl?.toString())
        } catch (e: ApiException) {
            Log.e(TAG, "Google sign-in failed, statusCode=${e.statusCode}", e)
            showStatus("Google sign-in failed (code ${e.statusCode}).", error = true)
        }
    }

    private fun exchangeGoogleToken(idToken: String, photoUrl: String?) {
        scope.launch {
            runCatching {
                showStatus("Signing in with Google...")
                ApiClient.create(tokenStore).googleLogin(GoogleAuthRequest(idToken))
            }.onSuccess { response ->
                onLoginSuccess(response.token, response.user.displayName, response.user.email, photoUrl)
            }.onFailure { e ->
                Log.e(TAG, "Backend googleLogin failed", e)
                showStatus("Google sign-in failed: ${e.message ?: "backend error"}.", error = true)
            }
        }
    }

    private fun showReactivateDialog() {
        val density = resources.displayMetrics.density
        val pad = (16 * density).toInt()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, 0)
        }
        val emailField = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            hint = "Email"
            // Prefill from the login field as a convenience; still a separate prompt.
            setText(binding.emailInput.text?.toString().orEmpty())
        }
        val passwordField = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "Password"
        }
        container.addView(emailField)
        container.addView(passwordField)

        AlertDialog.Builder(this)
            .setTitle("Reactivate account")
            .setMessage("Enter your credentials to restore a deactivated account.")
            .setView(container)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Reactivate") { _, _ ->
                val email = emailField.text?.toString()?.trim().orEmpty()
                val password = passwordField.text?.toString().orEmpty()
                if (email.isBlank() || password.isBlank()) {
                    showStatus("Email and password are required to reactivate.", error = true)
                } else {
                    reactivate(email, password)
                }
            }
            .show()
    }

    private fun reactivate(email: String, password: String) {
        scope.launch {
            runCatching {
                showStatus("Reactivating your account...")
                ApiClient.create(tokenStore).reactivate(LoginRequest(email, password))
            }.onSuccess { response ->
                onLoginSuccess(response.token, response.user.displayName, response.user.email)
            }.onFailure {
                showStatus("Could not reactivate. Check your credentials and try again.", error = true)
            }
        }
    }

    private fun onLoginSuccess(token: String, displayName: String, email: String, photoUrl: String? = null) {
        tokenStore.save(token, displayName, email, photoUrl)
        startActivity(Intent(this@LoginActivity, DashboardActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
