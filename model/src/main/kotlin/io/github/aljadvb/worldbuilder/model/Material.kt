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
    /** Size of one hex of the physical tiles: centre to corner in millimetres. */
    val hexSizeMm: Double = DEFAULT_HEX_SIZE_MM,
    val expansions: List<Expansion> = emptyList(),
    val tiles: List<TileDefinition> = emptyList(),
    val enemyTypes: List<EnemyType> = emptyList(),
    val battleBoards: List<BattleBoard> = emptyList(),
    val terrainPieces: List<TerrainPiece> = emptyList(),
    /** Optional own map picture for the campaign map (private catalog only). */
    val campaignMap: CampaignMapImage? = null,
    /** Items heroes can find in adventures (for now trinkets). */
    val items: List<Item> = emptyList(),
) {
    fun item(id: String): Item? = items.firstOrNull { it.id == id }
    fun battleBoard(id: String): BattleBoard? = battleBoards.firstOrNull { it.id == id }
    fun terrainPiece(id: String): TerrainPiece? = terrainPieces.firstOrNull { it.id == id }
    fun tile(id: String): TileDefinition? = tiles.firstOrNull { it.id == id }
    fun enemyType(id: String): EnemyType? = enemyTypes.firstOrNull { it.id == id }
    fun expansion(id: String): Expansion? = expansions.firstOrNull { it.id == id }
}

/** Measured on the original tiles (scan at 150 dpi: 131 px centre to corner). */
const val DEFAULT_HEX_SIZE_MM = 22.2

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
    /** What the side shows, as IDs from [Terrain.TAGS] (e.g. "wald_dicht", "fluss"). */
    val terrain: List<String> = emptyList(),
    /** Short description of the landscape in own words. */
    val description: String = "",
    /** Cells with a printed darkness symbol. */
    val darkness: List<HexCoord> = emptyList(),
    /** Cells with a printed fortified symbol. */
    val fortified: List<HexCoord> = emptyList(),
    /** Cells with a printed rough-ground symbol. */
    val rough: List<HexCoord> = emptyList(),
    /** Underground cells: their outer border is black (closed) except at [passages]. */
    val walled: List<HexCoord> = emptyList(),
    /** Open edges (marked with arrows) on the outer border of walled cells. */
    val passages: List<HexEdge> = emptyList(),
) {
    /** Can heroes leave tile-local [cell] across its side [dir] (outer border)? */
    fun isOpen(cell: HexCoord, dir: HexDir): Boolean = cell !in walled || HexEdge(cell.q, cell.r, dir) in passages
}

/** One side of a hex cell; flat side up, so the sides are N, NE, SE, S, SW, NW. */
@Serializable
enum class HexDir(val offset: HexCoord) {
    @SerialName("N") N(HexCoord(0, -1)),
    @SerialName("NE") NE(HexCoord(1, -1)),
    @SerialName("SE") SE(HexCoord(1, 0)),
    @SerialName("S") S(HexCoord(0, 1)),
    @SerialName("SW") SW(HexCoord(-1, 1)),
    @SerialName("NW") NW(HexCoord(-1, 0));

    /** The side after turning a tile [steps] × 60° clockwise. */
    fun rotated(steps: Int): HexDir = entries[Math.floorMod(ordinal + steps, 6)]
    val opposite: HexDir get() = rotated(3)

    companion object {
        fun of(offset: HexCoord): HexDir? = entries.firstOrNull { it.offset == offset }
    }
}

@Serializable
data class HexEdge(val q: Int, val r: Int, val side: HexDir) {
    val cell: HexCoord get() = HexCoord(q, r)
}

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
    /** Health of one enemy of this type. */
    val health: Int = 1,
    /** Absorbs hits of each attack (ignored by "Durchbohren"). */
    val armor: Int = 0,
    /** Absorbs hits of each attack (ignored by "Zerschlagen"). */
    val sorcery: Int = 0,
    val movement: Int = 2,
    val ranged: Boolean = false,
    /** Attacks the app picks from in the shadow phase. */
    val attacks: List<EnemyAttack> = emptyList(),
)

@Serializable
data class EnemyAttack(
    val damage: Int = 0,
    val fear: Int = 0,
    /** Short description in own words. */
    val text: String = "",
    /** Attribute of the test that negates it: each success prevents 1 damage or 1 fear. Null = cannot be negated. */
    val negate: Attribute? = null,
)

/** An item card heroes can find; card text and picture only in the private catalog. */
@Serializable
data class Item(
    val id: String,
    val name: String,
    val expansion: String,
    val kind: ItemKind = ItemKind.TRINKET,
    /** Short effect in own words (for the editor and the AI). */
    val effect: String = "",
    /** The user's own transcription of the card (private catalog only). */
    val cardText: String = "",
    val image: ItemImage? = null,
    /** Upgrade line: cards with the same line (same picture) are levels of one item. */
    val line: String? = null,
    /** Level (roman numeral on the card), 1–3. */
    val level: Int = 1,
    /** Lore value on the card: total lore the group needs before this card can be upgraded. */
    val lore: Int? = null,
    /** Consumption markers placed when equipped (number in the circle). */
    val uses: Int? = null,
) {
    /** Cards of the next level of the same line (a line may branch, e.g. two kinds of rations). */
    fun upgrades(items: List<Item>): List<Item> = line?.let { l -> items.filter { it.line == l && it.level == level + 1 } }.orEmpty()
}

@Serializable
enum class ItemKind { @SerialName("trinket") TRINKET }

@Serializable
data class ItemImage(val file: String)

/** A map picture of Middle-earth; places use relative coordinates (0–1) on it. */
@Serializable
data class CampaignMapImage(val file: String)
