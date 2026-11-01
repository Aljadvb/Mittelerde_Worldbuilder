package io.github.aljadvb.worldbuilder.editing

import io.github.aljadvb.worldbuilder.model.HexCoord
import io.github.aljadvb.worldbuilder.model.HexOrientation
import io.github.aljadvb.worldbuilder.model.TileSide
import io.github.aljadvb.worldbuilder.validation.CatalogValidator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CatalogEditsTest {
    @Test
    fun `build a tile by toggling cells and mirror it`() {
        var (c, id) = newCatalog().addTile("101", "grundspiel")
        assertEquals("k_101", id)
        c = c.toggleCell(id, TileSide.A, HexCoord(1, 0)).toggleCell(id, TileSide.A, HexCoord(0, 1))
        c = c.toggleCell(id, TileSide.A, HexCoord.ORIGIN) // anchor stays
        assertEquals(setOf(HexCoord(0, 0), HexCoord(1, 0), HexCoord(0, 1)), c.tile(id)!!.sides.getValue(TileSide.A).cells.toSet())

        c = c.mirrorAToB(id)
        assertEquals(setOf(HexCoord(0, 0), HexCoord(-1, 0), HexCoord(-1, 1)), c.tile(id)!!.sides.getValue(TileSide.B).cells.toSet())
        assertEquals("", CatalogValidator.validate(c).format())

        c = c.toggleCell(id, TileSide.A, HexCoord(1, 0))
        assertEquals(2, c.tile(id)!!.sides.getValue(TileSide.A).cells.size)
    }

    @Test
    fun `mirroring twice is the identity and keeps distances`() {
        for (o in HexOrientation.entries) for (q in -3..3) for (r in -3..3) {
            val h = HexCoord(q, r)
            assertEquals(h, h.mirrored(o).mirrored(o))
            assertEquals(h.distanceTo(HexCoord.ORIGIN), h.mirrored(o).distanceTo(HexCoord.ORIGIN))
            // neighbours stay neighbours
            h.neighbors().forEach { n -> assertEquals(1, n.mirrored(o).distanceTo(h.mirrored(o))) }
        }
    }

    @Test
    fun `enemy types and expansions`() {
        var c = newCatalog()
        val (c1, exp) = c.addExpansion("Schatten im Wald")
        val (c2, enemy) = c1.addEnemyType("Höhlentroll", exp)
        assertEquals("schatten_im_wald", exp)
        assertEquals("hoehlentroll", enemy)
        assertFailsWith<EditException> { c2.removeExpansion(exp) }
        c = c2.updateExpansion(exp) { it.copy(owned = false) }.removeEnemyType(enemy).removeExpansion(exp)
        assertTrue(c.expansions.none { it.id == exp })
        assertEquals("", CatalogValidator.validate(c).format())
    }
}
