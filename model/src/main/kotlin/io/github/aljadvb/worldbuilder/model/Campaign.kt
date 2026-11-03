package io.github.aljadvb.worldbuilder.model

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject

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
    /** Places on the campaign map. */
    val places: List<Place> = emptyList(),
    /** Campaign flags that remember outcomes across scenarios. */
    val flags: List<CampaignFlag> = emptyList(),
) {
    fun scenario(id: String): Scenario? = scenarios.firstOrNull { it.id == id }
    fun place(id: String): Place? = places.firstOrNull { it.id == id }
    fun flag(id: String): CampaignFlag? = flags.firstOrNull { it.id == id }

    /**
     * Next scenario after [scenarioId] ended with [resolutionId], or null if the campaign ends.
     * Transitions that require a set flag win over unconditional ones.
     */
    fun nextScenario(scenarioId: String, resolutionId: String, setFlags: Set<String> = emptySet()): String? =
        nextScenarios(scenarioId, resolutionId, setFlags).firstOrNull()

    /**
     * Scenarios the group may play next. Transitions whose flag is set take precedence over those
     * without a flag; several remaining transitions are an either-or choice for the players.
     */
    fun nextScenarios(scenarioId: String, resolutionId: String, setFlags: Set<String> = emptySet()): List<String> {
        val candidates = transitions.filter { it.from == scenarioId && it.resolution == resolutionId }
        val flagged = candidates.filter { it.requiresFlag != null && it.requiresFlag in setFlags }
        return (flagged.ifEmpty { candidates.filter { it.requiresFlag == null } }).map { it.to }.distinct()
    }
}

@Serializable
data class ScenarioTransition(
    val from: String,
    val resolution: String,
    val to: String,
    /** Only taken if this campaign flag is set. */
    val requiresFlag: String? = null,
)

/** A location on the campaign map; [x]/[y] are relative (0–1, left/top to right/bottom). */
@Serializable
data class Place(val id: String, val name: String, val x: Double, val y: Double)

@Serializable
data class CampaignFlag(val id: String, val label: String)

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
    /** Place on the campaign map. */
    val place: String? = null,
    /** Events the app draws from at the end of every round (also harmless ones). */
    val shadowEvents: List<String> = emptyList(),
    /** Field where the heroes' figures start (on the start chapter's map). */
    val heroStart: Location? = null,
) {
    /** The heroes' start field; without an explicit one, the anchor field of the start chapter's first tile. */
    val effectiveHeroStart: Location?
        get() = heroStart ?: chapter(startChapter)?.let { ch ->
            ch.tiles.firstOrNull { !it.unexplored }?.let { TileLocation(it.id, HexCoord.ORIGIN) }
                ?: ch.battlefield?.boards?.firstOrNull()?.let { SquareLocation(GridCoord(0, 0)) }
        }

    val tokens: List<Token> get() = chapters.flatMap { it.tokens }
    val placements: List<TilePlacement> get() = chapters.flatMap { it.tiles }
    val boardPlacements: List<BoardPlacement> get() = chapters.flatMap { it.battlefield?.boards.orEmpty() }
    val terrainPlacements: List<TerrainPlacement> get() = chapters.flatMap { it.battlefield?.terrain.orEmpty() }

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

    fun boardPlacement(id: String) = boardPlacements.firstOrNull { it.id == id }
    fun terrainPlacement(id: String) = terrainPlacements.firstOrNull { it.id == id }

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
    /** Present for battle chapters: boards and terrain pieces instead of tiles. */
    val battlefield: Battlefield? = null,
    /** Battle chapter: earlier boards and terrain pieces are cleared away first, so they can be used again. */
    val clearBattlefield: Boolean = false,
) {
    val isBattle: Boolean get() = battlefield != null
}

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
    /** Laid out greyed with an exploration marker; its tokens appear when it is explored. */
    val unexplored: Boolean = false,
    /** Event run when the tile is explored. */
    val onExplore: String? = null,
) {
    /** Converts a tile-local cell into a map coordinate. */
    fun toMap(local: HexCoord): HexCoord = local.rotateClockwise(rotation) + position
}

/**
 * Where a token or enemy group stands: on a hex of a placed tile (travel map) or in a field of
 * the battle map. Distinguished in JSON by its keys, without a "type" property.
 */
@Serializable(with = LocationSerializer::class)
sealed interface Location

/** The placement ID if this is a [TileLocation]. */
val Location.placementId: String? get() = (this as? TileLocation)?.placement

/** A cell on a specific placed tile, in tile-local coordinates. */
@Serializable
data class TileLocation(
    val placement: String,
    val cell: HexCoord,
) : Location

internal object LocationSerializer : JsonContentPolymorphicSerializer<Location>(Location::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<Location> {
        val keys = element.jsonObject.keys
        return when {
            "square" in keys -> SquareLocation.serializer()
            "placement" in keys || "cell" in keys -> TileLocation.serializer()
            else -> throw SerializationException("Ort braucht entweder 'placement' und 'cell' oder 'square'.")
        }
    }
}

@Serializable
data class EnemyGroup(
    val id: String,
    /** Enemy type ID from the material catalog. */
    val enemyType: String,
    /** Number of enemies by hero count (keys 1–5). */
    val count: Map<Int, Int>,
    val location: Location,
    /** Elite group: a stronger variant with [eliteBonuses]; counterattacks even when exhausted (not when stunned). */
    val elite: Boolean = false,
    val eliteBonuses: List<EliteBonus> = emptyList(),
    /** Why this group is elite, in story words (e.g. "Der Anführer trägt einen alten Zauberring."). */
    val eliteReason: String? = null,
) {
    /** The catalog type with the elite bonuses applied. */
    fun effectiveType(type: EnemyType): EnemyType {
        if (!elite) return type
        val b = eliteBonuses.toSet()
        return type.copy(
            health = type.health + if (EliteBonus.TOUGH in b) 2 else 0,
            armor = type.armor + if (EliteBonus.ARMORED in b) 1 else 0,
            sorcery = type.sorcery + if (EliteBonus.WARDED in b) 1 else 0,
            movement = type.movement + if (EliteBonus.SWIFT in b) 1 else 0,
            attacks = type.attacks.map { a ->
                a.copy(damage = a.damage + if (EliteBonus.CRUEL in b) 1 else 0, fear = a.fear + if (EliteBonus.TERRIFYING in b) 1 else 0)
            },
        )
    }

    fun has(bonus: EliteBonus) = elite && bonus in eliteBonuses
}

/** Bonuses of an elite enemy group; the scenario explains them in [EnemyGroup.eliteReason]. */
@Serializable
enum class EliteBonus {
    /** +2 health per enemy. */
    @SerialName("tough") TOUGH,
    /** +1 armor. */
    @SerialName("armored") ARMORED,
    /** +1 sorcery (magic ward). */
    @SerialName("warded") WARDED,
    /** Attacks +1 damage. */
    @SerialName("cruel") CRUEL,
    /** Attacks +1 fear. */
    @SerialName("terrifying") TERRIFYING,
    /** +1 movement. */
    @SerialName("swift") SWIFT,
    /** Cannot be stunned. */
    @SerialName("steadfast") STEADFAST,
    /** Counterattacks +1 damage. */
    @SerialName("furious") FURIOUS,
}

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
    /** Lore the group gains with this ending. */
    val lore: Int = 0,
    /** Experience each hero gains with this ending. */
    val xp: Int = 0,
    /** Campaign flags set by this ending. */
    val setFlags: List<String> = emptyList(),
)
