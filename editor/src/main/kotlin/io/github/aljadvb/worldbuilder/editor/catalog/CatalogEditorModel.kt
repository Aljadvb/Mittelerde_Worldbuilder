package io.github.aljadvb.worldbuilder.editor.catalog

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.aljadvb.worldbuilder.editing.EditException
import io.github.aljadvb.worldbuilder.editing.History
import io.github.aljadvb.worldbuilder.editor.CatalogFiles
import io.github.aljadvb.worldbuilder.editor.Texts
import io.github.aljadvb.worldbuilder.model.FormatJson
import io.github.aljadvb.worldbuilder.model.MaterialCatalog
import io.github.aljadvb.worldbuilder.model.TileSide
import io.github.aljadvb.worldbuilder.validation.CatalogValidator
import io.github.aljadvb.worldbuilder.validation.ValidationReport
import java.io.File

sealed interface CatalogSelection {
    data class Tile(val id: String) : CatalogSelection
    data class Enemy(val id: String) : CatalogSelection
    data class Expansion(val id: String) : CatalogSelection
    data class Board(val id: String) : CatalogSelection
    data class Piece(val id: String) : CatalogSelection
}

/** State of the catalog editor window. */
class CatalogEditorModel(initial: MaterialCatalog, file: File?, private val onSaved: (File) -> Unit) {
    var history by mutableStateOf(History(initial))
        private set
    var file by mutableStateOf(file)
        private set
    private var saved by mutableStateOf<MaterialCatalog?>(if (file != null) initial else null)
    var selection by mutableStateOf<CatalogSelection?>(initial.tiles.firstOrNull()?.let { CatalogSelection.Tile(it.id) })
    var side by mutableStateOf(TileSide.A)
    var message by mutableStateOf<String?>(null)

    /** Pictures resolve relative to the folder of the catalog file. */
    var tileImages by mutableStateOf(CatalogFiles.imageCache(file?.parentFile))
        private set

    val catalog: MaterialCatalog get() = history.present
    val isDirty: Boolean get() = catalog != saved
    val report: ValidationReport get() = CatalogValidator.validate(catalog)

    fun edit(key: String? = null, change: (MaterialCatalog) -> MaterialCatalog): Boolean = try {
        history = history.push(change(catalog), key)
        true
    } catch (e: EditException) {
        message = e.message
        false
    }

    fun undo() { history = history.undo() }
    fun redo() { history = history.redo() }

    /** Writes material.json and all pictures into a ZIP for the app. */
    fun exportZip(target: File): Boolean = try {
        CatalogFiles.exportZip(FormatJson.encodeCatalog(catalog) + "\n", catalog, file?.parentFile, target)
        message = Texts.exported(target.name)
        true
    } catch (e: Exception) {
        message = Texts.saveFailed(e.message ?: e.javaClass.simpleName)
        false
    }

    fun save(): Boolean = file?.let(::saveAs) ?: false

    fun saveAs(target: File): Boolean {
        val f = if (target.extension.equals("json", true)) target else File(target.path + ".json")
        if (!report.isValid) {
            message = Texts.CATALOG_SAVE_INVALID
            return false
        }
        return try {
            CatalogFiles.copyImages(catalog, file?.parentFile, f.absoluteFile.parentFile)
            f.writeText(FormatJson.encodeCatalog(catalog) + "\n")
            if (file?.parentFile?.canonicalPath != f.absoluteFile.parentFile.canonicalPath) tileImages = CatalogFiles.imageCache(f.absoluteFile.parentFile)
            file = f
            saved = catalog
            onSaved(f)
            true
        } catch (e: Exception) {
            message = Texts.saveFailed(e.message ?: e.javaClass.simpleName)
            false
        }
    }
}
