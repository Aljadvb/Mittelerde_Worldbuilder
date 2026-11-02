package io.github.aljadvb.worldbuilder.editing

import io.github.aljadvb.worldbuilder.model.CURRENT_FORMAT_VERSION
import io.github.aljadvb.worldbuilder.model.Battlefield
import io.github.aljadvb.worldbuilder.model.Campaign
import io.github.aljadvb.worldbuilder.model.Chapter
import io.github.aljadvb.worldbuilder.model.EnemyGroup
import io.github.aljadvb.worldbuilder.model.Event
import io.github.aljadvb.worldbuilder.model.EventStep
import io.github.aljadvb.worldbuilder.model.HexCoord
import io.github.aljadvb.worldbuilder.model.Objective
import io.github.aljadvb.worldbuilder.model.Outcome
import io.github.aljadvb.worldbuilder.model.Resolution
import io.github.aljadvb.worldbuilder.model.Scenario
import io.github.aljadvb.worldbuilder.model.ThreatConfig
import io.github.aljadvb.worldbuilder.model.ThreatPerRound
import io.github.aljadvb.worldbuilder.model.Location
import io.github.aljadvb.worldbuilder.model.TilePlacement
import io.github.aljadvb.worldbuilder.model.TileSide
import io.github.aljadvb.worldbuilder.model.Token
import io.github.aljadvb.worldbuilder.model.TokenType
import io.github.aljadvb.worldbuilder.model.Trigger
import io.github.aljadvb.worldbuilder.model.TriggerCondition
import io.github.aljadvb.worldbuilder.validation.ObjectKind

/** Rejected edit; the message is shown to the user (German). */
class EditException(message: String) : IllegalArgumentException(message)

/** Result of creating an object. */
data class Created(val campaign: Campaign, val id: String)

// ---- new documents -------------------------------------------------------------------

fun newCampaign(title: String): Campaign {
    val id = Ids.fromTitle(ObjectKind.CAMPAIGN, title, emptySet())
    val empty = Campaign(CURRENT_FORMAT_VERSION, id, title, startScenario = "", scenarios = emptyList())
    val (withScenario, scenarioId) = empty.addScenario("Erstes Szenario")
    return withScenario.copy(startScenario = scenarioId)
}

/**
 * Adds a scenario skeleton: one chapter, a start objective (still without trigger),
 * a victory and a defeat resolution, and a threat-max trigger leading to defeat.
 */
fun Campaign.addScenario(title: String): Created {
    val taken = allIds().toMutableSet()
    fun id(kind: ObjectKind, t: String) = Ids.fromTitle(kind, t, taken).also { taken += it }
    val scenarioId = id(ObjectKind.SCENARIO, title)
    val base = scenarioId.removePrefix(ObjectKind.SCENARIO.prefix)
    val chapterId = id(ObjectKind.CHAPTER, "${base}_1")
    val objectiveId = id(ObjectKind.OBJECTIVE, "${base}_ziel")
    val victoryId = id(ObjectKind.RESOLUTION, "${base}_sieg")
    val defeatId = id(ObjectKind.RESOLUTION, "${base}_niederlage")
    val defeatEventId = id(ObjectKind.EVENT, "${base}_niederlage")
    val threatMaxId = id(ObjectKind.TRIGGER, "${base}_bedrohung_max")
    val scenario = Scenario(
        id = scenarioId,
        title = title,
        intro = "",
        threat = ThreatConfig(max = 20, perRound = ThreatPerRound(base = 1, perHero = 1)),
        startChapter = chapterId,
        startObjective = objectiveId,
        chapters = listOf(Chapter(chapterId, "Kapitel 1")),
        events = listOf(Event(defeatEventId, listOf(EventStep.EndScenario(defeatId)))),
        triggers = listOf(Trigger(threatMaxId, TriggerCondition.ThreatMax, listOf(defeatEventId))),
        objectives = listOf(Objective(objectiveId, "Erstes Ziel", "", completedBy = "")),
        resolutions = listOf(
            Resolution(victoryId, "Sieg", "", Outcome.VICTORY),
            Resolution(defeatId, "Niederlage", "", Outcome.DEFEAT),
        ),
    )
    val updated = copy(scenarios = scenarios + scenario)
    return Created(if (startScenario.isEmpty()) updated.copy(startScenario = scenarioId) else updated, scenarioId)
}

