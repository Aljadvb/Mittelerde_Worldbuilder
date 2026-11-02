package io.github.aljadvb.worldbuilder.editing

import io.github.aljadvb.worldbuilder.model.Campaign
import io.github.aljadvb.worldbuilder.model.EventStep
import io.github.aljadvb.worldbuilder.validation.ObjectKind

/** Result of [importScenarios]: the merged campaign and the final IDs of the imported scenarios. */
data class ScenarioImport(val campaign: Campaign, val scenarioIds: List<String>)

/**
 * Copies scenarios [ids] of [source] (e.g. another saved campaign file) into this campaign.
 * - IDs that already exist here are renamed in the copy; all references follow.
 * - Places and campaign flags the scenarios use come along; a place or flag with the same ID
 *   already here is shared (not duplicated).
 * - Transitions between the imported scenarios are kept.
 */
fun Campaign.importScenarios(source: Campaign, ids: List<String>): ScenarioImport {
    val chosen = source.scenarios.filter { it.id in ids }
    if (chosen.isEmpty()) throw EditException("Keine Szenarien ausgewählt.")
    val usedFlags = chosen.flatMap { s ->
        s.resolutions.flatMap { it.setFlags } + s.events.flatMap { e ->
            e.steps.mapNotNull { st -> (st as? EventStep.SetFlag)?.flag ?: (st as? EventStep.CheckFlag)?.flag }
        }
    }.toSet() + source.transitions.filter { it.from in ids && it.to in ids }.mapNotNull { it.requiresFlag }
    val usedPlaces = chosen.mapNotNull { it.place }.toSet()
    var temp = source.copy(
        scenarios = chosen,
        transitions = source.transitions.filter { it.from in ids && it.to in ids },
        places = source.places.filter { it.id in usedPlaces },
        flags = source.flags.filter { it.id in usedFlags },
        startScenario = chosen.first().id,
    )
    // Shared kinds: same ID means the same place / flag.
    val shared = setOf(ObjectKind.CAMPAIGN, ObjectKind.PLACE, ObjectKind.FLAG)
    val taken = allIds().toMutableSet()
    for (def in temp.definitions()) {
        if (def.kind in shared || def.id !in taken) { taken += def.id; continue }
        val fresh = Ids.unique(def.id, taken + temp.allIds())
        temp = temp.rename(def.id, fresh)
        taken += fresh
    }
    val newPlaces = temp.places.filter { p -> places.none { it.id == p.id } }
    val newFlags = temp.flags.filter { f -> flags.none { it.id == f.id } }
    val merged = copy(
        scenarios = scenarios + temp.scenarios,
        transitions = transitions + temp.transitions,
        places = places + newPlaces,
        flags = flags + newFlags,
    )
    return ScenarioImport(merged, temp.scenarios.map { it.id })
}
