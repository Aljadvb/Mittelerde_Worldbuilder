package io.github.aljadvb.worldbuilder.validation

/** Kinds of identifiable objects in a campaign, with their expected ID prefix and German name. */
enum class ObjectKind(val prefix: String, val displayName: String) {
    CAMPAIGN("cmp_", "Kampagne"),
    SCENARIO("scn_", "Szenario"),
    CHAPTER("ch_", "Kapitel"),
    PLACEMENT("pl_", "Kachel-Platzierung"),
    BOARD_PLACEMENT("bp_", "Schlachtplan-Platzierung"),
    TERRAIN_PLACEMENT("gt_", "Geländeteil"),
    TOKEN("tok_", "Token"),
    EVENT("evt_", "Event"),
    TRIGGER("trg_", "Trigger"),
    OBJECTIVE("obj_", "Ziel"),
    ENEMY_GROUP("eg_", "Gegnergruppe"),
    RESOLUTION("res_", "Auflösung"),
    PLACE("pt_", "Ort"),
    FLAG("fl_", "Merker"),
}

internal val ID_PATTERN = Regex("^[a-z][a-z0-9_]*$")
