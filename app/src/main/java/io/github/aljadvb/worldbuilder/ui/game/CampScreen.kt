package io.github.aljadvb.worldbuilder.ui.game

import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.Place
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
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

/**
 * Camp between two scenarios, laid out as a scene: the camp picture fills the screen, the heroes
 * stand side by side at the top, lore and inventory at the bottom left, "continue" at the bottom
 * right. Details (hero skills, inventory, campaign map) open in dialogs.
 */
@Composable
fun CampContent(campaign: Campaign, progress: CampaignProgress, background: androidx.compose.ui.graphics.ImageBitmap? = null, catalog: io.github.aljadvb.worldbuilder.model.MaterialCatalog? = null, onAction: ((CampaignProgress) -> CampaignProgress) -> Unit) {
    val last = progress.completed.lastOrNull()
    var heroOpen by remember { mutableStateOf<Int?>(null) }
    var inventoryOpen by remember { mutableStateOf(false) }
    var mapOpen by remember { mutableStateOf(false) }
    val panel = androidx.compose.ui.graphics.Color(0xE61E1A14)
    val gold = androidx.compose.ui.graphics.Color(0xFFD9B865)
    val light = androidx.compose.ui.graphics.Color(0xFFF3E9D2)
    val onUpgrade: (String, String) -> Unit = { id, target -> onAction { p -> Camp.upgradeItem(p, id, catalog?.items.orEmpty(), target) } }
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        // Camp picture (created by the user) behind everything, slightly darkened.
        androidx.compose.foundation.Image(
            androidx.compose.ui.res.painterResource(R.drawable.camp_background), contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize(),
        )
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.2f)))

        // Top left: campaign map.
        androidx.compose.material3.FilledTonalIconButton(
            onClick = { mapOpen = true }, modifier = Modifier.align(Alignment.TopStart).padding(12.dp).size(52.dp),
            colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(containerColor = panel, contentColor = gold),
        ) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.Place, stringResource(R.string.camp_map)) }

        Column(Modifier.align(Alignment.TopCenter).padding(top = 12.dp, start = 76.dp, end = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // The heroes side by side.
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                progress.party.forEachIndexed { i, h -> HeroCard(h, panel, gold, light) { heroOpen = i } }
            }
            // What just happened.
            last?.let { c ->
                val sc = campaign.scenario(c.scenarioId)
                androidx.compose.material3.Surface(color = panel, shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.camp_last, sc?.title ?: c.scenarioId, sc?.resolution(c.resolutionId)?.title ?: c.resolutionId), color = light, style = MaterialTheme.typography.bodyMedium)
                        if (c.lore > 0 || c.xp > 0) Text(stringResource(R.string.camp_rewards, c.lore, c.xp), color = gold, fontWeight = FontWeight.Bold)
                        c.items.mapNotNull { catalog?.item(it)?.name }.takeIf { it.isNotEmpty() }?.let { Text(stringResource(R.string.camp_found_items, it.joinToString(", ")), color = gold) }
                    }
                }
            }
            if (progress.nextOptions.size > 1) {
                androidx.compose.material3.Surface(color = panel, shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.camp_choose_next), color = light, fontWeight = FontWeight.Bold)
                        progress.nextOptions.forEach { id ->
                            androidx.compose.material3.FilterChip(id == progress.currentScenario, { onAction { p -> Camp.chooseNext(p, id) } },
                                label = { Text(campaign.scenario(id)?.title ?: id, color = light) })
                        }
                    }
                }
            }
        }

        // Bottom left: lore and inventory.
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.Surface(color = panel, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, gold)) {
                Text(stringResource(R.string.camp_lore_short, progress.lore), color = light, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
            }
            val upgradable = Camp.upgradable(progress, catalog?.items.orEmpty()).isNotEmpty()
            Button(onClick = { inventoryOpen = true }, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = panel, contentColor = gold), border = androidx.compose.foundation.BorderStroke(1.dp, gold)) {
                Text(stringResource(R.string.camp_inventory_button, progress.inventory.size) + if (upgradable) " ★" else "")
            }
        }

        // Bottom right: on to the next adventure.
        val next = progress.currentScenario?.let { campaign.scenario(it) }
        Button(
            onClick = { onAction(Camp::leave) }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF7A3B1E), contentColor = light),
            border = androidx.compose.foundation.BorderStroke(1.dp, gold),
        ) { Text(stringResource(R.string.camp_continue, next?.title.orEmpty()), fontWeight = FontWeight.Bold) }
    }

    heroOpen?.let { i ->
        AlertDialog(
            onDismissRequest = { heroOpen = null },
            confirmButton = { TextButton(onClick = { heroOpen = null }) { Text(stringResource(R.string.close)) } },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) { HeroesSection(progress, editable = true, onAction = onAction, only = i, roles = catalog?.roles.orEmpty(), heroDefs = catalog?.heroes.orEmpty()) } },
        )
    }
    if (inventoryOpen) AlertDialog(
        onDismissRequest = { inventoryOpen = false },
        confirmButton = { TextButton(onClick = { inventoryOpen = false }) { Text(stringResource(R.string.close)) } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.camp_lore_hint, progress.lore), style = MaterialTheme.typography.bodySmall)
                InventorySection(progress, catalog, onUpgrade)
            }
        },
    )
    if (mapOpen) AlertDialog(
        onDismissRequest = { mapOpen = false },
        confirmButton = { TextButton(onClick = { mapOpen = false }) { Text(stringResource(R.string.close)) } },
        title = { Text(campaign.title) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) { CampaignMapSection(campaign, progress, background) } },
    )
}

