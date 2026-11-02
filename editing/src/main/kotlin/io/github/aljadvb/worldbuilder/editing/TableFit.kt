package io.github.aljadvb.worldbuilder.editing

import io.github.aljadvb.worldbuilder.model.HexCoord
import io.github.aljadvb.worldbuilder.model.HexOrientation
import io.github.aljadvb.worldbuilder.model.MaterialCatalog
import io.github.aljadvb.worldbuilder.model.Scenario
import io.github.aljadvb.worldbuilder.model.battleMap
import io.github.aljadvb.worldbuilder.model.battleSegments
import kotlin.math.sqrt

/** Size of the real table the map is laid out on. */
data class TableSize(val widthCm: Double, val heightCm: Double) {
    /** True if a map of [extent] fits, in either orientation. */
    fun fits(extent: MapExtent): Boolean {
        val (a, b) = listOf(widthCm, heightCm).sorted()
        val (x, y) = listOf(extent.widthCm, extent.heightCm).sorted()
        return x <= a + 1e-6 && y <= b + 1e-6
    }

    /** Rough number of hex cells that fit on the table (with room for gaps and odd shapes). */
    fun cellCapacity(hexSizeMm: Double): Int {
        val r = hexSizeMm / 10.0
        val hexArea = 1.5 * sqrt(3.0) * r * r
        return (widthCm * heightCm / hexArea * 0.55).toInt()
    }

    override fun toString() = "%.0f × %.0f cm".format(java.util.Locale.GERMANY, widthCm, heightCm)

    companion object {
        val DEFAULT = TableSize(120.0, 80.0)
    }
}

/** Bounding box of a map on the table. */
data class MapExtent(val widthCm: Double, val heightCm: Double) {
    override fun toString() = "%.0f × %.0f cm".format(java.util.Locale.GERMANY, widthCm, heightCm)

    companion object {
        val EMPTY = MapExtent(0.0, 0.0)

        /** Extent of the given map cells with real hex size [hexSizeMm] (centre to corner). */
        fun of(cells: Collection<HexCoord>, orientation: HexOrientation, hexSizeMm: Double): MapExtent {
            if (cells.isEmpty()) return EMPTY
            val s3 = sqrt(3.0)
            // unit centres (radius 1) and half extents of one hex
            val (xs, ys, hx, hy) = when (orientation) {
                HexOrientation.FLAT -> Quad(cells.map { 1.5 * it.q }, cells.map { s3 * (it.r + it.q / 2.0) }, 1.0, s3 / 2)
                HexOrientation.POINTY -> Quad(cells.map { s3 * (it.q + it.r / 2.0) }, cells.map { 1.5 * it.r }, s3 / 2, 1.0)
            }
            val r = hexSizeMm / 10.0
            return MapExtent((xs.max() - xs.min() + 2 * hx) * r, (ys.max() - ys.min() + 2 * hy) * r)
        }
    }

    private data class Quad(val a: List<Double>, val b: List<Double>, val c: Double, val d: Double)
}

/**
 * Extent of everything placed in this scenario: all travel chapters lie on the table together,
 * and so do all battle boards. A battle map replaces the travel map, so the larger one counts.
 */
fun Scenario.mapExtent(catalog: MaterialCatalog): MapExtent {
    val travel = MapExtent.of(placements.flatMap { it.mapCells(catalog) }, catalog.hexOrientation, catalog.hexSizeMm)
    val battle = battleExtent(catalog)
    return if (battle.widthCm * battle.heightCm > travel.widthCm * travel.heightCm) battle else travel
}

/** Extent of all battle boards of this scenario. */
fun Scenario.battleExtent(catalog: MaterialCatalog): MapExtent {
    val cellMm = boardPlacements.firstNotNullOfOrNull { catalog.battleBoard(it.board)?.sides?.get(it.side)?.cellSizeMm } ?: return MapExtent.EMPTY
    // Layouts that are cleared away never lie on the table together: the largest counts.
    return battleSegments().map { seg -> battleMap(catalog, seg.map { it.id }.toSet()).extent() }
        .map { (w, h) -> MapExtent(w * cellMm / 10.0, h * cellMm / 10.0) }
        .maxByOrNull { it.widthCm * it.heightCm } ?: MapExtent.EMPTY
}
