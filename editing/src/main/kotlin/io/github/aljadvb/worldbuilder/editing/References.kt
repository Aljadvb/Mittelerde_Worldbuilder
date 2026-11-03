package io.github.aljadvb.worldbuilder.editing

import io.github.aljadvb.worldbuilder.model.Campaign
import io.github.aljadvb.worldbuilder.model.Event
import io.github.aljadvb.worldbuilder.model.EventStep
import io.github.aljadvb.worldbuilder.model.Scenario
import io.github.aljadvb.worldbuilder.model.Location
import io.github.aljadvb.worldbuilder.model.TileLocation
import io.github.aljadvb.worldbuilder.model.Trigger
import io.github.aljadvb.worldbuilder.model.TriggerCondition
import io.github.aljadvb.worldbuilder.validation.ObjectKind

/** An object defined in the campaign. [path] uses the validator's path syntax. */
data class Definition(val kind: ObjectKind, val id: String, val path: String, val scenarioId: String?)

/**
 * A place where an ID is referenced.
 * @param ownerId the object containing the reference
 * @param path location in validator path syntax, e.g. `scenarios[0].events[2].steps[1].onSuccess`
 */
data class Reference(val kind: ObjectKind, val id: String, val path: String, val ownerId: String)

/** All objects defined in the campaign, in document order. */
fun Campaign.definitions(): List<Definition> = buildList {
    add(Definition(ObjectKind.CAMPAIGN, id, "", null))
    places.forEachIndexed { i, p -> add(Definition(ObjectKind.PLACE, p.id, "places[$i]", null)) }
    flags.forEachIndexed { i, f -> add(Definition(ObjectKind.FLAG, f.id, "flags[$i]", null)) }
    scenarios.forEachIndexed { si, s ->
        val sp = "scenarios[$si]"
        add(Definition(ObjectKind.SCENARIO, s.id, sp, s.id))
        s.chapters.forEachIndexed { ci, ch ->
            val cp = "$sp.chapters[$ci]"
            add(Definition(ObjectKind.CHAPTER, ch.id, cp, s.id))
            ch.tiles.forEachIndexed { i, p -> add(Definition(ObjectKind.PLACEMENT, p.id, "$cp.tiles[$i]", s.id)) }
            ch.tokens.forEachIndexed { i, t -> add(Definition(ObjectKind.TOKEN, t.id, "$cp.tokens[$i]", s.id)) }
            ch.battlefield?.boards?.forEachIndexed { i, b -> add(Definition(ObjectKind.BOARD_PLACEMENT, b.id, "$cp.battlefield.boards[$i]", s.id)) }
            ch.battlefield?.terrain?.forEachIndexed { i, g -> add(Definition(ObjectKind.TERRAIN_PLACEMENT, g.id, "$cp.battlefield.terrain[$i]", s.id)) }
        }
        s.enemyGroups.forEachIndexed { i, g -> add(Definition(ObjectKind.ENEMY_GROUP, g.id, "$sp.enemyGroups[$i]", s.id)) }
        s.events.forEachIndexed { i, e -> add(Definition(ObjectKind.EVENT, e.id, "$sp.events[$i]", s.id)) }
        s.triggers.forEachIndexed { i, t -> add(Definition(ObjectKind.TRIGGER, t.id, "$sp.triggers[$i]", s.id)) }
        s.objectives.forEachIndexed { i, o -> add(Definition(ObjectKind.OBJECTIVE, o.id, "$sp.objectives[$i]", s.id)) }
        s.resolutions.forEachIndexed { i, r -> add(Definition(ObjectKind.RESOLUTION, r.id, "$sp.resolutions[$i]", s.id)) }
    }
}

fun Campaign.allIds(): Set<String> = definitions().mapTo(mutableSetOf()) { it.id }

fun Campaign.definition(id: String): Definition? = definitions().firstOrNull { it.id == id }

