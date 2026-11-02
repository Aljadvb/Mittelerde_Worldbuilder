package io.github.aljadvb.worldbuilder.editor.map

import io.github.aljadvb.worldbuilder.hexmap.mapCell
import io.github.aljadvb.worldbuilder.model.placementId
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.aljadvb.worldbuilder.editing.addEnemyGroup
import io.github.aljadvb.worldbuilder.editing.addPlacement
import io.github.aljadvb.worldbuilder.editing.addToken
import io.github.aljadvb.worldbuilder.editing.delete
import io.github.aljadvb.worldbuilder.editing.flipPlacement
import io.github.aljadvb.worldbuilder.editing.locate
import io.github.aljadvb.worldbuilder.editing.mapCells
import io.github.aljadvb.worldbuilder.editing.moveEnemyGroup
import io.github.aljadvb.worldbuilder.editing.movePlacement
import io.github.aljadvb.worldbuilder.editing.moveToken
import io.github.aljadvb.worldbuilder.editing.overlaps
import io.github.aljadvb.worldbuilder.editing.rotatePlacement
import io.github.aljadvb.worldbuilder.editing.scenarioOf
import io.github.aljadvb.worldbuilder.editor.EditorModel
import io.github.aljadvb.worldbuilder.editor.Texts
import io.github.aljadvb.worldbuilder.model.Chapter
import io.github.aljadvb.worldbuilder.model.HexCoord
import io.github.aljadvb.worldbuilder.model.Scenario
import io.github.aljadvb.worldbuilder.model.TilePlacement
import io.github.aljadvb.worldbuilder.model.TileSide
import io.github.aljadvb.worldbuilder.model.TokenType
import io.github.aljadvb.worldbuilder.validation.ObjectKind

/** Active tool of the map editor. */
sealed interface MapTool {
    data object Select : MapTool
    data class PlaceTile(val tile: String, val side: TileSide, val rotation: Int = 0) : MapTool
    data class PlaceToken(val type: TokenType) : MapTool
    data object PlaceEnemy : MapTool
}

/** What is being dragged with the select tool. */
sealed interface Drag {
    /** [grab] = pressed cell minus the placement's position. */
    data class Tile(val placementId: String, val grab: HexCoord, val position: HexCoord) : Drag
    data class Token(val tokenId: String, val cell: HexCoord) : Drag
    data class Enemy(val groupId: String, val cell: HexCoord) : Drag
    /** Pressed on empty space: the view pans (handled in pixels by the UI). */
    data object Pan : Drag
}

/**
 * Map editing logic in hex coordinates (pixel handling stays in the composable),
 * so it can be tested without a window.
 */
class MapController(private val model: EditorModel) {
    /** View transform; kept here so zoom/pan survive switching between objects. */
    val camera = MapCamera()
    var tool: MapTool by mutableStateOf(MapTool.Select)
    var hover: HexCoord? by mutableStateOf(null)
    var drag: Drag? by mutableStateOf(null)
        private set

    val scenario: Scenario? get() = model.selection?.let { sel -> model.campaign?.let { it.scenario(sel) ?: it.scenarioOf(sel) } }

    /** Chapter being edited: derived from the selection, falling back to the start chapter. */
    val chapter: Chapter?
        get() {
            val s = scenario ?: return null
            val sel = model.selection
            val id = when {
                sel == null -> null
                s.chapter(sel) != null -> sel
                s.placement(sel) != null -> s.chapterOfPlacement(sel)?.id
                s.token(sel) != null -> s.chapterOfToken(sel)?.id
                s.enemyGroup(sel) != null -> s.chapterOfPlacement(s.enemyGroup(sel)!!.location.placementId.orEmpty())?.id
                else -> null
            }
            return s.chapter(id ?: s.startChapter) ?: s.chapters.firstOrNull()
        }

    /** Chapters drawn on the map: everything up to the current chapter (in list order). */
    val visibleChapters: List<Chapter>
        get() {
            val s = scenario ?: return emptyList()
            val idx = s.chapters.indexOf(chapter ?: return emptyList())
            return s.chapters.take(idx + 1)
        }

    /** The candidate placement shown under the cursor (new tile or dragged tile). */
    val ghost: TilePlacement?
        get() {
            val t = tool
            val d = drag
            return when {
                t is MapTool.PlaceTile && hover != null -> TilePlacement("pl__ghost", t.tile, t.side, hover!!, t.rotation)
                d is Drag.Tile -> scenario?.placement(d.placementId)?.copy(position = d.position)
                else -> null
            }
        }

    /** Map cells where the ghost would overlap other tiles. */
    val ghostConflicts: Set<HexCoord>
        get() {
            val g = ghost ?: return emptySet()
            val ignore = (drag as? Drag.Tile)?.placementId
            return scenario?.overlaps(g, model.catalog, ignore).orEmpty()
        }

    // ---- pointer -----------------------------------------------------------------

