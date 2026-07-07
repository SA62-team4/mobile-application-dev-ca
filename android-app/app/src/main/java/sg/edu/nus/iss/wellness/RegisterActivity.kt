package sg.edu.nus.iss.wellness

import android.app.Activity
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import sg.edu.nus.iss.wellness.api.ApiClient
import sg.edu.nus.iss.wellness.api.RegisterRequest
import sg.edu.nus.iss.wellness.databinding.ActivityRegisterBinding

/**
 * 1) Account registration screen.
 *    @author Tang Chee Seng, Tiong Zhong Cheng

 * 2) Refactor UI to Android methods taught in class
 *    @author Tang Chee Seng
 */
class RegisterActivity : AppCompatActivity() {
    private val scope = MainScope()
    private lateinit var binding: ActivityRegisterBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdge.apply(this, binding.root)

        val emailInput = binding.emailInput
        val passwordInput = binding.passwordInput
        val confirmPasswordInput = binding.confirmPasswordInput
        val statusText = binding.statusText
        val tokenStore = TokenStore(this)

        binding.createButton.setOnClickListener {
            val displayName = binding.displayNameInput.text.toString().trim()
            val email = binding.emailInput.text.toString().trim()
            val password = binding.passwordInput.text.toString()
            val confirmPassword = binding.confirmPasswordInput.text.toString()

            if (displayName.isBlank() || email.isBlank() || password.length < 8) {
                binding.statusText.visibility = View.VISIBLE
                binding.statusText.setBackgroundResource(R.drawable.bg_status_error)
                binding.statusText.text = "Name, valid email, and 8 character password are required."
                return@setOnClickListener
            }
            if (password != confirmPassword) {
                binding.statusText.visibility = View.VISIBLE
                binding.statusText.setBackgroundResource(R.drawable.bg_status_error)
                binding.statusText.text = "Passwords do not match."
                return@setOnClickListener
            }

            scope.launch {
                runCatching {
                    binding.statusText.visibility = View.VISIBLE
                    binding.statusText.setBackgroundResource(R.drawable.bg_status_success)
                    binding.statusText.text = "Creating account..."
                    ApiClient.create(tokenStore).register(RegisterRequest(displayName, email, password))
                }.onSuccess {
                    binding.statusText.visibility = View.VISIBLE
                    binding.statusText.setBackgroundResource(R.drawable.bg_status_success)
                    binding.statusText.text = "Account created. Return to login."
                }.onFailure {
                    binding.statusText.visibility = View.VISIBLE
                    binding.statusText.setBackgroundResource(R.drawable.bg_status_error)
                    binding.statusText.text = "Registration failed. Try a different email."
                }
            }
        }

        binding.backButton.setOnClickListener { 
            finish() 
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}