package io.github.aljadvb.worldbuilder.model

/** A tile placement's cells on the map with the shape they come from. */
private class Placed(val placement: TilePlacement, val shape: TileSideShape) {
    val byMapCell: Map<HexCoord, HexCoord> = shape.cells.associateBy { placement.toMap(it) }
}

/**
 * True if heroes can move from map cell [a] of placement [pa] to the adjacent map cell of [pb]
 * in map direction [dir]: neither side may be a closed (black) border.
 */
private fun open(pa: Placed, a: HexCoord, dir: HexDir, pb: Placed, b: HexCoord): Boolean {
    val la = pa.byMapCell.getValue(a)
    val lb = pb.byMapCell.getValue(b)
    return pa.shape.isOpen(la, dir.rotated(-pa.placement.rotation)) &&
        pb.shape.isOpen(lb, dir.opposite.rotated(-pb.placement.rotation))
}

/** Number of open (passable) edges between each pair of touching placements. */
fun connections(placements: List<TilePlacement>, catalog: MaterialCatalog): Map<Pair<String, String>, Int> {
    val placed = placements.mapNotNull { p -> catalog.tile(p.tile)?.sides?.get(p.side)?.let { Placed(p, it) } }
    val owner = HashMap<HexCoord, Placed>()
    placed.forEach { pl -> pl.byMapCell.keys.forEach { owner[it] = pl } }
    val result = HashMap<Pair<String, String>, Int>()
    for (pl in placed) for (cell in pl.byMapCell.keys) for (dir in HexDir.entries) {
        val nb = cell + dir.offset
        val other = owner[nb] ?: continue
        if (other === pl) continue
        if (pl.placement.id < other.placement.id && open(pl, cell, dir, other, nb)) {
            val key = pl.placement.id to other.placement.id
            result[key] = (result[key] ?: 0) + 1
        }
    }
    return result
}

/** Open edges between a candidate placement and the already placed ones. */
fun openContacts(candidate: TilePlacement, others: List<TilePlacement>, catalog: MaterialCatalog): Int =
    connections(others + candidate, catalog).filterKeys { candidate.id in it.toList() }.values.sum()

/** True if the placement has underground cells (black borders). */
fun TilePlacement.isUnderground(catalog: MaterialCatalog): Boolean =
    catalog.tile(tile)?.sides?.get(side)?.walled?.isNotEmpty() == true
