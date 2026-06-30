package sg.edu.nus.iss.wellness

import android.app.Activity
import android.content.Intent
import android.os.Bundle
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
            statusText.text = "Opening Google sign-in..."
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

        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(data)
                .getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken.isNullOrBlank()) {
                statusText.text = "Google sign-in failed: no ID token returned."
                return
            }
            exchangeGoogleToken(idToken)
        } catch (e: ApiException) {
            statusText.text = "Google sign-in cancelled or failed (code ${e.statusCode})."
        }
    }

    private fun exchangeGoogleToken(idToken: String) {
        scope.launch {
            runCatching {
                statusText.text = "Signing in with Google..."
                ApiClient.create(tokenStore).googleLogin(GoogleAuthRequest(idToken))
            }.onSuccess { response ->
                onLoginSuccess(response.token, response.user.displayName, response.user.email)
            }.onFailure {
                statusText.text = "Google sign-in failed. Check backend connection and client ID."
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
