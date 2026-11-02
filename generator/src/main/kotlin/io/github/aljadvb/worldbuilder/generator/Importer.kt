package io.github.aljadvb.worldbuilder.generator

import io.github.aljadvb.worldbuilder.editing.Ids
import io.github.aljadvb.worldbuilder.editing.allIds
import io.github.aljadvb.worldbuilder.editing.definitions
import io.github.aljadvb.worldbuilder.editing.rename
import io.github.aljadvb.worldbuilder.model.CURRENT_FORMAT_VERSION
import io.github.aljadvb.worldbuilder.model.Campaign
import io.github.aljadvb.worldbuilder.model.Scenario
import io.github.aljadvb.worldbuilder.validation.ObjectKind

/** Puts a generated scenario into a campaign. */
object Importer {
    /** New campaign containing only [scenario]. */
    fun newCampaign(generated: Scenario): Campaign {
        val scenario = generated.copy(place = null)
        val id = Ids.fromTitle(ObjectKind.CAMPAIGN, scenario.title, wrap(scenario).allIds())
        return Campaign(CURRENT_FORMAT_VERSION, id, scenario.title, description = "", startScenario = scenario.id, scenarios = listOf(scenario))
    }

    /**
     * Adds [scenario] to [campaign]. IDs already used in the campaign are renamed in the
     * new scenario (all references follow). Returns the campaign and the scenario's final ID.
     */
    fun addTo(campaign: Campaign, generated: Scenario): Pair<Campaign, String> {
        // A place is only kept if the campaign has it.
        val scenario = generated.copy(place = generated.place?.takeIf { campaign.place(it) != null })
        var temp = wrap(scenario)
        val taken = campaign.allIds().toMutableSet()
        for (def in temp.definitions()) {
            if (def.kind == ObjectKind.CAMPAIGN || def.id !in taken) { taken += def.id; continue }
            val fresh = Ids.unique(def.id, taken + temp.allIds())
            temp = temp.rename(def.id, fresh)
            taken += fresh
        }
        val imported = temp.scenarios.single()
        return campaign.copy(scenarios = campaign.scenarios + imported) to imported.id
    }

    private fun wrap(s: Scenario) =
        Campaign(formatVersion = CURRENT_FORMAT_VERSION, id = "cmp__import_tmp", title = "", startScenario = s.id, scenarios = listOf(s))
}
