package io.github.aljadvb.worldbuilder.editing

import io.github.aljadvb.worldbuilder.model.HexCoord
import io.github.aljadvb.worldbuilder.model.TileLocation
import io.github.aljadvb.worldbuilder.model.TilePlacement
import io.github.aljadvb.worldbuilder.model.TileSide
import io.github.aljadvb.worldbuilder.validation.IssueCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MapEditsAndHistoryTest {
    private val campaign = Examples.campaign
    private val catalog = Examples.catalog
    private val scenario get() = campaign.scenarios.single()

    @Test
    fun `locate maps a map cell back to a tile-local location`() {
        val p = TilePlacement("pl_t", "x02", TileSide.A, HexCoord(3, 4), rotation = 2)
        for (local in p.localCells(catalog)) assertEquals(local, p.toLocal(p.toMap(local)))
        // brunnen token sits on pl_dorfplatz (0,1); placement at origin without rotation
        assertEquals(TileLocation("pl_dorfplatz", HexCoord(0, 1)), scenario.locate(HexCoord(0, 1), catalog))
        assertNull(scenario.locate(HexCoord(50, 50), catalog))
        assertNull(scenario.locate(HexCoord(0, 1), catalog, chapterIds = setOf("ch_muehle")))
    }

    @Test
    fun `overlap preview`() {
        val candidate = TilePlacement("pl_new", "x01", TileSide.A, HexCoord(0, 0))
        assertEquals(setOf(HexCoord(0, 0), HexCoord(1, 0), HexCoord(0, 1), HexCoord(1, -1)), scenario.overlaps(candidate, catalog))
        assertTrue(scenario.overlaps(candidate.copy(position = HexCoord(0, 5)), catalog).isEmpty())
        // moving a placement onto itself is fine
        assertTrue(scenario.overlaps(scenario.placement("pl_dorfplatz")!!, catalog, ignore = "pl_dorfplatz").isEmpty())
    }

    @Test
    fun `rotating keeps tokens on their tile`() {
        val rotated = campaign.rotatePlacement("pl_muehle", 2).movePlacement("pl_muehle", HexCoord(8, -1))
        assertEquals(2, rotated.scenarios.single().placement("pl_muehle")!!.rotation)
        assertEquals("", rotated.validate().format())
        assertEquals(0, campaign.rotatePlacement("pl_muehle", -6).scenarios.single().placement("pl_muehle")!!.rotation)
    }

    @Test
    fun `flipping reports tokens that no longer fit`() {
        // x01 side B lacks the cells (1,0) and (0,1) where the two village tokens are
        val result = campaign.flipPlacement("pl_dorfplatz", catalog)
        assertEquals(listOf("tok_dorfbewohnerin", "tok_brunnen"), result.conflicts)
        assertEquals(TileSide.B, result.campaign.scenarios.single().placement("pl_dorfplatz")!!.side)
        assertTrue(result.campaign.validate().errors.any { it.code == IssueCode.CELL_NOT_ON_TILE })

        val fixed = result.campaign
            .moveToken("tok_dorfbewohnerin", TileLocation("pl_dorfplatz", HexCoord(-1, 0)))
            .moveToken("tok_brunnen", TileLocation("pl_dorfplatz", HexCoord(0, -1)))
        assertTrue(fixed.validate().errors.none { it.code == IssueCode.CELL_NOT_ON_TILE })
    }

    @Test
    fun `history undo redo and coalescing`() {
        var h = History("a")
        h = h.push("ab", coalesceKey = "title").push("abc", coalesceKey = "title") // one step
        h = h.push("abc!")
        assertEquals("abc!", h.present)
        h = h.undo()
        assertEquals("abc", h.present)
        h = h.undo()
        assertEquals("a", h.present)
        assertFalse(h.canUndo)
        h = h.redo().redo()
        assertEquals("abc!", h.present)
        assertFalse(h.canRedo)
        // a new edit after undo drops the redo branch
        h = h.undo().push("x")
        assertFalse(h.canRedo)
        // pushing an unchanged value is ignored
        assertEquals(h, h.push("x"))
    }

    @Test
    fun `history is bounded`() {
        var h = History(0, limit = 3)
        (1..10).forEach { h = h.push(it) }
        repeat(10) { h = h.undo() }
        assertEquals(7, h.present)
    }
}
