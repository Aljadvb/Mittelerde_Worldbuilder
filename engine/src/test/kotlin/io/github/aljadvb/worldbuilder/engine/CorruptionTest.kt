package io.github.aljadvb.worldbuilder.engine

import io.github.aljadvb.worldbuilder.model.ChoiceOption
import io.github.aljadvb.worldbuilder.model.Event
import io.github.aljadvb.worldbuilder.model.EventStep
import io.github.aljadvb.worldbuilder.model.FieldMarker
import io.github.aljadvb.worldbuilder.model.FormatJson
import io.github.aljadvb.worldbuilder.model.HexCoord
import io.github.aljadvb.worldbuilder.model.HeroTarget
import io.github.aljadvb.worldbuilder.model.TileLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CorruptionTest {
    private val base = Examples.scenario
    private val spot = TileLocation("pl_dorfweg", HexCoord(1, 0))

    private fun engine(vararg steps: EventStep) = GameEngine(base.copy(
        events = base.events.map { e -> if (e.id == "evt_brunnen") e.copy(steps = steps.toList()) else e } + listOf(
            Event("evt_pluendern", listOf(EventStep.Text("Ihr nehmt das Gold."))),
            Event("evt_lassen", listOf(EventStep.Text("Ihr lasst es liegen."))),
            Event("evt_dunkel", listOf(EventStep.Text("Das Gold flüstert dir zu."))),
            Event("evt_hell", listOf(EventStep.Text("Nichts geschieht."))),
        ),
    ))

    private val grave = EventStep.Choice("Ein offenes Grab.", listOf(ChoiceOption("Das Grab plündern", "evt_pluendern", corruption = 1), ChoiceOption("Weitergehen", "evt_lassen")))

    private fun atWell(e: GameEngine, heroes: Int = 2, corruption: List<Int> = emptyList()): GameState {
        var s = e.start(heroes, corruption)
        while (!s.isIdle) s = e.apply(s, Command.Acknowledge)
        s = e.apply(s, Command.InteractToken("tok_brunnen"))
        while (s.currentPrompt is Prompt.Notice) s = e.apply(s, Command.Acknowledge)
        return s
    }

    @Test
    fun `a choice with corruption asks for the hero and adds it`() {
        val e = engine(grave)
        var s = e.apply(atWell(e), Command.Choose(0))
        assertEquals(HeroQuestion.GainCorruption(1), (s.currentPrompt as Prompt.ChooseHero).question)
        s = e.apply(s, Command.ChooseHero(1))
        assertEquals(Prompt.Corruption(1, 1, 1), s.currentPrompt)
        assertEquals(listOf(0, 1), s.corruption)
        s = e.apply(s, Command.Acknowledge)
        assertEquals("Ihr nehmt das Gold.", (s.currentPrompt as Prompt.Text).text)
        // The other option costs nothing.
        assertEquals("Ihr lasst es liegen.", (e.apply(atWell(e), Command.Choose(1)).currentPrompt as Prompt.Text).text)
    }

    @Test
    fun `corruption is laid out at the start and a fourth one means a last stand`() {
        val e = engine(grave)
        val start = e.start(2, listOf(3, 0))
        assertEquals(listOf(Prompt.Intro, Prompt.CorruptionSetup(listOf(3, 0))), start.prompts.take(2))
        var s = e.apply(atWell(e, corruption = listOf(3, 0)), Command.Choose(0))
        s = e.apply(s, Command.ChooseHero(0))
        assertEquals(Prompt.LastStandTest(0, LimitKind.CORRUPTION), s.currentPrompt)
        val passed = e.apply(s, Command.ReportTest(1))
        assertEquals(listOf(3, 0), passed.corruption)
        assertTrue(passed.lastStandUsed.isEmpty())
        val failed = e.apply(s, Command.ReportTest(0))
        assertTrue(0 in failed.defeatedHeroes)
    }

    @Test
    fun `losing and checking corruption`() {
        val e = engine(EventStep.LoseCorruption(1, HeroTarget.EACH, "Das Quellwasser reinigt euch."), EventStep.CheckCorruption("evt_dunkel", "evt_hell"))
        var s = atWell(e, corruption = listOf(2, 0))
        // atWell acknowledged the corruption notice already.
        assertEquals(listOf(1, 0), s.corruption)
        assertTrue(s.currentPrompt is Prompt.ChooseHero)
        assertEquals("Das Gold flüstert dir zu.", (e.apply(s, Command.ChooseHero(0)).currentPrompt as Prompt.Text).text)
        assertEquals("Nichts geschieht.", (e.apply(s, Command.ChooseHero(1)).currentPrompt as Prompt.Text).text)
    }

    @Test
    fun `single hero is never asked`() {
        val e = engine(grave)
        val s = e.apply(atWell(e, heroes = 1), Command.Choose(0))
        assertEquals(Prompt.Corruption(0, 1, 1), s.currentPrompt)
    }

    @Test
    fun `field markers are placed and removed`() {
        val e = engine(EventStep.PlaceMarker(FieldMarker.ROUGH, spot, "Der Regen hat den Weg aufgeweicht."), EventStep.PlaceMarker(FieldMarker.FORTIFIED, spot))
        var s = e.start(2)
        while (!s.isIdle) s = e.apply(s, Command.Acknowledge)
        s = e.apply(s, Command.InteractToken("tok_brunnen"))
        val notices = mutableListOf<Prompt>()
        while (!s.isIdle) { notices += s.currentPrompt!!; s = e.apply(s, Command.Acknowledge) }
        assertTrue(Prompt.FieldMarker(FieldMarker.ROUGH, spot, true, "Der Regen hat den Weg aufgeweicht.") in notices)
        assertEquals(listOf(PlacedMarker(FieldMarker.ROUGH, spot), PlacedMarker(FieldMarker.FORTIFIED, spot)), s.fieldMarkers)
        val json = """{"type":"placeMarker","marker":"rough","location":{"placement":"pl_dorfweg","cell":{"q":1,"r":0}}}"""
        assertEquals(EventStep.PlaceMarker(FieldMarker.ROUGH, spot), FormatJson.json.decodeFromString(EventStep.serializer(), json))
    }

    @Test
    fun `gainCorruption step for an evil deed asks for the hero`() {
        val e = engine(EventStep.GainCorruption(1, HeroTarget.ACTIVE, "Die Schattenkrone flüstert in deinem Kopf."))
        var s = atWell(e)
        assertEquals("Die Schattenkrone flüstert in deinem Kopf.", (s.currentPrompt as Prompt.ChooseHero).text)
        s = e.apply(s, Command.ChooseHero(0))
        assertEquals(Prompt.Corruption(0, 1, 1), s.currentPrompt)
    }

    @Test
    fun `hard tests can be eased by taking corruption`() {
        val test = EventStep.Test(attribute = io.github.aljadvb.worldbuilder.model.Attribute.SPIRIT, successes = 3, onSuccess = "evt_pluendern", onFailure = "evt_lassen", corruptionEase = 1)
        val e = engine(test)
        var s = atWell(e)
        val open = s.currentPrompt as Prompt.Test
        assertTrue(open.canEase)
        assertEquals(3, open.required)
        s = e.apply(s, Command.EaseTest)
        s = e.apply(s, Command.ChooseHero(1))
        assertEquals(Prompt.Corruption(1, 1, 1), s.currentPrompt)
        s = e.apply(s, Command.Acknowledge)
        val eased = s.currentPrompt as Prompt.Test
        assertEquals(2, eased.required)
        assertTrue(!eased.canEase)
        assertEquals("Ihr nehmt das Gold.", (e.apply(s, Command.ReportTest(2)).currentPrompt as Prompt.Text).text)
        // Without help 2 successes are not enough.
        assertEquals("Ihr lasst es liegen.", (e.apply(atWell(e), Command.ReportTest(2)).currentPrompt as Prompt.Text).text)
    }

    @Test
    fun `darkness markers cause fear in the shadow phase`() {
        val e = engine(EventStep.PlaceMarker(FieldMarker.DARKNESS, spot))
        var s = e.start(2)
        while (!s.isIdle) s = e.apply(s, Command.Acknowledge)
        assertTrue(e.apply(s, Command.EndRound).prompts.none { it is Prompt.Darkness })
        s = e.apply(s, Command.InteractToken("tok_brunnen"))
        while (!s.isIdle) s = e.apply(s, Command.Acknowledge)
        assertTrue(Prompt.Darkness(1) in e.apply(s, Command.EndRound).prompts)
    }

    @Test
    fun `campaign keeps corruption between scenarios`() {
        val runner = CampaignRunner(Examples.campaign)
        val (p, _) = runner.start(2, listOf(Hero("Eleanor", "", corruption = 2), Hero("Beravor", "")))
        assertEquals(listOf(Prompt.Intro, Prompt.CorruptionSetup(listOf(2, 0))), runner.startCurrent(p)!!.prompts.take(2))
    }
}
