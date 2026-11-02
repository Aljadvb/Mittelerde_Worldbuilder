package io.github.aljadvb.worldbuilder.editor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.aljadvb.worldbuilder.editing.updatePlacement
import io.github.aljadvb.worldbuilder.editing.Definition
import io.github.aljadvb.worldbuilder.editing.addChapter
import io.github.aljadvb.worldbuilder.editing.addEnemyGroup
import io.github.aljadvb.worldbuilder.editing.addEvent
import io.github.aljadvb.worldbuilder.editing.addObjective
import io.github.aljadvb.worldbuilder.editing.addResolution
import io.github.aljadvb.worldbuilder.editing.addScenario
import io.github.aljadvb.worldbuilder.editing.addTrigger
import io.github.aljadvb.worldbuilder.editing.scenarioOf
import io.github.aljadvb.worldbuilder.editing.updateChapter
import io.github.aljadvb.worldbuilder.editing.updateEnemyGroup
import io.github.aljadvb.worldbuilder.editing.updateObjective
import io.github.aljadvb.worldbuilder.editing.updateResolution
import io.github.aljadvb.worldbuilder.editing.updateScenario
import io.github.aljadvb.worldbuilder.editing.updateToken
import io.github.aljadvb.worldbuilder.editing.updateTrigger
import io.github.aljadvb.worldbuilder.editor.EditorModel
import io.github.aljadvb.worldbuilder.editor.Texts
import io.github.aljadvb.worldbuilder.model.Campaign
import io.github.aljadvb.worldbuilder.model.HexCoord
import io.github.aljadvb.worldbuilder.model.Outcome
import io.github.aljadvb.worldbuilder.model.Scenario
import io.github.aljadvb.worldbuilder.model.ScenarioTransition
import io.github.aljadvb.worldbuilder.model.Location
import io.github.aljadvb.worldbuilder.model.TileLocation
import io.github.aljadvb.worldbuilder.model.TokenType
import io.github.aljadvb.worldbuilder.model.TriggerCondition
import io.github.aljadvb.worldbuilder.validation.ObjectKind

/** A pending "create object" request: asks for a name, then runs [create]. */
private class CreateRequest(val title: String, val create: (String) -> Unit)

@Composable
fun ObjectForm(model: EditorModel, def: Definition) {
    val campaign = model.campaign ?: return
    var request by remember { mutableStateOf<CreateRequest?>(null) }
    val ask: (String, (String) -> Unit) -> Unit = { title, create -> request = CreateRequest(title, create) }
    val scenario = if (def.kind == ObjectKind.SCENARIO) campaign.scenario(def.id) else campaign.scenarioOf(def.id)

    when (def.kind) {
        ObjectKind.CAMPAIGN -> CampaignForm(model, campaign, ask)
        ObjectKind.SCENARIO -> ScenarioForm(model, scenario!!, def.path, ask)
        ObjectKind.CHAPTER -> ChapterForm(model, scenario!!, def, ask)
        ObjectKind.OBJECTIVE -> ObjectiveForm(model, scenario!!, def, ask)
        ObjectKind.RESOLUTION -> ResolutionForm(model, scenario!!, def)
        ObjectKind.ENEMY_GROUP -> EnemyGroupForm(model, scenario!!, def)
        ObjectKind.TOKEN -> TokenForm(model, scenario!!, def, ask)
        ObjectKind.TRIGGER -> TriggerForm(model, scenario!!, def, ask)
        ObjectKind.PLACEMENT -> PlacementInfo(model, scenario!!, def)
        ObjectKind.EVENT -> EventForm(model, scenario!!, def, ask)
        ObjectKind.PLACE, ObjectKind.FLAG -> { FieldIssues(model, def.path); CampaignMapEditor(model, campaign, ask); FlagsEditor(model, campaign, ask) }
        ObjectKind.BOARD_PLACEMENT -> BoardForm(model, scenario!!, def)
        ObjectKind.TERRAIN_PLACEMENT -> TerrainForm(model, scenario!!, def)
    }

    request?.let { r ->
        NameDialog(r.title, onConfirm = { name -> request = null; r.create(name) }, onDismiss = { request = null })
    }
}