// ---- creating objects inside a scenario ----------------------------------------------

fun Campaign.addChapter(scenarioId: String, title: String): Created {
    val id = Ids.fromTitle(ObjectKind.CHAPTER, title, allIds())
    return Created(updateScenario(scenarioId) { it.copy(chapters = it.chapters + Chapter(id, title)) }, id)
}

fun Campaign.addEvent(scenarioId: String, name: String, steps: List<EventStep> = listOf(EventStep.Text(""))): Created {
    val id = Ids.fromTitle(ObjectKind.EVENT, name, allIds())
    return Created(updateScenario(scenarioId) { it.copy(events = it.events + Event(id, steps)) }, id)
}

fun Campaign.addTrigger(scenarioId: String, name: String, condition: TriggerCondition = TriggerCondition.Manual): Created {
    val id = Ids.fromTitle(ObjectKind.TRIGGER, name, allIds())
    return Created(updateScenario(scenarioId) { it.copy(triggers = it.triggers + Trigger(id, condition)) }, id)
}

fun Campaign.addObjective(scenarioId: String, title: String): Created {
    val id = Ids.fromTitle(ObjectKind.OBJECTIVE, title, allIds())
    return Created(updateScenario(scenarioId) { it.copy(objectives = it.objectives + Objective(id, title, "", "")) }, id)
}

fun Campaign.addResolution(scenarioId: String, title: String, outcome: Outcome): Created {
    val id = Ids.fromTitle(ObjectKind.RESOLUTION, title, allIds())
    return Created(updateScenario(scenarioId) { it.copy(resolutions = it.resolutions + Resolution(id, title, "", outcome)) }, id)
}

fun Campaign.addEnemyGroup(scenarioId: String, name: String, enemyType: String, location: Location): Created {
    val id = Ids.fromTitle(ObjectKind.ENEMY_GROUP, name, allIds())
    val group = EnemyGroup(id, enemyType, (1..5).associateWith { 1 }, location)
    return Created(updateScenario(scenarioId) { it.copy(enemyGroups = it.enemyGroups + group) }, id)
}

fun Campaign.addToken(chapterId: String, label: String, type: TokenType, location: Location): Created {
    val id = Ids.fromTitle(ObjectKind.TOKEN, label, allIds())
    val token = Token(id, type, customType = if (type == TokenType.CUSTOM) "Eigenes" else null, label = label, location = location)
    return Created(updateChapter(chapterId) { it.copy(tokens = it.tokens + token) }, id)
}

fun Campaign.addPlacement(chapterId: String, tile: String, side: TileSide, position: HexCoord, rotation: Int = 0): Created {
    val id = Ids.fromTitle(ObjectKind.PLACEMENT, tile, allIds())
    val placement = TilePlacement(id, tile, side, position, Math.floorMod(rotation, 6))
    return Created(updateChapter(chapterId) { it.copy(tiles = it.tiles + placement) }, id)
}

// ---- updating ------------------------------------------------------------------------

fun Campaign.updateScenario(id: String, edit: (Scenario) -> Scenario): Campaign {
    if (scenarios.none { it.id == id }) throw EditException("Szenario '$id' nicht gefunden.")
    return copy(scenarios = scenarios.map { if (it.id == id) edit(it) else it })
}

/** Scenario containing the object with [id], or null. */
fun Campaign.scenarioOf(id: String): Scenario? = definition(id)?.scenarioId?.let { scenario(it) }

private fun Campaign.updateOwningScenario(id: String, edit: (Scenario) -> Scenario): Campaign {
    val scenario = scenarioOf(id) ?: throw EditException("Objekt '$id' nicht gefunden.")
    return updateScenario(scenario.id, edit)
}

private inline fun <T> List<T>.replace(match: (T) -> Boolean, edit: (T) -> T) = map { if (match(it)) edit(it) else it }

fun Campaign.updateChapter(id: String, edit: (Chapter) -> Chapter) =
    updateOwningScenario(id) { s -> s.copy(chapters = s.chapters.replace({ it.id == id }, edit)) }

