package io.github.aljadvb.worldbuilder.validation

import io.github.aljadvb.worldbuilder.model.Campaign
import io.github.aljadvb.worldbuilder.model.Chapter
import io.github.aljadvb.worldbuilder.model.Event
import io.github.aljadvb.worldbuilder.model.FormatJson
import io.github.aljadvb.worldbuilder.model.MaterialCatalog
import io.github.aljadvb.worldbuilder.model.Scenario
import io.github.aljadvb.worldbuilder.model.Token
import java.io.File

object Examples {
    private val root = File(System.getProperty("repoRoot") ?: "..")
    val campaign: Campaign = FormatJson.decodeCampaign(File(root, "examples/beispiel-kampagne.json").readText())
    val catalog: MaterialCatalog = FormatJson.decodeCatalog(File(root, "examples/beispiel-material.json").readText())
    val battle: Campaign = FormatJson.decodeCampaign(File(root, "examples/beispiel-schlacht.json").readText())
}

fun Campaign.editScenario(edit: (Scenario) -> Scenario) = copy(scenarios = scenarios.map(edit))
fun Scenario.editEvent(id: String, edit: (Event) -> Event) = copy(events = events.map { if (it.id == id) edit(it) else it })
fun Scenario.editChapter(id: String, edit: (Chapter) -> Chapter) = copy(chapters = chapters.map { if (it.id == id) edit(it) else it })
fun Chapter.editToken(id: String, edit: (Token) -> Token) = copy(tokens = tokens.map { if (it.id == id) edit(it) else it })
