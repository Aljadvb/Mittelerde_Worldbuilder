package io.github.aljadvb.worldbuilder.validation

import io.github.aljadvb.worldbuilder.model.TileLocation
import io.github.aljadvb.worldbuilder.validation.Severity
import io.github.aljadvb.worldbuilder.model.EventStep
import io.github.aljadvb.worldbuilder.model.HexCoord
import io.github.aljadvb.worldbuilder.model.Scenario
import io.github.aljadvb.worldbuilder.model.ScenarioTransition
import io.github.aljadvb.worldbuilder.model.TriggerCondition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class CampaignValidatorTest {
    private fun validate(edit: (Scenario) -> Scenario) =
        CampaignValidator.validate(Examples.campaign.editScenario(edit), Examples.catalog)

    private fun ValidationReport.single(code: IssueCode): ValidationIssue {
        val matches = issues.filter { it.code == code }
        if (matches.size != 1) fail("Expected exactly one $code, got:\n${format()}")
        return matches.single()
    }

    private fun ValidationReport.assertHas(code: IssueCode, severity: Severity) {
        assertTrue(issues.any { it.code == code && it.severity == severity }, "Expected $severity $code, got:\n${format()}")
    }

    @Test
    fun `example campaign has no findings`() {
        val report = CampaignValidator.validate(Examples.campaign, Examples.catalog)
        assertEquals("", report.format())
    }

    @Test
    fun `unknown reference is reported with its path`() {
        val report = validate { s ->
            s.editEvent("evt_brunnen") { e ->
                e.copy(steps = e.steps.map { if (it is EventStep.Test) it.copy(onSuccess = "evt_gibt_es_nicht") else it })
            }
        }
        val issue = report.single(IssueCode.UNKNOWN_REFERENCE)
        assertEquals("scenarios[0].events[4].steps[1].onSuccess", issue.path)
        assertEquals("evt_brunnen", issue.objectId)
        assertEquals(Severity.ERROR, issue.severity)
    }

    @Test
    fun `reference to an object of the wrong kind`() {
        val report = validate { s ->
            s.editEvent("evt_dorf_ankunft") { e -> e.copy(steps = e.steps + EventStep.SetObjective("tok_brunnen")) }
        }
        val issue = report.single(IssueCode.WRONG_REFERENCE_TYPE)
        assertTrue("Token" in issue.message && "Ziel" in issue.message, issue.message)
    }

    @Test
    fun `duplicate ids`() {
        val report = validate { s -> s.editEvent("evt_sieg") { it.copy(id = "evt_niederlage") } }
        report.assertHas(IssueCode.DUPLICATE_ID, Severity.ERROR)
    }

    @Test
    fun `id prefix is a warning`() {
        val report = validate { s -> s.editChapter("ch_muehle") { it.copy(id = "muehle") } }
        report.assertHas(IssueCode.ID_PREFIX, Severity.WARNING)
    }

    @Test
    fun `tiles and enemies must exist in the catalog`() {
        val report = validate { s ->
            s.editChapter("ch_dorf") { ch -> ch.copy(tiles = ch.tiles.map { if (it.id == "pl_dorfweg") it.copy(tile = "x99") else it }) }
                .copy(enemyGroups = s.enemyGroups.map { it.copy(enemyType = "drache") })
        }
        report.assertHas(IssueCode.UNKNOWN_TILE, Severity.ERROR)
        report.assertHas(IssueCode.UNKNOWN_ENEMY_TYPE, Severity.ERROR)
    }

    @Test
    fun `token on a non-existing cell`() {
        val report = validate { s ->
            s.editChapter("ch_dorf") { ch ->
                ch.editToken("tok_brunnen") { it.copy(location = (it.location as TileLocation).copy(cell = HexCoord(5, 5))) }
            }
        }
        val issue = report.single(IssueCode.CELL_NOT_ON_TILE)
        assertEquals("scenarios[0].chapters[0].tokens[1].location.cell", issue.path)
    }

    @Test
    fun `overlapping tiles`() {
        val report = validate { s ->
            s.editChapter("ch_muehle") { ch -> ch.copy(tiles = ch.tiles.map { it.copy(position = HexCoord(1, 0)) }) }
        }
        report.assertHas(IssueCode.TILES_OVERLAP, Severity.ERROR)
    }

    @Test
    fun `branching step must be last`() {
        val report = validate { s -> s.editEvent("evt_brunnen") { e -> e.copy(steps = e.steps + EventStep.Text("danach")) } }
        report.single(IssueCode.TERMINAL_STEP_NOT_LAST)
    }

    @Test
    fun `fireTrigger only for manual triggers`() {
        val report = validate { s ->
            s.editEvent("evt_dorf_ankunft") { e -> e.copy(steps = e.steps + EventStep.FireTrigger("trg_woelfe_besiegt")) }
        }
        report.single(IssueCode.FIRE_NON_MANUAL_TRIGGER)
    }

    @Test
    fun `incomplete enemy count`() {
        val report = validate { s -> s.copy(enemyGroups = s.enemyGroups.map { it.copy(count = it.count - 5) }) }
        report.single(IssueCode.ENEMY_COUNT_INCOMPLETE)
    }

    @Test
    fun `objective without a reachable trigger path`() {
        // Without fireTrigger the manual trigger that frees the miller is never fired.
        val report = validate { s ->
            s.editEvent("evt_tor_offen") { e -> e.copy(steps = e.steps.filterNot { it is EventStep.FireTrigger }) }
        }
        val issue = report.single(IssueCode.OBJECTIVE_NOT_COMPLETABLE)
        assertEquals("obj_mueller_befreien", issue.objectId)
        // only the defeat ending is left
        report.assertHas(IssueCode.NO_VICTORY_REACHABLE, Severity.WARNING)
        assertTrue(report.warnings.any { it.code == IssueCode.UNREACHABLE && it.objectId == "trg_mueller_befreit" })
    }

    @Test
    fun `token that never appears makes the first objective unreachable`() {
        val report = validate { s ->
            s.copy(events = s.events.map { e -> e.copy(steps = e.steps.filterNot { it is EventStep.AddToken }) })
        }
        assertTrue(report.warnings.any { it.code == IssueCode.UNREACHABLE && it.objectId == "tok_spuren" }, report.format())
        assertEquals("obj_muehle_finden", report.single(IssueCode.OBJECTIVE_NOT_COMPLETABLE).objectId)
        // Without the tracks the mill chapter is never unlocked either.
        assertTrue(report.warnings.any { it.code == IssueCode.UNREACHABLE && it.objectId == "ch_muehle" })
    }

    @Test
    fun `no reachable resolution`() {
        val report = validate { s ->
            s.copy(events = s.events.map { e -> e.copy(steps = e.steps.map { if (it is EventStep.EndScenario) EventStep.Text("…") else it }) })
        }
        report.single(IssueCode.NO_RESOLUTION_REACHABLE)
    }

    @Test
    fun `missing threat max trigger`() {
        val report = validate { s -> s.copy(triggers = s.triggers.filterNot { it.on == TriggerCondition.ThreatMax }) }
        report.single(IssueCode.NO_THREAT_MAX_TRIGGER)
    }

    @Test
    fun `material from an expansion not owned is a warning`() {
        val catalog = Examples.catalog.copy(expansions = Examples.catalog.expansions.map { it.copy(owned = false) })
        val report = CampaignValidator.validate(Examples.campaign, catalog)
        assertTrue(report.isValid)
        report.assertHas(IssueCode.EXPANSION_NOT_OWNED, Severity.WARNING)
    }

    @Test
    fun `transitions must use a resolution of the source scenario`() {
        val campaign = Examples.campaign.copy(
            transitions = listOf(
                ScenarioTransition("scn_muehle", "res_unbekannt", "scn_muehle"),
            ),
        )
        val report = CampaignValidator.validate(campaign, Examples.catalog)
        assertEquals("transitions[0].resolution", report.single(IssueCode.UNKNOWN_REFERENCE).path)
    }
}

