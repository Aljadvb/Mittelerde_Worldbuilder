package io.github.aljadvb.worldbuilder.editor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.aljadvb.worldbuilder.editing.Definition
import io.github.aljadvb.worldbuilder.editing.addEvent
import io.github.aljadvb.worldbuilder.editing.addFlag
import io.github.aljadvb.worldbuilder.editing.moved
import io.github.aljadvb.worldbuilder.editing.updateEvent
import io.github.aljadvb.worldbuilder.editor.EditorModel
import io.github.aljadvb.worldbuilder.editor.Texts
import io.github.aljadvb.worldbuilder.model.Attribute
import io.github.aljadvb.worldbuilder.model.Campaign
import io.github.aljadvb.worldbuilder.model.ChoiceOption
import io.github.aljadvb.worldbuilder.model.EventStep
import io.github.aljadvb.worldbuilder.model.Scenario
import io.github.aljadvb.worldbuilder.model.TriggerCondition

/** Editor for the step list of an event. */
@Composable
fun EventEditor(model: EditorModel, s: Scenario, def: Definition, ask: (String, (String) -> Unit) -> Unit) {
    val event = s.event(def.id) ?: return
    val ep = def.path
    fun editSteps(f: (List<EventStep>) -> List<EventStep>) = model.edit { it.updateEvent(event.id) { e -> e.copy(steps = f(e.steps)) } }
    fun setStep(i: Int, step: EventStep): (Campaign) -> Campaign = { c -> c.updateEvent(event.id) { e -> e.copy(steps = e.steps.mapIndexed { j, x -> if (j == i) step else x }) } }
    /** Creates a new event and passes its ID to [use]. */
    fun newEvent(use: (String) -> Unit) = ask(Texts.NEW_EVENT) { name ->
        var id: String? = null
        if (model.edit { c -> c.addEvent(s.id, name).also { id = it.id }.campaign }) id?.let(use)
    }

    SectionHeader(Texts.STEPS)
    FieldIssues(model, "$ep.steps".takeIf { event.steps.isEmpty() })
    event.steps.forEachIndexed { i, step ->
        val p = "$ep.steps[$i]"
        val key = "${event.id}.steps[$i]"
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}. ${stepLabel(step)}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { editSteps { it.moved(i, i - 1) } }, enabled = i > 0) { Icon(Icons.Default.KeyboardArrowUp, Texts.MOVE_UP) }
                    IconButton(onClick = { editSteps { it.moved(i, i + 1) } }, enabled = i < event.steps.lastIndex) { Icon(Icons.Default.KeyboardArrowDown, Texts.MOVE_DOWN) }
                    IconButton(onClick = { editSteps { it.filterIndexed { j, _ -> j != i } } }) { Icon(Icons.Default.Close, Texts.DELETE) }
                }
                StepFields(model, s, step, p, key, set = { new -> model.edit(change = setStep(i, new)) }, setText = { new -> setStep(i, new) }, newEvent = ::newEvent)
                // issues about the step itself (e.g. wrong position)
                model.report.issues.filter { it.path == p }.forEach {
                    Text(it.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    val terminal = event.steps.lastOrNull()?.isTerminal == true
    if (terminal) {
        Text(Texts.STEP_LAST_HINT, style = MaterialTheme.typography.bodySmall)
    } else {
        AddStepButton { type -> editSteps { it + newStep(type, s) } }
    }
}

@Composable
private fun AddStepButton(onAdd: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text(Texts.ADD_STEP) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Texts.STEP_TYPES.forEach { (type, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { open = false; onAdd(type) })
            }
        }
    }
}

