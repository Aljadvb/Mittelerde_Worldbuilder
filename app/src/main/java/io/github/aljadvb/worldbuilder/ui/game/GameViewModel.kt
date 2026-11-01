package io.github.aljadvb.worldbuilder.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.aljadvb.worldbuilder.AppContainer
import io.github.aljadvb.worldbuilder.engine.CampaignRunner
import io.github.aljadvb.worldbuilder.engine.Command
import io.github.aljadvb.worldbuilder.engine.EngineException
import io.github.aljadvb.worldbuilder.engine.GameState
import io.github.aljadvb.worldbuilder.engine.save.SaveGame
import io.github.aljadvb.worldbuilder.model.Campaign
import io.github.aljadvb.worldbuilder.model.MaterialCatalog
import io.github.aljadvb.worldbuilder.model.Scenario
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class GameUi(
    val campaign: Campaign,
    val catalog: MaterialCatalog,
    /** Folder of the catalog's tile pictures (null: none). */
    val catalogImageDir: java.io.File?,
    val save: SaveGame,
    /** The campaign file differs from the one the game was started with. */
    val campaignChanged: Boolean,
    /** Last rejected command, shown once. */
    val error: String? = null,
) {
    val game: GameState? get() = save.game
    val scenario: Scenario? get() = game?.let { campaign.scenario(it.scenarioId) }
}

sealed interface GameLoad {
    data object Loading : GameLoad
    data object SaveMissing : GameLoad
    data class CampaignMissing(val title: String) : GameLoad
    data class Ready(val ui: GameUi) : GameLoad
}

class GameViewModel(
    private val container: AppContainer,
    private val campaignId: String,
    private val slot: String,
) : ViewModel() {
    private val _state = MutableStateFlow<GameLoad>(GameLoad.Loading)
    val state: StateFlow<GameLoad> = _state.asStateFlow()

    /** Saves are written one after another, in command order. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val ioSerial = Dispatchers.IO.limitedParallelism(1)

    private var runner: CampaignRunner? = null

    init {
        viewModelScope.launch {
            _state.value = withContext(Dispatchers.IO) {
                val save = runCatching { container.saves.load(campaignId, slot) }.getOrNull()
                    ?: return@withContext GameLoad.SaveMissing
                val stored = container.library.campaign(campaignId)
                    ?: return@withContext GameLoad.CampaignMissing(save.campaignTitle)
                runner = CampaignRunner(stored.campaign)
                GameLoad.Ready(
                    GameUi(
                        campaign = stored.campaign,
                        catalog = container.library.catalog(),
                        catalogImageDir = container.library.catalogImageDir,
                        save = save,
                        campaignChanged = save.campaignChecksum != null && save.campaignChecksum != stored.checksum,
                    ),
                )
            }
        }
    }

    fun send(command: Command) {
        val ui = (state.value as? GameLoad.Ready)?.ui ?: return
        val game = ui.game ?: return
        val engine = runner?.engineFor(game.scenarioId) ?: return
        try {
            update(ui, ui.save.copy(game = engine.apply(game, command)))
        } catch (e: EngineException) {
            _state.value = GameLoad.Ready(ui.copy(error = e.message))
        }
    }

    /** After a resolution: record it and continue with the next scenario (or end the campaign). */
    fun continueCampaign() {
        val ui = (state.value as? GameLoad.Ready)?.ui ?: return
        val game = ui.game?.takeIf { it.isOver } ?: return
        val (progress, next) = runner?.advance(ui.save.progress, game) ?: return
        update(ui, ui.save.copy(progress = progress, game = next))
    }

    fun dismissError() {
        _state.update { if (it is GameLoad.Ready) GameLoad.Ready(it.ui.copy(error = null)) else it }
    }

    private fun update(ui: GameUi, save: SaveGame) {
        val stamped = save.copy(savedAtEpochMillis = System.currentTimeMillis())
        _state.value = GameLoad.Ready(ui.copy(save = stamped, error = null))
        viewModelScope.launch(ioSerial) { container.saves.save(stamped) }
    }
}
