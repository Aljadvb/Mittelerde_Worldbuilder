package io.github.aljadvb.worldbuilder.generator

import io.github.aljadvb.worldbuilder.editing.MapExtent
import io.github.aljadvb.worldbuilder.editing.TableSize
import io.github.aljadvb.worldbuilder.editing.localCells
import io.github.aljadvb.worldbuilder.editing.mapExtent
import io.github.aljadvb.worldbuilder.model.HexCoord
import io.github.aljadvb.worldbuilder.model.MaterialCatalog
import io.github.aljadvb.worldbuilder.model.Scenario
import io.github.aljadvb.worldbuilder.model.TilePlacement

/** Result of [AutoLayout.layout]: the laid-out scenario, its size on the table and whether it fits. */
data class LayoutResult(val scenario: Scenario, val extent: MapExtent, val fits: Boolean)

/**
 * Places the tiles of a scenario on the map without overlaps: chapter by chapter, each
 * tile is attached edge-to-edge to tiles already placed, preferring many shared edges,
 * a compact map and no rotation. With a [TableSize], only positions that keep the whole
 * map (all chapters together) on the table are used. Deterministic.
 */
object AutoLayout {
    fun layout(scenario: Scenario, catalog: MaterialCatalog, table: TableSize? = null): LayoutResult {
        val occupied = mutableSetOf<HexCoord>()
        val placed = mutableMapOf<String, TilePlacement>()
        val placedList = mutableListOf<TilePlacement>()
        var previousChapterCells = emptySet<HexCoord>()

        for (chapter in scenario.chapters) {
            val chapterCells = mutableSetOf<HexCoord>()
            for (p in chapter.tiles) {
                val local = p.localCells(catalog).ifEmpty { listOf(HexCoord.ORIGIN) }
                val best = if (occupied.isEmpty()) {
                    p.copy(position = HexCoord.ORIGIN, rotation = 0)
                } else {
                    val attachTo = chapterCells.ifEmpty { previousChapterCells.ifEmpty { occupied } }
                    // Heroes must be able to walk in: prefer at least one open (non-black) edge to the map.
                    bestPlacement(p, local, occupied, attachTo, catalog, table, placedList, needOpen = true)
                        ?: bestPlacement(p, local, occupied, attachTo, catalog, null, placedList, needOpen = true)
                        ?: bestPlacement(p, local, occupied, attachTo, catalog, table, placedList, needOpen = false)
                        ?: bestPlacement(p, local, occupied, attachTo, catalog, null, placedList, needOpen = false)!!
                }
                val cells = local.map(best::toMap)
                occupied += cells; chapterCells += cells
                placed[p.id] = best
                placedList += best
            }
            if (chapterCells.isNotEmpty()) previousChapterCells = chapterCells
        }
        val laid = scenario.copy(chapters = scenario.chapters.map { ch -> ch.copy(tiles = ch.tiles.map { placed[it.id] ?: it }) })
        val extent = laid.mapExtent(catalog)
        return LayoutResult(laid, extent, table?.fits(extent) ?: true)
    }

    private fun bestPlacement(
        p: TilePlacement,
        local: List<HexCoord>,
        occupied: Set<HexCoord>,
        attachTo: Set<HexCoord>,
        catalog: MaterialCatalog,
        table: TableSize?,
        placedList: List<TilePlacement> = emptyList(),
        needOpen: Boolean = false,
    ): TilePlacement? {
        val center = centroid(occupied)
        val frontier = attachTo.flatMap { it.neighbors() }.filter { it !in occupied }.toSet()
        var best: TilePlacement? = null
        var bestScore = Double.NEGATIVE_INFINITY
        for (rotation in 0..5) {
            val rotated = local.map { it.rotateClockwise(rotation) }
            for (target in frontier) for (cell in rotated) {
                val position = target - cell
                val cells = rotated.map { it + position }
                if (cells.any { it in occupied }) continue
                val contacts = cells.sumOf { c -> c.neighbors().count { it in attachTo } }
                if (contacts == 0) continue
                val open = if (placedList.isEmpty()) 1 else io.github.aljadvb.worldbuilder.model.openContacts(p.copy(position = position, rotation = rotation), placedList, catalog)
                if (needOpen && open == 0) continue
                var score = contacts * 3.0 + open * 2.0 - cells.sumOf { it.distanceTo(center) }.toDouble() / cells.size - (if (rotation == 0) 0.0 else 2.5)
                if (table != null) {
                    val extent = MapExtent.of(occupied + cells, catalog.hexOrientation, catalog.hexSizeMm)
                    if (!table.fits(extent)) continue
                    // keep room: prefer maps that use a small share of the table
                    val (a, b) = listOf(table.widthCm, table.heightCm).sorted()
                    val (x, y) = listOf(extent.widthCm, extent.heightCm).sorted()
                    score -= 4.0 * maxOf(x / a, y / b)
                }
                if (score > bestScore) {
                    bestScore = score
                    best = p.copy(position = position, rotation = rotation)
                }
            }
        }
        if (best == null && table == null && !needOpen) {
            return p.copy(position = HexCoord(occupied.maxOf { it.q } + 3, 0), rotation = 0)
        }
        return best
    }

    private fun centroid(cells: Set<HexCoord>): HexCoord =
        HexCoord(Math.round(cells.map { it.q }.average()).toInt(), Math.round(cells.map { it.r }.average()).toInt())
}
