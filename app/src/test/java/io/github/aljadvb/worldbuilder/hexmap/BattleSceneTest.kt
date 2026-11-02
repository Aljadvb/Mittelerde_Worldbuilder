package io.github.aljadvb.worldbuilder.hexmap

import androidx.compose.ui.geometry.Offset
import io.github.aljadvb.worldbuilder.engine.Command
import io.github.aljadvb.worldbuilder.engine.GameEngine
import io.github.aljadvb.worldbuilder.engine.Prompt
import io.github.aljadvb.worldbuilder.model.EdgeSide
import io.github.aljadvb.worldbuilder.model.FormatJson
import io.github.aljadvb.worldbuilder.model.GridCoord
import io.github.aljadvb.worldbuilder.model.GridEdge
import io.github.aljadvb.worldbuilder.model.TileSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BattleSceneTest {
    private val root = File(System.getProperty("repoRoot") ?: "..")
    private val catalog = FormatJson.decodeCatalog(File(root, "examples/beispiel-material.json").readText())
    private val battle = FormatJson.decodeCampaign(File(root, "examples/beispiel-schlacht.json").readText()).scenarios.single()

    @Test
    fun squareAtAndEdgeAtInvertGeometry() {
        val proj = BattleProjection(100f, Offset(10f, 20f))
        assertEquals(GridCoord(2, 1), proj.squareAt(proj.center(GridCoord(2, 1))))
        assertEquals(GridCoord(-1, 0), proj.squareAt(Offset(5f, 25f)))
        assertEquals(GridEdge(0, 0, EdgeSide.EAST), proj.edgeAt(Offset(105f, 70f)))
        assertEquals(GridEdge(0, 0, EdgeSide.NORTH), proj.edgeAt(Offset(60f, 25f)))
    }

    @Test
    fun playSceneShowsUnlockedBattleWithPiecesAndHitTests() {
        val engine = GameEngine(battle)
        var st = engine.start(2)
        while (st.currentPrompt !is Prompt.Setup) st = engine.apply(st, Command.Acknowledge)
        assertTrue(battle.showsBattle(st))
        val scene = battlePlayScene(battle, catalog, st, highlightFor(st.currentPrompt, battle), emptyMap())
        assertEquals(2, scene.boards.size)
        assertEquals(10, scene.boards.sumOf { it.fields.size })
        assertEquals(setOf(GridCoord(4, 1)), scene.highlightSquares)
        assertTrue(scene.boards.all { it.emphasized })
        val bush = scene.pieces.single { it.id == "gt_busch" }
        assertTrue(bush.special)
        assertEquals("Versteck der Späherin", bush.name)
        val proj = BattleProjection(100f, Offset.Zero)
        assertEquals("gt_busch", pieceAt(proj, scene, proj.center(GridCoord(1, 1)))?.id)
        // The Bach lies on the east edge of (2,0): x = 300, y = 50.
        assertEquals("gt_bach_1", pieceAt(proj, scene, Offset(301f, 50f))?.id)
        assertNull(pieceAt(proj, scene, proj.center(GridCoord(5, 2))))
    }

    @Test
    fun setupAndTerrainInfo() {
        val setup = battle.battleSetup("ch_bach", catalog)!!
        assertEquals(listOf("SX1" to TileSide.A, "SX1" to TileSide.B), setup.boards)
        assertEquals(listOf("Bach" to 1, "Steinwall" to 1, "Dornbusch" to 1), setup.pieces)
        val wall = battle.terrainInfo("gt_wall_1", catalog)!!
        assertEquals("Steinwall", wall.title)
        assertNull(wall.pieceName)
        assertEquals("Nicht überquerbar; trennt die Felder.", wall.effect)
        val travel = FormatJson.decodeCampaign(File(root, "examples/beispiel-kampagne.json").readText()).scenarios.single()
        assertFalse(travel.showsBattle(GameEngine(travel).start(2)))
    }
}
