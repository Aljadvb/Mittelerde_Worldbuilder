package io.github.aljadvb.worldbuilder.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The user-maintained catalog of physical game material (tiles, enemy types, expansions).
 * It is deliberately kept outside the code so no game content is hard-wired.
 */
@Serializable
data class MaterialCatalog(
    val formatVersion: Int,
    val hexOrientation: HexOrientation = HexOrientation.POINTY,
    val expansions: List<Expansion> = emptyList(),
    val tiles: List<TileDefinition> = emptyList(),
    val enemyTypes: List<EnemyType> = emptyList(),
) {
    fun tile(id: String): TileDefinition? = tiles.firstOrNull { it.id == id }
    fun enemyType(id: String): EnemyType? = enemyTypes.firstOrNull { it.id == id }
    fun expansion(id: String): Expansion? = expansions.firstOrNull { it.id == id }
}

@Serializable
enum class HexOrientation {
    @SerialName("pointy") POINTY,
    @SerialName("flat") FLAT,
}

@Serializable
data class Expansion(
    val id: String,
    val name: String,
    val owned: Boolean = true,
)

/** A physical tile; each side has its own shape because flipping a tile mirrors it. */
@Serializable
data class TileDefinition(
    val id: String,
    /** The number printed on the tile, shown to the players. */
    val label: String,
    val expansion: String,
    val sides: Map<TileSide, TileSideShape>,
)

@Serializable
enum class TileSide { A, B }

@Serializable
data class TileSideShape(
    /** Cells in tile-local axial coordinates; (0,0) is the tile's anchor. */
    val cells: List<HexCoord>,
)

@Serializable
data class EnemyType(
    val id: String,
    val name: String,
    val expansion: String,
)
