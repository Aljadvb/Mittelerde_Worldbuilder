package io.github.aljadvb.worldbuilder.engine

import io.github.aljadvb.worldbuilder.model.Campaign
import io.github.aljadvb.worldbuilder.model.FormatJson
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.fail

object Examples {
    private val root = File(System.getProperty("repoRoot") ?: "..")
    val campaign: Campaign = FormatJson.decodeCampaign(File(root, "examples/beispiel-kampagne.json").readText())
    val scenario get() = campaign.scenarios.single()
}

/** Small DSL that applies commands and checks prompts. */
class Play(val engine: GameEngine, var state: GameState) {
    fun send(command: Command) = apply { state = engine.apply(state, command) }

    inline fun <reified P : Prompt> expect(check: (P) -> Unit = {}): Play {
        val prompt = state.currentPrompt ?: fail("Expected ${P::class.simpleName}, but no prompt is open")
        assertIs<P>(prompt, "Prompts: ${state.prompts}")
        check(prompt)
        return this
    }

    /** Expects a notice of the given type and acknowledges it. */
    inline fun <reified P : Prompt.Notice> ack(check: (P) -> Unit = {}): Play {
        expect<P>(check)
        return send(Command.Acknowledge)
    }

    fun text(fragment: String) = ack<Prompt.Text> { kotlin.test.assertContains(it.text, fragment) }

    fun idle() = apply { assertEquals(true, state.isIdle, "Expected idle, prompts: ${state.prompts}") }
}
