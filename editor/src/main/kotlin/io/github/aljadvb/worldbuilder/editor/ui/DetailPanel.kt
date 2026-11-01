package io.github.aljadvb.worldbuilder.editor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.aljadvb.worldbuilder.editing.Definition
import io.github.aljadvb.worldbuilder.editing.Ids
import io.github.aljadvb.worldbuilder.editing.delete
import io.github.aljadvb.worldbuilder.editing.rename
import io.github.aljadvb.worldbuilder.editing.scenarioOf
import io.github.aljadvb.worldbuilder.editing.usages
import io.github.aljadvb.worldbuilder.editor.EditorModel
import io.github.aljadvb.worldbuilder.editor.Texts
import io.github.aljadvb.worldbuilder.validation.ObjectKind

/** Centre panel: header (kind, ID, rename, delete) + the form of the selected object + usages. */
@Composable
fun DetailPanel(model: EditorModel, modifier: Modifier = Modifier) {
    val campaign = model.campaign ?: return
    val id = model.selection ?: campaign.id
    val def = model.definitions.firstOrNull { it.id == id } ?: return
    var renaming by remember(id) { mutableStateOf(false) }
    var deleting by remember(id) { mutableStateOf(false) }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(def.kind.displayName, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(id, style = MaterialTheme.typography.headlineSmall)
            }
            TextButton(onClick = { renaming = true }) { Text(Texts.RENAME) }
            if (def.kind != ObjectKind.CAMPAIGN) {
                OutlinedButton(onClick = { deleting = true }, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text(Texts.DELETE)
                }
            }
        }
        Column(Modifier.widthIn(max = 820.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ObjectForm(model, def)
        }
        UsagesSection(model, id)
    }

    if (renaming) RenameDialog(model, id, onDone = { renaming = false })
    if (deleting) DeleteDialog(model, def, onDone = { deleting = false })
}

@Composable
private fun UsagesSection(model: EditorModel, id: String) {
    val campaign = model.campaign ?: return
    SectionHeader(Texts.USED_BY)
    val usages = campaign.usages(id)
    if (usages.isEmpty()) Text(Texts.NOT_USED, style = MaterialTheme.typography.bodySmall)
    usages.forEach { u ->
        Text(
            "• ${u.ownerId}  ·  ${u.path}",
            Modifier.clickable { model.selection = u.ownerId },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun RenameDialog(model: EditorModel, id: String, onDone: () -> Unit) {
    var newId by remember { mutableStateOf(id) }
    val valid = Ids.isValid(newId) && (newId == id || newId !in model.definitions.map { it.id })
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(Texts.RENAME_TITLE) },
        text = {
            Column {
                OutlinedTextField(newId, { newId = it.trim() }, label = { Text(Texts.ID) }, singleLine = true, isError = !valid)
                Text(Texts.RENAME_HINT, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(enabled = valid, onClick = {
                if (model.edit { it.rename(id, newId) }) model.selection = newId
                onDone()
            }) { Text(Texts.OK) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(Texts.CANCEL) } },
    )
}

@Composable
private fun DeleteDialog(model: EditorModel, def: Definition, onDone: () -> Unit) {
    val campaign = model.campaign ?: return
    val usages = campaign.usages(def.id)
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("${def.kind.displayName} „${def.id}“ – ${Texts.DELETE_TITLE}") },
        text = {
            Column {
                Text(if (usages.isEmpty()) Texts.DELETE_UNUSED else Texts.DELETE_USED)
                usages.forEach { Text("• ${it.ownerId} (${it.path})", style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parent = campaign.scenarioOf(def.id)?.id?.takeIf { it != def.id } ?: campaign.id
                    if (model.edit { it.delete(def.id) }) model.selection = parent
                    onDone()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text(Texts.DELETE) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(Texts.CANCEL) } },
    )
}

@Composable
fun Gap() = Spacer(Modifier.padding(2.dp))
