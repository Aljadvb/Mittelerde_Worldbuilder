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
import io.github.aljadvb.worldbuilder.hexmap.MapColors
import io.github.aljadvb.worldbuilder.hexmap.MapEnemy
import io.github.aljadvb.worldbuilder.hexmap.MapScene
import io.github.aljadvb.worldbuilder.hexmap.MapTile
import io.github.aljadvb.worldbuilder.hexmap.MapToken
import io.github.aljadvb.worldbuilder.hexmap.tokenAt
import io.github.aljadvb.worldbuilder.model.HexCoord
import io.github.aljadvb.worldbuilder.model.MaterialCatalog
import io.github.aljadvb.worldbuilder.model.Scenario
import io.github.aljadvb.worldbuilder.model.TileLocation

/** What the map should emphasise for the current prompt. */
data class MapHighlight(
    val placements: Set<String> = emptySet(),
    val cells: Set<HexCoord> = emptySet(),
)

/** Map cell of a tile-local location, or null if the placement is unknown. */
fun Scenario.mapCell(location: TileLocation): HexCoord? = placement(location.placement)?.toMap(location.cell)

/** Builds the map of everything unlocked in [state]. */
fun playScene(scenario: Scenario, catalog: MaterialCatalog, state: GameState, highlight: MapHighlight, tokenLetters: Map<String, String>): MapScene {
    val tiles = state.unlockedChapters.flatMap { chapterId ->
        val chapterIndex = scenario.chapters.indexOfFirst { it.id == chapterId }
        scenario.chapter(chapterId)?.tiles.orEmpty().map { p ->
            val shape = catalog.tile(p.tile)?.sides?.get(p.side)?.cells.orEmpty()
            MapTile(
                id = p.id,
                label = "${catalog.tile(p.tile)?.label ?: p.tile} ${p.side}",
                cells = shape.map(p::toMap),
                fill = MapColors.chapterFill(chapterIndex),
                emphasized = p.id in highlight.placements,
            )
        }
    }
    val tokens = state.activeTokens.mapNotNull { id ->
        val t = scenario.token(id) ?: return@mapNotNull null
        scenario.mapCell(t.location)?.let { MapToken(t.id, it, t.type, tokenLetters[t.id].orEmpty()) }
    }
    val enemies = state.activeEnemyGroups.mapNotNull { id ->
        val g = scenario.enemyGroup(id) ?: return@mapNotNull null
        scenario.mapCell(g.location)?.let { MapEnemy(g.id, it, g.count[state.heroCount] ?: 0) }
    }
    return MapScene(catalog.hexOrientation, tiles, tokens, enemies, highlightCells = highlight.cells)
}

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