// ---- helpers ---------------------------------------------------------------------------

private fun Scenario.eventChoices() = events.map { Choice(it.id, it.id) }
private fun Scenario.triggerChoices() = triggers.map { Choice(it.id, it.id) }
private fun Scenario.objectiveChoices() = objectives.map { Choice(it.id, "${it.title} · ${it.id}") }
private fun Scenario.chapterChoices() = chapters.map { Choice(it.id, "${it.title} · ${it.id}") }
private fun Scenario.resolutionChoices() = resolutions.map { Choice(it.id, "${it.title} · ${it.id}") }
private fun Scenario.tokenChoices() = tokens.map { Choice(it.id, "${it.label} · ${it.id}") }
private fun Scenario.groupChoices() = enemyGroups.map { Choice(it.id, it.id) }

/** Creates an object via [create] and then selects or wires it with [then]. */
private fun EditorModel.createThen(create: (Campaign) -> io.github.aljadvb.worldbuilder.editing.Created, then: (String) -> Unit) {
    var newId: String? = null
    if (edit { c -> create(c).also { newId = it.id }.campaign }) newId?.let(then)
}

// ---- campaign ----------------------------------------------------------------------------

@Composable
private fun CampaignForm(model: EditorModel, c: Campaign, ask: (String, (String) -> Unit) -> Unit) {
    TextInput(model, Texts.TITLE, c.title, "${c.id}.title", "title") { v -> { it.copy(title = v) } }
    TextInput(model, Texts.DESCRIPTION, c.description, "${c.id}.description", "description", multiline = true) { v -> { it.copy(description = v) } }
    val scenarioChoices = c.scenarios.map { Choice(it.id, "${it.title} · ${it.id}") }
    ChoiceInput(model, Texts.START_SCENARIO, c.startScenario, scenarioChoices, "startScenario") { v ->
        model.edit { it.copy(startScenario = v.orEmpty()) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = {
            ask(Texts.NEW_SCENARIO) { name -> model.createThen({ it.addScenario(name) }) { model.selection = it } }
        }) { Text(Texts.ADD_SCENARIO) }
        ImportScenariosButton(model)
    }

    CampaignMapEditor(model, c, ask)
    FlagsEditor(model, c, ask)

    SectionHeader(Texts.TRANSITIONS)
    Text(Texts.TRANSITIONS_HINT, style = MaterialTheme.typography.bodySmall)
    c.transitions.forEachIndexed { i, t ->
        val p = "transitions[$i]"
        fun set(f: (ScenarioTransition) -> ScenarioTransition) = model.edit { cc ->
            cc.copy(transitions = cc.transitions.mapIndexed { j, x -> if (j == i) f(x) else x })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            ChoiceInput(model, Texts.FROM_SCENARIO, t.from, scenarioChoices, "$p.from", modifier = Modifier.weight(1f)) { v -> set { it.copy(from = v.orEmpty()) } }
            val resolutions = c.scenario(t.from)?.resolutionChoices().orEmpty()
            ChoiceInput(model, Texts.WITH_RESOLUTION, t.resolution, resolutions, "$p.resolution", modifier = Modifier.weight(1f)) { v -> set { it.copy(resolution = v.orEmpty()) } }
            ChoiceInput(model, Texts.TO_SCENARIO, t.to, scenarioChoices, "$p.to", modifier = Modifier.weight(1f)) { v -> set { it.copy(to = v.orEmpty()) } }
            ChoiceInput(model, Texts.REQUIRES_FLAG, t.requiresFlag, c.flags.map { Choice(it.id, it.label) }, "$p.requiresFlag", allowNone = true, modifier = Modifier.weight(0.8f)) { v -> set { it.copy(requiresFlag = v) } }
            IconButton(onClick = { model.edit { cc -> cc.copy(transitions = cc.transitions.filterIndexed { j, _ -> j != i }) } }) {
                Icon(Icons.Default.Close, Texts.DELETE)
            }
        }
    }
    OutlinedButton(onClick = {
        val first = c.scenarios.firstOrNull()
        model.edit { it.copy(transitions = it.transitions + ScenarioTransition(first?.id.orEmpty(), first?.resolutions?.firstOrNull()?.id.orEmpty(), first?.id.orEmpty())) }
    }) { Text(Texts.ADD_TRANSITION) }
}

