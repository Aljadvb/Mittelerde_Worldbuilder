package io.github.aljadvb.worldbuilder.engine.save

import java.io.File

/**
 * Stores save games as JSON files: `<root>/<campaignId>/<slot>.json`.
 * Writes go to a temporary file first and are then moved into place.
 */
class SaveStore(private val root: File) {

    fun save(save: SaveGame) {
        requireSafe(save.campaignId)
        requireSafe(save.slot)
        val dir = File(root, save.campaignId).apply { mkdirs() }
        val target = File(dir, "${save.slot}.json")
        val tmp = File(dir, "${save.slot}.json.tmp")
        tmp.writeText(SaveGameJson.encode(save))
        if (!tmp.renameTo(target)) {
            target.delete()
            check(tmp.renameTo(target)) { "Spielstand konnte nicht geschrieben werden: $target" }
        }
    }

    fun load(campaignId: String, slot: String): SaveGame? {
        val file = file(campaignId, slot)
        return if (file.isFile) SaveGameJson.decode(file.readText()) else null
    }

    /** All readable saves, newest first. Unreadable files are skipped. */
    fun list(): List<SaveGame> =
        root.listFiles().orEmpty().filter { it.isDirectory }
            .flatMap { dir -> dir.listFiles { f -> f.extension == "json" }.orEmpty().toList() }
            .mapNotNull { runCatching { SaveGameJson.decode(it.readText()) }.getOrNull() }
            .sortedByDescending { it.savedAtEpochMillis }

    fun delete(campaignId: String, slot: String): Boolean = file(campaignId, slot).delete()

    private fun file(campaignId: String, slot: String): File {
        requireSafe(campaignId)
        requireSafe(slot)
        return File(File(root, campaignId), "$slot.json")
    }

    private fun requireSafe(name: String) {
        require(SAFE_NAME.matches(name)) { "Ungültiger Name: '$name'" }
    }

    private companion object {
        val SAFE_NAME = Regex("^[A-Za-z0-9_-]{1,80}$")
    }
}
