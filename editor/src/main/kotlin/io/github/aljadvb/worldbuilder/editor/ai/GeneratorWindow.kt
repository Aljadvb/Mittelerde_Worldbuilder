package io.github.aljadvb.worldbuilder.editor.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import io.github.aljadvb.worldbuilder.editor.EditorModel
import io.github.aljadvb.worldbuilder.editor.Texts
import io.github.aljadvb.worldbuilder.editor.ui.CheckInput
import io.github.aljadvb.worldbuilder.generator.DraftChapter
import io.github.aljadvb.worldbuilder.generator.DraftEnding
import io.github.aljadvb.worldbuilder.generator.DraftEnemy
import io.github.aljadvb.worldbuilder.generator.DraftObjective
import io.github.aljadvb.worldbuilder.generator.GenerationInput
import io.github.aljadvb.worldbuilder.generator.ModelInfo
import io.github.aljadvb.worldbuilder.generator.StoryDraft
import io.github.aljadvb.worldbuilder.model.MaterialCatalog
import io.github.aljadvb.worldbuilder.model.Terrain
import io.github.aljadvb.worldbuilder.validation.Severity

/** Window content of the generator wizard. */
@Composable
fun GeneratorScreen(model: GeneratorModel, catalog: MaterialCatalog, editor: EditorModel, hasOpenCampaign: Boolean, onLoadFile: () -> String?, onDone: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(GeneratorModel.Step.INPUT to Texts.AI_STEP_INPUT, GeneratorModel.Step.STORY to Texts.AI_STEP_STORY, GeneratorModel.Step.SCENARIO to Texts.AI_STEP_SCENARIO)
                    .forEach { (s, label) -> FilterChip(selected = model.step == s, onClick = {}, enabled = false, label = { Text(label) }) }
                Spacer(Modifier.weight(1f))
                if (model.totalCost > 0) Text(Texts.aiCost(model.totalCost), style = MaterialTheme.typography.labelLarge)
            }
            HorizontalDivider()
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (model.step) {
                    GeneratorModel.Step.INPUT -> InputStep(model, catalog, hasOpenCampaign, onLoadFile)
                    GeneratorModel.Step.STORY -> model.draft?.let { DraftEditor(it, catalog) { d -> model.draft = d } }
                    GeneratorModel.Step.SCENARIO -> ResultStep(model)
                }
            }
            HorizontalDivider()
            val manual = model.manualRequest
            if (model.busy && manual != null) {
                ManualPanel(model, manual)
            } else if (model.busy) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.width(24.dp))
                    Text(model.status, Modifier.weight(1f))
                    OutlinedButton(onClick = model::cancelRun) { Text(Texts.AI_CANCEL) }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (model.step != GeneratorModel.Step.INPUT) OutlinedButton(onClick = model::back) { Text(Texts.AI_BACK) }
                    Spacer(Modifier.weight(1f))
                    when (model.step) {
                        GeneratorModel.Step.INPUT -> Button(onClick = model::createStory, enabled = model.canCreateStory) { Text(Texts.AI_CREATE_STORY) }
                        GeneratorModel.Step.STORY -> {
                            OutlinedButton(onClick = model::createStory) { Text(Texts.AI_REGENERATE) }
                            Button(onClick = model::createScenario) { Text(Texts.AI_CREATE_SCENARIO) }
                        }
                        GeneratorModel.Step.SCENARIO -> Button(
                            onClick = { if (model.accept(editor)) onDone() },
                            enabled = model.result != null,
                        ) { Text(Texts.AI_ACCEPT) }
                    }
                }
            }
        }
    }
    model.error?.let { msg ->
        AlertDialog(
            onDismissRequest = { model.error = null },
            confirmButton = { Button(onClick = { model.error = null }) { Text(Texts.OK) } },
            text = { Text(msg) },
        )
    }
}

