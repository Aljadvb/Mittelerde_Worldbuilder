package io.github.aljadvb.worldbuilder.generator

import io.github.aljadvb.worldbuilder.model.FormatJson
import io.github.aljadvb.worldbuilder.model.Scenario
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BattleGenerationTest {
    private val root = File(System.getProperty("repoRoot") ?: "..")
    private val battle = FormatJson.decodeCampaign(File(root, "examples/beispiel-schlacht.json").readText()).scenarios.single()

    @Test
    fun `a battle scenario from the model passes the pipeline unchanged`() {
        val answer = FormatJson.json.encodeToString(Scenario.serializer(), battle)
        val fake = FakeProvider(mutableListOf(answer))
        val draft = StoryDraft("T", "I", listOf(DraftChapter("Am Bach", "Kampf", listOf("Bach"), listOf("wiese"), battle = true)), emptyList(), emptyList(), emptyList(), "Ein Ort.")
        val result = ScenarioGenerator(fake, Examples.catalog, GenerationInput("Notizen", map = GenerationInput.MapChoice.AUTO)).scenario(draft) {}
        assertTrue(result.report.isValid, result.report.format())
        assertEquals(1, result.rounds)
        assertEquals(battle.boardPlacements, result.scenario.boardPlacements)
        assertTrue("Schlacht-Kapitel (Nr. 1)" in fake.requests.single().messages.first().text)
    }

    @Test
    fun `prompts describe battle material and the map choice`() {
        val auto = PromptBuilder(Examples.catalog, GenerationInput("x"))
        val system = auto.stableSystem()
        assertTrue("Plan-ID `sx1`" in system && "Teil-ID `bach_wall`, 4 Stück" in system, system)
        assertTrue("auf Feldgrenze" in system && "Rule".isNotEmpty())
        assertTrue("entscheide pro Kapitel" in auto.storyMessage())
        assertTrue("alle Kapitel sind Schlacht-Kapitel" in PromptBuilder(Examples.catalog, GenerationInput("x", map = GenerationInput.MapChoice.BATTLE)).storyMessage())
        // The map choice must not change the cached block.
        assertEquals(system, PromptBuilder(Examples.catalog, GenerationInput("x", map = GenerationInput.MapChoice.TRAVEL)).stableSystem())
        // Card texts never go into prompts.
        val withCard = Examples.catalog.copy(terrainPieces = Examples.catalog.terrainPieces.map { p -> p.copy(sides = p.sides.mapValues { it.value.copy(cardText = "GEHEIMER KARTENTEXT") }) })
        assertTrue("GEHEIMER" !in PromptBuilder(withCard, GenerationInput("x")).stableSystem())
    }

    @Test
    fun `story drafts carry the map decision`() {
        val json = """{"title":"T","intro":"I","chapters":[{"title":"K","summary":"S","locations":[],"terrain":[],"battle":true}],"objectives":[],"enemies":[],"endings":[],"mapReason":"Alles an einem Ort."}"""
        val d = Json.decodeFromString(StoryDraft.serializer(), json)
        assertTrue(d.chapters.single().battle)
        assertEquals("Alles an einem Ort.", d.mapReason)
        assertTrue("\"mapReason\"" in OutputSchemas.story && "\"battle\"" in OutputSchemas.story)
    }
}
