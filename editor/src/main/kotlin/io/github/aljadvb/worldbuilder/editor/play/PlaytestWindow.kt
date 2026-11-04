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
import io.github.aljadvb.worldbuilder.engine.AttackReason
import io.github.aljadvb.worldbuilder.engine.Command
import io.github.aljadvb.worldbuilder.engine.LimitKind
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
        PlaytestSetup(campaign) { scenarioId, heroes -> game = PlaytestModel(campaign, scenarioId, heroes, catalog) }
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
        if (game.progress.inCamp) {
            Column(Modifier.padding(32.dp).width(640.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CampCard(game)
                game.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Controls(game, onRestart)
            }
            return@Surface
        }
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
    var attacking by remember { mutableStateOf<String?>(null) }
    attacking?.let { gid -> AttackDialog(catalog.enemyType(scenario.enemyGroup(gid)?.enemyType.orEmpty())?.name ?: gid, enemyStatus(scenario, catalog, state, gid), onDismiss = { attacking = null }) { hits, mods ->
        attacking = null
        game.send(Command.AttackEnemy(gid, hits, pierce = "pierce" in mods, smite = "smite" in mods, sunder = "sunder" in mods, cleave = "cleave" in mods, stun = "stun" in mods, lethal = "lethal" in mods))
    } }
    state.activeEnemyGroups.mapNotNull { scenario.enemyGroup(it) }.forEach { g ->
        val type = catalog.enemyType(g.enemyType)?.let(g::effectiveType)
        val health = state.enemyHealth[g.id]?.filter { it > 0 }
        val banner = state.enemyBanners[g.id]?.let { if (g.elite) " · Elite-Banner $it" else " · Banner $it" } ?: if (g.elite) " · Elite" else ""
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${health?.size ?: (g.count[state.heroCount] ?: 0)}× ${type?.name ?: g.enemyType}$banner", fontWeight = FontWeight.Bold)
                if (health != null && type != null) Text(Texts.enemyHealth(health.joinToString(" · ") { "$it/${type.health}" }, (type.armor - (state.armorLoss[g.id] ?: 0)).coerceAtLeast(0), type.sorcery, g.id in state.exhaustedGroups),
                    style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = { attacking = g.id }) { Text(Texts.ATTACK) }
            TextButton(enabled = g.id !in state.exhaustedGroups, onClick = { game.send(Command.ProvokeEnemy(g.id)) }) { Text(Texts.PROVOKE) }
            OutlinedButton(onClick = { game.send(Command.DefeatEnemyGroup(g.id)) }) { Text(Texts.PLAYTEST_DEFEATED) }
        }
    }
    var limits by remember { mutableStateOf(false) }
    TextButton(onClick = { limits = !limits }) { Text((if (limits) "▾ " else "▸ ") + Texts.LIMIT_MENU) }
    if (limits) (0 until state.heroCount).filter { it !in state.defeatedHeroes }.forEach { h ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${Texts.LIMIT_REACHED} Held ${h + 1}", Modifier.weight(1f))
            TextButton(onClick = { game.send(Command.LimitReached(h, LimitKind.DAMAGE)) }) { Text(Texts.LIMIT_DAMAGE) }
            TextButton(onClick = { game.send(Command.LimitReached(h, LimitKind.FEAR)) }) { Text(Texts.LIMIT_FEAR) }
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

/** Hits and modifiers of an attack on an enemy group. */
@Composable
private fun enemyStatus(scenario: Scenario, catalog: MaterialCatalog, state: GameState, gid: String): String {
    val type = scenario.enemyGroup(gid)?.let { g -> catalog.enemyType(g.enemyType)?.let(g::effectiveType) } ?: return ""
    val health = state.enemyHealth[gid]?.filter { it > 0 }.orEmpty()
    return Texts.enemyHealth(health.joinToString(" · ") { "$it/${type.health}" }, (type.armor - (state.armorLoss[gid] ?: 0)).coerceAtLeast(0), type.sorcery, gid in state.exhaustedGroups)
}

@Composable
private fun AttackDialog(name: String, status: String, onDismiss: () -> Unit, onAttack: (Int, Set<String>) -> Unit) {
    var hits by remember { mutableIntStateOf(0) }
    var mods by remember { mutableStateOf(setOf<String>()) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Texts.attackTitle(name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (status.isNotBlank()) Text(status, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(Texts.HITS)
                    OutlinedButton(onClick = { if (hits > 0) hits-- }) { Text("−") }
                    Text("$hits", style = MaterialTheme.typography.titleLarge)
                    OutlinedButton(onClick = { hits++ }) { Text("+") }
                }
                Texts.MODIFIERS.forEach { (key, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(key in mods, { mods = if (it) mods + key else mods - key })
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onAttack(hits, mods) }) { Text(Texts.APPLY) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Texts.CANCEL) } },
    )
}

