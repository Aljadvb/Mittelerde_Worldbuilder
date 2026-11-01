package io.github.aljadvb.worldbuilder.model

import kotlinx.serialization.Serializable

/**
 * A hex cell in axial coordinates.
 *
 * Convention: cube coordinates are (x = q, z = r, y = -q - r). With r growing
 * "downwards" on screen, [rotateClockwise] turns a cell by 60° clockwise around
 * the origin. The drawing orientation (pointy/flat top) is a rendering concern only.
 */
@Serializable
data class HexCoord(val q: Int, val r: Int) {
    val s: Int get() = -q - r

    operator fun plus(other: HexCoord) = HexCoord(q + other.q, r + other.r)
    operator fun minus(other: HexCoord) = HexCoord(q - other.q, r - other.r)

    /** Rotates this cell around the origin by [steps] × 60° clockwise. Negative steps rotate counter-clockwise. */
    fun rotateClockwise(steps: Int): HexCoord {
        var result = this
        repeat(Math.floorMod(steps, 6)) { result = HexCoord(q = -result.r, r = -result.s) }
        return result
    }

    fun neighbors(): List<HexCoord> = DIRECTIONS.map { this + it }

    fun distanceTo(other: HexCoord): Int {
        val d = this - other
        return maxOf(kotlin.math.abs(d.q), kotlin.math.abs(d.r), kotlin.math.abs(d.s))
    }

    override fun toString() = "($q,$r)"

    companion object {
        val ORIGIN = HexCoord(0, 0)
        val DIRECTIONS = listOf(
            HexCoord(1, 0), HexCoord(0, 1), HexCoord(-1, 1),
            HexCoord(-1, 0), HexCoord(0, -1), HexCoord(1, -1),
        )
    }
}
