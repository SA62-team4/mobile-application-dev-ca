@file:Suppress("DEPRECATION")

package sg.edu.nus.iss.wellness

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.WindowInsets

/**
 * Applies system-bar insets so XML screens do not draw under cutouts or nav bars.
 *
 * @author SA62 Team
 */
object EdgeToEdge {
    fun apply(activity: Activity, root: View) {
        activity.window.statusBarColor = Color.TRANSPARENT
        activity.window.navigationBarColor = Color.WHITE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            var flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
            activity.window.decorView.systemUiVisibility = flags
        }

        val left = root.paddingLeft
        val top = root.paddingTop
        val right = root.paddingRight
        val bottom = root.paddingBottom

        root.setOnApplyWindowInsetsListener { view, insets ->
            val systemBars = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insets.getInsets(WindowInsets.Type.systemBars())
            } else {
                null
            }

            val insetLeft = systemBars?.left ?: insets.systemWindowInsetLeft
            val insetTop = systemBars?.top ?: insets.systemWindowInsetTop
            val insetRight = systemBars?.right ?: insets.systemWindowInsetRight
            val insetBottom = systemBars?.bottom ?: insets.systemWindowInsetBottom

            view.setPadding(
                left + insetLeft,
                top + insetTop,
                right + insetRight,
                bottom + insetBottom
            )
            insets
        }
        root.requestApplyInsets()
    }
}
