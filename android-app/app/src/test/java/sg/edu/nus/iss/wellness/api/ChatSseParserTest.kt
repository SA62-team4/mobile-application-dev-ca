package sg.edu.nus.iss.wellness.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the chat SSE wire-format parser handles each event type and malformed input
 * without touching the network or Android framework.
 *
 * @author Tiong Zhong Cheng
 */
class ChatSseParserTest {

    @Test
    fun `non-data lines are ignored`() {
        assertNull(ChatSseParser.parseLine(": keep-alive"))
        assertNull(ChatSseParser.parseLine("event: message"))
        assertNull(ChatSseParser.parseLine(""))
    }

    @Test
    fun `blank data payload is ignored`() {
        assertNull(ChatSseParser.parseLine("data:   "))
    }

    @Test
    fun `token event carries text`() {
        val event = ChatSseParser.parseLine("""data: {"type":"token","text":"Hello"}""")
        assertEquals(ChatStreamEvent.Token("Hello"), event)
    }

    @Test
    fun `token event with missing text defaults to empty`() {
        val event = ChatSseParser.parse("""{"type":"token"}""")
        assertEquals(ChatStreamEvent.Token(""), event)
    }

    @Test
    fun `sources event maps title and snippet`() {
        val event = ChatSseParser.parse(
            """{"type":"sources","sources":[{"title":"Sleep","snippet":"Rest well"}]}"""
        ) as ChatStreamEvent.Sources
        assertEquals(1, event.sources.size)
        assertEquals(SourceSnippet("Sleep", "Rest well"), event.sources.first())
    }

    @Test
    fun `sources fields fall back to empty strings`() {
        val event = ChatSseParser.parse("""{"type":"sources","sources":[{}]}""") as ChatStreamEvent.Sources
        assertEquals(SourceSnippet("", ""), event.sources.first())
    }

    @Test
    fun `done event parses id metadata and sources`() {
        val event = ChatSseParser.parse(
            """{"type":"done","id":42,"modelName":"llama","createdAt":"2026-01-01T00:00:00Z",
               "sources":[{"title":"T","snippet":"S"}]}"""
        ) as ChatStreamEvent.Done
        assertEquals(42L, event.id)
        assertEquals("llama", event.modelName)
        assertEquals("2026-01-01T00:00:00Z", event.createdAt)
        assertEquals(1, event.sources.size)
    }

    @Test
    fun `done event tolerates null metadata and missing id`() {
        val event = ChatSseParser.parse("""{"type":"done","modelName":null,"createdAt":null}""") as ChatStreamEvent.Done
        assertEquals(0L, event.id)
        assertNull(event.modelName)
        assertNull(event.createdAt)
        assertTrue(event.sources.isEmpty())
    }

    @Test
    fun `error event uses provided message`() {
        val event = ChatSseParser.parse("""{"type":"error","message":"Boom"}""")
        assertEquals(ChatStreamEvent.Error("Boom"), event)
    }

    @Test
    fun `error event falls back to default message`() {
        val event = ChatSseParser.parse("""{"type":"error"}""") as ChatStreamEvent.Error
        assertEquals("Chatbot unavailable. Please retry.", event.message)
    }

    @Test
    fun `unknown type returns null`() {
        assertNull(ChatSseParser.parse("""{"type":"heartbeat"}"""))
    }

    @Test
    fun `malformed json returns null instead of throwing`() {
        assertNull(ChatSseParser.parse("""{not json"""))
    }
}
