package io.github.aljadvb.worldbuilder.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aljadvb.worldbuilder.engine.GameState
import io.github.aljadvb.worldbuilder.model.HexCoord
import io.github.aljadvb.worldbuilder.model.HexOrientation
import io.github.aljadvb.worldbuilder.model.MaterialCatalog
import io.github.aljadvb.worldbuilder.model.Scenario
import io.github.aljadvb.worldbuilder.model.TileLocation
import io.github.aljadvb.worldbuilder.model.TokenType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** What the map should emphasise for the current prompt. */
data class MapHighlight(
    val placements: Set<String> = emptySet(),
    val cells: Set<HexCoord> = emptySet(),
)

/** Converts axial hex coordinates to pixels for a given orientation. */
private class HexLayout(val orientation: HexOrientation, val size: Float, val origin: Offset) {
    fun center(c: HexCoord): Offset = origin + unitCenter(orientation, c) * size

    fun corners(c: HexCoord): List<Offset> {
        val center = center(c)
        val start = if (orientation == HexOrientation.POINTY) 30.0 else 0.0
        return (0 until 6).map { i ->
            val a = (start + 60.0 * i) * PI / 180.0
            Offset(center.x + size * cos(a).toFloat(), center.y + size * sin(a).toFloat())
        }
    }

    companion object {
        private val SQRT3 = sqrt(3f)

        fun unitCenter(orientation: HexOrientation, c: HexCoord): Offset = when (orientation) {
            HexOrientation.POINTY -> Offset(SQRT3 * c.q + SQRT3 / 2f * c.r, 1.5f * c.r)
            HexOrientation.FLAT -> Offset(1.5f * c.q, SQRT3 / 2f * c.q + SQRT3 * c.r)
        }
    }
}

/** Map cell of a tile-local location, or null if the placement is unknown. */
fun Scenario.mapCell(location: TileLocation): HexCoord? = placement(location.placement)?.toMap(location.cell)

private class MapCell(val coord: HexCoord, val placementId: String, val chapterIndex: Int)

/**
 * Schematic map of all unlocked chapters: tiles as hex outlines with their printed label,
 * tokens as coloured discs (tap to interact) and enemy groups as red markers.
 */
@Composable
fun HexMap(
    scenario: Scenario,
    catalog: MaterialCatalog,
    state: GameState,
    highlight: MapHighlight,
    tokenLetters: Map<String, String>,
    tapEnabled: Boolean,
    onTokenTap: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val colors = MaterialTheme.colorScheme
    val chapterFills = listOf(Color(0xFFE8DCC4), Color(0xFFD5E3CF), Color(0xFFD9DCEB), Color(0xFFEBD9D9), Color(0xFFE3E0CC))
    val tokenCenters = remember { mutableMapOf<String, Offset>() }
    val tapRadius = remember { floatArrayOf(0f) }

    // Collect the cells of all visible placements.
    val cells = state.unlockedChapters.flatMap { chapterId ->
        val chapterIndex = scenario.chapters.indexOfFirst { it.id == chapterId }
        scenario.chapter(chapterId)?.tiles.orEmpty().flatMap { p ->
            val shape = catalog.tile(p.tile)?.sides?.get(p.side)?.cells.orEmpty()
            shape.map { MapCell(p.toMap(it), p.id, chapterIndex) }
        }
    }
    val placementCells = cells.groupBy { it.placementId }

    Canvas(
        modifier.pointerInput(tapEnabled) {
            if (!tapEnabled) return@pointerInput
            detectTapGestures { pos ->
                tokenCenters.minByOrNull { (it.value - pos).getDistance() }
                    ?.takeIf { (it.value - pos).getDistance() <= tapRadius[0] }
                    ?.let { onTokenTap(it.key) }
            }
        },
    ) {
        tokenCenters.clear()
        if (cells.isEmpty()) return@Canvas
        val layout = fitLayout(catalog.hexOrientation, cells.map { it.coord }, size)
        tapRadius[0] = layout.size * 0.9f
        val thin = 1.dp.toPx()

        // Tiles
        for ((placementId, tileCells) in placementCells) {
            val highlighted = placementId in highlight.placements
            val base = chapterFills[tileCells.first().chapterIndex.coerceAtLeast(0) % chapterFills.size]
            val fill = if (highlighted) colors.primary.copy(alpha = 0.35f).compositeOver(base) else base
            val coords = tileCells.map { it.coord }.toSet()
            for (cell in coords) {
                val path = hexPath(layout.corners(cell))
                drawPath(path, fill)
                drawPath(path, Color.Black.copy(alpha = 0.25f), style = Stroke(thin))
            }
            drawTileBorder(layout, coords, if (highlighted) colors.primary else Color(0xFF5D4E37), if (highlighted) 4.dp.toPx() else 2.dp.toPx())
        }

        // Highlighted cells
        for (cell in highlight.cells) {
            drawPath(hexPath(layout.corners(cell).map { layout.center(cell) + (it - layout.center(cell)) * 0.85f }), colors.primary, style = Stroke(3.dp.toPx()))
        }

        // Tile labels: in the cell closest to the tile's centroid, on a light pill for readability
        for ((placementId, tileCells) in placementCells) {
            val placement = scenario.placement(placementId) ?: continue
            val label = "${catalog.tile(placement.tile)?.label ?: placement.tile} ${placement.side}"
            val centers = tileCells.map { layout.center(it.coord) }
            val centroid = Offset(centers.map { it.x }.average().toFloat(), centers.map { it.y }.average().toFloat())
            val anchor = centers.minBy { (it - centroid).getDistance() }
            val measured = textMeasurer.measure(label, TextStyle(fontSize = (layout.size / 3.4f).toSp(), fontWeight = FontWeight.Bold, color = Color(0xFF3E3324)))
            val topLeft = Offset(anchor.x - measured.size.width / 2f, anchor.y + layout.size * 0.32f)
            val pad = 3.dp.toPx()
            drawRoundRect(
                Color.White.copy(alpha = 0.8f),
                topLeft = topLeft - Offset(pad, pad / 2),
                size = Size(measured.size.width + 2 * pad, measured.size.height + pad),
                cornerRadius = CornerRadius(pad * 2),
            )
            drawText(measured, topLeft = topLeft)
        }

        // Enemy groups
        for (groupId in state.activeEnemyGroups) {
            val group = scenario.enemyGroup(groupId) ?: continue
            val cell = scenario.mapCell(group.location) ?: continue
            val c = layout.center(cell) + Offset(layout.size * 0.35f, -layout.size * 0.35f)
            val r = layout.size * 0.3f
            val diamond = Path().apply {
                moveTo(c.x, c.y - r); lineTo(c.x + r, c.y); lineTo(c.x, c.y + r); lineTo(c.x - r, c.y); close()
            }
            drawPath(diamond, Color(0xFFB3261E))
            val n = (group.count[state.heroCount] ?: 0).toString()
            val m = textMeasurer.measure(n, TextStyle(fontSize = (r * 0.9f).toSp(), color = Color.White, fontWeight = FontWeight.Bold))
            drawText(m, topLeft = Offset(c.x - m.size.width / 2f, c.y - m.size.height / 2f))
        }

        // Tokens (several on one cell are spread out)
        val tokensByCell = state.activeTokens.mapNotNull { id -> scenario.token(id)?.let { t -> scenario.mapCell(t.location)?.let { it to t } } }
            .groupBy({ it.first }, { it.second })
        for ((cell, tokens) in tokensByCell) {
            tokens.forEachIndexed { i, token ->
                val spread = if (tokens.size == 1) Offset.Zero else Offset(
                    cos(2 * PI * i / tokens.size).toFloat(), sin(2 * PI * i / tokens.size).toFloat(),
                ) * (layout.size * 0.35f)
                val c = layout.center(cell) + spread - Offset(0f, layout.size * 0.1f)
                val r = layout.size * 0.32f
                drawCircle(Color.White, r * 1.12f, c)
                drawCircle(tokenColor(token.type), r, c)
                val letter = tokenLetters[token.id].orEmpty()
                val m = textMeasurer.measure(letter, TextStyle(fontSize = (r * 1.1f).toSp(), color = Color.White, fontWeight = FontWeight.Bold))
                drawText(m, topLeft = Offset(c.x - m.size.width / 2f, c.y - m.size.height / 2f))
                tokenCenters[token.id] = c
            }
        }
    }
}

