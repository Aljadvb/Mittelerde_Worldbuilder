package io.github.aljadvb.worldbuilder.editing

import io.github.aljadvb.worldbuilder.model.Attribute
import io.github.aljadvb.worldbuilder.model.ChoiceOption
import io.github.aljadvb.worldbuilder.model.EventStep
import io.github.aljadvb.worldbuilder.model.HexCoord
import io.github.aljadvb.worldbuilder.model.TileLocation
import io.github.aljadvb.worldbuilder.model.TileSide
import io.github.aljadvb.worldbuilder.model.TokenType
import io.github.aljadvb.worldbuilder.model.TriggerCondition
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Builds a two-chapter scenario purely through the edit operations the editor uses,
 * the way an author would, and expects a campaign without any findings.
 */
class BuildScenarioTest {
    @Test
    fun `scenario built with edit operations has no findings`() {
        var c = newCampaign("Der verlorene Pfad")
        val s = c.scenarios.single()
        val (victory, defeat) = s.resolutions.map { it.id }
        fun step(created: Created): String { c = created.campaign; return created.id }

        c = c.updateScenario(s.id) { it.copy(intro = "Ein Pfad verschwindet im Wald.") }
            .updateResolution(victory) { it.copy(text = "Der Pfad ist gefunden.") }
            .updateResolution(defeat) { it.copy(text = "Der Wald verschluckt euch.") }
            .updateChapter(s.startChapter) { it.copy(title = "Waldrand", setupText = "Hohe Tannen.") }

        // Chapter 1: two tiles, a person and a hidden clue
        val rand = step(c.addPlacement(s.startChapter, "x01", TileSide.A, HexCoord(0, 0)))
        val weg = step(c.addPlacement(s.startChapter, "x02", TileSide.A, HexCoord(2, 0)))
        val jaeger = step(c.addToken(s.startChapter, "Jäger", TokenType.PERSON, TileLocation(rand, HexCoord(1, 0))))
        val spur = step(c.addToken(s.startChapter, "Spur", TokenType.EXPLORE, TileLocation(weg, HexCoord(2, 0))))
        c = c.updateToken(spur) { it.copy(initiallyActive = false, removeAfterInteract = true) }

        // Chapter 2 with a tile, a goal token and an enemy group
        val lichtung = step(c.addChapter(s.id, "Lichtung"))
        val lichtungTile = step(c.addPlacement(lichtung, "x03", TileSide.A, HexCoord(6, -1)))
        val stein = step(c.addToken(lichtung, "Runenstein", TokenType.SEARCH, TileLocation(lichtungTile, HexCoord.ORIGIN)))
        val woelfe = step(c.addEnemyGroup(s.id, "Wölfe", "nebelwolf", TileLocation(lichtungTile, HexCoord(1, 0))))

        // Events
        val gespraech = step(c.addEvent(s.id, "Gespräch mit dem Jäger"))
        val hinweis = step(c.addEvent(s.id, "Hinweis", listOf(EventStep.Text("Er zeigt nach Osten."), EventStep.AddToken(spur))))
        val schweigen = step(c.addEvent(s.id, "Schweigen", listOf(EventStep.Text("Er schweigt."), EventStep.ChangeThreat(1), EventStep.AddToken(spur))))
        c = c.updateEvent(gespraech) {
            it.copy(steps = listOf(EventStep.Text("Der Jäger mustert euch."), EventStep.Choice("Was tut ihr?", listOf(ChoiceOption("Fragen", hinweis), ChoiceOption("Gehen", schweigen)))))
        }
        val spurFolgen = step(c.addEvent(s.id, "Spur folgen", listOf(EventStep.UnlockChapter(lichtung))))
        val ankunft = step(c.addEvent(s.id, "Ankunft Lichtung", listOf(EventStep.Text("Knurren."), EventStep.SpawnEnemies(woelfe))))
        val runen = step(c.addEvent(s.id, "Runen lesen"))
        val gelesen = step(c.addEvent(s.id, "Runen gelesen", listOf(EventStep.Text("Der Pfad leuchtet auf."), EventStep.EndScenario(victory))))
        c = c.updateEvent(runen) { it.copy(steps = listOf(EventStep.Test("Entziffert die Runen.", Attribute.WISDOM, 2, gelesen))) }
        c = c.updateToken(jaeger) { it.copy(onInteract = gespraech, removeAfterInteract = true) }
            .updateToken(spur) { it.copy(onInteract = spurFolgen) }
            .updateToken(stein) { it.copy(onInteract = runen) }
            .updateChapter(lichtung) { it.copy(onUnlock = ankunft, setupText = "Eine Lichtung.") }

        // Objectives and triggers
        val spurGefunden = step(c.addTrigger(s.id, "Spur gefunden", TriggerCondition.TokenInteracted(spur)))
        c = c.updateObjective(s.startObjective) { it.copy(title = "Findet den Pfad", reminder = "Fragt den Jäger.", completedBy = spurGefunden) }
        val zweitesZiel = step(c.addObjective(s.id, "Lest die Runen"))
        val runenTrigger = step(c.addTrigger(s.id, "Runenstein untersucht", TriggerCondition.TokenInteracted(stein)))
        c = c.updateObjective(zweitesZiel) { it.copy(reminder = "Der Stein auf der Lichtung.", completedBy = runenTrigger) }
            .updateEvent(spurFolgen) { it.copy(steps = it.steps + EventStep.SetObjective(zweitesZiel)) }
        val woelfeWeg = step(c.addEvent(s.id, "Wölfe vertrieben", listOf(EventStep.ChangeThreat(-2))))
        step(c.addTrigger(s.id, "Wölfe besiegt", TriggerCondition.EnemyGroupDefeated(woelfe)))
        c = c.updateTrigger(c.scenarios.single().triggers.last().id) { it.copy(events = listOf(woelfeWeg)) }

        // Rename something late: references follow
        c = c.rename(jaeger, "tok_alter_jaeger")

        assertEquals("", c.validate().format())
    }
}
