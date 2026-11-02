package io.github.aljadvb.worldbuilder.editor.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import io.github.aljadvb.worldbuilder.editing.Definition
import io.github.aljadvb.worldbuilder.editing.EditException
import io.github.aljadvb.worldbuilder.editing.setBattleChapter
import io.github.aljadvb.worldbuilder.editing.updateBoard
import io.github.aljadvb.worldbuilder.editing.updateChapter
import io.github.aljadvb.worldbuilder.editing.updateTerrain
import io.github.aljadvb.worldbuilder.editor.EditorModel
import io.github.aljadvb.worldbuilder.editor.Texts
import io.github.aljadvb.worldbuilder.model.Chapter
import io.github.aljadvb.worldbuilder.model.CustomRule
import io.github.aljadvb.worldbuilder.model.RuleMode
import io.github.aljadvb.worldbuilder.model.Scenario

/** Travel / battle switch of a chapter. */
@Composable
internal fun MapKindInput(model: EditorModel, ch: Chapter) {
    SectionHeader(Texts.MAP_KIND)
    Row(verticalAlignment = Alignment.CenterVertically) {
        listOf(false to Texts.MAP_KIND_TRAVEL, true to Texts.MAP_KIND_BATTLE).forEach { (battle, label) ->
            RadioButton(selected = ch.isBattle == battle, onClick = { model.edit { it.setBattleChapter(ch.id, battle) } })
            Text(label)
        }
    }
    if (ch.isBattle) CheckInput(Texts.CLEAR_BATTLEFIELD, ch.clearBattlefield) { v -> model.edit { it.updateChapter(ch.id) { x -> x.copy(clearBattlefield = v) } } }
}

@Composable
internal fun BoardForm(model: EditorModel, s: Scenario, def: Definition) {
    val b = s.boardPlacement(def.id) ?: return
    val board = model.catalog.battleBoard(b.board)
    Text("${Texts.BOARD}: ${board?.label ?: b.board} · ${Texts.SIDE} ${b.side} · ${Texts.ROTATION} ${b.rotation * 90}° · ${Texts.POSITION} ${b.position}")
    board?.sides?.get(b.side)?.description?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    FieldIssues(model, def.path)
    Text(Texts.BATTLE_HINT_SELECT, style = MaterialTheme.typography.bodySmall)
}

@Composable
internal fun TerrainForm(model: EditorModel, s: Scenario, def: Definition) {
    val t = s.terrainPlacement(def.id) ?: return
    val side = model.catalog.terrainPiece(t.piece)?.sides?.get(t.side)
    val where = t.edge?.let { "${Texts.BATTLE_ON_BORDER} $it" } ?: t.area?.let { "${Texts.BATTLE_IN_FIELD} $it" } ?: "–"
    Text("${Texts.PIECE}: ${side?.name ?: t.piece} · ${Texts.SIDE} ${t.side} · $where")
    FieldIssues(model, def.path)
    TextInput(model, Texts.CUSTOM_NAME, t.customName.orEmpty(), "${t.id}.customName", "${def.path}.customName") { v ->
        { c -> c.updateTerrain(t.id) { it.copy(customName = v.ifEmpty { null }) } }
    }
    TextInput(model, Texts.CUSTOM_RULE, t.customRule?.text.orEmpty(), "${t.id}.customRule", "${def.path}.customRule.text", multiline = true) { v ->
        { c -> c.updateTerrain(t.id) { it.copy(customRule = if (v.isEmpty()) null else CustomRule(it.customRule?.mode ?: RuleMode.REPLACE, v)) } }
    }
    t.customRule?.let { rule ->
        Text(Texts.CUSTOM_RULE_MODE, style = MaterialTheme.typography.labelMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            listOf(RuleMode.REPLACE to Texts.CUSTOM_RULE_REPLACE, RuleMode.ADD to Texts.CUSTOM_RULE_ADD).forEach { (mode, label) ->
                RadioButton(selected = rule.mode == mode, onClick = { model.edit { c -> c.updateTerrain(t.id) { it.copy(customRule = rule.copy(mode = mode)) } } })
                Text(label)
            }
        }
    }
    val card = side?.cardText?.ifBlank { side.effect }.orEmpty()
    if (card.isNotBlank()) {
        SectionHeader(Texts.CARD_TEXT_PREVIEW)
        Text(card, style = MaterialTheme.typography.bodySmall)
    }
}
