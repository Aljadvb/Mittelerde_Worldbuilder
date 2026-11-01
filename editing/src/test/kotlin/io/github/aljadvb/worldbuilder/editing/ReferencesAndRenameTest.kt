package io.github.aljadvb.worldbuilder.editing

import io.github.aljadvb.worldbuilder.validation.IssueCode
import io.github.aljadvb.worldbuilder.validation.ObjectKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ReferencesAndRenameTest {
    private val campaign = Examples.campaign

    @Test
    fun `ids from titles are readable and unique`() {
        assertEquals("tok_alter_brunnen", Ids.fromTitle(ObjectKind.TOKEN, "Alter Brunnen", emptySet()))
        assertEquals("evt_waesche_tragen_gross", Ids.fromTitle(ObjectKind.EVENT, "Wäsche tragen (groß)", emptySet()))
        assertEquals("tok_brunnen_2", Ids.fromTitle(ObjectKind.TOKEN, "Brunnen", setOf("tok_brunnen")))
        assertEquals("tok_brunnen_3", Ids.fromTitle(ObjectKind.TOKEN, "Brunnen", setOf("tok_brunnen", "tok_brunnen_2")))
        assertEquals("evt_neu", Ids.fromTitle(ObjectKind.EVENT, "!!!", emptySet()))
        assertEquals("evt_ankunft", Ids.fromTitle(ObjectKind.EVENT, "evt ankunft", emptySet()))
    }

    @Test
    fun `definitions and references cover the example`() {
        val defs = campaign.definitions()
        assertEquals(defs.size, campaign.allIds().size)
        // every reference in a valid campaign resolves to a definition of the same kind
        val byId = defs.associateBy { it.id }
        campaign.references().forEach { r -> assertEquals(r.kind, byId.getValue(r.id).kind, r.toString()) }
    }

    @Test
    fun `usages list where an object is used`() {
        val paths = campaign.usages("tok_spuren").map { it.path }
        assertEquals(
            listOf(
                "scenarios[0].events[2].steps[1].token",
                "scenarios[0].events[3].steps[2].token",
                "scenarios[0].triggers[0].on.token",
            ),
            paths,
        )
        assertEquals(listOf("evt_tor_offen", "obj_mueller_befreien"), campaign.usages("trg_mueller_befreit").map { it.ownerId })
    }

    @Test
    fun `mapping references with identity changes nothing`() {
        assertEquals(campaign, campaign.mapReferences { it.id })
    }

    @Test
    fun `rename updates the definition and all references`() {
        val renamed = campaign.rename("evt_brunnen_erfolg", "evt_schluessel_gefunden")
        assertTrue("evt_brunnen_erfolg" !in renamed.allIds())
        assertTrue("evt_schluessel_gefunden" in renamed.allIds())
        assertEquals(1, renamed.usages("evt_schluessel_gefunden").size)
        assertEquals("", renamed.validate().format())
    }

    @Test
    fun `renaming every object keeps the example valid`() {
        var c = campaign
        campaign.definitions().forEach { d -> c = c.rename(d.id, d.id + "_x") }
        assertEquals("", c.validate().format())
        assertTrue(c.allIds().all { it.endsWith("_x") })
    }

    @Test
    fun `rename rejects invalid or taken ids`() {
        assertFailsWith<EditException> { campaign.rename("tok_brunnen", "Brunnen 2") }
        assertFailsWith<EditException> { campaign.rename("tok_brunnen", "tok_spuren") }
        assertFailsWith<EditException> { campaign.rename("gibt_es_nicht", "tok_neu") }
    }

    @Test
    fun `delete removes the object and leaves dangling references for the validator`() {
        val deleted = campaign.delete("tok_spuren")
        assertTrue("tok_spuren" !in deleted.allIds())
        val unknown = deleted.validate().errors.filter { it.code == IssueCode.UNKNOWN_REFERENCE }
        assertEquals(3, unknown.size)
    }

    @Test
    fun `deleting a chapter removes its tiles and tokens`() {
        val deleted = campaign.delete("ch_muehle")
        assertTrue(listOf("ch_muehle", "pl_muehle", "tok_muehlentor").none { it in deleted.allIds() })
        assertFailsWith<EditException> { campaign.delete(campaign.id) }
    }
}
