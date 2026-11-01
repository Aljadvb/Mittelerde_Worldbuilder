package io.github.aljadvb.worldbuilder.editor

import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** Native file dialogs (on Windows the system's Open/Save dialogs). */
object FileDialogs {
    fun openJson(window: Frame?): File? = show(window, "Öffnen", FileDialog.LOAD, null)

    fun saveJson(window: Frame?, suggestedName: String?): File? =
        show(window, "Speichern unter", FileDialog.SAVE, suggestedName ?: "kampagne.json")

    private fun show(window: Frame?, title: String, mode: Int, file: String?): File? {
        val dialog = FileDialog(window, title, mode)
        dialog.file = file ?: "*.json"
        dialog.setFilenameFilter { _, name -> name.endsWith(".json", ignoreCase = true) }
        dialog.isVisible = true
        val name = dialog.file ?: return null
        return File(dialog.directory, name)
    }
}
