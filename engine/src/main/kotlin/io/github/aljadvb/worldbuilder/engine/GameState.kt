package io.github.aljadvb.worldbuilder.engine

import io.github.aljadvb.worldbuilder.model.EventStep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Complete, immutable state of a running scenario. Serializable as-is, so the game
 * can be saved at any moment – including while an event waits for input.
 */
@Serializable
data class GameState(
    val scenarioId: String,
    val heroCount: Int,
    val round: Int = 1,
    val threat: Int = 0,
    /** Unlocked chapters in unlock order. */
    val unlockedChapters: List<String> = emptyList(),
    val activeTokens: Set<String> = emptySet(),
    val interactedTokens: Set<String> = emptySet(),
    val activeEnemyGroups: Set<String> = emptySet(),
    val defeatedEnemyGroups: Set<String> = emptySet(),
    /** Active objectives in activation order. */
    val activeObjectives: List<String> = emptyList(),
    val completedObjectives: Set<String> = emptySet(),
    val firedTriggers: Set<String> = emptySet(),
    /** Unexplored tiles that have been explored. */
    val exploredPlacements: Set<String> = emptySet(),
    /** Work still to do; the first frame is the one currently running. */
    val agenda: List<Frame> = emptyList(),
    /** Prompts waiting for the players, oldest first. While non-empty, nothing else runs. */
    val prompts: List<Prompt> = emptyList(),
    /** Prompts already handled, for the event log. */
    val history: List<HistoryEntry> = emptyList(),
    /** Set once the scenario has ended. */
    val resolution: String? = null,
) {
    val isOver: Boolean get() = resolution != null
    val currentPrompt: Prompt? get() = prompts.firstOrNull()

    /** True if the players may act freely (tap tokens, end the round, report defeated enemies). */
    val isIdle: Boolean get() = prompts.isEmpty() && !isOver
}

/** Tiles of unlocked chapters that still carry an exploration marker. */
fun GameState.unexploredPlacements(scenario: io.github.aljadvb.worldbuilder.model.Scenario): Set<String> =
    unlockedChapters.flatMap { scenario.chapter(it)?.tiles.orEmpty() }.filter { it.unexplored && it.id !in exploredPlacements }.map { it.id }.toSet()

/** A sequence of steps being executed. */
@Serializable
sealed interface Frame {
    val stepIndex: Int

    @Serializable
    @SerialName("event")
    data class EventFrame(val eventId: String, override val stepIndex: Int = 0) : Frame

    /** Engine-generated steps (e.g. scenario start). */
    @Serializable
    @SerialName("inline")
    data class InlineFrame(val steps: List<EventStep>, override val stepIndex: Int = 0) : Frame
}

/** Something the app has to show to the players. */
@Serializable
sealed interface Prompt {
    /** Prompts that only need to be acknowledged. */
    sealed interface Notice : Prompt

    @Serializable @SerialName("intro")
    data object Intro : Notice

    @Serializable @SerialName("text")
    data class Text(val text: String) : Notice

    @Serializable @SerialName("inspect")
    data class Inspect(val tokenId: String) : Notice

    @Serializable @SerialName("setup")
    data class Setup(val chapterId: String) : Notice

    @Serializable @SerialName("spawnEnemies")
    data class SpawnEnemies(val groupId: String, val count: Int) : Notice

    @Serializable @SerialName("placeToken")
    data class PlaceToken(val tokenId: String) : Notice

    @Serializable @SerialName("removeToken")
    data class RemoveToken(val tokenId: String) : Notice

    @Serializable @SerialName("threatChanged")
    data class ThreatChanged(val delta: Int, val threat: Int) : Notice

    @Serializable @SerialName("roundEnded")
    data class RoundEnded(val round: Int, val threatAdded: Int, val threat: Int) : Notice

    @Serializable @SerialName("objectiveSet")
    data class ObjectiveSet(val objectiveId: String) : Notice

    @Serializable @SerialName("objectiveCompleted")
    data class ObjectiveCompleted(val objectiveId: String) : Notice

    /** A tile was explored: [tokenIds] are laid out now; the exploring or a near hero takes 1 inspiration. */
    @Serializable @SerialName("explored")
    data class Explored(val placementId: String, val tokenIds: List<String>) : Notice

    @Serializable @SerialName("harm")
    data class Harm(val damage: Int, val fear: Int, val who: io.github.aljadvb.worldbuilder.model.HeroTarget, val hidden: Boolean, val text: String? = null) : Notice

    @Serializable @SerialName("inspiration")
    data class Inspiration(val amount: Int, val who: io.github.aljadvb.worldbuilder.model.HeroTarget, val text: String? = null) : Notice

    /** Answer with [Command.Choose]. */
    @Serializable @SerialName("choice")
    data class Choice(val eventId: String?, val step: EventStep.Choice) : Prompt

    /** Answer with [Command.ReportTest]. */
    @Serializable @SerialName("test")
    data class Test(val eventId: String?, val step: EventStep.Test) : Prompt
}

@Serializable
data class HistoryEntry(val round: Int, val prompt: Prompt, val answer: String? = null)

/** Player actions. */
sealed interface Command {
    /** Dismiss the current [Prompt.Notice]. */
    data object Acknowledge : Command

    /** Pick option [index] of the current [Prompt.Choice]. */
    data class Choose(val index: Int) : Command

    /** Report the number of successes for the current [Prompt.Test]. */
    data class ReportTest(val successes: Int) : Command

    data class InteractToken(val tokenId: String) : Command
    /** Tap on the exploration marker of an unexplored tile. */
    data class ExploreTile(val placementId: String) : Command
    data class DefeatEnemyGroup(val groupId: String) : Command
    data object EndRound : Command
}

class EngineException(message: String) : IllegalStateException(message)
