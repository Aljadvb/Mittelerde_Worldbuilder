package io.github.aljadvb.worldbuilder.editor.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.aljadvb.worldbuilder.editing.removeBattleBoard
import io.github.aljadvb.worldbuilder.editing.removeTerrainPiece
import io.github.aljadvb.worldbuilder.editing.updateBattleBoard
import io.github.aljadvb.worldbuilder.editing.updatePieceSide
import io.github.aljadvb.worldbuilder.editing.updateTerrainPiece
import io.github.aljadvb.worldbuilder.editor.Texts
import io.github.aljadvb.worldbuilder.editor.ui.CheckInput
import io.github.aljadvb.worldbuilder.model.PiecePlacement
import io.github.aljadvb.worldbuilder.model.Terrain
import io.github.aljadvb.worldbuilder.model.TileSide

@Composable
private fun CountField(label: String, value: Int, onChange: (Int) -> Unit) {
    OutlinedTextField(
        value.toString(),
        { v -> v.filter(Char::isDigit).take(3).toIntOrNull()?.let(onChange) },
        label = { Text(label) }, singleLine = true, modifier = Modifier.width(260.dp),
    )
}

@Composable
private fun SideChips(model: CatalogEditorModel, sides: Set<TileSide>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        sides.sorted().forEach { s -> FilterChip(selected = model.side == s, onClick = { model.side = s }, label = { Text("${Texts.SIDE} $s") }) }
    }
}

@Composable
private fun TerrainTags(selected: List<String>, onToggle: (String) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Terrain.TAGS.forEach { tag -> FilterChip(selected = tag.id in selected, onClick = { onToggle(tag.id) }, label = { Text(tag.label) }) }
    }
}

private fun List<String>.toggle(id: String) = if (id in this) this - id else this + id

@Composable
internal fun BoardDetail(model: CatalogEditorModel, id: String) {
    val board = model.catalog.battleBoard(id) ?: return
    Text("${Texts.BOARD} · ${board.id}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    OutlinedTextField(board.label, { v -> model.edit("$id.label") { it.updateBattleBoard(id) { b -> b.copy(label = v) } } }, label = { Text(Texts.CATALOG_LABEL) }, singleLine = true)
    ExpansionChoice(model, board.expansion) { x -> model.edit { it.updateBattleBoard(id) { b -> b.copy(expansion = x) } } }
    CountField(Texts.CATALOG_COUNT, board.count) { n -> model.edit("$id.count") { it.updateBattleBoard(id) { b -> b.copy(count = n) } } }
    SideChips(model, board.sides.keys)
    val side = board.sides[model.side] ?: return
    Text(Texts.boardFields(side.width, side.height, side.fields().size, side.cellSizeMm))
    Text("${Texts.CATALOG_PICTURE}: ${side.image?.file ?: Texts.CATALOG_NO_PICTURE}", style = MaterialTheme.typography.bodySmall)
    OutlinedTextField(
        side.description,
        { v -> model.edit("$id.${model.side}.desc") { it.updateBattleBoard(id) { b -> b.copy(sides = b.sides + (model.side to side.copy(description = v))) } } },
        label = { Text(Texts.DESCRIPTION) }, modifier = Modifier.fillMaxWidth(),
    )
    TerrainTags(side.terrain) { tag -> model.edit { it.updateBattleBoard(id) { b -> b.copy(sides = b.sides + (model.side to side.copy(terrain = side.terrain.toggle(tag)))) } } }
    OutlinedButton(onClick = { model.edit { it.removeBattleBoard(id) }; model.selection = null }) { Text(Texts.DELETE) }
}

@Composable
internal fun PieceDetail(model: CatalogEditorModel, id: String) {
    val piece = model.catalog.terrainPiece(id) ?: return
    Text("${Texts.PIECE} · ${piece.id}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    ExpansionChoice(model, piece.expansion) { x -> model.edit { it.updateTerrainPiece(id) { p -> p.copy(expansion = x) } } }
    CountField(Texts.CATALOG_PIECE_COUNT, piece.count) { n -> model.edit("$id.count") { it.updateTerrainPiece(id) { p -> p.copy(count = n) } } }
    SideChips(model, piece.sides.keys)
    val sideKey = model.side
    val side = piece.sides[sideKey] ?: return
    fun edit(key: String, f: (io.github.aljadvb.worldbuilder.model.TerrainPieceSide) -> io.github.aljadvb.worldbuilder.model.TerrainPieceSide) =
        model.edit("$id.$sideKey.$key") { it.updatePieceSide(id, sideKey, f) }
    OutlinedTextField(side.name, { v -> edit("name") { s -> s.copy(name = v) } }, label = { Text(Texts.NAME) }, singleLine = true)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(PiecePlacement.AREA to Texts.BATTLE_IN_FIELD, PiecePlacement.EDGE to Texts.BATTLE_ON_BORDER).forEach { (p, label) ->
            FilterChip(selected = side.placement == p, onClick = { model.edit { it.updatePieceSide(id, sideKey) { s -> s.copy(placement = p) } } }, label = { Text(label) })
        }
    }
    CheckInput(Texts.CATALOG_BLOCKS, side.blocksMovement) { v -> model.edit { it.updatePieceSide(id, sideKey) { s -> s.copy(blocksMovement = v) } } }
    Text("${Texts.CATALOG_PICTURE}: ${side.image?.file ?: Texts.CATALOG_NO_PICTURE}", style = MaterialTheme.typography.bodySmall)
    OutlinedTextField(side.effect, { v -> edit("effect") { s -> s.copy(effect = v) } }, label = { Text(Texts.CATALOG_PIECE_EFFECT) }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(side.cardText, { v -> edit("card") { s -> s.copy(cardText = v) } }, label = { Text(Texts.CATALOG_CARD_TEXT) }, minLines = 3, modifier = Modifier.fillMaxWidth())
    TerrainTags(side.terrain) { tag -> model.edit { it.updatePieceSide(id, sideKey) { s -> s.copy(terrain = s.terrain.toggle(tag)) } } }
    OutlinedButton(onClick = { model.edit { it.removeTerrainPiece(id) }; model.selection = null }) { Text(Texts.DELETE) }
}
