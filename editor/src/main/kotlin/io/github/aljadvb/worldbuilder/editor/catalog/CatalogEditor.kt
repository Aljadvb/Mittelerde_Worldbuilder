package io.github.aljadvb.worldbuilder.editor.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.aljadvb.worldbuilder.editing.addBattleBoard
import io.github.aljadvb.worldbuilder.editing.addTerrainPiece
import io.github.aljadvb.worldbuilder.editing.addEnemyType
import io.github.aljadvb.worldbuilder.editing.addExpansion
import io.github.aljadvb.worldbuilder.editing.addTile
import io.github.aljadvb.worldbuilder.editing.mirrorAToB
import io.github.aljadvb.worldbuilder.editing.removeEnemyType
import io.github.aljadvb.worldbuilder.editing.removeExpansion
import io.github.aljadvb.worldbuilder.editing.removeTile
import io.github.aljadvb.worldbuilder.editing.setDescription
import io.github.aljadvb.worldbuilder.editing.toggleCell
import io.github.aljadvb.worldbuilder.editing.toggleTerrain
import io.github.aljadvb.worldbuilder.editing.updateEnemyType
import io.github.aljadvb.worldbuilder.editing.updateExpansion
import io.github.aljadvb.worldbuilder.editing.updateTile
import io.github.aljadvb.worldbuilder.editor.Texts
import io.github.aljadvb.worldbuilder.editor.ui.CheckInput
import io.github.aljadvb.worldbuilder.editor.ui.NameDialog
import io.github.aljadvb.worldbuilder.editor.ui.PanelTitle
import io.github.aljadvb.worldbuilder.hexmap.HexCanvas
import io.github.aljadvb.worldbuilder.hexmap.HexProjection
import io.github.aljadvb.worldbuilder.hexmap.MapColors
import io.github.aljadvb.worldbuilder.hexmap.MapScene
import io.github.aljadvb.worldbuilder.hexmap.MapTile
import io.github.aljadvb.worldbuilder.model.HexCoord
import io.github.aljadvb.worldbuilder.model.HexOrientation
import io.github.aljadvb.worldbuilder.model.Terrain
import io.github.aljadvb.worldbuilder.model.TerrainGroup
import io.github.aljadvb.worldbuilder.model.TileSide
import io.github.aljadvb.worldbuilder.validation.Severity

const val SHAPE_TAG = "shape"
private const val SHAPE_RADIUS = 3