@Composable
private fun PromptCard(game: PlaytestModel, scenario: Scenario, catalog: MaterialCatalog, prompt: Prompt) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (prompt) {
                is Prompt.Notice -> {
                    Text(describe(prompt, scenario, catalog), style = MaterialTheme.typography.bodyLarge)
                    if (prompt is Prompt.Setup) {
                        if (prompt.chapterId == scenario.startChapter && scenario.effectiveHeroStart != null) Text(Texts.SETUP_HEROES, fontWeight = FontWeight.Bold)
                        scenario.chapter(prompt.chapterId)?.tiles?.forEach { Text(Texts.tileLine(catalog.tile(it.tile)?.label ?: it.tile, it.side.name)) }
                        scenario.battleSetup(prompt.chapterId, catalog)?.let { setup ->
                            if (setup.clearFirst) Text(Texts.CLEAR_BATTLEFIELD_SETUP, fontWeight = FontWeight.Bold)
                            setup.boards.forEach { (label, side) -> Text(Texts.boardLine(label, side.name)) }
                            setup.pieces.forEach { (name, n) -> Text(Texts.pieceLine(n, name)) }
                        }
                    }
                    if (prompt is Prompt.EnemyActivation) {
                        Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { game.send(Command.NoTarget) }) { Text(Texts.NO_TARGET) }
                            OutlinedButton(onClick = { game.send(Command.MovementEnded) }) { Text(Texts.MOVEMENT_ENDED) }
                            Button(onClick = { game.send(Command.Acknowledge) }) { Text(Texts.DONE) }
                        }
                    } else {
                        Button(onClick = { game.send(Command.Acknowledge) }, modifier = Modifier.align(Alignment.End)) { Text(Texts.CONTINUE) }
                    }
                }
                is Prompt.CounterQuestion, is Prompt.LastStandTest -> {
                    Text(describe(prompt, scenario, catalog), style = MaterialTheme.typography.bodyLarge)
                    val counter = prompt is Prompt.CounterQuestion
                    Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { game.send(if (counter) Command.CounterAttack(false) else Command.ReportTest(0)) }) { Text(if (counter) Texts.NO else Texts.FAILED) }
                        Button(onClick = { game.send(if (counter) Command.CounterAttack(true) else Command.ReportTest(1)) }) { Text(if (counter) Texts.YES else Texts.PASSED) }
                    }
                }
                is Prompt.Choice -> {
                    Text(prompt.step.text, style = MaterialTheme.typography.bodyLarge)
                    prompt.step.options.forEachIndexed { i, o ->
                        FilledTonalButton(onClick = { game.send(Command.Choose(i)) }, modifier = Modifier.fillMaxWidth()) {
                            Text(o.label + if (o.corruption > 0) " · +${o.corruption} Verderbnis" else "")
                        }
                    }
                }
                is Prompt.ChooseHero -> {
                    Text(describe(prompt, scenario, catalog), style = MaterialTheme.typography.bodyLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (0 until game.heroCount).forEach { h -> FilledTonalButton(onClick = { game.send(Command.ChooseHero(h)) }) { Text("Held ${h + 1} (${game.state?.corruption?.getOrElse(h) { 0 } ?: 0})") } }
                    }
                }
                is Prompt.Test -> {
                    var successes by remember(prompt) { mutableIntStateOf(0) }
                    val attr = Texts.ATTRIBUTES.first { it.first == prompt.step.attribute.name }.second
                    Text(if (prompt.step.hidden) "Verdeckte Probe: $attr" else Texts.test(attr, prompt.required), fontWeight = FontWeight.Bold)
                    if (prompt.eased) Text("Durch Verderbnis erleichtert.")
                    if (prompt.canEase) OutlinedButton(onClick = { game.send(Command.EaseTest) }) { Text(Texts.ease(prompt.step.corruptionEase)) }
                    if (prompt.step.cumulative) Text(Texts.testProgress(prompt.progress, prompt.step.successes, prompt.step.hidden))
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

/** Simple camp between two scenarios: rewards, lore, inventory with upgrades, next adventure. */
@Composable
private fun CampCard(game: PlaytestModel) {
    val p = game.progress
    val items = game.catalog?.items.orEmpty()
    Text(Texts.CAMP, style = MaterialTheme.typography.headlineSmall)
    p.completed.lastOrNull()?.let { c ->
        val s = game.campaign.scenario(c.scenarioId)
        Text("${s?.title}: ${s?.resolution(c.resolutionId)?.title}", fontWeight = FontWeight.Bold)
        Text(Texts.campRewards(c.lore, c.xp, p.lore))
    }
    p.party.withIndex().filter { it.value.corruption > 0 }.takeIf { it.isNotEmpty() }?.let { list ->
        Text("Verderbnis: " + list.joinToString(", ") { "Held ${it.index + 1}: ${it.value.corruption}" })
    }
    Text(Texts.CAMP_INVENTORY, fontWeight = FontWeight.Bold)
    if (p.inventory.isEmpty()) Text("–")
    p.inventory.forEach { id ->
        val item = items.firstOrNull { it.id == id }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("• ${item?.name ?: id} (${listOf("I", "II", "III").getOrElse((item?.level ?: 1) - 1) { "" }})", Modifier.weight(1f))
            item?.upgrades(items)?.forEach { next ->
                TextButton(enabled = p.lore >= (item.lore ?: 0), onClick = { game.camp { io.github.aljadvb.worldbuilder.engine.Camp.upgradeItem(it, id, items, next.id) } }) {
                    Text(Texts.campUpgrade(next.name, item.lore ?: 0))
                }
            }
        }
    }
    if (p.nextOptions.size > 1) {
        Text(Texts.CAMP_CHOOSE_NEXT, fontWeight = FontWeight.Bold)
        p.nextOptions.forEach { id ->
            FilterChip(id == p.currentScenario, { game.camp { io.github.aljadvb.worldbuilder.engine.Camp.chooseNext(it, id) } }, label = { Text(game.campaign.scenario(id)?.title ?: id) })
        }
    }
    val next = p.currentScenario?.let { game.campaign.scenario(it) }
    Button(onClick = { game.camp { io.github.aljadvb.worldbuilder.engine.Camp.leave(it) } }) { Text(if (next != null) Texts.campContinue(next.title) else Texts.CONTINUE) }
}

