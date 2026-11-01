package io.github.aljadvb.worldbuilder.generator

import io.github.aljadvb.worldbuilder.model.FormatJson
import io.github.aljadvb.worldbuilder.model.MaterialCatalog
import io.github.aljadvb.worldbuilder.model.Terrain
import io.github.aljadvb.worldbuilder.validation.ValidationReport
import kotlinx.serialization.json.Json

/**
 * Builds the instructions for both generation steps. The first system block (rules,
 * format, material) depends only on the material selection, so it stays byte-identical
 * across story, scenario and repair requests and is cached.
 */
class PromptBuilder(private val catalog: MaterialCatalog, private val input: GenerationInput) {

    private val tiles = catalog.tiles.filter { t ->
        val owned = catalog.expansion(t.expansion)?.owned != false
        if (input.allowedTiles.isEmpty()) owned else t.id in input.allowedTiles
    }.sortedBy { it.label }

    private val enemies = catalog.enemyTypes.filter { e ->
        val owned = catalog.expansion(e.expansion)?.owned != false
        if (input.allowedEnemies.isEmpty()) owned else e.id in input.allowedEnemies
    }.sortedBy { it.name }

    val allowedTileIds: Set<String> get() = tiles.map { it.id }.toSet()

    /** Rules, game format and material – identical for all requests of one generation. */
    fun stableSystem(): String = buildString {
        appendLine(ROLE)
        appendLine()
        appendLine(FORMAT_RULES)
        appendLine()
        appendLine("# Verfügbares Material")
        appendLine()
        appendLine("Sechsecke mit flacher Seite oben; axiale Koordinaten (q, r). Die Felder jeder Kachelseite sind kachel-lokal angegeben, (0,0) ist der Ankerpunkt. Token und Gegner stehen auf genau diesen Feldern.")
        appendLine()
        appendLine("## Kacheln")
        for (t in tiles) {
            for ((side, shape) in t.sides.toSortedMap()) {
                val cells = shape.cells.joinToString(" ") { "(${it.q},${it.r})" }
                val terrain = shape.terrain.joinToString(", ") { "${Terrain.label(it)} [$it]" }.ifEmpty { "–" }
                appendLine("- Kachel-ID `${t.id}` (Nummer ${t.label}), Seite $side: Felder $cells; Landschaft: $terrain" +
                    if (shape.description.isNotBlank()) "; ${shape.description}" else "")
            }
        }
        appendLine()
        appendLine("## Gegnertypen")
        if (enemies.isEmpty()) appendLine("- (keine – verwende keine Gegnergruppen)")
        for (e in enemies) appendLine("- Gegnertyp-ID `${e.id}`: ${e.name}")
        appendLine()
        appendLine("## Landschafts-Kategorien")
        appendLine(Terrain.TAGS.joinToString(", ") { "${it.id} (${it.label})" })
    }

    fun system(extra: String? = null): List<SystemBlock> =
        listOfNotNull(SystemBlock(stableSystem(), cache = true), extra?.let { SystemBlock(it) })

    /** Step 1: story outline. */
    fun storyRequest(): LlmRequest = LlmRequest(
        system = system(),
        messages = listOf(LlmMessage(LlmMessage.Role.USER, storyMessage())),
        jsonSchema = OutputSchemas.story,
        maxTokens = 16_000,
        effort = Effort.MEDIUM,
    )

    fun storyMessage(): String = buildString {
        appendLine("Entwirf die Handlung für ein neues Szenario.")
        appendLine()
        appendLine("- Länge: ${input.length.label} (${input.length.chapters} Kapitel)")
        appendLine("- Schwierigkeit: ${input.difficulty.label}")
        if (input.wishes.isNotBlank()) appendLine("- Wünsche: ${input.wishes.trim()}")
        appendLine("- Jedes Kapitel bekommt Landschafts-Kategorien, die zu den verfügbaren Kacheln passen.")
        appendLine("- Mindestens ein Sieg- und ein Niederlage-Ende; Niederlage, wenn die Bedrohung ihr Maximum erreicht.")
        appendLine()
        appendLine("Vorlage (nur als Inspiration – Handlung und Namen dürfen übernommen werden, Formulierungen nicht):")
        appendLine("<vorlage>")
        appendLine(input.source.trim())
        appendLine("</vorlage>")
    }

