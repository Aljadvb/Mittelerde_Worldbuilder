package io.github.aljadvb.worldbuilder.editor

import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** Native file dialogs (on Windows the system's Open/Save dialogs). */
object FileDialogs {
    fun openJson(window: Frame?): File? = show(window, "Öffnen", FileDialog.LOAD, null)

    /** Catalog: material.json or a ZIP with pictures. */
    fun openCatalog(window: Frame?): File? = show(window, "Material-Katalog öffnen", FileDialog.LOAD, "*.json;*.zip", setOf("json", "zip"))

    /** Plain text source for the generator (.txt/.md). */
    fun openText(window: Frame?): File? = show(window, "Vorlage laden", FileDialog.LOAD, "*.txt;*.md", setOf("txt", "md"))

    fun saveZip(window: Frame?, suggestedName: String): File? =
        show(window, "Als ZIP exportieren", FileDialog.SAVE, suggestedName, setOf("zip"))?.let { if (it.extension.equals("zip", true)) it else File(it.path + ".zip") }

    fun saveJson(window: Frame?, suggestedName: String?): File? =
        show(window, "Speichern unter", FileDialog.SAVE, suggestedName ?: "kampagne.json")

    private fun show(window: Frame?, title: String, mode: Int, file: String?, extensions: Set<String> = setOf("json")): File? {
        val dialog = FileDialog(window, title, mode)
        dialog.file = file ?: "*.json"
        dialog.setFilenameFilter { _, name -> extensions.any { name.endsWith(".$it", ignoreCase = true) } }
        dialog.isVisible = true
        val name = dialog.file ?: return null
        return File(dialog.directory, name)
    }
}
