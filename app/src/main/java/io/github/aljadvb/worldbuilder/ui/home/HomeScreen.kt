package io.github.aljadvb.worldbuilder.ui.home

import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aljadvb.worldbuilder.R
import io.github.aljadvb.worldbuilder.data.CampaignLibrary
import io.github.aljadvb.worldbuilder.data.CampaignLibrary.ImportResult
import io.github.aljadvb.worldbuilder.engine.save.SaveGame
import io.github.aljadvb.worldbuilder.ui.common.ValidationReportDialog
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenGame: (campaignId: String, slot: String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var newGameFor by remember { mutableStateOf<CampaignLibrary.StoredCampaign?>(null) }
    var reportFor by remember { mutableStateOf<CampaignLibrary.StoredCampaign?>(null) }
    var deleteCampaign by remember { mutableStateOf<CampaignLibrary.StoredCampaign?>(null) }
    var deleteSave by remember { mutableStateOf<SaveGame?>(null) }

    fun readUri(uri: android.net.Uri?, handle: (String) -> Unit) {
        if (uri == null) return
        runCatching { context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() } }
            .onSuccess(handle)
            .onFailure { viewModel.reportReadError(it.message ?: it.javaClass.simpleName) }
    }

    val campaignPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        readUri(uri, viewModel::importCampaign)
    }
    val catalogPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.openInputStream(uri)!!.use { it.readBytes() } }
                .onSuccess(viewModel::importCatalog)
                .onFailure { viewModel.reportReadError(it.message ?: it.javaClass.simpleName) }
        }
    }
    val mimeTypes = arrayOf("application/json", "text/plain", "application/octet-stream")

    // Successful imports become a snackbar; failures a dialog.
    val successText = (state.importResult?.result as? ImportResult.Success)?.let { outcome ->
        if (state.importResult?.isCatalog == true) stringResource(R.string.import_catalog_success)
        else stringResource(R.string.import_success, outcome.title)
    }
    LaunchedEffect(state.importResult) {
        val result = state.importResult?.result
        if (result is ImportResult.Success && result.report.issues.isEmpty() && successText != null) {
            snackbar.showSnackbar(successText)
            viewModel.dismissImportResult()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.home_import_campaign)) },
                            onClick = { menuOpen = false; campaignPicker.launch(mimeTypes) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.home_import_catalog)) },
                            onClick = { menuOpen = false; catalogPicker.launch(mimeTypes + "application/zip") },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.home_install_example)) },
                            onClick = { menuOpen = false; viewModel.installExample() },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    if (state.usesExampleCatalog) stringResource(R.string.home_catalog_example)
                    else stringResource(R.string.home_catalog_own, state.tileCount, state.enemyTypeCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { SectionTitle(stringResource(R.string.home_saves)) }
            if (state.saves.isEmpty() && !state.loading) {
                item { Text(stringResource(R.string.home_no_saves)) }
            }
            items(state.saves, key = { "save_${it.save.campaignId}_${it.save.slot}" }) { summary ->
                SaveCard(
                    summary = summary,
                    onResume = { onOpenGame(summary.save.campaignId, summary.save.slot) },
                    onDelete = { deleteSave = summary.save },
                )
            }
            item { SectionTitle(stringResource(R.string.home_campaigns)) }
            if (state.campaigns.isEmpty() && !state.loading) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.home_no_campaigns))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { viewModel.installExample() }) { Text(stringResource(R.string.home_install_example)) }
                            OutlinedButton(onClick = { campaignPicker.launch(mimeTypes) }) { Text(stringResource(R.string.home_import_campaign)) }
                        }
                    }
                }
            }
            items(state.campaigns, key = { "campaign_${it.campaign.id}" }) { stored ->
                CampaignCard(
                    stored = stored,
                    onNewGame = { newGameFor = stored },
                    onShowReport = { reportFor = stored },
                    onDelete = { deleteCampaign = stored },
                )
            }
        }
    }

    newGameFor?.let { stored ->
        NewGameDialog(
            title = stored.campaign.title,
            onDismiss = { newGameFor = null },
            onStart = { heroes ->
                newGameFor = null
                viewModel.startNewGame(stored, heroes, onOpenGame)
            },
        )
    }
    reportFor?.let { stored ->
        ValidationReportDialog(stringResource(R.string.report_title), stored.campaign.title, stored.report) { reportFor = null }
    }
    deleteCampaign?.let { stored ->
        ConfirmDialog(
            title = stringResource(R.string.home_delete_campaign_title),
            text = stringResource(R.string.home_delete_campaign_text),
            onConfirm = { viewModel.deleteCampaign(stored.campaign.id); deleteCampaign = null },
            onDismiss = { deleteCampaign = null },
        )
    }
    deleteSave?.let { save ->
        ConfirmDialog(
            title = stringResource(R.string.home_delete_save_title),
            text = save.campaignTitle,
            onConfirm = { viewModel.deleteSave(save); deleteSave = null },
            onDismiss = { deleteSave = null },
        )
    }
    state.importResult?.let { outcome ->
        when (val result = outcome.result) {
            is ImportResult.ParseError -> AlertDialog(
                onDismissRequest = viewModel::dismissImportResult,
                confirmButton = { TextButton(onClick = viewModel::dismissImportResult) { Text(stringResource(R.string.close)) } },
                title = { Text(stringResource(R.string.import_failed_title)) },
                text = { Text(stringResource(R.string.import_parse_error, result.message)) },
            )
            is ImportResult.Invalid -> ValidationReportDialog(
                stringResource(R.string.import_failed_title),
                stringResource(R.string.import_invalid),
                result.report,
                viewModel::dismissImportResult,
            )
            is ImportResult.Success -> if (result.report.issues.isNotEmpty()) {
                ValidationReportDialog(stringResource(R.string.report_title), successText, result.report, viewModel::dismissImportResult)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun SaveCard(summary: SaveSummary, onResume: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val date = Date(summary.save.savedAtEpochMillis)
    val dateText = DateFormat.getMediumDateFormat(context).format(date) + " " + DateFormat.getTimeFormat(context).format(date)
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(summary.save.campaignTitle, style = MaterialTheme.typography.titleSmall)
                val game = summary.save.game
                Text(
                    when {
                        !summary.campaignAvailable -> stringResource(R.string.home_missing_campaign)
                        game == null -> stringResource(R.string.home_save_campaign_over, dateText)
                        else -> stringResource(R.string.home_save_line, summary.scenarioTitle.orEmpty(), game.round, dateText)
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete)) }
            Button(onClick = onResume, enabled = summary.campaignAvailable) { Text(stringResource(R.string.home_resume)) }
        }
    }
}

