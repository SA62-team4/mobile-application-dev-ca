package sg.edu.nus.iss.wellness.records

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import retrofit2.HttpException
import sg.edu.nus.iss.wellness.EdgeToEdge
import sg.edu.nus.iss.wellness.LoginActivity
import sg.edu.nus.iss.wellness.R
import sg.edu.nus.iss.wellness.TokenStore
import sg.edu.nus.iss.wellness.api.ApiClient
import sg.edu.nus.iss.wellness.api.ApiService
import sg.edu.nus.iss.wellness.api.WellnessRecordRequest
import sg.edu.nus.iss.wellness.databinding.ActivityRecordFormBinding
import java.io.IOException
import java.time.LocalDate

/**
 * Add/edit screen for wellness records.
 *
 * @author SA62 Team
 */
class RecordFormActivity : AppCompatActivity() {

    private val scope = MainScope()
    private lateinit var binding: ActivityRecordFormBinding
    private lateinit var tokenStore: TokenStore
    private lateinit var api: ApiService
    private var recordId: Long? = null
    private var selectedDate: LocalDate = LocalDate.now()

    companion object {
        const val EXTRA_RECORD_ID = "extra_record_id"
        const val EXTRA_RECORD_DATE = "extra_record_date"
        const val EXTRA_SLEEP = "extra_sleep"
        const val EXTRA_EXERCISE_TYPE = "extra_exercise_type"
        const val EXTRA_EXERCISE_MINUTES = "extra_exercise_minutes"
        const val EXTRA_MOOD = "extra_mood"
        const val EXTRA_NOTES = "extra_notes"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tokenStore = TokenStore(this)
        if (tokenStore.token().isNullOrBlank()) {
            goToLogin()
            return
        }

        api = ApiClient.create(tokenStore) { runOnUiThread { goToLogin() } }
        binding = ActivityRecordFormBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdge.apply(this, binding.rootForm)

        recordId = if (intent.hasExtra(EXTRA_RECORD_ID)) {
            intent.getLongExtra(EXTRA_RECORD_ID, -1L).takeIf { it > 0L }
        } else {
            null
        }

        populateFromIntent(intent)
        bindActions()
    }

    private fun populateFromIntent(intent: Intent) {
        val dateText = intent.getStringExtra(EXTRA_RECORD_DATE)
        selectedDate = runCatching { LocalDate.parse(dateText) }.getOrDefault(LocalDate.now())
        binding.recordDateText.text = selectedDate.toString()

        if (recordId != null) {
            binding.sleepInput.setText(intent.getDoubleExtra(EXTRA_SLEEP, 0.0).toString())
            binding.exerciseTypeInput.setText(intent.getStringExtra(EXTRA_EXERCISE_TYPE).orEmpty())
            binding.exerciseMinutesInput.setText(intent.getIntExtra(EXTRA_EXERCISE_MINUTES, 0).toString())
            binding.moodInput.setText(intent.getIntExtra(EXTRA_MOOD, 3).toString())
            binding.notesInput.setText(intent.getStringExtra(EXTRA_NOTES).orEmpty())
        }
    }

    private fun bindActions() {
        binding.recordDateText.setOnClickListener { showDatePicker() }
        binding.cancelButton.setOnClickListener { finish() }
        binding.saveButton.setOnClickListener { saveRecord() }
    }

    private fun showDatePicker() {
        DatePickerDialog(
            this,
            { _, year, month, day ->
                selectedDate = LocalDate.of(year, month + 1, day)
                binding.recordDateText.text = selectedDate.toString()
                binding.recordDateText.error = null
            },
            selectedDate.year,
            selectedDate.monthValue - 1,
            selectedDate.dayOfMonth
        ).show()
    }

    private fun saveRecord() {
        val request = buildRequestOrNull() ?: return
        setSaving(true)

        scope.launch {
            runCatching {
                val id = recordId
                if (id == null) {
                    api.createRecord(request)
                } else {
                    api.updateRecord(id, request)
                }
            }.onSuccess {
                Toast.makeText(this@RecordFormActivity, R.string.toast_record_saved, Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            }.onFailure { throwable ->
                setSaving(false)
                Toast.makeText(this@RecordFormActivity, apiErrorMessage(throwable), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun buildRequestOrNull(): WellnessRecordRequest? {
        clearErrors()

        val sleepHours = binding.sleepInput.text.toString().trim().toDoubleOrNull()
        val exerciseMinutes = binding.exerciseMinutesInput.text.toString().trim().toIntOrNull()
        val moodScore = binding.moodInput.text.toString().trim().toIntOrNull()

        var valid = true
        if (binding.recordDateText.text.isNullOrBlank()) {
            binding.recordDateText.error = getString(R.string.record_date_required)
            valid = false
        }
        if (sleepHours == null || sleepHours < 0.0 || sleepHours > 24.0) {
            binding.sleepInput.error = getString(R.string.error_sleep_range)
            valid = false
        }
        if (exerciseMinutes == null || exerciseMinutes < 0) {
            binding.exerciseMinutesInput.error = getString(R.string.error_exercise_minutes)
            valid = false
        }
        if (moodScore == null || moodScore !in 1..5) {
            binding.moodInput.error = getString(R.string.error_mood_range)
            valid = false
        }
        if (!valid) return null

        return WellnessRecordRequest(
            recordDate = selectedDate.toString(),
            sleepHours = sleepHours!!,
            exerciseType = binding.exerciseTypeInput.text.toString().trim().ifBlank { null },
            exerciseMinutes = exerciseMinutes!!,
            moodScore = moodScore!!,
            notes = binding.notesInput.text.toString().trim().ifBlank { null }
        )
    }

    private fun clearErrors() {
        binding.recordDateText.error = null
        binding.sleepInput.error = null
        binding.exerciseMinutesInput.error = null
        binding.moodInput.error = null
    }

    private fun setSaving(saving: Boolean) {
        binding.formProgress.visibility = if (saving) View.VISIBLE else View.GONE
        binding.saveButton.isEnabled = !saving
        binding.cancelButton.isEnabled = !saving
    }

    private fun apiErrorMessage(throwable: Throwable): String = when (throwable) {
        is HttpException -> "Save failed. Backend returned HTTP ${throwable.code()}."
        is IOException -> getString(R.string.toast_save_failed)
        else -> throwable.message ?: getString(R.string.toast_save_failed)
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
