package io.github.aljadvb.worldbuilder.engine

import io.github.aljadvb.worldbuilder.model.Event
import io.github.aljadvb.worldbuilder.model.EventStep
import io.github.aljadvb.worldbuilder.model.HeroTarget
import io.github.aljadvb.worldbuilder.model.Trigger
import io.github.aljadvb.worldbuilder.model.TriggerCondition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExplorationTest {
    /** The example with the village path unexplored, an explore event and a trigger. */
    private val scenario = Examples.scenario.let { s ->
        s.copy(
            chapters = s.chapters.map { ch ->
                ch.copy(tiles = ch.tiles.map { if (it.id == "pl_dorfplatz") it.copy(unexplored = true, onExplore = "evt_platz_erkundet") else it })
            },
            events = s.events + Event("evt_platz_erkundet", listOf(EventStep.Text("Der Platz liegt still da."), EventStep.GainInspiration(2, HeroTarget.EACH))),
            triggers = s.triggers + Trigger("trg_platz", TriggerCondition.TileExplored("pl_dorfplatz"), events = listOf("evt_trg_text")),
        ).let { it.copy(events = it.events + Event("evt_trg_text", listOf(EventStep.Text("Trigger!")))) }
    }
    private val engine = GameEngine(scenario)

    private fun idle(): GameState {
        var s = engine.start(2)
        while (!s.isIdle) s = engine.apply(s, Command.Acknowledge)
        return s
    }

    @Test
    fun `tokens on unexplored tiles appear only after exploring`() {
        val s = idle()
        assertEquals(setOf("pl_dorfplatz"), s.unexploredPlacements(scenario))
        assertFalse("tok_brunnen" in s.activeTokens)
        assertFailsWith<EngineException> { engine.apply(s, Command.InteractToken("tok_brunnen")) }

        Play(engine, engine.apply(s, Command.ExploreTile("pl_dorfplatz")))
            .ack<Prompt.Explored> { assertEquals(setOf("tok_dorfbewohnerin", "tok_brunnen"), it.tokenIds.toSet()) }
            .ack<Prompt.Text> { assertEquals("Der Platz liegt still da.", it.text) }
            .ack<Prompt.Inspiration> { assertEquals(2, it.amount); assertEquals(HeroTarget.EACH, it.who) }
            .ack<Prompt.Text> { assertEquals("Trigger!", it.text) }
            .also { p ->
                assertTrue(p.state.isIdle)
                assertTrue("tok_brunnen" in p.state.activeTokens)
                assertTrue(p.state.unexploredPlacements(scenario).isEmpty())
                assertFailsWith<EngineException> { engine.apply(p.state, Command.ExploreTile("pl_dorfplatz")) }
            }
    }

    @Test
    fun `tokens added onto an unexplored tile wait until it is explored`() {
        val moved = scenario.copy(
            chapters = scenario.chapters.map { ch ->
                ch.copy(tokens = ch.tokens.map { if (it.id == "tok_spuren") it.copy(location = io.github.aljadvb.worldbuilder.model.TileLocation("pl_dorfplatz", io.github.aljadvb.worldbuilder.model.HexCoord(2, 0))) else it })
            },
            events = scenario.events + Event("evt_add", listOf(EventStep.AddToken("tok_spuren"))),
            triggers = scenario.triggers + Trigger("trg_add", TriggerCondition.RoundEnded(1), events = listOf("evt_add")),
        )
        val e = GameEngine(moved)
        var s = e.start(2)
        while (!s.isIdle) s = e.apply(s, Command.Acknowledge)
        s = e.apply(s, Command.EndRound)
        while (!s.isIdle) {
            assertFalse(s.currentPrompt is Prompt.PlaceToken)
            s = if (s.currentPrompt is Prompt.CounterQuestion) e.apply(s, Command.CounterAttack(false)) else e.apply(s, Command.Acknowledge)
        }
        assertFalse("tok_spuren" in s.activeTokens)
        assertTrue("tok_spuren" in s.pendingTokens)
        s = e.apply(s, Command.ExploreTile("pl_dorfplatz"))
        assertTrue("tok_spuren" in (s.currentPrompt as Prompt.Explored).tokenIds)
        assertTrue("tok_spuren" in s.activeTokens)
        assertTrue(s.pendingTokens.isEmpty())
    }

    @Test
    fun `exploring needs an idle game and an unexplored tile`() {
        assertFailsWith<EngineException> { engine.apply(idle(), Command.ExploreTile("pl_dorfweg")) }
        assertFailsWith<EngineException> { engine.apply(engine.start(2), Command.ExploreTile("pl_dorfplatz")) }
    }

    @Test
    fun `explored state survives saving`() {
        val s = engine.apply(idle(), Command.ExploreTile("pl_dorfplatz"))
        val json = kotlinx.serialization.json.Json.encodeToString(GameState.serializer(), s)
        assertEquals(s, kotlinx.serialization.json.Json.decodeFromString(GameState.serializer(), json))
    }
}
