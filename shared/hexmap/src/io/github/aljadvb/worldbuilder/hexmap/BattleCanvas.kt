package io.github.aljadvb.worldbuilder.hexmap

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.aljadvb.worldbuilder.model.EdgeSide
import io.github.aljadvb.worldbuilder.model.GridCoord
import io.github.aljadvb.worldbuilder.model.GridEdge
import io.github.aljadvb.worldbuilder.model.TokenType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/** A board picture: the unrotated region of [bitmap] covering the board's squares. */
data class BoardPicture(val bitmap: ImageBitmap, val originX: Float, val originY: Float, val cellPx: Float)

/** A placed battle board in battlefield squares. */
data class BattleBoardView(
    val id: String,
    val label: String,
    /** Top-left battlefield square. */
    val origin: GridCoord,
    /** Unrotated size in squares. */
    val width: Int,
    val height: Int,
    val rotation: Int,
    /** Printed fields, in battlefield squares. */
    val fields: List<Set<GridCoord>>,
    val picture: BoardPicture? = null,
    val fill: Color = MapColors.chapterFill(0),
    val emphasized: Boolean = false,
    val alpha: Float = 1f,
) {
    val rotatedWidth get() = if (rotation % 2 == 0) width else height
    val rotatedHeight get() = if (rotation % 2 == 0) height else width
}

/** A terrain piece on the battle map: in the field of [square] or on [edge]. */
data class BattlePieceView(
    val id: String,
    val name: String,
    val square: GridCoord? = null,
    val edge: GridEdge? = null,
    val picture: ImageBitmap? = null,
    /** Real width of the picture's horizontal side; null = default size. */
    val widthMm: Double? = null,
    /** Turn the picture by 90° (e.g. a long piece in a tall field). */
    val upright: Boolean = false,
    /** Has a scenario-specific name or rule (drawn with a gold marker). */
    val special: Boolean = false,
    val emphasized: Boolean = false,
    val selected: Boolean = false,
    val alpha: Float = 1f,
)

data class SquareToken(val id: String, val square: GridCoord, val type: TokenType, val letter: String, val selected: Boolean = false)

data class SquareEnemy(val id: String, val square: GridCoord, val count: Int, val selected: Boolean = false, val elite: Boolean = false, val banner: Int? = null)

/** Everything a battle map shows. */
data class BattleScene(
    val boards: List<BattleBoardView> = emptyList(),
    val pieces: List<BattlePieceView> = emptyList(),
    val tokens: List<SquareToken> = emptyList(),
    val enemies: List<SquareEnemy> = emptyList(),
    /** Edge length of one square on the real board, to draw pieces to scale. */
    val cellSizeMm: Double = 95.0,
    val highlightSquares: Set<GridCoord> = emptySet(),
    val errorSquares: Set<GridCoord> = emptySet(),
    /** Draw a faint square grid around the boards (editor). */
    val showGrid: Boolean = false,
    /** Start square of the heroes' figures. */
    val heroStart: GridCoord? = null,
    /** Rough-ground and fortified markers laid by events. */
    val markers: List<Pair<GridCoord, io.github.aljadvb.worldbuilder.model.FieldMarker>> = emptyList(),
) {
    val squares: Set<GridCoord> get() = boards.flatMap { it.fields.flatten() }.toSet()
}

/** Converts between battlefield squares and pixels. [cell] is the edge length of a square in px. */
class BattleProjection(val cell: Float, val origin: Offset) {
    fun topLeft(sq: GridCoord) = origin + Offset(sq.x * cell, sq.y * cell)
    fun center(sq: GridCoord) = topLeft(sq) + Offset(cell / 2, cell / 2)
    fun rect(sq: GridCoord) = Rect(topLeft(sq), Size(cell, cell))
    fun squareAt(p: Offset): GridCoord = ((p - origin) / cell).let { GridCoord(floor(it.x).toInt(), floor(it.y).toInt()) }

