package io.github.aljadvb.worldbuilder.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * An event is a sequence of steps. Branching steps ([EventStep.isTerminal]) may only
 * appear as the last step; branches always continue in another event.
 */
@Serializable
data class Event(
    val id: String,
    val steps: List<EventStep>,
)

@Serializable
sealed interface EventStep {
    /** Steps that end the step list of an event (branching or ending the scenario). */
    val isTerminal: Boolean get() = false

    @Serializable
    @SerialName("text")
    data class Text(val text: String) : EventStep

    @Serializable
    @SerialName("choice")
    data class Choice(val text: String, val options: List<ChoiceOption>) : EventStep {
        override val isTerminal get() = true
    }

    @Serializable
    @SerialName("test")
    data class Test(
        val text: String? = null,
        val attribute: Attribute,
        val successes: Int,
        val onSuccess: String,
        val onFailure: String? = null,
    ) : EventStep {
        override val isTerminal get() = true
    }

    @Serializable
    @SerialName("spawnEnemies")
    data class SpawnEnemies(val group: String) : EventStep

    @Serializable
    @SerialName("changeThreat")
    data class ChangeThreat(val amount: Int) : EventStep

    @Serializable
    @SerialName("addToken")
    data class AddToken(val token: String) : EventStep

    @Serializable
    @SerialName("removeToken")
    data class RemoveToken(val token: String) : EventStep

    @Serializable
    @SerialName("unlockChapter")
    data class UnlockChapter(val chapter: String) : EventStep

    @Serializable
    @SerialName("setObjective")
    data class SetObjective(val objective: String) : EventStep

    @Serializable
    @SerialName("fireTrigger")
    data class FireTrigger(val trigger: String) : EventStep

    /** Reward: heroes take inspiration markers (the app does not count them). */
    @Serializable
    @SerialName("gainInspiration")
    data class GainInspiration(
        val amount: Int = 1,
        val who: InspirationTarget = InspirationTarget.ACTIVE,
        val text: String? = null,
    ) : EventStep

    @Serializable
    @SerialName("endScenario")
    data class EndScenario(val resolution: String) : EventStep {
        override val isTerminal get() = true
    }
}

@Serializable
enum class InspirationTarget {
    /** The hero who is acting (e.g. made the test or interacted). */
    @SerialName("active") ACTIVE,
    @SerialName("each") EACH,
    /** One hero chosen by the group. */
    @SerialName("chosen") CHOSEN,
}

@Serializable
data class ChoiceOption(
    val label: String,
    val event: String,
)