    /** Primary button pressed on [cell]. Returns true if the press was consumed (no panning). */
    fun press(cell: HexCoord): Boolean {
        val s = scenario ?: return false
        val ch = chapter ?: return false
        when (val t = tool) {
            is MapTool.PlaceTile -> {
                val candidate = TilePlacement("pl__ghost", t.tile, t.side, cell, t.rotation)
                if (s.overlaps(candidate, model.catalog).isNotEmpty()) {
                    model.message = Texts.MAP_OVERLAP
                    return true
                }
                create { it.addPlacement(ch.id, t.tile, t.side, cell, t.rotation) }
                tool = MapTool.Select
                return true
            }
            is MapTool.PlaceToken -> {
                val loc = s.locate(cell, model.catalog, visibleChapters.map { it.id }.toSet()) ?: return true.also { model.message = Texts.MAP_NO_TILE }
                create { it.addToken(ch.id, Texts.NEW_TOKEN, t.type, loc) }
                tool = MapTool.Select
                return true
            }
            MapTool.PlaceEnemy -> {
                val loc = s.locate(cell, model.catalog, visibleChapters.map { it.id }.toSet()) ?: return true.also { model.message = Texts.MAP_NO_TILE }
                val type = model.catalog.enemyTypes.firstOrNull()?.id.orEmpty()
                create { it.addEnemyGroup(s.id, Texts.NEW_ENEMY_GROUP, type, loc) }
                tool = MapTool.Select
                return true
            }
            MapTool.Select -> {
                val visible = visibleChapters.map { it.id }.toSet()
                val token = visibleChapters.flatMap { it.tokens }.firstOrNull { s.mapCell(it.location) == cell }
                val enemy = s.enemyGroups.firstOrNull { g -> s.chapterOfPlacement(g.location.placementId.orEmpty())?.id in visible && s.mapCell(g.location) == cell }
                val placement = ch.tiles.firstOrNull { cell in it.mapCells(model.catalog) }
                when {
                    token != null -> { model.selection = token.id; drag = Drag.Token(token.id, cell) }
                    enemy != null -> { model.selection = enemy.id; drag = Drag.Enemy(enemy.id, cell) }
                    placement != null -> { model.selection = placement.id; drag = Drag.Tile(placement.id, cell - placement.position, placement.position) }
                    else -> { drag = Drag.Pan; return false }
                }
                return true
            }
        }
    }

    fun move(cell: HexCoord) {
        hover = cell
        drag = when (val d = drag) {
            is Drag.Tile -> d.copy(position = cell - d.grab)
            is Drag.Token -> d.copy(cell = cell)
            is Drag.Enemy -> d.copy(cell = cell)
            else -> d
        }
    }

    fun release() {
        val s = scenario
        val d = drag
        drag = null
        if (s == null) return
        val visible = visibleChapters.map { it.id }.toSet()
        when (d) {
            is Drag.Tile -> {
                val p = s.placement(d.placementId) ?: return
                if (d.position == p.position) return
                if (s.overlaps(p.copy(position = d.position), model.catalog, ignore = p.id).isNotEmpty()) {
                    model.message = Texts.MAP_OVERLAP
                    return
                }
                model.edit { it.movePlacement(p.id, d.position) }
            }
            is Drag.Token -> {
                val t = s.token(d.tokenId) ?: return
                val loc = s.locate(d.cell, model.catalog, visible) ?: return
                if (loc != t.location) model.edit { it.moveToken(t.id, loc) }
            }
            is Drag.Enemy -> {
                val g = s.enemyGroup(d.groupId) ?: return
                val loc = s.locate(d.cell, model.catalog, visible) ?: return
                if (loc != g.location) model.edit { it.moveEnemyGroup(g.id, loc) }
            }
            Drag.Pan, null -> Unit
        }
    }

    // ---- keyboard ----------------------------------------------------------------

    /** R: rotate the new tile or the selected placement by 60°. */
    fun rotate(steps: Int = 1) {
        val t = tool
        if (t is MapTool.PlaceTile) { tool = t.copy(rotation = Math.floorMod(t.rotation + steps, 6)); return }
        val p = selectedPlacement() ?: return
        val s = scenario ?: return
        if (s.overlaps(p.copy(rotation = Math.floorMod(p.rotation + steps, 6)), model.catalog, ignore = p.id).isNotEmpty()) {
            model.message = Texts.MAP_OVERLAP
            return
        }
        model.edit { it.rotatePlacement(p.id, steps) }
    }

    /** F: turn the new tile or the selected placement over. */
    fun flip() {
        val t = tool
        if (t is MapTool.PlaceTile) {
            val other = if (t.side == TileSide.A) TileSide.B else TileSide.A
            if (model.catalog.tile(t.tile)?.sides?.containsKey(other) == true) tool = t.copy(side = other)
            return
        }
        val p = selectedPlacement() ?: return
        var conflicts = emptyList<String>()
        if (model.edit { c -> c.flipPlacement(p.id, model.catalog).also { conflicts = it.conflicts }.campaign } && conflicts.isNotEmpty()) {
            model.message = Texts.flipConflicts(conflicts)
        }
    }

    /** Entf: delete the selected placement, token or enemy group. */
    fun deleteSelection() {
        val sel = model.selection ?: return
        val kind = model.definitions.firstOrNull { it.id == sel }?.kind ?: return
        if (kind !in setOf(ObjectKind.PLACEMENT, ObjectKind.TOKEN, ObjectKind.ENEMY_GROUP)) return
        val parent = chapter?.id
        if (model.edit { it.delete(sel) }) model.selection = parent
    }

    /** Esc: back to the select tool. */
    fun cancel() {
        tool = MapTool.Select
        drag = null
    }

    private fun selectedPlacement(): TilePlacement? = model.selection?.let { scenario?.placement(it) }

    private fun create(f: (io.github.aljadvb.worldbuilder.model.Campaign) -> io.github.aljadvb.worldbuilder.editing.Created) {
        var id: String? = null
        if (model.edit { c -> f(c).also { id = it.id }.campaign }) model.selection = id
    }
}
