package io.github.aljadvb.worldbuilder.editor

import io.github.aljadvb.worldbuilder.editing.updateToken
import io.github.aljadvb.worldbuilder.editor.ui.ancestorsOf
import io.github.aljadvb.worldbuilder.editor.ui.buildTree
import io.github.aljadvb.worldbuilder.model.HexCoord
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditorModelTest {
    private fun model() = EditorModel(InMemoryEditorPrefs())

    @Test
    fun `example opens without findings and is unsaved`() {
        val m = model()
        m.openExample()
        assertEquals(emptyList(), m.report.issues)
        assertTrue(m.document!!.isDirty)
        assertNull(m.document!!.file)
    }

    @Test
    fun `edits validate live and can be undone`() {
        val m = model()
        m.openExample()
        m.edit { c -> c.updateToken("tok_brunnen") { it.copy(location = it.location.copy(cell = HexCoord(9, 9))) } }
        val issue = m.report.errors.single()
        assertEquals("tok_brunnen", m.objectOf(issue))
        assertTrue(m.issuesUnder("scenarios[0].chapters[0]").isNotEmpty())
        assertTrue(m.issuesUnder("scenarios[0].chapters[1]").isEmpty())
        m.undo()
        assertEquals(emptyList(), m.report.issues)
        m.redo()
        assertEquals(1, m.report.errors.size)
    }

    @Test
    fun `rejected edits leave a message`() {
        val m = model()
        m.openExample()
        assertFalse(m.edit { c -> c.updateToken("gibt_es_nicht") { it } })
        assertNotNull(m.message)
    }

    @Test
    fun `save, reopen and recent files`() {
        val dir = Files.createTempDirectory("editor").toFile()
        try {
            val prefs = InMemoryEditorPrefs()
            val m = EditorModel(prefs)
            m.newDocument("Test")
            assertTrue(m.saveAs(File(dir, "test")))
            val file = File(dir, "test.json")
            assertTrue(file.isFile)
            assertFalse(m.document!!.isDirty)
            assertEquals(listOf(file.absolutePath), prefs.recentFiles)
            // saved with errors -> message mentions it
            assertTrue(m.message!!.contains("Fehler"))

            val other = EditorModel(prefs)
            other.open(file)
            assertEquals(m.campaign, other.campaign)
            assertFalse(other.document!!.isDirty)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `broken files report an error instead of crashing`() {
        val file = Files.createTempFile("kaputt", ".json").toFile()
        try {
            file.writeText("{ nicht json")
            val m = model()
            m.open(file)
            assertNull(m.document)
            assertNotNull(m.message)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `tree reveals nested objects`() {
        val m = model()
        m.openExample()
        val tree = buildTree(m.campaign!!, m.catalog)
        assertEquals(
            listOf("cmp_hainbrueck", "scn_muehle", "scn_muehle#chapters", "ch_dorf", "ch_dorf#tokens"),
            tree.ancestorsOf("tok_brunnen"),
        )
    }
}

class NewStepTest {
    @Test
    fun `every step type can be created and serialized`() {
        val m = EditorModel(InMemoryEditorPrefs())
        m.openExample()
        val s = m.campaign!!.scenarios.single()
        Texts.STEP_TYPES.forEach { (type, _) ->
            val step = io.github.aljadvb.worldbuilder.editor.ui.newStep(type, s)
            val c = m.campaign!!.copy(scenarios = listOf(s.copy(events = s.events + io.github.aljadvb.worldbuilder.model.Event("evt_neu", listOf(step)))))
            val json = io.github.aljadvb.worldbuilder.model.FormatJson.encodeCampaign(c)
            assertTrue("\"type\": \"$type\"" in json, type)
        }
    }
}