class ExplorationValidationTest {
    private fun validate(edit: (io.github.aljadvb.worldbuilder.model.Scenario) -> io.github.aljadvb.worldbuilder.model.Scenario) =
        CampaignValidator.validate(Examples.campaign.editScenario(edit), Examples.catalog)

    @Test
    fun `unexplored tiles and inspiration are checked`() {
        val ok = validate { s ->
            s.copy(chapters = s.chapters.map { ch -> ch.copy(tiles = ch.tiles.map { if (it.id == "pl_muehle") it.copy(unexplored = true) else it }) })
                .editEvent("evt_brunnen") { e -> e.copy(steps = listOf(io.github.aljadvb.worldbuilder.model.EventStep.GainInspiration()) + e.steps) }
        }
        assertEquals("", ok.format())
        val bad = validate { s ->
            s.copy(
                chapters = s.chapters.map { ch -> ch.copy(tiles = ch.tiles.map { it.copy(unexplored = ch.id == "ch_dorf", onExplore = if (it.id == "pl_muehle") "evt_weg" else null) }) },
                triggers = s.triggers + io.github.aljadvb.worldbuilder.model.Trigger("trg_x", TriggerCondition.TileExplored("pl_muehle")),
            ).editEvent("evt_brunnen") { e -> e.copy(steps = listOf(io.github.aljadvb.worldbuilder.model.EventStep.GainInspiration(amount = 5)) + e.steps) }
        }
        val codes = bad.issues.map { it.code to it.severity }
        assertTrue((IssueCode.NO_EXPLORED_START to Severity.WARNING) in codes, bad.format())
        assertTrue((IssueCode.NOT_UNEXPLORED to Severity.WARNING) in codes, bad.format())
        assertTrue((IssueCode.UNKNOWN_REFERENCE to Severity.ERROR) in codes, bad.format())
        assertTrue((IssueCode.INVALID_NUMBER to Severity.ERROR) in codes, bad.format())
    }
}

