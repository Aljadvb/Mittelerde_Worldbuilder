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
class GameEngine(
    val scenario: Scenario,
    private val campaignFlags: Set<String> = emptySet(),
    /** Enemy statistics from the material catalog (health, armor, attacks). */
    private val enemyTypes: Map<String, io.github.aljadvb.worldbuilder.model.EnemyType> = emptyMap(),
    /** Items of the material catalog. */
    private val items: List<io.github.aljadvb.worldbuilder.model.Item> = emptyList(),
    /** Items the group already owns (campaign inventory). */
    private val ownedItems: List<String> = emptyList(),
) {

    fun start(heroCount: Int): GameState {
        require(heroCount in 1..5) { "heroCount must be 1–5" }
        val initial = GameState(
            scenarioId = scenario.id,
            heroCount = heroCount,
            flags = campaignFlags,
            ownedItems = ownedItems,
            rng = scenario.id.hashCode().toLong() * 2654435761L + 1,
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
                if (command.successes < 0) throw EngineException("Erfolge dürfen nicht negativ sein.")
                when (val prompt = state.currentPrompt) {
                    is Prompt.Test -> {
                        val step = prompt.step
                        run.popPrompt(answer = command.successes.toString())
                        if (step.cumulative) {
                            val key = prompt.key ?: ""
                            val total = (run.testProgress[key] ?: 0) + command.successes
                            if (total >= step.successes) {
                                run.testProgress -= key
                                if (step.hidden) run.prompts.add(0, Prompt.TestResult(true))
                                run.replaceCurrent(step.onSuccess)
                            } else {
                                run.testProgress[key] = total
                                run.prompts.add(0, Prompt.TestProgress(step, total))
                                run.replaceCurrent(step.onAttempt)
                            }
                        } else {
                            val success = command.successes >= step.successes
                            if (success) prompt.reveal?.let { run.prompts.add(0, it) }
                            if (step.hidden) run.prompts.add(0, Prompt.TestResult(success))
                            run.replaceCurrent((if (success) step.onSuccess else step.onFailure)?.ifEmpty { null })
                        }
                    }
                    is Prompt.LastStandTest -> {
                        run.popPrompt(answer = command.successes.toString())
                        run.lastStandResult(prompt, command.successes >= 1)
                    }
                    else -> throw EngineException("Keine Probe offen.")
                }
            }
            Command.NoTarget, Command.MovementEnded -> {
                val prompt = state.currentPrompt as? Prompt.EnemyActivation ?: throw EngineException("Keine Gegner-Aktivierung offen.")
                run.popPrompt(answer = if (command == Command.NoTarget) "noTarget" else "movementEnded")
                run.prompts.add(
                    0,
                    if (command == Command.NoTarget) Prompt.EnemyRegroup(prompt.groupId, prompt.movement)
                    else Prompt.EnemyHalted(prompt.groupId, prompt.damage, prompt.fear, prompt.text, prompt.ranged, prompt.negate, prompt.condition),
                )
            }
            is Command.ProvokeEnemy -> {
                requireIdle(state)
                if (command.groupId !in state.activeEnemyGroups) throw EngineException("Gegnergruppe '${command.groupId}' ist nicht im Spiel.")
                if (command.groupId in state.exhaustedGroups) throw EngineException("Eine erschöpfte Gruppe kann nicht provoziert werden.")
                run.enemyAttack(command.groupId, AttackReason.PROVOKED)
            }
            is Command.CounterAttack -> {
                val prompt = state.currentPrompt as? Prompt.CounterQuestion ?: throw EngineException("Keine Gegenangriffs-Frage offen.")
                run.popPrompt(answer = if (command.canAttack) "yes" else "no")
                if (command.canAttack) {
                    run.enemyAttack(prompt.groupId, AttackReason.COUNTER, first = true)
                    // A group is exhausted after a counterattack (unlike after being provoked).
                    run.exhausted += prompt.groupId
                }
            }
            is Command.LimitReached -> {
                requireIdle(state)
                if (command.hero !in 0 until state.heroCount) throw EngineException("Unbekannter Held ${command.hero}.")
                if (command.hero in state.defeatedHeroes) throw EngineException("Der Held ist bereits besiegt.")
                run.limitReached(command.hero, command.kind)
            }
            is Command.InteractToken -> {
                requireIdle(state)
                if (command.tokenId !in state.activeTokens) throw EngineException("Token '${command.tokenId}' liegt nicht aus.")
                run.interact(command.tokenId, command.showText)
            }
            is Command.AttackEnemy -> {
                requireIdle(state)
                if (command.groupId !in state.activeEnemyGroups) throw EngineException("Gegnergruppe '${command.groupId}' ist nicht im Spiel.")
                if (command.hits < 0) throw EngineException("Treffer dürfen nicht negativ sein.")
                run.attack(command)
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
        val pendingTokens = s.pendingTokens.toMutableSet()
        val flags = s.flags.toMutableSet()
        var loreGained = s.loreGained
        val enemyHealth = s.enemyHealth.toMutableMap()
        val armorLoss = s.armorLoss.toMutableMap()
        val exhausted = s.exhaustedGroups.toMutableSet()
        val stunned = s.stunnedGroups.toMutableSet()
        var darknessFear = s.darknessFear
        var rng = s.rng
        val defeatedHeroes = s.defeatedHeroes.toMutableSet()
        val lastStandUsed = s.lastStandUsed.toMutableSet()
        val ownedItemsAtStart = s.ownedItems
        val itemsFound = s.itemsFound.toMutableList()
        val testProgress = s.testProgress.toMutableMap()
        val enemyBanners = s.enemyBanners.toMutableMap()

        /** Deterministic random number in 0 until [n]. */
        fun random(n: Int): Int {
            rng = rng * 6364136223846793005L + 1442695040888963407L
            return if (n <= 1) 0 else ((rng ushr 33) % n).toInt()
        }
        val agenda = s.agenda.toMutableList()
        val prompts = s.prompts.toMutableList()
        val history = s.history.toMutableList()
        var resolution = s.resolution

        fun toState() = GameState(
            scenarioId = scenario.id, heroCount = heroCount, round = round, threat = threat,
            unlockedChapters = unlockedChapters.toList(), activeTokens = activeTokens.toSet(),
            interactedTokens = interactedTokens.toSet(), activeEnemyGroups = activeGroups.toSet(),
            defeatedEnemyGroups = defeatedGroups.toSet(), activeObjectives = activeObjectives.toList(),
            completedObjectives = completedObjectives.toSet(), firedTriggers = firedTriggers.toSet(), exploredPlacements = explored.toSet(), pendingTokens = pendingTokens.toSet(), flags = flags.toSet(), loreGained = loreGained,
            enemyHealth = enemyHealth.toMap(), armorLoss = armorLoss.toMap(), exhaustedGroups = exhausted.toSet(), stunnedGroups = stunned.toSet(),
            darknessFear = darknessFear, rng = rng,
            defeatedHeroes = defeatedHeroes.toSet(), lastStandUsed = lastStandUsed.toSet(),
            ownedItems = ownedItemsAtStart, itemsFound = itemsFound.toList(), testProgress = testProgress.toMap(), enemyBanners = enemyBanners.toMap(),
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

        fun interact(tokenId: String, showText: Boolean = true) {
            val token = scenario.token(tokenId) ?: return
            interactedTokens += tokenId
            if (token.removeAfterInteract) activeTokens -= tokenId
            if (token.inspectText != null && showText) prompts += Prompt.Inspect(tokenId)
            token.onInteract?.let { agenda += Frame.EventFrame(it) }
            fireMatching { it is TriggerCondition.TokenInteracted && it.token == tokenId }
        }

        fun explore(placementId: String) {
            val placement = scenario.placement(placementId) ?: return
            explored += placementId
            val revealed = scenario.chapters.filter { it.id in unlockedChapters }.flatMap { it.tokens }
                .filter { (it.initiallyActive || it.id in pendingTokens) && it.id !in interactedTokens && it.location.placementId == placementId }
                .map { it.id }.filter { activeTokens.add(it) }
            pendingTokens -= revealed.toSet()
            prompts += Prompt.Explored(placementId, revealed)
            placement.onExplore?.let { agenda += Frame.EventFrame(it) }
            fireMatching { it is TriggerCondition.TileExplored && it.placement == placementId }
        }

        /** Enemy type of [groupId] with its elite bonuses applied. */
        fun typeOf(groupId: String): io.github.aljadvb.worldbuilder.model.EnemyType? =
            scenario.enemyGroup(groupId)?.let { g -> enemyTypes[g.enemyType]?.let(g::effectiveType) }

        fun attack(c: Command.AttackEnemy, counter: Boolean = true) {
            val group = scenario.enemyGroup(c.groupId) ?: return
            val type = typeOf(c.groupId)
            val health = (enemyHealth[c.groupId] ?: List(group.count[heroCount] ?: 1) { type?.health ?: 1 }).toMutableList()
            if (c.sunder) armorLoss[c.groupId] = (armorLoss[c.groupId] ?: 0) + 1
            val armor = if (c.pierce) 0 else ((type?.armor ?: 0) - (armorLoss[c.groupId] ?: 0)).coerceAtLeast(0)
            val sorcery = if (c.smite) 0 else type?.sorcery ?: 0
            val effective = (c.hits - armor - sorcery).coerceAtLeast(0)
            fun hit(i: Int, amount: Int): Int {
                val before = health[i]
                val dealt = minOf(amount, before)
                health[i] = before - dealt
                if (c.lethal && dealt * 2 >= before && dealt > 0) health[i] = 0
                return dealt
            }
            val before = health.count { it > 0 }
            if (c.cleave) {
                health.indices.filter { health[it] > 0 }.forEach { hit(it, effective) }
            } else {
                var left = effective
                // All hits go to one enemy until it is defeated, then to the next.
                for (i in health.indices) {
                    if (left <= 0) break
                    if (health[i] <= 0) continue
                    left -= hit(i, left)
                }
            }
            val remaining = health.count { it > 0 }
            enemyHealth[c.groupId] = health
            // Steadfast elite groups cannot be stunned.
            val stun = c.stun && remaining > 0 && !group.has(io.github.aljadvb.worldbuilder.model.EliteBonus.STEADFAST)
            if (stun) { exhausted += c.groupId; stunned += c.groupId }
            prompts += Prompt.AttackResult(c.groupId, before - remaining, remaining, stun)
            if (remaining == 0) defeat(c.groupId)
            // Exhausted groups cannot counterattack, elite groups can unless they are stunned (rules 25.2, 65).
            else if (counter && (c.groupId !in exhausted || (group.elite && c.groupId !in stunned))) prompts += Prompt.CounterQuestion(c.groupId)
        }

        /** One attack of [groupId], picked at random from its type. */
        private fun rollAttack(groupId: String): Pair<io.github.aljadvb.worldbuilder.model.EnemyAttack?, io.github.aljadvb.worldbuilder.model.EnemyType?> {
            val type = typeOf(groupId)
            return type?.attacks?.takeIf { it.isNotEmpty() }?.let { it[random(it.size)] } to type
        }

        /** Provoked attack or counterattack; [first] puts it before other waiting prompts. */
        fun enemyAttack(groupId: String, reason: AttackReason, first: Boolean = false) {
            val (attack, type) = rollAttack(groupId)
            // Furious elite groups hit harder when they strike back.
            val furious = reason == AttackReason.COUNTER && scenario.enemyGroup(groupId)?.has(io.github.aljadvb.worldbuilder.model.EliteBonus.FURIOUS) == true
            val p = Prompt.EnemyAttack(groupId, reason, (attack?.damage ?: 1) + if (furious) 1 else 0, attack?.fear ?: 0, attack?.text.orEmpty(), type?.ranged == true, attack?.negate, attack?.condition)
            if (first) prompts.add(0, p) else prompts += p
        }

        fun limitReached(hero: Int, kind: LimitKind) {
            if (hero in lastStandUsed) defeatHero(hero, kind, secondTime = true)
            else prompts += Prompt.LastStandTest(hero, kind)
        }

        fun lastStandResult(test: Prompt.LastStandTest, passed: Boolean) {
            lastStandUsed += test.hero
            if (passed) prompts.add(0, Prompt.HeroSurvives(test.hero, test.kind))
            else defeatHero(test.hero, test.kind, secondTime = false, first = true)
        }

        private fun defeatHero(hero: Int, kind: LimitKind, secondTime: Boolean, first: Boolean = false) {
            defeatedHeroes += hero
            val p = Prompt.HeroDefeated(hero, kind, secondTime)
            if (first) prompts.add(0, p) else prompts += p
        }

        fun defeat(groupId: String) {
            enemyHealth -= groupId
            enemyBanners -= groupId
            // Reward: the hero who defeated the group takes 1 inspiration.
            if (groupId in activeGroups) prompts += Prompt.Inspiration(1, io.github.aljadvb.worldbuilder.model.HeroTarget.ACTIVE, DEFEAT_TEXT)
            exhausted -= groupId
            stunned -= groupId
            activeGroups -= groupId
            defeatedGroups += groupId
            fireMatching { it is TriggerCondition.EnemyGroupDefeated && it.group == groupId }
        }

        fun endRound() {
            // A defeated hero loses the adventure at the start of the next shadow phase.
            if (defeatedHeroes.isNotEmpty()) {
                scenario.resolutions.firstOrNull { it.outcome == io.github.aljadvb.worldbuilder.model.Outcome.DEFEAT }?.let {
                    resolution = it.id
                    agenda.clear()
                    prompts.clear()
                    return
                }
            }
            val living = (0 until heroCount).filter { it !in defeatedHeroes }.ifEmpty { (0 until heroCount).toList() }
            // Shadow phase 1: enemy activation.
            for (groupId in scenario.enemyGroups.map { it.id }.filter { it in activeGroups }) {
                if (exhausted.remove(groupId)) {
                    stunned -= groupId
                    prompts += Prompt.EnemyRecovers(groupId)
                    continue
                }
                val type = typeOf(groupId)
                val attack = type?.attacks?.takeIf { it.isNotEmpty() }?.let { it[random(it.size)] }
                prompts += Prompt.EnemyActivation(groupId, living[random(living.size)], type?.movement ?: 2, attack?.damage ?: 1, attack?.fear ?: 0, attack?.text.orEmpty(), type?.ranged == true, attack?.negate, attack?.condition)
            }
            // Shadow phase 2: darkness.
            if (darknessFear > 0) prompts += Prompt.Darkness(darknessFear)
            // Shadow event ("something happens"), runs after the notices.
            scenario.shadowEvents.takeIf { it.isNotEmpty() }?.let { events -> agenda += Frame.EventFrame(events[random(events.size)]) }
            // Shadow phase 3: threat.
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
                execute(steps[frame.stepIndex], (frame as? Frame.EventFrame)?.eventId, frame.stepIndex)
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

        private fun execute(step: EventStep, eventId: String?, index: Int = 0) {
            when (step) {
                is EventStep.Text -> prompts += Prompt.Text(step.text)
                is EventStep.Choice -> prompts += Prompt.Choice(eventId, step)
                is EventStep.Test -> {
                    val key = "$eventId#$index"
                    prompts += Prompt.Test(eventId, step, key, testProgress[key] ?: 0)
                }
                is EventStep.SpawnEnemies -> {
                    val group = scenario.enemyGroup(step.group) ?: return
                    if (activeGroups.add(group.id)) {
                        defeatedGroups -= group.id
                        val type = typeOf(group.id)
                        enemyHealth[group.id] = List(group.count[heroCount] ?: 0) { type?.health ?: 1 }
                        armorLoss -= group.id
                        // Elite groups always get an elite banner (numbered among elite groups); normal groups
                        // get a banner when another normal group of the same type is on the map.
                        fun isElite(id: String) = scenario.enemyGroup(id)?.elite == true
                        val needsBanner = group.elite || activeGroups.any { it != group.id && !isElite(it) && scenario.enemyGroup(it)?.enemyType == group.enemyType }
                        val taken = enemyBanners.filterKeys { isElite(it) == group.elite }.values
                        val banner = if (needsBanner) generateSequence(1) { it + 1 }.first { it !in taken } else null
                        banner?.let { enemyBanners[group.id] = it }
                        prompts += Prompt.SpawnEnemies(group.id, group.count[heroCount] ?: 0, banner, group.elite)
                    }
                }
                is EventStep.ChangeThreat -> {
                    val before = threat
                    changeThreat(step.amount)
                    if (threat != before) prompts += Prompt.ThreatChanged(threat - before, threat)
                }
                is EventStep.AddToken -> {
                    val token = scenario.token(step.token)
                    val placement = token?.location?.placementId
                    val hidden = placement != null && placement !in explored &&
                        scenario.chapters.any { ch -> ch.tiles.any { it.id == placement && it.unexplored } }
                    // A token on a tile that is not explored yet waits until the tile is explored.
                    if (token != null && hidden) { if (step.token !in activeTokens) pendingTokens += step.token }
                    else if (token != null && activeTokens.add(step.token)) prompts += Prompt.PlaceToken(step.token)
                }
                is EventStep.RemoveToken -> {
                    pendingTokens -= step.token
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
                is EventStep.SetDarkness -> darknessFear = step.fear.coerceAtLeast(0)
                is EventStep.CheckFlag -> replaceCurrent(if (step.flag in flags) step.ifSet else step.ifNotSet)
                is EventStep.GainLore -> if (step.amount > 0) {
                    loreGained += step.amount
                    prompts += Prompt.Lore(step.amount, step.text)
                }
                is EventStep.GainInspiration -> prompts += Prompt.Inspiration(step.amount, step.who, step.text)
                is EventStep.AffectEnemy -> if (step.group in activeGroups) {
                    step.text?.let { prompts += Prompt.Text(it) }
                    // Effects from events never cause a counterattack (rules 45.3).
                    attack(Command.AttackEnemy(step.group, step.hits, step.pierce, step.smite, step.sunder, step.cleave, step.stun, step.lethal), counter = false)
                }
                is EventStep.Inscription -> when (val attribute = step.test) {
                    null -> prompts += Prompt.Inscription(step.elvish, step.translation, step.text)
                    else -> {
                        // Riddle: the translation only shows after a passed test.
                        prompts += Prompt.Inscription(step.elvish, step.translation, step.text, translationHidden = true)
                        val test = EventStep.Test(attribute = attribute, successes = step.successes, onSuccess = step.onSuccess.orEmpty(), onFailure = step.onFailure)
                        prompts += Prompt.Test(eventId, test, reveal = Prompt.Inscription(step.elvish, step.translation))
                    }
                }
                is EventStep.GainCondition -> prompts += Prompt.Condition(step.condition, step.who, step.text)
                is EventStep.Recover -> if (step.damage > 0 || step.fear > 0) prompts += Prompt.Recover(step.damage, step.fear, step.who, step.text)
                is EventStep.GainItem -> {
                    val taken = ownedItemsAtStart + itemsFound
                    val byId = items.associateBy { it.id }
                    // A card is free while fewer copies are owned than exist.
                    fun free(i: io.github.aljadvb.worldbuilder.model.Item) = taken.count { it == i.id } < i.count
                    // A line is free while the group owns fewer cards of it than the line has starting copies.
                    fun lineFree(line: String): Boolean {
                        val starts = items.filter { it.line == line }.let { l -> l.filter { it.level == l.minOf { x -> x.level } } }.sumOf { it.count }
                        return taken.count { byId[it]?.line == line } < starts
                    }
                    val wanted = step.item?.takeIf { id -> byId[id]?.let(::free) == true }
                    // A fixed item already owned (e.g. a replayed scenario) is replaced by a random trinket.
                    // Random finds are the lowest level of a line that still has a free copy.
                    val pool = items.filter { it.kind == io.github.aljadvb.worldbuilder.model.ItemKind.TRINKET && free(it) && (it.line?.let(::lineFree) ?: true) }
                        .groupBy { it.line ?: it.id }.values.map { line -> line.minBy { it.level } }
                    val found = wanted ?: pool.takeIf { it.isNotEmpty() }?.let { it[random(it.size)].id }
                    found?.let { itemsFound += it }
                    prompts += Prompt.ItemFound(found, step.who, step.text)
                }
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
                // Enemies standing on the cleared boards leave the table too (no defeat, no reward).
                val gone = activeGroups.filter { scenario.enemyGroup(it)?.location is io.github.aljadvb.worldbuilder.model.SquareLocation }
                activeGroups -= gone.toSet(); gone.forEach { enemyHealth -= it; enemyBanners -= it; exhausted -= it; stunned -= it }
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
        const val DEFEAT_TEXT = "Die Gegnergruppe ist besiegt!"
    }
}
