package io.github.aljadvb.worldbuilder.generator

import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.InetSocketAddress
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OllamaProviderTest {
    private val request = LlmRequest(
        system = listOf(SystemBlock("Regeln und Katalog.")),
        messages = listOf(LlmMessage(LlmMessage.Role.USER, "Eine Idee.")),
        jsonSchema = """{"type":"object","properties":{"title":{"type":"string"}},"required":["title"]}""",
    )

    /** A fake Ollama server answering /api/chat with [status] and [answer]; [seen] receives the request body. */
    private fun server(status: Int, answer: String, delayMs: Long = 0, seen: (String) -> Unit = {}): HttpServer =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/api/chat") { ex ->
                seen(ex.requestBody.readBytes().toString(Charsets.UTF_8))
                if (delayMs > 0) Thread.sleep(delayMs)
                val bytes = answer.toByteArray()
                runCatching { ex.sendResponseHeaders(status, bytes.size.toLong()); ex.responseBody.use { it.write(bytes) } }
            }
            executor = java.util.concurrent.Executors.newCachedThreadPool()
            start()
        }

    private fun HttpServer.url() = "http://127.0.0.1:${address.port}"

    @Test
    fun `sends model, schema and prompts and reads message content`() {
        var body = ""
        val s = server(200, """{"model":"qwen3.6:35b","message":{"role":"assistant","content":"{\"title\":\"Der Weg\"}"},"done":true,"done_reason":"stop","prompt_eval_count":120,"eval_count":30}""") { body = it }
        try {
            val r = OllamaProvider(s.url()).generate(request)
            assertEquals("""{"title":"Der Weg"}""", r.text)
            assertEquals(StopReason.END, r.stopReason)
            assertEquals(Usage(inputTokens = 120, outputTokens = 30), r.usage)
            val sent = Json.parseToJsonElement(body).jsonObject
            assertEquals("qwen3.6:35b", sent["model"]!!.jsonPrimitive.content)
            assertEquals(false, sent["stream"]!!.jsonPrimitive.boolean)
            assertEquals(false, sent["think"]!!.jsonPrimitive.boolean)
            assertEquals("object", sent["format"]!!.jsonObject["type"]!!.jsonPrimitive.content)
            assertEquals(32768, sent["options"]!!.jsonObject["num_ctx"]!!.jsonPrimitive.content.toInt())
            val messages = sent["messages"]!!.jsonArray.map { it.jsonObject }
            assertEquals(listOf("system", "user"), messages.map { it["role"]!!.jsonPrimitive.content })
            val system = messages[0]["content"]!!.jsonPrimitive.content
            assertTrue(system.startsWith("Du bist Abenteuer-Autor für Journeys in Middle-earth, schreibe auf Deutsch im Stil Tolkiens."))
            assertTrue(system.endsWith("Regeln und Katalog."))
        } finally { s.stop(0) }
    }

    @Test
    fun `unreachable server asks about the home network`() {
        val s = server(200, "{}"); val url = s.url(); s.stop(0)
        val e = assertFailsWith<LlmException> { OllamaProvider(url).generate(request) }
        assertTrue("nicht erreichbar" in e.message!! && "Bist du im Heimnetz?" in e.message!!, e.message)
    }

    @Test
    fun `unknown model, slow server, length stop and cancel`() {
        val missing = server(404, """{"error":"model 'x' not found"}""")
        val slow = server(200, "{}", delayMs = 3000)
        val long = server(200, """{"message":{"content":"{"},"done_reason":"length"}""")
        try {
            assertTrue("ollama pull x" in assertFailsWith<LlmException> { OllamaProvider(missing.url(), model = "x").generate(request) }.message!!)
            assertTrue("nicht innerhalb von 1 Sekunden" in assertFailsWith<LlmException> { OllamaProvider(slow.url(), readTimeoutSeconds = 1).generate(request) }.message!!)
            assertEquals(StopReason.MAX_TOKENS, OllamaProvider(long.url()).generate(request).stopReason)
            val cancel = CancelSignal()
            thread { Thread.sleep(300); cancel.cancel() }
            assertFailsWith<CancelledException> { OllamaProvider(slow.url()).generate(request, cancel) }
        } finally { missing.stop(0); slow.stop(0); long.stop(0) }
    }

    @Test
    fun `the real scenario schema is sent as format`() {
        val sent = OllamaProvider().body(LlmRequest(emptyList(), emptyList(), jsonSchema = OutputSchemas.scenario))
        assertTrue(sent["format"]!!.jsonObject.isNotEmpty())
    }
}
