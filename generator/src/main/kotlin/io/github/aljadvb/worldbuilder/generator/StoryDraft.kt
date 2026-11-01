package io.github.aljadvb.worldbuilder.generator

import kotlinx.serialization.Serializable

/** What the user asks for. */
data class GenerationInput(
    /** Notes, a summary or a public-domain text the story is based on. */
    val source: String,
    val length: Length = Length.MEDIUM,
    val difficulty: Difficulty = Difficulty.NORMAL,
    /** Tile IDs that may be used (empty: all owned tiles). */
    val allowedTiles: Set<String> = emptySet(),
    /** Enemy type IDs that may be used (empty: all owned). */
    val allowedEnemies: Set<String> = emptySet(),
    /** Extra wishes in free text (tone, places, characters …). */
    val wishes: String = "",
    /** The whole map (all chapters) must fit on this table. */
    val table: io.github.aljadvb.worldbuilder.editing.TableSize = io.github.aljadvb.worldbuilder.editing.TableSize.DEFAULT,
) {
    enum class Length(val chapters: Int, val label: String) { SHORT(2, "kurz"), MEDIUM(3, "mittel"), LONG(4, "lang") }
    enum class Difficulty(val label: String) { EASY("leicht"), NORMAL("normal"), HARD("schwer") }
}

/** Step 1 result: the story outline the user reviews and edits before the scenario is built. */
@Serializable
data class StoryDraft(
    val title: String,
    val intro: String,
    val chapters: List<DraftChapter>,
    val objectives: List<DraftObjective>,
    val enemies: List<DraftEnemy>,
    val endings: List<DraftEnding>,
)

@Serializable
data class DraftChapter(
    val title: String,
    val summary: String,
    /** Places of this chapter, e.g. "Turmruine am Waldrand". */
    val locations: List<String>,
    /** Terrain category IDs the chapter's tiles should show. */
    val terrain: List<String>,
)

@Serializable
data class DraftObjective(val title: String, val howToComplete: String)

@Serializable
data class DraftEnemy(val enemyType: String, val where: String)

@Serializable
data class DraftEnding(val title: String, val outcome: String, val condition: String)
