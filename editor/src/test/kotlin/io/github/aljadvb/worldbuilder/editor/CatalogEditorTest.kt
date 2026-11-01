package io.github.aljadvb.worldbuilder.editor

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.aljadvb.worldbuilder.editing.addTile
import io.github.aljadvb.worldbuilder.editing.newCatalog
import io.github.aljadvb.worldbuilder.editor.catalog.CatalogEditor
import io.github.aljadvb.worldbuilder.editor.catalog.CatalogEditorModel
import io.github.aljadvb.worldbuilder.editor.catalog.CatalogSelection
import io.github.aljadvb.worldbuilder.editor.catalog.SHAPE_TAG
import io.github.aljadvb.worldbuilder.model.FormatJson
import io.github.aljadvb.worldbuilder.model.TileSide
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class CatalogEditorTest {
    @Test
    fun clickingTheShapeGridTogglesCells() = runComposeUiTest {
        val (catalog, id) = newCatalog().addTile("101", "grundspiel")
        val model = CatalogEditorModel(catalog, null) {}
        model.selection = CatalogSelection.Tile(id)
        setContent { MaterialTheme(colorScheme = lightColorScheme()) { CatalogEditor(model) } }
        // the right neighbour of the centre cell
        onNodeWithTag(SHAPE_TAG).performMouseInput { click(center + androidx.compose.ui.geometry.Offset(width / 9f, 0f)) }
        waitForIdle()
        assertEquals(2, model.catalog.tile(id)!!.sides.getValue(TileSide.A).cells.size)
        onNodeWithTag(SHAPE_TAG).performMouseInput { click(center + androidx.compose.ui.geometry.Offset(width / 9f, 0f)) }
        waitForIdle()
        assertEquals(1, model.catalog.tile(id)!!.sides.getValue(TileSide.A).cells.size)
    }

    @Test
    fun saveNotifiesTheEditor() {
        val dir = Files.createTempDirectory("cat").toFile()
        try {
            var loaded: File? = null
            val model = CatalogEditorModel(newCatalog(), null) { loaded = it }
            assertTrue(model.isDirty)
            assertTrue(model.saveAs(File(dir, "material")))
            assertFalse(model.isDirty)
            assertEquals(File(dir, "material.json"), loaded)
            assertEquals(model.catalog, FormatJson.decodeCatalog(loaded!!.readText()))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun screenshot() {
        val model = CatalogEditorModel(EditorModel.loadExampleCatalog(), null) {}
        model.selection = CatalogSelection.Tile("x01")
        val scene = ImageComposeScene(1200, 860, Density(1f)) { MaterialTheme(colorScheme = lightColorScheme()) { CatalogEditor(model) } }
        try {
            scene.render()
            val img = scene.render(1_000_000_000L)
            File("build/screenshots").mkdirs()
            File("build/screenshots/e11_catalog.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        } finally {
            scene.close()
        }
    }
}
