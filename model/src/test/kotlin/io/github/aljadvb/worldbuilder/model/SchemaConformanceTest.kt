package io.github.aljadvb.worldbuilder.model

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Keeps the hand-written JSON schemas, the Kotlin model and the example files in sync:
 * the examples must satisfy the schema, parse into the model, and the model's output
 * must satisfy the schema again.
 */
class SchemaConformanceTest {
    private val mapper = ObjectMapper()
    private val factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
    private val campaignSchema = factory.getSchema(RepoFiles.campaignSchema)
    private val materialSchema = factory.getSchema(RepoFiles.materialSchema)

    private fun campaignErrors(json: String) = campaignSchema.validate(mapper.readTree(json)).map { it.message }
    private fun materialErrors(json: String) = materialSchema.validate(mapper.readTree(json)).map { it.message }

    @Test
    fun `example campaign satisfies the schema`() {
        assertEquals(emptyList(), campaignErrors(RepoFiles.exampleCampaign))
    }

    @Test
    fun `example material satisfies the schema`() {
        assertEquals(emptyList(), materialErrors(RepoFiles.exampleMaterial))
    }

    @Test
    fun `examples parse into the model`() {
        val campaign = FormatJson.decodeCampaign(RepoFiles.exampleCampaign)
        assertEquals("scn_muehle", campaign.startScenario)
        assertEquals(2, campaign.scenarios.single().chapters.size)
        val catalog = FormatJson.decodeCatalog(RepoFiles.exampleMaterial)
        assertEquals(3, catalog.tiles.size)
    }

    @Test
    fun `model output satisfies the schema`() {
        val campaign = FormatJson.decodeCampaign(RepoFiles.exampleCampaign)
        assertEquals(emptyList(), campaignErrors(FormatJson.encodeCampaign(campaign)))
        val catalog = FormatJson.decodeCatalog(RepoFiles.exampleMaterial)
        assertEquals(emptyList(), materialErrors(FormatJson.encodeCatalog(catalog)))
    }

    @Test
    fun `schema rejects typical mistakes`() {
        /** Applies [edit] to the object at JSON pointer [pointer] of a fresh copy of the example. */
        fun mutated(pointer: String, edit: (ObjectNode) -> Unit): List<String> {
            val root = mapper.readTree(RepoFiles.exampleCampaign)
            edit(root.at(pointer) as ObjectNode)
            return campaignErrors(mapper.writeValueAsString(root))
        }
        val scenario = "/scenarios/0"

        assertTrue(mutated("") { it.put("titel", "x") }.isNotEmpty(), "unknown property")
        assertTrue(mutated("$scenario/events/0/steps/0") { it.put("type", "teleport") }.isNotEmpty(), "unknown step type")
        assertTrue(mutated("") { it.put("id", "Kampagne 1") }.isNotEmpty(), "invalid id")
        assertTrue(mutated("$scenario/chapters/1/tokens/0") { it.remove("customType") }.isNotEmpty(), "custom token without customType")
        assertTrue(mutated("$scenario/chapters/0/tokens/0") { it.put("customType", "x") }.isNotEmpty(), "customType on non-custom token")
        assertTrue(mutated("$scenario/enemyGroups/0/count") { it.remove("5") }.isNotEmpty(), "incomplete enemy count")
        assertTrue(mutated("$scenario/chapters/0/tiles/0") { it.put("rotation", 6) }.isNotEmpty(), "rotation out of range")
    }

    @Test
    fun `battle example satisfies the schema and round-trips`() {
        assertEquals(emptyList(), campaignErrors(RepoFiles.exampleBattle))
        val campaign = FormatJson.decodeCampaign(RepoFiles.exampleBattle)
        val chapter = campaign.scenarios.single().chapters.single()
        assertTrue(chapter.isBattle)
        assertEquals(SquareLocation(GridCoord(4, 1)), chapter.tokens.single().location)
        assertEquals(RuleMode.ADD, chapter.battlefield!!.terrain.last().customRule!!.mode)
        val encoded = FormatJson.encodeCampaign(campaign)
        assertEquals(emptyList(), campaignErrors(encoded))
        assertEquals(campaign, FormatJson.decodeCampaign(encoded))
        // Locations are told apart by their keys; no "type" property is written.
        assertTrue("\"square\"" in encoded && "\"type\": \"square" !in encoded)
        val catalog = FormatJson.decodeCatalog(RepoFiles.exampleMaterial)
        assertEquals(2, catalog.battleBoard("sx1")!!.count)
        assertEquals(PiecePlacement.EDGE, catalog.terrainPiece("bach_wall")!!.sides.getValue(TileSide.B).placement)
    }

    @Test
    fun `schema rejects battle mistakes`() {
        fun errors(edit: (ObjectNode) -> Unit): List<String> {
            val root = mapper.readTree(RepoFiles.exampleBattle)
            edit(root.at("/scenarios/0/chapters/0/battlefield") as ObjectNode)
            return campaignErrors(mapper.writeValueAsString(root))
        }
        assertTrue(errors { (it.at("/boards/0") as ObjectNode).put("rotation", 4) }.isNotEmpty(), "rotation out of range")
        assertTrue(errors { (it.at("/terrain/0/edge") as ObjectNode).put("side", "up") }.isNotEmpty(), "unknown edge side")
        assertTrue(errors { (it.at("/terrain/2/customRule") as ObjectNode).put("mode", "merge") }.isNotEmpty(), "unknown rule mode")
        val loc = mapper.readTree(RepoFiles.exampleBattle)
        (loc.at("/scenarios/0/chapters/0/tokens/0/location") as ObjectNode).put("placement", "pl_x")
        assertTrue(campaignErrors(mapper.writeValueAsString(loc)).isNotEmpty(), "mixed location")
    }

    @Test
    fun `location without placement or square is rejected when decoding`() {
        val broken = Regex("\"square\"(?=: \\{\\s*\"x\": 4)").replace(RepoFiles.exampleBattle, "\"spot\"")
        check(broken != RepoFiles.exampleBattle)
        assertTrue(runCatching { FormatJson.decodeCampaign(broken) }.isFailure)
    }
}
