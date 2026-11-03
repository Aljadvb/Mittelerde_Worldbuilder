package io.github.aljadvb.worldbuilder.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import io.github.aljadvb.worldbuilder.data.CampaignLibrary
import io.github.aljadvb.worldbuilder.editing.addPlacement
import io.github.aljadvb.worldbuilder.engine.Command
import io.github.aljadvb.worldbuilder.engine.GameEngine
import io.github.aljadvb.worldbuilder.engine.GameState
import io.github.aljadvb.worldbuilder.engine.Prompt
import io.github.aljadvb.worldbuilder.hexmap.terrainInfo
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

    /** Like [show], but the shown state can be swapped later in the same test. */
    private fun showMutable(state: androidx.compose.runtime.MutableState<GameState>, onCommand: (Command) -> Unit) = compose.setContent {
        Screen { PlayContent(scenario, catalog, state.value, campaignChanged = false, onCommand = onCommand) }
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
        compose.onNodeWithText("Probe: Körperkraft").assertExists()
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

    // ---- battle maps -------------------------------------------------------------------

    private val battle = FormatJson.decodeCampaign(assets.open("beispiel-schlacht.json").bufferedReader().readText()).scenarios.single()

    private fun battleSetup(sc: io.github.aljadvb.worldbuilder.model.Scenario): Pair<GameEngine, GameState> {
        val eng = GameEngine(sc)
        var st = eng.start(2)
        while (st.currentPrompt !is Prompt.Setup) st = eng.apply(st, Command.Acknowledge)
        return eng to st
    }

    @Test
    fun battleSetupShowsBoardsAndPieces() {
        val (_, st) = battleSetup(battle)
        compose.setContent { Screen { PlayContent(battle, catalog, st, campaignChanged = false, onCommand = {}) } }
        compose.onNodeWithText("Schlachtpläne").assertExists()
        compose.onNodeWithText("• SX1, Seite B").assertExists()
        compose.onNodeWithText("• 1× Steinwall").assertExists()
        snapshot("08_battle_setup")
    }

    @Test
    fun terrainDialogShowsScenarioRule() {
        val info = battle.terrainInfo("gt_busch", catalog)!!
        assertEquals("Versteck der Späherin", info.title)
        compose.setContent {
            Screen {
                androidx.compose.foundation.layout.Column(Modifier.padding(24.dp)) {
                    androidx.compose.material3.Text(info.title, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
                    io.github.aljadvb.worldbuilder.ui.game.TerrainDetails(info, null)
                }
            }
        }
        compose.onNodeWithText("Geländeteil: Dornbusch").assertExists()
        compose.onNodeWithText("In diesem Abenteuer – zusätzlich zur Geländekarte").assertExists()
        compose.onNodeWithText("Bietet Deckung.").assertExists()
        snapshot("09_terrain_dialog")
    }

    /** Local only: battle map with the user's own boards and pieces (WB_PRIVATE_CATALOG); never committed. */
    @Test
    fun privateBattleWithPictures() {
        val path = System.getenv("WB_PRIVATE_CATALOG") ?: return
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val library = CampaignLibrary(context.filesDir, context.assets)
        check(library.importCatalog(File(path).readBytes()) is CampaignLibrary.ImportResult.Success)
        val own = library.catalog()
        val json = assets.open("beispiel-schlacht.json").bufferedReader().readText()
            .replace("\"sx1\"", "\"schlachtplan\"")
            .replace("\"bach_wall\"", "\"fluss_mauer\"")
            .replace("\"busch_kiste\"", "\"gebuesch_felsen\"")
            .replace("\"nebelwolf\"", "\"x\"")
        val sc = FormatJson.decodeCampaign(json).scenarios.single().let { s ->
            val bf = s.chapters.single().battlefield!!
            fun piece(id: String, piece: String, side: io.github.aljadvb.worldbuilder.model.TileSide, x: Int, y: Int) =
                io.github.aljadvb.worldbuilder.model.TerrainPlacement(id, piece, side, area = io.github.aljadvb.worldbuilder.model.GridCoord(x, y))
            val extra = listOf(
                piece("gt_truhe", "truhe_barrikade", io.github.aljadvb.worldbuilder.model.TileSide.A, 0, 0),
                piece("gt_nebel", "nebel_grube", io.github.aljadvb.worldbuilder.model.TileSide.A, 5, 2),
                piece("gt_feuer", "feuerstelle_statue", io.github.aljadvb.worldbuilder.model.TileSide.A, 2, 2),
                piece("gt_anhoehe", "baumstamm_anhoehe", io.github.aljadvb.worldbuilder.model.TileSide.B, 4, 0),
            )
            s.copy(chapters = listOf(s.chapters.single().copy(battlefield = bf.copy(terrain = bf.terrain + extra))))
        }
        val (_, st) = battleSetup(sc)
        compose.setContent {
            Screen { PlayContent(sc, own, st, campaignChanged = false, onCommand = {}, images = io.github.aljadvb.worldbuilder.ui.game.rememberTileImages(library.catalogImageDir)) }
        }
        snapshot("10_private_battle")
    }

    @Test
    fun privateTerrainDetails() {
        val path = System.getenv("WB_PRIVATE_CATALOG") ?: return
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val library = CampaignLibrary(context.filesDir, context.assets)
        check(library.importCatalog(File(path).readBytes()) is CampaignLibrary.ImportResult.Success)
        val own = library.catalog()
        val sc = FormatJson.decodeCampaign(
            assets.open("beispiel-schlacht.json").bufferedReader().readText().replace("\"busch_kiste\"", "\"feuerstelle_statue\"").replace("\"sx1\"", "\"schlachtplan\""),
        ).scenarios.single()
        val custom = sc.terrainInfo("gt_busch", own)!!
        val plain = sc.terrainInfo("gt_busch", own)!!.copy(title = "Feuerstelle", pieceName = null, customRule = null)
        compose.setContent {
            Screen {
                val images = io.github.aljadvb.worldbuilder.ui.game.rememberTileImages(library.catalogImageDir)
                androidx.compose.foundation.layout.Column(Modifier.padding(24.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(24.dp)) {
                    androidx.compose.material3.Text(plain.title, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
                    io.github.aljadvb.worldbuilder.ui.game.TerrainDetails(plain, images)
                    androidx.compose.material3.Text(custom.title, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
                    io.github.aljadvb.worldbuilder.ui.game.TerrainDetails(custom.copy(customRule = custom.customRule!!.copy(mode = io.github.aljadvb.worldbuilder.model.RuleMode.REPLACE)), images)
                }
            }
        }
        snapshot("11_private_terrain_dialog")
    }

    @Test
    fun unexploredTileAndExploring() {
        val sc = scenario.copy(chapters = scenario.chapters.map { ch -> ch.copy(tiles = ch.tiles.map { if (it.id == "pl_dorfweg") it.copy(unexplored = true) else it }) })
        val eng = GameEngine(sc)
        var st = eng.start(2)
        while (!st.isIdle) st = eng.apply(st, Command.Acknowledge)
        compose.setContent { Screen { PlayContent(sc, catalog, st, campaignChanged = false, onCommand = {}) } }
        compose.onNodeWithText("Tippe auf ein Token oder einen Erkundungsmarker (grüner Kompass).").assertExists()
        snapshot("12_unexplored")
    }

    @Test
    fun exploredPrompt() {
        val sc = scenario.copy(chapters = scenario.chapters.map { ch -> ch.copy(tiles = ch.tiles.map { if (it.id == "pl_dorfplatz") it.copy(unexplored = true) else it }) })
        val eng = GameEngine(sc)
        var st = eng.start(2)
        while (!st.isIdle) st = eng.apply(st, Command.Acknowledge)
        st = eng.apply(st, Command.ExploreTile("pl_dorfplatz"))
        compose.setContent { Screen { PlayContent(sc, catalog, st, campaignChanged = false, onCommand = {}) } }
        compose.onNodeWithText("Der erkundende Held oder ein naher Held erhält 1 Inspiration (Marker nehmen). Entfernt den Erkundungsmarker.").assertExists()
        snapshot("13_explored")
    }

    @Test
    fun campScreen() {
        val camp = campaign.copy(places = campaign.places + listOf(
            io.github.aljadvb.worldbuilder.model.Place("pt_bree", "Bree", 0.30, 0.38),
            io.github.aljadvb.worldbuilder.model.Place("pt_wetter", "Wetterspitze", 0.36, 0.33),
        ))
        val progress = io.github.aljadvb.worldbuilder.engine.CampaignProgress(
            heroCount = 2, currentScenario = scenario.id,
            completed = listOf(io.github.aljadvb.worldbuilder.engine.CompletedScenario(scenario.id, "res_muehle_sieg", lore = 4, xp = 3)),
            heroes = listOf(
                io.github.aljadvb.worldbuilder.engine.Hero("Aragorn", "Hauptmann", xp = mapOf("Hauptmann" to io.github.aljadvb.worldbuilder.engine.RoleXp(5, 2)),
                    skills = listOf(io.github.aljadvb.worldbuilder.engine.SkillPurchase("Hauptmann", "Anführen", 2))),
                io.github.aljadvb.worldbuilder.engine.Hero("Bilbo", "Meisterdieb", xp = mapOf("Meisterdieb" to io.github.aljadvb.worldbuilder.engine.RoleXp(3, 0))),
            ),
            lore = 7, inCamp = true,
        )
        compose.setContent { Screen { io.github.aljadvb.worldbuilder.ui.game.CampContent(camp, progress) {} } }
        compose.onNodeWithText("Gesamtwissen: 7").assertExists()
        snapshot("14_camp")
    }

    /** Mill chapter, idle, with the enemy group in play. */
    private fun millWithEnemies(): GameState {
        var s = millSetup().ackUntil { it.isIdle }
        s = engine.apply(s, Command.InteractToken("tok_muehlentor")).ackUntil { it.currentPrompt is Prompt.Test }
        return engine.apply(s, Command.ReportTest(0)).ackUntil { it.isIdle || it.isOver }
    }

    @Test
    fun enemyActivationWithNoTargetButtons() {
        val s = millWithEnemies()
        val round = engine.apply(s, Command.EndRound)
        if (round.currentPrompt !is Prompt.EnemyActivation) return
        var sent: Command? = null
        show(round) { sent = it }
        compose.onNodeWithText("Kein Ziel").assertExists()
        compose.onNodeWithText("Bewegung beendet").assertExists()
        snapshot("15_enemy_activation")
        compose.onNodeWithText("Kein Ziel").performClick()
        assertEquals(Command.NoTarget, sent)
    }

    @Test
    fun counterQuestionAndProvoke() {
        val s = millWithEnemies()
        val group = s.activeEnemyGroups.firstOrNull() ?: return
        var sent: Command? = null
        val shown = androidx.compose.runtime.mutableStateOf(s)
        showMutable(shown) { sent = it }
        compose.onNodeWithText("Provozieren").performClick()
        assertEquals(Command.ProvokeEnemy(group), sent)
        shown.value = engine.apply(s, Command.AttackEnemy(group, hits = 0)).ackUntil { it.currentPrompt is Prompt.CounterQuestion }
        compose.onNodeWithText("Kann der Feind angreifen?").assertExists()
        snapshot("16_counter_question")
    }

    @Test
    fun lastStand() {
        val s = engine.start(3).ackUntil { it.isIdle }
        var sent: Command? = null
        val shown = androidx.compose.runtime.mutableStateOf(s)
        showMutable(shown) { sent = it }
        compose.onNodeWithContentDescription("Mehr").performClick()
        compose.onNodeWithText("Held hat Schaden- oder Furchtlimit erreicht …").performClick()
        compose.onNodeWithText("Held 2").performClick()
        compose.onNodeWithText("Furcht").performClick()
        assertEquals(Command.LimitReached(1, io.github.aljadvb.worldbuilder.engine.LimitKind.FEAR), sent)
        shown.value = engine.apply(s, Command.LimitReached(1, io.github.aljadvb.worldbuilder.engine.LimitKind.FEAR))
        compose.onNodeWithText("Letztes Aufbäumen: Held 2").assertExists()
        snapshot("17_last_stand")
    }

    @Test
    fun itemFound() {
        val sc = scenario.copy(events = scenario.events.map { e ->
            if (e.id == "evt_brunnen") e.copy(steps = listOf(io.github.aljadvb.worldbuilder.model.EventStep.GainItem(text = "Zwischen den Steinen glitzert etwas.")) + e.steps) else e
        })
        val e = GameEngine(sc, items = catalog.items)
        var st = e.start(3)
        while (!st.isIdle) st = e.apply(st, Command.Acknowledge)
        st = e.apply(st, Command.InteractToken("tok_brunnen"))
        while (st.currentPrompt !is Prompt.ItemFound) st = e.apply(st, Command.Acknowledge)
        compose.setContent { Screen { PlayContent(sc, catalog, st, campaignChanged = false, onCommand = {}) } }
        compose.onNodeWithText("Schmuckstück gefunden").assertExists()
        snapshot("18_item_found")
    }

    @Test
    fun enemyMenu() {
        val s = millWithEnemies()
        val group = s.activeEnemyGroups.firstOrNull() ?: return
        val name = catalog.enemyType(scenario.enemyGroup(group)!!.enemyType)!!.name
        show(s)
        compose.onAllNodesWithText(name, substring = true)[0].performClick()
        compose.onNodeWithText("Schließen").assertExists()
        snapshot("19_enemy_menu")
    }
}
