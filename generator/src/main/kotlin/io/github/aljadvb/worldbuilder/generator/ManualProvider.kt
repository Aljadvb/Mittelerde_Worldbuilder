package io.github.aljadvb.worldbuilder.generator

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** A request the user copies into a chat (e.g. claude.ai) and answers by pasting the reply. */
data class ManualRequest(
    /** Text to copy into the chat. */
    val prompt: String,
    /** True if this continues the previous chat (correction round), false for a new chat. */
    val sameChat: Boolean,
)

/**
 * Provider without API: each request is turned into one text for the user to paste into
 * a chat; [generate] waits until the user pastes the answer back via [answer].
 */
class ManualProvider(override val model: String = "manual") : LlmProvider {
    override val displayName = "Claude.ai (manuell)"

    @Volatile var pending: ManualRequest? = null
        private set
    @Volatile private var future: CompletableFuture<String>? = null

    /** Called when a new request waits for the user (UI update). */
    var onPending: (ManualRequest?) -> Unit = {}

    override fun generate(request: LlmRequest, cancel: CancelSignal, onProgress: (Int) -> Unit): LlmResult {
        val f = CompletableFuture<String>()
        future = f
        val req = ManualRequest(render(request), sameChat = request.messages.size > 1)
        pending = req
        onPending(req)
        try {
            while (true) {
                if (cancel.cancelled) throw CancelledException()
                try {
                    val text = f.get(200, TimeUnit.MILLISECONDS)
                    onProgress(text.length)
                    return LlmResult(stripFences(text), StopReason.END, Usage(), model)
                } catch (_: TimeoutException) {
                    // keep waiting for the user
                } catch (e: ExecutionException) {
                    throw e.cause ?: e
                }
            }
        } finally {
            pending = null
            future = null
            onPending(null)
        }
    }

    /** The user pasted the chat's answer. */
    fun answer(text: String) {
        future?.complete(text)
    }

    companion object {
        /**
         * One self-contained text: for a new chat everything (instructions, format, task);
         * for a correction round only the last message (the chat already has the rest).
         */
        fun render(request: LlmRequest): String = buildString {
            if (request.messages.size > 1) {
                appendLine(request.messages.last().text.trim())
                appendLine()
                appendLine(ANSWER_RULE)
                return@buildString
            }
            request.system.forEach { appendLine(it.text.trim()); appendLine() }
            appendLine("# Aufgabe")
            appendLine()
            appendLine(request.messages.single().text.trim())
            request.jsonSchema?.let { schema ->
                appendLine()
                appendLine("# Antwortformat")
                appendLine()
                appendLine("Die Antwort muss diesem JSON-Schema entsprechen:")
                appendLine("```json")
                appendLine(schema)
                appendLine("```")
            }
            appendLine()
            appendLine(ANSWER_RULE)
        }

        const val ANSWER_RULE = "Antworte ausschließlich mit dem vollständigen JSON in einem einzigen ```json-Codeblock – ohne Erklärungen davor oder danach, ohne Kommentare im JSON."

        /** Takes the JSON out of a chat answer (code fences, text around it). */
        fun stripFences(text: String): String {
            val fenced = Regex("```(?:json)?\\s*\\n([\\s\\S]*?)\\n?```").find(text)?.groupValues?.get(1)
            val body = (fenced ?: text).trim()
            val start = body.indexOf('{')
            val end = body.lastIndexOf('}')
            return if (start >= 0 && end > start) body.substring(start, end + 1) else body
        }
    }
}
