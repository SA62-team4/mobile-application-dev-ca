package sg.edu.nus.iss.wellness

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import sg.edu.nus.iss.wellness.api.ApiClient
import sg.edu.nus.iss.wellness.api.ApiService
import sg.edu.nus.iss.wellness.api.DeleteAccountRequest
import sg.edu.nus.iss.wellness.databinding.ActivityPrivacyBinding

/**
 * Privacy & data control screen (S-03).
 *
 * Surfaces the app's local-AI privacy posture and lets the user control their
 * data: export a full JSON copy, reversibly deactivate, or permanently delete.
 * Local-password accounts reconfirm the password; Google-only accounts confirm
 * through their active app session because no app password exists. Export uses
 * the Storage Access Framework so it needs no storage permission: the user
 * chooses where the file is written.
 *
 * @author Chua Wei Yi Justin
 * @author Tiong Zhong Cheng
 */
class PrivacyActivity : AppCompatActivity() {
    private val scope = MainScope()
    private lateinit var binding: ActivityPrivacyBinding
    private lateinit var tokenStore: TokenStore

    // Holds the fetched export bytes until the user has picked a destination file.
    private var pendingExport: ByteArray? = null

    private val createDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val data = pendingExport
        pendingExport = null
        if (uri == null || data == null) {
            showStatus("Export cancelled.")
            return@registerForActivityResult
        }
        runCatching {
            contentResolver.openOutputStream(uri)?.use { it.write(data) }
        }.onSuccess {
            showStatus("Your data was exported successfully.")
        }.onFailure {
            showStatus("Could not save the export file.", error = true)
        }
    }

    /** Authenticated client; a genuine 401 (expired token) returns to login. */
    private fun api(): ApiService = ApiClient.create(tokenStore) {
        runOnUiThread { goToLogin(sessionExpired = true) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tokenStore = TokenStore(this)
        if (tokenStore.token().isNullOrBlank()) {
            goToLogin()
            return
        }

        binding = ActivityPrivacyBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdge.apply(this, binding.rootContainer)

        binding.exportButton.setOnClickListener { startExport() }
        binding.deactivateButton.setOnClickListener { confirmDeactivate() }
        binding.deleteButton.setOnClickListener { confirmDelete() }
    }

    private fun startExport() {
        showStatus("Preparing your data...")
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val response = api().exportAccountData()
                    if (!response.isSuccessful) throw HttpException(response)
                    response.body()?.bytes() ?: ByteArray(0)
                }
            }.onSuccess { bytes ->
                pendingExport = bytes
                createDocument.launch("wellness-export.json")
            }.onFailure {
                showStatus("Could not prepare your data. Try again.", error = true)
            }
        }
    }

    private fun confirmDeactivate() {
        AlertDialog.Builder(this)
            .setTitle("Deactivate account?")
            .setMessage(
                "Your account will be hidden and sign-in blocked, but your records, " +
                    "recommendations and chat history are kept. You can reactivate anytime " +
                    "by signing in again."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Deactivate") { _, _ -> deactivate() }
            .show()
    }

    private fun deactivate() {
        showStatus("Deactivating...")
        scope.launch {
            runCatching { api().deactivateAccount() }
                .onSuccess {
                    tokenStore.clear()
                    goToLogin()
                }
                .onFailure {
                    showStatus("Could not deactivate. Try again.", error = true)
                }
        }
    }

    private fun confirmDelete() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "Password (leave blank for Google-only)"
        }
        AlertDialog.Builder(this)
            .setTitle("Delete account permanently?")
            .setMessage(
                "This erases your account and all records, recommendations and chat history. " +
                    "It cannot be undone. Enter your password if this account has one; " +
                    "Google-only accounts can leave it blank."
            )
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete forever") { _, _ ->
                val password = input.text?.toString().orEmpty()
                deleteAccount(password.takeIf { it.isNotBlank() })
            }
            .show()
    }

    private fun deleteAccount(password: String?) {
        showStatus("Deleting your account...")
        scope.launch {
            runCatching { api().deleteAccount(DeleteAccountRequest(password)) }
                .onSuccess {
                    tokenStore.clear()
                    goToLogin()
                }
                .onFailure { e ->
                    // The backend returns 400 (not 401/403) for a wrong password, so the
                    // session interceptor does not log the user out on a simple typo.
                    val message = if (e is HttpException && e.code() == 400) {
                        "Password required or incorrect. Google-only accounts can leave it blank."
                    } else {
                        "Could not delete your account. Try again."
                    }
                    showStatus(message, error = true)
                }
        }
    }

    private fun showStatus(message: String, error: Boolean = false) {
        binding.statusText.visibility = View.VISIBLE
        binding.statusText.setBackgroundResource(
            if (error) R.drawable.bg_status_error else R.drawable.bg_status_success
        )
        binding.statusText.text = message
    }

    private fun goToLogin(sessionExpired: Boolean = false) {
        // Clear the whole task so Back cannot reveal the now-stale Dashboard/Profile
        // of a deactivated or deleted account.
        startActivity(LoginActivity.redirectIntent(this, sessionExpired))
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
