package io.github.aljadvb.worldbuilder.engine

import io.github.aljadvb.worldbuilder.model.Event
import io.github.aljadvb.worldbuilder.model.EventStep
import io.github.aljadvb.worldbuilder.model.HeroTarget
import io.github.aljadvb.worldbuilder.model.Item
import io.github.aljadvb.worldbuilder.model.MaterialCatalog
import io.github.aljadvb.worldbuilder.model.ScenarioTransition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ItemTest {
    private val feder = Item("feder", "Feder", "beispiel")
    private val beutel = Item("beutel", "Beutel", "beispiel")
    private val items = listOf(feder, beutel)
    private val base = Examples.scenario

    /** The well event first gives [step], then continues as before. */
    private fun scenarioWith(vararg steps: EventStep) =
        base.copy(events = base.events.map { e -> if (e.id == "evt_brunnen") e.copy(steps = steps.toList() + e.steps) else e })

    /** Prompts seen after touching the well, answered until the group can act again. */
    private val seen = mutableListOf<Prompt>()

    private fun touchWell(engine: GameEngine, start: GameState = engine.start(3)): GameState {
        var s = start
        while (!s.isIdle) s = engine.apply(s, Command.Acknowledge)
        s = engine.apply(s, Command.InteractToken("tok_brunnen"))
        while (!s.isIdle && !s.isOver) {
            seen += s.currentPrompt!!
            s = when (s.currentPrompt) {
                is Prompt.Test -> engine.apply(s, Command.ReportTest(0))
                is Prompt.Choice -> engine.apply(s, Command.Choose(0))
                else -> engine.apply(s, Command.Acknowledge)
            }
        }
        return s
    }

    @Test
    fun `fixed item is found and recorded`() {
        val e = GameEngine(scenarioWith(EventStep.GainItem("beutel", HeroTarget.CHOSEN, "Im Brunnen glitzert etwas.")), items = items)
        val s = touchWell(e)
        assertEquals(Prompt.ItemFound("beutel", HeroTarget.CHOSEN, "Im Brunnen glitzert etwas."), seen.first { it is Prompt.ItemFound })
        assertEquals(listOf("beutel"), s.itemsFound)
    }

    @Test
    fun `random trinket skips owned and found ones, nothing left gives null`() {
        val e = GameEngine(scenarioWith(EventStep.GainItem(), EventStep.GainItem()), items = items, ownedItems = setOf("feder"))
        val s = touchWell(e)
        val found = seen.filterIsInstance<Prompt.ItemFound>()
        assertEquals("beutel", found[0].itemId)
        assertNull(found[1].itemId)
        assertEquals(listOf("beutel"), s.itemsFound)
    }

    @Test
    fun `owned fixed item is replaced by a random one`() {
        val e = GameEngine(scenarioWith(EventStep.GainItem("feder")), items = items, ownedItems = setOf("feder"))
        assertEquals(listOf("beutel"), touchWell(e).itemsFound)
    }

    @Test
    fun `campaign keeps the group inventory and passes it on`() {
        val s1 = scenarioWith(EventStep.GainItem())
        val s2 = s1.copy(id = "scn_zwei")
        val campaign = Examples.campaign.copy(
            scenarios = listOf(s1, s2),
            transitions = s1.resolutions.map { ScenarioTransition(s1.id, it.id, "scn_zwei") },
        )
        val runner = CampaignRunner(campaign, MaterialCatalog(formatVersion = 1, items = items))
        val (p0, g0) = runner.start(3)
        val engine = runner.engineFor(campaign.startScenario)
        var st = touchWell(engine, g0)
        while (!st.isOver) st = when {
            st.isIdle -> engine.apply(st, Command.EndRound)
            st.currentPrompt is Prompt.Test -> engine.apply(st, Command.ReportTest(0))
            st.currentPrompt is Prompt.CounterQuestion -> engine.apply(st, Command.CounterAttack(false))
            else -> engine.apply(st, Command.Acknowledge)
        }
        val first = st.itemsFound.single()
        val (p1, g1) = runner.advance(p0, st)
        assertEquals(listOf(first), p1.inventory)
        assertEquals(listOf(first), p1.completed.single().items)
        assertTrue(first in g1!!.ownedItems)
        seen.clear()
        // In the second scenario only the other trinket can be found.
        val e2 = runner.engineFor("scn_zwei")
        val t = touchWell(e2, g1)
        assertEquals((items.map { it.id } - first), t.itemsFound)
    }
}