private fun stepLabel(step: EventStep): String {
    val type = when (step) {
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
        is EventStep.SufferHarm -> "sufferHarm"
        is EventStep.GainItem -> "gainItem"
        is EventStep.Recover -> "recover"
        is EventStep.GainCondition -> "gainCondition"
        is EventStep.GainCorruption -> "gainCorruption"
        is EventStep.GainTitle -> "gainTitle"
        is EventStep.GainWeakness -> "gainWeakness"
        is EventStep.LoseCorruption -> "loseCorruption"
        is EventStep.CheckCorruption -> "checkCorruption"
        is EventStep.PlaceMarker -> "placeMarker"
        is EventStep.RemoveMarker -> "removeMarker"
        is EventStep.Inscription -> "inscription"
        is EventStep.AffectEnemy -> "affectEnemy"
        is EventStep.GainLore -> "gainLore"
        is EventStep.SetDarkness -> "setDarkness"
        is EventStep.SetFlag -> "setFlag"
        is EventStep.CheckFlag -> "checkFlag"
        is EventStep.EndScenario -> "endScenario"
    }
    return Texts.STEP_TYPES.first { it.first == type }.second
}

/** First field of the scenario: a square on battle maps, else the anchor of the first tile. */
private fun defaultLocation(s: Scenario): io.github.aljadvb.worldbuilder.model.Location =
    if (s.chapters.any { it.isBattle }) io.github.aljadvb.worldbuilder.model.SquareLocation(io.github.aljadvb.worldbuilder.model.GridCoord(0, 0))
    else io.github.aljadvb.worldbuilder.model.TileLocation(s.placements.firstOrNull()?.id.orEmpty(), io.github.aljadvb.worldbuilder.model.HexCoord.ORIGIN)

/** A new step of [type], pre-filled with the first sensible reference. */
fun newStep(type: String, s: Scenario): EventStep = when (type) {
    "text" -> EventStep.Text("")
    "choice" -> EventStep.Choice("", listOf(ChoiceOption("", ""), ChoiceOption("", "")))
    "test" -> EventStep.Test(attribute = Attribute.MIGHT, successes = 1, onSuccess = "")
    "spawnEnemies" -> EventStep.SpawnEnemies(s.enemyGroups.firstOrNull()?.id.orEmpty())
    "changeThreat" -> EventStep.ChangeThreat(1)
    "addToken" -> EventStep.AddToken(s.tokens.firstOrNull { !it.initiallyActive }?.id ?: s.tokens.firstOrNull()?.id.orEmpty())
    "removeToken" -> EventStep.RemoveToken(s.tokens.firstOrNull()?.id.orEmpty())
    "unlockChapter" -> EventStep.UnlockChapter(s.chapters.getOrNull(1)?.id ?: s.chapters.firstOrNull()?.id.orEmpty())
    "setObjective" -> EventStep.SetObjective(s.objectives.firstOrNull()?.id.orEmpty())
    "fireTrigger" -> EventStep.FireTrigger(s.triggers.firstOrNull { it.on == TriggerCondition.Manual }?.id.orEmpty())
    "endScenario" -> EventStep.EndScenario(s.resolutions.firstOrNull()?.id.orEmpty())
    "gainInspiration" -> EventStep.GainInspiration()
    "sufferHarm" -> EventStep.SufferHarm(damage = 1)
    "gainItem" -> EventStep.GainItem()
    "recover" -> EventStep.Recover(damage = 1)
    "gainCondition" -> EventStep.GainCondition(io.github.aljadvb.worldbuilder.model.Condition.HIDDEN)
    "gainCorruption" -> EventStep.GainCorruption()
    "gainTitle" -> EventStep.GainTitle("")
    "gainWeakness" -> EventStep.GainWeakness()
    "loseCorruption" -> EventStep.LoseCorruption()
    "checkCorruption" -> EventStep.CheckCorruption("")
    "placeMarker" -> EventStep.PlaceMarker(io.github.aljadvb.worldbuilder.model.FieldMarker.ROUGH, defaultLocation(s))
    "removeMarker" -> EventStep.RemoveMarker(io.github.aljadvb.worldbuilder.model.FieldMarker.ROUGH, defaultLocation(s))
    "inscription" -> EventStep.Inscription("Mae govannen", "Willkommen")
    "affectEnemy" -> EventStep.AffectEnemy(s.enemyGroups.firstOrNull()?.id.orEmpty(), hits = 2)
    "gainLore" -> EventStep.GainLore(1)
    "setDarkness" -> EventStep.SetDarkness(1)
    "setFlag" -> EventStep.SetFlag("")
    "checkFlag" -> EventStep.CheckFlag("", "")
    else -> error("unknown step type $type")
}

