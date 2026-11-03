package io.github.aljadvb.worldbuilder.ui.game

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.aljadvb.worldbuilder.R
import io.github.aljadvb.worldbuilder.engine.GameState
import io.github.aljadvb.worldbuilder.hexmap.BattleCanvas
import io.github.aljadvb.worldbuilder.hexmap.BattleProjection
import io.github.aljadvb.worldbuilder.hexmap.MapHighlight
import io.github.aljadvb.worldbuilder.hexmap.TerrainInfo
import io.github.aljadvb.worldbuilder.hexmap.TileImageCache
import io.github.aljadvb.worldbuilder.hexmap.battlePlayScene
import io.github.aljadvb.worldbuilder.hexmap.pieceAt
import io.github.aljadvb.worldbuilder.hexmap.squareTokenAt
import io.github.aljadvb.worldbuilder.model.MaterialCatalog
import io.github.aljadvb.worldbuilder.model.RuleMode
import io.github.aljadvb.worldbuilder.model.Scenario

/** Player battle map: tap a token to interact, tap a terrain piece to read its rules. */
@Composable
fun BattleMapView(
    scenario: Scenario,
    catalog: MaterialCatalog,
    state: GameState,
    highlight: MapHighlight,
    tokenLetters: Map<String, String>,
    tapEnabled: Boolean,
    onTokenTap: (String) -> Unit,
    onPieceTap: (String) -> Unit,
    modifier: Modifier = Modifier,
    images: TileImageCache? = null,
    onEnemyTap: (String) -> Unit = {},
) {
    val scene = battlePlayScene(scenario, catalog, state, highlight, tokenLetters, images)
    val projection = remember { arrayOfNulls<BattleProjection>(1) }
    ZoomableMap(modifier) { zoom -> BattleCanvas(
        scene = scene,
        highlight = MaterialTheme.colorScheme.primary,
        modifier = zoom.pointerInput(tapEnabled, scene) {
            detectTapGestures { pos ->
                val proj = projection[0] ?: return@detectTapGestures
                val token = if (tapEnabled) squareTokenAt(proj, scene.tokens, pos) else null
                val enemy = if (tapEnabled) io.github.aljadvb.worldbuilder.hexmap.squareEnemyAt(proj, scene.enemies, pos) else null
                when {
                    enemy != null -> onEnemyTap(enemy.id)
                    token != null -> onTokenTap(token.id)
                    else -> pieceAt(proj, scene, pos)?.let { onPieceTap(it.id) }
                }
            }
        },
        onProjection = { projection[0] = it },
    ) }
}

/** Name, picture, scenario rule and card text of a terrain piece. */
@Composable
fun TerrainDialog(info: TerrainInfo, images: TileImageCache?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        title = { Text(info.title) },
        text = { TerrainDetails(info, images, Modifier.verticalScroll(rememberScrollState())) },
    )
}

@Composable
fun TerrainDetails(info: TerrainInfo, images: TileImageCache?, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        info.pieceName?.let { Text(stringResource(R.string.terrain_piece_name, it), style = MaterialTheme.typography.labelLarge) }
        info.imageFile?.let { images?.bitmap(it) }?.let {
            Image(it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().heightIn(max = 140.dp))
        }
        val rule = info.customRule
        if (rule != null) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(if (rule.mode == RuleMode.REPLACE) R.string.terrain_scenario_rule_replace else R.string.terrain_scenario_rule_add),
                        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(rule.text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        val ignored = rule?.mode == RuleMode.REPLACE
        val cardText = info.cardText.ifBlank { info.effect }
        Text(stringResource(if (ignored) R.string.terrain_card_ignored else R.string.terrain_card), style = MaterialTheme.typography.labelMedium)
        Text(
            cardText.ifBlank { stringResource(R.string.terrain_no_text) },
            style = MaterialTheme.typography.bodyMedium,
            color = if (ignored) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
        )
    }
}
