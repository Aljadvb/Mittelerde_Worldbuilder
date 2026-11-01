package io.github.aljadvb.worldbuilder.ui.game

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import io.github.aljadvb.worldbuilder.engine.GameState
import io.github.aljadvb.worldbuilder.hexmap.HexCanvas
import io.github.aljadvb.worldbuilder.hexmap.HexProjection
import io.github.aljadvb.worldbuilder.hexmap.MapHighlight
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
) {
    val scene = playScene(scenario, catalog, state, highlight, tokenLetters)
    val projection = remember { arrayOfNulls<HexProjection>(1) }
    HexCanvas(
        scene = scene,
        highlight = MaterialTheme.colorScheme.primary,
        modifier = modifier.pointerInput(tapEnabled, scene.tokens) {
            if (!tapEnabled) return@pointerInput
            detectTapGestures { pos ->
                val proj = projection[0] ?: return@detectTapGestures
                tokenAt(proj, scene.tokens, pos)?.let { onTokenTap(it.id) }
            }
        },
        onProjection = { projection[0] = it },
    )
}