    /** Step 2: scenario from the approved outline. */
    fun scenarioRequest(draft: StoryDraft): LlmRequest = LlmRequest(
        system = system(),
        messages = listOf(LlmMessage(LlmMessage.Role.USER, scenarioMessage(draft))),
        jsonSchema = OutputSchemas.scenario,
        maxTokens = 64_000,
        effort = Effort.HIGH,
    )

    fun scenarioMessage(draft: StoryDraft): String = buildString {
        appendLine("Setze diesen freigegebenen Entwurf als vollständiges Szenario im beschriebenen Format um.")
        appendLine()
        appendLine("Vorgaben:")
        appendLine("- Genau ${draft.chapters.size} Kapitel in der Reihenfolge des Entwurfs; pro Kapitel 1–3 Kacheln, deren Landschaft zum Kapitel passt.")
        appendLine("- Jede Kachel höchstens einmal im ganzen Szenario.")
        appendLine("- Am Ende liegen alle Kapitel gleichzeitig auf einem Tisch von ${input.table}: alle Kacheln zusammen höchstens etwa ${input.table.cellCapacity(catalog.hexSizeMm)} Felder (Felder je Kachel siehe Material).")
        appendLine("- Pro Kapitel 2–5 Token mit eigenen Texten; Proben und Auswahlen sorgen für Abwechslung.")
        appendLine("- Schwierigkeit „${input.difficulty.label}“: ${difficultyHint()}")
        appendLine()
        appendLine("Entwurf:")
        appendLine(DRAFT_JSON.encodeToString(StoryDraft.serializer(), draft))
    }

    /** Correction round: the previous answer plus the validator's findings. */
    fun repairRequest(draft: StoryDraft, previousAnswer: String, report: ValidationReport, extraProblems: List<String> = emptyList()): LlmRequest {
        val issues = (extraProblems + report.errors.map { "- [${it.code}] ${it.path}: ${it.message}" }).joinToString("\n")
        val text = buildString {
            appendLine("Die Prüfung hat in deinem Szenario Fehler gefunden:")
            appendLine()
            appendLine(issues)
            appendLine()
            appendLine("Behebe alle Fehler und gib das vollständige, korrigierte Szenario zurück. Ändere nichts, was nicht nötig ist.")
        }
        return LlmRequest(
            system = system(),
            messages = listOf(
                LlmMessage(LlmMessage.Role.USER, scenarioMessage(draft)),
                LlmMessage(LlmMessage.Role.ASSISTANT, previousAnswer),
                LlmMessage(LlmMessage.Role.USER, text),
            ),
            jsonSchema = OutputSchemas.scenario,
            maxTokens = 64_000,
            effort = Effort.HIGH,
        )
    }

    private fun difficultyHint() = when (input.difficulty) {
        GenerationInput.Difficulty.EASY -> "Bedrohungsmaximum eher hoch (24–30), Proben mit 1–2 Erfolgen, wenige Gegner."
        GenerationInput.Difficulty.NORMAL -> "Bedrohungsmaximum 18–24, Proben mit 2–3 Erfolgen."
        GenerationInput.Difficulty.HARD -> "Bedrohungsmaximum 14–18, Proben mit 3–4 Erfolgen, mehr Gegner."
    }