/** All references in the campaign, in document order. */
fun Campaign.references(): List<Reference> {
    val result = mutableListOf<Reference>()
    mapReferences { result += it; it.id }
    return result
}

/** References pointing at [id] ("used by"). */
fun Campaign.usages(id: String): List<Reference> = references().filter { it.id == id }

/**
 * Rebuilds the campaign with every reference passed through [map].
 * This is the single place that knows which fields hold references.
 */
fun Campaign.mapReferences(map: (Reference) -> String): Campaign {
    fun ref(kind: ObjectKind, id: String, path: String, owner: String) = map(Reference(kind, id, path, owner))

    return copy(
        startScenario = ref(ObjectKind.SCENARIO, startScenario, "startScenario", id),
        transitions = transitions.mapIndexed { i, t ->
            val p = "transitions[$i]"
            t.copy(
                from = ref(ObjectKind.SCENARIO, t.from, "$p.from", id),
                resolution = ref(ObjectKind.RESOLUTION, t.resolution, "$p.resolution", id),
                to = ref(ObjectKind.SCENARIO, t.to, "$p.to", id),
                requiresFlag = t.requiresFlag?.let { ref(ObjectKind.FLAG, it, "$p.requiresFlag", id) },
            )
        },
        scenarios = scenarios.mapIndexed { si, s -> s.mapReferences("scenarios[$si]", ::ref) },
    )
}

private typealias RefFn = (ObjectKind, String, String, String) -> String

private fun Scenario.mapReferences(sp: String, ref: RefFn): Scenario {
    fun loc(l: Location, p: String, owner: String): Location =
        if (l is TileLocation) l.copy(placement = ref(ObjectKind.PLACEMENT, l.placement, "$p.placement", owner)) else l
    return copy(
        startChapter = ref(ObjectKind.CHAPTER, startChapter, "$sp.startChapter", id),
        startObjective = ref(ObjectKind.OBJECTIVE, startObjective, "$sp.startObjective", id),
        place = place?.let { ref(ObjectKind.PLACE, it, "$sp.place", id) },
        shadowEvents = shadowEvents.mapIndexed { i, e -> ref(ObjectKind.EVENT, e, "$sp.shadowEvents[$i]", id) },
        resolutions = resolutions.mapIndexed { i, r ->
            r.copy(setFlags = r.setFlags.mapIndexed { fi, f -> ref(ObjectKind.FLAG, f, "$sp.resolutions[$i].setFlags[$fi]", r.id) })
        },
        chapters = chapters.mapIndexed { ci, ch ->
            val cp = "$sp.chapters[$ci]"
            ch.copy(
                onUnlock = ch.onUnlock?.let { ref(ObjectKind.EVENT, it, "$cp.onUnlock", ch.id) },
                tiles = ch.tiles.mapIndexed { pi, pl -> pl.copy(onExplore = pl.onExplore?.let { ref(ObjectKind.EVENT, it, "$cp.tiles[$pi].onExplore", pl.id) }) },
                tokens = ch.tokens.mapIndexed { ti, t ->
                    val tp = "$cp.tokens[$ti]"
                    t.copy(
                        location = loc(t.location, "$tp.location", t.id),
                        onInteract = t.onInteract?.let { ref(ObjectKind.EVENT, it, "$tp.onInteract", t.id) },
                    )
                },
            )
        },
        enemyGroups = enemyGroups.mapIndexed { i, g -> g.copy(location = loc(g.location, "$sp.enemyGroups[$i].location", g.id)) },
        heroStart = heroStart?.let { loc(it, "$sp.heroStart", id) },
        events = events.mapIndexed { i, e -> e.mapReferences("$sp.events[$i]", ref) },
        triggers = triggers.mapIndexed { i, t -> t.mapReferences("$sp.triggers[$i]", ref) },
        objectives = objectives.mapIndexed { i, o ->
            o.copy(completedBy = ref(ObjectKind.TRIGGER, o.completedBy, "$sp.objectives[$i].completedBy", o.id))
        },
    )
}

