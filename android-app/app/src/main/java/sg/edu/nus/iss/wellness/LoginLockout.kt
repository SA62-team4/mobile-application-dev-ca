package sg.edu.nus.iss.wellness

/**
 * Pure formatting helpers for the login-throttling (S-04) UX, kept free of Android
 * dependencies so they can be unit-tested on the JVM.
 *
 * @author Chua Wei Yi Justin
 */
internal object LoginLockout {

    /**
     * Human-readable lockout message. Uses the backend's Retry-After window when
     * available, otherwise falls back to a generic "wait a few minutes" message.
     */
    fun message(retryAfterSeconds: Long?): String {
        if (retryAfterSeconds == null || retryAfterSeconds <= 0) {
            return "Too many failed attempts. Please wait a few minutes and try again."
        }
        val minutes = retryAfterSeconds / 60
        val remainder = retryAfterSeconds % 60
        val window = if (minutes > 0) "${minutes}m ${remainder}s" else "${remainder}s"
        return "Too many failed attempts. Try again in $window."
    }
}
