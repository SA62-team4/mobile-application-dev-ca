package sg.edu.nus.iss.wellness

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
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

/**
 * Login screen — supports email/password and Google SSO.
 *
 * @author Surya Kumaraguru
 */
class LoginActivity : Activity() {

    private val scope = MainScope()
    private lateinit var tokenStore: TokenStore
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var statusText: TextView

    companion object {
        private const val RC_GOOGLE_SIGN_IN = 9001
        private const val TAG = "LoginActivity"
    }

    private fun showStatus(message: String, error: Boolean = false) {
        statusText.visibility = View.VISIBLE
        statusText.setBackgroundResource(
            if (error) R.drawable.bg_status_error else R.drawable.bg_status_success
        )
        statusText.text = message
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tokenStore = TokenStore(this)

        if (!tokenStore.token().isNullOrBlank()) {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_login)
        EdgeToEdge.apply(this, findViewById(R.id.rootContainer))

        statusText = findViewById(R.id.statusText)
        val emailInput = findViewById<EditText>(R.id.emailInput)
        val passwordInput = findViewById<EditText>(R.id.passwordInput)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        findViewById<Button>(R.id.loginButton).setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString()
            if (email.isBlank() || password.isBlank()) {
                statusText.visibility = View.VISIBLE
                statusText.setBackgroundResource(R.drawable.bg_status_error)
                statusText.text = "Email and password are required."
                return@setOnClickListener
            }
            scope.launch {
                runCatching {
                    statusText.visibility = View.VISIBLE
                    statusText.setBackgroundResource(R.drawable.bg_status_success)
                    statusText.text = "Logging in..."
                    ApiClient.create(tokenStore).login(LoginRequest(email, password))
                }.onSuccess { response ->
                    onLoginSuccess(response.token, response.user.displayName, response.user.email)
                }.onFailure {
                    statusText.visibility = View.VISIBLE
                    statusText.setBackgroundResource(R.drawable.bg_status_error)
                    statusText.text = "Login failed. Check your credentials or backend connection."
                }
            }
        }

        findViewById<Button>(R.id.googleSignInButton).setOnClickListener {
            Log.d(TAG, "Google sign-in tapped; webClientId=${BuildConfig.GOOGLE_WEB_CLIENT_ID}")
            showStatus("Opening Google sign-in...")
            @Suppress("DEPRECATION")
            startActivityForResult(googleSignInClient.signInIntent, RC_GOOGLE_SIGN_IN)
        }

        findViewById<Button>(R.id.registerButton).setOnClickListener {
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
        startActivity(Intent(this@LoginActivity, HomeActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
