package io.github.aljadvb.worldbuilder.hexmap

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlin.math.sin

/** How a place appears on the campaign map. */
enum class PlaceState { OPEN, CURRENT, VICTORY, DEFEAT, NEUTRAL }

data class MapPlace(val id: String, val name: String, val x: Float, val y: Float, val state: PlaceState = PlaceState.OPEN, val selected: Boolean = false)

/**
 * Places plus the route travelled so far (place IDs in order). With a [background] (the user's
 * own map picture from the private catalog) that picture is drawn instead of the schematic map.
 */
data class CampaignMapScene(val places: List<MapPlace>, val route: List<String> = emptyList(), val background: ImageBitmap? = null) {
    val aspect: Float get() = background?.let { it.width.toFloat() / it.height } ?: (4f / 3f)
}

/** Pixel rectangle of the map inside a canvas: kept at the map's aspect, centred. */
fun campaignMapRect(size: Size, aspect: Float = 4f / 3f): Rect {
    val w = min(size.width, size.height * aspect)
    val h = w / aspect
    return Rect(Offset((size.width - w) / 2f, (size.height - h) / 2f), Size(w, h))
}

fun placeCenter(rect: Rect, x: Float, y: Float) = Offset(rect.left + x * rect.width, rect.top + y * rect.height)

/** Relative map coordinates (0–1) of pixel [p]. */
fun mapCoordinates(size: Size, scene: CampaignMapScene, p: Offset): Pair<Float, Float> {
    val r = campaignMapRect(size, scene.aspect)
    return ((p.x - r.left) / r.width).coerceIn(0f, 1f) to ((p.y - r.top) / r.height).coerceIn(0f, 1f)
}

/** The place under [p], if any. */
fun placeAt(size: Size, scene: CampaignMapScene, p: Offset): MapPlace? {
    val r = campaignMapRect(size, scene.aspect)
    val radius = r.width * 0.025f
    return scene.places.minByOrNull { (placeCenter(r, it.x, it.y) - p).getDistance() }
        ?.takeIf { (placeCenter(r, it.x, it.y) - p).getDistance() <= radius * 1.8f }
}

private object MapInk {
    val Parchment = Color(0xFFF1E4C3)
    val ParchmentDark = Color(0xFFE2CFA1)
    val Sea = Color(0xFFB9CBD0)
    val Ink = Color(0xFF5B4630)
    val Mountain = Color(0xFF8A7558)
    val Forest = Color(0xFF7E9465)
    val River = Color(0xFF7FA0B0)
    val Route = Color(0xFF8B2E1F)
    val Victory = Color(0xFF2E7D32)
    val Defeat = Color(0xFFB3261E)
    val Neutral = Color(0xFF6D6D6D)
    val Open = Color(0xFFFFFBF0)
}

/**
 * A schematic, self-drawn map of Middle-earth (no foreign artwork): coast, the great mountain
 * chains, rivers and forests, a few region names, and the campaign's places on top.
 */
@Composable
fun CampaignMapCanvas(scene: CampaignMapScene, highlight: Color, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        val r = campaignMapRect(size, scene.aspect)
        fun at(x: Float, y: Float) = placeCenter(r, x, y)
        val bg = scene.background
        if (bg != null) {
            drawImage(bg, IntOffset.Zero, IntSize(bg.width, bg.height), IntOffset(r.left.roundToInt(), r.top.roundToInt()), IntSize(r.width.roundToInt(), r.height.roundToInt()))
        } else {
            drawLandscape(r, ::at)
        }

        // Region names (schematic map only)
        if (bg == null) {
        val regionStyle = TextStyle(fontSize = (r.width / 55f).toSp(), color = MapInk.Ink.copy(alpha = 0.55f), fontStyle = FontStyle.Italic, letterSpacing = (r.width / 400f).toSp())
        for ((name, x, y) in REGIONS) {
            val m = measurer.measure(name, regionStyle)
            drawText(m, topLeft = at(x, y) - Offset(m.size.width / 2f, m.size.height / 2f))
        }
        }

        // Route travelled so far
        val byId = scene.places.associateBy { it.id }
        val route = scene.route.mapNotNull { byId[it] }
        for ((a, b) in route.zipWithNext()) {
            drawLine(MapInk.Route, at(a.x, a.y), at(b.x, b.y), strokeWidth = r.width / 260f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(r.width / 90f, r.width / 140f)))
        }

        // Places
        // Smaller markers on a detailed own map picture.
        val radius = r.width * (if (bg != null) 0.009f else 0.016f)
        val nameStyle = TextStyle(fontSize = (r.width / (if (bg != null) 75f else 48f)).toSp(), color = MapInk.Ink, fontWeight = FontWeight.Bold)
        for (p in scene.places) {
            val c = at(p.x, p.y)
            val fill = when (p.state) {
                PlaceState.VICTORY -> MapInk.Victory
                PlaceState.DEFEAT -> MapInk.Defeat
                PlaceState.NEUTRAL -> MapInk.Neutral
                PlaceState.CURRENT, PlaceState.OPEN -> MapInk.Open
            }
            if (p.state == PlaceState.CURRENT) drawCircle(highlight, radius * 2.0f, c, style = Stroke(radius * 0.5f))
            if (p.selected) drawCircle(highlight, radius * 2.6f, c, style = Stroke(2.dp.toPx()))
            drawCircle(fill, radius, c)
            drawCircle(MapInk.Ink, radius, c, style = Stroke(radius * 0.3f))
            if (p.state == PlaceState.VICTORY || p.state == PlaceState.DEFEAT || p.state == PlaceState.NEUTRAL) {
                // check mark
                val k = Path().apply {
                    moveTo(c.x - radius * 0.5f, c.y); lineTo(c.x - radius * 0.1f, c.y + radius * 0.45f); lineTo(c.x + radius * 0.55f, c.y - radius * 0.45f)
                }
                drawPath(k, Color.White, style = Stroke(radius * 0.3f))
            }
            val m = measurer.measure(p.name, nameStyle)
            val tl = c + Offset(-m.size.width / 2f, radius * 1.4f)
            drawRoundRect(MapInk.Parchment.copy(alpha = 0.85f), tl - Offset(4f, 1f), Size(m.size.width + 8f, m.size.height + 2f), androidx.compose.ui.geometry.CornerRadius(6f))
            drawText(m, topLeft = tl)
        }
    }
}

