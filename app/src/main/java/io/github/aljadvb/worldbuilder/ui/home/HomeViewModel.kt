package io.github.aljadvb.worldbuilder.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.aljadvb.worldbuilder.AppContainer
import io.github.aljadvb.worldbuilder.data.CampaignLibrary
import io.github.aljadvb.worldbuilder.data.CampaignLibrary.ImportResult
import io.github.aljadvb.worldbuilder.engine.CampaignRunner
import io.github.aljadvb.worldbuilder.engine.save.SaveGame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SaveSummary(
    val save: SaveGame,
    val scenarioTitle: String?,
    val campaignAvailable: Boolean,
)

data class HomeUiState(
    val loading: Boolean = true,
    val campaigns: List<CampaignLibrary.StoredCampaign> = emptyList(),
    val saves: List<SaveSummary> = emptyList(),
    val usesExampleCatalog: Boolean = true,
    val tileCount: Int = 0,
    val enemyTypeCount: Int = 0,
    /** Result of the last import, shown once. */
    val importResult: ImportOutcome? = null,
)

data class ImportOutcome(val isCatalog: Boolean, val result: ImportResult)

class HomeViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()


    fun refresh() {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val library = container.library
                val catalog = library.catalog()
                val campaigns = library.campaigns()
                val byId = campaigns.associateBy { it.campaign.id }
                val saves = container.saves.list().map { save ->
                    val campaign = byId[save.campaignId]?.campaign
                    SaveSummary(
                        save = save,
                        scenarioTitle = save.game?.scenarioId?.let { campaign?.scenario(it)?.title },
                        campaignAvailable = campaign != null,
                    )
                }
                HomeUiState(
                    loading = false,
                    campaigns = campaigns,
                    saves = saves,
                    usesExampleCatalog = library.usesExampleCatalog,
                    tileCount = catalog.tiles.size,
                    enemyTypeCount = catalog.enemyTypes.size,
                )
            }
            _state.update { loaded.copy(importResult = it.importResult) }
        }
    }

    fun importCampaign(text: String) = runImport(isCatalog = false) { container.library.importCampaign(text) }
    fun importCatalog(bytes: ByteArray) = runImport(isCatalog = true) { container.library.importCatalog(bytes) }
    fun installExample() = runImport(isCatalog = false) { container.library.installExample() }

    fun reportReadError(message: String) {
        _state.update { it.copy(importResult = ImportOutcome(false, ImportResult.ParseError(message))) }
    }

    fun dismissImportResult() = _state.update { it.copy(importResult = null) }

    private fun runImport(isCatalog: Boolean, block: () -> ImportResult) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { block() }
            _state.update { it.copy(importResult = ImportOutcome(isCatalog, result)) }
            refresh()
        }
    }

    fun deleteCampaign(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { container.library.delete(id) }
            refresh()
        }
    }

    fun deleteSave(save: SaveGame) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { container.saves.delete(save.campaignId, save.slot) }
            refresh()
        }
    }

    /** Creates a new save for the campaign and reports its slot to [onCreated]. */
    fun startNewGame(stored: CampaignLibrary.StoredCampaign, heroCount: Int, onCreated: (campaignId: String, slot: String) -> Unit) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val slot = "spiel_$now"
            withContext(Dispatchers.IO) {
                val (progress, game) = CampaignRunner(stored.campaign).start(heroCount)
                container.saves.save(
                    SaveGame(
                        slot = slot,
                        campaignId = stored.campaign.id,
                        campaignTitle = stored.campaign.title,
                        campaignChecksum = stored.checksum,
                        savedAtEpochMillis = now,
                        progress = progress,
                        game = game,
                    ),
                )
            }
            onCreated(stored.campaign.id, slot)
        }
    }
}
