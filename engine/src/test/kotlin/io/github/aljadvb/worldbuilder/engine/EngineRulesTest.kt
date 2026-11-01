package io.github.aljadvb.worldbuilder.engine

import io.github.aljadvb.worldbuilder.model.Attribute
import io.github.aljadvb.worldbuilder.model.Chapter
import io.github.aljadvb.worldbuilder.model.Event
import io.github.aljadvb.worldbuilder.model.EventStep
import io.github.aljadvb.worldbuilder.model.HexCoord
import io.github.aljadvb.worldbuilder.model.Objective
import io.github.aljadvb.worldbuilder.model.Outcome
import io.github.aljadvb.worldbuilder.model.Resolution
import io.github.aljadvb.worldbuilder.model.Scenario
import io.github.aljadvb.worldbuilder.model.ThreatConfig
import io.github.aljadvb.worldbuilder.model.TileLocation
import io.github.aljadvb.worldbuilder.model.TilePlacement
import io.github.aljadvb.worldbuilder.model.TileSide
import io.github.aljadvb.worldbuilder.model.Token
import io.github.aljadvb.worldbuilder.model.TokenType
import io.github.aljadvb.worldbuilder.model.Trigger
import io.github.aljadvb.worldbuilder.model.TriggerCondition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Rules checked on small hand-built scenarios. */
class EngineRulesTest {
    private val loc = TileLocation("pl_a", HexCoord.ORIGIN)

    private fun scenario(
        events: List<Event> = emptyList(),
        triggers: List<Trigger> = emptyList(),
        tokens: List<Token> = listOf(Token("tok_a", TokenType.SEARCH, label = "A", location = loc, onInteract = "evt_a")),
        chapters: List<Chapter> = emptyList(),
    ) = Scenario(
        id = "scn_t", title = "T", intro = "I",
        threat = ThreatConfig(max = 10),
        startChapter = "ch_1", startObjective = "obj_1",
        chapters = listOf(Chapter("ch_1", "Eins", tiles = listOf(TilePlacement("pl_a", "x01", TileSide.A, HexCoord.ORIGIN)), tokens = tokens)) + chapters,
        events = events,
        triggers = triggers + Trigger("trg_never", TriggerCondition.Manual),
        objectives = listOf(Objective("obj_1", "Z", "R", "trg_never")),
        resolutions = listOf(Resolution("res_end", "E", "E", Outcome.NEUTRAL)),
    )

    private fun idlePlay(s: Scenario): Play {
        val engine = GameEngine(s)
        return Play(engine, engine.start(1)).ack<Prompt.Intro>().ack<Prompt.Setup>().ack<Prompt.ObjectiveSet>().idle()
    }

    @Test
    fun `repeatable triggers fire every time, others once`() {
        val s = scenario(
            events = listOf(
                Event("evt_a", listOf(EventStep.Text("a"))),
                Event("evt_once", listOf(EventStep.Text("once"))),
                Event("evt_again", listOf(EventStep.Text("again"))),
            ),
            triggers = listOf(
                Trigger("trg_once", TriggerCondition.TokenInteracted("tok_a"), listOf("evt_once")),
                Trigger("trg_again", TriggerCondition.TokenInteracted("tok_a"), listOf("evt_again"), repeatable = true),
            ),
        )
        val play = idlePlay(s)
        play.send(Command.InteractToken("tok_a")).text("a").text("once").text("again").idle()
        play.send(Command.InteractToken("tok_a")).text("a").text("again").idle()
    }

    @Test
    fun `onUnlock runs right after the current event, before trigger events`() {
        val s = scenario(
            events = listOf(
                Event("evt_a", listOf(EventStep.UnlockChapter("ch_2"), EventStep.Text("rest of a"))),
                Event("evt_unlock", listOf(EventStep.Text("unlock"))),
                Event("evt_trigger", listOf(EventStep.Text("trigger"))),
            ),
            triggers = listOf(Trigger("trg_t", TriggerCondition.TokenInteracted("tok_a"), listOf("evt_trigger"))),
            chapters = listOf(Chapter("ch_2", "Zwei", onUnlock = "evt_unlock")),
        )
        idlePlay(s).send(Command.InteractToken("tok_a"))
            .ack<Prompt.Setup> { assertEquals("ch_2", it.chapterId) }
            .text("rest of a")
            .text("unlock")
            .text("trigger")
            .idle()
    }

    @Test
    fun `failed test without onFailure just ends the event`() {
        val s = scenario(
            events = listOf(
                Event("evt_a", listOf(EventStep.Test(attribute = Attribute.WIT, successes = 2, onSuccess = "evt_ok"))),
                Event("evt_ok", listOf(EventStep.Text("ok"))),
            ),
        )
        idlePlay(s).send(Command.InteractToken("tok_a")).expect<Prompt.Test>().send(Command.ReportTest(0)).idle()
    }

    @Test
    fun `endless loops without player input are stopped`() {
        val s = scenario(
            events = listOf(Event("evt_a", listOf(EventStep.FireTrigger("trg_loop")))),
            triggers = listOf(Trigger("trg_loop", TriggerCondition.Manual, listOf("evt_a"), repeatable = true)),
        )
        val play = idlePlay(s)
        assertFailsWith<EngineException> { play.send(Command.InteractToken("tok_a")) }
    }

    @Test
    fun `threat never drops below zero`() {
        val s = scenario(events = listOf(Event("evt_a", listOf(EventStep.ChangeThreat(-3), EventStep.ChangeThreat(4)))))
        idlePlay(s).send(Command.InteractToken("tok_a")).ack<Prompt.ThreatChanged> { assertEquals(4, it.threat) }.idle()
    }

    @Test
    fun `end scenario stops everything that is still scheduled`() {
        val s = scenario(
            events = listOf(Event("evt_a", listOf(EventStep.EndScenario("res_end"))), Event("evt_late", listOf(EventStep.Text("late")))),
            triggers = listOf(Trigger("trg_late", TriggerCondition.TokenInteracted("tok_a"), listOf("evt_late"))),
        )
        val play = idlePlay(s).send(Command.InteractToken("tok_a"))
        assertEquals("res_end", play.state.resolution)
        assertEquals(emptyList(), play.state.agenda)
    }
}
