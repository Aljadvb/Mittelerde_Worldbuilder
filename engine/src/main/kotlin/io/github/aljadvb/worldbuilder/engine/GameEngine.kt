package io.github.aljadvb.worldbuilder.engine

import io.github.aljadvb.worldbuilder.model.EventStep
import io.github.aljadvb.worldbuilder.model.Scenario
import io.github.aljadvb.worldbuilder.model.Trigger
import io.github.aljadvb.worldbuilder.model.placementId
import io.github.aljadvb.worldbuilder.model.TriggerCondition

/**
 * Rules of play for one scenario. Pure and deterministic: `apply(state, command)` returns
 * a new state. Assumes a validated scenario; dangling references are skipped silently.
 *
 * Execution order:
 * - steps of the current frame run until a prompt is produced;
 * - branches (choice, test) replace the current frame;
 * - a chapter's `onUnlock` runs right after the current frame;
 * - events of fired triggers run after everything already scheduled.
 */
class GameEngine(val scenario: Scenario, private val campaignFlags: Set<String> = emptySet()) {

    fun start(heroCount: Int): GameState {
        require(heroCount in 1..5) { "heroCount must be 1–5" }
        val initial = GameState(
            scenarioId = scenario.id,
            heroCount = heroCount,
            flags = campaignFlags,
            agenda = listOf(
                Frame.InlineFrame(
                    listOf(EventStep.UnlockChapter(scenario.startChapter), EventStep.SetObjective(scenario.startObjective)),
                ),
            ),
            prompts = listOf(Prompt.Intro),
        )
        return initial
    }

    fun apply(state: GameState, command: Command): GameState {
        require(state.scenarioId == scenario.id) { "state belongs to another scenario" }
        if (state.isOver) throw EngineException("Das Szenario ist bereits beendet.")
        val run = Run(state)
        when (command) {
            Command.Acknowledge -> {
                if (state.currentPrompt !is Prompt.Notice) throw EngineException("Keine Meldung zum Bestätigen.")
                run.popPrompt()
            }
            is Command.Choose -> {
                val prompt = state.currentPrompt as? Prompt.Choice ?: throw EngineException("Keine Auswahl offen.")
                val option = prompt.step.options.getOrNull(command.index) ?: throw EngineException("Ungültige Option ${command.index}.")
                run.popPrompt(answer = option.label)
                run.replaceCurrent(option.event)
            }
            is Command.ReportTest -> {
                val prompt = state.currentPrompt as? Prompt.Test ?: throw EngineException("Keine Probe offen.")
                if (command.successes < 0) throw EngineException("Erfolge dürfen nicht negativ sein.")
                val success = command.successes >= prompt.step.successes
                run.popPrompt(answer = command.successes.toString())
                run.replaceCurrent(if (success) prompt.step.onSuccess else prompt.step.onFailure)
            }
            is Command.InteractToken -> {
                requireIdle(state)
                if (command.tokenId !in state.activeTokens) throw EngineException("Token '${command.tokenId}' liegt nicht aus.")
                run.interact(command.tokenId)
            }
            is Command.ExploreTile -> {
                requireIdle(state)
                if (command.placementId !in state.unexploredPlacements(scenario)) throw EngineException("Kachel '${command.placementId}' ist nicht unerforscht.")
                run.explore(command.placementId)
            }
            is Command.DefeatEnemyGroup -> {
                requireIdle(state)
                if (command.groupId !in state.activeEnemyGroups) throw EngineException("Gegnergruppe '${command.groupId}' ist nicht im Spiel.")
                run.defeat(command.groupId)
            }
            Command.EndRound -> {
                requireIdle(state)
                run.endRound()
            }
        }
        run.process()
        return run.toState()
    }

    private fun requireIdle(state: GameState) {
        if (!state.isIdle) throw EngineException("Erst die offene Meldung abschließen.")
    }

