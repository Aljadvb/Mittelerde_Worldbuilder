package io.github.aljadvb.worldbuilder.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PassagesTest {
    private val base = FormatJson.decodeCatalog(RepoFiles.exampleMaterial)
    /** x01 side A as a cave: only cell (0,0) side NW is open. */
    private val catalog = base.copy(tiles = base.tiles.map { t ->
        if (t.id != "x01") t else t.copy(sides = t.sides.mapValues { (s, shape) ->
            if (s != TileSide.A) shape else shape.copy(walled = shape.cells, passages = listOf(HexEdge(0, 0, HexDir.NW)))
        })
    })

    @Test
    fun `directions rotate clockwise like cells`() {
        assertEquals(HexDir.NE, HexDir.N.rotated(1))
        assertEquals(HexDir.NW, HexDir.N.rotated(-1))
        assertEquals(HexDir.S, HexDir.N.opposite)
        for (d in HexDir.entries) assertEquals(d.rotated(2).offset, d.offset.rotateClockwise(2))
    }

    @Test
    fun `caves connect only through passages`() {
        val cave = TilePlacement("pl_cave", "x01", TileSide.A, HexCoord.ORIGIN)
        val west = TilePlacement("pl_w", "x03", TileSide.A, HexCoord(-2, 1)) // centre (-2,1): touches (0,0) on its NW side? check below
        val touching = HexCoord(-1, 0) // NW neighbour of (0,0)
        val westOk = west.copy(position = touching - HexCoord(1, 0)) // its cell (1,0) lies on (-1,0)
        assertTrue(touching in (catalog.tile("x03")!!.sides.getValue(TileSide.A).cells.map(westOk::toMap)))
        assertEquals(1, openContacts(westOk, listOf(cave), catalog).coerceAtMost(1))
        // East side of the cave is closed.
        val east = TilePlacement("pl_e", "x03", TileSide.A, HexCoord(3, -1))
        assertEquals(0, openContacts(east, listOf(cave), catalog))
        // Turning the cave by 180° moves the passage to the SE side.
        val turned = cave.copy(rotation = 3)
        val se = HexCoord(0, 0) + HexDir.SE.offset
        val eastTouching = east.copy(position = se - HexCoord(-1, 0))
        assertTrue(se in catalog.tile("x03")!!.sides.getValue(TileSide.A).cells.map(eastTouching::toMap))
        assertTrue(openContacts(eastTouching, listOf(turned), catalog) >= 1)
        assertTrue(cave.isUnderground(catalog) && !west.isUnderground(catalog))
    }
}
