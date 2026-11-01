package io.github.aljadvb.worldbuilder.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.aljadvb.worldbuilder.R
import io.github.aljadvb.worldbuilder.engine.Command
import io.github.aljadvb.worldbuilder.engine.Prompt
import io.github.aljadvb.worldbuilder.model.MaterialCatalog
import io.github.aljadvb.worldbuilder.model.Scenario
import io.github.aljadvb.worldbuilder.ui.common.attributeLabel
import io.github.aljadvb.worldbuilder.ui.common.tokenTypeLabel

/** Docked panel showing the current prompt; the map stays visible above it. */
@Composable
fun PromptPanel(
    prompt: Prompt,
    scenario: Scenario,
    catalog: MaterialCatalog,
    heroCount: Int,
    onCommand: (Command) -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier.fillMaxWidth()) {
        Column(
            Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (prompt) {
                is Prompt.Notice -> {
                    NoticeContent(prompt, scenario, catalog)
                    Button(onClick = { onCommand(Command.Acknowledge) }, modifier = Modifier.align(Alignment.End)) {
                        Text(stringResource(R.string.continue_))
                    }
                }
                is Prompt.Choice -> {
                    Body(prompt.step.text)
                    prompt.step.options.forEachIndexed { i, option ->
                        FilledTonalButton(onClick = { onCommand(Command.Choose(i)) }, modifier = Modifier.fillMaxWidth()) {
                            Text(option.label)
                        }
                    }
                }
                is Prompt.Test -> TestContent(prompt, onCommand)
            }
        }
    }
}

@Composable
private fun Title(text: String) = Text(text, style = MaterialTheme.typography.titleLarge)

@Composable
private fun Body(text: String) = Text(text, style = MaterialTheme.typography.bodyLarge)

@Composable
private fun Hint(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun NoticeContent(prompt: Prompt.Notice, scenario: Scenario, catalog: MaterialCatalog) {
    fun tileLabel(placementId: String): String {
        val p = scenario.placement(placementId) ?: return placementId
        return catalog.tile(p.tile)?.label ?: p.tile
    }
    when (prompt) {
        Prompt.Intro -> {
            Title(scenario.title)
            Body(scenario.intro)
        }
        is Prompt.Text -> Body(prompt.text)
        is Prompt.Inspect -> {
            val token = scenario.token(prompt.tokenId)
            Title(token?.label.orEmpty())
            Body(token?.inspectText.orEmpty())
        }
        is Prompt.Setup -> {
            val chapter = scenario.chapter(prompt.chapterId) ?: return
            Title(stringResource(R.string.prompt_setup_title, chapter.title))
            if (chapter.setupText.isNotBlank()) Body(chapter.setupText)
            if (chapter.tiles.isNotEmpty()) {
                Text(stringResource(R.string.prompt_setup_tiles), fontWeight = FontWeight.Bold)
                chapter.tiles.forEach { Text("• " + stringResource(R.string.prompt_setup_tile_line, tileLabel(it.id), it.side.name)) }
            }
            val tokens = chapter.tokens.filter { it.initiallyActive }
            if (tokens.isNotEmpty()) {
                Text(stringResource(R.string.prompt_setup_tokens), fontWeight = FontWeight.Bold)
                tokens.forEach { t ->
                    Text("• " + stringResource(R.string.prompt_setup_token_line, t.label, tokenTypeLabel(t), tileLabel(t.location.placement)))
                }
            }
            Hint(stringResource(R.string.prompt_setup_hint))
        }
        is Prompt.SpawnEnemies -> {
            val group = scenario.enemyGroup(prompt.groupId) ?: return
            Title(stringResource(R.string.prompt_spawn_title))
            val name = catalog.enemyType(group.enemyType)?.name ?: group.enemyType
            Body(stringResource(R.string.prompt_spawn_text, prompt.count, name, tileLabel(group.location.placement)))
        }
        is Prompt.PlaceToken -> {
            val token = scenario.token(prompt.tokenId) ?: return
            Title(stringResource(R.string.prompt_place_token_title))
            Body(stringResource(R.string.prompt_place_token_text, token.label, tokenTypeLabel(token), tileLabel(token.location.placement)))
        }
        is Prompt.RemoveToken -> {
            val token = scenario.token(prompt.tokenId) ?: return
            Title(stringResource(R.string.prompt_remove_token_title))
            Body(stringResource(R.string.prompt_remove_token_text, token.label, tileLabel(token.location.placement)))
        }
        is Prompt.ThreatChanged -> {
            Title(stringResource(R.string.prompt_threat_title))
            Body(
                if (prompt.delta > 0) stringResource(R.string.prompt_threat_up, prompt.delta, prompt.threat)
                else stringResource(R.string.prompt_threat_down, -prompt.delta, prompt.threat),
            )
        }
        is Prompt.RoundEnded -> {
            Title(stringResource(R.string.prompt_round_title, prompt.round))
            Body(stringResource(R.string.prompt_round_text, prompt.threatAdded, prompt.threat))
        }
        is Prompt.ObjectiveSet -> {
            val objective = scenario.objective(prompt.objectiveId) ?: return
            Hint(stringResource(R.string.prompt_objective_set_title))
            Title(objective.title)
            Body(objective.reminder)
        }
        is Prompt.ObjectiveCompleted -> {
            val objective = scenario.objective(prompt.objectiveId) ?: return
            Hint(stringResource(R.string.prompt_objective_done_title))
            Title(objective.title)
        }
    }
}

@Composable
private fun TestContent(prompt: Prompt.Test, onCommand: (Command) -> Unit) {
    var successes by rememberSaveable(prompt) { mutableIntStateOf(0) }
    Title(stringResource(R.string.prompt_test_title, attributeLabel(prompt.step.attribute)))
    prompt.step.text?.let { Body(it) }
    Text(stringResource(R.string.prompt_test_required, prompt.step.successes), fontWeight = FontWeight.Bold)
    Text(stringResource(R.string.prompt_test_successes))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedButton(onClick = { if (successes > 0) successes-- }) { Text("−") }
        Text("$successes", style = MaterialTheme.typography.headlineMedium)
        OutlinedButton(onClick = { if (successes < 30) successes++ }) { Text("+") }
    }
    Button(onClick = { onCommand(Command.ReportTest(successes)) }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.confirm))
    }
}

/** What to emphasise on the map for a prompt. */
fun highlightFor(prompt: Prompt?, scenario: Scenario): MapHighlight = when (prompt) {
    is Prompt.Setup -> scenario.chapter(prompt.chapterId)?.let { ch ->
        MapHighlight(
            placements = ch.tiles.map { it.id }.toSet(),
            cells = ch.tokens.filter { it.initiallyActive }.mapNotNull { scenario.mapCell(it.location) }.toSet(),
        )
    } ?: MapHighlight()
    is Prompt.PlaceToken -> tokenHighlight(scenario, prompt.tokenId)
    is Prompt.RemoveToken -> tokenHighlight(scenario, prompt.tokenId)
    is Prompt.Inspect -> tokenHighlight(scenario, prompt.tokenId)
    is Prompt.SpawnEnemies -> scenario.enemyGroup(prompt.groupId)?.let { g ->
        MapHighlight(cells = setOfNotNull(scenario.mapCell(g.location)))
    } ?: MapHighlight()
    else -> MapHighlight()
}

private fun tokenHighlight(scenario: Scenario, tokenId: String) =
    MapHighlight(cells = setOfNotNull(scenario.token(tokenId)?.let { scenario.mapCell(it.location) }))
