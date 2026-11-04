package io.github.aljadvb.worldbuilder.engine

import io.github.aljadvb.worldbuilder.model.Campaign
import kotlinx.serialization.Serializable

/** Progress through a campaign: scenarios played, lore, flags and the heroes' experience. */
@Serializable
data class CampaignProgress(
    val heroCount: Int,
    val currentScenario: String?,
    val completed: List<CompletedScenario> = emptyList(),
    val heroes: List<Hero> = emptyList(),
    /** Total lore of the group; it never decreases. */
    val lore: Int = 0,
    val flags: Set<String> = emptySet(),
    /** Items of the group (trinkets) in the order they were found; they belong to the whole group. */
    val inventory: List<String> = emptyList(),
    /** Either-or choice for the next adventure (empty or one entry: no choice). */
    val nextOptions: List<String> = emptyList(),
    /** True between two scenarios until the group leaves the camp. */
    val inCamp: Boolean = false,
) {
    val isCampaignOver: Boolean get() = currentScenario == null

    /** Heroes, filled up with placeholders for saves made before heroes were recorded. */
    val party: List<Hero> get() = heroes + (heroes.size until heroCount).map { Hero("Held ${it + 1}", "") }
}

/** One scenario played, with what it brought. */
@Serializable
data class CompletedScenario(val scenarioId: String, val resolutionId: String, val lore: Int = 0, val xp: Int = 0, val items: List<String> = emptyList())

/** A hero of the group. Experience is kept per role, as in the rules. */
@Serializable
data class Hero(
    val name: String,
    val role: String,
    val xp: Map<String, RoleXp> = emptyMap(),
    val skills: List<SkillPurchase> = emptyList(),
    /** Corruption (Dark Paths); it lasts for the whole campaign. */
    val corruption: Int = 0,
    /** Title cards (catalog skill IDs) the hero earned. */
    val titles: List<String> = emptyList(),
) {
    fun xpFor(role: String): RoleXp = xp[role] ?: RoleXp()
    val available: Int get() = xp.values.sumOf { it.available }
}

@Serializable
data class RoleXp(val earned: Int = 0, val spent: Int = 0) {
    val available: Int get() = earned - spent
}

/** A skill card bought in camp; [sold] cards gave their experience back. */
@Serializable
data class SkillPurchase(val role: String, val name: String, val cost: Int, val sold: Boolean = false)

/** Moves a group through the scenarios of a campaign, following its transitions. */
class CampaignRunner(val campaign: Campaign, catalog: io.github.aljadvb.worldbuilder.model.MaterialCatalog? = null) {
    private val enemyTypes = catalog?.enemyTypes.orEmpty().associateBy { it.id }
    private val items = catalog?.items.orEmpty()


    /** A fresh game for the progress' current scenario (after the group chose another next adventure). */
    fun startCurrent(p: CampaignProgress): GameState? = p.currentScenario?.let { engineFor(it, p.flags, p.inventory).start(p.heroCount, p.party.map { h -> h.corruption }, p.party.flatMap { h -> h.titles }.toSet()) }

    fun engineFor(scenarioId: String, flags: Set<String> = emptySet(), owned: List<String> = emptyList()): GameEngine =
        GameEngine(campaign.scenario(scenarioId) ?: throw EngineException("Szenario '$scenarioId' fehlt in der Kampagne."), flags, enemyTypes, items, owned)

    fun start(heroCount: Int, heroes: List<Hero> = emptyList()): Pair<CampaignProgress, GameState> {
        val progress = CampaignProgress(heroCount, campaign.startScenario, heroes = heroes.take(heroCount))
        return progress to engineFor(campaign.startScenario).start(heroCount, progress.party.map { it.corruption }, progress.party.flatMap { it.titles }.toSet())
    }

    /**
     * Records the ended scenario with its rewards (lore, experience for every hero's current
     * role, flags) and starts the next one according to the transitions; the group is then in
     * camp. Returns a null state if the campaign is over.
     */
    fun advance(progress: CampaignProgress, finished: GameState): Pair<CampaignProgress, GameState?> {
        val resolutionId = finished.resolution ?: throw EngineException("Das Szenario ist noch nicht beendet.")
        val resolution = campaign.scenario(finished.scenarioId)?.resolution(resolutionId)
        val xp = resolution?.xp ?: 0
        val lore = finished.loreGained + (resolution?.lore ?: 0)
        val flags = progress.flags + finished.flags + resolution?.setFlags.orEmpty()
        val heroes = progress.party.mapIndexed { i, h ->
            h.copy(
                xp = h.xp + (h.role to h.xpFor(h.role).let { it.copy(earned = it.earned + xp) }),
                corruption = finished.corruption.getOrElse(i) { h.corruption },
                titles = h.titles + finished.titlesGained.filter { it.hero == i }.map { it.title },
            )
        }
        val options = campaign.nextScenarios(finished.scenarioId, resolutionId, flags)
        val next = options.firstOrNull()
        val updated = progress.copy(
            currentScenario = next,
            completed = progress.completed + CompletedScenario(finished.scenarioId, resolutionId, lore, xp, finished.itemsFound),
            inventory = progress.inventory + finished.itemsFound,
            heroes = heroes,
            lore = progress.lore + lore,
            flags = flags,
            nextOptions = options,
            inCamp = true,
        )
        return updated to next?.let { engineFor(it, flags, updated.inventory).start(progress.heroCount, heroes.map { h -> h.corruption }, heroes.flatMap { h -> h.titles }.toSet()) }
    }
}

