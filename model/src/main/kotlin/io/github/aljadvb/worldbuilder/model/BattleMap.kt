package io.github.aljadvb.worldbuilder.model

/**
 * The resolved geometry of a battle map: which battlefield square belongs to which field.
 * Built from board placements and the catalog; unknown boards or sides are skipped.
 */
class BattleMap(boards: List<BoardPlacement>, catalog: MaterialCatalog) {
    /** A printed field of a placed board, in battlefield squares. */
    data class Field(val index: Int, val boardPlacement: String, val squares: Set<GridCoord>) {
        /** Smallest square (top-left-most) – a stable representative. */
        val anchor: GridCoord get() = squares.minWith(compareBy<GridCoord> { it.y }.thenBy { it.x })
    }

    val fields: List<Field>
    private val fieldBySquare: Map<GridCoord, Field>

    /** Board placement ID → battlefield squares it covers. */
    val boardSquares: Map<String, Set<GridCoord>>

    init {
        val result = mutableListOf<Field>()
        val covered = linkedMapOf<String, Set<GridCoord>>()
        for (b in boards) {
            val side = catalog.battleBoard(b.board)?.sides?.get(b.side) ?: continue
            val origin = boardOrigin(b, side)
            covered[b.id] = (0 until side.height).flatMap { y -> (0 until side.width).map { x -> rotateSquare(GridCoord(x, y), side, b.rotation) + origin } }.toSet()
            side.fields().forEach { local ->
                result += Field(result.size, b.id, local.map { rotateSquare(it, side, b.rotation) + origin }.toSet())
            }
        }
        fields = result
        boardSquares = covered
        // Later fields win on overlap; overlaps are reported by the validator.
        fieldBySquare = result.flatMap { f -> f.squares.map { it to f } }.toMap()
    }

    val squares: Set<GridCoord> get() = fieldBySquare.keys

    fun field(square: GridCoord): Field? = fieldBySquare[square]

    /** Kind of an edge between two squares. */
    enum class EdgeKind { OFF_MAP, INSIDE_FIELD, RIM, BORDER }

    fun edgeKind(edge: GridEdge): EdgeKind {
        val (a, b) = edge.squares()
        val fa = field(a)
        val fb = field(b)
        return when {
            fa == null && fb == null -> EdgeKind.OFF_MAP
            fa == null || fb == null -> EdgeKind.RIM
            fa == fb -> EdgeKind.INSIDE_FIELD
            else -> EdgeKind.BORDER
        }
    }

    /** Fields adjacent to [field]: sharing a square side not listed in [blockedEdges] (normalized). */
    fun neighbors(field: Field, blockedEdges: Set<GridEdge> = emptySet()): Set<Field> =
        field.squares.flatMap { sq ->
            EdgeSide.entries.mapNotNull { side ->
                val other = field(sq.neighbor(side)) ?: return@mapNotNull null
                if (other == field || GridEdge(sq.x, sq.y, side).normalized() in blockedEdges) null else other
            }
        }.toSet()

    /** Extent in squares (width, height) of the whole map, or (0,0) if empty. */
    fun extent(): Pair<Int, Int> {
        if (squares.isEmpty()) return 0 to 0
        return (squares.maxOf { it.x } - squares.minOf { it.x } + 1) to (squares.maxOf { it.y } - squares.minOf { it.y } + 1)
    }

    companion object {
        /** Width and height of a board side after [rotation] quarter turns. */
        fun rotatedSize(side: BoardSide, rotation: Int): Pair<Int, Int> =
            if (Math.floorMod(rotation, 2) == 0) side.width to side.height else side.height to side.width

        /** Battlefield square of the board's top-left corner: position counts whole boards. */
        fun boardOrigin(b: BoardPlacement, side: BoardSide): GridCoord {
            val (w, h) = rotatedSize(side, b.rotation)
            return GridCoord(b.position.x * w, b.position.y * h)
        }

        /** Board-local square after turning the board [rotation] × 90° clockwise. */
        fun rotateSquare(sq: GridCoord, side: BoardSide, rotation: Int): GridCoord {
            var p = sq
            var w = side.width
            var h = side.height
            repeat(Math.floorMod(rotation, 4)) {
                p = GridCoord(h - 1 - p.y, p.x)
                val t = w; w = h; h = t
            }
            return p
        }
    }
}

/** The shared battle map of all battle chapters of this scenario (or of [chapterIds] only). */
fun Scenario.battleMap(catalog: MaterialCatalog, chapterIds: Set<String>? = null): BattleMap =
    BattleMap(chapters.filter { chapterIds == null || it.id in chapterIds }.flatMap { it.battlefield?.boards.orEmpty() }, catalog)

/**
 * Battle chapters grouped into layouts on the table: a chapter with [Chapter.clearBattlefield]
 * starts a new layout (earlier boards and pieces are cleared away). In chapter order.
 */
fun Scenario.battleSegments(): List<List<Chapter>> {
    val result = mutableListOf<MutableList<Chapter>>()
    for (ch in chapters.filter { it.isBattle }) {
        if (result.isEmpty() || ch.clearBattlefield) result += mutableListOf<Chapter>()
        result.last() += ch
    }
    return result
}

/** The battle chapters lying on the table together with [chapterId] (up to and including it if [upTo]). */
fun Scenario.battleSegmentOf(chapterId: String, upTo: Boolean = false): List<Chapter> {
    val seg = battleSegments().firstOrNull { s -> s.any { it.id == chapterId } } ?: return emptyList()
    return if (upTo) seg.take(seg.indexOfFirst { it.id == chapterId } + 1) else seg
}