class CampaignMapValidationTest {
    @Test
    fun `places, flags and rewards are checked`() {
        val c = Examples.campaign
        val s = c.scenarios.single()
        val good = c.copy(
            places = listOf(io.github.aljadvb.worldbuilder.model.Place("pt_bree", "Bree", 0.3, 0.4)),
            flags = listOf(io.github.aljadvb.worldbuilder.model.CampaignFlag("fl_a", "A")),
            scenarios = listOf(s.copy(place = "pt_bree", resolutions = s.resolutions.map { it.copy(lore = 2, xp = 1, setFlags = listOf("fl_a")) })),
            transitions = listOf(io.github.aljadvb.worldbuilder.model.ScenarioTransition(s.id, s.resolutions.first().id, s.id, requiresFlag = "fl_a")),
        )
        assertEquals("", CampaignValidator.validate(good, Examples.catalog).format())
        val bad = good.copy(
            places = listOf(io.github.aljadvb.worldbuilder.model.Place("pt_bree", "Bree", 1.3, 0.4)),
            flags = good.flags + io.github.aljadvb.worldbuilder.model.CampaignFlag("fl_b", "B") + io.github.aljadvb.worldbuilder.model.CampaignFlag("fl_c", "C"),
            scenarios = listOf(s.copy(place = "pt_gibtsnicht", resolutions = s.resolutions.map { it.copy(xp = -1, setFlags = listOf("fl_c")) })),
            transitions = good.transitions + good.transitions.first().copy(requiresFlag = "fl_b") + good.transitions.first().copy(requiresFlag = "fl_b"),
        )
        val codes = CampaignValidator.validate(bad, Examples.catalog).issues.map { it.code }.toSet()
        for (code in listOf(IssueCode.INVALID_NUMBER, IssueCode.UNKNOWN_REFERENCE, IssueCode.FLAG_NEVER_SET, IssueCode.FLAG_NEVER_USED, IssueCode.DUPLICATE_TRANSITION)) {
            assertTrue(code in codes, "$code missing: $codes")
        }
    }
}
