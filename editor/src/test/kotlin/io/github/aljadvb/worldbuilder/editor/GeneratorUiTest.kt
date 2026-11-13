package io.github.aljadvb.worldbuilder.editor

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import io.github.aljadvb.worldbuilder.editor.ai.GeneratorModel
import io.github.aljadvb.worldbuilder.editor.ai.GeneratorScreen
import io.github.aljadvb.worldbuilder.generator.DraftChapter
import io.github.aljadvb.worldbuilder.generator.DraftEnding
import io.github.aljadvb.worldbuilder.generator.DraftEnemy
import io.github.aljadvb.worldbuilder.generator.DraftObjective
import io.github.aljadvb.worldbuilder.generator.FakeProvider
import io.github.aljadvb.worldbuilder.generator.StoryDraft
import io.github.aljadvb.worldbuilder.model.FormatJson
import io.github.aljadvb.worldbuilder.model.Scenario
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GeneratorUiTest {
    private val catalog = EditorModel.loadExampleCatalog()
    private val example = FormatJson.decodeCampaign(EditorModel.exampleCampaignText()).scenarios.single()

    private val draft = StoryDraft(
        "Die stille Mühle", "Nebel liegt über dem Tal …",
        listOf(
            DraftChapter("Das Dorf", "Die Gruppe hört sich um.", listOf("Dorfplatz", "Brunnen"), listOf("wiese")),
            DraftChapter("Die Mühle", "Am Bach wartet die Mühle.", listOf("Mühle am Bach"), listOf("fluss", "wald_licht")),
        ),
        listOf(DraftObjective("Finde die Mühle", "Spuren untersuchen"), DraftObjective("Befreie den Müller", "Tor öffnen")),
        listOf(DraftEnemy("nebelwolf", "an der Mühle")),
        listOf(DraftEnding("Das Rad dreht sich", "victory", "Müller befreit"), DraftEnding("Im Nebel verloren", "defeat", "Bedrohung voll")),
    )

    private fun modelOutput(s: Scenario): String {
        val root = FormatJson.json.encodeToJsonElement(Scenario.serializer(), s).jsonObject
        val chapters = JsonArray((root.getValue("chapters") as JsonArray).map { ch ->
            val o = ch.jsonObject
            JsonObject(o + ("tiles" to JsonArray((o["tiles"] as JsonArray).map { JsonObject(it.jsonObject - "position" - "rotation") })))
        })
        return Json.encodeToString(JsonObject.serializer(), JsonObject(root + ("chapters" to chapters)))
    }

    private fun generator(answers: MutableList<String>?) = runBlocking {
        GeneratorModel(catalog, scope = this, providerFactory = { answers?.let { FakeProvider(it, model = "claude-opus-5-5") } }, worker = Dispatchers.Unconfined)
    }

    @Test
    fun ollamaModeUsesTheLocalServerAndNeedsNoKey() = runBlocking {
        val fake = mutableListOf(Json.encodeToString(StoryDraft.serializer(), draft))
        val g = GeneratorModel(catalog, scope = this, providerFactory = { null }, worker = Dispatchers.Unconfined, ollamaFactory = { FakeProvider(fake, model = "qwen3.6:35b") })
        assertTrue(g.hasOllama)
        g.mode = GeneratorModel.Mode.OLLAMA
        g.source = "Notizen: verschwundener Müller, Nebel."
        g.createStory(); g.join()
        assertNull(g.error)
        assertEquals(draft, g.draft)
        // A local model costs nothing.
        assertEquals(0.0, g.totalCost)
    }

    @Test
    fun unreachableOllamaServerAsksAboutTheHomeNetwork() = runBlocking {
        // Port 9 on localhost: nothing listens there.
        val g = GeneratorModel(catalog, scope = this, providerFactory = { null }, worker = Dispatchers.Unconfined,
            ollamaFactory = { io.github.aljadvb.worldbuilder.generator.OllamaProvider("http://127.0.0.1:9") })
        g.mode = GeneratorModel.Mode.OLLAMA
        g.source = "Notizen"
        g.createStory(); g.join()
        assertTrue("Bist du im Heimnetz?" in g.error.orEmpty(), g.error)
    }

    @Test
    fun wholeFlowEndsInTheEditor() = runBlocking {
        val fake = mutableListOf(Json.encodeToString(StoryDraft.serializer(), draft), modelOutput(example))
        val g = GeneratorModel(catalog, scope = this, providerFactory = { FakeProvider(fake, model = "claude-opus-5-5") }, worker = Dispatchers.Unconfined)
        g.source = "Notizen: verschwundener Müller, Nebel."
        assertTrue(g.canCreateStory)
        g.createStory(); g.join()
        assertNull(g.error)
        assertEquals(GeneratorModel.Step.STORY, g.step)
        assertEquals(draft, g.draft)
        g.draft = g.draft!!.copy(title = "Geänderter Titel")
        g.createScenario(); g.join()
        val result = assertNotNull(g.result)
        assertTrue(result.report.isValid)
        assertTrue(g.totalCost > 0)

        val editor = EditorModel(InMemoryEditorPrefs())
        assertTrue(g.accept(editor))
        assertEquals(example.id, editor.selection)
        assertTrue(editor.document!!.isDirty)
        assertTrue(editor.report.isValid, editor.report.format())
    }

    @Test
    fun missingKeyIsReported() {
        val g = generator(null)
        g.source = "x"
        g.createStory()
        assertNotNull(g.error)
        assertEquals(GeneratorModel.Step.INPUT, g.step)
    }

    @Test
    fun addingToTheOpenCampaignKeepsItsScenarios() = runBlocking {
        val g = GeneratorModel(catalog, scope = this, providerFactory = { FakeProvider(mutableListOf(modelOutput(example)), model = "claude-opus-5-5") }, worker = Dispatchers.Unconfined)
        g.draft = draft
        g.target = GeneratorModel.Target.CURRENT_CAMPAIGN
        g.createScenario(); g.join()
        val editor = EditorModel(InMemoryEditorPrefs()).apply { openExample() }
        g.accept(editor)
        assertEquals(2, editor.campaign!!.scenarios.size)
        assertTrue(editor.report.isValid, editor.report.format())
        editor.undo()
        assertEquals(1, editor.campaign!!.scenarios.size)
    }

    private fun render(name: String, g: GeneratorModel) {
        val editor = EditorModel(InMemoryEditorPrefs())
        val scene = ImageComposeScene(1100, 860, Density(1f)) {
            MaterialTheme(colorScheme = lightColorScheme()) { GeneratorScreen(g, catalog, editor, hasOpenCampaign = false, onLoadFile = { null }, onDone = {}) }
        }
        try {
            scene.render()
            File("build/screenshots").mkdirs()
            File("build/screenshots/$name.png").writeBytes(scene.render(1_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        } finally {
            scene.close()
        }
    }

    @Test
    fun screenshots() = runBlocking {
        val g = GeneratorModel(catalog, scope = this, providerFactory = { FakeProvider(mutableListOf(modelOutput(example)), model = "claude-opus-5-5") }, worker = Dispatchers.Unconfined)
        g.source = "Seit Tagen steht die Mühle still. Der Müller ist verschwunden, und abends steigt Nebel aus dem Bach."
        g.wishes = "düster, aber mit Hoffnung"
        render("e13_ai_input", g)
        g.draft = draft; g.step = GeneratorModel.Step.STORY
        render("e14_ai_story", g)
        g.createScenario(); g.join()
        render("e15_ai_result", g)
    }
}

class ManualModeTest {
    @Test
    fun copyAndPasteFlowWithoutApiKey() = runBlocking { kotlinx.coroutines.withTimeout(20_000) {
        val catalog = EditorModel.loadExampleCatalog()
        val draft = StoryDraft("Titel", "Intro", emptyList(), emptyList(), emptyList(), emptyList())
        val g = GeneratorModel(catalog, scope = this, providerFactory = { null }, worker = Dispatchers.IO)
        g.mode = GeneratorModel.Mode.CLAUDE_AI
        g.source = "Notizen"
        g.createStory()
        var waited = 0
        while (g.manualRequest == null && waited++ < 200) kotlinx.coroutines.delay(20)
        val request = g.manualRequest ?: run { g.cancelRun(); error("no manual request") }
        assertTrue("# Verfügbares Material" in request.prompt)
        val scene = ImageComposeScene(1100, 860, Density(1f)) {
            MaterialTheme(colorScheme = lightColorScheme()) { GeneratorScreen(g, catalog, EditorModel(InMemoryEditorPrefs()), false, { null }, {}) }
        }
        try {
            scene.render()
            File("build/screenshots").mkdirs()
            File("build/screenshots/e16_ai_manual.png").writeBytes(scene.render(1_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        } finally {
            scene.close()
        }
        g.manualAnswer = "Gern!\n```json\n" + Json.encodeToString(StoryDraft.serializer(), draft) + "\n```"
        g.submitManualAnswer()
        g.join()
        assertNull(g.error)
        assertEquals(draft, g.draft)
        assertEquals(0.0, g.totalCost)
    } }
}

class GeneratorFailureTest {
    @Test
    fun failedScenarioRunKeepsTheReasonVisible() = runBlocking {
        val catalog = EditorModel.loadExampleCatalog()
        val g = GeneratorModel(catalog, scope = this, providerFactory = { FakeProvider(MutableList(4) { "kein json" }, model = "claude-opus-5-5") }, worker = Dispatchers.Unconfined)
        g.draft = StoryDraft("T", "I", emptyList(), emptyList(), emptyList(), emptyList())
        g.step = GeneratorModel.Step.STORY
        g.createScenario(); g.join()
        assertEquals(GeneratorModel.Step.SCENARIO, g.step)
        assertNull(g.result)
        val reason = assertNotNull(g.lastFailure)
        g.error = null
        assertEquals(reason, g.lastFailure)
    }
}
