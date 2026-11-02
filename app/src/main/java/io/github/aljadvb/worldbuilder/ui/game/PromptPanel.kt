package io.github.aljadvb.worldbuilder.ui.game

import io.github.aljadvb.worldbuilder.hexmap.battleSetup
import io.github.aljadvb.worldbuilder.model.SquareLocation
import io.github.aljadvb.worldbuilder.model.placementId
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
            scenario.battleSetup(chapter.id, catalog)?.let { setup ->
                if (setup.boards.isNotEmpty()) {
                    Text(stringResource(R.string.prompt_setup_boards), fontWeight = FontWeight.Bold)
                    setup.boards.forEach { (label, side) -> Text("• " + stringResource(R.string.prompt_setup_board_line, label, side.name)) }
                }
                if (setup.pieces.isNotEmpty()) {
                    Text(stringResource(R.string.prompt_setup_terrain), fontWeight = FontWeight.Bold)
                    setup.pieces.forEach { (name, n) -> Text("• " + stringResource(R.string.prompt_setup_terrain_line, n, name)) }
                }
            }
            if (chapter.tiles.isNotEmpty()) {
                Text(stringResource(R.string.prompt_setup_tiles), fontWeight = FontWeight.Bold)
                chapter.tiles.forEach {
                    Text("• " + stringResource(if (it.unexplored) R.string.prompt_setup_tile_unexplored else R.string.prompt_setup_tile_line, tileLabel(it.id), it.side.name))
                }
            }
            val tokens = chapter.tokens.filter { it.initiallyActive }
            if (tokens.isNotEmpty()) {
                Text(stringResource(R.string.prompt_setup_tokens), fontWeight = FontWeight.Bold)
                tokens.forEach { t ->
                    Text("• " + if (t.location is SquareLocation) stringResource(R.string.prompt_setup_token_line_battle, t.label, tokenTypeLabel(t))
                        else stringResource(R.string.prompt_setup_token_line, t.label, tokenTypeLabel(t), tileLabel(t.location.placementId.orEmpty())))
                }
            }
            Hint(stringResource(if (chapter.isBattle) R.string.prompt_setup_hint_battle else R.string.prompt_setup_hint))
        }
        is Prompt.SpawnEnemies -> {
            val group = scenario.enemyGroup(prompt.groupId) ?: return
            Title(stringResource(R.string.prompt_spawn_title))
            val name = catalog.enemyType(group.enemyType)?.name ?: group.enemyType
            Body(
                if (group.location is SquareLocation) stringResource(R.string.prompt_spawn_text_battle, prompt.count, name)
                else stringResource(R.string.prompt_spawn_text, prompt.count, name, tileLabel(group.location.placementId.orEmpty())),
            )
        }
        is Prompt.PlaceToken -> {
            val token = scenario.token(prompt.tokenId) ?: return
            Title(stringResource(R.string.prompt_place_token_title))
            Body(
                if (token.location is SquareLocation) stringResource(R.string.prompt_place_token_text_battle, token.label, tokenTypeLabel(token))
                else stringResource(R.string.prompt_place_token_text, token.label, tokenTypeLabel(token), tileLabel(token.location.placementId.orEmpty())),
            )
        }
        is Prompt.RemoveToken -> {
            val token = scenario.token(prompt.tokenId) ?: return
            Title(stringResource(R.string.prompt_remove_token_title))
            Body(
                if (token.location is SquareLocation) stringResource(R.string.prompt_remove_token_text_battle, token.label)
                else stringResource(R.string.prompt_remove_token_text, token.label, tileLabel(token.location.placementId.orEmpty())),
            )
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
        is Prompt.Explored -> {
            Title(stringResource(R.string.prompt_explored_title, tileLabel(prompt.placementId)))
            Body(stringResource(R.string.prompt_explored_inspiration))
            val tokens = prompt.tokenIds.mapNotNull { scenario.token(it) }
            if (tokens.isNotEmpty()) {
                Text(stringResource(R.string.prompt_setup_tokens), fontWeight = FontWeight.Bold)
                tokens.forEach { t -> Text("• " + stringResource(R.string.prompt_setup_token_line, t.label, tokenTypeLabel(t), tileLabel(prompt.placementId))) }
            }
            Hint(stringResource(R.string.prompt_explored_hint))
        }
        is Prompt.Inspiration -> {
            Title(stringResource(R.string.prompt_inspiration_title))
            prompt.text?.let { Body(it) }
            Body(inspirationText(prompt))
            Hint(stringResource(R.string.prompt_inspiration_hint))
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


@Composable
internal fun inspirationText(prompt: Prompt.Inspiration): String = when (prompt.who) {
    io.github.aljadvb.worldbuilder.model.InspirationTarget.ACTIVE -> stringResource(R.string.inspiration_active, prompt.amount)
    io.github.aljadvb.worldbuilder.model.InspirationTarget.EACH -> stringResource(R.string.inspiration_each, prompt.amount)
    io.github.aljadvb.worldbuilder.model.InspirationTarget.CHOSEN -> stringResource(R.string.inspiration_chosen, prompt.amount)
}
