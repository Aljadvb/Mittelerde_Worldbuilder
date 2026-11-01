package io.github.aljadvb.worldbuilder.editor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.aljadvb.worldbuilder.editing.usages
import io.github.aljadvb.worldbuilder.editor.EditorModel
import io.github.aljadvb.worldbuilder.editor.Texts

/** Centre panel: the form of the selected object (forms follow in the next step). */
@Composable
fun DetailPanel(model: EditorModel, modifier: Modifier = Modifier) {
    val campaign = model.campaign ?: return
    val id = model.selection ?: campaign.id
    val def = model.definitions.firstOrNull { it.id == id } ?: return
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(def.kind.displayName, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(id, style = MaterialTheme.typography.headlineSmall)
        Text(Texts.USED_BY, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        val usages = campaign.usages(id)
        if (usages.isEmpty()) Text(Texts.NOT_USED, style = MaterialTheme.typography.bodySmall)
        usages.forEach { u ->
            Text("• ${u.ownerId}  (${u.path})", Modifier.clickable { model.selection = u.ownerId }, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
