package io.github.aljadvb.worldbuilder.editor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aljadvb.worldbuilder.editor.EditorModel
import io.github.aljadvb.worldbuilder.editor.Texts
import io.github.aljadvb.worldbuilder.editor.isUnder
import io.github.aljadvb.worldbuilder.validation.Severity

/** Issues for a field, shown below it. Only issues whose path lies under [path]. */
@Composable
fun FieldIssues(model: EditorModel, path: String?) {
    if (path == null) return
    model.report.issues.filter { it.path.isUnder(path) }.forEach { issue ->
        Text(
            issue.message,
            color = if (issue.severity == Severity.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
}

/**
 * Text field bound directly to the campaign. Every keystroke is an edit; consecutive
 * keystrokes in the same field form one undo step via [key].
 */
@Composable
fun TextInput(
    model: EditorModel,
    label: String,
    value: String,
    key: String,
    path: String?,
    multiline: Boolean = false,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onChange: (String) -> (io.github.aljadvb.worldbuilder.model.Campaign) -> io.github.aljadvb.worldbuilder.model.Campaign,
) {
    Column(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = { model.edit(coalesceKey = key, change = onChange(it)) },
            label = { Text(label) },
            singleLine = !multiline,
            minLines = if (multiline) 3 else 1,
            modifier = Modifier.fillMaxWidth().then(if (multiline) Modifier.heightIn(min = 96.dp) else Modifier),
        )
        FieldIssues(model, path)
    }
}

/** Integer field; non-numeric input is ignored. */
@Composable
fun IntInput(
    model: EditorModel,
    label: String,
    value: Int,
    key: String,
    path: String?,
    width: Dp = 140.dp,
    onChange: (Int) -> (io.github.aljadvb.worldbuilder.model.Campaign) -> io.github.aljadvb.worldbuilder.model.Campaign,
) {
    var text by remember(key, value) { mutableStateOf(value.toString()) }
    Column(Modifier.width(width)) {
        OutlinedTextField(
            value = text,
            onValueChange = { input ->
                val filtered = input.filter { it.isDigit() || it == '-' }.take(6)
                text = filtered
                filtered.toIntOrNull()?.let { model.edit(coalesceKey = key, change = onChange(it)) }
            },
            label = { Text(label) },
            singleLine = true,
        )
        FieldIssues(model, path)
    }
}

data class Choice(val id: String, val label: String)

/**
 * Dropdown for a reference or an enum. [onCreate] adds a "Neu anlegen …" entry.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChoiceInput(
    model: EditorModel,
    label: String,
    selected: String?,
    choices: List<Choice>,
    path: String?,
    allowNone: Boolean = false,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onCreate: (() -> Unit)? = null,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = choices.firstOrNull { it.id == selected }?.label
        ?: if (selected.isNullOrEmpty()) Texts.NONE else "$selected ⚠"
    Column(modifier) {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = current,
                onValueChange = {},
                readOnly = true,
                label = { Text(label) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                if (allowNone) DropdownMenuItem(text = { Text(Texts.NONE) }, onClick = { expanded = false; onSelect(null) })
                choices.forEach { c -> DropdownMenuItem(text = { Text(c.label) }, onClick = { expanded = false; onSelect(c.id) }) }
                if (onCreate != null) {
                    DropdownMenuItem(text = { Text(Texts.CREATE_NEW, color = MaterialTheme.colorScheme.primary) }, onClick = { expanded = false; onCreate() })
                }
            }
        }
        FieldIssues(model, path)
    }
}

@Composable
fun CheckInput(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label)
    }
}

/** Asks for a name/title (used when creating objects). */
@Composable
fun NameDialog(title: String, initial: String = "", label: String = Texts.NAME, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(name, { name = it }, label = { Text(label) }, singleLine = true) },
        confirmButton = { Button(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text(Texts.OK) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Texts.CANCEL) } },
    )
}

@Composable
fun ButtonRow(content: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { content() }
}
