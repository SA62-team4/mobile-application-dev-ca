package sg.edu.nus.iss.wellness.ui

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * Verifies user-facing error messages produced from network/HTTP failures, kept as a pure
 * function so it can be tested without the Android framework.
 *
 * @author Tiong Zhong Cheng
 */
class ApiErrorMessageTest {

    private fun httpError(code: Int): HttpException {
        val body = "error".toResponseBody("text/plain".toMediaType())
        return HttpException(Response.error<Any>(code, body))
    }

    @Test
    fun `auth failures ask the user to re-login`() {
        assertEquals("Sync failed. Please log out and log in again.", apiErrorMessage("Sync failed", httpError(401)))
        assertEquals("Sync failed. Please log out and log in again.", apiErrorMessage("Sync failed", httpError(403)))
    }

    @Test
    fun `service unavailable points at Python and Ollama`() {
        assertEquals("Sync failed. Check Python AI service and Ollama.", apiErrorMessage("Sync failed", httpError(503)))
    }

    @Test
    fun `other http codes are surfaced verbatim`() {
        assertEquals("Sync failed. Backend returned HTTP 500.", apiErrorMessage("Sync failed", httpError(500)))
    }

    @Test
    fun `io failures mention backend reachability`() {
        val message = apiErrorMessage("Sync failed", IOException("timeout"))
        assertEquals("Sync failed. Check that the backend is reachable from the emulator.", message)
    }

    @Test
    fun `unexpected errors include the exception type`() {
        val message = apiErrorMessage("Sync failed", IllegalStateException("boom"))
        assertTrue(message.contains("IllegalStateException"))
    }
}
