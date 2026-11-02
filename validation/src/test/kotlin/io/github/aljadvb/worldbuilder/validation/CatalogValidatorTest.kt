package io.github.aljadvb.worldbuilder.validation

import io.github.aljadvb.worldbuilder.model.HexCoord
import io.github.aljadvb.worldbuilder.model.TileSide
import io.github.aljadvb.worldbuilder.model.TileSideShape
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CatalogValidatorTest {
    @Test
    fun `example catalog is valid`() {
        assertEquals("", CatalogValidator.validate(Examples.catalog).format())
    }

    @Test
    fun `broken tile definitions are reported`() {
        val tile = Examples.catalog.tiles.first()
        val broken = tile.copy(
            expansion = "unbekannt",
            sides = mapOf(TileSide.A to TileSideShape(listOf(HexCoord(1, 0), HexCoord(1, 0)))),
        )
        val catalog = Examples.catalog.copy(tiles = Examples.catalog.tiles + broken)
        val codes = CatalogValidator.validate(catalog).issues.map { it.code }.toSet()
        assertTrue(IssueCode.DUPLICATE_ID in codes)
        assertTrue(IssueCode.UNKNOWN_EXPANSION in codes)
        assertTrue(IssueCode.TILE_SHAPE_WITHOUT_ANCHOR in codes)
        assertTrue(IssueCode.TILE_SHAPE_DUPLICATE_CELL in codes)
    }
}

class BattleCatalogValidationTest {
    @Test
    fun `board fields must cover every square once`() {
        val board = Examples.catalog.battleBoard("sx1")!!
        val side = board.sides.getValue(TileSide.A)
        val broken = board.copy(count = 0, sides = mapOf(TileSide.A to side.copy(areas = side.areas.drop(1) + listOf(listOf(io.github.aljadvb.worldbuilder.model.GridCoord(1, 1), io.github.aljadvb.worldbuilder.model.GridCoord(3, 0))))))
        val report = CatalogValidator.validate(Examples.catalog.copy(battleBoards = listOf(broken)))
        val fields = report.issues.single { it.code == IssueCode.INVALID_BOARD_FIELDS }
        assertTrue("außerhalb: (3,0)" in fields.message && "doppelt: (1,1)" in fields.message && "fehlt: (0,0), (0,1)" in fields.message, fields.message)
        assertTrue(report.issues.any { it.code == IssueCode.INVALID_NUMBER && it.path == "battleBoards[0].count" })
    }

    @Test
    fun `terrain pieces need names and known expansions`() {
        val piece = Examples.catalog.terrainPiece("bach_wall")!!
        val broken = piece.copy(expansion = "weg", sides = piece.sides.mapValues { (_, s) -> s.copy(name = "", terrain = listOf("lava")) })
        val codes = CatalogValidator.validate(Examples.catalog.copy(terrainPieces = listOf(broken))).issues.map { it.code }
        assertTrue(IssueCode.UNKNOWN_EXPANSION in codes && IssueCode.BLANK_TEXT in codes && IssueCode.UNKNOWN_TERRAIN in codes, codes.toString())
    }
}

class TileImageValidationTest {
    @Test
    fun `image paths must be relative`() {
        val tile = Examples.catalog.tiles.first()
        fun withImage(file: String, size: Double = 50.0) = Examples.catalog.copy(
            tiles = listOf(tile.copy(sides = tile.sides.mapValues { (_, s) -> s.copy(image = io.github.aljadvb.worldbuilder.model.TileImage(file, 10.0, 10.0, size)) })),
        )
        assertEquals("", CatalogValidator.validate(withImage("bilder/x01A.jpg")).format())
        listOf("/etc/passwd", "../geheim.jpg", "C:\\bild.jpg", "").forEach { bad ->
            assertTrue(CatalogValidator.validate(withImage(bad)).issues.any { it.code == IssueCode.INVALID_IMAGE }, bad)
        }
        assertTrue(CatalogValidator.validate(withImage("a.jpg", 0.0)).issues.any { it.code == IssueCode.INVALID_IMAGE })
    }
}