fun Campaign.updatePlacement(id: String, edit: (TilePlacement) -> TilePlacement) =
    updateOwningScenario(id) { s -> s.copy(chapters = s.chapters.map { ch -> ch.copy(tiles = ch.tiles.replace({ it.id == id }, edit)) }) }

fun Campaign.updateToken(id: String, edit: (Token) -> Token) =
    updateOwningScenario(id) { s -> s.copy(chapters = s.chapters.map { ch -> ch.copy(tokens = ch.tokens.replace({ it.id == id }, edit)) }) }

fun Campaign.updateEvent(id: String, edit: (Event) -> Event) =
    updateOwningScenario(id) { s -> s.copy(events = s.events.replace({ it.id == id }, edit)) }

fun Campaign.updateTrigger(id: String, edit: (Trigger) -> Trigger) =
    updateOwningScenario(id) { s -> s.copy(triggers = s.triggers.replace({ it.id == id }, edit)) }

fun Campaign.updateObjective(id: String, edit: (Objective) -> Objective) =
    updateOwningScenario(id) { s -> s.copy(objectives = s.objectives.replace({ it.id == id }, edit)) }

fun Campaign.updateEnemyGroup(id: String, edit: (EnemyGroup) -> EnemyGroup) =
    updateOwningScenario(id) { s -> s.copy(enemyGroups = s.enemyGroups.replace({ it.id == id }, edit)) }

fun Campaign.updateResolution(id: String, edit: (Resolution) -> Resolution) =
    updateOwningScenario(id) { s -> s.copy(resolutions = s.resolutions.replace({ it.id == id }, edit)) }

/** Moves the element at [from] to index [to]. */
fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices || from == to) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

// ---- rename & delete -----------------------------------------------------------------

/** Renames an object and rewrites every reference to it. */
fun Campaign.rename(oldId: String, newId: String): Campaign {
    if (oldId == newId) return this
    val def = definition(oldId) ?: throw EditException("Objekt '$oldId' nicht gefunden.")
    if (!Ids.isValid(newId)) throw EditException("'$newId' ist keine gültige ID (Kleinbuchstaben, Ziffern, '_', beginnend mit Buchstabe).")
    if (newId in allIds()) throw EditException("Die ID '$newId' ist bereits vergeben.")

    val renamed = mapReferences { r -> if (r.kind == def.kind && r.id == oldId) newId else r.id }
    return when (def.kind) {
        ObjectKind.CAMPAIGN -> renamed.copy(id = newId)
        ObjectKind.SCENARIO -> renamed.copy(scenarios = renamed.scenarios.map { if (it.id == oldId) it.copy(id = newId) else it })
        ObjectKind.CHAPTER -> renamed.updateChapter(oldId) { it.copy(id = newId) }
        ObjectKind.PLACEMENT -> renamed.updatePlacement(oldId) { it.copy(id = newId) }
        ObjectKind.TOKEN -> renamed.updateToken(oldId) { it.copy(id = newId) }
        ObjectKind.EVENT -> renamed.updateEvent(oldId) { it.copy(id = newId) }
        ObjectKind.TRIGGER -> renamed.updateTrigger(oldId) { it.copy(id = newId) }
        ObjectKind.OBJECTIVE -> renamed.updateObjective(oldId) { it.copy(id = newId) }
        ObjectKind.ENEMY_GROUP -> renamed.updateEnemyGroup(oldId) { it.copy(id = newId) }
        ObjectKind.RESOLUTION -> renamed.updateResolution(oldId) { it.copy(id = newId) }
        ObjectKind.PLACE -> renamed.copy(places = renamed.places.map { if (it.id == oldId) it.copy(id = newId) else it })
        ObjectKind.FLAG -> renamed.copy(flags = renamed.flags.map { if (it.id == oldId) it.copy(id = newId) else it })
        ObjectKind.BOARD_PLACEMENT -> renamed.updateBattlefields(oldId) { bf -> bf.copy(boards = bf.boards.map { if (it.id == oldId) it.copy(id = newId) else it }) }
        ObjectKind.TERRAIN_PLACEMENT -> renamed.updateBattlefields(oldId) { bf -> bf.copy(terrain = bf.terrain.map { if (it.id == oldId) it.copy(id = newId) else it }) }
    }
}

