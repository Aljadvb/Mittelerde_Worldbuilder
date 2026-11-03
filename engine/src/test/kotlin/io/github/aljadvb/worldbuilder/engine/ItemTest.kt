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
        val e = GameEngine(scenarioWith(EventStep.GainItem(), EventStep.GainItem()), items = items, ownedItems = listOf("feder"))
        val s = touchWell(e)
        val found = seen.filterIsInstance<Prompt.ItemFound>()
        assertEquals("beutel", found[0].itemId)
        assertNull(found[1].itemId)
        assertEquals(listOf("beutel"), s.itemsFound)
    }

    @Test
    fun `owned fixed item is replaced by a random one`() {
        val e = GameEngine(scenarioWith(EventStep.GainItem("feder")), items = items, ownedItems = listOf("feder"))
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

class TestKindsTest {
    private val base = Examples.scenario

    private fun scenarioWith(test: io.github.aljadvb.worldbuilder.model.EventStep.Test) = base.copy(
        events = base.events.map { e -> if (e.id == "evt_brunnen") e.copy(steps = listOf(io.github.aljadvb.worldbuilder.model.EventStep.Text("Der Stein."), test)) else e } +
            listOf(
                Event("evt_ok", listOf(EventStep.Text("Geschafft!"))),
                Event("evt_nein", listOf(EventStep.Text("Leider nicht."))),
                Event("evt_knirsch", listOf(EventStep.Text("Es knirscht."))),
            ),
        chapters = base.chapters.map { ch -> ch.copy(tokens = ch.tokens.map { if (it.id == "tok_brunnen") it.copy(removeAfterInteract = false) else it }) },
    )

    private fun untilTest(e: GameEngine, s0: GameState): GameState {
        var s = s0
        while (!s.isIdle) s = e.apply(s, Command.Acknowledge)
        s = e.apply(s, Command.InteractToken("tok_brunnen"))
        while (s.currentPrompt !is Prompt.Test) s = e.apply(s, Command.Acknowledge)
        return s
    }

    @Test
    fun `hidden test reports only the result`() {
        val e = GameEngine(scenarioWith(EventStep.Test(attribute = io.github.aljadvb.worldbuilder.model.Attribute.WISDOM, successes = 3, onSuccess = "evt_ok", onFailure = "evt_nein", hidden = true)))
        val s = untilTest(e, e.start(2))
        val failed = e.apply(s, Command.ReportTest(2))
        assertEquals(Prompt.TestResult(false), failed.currentPrompt)
        assertEquals("Leider nicht.", (e.apply(failed, Command.Acknowledge).currentPrompt as Prompt.Text).text)
        assertEquals(Prompt.TestResult(true), e.apply(s, Command.ReportTest(3)).currentPrompt)
    }

    @Test
    fun `cumulative test collects successes over attempts`() {
        val test = EventStep.Test(attribute = io.github.aljadvb.worldbuilder.model.Attribute.MIGHT, successes = 6, onSuccess = "evt_ok", cumulative = true, onAttempt = "evt_knirsch")
        val e = GameEngine(scenarioWith(test))
        var s = untilTest(e, e.start(3))
        s = e.apply(s, Command.ReportTest(2))
        assertEquals(Prompt.TestProgress(test, 2), s.currentPrompt)
        s = e.apply(s, Command.Acknowledge)
        assertEquals("Es knirscht.", (s.currentPrompt as Prompt.Text).text)
        while (!s.isIdle) s = e.apply(s, Command.Acknowledge)
        s = e.apply(s, Command.EndRound)
        while (!s.isIdle) s = if (s.currentPrompt is Prompt.CounterQuestion) e.apply(s, Command.CounterAttack(false)) else e.apply(s, Command.Acknowledge)
        s = untilTest(e, s)
        assertEquals(2, (s.currentPrompt as Prompt.Test).progress)
        s = e.apply(s, Command.ReportTest(4))
        assertEquals("Geschafft!", (s.currentPrompt as Prompt.Text).text)
        assertTrue(s.testProgress.isEmpty())
    }
}

class ItemLevelTest {
    private val items = listOf(
        Item("stiefel", "Stiefel", "b", line = "stiefel", level = 1, lore = 20),
        Item("gruen", "Grünweg", "b", line = "stiefel", level = 2, lore = 50),
        Item("brosche", "Brosche", "b", line = "brosche", level = 1, lore = 26),
    )

    @Test
    fun `upgrade needs enough lore and moves to the next level`() {
        val p = CampaignProgress(heroCount = 2, currentScenario = null, inventory = listOf("stiefel"), lore = 19)
        assertTrue(Camp.upgradable(p, items).isEmpty())
        kotlin.test.assertFailsWith<EngineException> { Camp.upgradeItem(p, "stiefel", items) }
        val up = Camp.upgradeItem(p.copy(lore = 20), "stiefel", items)
        assertEquals(listOf("gruen"), up.inventory)
        kotlin.test.assertFailsWith<EngineException> { Camp.upgradeItem(up.copy(lore = 99), "gruen", items) }
        kotlin.test.assertFailsWith<EngineException> { Camp.upgradeItem(p.copy(lore = 20), "stiefel", items, "brosche") }
    }

    @Test
    fun `random finds skip owned lines and higher levels`() {
        val s0 = Examples.scenario
        val sc = s0.copy(events = s0.events.map { e -> if (e.id == "evt_brunnen") e.copy(steps = listOf(EventStep.GainItem()) + e.steps) else e })
        val e = GameEngine(sc, items = items, ownedItems = listOf("gruen"))
        var s = e.start(2)
        while (!s.isIdle) s = e.apply(s, Command.Acknowledge)
        s = e.apply(s, Command.InteractToken("tok_brunnen"))
        while (s.itemsFound.isEmpty() && !s.isIdle) s = if (s.currentPrompt is Prompt.Test) e.apply(s, Command.ReportTest(0)) else e.apply(s, Command.Acknowledge)
        assertEquals(listOf("brosche"), s.itemsFound)
    }
}

class ItemCopiesTest {
    private val items = listOf(
        Item("extra", "Extrarationen", "b", line = "rationen", level = 1, lore = 24, count = 2),
        Item("bree", "Breeländische Rationen", "b", line = "rationen", level = 2, lore = 48),
        Item("hobbit", "Hobbitrationen", "b", line = "rationen", level = 2, lore = 48),
    )

    private fun find(owned: List<String>): List<String> {
        val s0 = Examples.scenario
        val sc = s0.copy(events = s0.events.map { e -> if (e.id == "evt_brunnen") e.copy(steps = listOf(EventStep.GainItem()) + e.steps) else e })
        val e = GameEngine(sc, items = items, ownedItems = owned)
        var s = e.start(2)
        while (!s.isIdle) s = e.apply(s, Command.Acknowledge)
        s = e.apply(s, Command.InteractToken("tok_brunnen"))
        while (s.itemsFound.isEmpty() && !s.isIdle) s = if (s.currentPrompt is Prompt.Test) e.apply(s, Command.ReportTest(0)) else e.apply(s, Command.Acknowledge)
        return s.itemsFound
    }

    @Test
    fun `a card with two copies can be found twice and upgraded separately`() {
        assertEquals(listOf("extra"), find(emptyList()))
        assertEquals(listOf("extra"), find(listOf("bree")))
        assertEquals(emptyList(), find(listOf("bree", "extra")))
        val p = CampaignProgress(heroCount = 2, currentScenario = null, inventory = listOf("extra", "extra"), lore = 30)
        val one = Camp.upgradeItem(p, "extra", items, "hobbit")
        assertEquals(listOf("hobbit", "extra"), one.inventory)
        assertEquals(listOf("hobbit", "bree"), Camp.upgradeItem(one, "extra", items, "bree").inventory)
    }
}

class NextChoiceTest {
    @Test
    fun `several transitions are an either-or choice in camp`() {
        val s = Examples.scenario
        val a = s.copy(id = "scn_a"); val b = s.copy(id = "scn_b")
        val campaign = Examples.campaign.copy(
            scenarios = listOf(s, a, b),
            transitions = s.resolutions.flatMap { r -> listOf(io.github.aljadvb.worldbuilder.model.ScenarioTransition(s.id, r.id, "scn_a"), io.github.aljadvb.worldbuilder.model.ScenarioTransition(s.id, r.id, "scn_b")) },
        )
        val runner = CampaignRunner(campaign)
        val (p0, g0) = runner.start(2)
        val e = runner.engineFor(s.id)
        var st = g0
        while (!st.isOver) st = when {
            st.isIdle -> e.apply(st, Command.EndRound)
            st.currentPrompt is Prompt.Test -> e.apply(st, Command.ReportTest(0))
            st.currentPrompt is Prompt.CounterQuestion -> e.apply(st, Command.CounterAttack(false))
            else -> e.apply(st, Command.Acknowledge)
        }
        val (p1, g1) = runner.advance(p0, st)
        assertEquals(listOf("scn_a", "scn_b"), p1.nextOptions)
        assertEquals("scn_a", g1!!.scenarioId)
        val chosen = Camp.chooseNext(p1, "scn_b")
        assertEquals("scn_b", runner.startCurrent(chosen)!!.scenarioId)
        kotlin.test.assertFailsWith<EngineException> { Camp.chooseNext(p1, "scn_x") }
        assertTrue(Camp.leave(chosen).nextOptions.isEmpty())
    }
}
