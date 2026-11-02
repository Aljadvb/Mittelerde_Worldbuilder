package io.github.aljadvb.worldbuilder.generator

import io.github.aljadvb.worldbuilder.model.FormatJson
import io.github.aljadvb.worldbuilder.model.MaterialCatalog
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

object Examples {
    private val root = File(System.getProperty("repoRoot") ?: "..")
    val catalog: MaterialCatalog = FormatJson.decodeCatalog(File(root, "examples/beispiel-material.json").readText())
    val campaign = FormatJson.decodeCampaign(File(root, "examples/beispiel-kampagne.json").readText())
}

class PromptAndSchemaTest {
    private val input = GenerationInput(source = "Eine verschwundene Müllerin, Nebel im Tal.", wishes = "düster")

    @Test
    fun `output schemas only use supported keywords and close every object`() {
        for (schema in listOf(OutputSchemas.scenario, OutputSchemas.story)) {
            val objects = OutputSchemas.objects(OutputSchemas.parse(schema))
            val forbidden = setOf("oneOf", "minLength", "minimum", "minItems", "uniqueItems", "pattern", "if", "then", "else", "not", "default")
            objects.forEach { o -> assertTrue(o.keys.none { it in forbidden }, o.keys.toString()) }
            objects.filter { it["type"] == JsonPrimitive("object") && "properties" in it }
                .forEach { assertEquals(JsonPrimitive(false), it["additionalProperties"], it.toString().take(120)) }
        }
    }

    @Test
    fun `scenario schema leaves the layout to the app`() {
        val root = OutputSchemas.parse(OutputSchemas.scenario).jsonObject
        val placement = root.getValue("\$defs").jsonObject.getValue("tilePlacement").jsonObject
        assertEquals(setOf("id", "tile", "side", "unexplored", "onExplore"), placement.getValue("properties").jsonObject.keys)
        assertEquals(JsonArray(listOf("id", "tile", "side").map(::JsonPrimitive)), placement["required"])
        assertTrue("chapters" in root.getValue("properties").jsonObject)
    }

    @Test
    fun `prompts contain material and source, and the cached block is stable`() {
        val b = PromptBuilder(Examples.catalog, input)
        val sys = b.stableSystem()
        assertTrue("`x01`" in sys && "(0,0)" in sys && "nebelwolf" in sys, sys)
        assertTrue("wald_dicht" in sys)
        val story = b.storyRequest()
        assertTrue(story.system.first().cache)
        assertTrue("verschwundene Müllerin" in story.messages.single().text)
        val draft = StoryDraft("T", "I", listOf(DraftChapter("K", "S", listOf("Ort"), listOf("wiese"))), emptyList(), emptyList(), emptyList())
        assertEquals(story.system.first(), b.scenarioRequest(draft).system.first())
        assertEquals(sys, PromptBuilder(Examples.catalog, input).stableSystem())
    }

    @Test
    fun `material selection restricts tiles and enemies`() {
        val b = PromptBuilder(Examples.catalog, input.copy(allowedTiles = setOf("x02"), allowedEnemies = setOf("aschekraehe")))
        val sys = b.stableSystem()
        assertTrue("`x02`" in sys)
        assertFalse("`x01`" in sys)
        assertFalse("nebelwolf" in sys)
        assertEquals(setOf("x02"), b.allowedTileIds)
    }
}
