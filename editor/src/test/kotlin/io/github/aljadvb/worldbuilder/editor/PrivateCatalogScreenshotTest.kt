package io.github.aljadvb.worldbuilder.editor

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import io.github.aljadvb.worldbuilder.editing.addPlacement
import io.github.aljadvb.worldbuilder.editing.newCampaign
import io.github.aljadvb.worldbuilder.editor.ui.EditorContent
import io.github.aljadvb.worldbuilder.model.HexCoord
import io.github.aljadvb.worldbuilder.model.TileSide
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * Renders the editor with the user's own catalog (pictures of the original tiles).
 * Runs only locally when WB_PRIVATE_CATALOG points to such a ZIP; nothing of it is committed.
 */
class PrivateCatalogScreenshotTest {
    @Test
    fun mapWithTilePictures() {
        val path = System.getenv("WB_PRIVATE_CATALOG") ?: return
        CatalogFiles.dataDir = File("build/private-catalog")
        val m = EditorModel(InMemoryEditorPrefs())
        m.loadCatalog(File(path))
        check(m.message == null) { m.message!! }
        m.newDocument("Test")
        val chapter = m.campaign!!.scenarios.single().startChapter
        m.edit { it.addPlacement(chapter, "t101", TileSide.A, HexCoord(0, 0)).campaign }
        m.edit { it.addPlacement(chapter, "t103", TileSide.A, HexCoord(3, -1), rotation = 1).campaign }
        m.edit { it.addPlacement(chapter, "t104", TileSide.B, HexCoord(0, 2)).campaign }
        m.edit { it.addPlacement(chapter, "t102", TileSide.B, HexCoord(-3, 1), rotation = 3).campaign }
        m.selection = chapter
        val scene = ImageComposeScene(1400, 1000, Density(1f)) { MaterialTheme(colorScheme = lightColorScheme()) { EditorContent(m) } }
        try {
            scene.render()
            File("build/screenshots").mkdirs()
            File("build/screenshots/private_map.png").writeBytes(scene.render(1_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        } finally {
            scene.close()
        }
    }
}
