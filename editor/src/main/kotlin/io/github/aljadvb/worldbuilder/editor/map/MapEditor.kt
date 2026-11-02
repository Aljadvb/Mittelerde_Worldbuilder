package io.github.aljadvb.worldbuilder.editor.map

import io.github.aljadvb.worldbuilder.hexmap.mapCell
import io.github.aljadvb.worldbuilder.model.placementId
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AssistChip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.aljadvb.worldbuilder.editing.mapCells
import io.github.aljadvb.worldbuilder.editing.mapExtent
import io.github.aljadvb.worldbuilder.editor.EditorModel
import io.github.aljadvb.worldbuilder.editor.Texts
import io.github.aljadvb.worldbuilder.hexmap.HexCanvas
import io.github.aljadvb.worldbuilder.hexmap.HexProjection
import io.github.aljadvb.worldbuilder.hexmap.MapColors
import io.github.aljadvb.worldbuilder.hexmap.MapEnemy
import io.github.aljadvb.worldbuilder.hexmap.MapScene
import io.github.aljadvb.worldbuilder.hexmap.MapTile
import io.github.aljadvb.worldbuilder.hexmap.MapToken
import io.github.aljadvb.worldbuilder.model.TokenType

const val MAP_TAG = "map"

/** Zoom and pan of the editor map (hex radius in px and pixel origin of hex (0,0)). */
class MapCamera {
    var size by mutableStateOf(36f)
    var origin by mutableStateOf(Offset.Zero)
    var fitted by mutableStateOf(false)
}

private val TOKEN_LETTERS = mapOf(
    TokenType.SEARCH to "S", TokenType.PERSON to "P", TokenType.THREAT to "B", TokenType.EXPLORE to "E", TokenType.CUSTOM to "?",
)

/** Builds what the editor map shows for the controller's current chapter. */
fun editorScene(model: EditorModel, c: MapController, highlight: Color): MapScene? {
    val s = c.scenario ?: return null
    val current = c.chapter ?: return null
    val visible = c.visibleChapters
    val catalog = model.catalog
    val drag = c.drag
    val tiles = visible.flatMap { ch ->
        val index = s.chapters.indexOf(ch)
        ch.tiles.filter { !(drag is Drag.Tile && drag.placementId == it.id) }.map { p ->
            MapTile(
                id = p.id,
                label = "${catalog.tile(p.tile)?.label ?: p.tile} ${p.side}",
                cells = p.mapCells(catalog),
                fill = MapColors.chapterFill(index),
                emphasized = p.id == model.selection,
                alpha = if (ch.id == current.id) 1f else 0.4f,
                image = model.tileImages.forPlacement(p, catalog),
            )
        }
    } + listOfNotNull(c.ghost?.let { g ->
        MapTile(
            "pl__ghost", "${catalog.tile(g.tile)?.label ?: g.tile} ${g.side}", g.mapCells(catalog), highlight.copy(alpha = 0.35f),
            emphasized = true, alpha = 0.85f, image = model.tileImages.forPlacement(g, catalog),
        )
    })
    val tokens = visible.flatMap { it.tokens }.mapNotNull { t ->
        val cell = (drag as? Drag.Token)?.takeIf { it.tokenId == t.id }?.cell
            ?: s.mapCell(t.location) ?: return@mapNotNull null
        MapToken(t.id, cell, t.type, TOKEN_LETTERS.getValue(t.type), selected = t.id == model.selection)
    }
    val visibleIds = visible.map { it.id }.toSet()
    val enemies = s.enemyGroups.filter { s.chapterOfPlacement(it.location.placementId.orEmpty())?.id in visibleIds }.mapNotNull { g ->
        val cell = (drag as? Drag.Enemy)?.takeIf { it.groupId == g.id }?.cell
            ?: s.mapCell(g.location) ?: return@mapNotNull null
        MapEnemy(g.id, cell, g.count[2] ?: 0, selected = g.id == model.selection)
    }
    val placing = c.tool is MapTool.PlaceToken || c.tool == MapTool.PlaceEnemy
    return MapScene(
        orientation = catalog.hexOrientation,
        tiles = tiles,
        tokens = tokens,
        enemies = enemies,
        highlightCells = if (placing) setOfNotNull(c.hover) else emptySet(),
        errorCells = c.ghostConflicts,
        showGrid = true,
    )
}

