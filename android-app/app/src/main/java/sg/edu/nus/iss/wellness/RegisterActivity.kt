package sg.edu.nus.iss.wellness

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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

    private fun hideKeyboard() {
        val focusedView = currentFocus ?: binding.root
        focusedView.clearFocus()
        val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(focusedView.windowToken, 0)
    }

    private fun showSnackbar(message: String, isSuccess: Boolean) {
        val snackbar = Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
        val snackbarView = snackbar.view
        val backgroundColor = if (isSuccess) {
            ContextCompat.getColor(this, R.color.metric_green)
        } else {
            ContextCompat.getColor(this, R.color.error)
        }
        val radius = resources.getDimension(R.dimen.snackbar_corner_radius)
        val shape = MaterialShapeDrawable(
            ShapeAppearanceModel.builder()
                .setAllCornerSizes(radius)
                .build()
        ).apply {
            fillColor = ColorStateList.valueOf(backgroundColor)
        }
        snackbarView.background = shape

        val layoutParams = snackbarView.layoutParams as FrameLayout.LayoutParams
        val horizontalMargin = resources.getDimensionPixelSize(R.dimen.snackbar_horizontal_margin)
        val bottomMargin = resources.getDimensionPixelSize(R.dimen.snackbar_bottom_margin)
        layoutParams.setMargins(horizontalMargin, 0, horizontalMargin, bottomMargin)
        snackbarView.layoutParams = layoutParams

        snackbarView.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
            ?.setTextColor(ContextCompat.getColor(this, R.color.text_on_primary))

        snackbar.show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdge.apply(this, binding.root)

        val tokenStore = TokenStore(this)

        binding.createButton.setOnClickListener {
            hideKeyboard()
            val displayName = binding.displayNameInput.text.toString().trim()
            val email = binding.emailInput.text.toString().trim()
            val password = binding.passwordInput.text.toString()
            val confirmPassword = binding.confirmPasswordInput.text.toString()

            if (displayName.isBlank() || email.isBlank() || password.length < 8) {
                showSnackbar("Name, valid email, and 8 character password are required.", isSuccess = false)
                return@setOnClickListener
            }
            if (password != confirmPassword) {
                showSnackbar("Passwords do not match.", isSuccess = false)
                return@setOnClickListener
            }

            scope.launch {
                runCatching {
                    ApiClient.create(tokenStore).register(RegisterRequest(displayName, email, password))
                }.onSuccess {
                    showSnackbar("Account created successfully", isSuccess = true)
                    delay(1500)
                    startActivity(Intent(this@RegisterActivity, LoginActivity::class.java))
                    finish()
                }.onFailure {
                    showSnackbar("Registration failed. Try a different email.", isSuccess = false)
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