private val REGIONS = listOf(
    Triple("ERIADOR", 0.25f, 0.30f),
    Triple("Auenland", 0.17f, 0.42f),
    Triple("Nebelgebirge", 0.47f, 0.18f),
    Triple("Düsterwald", 0.66f, 0.27f),
    Triple("Rohan", 0.50f, 0.66f),
    Triple("Gondor", 0.62f, 0.86f),
    Triple("Mordor", 0.86f, 0.80f),
    Triple("Rhovanion", 0.82f, 0.15f),
)

/** Coast, mountains, rivers and forests in relative coordinates. */
private fun DrawScope.drawLandscape(r: Rect, at: (Float, Float) -> Offset) {
    drawRect(MapInk.Parchment, r.topLeft, r.size)
    // darker frame
    drawRect(MapInk.ParchmentDark, r.topLeft, r.size, style = Stroke(r.width / 60f))

    // Western sea
    val coast = listOf(0f to 0f, 0.09f to 0f, 0.07f to 0.12f, 0.10f to 0.22f, 0.06f to 0.30f, 0.09f to 0.40f, 0.05f to 0.52f, 0.11f to 0.62f,
        0.18f to 0.72f, 0.24f to 0.80f, 0.33f to 0.86f, 0.42f to 0.93f, 0.48f to 1f, 0f to 1f)
    drawPath(Path().apply { coast.forEachIndexed { i, (x, y) -> at(x, y).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }; close() }, MapInk.Sea)
    // Bay of Belfalas outline continues east
    drawPath(Path().apply {
        listOf(0.48f to 1f, 0.55f to 0.95f, 0.62f to 0.97f, 0.66f to 1f).forEachIndexed { i, (x, y) -> at(x, y).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }
        close()
    }, MapInk.Sea)

    // Rivers
    fun river(vararg pts: Pair<Float, Float>) {
        val path = Path()
        pts.forEachIndexed { i, (x, y) -> at(x, y).let { if (i == 0) path.moveTo(it.x, it.y) else path.lineTo(it.x, it.y) } }
        drawPath(path, MapInk.River, style = Stroke(r.width / 280f))
    }
    river(0.56f to 0.05f, 0.57f to 0.20f, 0.58f to 0.35f, 0.60f to 0.50f, 0.64f to 0.62f, 0.66f to 0.74f, 0.65f to 0.86f, 0.60f to 0.96f) // great river
    river(0.20f to 0.15f, 0.22f to 0.30f, 0.21f to 0.45f, 0.18f to 0.60f, 0.13f to 0.66f) // western river
    river(0.36f to 0.20f, 0.33f to 0.40f, 0.28f to 0.58f, 0.22f to 0.70f)
    river(0.48f to 0.45f, 0.52f to 0.58f, 0.58f to 0.64f, 0.64f to 0.64f)

    // Mountain chains
    fun mountains(vararg pts: Pair<Float, Float>, size: Float = 0.022f) {
        for ((a, b) in pts.toList().zipWithNext()) {
            val steps = (((b.first - a.first) * (b.first - a.first) + (b.second - a.second) * (b.second - a.second)).let { kotlin.math.sqrt(it) } / (size * 0.9f)).toInt().coerceAtLeast(1)
            for (i in 0 until steps) {
                val t = i / steps.toFloat()
                val x = a.first + (b.first - a.first) * t
                val y = a.second + (b.second - a.second) * t + (sin(i * 2.1) * size * 0.25).toFloat()
                val c = at(x, y)
                val w = r.width * size
                val tri = Path().apply { moveTo(c.x - w * 0.6f, c.y + w * 0.4f); lineTo(c.x, c.y - w * 0.6f); lineTo(c.x + w * 0.6f, c.y + w * 0.4f) }
                drawPath(tri, MapInk.ParchmentDark)
                drawPath(tri, MapInk.Mountain, style = Stroke(r.width / 500f))
            }
        }
    }
    mountains(0.47f to 0.04f, 0.46f to 0.20f, 0.48f to 0.36f, 0.45f to 0.50f) // misty mountains
    mountains(0.52f to 0.05f, 0.75f to 0.06f) // northern grey mountains
    mountains(0.40f to 0.74f, 0.52f to 0.76f, 0.62f to 0.73f) // white mountains
    mountains(0.74f to 0.66f, 0.95f to 0.66f, size = 0.018f) // ash mountains
    mountains(0.74f to 0.68f, 0.73f to 0.90f, 0.92f to 0.92f, size = 0.018f) // shadow mountains
    mountains(0.12f to 0.30f, 0.14f to 0.38f, size = 0.016f) // western hills

    // Forests
    fun forest(cx: Float, cy: Float, rx: Float, ry: Float) {
        val n = (rx * ry * 9000).toInt().coerceIn(8, 160)
        for (i in 0 until n) {
            val a = i * 2.399963 // golden angle
            val d = kotlin.math.sqrt(i / n.toDouble())
            val c = at(cx + (kotlin.math.cos(a) * d * rx).toFloat(), cy + (kotlin.math.sin(a) * d * ry).toFloat())
            drawCircle(MapInk.Forest.copy(alpha = 0.7f), r.width * 0.009f, c)
        }
    }
    forest(0.67f, 0.28f, 0.07f, 0.16f) // great forest east of the river
    forest(0.53f, 0.56f, 0.03f, 0.04f)
    forest(0.26f, 0.48f, 0.035f, 0.03f)
    forest(0.72f, 0.58f, 0.03f, 0.025f)
    // fold lines of the parchment
    val fold = MapInk.ParchmentDark.copy(alpha = 0.35f)
    drawLine(fold, at(0.5f, 0f), at(0.5f, 1f), strokeWidth = 1f)
    drawLine(fold, at(0f, 0.5f), at(1f, 0.5f), strokeWidth = 1f)
}