@Composable
fun CatalogEditor(model: CatalogEditorModel) {
    var creating by remember { mutableStateOf<Pair<String, (String) -> Unit>?>(null) }
    val catalog = model.catalog
    val defaultExpansion = catalog.expansions.firstOrNull()?.id.orEmpty()

    Surface(Modifier.fillMaxSize()) {
        Row {
            LazyColumn(Modifier.width(280.dp).fillMaxHeight()) {
                item {
                    ListHeader(Texts.CATALOG_TILES) {
                        creating = Texts.CATALOG_NEW_TILE to { name ->
                            var id = ""
                            model.edit { c -> c.addTile(name, defaultExpansion).also { id = it.second }.first }
                            model.selection = CatalogSelection.Tile(id)
                        }
                    }
                }
                items(catalog.tiles, key = { "t_" + it.id }) { t ->
                    ListRow("${t.label}  ·  ${t.sides.values.firstOrNull()?.cells?.size ?: 0} ${Texts.CATALOG_CELLS}", model.selection == CatalogSelection.Tile(t.id)) {
                        model.selection = CatalogSelection.Tile(t.id)
                    }
                }
                item {
                    ListHeader(Texts.CATALOG_BOARDS) {
                        creating = Texts.CATALOG_NEW_BOARD to { name ->
                            var id = ""
                            model.edit { c -> c.addBattleBoard(name, defaultExpansion).also { id = it.second }.first }
                            model.selection = CatalogSelection.Board(id)
                        }
                    }
                }
                items(catalog.battleBoards, key = { "b_" + it.id }) { b ->
                    ListRow("${b.label}  ·  ${b.count}×", model.selection == CatalogSelection.Board(b.id)) { model.selection = CatalogSelection.Board(b.id) }
                }
                item {
                    ListHeader(Texts.CATALOG_PIECES) {
                        creating = Texts.CATALOG_NEW_PIECE to { name ->
                            var id = ""
                            model.edit { c -> c.addTerrainPiece(name, defaultExpansion).also { id = it.second }.first }
                            model.selection = CatalogSelection.Piece(id)
                        }
                    }
                }
                items(catalog.terrainPieces, key = { "g_" + it.id }) { p ->
                    ListRow("${p.sides.values.joinToString(" / ") { it.name }}  ·  ${p.count}×", model.selection == CatalogSelection.Piece(p.id)) { model.selection = CatalogSelection.Piece(p.id) }
                }
                item {
                    ListHeader(Texts.CATALOG_ENEMIES) {
                        creating = Texts.CATALOG_NEW_ENEMY to { name ->
                            var id = ""
                            model.edit { c -> c.addEnemyType(name, defaultExpansion).also { id = it.second }.first }
                            model.selection = CatalogSelection.Enemy(id)
                        }
                    }
                }
                items(catalog.enemyTypes, key = { "e_" + it.id }) { e ->
                    ListRow(e.name, model.selection == CatalogSelection.Enemy(e.id)) { model.selection = CatalogSelection.Enemy(e.id) }
                }
                item {
                    ListHeader(Texts.CATALOG_EXPANSIONS) {
                        creating = Texts.CATALOG_NEW_EXPANSION to { name ->
                            var id = ""
                            model.edit { c -> c.addExpansion(name).also { id = it.second }.first }
                            model.selection = CatalogSelection.Expansion(id)
                        }
                    }
                }
                items(catalog.expansions, key = { "x_" + it.id }) { x ->
                    ListRow(x.name + if (x.owned) "" else " (${Texts.CATALOG_NOT_OWNED})", model.selection == CatalogSelection.Expansion(x.id)) {
                        model.selection = CatalogSelection.Expansion(x.id)
                    }
                }
            }
            VerticalDivider()
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (val sel = model.selection) {
                    is CatalogSelection.Tile -> TileDetail(model, sel.id)
                    is CatalogSelection.Enemy -> EnemyDetail(model, sel.id)
                    is CatalogSelection.Expansion -> ExpansionDetail(model, sel.id)
                    is CatalogSelection.Board -> BoardDetail(model, sel.id)
                    is CatalogSelection.Piece -> PieceDetail(model, sel.id)
                    null -> Text(Texts.CATALOG_EMPTY)
                }
                HorizontalDivider()
                val report = model.report
                if (report.issues.isEmpty()) Text(Texts.CATALOG_VALID, style = MaterialTheme.typography.bodySmall)
                report.issues.forEach {
                    Text(it.message, color = if (it.severity == Severity.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    creating?.let { (title, action) ->
        NameDialog(title, onConfirm = { creating = null; action(it) }, onDismiss = { creating = null })
    }
    model.message?.let { msg ->
        AlertDialog(onDismissRequest = { model.message = null }, confirmButton = { Button(onClick = { model.message = null }) { Text(Texts.OK) } }, text = { Text(msg) })
    }
}

@Composable
private fun ListHeader(title: String, onAdd: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        PanelTitle(title, Modifier.weight(1f))
        TextButton(onClick = onAdd) { Text("＋") }
    }
}

@Composable
private fun ListRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        Modifier.fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

@Composable
internal fun ExpansionChoice(model: CatalogEditorModel, selected: String, onSelect: (String) -> Unit) {
    Text(Texts.CATALOG_EXPANSION, style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        model.catalog.expansions.forEach { x -> FilterChip(selected = x.id == selected, onClick = { onSelect(x.id) }, label = { Text(x.name) }) }
    }
}

@Composable
private fun TileDetail(model: CatalogEditorModel, id: String) {
    val tile = model.catalog.tile(id) ?: return
    Text("${Texts.TILE} · ${tile.id}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    OutlinedTextField(tile.label, { v -> model.edit("$id.label") { it.updateTile(id) { t -> t.copy(label = v) } } }, label = { Text(Texts.CATALOG_LABEL) }, singleLine = true)
    ExpansionChoice(model, tile.expansion) { x -> model.edit { it.updateTile(id) { t -> t.copy(expansion = x) } } }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        TileSide.entries.forEach { s -> FilterChip(selected = model.side == s, onClick = { model.side = s }, label = { Text("${Texts.SIDE} $s") }) }
        OutlinedButton(onClick = { model.edit { it.mirrorAToB(id) }; model.side = TileSide.B }) { Text(Texts.CATALOG_MIRROR) }
        OutlinedButton(onClick = { model.edit { it.removeTile(id) }; model.selection = null }) { Text(Texts.DELETE) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(Texts.CATALOG_SHAPE_HINT, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(460.dp))
            ShapeGrid(model, id)
        }
        TerrainEditor(model, id, Modifier.weight(1f))
    }
}

/** Categories and description of the current side. */
@Composable
private fun TerrainEditor(model: CatalogEditorModel, id: String, modifier: Modifier) {
    val shape = model.catalog.tile(id)?.sides?.get(model.side) ?: return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(Texts.catalogTerrain(model.side.name), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(Texts.CATALOG_TERRAIN_HINT, style = MaterialTheme.typography.bodySmall)
        TerrainGroup.entries.forEach { group ->
            Text(group.label, style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Terrain.TAGS.filter { it.group == group }.forEach { tag ->
                    FilterChip(
                        selected = tag.id in shape.terrain,
                        onClick = { model.edit { it.toggleTerrain(id, model.side, tag.id) } },
                        label = { Text(tag.label) },
                    )
                }
            }
        }
        OutlinedTextField(
            shape.description,
            { v -> model.edit("$id.${model.side}.description") { it.setDescription(id, model.side, v) } },
            label = { Text(Texts.CATALOG_DESCRIPTION) },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        fun cells(l: List<io.github.aljadvb.worldbuilder.model.HexCoord>) = l.joinToString(" ") { "(${it.q},${it.r})" }
        listOf(
            Texts.SYMBOL_DARKNESS to cells(shape.darkness),
            Texts.SYMBOL_FORTIFIED to cells(shape.fortified),
            Texts.SYMBOL_ROUGH to cells(shape.rough),
            Texts.SYMBOL_UNDERGROUND to if (shape.walled.isEmpty()) "" else cells(shape.walled) + " · " + Texts.passages(shape.passages.size),
        ).filter { it.second.isNotEmpty() }.forEach { (k, v) -> Text("$k: $v", style = MaterialTheme.typography.bodySmall) }
    }
}

/** Click cells of a hex grid to toggle them on the current side. */
@Composable
private fun ShapeGrid(model: CatalogEditorModel, id: String) {
    val tile = model.catalog.tile(id) ?: return
    val orientation = model.catalog.hexOrientation
    val cells = tile.sides[model.side]?.cells.orEmpty()
    val area = (-SHAPE_RADIUS..SHAPE_RADIUS).flatMap { q -> (-SHAPE_RADIUS..SHAPE_RADIUS).map { r -> HexCoord(q, r) } }
        .filter { it.distanceTo(HexCoord.ORIGIN) <= SHAPE_RADIUS }
    val projection = remember { arrayOfNulls<HexProjection>(1) }
    val scene = MapScene(
        orientation = orientation,
        tiles = listOf(
            MapTile("area", "", area, MapColors.Grid.copy(alpha = 0.04f), border = MapColors.Grid),
            MapTile(
                tile.id, "", cells, MapColors.chapterFill(0),
                image = tile.sides[model.side]?.image?.let { model.tileImages.image(it, HexCoord.ORIGIN, 0) },
            ),
        ),
        highlightCells = setOf(HexCoord.ORIGIN),
    )
    Box(
        Modifier.size(460.dp).clipToBounds().testTag(SHAPE_TAG).pointerInput(id, model.side) {
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent()
                    if (e.type == PointerEventType.Press) {
                        val proj = projection[0] ?: continue
                        val cell = proj.hexAt(e.changes.first().position)
                        if (cell.distanceTo(HexCoord.ORIGIN) <= SHAPE_RADIUS) model.edit { it.toggleCell(id, model.side, cell) }
                    }
                }
            }
        },
    ) {
        HexCanvas(
            scene, MaterialTheme.colorScheme.primary, Modifier.fillMaxSize(),
            projection = { size -> HexProjection.fit(orientation, area, size.width, size.height, 8f, 200f) },
            onProjection = { projection[0] = it },
        )
    }
}

@Composable
private fun EnemyDetail(model: CatalogEditorModel, id: String) {
    val e = model.catalog.enemyType(id) ?: return
    Text("${Texts.ENEMY_TYPE} · ${e.id}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    OutlinedTextField(e.name, { v -> model.edit("$id.name") { it.updateEnemyType(id) { x -> x.copy(name = v) } } }, label = { Text(Texts.NAME) }, singleLine = true)
    ExpansionChoice(model, e.expansion) { x -> model.edit { it.updateEnemyType(id) { t -> t.copy(expansion = x) } } }
    OutlinedButton(onClick = { model.edit { it.removeEnemyType(id) }; model.selection = null }) { Text(Texts.DELETE) }
}

@Composable
private fun ExpansionDetail(model: CatalogEditorModel, id: String) {
    val x = model.catalog.expansion(id) ?: return
    Text("${Texts.CATALOG_EXPANSION} · ${x.id}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    OutlinedTextField(x.name, { v -> model.edit("$id.name") { it.updateExpansion(id) { e -> e.copy(name = v) } } }, label = { Text(Texts.NAME) }, singleLine = true)
    CheckInput(Texts.CATALOG_OWNED, x.owned) { v -> model.edit { it.updateExpansion(id) { e -> e.copy(owned = v) } } }
    Text(Texts.CATALOG_ORIENTATION, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        HexOrientation.entries.forEach { o ->
            FilterChip(selected = model.catalog.hexOrientation == o, onClick = { model.edit { it.copy(hexOrientation = o) } }, label = { Text(if (o == HexOrientation.POINTY) Texts.CATALOG_POINTY else Texts.CATALOG_FLAT) })
        }
    }
    OutlinedButton(onClick = { if (model.edit { it.removeExpansion(id) }) model.selection = null }) { Text(Texts.DELETE) }
}
