package io.github.aljadvb.worldbuilder.ui.game

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.aljadvb.worldbuilder.R
import io.github.aljadvb.worldbuilder.engine.Camp
import io.github.aljadvb.worldbuilder.engine.CampaignProgress
import io.github.aljadvb.worldbuilder.engine.Hero
import io.github.aljadvb.worldbuilder.hexmap.CampaignMapCanvas
import io.github.aljadvb.worldbuilder.hexmap.campaignMapScene
import io.github.aljadvb.worldbuilder.hexmap.placeAt
import io.github.aljadvb.worldbuilder.model.Campaign

/** Camp between two scenarios: rewards, lore, heroes' experience, then on to the next adventure. */
@Composable
fun CampContent(campaign: Campaign, progress: CampaignProgress, onAction: ((CampaignProgress) -> CampaignProgress) -> Unit) {
    val last = progress.completed.lastOrNull()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.camp_title), style = MaterialTheme.typography.headlineMedium)
        last?.let { c ->
            val s = campaign.scenario(c.scenarioId)
            Text(stringResource(R.string.camp_last, s?.title ?: c.scenarioId, s?.resolution(c.resolutionId)?.title ?: c.resolutionId))
            if (c.lore > 0 || c.xp > 0) Text(stringResource(R.string.camp_rewards, c.lore, c.xp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        CampaignOverview(campaign, progress)
        HeroesSection(progress, editable = true, onAction = onAction)
        val next = progress.currentScenario?.let { campaign.scenario(it) }
        Button(onClick = { onAction(Camp::leave) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.camp_continue, next?.title.orEmpty()))
        }
    }
}

/** Map, lore and the list of played scenarios. Shared by camp, campaign end and the in-game overview. */
@Composable
fun CampaignOverview(campaign: Campaign, progress: CampaignProgress) {
    var selected by remember { mutableStateOf<String?>(null) }
    if (campaign.places.isNotEmpty()) {
        val scene = campaignMapScene(campaign, progress.completed.map { it.scenarioId to it.resolutionId }, progress.currentScenario, selected)
        val size = remember { arrayOf(Size.Zero) }
        CampaignMapCanvas(
            scene, MaterialTheme.colorScheme.primary,
            Modifier.fillMaxWidth().height(260.dp)
                .pointerInput(scene) { this@pointerInput.size.let { size[0] = Size(it.width.toFloat(), it.height.toFloat()) }; detectTapGestures { p -> selected = placeAt(size[0], scene, p)?.id } },
        )
        selected?.let { placeId ->
            val place = campaign.place(placeId)
            val played = progress.completed.filter { campaign.scenario(it.scenarioId)?.place == placeId }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(place?.name.orEmpty(), fontWeight = FontWeight.Bold)
                    if (played.isEmpty()) Text(stringResource(R.string.camp_place_unplayed), style = MaterialTheme.typography.bodySmall)
                    played.forEach { c ->
                        val s = campaign.scenario(c.scenarioId)
                        Text("• ${s?.title}: ${s?.resolution(c.resolutionId)?.title}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.camp_lore, progress.lore), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.camp_lore_hint, progress.lore), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun HeroesSection(progress: CampaignProgress, editable: Boolean, onAction: ((CampaignProgress) -> CampaignProgress) -> Unit = {}) {
    var buyFor by remember { mutableStateOf<Int?>(null) }
    var roleFor by remember { mutableStateOf<Int?>(null) }
    Text(stringResource(R.string.camp_heroes), style = MaterialTheme.typography.titleMedium)
    progress.party.forEachIndexed { i, h ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(h.name + if (h.role.isNotBlank()) " – ${h.role}" else "", fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.camp_xp_current, h.xpFor(h.role).available, h.xpFor(h.role).earned), style = MaterialTheme.typography.bodyMedium)
                h.xp.filterKeys { it != h.role }.filterValues { it.earned > 0 }.forEach { (role, xp) ->
                    Text(stringResource(R.string.camp_xp_other, role.ifBlank { "–" }, xp.available), style = MaterialTheme.typography.bodySmall)
                }
                h.skills.forEachIndexed { si, s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "• ${s.name.ifBlank { stringResource(R.string.camp_skill) }} (${s.role.ifBlank { "–" }}, ${s.cost} EP)",
                            Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                            textDecoration = if (s.sold) TextDecoration.LineThrough else null,
                        )
                        if (editable && !s.sold) TextButton(onClick = { onAction { Camp.sellSkill(it, i, si) } }) { Text(stringResource(R.string.camp_sell)) }
                    }
                }
                if (editable) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { buyFor = i }) { Text(stringResource(R.string.camp_buy)) }
                        TextButton(onClick = { roleFor = i }) { Text(stringResource(R.string.camp_change_role)) }
                    }
                }
            }
        }
    }
    buyFor?.let { i ->
        var name by remember { mutableStateOf("") }
        var cost by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { buyFor = null },
            title = { Text(stringResource(R.string.camp_buy_title, progress.party[i].name)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.camp_skill_name)) }, singleLine = true)
                    OutlinedTextField(cost, { cost = it.filter(Char::isDigit).take(2) }, label = { Text(stringResource(R.string.camp_skill_cost)) }, singleLine = true)
                }
            },
            confirmButton = {
                Button(enabled = cost.isNotEmpty(), onClick = { onAction { Camp.buySkill(it, i, name.trim(), cost.toInt()) }; buyFor = null }) { Text(stringResource(R.string.camp_buy)) }
            },
            dismissButton = { TextButton(onClick = { buyFor = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    roleFor?.let { i ->
        var role by remember { mutableStateOf(progress.party[i].role) }
        AlertDialog(
            onDismissRequest = { roleFor = null },
            title = { Text(stringResource(R.string.camp_change_role)) },
            text = { OutlinedTextField(role, { role = it }, label = { Text(stringResource(R.string.new_game_hero_role)) }, singleLine = true) },
            confirmButton = { Button(onClick = { onAction { Camp.changeRole(it, i, role) }; roleFor = null }) { Text(stringResource(R.string.ok)) } },
            dismissButton = { TextButton(onClick = { roleFor = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Suppress("unused")
private fun Hero.label() = name