/** Copy & paste exchange with a chat (claude.ai). */
@Composable
private fun ManualPanel(model: GeneratorModel, request: io.github.aljadvb.worldbuilder.generator.ManualRequest) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var copied by remember(request) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (request.sameChat) Texts.AI_MANUAL_STEP_SAME else Texts.AI_MANUAL_STEP_NEW, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(request.prompt)); copied = true }) {
                    Text(if (copied) Texts.AI_MANUAL_COPIED else Texts.AI_MANUAL_COPY)
                }
                Text(Texts.aiManualSize(request.prompt.length), style = MaterialTheme.typography.bodySmall)
            }
            OutlinedTextField(
                model.manualAnswer, { model.manualAnswer = it },
                Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 240.dp),
                label = { Text(Texts.AI_MANUAL_ANSWER) }, minLines = 5,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = model::submitManualAnswer, enabled = model.manualAnswer.isNotBlank()) { Text(Texts.AI_MANUAL_SUBMIT) }
                OutlinedButton(onClick = model::cancelRun) { Text(Texts.AI_CANCEL) }
            }
            Text(Texts.AI_MANUAL_HINT, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Heading(text: String) = Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

@Composable
private fun InputStep(model: GeneratorModel, catalog: MaterialCatalog, hasOpenCampaign: Boolean, onLoadFile: () -> String?) {
    Heading(Texts.AI_SOURCE)
    OutlinedTextField(model.source, { model.source = it }, Modifier.fillMaxWidth().heightIn(min = 180.dp), minLines = 8)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { onLoadFile()?.let { model.source = it } }) { Text(Texts.AI_LOAD_FILE) }
        Text(Texts.AI_SOURCE_HINT, style = MaterialTheme.typography.bodySmall)
    }
    OutlinedTextField(model.wishes, { model.wishes = it }, Modifier.fillMaxWidth(), label = { Text(Texts.AI_WISHES) })
    Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Heading(Texts.AI_LENGTH)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GenerationInput.Length.entries.forEach { l ->
                    FilterChip(model.length == l, { model.length = l }, label = { Text("${l.label} (${l.chapters} Kapitel)") })
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Heading(Texts.AI_DIFFICULTY)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GenerationInput.Difficulty.entries.forEach { d -> FilterChip(model.difficulty == d, { model.difficulty = d }, label = { Text(d.label) }) }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Heading(Texts.AI_MAP_KIND)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GenerationInput.MapChoice.entries.forEach { c -> FilterChip(model.map == c, { model.map = c }, label = { Text(c.label) }) }
            }
            if (model.map == GenerationInput.MapChoice.AUTO) Text(Texts.AI_MAP_AUTO_HINT, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(360.dp))
        }
    }
    Heading(Texts.AI_MATERIAL)
    Text(Texts.AI_TILES, style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        catalog.tiles.sortedBy { it.label }.forEach { t ->
            val terrain = t.sides.values.flatMap { it.terrain }.distinct().take(3).joinToString(", ") { Terrain.label(it) }
            FilterChip(
                selected = t.id in model.allowedTiles,
                onClick = { model.allowedTiles = if (t.id in model.allowedTiles) model.allowedTiles - t.id else model.allowedTiles + t.id },
                label = { Text(t.label + if (terrain.isNotEmpty()) " · $terrain" else "") },
            )
        }
    }
    if (catalog.enemyTypes.isNotEmpty()) {
        Text(Texts.AI_ENEMIES, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            catalog.enemyTypes.forEach { e ->
                FilterChip(
                    selected = e.id in model.allowedEnemies,
                    onClick = { model.allowedEnemies = if (e.id in model.allowedEnemies) model.allowedEnemies - e.id else model.allowedEnemies + e.id },
                    label = { Text(e.name) },
                )
            }
        }
    }
    Heading(Texts.AI_MODE)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(model.mode == GeneratorModel.Mode.CLAUDE_AI, { model.mode = GeneratorModel.Mode.CLAUDE_AI }, label = { Text(Texts.AI_MODE_MANUAL) })
        FilterChip(model.mode == GeneratorModel.Mode.API, { model.mode = GeneratorModel.Mode.API }, label = { Text(Texts.AI_MODE_API) })
    }
    Heading(Texts.AI_TARGET)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(model.target == GeneratorModel.Target.NEW_CAMPAIGN, { model.target = GeneratorModel.Target.NEW_CAMPAIGN }, label = { Text(Texts.AI_TARGET_NEW) })
        FilterChip(model.target == GeneratorModel.Target.CURRENT_CAMPAIGN, { model.target = GeneratorModel.Target.CURRENT_CAMPAIGN }, enabled = hasOpenCampaign, label = { Text(Texts.AI_TARGET_CURRENT) })
    }
}

