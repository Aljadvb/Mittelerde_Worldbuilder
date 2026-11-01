package io.github.aljadvb.worldbuilder.generator

import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Runs the real SDK against a local server that answers like the Messages API (no costs). */
class AnthropicProviderTest {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val bodies = mutableListOf<String>()
    private val betas = mutableListOf<String?>()
    private var status = 200
    private var stopReason = "end_turn"
    private val answer = """{"title":"Nebel","n":1}"""

    init {
        server.createContext("/v1/messages") { ex ->
            bodies += ex.requestBody.readBytes().decodeToString()
            betas += ex.requestHeaders.getFirst("anthropic-beta")
            if (status != 200) {
                val err = """{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}""".toByteArray()
                ex.responseHeaders.add("content-type", "application/json")
                ex.sendResponseHeaders(status, err.size.toLong()); ex.responseBody.use { it.write(err) }
                return@createContext
            }
            val chunks = answer.chunked(7).joinToString("") { c ->
                val esc = Json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(c))
                "event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":$esc}}\n\n"
            }
            val sse = """event: message_start
data: {"type":"message_start","message":{"id":"msg_1","type":"message","role":"assistant","model":"claude-opus-5-5","content":[],"stop_reason":null,"stop_sequence":null,"usage":{"input_tokens":120,"output_tokens":1,"cache_read_input_tokens":3000,"cache_creation_input_tokens":0}}}

event: content_block_start
data: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}

""" + chunks + """event: content_block_stop
data: {"type":"content_block_stop","index":0}

event: message_delta
data: {"type":"message_delta","delta":{"stop_reason":"$stopReason","stop_sequence":null},"usage":{"output_tokens":40}}

event: message_stop
data: {"type":"message_stop"}

"""
            val bytes = sse.toByteArray()
            ex.responseHeaders.add("content-type", "text/event-stream")
            ex.sendResponseHeaders(200, bytes.size.toLong()); ex.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    @AfterTest
    fun stop() = server.stop(0)

    private fun provider(fallbacks: Boolean = true) =
        AnthropicProvider("test-key", baseUrl = "http://127.0.0.1:${server.address.port}", useFallbacks = fallbacks)

    private val request = LlmRequest(
        system = listOf(SystemBlock("Regeln und Material", cache = true), SystemBlock("Zusatz")),
        messages = listOf(LlmMessage(LlmMessage.Role.USER, "Entwirf etwas"), LlmMessage(LlmMessage.Role.ASSISTANT, "{}"), LlmMessage(LlmMessage.Role.USER, "Korrigiere")),
        jsonSchema = OutputSchemas.story,
        maxTokens = 16_000,
        effort = Effort.MEDIUM,
    )

    @Test
    fun streamsTheAnswerAndReportsUsage() {
        var progress = 0
        val result = provider().generate(request) { progress = it }
        assertEquals(answer, result.text)
        assertEquals(StopReason.END, result.stopReason)
        assertEquals(answer.length, progress)
        assertEquals(Usage(120, 40, 3000, 0), result.usage)
        assertEquals("claude-opus-5-5", result.model)
    }

    @Test
    fun sendsCachingSchemaEffortAndFallbacks() {
        provider().generate(request)
        val body = Json.parseToJsonElement(bodies.single()).jsonObject
        assertEquals("claude-opus-5-5", body.getValue("model").jsonPrimitive.content)
        assertEquals(16000, body.getValue("max_tokens").jsonPrimitive.content.toInt())
        val system = body.getValue("system").jsonArray
        assertEquals("ephemeral", system[0].jsonObject.getValue("cache_control").jsonObject.getValue("type").jsonPrimitive.content)
        assertTrue("cache_control" !in system[1].jsonObject)
        assertEquals(listOf("user", "assistant", "user"), body.getValue("messages").jsonArray.map { it.jsonObject.getValue("role").jsonPrimitive.content })
        val output = body.getValue("output_config").jsonObject
        assertEquals("medium", output.getValue("effort").jsonPrimitive.content)
        val format = output.getValue("format").jsonObject
        assertEquals("json_schema", format.getValue("type").jsonPrimitive.content)
        assertEquals(Json.parseToJsonElement(OutputSchemas.story), format.getValue("schema") as JsonObject)
        assertEquals("default", body.getValue("fallbacks").jsonPrimitive.content)
        assertTrue(betas.single()!!.contains(AnthropicProvider.FALLBACK_BETA))
        assertTrue("thinking" !in body) // Opus 5.5 thinks adaptively by default
    }

    @Test
    fun fallbacksCanBeSwitchedOff() {
        provider(fallbacks = false).generate(request)
        assertTrue("fallbacks" !in Json.parseToJsonElement(bodies.single()).jsonObject)
    }

    @Test
    fun refusalAndCutOffAreMapped() {
        stopReason = "refusal"
        assertEquals(StopReason.REFUSAL, provider().generate(request).stopReason)
        stopReason = "max_tokens"
        assertEquals(StopReason.MAX_TOKENS, provider().generate(request).stopReason)
    }

    @Test
    fun wrongKeyGivesAClearMessage() {
        status = 401
        val e = assertFailsWith<LlmException> { provider().generate(request) }
        assertTrue("API-Schlüssel" in e.message!!, e.message)
    }

    @Test
    fun cancelStopsBeforeSending() {
        val signal = CancelSignal().apply { cancel() }
        assertFailsWith<CancelledException> { provider().generate(request, signal) }
        assertTrue(bodies.isEmpty())
    }
}
