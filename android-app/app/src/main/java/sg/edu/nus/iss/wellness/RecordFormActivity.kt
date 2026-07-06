package sg.edu.nus.iss.wellness

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import sg.edu.nus.iss.wellness.api.ApiClient
import sg.edu.nus.iss.wellness.api.ApiService
import sg.edu.nus.iss.wellness.api.WellnessRecordRequest
import sg.edu.nus.iss.wellness.databinding.ActivityRecordFormBinding
import sg.edu.nus.iss.wellness.ui.addStateBlock
import sg.edu.nus.iss.wellness.ui.apiErrorMessage
import sg.edu.nus.iss.wellness.ui.showError
import java.time.LocalDate

/**
 * Add/edit screen for a single wellness record. Returns RESULT_OK to the
 * caller (Dashboard) on save or delete so it can refresh.
 *
 * @author SA62 Team
 */
class RecordFormActivity : AppCompatActivity() {
    private val scope = MainScope()
    private lateinit var binding: ActivityRecordFormBinding
    private lateinit var tokenStore: TokenStore
    private lateinit var api: ApiService
    private var recordId: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tokenStore = TokenStore(this)
        if (tokenStore.token().isNullOrBlank()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        api = ApiClient.create(tokenStore)

        binding = ActivityRecordFormBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdge.apply(this, binding.rootContainer)

        recordId = intent.getLongExtra(Constants.EXTRA_RECORD_ID, -1L).takeIf { it != -1L }
        val isEdit = recordId != null

        binding.titleText.text = if (isEdit) "Edit record" else "Add record"
        binding.deleteButton.visibility = if (isEdit) View.VISIBLE else View.GONE

        binding.dateInput.setText(intent.getStringExtra(Constants.EXTRA_RECORD_DATE) ?: LocalDate.now().toString())
        binding.sleepInput.setText(intent.getDoubleExtra(Constants.EXTRA_RECORD_SLEEP_HOURS, 7.0).toString())
        binding.exerciseTypeInput.setText(intent.getStringExtra(Constants.EXTRA_RECORD_EXERCISE_TYPE) ?: "Walking")
        binding.exerciseMinutesInput.setText(intent.getIntExtra(Constants.EXTRA_RECORD_EXERCISE_MINUTES, 20).toString())
        binding.moodInput.setText(intent.getIntExtra(Constants.EXTRA_RECORD_MOOD_SCORE, 3).toString())
        binding.notesInput.setText(intent.getStringExtra(Constants.EXTRA_RECORD_NOTES) ?: "")

        binding.saveButton.setOnClickListener { save() }
        binding.deleteButton.setOnClickListener { confirmDelete() }
        binding.cancelButton.setOnClickListener { finish() }
    }

    private fun save() {
        binding.statusContainer.removeAllViews()
        val request = WellnessRecordRequest(
            recordDate = binding.dateInput.text.toString(),
            sleepHours = binding.sleepInput.text.toString().toDoubleOrNull() ?: 0.0,
            exerciseType = binding.exerciseTypeInput.text.toString(),
            exerciseMinutes = binding.exerciseMinutesInput.text.toString().toIntOrNull() ?: 0,
            moodScore = binding.moodInput.text.toString().toIntOrNull() ?: 3,
            notes = binding.notesInput.text.toString()
        )

        addStateBlock(binding.statusContainer, "Saving record", "Sending your wellness log to the backend.", "...")
        scope.launch {
            val id = recordId
            runCatching {
                if (id == null) api.createRecord(request) else api.updateRecord(id, request)
            }.onSuccess {
                setResult(RESULT_OK)
                finish()
            }.onFailure {
                binding.statusContainer.removeAllViews()
                showError(binding.statusContainer, apiErrorMessage("Could not save record", it), "Check fields and backend connection.")
            }
        }
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("Delete record?")
            .setMessage("This removes the wellness log after backend confirmation.")
            .setPositiveButton("Delete") { _, _ -> delete() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun delete() {
        val id = recordId ?: return
        binding.statusContainer.removeAllViews()
        addStateBlock(binding.statusContainer, "Deleting record", "Removing the selected wellness log.", "...")
        scope.launch {
            runCatching { api.deleteRecord(id) }
                .onSuccess {
                    setResult(RESULT_OK)
                    finish()
                }
                .onFailure {
                    binding.statusContainer.removeAllViews()
                    showError(binding.statusContainer, "Could not delete record.", "Please retry after checking the backend connection.")
                }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