@Composable
private fun CampaignCard(
    stored: CampaignLibrary.StoredCampaign,
    onNewGame: () -> Unit,
    onShowReport: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stored.campaign.title, style = MaterialTheme.typography.titleMedium)
            if (stored.campaign.description.isNotBlank()) {
                Text(stored.campaign.description, style = MaterialTheme.typography.bodyMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val errors = stored.report.errors.size
                val warnings = stored.report.warnings.size
                if (errors > 0 || warnings > 0) {
                    TextButton(onClick = onShowReport) {
                        Text(
                            if (errors > 0) stringResource(R.string.home_errors, errors)
                            else stringResource(R.string.home_warnings, warnings),
                            color = if (errors > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete)) }
                Button(onClick = onNewGame, enabled = stored.report.isValid) { Text(stringResource(R.string.home_new_game)) }
            }
        }
    }
}

@Composable
private fun NewGameDialog(title: String, onDismiss: () -> Unit, onStart: (List<io.github.aljadvb.worldbuilder.engine.Hero>) -> Unit) {
    var heroes by remember { mutableIntStateOf(2) }
    val names = remember { androidx.compose.runtime.mutableStateListOf("", "", "", "", "") }
    val roles = remember { androidx.compose.runtime.mutableStateListOf("", "", "", "", "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_game_title, title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.new_game_heroes))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..5).forEach { n ->
                        FilterChip(selected = heroes == n, onClick = { heroes = n }, label = { Text("$n") })
                    }
                }
                Text(stringResource(R.string.new_game_hero_hint), style = MaterialTheme.typography.bodySmall)
                (0 until heroes).forEach { i ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        androidx.compose.material3.OutlinedTextField(names[i], { names[i] = it }, Modifier.weight(1f), singleLine = true,
                            label = { Text(stringResource(R.string.new_game_hero_name, i + 1)) })
                        androidx.compose.material3.OutlinedTextField(roles[i], { roles[i] = it }, Modifier.weight(1f), singleLine = true,
                            label = { Text(stringResource(R.string.new_game_hero_role)) })
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onStart((0 until heroes).map { i -> io.github.aljadvb.worldbuilder.engine.Hero(names[i].trim().ifEmpty { "Held ${i + 1}" }, roles[i].trim()) })
            }) { Text(stringResource(R.string.new_game_start)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, onConfirm: () -> Unit, onDismiss: () -> Unit, confirmLabel: String? = null) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { Button(onClick = onConfirm) { Text(confirmLabel ?: stringResource(R.string.confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

