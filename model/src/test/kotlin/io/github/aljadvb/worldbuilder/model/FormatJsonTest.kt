package io.github.aljadvb.worldbuilder.model

import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FormatJsonTest {
    private val loc = TileLocation("pl_a", HexCoord(0, 0))

    private val scenario = Scenario(
        id = "scn_test",
        title = "Test",
        intro = "Intro",
        threat = ThreatConfig(max = 10, perRound = ThreatPerRound(base = 1, perHero = 1)),
        startChapter = "ch_1",
        startObjective = "obj_1",
        chapters = listOf(
            Chapter(
                id = "ch_1",
                title = "Eins",
                tiles = listOf(TilePlacement("pl_a", "x01", TileSide.A, HexCoord(0, 0), 2)),
                tokens = listOf(
                    Token("tok_a", TokenType.SEARCH, label = "A", location = loc, onInteract = "evt_a"),
                    Token("tok_b", TokenType.CUSTOM, customType = "Altar", label = "B", location = loc, initiallyActive = false),
                ),
            ),
        ),
        enemyGroups = listOf(EnemyGroup("eg_1", "wolf", mapOf(1 to 1, 2 to 1, 3 to 2, 4 to 2, 5 to 3), loc)),
        events = listOf(
            Event(
                "evt_a",
                listOf(
                    EventStep.Text("Hallo"),
                    EventStep.ChangeThreat(-2),
                    EventStep.Test(attribute = Attribute.WISDOM, successes = 2, onSuccess = "evt_b"),
                ),
            ),
            Event("evt_b", listOf(EventStep.EndScenario("res_win"))),
        ),
        triggers = listOf(
            Trigger("trg_a", TriggerCondition.TokenInteracted("tok_a")),
            Trigger("trg_max", TriggerCondition.ThreatMax, events = listOf("evt_b")),
        ),
        objectives = listOf(Objective("obj_1", "Ziel", "Erinnerung", "trg_a")),
        resolutions = listOf(Resolution("res_win", "Sieg", "Text", Outcome.VICTORY)),
    )

    private val campaign = Campaign(
        formatVersion = 1, id = "cmp_t", title = "T", startScenario = "scn_test", scenarios = listOf(scenario),
    )

    @Test
    fun `campaign survives a round trip`() {
        val text = FormatJson.encodeCampaign(campaign)
        assertEquals(campaign, FormatJson.decodeCampaign(text))
    }

    @Test
    fun `polymorphic objects use a type field with the documented names`() {
        val text = FormatJson.encodeCampaign(campaign)
        assertTrue("\"type\": \"changeThreat\"" in text)
        assertTrue("\"type\": \"threatMax\"" in text)
        assertTrue("\"type\": \"custom\"" in text)
        assertTrue("\"attribute\": \"wisdom\"" in text)
    }

    @Test
    fun `defaults are omitted when writing`() {
        val text = FormatJson.encodeCampaign(campaign)
        assertTrue("removeAfterInteract" !in text)
        assertTrue("repeatable" !in text)
    }

    @Test
    fun `enemy counts are keyed by hero count as strings`() {
        val text = FormatJson.encodeCampaign(campaign)
        assertTrue("\"3\": 2" in text)
    }

    @Test
    fun `unknown keys are rejected`() {
        val text = FormatJson.encodeCampaign(campaign).replaceFirst("\"title\"", "\"titel\": \"x\", \"title\"")
        assertFailsWith<SerializationException> { FormatJson.decodeCampaign(text) }
    }

    @Test
    fun `step without type is rejected`() {
        val text = """
            {"formatVersion":1,"id":"cmp","title":"t","startScenario":"s","scenarios":[
              {"id":"s","title":"t","intro":"i","threat":{"max":5},"startChapter":"c","startObjective":"o",
               "chapters":[],"resolutions":[],"events":[{"id":"e","steps":[{"text":"x"}]}]}]}
        """.trimIndent()
        assertFailsWith<SerializationException> { FormatJson.decodeCampaign(text) }
    }

    @Test
    fun `catalog parses with per-side shapes`() {
        val text = """
            {"formatVersion":1,"hexOrientation":"flat",
             "expansions":[{"id":"basis","name":"Grundspiel"}],
             "tiles":[{"id":"x01","label":"X01","expansion":"basis",
                       "sides":{"A":{"cells":[{"q":0,"r":0},{"q":1,"r":0}]},"B":{"cells":[{"q":0,"r":0}]}}}],
             "enemyTypes":[{"id":"wolf","name":"Nebelwolf","expansion":"basis"}]}
        """.trimIndent()
        val catalog = FormatJson.decodeCatalog(text)
        assertEquals(HexOrientation.FLAT, catalog.hexOrientation)
        assertEquals(2, catalog.tile("x01")!!.sides.getValue(TileSide.A).cells.size)
        assertTrue(catalog.expansion("basis")!!.owned)
        assertIs<MaterialCatalog>(FormatJson.decodeCatalog(FormatJson.encodeCatalog(catalog)))
    }
}

class TerrainTest {
    @Test
    fun `terrain ids are unique, valid and listed in the schema`() {
        val ids = Terrain.TAGS.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { Regex("^[a-z][a-z0-9_]*$").matches(it) })
        val schema = RepoFiles.materialSchema
        ids.forEach { assertTrue("\"$it\"" in schema, it) }
        assertEquals("Dichter Wald", Terrain.label("wald_dicht"))
    }

    @Test
    fun `terrain and description round trip`() {
        val side = TileSideShape(listOf(HexCoord.ORIGIN), terrain = listOf("fluss", "huegel"), description = "Hügel am Fluss")
        val catalog = MaterialCatalog(1, tiles = listOf(TileDefinition("t1", "1", "x", mapOf(TileSide.A to side))))
        assertEquals(catalog, FormatJson.decodeCatalog(FormatJson.encodeCatalog(catalog)))
    }
}
