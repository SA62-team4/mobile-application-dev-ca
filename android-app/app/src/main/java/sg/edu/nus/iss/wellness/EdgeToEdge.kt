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
 * @author Bryan Phang Wai Yip, Tiong Zhong Cheng
 */
object EdgeToEdge {
    fun apply(activity: Activity, root: View) {
        activity.window.statusBarColor = Color.TRANSPARENT
        activity.window.navigationBarColor = Color.WHITE

        // minSdk 26 (Android O) guarantees both light-bar flags are available.
        activity.window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        val left = root.paddingLeft
        val top = root.paddingTop
        val right = root.paddingRight
        val bottom = root.paddingBottom

        root.setOnApplyWindowInsetsListener { view, insets ->
            val insetLeft: Int
            val insetTop: Int
            val insetRight: Int
            val insetBottom: Int
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val systemBars = insets.getInsets(WindowInsets.Type.systemBars())
                insetLeft = systemBars.left
                insetTop = systemBars.top
                insetRight = systemBars.right
                insetBottom = systemBars.bottom
            } else {
                insetLeft = insets.systemWindowInsetLeft
                insetTop = insets.systemWindowInsetTop
                insetRight = insets.systemWindowInsetRight
                insetBottom = insets.systemWindowInsetBottom
            }

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
