package io.github.aljadvb.worldbuilder.validation

import io.github.aljadvb.worldbuilder.model.BattleMap
import io.github.aljadvb.worldbuilder.model.GridCoord
import io.github.aljadvb.worldbuilder.model.GridEdge
import io.github.aljadvb.worldbuilder.model.MaterialCatalog
import io.github.aljadvb.worldbuilder.model.PiecePlacement
import io.github.aljadvb.worldbuilder.model.Scenario
import io.github.aljadvb.worldbuilder.model.SquareLocation
import io.github.aljadvb.worldbuilder.model.battleSegments

/** Checks the battle chapters of one scenario: boards, terrain pieces and their counts. */
internal class BattleChecks(
    private val s: Scenario,
    private val sp: String,
    private val catalog: MaterialCatalog,
    private val c: IssueCollector,
    private val checkExpansionOwned: (expansion: String, path: String, ownerId: String, what: String) -> Unit,
) {
    fun run() {
        if (s.chapters.none { it.isBattle }) return
        s.chapters.filter { it.tiles.isNotEmpty() && it.isBattle }.forEach { ch ->
            c.error(IssueCode.MIXED_MAP, "${chapterPath(s.chapters.indexOf(ch))}.tiles", ch.id, "Kapitel '${ch.id}' hat Kacheln und einen Schlachtplan – ein Kapitel ist entweder Reise oder Schlacht.")
        }
        // Rebuilding the whole battlefield in the middle of a scenario is discouraged.
        s.chapters.forEachIndexed { ci, ch ->
            if (ch.isBattle && ch.clearBattlefield) c.warn(IssueCode.CLEAR_BATTLEFIELD, "${chapterPath(ci)}.clearBattlefield", ch.id,
                "Kapitel '${ch.id}' räumt das Schlachtfeld ab und baut alles neu auf. Besser die liegende Karte erweitern oder Gegner und Marker auf ihr erscheinen lassen.")
        }
        // Each layout on the table is checked on its own: cleared boards and pieces may be used again.
        for (segment in s.battleSegments()) {
            val ids = segment.map { it.id }.toSet()
            checkBoards(ids)
            val map = BattleMap(segment.flatMap { it.battlefield!!.boards }, catalog)
            checkTerrain(map, ids)
            checkReachability(map, ids)
            if (segment.sumOf { it.battlefield!!.boards.size } == 1 && (catalog.battleBoard(segment.first().battlefield!!.boards.first().board)?.count ?: 1) > 1) {
                val ci = s.chapters.indexOf(segment.first())
                c.warn(IssueCode.SINGLE_BOARD, "${chapterPath(ci)}.battlefield.boards", segment.first().id, "Nur ein Schlachtplan liegt aus – meist braucht eine Schlacht zwei Pläne nebeneinander, damit es genug Felder gibt.")
            }
        }
    }

    private fun chapterPath(ci: Int) = "$sp.chapters[$ci]"

    private fun checkBoards(seg: Set<String>) {
        val covered = mutableMapOf<GridCoord, String>()
        val used = mutableMapOf<String, Int>()
        var size: Pair<Int, Int>? = null
        s.chapters.forEachIndexed { ci, ch ->
            val bf = ch.battlefield ?: return@forEachIndexed
            if (ch.id !in seg) return@forEachIndexed
            val cp = chapterPath(ci)
            bf.boards.forEachIndexed { bi, b ->
                val bp = "$cp.battlefield.boards[$bi]"
                if (b.rotation !in 0..3) c.error(IssueCode.INVALID_ROTATION, "$bp.rotation", b.id, "Drehung ${b.rotation} ist ungültig (erlaubt: 0–3).")
                val board = catalog.battleBoard(b.board)
                if (board == null) {
                    c.error(IssueCode.UNKNOWN_BOARD, "$bp.board", b.id, "Schlachtplan '${b.board}' ist nicht im Material-Katalog.")
                    return@forEachIndexed
                }
                checkExpansionOwned(board.expansion, "$bp.board", b.id, "Schlachtplan ${board.label}")
                val side = board.sides[b.side]
                if (side == null) {
                    c.error(IssueCode.UNKNOWN_BOARD_SIDE, "$bp.side", b.id, "Schlachtplan ${board.label} hat im Katalog keine Seite ${b.side}.")
                    return@forEachIndexed
                }
                val n = used.merge(board.id, 1, Int::plus)!!
                if (n == board.count + 1) {
                    c.error(IssueCode.BOARD_COUNT_EXCEEDED, "$bp.board", b.id, "Schlachtplan ${board.label} ist nur ${board.count}× vorhanden, wird aber öfter verwendet.")
                }
                val rotated = BattleMap.rotatedSize(side, b.rotation)
                if (size == null) size = rotated
                else if (size != rotated) {
                    c.error(IssueCode.BOARD_SIZE_MISMATCH, bp, b.id, "Schlachtpläne unterschiedlicher Größe (oder Ausrichtung) können nicht bündig aneinandergelegt werden.")
                }
                val origin = BattleMap.boardOrigin(b, side)
                val other = (0 until side.height).flatMap { y -> (0 until side.width).map { x -> BattleMap.rotateSquare(GridCoord(x, y), side, b.rotation) + origin } }
                    .firstNotNullOfOrNull { sq -> covered.putIfAbsent(sq, b.id) }
                if (other != null) c.error(IssueCode.BOARDS_OVERLAP, bp, b.id, "Schlachtplan '${b.id}' liegt auf '$other'.")
            }
        }
    }

    private fun checkTerrain(map: BattleMap, seg: Set<String>) {
        val used = mutableMapOf<String, Int>()
        val edges = mutableMapOf<GridEdge, String>()
        s.chapters.forEachIndexed { ci, ch ->
            val bf = ch.battlefield ?: return@forEachIndexed
            if (ch.id !in seg) return@forEachIndexed
            bf.terrain.forEachIndexed { gi, t ->
                val tp = "${chapterPath(ci)}.battlefield.terrain[$gi]"
                t.customName?.let { if (it.isBlank()) c.error(IssueCode.BLANK_TEXT, "$tp.customName", t.id, "Eigener Name von '${t.id}' ist leer.") }
                t.customRule?.let { if (it.text.isBlank()) c.error(IssueCode.BLANK_TEXT, "$tp.customRule.text", t.id, "Eigene Regel von '${t.id}' ist leer.") }
                val piece = catalog.terrainPiece(t.piece)
                if (piece == null) {
                    c.error(IssueCode.UNKNOWN_TERRAIN_PIECE, "$tp.piece", t.id, "Geländeteil '${t.piece}' ist nicht im Material-Katalog.")
                    return@forEachIndexed
                }
                checkExpansionOwned(piece.expansion, "$tp.piece", t.id, "Geländeteil '${t.piece}'")
                val side = piece.sides[t.side]
                if (side == null) {
                    c.error(IssueCode.UNKNOWN_PIECE_SIDE, "$tp.side", t.id, "Geländeteil '${piece.id}' hat im Katalog keine Seite ${t.side}.")
                    return@forEachIndexed
                }
                val n = used.merge(piece.id, 1, Int::plus)!!
                if (n == piece.count + 1) {
                    val names = piece.sides.values.joinToString("/") { it.name }
                    c.error(IssueCode.PIECE_COUNT_EXCEEDED, "$tp.piece", t.id, "Vom Geländeteil $names gibt es nur ${piece.count} Stück (beide Seiten zusammen), es werden aber mehr verwendet.")
                }
                when (side.placement) {
                    PiecePlacement.AREA -> {
                        val area = t.area
                        if (area == null || t.edge != null) {
                            c.error(IssueCode.PIECE_PLACEMENT_KIND, tp, t.id, "${side.name} liegt in einem Feld: bitte 'area' angeben (und keine 'edge').")
                            return@forEachIndexed
                        }
                        if (map.field(area) == null) c.error(IssueCode.SQUARE_NOT_ON_MAP, "$tp.area", t.id, "${side.name} ('${t.id}'): Feld $area liegt auf keinem Schlachtplan.")
                    }
                    PiecePlacement.EDGE -> {
                        val edge = t.edge
                        if (edge == null || t.area != null) {
                            c.error(IssueCode.PIECE_PLACEMENT_KIND, tp, t.id, "${side.name} liegt auf einer Feldgrenze: bitte 'edge' angeben (und kein 'area').")
                            return@forEachIndexed
                        }
                        when (map.edgeKind(edge)) {
                            BattleMap.EdgeKind.OFF_MAP -> c.error(IssueCode.SQUARE_NOT_ON_MAP, "$tp.edge", t.id, "${side.name} ('${t.id}'): Die Kante liegt auf keinem Schlachtplan.")
                            BattleMap.EdgeKind.INSIDE_FIELD -> c.error(IssueCode.EDGE_NOT_BORDER, "$tp.edge", t.id, "${side.name} ('${t.id}'): Die Kante liegt innerhalb eines Feldes, nicht auf einer Feldgrenze.")
                            BattleMap.EdgeKind.RIM -> c.warn(IssueCode.EDGE_ON_RIM, "$tp.edge", t.id, "${side.name} ('${t.id}') liegt am Außenrand und hat dort keine Wirkung.")
                            BattleMap.EdgeKind.BORDER -> Unit
                        }
                        edges.put(edge.normalized(), t.id)?.let { other ->
                            c.error(IssueCode.EDGE_OCCUPIED, "$tp.edge", t.id, "Auf dieser Feldgrenze liegt bereits '$other'.")
                        }
                    }
                }
            }
        }
    }

    /** Hints only: figures in blocked fields, and fields cut off by walls or barricades. */
    private fun checkReachability(map: BattleMap, seg: Set<String>) {
        val blockedEdges = mutableSetOf<GridEdge>()
        val blockedFields = mutableSetOf<BattleMap.Field>()
        for (t in s.chapters.filter { it.id in seg }.flatMap { it.battlefield?.terrain.orEmpty() }) {
            val side = catalog.terrainPiece(t.piece)?.sides?.get(t.side) ?: continue
            if (!side.blocksMovement) continue
            t.edge?.let { blockedEdges += it.normalized() }
            t.area?.let { a -> map.field(a)?.let { blockedFields += it } }
        }
        val figures = s.chapters.flatMapIndexed { ci, ch ->
            if (ch.id !in seg) emptyList() else ch.tokens.mapIndexedNotNull { ti, tok -> (tok.location as? SquareLocation)?.let { Triple(tok.id, "${chapterPath(ci)}.tokens[$ti].location", it) } }
        } + s.enemyGroups.mapIndexedNotNull { i, g -> (g.location as? SquareLocation)?.let { Triple(g.id, "$sp.enemyGroups[$i].location", it) } }
        val placed = figures.mapNotNull { (id, path, loc) -> map.field(loc.square)?.let { Triple(id, path, it) } }
        placed.filter { it.third in blockedFields }.forEach { (id, path, _) ->
            c.warn(IssueCode.FIELD_BLOCKED, path, id, "'$id' steht auf einem Feld, das nicht betreten werden kann (z. B. Barrikade).")
        }
        // Fields cut off from the largest connected part of the map are suspicious.
        val open = map.fields.filter { it !in blockedFields }
        val component = mutableMapOf<BattleMap.Field, Int>()
        for (f in open) {
            if (f in component) continue
            val id = f.index
            component[f] = id
            val queue = ArrayDeque(listOf(f))
            while (queue.isNotEmpty()) {
                map.neighbors(queue.removeFirst(), blockedEdges).filter { it !in blockedFields && it !in component }.forEach { component[it] = id; queue += it }
            }
        }
        val main = component.values.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: return
        placed.filter { it.third !in blockedFields && component[it.third] != main }.forEach { (id, path, _) ->
            c.warn(IssueCode.FIELD_UNREACHABLE, path, id, "'$id' steht in einem abgeschnittenen Bereich (Mauern oder Barrikaden versperren den Weg).")
        }
    }
}
