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
    /** Campaign flags: those set before this scenario plus those set during it. */
    val flags: Set<String> = emptySet(),
    /** Lore gained during this scenario (added to the campaign at the end). */
    val loreGained: Int = 0,
    /** Remaining health of each enemy, per active group. */
    val enemyHealth: Map<String, List<Int>> = emptyMap(),
    /** Permanent armor loss per group ("Zerfallen"). */
    val armorLoss: Map<String, Int> = emptyMap(),
    /** Exhausted groups skip their next activation. */
    val exhaustedGroups: Set<String> = emptySet(),
    /** Fear in darkness per shadow phase (0 = no darkness). */
    val darknessFear: Int = 0,
    /** State of the deterministic random generator (targets, attacks, shadow events). */
    val rng: Long = 0,
    /** Unexplored tiles that have been explored. */
    val exploredPlacements: Set<String> = emptySet(),
    /** Tokens added by an event onto a tile that is not explored yet; they appear when it is explored. */
    val pendingTokens: Set<String> = emptySet(),
    /** Heroes (0-based) defeated in this scenario; the scenario is lost at the start of the next shadow phase. */
    val defeatedHeroes: Set<Int> = emptySet(),
    /** Heroes that already had their one last stand in this scenario. */
    val lastStandUsed: Set<Int> = emptySet(),
    /** Items the group owned when the scenario started (from the campaign inventory). */
    val ownedItems: List<String> = emptyList(),
    /** Items found in this scenario, in order (added to the campaign inventory at the end). */
    val itemsFound: List<String> = emptyList(),
    /** Numbered enemy banners of active groups (a group gets one when another group of its type is on the map). */
    val enemyBanners: Map<String, Int> = emptyMap(),
    /** Successes collected so far per cumulative test (key: event id and step index). */
    val testProgress: Map<String, Int> = emptyMap(),
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
    data class SpawnEnemies(val groupId: String, val count: Int, val banner: Int? = null) : Notice

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

    /** Shadow phase: an enemy group moves and attacks hero number [hero] (0-based). */
    @Serializable @SerialName("enemyActivation")
    data class EnemyActivation(val groupId: String, val hero: Int, val movement: Int, val damage: Int, val fear: Int, val text: String, val ranged: Boolean, val negate: io.github.aljadvb.worldbuilder.model.Attribute? = null) : Notice

    /** Shadow phase: an exhausted group recovers instead of acting. */
    @Serializable @SerialName("enemyRecovers")
    data class EnemyRecovers(val groupId: String) : Notice

    /** "Kein Ziel": the group moves up to [movement] spaces toward the nearest hero and does not attack. */
    @Serializable @SerialName("enemyRegroup")
    data class EnemyRegroup(val groupId: String, val movement: Int) : Notice

    /** "Bewegung beendet": the group stops at a river, fence or ditch and attacks only a hero in reach. */
    @Serializable @SerialName("enemyHalted")
    data class EnemyHalted(val groupId: String, val damage: Int, val fear: Int, val text: String, val ranged: Boolean, val negate: io.github.aljadvb.worldbuilder.model.Attribute? = null) : Notice

    /** A provoked group or a counterattack hits the hero who provoked or attacked it. */
    @Serializable @SerialName("enemyAttack")
    data class EnemyAttack(val groupId: String, val reason: AttackReason, val damage: Int, val fear: Int, val text: String, val ranged: Boolean, val negate: io.github.aljadvb.worldbuilder.model.Attribute? = null) : Notice

    /** A hero survived the last stand and discards all cards of [kind]. */
    @Serializable @SerialName("heroSurvives")
    data class HeroSurvives(val hero: Int, val kind: LimitKind) : Notice

    /** A hero is defeated; [secondTime] if the hero had used the last stand already. */
    @Serializable @SerialName("heroDefeated")
    data class HeroDefeated(val hero: Int, val kind: LimitKind, val secondTime: Boolean) : Notice

    /** The group found item [itemId]; null if every trinket is already owned. */
    @Serializable @SerialName("itemFound")
    data class ItemFound(val itemId: String?, val who: io.github.aljadvb.worldbuilder.model.HeroTarget, val text: String? = null) : Notice

    @Serializable @SerialName("recover")
    data class Recover(val damage: Int, val fear: Int, val who: io.github.aljadvb.worldbuilder.model.HeroTarget, val text: String? = null) : Notice

    @Serializable @SerialName("inscription")
    data class Inscription(
        val elvish: String,
        val translation: String,
        val text: String? = null,
        /** Riddle not solved yet: the translation must not be shown. */
        val translationHidden: Boolean = false,
    ) : Notice

    @Serializable @SerialName("darkness")
    data class Darkness(val fear: Int) : Notice

    /** Result of an attack on an enemy group. */
    @Serializable @SerialName("attackResult")
    data class AttackResult(val groupId: String, val defeated: Int, val remaining: Int, val stunned: Boolean) : Notice

    @Serializable @SerialName("lore")
    data class Lore(val amount: Int, val text: String? = null) : Notice

    @Serializable @SerialName("harm")
    data class Harm(val damage: Int, val fear: Int, val who: io.github.aljadvb.worldbuilder.model.HeroTarget, val hidden: Boolean, val text: String? = null) : Notice

    @Serializable @SerialName("inspiration")
    data class Inspiration(val amount: Int, val who: io.github.aljadvb.worldbuilder.model.HeroTarget, val text: String? = null) : Notice

    /** Answer with [Command.Choose]. */
    @Serializable @SerialName("choice")
    data class Choice(val eventId: String?, val step: EventStep.Choice) : Prompt

    /** Answer with [Command.ReportTest]. */
    @Serializable @SerialName("test")
    data class Test(
        val eventId: String?,
        val step: EventStep.Test,
        val key: String? = null,
        val progress: Int = 0,
        /** Riddle: inscription shown with its translation when the test succeeds. */
        val reveal: Inscription? = null,
    ) : Prompt

    /** Result of a hidden test (the players do not know the required successes). */
    @Serializable @SerialName("testResult")
    data class TestResult(val passed: Boolean) : Notice

    /** A cumulative test is not done yet; [total] successes so far. */
    @Serializable @SerialName("testProgress")
    data class TestProgress(val step: EventStep.Test, val total: Int) : Notice

    /** "Kann der Feind angreifen?" after a hero's attack. Answer with [Command.CounterAttack]. */
    @Serializable @SerialName("counterQuestion")
    data class CounterQuestion(val groupId: String) : Prompt

    /** Last stand: a test on might (damage) or spirit (fear); 1 success is enough. Answer with [Command.ReportTest]. */
    @Serializable @SerialName("lastStand")
    data class LastStandTest(val hero: Int, val kind: LimitKind) : Prompt {
        val attribute: io.github.aljadvb.worldbuilder.model.Attribute
            get() = if (kind == LimitKind.DAMAGE) io.github.aljadvb.worldbuilder.model.Attribute.MIGHT else io.github.aljadvb.worldbuilder.model.Attribute.SPIRIT
    }
}