/** Editable story outline. */
@Composable
fun DraftEditor(draft: StoryDraft, catalog: MaterialCatalog, onChange: (StoryDraft) -> Unit) {
    OutlinedTextField(draft.title, { onChange(draft.copy(title = it)) }, Modifier.fillMaxWidth(), label = { Text(Texts.AI_DRAFT_TITLE) })
    OutlinedTextField(draft.intro, { onChange(draft.copy(intro = it)) }, Modifier.fillMaxWidth(), label = { Text(Texts.AI_DRAFT_INTRO) }, minLines = 3)
    if (draft.mapReason.isNotBlank()) Text("${Texts.AI_MAP_REASON}: ${draft.mapReason}", style = MaterialTheme.typography.bodyMedium)

    Heading(Texts.AI_DRAFT_CHAPTERS)
    draft.chapters.forEachIndexed { i, ch ->
        fun set(c: DraftChapter) = onChange(draft.copy(chapters = draft.chapters.mapIndexed { j, x -> if (j == i) c else x }))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(ch.title, { set(ch.copy(title = it)) }, Modifier.fillMaxWidth(), label = { Text("${i + 1}. ${Texts.AI_DRAFT_TITLE}") })
                OutlinedTextField(ch.summary, { set(ch.copy(summary = it)) }, Modifier.fillMaxWidth(), label = { Text(Texts.AI_DRAFT_SUMMARY) }, minLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(!ch.battle, { set(ch.copy(battle = false)) }, label = { Text(Texts.MAP_KIND_TRAVEL) })
                    FilterChip(ch.battle, { set(ch.copy(battle = true)) }, label = { Text(Texts.MAP_KIND_BATTLE) })
                }
                OutlinedTextField(ch.locations.joinToString(", "), { v -> set(ch.copy(locations = v.split(",").map { it.trim() }.filter { it.isNotEmpty() })) }, Modifier.fillMaxWidth(), label = { Text(Texts.AI_DRAFT_LOCATIONS) })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Terrain.TAGS.forEach { tag ->
                        FilterChip(tag.id in ch.terrain, { set(ch.copy(terrain = if (tag.id in ch.terrain) ch.terrain - tag.id else ch.terrain + tag.id)) }, label = { Text(tag.label) })
                    }
                }
            }
        }
    }

    Heading(Texts.AI_DRAFT_OBJECTIVES)
    draft.objectives.forEachIndexed { i, o ->
        fun set(x: DraftObjective?) = onChange(draft.copy(objectives = draft.objectives.mapIndexedNotNull { j, y -> if (j == i) x else y }))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(o.title, { set(o.copy(title = it)) }, Modifier.weight(1f), label = { Text(Texts.AI_DRAFT_TITLE) })
            OutlinedTextField(o.howToComplete, { set(o.copy(howToComplete = it)) }, Modifier.weight(2f), label = { Text(Texts.AI_DRAFT_HOW) })
            IconButton(onClick = { set(null) }) { Icon(Icons.Default.Close, Texts.DELETE) }
        }
    }

    if (draft.enemies.isNotEmpty()) Heading(Texts.AI_DRAFT_ENEMIES)
    draft.enemies.forEachIndexed { i, e ->
        fun set(x: DraftEnemy?) = onChange(draft.copy(enemies = draft.enemies.mapIndexedNotNull { j, y -> if (j == i) x else y }))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(catalog.enemyType(e.enemyType)?.name ?: e.enemyType, Modifier.width(160.dp))
            OutlinedTextField(e.where, { set(e.copy(where = it)) }, Modifier.weight(1f), label = { Text(Texts.AI_DRAFT_WHERE) })
            IconButton(onClick = { set(null) }) { Icon(Icons.Default.Close, Texts.DELETE) }
        }
    }

    Heading(Texts.AI_DRAFT_ENDINGS)
    draft.endings.forEachIndexed { i, e ->
        fun set(x: DraftEnding) = onChange(draft.copy(endings = draft.endings.mapIndexed { j, y -> if (j == i) x else y }))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (e.outcome == "victory") Texts.OUTCOME_VICTORY else if (e.outcome == "defeat") Texts.OUTCOME_DEFEAT else Texts.OUTCOME_NEUTRAL, Modifier.width(90.dp))
            OutlinedTextField(e.title, { set(e.copy(title = it)) }, Modifier.weight(1f), label = { Text(Texts.AI_DRAFT_TITLE) })
            OutlinedTextField(e.condition, { set(e.copy(condition = it)) }, Modifier.weight(2f), label = { Text(Texts.AI_DRAFT_CONDITION) })
        }
    }
}

