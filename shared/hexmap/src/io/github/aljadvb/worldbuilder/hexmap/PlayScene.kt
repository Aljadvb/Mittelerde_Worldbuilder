package io.github.aljadvb.worldbuilder.hexmap

import io.github.aljadvb.worldbuilder.engine.GameState
import io.github.aljadvb.worldbuilder.engine.Prompt
import io.github.aljadvb.worldbuilder.model.GridCoord
import io.github.aljadvb.worldbuilder.model.HexCoord
import io.github.aljadvb.worldbuilder.model.SquareLocation
import io.github.aljadvb.worldbuilder.model.MaterialCatalog
import io.github.aljadvb.worldbuilder.model.Scenario
import io.github.aljadvb.worldbuilder.model.Location
import io.github.aljadvb.worldbuilder.model.TileLocation

/** What the play map should emphasise for the current prompt. */
data class MapHighlight(
    /** IDs of tile placements, board placements or terrain pieces. */
    val placements: Set<String> = emptySet(),
    val cells: Set<HexCoord> = emptySet(),
    /** Battle map squares. */
    val squares: Set<GridCoord> = emptySet(),
)

/** Map cell of a tile-local location, or null if the placement is unknown or it is a battle-map location. */
fun Scenario.mapCell(location: Location): HexCoord? =
    (location as? TileLocation)?.let { placement(it.placement)?.toMap(it.cell) }

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
                unexplored = p.unexplored && p.id !in state.exploredPlacements,
            )
        }
    }
    val tokens = state.activeTokens.mapNotNull { id ->
        val t = scenario.token(id) ?: return@mapNotNull null
        scenario.mapCell(t.location)?.let { MapToken(t.id, it, t.type, tokenLetters[t.id].orEmpty()) }
    }
    val enemies = state.activeEnemyGroups.mapNotNull { id ->
        val g = scenario.enemyGroup(id) ?: return@mapNotNull null
        val alive = state.enemyHealth[g.id]?.count { it > 0 } ?: (g.count[state.heroCount] ?: 0)
        scenario.mapCell(g.location)?.let { MapEnemy(g.id, it, alive, banner = state.enemyBanners[g.id], elite = g.elite) }
    }
    // The start field is shown during the first round, while the heroes set up.
    val start = if (state.round == 1) scenario.effectiveHeroStart?.let { scenario.mapCell(it) } else null
    val markers = state.fieldMarkers.mapNotNull { m -> scenario.mapCell(m.location)?.let { it to m.marker } }
    return MapScene(catalog.hexOrientation, tiles, tokens, enemies, highlightCells = highlight.cells, heroStart = start, markers = markers)
}

/** What to emphasise on the map for a prompt. */
fun highlightFor(prompt: Prompt?, scenario: Scenario): MapHighlight = when (prompt) {
    is Prompt.Setup -> scenario.chapter(prompt.chapterId)?.let { ch ->
        MapHighlight(
            placements = (ch.tiles.map { it.id } + ch.battlefield?.boards.orEmpty().map { it.id } + ch.battlefield?.terrain.orEmpty().map { it.id }).toSet(),
            cells = ch.tokens.filter { it.initiallyActive }.mapNotNull { scenario.mapCell(it.location) }.toSet(),
            squares = ch.tokens.filter { it.initiallyActive }.mapNotNull { (it.location as? SquareLocation)?.square }.toSet(),
        )
    } ?: MapHighlight()
    is Prompt.PlaceToken -> tokenHighlight(scenario, prompt.tokenId)
    is Prompt.Explored -> MapHighlight(
        placements = setOf(prompt.placementId),
        cells = prompt.tokenIds.mapNotNull { scenario.token(it)?.let { t -> scenario.mapCell(t.location) } }.toSet(),
    )
    is Prompt.RemoveToken -> tokenHighlight(scenario, prompt.tokenId)
    is Prompt.Inspect -> tokenHighlight(scenario, prompt.tokenId)
    is Prompt.SpawnEnemies -> scenario.enemyGroup(prompt.groupId)?.let { g ->
        MapHighlight(cells = setOfNotNull(scenario.mapCell(g.location)), squares = setOfNotNull((g.location as? SquareLocation)?.square))
    } ?: MapHighlight()
    else -> MapHighlight()
}

private fun tokenHighlight(scenario: Scenario, tokenId: String): MapHighlight {
    val location = scenario.token(tokenId)?.location ?: return MapHighlight()
    return MapHighlight(cells = setOfNotNull(scenario.mapCell(location)), squares = setOfNotNull((location as? SquareLocation)?.square))
}
