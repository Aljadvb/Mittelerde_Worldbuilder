package io.github.aljadvb.worldbuilder.editor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.aljadvb.worldbuilder.editing.TableSize
import io.github.aljadvb.worldbuilder.editor.Texts

@Composable
fun TableDialog(current: TableSize, onSave: (TableSize) -> Unit, onDismiss: () -> Unit) {
    var w by remember { mutableStateOf(current.widthCm.toInt().toString()) }
    var h by remember { mutableStateOf(current.heightCm.toInt().toString()) }
    val width = w.toDoubleOrNull()?.takeIf { it in 20.0..500.0 }
    val height = h.toDoubleOrNull()?.takeIf { it in 20.0..500.0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Texts.TABLE_TITLE) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.width(420.dp)) {
                Text(Texts.TABLE_HINT, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(w, { w = it.filter(Char::isDigit).take(3) }, label = { Text(Texts.TABLE_WIDTH) }, isError = width == null, singleLine = true, modifier = Modifier.width(150.dp))
                    OutlinedTextField(h, { h = it.filter(Char::isDigit).take(3) }, label = { Text(Texts.TABLE_HEIGHT) }, isError = height == null, singleLine = true, modifier = Modifier.width(150.dp))
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(TableSize(width!!, height!!)) }, enabled = width != null && height != null) { Text(Texts.OK) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Texts.CANCEL) } },
    )
}
