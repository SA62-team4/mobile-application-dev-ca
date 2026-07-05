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