// ---- scenario ----------------------------------------------------------------------------

@Composable
private fun ScenarioForm(model: EditorModel, s: Scenario, sp: String, ask: (String, (String) -> Unit) -> Unit) {
    fun upd(f: (Scenario) -> Scenario): (Campaign) -> Campaign = { it.updateScenario(s.id, f) }
    TextInput(model, Texts.TITLE, s.title, "${s.id}.title", "$sp.title") { v -> upd { it.copy(title = v) } }
    TextInput(model, Texts.INTRO, s.intro, "${s.id}.intro", "$sp.intro", multiline = true) { v -> upd { it.copy(intro = v) } }
    model.campaign?.let { c ->
        ChoiceInput(model, Texts.PLACE, s.place, c.places.map { Choice(it.id, it.name) }, "$sp.place", allowNone = true) { v -> model.edit(change = upd { it.copy(place = v) }) }
    }

    SectionHeader(Texts.THREAT)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IntInput(model, Texts.THREAT_MAX, s.threat.max, "${s.id}.threat.max", "$sp.threat.max") { v -> upd { it.copy(threat = it.threat.copy(max = v)) } }
        IntInput(model, Texts.THREAT_BASE, s.threat.perRound.base, "${s.id}.threat.base", "$sp.threat.perRound") { v ->
            upd { it.copy(threat = it.threat.copy(perRound = it.threat.perRound.copy(base = v))) }
        }
        IntInput(model, Texts.THREAT_PER_HERO, s.threat.perRound.perHero, "${s.id}.threat.perHero", null) { v ->
            upd { it.copy(threat = it.threat.copy(perRound = it.threat.perRound.copy(perHero = v))) }
        }
    }
    Text(Texts.THREAT_HINT, style = MaterialTheme.typography.bodySmall)

    SectionHeader(Texts.SHADOW_EVENTS)
    Text(Texts.SHADOW_EVENTS_HINT, style = MaterialTheme.typography.bodySmall)
    s.shadowEvents.forEachIndexed { i, e ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChoiceInput(model, "${i + 1}.", e, s.eventChoices(), "$sp.shadowEvents[$i]", modifier = Modifier.weight(1f)) { v ->
                model.edit(change = upd { x -> x.copy(shadowEvents = x.shadowEvents.mapIndexed { j, old -> if (j == i) v.orEmpty() else old }) })
            }
            IconButton(onClick = { model.edit(change = upd { x -> x.copy(shadowEvents = x.shadowEvents.filterIndexed { j, _ -> j != i }) }) }) { Icon(Icons.Default.Close, Texts.DELETE) }
        }
    }
    OutlinedButton(onClick = {
        ask(Texts.NEW_EVENT) { n -> model.createThen({ it.addEvent(s.id, n) }) { id -> model.edit(change = upd { x -> x.copy(shadowEvents = x.shadowEvents + id) }) } }
    }) { Text("＋ " + Texts.SHADOW_EVENTS) }

    SectionHeader(Texts.START_CHAPTER + " / " + Texts.START_OBJECTIVE)
    ChoiceInput(model, Texts.START_CHAPTER, s.startChapter, s.chapterChoices(), "$sp.startChapter") { v -> model.edit(change = upd { it.copy(startChapter = v.orEmpty()) }) }
    ChoiceInput(model, Texts.START_OBJECTIVE, s.startObjective, s.objectiveChoices(), "$sp.startObjective") { v -> model.edit(change = upd { it.copy(startObjective = v.orEmpty()) }) }

    SectionHeader(Texts.CONTENT)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val select: (String) -> Unit = { model.selection = it }
        OutlinedButton(onClick = { ask(Texts.NEW_CHAPTER) { n -> model.createThen({ it.addChapter(s.id, n) }, select) } }) { Text(Texts.GROUP_CHAPTERS) }
        OutlinedButton(onClick = { ask(Texts.NEW_EVENT) { n -> model.createThen({ it.addEvent(s.id, n) }, select) } }) { Text(Texts.GROUP_EVENTS) }
        OutlinedButton(onClick = { ask(Texts.NEW_TRIGGER) { n -> model.createThen({ it.addTrigger(s.id, n) }, select) } }) { Text(Texts.GROUP_TRIGGERS) }
        OutlinedButton(onClick = { ask(Texts.NEW_OBJECTIVE) { n -> model.createThen({ it.addObjective(s.id, n) }, select) } }) { Text(Texts.GROUP_OBJECTIVES) }
        OutlinedButton(onClick = { ask(Texts.NEW_RESOLUTION) { n -> model.createThen({ it.addResolution(s.id, n, Outcome.VICTORY) }, select) } }) { Text(Texts.GROUP_RESOLUTIONS) }
        OutlinedButton(onClick = {
            ask(Texts.NEW_ENEMY_GROUP) { n ->
                val type = model.catalog.enemyTypes.firstOrNull()?.id.orEmpty()
                val placement = s.chapter(s.startChapter)?.tiles?.firstOrNull()?.id.orEmpty()
                model.createThen({ it.addEnemyGroup(s.id, n, type, TileLocation(placement, HexCoord.ORIGIN)) }, select)
            }
        }) { Text(Texts.GROUP_ENEMIES) }
    }
}

