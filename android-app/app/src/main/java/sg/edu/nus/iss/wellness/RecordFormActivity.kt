package sg.edu.nus.iss.wellness

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
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
 * @author Abu Bakar Nasir
 */
class RecordFormActivity : AppCompatActivity() {
    private val scope = MainScope()
    private lateinit var binding: ActivityRecordFormBinding
    private lateinit var tokenStore: TokenStore
    private lateinit var api: ApiService
    private var recordId: Long? = null
    private var selectedDate: LocalDate = LocalDate.now()

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
        setupExerciseTypeSpinner()

        selectedDate = runCatching {
            LocalDate.parse(intent.getStringExtra(Constants.EXTRA_RECORD_DATE))
        }.getOrDefault(LocalDate.now())
        if (isEdit) {
            binding.dateInput.setText(selectedDate.toString())
        }

        if (isEdit) {
            binding.sleepInput.setText(intent.getDoubleExtra(Constants.EXTRA_RECORD_SLEEP_HOURS, 0.0).toString())
        }
        val exerciseType = if (isEdit) {
            intent.getStringExtra(Constants.EXTRA_RECORD_EXERCISE_TYPE)
        } else {
            null
        }
        binding.exerciseTypeInput.setSelection(ExerciseTypeOptions.selectedIndexFor(exerciseType))
        if (isEdit) {
            binding.exerciseMinutesInput.setText(intent.getIntExtra(Constants.EXTRA_RECORD_EXERCISE_MINUTES, 0).toString())
            binding.moodInput.setText(intent.getIntExtra(Constants.EXTRA_RECORD_MOOD_SCORE, 3).toString())
            binding.notesInput.setText(intent.getStringExtra(Constants.EXTRA_RECORD_NOTES) ?: "")
        }

        binding.dateInput.setOnClickListener { showDatePicker() }
        binding.saveButton.setOnClickListener { save() }
        binding.deleteButton.setOnClickListener { confirmDelete() }
        binding.cancelButton.setOnClickListener { finish() }
    }

    private fun setupExerciseTypeSpinner() {
        ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            ExerciseTypeOptions.options
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.exerciseTypeInput.adapter = adapter
        }
    }

    private fun showDatePicker() {
        DatePickerDialog(
            this,
            { _, year, month, day ->
                selectedDate = LocalDate.of(year, month + 1, day)
                binding.dateInput.setText(selectedDate.toString())
                binding.dateInput.error = null
            },
            selectedDate.year,
            selectedDate.monthValue - 1,
            selectedDate.dayOfMonth
        ).show()
    }

    private fun save() {
        binding.statusContainer.removeAllViews()
        val request = buildRequestOrNull() ?: return

        setSaving(true)
        addStateBlock(binding.statusContainer, "Saving record", "Sending your wellness log to the backend.", "...")
        scope.launch {
            val id = recordId
            runCatching {
                if (id == null) api.createRecord(request) else api.updateRecord(id, request)
            }.onSuccess {
                Toast.makeText(this@RecordFormActivity, "Record saved", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            }.onFailure {
                setSaving(false)
                binding.statusContainer.removeAllViews()
                showError(binding.statusContainer, apiErrorMessage("Could not save record", it), "Check fields and backend connection.")
            }
        }
    }

    private fun buildRequestOrNull(): WellnessRecordRequest? {
        clearErrors()

        val dateText = binding.dateInput.text.toString().trim()
        val parsedDate = runCatching { LocalDate.parse(dateText) }.getOrNull()
        val sleepHours = binding.sleepInput.text.toString().trim().toDoubleOrNull()
        val exerciseMinutes = binding.exerciseMinutesInput.text.toString().trim().toIntOrNull()
        val moodScore = binding.moodInput.text.toString().trim().toIntOrNull()

        var valid = true
        if (parsedDate == null) {
            binding.dateInput.error = "Date is required."
            valid = false
        }
        if (sleepHours == null || sleepHours < 0.0 || sleepHours > 24.0) {
            binding.sleepInput.error = "Sleep hours must be 0-24."
            valid = false
        }
        if (exerciseMinutes == null || exerciseMinutes < 0) {
            binding.exerciseMinutesInput.error = "Exercise minutes must be 0 or more."
            valid = false
        }
        if (moodScore == null || moodScore !in 1..5) {
            binding.moodInput.error = "Mood score must be 1-5."
            valid = false
        }
        if (!valid) return null

        selectedDate = parsedDate!!
        return WellnessRecordRequest(
            recordDate = selectedDate.toString(),
            sleepHours = sleepHours!!,
            exerciseType = ExerciseTypeOptions.requestValueAt(binding.exerciseTypeInput.selectedItemPosition),
            exerciseMinutes = exerciseMinutes!!,
            moodScore = moodScore!!,
            notes = binding.notesInput.text.toString().trim().ifBlank { null }
        )
    }

    private fun clearErrors() {
        binding.dateInput.error = null
        binding.sleepInput.error = null
        binding.exerciseMinutesInput.error = null
        binding.moodInput.error = null
    }

    private fun setSaving(saving: Boolean) {
        binding.saveButton.isEnabled = !saving
        binding.deleteButton.isEnabled = !saving
        binding.cancelButton.isEnabled = !saving
        binding.exerciseTypeInput.isEnabled = !saving
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
        setSaving(true)
        scope.launch {
            runCatching { api.deleteRecord(id) }
                .onSuccess {
                    Toast.makeText(this@RecordFormActivity, "Record deleted", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                }
                .onFailure {
                    setSaving(false)
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