/** Camp actions between scenarios. All return a new progress or throw [EngineException]. */
object Camp {
    fun buySkill(p: CampaignProgress, hero: Int, name: String, cost: Int): CampaignProgress = updateHero(p, hero) { h ->
        if (cost < 0) throw EngineException("Kosten dürfen nicht negativ sein.")
        val role = h.xpFor(h.role)
        if (role.available < cost) throw EngineException("${h.name} hat als ${h.role.ifBlank { "Held" }} nur ${role.available} EP.")
        h.copy(xp = h.xp + (h.role to role.copy(spent = role.spent + cost)), skills = h.skills + SkillPurchase(h.role, name, cost))
    }

    fun sellSkill(p: CampaignProgress, hero: Int, skill: Int): CampaignProgress = updateHero(p, hero) { h ->
        val s = h.skills.getOrNull(skill)?.takeIf { !it.sold } ?: throw EngineException("Diese Fertigkeit wurde nicht gekauft.")
        val role = h.xpFor(s.role)
        h.copy(xp = h.xp + (s.role to role.copy(spent = role.spent - s.cost)), skills = h.skills.mapIndexed { i, x -> if (i == skill) x.copy(sold = true) else x })
    }

    /** Changes the hero's role; experience of the old role stays with it. */
    fun changeRole(p: CampaignProgress, hero: Int, role: String): CampaignProgress = updateHero(p, hero) { it.copy(role = role.trim()) }

    fun rename(p: CampaignProgress, hero: Int, name: String): CampaignProgress = updateHero(p, hero) { it.copy(name = name.trim().ifEmpty { it.name }) }

    fun leave(p: CampaignProgress): CampaignProgress = p.copy(inCamp = false, nextOptions = emptyList())

    /** Either-or decision: the group picks the next adventure among [CampaignProgress.nextOptions]. */
    fun chooseNext(p: CampaignProgress, scenarioId: String): CampaignProgress {
        if (scenarioId !in p.nextOptions) throw EngineException("Dieses Abenteuer steht nicht zur Wahl.")
        return p.copy(currentScenario = scenarioId)
    }

    /** Replaces trinket [itemId] by [targetId] (a card of the next level), if the group's lore reaches the card's lore value. */
    fun upgradeItem(p: CampaignProgress, itemId: String, items: List<io.github.aljadvb.worldbuilder.model.Item>, targetId: String? = null): CampaignProgress {
        if (itemId !in p.inventory) throw EngineException("Die Gruppe besitzt dieses Schmuckstück nicht.")
        val item = items.firstOrNull { it.id == itemId } ?: throw EngineException("Unbekanntes Schmuckstück '$itemId'.")
        val options = item.upgrades(items)
        val next = (if (targetId == null) options.firstOrNull() else options.firstOrNull { it.id == targetId })
            ?: throw EngineException("„${item.name}“ hat keine passende höhere Stufe.")
        val needed = item.lore ?: 0
        if (p.lore < needed) throw EngineException("Dafür braucht die Gruppe $needed Wissen (vorhanden: ${p.lore}).")
        // Only one copy is upgraded (the group may own two of the same card).
        val i = p.inventory.indexOf(itemId)
        return p.copy(inventory = p.inventory.toMutableList().also { it[i] = next.id })
    }

    /** Trinkets of the inventory that can be upgraded now. */
    fun upgradable(p: CampaignProgress, items: List<io.github.aljadvb.worldbuilder.model.Item>): List<String> =
        p.inventory.filter { id -> items.firstOrNull { it.id == id }?.let { it.upgrades(items).isNotEmpty() && p.lore >= (it.lore ?: 0) } == true }

    private fun updateHero(p: CampaignProgress, index: Int, f: (Hero) -> Hero): CampaignProgress {
        val party = p.party
        if (index !in party.indices) throw EngineException("Unbekannter Held.")
        return p.copy(heroes = party.mapIndexed { i, h -> if (i == index) f(h) else h })
    }
}
