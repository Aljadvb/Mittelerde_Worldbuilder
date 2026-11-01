package io.github.aljadvb.worldbuilder.engine.save

import io.github.aljadvb.worldbuilder.engine.CampaignProgress
import io.github.aljadvb.worldbuilder.engine.GameState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val SAVE_FORMAT_VERSION = 1

/** A saved game: campaign progress plus the state of the running scenario. */
@Serializable
data class SaveGame(
    val saveFormatVersion: Int = SAVE_FORMAT_VERSION,
    val slot: String,
    val campaignId: String,
    val campaignTitle: String,
    /** Checksum of the campaign file the game was started with, to detect later edits. */
    val campaignChecksum: String? = null,
    val savedAtEpochMillis: Long,
    val progress: CampaignProgress,
    /** Null once the campaign is over. */
    val game: GameState?,
)

object SaveGameJson {
    val json = Json {
        classDiscriminator = "type"
        // Newer app versions may add fields; old saves must still load.
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun encode(save: SaveGame): String = json.encodeToString(SaveGame.serializer(), save)

    fun decode(text: String): SaveGame {
        val save = json.decodeFromString(SaveGame.serializer(), text)
        require(save.saveFormatVersion <= SAVE_FORMAT_VERSION) { "Spielstand-Format ${save.saveFormatVersion} ist zu neu." }
        return save
    }
}
