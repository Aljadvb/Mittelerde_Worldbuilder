package io.github.aljadvb.worldbuilder.hexmap

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import io.github.aljadvb.worldbuilder.model.HexCoord
import io.github.aljadvb.worldbuilder.model.MaterialCatalog
import io.github.aljadvb.worldbuilder.model.TileImage
import io.github.aljadvb.worldbuilder.model.TilePlacement

/** A tile picture positioned on the map: [anchorPx] of the bitmap lands on [anchorCell]. */
data class MapTileImage(
    val bitmap: ImageBitmap,
    val anchorPx: Offset,
    val hexSize: Float,
    val anchorCell: HexCoord,
    val rotation: Int,
)

/**
 * Loads tile pictures once and hands them out per placement. [decode] is platform specific
 * (Android: BitmapFactory, desktop: Skia) and receives the catalog-relative file name.
 */
class TileImageCache(private val decode: (String) -> ImageBitmap?) {
    private val cache = HashMap<String, ImageBitmap?>()

    fun bitmap(file: String): ImageBitmap? = cache.getOrPut(file) { runCatching { decode(file) }.getOrNull() }

    fun image(meta: TileImage, anchorCell: HexCoord, rotation: Int): MapTileImage? {
        val bmp = bitmap(meta.file) ?: return null
        return MapTileImage(bmp, Offset(meta.anchorX.toFloat(), meta.anchorY.toFloat()), meta.hexSize.toFloat(), anchorCell, rotation)
    }

    fun forPlacement(p: TilePlacement, catalog: MaterialCatalog): MapTileImage? {
        val meta = catalog.tile(p.tile)?.sides?.get(p.side)?.image ?: return null
        return image(meta, p.position, p.rotation)
    }
}