    /** Pixel end points of an edge. */
    fun edgeLine(e: GridEdge): Pair<Offset, Offset> {
        val r = rect(e.square)
        return when (e.side) {
            EdgeSide.NORTH -> r.topLeft to r.topRight
            EdgeSide.SOUTH -> r.bottomLeft to r.bottomRight
            EdgeSide.WEST -> r.topLeft to r.bottomLeft
            EdgeSide.EAST -> r.topRight to r.bottomRight
        }
    }

    /** The edge of the square under [p] that is closest to it. */
    fun edgeAt(p: Offset): GridEdge {
        val sq = squareAt(p)
        val local = (p - topLeft(sq)) / cell
        val d = mapOf(EdgeSide.NORTH to local.y, EdgeSide.SOUTH to 1 - local.y, EdgeSide.WEST to local.x, EdgeSide.EAST to 1 - local.x)
        return GridEdge(sq.x, sq.y, d.minBy { it.value }.key)
    }

    companion object {
        fun fit(squares: Collection<GridCoord>, width: Float, height: Float, padding: Float, maxCell: Float): BattleProjection {
            if (squares.isEmpty()) return BattleProjection(maxCell, Offset(width / 2, height / 2))
            val minX = squares.minOf { it.x }
            val maxX = squares.maxOf { it.x } + 1
            val minY = squares.minOf { it.y }
            val maxY = squares.maxOf { it.y } + 1
            val cell = minOf((width - 2 * padding) / (maxX - minX), (height - 2 * padding) / (maxY - minY), maxCell)
            val origin = Offset((width - (maxX - minX) * cell) / 2 - minX * cell, (height - (maxY - minY) * cell) / 2 - minY * cell)
            return BattleProjection(cell, origin)
        }
    }
}

/** Centre of a piece in pixels. */
fun pieceCenter(proj: BattleProjection, piece: BattlePieceView): Offset? =
    piece.edge?.let { e -> proj.edgeLine(e).let { (a, b) -> (a + b) / 2f } } ?: piece.square?.let(proj::center)

/** Pixel size (unrotated picture width × height) of a piece. */
fun pieceSize(proj: BattleProjection, scene: BattleScene, piece: BattlePieceView): Size {
    val pxPerMm = proj.cell / scene.cellSizeMm.toFloat()
    val pic = piece.picture
    val defaultWidth = if (piece.edge != null) proj.cell else proj.cell * 0.4f
    val w = piece.widthMm?.let { (it * pxPerMm).toFloat() } ?: defaultWidth
    val h = if (pic != null) w * pic.height / pic.width else if (piece.edge != null) proj.cell * 0.2f else w
    return Size(w, h)
}

/** The piece under [p], if any (pieces drawn last win). */
fun pieceAt(proj: BattleProjection, scene: BattleScene, p: Offset): BattlePieceView? =
    scene.pieces.asReversed().firstOrNull { piece ->
        val c = pieceCenter(proj, piece) ?: return@firstOrNull false
        val s = pieceSize(proj, scene, piece)
        val vertical = piece.upright || piece.edge?.side == EdgeSide.EAST || piece.edge?.side == EdgeSide.WEST
        val (hw, hh) = if (vertical) s.height / 2 to s.width / 2 else s.width / 2 to s.height / 2
        val minHalf = proj.cell * 0.12f
        kotlin.math.abs(p.x - c.x) <= maxOf(hw, minHalf) && kotlin.math.abs(p.y - c.y) <= maxOf(hh, minHalf)
    }

/** Pixel centres of tokens; several tokens on one square are spread around it. */
fun squareTokenCenters(proj: BattleProjection, tokens: List<SquareToken>): List<Pair<SquareToken, Offset>> =
    tokens.groupBy { it.square }.flatMap { (sq, on) ->
        on.mapIndexed { i, t ->
            val spread = if (on.size == 1) Offset.Zero else Offset(cos(2 * PI * i / on.size).toFloat(), sin(2 * PI * i / on.size).toFloat()) * (proj.cell * 0.18f)
            t to (proj.center(sq) + spread + Offset(-proj.cell * 0.22f, proj.cell * 0.22f))
        }
    }

