package io.github.aljadvb.worldbuilder.editor.ai

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.aljadvb.worldbuilder.editor.EditorModel
import io.github.aljadvb.worldbuilder.editor.Texts
import io.github.aljadvb.worldbuilder.generator.CancelSignal
import io.github.aljadvb.worldbuilder.generator.CancelledException
import io.github.aljadvb.worldbuilder.generator.GenerationException
import io.github.aljadvb.worldbuilder.generator.GenerationInput
import io.github.aljadvb.worldbuilder.generator.GenerationStatus
import io.github.aljadvb.worldbuilder.generator.Importer
import io.github.aljadvb.worldbuilder.generator.LlmException
import io.github.aljadvb.worldbuilder.generator.LlmProvider
import io.github.aljadvb.worldbuilder.generator.ScenarioGenerator
import io.github.aljadvb.worldbuilder.generator.ScenarioResult
import io.github.aljadvb.worldbuilder.generator.StoryDraft
import io.github.aljadvb.worldbuilder.model.MaterialCatalog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * State of the generator wizard: inputs → story draft (editable) → scenario → result.
 * Long calls run on [worker]; [providerFactory] returns null when no API key is set.
 */
class GeneratorModel(
    private val catalog: MaterialCatalog,
    private val scope: CoroutineScope,
    private val providerFactory: () -> LlmProvider?,
    private val worker: CoroutineDispatcher = Dispatchers.IO,
) {
    enum class Step { INPUT, STORY, SCENARIO }
    enum class Target { NEW_CAMPAIGN, CURRENT_CAMPAIGN }

    var step by mutableStateOf(Step.INPUT)
    // inputs
    var source by mutableStateOf("")
    var length by mutableStateOf(GenerationInput.Length.MEDIUM)
    var difficulty by mutableStateOf(GenerationInput.Difficulty.NORMAL)
    var wishes by mutableStateOf("")
    var allowedTiles by mutableStateOf(ownedTiles())
    var allowedEnemies by mutableStateOf(ownedEnemies())
    var target by mutableStateOf(Target.NEW_CAMPAIGN)
    // progress & results
    var draft by mutableStateOf<StoryDraft?>(null)
    var result by mutableStateOf<ScenarioResult?>(null)
    var busy by mutableStateOf(false)
        private set
    var status by mutableStateOf("")
        private set
    var error by mutableStateOf<String?>(null)
    var totalCost by mutableStateOf(0.0)
        private set
    private var cancel: CancelSignal? = null
    private var job: Job? = null

    private fun ownedTiles() = catalog.tiles.filter { catalog.expansion(it.expansion)?.owned != false }.map { it.id }.toSet()
    private fun ownedEnemies() = catalog.enemyTypes.filter { catalog.expansion(it.expansion)?.owned != false }.map { it.id }.toSet()

    private fun input() = GenerationInput(source, length, difficulty, allowedTiles, allowedEnemies, wishes)

    val canCreateStory get() = !busy && source.isNotBlank() && allowedTiles.isNotEmpty()

    fun createStory() = run(Texts.AI_STATUS_STORY) { gen, signal ->
        val r = gen.story(signal) { status = Texts.aiWriting(it) }
        totalCost += r.cost
        draft = r.draft
        step = Step.STORY
    }

    fun createScenario() {
        val d = draft ?: return
        step = Step.SCENARIO
        result = null
        run(Texts.AI_STATUS_SCENARIO) { gen, signal ->
            val r = gen.scenario(d, signal) { s ->
                status = when (s) {
                    is GenerationStatus.Writing -> Texts.aiRound(s.round) + " · " + Texts.aiWriting(s.characters)
                    is GenerationStatus.Checking -> Texts.aiRound(s.round) + " · " + Texts.AI_CHECKING
                    is GenerationStatus.Repairing -> Texts.aiRound(s.round) + " · " + Texts.aiRepairing(s.problems)
                }
            }
            totalCost += r.cost
            result = r
        }
    }

    fun cancelRun() { cancel?.cancel() }

    fun back() {
        if (busy) return
        step = if (step == Step.SCENARIO) Step.STORY else Step.INPUT
    }

    /** Puts the generated scenario into the editor. Returns false if nothing to accept. */
    fun accept(editor: EditorModel): Boolean {
        val scenario = result?.scenario ?: return false
        val current = editor.campaign
        if (target == Target.CURRENT_CAMPAIGN && current != null) {
            var id = scenario.id
            editor.edit { c -> Importer.addTo(c, scenario).also { id = it.second }.first }
            editor.selection = id
        } else {
            editor.openUnsaved(Importer.newCampaign(scenario))
            editor.selection = scenario.id
        }
        return true
    }

    private fun run(startStatus: String, block: suspend (ScenarioGenerator, CancelSignal) -> Unit) {
        if (busy) return
        val provider = providerFactory()
        if (provider == null) {
            error = Texts.AI_NO_KEY
            return
        }
        val signal = CancelSignal().also { cancel = it }
        busy = true; error = null; status = startStatus
        job = scope.launch(worker) {
            try {
                block(ScenarioGenerator(provider, catalog, input()), signal)
            } catch (e: CancelledException) {
                error = Texts.AI_CANCELLED
            } catch (e: GenerationException) {
                error = e.message
            } catch (e: LlmException) {
                error = e.message
            } catch (e: Exception) {
                error = Texts.aiUnexpected(e.message ?: e.javaClass.simpleName)
            } finally {
                busy = false
                status = ""
            }
        }
    }

    /** Waits for the running request (tests). */
    suspend fun join() { job?.join() }
}
