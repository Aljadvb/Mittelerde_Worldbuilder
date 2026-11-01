package io.github.aljadvb.worldbuilder.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aljadvb.worldbuilder.R
import io.github.aljadvb.worldbuilder.engine.Command
import io.github.aljadvb.worldbuilder.engine.GameState
import io.github.aljadvb.worldbuilder.engine.HistoryEntry
import io.github.aljadvb.worldbuilder.engine.Prompt
import io.github.aljadvb.worldbuilder.model.Campaign
import io.github.aljadvb.worldbuilder.model.MaterialCatalog
import io.github.aljadvb.worldbuilder.model.Scenario
import io.github.aljadvb.worldbuilder.model.TriggerCondition
import io.github.aljadvb.worldbuilder.ui.common.outcomeLabel
import io.github.aljadvb.worldbuilder.ui.common.tokenTypeLabel
import io.github.aljadvb.worldbuilder.ui.home.ConfirmDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(viewModel: GameViewModel, onBack: () -> Unit) {
    val load by viewModel.state.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }
    val ui = (load as? GameLoad.Ready)?.ui
    val game = ui?.game
    val scenario = ui?.scenario

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                title = {
                    Column {
                        Text(scenario?.title ?: ui?.campaign?.title.orEmpty(), maxLines = 1)
                        if (game != null && !game.isOver) {
                            Text(stringResource(R.string.game_round, game.round), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                },
                actions = {
                    if (game != null) {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more)) }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.game_log)) }, onClick = { menuOpen = false; showLog = true })
                            DropdownMenuItem(text = { Text(stringResource(R.string.game_to_menu)) }, onClick = { menuOpen = false; onBack() })
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val l = load) {
                GameLoad.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                GameLoad.SaveMissing -> Message(stringResource(R.string.game_load_failed))
                is GameLoad.CampaignMissing -> Message(stringResource(R.string.game_campaign_missing, l.title))
                is GameLoad.Ready -> when {
                    game == null -> CampaignOver(l.ui.campaign, l.ui.save.progress.completed.map { it.scenarioId to it.resolutionId }, onBack)
                    scenario == null -> Message(stringResource(R.string.game_load_failed))
                    game.isOver -> ResolutionContent(scenario, game, onContinue = viewModel::continueCampaign)
                    else -> PlayContent(scenario, l.ui.catalog, game, l.ui.campaignChanged, viewModel::send)
                }
            }
        }
    }

    if (showLog && game != null && scenario != null) {
        HistoryDialog(game.history, scenario, onDismiss = { showLog = false })
    }
    ui?.error?.let { error ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            confirmButton = { TextButton(onClick = viewModel::dismissError) { Text(stringResource(R.string.ok)) } },
            text = { Text(error) },
        )
    }
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Text(text) }
}

@Composable
internal fun PlayContent(
    scenario: Scenario,
    catalog: MaterialCatalog,
    game: GameState,
    campaignChanged: Boolean,
    onCommand: (Command) -> Unit,
) {
    var confirmEndRound by remember { mutableStateOf(false) }
    var confirmDefeat by remember { mutableStateOf<String?>(null) }
    val prompt = game.currentPrompt
    val tokenLetters = scenario.tokens.associate { it.id to tokenTypeLabel(it).take(1).uppercase() }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (campaignChanged) {
            Text(stringResource(R.string.game_campaign_changed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        ThreatBar(scenario, game.threat)
        Objectives(scenario, game)
        HexMap(
            scenario = scenario,
            catalog = catalog,
            state = game,
            highlight = highlightFor(prompt, scenario),
            tokenLetters = tokenLetters,
            tapEnabled = game.isIdle,
            onTokenTap = { onCommand(Command.InteractToken(it)) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        if (prompt != null) {
            PromptPanel(prompt, scenario, catalog, game.heroCount, onCommand, Modifier.padding(bottom = 12.dp))
        } else {
            ActionPanel(
                scenario = scenario,
                catalog = catalog,
                game = game,
                onToken = { onCommand(Command.InteractToken(it)) },
                onDefeat = { confirmDefeat = it },
                onEndRound = { confirmEndRound = true },
            )
        }
    }

    if (confirmEndRound) {
        ConfirmDialog(
            title = stringResource(R.string.game_end_round_confirm_title, game.round),
            text = stringResource(R.string.game_end_round_confirm_text, scenario.threat.perRound.amountFor(game.heroCount)),
            onConfirm = { confirmEndRound = false; onCommand(Command.EndRound) },
            onDismiss = { confirmEndRound = false },
        )
    }
    confirmDefeat?.let { groupId ->
        val group = scenario.enemyGroup(groupId)
        val name = group?.let { catalog.enemyType(it.enemyType)?.name ?: it.enemyType }.orEmpty()
        ConfirmDialog(
            title = stringResource(R.string.game_defeat_confirm_title, name),
            text = "",
            onConfirm = { confirmDefeat = null; onCommand(Command.DefeatEnemyGroup(groupId)) },
            onDismiss = { confirmDefeat = null },
        )
    }
}

@Composable
private fun ThreatBar(scenario: Scenario, threat: Int) {
    val max = scenario.threat.max
    val thresholds = scenario.triggers.mapNotNull { (it.on as? TriggerCondition.ThreatReached)?.value }.filter { it in 1 until max }
    val markerColor = MaterialTheme.colorScheme.onSurface
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.game_threat, threat, max), style = MaterialTheme.typography.labelLarge)
        Box {
            LinearProgressIndicator(
                progress = { threat.toFloat() / max },
                modifier = Modifier.fillMaxWidth().height(10.dp),
                color = MaterialTheme.colorScheme.error,
            )
            Canvas(Modifier.fillMaxWidth().height(10.dp)) {
                thresholds.forEach { v ->
                    val x = size.width * v / max
                    drawLine(markerColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
                }
            }
        }
    }
}

@Composable
private fun Objectives(scenario: Scenario, game: GameState) {
    val objectives = game.activeObjectives.mapNotNull { scenario.objective(it) }
    if (objectives.isEmpty()) return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.game_objectives), style = MaterialTheme.typography.labelMedium)
            objectives.forEach { o ->
                Text(o.title, fontWeight = FontWeight.Bold)
                Text(o.reminder, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ActionPanel(
    scenario: Scenario,
    catalog: MaterialCatalog,
    game: GameState,
    onToken: (String) -> Unit,
    onDefeat: (String) -> Unit,
    onEndRound: () -> Unit,
) {
    Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val groups = game.activeEnemyGroups.mapNotNull { scenario.enemyGroup(it) }
        if (groups.isNotEmpty()) {
            Text(stringResource(R.string.game_enemies), style = MaterialTheme.typography.labelMedium)
            groups.forEach { g ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val name = catalog.enemyType(g.enemyType)?.name ?: g.enemyType
                    Text(stringResource(R.string.game_enemy_line, g.count[game.heroCount] ?: 0, name), Modifier.weight(1f))
                    OutlinedButton(onClick = { onDefeat(g.id) }) { Text(stringResource(R.string.game_defeated)) }
                }
            }
        }
        val tokens = scenario.tokens.filter { it.id in game.activeTokens }
        Text(
            if (tokens.isEmpty()) stringResource(R.string.game_no_tokens) else stringResource(R.string.game_tap_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            tokens.forEach { t -> AssistChip(onClick = { onToken(t.id) }, label = { Text(t.label) }) }
        }
        Button(onClick = onEndRound, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.game_end_round)) }
    }
}

