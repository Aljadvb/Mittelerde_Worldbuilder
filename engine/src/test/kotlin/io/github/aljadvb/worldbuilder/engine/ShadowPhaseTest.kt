package io.github.aljadvb.worldbuilder.engine

import io.github.aljadvb.worldbuilder.model.Attribute
import io.github.aljadvb.worldbuilder.model.EnemyAttack
import io.github.aljadvb.worldbuilder.model.EnemyType
import io.github.aljadvb.worldbuilder.model.Event
import io.github.aljadvb.worldbuilder.model.EventStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ShadowPhaseTest {
    private val wolf = EnemyType("nebelwolf", "Nebelwolf", "beispiel", health = 4, armor = 1, sorcery = 0, movement = 3,
        attacks = listOf(EnemyAttack(2, 0, "Biss", Attribute.AGILITY), EnemyAttack(1, 1, "Heulen", Attribute.SPIRIT)))
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
        assertEquals(if (act.damage == 2) Attribute.AGILITY else Attribute.SPIRIT, act.negate)
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
        assertTrue(s.currentPrompt is Prompt.CounterQuestion)
        s = engine.apply(s, Command.CounterAttack(false))
        s = engine.apply(s, Command.AttackEnemy("eg_nebelwoelfe", hits = 2, pierce = true, stun = true)) // first dies
        assertEquals(listOf(0, 4), s.enemyHealth.getValue("eg_nebelwoelfe"))
        assertTrue("eg_nebelwoelfe" in s.exhaustedGroups)
        s = engine.apply(s, Command.Acknowledge)
        assertTrue(s.isIdle, "stunned groups cannot counterattack")
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

    @Test
    fun `counterattack hits the attacker when the group can reach`() {
        var s = withWolves(3)
        s = engine.apply(s, Command.AttackEnemy("eg_nebelwoelfe", hits = 1))
        s = engine.apply(s, Command.Acknowledge)
        s = engine.apply(s, Command.CounterAttack(true))
        val counter = s.currentPrompt as Prompt.EnemyAttack
        assertEquals(AttackReason.COUNTER, counter.reason)
        assertTrue((counter.damage to counter.fear) in listOf(2 to 0, 1 to 1))
        s = engine.apply(s, Command.Acknowledge)
        assertTrue(s.isIdle)
    }

    @Test
    fun `ready groups can be provoked, exhausted ones not`() {
        var s = withWolves(3)
        s = engine.apply(s, Command.ProvokeEnemy("eg_nebelwoelfe"))
        assertEquals(AttackReason.PROVOKED, (s.currentPrompt as Prompt.EnemyAttack).reason)
        s = engine.apply(s, Command.Acknowledge)
        s = engine.apply(s, Command.AttackEnemy("eg_nebelwoelfe", hits = 0, stun = true))
        while (!s.isIdle) s = engine.apply(s, Command.Acknowledge)
        val stunned = s
        assertFailsWith<EngineException> { engine.apply(stunned, Command.ProvokeEnemy("eg_nebelwoelfe")) }
    }

    @Test
    fun `no target and movement ended replace the activation`() {
        val round = engine.apply(withWolves(3), Command.EndRound)
        val act = round.currentPrompt as Prompt.EnemyActivation
        val regroup = engine.apply(round, Command.NoTarget)
        assertEquals(Prompt.EnemyRegroup(act.groupId, act.movement), regroup.currentPrompt)
        val halted = engine.apply(round, Command.MovementEnded)
        assertEquals(Prompt.EnemyHalted(act.groupId, act.damage, act.fear, act.text, act.ranged, act.negate), halted.currentPrompt)
        assertEquals(round.prompts.size, halted.prompts.size)
        assertFailsWith<EngineException> { engine.apply(regroup, Command.NoTarget) }
    }

    @Test
    fun `last stand once, then the hero is defeated and the scenario is lost at the next shadow phase`() {
        var s = withWolves(2)
        s = engine.apply(s, Command.LimitReached(1, LimitKind.FEAR))
        val test = s.currentPrompt as Prompt.LastStandTest
        assertEquals(io.github.aljadvb.worldbuilder.model.Attribute.SPIRIT, test.attribute)
        s = engine.apply(s, Command.ReportTest(1))
        assertEquals(Prompt.HeroSurvives(1, LimitKind.FEAR), s.currentPrompt)
        s = engine.apply(s, Command.Acknowledge)
        // Second time: no last stand any more.
        s = engine.apply(s, Command.LimitReached(1, LimitKind.DAMAGE))
        assertEquals(Prompt.HeroDefeated(1, LimitKind.DAMAGE, secondTime = true), s.currentPrompt)
        s = engine.apply(s, Command.Acknowledge)
        assertEquals(setOf(1), s.defeatedHeroes)
        val defeatedHero = s
        assertFailsWith<EngineException> { engine.apply(defeatedHero, Command.LimitReached(1, LimitKind.FEAR)) }
        val end = engine.apply(s, Command.EndRound)
        val res = scenario.resolutions.first { it.outcome == io.github.aljadvb.worldbuilder.model.Outcome.DEFEAT }
        assertEquals(res.id, end.resolution)
    }

    @Test
    fun `failed last stand defeats the hero and activations skip defeated heroes`() {
        val noDefeat = scenario.copy(resolutions = scenario.resolutions.filter { it.outcome != io.github.aljadvb.worldbuilder.model.Outcome.DEFEAT })
        val e = GameEngine(noDefeat, enemyTypes = mapOf("nebelwolf" to wolf))
        var s = withWolves(2)
        s = e.apply(s, Command.LimitReached(0, LimitKind.DAMAGE))
        assertEquals(io.github.aljadvb.worldbuilder.model.Attribute.MIGHT, (s.currentPrompt as Prompt.LastStandTest).attribute)
        s = e.apply(s, Command.ReportTest(0))
        assertEquals(Prompt.HeroDefeated(0, LimitKind.DAMAGE, secondTime = false), s.currentPrompt)
        s = e.apply(s, Command.Acknowledge)
        var x = s
        repeat(5) {
            x = e.apply(x, Command.EndRound)
            x.prompts.filterIsInstance<Prompt.EnemyActivation>().forEach { assertEquals(1, it.hero) }
            while (!x.isIdle && !x.isOver) x = e.apply(x, Command.Acknowledge)
        }
    }
}
