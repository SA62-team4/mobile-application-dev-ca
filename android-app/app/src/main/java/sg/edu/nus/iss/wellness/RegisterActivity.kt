package sg.edu.nus.iss.wellness

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import sg.edu.nus.iss.wellness.api.ApiClient
import sg.edu.nus.iss.wellness.api.RegisterRequest

/**
 * Account registration screen.
 *
 * @author SA62 Team
 */
class RegisterActivity : Activity() {
    private val scope = MainScope()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)
        EdgeToEdge.apply(this, findViewById(R.id.rootContainer))

        val displayNameInput = findViewById<EditText>(R.id.displayNameInput)
        val emailInput = findViewById<EditText>(R.id.emailInput)
        val passwordInput = findViewById<EditText>(R.id.passwordInput)
        val confirmPasswordInput = findViewById<EditText>(R.id.confirmPasswordInput)
        val statusText = findViewById<TextView>(R.id.statusText)
        val tokenStore = TokenStore(this)

        findViewById<Button>(R.id.createButton).setOnClickListener {
            val displayName = displayNameInput.text.toString().trim()
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString()
            val confirmPassword = confirmPasswordInput.text.toString()

            if (displayName.isBlank() || email.isBlank() || password.length < 8) {
                statusText.text = "Name, valid email, and 8 character password are required."
                return@setOnClickListener
            }
            if (password != confirmPassword) {
                statusText.text = "Passwords do not match."
                return@setOnClickListener
            }

            scope.launch {
                runCatching {
                    statusText.text = "Creating account..."
                    ApiClient.create(tokenStore).register(RegisterRequest(displayName, email, password))
                }.onSuccess {
                    statusText.text = "Account created. Return to login."
                }.onFailure {
                    statusText.text = "Registration failed. Try a different email."
                }
            }
        }

        findViewById<Button>(R.id.backButton).setOnClickListener { finish() }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
