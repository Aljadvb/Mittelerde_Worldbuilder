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
    val hexOrientation: HexOrientation = HexOrientation.FLAT,
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
    /** Flat side up – the orientation of the original game tiles. */
    @SerialName("flat") FLAT,
    @SerialName("pointy") POINTY,
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
    /** Optional picture of this side (e.g. the user's own scan), drawn clipped to the cells. */
    val image: TileImage? = null,
)

/**
 * A picture of a tile side and how it lies on the hex grid. The picture shows the side
 * unrotated (rotation 0); [anchorX]/[anchorY] is the pixel centre of cell (0,0) and
 * [hexSize] the hex radius (centre to corner) in pixels.
 */
@Serializable
data class TileImage(
    /** Path relative to the catalog file, e.g. "bilder/101A.jpg". */
    val file: String,
    val anchorX: Double,
    val anchorY: Double,
    val hexSize: Double,
)

@Serializable
data class EnemyType(
    val id: String,
    val name: String,
    val expansion: String,
)
