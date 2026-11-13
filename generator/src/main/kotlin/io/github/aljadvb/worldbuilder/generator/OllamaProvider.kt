package io.github.aljadvb.worldbuilder.generator

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * A local Ollama server (e.g. in the home network): one non-streaming call to /api/chat,
 * with the output schema as "format" so the answer is JSON of that shape.
 *
 * [contextTokens] is sent as num_ctx: Ollama's small default context would silently cut off
 * the long system prompt with the material catalog.
 */
class OllamaProvider(
    baseUrl: String = DEFAULT_URL,
    override val model: String = DEFAULT_MODEL,
    private val readTimeoutSeconds: Int = DEFAULT_TIMEOUT_SECONDS,
    private val contextTokens: Int = DEFAULT_CONTEXT_TOKENS,
    private val systemPrefix: String = SYSTEM_PREFIX,
) : LlmProvider {
    override val displayName = "Ollama (lokal)"

    private val baseUrl = baseUrl.trim().trimEnd('/')
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(readTimeoutSeconds.toLong(), TimeUnit.SECONDS)
        .build()

    /** The JSON body of the request (visible for tests). */
    internal fun body(request: LlmRequest): JsonObject = buildJsonObject {
        put("model", model)
        put("stream", false)
        put("think", false)
        request.jsonSchema?.let { put("format", json.parseToJsonElement(it)) }
        put("options", buildJsonObject { put("num_ctx", contextTokens) })
        put("messages", buildJsonArray {
            add(buildJsonObject {
                put("role", "system")
                put("content", (listOf(systemPrefix) + request.system.map { it.text }).filter { it.isNotBlank() }.joinToString("\n\n"))
            })
            request.messages.forEach { m ->
                add(buildJsonObject {
                    put("role", if (m.role == LlmMessage.Role.USER) "user" else "assistant")
                    put("content", m.text)
                })
            }
        })
    }

    override fun generate(request: LlmRequest, cancel: CancelSignal, onProgress: (Int) -> Unit): LlmResult {
        val http = try {
            Request.Builder().url("$baseUrl/api/chat").post(body(request).toString().toRequestBody(JSON)).build()
        } catch (e: IllegalArgumentException) {
            throw LlmException("Die Ollama-Adresse „$baseUrl“ ist ungültig. Beispiel: $DEFAULT_URL")
        }
        val call = client.newCall(http)
        val done = CountDownLatch(1)
        var text: String? = null
        var status = 0
        var failure: IOException? = null
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { failure = e; done.countDown() }
            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use { status = it.code; text = it.body?.string().orEmpty() }
                } catch (e: IOException) {
                    failure = e
                }
                done.countDown()
            }
        })
        // The call blocks for minutes; look at the cancel signal meanwhile.
        while (!done.await(200, TimeUnit.MILLISECONDS)) {
            if (cancel.cancelled) { call.cancel(); throw CancelledException() }
        }
        if (cancel.cancelled) throw CancelledException()
        failure?.let { throw translate(it) }
        val answer = text.orEmpty()
        if (status !in 200..299) throw httpError(status, answer)

        val obj = try {
            json.parseToJsonElement(answer).jsonObject
        } catch (e: Exception) {
            throw LlmException("Der Ollama-Server hat keine lesbare Antwort geschickt.", cause = e)
        }
        val content = (obj["message"] as? JsonObject)?.get("content")?.jsonPrimitive?.content.orEmpty()
        if (content.isBlank()) throw LlmException("Der Ollama-Server hat eine leere Antwort geschickt. Kann das Modell „$model“ strukturierte Ausgaben?")
        onProgress(content.length)
        val reason = obj["done_reason"]?.jsonPrimitive?.content
        return LlmResult(
            text = content,
            stopReason = if (reason == "length") StopReason.MAX_TOKENS else StopReason.END,
            usage = Usage(
                inputTokens = obj["prompt_eval_count"]?.jsonPrimitive?.longOrNull ?: 0,
                outputTokens = obj["eval_count"]?.jsonPrimitive?.longOrNull ?: 0,
            ),
            model = obj["model"]?.jsonPrimitive?.content ?: model,
        )
    }

    private fun translate(e: IOException): LlmException = when {
        // Read timeout: the server is there but too slow.
        e is SocketTimeoutException && e.message?.contains("connect", ignoreCase = true) != true ->
            LlmException("Der Ollama-Server hat nicht innerhalb von $readTimeoutSeconds Sekunden geantwortet. Das Modell braucht länger – erhöhe das Zeitlimit unter „KI → Einstellungen …“ oder wähle eine kürzere Länge.", cause = e)
        e is ConnectException || e is UnknownHostException || e is NoRouteToHostException || e is SocketTimeoutException || e is InterruptedIOException ->
            LlmException("Der Ollama-Server unter $baseUrl ist nicht erreichbar. Bist du im Heimnetz? Läuft Ollama auf dem Rechner?", retryable = true, cause = e)
        else -> LlmException("Verbindung zum Ollama-Server unter $baseUrl abgebrochen: ${e.message ?: e.javaClass.simpleName}. Bist du im Heimnetz?", retryable = true, cause = e)
    }

    private fun httpError(status: Int, answer: String): LlmException {
        val detail = runCatching { json.parseToJsonElement(answer).jsonObject["error"]?.jsonPrimitive?.content }.getOrNull() ?: answer.take(200)
        return if (status == 404) {
            LlmException("Der Ollama-Server kennt das Modell „$model“ nicht. Lade es dort mit „ollama pull $model“ oder ändere das Modell unter „KI → Einstellungen …“. ($detail)")
        } else {
            LlmException("Der Ollama-Server meldet einen Fehler ($status): $detail")
        }
    }

    companion object {
        const val DEFAULT_URL = "http://192.168.178.111:11434"
        const val DEFAULT_MODEL = "qwen3.6:35b"
        const val DEFAULT_TIMEOUT_SECONDS = 300
        const val DEFAULT_CONTEXT_TOKENS = 32768
        const val SYSTEM_PREFIX = "Du bist Abenteuer-Autor für Journeys in Middle-earth, schreibe auf Deutsch im Stil Tolkiens."
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val json = Json { ignoreUnknownKeys = true }
    }
}