fun squareEnemyCenters(proj: BattleProjection, enemies: List<SquareEnemy>): List<Pair<SquareEnemy, Offset>> =
    enemies.groupBy { it.square }.flatMap { (sq, on) -> on.mapIndexed { i, e -> e to (squareEnemyCenter(proj, sq) - Offset(proj.cell * 0.26f * i, 0f)) } }

fun squareEnemyAt(proj: BattleProjection, enemies: List<SquareEnemy>, p: Offset): SquareEnemy? =
    squareEnemyCenters(proj, enemies).minByOrNull { (it.second - p).getDistance() }?.takeIf { (it.second - p).getDistance() <= proj.cell * 0.15f }?.first

fun squareEnemyCenter(proj: BattleProjection, sq: GridCoord) = proj.center(sq) + Offset(proj.cell * 0.25f, -proj.cell * 0.25f)

fun squareTokenAt(proj: BattleProjection, tokens: List<SquareToken>, p: Offset): SquareToken? =
    squareTokenCenters(proj, tokens).minByOrNull { (it.second - p).getDistance() }
        ?.takeIf { (it.second - p).getDistance() <= proj.cell * 0.15f }?.first

/**
 * Draws a [BattleScene]. With [projection] == null the boards are fitted into the canvas;
 * the projection used is reported through [onProjection].
 */
