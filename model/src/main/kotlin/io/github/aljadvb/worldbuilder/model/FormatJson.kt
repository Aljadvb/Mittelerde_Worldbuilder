package io.github.aljadvb.worldbuilder.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

/** JSON configuration for campaign and catalog files. Strict: unknown keys are errors. */
@OptIn(ExperimentalSerializationApi::class)
object FormatJson {
    val json: Json = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = false
        explicitNulls = false
        encodeDefaults = false
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    fun decodeCampaign(text: String): Campaign = json.decodeFromString(Campaign.serializer(), text)
    fun encodeCampaign(campaign: Campaign): String = json.encodeToString(Campaign.serializer(), campaign)

    fun decodeCatalog(text: String): MaterialCatalog = json.decodeFromString(MaterialCatalog.serializer(), text)
    fun encodeCatalog(catalog: MaterialCatalog): String = json.encodeToString(MaterialCatalog.serializer(), catalog)
}
