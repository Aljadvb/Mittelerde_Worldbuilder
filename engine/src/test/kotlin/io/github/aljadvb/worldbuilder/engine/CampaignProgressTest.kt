package io.github.aljadvb.worldbuilder.engine

import io.github.aljadvb.worldbuilder.model.CampaignFlag
import io.github.aljadvb.worldbuilder.model.Event
import io.github.aljadvb.worldbuilder.model.EventStep
import io.github.aljadvb.worldbuilder.model.ScenarioTransition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CampaignProgressTest {
    /** Example campaign played twice: a flag from the first run changes the follow-up and the intro event. */
    private val base = Examples.campaign
    private val s = base.scenarios.single()
    private val second = s.copy(
        id = "scn_zwei",
        events = s.events + listOf(
            Event("evt_check", listOf(EventStep.CheckFlag("fl_spuren", "evt_mit", "evt_ohne"))),
            Event("evt_mit", listOf(EventStep.Text("Ihr kennt die Spuren schon."))),
            Event("evt_ohne", listOf(EventStep.Text("Alles ist neu."))),
        ),
        chapters = s.chapters.map { if (it.id == s.startChapter) it.copy(onUnlock = "evt_check") else it },
    )
    private val campaign = base.copy(
        scenarios = listOf(
            s.copy(
                events = s.events.map { e -> if (e.id == "evt_brunnen") e.copy(steps = listOf(EventStep.SetFlag("fl_spuren"), EventStep.GainLore(2)) + e.steps) else e },
                resolutions = s.resolutions.map { it.copy(lore = 3, xp = 2) },
            ),
            second,
        ),
        flags = listOf(CampaignFlag("fl_spuren", "Spuren gefunden")),
        transitions = s.resolutions.map { ScenarioTransition(s.id, it.id, "scn_zwei") },
    )
    private val runner = CampaignRunner(campaign)

    private fun playToEnd(engine: GameEngine, state: GameState, touchWell: Boolean): GameState {
        var st = state
        while (!st.isIdle) st = engine.apply(st, Command.Acknowledge)
        if (touchWell) {
            st = engine.apply(st, Command.InteractToken("tok_brunnen"))
            while (!st.isIdle && !st.isOver) st = if (st.currentPrompt is Prompt.Test) engine.apply(st, Command.ReportTest(0)) else engine.apply(st, Command.Acknowledge)
        }
        while (!st.isOver) st = if (st.isIdle) engine.apply(st, Command.EndRound) else if (st.currentPrompt is Prompt.Test) engine.apply(st, Command.ReportTest(0)) else engine.apply(st, Command.Acknowledge)
        return st
    }

    @Test
    fun `rewards, flags and experience carry over`() {
        val (p0, st0) = runner.start(2, listOf(Hero("Aragorn", "Hauptmann"), Hero("Bilbo", "Meisterdieb")))
        val end = playToEnd(runner.engineFor(s.id), st0, touchWell = true)
        assertTrue("fl_spuren" in end.flags)
        assertEquals(2, end.loreGained)
        val (p1, next) = runner.advance(p0, end)
        assertEquals(5, p1.lore)
        assertTrue(p1.inCamp)
        assertEquals(listOf(2, 2), p1.party.map { it.xpFor(it.role).available })
        assertEquals("scn_zwei", p1.currentScenario)
        // The flag is known to the next scenario.
        var st = next!!
        val texts = mutableListOf<String>()
        val engine = runner.engineFor("scn_zwei", p1.flags)
        while (!st.isIdle) { (st.currentPrompt as? Prompt.Text)?.let { texts += it.text }; st = engine.apply(st, Command.Acknowledge) }
        assertTrue("Ihr kennt die Spuren schon." in texts, texts.toString())
    }

    @Test
    fun `without the flag the other branch runs`() {
        val (p0, st0) = runner.start(1)
        val (p1, next) = runner.advance(p0, playToEnd(runner.engineFor(s.id), st0, touchWell = false))
        assertFalse("fl_spuren" in p1.flags)
        assertEquals(3, p1.lore)
        val engine = runner.engineFor("scn_zwei", p1.flags)
        var st = next!!
        val texts = mutableListOf<String>()
        while (!st.isIdle) { (st.currentPrompt as? Prompt.Text)?.let { texts += it.text }; st = engine.apply(st, Command.Acknowledge) }
        assertTrue("Alles ist neu." in texts, texts.toString())
        assertEquals("Held 1", p1.party.single().name)
    }

    @Test
    fun `flag-dependent transitions win over unconditional ones`() {
        val c = campaign.copy(transitions = campaign.transitions + campaign.transitions.map { it.copy(to = s.id, requiresFlag = "fl_spuren") })
        assertEquals(s.id, c.nextScenario(s.id, s.resolutions.first().id, setOf("fl_spuren")))
        assertEquals("scn_zwei", c.nextScenario(s.id, s.resolutions.first().id, emptySet()))
    }

    @Test
    fun `camp buys and sells skills per role`() {
        var p = CampaignProgress(1, "scn_zwei", heroes = listOf(Hero("Legolas", "Jäger", xp = mapOf("Jäger" to RoleXp(earned = 5)))))
        p = Camp.buySkill(p, 0, "Scharfes Auge", 3)
        assertEquals(2, p.party[0].xpFor("Jäger").available)
        assertFailsWith<EngineException> { Camp.buySkill(p, 0, "Teuer", 3) }
        p = Camp.changeRole(p, 0, "Späher")
        assertEquals(0, p.party[0].xpFor("Späher").available)
        assertEquals(2, p.party[0].available)
        p = Camp.sellSkill(p, 0, 0)
        assertEquals(5, p.party[0].xpFor("Jäger").available)
        assertFailsWith<EngineException> { Camp.sellSkill(p, 0, 0) }
        assertFalse(Camp.leave(p.copy(inCamp = true)).inCamp)
    }
}