@Composable
fun BattleCanvas(
    scene: BattleScene,
    highlight: Color,
    modifier: Modifier = Modifier,
    projection: ((Size) -> BattleProjection)? = null,
    onProjection: (BattleProjection) -> Unit = {},
) {
    val textMeasurer = rememberTextMeasurer()
    Canvas(modifier) {
        val proj = projection?.invoke(size) ?: BattleProjection.fit(scene.squares, size.width, size.height, 12.dp.toPx(), 260.dp.toPx())
        onProjection(proj)
        if (scene.showGrid) drawSquareGrid(proj)

        for (board in scene.boards) drawBoard(proj, board, highlight)

        for (sq in scene.errorSquares) drawRect(MapColors.Error, proj.topLeft(sq), Size(proj.cell, proj.cell), alpha = 0.4f)
        for (sq in scene.highlightSquares) {
            val inset = proj.cell * 0.08f
            drawRect(highlight, proj.topLeft(sq) + Offset(inset, inset), Size(proj.cell - 2 * inset, proj.cell - 2 * inset), style = Stroke(3.dp.toPx()))
        }

        for (piece in scene.pieces) {
            val c = pieceCenter(proj, piece) ?: continue
            val s = pieceSize(proj, scene, piece)
            val vertical = piece.upright || piece.edge?.side == EdgeSide.EAST || piece.edge?.side == EdgeSide.WEST
            rotate(if (vertical) 90f else 0f, c) {
                val topLeft = c - Offset(s.width / 2, s.height / 2)
                val pic = piece.picture
                if (pic != null) {
                    drawImage(pic, IntOffset.Zero, IntSize(pic.width, pic.height), IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
                        IntSize(s.width.roundToInt().coerceAtLeast(1), s.height.roundToInt().coerceAtLeast(1)), alpha = piece.alpha)
                } else {
                    drawRoundRect(MapColors.Piece, topLeft, s, androidx.compose.ui.geometry.CornerRadius(s.height / 4), alpha = piece.alpha)
                    drawRoundRect(Color.Black, topLeft, s, androidx.compose.ui.geometry.CornerRadius(s.height / 4), alpha = 0.5f * piece.alpha, style = Stroke(1.dp.toPx()))
                }
                if (piece.emphasized || piece.selected) {
                    val pad = 3.dp.toPx()
                    drawRoundRect(highlight, topLeft - Offset(pad, pad), Size(s.width + 2 * pad, s.height + 2 * pad),
                        androidx.compose.ui.geometry.CornerRadius(pad * 2), style = Stroke((if (piece.selected) 4 else 3).dp.toPx()))
                }
            }
            if (piece.picture == null) {
                // Name label for pieces without picture: along the piece, shrunk to its length
                val s0 = pieceSize(proj, scene, piece)
                var font = proj.cell / 9f
                var m = textMeasurer.measure(piece.name, TextStyle(fontSize = font.toSp(), color = MapColors.Label, fontWeight = FontWeight.Bold))
                if (m.size.width > s0.width * 0.95f) {
                    font *= s0.width * 0.95f / m.size.width
                    m = textMeasurer.measure(piece.name, TextStyle(fontSize = font.toSp(), color = MapColors.Label, fontWeight = FontWeight.Bold))
                }
                rotate(if (vertical) 90f else 0f, c) { drawText(m, topLeft = c - Offset(m.size.width / 2f, m.size.height / 2f)) }
            }
            if (piece.special) drawSpecialMarker(c + Offset(minOf(s.width, proj.cell * 0.5f) / 2, -minOf(s.height, proj.cell * 0.5f) / 2), proj.cell * 0.07f)
        }

        for ((sq, marker) in scene.markers) {
            val r = proj.rect(sq)
            drawFieldMarker(textMeasurer, marker, r.center, proj.cell * 0.09f, Offset(-proj.cell * 0.3f, proj.cell * 0.32f))
        }

        scene.heroStart?.let { sq ->
            val r = proj.rect(sq)
            drawRect(MapColors.HeroStart, topLeft = r.topLeft + Offset(3f, 3f), size = Size(r.width - 6f, r.height - 6f), style = Stroke(4.dp.toPx()))
            val m = textMeasurer.measure("Start", TextStyle(fontSize = (proj.cell * 0.11f).toSp(), color = Color.White, fontWeight = FontWeight.Bold))
            val tl = Offset(r.center.x - m.size.width / 2f, r.bottom - m.size.height - 8f)
            drawRect(MapColors.HeroStart, topLeft = tl - Offset(4f, 1f), size = Size(m.size.width + 8f, m.size.height + 2f))
            drawText(m, topLeft = tl)
        }
        for ((enemy, c) in squareEnemyCenters(proj, scene.enemies)) {
            val r = proj.cell * 0.12f
            if (enemy.selected) drawCircle(highlight, r * 1.4f, c, style = Stroke(3.dp.toPx()))
            drawEnemyMarker(c, r, enemy.elite)
            val m = textMeasurer.measure(enemy.count.toString(), TextStyle(fontSize = (r * 0.9f).toSp(), color = Color.White, fontWeight = FontWeight.Bold))
            drawText(m, topLeft = Offset(c.x - m.size.width / 2f, c.y - m.size.height / 2f))
            enemy.banner?.let { drawBanner(textMeasurer, it, c + Offset(r * 0.9f, r * 0.9f), r * 0.55f, enemy.elite) }
        }
        for ((token, c) in squareTokenCenters(proj, scene.tokens)) {
            val r = proj.cell * 0.11f
            if (token.selected) drawCircle(highlight, r * 1.45f, c, style = Stroke(3.dp.toPx()))
            drawCircle(Color.White, r * 1.12f, c)
            drawCircle(MapColors.token(token.type), r, c)
            val m = textMeasurer.measure(token.letter, TextStyle(fontSize = (r * 1.1f).toSp(), color = Color.White, fontWeight = FontWeight.Bold))
            drawText(m, topLeft = Offset(c.x - m.size.width / 2f, c.y - m.size.height / 2f))
        }

        // Board labels
        for (board in scene.boards) {
            if (board.label.isBlank()) continue
            val m = textMeasurer.measure(board.label, TextStyle(fontSize = (proj.cell / 8f).toSp(), fontWeight = FontWeight.Bold, color = MapColors.Label.copy(alpha = board.alpha)))
            val tl = proj.topLeft(board.origin) + Offset(4.dp.toPx(), 4.dp.toPx())
            val pad = 3.dp.toPx()
            drawRoundRect(Color.White.copy(alpha = 0.75f * board.alpha), tl - Offset(pad, pad / 2), Size(m.size.width + 2 * pad, m.size.height + pad),
                androidx.compose.ui.geometry.CornerRadius(pad * 2))
            drawText(m, topLeft = tl)
        }
    }
}

