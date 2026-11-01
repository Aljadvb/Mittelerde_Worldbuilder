package io.github.aljadvb.worldbuilder.editor

import io.github.aljadvb.worldbuilder.editor.map.Drag
import io.github.aljadvb.worldbuilder.editor.map.MapController
import io.github.aljadvb.worldbuilder.editor.map.MapTool
import io.github.aljadvb.worldbuilder.model.HexCoord
import io.github.aljadvb.worldbuilder.model.TileLocation
import io.github.aljadvb.worldbuilder.model.TileSide
import io.github.aljadvb.worldbuilder.model.TokenType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MapControllerTest {
    private fun setup(selection: String): Pair<EditorModel, MapController> {
        val m = EditorModel(InMemoryEditorPrefs())
        m.openExample()
        m.selection = selection
        return m to MapController(m)
    }

    private val EditorModel.scenario get() = campaign!!.scenarios.single()

    @Test
    fun `current chapter and visible chapters follow the selection`() {
        val (m, c) = setup("tok_muehlentor")
        assertEquals("ch_muehle", c.chapter!!.id)
        assertEquals(listOf("ch_dorf", "ch_muehle"), c.visibleChapters.map { it.id })
        m.selection = "pl_dorfweg"
        assertEquals(listOf("ch_dorf"), c.visibleChapters.map { it.id })
    }

    @Test
    fun `dragging a tile moves it together with its tokens`() {
        val (m, c) = setup("ch_muehle")
        assertTrue(c.press(HexCoord(6, -1))) // anchor of pl_muehle (gate token sits here too -> token wins)
        assertIs<Drag.Token>(c.drag)
        c.release()

        assertTrue(c.press(HexCoord(6, 0))) // a free cell of the mill tile (local (0,1))
        assertEquals("pl_muehle", m.selection)
        c.move(HexCoord(6, 3))
        assertEquals(HexCoord(6, 2), c.ghost!!.position)
        c.release()
        assertEquals(HexCoord(6, 2), m.scenario.placement("pl_muehle")!!.position)
        assertEquals(emptyList(), m.report.errors)
    }

    @Test
    fun `overlapping drops are refused`() {
        val (m, c) = setup("ch_muehle")
        c.press(HexCoord(6, 0))
        c.move(HexCoord(1, 1)) // anchor would land on (1,0), inside the village
        assertTrue(c.ghostConflicts.isNotEmpty())
        c.release()
        assertEquals(HexCoord(6, -1), m.scenario.placement("pl_muehle")!!.position)
        assertNotNull(m.message)
    }

    @Test
    fun `placing a new tile, then a token and an enemy on it`() {
        val (m, c) = setup("ch_muehle")
        c.tool = MapTool.PlaceTile("x02", TileSide.B, rotation = 1)
        c.move(HexCoord(0, 0))
        assertTrue(c.ghostConflicts.isNotEmpty())
        c.press(HexCoord(0, 6))
        val placement = m.scenario.placement(m.selection!!)!!
        assertEquals(HexCoord(0, 6), placement.position)
        assertEquals(1, placement.rotation)
        assertEquals("ch_muehle", m.scenario.chapterOfPlacement(placement.id)!!.id)
        assertEquals(MapTool.Select, c.tool)

        c.tool = MapTool.PlaceToken(TokenType.EXPLORE)
        c.press(HexCoord(0, 6))
        val token = m.scenario.token(m.selection!!)!!
        assertEquals(TileLocation(placement.id, HexCoord.ORIGIN), token.location)

        c.tool = MapTool.PlaceEnemy
        c.press(HexCoord(30, 30))
        assertNotNull(m.message) // no tile there
        m.message = null
        c.press(HexCoord(0, 6))
        assertNotNull(m.scenario.enemyGroup(m.selection!!))
    }

    @Test
    fun `dragging a token onto another tile cell moves it`() {
        val (m, c) = setup("ch_dorf")
        c.press(HexCoord(0, 1)) // brunnen
        assertEquals("tok_brunnen", m.selection)
        c.move(HexCoord(3, 0)) // on pl_dorfweg (local (1,0))
        c.release()
        assertEquals(TileLocation("pl_dorfweg", HexCoord(1, 0)), m.scenario.token("tok_brunnen")!!.location)
    }

    @Test
    fun `keyboard rotate flip delete`() {
        val (m, c) = setup("pl_dorfweg")
        c.rotate()
        assertEquals(1, m.scenario.placement("pl_dorfweg")!!.rotation)
        m.selection = "pl_dorfplatz"
        c.flip()
        assertEquals(TileSide.B, m.scenario.placement("pl_dorfplatz")!!.side)
        assertTrue(m.message!!.contains("tok_brunnen"))
        m.selection = "tok_spuren"
        c.deleteSelection()
        assertNull(m.scenario.token("tok_spuren"))
        assertEquals("ch_dorf", m.selection)
    }
}