// ---- chapter -----------------------------------------------------------------------------

@Composable
private fun ChapterForm(model: EditorModel, s: Scenario, def: Definition, ask: (String, (String) -> Unit) -> Unit) {
    val ch = s.chapter(def.id) ?: return
    TextInput(model, Texts.TITLE, ch.title, "${ch.id}.title", "${def.path}.title") { v -> { it.updateChapter(ch.id) { c -> c.copy(title = v) } } }
    TextInput(model, Texts.SETUP_TEXT, ch.setupText, "${ch.id}.setup", "${def.path}.setupText", multiline = true) { v -> { it.updateChapter(ch.id) { c -> c.copy(setupText = v) } } }
    ChoiceInput(
        model, Texts.ON_UNLOCK, ch.onUnlock, s.eventChoices(), "${def.path}.onUnlock", allowNone = true,
        onCreate = { ask(Texts.NEW_EVENT) { n -> model.createThen({ it.addEvent(s.id, n) }) { id -> model.edit { c -> c.updateChapter(ch.id) { x -> x.copy(onUnlock = id) } } } } },
    ) { v -> model.edit { it.updateChapter(ch.id) { c -> c.copy(onUnlock = v) } } }
    MapKindInput(model, ch)
    Text(if (ch.isBattle) Texts.BATTLE_HINT_SELECT else Texts.MAP_HINT, style = MaterialTheme.typography.bodySmall)
}

// ---- objective / resolution ---------------------------------------------------------------

@Composable
private fun ObjectiveForm(model: EditorModel, s: Scenario, def: Definition, ask: (String, (String) -> Unit) -> Unit) {
    val o = s.objective(def.id) ?: return
    TextInput(model, Texts.TITLE, o.title, "${o.id}.title", "${def.path}.title") { v -> { it.updateObjective(o.id) { x -> x.copy(title = v) } } }
    TextInput(model, Texts.REMINDER, o.reminder, "${o.id}.reminder", "${def.path}.reminder", multiline = true) { v -> { it.updateObjective(o.id) { x -> x.copy(reminder = v) } } }
    ChoiceInput(
        model, Texts.COMPLETED_BY, o.completedBy, s.triggerChoices(), "${def.path}.completedBy",
        onCreate = { ask(Texts.NEW_TRIGGER) { n -> model.createThen({ it.addTrigger(s.id, n) }) { id -> model.edit { c -> c.updateObjective(o.id) { x -> x.copy(completedBy = id) } } } } },
    ) { v -> model.edit { it.updateObjective(o.id) { x -> x.copy(completedBy = v.orEmpty()) } } }
}

