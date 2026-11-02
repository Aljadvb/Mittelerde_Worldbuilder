package io.github.aljadvb.worldbuilder.editor.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.aljadvb.worldbuilder.editing.freeBoards
import io.github.aljadvb.worldbuilder.editing.freePieces
import io.github.aljadvb.worldbuilder.editing.isEdgePiece
import io.github.aljadvb.worldbuilder.editing.mapExtent
import io.github.aljadvb.worldbuilder.editor.EditorModel
import io.github.aljadvb.worldbuilder.editor.Texts
import io.github.aljadvb.worldbuilder.hexmap.BattleCanvas
import io.github.aljadvb.worldbuilder.hexmap.BattleProjection
import io.github.aljadvb.worldbuilder.hexmap.BattleScene
import io.github.aljadvb.worldbuilder.hexmap.MapColors
import io.github.aljadvb.worldbuilder.hexmap.SquareEnemy
import io.github.aljadvb.worldbuilder.hexmap.SquareToken
import io.github.aljadvb.worldbuilder.hexmap.battleBoardView
import io.github.aljadvb.worldbuilder.hexmap.battlePieceView
import io.github.aljadvb.worldbuilder.model.BattleMap
import io.github.aljadvb.worldbuilder.model.BoardPlacement
import io.github.aljadvb.worldbuilder.model.SquareLocation
import io.github.aljadvb.worldbuilder.model.TerrainPlacement
import io.github.aljadvb.worldbuilder.model.TokenType

const val BATTLE_MAP_TAG = "battle_map"

private val LETTERS = mapOf(TokenType.SEARCH to "S", TokenType.PERSON to "P", TokenType.THREAT to "B", TokenType.EXPLORE to "E", TokenType.CUSTOM to "?")

/** What the battle editor shows for the controller's chapter, including drag and placement previews. */
fun editorBattleScene(model: EditorModel, c: BattleController): BattleScene? {
    val s = c.scenario ?: return null
    val current = c.chapter ?: return null
    val catalog = model.catalog
    val images = model.tileImages
    val drag = c.drag
    val tool = c.tool
    val chapters = c.visibleChapters
    fun alpha(chId: String) = if (chId == current.id) 1f else 0.45f

    val boardPlacements = chapters.flatMap { ch ->
        ch.battlefield!!.boards.map { b -> (if (drag is BattleDrag.Board && drag.id == b.id) b.copy(position = drag.position) else b) to ch.id }
    }
    val ghostBoard = (tool as? BattleTool.PlaceBoard)?.let { t ->
        c.hoverSquare?.let { sq -> io.github.aljadvb.worldbuilder.editing.boardPositionAt(sq, catalog, t.board, t.side, t.rotation) }
            ?.let { BoardPlacement("bp__ghost", t.board, t.side, it, t.rotation) }
    }
    val map = BattleMap(boardPlacements.map { it.first }, catalog)
    val boards = boardPlacements.mapIndexedNotNull { i, (b, ch) ->
        battleBoardView(b, catalog, images, fill = MapColors.chapterFill(i), emphasized = b.id == model.selection, alpha = alpha(ch))
    } + listOfNotNull(ghostBoard?.let { battleBoardView(it, catalog, images, alpha = 0.5f, emphasized = true) })

    val pieces = chapters.flatMap { ch ->
        ch.battlefield!!.terrain.map { t ->
            val moved = if (drag is BattleDrag.Piece && drag.id == t.id) {
                if (t.edge != null) t.copy(edge = drag.edge) else t.copy(area = drag.square)
            } else t
            battlePieceView(moved, catalog, images, map, selected = t.id == model.selection, alpha = alpha(ch.id))
        }
    } + listOfNotNull((tool as? BattleTool.PlacePiece)?.let { t ->
        val edge = catalog.isEdgePiece(t.piece, t.side)
        val ghost = if (edge) c.hoverEdge?.let { TerrainPlacement("gt__ghost", t.piece, t.side, edge = it) }
        else c.hoverSquare?.let { TerrainPlacement("gt__ghost", t.piece, t.side, area = it) }
        ghost?.let { battlePieceView(it, catalog, images, map, alpha = 0.6f) }
    })

    val tokens = chapters.flatMap { it.tokens }.mapNotNull { t ->
        val sq = (drag as? BattleDrag.Token)?.takeIf { it.id == t.id }?.square ?: (t.location as? SquareLocation)?.square ?: return@mapNotNull null
        SquareToken(t.id, sq, t.type, LETTERS.getValue(t.type), selected = t.id == model.selection)
    }
    val enemies = s.enemyGroups.mapNotNull { g ->
        val sq = (drag as? BattleDrag.Enemy)?.takeIf { it.id == g.id }?.square ?: (g.location as? SquareLocation)?.square ?: return@mapNotNull null
        if (map.field(sq) == null && drag !is BattleDrag.Enemy) return@mapNotNull null
        SquareEnemy(g.id, sq, g.count[2] ?: 0, selected = g.id == model.selection)
    }
    val placingFigure = tool is BattleTool.PlaceToken || tool == BattleTool.PlaceEnemy
    val cellMm = boardPlacements.firstNotNullOfOrNull { (b, _) -> catalog.battleBoard(b.board)?.sides?.get(b.side)?.cellSizeMm } ?: 95.0
    return BattleScene(
        boards = boards, pieces = pieces, tokens = tokens, enemies = enemies, cellSizeMm = cellMm,
        highlightSquares = if (placingFigure) setOfNotNull(c.hoverSquare) else emptySet(),
        showGrid = true,
    )
}