private fun DrawScope.drawBoard(proj: BattleProjection, board: BattleBoardView, highlight: Color) {
    val topLeft = proj.topLeft(board.origin)
    val size = Size(board.rotatedWidth * proj.cell, board.rotatedHeight * proj.cell)
    val center = topLeft + Offset(size.width / 2, size.height / 2)
    val pic = board.picture
    if (pic != null) {
        val srcW = board.width * pic.cellPx
        val srcH = board.height * pic.cellPx
        val dstW = board.width * proj.cell
        val dstH = board.height * proj.cell
        rotate(board.rotation * 90f, center) {
            drawImage(
                pic.bitmap,
                IntOffset(pic.originX.roundToInt(), pic.originY.roundToInt()), IntSize(srcW.roundToInt(), srcH.roundToInt()),
                IntOffset((center.x - dstW / 2).roundToInt(), (center.y - dstH / 2).roundToInt()), IntSize(dstW.roundToInt(), dstH.roundToInt()),
                alpha = board.alpha,
            )
        }
    } else {
        drawRect(board.fill, topLeft, size, alpha = board.alpha)
    }
    // Field borders (thick) and square lines inside fields (thin), only drawn without picture.
    val fieldOf = board.fields.flatMap { f -> f.map { it to f } }.toMap()
    val thin = 1.dp.toPx()
    val thick = 2.5.dp.toPx()
    for ((sq, field) in fieldOf) {
        for (side in EdgeSide.entries) {
            val other = fieldOf[sq.neighbor(side)]
            if (other === field) {
                if (pic == null) proj.edgeLine(GridEdge(sq.x, sq.y, side)).let { (a, b) -> drawLine(Color.Black, a, b, thin, alpha = 0.15f * board.alpha) }
            } else if (pic == null || other == null) {
                proj.edgeLine(GridEdge(sq.x, sq.y, side)).let { (a, b) -> drawLine(MapColors.TileBorder, a, b, if (other == null) thick else thick * 0.8f, alpha = board.alpha) }
            }
        }
    }
    if (board.emphasized) drawRect(highlight, topLeft, size, style = Stroke(4.dp.toPx()))
}

/** Gold four-pointed star marking scenario-specific terrain. */
private fun DrawScope.drawSpecialMarker(c: Offset, r: Float) {
    val star = Path().apply {
        for (i in 0 until 8) {
            val a = PI / 4 * i - PI / 2
            val rr = if (i % 2 == 0) r else r * 0.4f
            val p = c + Offset((cos(a) * rr).toFloat(), (sin(a) * rr).toFloat())
            if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
        close()
    }
    drawCircle(Color.White, r * 0.9f, c, alpha = 0.85f)
    drawPath(star, MapColors.Special)
}

private fun DrawScope.drawSquareGrid(proj: BattleProjection) {
    val a = proj.squareAt(Offset.Zero)
    val b = proj.squareAt(Offset(size.width, size.height))
    for (x in a.x..b.x + 1) {
        val px = proj.topLeft(GridCoord(x, 0)).x
        drawLine(MapColors.Grid, Offset(px, 0f), Offset(px, size.height))
    }
    for (y in a.y..b.y + 1) {
        val py = proj.topLeft(GridCoord(0, y)).y
        drawLine(MapColors.Grid, Offset(0f, py), Offset(size.width, py))
    }
}
