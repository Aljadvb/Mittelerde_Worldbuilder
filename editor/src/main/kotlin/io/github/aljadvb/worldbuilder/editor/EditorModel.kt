package io.github.aljadvb.worldbuilder.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.aljadvb.worldbuilder.editing.Definition
import io.github.aljadvb.worldbuilder.editing.EditException
import io.github.aljadvb.worldbuilder.editing.History
import io.github.aljadvb.worldbuilder.editing.definitions
import io.github.aljadvb.worldbuilder.editing.newCampaign
import io.github.aljadvb.worldbuilder.model.Campaign
import io.github.aljadvb.worldbuilder.model.FormatJson
import io.github.aljadvb.worldbuilder.model.MaterialCatalog
import io.github.aljadvb.worldbuilder.validation.CampaignValidator
import io.github.aljadvb.worldbuilder.validation.CatalogValidator
import io.github.aljadvb.worldbuilder.validation.ValidationIssue
import io.github.aljadvb.worldbuilder.validation.ValidationReport
import java.io.File

/** An open campaign file with its undo history. */
data class EditorDocument(
    val file: File?,
    val history: History<Campaign>,
    val saved: Campaign?,
) {
    val campaign: Campaign get() = history.present
    val isDirty: Boolean get() = campaign != saved
}

/**
 * State and actions of the editor window. Compose state, but no UI code,
 * so it can be tested without a window.
 */
class EditorModel(private val prefs: EditorPrefs) {
    var catalog: MaterialCatalog by mutableStateOf(loadExampleCatalog())
        private set
    var catalogFile: File? by mutableStateOf(null)
        private set
    /** Tile pictures of the current catalog (resolved relative to its folder). */
    var tileImages by mutableStateOf(CatalogFiles.imageCache(null))
        private set
    var document: EditorDocument? by mutableStateOf(null)
        private set
    var report: ValidationReport by mutableStateOf(ValidationReport(emptyList()))
        private set
    var definitions: List<Definition> by mutableStateOf(emptyList())
        private set
    var selection: String? by mutableStateOf(null)
    /** One-off message for the status bar / dialog. */
    var message: String? by mutableStateOf(null)
    var recentFiles: List<String> by mutableStateOf(prefs.recentFiles)
        private set

    init {
        prefs.catalogPath?.let { path -> runCatching { loadCatalog(File(path)) } }
    }

    val campaign: Campaign? get() = document?.campaign

    // ---- documents ---------------------------------------------------------------

    fun newDocument(title: String = "Neue Kampagne") {
        replaceDocument(EditorDocument(null, History(newCampaign(title)), saved = null))
    }

    fun open(file: File) {
        val campaign = try {
            FormatJson.decodeCampaign(file.readText())
        } catch (e: Exception) {
            message = Texts.openFailed(file.name, e.message ?: e.javaClass.simpleName)
            return
        }
        replaceDocument(EditorDocument(file, History(campaign), saved = campaign))
        remember(file)
    }

    /** Opens a campaign that was created elsewhere (e.g. by the generator) as a new, unsaved document. */
    fun openUnsaved(campaign: Campaign) {
        replaceDocument(EditorDocument(null, History(campaign), saved = null))
    }

    /** Opens the bundled example as a new, unsaved document. */
    fun openExample() {
        val campaign = FormatJson.decodeCampaign(exampleCampaignText())
        replaceDocument(EditorDocument(null, History(campaign), saved = null))
    }

    /** Saves to the current file; returns false if no file is set (use [saveAs]). */
    fun save(): Boolean {
        val doc = document ?: return true
        val file = doc.file ?: return false
        return writeTo(file)
    }

    fun saveAs(file: File): Boolean {
        val target = if (file.extension.equals("json", ignoreCase = true)) file else File(file.path + ".json")
        return writeTo(target)
    }