@Composable
fun BattleEditor(model: EditorModel, controller: BattleController, modifier: Modifier = Modifier) {
    val camera = controller.camera
    val focus = remember { FocusRequester() }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val highlight = MaterialTheme.colorScheme.primary
    val scene = editorBattleScene(model, controller) ?: return

    fun fit() {
        val squares = scene.squares.ifEmpty { (0..2).flatMap { x -> (0..2).map { y -> io.github.aljadvb.worldbuilder.model.GridCoord(x, y) } }.toSet() }
        val proj = BattleProjection.fit(squares, viewSize.width.toFloat(), viewSize.height.toFloat(), 40f, 220f)
        camera.cell = proj.cell
        camera.origin = proj.origin
        camera.fitted = true
    }

    Column(modifier) {
        BattleToolbar(model, controller, onFit = ::fit)
        val s = controller.scenario
        if (s != null) {
            val extent = s.mapExtent(model.catalog)
            val fits = model.table.fits(extent)
            Text(
                Texts.tableInfo(extent.toString(), model.table.toString(), fits),
                color = if (fits) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        Text(
            when (controller.tool) {
                BattleTool.Select -> Texts.BATTLE_HINT_SELECT
                is BattleTool.PlaceBoard -> Texts.BATTLE_HINT_BOARD
                is BattleTool.PlacePiece -> Texts.BATTLE_HINT_PIECE
                else -> Texts.MAP_HINT_PLACE
            } + (controller.chapter?.let { "   ·   ${Texts.MAP_EDITING}: ${it.title}" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        )
        Box(
            Modifier.weight(1f).fillMaxWidth()
                .testTag(BATTLE_MAP_TAG)
                .clipToBounds()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                .background(Color(0xFFFAF8F3))
                .onSizeChanged { viewSize = it; if (!camera.fitted && it.width > 0) fit() }
                .focusRequester(focus)
                .focusable()
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.R -> { controller.rotate(); true }
                        Key.F -> { controller.flip(); true }
                        Key.Delete -> { controller.deleteSelection(); true }
                        Key.Escape -> { controller.cancel(); true }
                        else -> false
                    }
                }
                .pointerInput(Unit) {
                    var panFrom: Offset? = null
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.first()
                            val pos = change.position
                            val proj = camera.projection()
                            when (event.type) {
                                PointerEventType.Press -> {
                                    focus.requestFocus()
                                    val current = editorBattleScene(model, controller) ?: continue
                                    val consumed = event.buttons.isPrimaryPressed && controller.press(pos, proj, current)
                                    if (!consumed || event.buttons.isTertiaryPressed) panFrom = pos
                                }
                                PointerEventType.Move -> {
                                    val from = panFrom
                                    if (from != null) { camera.origin += pos - from; panFrom = pos } else controller.move(pos, proj)
                                }
                                PointerEventType.Release -> { panFrom = null; controller.release() }
                                PointerEventType.Exit -> { controller.hoverSquare = null; controller.hoverEdge = null }
                                PointerEventType.Scroll -> {
                                    val factor = if (change.scrollDelta.y > 0) 0.9f else 1.1f
                                    val newCell = (camera.cell * factor).coerceIn(30f, 600f)
                                    camera.origin = pos - (pos - camera.origin) * (newCell / camera.cell)
                                    camera.cell = newCell
                                }
                            }
                        }
                    }
                },
        ) {
            BattleCanvas(scene, highlight, Modifier.fillMaxSize(), projection = { camera.projection() })
        }
    }
}