@Composable
private fun ResolutionForm(model: EditorModel, s: Scenario, def: Definition) {
    val r = s.resolution(def.id) ?: return
    TextInput(model, Texts.TITLE, r.title, "${r.id}.title", "${def.path}.title") { v -> { it.updateResolution(r.id) { x -> x.copy(title = v) } } }
    TextInput(model, Texts.TEXT, r.text, "${r.id}.text", "${def.path}.text", multiline = true) { v -> { it.updateResolution(r.id) { x -> x.copy(text = v) } } }
    val outcomes = listOf(Choice("VICTORY", Texts.OUTCOME_VICTORY), Choice("DEFEAT", Texts.OUTCOME_DEFEAT), Choice("NEUTRAL", Texts.OUTCOME_NEUTRAL))
    ChoiceInput(model, Texts.OUTCOME, r.outcome.name, outcomes, null) { v ->
        model.edit { it.updateResolution(r.id) { x -> x.copy(outcome = Outcome.valueOf(v!!)) } }
    }
    SectionHeader(Texts.REWARDS)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IntInput(model, Texts.LORE, r.lore, "${r.id}.lore", "${def.path}.lore") { v -> { it.updateResolution(r.id) { x -> x.copy(lore = v) } } }
        IntInput(model, Texts.XP, r.xp, "${r.id}.xp", "${def.path}.xp") { v -> { it.updateResolution(r.id) { x -> x.copy(xp = v) } } }
    }
    Text(Texts.SETS_FLAGS, style = MaterialTheme.typography.labelLarge)
    model.campaign?.let { c ->
        FlagChips(c, r.setFlags) { f -> model.edit { it.updateResolution(r.id) { x -> x.copy(setFlags = if (f in x.setFlags) x.setFlags - f else x.setFlags + f) } } }
    }
}

// ---- enemy group -------------------------------------------------------------------------

@Composable
private fun EnemyGroupForm(model: EditorModel, s: Scenario, def: Definition) {
    val g = s.enemyGroup(def.id) ?: return
    val types = model.catalog.enemyTypes.map { Choice(it.id, it.name) }
    ChoiceInput(model, Texts.ENEMY_TYPE, g.enemyType, types, "${def.path}.enemyType") { v -> model.edit { it.updateEnemyGroup(g.id) { x -> x.copy(enemyType = v.orEmpty()) } } }
    SectionHeader(Texts.COUNT_BY_HEROES)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (1..5).forEach { n ->
            IntInput(model, Texts.heroes(n), g.count[n] ?: 0, "${g.id}.count.$n", null, width = 110.dp) { v ->
                { it.updateEnemyGroup(g.id) { x -> x.copy(count = x.count + (n to v.coerceAtLeast(0))) } }
            }
        }
    }
    FieldIssues(model, "${def.path}.count")
    LocationInfo(model, s, g.location, "${def.path}.location")
}

@Composable
private fun LocationInfo(model: EditorModel, s: Scenario, location: Location, path: String) {
    SectionHeader(Texts.LOCATION)
    val loc = location as? TileLocation ?: run {
        Text(location.toString())
        FieldIssues(model, path)
        return
    }
    val p = s.placement(loc.placement)
    val tile = p?.let { model.catalog.tile(it.tile)?.label ?: it.tile } ?: loc.placement.ifEmpty { "–" }
    Text(Texts.location(tile, loc.cell.toString()))
    Text(Texts.MAP_HINT, style = MaterialTheme.typography.bodySmall)
    FieldIssues(model, path)
}

// ---- token -------------------------------------------------------------------------------

