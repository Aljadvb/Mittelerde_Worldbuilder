package io.github.aljadvb.worldbuilder.validation

import io.github.aljadvb.worldbuilder.model.EventStep
import io.github.aljadvb.worldbuilder.model.Outcome
import io.github.aljadvb.worldbuilder.model.Scenario
import io.github.aljadvb.worldbuilder.model.Trigger
import io.github.aljadvb.worldbuilder.model.TriggerCondition

/**
 * Optimistic reachability analysis of a scenario: computes the fixpoint of everything
 * that can happen on *some* path, ignoring test outcomes and choice exclusivity.
 * Unknown references are tolerated (they are reported by the structural checks).
 */
internal object Reachability {

    class Result(
        val chapters: Set<String>,
        val activeTokens: Set<String>,
        val events: Set<String>,
        val firableTriggers: Set<String>,
        val activeObjectives: Set<String>,
        val completedObjectives: Set<String>,
        val spawnedGroups: Set<String>,
        val resolutions: Set<String>,
    )

    fun analyze(s: Scenario): Result {
        val chapters = mutableSetOf(s.startChapter)
        val tokens = mutableSetOf<String>()
        val events = mutableSetOf<String>()
        val triggers = mutableSetOf<String>()
        val manuallyFired = mutableSetOf<String>()
        val activeObjectives = mutableSetOf(s.startObjective)
        val completedObjectives = mutableSetOf<String>()
        val groups = mutableSetOf<String>()
        // A defeated hero ends the scenario with its first defeat resolution.
        val resolutions = s.resolutions.firstOrNull { it.outcome == Outcome.DEFEAT }?.let { mutableSetOf(it.id) } ?: mutableSetOf()
        val perRound = s.threat.perRound
        var threatCanRise = perRound.base > 0 || perRound.perHero > 0

        fun firable(t: Trigger): Boolean = when (val on = t.on) {
            is TriggerCondition.TokenInteracted -> on.token in tokens
            is TriggerCondition.ThreatReached -> threatCanRise && on.value <= s.threat.max
            TriggerCondition.ThreatMax -> threatCanRise
            is TriggerCondition.EnemyGroupDefeated -> on.group in groups
            is TriggerCondition.ObjectiveCompleted -> on.objective in completedObjectives
            is TriggerCondition.RoundEnded -> true
            is TriggerCondition.TileExplored -> chapters.any { ch -> s.chapter(ch)?.tiles.orEmpty().any { it.id == on.placement && it.unexplored } }
            TriggerCondition.Manual -> t.id in manuallyFired
        }

        fun size() = chapters.size + tokens.size + events.size + triggers.size + manuallyFired.size +
            activeObjectives.size + completedObjectives.size + groups.size + resolutions.size + (if (threatCanRise) 1 else 0)

        do {
            val before = size()
            for (id in chapters.toList()) {
                val ch = s.chapter(id) ?: continue
                ch.onUnlock?.let(events::add)
                ch.tiles.filter { it.unexplored }.forEach { p -> p.onExplore?.let(events::add) }
                ch.tokens.filter { it.initiallyActive }.forEach { tokens += it.id }
            }
            for (id in tokens.toList()) s.token(id)?.onInteract?.let(events::add)
            events += s.shadowEvents
            for (id in events.toList()) {
                for (step in s.event(id)?.steps.orEmpty()) when (step) {
                    is EventStep.Text, is EventStep.RemoveToken, is EventStep.GainInspiration, is EventStep.SufferHarm, is EventStep.GainItem, is EventStep.Recover, is EventStep.AffectEnemy, is EventStep.GainCondition, is EventStep.GainCorruption, is EventStep.GainTitle, is EventStep.GainWeakness, is EventStep.LoseCorruption, is EventStep.PlaceMarker, is EventStep.RemoveMarker,
                    is EventStep.SetFlag, is EventStep.GainLore, is EventStep.SetDarkness -> Unit
                    is EventStep.CheckFlag -> { events += step.ifSet; step.ifNotSet?.let(events::add) }
                    is EventStep.Choice -> step.options.forEach { events += it.event }
                    is EventStep.CheckCorruption -> { events += step.ifCorrupted; step.ifNotCorrupted?.let(events::add) }
                    is EventStep.Inscription -> { step.onSuccess?.let(events::add); step.onFailure?.let(events::add) }
                    is EventStep.Test -> { events += step.onSuccess; step.onFailure?.let(events::add); step.onAttempt?.let(events::add) }
                    is EventStep.SpawnEnemies -> groups += step.group
                    is EventStep.ChangeThreat -> if (step.amount > 0) threatCanRise = true
                    is EventStep.AddToken -> tokens += step.token
                    is EventStep.UnlockChapter -> chapters += step.chapter
                    is EventStep.SetObjective -> activeObjectives += step.objective
                    is EventStep.FireTrigger -> if (s.trigger(step.trigger)?.on == TriggerCondition.Manual) manuallyFired += step.trigger
                    is EventStep.EndScenario -> resolutions += step.resolution
                }
            }
            for (o in s.objectives) {
                if (o.id in activeObjectives && o.completedBy in triggers) completedObjectives += o.id
            }
            for (t in s.triggers) {
                if (firable(t)) { triggers += t.id; events += t.events }
            }
        } while (size() != before)

        return Result(chapters, tokens, events, triggers, activeObjectives, completedObjectives, groups, resolutions)
    }

