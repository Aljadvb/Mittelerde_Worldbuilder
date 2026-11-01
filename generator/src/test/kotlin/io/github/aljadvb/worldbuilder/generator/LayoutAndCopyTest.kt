package io.github.aljadvb.worldbuilder.generator

import io.github.aljadvb.worldbuilder.editing.mapCells
import io.github.aljadvb.worldbuilder.model.HexCoord
import io.github.aljadvb.worldbuilder.validation.CampaignValidator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LayoutAndCopyTest {
    private val scenario = Examples.campaign.scenarios.single()

    @Test
    fun `layout of the example has no overlaps and keeps it valid`() {
        // throw away the hand-made positions
        val scrambled = scenario.copy(chapters = scenario.chapters.map { ch -> ch.copy(tiles = ch.tiles.map { it.copy(position = HexCoord.ORIGIN, rotation = 0) }) })
        val laid = AutoLayout.layout(scrambled, Examples.catalog)
        val cells = laid.placements.flatMap { it.mapCells(Examples.catalog) }
        assertEquals(cells.size, cells.toSet().size)
        val report = CampaignValidator.validate(Examples.campaign.copy(scenarios = listOf(laid)), Examples.catalog)
        assertEquals("", report.format())
        // every tile touches the map built so far
        laid.placements.drop(1).forEach { p ->
            val others = laid.placements.filter { it.id != p.id }.flatMap { it.mapCells(Examples.catalog) }.toSet()
            assertTrue(p.mapCells(Examples.catalog).any { c -> c.neighbors().any { it in others } }, p.id)
        }
    }

    @Test
    fun `layout is deterministic`() {
        assertEquals(AutoLayout.layout(scenario, Examples.catalog), AutoLayout.layout(scenario, Examples.catalog))
    }

    @Test
    fun `copy check finds long verbatim passages only`() {
        val source = "Seit drei Tagen dreht sich das Rad der alten Mühle von Hainbrück nicht mehr und der Müller ist fort."
        val findings = CopyCheck.check(scenario, source)
        assertEquals(listOf(scenario.id), findings.map { it.objectId })
        assertTrue(findings.single().words >= 12)
        assertTrue(CopyCheck.check(scenario, "Hainbrück, die Mühle und der Nebel.").isEmpty())
    }
}