@Composable
private fun ResultStep(model: GeneratorModel) {
    val r = model.result ?: return
    Text(r.scenario.title, style = MaterialTheme.typography.headlineSmall)
    Text(r.scenario.intro)
    Text(
        Texts.aiResult(r.rounds, r.report.errors.size, r.report.warnings.size),
        color = if (r.report.isValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        fontWeight = FontWeight.Bold,
    )
    r.scenario.chapters.forEach { ch ->
        Text("• ${ch.title}: ${ch.tiles.size} Kacheln, ${ch.tokens.size} Token", style = MaterialTheme.typography.bodyMedium)
    }
    Text("${r.scenario.events.size} Events · ${r.scenario.triggers.size} Trigger · ${r.scenario.objectives.size} Ziele · ${r.scenario.resolutions.size} Enden", style = MaterialTheme.typography.bodySmall)
    r.report.issues.filter { it.severity == Severity.ERROR }.take(10).forEach {
        Text("– ${it.message}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
    if (r.copyFindings.isNotEmpty()) {
        Text(Texts.AI_COPY_FINDINGS, fontWeight = FontWeight.Bold)
        r.copyFindings.forEach { Text(Texts.aiCopyFinding(it.objectId, it.field, it.words, it.excerpt), style = MaterialTheme.typography.bodySmall) }
    }
}

/** Model, options and API key. */
@Composable
fun AiSettingsDialog(settings: AiSettings, keys: ApiKeyStore, onDismiss: () -> Unit) {
    var model by remember { mutableStateOf(settings.model) }
    var fallbacks by remember { mutableStateOf(settings.fallbacks) }
    var key by remember { mutableStateOf("") }
    var hasKey by remember { mutableStateOf(keys.load() != null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Texts.AI_SETTINGS_TITLE) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.width(520.dp)) {
                Text(Texts.AI_MODEL, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ModelInfo.ALL.forEach { m ->
                        FilterChip(model == m.id, { model = m.id }, label = { Text("${m.label} (${m.inputPerM.toInt()} \$ / ${m.outputPerM.toInt()} \$ je Mio. Tokens)") })
                    }
                }
                CheckInput(Texts.AI_FALLBACKS, fallbacks) { fallbacks = it }
                Text(Texts.AI_COST_HINT, style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                Text(Texts.AI_KEY, fontWeight = FontWeight.Bold)
                Text(if (hasKey) Texts.AI_KEY_SET else Texts.AI_KEY_NONE, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(key, { key = it.trim() }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("sk-ant-…") },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { keys.save(key); key = ""; hasKey = true }, enabled = key.length > 10) { Text(Texts.AI_KEY_SAVE) }
                    OutlinedButton(onClick = { keys.clear(); hasKey = false }, enabled = hasKey) { Text(Texts.AI_KEY_CLEAR) }
                }
                Text(Texts.aiKeyStorage(keys.description), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button(onClick = { settings.model = model; settings.fallbacks = fallbacks; onDismiss() }) { Text(Texts.OK) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Texts.CANCEL) } },
    )
}
