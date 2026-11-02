package io.github.aljadvb.worldbuilder.editing

import io.github.aljadvb.worldbuilder.model.BattleMap
import io.github.aljadvb.worldbuilder.model.Battlefield
import io.github.aljadvb.worldbuilder.model.BoardPlacement
import io.github.aljadvb.worldbuilder.model.Campaign
import io.github.aljadvb.worldbuilder.model.CustomRule
import io.github.aljadvb.worldbuilder.model.GridCoord
import io.github.aljadvb.worldbuilder.model.GridEdge
import io.github.aljadvb.worldbuilder.model.MaterialCatalog
import io.github.aljadvb.worldbuilder.model.PiecePlacement
import io.github.aljadvb.worldbuilder.model.Scenario
import io.github.aljadvb.worldbuilder.model.SquareLocation
import io.github.aljadvb.worldbuilder.model.TerrainPlacement
import io.github.aljadvb.worldbuilder.model.TileLocation
import io.github.aljadvb.worldbuilder.model.TileSide
import io.github.aljadvb.worldbuilder.model.battleSegmentOf
import io.github.aljadvb.worldbuilder.validation.ObjectKind

/** Turns a chapter into a battle chapter (empty battlefield) or back into a travel chapter. */
fun Campaign.setBattleChapter(chapterId: String, battle: Boolean): Campaign {
    val scenario = scenarios.firstOrNull { s -> s.chapters.any { it.id == chapterId } } ?: throw EditException("Kapitel '$chapterId' nicht gefunden.")
    val ch = scenario.chapter(chapterId)!!
    if (battle == ch.isBattle) return this
    if (battle) {
        if (ch.tiles.isNotEmpty() || ch.tokens.any { it.location is TileLocation }) {
            throw EditException("Das Kapitel hat noch Kacheln oder Token auf Kacheln. Bitte zuerst entfernen.")
        }
        return updateChapter(chapterId) { it.copy(battlefield = Battlefield()) }
    }
    val bf = ch.battlefield!!
    if (bf.boards.isNotEmpty() || bf.terrain.isNotEmpty() || ch.tokens.any { it.location is SquareLocation }) {
        throw EditException("Das Kapitel hat noch Schlachtpläne, Geländeteile oder Token darauf. Bitte zuerst entfernen.")
    }
    return updateChapter(chapterId) { it.copy(battlefield = null) }
}

private fun Campaign.updateBattlefield(chapterId: String, edit: (Battlefield) -> Battlefield) = updateChapter(chapterId) { ch ->
    ch.copy(battlefield = edit(ch.battlefield ?: throw EditException("Kapitel '$chapterId' ist kein Schlacht-Kapitel.")))
}

fun Campaign.addBoard(chapterId: String, board: String, side: TileSide, position: GridCoord, rotation: Int = 0): Created {
    val id = Ids.fromTitle(ObjectKind.BOARD_PLACEMENT, board, allIds())
    return Created(updateBattlefield(chapterId) { it.copy(boards = it.boards + BoardPlacement(id, board, side, position, Math.floorMod(rotation, 4))) }, id)
}

fun Campaign.updateBoard(id: String, edit: (BoardPlacement) -> BoardPlacement) =
    updateBattlefields(id) { bf -> bf.copy(boards = bf.boards.map { if (it.id == id) edit(it) else it }) }

fun Campaign.addTerrain(chapterId: String, piece: String, side: TileSide, area: GridCoord? = null, edge: GridEdge? = null): Created {
    val id = Ids.fromTitle(ObjectKind.TERRAIN_PLACEMENT, piece, allIds())
    return Created(updateBattlefield(chapterId) { it.copy(terrain = it.terrain + TerrainPlacement(id, piece, side, area, edge)) }, id)
}

fun Campaign.updateTerrain(id: String, edit: (TerrainPlacement) -> TerrainPlacement) =
    updateBattlefields(id) { bf -> bf.copy(terrain = bf.terrain.map { if (it.id == id) edit(it) else it }) }

/** Sets or clears the scenario-specific name and rule of a terrain piece; blank texts clear them. */
fun Campaign.setTerrainCustom(id: String, name: String?, rule: CustomRule?) = updateTerrain(id) {
    it.copy(customName = name?.takeIf { n -> n.isNotBlank() }, customRule = rule?.takeIf { r -> r.text.isNotBlank() })
}

/** Battle squares, chapter by chapter, that the board placements of this scenario cover. */
fun Scenario.boardSquares(catalog: MaterialCatalog): Map<String, Set<GridCoord>> = battleMapOf(catalog).boardSquares

private fun Scenario.battleMapOf(catalog: MaterialCatalog) = BattleMap(boardPlacements, catalog)

/** Board position (in whole boards) that contains [square] for a board of the given size and rotation. */
fun boardPositionAt(square: GridCoord, catalog: MaterialCatalog, board: String, side: TileSide, rotation: Int): GridCoord? {
    val s = catalog.battleBoard(board)?.sides?.get(side) ?: return null
    val (w, h) = BattleMap.rotatedSize(s, rotation)
    return GridCoord(Math.floorDiv(square.x, w), Math.floorDiv(square.y, h))
}

/** How many pieces of each catalog piece are still free in this scenario (both sides together). */
/** Free pieces for the layout of [chapterId] (whole scenario if null); cleared layouts do not count. */
fun Scenario.freePieces(catalog: MaterialCatalog, chapterId: String? = null): Map<String, Int> {
    val chs = chapterId?.let { battleSegmentOf(it) } ?: chapters
    val used = chs.flatMap { it.battlefield?.terrain.orEmpty() }.groupingBy { it.piece }.eachCount()
    return catalog.terrainPieces.associate { it.id to it.count - (used[it.id] ?: 0) }
}

/** How many copies of each board are still free in this scenario. */
fun Scenario.freeBoards(catalog: MaterialCatalog, chapterId: String? = null): Map<String, Int> {
    val chs = chapterId?.let { battleSegmentOf(it) } ?: chapters
    val used = chs.flatMap { it.battlefield?.boards.orEmpty() }.groupingBy { it.board }.eachCount()
    return catalog.battleBoards.associate { it.id to it.count - (used[it.id] ?: 0) }
}

/** Whether [piece]/[side] lies on edges (otherwise inside fields). */
fun MaterialCatalog.isEdgePiece(piece: String, side: TileSide) = terrainPiece(piece)?.sides?.get(side)?.placement == PiecePlacement.EDGE

/** Flips a terrain piece to its other side if that side exists and lies the same way. */
fun Campaign.flipTerrain(id: String, catalog: MaterialCatalog): Campaign {
    val t = scenarioOf(id)?.terrainPlacement(id) ?: throw EditException("Geländeteil '$id' nicht gefunden.")
    val other = if (t.side == TileSide.A) TileSide.B else TileSide.A
    val piece = catalog.terrainPiece(t.piece) ?: throw EditException("Geländeteil '${t.piece}' fehlt im Katalog.")
    val a = piece.sides[t.side]?.placement
    val b = piece.sides[other] ?: throw EditException("Geländeteil '${t.piece}' hat keine Seite $other.")
    if (a != b.placement) throw EditException("Die andere Seite (${b.name}) liegt anders – bitte neu setzen.")
    return updateTerrain(id) { it.copy(side = other) }
}
