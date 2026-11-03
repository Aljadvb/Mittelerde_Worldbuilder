package io.github.aljadvb.worldbuilder.validation

import io.github.aljadvb.worldbuilder.model.placementId
import io.github.aljadvb.worldbuilder.model.CURRENT_FORMAT_VERSION
import io.github.aljadvb.worldbuilder.model.Campaign
import io.github.aljadvb.worldbuilder.model.EventStep
import io.github.aljadvb.worldbuilder.model.HexCoord
import io.github.aljadvb.worldbuilder.model.MaterialCatalog
import io.github.aljadvb.worldbuilder.model.Scenario
import io.github.aljadvb.worldbuilder.model.Location
import io.github.aljadvb.worldbuilder.model.SquareLocation
import io.github.aljadvb.worldbuilder.model.TileLocation
import io.github.aljadvb.worldbuilder.model.battleMap
import io.github.aljadvb.worldbuilder.model.isUnderground
import io.github.aljadvb.worldbuilder.model.battleSegmentOf
import io.github.aljadvb.worldbuilder.model.TokenType
import io.github.aljadvb.worldbuilder.model.TriggerCondition

/**
 * Validates a campaign against the format rules and the user's material catalog.
 * Returns all findings at once; nothing is thrown.
 */
object CampaignValidator {
    fun validate(campaign: Campaign, catalog: MaterialCatalog): ValidationReport {
        val c = IssueCollector()
        CampaignChecks(campaign, catalog, c).run()
        campaign.scenarios.forEachIndexed { i, scenario -> Reachability.check(scenario, "scenarios[$i]", c) }
        return ValidationReport(c.issues)
    }
}

