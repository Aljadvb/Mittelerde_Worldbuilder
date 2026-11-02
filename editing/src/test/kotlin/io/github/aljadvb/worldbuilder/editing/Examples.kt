package io.github.aljadvb.worldbuilder.editing

import io.github.aljadvb.worldbuilder.model.Campaign
import io.github.aljadvb.worldbuilder.model.FormatJson
import io.github.aljadvb.worldbuilder.model.MaterialCatalog
import io.github.aljadvb.worldbuilder.validation.CampaignValidator
import io.github.aljadvb.worldbuilder.validation.ValidationReport
import java.io.File

object Examples {
    private val root = File(System.getProperty("repoRoot") ?: "..")
    val campaign: Campaign = FormatJson.decodeCampaign(File(root, "examples/beispiel-kampagne.json").readText())
    val catalog: MaterialCatalog = FormatJson.decodeCatalog(File(root, "examples/beispiel-material.json").readText())
    val battle: Campaign = FormatJson.decodeCampaign(File(root, "examples/beispiel-schlacht.json").readText())
}

fun Campaign.validate(): ValidationReport = CampaignValidator.validate(this, Examples.catalog)