/** A hero standing in camp: name, role and experience available in the current role. */
@Composable
private fun HeroCard(hero: Hero, panel: androidx.compose.ui.graphics.Color, gold: androidx.compose.ui.graphics.Color, light: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    androidx.compose.material3.Surface(
        onClick = onClick, color = panel, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, gold), modifier = Modifier.width(118.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(hero.name, color = light, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Color(0xFF3A2E22)).padding(vertical = 4.dp, horizontal = 6.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            androidx.compose.foundation.layout.Box(
                Modifier.padding(top = 10.dp).size(64.dp).background(gold.copy(alpha = 0.25f), androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text(hero.name.take(1).uppercase(), color = gold, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
            Text(hero.role.ifBlank { "–" }, color = light, style = MaterialTheme.typography.bodySmall, maxLines = 1, modifier = Modifier.padding(top = 6.dp, start = 6.dp, end = 6.dp))
            Row(Modifier.fillMaxWidth().padding(8.dp).background(light, androidx.compose.foundation.shape.RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.camp_xp_label), color = androidx.compose.ui.graphics.Color(0xFF3A2E22), fontWeight = FontWeight.Bold)
                Text("${hero.xpFor(hero.role).available}", color = androidx.compose.ui.graphics.Color(0xFF3A2E22), fontWeight = FontWeight.Bold)
            }
            if (hero.corruption > 0) {
                Text(stringResource(R.string.camp_corruption, hero.corruption), color = androidx.compose.ui.graphics.Color(0xFFE57373),
                    style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
            }
        }
    }
}

/** The group's trinkets with level and possible upgrades. */
@Composable
fun InventorySection(progress: CampaignProgress, catalog: io.github.aljadvb.worldbuilder.model.MaterialCatalog?, onUpgrade: ((String, String) -> Unit)?) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.camp_inventory), style = MaterialTheme.typography.titleMedium)
            if (progress.inventory.isEmpty()) Text(stringResource(R.string.camp_inventory_empty), style = MaterialTheme.typography.bodySmall)
            val items = catalog?.items.orEmpty()
            progress.inventory.forEach { id ->
                val item = catalog?.item(id)
                val roman = item?.let { listOf("I", "II", "III").getOrNull(it.level - 1) }
                Text("• " + (item?.name ?: id) + (roman?.let { " ($it)" } ?: ""), fontWeight = FontWeight.Bold)
                item?.effect?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                val options = item?.upgrades(items).orEmpty()
                if (item != null && options.isNotEmpty()) {
                    val needed = item.lore ?: 0
                    options.forEach { next ->
                        if (onUpgrade != null && progress.lore >= needed) {
                            androidx.compose.material3.OutlinedButton(onClick = { onUpgrade(id, next.id) }) { Text(stringResource(R.string.camp_upgrade, next.name)) }
                        } else {
                            Text(stringResource(R.string.camp_upgrade_later, next.name, needed), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

/** The campaign map with the reached places; tap a place for what happened there. */
@Composable
fun CampaignMapSection(campaign: Campaign, progress: CampaignProgress, background: androidx.compose.ui.graphics.ImageBitmap?) {
    var selected by remember { mutableStateOf<String?>(null) }
    if (campaign.places.isNotEmpty()) {
        val scene = campaignMapScene(campaign, progress.completed.map { it.scenarioId to it.resolutionId }, progress.currentScenario, selected, background, progress.nextOptions)
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
}

/** Map, lore and the list of played scenarios. Shared by camp, campaign end and the in-game overview. */
@Composable
fun CampaignOverview(campaign: Campaign, progress: CampaignProgress, background: androidx.compose.ui.graphics.ImageBitmap? = null, catalog: io.github.aljadvb.worldbuilder.model.MaterialCatalog? = null, onUpgrade: ((String, String) -> Unit)? = null) {
    CampaignMapSection(campaign, progress, background)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.camp_lore, progress.lore), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.camp_lore_hint, progress.lore), style = MaterialTheme.typography.bodySmall)
        }
    }
    InventorySection(progress, catalog, onUpgrade)
}

@Composable
fun HeroesSection(progress: CampaignProgress, editable: Boolean, onAction: ((CampaignProgress) -> CampaignProgress) -> Unit = {}, onImage: Boolean = false, only: Int? = null, roles: List<io.github.aljadvb.worldbuilder.model.RoleDefinition> = emptyList(), heroDefs: List<io.github.aljadvb.worldbuilder.model.HeroDefinition> = emptyList()) {
    val statsLabel = io.github.aljadvb.worldbuilder.ui.home.heroStatsShort()
    var buyFor by remember { mutableStateOf<Int?>(null) }
    var roleFor by remember { mutableStateOf<Int?>(null) }
    if (only == null) Text(stringResource(R.string.camp_heroes), style = MaterialTheme.typography.titleMedium,
        color = if (onImage) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Unspecified, fontWeight = if (onImage) FontWeight.Bold else null)
    progress.party.forEachIndexed { i, h ->
        if (only != null && i != only) return@forEachIndexed
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(h.name + if (h.role.isNotBlank()) " – ${h.role}" else "", fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.camp_xp_current, h.xpFor(h.role).available, h.xpFor(h.role).earned), style = MaterialTheme.typography.bodyMedium)
                if (h.corruption > 0) Text(stringResource(R.string.camp_corruption, h.corruption), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                heroDefs.firstOrNull { it.name == h.name }?.let { d ->
                    d.stats?.let { Text(statsLabel(it), style = MaterialTheme.typography.bodySmall) }
                    if (d.ability.isNotBlank()) Text(stringResource(R.string.hero_ability, d.ability), style = MaterialTheme.typography.bodySmall)
                    if (d.startingGear.isNotEmpty()) Text(stringResource(R.string.hero_gear, d.startingGear.joinToString(", ")), style = MaterialTheme.typography.bodySmall)
                }
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
            text = {
                if (roles.isNotEmpty()) io.github.aljadvb.worldbuilder.ui.home.CatalogPicker(role, roles.map { it.name to it.description }, Modifier.fillMaxWidth(), stringResource(R.string.new_game_hero_role)) { role = it }
                else OutlinedTextField(role, { role = it }, label = { Text(stringResource(R.string.new_game_hero_role)) }, singleLine = true)
            },
            confirmButton = { Button(onClick = { onAction { Camp.changeRole(it, i, role) }; roleFor = null }) { Text(stringResource(R.string.ok)) } },
            dismissButton = { TextButton(onClick = { roleFor = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Suppress("unused")
private fun Hero.label() = name
