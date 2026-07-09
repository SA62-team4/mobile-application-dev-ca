package sg.edu.nus.iss.wellness

import android.content.Context
import androidx.core.content.edit

/**
 * Stores the JWT access token for authenticated API calls.
 *
 * @author Tiong Zhong Cheng
 */
class TokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("wellness_auth", Context.MODE_PRIVATE)

    fun save(token: String, displayName: String, email: String, photoUrl: String? = null) {
        prefs.edit {
            putString("token", token)
            putString("displayName", displayName)
            putString("email", email)
            putString("photoUrl", photoUrl)
        }
    }

    fun token(): String? = prefs.getString("token", null)
    fun displayName(): String = prefs.getString("displayName", "User") ?: "User"
    fun email(): String = prefs.getString("email", "") ?: ""

    /** Google account picture URL captured at sign-in; empty for email/password logins. */
    fun photoUrl(): String = prefs.getString("photoUrl", "") ?: ""
    fun clear() = prefs.edit { clear() }
}