    fun check(s: Scenario, sp: String, c: IssueCollector) {
        val r = analyze(s)

        if (s.triggers.none { it.on == TriggerCondition.ThreatMax }) {
            c.error(IssueCode.NO_THREAT_MAX_TRIGGER, "$sp.triggers", s.id,
                "Es gibt keinen Trigger vom Typ 'threatMax' – was passiert, wenn die Bedrohung das Maximum erreicht?")
        }

        s.objectives.forEachIndexed { i, o ->
            val p = "$sp.objectives[$i]"
            when {
                o.id !in r.activeObjectives -> c.warn(IssueCode.UNREACHABLE, p, o.id, "Ziel '${o.id}' wird nie aktiviert.")
                o.id !in r.completedObjectives -> c.error(IssueCode.OBJECTIVE_NOT_COMPLETABLE, p, o.id,
                    "Ziel '${o.id}' kann nie erfüllt werden: Trigger '${o.completedBy}' ist auf keinem Weg auslösbar.")
            }
        }

        if (s.resolutions.none { it.outcome == Outcome.DEFEAT }) {
            c.warn(IssueCode.NO_DEFEAT_RESOLUTION, "$sp.resolutions", s.id,
                "Es gibt keine Niederlage-Auflösung. Wird ein Held besiegt, kann das Abenteuer dann nicht verloren gehen.")
        }
        val reachedResolutions = s.resolutions.filter { it.id in r.resolutions }
        if (reachedResolutions.isEmpty()) {
            c.error(IssueCode.NO_RESOLUTION_REACHABLE, "$sp.resolutions", s.id, "Keine Auflösung ist auf irgendeinem Weg erreichbar.")
        } else if (reachedResolutions.all { it.outcome == Outcome.DEFEAT }) {
            c.warn(IssueCode.NO_VICTORY_REACHABLE, "$sp.resolutions", s.id, "Es sind nur Niederlage-Auflösungen erreichbar.")
        }

        fun unreachable(path: String, id: String, text: String) = c.warn(IssueCode.UNREACHABLE, path, id, text)
        s.chapters.forEachIndexed { ci, ch ->
            val cp = "$sp.chapters[$ci]"
            if (ch.id !in r.chapters) unreachable(cp, ch.id, "Kapitel '${ch.id}' wird nie freigeschaltet.")
            ch.tokens.forEachIndexed { ti, t ->
                if (t.id !in r.activeTokens) unreachable("$cp.tokens[$ti]", t.id, "Token '${t.id}' wird nie aktiv.")
            }
        }
        s.events.forEachIndexed { i, e ->
            if (e.id !in r.events) unreachable("$sp.events[$i]", e.id, "Event '${e.id}' wird nie ausgelöst.")
        }
        s.triggers.forEachIndexed { i, t ->
            if (t.id !in r.firableTriggers) unreachable("$sp.triggers[$i]", t.id, "Trigger '${t.id}' kann nie auslösen.")
        }
        s.enemyGroups.forEachIndexed { i, g ->
            if (g.id !in r.spawnedGroups) unreachable("$sp.enemyGroups[$i]", g.id, "Gegnergruppe '${g.id}' erscheint nie.")
        }
        s.resolutions.forEachIndexed { i, res ->
            if (res.id !in r.resolutions) unreachable("$sp.resolutions[$i]", res.id, "Auflösung '${res.id}' ist nie erreichbar.")
        }
    }
}
