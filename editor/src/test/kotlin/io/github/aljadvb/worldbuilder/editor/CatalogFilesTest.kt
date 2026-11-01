package io.github.aljadvb.worldbuilder.editor

import io.github.aljadvb.worldbuilder.model.FormatJson
import io.github.aljadvb.worldbuilder.model.TileImage
import io.github.aljadvb.worldbuilder.model.TileSide
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatalogFilesTest {
    private val tmp = Files.createTempDirectory("catfiles").toFile().also { CatalogFiles.dataDir = File(it, "data") }

    @AfterTest
    fun cleanup() { tmp.deleteRecursively() }

    private fun catalogWithPicture(): Pair<File, io.github.aljadvb.worldbuilder.model.MaterialCatalog> {
        val src = File(tmp, "src").apply { mkdirs() }
        File(src, "bilder").mkdirs()
        ImageIO.write(BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB), "png", File(src, "bilder/x01A.png"))
        val base = EditorModel.loadExampleCatalog()
        val tile = base.tiles.first()
        val catalog = base.copy(tiles = listOf(tile.copy(sides = tile.sides + (TileSide.A to tile.sides.getValue(TileSide.A).copy(image = TileImage("bilder/x01A.png", 10.0, 12.0, 8.0))))))
        File(src, "material.json").writeText(FormatJson.encodeCatalog(catalog))
        return src to catalog
    }

    @Test
    fun exportUnpackAndLoadPictures() {
        val (src, catalog) = catalogWithPicture()
        val zip = File(tmp, "katalog.zip")
        CatalogFiles.exportZip(FormatJson.encodeCatalog(catalog), catalog, src, zip)

        val model = EditorModel(InMemoryEditorPrefs())
        model.loadCatalog(zip)
        assertNull(model.message)
        assertEquals(catalog, model.catalog)
        assertTrue(model.catalogFile!!.path.startsWith(CatalogFiles.dataDir.path))
        val placementImage = model.tileImages.bitmap("bilder/x01A.png")
        assertNotNull(placementImage)
        assertEquals(40, placementImage.width)
    }

    @Test
    fun zipWithPathTraversalIsRejected() {
        val zip = File(tmp, "boese.zip")
        ZipOutputStream(zip.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("../../ausserhalb.txt")); z.write("x".toByteArray()); z.closeEntry()
        }
        assertFailsWith<IllegalArgumentException> { CatalogFiles.unpack(zip) }
        assertTrue(!File(tmp, "ausserhalb.txt").exists())
    }

    @Test
    fun saveAsCopiesPicturesToTheNewFolder() {
        val (src, catalog) = catalogWithPicture()
        val model = io.github.aljadvb.worldbuilder.editor.catalog.CatalogEditorModel(catalog, File(src, "material.json")) {}
        val target = File(tmp, "neu/material.json").apply { parentFile.mkdirs() }
        assertTrue(model.saveAs(target))
        assertTrue(File(tmp, "neu/bilder/x01A.png").isFile)
    }
}
