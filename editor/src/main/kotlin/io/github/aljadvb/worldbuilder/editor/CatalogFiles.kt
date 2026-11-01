package io.github.aljadvb.worldbuilder.editor

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import io.github.aljadvb.worldbuilder.editing.CatalogPackage
import io.github.aljadvb.worldbuilder.hexmap.TileImageCache
import io.github.aljadvb.worldbuilder.model.MaterialCatalog
import org.jetbrains.skia.Image
import java.io.File

/**
 * A catalog is either a single material.json or a ZIP with material.json plus the tile
 * pictures it references (e.g. bilder/101A.jpg). ZIPs are unpacked into the editor's data folder.
 */
object CatalogFiles {
    /** Folder for unpacked catalogs, e.g. %USERPROFILE%\.mittelerde-worldbuilder\kataloge. */
    var dataDir: File = File(System.getProperty("user.home"), ".mittelerde-worldbuilder/kataloge")

    /** Unpacks [zip] and returns the contained material.json. */
    fun unpack(zip: File): File =
        zip.inputStream().use { CatalogPackage.unpack(it, File(dataDir, zip.nameWithoutExtension.replace(Regex("[^A-Za-z0-9_-]"), "_"))) }

    /** Writes material.json and every referenced picture into [zip] (for the app). */
    fun exportZip(catalogJson: String, catalog: MaterialCatalog, imageDir: File?, zip: File) =
        zip.outputStream().use { CatalogPackage.pack(catalogJson, catalog, imageDir, it) }

    /** Copies referenced pictures from [from] to [to] (used by "save as" into another folder). */
    fun copyImages(catalog: MaterialCatalog, from: File?, to: File) {
        if (from == null || from.canonicalPath == to.canonicalPath) return
        CatalogPackage.pictures(catalog).forEach { name ->
            val src = File(from, name); val dst = File(to, name)
            if (src.isFile && !dst.exists()) { dst.parentFile.mkdirs(); src.copyTo(dst) }
        }
    }

    fun decode(file: File): ImageBitmap? =
        if (file.isFile) Image.makeFromEncoded(file.readBytes()).toComposeImageBitmap() else null

    /** Picture cache for a catalog stored in [dir] (null: no pictures). */
    fun imageCache(dir: File?): TileImageCache = TileImageCache { name -> dir?.let { decode(File(it, name)) } }
}