@Composable
private fun TokenForm(model: EditorModel, s: Scenario, def: Definition, ask: (String, (String) -> Unit) -> Unit) {
    val t = s.token(def.id) ?: return
    fun upd(f: (io.github.aljadvb.worldbuilder.model.Token) -> io.github.aljadvb.worldbuilder.model.Token): (Campaign) -> Campaign = { it.updateToken(t.id, f) }
    TextInput(model, Texts.LABEL, t.label, "${t.id}.label", "${def.path}.label") { v -> upd { it.copy(label = v) } }
    val types = Texts.TOKEN_TYPES.map { (k, label) -> Choice(k, label) }
    val typeKey = t.type.name.lowercase()
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ChoiceInput(model, Texts.TOKEN_TYPE, typeKey, types, null, modifier = Modifier.weight(1f)) { v ->
            val type = TokenType.valueOf(v!!.uppercase())
            model.edit(change = upd { it.copy(type = type, customType = if (type == TokenType.CUSTOM) it.customType ?: "Eigenes" else null) })
        }
        if (t.type == TokenType.CUSTOM) {
            TextInput(model, Texts.CUSTOM_TYPE, t.customType.orEmpty(), "${t.id}.customType", "${def.path}.customType", modifier = Modifier.weight(1f)) { v ->
                upd { it.copy(customType = v) }
            }
        }
    }
    TextInput(model, Texts.INSPECT_TEXT, t.inspectText.orEmpty(), "${t.id}.inspect", "${def.path}.inspectText", multiline = true) { v ->
        upd { it.copy(inspectText = v.ifEmpty { null }) }
    }
    ChoiceInput(
        model, Texts.ON_INTERACT, t.onInteract, s.eventChoices(), "${def.path}.onInteract", allowNone = true,
        onCreate = { ask(Texts.NEW_EVENT) { n -> model.createThen({ it.addEvent(s.id, n) }) { id -> model.edit(change = upd { x -> x.copy(onInteract = id) }) } } },
    ) { v -> model.edit(change = upd { it.copy(onInteract = v) }) }
    CheckInput(Texts.INITIALLY_ACTIVE, t.initiallyActive) { v -> model.edit(change = upd { it.copy(initiallyActive = v) }) }
    CheckInput(Texts.REMOVE_AFTER, t.removeAfterInteract) { v -> model.edit(change = upd { it.copy(removeAfterInteract = v) }) }
    LocationInfo(model, s, t.location, "${def.path}.location")
}

// ---- trigger -----------------------------------------------------------------------------

