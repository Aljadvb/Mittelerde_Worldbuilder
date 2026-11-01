package io.github.aljadvb.worldbuilder.editor

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import io.github.aljadvb.worldbuilder.editor.map.MAP_TAG
import io.github.aljadvb.worldbuilder.editor.map.MapController
import io.github.aljadvb.worldbuilder.editor.map.MapEditor
import io.github.aljadvb.worldbuilder.hexmap.HexProjection
import io.github.aljadvb.worldbuilder.model.HexCoord
import io.github.aljadvb.worldbuilder.model.TileLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Drives the map with simulated mouse input (same pointer code path as the real window). */
@OptIn(ExperimentalTestApi::class)
class MapMouseTest {
    private fun EditorModel.scenario() = campaign!!.scenarios.single()

    @Test
    fun dragTokenWithTheMouseAndZoom() = runComposeUiTest {
        val model = EditorModel(InMemoryEditorPrefs()).apply { openExample(); selection = "ch_dorf" }
        val controller = MapController(model)
        setContent { MaterialTheme(colorScheme = lightColorScheme()) { MapEditor(model, controller) } }
        waitForIdle()

        fun px(cell: HexCoord): Offset =
            HexProjection(model.catalog.hexOrientation, controller.camera.size, controller.camera.origin).center(cell)

        // drag the well token from (0,1) to (3,0)
        onNodeWithTag(MAP_TAG).performMouseInput {
            moveTo(px(HexCoord(0, 1)))
            press()
            moveTo(px(HexCoord(2, 0)))
            moveTo(px(HexCoord(3, 0)))
            release()
        }
        waitForIdle()
        assertEquals("tok_brunnen", model.selection)
        assertEquals(TileLocation("pl_dorfweg", HexCoord(1, 0)), model.scenario().token("tok_brunnen")!!.location)

        // mouse wheel zooms around the cursor
        val before = controller.camera.size
        onNodeWithTag(MAP_TAG).performMouseInput { moveTo(center); scroll(-1f) }
        waitForIdle()
        assertTrue(controller.camera.size > before)

        // dragging empty space pans the view
        val origin = controller.camera.origin
        onNodeWithTag(MAP_TAG).performMouseInput {
            moveTo(Offset(5f, 5f)); press(); moveTo(Offset(45f, 25f)); release()
        }
        waitForIdle()
        assertEquals(origin + Offset(40f, 20f), controller.camera.origin)
    }
}
