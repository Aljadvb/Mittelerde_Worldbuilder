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
        /** The app does not tell the players how many successes are needed. */
        val hidden: Boolean = false,
        /** Successes add up over several attempts (one action each, possibly by different heroes). */
        val cumulative: Boolean = false,
        /** Cumulative tests only: runs after an attempt that is not yet enough. */
        val onAttempt: String? = null,
        /**
         * Hard tests may offer help for corruption: if the players ask for it, the acting hero
         * takes 1 corruption and the test needs this many successes less (never below 1).
         */
        val corruptionEase: Int = 0,
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

    /** Heroes take a boon (Hidden, Inspired, Determined) or curse card (Captured, Disheartened, Frightened). */
    @Serializable
    @SerialName("gainCondition")
    data class GainCondition(
        val condition: Condition,
        val who: HeroTarget = HeroTarget.ACTIVE,
        val text: String? = null,
    ) : EventStep

    /**
     * Heroes take corruption for an evil deed or for using a very powerful item (e.g. a cursed crown).
     * The deed is the players' decision, so the scenario lets them choose it first.
     */
    @Serializable
    @SerialName("gainCorruption")
    data class GainCorruption(
        val amount: Int = 1,
        val who: HeroTarget = HeroTarget.ACTIVE,
        val text: String? = null,
    ) : EventStep

    /** A hero earns a title card (catalog skill ID); it stays with the hero for the whole campaign. */
    @Serializable
    @SerialName("gainTitle")
    data class GainTitle(
        val title: String,
        val text: String? = null,
    ) : EventStep

    /** Heroes shuffle weakness cards into their skill deck (removed again between adventures). */
    @Serializable
    @SerialName("gainWeakness")
    data class GainWeakness(
        val amount: Int = 1,
        val who: HeroTarget = HeroTarget.ACTIVE,
        val text: String? = null,
    ) : EventStep

    /** Heroes lose corruption (e.g. purification); the app asks which hero unless it is everyone. */
    @Serializable
    @SerialName("loseCorruption")
    data class LoseCorruption(
        val amount: Int = 1,
        val who: HeroTarget = HeroTarget.ACTIVE,
        val text: String? = null,
    ) : EventStep

    /** Branch on whether the acting hero is corrupted (1 or more corruption); the app asks which hero acts. */
    @Serializable
    @SerialName("checkCorruption")
    data class CheckCorruption(
        val ifCorrupted: String,
        val ifNotCorrupted: String? = null,
        val text: String? = null,
    ) : EventStep {
        override val isTerminal get() = true
    }

    /** A rough-ground or fortified marker is laid on a field. */
    @Serializable
    @SerialName("placeMarker")
    data class PlaceMarker(
        val marker: FieldMarker,
        val location: Location,
        val text: String? = null,
    ) : EventStep

    @Serializable
    @SerialName("removeMarker")
    data class RemoveMarker(
        val marker: FieldMarker,
        val location: Location,
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

    /** The group finds an item: [item] from the catalog, or a random trinket not owned yet if null. */
    @Serializable
    @SerialName("gainItem")
    data class GainItem(
        val item: String? = null,
        val who: HeroTarget = HeroTarget.ACTIVE,
        val text: String? = null,
    ) : EventStep

    /** Tactical effect on an enemy group: hits and attack modifiers without a counterattack (e.g. a rolling boulder). */
    @Serializable
    @SerialName("affectEnemy")
    data class AffectEnemy(
        val group: String,
        val hits: Int = 0,
        val pierce: Boolean = false,
        val smite: Boolean = false,
        val sunder: Boolean = false,
        val cleave: Boolean = false,
        val stun: Boolean = false,
        val lethal: Boolean = false,
        val text: String? = null,
    ) : EventStep

    /**
     * An inscription or saying in an Elven tongue: shown in Tengwar with transcription and translation.
     * As a riddle ([test] set) the translation is only shown after the test succeeds.
     */
    @Serializable
    @SerialName("inscription")
    data class Inscription(
        /** Sindarin or Quenya in Latin letters, e.g. "Pedo mellon a minno". */
        val elvish: String,
        /** German translation; shown at once, or for a riddle only after a passed test. */
        val translation: String,
        /** Optional context, e.g. "In den Torbogen sind Zeichen geritzt." */
        val text: String? = null,
        /** Riddle: attribute of the test that reveals the translation. */
        val test: Attribute? = null,
        /** Riddle: successes needed. */
        val successes: Int = 2,
        /** Riddle: event after a passed test (optional). */
        val onSuccess: String? = null,
        /** Riddle: event after a failed test (optional). */
        val onFailure: String? = null,
    ) : EventStep {
        /** A riddle branches like a test, so it must be the last step. */
        override val isTerminal get() = test != null
    }

    /** Reward: heroes discard damage and/or fear cards (the app does not count them). */
    @Serializable
    @SerialName("recover")
    data class Recover(
        val damage: Int = 0,
        val fear: Int = 0,
        val who: HeroTarget = HeroTarget.ACTIVE,
        val text: String? = null,
    ) : EventStep

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
    /**
     * Corruption the acting hero takes for this choice. Corruption only ever comes from such an
     * option, so the players always see it on the button before they decide.
     */
    val corruption: Int = 0,
)

/** Conditions a hero can receive as a card: boons help, curses hinder. A hero never holds the same one twice. */
@Serializable
enum class Condition(val isCurse: Boolean) {
    @SerialName("hidden") HIDDEN(false),
    @SerialName("inspired") INSPIRED(false),
    @SerialName("determined") DETERMINED(false),
    /** Curse: the hero takes a random "Captured" card face down. */
    @SerialName("captured") CAPTURED(true),
    @SerialName("disheartened") DISHEARTENED(true),
    @SerialName("frightened") FRIGHTENED(true),
}

/** Markers that change a field: rough ground (moving in costs a card or ends the move), fortified (holding the field) and darkness. */
@Serializable
enum class FieldMarker {
    @SerialName("rough") ROUGH,
    @SerialName("fortified") FORTIFIED,
    /** The field is in darkness: heroes on it suffer fear in the shadow phase. */
    @SerialName("darkness") DARKNESS,
}