/** Places of a campaign as drawn for the given progress. */
fun campaignMapScene(
    campaign: io.github.aljadvb.worldbuilder.model.Campaign,
    completed: List<Pair<String, String>>,
    current: String?,
    selected: String? = null,
    background: ImageBitmap? = null,
    /** Possible next adventures (either-or choice in camp). */
    options: List<String> = emptyList(),
): CampaignMapScene {
    val stateByPlace = mutableMapOf<String, PlaceState>()
    for ((scenarioId, resolutionId) in completed) {
        val s = campaign.scenario(scenarioId) ?: continue
        val place = s.place ?: continue
        stateByPlace[place] = when (s.resolution(resolutionId)?.outcome) {
            io.github.aljadvb.worldbuilder.model.Outcome.VICTORY -> PlaceState.VICTORY
            io.github.aljadvb.worldbuilder.model.Outcome.DEFEAT -> PlaceState.DEFEAT
            else -> PlaceState.NEUTRAL
        }
    }
    current?.let { campaign.scenario(it)?.place }?.let { stateByPlace[it] = PlaceState.CURRENT }
    val route = (completed.map { it.first } + listOfNotNull(current)).mapNotNull { campaign.scenario(it)?.place }
        .fold(emptyList<String>()) { acc, p -> if (acc.lastOrNull() == p) acc else acc + p }
    return CampaignMapScene(
        // Only places the group has reached, its current adventure and the possible next ones are shown.
        places = campaign.places.filter { it.id in stateByPlace || it.id in options.mapNotNull { o -> campaign.scenario(o)?.place } }.map { MapPlace(it.id, it.name, it.x.toFloat(), it.y.toFloat(), stateByPlace[it.id] ?: PlaceState.OPEN, it.id == selected) },
        route = route,
        background = background,
    )
}
