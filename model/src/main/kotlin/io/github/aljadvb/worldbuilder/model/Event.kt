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
        val who: HeroTarget = HeroTarget.ACTIVE,
        val text: String? = null,
    ) : EventStep

    /** Darkness: in each shadow phase heroes in darkness suffer [fear] (0 switches it off). */
    @Serializable
    @SerialName("setDarkness")
    data class SetDarkness(val fear: Int = 1) : EventStep

    /** Sets a campaign flag (silent). */
    @Serializable
    @SerialName("setFlag")
    data class SetFlag(val flag: String) : EventStep

    /** Branches on a campaign flag. */
    @Serializable
    @SerialName("checkFlag")
    data class CheckFlag(val flag: String, val ifSet: String, val ifNotSet: String? = null) : EventStep {
        override val isTerminal get() = true
    }

    /** The group's lore rises (kept by the app for the whole campaign). */
    @Serializable
    @SerialName("gainLore")
    data class GainLore(val amount: Int = 1, val text: String? = null) : EventStep

    /** Heroes suffer damage and/or fear (cards at the table; the app does not count them). */
    @Serializable
    @SerialName("sufferHarm")
    data class SufferHarm(
        val damage: Int = 0,
        val fear: Int = 0,
        val who: HeroTarget = HeroTarget.ACTIVE,
        /** Cards are taken face down ("verdeckt"). */
        val hidden: Boolean = false,
        val text: String? = null,
    ) : EventStep

    @Serializable
    @SerialName("endScenario")
    data class EndScenario(val resolution: String) : EventStep {
        override val isTerminal get() = true
    }
}

@Serializable
enum class HeroTarget {
    /** Who is affected by a reward or harm. The hero who is acting (e.g. made the test or interacted). */
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
