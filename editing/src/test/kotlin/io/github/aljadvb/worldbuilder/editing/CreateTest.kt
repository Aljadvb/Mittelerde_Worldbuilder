package io.github.aljadvb.worldbuilder.editing

import io.github.aljadvb.worldbuilder.model.EventStep
import io.github.aljadvb.worldbuilder.model.HexCoord
import io.github.aljadvb.worldbuilder.model.Outcome
import io.github.aljadvb.worldbuilder.model.TileLocation
import io.github.aljadvb.worldbuilder.model.TileSide
import io.github.aljadvb.worldbuilder.model.TokenType
import io.github.aljadvb.worldbuilder.model.TriggerCondition
import io.github.aljadvb.worldbuilder.validation.IssueCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CreateTest {
    @Test
    fun `new campaign skeleton only lacks the content an author must add`() {
        val c = newCampaign("Meine Kampagne")
        assertEquals("cmp_meine_kampagne", c.id)
        assertEquals(c.scenarios.single().id, c.startScenario)
        val codes = c.validate().errors.map { it.code }.toSet()
        // only the empty intro and the objective without trigger are missing
        val expected = setOf(IssueCode.BLANK_TEXT, IssueCode.UNKNOWN_REFERENCE, IssueCode.OBJECTIVE_NOT_COMPLETABLE)
        assertTrue(expected.containsAll(codes), c.validate().format())
    }

    @Test
    fun `objects can be created and wired into a playable scenario`() {
        var c = newCampaign("Test")
        val s = c.scenarios.single()
        c = c.updateScenario(s.id) { it.copy(intro = "Es war einmal.") }
        c = c.updateResolution(s.resolutions[0].id) { it.copy(text = "Gewonnen.") }
        c = c.updateResolution(s.resolutions[1].id) { it.copy(text = "Verloren.") }
        c = c.addPlacement(s.startChapter, "x01", TileSide.A, HexCoord(0, 0)).campaign
        val placement = c.scenario(s.id)!!.placements.single().id
        val (c1, token) = c.addToken(s.startChapter, "Alte Truhe", TokenType.SEARCH, TileLocation(placement, HexCoord(1, 0)))
        val (c2, win) = c1.addEvent(s.id, "Truhe geöffnet", listOf(EventStep.Text("Gold!"), EventStep.EndScenario(s.resolutions[0].id)))
        val (c3, trigger) = c2.addTrigger(s.id, "Truhe untersucht", TriggerCondition.TokenInteracted(token))
        c = c3.updateToken(token) { it.copy(onInteract = win) }
            .updateObjective(s.startObjective) { it.copy(reminder = "Öffnet die Truhe.", completedBy = trigger) }
        assertEquals("tok_alte_truhe", token)
        assertEquals("", c.validate().format())
    }

    @Test
    fun `list move helper`() {
        assertEquals(listOf("b", "a", "c"), listOf("a", "b", "c").moved(0, 1))
        assertEquals(listOf("a", "b", "c"), listOf("a", "b", "c").moved(5, 1))
    }

    @Test
    fun `added resolution gets a unique id`() {
        val c = newCampaign("Test")
        val (_, id) = c.addResolution(c.startScenario, "Sieg", Outcome.VICTORY)
        assertTrue(id.startsWith("res_sieg"))
        assertTrue(id !in c.allIds())
    }
}
