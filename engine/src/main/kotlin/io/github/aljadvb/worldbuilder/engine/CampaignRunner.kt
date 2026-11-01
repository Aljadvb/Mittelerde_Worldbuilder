package io.github.aljadvb.worldbuilder.engine

import io.github.aljadvb.worldbuilder.model.Campaign
import kotlinx.serialization.Serializable

/** Progress through a campaign's scenarios. */
@Serializable
data class CampaignProgress(
    val heroCount: Int,
    val currentScenario: String?,
    val completed: List<CompletedScenario> = emptyList(),
) {
    val isCampaignOver: Boolean get() = currentScenario == null
}

@Serializable
data class CompletedScenario(val scenarioId: String, val resolutionId: String)

/** Moves a group through the scenarios of a campaign, following its transitions. */
class CampaignRunner(val campaign: Campaign) {

    fun engineFor(scenarioId: String): GameEngine =
        GameEngine(campaign.scenario(scenarioId) ?: throw EngineException("Szenario '$scenarioId' fehlt in der Kampagne."))

    fun start(heroCount: Int): Pair<CampaignProgress, GameState> {
        val progress = CampaignProgress(heroCount, campaign.startScenario)
        return progress to engineFor(campaign.startScenario).start(heroCount)
    }

    /**
     * Records the ended scenario and starts the next one according to the campaign's
     * transitions. Returns a null state if the campaign is over.
     */
    fun advance(progress: CampaignProgress, finished: GameState): Pair<CampaignProgress, GameState?> {
        val resolution = finished.resolution ?: throw EngineException("Das Szenario ist noch nicht beendet.")
        val next = campaign.nextScenario(finished.scenarioId, resolution)
        val updated = progress.copy(
            currentScenario = next,
            completed = progress.completed + CompletedScenario(finished.scenarioId, resolution),
        )
        return updated to next?.let { engineFor(it).start(progress.heroCount) }
    }
}