@Composable
private fun BattleToolbar(model: EditorModel, controller: BattleController, onFit: () -> Unit) {
    var boardsOpen by remember { mutableStateOf(false) }
    var piecesOpen by remember { mutableStateOf(false) }
    var tokensOpen by remember { mutableStateOf(false) }
    val s = controller.scenario
    val owned = model.catalog.expansions.filter { it.owned }.map { it.id }.toSet()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FilterChip(selected = controller.tool == BattleTool.Select, onClick = { controller.cancel() }, label = { Text(Texts.MAP_SELECT) })
        Box {
            FilterChip(selected = controller.tool is BattleTool.PlaceBoard, onClick = { boardsOpen = true }, label = { Text(Texts.BATTLE_ADD_BOARD + " ▾") })
            DropdownMenu(expanded = boardsOpen, onDismissRequest = { boardsOpen = false }) {
                val free = s?.freeBoards(model.catalog, controller.chapter?.id).orEmpty()
                model.catalog.battleBoards.filter { it.expansion in owned }.forEach { board ->
                    board.sides.keys.sorted().forEach { side ->
                        val desc = board.sides.getValue(side).description
                        DropdownMenuItem(
                            text = { Text("${board.label} ${Texts.SIDE} $side · ${Texts.free(free[board.id] ?: board.count)}" + if (desc.isNotBlank()) " · $desc" else "") },
                            enabled = (free[board.id] ?: board.count) > 0,
                            onClick = { boardsOpen = false; controller.tool = BattleTool.PlaceBoard(board.id, side) },
                        )
                    }
                }
            }
        }
        Box {
            FilterChip(selected = controller.tool is BattleTool.PlacePiece, onClick = { piecesOpen = true }, label = { Text(Texts.BATTLE_ADD_PIECE + " ▾") })
            DropdownMenu(expanded = piecesOpen, onDismissRequest = { piecesOpen = false }) {
                val free = s?.freePieces(model.catalog, controller.chapter?.id).orEmpty()
                model.catalog.terrainPieces.filter { it.expansion in owned }.forEach { piece ->
                    piece.sides.keys.sorted().forEach { side ->
                        val ps = piece.sides.getValue(side)
                        val n = free[piece.id] ?: piece.count
                        DropdownMenuItem(
                            text = { Text("${ps.name} · ${if (ps.placement.name == "EDGE") Texts.BATTLE_ON_BORDER else Texts.BATTLE_IN_FIELD} · ${Texts.free(n)}" + if (ps.effect.isNotBlank()) " · ${ps.effect}" else "") },
                            enabled = n > 0,
                            onClick = { piecesOpen = false; controller.tool = BattleTool.PlacePiece(piece.id, side) },
                        )
                    }
                }
            }
        }
        Box {
            FilterChip(selected = controller.tool is BattleTool.PlaceToken, onClick = { tokensOpen = true }, label = { Text(Texts.MAP_ADD_TOKEN + " ▾") })
            DropdownMenu(expanded = tokensOpen, onDismissRequest = { tokensOpen = false }) {
                Texts.TOKEN_TYPES.forEach { (key, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { tokensOpen = false; controller.tool = BattleTool.PlaceToken(TokenType.valueOf(key.uppercase())) })
                }
            }
        }
        FilterChip(selected = controller.tool == BattleTool.PlaceEnemy, onClick = { controller.tool = BattleTool.PlaceEnemy }, label = { Text(Texts.MAP_ADD_ENEMY) })
        AssistChip(onClick = { controller.rotate() }, label = { Text(Texts.MAP_ROTATE) })
        AssistChip(onClick = { controller.flip() }, label = { Text(Texts.MAP_FLIP) })
        AssistChip(onClick = onFit, label = { Text(Texts.MAP_FIT) })
    }
}
