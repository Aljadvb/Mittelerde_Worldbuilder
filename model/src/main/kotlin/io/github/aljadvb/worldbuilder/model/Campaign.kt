package io.github.aljadvb.worldbuilder.model

import kotlinx.serialization.Serializable

const val CURRENT_FORMAT_VERSION = 1

/** Root object of a campaign file. All references between objects are by ID. */
@Serializable
data class Campaign(
    val formatVersion: Int,
    val id: String,
    val title: String,
    val description: String = "",
    val startScenario: String,
    val scenarios: List<Scenario>,
    val transitions: List<ScenarioTransition> = emptyList(),
) {
    fun scenario(id: String): Scenario? = scenarios.firstOrNull { it.id == id }

    /** Next scenario after [scenarioId] ended with [resolutionId], or null if the campaign ends. */
    fun nextScenario(scenarioId: String, resolutionId: String): String? =
        transitions.firstOrNull { it.from == scenarioId && it.resolution == resolutionId }?.to
}

@Serializable
data class ScenarioTransition(
    val from: String,
    val resolution: String,
    val to: String,
)

@Serializable
data class Scenario(
    val id: String,
    val title: String,
    val intro: String,
    val threat: ThreatConfig,
    val startChapter: String,
    val startObjective: String,
    val chapters: List<Chapter>,
    val enemyGroups: List<EnemyGroup> = emptyList(),
    val events: List<Event> = emptyList(),
    val triggers: List<Trigger> = emptyList(),
    val objectives: List<Objective> = emptyList(),
    val resolutions: List<Resolution>,
) {
    val tokens: List<Token> get() = chapters.flatMap { it.tokens }
    val placements: List<TilePlacement> get() = chapters.flatMap { it.tiles }

    fun chapter(id: String) = chapters.firstOrNull { it.id == id }
    fun token(id: String) = tokens.firstOrNull { it.id == id }
    fun placement(id: String) = placements.firstOrNull { it.id == id }
    fun event(id: String) = events.firstOrNull { it.id == id }
    fun trigger(id: String) = triggers.firstOrNull { it.id == id }
    fun objective(id: String) = objectives.firstOrNull { it.id == id }
    fun enemyGroup(id: String) = enemyGroups.firstOrNull { it.id == id }
    fun resolution(id: String) = resolutions.firstOrNull { it.id == id }

    /** The chapter that defines the given placement. */
    fun chapterOfPlacement(placementId: String) = chapters.firstOrNull { c -> c.tiles.any { it.id == placementId } }

    /** The chapter that defines the given token. */
    fun chapterOfToken(tokenId: String) = chapters.firstOrNull { c -> c.tokens.any { it.id == tokenId } }
}

@Serializable
data class ThreatConfig(
    val max: Int,
    val perRound: ThreatPerRound = ThreatPerRound(),
)

/** Threat added at the end of each round: `base + perHero × heroCount`. */
@Serializable
data class ThreatPerRound(
    val base: Int = 0,
    val perHero: Int = 0,
) {
    fun amountFor(heroCount: Int) = base + perHero * heroCount
}

@Serializable
data class Chapter(
    val id: String,
    val title: String,
    val setupText: String = "",
    val onUnlock: String? = null,
    val tiles: List<TilePlacement> = emptyList(),
    val tokens: List<Token> = emptyList(),
)

@Serializable
data class TilePlacement(
    val id: String,
    /** Tile ID from the material catalog. */
    val tile: String,
    val side: TileSide,
    /** Map coordinate of the tile's local (0,0) anchor. */
    val position: HexCoord,
    /** 0–5, steps of 60° clockwise around the anchor. */
    val rotation: Int = 0,
) {
    /** Converts a tile-local cell into a map coordinate. */
    fun toMap(local: HexCoord): HexCoord = local.rotateClockwise(rotation) + position
}

/** A cell on a specific placed tile, in tile-local coordinates. */
@Serializable
data class TileLocation(
    val placement: String,
    val cell: HexCoord,
)

@Serializable
data class EnemyGroup(
    val id: String,
    /** Enemy type ID from the material catalog. */
    val enemyType: String,
    /** Number of enemies by hero count (keys 1–5). */
    val count: Map<Int, Int>,
    val location: TileLocation,
)

@Serializable
data class Objective(
    val id: String,
    val title: String,
    val reminder: String,
    /** Trigger that completes this objective while it is active. */
    val completedBy: String,
)

@Serializable
data class Resolution(
    val id: String,
    val title: String,
    val text: String,
    val outcome: Outcome,
)