private fun Event.mapReferences(ep: String, ref: RefFn): Event = copy(
    steps = steps.mapIndexed { i, step ->
        val p = "$ep.steps[$i]"
        when (step) {
            is EventStep.Text, is EventStep.ChangeThreat, is EventStep.GainInspiration, is EventStep.SufferHarm, is EventStep.GainItem, is EventStep.Recover, is EventStep.GainLore, is EventStep.SetDarkness -> step
            is EventStep.SetFlag -> step.copy(flag = ref(ObjectKind.FLAG, step.flag, "$p.flag", id))
            is EventStep.CheckFlag -> step.copy(
                flag = ref(ObjectKind.FLAG, step.flag, "$p.flag", id),
                ifSet = ref(ObjectKind.EVENT, step.ifSet, "$p.ifSet", id),
                ifNotSet = step.ifNotSet?.let { ref(ObjectKind.EVENT, it, "$p.ifNotSet", id) },
            )
            is EventStep.Choice -> step.copy(
                options = step.options.mapIndexed { oi, o -> o.copy(event = ref(ObjectKind.EVENT, o.event, "$p.options[$oi].event", id)) },
            )
            is EventStep.Test -> step.copy(
                onSuccess = ref(ObjectKind.EVENT, step.onSuccess, "$p.onSuccess", id),
                onFailure = step.onFailure?.let { ref(ObjectKind.EVENT, it, "$p.onFailure", id) },
                onAttempt = step.onAttempt?.let { ref(ObjectKind.EVENT, it, "$p.onAttempt", id) },
            )
            is EventStep.SpawnEnemies -> step.copy(group = ref(ObjectKind.ENEMY_GROUP, step.group, "$p.group", id))
            is EventStep.AffectEnemy -> step.copy(group = ref(ObjectKind.ENEMY_GROUP, step.group, "$p.group", id))
            is EventStep.AddToken -> step.copy(token = ref(ObjectKind.TOKEN, step.token, "$p.token", id))
            is EventStep.RemoveToken -> step.copy(token = ref(ObjectKind.TOKEN, step.token, "$p.token", id))
            is EventStep.UnlockChapter -> step.copy(chapter = ref(ObjectKind.CHAPTER, step.chapter, "$p.chapter", id))
            is EventStep.SetObjective -> step.copy(objective = ref(ObjectKind.OBJECTIVE, step.objective, "$p.objective", id))
            is EventStep.FireTrigger -> step.copy(trigger = ref(ObjectKind.TRIGGER, step.trigger, "$p.trigger", id))
            is EventStep.EndScenario -> step.copy(resolution = ref(ObjectKind.RESOLUTION, step.resolution, "$p.resolution", id))
        }
    },
)

private fun Trigger.mapReferences(tp: String, ref: RefFn): Trigger = copy(
    on = when (val on = on) {
        is TriggerCondition.TokenInteracted -> on.copy(token = ref(ObjectKind.TOKEN, on.token, "$tp.on.token", id))
        is TriggerCondition.EnemyGroupDefeated -> on.copy(group = ref(ObjectKind.ENEMY_GROUP, on.group, "$tp.on.group", id))
        is TriggerCondition.ObjectiveCompleted -> on.copy(objective = ref(ObjectKind.OBJECTIVE, on.objective, "$tp.on.objective", id))
        is TriggerCondition.TileExplored -> on.copy(placement = ref(ObjectKind.PLACEMENT, on.placement, "$tp.on.placement", id))
        is TriggerCondition.ThreatReached, TriggerCondition.ThreatMax, is TriggerCondition.RoundEnded, TriggerCondition.Manual -> on
    },
    events = events.mapIndexed { i, e -> ref(ObjectKind.EVENT, e, "$tp.events[$i]", id) },
)
