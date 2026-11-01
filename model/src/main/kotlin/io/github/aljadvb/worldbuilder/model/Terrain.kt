package io.github.aljadvb.worldbuilder.model

/**
 * Fixed vocabulary for describing what a tile side shows. Used to pick tiles that fit a
 * story (e.g. by the scenario generator) and shown in the editor. IDs are stable; labels German.
 */
enum class TerrainGroup(val label: String) {
    LANDSCAPE("Landschaft"),
    WATER("Wasser"),
    STRUCTURE("Bauwerke"),
    INDOOR("Drinnen"),
    MOOD("Stimmung"),
}

data class TerrainTag(val id: String, val label: String, val group: TerrainGroup)

object Terrain {
    val TAGS: List<TerrainTag> = listOf(
        TerrainTag("wiese", "Wiese / Grasland", TerrainGroup.LANDSCAPE),
        TerrainTag("gebuesch", "Gebüsch", TerrainGroup.LANDSCAPE),
        TerrainTag("lichtung", "Lichtung", TerrainGroup.LANDSCAPE),
        TerrainTag("wald_licht", "Lichter Wald", TerrainGroup.LANDSCAPE),
        TerrainTag("wald_dicht", "Dichter Wald", TerrainGroup.LANDSCAPE),
        TerrainTag("huegel", "Hügel", TerrainGroup.LANDSCAPE),
        TerrainTag("felsen", "Felsen / Klippen", TerrainGroup.LANDSCAPE),
        TerrainTag("gebirge", "Gebirge", TerrainGroup.LANDSCAPE),
        TerrainTag("oedland", "Ödland", TerrainGroup.LANDSCAPE),
        TerrainTag("weg", "Weg / Straße", TerrainGroup.LANDSCAPE),
        TerrainTag("fluss", "Fluss / Bach", TerrainGroup.WATER),
        TerrainTag("gewaesser", "See / Ufer", TerrainGroup.WATER),
        TerrainTag("sumpf", "Sumpf", TerrainGroup.WATER),
        TerrainTag("ruinen", "Ruinen", TerrainGroup.STRUCTURE),
        TerrainTag("bauwerk", "Bauwerk / Turm", TerrainGroup.STRUCTURE),
        TerrainTag("siedlung", "Dorf / Stadt", TerrainGroup.STRUCTURE),
        TerrainTag("hoehle", "Höhle", TerrainGroup.INDOOR),
        TerrainTag("innenraum", "Innenraum / Halle", TerrainGroup.INDOOR),
        TerrainTag("duester", "Düster", TerrainGroup.MOOD),
    )

    private val byId = TAGS.associateBy { it.id }

    fun tag(id: String): TerrainTag? = byId[id]

    fun label(id: String): String = byId[id]?.label ?: id
}
