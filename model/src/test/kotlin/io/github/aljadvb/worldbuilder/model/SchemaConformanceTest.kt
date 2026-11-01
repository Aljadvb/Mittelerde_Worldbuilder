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
}
