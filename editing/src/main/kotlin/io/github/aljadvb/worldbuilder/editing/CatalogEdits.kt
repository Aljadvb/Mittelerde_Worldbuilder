package io.github.aljadvb.worldbuilder.editing

import io.github.aljadvb.worldbuilder.model.CURRENT_FORMAT_VERSION
import io.github.aljadvb.worldbuilder.model.EnemyType
import io.github.aljadvb.worldbuilder.model.Expansion
import io.github.aljadvb.worldbuilder.model.HexCoord
import io.github.aljadvb.worldbuilder.model.HexOrientation
import io.github.aljadvb.worldbuilder.model.MaterialCatalog
import io.github.aljadvb.worldbuilder.model.TileDefinition
import io.github.aljadvb.worldbuilder.model.TileSide
import io.github.aljadvb.worldbuilder.model.TileSideShape

/** Edits on the material catalog (tile shapes, enemy types, expansions). */

fun newCatalog(): MaterialCatalog = MaterialCatalog(
    formatVersion = CURRENT_FORMAT_VERSION,
    expansions = listOf(Expansion("grundspiel", "Grundspiel", owned = true)),
)

private val CATALOG_ID = Regex("[^a-z0-9]+")

/** Catalog IDs have no prefix: "Kachel 101" → `kachel_101`. */
fun catalogId(name: String, taken: Set<String>): String {
    val slug = name.lowercase().replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
        .replace(CATALOG_ID, "_").trim('_').ifEmpty { "neu" }
    val base = if (slug.first().isLetter()) slug else "k_$slug"
    return Ids.unique(base, taken)
}

/** Adds a tile whose sides A and B consist of the anchor cell only. */
fun MaterialCatalog.addTile(label: String, expansion: String): Pair<MaterialCatalog, String> {
    val id = catalogId(label, tiles.map { it.id }.toSet())
    val shape = TileSideShape(listOf(HexCoord.ORIGIN))
    val tile = TileDefinition(id, label, expansion, mapOf(TileSide.A to shape, TileSide.B to shape))
    return copy(tiles = tiles + tile) to id
}

fun MaterialCatalog.updateTile(id: String, edit: (TileDefinition) -> TileDefinition): MaterialCatalog {
    if (tiles.none { it.id == id }) throw EditException("Kachel '$id' nicht gefunden.")
    return copy(tiles = tiles.map { if (it.id == id) edit(it) else it })
}

fun MaterialCatalog.removeTile(id: String) = copy(tiles = tiles.filterNot { it.id == id })

/** Adds or removes [cell] on one side. The anchor (0,0) cannot be removed. */
fun MaterialCatalog.toggleCell(tileId: String, side: TileSide, cell: HexCoord): MaterialCatalog = updateTile(tileId) { tile ->
    val cells = tile.sides[side]?.cells.orEmpty()
    val updated = when {
        cell == HexCoord.ORIGIN -> cells
        cell in cells -> cells - cell
        else -> cells + cell
    }
    tile.copy(sides = tile.sides + (side to TileSideShape(updated.ifEmpty { listOf(HexCoord.ORIGIN) })))
}

/**
 * Mirror image of a cell across the vertical axis through the anchor – what happens
 * when a tile is turned over left-to-right.
 */
fun HexCoord.mirrored(orientation: HexOrientation): HexCoord = when (orientation) {
    HexOrientation.POINTY -> HexCoord(-q - r, r)
    HexOrientation.FLAT -> HexCoord(-q, r + q)
}

/** Sets side B to the mirror image of side A. */
fun MaterialCatalog.mirrorAToB(tileId: String): MaterialCatalog = updateTile(tileId) { tile ->
    val a = tile.sides[TileSide.A]?.cells.orEmpty()
    tile.copy(sides = tile.sides + (TileSide.B to TileSideShape(a.map { it.mirrored(hexOrientation) })))
}

fun MaterialCatalog.addEnemyType(name: String, expansion: String): Pair<MaterialCatalog, String> {
    val id = catalogId(name, enemyTypes.map { it.id }.toSet())
    return copy(enemyTypes = enemyTypes + EnemyType(id, name, expansion)) to id
}

fun MaterialCatalog.updateEnemyType(id: String, edit: (EnemyType) -> EnemyType) =
    copy(enemyTypes = enemyTypes.map { if (it.id == id) edit(it) else it })

fun MaterialCatalog.removeEnemyType(id: String) = copy(enemyTypes = enemyTypes.filterNot { it.id == id })

fun MaterialCatalog.addExpansion(name: String): Pair<MaterialCatalog, String> {
    val id = catalogId(name, expansions.map { it.id }.toSet())
    return copy(expansions = expansions + Expansion(id, name, owned = true)) to id
}

fun MaterialCatalog.updateExpansion(id: String, edit: (Expansion) -> Expansion) =
    copy(expansions = expansions.map { if (it.id == id) edit(it) else it })

/** Removes an expansion; refused while tiles or enemy types still belong to it. */
fun MaterialCatalog.removeExpansion(id: String): MaterialCatalog {
    val used = tiles.count { it.expansion == id } + enemyTypes.count { it.expansion == id }
    if (used > 0) throw EditException("Die Erweiterung wird noch von $used Kacheln/Gegnertypen verwendet.")
    return copy(expansions = expansions.filterNot { it.id == id })
}

/** Adds or removes a terrain category on one side. */
fun MaterialCatalog.toggleTerrain(tileId: String, side: TileSide, tag: String): MaterialCatalog = updateTile(tileId) { tile ->
    val shape = tile.sides[side] ?: return@updateTile tile
    val terrain = if (tag in shape.terrain) shape.terrain - tag else shape.terrain + tag
    tile.copy(sides = tile.sides + (side to shape.copy(terrain = terrain)))
}

fun MaterialCatalog.setDescription(tileId: String, side: TileSide, text: String): MaterialCatalog = updateTile(tileId) { tile ->
    val shape = tile.sides[side] ?: return@updateTile tile
    tile.copy(sides = tile.sides + (side to shape.copy(description = text)))
}

/** Tiles whose side shows all of [tags] (for picking tiles that fit a description). */
fun MaterialCatalog.tilesWith(vararg tags: String): List<Pair<TileDefinition, TileSide>> =
    tiles.flatMap { t -> t.sides.filter { (_, s) -> tags.all { it in s.terrain } }.keys.map { t to it } }
