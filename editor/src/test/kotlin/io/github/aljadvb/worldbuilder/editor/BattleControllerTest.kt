package io.github.aljadvb.worldbuilder.editor

import io.github.aljadvb.worldbuilder.editing.setBattleChapter
import io.github.aljadvb.worldbuilder.editor.map.BattleController
import io.github.aljadvb.worldbuilder.editor.map.BattleDrag
import io.github.aljadvb.worldbuilder.editor.map.BattleTool
import io.github.aljadvb.worldbuilder.editor.map.editorBattleScene
import io.github.aljadvb.worldbuilder.hexmap.BattleProjection
import io.github.aljadvb.worldbuilder.model.EdgeSide
import io.github.aljadvb.worldbuilder.model.FormatJson
import io.github.aljadvb.worldbuilder.model.GridCoord
import io.github.aljadvb.worldbuilder.model.GridEdge
import io.github.aljadvb.worldbuilder.model.SquareLocation
import io.github.aljadvb.worldbuilder.model.TileSide
import io.github.aljadvb.worldbuilder.model.TokenType
import androidx.compose.ui.geometry.Offset
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BattleControllerTest {
    private val root = File(System.getProperty("repoRoot") ?: "..")
    private val proj = BattleProjection(100f, Offset.Zero)

    private fun battleModel(selection: String): Pair<EditorModel, BattleController> {
        val m = EditorModel(InMemoryEditorPrefs())
        m.openUnsaved(FormatJson.decodeCampaign(File(root, "examples/beispiel-schlacht.json").readText()))
        m.selection = selection
        return m to BattleController(m)
    }

    private val EditorModel.scenario get() = campaign!!.scenarios.single()
    private fun BattleController.click(m: EditorModel, x: Float, y: Float) = press(Offset(x, y), proj, editorBattleScene(m, this)!!)

    @Test
    fun `chapter follows battle objects`() {
        val (_, c) = battleModel("gt_busch")
        assertEquals("ch_bach", c.chapter!!.id)
        val (_, c2) = battleModel("eg_woelfe")
        assertEquals("ch_bach", c2.chapter!!.id)
    }

    @Test
    fun `place pieces on fields and borders, counting what is left`() {
        val (m, c) = battleModel("ch_bach")
        c.tool = BattleTool.PlacePiece("bach_wall", TileSide.A)
        // Inside field (0,0)-(0,1): the edge between them is not a border.
        c.click(m, 50f, 99f)
        assertEquals(2, m.scenario.terrainPlacements.count { it.piece == "bach_wall" })
        // East edge of (0,0): border to field (1,0)-(2,0).
        c.click(m, 98f, 50f)
        val added = m.scenario.terrainPlacements.last()
        assertEquals(GridEdge(0, 0, EdgeSide.EAST), added.edge)
        c.click(m, 98f, 150f) // east edge of (0,1) -> border to the centre field
        c.click(m, 150f, 198f) // south edge of (1,1) -> 4th piece, the last one
        assertEquals(4, m.scenario.terrainPlacements.count { it.piece == "bach_wall" })
        c.click(m, 250f, 198f)
        assertEquals(4, m.scenario.terrainPlacements.count { it.piece == "bach_wall" })
        assertNotNull(m.message)
    }

    @Test
    fun `drag token and board, rotate and flip`() {
        val (m, c) = battleModel("ch_bach")
        // Token "Vorräte" sits in square (4,1): its disc is at the lower left of the square.
        assertTrue(c.click(m, 428f, 172f))
        assertIs<BattleDrag.Token>(c.drag)
        c.move(Offset(560f, 260f), proj)
        c.release()
        assertEquals(SquareLocation(GridCoord(5, 2)), m.scenario.token("tok_vorraete")!!.location)

        // Board bp_halle: press an empty spot, drag it one board down.
        assertTrue(c.click(m, 560f, 40f))
        assertEquals("bp_halle", m.selection)
        c.move(Offset(560f, 340f), proj)
        c.release()
        assertEquals(GridCoord(1, 1), m.scenario.boardPlacement("bp_halle")!!.position)
        c.rotate()
        assertEquals(2, m.scenario.boardPlacement("bp_halle")!!.rotation)
        c.flip()
        assertEquals(TileSide.A, m.scenario.boardPlacement("bp_halle")!!.side)
    }

    @Test
    fun `place a board, a token and an enemy in a new battle chapter`() {
        val m = EditorModel(InMemoryEditorPrefs())
        m.openUnsaved(io.github.aljadvb.worldbuilder.editing.newCampaign("Neu"))
        val ch = m.campaign!!.scenarios.single().startChapter
        m.edit { it.setBattleChapter(ch, true) }
        m.selection = ch
        val c = BattleController(m)
        c.tool = BattleTool.PlaceBoard("sx1", TileSide.B)
        c.click(m, 120f, 120f)
        assertEquals(GridCoord(0, 0), m.scenario.boardPlacements.single().position)
        m.selection = ch
        c.tool = BattleTool.PlaceToken(TokenType.PERSON)
        c.click(m, 150f, 150f)
        assertEquals(SquareLocation(GridCoord(1, 1)), m.scenario.tokens.single().location)
        m.selection = ch
        c.tool = BattleTool.PlaceEnemy
        c.click(m, 250f, 250f)
        assertEquals(SquareLocation(GridCoord(2, 2)), m.scenario.enemyGroups.single().location)
        c.deleteSelection()
        assertTrue(m.scenario.enemyGroups.isEmpty())
    }
}