    companion object {
        private val DRAFT_JSON = Json { prettyPrint = true }


        const val ROLE = """Du schreibst Szenarien für ein kooperatives Fantasy-Abenteuerspiel. Gespielt wird am Tisch mit echten Kacheln, Token und Figuren; eine App führt durch das Szenario (Einleitung, Aufbau, Ereignisse, Proben, Bedrohung, Ziele, Enden).

Regeln für alle Texte:
- Schreibe auf Deutsch, atmosphärisch und knapp (Ereignistexte 1–4 Sätze).
- Formuliere alles in eigenen Worten. Übernimm keine Sätze oder Passagen aus der Vorlage oder aus veröffentlichten Büchern; Namen und Handlungselemente dürfen vorkommen.
- Keine Regeltexte oder Kartentexte aus veröffentlichten Spielen."""

        const val FORMAT_RULES = """# Format und Regeln eines Szenarios

Ein Szenario besteht aus Kapiteln (Abschnitte mit eigenen Kacheln und Token), Events (Abfolgen von Schritten), Triggern (Auslöser), Zielen, Gegnergruppen und Auflösungen (Enden). Alle Verweise laufen über IDs.

IDs: nur Kleinbuchstaben, Ziffern und _, eindeutig, mit Präfix: scn_ Szenario, ch_ Kapitel, pl_ Kachel-Platzierung, tok_ Token, evt_ Event, trg_ Trigger, obj_ Ziel, eg_ Gegnergruppe, res_ Auflösung.

Ablauf im Spiel:
- Start: Einleitung, dann wird startChapter aufgebaut und startObjective aktiv.
- Die Gruppe tippt Token an: inspectText wird gezeigt, dann läuft onInteract. Danach feuern Trigger vom Typ tokenInteracted.
- Am Rundenende steigt die Bedrohung um perRound.base + perRound.perHero × Heldenzahl. Schwellen sind Trigger vom Typ threatReached; bei Erreichen des Maximums feuern Trigger vom Typ threatMax.
- Ein Ziel ist erfüllt, wenn sein Trigger (completedBy) feuert, während es aktiv ist; danach feuern objectiveCompleted-Trigger.
- unlockChapter baut ein weiteres Kapitel auf (Kacheln und Token mit initiallyActive=true erscheinen), danach läuft dessen onUnlock.

Schritte eines Events: text, choice (Auswahl, ≥2 Optionen, jede führt in ein Event), test (Probe: attribute might/agility/wisdom/spirit/wit, successes = benötigte Erfolge, onSuccess/onFailure = Events), spawnEnemies, changeThreat (amount ≠ 0), addToken, removeToken, unlockChapter, setObjective, fireTrigger (nur Trigger vom Typ manual), endScenario.

Pflichtregeln (werden automatisch geprüft):
1. choice, test und endScenario dürfen nur als LETZTER Schritt eines Events stehen. Fortsetzungen gehören in eigene Events.
2. Jeder Verweis zeigt auf ein existierendes Objekt des richtigen Typs.
3. Jedes Ziel muss erfüllbar sein: sein completedBy-Trigger muss auf einem Weg auslösbar sein, während das Ziel aktiv ist.
4. Es gibt einen Trigger vom Typ threatMax, der in ein Event mit endScenario (Niederlage) führt. Ein Sieg-Ende muss erreichbar sein.
5. Token mit initiallyActive=false erscheinen nur durch addToken – dieser Schritt muss irgendwo vorkommen.
6. Token- und Gegner-Felder (location.cell) müssen zu den Feldern der gewählten Kachelseite gehören (siehe Material). Verwende nur die angegebenen Kachel-IDs und Gegnertyp-IDs.
7. Jede Kachel höchstens einmal. Kachel-Platzierungen nennen nur Kachel und Seite – die Anordnung auf der Karte berechnet die App.
8. Gegnergruppen brauchen eine Anzahl für 1, 2, 3, 4 und 5 Helden.
9. Kein Text darf leer sein (Titel, Einleitung, Ereignistexte, Erinnerungstexte, Auflösungstexte)."""
    }
}