private fun tokenColor(type: TokenType) = when (type) {
    TokenType.SEARCH -> Color(0xFFB8860B)
    TokenType.PERSON -> Color(0xFF1E5AA8)
    TokenType.THREAT -> Color(0xFF6A1B9A)
    TokenType.EXPLORE -> Color(0xFF2E7D32)
    TokenType.CUSTOM -> Color(0xFF546E7A)
}

/** Scales and centres the map so that all [cells] fit into [canvas]. */
private fun DrawScope.fitLayout(orientation: HexOrientation, cells: List<HexCoord>, canvas: Size): HexLayout {
    val centers = cells.map { HexLayout.unitCenter(orientation, it) }
    val minX = centers.minOf { it.x } - 1f
    val maxX = centers.maxOf { it.x } + 1f
    val minY = centers.minOf { it.y } - 1f
    val maxY = centers.maxOf { it.y } + 1f
    val pad = 12.dp.toPx()
    val scale = minOf((canvas.width - 2 * pad) / (maxX - minX), (canvas.height - 2 * pad) / (maxY - minY), 56.dp.toPx())
    val contentW = (maxX - minX) * scale
    val contentH = (maxY - minY) * scale
    val origin = Offset(
        (canvas.width - contentW) / 2f - minX * scale,
        (canvas.height - contentH) / 2f - minY * scale,
    )
    return HexLayout(orientation, scale, origin)
}

private fun hexPath(corners: List<Offset>) = Path().apply {
    moveTo(corners[0].x, corners[0].y)
    corners.drop(1).forEach { lineTo(it.x, it.y) }
    close()
}

/** Draws the outer outline of a tile: every cell edge whose neighbour is not part of the tile. */
private fun DrawScope.drawTileBorder(layout: HexLayout, cells: Set<HexCoord>, color: Color, width: Float) {
    for (cell in cells) {
        val center = layout.center(cell)
        val corners = layout.corners(cell)
        for (neighbor in cell.neighbors()) {
            if (neighbor in cells) continue
            val dir = layout.center(neighbor) - center
            val edge = corners.sortedByDescending { it.x * dir.x - center.x * dir.x + it.y * dir.y - center.y * dir.y }.take(2)
            drawLine(color, edge[0], edge[1], strokeWidth = width)
        }
    }
}
