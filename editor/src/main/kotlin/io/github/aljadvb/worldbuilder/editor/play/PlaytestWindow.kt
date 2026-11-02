package io.github.aljadvb.worldbuilder.editor.play

import io.github.aljadvb.worldbuilder.model.placementId
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.aljadvb.worldbuilder.editor.Texts
import io.github.aljadvb.worldbuilder.engine.Command
import io.github.aljadvb.worldbuilder.engine.GameState
import io.github.aljadvb.worldbuilder.engine.Prompt
import io.github.aljadvb.worldbuilder.hexmap.BattleCanvas
import io.github.aljadvb.worldbuilder.hexmap.BattleProjection
import io.github.aljadvb.worldbuilder.hexmap.TerrainInfo
import io.github.aljadvb.worldbuilder.hexmap.battlePlayScene
import io.github.aljadvb.worldbuilder.hexmap.battleSetup
import io.github.aljadvb.worldbuilder.hexmap.pieceAt
import io.github.aljadvb.worldbuilder.hexmap.showsBattle
import io.github.aljadvb.worldbuilder.hexmap.squareTokenAt
import io.github.aljadvb.worldbuilder.hexmap.terrainInfo
import io.github.aljadvb.worldbuilder.model.RuleMode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import io.github.aljadvb.worldbuilder.hexmap.HexCanvas
import io.github.aljadvb.worldbuilder.hexmap.HexProjection
import io.github.aljadvb.worldbuilder.hexmap.TileImageCache
import io.github.aljadvb.worldbuilder.hexmap.highlightFor
import io.github.aljadvb.worldbuilder.hexmap.playScene
import io.github.aljadvb.worldbuilder.hexmap.tokenAt
import io.github.aljadvb.worldbuilder.model.Campaign
import io.github.aljadvb.worldbuilder.model.MaterialCatalog
import io.github.aljadvb.worldbuilder.model.Scenario
import io.github.aljadvb.worldbuilder.model.TokenType

const val PLAY_MAP_TAG = "playMap"

/** Choose scenario and hero count, then play. */
@Composable
fun PlaytestScreen(campaign: Campaign, catalog: MaterialCatalog, images: TileImageCache? = null) {
    var game by remember(campaign) { mutableStateOf<PlaytestModel?>(null) }
    val current = game
    if (current == null) {
        PlaytestSetup(campaign) { scenarioId, heroes -> game = PlaytestModel(campaign, scenarioId, heroes) }
    } else {
        PlaytestView(current, catalog, images, onRestart = { game = null })
    }
}

@Composable
private fun PlaytestSetup(campaign: Campaign, onStart: (String, Int) -> Unit) {
    var scenarioId by remember { mutableStateOf(campaign.startScenario.takeIf { campaign.scenario(it) != null } ?: campaign.scenarios.first().id) }
    var heroes by remember { mutableIntStateOf(2) }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(campaign.title, style = MaterialTheme.typography.headlineSmall)
            Text(Texts.PLAYTEST_SCENARIO, fontWeight = FontWeight.Bold)
            campaign.scenarios.forEach { s ->
                FilterChip(selected = s.id == scenarioId, onClick = { scenarioId = s.id }, label = { Text(s.title.ifBlank { s.id }) })
            }
            Text(Texts.PLAYTEST_HEROES, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..5).forEach { n -> FilterChip(selected = heroes == n, onClick = { heroes = n }, label = { Text("$n") }) }
            }
            Button(onClick = { onStart(scenarioId, heroes) }) { Text(Texts.PLAYTEST_START) }
        }
    }
}

private val LETTERS = mapOf(TokenType.SEARCH to "S", TokenType.PERSON to "P", TokenType.THREAT to "B", TokenType.EXPLORE to "E", TokenType.CUSTOM to "?")

