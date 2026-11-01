package io.github.aljadvb.worldbuilder.editor

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.aljadvb.worldbuilder.editor.play.PlaytestModel
import io.github.aljadvb.worldbuilder.editor.play.PlaytestView
import io.github.aljadvb.worldbuilder.engine.Command
import io.github.aljadvb.worldbuilder.engine.Prompt
import io.github.aljadvb.worldbuilder.model.FormatJson
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class PlaytestTest {
    private val campaign = FormatJson.decodeCampaign(EditorModel.exampleCampaignText())
    private val catalog = EditorModel.loadExampleCatalog()

    private fun PlaytestModel.ackUntil(stop: (Prompt?) -> Boolean) {
        while (!stop(state!!.currentPrompt)) send(Command.Acknowledge)
    }

    @Test
    fun stepBackUndoesTheLastCommand() {
        val game = PlaytestModel(campaign, "scn_muehle", 2)
        game.ackUntil { it == null }
        val idle = game.state
        game.send(Command.EndRound)
        assertEquals(3, game.state!!.threat)
        game.stepBack()
        assertEquals(idle, game.state)
    }

    @Test
    fun defeatEndsTheCampaignWithoutTransition() {
        val game = PlaytestModel(campaign, "scn_muehle", 5)
        while (game.state?.isOver == false) {
            if (game.state!!.isIdle) game.send(Command.EndRound) else game.send(Command.Acknowledge)
        }
        game.continueCampaign()
        assertNull(game.state)
        assertTrue(game.progress.isCampaignOver)
        game.stepBack()
        assertTrue(game.state!!.isOver)
    }

    @Test
    fun playingThroughTheWindow() = runComposeUiTest {
        val game = PlaytestModel(campaign, "scn_muehle", 2)
        setContent { MaterialTheme(colorScheme = lightColorScheme()) { PlaytestView(game, catalog) {} } }
        repeat(4) { onNodeWithText(Texts.CONTINUE).performClick() } // intro, setup, objective, arrival text
        onNodeWithText("Alter Brunnen").performClick()
        waitForIdle()
        assertTrue(game.state!!.currentPrompt is Prompt.Inspect)
        repeat(2) { onNodeWithText(Texts.CONTINUE).performClick() }
        onNodeWithText("+").performClick()
        onNodeWithText("+").performClick()
        onNodeWithText(Texts.OK).performClick()
        waitForIdle()
        assertTrue((game.state!!.currentPrompt as Prompt.Text).text.contains("Messingschlüssel"))
        assertFalse("tok_brunnen" in game.state!!.activeTokens)
    }

    @Test
    fun screenshot() {
        val game = PlaytestModel(campaign, "scn_muehle", 3)
        game.ackUntil { it is Prompt.Setup }
        val scene = ImageComposeScene(1400, 860, Density(1f)) { MaterialTheme(colorScheme = lightColorScheme()) { PlaytestView(game, catalog) {} } }
        try {
            scene.render()
            File("build/screenshots").mkdirs()
            File("build/screenshots/e12_playtest.png").writeBytes(scene.render(1_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        } finally {
            scene.close()
        }
    }
}
