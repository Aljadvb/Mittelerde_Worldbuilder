package io.github.aljadvb.worldbuilder.engine

import io.github.aljadvb.worldbuilder.model.EventStep
import io.github.aljadvb.worldbuilder.model.HeroTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TitleTest {
    private val base = Examples.scenario
    private fun engine(vararg steps: EventStep) = GameEngine(base.copy(events = base.events.map { e -> if (e.id == "evt_brunnen") e.copy(steps = steps.toList()) else e }))

    private fun atWell(e: GameEngine, titles: Set<String> = emptySet()): GameState {
        var s = e.start(2, titles = titles)
        while (!s.isIdle) s = e.apply(s, Command.Acknowledge)
        s = e.apply(s, Command.InteractToken("tok_brunnen"))
        // Skip the token description.
        while (s.currentPrompt is Prompt.Inspect) s = e.apply(s, Command.Acknowledge)
        return s
    }

    @Test
    fun `a title goes to the chosen hero and only once`() {
        val e = engine(EventStep.GainTitle("titel_zwergenfreund", "Die Zwerge ehren euch."), EventStep.GainTitle("titel_zwergenfreund"))
        var s = atWell(e)
        assertEquals(HeroQuestion.GainTitle("titel_zwergenfreund"), (s.currentPrompt as Prompt.ChooseHero).question)
        s = e.apply(s, Command.ChooseHero(1))
        assertEquals(Prompt.TitleGained(1, "titel_zwergenfreund"), s.currentPrompt)
        s = e.apply(s, Command.Acknowledge)
        assertTrue(s.isIdle)
        assertEquals(listOf(HeroTitle(1, "titel_zwergenfreund")), s.titlesGained)
        // Already owned from an earlier scenario: nothing happens.
        assertTrue(atWell(e, titles = setOf("titel_zwergenfreund")).isIdle)
    }

    @Test
    fun `weakness is a notice`() {
        val s = atWell(engine(EventStep.GainWeakness(1, HeroTarget.EACH, "Der Weg zehrt an euch.")))
        assertEquals(Prompt.Weakness(1, HeroTarget.EACH, "Der Weg zehrt an euch."), s.currentPrompt)
    }

    @Test
    fun `campaign keeps titles`() {
        val runner = CampaignRunner(Examples.campaign)
        val (p, _) = runner.start(2, listOf(Hero("Eleanor", "", titles = listOf("titel_x")), Hero("Beravor", "")))
        assertEquals(setOf("titel_x"), runner.startCurrent(p)!!.ownedTitles)
    }
}
