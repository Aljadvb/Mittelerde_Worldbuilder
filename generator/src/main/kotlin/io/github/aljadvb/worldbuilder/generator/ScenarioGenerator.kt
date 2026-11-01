package io.github.aljadvb.worldbuilder.generator

import io.github.aljadvb.worldbuilder.model.CURRENT_FORMAT_VERSION
import io.github.aljadvb.worldbuilder.model.Campaign
import io.github.aljadvb.worldbuilder.model.FormatJson
import io.github.aljadvb.worldbuilder.model.MaterialCatalog
import io.github.aljadvb.worldbuilder.model.Scenario
import io.github.aljadvb.worldbuilder.validation.CampaignValidator
import io.github.aljadvb.worldbuilder.validation.ValidationReport
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** Progress of a generation, for the UI. */
sealed interface GenerationStatus {
    data class Writing(val round: Int, val characters: Int) : GenerationStatus
    data class Checking(val round: Int) : GenerationStatus
    data class Repairing(val round: Int, val problems: Int) : GenerationStatus
}

class GenerationException(message: String) : Exception(message)

data class StoryResult(val draft: StoryDraft, val usage: Usage, val cost: Double)

data class ScenarioResult(
    val scenario: Scenario,
    /** Findings that remain after the last round (errors are possible if all rounds failed). */
    val report: ValidationReport,
    val rounds: Int,
    val usage: Usage,
    val cost: Double,
    val copyFindings: List<CopyFinding>,
)

/**
 * The two-step pipeline: story outline, then scenario with validation and up to
 * [maxRepairs] correction rounds in which the validator's findings go back to the model.
 */
class ScenarioGenerator(
    private val provider: LlmProvider,
    private val catalog: MaterialCatalog,
    private val input: GenerationInput,
    private val maxRepairs: Int = 3,
) {
    private val prompts = PromptBuilder(catalog, input)
    private val price = ModelInfo.of(provider.model)
    private val lenient = Json { ignoreUnknownKeys = true }

    private fun cost(u: Usage) = price?.cost(u) ?: 0.0

    fun story(cancel: CancelSignal = CancelSignal(), onProgress: (Int) -> Unit = {}): StoryResult {
        val result = call(prompts.storyRequest(), cancel, onProgress)
        val draft = try {
            lenient.decodeFromString(StoryDraft.serializer(), result.text)
        } catch (e: Exception) {
            throw GenerationException("Der Entwurf konnte nicht gelesen werden: ${e.message}")
        }
        return StoryResult(draft, result.usage, cost(result.usage))
    }

    fun scenario(draft: StoryDraft, cancel: CancelSignal = CancelSignal(), onStatus: (GenerationStatus) -> Unit = {}): ScenarioResult {
        var usage = Usage()
        var request = prompts.scenarioRequest(draft)
        var round = 0
        while (true) {
            round++
            onStatus(GenerationStatus.Writing(round, 0))
            val result = call(request, cancel) { onStatus(GenerationStatus.Writing(round, it)) }
            usage += result.usage
            onStatus(GenerationStatus.Checking(round))

            val parsed = parse(result.text)
            val problems = parsed.problems
            val scenario = parsed.scenario?.let { AutoLayout.layout(it, catalog) }
            val report = scenario?.let { validate(it) } ?: ValidationReport(emptyList())

            val done = scenario != null && problems.isEmpty() && report.isValid
            if (done || round > maxRepairs) {
                if (scenario == null) throw GenerationException("Auch nach $maxRepairs Korrekturen kein lesbares Szenario: ${problems.joinToString("; ")}")
                return ScenarioResult(scenario, report, round, usage, cost(usage), CopyCheck.check(scenario, input.source))
            }
            onStatus(GenerationStatus.Repairing(round, problems.size + report.errors.size))
            request = prompts.repairRequest(draft, result.text, report, problems)
        }
    }

    private fun call(request: LlmRequest, cancel: CancelSignal, onProgress: (Int) -> Unit): LlmResult {
        val result = provider.generate(request, cancel, onProgress)
        when (result.stopReason) {
            StopReason.REFUSAL -> throw GenerationException(
                "Die KI hat die Anfrage abgelehnt" + (result.refusal?.let { ": $it" } ?: ".") + " Bitte Vorlage oder Wünsche anpassen.",
            )
            StopReason.MAX_TOKENS -> throw GenerationException("Die Antwort wurde zu lang und abgeschnitten. Bitte eine kürzere Länge wählen.")
            else -> Unit
        }
        return result
    }

    private data class Parsed(val scenario: Scenario?, val problems: List<String>)

    /** Reads the model's JSON; positions are filled in later by [AutoLayout]. */
    private fun parse(text: String): Parsed {
        val element = try {
            lenient.parseToJsonElement(text)
        } catch (e: Exception) {
            return Parsed(null, listOf("- Die Antwort ist kein gültiges JSON: ${e.message}"))
        }
        val scenario = try {
            FormatJson.json.decodeFromJsonElement(Scenario.serializer(), withPositions(element))
        } catch (e: Exception) {
            return Parsed(null, listOf("- Das JSON passt nicht zum Format: ${e.message}"))
        }
        val problems = mutableListOf<String>()
        val allowed = prompts.allowedTileIds
        scenario.placements.filter { it.tile !in allowed }.forEach {
            problems += "- Platzierung '${it.id}' verwendet Kachel '${it.tile}', die nicht zum freigegebenen Material gehört."
        }
        scenario.placements.groupBy { it.tile }.filterValues { it.size > 1 }.keys.forEach {
            problems += "- Kachel '$it' wird mehrfach verwendet; jede Kachel gibt es nur einmal."
        }
        return Parsed(scenario, problems)
    }

    /** Adds `position`/`rotation` to every tile placement so the strict model can read it. */
    private fun withPositions(e: JsonElement): JsonElement {
        val root = e.jsonObject
        val chapters = (root["chapters"] as? JsonArray) ?: return e
        val fixed = JsonArray(chapters.map { ch ->
            val obj = ch.jsonObject
            val tiles = (obj["tiles"] as? JsonArray)?.map { t ->
                JsonObject(t.jsonObject + mapOf("position" to JsonObject(mapOf("q" to JsonPrimitive(0), "r" to JsonPrimitive(0)))))
            }
            if (tiles == null) obj else JsonObject(obj + ("tiles" to JsonArray(tiles)))
        })
        return JsonObject(root + ("chapters" to fixed))
    }

    /** Validates the scenario on its own (campaign-level findings are ignored). */
    fun validate(scenario: Scenario): ValidationReport {
        val campaign = Campaign(CURRENT_FORMAT_VERSION, "cmp_generator", "Generator", startScenario = scenario.id, scenarios = listOf(scenario))
        return CampaignValidator.validate(campaign, catalog)
    }
}
