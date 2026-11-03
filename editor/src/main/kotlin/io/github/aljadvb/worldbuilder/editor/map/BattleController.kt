package io.github.aljadvb.worldbuilder.editor.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import io.github.aljadvb.worldbuilder.editing.Created
import io.github.aljadvb.worldbuilder.editing.addBoard
import io.github.aljadvb.worldbuilder.editing.addEnemyGroup
import io.github.aljadvb.worldbuilder.editing.addTerrain
import io.github.aljadvb.worldbuilder.editing.addToken
import io.github.aljadvb.worldbuilder.editing.boardPositionAt
import io.github.aljadvb.worldbuilder.editing.delete
import io.github.aljadvb.worldbuilder.editing.flipTerrain
import io.github.aljadvb.worldbuilder.editing.freeBoards
import io.github.aljadvb.worldbuilder.editing.freePieces
import io.github.aljadvb.worldbuilder.editing.isEdgePiece
import io.github.aljadvb.worldbuilder.editing.moveEnemyGroup
import io.github.aljadvb.worldbuilder.editing.moveToken
import io.github.aljadvb.worldbuilder.editing.scenarioOf
import io.github.aljadvb.worldbuilder.editing.updateBoard
import io.github.aljadvb.worldbuilder.editing.updateTerrain
import io.github.aljadvb.worldbuilder.editor.EditorModel
import io.github.aljadvb.worldbuilder.editor.Texts
import io.github.aljadvb.worldbuilder.hexmap.BattleProjection
import io.github.aljadvb.worldbuilder.model.BattleMap
import io.github.aljadvb.worldbuilder.model.Campaign
import io.github.aljadvb.worldbuilder.model.Chapter
import io.github.aljadvb.worldbuilder.model.GridCoord
import io.github.aljadvb.worldbuilder.model.GridEdge
import io.github.aljadvb.worldbuilder.model.Scenario
import io.github.aljadvb.worldbuilder.model.SquareLocation
import io.github.aljadvb.worldbuilder.model.TileSide
import io.github.aljadvb.worldbuilder.model.TokenType
import io.github.aljadvb.worldbuilder.model.battleSegmentOf
import io.github.aljadvb.worldbuilder.validation.ObjectKind

/** Active tool of the battle map editor. */
sealed interface BattleTool {
    data object Select : BattleTool
    data class PlaceBoard(val board: String, val side: TileSide, val rotation: Int = 0) : BattleTool
    data class PlacePiece(val piece: String, val side: TileSide) : BattleTool
    data class PlaceToken(val type: TokenType) : BattleTool
    data object PlaceEnemy : BattleTool
    data object HeroStart : BattleTool
}

/** What is being dragged on the battle map. */
sealed interface BattleDrag {
    data class Board(val id: String, val position: GridCoord) : BattleDrag
    data class Piece(val id: String, val square: GridCoord, val edge: GridEdge) : BattleDrag
    data class Token(val id: String, val square: GridCoord) : BattleDrag
    data class Enemy(val id: String, val square: GridCoord) : BattleDrag
    data object Pan : BattleDrag
}

/** Zoom and pan of the battle map: square size in px and pixel origin of square (0,0). */
class BattleCamera {
    var cell by mutableStateOf(120f)
    var origin by mutableStateOf(Offset(40f, 40f))
    var fitted by mutableStateOf(false)
    fun projection() = BattleProjection(cell, origin)
}

/** Battle map editing in grid coordinates; pixel handling stays in the composable. */
class BattleController(private val model: EditorModel) {
    val camera = BattleCamera()
    var tool: BattleTool by mutableStateOf(BattleTool.Select)
    var hoverSquare: GridCoord? by mutableStateOf(null)
    var hoverEdge: GridEdge? by mutableStateOf(null)
    var drag: BattleDrag? by mutableStateOf(null)
        private set

    val scenario: Scenario? get() = model.selection?.let { sel -> model.campaign?.let { it.scenario(sel) ?: it.scenarioOf(sel) } }

    /** The battle chapter being edited, derived from the selection. */
    val chapter: Chapter?
        get() {
            val s = scenario ?: return null
            val sel = model.selection ?: return null
            s.chapter(sel)?.let { return it }
            s.chapters.firstOrNull { ch -> ch.battlefield?.boards.orEmpty().any { it.id == sel } || ch.battlefield?.terrain.orEmpty().any { it.id == sel } }?.let { return it }
            s.chapterOfToken(sel)?.let { return it }
            val g = s.enemyGroup(sel) ?: return null
            val square = (g.location as? SquareLocation)?.square ?: return null
            val board = s.battleMap(model.catalog).field(square)?.boardPlacement ?: return s.chapters.firstOrNull { it.isBattle }
            return s.chapters.firstOrNull { ch -> ch.battlefield?.boards.orEmpty().any { it.id == board } }
        }