/**
 * Removes an object (a chapter with its tiles and tokens, a scenario with everything in it).
 * References to it stay in place and show up as validation errors; check [usages] first.
 */
fun Campaign.delete(id: String): Campaign {
    val def = definition(id) ?: throw EditException("Objekt '$id' nicht gefunden.")
    return when (def.kind) {
        ObjectKind.CAMPAIGN -> throw EditException("Die Kampagne selbst kann nicht gelöscht werden.")
        ObjectKind.SCENARIO -> copy(scenarios = scenarios.filterNot { it.id == id })
        ObjectKind.CHAPTER -> updateOwningScenario(id) { s -> s.copy(chapters = s.chapters.filterNot { it.id == id }) }
        ObjectKind.PLACEMENT -> updateOwningScenario(id) { s -> s.copy(chapters = s.chapters.map { ch -> ch.copy(tiles = ch.tiles.filterNot { it.id == id }) }) }
        ObjectKind.TOKEN -> updateOwningScenario(id) { s -> s.copy(chapters = s.chapters.map { ch -> ch.copy(tokens = ch.tokens.filterNot { it.id == id }) }) }
        ObjectKind.EVENT -> updateOwningScenario(id) { s -> s.copy(events = s.events.filterNot { it.id == id }) }
        ObjectKind.TRIGGER -> updateOwningScenario(id) { s -> s.copy(triggers = s.triggers.filterNot { it.id == id }) }
        ObjectKind.OBJECTIVE -> updateOwningScenario(id) { s -> s.copy(objectives = s.objectives.filterNot { it.id == id }) }
        ObjectKind.ENEMY_GROUP -> updateOwningScenario(id) { s -> s.copy(enemyGroups = s.enemyGroups.filterNot { it.id == id }) }
        ObjectKind.RESOLUTION -> updateOwningScenario(id) { s -> s.copy(resolutions = s.resolutions.filterNot { it.id == id }) }
        ObjectKind.PLACE -> copy(places = places.filterNot { it.id == id })
        ObjectKind.FLAG -> copy(flags = flags.filterNot { it.id == id })
        ObjectKind.BOARD_PLACEMENT -> updateBattlefields(id) { bf -> bf.copy(boards = bf.boards.filterNot { it.id == id }) }
        ObjectKind.TERRAIN_PLACEMENT -> updateBattlefields(id) { bf -> bf.copy(terrain = bf.terrain.filterNot { it.id == id }) }
    }
}

/** Applies [f] to every battlefield of the scenario that owns [id]. */
internal fun Campaign.updateBattlefields(id: String, f: (Battlefield) -> Battlefield): Campaign =
    updateOwningScenario(id) { s -> s.copy(chapters = s.chapters.map { ch -> ch.battlefield?.let { ch.copy(battlefield = f(it)) } ?: ch }) }


// ---- campaign map and flags ----------------------------------------------------------

fun Campaign.addPlace(name: String, x: Double = 0.5, y: Double = 0.5): Created {
    val id = Ids.fromTitle(ObjectKind.PLACE, name, allIds())
    return Created(copy(places = places + io.github.aljadvb.worldbuilder.model.Place(id, name, x.coerceIn(0.0, 1.0), y.coerceIn(0.0, 1.0))), id)
}

fun Campaign.updatePlace(id: String, edit: (io.github.aljadvb.worldbuilder.model.Place) -> io.github.aljadvb.worldbuilder.model.Place) =
    copy(places = places.map { if (it.id == id) edit(it).let { p -> p.copy(x = p.x.coerceIn(0.0, 1.0), y = p.y.coerceIn(0.0, 1.0)) } else it })

fun Campaign.addFlag(label: String): Created {
    val id = Ids.fromTitle(ObjectKind.FLAG, label, allIds())
    return Created(copy(flags = flags + io.github.aljadvb.worldbuilder.model.CampaignFlag(id, label)), id)
}

fun Campaign.updateFlag(id: String, edit: (io.github.aljadvb.worldbuilder.model.CampaignFlag) -> io.github.aljadvb.worldbuilder.model.CampaignFlag) =
    copy(flags = flags.map { if (it.id == id) edit(it) else it })
