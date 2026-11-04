package io.github.aljadvb.worldbuilder.ui.game

import io.github.aljadvb.worldbuilder.hexmap.battleSetup
import io.github.aljadvb.worldbuilder.model.SquareLocation
import io.github.aljadvb.worldbuilder.model.placementId
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.aljadvb.worldbuilder.R
import io.github.aljadvb.worldbuilder.engine.Command
import io.github.aljadvb.worldbuilder.engine.Prompt
import io.github.aljadvb.worldbuilder.model.MaterialCatalog
import io.github.aljadvb.worldbuilder.model.Scenario
import io.github.aljadvb.worldbuilder.ui.common.attributeLabel
import io.github.aljadvb.worldbuilder.ui.common.tokenTypeLabel

/** Docked panel showing the current prompt; the map stays visible above it. */
@Composable
fun PromptPanel(
    prompt: Prompt,
    scenario: Scenario,
    catalog: MaterialCatalog,
    heroCount: Int,
    onCommand: (Command) -> Unit,
    modifier: Modifier = Modifier,
    heroNames: List<String> = emptyList(),
    images: io.github.aljadvb.worldbuilder.hexmap.TileImageCache? = null,
    corruption: List<Int> = emptyList(),
) {
    ElevatedCard(modifier.fillMaxWidth(), colors = androidx.compose.material3.CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.9f))) {
        Column(
            Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (prompt) {
                is Prompt.Notice -> {
                    NoticeContent(prompt, scenario, catalog, heroNames, images)
                    if (prompt is Prompt.EnemyActivation) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onCommand(Command.NoTarget) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.enemy_no_target)) }
                            OutlinedButton(onClick = { onCommand(Command.MovementEnded) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.enemy_movement_ended)) }
                        }
                        Button(onClick = { onCommand(Command.Acknowledge) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.done)) }
                    } else {
                        Button(onClick = { onCommand(Command.Acknowledge) }, modifier = Modifier.align(Alignment.End)) {
                            Text(stringResource(R.string.continue_))
                        }
                    }
                }
                is Prompt.CounterQuestion -> {
                    Title(stringResource(R.string.counter_title, enemyName(scenario, catalog, prompt.groupId)))
                    Body(stringResource(R.string.counter_question))
                    Hint(stringResource(R.string.counter_hint))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                        OutlinedButton(onClick = { onCommand(Command.CounterAttack(false)) }) { Text(stringResource(R.string.no)) }
                        Button(onClick = { onCommand(Command.CounterAttack(true)) }) { Text(stringResource(R.string.yes)) }
                    }
                }
                is Prompt.LastStandTest -> {
                    Title(stringResource(R.string.last_stand_title, heroName(heroNames, prompt.hero)))
                    Body(stringResource(when (prompt.kind) {
                        io.github.aljadvb.worldbuilder.engine.LimitKind.DAMAGE -> R.string.last_stand_damage
                        io.github.aljadvb.worldbuilder.engine.LimitKind.FEAR -> R.string.last_stand_fear
                        io.github.aljadvb.worldbuilder.engine.LimitKind.CORRUPTION -> R.string.last_stand_corruption
                    }, attributeLabel(prompt.attribute)))
                    Hint(stringResource(if (prompt.kind == io.github.aljadvb.worldbuilder.engine.LimitKind.CORRUPTION) R.string.last_stand_corruption_hint else R.string.last_stand_hint))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                        OutlinedButton(onClick = { onCommand(Command.ReportTest(0)) }) { Text(stringResource(R.string.last_stand_failed)) }
                        Button(onClick = { onCommand(Command.ReportTest(1)) }) { Text(stringResource(R.string.last_stand_passed)) }
                    }
                }
                is Prompt.Choice -> {
                    Body(prompt.step.text)
                    prompt.step.options.forEachIndexed { i, option ->
                        FilledTonalButton(onClick = { onCommand(Command.Choose(i)) }, modifier = Modifier.fillMaxWidth()) {
                            // Corruption is always shown before the players decide.
                            Text(option.label + if (option.corruption > 0) " · " + stringResource(R.string.corruption_badge, option.corruption) else "")
                        }
                    }
                    if (prompt.step.options.any { it.corruption > 0 }) Hint(stringResource(R.string.corruption_option_hint))
                }
                is Prompt.ChooseHero -> {
                    prompt.text?.let { Body(it) }
                    Title(when (val q = prompt.question) {
                        is io.github.aljadvb.worldbuilder.engine.HeroQuestion.GainCorruption -> stringResource(R.string.choose_hero_gain, q.amount)
                        is io.github.aljadvb.worldbuilder.engine.HeroQuestion.LoseCorruption -> stringResource(R.string.choose_hero_lose, q.amount)
                        is io.github.aljadvb.worldbuilder.engine.HeroQuestion.CheckCorruption -> stringResource(R.string.choose_hero_check)
                    })
                    (0 until heroCount).forEach { h ->
                        FilledTonalButton(onClick = { onCommand(Command.ChooseHero(h)) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.hero_with_corruption, heroName(heroNames, h), corruption.getOrElse(h) { 0 }))
                        }
                    }
                }
                is Prompt.Test -> TestContent(prompt, onCommand)
            }
        }
    }
}

