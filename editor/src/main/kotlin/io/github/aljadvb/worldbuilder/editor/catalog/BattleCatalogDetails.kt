package io.github.aljadvb.worldbuilder.editor.catalog

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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


@Composable
internal fun EnemyStats(model: CatalogEditorModel, e: io.github.aljadvb.worldbuilder.model.EnemyType) {
    val id = e.id
    fun upd(key: String, f: (io.github.aljadvb.worldbuilder.model.EnemyType) -> io.github.aljadvb.worldbuilder.model.EnemyType) =
        model.edit("$id.$key") { c -> c.copy(enemyTypes = c.enemyTypes.map { if (it.id == id) f(it) else it }) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            Triple(Texts.ENEMY_HEALTH, e.health) { v: Int -> upd("health") { it.copy(health = v.coerceAtLeast(1)) } },
            Triple(Texts.ENEMY_ARMOR, e.armor) { v: Int -> upd("armor") { it.copy(armor = v) } },
            Triple(Texts.ENEMY_SORCERY, e.sorcery) { v: Int -> upd("sorcery") { it.copy(sorcery = v) } },
            Triple(Texts.ENEMY_MOVEMENT, e.movement) { v: Int -> upd("movement") { it.copy(movement = v) } },
        ).forEach { (label, value, set) ->
            OutlinedTextField(value.toString(), { v -> v.filter(Char::isDigit).take(2).toIntOrNull()?.let(set) }, label = { Text(label) }, singleLine = true, modifier = Modifier.width(140.dp))
        }
    }
    CheckInput(Texts.ENEMY_RANGED, e.ranged) { v -> upd("ranged") { it.copy(ranged = v) } }
    Text(Texts.ENEMY_ATTACKS, style = MaterialTheme.typography.labelLarge)
    e.attacks.forEachIndexed { i, a ->
        fun setA(f: (io.github.aljadvb.worldbuilder.model.EnemyAttack) -> io.github.aljadvb.worldbuilder.model.EnemyAttack) =
            upd("attack$i") { t -> t.copy(attacks = t.attacks.mapIndexed { j, x -> if (j == i) f(x) else x }) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(a.damage.toString(), { v -> v.filter(Char::isDigit).take(2).toIntOrNull()?.let { n -> setA { it.copy(damage = n) } } }, label = { Text(Texts.DAMAGE) }, singleLine = true, modifier = Modifier.width(110.dp))
            OutlinedTextField(a.fear.toString(), { v -> v.filter(Char::isDigit).take(2).toIntOrNull()?.let { n -> setA { it.copy(fear = n) } } }, label = { Text(Texts.FEAR) }, singleLine = true, modifier = Modifier.width(110.dp))
            OutlinedTextField(a.text, { v -> setA { it.copy(text = v) } }, label = { Text(Texts.TEXT) }, modifier = Modifier.weight(1f))
            NegateInput(a.negate) { v -> setA { it.copy(negate = v) } }
            ConditionInput(a.condition) { v -> setA { it.copy(condition = v) } }
            androidx.compose.material3.TextButton(onClick = { upd("attacks") { t -> t.copy(attacks = t.attacks.filterIndexed { j, _ -> j != i }) } }) { Text("✕") }
        }
    }
    OutlinedButton(onClick = { upd("attacks") { it.copy(attacks = it.attacks + io.github.aljadvb.worldbuilder.model.EnemyAttack(damage = 1)) } }) { Text(Texts.ENEMY_ADD_ATTACK) }
}

/** Condition the attacked hero also receives ("kein" = none). */
@androidx.compose.runtime.Composable
private fun ConditionInput(value: io.github.aljadvb.worldbuilder.model.Condition?, onChange: (io.github.aljadvb.worldbuilder.model.Condition?) -> Unit) {
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    fun label(c: io.github.aljadvb.worldbuilder.model.Condition?) = c?.let(Texts::conditionName) ?: Texts.HERO_CONDITION_NONE
    androidx.compose.foundation.layout.Box {
        OutlinedButton(onClick = { open = true }) { Text(Texts.HERO_CONDITION + ": " + label(value)) }
        androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            (listOf<io.github.aljadvb.worldbuilder.model.Condition?>(null) + io.github.aljadvb.worldbuilder.model.Condition.entries).forEach { c ->
                androidx.compose.material3.DropdownMenuItem(text = { Text(label(c)) }, onClick = { open = false; onChange(c) })
            }
        }
    }
}

/** Attribute of the test that negates an attack ("–" = cannot be negated). */
@androidx.compose.runtime.Composable
private fun NegateInput(value: io.github.aljadvb.worldbuilder.model.Attribute?, onChange: (io.github.aljadvb.worldbuilder.model.Attribute?) -> Unit) {
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    fun label(a: io.github.aljadvb.worldbuilder.model.Attribute?) = a?.let { x -> Texts.ATTRIBUTES.first { it.first == x.name }.second } ?: Texts.NEGATE_NONE
    androidx.compose.foundation.layout.Box {
        OutlinedButton(onClick = { open = true }) { Text(Texts.NEGATE + ": " + label(value)) }
        androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            (listOf<io.github.aljadvb.worldbuilder.model.Attribute?>(null) + io.github.aljadvb.worldbuilder.model.Attribute.entries).forEach { a ->
                androidx.compose.material3.DropdownMenuItem(text = { Text(label(a)) }, onClick = { open = false; onChange(a) })
            }
        }
    }
}
