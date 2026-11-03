package io.github.aljadvb.worldbuilder.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.layout.onSizeChanged
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
import androidx.compose.material3.FilterChip
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
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aljadvb.worldbuilder.R
import io.github.aljadvb.worldbuilder.engine.unexploredPlacements
import io.github.aljadvb.worldbuilder.engine.Command
import io.github.aljadvb.worldbuilder.engine.GameState
import io.github.aljadvb.worldbuilder.engine.HistoryEntry
import io.github.aljadvb.worldbuilder.engine.Prompt
import io.github.aljadvb.worldbuilder.hexmap.showsBattle
import io.github.aljadvb.worldbuilder.hexmap.terrainInfo
import io.github.aljadvb.worldbuilder.model.Campaign
import io.github.aljadvb.worldbuilder.model.MaterialCatalog
import io.github.aljadvb.worldbuilder.model.Scenario
import io.github.aljadvb.worldbuilder.model.TriggerCondition
import io.github.aljadvb.worldbuilder.hexmap.highlightFor
import io.github.aljadvb.worldbuilder.ui.common.outcomeLabel
import io.github.aljadvb.worldbuilder.ui.common.tokenTypeLabel
import io.github.aljadvb.worldbuilder.ui.home.ConfirmDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(viewModel: GameViewModel, onBack: () -> Unit) {
    val load by viewModel.state.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }
    var showCampaign by remember { mutableStateOf(false) }
    val ui = (load as? GameLoad.Ready)?.ui
    val game = ui?.game
    val scenario = ui?.scenario
    val tileImages = rememberTileImages(ui?.catalogImageDir)
    val mapBackground = ui?.catalog?.campaignMap?.let { tileImages.bitmap(it.file) }

    val playing = ui != null && game != null && scenario != null && !game.isOver && !ui.save.progress.inCamp
    Scaffold(
        topBar = {
            TopAppBar(
                colors = if (playing) androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
                    else androidx.compose.material3.TopAppBarDefaults.topAppBarColors(),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(scenario?.title ?: ui?.campaign?.title.orEmpty(), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                            if (game != null && !game.isOver) {
                                Text(stringResource(R.string.game_round, game.round), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        if (playing && scenario != null && game != null) ThreatBar(scenario, game.threat, Modifier.width(150.dp))
                    }
                },
                actions = {
                    if (game != null) {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more)) }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.game_log)) }, onClick = { menuOpen = false; showLog = true })
                            DropdownMenuItem(text = { Text(stringResource(R.string.campaign_overview)) }, onClick = { menuOpen = false; showCampaign = true })
                            DropdownMenuItem(text = { Text(stringResource(R.string.game_to_menu)) }, onClick = { menuOpen = false; onBack() })
                        }
                    }
                },
            )
        },
    ) { padding ->
        // While playing, the map lies under the translucent top bar.
        Box(Modifier.fillMaxSize().then(if (playing) Modifier.padding(bottom = padding.calculateBottomPadding()) else Modifier.padding(padding))) {
            when (val l = load) {
                GameLoad.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                GameLoad.SaveMissing -> Message(stringResource(R.string.game_load_failed))
                is GameLoad.CampaignMissing -> Message(stringResource(R.string.game_campaign_missing, l.title))
                is GameLoad.Ready -> when {
                    game == null -> CampaignOver(l.ui.campaign, l.ui.save.progress, mapBackground, onBack)
                    l.ui.save.progress.inCamp -> CampContent(l.ui.campaign, l.ui.save.progress, mapBackground, l.ui.catalog, viewModel::camp)
                    scenario == null -> Message(stringResource(R.string.game_load_failed))
                    game.isOver -> ResolutionContent(scenario, game, onContinue = viewModel::continueCampaign)
                    else -> PlayContent(scenario, l.ui.catalog, game, l.ui.campaignChanged, viewModel::send, tileImages, l.ui.save.progress.party.map { it.name }, topInset = padding.calculateTopPadding())
                }
            }
        }
    }

    if (showCampaign && ui != null) {
        AlertDialog(
            onDismissRequest = { showCampaign = false },
            confirmButton = { TextButton(onClick = { showCampaign = false }) { Text(stringResource(R.string.close)) } },
            title = { Text(ui.campaign.title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CampaignOverview(ui.campaign, ui.save.progress, mapBackground, ui.catalog)
                    HeroesSection(ui.save.progress, editable = false)
                }
            },
        )
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
    images: io.github.aljadvb.worldbuilder.hexmap.TileImageCache? = null,
    heroNames: List<String> = emptyList(),
    topInset: androidx.compose.ui.unit.Dp = 0.dp,
) {
    var attackGroup by remember { mutableStateOf<String?>(null) }
    var confirmEndRound by remember { mutableStateOf(false) }
    var confirmDefeat by remember { mutableStateOf<String?>(null) }
    var showLimit by remember { mutableStateOf(false) }
    var enemyMenu by remember { mutableStateOf<String?>(null) }
    var tokenInfo by remember { mutableStateOf<String?>(null) }
    var terrainInfo by remember { mutableStateOf<io.github.aljadvb.worldbuilder.hexmap.TerrainInfo?>(null) }
    val prompt = game.currentPrompt
    val tokenLetters = scenario.tokens.associate { it.id to tokenTypeLabel(it).take(1).uppercase() }

    // The map fills the screen; objective line and bottom panel lie on top of it, translucent.
    // The map is fitted into the visible space between them (measured), and can be zoomed and moved under them.
    val density = androidx.compose.ui.platform.LocalDensity.current
    var topHeight by remember { mutableStateOf(0.dp) }
    var bottomHeight by remember { mutableStateOf(0.dp) }
    Box(Modifier.fillMaxSize()) {
        if (scenario.showsBattle(game)) {
            BattleMapView(
                scenario = scenario,
                catalog = catalog,
                state = game,
                highlight = highlightFor(prompt, scenario),
                tokenLetters = tokenLetters,
                tapEnabled = game.isIdle,
                onTokenTap = { tokenInfo = it },
                onPieceTap = { terrainInfo = scenario.terrainInfo(it, catalog) },
                modifier = Modifier.fillMaxSize().padding(top = topInset + topHeight, bottom = bottomHeight),
                images = images,
                onEnemyTap = { enemyMenu = it },
            )
        } else {
            HexMap(
                scenario = scenario,
                catalog = catalog,
                state = game,
                highlight = highlightFor(prompt, scenario),
                tokenLetters = tokenLetters,
                tapEnabled = game.isIdle,
                onTokenTap = { tokenInfo = it },
                modifier = Modifier.fillMaxSize().padding(top = topInset + topHeight, bottom = bottomHeight),
                images = images,
                onExploreTap = { onCommand(Command.ExploreTile(it)) },
                onEnemyTap = { enemyMenu = it },
            )
        }
        Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(topInset))
        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp).onSizeChanged { topHeight = with(density) { it.height.toDp() } + 8.dp }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (topInset == 0.dp) ThreatBar(scenario, game.threat, Modifier.fillMaxWidth())
            Objectives(scenario, game)
            if (campaignChanged) {
                Text(stringResource(R.string.game_campaign_changed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.onSizeChanged { bottomHeight = with(density) { it.height.toDp() } }) {
        if (prompt != null) {
            PromptPanel(prompt, scenario, catalog, game.heroCount, onCommand, Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp), heroNames, images)
        } else {
            androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            ActionPanel(
                scenario = scenario,
                catalog = catalog,
                game = game,
                onToken = { tokenInfo = it },
                onDefeat = { confirmDefeat = it },
                onEndRound = { confirmEndRound = true },
                battle = scenario.showsBattle(game),
                onAttack = { attackGroup = it },
                onProvoke = { onCommand(Command.ProvokeEnemy(it)) },
                onEnemyMenu = { enemyMenu = it },
                onLimit = { showLimit = true },
            )
            }
            }
        }
        }
        }
    }

    tokenInfo?.takeIf { it in game.activeTokens && game.isIdle }?.let { id ->
        TokenDialog(scenario, id, onDismiss = { tokenInfo = null }) { tokenInfo = null; onCommand(Command.InteractToken(id, showText = false)) }
    }
    enemyMenu?.takeIf { it in game.activeEnemyGroups }?.let { gid ->
        EnemyMenu(scenario, catalog, game, gid, onDismiss = { enemyMenu = null },
            onAttack = { enemyMenu = null; attackGroup = gid },
            onProvoke = { enemyMenu = null; onCommand(Command.ProvokeEnemy(gid)) },
            onDefeat = { enemyMenu = null; confirmDefeat = gid })
    }
    if (showLimit) LimitDialog(game, heroNames, onDismiss = { showLimit = false }) { hero, kind -> showLimit = false; onCommand(Command.LimitReached(hero, kind)) }
    terrainInfo?.let { TerrainDialog(it, images, onDismiss = { terrainInfo = null }) }
    attackGroup?.let { gid -> AttackDialog(scenario, catalog, game, gid, onDismiss = { attackGroup = null }) { onCommand(it); attackGroup = null } }
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
private fun ThreatBar(scenario: Scenario, threat: Int, modifier: Modifier = Modifier) {
    val max = scenario.threat.max
    val thresholds = scenario.triggers.mapNotNull { (it.on as? TriggerCondition.ThreatReached)?.value }.filter { it in 1 until max }
    val markerColor = MaterialTheme.colorScheme.onSurface
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.game_threat, threat, max), style = MaterialTheme.typography.labelMedium)
        Box {
            LinearProgressIndicator(
                progress = { threat.toFloat() / max },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = MaterialTheme.colorScheme.error,
                trackColor = MaterialTheme.colorScheme.outlineVariant,
            )
            Canvas(Modifier.fillMaxWidth().height(8.dp)) {
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
    var open by remember { mutableStateOf(false) }
    androidx.compose.material3.Surface(
        onClick = { open = true },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            stringResource(R.string.game_objective_line, objectives.joinToString(" · ") { it.title }),
            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.close)) } },
        title = { Text(stringResource(R.string.game_objectives)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                objectives.forEach { o ->
                    Text(o.title, fontWeight = FontWeight.Bold)
                    Text(o.reminder, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
    )
}

@Composable
private fun ActionPanel(
    scenario: Scenario,
    catalog: MaterialCatalog,
    game: GameState,
    onToken: (String) -> Unit,
    onDefeat: (String) -> Unit,
    onEndRound: () -> Unit,
    battle: Boolean = false,
    onAttack: (String) -> Unit = {},
    onProvoke: (String) -> Unit = {},
    onLimit: () -> Unit = {},
    onEnemyMenu: (String) -> Unit = {},
) {
    Column(Modifier.padding(bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val groups = game.activeEnemyGroups.mapNotNull { scenario.enemyGroup(it) }
        if (groups.isNotEmpty()) {
            Text(stringResource(R.string.game_enemies), style = MaterialTheme.typography.labelMedium)
            Text(stringResource(R.string.provoke_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            groups.forEach { g ->
                Column {
                    val name = enemyLabel(catalog, game, g.id, g.enemyType)
                    val health = game.enemyHealth[g.id]
                    Column(Modifier.fillMaxWidth().clickable { onEnemyMenu(g.id) }) {
                        Text(stringResource(R.string.game_enemy_line, health?.count { it > 0 } ?: (g.count[game.heroCount] ?: 0), name), fontWeight = FontWeight.Bold)
                        val type = catalog.enemyType(g.enemyType)
                        if (health != null && type != null) {
                            Text(
                                stringResource(R.string.enemy_health_line, health.filter { it > 0 }.joinToString(" · ") { "$it/${type.health}" },
                                    (type.armor - (game.armorLoss[g.id] ?: 0)).coerceAtLeast(0), type.sorcery) +
                                    if (g.id in game.exhaustedGroups) " · " + stringResource(R.string.enemy_exhausted) else "",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(onClick = { onAttack(g.id) }) { Text(stringResource(R.string.attack)) }
                        TextButton(enabled = g.id !in game.exhaustedGroups, onClick = { onProvoke(g.id) }) { Text(stringResource(R.string.provoke)) }
                        TextButton(onClick = { onDefeat(g.id) }) { Text(stringResource(R.string.game_defeated)) }
                    }
                }
            }
        }
        val tokens = scenario.tokens.filter { it.id in game.activeTokens }
        Text(
            when {
                battle -> stringResource(R.string.game_tap_hint_battle)
                game.unexploredPlacements(scenario).isNotEmpty() -> stringResource(R.string.game_tap_hint_explore)
                tokens.isEmpty() -> stringResource(R.string.game_no_tokens)
                else -> stringResource(R.string.game_tap_hint)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            tokens.forEach { t -> AssistChip(onClick = { onToken(t.id) }, label = { Text(t.label) }) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onEndRound, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.game_end_round)) }
            // Rarely needed actions in a small menu.
            var more by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { more = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more)) }
                DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.limit_reached_menu)) }, onClick = { more = false; onLimit() })
                }
            }
        }
    }
}