@Composable
private fun StepFields(
    model: EditorModel,
    s: Scenario,
    step: EventStep,
    p: String,
    key: String,
    set: (EventStep) -> Unit,
    setText: (EventStep) -> (Campaign) -> Campaign,
    newEvent: ((String) -> Unit) -> Unit,
) {
    val events = s.events.map { Choice(it.id, it.id) }
    when (step) {
        is EventStep.Text -> TextInput(model, Texts.TEXT, step.text, "$key.text", "$p.text", multiline = true) { v -> setText(step.copy(text = v)) }
        is EventStep.Choice -> {
            TextInput(model, Texts.QUESTION, step.text, "$key.text", "$p.text", multiline = true) { v -> setText(step.copy(text = v)) }
            Text(Texts.OPTIONS, style = MaterialTheme.typography.labelLarge)
            step.options.forEachIndexed { oi, o ->
                fun withOption(f: (ChoiceOption) -> ChoiceOption) = step.copy(options = step.options.mapIndexed { j, x -> if (j == oi) f(x) else x })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    TextInput(model, Texts.OPTION_LABEL, o.label, "$key.options[$oi].label", "$p.options[$oi].label", modifier = Modifier.weight(1f)) { v ->
                        setText(withOption { it.copy(label = v) })
                    }
                    ChoiceInput(
                        model, Texts.OPTION_EVENT, o.event, events, "$p.options[$oi].event", modifier = Modifier.weight(1f),
                        onCreate = { newEvent { id -> set(withOption { it.copy(event = id) }) } },
                    ) { v -> set(withOption { it.copy(event = v.orEmpty()) }) }
                    IntInput(model, Texts.OPTION_CORRUPTION, o.corruption, "$key.options[$oi].corruption", "$p.options[$oi].corruption", width = 110.dp) { v ->
                        setText(withOption { it.copy(corruption = v.coerceIn(0, 2)) })
                    }
                    IconButton(onClick = { set(step.copy(options = step.options.filterIndexed { j, _ -> j != oi })) }) { Icon(Icons.Default.Close, Texts.DELETE) }
                }
            }
            FieldIssues(model, "$p.options".takeIf { step.options.size < 2 })
            OutlinedButton(onClick = { set(step.copy(options = step.options + ChoiceOption("", ""))) }) { Text(Texts.ADD_OPTION) }
        }
        is EventStep.Test -> {
            TextInput(model, Texts.TEXT, step.text.orEmpty(), "$key.text", "$p.text", multiline = true) { v -> setText(step.copy(text = v.ifEmpty { null })) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ChoiceInput(model, Texts.ATTRIBUTE, step.attribute.name, Texts.ATTRIBUTES.map { Choice(it.first, it.second) }, null, modifier = Modifier.weight(1f)) { v ->
                    set(step.copy(attribute = Attribute.valueOf(v!!)))
                }
                IntInput(model, Texts.SUCCESSES, step.successes, "$key.successes", "$p.successes") { v -> setText(step.copy(successes = v)) }
                if (!step.cumulative) IntInput(model, Texts.CORRUPTION_EASE, step.corruptionEase, "$key.corruptionEase", "$p.corruptionEase", width = 260.dp) { v -> setText(step.copy(corruptionEase = v.coerceIn(0, 2))) }
            }
            ChoiceInput(model, Texts.ON_SUCCESS, step.onSuccess, events, "$p.onSuccess", onCreate = { newEvent { id -> set(step.copy(onSuccess = id)) } }) { v ->
                set(step.copy(onSuccess = v.orEmpty()))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CheckInput(Texts.TEST_HIDDEN, step.hidden) { v -> set(step.copy(hidden = v)) }
                CheckInput(Texts.TEST_CUMULATIVE, step.cumulative) { v -> set(step.copy(cumulative = v, onFailure = if (v) null else step.onFailure, onAttempt = if (v) step.onAttempt else null)) }
            }
            if (step.cumulative) {
                ChoiceInput(model, Texts.ON_ATTEMPT, step.onAttempt, events, "$p.onAttempt", allowNone = true, onCreate = { newEvent { id -> set(step.copy(onAttempt = id)) } }) { v ->
                    set(step.copy(onAttempt = v))
                }
            } else {
                ChoiceInput(model, Texts.ON_FAILURE, step.onFailure, events, "$p.onFailure", allowNone = true, onCreate = { newEvent { id -> set(step.copy(onFailure = id)) } }) { v ->
                    set(step.copy(onFailure = v))
                }
            }
        }
        is EventStep.SpawnEnemies -> ChoiceInput(model, Texts.ENEMY_GROUP, step.group, s.enemyGroups.map { Choice(it.id, it.id) }, "$p.group") { v -> set(step.copy(group = v.orEmpty())) }
        is EventStep.ChangeThreat -> IntInput(model, Texts.AMOUNT, step.amount, "$key.amount", "$p.amount") { v -> setText(step.copy(amount = v)) }
        is EventStep.AddToken -> ChoiceInput(model, Texts.TOKEN, step.token, s.tokens.map { Choice(it.id, "${it.label} · ${it.id}") }, "$p.token") { v -> set(step.copy(token = v.orEmpty())) }
        is EventStep.RemoveToken -> ChoiceInput(model, Texts.TOKEN, step.token, s.tokens.map { Choice(it.id, "${it.label} · ${it.id}") }, "$p.token") { v -> set(step.copy(token = v.orEmpty())) }
        is EventStep.UnlockChapter -> ChoiceInput(model, Texts.CHAPTER, step.chapter, s.chapters.map { Choice(it.id, "${it.title} · ${it.id}") }, "$p.chapter") { v -> set(step.copy(chapter = v.orEmpty())) }
        is EventStep.SetObjective -> ChoiceInput(model, Texts.OBJECTIVE, step.objective, s.objectives.map { Choice(it.id, "${it.title} · ${it.id}") }, "$p.objective") { v -> set(step.copy(objective = v.orEmpty())) }
        is EventStep.FireTrigger -> ChoiceInput(
            model, Texts.TRIGGER, step.trigger, s.triggers.filter { it.on == TriggerCondition.Manual }.map { Choice(it.id, it.id) }, "$p.trigger",
        ) { v -> set(step.copy(trigger = v.orEmpty())) }
        is EventStep.GainInspiration -> {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IntInput(model, Texts.AMOUNT, step.amount, "$key.amount", "$p.amount") { v -> setText(step.copy(amount = v)) }
                ChoiceInput(model, Texts.INSPIRATION_WHO, step.who.name, Texts.INSPIRATION_TARGETS.map { Choice(it.first, it.second) }, null, modifier = Modifier.weight(1f)) { v ->
                    set(step.copy(who = io.github.aljadvb.worldbuilder.model.HeroTarget.valueOf(v!!)))
                }
            }
            TextInput(model, Texts.OPTIONAL_TEXT, step.text.orEmpty(), "$key.text", "$p.text") { v -> setText(step.copy(text = v.ifEmpty { null })) }
        }
        is EventStep.GainLore -> {
            IntInput(model, Texts.AMOUNT, step.amount, "$key.amount", "$p.amount") { v -> setText(step.copy(amount = v)) }
            TextInput(model, Texts.OPTIONAL_TEXT, step.text.orEmpty(), "$key.text", "$p.text") { v -> setText(step.copy(text = v.ifEmpty { null })) }
        }
        is EventStep.SetDarkness -> IntInput(model, Texts.DARKNESS_FEAR, step.fear, "$key.fear", "$p.fear", width = 360.dp) { v -> setText(step.copy(fear = v)) }
        is EventStep.SetFlag -> FlagInput(model, step.flag, "$p.flag") { v -> set(step.copy(flag = v)) }
        is EventStep.CheckFlag -> {
            FlagInput(model, step.flag, "$p.flag") { v -> set(step.copy(flag = v)) }
            ChoiceInput(model, Texts.IF_SET, step.ifSet, events, "$p.ifSet", onCreate = { newEvent { id -> set(step.copy(ifSet = id)) } }) { v -> set(step.copy(ifSet = v.orEmpty())) }
            ChoiceInput(model, Texts.IF_NOT_SET, step.ifNotSet, events, "$p.ifNotSet", allowNone = true, onCreate = { newEvent { id -> set(step.copy(ifNotSet = id)) } }) { v -> set(step.copy(ifNotSet = v)) }
        }
        is EventStep.SufferHarm -> {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                IntInput(model, Texts.DAMAGE, step.damage, "$key.damage", "$p") { v -> setText(step.copy(damage = v)) }
                IntInput(model, Texts.FEAR, step.fear, "$key.fear", null) { v -> setText(step.copy(fear = v)) }
                ChoiceInput(model, Texts.HARM_WHO, step.who.name, Texts.INSPIRATION_TARGETS.map { Choice(it.first, it.second) }, null, modifier = Modifier.weight(1f)) { v ->
                    set(step.copy(who = io.github.aljadvb.worldbuilder.model.HeroTarget.valueOf(v!!)))
                }
                CheckInput(Texts.HIDDEN, step.hidden) { v -> set(step.copy(hidden = v)) }
            }
            TextInput(model, Texts.OPTIONAL_TEXT, step.text.orEmpty(), "$key.text", "$p.text") { v -> setText(step.copy(text = v.ifEmpty { null })) }
        }
        is EventStep.AffectEnemy -> {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                ChoiceInput(model, Texts.ENEMY_GROUP, step.group, s.enemyGroups.map { Choice(it.id, it.id) }, "$p.group", modifier = Modifier.weight(1f)) { v -> set(step.copy(group = v.orEmpty())) }
                IntInput(model, Texts.HITS, step.hits, "$key.hits", "$p.hits") { v -> setText(step.copy(hits = v)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CheckInput(Texts.MOD_PIERCE, step.pierce) { v -> set(step.copy(pierce = v)) }
                CheckInput(Texts.MOD_SMITE, step.smite) { v -> set(step.copy(smite = v)) }
                CheckInput(Texts.MOD_SUNDER, step.sunder) { v -> set(step.copy(sunder = v)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CheckInput(Texts.MOD_CLEAVE, step.cleave) { v -> set(step.copy(cleave = v)) }
                CheckInput(Texts.MOD_STUN, step.stun) { v -> set(step.copy(stun = v)) }
                CheckInput(Texts.MOD_LETHAL, step.lethal) { v -> set(step.copy(lethal = v)) }
            }
            TextInput(model, Texts.OPTIONAL_TEXT, step.text.orEmpty(), "$key.text", "$p.text") { v -> setText(step.copy(text = v.ifEmpty { null })) }
        }
        is EventStep.Inscription -> {
            TextInput(model, Texts.OPTIONAL_TEXT, step.text.orEmpty(), "$key.text", "$p.text") { v -> setText(step.copy(text = v.ifEmpty { null })) }
            TextInput(model, Texts.ELVISH, step.elvish, "$key.elvish", "$p.elvish") { v -> setText(step.copy(elvish = v)) }
            TextInput(model, Texts.TRANSLATION, step.translation, "$key.translation", "$p.translation") { v -> setText(step.copy(translation = v)) }
            Text(io.github.aljadvb.worldbuilder.model.Tengwar.transliterate(step.elvish), fontFamily = TengwarFont, style = MaterialTheme.typography.headlineSmall)
            CheckInput(Texts.RIDDLE, step.test != null) { v ->
                set(if (v) step.copy(test = Attribute.WISDOM) else step.copy(test = null, onSuccess = null, onFailure = null))
            }
            step.test?.let { attr ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ChoiceInput(model, Texts.ATTRIBUTE, attr.name, Texts.ATTRIBUTES.map { Choice(it.first, it.second) }, null, modifier = Modifier.weight(1f)) { v ->
                        set(step.copy(test = Attribute.valueOf(v!!)))
                    }
                    IntInput(model, Texts.SUCCESSES, step.successes, "$key.successes", "$p.successes") { v -> setText(step.copy(successes = v)) }
                }
                ChoiceInput(model, Texts.ON_SUCCESS, step.onSuccess, events, "$p.onSuccess", allowNone = true, onCreate = { newEvent { id -> set(step.copy(onSuccess = id)) } }) { v ->
                    set(step.copy(onSuccess = v))
                }
                ChoiceInput(model, Texts.ON_FAILURE, step.onFailure, events, "$p.onFailure", allowNone = true, onCreate = { newEvent { id -> set(step.copy(onFailure = id)) } }) { v ->
                    set(step.copy(onFailure = v))
                }
            }
        }
        is EventStep.GainTitle -> {
            ChoiceInput(model, Texts.TITLE_CARD, step.title, model.catalog.titles.map { Choice(it.id, it.name + if (it.summary.isNotBlank()) " – " + it.summary.take(60) else "") }, "$p.title") { v ->
                set(step.copy(title = v.orEmpty()))
            }
            TextInput(model, Texts.OPTIONAL_TEXT, step.text.orEmpty(), "$key.text", "$p.text") { v -> setText(step.copy(text = v.ifEmpty { null })) }
        }
        is EventStep.GainWeakness -> {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IntInput(model, Texts.AMOUNT, step.amount, "$key.amount", "$p.amount") { v -> setText(step.copy(amount = v)) }
                ChoiceInput(model, Texts.HARM_WHO, step.who.name, Texts.INSPIRATION_TARGETS.map { Choice(it.first, it.second) }, null, modifier = Modifier.weight(1f)) { v ->
                    set(step.copy(who = io.github.aljadvb.worldbuilder.model.HeroTarget.valueOf(v!!)))
                }
            }
            TextInput(model, Texts.OPTIONAL_TEXT, step.text.orEmpty(), "$key.text", "$p.text") { v -> setText(step.copy(text = v.ifEmpty { null })) }
        }
        is EventStep.GainCorruption -> {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IntInput(model, Texts.AMOUNT, step.amount, "$key.amount", "$p.amount") { v -> setText(step.copy(amount = v)) }
                ChoiceInput(model, Texts.HARM_WHO, step.who.name, Texts.INSPIRATION_TARGETS.map { Choice(it.first, it.second) }, null, modifier = Modifier.weight(1f)) { v ->
                    set(step.copy(who = io.github.aljadvb.worldbuilder.model.HeroTarget.valueOf(v!!)))
                }
            }
            TextInput(model, Texts.OPTIONAL_TEXT, step.text.orEmpty(), "$key.text", "$p.text") { v -> setText(step.copy(text = v.ifEmpty { null })) }
        }
        is EventStep.LoseCorruption -> {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IntInput(model, Texts.AMOUNT, step.amount, "$key.amount", "$p.amount") { v -> setText(step.copy(amount = v)) }
                ChoiceInput(model, Texts.HARM_WHO, step.who.name, Texts.INSPIRATION_TARGETS.map { Choice(it.first, it.second) }, null, modifier = Modifier.weight(1f)) { v ->
                    set(step.copy(who = io.github.aljadvb.worldbuilder.model.HeroTarget.valueOf(v!!)))
                }
            }
            TextInput(model, Texts.OPTIONAL_TEXT, step.text.orEmpty(), "$key.text", "$p.text") { v -> setText(step.copy(text = v.ifEmpty { null })) }
        }
        is EventStep.CheckCorruption -> {
            TextInput(model, Texts.OPTIONAL_TEXT, step.text.orEmpty(), "$key.text", "$p.text") { v -> setText(step.copy(text = v.ifEmpty { null })) }
            ChoiceInput(model, Texts.CORRUPTION_IF, step.ifCorrupted, events, "$p.ifCorrupted", onCreate = { newEvent { id -> set(step.copy(ifCorrupted = id)) } }) { v ->
                set(step.copy(ifCorrupted = v.orEmpty()))
            }
            ChoiceInput(model, Texts.CORRUPTION_IF_NOT, step.ifNotCorrupted, events, "$p.ifNotCorrupted", allowNone = true, onCreate = { newEvent { id -> set(step.copy(ifNotCorrupted = id)) } }) { v ->
                set(step.copy(ifNotCorrupted = v))
            }
        }
        is EventStep.PlaceMarker -> {
            ChoiceInput(model, Texts.MARKER, step.marker.name, Texts.MARKERS.map { Choice(it.first, it.second) }, null) { v ->
                set(step.copy(marker = io.github.aljadvb.worldbuilder.model.FieldMarker.valueOf(v!!)))
            }
            MarkerLocationInput(model, s, step.location, key, "$p.location", { set(step.copy(location = it)) }) { setText(step.copy(location = it)) }
            TextInput(model, Texts.OPTIONAL_TEXT, step.text.orEmpty(), "$key.text", "$p.text") { v -> setText(step.copy(text = v.ifEmpty { null })) }
        }
        is EventStep.RemoveMarker -> {
            ChoiceInput(model, Texts.MARKER, step.marker.name, Texts.MARKERS.map { Choice(it.first, it.second) }, null) { v ->
                set(step.copy(marker = io.github.aljadvb.worldbuilder.model.FieldMarker.valueOf(v!!)))
            }
            MarkerLocationInput(model, s, step.location, key, "$p.location", { set(step.copy(location = it)) }) { setText(step.copy(location = it)) }
        }
        is EventStep.GainCondition -> {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ChoiceInput(model, Texts.HERO_CONDITION, step.condition.name, Texts.HERO_CONDITIONS.map { Choice(it.first, it.second) }, null, modifier = Modifier.weight(1f)) { v ->
                    set(step.copy(condition = io.github.aljadvb.worldbuilder.model.Condition.valueOf(v!!)))
                }
                ChoiceInput(model, Texts.HARM_WHO, step.who.name, Texts.INSPIRATION_TARGETS.map { Choice(it.first, it.second) }, null, modifier = Modifier.weight(1f)) { v ->
                    set(step.copy(who = io.github.aljadvb.worldbuilder.model.HeroTarget.valueOf(v!!)))
                }
            }
            TextInput(model, Texts.OPTIONAL_TEXT, step.text.orEmpty(), "$key.text", "$p.text") { v -> setText(step.copy(text = v.ifEmpty { null })) }
        }
        is EventStep.Recover -> {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                IntInput(model, Texts.DAMAGE, step.damage, "$key.damage", "$p") { v -> setText(step.copy(damage = v)) }
                IntInput(model, Texts.FEAR, step.fear, "$key.fear", null) { v -> setText(step.copy(fear = v)) }
                ChoiceInput(model, Texts.HARM_WHO, step.who.name, Texts.INSPIRATION_TARGETS.map { Choice(it.first, it.second) }, null, modifier = Modifier.weight(1f)) { v ->
                    set(step.copy(who = io.github.aljadvb.worldbuilder.model.HeroTarget.valueOf(v!!)))
                }
            }
            TextInput(model, Texts.OPTIONAL_TEXT, step.text.orEmpty(), "$key.text", "$p.text") { v -> setText(step.copy(text = v.ifEmpty { null })) }
        }
        is EventStep.GainItem -> {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ChoiceInput(model, Texts.ITEM, step.item.orEmpty(),
                    listOf(Choice("", Texts.RANDOM_TRINKET)) + model.catalog.items.filter { !it.isGear }.map { Choice(it.id, it.name + if (it.kind == io.github.aljadvb.worldbuilder.model.ItemKind.MOUNT) " (Reittier)" else "") }, "$p.item", modifier = Modifier.weight(1f)) { v ->
                    set(step.copy(item = v?.ifEmpty { null }))
                }
                ChoiceInput(model, Texts.ITEM_WHO, step.who.name, Texts.INSPIRATION_TARGETS.filter { it.first != "EACH" }.map { Choice(it.first, it.second) }, null, modifier = Modifier.weight(1f)) { v ->
                    set(step.copy(who = io.github.aljadvb.worldbuilder.model.HeroTarget.valueOf(v!!)))
                }
            }
            TextInput(model, Texts.OPTIONAL_TEXT, step.text.orEmpty(), "$key.text", "$p.text") { v -> setText(step.copy(text = v.ifEmpty { null })) }
        }
        is EventStep.EndScenario -> ChoiceInput(model, Texts.RESOLUTION, step.resolution, s.resolutions.map { Choice(it.id, "${it.title} · ${it.id}") }, "$p.resolution") { v ->
            set(step.copy(resolution = v.orEmpty()))
        }
    }
}


