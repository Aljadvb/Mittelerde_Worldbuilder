package io.github.aljadvb.worldbuilder.hexmap

import androidx.compose.ui.geometry.Offset
import io.github.aljadvb.worldbuilder.model.HexCoord
import io.github.aljadvb.worldbuilder.model.HexOrientation
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Converts between axial hex coordinates and pixels. [size] is the hex radius in px. */
class HexProjection(val orientation: HexOrientation, val size: Float, val origin: Offset) {

    fun center(c: HexCoord): Offset = origin + unitCenter(orientation, c) * size

    fun corners(c: HexCoord): List<Offset> {
        val center = center(c)
        val start = if (orientation == HexOrientation.POINTY) 30.0 else 0.0
        return (0 until 6).map { i ->
            val a = (start + 60.0 * i) * PI / 180.0
            Offset(center.x + size * cos(a).toFloat(), center.y + size * sin(a).toFloat())
        }
    }

    /** The hex containing pixel [p]. */
    fun hexAt(p: Offset): HexCoord {
        val u = (p - origin) / size
        val (q, r) = when (orientation) {
            HexOrientation.POINTY -> Pair(SQRT3 / 3f * u.x - u.y / 3f, 2f / 3f * u.y)
            HexOrientation.FLAT -> Pair(2f / 3f * u.x, -u.x / 3f + SQRT3 / 3f * u.y)
        }
        return cubeRound(q, r)
    }

    companion object {
        private val SQRT3 = sqrt(3f)

        fun unitCenter(orientation: HexOrientation, c: HexCoord): Offset = when (orientation) {
            HexOrientation.POINTY -> Offset(SQRT3 * c.q + SQRT3 / 2f * c.r, 1.5f * c.r)
            HexOrientation.FLAT -> Offset(1.5f * c.q, SQRT3 / 2f * c.q + SQRT3 * c.r)
        }

        /** Projection that fits [cells] into a canvas of [width] × [height] px. */
        fun fit(orientation: HexOrientation, cells: Collection<HexCoord>, width: Float, height: Float, padding: Float, maxSize: Float): HexProjection {
            if (cells.isEmpty()) return HexProjection(orientation, maxSize, Offset(width / 2f, height / 2f))
            val centers = cells.map { unitCenter(orientation, it) }
            val minX = centers.minOf { it.x } - 1f
            val maxX = centers.maxOf { it.x } + 1f
            val minY = centers.minOf { it.y } - 1f
            val maxY = centers.maxOf { it.y } + 1f
            val scale = minOf((width - 2 * padding) / (maxX - minX), (height - 2 * padding) / (maxY - minY), maxSize)
            val origin = Offset(
                (width - (maxX - minX) * scale) / 2f - minX * scale,
                (height - (maxY - minY) * scale) / 2f - minY * scale,
            )
            return HexProjection(orientation, scale, origin)
        }

        private fun cubeRound(q: Float, r: Float): HexCoord {
            val s = -q - r
            var rq = q.roundToInt()
            var rr = r.roundToInt()
            val rs = s.roundToInt()
            val dq = abs(rq - q)
            val dr = abs(rr - r)
            val ds = abs(rs - s)
            if (dq > dr && dq > ds) rq = -rr - rs else if (dr > ds) rr = -rq - rs
            return HexCoord(rq, rr)
        }
    }
}
