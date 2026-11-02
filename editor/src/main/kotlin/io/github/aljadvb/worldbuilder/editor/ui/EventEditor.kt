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
        is EventStep.EndScenario -> "endScenario"
    }
    return Texts.STEP_TYPES.first { it.first == type }.second
}

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
            }
            ChoiceInput(model, Texts.ON_SUCCESS, step.onSuccess, events, "$p.onSuccess", onCreate = { newEvent { id -> set(step.copy(onSuccess = id)) } }) { v ->
                set(step.copy(onSuccess = v.orEmpty()))
            }
            ChoiceInput(model, Texts.ON_FAILURE, step.onFailure, events, "$p.onFailure", allowNone = true, onCreate = { newEvent { id -> set(step.copy(onFailure = id)) } }) { v ->
                set(step.copy(onFailure = v))
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
        is EventStep.EndScenario -> ChoiceInput(model, Texts.RESOLUTION, step.resolution, s.resolutions.map { Choice(it.id, "${it.title} · ${it.id}") }, "$p.resolution") { v ->
            set(step.copy(resolution = v.orEmpty()))
        }
    }
}