    /** Battle chapters drawn: all battle chapters up to the current one. */
    val visibleChapters: List<Chapter>
        get() {
            val s = scenario ?: return emptyList()
            val idx = s.chapters.indexOf(chapter ?: return emptyList())
            return s.battleSegmentOf(s.chapters[idx].id, upTo = true)
        }

    fun map(): BattleMap = BattleMap(visibleChapters.flatMap { it.battlefield!!.boards }, model.catalog)

    private fun Scenario.battleMap(catalog: io.github.aljadvb.worldbuilder.model.MaterialCatalog) = BattleMap(boardPlacements, catalog)

    // ---- pointer ---------------------------------------------------------------------

    /** Primary press at pixel [p]. Returns true if consumed (no panning). */
    fun press(p: Offset, proj: BattleProjection, scene: io.github.aljadvb.worldbuilder.hexmap.BattleScene): Boolean {
        val s = scenario ?: return false
        val ch = chapter ?: return false
        val square = proj.squareAt(p)
        when (val t = tool) {
            is BattleTool.PlaceBoard -> {
                if ((s.freeBoards(model.catalog, ch.id)[t.board] ?: 0) <= 0) return true.also { model.message = Texts.BATTLE_NO_BOARD_LEFT }
                val pos = boardPositionAt(square, model.catalog, t.board, t.side, t.rotation) ?: return true
                if (boardOverlaps(t.board, t.side, t.rotation, pos, ignore = null)) return true.also { model.message = Texts.MAP_OVERLAP }
                create { it.addBoard(ch.id, t.board, t.side, pos, t.rotation) }
                tool = BattleTool.Select
                return true
            }
            is BattleTool.PlacePiece -> {
                if ((s.freePieces(model.catalog, ch.id)[t.piece] ?: 0) <= 0) return true.also { model.message = Texts.BATTLE_NO_PIECE_LEFT }
                val edgePiece = model.catalog.isEdgePiece(t.piece, t.side)
                val map = map()
                if (edgePiece) {
                    val edge = proj.edgeAt(p)
                    if (map.edgeKind(edge) != BattleMap.EdgeKind.BORDER) return true.also { model.message = Texts.BATTLE_NEED_BORDER }
                    create { it.addTerrain(ch.id, t.piece, t.side, edge = edge) }
                } else {
                    if (map.field(square) == null) return true.also { model.message = Texts.BATTLE_NEED_BOARD }
                    create { it.addTerrain(ch.id, t.piece, t.side, area = square) }
                }
                return true // stay in the tool to place several pieces
            }
            is BattleTool.PlaceToken -> {
                if (map().field(square) == null) return true.also { model.message = Texts.BATTLE_NEED_BOARD }
                create { it.addToken(ch.id, Texts.NEW_TOKEN, t.type, SquareLocation(square)) }
                tool = BattleTool.Select
                return true
            }
            BattleTool.PlaceEnemy -> {
                if (map().field(square) == null) return true.also { model.message = Texts.BATTLE_NEED_BOARD }
                val type = model.catalog.enemyTypes.firstOrNull()?.id.orEmpty()
                create { it.addEnemyGroup(s.id, Texts.NEW_ENEMY_GROUP, type, SquareLocation(square)) }
                tool = BattleTool.Select
                return true
            }
            BattleTool.HeroStart -> {
                if (map().field(square) == null) return true.also { model.message = Texts.BATTLE_NEED_BOARD }
                model.edit { c -> c.copy(scenarios = c.scenarios.map { if (it.id == s.id) it.copy(heroStart = SquareLocation(square)) else it }) }
                tool = BattleTool.Select
                return true
            }
            BattleTool.Select -> {
                io.github.aljadvb.worldbuilder.hexmap.squareTokenAt(proj, scene.tokens, p)?.let {
                    model.selection = it.id; drag = BattleDrag.Token(it.id, square); return true
                }
                scene.enemies.firstOrNull { (io.github.aljadvb.worldbuilder.hexmap.squareEnemyCenter(proj, it.square) - p).getDistance() < proj.cell * 0.15f }?.let {
                    model.selection = it.id; drag = BattleDrag.Enemy(it.id, square); return true
                }
                io.github.aljadvb.worldbuilder.hexmap.pieceAt(proj, scene, p)?.let {
                    model.selection = it.id; drag = BattleDrag.Piece(it.id, square, proj.edgeAt(p)); return true
                }
                val board = ch.battlefield?.boards?.firstOrNull { b -> square in map().boardSquares[b.id].orEmpty() }
                if (board != null) {
                    model.selection = board.id
                    drag = BattleDrag.Board(board.id, board.position)
                    return true
                }
                drag = BattleDrag.Pan
                return false
            }
        }
    }

