package io.github.aljadvb.worldbuilder.editing

import io.github.aljadvb.worldbuilder.validation.ObjectKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BattleEditsTest {
    private val battle = Examples.battle

    @Test
    fun `board and terrain placements are known objects`() {
        assertEquals(ObjectKind.BOARD_PLACEMENT, battle.definition("bp_halle")?.kind)
        assertEquals("scenarios[0].chapters[0].battlefield.terrain[2]", battle.definition("gt_busch")?.path)
    }

    @Test
    fun `rename and delete battle objects`() {
        val renamed = battle.rename("gt_busch", "gt_versteck")
        assertTrue("gt_versteck" in renamed.allIds() && "gt_busch" !in renamed.allIds())
        assertEquals("", renamed.validate().format())
        val deleted = battle.delete("bp_halle")
        assertFalse("bp_halle" in deleted.allIds())
        // The token stood on the removed board.
        assertTrue(deleted.validate().errors.isNotEmpty())
    }

    @Test
    fun `battle extent counts whole boards`() {
        val s = battle.scenarios.single()
        assertEquals(MapExtent(57.0, 28.5), s.battleExtent(Examples.catalog))
        assertEquals(MapExtent(57.0, 28.5), s.mapExtent(Examples.catalog))
        assertTrue(TableSize.DEFAULT.fits(s.mapExtent(Examples.catalog)))
        assertFalse(TableSize(50.0, 50.0).fits(s.mapExtent(Examples.catalog)))
    }
}
