package io.github.aljadvb.worldbuilder.generator

import kotlinx.serialization.json.Json
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ManualProviderTest {
    private val input = GenerationInput(source = "Notizen zur Mühle.")

    @Test
    fun `answers are taken out of chat formatting`() {
        assertEquals("""{"a":1}""", ManualProvider.stripFences("Hier ist das Szenario:\n```json\n{\"a\":1}\n```\nViel Spaß!"))
        assertEquals("""{"a":1}""", ManualProvider.stripFences("""{"a":1}"""))
        assertEquals("""{"a":{"b":2}}""", ManualProvider.stripFences("Text vorher {\"a\":{\"b\":2}} danach"))
    }

    @Test
    fun `a new chat gets everything, a correction only the last message`() {
        val b = PromptBuilder(Examples.catalog, input)
        val first = ManualProvider.render(b.storyRequest())
        assertTrue("# Verfügbares Material" in first && "`x01`" in first && "Antwortformat" in first && "Notizen zur Mühle" in first)
        val draft = StoryDraft("T", "I", emptyList(), emptyList(), emptyList(), emptyList())
        val repair = ManualProvider.render(b.repairRequest(draft, "{}", io.github.aljadvb.worldbuilder.validation.ValidationReport(emptyList()), listOf("- Fehler X")))
        assertTrue("Fehler X" in repair)
        assertFalse("# Verfügbares Material" in repair)
        assertTrue(ManualProvider.ANSWER_RULE in repair)
    }

    @Test
    fun `generator waits for the pasted answer`() {
        val manual = ManualProvider()
        val draft = StoryDraft("Titel", "Intro", emptyList(), emptyList(), emptyList(), emptyList())
        val pool = Executors.newSingleThreadExecutor()
        try {
            val result = pool.submit<StoryResult> { ScenarioGenerator(manual, Examples.catalog, input).story() }
            var waited = 0
            while (manual.pending == null && waited++ < 100) Thread.sleep(20)
            val pending = assertNotNull(manual.pending)
            assertFalse(pending.sameChat)
            manual.answer("```json\n" + Json.encodeToString(StoryDraft.serializer(), draft) + "\n```")
            assertEquals(draft, result.get(5, TimeUnit.SECONDS).draft)
            assertEquals(0.0, result.get().cost)
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `cancel ends the wait`() {
        val manual = ManualProvider()
        val signal = CancelSignal()
        val pool = Executors.newSingleThreadExecutor()
        try {
            val f = pool.submit<LlmResult> { manual.generate(PromptBuilder(Examples.catalog, input).storyRequest(), signal) }
            Thread.sleep(100)
            signal.cancel()
            val e = assertFailsWith<java.util.concurrent.ExecutionException> { f.get(5, TimeUnit.SECONDS) }
            assertTrue(e.cause is CancelledException)
        } finally {
            pool.shutdownNow()
        }
    }
}