@Composable
internal fun ResolutionContent(scenario: Scenario, game: GameState, onContinue: () -> Unit) {
    val resolution = scenario.resolution(game.resolution.orEmpty())
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        resolution?.let {
            Text(outcomeLabel(it.outcome), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(it.title, style = MaterialTheme.typography.headlineMedium)
            Text(it.text, style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.continue_)) }
    }
}

@Composable
private fun CampaignOver(campaign: Campaign, played: List<Pair<String, String>>, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.campaign_over_title), style = MaterialTheme.typography.headlineMedium)
        Text(campaign.title, style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.campaign_over_played), fontWeight = FontWeight.Bold)
        played.forEach { (scenarioId, resolutionId) ->
            val s = campaign.scenario(scenarioId)
            Text("• " + stringResource(R.string.campaign_over_line, s?.title ?: scenarioId, s?.resolution(resolutionId)?.title ?: resolutionId))
        }
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.game_to_menu)) }
    }
}

@Composable
private fun HistoryDialog(history: List<HistoryEntry>, scenario: Scenario, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        title = { Text(stringResource(R.string.game_log)) },
        text = {
            if (history.isEmpty()) {
                Text(stringResource(R.string.game_log_empty))
            } else {
                LazyColumn(Modifier.heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(history.asReversed()) { entry ->
                        Column {
                            Text(stringResource(R.string.game_log_round, entry.round), style = MaterialTheme.typography.labelSmall)
                            Text(historyText(entry.prompt, scenario), style = MaterialTheme.typography.bodyMedium)
                            entry.answer?.let { Text(stringResource(R.string.game_log_answer, it), style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun historyText(prompt: Prompt, scenario: Scenario): String = when (prompt) {
    Prompt.Intro -> scenario.title
    is Prompt.Text -> prompt.text
    is Prompt.Inspect -> scenario.token(prompt.tokenId)?.let { "${it.label}: ${it.inspectText.orEmpty()}" }.orEmpty()
    is Prompt.Setup -> stringResource(R.string.prompt_setup_title, scenario.chapter(prompt.chapterId)?.title.orEmpty())
    is Prompt.SpawnEnemies -> stringResource(R.string.prompt_spawn_title)
    is Prompt.PlaceToken -> stringResource(R.string.prompt_place_token_title) + ": " + scenario.token(prompt.tokenId)?.label.orEmpty()
    is Prompt.RemoveToken -> stringResource(R.string.prompt_remove_token_title) + ": " + scenario.token(prompt.tokenId)?.label.orEmpty()
    is Prompt.ThreatChanged -> if (prompt.delta > 0) stringResource(R.string.prompt_threat_up, prompt.delta, prompt.threat)
        else stringResource(R.string.prompt_threat_down, -prompt.delta, prompt.threat)
    is Prompt.RoundEnded -> stringResource(R.string.prompt_round_title, prompt.round)
    is Prompt.ObjectiveSet -> stringResource(R.string.prompt_objective_set_title) + ": " + scenario.objective(prompt.objectiveId)?.title.orEmpty()
    is Prompt.ObjectiveCompleted -> stringResource(R.string.prompt_objective_done_title) + ": " + scenario.objective(prompt.objectiveId)?.title.orEmpty()
    is Prompt.Choice -> prompt.step.text
    is Prompt.Test -> prompt.step.text ?: ""
}
