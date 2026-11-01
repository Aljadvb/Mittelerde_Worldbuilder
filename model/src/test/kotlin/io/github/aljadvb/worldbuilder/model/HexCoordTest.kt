package io.github.aljadvb.worldbuilder.model

import kotlin.test.Test
import kotlin.test.assertEquals

class HexCoordTest {
    @Test
    fun `six rotations return to the start`() {
        val c = HexCoord(2, -1)
        assertEquals(c, c.rotateClockwise(6))
        assertEquals(c, c.rotateClockwise(0))
    }

    @Test
    fun `one clockwise step walks through the neighbour ring in order`() {
        // DIRECTIONS are listed clockwise, so rotating each by one step yields the next.
        HexCoord.DIRECTIONS.forEachIndexed { i, d ->
            assertEquals(HexCoord.DIRECTIONS[(i + 1) % 6], d.rotateClockwise(1))
        }
    }

    @Test
    fun `negative rotation is the inverse`() {
        val c = HexCoord(3, -2)
        assertEquals(c, c.rotateClockwise(2).rotateClockwise(-2))
        assertEquals(c.rotateClockwise(5), c.rotateClockwise(-1))
    }

    @Test
    fun `rotation preserves distance to origin`() {
        val c = HexCoord(3, -1)
        (0..5).forEach { assertEquals(3, c.rotateClockwise(it).distanceTo(HexCoord.ORIGIN)) }
    }

    @Test
    fun `placement maps local cells with rotation and offset`() {
        val p = TilePlacement(id = "pl_x", tile = "x01", side = TileSide.A, position = HexCoord(5, 5), rotation = 1)
        assertEquals(HexCoord(5, 5), p.toMap(HexCoord.ORIGIN))
        assertEquals(HexCoord(5, 6), p.toMap(HexCoord(1, 0)))
    }
}
