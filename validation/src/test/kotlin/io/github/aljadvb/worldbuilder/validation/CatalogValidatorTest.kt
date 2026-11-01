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
