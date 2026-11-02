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

    private val boards = catalog.battleBoards.filter { catalog.expansion(it.expansion)?.owned != false }.sortedBy { it.label }
    private val pieces = catalog.terrainPieces.filter { catalog.expansion(it.expansion)?.owned != false }

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
                fun list(l: List<io.github.aljadvb.worldbuilder.model.HexCoord>) = l.joinToString(" ") { "(${it.q},${it.r})" }
                val marks = listOfNotNull(
                    shape.darkness.takeIf { it.isNotEmpty() }?.let { "Dunkelheit ${list(it)}" },
                    shape.fortified.takeIf { it.isNotEmpty() }?.let { "befestigt ${list(it)}" },
                    shape.rough.takeIf { it.isNotEmpty() }?.let { "unwegsam ${list(it)}" },
                    shape.walled.takeIf { it.isNotEmpty() }?.let { "unterirdisch ${list(it)} mit ${shape.passages.size} Durchgängen" },
                ).joinToString("; ")
                appendLine("- Kachel-ID `${t.id}` (Nummer ${t.label}), Seite $side: Felder $cells; Landschaft: $terrain" +
                    (if (marks.isNotEmpty()) "; Symbole: $marks" else "") +
                    if (shape.description.isNotBlank()) "; ${shape.description}" else "")
            }
        }
        appendLine()
        appendLine("## Schlachtpläne")
        if (boards.isEmpty()) appendLine("- (keine – verwende keine Schlacht-Kapitel)")
        for (b in boards) {
            for ((side, s) in b.sides.toSortedMap()) {
                val fields = s.fields().joinToString(" ") { f -> "[" + f.joinToString(" ") { "(${it.x},${it.y})" } + "]" }
                val terrain = s.terrain.joinToString(", ") { "${Terrain.label(it)} [$it]" }.ifEmpty { "–" }
                appendLine("- Plan-ID `${b.id}` (${b.label}, ${b.count}× vorhanden), Seite $side: ${s.width}×${s.height} Quadrate; Felder $fields; Landschaft: $terrain" +
                    if (s.description.isNotBlank()) "; ${s.description}" else "")
            }
        }
        appendLine()
        appendLine("## Geländeteile")
        if (pieces.isEmpty()) appendLine("- (keine)")
        for (p in pieces) {
            val sides = p.sides.toSortedMap().entries.joinToString(" | ") { (side, s) ->
                "Seite $side „${s.name}“ (${if (s.placement.name == "EDGE") "auf Feldgrenze" else "im Feld"})" + if (s.effect.isNotBlank()) ": ${s.effect}" else ""
            }
            appendLine("- Teil-ID `${p.id}`, ${p.count} Stück (beide Seiten zusammen): $sides")
        }
        appendLine()
        appendLine("## Gegnertypen")
        if (enemies.isEmpty()) appendLine("- (keine – verwende keine Gegnergruppen)")
        for (e in enemies) appendLine("- Gegnertyp-ID `${e.id}`: ${e.name} (Gesundheit ${e.health}, Rüstung ${e.armor}, Schutzzauber ${e.sorcery}" + (if (e.ranged) ", Fernkampf" else "") + ")")
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
        appendLine("- Jedes Kapitel bekommt Landschafts-Kategorien, die zu den verfügbaren Kacheln bzw. Schlachtplänen passen.")
        appendLine("- Kartenart: " + mapInstruction())
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
        appendLine("- Genau ${draft.chapters.size} Kapitel in der Reihenfolge des Entwurfs; pro Reise-Kapitel 1–3 Kacheln, deren Landschaft zum Kapitel passt.")
        val battles = draft.chapters.mapIndexedNotNull { i, c -> (i + 1).takeIf { c.battle } }
        if (battles.isNotEmpty()) {
            appendLine("- Schlacht-Kapitel (Nr. ${battles.joinToString(", ")}): statt Kacheln ein battlefield mit 1–2 Schlachtplänen und passenden Geländeteilen (siehe Regeln 10–14); Token und Gegner dort mit {\"square\": …}.")
        }
        appendLine("- Jede Kachel höchstens einmal im ganzen Szenario.")
        appendLine("- Am Ende liegen alle Kapitel gleichzeitig auf einem Tisch von ${input.table}: alle Kacheln zusammen höchstens etwa ${input.table.cellCapacity(catalog.hexSizeMm)} Felder (Felder je Kachel siehe Material).")
        appendLine("- Pro Kapitel 2–5 Token mit eigenen Texten; Proben und Auswahlen sorgen für Abwechslung.")
        appendLine("- Reise-Kapitel: Die erste Kachel des Startkapitels ist erforscht (Startpunkt), weitere Kacheln meist \"unexplored\": true. Lege interessante Token und Gegner bevorzugt auf unerforschte Kacheln; gelegentlich ein kurzes onExplore-Event.")
        appendLine("- Fehlgeschlagene Proben (onFailure) haben passende Folgen für die Helden: körperliche Gefahren (Sturz, Falle, Kampf) → sufferHarm mit damage, Unheimliches oder Schreckliches → fear, meist 1–2 für den handelnden Held; manchmal auch ein Gegner, der erscheint, oder ein verpasster Hinweis.")
        appendLine("- Die Bedrohung steigt normalerweise nur am Rundenende (threat.perRound). changeThreat in Events nur in seltenen Ausnahmen (z. B. ein Alarm, der den Feind weckt) – höchstens ein- bis zweimal im ganzen Szenario, nie als Standardfolge einer Probe.")
        appendLine("- Jede Auflösung hat eine Belohnung: \"xp\" (EP für jeden Helden; Sieg meist 2–4, Niederlage 1–2) und \"lore\" (Wissen der Gruppe; Sieg 2–5, Niederlage 0–2). Zusätzlich 1–2 Mal pro Szenario gainLore (1–2) für das Lüften eines Geheimnisses oder einen wichtigen Fund.")
        appendLine("- Kampagnen-Merker (ID-Präfix fl_) halten Ergebnisse fest, die spätere Szenarien verändern können (z. B. fl_mueller_gerettet). Setze sie über resolution.setFlags oder setFlag, wenn ein Ausgang für die Fortsetzung wichtig ist." +
            if (input.campaignFlags.isEmpty()) "" else " Bestehende Merker der Kampagne: " + input.campaignFlags.joinToString(", ") { "${it.id} („${it.label}“)" } + " – nutze checkFlag, damit dieses Szenario darauf reagiert (z. B. andere Begrüßung, zusätzlicher Verbündeter oder Gegner).")
        appendLine("- Kachel-Symbole nutzen: Dunkelheit-Felder für bedrückende Orte (Furcht in der Schattenphase), befestigte Felder für Verteidigung oder Wachposten, unwegsame Felder für mühsames Gelände. Unterirdische Kacheln (Höhlen, Gewölbe) brauchen eine Verbindung nach draußen; die App legt sie so an, dass Durchgänge (Pfeile) anschließen.")
        appendLine("- Schattenphase: Am Ende jeder Runde greifen aktive Gegner an (die App würfelt Ziel und Angriff), danach wirkt Dunkelheit, dann steigt die Bedrohung. Dazu zieht die App jede Runde ein Event aus scenario.shadowEvents: lege 4–8 eigene Events an – etwa die Hälfte harmlos oder stimmungsvoll (ein Geräusch, eine ruhige Nacht, ein kleiner Fund, Inspiration), die anderen mit Folgen (Schaden/Furcht, Gegner erscheinen, selten Bedrohung). Für dunkle Orte (Höhlen, verfluchte Wälder) setDarkness im onUnlock des Kapitels.")
        appendLine("- Belohne mit gainInspiration: nach bestandenen Proben (im onSuccess-Event), gelungenen Interaktionen, erfüllten Zielen oder besiegten Gegnern – sparsam, etwa 1–3 Mal pro Kapitel.")
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

    private fun mapInstruction(): String = when (input.map) {
        GenerationInput.MapChoice.TRAVEL -> "alle Kapitel sind Reise-Kapitel (battle = false)."
        GenerationInput.MapChoice.BATTLE -> "alle Kapitel sind Schlacht-Kapitel (battle = true)."
        GenerationInput.MapChoice.AUTO -> "entscheide pro Kapitel. Spielt es an EINEM Ort (Verteidigung, Überfall, Kampf in einer Halle oder auf einer Lichtung) → Schlacht (battle = true). Geht es um Erkundung, Reise, Flucht, Suche über mehrere Orte → Reise (battle = false). Begründe kurz in mapReason." +
            if (boards.isEmpty()) " Es gibt keine Schlachtpläne, also nur Reise." else ""
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
- Kachel-Platzierungen mit "unexplored": true liegen zunächst ausgegraut mit Erkundungsmarker; ihre Token erscheinen erst, wenn die Gruppe die Kachel erkundet. Dabei erhält ein Held automatisch 1 Inspiration, dann läuft das optionale Event "onExplore" und Trigger vom Typ tileExplored (placement) feuern.

Schritte eines Events: text, choice (Auswahl, ≥2 Optionen, jede führt in ein Event), test (Probe: attribute might/agility/wisdom/spirit/wit, successes = benötigte Erfolge, onSuccess/onFailure = Events), spawnEnemies, changeThreat (amount ≠ 0), addToken, removeToken, unlockChapter, setObjective, fireTrigger (nur Trigger vom Typ manual), gainLore (Wissen der Gruppe +amount, 1–10), setDarkness (fear 0–3: Furcht für Helden in Dunkelheit in jeder Schattenphase; 0 = aus), setFlag (Kampagnen-Merker setzen), checkFlag (Verzweigung nach Merker: ifSet/ifNotSet = Events; nur als letzter Schritt), gainInspiration (Belohnung: amount 1–3, who = active/each/chosen, optional text), sufferHarm (damage 0–5 und/oder fear 0–5, who = active/each/chosen, hidden = verdeckt, optional text), endScenario.

Pflichtregeln (werden automatisch geprüft):
1. choice, test und endScenario dürfen nur als LETZTER Schritt eines Events stehen. Fortsetzungen gehören in eigene Events.
2. Jeder Verweis zeigt auf ein existierendes Objekt des richtigen Typs.
3. Jedes Ziel muss erfüllbar sein: sein completedBy-Trigger muss auf einem Weg auslösbar sein, während das Ziel aktiv ist.
4. Es gibt einen Trigger vom Typ threatMax, der in ein Event mit endScenario (Niederlage) führt. Ein Sieg-Ende muss erreichbar sein.
5. Token mit initiallyActive=false erscheinen nur durch addToken – dieser Schritt muss irgendwo vorkommen.
6. In Reise-Kapiteln müssen Token- und Gegner-Felder (location.cell) zu den Feldern der gewählten Kachelseite gehören (siehe Material). Verwende nur die angegebenen Kachel-IDs und Gegnertyp-IDs.
7. Jede Kachel höchstens einmal. Kachel-Platzierungen nennen nur Kachel und Seite – die Anordnung auf der Karte berechnet die App.
8. Gegnergruppen brauchen eine Anzahl für 1, 2, 3, 4 und 5 Helden.
9. Kein Text darf leer sein (Titel, Einleitung, Ereignistexte, Erinnerungstexte, Auflösungstexte).

Schlacht-Kapitel (Kapitel mit "battlefield", ohne "tiles"):
10. battlefield.boards: Schlachtpläne mit ID-Präfix bp_, Plan-ID, Seite und position in GANZEN Plänen ({"x":0,"y":0}, daneben {"x":1,"y":0}); rotation 0–3. Normalerweise ZWEI Pläne nebeneinander, damit es genug Felder gibt. Nie mehr Pläne als vorhanden – ein späteres Schlacht-Kapitel mit "clearBattlefield": true räumt die bisherigen Pläne und Teile ab, danach können sie neu gelegt werden.
11. Raster: Alle Pläne bilden ein gemeinsames Quadratraster (x nach rechts, y nach unten); Quadrat (x,y) des Plans an position (px,py) mit Breite w, Höhe h ist (px·w + x, py·h + y). Ein Feld ist eine Gruppe von Quadraten (siehe Material).
12. battlefield.terrain: Geländeteile mit ID-Präfix gt_, Teil-ID und Seite. Teile „im Feld“ mit "area": ein Quadrat des Feldes. Teile „auf Feldgrenze“ mit "edge": {"x","y","side": north/east/south/west} – die Kante muss zwischen zwei VERSCHIEDENEN Feldern liegen, höchstens ein Teil pro Kante. Nie mehr Teile einer Teil-ID als vorhanden (beide Seiten zählen zusammen).
13. Token und Gegner stehen in Schlacht-Kapiteln mit "location": {"square": {"x","y"}} auf einem Quadrat eines Plans.
14. Geländeteile dürfen im Szenario eine eigene Bedeutung bekommen: "customName" (z. B. „Altar der Schatten“) und "customRule": {"mode": "replace" oder "add", "text": eigene kurze Regel}. Nutze das sparsam und passend zur Geschichte."""
    }
}
