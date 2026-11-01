package io.github.aljadvb.worldbuilder.editor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.aljadvb.worldbuilder.editor.EditorModel
import io.github.aljadvb.worldbuilder.editor.Texts

/** Window content: structure | detail | check, plus a status bar. */
@Composable
fun EditorContent(model: EditorModel) {
    val campaign = model.campaign
    var expanded by remember { mutableStateOf(setOf<String>()) }
    val tree = campaign?.let { buildTree(it, model.catalog) }

    // Open the top levels of a newly loaded campaign …
    LaunchedEffect(campaign?.id) {
        if (tree != null) expanded = expanded + tree.key + tree.children.map { it.key }
    }
    // … and reveal the selected object.
    LaunchedEffect(model.selection, tree) {
        val sel = model.selection ?: return@LaunchedEffect
        tree?.ancestorsOf(sel)?.let { expanded = expanded + it }
    }

    Surface(Modifier.fillMaxSize()) {
        Column {
            if (campaign == null || tree == null) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(Texts.NOTHING_OPEN, style = MaterialTheme.typography.titleMedium)
                        Text(Texts.NOTHING_OPEN_HINT, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                Row(Modifier.weight(1f)) {
                    StructurePanel(
                        model, tree, expanded,
                        onToggle = { key -> expanded = if (key in expanded) expanded - key else expanded + key },
                        modifier = Modifier.width(300.dp).fillMaxHeight(),
                    )
                    VerticalDivider()
                    DetailPanel(model, Modifier.weight(1f).fillMaxHeight())
                    VerticalDivider()
                    IssuesPanel(model, onSelect = { model.selection = it }, modifier = Modifier.width(340.dp).fillMaxHeight())
                }
            }
            HorizontalDivider()
            Text(
                Texts.catalogLabel(model.catalogFile?.name),
                Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }

    model.message?.let { msg ->
        AlertDialog(
            onDismissRequest = { model.message = null },
            confirmButton = { Button(onClick = { model.message = null }) { Text(Texts.OK) } },
            text = { Text(msg) },
        )
    }
}

/** Asks whether to save unsaved changes. */
@Composable
fun UnsavedChangesDialog(onSave: () -> Unit, onDiscard: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(Texts.UNSAVED_TITLE) },
        text = { Text(Texts.UNSAVED_TEXT) },
        confirmButton = { Button(onClick = onSave) { Text(Texts.SAVE) } },
        dismissButton = {
            Row {
                TextButton(onClick = onDiscard) { Text(Texts.DISCARD) }
                TextButton(onClick = onCancel) { Text(Texts.CANCEL) }
            }
        },
    )
}
