package io.github.aljadvb.worldbuilder.generator

import io.github.aljadvb.worldbuilder.model.EventStep
import io.github.aljadvb.worldbuilder.model.FormatJson
import io.github.aljadvb.worldbuilder.model.Scenario
import io.github.aljadvb.worldbuilder.validation.CampaignValidator
import io.github.aljadvb.worldbuilder.validation.IssueCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ScenarioGeneratorTest {
    private val example = Examples.campaign.scenarios.single()
    private val input = GenerationInput(source = "Notizen zur Mühle.")

    /** The scenario as the model would write it: tile placements without position/rotation. */
    private fun asModelOutput(s: Scenario): String {
        val root = FormatJson.json.encodeToJsonElement(Scenario.serializer(), s).jsonObject
        val chapters = JsonArray(root.getValue("chapters").let { it as JsonArray }.map { ch ->
            val o = ch.jsonObject
            JsonObject(o + ("tiles" to JsonArray((o["tiles"] as JsonArray).map { JsonObject(it.jsonObject - "position" - "rotation") })))
        })
        return Json.encodeToString(JsonObject.serializer(), JsonObject(root + ("chapters" to chapters)))
    }

    private val broken = example.copy(events = example.events.map { e ->
        if (e.id == "evt_brunnen") e.copy(steps = e.steps + EventStep.Text("danach")) else e
    })

    @Test
    fun `story draft is read from the model answer`() {
        val draft = StoryDraft("Titel", "Einleitung", listOf(DraftChapter("K1", "S", listOf("Dorf"), listOf("wiese"))), listOf(DraftObjective("Z", "so")), emptyList(), listOf(DraftEnding("Sieg", "victory", "Ziel erfüllt")))
        val fake = FakeProvider(mutableListOf(Json.encodeToString(StoryDraft.serializer(), draft)))
        val result = ScenarioGenerator(fake, Examples.catalog, input).story()
        assertEquals(draft, result.draft)
        assertEquals(OutputSchemas.story, fake.requests.single().jsonSchema)
    }

    @Test
    fun `validator findings go back to the model until the scenario is valid`() {
        val fake = FakeProvider(mutableListOf(asModelOutput(broken), asModelOutput(example)))
        val statuses = mutableListOf<GenerationStatus>()
        val draft = StoryDraft("T", "I", emptyList(), emptyList(), emptyList(), emptyList())
        val result = ScenarioGenerator(fake, Examples.catalog, input).scenario(draft) { statuses += it }

        assertEquals(2, result.rounds)
        assertTrue(result.report.isValid, result.report.format())
        assertTrue(statuses.any { it is GenerationStatus.Repairing })
        val repair = fake.requests[1]
        assertEquals(3, repair.messages.size)
        assertTrue(IssueCode.TERMINAL_STEP_NOT_LAST.name in repair.messages.last().text)
        assertEquals(asModelOutput(broken), repair.messages[1].text)
        // layout was computed: tiles do not overlap
        assertTrue(CampaignValidator.validate(Examples.campaign.copy(scenarios = listOf(result.scenario)), Examples.catalog).isValid)
    }

    @Test
    fun `unreadable answers are repaired too, and the generator gives up after max rounds`() {
        val fake = FakeProvider(mutableListOf("kein json", asModelOutput(example)))
        val draft = StoryDraft("T", "I", emptyList(), emptyList(), emptyList(), emptyList())
        assertEquals(2, ScenarioGenerator(fake, Examples.catalog, input).scenario(draft).rounds)

        val stubborn = FakeProvider(MutableList(4) { asModelOutput(broken) })
        val result = ScenarioGenerator(stubborn, Examples.catalog, input, maxRepairs = 3).scenario(draft)
        assertEquals(4, result.rounds)
        assertTrue(!result.report.isValid)
    }

    @Test
    fun `tiles outside the selected material are rejected`() {
        val limited = input.copy(allowedTiles = setOf("x01", "x02"))
        val fake = FakeProvider(mutableListOf(asModelOutput(example), asModelOutput(example)))
        val draft = StoryDraft("T", "I", emptyList(), emptyList(), emptyList(), emptyList())
        ScenarioGenerator(fake, Examples.catalog, limited, maxRepairs = 1).scenario(draft)
        assertTrue("x03" in fake.requests[1].messages.last().text)
    }

    @Test
    fun `refusals and cut-off answers are reported clearly`() {
        class Refusing(val reason: StopReason) : LlmProvider {
            override val displayName = "x"; override val model = "claude-opus-5-5"
            override fun generate(request: LlmRequest, cancel: CancelSignal, onProgress: (Int) -> Unit) =
                LlmResult("", reason, Usage(), model, refusal = "Kategorie x")
        }
        val draft = StoryDraft("T", "I", emptyList(), emptyList(), emptyList(), emptyList())
        val e = assertFailsWith<GenerationException> { ScenarioGenerator(Refusing(StopReason.REFUSAL), Examples.catalog, input).scenario(draft) }
        assertTrue("abgelehnt" in e.message!!)
        assertFailsWith<GenerationException> { ScenarioGenerator(Refusing(StopReason.MAX_TOKENS), Examples.catalog, input).story() }
    }

    @Test
    fun `cost is computed from the model's prices`() {
        val u = Usage(inputTokens = 1_000_000, outputTokens = 100_000, cacheReadTokens = 1_000_000)
        assertEquals(4.0 + 2.0 + 0.2, ModelInfo.OPUS_5_5.cost(u), 1e-9)
    }

    @Test
    fun `importer renames colliding ids`() {
        val (campaign, id) = Importer.addTo(Examples.campaign, example)
        assertEquals(2, campaign.scenarios.size)
        assertTrue(id != example.id)
        val report = CampaignValidator.validate(campaign, Examples.catalog)
        assertTrue(report.isValid, report.format())
        val fresh = Importer.newCampaign(example)
        assertEquals(example.id, fresh.startScenario)
        assertTrue(CampaignValidator.validate(fresh, Examples.catalog).isValid)
    }

    @Test
    fun `a map that does not fit on the table goes back for correction`() {
        val small = input.copy(table = io.github.aljadvb.worldbuilder.editing.TableSize(10.0, 10.0))
        val fake = FakeProvider(mutableListOf(asModelOutput(example), asModelOutput(example)))
        val draft = StoryDraft("T", "I", emptyList(), emptyList(), emptyList(), emptyList())
        ScenarioGenerator(fake, Examples.catalog, small, maxRepairs = 1).scenario(draft)
        assertTrue("zu groß für den Tisch" in fake.requests[1].messages.last().text)
        assertTrue("10 × 10 cm" in fake.requests[0].messages.single().text)
    }
}
