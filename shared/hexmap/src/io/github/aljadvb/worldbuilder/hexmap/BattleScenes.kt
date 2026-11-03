package io.github.aljadvb.worldbuilder.hexmap

import androidx.compose.ui.graphics.ImageBitmap
import io.github.aljadvb.worldbuilder.engine.GameState
import io.github.aljadvb.worldbuilder.model.BattleMap
import io.github.aljadvb.worldbuilder.model.BoardPlacement
import io.github.aljadvb.worldbuilder.model.CustomRule
import io.github.aljadvb.worldbuilder.model.MaterialCatalog
import io.github.aljadvb.worldbuilder.model.Scenario
import io.github.aljadvb.worldbuilder.model.SquareLocation
import io.github.aljadvb.worldbuilder.model.TerrainPlacement
import io.github.aljadvb.worldbuilder.model.TileSide

/** True if the most recently unlocked chapter is a battle: its map replaces the travel map. */
fun Scenario.showsBattle(state: GameState): Boolean =
    state.unlockedChapters.lastOrNull()?.let { chapter(it)?.isBattle } == true

/** A placed board as drawn; unknown boards get a 3 × 3 placeholder. */
fun battleBoardView(
    b: BoardPlacement,
    catalog: MaterialCatalog,
    images: TileImageCache?,
    fill: androidx.compose.ui.graphics.Color = MapColors.chapterFill(0),
    emphasized: Boolean = false,
    alpha: Float = 1f,
): BattleBoardView? {
    val board = catalog.battleBoard(b.board) ?: return null
    val side = board.sides[b.side] ?: return null
    val origin = BattleMap.boardOrigin(b, side)
    val fields = side.fields().map { f -> f.map { BattleMap.rotateSquare(it, side, b.rotation) + origin }.toSet() }
    val picture = side.image?.let { meta ->
        images?.bitmap(meta.file)?.let { BoardPicture(it, meta.originX.toFloat(), meta.originY.toFloat(), meta.cellSize.toFloat()) }
    }
    return BattleBoardView(b.id, "${board.label} ${b.side}", origin, side.width, side.height, Math.floorMod(b.rotation, 4), fields, picture, fill, emphasized, alpha)
}

/** A placed terrain piece as drawn. */
fun battlePieceView(
    t: TerrainPlacement,
    catalog: MaterialCatalog,
    images: TileImageCache?,
    map: BattleMap,
    emphasized: Boolean = false,
    selected: Boolean = false,
    alpha: Float = 1f,
): BattlePieceView {
    val side = catalog.terrainPiece(t.piece)?.sides?.get(t.side)
    val picture: ImageBitmap? = side?.image?.let { images?.bitmap(it.file) }
    // A long picture in a tall field is turned upright.
    val field = t.area?.let { map.field(it) }
    val tallField = field != null && field.squares.map { it.y }.toSet().size > field.squares.map { it.x }.toSet().size
    val widePicture = picture == null || picture.width > picture.height * 1.2
    return BattlePieceView(
        id = t.id,
        name = t.customName ?: side?.name ?: t.piece,
        square = t.area,
        edge = t.edge,
        picture = picture,
        widthMm = side?.image?.widthMm,
        upright = tallField && widePicture,
        special = t.customName != null || t.customRule != null,
        emphasized = emphasized,
        selected = selected,
        alpha = alpha,
    )
}

/** The battle map of everything unlocked in [state] (app and editor playtest). */
fun battlePlayScene(
    scenario: Scenario,
    catalog: MaterialCatalog,
    state: GameState,
    highlight: MapHighlight,
    tokenLetters: Map<String, String>,
    images: TileImageCache? = null,
): BattleScene {
    val unlocked = state.unlockedChapters.mapNotNull { scenario.chapter(it) }.filter { it.isBattle }
    // Only the current layout lies on the table: everything before the last clearing chapter is gone.
    val chapters = unlocked.drop(unlocked.indexOfLast { it.clearBattlefield }.coerceAtLeast(0))
    val boards = chapters.flatMap { it.battlefield!!.boards }
    val map = BattleMap(boards, catalog)
    val boardViews = boards.mapNotNull { battleBoardView(it, catalog, images, emphasized = it.id in highlight.placements) }
    val pieces = chapters.flatMap { it.battlefield!!.terrain }.map { battlePieceView(it, catalog, images, map, emphasized = it.id in highlight.placements) }
    val tokens = state.activeTokens.mapNotNull { id ->
        val t = scenario.token(id) ?: return@mapNotNull null
        (t.location as? SquareLocation)?.let { SquareToken(t.id, it.square, t.type, tokenLetters[t.id].orEmpty()) }
    }
    val enemies = state.activeEnemyGroups.mapNotNull { id ->
        val g = scenario.enemyGroup(id) ?: return@mapNotNull null
        val alive = state.enemyHealth[g.id]?.count { it > 0 } ?: (g.count[state.heroCount] ?: 0)
        (g.location as? SquareLocation)?.let { SquareEnemy(g.id, it.square, alive, elite = g.elite, banner = state.enemyBanners[g.id]) }
    }
    val cellMm = boards.firstNotNullOfOrNull { catalog.battleBoard(it.board)?.sides?.get(it.side)?.cellSizeMm } ?: 95.0
    val start = if (state.round == 1) (scenario.effectiveHeroStart as? SquareLocation)?.square else null
    val markers = state.fieldMarkers.mapNotNull { m -> (m.location as? SquareLocation)?.let { it.square to m.marker } }
    return BattleScene(boardViews, pieces, tokens, enemies, cellMm, highlightSquares = highlight.squares, heroStart = start, markers = markers)
}

/** What the players see when they tap a terrain piece. */
data class TerrainInfo(
    /** Name in this scenario (custom name or the card name). */
    val title: String,
    /** The card name, if [title] is a custom name. */
    val pieceName: String?,
    val cardText: String,
    /** Short own-words effect from the catalog (shown if there is no card text). */
    val effect: String,
    val customRule: CustomRule?,
    val imageFile: String?,
)

fun Scenario.terrainInfo(id: String, catalog: MaterialCatalog): TerrainInfo? {
    val t = terrainPlacement(id) ?: return null
    val side = catalog.terrainPiece(t.piece)?.sides?.get(t.side)
    val name = side?.name ?: t.piece
    return TerrainInfo(
        title = t.customName ?: name,
        pieceName = name.takeIf { t.customName != null },
        cardText = side?.cardText.orEmpty(),
        effect = side?.effect.orEmpty(),
        customRule = t.customRule,
        imageFile = side?.image?.file,
    )
}

/** Setup lines for a battle chapter: boards with side, and terrain pieces grouped by name. */
data class BattleSetup(val boards: List<Pair<String, TileSide>>, val pieces: List<Pair<String, Int>>, val clearFirst: Boolean = false)

fun Scenario.battleSetup(chapterId: String, catalog: MaterialCatalog): BattleSetup? {
    val bf = chapter(chapterId)?.battlefield ?: return null
    val boards = bf.boards.map { (catalog.battleBoard(it.board)?.label ?: it.board) to it.side }
    val pieces = bf.terrain.groupBy { catalog.terrainPiece(it.piece)?.sides?.get(it.side)?.name ?: it.piece }
        .map { (name, list) -> name to list.size }
    return BattleSetup(boards, pieces, chapter(chapterId)?.clearBattlefield == true)
}
