package io.github.aljadvb.worldbuilder.engine

import io.github.aljadvb.worldbuilder.model.Attribute
import io.github.aljadvb.worldbuilder.model.EliteBonus
import io.github.aljadvb.worldbuilder.model.EnemyAttack
import io.github.aljadvb.worldbuilder.model.EnemyType
import io.github.aljadvb.worldbuilder.model.Event
import io.github.aljadvb.worldbuilder.model.EventStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EliteTest {
    private val wolf = EnemyType("nebelwolf", "Nebelwolf", "beispiel", health = 4, armor = 1, sorcery = 0, movement = 3,
        attacks = listOf(EnemyAttack(2, 0, "Biss", Attribute.AGILITY)))
    private val base = Examples.scenario

    private fun engineWith(vararg bonuses: EliteBonus, elite: Boolean = true, extraNormal: Boolean = false): GameEngine {
        val g = base.enemyGroups.single { it.id == "eg_nebelwoelfe" }
        val eliteGroup = g.copy(elite = elite, eliteBonuses = bonuses.toList(), eliteReason = "Der Leitwolf führt das Rudel an.")
        val groups = base.enemyGroups.map { if (it.id == g.id) eliteGroup else it } + if (extraNormal) listOf(g.copy(id = "eg_normal")) else emptyList()
        val spawn = listOf(EventStep.SpawnEnemies("eg_nebelwoelfe")) + if (extraNormal) listOf(EventStep.SpawnEnemies("eg_normal")) else emptyList()
        return GameEngine(base.copy(enemyGroups = groups, events = base.events + Event("evt_spawn", spawn)), enemyTypes = mapOf("nebelwolf" to wolf))
    }

    private fun spawned(e: GameEngine, seen: MutableList<Prompt> = mutableListOf()): GameState {
        var s = e.start(3)
        while (!s.isIdle) s = e.apply(s, Command.Acknowledge)
        s = s.copy(agenda = listOf(Frame.EventFrame("evt_spawn")), prompts = listOf(Prompt.Intro))
        while (!s.isIdle) { seen += s.currentPrompt!!; s = e.apply(s, Command.Acknowledge) }
        return s
    }

    @Test
    fun `bonuses change health, armor and attacks`() {
        val e = engineWith(EliteBonus.TOUGH, EliteBonus.ARMORED, EliteBonus.CRUEL)
        var s = spawned(e)
        assertEquals(listOf(6, 6), s.enemyHealth.getValue("eg_nebelwoelfe"))
        s = e.apply(s, Command.AttackEnemy("eg_nebelwoelfe", hits = 4)) // armor 2 -> 2 hits
        assertEquals(listOf(4, 6), s.enemyHealth.getValue("eg_nebelwoelfe"))
        val provoked = e.apply(spawned(e), Command.ProvokeEnemy("eg_nebelwoelfe")).currentPrompt as Prompt.EnemyAttack
        assertEquals(3, provoked.damage)
    }

    @Test
    fun `elite group gets an elite banner, normal groups count separately`() {
        val seen = mutableListOf<Prompt>()
        spawned(engineWith(EliteBonus.SWIFT, extraNormal = true), seen)
        val spawns = seen.filterIsInstance<Prompt.SpawnEnemies>()
        assertEquals(listOf(1 to true, null to false), spawns.map { it.banner to it.elite })
    }

    @Test
    fun `exhausted elite groups still counterattack, stunned ones not`() {
        val e = engineWith(EliteBonus.FURIOUS)
        var s = spawned(e)
        s = e.apply(s, Command.AttackEnemy("eg_nebelwoelfe", hits = 1))
        s = e.apply(s, Command.Acknowledge)
        s = e.apply(s, Command.CounterAttack(true))
        assertEquals(3, (s.currentPrompt as Prompt.EnemyAttack).damage) // furious: +1 on counterattacks
        s = e.apply(s, Command.Acknowledge)
        assertTrue("eg_nebelwoelfe" in s.exhaustedGroups)
        // Exhausted, but elite: asks again.
        s = e.apply(s, Command.AttackEnemy("eg_nebelwoelfe", hits = 1))
        s = e.apply(s, Command.Acknowledge)
        assertTrue(s.currentPrompt is Prompt.CounterQuestion)
        s = e.apply(s, Command.CounterAttack(false))
        // Stunned: no counterattack, not even from an elite group.
        s = e.apply(s, Command.AttackEnemy("eg_nebelwoelfe", hits = 1, stun = true))
        s = e.apply(s, Command.Acknowledge)
        assertTrue(s.isIdle)
    }

    @Test
    fun `normal groups are exhausted after a counterattack`() {
        val e = engineWith(elite = false)
        var s = spawned(e)
        s = e.apply(s, Command.AttackEnemy("eg_nebelwoelfe", hits = 1))
        s = e.apply(s, Command.Acknowledge)
        s = e.apply(s, Command.CounterAttack(true))
        s = e.apply(s, Command.Acknowledge)
        s = e.apply(s, Command.AttackEnemy("eg_nebelwoelfe", hits = 1))
        s = e.apply(s, Command.Acknowledge)
        assertTrue(s.isIdle)
    }

    @Test
    fun `steadfast groups cannot be stunned`() {
        val e = engineWith(EliteBonus.STEADFAST)
        var s = spawned(e)
        s = e.apply(s, Command.AttackEnemy("eg_nebelwoelfe", hits = 1, stun = true))
        assertFalse((s.currentPrompt as Prompt.AttackResult).stunned)
        assertFalse("eg_nebelwoelfe" in s.exhaustedGroups)
    }
}
