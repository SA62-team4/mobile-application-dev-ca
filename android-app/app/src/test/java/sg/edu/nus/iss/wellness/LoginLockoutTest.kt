package sg.edu.nus.iss.wellness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the login-throttling (S-04) message formatting: a retry window is shown
 * in minutes/seconds when the backend provides one, with a generic fallback
 * otherwise.
 *
 * @author Chua Wei Yi Justin
 */
class LoginLockoutTest {

    @Test
    fun `formats a multi-minute window`() {
        assertEquals("Too many failed attempts. Try again in 3m 0s.", LoginLockout.message(180))
    }

    @Test
    fun `formats a sub-minute window`() {
        assertEquals("Too many failed attempts. Try again in 45s.", LoginLockout.message(45))
    }

    @Test
    fun `formats minutes and seconds together`() {
        assertEquals("Too many failed attempts. Try again in 2m 5s.", LoginLockout.message(125))
    }

    @Test
    fun `falls back when no window is provided`() {
        val expected = "Too many failed attempts. Please wait a few minutes and try again."
        assertEquals(expected, LoginLockout.message(null))
        assertEquals(expected, LoginLockout.message(0))
        assertEquals(expected, LoginLockout.message(-5))
    }

    @Test
    fun `message always names the throttle cause`() {
        assertTrue(LoginLockout.message(30).startsWith("Too many failed attempts"))
    }
}