/** Campaign flag dropdown with "create new". */
@Composable
private fun FlagInput(model: EditorModel, flag: String, path: String, onSelect: (String) -> Unit) {
    var creating by remember { mutableStateOf(false) }
    val flags = model.campaign?.flags.orEmpty().map { Choice(it.id, "${it.label} · ${it.id}") }
    ChoiceInput(model, Texts.FLAG, flag, flags, path, onCreate = { creating = true }) { v -> onSelect(v.orEmpty()) }
    if (creating) {
        NameDialog(Texts.NEW_FLAG, onConfirm = { label ->
            creating = false
            var id: String? = null
            if (model.edit { c -> c.addFlag(label).also { id = it.id }.campaign }) id?.let(onSelect)
        }, onDismiss = { creating = false })
    }
}

/** Alcarin Tengwar (OFL), bundled with the editor for previews of inscriptions. */
internal val TengwarFont: androidx.compose.ui.text.font.FontFamily by lazy {
    androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.platform.Font("fonts/alcarin_tengwar.ttf"))
}

/** Field of a marker: tile and hex field on travel maps, column/row on battle maps. */
@Composable
private fun MarkerLocationInput(
    model: io.github.aljadvb.worldbuilder.editor.EditorModel,
    s: Scenario,
    location: io.github.aljadvb.worldbuilder.model.Location,
    key: String,
    path: String,
    set: (io.github.aljadvb.worldbuilder.model.Location) -> Unit,
    setText: (io.github.aljadvb.worldbuilder.model.Location) -> (io.github.aljadvb.worldbuilder.model.Campaign) -> io.github.aljadvb.worldbuilder.model.Campaign,
) {
    when (location) {
        is io.github.aljadvb.worldbuilder.model.SquareLocation -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IntInput(model, "Spalte", location.square.x, "$key.x", path) { v -> setText(location.copy(square = location.square.copy(x = v))) }
            IntInput(model, "Zeile", location.square.y, "$key.y", null) { v -> setText(location.copy(square = location.square.copy(y = v))) }
        }
        is io.github.aljadvb.worldbuilder.model.TileLocation -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChoiceInput(model, Texts.MARKER_TILE, location.placement, s.placements.map { Choice(it.id, it.id) }, path, modifier = Modifier.weight(1f)) { v ->
                set(location.copy(placement = v.orEmpty()))
            }
            IntInput(model, "q", location.cell.q, "$key.q", null, width = 90.dp) { v -> setText(location.copy(cell = location.cell.copy(q = v))) }
            IntInput(model, "r", location.cell.r, "$key.r", null, width = 90.dp) { v -> setText(location.copy(cell = location.cell.copy(r = v))) }
        }
    }
}
