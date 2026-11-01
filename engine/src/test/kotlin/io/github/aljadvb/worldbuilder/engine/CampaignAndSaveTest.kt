package io.github.aljadvb.worldbuilder.engine

import io.github.aljadvb.worldbuilder.engine.save.SaveGame
import io.github.aljadvb.worldbuilder.engine.save.SaveGameJson
import io.github.aljadvb.worldbuilder.engine.save.SaveStore
import io.github.aljadvb.worldbuilder.model.ScenarioTransition
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CampaignAndSaveTest {
    private val runner = CampaignRunner(Examples.campaign)

    /** Plays the example to the defeat ending by ending rounds. */
    private fun playToDefeat(state: GameState): GameState {
        val engine = runner.engineFor(state.scenarioId)
        var s = state
        while (!s.isOver) {
            s = if (s.isIdle) engine.apply(s, Command.EndRound) else engine.apply(s, Command.Acknowledge)
        }
        return s
    }

    @Test
    fun `campaign without transition ends after its scenario`() {
        val (progress, state) = runner.start(heroCount = 3)
        val (after, next) = runner.advance(progress, playToDefeat(state))
        assertNull(next)
        assertTrue(after.isCampaignOver)
        assertEquals(listOf(CompletedScenario("scn_muehle", "res_muehle_niederlage")), after.completed)
    }

    @Test
    fun `transitions pick the next scenario by resolution`() {
        val replay = Examples.scenario.copy(id = "scn_nochmal")
        val campaign = Examples.campaign.copy(
            scenarios = Examples.campaign.scenarios + replay,
            transitions = listOf(ScenarioTransition("scn_muehle", "res_muehle_niederlage", "scn_nochmal")),
        )
        val runner = CampaignRunner(campaign)
        val (progress, state) = runner.start(2)
        val (after, next) = runner.advance(progress, playToDefeat(state))
        assertEquals("scn_nochmal", after.currentScenario)
        assertEquals("scn_nochmal", assertNotNull(next).scenarioId)
        assertEquals(2, next.heroCount)
    }

    @Test
    fun `advancing an unfinished scenario fails`() {
        val (progress, state) = runner.start(2)
        assertFailsWith<EngineException> { runner.advance(progress, state) }
    }

    @Test
    fun `save store round trip, listing and deletion`() {
        val dir = Files.createTempDirectory("saves").toFile()
        try {
            val store = SaveStore(dir)
            val (progress, state) = runner.start(4)
            val engine = runner.engineFor(state.scenarioId)
            val midGame = engine.apply(state, Command.Acknowledge) // setup prompt open
            val older = SaveGame(slot = "auto", campaignId = "cmp_hainbrueck", campaignTitle = "T", savedAtEpochMillis = 1, progress = progress, game = state)
            val newer = older.copy(slot = "slot_2", savedAtEpochMillis = 2, game = midGame, campaignChecksum = "abc")
            store.save(older)
            store.save(newer)
            assertEquals(newer, store.load("cmp_hainbrueck", "slot_2"))
            assertEquals(listOf("slot_2", "auto"), store.list().map { it.slot })

            // overwrite in place
            store.save(older.copy(savedAtEpochMillis = 3))
            assertEquals(3, store.load("cmp_hainbrueck", "auto")?.savedAtEpochMillis)

            assertTrue(store.delete("cmp_hainbrueck", "auto"))
            assertNull(store.load("cmp_hainbrueck", "auto"))
            assertFailsWith<IllegalArgumentException> { store.load("../etc", "x") }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `saves from newer app versions with extra fields still load`() {
        val (progress, state) = runner.start(1)
        val text = SaveGameJson.encode(
            SaveGame(slot = "a", campaignId = "c", campaignTitle = "t", savedAtEpochMillis = 0, progress = progress, game = state),
        ).replaceFirst("{", "{\n  \"futureField\": 42,")
        assertEquals("a", SaveGameJson.decode(text).slot)
    }
}
