package io.github.aljadvb.worldbuilder.editing

import io.github.aljadvb.worldbuilder.model.CampaignFlag
import io.github.aljadvb.worldbuilder.model.EventStep
import io.github.aljadvb.worldbuilder.model.ScenarioTransition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ScenarioImportTest {
    @Test
    fun `scenarios from other files are merged with renamed ids`() {
        val base = Examples.campaign
        val other = base.copy(
            id = "cmp_andere",
            flags = listOf(CampaignFlag("fl_brunnen", "Brunnen untersucht")),
            scenarios = base.scenarios.map { s ->
                s.copy(events = s.events.map { e -> if (e.id == "evt_brunnen") e.copy(steps = listOf(EventStep.SetFlag("fl_brunnen")) + e.steps) else e })
            },
        )
        val result = base.importScenarios(other, listOf("scn_muehle"))
        val c = result.campaign
        assertEquals(2, c.scenarios.size)
        assertTrue(result.scenarioIds.single() != "scn_muehle")
        assertEquals(1, c.places.size) // same place ID is shared
        assertEquals(listOf("fl_brunnen"), c.flags.map { it.id })
        assertEquals(c.allIds().size, c.definitions().size) // no duplicates
        val errors = c.validate().errors
        assertTrue(errors.isEmpty(), c.validate().format())
        // Link the old scenario to the imported one.
        val linked = c.copy(transitions = listOf(ScenarioTransition("scn_muehle", "res_muehle_sieg", result.scenarioIds.single())))
        assertTrue(linked.validate().issues.none { it.code == io.github.aljadvb.worldbuilder.validation.IssueCode.UNREACHABLE }, linked.validate().format())
    }

    @Test
    fun `nothing selected is an error`() {
        assertFailsWith<EditException> { Examples.campaign.importScenarios(Examples.campaign, emptyList()) }
    }
}