/** Token description first; the players decide whether to interact (e.g. take the test). */
@Composable
private fun TokenDialog(scenario: Scenario, tokenId: String, onDismiss: () -> Unit, onInteract: () -> Unit) {
    val t = scenario.token(tokenId) ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t.label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tokenTypeLabel(t), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(t.inspectText?.takeIf { it.isNotBlank() } ?: stringResource(R.string.token_no_text), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.token_interact_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button(onClick = onInteract) { Text(stringResource(R.string.token_interact)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.back)) } },
    )
}

/** Enemy name with its banner number, e.g. "Ork-Jäger · Banner 2". */
@Composable
internal fun enemyLabel(catalog: MaterialCatalog, game: GameState, groupId: String, enemyType: String): String {
    val name = catalog.enemyType(enemyType)?.name ?: enemyType
    return game.enemyBanners[groupId]?.let { stringResource(R.string.enemy_with_banner, name, it) } ?: name
}

/** Enemy menu of one group: values, health of each enemy, and the actions. */
@Composable
private fun EnemyMenu(scenario: Scenario, catalog: MaterialCatalog, game: GameState, groupId: String, onDismiss: () -> Unit, onAttack: () -> Unit, onProvoke: () -> Unit, onDefeat: () -> Unit) {
    val g = scenario.enemyGroup(groupId) ?: return
    val type = catalog.enemyType(g.enemyType)
    val health = game.enemyHealth[groupId]
    val exhausted = groupId in game.exhaustedGroups
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        title = { Text(enemyLabel(catalog, game, groupId, g.enemyType)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (type != null) {
                    Text(stringResource(R.string.enemy_menu_values, (type.armor - (game.armorLoss[groupId] ?: 0)).coerceAtLeast(0), type.sorcery, type.movement) +
                        if (type.ranged) " · " + stringResource(R.string.enemy_menu_ranged) else "")
                }
                val alive = health?.withIndex()?.filter { it.value > 0 }.orEmpty()
                alive.forEach { (i, hp) -> Text(stringResource(R.string.enemy_menu_health, i + 1, hp, type?.health ?: hp)) }
                if (exhausted) Text(stringResource(R.string.enemy_exhausted), color = MaterialTheme.colorScheme.error)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onAttack) { Text(stringResource(R.string.attack)) }
                    OutlinedButton(enabled = !exhausted, onClick = onProvoke) { Text(stringResource(R.string.provoke)) }
                }
                TextButton(onClick = onDefeat) { Text(stringResource(R.string.game_defeated)) }
            }
        },
    )
}

