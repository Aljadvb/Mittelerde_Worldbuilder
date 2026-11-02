package io.github.aljadvb.worldbuilder.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class BattleMapTest {
    private val catalog = FormatJson.decodeCatalog(RepoFiles.exampleMaterial)
    private val side = catalog.battleBoard("sx1")!!.sides.getValue(TileSide.A)

    private fun map(vararg boards: BoardPlacement) = BattleMap(boards.toList(), catalog)

    @Test
    fun `rotating a 3x3 board turns squares clockwise`() {
        assertEquals(GridCoord(2, 0), BattleMap.rotateSquare(GridCoord(0, 0), side, 1))
        assertEquals(GridCoord(2, 2), BattleMap.rotateSquare(GridCoord(0, 0), side, 2))
        assertEquals(GridCoord(0, 2), BattleMap.rotateSquare(GridCoord(0, 0), side, 3))
        assertEquals(GridCoord(1, 1), BattleMap.rotateSquare(GridCoord(1, 1), side, 1))
        val wide = BoardSide(width = 4, height = 2)
        assertEquals(GridCoord(1, 0), BattleMap.rotateSquare(GridCoord(0, 0), wide, 1))
        assertEquals(GridCoord(0, 3), BattleMap.rotateSquare(GridCoord(3, 1), wide, 1))
        assertEquals(2 to 4, BattleMap.rotatedSize(wide, 1))
    }

    @Test
    fun `boards are positioned in whole boards and keep their fields`() {
        val m = map(
            BoardPlacement("bp_a", "sx1", TileSide.A, GridCoord(0, 0)),
            BoardPlacement("bp_b", "sx1", TileSide.B, GridCoord(1, 0)),
        )
        assertEquals(10, m.fields.size)
        assertEquals(6 to 3, m.extent())
        assertEquals(setOf(GridCoord(3, 0), GridCoord(3, 1)), m.field(GridCoord(3, 1))!!.squares)
        assertEquals("bp_b", m.field(GridCoord(4, 1))!!.boardPlacement)
        assertNull(m.field(GridCoord(6, 0)))
    }

    @Test
    fun `edges are classified`() {
        val m = map(BoardPlacement("bp_a", "sx1", TileSide.A, GridCoord(0, 0)))
        // (0,0) and (0,1) form one field.
        assertEquals(BattleMap.EdgeKind.INSIDE_FIELD, m.edgeKind(GridEdge(0, 0, EdgeSide.SOUTH)))
        assertEquals(BattleMap.EdgeKind.BORDER, m.edgeKind(GridEdge(0, 0, EdgeSide.EAST)))
        assertEquals(BattleMap.EdgeKind.RIM, m.edgeKind(GridEdge(0, 0, EdgeSide.NORTH)))
        assertEquals(BattleMap.EdgeKind.OFF_MAP, m.edgeKind(GridEdge(5, 5, EdgeSide.NORTH)))
        assertEquals(GridEdge(1, 0, EdgeSide.WEST), GridEdge(0, 0, EdgeSide.EAST).normalized())
        assertEquals(GridEdge(0, 1, EdgeSide.NORTH), GridEdge(0, 0, EdgeSide.SOUTH).normalized())
    }

    @Test
    fun `walls cut adjacency`() {
        val m = map(BoardPlacement("bp_a", "sx1", TileSide.A, GridCoord(0, 0)))
        val centre = m.field(GridCoord(1, 1))!!
        assertEquals(4, m.neighbors(centre).size)
        val west = m.field(GridCoord(0, 1))!!
        val blocked = setOf(GridEdge(1, 1, EdgeSide.WEST).normalized())
        assertEquals(3, m.neighbors(centre, blocked).size)
        assertNotEquals(true, west in m.neighbors(centre, blocked))
    }
}
