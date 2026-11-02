package io.github.aljadvb.worldbuilder.editor.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.aljadvb.worldbuilder.editing.addFlag
import io.github.aljadvb.worldbuilder.editing.addPlace
import io.github.aljadvb.worldbuilder.editing.delete
import io.github.aljadvb.worldbuilder.editing.updateFlag
import io.github.aljadvb.worldbuilder.editing.updatePlace
import io.github.aljadvb.worldbuilder.editor.EditorModel
import io.github.aljadvb.worldbuilder.editor.Texts
import io.github.aljadvb.worldbuilder.hexmap.CampaignMapCanvas
import io.github.aljadvb.worldbuilder.hexmap.CampaignMapScene
import io.github.aljadvb.worldbuilder.hexmap.MapPlace
import io.github.aljadvb.worldbuilder.hexmap.mapCoordinates
import io.github.aljadvb.worldbuilder.hexmap.placeAt
import io.github.aljadvb.worldbuilder.model.Campaign

const val CAMPAIGN_MAP_TAG = "campaign_map"

/** Campaign map: click to select, drag to move; with "add" active a click creates a place. */
@Composable
internal fun CampaignMapEditor(model: EditorModel, c: Campaign, ask: (String, (String) -> Unit) -> Unit) {
    SectionHeader(Texts.CAMPAIGN_MAP)
    Text(Texts.CAMPAIGN_MAP_HINT, style = MaterialTheme.typography.bodySmall)
    var adding by remember { mutableStateOf(false) }
    val selected = model.selection?.takeIf { c.place(it) != null }
    val scene = CampaignMapScene(c.places.map { MapPlace(it.id, it.name, it.x.toFloat(), it.y.toFloat(), selected = it.id == selected) })
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        FilterChip(adding, { adding = !adding }, label = { Text(Texts.ADD_PLACE) })
        selected?.let { id -> OutlinedButton(onClick = { if (model.edit { it.delete(id) }) model.selection = c.id }) { Text(Texts.DELETE) } }
    }
    val sceneState = remember { arrayOf(scene) }
    sceneState[0] = scene
    var dragging by remember { mutableStateOf<String?>(null) }
    CampaignMapCanvas(
        scene, MaterialTheme.colorScheme.primary,
        Modifier.fillMaxWidth().height(420.dp).testTag(CAMPAIGN_MAP_TAG).border(1.dp, MaterialTheme.colorScheme.outlineVariant)
            .pointerInput(adding) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        val pos: Offset = e.changes.first().position
                        val sz = Size(size.width.toFloat(), size.height.toFloat())
                        when (e.type) {
                            PointerEventType.Press -> {
                                val hit = placeAt(sz, sceneState[0], pos)
                                if (hit != null) {
                                    model.selection = hit.id; dragging = hit.id
                                } else if (adding) {
                                    val (x, y) = mapCoordinates(sz, pos)
                                    ask(Texts.NEW_PLACE) { name ->
                                        var id: String? = null
                                        if (model.edit { cc -> cc.addPlace(name, x.toDouble(), y.toDouble()).also { id = it.id }.campaign }) model.selection = id
                                    }
                                    adding = false
                                }
                            }
                            PointerEventType.Move -> dragging?.let { id ->
                                val (x, y) = mapCoordinates(sz, pos)
                                model.edit(coalesceKey = "$id.move") { it.updatePlace(id) { p -> p.copy(x = x.toDouble(), y = y.toDouble()) } }
                            }
                            PointerEventType.Release -> dragging = null
                        }
                    }
                }
            },
    )
    selected?.let { id ->
        val p = c.place(id)!!
        TextInput(model, Texts.PLACE_NAME, p.name, "$id.name", "places[${c.places.indexOf(p)}].name") { v -> { it.updatePlace(id) { x -> x.copy(name = v) } } }
        val used = c.scenarios.filter { it.place == id }.joinToString { it.title }
        Text(if (used.isEmpty()) Texts.PLACE_UNUSED else Texts.placeUsedBy(used), style = MaterialTheme.typography.bodySmall)
    }
}

/** List of campaign flags with their labels. */
@Composable
internal fun FlagsEditor(model: EditorModel, c: Campaign, ask: (String, (String) -> Unit) -> Unit) {
    SectionHeader(Texts.FLAGS)
    Text(Texts.FLAGS_HINT, style = MaterialTheme.typography.bodySmall)
    c.flags.forEachIndexed { i, f ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextInput(model, f.id, f.label, "${f.id}.label", "flags[$i].label", modifier = Modifier.weight(1f)) { v -> { it.updateFlag(f.id) { x -> x.copy(label = v) } } }
            IconButton(onClick = { model.edit { it.delete(f.id) } }) { Icon(Icons.Default.Close, Texts.DELETE) }
        }
    }
    OutlinedButton(onClick = { ask(Texts.NEW_FLAG) { label -> model.edit { it.addFlag(label).campaign } } }) { Text(Texts.ADD_FLAG) }
}

/** Toggle chips for the flags an ending sets. */
@Composable
internal fun FlagChips(c: Campaign, selected: List<String>, onToggle: (String) -> Unit) {
    if (c.flags.isEmpty()) {
        Text(Texts.FLAGS_NONE, style = MaterialTheme.typography.bodySmall)
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        c.flags.forEach { f -> FilterChip(f.id in selected, { onToggle(f.id) }, label = { Text(f.label) }) }
    }
}
