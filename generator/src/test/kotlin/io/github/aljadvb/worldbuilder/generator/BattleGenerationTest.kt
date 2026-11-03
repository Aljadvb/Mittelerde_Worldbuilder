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
        assertTrue("entscheide für das GANZE Szenario" in auto.storyMessage())
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

class CampaignGenerationTest {
    @Test
    fun `prompt asks for rewards and knows the campaign flags`() {
        val input = GenerationInput("x", campaignFlags = listOf(io.github.aljadvb.worldbuilder.model.CampaignFlag("fl_mueller_gerettet", "Müller gerettet")))
        val msg = PromptBuilder(Examples.catalog, input).scenarioMessage(StoryDraft("T", "I", emptyList(), emptyList(), emptyList(), emptyList()))
        assertTrue("\"xp\"" in msg && "\"lore\"" in msg && "fl_mueller_gerettet" in msg, msg)
        assertTrue("gainLore" in PromptBuilder(Examples.catalog, input).stableSystem())
    }

    @Test
    fun `new flags from the model are added to the campaign`() {
        val s = Examples.campaign.scenarios.single()
        val withFlag = s.copy(place = null, resolutions = s.resolutions.map { it.copy(setFlags = listOf("fl_mueller_gerettet")) })
        val fresh = Importer.newCampaign(withFlag)
        assertEquals(listOf("Mueller gerettet"), fresh.flags.map { it.label })
        val (merged, _) = Importer.addTo(fresh, withFlag)
        assertEquals(1, merged.flags.size)
        // Validation inside the generator accepts the new flag.
        val report = ScenarioGenerator(FakeProvider(mutableListOf()), Examples.catalog, GenerationInput("x")).validate(withFlag)
        assertTrue(report.isValid, report.format())
    }
}
