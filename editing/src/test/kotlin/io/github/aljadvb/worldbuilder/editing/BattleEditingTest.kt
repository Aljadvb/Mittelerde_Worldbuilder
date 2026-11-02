package io.github.aljadvb.worldbuilder.editing

import io.github.aljadvb.worldbuilder.model.CustomRule
import io.github.aljadvb.worldbuilder.model.EdgeSide
import io.github.aljadvb.worldbuilder.model.GridCoord
import io.github.aljadvb.worldbuilder.model.GridEdge
import io.github.aljadvb.worldbuilder.model.SquareLocation
import io.github.aljadvb.worldbuilder.model.TileSide
import io.github.aljadvb.worldbuilder.model.TokenType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BattleEditingTest {
    private val catalog = Examples.catalog

    @Test
    fun `build a battle chapter from scratch`() {
        var c = newCampaign("Test")
        val chapter = c.scenarios.single().startChapter
        c = c.setBattleChapter(chapter, true)
        val board = c.addBoard(chapter, "sx1", TileSide.A, GridCoord(0, 0))
        c = board.campaign
        c = c.addBoard(chapter, "sx1", TileSide.B, GridCoord(1, 0), rotation = 5).campaign
        val wall = c.addTerrain(chapter, "bach_wall", TileSide.B, edge = GridEdge(2, 1, EdgeSide.EAST))
        c = wall.campaign
        val bush = c.addTerrain(chapter, "busch_kiste", TileSide.A, area = GridCoord(1, 1))
        c = bush.campaign.setTerrainCustom(bush.id, "Hexenkraut", CustomRule(text = "Riecht seltsam."))
        c = c.addToken(chapter, "Kiste", TokenType.SEARCH, SquareLocation(GridCoord(4, 1))).campaign
        val s = c.scenarios.single()
        assertEquals(1, s.boardPlacements.last().rotation)
        assertEquals("Hexenkraut", s.terrainPlacement(bush.id)!!.customName)
        assertEquals(mapOf("bach_wall" to 3, "busch_kiste" to 1), s.freePieces(catalog))
        assertEquals(mapOf("sx1" to 0), s.freeBoards(catalog))
        assertEquals(18, s.boardSquares(catalog).values.sumOf { it.size })
        val battleIssues = c.validate().issues.filter { it.path.contains("battlefield") || it.path.contains("tokens") }
        assertEquals(emptyList(), battleIssues, c.validate().format())

        // Clearing the custom texts removes them.
        val cleared = c.setTerrainCustom(bush.id, " ", CustomRule(text = ""))
        assertNull(cleared.scenarios.single().terrainPlacement(bush.id)!!.customRule)
        // Flipping keeps edge pieces on edges.
        assertEquals(TileSide.A, c.flipTerrain(wall.id, catalog).scenarios.single().terrainPlacement(wall.id)!!.side)
        // A chapter with battle content cannot simply become a travel chapter.
        assertFailsWith<EditException> { c.setBattleChapter(chapter, false) }
    }

    @Test
    fun `travel chapter with tiles cannot become a battle`() {
        val c = Examples.campaign
        assertFailsWith<EditException> { c.setBattleChapter("ch_dorf", true) }
        assertFailsWith<EditException> { c.addBoard("ch_dorf", "sx1", TileSide.A, GridCoord(0, 0)) }
    }

    @Test
    fun `board position snaps to whole boards`() {
        assertEquals(GridCoord(1, 0), boardPositionAt(GridCoord(4, 2), catalog, "sx1", TileSide.A, 0))
        assertEquals(GridCoord(-1, -1), boardPositionAt(GridCoord(-1, -3), catalog, "sx1", TileSide.A, 0))
        assertTrue(catalog.isEdgePiece("bach_wall", TileSide.A))
    }
}

class BattleCatalogEditsTest {
    @Test
    fun `add, edit and remove boards and pieces`() {
        var c = Examples.catalog
        val (c1, boardId) = c.addBattleBoard("Burghof", "beispiel")
        c = c1.updateBattleBoard(boardId) { it.copy(count = 2) }
        val (c2, pieceId) = c.addTerrainPiece("Brücke", "beispiel")
        c = c2.updatePieceSide(pieceId, TileSide.B) { it.copy(name = "Furt", placement = io.github.aljadvb.worldbuilder.model.PiecePlacement.EDGE, cardText = "x") }
        assertEquals("", io.github.aljadvb.worldbuilder.validation.CatalogValidator.validate(c).format())
        assertEquals("Furt", c.terrainPiece(pieceId)!!.sides.getValue(TileSide.B).name)
        assertEquals("Brücke", c.terrainPiece(pieceId)!!.sides.getValue(TileSide.A).name)
        c = c.removeBattleBoard(boardId).removeTerrainPiece(pieceId)
        assertEquals(Examples.catalog, c)
    }

    @Test
    fun `export lists board and piece pictures`() {
        val board = Examples.catalog.battleBoard("sx1")!!
        val withPics = Examples.catalog.updateBattleBoard("sx1") {
            it.copy(sides = board.sides.mapValues { (_, s) -> s.copy(image = io.github.aljadvb.worldbuilder.model.BoardImage("bilder/plan.jpg", cellSize = 100.0)) })
        }.updatePieceSide("bach_wall", TileSide.A) { it.copy(image = io.github.aljadvb.worldbuilder.model.PieceImage("bilder/bach.png")) }
        assertEquals(listOf("bilder/plan.jpg", "bilder/bach.png"), CatalogPackage.pictures(withPics))
    }
}