/** Pick the hero who reached a limit and whether it is damage or fear. */
@Composable
private fun LimitDialog(game: GameState, heroNames: List<String>, onDismiss: () -> Unit, onPick: (Int, io.github.aljadvb.worldbuilder.engine.LimitKind) -> Unit) {
    var hero by remember { mutableStateOf<Int?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        title = { Text(stringResource(R.string.limit_reached)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.limit_choose_hero))
                (0 until game.heroCount).forEach { i ->
                    val name = heroName(heroNames, i)
                    val defeated = i in game.defeatedHeroes
                    FilterChip(
                        selected = hero == i, enabled = !defeated, onClick = { hero = i },
                        label = { Text(if (defeated) stringResource(R.string.hero_is_defeated, name) else name) },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = hero != null, onClick = { onPick(hero!!, io.github.aljadvb.worldbuilder.engine.LimitKind.DAMAGE) }) { Text(stringResource(R.string.limit_damage)) }
                    Button(enabled = hero != null, onClick = { onPick(hero!!, io.github.aljadvb.worldbuilder.engine.LimitKind.FEAR) }) { Text(stringResource(R.string.limit_fear)) }
                }
            }
        },
    )
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
private fun CampaignOver(campaign: Campaign, progress: io.github.aljadvb.worldbuilder.engine.CampaignProgress, background: androidx.compose.ui.graphics.ImageBitmap?, onBack: () -> Unit) {
    val played = progress.completed.map { it.scenarioId to it.resolutionId }
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
        CampaignOverview(campaign, progress, background)
        HeroesSection(progress, editable = false)
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
    is Prompt.Explored -> stringResource(R.string.prompt_explored_short)
    is Prompt.Inspiration -> inspirationText(prompt)
    is Prompt.TestResult -> stringResource(if (prompt.passed) R.string.test_passed else R.string.test_failed)
    is Prompt.TestProgress -> stringResource(R.string.test_progress_title)
    is Prompt.Recover -> recoverText(prompt)
    is Prompt.ItemFound -> stringResource(R.string.item_found_title) + ": " + (prompt.itemId ?: "–")
    is Prompt.Harm -> harmText(prompt)
    is Prompt.Lore -> stringResource(R.string.prompt_lore_text, prompt.amount)
    is Prompt.EnemyActivation -> stringResource(R.string.shadow_phase) + ": " + (scenario.enemyGroup(prompt.groupId)?.id ?: prompt.groupId) + " · " + stringResource(R.string.enemy_attack_values, prompt.damage, prompt.fear)
    is Prompt.EnemyRecovers -> stringResource(R.string.enemy_recovers, prompt.groupId)
    is Prompt.Darkness -> stringResource(R.string.darkness_text, prompt.fear)
    is Prompt.AttackResult -> stringResource(R.string.attack_result, prompt.defeated, prompt.remaining)
    is Prompt.Choice -> prompt.step.text
    is Prompt.Test -> prompt.step.text ?: ""
    is Prompt.EnemyRegroup -> stringResource(R.string.enemy_no_target) + ": " + prompt.groupId
    is Prompt.EnemyHalted -> stringResource(R.string.enemy_movement_ended) + ": " + prompt.groupId + " · " + stringResource(R.string.enemy_attack_values, prompt.damage, prompt.fear)
    is Prompt.EnemyAttack -> prompt.groupId + " · " + stringResource(R.string.enemy_attack_values, prompt.damage, prompt.fear)
    is Prompt.CounterQuestion -> stringResource(R.string.counter_question)
    is Prompt.LastStandTest -> stringResource(R.string.last_stand_title, stringResource(R.string.hero_n, prompt.hero + 1))
    is Prompt.HeroSurvives -> stringResource(R.string.hero_survives_title, stringResource(R.string.hero_n, prompt.hero + 1))
    is Prompt.HeroDefeated -> stringResource(R.string.hero_defeated_title, stringResource(R.string.hero_n, prompt.hero + 1))
}


