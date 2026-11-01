package io.github.aljadvb.worldbuilder.engine

import io.github.aljadvb.worldbuilder.model.FormatJson
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExamplePlaythroughTest {
    private val engine = GameEngine(Examples.scenario)

    private fun started(heroes: Int = 2): Play =
        Play(engine, engine.start(heroes))
            .ack<Prompt.Intro>()
            .ack<Prompt.Setup> { assertEquals("ch_dorf", it.chapterId) }
            .ack<Prompt.ObjectiveSet> { assertEquals("obj_muehle_finden", it.objectiveId) }
            .text("Hahn")
            .idle()

    @Test
    fun `victory path through both chapters`() {
        val play = started()
        assertEquals(setOf("tok_dorfbewohnerin", "tok_brunnen"), play.state.activeTokens)

        // Well: successful test lowers threat (already 0, so no notice).
        play.send(Command.InteractToken("tok_brunnen"))
            .ack<Prompt.Inspect>()
            .text("Eimer")
            .expect<Prompt.Test> { assertEquals(2, it.step.successes) }
            .send(Command.ReportTest(2))
            .text("Messingschlüssel")
            .idle()
        assertFalse("tok_brunnen" in play.state.activeTokens)

        // Washerwoman: choice adds the tracks token.
        play.send(Command.InteractToken("tok_dorfbewohnerin"))
            .ack<Prompt.Inspect>()
            .text("Mühle")
            .expect<Prompt.Choice>()
            .send(Command.Choose(0))
            .text("fortgeschleift")
            .ack<Prompt.PlaceToken> { assertEquals("tok_spuren", it.tokenId) }
            .idle()

        // Tracks: completes objective 1, unlocks the mill, spawns wolves.
        play.send(Command.InteractToken("tok_spuren"))
            .ack<Prompt.Inspect>()
            .ack<Prompt.ObjectiveCompleted> { assertEquals("obj_muehle_finden", it.objectiveId) }
            .text("Spuren den Hang")
            .ack<Prompt.Setup> { assertEquals("ch_muehle", it.chapterId) }
            .ack<Prompt.ObjectiveSet> { assertEquals("obj_mueller_befreien", it.objectiveId) }
            .text("graue Gestalten")
            .ack<Prompt.SpawnEnemies> { assertEquals(1, it.count) }
            .idle()
        assertEquals(listOf("ch_dorf", "ch_muehle"), play.state.unlockedChapters)
        assertEquals(listOf("obj_mueller_befreien"), play.state.activeObjectives)

        play.send(Command.DefeatEnemyGroup("eg_nebelwoelfe")).text("Heulen").idle()

        // Gate: first attempt fails and raises threat; token stays.
        play.send(Command.InteractToken("tok_muehlentor"))
            .ack<Prompt.Inspect>()
            .text("Balken")
            .send(Command.ReportTest(1))
            .text("rührt sich nicht")
            .ack<Prompt.ThreatChanged> { assertEquals(2, it.threat) }
            .idle()
        assertTrue("tok_muehlentor" in play.state.activeTokens)

        play.send(Command.InteractToken("tok_muehlentor"))
            .ack<Prompt.Inspect>()
            .text("Balken")
            .send(Command.ReportTest(3))
            .text("Müller gefesselt")
            .ack<Prompt.RemoveToken>()
            .ack<Prompt.ObjectiveCompleted> { assertEquals("obj_mueller_befreien", it.objectiveId) }

        assertEquals("res_muehle_sieg", play.state.resolution)
        assertTrue(play.state.isOver)
        assertFailsWith<EngineException> { play.send(Command.EndRound) }
    }

    @Test
    fun `defeat when threat reaches its maximum`() {
        val play = started(heroes = 2) // +3 threat per round
        repeat(2) { play.send(Command.EndRound).ack<Prompt.RoundEnded>().idle() } // 3, 6
        play.send(Command.EndRound) // 9: crosses threshold 8
            .ack<Prompt.RoundEnded> { assertEquals(9, it.threat) }
            .text("Gassen")
            .ack<Prompt.RemoveToken> { assertEquals("tok_dorfbewohnerin", it.tokenId) }
            .idle()
        play.send(Command.EndRound).ack<Prompt.RoundEnded>().idle() // 12
        play.send(Command.EndRound).ack<Prompt.RoundEnded>().text("Eisige Kälte").idle() // 15: crosses 14
        play.send(Command.EndRound).ack<Prompt.RoundEnded>().idle() // 18
        assertFalse(play.state.isOver)
        play.send(Command.EndRound).ack<Prompt.RoundEnded> { assertEquals(20, it.threat) } // capped at max
        assertEquals("res_muehle_niederlage", play.state.resolution)
        assertEquals(7, play.state.round - 1)
    }

    @Test
    fun `threat thresholds fire once and threat max ends the scenario`() {
        var play = started(heroes = 5) // +6 per round
        play.send(Command.EndRound).ack<Prompt.RoundEnded> { assertEquals(6, it.threat) }.idle()
        play.send(Command.EndRound) // 12: crosses 8
            .ack<Prompt.RoundEnded>()
            .text("Gassen")
            .ack<Prompt.RemoveToken>()
            .idle()
        play.send(Command.EndRound) // 18: crosses 14
            .ack<Prompt.RoundEnded>()
            .text("Eisige Kälte")
            .idle()
        play.send(Command.EndRound) // capped at 20 -> max
            .ack<Prompt.RoundEnded> { assertEquals(20, it.threat) }
        assertEquals("res_muehle_niederlage", play.state.resolution)
        assertEquals(setOf("trg_bedrohung_8", "trg_bedrohung_14", "trg_bedrohung_max"), play.state.firedTriggers)
    }

    @Test
    fun `invalid commands are rejected`() {
        val fresh = engine.start(3)
        assertFailsWith<EngineException> { engine.apply(fresh, Command.EndRound) }
        assertFailsWith<EngineException> { engine.apply(fresh, Command.Choose(0)) }
        val play = started()
        assertFailsWith<EngineException> { play.send(Command.InteractToken("tok_spuren")) } // not placed yet
        assertFailsWith<EngineException> { play.send(Command.DefeatEnemyGroup("eg_nebelwoelfe")) }
        assertFailsWith<EngineException> { play.send(Command.Acknowledge) }
    }

    @Test
    fun `state can be saved in the middle of an event`() {
        val play = started()
            .send(Command.InteractToken("tok_dorfbewohnerin"))
            .ack<Prompt.Inspect>()
            .text("Mühle")
            .expect<Prompt.Choice>()
        val json = Json { classDiscriminator = "type" }
        val restored = json.decodeFromString(GameState.serializer(), json.encodeToString(GameState.serializer(), play.state))
        assertEquals(play.state, restored)

        Play(engine, restored).send(Command.Choose(1))
            .text("Dankbar")
            .ack<Prompt.PlaceToken>()
            .idle()
        // FormatJson is only used to make sure model and engine share the discriminator name.
        assertEquals("type", FormatJson.json.configuration.classDiscriminator)
    }
}
