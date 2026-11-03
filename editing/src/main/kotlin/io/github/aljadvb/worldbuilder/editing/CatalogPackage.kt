package io.github.aljadvb.worldbuilder.editing

import io.github.aljadvb.worldbuilder.model.MaterialCatalog
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * A catalog package: a ZIP with material.json and the tile pictures it references
 * (e.g. bilder/101A.jpg). Used by the editor and the app.
 */
object CatalogPackage {
    const val CATALOG_NAME = "material.json"
    private const val MAX_ENTRY_BYTES = 30L * 1024 * 1024
    private const val MAX_ENTRIES = 2000

    /**
     * Unpacks [zip] into [target] (which is emptied first) and returns the material.json.
     * Entries that would land outside [target] are rejected.
     */
    fun unpack(zip: InputStream, target: File): File {
        target.deleteRecursively()
        target.mkdirs()
        val root = target.canonicalFile.toPath()
        var count = 0
        ZipInputStream(zip.buffered()).use { zin ->
            while (true) {
                val entry = zin.nextEntry ?: break
                require(++count <= MAX_ENTRIES) { "Zu viele Dateien im ZIP." }
                val out = File(target, entry.name).canonicalFile
                require(out.toPath().startsWith(root)) { "Ungültiger Pfad im ZIP: ${entry.name}" }
                if (entry.isDirectory) { out.mkdirs(); continue }
                out.parentFile.mkdirs()
                out.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024); var total = 0L
                    while (true) {
                        val n = zin.read(buf); if (n < 0) break
                        total += n
                        require(total <= MAX_ENTRY_BYTES) { "Datei im ZIP zu groß: ${entry.name}" }
                        o.write(buf, 0, n)
                    }
                }
            }
        }
        return target.walkTopDown().firstOrNull { it.isFile && it.name == CATALOG_NAME }
            ?: throw IllegalArgumentException("Im ZIP fehlt die Datei $CATALOG_NAME.")
    }

    /** Picture files referenced by the catalog. */
    fun pictures(catalog: MaterialCatalog): List<String> =
        (catalog.tiles.flatMap { t -> t.sides.values.mapNotNull { it.image?.file } } +
            catalog.battleBoards.flatMap { b -> b.sides.values.mapNotNull { it.image?.file } } +
            catalog.terrainPieces.flatMap { p -> p.sides.values.mapNotNull { it.image?.file } } +
            listOfNotNull(catalog.campaignMap?.file, catalog.titleFont, catalog.ornamentFont) + catalog.items.mapNotNull { it.image?.file }).distinct()

    /** Writes material.json plus every referenced picture found in [imageDir]. */
    fun pack(catalogJson: String, catalog: MaterialCatalog, imageDir: File?, out: OutputStream) {
        ZipOutputStream(out.buffered()).use { z ->
            z.putNextEntry(ZipEntry(CATALOG_NAME)); z.write(catalogJson.toByteArray()); z.closeEntry()
            if (imageDir != null) pictures(catalog).forEach { name ->
                val f = File(imageDir, name)
                if (f.isFile) { z.putNextEntry(ZipEntry(name)); f.inputStream().use { it.copyTo(z) }; z.closeEntry() }
            }
        }
    }

    /** True if [bytes] start like a ZIP file. */
    fun isZip(bytes: ByteArray) = bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
}
