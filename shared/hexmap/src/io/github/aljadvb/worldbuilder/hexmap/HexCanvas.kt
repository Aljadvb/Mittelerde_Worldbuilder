package io.github.aljadvb.worldbuilder.hexmap

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.github.aljadvb.worldbuilder.model.HexCoord
import io.github.aljadvb.worldbuilder.model.HexOrientation
import io.github.aljadvb.worldbuilder.model.TokenType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A tile to draw: its map cells and appearance. */
data class MapTile(
    val id: String,
    val label: String,
    val cells: List<HexCoord>,
    val fill: Color,
    val border: Color = MapColors.TileBorder,
    val emphasized: Boolean = false,
    val alpha: Float = 1f,
    /** Picture of the tile (own scan); drawn clipped to [cells] instead of [fill]. */
    val image: MapTileImage? = null,
)

data class MapToken(val id: String, val cell: HexCoord, val type: TokenType, val letter: String, val selected: Boolean = false)

data class MapEnemy(val id: String, val cell: HexCoord, val count: Int, val selected: Boolean = false)

/** Everything the map shows. */
data class MapScene(
    val orientation: HexOrientation,
    val tiles: List<MapTile> = emptyList(),
    val tokens: List<MapToken> = emptyList(),
    val enemies: List<MapEnemy> = emptyList(),
    /** Cells to ring in the highlight colour (e.g. where to put a new token). */
    val highlightCells: Set<HexCoord> = emptySet(),
    /** Cells to mark as conflicting (e.g. overlapping tiles). */
    val errorCells: Set<HexCoord> = emptySet(),
    /** Draw a faint background grid (editor). */
    val showGrid: Boolean = false,
)

object MapColors {
    val TileBorder = Color(0xFF5D4E37)
    val Label = Color(0xFF3E3324)
    val Enemy = Color(0xFFB3261E)
    val Error = Color(0xFFD32F2F)
    val Grid = Color(0x22000000)
    val ChapterFills = listOf(Color(0xFFE8DCC4), Color(0xFFD5E3CF), Color(0xFFD9DCEB), Color(0xFFEBD9D9), Color(0xFFE3E0CC))

    fun chapterFill(index: Int) = ChapterFills[index.coerceAtLeast(0) % ChapterFills.size]

    fun token(type: TokenType) = when (type) {
        TokenType.SEARCH -> Color(0xFFB8860B)
        TokenType.PERSON -> Color(0xFF1E5AA8)
        TokenType.THREAT -> Color(0xFF6A1B9A)
        TokenType.EXPLORE -> Color(0xFF2E7D32)
        TokenType.CUSTOM -> Color(0xFF546E7A)
    }
}

/**
 * Draws a [MapScene]. With [projection] == null the map is fitted into the canvas;
 * the projection actually used is reported through [onProjection] so callers can map
 * pointer positions to hexes.
 */
@Composable
fun HexCanvas(
    scene: MapScene,
    highlight: Color,
    modifier: Modifier = Modifier,
    projection: ((Size) -> HexProjection)? = null,
    onProjection: (HexProjection) -> Unit = {},
) {
    val textMeasurer = rememberTextMeasurer()
    Canvas(modifier) {
        val allCells = scene.tiles.flatMap { it.cells }
        val proj = projection?.invoke(size)
            ?: HexProjection.fit(scene.orientation, allCells, size.width, size.height, 12.dp.toPx(), 56.dp.toPx())
        onProjection(proj)
        val thin = 1.dp.toPx()

        if (scene.showGrid) drawGrid(proj)

        for (tile in scene.tiles) {
            val coords = tile.cells.toSet()
            val image = tile.image
            if (image != null) {
                drawTileImage(proj, coords, image, tile.alpha)
                if (tile.emphasized) for (cell in coords) drawPath(hexPath(proj.corners(cell)), highlight, alpha = 0.25f * tile.alpha)
            }
            for (cell in coords) {
                val path = hexPath(proj.corners(cell))
                if (image == null) drawPath(path, tile.fill, alpha = tile.alpha)
                drawPath(path, Color.Black.copy(alpha = (if (image == null) 0.25f else 0.18f) * tile.alpha), style = Stroke(thin))
            }
            drawTileBorder(proj, coords, if (tile.emphasized) highlight else tile.border, if (tile.emphasized) 4.dp.toPx() else 2.dp.toPx(), tile.alpha)
        }

        for (cell in scene.errorCells) {
            drawPath(hexPath(proj.corners(cell)), MapColors.Error, alpha = 0.45f)
        }
        for (cell in scene.highlightCells) {
            val c = proj.center(cell)
            drawPath(hexPath(proj.corners(cell).map { c + (it - c) * 0.85f }), highlight, style = Stroke(3.dp.toPx()))
        }

        // Tile labels in the cell nearest to the centroid, on a light pill
        for (tile in scene.tiles) {
            if (tile.cells.isEmpty() || tile.label.isBlank()) continue
            val centers = tile.cells.map { proj.center(it) }
            val centroid = Offset(centers.map { it.x }.average().toFloat(), centers.map { it.y }.average().toFloat())
            val anchor = centers.minBy { (it - centroid).getDistance() }
            val measured = textMeasurer.measure(
                tile.label,
                TextStyle(fontSize = (proj.size / 3.4f).toSp(), fontWeight = FontWeight.Bold, color = MapColors.Label.copy(alpha = tile.alpha)),
            )
            val topLeft = Offset(anchor.x - measured.size.width / 2f, anchor.y + proj.size * 0.32f)
            val pad = 3.dp.toPx()
            drawRoundRect(
                Color.White.copy(alpha = 0.8f * tile.alpha),
                topLeft = topLeft - Offset(pad, pad / 2),
                size = Size(measured.size.width + 2 * pad, measured.size.height + pad),
                cornerRadius = CornerRadius(pad * 2),
            )
            drawText(measured, topLeft = topLeft)
        }

        for (enemy in scene.enemies) {
            val c = enemyCenter(proj, enemy.cell)
            val r = proj.size * 0.3f
            val diamond = Path().apply {
                moveTo(c.x, c.y - r); lineTo(c.x + r, c.y); lineTo(c.x, c.y + r); lineTo(c.x - r, c.y); close()
            }
            if (enemy.selected) drawCircle(highlight, r * 1.4f, c, style = Stroke(3.dp.toPx()))
            drawPath(diamond, MapColors.Enemy)
            val m = textMeasurer.measure(enemy.count.toString(), TextStyle(fontSize = (r * 0.9f).toSp(), color = Color.White, fontWeight = FontWeight.Bold))
            drawText(m, topLeft = Offset(c.x - m.size.width / 2f, c.y - m.size.height / 2f))
        }

        for ((token, c) in tokenCenters(proj, scene.tokens)) {
            val r = proj.size * 0.32f
            if (token.selected) drawCircle(highlight, r * 1.45f, c, style = Stroke(3.dp.toPx()))
            drawCircle(Color.White, r * 1.12f, c)
            drawCircle(MapColors.token(token.type), r, c)
            val m = textMeasurer.measure(token.letter, TextStyle(fontSize = (r * 1.1f).toSp(), color = Color.White, fontWeight = FontWeight.Bold))
            drawText(m, topLeft = Offset(c.x - m.size.width / 2f, c.y - m.size.height / 2f))
        }
    }
}

