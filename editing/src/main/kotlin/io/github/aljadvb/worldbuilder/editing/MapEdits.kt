package io.github.aljadvb.worldbuilder.editing

import io.github.aljadvb.worldbuilder.model.Campaign
import io.github.aljadvb.worldbuilder.model.HexCoord
import io.github.aljadvb.worldbuilder.model.MaterialCatalog
import io.github.aljadvb.worldbuilder.model.Scenario
import io.github.aljadvb.worldbuilder.model.TileLocation
import io.github.aljadvb.worldbuilder.model.TilePlacement
import io.github.aljadvb.worldbuilder.model.TileSide

/** Tile-local cells of a placement according to the catalog (empty if unknown). */
fun TilePlacement.localCells(catalog: MaterialCatalog): List<HexCoord> =
    catalog.tile(tile)?.sides?.get(side)?.cells.orEmpty()

/** Map cells covered by a placement. */
fun TilePlacement.mapCells(catalog: MaterialCatalog): List<HexCoord> = localCells(catalog).map(::toMap)

/** Inverse of [TilePlacement.toMap]. */
fun TilePlacement.toLocal(mapCell: HexCoord): HexCoord = (mapCell - position).rotateClockwise(-rotation)

/**
 * Finds the placed tile covering [mapCell] and returns the tile-local location.
 * Only placements in [chapterIds] are considered (all if null).
 */
fun Scenario.locate(mapCell: HexCoord, catalog: MaterialCatalog, chapterIds: Set<String>? = null): TileLocation? =
    chapters.filter { chapterIds == null || it.id in chapterIds }
        .flatMap { it.tiles }
        .firstOrNull { p -> p.toLocal(mapCell) in p.localCells(catalog) }
        ?.let { p -> TileLocation(p.id, p.toLocal(mapCell)) }

/**
 * Map cells where a candidate placement would overlap tiles already placed in the scenario
 * (all chapters share one map). [ignore] excludes the placement being moved.
 */
fun Scenario.overlaps(candidate: TilePlacement, catalog: MaterialCatalog, ignore: String? = null): Set<HexCoord> {
    val occupied = placements.filter { it.id != ignore }.flatMap { it.mapCells(catalog) }.toSet()
    return candidate.mapCells(catalog).filterTo(mutableSetOf()) { it in occupied }
}

fun Campaign.movePlacement(id: String, position: HexCoord) = updatePlacement(id) { it.copy(position = position) }

fun Campaign.rotatePlacement(id: String, steps: Int) = updatePlacement(id) { it.copy(rotation = Math.floorMod(it.rotation + steps, 6)) }

/** Result of flipping a tile: tokens/enemy groups whose cell does not exist on the new side. */
data class FlipResult(val campaign: Campaign, val conflicts: List<String>)

fun Campaign.flipPlacement(id: String, catalog: MaterialCatalog): FlipResult {
    val scenario = scenarioOf(id) ?: throw EditException("Platzierung '$id' nicht gefunden.")
    val placement = scenario.placement(id)!!
    val newSide = if (placement.side == TileSide.A) TileSide.B else TileSide.A
    val shape = catalog.tile(placement.tile)?.sides?.get(newSide)
        ?: throw EditException("Kachel '${placement.tile}' hat im Katalog keine Seite $newSide.")
    val conflicts = scenario.tokens.filter { it.location.placement == id && it.location.cell !in shape.cells }.map { it.id } +
        scenario.enemyGroups.filter { it.location.placement == id && it.location.cell !in shape.cells }.map { it.id }
    return FlipResult(updatePlacement(id) { it.copy(side = newSide) }, conflicts)
}

fun Campaign.moveToken(id: String, location: TileLocation) = updateToken(id) { it.copy(location = location) }

fun Campaign.moveEnemyGroup(id: String, location: TileLocation) = updateEnemyGroup(id) { it.copy(location = location) }
