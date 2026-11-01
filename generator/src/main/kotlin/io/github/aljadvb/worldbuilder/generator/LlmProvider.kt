package io.github.aljadvb.worldbuilder.generator

/** One block of the system prompt; [cache] marks the end of a cacheable prefix. */
data class SystemBlock(val text: String, val cache: Boolean = false)

data class LlmMessage(val role: Role, val text: String) {
    enum class Role { USER, ASSISTANT }
}

enum class Effort { LOW, MEDIUM, HIGH }

/** A provider-neutral request. [jsonSchema] constrains the answer to JSON of that shape. */
data class LlmRequest(
    val system: List<SystemBlock>,
    val messages: List<LlmMessage>,
    val jsonSchema: String? = null,
    val maxTokens: Long = 32_000,
    val effort: Effort = Effort.HIGH,
)

data class Usage(
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val cacheReadTokens: Long = 0,
    val cacheWriteTokens: Long = 0,
) {
    operator fun plus(o: Usage) = Usage(inputTokens + o.inputTokens, outputTokens + o.outputTokens, cacheReadTokens + o.cacheReadTokens, cacheWriteTokens + o.cacheWriteTokens)
}

enum class StopReason { END, MAX_TOKENS, REFUSAL, OTHER }

data class LlmResult(
    val text: String,
    val stopReason: StopReason,
    val usage: Usage,
    val model: String,
    /** Explanation when the model declined ([StopReason.REFUSAL]). */
    val refusal: String? = null,
)

/** Set by the UI to abort a running request. */
class CancelSignal {
    @Volatile var cancelled = false
        private set
    fun cancel() { cancelled = true }
}

class LlmException(message: String, val retryable: Boolean = false, cause: Throwable? = null) : Exception(message, cause)
class CancelledException : Exception("Abgebrochen.")

/** Exchangeable AI backend. Implementations block; call them off the UI thread. */
interface LlmProvider {
    val displayName: String
    val model: String

    /** [onProgress] receives the number of characters generated so far (for a progress display). */
    fun generate(request: LlmRequest, cancel: CancelSignal = CancelSignal(), onProgress: (Int) -> Unit = {}): LlmResult
}

/** Prices in US$ per million tokens, for showing what a request cost. */
data class ModelInfo(
    val id: String,
    val label: String,
    val inputPerM: Double,
    val outputPerM: Double,
    val cacheReadPerM: Double,
    val cacheWritePerM: Double,
) {
    fun cost(u: Usage): Double =
        (u.inputTokens * inputPerM + u.outputTokens * outputPerM + u.cacheReadTokens * cacheReadPerM + u.cacheWriteTokens * cacheWritePerM) / 1_000_000.0

    companion object {
        // Cache writes (5-minute TTL) are billed at 1.25x the input price.
        val OPUS_5_5 = ModelInfo("claude-opus-5-5", "Claude Opus 5.5", 4.0, 20.0, 0.20, 5.0)
        val SONNET_5_5 = ModelInfo("claude-sonnet-5-5", "Claude Sonnet 5.5", 2.0, 10.0, 0.20, 2.5)
        val ALL = listOf(OPUS_5_5, SONNET_5_5)

        fun of(id: String) = ALL.firstOrNull { it.id == id }
    }
}

/** Replays prepared answers (tests and offline demos). Records every request. */
class FakeProvider(private val answers: MutableList<String>, override val model: String = "fake") : LlmProvider {
    override val displayName = "Test"
    val requests = mutableListOf<LlmRequest>()

    override fun generate(request: LlmRequest, cancel: CancelSignal, onProgress: (Int) -> Unit): LlmResult {
        requests += request
        if (cancel.cancelled) throw CancelledException()
        val text = answers.removeFirstOrNull() ?: throw LlmException("Keine vorbereitete Antwort mehr.")
        onProgress(text.length)
        return LlmResult(text, StopReason.END, Usage(inputTokens = 1000, outputTokens = text.length / 4L), model)
    }
}
