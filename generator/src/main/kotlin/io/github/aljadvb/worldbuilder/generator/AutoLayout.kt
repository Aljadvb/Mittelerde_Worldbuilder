package io.github.aljadvb.worldbuilder.generator

import io.github.aljadvb.worldbuilder.editing.localCells
import io.github.aljadvb.worldbuilder.model.HexCoord
import io.github.aljadvb.worldbuilder.model.MaterialCatalog
import io.github.aljadvb.worldbuilder.model.Scenario
import io.github.aljadvb.worldbuilder.model.TilePlacement

/**
 * Places the tiles of a scenario on the map without overlaps: chapter by chapter, each
 * tile is attached edge-to-edge to tiles already placed, preferring many shared edges,
 * a compact map and no rotation. Deterministic.
 */
object AutoLayout {
    fun layout(scenario: Scenario, catalog: MaterialCatalog): Scenario {
        val occupied = mutableSetOf<HexCoord>()
        val placed = mutableMapOf<String, TilePlacement>()
        var previousChapterCells = emptySet<HexCoord>()

        for (chapter in scenario.chapters) {
            val chapterCells = mutableSetOf<HexCoord>()
            for (p in chapter.tiles) {
                val local = p.localCells(catalog).ifEmpty { listOf(HexCoord.ORIGIN) }
                val best = if (occupied.isEmpty()) {
                    p.copy(position = HexCoord.ORIGIN, rotation = 0)
                } else {
                    bestPlacement(p, local, occupied, attachTo = chapterCells.ifEmpty { previousChapterCells.ifEmpty { occupied } })
                }
                val cells = local.map(best::toMap)
                occupied += cells; chapterCells += cells
                placed[p.id] = best
            }
            if (chapterCells.isNotEmpty()) previousChapterCells = chapterCells
        }
        return scenario.copy(chapters = scenario.chapters.map { ch -> ch.copy(tiles = ch.tiles.map { placed[it.id] ?: it }) })
    }

    private fun bestPlacement(p: TilePlacement, local: List<HexCoord>, occupied: Set<HexCoord>, attachTo: Set<HexCoord>): TilePlacement {
        val center = centroid(occupied)
        // anchor candidates: around every free neighbour of the cells to attach to
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
                val spread = cells.sumOf { it.distanceTo(center) }.toDouble() / cells.size
                val score = contacts * 3.0 - spread - (if (rotation == 0) 0.0 else 2.5)
                if (score > bestScore) {
                    bestScore = score
                    best = p.copy(position = position, rotation = rotation)
                }
            }
        }
        return best ?: p.copy(position = HexCoord(occupied.maxOf { it.q } + 3, 0), rotation = 0)
    }

    private fun centroid(cells: Set<HexCoord>): HexCoord =
        HexCoord(Math.round(cells.map { it.q }.average()).toInt(), Math.round(cells.map { it.r }.average()).toInt())
}
