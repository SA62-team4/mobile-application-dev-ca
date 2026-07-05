package sg.edu.nus.iss.wellness.records

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.FrameLayout

/**
 * Minimal stub RecordFormActivity so the app compiles while the full form is implemented.
 * This activity will return RESULT_OK when the "Save (stub)" button is pressed.
 */
class RecordFormActivity : Activity() {

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

        // Try to use the real layout if it exists; otherwise fall back to a simple programmatic view.
        try {
            setContentView(R.layout.activity_record_form)
        } catch (e: Exception) {
            val btn = Button(this).apply {
                text = "Save (stub)"
                setOnClickListener {
                    setResult(Activity.RESULT_OK)
                    finish()
                }
            }
            val container = FrameLayout(this)
            container.addView(btn)
            setContentView(container)
        }
    }
}