/** Pixel centres of tokens; several tokens on one cell are spread around its centre. */
fun tokenCenters(proj: HexProjection, tokens: List<MapToken>): List<Pair<MapToken, Offset>> =
    tokens.groupBy { it.cell }.flatMap { (cell, onCell) ->
        onCell.mapIndexed { i, token ->
            val spread = if (onCell.size == 1) Offset.Zero else Offset(
                cos(2 * PI * i / onCell.size).toFloat(), sin(2 * PI * i / onCell.size).toFloat(),
            ) * (proj.size * 0.35f)
            token to (proj.center(cell) + spread - Offset(0f, proj.size * 0.1f))
        }
    }

fun enemyCenter(proj: HexProjection, cell: HexCoord): Offset = proj.center(cell) + Offset(proj.size * 0.35f, -proj.size * 0.35f)

/** The token whose disc contains [p], if any. */
fun tokenAt(proj: HexProjection, tokens: List<MapToken>, p: Offset): MapToken? =
    tokenCenters(proj, tokens)
        .minByOrNull { (it.second - p).getDistance() }
        ?.takeIf { (it.second - p).getDistance() <= proj.size * 0.45f }
        ?.first

private fun hexPath(corners: List<Offset>) = Path().apply {
    moveTo(corners[0].x, corners[0].y)
    corners.drop(1).forEach { lineTo(it.x, it.y) }
    close()
}

/** Outer outline of a tile: every cell edge whose neighbour is not part of the tile. */
private fun DrawScope.drawTileBorder(proj: HexProjection, cells: Set<HexCoord>, color: Color, width: Float, alpha: Float) {
    for (cell in cells) {
        val center = proj.center(cell)
        val corners = proj.corners(cell)
        for (neighbor in cell.neighbors()) {
            if (neighbor in cells) continue
            val dir = proj.center(neighbor) - center
            val edge = corners.sortedByDescending { (it - center).x * dir.x + (it - center).y * dir.y }.take(2)
            drawLine(color, edge[0], edge[1], strokeWidth = width, alpha = alpha)
        }
    }
}

/**
 * Draws a tile picture so that its anchor pixel lands on the anchor cell, scaled to the
 * current hex size and rotated with the placement, clipped to the tile's cells.
 */
private fun DrawScope.drawTileImage(proj: HexProjection, cells: Set<HexCoord>, image: MapTileImage, alpha: Float) {
    val clip = Path().apply { cells.forEach { addPath(hexPath(proj.corners(it))) } }
    val target = proj.center(image.anchorCell)
    val a = image.anchorPx
    val scale = proj.size / image.hexSize
    clipPath(clip) {
        withTransform({
            translate(target.x - a.x, target.y - a.y)
            rotate(image.rotation * 60f, a)
            scale(scale, scale, a)
        }) {
            drawImage(image.bitmap, alpha = alpha)
        }
    }
}

/** Faint grid over the visible area. */
private fun DrawScope.drawGrid(proj: HexProjection) {
    val corners = listOf(Offset.Zero, Offset(size.width, 0f), Offset(0f, size.height), Offset(size.width, size.height)).map(proj::hexAt)
    val qs = corners.map { it.q }
    val rs = corners.map { it.r }
    val stroke = Stroke(1f)
    for (q in qs.min() - 2..qs.max() + 2) for (r in rs.min() - 2..rs.max() + 2) {
        val c = proj.center(HexCoord(q, r))
        if (c.x < -proj.size || c.y < -proj.size || c.x > size.width + proj.size || c.y > size.height + proj.size) continue
        drawPath(hexPath(proj.corners(HexCoord(q, r))), MapColors.Grid, style = stroke)
    }
}