@Composable
fun MapEditor(model: EditorModel, controller: MapController, modifier: Modifier = Modifier) {
    val camera = controller.camera
    val focus = remember { FocusRequester() }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val highlight = MaterialTheme.colorScheme.primary
    val scene = editorScene(model, controller, highlight) ?: return
    val chapter = controller.chapter

    fun fit() {
        val cells = controller.visibleChapters.flatMap { it.tiles }.flatMap { it.mapCells(model.catalog) }
        val proj = HexProjection.fit(scene.orientation, cells.ifEmpty { listOf(io.github.aljadvb.worldbuilder.model.HexCoord.ORIGIN) }, viewSize.width.toFloat(), viewSize.height.toFloat(), 40f, 48f)
        camera.size = proj.size
        camera.origin = proj.origin
        camera.fitted = true
    }
    fun projection() = HexProjection(scene.orientation, camera.size, camera.origin)

    Column(modifier) {
        Toolbar(model, controller, onFit = ::fit)
        TableInfo(model, controller)
        Text(
            when (controller.tool) {
                MapTool.Select -> Texts.MAP_HINT_SELECT
                is MapTool.PlaceTile -> Texts.MAP_HINT_TILE
                else -> Texts.MAP_HINT_PLACE
            } + (chapter?.let { "   ·   ${Texts.MAP_EDITING}: ${it.title}" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        )
        Box(
            Modifier.weight(1f).fillMaxWidth()
                .testTag(MAP_TAG)
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
                .pointerInput(scene.orientation) {
                    var panFrom: Offset? = null
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.first()
                            val pos = change.position
                            when (event.type) {
                                PointerEventType.Press -> {
                                    focus.requestFocus()
                                    val consumed = event.buttons.isPrimaryPressed && controller.press(projection().hexAt(pos))
                                    if (!consumed || event.buttons.isTertiaryPressed) panFrom = pos
                                }
                                PointerEventType.Move -> {
                                    val from = panFrom
                                    if (from != null) {
                                        camera.origin += pos - from
                                        panFrom = pos
                                    } else {
                                        controller.move(projection().hexAt(pos))
                                    }
                                }
                                PointerEventType.Release -> {
                                    if (panFrom != null) panFrom = null
                                    controller.release()
                                }
                                PointerEventType.Exit -> controller.hover = null
                                PointerEventType.Scroll -> {
                                    val factor = if (change.scrollDelta.y > 0) 0.9f else 1.1f
                                    val newSize = (camera.size * factor).coerceIn(10f, 140f)
                                    camera.origin = pos - (pos - camera.origin) * (newSize / camera.size)
                                    camera.size = newSize
                                }
                            }
                        }
                    }
                },
        ) {
            HexCanvas(
                scene = scene,
                highlight = highlight,
                modifier = Modifier.fillMaxSize(),
                projection = { projection() },
            )
        }
    }
}

/** Size of the whole scenario map compared with the table. */
@Composable
private fun TableInfo(model: EditorModel, controller: MapController) {
    val scenario = controller.scenario ?: return
    val extent = scenario.mapExtent(model.catalog)
    val fits = model.table.fits(extent)
    Text(
        Texts.tableInfo(extent.toString(), model.table.toString(), fits),
        color = if (fits) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun Toolbar(model: EditorModel, controller: MapController, onFit: () -> Unit) {
    var tilesOpen by remember { mutableStateOf(false) }
    var tokensOpen by remember { mutableStateOf(false) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FilterChip(selected = controller.tool == MapTool.Select, onClick = { controller.cancel() }, label = { Text(Texts.MAP_SELECT) })
        Box {
            FilterChip(selected = controller.tool is MapTool.PlaceTile, onClick = { tilesOpen = true }, label = { Text(Texts.MAP_ADD_TILE + " ▾") })
            DropdownMenu(expanded = tilesOpen, onDismissRequest = { tilesOpen = false }) {
                val owned = model.catalog.expansions.filter { it.owned }.map { it.id }.toSet()
                model.catalog.tiles.filter { it.expansion in owned }.forEach { tile ->
                    tile.sides.keys.sorted().forEach { side ->
                        DropdownMenuItem(
                            text = {
                                val shape = tile.sides.getValue(side)
                                val terrain = shape.terrain.joinToString(", ") { io.github.aljadvb.worldbuilder.model.Terrain.label(it) }
                                Text("${tile.label} ${Texts.SIDE} $side · ${shape.cells.size} Felder" + if (terrain.isNotEmpty()) " · $terrain" else "")
                            },
                            onClick = { tilesOpen = false; controller.tool = MapTool.PlaceTile(tile.id, side) },
                        )
                    }
                }
            }
        }
        Box {
            FilterChip(selected = controller.tool is MapTool.PlaceToken, onClick = { tokensOpen = true }, label = { Text(Texts.MAP_ADD_TOKEN + " ▾") })
            DropdownMenu(expanded = tokensOpen, onDismissRequest = { tokensOpen = false }) {
                Texts.TOKEN_TYPES.forEach { (key, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { tokensOpen = false; controller.tool = MapTool.PlaceToken(TokenType.valueOf(key.uppercase())) })
                }
            }
        }
        FilterChip(selected = controller.tool == MapTool.PlaceEnemy, onClick = { controller.tool = MapTool.PlaceEnemy }, label = { Text(Texts.MAP_ADD_ENEMY) })
        AssistChip(onClick = { controller.rotate() }, label = { Text(Texts.MAP_ROTATE) })
        AssistChip(onClick = { controller.flip() }, label = { Text(Texts.MAP_FLIP) })
        AssistChip(onClick = onFit, label = { Text(Texts.MAP_FIT) })
    }
}

