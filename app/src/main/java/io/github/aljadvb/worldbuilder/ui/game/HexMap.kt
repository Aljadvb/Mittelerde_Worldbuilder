package io.github.aljadvb.worldbuilder.ui.game

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import io.github.aljadvb.worldbuilder.engine.GameState
import io.github.aljadvb.worldbuilder.hexmap.HexCanvas
import io.github.aljadvb.worldbuilder.hexmap.HexProjection
import io.github.aljadvb.worldbuilder.hexmap.MapHighlight
import io.github.aljadvb.worldbuilder.hexmap.TileImageCache
import io.github.aljadvb.worldbuilder.hexmap.playScene
import io.github.aljadvb.worldbuilder.hexmap.tokenAt
import io.github.aljadvb.worldbuilder.model.MaterialCatalog
import io.github.aljadvb.worldbuilder.model.Scenario

/** Player map: tiles of unlocked chapters, tap a token to interact. */
@Composable
fun HexMap(
    scenario: Scenario,
    catalog: MaterialCatalog,
    state: GameState,
    highlight: MapHighlight,
    tokenLetters: Map<String, String>,
    tapEnabled: Boolean,
    onTokenTap: (String) -> Unit,
    modifier: Modifier = Modifier,
    onExploreTap: (String) -> Unit = {},
    images: TileImageCache? = null,
    onEnemyTap: (String) -> Unit = {},
) {
    val scene = playScene(scenario, catalog, state, highlight, tokenLetters, images)
    val projection = remember { arrayOfNulls<HexProjection>(1) }
    HexCanvas(
        scene = scene,
        highlight = MaterialTheme.colorScheme.primary,
        modifier = modifier.pointerInput(tapEnabled, scene.tokens) {
            if (!tapEnabled) return@pointerInput
            detectTapGestures { pos ->
                val proj = projection[0] ?: return@detectTapGestures
                val token = tokenAt(proj, scene.tokens, pos)
                val enemy = io.github.aljadvb.worldbuilder.hexmap.enemyAt(proj, scene.enemies, pos)
                if (enemy != null) onEnemyTap(enemy.id)
                else if (token != null) onTokenTap(token.id)
                else io.github.aljadvb.worldbuilder.hexmap.exploreMarkerAt(proj, scene.tiles, pos)?.let { onExploreTap(it.id) }
            }
        },
        onProjection = { projection[0] = it },
    )
}

/** Tile pictures from the imported catalog folder, decoded with Android's BitmapFactory. */
@Composable
fun rememberTileImages(dir: java.io.File?): TileImageCache = remember(dir) {
    TileImageCache { name ->
        dir?.let { android.graphics.BitmapFactory.decodeFile(java.io.File(it, name).path) }?.asImageBitmap()
    }
}
