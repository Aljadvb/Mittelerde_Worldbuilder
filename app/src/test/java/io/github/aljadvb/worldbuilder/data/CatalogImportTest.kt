package io.github.aljadvb.worldbuilder.data

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import io.github.aljadvb.worldbuilder.editing.CatalogPackage
import io.github.aljadvb.worldbuilder.model.FormatJson
import io.github.aljadvb.worldbuilder.model.TileImage
import io.github.aljadvb.worldbuilder.model.TileSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class CatalogImportTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val library = CampaignLibrary(context.filesDir, context.assets)

    @Test
    fun zipWithPicturesIsImportedAndUsable() {
        assertNull(library.catalogImageDir) // example catalog, no pictures

        // build a package: example catalog with a picture on x01/A
        val src = File(context.cacheDir, "src").apply { deleteRecursively(); mkdirs() }
        File(src, "bilder").mkdirs()
        File(src, "bilder/x01A.png").outputStream().use { Bitmap.createBitmap(30, 20, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
        val base = library.catalog()
        val tile = base.tiles.first()
        val catalog = base.copy(tiles = base.tiles.map {
            if (it.id != tile.id) it else it.copy(sides = it.sides + (TileSide.A to it.sides.getValue(TileSide.A).copy(image = TileImage("bilder/x01A.png", 5.0, 5.0, 4.0))))
        })
        val zip = ByteArrayOutputStream().also { CatalogPackage.pack(FormatJson.encodeCatalog(catalog), catalog, src, it) }.toByteArray()

        val result = library.importCatalog(zip)
        assertTrue(result.toString(), result is CampaignLibrary.ImportResult.Success)
        assertEquals(catalog, library.catalog())
        val dir = library.catalogImageDir
        assertNotNull(dir)
        assertTrue(File(dir, "bilder/x01A.png").isFile)
        assertNotNull(android.graphics.BitmapFactory.decodeFile(File(dir, "bilder/x01A.png").path))

        // a plain material.json still works and replaces the package
        assertTrue(library.importCatalog(FormatJson.encodeCatalog(base).toByteArray()) is CampaignLibrary.ImportResult.Success)
        assertEquals(base, library.catalog())
        assertTrue(!File(dir, "bilder/x01A.png").exists())
    }

    @Test
    fun brokenZipIsReportedAndKeepsTheOldCatalog() {
        val before = library.catalog()
        val result = library.importCatalog("PK\u0003\u0004kaputt".toByteArray())
        assertTrue(result !is CampaignLibrary.ImportResult.Success)
        assertEquals(before, library.catalog())
    }
}
