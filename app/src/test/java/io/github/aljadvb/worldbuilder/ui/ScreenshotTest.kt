package io.github.aljadvb.worldbuilder.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import io.github.aljadvb.worldbuilder.data.CampaignLibrary
import io.github.aljadvb.worldbuilder.editing.addPlacement
import io.github.aljadvb.worldbuilder.engine.Command
import io.github.aljadvb.worldbuilder.engine.GameEngine
import io.github.aljadvb.worldbuilder.engine.GameState
import io.github.aljadvb.worldbuilder.engine.Prompt
import io.github.aljadvb.worldbuilder.model.FormatJson
import io.github.aljadvb.worldbuilder.ui.game.PlayContent
import io.github.aljadvb.worldbuilder.ui.game.ResolutionContent
import io.github.aljadvb.worldbuilder.ui.theme.WorldbuilderTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the play screen with the bundled example in a few situations, checks basic
 * interaction and writes PNGs to build/screenshots for manual inspection.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
    private val campaign = FormatJson.decodeCampaign(assets.open(CampaignLibrary.EXAMPLE_CAMPAIGN).bufferedReader().readText())
    private val catalog = FormatJson.decodeCatalog(assets.open(CampaignLibrary.EXAMPLE_CATALOG).bufferedReader().readText())
    private val scenario = campaign.scenarios.single()
    private val engine = GameEngine(scenario)

    private fun GameState.ackUntil(predicate: (GameState) -> Boolean): GameState {
        var s = this
        while (!predicate(s)) s = engine.apply(s, Command.Acknowledge)
        return s
    }

    private fun show(state: GameState, onCommand: (Command) -> Unit = {}) = compose.setContent {
        Screen { PlayContent(scenario, catalog, state, campaignChanged = false, onCommand = onCommand) }
    }

    @Composable
    private fun Screen(content: @Composable () -> Unit) {
        WorldbuilderTheme(darkTheme = false) { Surface(Modifier.fillMaxSize()) { content() } }
    }

    /** Draws the activity's view tree into a bitmap (PixelCopy is not available under Robolectric). */
    private fun snapshot(name: String) {
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun intro() {
        show(engine.start(2))
        compose.onNodeWithText(scenario.title).assertExists()
        snapshot("01_intro")
    }

    @Test
    fun setupOfFirstChapter() {
        show(engine.start(2).ackUntil { it.currentPrompt is Prompt.Setup })
        compose.onNodeWithText("Aufbau: Das Dorf").assertExists()
        snapshot("02_setup_dorf")
    }

    @Test
    fun idleWithTokensAndTapOnChip() {
        val idle = engine.start(2).ackUntil { it.isIdle }
        val sent = mutableListOf<Command>()
        show(idle) { sent += it }
        snapshot("03_idle_dorf")
        compose.onNodeWithText("Alter Brunnen").performClick()
        assertEquals(listOf<Command>(Command.InteractToken("tok_brunnen")), sent)
    }

    /** Plays to the moment the mill chapter is being set up. */
    private fun millSetup(): GameState {
        var s = engine.start(3).ackUntil { it.isIdle }
        s = engine.apply(s, Command.InteractToken("tok_dorfbewohnerin")).ackUntil { it.currentPrompt is Prompt.Choice }
        s = engine.apply(s, Command.Choose(0)).ackUntil { it.isIdle }
        return engine.apply(s, Command.InteractToken("tok_spuren")).ackUntil { it.currentPrompt is Prompt.Setup }
    }

    @Test
    fun setupOfSecondChapter() {
        show(millSetup())
        compose.onNodeWithText("Aufbau: Die Mühle am Bach").assertExists()
        snapshot("04_setup_muehle")
    }

    @Test
    fun enemiesAndTestInSecondChapter() {
        var s = millSetup().ackUntil { it.isIdle }
        s = engine.apply(s, Command.InteractToken("tok_muehlentor")).ackUntil { it.currentPrompt is Prompt.Test }
        show(s)
        compose.onNodeWithText("Probe: Stärke").assertExists()
        snapshot("05_test_tor")
    }

    @Test
    fun resolution() {
        var s = engine.start(5).ackUntil { it.isIdle }
        while (!s.isOver) s = if (s.isIdle) engine.apply(s, Command.EndRound) else engine.apply(s, Command.Acknowledge)
        compose.setContent { Screen { ResolutionContent(scenario, s, onContinue = {}) } }
        compose.onNodeWithText("Im Nebel verloren").assertExists()
        snapshot("06_resolution")
    }

    /** Local only: the user's own catalog with tile pictures (WB_PRIVATE_CATALOG); never committed. */
    @Test
    fun privateCatalogWithPictures() {
        val path = System.getenv("WB_PRIVATE_CATALOG") ?: return
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val library = CampaignLibrary(context.filesDir, context.assets)
        check(library.importCatalog(File(path).readBytes()) is CampaignLibrary.ImportResult.Success)
        val own = library.catalog()
        var c = io.github.aljadvb.worldbuilder.editing.newCampaign("Test")
        val s0 = c.scenarios.single()
        c = c.addPlacement(s0.startChapter, "t101", io.github.aljadvb.worldbuilder.model.TileSide.A, io.github.aljadvb.worldbuilder.model.HexCoord(0, 0)).campaign
        c = c.addPlacement(s0.startChapter, "t103", io.github.aljadvb.worldbuilder.model.TileSide.B, io.github.aljadvb.worldbuilder.model.HexCoord(3, -1), 1).campaign
        c = c.addPlacement(s0.startChapter, "t104", io.github.aljadvb.worldbuilder.model.TileSide.A, io.github.aljadvb.worldbuilder.model.HexCoord(0, 2)).campaign
        val sc = c.scenarios.single()
        val eng = GameEngine(sc)
        var st = eng.start(2)
        while (st.currentPrompt !is Prompt.Setup) st = eng.apply(st, Command.Acknowledge)
        compose.setContent {
            Screen { PlayContent(sc, own, st, campaignChanged = false, onCommand = {}, images = io.github.aljadvb.worldbuilder.ui.game.rememberTileImages(library.catalogImageDir)) }
        }
        snapshot("07_private_tiles")
    }
}