@Composable
private fun Title(text: String) = Text(text, style = MaterialTheme.typography.titleLarge)

@Composable
private fun Body(text: String) = Text(text, style = MaterialTheme.typography.bodyLarge)

@Composable
private fun Hint(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun NoticeContent(prompt: Prompt.Notice, scenario: Scenario, catalog: MaterialCatalog, heroNames: List<String> = emptyList(), images: io.github.aljadvb.worldbuilder.hexmap.TileImageCache? = null) {
    fun tileLabel(placementId: String): String {
        val p = scenario.placement(placementId) ?: return placementId
        return catalog.tile(p.tile)?.label ?: p.tile
    }
    when (prompt) {
        Prompt.Intro -> {
            Title(scenario.title)
            io.github.aljadvb.worldbuilder.ui.theme.Ornament("band")
            Body(scenario.intro)
        }
        is Prompt.Text -> Body(prompt.text)
        is Prompt.Inspect -> {
            val token = scenario.token(prompt.tokenId)
            Title(token?.label.orEmpty())
            Body(token?.inspectText.orEmpty())
        }
        is Prompt.Setup -> {
            val chapter = scenario.chapter(prompt.chapterId) ?: return
            Title(stringResource(R.string.prompt_setup_title, chapter.title))
            if (chapter.setupText.isNotBlank()) Body(chapter.setupText)
            scenario.battleSetup(chapter.id, catalog)?.let { setup ->
                if (setup.clearFirst) Text(stringResource(R.string.prompt_setup_clear), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                if (setup.boards.isNotEmpty()) {
                    Text(stringResource(R.string.prompt_setup_boards), fontWeight = FontWeight.Bold)
                    setup.boards.forEach { (label, side) -> Text("• " + stringResource(R.string.prompt_setup_board_line, label, side.name)) }
                }
                if (setup.pieces.isNotEmpty()) {
                    Text(stringResource(R.string.prompt_setup_terrain), fontWeight = FontWeight.Bold)
                    setup.pieces.forEach { (name, n) -> Text("• " + stringResource(R.string.prompt_setup_terrain_line, n, name)) }
                }
            }
            if (chapter.tiles.isNotEmpty()) {
                Text(stringResource(R.string.prompt_setup_tiles), fontWeight = FontWeight.Bold)
                chapter.tiles.forEach {
                    Text("• " + stringResource(if (it.unexplored) R.string.prompt_setup_tile_unexplored else R.string.prompt_setup_tile_line, tileLabel(it.id), it.side.name))
                }
            }
            val tokens = chapter.tokens.filter { it.initiallyActive }
            if (tokens.isNotEmpty()) {
                Text(stringResource(R.string.prompt_setup_tokens), fontWeight = FontWeight.Bold)
                tokens.forEach { t ->
                    Text("• " + if (t.location is SquareLocation) stringResource(R.string.prompt_setup_token_line_battle, t.label, tokenTypeLabel(t))
                        else stringResource(R.string.prompt_setup_token_line, t.label, tokenTypeLabel(t), tileLabel(t.location.placementId.orEmpty())))
                }
            }
            if (chapter.id == scenario.startChapter && scenario.effectiveHeroStart != null) {
                Text(stringResource(R.string.prompt_setup_heroes), fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color(0xFF2E7D32))
            }
            Hint(stringResource(if (chapter.isBattle) R.string.prompt_setup_hint_battle else R.string.prompt_setup_hint))
        }
        is Prompt.SpawnEnemies -> {
            val group = scenario.enemyGroup(prompt.groupId) ?: return
            Title(stringResource(R.string.prompt_spawn_title))
            val name = catalog.enemyType(group.enemyType)?.name ?: group.enemyType
            Body(
                if (group.location is SquareLocation) stringResource(R.string.prompt_spawn_text_battle, prompt.count, name)
                else stringResource(R.string.prompt_spawn_text, prompt.count, name, tileLabel(group.location.placementId.orEmpty())),
            )
            prompt.banner?.let { Text(stringResource(if (prompt.elite) R.string.prompt_spawn_elite else R.string.prompt_spawn_banner, it), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) }
            EliteInfo(group)
        }
        is Prompt.PlaceToken -> {
            val token = scenario.token(prompt.tokenId) ?: return
            Title(stringResource(R.string.prompt_place_token_title))
            Body(
                if (token.location is SquareLocation) stringResource(R.string.prompt_place_token_text_battle, token.label, tokenTypeLabel(token))
                else stringResource(R.string.prompt_place_token_text, token.label, tokenTypeLabel(token), tileLabel(token.location.placementId.orEmpty())),
            )
        }
        is Prompt.RemoveToken -> {
            val token = scenario.token(prompt.tokenId) ?: return
            Title(stringResource(R.string.prompt_remove_token_title))
            Body(
                if (token.location is SquareLocation) stringResource(R.string.prompt_remove_token_text_battle, token.label)
                else stringResource(R.string.prompt_remove_token_text, token.label, tileLabel(token.location.placementId.orEmpty())),
            )
        }
        is Prompt.ThreatChanged -> {
            Title(stringResource(R.string.prompt_threat_title))
            Body(
                if (prompt.delta > 0) stringResource(R.string.prompt_threat_up, prompt.delta, prompt.threat)
                else stringResource(R.string.prompt_threat_down, -prompt.delta, prompt.threat),
            )
        }
        is Prompt.RoundEnded -> {
            Title(stringResource(R.string.prompt_round_title, prompt.round))
            Body(stringResource(R.string.prompt_round_text, prompt.threatAdded, prompt.threat))
        }
        is Prompt.ObjectiveSet -> {
            val objective = scenario.objective(prompt.objectiveId) ?: return
            Hint(stringResource(R.string.prompt_objective_set_title))
            Title(objective.title)
            Body(objective.reminder)
        }
        is Prompt.Explored -> {
            Title(stringResource(R.string.prompt_explored_title, tileLabel(prompt.placementId)))
            Body(stringResource(R.string.prompt_explored_inspiration))
            val tokens = prompt.tokenIds.mapNotNull { scenario.token(it) }
            if (tokens.isNotEmpty()) {
                Text(stringResource(R.string.prompt_setup_tokens), fontWeight = FontWeight.Bold)
                tokens.forEach { t -> Text("• " + stringResource(R.string.prompt_setup_token_line, t.label, tokenTypeLabel(t), tileLabel(prompt.placementId))) }
            }
            Hint(stringResource(R.string.prompt_explored_hint))
        }
        is Prompt.EnemyActivation -> {
            val group = scenario.enemyGroup(prompt.groupId) ?: return
            val name = catalog.enemyType(group.enemyType)?.name ?: group.enemyType
            val hero = heroNames.getOrNull(prompt.hero)?.takeIf { it.isNotBlank() } ?: stringResource(R.string.hero_n, prompt.hero + 1)
            Hint(stringResource(R.string.shadow_phase))
            Title(stringResource(R.string.enemy_activation_title, name))
            Body(stringResource(if (prompt.ranged) R.string.enemy_activation_ranged else R.string.enemy_activation_text, prompt.movement, hero))
            Text(stringResource(R.string.enemy_attack_values, prompt.damage, prompt.fear), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            if (prompt.text.isNotBlank()) Body(prompt.text)
            NegateLine(prompt.negate)
            prompt.condition?.let { Body(stringResource(R.string.attack_condition, conditionName(it))) }
            Hint(stringResource(R.string.enemy_activation_hint))
        }
        is Prompt.EnemyRegroup -> {
            Hint(stringResource(R.string.shadow_phase))
            Title(stringResource(R.string.enemy_no_target_title, enemyName(scenario, catalog, prompt.groupId)))
            Body(stringResource(R.string.enemy_regroup_text, prompt.movement))
        }
        is Prompt.EnemyHalted -> {
            Hint(stringResource(R.string.shadow_phase))
            Title(stringResource(R.string.enemy_halted_title, enemyName(scenario, catalog, prompt.groupId)))
            Body(stringResource(R.string.enemy_halted_text))
            Text(stringResource(R.string.enemy_attack_values, prompt.damage, prompt.fear), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            if (prompt.text.isNotBlank()) Body(prompt.text)
            NegateLine(prompt.negate)
            prompt.condition?.let { Body(stringResource(R.string.attack_condition, conditionName(it))) }
        }
        is Prompt.EnemyAttack -> {
            val name = enemyName(scenario, catalog, prompt.groupId)
            Title(stringResource(if (prompt.reason == io.github.aljadvb.worldbuilder.engine.AttackReason.COUNTER) R.string.enemy_counter_title else R.string.enemy_provoked_title, name))
            Body(stringResource(if (prompt.reason == io.github.aljadvb.worldbuilder.engine.AttackReason.COUNTER) R.string.enemy_counter_text else R.string.enemy_provoked_text))
            Text(stringResource(R.string.enemy_attack_values, prompt.damage, prompt.fear), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            if (prompt.text.isNotBlank()) Body(prompt.text)
            NegateLine(prompt.negate)
            prompt.condition?.let { Body(stringResource(R.string.attack_condition, conditionName(it))) }
        }
        is Prompt.HeroSurvives -> {
            Title(stringResource(R.string.hero_survives_title, heroName(heroNames, prompt.hero)))
            Body(stringResource(when (prompt.kind) {
                io.github.aljadvb.worldbuilder.engine.LimitKind.DAMAGE -> R.string.hero_survives_damage
                io.github.aljadvb.worldbuilder.engine.LimitKind.FEAR -> R.string.hero_survives_fear
                io.github.aljadvb.worldbuilder.engine.LimitKind.CORRUPTION -> R.string.hero_survives_corruption
            }))
            if (prompt.kind != io.github.aljadvb.worldbuilder.engine.LimitKind.CORRUPTION) Hint(stringResource(R.string.hero_survives_hint))
        }
        is Prompt.HeroDefeated -> {
            Title(stringResource(R.string.hero_defeated_title, heroName(heroNames, prompt.hero)))
            if (prompt.secondTime) Body(stringResource(R.string.hero_defeated_second))
            Body(stringResource(R.string.hero_defeated_text))
        }
        is Prompt.EnemyRecovers -> {
            val name = scenario.enemyGroup(prompt.groupId)?.let { catalog.enemyType(it.enemyType)?.name ?: it.enemyType }.orEmpty()
            Hint(stringResource(R.string.shadow_phase))
            Body(stringResource(R.string.enemy_recovers, name))
        }
        is Prompt.Darkness -> {
            Hint(stringResource(R.string.shadow_phase))
            Title(stringResource(R.string.darkness_title))
            Body(stringResource(R.string.darkness_text, prompt.fear))
        }
        is Prompt.AttackResult -> {
            val name = scenario.enemyGroup(prompt.groupId)?.let { catalog.enemyType(it.enemyType)?.name ?: it.enemyType }.orEmpty()
            Title(name)
            Body(if (prompt.remaining == 0) stringResource(R.string.attack_all_defeated) else stringResource(R.string.attack_result, prompt.defeated, prompt.remaining))
            if (prompt.stunned) Body(stringResource(R.string.attack_stunned))
            if (prompt.defeated > 0) Hint(stringResource(R.string.attack_remove_hint, prompt.defeated))
        }
        is Prompt.Lore -> {
            Title(stringResource(R.string.prompt_lore_title))
            prompt.text?.let { Body(it) }
            Body(stringResource(R.string.prompt_lore_text, prompt.amount))
            Hint(stringResource(R.string.prompt_lore_hint))
        }
        is Prompt.Harm -> {
            Title(stringResource(R.string.prompt_harm_title))
            prompt.text?.let { Body(it) }
            Body(harmText(prompt))
            Hint(stringResource(R.string.prompt_harm_hint))
        }
        is Prompt.TestResult -> Title(stringResource(if (prompt.passed) R.string.test_passed else R.string.test_failed))
        is Prompt.TestProgress -> {
            Title(stringResource(R.string.test_progress_title))
            Body(if (prompt.step.hidden) stringResource(R.string.test_progress_hidden) else stringResource(R.string.test_progress_open, prompt.total, prompt.step.successes))
            Hint(stringResource(R.string.prompt_test_cumulative_hint))
        }
        is Prompt.Inscription -> {
            Title(stringResource(R.string.inscription_title))
            prompt.text?.let { Body(it) }
            Text(io.github.aljadvb.worldbuilder.model.Tengwar.transliterate(prompt.elvish), fontFamily = io.github.aljadvb.worldbuilder.ui.theme.TengwarFamily,
                style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Text(prompt.elvish, style = MaterialTheme.typography.bodyMedium, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (prompt.translationHidden) Hint(stringResource(R.string.inscription_riddle))
            else Text(stringResource(R.string.inscription_translation, prompt.translation), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
        is Prompt.Corruption -> {
            Title(stringResource(R.string.corruption_title))
            prompt.text?.let { Body(it) }
            Body(corruptionText(prompt, heroNames))
        }
        is Prompt.CorruptionSetup -> {
            Title(stringResource(R.string.corruption_setup_title))
            prompt.values.withIndex().filter { it.value > 0 }.forEach { (h, v) -> Body(stringResource(R.string.corruption_setup_line, heroName(heroNames, h), v)) }
        }
        is Prompt.FieldMarker -> {
            val name = markerName(prompt.marker)
            Title(stringResource(if (prompt.placed) R.string.marker_place_title else R.string.marker_remove_title, name))
            prompt.text?.let { Body(it) }
            Body(when (val l = prompt.location) {
                is io.github.aljadvb.worldbuilder.model.TileLocation -> stringResource(R.string.marker_where_tile, tileLabel(l.placement))
                is io.github.aljadvb.worldbuilder.model.SquareLocation -> stringResource(R.string.marker_where_square)
            })
            if (prompt.placed) Hint(markerRule(prompt.marker))
        }
        is Prompt.Condition -> {
            val name = conditionName(prompt.condition)
            Title(stringResource(if (prompt.condition.isCurse) R.string.condition_curse_title else R.string.condition_boon_title, name))
            prompt.text?.let { Body(it) }
            Body(conditionText(prompt))
            catalog.condition(prompt.condition)?.let { Text(it.effect, style = MaterialTheme.typography.bodyMedium, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic) }
            Hint(when {
                prompt.condition == io.github.aljadvb.worldbuilder.model.Condition.CAPTURED -> stringResource(R.string.condition_captured_hint)
                prompt.condition.isCurse -> stringResource(R.string.condition_curse_hint, name)
                else -> stringResource(R.string.condition_boon_hint, name)
            })
        }
        is Prompt.Recover -> {
            Title(stringResource(R.string.recover_title))
            prompt.text?.let { Body(it) }
            Body(recoverText(prompt))
            Hint(stringResource(R.string.prompt_harm_hint))
        }
        is Prompt.ItemFound -> {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                io.github.aljadvb.worldbuilder.ui.theme.Ornament("ring", size = 40)
                Title(stringResource(R.string.item_found_title))
            }
            prompt.text?.let { Body(it) }
            val item = prompt.itemId?.let { catalog.item(it) }
            if (prompt.itemId == null) {
                Body(stringResource(R.string.item_found_none))
            } else {
                Text(item?.name ?: prompt.itemId.orEmpty(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Body(stringResource(if (prompt.who == io.github.aljadvb.worldbuilder.model.HeroTarget.CHOSEN) R.string.item_found_chosen else R.string.item_found_active))
                item?.image?.let { images?.bitmap(it.file) }?.let {
                    androidx.compose.foundation.Image(it, contentDescription = item?.name, contentScale = androidx.compose.ui.layout.ContentScale.Fit, modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp))
                }
                item?.cardText?.takeIf { it.isNotBlank() }?.let { Body(it) } ?: item?.effect?.takeIf { it.isNotBlank() }?.let { Body(it) }
                Hint(stringResource(R.string.item_found_hint))
            }
        }
        is Prompt.Inspiration -> {
            Title(stringResource(R.string.prompt_inspiration_title))
            prompt.text?.let { Body(it) }
            Body(inspirationText(prompt))
            Hint(stringResource(R.string.prompt_inspiration_hint))
        }
        is Prompt.ObjectiveCompleted -> {
            val objective = scenario.objective(prompt.objectiveId) ?: return
            Hint(stringResource(R.string.prompt_objective_done_title))
            Title(objective.title)
        }
    }
}

/** "Abwehr: Probe auf X" – each success prevents 1 damage or 1 fear. */
@Composable
private fun NegateLine(attribute: io.github.aljadvb.worldbuilder.model.Attribute?) {
    if (attribute == null) {
        Hint(stringResource(R.string.negate_none))
        return
    }
    Text(stringResource(R.string.negate_title, attributeLabel(attribute)), fontWeight = FontWeight.Bold)
    Hint(stringResource(R.string.negate_hint))
}

@Composable
private fun enemyName(scenario: Scenario, catalog: MaterialCatalog, groupId: String): String =
    scenario.enemyGroup(groupId)?.let { catalog.enemyType(it.enemyType)?.name ?: it.enemyType }.orEmpty()

@Composable
internal fun heroName(heroNames: List<String>, hero: Int): String =
    heroNames.getOrNull(hero)?.takeIf { it.isNotBlank() } ?: stringResource(R.string.hero_n, hero + 1)

@Composable
private fun TestContent(prompt: Prompt.Test, onCommand: (Command) -> Unit) {
    var successes by rememberSaveable(prompt) { mutableIntStateOf(0) }
    Title(stringResource(if (prompt.step.cumulative) R.string.prompt_test_title_cumulative else R.string.prompt_test_title, attributeLabel(prompt.step.attribute)))
    prompt.step.text?.let { Body(it) }
    when {
        prompt.step.hidden -> Text(stringResource(R.string.prompt_test_hidden), fontWeight = FontWeight.Bold)
        prompt.step.cumulative -> Text(stringResource(R.string.prompt_test_cumulative, prompt.step.successes, prompt.progress), fontWeight = FontWeight.Bold)
        else -> Text(stringResource(R.string.prompt_test_required, prompt.required), fontWeight = FontWeight.Bold)
    }
    if (prompt.eased) Hint(stringResource(R.string.test_eased))
    if (prompt.canEase) {
        OutlinedButton(onClick = { onCommand(Command.EaseTest) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.test_ease_button, prompt.step.corruptionEase))
        }
        Hint(stringResource(R.string.test_ease_hint))
    }
    if (prompt.step.cumulative) Hint(stringResource(R.string.prompt_test_cumulative_hint))
    Text(stringResource(R.string.prompt_test_successes))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedButton(onClick = { if (successes > 0) successes-- }) { Text("−") }
        Text("$successes", style = MaterialTheme.typography.headlineMedium)
        OutlinedButton(onClick = { if (successes < 30) successes++ }) { Text("+") }
    }
    Button(onClick = { onCommand(Command.ReportTest(successes)) }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.confirm))
    }
}


@Composable
internal fun inspirationText(prompt: Prompt.Inspiration): String = when (prompt.who) {
    io.github.aljadvb.worldbuilder.model.HeroTarget.ACTIVE -> stringResource(R.string.inspiration_active, prompt.amount)
    io.github.aljadvb.worldbuilder.model.HeroTarget.EACH -> stringResource(R.string.inspiration_each, prompt.amount)
    io.github.aljadvb.worldbuilder.model.HeroTarget.CHOSEN -> stringResource(R.string.inspiration_chosen, prompt.amount)
}

@Composable
internal fun conditionName(c: io.github.aljadvb.worldbuilder.model.Condition): String = stringResource(when (c) {
    io.github.aljadvb.worldbuilder.model.Condition.HIDDEN -> R.string.condition_hidden
    io.github.aljadvb.worldbuilder.model.Condition.INSPIRED -> R.string.condition_inspired
    io.github.aljadvb.worldbuilder.model.Condition.DETERMINED -> R.string.condition_determined
    io.github.aljadvb.worldbuilder.model.Condition.CAPTURED -> R.string.condition_captured
    io.github.aljadvb.worldbuilder.model.Condition.DISHEARTENED -> R.string.condition_disheartened
    io.github.aljadvb.worldbuilder.model.Condition.FRIGHTENED -> R.string.condition_frightened
})

@Composable
internal fun conditionText(prompt: Prompt.Condition): String = when (prompt.who) {
    io.github.aljadvb.worldbuilder.model.HeroTarget.ACTIVE -> stringResource(R.string.condition_active, conditionName(prompt.condition))
    io.github.aljadvb.worldbuilder.model.HeroTarget.EACH -> stringResource(R.string.condition_each, conditionName(prompt.condition))
    io.github.aljadvb.worldbuilder.model.HeroTarget.CHOSEN -> stringResource(R.string.condition_chosen, conditionName(prompt.condition))
}

@Composable
internal fun recoverText(prompt: Prompt.Recover): String {
    val parts = listOfNotNull(
        prompt.damage.takeIf { it > 0 }?.let { stringResource(R.string.harm_damage, it) },
        prompt.fear.takeIf { it > 0 }?.let { stringResource(R.string.harm_fear, it) },
    ).joinToString(stringResource(R.string.harm_and))
    return when (prompt.who) {
        io.github.aljadvb.worldbuilder.model.HeroTarget.ACTIVE -> stringResource(R.string.recover_active, parts)
        io.github.aljadvb.worldbuilder.model.HeroTarget.EACH -> stringResource(R.string.recover_each, parts)
        io.github.aljadvb.worldbuilder.model.HeroTarget.CHOSEN -> stringResource(R.string.recover_chosen, parts)
    }
}

@Composable
internal fun harmText(prompt: Prompt.Harm): String {
    val parts = listOfNotNull(
        prompt.damage.takeIf { it > 0 }?.let { stringResource(R.string.harm_damage, it) },
        prompt.fear.takeIf { it > 0 }?.let { stringResource(R.string.harm_fear, it) },
    ).joinToString(stringResource(R.string.harm_and)) + if (prompt.hidden) stringResource(R.string.harm_hidden) else ""
    return when (prompt.who) {
        io.github.aljadvb.worldbuilder.model.HeroTarget.ACTIVE -> stringResource(R.string.harm_active, parts)
        io.github.aljadvb.worldbuilder.model.HeroTarget.EACH -> stringResource(R.string.harm_each, parts)
        io.github.aljadvb.worldbuilder.model.HeroTarget.CHOSEN -> stringResource(R.string.harm_chosen, parts)
    }
}

/** Elite section of an enemy group: the story reason and each bonus with its effect. Nothing for normal groups. */
@Composable
internal fun EliteInfo(group: io.github.aljadvb.worldbuilder.model.EnemyGroup) {
    if (!group.elite) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.elite_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
        group.eliteReason?.let { Text(it, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic) }
        group.eliteBonuses.forEach { b -> Text("• " + stringResource(eliteBonusText(b)), style = MaterialTheme.typography.bodyMedium) }
        Hint(stringResource(R.string.elite_counter_hint))
    }
}

private fun eliteBonusText(b: io.github.aljadvb.worldbuilder.model.EliteBonus): Int = when (b) {
    io.github.aljadvb.worldbuilder.model.EliteBonus.TOUGH -> R.string.elite_tough
    io.github.aljadvb.worldbuilder.model.EliteBonus.ARMORED -> R.string.elite_armored
    io.github.aljadvb.worldbuilder.model.EliteBonus.WARDED -> R.string.elite_warded
    io.github.aljadvb.worldbuilder.model.EliteBonus.CRUEL -> R.string.elite_cruel
    io.github.aljadvb.worldbuilder.model.EliteBonus.TERRIFYING -> R.string.elite_terrifying
    io.github.aljadvb.worldbuilder.model.EliteBonus.SWIFT -> R.string.elite_swift
    io.github.aljadvb.worldbuilder.model.EliteBonus.STEADFAST -> R.string.elite_steadfast
    io.github.aljadvb.worldbuilder.model.EliteBonus.FURIOUS -> R.string.elite_furious
}

@Composable
internal fun corruptionText(prompt: Prompt.Corruption, heroNames: List<String>): String =
    if (prompt.delta > 0) stringResource(R.string.corruption_gain, heroName(heroNames, prompt.hero), prompt.delta, prompt.total)
    else stringResource(R.string.corruption_lose, heroName(heroNames, prompt.hero), -prompt.delta, prompt.total)

@Composable
internal fun markerName(m: io.github.aljadvb.worldbuilder.model.FieldMarker): String = stringResource(when (m) {
    io.github.aljadvb.worldbuilder.model.FieldMarker.ROUGH -> R.string.marker_rough
    io.github.aljadvb.worldbuilder.model.FieldMarker.FORTIFIED -> R.string.marker_fortified
    io.github.aljadvb.worldbuilder.model.FieldMarker.DARKNESS -> R.string.marker_darkness
})

@Composable
internal fun markerRule(m: io.github.aljadvb.worldbuilder.model.FieldMarker): String = stringResource(when (m) {
    io.github.aljadvb.worldbuilder.model.FieldMarker.ROUGH -> R.string.marker_rough_rule
    io.github.aljadvb.worldbuilder.model.FieldMarker.FORTIFIED -> R.string.marker_fortified_rule
    io.github.aljadvb.worldbuilder.model.FieldMarker.DARKNESS -> R.string.marker_darkness_rule
})
