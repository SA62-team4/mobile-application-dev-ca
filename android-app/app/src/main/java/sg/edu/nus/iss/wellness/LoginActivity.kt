package sg.edu.nus.iss.wellness

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import sg.edu.nus.iss.wellness.api.ApiClient
import sg.edu.nus.iss.wellness.api.LoginRequest

/**
 * Login screen for JWT authentication.
 *
 * @author SA62 Team
 */
class LoginActivity : Activity() {
    private val scope = MainScope()
    private lateinit var tokenStore: TokenStore

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

        val emailInput = findViewById<EditText>(R.id.emailInput)
        val passwordInput = findViewById<EditText>(R.id.passwordInput)
        val statusText = findViewById<TextView>(R.id.statusText)

        findViewById<Button>(R.id.loginButton).setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString()
            if (email.isBlank() || password.isBlank()) {
                statusText.text = "Email and password are required."
                return@setOnClickListener
            }
            scope.launch {
                runCatching {
                    statusText.text = "Logging in..."
                    ApiClient.create(tokenStore).login(LoginRequest(email, password))
                }.onSuccess { response ->
                    tokenStore.save(response.token, response.user.displayName, response.user.email)
                    startActivity(Intent(this@LoginActivity, HomeActivity::class.java))
                    finish()
                }.onFailure {
                    statusText.text = "Login failed. Check your credentials or backend connection."
                }
            }
        }

        findViewById<Button>(R.id.registerButton).setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
