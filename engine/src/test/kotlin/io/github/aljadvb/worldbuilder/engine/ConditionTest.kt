package io.github.aljadvb.worldbuilder.engine

import io.github.aljadvb.worldbuilder.model.Condition
import io.github.aljadvb.worldbuilder.model.EventStep
import io.github.aljadvb.worldbuilder.model.FormatJson
import io.github.aljadvb.worldbuilder.model.HeroTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConditionTest {
    private val base = Examples.scenario
    private val scenario = base.copy(events = base.events.map { e ->
        if (e.id == "evt_brunnen") e.copy(steps = listOf(
            EventStep.GainCondition(Condition.HIDDEN, HeroTarget.CHOSEN, "Ihr duckt euch hinter den Brunnen."),
            EventStep.GainCondition(Condition.CAPTURED),
        ) + e.steps) else e
    })

    @Test
    fun `gainCondition shows boon and curse notices`() {
        val engine = GameEngine(scenario)
        var s = engine.start(2)
        while (!s.isIdle) s = engine.apply(s, Command.Acknowledge)
        s = engine.apply(s, Command.InteractToken("tok_brunnen"))
        val seen = mutableListOf<Prompt.Condition>()
        while (!s.isIdle && !s.isOver) {
            (s.currentPrompt as? Prompt.Condition)?.let(seen::add)
            s = when (s.currentPrompt) {
                is Prompt.Test -> engine.apply(s, Command.ReportTest(0))
                is Prompt.Choice -> engine.apply(s, Command.Choose(0))
                else -> engine.apply(s, Command.Acknowledge)
            }
        }
        assertEquals(listOf(
            Prompt.Condition(Condition.HIDDEN, HeroTarget.CHOSEN, "Ihr duckt euch hinter den Brunnen."),
            Prompt.Condition(Condition.CAPTURED, HeroTarget.ACTIVE),
        ), seen)
        assertTrue(Condition.CAPTURED.isCurse && !Condition.HIDDEN.isCurse)
    }

    @Test
    fun `step round-trips through json`() {
        val json = """{"type":"gainCondition","condition":"frightened","who":"each"}"""
        assertEquals(EventStep.GainCondition(Condition.FRIGHTENED, HeroTarget.EACH), FormatJson.json.decodeFromString(EventStep.serializer(), json))
    }
}