/** One-line description of a prompt for the panel and the log. */
fun describe(prompt: Prompt, scenario: Scenario, catalog: MaterialCatalog): String {
    fun tile(placement: String) = scenario.placement(placement)?.let { catalog.tile(it.tile)?.label ?: it.tile } ?: placement
    fun attr(a: io.github.aljadvb.worldbuilder.model.Attribute?) = a?.let { x -> Texts.ATTRIBUTES.first { it.first == x.name }.second }
    fun enemy(groupId: String) = scenario.enemyGroup(groupId)?.let { catalog.enemyType(it.enemyType)?.name ?: it.enemyType } ?: groupId
    return when (prompt) {
        Prompt.Intro -> scenario.title + "\n\n" + scenario.intro
        is Prompt.Text -> prompt.text
        is Prompt.Inspect -> scenario.token(prompt.tokenId)?.let { "${it.label}: ${it.inspectText.orEmpty()}" }.orEmpty()
        is Prompt.Setup -> scenario.chapter(prompt.chapterId)?.let { Texts.setup(it.title) + if (it.setupText.isNotBlank()) "\n" + it.setupText else "" }.orEmpty()
        is Prompt.SpawnEnemies -> scenario.enemyGroup(prompt.groupId)?.let { Texts.spawn(prompt.count, catalog.enemyType(it.enemyType)?.name ?: it.enemyType, tile(it.location.placementId.orEmpty())) }.orEmpty() +
            (prompt.banner?.let { if (prompt.elite) "\nElite! Stellt Elite-Banner $it dazu." else "\nStellt Banner $it dazu." } ?: "") +
                (scenario.enemyGroup(prompt.groupId)?.takeIf { it.elite }?.let { g -> "\n" + listOfNotNull(g.eliteReason, g.eliteBonuses.joinToString(", ") { Texts.eliteBonusName(it) }.ifEmpty { null }).joinToString(" – ") } ?: "")
        is Prompt.PlaceToken -> scenario.token(prompt.tokenId)?.let { Texts.placeToken(it.label, tile(it.location.placementId.orEmpty())) }.orEmpty()
        is Prompt.RemoveToken -> scenario.token(prompt.tokenId)?.let { Texts.removeToken(it.label) }.orEmpty()
        is Prompt.ThreatChanged -> Texts.threatChanged(prompt.delta, prompt.threat)
        is Prompt.RoundEnded -> Texts.roundEnded(prompt.round, prompt.threatAdded, prompt.threat)
        is Prompt.ObjectiveSet -> scenario.objective(prompt.objectiveId)?.let { Texts.objectiveSet(it.title) }.orEmpty()
        is Prompt.ObjectiveCompleted -> scenario.objective(prompt.objectiveId)?.let { Texts.objectiveDone(it.title) }.orEmpty()
        is Prompt.Explored -> Texts.explored(tile(prompt.placementId)) + prompt.tokenIds.mapNotNull { scenario.token(it) }.joinToString("") { "\n• " + Texts.placeToken(it.label, tile(prompt.placementId)) }
        is Prompt.EnemyActivation -> scenario.enemyGroup(prompt.groupId)?.let { g ->
            Texts.activation(catalog.enemyType(g.enemyType)?.name ?: g.enemyType, prompt.movement, "Held ${prompt.hero + 1}", prompt.damage, prompt.fear, prompt.ranged) +
                (if (prompt.text.isNotBlank()) "\n${prompt.text}" else "") + Texts.negate(attr(prompt.negate)) + Texts.attackCondition(prompt.condition)
        }.orEmpty()
        is Prompt.EnemyRecovers -> Texts.recovers(scenario.enemyGroup(prompt.groupId)?.let { catalog.enemyType(it.enemyType)?.name } ?: prompt.groupId)
        is Prompt.Darkness -> Texts.darkness(prompt.fear)
        is Prompt.AttackResult -> Texts.attackResult(prompt.defeated, prompt.remaining, prompt.stunned)
        is Prompt.Lore -> listOfNotNull(prompt.text, Texts.lore(prompt.amount)).joinToString("\n")
        is Prompt.Harm -> listOfNotNull(prompt.text, Texts.harm(prompt.damage, prompt.fear, prompt.who, prompt.hidden)).joinToString("\n")
        is Prompt.Inspiration -> listOfNotNull(prompt.text, Texts.inspiration(prompt.amount, prompt.who)).joinToString("\n")
        is Prompt.Choice -> prompt.step.text
        is Prompt.Test -> prompt.step.text.orEmpty()
        is Prompt.EnemyRegroup -> Texts.regroup(enemy(prompt.groupId), prompt.movement)
        is Prompt.EnemyHalted -> Texts.halted(enemy(prompt.groupId), prompt.damage, prompt.fear) + (if (prompt.text.isNotBlank()) "\n${prompt.text}" else "") + Texts.negate(attr(prompt.negate)) + Texts.attackCondition(prompt.condition)
        is Prompt.EnemyAttack -> Texts.enemyAttack(enemy(prompt.groupId), prompt.reason == AttackReason.COUNTER, prompt.damage, prompt.fear) +
            (if (prompt.text.isNotBlank()) "\n${prompt.text}" else "") + Texts.negate(attr(prompt.negate)) + Texts.attackCondition(prompt.condition)
        is Prompt.TestResult -> Texts.testResult(prompt.passed)
        is Prompt.TestProgress -> Texts.testProgress(prompt.total, prompt.step.successes, prompt.step.hidden)
        is Prompt.Inscription -> listOfNotNull(prompt.text, Texts.inscription(prompt.elvish, if (prompt.translationHidden) "?" else prompt.translation)).joinToString("\n")
        is Prompt.Condition -> listOfNotNull(prompt.text, Texts.condition(prompt.condition, prompt.who)).joinToString("\n")
        is Prompt.Recover -> listOfNotNull(prompt.text, Texts.recover(prompt.damage, prompt.fear, prompt.who)).joinToString("\n")
        is Prompt.ItemFound -> listOfNotNull(prompt.text, Texts.itemFound(prompt.itemId?.let { catalog.item(it)?.name ?: it },
            if (prompt.who == io.github.aljadvb.worldbuilder.model.HeroTarget.CHOSEN) "Ein Held eurer Wahl" else "Der handelnde Held")).joinToString("\n")
        is Prompt.CounterQuestion -> Texts.counterQuestion(enemy(prompt.groupId))
        is Prompt.LastStandTest -> Texts.lastStand(prompt.hero, prompt.kind)
        is Prompt.HeroSurvives -> Texts.heroSurvives(prompt.hero, prompt.kind)
        is Prompt.ChooseHero -> listOfNotNull(prompt.text, Texts.chooseHero(prompt.question)).joinToString("\n")
        is Prompt.Corruption -> listOfNotNull(prompt.text, Texts.corruption(prompt.hero, prompt.delta, prompt.total)).joinToString("\n")
        is Prompt.CorruptionSetup -> Texts.corruptionSetup(prompt.values)
        is Prompt.FieldMarker -> listOfNotNull(prompt.text, Texts.fieldMarker(prompt.marker, when (val l = prompt.location) {
            is io.github.aljadvb.worldbuilder.model.TileLocation -> "Kachel ${tile(l.placement)}, Feld ${l.cell.q}/${l.cell.r}"
            is io.github.aljadvb.worldbuilder.model.SquareLocation -> "Feld ${l.square.x}/${l.square.y}"
        }, prompt.placed)).joinToString("\n")
        is Prompt.HeroDefeated -> Texts.heroDefeated(prompt.hero, prompt.secondTime)
    }
}
