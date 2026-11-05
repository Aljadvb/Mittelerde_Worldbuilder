package io.github.aljadvb.worldbuilder.engine

import io.github.aljadvb.worldbuilder.model.HeroDefinition
import io.github.aljadvb.worldbuilder.model.Item
import io.github.aljadvb.worldbuilder.model.ItemKind
import io.github.aljadvb.worldbuilder.model.MaterialCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GearTest {
    private fun w(id: String, name: String, line: String, level: Int, lore: Int?, count: Int = 1) =
        Item(id, name, "x", ItemKind.WEAPON, line = line, level = level, lore = lore, count = count, stat = "Körperkraft", hands = 1)
    private val items = listOf(
        w("schwert", "Schwert", "schwert", 1, 27, count = 2), w("schwert_n", "Númenórisches Schwert", "schwert", 2, 58),
        w("schwert_3", "Sonnensilber", "schwert", 3, 90), w("schwert_4", "Willensschnitzer", "schwert", 4, null),
        w("dolch", "Dolch", "dolch", 1, 25), w("dolch_2", "Gondolin-Dolch", "dolch", 2, 55), w("dolch_3", "Witwenwarnung", "dolch", 3, 85), w("dolch_4", "Wurmzahn", "dolch", 4, null),
    )
    private val catalog = MaterialCatalog(formatVersion = 1, items = items, heroes = listOf(HeroDefinition("aragorn", "Aragorn", "x", startingGear = listOf("Schwert", "Dolch")), HeroDefinition("boromir", "Boromir", "x", startingGear = listOf("Schwert"))))

    @Test
    fun `heroes start with their equipment`() {
        val (p, _) = CampaignRunner(Examples.campaign, catalog).start(2, listOf(Hero("Aragorn", ""), Hero("Boromir", "")))
        assertEquals(listOf("schwert", "dolch"), p.party[0].gear)
        assertEquals(listOf("schwert"), p.party[1].gear)
    }

    @Test
    fun `upgrades need lore, free cards, and only one level IV item`() {
        var p = CampaignProgress(2, null, heroes = listOf(Hero("A", "", gear = listOf("schwert", "dolch")), Hero("B", "", gear = listOf("schwert"))), lore = 20)
        assertFailsWith<EngineException> { Camp.upgradeGear(p, 0, "schwert", items) }
        p = p.copy(lore = 100)
        p = Camp.upgradeGear(p, 0, "schwert", items)
        assertEquals(listOf("schwert_n", "dolch"), p.party[0].gear)
        // The only Númenórean sword is taken.
        assertEquals(emptyList(), Camp.gearOptions(p, 1, items[0], items))
        p = Camp.upgradeGear(Camp.upgradeGear(p, 0, "schwert_n", items), 0, "schwert_3", items)
        p = Camp.upgradeGear(Camp.upgradeGear(p, 0, "dolch", items), 0, "dolch_2", items)
        assertEquals(listOf("schwert_4", "dolch_3"), p.party[0].gear)
        assertFailsWith<EngineException> { Camp.upgradeGear(p, 0, "dolch_3", items) }
        assertEquals(emptyList(), Camp.upgradableGear(p, 0, items))
    }

    @Test
    fun `starting equipment allows one armor and limited hands`() {
        val list = items + listOf(
            Item("bogen", "Großer Bogen", "x", ItemKind.WEAPON, line = "bogen", hands = 2),
            Item("ring", "Ringpanzer", "x", ItemKind.ARMOR, line = "ring"), Item("umhang", "Umhang", "x", ItemKind.ARMOR, line = "umhang"),
        )
        assertEquals(emptyList(), Camp.gearProblems(listOf("bogen", "ring"), list))
        assertEquals(2, Camp.gearProblems(listOf("bogen", "dolch", "ring", "umhang"), list).size)
        assertEquals(1, Camp.gearProblems(listOf("schwert", "dolch"), list, hands = 1).size)
    }
}