@Composable
fun PlaytestView(game: PlaytestModel, catalog: MaterialCatalog, images: TileImageCache? = null, onRestart: () -> Unit) {
    val state = game.state
    val scenario = game.scenario
    Surface(Modifier.fillMaxSize()) {
        if (state == null || scenario == null) {
            Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(Texts.PLAYTEST_CAMPAIGN_OVER, style = MaterialTheme.typography.headlineSmall)
                Controls(game, onRestart)
            }
            return@Surface
        }
        Row {
            PlayMap(game, scenario, catalog, state, images, Modifier.weight(1f).fillMaxHeight())
            VerticalDivider()
            Column(
                Modifier.width(440.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(scenario.title, style = MaterialTheme.typography.titleLarge)
                Text("${Texts.round(state.round)} · ${Texts.threat(state.threat, scenario.threat.max)}")
                LinearProgressIndicator(progress = { state.threat.toFloat() / scenario.threat.max }, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.error)
                state.activeObjectives.mapNotNull { scenario.objective(it) }.forEach { o ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp)) {
                            Text(o.title, fontWeight = FontWeight.Bold)
                            Text(o.reminder, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                val prompt = state.currentPrompt
                when {
                    state.isOver -> ResolutionCard(game, scenario, state)
                    prompt != null -> PromptCard(game, scenario, catalog, prompt)
                    else -> IdleActions(game, scenario, catalog, state)
                }
                game.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Controls(game, onRestart)
                HorizontalDivider()
                Text(Texts.PLAYTEST_LOG, fontWeight = FontWeight.Bold)
                state.history.asReversed().take(30).forEach { entry ->
                    Text(
                        "${Texts.round(entry.round)}: ${describe(entry.prompt, scenario, catalog)}" + (entry.answer?.let { " → $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun Controls(game: PlaytestModel, onRestart: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = game::stepBack, enabled = game.canStepBack) { Text(Texts.PLAYTEST_BACK) }
        TextButton(onClick = onRestart) { Text(Texts.PLAYTEST_RESTART) }
    }
}

@Composable
private fun PlayMap(game: PlaytestModel, scenario: Scenario, catalog: MaterialCatalog, state: GameState, images: TileImageCache?, modifier: Modifier) {
    val letters = scenario.tokens.associate { it.id to LETTERS.getValue(it.type) }
    if (scenario.showsBattle(state)) {
        BattlePlayMap(game, scenario, catalog, state, images, letters, modifier)
        return
    }
    val scene = playScene(scenario, catalog, state, highlightFor(state.currentPrompt, scenario), letters, images)
    val projection = remember { arrayOfNulls<HexProjection>(1) }
    HexCanvas(
        scene, MaterialTheme.colorScheme.primary,
        modifier.testTag(PLAY_MAP_TAG).pointerInput(state.isIdle, scene.tokens) {
            detectTapGestures { pos ->
                if (!state.isIdle) return@detectTapGestures
                val proj = projection[0] ?: return@detectTapGestures
                val token = tokenAt(proj, scene.tokens, pos)
                if (token != null) game.send(Command.InteractToken(token.id))
                else io.github.aljadvb.worldbuilder.hexmap.exploreMarkerAt(proj, scene.tiles, pos)?.let { game.send(Command.ExploreTile(it.id)) }
            }
        },
        onProjection = { projection[0] = it },
    )
}

@Composable
private fun BattlePlayMap(
    game: PlaytestModel, scenario: Scenario, catalog: MaterialCatalog, state: GameState, images: TileImageCache?,
    letters: Map<String, String>, modifier: Modifier,
) {
    val scene = battlePlayScene(scenario, catalog, state, highlightFor(state.currentPrompt, scenario), letters, images)
    val projection = remember { arrayOfNulls<BattleProjection>(1) }
    var info by remember { mutableStateOf<TerrainInfo?>(null) }
    BattleCanvas(
        scene, MaterialTheme.colorScheme.primary,
        modifier.testTag(PLAY_MAP_TAG).pointerInput(state.isIdle, scene) {
            detectTapGestures { pos ->
                val proj = projection[0] ?: return@detectTapGestures
                val token = if (state.isIdle) squareTokenAt(proj, scene.tokens, pos) else null
                if (token != null) game.send(Command.InteractToken(token.id))
                else pieceAt(proj, scene, pos)?.let { info = scenario.terrainInfo(it.id, catalog) }
            }
        },
        onProjection = { projection[0] = it },
    )
    info?.let { i ->
        AlertDialog(
            onDismissRequest = { info = null },
            confirmButton = { TextButton(onClick = { info = null }) { Text(Texts.OK) } },
            title = { Text(i.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    i.pieceName?.let { Text(Texts.terrainPiece(it), style = MaterialTheme.typography.labelLarge) }
                    i.customRule?.let { r ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                            Column(Modifier.padding(10.dp)) {
                                Text(if (r.mode == RuleMode.REPLACE) Texts.TERRAIN_RULE_REPLACE else Texts.TERRAIN_RULE_ADD, fontWeight = FontWeight.Bold)
                                Text(r.text)
                            }
                        }
                    }
                    val ignored = i.customRule?.mode == RuleMode.REPLACE
                    Text(if (ignored) Texts.TERRAIN_CARD_IGNORED else Texts.TERRAIN_CARD, style = MaterialTheme.typography.labelMedium)
                    Text(i.cardText.ifBlank { i.effect }.ifBlank { Texts.TERRAIN_NO_TEXT }, color = if (ignored) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                }
            },
        )
    }
}

@Composable
private fun IdleActions(game: PlaytestModel, scenario: Scenario, catalog: MaterialCatalog, state: GameState) {
    state.activeEnemyGroups.mapNotNull { scenario.enemyGroup(it) }.forEach { g ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${g.count[state.heroCount] ?: 0}× ${catalog.enemyType(g.enemyType)?.name ?: g.enemyType}", Modifier.weight(1f))
            OutlinedButton(onClick = { game.send(Command.DefeatEnemyGroup(g.id)) }) { Text(Texts.PLAYTEST_DEFEATED) }
        }
    }
    Text(Texts.PLAYTEST_TOKENS, style = MaterialTheme.typography.labelLarge)
    scenario.tokens.filter { it.id in state.activeTokens }.forEach { t ->
        TextButton(onClick = { game.send(Command.InteractToken(t.id)) }) { Text(t.label) }
    }
    Button(onClick = { game.send(Command.EndRound) }, modifier = Modifier.fillMaxWidth()) {
        Text("${Texts.PLAYTEST_END_ROUND} (+${scenario.threat.perRound.amountFor(state.heroCount)})")
    }
}

@Composable
private fun PromptCard(game: PlaytestModel, scenario: Scenario, catalog: MaterialCatalog, prompt: Prompt) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (prompt) {
                is Prompt.Notice -> {
                    Text(describe(prompt, scenario, catalog), style = MaterialTheme.typography.bodyLarge)
                    if (prompt is Prompt.Setup) {
                        scenario.chapter(prompt.chapterId)?.tiles?.forEach { Text(Texts.tileLine(catalog.tile(it.tile)?.label ?: it.tile, it.side.name)) }
                        scenario.battleSetup(prompt.chapterId, catalog)?.let { setup ->
                            setup.boards.forEach { (label, side) -> Text(Texts.boardLine(label, side.name)) }
                            setup.pieces.forEach { (name, n) -> Text(Texts.pieceLine(n, name)) }
                        }
                    }
                    Button(onClick = { game.send(Command.Acknowledge) }, modifier = Modifier.align(Alignment.End)) { Text(Texts.CONTINUE) }
                }
                is Prompt.Choice -> {
                    Text(prompt.step.text, style = MaterialTheme.typography.bodyLarge)
                    prompt.step.options.forEachIndexed { i, o ->
                        FilledTonalButton(onClick = { game.send(Command.Choose(i)) }, modifier = Modifier.fillMaxWidth()) { Text(o.label) }
                    }
                }
                is Prompt.Test -> {
                    var successes by remember(prompt) { mutableIntStateOf(0) }
                    Text(Texts.test(Texts.ATTRIBUTES.first { it.first == prompt.step.attribute.name }.second, prompt.step.successes), fontWeight = FontWeight.Bold)
                    prompt.step.text?.let { Text(it) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(Texts.SUCCESSES_REACHED)
                        OutlinedButton(onClick = { if (successes > 0) successes-- }) { Text("−") }
                        Text("$successes", style = MaterialTheme.typography.titleLarge)
                        OutlinedButton(onClick = { successes++ }) { Text("+") }
                    }
                    Button(onClick = { game.send(Command.ReportTest(successes)) }) { Text(Texts.OK) }
                }
            }
        }
    }
}

@Composable
private fun ResolutionCard(game: PlaytestModel, scenario: Scenario, state: GameState) {
    val r = scenario.resolution(state.resolution.orEmpty()) ?: return
    val outcome = when (r.outcome.name) { "VICTORY" -> Texts.OUTCOME_VICTORY; "DEFEAT" -> Texts.OUTCOME_DEFEAT; else -> Texts.OUTCOME_NEUTRAL }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(Texts.resolution(outcome, r.title), style = MaterialTheme.typography.titleMedium)
            Text(r.text)
            Button(onClick = game::continueCampaign) { Text(Texts.CONTINUE) }
        }
    }
}

/** One-line description of a prompt for the panel and the log. */
fun describe(prompt: Prompt, scenario: Scenario, catalog: MaterialCatalog): String {
    fun tile(placement: String) = scenario.placement(placement)?.let { catalog.tile(it.tile)?.label ?: it.tile } ?: placement
    return when (prompt) {
        Prompt.Intro -> scenario.title + "\n\n" + scenario.intro
        is Prompt.Text -> prompt.text
        is Prompt.Inspect -> scenario.token(prompt.tokenId)?.let { "${it.label}: ${it.inspectText.orEmpty()}" }.orEmpty()
        is Prompt.Setup -> scenario.chapter(prompt.chapterId)?.let { Texts.setup(it.title) + if (it.setupText.isNotBlank()) "\n" + it.setupText else "" }.orEmpty()
        is Prompt.SpawnEnemies -> scenario.enemyGroup(prompt.groupId)?.let { Texts.spawn(prompt.count, catalog.enemyType(it.enemyType)?.name ?: it.enemyType, tile(it.location.placementId.orEmpty())) }.orEmpty()
        is Prompt.PlaceToken -> scenario.token(prompt.tokenId)?.let { Texts.placeToken(it.label, tile(it.location.placementId.orEmpty())) }.orEmpty()
        is Prompt.RemoveToken -> scenario.token(prompt.tokenId)?.let { Texts.removeToken(it.label) }.orEmpty()
        is Prompt.ThreatChanged -> Texts.threatChanged(prompt.delta, prompt.threat)
        is Prompt.RoundEnded -> Texts.roundEnded(prompt.round, prompt.threatAdded, prompt.threat)
        is Prompt.ObjectiveSet -> scenario.objective(prompt.objectiveId)?.let { Texts.objectiveSet(it.title) }.orEmpty()
        is Prompt.ObjectiveCompleted -> scenario.objective(prompt.objectiveId)?.let { Texts.objectiveDone(it.title) }.orEmpty()
        is Prompt.Explored -> Texts.explored(tile(prompt.placementId)) + prompt.tokenIds.mapNotNull { scenario.token(it) }.joinToString("") { "\n• " + Texts.placeToken(it.label, tile(prompt.placementId)) }
        is Prompt.Lore -> listOfNotNull(prompt.text, Texts.lore(prompt.amount)).joinToString("\n")
        is Prompt.Harm -> listOfNotNull(prompt.text, Texts.harm(prompt.damage, prompt.fear, prompt.who, prompt.hidden)).joinToString("\n")
        is Prompt.Inspiration -> listOfNotNull(prompt.text, Texts.inspiration(prompt.amount, prompt.who)).joinToString("\n")
        is Prompt.Choice -> prompt.step.text
        is Prompt.Test -> prompt.step.text.orEmpty()
    }
}
