package io.github.aljadvb.worldbuilder.editor

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import io.github.aljadvb.worldbuilder.editing.updateToken
import io.github.aljadvb.worldbuilder.editor.ui.EditorContent
import io.github.aljadvb.worldbuilder.model.HexCoord
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/** Renders the editor window content headlessly to build/screenshots for inspection. */
class EditorScreenshotTest {
    private fun render(name: String, model: EditorModel, height: Int = 860) {
        val scene = ImageComposeScene(width = 1400, height = height, density = Density(1f)) {
            MaterialTheme(colorScheme = lightColorScheme()) { EditorContent(model) }
        }
        try {
            scene.render() // first frame runs effects
            val image = scene.render(1_000_000_000L)
            val dir = File("build/screenshots").apply { mkdirs() }
            File(dir, "$name.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        } finally {
            scene.close()
        }
    }

    @Test
    fun emptyWindow() = render("e01_empty", EditorModel(InMemoryEditorPrefs()))

    @Test
    fun exampleWithError() {
        val m = EditorModel(InMemoryEditorPrefs())
        m.openExample()
        m.edit { c -> c.updateToken("tok_brunnen") { it.copy(location = it.location.copy(cell = HexCoord(9, 9))) } }
        m.selection = "tok_brunnen"
        render("e02_structure", m)
    }

    private fun example(selection: String) = EditorModel(InMemoryEditorPrefs()).apply { openExample(); this.selection = selection }

    @Test
    fun campaignForm() = render("e03_campaign", example("cmp_hainbrueck"))

    @Test
    fun scenarioForm() = render("e04_scenario", example("scn_muehle"))

    @Test
    fun tokenForm() = render("e05_token", example("tok_muehlentor"))

    @Test
    fun triggerForm() = render("e06_trigger", example("trg_bedrohung_8"))

    @Test
    fun eventEditor() = render("e07_event", example("evt_dorfbewohnerin"), height = 1000)

    @Test
    fun testStepEvent() = render("e08_event_test", example("evt_tor"))

    @Test
    fun mapEditorChapter() = render("e09_map_chapter", example("ch_muehle"), height = 1000)

    @Test
    fun mapEditorToken() = render("e10_map_token", example("tok_brunnen"), height = 1000)
}
