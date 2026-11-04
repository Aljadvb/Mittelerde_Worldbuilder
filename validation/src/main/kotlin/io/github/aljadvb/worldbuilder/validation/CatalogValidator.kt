package io.github.aljadvb.worldbuilder.validation

import io.github.aljadvb.worldbuilder.model.CURRENT_FORMAT_VERSION
import io.github.aljadvb.worldbuilder.model.GridCoord
import io.github.aljadvb.worldbuilder.model.HexCoord
import io.github.aljadvb.worldbuilder.model.MaterialCatalog
import io.github.aljadvb.worldbuilder.model.Terrain

/** Checks the user's material catalog on its own. */
object CatalogValidator {
    fun validate(catalog: MaterialCatalog): ValidationReport {
        val c = IssueCollector()
        if (catalog.formatVersion != CURRENT_FORMAT_VERSION) {
            c.error(IssueCode.UNSUPPORTED_FORMAT_VERSION, "formatVersion", null,
                "Formatversion ${catalog.formatVersion} wird nicht unterstützt (erwartet: $CURRENT_FORMAT_VERSION).")
        }
        if (catalog.hexSizeMm <= 0) c.error(IssueCode.INVALID_NUMBER, "hexSizeMm", null, "Die Feldgröße muss größer als 0 mm sein.")
        val expansionIds = catalog.expansions.map { it.id }.toSet()

        fun checkIds(ids: List<String>, path: String, what: String) {
            val seen = mutableSetOf<String>()
            ids.forEachIndexed { i, id ->
                if (!ID_PATTERN.matches(id)) {
                    c.error(IssueCode.INVALID_ID, "$path[$i].id", id, "Ungültige ID '$id' ($what): nur Kleinbuchstaben, Ziffern und '_', beginnend mit Buchstabe.")
                }
                if (!seen.add(id)) c.error(IssueCode.DUPLICATE_ID, "$path[$i].id", id, "ID '$id' ($what) ist doppelt vergeben.")
            }
        }
        checkIds(catalog.expansions.map { it.id }, "expansions", "Erweiterung")
        checkIds(catalog.tiles.map { it.id }, "tiles", "Kachel")
        checkIds(catalog.enemyTypes.map { it.id }, "enemyTypes", "Gegnertyp")
        checkIds(catalog.battleBoards.map { it.id }, "battleBoards", "Schlachtplan")
        checkIds(catalog.terrainPieces.map { it.id }, "terrainPieces", "Geländeteil")
        checkIds(catalog.items.map { it.id }, "items", "Gegenstand")
        checkIds(catalog.heroes.map { it.id }, "heroes", "Held")
        checkIds(catalog.roles.map { it.id }, "roles", "Rolle")
        catalog.roles.forEachIndexed { i, r ->
            if (r.expansion !in expansionIds) c.error(IssueCode.UNKNOWN_EXPANSION, "roles[$i].expansion", r.id, "Rolle '${r.id}' gehört zu unbekannter Erweiterung '${r.expansion}'.")
        }
        catalog.heroes.forEachIndexed { i, h ->
            if (h.expansion !in expansionIds) c.error(IssueCode.UNKNOWN_EXPANSION, "heroes[$i].expansion", h.id, "Held '${h.id}' gehört zu unbekannter Erweiterung '${h.expansion}'.")
            if (h.name.isBlank()) c.error(IssueCode.BLANK_TEXT, "heroes[$i].name", h.id, "Held '${h.id}' hat keinen Namen.")
            h.stats?.let { st ->
                val attrs = listOf(st.might, st.agility, st.wisdom, st.spirit, st.wit)
                if (attrs.any { it !in 1..6 } || st.inspiration !in 1..9 || st.damage !in 1..12 || st.fear !in 1..12) {
                    c.error(IssueCode.INVALID_NUMBER, "heroes[$i].stats", h.id, "Held '${h.id}': Eigenschaften 1–6, Inspiration 1–9, Limits 1–12.")
                }
            }
            h.suggestedRole?.let { r -> if (catalog.roles.none { it.id == r }) c.warn(IssueCode.UNKNOWN_REFERENCE, "heroes[$i].suggestedRole", h.id, "Held '${h.id}': empfohlene Rolle '$r' ist nicht im Katalog.") }
        }

        fun checkImage(file: String, path: String, owner: String) {
            if (file.isBlank() || file.startsWith("/") || file.contains("..") || file.contains('\\')) {
                c.error(IssueCode.INVALID_IMAGE, path, owner, "Bildpfad '$file' muss relativ zum Katalog sein (z. B. bilder/101A.jpg).")
            }
        }
        fun checkTerrainTags(tags: List<String>, path: String, owner: String, what: String) {
            tags.filter { Terrain.tag(it) == null }.forEach { unknown ->
                c.warn(IssueCode.UNKNOWN_TERRAIN, path, owner, "Unbekannte Kategorie '$unknown' an $what.")
            }
        }

        catalog.tiles.forEachIndexed { i, tile ->
            val path = "tiles[$i]"
            if (tile.expansion !in expansionIds) {
                c.error(IssueCode.UNKNOWN_EXPANSION, "$path.expansion", tile.id, "Kachel '${tile.id}' gehört zu unbekannter Erweiterung '${tile.expansion}'.")
            }
            if (tile.label.isBlank()) c.error(IssueCode.BLANK_TEXT, "$path.label", tile.id, "Kachel '${tile.id}' hat keine Beschriftung.")
            tile.sides.forEach { (side, shape) ->
                val sidePath = "$path.sides.$side.cells"
                if (HexCoord.ORIGIN !in shape.cells) {
                    c.error(IssueCode.TILE_SHAPE_WITHOUT_ANCHOR, sidePath, tile.id, "Seite $side von Kachel '${tile.id}' enthält das Ankerfeld (0,0) nicht.")
                }
                shape.image?.let { img ->
                    checkImage(img.file, "$path.sides.$side.image.file", tile.id)
                    if (img.hexSize <= 0) c.error(IssueCode.INVALID_IMAGE, "$path.sides.$side.image.hexSize", tile.id, "Feldgröße des Bildes muss größer als 0 sein.")
                }
                checkTerrainTags(shape.terrain, "$path.sides.$side.terrain", tile.id, "Seite $side von Kachel '${tile.id}'")
                val featureCells = shape.darkness + shape.fortified + shape.rough + shape.walled + shape.passages.map { it.cell }
                featureCells.filter { it !in shape.cells }.distinct().forEach { cell ->
                    c.error(IssueCode.CELL_NOT_ON_TILE, "$path.sides.$side", tile.id, "Seite $side von Kachel '${tile.id}': Merkmal auf Feld $cell, das nicht zur Kachel gehört.")
                }
                shape.cells.groupBy { it }.filterValues { it.size > 1 }.keys.forEach { cell ->
                    c.error(IssueCode.TILE_SHAPE_DUPLICATE_CELL, sidePath, tile.id, "Seite $side von Kachel '${tile.id}' enthält Feld $cell mehrfach.")
                }
            }
        }
        catalog.enemyTypes.forEachIndexed { i, enemy ->
            if (enemy.expansion !in expansionIds) {
                c.error(IssueCode.UNKNOWN_EXPANSION, "enemyTypes[$i].expansion", enemy.id, "Gegnertyp '${enemy.id}' gehört zu unbekannter Erweiterung '${enemy.expansion}'.")
            }
            enemy.image?.let { checkImage(it.file, "enemyTypes[$i].image.file", enemy.id) }
        }
        catalog.campaignMap?.let { checkImage(it.file, "campaignMap.file", "campaignMap") }
        catalog.items.forEachIndexed { i, item ->
            val path = "items[$i]"
            if (item.expansion !in expansionIds) {
                c.error(IssueCode.UNKNOWN_EXPANSION, "$path.expansion", item.id, "Gegenstand '${item.id}' gehört zu unbekannter Erweiterung '${item.expansion}'.")
            }
            if (item.name.isBlank()) c.error(IssueCode.BLANK_TEXT, "$path.name", item.id, "Gegenstand '${item.id}' hat keinen Namen.")
            item.image?.let { checkImage(it.file, "$path.image.file", item.id) }
        }
        catalog.battleBoards.forEachIndexed { i, board ->
            val path = "battleBoards[$i]"
            if (board.expansion !in expansionIds) {
                c.error(IssueCode.UNKNOWN_EXPANSION, "$path.expansion", board.id, "Schlachtplan '${board.id}' gehört zu unbekannter Erweiterung '${board.expansion}'.")
            }
            if (board.label.isBlank()) c.error(IssueCode.BLANK_TEXT, "$path.label", board.id, "Schlachtplan '${board.id}' hat keine Beschriftung.")
            if (board.count < 1) c.error(IssueCode.INVALID_NUMBER, "$path.count", board.id, "Anzahl von Schlachtplan '${board.id}' muss mindestens 1 sein.")
            board.sides.forEach { (sideName, side) ->
                val sp = "$path.sides.$sideName"
                if (side.width < 1 || side.height < 1) {
                    c.error(IssueCode.INVALID_NUMBER, sp, board.id, "Seite $sideName von Schlachtplan '${board.id}': Breite und Höhe müssen mindestens 1 sein.")
                    return@forEach
                }
                if (side.cellSizeMm <= 0) c.error(IssueCode.INVALID_NUMBER, "$sp.cellSizeMm", board.id, "Die Feldgröße muss größer als 0 mm sein.")
                side.image?.let { img ->
                    checkImage(img.file, "$sp.image.file", board.id)
                    if (img.cellSize <= 0) c.error(IssueCode.INVALID_IMAGE, "$sp.image.cellSize", board.id, "Feldgröße des Bildes muss größer als 0 sein.")
                }
                checkTerrainTags(side.terrain, "$sp.terrain", board.id, "Seite $sideName von Schlachtplan '${board.id}'")
                if (side.areas.isNotEmpty()) {
                    val all = side.areas.flatten()
                    val outside = all.filter { it.x !in 0 until side.width || it.y !in 0 until side.height }
                    val twice = all.groupBy { it }.filterValues { it.size > 1 }.keys
                    val missing = (0 until side.height).flatMap { y -> (0 until side.width).map { x -> GridCoord(x, y) } } - all.toSet()
                    if (outside.isNotEmpty() || twice.isNotEmpty() || missing.isNotEmpty() || side.areas.any { it.isEmpty() }) {
                        c.error(IssueCode.INVALID_BOARD_FIELDS, "$sp.areas", board.id,
                            "Seite $sideName von Schlachtplan '${board.id}': Jedes Quadrat muss genau einem Feld angehören" +
                                listOfNotNull(
                                    outside.takeIf { it.isNotEmpty() }?.let { "außerhalb: ${it.joinToString()}" },
                                    twice.takeIf { it.isNotEmpty() }?.let { "doppelt: ${it.joinToString()}" },
                                    missing.takeIf { it.isNotEmpty() }?.let { "fehlt: ${it.joinToString()}" },
                                ).joinToString("; ", prefix = " (", postfix = ")") + ".")
                    }
                }
            }
        }
        catalog.terrainPieces.forEachIndexed { i, piece ->
            val path = "terrainPieces[$i]"
            if (piece.expansion !in expansionIds) {
                c.error(IssueCode.UNKNOWN_EXPANSION, "$path.expansion", piece.id, "Geländeteil '${piece.id}' gehört zu unbekannter Erweiterung '${piece.expansion}'.")
            }
            if (piece.count < 1) c.error(IssueCode.INVALID_NUMBER, "$path.count", piece.id, "Anzahl von Geländeteil '${piece.id}' muss mindestens 1 sein.")
            piece.sides.forEach { (sideName, side) ->
                val sp = "$path.sides.$sideName"
                if (side.name.isBlank()) c.error(IssueCode.BLANK_TEXT, "$sp.name", piece.id, "Seite $sideName von Geländeteil '${piece.id}' hat keinen Namen.")
                side.image?.let { checkImage(it.file, "$sp.image.file", piece.id) }
                checkTerrainTags(side.terrain, "$sp.terrain", piece.id, "Seite $sideName von Geländeteil '${piece.id}'")
            }
        }
        return ValidationReport(c.issues)
    }
}
