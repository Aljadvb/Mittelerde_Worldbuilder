package io.github.aljadvb.worldbuilder.engine

import io.github.aljadvb.worldbuilder.model.EventStep
import io.github.aljadvb.worldbuilder.model.FormatJson
import io.github.aljadvb.worldbuilder.model.HeroTarget
import kotlin.test.Test
import kotlin.test.assertEquals

class HarmTest {
    @Test
    fun `failed test leads to damage and fear notices`() {
        val s = Examples.scenario
        val sc = s.copy(events = s.events.map { e ->
            if (e.id == "evt_brunnen") e.copy(steps = listOf(EventStep.SufferHarm(damage = 2, fear = 1, hidden = true, text = "Der Rand bricht weg."))) else e
        })
        val engine = GameEngine(sc)
        var st = engine.start(2)
        while (!st.isIdle) st = engine.apply(st, Command.Acknowledge)
        Play(engine, engine.apply(st, Command.InteractToken("tok_brunnen")))
            .also { p -> while (p.state.currentPrompt !is Prompt.Harm) p.send(Command.Acknowledge) }
            .ack<Prompt.Harm> {
                assertEquals(2, it.damage); assertEquals(1, it.fear)
                assertEquals(HeroTarget.ACTIVE, it.who); assertEquals(true, it.hidden)
            }
    }

    @Test
    fun `step round-trips through json`() {
        val json = """{"type":"sufferHarm","fear":2,"who":"each"}"""
        val step = FormatJson.json.decodeFromString(EventStep.serializer(), json)
        assertEquals(EventStep.SufferHarm(fear = 2, who = HeroTarget.EACH), step)
    }
}
