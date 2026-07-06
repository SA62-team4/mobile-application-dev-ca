package sg.edu.nus.iss.wellness

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import sg.edu.nus.iss.wellness.api.ApiClient
import sg.edu.nus.iss.wellness.api.GoogleAuthRequest
import sg.edu.nus.iss.wellness.api.LoginRequest
import sg.edu.nus.iss.wellness.databinding.ActivityLoginBinding

/**
 * 1) Login screen — supports email/password and Google SSO.
 *    Done by @author Surya Kumaraguru

 * 2) Refactor UI to Android methods taught in class
 *    Done by @author Tang Chee Seng
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
                }.onFailure {
                    binding.statusText.visibility = View.VISIBLE
                    binding.statusText.setBackgroundResource(R.drawable.bg_status_error)
                    binding.statusText.text = "Login failed. Check your credentials or backend connection."
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
            exchangeGoogleToken(idToken)
        } catch (e: ApiException) {
            Log.e(TAG, "Google sign-in failed, statusCode=${e.statusCode}", e)
            showStatus("Google sign-in failed (code ${e.statusCode}).", error = true)
        }
    }

    private fun exchangeGoogleToken(idToken: String) {
        scope.launch {
            runCatching {
                showStatus("Signing in with Google...")
                ApiClient.create(tokenStore).googleLogin(GoogleAuthRequest(idToken))
            }.onSuccess { response ->
                onLoginSuccess(response.token, response.user.displayName, response.user.email)
            }.onFailure { e ->
                Log.e(TAG, "Backend googleLogin failed", e)
                showStatus("Google sign-in failed: ${e.message ?: "backend error"}.", error = true)
            }
        }
    }

    private fun onLoginSuccess(token: String, displayName: String, email: String) {
        tokenStore.save(token, displayName, email)
        startActivity(Intent(this@LoginActivity, DashboardActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
