package io.github.aljadvb.worldbuilder.editing

import io.github.aljadvb.worldbuilder.model.HexCoord
import io.github.aljadvb.worldbuilder.model.HexOrientation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TableFitTest {
    @Test
    fun `one flat hex of the original tiles is 4,4 x 3,8 cm`() {
        val e = MapExtent.of(listOf(HexCoord.ORIGIN), HexOrientation.FLAT, 22.2)
        assertEquals(4.44, e.widthCm, 1e-9)
        assertEquals(3.845, e.heightCm, 1e-3)
        // a row of 3 along q (zig-zag) is 1.5 hexes further per step
        assertEquals(4.44 + 2 * 3.33, MapExtent.of(listOf(HexCoord(0, 0), HexCoord(1, 0), HexCoord(2, 0)), HexOrientation.FLAT, 22.2).widthCm, 1e-9)
    }

    @Test
    fun `table fit works in both orientations`() {
        val table = TableSize(120.0, 80.0)
        assertTrue(table.fits(MapExtent(100.0, 70.0)))
        assertTrue(table.fits(MapExtent(70.0, 110.0)))
        assertFalse(table.fits(MapExtent(130.0, 50.0)))
        assertFalse(table.fits(MapExtent(85.0, 85.0)))
        assertTrue(table.cellCapacity(22.2) in 300..450)
    }

    @Test
    fun `example scenario extent`() {
        val e = Examples.campaign.scenarios.single().mapExtent(Examples.catalog)
        assertTrue(e.widthCm > 20 && e.heightCm > 10, e.toString())
    }
}