private class CampaignChecks(
    private val campaign: Campaign,
    private val catalog: MaterialCatalog,
    private val c: IssueCollector,
) {
    private data class Entry(val kind: ObjectKind, val path: String, val scenarioIndex: Int?)

    private val index = mutableMapOf<String, Entry>()

    fun run() {
        if (campaign.formatVersion != CURRENT_FORMAT_VERSION) {
            c.error(IssueCode.UNSUPPORTED_FORMAT_VERSION, "formatVersion", null,
                "Formatversion ${campaign.formatVersion} wird nicht unterstützt (erwartet: $CURRENT_FORMAT_VERSION).")
        }
        buildIndex()
        blank(campaign.title, "title", campaign.id, "Titel der Kampagne")
        ref(campaign.startScenario, ObjectKind.SCENARIO, null, "startScenario", campaign.id, "Startszenario")
        checkTransitions()
        checkPlacesAndFlags()
        campaign.scenarios.forEachIndexed { i, s -> checkScenario(s, i, "scenarios[$i]") }
    }

    // ---- IDs -------------------------------------------------------------------------

    private fun register(id: String, kind: ObjectKind, path: String, scenarioIndex: Int?) {
        val idPath = "$path.id"
        if (!ID_PATTERN.matches(id)) {
            c.error(IssueCode.INVALID_ID, idPath, id, "Ungültige ID '$id': nur Kleinbuchstaben, Ziffern und '_', beginnend mit Buchstabe.")
        } else if (!id.startsWith(kind.prefix)) {
            c.warn(IssueCode.ID_PREFIX, idPath, id, "ID '$id' (${kind.displayName}) sollte mit '${kind.prefix}' beginnen.")
        }
        val existing = index[id]
        if (existing != null) {
            c.error(IssueCode.DUPLICATE_ID, idPath, id, "ID '$id' ist doppelt vergeben (bereits bei ${existing.path}).")
        } else {
            index[id] = Entry(kind, path, scenarioIndex)
        }
    }

    private fun buildIndex() {
        register(campaign.id, ObjectKind.CAMPAIGN, "", null)
        campaign.places.forEachIndexed { i, p -> register(p.id, ObjectKind.PLACE, "places[$i]", null) }
        campaign.flags.forEachIndexed { i, f -> register(f.id, ObjectKind.FLAG, "flags[$i]", null) }
        campaign.scenarios.forEachIndexed { si, s ->
            val sp = "scenarios[$si]"
            register(s.id, ObjectKind.SCENARIO, sp, si)
            s.chapters.forEachIndexed { ci, ch ->
                val cp = "$sp.chapters[$ci]"
                register(ch.id, ObjectKind.CHAPTER, cp, si)
                ch.tiles.forEachIndexed { pi, p -> register(p.id, ObjectKind.PLACEMENT, "$cp.tiles[$pi]", si) }
                ch.tokens.forEachIndexed { ti, t -> register(t.id, ObjectKind.TOKEN, "$cp.tokens[$ti]", si) }
                ch.battlefield?.boards?.forEachIndexed { bi, b -> register(b.id, ObjectKind.BOARD_PLACEMENT, "$cp.battlefield.boards[$bi]", si) }
                ch.battlefield?.terrain?.forEachIndexed { gi, g -> register(g.id, ObjectKind.TERRAIN_PLACEMENT, "$cp.battlefield.terrain[$gi]", si) }
            }
            s.enemyGroups.forEachIndexed { i, g -> register(g.id, ObjectKind.ENEMY_GROUP, "$sp.enemyGroups[$i]", si) }
            s.events.forEachIndexed { i, e -> register(e.id, ObjectKind.EVENT, "$sp.events[$i]", si) }
            s.triggers.forEachIndexed { i, t -> register(t.id, ObjectKind.TRIGGER, "$sp.triggers[$i]", si) }
            s.objectives.forEachIndexed { i, o -> register(o.id, ObjectKind.OBJECTIVE, "$sp.objectives[$i]", si) }
            s.resolutions.forEachIndexed { i, r -> register(r.id, ObjectKind.RESOLUTION, "$sp.resolutions[$i]", si) }
        }
    }

    /** Checks a reference; returns true if it resolves to the expected kind (in the expected scenario). */
    private fun ref(id: String, expected: ObjectKind, scenarioIndex: Int?, path: String, ownerId: String?, what: String): Boolean {
        val entry = index[id]
        when {
            entry == null -> c.error(IssueCode.UNKNOWN_REFERENCE, path, ownerId,
                "$what: Verweis auf unbekannte ID '$id' (erwartet: ${expected.displayName}).")
            entry.kind != expected -> c.error(IssueCode.WRONG_REFERENCE_TYPE, path, ownerId,
                "$what: '$id' ist ein(e) ${entry.kind.displayName}, erwartet wird ein(e) ${expected.displayName}.")
            scenarioIndex != null && entry.scenarioIndex != scenarioIndex -> c.error(IssueCode.REFERENCE_TO_OTHER_SCENARIO, path, ownerId,
                "$what: '$id' gehört zu einem anderen Szenario.")
            else -> return true
        }
        return false
    }

    private fun blank(text: String, path: String, ownerId: String?, what: String) {
        if (text.isBlank()) c.error(IssueCode.BLANK_TEXT, path, ownerId, "$what ist leer.")
    }

    // ---- Campaign --------------------------------------------------------------------

    private fun checkTransitions() {
        val seen = mutableSetOf<List<String?>>()
        campaign.transitions.forEachIndexed { i, t ->
            val path = "transitions[$i]"
            val fromOk = ref(t.from, ObjectKind.SCENARIO, null, "$path.from", null, "Übergang (von)")
            ref(t.to, ObjectKind.SCENARIO, null, "$path.to", null, "Übergang (nach)")
            if (fromOk) {
                val fromIndex = index.getValue(t.from).scenarioIndex
                ref(t.resolution, ObjectKind.RESOLUTION, fromIndex, "$path.resolution", null, "Übergang (Auflösung von '${t.from}')")
            }
            t.requiresFlag?.let { ref(it, ObjectKind.FLAG, null, "$path.requiresFlag", null, "Übergang (Bedingung)") }
            // Several transitions to different scenarios are an either-or choice; the same one twice is a mistake.
            if (!seen.add(listOf(t.from, t.resolution, t.requiresFlag, t.to))) {
                c.error(IssueCode.DUPLICATE_TRANSITION, path, null,
                    "Der Übergang von '${t.from}' (Auflösung '${t.resolution}') nach '${t.to}'" + (t.requiresFlag?.let { " mit Merker '$it'" } ?: "") + " ist doppelt.")
            }
        }
        // Scenarios that can never be played
        val reachable = mutableSetOf(campaign.startScenario)
        var changed = true
        while (changed) {
            changed = campaign.transitions.filter { it.from in reachable }.any { reachable.add(it.to) }
        }
        campaign.scenarios.forEachIndexed { i, s ->
            if (s.id !in reachable) {
                c.warn(IssueCode.UNREACHABLE, "scenarios[$i]", s.id, "Szenario '${s.id}' ist über keinen Übergang erreichbar.")
            }
        }
    }

    private fun checkPlacesAndFlags() {
        campaign.places.forEachIndexed { i, p ->
            blank(p.name, "places[$i].name", p.id, "Name des Orts")
            if (p.x !in 0.0..1.0 || p.y !in 0.0..1.0) c.error(IssueCode.INVALID_NUMBER, "places[$i]", p.id, "Ort '${p.id}' liegt außerhalb der Karte (x und y je 0–1).")
        }
        campaign.flags.forEachIndexed { i, f -> blank(f.label, "flags[$i].label", f.id, "Bezeichnung des Merkers") }
        val steps = campaign.scenarios.flatMap { s -> s.events.flatMap { it.steps } }
        val set = steps.filterIsInstance<io.github.aljadvb.worldbuilder.model.EventStep.SetFlag>().map { it.flag } +
            campaign.scenarios.flatMap { s -> s.resolutions.flatMap { it.setFlags } }
        val used = steps.filterIsInstance<io.github.aljadvb.worldbuilder.model.EventStep.CheckFlag>().map { it.flag } + campaign.transitions.mapNotNull { it.requiresFlag }
        campaign.flags.forEachIndexed { i, f ->
            if (f.id in used && f.id !in set) c.warn(IssueCode.FLAG_NEVER_SET, "flags[$i]", f.id, "Merker '${f.label}' wird abgefragt, aber nirgends gesetzt.")
            if (f.id in set && f.id !in used) c.warn(IssueCode.FLAG_NEVER_USED, "flags[$i]", f.id, "Merker '${f.label}' wird gesetzt, aber nirgends abgefragt.")
        }
    }

    // ---- Scenario --------------------------------------------------------------------

    private fun checkScenario(s: Scenario, si: Int, sp: String) {
        blank(s.title, "$sp.title", s.id, "Titel des Szenarios")
        blank(s.intro, "$sp.intro", s.id, "Einleitungstext")
        if (s.threat.max < 1) c.error(IssueCode.INVALID_NUMBER, "$sp.threat.max", s.id, "Bedrohungsmaximum muss mindestens 1 sein.")
        if (s.threat.perRound.base < 0 || s.threat.perRound.perHero < 0) {
            c.error(IssueCode.INVALID_NUMBER, "$sp.threat.perRound", s.id, "Bedrohung pro Runde darf nicht negativ sein.")
        }
        ref(s.startChapter, ObjectKind.CHAPTER, si, "$sp.startChapter", s.id, "Startkapitel")
        s.shadowEvents.forEachIndexed { i, e -> ref(e, ObjectKind.EVENT, si, "$sp.shadowEvents[$i]", s.id, "Schattenereignis") }
        s.place?.let { ref(it, ObjectKind.PLACE, null, "$sp.place", s.id, "Ort auf der Kampagnenkarte") }
        ref(s.startObjective, ObjectKind.OBJECTIVE, si, "$sp.startObjective", s.id, "Startziel")

        checkMap(s, si, sp)
        checkEnemyGroups(s, si, sp)
        s.events.forEachIndexed { i, e -> checkEvent(s, si, "$sp.events[$i]", e.id, e.steps) }
        checkTriggers(s, si, sp)
        s.objectives.forEachIndexed { i, o ->
            val op = "$sp.objectives[$i]"
            blank(o.title, "$op.title", o.id, "Titel des Ziels")
            blank(o.reminder, "$op.reminder", o.id, "Erinnerungstext des Ziels")
            ref(o.completedBy, ObjectKind.TRIGGER, si, "$op.completedBy", o.id, "Ziel '${o.id}' (completedBy)")
        }
        s.resolutions.forEachIndexed { i, r ->
            blank(r.title, "$sp.resolutions[$i].title", r.id, "Titel der Auflösung")
            blank(r.text, "$sp.resolutions[$i].text", r.id, "Text der Auflösung")
            if (r.lore < 0 || r.xp < 0) c.error(IssueCode.INVALID_NUMBER, "$sp.resolutions[$i]", r.id, "Wissen und EP einer Auflösung dürfen nicht negativ sein.")
            r.setFlags.forEachIndexed { fi, f -> ref(f, ObjectKind.FLAG, null, "$sp.resolutions[$i].setFlags[$fi]", r.id, "Auflösung '${r.id}' (Merker)") }
        }
    }

    /** Underground tiles need at least one passage to the rest of the map, or heroes cannot get in and out. */
    private fun checkPassages(s: Scenario, sp: String) {
        val all = s.placements
        if (all.size < 2) return
        val links = io.github.aljadvb.worldbuilder.model.connections(all, catalog)
        s.chapters.forEachIndexed { ci, ch ->
            ch.tiles.forEachIndexed { pi, p ->
                if (!p.isUnderground(catalog)) return@forEachIndexed
                if (links.keys.none { p.id in it.toList() }) {
                    c.warn(IssueCode.NO_PASSAGE, "$sp.chapters[$ci].tiles[$pi]", p.id,
                        "Kachel '${p.id}' ist unterirdisch und hat keinen Durchgang (Pfeil) zu einer anderen Kachel – die Helden kommen nicht hinein oder heraus.")
                }
            }
        }
    }

    private fun checkExpansionOwned(expansionId: String, path: String, ownerId: String, what: String) {
        val expansion = catalog.expansion(expansionId)
        if (expansion != null && !expansion.owned) {
            c.warn(IssueCode.EXPANSION_NOT_OWNED, path, ownerId, "$what stammt aus '${expansion.name}', die nicht als vorhanden markiert ist.")
        }
    }

    /** Tile-local cells of a placement, or null if tile/side are unknown. */
    private fun shapeOf(s: Scenario, placementId: String): List<HexCoord>? {
        val placement = s.placement(placementId) ?: return null
        return catalog.tile(placement.tile)?.sides?.get(placement.side)?.cells
    }

    private fun checkMap(s: Scenario, si: Int, sp: String) {
        s.chapter(s.startChapter)?.let { start ->
            if (start.tiles.isNotEmpty() && start.tiles.all { it.unexplored }) {
                c.warn(IssueCode.NO_EXPLORED_START, "$sp.startChapter", s.id, "Alle Kacheln des Startkapitels sind unerforscht – die Helden brauchen eine erforschte Startkachel.")
            }
        }
        BattleChecks(s, sp, catalog, c, ::checkExpansionOwned).run()
        checkPassages(s, sp)
        // Something to do on every travel tile: a token or an enemy group stands on it.
        val used = (s.tokens.map { it.location } + s.enemyGroups.map { it.location }).mapNotNull { it.placementId }.toSet()
        s.chapters.forEachIndexed { ci, ch ->
            ch.tiles.forEachIndexed { pi, p ->
                if (p.id !in used) c.warn(IssueCode.EMPTY_TILE, "$sp.chapters[$ci].tiles[$pi]", p.id,
                    "Auf Kachel '${p.id}' liegt kein Token und steht kein Gegner – dort gibt es nichts zu tun.")
            }
        }
        val occupied = mutableMapOf<HexCoord, String>()
        val tileUse = mutableMapOf<String, String>()
        s.chapters.forEachIndexed { ci, ch ->
            val cp = "$sp.chapters[$ci]"
            blank(ch.title, "$cp.title", ch.id, "Titel des Kapitels")
            ch.onUnlock?.let { ref(it, ObjectKind.EVENT, si, "$cp.onUnlock", ch.id, "Kapitel '${ch.id}' (onUnlock)") }

            ch.tiles.forEachIndexed { pi, p ->
                val pp = "$cp.tiles[$pi]"
                if (p.rotation !in 0..5) {
                    c.error(IssueCode.INVALID_ROTATION, "$pp.rotation", p.id, "Drehung ${p.rotation} ist ungültig (erlaubt: 0–5).")
                }
                val tile = catalog.tile(p.tile)
                if (tile == null) {
                    c.error(IssueCode.UNKNOWN_TILE, "$pp.tile", p.id, "Kachel '${p.tile}' ist nicht im Material-Katalog.")
                    return@forEachIndexed
                }
                checkExpansionOwned(tile.expansion, "$pp.tile", p.id, "Kachel ${tile.label}")
                val shape = tile.sides[p.side]
                if (shape == null) {
                    c.error(IssueCode.UNKNOWN_TILE_SIDE, "$pp.side", p.id, "Kachel ${tile.label} hat im Katalog keine Seite ${p.side}.")
                    return@forEachIndexed
                }
                p.onExplore?.let { ref(it, ObjectKind.EVENT, si, "$pp.onExplore", p.id, "Kachel '${p.id}' (onExplore)") }
                if (p.onExplore != null && !p.unexplored) {
                    c.warn(IssueCode.NOT_UNEXPLORED, "$pp.onExplore", p.id, "Kachel '${p.id}' hat ein Erkundungs-Event, ist aber nicht als unerforscht markiert.")
                }
                tileUse.put(p.tile, p.id)?.let { other ->
                    c.warn(IssueCode.TILE_USED_TWICE, "$pp.tile", p.id, "Kachel ${tile.label} wird bereits von '$other' verwendet.")
                }
                for (cell in shape.cells) {
                    val mapCell = p.toMap(cell)
                    val other = occupied.putIfAbsent(mapCell, p.id)
                    if (other != null) {
                        c.error(IssueCode.TILES_OVERLAP, pp, p.id, "Kachel '${p.id}' überlappt auf Kartenfeld $mapCell mit '$other'.")
                        break
                    }
                }
            }

            ch.tokens.forEachIndexed { ti, t ->
                val tp = "$cp.tokens[$ti]"
                blank(t.label, "$tp.label", t.id, "Bezeichnung des Tokens")
                if (t.type == TokenType.CUSTOM && t.customType.isNullOrBlank()) {
                    c.error(IssueCode.CUSTOM_TYPE_MISSING, "$tp.customType", t.id, "Token vom Typ 'custom' braucht einen 'customType'.")
                }
                if (t.type != TokenType.CUSTOM && t.customType != null) {
                    c.error(IssueCode.CUSTOM_TYPE_NOT_ALLOWED, "$tp.customType", t.id, "'customType' ist nur bei Typ 'custom' erlaubt.")
                }
                t.onInteract?.let { ref(it, ObjectKind.EVENT, si, "$tp.onInteract", t.id, "Token '${t.id}' (onInteract)") }
                checkLocation(s, si, t.location, "$tp.location", t.id, ownerChapter = ch.id)
            }
        }
    }

    private fun checkLocation(s: Scenario, si: Int, location: Location, path: String, ownerId: String, ownerChapter: String?) {
        val chapter = ownerChapter?.let { s.chapter(it) }
        val loc = when (location) {
            is SquareLocation -> {
                if (chapter != null && !chapter.isBattle) {
                    c.error(IssueCode.LOCATION_WRONG_MAP, path, ownerId, "'$ownerId' steht auf einem Schlachtplan-Feld, aber Kapitel '${chapter.id}' ist ein Reise-Kapitel.")
                    return
                }
                val segment = chapter?.let { s.battleSegmentOf(it.id).map { ch -> ch.id }.toSet() }
                val board = s.battleMap(catalog, segment).field(location.square)?.boardPlacement
                if (board == null) {
                    c.error(IssueCode.SQUARE_NOT_ON_MAP, "$path.square", ownerId, "Feld ${location.square} von '$ownerId' liegt auf keinem Schlachtplan.")
                    return
                }
                val boardChapter = s.chapters.firstOrNull { ch -> ch.battlefield?.boards.orEmpty().any { it.id == board } }?.id
                if (ownerChapter != null && boardChapter != ownerChapter && boardChapter != s.startChapter) {
                    c.warn(IssueCode.LOCATION_ON_LATER_CHAPTER, "$path.square", ownerId,
                        "'$ownerId' steht auf einem Schlachtplan aus Kapitel '$boardChapter', das evtl. noch nicht aufgebaut ist.")
                }
                return
            }
            is TileLocation -> location
        }
        if (chapter != null && chapter.isBattle) {
            c.error(IssueCode.LOCATION_WRONG_MAP, path, ownerId, "'$ownerId' steht auf einer Kachel, aber Kapitel '${chapter.id}' ist ein Schlacht-Kapitel (Ort: {\"square\": {\"x\", \"y\"}}).")
            return
        }
        if (!ref(loc.placement, ObjectKind.PLACEMENT, si, "$path.placement", ownerId, "Ort von '$ownerId'")) return
        val shape = shapeOf(s, loc.placement) ?: return // unknown tile is reported at the placement
        if (loc.cell !in shape) {
            c.error(IssueCode.CELL_NOT_ON_TILE, "$path.cell", ownerId,
                "Feld ${loc.cell} existiert auf Platzierung '${loc.placement}' nicht (vorhanden: ${shape.joinToString(", ")}).")
        }
        val placementChapter = s.chapterOfPlacement(loc.placement)?.id
        if (ownerChapter != null && placementChapter != ownerChapter && placementChapter != s.startChapter) {
            c.warn(IssueCode.LOCATION_ON_LATER_CHAPTER, "$path.placement", ownerId,
                "'$ownerId' liegt auf einer Kachel aus Kapitel '$placementChapter', das evtl. noch nicht aufgebaut ist.")
        }
    }

    private fun checkEnemyGroups(s: Scenario, si: Int, sp: String) {
        s.enemyGroups.forEachIndexed { i, g ->
            val gp = "$sp.enemyGroups[$i]"
            val type = catalog.enemyType(g.enemyType)
            if (type == null) {
                c.error(IssueCode.UNKNOWN_ENEMY_TYPE, "$gp.enemyType", g.id, "Gegnertyp '${g.enemyType}' ist nicht im Material-Katalog.")
            } else {
                checkExpansionOwned(type.expansion, "$gp.enemyType", g.id, "Gegnertyp '${type.name}'")
            }
            val missing = (1..5).filter { it !in g.count }
            if (missing.isNotEmpty()) {
                c.error(IssueCode.ENEMY_COUNT_INCOMPLETE, "$gp.count", g.id, "Anzahl fehlt für Heldenzahl ${missing.joinToString(", ")}.")
            }
            g.count.forEach { (heroes, n) ->
                if (heroes !in 1..5 || n < 0) c.error(IssueCode.INVALID_NUMBER, "$gp.count", g.id, "Ungültiger Eintrag $heroes → $n (Heldenzahl 1–5, Anzahl ≥ 0).")
            }
            checkLocation(s, si, g.location, "$gp.location", g.id, ownerChapter = null)
        }
        // Start field of the heroes: on the start chapter, on an explored tile.
        // Without one the app uses the anchor field of the start chapter's first tile.
        val start = s.heroStart
        if (start != null) {
            checkLocation(s, si, start, "$sp.heroStart", s.id, ownerChapter = s.startChapter)
            start.placementId?.let { pl -> if (s.placement(pl)?.unexplored == true) c.error(IssueCode.LOCATION_ON_LATER_CHAPTER, "$sp.heroStart", s.id, "Das Startfeld der Helden liegt auf einer unerforschten Kachel.") }
        }
    }

    private fun checkEvent(s: Scenario, si: Int, ep: String, eventId: String, steps: List<EventStep>) {
        if (steps.isEmpty()) {
            c.error(IssueCode.EMPTY_EVENT, "$ep.steps", eventId, "Event '$eventId' hat keine Schritte.")
        }
        steps.forEachIndexed { i, step ->
            val p = "$ep.steps[$i]"
            if (step.isTerminal && i != steps.lastIndex) {
                c.error(IssueCode.TERMINAL_STEP_NOT_LAST, p, eventId,
                    "Schritt ${i + 1} (${stepName(step)}) muss der letzte Schritt des Events sein; Fortsetzung bitte in einem eigenen Event.")
            }
            val what = "Event '$eventId', Schritt ${i + 1}"
            when (step) {
                is EventStep.Text -> blank(step.text, "$p.text", eventId, "$what: Text")
                is EventStep.Choice -> {
                    blank(step.text, "$p.text", eventId, "$what: Text")
                    if (step.options.size < 2) {
                        c.error(IssueCode.CHOICE_TOO_FEW_OPTIONS, "$p.options", eventId, "$what: Eine Auswahl braucht mindestens 2 Optionen.")
                    }
                    step.options.forEachIndexed { oi, o ->
                        blank(o.label, "$p.options[$oi].label", eventId, "$what: Beschriftung von Option ${oi + 1}")
                        ref(o.event, ObjectKind.EVENT, si, "$p.options[$oi].event", eventId, "$what, Option ${oi + 1}")
                    }
                }
                is EventStep.Test -> {
                    if (step.successes < 1) c.error(IssueCode.INVALID_NUMBER, "$p.successes", eventId, "$what: Benötigte Erfolge müssen ≥ 1 sein.")
                    ref(step.onSuccess, ObjectKind.EVENT, si, "$p.onSuccess", eventId, "$what (onSuccess)")
                    step.onFailure?.let { ref(it, ObjectKind.EVENT, si, "$p.onFailure", eventId, "$what (onFailure)") }
                    step.onAttempt?.let { ref(it, ObjectKind.EVENT, si, "$p.onAttempt", eventId, "$what (onAttempt)") }
                    if (step.cumulative && step.onFailure != null) {
                        c.warn(IssueCode.CUMULATIVE_FAILURE, "$p.onFailure", eventId, "$what: Eine Sammelprobe scheitert nie – 'onFailure' wird nicht verwendet (für jeden Versuch gibt es 'onAttempt').")
                    }
                    if (!step.cumulative && step.onAttempt != null) {
                        c.warn(IssueCode.CUMULATIVE_FAILURE, "$p.onAttempt", eventId, "$what: 'onAttempt' wirkt nur bei Sammelproben.")
                    }
                }
                is EventStep.SpawnEnemies -> ref(step.group, ObjectKind.ENEMY_GROUP, si, "$p.group", eventId, what)
                is EventStep.ChangeThreat -> if (step.amount == 0) {
                    c.warn(IssueCode.INVALID_NUMBER, "$p.amount", eventId, "$what: Bedrohungsänderung um 0 hat keine Wirkung.")
                }
                is EventStep.AddToken -> ref(step.token, ObjectKind.TOKEN, si, "$p.token", eventId, what)
                is EventStep.RemoveToken -> ref(step.token, ObjectKind.TOKEN, si, "$p.token", eventId, what)
                is EventStep.UnlockChapter -> ref(step.chapter, ObjectKind.CHAPTER, si, "$p.chapter", eventId, what)
                is EventStep.SetObjective -> ref(step.objective, ObjectKind.OBJECTIVE, si, "$p.objective", eventId, what)
                is EventStep.FireTrigger -> if (ref(step.trigger, ObjectKind.TRIGGER, si, "$p.trigger", eventId, what)) {
                    if (s.trigger(step.trigger)?.on != TriggerCondition.Manual) {
                        c.error(IssueCode.FIRE_NON_MANUAL_TRIGGER, "$p.trigger", eventId,
                            "$what: 'fireTrigger' darf nur Trigger vom Typ 'manual' auslösen ('${step.trigger}' ist es nicht).")
                    }
                }
                is EventStep.EndScenario -> ref(step.resolution, ObjectKind.RESOLUTION, si, "$p.resolution", eventId, what)
                is EventStep.SetFlag -> ref(step.flag, ObjectKind.FLAG, null, "$p.flag", eventId, what)
                is EventStep.SetDarkness -> if (step.fear !in 0..3) c.error(IssueCode.INVALID_NUMBER, "$p.fear", eventId, "$what: Furcht durch Dunkelheit muss 0–3 betragen.")
                is EventStep.CheckFlag -> {
                    ref(step.flag, ObjectKind.FLAG, null, "$p.flag", eventId, what)
                    ref(step.ifSet, ObjectKind.EVENT, si, "$p.ifSet", eventId, "$what (ifSet)")
                    step.ifNotSet?.let { ref(it, ObjectKind.EVENT, si, "$p.ifNotSet", eventId, "$what (ifNotSet)") }
                }
                is EventStep.GainLore -> {
                    if (step.amount !in 1..10) c.error(IssueCode.INVALID_NUMBER, "$p.amount", eventId, "$what: Wissen muss 1–10 betragen.")
                    step.text?.let { blank(it, "$p.text", eventId, "$what: Text") }
                }
                is EventStep.SufferHarm -> {
                    if (step.damage !in 0..5 || step.fear !in 0..5 || step.damage + step.fear == 0) {
                        c.error(IssueCode.INVALID_NUMBER, p, eventId, "$what: Schaden und Furcht je 0–5, zusammen mindestens 1.")
                    }
                    step.text?.let { blank(it, "$p.text", eventId, "$what: Text") }
                }
                is EventStep.Inscription -> {
                    blank(step.elvish, "$p.elvish", eventId, "$what: elbischer Text")
                    blank(step.translation, "$p.translation", eventId, "$what: Übersetzung")
                    step.text?.let { blank(it, "$p.text", eventId, "$what: Text") }
                }
                is EventStep.AffectEnemy -> {
                    ref(step.group, ObjectKind.ENEMY_GROUP, si, "$p.group", eventId, what)
                    if (step.hits !in 0..20) c.error(IssueCode.INVALID_NUMBER, "$p.hits", eventId, "$what: Treffer müssen 0–20 betragen.")
                    if (step.hits == 0 && !step.stun && !step.sunder) c.warn(IssueCode.INVALID_NUMBER, p, eventId, "$what: Ohne Treffer, Betäuben oder Zerfallen hat der Schritt keine Wirkung.")
                    step.text?.let { blank(it, "$p.text", eventId, "$what: Text") }
                }
                is EventStep.Recover -> {
                    if (step.damage !in 0..5 || step.fear !in 0..5 || step.damage + step.fear == 0) {
                        c.error(IssueCode.INVALID_NUMBER, p, eventId, "$what: Schaden und Furcht je 0–5, zusammen mindestens 1.")
                    }
                    step.text?.let { blank(it, "$p.text", eventId, "$what: Text") }
                }
                is EventStep.GainItem -> {
                    val item = step.item
                    if (item != null) {
                        val def = catalog.item(item)
                        if (def == null) c.error(IssueCode.UNKNOWN_ITEM, "$p.item", eventId, "$what: Gegenstand '$item' ist nicht im Material-Katalog.")
                        else checkExpansionOwned(def.expansion, "$p.item", eventId, "Gegenstand '${def.name}'")
                    } else if (catalog.items.isEmpty()) {
                        c.warn(IssueCode.NO_ITEMS, p, eventId, "$what: Der Katalog enthält keine Schmuckstücke – die Helden finden nichts.")
                    }
                    step.text?.let { blank(it, "$p.text", eventId, "$what: Text") }
                }
                is EventStep.GainInspiration -> {
                    if (step.amount !in 1..3) c.error(IssueCode.INVALID_NUMBER, "$p.amount", eventId, "$what: Inspiration muss 1–3 betragen.")
                    step.text?.let { blank(it, "$p.text", eventId, "$what: Text") }
                }
            }
        }
    }

    private fun checkTriggers(s: Scenario, si: Int, sp: String) {
        s.triggers.forEachIndexed { i, t ->
            val tp = "$sp.triggers[$i]"
            val what = "Trigger '${t.id}'"
            when (val on = t.on) {
                is TriggerCondition.TokenInteracted -> ref(on.token, ObjectKind.TOKEN, si, "$tp.on.token", t.id, what)
                is TriggerCondition.EnemyGroupDefeated -> ref(on.group, ObjectKind.ENEMY_GROUP, si, "$tp.on.group", t.id, what)
                is TriggerCondition.ObjectiveCompleted -> ref(on.objective, ObjectKind.OBJECTIVE, si, "$tp.on.objective", t.id, what)
                is TriggerCondition.ThreatReached -> when {
                    on.value < 1 -> c.error(IssueCode.INVALID_NUMBER, "$tp.on.value", t.id, "$what: Schwelle muss ≥ 1 sein.")
                    on.value > s.threat.max -> c.warn(IssueCode.THREAT_THRESHOLD_ABOVE_MAX, "$tp.on.value", t.id,
                        "$what: Schwelle ${on.value} liegt über dem Bedrohungsmaximum ${s.threat.max}.")
                }
                is TriggerCondition.RoundEnded -> if (on.round < 1) {
                    c.error(IssueCode.INVALID_NUMBER, "$tp.on.round", t.id, "$what: Rundennummer muss ≥ 1 sein.")
                }
                is TriggerCondition.TileExplored -> if (ref(on.placement, ObjectKind.PLACEMENT, si, "$tp.on.placement", t.id, what)) {
                    if (s.placement(on.placement)?.unexplored != true) {
                        c.warn(IssueCode.NOT_UNEXPLORED, "$tp.on.placement", t.id, "$what: Kachel '${on.placement}' ist nicht als unerforscht markiert und wird daher nie erkundet.")
                    }
                }
                TriggerCondition.ThreatMax, TriggerCondition.Manual -> Unit
            }
            t.events.forEachIndexed { ei, e -> ref(e, ObjectKind.EVENT, si, "$tp.events[$ei]", t.id, what) }
        }
    }
}

internal fun stepName(step: EventStep): String = when (step) {
    is EventStep.Text -> "text"
    is EventStep.Choice -> "choice"
    is EventStep.Test -> "test"
    is EventStep.SpawnEnemies -> "spawnEnemies"
    is EventStep.ChangeThreat -> "changeThreat"
    is EventStep.AddToken -> "addToken"
    is EventStep.RemoveToken -> "removeToken"
    is EventStep.UnlockChapter -> "unlockChapter"
    is EventStep.SetObjective -> "setObjective"
    is EventStep.FireTrigger -> "fireTrigger"
    is EventStep.GainInspiration -> "gainInspiration"
    is EventStep.SetFlag -> "setFlag"
    is EventStep.SetDarkness -> "setDarkness"
    is EventStep.CheckFlag -> "checkFlag"
    is EventStep.GainLore -> "gainLore"
    is EventStep.SufferHarm -> "sufferHarm"
    is EventStep.GainItem -> "gainItem"
    is EventStep.Recover -> "recover"
    is EventStep.AffectEnemy -> "affectEnemy"
    is EventStep.Inscription -> "inscription"
    is EventStep.EndScenario -> "endScenario"
}