/** Hits and modifiers of an attack on an enemy group. */
@Composable
private fun AttackDialog(scenario: Scenario, catalog: MaterialCatalog, game: GameState, groupId: String, onDismiss: () -> Unit, onAttack: (Command) -> Unit) {
    val group = scenario.enemyGroup(groupId) ?: return
    var hits by remember { mutableStateOf(0) }
    val mods = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
    val labels = listOf(
        "pierce" to R.string.mod_pierce, "smite" to R.string.mod_smite, "sunder" to R.string.mod_sunder,
        "cleave" to R.string.mod_cleave, "stun" to R.string.mod_stun, "lethal" to R.string.mod_lethal,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.attack_title, enemyLabel(catalog, game, groupId, group.enemyType))) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Current state of the group: health of each enemy, armor (after "Zerfallen") and sorcery.
                val type = catalog.enemyType(group.enemyType)
                val health = game.enemyHealth[groupId]?.withIndex()?.filter { it.value > 0 }.orEmpty()
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        health.forEach { (i, hp) -> Text(stringResource(R.string.enemy_menu_health, i + 1, hp, type?.health ?: hp), fontWeight = FontWeight.Bold) }
                        if (type != null) {
                            val armor = (type.armor - (game.armorLoss[groupId] ?: 0)).coerceAtLeast(0)
                            Text(stringResource(R.string.attack_defense, armor, type.sorcery), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Text(stringResource(R.string.attack_hits))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedButton(onClick = { if (hits > 0) hits-- }) { Text("−") }
                    Text("$hits", style = MaterialTheme.typography.headlineMedium)
                    OutlinedButton(onClick = { if (hits < 40) hits++ }) { Text("+") }
                }
                labels.forEach { (key, res) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(mods[key] == true, { mods[key] = it })
                        Text(stringResource(res), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onAttack(Command.AttackEnemy(groupId, hits, pierce = mods["pierce"] == true, smite = mods["smite"] == true, sunder = mods["sunder"] == true,
                    cleave = mods["cleave"] == true, stun = mods["stun"] == true, lethal = mods["lethal"] == true))
            }) { Text(stringResource(R.string.attack_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
