package io.github.aljadvb.worldbuilder.generator

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.BadRequestException
import com.anthropic.errors.InternalServerException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.helpers.MessageAccumulator
import com.anthropic.models.messages.CacheControlEphemeral
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.TextBlockParam
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import com.anthropic.models.messages.StopReason as ApiStopReason

/**
 * Claude via the official Anthropic Java SDK: streaming, structured JSON output,
 * prompt caching of the stable system block, effort, and (optionally) server-side
 * fallbacks when a request is declined.
 */
class AnthropicProvider(
    apiKey: String,
    override val model: String = ModelInfo.OPUS_5_5.id,
    private val useFallbacks: Boolean = true,
    baseUrl: String? = null,
) : LlmProvider {
    override val displayName = "Anthropic Claude"

    private val client: AnthropicClient = AnthropicOkHttpClient.builder()
        .apiKey(apiKey)
        .apply { if (baseUrl != null) baseUrl(baseUrl) }
        .build()

    override fun generate(request: LlmRequest, cancel: CancelSignal, onProgress: (Int) -> Unit): LlmResult = try {
        stream(params(request, useFallbacks), cancel, onProgress)
    } catch (e: BadRequestException) {
        // Should the account not accept server-side fallbacks, run the request without them.
        if (useFallbacks && (e.message ?: "").contains("fallback", ignoreCase = true)) {
            stream(params(request, withFallbacks = false), cancel, onProgress)
        } else {
            throw translate(e)
        }
    } catch (e: CancelledException) {
        throw e
    } catch (e: Exception) {
        throw translate(e)
    }

    /** Builds the API request (internal for tests). */
    internal fun params(request: LlmRequest, withFallbacks: Boolean): MessageCreateParams {
        val b = MessageCreateParams.builder()
            .model(model)
            .maxTokens(request.maxTokens)
            .systemOfTextBlockParams(request.system.map { block ->
                TextBlockParam.builder().text(block.text).apply {
                    if (block.cache) cacheControl(CacheControlEphemeral.builder().build())
                }.build()
            })
        for (m in request.messages) {
            when (m.role) {
                LlmMessage.Role.USER -> b.addUserMessage(m.text)
                LlmMessage.Role.ASSISTANT -> b.addAssistantMessage(m.text)
            }
        }
        val output = OutputConfig.builder().effort(
            when (request.effort) {
                Effort.LOW -> OutputConfig.Effort.LOW
                Effort.MEDIUM -> OutputConfig.Effort.MEDIUM
                Effort.HIGH -> OutputConfig.Effort.HIGH
            },
        )
        request.jsonSchema?.let { schema ->
            val root = OutputSchemas.parse(schema) as JsonObject
            output.format(
                JsonOutputFormat.builder()
                    .type(JsonValue.from("json_schema"))
                    .schema(JsonOutputFormat.Schema.builder().putAllAdditionalProperties(root.mapValues { JsonValue.from(plain(it.value)) }).build())
                    .build(),
            )
        }
        b.outputConfig(output.build())
        if (withFallbacks) {
            b.putAdditionalHeader("anthropic-beta", FALLBACK_BETA)
            b.putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
        }
        return b.build()
    }

    private fun stream(params: MessageCreateParams, cancel: CancelSignal, onProgress: (Int) -> Unit): LlmResult {
        if (cancel.cancelled) throw CancelledException()
        val acc = MessageAccumulator.create()
        var chars = 0
        client.messages().createStreaming(params).use { response ->
            val it = response.stream().iterator()
            while (it.hasNext()) {
                if (cancel.cancelled) throw CancelledException()
                val event = it.next()
                acc.accumulate(event)
                event.contentBlockDelta().flatMap { d -> d.delta().text() }.ifPresent { t ->
                    chars += t.text().length
                    onProgress(chars)
                }
            }
        }
        return toResult(acc.message())
    }

    private fun toResult(message: Message): LlmResult {
        val text = message.content().joinToString("") { block -> block.text().map { it.text() }.orElse("") }
        val usage = message.usage().let {
            Usage(it.inputTokens(), it.outputTokens(), it.cacheReadInputTokens().orElse(0L), it.cacheCreationInputTokens().orElse(0L))
        }
        val stop = when (message.stopReason().orElse(null)) {
            ApiStopReason.END_TURN, ApiStopReason.STOP_SEQUENCE -> StopReason.END
            ApiStopReason.MAX_TOKENS, ApiStopReason.MODEL_CONTEXT_WINDOW_EXCEEDED -> StopReason.MAX_TOKENS
            ApiStopReason.REFUSAL -> StopReason.REFUSAL
            else -> StopReason.OTHER
        }
        val refusal = message.stopDetails().flatMap { it.explanation() }.orElse(null)
        return LlmResult(text, stop, usage, message.model().asString(), refusal)
    }

    private fun translate(e: Exception): LlmException = when (e) {
        is UnauthorizedException -> LlmException("Der API-Schlüssel wurde abgelehnt. Bitte unter „KI → Einstellungen“ prüfen.")
        is PermissionDeniedException -> LlmException("Der API-Schlüssel hat keinen Zugriff auf dieses Modell.")
        is RateLimitException -> LlmException("Zu viele Anfragen – bitte kurz warten und erneut versuchen.", retryable = true)
        is InternalServerException -> LlmException("Der KI-Dienst ist gerade überlastet oder gestört. Bitte später erneut versuchen.", retryable = true)
        is AnthropicServiceException -> LlmException("Fehler vom KI-Dienst (${e.statusCode()}): ${e.message}")
        is AnthropicIoException -> LlmException("Keine Verbindung zum KI-Dienst: ${e.message}", retryable = true)
        else -> LlmException("Unerwarteter Fehler: ${e.message}", cause = e)
    }

    companion object {
        const val FALLBACK_BETA = "server-side-fallback-2026-07-01"

        /** kotlinx JSON → plain Kotlin values for [JsonValue.from]. */
        fun plain(e: JsonElement): Any? = when (e) {
            is JsonNull -> null
            is JsonObject -> e.mapValues { plain(it.value) }
            is JsonArray -> e.map(::plain)
            is JsonPrimitive -> if (e.isString) e.content else e.booleanOrNull ?: e.longOrNull ?: e.doubleOrNull
        }
    }
}