    fun move(p: Offset, proj: BattleProjection) {
        val square = proj.squareAt(p)
        hoverSquare = square
        hoverEdge = proj.edgeAt(p)
        drag = when (val d = drag) {
            is BattleDrag.Board -> {
                val b = scenario?.boardPlacement(d.id)
                val pos = b?.let { boardPositionAt(square, model.catalog, it.board, it.side, it.rotation) } ?: d.position
                d.copy(position = pos)
            }
            is BattleDrag.Piece -> d.copy(square = square, edge = proj.edgeAt(p))
            is BattleDrag.Token -> d.copy(square = square)
            is BattleDrag.Enemy -> d.copy(square = square)
            else -> d
        }
    }

    fun release() {
        val d = drag
        drag = null
        val s = scenario ?: return
        when (d) {
            is BattleDrag.Board -> {
                val b = s.boardPlacement(d.id) ?: return
                if (b.position == d.position) return
                if (boardOverlaps(b.board, b.side, b.rotation, d.position, ignore = b.id)) return run { model.message = Texts.MAP_OVERLAP }
                model.edit { it.updateBoard(b.id) { x -> x.copy(position = d.position) } }
            }
            is BattleDrag.Piece -> {
                val t = s.terrainPlacement(d.id) ?: return
                val map = map()
                if (t.edge != null) {
                    if (d.edge.normalized() == t.edge!!.normalized()) return
                    if (map.edgeKind(d.edge) != BattleMap.EdgeKind.BORDER) return run { model.message = Texts.BATTLE_NEED_BORDER }
                    model.edit { it.updateTerrain(t.id) { x -> x.copy(edge = d.edge) } }
                } else {
                    if (d.square == t.area) return
                    if (map.field(d.square) == null) return run { model.message = Texts.BATTLE_NEED_BOARD }
                    model.edit { it.updateTerrain(t.id) { x -> x.copy(area = d.square) } }
                }
            }
            is BattleDrag.Token -> {
                val t = s.token(d.id) ?: return
                if (map().field(d.square) == null || t.location == SquareLocation(d.square)) return
                model.edit { it.moveToken(t.id, SquareLocation(d.square)) }
            }
            is BattleDrag.Enemy -> {
                val g = s.enemyGroup(d.id) ?: return
                if (map().field(d.square) == null || g.location == SquareLocation(d.square)) return
                model.edit { it.moveEnemyGroup(g.id, SquareLocation(d.square)) }
            }
            BattleDrag.Pan, null -> Unit
        }
    }

    private fun boardOverlaps(board: String, side: TileSide, rotation: Int, position: GridCoord, ignore: String?): Boolean {
        val s = scenario ?: return false
        val others = BattleMap(s.boardPlacements.filter { it.id != ignore }, model.catalog).squares
        val candidate = BattleMap(listOf(io.github.aljadvb.worldbuilder.model.BoardPlacement("bp__ghost", board, side, position, rotation)), model.catalog).squares
        return candidate.any { it in others }
    }

    // ---- keyboard --------------------------------------------------------------------

    /** R: turn the new or selected board by 90°. */
    fun rotate() {
        val t = tool
        if (t is BattleTool.PlaceBoard) { tool = t.copy(rotation = (t.rotation + 1) % 4); return }
        val b = model.selection?.let { scenario?.boardPlacement(it) } ?: return
        val r = (b.rotation + 1) % 4
        if (boardOverlaps(b.board, b.side, r, b.position, ignore = b.id)) return run { model.message = Texts.MAP_OVERLAP }
        model.edit { it.updateBoard(b.id) { x -> x.copy(rotation = r) } }
    }

    /** F: turn the new or selected board or piece over. */
    fun flip() {
        val t = tool
        val sel = model.selection
        when {
            t is BattleTool.PlaceBoard -> tool = t.copy(side = other(t.side))
            t is BattleTool.PlacePiece -> if (model.catalog.isEdgePiece(t.piece, t.side) == model.catalog.isEdgePiece(t.piece, other(t.side))) tool = t.copy(side = other(t.side))
            sel != null && scenario?.boardPlacement(sel) != null -> model.edit { it.updateBoard(sel) { b -> b.copy(side = other(b.side)) } }
            sel != null && scenario?.terrainPlacement(sel) != null -> model.edit { it.flipTerrain(sel, model.catalog) }
        }
    }

    /** Entf: delete the selected board, piece, token or enemy group. */
    fun deleteSelection() {
        val sel = model.selection ?: return
        val kind = model.definitions.firstOrNull { it.id == sel }?.kind ?: return
        if (kind !in setOf(ObjectKind.BOARD_PLACEMENT, ObjectKind.TERRAIN_PLACEMENT, ObjectKind.TOKEN, ObjectKind.ENEMY_GROUP)) return
        val parent = chapter?.id
        if (model.edit { it.delete(sel) }) model.selection = parent
    }

    fun cancel() {
        tool = BattleTool.Select
        drag = null
    }

    private fun other(side: TileSide) = if (side == TileSide.A) TileSide.B else TileSide.A

    private fun create(f: (Campaign) -> Created) {
        var id: String? = null
        if (model.edit { c -> f(c).also { id = it.id }.campaign }) model.selection = id
    }
}
