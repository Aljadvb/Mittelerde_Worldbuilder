package io.github.aljadvb.worldbuilder.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A named condition that runs events and/or completes objectives when it fires. */
@Serializable
data class Trigger(
    val id: String,
    val on: TriggerCondition,
    val events: List<String> = emptyList(),
    val repeatable: Boolean = false,
)

@Serializable
sealed interface TriggerCondition {
    @Serializable
    @SerialName("tokenInteracted")
    data class TokenInteracted(val token: String) : TriggerCondition

    /** Fires when threat reaches or exceeds [value]. */
    @Serializable
    @SerialName("threatReached")
    data class ThreatReached(val value: Int) : TriggerCondition

    /** Fires when threat reaches the scenario's maximum. */
    @Serializable
    @SerialName("threatMax")
    data object ThreatMax : TriggerCondition

    @Serializable
    @SerialName("enemyGroupDefeated")
    data class EnemyGroupDefeated(val group: String) : TriggerCondition

    @Serializable
    @SerialName("objectiveCompleted")
    data class ObjectiveCompleted(val objective: String) : TriggerCondition

    @Serializable
    @SerialName("roundEnded")
    data class RoundEnded(val round: Int) : TriggerCondition

    /** Fires when the players explore an unexplored tile. */
    @Serializable
    @SerialName("tileExplored")
    data class TileExplored(val placement: String) : TriggerCondition

    /** Fired explicitly by an [EventStep.FireTrigger] step. */
    @Serializable
    @SerialName("manual")
    data object Manual : TriggerCondition
}
