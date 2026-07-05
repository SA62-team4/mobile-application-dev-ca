package sg.edu.nus.iss.wellness.records

import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import sg.edu.nus.iss.wellness.ApiClient
import sg.edu.nus.iss.wellness.R
import sg.edu.nus.iss.wellness.TokenStore
import sg.edu.nus.iss.wellness.api.WellnessRecordRequest
import java.time.LocalDate
import java.util.Calendar

/**
 * Form activity for adding or editing a wellness record.
 *
 * Start without extras to add a new record.
 * Start with extras to edit an existing record.
 *
 * Returns RESULT_OK on successful save.
 */
class RecordFormActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RECORD_ID = "extra_record_id"
        const val EXTRA_RECORD_DATE = "extra_record_date"
        const val EXTRA_SLEEP = "extra_sleep"
        const val EXTRA_EXERCISE_TYPE = "extra_exercise_type"
        const val EXTRA_EXERCISE_MINUTES = "extra_exercise_minutes"
        const val EXTRA_MOOD = "extra_mood"
        const val EXTRA_NOTES = "extra_notes"

        private const val TAG = "RecordFormActivity"
    }

    private lateinit var tokenStore: TokenStore

    private lateinit var saveButton: Button
    private lateinit var cancelButton: Button
    private lateinit var dateText: TextView
    private lateinit var sleepInput: EditText
    private lateinit var exerciseTypeInput: EditText
    private lateinit var exerciseMinutesInput: EditText
    private lateinit var moodInput: EditText
    private lateinit var notesInput: EditText
    private lateinit var progressBar: ProgressBar

    private var recordId: Long? = null
    private var selectedDate: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tokenStore = TokenStore(this)

        setContentView(R.layout.activity_record_form)

        saveButton = findViewById(R.id.saveButton)
        cancelButton = findViewById(R.id.cancelButton)
        dateText = findViewById(R.id.recordDateText)
        sleepInput = findViewById(R.id.sleepInput)
        exerciseTypeInput = findViewById(R.id.exerciseTypeInput)
        exerciseMinutesInput = findViewById(R.id.exerciseMinutesInput)
        moodInput = findViewById(R.id.moodInput)
        notesInput = findViewById(R.id.notesInput)
        progressBar = findViewById(R.id.formProgress)

        loadEditExtras()

        dateText.setOnClickListener {
            openDatePicker()
        }

        cancelButton.setOnClickListener {
            finish()
        }

        saveButton.setOnClickListener {
            validateAndSave()
        }
    }

    private fun loadEditExtras() {
        recordId = intent.getLongExtra(EXTRA_RECORD_ID, -1L).takeIf { it != -1L }

        intent.getStringExtra(EXTRA_RECORD_DATE)?.let {
            selectedDate = it
            dateText.text = it
        }

        intent.getDoubleExtra(EXTRA_SLEEP, Double.NaN)
            .takeIf { !it.isNaN() }
            ?.let {
                sleepInput.setText(it.toString())
            }

        intent.getStringExtra(EXTRA_EXERCISE_TYPE)?.let {
            exerciseTypeInput.setText(it)
        }

        intent.getIntExtra(EXTRA_EXERCISE_MINUTES, -1)
            .takeIf { it >= 0 }
            ?.let {
                exerciseMinutesInput.setText(it.toString())
            }

        intent.getIntExtra(EXTRA_MOOD, -1)
            .takeIf { it >= 1 }
            ?.let {
                moodInput.setText(it.toString())
            }

        intent.getStringExtra(EXTRA_NOTES)?.let {
            notesInput.setText(it)
        }
    }

    private fun openDatePicker() {
        val initialDate = selectedDate
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

        val calendar = Calendar.getInstance()

        val year = initialDate?.year ?: calendar.get(Calendar.YEAR)
        val month = initialDate?.monthValue?.minus(1) ?: calendar.get(Calendar.MONTH)
        val day = initialDate?.dayOfMonth ?: calendar.get(Calendar.DAY_OF_MONTH)

        val datePicker = DatePickerDialog(
            this,
            { _, selectedYear, selectedMonth, selectedDay ->
                val date = LocalDate.of(
                    selectedYear,
                    selectedMonth + 1,
                    selectedDay
                ).toString()

                selectedDate = date
                dateText.text = date
            },
            year,
            month,
            day
        )

        datePicker.show()
    }

    private fun validateAndSave() {
        clearFieldErrors()

        val date = selectedDate

        if (date.isNullOrBlank()) {
            toastError(getString(R.string.record_date_required))
            return
        }

        val sleep = sleepInput.text.toString().trim().toDoubleOrNull()

        if (sleep == null || sleep < 0.0 || sleep > 24.0) {
            sleepInput.error = getString(R.string.error_sleep_range)
            return
        }

        val exerciseType = exerciseTypeInput.text.toString().trim().ifBlank {
            null
        }

        val exerciseMinutesText = exerciseMinutesInput.text.toString().trim()

        val exerciseMinutes = if (exerciseMinutesText.isBlank()) {
            0
        } else {
            exerciseMinutesText.toIntOrNull()
        }

        if (exerciseMinutes == null || exerciseMinutes < 0) {
            exerciseMinutesInput.error = getString(R.string.error_exercise_minutes)
            return
        }

        val mood = moodInput.text.toString().trim().toIntOrNull()

        if (mood == null || mood < 1 || mood > 5) {
            moodInput.error = getString(R.string.error_mood_range)
            return
        }

        val notes = notesInput.text.toString().trim().ifBlank {
            null
        }

        val request = WellnessRecordRequest(
            recordDate = date,
            sleepHours = sleep,
            exerciseType = exerciseType,
            exerciseMinutes = exerciseMinutes,
            moodScore = mood,
            notes = notes
        )

        saveRecord(request)
    }

    private fun saveRecord(request: WellnessRecordRequest) {
        setLoading(true)

        lifecycleScope.launch {
            val api = ApiClient.create(tokenStore)

            try {
                if (recordId == null) {
                    api.createRecord(request)
                } else {
                    api.updateRecord(recordId!!, request)
                }

                toastSuccess(getString(R.string.toast_record_saved))
                setResult(RESULT_OK)
                finish()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save wellness record", e)
                toastError(getString(R.string.toast_save_failed))
            } finally {
                setLoading(false)
            }
        }
    }

    private fun clearFieldErrors() {
        sleepInput.error = null
        exerciseMinutesInput.error = null
        moodInput.error = null
    }

    private fun setLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE

        saveButton.isEnabled = !isLoading
        cancelButton.isEnabled = !isLoading
        dateText.isEnabled = !isLoading
        sleepInput.isEnabled = !isLoading
        exerciseTypeInput.isEnabled = !isLoading
        exerciseMinutesInput.isEnabled = !isLoading
        moodInput.isEnabled = !isLoading
        notesInput.isEnabled = !isLoading
    }

    private fun toastError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun toastSuccess(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