    private fun writeTo(file: File): Boolean {
        val doc = document ?: return true
        return try {
            file.writeText(FormatJson.encodeCampaign(doc.campaign) + "\n")
            document = doc.copy(file = file, saved = doc.campaign)
            remember(file)
            message = if (report.isValid) Texts.saved(file.name) else Texts.savedWithErrors(file.name, report.errors.size)
            true
        } catch (e: Exception) {
            message = Texts.saveFailed(e.message ?: e.javaClass.simpleName)
            false
        }
    }

    fun close() = replaceDocument(null)

    private fun replaceDocument(doc: EditorDocument?) {
        document = doc
        selection = doc?.campaign?.id
        revalidate()
    }

    private fun remember(file: File) {
        recentFiles = (listOf(file.absolutePath) + recentFiles.filter { it != file.absolutePath }).take(8)
        prefs.recentFiles = recentFiles
    }

    // ---- catalog -----------------------------------------------------------------

    /** Loads a catalog from material.json or from a ZIP with pictures. */
    fun loadCatalog(file: File) {
        val json = try {
            if (file.extension.equals("zip", ignoreCase = true)) CatalogFiles.unpack(file) else file
        } catch (e: Exception) {
            message = Texts.openFailed(file.name, e.message ?: e.javaClass.simpleName)
            return
        }
        val loaded = try {
            FormatJson.decodeCatalog(json.readText())
        } catch (e: Exception) {
            message = Texts.openFailed(file.name, e.message ?: e.javaClass.simpleName)
            return
        }
        val catalogReport = CatalogValidator.validate(loaded)
        if (!catalogReport.isValid) {
            message = Texts.catalogInvalid(catalogReport.errors.first().message)
            return
        }
        catalog = loaded
        catalogFile = json
        tileImages = CatalogFiles.imageCache(json.parentFile)
        prefs.catalogPath = json.absolutePath
        revalidate()
    }

    fun useExampleCatalog() {
        catalog = loadExampleCatalog()
        catalogFile = null
        tileImages = CatalogFiles.imageCache(null)
        prefs.catalogPath = null
        revalidate()
    }

    // ---- editing -----------------------------------------------------------------

    /**
     * Applies an edit. Edits with the same [coalesceKey] in a row form one undo step
     * (use the field identity for text input). Rejected edits set [message].
     */
    fun edit(coalesceKey: String? = null, change: (Campaign) -> Campaign): Boolean {
        val doc = document ?: return false
        val updated = try {
            change(doc.campaign)
        } catch (e: EditException) {
            message = e.message
            return false
        }
        document = doc.copy(history = doc.history.push(updated, coalesceKey))
        revalidate()
        return true
    }

    fun undo() = document?.let { document = it.copy(history = it.history.undo()); revalidate() }
    fun redo() = document?.let { document = it.copy(history = it.history.redo()); revalidate() }

    private fun revalidate() {
        val c = campaign
        report = if (c == null) ValidationReport(emptyList()) else CampaignValidator.validate(c, catalog)
        definitions = c?.definitions().orEmpty()
        if (selection != null && definitions.none { it.id == selection }) selection = c?.id
    }

    // ---- issues ------------------------------------------------------------------

    /** Issues located at or below [path]. */
    fun issuesUnder(path: String): List<ValidationIssue> = report.issues.filter { it.path.isUnder(path) }

    /** The innermost object containing an issue's location. */
    fun objectOf(issue: ValidationIssue): String? =
        definitions.filter { issue.path.isUnder(it.path) }.maxByOrNull { it.path.length }?.id

    companion object {
        fun loadExampleCatalog(): MaterialCatalog =
            FormatJson.decodeCatalog(EditorModel::class.java.getResource("/beispiel-material.json")!!.readText())

        fun exampleCampaignText(): String =
            EditorModel::class.java.getResource("/beispiel-kampagne.json")!!.readText()
    }
}

/** True if this validator path lies at or below [parent] ("" is the root). */
fun String.isUnder(parent: String): Boolean =
    parent.isEmpty() || this == parent || startsWith("$parent.") || startsWith("$parent[")