@Composable
private fun TriggerForm(model: EditorModel, s: Scenario, def: Definition, ask: (String, (String) -> Unit) -> Unit) {
    val t = s.trigger(def.id) ?: return
    fun set(cond: TriggerCondition) = model.edit { it.updateTrigger(t.id) { x -> x.copy(on = cond) } }
    val key = when (t.on) {
        is TriggerCondition.TokenInteracted -> "tokenInteracted"
        is TriggerCondition.ThreatReached -> "threatReached"
        TriggerCondition.ThreatMax -> "threatMax"
        is TriggerCondition.EnemyGroupDefeated -> "enemyGroupDefeated"
        is TriggerCondition.ObjectiveCompleted -> "objectiveCompleted"
        is TriggerCondition.RoundEnded -> "roundEnded"
        is TriggerCondition.TileExplored -> "tileExplored"
        TriggerCondition.Manual -> "manual"
    }
    ChoiceInput(model, Texts.CONDITION, key, Texts.CONDITIONS.map { Choice(it.first, it.second) }, null) { v ->
        set(
            when (v) {
                "tokenInteracted" -> TriggerCondition.TokenInteracted(s.tokens.firstOrNull()?.id.orEmpty())
                "threatReached" -> TriggerCondition.ThreatReached((s.threat.max / 2).coerceAtLeast(1))
                "threatMax" -> TriggerCondition.ThreatMax
                "enemyGroupDefeated" -> TriggerCondition.EnemyGroupDefeated(s.enemyGroups.firstOrNull()?.id.orEmpty())
                "objectiveCompleted" -> TriggerCondition.ObjectiveCompleted(s.objectives.firstOrNull()?.id.orEmpty())
                "roundEnded" -> TriggerCondition.RoundEnded(1)
                "tileExplored" -> TriggerCondition.TileExplored(s.placements.firstOrNull { it.unexplored }?.id ?: s.placements.firstOrNull()?.id.orEmpty())
                else -> TriggerCondition.Manual
            },
        )
    }
    val p = "${def.path}.on"
    when (val on = t.on) {
        is TriggerCondition.TokenInteracted -> ChoiceInput(model, Texts.TOKEN, on.token, s.tokenChoices(), "$p.token") { v -> set(on.copy(token = v.orEmpty())) }
        is TriggerCondition.EnemyGroupDefeated -> ChoiceInput(model, Texts.ENEMY_GROUP, on.group, s.groupChoices(), "$p.group") { v -> set(on.copy(group = v.orEmpty())) }
        is TriggerCondition.ObjectiveCompleted -> ChoiceInput(model, Texts.OBJECTIVE, on.objective, s.objectiveChoices(), "$p.objective") { v -> set(on.copy(objective = v.orEmpty())) }
        is TriggerCondition.ThreatReached -> IntInput(model, Texts.VALUE, on.value, "${t.id}.value", "$p.value") { v -> { it.updateTrigger(t.id) { x -> x.copy(on = on.copy(value = v)) } } }
        is TriggerCondition.RoundEnded -> IntInput(model, Texts.ROUND, on.round, "${t.id}.round", "$p.round") { v -> { it.updateTrigger(t.id) { x -> x.copy(on = on.copy(round = v)) } } }
        is TriggerCondition.TileExplored -> ChoiceInput(
            model, Texts.TILE, on.placement, s.placements.map { Choice(it.id, "${model.catalog.tile(it.tile)?.label ?: it.tile} ${it.side} · ${it.id}" + if (it.unexplored) "" else " (erforscht)") }, "$p.placement",
        ) { v -> set(on.copy(placement = v.orEmpty())) }
        TriggerCondition.ThreatMax, TriggerCondition.Manual -> Unit
    }

    SectionHeader(Texts.TRIGGER_EVENTS)
    t.events.forEachIndexed { i, e ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChoiceInput(model, "${i + 1}.", e, s.eventChoices(), "${def.path}.events[$i]", modifier = Modifier.weight(1f)) { v ->
                model.edit { it.updateTrigger(t.id) { x -> x.copy(events = x.events.mapIndexed { j, old -> if (j == i) v.orEmpty() else old }) } }
            }
            IconButton(onClick = { model.edit { it.updateTrigger(t.id) { x -> x.copy(events = x.events.filterIndexed { j, _ -> j != i }) } } }) {
                Icon(Icons.Default.Close, Texts.DELETE)
            }
        }
    }
    ButtonRow {
        OutlinedButton(onClick = {
            val first = s.events.firstOrNull()?.id.orEmpty()
            model.edit { it.updateTrigger(t.id) { x -> x.copy(events = x.events + first) } }
        }) { Text(Texts.ADD_EVENT) }
        OutlinedButton(onClick = {
            ask(Texts.NEW_EVENT) { n -> model.createThen({ it.addEvent(s.id, n) }) { id -> model.edit { c -> c.updateTrigger(t.id) { x -> x.copy(events = x.events + id) } } } }
        }) { Text(Texts.CREATE_NEW) }
    }
    CheckInput(Texts.REPEATABLE, t.repeatable) { v -> model.edit { it.updateTrigger(t.id) { x -> x.copy(repeatable = v) } } }
}

// ---- placement / event (full editors follow) ---------------------------------------------

@Composable
private fun PlacementInfo(model: EditorModel, s: Scenario, def: Definition) {
    val p = s.placement(def.id) ?: return
    Text("${Texts.TILE}: ${model.catalog.tile(p.tile)?.label ?: p.tile} · ${Texts.SIDE} ${p.side} · ${Texts.ROTATION} ${p.rotation * 60}° · ${Texts.POSITION} ${p.position}")
    FieldIssues(model, def.path)
    CheckInput(Texts.UNEXPLORED, p.unexplored) { v -> model.edit { it.updatePlacement(p.id) { x -> x.copy(unexplored = v) } } }
    if (p.unexplored || p.onExplore != null) {
        ChoiceInput(model, Texts.ON_EXPLORE, p.onExplore, s.eventChoices(), "${def.path}.onExplore", allowNone = true) { v ->
            model.edit { it.updatePlacement(p.id) { x -> x.copy(onExplore = v) } }
        }
    }
    Text(Texts.MAP_HINT, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun EventForm(model: EditorModel, s: Scenario, def: Definition, ask: (String, (String) -> Unit) -> Unit) {
    EventEditor(model, s, def, ask)
}