@Serializable
enum class AttackReason { @SerialName("provoked") PROVOKED, @SerialName("counter") COUNTER }

/** Which limit a hero reached. */
@Serializable
enum class LimitKind { @SerialName("damage") DAMAGE, @SerialName("fear") FEAR }

@Serializable
data class HistoryEntry(val round: Int, val prompt: Prompt, val answer: String? = null)

/** Player actions. */
sealed interface Command {
    /** Dismiss the current [Prompt.Notice]. */
    data object Acknowledge : Command

    /** Pick option [index] of the current [Prompt.Choice]. */
    data class Choose(val index: Int) : Command

    /** Report the number of successes for the current [Prompt.Test] or [Prompt.LastStandTest]. */
    data class ReportTest(val successes: Int) : Command

    /** [showText] = false if the players already read the token's text before deciding to interact. */
    data class InteractToken(val tokenId: String, val showText: Boolean = true) : Command
    /** A hero attacks an enemy group with [hits] and modifiers. */
    data class AttackEnemy(
        val groupId: String,
        val hits: Int,
        val pierce: Boolean = false,
        val smite: Boolean = false,
        val sunder: Boolean = false,
        val cleave: Boolean = false,
        val stun: Boolean = false,
        val lethal: Boolean = false,
    ) : Command
    /** Tap on the exploration marker of an unexplored tile. */
    data class ExploreTile(val placementId: String) : Command
    data class DefeatEnemyGroup(val groupId: String) : Command
    data object EndRound : Command
    /** During [Prompt.EnemyActivation]: the group can reach neither its target nor the nearest hero. */
    data object NoTarget : Command
    /** During [Prompt.EnemyActivation]: the group had to stop at a river, fence or ditch. */
    data object MovementEnded : Command
    /** A hero leaves the space of a ready group or interacts there. */
    data class ProvokeEnemy(val groupId: String) : Command
    /** Answer to [Prompt.CounterQuestion]. */
    data class CounterAttack(val canAttack: Boolean) : Command
    /** Hero [hero] (0-based) reached the damage or fear limit. */
    data class LimitReached(val hero: Int, val kind: LimitKind) : Command
}

class EngineException(message: String) : IllegalStateException(message)
