package io.github.aljadvb.worldbuilder.data

import android.content.res.AssetManager
import io.github.aljadvb.worldbuilder.model.Campaign
import io.github.aljadvb.worldbuilder.model.FormatJson
import io.github.aljadvb.worldbuilder.model.MaterialCatalog
import io.github.aljadvb.worldbuilder.validation.CampaignValidator
import io.github.aljadvb.worldbuilder.validation.CatalogValidator
import io.github.aljadvb.worldbuilder.validation.ValidationReport
import java.io.File
import java.security.MessageDigest

/**
 * Campaign files and the material catalog stored in app-private storage.
 * Blocking I/O – call from a background dispatcher.
 */
class CampaignLibrary(filesDir: File, private val assets: AssetManager) {
    private val campaignDir = File(filesDir, "campaigns").apply { mkdirs() }
    private val catalogFile = File(filesDir, "material.json")

    data class StoredCampaign(val campaign: Campaign, val checksum: String, val report: ValidationReport)

    sealed interface ImportResult {
        data class Success(val title: String, val report: ValidationReport) : ImportResult
        data class Invalid(val report: ValidationReport) : ImportResult
        data class ParseError(val message: String) : ImportResult
    }

    /** True while no own catalog has been imported and the bundled example catalog is used. */
    val usesExampleCatalog: Boolean get() = !catalogFile.isFile

    fun catalog(): MaterialCatalog {
        val text = if (catalogFile.isFile) catalogFile.readText() else readAsset(EXAMPLE_CATALOG)
        return FormatJson.decodeCatalog(text)
    }

    fun campaigns(): List<StoredCampaign> {
        val catalog = catalog()
        return campaignDir.listFiles { f -> f.extension == "json" }.orEmpty()
            .mapNotNull { file -> runCatching { load(file.readText(), catalog) }.getOrNull() }
            .sortedBy { it.campaign.title.lowercase() }
    }

    fun campaign(id: String): StoredCampaign? {
        val file = fileFor(id) ?: return null
        return if (file.isFile) runCatching { load(file.readText(), catalog()) }.getOrNull() else null
    }

    fun importCampaign(text: String): ImportResult {
        val campaign = try {
            FormatJson.decodeCampaign(text)
        } catch (e: Exception) {
            return ImportResult.ParseError(e.message ?: e.javaClass.simpleName)
        }
        val report = CampaignValidator.validate(campaign, catalog())
        if (!report.isValid) return ImportResult.Invalid(report)
        val file = fileFor(campaign.id) ?: return ImportResult.ParseError("Ungültige Kampagnen-ID '${campaign.id}'")
        file.writeText(text)
        return ImportResult.Success(campaign.title, report)
    }

    fun importCatalog(text: String): ImportResult {
        val catalog = try {
            FormatJson.decodeCatalog(text)
        } catch (e: Exception) {
            return ImportResult.ParseError(e.message ?: e.javaClass.simpleName)
        }
        val report = CatalogValidator.validate(catalog)
        if (!report.isValid) return ImportResult.Invalid(report)
        catalogFile.writeText(text)
        return ImportResult.Success("", report)
    }

    fun installExample(): ImportResult = importCampaign(readAsset(EXAMPLE_CAMPAIGN))

    fun delete(id: String) {
        fileFor(id)?.delete()
    }

    private fun load(text: String, catalog: MaterialCatalog): StoredCampaign {
        val campaign = FormatJson.decodeCampaign(text)
        return StoredCampaign(campaign, sha256(text), CampaignValidator.validate(campaign, catalog))
    }

    private fun fileFor(id: String): File? =
        if (SAFE_ID.matches(id)) File(campaignDir, "$id.json") else null

    private fun readAsset(name: String) = assets.open(name).bufferedReader().use { it.readText() }

    companion object {
        const val EXAMPLE_CAMPAIGN = "beispiel-kampagne.json"
        const val EXAMPLE_CATALOG = "beispiel-material.json"
        private val SAFE_ID = Regex("^[a-z][a-z0-9_]{0,80}$")

        fun sha256(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
