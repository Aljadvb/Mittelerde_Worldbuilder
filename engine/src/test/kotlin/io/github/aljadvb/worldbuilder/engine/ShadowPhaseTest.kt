package io.github.aljadvb.worldbuilder.engine

import io.github.aljadvb.worldbuilder.model.EnemyAttack
import io.github.aljadvb.worldbuilder.model.EnemyType
import io.github.aljadvb.worldbuilder.model.Event
import io.github.aljadvb.worldbuilder.model.EventStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShadowPhaseTest {
    private val wolf = EnemyType("nebelwolf", "Nebelwolf", "beispiel", health = 4, armor = 1, sorcery = 0, movement = 3,
        attacks = listOf(EnemyAttack(2, 0, "Biss"), EnemyAttack(1, 1, "Heulen")))
    private val base = Examples.scenario
    private val scenario = base.copy(
        events = base.events + listOf(
            Event("evt_still", listOf(EventStep.Text("Eine ruhige Nacht."))),
            Event("evt_dunkel", listOf(EventStep.SetDarkness(1))),
        ),
        shadowEvents = listOf("evt_still"),
        chapters = base.chapters.map { if (it.id == base.startChapter) it.copy(onUnlock = "evt_dunkel") else it },
    )
    private val engine = GameEngine(scenario, enemyTypes = mapOf("nebelwolf" to wolf))

    private fun withWolves(heroes: Int): GameState {
        var s = engine.start(heroes)
        while (!s.isIdle) s = engine.apply(s, Command.Acknowledge)
        // Spawn directly via a fired event: use the group spawn of the example.
        val spawn = scenario.copy(events = scenario.events + Event("evt_spawn", listOf(EventStep.SpawnEnemies("eg_nebelwoelfe"))))
        return s.copy(agenda = listOf(Frame.EventFrame("evt_spawn"))).let { st -> GameEngine(spawn, enemyTypes = mapOf("nebelwolf" to wolf)) to st }
            .let { (e, st) -> var x = e.apply(st.copy(prompts = listOf(Prompt.Intro)), Command.Acknowledge); while (!x.isIdle) x = e.apply(x, Command.Acknowledge); x }
    }

    @Test
    fun `round end activates enemies, darkness, shadow event and threat`() {
        val s = withWolves(3)
        assertEquals(listOf(4, 4), s.enemyHealth.getValue("eg_nebelwoelfe"))
        assertEquals(1, s.darknessFear)
        val after = engine.apply(s, Command.EndRound)
        val kinds = after.prompts.map { it::class.simpleName }
        assertEquals(listOf("EnemyActivation", "Darkness", "RoundEnded"), kinds)
        val act = after.prompts.first() as Prompt.EnemyActivation
        assertTrue(act.hero in 0..2 && act.movement == 3 && (act.damage to act.fear) in listOf(2 to 0, 1 to 1))
        var x = after
        val texts = mutableListOf<String>()
        while (!x.isIdle) { (x.currentPrompt as? Prompt.Text)?.let { texts += it.text }; x = engine.apply(x, Command.Acknowledge) }
        assertTrue("Eine ruhige Nacht." in texts)
        // Deterministic: the same state gives the same result.
        assertEquals(after, engine.apply(s, Command.EndRound))
    }

    @Test
    fun `attacks use health, armor and modifiers`() {
        var s = withWolves(3)
        s = engine.apply(s, Command.AttackEnemy("eg_nebelwoelfe", hits = 3)) // armor 1 -> 2 hits
        assertEquals(listOf(2, 4), s.enemyHealth.getValue("eg_nebelwoelfe"))
        s = engine.apply(s, Command.Acknowledge)
        s = engine.apply(s, Command.AttackEnemy("eg_nebelwoelfe", hits = 2, pierce = true, stun = true)) // first dies
        assertEquals(listOf(0, 4), s.enemyHealth.getValue("eg_nebelwoelfe"))
        assertTrue("eg_nebelwoelfe" in s.exhaustedGroups)
        s = engine.apply(s, Command.Acknowledge)
        val round = engine.apply(s, Command.EndRound)
        assertTrue(round.prompts.first() is Prompt.EnemyRecovers)
        var x = round
        while (!x.isIdle) x = engine.apply(x, Command.Acknowledge)
        x = engine.apply(x, Command.AttackEnemy("eg_nebelwoelfe", hits = 3, lethal = true)) // 2 effective >= half of 4
        assertTrue("eg_nebelwoelfe" !in x.activeEnemyGroups && "eg_nebelwoelfe" in x.defeatedEnemyGroups)
    }

    @Test
    fun `cleave hits every enemy`() {
        var s = withWolves(3)
        s = engine.apply(s, Command.AttackEnemy("eg_nebelwoelfe", hits = 3, cleave = true))
        assertEquals(listOf(2, 2), s.enemyHealth.getValue("eg_nebelwoelfe"))
    }
}
