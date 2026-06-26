package sg.edu.nus.iss.wellness

import android.content.Context

/**
 * Stores the JWT access token for authenticated API calls.
 *
 * @author SA62 Team
 */
class TokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("wellness_auth", Context.MODE_PRIVATE)

    fun save(token: String, displayName: String, email: String) {
        prefs.edit()
            .putString("token", token)
            .putString("displayName", displayName)
            .putString("email", email)
            .apply()
    }

    fun token(): String? = prefs.getString("token", null)
    fun displayName(): String = prefs.getString("displayName", "User") ?: "User"
    fun email(): String = prefs.getString("email", "") ?: ""
    fun clear() = prefs.edit().clear().apply()
}