    /** Mutable working copy for applying one command. */
    private inner class Run(s: GameState) {
        private val heroCount = s.heroCount
        var round = s.round
        var threat = s.threat
        val unlockedChapters = s.unlockedChapters.toMutableList()
        val activeTokens = s.activeTokens.toMutableSet()
        val interactedTokens = s.interactedTokens.toMutableSet()
        val activeGroups = s.activeEnemyGroups.toMutableSet()
        val defeatedGroups = s.defeatedEnemyGroups.toMutableSet()
        val activeObjectives = s.activeObjectives.toMutableList()
        val completedObjectives = s.completedObjectives.toMutableSet()
        val firedTriggers = s.firedTriggers.toMutableSet()
        val explored = s.exploredPlacements.toMutableSet()
        val flags = s.flags.toMutableSet()
        var loreGained = s.loreGained
        val agenda = s.agenda.toMutableList()
        val prompts = s.prompts.toMutableList()
        val history = s.history.toMutableList()
        var resolution = s.resolution

        fun toState() = GameState(
            scenarioId = scenario.id, heroCount = heroCount, round = round, threat = threat,
            unlockedChapters = unlockedChapters.toList(), activeTokens = activeTokens.toSet(),
            interactedTokens = interactedTokens.toSet(), activeEnemyGroups = activeGroups.toSet(),
            defeatedEnemyGroups = defeatedGroups.toSet(), activeObjectives = activeObjectives.toList(),
            completedObjectives = completedObjectives.toSet(), firedTriggers = firedTriggers.toSet(), exploredPlacements = explored.toSet(), flags = flags.toSet(), loreGained = loreGained,
            agenda = agenda.toList(), prompts = prompts.toList(), history = history.toList(), resolution = resolution,
        )

        fun popPrompt(answer: String? = null) {
            history += HistoryEntry(round, prompts.removeAt(0), answer)
        }

        /** Branch: the current frame ends and [eventId] (if any) runs in its place. */
        fun replaceCurrent(eventId: String?) {
            if (agenda.isNotEmpty()) agenda.removeAt(0)
            if (eventId != null) agenda.add(0, Frame.EventFrame(eventId))
        }

        // ---- player actions ----------------------------------------------------------

        fun interact(tokenId: String) {
            val token = scenario.token(tokenId) ?: return
            interactedTokens += tokenId
            if (token.removeAfterInteract) activeTokens -= tokenId
            if (token.inspectText != null) prompts += Prompt.Inspect(tokenId)
            token.onInteract?.let { agenda += Frame.EventFrame(it) }
            fireMatching { it is TriggerCondition.TokenInteracted && it.token == tokenId }
        }

        fun explore(placementId: String) {
            val placement = scenario.placement(placementId) ?: return
            explored += placementId
            val revealed = scenario.chapters.filter { it.id in unlockedChapters }.flatMap { it.tokens }
                .filter { it.initiallyActive && it.id !in interactedTokens && it.location.placementId == placementId }
                .map { it.id }.filter { activeTokens.add(it) }
            prompts += Prompt.Explored(placementId, revealed)
            placement.onExplore?.let { agenda += Frame.EventFrame(it) }
            fireMatching { it is TriggerCondition.TileExplored && it.placement == placementId }
        }

        fun defeat(groupId: String) {
            activeGroups -= groupId
            defeatedGroups += groupId
            fireMatching { it is TriggerCondition.EnemyGroupDefeated && it.group == groupId }
        }

        fun endRound() {
            val added = scenario.threat.perRound.amountFor(heroCount)
            val finished = round
            round += 1
            val before = threat
            changeThreat(added)
            prompts += Prompt.RoundEnded(finished, threat - before, threat)
            fireMatching { it is TriggerCondition.RoundEnded && it.round == finished }
        }

        // ---- processing --------------------------------------------------------------

        fun process() {
            var budget = MAX_STEPS_PER_COMMAND
            while (prompts.isEmpty() && resolution == null && agenda.isNotEmpty()) {
                if (--budget < 0) throw EngineException("Endlosschleife: zu viele Schritte ohne Spielereingabe.")
                val frame = agenda[0]
                val steps = stepsOf(frame)
                if (steps == null || frame.stepIndex >= steps.size) {
                    agenda.removeAt(0)
                    continue
                }
                agenda[0] = advanced(frame)
                execute(steps[frame.stepIndex], (frame as? Frame.EventFrame)?.eventId)
            }
        }

        private fun stepsOf(frame: Frame): List<EventStep>? = when (frame) {
            is Frame.EventFrame -> scenario.event(frame.eventId)?.steps
            is Frame.InlineFrame -> frame.steps
        }

        private fun advanced(frame: Frame): Frame = when (frame) {
            is Frame.EventFrame -> frame.copy(stepIndex = frame.stepIndex + 1)
            is Frame.InlineFrame -> frame.copy(stepIndex = frame.stepIndex + 1)
        }

        private fun execute(step: EventStep, eventId: String?) {
            when (step) {
                is EventStep.Text -> prompts += Prompt.Text(step.text)
                is EventStep.Choice -> prompts += Prompt.Choice(eventId, step)
                is EventStep.Test -> prompts += Prompt.Test(eventId, step)
                is EventStep.SpawnEnemies -> {
                    val group = scenario.enemyGroup(step.group) ?: return
                    if (activeGroups.add(group.id)) {
                        defeatedGroups -= group.id
                        prompts += Prompt.SpawnEnemies(group.id, group.count[heroCount] ?: 0)
                    }
                }
                is EventStep.ChangeThreat -> {
                    val before = threat
                    changeThreat(step.amount)
                    if (threat != before) prompts += Prompt.ThreatChanged(threat - before, threat)
                }
                is EventStep.AddToken -> {
                    if (scenario.token(step.token) != null && activeTokens.add(step.token)) prompts += Prompt.PlaceToken(step.token)
                }
                is EventStep.RemoveToken -> {
                    if (activeTokens.remove(step.token)) prompts += Prompt.RemoveToken(step.token)
                }
                is EventStep.UnlockChapter -> unlockChapter(step.chapter)
                is EventStep.SetObjective -> {
                    val id = step.objective
                    if (scenario.objective(id) != null && id !in activeObjectives && id !in completedObjectives) {
                        activeObjectives += id
                        prompts += Prompt.ObjectiveSet(id)
                    }
                }
                is EventStep.FireTrigger -> {
                    val trigger = scenario.trigger(step.trigger)
                    if (trigger?.on == TriggerCondition.Manual) fire(trigger)
                }
                is EventStep.SetFlag -> flags += step.flag
                is EventStep.CheckFlag -> replaceCurrent(if (step.flag in flags) step.ifSet else step.ifNotSet)
                is EventStep.GainLore -> if (step.amount > 0) {
                    loreGained += step.amount
                    prompts += Prompt.Lore(step.amount, step.text)
                }
                is EventStep.GainInspiration -> prompts += Prompt.Inspiration(step.amount, step.who, step.text)
                is EventStep.SufferHarm -> if (step.damage > 0 || step.fear > 0) prompts += Prompt.Harm(step.damage, step.fear, step.who, step.hidden, step.text)
                is EventStep.EndScenario -> {
                    resolution = step.resolution
                    agenda.clear()
                    prompts.clear()
                }
            }
        }

        private fun unlockChapter(chapterId: String) {
            val chapter = scenario.chapter(chapterId) ?: return
            if (chapterId in unlockedChapters) return
            if (chapter.isBattle && chapter.clearBattlefield) {
                // Earlier boards and pieces are cleared away, and with them the tokens lying on them.
                val cleared = unlockedChapters.mapNotNull { scenario.chapter(it) }.filter { it.isBattle }
                    .flatMap { it.tokens }.filter { it.location is io.github.aljadvb.worldbuilder.model.SquareLocation }.map { it.id }
                activeTokens -= cleared.toSet()
            }
            unlockedChapters += chapterId
            // Tokens on unexplored tiles appear only when the tile is explored.
            val hidden = chapter.tiles.filter { it.unexplored && it.id !in explored }.map { it.id }.toSet()
            activeTokens += chapter.tokens.filter { it.initiallyActive && it.location.placementId !in hidden }.map { it.id }
            prompts += Prompt.Setup(chapterId)
            chapter.onUnlock?.let { agenda.add(minOf(1, agenda.size), Frame.EventFrame(it)) }
        }

        private fun changeThreat(delta: Int) {
            val max = scenario.threat.max
            val before = threat
            threat = (threat + delta).coerceIn(0, max)
            if (threat <= before) return
            fireMatching {
                (it is TriggerCondition.ThreatReached && it.value in (before + 1)..threat) ||
                    (it == TriggerCondition.ThreatMax && threat >= max)
            }
        }

        // ---- triggers ----------------------------------------------------------------

        private fun fireMatching(predicate: (TriggerCondition) -> Boolean) {
            scenario.triggers.filter { predicate(it.on) }.forEach(::fire)
        }

        private fun fire(trigger: Trigger) {
            if (trigger.id in firedTriggers && !trigger.repeatable) return
            firedTriggers += trigger.id
            trigger.events.forEach { agenda += Frame.EventFrame(it) }
            scenario.objectives
                .filter { it.completedBy == trigger.id && it.id in activeObjectives }
                .forEach { objective ->
                    activeObjectives -= objective.id
                    completedObjectives += objective.id
                    prompts += Prompt.ObjectiveCompleted(objective.id)
                    fireMatching { it is TriggerCondition.ObjectiveCompleted && it.objective == objective.id }
                }
        }
    }

    companion object {
        const val MAX_STEPS_PER_COMMAND = 10_000
    }
}
