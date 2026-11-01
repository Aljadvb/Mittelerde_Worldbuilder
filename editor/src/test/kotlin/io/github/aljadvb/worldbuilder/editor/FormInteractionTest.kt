package io.github.aljadvb.worldbuilder.editor

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import io.github.aljadvb.worldbuilder.editor.ui.EditorContent
import io.github.aljadvb.worldbuilder.validation.IssueCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class FormInteractionTest {
    @Test
    fun typingIntoATitleEditsTheCampaignAsOneUndoStep() = runComposeUiTest {
        val model = EditorModel(InMemoryEditorPrefs()).apply { openExample(); selection = "res_muehle_sieg" }
        setContent { MaterialTheme(colorScheme = lightColorScheme()) { EditorContent(model) } }

        onNode(hasSetTextAction() and hasText("Das Rad dreht sich wieder")).performTextReplacement("")
        assertTrue(model.report.errors.any { it.code == IssueCode.BLANK_TEXT }, "blank title is reported live")
        onNode(hasSetTextAction() and hasText("")).performTextInput("Neuer Titel")
        assertEquals("Neuer Titel", model.campaign!!.scenarios[0].resolution("res_muehle_sieg")!!.title)

        model.undo()
        assertEquals("Das Rad dreht sich wieder", model.campaign!!.scenarios[0].resolution("res_muehle_sieg")!!.title)
    }

    @Test
    fun usagesNavigateToTheReferencingObject() = runComposeUiTest {
        val model = EditorModel(InMemoryEditorPrefs()).apply { openExample(); selection = "evt_brunnen_erfolg" }
        setContent { MaterialTheme(colorScheme = lightColorScheme()) { EditorContent(model) } }
        onAllNodesWithText("evt_brunnen", substring = true).filterToOne(hasText("• evt_brunnen ", substring = true)).performClick()
        assertEquals("evt_brunnen", model.selection)
    }
}
