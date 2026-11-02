package io.github.aljadvb.worldbuilder.editor.play

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.aljadvb.worldbuilder.engine.CampaignProgress
import io.github.aljadvb.worldbuilder.engine.CampaignRunner
import io.github.aljadvb.worldbuilder.engine.Command
import io.github.aljadvb.worldbuilder.engine.EngineException
import io.github.aljadvb.worldbuilder.engine.GameState
import io.github.aljadvb.worldbuilder.model.Campaign
import io.github.aljadvb.worldbuilder.model.Scenario

/**
 * A test run of the campaign in the editor, using the same engine as the app.
 * Keeps every previous state so the tester can step back.
 */
class PlaytestModel(val campaign: Campaign, startScenario: String, val heroCount: Int, catalog: io.github.aljadvb.worldbuilder.model.MaterialCatalog? = null) {
    private val runner = CampaignRunner(campaign, catalog)

    var progress by mutableStateOf(CampaignProgress(heroCount, startScenario))
        private set
    var state by mutableStateOf<GameState?>(runner.engineFor(startScenario).start(heroCount))
        private set
    var error by mutableStateOf<String?>(null)
    private var past by mutableStateOf(emptyList<Pair<CampaignProgress, GameState?>>())

    val scenario: Scenario? get() = state?.let { campaign.scenario(it.scenarioId) }
    val canStepBack: Boolean get() = past.isNotEmpty()

    fun send(command: Command) {
        val s = state ?: return
        try {
            val next = runner.engineFor(s.scenarioId).apply(s, command)
            past = past + (progress to s)
            state = next
            error = null
        } catch (e: EngineException) {
            error = e.message
        }
    }

    /** After a resolution: continue with the next scenario according to the transitions. */
    fun continueCampaign() {
        val s = state?.takeIf { it.isOver } ?: return
        val (p, next) = runner.advance(progress, s)
        past = past + (progress to s)
        progress = p
        state = next
    }

    fun stepBack() {
        val (p, s) = past.lastOrNull() ?: return
        past = past.dropLast(1)
        progress = p
        state = s
        error = null
    }
}
