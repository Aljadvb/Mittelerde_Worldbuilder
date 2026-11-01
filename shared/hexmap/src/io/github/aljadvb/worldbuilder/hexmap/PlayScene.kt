package io.github.aljadvb.worldbuilder.hexmap

import io.github.aljadvb.worldbuilder.engine.GameState
import io.github.aljadvb.worldbuilder.engine.Prompt
import io.github.aljadvb.worldbuilder.model.HexCoord
import io.github.aljadvb.worldbuilder.model.MaterialCatalog
import io.github.aljadvb.worldbuilder.model.Scenario
import io.github.aljadvb.worldbuilder.model.TileLocation

/** What the play map should emphasise for the current prompt. */
data class MapHighlight(
    val placements: Set<String> = emptySet(),
    val cells: Set<HexCoord> = emptySet(),
)

/** Map cell of a tile-local location, or null if the placement is unknown. */
fun Scenario.mapCell(location: TileLocation): HexCoord? = placement(location.placement)?.toMap(location.cell)

/** The map of everything unlocked in [state] (used by the app and the editor's playtest). */
fun playScene(
    scenario: Scenario,
    catalog: MaterialCatalog,
    state: GameState,
    highlight: MapHighlight,
    tokenLetters: Map<String, String>,
    images: TileImageCache? = null,
): MapScene {
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
                image = images?.forPlacement(p, catalog),
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

/** What to emphasise on the map for a prompt. */
fun highlightFor(prompt: Prompt?, scenario: Scenario): MapHighlight = when (prompt) {
    is Prompt.Setup -> scenario.chapter(prompt.chapterId)?.let { ch ->
        MapHighlight(
            placements = ch.tiles.map { it.id }.toSet(),
            cells = ch.tokens.filter { it.initiallyActive }.mapNotNull { scenario.mapCell(it.location) }.toSet(),
        )
    } ?: MapHighlight()
    is Prompt.PlaceToken -> tokenHighlight(scenario, prompt.tokenId)
    is Prompt.RemoveToken -> tokenHighlight(scenario, prompt.tokenId)
    is Prompt.Inspect -> tokenHighlight(scenario, prompt.tokenId)
    is Prompt.SpawnEnemies -> scenario.enemyGroup(prompt.groupId)?.let { g ->
        MapHighlight(cells = setOfNotNull(scenario.mapCell(g.location)))
    } ?: MapHighlight()
    else -> MapHighlight()
}

private fun tokenHighlight(scenario: Scenario, tokenId: String) =
    MapHighlight(cells = setOfNotNull(scenario.token(tokenId)?.let { scenario.mapCell(it.location) }))
