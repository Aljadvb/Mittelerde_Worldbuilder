package io.github.aljadvb.worldbuilder.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Token(
    val id: String,
    val type: TokenType,
    /** Required if [type] is [TokenType.CUSTOM]; forbidden otherwise. */
    val customType: String? = null,
    val label: String,
    val location: Location,
    val initiallyActive: Boolean = true,
    val inspectText: String? = null,
    /** Event run when the token is interacted with. */
    val onInteract: String? = null,
    val removeAfterInteract: Boolean = false,
)

@Serializable
enum class TokenType {
    @SerialName("search") SEARCH,
    @SerialName("person") PERSON,
    @SerialName("threat") THREAT,
    @SerialName("explore") EXPLORE,
    @SerialName("custom") CUSTOM,
}

@Serializable
enum class Attribute {
    @SerialName("might") MIGHT,
    @SerialName("agility") AGILITY,
    @SerialName("wisdom") WISDOM,
    @SerialName("spirit") SPIRIT,
    @SerialName("wit") WIT,
}

@Serializable
enum class Outcome {
    @SerialName("victory") VICTORY,
    @SerialName("defeat") DEFEAT,
    @SerialName("neutral") NEUTRAL,
}
