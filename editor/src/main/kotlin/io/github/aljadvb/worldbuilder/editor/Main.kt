package io.github.aljadvb.worldbuilder.editor

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.aljadvb.worldbuilder.editor.ui.EditorContent
import io.github.aljadvb.worldbuilder.editor.ui.UnsavedChangesDialog
import java.io.File

fun main() = application {
    val model = remember { EditorModel(SystemEditorPrefs()) }
    val windowState = rememberWindowState(placement = WindowPlacement.Maximized, width = 1400.dp, height = 900.dp)
    /** Action waiting for the unsaved-changes decision. */
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun guarded(action: () -> Unit) {
        if (model.document?.isDirty == true) pending = action else action()
    }
    fun saveOrAsk(): Boolean = model.save() || FileDialogs.saveJson(window = null, model.document?.file?.name)?.let(model::saveAs) == true

    val doc = model.document
    val title = buildString {
        if (doc != null) {
            if (doc.isDirty) append("• ")
            append(doc.file?.name ?: Texts.UNTITLED)
            append(" – ")
        }
        append(Texts.APP_TITLE)
    }

    Window(onCloseRequest = { guarded(::exitApplication) }, state = windowState, title = title) {
        MenuBar {
            Menu(Texts.MENU_FILE, mnemonic = 'D') {
                Item(Texts.MENU_NEW, shortcut = KeyShortcut(Key.N, ctrl = true)) { guarded { model.newDocument() } }
                Item(Texts.MENU_OPEN, shortcut = KeyShortcut(Key.O, ctrl = true)) {
                    guarded { FileDialogs.openJson(window)?.let(model::open) }
                }
                Item(Texts.MENU_OPEN_EXAMPLE) {
                    guarded { model.openExample() }
                }
                if (model.recentFiles.isNotEmpty()) {
                    Menu(Texts.MENU_RECENT) {
                        model.recentFiles.forEach { path -> Item(path) { guarded { model.open(File(path)) } } }
                    }
                }
                Separator()
                Item(Texts.MENU_SAVE, enabled = doc != null, shortcut = KeyShortcut(Key.S, ctrl = true)) { saveOrAsk() }
                Item(Texts.MENU_SAVE_AS, enabled = doc != null, shortcut = KeyShortcut(Key.S, ctrl = true, shift = true)) {
                    FileDialogs.saveJson(window, doc?.file?.name)?.let(model::saveAs)
                }
                Separator()
                Item(Texts.MENU_CATALOG) { FileDialogs.openJson(window)?.let(model::loadCatalog) }
                Item(Texts.MENU_CATALOG_EXAMPLE, enabled = model.catalogFile != null) { model.useExampleCatalog() }
                Separator()
                Item(Texts.MENU_CLOSE, enabled = doc != null) { guarded { model.close() } }
                Item(Texts.MENU_EXIT) { guarded(::exitApplication) }
            }
            Menu(Texts.MENU_EDIT, mnemonic = 'B') {
                Item(Texts.MENU_UNDO, enabled = doc?.history?.canUndo == true, shortcut = KeyShortcut(Key.Z, ctrl = true)) { model.undo() }
                Item(Texts.MENU_REDO, enabled = doc?.history?.canRedo == true, shortcut = KeyShortcut(Key.Y, ctrl = true)) { model.redo() }
            }
        }
        MaterialTheme(colorScheme = lightColorScheme()) {
            EditorContent(model)
            pending?.let { action ->
                UnsavedChangesDialog(
                    onSave = { if (saveOrAsk()) { pending = null; action() } },
                    onDiscard = { pending = null; action() },
                    onCancel = { pending = null },
                )
            }
        }
    }
}
