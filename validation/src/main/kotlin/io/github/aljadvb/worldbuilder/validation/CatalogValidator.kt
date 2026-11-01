package io.github.aljadvb.worldbuilder.validation

import io.github.aljadvb.worldbuilder.model.CURRENT_FORMAT_VERSION
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
                    if (img.file.isBlank() || img.file.startsWith("/") || img.file.contains("..") || img.file.contains('\\')) {
                        c.error(IssueCode.INVALID_IMAGE, "$path.sides.$side.image.file", tile.id, "Bildpfad '${img.file}' muss relativ zum Katalog sein (z. B. bilder/101A.jpg).")
                    }
                    if (img.hexSize <= 0) c.error(IssueCode.INVALID_IMAGE, "$path.sides.$side.image.hexSize", tile.id, "Feldgröße des Bildes muss größer als 0 sein.")
                }
                shape.terrain.filter { Terrain.tag(it) == null }.forEach { unknown ->
                    c.warn(IssueCode.UNKNOWN_TERRAIN, "$path.sides.$side.terrain", tile.id, "Unbekannte Kategorie '$unknown' an Seite $side von Kachel '${tile.id}'.")
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
        }
        return ValidationReport(c.issues)
    }
}
