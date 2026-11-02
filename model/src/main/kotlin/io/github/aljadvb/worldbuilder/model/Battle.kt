package io.github.aljadvb.worldbuilder.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---- Material catalog: battle boards and terrain pieces ---------------------------------

/** A square on a battle board grid (board-local or battlefield coordinates); y grows downwards. */
@Serializable
data class GridCoord(val x: Int, val y: Int) {
    operator fun plus(other: GridCoord) = GridCoord(x + other.x, y + other.y)
    operator fun minus(other: GridCoord) = GridCoord(x - other.x, y - other.y)

    fun neighbor(side: EdgeSide) = this + side.offset

    override fun toString() = "($x,$y)"
}

/** A physical battle board; like tiles it has two printed sides. */
@Serializable
data class BattleBoard(
    val id: String,
    /** Name shown to the players, e.g. "S1". */
    val label: String,
    val expansion: String,
    /** Number of identical copies owned. */
    val count: Int = 1,
    val sides: Map<TileSide, BoardSide>,
)

@Serializable
data class BoardSide(
    /** Size in grid squares. */
    val width: Int,
    val height: Int,
    /** Edge length of one grid square in millimetres. */
    val cellSizeMm: Double = DEFAULT_BOARD_CELL_MM,
    /**
     * The printed fields ("Felder"): each is a group of board-local squares. Empty means every
     * square is a field of its own. Pieces and figures stand in a field; edge pieces lie on the
     * border between two fields.
     */
    val areas: List<List<GridCoord>> = emptyList(),
    val image: BoardImage? = null,
    /** What the side shows, as IDs from [Terrain.TAGS]. */
    val terrain: List<String> = emptyList(),
    /** Short description in own words. */
    val description: String = "",
) {
    /** The fields of this side; every square on its own if [areas] is empty. */
    fun fields(): List<List<GridCoord>> =
        areas.ifEmpty { (0 until height).flatMap { y -> (0 until width).map { x -> listOf(GridCoord(x, y)) } } }
}

/** Measured on the original board: 28.5 cm, split into 3 × 3 squares. */
const val DEFAULT_BOARD_CELL_MM = 95.0

/**
 * Picture of a board side, unrotated. [originX]/[originY] is the pixel position of the top-left
 * corner of square (0,0); [cellSize] the edge length of one square in pixels.
 */
@Serializable
data class BoardImage(
    val file: String,
    val originX: Double = 0.0,
    val originY: Double = 0.0,
    val cellSize: Double,
)

/** A two-sided cardboard terrain piece; [count] is the number of physical pieces (both sides together). */
@Serializable
data class TerrainPiece(
    val id: String,
    val expansion: String,
    val count: Int = 1,
    val sides: Map<TileSide, TerrainPieceSide>,
)

@Serializable
data class TerrainPieceSide(
    val name: String,
    val placement: PiecePlacement,
    /** Picture of the piece (e.g. the user's own scan); drawn centred in the field or along the edge. */
    val image: PieceImage? = null,
    /** IDs from [Terrain.TAGS]. */
    val terrain: List<String> = emptyList(),
    /** Short summary of the effect in own words (used by the editor and the AI generator). */
    val effect: String = "",
    /** The rules text of the user's own terrain card. Only kept in the private catalog, never in the repo. */
    val cardText: String = "",
    /** Edge pieces: figures cannot cross. Field pieces: figures cannot enter. Used for reachability hints only. */
    val blocksMovement: Boolean = false,
)

@Serializable
enum class PiecePlacement {
    /** Lies inside a field (e.g. bushes, a statue). */
    @SerialName("area") AREA,
    /** Lies on the border between two fields (e.g. river, wall). */
    @SerialName("edge") EDGE,
}

@Serializable
data class PieceImage(
    val file: String,
    /** Real width of the piece (the picture's horizontal side) in millimetres; edge pictures run along the edge. */
    val widthMm: Double? = null,
)

// ---- Campaign: battlefield of a chapter --------------------------------------------------

/**
 * The battle map a chapter adds. All battle chapters of a scenario share one grid of squares,
 * just like travel chapters share the hex map.
 */
@Serializable
data class Battlefield(
    val boards: List<BoardPlacement> = emptyList(),
    val terrain: List<TerrainPlacement> = emptyList(),
)

@Serializable
data class BoardPlacement(
    val id: String,
    /** Board ID from the material catalog. */
    val board: String,
    val side: TileSide,
    /** Position in whole boards: (1,0) lies right next to (0,0). */
    val position: GridCoord,
    /** 0–3, steps of 90° clockwise. */
    val rotation: Int = 0,
)

@Serializable
data class TerrainPlacement(
    val id: String,
    /** Terrain piece ID from the material catalog. */
    val piece: String,
    val side: TileSide,
    /** For field pieces: any battlefield square of the field. */
    val area: GridCoord? = null,
    /** For edge pieces: one side of a battlefield square. */
    val edge: GridEdge? = null,
    /** Name of the piece in this scenario, e.g. "Altar des Nekromanten". */
    val customName: String? = null,
    /** Scenario-specific rule shown when the piece is tapped. */
    val customRule: CustomRule? = null,
)

@Serializable
data class GridEdge(val x: Int, val y: Int, val side: EdgeSide) {
    val square get() = GridCoord(x, y)

    /** The same edge described from the square with the lower coordinate (north/west form). */
    fun normalized(): GridEdge = when (side) {
        EdgeSide.SOUTH -> GridEdge(x, y + 1, EdgeSide.NORTH)
        EdgeSide.EAST -> GridEdge(x + 1, y, EdgeSide.WEST)
        else -> this
    }

    /** The two squares on either side of the edge. */
    fun squares(): Pair<GridCoord, GridCoord> = square to square.neighbor(side)
}

@Serializable
enum class EdgeSide(val offset: GridCoord) {
    @SerialName("north") NORTH(GridCoord(0, -1)),
    @SerialName("east") EAST(GridCoord(1, 0)),
    @SerialName("south") SOUTH(GridCoord(0, 1)),
    @SerialName("west") WEST(GridCoord(-1, 0)),
}

@Serializable
data class CustomRule(
    val mode: RuleMode = RuleMode.REPLACE,
    val text: String,
)

@Serializable
enum class RuleMode {
    /** Replaces the terrain card (as in the official app). */
    @SerialName("replace") REPLACE,
    /** Applies in addition to the terrain card. */
    @SerialName("add") ADD,
}

/** A figure's position on a battle map: the field containing [square]. */
@Serializable
data class SquareLocation(val square: GridCoord) : Location {
    override fun toString() = "Feld $square"
}
